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
import com.adobe.marketing.mobile.util.CloneFailedException;
import com.adobe.marketing.mobile.util.EventDataUtils;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Utils {

	private static final String LOG_SOURCE = "Utils";

	private Utils() {}

	static boolean isNullOrEmpty(final Map<String, Object> map) {
		return map == null || map.isEmpty();
	}

	static boolean isNullOrEmpty(final JSONArray jsonArray) {
		return jsonArray == null || jsonArray.length() == 0;
	}

	static boolean isNullOrEmpty(final JSONObject jsonObject) {
		return jsonObject == null || jsonObject.length() == 0;
	}

	/* Helpers for manipulating Maps */
	/**
	 * Adds {@code key}/{@code values} to {@code map} if {@code values} is not null or an
	 * empty collection.
	 *
	 * @param map collection to put {@code values} mapped to {@code key} if {@code values} is
	 *            non-null and contains at least one entry
	 * @param key key used to map {@code values} in {@code map}
	 * @param values a map to add to {@code map} if not null or empty
	 */
	static void putIfNotEmpty(final Map<String, Object> map, final String key, final Map<String, Object> values) {
		boolean addValues = map != null && key != null && !isNullOrEmpty(values);

		if (addValues) {
			map.put(key, values);
		}
	}

	/**
	 * Adds {@code key}/{@code value} to {@code map} if {@code value} is not null or an
	 * empty collection.
	 *
	 * @param map collection to put {@code value} mapped to {@code key} if {@code value} is
	 *            non-null and contains at least one entry
	 * @param key key used to map {@code value} in {@code map}
	 * @param value a String to add to {@code map} if not null or empty
	 */
	public static void putIfNotEmpty(final Map<String, Object> map, final String key, final String value) {
		boolean addValues = map != null && key != null && !StringUtils.isNullOrEmpty(value);

		if (addValues) {
			map.put(key, value);
		}
	}

	/* JSON - Map conversion helpers */
	static List<Map<String, Object>> toListOfMaps(final JSONArray jsonArray) {
		if (jsonArray == null) {
			return null;
		}

		List<Map<String, Object>> jsonArrayAsList = new ArrayList<>();
		int size = jsonArray.length();

		for (int i = 0; i < size; i++) {
			Object value = null;

			try {
				value = jsonArray.get(i);
			} catch (JSONException e) {
				Log.debug(LOG_TAG, LOG_SOURCE, "Unable to convert jsonObject to List for index %d, skipping.", i);
			}

			if (value == null) {
				continue;
			}

			if (value instanceof JSONObject) {
				try {
					Map<String, Object> returnValue = JSONUtils.toMap((JSONObject) value);
					jsonArrayAsList.add(returnValue);
				} catch (JSONException e) {
					Log.debug(LOG_TAG, LOG_SOURCE, "Unable to convert jsonObject to Map for index %d, skipping.", i);
				}
			}
		}

		return jsonArrayAsList;
	}

	/**
	 * Creates a deep copy of the provided {@link Map}.
	 *
	 * @param map to be copied
	 * @return {@link Map} containing a deep copy of all the elements in {@code map}
	 */
	static Map<String, Object> deepCopy(final Map<String, Object> map) {
		try {
			return EventDataUtils.clone(map);
		} catch (CloneFailedException e) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to deep copy map. CloneFailedException: %s",
				e.getLocalizedMessage()
			);
		}

		return null;
	}

	/**
	 * Creates a deep copy of the provided {@code listOfMaps}.
	 *
	 * @param listOfMaps to be copied
	 * @return {@link List} containing a deep copy of all the elements in {@code listOfMaps}
	 * @see #deepCopy(Map)
	 */
	static List<Map<String, Object>> deepCopy(final List<Map<String, Object>> listOfMaps) {
		if (listOfMaps == null) {
			return null;
		}

		List<Map<String, Object>> deepCopy = new ArrayList<>();

		for (Map<String, Object> map : listOfMaps) {
			deepCopy.add(deepCopy(map));
		}

		return deepCopy;
	}
}
