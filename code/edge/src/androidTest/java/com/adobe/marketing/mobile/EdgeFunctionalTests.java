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

import static com.adobe.marketing.mobile.services.HttpMethod.POST;
import static com.adobe.marketing.mobile.util.FunctionalTestHelper.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.FunctionalTestConstants;
import com.adobe.marketing.mobile.util.FunctionalTestUtils;
import com.adobe.marketing.mobile.util.TestXDMSchema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EdgeFunctionalTests {

	private static final String EXEDGE_INTERACT_URL_STRING =
		FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String EXEDGE_INTERACT_OR2_LOC_URL_STRING =
		FunctionalTestConstants.Defaults.EXEDGE_INTERACT_OR2_LOC_URL_STRING;
	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";
	private static final String DEFAULT_RESPONSE_STRING = "\u0000{\"test\": \"json\"}";
	private static final int TIMEOUT_MILLIS = 5000;
	private static final ExperienceEvent XDM_EXPERIENCE_EVENT = new ExperienceEvent.Builder()
		.setXdmSchema(
			new HashMap<String, Object>() {
				{
					put("key", "value");
				}
			}
		)
		.setData(null)
		.build();

	@Rule
	public RuleChain rule = RuleChain
		.outerRule(new LogOnErrorRule())
		.around(new SetupCoreRule())
		.around(new RegisterMonitorExtensionRule());

	@Before
	public void setup() throws Exception {
		setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);
		setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, 4);

		HashMap<String, Object> config = new HashMap<String, Object>() {
			{
				put("edge.configId", CONFIG_ID);
			}
		};
		MobileCore.updateConfiguration(config);

		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.registerExtensions(Arrays.asList(Edge.EXTENSION, Identity.EXTENSION), o -> latch.countDown());
		latch.await();

		assertExpectedEvents(false);
		resetTestExpectations();
	}

	// --------------------------------------------------------------------------------------------
	// test request event format
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_withXDMData_sendsCorrectRequestEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdm");
						put("testInt", 10);
						put("testBool", false);
						put("testDouble", 12.89);
						put(
							"testArray",
							new ArrayList<String>() {
								{
									add("elem1");
									add("elem2");
								}
							}
						);
						put(
							"testMap",
							new HashMap<String, String>() {
								{
									put("key", "value");
								}
							}
						);
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertExpectedEvents(false);
		List<Event> resultEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, resultEvents.size());
		Map<String, Object> eventData = resultEvents.get(0).getEventData();
		assertNotNull(eventData);

		Map<String, String> flattenedData = FunctionalTestUtils.flattenMap(eventData);
		assertEquals(7, flattenedData.size());
		assertEquals("xdm", flattenedData.get("xdm.testString"));
		assertEquals("10", flattenedData.get("xdm.testInt"));
		assertEquals("false", flattenedData.get("xdm.testBool"));
		assertEquals("12.89", flattenedData.get("xdm.testDouble"));
		assertEquals("elem1", flattenedData.get("xdm.testArray[0]"));
		assertEquals("elem2", flattenedData.get("xdm.testArray[1]"));
		assertEquals("value", flattenedData.get("xdm.testMap.key"));
	}

	@Test
	public void testSendEvent_withXDMDataAndCustomData_sendsCorrectRequestEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdm");
					}
				}
			)
			.setData(
				new HashMap<String, Object>() {
					{
						put("testString", "stringValue");
						put("testInt", 101);
						put("testBool", true);
						put("testDouble", 13.66);
						put(
							"testArray",
							new ArrayList<String>() {
								{
									add("elem1");
									add("elem2");
								}
							}
						);
						put(
							"testMap",
							new HashMap<String, String>() {
								{
									put("key", "value");
								}
							}
						);
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertExpectedEvents(false);
		List<Event> resultEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, resultEvents.size());
		Map<String, Object> eventData = resultEvents.get(0).getEventData();
		assertNotNull(eventData);

		Map<String, String> flattenedData = FunctionalTestUtils.flattenMap(eventData);
		assertEquals(8, flattenedData.size());
		assertEquals("xdm", flattenedData.get("xdm.testString"));
		assertEquals("stringValue", flattenedData.get("data.testString"));
		assertEquals("101", flattenedData.get("data.testInt"));
		assertEquals("true", flattenedData.get("data.testBool"));
		assertEquals("13.66", flattenedData.get("data.testDouble"));
		assertEquals("elem1", flattenedData.get("data.testArray[0]"));
		assertEquals("elem2", flattenedData.get("data.testArray[1]"));
		assertEquals("value", flattenedData.get("data.testMap.key"));
	}

	@Test
	public void testSendEvent_withXDMDataAndNullData_sendsCorrectRequestEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdm");
					}
				}
			)
			.setData(null)
			.build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertExpectedEvents(false);
		List<Event> resultEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, resultEvents.size());
		Map<String, Object> eventData = resultEvents.get(0).getEventData();
		assertNotNull(eventData);

		Map<String, String> flattenedData = FunctionalTestUtils.flattenMap(eventData);
		assertEquals(1, flattenedData.size());
		assertEquals("xdm", flattenedData.get("xdm.testString"));
	}

	@Test
	public void testSendEvent_withEmptyXDMDataAndNullData_DoesNotSendRequestEvent() throws InterruptedException {
		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(new HashMap<>())
			.setData(null)
			.build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertUnexpectedEvents();
	}

	@Test
	public void testSendEvent_withEmptyXDMSchema_DoesNotSendRequestEvent() throws InterruptedException {
		ExperienceEvent experienceEvent = new ExperienceEvent.Builder().setXdmSchema(new TestXDMSchema()).build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertUnexpectedEvents();
	}

	// --------------------------------------------------------------------------------------------
	// test network request format
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_withXDMData_sendsExEdgeNetworkRequest() throws Exception {
		HttpConnecting responseConnection = createNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdmValue");
						put("testInt", 10);
						put("testBool", false);
						put("testDouble", 12.89);
						put(
							"testArray",
							new ArrayList<String>() {
								{
									add("elem1");
									add("elem2");
								}
							}
						);
						put(
							"testMap",
							new HashMap<String, String>() {
								{
									put("key", "value");
								}
							}
						);
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		Map<String, String> resultPayload = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals(18, resultPayload.size());
		assertEquals("true", resultPayload.get("meta.konductorConfig.streaming.enabled"));
		assertEquals("\u0000", resultPayload.get("meta.konductorConfig.streaming.recordSeparator"));
		assertEquals("\n", resultPayload.get("meta.konductorConfig.streaming.lineFeed"));
		assertNotNull(resultPayload.get("xdm.identityMap.ECID[0].id"));
		assertEquals("false", resultPayload.get("xdm.identityMap.ECID[0].primary"));
		assertEquals("ambiguous", resultPayload.get("xdm.identityMap.ECID[0].authenticatedState"));
		assertNotNull(resultPayload.get("events[0].xdm._id"));
		assertNotNull(resultPayload.get("events[0].xdm.timestamp"));
		assertEquals("xdmValue", resultPayload.get("events[0].xdm.testString"));
		assertEquals("10", resultPayload.get("events[0].xdm.testInt"));
		assertEquals("false", resultPayload.get("events[0].xdm.testBool"));
		assertEquals("12.89", resultPayload.get("events[0].xdm.testDouble"));
		assertEquals("elem1", resultPayload.get("events[0].xdm.testArray[0]"));
		assertEquals("elem2", resultPayload.get("events[0].xdm.testArray[1]"));
		assertEquals("value", resultPayload.get("events[0].xdm.testMap.key"));

		assertTrue(resultRequests.get(0).getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));

		assertEquals("app", resultPayload.get("xdm.implementationDetails.environment"));
		assertEquals(
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE,
			resultPayload.get("xdm.implementationDetails.name")
		);
		assertEquals(
			MobileCore.extensionVersion() + "+" + Edge.extensionVersion(),
			resultPayload.get("xdm.implementationDetails.version")
		);
	}

	@Test
	public void testSendEvent_withXDMDataAndCustomData_sendsExEdgeNetworkRequest() throws Exception {
		HttpConnecting responseConnection = createNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdmValue");
					}
				}
			)
			.setData(
				new HashMap<String, Object>() {
					{
						put("testString", "stringValue");
						put("testInt", 101);
						put("testBool", true);
						put("testDouble", 13.66);
						put(
							"testArray",
							new ArrayList<String>() {
								{
									add("elem1");
									add("elem2");
								}
							}
						);
						put(
							"testMap",
							new HashMap<String, String>() {
								{
									put("key", "value");
								}
							}
						);
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		Map<String, String> resultPayload = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals(19, resultPayload.size());
		assertEquals("true", resultPayload.get("meta.konductorConfig.streaming.enabled"));
		assertEquals("\u0000", resultPayload.get("meta.konductorConfig.streaming.recordSeparator"));
		assertEquals("\n", resultPayload.get("meta.konductorConfig.streaming.lineFeed"));
		assertNotNull(resultPayload.get("xdm.identityMap.ECID[0].id"));
		assertEquals("false", resultPayload.get("xdm.identityMap.ECID[0].primary"));
		assertEquals("ambiguous", resultPayload.get("xdm.identityMap.ECID[0].authenticatedState"));
		assertNotNull(resultPayload.get("events[0].xdm._id"));
		assertNotNull(resultPayload.get("events[0].xdm.timestamp"));
		assertEquals("xdmValue", resultPayload.get("events[0].xdm.testString"));
		assertEquals("stringValue", resultPayload.get("events[0].data.testString"));
		assertEquals("101", resultPayload.get("events[0].data.testInt"));
		assertEquals("true", resultPayload.get("events[0].data.testBool"));
		assertEquals("13.66", resultPayload.get("events[0].data.testDouble"));
		assertEquals("elem1", resultPayload.get("events[0].data.testArray[0]"));
		assertEquals("elem2", resultPayload.get("events[0].data.testArray[1]"));
		assertEquals("value", resultPayload.get("events[0].data.testMap.key"));
		assertEquals("app", resultPayload.get("xdm.implementationDetails.environment"));
		assertEquals(
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE,
			resultPayload.get("xdm.implementationDetails.name")
		);
		assertEquals(
			MobileCore.extensionVersion() + "+" + Edge.extensionVersion(),
			resultPayload.get("xdm.implementationDetails.version")
		);

		assertTrue(resultRequests.get(0).getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));
	}

	@Test
	public void testSendEvent_withXDMSchema_sendsExEdgeNetworkRequest() throws Exception {
		HttpConnecting responseConnection = createNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		TestXDMSchema.TestXDMObject xdmObject = new TestXDMSchema.TestXDMObject();
		xdmObject.innerKey = "testInnerObject";
		TestXDMSchema xdmSchema = new TestXDMSchema();
		xdmSchema.boolObject = true;
		xdmSchema.intObject = 100;
		xdmSchema.stringObject = "testWithXdmSchema";
		xdmSchema.doubleObject = 3.42;
		xdmSchema.xdmObject = xdmObject;

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder().setXdmSchema(xdmSchema).build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		Map<String, String> resultPayload = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals(17, resultPayload.size());

		assertEquals("true", resultPayload.get("meta.konductorConfig.streaming.enabled"));
		assertEquals("\u0000", resultPayload.get("meta.konductorConfig.streaming.recordSeparator"));
		assertEquals("\n", resultPayload.get("meta.konductorConfig.streaming.lineFeed"));
		assertNotNull(resultPayload.get("xdm.identityMap.ECID[0].id"));
		assertEquals("false", resultPayload.get("xdm.identityMap.ECID[0].primary"));
		assertEquals("ambiguous", resultPayload.get("xdm.identityMap.ECID[0].authenticatedState"));
		assertNotNull(resultPayload.get("events[0].xdm._id"));
		assertNotNull(resultPayload.get("events[0].xdm.timestamp"));

		assertEquals("testWithXdmSchema", resultPayload.get("events[0].xdm.stringObject"));
		assertEquals("100", resultPayload.get("events[0].xdm.intObject"));
		assertEquals("true", resultPayload.get("events[0].xdm.boolObject"));
		assertEquals("3.42", resultPayload.get("events[0].xdm.doubleObject"));
		assertEquals("testInnerObject", resultPayload.get("events[0].xdm.xdmObject.innerKey"));
		assertEquals("abc123def", resultPayload.get("events[0].meta.collect.datasetId"));

		assertTrue(resultRequests.get(0).getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));

		assertEquals("app", resultPayload.get("xdm.implementationDetails.environment"));
		assertEquals(
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE,
			resultPayload.get("xdm.implementationDetails.name")
		);
		assertEquals(
			MobileCore.extensionVersion() + "+" + Edge.extensionVersion(),
			resultPayload.get("xdm.implementationDetails.version")
		);
	}

	@Test
	public void testSendEvent_withEmptyXDMSchema_doesNotSendExEdgeNetworkRequest() throws InterruptedException {
		HttpConnecting responseConnection = createNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder().setXdmSchema(new TestXDMSchema()).build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testSendEvent_withEmptyXDMSchemaAndEmptyData_doesNotSendExEdgeNetworkRequest()
		throws InterruptedException {
		HttpConnecting responseConnection = createNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(new TestXDMSchema())
			.setData(new HashMap<>())
			.build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testSendEvent_withEmptyXDMSchemaAndNullData_doesNotSendExEdgeNetworkRequest()
		throws InterruptedException {
		HttpConnecting responseConnection = createNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(new TestXDMSchema())
			.setData(null)
			.build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST);
		assertEquals(0, resultRequests.size());
	}

	// --------------------------------------------------------------------------------------------
	// Configurable Endpoint
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_withConfigurableEndpoint_withEmptyConfig_usesProductionEndpoint() throws Exception {
		setExpectationNetworkRequest(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		assertTrue(
			resultRequests.get(0).getUrl().startsWith(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING)
		);
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));
	}

	@Test
	public void testSendEvent_withConfigurableEndpoint_withInvalidConfig_usesProductionEndpoint() throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "invalid");
				}
			}
		);

		setExpectationNetworkRequest(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		assertTrue(
			resultRequests.get(0).getUrl().startsWith(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING)
		);
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));
	}

	@Test
	public void testSendEvent_withConfigurableEndpoint_withProdConfigEndpoint_usesProductionEndpoint()
		throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "prod");
				}
			}
		);

		setExpectationNetworkRequest(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		assertTrue(
			resultRequests.get(0).getUrl().startsWith(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_URL_STRING)
		);
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));
	}

	@Test
	public void testSendEvent_withConfigurableEndpoint_withPreProdConfigEndpoint_usesPreProductionEndpoint()
		throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "pre-prod");
				}
			}
		);

		setExpectationNetworkRequest(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_PRE_PROD_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			FunctionalTestConstants.Defaults.EXEDGE_INTERACT_PRE_PROD_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		assertTrue(
			resultRequests
				.get(0)
				.getUrl()
				.startsWith(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_PRE_PROD_URL_STRING)
		);
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));
	}

	@Test
	public void testSendEvent_withConfigurableEndpoint_withIntConfigEndpoint_usesIntegrationEndpoint()
		throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "int");
				}
			}
		);

		setExpectationNetworkRequest(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_INT_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		assertNetworkRequestCount();
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			FunctionalTestConstants.Defaults.EXEDGE_INTERACT_INT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		assertTrue(
			resultRequests.get(0).getUrl().startsWith(FunctionalTestConstants.Defaults.EXEDGE_INTERACT_INT_URL_STRING)
		);
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));
	}

	// --------------------------------------------------------------------------------------------
	// Client-side store
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_twoConsecutiveCalls_appendsReceivedClientSideStore() throws InterruptedException {
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		final String storeResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"key\": \"kndctr_testOrg_AdobeOrg_identity\",\"value\": \"hashed_value\",\"maxAge\": 34128000},{\"key\": \"kndctr_testOrg_AdobeOrg_consent_check\",\"value\": \"1\",\"maxAge\": 7200},{\"key\": \"expired_key\",\"value\": \"1\",\"maxAge\": 0}],\"type\": \"state:store\"}]}\n";
		HttpConnecting responseConnection = createNetworkResponse(storeResponseBody, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no stored data
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		Map<String, String> requestBody = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals("Expected request body with 12 elements, but found: " + requestBody, 12, requestBody.size());

		assertExpectedEvents(true);
		resetTestExpectations();

		// send a new event, should contain previously stored store data
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		resultRequests = getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());
		requestBody = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals("Expected request body with 18 elements, but found: " + requestBody, 18, requestBody.size());

		String firstStore = requestBody.get("meta.state.entries[0].key");
		assertNotNull("client-side store not found", firstStore);
		int identityIndex = firstStore.equals("kndctr_testOrg_AdobeOrg_identity") ? 0 : 1;
		int consentIndex = firstStore.equals("kndctr_testOrg_AdobeOrg_identity") ? 1 : 0;

		assertEquals(
			"kndctr_testOrg_AdobeOrg_identity",
			requestBody.get("meta.state.entries[" + identityIndex + "].key")
		);
		assertEquals("hashed_value", requestBody.get("meta.state.entries[" + identityIndex + "].value"));
		assertEquals("34128000", requestBody.get("meta.state.entries[" + identityIndex + "].maxAge"));

		assertEquals(
			"kndctr_testOrg_AdobeOrg_consent_check",
			requestBody.get("meta.state.entries[" + consentIndex + "].key")
		);
		assertEquals("1", requestBody.get("meta.state.entries[" + consentIndex + "].value"));
		assertEquals("7200", requestBody.get("meta.state.entries[" + consentIndex + "].maxAge"));

		assertTrue(resultRequests.get(0).getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));

		assertExpectedEvents(true);
	}

	@Test
	public void testSendEvent_twoConsecutiveCalls_resetBefore_appendsReceivedClientSideStore()
		throws InterruptedException {
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		// send the reset event before
		final Event resetEvent = new Event.Builder("resetEvent", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE)
			.build();
		MobileCore.dispatchEvent(resetEvent);

		final String storeResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"key\": \"kndctr_testOrg_AdobeOrg_identity\",\"value\": \"hashed_value\",\"maxAge\": 34128000},{\"key\": \"kndctr_testOrg_AdobeOrg_consent_check\",\"value\": \"1\",\"maxAge\": 7200},{\"key\": \"expired_key\",\"value\": \"1\",\"maxAge\": 0}],\"type\": \"state:store\"}]}\n";
		HttpConnecting responseConnection = createNetworkResponse(storeResponseBody, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no stored data
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		Map<String, String> requestBody = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals("Expected request body with 12 elements, but found: " + requestBody, 12, requestBody.size());

		assertExpectedEvents(true);
		resetTestExpectations();

		// send a new event, should contain previously stored store data
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		resultRequests = getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());
		requestBody = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals("Expected request body with 18 elements, but found: " + requestBody, 18, requestBody.size());

		String firstStore = requestBody.get("meta.state.entries[0].key");
		assertNotNull("client-side store not found", firstStore);
		int identityIndex = firstStore.equals("kndctr_testOrg_AdobeOrg_identity") ? 0 : 1;
		int consentIndex = firstStore.equals("kndctr_testOrg_AdobeOrg_identity") ? 1 : 0;

		assertEquals(
			"kndctr_testOrg_AdobeOrg_identity",
			requestBody.get("meta.state.entries[" + identityIndex + "].key")
		);
		assertEquals("hashed_value", requestBody.get("meta.state.entries[" + identityIndex + "].value"));
		assertEquals("34128000", requestBody.get("meta.state.entries[" + identityIndex + "].maxAge"));

		assertEquals(
			"kndctr_testOrg_AdobeOrg_consent_check",
			requestBody.get("meta.state.entries[" + consentIndex + "].key")
		);
		assertEquals("1", requestBody.get("meta.state.entries[" + consentIndex + "].value"));
		assertEquals("7200", requestBody.get("meta.state.entries[" + consentIndex + "].maxAge"));

		assertTrue(resultRequests.get(0).getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, resultRequests.get(0).queryParam("configId"));
		assertNotNull(resultRequests.get(0).queryParam("requestId"));

		assertExpectedEvents(true);
	}

	@Test
	public void testSendEvent_twoConsecutiveCalls_resetBetween_clearsClientSideStore() throws InterruptedException {
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		final String storeResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"key\": \"kndctr_testOrg_AdobeOrg_identity\",\"value\": \"hashed_value\",\"maxAge\": 34128000},{\"key\": \"kndctr_testOrg_AdobeOrg_consent_check\",\"value\": \"1\",\"maxAge\": 7200},{\"key\": \"expired_key\",\"value\": \"1\",\"maxAge\": 0}],\"type\": \"state:store\"}]}\n";
		HttpConnecting responseConnection = createNetworkResponse(storeResponseBody, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no stored data
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		Map<String, String> requestBody = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals("Expected request body with 12 elements, but found: " + requestBody, 12, requestBody.size());

		assertExpectedEvents(true);
		resetTestExpectations();

		// send the reset event in-between
		final Event resetEvent = new Event.Builder("resetEvent", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE)
			.build();
		MobileCore.dispatchEvent(resetEvent);

		// send a new event, should contain previously stored store data
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		resultRequests = getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());
		requestBody = getFlattenedNetworkRequestBody(resultRequests.get(0));
		assertEquals("Expected request body with 12 elements, but found: " + requestBody, 12, requestBody.size());

		String firstStore = requestBody.get("meta.state.entries[0].key");
		assertNull("client-side store was found", firstStore);
	}

	// --------------------------------------------------------------------------------------------
	// Paired request-response events
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_receivesResponseEventHandle_sendsResponseEvent_pairedWithTheRequestEventId()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, "personalization:decisions", 1);

		final String responseBody =
			"\u0000{\"requestId\": \"0ee43289-4a4e-469a-bf5c-1d8186919a26\",\"handle\": [{\"payload\": [{\"id\": \"AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9\",\"scope\": \"buttonColor\",\"items\": [{                           \"schema\": \"https://ns.adobe.com/personalization/json-content-item\",\"data\": {\"content\": {\"value\": \"#D41DBA\"}}}]}],\"type\": \"personalization:decisions\",\"eventIndex\": 0}]}\n";
		HttpConnecting responseConnection = createNetworkResponse(responseBody, 200);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		assertNetworkRequestCount();
		assertExpectedEvents(true);

		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		String requestId = resultRequests.get(0).queryParam("requestId");

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());
		String requestEventUuid = requestEvents.get(0).getUniqueIdentifier();

		List<Event> responseEvents = getDispatchedEventsWith(EventType.EDGE, "personalization:decisions");
		assertEquals(1, responseEvents.size());
		Map<String, Object> responseEventData = responseEvents.get(0).getEventData();
		assertNotNull(responseEventData);

		Map<String, String> flattenedEventData = FunctionalTestUtils.flattenMap(responseEventData);
		assertEquals(7, flattenedEventData.size());
		assertEquals("personalization:decisions", flattenedEventData.get("type"));
		assertEquals(
			"AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9",
			flattenedEventData.get("payload[0].id")
		);
		assertEquals("#D41DBA", flattenedEventData.get("payload[0].items[0].data.content.value"));
		assertEquals(
			"https://ns.adobe.com/personalization/json-content-item",
			flattenedEventData.get("payload[0].items[0].schema")
		);
		assertEquals("buttonColor", flattenedEventData.get("payload[0].scope"));
		assertEquals(requestId, flattenedEventData.get("requestId"));
		assertEquals(requestEventUuid, flattenedEventData.get("requestEventId"));
		assertEquals(requestEventUuid, responseEvents.get(0).getParentID());
	}

	@Test
	public void testSendEvent_receivesResponseEventWarning_sendsErrorResponseEvent_pairedWithTheRequestEventId()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);

		final String responseBody =
			"\u0000{\"requestId\": \"0ee43289-4a4e-469a-bf5c-1d8186919a26\",\"handle\": [],\"warnings\": [{\"eventIndex\": 0,\"code\": \"personalization:0\",\"message\": \"Failed due to unrecoverable system error\"}]}\n";
		HttpConnecting responseConnection = createNetworkResponse(responseBody, 200);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "personalizationEvent");
						put("test", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(event, null);

		assertNetworkRequestCount();
		assertExpectedEvents(false);

		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		String requestId = resultRequests.get(0).queryParam("requestId");

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());
		String requestEventUuid = requestEvents.get(0).getUniqueIdentifier();

		List<Event> errorResponseEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, errorResponseEvents.size());
		Map<String, Object> responseEventData = errorResponseEvents.get(0).getEventData();
		assertNotNull(responseEventData);

		Map<String, String> flattenedEventData = FunctionalTestUtils.flattenMap(responseEventData);
		assertEquals(5, flattenedEventData.size());
		assertEquals("personalization:0", flattenedEventData.get("code"));
		assertEquals("Failed due to unrecoverable system error", flattenedEventData.get("message"));
		assertEquals("0", flattenedEventData.get("eventIndex"));
		assertEquals(requestId, flattenedEventData.get("requestId"));
		assertEquals(requestEventUuid, flattenedEventData.get("requestEventId"));
		assertEquals(requestEventUuid, errorResponseEvents.get(0).getParentID());
	}

	// --------------------------------------------------------------------------------------------
	// Location Hint result
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_edgeNetworkResponseContainsLocationHint_nextSendEventIncludesLocationHint()
		throws InterruptedException {
		final String hintResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"scope\": \"EdgeNetwork\",\"hint\": \"or2\",\"ttlSeconds\": 1800}],\"type\": \"locationHint:result\"}]}\n";

		HttpConnecting responseConnection = createNetworkResponse(hintResponseBody, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setExpectationNetworkRequest(EXEDGE_INTERACT_OR2_LOC_URL_STRING, POST, 1);

		setExpectationEvent(EventType.EDGE, "locationHint:result", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no location hint
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		// second network call, has location hint
		resultRequests = getNetworkRequestsWith(EXEDGE_INTERACT_OR2_LOC_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());

		// location hint handle dispatched
		assertExpectedEvents(true);
	}

	@Test
	public void testSendEvent_edgeNetworkResponseContainsLocationHint_sendEventDoesNotIncludeExpiredLocationHint()
		throws InterruptedException {
		final String hintResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"scope\": \"EdgeNetwork\",\"hint\": \"or2\",\"ttlSeconds\": 1}],\"type\": \"locationHint:result\"}]}\n";

		HttpConnecting responseConnection = createNetworkResponse(hintResponseBody, 200);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 2);

		setExpectationEvent(EventType.EDGE, "locationHint:result", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);
		Thread.sleep(1500); // wait for hint to expire
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// all network calls, no location hint
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(2, resultRequests.size());

		// location hint handle dispatched
		assertExpectedEvents(true);
	}

	// --------------------------------------------------------------------------------------------
	// test get and set location hint
	// --------------------------------------------------------------------------------------------

	@Test
	public void testGetLocationHint_whenValueHint_returnsValue() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] result = new String[1];

		Edge.setLocationHint("or2");
		Edge.getLocationHint(s -> {
			result[0] = s;
			latch.countDown();
		});

		latch.await(2000, TimeUnit.MILLISECONDS);
		assertEquals("or2", result[0]);
	}

	@Test
	public void testGetLocationHint_whenNullHint_returnsNull() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] result = new String[1];

		Edge.setLocationHint("or2");
		Edge.setLocationHint(null);
		Edge.getLocationHint(s -> {
			result[0] = s;
			latch.countDown();
		});

		latch.await(2000, TimeUnit.MILLISECONDS);
		assertNull(result[0]);
	}

	@Test
	public void testGetLocationHint_whenEmptyHint_returnsNull() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] result = new String[1];

		Edge.setLocationHint("or2");
		Edge.setLocationHint("");
		Edge.getLocationHint(s -> {
			result[0] = s;
			latch.countDown();
		});

		latch.await(2000, TimeUnit.MILLISECONDS);
		assertNull(result[0]);
	}

	@Test
	public void testSetLocationHint_withValueHint_createsSharedState() throws InterruptedException {
		Edge.setLocationHint("or2");
		sleep(500); // wait for state creation
		Map<String, Object> sharedState = getSharedStateFor(FunctionalTestConstants.SharedState.EDGE, 1000);
		assertNotNull(sharedState);
		assertEquals("or2", sharedState.get("locationHint"));
	}

	@Test
	public void testSetLocationHint_withValueHint_savesToDataStore() throws InterruptedException {
		final Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.add(Calendar.SECOND, 1800); // default hint TTL
		long expectedExpiryTime = calendar.getTimeInMillis();

		Edge.setLocationHint("or2");
		sleep(500); // wait for event to get processed

		NamedCollection dataStore = ServiceProvider
			.getInstance()
			.getDataStoreService()
			.getNamedCollection(EdgeConstants.EDGE_DATA_STORAGE);
		final String hint = dataStore.getString(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT, null);
		final long hintExpiry = dataStore.getLong(
			EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP,
			0
		);

		assertEquals("or2", hint);
		assertEquals(expectedExpiryTime, hintExpiry, 100);
	}

	@Test
	public void testSetLocationHint_withValueHint_edgeNetworkResponseContainsLocationHint()
		throws InterruptedException {
		setExpectationNetworkRequest(EXEDGE_INTERACT_OR2_LOC_URL_STRING, POST, 1);

		Edge.setLocationHint("or2"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			EXEDGE_INTERACT_OR2_LOC_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
	}

	@Test
	public void testSetLocationHint_withHintWithSpaces_edgeNetworkResponseContainsLocationHint()
		throws InterruptedException {
		setExpectationNetworkRequest("https://edge.adobedc.net/ee/incorrect location hint/v1/interact", POST, 1);

		Edge.setLocationHint("incorrect location hint"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			"https://edge.adobedc.net/ee/incorrect location hint/v1/interact",
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
	}

	@Test
	public void testSetLocationHint_withHintWithSpecialCharacters_edgeNetworkResponseContainsLocationHint()
		throws InterruptedException {
		setExpectationNetworkRequest("https://edge.adobedc.net/ee/{\"example\":\"incorrect\"}/v1/interact", POST, 1);

		Edge.setLocationHint("{\"example\":\"incorrect\"}"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			"https://edge.adobedc.net/ee/{\"example\":\"incorrect\"}/v1/interact",
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
	}

	@Test
	public void testSetLocationHint_withHintWithUnicodeCharacters_edgeNetworkResponseContainsLocationHint()
		throws InterruptedException {
		setExpectationNetworkRequest("https://edge.adobedc.net/ee/\u0048\u0065\u006C\u006C\u006F/v1/interact", POST, 1);

		Edge.setLocationHint("\u0048\u0065\u006C\u006C\u006F"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = getNetworkRequestsWith(
			"https://edge.adobedc.net/ee/\u0048\u0065\u006C\u006C\u006F/v1/interact",
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
	}

	@Test
	public void testSetLocationHint_andGetLocationHint_hintWithSpaces_doesNotCrash() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] result = new String[1];

		Edge.setLocationHint("incorrect location hint");
		Edge.getLocationHint(s -> {
			result[0] = s;
			latch.countDown();
		});

		latch.await(2000, TimeUnit.MILLISECONDS);
		assertEquals("incorrect location hint", result[0]);
	}

	@Test
	public void testSetLocationHint_andGetLocationHint_hintWithSpecialCharacters_doesNotCrash()
		throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] result = new String[1];
		final String expectedHint = "{\"example\":\"incorrect\"}";

		Edge.setLocationHint(expectedHint);
		Edge.getLocationHint(s -> {
			result[0] = s;
			latch.countDown();
		});

		latch.await(2000, TimeUnit.MILLISECONDS);
		assertEquals(expectedHint, result[0]);
	}

	@Test
	public void testSetLocationHint_andGetLocationHint_hintWithUnicodeCharacters_doesNotCrash()
		throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] result = new String[1];
		final String expectedHint = "\u0048\u0065\u006C\u006C\u006F World";

		Edge.setLocationHint(expectedHint);
		Edge.getLocationHint(s -> {
			result[0] = s;
			latch.countDown();
		});

		latch.await(2000, TimeUnit.MILLISECONDS);
		assertEquals(expectedHint, result[0]);
	}

	@Test
	public void testGetLocationHint_responseEventChainedToParentId() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);

		Edge.setLocationHint("or2");
		Edge.getLocationHint(s -> {
			latch.countDown();
		});

		latch.await(2000, TimeUnit.MILLISECONDS);

		List<Event> dispatchedRequests = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_IDENTITY);
		assertEquals(1, dispatchedRequests.size());
		List<Event> dispatchedResponses = getDispatchedEventsWith(EventType.EDGE, EventSource.RESPONSE_IDENTITY);
		assertEquals(1, dispatchedResponses.size());

		assertEquals(dispatchedRequests.get(0).getUniqueIdentifier(), dispatchedResponses.get(0).getParentID());
	}

	// --------------------------------------------------------------------------------------------
	// test persisted hits (recoverable errors)
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_withXDMData_sendsExEdgeNetworkRequest_afterRetry() throws InterruptedException {
		String edgeResponse =
			"\u0000{\"requestId\": \"test-req-id\",\"handle\": [],\"errors\": [],\"warnings\": [{\"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-502\",\"status\": 503,\"title\": \"A warning occurred.\",\"report\": {\"cause\": {\"message\": \"Unavailable\",\"code\": 503}}}]}";

		// bad connection, hits will be retried
		HttpConnecting responseConnection = createNetworkResponse(null, edgeResponse, 503, null, null);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("test", "xdm");
						put("testInt", 10);
						put("testBool", false);
						put("testDouble", 12.89);
						put(
							"testArray",
							new ArrayList<String>() {
								{
									add("elem1");
									add("elem2");
								}
							}
						);
						put(
							"testMap",
							new HashMap<String, String>() {
								{
									put("key", "value");
								}
							}
						);
					}
				}
			)
			.build();
		Edge.sendEvent(event, null);
		assertNetworkRequestCount();
		resetTestExpectations();

		// good connection, hits sent
		responseConnection = createNetworkResponse(edgeResponse, 200);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);

		assertNetworkRequestCount();
		assertExpectedEvents(true);
	}

	@Test
	public void testSendEvent_withXDMData_sendsExEdgeNetworkRequest_afterRetryMultipleHits()
		throws InterruptedException {
		String edgeResponse =
			"\u0000{" +
			"  \"requestId\": \"test-req-id\"," +
			"  \"handle\": [" +
			"  ]," +
			"  \"errors\": [" +
			"  ]," +
			"  \"warnings\": [" +
			"    {" +
			"      \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-502\"," +
			"      \"status\": 503," +
			"      \"title\": \"A warning occurred.\"," +
			"      \"report\": {" +
			"        \"cause\": {" +
			"          \"message\": \"Unavailable\"," +
			"          \"code\": 503" +
			"        }" +
			"      }" +
			"    }" +
			"  ]" +
			"}";

		// bad connection, hits will be retried
		HttpConnecting responseConnection = createNetworkResponse(null, edgeResponse, 503, null, null);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 2);

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("test", "xdm");
						put("testInt", 10);
						put("testBool", false);
						put("testDouble", 12.89);
						put(
							"testArray",
							new ArrayList<String>() {
								{
									add("elem1");
									add("elem2");
								}
							}
						);
						put(
							"testMap",
							new HashMap<String, String>() {
								{
									put("key", "value");
								}
							}
						);
					}
				}
			)
			.build();
		Edge.sendEvent(event, null);
		Edge.sendEvent(event, null);

		assertExpectedEvents(false);
		assertNetworkRequestCount();
		resetTestExpectations();

		// good connection, hits sent
		responseConnection = createNetworkResponse(edgeResponse, 200);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 2);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 2);

		assertNetworkRequestCount();
		assertExpectedEvents(false);
	}

	@Test
	public void testSendEvent_multiStatusResponse_dispatchesEvents() throws InterruptedException {
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		final String response =
			"\u0000{\"requestId\":\"72eaa048-207e-4dde-bf16-0cb2b21336d5\",\"handle\":[],\"errors\":[{\"type\":\"https://ns.adobe.com/aep/errors/EXEG-0201-504\",\"status\":504,\"title\":\"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\"eventIndex\":0}],\"warnings\":[{\"type\":\"https://ns.adobe.com/aep/errors/EXEG-0204-200\",\"status\":200,\"title\":\"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\",\"eventIndex\":0,\"report\":{\"cause\":{\"message\":\"Cannot read related customer for device id: ...\",\"code\":202}}}]}\n";
		HttpConnecting responseConnection = createNetworkResponse(response, 207);

		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 2);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		assertNetworkRequestCount();
		assertExpectedEvents(false);

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());

		List<Event> resultEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(2, resultEvents.size());

		Map<String, String> eventData1 = FunctionalTestUtils.flattenMap(resultEvents.get(0).getEventData());
		assertEquals(6, eventData1.size());
		assertEquals("504", eventData1.get("status"));
		assertEquals("0", eventData1.get("eventIndex"));
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0201-504", eventData1.get("type"));
		assertEquals(
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.",
			eventData1.get("title")
		);
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), eventData1.get("requestEventId"));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), resultEvents.get(0).getParentID());

		Map<String, String> eventData2 = FunctionalTestUtils.flattenMap(resultEvents.get(1).getEventData());
		assertEquals(8, eventData2.size());
		assertEquals("200", eventData2.get("status"));
		assertEquals("0", eventData2.get("eventIndex"));
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0204-200", eventData2.get("type"));
		assertEquals(
			"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.",
			eventData2.get("title")
		);
		assertEquals("Cannot read related customer for device id: ...", eventData2.get("report.cause.message"));
		assertEquals("202", eventData2.get("report.cause.code"));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), eventData2.get("requestEventId"));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), resultEvents.get(1).getParentID());
	}

	@Test
	public void testSendEvent_fatalError() throws InterruptedException {
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		final String response =
			"{" +
			"\"type\" : \"https://ns.adobe.com/aep/errors/EXEG-0104-422\"," +
			"\"status\": 422," +
			"\"title\" : \"Unprocessable Entity\"," +
			"\"detail\": \"Invalid request (report attached). Please check your input and try again.\"," +
			"\"report\": {" +
			"  \"errors\": [" +
			"     \"Allowed Adobe version is 1.0 for standard 'Adobe' at index 0\"," +
			"     \"Allowed IAB version is 2.0 for standard 'IAB TCF' at index 1\"," +
			"     \"IAB consent string value must not be empty for standard 'IAB TCF' at index 1\"" +
			"  ]," +
			"  \"requestId\": \"0f8821e5-ed1a-4301-b445-5f336fb50ee8\"," +
			"  \"orgId\": \"test@AdobeOrg\"" +
			" }" +
			"}";

		HttpConnecting responseConnection = createNetworkResponse(null, response, 422, null, null);
		setNetworkResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		assertNetworkRequestCount();
		assertExpectedEvents(false);

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());

		List<Event> resultEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, resultEvents.size());

		Map<String, String> eventData = FunctionalTestUtils.flattenMap(resultEvents.get(0).getEventData());

		assertEquals(11, eventData.size());
		assertEquals("422", eventData.get("status"));
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0104-422", eventData.get("type"));
		assertEquals("Unprocessable Entity", eventData.get("title"));
		assertEquals(
			"Invalid request (report attached). Please check your input and try again.",
			eventData.get("detail")
		);
		assertEquals("Allowed Adobe version is 1.0 for standard 'Adobe' at index 0", eventData.get("report.errors[0]"));
		assertEquals("Allowed IAB version is 2.0 for standard 'IAB TCF' at index 1", eventData.get("report.errors[1]"));
		assertEquals(
			"IAB consent string value must not be empty for standard 'IAB TCF' at index 1",
			eventData.get("report.errors[2]")
		);
		assertEquals("0f8821e5-ed1a-4301-b445-5f336fb50ee8", eventData.get("report.requestId"));
		assertEquals("test@AdobeOrg", eventData.get("report.orgId"));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), eventData.get("requestEventId"));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), resultEvents.get(0).getParentID());
	}

	private void updateConfiguration(final Map<String, Object> config) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.updateConfiguration(config);
		MobileCore.getPrivacyStatus(mobilePrivacyStatus -> latch.countDown());

		assertTrue(latch.await(2, TimeUnit.SECONDS));
	}
}
