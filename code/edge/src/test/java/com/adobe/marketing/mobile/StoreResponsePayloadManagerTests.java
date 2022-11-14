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
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

import com.adobe.marketing.mobile.util.FakeNamedCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class StoreResponsePayloadManagerTests {

	private static final String STORE_PAYLOADS = "storePayloads";

	private FakeNamedCollection fakeNamedCollection;

	@Before
	public void setup() {
		fakeNamedCollection = new FakeNamedCollection();
	}

	@Test
	public void getActiveStores_isNull_whenDataStoreIsNull() {
		assertNull(new StoreResponsePayloadManager(null).getActiveStores());
	}

	@Test
	public void getActiveStores_isCorrect_whenRecordsInDataStore() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		manager.saveStorePayloads(buildStorePayloads());

		assertEquals(2, manager.getActiveStores().size());
	}

	@Test
	public void getActiveStores_evictsExpiredKey_whenCurrentDatePassesExpiry() throws Exception {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		manager.saveStorePayloads(buildStorePayloads());

		assertEquals(2, manager.getActiveStores().size());
		Thread.sleep(2100); // wait for record to expire
		assertEquals(1, manager.getActiveStores().size());
	}

	@Test
	public void saveStorePayloads_noException_whenDataStoreIsNull() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(null);
		manager.saveStorePayloads(null);
		assertTrue(true);
	}

	@Test
	public void saveStorePayloads_returnsNull_whenEmptyList() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(null);
		manager.saveStorePayloads(new ArrayList<Map<String, Object>>());
		assertNull(manager.getActiveStores());
	}

	@Test
	public void saveStorePayloads_savesPayloads_whenValid() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		manager.saveStorePayloads(buildStorePayloads());

		assertEquals(2, manager.getActiveStores().size());
	}

	@Test
	public void saveStorePayloads_overwritesPayloads_whenDuplicateKeys() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		manager.saveStorePayloads(buildStorePayloads());
		manager.saveStorePayloads(buildStorePayloads());

		// Overwrites, no duplicate keys
		assertEquals(2, manager.getActiveStores().size());
	}

	@Test
	public void saveStorePayloads_overwritesPayloads_whenDuplicateKeysAndNewValues() {
		// setup
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		manager.saveStorePayloads(buildStorePayloads());

		Map<String, StoreResponsePayload> originalPayloads = manager.getActiveStores();

		List<Map<String, Object>> newPaylods = buildStorePayloads();
		newPaylods.get(0).put(EdgeJson.Response.EventHandle.Store.VALUE, "general=false");
		newPaylods.get(0).put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 8000);
		newPaylods.get(0).remove(EdgeJson.Response.EventHandle.Store.EXPIRY_DATE);
		newPaylods.get(1).put(EdgeJson.Response.EventHandle.Store.VALUE, "newValue");
		newPaylods.get(1).put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 10);
		newPaylods.get(1).remove(EdgeJson.Response.EventHandle.Store.EXPIRY_DATE);

		// Overwrites and updates
		manager.saveStorePayloads(newPaylods);

		Map<String, StoreResponsePayload> activeStores = manager.getActiveStores();
		assertEquals(2, activeStores.size());

		StoreResponsePayload payload1 = activeStores.get("kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optout");
		StoreResponsePayload payload2 = activeStores.get("kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optin");

		assertEquals("kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optout", payload1.getKey());
		assertEquals("general=false", payload1.getValue());
		assertEquals(new Integer(8000), payload1.getMaxAge());
		assertTrue(payload1.getExpiryDate() > originalPayloads.get(payload1.getKey()).getExpiryDate());

		assertEquals("kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optin", payload2.getKey());
		assertEquals("newValue", payload2.getValue());
		assertEquals(new Integer(10), payload2.getMaxAge());
		assertTrue(payload2.getExpiryDate() > originalPayloads.get(payload2.getKey()).getExpiryDate());
	}

	@Test
	public void deleteStoreResponses_noException_whenDatastoreIsNull() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(null);
		ArrayList<String> toDelete = new ArrayList<>();
		toDelete.add("badkey");

		manager.deleteStoreResponses(toDelete);
		assertTrue(true);
	}

	@Test
	public void deleteStoreResponses_noException_whenKeyIsInvalid() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(null);
		manager.deleteStoreResponses(null);
		assertTrue(true);
	}

	@Test
	public void deleteStoreResponses_deletesKey_whenValidKey() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		manager.saveStorePayloads(buildStorePayloads());

		ArrayList<String> toDelete = new ArrayList<>();
		toDelete.add("kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optout");
		toDelete.add("kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optin");

		manager.deleteStoreResponses(toDelete);
		assertEquals(0, manager.getActiveStores().size());
	}

	@Test
	public void deleteStorePayloads_whenStoredValues_deletesAll() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		manager.saveStorePayloads(buildStorePayloads());
		assertNotNull(fakeNamedCollection.getMap(STORE_PAYLOADS));

		manager.deleteAllStorePayloads();
		assertNull(manager.getActiveStores());
		assertNull(fakeNamedCollection.getMap(STORE_PAYLOADS));
	}

	@Test
	public void deleteAllStorePayloads_whenNoStoredValues_deletesAll() {
		StoreResponsePayloadManager manager = new StoreResponsePayloadManager(fakeNamedCollection);
		assertNull(fakeNamedCollection.getMap(STORE_PAYLOADS));

		manager.deleteAllStorePayloads();
		assertNull(manager.getActiveStores());
		assertNull(fakeNamedCollection.getMap(STORE_PAYLOADS));
	}

	@Test
	public void deleteAllStorePayloads_whenDataStoreIsNull_doesNotCrash() {
		new StoreResponsePayloadManager(null).deleteAllStorePayloads();
	}

	private List<Map<String, Object>> buildStorePayloads() {
		List<Map<String, Object>> payloads = new ArrayList<>();

		HashMap<String, Object> map = new HashMap<>();
		map.put(EdgeJson.Response.EventHandle.Store.KEY, "kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optout");
		map.put(EdgeJson.Response.EventHandle.Store.VALUE, "general=true");
		map.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 7200);

		HashMap<String, Object> map1 = new HashMap<>();
		map1.put(EdgeJson.Response.EventHandle.Store.KEY, "kndctr_53A16ACB5CC1D3760A495C99_AdobeOrg_optin");
		map1.put(EdgeJson.Response.EventHandle.Store.VALUE, "");
		map1.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 2);

		payloads.add(map);
		payloads.add(map1);

		return payloads;
	}
}
