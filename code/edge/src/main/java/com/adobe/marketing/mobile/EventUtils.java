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

import com.adobe.marketing.mobile.util.DataReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Class for event or event data specific helpers.
 */
final class EventUtils {

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE} and source {@link EdgeConstants.EventSource#REQUEST_CONTENT}.
	 *
	 * @param event the event to verify
	 * @return true if both type and source match, false otherwise
	 */
	static boolean isExperienceEvent(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.REQUEST_CONTENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE} and source {@link EdgeConstants.EventSource#UPDATE_CONSENT}.
	 *
	 * @param event the event to verify
	 * @return true if both type and source match, false otherwise
	 */
	static boolean isUpdateConsentEvent(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.UPDATE_CONSENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EdgeConstants.EventType#EDGE_IDENTITY} and source {@link EdgeConstants.EventSource#RESET_COMPLETE}.
	 *
	 * @param event current event to check
	 * @return true if the type and source matches, false otherwise
	 */
	static boolean isResetComplete(final Event event) {
		return (
			event != null &&
			EdgeConstants.EventType.EDGE_IDENTITY.equalsIgnoreCase(event.getType()) &&
			EdgeConstants.EventSource.RESET_COMPLETE.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Extracts the Edge config values from the Configuration shared state payload,
	 * including {@code edge.configId}, {@code edge.environment}, and {@code edge.domain}.
	 *
	 * @param configSharedState shared state payload for Configuration
	 * @return all Edge config keys extracted from the {@code configSharedState}
	 */
	static Map<String, Object> getEdgeConfiguration(final Map<String, Object> configSharedState) {
		final String[] configKeysWithStringValue = new String[] {
			EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID,
			EdgeConstants.SharedState.Configuration.EDGE_REQUEST_ENVIRONMENT,
			EdgeConstants.SharedState.Configuration.EDGE_DOMAIN,
		};

		Map<String, Object> edgeConfig = new HashMap<>();

		for (String configKey : configKeysWithStringValue) {
			final String configValue = DataReader.optString(configSharedState, configKey, null);
			Utils.putIfNotEmpty(edgeConfig, configKey, configValue);
		}

		return edgeConfig;
	}
}
