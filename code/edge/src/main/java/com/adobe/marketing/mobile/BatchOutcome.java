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
		/** All entities in the batch were resolved (delivered, dropped with error, or ingested).
		 *  Remove the whole batch from the queue. */
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

	static BatchOutcome done() {
		return new BatchOutcome(Kind.DONE, 0, 0);
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
