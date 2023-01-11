/*
  Copyright 2020 Adobe. All rights reserved.
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
import com.adobe.marketing.mobile.util.JSONUtils;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Client side stored information. A property in the {@link RequestMetadata} object.
 */
class StateMetadata {

	private static final String JSON_KEY_ENTRIES = "entries";
	private static final String LOG_SOURCE = "StateMetadata";
	private final JSONObject metadataPayload = new JSONObject();

	StateMetadata(final Map<String, StoreResponsePayload> payloadMap) {
		if (payloadMap == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot init StateMetadata, payloadMap is null.");
			return;
		}

		JSONArray payloadArray = new JSONArray();

		for (StoreResponsePayload payload : payloadMap.values()) {
			JSONObject payloadJson = payload.toJsonObject();

			if (payloadJson != null) {
				// EXPIRY_DATE only used for client side computation, don't send this to Konductor
				payloadJson.remove(EdgeJson.Response.EventHandle.Store.EXPIRY_DATE);
				payloadArray.put(payloadJson);
			}
		}

		try {
			if (payloadArray.length() != 0) {
				metadataPayload.put(JSON_KEY_ENTRIES, payloadArray);
			}
		} catch (JSONException e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Could not add payload array to entries: %s", e.getLocalizedMessage());
		}
	}

	Map<String, Object> toObjectMap() {
		try {
			return JSONUtils.toMap(metadataPayload);
		} catch (JSONException e) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to create Object map for StateMetadata due to JSONException: %s",
				e.getLocalizedMessage()
			);
		}

		return null;
	}
}
