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

/**
 * Result returned by {@link EdgeHitProcessor#processBatch} to tell
 * {@link EdgeBatchingHitQueue} how to advance the queue after a batch attempt.
 */
class BatchOutcome {

	enum Kind {
		/** The first {@link #resolvedHeadCount} entities (from the head) were resolved (delivered,
		 *  dropped with error, or ingested). Remove exactly that many from the queue — {@code
		 *  processBatch} may have been given a larger peeked window than it actually resolved (e.g. a
		 *  window truncated at a Consent/Reset/decode-failure boundary, or a single non-ExperienceEvent
		 *  head processed alone); only the resolved prefix is safe to dequeue. Anything beyond it was
		 *  never sent and must stay queued for the next cycle. */
		DONE,

		/** A recoverable network error occurred; nothing was ingested.
		 *  Leave the entire batch in the queue and retry after {@link #retryAfterSeconds}. */
		RETRY_BATCH,

		/** A 400 explosion partially resolved the batch from the head.
		 *  Remove the first {@link #resolvedHeadCount} entities; leave the rest for the next cycle. */
		PARTIAL_REMOVE
	}

	private final Kind kind;
	private final int retryAfterSeconds;
	private final int resolvedHeadCount;

	private BatchOutcome(final Kind kind, final int retryAfterSeconds, final int resolvedHeadCount) {
		this.kind = kind;
		this.retryAfterSeconds = retryAfterSeconds;
		this.resolvedHeadCount = resolvedHeadCount;
	}

	/**
	 * @param resolvedCount the number of entities, counted from the head of the window {@code
	 *     processBatch} was given, that were actually resolved (sent/dropped) and are therefore safe
	 *     to remove from the queue. Must never exceed the size of the window passed to {@code
	 *     processBatch} — pass the count of entities actually acted upon, not the full peeked window,
	 *     whenever the two can differ (truncation, single-entity delegation, decode failure).
	 */
	static BatchOutcome done(final int resolvedCount) {
		return new BatchOutcome(Kind.DONE, 0, resolvedCount);
	}

	static BatchOutcome retryBatch(final int retryAfterSeconds) {
		return new BatchOutcome(Kind.RETRY_BATCH, retryAfterSeconds, 0);
	}

	static BatchOutcome partialRemove(final int resolvedHeadCount) {
		return new BatchOutcome(Kind.PARTIAL_REMOVE, 0, resolvedHeadCount);
	}

	/**
	 * Variant used when the failed entity at the new queue head needs a retry delay before
	 * the next cycle — e.g. after partial explosion where the next entity returned a recoverable error.
	 */
	static BatchOutcome partialRemove(final int resolvedHeadCount, final int retryAfterSeconds) {
		return new BatchOutcome(Kind.PARTIAL_REMOVE, retryAfterSeconds, resolvedHeadCount);
	}

	Kind getKind() {
		return kind;
	}

	int getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

	int getResolvedHeadCount() {
		return resolvedHeadCount;
	}
}
