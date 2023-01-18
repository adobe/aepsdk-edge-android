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

	private final EdgeNetworkService.Retry shouldRetry;
	private int retryIntervalSeconds = EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;

	/**
	 * Constructs a {@link RetryResult} with the specified retry value and default retry interval of 5 seconds.
	 *
	 * @param shouldRetry value indicating if the hit should be retried
	 */
	RetryResult(final EdgeNetworkService.Retry shouldRetry) {
		this.shouldRetry = shouldRetry;
	}

	/**
	 * Constructs a {@link RetryResult} with the specified retry value and retry interval.
	 *
	 * @param shouldRetry value indicating if the hit should be retried
	 * @param retryIntervalSeconds value in seconds indicating the retry interval
	 */
	RetryResult(final EdgeNetworkService.Retry shouldRetry, final int retryIntervalSeconds) {
		this.shouldRetry = shouldRetry;
		this.retryIntervalSeconds =
			retryIntervalSeconds > 0 ? retryIntervalSeconds : EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
	}

	/**
	 * Gets the value determining if this hit should be retried.
	 *
	 * @return An EdgeNetworkService.Retry value determining if the hit should be retried
	 */
	public EdgeNetworkService.Retry getShouldRetry() {
		return shouldRetry;
	}

	/**
	 * Gets the retry interval in seconds.
	 *
	 * @return a int representing the amount of time that should pass before retrying the request
	 */
	public int getRetryIntervalSeconds() {
		return retryIntervalSeconds;
	}
}
