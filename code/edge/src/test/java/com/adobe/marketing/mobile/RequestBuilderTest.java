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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNotSame;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.NamedCollection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class RequestBuilderTest {

	static final String IDENTITY_MAP_KEY = "identityMap";
	private RequestBuilder requestBuilder;

	@Mock
	NamedCollection mockNamedCollection;

	@Before
	public void setup() {
		requestBuilder = new RequestBuilder(mockNamedCollection);
	}

	// getPayloadWithExperienceEvents tests
	@Test
	public void getPayloadWithExperienceEvents_returnsNull_whenEventsListIsNull() {
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(null);
		assertNull(payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_returnsNull_whenEventsListIsEmpty() {
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(new ArrayList<>());
		assertNull(payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotFail_whenDataStoreIsNull() throws Exception {
		RequestBuilder requestBuilder = new RequestBuilder(null);
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("value", payloadMap.get("events[0].data.key"));
		assertEquals(events.get(0).getUniqueIdentifier(), payloadMap.get("events[0].xdm._id"));
		assertEquals(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
	}

	@Test
	public void getPayloadWithExperienceEvents_setsEventTimestampAndEventId_whenEventsListIsValid() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals(events.get(0).getUniqueIdentifier(), payloadMap.get("events[0].xdm._id"));
		assertEquals(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotOverwriteTimestamp_whenValidTimestampPresent() throws Exception {
		String testTimestamp = "2021-06-03T00:00:20Z";
		List<Event> events = getSingleEvent(getExperienceEventData("value", null, testTimestamp));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);
		assertNotSame(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
		assertEquals(testTimestamp, payloadMap.get("events[0].xdm.timestamp"));
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotOverwriteTimestamp_whenInvalidTimestampPresent()
		throws Exception {
		String testTimestamp = "invalidTimestamp";
		List<Event> events = getSingleEvent(getExperienceEventData("value", null, testTimestamp));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);
		assertNotSame(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
		assertEquals(testTimestamp, payloadMap.get("events[0].xdm.timestamp"));
	}

	@Test
	public void getPayloadWithExperienceEvents_setsEventTimestamp_whenProvidedTimestampIsEmpty() throws Exception {
		String testTimestamp = "";
		List<Event> events = getSingleEvent(getExperienceEventData("value", null, testTimestamp));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);
		assertEquals(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
	}

	@Test
	public void getPayloadWithExperienceEvents_setsCollectMeta_whenEventContainsDatasetId() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", "5dd603781b95cc18a83d42ce"));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("5dd603781b95cc18a83d42ce", payloadMap.get("events[0].meta.collect.datasetId"));
		assertFalse(payloadMap.containsKey("events[0].datasetId")); // verify internal key is removed
	}

	@Test
	public void getPayloadWithExperienceEvents_setsCollectMeta_whenEventContainsDatasetIdWithWhitespace()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", "   5dd603781b95cc18a83d42ce   "));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("5dd603781b95cc18a83d42ce", payloadMap.get("events[0].meta.collect.datasetId"));
		assertFalse(payloadMap.containsKey("events[0].datasetId")); // verify internal key is removed
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotSetCollectMeta_whenEventDoesNotContainDatasetId()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertFalse(payloadMap.containsKey("events[0].meta"));
		assertFalse(payloadMap.containsKey("events[0].datasetId")); // verify internal key is removed
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotCollectMeta_whenEventContainsEmptyDatasetId() throws Exception {
		List<Map<String, Object>> eventsData = new ArrayList<>();
		eventsData.add(getExperienceEventData("one", ""));
		eventsData.add(getExperienceEventData("two", "   "));
		List<Event> events = getMultipleEvents(eventsData);

		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 2);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertFalse(payloadMap.containsKey("events[0].meta"));
		assertFalse(payloadMap.containsKey("events[0].datasetId")); // verify internal key not set
		assertFalse(payloadMap.containsKey("events[1].meta"));
		assertFalse(payloadMap.containsKey("events[1].datasetId")); // verify internal key not set
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotCollectMeta_whenEventContainsNullDatasetId() throws Exception {
		Map<String, Object> collectMeta = new HashMap<>();
		collectMeta.put(EdgeJson.Event.Metadata.DATASET_ID, null);
		Map<String, Object> eventMeta = new HashMap<>();
		eventMeta.put(EdgeJson.Event.Metadata.COLLECT, collectMeta);

		Map<String, Object> eventdata = getExperienceEventData("value");
		eventdata.put(EdgeJson.Event.METADATA, eventMeta);

		List<Event> events = getSingleEvent(eventdata);
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals(events.get(0).getUniqueIdentifier(), payloadMap.get("events[0].xdm._id"));
		assertEquals(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
		assertFalse(payloadMap.containsKey("events[0].meta"));
		assertFalse(payloadMap.containsKey("events[0].datasetId")); // verify internal key not set
	}

	@Test
	public void getPayloadWithExperienceEvents_addsMultipleEvents_whenEventsListContainsMultipleEvents()
		throws Exception {
		List<Map<String, Object>> eventsData = new ArrayList<>();
		eventsData.add(getExperienceEventData("one"));
		eventsData.add(getExperienceEventData("two"));
		List<Event> events = getMultipleEvents(eventsData);

		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 2);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("one", payloadMap.get("events[0].data.key"));
		assertEquals(events.get(0).getUniqueIdentifier(), payloadMap.get("events[0].xdm._id"));
		assertEquals(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
		assertFalse(payloadMap.containsKey("events[0].meta"));

		assertEquals("two", payloadMap.get("events[1].data.key"));
		assertEquals(events.get(1).getUniqueIdentifier(), payloadMap.get("events[1].xdm._id"));
		assertEquals(formatTimestamp(events.get(1).getTimestamp()), payloadMap.get("events[1].xdm.timestamp"));
		assertFalse(payloadMap.containsKey("events[1].meta"));
	}

	@Test
	public void getPayloadWithExperienceEvents_addsMultipleEventsWithDatasetId_whenEventsListContainsMultipleEventsWithDatasetId()
		throws Exception {
		List<Map<String, Object>> eventsData = new ArrayList<>();
		eventsData.add(getExperienceEventData("one", "abc"));
		eventsData.add(getExperienceEventData("two", "123"));
		List<Event> events = getMultipleEvents(eventsData);

		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 2);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("one", payloadMap.get("events[0].data.key"));
		assertEquals(events.get(0).getUniqueIdentifier(), payloadMap.get("events[0].xdm._id"));
		assertEquals(formatTimestamp(events.get(0).getTimestamp()), payloadMap.get("events[0].xdm.timestamp"));
		assertEquals("abc", payloadMap.get("events[0].meta.collect.datasetId"));
		assertFalse(payloadMap.containsKey("events[0].datasetId")); // verify internal key is removed

		assertEquals("two", payloadMap.get("events[1].data.key"));
		assertEquals(events.get(1).getUniqueIdentifier(), payloadMap.get("events[1].xdm._id"));
		assertEquals(formatTimestamp(events.get(1).getTimestamp()), payloadMap.get("events[1].xdm.timestamp"));
		assertEquals("123", payloadMap.get("events[1].meta.collect.datasetId"));
		assertFalse(payloadMap.containsKey("events[1].datasetId")); // verify internal key is removed
	}

	@Test
	public void getPayloadWithExperienceEvents_addsKonductorConfigWithStreaming_whenStreamingEnabled()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		requestBuilder.enableResponseStreaming("\u0000", "\n");
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("true", payloadMap.get("meta.konductorConfig.streaming.enabled"));
		assertEquals("\u0000", payloadMap.get("meta.konductorConfig.streaming.recordSeparator"));
		assertEquals("\n", payloadMap.get("meta.konductorConfig.streaming.lineFeed"));
	}

	@Test
	public void getPayloadWithExperienceEvents_addsIdentityMap_whenEcidGiven() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		final String jsonStr =
			"{\n" +
			"      \"identityMap\": {\n" +
			"        \"ECID\": [\n" +
			"          {\n" +
			"            \"id\":" +
			"myECID" +
			",\n" +
			"            \"authenticatedState\": \"ambiguous\",\n" +
			"            \"primary\": false\n" +
			"          }\n" +
			"        ]\n" +
			"      }\n" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityState = Utils.toMap(jsonObject);
		requestBuilder.addXdmPayload(identityState);
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("myECID", payloadMap.get("xdm.identityMap.ECID[0].id"));
	}

	@Test
	public void getPayloadWithExperienceEvents_addsStoreMetadataWithPayloads_whenPayloadsInDataStore()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		setupMockStoreMetadata();
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);
		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_optout", payloadMap.get("meta.state.entries[0].key"));
		assertEquals("", payloadMap.get("meta.state.entries[0].value"));
		assertEquals("7200", payloadMap.get("meta.state.entries[0].maxAge"));

		assertEquals("kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_identity", payloadMap.get("meta.state.entries[1].key"));
		assertEquals(
			"Cg8KBnN5bmNlZBIBMRiA9SQKMwoERUNJRBImNjA5MjY2MDcwMDIxMDI0NzMyNDYwNDgzMTg3MjA0MDkxMDQ3NjQYgIGjEBCFwbjv_i0=",
			payloadMap.get("meta.state.entries[1].value")
		);
		assertEquals("34128000", payloadMap.get("meta.state.entries[1].maxAge"));
	}

	@Test
	public void getPayloadWithExperienceEvents_NoAddStoreMetadata_whenNullPayloadInDataStore() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		setupMockStoreMetadataNullDatastoreKey();
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);
		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertNull(payloadMap.get("meta.state"));
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotAddStoreMetadata_whenDatastoreIsEmpty() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		setupMockStoreMetadataEmpty();
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);
		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);
		assertNull(payloadMap.get("meta.state")); // StateMetadata not added if empty
	}

	// getConsentPayload tests
	@Test
	public void getConsentPayload_returnsNull_whenEventNull() {
		JSONObject payload = requestBuilder.getConsentPayload(null);
		assertNull(payload);
	}

	@Test
	public void getConsentPayload_returnsNull_whenEventsListIsEmpty() {
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource").setEventData(null).build()
		);
		assertNull(payload);
	}

	@Test
	public void getConsentPayload_returnsNull_whenConsentsMissing() {
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource")
				.setEventData(
					new HashMap<String, Object>() {
						{
							put("missing", "consents");
						}
					}
				)
				.build()
		);
		assertNull(payload);
	}

	@Test
	public void getConsentPayload_returnsNull_whenConsentsPresentWithEmptyValue() {
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource")
				.setEventData(
					new HashMap<String, Object>() {
						{
							put("consents", new HashMap<>());
						}
					}
				)
				.build()
		);
		assertNull(payload);
	}

	@Test
	public void getConsentPayload_happy() throws Exception {
		final Map<String, Object> collectConsent = new HashMap<String, Object>() {
			{
				put(
					"collect",
					new HashMap<String, Object>() {
						{
							put("val", "y");
						}
					}
				);
			}
		};
		Map<String, Object> consentsEventData = new HashMap<String, Object>() {
			{
				put("consents", collectConsent);
			}
		};
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource").setEventData(consentsEventData).build()
		);
		assertNotNull(payload);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);
		assertEquals(5, payloadMap.size()); // 4 from standard fields, 1 from collect value
		assertStandardFieldsInConsentUpdatesPayload(payload);
		// assert consent value provided in the consent event
		assertEquals("y", payloadMap.get("consent[0].value.collect.val"));
	}

	@Test
	public void getConsentPayload_doesNotAddStoreMetadata() throws Exception {
		setupMockStoreMetadata();
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource")
				.setEventData(
					new HashMap<String, Object>() {
						{
							put(
								"consents",
								new HashMap<String, Object>() {
									{
										put(
											"collect",
											new HashMap<String, Object>() {
												{
													put("val", "y");
												}
											}
										);
									}
								}
							);
						}
					}
				)
				.build()
		);
		assertStandardFieldsInConsentUpdatesPayload(payload);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);
		assertNull(payloadMap.get("meta.state")); // StateMetadata not added to consent requests
	}

	@Test
	public void getConsentPayload_addsIdentityMap() throws Exception {
		final String jsonStr =
			"{\n" +
			"      \"identityMap\": {\n" +
			"        \"ECID\": [\n" +
			"          {\n" +
			"            \"id\":" +
			"1234" +
			",\n" +
			"            \"authenticatedState\": \"ambiguous\",\n" +
			"            \"primary\": false\n" +
			"          }\n" +
			"        ]\n" +
			"      }\n" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityState = Utils.toMap(jsonObject);
		requestBuilder.addXdmPayload(identityState);
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource")
				.setEventData(
					new HashMap<String, Object>() {
						{
							put(
								"consents",
								new HashMap<String, Object>() {
									{
										put(
											"collect",
											new HashMap<String, Object>() {
												{
													put("val", "y");
												}
											}
										);
									}
								}
							);
						}
					}
				)
				.build()
		);

		assertStandardFieldsInConsentUpdatesPayload(payload);
		assertNotNull(payload.getJSONObject(IDENTITY_MAP_KEY));
	}

	@Test
	public void getConsentPayload_skipsIdentityMap_whenNotSet() throws Exception {
		// adding for completeness, but this scenario is not expected in usual flows
		// as the identity shared state is required and has at a min an ECID
		final String jsonStr = "{\"identityMap\": {} }";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityState = Utils.toMap(jsonObject);
		requestBuilder.addXdmPayload(identityState);
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource")
				.setEventData(
					new HashMap<String, Object>() {
						{
							put(
								"consents",
								new HashMap<String, Object>() {
									{
										put(
											"collect",
											new HashMap<String, Object>() {
												{
													put("val", "y");
												}
											}
										);
									}
								}
							);
						}
					}
				)
				.build()
		);

		assertStandardFieldsInConsentUpdatesPayload(payload);
		assertFalse(payload.has(IDENTITY_MAP_KEY));
	}

	@Test
	public void getConsentPayload_addsKonductorConfigWithStreaming_whenStreamingEnabled() throws Exception {
		requestBuilder.enableResponseStreaming("\u0000", "\n");
		JSONObject payload = requestBuilder.getConsentPayload(
			new Event.Builder("test", "testType", "testSource")
				.setEventData(
					new HashMap<String, Object>() {
						{
							put(
								"consents",
								new HashMap<String, Object>() {
									{
										put(
											"collect",
											new HashMap<String, Object>() {
												{
													put("val", "y");
												}
											}
										);
									}
								}
							);
						}
					}
				)
				.build()
		);

		assertStandardFieldsInConsentUpdatesPayload(payload);

		Map<String, String> payloadMap = new HashMap<>();
		addKeys("", new ObjectMapper().readTree(payload.toString()), payloadMap);

		assertEquals("true", payloadMap.get("meta.konductorConfig.streaming.enabled"));
		assertEquals("\u0000", payloadMap.get("meta.konductorConfig.streaming.recordSeparator"));
		assertEquals("\n", payloadMap.get("meta.konductorConfig.streaming.lineFeed"));
	}

	// assert on standard fields included in the payload of all consent requests
	private void assertStandardFieldsInConsentUpdatesPayload(final JSONObject payload) throws Exception {
		assertNotNull(payload);
		JSONObject query = payload.getJSONObject("query");
		assertNotNull(query);
		JSONObject consentQuery = query.getJSONObject("consent");
		assertNotNull(consentQuery);
		assertEquals(1, consentQuery.length());
		assertEquals("update", consentQuery.getString("operation"));

		JSONArray consent = payload.optJSONArray("consent");
		assertNotNull(consent);
		assertEquals(1, consent.length());
		assertEquals("Adobe", consent.getJSONObject(0).getString("standard"));
		assertEquals("2.0", consent.getJSONObject(0).getString("version"));
	}

	private void assertNumberOfEvents(final JSONObject payload, final int expectedEventCount) {
		JSONArray events;

		try {
			events = payload.getJSONArray("events");
		} catch (JSONException e) {
			if (expectedEventCount != 0) {
				fail(
					"Request payload does not contain 'events' array but expected to have " +
					expectedEventCount +
					" events."
				);
			}

			return;
		}

		assertEquals(expectedEventCount, events.length());
	}

	private List<Event> getSingleEvent(final Map<String, Object> eventData) {
		final Event event = new Event.Builder("Request Builder Test Event", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(eventData)
			.build();

		return new ArrayList<Event>() {
			{
				add(event);
			}
		};
	}

	private List<Event> getMultipleEvents(final List<Map<String, Object>> eventData) {
		List<Event> events = new ArrayList<>(eventData.size());

		for (Map<String, Object> data : eventData) {
			Event event = new Event.Builder("Request Builder Test Event", EventType.EDGE, EventSource.REQUEST_CONTENT)
				.setEventData(data)
				.build();
			events.add(event);
		}

		return events;
	}

	private Map<String, Object> getExperienceEventData(final String value, final String datasetId) throws IOException {
		return getExperienceEventData(value, datasetId, null);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getExperienceEventData(
		final String value,
		final String datasetId,
		final String timestamp
	) throws IOException {
		final String spaces = "    ";
		StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		builder.append(spaces).append("\"xdm\": {\n");
		builder.append(spaces).append(spaces).append("\"stitch\": \"abc_stitch\",\n");
		builder.append(spaces).append(spaces).append("\"eventType\": \"view:load\"\n");

		if (timestamp != null) {
			builder.append(spaces).append(spaces).append(",\"timestamp\": \"").append(timestamp).append("\"\n");
		}

		builder.append(spaces).append("}");

		if (value != null || datasetId != null) {
			builder.append(",\n");
		} else {
			builder.append("\n");
		}

		if (value != null) {
			builder.append(spaces).append("\"data\": {\n");
			builder.append(spaces).append(spaces).append("\"key\": \"").append(value).append("\"\n");
			builder.append(spaces).append("}");

			if (datasetId != null) {
				builder.append(",\n");
			} else {
				builder.append("\n");
			}
		}

		if (datasetId != null) {
			builder.append(spaces).append("\"datasetId\": \"").append(datasetId).append("\"\n");
		}

		builder.append("}");

		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(builder.toString(), Map.class);
	}

	private Map<String, Object> getExperienceEventData(final String value) throws IOException {
		return getExperienceEventData(value, null);
	}

	private String formatTimestamp(final long timestamp) {
		final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(TIMESTAMP_FORMAT);
		simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return simpleDateFormat.format(new Date(timestamp));
	}

	private void setupMockStoreMetadataNullDatastoreKey() {
		when(mockNamedCollection.getMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS)).thenReturn(null);
	}

	private void setupMockStoreMetadata() {
		final String key1 = "kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_optout";
		final String value1 =
			"{\"key\":\"kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_optout\",\"value\":\"\",\"maxAge\":7200}";
		final String key2 = "kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_identity";
		final String value2 =
			"{\"key\":\"kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_identity\",\"value\":\"Cg8KBnN5bmNlZBIBMRiA9SQKMwoERUNJRBImNjA5MjY2MDcwMDIxMDI0NzMyNDYwNDgzMTg3MjA0MDkxMDQ3NjQYgIGjEBCFwbjv_i0=\",\"maxAge\":34128000}";
		HashMap<String, String> payloads = new HashMap<>();
		payloads.put(key1, value1);
		payloads.put(key2, value2);

		when(mockNamedCollection.getMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS)).thenReturn(payloads);
	}

	private void setupMockStoreMetadataEmpty() {
		HashMap<String, String> payloads = new HashMap<>();

		when(mockNamedCollection.getMap(EdgeConstants.DataStoreKeys.STORE_PAYLOADS)).thenReturn(payloads);
	}

	/**
	 * Deserialize {@code JsonNode} and flatten to provided {@code map}.
	 * For example, a JSON such as "{xdm: {stitchId: myID, eventType: myType}}" is flattened
	 * to two map elements "xdm.stitchId" = "myID" and "xdm.eventType" = "myType".
	 *
	 * Method is called recursively. To use, call with an empty path such as
	 * {@code addKeys("", new ObjectMapper().readTree(JsonNodeAsString), map);}
	 *
	 * @param currentPath the path in {@code JsonNode} to process
	 * @param jsonNode {@link JsonNode} to deserialize
	 * @param map {@code Map<String, String>} instance to store flattened JSON result
	 *
	 * @see <a href="https://stackoverflow.com/a/24150263">Stack Overflow post</a>
	 */
	private void addKeys(String currentPath, JsonNode jsonNode, Map<String, String> map) {
		if (jsonNode.isObject()) {
			ObjectNode objectNode = (ObjectNode) jsonNode;
			Iterator<Map.Entry<String, JsonNode>> iter = objectNode.fields();
			String pathPrefix = currentPath.isEmpty() ? "" : currentPath + ".";

			while (iter.hasNext()) {
				Map.Entry<String, JsonNode> entry = iter.next();
				addKeys(pathPrefix + entry.getKey(), entry.getValue(), map);
			}
		} else if (jsonNode.isArray()) {
			ArrayNode arrayNode = (ArrayNode) jsonNode;

			for (int i = 0; i < arrayNode.size(); i++) {
				addKeys(currentPath + "[" + i + "]", arrayNode.get(i), map);
			}
		} else if (jsonNode.isValueNode()) {
			ValueNode valueNode = (ValueNode) jsonNode;
			map.put(currentPath, valueNode.asText());
		}
	}
}
