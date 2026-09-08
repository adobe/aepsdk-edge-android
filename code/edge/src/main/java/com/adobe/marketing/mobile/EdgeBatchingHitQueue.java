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

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import androidx.annotation.VisibleForTesting;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.Log;
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
 *   <li>batching enabled → window of {@code min(queue.count(), maxBatchSize)}, where
 *       {@code maxBatchSize} comes from {@code edge.batching.maxBatchSize} (falling back to
 *       {@link EdgeConstants.Defaults#MAX_BATCH_SIZE} if absent or non-positive, and clamped to
 *       {@link EdgeConstants.Defaults#MAX_BATCH_SIZE_LIMIT} regardless of source).
 * </ul>
 *
 * <p>A batch that gets a 400 is exploded into individual resends inside
 * {@link EdgeHitProcessor#processBatch}, which reports back only how many head entities to remove and
 * whether to retry — the queue itself has no draining state.
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
		// Honor a suspend() that landed after this cycle was scheduled but before it started. Without
		// this, a queue suspended (e.g. collect consent flipping to pending) could still send one more
		// request — and because this is the batch queue, that request could carry up to maxBatchSize
		// events rather than one. Cannot stop a send already in progress; this only closes the
		// scheduled-but-not-started window. Resumption happens via beginProcessing().
		if (suspended.get()) {
			isTaskScheduled.set(false);
			return;
		}

		// Peek head to determine effective batch size from snapshotted config.
		final DataEntity head = queue.peek();
		if (head == null) {
			isTaskScheduled.set(false);
			return;
		}

		final int batchSize = getEffectiveBatchSize(head);
		final List<DataEntity> entities = batchSize > 1 ? queue.peek(batchSize) : Collections.singletonList(head);

		final BatchOutcome outcome = processor.processBatch(entities);

		// Every outcome carries how many head entities are now safe to remove and how long to wait
		// before the next cycle; applying both uniformly covers all three kinds:
		//   DONE           — remove the resolved prefix (processBatch may have acted on fewer than the
		//                    peeked window: truncation at a Consent/Reset/decode/allowlist/config
		//                    boundary, or a single non-batchable head), continue immediately.
		//   RETRY          — remove nothing, retry the whole batch after the delay.
		//   PARTIAL_REMOVE — a 400 exploded into individual resends; remove the resolved prefix and
		//                    retry the next (failed) entity after the delay.
		final int removeCount = outcome.getRemoveCount();
		final int retryDelaySeconds = outcome.getRetryDelaySeconds();

		if (removeCount > 0) {
			queue.remove(removeCount);
		}

		if (retryDelaySeconds > 0) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Removed %d resolved entities; next cycle scheduled in %d seconds.",
				removeCount,
				retryDelaySeconds
			);
			scheduledExecutorService.schedule(
				() -> {
					isTaskScheduled.set(false);
					processNextBatch();
				},
				retryDelaySeconds,
				TimeUnit.SECONDS
			);
		} else {
			isTaskScheduled.set(false);
			processNextBatch();
		}
	}

	/**
	 * Returns the number of entities to include in the next batch, based on the {@code enabled} flag
	 * and {@code maxBatchSize} from the {@code edge.batching} configuration snapshotted in the head
	 * entity's configuration (parsed by {@link EdgeBatchingConfig}). {@code maxBatchSize} is clamped to
	 * a positive value no greater than {@link EdgeConstants.Defaults#MAX_BATCH_SIZE_LIMIT}, so a
	 * misconfigured value can't grow the batch (and the request payload) unbounded.
	 */
	private int getEffectiveBatchSize(final DataEntity head) {
		final EdgeDataEntity entity = EdgeDataEntity.fromDataEntity(head);
		if (entity == null) {
			return 1;
		}
		final EdgeBatchingConfig batchingConfig = EdgeBatchingConfig.from(entity.getConfiguration());
		if (!batchingConfig.isEnabled()) {
			return 1;
		}
		final int queueDepth = queue.count();
		return Math.min(queueDepth, batchingConfig.getMaxBatchSize());
	}
}
