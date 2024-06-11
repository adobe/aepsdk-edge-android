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
import static com.adobe.marketing.mobile.util.JSONAsserts.assertExactMatch;
import static com.adobe.marketing.mobile.util.JSONAsserts.assertTypeMatch;
import static com.adobe.marketing.mobile.util.NodeConfig.Scope.Subtree;
import static com.adobe.marketing.mobile.util.TestHelper.LogOnErrorRule;
import static com.adobe.marketing.mobile.util.TestHelper.SetupCoreRule;
import static com.adobe.marketing.mobile.util.TestHelper.assertExpectedEvents;
import static com.adobe.marketing.mobile.util.TestHelper.assertUnexpectedEvents;
import static com.adobe.marketing.mobile.util.TestHelper.getDispatchedEventsWith;
import static com.adobe.marketing.mobile.util.TestHelper.getSharedStateFor;
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static com.adobe.marketing.mobile.util.TestHelper.sleep;
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
import com.adobe.marketing.mobile.util.AnyOrderMatch;
import com.adobe.marketing.mobile.util.CollectionEqualCount;
import com.adobe.marketing.mobile.util.ElementCount;
import com.adobe.marketing.mobile.util.JSONAsserts;
import com.adobe.marketing.mobile.util.KeyMustBeAbsent;
import com.adobe.marketing.mobile.util.MockNetworkService;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
import com.adobe.marketing.mobile.util.TestXDMSchema;
import com.adobe.marketing.mobile.util.ValueTypeMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EdgeFunctionalTests {

	private static final MockNetworkService mockNetworkService = new MockNetworkService();
	private static final String EXEDGE_INTERACT_URL_STRING = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String EXEDGE_INTERACT_OR2_LOC_URL_STRING =
		TestConstants.Defaults.EXEDGE_INTERACT_OR2_LOC_URL_STRING;
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
	public RuleChain rule = RuleChain.outerRule(new LogOnErrorRule()).around(new SetupCoreRule());

	@Before
	public void setup() throws Exception {
		ServiceProvider.getInstance().setNetworkService(mockNetworkService);

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
		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, Identity.EXTENSION, MonitorExtension.EXTENSION),
			o -> latch.countDown()
		);
		latch.await();

		assertExpectedEvents(false);
		resetTestExpectations();
	}

	@After
	public void tearDown() {
		mockNetworkService.reset();
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
						put("testString", "stringValue");
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

		String expected =
			"{" +
			"  \"xdm\": {" +
			"    \"testString\": \"stringValue\"," +
			"    \"testInt\": 10," +
			"    \"testBool\": false," +
			"    \"testDouble\": 12.89," +
			"    \"testArray\": [" +
			"      \"elem1\"," +
			"      \"elem2\"" +
			"    ]," +
			"    \"testMap\": {" +
			"      \"key\": \"value\"" +
			"    }" +
			"  }" +
			"}";
		JSONAsserts.assertEquals(expected, resultEvents.get(0).getEventData());
	}

	@Test
	public void testSendEvent_withXDMDataAndCustomData_sendsCorrectRequestEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "stringValue");
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

		String expected =
			"{" +
			"  \"xdm\": {" +
			"    \"testString\": \"stringValue\"" +
			"  }," +
			"  \"data\": {" +
			"    \"testString\": \"stringValue\"," +
			"    \"testInt\": 101," +
			"    \"testBool\": true," +
			"    \"testDouble\": 13.66," +
			"    \"testArray\": [" +
			"      \"elem1\"," +
			"      \"elem2\"" +
			"    ]," +
			"    \"testMap\": {" +
			"      \"key\": \"value\"" +
			"    }" +
			"  }" +
			"}";
		JSONAsserts.assertEquals(expected, resultEvents.get(0).getEventData());
	}

	@Test
	public void testSendEvent_withXDMDataAndNullData_sendsCorrectRequestEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "stringValue");
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

		String expected = "{\"xdm\": {\"testString\": \"stringValue\"}}";
		JSONAsserts.assertEquals(expected, resultEvents.get(0).getEventData());
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
	// test complete event format
	// --------------------------------------------------------------------------------------------

	@Test
	public void testDispatchEvent_sendCompleteEvent_sendsPairedCompleteEvent() throws InterruptedException {
		Map<String, Object> eventData = new HashMap<String, Object>() {
			{
				put(
					"xdm",
					new HashMap<String, Object>() {
						{
							put("test", "data");
						}
					}
				);
				put(
					"request",
					new HashMap<String, Object>() {
						{
							put("sendCompletion", true);
						}
					}
				);
			}
		};

		Event edgeEvent = new Event.Builder(
			"Edge Event Completion Request",
			EventType.EDGE,
			EventSource.REQUEST_CONTENT
		)
			.setEventData(eventData)
			.build();

		final CountDownLatch latch = new CountDownLatch(1);

		MobileCore.dispatchEventWithResponseCallback(
			edgeEvent,
			2000,
			new AdobeCallbackWithError<Event>() {
				@Override
				public void fail(AdobeError adobeError) {
					Assert.fail("DispatchEventWithResponseCallback returned an error: " + adobeError.toString());
				}

				@Override
				public void call(Event event) {
					assertEquals("AEP Response Complete", event.getName());
					assertEquals(EventType.EDGE, event.getType());
					assertEquals(EventSource.CONTENT_COMPLETE, event.getSource());
					assertEquals(edgeEvent.getUniqueIdentifier(), event.getParentID());
					assertEquals(edgeEvent.getUniqueIdentifier(), event.getResponseID());

					String expected = "{  \"requestId\": \"STRING_TYPE\" }";
					assertTypeMatch(expected, event.getEventData(), new CollectionEqualCount(Subtree));
					latch.countDown();
				}
			}
		);

		assertTrue(latch.await(3000, TimeUnit.MILLISECONDS));
	}

	// --------------------------------------------------------------------------------------------
	// test network request format
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_withXDMData_sendsExEdgeNetworkRequest() throws Exception {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "stringValue");
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
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"xdm\": {" +
			"        \"_id\": \"STRING_TYPE\"," +
			"        \"testArray\": [\"elem1\", \"elem2\"]," +
			"        \"testBool\": false," +
			"        \"testDouble\": 12.89," +
			"        \"testInt\": 10," +
			"        \"testMap\": {" +
			"          \"key\": \"value\"" +
			"        }," +
			"        \"testString\": \"stringValue\"," +
			"        \"timestamp\": \"STRING_TYPE\"" +
			"      }" +
			"    }" +
			"  ]," +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": true," +
			"        \"lineFeed\": \"\\n\"," +
			"        \"recordSeparator\": \"\\u0000\"" +
			"      }" +
			"    }" +
			"  }," +
			"  \"xdm\": {" +
			"    \"identityMap\": {" +
			"      \"ECID\": [" +
			"        {" +
			"          \"authenticatedState\": \"ambiguous\"," +
			"          \"id\": \"STRING_TYPE\"," +
			"          \"primary\": false" +
			"        }" +
			"      ]" +
			"    }," +
			"    \"implementationDetails\": {" +
			"      \"environment\": \"app\"," +
			"      \"name\": \"" +
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE +
			"\"," +
			"      \"version\": \"" +
			MobileCore.extensionVersion() +
			"+" +
			Edge.extensionVersion() +
			"\"" +
			"    }" +
			"  }" +
			"}";

		assertExactMatch(
			expected,
			networkRequest.getBodyJson(),
			new CollectionEqualCount(Subtree),
			new ValueTypeMatch("xdm.identityMap.ECID[0].id", "events[0].xdm._id", "events[0].xdm.timestamp")
		);
	}

	@Test
	public void testSendEvent_withXDMDataAndCustomData_sendsExEdgeNetworkRequest() throws Exception {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

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
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"data\": {" +
			"        \"testArray\": [\"elem1\", \"elem2\"]," +
			"        \"testBool\": true," +
			"        \"testDouble\": 13.66," +
			"        \"testInt\": 101," +
			"        \"testMap\": {" +
			"          \"key\": \"value\"" +
			"        }," +
			"        \"testString\": \"stringValue\"" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"STRING_TYPE\"," +
			"        \"testString\": \"xdmValue\"," +
			"        \"timestamp\": \"STRING_TYPE\"" +
			"      }" +
			"    }" +
			"  ]," +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": true," +
			"        \"lineFeed\": \"\\n\"," +
			"        \"recordSeparator\": \"\\u0000\"" +
			"      }" +
			"    }" +
			"  }," +
			"  \"xdm\": {" +
			"    \"identityMap\": {" +
			"      \"ECID\": [" +
			"        {" +
			"          \"authenticatedState\": \"ambiguous\"," +
			"          \"id\": \"STRING_TYPE\"," +
			"          \"primary\": false" +
			"        }" +
			"      ]" +
			"    }," +
			"    \"implementationDetails\": {" +
			"      \"environment\": \"app\"," +
			"      \"name\": \"" +
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE +
			"\"," +
			"      \"version\": \"" +
			MobileCore.extensionVersion() +
			"+" +
			Edge.extensionVersion() +
			"\"" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(
			expected,
			networkRequest.getBodyJson(),
			new CollectionEqualCount(Subtree),
			new ValueTypeMatch("xdm.identityMap.ECID[0].id", "events[0].xdm._id", "events[0].xdm.timestamp")
		);
	}

	@Test
	public void testSendEvent_withXDMSchema_sendsExEdgeNetworkRequest() throws Exception {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		TestXDMSchema.TestXDMObject xdmObject = new TestXDMSchema.TestXDMObject();
		xdmObject.innerKey = "testInnerObject";
		TestXDMSchema xdmSchema = new TestXDMSchema();
		xdmSchema.testBool = true;
		xdmSchema.testInt = 100;
		xdmSchema.testString = "testWithXdmSchema";
		xdmSchema.testDouble = 3.42;
		xdmSchema.testXDMObject = xdmObject;

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder().setXdmSchema(xdmSchema).build();
		Edge.sendEvent(experienceEvent, null);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"meta\": {" +
			"        \"collect\": {" +
			"          \"datasetId\": \"abc123def\"" +
			"        }" +
			"      }," +
			"      \"xdm\": {" +
			"        \"_id\": \"STRING_TYPE\"," +
			"        \"testBool\": true," +
			"        \"testDouble\": 3.42," +
			"        \"testInt\": 100," +
			"        \"testString\": \"testWithXdmSchema\"," +
			"        \"timestamp\": \"STRING_TYPE\"," +
			"        \"testXDMObject\": {" +
			"          \"innerKey\": \"testInnerObject\"" +
			"        }" +
			"      }" +
			"    }" +
			"  ]," +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": true," +
			"        \"lineFeed\": \"\\n\"," +
			"        \"recordSeparator\": \"\\u0000\"" +
			"      }" +
			"    }" +
			"  }," +
			"  \"xdm\": {" +
			"    \"identityMap\": {" +
			"      \"ECID\": [" +
			"        {" +
			"          \"authenticatedState\": \"ambiguous\"," +
			"          \"id\": \"STRING_TYPE\"," +
			"          \"primary\": false" +
			"        }" +
			"      ]" +
			"    }," +
			"    \"implementationDetails\": {" +
			"      \"environment\": \"app\"," +
			"      \"name\": \"" +
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE +
			"\"," +
			"      \"version\": \"" +
			MobileCore.extensionVersion() +
			"+" +
			Edge.extensionVersion() +
			"\"" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(
			expected,
			networkRequest.getBodyJson(),
			new CollectionEqualCount(Subtree),
			new ValueTypeMatch("xdm.identityMap.ECID[0].id", "events[0].xdm._id", "events[0].xdm.timestamp")
		);
	}

	@Test
	public void testSendEvent_withEmptyXDMSchema_doesNotSendExEdgeNetworkRequest() throws InterruptedException {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder().setXdmSchema(new TestXDMSchema()).build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testSendEvent_withEmptyXDMSchemaAndEmptyData_doesNotSendExEdgeNetworkRequest()
		throws InterruptedException {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(new TestXDMSchema())
			.setData(new HashMap<>())
			.build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testSendEvent_withEmptyXDMSchemaAndNullData_doesNotSendExEdgeNetworkRequest()
		throws InterruptedException {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(new TestXDMSchema())
			.setData(null)
			.build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		assertEquals(0, resultRequests.size());
	}

	// --------------------------------------------------------------------------------------------
	// Configurable Endpoint
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_withConfigurableEndpoint_withEmptyConfig_usesProductionEndpoint() throws Exception {
		mockNetworkService.setExpectationForNetworkRequest(TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));
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

		mockNetworkService.setExpectationForNetworkRequest(TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));
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

		mockNetworkService.setExpectationForNetworkRequest(TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING, POST, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));
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

		mockNetworkService.setExpectationForNetworkRequest(
			TestConstants.Defaults.EXEDGE_INTERACT_PRE_PROD_URL_STRING,
			POST,
			1
		);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_INTERACT_PRE_PROD_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(TestConstants.Defaults.EXEDGE_INTERACT_PRE_PROD_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));
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

		mockNetworkService.setExpectationForNetworkRequest(
			TestConstants.Defaults.EXEDGE_INTERACT_INT_URL_STRING,
			POST,
			1
		);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_INTERACT_INT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(TestConstants.Defaults.EXEDGE_INTERACT_INT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));
	}

	// --------------------------------------------------------------------------------------------
	// Client-side store
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_twoConsecutiveCalls_appendsReceivedClientSideStore()
		throws InterruptedException, JSONException {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		final String storeResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"key\": \"kndctr_testOrg_AdobeOrg_identity\",\"value\": \"hashed_value\",\"maxAge\": 34128000},{\"key\": \"kndctr_testOrg_AdobeOrg_consent_check\",\"value\": \"1\",\"maxAge\": 7200},{\"key\": \"expired_key\",\"value\": \"1\",\"maxAge\": 0}],\"type\": \"state:store\"}]}\n";
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(storeResponseBody, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no stored data
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		// Asserting body has 12 total key value elements
		assertTypeMatch("{}", resultRequests.get(0).getBodyJson(), new ElementCount(12, Subtree));

		assertExpectedEvents(true);
		resetTestExpectations();

		// send a new event, should contain previously stored store data
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		resultRequests = mockNetworkService.getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());

		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));

		String expected =
			"{" +
			"  \"meta\": {" +
			"      \"state\": {" +
			"        \"entries\": [" +
			"          {" +
			"            \"key\": \"kndctr_testOrg_AdobeOrg_identity\"," +
			"            \"maxAge\": 34128000," +
			"            \"value\": \"hashed_value\"" +
			"          }," +
			"          {" +
			"            \"key\": \"kndctr_testOrg_AdobeOrg_consent_check\"," +
			"            \"maxAge\": 7200," +
			"            \"value\": \"1\"" +
			"          }" +
			"        ]" +
			"      }" +
			"  }" +
			"}";
		assertExactMatch(
			expected,
			networkRequest.getBodyJson(),
			new CollectionEqualCount(Subtree, "meta.state.entries"),
			new AnyOrderMatch("meta.state.entries"),
			new ElementCount(18, Subtree)
		);

		assertExpectedEvents(true);
	}

	@Test
	public void testSendEvent_twoConsecutiveCalls_resetBefore_appendsReceivedClientSideStore()
		throws InterruptedException {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		// send the reset event before
		final Event resetEvent = new Event.Builder("resetEvent", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE)
			.build();
		MobileCore.dispatchEvent(resetEvent);

		final String storeResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"key\": \"kndctr_testOrg_AdobeOrg_identity\",\"value\": \"hashed_value\",\"maxAge\": 34128000},{\"key\": \"kndctr_testOrg_AdobeOrg_consent_check\",\"value\": \"1\",\"maxAge\": 7200},{\"key\": \"expired_key\",\"value\": \"1\",\"maxAge\": 0}],\"type\": \"state:store\"}]}\n";
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(storeResponseBody, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no stored data
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		// Asserting body has 12 total key value elements
		assertTypeMatch("{}", resultRequests.get(0).getBodyJson(), new ElementCount(12, Subtree));

		assertExpectedEvents(true);
		resetTestExpectations();

		// send a new event, should contain previously stored store data
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// Validate
		resultRequests = mockNetworkService.getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());

		// Validate network request query params
		TestableNetworkRequest networkRequest = resultRequests.get(0);
		assertTrue(networkRequest.getUrl().startsWith(EXEDGE_INTERACT_URL_STRING));
		assertEquals(CONFIG_ID, networkRequest.queryParam("configId"));
		assertNotNull(networkRequest.queryParam("requestId"));

		String expected =
			"{" +
			"  \"meta\": {" +
			"      \"state\": {" +
			"        \"entries\": [" +
			"          {" +
			"            \"key\": \"kndctr_testOrg_AdobeOrg_identity\"," +
			"            \"maxAge\": 34128000," +
			"            \"value\": \"hashed_value\"" +
			"          }," +
			"          {" +
			"            \"key\": \"kndctr_testOrg_AdobeOrg_consent_check\"," +
			"            \"maxAge\": 7200," +
			"            \"value\": \"1\"" +
			"          }" +
			"        ]" +
			"      }" +
			"  }" +
			"}";
		assertExactMatch(
			expected,
			networkRequest.getBodyJson(),
			new CollectionEqualCount(Subtree, "meta.state.entries"),
			new AnyOrderMatch("meta.state.entries"),
			new ElementCount(18, Subtree)
		);

		assertExpectedEvents(true);
	}

	@Test
	public void testSendEvent_twoConsecutiveCalls_resetBetween_clearsClientSideStore() throws InterruptedException {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		final String storeResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"key\": \"kndctr_testOrg_AdobeOrg_identity\",\"value\": \"hashed_value\",\"maxAge\": 34128000},{\"key\": \"kndctr_testOrg_AdobeOrg_consent_check\",\"value\": \"1\",\"maxAge\": 7200},{\"key\": \"expired_key\",\"value\": \"1\",\"maxAge\": 0}],\"type\": \"state:store\"}]}\n";
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(storeResponseBody, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no stored data
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		// Asserting body has 12 total key value elements
		assertTypeMatch("{}", resultRequests.get(0).getBodyJson(), new ElementCount(12, Subtree));

		assertExpectedEvents(true);
		resetTestExpectations();

		// send the reset event in-between
		final Event resetEvent = new Event.Builder("resetEvent", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE)
			.build();
		MobileCore.dispatchEvent(resetEvent);

		// send a new event, should contain previously stored store data
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, "state:store", 1);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		resultRequests = mockNetworkService.getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());
		// Asserting body has 12 total key value elements and that "key" is not present in the first
		// element of "entries"
		assertExactMatch(
			"{}",
			resultRequests.get(0).getBodyJson(),
			new ElementCount(12, Subtree),
			new KeyMustBeAbsent("meta.state.entries[0].key")
		);
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
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(responseBody, 200);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertExpectedEvents(true);

		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		TestableNetworkRequest networkRequest = resultRequests.get(0);
		String requestId = networkRequest.queryParam("requestId");

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());
		String requestEventUuid = requestEvents.get(0).getUniqueIdentifier();

		List<Event> responseEvents = getDispatchedEventsWith(EventType.EDGE, "personalization:decisions");
		assertEquals(1, responseEvents.size());

		String expected =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"id\": \"AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9\"," +
			"      \"items\": [" +
			"        {" +
			"          \"data\": {" +
			"            \"content\": {" +
			"              \"value\": \"#D41DBA\"" +
			"            }" +
			"          }," +
			"          \"schema\": \"https://ns.adobe.com/personalization/json-content-item\"" +
			"        }" +
			"      ]," +
			"      \"scope\": \"buttonColor\"" +
			"    }" +
			"  ]," +
			"  \"requestEventId\": \"" +
			requestEventUuid +
			"\"," +
			"  \"requestId\": \"" +
			requestId +
			"\"," +
			"  \"type\": \"personalization:decisions\"" +
			"}";
		Event event = responseEvents.get(0);
		JSONAsserts.assertEquals(expected, event.getEventData());
		assertEquals(requestEventUuid, event.getParentID());
	}

	@Test
	public void testSendEvent_receivesResponseEventWarning_sendsErrorResponseEvent_pairedWithTheRequestEventId()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);

		final String responseBody =
			"\u0000{\"requestId\": \"0ee43289-4a4e-469a-bf5c-1d8186919a26\",\"handle\": [],\"warnings\": [{\"code\": \"personalization:0\",\"message\": \"Failed due to unrecoverable system error\",\"report\":{\"eventIndex\":0}}]}\n";
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(responseBody, 200);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

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

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertExpectedEvents(false);

		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
		TestableNetworkRequest networkRequest = resultRequests.get(0);
		String requestId = networkRequest.queryParam("requestId");

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());
		String requestEventUuid = requestEvents.get(0).getUniqueIdentifier();

		List<Event> errorResponseEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, errorResponseEvents.size());

		String expected =
			"{" +
			"  \"code\": \"personalization:0\"," +
			"  \"message\": \"Failed due to unrecoverable system error\"," +
			"  \"requestEventId\": \"" +
			requestEventUuid +
			"\"," +
			"  \"requestId\": \"" +
			requestId +
			"\"" +
			"}";
		Event errorEvent = errorResponseEvents.get(0);
		JSONAsserts.assertEquals(expected, errorEvent.getEventData());
		assertEquals(requestEventUuid, errorEvent.getParentID());
	}

	// --------------------------------------------------------------------------------------------
	// Location Hint result
	// --------------------------------------------------------------------------------------------

	@Test
	public void testSendEvent_edgeNetworkResponseContainsLocationHint_nextSendEventIncludesLocationHint()
		throws InterruptedException {
		final String hintResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"scope\": \"EdgeNetwork\",\"hint\": \"or2\",\"ttlSeconds\": 1800}],\"type\": \"locationHint:result\"}]}\n";

		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(hintResponseBody, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_OR2_LOC_URL_STRING, POST, 1);

		setExpectationEvent(EventType.EDGE, "locationHint:result", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// first network call, no location hint
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		// second network call, has location hint
		resultRequests =
			mockNetworkService.getNetworkRequestsWith(EXEDGE_INTERACT_OR2_LOC_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(1, resultRequests.size());

		// location hint handle dispatched
		assertExpectedEvents(true);
	}

	@Test
	public void testSendEvent_edgeNetworkResponseContainsLocationHint_sendEventDoesNotIncludeExpiredLocationHint()
		throws InterruptedException {
		final String hintResponseBody =
			"\u0000{\"requestId\": \"0000-4a4e-1111-bf5c-abcd\",\"handle\": [{\"payload\": [{\"scope\": \"EdgeNetwork\",\"hint\": \"or2\",\"ttlSeconds\": 1}],\"type\": \"locationHint:result\"}]}\n";

		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(hintResponseBody, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 2);

		setExpectationEvent(EventType.EDGE, "locationHint:result", 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);
		Thread.sleep(1500); // wait for hint to expire
		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		// all network calls, no location hint
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
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
		Map<String, Object> sharedState = getSharedStateFor(TestConstants.SharedState.EDGE, 1000);
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
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_OR2_LOC_URL_STRING, POST, 1);

		Edge.setLocationHint("or2"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_OR2_LOC_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
	}

	@Test
	public void testSetLocationHint_withHintWithSpaces_edgeNetworkResponseContainsLocationHint()
		throws InterruptedException {
		mockNetworkService.setExpectationForNetworkRequest(
			"https://edge.adobedc.net/ee/incorrect location hint/v1/interact",
			POST,
			1
		);

		Edge.setLocationHint("incorrect location hint"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			"https://edge.adobedc.net/ee/incorrect location hint/v1/interact",
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
	}

	@Test
	public void testSetLocationHint_withHintWithSpecialCharacters_edgeNetworkResponseContainsLocationHint()
		throws InterruptedException {
		mockNetworkService.setExpectationForNetworkRequest(
			"https://edge.adobedc.net/ee/{\"example\":\"incorrect\"}/v1/interact",
			POST,
			1
		);

		Edge.setLocationHint("{\"example\":\"incorrect\"}"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			"https://edge.adobedc.net/ee/{\"example\":\"incorrect\"}/v1/interact",
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());
	}

	@Test
	public void testSetLocationHint_withHintWithUnicodeCharacters_edgeNetworkResponseContainsLocationHint()
		throws InterruptedException {
		mockNetworkService.setExpectationForNetworkRequest(
			"https://edge.adobedc.net/ee/\u0048\u0065\u006C\u006C\u006F/v1/interact",
			POST,
			1
		);

		Edge.setLocationHint("\u0048\u0065\u006C\u006C\u006F"); // set hint

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null); // send event

		// verify send event request includes set location hint
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
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
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			null,
			edgeResponse,
			503,
			null,
			null
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

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
		mockNetworkService.assertAllNetworkRequestExpectations();
		resetTestExpectations();

		// good connection, hits sent
		responseConnection = mockNetworkService.createMockNetworkResponse(edgeResponse, 200);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);

		// Edge retry interval is 5 sec
		mockNetworkService.assertAllNetworkRequestExpectations(true, true, 6000);
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
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			null,
			edgeResponse,
			503,
			null,
			null
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
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
		mockNetworkService.assertAllNetworkRequestExpectations();
		resetTestExpectations();

		// good connection, hits sent
		responseConnection = mockNetworkService.createMockNetworkResponse(edgeResponse, 200);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 2);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 2);

		// Edge retry interval is 5 sec
		mockNetworkService.assertAllNetworkRequestExpectations(true, true, 6000);
		assertExpectedEvents(false);
	}

	@Test
	public void testSendEvent_multiStatusResponse_dispatchesEvents() throws InterruptedException {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
		final String response =
			"\u0000{\"requestId\":\"72eaa048-207e-4dde-bf16-0cb2b21336d5\",\"handle\":[],\"errors\":[{\"type\":\"https://ns.adobe.com/aep/errors/EXEG-0201-504\",\"status\":504,\"title\":\"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\"report\":{\"eventIndex\":0}}],\"warnings\":[{\"type\":\"https://ns.adobe.com/aep/errors/EXEG-0204-200\",\"status\":200,\"title\":\"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\",\"report\":{\"eventIndex\":0,\"cause\":{\"message\":\"Cannot read related customer for device id: ...\",\"code\":202}}}]}\n";
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(response, 207);

		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 2);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertExpectedEvents(false);

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());

		List<Event> resultEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(2, resultEvents.size());

		String expectedEventData1 =
			"{" +
			"  \"requestEventId\": \"" +
			requestEvents.get(0).getUniqueIdentifier() +
			"\"," +
			"  \"status\": 504," +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-504\"" +
			"}";
		assertExactMatch(expectedEventData1, resultEvents.get(0).getEventData(), new ElementCount(5, Subtree));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), resultEvents.get(0).getParentID());

		String expectedEventData2 =
			"{" +
			"  \"report\": {" +
			"    \"cause\": {" +
			"      \"code\": 202," +
			"      \"message\": \"Cannot read related customer for device id: ...\"" +
			"    }" +
			"  }," +
			"  \"requestEventId\": \"" +
			requestEvents.get(0).getUniqueIdentifier() +
			"\"," +
			"  \"status\": 200," +
			"  \"title\": \"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-200\"" +
			"}";
		assertExactMatch(expectedEventData2, resultEvents.get(1).getEventData(), new ElementCount(7, Subtree));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), resultEvents.get(1).getParentID());
	}

	@Test
	public void testSendEvent_fatalError() throws InterruptedException {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);
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

		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			null,
			response,
			422,
			null,
			null
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);

		Edge.sendEvent(XDM_EXPERIENCE_EVENT, null);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertExpectedEvents(false);

		List<Event> requestEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.REQUEST_CONTENT);
		assertEquals(1, requestEvents.size());

		List<Event> resultEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, resultEvents.size());

		String expected =
			"{" +
			"  \"detail\": \"Invalid request (report attached). Please check your input and try again.\"," +
			"  \"report\": {" +
			"    \"errors\": [" +
			"      \"Allowed Adobe version is 1.0 for standard 'Adobe' at index 0\"," +
			"      \"Allowed IAB version is 2.0 for standard 'IAB TCF' at index 1\"," +
			"      \"IAB consent string value must not be empty for standard 'IAB TCF' at index 1\"" +
			"    ]," +
			"    \"orgId\": \"test@AdobeOrg\"," +
			"    \"requestId\": \"0f8821e5-ed1a-4301-b445-5f336fb50ee8\"" +
			"  }," +
			"  \"requestEventId\": \"" +
			requestEvents.get(0).getUniqueIdentifier() +
			"\"," +
			"  \"status\": 422," +
			"  \"title\": \"Unprocessable Entity\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0104-422\"" +
			"}";
		assertExactMatch(expected, resultEvents.get(0).getEventData(), new ElementCount(11, Subtree));
		assertEquals(requestEvents.get(0).getUniqueIdentifier(), resultEvents.get(0).getParentID());
	}

	/**
	 * Resets all test helper expectations and recorded data
	 */
	private void resetTestExpectations() {
		mockNetworkService.reset();
		TestHelper.resetTestExpectations();
	}

	private void updateConfiguration(final Map<String, Object> config) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.updateConfiguration(config);
		MobileCore.getPrivacyStatus(mobilePrivacyStatus -> latch.countDown());

		assertTrue(latch.await(2, TimeUnit.SECONDS));
	}
}
