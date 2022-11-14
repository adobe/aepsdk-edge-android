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
 * Callback for fetching Edge state from the outside of the extension class.
 */
interface EdgeStateCallback {
	/**
	 * Returns the {@link com.adobe.marketing.mobile.EdgeJson.Event.ImplementationDetails} for the current session.
	 * @return {@code ImplementationDetails} for the current session or null if none exist
	 */
	Map<String, Object> getImplementationDetails();

	/**
	 * Returns the Edge Network location hint from the {@code EdgeExtension}.
	 * @return the Edge Network location hint or null if no location hint is set or the location hint expired.
	 */
	String getLocationHint();

	/**
	 * Set the location hint for the Edge Network. The new location hint and expire date (calculated from the {@code ttlSeconds}
	 * are updated in memory and persistent storage. If the new location hint is different from the previous, then a
	 * shared state is also created with the new hint. A null {@code hint} value clears the location hint
	 * in memory, persisted storage, and create a new shared state if a previous location hint was set.
	 * @param hint the new Edge Network location hint to set
	 * @param ttlSeconds the time-to-live for the location hint
	 */
	void setLocationHint(final String hint, final int ttlSeconds);
}
