/*
  Copyright 2024 Adobe. All rights reserved.
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
import java.util.Collections;
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

	@Mock DataQueue mockDataQueue;
	@Mock EdgeHitProcessor mockProcessor;

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
			.thenAnswer(inv -> { latch.countDown(); return null; });
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
			.thenAnswer(inv -> { latch.countDown(); return null; });
		when(mockDataQueue.count()).thenReturn(3);
		when(mockDataQueue.peek(3)).thenReturn(batch);
		when(mockProcessor.processBatch(batch)).thenReturn(BatchOutcome.done(3));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).remove(3);
	}

	@Test
	public void testBeginProcessing_done_truncatedResolvedCount_removesOnlyResolvedPrefix() throws InterruptedException {
		// Regression test: processBatch may resolve fewer entities than the peeked window (e.g. a
		// window truncated at a Consent/Reset/decode-failure boundary). The queue must remove only
		// the resolved prefix reported on BatchOutcome, never the full peeked window size — otherwise
		// the untouched trailing entities are silently lost.
		List<DataEntity> batch = buildBatchEntities(3, true);
		DataEntity head = batch.get(0);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(head)
			.thenAnswer(inv -> { latch.countDown(); return null; });
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
	// EXPLODE outcome — drain individually via forced batch-size-1, one at a time
	// -------------------------------------------------------------------------

	@Test
	public void testBeginProcessing_explode_drainsOneAtATime_neverRemovesAsWholeBatch() throws InterruptedException {
		// Batch of 3 gets a 400 (explode(3)); each of the 3 is then resolved individually via the
		// ordinary single-entity path, forced to batchSize 1 regardless of config.
		List<DataEntity> batch = buildBatchEntities(3, true);
		final CountDownLatch latch = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(batch.get(0), batch.get(0), batch.get(1), batch.get(2))
			.thenAnswer(inv -> { latch.countDown(); return null; });
		when(mockDataQueue.count()).thenReturn(3);
		when(mockDataQueue.peek(3)).thenReturn(batch);
		when(mockProcessor.processBatch(batch)).thenReturn(BatchOutcome.explode(3));
		when(mockProcessor.processBatch(Collections.singletonList(batch.get(0))))
			.thenReturn(BatchOutcome.done(1));
		when(mockProcessor.processBatch(Collections.singletonList(batch.get(1))))
			.thenReturn(BatchOutcome.done(1));
		when(mockProcessor.processBatch(Collections.singletonList(batch.get(2))))
			.thenReturn(BatchOutcome.done(1));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		// The original batch-sized peek happened exactly once (the failed attempt); the 3 drain
		// cycles never re-peek a batch window (peek(int) is called exactly this once, total).
		verify(mockDataQueue, times(1)).peek(anyInt());
		verify(mockDataQueue, times(1)).peek(3);
		// Each of the 3 drained entities removed individually — never as a group of 3.
		verify(mockDataQueue, times(3)).remove(1);
		verify(mockDataQueue, never()).remove(3);
	}

	@Test
	public void testBeginProcessing_explode_recoverableFailureDuringDrain_retriesInPlace_doesNotSkip()
		throws InterruptedException {
		// Batch of 2 gets a 400 (explode(2)); the first drained entity hits a recoverable failure and
		// must be retried in place — never skipped, never removed — exactly like a non-batched retry.
		List<DataEntity> batch = buildBatchEntities(2, true);
		final CountDownLatch retrySeen = new CountDownLatch(1);

		when(mockDataQueue.peek())
			.thenReturn(batch.get(0))
			.thenAnswer(inv -> { retrySeen.countDown(); return batch.get(0); });
		when(mockDataQueue.count()).thenReturn(2);
		when(mockDataQueue.peek(2)).thenReturn(batch);
		when(mockProcessor.processBatch(batch)).thenReturn(BatchOutcome.explode(2));
		when(mockProcessor.processBatch(Collections.singletonList(batch.get(0))))
			.thenReturn(BatchOutcome.retryBatch(30));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		retrySeen.await(2, TimeUnit.SECONDS);

		// Never removed — the retrying entity stays queued, retried in place.
		verify(mockDataQueue, never()).remove(anyInt());
		verify(mockDataQueue, never()).remove();
		// The second entity in the batch was never attempted while the first is still retrying.
		verify(mockProcessor, never()).processBatch(Collections.singletonList(batch.get(1)));
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
			.thenAnswer(inv -> { latch.countDown(); return null; });
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
			.thenAnswer(inv -> { latch.countDown(); return null; });
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
			.thenAnswer(inv -> { latch.countDown(); return null; });
		when(mockDataQueue.count()).thenReturn(queueCount);
		when(mockDataQueue.peek(EdgeConstants.Defaults.MAX_BATCH_SIZE)).thenReturn(batch);
		when(mockProcessor.processBatch(any())).thenReturn(BatchOutcome.done(EdgeConstants.Defaults.MAX_BATCH_SIZE));

		hitQueue = new EdgeBatchingHitQueue(mockDataQueue, mockProcessor, executor);
		hitQueue.beginProcessing();

		latch.await(2, TimeUnit.SECONDS);

		verify(mockDataQueue, times(1)).peek(EdgeConstants.Defaults.MAX_BATCH_SIZE);
		verify(mockDataQueue, never()).peek(queueCount);
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
	// Helpers
	// -------------------------------------------------------------------------

	private DataEntity buildEntity(final boolean batchingEnabled) {
		Map<String, Object> config = new HashMap<>(edgeConfig);
		if (batchingEnabled) {
			config.put(EdgeConstants.SharedState.Configuration.EDGE_BATCHING_ENABLED, true);
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
		List<DataEntity> list = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			list.add(buildEntity(batchingEnabled));
		}
		return list;
	}
}
