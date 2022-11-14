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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.powermock.api.mockito.PowerMockito.when;

import android.app.Application;
import com.adobe.marketing.mobile.util.MockHitQueue;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ MobileCore.class, ExtensionApi.class, EdgeExtension.class })
public class EdgeExtensionTest {

	private EdgeExtension edgeExtension;
	private EdgeState state;
	private MockHitQueue mockQueue;
	private Map<String, Object> configData = new HashMap<String, Object>() {
		{
			put(EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID, "123");
		}
	};

	private Event event1 = new Event.Builder(
		"event1",
		EdgeConstants.EventType.EDGE,
		EdgeConstants.EventSource.REQUEST_CONTENT
	)
		.setEventData(
			new HashMap<String, Object>() {
				{
					put("data", "testevent1");
				}
			}
		)
		.build();

	private Event getHintEvent = new Event.Builder(
		"Get Location Hint",
		EdgeConstants.EventType.EDGE,
		EdgeConstants.EventSource.REQUEST_IDENTITY
	)
		.setEventData(
			new HashMap<String, Object>() {
				{
					put("locationHint", true);
				}
			}
		)
		.build();

	private final String jsonStr =
		"{\n" +
		"      \"identityMap\": {\n" +
		"        \"ECID\": [\n" +
		"          {\n" +
		"            \"id\":" +
		"123" +
		",\n" +
		"            \"authenticatedState\": \"authenticated\",\n" +
		"            \"primary\": false\n" +
		"          }\n" +
		"        ]\n" +
		"      }\n" +
		"}";
	private Map<String, Object> identityState;

	@Mock
	ExtensionApi mockExtensionApi;

	@Mock
	Application mockApplication;

	@Before
	public void setup() throws Exception {
		PowerMockito.mockStatic(MobileCore.class);
		Mockito.when(MobileCore.getApplication()).thenReturn(mockApplication);

		mockQueue = new MockHitQueue();
		edgeExtension = new EdgeExtension(mockExtensionApi, mockQueue);
		state = edgeExtension.state;
		mockSharedStates(null, null); // default return null, to be set in test if needed
		JSONObject test = new JSONObject();
		test.put("test", "case");

		final JSONObject jsonObject = new JSONObject(jsonStr);
		identityState = Utils.toMap(jsonObject);
	}

	@Test
	public void testHandleExperienceEventRequest_queued() {
		edgeExtension.handleExperienceEventRequest(event1);
		edgeExtension.handleExperienceEventRequest(event1);
		edgeExtension.handleExperienceEventRequest(event1);
		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		assertEquals(4, edgeExtension.getCachedEvents().size());
	}

