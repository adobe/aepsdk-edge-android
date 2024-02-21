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

package com.adobe.marketing.mobile.xdm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Formatters {

	/**
	 * Serialize a list of {@code Property} elements to a list of XDM formatted maps.
	 * Calls {@link Property#serializeToXdm()} on each element in the list.
	 *
	 * @param listProperty list of {@link Property} elements
	 * @return a list of {@link Property} elements serialized to XDM map structure
	 */
	public static List<Map<String, Object>> serializeFromList(final List<? extends Property> listProperty) {
		List<Map<String, Object>> serializedList = new ArrayList<>();

		if (listProperty == null) {
			return serializedList;
		}

		for (Property property : listProperty) {
			if (property != null) {
				serializedList.add(property.serializeToXdm());
			}
		}

		return serializedList;
	}

	private Formatters() {}
}
