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

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.JSONUtils;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class that encapsulates the data to be queued persistently for the {@link EdgeExtension}
 */
final class EdgeDataEntity {

	private static final String LOG_SOURCE = "EdgeDataEntity";

	private static final String CONFIGURATION_KEY = "configuration";
	private static final String IDENTITY_MAP_KEY = "identityMap";
	private static final String EVENT_KEY = "event";

	private final Event event;
	private final Map<String, Object> configuration;
	private final Map<String, Object> identityMap;

	/**
	 * Creates a read-only {@link EdgeDataEntity} object with the provided information.
	 *
	 * @param event an {@link Event}, should not be null
	 * @param config the Edge configuration for this {@code event}
	 * @param identityMap the identity information for this {@code event}
	 * @throws IllegalArgumentException if the provided {@code event} is null
	 */
	EdgeDataEntity(final Event event, final Map<String, Object> config, final Map<String, Object> identityMap) {
		if (event == null) {
			throw new IllegalArgumentException();
		}

		this.event = event;
		this.configuration = config == null ? Collections.emptyMap() : Utils.deepCopy(config);
		this.identityMap = identityMap == null ? Collections.emptyMap() : Utils.deepCopy(identityMap);
	}

	/**
	 * Creates a read-only {@link EdgeDataEntity} object with the provided information.
	 *
	 * @param event an {@link Event}, should not be null
	 * @throws IllegalArgumentException if the provided {@code event} is null
	 */
	EdgeDataEntity(final Event event) {
		this(event, null, null);
	}

	/**
	 * @return the {@link Event} cannot be null based on constructor
	 */
	Event getEvent() {
		return event;
	}

	/**
	 * @return the Edge configuration for this {@link EdgeDataEntity} as a read-only {@code Map<String, Object>}.
	 * Attempts to modify the returned map, whether direct or via its collection views, result in an {@link UnsupportedOperationException}.
	 */
	Map<String, Object> getConfiguration() {
		return Collections.unmodifiableMap(configuration);
	}

	/**
	 * @return the identity information for this {@link EdgeDataEntity} as a read-only {@code Map<String, Object>}.
	 * Attempts to modify the returned map, whether direct or via its collection views, result in an {@link UnsupportedOperationException}.
	 */
	Map<String, Object> getIdentityMap() {
		return Collections.unmodifiableMap(identityMap);
	}

	/**
	 * Serializes this to a {@code DataEntity}.
	 * @return serialized {@code EdgeDataEntity} or null if it could not be serialized.
	 */
	@Nullable
	DataEntity toDataEntity() {
		try {
			JSONObject serializedEntity = new JSONObject();
			serializedEntity.put(EVENT_KEY, new JSONObject(EventCoder.encode(this.event)));
			serializedEntity.put(CONFIGURATION_KEY, new JSONObject(this.configuration));
			serializedEntity.put(IDENTITY_MAP_KEY, new JSONObject(this.identityMap));

			return new DataEntity(
				event.getUniqueIdentifier(),
				new Date(event.getTimestamp()),
				serializedEntity.toString()
			);
		} catch (JSONException e) {
			Log.debug(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Failed to serialize EdgeDataEntity to DataEntity: " + e.getLocalizedMessage()
			);
		}

		return null;
	}

	/**
	 * Deserializes a {@code DataEntity} to a {@code EdgeDataEntity}.
	 * @param dataEntity {@code DataEntity} to be processed
	 * @return a deserialized {@code EdgeDataEntity} instance or null if it
	 * could not be deserialized to an {@code EdgeDataEntity}
	 */
	@Nullable
	static EdgeDataEntity fromDataEntity(@NotNull final DataEntity dataEntity) {
		String entity = dataEntity.getData();
		if (entity == null || entity.isEmpty()) {
			return null;
		}

		try {
			JSONObject serializedEntity = new JSONObject(entity);

			Map<String, Object> configuration = null;

			if (serializedEntity.has(CONFIGURATION_KEY)) {
				JSONObject configObj = serializedEntity.getJSONObject(CONFIGURATION_KEY);
				configuration = JSONUtils.toMap(configObj);
			}

			Map<String, Object> identityMap = null;

			if (serializedEntity.has(IDENTITY_MAP_KEY)) {
				JSONObject identityObj = serializedEntity.getJSONObject(IDENTITY_MAP_KEY);
				identityMap = JSONUtils.toMap(identityObj);
			}

			String eventString = serializedEntity.getJSONObject(EVENT_KEY).toString();
			Event event = EventCoder.decode(eventString);

			return new EdgeDataEntity(event, configuration, identityMap);
		} catch (JSONException | IllegalArgumentException e) {
			Log.debug(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Failed to deserialize DataEntity to EdgeDataEntity: " + e.getLocalizedMessage()
			);
		}

		return null;
	}
}
