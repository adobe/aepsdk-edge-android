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
import com.adobe.marketing.mobile.util.MapUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * An update Consent request payload.
 */
class EdgeConsentUpdate {

	private static final String LOG_SOURCE = "EdgeConsentUpdate";

	private RequestMetadata metadata;
	private QueryOptions query;
	private Map<String, Object> identityMap;
	private final Map<String, Object> consents;

	/**
	 * Construct a new {@code EdgeConsentUpdate}.
	 *
	 * @param consents the consent values
	 */
	EdgeConsentUpdate(final Map<String, Object> consents) {
		this.consents = consents;
	}

	/**
	 * Set the metadata passed to Edge Network/solutions with possibility of overriding at event level
	 *
	 * @param metadata the request metadata
	 */
	void setRequestMetadata(final RequestMetadata metadata) {
		this.metadata = metadata;
	}

	/**
	 * Set the additional query options passed to Edge Network
	 * @param query the query data for this request
	 */
	void setQueryOptions(final QueryOptions query) {
		this.query = query;
	}

	/**
	 * Sets the current IdentityMap for this request
	 * @param identityMap XDM IdentityMap data
	 */
	void setIdentityMap(final Map<String, Object> identityMap) {
		this.identityMap = identityMap;
	}

	/**
	 * Builds the request payload with all the provided parameters and returns
	 * as a JSON Object suitable as a request body.
	 *
	 * @return the request payload in {@link JSONObject} format or null if the consents are null/empty
	 */
	JSONObject asJsonObject() {
		if (MapUtils.isNullOrEmpty(consents)) {
			Log.debug(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Invalid consent update request, consents payload was null/empty."
			);
			return null;
		}

		final Map<String, Object> payload = new HashMap<>();

		if (metadata != null) {
			MapUtils.putIfNotEmpty(payload, EdgeJson.Event.METADATA, metadata.toObjectMap());
		}

		if (query != null) {
			MapUtils.putIfNotEmpty(payload, EdgeJson.Event.QUERY, query.toObjectMap());
		}

		MapUtils.putIfNotEmpty(payload, EdgeJson.Event.Xdm.IDENTITY_MAP, identityMap);

		final Map<String, Object> consent = new HashMap<>();
		consent.put(EdgeJson.Event.Consent.STANDARD_KEY, EdgeJson.Event.Consent.STANDARD_VALUE);
		consent.put(EdgeJson.Event.Consent.VERSION_KEY, EdgeJson.Event.Consent.VERSION_VALUE);
		consent.put(EdgeJson.Event.Consent.VALUE_KEY, consents);

		payload.put(
			EdgeJson.Event.Consent.CONSENT_KEY,
			new ArrayList<Map<String, Object>>() {
				{
					add(consent);
				}
			}
		);

		return new JSONObject(payload);
	}
}
