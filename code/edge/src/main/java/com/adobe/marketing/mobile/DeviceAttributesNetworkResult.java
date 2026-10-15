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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Classified outcome of a device attributes network request.
 */
final class DeviceAttributesNetworkResult {

	enum Type {
		/** The request was accepted (HTTP 200 or 202). */
		SUCCESS,
		/** The request failed with a recoverable error and should be retried. */
		RETRY,
		/** The request failed with an unrecoverable error. */
		FAILED,
	}

	private final Type type;
	private final Integer httpStatus;
	private final String body;
	private final int retryIntervalSeconds;

	private DeviceAttributesNetworkResult(
		final Type type,
		final Integer httpStatus,
		final String body,
		final int retryIntervalSeconds
	) {
		this.type = type;
		this.httpStatus = httpStatus;
		this.body = body;
		this.retryIntervalSeconds = retryIntervalSeconds;
	}

	/**
	 * @param httpStatus the successful HTTP status code
	 * @param body the plain JSON response body, if any
	 * @return a {@link Type#SUCCESS} result
	 */
	static DeviceAttributesNetworkResult success(final int httpStatus, @Nullable final String body) {
		return new DeviceAttributesNetworkResult(Type.SUCCESS, httpStatus, body, 0);
	}

	/**
	 * @param retryIntervalSeconds the interval in seconds after which the request should be retried
	 * @return a {@link Type#RETRY} result
	 */
	static DeviceAttributesNetworkResult retry(final int retryIntervalSeconds) {
		return new DeviceAttributesNetworkResult(Type.RETRY, null, null, retryIntervalSeconds);
	}

	/**
	 * @param httpStatus the HTTP status code, or null when no response code was received
	 * @return a {@link Type#FAILED} result
	 */
	static DeviceAttributesNetworkResult failed(@Nullable final Integer httpStatus) {
		return new DeviceAttributesNetworkResult(Type.FAILED, httpStatus, null, 0);
	}

	@NonNull Type getType() {
		return type;
	}

	@Nullable Integer getHttpStatus() {
		return httpStatus;
	}

	@Nullable String getBody() {
		return body;
	}

	int getRetryIntervalSeconds() {
		return retryIntervalSeconds;
	}
}
