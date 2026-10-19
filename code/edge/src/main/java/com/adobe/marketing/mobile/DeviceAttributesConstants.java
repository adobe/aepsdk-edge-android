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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Constants used only by the consent-independent device attributes path.
 */
final class DeviceAttributesConstants {

	/**
	 * Request (E3, {@code requestContent}) and response (E4, {@code responseContent}) event type for the
	 * device attributes path.
	 */
	static final String EVENT_TYPE = "com.adobe.eventType.edgeBypassConsent";

	/** Name of the device attributes response event (E4). */
	static final String EVENT_NAME_RESPONSE = "Device Attributes Response";

	/** Suffix appended to the Edge extension name to label the dedicated device attributes queue. */
	static final String DATA_QUEUE_SUFFIX = ".deviceAttributes";

	/** HTTP status codes treated as a successful device attributes request. */
	static final Set<Integer> SUCCESS_RESPONSE_CODES = Collections.unmodifiableSet(
		new HashSet<>(Arrays.asList(200, 202))
	);

	static final class EventDataKeys {

		static final String STATUS = "status";
		static final String REASON = "reason";
		static final String HTTP_STATUS = "httpStatus";
		static final String REQUEST_EVENT_ID = EdgeConstants.EventDataKeys.REQUEST_EVENT_ID;

		private EventDataKeys() {}
	}

	static final class Status {

		static final String SUCCESS = "success";
		static final String FAILED = "failed";
		static final String DROPPED = "dropped";

		private Status() {}
	}

	static final class Reason {

		static final String MISSING_CONFIG = "missingConfig";
		static final String INVALID_PAYLOAD = "invalidPayload";
		static final String QUEUE_UNAVAILABLE = "queueUnavailable";
		static final String NETWORK = "network";
		static final String IDENTITY_CHANGED = "identityChanged";

		private Reason() {}
	}

	static final class ResponseKeys {

		static final String TITLE = "title";
		static final String REPORT = "report";
		static final String ERRORS = "errors";

		private ResponseKeys() {}
	}

	private DeviceAttributesConstants() {}
}
