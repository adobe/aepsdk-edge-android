/*
  Copyright 2019 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import androidx.annotation.VisibleForTesting;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Batch-capable hit queue for the Edge extension.
 *
 * <p>Uses the same {@link DataQueue} persistence and {@link ScheduledExecutorService} pattern as
 * {@link com.adobe.marketing.mobile.services.PersistentHitQueue}, but processes a window of
 * entities per cycle when batching is enabled ({@code edge.batching.enabled = true}).
 *
 * <p>The effective batch size is determined at processing time from the head entity's snapshotted
 * configuration:
 * <ul>
 *   <li>batching disabled (default) → window of 1, identical to current single-event behaviour.
 *   <li>batching enabled → window of {@code min(queue.count(), MAX_BATCH_SIZE)}.
 * </ul>
 */
class EdgeBatchingHitQueue extends HitQueuing {

	private static final String LOG_SOURCE = "EdgeBatchingHitQueue";

	private final DataQueue queue;
	private final EdgeHitProcessor processor;
	private final AtomicBoolean suspended = new AtomicBoolean(true);
	private final AtomicBoolean isTaskScheduled = new AtomicBoolean(false);
	private final ScheduledExecutorService scheduledExecutorService;

	EdgeBatchingHitQueue(final DataQueue queue, final EdgeHitProcessor processor) {
		this(queue, processor, Executors.newSingleThreadScheduledExecutor());
	}

	@VisibleForTesting
	EdgeBatchingHitQueue(
		final DataQueue queue,
		final EdgeHitProcessor processor,
		final ScheduledExecutorService executorService
	) {
		if (queue == null || processor == null) {
			throw new IllegalArgumentException("Null value is not allowed in EdgeBatchingHitQueue constructor.");
		}
		this.queue = queue;
		this.processor = processor;
		this.scheduledExecutorService = executorService;
	}

	@Override
	public boolean queue(final DataEntity entity) {
		final boolean result = queue.add(entity);
		Log.debug(
			LOG_TAG,
			LOG_SOURCE,
			"Queued entity %s (added=%b); queue depth now %d.",
			entity != null ? entity.getUniqueIdentifier() : "null",
			result,
			queue.count()
		);
		processNextBatch();
		return result;
	}

	@Override
	public void beginProcessing() {
		suspended.set(false);
		processNextBatch();
	}

	@Override
	public void suspend() {
		suspended.set(true);
	}

	@Override
	public void clear() {
		queue.clear();
	}

	@Override
	public int count() {
		return queue.count();
	}

	@Override
	public void close() {
		suspend();
		queue.close();
		scheduledExecutorService.shutdown();
	}

	/**
	 * Schedules one batch-processing cycle if none is already running.
	 * Mirrors the guard in {@code PersistentHitQueue.processNextHit()}.
	 */
	private void processNextBatch() {
		if (suspended.get()) {
			return;
		}

		if (!isTaskScheduled.compareAndSet(false, true)) {
			return;
		}

		scheduledExecutorService.execute(this::runBatchCycle);
	}

	private void runBatchCycle() {
		// Peek head to determine effective batch size from snapshotted config.
		final DataEntity head = queue.peek();
		if (head == null) {
			isTaskScheduled.set(false);
			return;
		}

		final int batchSize = getEffectiveBatchSize(head);
		final List<DataEntity> entities = batchSize > 1 ? queue.peek(batchSize) : Collections.singletonList(head);

		final BatchOutcome outcome = processor.processBatch(entities);

		switch (outcome.getKind()) {
			case DONE:
				queue.remove(entities.size());
				isTaskScheduled.set(false);
				processNextBatch();
				break;

			case RETRY_BATCH:
				Log.trace(
					LOG_TAG,
					LOG_SOURCE,
					"Batch of %d will be retried in %d seconds.",
					entities.size(),
					outcome.getRetryAfterSeconds()
				);
				scheduledExecutorService.schedule(
					() -> {
						isTaskScheduled.set(false);
						processNextBatch();
					},
					outcome.getRetryAfterSeconds(),
					TimeUnit.SECONDS
				);
				break;

			case PARTIAL_REMOVE:
				final int resolved = outcome.getResolvedHeadCount();
				if (resolved > 0) {
					queue.remove(resolved);
				}
				final int partialDelay = outcome.getRetryAfterSeconds();
				if (partialDelay > 0) {
					Log.trace(
						LOG_TAG,
						LOG_SOURCE,
						"Partial removal of %d entities; next entity needs retry in %d seconds.",
						resolved,
						partialDelay
					);
					scheduledExecutorService.schedule(
						() -> {
							isTaskScheduled.set(false);
							processNextBatch();
						},
						partialDelay,
						TimeUnit.SECONDS
					);
				} else {
					isTaskScheduled.set(false);
					processNextBatch();
				}
				break;
		}
	}

	/**
	 * Returns the number of entities to include in the next batch, based on the
	 * {@code edge.batching.enabled} flag snapshotted in the head entity's configuration.
	 */
	private int getEffectiveBatchSize(final DataEntity head) {
		final EdgeDataEntity entity = EdgeDataEntity.fromDataEntity(head);
		if (entity == null) {
			return 1;
		}
		final boolean batchingEnabled = DataReader.optBoolean(
			entity.getConfiguration(),
			EdgeConstants.SharedState.Configuration.EDGE_BATCHING_ENABLED,
			false
		);
		final int queueDepth = queue.count();
		final int batchSize = batchingEnabled ? Math.min(queueDepth, EdgeConstants.Defaults.MAX_BATCH_SIZE) : 1;
		Log.debug(
			LOG_TAG,
			LOG_SOURCE,
			"Batch-size decision: batching=%b, queueDepth=%d → batchSize=%d.",
			batchingEnabled,
			queueDepth,
			batchSize
		);
		return batchSize;
	}
}
