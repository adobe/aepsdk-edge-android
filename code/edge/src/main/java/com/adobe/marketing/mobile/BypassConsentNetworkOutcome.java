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
 * Terminal or retry outcome of a device-attributes (bypass consent) network request.
 * Only the HTTP status is considered; the response body is never processed.
 */
final class BypassConsentNetworkOutcome {

	enum Type {
		SUCCESS,
		FAILURE,
		RETRY,
	}

	private final Type type;
	private final Integer httpStatus;
	private final int retryIntervalSeconds;

	private BypassConsentNetworkOutcome(
		@NonNull final Type type,
		@Nullable final Integer httpStatus,
		final int retryIntervalSeconds
	) {
		this.type = type;
		this.httpStatus = httpStatus;
		this.retryIntervalSeconds = retryIntervalSeconds;
	}

	static BypassConsentNetworkOutcome success(final int httpStatus) {
		return new BypassConsentNetworkOutcome(Type.SUCCESS, httpStatus, 0);
	}

	static BypassConsentNetworkOutcome failure(@Nullable final Integer httpStatus) {
		return new BypassConsentNetworkOutcome(Type.FAILURE, httpStatus, 0);
	}

	static BypassConsentNetworkOutcome retry(final int retryIntervalSeconds) {
		return new BypassConsentNetworkOutcome(
			Type.RETRY,
			null,
			retryIntervalSeconds > 0 ? retryIntervalSeconds : EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS
		);
	}

	@NonNull Type getType() {
		return type;
	}

	@Nullable Integer getHttpStatus() {
		return httpStatus;
	}

	int getRetryIntervalSeconds() {
		return retryIntervalSeconds;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (!(o instanceof BypassConsentNetworkOutcome)) return false;
		final BypassConsentNetworkOutcome that = (BypassConsentNetworkOutcome) o;
		return (
			type == that.type &&
			retryIntervalSeconds == that.retryIntervalSeconds &&
			(httpStatus == null ? that.httpStatus == null : httpStatus.equals(that.httpStatus))
		);
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + (httpStatus != null ? httpStatus.hashCode() : 0);
		result = 31 * result + retryIntervalSeconds;
		return result;
	}

	@NonNull @Override
	public String toString() {
		return type + "(httpStatus=" + httpStatus + ", retryIntervalSeconds=" + retryIntervalSeconds + ")";
	}
}
