/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import com.adobe.marketing.mobile.util.MapUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines query options on the requests to Edge Network
 */
class QueryOptions {

	private static final String JSON_KEY_CONSENT = "consent";
	private Map<String, Object> consent;

	/**
	 * Sets the query options required for consent updates
	 * @param consent query options to specify the operation type and other settings, if needed
	 */
	void setConsentOptions(final Map<String, Object> consent) {
		this.consent = consent != null ? new HashMap<>(consent) : new HashMap<>();
	}

	/**
	 * Converts current {@code QueryOptions} into map.
	 *
	 * @return map containing the {@link QueryOptions} data
	 */
	Map<String, Object> toObjectMap() {
		Map<String, Object> serializedMap = new HashMap<>();
		MapUtils.putIfNotEmpty(serializedMap, JSON_KEY_CONSENT, consent);
		return serializedMap;
	}
}
