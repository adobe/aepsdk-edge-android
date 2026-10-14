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
		data.put("status", status);
		if (reason != null) {
			data.put("reason", reason);
		}
		if (httpStatus != null) {
			data.put("httpStatus", httpStatus);
		}
		return new Event.Builder(
			"Device Attributes Response",
			EdgeConstants.EventType.EDGE_BYPASS_CONSENT,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.chainToParentEvent(request)
			.build();
	}

	static Event createDropResult(final String requestEventId) {
		final Map<String, Object> data = new HashMap<>();
		data.put("status", "dropped");
		data.put("reason", "invalidPayload");
		data.put("requestEventId", requestEventId);
		return new Event.Builder(
			"Device Attributes Response",
			EdgeConstants.EventType.EDGE_BYPASS_CONSENT,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(data)
			.build();
	}
}
