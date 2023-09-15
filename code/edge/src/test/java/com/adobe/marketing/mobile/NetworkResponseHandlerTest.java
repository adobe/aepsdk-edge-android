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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NetworkResponseHandlerTest {

	private static final String EVENT_TYPE_EDGE = "com.adobe.eventType.edge";
	private static final String EVENT_SOURCE_EXTENSION_RESPONSE_CONTENT = "com.adobe.eventSource.responseContent";
	private static final String EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT =
		"com.adobe.eventSource.errorResponseContent";
	private static final String EVENT_SOURCE_CONTENT_COMPLETE = "com.adobe.eventSource.contentComplete";
	private static final String EVENT_NAME_RESPONSE = "AEP Response Event Handle";
	private static final String EVENT_NAME_ERROR_RESPONSE = "AEP Error Response";
	private static final String EVENT_NAME_CONTENT_COMPLETE = "AEP Response Complete";
	private static final String REQUEST_ID = "requestId";
	private static final String REQUEST_EVENT_ID = "requestEventId";
	private NetworkResponseHandler networkResponseHandler;
	private String receivedSetLocationHint;
	private Integer receivedSetTtlSeconds;
	private final EdgeStateCallback edgeStateCallback = new EdgeStateCallback() {
		@Override
		public Map<String, Object> getImplementationDetails() {
			fail("NetworkResponseHandler is not expected to call getImplementationDetails.");
			return null;
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

	private static final Map<String, Object> requestSendCompletionTrueEventData = new HashMap<String, Object>() {
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

	private static final Map<String, Object> requestSendCompletionFalseEventData = new HashMap<String, Object>() {
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
						put("sendCompletion", false);
					}
				}
			);
		}
	};

	@Mock
	NamedCollection mockNamedCollection;

	MockedStatic<MobileCore> mockCore;

	@Before
	public void setup() {
		mockCore = mockStatic(MobileCore.class);
		networkResponseHandler = new NetworkResponseHandler(mockNamedCollection, edgeStateCallback);
	}

	@After
	public void tearDown() {
		mockCore.close();
	}

	@Test
	public void testProcessResponseOnError_WhenNullJsonError_doesNotHandleError() {
		final String jsonError = null;
		networkResponseHandler.processResponseOnError(jsonError, "123");

		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnError_WhenEmptyJsonError_doesNotHandleError() {
		final String jsonError = "";
		networkResponseHandler.processResponseOnError(jsonError, "123");

		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnError_WhenInvalidJsonError_doesNotHandleError() {
		final String jsonError = "{ invalid json }";
		networkResponseHandler.processResponseOnError(jsonError, "123");

		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnError_WhenFatalJsonError_dispatchesEvent() throws JSONException {
		final String jsonError =
			"{\n" +
			"      \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0104-422\",\n" +
			"      \"status\": 422,\n" +
			"      \"title\": \"Unprocessable Entity\",\n" +
			"      \"detail\": \"Invalid request (report attached). Please check your input and try again.\",\n" +
			"      \"report\": {\n" +
			"         \"errors\": [\n" +
			"             \"Allowed Adobe version is 1.0 for standard 'Adobe' at index 0\",\n" +
			"             \"Allowed IAB version is 2.0 for standard 'IAB TCF' at index 1\",\n" +
			"             \"IAB consent string value must not be empty for standard 'IAB TCF' at index 1\",\n" +
			"         ],\n" +
			"         \"requestId\": \"0f8821e5-ed1a-4301-b445-5f336fb50ee8\",\n" +
			"         \"orgId\": \"test@AdobeOrg\"\n" +
			"       }\n" +
			"    }";
		networkResponseHandler.processResponseOnError(jsonError, "123");

		// verify
		JSONObject json = new JSONObject(jsonError);
		Map<String, Object> expectedEventData = JSONUtils.toMap(json);
		expectedEventData.put(REQUEST_ID, "123");
		assertResponseErrorEventWithData(expectedEventData);
	}

	@Test
	public void testProcessResponseOnError_WhenOneEventJsonError_dispatchesEvent() {
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\"\n" +
			"        }\n" +
			"      ]\n" +
			"    }";

		// test
		networkResponseHandler.processResponseOnError(jsonError, "123");

		// verify
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-503");
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases"
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertResponseErrorEventWithData(expectedEventData);
	}

	@Test
	public void testProcessResponseOnError_WhenValidEventIndex_dispatchesPairedEvent_doesNotEncodeEmptyReport() {
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 0\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }";

		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);
		networkResponseHandler.processResponseOnError(jsonError, requestId);

		// verify
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-503");
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later."
		);
		expectedEventData.put(REQUEST_ID, "123");
		expectedEventData.put(REQUEST_EVENT_ID, requestEvent1.getUniqueIdentifier());
		assertResponseErrorEventWithData(expectedEventData);
	}

	@Test
	public void testProcessResponseOnSuccess_WhenWarning_dispatchesPairedEvent_doesNotEncodeEmptyReport() {
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"warnings\": [\n" +
			"        {\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-200\",\n" +
			"          \"status\": 202,\n" +
			"          \"title\": \"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\",\n" +
			"          \"report\": {\n" +
			"              \"eventIndex\": 10, \n" +
			"          	}\n" +
			"        }\n" +
			"       ]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(1));

		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(1, returnedEvents.size());
		Map<String, Object> expectedEventData = new HashMap<>();
		Event returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0204-200");
		expectedEventData.put("status", 202);
		expectedEventData.put(
			"title",
			"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request."
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	@Test
	public void testProcessResponseOnError_WhenValidEventIndex_dispatchesPairedEvent() {
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 0,\n" +
			"            \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"            \"orgId\": \"1234@AdobeOrg\",\n" +
			"            \"errors\": [\"error1\",\"error2\"]\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }";

		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);
		networkResponseHandler.processResponseOnError(jsonError, requestId);

		// verify
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-503");
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later."
		);
		expectedEventData.put(
			"report",
			new HashMap<String, Object>() {
				{
					put("requestId", "d81c93e5-7558-4996-a93c-489d550748b8");
					put("orgId", "1234@AdobeOrg");
					put("errors", Arrays.asList("error1", "error2"));
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		expectedEventData.put(REQUEST_EVENT_ID, requestEvent1.getUniqueIdentifier());
		assertResponseErrorEventWithData(expectedEventData);
	}

	@Test
	public void testProcessResponseOnError_WhenUnknownEventIndex_doesNotCrash() {
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 10\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }";

		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);
		networkResponseHandler.processResponseOnError(jsonError, requestId);

		// verify
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-503");
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later."
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertResponseErrorEventWithData(expectedEventData);
	}

	@Test
	public void testProcessResponseOnError_WhenUnknownRequestId_doesNotCrash() {
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 0\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }";

		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);
		networkResponseHandler.processResponseOnError(jsonError, "567");

		// verify
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-503");
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later."
		);
		expectedEventData.put(REQUEST_ID, "567");
		assertResponseErrorEventWithData(expectedEventData);
	}

	@Test
	public void testProcessResponseOnError_WhenTwoEventJsonError_dispatchesTwoEvents() {
		final String jsonError =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\"\n" +
			"        },\n" +
			"        {\n" +
			"          \"status\": 502,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-502\",\n" +
			"          \"title\": \"The server encountered a temporary error and could not complete your request\"\n" +
			"        }\n" +
			"      ]\n" +
			"    }";
		networkResponseHandler.processResponseOnError(jsonError, "123");

		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(2));

		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(2, returnedEvents.size());
		Event returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);
		assertEquals(EVENT_TYPE_EDGE, returnedEvent.getType());
		assertEquals(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT, returnedEvent.getSource());
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-503");
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later."
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertEquals(EVENT_TYPE_EDGE, returnedEvent.getType());
		assertEquals(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT, returnedEvent.getSource());
		expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-502");
		expectedEventData.put("status", 502);
		expectedEventData.put("title", "The server encountered a temporary error and could not complete your request");
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenNullJsonResponse_doesNotDispatchEvent() {
		final String jsonResponse = null;
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEmptyStringResponse_doesNotDispatchEvent() {
		final String jsonResponse = "";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEmptyJsonResponse_doesNotDispatchEvent() {
		final String jsonResponse = "{}";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenInvalidJsonResponse_doesNotDispatchEvent() {
		final String jsonResponse = "{ invalid json }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenOneEventHandle_dispatchesEvent() {
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
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		final Map<String, Object> expectedPayload = new HashMap<>();
		expectedPayload.put("key", "s_ecid");
		expectedPayload.put("value", "MCMID|29068398647607325310376254630528178721");
		expectedPayload.put("maxAge", 15552000);
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "state:store");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertResponseContentEventWithData(expectedEventData, "state:store");
	}

	@Test
	public void testProcessResponseOnSuccess_beforeReset_doesNotSavePayloads() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		networkResponseHandler.addWaitingEvent("123", event);
		final long resetTime = event.getTimestamp() + 10; // reset received after event was queued, ignore its state store
		networkResponseHandler.setLastResetDate(resetTime);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"123\",\n" +
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
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		verify(mockNamedCollection, never()).setMap(eq(EdgeConstants.DataStoreKeys.STORE_PAYLOADS), any(Map.class));
	}

	@Test
	public void testProcessResponseOnSuccess_afterReset_doesSavePayloads() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		networkResponseHandler.addWaitingEvent("123", event);
		final long resetTime = event.getTimestamp() - 10; // reset received before event was queued, save its state store
		networkResponseHandler.setLastResetDate(resetTime);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"123\",\n" +
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
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		// expect 2 invocations based on payload manager implementation
		verify(mockNamedCollection, times(2)).setMap(eq(EdgeConstants.DataStoreKeys.STORE_PAYLOADS), any(Map.class));
	}

	@Test
	public void testProcessResponseOnSuccess_beforePersistedReset_doesNotSavePayloads() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		final long resetTime = event.getTimestamp() + 10; // reset received after event was queued, ignore its state store

		when(mockNamedCollection.getLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, 0)).thenReturn(resetTime);
		networkResponseHandler = new NetworkResponseHandler(mockNamedCollection, null); // reset response handler to load persisted reset time
		networkResponseHandler.addWaitingEvent("123", event);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"123\",\n" +
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
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		verify(mockNamedCollection, never()).setMap(eq(EdgeConstants.DataStoreKeys.STORE_PAYLOADS), any(Map.class));
	}

	@Test
	public void testProcessResponseOnSuccess_afterPersistedReset_doesSavePayloads() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		final long resetTime = event.getTimestamp() - 10; // reset received before event was queued, save its state store
		mockNamedCollection.setLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, resetTime);

		networkResponseHandler = new NetworkResponseHandler(mockNamedCollection, null); // reset response handler to load persisted reset time
		networkResponseHandler.addWaitingEvent("123", event);

		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"123\",\n" +
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
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		// expect 2 invocations based on payload manager implementation
		verify(mockNamedCollection, times(2)).setMap(eq(EdgeConstants.DataStoreKeys.STORE_PAYLOADS), any(Map.class));
	}

	@Test
	public void testProcessResponseOnSuccess_WhenTwoEventHandles_dispatchesTwoEvents() {
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

		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(2));

		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(2, returnedEvents.size());
		Event returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue("state:store".equalsIgnoreCase(returnedEvent.getSource()));
		final Map<String, Object> expectedPayload = new HashMap<>();
		expectedPayload.put("key", "s_ecid");
		expectedPayload.put("value", "MCMID|29068398647607325310376254630528178721");
		expectedPayload.put("maxAge", 15552000);
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "state:store");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue("identity:persist".equalsIgnoreCase(returnedEvent.getSource()));
		expectedPayload.clear();
		expectedPayload.put("id", "29068398647607325310376254630528178721");
		expectedPayload.put(
			"namespace",
			new HashMap<String, String>() {
				{
					put("code", "ECID");
				}
			}
		);
		expectedEventData = new HashMap<>();
		expectedEventData.put("type", "identity:persist");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleWithEventIndex_dispatchesEventWithRequestEventId() {
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
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(2));

		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(2, returnedEvents.size());
		Event returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue("state:store".equalsIgnoreCase(returnedEvent.getSource()));
		final Map<String, Object> expectedPayload = new HashMap<>();
		expectedPayload.put("key", "s_ecid");
		expectedPayload.put("value", "MCMID|29068398647607325310376254630528178721");
		expectedPayload.put("maxAge", 15552000);
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "state:store");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		expectedEventData.put(REQUEST_EVENT_ID, requestEvent1.getUniqueIdentifier());
		assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue("pairedeventexample".equalsIgnoreCase(returnedEvent.getSource()));
		expectedPayload.clear();
		expectedPayload.put("id", "123612123812381");
		expectedEventData = new HashMap<>();
		expectedEventData.put("type", "pairedeventexample");
		expectedEventData.put(REQUEST_EVENT_ID, requestEvent2.getUniqueIdentifier());
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		expectedEventData.put(REQUEST_EVENT_ID, requestEvent2.getUniqueIdentifier());
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleWithUnknownEventIndex_dispatchesUnpairedEvent() {
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
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);

		// verify
		final Map<String, Object> expectedPayload = new HashMap<>();
		Map<String, Object> expectedEventData;
		expectedPayload.put("id", "123612123812381");
		expectedEventData = new HashMap<>();
		expectedEventData.put("type", "pairedeventexample");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, requestId);
		assertResponseContentEventWithData(expectedEventData, "pairedeventexample");
	}

	@Test
	public void testProcessResponseOnSuccess_WhenUnknownRequestId_doesNotCrash() {
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
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			"567",
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);
		networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);

		//verify
		final Map<String, Object> expectedPayload = new HashMap<>();
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedPayload.put("id", "123612123812381");
		expectedEventData = new HashMap<>();
		expectedEventData.put("type", "pairedeventexample");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, requestId);
		assertResponseContentEventWithData(expectedEventData, "pairedeventexample");
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleAndError_dispatchesTwoEvents() {
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
			"          \"status\": 503,\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\"\n" +
			"        }\n" +
			"       ]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(2));

		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(2, returnedEvents.size());
		Event returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue("state:store".equalsIgnoreCase(returnedEvent.getSource()));
		final Map<String, Object> expectedPayload = new HashMap<>();
		expectedPayload.put("key", "s_ecid");
		expectedPayload.put("value", "MCMID|29068398647607325310376254630528178721");
		expectedPayload.put("maxAge", 15552000);
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "state:store");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));
		expectedEventData = new HashMap<>();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0201-503");
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later."
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenErrorAndWarning_dispatchesTwoEvents() {
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 2 \n" +
			"          }\n" +
			"        }\n" +
			"       ],\n" +
			"      \"warnings\": [\n" +
			"        {\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-200\",\n" +
			"          \"status\": 202,\n" +
			"          \"title\": \"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\",\n" +
			"          \"report\": {\n" +
			"              \"eventIndex\": 10, \n" +
			"              \"cause\": {\n" +
			"                  \"message\": \"Cannot read related customer for device id: ...\",\n" +
			"                  \"code\": 202\n" +
			"          	    }\n" +
			"          	}\n" +
			"        }\n" +
			"       ]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(2));

		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(2, returnedEvents.size());
		Event returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));

		returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("status", 503);
		expectedEventData.put(
			"title",
			"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later."
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));
		expectedEventData.clear();
		expectedEventData.put("type", "https://ns.adobe.com/aep/errors/EXEG-0204-200");
		expectedEventData.put("status", 202);
		expectedEventData.put(
			"title",
			"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request."
		);
		HashMap<String, Object> expectedReport = new HashMap<>();
		HashMap<String, Object> expectedCause = new HashMap<>();
		expectedCause.put("message", "Cannot read related customer for device id: ...");
		expectedCause.put("code", 202);
		expectedReport.put("cause", expectedCause);
		expectedEventData.put("report", expectedReport);
		expectedEventData.put(REQUEST_ID, "123");
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenOneEventHandle_emptyEventHandleType_dispatchesEvent() {
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

		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(
						new HashMap<String, Object>() {
							{
								put("key", "s_ecid");
								put("value", "MCMID|29068398647607325310376254630528178721");
								put("maxAge", 15552000);
							}
						}
					);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertResponseContentEventWithData(expectedEventData, EVENT_SOURCE_EXTENSION_RESPONSE_CONTENT);
	}

	@Test
	public void testProcessResponseOnSuccess_WhenOneEventHandle_nullEventHandleType_dispatchesEvent() {
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

		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(
						new HashMap<String, Object>() {
							{
								put("key", "s_ecid");
								put("value", "MCMID|29068398647607325310376254630528178721");
								put("maxAge", 15552000);
							}
						}
					);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "123");
		assertResponseContentEventWithData(expectedEventData, EVENT_SOURCE_EXTENSION_RESPONSE_CONTENT);
	}

	@Test
	public void testProcessResponseOnSuccess_WhenEventHandleWithEventIndexAndWithoutEventIndex_invokesCallback()
		throws Exception {
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
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);

		// Expected onComplete callback to be invoked for both events
		final CountDownLatch latch = new CountDownLatch(2);
		final List<EdgeEventHandle> receivedData1 = new ArrayList<EdgeEventHandle>();
		final List<EdgeEventHandle> receivedData2 = new ArrayList<EdgeEventHandle>();

		// Expect callback not to be called as no response handle contains eventIndex 0
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				requestEvent1.getUniqueIdentifier(),
				handles -> {
					receivedData1.addAll(handles);
					latch.countDown();
				}
			);

		// Expect callback to be called for response handle with eventIndex 1
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				requestEvent2.getUniqueIdentifier(),
				handles -> {
					receivedData2.addAll(handles);
					latch.countDown();
				}
			);

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");
		CompletionCallbacksManager.getInstance().unregisterCallback(requestEvent1.getUniqueIdentifier());
		CompletionCallbacksManager.getInstance().unregisterCallback(requestEvent2.getUniqueIdentifier());

		final Map<String, Object> expectedPayload1 = new HashMap<>();
		expectedPayload1.put("key", "s_ecid");
		expectedPayload1.put("value", "MCMID|29068398647607325310376254630528178721");
		expectedPayload1.put("maxAge", 15552000);
		Map<String, Object> expectedEventData1 = new HashMap<>();
		expectedEventData1.put("type", "state:store");
		expectedEventData1.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload1);
				}
			}
		);
		latch.await(100, TimeUnit.MILLISECONDS);

		final Map<String, Object> expectedPayload2 = new HashMap<>();
		expectedPayload2.put("id", "123612123812381");
		Map<String, Object> expectedEventData2 = new HashMap<>();
		expectedEventData2.put("type", "pairedeventexample");
		expectedEventData2.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload2);
				}
			}
		);
		latch.await(100, TimeUnit.MILLISECONDS);
		assertEquals(1, receivedData1.size());
		assertEquals(expectedEventData1, receivedData1.get(0).toMap());
		assertEquals(1, receivedData2.size());
		assertEquals(expectedEventData2, receivedData2.get(0).toMap());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenHandleAndErrorAndWarning_invokeResponseCallbackForHandle()
		throws Exception {
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
			"      \"errors\": [" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 1\n" +
			"          }\n" +
			"        }\n" +
			"       ],\n" +
			"      \"warnings\": [" +
			"        {\n" +
			"          \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-20\",\n" +
			"          \"title\": \"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 2 \n" +
			"          }\n" +
			"        }\n" +
			"       ]\n" +
			"    }";

		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();
		final Event requestEvent3 = new Event.Builder("test3", "testType", "testSource").build();

		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
					add(requestEvent3);
				}
			}
		);

		// Expected onComplete callback to be invoked for all 3 events
		final CountDownLatch latch = new CountDownLatch(3);
		final List<EdgeEventHandle> receivedData1 = new ArrayList<EdgeEventHandle>();
		final List<EdgeEventHandle> receivedData2 = new ArrayList<EdgeEventHandle>();
		final List<EdgeEventHandle> receivedData3 = new ArrayList<EdgeEventHandle>();

		// Expect callback to be called for response handle with eventIndex 0
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				requestEvent1.getUniqueIdentifier(),
				handles -> {
					receivedData1.addAll(handles);
					latch.countDown();
				}
			);
		// Expect callback not to be called for response errors
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				requestEvent2.getUniqueIdentifier(),
				handles -> {
					receivedData2.addAll(handles);
					latch.countDown();
				}
			);
		// Expect callback not to be called for response warnings
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				requestEvent3.getUniqueIdentifier(),
				handles -> {
					receivedData3.addAll(handles);
					latch.countDown();
				}
			);

		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");
		CompletionCallbacksManager.getInstance().unregisterCallback(requestEvent1.getUniqueIdentifier());
		CompletionCallbacksManager.getInstance().unregisterCallback(requestEvent2.getUniqueIdentifier());
		CompletionCallbacksManager.getInstance().unregisterCallback(requestEvent3.getUniqueIdentifier());

		final Map<String, Object> expectedPayload = new HashMap<>();
		expectedPayload.put("id", "123612123812381");
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "pairedeventexample");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload);
				}
			}
		);

		latch.await(100, TimeUnit.MILLISECONDS);
		assertEquals(1, receivedData1.size());
		assertEquals(expectedEventData, receivedData1.get(0).toMap());
		assertTrue(receivedData2.isEmpty());
		assertTrue(receivedData3.isEmpty());
	}

	@Test
	public void testProcessResponseOnSuccess_WhenErrorAndWarning_logsTheTwoEvents() {
		MockedStatic<Log> mockLogService = mockStatic(Log.class);
		final String jsonResponse =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [" +
			"        {\n" +
			"          \"status\": 503,\n" +
			"          \"title\": \"Failed to process personalization event\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 2\n" +
			"          }\n" +
			"        }\n" +
			"       ],\n" +
			"      \"warnings\": [" +
			"        {\n" +
			"          \"status\": 202,\n" +
			"          \"title\": \"Some Informative stuff here\",\n" +
			"          \"report\": {\n" +
			"            \"eventIndex\": 10\n" +
			"          }\n" +
			"        }\n" +
			"       ]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		List<String> expectedErrorLogs = new ArrayList<>();
		expectedErrorLogs.add(
			"Received event error for request id (123), error details:\n " +
			"{\n" +
			"  \"report\": {\"eventIndex\": 2},\n" +
			"  \"title\": \"Failed to process personalization event\",\n" +
			"  \"status\": 503\n" +
			"}"
		);
		List<String> expectedWarningLogs = new ArrayList<>();
		expectedWarningLogs.add(
			"Received event error for request id (123), error details:\n " +
			"{\n" +
			"  \"report\": {\"eventIndex\": 10},\n" +
			"  \"title\": \"Some Informative stuff here\",\n" +
			"  \"status\": 202\n" +
			"}"
		);
		assertErrorLogs(mockLogService, expectedErrorLogs);
		assertWarningLogs(mockLogService, expectedWarningLogs);
		mockLogService.close();
	}

	// ---------------------------------------------------------------------------------------------
	// processResponseOnSuccess with locationHint:result
	// ---------------------------------------------------------------------------------------------

	@Test
	public void testProcessResponseOnSuccess_WhenLocationHintResultEventHandle_dispatchesEvent() {
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
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "d81c93e5-7558-4996-a93c-489d550748b8");

		// verify
		final Map<String, Object> expectedPayload1 = new HashMap<>();
		expectedPayload1.put("scope", "EdgeNetwork");
		expectedPayload1.put("hint", "or2");
		expectedPayload1.put("ttlSeconds", 1800);
		final Map<String, Object> expectedPayload2 = new HashMap<>();
		expectedPayload2.put("scope", "Target");
		expectedPayload2.put("hint", "edge34");
		expectedPayload2.put("ttlSeconds", 600);
		Map<String, Object> expectedEventData = new HashMap<>();
		expectedEventData.put("type", "locationHint:result");
		expectedEventData.put(
			"payload",
			new ArrayList<Object>() {
				{
					add(expectedPayload1);
					add(expectedPayload2);
				}
			}
		);
		expectedEventData.put(REQUEST_ID, "d81c93e5-7558-4996-a93c-489d550748b8");
		assertResponseContentEventWithData(expectedEventData, "locationHint:result");
	}

	@Test
	public void testProcessResponseOnSuccess_beforeReset_doesNotSetLocationHint() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		networkResponseHandler.addWaitingEvent("123", event);
		final long resetTime = event.getTimestamp() + 10;
		networkResponseHandler.setLastResetDate(resetTime);

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
			"        }]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		assertNull(receivedSetLocationHint);
		assertNull(receivedSetTtlSeconds);
	}

	@Test
	public void testProcessResponseOnSuccess_afterReset_setsLocationHint() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		networkResponseHandler.addWaitingEvent("123", event);
		final long resetTime = event.getTimestamp() - 10;
		networkResponseHandler.setLastResetDate(resetTime);

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
			"        }]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		assertEquals("or2", receivedSetLocationHint);
		assertEquals(1800, receivedSetTtlSeconds, 2);
	}

	@Test
	public void testProcessResponseOnSuccess_beforePersistedReset_doesNotSetLocationHint() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		final long resetTime = event.getTimestamp() + 10;
		when(mockNamedCollection.getLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, 0)).thenReturn(resetTime);
		// reset response handler to load persisted reset time
		networkResponseHandler = new NetworkResponseHandler(mockNamedCollection, edgeStateCallback);
		networkResponseHandler.addWaitingEvent("123", event);

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
			"        }]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		assertNull(receivedSetLocationHint);
		assertNull(receivedSetTtlSeconds);
	}

	@Test
	public void testProcessResponseOnSuccess_afterPersistedReset_setsLocationHint() {
		final Event event = new Event.Builder("test Event", EventType.EDGE, EventSource.REQUEST_CONTENT).build();
		final long resetTime = event.getTimestamp() - 10;

		mockNamedCollection.setLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, resetTime);
		// reset response handler to load persisted reset time
		networkResponseHandler = new NetworkResponseHandler(mockNamedCollection, edgeStateCallback);
		networkResponseHandler.addWaitingEvent("123", event);

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
			"        }]\n" +
			"    }";
		networkResponseHandler.processResponseOnSuccess(jsonResponse, "123");

		// verify
		assertEquals("or2", receivedSetLocationHint);
		assertEquals(1800, receivedSetTtlSeconds, 2);
	}

	@Test
	public void testProcessResponseOnComplete_ifCompletionEventNotRequested_doesNotDispatchEvent() {
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource").build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);

		networkResponseHandler.processResponseOnComplete(requestId);
		mockCore.verify(() -> MobileCore.dispatchEvent(any(Event.class)), never());
	}

	@Test
	public void testProcessResponseOnComplete_ifCompletionEventRequested_dispatchesEvent() {
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource")
			.setEventData(requestSendCompletionTrueEventData)
			.build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource").build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);

		networkResponseHandler.processResponseOnComplete(requestId);

		final Map<String, Object> expectedEventData = new HashMap<String, Object>() {
			{
				put("requestId", requestId);
			}
		};

		assertResponseCompleteEventWithData(
			new Map[] { expectedEventData },
			new String[] { requestEvent1.getUniqueIdentifier() }
		);
	}

	@Test
	public void testProcessResponseOnComplete_ifMultipleCompletionEventRequested_dispatchesMultipleEvents() {
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource")
			.setEventData(requestSendCompletionTrueEventData)
			.build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource")
			.setEventData(requestSendCompletionTrueEventData)
			.build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);

		networkResponseHandler.processResponseOnComplete(requestId);

		final Map<String, Object> expectedEventData = new HashMap<String, Object>() {
			{
				put("requestId", requestId);
			}
		};

		assertResponseCompleteEventWithData(
			new Map[] { expectedEventData, expectedEventData },
			new String[] { requestEvent1.getUniqueIdentifier(), requestEvent2.getUniqueIdentifier() }
		);
	}

	@Test
	public void testProcessResponseOnComplete_ifCompletionEventRequestFalse_doesNotDispatchEvent() {
		final String requestId = "123";
		final Event requestEvent1 = new Event.Builder("test1", "testType", "testSource")
			.setEventData(requestSendCompletionFalseEventData)
			.build();
		final Event requestEvent2 = new Event.Builder("test2", "testType", "testSource")
			.setEventData(requestSendCompletionTrueEventData)
			.build();

		// test
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(requestEvent1);
					add(requestEvent2);
				}
			}
		);

		networkResponseHandler.processResponseOnComplete(requestId);

		final Map<String, Object> expectedEventData = new HashMap<String, Object>() {
			{
				put("requestId", requestId);
			}
		};

		// Assert complete event only dispatched for requestEvent2
		assertResponseCompleteEventWithData(
			new Map[] { expectedEventData },
			new String[] { requestEvent2.getUniqueIdentifier() }
		);
	}

	@Test
	public void testAddWaitingEvents_addsNewList_happy() {
		final String requestId = "test";
		List<Event> eventsList = new ArrayList<>();
		Event e1 = new Event.Builder("e1", "eventType", "eventSource").build();
		Event e2 = new Event.Builder("e2", "eventType", "eventSource").build();
		eventsList.add(e1);
		eventsList.add(e2);
		networkResponseHandler.addWaitingEvents(requestId, eventsList);

		List<String> result = networkResponseHandler.getWaitingEvents(requestId);
		assertEquals(2, result.size());
		assertEquals(e1.getUniqueIdentifier(), result.get(0));
		assertEquals(e2.getUniqueIdentifier(), result.get(1));
	}

	@Test
	public void testAddWaitingEvents_skips_whenNullRequestId() {
		List<Event> eventsList = new ArrayList<>();
		Event e1 = new Event.Builder("e1", "eventType", "eventSource").build();
		Event e2 = new Event.Builder("e2", "eventType", "eventSource").build();
		eventsList.add(e1);
		eventsList.add(e2);
		networkResponseHandler.addWaitingEvents(null, eventsList);

		List<String> result = networkResponseHandler.getWaitingEvents(null);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testAddWaitingEvents_skips_whenEmptyRequestId() {
		List<Event> eventsList = new ArrayList<>();
		Event e1 = new Event.Builder("e1", "eventType", "eventSource").build();
		Event e2 = new Event.Builder("e2", "eventType", "eventSource").build();
		eventsList.add(e1);
		eventsList.add(e2);
		networkResponseHandler.addWaitingEvents("", eventsList);

		List<String> result = networkResponseHandler.getWaitingEvents("");
		assertTrue(result.isEmpty());
	}

	@Test
	public void testAddWaitingEvents_skips_whenNullList() {
		final String requestId = "test";
		networkResponseHandler.addWaitingEvents(requestId, null);

		List<String> result = networkResponseHandler.getWaitingEvents(requestId);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testAddWaitingEvents_skips_whenEmptyList() {
		final String requestId = "test";
		networkResponseHandler.addWaitingEvents(requestId, new ArrayList<>());

		List<String> result = networkResponseHandler.getWaitingEvents(requestId);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testAddWaitingEvents_overrides_existingRequestId() {
		final String requestId = "test";
		List<Event> eventsList = new ArrayList<>();
		Event e11 = new Event.Builder("e11", "eventType", "eventSource").build();
		Event e12 = new Event.Builder("e12", "eventType", "eventSource").build();
		eventsList.add(e11);
		eventsList.add(e12);

		List<Event> eventsList2 = new ArrayList<>();
		Event e21 = new Event.Builder("e21", "eventType", "eventSource").build();
		eventsList2.add(e21);

		// test
		networkResponseHandler.addWaitingEvents(requestId, eventsList);
		networkResponseHandler.addWaitingEvents(requestId, eventsList2);

		// verify
		List<String> result = networkResponseHandler.getWaitingEvents(requestId);
		assertEquals(1, result.size());
		assertEquals(e21.getUniqueIdentifier(), result.get(0));
	}

	@Test
	public void testRemoveWaitingEvents_removesByRequestId() {
		final String requestId1 = "test1";
		final String requestId2 = "test2";
		List<Event> eventsList = new ArrayList<>();
		Event e11 = new Event.Builder("e11", "eventType", "eventSource").build();
		Event e12 = new Event.Builder("e12", "eventType", "eventSource").build();
		eventsList.add(e11);
		eventsList.add(e12);

		List<Event> eventsList2 = new ArrayList<>();
		Event e21 = new Event.Builder("e21", "eventType", "eventSource").build();
		eventsList2.add(e21);

		// test
		networkResponseHandler.addWaitingEvents(requestId1, eventsList);
		networkResponseHandler.addWaitingEvents(requestId2, eventsList2);
		assertEquals(1, networkResponseHandler.removeWaitingEvents(requestId2).size());

		// verify
		List<String> result = networkResponseHandler.getWaitingEvents(requestId2);
		assertTrue(result.isEmpty());
		result = networkResponseHandler.getWaitingEvents(requestId1);
		assertEquals(2, result.size());
	}

	@Test
	public void testRemoveWaitingEvents_returnsNull_whenNullEmptyRequestId() {
		final String requestId1 = "test1";
		List<Event> eventsList = new ArrayList<>();
		Event e11 = new Event.Builder("e11", "eventType", "eventSource").build();
		Event e12 = new Event.Builder("e12", "eventType", "eventSource").build();
		eventsList.add(e11);
		eventsList.add(e12);

		// test
		networkResponseHandler.addWaitingEvents(requestId1, eventsList);
		assertNull(networkResponseHandler.removeWaitingEvents(null));
		assertNull(networkResponseHandler.removeWaitingEvents(""));

		// verify
		List<String> result = networkResponseHandler.getWaitingEvents(requestId1);
		assertEquals(2, result.size());
	}

	@Test
	public void testRemoveWaitingEvents_returnsNull_whenNotKnownRequestId() {
		final String requestId1 = "test1";
		final String requestId2 = "test2";
		List<Event> eventsList = new ArrayList<>();
		Event e11 = new Event.Builder("e11", "eventType", "eventSource").build();
		Event e12 = new Event.Builder("e12", "eventType", "eventSource").build();
		eventsList.add(e11);
		eventsList.add(e12);

		// test
		networkResponseHandler.addWaitingEvents(requestId1, eventsList);
		assertNull(networkResponseHandler.removeWaitingEvents(requestId2));

		// verify
		List<String> result = networkResponseHandler.getWaitingEvents(requestId1);
		assertEquals(2, result.size());
	}

	@Test
	public void testAddRemoveWaitingEvents_noConcurrencyCrash_whenCalledFromDifferentThreads()
		throws InterruptedException {
		final String requestId1 = "test1";
		final List<Event> eventsList = new ArrayList<>();
		Event e11 = new Event.Builder("e11", "eventType", "eventSource").build();
		Event e12 = new Event.Builder("e12", "eventType", "eventSource").build();
		eventsList.add(e11);
		eventsList.add(e12);
		ExecutorService threadPool = Executors.newFixedThreadPool(10);
		List<Callable<Integer>> futures = new ArrayList<>();
		final CountDownLatch latch100 = new CountDownLatch(100);

		for (int i = 0; i < 100; i++) {
			Random rand = new Random();
			final int randInt = rand.nextInt(1000);

			if (randInt % 2 == 0) {
				futures.add(() -> {
					networkResponseHandler.addWaitingEvents(requestId1, eventsList);
					latch100.countDown();
					return randInt;
				});
			} else {
				futures.add(() -> {
					networkResponseHandler.removeWaitingEvents(requestId1);
					latch100.countDown();
					return randInt;
				});
			}
		}

		// Invoke & wait for all
		threadPool.invokeAll(futures);
		assertTrue(latch100.await(1000, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testSetLastResetDate_persistsTimestamp() {
		final long lastResetDate = 1;
		networkResponseHandler.setLastResetDate(lastResetDate);

		verify(mockNamedCollection, times(1)).setLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, lastResetDate);
	}

	/**
	 * Asserts the Log.error API received the {@code expectedLogs} number and exact log messages.
	 */
	private void assertErrorLogs(final MockedStatic<Log> mockLogService, @NotNull final List<String> expectedLogs) {
		ArgumentCaptor<String> logMessageArgCaptor = ArgumentCaptor.forClass(String.class);

		mockLogService.verify(
			() -> Log.error(anyString(), anyString(), logMessageArgCaptor.capture(), any()),
			times(expectedLogs.size())
		);

		assertEqualLogMessages(expectedLogs, logMessageArgCaptor.getAllValues());
	}

	/**
	 * Asserts the Log.warning API received the {@code expectedLogs} number and exact log messages.
	 */
	private void assertWarningLogs(final MockedStatic<Log> mockLogService, @NotNull final List<String> expectedLogs) {
		ArgumentCaptor<String> logMessageArgCaptor = ArgumentCaptor.forClass(String.class);

		mockLogService.verify(
			() -> Log.warning(anyString(), anyString(), logMessageArgCaptor.capture(), any()),
			times(expectedLogs.size())
		);

		assertEqualLogMessages(expectedLogs, logMessageArgCaptor.getAllValues());
	}

	private void assertEqualLogMessages(final List<String> expectedLogs, final List<String> actualLogs) {
		if (expectedLogs == null || expectedLogs.size() == 0) {
			return;
		}

		if (actualLogs == null || actualLogs.isEmpty()) {
			fail(String.format("actualLogs were empty, expected %d entries", expectedLogs.size()));
			return;
		}

		for (String log : expectedLogs) {
			boolean found = false;

			for (String actualMessage : actualLogs) {
				if (actualMessage != null && actualMessage.contains(log)) {
					assertFalse("Log message found multiple times (" + log + "), expected to find it once", found);

					found = true;
				}
			}

			assertTrue("Log message not found (" + log + ")", found);
		}
	}

	private void assertResponseErrorEventWithData(final Map<String, Object> expectedEventData) {
		ArgumentCaptor<Event> eventArgCaptor;
		eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(1));

		Event returnedEvent = eventArgCaptor.getValue();
		assertNotNull(returnedEvent);
		assertEquals(EVENT_NAME_ERROR_RESPONSE, returnedEvent.getName());
		assertEquals(EVENT_TYPE_EDGE, returnedEvent.getType());
		assertEquals(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT, returnedEvent.getSource());
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	private void assertResponseContentEventWithData(
		final Map<String, Object> expectedEventData,
		final String eventSource
	) {
		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(1));

		Event returnedEvent = eventArgCaptor.getValue();
		assertNotNull(returnedEvent);
		assertEquals(EVENT_NAME_RESPONSE, returnedEvent.getName());
		assertEquals(EVENT_TYPE_EDGE, returnedEvent.getType());
		String expectedEventSource = StringUtils.isNullOrEmpty(eventSource)
			? EVENT_SOURCE_EXTENSION_RESPONSE_CONTENT
			: eventSource;
		assertEquals(expectedEventSource, returnedEvent.getSource());
		assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	private void assertResponseCompleteEventWithData(
		final Map<String, Object>[] expectedEventDatas,
		final String[] parentEventIds
	) {
		ArgumentCaptor<Event> eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(parentEventIds.length));

		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(parentEventIds.length, returnedEvents.size());
		for (int i = 0; i < parentEventIds.length; i++) {
			Event returnedEvent = returnedEvents.get(i);
			assertNotNull(returnedEvent);
			assertEquals(EVENT_NAME_CONTENT_COMPLETE, returnedEvent.getName());
			assertEquals(EVENT_TYPE_EDGE, returnedEvent.getType());
			assertEquals(EVENT_SOURCE_CONTENT_COMPLETE, returnedEvent.getSource());
			assertEquals(expectedEventDatas[i], returnedEvent.getEventData());
			assertEquals(parentEventIds[i], returnedEvent.getParentID());
			assertEquals(parentEventIds[i], returnedEvent.getResponseID());
		}
	}
}
