/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import org.json.JSONObject;
import org.junit.Test;

public class StateMetadataTests {

	@Test
	public void testStateMetadata_NullConstructor_IsNotNull() {
		assertNotNull(new StateMetadata(null));
		assertNotNull(new StateMetadata(null).toObjectMap());
		assertNotNull(new StateMetadata(null).toObjectMap().size() == 0);
	}

	@Test
	public void testStateMetadata_EmptyConstructor_IsNotNull() {
		assertNotNull(new StateMetadata(new HashMap<String, StoreResponsePayload>()));
		assertNotNull(new StateMetadata(new HashMap<String, StoreResponsePayload>()).toObjectMap());
	}

	@Test
	public void testStateMetadata_ValidConstructorSingle_ToObjectMapIsCorrect() {
		HashMap<String, StoreResponsePayload> payloadMap = new HashMap<>();
		payloadMap.put("key1", buildStoreResponsePayload("key1", "value1", 7200));

		StateMetadata metadata = new StateMetadata(payloadMap);

		ArrayList<StoreResponsePayload> payloads = (ArrayList<StoreResponsePayload>) metadata
			.toObjectMap()
			.get("entries");
		assertNotNull(payloads);
		assertEquals(1, payloads.size());
	}

	@Test
	public void testStateMetadata_ValidConstructorMultiple_ToObjectMapIsCorrect() {
		HashMap<String, StoreResponsePayload> payloadMap = new HashMap<>();
		payloadMap.put("key1", buildStoreResponsePayload("key1", "value1", 7200));
		payloadMap.put("key2", buildStoreResponsePayload("key2", "value2", 7200));
		payloadMap.put("key3", buildStoreResponsePayload("key3", "value3", 7200));

		StateMetadata metadata = new StateMetadata(payloadMap);

		ArrayList<HashMap<String, Object>> payloads = (ArrayList<HashMap<String, Object>>) metadata
			.toObjectMap()
			.get("entries");
		assertNotNull(payloads);
		assertEquals(3, payloads.size());

		StoreResponsePayload payload1 = StoreResponsePayload.fromJsonObject(new JSONObject(payloads.get(0)));
		assertNotNull(payload1);
		assertEquals("key1", payload1.getKey());
		assertEquals("value1", payload1.getValue());
		assertEquals(new Integer(7200), payload1.getMaxAge());

		StoreResponsePayload payload2 = StoreResponsePayload.fromJsonObject(new JSONObject(payloads.get(1)));
		assertNotNull(payload2);
		assertEquals("key2", payload2.getKey());
		assertEquals("value2", payload2.getValue());
		assertEquals(new Integer(7200), payload2.getMaxAge());

		StoreResponsePayload payload3 = StoreResponsePayload.fromJsonObject(new JSONObject(payloads.get(2)));
		assertNotNull(payload3);
		assertEquals("key3", payload3.getKey());
		assertEquals("value3", payload3.getValue());
		assertEquals(new Integer(7200), payload3.getMaxAge());
	}

	private StoreResponsePayload buildStoreResponsePayload(final String key, final String value, final int maxAge) {
		HashMap<String, Object> map = new HashMap<>();
		map.put(EdgeJson.Response.EventHandle.Store.KEY, key);
		map.put(EdgeJson.Response.EventHandle.Store.VALUE, value);
		map.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, maxAge);

		return StoreResponsePayload.fromJsonObject(new JSONObject(map));
	}
}
