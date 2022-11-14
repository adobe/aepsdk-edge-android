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

import java.util.Map;

/**
 * Callback for fetching Shared States from the outside of the extension class.
 */
interface EdgeSharedStateCallback {
	/**
	 * Fetches the Shared State for the provided {@code event} from the specified {@code stateOwner}.
	 *
	 * @param stateOwner Shared state owner name
	 * @param event current event for which to fetch the shared state; if null is passed, the latest shared state is returned
	 * @return current shared state if found, null if shared state is pending or an error occurred
	 */
	Map<String, Object> getSharedState(final String stateOwner, final Event event);

	/**
	 * Sets a Shared State to the {@link EventHub}.
	 *
	 * @param state the state data to share
	 * @param event the {@link Event} used to version the shared state. If null is passed, the shared
	 *              state is versioned at the next available number.
	 */
	void setSharedState(final Map<String, Object> state, final Event event);
}
