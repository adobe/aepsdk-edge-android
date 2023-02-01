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
import com.adobe.marketing.mobile.services.NamedCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

class StoreResponsePayloadManager {

	private static final String LOG_SOURCE = "StoreResponsePayloadManager";

	private final NamedCollection namedCollection;

	StoreResponsePayloadManager(final NamedCollection dataStore) {
		this.namedCollection = dataStore;
	}

	/**
	 * Reads all the active saved store payloads from datastore.
	 * Any store payload that has expired will not be included and will be evicted from the data store.
	 *
	 * @return a map of active store payload objects
	 */
	Map<String, StoreResponsePayload> getActiveStores() {
		if (namedCollection == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot get active stores, dataStore is null.");
			return null;
		}

		Map<String, String> serializedPayloads = namedCollection.getMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS);

		if (serializedPayloads == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot get active stores, serializedPayloads is null.");
			return null;
		}

		Map<String, StoreResponsePayload> deserializedPayloads = new HashMap<>();
		ArrayList<String> toBeDeleted = new ArrayList<>();

		for (String serializedPayload : serializedPayloads.values()) {
			StoreResponsePayload payload;

			try {
				payload = StoreResponsePayload.fromJsonObject(new JSONObject(serializedPayload));
			} catch (JSONException e) {
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Failed to convert JSON object to StoreResponsePayload: %s",
					e.getLocalizedMessage()
				);
				continue;
			}

			if (payload != null) {
				if (payload.isExpired()) {
					toBeDeleted.add(payload.getKey());
				} else {
					deserializedPayloads.put(payload.getKey(), payload);
				}
			}
		}

		deleteStoreResponses(toBeDeleted);
		return deserializedPayloads;
	}

	/**
	 * Saves a list of response payloads to the data store and deletes payloads with a maxAge lower or equal to 0.
	 *
	 * @param responsePayloads a list of payloads to be saved to data store.
	 */
	void saveStorePayloads(final List<Map<String, Object>> responsePayloads) {
		if (namedCollection == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot save stores, dataStore is null.");
			return;
		}

		if (responsePayloads == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot save stores, responsePayloads is null.");
			return;
		}

		Map<String, String> serializedPayloads = namedCollection.getMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS);

		if (serializedPayloads == null) {
			serializedPayloads = new HashMap<>();
		}

		ArrayList<String> toBeDeleted = new ArrayList<>();

		for (Map<String, Object> payloadMap : responsePayloads) {
			StoreResponsePayload payload = StoreResponsePayload.fromJsonObject(new JSONObject(payloadMap));

			if (payload != null) {
				if (payload.getMaxAge() <= 0) {
					// The Experience Edge server (Konductor) defines state values with 0 or -1 max age as to be deleted on the client.
					toBeDeleted.add(payload.getKey());
				} else {
					serializedPayloads.put(payload.getKey(), payload.toJsonObject().toString());
				}
			}
		}

		namedCollection.setMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS, serializedPayloads);
		deleteStoreResponses(toBeDeleted);
	}

	/**
	 * Deletes a list of stores from the data store.
	 *
	 * @param keys a list of store keys
	 */
	void deleteStoreResponses(final ArrayList<String> keys) {
		if (namedCollection == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot delete stores, dataStore is null.");
			return;
		}

		if (keys == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot delete stores, keys is null.");
			return;
		}

		Map<String, String> serializedPayloads = namedCollection.getMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS);

		if (serializedPayloads == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot delete stores, data store is null.");
			return;
		}

		for (String key : keys) {
			serializedPayloads.remove(key);
		}

		namedCollection.setMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS, serializedPayloads);
	}

	/**
	 * Deletes all the stores from the data store.
	 */
	void deleteAllStorePayloads() {
		if (namedCollection == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Cannot delete the store payloads, dataStore is null.");
			return;
		}

		namedCollection.remove(EdgeConstants.DataStoreKeys.STORE_PAYLOADS);
	}
}
