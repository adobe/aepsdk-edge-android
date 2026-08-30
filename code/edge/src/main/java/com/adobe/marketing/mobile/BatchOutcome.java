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

/**
 * Result returned by {@link EdgeHitProcessor#processBatch} to tell
 * {@link EdgeBatchingHitQueue} how to advance the queue after a batch attempt. Mirrors
 * {@code aepsdk-edge-ios}'s {@code BatchOutcome}.
 *
 * <p>Every outcome carries a {@code removeCount} (head entities safe to dequeue) and a
 * {@code retryDelaySeconds} (0 = process the next cycle immediately). {@link EdgeBatchingHitQueue}
 * applies both uniformly — remove the resolved prefix, then either retry after the delay or continue
 * — so the three {@link Kind}s differ only in which of these are non-zero. {@link #getKind()} remains
 * available for logging.
 */
class BatchOutcome {

	/** What happened; determines which of {@code removeCount} / {@code retryDelaySeconds} are set. */
	enum Kind {
		/** {@code removeCount} = entities resolved from the head; {@code retryDelaySeconds} = 0. */
		DONE,
		/** {@code removeCount} = 0 (whole batch stays queued); {@code retryDelaySeconds} = wait before retry. */
		RETRY,
		/** {@code removeCount} = resolved head prefix; {@code retryDelaySeconds} = wait before the next entity. */
		PARTIAL_REMOVE,
	}

	private final Kind kind;
	private final int removeCount;
	private final int retryDelaySeconds;

	private BatchOutcome(final Kind kind, final int removeCount, final int retryDelaySeconds) {
		this.kind = kind;
		this.removeCount = removeCount;
		this.retryDelaySeconds = retryDelaySeconds;
	}

	/**
	 * The first {@code resolvedCount} entities (from the head) were resolved (delivered, dropped with
	 * error, or ingested). Remove exactly that many and process the next cycle immediately — {@code
	 * processBatch} may have been given a larger peeked window than it acted on (truncation at a
	 * Consent/Reset/decode-failure/allowlist/config-mismatch boundary, or a single non-batchable head
	 * processed alone); anything beyond the resolved prefix was never sent and must stay queued for the
	 * next cycle.
	 *
	 * @param resolvedCount number of head entities safe to remove; never more than the window size.
	 */
	static BatchOutcome done(final int resolvedCount) {
		return new BatchOutcome(Kind.DONE, resolvedCount, 0);
	}

	/**
	 * A recoverable network error occurred; nothing was ingested. Remove nothing (the whole batch
	 * stays queued) and retry after {@code retryDelaySeconds}.
	 */
	static BatchOutcome retryBatch(final int retryDelaySeconds) {
		return new BatchOutcome(Kind.RETRY, 0, retryDelaySeconds);
	}

	/**
	 * A batch 400 was exploded into individual resends (see {@link EdgeHitProcessor#explodeAndResend}):
	 * the first {@code resolvedHeadCount} entities resolved individually, then a recoverable failure
	 * stopped the drain. Remove the resolved prefix and schedule the next cycle after
	 * {@code retryDelaySeconds} so the next (failed) entity is retried rather than skipped.
	 *
	 * @param resolvedHeadCount number of head entities that resolved individually and are safe to remove
	 * @param retryDelaySeconds seconds to wait before retrying the next unresolved entity
	 */
	static BatchOutcome partialRemove(final int resolvedHeadCount, final int retryDelaySeconds) {
		return new BatchOutcome(Kind.PARTIAL_REMOVE, resolvedHeadCount, retryDelaySeconds);
	}

	/** Which case this outcome represents; retained for logging. */
	Kind getKind() {
		return kind;
	}

	/** Number of entities to remove from the head of the queue after this attempt (0 for {@link Kind#RETRY}). */
	int getRemoveCount() {
		return removeCount;
	}

	/** Seconds to wait before the next processing cycle; 0 means continue immediately. */
	int getRetryDelaySeconds() {
		return retryDelaySeconds;
	}
}
