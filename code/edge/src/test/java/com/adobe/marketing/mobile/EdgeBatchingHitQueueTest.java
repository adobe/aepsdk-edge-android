/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link EdgeBatchingHitQueue} — queue peek/remove behaviour per {@link BatchOutcome},
 * effective batch-size selection, and suspend/resume lifecycle.
 *
 * <p>Uses {@code MockitoJUnitRunner.Silent} to suppress strict-stub checks: several tests stub
 * {@code queue.peek()} via an Answer that fires a latch on the second call, which looks like an
 * "unnecessary" stub to the strict runner even though it controls test completion.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class EdgeBatchingHitQueueTest {

	@Mock
	DataQueue mockDataQueue;

	@Mock
	EdgeHitProcessor mockProcessor;

	private EdgeBatchingHitQueue hitQueue;
	private ScheduledExecutorService executor;
	private Map<String, Object> edgeConfig;

	@Before
	public void setup() {
		executor = Executors.newSingleThreadScheduledExecutor();
		edgeConfig = new HashMap<>();
		edgeConfig.put("edge.configId", "test-id");
	}

	@After
	public void tearDown() {
		if (hitQueue != null) {
			hitQueue.close();
		}
		executor.shutdownNow();
	}

	// -------------------------------------------------------------------------
	// DONE outcome — remove all entities in the batch
	// -------------------------------------------------------------------------

	@Test
	public void testBeginProcessing_done_removesBatch() throws InterruptedException {
		DataEntity entity = buildEntity(false);
		final CountDownLatch latch = new CountDownLatch(1);

		// First peek() returns the entity; second (next cycle, empty queue) fires the latch.
		when(mockDataQueue.peek())
			.thenReturn(entity)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(1));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		// Exactly one entity removed (single-entity batch, batching off)
		verify(mockDataQueue, times(1)).remove(1);
	}

	@Test
	public void testBeginProcessing_done_batchOfThree_removesThree() throws InterruptedException {
		List<DataEntity> batch = buildBatchEntities(3, true);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(3);
		when(mockDataQueue.peek(3)).thenReturn(batch);
		when(mockProcessor.processBatch(batch)).thenReturn(BatchOutcome.done(3));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).remove(3);
	}

	@Test
	public void testBeginProcessing_done_truncatedResolvedCount_removesOnlyResolvedPrefix()
		throws InterruptedException {
		// Regression test: processBatch may resolve fewer entities than the peeked window (e.g. a
		// window truncated at a Consent/Reset/decode-failure boundary). The queue must remove only
		// the resolved prefix reported on BatchOutcome, never the full peeked window size — otherwise
		// the untouched trailing entities are silently lost.
		List<DataEntity> batch = buildBatchEntities(3, true);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(3);
		when(mockDataQueue.peek(3)).thenReturn(batch);
		// Only 2 of the 3 peeked entities were actually resolved (e.g. the 3rd was a Consent event
		// that truncated the ExperienceEvent run).
		when(mockProcessor.processBatch(batch)).thenReturn(BatchOutcome.done(2));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).remove(2);
		verify(mockDataQueue, never()).remove(3);
	}

	// -------------------------------------------------------------------------
	// RETRY_BATCH outcome — entities retained, nothing removed
	// -------------------------------------------------------------------------

	@Test
	public void testBeginProcessing_retryBatch_doesNotRemoveEntities() throws InterruptedException {
		DataEntity entity = buildEntity(false);
		when(mockDataQueue.peek()).thenReturn(entity);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.retryBatch(30));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		Thread.sleep(150);

		// Nothing removed — entities stay for retry
		verify(mockDataQueue, never()).remove(anyInt());
		verify(mockDataQueue, never()).remove();
	}

	// -------------------------------------------------------------------------
	// PARTIAL_REMOVE outcome — a batch 400 was exploded into individual resends inside
	// EdgeHitProcessor.processBatch; the queue removes only the resolved head prefix and
	// retries the next (failed) entity, never removing the whole batch or skipping an entity.
	// -------------------------------------------------------------------------

	@Test
	public void testBeginProcessing_partialRemove_removesResolvedPrefixAndRetriesRemainder()
		throws InterruptedException {
		// EdgeHitProcessor.processBatch exploded a batch 400 into individual resends: 2 of 3 resolved,
		// the 3rd hit a recoverable failure, so it reports partialRemove(2, 30). The queue removes only
		// the 2 resolved entities and schedules a retry 30s out — it never removes the whole batch, and
		// the failed 3rd entity is retried, not skipped.
		List<DataEntity> batch = buildBatchEntities(3, true);
		when(mockDataQueue.peek()).thenReturn(batch.get(0));
		when(mockDataQueue.count()).thenReturn(3);
		when(mockDataQueue.peek(3)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.partialRemove(2, 30));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		Thread.sleep(150);

		// Only the resolved prefix (2) removed, exactly once; never the whole batch of 3.
		verify(mockDataQueue, times(1)).remove(2);
		verify(mockDataQueue, never()).remove(3);
		// The retry is scheduled 30s out (not immediate), so only the one batch attempt has run — the
		// failed entity waits for its retry rather than being dropped or re-attempted immediately.
		verify(mockProcessor, times(1)).processBatch(any());
	}

	// -------------------------------------------------------------------------
	// Effective batch-size selection
	// -------------------------------------------------------------------------

	@Test
	public void testGetEffectiveBatchSize_batchingDisabled_doesNotCallPeekN() throws InterruptedException {
		// When batching is off, the queue uses singletonList(head) — no peek(n) call.
		DataEntity entity = buildEntity(false);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(entity)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(1));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, never()).peek(anyInt());
		verify(mockProcessor, atLeastOnce()).processBatch(any());
	}

	@Test
	public void testGetEffectiveBatchSize_batchingEnabled_peeksMin_countAndMax() throws InterruptedException {
		// 5 entries in queue, batching on → peek(min(5, MAX_BATCH_SIZE))
		int queueCount = 5;
		int expectedBatchSize = Math.min(queueCount, EdgeConstants.Defaults.MAX_BATCH_SIZE);
		List<DataEntity> batch = buildBatchEntities(expectedBatchSize, true);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(queueCount);
		when(mockDataQueue.peek(expectedBatchSize)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(expectedBatchSize));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).peek(expectedBatchSize);
	}

	@Test
	public void testGetEffectiveBatchSize_batchingEnabled_queueLargerThanMax_capsAtMax() throws InterruptedException {
		int queueCount = EdgeConstants.Defaults.MAX_BATCH_SIZE + 3;
		List<DataEntity> batch = buildBatchEntities(EdgeConstants.Defaults.MAX_BATCH_SIZE, true);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(queueCount);
		when(mockDataQueue.peek(EdgeConstants.Defaults.MAX_BATCH_SIZE)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(EdgeConstants.Defaults.MAX_BATCH_SIZE));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).peek(EdgeConstants.Defaults.MAX_BATCH_SIZE);
		verify(mockDataQueue, never()).peek(queueCount);
	}

	@Test
	public void testGetEffectiveBatchSize_configuredMaxBatchSize_smallerThanDefault_honorsConfiguredValue()
		throws InterruptedException {
		// edge.batching.maxBatchSize=3 caps the window below the built-in default of 10.
		final int configuredMax = 3;
		List<DataEntity> batch = buildBatchEntities(configuredMax, true, configuredMax);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(10);
		when(mockDataQueue.peek(configuredMax)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(configuredMax));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).peek(configuredMax);
		verify(mockDataQueue, never()).peek(EdgeConstants.Defaults.MAX_BATCH_SIZE);
	}

	@Test
	public void testGetEffectiveBatchSize_configuredMaxBatchSize_largerThanDefault_honorsConfiguredValue()
		throws InterruptedException {
		// edge.batching.maxBatchSize=20 raises the window above the built-in default of 10.
		final int configuredMax = 20;
		List<DataEntity> batch = buildBatchEntities(configuredMax, true, configuredMax);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(25);
		when(mockDataQueue.peek(configuredMax)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(configuredMax));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).peek(configuredMax);
		verify(mockDataQueue, never()).peek(EdgeConstants.Defaults.MAX_BATCH_SIZE);
	}

	@Test
	public void testGetEffectiveBatchSize_configuredMaxBatchSize_exceedsLimit_clampsToLimit()
		throws InterruptedException {
		// edge.batching.maxBatchSize=50 exceeds MAX_BATCH_SIZE_LIMIT (20); clamped to the limit
		// regardless of what was configured, so a misconfigured value can't grow the batch unbounded.
		final int configuredMax = 50;
		final int limit = EdgeConstants.Defaults.MAX_BATCH_SIZE_LIMIT;
		List<DataEntity> batch = buildBatchEntities(limit, true, configuredMax);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(100);
		when(mockDataQueue.peek(limit)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(limit));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).peek(limit);
		verify(mockDataQueue, never()).peek(configuredMax);
	}

	@Test
	public void testGetEffectiveBatchSize_nonPositiveConfiguredMaxBatchSize_fallsBackToDefault()
		throws InterruptedException {
		// edge.batching.maxBatchSize=0 is not a usable value; falls back to the built-in default.
		List<DataEntity> batch = buildBatchEntities(EdgeConstants.Defaults.MAX_BATCH_SIZE, true, 0);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> {
				latch.countDown();
				return null;
			});
		when(mockDataQueue.count()).thenReturn(EdgeConstants.Defaults.MAX_BATCH_SIZE + 5);
		when(mockDataQueue.peek(EdgeConstants.Defaults.MAX_BATCH_SIZE)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(EdgeConstants.Defaults.MAX_BATCH_SIZE));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).peek(EdgeConstants.Defaults.MAX_BATCH_SIZE);
	}

	// -------------------------------------------------------------------------
	// Suspend
	// -------------------------------------------------------------------------

	@Test
	public void testSuspend_stopsProcessing() throws InterruptedException {
		DataEntity entity = buildEntity(false);
		when(mockDataQueue.add(any())).thenReturn(true);

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.suspend();
		hitQueue.queue(entity);

		Thread.sleep(150);

		verify(mockProcessor, never()).processBatch(any());
	}

	// -------------------------------------------------------------------------
	// C1: suspend() landing after a cycle is scheduled but before it starts
	// -------------------------------------------------------------------------

	@Test
	public void testRunBatchCycle_suspendedBeforeCycleStarts_doesNotProcess() throws InterruptedException {
		// A batchable entity is available; if the cycle ran, it would call processBatch.
		when(mockDataQueue.peek()).thenReturn(buildEntity(true));
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(1));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);

		// Occupy the single worker thread so the scheduled runBatchCycle cannot start yet.
		final CountDownLatch blockerStarted = new CountDownLatch(1);
		final CountDownLatch release = new CountDownLatch(1);
		executor.execute(() -> {
			blockerStarted.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		});
		blockerStarted.await();

		// Schedule a cycle (queued behind the blocker), then suspend before it can run.
		hitQueue.beginProcessing();
		hitQueue.suspend();

		// Marker enqueued after runBatchCycle; once it runs, the cycle has already been processed.
		final CountDownLatch cycleFinished = new CountDownLatch(1);
		executor.execute(cycleFinished::countDown);

		release.countDown();
		cycleFinished.await(2, TimeUnit.SECONDS);

		// Guard bailed before peeking/sending — nothing processed while suspended.
		verify(mockProcessor, never()).processBatch(any());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private DataEntity buildEntity(final boolean batchingEnabled) {
		return buildEntity(batchingEnabled, null);
	}

	private DataEntity buildEntity(final boolean batchingEnabled, final Integer maxBatchSize) {
		Map<String, Object> config = new HashMap<>(edgeConfig);
		Map<String, Object> batching = new HashMap<>();
		if (batchingEnabled) {
			batching.put(EdgeConstants.Batching.ENABLED, true);
		}
		if (maxBatchSize != null) {
			batching.put(EdgeConstants.Batching.MAX_BATCH_SIZE, maxBatchSize);
		}
		if (!batching.isEmpty()) {
			config.put(EdgeConstants.SharedState.Configuration.EDGE_BATCHING, batching);
		}

		Map<String, Object> xdmData = new HashMap<>();
		xdmData.put("test", "data");
		Map<String, Object> eventData = new HashMap<>();
		eventData.put("xdm", xdmData);

		Event event = new Event.Builder("test", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(eventData)
			.build();

		return new EdgeDataEntity(event, config, new HashMap<>()).toDataEntity();
	}

	private List<DataEntity> buildBatchEntities(final int count, final boolean batchingEnabled) {
		return buildBatchEntities(count, batchingEnabled, null);
	}

	private List<DataEntity> buildBatchEntities(
		final int count,
		final boolean batchingEnabled,
		final Integer maxBatchSize
	) {
		List<DataEntity> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(buildEntity(batchingEnabled, maxBatchSize));
		}
		return list;
	}
}
