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
import com.adobe.marketing.mobile.util.JSONAsserts;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
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
	private static final Event eventSendComplete = new Event.Builder("ec", "eventType", "eventSource")
		.setEventData(
			new HashMap<String, Object>() {
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
			}
		)
		.build();
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
	public RuleChain rule = RuleChain.outerRule(new TestHelper.LogOnErrorRule()).around(new TestHelper.SetupCoreRule());

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
		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, Identity.EXTENSION, MonitorExtension.EXTENSION),
			o -> latch.countDown()
		);
		latch.await();

		assertExpectedEvents(false);
		resetTestExpectations();

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
			"{" +
			"\"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\"," +
			"\"title\": \"Request to Data platform failed with an unknown exception\"" +
			"}";
		networkResponseHandler.addWaitingEvent("abc", event1); // Request ID does not match
		networkResponseHandler.processResponseOnError(jsonError, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 5000);
		assertEquals(1, dispatchEvents.size());

		String expected =
			"{" +
			"  \"requestId\": \"123\"," +
			"  \"title\": \"Request to Data platform failed with an unknown exception\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expected =
			"{" +
			"  \"requestEventId\": \"" +
			event1.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"title\": \"Request to Data platform failed with an unknown exception\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expected =
			"{" +
			"  \"requestId\": \"123\"," +
			"  \"status\": 500," +
			"  \"title\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
		assertNull(dispatchEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnError_WhenJsonErrorIncorrectType_doesNotHandleError() throws InterruptedException {
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\":\n" + // Json Array expected
			"        {\n" +
			"          \"status\": 500,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\"\n" +
			"        }\n" +
			"    }";
		networkResponseHandler.processResponseOnError(jsonError, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(0, dispatchEvents.size());
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

		String expected =
			"{" +
			"  \"requestEventId\": \"" +
			event2.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"" +
			requestId +
			"\"," +
			"  \"status\": 100," +
			"  \"title\": \"Button color not found\"," +
			"  \"type\": \"personalization\"" +
			"}";

		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expected =
			"{" +
			"  \"requestId\": \"" +
			requestId +
			"\"," +
			"  \"status\": 100," +
			"  \"title\": \"Button color not found\"," +
			"  \"type\": \"personalization\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expected =
			"{" +
			"  \"requestId\": \"567\"," +
			"  \"status\": 100," +
			"  \"title\": \"Button color not found\"," +
			"  \"type\": \"personalization\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expectedEventData1 =
			"{" +
			"  \"requestEventId\": \"" +
			event1.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"" +
			requestId +
			"\"," +
			"  \"status\": 0," +
			"  \"title\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\"" +
			"}";
		JSONAsserts.assertEquals(expectedEventData1, dispatchEvents.get(0).getEventData());
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());

		String expectedEventData2 =
			"{" +
			"  \"requestEventId\": \"" +
			event1.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"" +
			requestId +
			"\"," +
			"  \"status\": 2003," +
			"  \"title\": \"Failed to process personalization event\"," +
			"  \"type\": \"personalization\"" +
			"}";
		JSONAsserts.assertEquals(expectedEventData2, dispatchEvents.get(1).getEventData());
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(1).getParentID());
	}

	// ---------------------------------------------------------------------------------------------
	// processResponseOnSuccess
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnSuccess_WhenEmptyStringResponse_doesNotDispatchEvent()
		throws InterruptedException {
		final String jsonResponse = "";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.RESPONSE_CONTENT);
		assertEquals(0, dispatchEvents.size());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEmptyJsonResponse_doesNotDispatchEvent() throws InterruptedException {
		final String jsonResponse = "{}";
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

		String expected =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"key\": \"s_ecid\"," +
			"      \"maxAge\": 15552000," +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\"" +
			"    }" +
			"  ]," +
			"  \"requestEventId\": \"" +
			event1.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"state:store\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expected =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"key\": \"s_ecid\"," +
			"      \"maxAge\": 15552000," +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\"" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expected =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"key\": \"s_ecid\"," +
			"      \"maxAge\": 15552000," +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\"" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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
		String expectedEventData1 =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"key\": \"s_ecid\"," +
			"      \"maxAge\": 15552000," +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\"" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"state:store\"" +
			"}";
		JSONAsserts.assertEquals(expectedEventData1, dispatchEvents.get(0).getEventData());
		assertNull(dispatchEvents.get(0).getParentID());

		// verify event 2
		String expectedEventData2 =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"id\": \"29068398647607325310376254630528178721\"," +
			"      \"namespace\": {" +
			"        \"code\": \"ECID\"" +
			"      }" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"identity:persist\"" +
			"}";
		JSONAsserts.assertEquals(expectedEventData2, dispatchEvents.get(1).getEventData());
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

		// The state:store handle carries no eventIndex and is a global (session-scoped) handle, so in a
		// multi-event request it is broadcast rather than attributed to a specific event: no
		// requestEventId in the data and a null parent id.
		String expectedEventData1 =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"key\": \"s_ecid\"," +
			"      \"maxAge\": 15552000," +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\"" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"state:store\"" +
			"}";
		JSONAsserts.assertEquals(expectedEventData1, dispatchEvents.get(0).getEventData());
		assertNull(dispatchEvents.get(0).getParentID());

		// verify event 2
		String expectedEventData2 =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"id\": \"123612123812381\"" +
			"    }" +
			"  ]," +
			"  \"requestEventId\": \"" +
			event2.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"pairedeventexample\"" +
			"}";
		JSONAsserts.assertEquals(expectedEventData2, dispatchEvents.get(1).getEventData());
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

		String expected =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"id\": \"123612123812381\"" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"pairedeventexample\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

		String expected =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"id\": \"123612123812381\"" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"pairedeventexample\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
		assertNull(dispatchEvents.get(0).getParentID());
	}

	// ---------------------------------------------------------------------------------------------
	// processResponseOnSuccess with mixed event handles, errors, warnings
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleAndError_dispatchesTwoEvents() throws InterruptedException {
		setExpectationEvent(EventType.EDGE, EventSource.RESPONSE_CONTENT, 1);
		// The no-eventIndex error fans out to both waiting events, so two error events are dispatched.
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 2);
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

		// The state:store handle carries no eventIndex and is a global (session-scoped) handle, so in a
		// multi-event request it is broadcast rather than attributed to a specific event: no
		// requestEventId in the data and a null parent id.
		String expectedEventData1 =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"key\": \"s_ecid\"," +
			"      \"maxAge\": 15552000," +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\"" +
			"    }" +
			"  ]," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"state:store\"" +
			"}";

		JSONAsserts.assertEquals(expectedEventData1, dispatchEvents.get(0).getEventData());
		assertNull(dispatchEvents.get(0).getParentID());

		// The error carries no eventIndex — a root-level error means the whole batch failed, so it is
		// dispatched once per waiting event (each caller learns their event failed).
		List<Event> dispatchErrorEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT);
		assertEquals(2, dispatchErrorEvents.size());

		String expectedErrorForEvent1 =
			"{" +
			"  \"requestEventId\": \"" +
			event1.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"status\": 2003," +
			"  \"title\": \"Failed to process personalization event\"," +
			"  \"type\": \"personalization\"" +
			"}";
		JSONAsserts.assertEquals(expectedErrorForEvent1, dispatchErrorEvents.get(0).getEventData());
		assertEquals(event1.getUniqueIdentifier(), dispatchErrorEvents.get(0).getParentID());

		String expectedErrorForEvent2 =
			"{" +
			"  \"requestEventId\": \"" +
			event2.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"status\": 2003," +
			"  \"title\": \"Failed to process personalization event\"," +
			"  \"type\": \"personalization\"" +
			"}";
		JSONAsserts.assertEquals(expectedErrorForEvent2, dispatchErrorEvents.get(1).getEventData());
		assertEquals(event2.getUniqueIdentifier(), dispatchErrorEvents.get(1).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_singleResponse_higherIndexHandle_lowerIndexError_deliversOnErrorForLowerEvent()
		throws InterruptedException {
		// Reviewer-reported ordering bug, end-to-end: in ONE success response a handle at eventIndex 2
		// must not complete event 0 before event 0's error (eventIndex 0) is recorded, otherwise
		// event 0's onError is dropped (and the error leaks). Merged index-ordered processing records
		// the error before the completion boundary advances past index 0, so onError fires.
		setExpectationEvent(EventType.EDGE, EventSource.ERROR_RESPONSE_CONTENT, 1);

		final String requestId = "123";
		final Event ev0 = new Event.Builder("ev0", "eventType", "eventSource").build();
		final Event ev1 = new Event.Builder("ev1", "eventType", "eventSource").build();
		final Event ev2 = new Event.Builder("ev2", "eventType", "eventSource").build();
		networkResponseHandler.addWaitingEvents(requestId, Arrays.asList(ev0, ev1, ev2));

		final List<EdgeEventHandle> ev0Handles = new ArrayList<>();
		final List<EdgeEventError> ev0Errors = new ArrayList<>();
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				ev0.getUniqueIdentifier(),
				new EdgeCallbackWithError() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						ev0Handles.addAll(handles);
					}

					@Override
					public void onError(final List<EdgeEventError> errors) {
						ev0Errors.addAll(errors);
					}
				}
			);

		final String jsonResponse =
			"{\n" +
			"  \"handle\": [{ \"type\": \"pairedeventexample\", \"eventIndex\": 2, \"payload\": [{ \"id\": \"h2\" }] }],\n" +
			"  \"errors\": [{ \"status\": 503, \"title\": \"Service temporarily unavailable\", \"report\": { \"eventIndex\": 0 } }]\n" +
			"}";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);
		networkResponseHandler.processResponseOnComplete(requestId);

		// Primary symptom: event 0's onError fires with its error (dropped before the fix).
		assertEquals("event 0 must receive its error via onError", 1, ev0Errors.size());
		assertEquals(503, ev0Errors.get(0).getStatus());

		// Integration: the error response event is dispatched and paired to event 0.
		List<Event> dispatchErrorEvents = getDispatchedEventsWith(
			EventType.EDGE,
			EventSource.ERROR_RESPONSE_CONTENT,
			5000
		);
		assertEquals(1, dispatchErrorEvents.size());
		assertEquals(ev0.getUniqueIdentifier(), dispatchErrorEvents.get(0).getParentID());

		// The higher-index handle is paired to event 2.
		List<Event> dispatchHandleEvents = getDispatchedEventsWith(EventType.EDGE, "pairedeventexample");
		assertEquals(1, dispatchHandleEvents.size());
		assertEquals(ev2.getUniqueIdentifier(), dispatchHandleEvents.get(0).getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_singleResponse_sameEventHandleAndError_deliversBothCallbacks()
		throws InterruptedException {
		// A single 200 response where event 0 carries BOTH a handle and an error (eventIndex 0), plus a
		// higher-index handle (2) that advances the completion boundary. Event 0 must receive its handle
		// via onComplete AND its error via onError.
		final String requestId = "123";
		final Event ev0 = new Event.Builder("ev0", "eventType", "eventSource").build();
		final Event ev1 = new Event.Builder("ev1", "eventType", "eventSource").build();
		final Event ev2 = new Event.Builder("ev2", "eventType", "eventSource").build();
		networkResponseHandler.addWaitingEvents(requestId, Arrays.asList(ev0, ev1, ev2));

		final List<EdgeEventHandle> ev0Handles = new ArrayList<>();
		final List<EdgeEventError> ev0Errors = new ArrayList<>();
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				ev0.getUniqueIdentifier(),
				new EdgeCallbackWithError() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						ev0Handles.addAll(handles);
					}

					@Override
					public void onError(final List<EdgeEventError> errors) {
						ev0Errors.addAll(errors);
					}
				}
			);

		final String jsonResponse =
			"{\n" +
			"  \"handle\": [\n" +
			"    { \"type\": \"pairedeventexample\", \"eventIndex\": 0, \"payload\": [{ \"id\": \"h0\" }] },\n" +
			"    { \"type\": \"pairedeventexample\", \"eventIndex\": 2, \"payload\": [{ \"id\": \"h2\" }] }\n" +
			"  ],\n" +
			"  \"errors\": [{ \"status\": 503, \"title\": \"err0\", \"report\": { \"eventIndex\": 0 } }]\n" +
			"}";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);
		networkResponseHandler.processResponseOnComplete(requestId);

		assertEquals("event 0 must receive its handle via onComplete", 1, ev0Handles.size());
		assertEquals("event 0 must receive its error via onError", 1, ev0Errors.size());
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

		// Merged index-ordered processing dispatches ascending by eventIndex: the warning at eventIndex 0
		// (event1) is dispatched before the error at eventIndex 1 (event2).
		String expectedWarningForEvent1 =
			"{" +
			"  \"report\": {" +
			"    \"cause\": {" +
			"      \"code\": 202," +
			"      \"message\": \"Some Informative stuff here\"" +
			"    }" +
			"  }," +
			"  \"requestEventId\": \"" +
			event1.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"status\": 98," +
			"  \"title\": \"Some Informative stuff here\"," +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-200\"" +
			"}";
		JSONAsserts.assertEquals(expectedWarningForEvent1, dispatchEvents.get(0).getEventData());
		assertEquals(event1.getUniqueIdentifier(), dispatchEvents.get(0).getParentID());

		String expectedErrorForEvent2 =
			"{" +
			"  \"requestEventId\": \"" +
			event2.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"status\": 2003," +
			"  \"title\": \"Failed to process personalization event\"" +
			"}";
		JSONAsserts.assertEquals(expectedErrorForEvent2, dispatchEvents.get(1).getEventData());
		assertEquals(event2.getUniqueIdentifier(), dispatchEvents.get(1).getParentID());
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

		String expected =
			"{" +
			"  \"payload\": [" +
			"    {" +
			"      \"hint\": \"or2\"," +
			"      \"scope\": \"EdgeNetwork\"," +
			"      \"ttlSeconds\": 1800" +
			"    }," +
			"    {" +
			"      \"hint\": \"edge34\"," +
			"      \"scope\": \"Target\"," +
			"      \"ttlSeconds\": 600" +
			"    }" +
			"  ]," +
			"  \"requestEventId\": \"" +
			event1.getUniqueIdentifier() +
			"\"," +
			"  \"requestId\": \"123\"," +
			"  \"type\": \"locationHint:result\"" +
			"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
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

	// ---------------------------------------------------------------------------------------------
	// processResponseOnComplete
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnComplete_whenNoEventRequestsCompletion_thenNoEventDispatched()
		throws InterruptedException {
		networkResponseHandler.addWaitingEvents(
			"123",
			new ArrayList<Event>() {
				{
					add(event1);
				}
			}
		);
		networkResponseHandler.processResponseOnComplete("123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.CONTENT_COMPLETE);
		assertEquals(0, dispatchEvents.size());
	}

	@Test
	public void testProcessResponseOnComplete_whenEventRequestsCompletion_thenDispatchCompleteEvent()
		throws InterruptedException {
		networkResponseHandler.addWaitingEvents(
			"123",
			new ArrayList<Event>() {
				{
					add(eventSendComplete);
				}
			}
		);
		networkResponseHandler.processResponseOnComplete("123");
		List<Event> dispatchEvents = getDispatchedEventsWith(EventType.EDGE, EventSource.CONTENT_COMPLETE);
		assertEquals(1, dispatchEvents.size());

		String expected = "{\"requestId\": \"123\"}";
		JSONAsserts.assertEquals(expected, dispatchEvents.get(0).getEventData());
	}
}