	@Test
	public void testHandleExperienceEventRequest_whenCollectConsentNo_doesNotQueue() throws Exception {
		when(
			mockExtensionApi.getXDMSharedEventState(
				eq(EdgeConstants.SharedState.CONSENT),
				any(Event.class),
				isNull(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(getConsentsData(ConsentStatus.NO));

		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		assertEquals(0, edgeExtension.getCachedEvents().size());
	}

	@Test
	public void testHandleExperienceEventRequest_whenCollectConsentPending_queues() throws Exception {
		when(
			mockExtensionApi.getXDMSharedEventState(
				eq(EdgeConstants.SharedState.CONSENT),
				any(Event.class),
				isNull(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(getConsentsData(ConsentStatus.PENDING));

		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		assertEquals(1, edgeExtension.getCachedEvents().size());
	}

	@Test
	public void testHandleExperienceEventRequest_whenConsentSharedStateNull_usesCurrentConsent() throws Exception {
		when(
			mockExtensionApi.getXDMSharedEventState(
				eq(EdgeConstants.SharedState.CONSENT),
				any(Event.class),
				isNull(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(null);

		// test current consent yes
		state.updateCurrentConsent(ConsentStatus.YES);
		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		assertEquals(1, edgeExtension.getCachedEvents().size());

		// test current consent no
		state.updateCurrentConsent(ConsentStatus.NO);
		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		assertEquals(1, edgeExtension.getCachedEvents().size());

		// test current consent yes
		state.updateCurrentConsent(ConsentStatus.YES);
		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		assertEquals(2, edgeExtension.getCachedEvents().size());
	}

	// Tests for void handleConsentUpdate(final Event event)
	// testHear_edgeUpdateConsent_whenEmptyData - see EdgeExtensionListenerTests

	@Test
	public void testHandleConsentUpdate_queues() {
		edgeExtension.handleConsentUpdate(
			new Event.Builder("Consent update", EdgeConstants.EventType.EDGE, EdgeConstants.EventSource.UPDATE_CONSENT)
				.setEventData(getConsentsData(ConsentStatus.YES))
				.build()
		);

		assertEquals(1, edgeExtension.getCachedEvents().size());
	}

	// Tests for void handleConsentPreferencesUpdate(final Event event)
	// testHear_consentPreferencesUpdated_whenEmptyData - see EdgeExtensionListenerTests

	@Test
	public void testHandlePreferencesUpdate_validData() {
		edgeExtension.handleConsentPreferencesUpdate(
			new Event.Builder(
				"Consent update",
				EdgeConstants.EventType.CONSENT,
				EdgeConstants.EventSource.RESPONSE_CONTENT
			)
				.setEventData(getConsentsData(ConsentStatus.YES))
				.build()
		);

		assertEquals(0, mockQueue.getCachedEntities().size());
		assertEquals(ConsentStatus.YES, state.getCurrentCollectConsent());
	}

	// Tests for void handleSharedStateUpdate(final Event event)
	// testHear_sharedStateUpdate_whenEmptyData - see EdgeExtensionListenerTests

	@Test
	public void testHandleSharedStateUpdate_hubSharedState_consentNotRegistered() {
		when(
			mockExtensionApi.getXDMSharedEventState(
				eq(EdgeConstants.SharedState.HUB),
				any(Event.class),
				isNull(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(getHubExtensions(false));
		edgeExtension.handleSharedStateUpdate(
			new Event.Builder(
				"Shared State update",
				EdgeConstants.EventType.ADOBE_HUB,
				EdgeConstants.EventSource.ADOBE_SHARED_STATE
			)
				.setEventData(
					new HashMap<String, Object>() {
						{
							put("stateowner", EdgeConstants.SharedState.HUB);
						}
					}
				)
				.build()
		);

		assertEquals(ConsentStatus.YES, state.getCurrentCollectConsent());
	}

	@Test
	public void testHandleSharedStateUpdate_hubSharedState_consentRegistered() {
		when(
			mockExtensionApi.getSharedEventState(
				eq(EdgeConstants.SharedState.HUB),
				any(Event.class),
				isNull(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(getHubExtensions(true));
		edgeExtension.handleSharedStateUpdate(
			new Event.Builder(
				"Shared State update",
				EdgeConstants.EventType.ADOBE_HUB,
				EdgeConstants.EventSource.ADOBE_SHARED_STATE
			)
				.setEventData(
					new HashMap<String, Object>() {
						{
							put("stateowner", EdgeConstants.SharedState.HUB);
						}
					}
				)
				.build()
		);

		assertEquals(ConsentStatus.PENDING, state.getCurrentCollectConsent());
	}

	@Test
	public void testHandleSharedStateUpdate_hubSharedState_setsImplementationDetails() throws Exception {
		when(
			mockExtensionApi.getSharedEventState(
				eq(EdgeConstants.SharedState.HUB),
				any(Event.class),
				isNull(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(getHubExtensions(true));
		edgeExtension.handleSharedStateUpdate(
			new Event.Builder(
				"Shared State update",
				EdgeConstants.EventType.ADOBE_HUB,
				EdgeConstants.EventSource.ADOBE_SHARED_STATE
			)
				.setEventData(
					new HashMap<String, Object>() {
						{
							put("stateowner", EdgeConstants.SharedState.HUB);
						}
					}
				)
				.build()
		);

		assertNotNull(state.getImplementationDetails());
		Map<String, Object> details = (Map<String, Object>) state
			.getImplementationDetails()
			.get("implementationDetails");
		assertNotNull(details);
		assertEquals("app", details.get("environment"));
		assertEquals("1.0.0+" + EdgeConstants.EXTENSION_VERSION, details.get("version"));
		assertEquals(EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE, details.get("name"));
	}

	@Test
	public void testProcessAddEvent_nullEvent_doesNotCrash() {
		edgeExtension.processAddEvent(null);

		//verify
		assertEquals(0, edgeExtension.getCachedEvents().size());
		assertEquals(0, mockQueue.getCachedEntities().size());
	}

	@Test
	public void testProcessCachedEvents_resetComplete_doesNotWaitForConfigIdentity() {
		edgeExtension.processAddEvent(
			new Event.Builder("test", "com.adobe.eventType.edgeIdentity", "com.adobe.eventSource.resetComplete").build()
		);
		edgeExtension.processCachedEvents();

		//verify
		assertEquals(0, edgeExtension.getCachedEvents().size());
		assertEquals(1, mockQueue.getCachedEntities().size());
	}

	@Test
	public void testProcessCachedEvents_otherEvents_waitsForConfig() {
		edgeExtension.processAddEvent(event1);
		edgeExtension.processCachedEvents();

		//verify
		assertEquals(1, edgeExtension.getCachedEvents().size());
		assertEquals(0, mockQueue.getCachedEntities().size());
	}

	@Test
	public void testProcessCachedEvents_otherEvents_waitsForIdentity() {
		mockSharedStates(configData, null);

		edgeExtension.processAddEvent(event1);
		edgeExtension.processCachedEvents();

		//verify
		assertEquals(1, edgeExtension.getCachedEvents().size());
		assertEquals(0, mockQueue.getCachedEntities().size());
	}

	@Test
	public void testProcessCachedEvents_otherEvents_whenConfigIdMissing_dropsEvent() {
		mockSharedStates(
			new HashMap<String, Object>() {
				{
					put("test", "missingEdgeConfigId");
				}
			},
			identityState
		);

		edgeExtension.processAddEvent(event1);
		edgeExtension.processCachedEvents();

		//verify
		assertEquals(0, edgeExtension.getCachedEvents().size());
		assertEquals(0, mockQueue.getCachedEntities().size());
	}

	@Test
	public void testProcessCachedEvents_otherEvents_happy() {
		mockSharedStates(configData, identityState);

		edgeExtension.processAddEvent(event1);
		edgeExtension.processCachedEvents();

		//verify
		assertEquals(0, edgeExtension.getCachedEvents().size());
		assertEquals(1, mockQueue.getCachedEntities().size());
	}

	@Test
	public void testHandleGetLocationHint_dispatchesResponseWithHintValue() {
		state.setLocationHint("or2", 1800);
		edgeExtension.handleGetLocationHint(getHintEvent);

		final ArgumentCaptor<Event> responseEventCaptor = ArgumentCaptor.forClass(Event.class);
		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);

		PowerMockito.verifyStatic(MobileCore.class, Mockito.times(1));
		MobileCore.dispatchResponseEvent(
			responseEventCaptor.capture(),
			requestEventCaptor.capture(),
			any(ExtensionErrorCallback.class)
		);

		final Event responseEvent = responseEventCaptor.getAllValues().get(0);
		assertEquals("com.adobe.eventtype.edge", responseEvent.getType());
		assertEquals("com.adobe.eventsource.responseidentity", responseEvent.getSource());
		assertTrue(responseEvent.getEventData().containsKey("locationHint"));

		final String hint = (String) responseEvent.getEventData().get("locationHint");
		assertEquals("or2", hint);
	}

	@Test
	public void testHandleGetLocationHint_whenNoHint_dispatchesResponseWithNullValue() {
		edgeExtension.handleGetLocationHint(getHintEvent);

		final ArgumentCaptor<Event> responseEventCaptor = ArgumentCaptor.forClass(Event.class);
		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);

		PowerMockito.verifyStatic(MobileCore.class, Mockito.times(1));
		MobileCore.dispatchResponseEvent(
			responseEventCaptor.capture(),
			requestEventCaptor.capture(),
			any(ExtensionErrorCallback.class)
		);

		final Event responseEvent = responseEventCaptor.getAllValues().get(0);
		assertEquals("com.adobe.eventtype.edge", responseEvent.getType());
		assertEquals("com.adobe.eventsource.responseidentity", responseEvent.getSource());
		assertTrue(responseEvent.getEventData().containsKey("locationHint"));

		assertTrue(responseEvent.getEventData().containsKey("locationHint"));
		assertNull(responseEvent.getEventData().get("locationHint")); // no hint set returns null
	}

	@Test
	public void testHandleGetLocationHint_whenHintExpired_dispatchesResponseWithNullValue()
		throws InterruptedException {
		state.setLocationHint("or2", 1);
		Thread.sleep(1500); // expire hint
		edgeExtension.handleGetLocationHint(getHintEvent);

		final ArgumentCaptor<Event> responseEventCaptor = ArgumentCaptor.forClass(Event.class);
		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);

		PowerMockito.verifyStatic(MobileCore.class, Mockito.times(1));
		MobileCore.dispatchResponseEvent(
			responseEventCaptor.capture(),
			requestEventCaptor.capture(),
			any(ExtensionErrorCallback.class)
		);

		final Event responseEvent = responseEventCaptor.getAllValues().get(0);
		assertEquals("com.adobe.eventtype.edge", responseEvent.getType());
		assertEquals("com.adobe.eventsource.responseidentity", responseEvent.getSource());
		assertTrue(responseEvent.getEventData().containsKey("locationHint"));
		assertNull(responseEvent.getEventData().get("locationHint")); // expired hint returns null
	}

	@Test
	public void testhandleSetLocationHint_whenValueHint_setsHint() {
		final Event requestEvent = new Event.Builder(
			"Set Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("locationHint", "or2");
					}
				}
			)
			.build();

		edgeExtension.handleSetLocationHint(requestEvent);

		assertEquals("or2", state.getLocationHint());
	}

	@Test
	public void testhandleSetLocationHint_whenEmptyHint_clearsHint() {
		state.setLocationHint("or2", 1800);
		final Event requestEvent = new Event.Builder(
			"Set Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("locationHint", "");
					}
				}
			)
			.build();

		edgeExtension.handleSetLocationHint(requestEvent);

		assertNull(state.getLocationHint());
	}

	@Test
	public void testhandleSetLocationHint_whenNullHint_clearsHint() {
		state.setLocationHint("or2", 1800);
		final Event requestEvent = new Event.Builder(
			"Set Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("locationHint", null);
					}
				}
			)
			.build();

		edgeExtension.handleSetLocationHint(requestEvent);

		assertNull(state.getLocationHint());
	}

	private void mockSharedStates(final Map<String, Object> config, final Map<String, Object> identity) {
		when(
			mockExtensionApi.getSharedEventState(
				eq(EdgeConstants.SharedState.CONFIGURATION),
				any(Event.class),
				any(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(config);
		when(
			mockExtensionApi.getXDMSharedEventState(
				eq(EdgeConstants.SharedState.IDENTITY),
				any(Event.class),
				any(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(identity);
	}

	private void mockHubSharedState(final Map<String, Object> hub) {
		when(
			mockExtensionApi.getSharedEventState(
				eq(EdgeConstants.SharedState.HUB),
				any(Event.class),
				isNull(ExtensionErrorCallback.class)
			)
		)
			.thenReturn(hub);
	}

	/**
	 * xdm formatted payload for collect consent with provided {@code status}
	 */
	private Map<String, Object> getConsentsData(final ConsentStatus status) {
		return new HashMap<String, Object>() {
			{
				put(
					"consents",
					new HashMap<String, Object>() {
						{
							put(
								"collect",
								new HashMap<String, Object>() {
									{
										put("val", status.getValue());
									}
								}
							);
						}
					}
				);
			}
		};
	}

	/**
	 * Mocks the hub shared state payload for registered extensions. To be used when testing consent extension registration checks
	 */
	private Map<String, Object> getHubExtensions(final boolean consentRegistered) {
		final Map<String, Object> registeredExtensions = new HashMap<String, Object>();

		if (consentRegistered) {
			registeredExtensions.put(
				EdgeConstants.SharedState.CONSENT,
				new HashMap<String, Object>() {
					{
						put("version", "1.0.0");
					}
				}
			);
		}

		registeredExtensions.put(
			EdgeConstants.SharedState.CONFIGURATION,
			new HashMap<String, Object>() {
				{
					put("version", "2.0.0");
				}
			}
		);

		return new HashMap<String, Object>() {
			{
				put("extensions", registeredExtensions);
				put("version", "1.0.0");
			}
		};
	}
}
