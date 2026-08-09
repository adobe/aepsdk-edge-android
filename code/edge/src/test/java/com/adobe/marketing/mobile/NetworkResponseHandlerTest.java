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
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.JSONAsserts;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\",\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"  \"requestId\": \"123\",\n" +
			"  \"requestEventId\": \"" +
			requestEvent1.getUniqueIdentifier() +
			"\"\n" +
			"}";

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

		// Verify
		List<Event> returnedEvents = eventArgCaptor.getAllValues();
		assertEquals(1, returnedEvents.size());
		Event returnedEvent = returnedEvents.get(0);
		assertNotNull(returnedEvent);

		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));

		String expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-200\",\n" +
			"  \"status\": 202,\n" +
			"  \"title\": \"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\",\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"  \"report\": {\n" +
			"    \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"    \"orgId\": \"1234@AdobeOrg\",\n" +
			"    \"errors\": [\"error1\", \"error2\"]\n" +
			"  },\n" +
			"  \"requestId\": \"123\",\n" +
			"  \"requestEventId\": \"" +
			requestEvent1.getUniqueIdentifier() +
			"\"\n" +
			"}";
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"  \"requestId\": \"567\"\n" +
			"}";
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertEquals(EVENT_TYPE_EDGE, returnedEvent.getType());
		assertEquals(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT, returnedEvent.getSource());
		expectedEventData =
			"{\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-502\",\n" +
			"  \"status\": 502,\n" +
			"  \"title\": \"The server encountered a temporary error and could not complete your request\",\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"state:store\",\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"key\": \"s_ecid\",\n" +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"      \"maxAge\": 15552000\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
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
		String expectedEventData =
			"{\n" +
			"  \"type\": \"state:store\",\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"key\": \"s_ecid\",\n" +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"      \"maxAge\": 15552000\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"123\"\n" +
			"}";

		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue("identity:persist".equalsIgnoreCase(returnedEvent.getSource()));
		expectedEventData =
			"{\n" +
			"  \"type\": \"identity:persist\",\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"id\": \"29068398647607325310376254630528178721\",\n" +
			"      \"namespace\": {\n" +
			"        \"code\": \"ECID\"\n" +
			"      }\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
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
		// Batch routing policy: state:store carries no eventIndex and is a global (session-scoped)
		// handle. In a multi-event request it is broadcast with a null parentId rather than being
		// mis-attributed to event[0], so no requestEventId is present. The store side-effect is still
		// persisted globally (handleStoreEventHandle runs before routing).
		String expectedEventData =
			"{\n" +
			"  \"type\": \"state:store\",\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"key\": \"s_ecid\",\n" +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"      \"maxAge\": 15552000\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue("pairedeventexample".equalsIgnoreCase(returnedEvent.getSource()));
		// Indexed handle (eventIndex 1) is still attributed to its specific event.
		expectedEventData =
			"{\n" +
			"  \"type\": \"pairedeventexample\",\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"id\": \"123612123812381\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestEventId\": \"" +
			requestEvent2.getUniqueIdentifier() +
			"\",\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
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
		String expectedEventData =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"id\": \"123612123812381\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"" +
			requestId +
			"\",\n" +
			"  \"type\": \"pairedeventexample\"\n" +
			"}";
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
		String expectedEventData =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"id\": \"123612123812381\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"" +
			requestId +
			"\",\n" +
			"  \"type\": \"pairedeventexample\"\n" +
			"}";
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
		String expectedEventData =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"key\": \"s_ecid\",\n" +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"      \"maxAge\": 15552000\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"123\",\n" +
			"  \"type\": \"state:store\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));
		expectedEventData =
			"{\n" +
			"  \"requestId\": \"123\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\",\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
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
		String expectedEventData =
			"{\n" +
			"  \"requestId\": \"123\",\n" +
			"  \"status\": 503,\n" +
			"  \"title\": \"The 'com.adobe.experience.platform.ode' service is temporarily unable to serve this request. Please try again later.\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());

		returnedEvent = returnedEvents.get(1);
		assertNotNull(returnedEvent);
		assertTrue(EVENT_TYPE_EDGE.equalsIgnoreCase(returnedEvent.getType()));
		assertTrue(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT.equalsIgnoreCase(returnedEvent.getSource()));
		expectedEventData =
			"{\n" +
			"  \"report\": {\n" +
			"    \"cause\": {\n" +
			"      \"code\": 202,\n" +
			"      \"message\": \"Cannot read related customer for device id: ...\"\n" +
			"    }\n" +
			"  },\n" +
			"  \"requestId\": \"123\",\n" +
			"  \"status\": 202,\n" +
			"  \"title\": \"A warning occurred while calling the 'com.adobe.audiencemanager' service for this request.\",\n" +
			"  \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-200\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
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

		String expectedEventData =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"key\": \"s_ecid\",\n" +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"      \"maxAge\": 15552000\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
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

		String expectedEventData =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"key\": \"s_ecid\",\n" +
			"      \"value\": \"MCMID|29068398647607325310376254630528178721\",\n" +
			"      \"maxAge\": 15552000\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"123\"\n" +
			"}";
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

		latch.await(100, TimeUnit.MILLISECONDS);
		latch.await(100, TimeUnit.MILLISECONDS);

		// state:store carries no eventIndex and is a global handle: in a multi-event request it is
		// broadcast (null parentId) and not accumulated against any single event's callback, so
		// event1 (which had no indexed handle of its own) receives zero handles. The store payload
		// is still persisted globally.
		assertEquals(0, receivedData1.size());

		assertEquals(1, receivedData2.size());
		String expectedEventData2 =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"id\": \"123612123812381\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"type\": \"pairedeventexample\"\n" +
			"}";
		JSONAsserts.assertEquals(expectedEventData2, receivedData2.get(0).toMap());
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

		latch.await(100, TimeUnit.MILLISECONDS);
		assertEquals(1, receivedData1.size());
		assertTrue(receivedData2.isEmpty());
		assertTrue(receivedData3.isEmpty());
		String expectedEventData =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"id\": \"123612123812381\"\n" +
			"    }\n" +
			"  ],\n" +
			"  \"type\": \"pairedeventexample\"\n" +
			"}";

		JSONAsserts.assertEquals(expectedEventData, receivedData1.get(0).toMap());
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
		String expectedEventData =
			"{\n" +
			"  \"payload\": [\n" +
			"    {\n" +
			"      \"hint\": \"or2\",\n" +
			"      \"scope\": \"EdgeNetwork\",\n" +
			"      \"ttlSeconds\": 1800\n" +
			"    },\n" +
			"    {\n" +
			"      \"hint\": \"edge34\",\n" +
			"      \"scope\": \"Target\",\n" +
			"      \"ttlSeconds\": 600\n" +
			"    }\n" +
			"  ],\n" +
			"  \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"  \"type\": \"locationHint:result\"\n" +
			"}";

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

		String expectedEventData = "{\"requestId\": \"" + requestId + "\"}";

		assertResponseCompleteEventWithData(
			new Object[] { expectedEventData },
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

		String expectedEventData = "{\"requestId\": \"" + requestId + "\"}";

		assertResponseCompleteEventWithData(
			new Object[] { expectedEventData, expectedEventData },
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

		String expectedEventData = "{\"requestId\": \"" + requestId + "\"}";
		// Assert complete event only dispatched for requestEvent2
		assertResponseCompleteEventWithData(
			new Object[] { expectedEventData },
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

	private void assertResponseErrorEventWithData(final Object expectedEventData) {
		ArgumentCaptor<Event> eventArgCaptor;
		eventArgCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(eventArgCaptor.capture()), times(1));

		Event returnedEvent = eventArgCaptor.getValue();
		assertNotNull(returnedEvent);
		assertEquals(EVENT_NAME_ERROR_RESPONSE, returnedEvent.getName());
		assertEquals(EVENT_TYPE_EDGE, returnedEvent.getType());
		assertEquals(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT, returnedEvent.getSource());
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	private void assertResponseContentEventWithData(final Object expectedEventData, final String eventSource) {
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
		JSONAsserts.assertEquals(expectedEventData, returnedEvent.getEventData());
	}

	private void assertResponseCompleteEventWithData(final Object[] expectedEventDatas, final String[] parentEventIds) {
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
			JSONAsserts.assertEquals(expectedEventDatas[i], returnedEvent.getEventData());
			assertEquals(parentEventIds[i], returnedEvent.getParentID());
			assertEquals(parentEventIds[i], returnedEvent.getResponseID());
		}
	}

	// -------------------------------------------------------------------------
	// WI-2: collapsed no-index error routing — fan-out to all waiting events
	// -------------------------------------------------------------------------

	@Test
	public void testDispatchEventErrors_noIndex_batchOfTwo_fanOutToBothWaitingEvents() {
		// A no-index error for a batch of 2 must produce one ERROR_RESPONSE_CONTENT per waiting event.
		final String jsonError =
			"{\n" +
			"  \"requestId\": \"batch-req-123\",\n" +
			"  \"errors\": [\n" +
			"    {\n" +
			"      \"status\": 503,\n" +
			"      \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"      \"title\": \"Service unavailable\"\n" +
			"    }\n" +
			"  ]\n" +
			"}";

		final String requestId = "batch-req-id";
		final Event event1 = new Event.Builder("e1", "testType", "testSource").build();
		final Event event2 = new Event.Builder("e2", "testType", "testSource").build();

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

		// 2 error events dispatched — one per waiting event
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(captor.capture()), times(2));

		List<Event> dispatched = captor.getAllValues();
		assertEquals(2, dispatched.size());

		// Both events are on the error channel
		for (Event e : dispatched) {
			assertEquals(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT, e.getSource());
		}

		// Each event is chained to its own originating request event
		assertEquals(event1.getUniqueIdentifier(), dispatched.get(0).getParentID());
		assertEquals(event2.getUniqueIdentifier(), dispatched.get(1).getParentID());
	}

	@Test
	public void testDispatchEventErrors_noIndex_singleWaitingEvent_routesToThatEvent() {
		// WI-2 collapsed path: a single waiting event is N==1, the loop naturally yields event[0].
		final String jsonError =
			"{\n" +
			"  \"requestId\": \"req-single\",\n" +
			"  \"errors\": [\n" +
			"    {\n" +
			"      \"status\": 503,\n" +
			"      \"type\": \"https://ns.adobe.com/aep/errors/EXEG-0201-503\",\n" +
			"      \"title\": \"Service unavailable\"\n" +
			"    }\n" +
			"  ]\n" +
			"}";

		final String requestId = "req-single";
		final Event event1 = new Event.Builder("e1", "testType", "testSource").build();

		networkResponseHandler.addWaitingEvents(requestId, new ArrayList<Event>() {{ add(event1); }});
		networkResponseHandler.processResponseOnError(jsonError, requestId);

		// Exactly 1 error event, chained to event1
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(captor.capture()), times(1));

		Event dispatched = captor.getValue();
		assertEquals(EVENT_SOURCE_EXTENSION_ERROR_RESPONSE_CONTENT, dispatched.getSource());
		assertEquals(event1.getUniqueIdentifier(), dispatched.getParentID());
	}

	@Test
	public void testProcessResponseOnSuccess_stateStoreHandle_noIndex_batchOfTwo_broadcastsOnce() {
		// Global handle (state:store) with no eventIndex must be broadcast as exactly ONE event
		// with null parentId regardless of batch size (locked decision 1).
		final String jsonResponse =
			"{\n" +
			"  \"requestId\": \"batch-state-req\",\n" +
			"  \"handle\": [\n" +
			"    {\n" +
			"      \"type\": \"state:store\",\n" +
			"      \"payload\": [{\"key\": \"kndctr_org_cluster\", \"value\": \"va6\", \"maxAge\": 1800}]\n" +
			"    }\n" +
			"  ]\n" +
			"}";

		final String requestId = "batch-state-req";
		final Event event1 = new Event.Builder("e1", "testType", "testSource").build();
		final Event event2 = new Event.Builder("e2", "testType", "testSource").build();

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

		// Exactly one RESPONSE_CONTENT event (the broadcast), NOT two
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(captor.capture()), times(1));

		Event dispatched = captor.getValue();
		// state:store events are dispatched using the handle type as the event source (see dispatchEventResponse)
		assertEquals("state:store", dispatched.getSource());
		// Broadcast has no parent
		assertNull(dispatched.getParentID());
	}

	// -------------------------------------------------------------------------
	// Early per-event completion (index-advance)
	// -------------------------------------------------------------------------

	/** A single streamed fragment carrying one indexed, non-global handle for {@code eventIndex}. */
	private static String indexedHandleFragment(final int eventIndex) {
		return (
			"{\n" +
			"  \"handle\": [\n" +
			"    { \"type\": \"pairedeventexample\", \"eventIndex\": " +
			eventIndex +
			", \"payload\": [ { \"id\": \"p" +
			eventIndex +
			"\" } ] }\n" +
			"  ]\n" +
			"}"
		);
	}

	/** A single streamed fragment carrying a global (no eventIndex) state:store handle. */
	private static String globalStateStoreFragment() {
		return (
			"{\n" +
			"  \"handle\": [\n" +
			"    { \"type\": \"state:store\", \"payload\": [ { \"key\": \"k\", \"value\": \"v\", \"maxAge\": 1 } ] }\n" +
			"  ]\n" +
			"}"
		);
	}

	private Event completionEvent(final String name) {
		return new Event.Builder(name, "testType", "testSource").setEventData(requestSendCompletionTrueEventData).build();
	}

	/** Captures all dispatched events so far and returns only the CONTENT_COMPLETE ones, in order. */
	private List<Event> capturedContentCompleteEvents() {
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(captor.capture()), atLeast(0));
		List<Event> completes = new ArrayList<>();
		for (Event e : captor.getAllValues()) {
			if (EVENT_SOURCE_CONTENT_COMPLETE.equals(e.getSource())) {
				completes.add(e);
			}
		}
		return completes;
	}

	@Test
	public void testEarlyCompletion_multiEventStream_completesEachEventWhenNextIndexArrives() {
		final String requestId = "req-early";
		final Event e0 = completionEvent("e0");
		final Event e1 = completionEvent("e1");
		final Event e2 = completionEvent("e2");
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(e0);
					add(e1);
					add(e2);
				}
			}
		);

		// index 0 fragment: nothing completes yet (no higher index seen)
		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(0), requestId);
		assertEquals(0, capturedContentCompleteEvents().size());

		// index 1 fragment: event 0 is now known complete
		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(1), requestId);
		List<Event> after1 = capturedContentCompleteEvents();
		assertEquals(1, after1.size());
		assertEquals(e0.getUniqueIdentifier(), after1.get(0).getParentID());

		// index 2 fragment: event 1 now completes
		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(2), requestId);
		List<Event> after2 = capturedContentCompleteEvents();
		assertEquals(2, after2.size());
		assertEquals(e1.getUniqueIdentifier(), after2.get(1).getParentID());

		// stream close: the last event (index 2) completes
		networkResponseHandler.processResponseOnComplete(requestId);
		List<Event> afterComplete = capturedContentCompleteEvents();
		assertEquals(3, afterComplete.size());
		assertEquals(e2.getUniqueIdentifier(), afterComplete.get(2).getParentID());
	}

	@Test
	public void testEarlyCompletion_completionFiresBeforeNextIndexHandleDispatch() {
		// A completing event's downstream listeners (e.g. a caller merging accumulated response data
		// into its own cache on completion) must never observe a higher-indexed sibling's handle data
		// that arrived in the same response — so completion(N) must be dispatched strictly before
		// index N+1's own handle is dispatched, not after.
		final String requestId = "req-order";
		final Event e0 = completionEvent("e0");
		final Event e1 = completionEvent("e1");
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(e0);
					add(e1);
				}
			}
		);

		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(0), requestId);

		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		mockCore.reset();
		// index 1 fragment, in the SAME processResponseOnSuccess call: this must first complete
		// event 0, THEN dispatch index 1's own handle — not the other way around.
		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(1), requestId);
		mockCore.verify(() -> MobileCore.dispatchEvent(captor.capture()), atLeast(1));

		int completionIndex = -1;
		int handle1Index = -1;
		List<Event> dispatched = captor.getAllValues();
		for (int i = 0; i < dispatched.size(); i++) {
			Event e = dispatched.get(i);
			if (EVENT_SOURCE_CONTENT_COMPLETE.equals(e.getSource()) && e0.getUniqueIdentifier().equals(e.getParentID())) {
				completionIndex = i;
			} else if ("pairedeventexample".equals(e.getSource())) {
				handle1Index = i;
			}
		}

		assertTrue("event 0's completion must be dispatched", completionIndex >= 0);
		assertTrue("index 1's handle must be dispatched", handle1Index >= 0);
		assertTrue(
			"completion for event 0 must be dispatched before index 1's handle, so a completion listener never observes index 1's data",
			completionIndex < handle1Index
		);
	}

	@Test
	public void testEarlyCompletion_zeroHandleMiddleEvent_completesOnIndexAdvance() {
		final String requestId = "req-zero";
		final Event e0 = completionEvent("e0");
		final Event e1 = completionEvent("e1"); // will receive no handle of its own
		final Event e2 = completionEvent("e2");
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(e0);
					add(e1);
					add(e2);
				}
			}
		);

		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(0), requestId);
		assertEquals(0, capturedContentCompleteEvents().size());

		// jump straight to index 2 (event 1 produced nothing) → events 0 AND 1 complete
		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(2), requestId);
		List<Event> completes = capturedContentCompleteEvents();
		assertEquals(2, completes.size());
		assertEquals(e0.getUniqueIdentifier(), completes.get(0).getParentID());
		assertEquals(e1.getUniqueIdentifier(), completes.get(1).getParentID());

		networkResponseHandler.processResponseOnComplete(requestId);
		assertEquals(3, capturedContentCompleteEvents().size());
	}

	@Test
	public void testEarlyCompletion_globalHandle_doesNotAdvanceCompletion() {
		final String requestId = "req-global";
		final Event e0 = completionEvent("e0");
		final Event e1 = completionEvent("e1");
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(e0);
					add(e1);
				}
			}
		);

		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(0), requestId);
		// a global (no-eventIndex) handle must NOT advance completion
		networkResponseHandler.processResponseOnSuccess(globalStateStoreFragment(), requestId);
		assertEquals(0, capturedContentCompleteEvents().size());

		networkResponseHandler.processResponseOnComplete(requestId);
		assertEquals(2, capturedContentCompleteEvents().size());
	}

	@Test
	public void testEarlyCompletion_batchOfOne_completesOnlyAtStreamClose() {
		final String requestId = "req-single";
		final Event e0 = completionEvent("e0");
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(e0);
				}
			}
		);

		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(0), requestId);
		// only index 0 exists → no early completion, identical to legacy
		assertEquals(0, capturedContentCompleteEvents().size());

		networkResponseHandler.processResponseOnComplete(requestId);
		List<Event> completes = capturedContentCompleteEvents();
		assertEquals(1, completes.size());
		assertEquals(e0.getUniqueIdentifier(), completes.get(0).getParentID());
	}

	@Test
	public void testEarlyCompletion_outOfOrderLowerIndex_doesNotDoubleComplete() {
		final String requestId = "req-anomaly";
		final Event e0 = completionEvent("e0");
		final Event e1 = completionEvent("e1");
		final Event e2 = completionEvent("e2");
		networkResponseHandler.addWaitingEvents(
			requestId,
			new ArrayList<Event>() {
				{
					add(e0);
					add(e1);
					add(e2);
				}
			}
		);

		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(0), requestId);
		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(2), requestId); // completes e0, e1
		assertEquals(2, capturedContentCompleteEvents().size());

		// anomaly: index 1 arrives after events through index 1 were already completed → no re-completion
		networkResponseHandler.processResponseOnSuccess(indexedHandleFragment(1), requestId);
		assertEquals(2, capturedContentCompleteEvents().size());

		networkResponseHandler.processResponseOnComplete(requestId);
		List<Event> all = capturedContentCompleteEvents();
		assertEquals(3, all.size());
		// each event completed exactly once, in index order
		assertEquals(e0.getUniqueIdentifier(), all.get(0).getParentID());
		assertEquals(e1.getUniqueIdentifier(), all.get(1).getParentID());
		assertEquals(e2.getUniqueIdentifier(), all.get(2).getParentID());
	}

}
