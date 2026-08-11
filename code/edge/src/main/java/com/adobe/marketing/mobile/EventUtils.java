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

import androidx.annotation.NonNull;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.MapUtils;
import java.util.HashMap;
import java.util.Map;

/**
 * Class for event or event data specific helpers.
 */
final class EventUtils {

	/**
	 * Checks if the provided {@code event} is of type {@link EventType#EDGE} and source {@link EventSource#REQUEST_CONTENT}.
	 *
	 * @param event the event to verify
	 * @return true if both type and source match, false otherwise
	 */
	static boolean isExperienceEvent(@NonNull final Event event) {
		return (
			EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EventSource.REQUEST_CONTENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EventType#EDGE} and source {@link EventSource#UPDATE_CONSENT}.
	 *
	 * @param event the event to verify
	 * @return true if both type and source match, false otherwise
	 */
	static boolean isUpdateConsentEvent(@NonNull final Event event) {
		return (
			EventType.EDGE.equalsIgnoreCase(event.getType()) &&
			EventSource.UPDATE_CONSENT.equalsIgnoreCase(event.getSource())
		);
	}

	/**
	 * Checks if the provided {@code event} is of type {@link EventType#EDGE_IDENTITY} and source {@link EventSource#RESET_COMPLETE}.
	 *
	 * @param event current event to check
	 * @return true if the type and source matches, false otherwise
	 */
	static boolean isResetComplete(@NonNull final Event event) {
		return (
			EventType.EDGE_IDENTITY.equalsIgnoreCase(event.getType()) &&
			EventSource.RESET_COMPLETE.equalsIgnoreCase(event.getSource())
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
			MapUtils.putIfNotEmpty(edgeConfig, configKey, configValue);
		}

		// Bundled batching config is a per-key fallback only: consulted for a given key solely when
		// that key is absent from the Configuration shared state (i.e. not set programmatically, and
		// not delivered by a remote/Launch-published configuration). Any value present in the
		// Configuration shared state always wins over the bundled file for that same key. Values are
		// copied raw; consumers coerce via DataReader (optBoolean/optStringList) at read time.
		final Map<String, Object> bundledBatchingConfig = EdgeBundledBatchingConfig.get();
		final String[] batchingConfigKeys = new String[] {
			EdgeConstants.SharedState.Configuration.EDGE_BATCHING_ENABLED,
			EdgeConstants.SharedState.Configuration.EDGE_BATCHING_EVENT_NAME_ALLOWLIST,
			EdgeConstants.SharedState.Configuration.EDGE_BATCHING_MAX_BATCH_SIZE,
		};

		for (final String batchingKey : batchingConfigKeys) {
			if (configSharedState != null && configSharedState.containsKey(batchingKey)) {
				edgeConfig.put(batchingKey, configSharedState.get(batchingKey));
			} else if (bundledBatchingConfig.containsKey(batchingKey)) {
				edgeConfig.put(batchingKey, bundledBatchingConfig.get(batchingKey));
			}
		}

		return edgeConfig;
	}

	static Map<String, Object> getConfig(@NonNull final Event event) {
		return DataReader.optTypedMap(Object.class, event.getEventData(), EdgeConstants.EventDataKeys.Config.KEY, null);
	}
}
