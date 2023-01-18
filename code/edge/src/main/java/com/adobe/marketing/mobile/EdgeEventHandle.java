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

import com.adobe.marketing.mobile.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * The {@link EdgeEventHandle} is a response fragment from Adobe Edge Network service for a sent XDM Experience Event.
 * One event can receive none, one or multiple {@link EdgeEventHandle}(s) as response.
 */
public class EdgeEventHandle {

	private final int eventIndex;
	private final String type;
	private final List<Map<String, Object>> payload;

	/**
	 * Extracts the Edge event handle information (eventIndex, type and payload) from the provided {@link JSONObject}
	 * and creates an instance of {@link EdgeEventHandle}.
	 *
	 * @param handle the event handle from the network response; cannot be null
	 * @throws IllegalArgumentException if {@code handle} is null
	 */
	EdgeEventHandle(final JSONObject handle) throws IllegalArgumentException {
		if (handle == null) {
			throw new IllegalArgumentException("The Event handle cannot be null");
		}

		String tempType = handle.optString(EdgeJson.Response.EventHandle.TYPE);
		this.type = StringUtils.isNullOrEmpty(tempType) ? null : tempType;
		this.eventIndex = handle.optInt(EdgeJson.Response.EventHandle.EVENT_INDEX, 0);
		this.payload = Utils.toListOfMaps(handle.optJSONArray(EdgeJson.Response.EventHandle.PAYLOAD));
	}

	/**
	 * @return the payload type or null if not found in the {@link JSONObject} response
	 */
	public String getType() {
		return type;
	}

	/**
	 * @return the event payload values for this {@link EdgeEventHandle} or null if not found in the {@link JSONObject} response
	 */
	public List<Map<String, Object>> getPayload() {
		return payload;
	}

	/**
	 * @return Encodes the event to which this handle is attached as the index in the events array in EdgeRequest
	 * If not found in the {@link JSONObject} response, it fallbacks to 0 as per Edge Network spec
	 */
	int getEventIndex() {
		return eventIndex;
	}

	/**
	 * @return this {@link EdgeEventHandle} as Map or an empty map if this {@code EdgeEventHandle}
	 * contains no data. The payload, if present, is deep copied.
	 */
	Map<String, Object> toMap() {
		final Map<String, Object> handleToMap = new HashMap<>();

		if (type != null) {
			handleToMap.put(EdgeJson.Response.EventHandle.TYPE, type);
		}

		if (payload != null) {
			handleToMap.put(EdgeJson.Response.EventHandle.PAYLOAD, Utils.deepCopy(payload));
		}

		return handleToMap;
	}

	@Override
	public String toString() {
		Map<String, Object> map = toMap();
		return map != null ? map.toString() : "";
	}
}
