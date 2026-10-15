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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.HitProcessing;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.Log;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Single-hit persistent queue whose acknowledgments cannot remove hits queued after an identity reset.
 */
class BypassConsentHitQueue extends HitQueuing {

	private static final String LOG_SOURCE = "BypassConsentHitQueue";

	final HitProcessing processor;
	private final DataQueue dataQueue;
	private final Object lock = new Object();
	private final ScheduledExecutorService queue;
	private boolean suspended = true;
	private boolean closed = false;
	private long generation = 0;
	private String activeAttempt;
	private ScheduledFuture<?> retryTask;

	private static final class Attempt {

		final String id;
		final long generation;
		final DataEntity entity;

		Attempt(final String id, final long generation, final DataEntity entity) {
			this.id = id;
			this.generation = generation;
			this.entity = entity;
		}
	}

	BypassConsentHitQueue(@NonNull final DataQueue dataQueue, @NonNull final HitProcessing processor) {
		this(dataQueue, processor, Executors.newSingleThreadScheduledExecutor());
	}

	@VisibleForTesting
	BypassConsentHitQueue(
		@NonNull final DataQueue dataQueue,
		@NonNull final HitProcessing processor,
		@NonNull final ScheduledExecutorService queue
	) {
		this.dataQueue = dataQueue;
		this.processor = processor;
		this.queue = queue;
	}

	@Override
	public boolean queue(final DataEntity entity) {
		synchronized (lock) {
			if (closed) {
				Log.error(LOG_TAG, LOG_SOURCE, "Cannot enqueue device attributes on a closed queue.");
				return false;
			}
			final boolean added = dataQueue.add(entity);
			processNextHit();
			return added;
		}
	}

	@Override
	public void beginProcessing() {
		synchronized (lock) {
			if (closed) {
				Log.error(LOG_TAG, LOG_SOURCE, "Cannot start a closed device attributes queue.");
				return;
			}
			suspended = false;
			processNextHit();
		}
	}

	@Override
	public void suspend() {
		synchronized (lock) {
			suspended = true;
		}
	}

	@Override
	public void clear() {
		synchronized (lock) {
			if (!dataQueue.clear()) {
				Log.error(LOG_TAG, LOG_SOURCE, "Failed to clear device attributes persistence. Processing is paused.");
				suspended = true;
				return;
			}
			generation++;
			if (retryTask != null) {
				retryTask.cancel(false);
				retryTask = null;
				activeAttempt = null;
			}
			processNextHit();
		}
	}

	@Override
	public int count() {
		synchronized (lock) {
			return dataQueue.count();
		}
	}

	@Override
	public void close() {
		synchronized (lock) {
			closed = true;
			suspended = true;
			generation++;
			if (retryTask != null) {
				retryTask.cancel(false);
			}
			retryTask = null;
			activeAttempt = null;
			dataQueue.close();
		}
		queue.shutdown();
	}

	private Attempt nextAttempt() {
		synchronized (lock) {
			if (suspended || closed || activeAttempt != null) {
				return null;
			}
			final DataEntity entity = dataQueue.peek();
			if (entity == null) {
				return null;
			}
			final Attempt attempt = new Attempt(UUID.randomUUID().toString(), generation, entity);
			activeAttempt = attempt.id;
			return attempt;
		}
	}

	private void processNextHit() {
		execute(() -> {
			final Attempt attempt = nextAttempt();
			if (attempt == null) {
				return;
			}
			processor.processHit(attempt.entity, finished -> handleCompletion(finished, attempt));
		});
	}

	private void handleCompletion(final boolean finished, final Attempt attempt) {
		execute(() -> {
			synchronized (lock) {
				if (closed) {
					return;
				}
				if (!attempt.id.equals(activeAttempt) || retryTask != null) {
					Log.warning(
						LOG_TAG,
						LOG_SOURCE,
						"Ignoring duplicate or abandoned device attributes queue completion."
					);
					return;
				}
				// A reset has already removed this hit. Do not remove or delay the new queue head.
				if (generation != attempt.generation) {
					activeAttempt = null;
					processNextHit();
					return;
				}

				if (finished) {
					final DataEntity head = dataQueue.peek();
					if (
						head != null &&
						attempt.entity.getUniqueIdentifier().equals(head.getUniqueIdentifier()) &&
						!dataQueue.remove()
					) {
						Log.error(
							LOG_TAG,
							LOG_SOURCE,
							"Failed to remove a device attributes hit from persistence. Processing is paused."
						);
						suspended = true;
					}
					activeAttempt = null;
					processNextHit();
				} else {
					final Runnable task = () -> {
						synchronized (lock) {
							if (generation != attempt.generation || !attempt.id.equals(activeAttempt)) {
								return;
							}
							retryTask = null;
							activeAttempt = null;
							processNextHit();
						}
					};
					try {
						retryTask =
							queue.schedule(
								task,
								Math.max(0, processor.retryInterval(attempt.entity)),
								TimeUnit.SECONDS
							);
					} catch (final RejectedExecutionException e) {
						Log.debug(LOG_TAG, LOG_SOURCE, "Device attributes queue is shut down; retry not scheduled.");
					}
				}
			}
		});
	}

	private void execute(final Runnable runnable) {
		try {
			queue.execute(runnable);
		} catch (final RejectedExecutionException e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Device attributes queue is shut down; ignoring work.");
		}
	}
}
