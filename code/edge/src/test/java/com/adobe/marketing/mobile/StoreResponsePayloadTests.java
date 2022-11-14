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
import static junit.framework.TestCase.assertNull;

import java.util.HashMap;
import org.json.JSONObject;
import org.junit.Test;

public class StoreResponsePayloadTests {

	@Test
	public void testStoreResponsePayload_fromJsonObjectNull() {
		assertNull(StoreResponsePayload.fromJsonObject(null));
	}

	@Test
	public void testStoreResponsePayloadFromMap_WhenMapValid() {
		HashMap<String, Object> map = new HashMap<>();
		map.put(EdgeJson.Response.EventHandle.Store.KEY, "kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optout");
		map.put(EdgeJson.Response.EventHandle.Store.VALUE, "general=true");
		map.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 7200);

		StoreResponsePayload storeResponsePayload = StoreResponsePayload.fromJsonObject(new JSONObject(map));

		assertEquals(map.get(EdgeJson.Response.EventHandle.Store.KEY), storeResponsePayload.getKey());
		assertEquals(map.get(EdgeJson.Response.EventHandle.Store.VALUE), storeResponsePayload.getValue());
		assertEquals(map.get(EdgeJson.Response.EventHandle.Store.MAX_AGE), storeResponsePayload.getMaxAge());
	}

	@Test
	public void testStoreResponsePayloadFromMap_WhenKeyMissing_IsNull() {
		HashMap<String, Object> map = new HashMap<>();
		map.put(EdgeJson.Response.EventHandle.Store.VALUE, "general=true");
		map.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 7200);

		StoreResponsePayload storeResponsePayload = StoreResponsePayload.fromJsonObject(new JSONObject(map));

		assertNull(storeResponsePayload);
	}

	@Test
	public void testStoreResponsePayloadFromMap_WhenValueMissing_IsNull() {
		HashMap<String, Object> map = new HashMap<>();
		map.put(EdgeJson.Response.EventHandle.Store.KEY, "kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optout");
		map.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 7200);

		StoreResponsePayload storeResponsePayload = StoreResponsePayload.fromJsonObject(new JSONObject(map));

		assertNull(storeResponsePayload);
	}

	@Test
	public void testStoreResponsePayloadFromMap_WhenMaxAgeMissing_IsNull() {
		HashMap<String, Object> map = new HashMap<>();
		map.put(EdgeJson.Response.EventHandle.Store.KEY, "kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optout");
		map.put(EdgeJson.Response.EventHandle.Store.VALUE, "general=true");

		StoreResponsePayload storeResponsePayload = StoreResponsePayload.fromJsonObject(new JSONObject(map));

		assertNull(storeResponsePayload);
	}
}
