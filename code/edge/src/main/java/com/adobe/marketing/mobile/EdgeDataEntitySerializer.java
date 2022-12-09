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

import com.adobe.marketing.mobile.services.Log;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

class EdgeDataEntitySerializer {

	private static final String LOG_SOURCE = "EdgeDataEntitySerializer";
	private static final String CONFIGURATION_KEY = "configuration";
	private static final String IDENTITY_MAP_KEY = "identityMap";
	private static final String EVENT_KEY = "event";

	private EdgeDataEntitySerializer() {}

	/**
	 * Serialize this {@code EdgeDataEntity} to a String.
	 * @param entity the {@code EdgeDataEntity} instance to serialize
	 * @return string representation of a serialized {@code EdgeDataEntity} or null
	 * if {@code entity} could not be serialized to a string.
	 */
	static String serialize(final EdgeDataEntity entity) {
		if (entity == null) {
			return null;
		}

		try {
			JSONObject serializedEntity = new JSONObject();
			serializedEntity.put(EVENT_KEY, new JSONObject(EventCoder.encode(entity.getEvent())));
			serializedEntity.put(CONFIGURATION_KEY, new JSONObject(entity.getConfiguration()));
			serializedEntity.put(IDENTITY_MAP_KEY, new JSONObject(entity.getIdentityMap()));

			return serializedEntity.toString();
		} catch (JSONException e) {
			Log.debug(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Failed to serialize EdgeDataEntity to string: " + e.getLocalizedMessage()
			);
		}

		return null;
	}

	/**
	 * Deserialize a String to a {@code EdgeDataEntity}.
	 * @param entity a string representation of a serialized {@code EdgeDataEntity}
	 * @return a deserialized {@code EdgeDataEntity} instance or null if the string
	 * could not be deserialized to an {@code EdgeDataEntity}
	 */
	static EdgeDataEntity deserialize(final String entity) {
		if (entity == null || entity.isEmpty()) {
			return null;
		}

		try {
			JSONObject serializedEntity = new JSONObject(entity);

			Map<String, Object> configuration = null;

			if (serializedEntity.has(CONFIGURATION_KEY)) {
				JSONObject configObj = serializedEntity.getJSONObject(CONFIGURATION_KEY);
				configuration = Utils.toMap(configObj);
			}

			Map<String, Object> identityMap = null;

			if (serializedEntity.has(IDENTITY_MAP_KEY)) {
				JSONObject identityObj = serializedEntity.getJSONObject(IDENTITY_MAP_KEY);
				identityMap = Utils.toMap(identityObj);
			}

			String eventString = serializedEntity.getJSONObject(EVENT_KEY).toString();
			Event event = EventCoder.decode(eventString);

			return new EdgeDataEntity(event, configuration, identityMap);
		} catch (JSONException | IllegalArgumentException e) {
			Log.debug(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Failed to deserialize string to EdgeDataEntity: " + e.getLocalizedMessage()
			);
		}

		return null;
	}
}
