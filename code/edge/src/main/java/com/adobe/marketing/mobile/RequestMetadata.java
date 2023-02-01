/*
  Copyright 2019 Adobe. All rights reserved.
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
 * Metadata passed to Edge Network/solutions with possibility of overriding at event level.
 */
class RequestMetadata {

	private static final String JSON_KEY_GATEWAY = "konductorConfig";
	private static final String JSON_KEY_STATE = "state";

	private Map<String, Object> konductorConfig;
	private Map<String, Object> state;

	private RequestMetadata() {
		konductorConfig = new HashMap<>();
		state = new HashMap<>();
	}

	/**
	 * Get the metadata for Edge Network.
	 *
	 * @return map of metadata for Edge Network
	 */
	Map<String, Object> getKonductorConfig() {
		return konductorConfig;
	}

	/**
	 * Get the state for Edge Network.
	 *
	 * @return Map of metadata for Edge Network
	 */
	Map<String, Object> getState() {
		return state;
	}

	/**
	 * Converts current {@code RequestMetadata} into map.
	 *
	 * @return map containing the {@link RequestMetadata} data
	 */
	Map<String, Object> toObjectMap() {
		Map<String, Object> serializedMap = new HashMap<>();
		MapUtils.putIfNotEmpty(serializedMap, JSON_KEY_GATEWAY, konductorConfig);
		MapUtils.putIfNotEmpty(serializedMap, JSON_KEY_STATE, state);
		return serializedMap;
	}

	static class Builder {

		private final RequestMetadata requestMetadata;
		private boolean didBuild;

		Builder() {
			requestMetadata = new RequestMetadata();
			didBuild = false;
		}

		/**
		 * Sets the configuration metadata for Edge Network.
		 *
		 * @param konductorConfig the metadata for the Experience Platform Orchestration Service
		 * @return this {@code RequestMetadata.Builder} instance
		 */
		RequestMetadata.Builder setKonductorConfig(final Map<String, Object> konductorConfig) {
			throwIfAlreadyBuilt();
			requestMetadata.konductorConfig =
				konductorConfig != null ? new HashMap<>(konductorConfig) : new HashMap<>();
			return this;
		}

		/**
		 * Sets the state metadata for Edge Network.
		 *
		 * @param stateMetadata the metadata for the Edge Network
		 * @return this {@code RequestMetadata.Builder} instance
		 */
		RequestMetadata.Builder setStateMetadata(final Map<String, Object> stateMetadata) {
			throwIfAlreadyBuilt();
			requestMetadata.state = stateMetadata != null ? new HashMap<>(stateMetadata) : new HashMap<>();
			return this;
		}

		private void throwIfAlreadyBuilt() {
			if (didBuild) {
				throw new UnsupportedOperationException(
					"RequestMetadata.Builder - attempt to call setters after build() was called."
				);
			}
		}

		/**
		 * Builds and returns a new instance of {@code RequestMetadata}.
		 *
		 * @return new instance of {@code RequestMetadata}
		 * @throws UnsupportedOperationException if {@code build} was already called on this object
		 */
		RequestMetadata build() {
			throwIfAlreadyBuilt();
			didBuild = true;
			return requestMetadata;
		}
	}
}
