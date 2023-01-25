/*
  Copyright 2019 Adobe. All rights reserved.
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

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.MapUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * A request for pushing events to the Adobe Experience Edge.
 * An {@link EdgeRequest} is the top-level request object sent to Experience Edge
 */
class EdgeRequest {

	private static final String LOG_SOURCE = "EdgeRequest";

	private static final String JSON_KEY_XDM = "xdm";
	private static final String JSON_KEY_EVENTS = "events";
	private static final String JSON_KEY_META = "meta";

	// Metadata passed to the Experience Cloud solutions and even to the Edge itself with possibility of overriding at event level
	private RequestMetadata metadata;

	// XDM data applied for the entire request
	private Map<String, Object> xdmPayloads;

	/**
	 * Set the metadata passed to Edge Network / solutions with possibility of overriding at event level
	 *
	 * @param metadata the request metadata
	 */
	void setRequestMetadata(final RequestMetadata metadata) {
		this.metadata = metadata;
	}

	/**
	 * Sets the current XDM data for this request
	 * @param xdmPayloads XDM payload data
	 */
	void setXdmPayloads(final Map<String, Object> xdmPayloads) {
		this.xdmPayloads = xdmPayloads;
	}

	/**
	 * Builds the request payload with all the provided parameters and events and returns
	 * as a JSON Object suitable as a request body.
	 *
	 * @param serializedEvents list of experience events, should not be null/empty
	 * @return the request payload in {@link JSONObject} format or null if events list is null/empty
	 */
	JSONObject asJsonObject(final List<Map<String, Object>> serializedEvents) {
		if (serializedEvents == null || serializedEvents.isEmpty()) {
			Log.warning(LOG_TAG, LOG_SOURCE, "Unable to create Edge Request with no Events.");
			return null;
		}

		Map<String, Object> requestPayload = new HashMap<>();

		MapUtils.putIfNotEmpty(requestPayload, JSON_KEY_XDM, xdmPayloads);

		requestPayload.put(JSON_KEY_EVENTS, serializedEvents);

		if (metadata != null) {
			MapUtils.putIfNotEmpty(requestPayload, JSON_KEY_META, metadata.toObjectMap());
		}

		JSONObject jsonPayload = null;
		try {
			jsonPayload = new JSONObject(requestPayload);
		} catch (NullPointerException e) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to create Edge Request with null keys: %s",
				e.getLocalizedMessage()
			);
		}

		return jsonPayload;
	}
}
