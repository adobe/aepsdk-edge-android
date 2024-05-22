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

import static com.adobe.marketing.mobile.util.JSONAsserts.assertExactMatch;
import static com.adobe.marketing.mobile.util.NodeConfig.Scope.Subtree;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNotSame;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.fail;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.edge.Datastream;
import com.adobe.marketing.mobile.edge.SDKConfig;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.CollectionEqualCount;
import com.adobe.marketing.mobile.util.JSONAsserts;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.KeyMustBeAbsent;
import com.adobe.marketing.mobile.util.TimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"data\": {" +
			"        \"key\": \"value\"" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"" +
			events.get(0).getUniqueIdentifier() +
			"\"," +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(0).getTimestamp()) +
			"\"" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_setsEventTimestampAndEventId_whenEventsListIsValid() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"xdm\": {" +
			"        \"_id\": \"" +
			events.get(0).getUniqueIdentifier() +
			"\"," +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(0).getTimestamp()) +
			"\"" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotOverwriteTimestamp_whenValidTimestampPresent() throws Exception {
		String testTimestamp = "2021-06-03T00:00:20Z";
		List<Event> events = getSingleEvent(getExperienceEventData("value", null, testTimestamp));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		assertNotSame(testTimestamp, formatTimestamp(events.get(0).getTimestamp()));
		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"xdm\": {" +
			"        \"timestamp\": \"" +
			testTimestamp +
			"\"" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotOverwriteTimestamp_whenInvalidTimestampPresent()
		throws Exception {
		String testTimestamp = "invalidTimestamp";
		List<Event> events = getSingleEvent(getExperienceEventData("value", null, testTimestamp));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		assertNotSame(testTimestamp, formatTimestamp(events.get(0).getTimestamp()));
		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"xdm\": {" +
			"        \"timestamp\": \"" +
			testTimestamp +
			"\"" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_setsEventTimestamp_whenProvidedTimestampIsEmpty() throws Exception {
		String testTimestamp = "";
		List<Event> events = getSingleEvent(getExperienceEventData("value", null, testTimestamp));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"xdm\": {" +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(0).getTimestamp()) +
			"\"" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_setsCollectMeta_whenEventContainsDatasetId() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", "5dd603781b95cc18a83d42ce"));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"meta\": {" +
			"        \"collect\": {" +
			"          \"datasetId\": \"5dd603781b95cc18a83d42ce\"" +
			"        }" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		// Verify internal key is removed
		assertExactMatch(expected, payload, new KeyMustBeAbsent("events[0].datasetId"));
	}

	@Test
	public void getPayloadWithExperienceEvents_setsCollectMeta_whenEventContainsDatasetIdWithWhitespace()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", "   5dd603781b95cc18a83d42ce   "));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"meta\": {" +
			"        \"collect\": {" +
			"          \"datasetId\": \"5dd603781b95cc18a83d42ce\"" +
			"        }" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		// Verify internal key is removed
		assertExactMatch(expected, payload, new KeyMustBeAbsent("events[0].datasetId"));
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotSetCollectMeta_whenEventDoesNotContainDatasetId()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		// Verify internal key is removed
		assertExactMatch("{}", payload, new KeyMustBeAbsent("events[0].datasetId", "events[0].meta"));
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

		assertExactMatch("{}", payload, new KeyMustBeAbsent("events[*].datasetId", "events[*].meta"));
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

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"meta\": {" +
			"        \"collect\": {}" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"" +
			events.get(0).getUniqueIdentifier() +
			"\"," +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(0).getTimestamp()) +
			"\"" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(
			expected,
			payload,
			new KeyMustBeAbsent("events[0].datasetId"),
			new CollectionEqualCount(Subtree, "events[0].meta")
		);
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

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"data\": {" +
			"        \"key\": \"one\"" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"" +
			events.get(0).getUniqueIdentifier() +
			"\"," +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(0).getTimestamp()) +
			"\"" +
			"      }" +
			"    }," +
			"    {" +
			"      \"data\": {" +
			"        \"key\": \"two\"" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"" +
			events.get(1).getUniqueIdentifier() +
			"\"," +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(1).getTimestamp()) +
			"\"" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(expected, payload, new KeyMustBeAbsent("events[*].meta"));
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

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"data\": {" +
			"        \"key\": \"one\"" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"" +
			events.get(0).getUniqueIdentifier() +
			"\"," +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(0).getTimestamp()) +
			"\"" +
			"      }," +
			"      \"meta\": {" +
			"        \"collect\": {" +
			"          \"datasetId\": \"abc\"" +
			"        }" +
			"      }" +
			"    }," +
			"    {" +
			"      \"data\": {" +
			"        \"key\": \"two\"" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"" +
			events.get(1).getUniqueIdentifier() +
			"\"," +
			"        \"timestamp\": \"" +
			formatTimestamp(events.get(1).getTimestamp()) +
			"\"" +
			"      }," +
			"      \"meta\": {" +
			"        \"collect\": {" +
			"          \"datasetId\": \"123\"" +
			"        }" +
			"      }" +
			"    }" +
			"  ]" +
			"}";
		assertExactMatch(expected, payload, new KeyMustBeAbsent("events[*].datasetId"));
	}

	@Test
	public void getPayloadWithExperienceEvents_addsKonductorConfigWithStreaming_whenStreamingEnabled()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		requestBuilder.enableResponseStreaming("\u0000", "\n");
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": true," +
			"        \"recordSeparator\": \"\\u0000\"," +
			"        \"lineFeed\": \"\\n\"" +
			"      }" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_setsSdkConfigMeta_whenSdkConfigPresent() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", null));
		requestBuilder.addSdkConfig(new SDKConfig(new Datastream("OriginalDatastreamId")));
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"meta\": {" +
			"    \"sdkConfig\": {" +
			"      \"datastream\": {" +
			"        \"original\": \"OriginalDatastreamId\"" +
			"      }" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_setsConfigOverridesMeta_whenConfigOverridesPresent() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", "5dd603781b95cc18a83d42ce"));
		requestBuilder.addConfigOverrides(
			new HashMap() {
				{
					put("key", "val");
				}
			}
		);
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" + "  \"meta\": {" + "    \"configOverrides\": {" + "      \"key\": \"val\"" + "    }" + "  }" + "}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotSetConfigOverridesMeta_whenConfigOverridesEmpty()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", "5dd603781b95cc18a83d42ce"));
		requestBuilder.addConfigOverrides(new HashMap() {});
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		assertExactMatch("{}", payload, new KeyMustBeAbsent("meta.configOverrides"));
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotSetConfigOverridesMeta_whenConfigOverridesNull()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value", "5dd603781b95cc18a83d42ce"));
		requestBuilder.addConfigOverrides(null);
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		assertExactMatch("{}", payload, new KeyMustBeAbsent("meta.configOverrides"));
	}

	@Test
	public void getPayloadWithExperienceEvents_addsIdentityMap_whenEcidGiven() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		final String jsonStr =
			"{" +
			"      \"identityMap\": {" +
			"        \"ECID\": [" +
			"          {" +
			"            \"id\": \"myECID\"," +
			"            \"authenticatedState\": \"ambiguous\"," +
			"            \"primary\": false" +
			"          }" +
			"        ]" +
			"      }" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityState = JSONUtils.toMap(jsonObject);
		requestBuilder.addXdmPayload(identityState);
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);

		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"xdm\": {" +
			"    \"identityMap\": {" +
			"      \"ECID\": [" +
			"        {" +
			"          \"id\": \"myECID\"" +
			"        }" +
			"      ]" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_addsStoreMetadataWithPayloads_whenPayloadsInDataStore()
		throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		setupMockStoreMetadata();
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);
		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		String expected =
			"{" +
			"  \"meta\": {" +
			"    \"state\": {" +
			"      \"entries\": [" +
			"        {" +
			"          \"key\": \"kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_optout\"," +
			"          \"value\": \"\"," +
			"          \"maxAge\": 7200" +
			"        }," +
			"        {" +
			"          \"key\": \"kndctr_3E2A28175B8ED3720A495E23_AdobeOrg_identity\"," +
			"          \"value\": \"Cg8KBnN5bmNlZBIBMRiA9SQKMwoERUNJRBImNjA5MjY2MDcwMDIxMDI0NzMyNDYwNDgzMTg3MjA0MDkxMDQ3NjQYgIGjEBCFwbjv_i0=\"," +
			"          \"maxAge\": 34128000" +
			"        }" +
			"      ]" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(expected, payload);
	}

	@Test
	public void getPayloadWithExperienceEvents_NoAddStoreMetadata_whenNullPayloadInDataStore() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		setupMockStoreMetadataNullDatastoreKey();
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);
		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		assertExactMatch("{}", payload, new KeyMustBeAbsent("meta.state"));
	}

	@Test
	public void getPayloadWithExperienceEvents_doesNotAddStoreMetadata_whenDatastoreIsEmpty() throws Exception {
		List<Event> events = getSingleEvent(getExperienceEventData("value"));
		setupMockStoreMetadataEmpty();
		JSONObject payload = requestBuilder.getPayloadWithExperienceEvents(events);
		assertNotNull(payload);
		assertNumberOfEvents(payload, 1);

		// StateMetadata not added if empty
		assertExactMatch("{}", payload, new KeyMustBeAbsent("meta.state"));
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

		String expected =
			"{" +
			"  \"consent\": [" +
			"    {" +
			"      \"standard\": \"Adobe\"," +
			"      \"value\": {" +
			"        \"collect\": {" +
			"          \"val\": \"y\"" +
			"        }" +
			"      }," +
			"      \"version\": \"2.0\"" +
			"    }" +
			"  ]," +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": false" +
			"      }" +
			"    }" +
			"  }," +
			"  \"query\": {" +
			"    \"consent\": {" +
			"      \"operation\": \"update\"" +
			"    }" +
			"  }" +
			"}";
		JSONAsserts.assertEquals(expected, payload);
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

		// StateMetadata not added to consent requests
		assertExactMatch("{}", payload, new KeyMustBeAbsent("meta.state"));
	}

	@Test
	public void getConsentPayload_addsIdentityMap() throws Exception {
		final String jsonStr =
			"{" +
			"      \"identityMap\": {" +
			"        \"ECID\": [" +
			"          {" +
			"            \"id\": 1234," +
			"            \"authenticatedState\": \"ambiguous\"," +
			"            \"primary\": false" +
			"          }" +
			"        ]" +
			"      }" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityState = JSONUtils.toMap(jsonObject);
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
		final Map<String, Object> identityState = JSONUtils.toMap(jsonObject);
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

		String expected =
			"{" +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": true," +
			"        \"recordSeparator\": \"\\u0000\"," +
			"        \"lineFeed\": \"\\n\"" +
			"      }" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(expected, payload);
	}

	// assert on standard fields included in the payload of all consent requests
	private void assertStandardFieldsInConsentUpdatesPayload(final JSONObject payload) throws Exception {
		String expected =
			"{" +
			"  \"consent\": [" +
			"    {" +
			"      \"standard\": \"Adobe\"," +
			"      \"version\": \"2.0\"" +
			"    }" +
			"  ]," +
			"  \"query\": {" +
			"    \"consent\": {" +
			"      \"operation\": \"update\"" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(
			expected,
			payload,
			new CollectionEqualCount("consent"), // Validates that `consent` array only has 1 element
			new CollectionEqualCount(Subtree, "query") // Validates that `query` onwards has same number of elements
		);
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
		return TimeUtils.getISO8601UTCDateWithMilliseconds(new Date(timestamp));
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
}
