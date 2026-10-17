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
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Applies the {@code state:store} handles from a successful device attributes response. Other handle types,
 * including {@code locationHint:result}, are ignored and no events are dispatched.
 */
class DeviceAttributesResponseHandler {

	private static final String LOG_SOURCE = "DeviceAttributesResponseHandler";

	private final StoreResponsePayloadManager storeResponsePayloadManager;

	/**
	 * @param namedCollection data store holding the {@code state:store} payloads
	 */
	DeviceAttributesResponseHandler(final NamedCollection namedCollection) {
		this.storeResponsePayloadManager = new StoreResponsePayloadManager(namedCollection);
	}

	/**
	 * Parses {@code responseBody} and applies the supported handles.
	 *
	 * @param responseBody plain JSON response body
	 * @param applyStorePayloads false to ignore {@code state:store} handles (the ECID changed while the request was in
	 *                           flight)
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

			if (EdgeJson.Response.EventHandle.Store.TYPE.equals(type)) {
				if (applyStorePayloads) {
					handleStorePayloads(payload);
				} else {
					Log.debug(
						LOG_TAG,
						LOG_SOURCE,
						"ECID changed since the request was sent, ignoring state:store handle."
					);
				}
			}
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
