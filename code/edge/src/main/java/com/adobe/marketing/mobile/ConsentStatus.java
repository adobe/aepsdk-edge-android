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
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import java.util.Map;

/**
 * Enum for the known collect consent values for the Edge extension.
 */
enum ConsentStatus {
	YES("y"),
	NO("n"),
	PENDING("p");

	private static final String LOG_SOURCE = "ConsentStatus";
	private final String value;

	ConsentStatus(final String value) {
		this.value = value;
	}

	/**
	 * @return the string name for this enum type.
	 */
	String getValue() {
		return value;
	}

	/**
	 * Returns a {@link ConsentStatus} object based on the provided {@code rawValue}.
	 * If the rawValue provided is not valid, {@link EdgeConstants.Defaults#COLLECT_CONSENT_PENDING} is returned.
	 *
	 * @param rawValue {@link String} to be converted to a {@code ConsentStatus} object
	 * @return {@code ConsentStatus} object equivalent to the provided rawValue
	 */
	static ConsentStatus fromString(final String rawValue) {
		for (ConsentStatus val : ConsentStatus.values()) {
			if (val.getValue().equalsIgnoreCase(rawValue)) {
				return val;
			}
		}

		return EdgeConstants.Defaults.COLLECT_CONSENT_PENDING;
	}

	/**
	 * Extracts the collect consent value from the provided event data payload.
	 * If encoding fails it returns the default {@link EdgeConstants.Defaults#COLLECT_CONSENT_PENDING}
	 *
	 *  @param eventData consent preferences update payload
	 * @return the collect consent value extracted from the payload, or pending if the decoding failed
	 */
	static ConsentStatus getCollectConsentOrDefault(final Map<String, Object> eventData) {
		String collectConsentValue = null;

		try {
			Map<String, Object> consents = DataReader.getTypedMap(
				Object.class,
				eventData,
				EdgeConstants.SharedState.Consent.CONSENTS
			);

			if (consents != null) {
				Map<String, Object> collectConsent = DataReader.getTypedMap(
					Object.class,
					consents,
					EdgeConstants.SharedState.Consent.COLLECT
				);

				if (collectConsent != null) {
					collectConsentValue = DataReader.getString(collectConsent, EdgeConstants.SharedState.Consent.VAL);
				}
			}
		} catch (DataReaderException e) {
			// if collect consent not set yet, use default (pending)
			Log.trace(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Failed to read collect consent from event data, defaulting to (p)"
			);
			return EdgeConstants.Defaults.COLLECT_CONSENT_PENDING;
		}

		return ConsentStatus.fromString(collectConsentValue);
	}
}
