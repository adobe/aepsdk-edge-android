/*
  Copyright 2026 Adobe. All rights reserved.
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitQueuing;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

public class DeviceAttributesEdgeExtensionTests {

	private ExtensionApi mockExtensionApi;
	private HitQueuing mockHitQueue;
	private HitQueuing mockDeviceAttributesQueue;
	private EdgeExtension edgeExtension;
	private Map<String, Object> configData;
	private Map<String, Object> identityState;

	@Before
	public void setUp() {
		mockExtensionApi = mock(ExtensionApi.class);
		mockHitQueue = mock(HitQueuing.class);
		mockDeviceAttributesQueue = mock(HitQueuing.class);
		when(mockHitQueue.queue(any())).thenReturn(true);
		when(mockDeviceAttributesQueue.queue(any())).thenReturn(true);
		edgeExtension = new EdgeExtension(mockExtensionApi, mockHitQueue, mockDeviceAttributesQueue);

		configData = new HashMap<>();
		configData.put(EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID, "datastream-id");
		identityState = map("identityMap", map("ECID", Arrays.asList(map("id", "ecid-value"))));
	}

	@Test
	public void testIsDeviceAttributesRequestEvent_trueOnlyForDeviceAttributesTypeAndRequestContentSource() {
		assertTrue(DeviceAttributesEvents.isDeviceAttributesRequestEvent(deviceAttributesRequestEvent(data())));
		assertTrue(
			DeviceAttributesEvents.isDeviceAttributesRequestEvent(
				new Event.Builder("e", "COM.ADOBE.EVENTTYPE.EDGEBYPASSCONSENT", EventSource.REQUEST_CONTENT).build()
			)
		);
		assertFalse(
			DeviceAttributesEvents.isDeviceAttributesRequestEvent(
				new Event.Builder("e", DeviceAttributesConstants.EVENT_TYPE, EventSource.RESPONSE_CONTENT).build()
			)
		);
		assertFalse(DeviceAttributesEvents.isDeviceAttributesRequestEvent(experienceEvent()));
	}

	@Test
	public void testOnRegistered_registersDeviceAttributesListener() {
		edgeExtension.onRegistered();

		verify(mockExtensionApi)
			.registerEventListener(
				eq(DeviceAttributesConstants.EVENT_TYPE),
				eq(EventSource.REQUEST_CONTENT),
				any(ExtensionEventListener.class)
			);
	}

	@Test
	public void testOnUnregistered_closesBothQueues() {
		edgeExtension.onUnregistered();

		verify(mockHitQueue).close();
		verify(mockDeviceAttributesQueue).close();
	}

	@Test
	public void testOnUnregistered_withoutDeviceAttributesQueue_closesPrimaryQueue() {
		new EdgeExtension(mockExtensionApi, mockHitQueue, null).onUnregistered();

		verify(mockHitQueue).close();
	}

	@Test
	public void testInit_doesNotStartDeviceAttributesQueue() {
		verifyNoInteractions(mockDeviceAttributesQueue);
	}

	@Test
	public void testHandleDeviceAttributesRequest_queuesOnDeviceAttributesQueueOnly_regardlessOfCollectConsentNo() {
		mockSharedStates(configData, identityState);
		edgeExtension.state.updateCurrentConsent(ConsentStatus.NO);
		final Event event = deviceAttributesRequestEvent(data());

		edgeExtension.handleDeviceAttributesRequest(event);

		final DataEntity entity = verifyQueuedOnDeviceAttributesQueue(1).get(0);
		assertEquals(event.getUniqueIdentifier(), entity.getUniqueIdentifier());
		assertEquals(event.getTimestamp(), entity.getTimestamp().getTime());
		final EdgeDataEntity edgeEntity = EdgeDataEntity.fromDataEntity(entity);
		assertEquals("datastream-id", edgeEntity.getConfiguration().get("edge.configId"));
		assertEquals(identityState, edgeEntity.getIdentityMap());
		verify(mockHitQueue, never()).queue(any());
		verifyNoDeviceAttributesResponse();
	}

	@Test
	public void testDeviceAttributesRequestUsesDedicatedQueueForEveryCollectConsent() {
		mockSharedStates(configData, identityState);

		for (final ConsentStatus status : ConsentStatus.values()) {
			edgeExtension.state.updateCurrentConsent(status);
			edgeExtension.handleDeviceAttributesRequest(deviceAttributesRequestEvent(data()));
		}

		verifyQueuedOnDeviceAttributesQueue(ConsentStatus.values().length);
		verify(mockHitQueue, never()).queue(any());
		verify(mockDeviceAttributesQueue, never()).clear();
		verify(mockDeviceAttributesQueue, never()).suspend();
		verifyNoDeviceAttributesResponse();
	}

	@Test
	public void testReadyForEvent_deviceAttributesMatchesExperienceAndConsentEventReadiness() {
		final Map<String, Object> hub = map("extensions", new HashMap<>());
		final Object[][] cases = {
			{ null, null, null },
			{ hub, null, null },
			{ hub, new SharedStateResult(SharedStateStatus.PENDING, null), null },
			{ hub, new SharedStateResult(SharedStateStatus.SET, configData), null },
			{
				hub,
				new SharedStateResult(SharedStateStatus.SET, configData),
				new SharedStateResult(SharedStateStatus.PENDING, null),
			},
			{
				hub,
				new SharedStateResult(SharedStateStatus.SET, configData),
				new SharedStateResult(SharedStateStatus.SET, identityState),
			},
		};
		final Event consentEvent = new Event.Builder("consent", EventType.EDGE, EventSource.UPDATE_CONSENT)
			.setEventData(data())
			.build();

		for (final Object[] testCase : cases) {
			setUp();
			mockHubSharedState(
				testCase[0] != null
					? new SharedStateResult(SharedStateStatus.SET, (Map<String, Object>) testCase[0])
					: null
			);
			mockSharedStateResults((SharedStateResult) testCase[1], (SharedStateResult) testCase[2]);

			final boolean experienceReady = edgeExtension.readyForEvent(experienceEvent());
			assertEquals(experienceReady, edgeExtension.readyForEvent(consentEvent));
			assertEquals(experienceReady, edgeExtension.readyForEvent(deviceAttributesRequestEvent(data())));
		}

		// the last case is ready
		assertTrue(edgeExtension.readyForEvent(deviceAttributesRequestEvent(data())));
	}

	@Test
	public void testReadyForEvent_bootsStateAndStartsDeviceAttributesQueueOnce() {
		mockHubSharedState(new SharedStateResult(SharedStateStatus.SET, map("extensions", new HashMap<>())));
		mockSharedStates(configData, identityState);

		assertTrue(edgeExtension.readyForEvent(deviceAttributesRequestEvent(data())));
		assertTrue(edgeExtension.readyForEvent(experienceEvent()));

		verify(mockDeviceAttributesQueue, times(1)).beginProcessing();
	}

	@Test
	public void testReadyForEvent_bootupNotComplete_doesNotStartDeviceAttributesQueue() {
		mockHubSharedState(new SharedStateResult(SharedStateStatus.PENDING, null));
		mockSharedStates(configData, identityState);

		assertFalse(edgeExtension.readyForEvent(deviceAttributesRequestEvent(data())));

		verify(mockDeviceAttributesQueue, never()).beginProcessing();
	}

	@Test
	public void testHandleResetComplete_doesNotTouchDeviceAttributesQueue() {
		final Event resetEvent = new Event.Builder("reset", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE)
			.build();

		edgeExtension.handleResetComplete(resetEvent);

		verify(mockHitQueue).queue(any());
		verifyNoInteractions(mockDeviceAttributesQueue);
	}

	@Test
	public void testDeviceAttributesQueueAcceptsNewIdentityRequestsAfterReset() {
		mockSharedStates(configData, identityState);
		edgeExtension.handleResetComplete(
			new Event.Builder("reset", EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE).build()
		);
		final Event event = deviceAttributesRequestEvent(data());

		edgeExtension.handleDeviceAttributesRequest(event);

		assertEquals(event.getUniqueIdentifier(), verifyQueuedOnDeviceAttributesQueue(1).get(0).getUniqueIdentifier());
		verifyNoDeviceAttributesResponse();
	}

	@Test
	public void testDeviceAttributesQueueCreationFailurePreservesOrdinaryQueueing() {
		edgeExtension = new EdgeExtension(mockExtensionApi, mockHitQueue, null);
		mockSharedStates(configData, identityState);

		edgeExtension.handleExperienceEventRequest(experienceEvent());

		verify(mockHitQueue, times(1)).queue(any());
		verifyNoDeviceAttributesResponse();
	}

	@Test
	public void testDeviceAttributesMissingQueueProducesCorrelatedFailure() {
		edgeExtension = new EdgeExtension(mockExtensionApi, mockHitQueue, null);
		mockSharedStates(configData, identityState);
		final Event event = deviceAttributesRequestEvent(data());

		edgeExtension.handleDeviceAttributesRequest(event);

		assertDeviceAttributesResponse(
			event,
			DeviceAttributesConstants.Status.FAILED,
			DeviceAttributesConstants.Reason.QUEUE_UNAVAILABLE
		);
		verify(mockHitQueue, never()).queue(any());
	}

	@Test
	public void testDeviceAttributesFailedEnqueueProducesCorrelatedFailure() {
		when(mockDeviceAttributesQueue.queue(any())).thenReturn(false);
		mockSharedStates(configData, identityState);
		final Event event = deviceAttributesRequestEvent(data());

		edgeExtension.handleDeviceAttributesRequest(event);

		assertDeviceAttributesResponse(
			event,
			DeviceAttributesConstants.Status.FAILED,
			DeviceAttributesConstants.Reason.QUEUE_UNAVAILABLE
		);
	}

	@Test
	public void testOrdinaryFailedEnqueueDoesNotProduceDeviceAttributesResponse() {
		when(mockHitQueue.queue(any())).thenReturn(false);
		mockSharedStates(configData, identityState);

		edgeExtension.handleExperienceEventRequest(experienceEvent());

		verify(mockHitQueue, times(1)).queue(any());
		verifyNoDeviceAttributesResponse();
	}

	@Test
	public void testDeviceAttributesEmptyPayloadProducesCorrelatedDrop() {
		mockSharedStates(configData, identityState);

		for (final Map<String, Object> data : Arrays.<Map<String, Object>>asList(null, new HashMap<>())) {
			clearInvocations(mockExtensionApi);
			final Event event = deviceAttributesRequestEvent(data);

			edgeExtension.handleDeviceAttributesRequest(event);

			assertDeviceAttributesResponse(
				event,
				DeviceAttributesConstants.Status.DROPPED,
				DeviceAttributesConstants.Reason.INVALID_PAYLOAD
			);
		}

		verify(mockDeviceAttributesQueue, never()).queue(any());
	}

	@Test
	public void testDeviceAttributesMissingConfigurationProducesCorrelatedDrop() {
		final Object[] configs = { null, new HashMap<>(), map("edge.configId", "") };

		for (final Object config : configs) {
			clearInvocations(mockExtensionApi);
			mockSharedStates((Map<String, Object>) config, identityState);
			final Event event = deviceAttributesRequestEvent(data());

			edgeExtension.handleDeviceAttributesRequest(event);

			assertDeviceAttributesResponse(
				event,
				DeviceAttributesConstants.Status.DROPPED,
				DeviceAttributesConstants.Reason.MISSING_CONFIG
			);
		}

		verify(mockDeviceAttributesQueue, never()).queue(any());
	}

	@Test
	public void testDeviceAttributesMissingIdentityProducesCorrelatedDrop() {
		mockSharedStates(configData, null);
		final Event event = deviceAttributesRequestEvent(data());

		edgeExtension.handleDeviceAttributesRequest(event);

		assertDeviceAttributesResponse(
			event,
			DeviceAttributesConstants.Status.DROPPED,
			DeviceAttributesConstants.Reason.INVALID_PAYLOAD
		);
		verify(mockDeviceAttributesQueue, never()).queue(any());
	}

	@Test
	public void testDeviceAttributesResponsesRetainRequestCorrelation() {
		final Event request = deviceAttributesRequestEvent(data());

		final Event result = DeviceAttributesEvents.createResponse(
			request,
			DeviceAttributesConstants.Status.FAILED,
			DeviceAttributesConstants.Reason.NETWORK,
			500
		);

		assertEquals(request.getUniqueIdentifier(), result.getParentID());
		assertNull(result.getResponseID());
		assertEquals(DeviceAttributesConstants.EVENT_NAME_RESPONSE, result.getName());
		assertEquals(DeviceAttributesConstants.EVENT_TYPE, result.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, result.getSource());
		assertEquals(500, result.getEventData().get("httpStatus"));
	}

	@Test
	public void testDeviceAttributesResponseConstantsPreserveWireValues() {
		assertEquals("com.adobe.eventType.edgeBypassConsent", DeviceAttributesConstants.EVENT_TYPE);
		assertEquals("Device Attributes Response", DeviceAttributesConstants.EVENT_NAME_RESPONSE);
		assertEquals("success", DeviceAttributesConstants.Status.SUCCESS);
		assertEquals("failed", DeviceAttributesConstants.Status.FAILED);
		assertEquals("dropped", DeviceAttributesConstants.Status.DROPPED);
		assertEquals("missingConfig", DeviceAttributesConstants.Reason.MISSING_CONFIG);
		assertEquals("invalidPayload", DeviceAttributesConstants.Reason.INVALID_PAYLOAD);
		assertEquals("queueUnavailable", DeviceAttributesConstants.Reason.QUEUE_UNAVAILABLE);
		assertEquals("network", DeviceAttributesConstants.Reason.NETWORK);
		assertEquals("mobile/device-attributes", EdgeNetworkService.RequestType.DEVICE_ATTRIBUTES.type);
	}

	@Test
	public void testDeviceAttributesDecodeDropPreservesUnchainedWireContract() {
		final Event result = DeviceAttributesEvents.createDroppedResponse("stored-id");

		assertNull(result.getParentID());
		assertNull(result.getResponseID());
		final Map<String, Object> expected = new HashMap<>();
		expected.put("status", "dropped");
		expected.put("reason", "invalidPayload");
		expected.put("requestEventId", "stored-id");
		assertEquals(expected, result.getEventData());
	}

	// Helpers

	@Test
	public void testDeviceAttributesUnexpectedExceptionProducesCorrelatedDrop() {
		mockSharedStates(configData, identityState);
		final Event event = deviceAttributesRequestEvent(data());

		try (
			MockedConstruction<EdgeDataEntity> ignored = mockConstruction(
				EdgeDataEntity.class,
				(mockEntity, context) -> when(mockEntity.toDataEntity()).thenThrow(new RuntimeException("boom"))
			)
		) {
			edgeExtension.handleDeviceAttributesRequest(event);
		}

		assertDeviceAttributesResponse(
			event,
			DeviceAttributesConstants.Status.DROPPED,
			DeviceAttributesConstants.Reason.INVALID_PAYLOAD
		);
		verify(mockDeviceAttributesQueue, never()).queue(any());
	}

	private List<DataEntity> verifyQueuedOnDeviceAttributesQueue(final int count) {
		final ArgumentCaptor<DataEntity> captor = ArgumentCaptor.forClass(DataEntity.class);
		verify(mockDeviceAttributesQueue, times(count)).queue(captor.capture());
		return captor.getAllValues();
	}

	private void verifyNoDeviceAttributesResponse() {
		verify(mockExtensionApi, never()).dispatch(any(Event.class));
	}

	private void assertDeviceAttributesResponse(final Event request, final String status, final String reason) {
		final ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(mockExtensionApi, times(1)).dispatch(captor.capture());
		final Event result = captor.getValue();
		assertEquals(DeviceAttributesConstants.EVENT_NAME_RESPONSE, result.getName());
		assertEquals(DeviceAttributesConstants.EVENT_TYPE, result.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, result.getSource());
		assertEquals(request.getUniqueIdentifier(), result.getParentID());
		final Map<String, Object> expected = new HashMap<>();
		expected.put("status", status);
		expected.put("reason", reason);
		assertEquals(expected, result.getEventData());
	}

	private void mockSharedStates(final Map<String, Object> config, final Map<String, Object> identity) {
		mockSharedStateResults(
			config != null ? new SharedStateResult(SharedStateStatus.SET, config) : null,
			identity != null ? new SharedStateResult(SharedStateStatus.SET, identity) : null
		);
	}

	private void mockSharedStateResults(final SharedStateResult config, final SharedStateResult identity) {
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
	}

	private void mockHubSharedState(final SharedStateResult result) {
		when(
			mockExtensionApi.getSharedState(
				eq(EdgeConstants.SharedState.HUB),
				isNull(),
				any(Boolean.class),
				any(SharedStateResolution.class)
			)
		)
			.thenReturn(result);
	}

	private static Event deviceAttributesRequestEvent(final Map<String, Object> data) {
		return new Event.Builder(
			"Device Attributes Request",
			DeviceAttributesConstants.EVENT_TYPE,
			EventSource.REQUEST_CONTENT
		)
			.setEventData(data)
			.build();
	}

	private static Event experienceEvent() {
		return new Event.Builder("experience", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(map("xdm", map("eventType", "test")))
			.build();
	}

	private static Map<String, Object> data() {
		final Map<String, Object> data = new HashMap<>();
		data.put("app", map("id", "com.adobe.app"));
		data.put("timezone", "America/Los_Angeles");
		return data;
	}

	private static Map<String, Object> map(final String key, final Object value) {
		final Map<String, Object> map = new HashMap<>();
		map.put(key, value);
		return map;
	}
}
