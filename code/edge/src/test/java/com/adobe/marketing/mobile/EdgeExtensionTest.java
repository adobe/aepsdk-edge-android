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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.util.JSONUtils;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EdgeExtensionTest {

	private EdgeExtension edgeExtension;
	private EdgeState state;
	private final Map<String, Object> configData = new HashMap<String, Object>() {
		{
			put(EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID, "123");
		}
	};

	private final Event event1 = new Event.Builder("event1", EventType.EDGE, EventSource.REQUEST_CONTENT)
		.setEventData(
			new HashMap<String, Object>() {
				{
					put("data", "testevent1");
				}
			}
		)
		.build();

	private final Event getHintEvent = new Event.Builder(
		"Get Location Hint",
		EventType.EDGE,
		EventSource.REQUEST_IDENTITY
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
	HitQueuing mockQueue;

	@Before
	public void setup() throws Exception {
		edgeExtension = new EdgeExtension(mockExtensionApi, mockQueue);
		state = edgeExtension.state;
		mockSharedStates(null, null, null); // default return null, to be set in test if needed

		identityState = JSONUtils.toMap(new JSONObject(jsonStr));
	}

	@Test
	public void testHandleExperienceEventRequest_whenCollectConsentYes_queues() {
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			new SharedStateResult(SharedStateStatus.SET, getConsentsData(ConsentStatus.YES))
		);

		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		verifyEventQueued(event1);
		verifyGetSharedStateCalls(1, 1, 1);
	}

	@Test
	public void testHandleExperienceEventRequest_whenCollectConsentNo_doesNotQueue() {
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			new SharedStateResult(SharedStateStatus.SET, getConsentsData(ConsentStatus.NO))
		);

		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
		verifyGetSharedStateCalls(0, 0, 1);
	}

	@Test
	public void testHandleExperienceEventRequest_whenCollectConsentPending_queues() {
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			new SharedStateResult(SharedStateStatus.SET, getConsentsData(ConsentStatus.PENDING))
		);

		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		verifyEventQueued(event1);
		verifyGetSharedStateCalls(1, 1, 1);
	}

	@Test
	public void testHandleExperienceEventRequest_whenConsentSharedStateNull_usesCurrentConsent() throws Exception {
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null
		);

		// todo: verify this
		// test current consent yes
		state.updateCurrentConsent(ConsentStatus.YES);
		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		verifyEventQueued(event1);
		reset(mockQueue);

		// test current consent no
		state.updateCurrentConsent(ConsentStatus.NO);
		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
		reset(mockQueue);

		// test current consent yes
		state.updateCurrentConsent(ConsentStatus.YES);
		edgeExtension.handleExperienceEventRequest(event1);

		//verify
		verifyEventQueued(event1);
	}

	@Test
	public void testHandleExperienceEventRequest_whenEmptyEventData_ignoresEvent() {
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null
		);

		edgeExtension.handleExperienceEventRequest(
			new Event.Builder("event1", EventType.EDGE, EventSource.REQUEST_CONTENT).build()
		);

		//verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
	}

	// Tests for void handleConsentUpdate(final Event event)

	@Test
	public void testHandleConsentUpdate_queues() {
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null // consent data taken from current event and not from shared state in this case
		);
		Event consentUpdate = new Event.Builder("Consent update", EventType.EDGE, EventSource.UPDATE_CONSENT)
			.setEventData(getConsentsData(ConsentStatus.YES))
			.build();
		edgeExtension.handleConsentUpdate(consentUpdate);

		verifyEventQueued(consentUpdate);
		verifyGetSharedStateCalls(1, 1, 0);
	}

	@Test
	public void testHandleConsentUpdate_whenEmptyEventData_ignoresEvent() {
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null
		);

		edgeExtension.handleConsentUpdate(
			new Event.Builder("Consent update", EventType.EDGE, EventSource.UPDATE_CONSENT).build()
		);

		//verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
	}

	@Test
	public void testProcessAndQueueEvent_whenConfigIdMissing_dropsEvent() {
		mockSharedStates(
			new SharedStateResult(
				SharedStateStatus.SET,
				new HashMap<String, Object>() {
					{
						put("test", "missingEdgeConfigId");
					}
				}
			),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null
		);

		edgeExtension.processAndQueueEvent(event1);

		//verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
	}

	// Tests for void handleConsentPreferencesUpdate(final Event event)

	@Test
	public void testHandlePreferencesUpdate_validData_readsConsentStatus() {
		edgeExtension.handleConsentPreferencesUpdate(
			new Event.Builder("Consent update", EventType.CONSENT, EventSource.RESPONSE_CONTENT)
				.setEventData(getConsentsData(ConsentStatus.YES))
				.build()
		);

		// verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
		assertEquals(ConsentStatus.YES, state.getCurrentCollectConsent());
	}

	@Test
	public void testHandlePreferencesUpdate_whenEmptyEventData_ignoresEvent() {
		// set consent preferences yes
		state.updateCurrentConsent(ConsentStatus.YES);

		//
		edgeExtension.handleConsentPreferencesUpdate(
			new Event.Builder("Consent update", EventType.CONSENT, EventSource.RESPONSE_CONTENT).build()
		);

		// verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
		assertEquals(ConsentStatus.YES, state.getCurrentCollectConsent());
	}

	@Test
	public void testHandlePreferencesUpdate_invalidPayloadFormat_setsDefaultConsentPending() {
		edgeExtension.handleConsentPreferencesUpdate(
			new Event.Builder("Consent update", EventType.CONSENT, EventSource.RESPONSE_CONTENT)
				.setEventData(
					new HashMap<String, Object>() {
						{
							put("consents", "I am a string and not a map");
						}
					}
				)
				.build()
		);

		// verify
		verify(mockQueue, never()).queue(any(DataEntity.class));
		assertEquals(ConsentStatus.PENDING, state.getCurrentCollectConsent());
	}

	@Test
	public void testReadyForEvent_whenNotBootedUp_waits_returnsFalse() {
		// setup: bootupIfNeeded waits for HUB shared state
		mockHubSharedState(new SharedStateResult(SharedStateStatus.PENDING, null));
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null
		);
		assertFalse(edgeExtension.readyForEvent(event1));
	}

	@Test
	public void testReadyForEvent_resetIdentityComplete_waitsForConfig_returnsFalse() {
		// setup: mock hub shared state for bootupIfNeeded
		mockHubSharedState(new SharedStateResult(SharedStateStatus.SET, getHubExtensions(true)));
		mockSharedStates(new SharedStateResult(SharedStateStatus.PENDING, null), null, null);
		Event resetCompleteEvent = new Event.Builder(
			"test reset complete",
			EventType.EDGE_IDENTITY,
			EventSource.RESET_COMPLETE
		)
			.build();
		assertFalse(edgeExtension.readyForEvent(resetCompleteEvent));
	}

	@Test
	public void testReadyForEvent_resetIdentityComplete_waitsForIdentity_returnsFalse() {
		// setup: mock hub shared state for bootupIfNeeded
		mockHubSharedState(new SharedStateResult(SharedStateStatus.SET, getHubExtensions(true)));
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.PENDING, null),
			null
		);
		Event resetCompleteEvent = new Event.Builder(
			"test reset complete",
			EventType.EDGE_IDENTITY,
			EventSource.RESET_COMPLETE
		)
			.build();
		assertFalse(edgeExtension.readyForEvent(resetCompleteEvent));
	}

	@Test
	public void testReadyForEvent_resetIdentityComplete_withConfigAndIdentity_returnsTrue() {
		// setup: mock hub shared state for bootupIfNeeded
		mockHubSharedState(new SharedStateResult(SharedStateStatus.SET, getHubExtensions(true)));
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null
		);
		Event resetCompleteEvent = new Event.Builder(
			"test reset complete",
			EventType.EDGE_IDENTITY,
			EventSource.RESET_COMPLETE
		)
			.build();
		assertTrue(edgeExtension.readyForEvent(resetCompleteEvent));
	}

	@Test
	public void testReadyForEvent_otherEvents_waitsForConfig_returnsFalse() {
		mockSharedStates(new SharedStateResult(SharedStateStatus.PENDING, null), null, null);

		//verify
		assertFalse(edgeExtension.readyForEvent(event1));
	}

	@Test
	public void testReadyForEvent_otherEvents_waitsForIdentity_returnsFalse() {
		mockSharedStates(new SharedStateResult(SharedStateStatus.SET, configData), null, null);

		//verify
		assertFalse(edgeExtension.readyForEvent(event1));
	}

	@Test
	public void testReadyForEvent_otherEvents_withConfigAndIdentity_returnsTrue() {
		// setup: mock hub shared state for bootupIfNeeded
		mockHubSharedState(new SharedStateResult(SharedStateStatus.SET, getHubExtensions(true)));
		mockSharedStates(
			new SharedStateResult(SharedStateStatus.SET, configData),
			new SharedStateResult(SharedStateStatus.SET, identityState),
			null
		);
		assertTrue(edgeExtension.readyForEvent(event1));
	}

	@Test
	public void testHandleGetLocationHint_dispatchesResponseWithHintValue() {
		state.setLocationHint("or2", 1800);
		edgeExtension.handleGetLocationHint(getHintEvent);

		final ArgumentCaptor<Event> responseEventCaptor = ArgumentCaptor.forClass(Event.class);

		verify(mockExtensionApi, times(1)).dispatch(responseEventCaptor.capture());

		final Event responseEvent = responseEventCaptor.getAllValues().get(0);
		assertEquals(EventType.EDGE, responseEvent.getType());
		assertEquals(EventSource.RESPONSE_IDENTITY, responseEvent.getSource());
		assertEquals(getHintEvent.getUniqueIdentifier(), responseEvent.getResponseID()); // verifies in response to request event

		assertTrue(responseEvent.getEventData().containsKey("locationHint"));
		assertEquals("or2", (String) responseEvent.getEventData().get("locationHint"));
	}

	@Test
	public void testHandleGetLocationHint_whenNoHint_dispatchesResponseWithNullValue() {
		edgeExtension.handleGetLocationHint(getHintEvent);

		final ArgumentCaptor<Event> responseEventCaptor = ArgumentCaptor.forClass(Event.class);
		verify(mockExtensionApi, times(1)).dispatch(responseEventCaptor.capture());

		final Event responseEvent = responseEventCaptor.getAllValues().get(0);
		assertEquals(EventType.EDGE, responseEvent.getType());
		assertEquals(EventSource.RESPONSE_IDENTITY, responseEvent.getSource());

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
		verify(mockExtensionApi, times(1)).dispatch(responseEventCaptor.capture());

		final Event responseEvent = responseEventCaptor.getAllValues().get(0);
		assertEquals(EventType.EDGE, responseEvent.getType());
		assertEquals(EventSource.RESPONSE_IDENTITY, responseEvent.getSource());

		assertTrue(responseEvent.getEventData().containsKey("locationHint"));
		assertNull(responseEvent.getEventData().get("locationHint")); // expired hint returns null
	}

	@Test
	public void testHandleSetLocationHint_whenValueHint_setsHint() {
		final Event requestEvent = new Event.Builder("Set Location Hint", EventType.EDGE, EventSource.UPDATE_IDENTITY)
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
	public void testHandleSetLocationHint_whenEmptyHint_clearsHint() {
		state.setLocationHint("or2", 1800);
		final Event requestEvent = new Event.Builder("Set Location Hint", EventType.EDGE, EventSource.UPDATE_IDENTITY)
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
	public void testHandleSetLocationHint_whenNullHint_clearsHint() {
		state.setLocationHint("or2", 1800);
		final Event requestEvent = new Event.Builder("Set Location Hint", EventType.EDGE, EventSource.UPDATE_IDENTITY)
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

	@Test
	public void testHandleSetLocationHint_whenEmptyEventData_ignoresEvent() {
		state.setLocationHint("or2", 1800);
		final Event requestEvent = new Event.Builder("Set Location Hint", EventType.EDGE, EventSource.UPDATE_IDENTITY)
			.build();

		edgeExtension.handleSetLocationHint(requestEvent);

		assertEquals("or2", state.getLocationHint());
	}

	@Test
	public void testHandleSetLocationHint_whenInvalidLocationHintType_doesNotCrash() {
		state.setLocationHint("or2", 1800);
		final Event requestEvent = new Event.Builder("Set Location Hint", EventType.EDGE, EventSource.UPDATE_IDENTITY)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("locationHint", true);
					}
				}
			)
			.build();

		edgeExtension.handleSetLocationHint(requestEvent);

		assertEquals("or2", state.getLocationHint());
	}

	/**
	 * Mocks the shared state result on the {@code mockExtensionApi}
	 *
	 * @param config {@code SharedStateResult} returned when the getSharedState API is invoked for Configuration
	 * @param identity {@code SharedStateResult} returned when the getXDMSharedState API is invoked for Edge Identity
	 * @param consent {@code SharedStateResult} returned when the getXDMSharedState API is invoked for Consent
	 */
	private void mockSharedStates(
		final SharedStateResult config,
		final SharedStateResult identity,
		final SharedStateResult consent
	) {
		when(
			mockExtensionApi.getSharedState(
				eq(EdgeConstants.SharedState.CONFIGURATION),
				any(Event.class),
				any(Boolean.class),
				any(SharedStateResolution.class)
			)
		)
			.thenReturn(config);

		when(
			mockExtensionApi.getXDMSharedState(
				eq(EdgeConstants.SharedState.IDENTITY),
				any(Event.class),
				any(Boolean.class),
				any(SharedStateResolution.class)
			)
		)
			.thenReturn(identity);

		when(
			mockExtensionApi.getXDMSharedState(
				eq(EdgeConstants.SharedState.CONSENT),
				any(Event.class),
				any(Boolean.class),
				any(SharedStateResolution.class)
			)
		)
			.thenReturn(consent);
	}

	/**
	 * Verifies the {@code expectedEvent} was queued once.
	 *
	 * @param expectedEvent to verify against the queue captor; should not be null
	 */
	private void verifyEventQueued(final Event expectedEvent) {
		assertNotNull(expectedEvent);
		final ArgumentCaptor<DataEntity> entityCaptor = ArgumentCaptor.forClass(DataEntity.class);
		verify(mockQueue, times(1)).queue(entityCaptor.capture());
		EdgeDataEntity edgeEntity = EdgeDataEntity.fromDataEntity(entityCaptor.getValue());
		assertNotNull(edgeEntity);
		assertEquals(expectedEvent.getName(), edgeEntity.getEvent().getName());
	}

	/**
	 * Verifies the number of shared state retrievals was as expected. The expected number of invocations
	 * should be equal or greater than 0.
	 *
	 * @param configTimes number of times the getSharedState API is invoked for Configuration
	 * @param identityTimes number of times the getXDMSharedState API is invoked for Edge Identity
	 * @param consentTimes number of times the getXDMSharedState API is invoked for Consent
	 */
	private void verifyGetSharedStateCalls(final int configTimes, final int identityTimes, final int consentTimes) {
		if (configTimes >= 0) {
			verify(mockExtensionApi, times(configTimes))
				.getSharedState(
					eq(EdgeConstants.SharedState.CONFIGURATION),
					any(Event.class),
					any(Boolean.class),
					any(SharedStateResolution.class)
				);
		}

		if (identityTimes >= 0) {
			verify(mockExtensionApi, times(identityTimes))
				.getXDMSharedState(
					eq(EdgeConstants.SharedState.IDENTITY),
					any(Event.class),
					any(Boolean.class),
					any(SharedStateResolution.class)
				);
		}

		if (consentTimes >= 0) {
			verify(mockExtensionApi, times(consentTimes))
				.getXDMSharedState(
					eq(EdgeConstants.SharedState.CONSENT),
					any(Event.class),
					any(Boolean.class),
					any(SharedStateResolution.class)
				);
		}
	}

	private void mockHubSharedState(final SharedStateResult sharedStateResult) {
		when(
			mockExtensionApi.getSharedState(
				eq(EdgeConstants.SharedState.HUB),
				isNull(),
				any(Boolean.class),
				any(SharedStateResolution.class)
			)
		)
			.thenReturn(sharedStateResult);
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
		final Map<String, Object> registeredExtensions = new HashMap<>();

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
