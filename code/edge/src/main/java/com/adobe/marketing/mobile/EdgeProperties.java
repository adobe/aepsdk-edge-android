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

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Manages the structure of the properties used by the Edge extension.
 */
class EdgeProperties {

	private final String LOG_SOURCE = "EdgeProperties";
	private final NamedCollection namedCollection;

	// Edge Network location hint and expiration date. Location hint is invalid after expiry date.
	private String locationHint;
	private Calendar locationHintExpiryDate;

	EdgeProperties(final NamedCollection namedCollection) {
		this.namedCollection = namedCollection;
	}

	/**
	 * Retrieves the Edge Network location hint. Returns null if location hint expired or is not set.
	 * @return the Edge Network location hint or null if location hint expired or is not set.
	 */
	String getLocationHint() {
		final Calendar now = Calendar.getInstance(TimeZone.getTimeZone("GMT"));

		if (locationHintExpiryDate != null && locationHintExpiryDate.after(now)) {
			return locationHint;
		}

		return null;
	}

	/**
	 * Update the Edge Network location hint and persist the new hint to the data store. If
	 * {@code hint} is null, then the stored location hint and expiry date is removed from memory
	 * and persistent storage.
	 * If the updated location hint is different from the previous, then returns true.
	 * @param hint the Edge Network location hint to set
	 * @param ttlSeconds the time-to-live in seconds for the given location hint
	 * @return true if the location hint value changed
	 */
	Boolean setLocationHint(final String hint, final int ttlSeconds) {
		// Call getLocationHint so expiry data is checked, instead of using global variable directly
		final String currentHint = getLocationHint();
		final Boolean hintHasChanged =
			(currentHint == null && !StringUtils.isNullOrEmpty(hint)) ||
			(currentHint != null && !currentHint.equals(hint));

		if (StringUtils.isNullOrEmpty(hint)) {
			locationHint = null;
			locationHintExpiryDate = null;
		} else {
			final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
			calendar.add(Calendar.SECOND, Math.max(ttlSeconds, 0));

			locationHint = hint;
			locationHintExpiryDate = calendar;
		}

		saveToPersistence();

		return hintHasChanged;
	}

	/**
	 * Loads the {@code EdgeProperties} fields from the Edge extension's local data store.
	 */
	void loadFromPersistence() {
		if (namedCollection == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Local Storage Service is null. Unable to load properties from persistence."
			);
			return;
		}

		final String hint = namedCollection.getString(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT, null);
		final long hintExpiry = namedCollection.getLong(
			EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP,
			0
		);
		final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.setTimeInMillis(hintExpiry);

		this.locationHint = hint;
		this.locationHintExpiryDate = calendar;
	}

	/**
	 * Saves the {@code EdgeProperties} fields to the Edge extension's local data store.
	 */
	void saveToPersistence() {
		if (namedCollection == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Local Storage Service is null. Unable to save properties to persistence."
			);
			return;
		}

		if (StringUtils.isNullOrEmpty(locationHint)) {
			namedCollection.remove(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT);
		} else {
			namedCollection.setString(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT, locationHint);
		}

		if (locationHintExpiryDate == null) {
			namedCollection.remove(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP);
		} else {
			namedCollection.setLong(
				EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP,
				locationHintExpiryDate.getTimeInMillis()
			);
		}
	}

	/**
	 * Returns a map of the fields stored in this {@code EdgeProperties}. Fields which have null
	 * values are not included in the resultant map.
	 * The returned map is suitable for sharing in the {@code EventHub} as a shared state.
	 * @return a {@code Map<String, Object>} of this {@code EdgeProperties}.
	 */
	Map<String, Object> toEventData() {
		final Map<String, Object> map = new HashMap<>();

		// Call getLocationHint so expiry data is checked, instead of using global variable directly
		final String hint = getLocationHint();

		if (hint != null) {
			map.put(EdgeConstants.SharedState.Edge.LOCATION_HINT, hint);
		}

		return map;
	}
}
