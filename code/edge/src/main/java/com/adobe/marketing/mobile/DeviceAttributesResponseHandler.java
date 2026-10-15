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

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Applies the supported handles from a successful device attributes response:
 * {@code locationHint:result} (EdgeNetwork scope) and {@code state:store}. Other handle types are ignored and no
 * events are dispatched.
 */
class DeviceAttributesResponseHandler {

	private static final String LOG_SOURCE = "DeviceAttributesResponseHandler";

	private final StoreResponsePayloadManager storeResponsePayloadManager;
	private final EdgeStateCallback stateCallback;

	/**
	 * @param namedCollection data store holding the {@code state:store} payloads
	 * @param stateCallback used to set the Edge Network location hint received in the response
	 */
	DeviceAttributesResponseHandler(final NamedCollection namedCollection, final EdgeStateCallback stateCallback) {
		this.storeResponsePayloadManager = new StoreResponsePayloadManager(namedCollection);
		this.stateCallback = stateCallback;
	}

	/**
	 * Parses {@code responseBody} and applies the supported handles.
	 *
	 * @param responseBody plain JSON response body
	 * @param applyStorePayloads false to ignore {@code state:store} handles (e.g. the identities were reset while
	 *                           the request was in flight)
	 */
	void handle(@Nullable final String responseBody, final boolean applyStorePayloads) {
		if (StringUtils.isNullOrEmpty(responseBody)) {
			Log.trace(LOG_TAG, LOG_SOURCE, "Device attributes response has no content.");
			return;
		}

		final Map<String, Object> json;

		try {
			json = JSONUtils.toMap(new JSONObject(responseBody));
		} catch (final Exception e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Unable to parse device attributes response: %s", responseBody);
			return;
		}

		final Object handles = json != null ? json.get(DeviceAttributesConstants.ResponseKeys.HANDLE) : null;

		if (!(handles instanceof List)) {
			return;
		}

		for (final Object handle : (List<?>) handles) {
			if (!(handle instanceof Map)) {
				continue;
			}

			final Map<?, ?> handleMap = (Map<?, ?>) handle;
			final Object type = handleMap.get(DeviceAttributesConstants.ResponseKeys.TYPE);
			final List<Map<String, Object>> payload = toPayloadList(
				handleMap.get(DeviceAttributesConstants.ResponseKeys.PAYLOAD)
			);

			if (!(type instanceof String) || payload == null) {
				continue;
			}

			if (EdgeJson.Response.EventHandle.LocationHint.TYPE.equals(type)) {
				handleLocationHint(payload);
			} else if (EdgeJson.Response.EventHandle.Store.TYPE.equals(type)) {
				if (applyStorePayloads) {
					handleStorePayloads(payload);
				} else {
					Log.debug(LOG_TAG, LOG_SOURCE, "Identities were reset, ignoring state:store handle.");
				}
			}
		}
	}

	/**
	 * Applies the first {@code EdgeNetwork} scoped location hint, mirroring {@link NetworkResponseHandler}.
	 *
	 * @param payload the {@code locationHint:result} handle payload
	 */
	private void handleLocationHint(final List<Map<String, Object>> payload) {
		for (final Map<String, Object> locationHint : payload) {
			final String scope = DataReader.optString(
				locationHint,
				EdgeJson.Response.EventHandle.LocationHint.SCOPE,
				null
			);

			if (!DeviceAttributesConstants.EDGE_NETWORK_SCOPE.equals(scope)) {
				continue;
			}

			try {
				final String hint = DataReader.getString(locationHint, EdgeJson.Response.EventHandle.LocationHint.HINT);
				final int ttlSeconds = DataReader.getInt(
					locationHint,
					EdgeJson.Response.EventHandle.LocationHint.TTL_SECONDS
				);

				if (!StringUtils.isNullOrEmpty(hint) && stateCallback != null) {
					stateCallback.setLocationHint(hint, ttlSeconds);
				}
			} catch (final DataReaderException e) {
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Failed to parse 'locationHint:result' for scope 'EdgeNetwork': %s",
					e.getLocalizedMessage()
				);
			}

			break;
		}
	}

	/**
	 * Persists {@code state:store} payloads, mirroring {@link NetworkResponseHandler}.
	 *
	 * @param payload the {@code state:store} handle payload
	 */
	private void handleStorePayloads(final List<Map<String, Object>> payload) {
		storeResponsePayloadManager.saveStorePayloads(payload);

		if (!payload.isEmpty()) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Processed %d store response payload(s).", payload.size());
		}
	}

	@SuppressWarnings("unchecked")
	@Nullable private static List<Map<String, Object>> toPayloadList(final Object payload) {
		if (!(payload instanceof List)) {
			return null;
		}

		final List<Map<String, Object>> result = new ArrayList<>();

		for (final Object item : (List<?>) payload) {
			if (item instanceof Map) {
				result.add((Map<String, Object>) item);
			}
		}

		return result;
	}
}
