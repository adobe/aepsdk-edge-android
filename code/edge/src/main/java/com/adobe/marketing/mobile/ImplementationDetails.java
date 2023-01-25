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

import static com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE;
import static com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails.ENVIRONMENT;
import static com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails.ENVIRONMENT_VALUE_APP;
import static com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails.IMPLEMENTATION_DETAILS;
import static com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails.NAME;
import static com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails.VALUE_UNKNOWN;
import static com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails.VERSION;

import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.HashMap;
import java.util.Map;

final class ImplementationDetails {

	/**
	 * Builds and returns the Implementation Details for the current session. Parses the given {@code eventHubState}
	 * to retrieve the Mobile Core version and wrapper type. If no Mobile Core version is found in {@code eventHubState}, then "unknown" is used.
	 * If the Mobile Core "wrapper" exists but the "type" cannot be parsed, then "unknown" is used for the wrapper type.
	 * If the "wrapper" does not exist in {@code eventHubState}, or the found wrapper type is not supported,
	 * then the default {@link WrapperType#NONE} is used.
	 *
	 * See <a href="https://github.com/adobe/xdm/blob/master/components/datatypes/industry-verticals/implementationdetails.schema.json">XDM Implementation Details</a>
	 * @param eventHubState the Event Hub shared state
	 * @return a map conforming to the XDM Implementation Detail data type, or null if no implementation details exist
	 */
	static Map<String, Object> fromEventHubState(final Map<String, Object> eventHubState) {
		if (MapUtils.isNullOrEmpty(eventHubState)) {
			return null;
		}

		final String coreVersion = getCoreVersion(eventHubState);
		final String wrapperType = getWrapperType(eventHubState);
		final String wrapperName = getWrapperName(wrapperType);

		final Map<String, Object> implementationDetails = new HashMap<>();
		implementationDetails.put(ENVIRONMENT, ENVIRONMENT_VALUE_APP);
		implementationDetails.put(VERSION, coreVersion + "+" + EdgeConstants.EXTENSION_VERSION);
		implementationDetails.put(
			NAME,
			BASE_NAMESPACE + (StringUtils.isNullOrEmpty(wrapperName) ? "" : "/" + wrapperName)
		);

		return new HashMap<String, Object>() {
			{
				put(IMPLEMENTATION_DETAILS, implementationDetails);
			}
		};
	}

	/**
	 * Get Mobile Core version from Event Hub shared state.
	 * @param eventHubState the Event Hub shared state
	 * @return the version of Core, or 'unknown' String if Core version was not found in shared state.
	 */
	private static String getCoreVersion(final Map<String, Object> eventHubState) {
		if (eventHubState == null) {
			return VALUE_UNKNOWN;
		}

		String version = DataReader.optString(eventHubState, EdgeConstants.SharedState.Hub.VERSION, null);

		return StringUtils.isNullOrEmpty(version) ? VALUE_UNKNOWN : version;
	}

	/**
	 * Parse the wrapper type from the Event Hub shared state.
	 * @param eventHubState the Event Hub shared state
	 * @return wrapper type tag parsed from shared state, or null if wrapper type could not be parsed
	 */
	private static String getWrapperType(final Map<String, Object> eventHubState) {
		if (eventHubState == null) {
			return null;
		}

		// if Wrapper mapping is not in shared state, default to None type
		if (!eventHubState.containsKey(EdgeConstants.SharedState.Hub.WRAPPER)) {
			return WrapperType.NONE.getWrapperTag();
		}

		Map<String, Object> wrapperDetails = DataReader.optTypedMap(
			Object.class,
			eventHubState,
			EdgeConstants.SharedState.Hub.WRAPPER,
			null
		);
		return DataReader.optString(wrapperDetails, EdgeConstants.SharedState.Hub.TYPE, null); // if "type" could not be parsed, return null
	}

	/**
	 * Get the wrapper name from the wrapper tag.
	 * @param wrapperTag the wrapper type tag name
	 * @return the name of the wrapper for use in the Implementation Details name,
	 * or "unknown" if the value is not recognized
	 */
	private static String getWrapperName(final String wrapperTag) {
		// WrapperType enum has no case for unknown type. The method WrapperType.fromString() converts any invalid string to None.
		// Edge needs to differentiate between invalid/unknown and None type.
		if (WrapperType.NONE.getWrapperTag().equals(wrapperTag)) {
			return "";
		}

		WrapperType wrapperType = WrapperType.fromString(wrapperTag);

		if (wrapperType == WrapperType.CORDOVA) {
			return EdgeJson.Event.ImplementationDetails.WRAPPER_CORDOVA;
		} else if (wrapperType == WrapperType.FLUTTER) {
			return EdgeJson.Event.ImplementationDetails.WRAPPER_FLUTTER;
		} else if (wrapperType == WrapperType.REACT_NATIVE) {
			return EdgeJson.Event.ImplementationDetails.WRAPPER_REACT_NATIVE;
		} else if (wrapperType == WrapperType.UNITY) {
			return EdgeJson.Event.ImplementationDetails.WRAPPER_UNITY;
		} else if (wrapperType == WrapperType.XAMARIN) {
			return EdgeJson.Event.ImplementationDetails.WRAPPER_XAMARIN;
		} else {
			return VALUE_UNKNOWN;
		}
	}
}
