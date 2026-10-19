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
import com.adobe.marketing.mobile.util.MapUtils;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Builds the device attributes request body. The body is flat (not the interact request envelope):
 * the producer data from the request event, plus the SDK-owned {@code xdm} (implementationDetails and identityMap)
 * and {@code meta.state}.
 */
class DeviceAttributesRequestBuilder {

	private static final String LOG_SOURCE = "DeviceAttributesRequestBuilder";
	private static final String META_STATE_KEY = "state";

	// Read-only access to stored state:store payloads used for meta.state
	private final StoreResponsePayloadManager storeResponsePayloadManager;

	DeviceAttributesRequestBuilder(final NamedCollection namedCollection) {
		this.storeResponsePayloadManager = new StoreResponsePayloadManager(namedCollection);
	}

	/**
	 * Builds the device attributes request payload. Producer-supplied {@code xdm} and {@code meta} are ignored.
	 * The SDK adds {@code xdm} (implementationDetails and identityMap) and {@code meta.state} when available.
	 *
	 * @param eventData the request event (E3) data forming the base of the request body
	 * @param implementationDetails the SDK implementation details, as {@code {implementationDetails: {...}}}
	 * @param identityMap the identity map to attach under {@code xdm}
	 * @return the request payload, or null if there is nothing to send
	 */
	@Nullable Map<String, Object> buildPayload(
		@Nullable final Map<String, Object> eventData,
		@Nullable final Map<String, Object> implementationDetails,
		@Nullable final Map<String, Object> identityMap
	) {
		if (MapUtils.isNullOrEmpty(eventData)) {
			return null;
		}

		final Map<String, Object> payload = new HashMap<>(eventData);
		final boolean hadXdm = payload.remove(EdgeJson.Event.XDM) != null;
		final boolean hadMeta = payload.remove(EdgeJson.Event.METADATA) != null;

		if (hadXdm || hadMeta) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Ignoring producer-supplied SDK-owned xdm/meta fields for device attributes."
			);
		}

		final Map<String, Object> xdm = new HashMap<>();

		if (!MapUtils.isNullOrEmpty(implementationDetails)) {
			xdm.putAll(implementationDetails);
		}

		if (!MapUtils.isNullOrEmpty(identityMap)) {
			final Map<String, Object> requestIdentityMap = new HashMap<>(identityMap);
			final List<Map<String, Object>> ecids = DataReader.optTypedListOfMap(
				Object.class,
				identityMap,
				"ECID",
				null
			);

			if (ecids != null && ecids.size() > 1) {
				// Edge Identity's current ECID is first; this endpoint does not accept additional legacy ECIDs.
				requestIdentityMap.put("ECID", Collections.singletonList(ecids.get(0)));
			}

			xdm.put(EdgeJson.Event.Xdm.IDENTITY_MAP, requestIdentityMap);
		}

		if (!xdm.isEmpty()) {
			payload.put(EdgeJson.Event.XDM, xdm);
		}

		// meta.state uses the same StateMetadata shape sent to the interact endpoint
		final Map<String, Object> stateMetadata = new StateMetadata(storeResponsePayloadManager.getActiveStores())
			.toObjectMap();

		if (!MapUtils.isNullOrEmpty(stateMetadata)) {
			final Map<String, Object> meta = new HashMap<>();
			meta.put(META_STATE_KEY, stateMetadata);
			payload.put(EdgeJson.Event.METADATA, meta);
		}

		return payload.isEmpty() ? null : payload;
	}

	/**
	 * Builds the device attributes request payload and encodes it as compact JSON.
	 *
	 * @param eventData the request event (E3) data forming the base of the request body
	 * @param implementationDetails the SDK implementation details, as {@code {implementationDetails: {...}}}
	 * @param identityMap the identity map to attach under {@code xdm}
	 * @return the JSON request body, or null if there is nothing to send or encoding fails
	 */
	@Nullable String buildRequestBody(
		@Nullable final Map<String, Object> eventData,
		@Nullable final Map<String, Object> implementationDetails,
		@Nullable final Map<String, Object> identityMap
	) {
		final Map<String, Object> payload = buildPayload(eventData, implementationDetails, identityMap);

		if (payload == null) {
			return null;
		}

		try {
			return new JSONObject(payload).toString();
		} catch (final Exception e) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to encode device attributes request body: %s",
				e.getLocalizedMessage()
			);
			return null;
		}
	}
}
