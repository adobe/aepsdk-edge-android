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
import java.util.Calendar;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Contains a store payload with its expiring information. Use this class when serializing to local storage.
 */
class StoreResponsePayload {

	private static final String LOG_SOURCE = "StoreResponsePayload";

	private final String key;
	private final String value;
	private final Integer maxAgeSeconds;
	private long expiryTimestampMilliseconds; // date stamp in milliseconds that this payload expires.

	private StoreResponsePayload(final String key, final String value, final Integer maxAge) {
		this.key = key;
		this.value = value;
		this.maxAgeSeconds = maxAge;

		// compute expiryDate
		Calendar calendar = Calendar.getInstance();
		calendar.add(Calendar.SECOND, maxAge);
		expiryTimestampMilliseconds = calendar.getTimeInMillis();
	}

	/**
	 * Checks if the payload has exceeded its max age
	 *
	 * @return true if the payload is expired, false otherwise
	 */
	boolean isExpired() {
		return Calendar.getInstance().getTimeInMillis() >= expiryTimestampMilliseconds;
	}

	/**
	 * Converts current StoreResponsePayload into map.
	 *
	 * @return map containing the {@link StoreResponsePayload} data
	 */
	JSONObject toJsonObject() {
		JSONObject jsonObject = new JSONObject();

		try {
			jsonObject.put(EdgeJson.Response.EventHandle.Store.KEY, key);
			jsonObject.put(EdgeJson.Response.EventHandle.Store.VALUE, value);
			jsonObject.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, maxAgeSeconds);
			jsonObject.put(EdgeJson.Response.EventHandle.Store.EXPIRY_DATE, expiryTimestampMilliseconds);
		} catch (JSONException e) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to create the json object from payload: %s",
				e.getLocalizedMessage()
			);
			return null;
		}

		return jsonObject;
	}

	static StoreResponsePayload fromJsonObject(final JSONObject jsonObject) {
		if (jsonObject == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to create the payload from the map, StoreResponsePayload is null or empty"
			);
			return null;
		}

		String key;
		String value;
		int maxAge;
		long expiryDate;

		try {
			key = jsonObject.getString(EdgeJson.Response.EventHandle.Store.KEY);
			value = jsonObject.getString(EdgeJson.Response.EventHandle.Store.VALUE);
			maxAge = jsonObject.optInt(EdgeJson.Response.EventHandle.Store.MAX_AGE, Integer.MIN_VALUE);
			expiryDate = jsonObject.optLong(EdgeJson.Response.EventHandle.Store.EXPIRY_DATE, Long.MIN_VALUE);
		} catch (JSONException e) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to create the json object from payload: %s",
				e.getLocalizedMessage()
			);
			return null;
		}

		if (key == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to create the payload from payload json object, key does not exist in the payload"
			);
			return null;
		}

		if (value == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to create the payload from payload json object, value does not exist in the payload"
			);
			return null;
		}

		if (maxAge == Integer.MIN_VALUE) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to create the payload from payload json object, maxAge does not exist in the payload"
			);
			return null;
		}

		StoreResponsePayload payloadObj = new StoreResponsePayload(key, value, maxAge);

		// expiryDate not required, set it if available
		if (expiryDate != Long.MIN_VALUE) {
			payloadObj.expiryTimestampMilliseconds = expiryDate;
		}

		return payloadObj;
	}

	/**
	 * Gets the key for this store payload.
	 *
	 * @return key of the store payload
	 */
	String getKey() {
		return key;
	}

	/**
	 * Gets the value for this store payload.
	 *
	 * @return value of the store payload
	 */
	String getValue() {
		return value;
	}

	/**
	 * Gets the key for this store payload.
	 *
	 * @return max age of the store payload
	 */
	Integer getMaxAge() {
		return maxAgeSeconds;
	}

	/**
	 * Gets the key for this store payload.
	 *
	 * @return expiry date of the store payload
	 */
	long getExpiryDate() {
		return expiryTimestampMilliseconds;
	}
}
