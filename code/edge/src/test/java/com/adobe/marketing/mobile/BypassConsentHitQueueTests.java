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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.HitProcessing;
import com.adobe.marketing.mobile.services.HitProcessingResult;
import com.adobe.marketing.mobile.services.HitQueuing;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BypassConsentHitQueueTests {

	private static final long TIMEOUT_MS = 2000;

	private ControlledBypassDataQueue dataQueue;
	private DeferredBypassProcessor processor;
	private HitQueuing hitQueue;

	@Before
	public void setUp() {
		dataQueue = new ControlledBypassDataQueue();
		processor = new DeferredBypassProcessor();
		hitQueue = new BypassConsentHitQueue(dataQueue, processor);
	}

	@After
	public void tearDown() {
		hitQueue.close();
		processor.onProcess = null;
		processor.onRetry = null;
	}

	@Test
	public void testTerminalCompletionAdvancesToNextHit() throws Exception {
		final CountDownLatch processed = new CountDownLatch(2);
		processor.onProcess =
			entity -> {
				processor.complete(entity.getUniqueIdentifier(), true);
				processed.countDown();
			};

		assertTrue(hitQueue.queue(entity("first")));
		assertTrue(hitQueue.queue(entity("second")));
		hitQueue.beginProcessing();

		await(processed);
		waitUntilEmpty();
		assertEquals(Arrays.asList("first", "second"), processor.processedIds());
	}

	@Test
	public void testRetryRetainsHeadUntilLaterTerminalCompletion() throws Exception {
		final CountDownLatch retried = new CountDownLatch(3);
		processor.onProcess =
			entity -> {
				final boolean retry = processor.processedIds().size() == 1;
				processor.complete(entity.getUniqueIdentifier(), !retry);
				retried.countDown();
			};

		hitQueue.queue(entity("first"));
		hitQueue.queue(entity("second"));
		hitQueue.beginProcessing();

		await(retried);
		waitUntilEmpty();
		assertEquals(Arrays.asList("first", "first", "second"), processor.processedIds());
	}

	@Test
	public void testResetDuringInFlightRequestDoesNotRemoveNewHead() throws Exception {
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch newStarted = new CountDownLatch(1);
		processor.onProcess =
			entity -> {
				if ("old".equals(entity.getUniqueIdentifier())) {
					started.countDown();
				} else {
					newStarted.countDown();
					processor.complete(entity.getUniqueIdentifier(), true);
				}
			};

		hitQueue.queue(entity("old"));
		hitQueue.beginProcessing();
		await(started);

		hitQueue.clear();
		assertEquals(0, hitQueue.count());
		hitQueue.queue(entity("new"));
		processor.complete("old", true);

		await(newStarted);
		waitUntilEmpty();
		assertEquals(Arrays.asList("old", "new"), processor.processedIds());
	}

	@Test
	public void testResetDuringInFlightRetryDoesNotDelayNewHead() throws Exception {
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch newStarted = new CountDownLatch(1);
		processor.retryDelay = 60;
		processor.onProcess =
			entity -> {
				if ("old".equals(entity.getUniqueIdentifier())) {
					started.countDown();
				} else {
					newStarted.countDown();
					processor.complete(entity.getUniqueIdentifier(), true);
				}
			};

		hitQueue.queue(entity("old"));
		hitQueue.beginProcessing();
		await(started);

		hitQueue.clear();
		hitQueue.queue(entity("new"));
		processor.complete("old", false);

		await(newStarted);
		waitUntilEmpty();
		assertEquals(Arrays.asList("old", "new"), processor.processedIds());
	}

	@Test
	public void testResetWhileWaitingToRetryCancelsOldDelay() throws Exception {
		final CountDownLatch retryScheduled = new CountDownLatch(1);
		final CountDownLatch newStarted = new CountDownLatch(1);
		processor.retryDelay = 60;
		processor.onRetry = retryScheduled::countDown;
		processor.onProcess =
			entity -> {
				if ("old".equals(entity.getUniqueIdentifier())) {
					processor.complete("old", false);
				} else {
					newStarted.countDown();
					processor.complete(entity.getUniqueIdentifier(), true);
				}
			};

		hitQueue.queue(entity("old"));
		hitQueue.beginProcessing();
		await(retryScheduled);

		hitQueue.clear();
		hitQueue.queue(entity("new"));

		await(newStarted);
		waitUntilEmpty();
		assertEquals(Arrays.asList("old", "new"), processor.processedIds());
	}

	@Test
	public void testResetWithEmptyQueueAllowsLaterRequests() throws Exception {
		final CountDownLatch started = new CountDownLatch(1);
		processor.onProcess = entity -> started.countDown();

		hitQueue.queue(entity("old"));
		hitQueue.beginProcessing();
		await(started);

		hitQueue.suspend();
		hitQueue.clear();
		processor.complete("old", true);

		final CountDownLatch newStarted = new CountDownLatch(1);
		processor.onProcess =
			entity -> {
				newStarted.countDown();
				processor.complete(entity.getUniqueIdentifier(), true);
			};
		hitQueue.queue(entity("new"));
		hitQueue.beginProcessing();

		await(newStarted);
		waitUntilEmpty();
		assertEquals(Arrays.asList("old", "new"), processor.processedIds());
	}

	@Test
	public void testDuplicateCompletionDoesNotRemoveNextHit() throws Exception {
		final CountDownLatch oldStarted = new CountDownLatch(1);
		final CountDownLatch newStarted = new CountDownLatch(1);
		processor.onProcess =
			entity -> ("first".equals(entity.getUniqueIdentifier()) ? oldStarted : newStarted).countDown();

		hitQueue.queue(entity("first"));
		hitQueue.queue(entity("second"));
		hitQueue.beginProcessing();
		await(oldStarted);

		processor.complete("first", true);
		await(newStarted);
		processor.complete("first", true);

		waitUntil(() -> hitQueue.count() == 1);
		Thread.sleep(100);
		assertEquals(1, hitQueue.count());

		processor.complete("second", true);
		waitUntilEmpty();
		assertEquals(Arrays.asList("first", "second"), processor.processedIds());
	}

	@Test
	public void testSuspendStopsNextHitUntilResumed() throws Exception {
		final CountDownLatch started = new CountDownLatch(1);
		final CountDownLatch next = new CountDownLatch(1);
		processor.onProcess = entity -> ("first".equals(entity.getUniqueIdentifier()) ? started : next).countDown();

		hitQueue.queue(entity("first"));
		hitQueue.queue(entity("second"));
		hitQueue.beginProcessing();
		await(started);

		hitQueue.suspend();
		processor.complete("first", true);
		waitUntil(() -> hitQueue.count() == 1);
		Thread.sleep(100);
		assertEquals(Collections.singletonList("first"), processor.processedIds());

		hitQueue.beginProcessing();
		await(next);
		processor.complete("second", true);
		waitUntilEmpty();
	}

	@Test
	public void testPersistenceRemovalFailurePausesUntilExplicitResume() throws Exception {
		final CountDownLatch failedRemoval = new CountDownLatch(1);
		dataQueue.removeSucceeds = false;
		dataQueue.onRemove = failedRemoval::countDown;
		processor.onProcess = entity -> processor.complete(entity.getUniqueIdentifier(), true);

		hitQueue.queue(entity("first"));
		hitQueue.beginProcessing();
		await(failedRemoval);
		Thread.sleep(100);

		assertEquals(1, hitQueue.count());
		assertEquals(Collections.singletonList("first"), processor.processedIds());

		dataQueue.onRemove = null;
		dataQueue.removeSucceeds = true;
		hitQueue.beginProcessing();
		waitUntilEmpty();
		assertEquals(Arrays.asList("first", "first"), processor.processedIds());
	}

	@Test
	public void testFailedClearPausesPendingOldIdentityHits() throws Exception {
		final CountDownLatch started = new CountDownLatch(1);
		processor.onProcess = entity -> started.countDown();

		hitQueue.queue(entity("first"));
		hitQueue.queue(entity("old-pending"));
		hitQueue.beginProcessing();
		await(started);

		dataQueue.clearSucceeds = false;
		hitQueue.clear();
		processor.complete("first", true);

		waitUntil(() -> hitQueue.count() == 1);
		Thread.sleep(100);
		assertEquals(Collections.singletonList("first"), processor.processedIds());
	}

	@Test
	public void testClosedQueueRejectsNewHitsAndLateCompletion() throws Exception {
		final CountDownLatch started = new CountDownLatch(1);
		processor.onProcess = entity -> started.countDown();

		hitQueue.queue(entity("first"));
		hitQueue.beginProcessing();
		await(started);

		hitQueue.close();
		assertFalse(hitQueue.queue(entity("new")));
		processor.complete("first", true);
		Thread.sleep(100);

		assertEquals(Collections.singletonList("first"), processor.processedIds());
	}

	private static DataEntity entity(final String id) {
		return new DataEntity(id, new Date(), "{}");
	}

	private static void await(final CountDownLatch latch) throws InterruptedException {
		assertTrue("Timed out waiting for queue processing", latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
	}

	private void waitUntilEmpty() throws InterruptedException {
		waitUntil(() -> hitQueue.count() == 0);
	}

	private static void waitUntil(final BooleanSupplier condition) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + TIMEOUT_MS;
		while (!condition.getAsBoolean()) {
			if (System.currentTimeMillis() > deadline) {
				fail("Timed out waiting for condition");
			}
			Thread.sleep(10);
		}
	}

	private static class DeferredBypassProcessor implements HitProcessing {

		private final List<String> processedIds = new CopyOnWriteArrayList<>();
		private final Map<String, HitProcessingResult> completions = new ConcurrentHashMap<>();
		volatile Consumer<DataEntity> onProcess;
		volatile Runnable onRetry;
		volatile int retryDelay = 0;

		@Override
		public int retryInterval(@NonNull final DataEntity entity) {
			final Runnable retry = onRetry;
			if (retry != null) {
				retry.run();
			}
			return retryDelay;
		}

		@Override
		public void processHit(@NonNull final DataEntity entity, @NonNull final HitProcessingResult processingResult) {
			processedIds.add(entity.getUniqueIdentifier());
			completions.put(entity.getUniqueIdentifier(), processingResult);
			final Consumer<DataEntity> process = onProcess;
			if (process != null) {
				process.accept(entity);
			}
		}

		void complete(final String id, final boolean finished) {
			final HitProcessingResult completion = completions.get(id);
			if (completion == null) {
				fail("No pending completion for " + id);
				return;
			}
			completion.complete(finished);
		}

		List<String> processedIds() {
			return new ArrayList<>(processedIds);
		}
	}

	private static class ControlledBypassDataQueue implements DataQueue {

		private final LinkedList<DataEntity> storage = new LinkedList<>();
		volatile boolean removeSucceeds = true;
		volatile boolean clearSucceeds = true;
		volatile Runnable onRemove;

		@Override
		public synchronized boolean add(final DataEntity dataEntity) {
			return storage.add(dataEntity);
		}

		@Override
		public synchronized DataEntity peek() {
			return storage.peekFirst();
		}

		@Override
		public synchronized List<DataEntity> peek(final int n) {
			return new ArrayList<>(storage.subList(0, Math.min(n, storage.size())));
		}

		@Override
		public boolean remove() {
			final boolean removed;
			synchronized (this) {
				removed = removeSucceeds && storage.pollFirst() != null;
			}
			final Runnable callback = onRemove;
			if (callback != null) {
				callback.run();
			}
			return removed;
		}

		@Override
		public synchronized boolean remove(final int n) {
			if (!removeSucceeds) {
				return false;
			}
			for (int i = 0; i < n && !storage.isEmpty(); i++) {
				storage.pollFirst();
			}
			return true;
		}

		@Override
		public synchronized boolean clear() {
			if (!clearSucceeds) {
				return false;
			}
			storage.clear();
			return true;
		}

		@Override
		public synchronized int count() {
			return storage.size();
		}

		@Override
		public void close() {}
	}
}
