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
	 * Checks if the provided {@code event} is a device attributes request event, which has the dedicated
	 * {@code com.adobe.eventType.edgeDeviceAttributes} type and the {@link EventSource#REQUEST_CONTENT} source. Such events are routed to
	 * the separate device attributes queue and are not subject to collect consent.
	 *
	 * @param event current event to check
	 * @return true if the {@code event} is a device attributes request event
	 */
	static boolean isDeviceAttributesRequestEvent(@NonNull final Event event) {
		return (
			DeviceAttributesConstants.EVENT_TYPE.equalsIgnoreCase(event.getType()) &&
			EventSource.REQUEST_CONTENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Creates the device attributes response event, with the request event as its parent event.
	 *
	 * @param requestEvent the device attributes request event
	 * @param status the response status
	 * @param reason the optional response reason
	 * @param httpStatus the optional HTTP status code
	 * @return the response event
	 */
	@NonNull static Event createResponse(
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
			DeviceAttributesConstants.EVENT_NAME_RESPONSE,
			DeviceAttributesConstants.EVENT_TYPE,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.chainToParentEvent(requestEvent)
			.build();
	}

	/**
	 * Creates the device attributes response event for a stored hit that could not be decoded. It has no parent event
	 * because the request event is unavailable; the stored hit's identifier identifies the dropped request.
	 *
	 * @param requestEventId the unique identifier of the stored hit
	 * @return the response event
	 */
	@NonNull static Event createDroppedResponse(@NonNull final String requestEventId) {
		final Map<String, Object> data = new HashMap<>();
		data.put(DeviceAttributesConstants.EventDataKeys.STATUS, DeviceAttributesConstants.Status.DROPPED);
		data.put(DeviceAttributesConstants.EventDataKeys.REASON, DeviceAttributesConstants.Reason.INVALID_PAYLOAD);
		data.put(DeviceAttributesConstants.EventDataKeys.REQUEST_EVENT_ID, requestEventId);

		return new Event.Builder(
			DeviceAttributesConstants.EVENT_NAME_RESPONSE,
			DeviceAttributesConstants.EVENT_TYPE,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.build();
	}
}
