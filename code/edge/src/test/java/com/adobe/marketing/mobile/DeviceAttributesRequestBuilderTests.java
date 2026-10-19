/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.adobe.marketing.mobile.util.FakeNamedCollection;
import com.adobe.marketing.mobile.util.JSONUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class DeviceAttributesRequestBuilderTests {

	private FakeNamedCollection fakeNamedCollection;
	private DeviceAttributesRequestBuilder builder;
	private Map<String, Object> implementationDetails;
	private Map<String, Object> identityMap;

	@Before
	public void setUp() {
		fakeNamedCollection = new FakeNamedCollection();
		builder = new DeviceAttributesRequestBuilder(fakeNamedCollection);
		implementationDetails =
			map("implementationDetails", map("name", "https://ns.adobe.com/experience/mobilesdk/android"));
		identityMap = map("ECID", Arrays.asList(map("id", "ecid-value")));
	}

	@Test
	public void testBuildPayload_usesSdkXdmAndMetaInsteadOfProducerValues() {
		saveStore("kndctr_org_cluster", "or2", 1800);
		final Map<String, Object> eventData = producerData();
		eventData.put("xdm", map("identityMap", map("producer", "identity")));
		eventData.put("meta", map("state", map("entries", "producer")));

		final Map<String, Object> payload = builder.buildPayload(eventData, implementationDetails, identityMap);

		final Map<String, Object> xdm = (Map<String, Object>) payload.get("xdm");
		assertEquals(implementationDetails.get("implementationDetails"), xdm.get("implementationDetails"));
		assertEquals(identityMap, xdm.get("identityMap"));
		assertEquals(2, xdm.size());

		final Map<String, Object> state = (Map<String, Object>) ((Map<String, Object>) payload.get("meta")).get(
				"state"
			);
		final List<Object> entries = (List<Object>) state.get("entries");
		assertEquals(1, entries.size());
		assertEquals("kndctr_org_cluster", ((Map<String, Object>) entries.get(0)).get("key"));
	}

	@Test
	public void testBuildPayload_preservesAppEmptyTokenAndTimezoneWithoutEventsEnvelope() {
		final Map<String, Object> eventData = producerData();
		eventData.put("tokens", new HashMap<>());

		final Map<String, Object> payload = builder.buildPayload(eventData, implementationDetails, identityMap);

		assertEquals(map("id", "com.adobe.app"), payload.get("app"));
		assertEquals(new HashMap<>(), payload.get("tokens"));
		assertEquals("America/Los_Angeles", payload.get("timezone"));
		assertFalse(payload.containsKey("events"));
		assertFalse(payload.containsKey("meta"));
		assertNotNull(payload.get("xdm"));
	}

	@Test
	public void testBuildPayload_discardsProducerXdmAndMetaWithoutSdkEnrichment() {
		final Map<String, Object> eventData = producerData();
		eventData.put("xdm", map("producer", "value"));
		eventData.put("meta", map("producer", "value"));

		final Map<String, Object> payload = builder.buildPayload(eventData, null, null);

		assertFalse(payload.containsKey("xdm"));
		assertFalse(payload.containsKey("meta"));
		assertEquals("America/Los_Angeles", payload.get("timezone"));
	}

	@Test
	public void testBuildPayload_onlyProducerSdkFieldsAndNoEnrichment_returnsNull() {
		final Map<String, Object> eventData = new HashMap<>();
		eventData.put("xdm", map("producer", "value"));
		eventData.put("meta", map("producer", "value"));

		assertNull(builder.buildPayload(eventData, null, null));
	}

	@Test
	public void testBuildPayload_nullOrEmptyEventData_returnsNull() {
		assertNull(builder.buildPayload(null, implementationDetails, identityMap));
		assertNull(builder.buildPayload(new HashMap<>(), implementationDetails, identityMap));
	}

	@Test
	public void testBuildPayload_doesNotModifyEventData() {
		final Map<String, Object> eventData = producerData();
		eventData.put("xdm", map("producer", "value"));

		builder.buildPayload(eventData, implementationDetails, identityMap);

		assertEquals(map("producer", "value"), eventData.get("xdm"));
	}

	@Test
	public void testBuildPayload_expiredStorePayloadsAreNotSent() throws Exception {
		saveStore("expiring", "value", 1);
		Thread.sleep(1100);

		final Map<String, Object> payload = builder.buildPayload(producerData(), implementationDetails, identityMap);

		assertFalse(payload.containsKey("meta"));
	}

	@Test
	public void testBuildRequestBody_isCompactJson() throws Exception {
		final String body = builder.buildRequestBody(producerData(), implementationDetails, identityMap);

		assertNotNull(body);
		assertFalse(body.contains("\n"));
		assertFalse(body.contains(": "));
		final Map<String, Object> decoded = JSONUtils.toMap(new JSONObject(body));
		assertEquals("America/Los_Angeles", decoded.get("timezone"));
		assertTrue(decoded.containsKey("xdm"));
	}

	@Test
	public void testBuildRequestBody_multipleEcids_keepsCurrentEcidAndPreservesSharedIdentityMap() throws Exception {
		final Map<String, Object> currentEcid = map("id", "current-edge-ecid");
		currentEcid.put("primary", true);
		currentEcid.put("authenticatedState", "ambiguous");
		final List<Map<String, Object>> ecids = Arrays.asList(
			currentEcid,
			map("id", "legacy-ecid"),
			map("id", "another-legacy-ecid")
		);
		identityMap.put("ECID", ecids);
		identityMap.put("Email", Arrays.asList(map("id", "test@example.com")));

		final JSONObject body = new JSONObject(
			builder.buildRequestBody(producerData(), implementationDetails, identityMap)
		);
		final JSONObject requestIdentityMap = body.getJSONObject("xdm").getJSONObject("identityMap");

		assertEquals(1, requestIdentityMap.getJSONArray("ECID").length());
		assertEquals(currentEcid, JSONUtils.toMap(requestIdentityMap.getJSONArray("ECID").getJSONObject(0)));
		assertEquals("test@example.com", requestIdentityMap.getJSONArray("Email").getJSONObject(0).getString("id"));
		assertEquals(ecids, identityMap.get("ECID"));
		assertEquals(3, ((List<?>) identityMap.get("ECID")).size());
	}

	@Test
	public void testBuildRequestBody_singleEcid_isPreserved() throws Exception {
		final JSONObject body = new JSONObject(
			builder.buildRequestBody(producerData(), implementationDetails, identityMap)
		);
		assertEquals(identityMap, JSONUtils.toMap(body.getJSONObject("xdm").getJSONObject("identityMap")));
	}

	@Test
	public void testBuildRequestBody_noData_returnsNull() {
		assertNull(builder.buildRequestBody(null, implementationDetails, identityMap));
	}

	@Test
	public void testBuildRequestBody_unencodablePayload_returnsNull() {
		final Map<String, Object> eventData = producerData();
		eventData.put("app", map("score", Double.NaN));

		assertNull(builder.buildRequestBody(eventData, implementationDetails, identityMap));
	}

	private void saveStore(final String key, final String value, final int maxAge) {
		final Map<String, Object> store = new HashMap<>();
		store.put(EdgeJson.Response.EventHandle.Store.KEY, key);
		store.put(EdgeJson.Response.EventHandle.Store.VALUE, value);
		store.put(EdgeJson.Response.EventHandle.Store.MAX_AGE, maxAge);
		new StoreResponsePayloadManager(fakeNamedCollection).saveStorePayloads(Arrays.asList(store));
	}

	private static Map<String, Object> producerData() {
		final Map<String, Object> data = new HashMap<>();
		data.put("app", map("id", "com.adobe.app"));
		data.put("tokens", map("fcm", "token"));
		data.put("timezone", "America/Los_Angeles");
		return data;
	}

	private static Map<String, Object> map(final String key, final Object value) {
		final Map<String, Object> map = new HashMap<>();
		map.put(key, value);
		return map;
	}
}
