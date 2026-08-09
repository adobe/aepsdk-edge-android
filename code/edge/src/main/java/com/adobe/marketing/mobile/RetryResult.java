/*
  Copyright 2021 Adobe. All rights reserved.
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
 * Represents the retry result of a network request specifying the retry interval and whether the request should be retried at all
 */
class RetryResult {

	private int retryIntervalSeconds = EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
	private final EdgeNetworkService.NetworkRequestOutcome networkRequestOutcome;
	private String responseBody = null;

	/**
	 * Constructs a {@link RetryResult} with the specified retry value and default retry interval of 5 seconds.
	 *
	 * @param shouldRetry value indicating if the hit should be retried
	 */
	RetryResult(final EdgeNetworkService.Retry shouldRetry) {
		this.networkRequestOutcome =
			shouldRetry == EdgeNetworkService.Retry.YES
				? EdgeNetworkService.NetworkRequestOutcome.RETRY
				: EdgeNetworkService.NetworkRequestOutcome.SUCCESS;
	}

	/**
	 * Constructs a {@link RetryResult} with the specified retry value and retry interval.
	 *
	 * @param shouldRetry value indicating if the hit should be retried
	 * @param retryIntervalSeconds value in seconds indicating the retry interval
	 */
	RetryResult(final EdgeNetworkService.Retry shouldRetry, final int retryIntervalSeconds) {
		this.retryIntervalSeconds =
			retryIntervalSeconds > 0 ? retryIntervalSeconds : EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
		this.networkRequestOutcome =
			shouldRetry == EdgeNetworkService.Retry.YES
				? EdgeNetworkService.NetworkRequestOutcome.RETRY
				: EdgeNetworkService.NetworkRequestOutcome.SUCCESS;
	}

	/**
	 * Constructs a {@link RetryResult} with an explicit {@link EdgeNetworkService.NetworkRequestOutcome},
	 * used by batch-aware callers that need finer-grained classification than retry/no-retry.
	 *
	 * @param outcome the granular outcome of the network attempt
	 * @param retryIntervalSeconds retry wait in seconds (only meaningful when outcome is RETRY)
	 */
	RetryResult(final EdgeNetworkService.NetworkRequestOutcome outcome, final int retryIntervalSeconds) {
		this.networkRequestOutcome = outcome;
		this.retryIntervalSeconds =
			retryIntervalSeconds > 0 ? retryIntervalSeconds : EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
	}

	/**
	 * Gets the value determining if this hit should be retried, derived from the
	 * {@link EdgeNetworkService.NetworkRequestOutcome}: {@code RETRY} maps to {@link EdgeNetworkService.Retry#YES},
	 * every other outcome to {@link EdgeNetworkService.Retry#NO}.
	 *
	 * @return An EdgeNetworkService.Retry value determining if the hit should be retried
	 */
	public EdgeNetworkService.Retry getShouldRetry() {
		return networkRequestOutcome == EdgeNetworkService.NetworkRequestOutcome.RETRY
			? EdgeNetworkService.Retry.YES
			: EdgeNetworkService.Retry.NO;
	}

	/**
	 * Gets the retry interval in seconds.
	 *
	 * @return a int representing the amount of time that should pass before retrying the request
	 */
	public int getRetryIntervalSeconds() {
		return retryIntervalSeconds;
	}

	/**
	 * Gets the granular network outcome, used by batch-aware processors to decide the next queue action.
	 *
	 * @return the {@link EdgeNetworkService.NetworkRequestOutcome} for this result
	 */
	public EdgeNetworkService.NetworkRequestOutcome getNetworkRequestOutcome() {
		return networkRequestOutcome;
	}

	/**
	 * Returns the server error body captured for a terminal 400 response, or {@code null} if none
	 * was captured. Only meaningful when {@link #getNetworkRequestOutcome()} is
	 * {@link EdgeNetworkService.NetworkRequestOutcome#EXPLODE_400}.
	 */
	String getResponseBody() {
		return responseBody;
	}

	void setResponseBody(final String responseBody) {
		this.responseBody = responseBody;
	}
}
