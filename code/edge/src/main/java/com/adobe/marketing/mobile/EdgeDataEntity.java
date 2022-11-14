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

import java.util.Collections;
import java.util.Map;

/**
 * Class that encapsulates the data to be queued persistently for the {@link EdgeExtension}
 */
final class EdgeDataEntity {

	private final Event event;
	private final Map<String, Object> configuration;
	private final Map<String, Object> identityMap;

	/**
	 * Creates a read-only {@link EdgeDataEntity} object with the provided information.
	 *
	 * @param event an {@link Event}, should not be null
	 * @param config the Edge configuration for this {@code event}
	 * @param identityMap the identity information for this {@code event}
	 * @throws IllegalArgumentException if the provided {@code event} is null
	 */
	EdgeDataEntity(final Event event, final Map<String, Object> config, final Map<String, Object> identityMap) {
		if (event == null) {
			throw new IllegalArgumentException();
		}

		this.event = event;
		this.configuration = config == null ? Collections.emptyMap() : Utils.deepCopy(config);
		this.identityMap = identityMap == null ? Collections.emptyMap() : Utils.deepCopy(identityMap);
	}

	/**
	 * Creates a read-only {@link EdgeDataEntity} object with the provided information.
	 *
	 * @param event an {@link Event}, should not be null
	 * @throws IllegalArgumentException if the provided {@code event} is null
	 */
	EdgeDataEntity(final Event event) {
		this(event, null, null);
	}

	/**
	 * @return the {@link Event} cannot be null based on constructor
	 */
	Event getEvent() {
		return event;
	}

	/**
	 * @return the Edge configuration for this {@link EdgeDataEntity} as a read-only {@code Map<String, Object>}.
	 * Attempts to modify the returned map, whether direct or via its collection views, result in an {@link UnsupportedOperationException}.
	 */
	Map<String, Object> getConfiguration() {
		return Collections.unmodifiableMap(configuration);
	}

	/**
	 * @return the identity information for this {@link EdgeDataEntity} as a read-only {@code Map<String, Object>}.
	 * Attempts to modify the returned map, whether direct or via its collection views, result in an {@link UnsupportedOperationException}.
	 */
	Map<String, Object> getIdentityMap() {
		return Collections.unmodifiableMap(identityMap);
	}
}
