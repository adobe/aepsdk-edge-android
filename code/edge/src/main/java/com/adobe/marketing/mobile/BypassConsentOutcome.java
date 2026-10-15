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

import java.util.HashMap;
import java.util.Map;

final class BypassConsentOutcome {

	private BypassConsentOutcome() {}

	static Event createResult(final Event request, final String status, final String reason, final Integer httpStatus) {
		final Map<String, Object> data = new HashMap<>();
		data.put(EdgeConstants.EventDataKeys.BypassResult.STATUS, status);
		if (reason != null) {
			data.put(EdgeConstants.EventDataKeys.BypassResult.REASON, reason);
		}
		if (httpStatus != null) {
			data.put(EdgeConstants.EventDataKeys.BypassResult.HTTP_STATUS, httpStatus);
		}
		return new Event.Builder(
			EdgeConstants.EventName.BYPASS_RESULT,
			EdgeConstants.EventType.EDGE_BYPASS_CONSENT,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.chainToParentEvent(request)
			.build();
	}

	/**
	 * A corrupt hit has no usable Event; its persisted identifier still identifies the request to drop.
	 */
	static Event createDropResult(final String requestEventId) {
		final Map<String, Object> data = new HashMap<>();
		data.put(EdgeConstants.EventDataKeys.BypassResult.STATUS, EdgeConstants.BypassResult.Status.DROPPED);
		data.put(EdgeConstants.EventDataKeys.BypassResult.REASON, EdgeConstants.BypassResult.Reason.INVALID_PAYLOAD);
		data.put(EdgeConstants.EventDataKeys.BypassResult.REQUEST_EVENT_ID, requestEventId);
		return new Event.Builder(
			EdgeConstants.EventName.BYPASS_RESULT,
			EdgeConstants.EventType.EDGE_BYPASS_CONSENT,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.build();
	}
}
