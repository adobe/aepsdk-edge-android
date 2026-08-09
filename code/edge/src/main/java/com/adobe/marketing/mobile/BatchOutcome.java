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
 * {@link EdgeBatchingHitQueue} how to advance the queue after a batch attempt.
 *
 * <p>Every outcome is exactly one {@link Kind} plus the single integer that kind carries — never a
 * combination, so there is no risk of reading the wrong field for the current outcome.
 * {@link EdgeBatchingHitQueue} switches on {@link #getKind()} to act on it.
 */
class BatchOutcome {

	/** What happened, and what {@link BatchOutcome#getValue()} means for it. */
	enum Kind {
		/** {@code value} = number of entities resolved from the head of the queue; safe to remove. */
		DONE,
		/** {@code value} = seconds to wait before the next cycle; nothing removed. */
		RETRY,
		/** {@code value} = number of entities to drain one at a time via forced batch-size-1. */
		EXPLODE,
	}

	private final Kind kind;
	private final int value;

	private BatchOutcome(final Kind kind, final int value) {
		this.kind = kind;
		this.value = value;
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
		return new BatchOutcome(Kind.DONE, resolvedCount);
	}

	/**
	 * A recoverable network error occurred; nothing was ingested. Remove nothing (the whole batch
	 * stays queued) and retry after {@code retryDelaySeconds}.
	 */
	static BatchOutcome retryBatch(final int retryDelaySeconds) {
		return new BatchOutcome(Kind.RETRY, retryDelaySeconds);
	}

	/**
	 * A batch of {@code count} events got a 400 — nothing was ingested. Remove nothing yet; instead
	 * tell the queue to drain exactly these {@code count} entities one at a time (via the ordinary
	 * single-event path) before resuming normal batch-sized peeking. See
	 *
	 * @param count number of entities in the batch that must now be drained individually
	 */
	static BatchOutcome explode(final int count) {
		return new BatchOutcome(Kind.EXPLODE, count);
	}

	/** Which case this outcome represents; determines what {@link #getValue()} means. */
	Kind getKind() {
		return kind;
	}

	/** The single integer this outcome carries; see {@link Kind} for what it means per case. */
	int getValue() {
		return value;
	}

	/** Number of entities to remove from the head of the queue after this attempt; 0 unless {@link Kind#DONE}. */
	int getRemoveCount() {
		return kind == Kind.DONE ? value : 0;
	}

	/** Seconds to wait before the next processing cycle; 0 unless {@link Kind#RETRY}. */
	int getRetryDelaySeconds() {
		return kind == Kind.RETRY ? value : 0;
	}

	/**
	 * Number of entities that must be drained one at a time before batch-sized peeking resumes; 0
	 * unless {@link Kind#EXPLODE}.
	 */
	int getExplodeCount() {
		return kind == Kind.EXPLODE ? value : 0;
	}
}
