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

import static com.adobe.marketing.mobile.util.TestHelper.assertExpectedEvents;
import static com.adobe.marketing.mobile.util.TestHelper.getDispatchedEventsWith;
import static com.adobe.marketing.mobile.util.TestHelper.resetTestExpectations;
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.MockDataStoreService;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
import com.adobe.marketing.mobile.util.TestUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NetworkResponseHandlerFunctionalTests {

	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";

	private static final Event event1 = new Event.Builder("e1", "eventType", "eventSource").build();
	private static final Event event2 = new Event.Builder("e2", "eventType", "eventSource").build();
	private NetworkResponseHandler networkResponseHandler;

	private String receivedSetLocationHint;
	private Integer receivedSetTtlSeconds;
	private final MockDataStoreService mockDataStoreService = new MockDataStoreService();
	private NamedCollection testNamedCollection;

	private final EdgeStateCallback edgeStateCallback = new EdgeStateCallback() {
		@Override
		public Map<String, Object> getImplementationDetails() {
			return null; // not called by NetworkResponseHandler
		}

		@Override
		public String getLocationHint() {
			return null; // not called by NetworkResponseHandler
		}

		@Override
		public void setLocationHint(String hint, int ttlSeconds) {
			receivedSetLocationHint = hint;
			receivedSetTtlSeconds = ttlSeconds;
		}
	};

	@Rule
	public RuleChain rule = RuleChain
		.outerRule(new TestHelper.LogOnErrorRule())
		.around(new TestHelper.SetupCoreRule())
		.around(new TestHelper.RegisterMonitorExtensionRule());

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
		resetTestExpectations(null);

		this.testNamedCollection = mockDataStoreService.getNamedCollection(TestConstants.EDGE_DATA_STORAGE);
		this.networkResponseHandler = new NetworkResponseHandler(testNamedCollection, edgeStateCallback);
	}

	// ---------------------------------------------------------------------------------------------
	// processResponseOnError
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnError_WhenEmptyJsonError_doesNotHandleError() throws InterruptedException {
		final String jsonError = "";
		networkResponseHandler.processResponseOnError(jsonError, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(0, dispatchEvents.size());
	}

	@Test
	public void testProcessResponseOnError_WhenInvalidJsonError_doesNotHandleError() throws InterruptedException {
		final String jsonError = "{ invalid json }";
		networkResponseHandler.processResponseOnError(jsonError, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(0, dispatchEvents.size());
	}

	@Test
	public void testProcessResponseOnError_WhenGenericJsonError_noMatchingEvents_dispatchesEvent()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);
		final String jsonError =
			"{\n" +
			"\"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"\"title\": \"Request to Data platform failed with an unknown exception\"" +
			"\n}";
		networkResponseHandler.addWaitingEvent("abc", event1); // Request ID does not match
		networkResponseHandler.processResponseOnError(jsonError, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 5000);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(3, flattenReceivedData.size());
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0201-503", flattenReceivedData.get("type"));
		assertEquals("Request to Data platform failed with an unknown exception", flattenReceivedData.get("title"));
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertNull(flattenReceivedData.get("requestEventId"));
		assertNull(dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnError_WhenGenericJsonError_dispatchesEventChainedToParentEvent()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);
		final String jsonError =
			"{\n" +
			"\"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"\"title\": \"Request to Data platform failed with an unknown exception\"" +
			"\n}";
		networkResponseHandler.addWaitingEvent("123", event1);
		networkResponseHandler.processResponseOnError(jsonError, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 5000);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(4, flattenReceivedData.size());
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0201-503", flattenReceivedData.get("type"));
		assertEquals("Request to Data platform failed with an unknown exception", flattenReceivedData.get("title"));
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnError_WhenOneEventJsonError_dispatchesEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 500,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\"\n" +
			"        }\n" +
			"      ]\n" +
			"    }";
		networkResponseHandler.processResponseOnError(jsonError, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(4, flattenReceivedData.size());
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0201-503", flattenReceivedData.get("type"));
		assertEquals("500", flattenReceivedData.get("status"));
		assertEquals(
			"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases",
			flattenReceivedData.get("title")
		);
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertNull(dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnError_WhenValidEventIndex_dispatchesPairedEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);
		final String requestId = "123";
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 100,\n" +
			"          \"type\": \"personalization\",\n" +
			"          \"title\": \"Button color not found\",\n" +
			"          \"report\": {\n" +
			"           \"eventIndex\": 1\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }";
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(event1);
					add(event2);
				}
			}
		);
		networkResponseHandler.processResponseOnError(jsonError, requestId);
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(5, flattenReceivedData.size());
		assertEquals("personalization", flattenReceivedData.get("type"));
		assertEquals("100", flattenReceivedData.get("status"));
		assertEquals("Button color not found", flattenReceivedData.get("title"));
		assertEquals(requestId, flattenReceivedData.get("requestId"));
		assertEquals(event2.getUniqueIdentifier(), flattenReceivedData.get("requestEventId"));
		assertEquals(event2.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnError_WhenUnknownEventIndex_doesNotCrash() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);
		final String requestId = "123";
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 100,\n" +
			"          \"type\": \"personalization\",\n" +
			"          \"title\": \"Button color not found\",\n" +
			"          \"report\": {\n" +
			"           \"eventIndex\": 10\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }";
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(event1);
					add(event2);
				}
			}
		);
		networkResponseHandler.processResponseOnError(jsonError, requestId);
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(4, flattenReceivedData.size());
		assertEquals("personalization", flattenReceivedData.get("type"));
		assertEquals("100", flattenReceivedData.get("status"));
		assertEquals("Button color not found", flattenReceivedData.get("title"));
		assertEquals(requestId, flattenReceivedData.get("requestId"));
		assertNull(dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnError_WhenUnknownRequestId_doesNotCrash() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);
		final String requestId = "123";
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 100,\n" +
			"          \"type\": \"personalization\",\n" +
			"          \"title\": \"Button color not found\",\n" +
			"          \"report\": {\n" +
			"           \"eventIndex\": 0\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }";
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(event1);
					add(event2);
				}
			}
		);
		networkResponseHandler.processResponseOnError(jsonError, "567");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(4, flattenReceivedData.size());
		assertEquals("personalization", flattenReceivedData.get("type"));
		assertEquals("100", flattenReceivedData.get("status"));
		assertEquals("Button color not found", flattenReceivedData.get("title"));
		assertEquals("567", flattenReceivedData.get("requestId"));
		assertNull(dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnError_WhenTwoEventJsonError_dispatchesTwoEvents() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 2);
		final String requestId = "123";
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 0,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\"\n" +
			"        },\n" +
			"        {\n" +
			"          \"status\": 2003,\n" +
			"          \"type\": \"personalization\",\n" +
			"          \"title\": \"Failed to process personalization event\"\n" +
			"        }\n" +
			"      ]\n" +
			"    }";
		networkResponseHandler.addWaitingEvent(requestId, event1);
		networkResponseHandler.processResponseOnError(jsonError, requestId);
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(2, dispatchEvents.size());

		Map<String, String> flattenReceivedData1 = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(5, flattenReceivedData1.size());
		assertEquals("0", flattenReceivedData1.get("status"));
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0201-503", flattenReceivedData1.get("type"));
		assertEquals(
			"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases",
			flattenReceivedData1.get("title")
		);
		assertEquals(requestId, flattenReceivedData1.get("requestId"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData1.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());

		Map<String, String> flattenReceivedData2 = TestUtils.flattenMap(dispatchEvents.get(1).getEventData());
		assertEquals(5, flattenReceivedData2.size());
		assertEquals("2003", flattenReceivedData2.get("status"));
		assertEquals("personalization", flattenReceivedData2.get("type"));
		assertEquals("Failed to process personalization event", flattenReceivedData2.get("title"));
		assertEquals(requestId, flattenReceivedData2.get("requestId"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData2.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(1).getParentID());
	}

	// ---------------------------------------------------------------------------------------------
	// processResponseOnSuccess
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnSuccess_WhenEmptyJsonResponse_doesNotDispatchEvent() throws InterruptedException {
		final String jsonResponse = "";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.RESPONSE_CONTENT);
		assertEquals(0, dispatchEvents.size());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenInvalidJsonResponse_doesNotDispatchEvent()
		throws InterruptedException {
		final String jsonResponse = "{ invalid json }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.RESPONSE_CONTENT);
		assertEquals(0, dispatchEvents.size());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenOneEventHandle_dispatchesEvent() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"state:store\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"key\": \"s_ecid\",\n" +
			"                    \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"                    \"maxAge\": 15552000\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";
		networkResponseHandler.addWaitingEvent("123", event1);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, "state:store");
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(6, flattenReceivedData.size());
		assertEquals("state:store", flattenReceivedData.get("type"));
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertEquals("s_ecid", flattenReceivedData.get("payload[0].key"));
		assertEquals("MCMID|29068398647607325310376254630528178721", flattenReceivedData.get("payload[0].value"));
		assertEquals("15552000", flattenReceivedData.get("payload[0].maxAge"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenOneEventHandle_emptyEventHandleType_dispatchesEvent()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"key\": \"s_ecid\",\n" +
			"                    \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"                    \"maxAge\": 15552000\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.RESPONSE_CONTENT);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(4, flattenReceivedData.size());
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertEquals("s_ecid", flattenReceivedData.get("payload[0].key"));
		assertEquals("MCMID|29068398647607325310376254630528178721", flattenReceivedData.get("payload[0].value"));
		assertEquals("15552000", flattenReceivedData.get("payload[0].maxAge"));
	}

	@Test
	public void testProcessResponseOnSuccess_WhenOneEventHandle_nullEventHandleType_dispatchesEvent()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"key\": \"s_ecid\",\n" +
			"                    \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"                    \"maxAge\": 15552000\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.RESPONSE_CONTENT);
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(4, flattenReceivedData.size());
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertEquals("s_ecid", flattenReceivedData.get("payload[0].key"));
		assertEquals("MCMID|29068398647607325310376254630528178721", flattenReceivedData.get("payload[0].value"));
		assertEquals("15552000", flattenReceivedData.get("payload[0].maxAge"));
	}

	@Test
	public void testProcessResponseOnSuccess_WhenTwoEventHandles_dispatchesTwoEvents() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 2);
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"            {\n" +
			"            \"type\": \"state:store\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"key\": \"s_ecid\",\n" +
			"                    \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"                    \"maxAge\": 15552000\n" +
			"                }\n" +
			"            ]},\n" +
			"           {\n" +
			"            \"type\": \"identity:persist\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"id\": \"29068398647607325310376254630528178721\",\n" +
			"                    \"namespace\": {\n" +
			"                        \"code\": \"ECID\"\n" +
			"                    }\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, "state:store");
		dispatchEvents.addAll(getDispatchedEventsWith(EventType.EDGE, "identity:persist"));
		assertEquals(2, dispatchEvents.size());

		// verify event 1
		Map<String, String> flattenReceivedData1 = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(5, flattenReceivedData1.size());
		assertEquals("state:store", flattenReceivedData1.get("type"));
		assertEquals("123", flattenReceivedData1.get("requestId"));
		assertEquals("s_ecid", flattenReceivedData1.get("payload[0].key"));
		assertEquals("MCMID|29068398647607325310376254630528178721", flattenReceivedData1.get("payload[0].value"));
		assertEquals("15552000", flattenReceivedData1.get("payload[0].maxAge"));
		assertNull(dispatchEvents.get(0).getParentID());

		// verify event 2
		Map<String, String> flattenReceivedData2 = TestUtils.flattenMap(dispatchEvents.get(1).getEventData());
		assertEquals(4, flattenReceivedData2.size());
		assertEquals("identity:persist", flattenReceivedData2.get("type"));
		assertEquals("123", flattenReceivedData2.get("requestId"));
		assertEquals("29068398647607325310376254630528178721", flattenReceivedData2.get("payload[0].id"));
		assertEquals("ECID", flattenReceivedData2.get("payload[0].namespace.code"));
		assertNull(dispatchEvents.get(1).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleWithEventIndex_dispatchesEventWithRequestEventId()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 2);
		final String requestId = "123";
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"            {\n" +
			"            \"type\": \"state:store\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"key\": \"s_ecid\",\n" +
			"                    \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"                    \"maxAge\": 15552000\n" +
			"                }\n" +
			"            ]},\n" +
			"           {\n" +
			"            \"type\": \"pairedeventexample\",\n" +
			"            \"eventIndex\": 1,\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"id\": \"123612123812381\"\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(event1);
					add(event2);
				}
			}
		);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);

		// verify event 1
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, "state:store");
		dispatchEvents.addAll(getDispatchedEventsWith(EventType.EDGE, "pairedeventexample"));
		assertEquals(2, dispatchEvents.size());

		Map<String, String> flattenReceivedData1 = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(6, flattenReceivedData1.size());
		assertEquals("state:store", flattenReceivedData1.get("type"));
		assertEquals("s_ecid", flattenReceivedData1.get("payload[0].key"));
		assertEquals("MCMID|29068398647607325310376254630528178721", flattenReceivedData1.get("payload[0].value"));
		assertEquals("15552000", flattenReceivedData1.get("payload[0].maxAge"));
		assertEquals("123", flattenReceivedData1.get("requestId"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData1.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());

		// verify event 2
		Map<String, String> flattenReceivedData2 = TestUtils.flattenMap(dispatchEvents.get(1).getEventData());
		assertEquals(4, flattenReceivedData2.size());
		assertEquals("pairedeventexample", flattenReceivedData2.get("type"));
		assertEquals("123612123812381", flattenReceivedData2.get("payload[0].id"));
		assertEquals("123", flattenReceivedData2.get("requestId"));
		assertEquals(event2.getUniqueIdentifier(), flattenReceivedData2.get("requestEventId"));
		assertEquals(event2.getUniqueIdentifier(), dispatchEvents.get(1).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleWithUnknownEventIndex_dispatchesUnpairedEvent()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		final String requestId = "123";
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"pairedeventexample\",\n" +
			"            \"eventIndex\": 10,\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"id\": \"123612123812381\"\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(event1);
					add(event2);
				}
			}
		);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, "pairedeventexample");
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(3, flattenReceivedData.size());
		assertEquals("pairedeventexample", flattenReceivedData.get("type"));
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertEquals("123612123812381", flattenReceivedData.get("payload[0].id"));
		assertNull(dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenUnknownRequestId_doesNotCrash() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		final String requestId = "123";
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"pairedeventexample\",\n" +
			"            \"eventIndex\": 0,\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"id\": \"123612123812381\"\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.addWaitingEvents(
			"567",
			new ArrayList<Event>() {
				{
					add(event1);
					add(event2);
				}
			}
		);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, "pairedeventexample");
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(3, flattenReceivedData.size());
		assertEquals("pairedeventexample", flattenReceivedData.get("type"));
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertEquals("123612123812381", flattenReceivedData.get("payload[0].id"));
		assertNull(dispatchEvents.get(0).getParentID());
	}

	// ---------------------------------------------------------------------------------------------
	// processResponseOnSuccess with mixed event handles, errors, warnings
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleAndError_dispatchesTwoEvents() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);
		final String requestId = "123";
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"            {\n" +
			"            \"type\": \"state:store\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"key\": \"s_ecid\",\n" +
			"                    \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"                    \"maxAge\": 15552000\n" +
			"                }\n" +
			"            ]}],\n" +
			"      \"errors\": [" +
			"        {\n" +
			"          \"status\": 2003,\n" +
			"          \"type\": \"personalization\",\n" +
			"          \"title\": \"Failed to process personalization event\"\n" +
			"        }\n" +
			"       ]\n" +
			"    }";

		networkResponseHandler.addWaitingEvents(requestId, Arrays.asList(event1, event2));
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, "state:store");
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData1 = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(6, flattenReceivedData1.size());
		assertEquals("state:store", flattenReceivedData1.get("type"));
		assertEquals("123", flattenReceivedData1.get("requestId"));
		assertEquals("s_ecid", flattenReceivedData1.get("payload[0].key"));
		assertEquals("MCMID|29068398647607325310376254630528178721", flattenReceivedData1.get("payload[0].value"));
		assertEquals("15552000", flattenReceivedData1.get("payload[0].maxAge"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData1.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());

		List<Event> dispatchErrorEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(1, dispatchErrorEvents.size());

		Map<String, String> flattenReceivedData2 = TestUtils.flattenMap(dispatchErrorEvents.get(0).getEventData());
		assertEquals(5, flattenReceivedData2.size());
		assertEquals("personalization", flattenReceivedData2.get("type"));
		assertEquals("2003", flattenReceivedData2.get("status"));
		assertEquals("Failed to process personalization event", flattenReceivedData2.get("title"));
		assertEquals("123", flattenReceivedData2.get("requestId"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData2.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchErrorEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenErrorAndWarning_dispatchesTwoEvents() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 2);
		final String requestId = "123";
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [" +
			"        {\n" +
			"          \"status\": 2003,\n" +
			"          \"title\": \"Failed to process personalization event\",\n" +
			"          \"report\": {\n" +
			"           \"eventIndex\": 1 \n" +
			"          }\n" +
			"        }\n" +
			"       ],\n" +
			"      \"warnings\": [" +
			"        {\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-200\",\n" +
			"          \"status\": 98,\n" +
			"          \"title\": \"Some Informative stuff here\",\n" +
			"          \"report\": {" +
			"             \"eventIndex\": 0, \n" +
			"             \"cause\": {" +
			"                \"message\": \"Some Informative stuff here\",\n" +
			"                \"code\": 202\n" +
			"             }" +
			"          }" +
			"        }\n" +
			"       ]\n" +
			"    }";

		networkResponseHandler.addWaitingEvents(requestId, Arrays.asList(event1, event2));
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(2, dispatchEvents.size());

		Map<String, String> flattenReceivedData1 = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(4, flattenReceivedData1.size());
		assertEquals("2003", flattenReceivedData1.get("status"));
		assertEquals("Failed to process personalization event", flattenReceivedData1.get("title"));
		assertEquals("123", flattenReceivedData1.get("requestId"));
		assertEquals(event2.getUniqueIdentifier(), flattenReceivedData1.get("requestEventId"));
		assertEquals(event2.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());

		Map<String, String> flattenReceivedData2 = TestUtils.flattenMap(dispatchEvents.get(1).getEventData());
		assertEquals(7, flattenReceivedData2.size());
		assertEquals("https://ns.adobe.com/aep/errors/EXEG-0204-200", flattenReceivedData2.get("type"));
		assertEquals("98", flattenReceivedData2.get("status"));
		assertEquals("Some Informative stuff here", flattenReceivedData2.get("title"));
		assertEquals("Some Informative stuff here", flattenReceivedData2.get("report.cause.message"));
		assertEquals("202", flattenReceivedData2.get("report.cause.code"));
		assertEquals("123", flattenReceivedData2.get("requestId"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData2.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(1).getParentID());
	}

	// ---------------------------------------------------------------------------------------------
	// processResponseOnSuccess with locationHint:result
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnSuccess_WhenLocationHintResultEventHandle_dispatchesEvent()
		throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"hint\": \"or2\",\n" +
			"                    \"ttlSeconds\": 1800\n" +
			"                },\n" +
			"                {\n" +
			"                    \"scope\": \"Target\",\n" +
			"                    \"hint\": \"edge34\",\n" +
			"                    \"ttlSeconds\": 600\n" +
			"                }\n" +
			"            ]\n" +
			"        }]\n" +
			"    }";
		networkResponseHandler.addWaitingEvent("123", event1);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, "locationHint:result");
		assertEquals(1, dispatchEvents.size());

		Map<String, String> flattenReceivedData = TestUtils.flattenMap(dispatchEvents.get(0).getEventData());
		assertEquals(9, flattenReceivedData.size());
		assertEquals("locationHint:result", flattenReceivedData.get("type"));
		assertEquals("123", flattenReceivedData.get("requestId"));
		assertEquals("EdgeNetwork", flattenReceivedData.get("payload[0].scope"));
		assertEquals("or2", flattenReceivedData.get("payload[0].hint"));
		assertEquals("1800", flattenReceivedData.get("payload[0].ttlSeconds"));
		assertEquals("Target", flattenReceivedData.get("payload[1].scope"));
		assertEquals("edge34", flattenReceivedData.get("payload[1].hint"));
		assertEquals("600", flattenReceivedData.get("payload[1].ttlSeconds"));
		assertEquals(event1.getUniqueIdentifier(), flattenReceivedData.get("requestEventId"));
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_afterResetEvent_updatesLocationHint() {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(event1.getTimestamp());
		calendar.add(Calendar.SECOND, -10); // date is before event timestamp
		networkResponseHandler.setLastResetDate(calendar.getTimeInMillis());

		networkResponseHandler.addWaitingEvents(
			"d81c93e5-7558-4996-a93c-489d550748b8",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"hint\": \"or2\",\n" +
			"                    \"ttlSeconds\": 1800\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify saved location hint
		assertEquals("or2", receivedSetLocationHint);
		assertEquals(Integer.valueOf(1800), receivedSetTtlSeconds);
	}

	@Test
	public void testProcessResponseOnSuccess_beforeResetEvent_doesNotUpdateLocationHint() {
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(event1.getTimestamp());
		calendar.add(Calendar.SECOND, 10); // date is after event timestamp
		networkResponseHandler.setLastResetDate(calendar.getTimeInMillis());

		networkResponseHandler.addWaitingEvents(
			"d81c93e5-7558-4996-a93c-489d550748b8",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"hint\": \"or2\",\n" +
			"                    \"ttlSeconds\": 1800\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify saved location hint
		assertNull(receivedSetLocationHint);
		assertNull(receivedSetTtlSeconds);
	}

	@Test
	public void testProcessResponseOnSuccess_whenEventHandleHasBothStateStoreAndLocationHintResult_stateStoreSaved_locationHintUpdated() {
		networkResponseHandler.addWaitingEvents(
			"d81c93e5-7558-4996-a93c-489d550748b8",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"hint\": \"or2\",\n" +
			"                    \"ttlSeconds\": 1800\n" +
			"                }\n" +
			"            ]},\n" +
			"           {\n" +
			"            \"type\": \"state:store\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"key\": \"s_ecid\",\n" +
			"                    \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"                    \"maxAge\": 15552000\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify saved location hint
		assertEquals("or2", receivedSetLocationHint);
		assertEquals(Integer.valueOf(1800), receivedSetTtlSeconds);

		// verify saved state
		StoreResponsePayloadManager payloadManager = new StoreResponsePayloadManager(testNamedCollection);
		assertFalse(payloadManager.getActiveStores().isEmpty());
	}

	@Test
	public void testProcessResponseOnSuccess_whenLocationHintHandleDoesNotHaveHint_thenLocationHintNotUpdate() {
		networkResponseHandler.addWaitingEvents(
			"d81c93e5-7558-4996-a93c-489d550748b8",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"ttlSeconds\": 1800\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify saved location hint
		assertNull(receivedSetLocationHint);
		assertNull(receivedSetTtlSeconds);
	}

	@Test
	public void testProcessResponseOnSuccess_whenLocationHintHandleHasEmptyHint_thenLocationHintNotUpdated() {
		networkResponseHandler.addWaitingEvents(
			"d81c93e5-7558-4996-a93c-489d550748b8",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"hint\": \"\",\n" +
			"                    \"ttlSeconds\": 1800\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify saved location hint
		assertNull(receivedSetLocationHint);
		assertNull(receivedSetTtlSeconds);
	}

	@Test
	public void testProcessResponseOnSuccess_whenLocationHintHandleDoesNotHaveTtl_thenLocationHintNotUpdated() {
		networkResponseHandler.addWaitingEvents(
			"d81c93e5-7558-4996-a93c-489d550748b8",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"hint\": \"or2\"\n" +
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify saved location hint
		assertNull(receivedSetLocationHint);
		assertNull(receivedSetTtlSeconds);
	}

	@Test
	public void testProcessResponseOnSuccess_whenLocationHintHandleHasIncorrectTtlType_thenLocationHintNotUpdated() {
		networkResponseHandler.addWaitingEvents(
			"d81c93e5-7558-4996-a93c-489d550748b8",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [" +
			"           {\n" +
			"            \"type\": \"locationHint:result\",\n" +
			"            \"payload\": [\n" +
			"                {\n" +
			"                    \"scope\": \"EdgeNetwork\",\n" +
			"                    \"hint\": \"or2\",\n" +
			"                    \"ttlSeconds\": \"1800\"\n" + // String but expect Int
			"                }\n" +
			"            ]\n" +
			"        }],\n" +
			"      \"errors\": []\n" +
			"    }";

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify saved location hint
		assertNull(receivedSetLocationHint);
		assertNull(receivedSetTtlSeconds);
	}
}
