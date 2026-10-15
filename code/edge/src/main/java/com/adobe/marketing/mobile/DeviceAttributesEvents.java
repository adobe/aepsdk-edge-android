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
import java.util.HashMap;
import java.util.Map;

/**
 * Event helpers for the consent-independent device attributes path.
 */
final class DeviceAttributesEvents {

	private DeviceAttributesEvents() {}

	/**
	 * Checks if the provided {@code event} is a device attributes request event (E3), which has the dedicated
	 * {@code edgeBypassConsent} type and the {@link EventSource#REQUEST_CONTENT} source. Such events are routed to
	 * the separate device attributes queue and are not subject to the collect consent gate.
	 *
	 * @param event current event to check
	 * @return true if the {@code event} is a device attributes request event
	 */
	static boolean isBypassConsentEvent(@NonNull final Event event) {
		return (
			DeviceAttributesConstants.EVENT_TYPE_EDGE_BYPASS_CONSENT.equalsIgnoreCase(event.getType()) &&
			EventSource.REQUEST_CONTENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Creates the device attributes result event (E4) chained to the request event (E3).
	 *
	 * @param requestEvent the device attributes request event (E3)
	 * @param status the result status
	 * @param reason the optional result reason
	 * @param httpStatus the optional HTTP status code
	 * @return the result event
	 */
	@NonNull static Event createResult(
		@NonNull final Event requestEvent,
		@NonNull final String status,
		@Nullable final String reason,
		@Nullable final Integer httpStatus
	) {
		final Map<String, Object> data = new HashMap<>();
		data.put(DeviceAttributesConstants.EventDataKeys.STATUS, status);

		if (reason != null) {
			data.put(DeviceAttributesConstants.EventDataKeys.REASON, reason);
		}

		if (httpStatus != null) {
			data.put(DeviceAttributesConstants.EventDataKeys.HTTP_STATUS, httpStatus);
		}

		return new Event.Builder(
			DeviceAttributesConstants.EVENT_NAME_RESULT,
			DeviceAttributesConstants.EVENT_TYPE_EDGE_BYPASS_CONSENT,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.chainToParentEvent(requestEvent)
			.build();
	}

	/**
	 * Creates the unchained result event (E4) for a stored hit that could not be decoded. A corrupt hit has no
	 * usable request event; its persisted identifier still identifies the request that was dropped.
	 *
	 * @param requestEventId the unique identifier of the stored hit
	 * @return the result event
	 */
	@NonNull static Event createDropResult(@NonNull final String requestEventId) {
		final Map<String, Object> data = new HashMap<>();
		data.put(DeviceAttributesConstants.EventDataKeys.STATUS, DeviceAttributesConstants.Status.DROPPED);
		data.put(DeviceAttributesConstants.EventDataKeys.REASON, DeviceAttributesConstants.Reason.INVALID_PAYLOAD);
		data.put(DeviceAttributesConstants.EventDataKeys.REQUEST_EVENT_ID, requestEventId);

		return new Event.Builder(
			DeviceAttributesConstants.EVENT_NAME_RESULT,
			DeviceAttributesConstants.EVENT_TYPE_EDGE_BYPASS_CONSENT,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.build();
	}
}
