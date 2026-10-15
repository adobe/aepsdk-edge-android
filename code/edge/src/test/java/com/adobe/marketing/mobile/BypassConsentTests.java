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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.MockConnection;
import com.adobe.marketing.mobile.util.MockNetworkService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

public class BypassConsentTests {

	private static final String RESPONSE_HANDLES =
		"{\"handle\":[" +
		"{\"type\":\"locationHint:result\",\"payload\":[{\"scope\":\"EdgeNetwork\",\"hint\":\"or2\",\"ttlSeconds\":1800}]}," +
		"{\"type\":\"state:store\",\"payload\":[{\"key\":\"device-state\",\"value\":\"value-123\",\"maxAge\":1800}]}]}";
	private final Map<String, Object> configuration = Collections.singletonMap("edge.configId", "config");
	private final Map<String, Object> identity = identity("sdk", "other");
	private EdgeNetworkService network;
	private NetworkResponseHandler responses;
	private EdgeSharedStateCallback sharedStateCallback;
	private EdgeHitProcessor processor;
	private MockedStatic<MobileCore> core;
	private final List<Event> outcomes = new ArrayList<>();

	@Before
	public void setup() {
		core = mockStatic(MobileCore.class);
		core
			.when(() -> MobileCore.dispatchEvent(any(Event.class)))
			.thenAnswer(call -> {
				outcomes.add(call.getArgument(0));
				return null;
			});
		network = mock(EdgeNetworkService.class);
		responses = mock(NetworkResponseHandler.class);
		sharedStateCallback = mock(EdgeSharedStateCallback.class);
		processor = newProcessor();
	}

	@After
	public void teardown() {
		core.close();
	}

	private static Map<String, Object> identity(String first, String second) {
		Map<String, Object> sdk = new HashMap<>();
		sdk.put("id", first);
		sdk.put("primary", false);
		Map<String, Object> other = new HashMap<>();
		other.put("id", second);
		other.put("primary", true);
		return Collections.singletonMap("identityMap", Collections.singletonMap("ECID", Arrays.asList(sdk, other)));
	}

	private Event request(Map<String, Object> data) {
		return new Event.Builder(
			"Device Attributes",
			EdgeConstants.EventType.EDGE_BYPASS_CONSENT,
			EventSource.REQUEST_CONTENT
		)
			.setEventData(data)
			.build();
	}

	private DataEntity entity(Event event, Map<String, Object> config, Map<String, Object> state) {
		DataEntity entity = new EdgeDataEntity(event, config, state).toDataEntity();
		assertNotNull(entity);
		return entity;
	}

	private EdgeHitProcessor newProcessor() {
		return new EdgeHitProcessor(responses, network, mock(NamedCollection.class), sharedStateCallback, null);
	}

	private boolean process(DataEntity entity) {
		boolean[] done = { false };
		processor.processHit(entity, result -> done[0] = result);
		return done[0];
	}

	private void stubNetwork(BypassConsentNetworkOutcome... results) {
		when(network.buildUrl(any(), anyString(), anyString()))
			.thenReturn("https://edge.adobedc.net/ee/v1/mobile/device-attributes");
		final int[] attempt = { 0 };
		when(network.doBypassConsentRequest(anyString(), anyString(), anyMap()))
			.thenAnswer(invocation -> {
				BypassConsentNetworkOutcome result = results[Math.min(attempt[0]++, results.length - 1)];
				return result != null ? result : BypassConsentNetworkOutcome.retry(5);
			});
	}

	private void assertOutcome(Event original, String status, String reason, Integer httpStatus) {
		List<Event> bypassResults = new ArrayList<>();
		for (Event dispatched : outcomes) {
			if (
				EdgeConstants.EventType.EDGE_BYPASS_CONSENT.equals(dispatched.getType()) &&
				EventSource.RESPONSE_CONTENT.equals(dispatched.getSource())
			) {
				bypassResults.add(dispatched);
			}
		}
		assertEquals(1, bypassResults.size());
		Event outcome = bypassResults.get(0);
		assertEquals(original.getUniqueIdentifier(), outcome.getParentID());
		assertNull(outcome.getResponseID());
		assertEquals(EdgeConstants.EventType.EDGE_BYPASS_CONSENT, outcome.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, outcome.getSource());
		assertEquals(status, outcome.getEventData().get("status"));
		assertEquals(reason, outcome.getEventData().get("reason"));
		assertEquals(httpStatus, outcome.getEventData().get("httpStatus"));
	}

	private void useNetworkResponse(
		MockConnection connection,
		NamedCollection collection,
		EdgeStateCallback stateCallback
	) {
		MockNetworkService networking = new MockNetworkService();
		networking.setDefaultResponse(connection);
		responses = spy(new NetworkResponseHandler(collection, stateCallback));
		processor =
			new EdgeHitProcessor(
				responses,
				new EdgeNetworkService(networking),
				collection,
				sharedStateCallback,
				stateCallback
			);
	}

	@Test
	public void bypassResponse_doesNotMutatePrimaryStateOrLocationHint() {
		NamedCollection collection = mock(NamedCollection.class);
		EdgeStateCallback stateCallback = mock(EdgeStateCallback.class);
		MockConnection connection = new MockConnection(202, RESPONSE_HANDLES, null);
		useNetworkResponse(connection, collection, stateCallback);
		Event event = request(Collections.singletonMap("timezone", "UTC"));

		assertTrue(process(entity(event, configuration, identity)));

		assertOutcome(event, "success", null, 202);
		assertEquals(1, outcomes.size());
		verify(stateCallback, never()).setLocationHint(anyString(), anyInt());
		// Request building may prune expired stores (writes {}); response handles must never be persisted.
		verify(collection, never())
			.setMap(
				eq(EdgeConstants.DataStoreKeys.STORE_PAYLOADS),
				argThat(stores -> stores != null && !stores.isEmpty())
			);
		verifyNoInteractions(responses);
		assertEquals(1, connection.closeCalledTimes);
	}

	@Test
	public void bypassResults_doNotDispatchStandardResponseErrorOrCompletionEvents() {
		final String body =
			"{\"handle\":[{\"type\":\"device-attributes:result\",\"eventIndex\":0,\"payload\":[{\"id\":\"registered\"}]}]," +
			"\"errors\":[{\"status\":400,\"title\":\"Device attribute rejected\",\"report\":{\"eventIndex\":0}}]," +
			"\"warnings\":[{\"status\":400,\"title\":\"Warning\",\"report\":{\"eventIndex\":0}}]}";
		for (int code : new int[] { 200, 202, 204, 207, 400 }) {
			outcomes.clear();
			final boolean success = code == 200 || code == 202 || code == 204;
			useNetworkResponse(
				new MockConnection(code, body, body),
				mock(NamedCollection.class),
				mock(EdgeStateCallback.class)
			);
			Event event = request(Collections.singletonMap("timezone", "UTC"));

			assertTrue(process(entity(event, configuration, identity)));

			assertOutcome(event, success ? "success" : "failed", success ? null : "network", code);
			assertEquals("only the bypass result is dispatched for HTTP " + code, 1, outcomes.size());
			verifyNoInteractions(responses);
		}
	}

	@Test
	public void responseHandles_afterIdentityReset_preserveExistingSuppression() {
		NamedCollection collection = mock(NamedCollection.class);
		EdgeStateCallback stateCallback = mock(EdgeStateCallback.class);
		useNetworkResponse(new MockConnection(202, RESPONSE_HANDLES, null), collection, stateCallback);
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		responses.setLastResetDate(event.getTimestamp() + 1);

		assertTrue(process(entity(event, configuration, identity)));

		assertOutcome(event, "success", null, 202);
		verify(stateCallback, never()).setLocationHint(anyString(), anyInt());
		verify(collection, never())
			.setMap(
				eq(EdgeConstants.DataStoreKeys.STORE_PAYLOADS),
				argThat(stores -> stores.containsKey("device-state"))
			);
	}

	@Test
	public void invalidUrl_dropsWithoutRegisteringWaitingEvent() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		when(network.buildUrl(any(), anyString(), anyString()))
			.thenReturn("http://edge.adobedc.net/ee/v1/mobile/device-attributes");

		assertTrue(process(entity(event, configuration, identity)));

		assertOutcome(event, "dropped", "invalidPayload", null);
		verifyNoInteractions(responses);
		verify(network, never()).doBypassConsentRequest(anyString(), anyString(), anyMap());
	}

	@Test
	public void emptyOrMalformedResponse_stillReportsHTTPAcceptance() {
		for (String body : Arrays.asList(null, "", "not-json")) {
			outcomes.clear();
			NamedCollection collection = mock(NamedCollection.class);
			EdgeStateCallback stateCallback = mock(EdgeStateCallback.class);
			useNetworkResponse(new MockConnection(202, body, null), collection, stateCallback);
			Event event = request(Collections.singletonMap("timezone", "UTC"));

			assertTrue(process(entity(event, configuration, identity)));

			assertOutcome(event, "success", null, 202);
			assertEquals(1, outcomes.size());
			verify(stateCallback, never()).setLocationHint(anyString(), anyInt());
			verifyNoInteractions(responses);
		}
	}

	@Test
	public void terminalError_reportsFailureWithoutProcessingErrorBody() {
		final String errorBody = "{\"title\":\"Forbidden\",\"status\":403}";
		useNetworkResponse(
			new MockConnection(403, null, errorBody),
			mock(NamedCollection.class),
			mock(EdgeStateCallback.class)
		);
		Event event = request(Collections.singletonMap("timezone", "UTC"));

		assertTrue(process(entity(event, configuration, identity)));

		assertOutcome(event, "failed", "network", 403);
		assertEquals(1, outcomes.size());
		verifyNoInteractions(responses);
	}

	@Test
	public void retry_completesForRetryWithoutTerminalOutcomeOrWaitingEvents() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		DataEntity persisted = entity(event, configuration, identity);
		stubNetwork(
			BypassConsentNetworkOutcome.retry(17),
			BypassConsentNetworkOutcome.retry(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS),
			BypassConsentNetworkOutcome.success(202)
		);

		assertFalse(process(persisted));
		assertEquals(17, processor.retryInterval(persisted));
		assertFalse(process(persisted));
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, processor.retryInterval(persisted));
		assertTrue(outcomes.isEmpty());
		assertTrue(process(persisted));
		assertOutcome(event, "success", null, 202);
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, processor.retryInterval(persisted));
		verify(network, times(3)).doBypassConsentRequest(anyString(), anyString(), anyMap());
		verifyNoInteractions(responses);
	}

	@Test
	public void createResult_preservesCorrelationAndDoesNotDispatch() {
		Event original = request(Collections.singletonMap("timezone", "UTC"));
		for (String status : Arrays.asList("success", "failed", "dropped")) {
			Event result = BypassConsentOutcome.createResult(original, status, null, null);
			assertEquals(original.getUniqueIdentifier(), result.getParentID());
			assertNull(result.getResponseID());
			assertEquals(EdgeConstants.EventType.EDGE_BYPASS_CONSENT, result.getType());
			assertEquals(EventSource.RESPONSE_CONTENT, result.getSource());
			assertEquals(Collections.singletonMap("status", status), result.getEventData());
		}
		Event failure = BypassConsentOutcome.createResult(original, "failed", "network", 403);
		assertEquals("network", failure.getEventData().get("reason"));
		assertEquals(403, failure.getEventData().get("httpStatus"));
		assertTrue(outcomes.isEmpty());
		core.verifyNoInteractions();
	}

	@Test
	public void createDropResult_usesPersistedIdentifierWithoutInventingEventCorrelation() {
		Event result = BypassConsentOutcome.createDropResult("persisted-request-id");
		assertEquals(EdgeConstants.EventType.EDGE_BYPASS_CONSENT, result.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, result.getSource());
		assertNull(result.getParentID());
		assertNull(result.getResponseID());
		Map<String, Object> expected = new HashMap<>();
		expected.put("status", "dropped");
		expected.put("reason", "invalidPayload");
		expected.put("requestEventId", "persisted-request-id");
		assertEquals(expected, result.getEventData());
		core.verifyNoInteractions();
	}

	@Test
	public void productionQueueWiring_notifiesOnlyCorruptBypassHitsAndContinuesProcessing() {
		final List<EdgeHitProcessor> ordinaryProcessors = new ArrayList<>();
		final List<EdgeHitProcessor> bypassProcessors = new ArrayList<>();
		try (
			MockedStatic<ServiceProvider> services = mockStatic(ServiceProvider.class);
			MockedConstruction<EdgeBatchingHitQueue> ordinaryQueues = mockConstruction(
				EdgeBatchingHitQueue.class,
				(queue, context) -> ordinaryProcessors.add((EdgeHitProcessor) context.arguments().get(1))
			);
			MockedConstruction<BypassConsentHitQueue> bypassQueues = mockConstruction(
				BypassConsentHitQueue.class,
				(queue, context) -> bypassProcessors.add((EdgeHitProcessor) context.arguments().get(1))
			)
		) {
			ServiceProvider provider = mock(ServiceProvider.class, RETURNS_DEEP_STUBS);
			services.when(ServiceProvider::getInstance).thenReturn(provider);
			EdgeExtension extension = new EdgeExtension(mock(ExtensionApi.class));
			assertEquals(1, ordinaryQueues.constructed().size());
			assertEquals(1, bypassQueues.constructed().size());
			EdgeHitProcessor ordinaryProcessor = ordinaryProcessors.get(0);
			EdgeHitProcessor bypassProcessor = bypassProcessors.get(0);
			assertNotSame(ordinaryProcessor, bypassProcessor);

			int count = 0;
			for (String data : Arrays.asList(null, "", "{", "{}")) {
				DataEntity corrupted = new DataEntity("corrupt-" + count, new Date(), data);
				ordinaryProcessor.processHit(corrupted, finished -> assertTrue(finished));
				assertEquals(count, outcomes.size());
				bypassProcessor.processHit(corrupted, finished -> assertTrue(finished));
				assertEquals(++count, outcomes.size());
				Event outcome = outcomes.get(count - 1);
				assertEquals(EdgeConstants.EventType.EDGE_BYPASS_CONSENT, outcome.getType());
				assertEquals(EventSource.RESPONSE_CONTENT, outcome.getSource());
				assertEquals("dropped", outcome.getEventData().get("status"));
				assertEquals("invalidPayload", outcome.getEventData().get("reason"));
				assertEquals(corrupted.getUniqueIdentifier(), outcome.getEventData().get("requestEventId"));
				assertNull(outcome.getParentID());
				assertNull(outcome.getResponseID());
			}

			outcomes.clear();
			Event valid = request(Collections.singletonMap("timezone", "UTC"));
			bypassProcessor.processHit(
				entity(valid, Collections.emptyMap(), identity),
				finished -> assertTrue(finished)
			);
			assertOutcome(valid, "dropped", "missingConfig", null);
			assertFalse(outcomes.get(0).getEventData().containsKey("requestEventId"));
			extension.onUnregistered();
			verify(ordinaryQueues.constructed().get(0)).close();
			verify(bypassQueues.constructed().get(0)).close();
		}
	}

	@Test
	public void productionQueueWiring_unavailableBypassDataQueue_failsWithoutAffectingOrdinaryQueue() {
		try (
			MockedStatic<ServiceProvider> services = mockStatic(ServiceProvider.class);
			MockedConstruction<EdgeBatchingHitQueue> ordinaryQueues = mockConstruction(EdgeBatchingHitQueue.class);
			MockedConstruction<BypassConsentHitQueue> bypassQueues = mockConstruction(BypassConsentHitQueue.class)
		) {
			ServiceProvider provider = mock(ServiceProvider.class, RETURNS_DEEP_STUBS);
			services.when(ServiceProvider::getInstance).thenReturn(provider);
			when(
				provider
					.getDataQueueService()
					.getDataQueue(EdgeConstants.EXTENSION_NAME + EdgeConstants.DataQueueLabels.BYPASS_CONSENT_SUFFIX)
			)
				.thenReturn(null);
			ExtensionApi api = mock(ExtensionApi.class);
			when(api.getSharedState(eq(EdgeConstants.SharedState.CONFIGURATION), any(), eq(false), any()))
				.thenReturn(new SharedStateResult(SharedStateStatus.SET, configuration));
			when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
				.thenReturn(new SharedStateResult(SharedStateStatus.SET, identity));
			EdgeExtension extension = new EdgeExtension(api);
			assertEquals(1, ordinaryQueues.constructed().size());
			assertTrue(bypassQueues.constructed().isEmpty());
			HitQueuing ordinary = ordinaryQueues.constructed().get(0);
			Event event = request(Collections.singletonMap("timezone", "UTC"));

			extension.handleBypassConsentRequest(event);

			assertOutcome(event, "failed", "queueUnavailable", null);
			assertEquals(1, outcomes.size());
			verify(ordinary, never()).queue(any(DataEntity.class));
			extension.onUnregistered();
			verify(ordinary).close();
		}
	}

	@Test
	public void legacyQueueConstructor_rejectsBypassButStillQueuesOrdinaryEvents() {
		ExtensionApi api = mock(ExtensionApi.class);
		when(api.getSharedState(eq(EdgeConstants.SharedState.CONFIGURATION), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, configuration));
		when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, identity));
		HitQueuing ordinary = mock(HitQueuing.class);
		when(ordinary.queue(any(DataEntity.class))).thenReturn(true);
		EdgeExtension extension = new EdgeExtension(api, ordinary);
		Event bypass = request(Collections.singletonMap("timezone", "UTC"));
		Event experience = new Event.Builder("Experience", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(Collections.singletonMap("xdm", Collections.singletonMap("eventType", "test")))
			.build();

		extension.handleBypassConsentRequest(bypass);
		extension.handleExperienceEventRequest(experience);

		assertOutcome(bypass, "failed", "queueUnavailable", null);
		assertEquals(1, outcomes.size());
		ArgumentCaptor<DataEntity> queued = ArgumentCaptor.forClass(DataEntity.class);
		verify(ordinary).queue(queued.capture());
		assertEquals(
			experience.getUniqueIdentifier(),
			EdgeDataEntity.fromDataEntity(queued.getValue()).getEvent().getUniqueIdentifier()
		);
		extension.onUnregistered();
		verify(ordinary).close();
	}

	@Test
	public void retryThen202_emitsExactlyOneOutcome_preservesBothECIDsAndCorrelation() throws Exception {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		DataEntity persisted = entity(event, configuration, identity);
		stubNetwork(BypassConsentNetworkOutcome.retry(17), BypassConsentNetworkOutcome.success(202));
		assertFalse(process(persisted));
		assertEquals(17, processor.retryInterval(persisted));
		assertTrue(outcomes.isEmpty());
		// Re-decodes the persisted original event rather than inventing a request correlation.
		assertTrue(process(persisted));
		assertOutcome(event, "success", null, 202);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(network, times(2)).doBypassConsentRequest(anyString(), body.capture(), anyMap());
		JSONObject payload = new JSONObject(body.getValue());
		assertEquals(2, payload.getJSONObject("xdm").getJSONObject("identityMap").getJSONArray("ECID").length());
		assertEquals(
			"sdk",
			payload
				.getJSONObject("xdm")
				.getJSONObject("identityMap")
				.getJSONArray("ECID")
				.getJSONObject(0)
				.getString("id")
		);
		assertEquals(
			"other",
			payload
				.getJSONObject("xdm")
				.getJSONObject("identityMap")
				.getJSONArray("ECID")
				.getJSONObject(1)
				.getString("id")
		);
	}

	@Test
	public void identityResetAfterRestoredHit_sendsWithQueuedIdentity() throws Exception {
		ExtensionApi api = mock(ExtensionApi.class);
		HitQueuing bypass = mock(HitQueuing.class);
		when(api.getSharedState(eq(EdgeConstants.SharedState.CONFIGURATION), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, configuration));
		when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, identity));
		when(bypass.queue(any())).thenReturn(true);
		EdgeExtension extension = new EdgeExtension(api, mock(HitQueuing.class), bypass);
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		extension.handleBypassConsentRequest(event);
		ArgumentCaptor<DataEntity> queued = ArgumentCaptor.forClass(DataEntity.class);
		verify(bypass).queue(queued.capture());
		DataEntity persisted = queued.getValue();
		stubNetwork(BypassConsentNetworkOutcome.retry(5), BypassConsentNetworkOutcome.success(202));
		assertFalse(process(persisted));
		assertTrue(outcomes.isEmpty());
		when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, identity("newSDK", "other")));
		processor = newProcessor();
		assertTrue(process(persisted));
		assertOutcome(event, "success", null, 202);
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(network, times(2)).doBypassConsentRequest(anyString(), body.capture(), anyMap());
		for (String attempt : body.getAllValues()) {
			assertEquals(
				"sdk",
				new JSONObject(attempt)
					.getJSONObject("xdm")
					.getJSONObject("identityMap")
					.getJSONArray("ECID")
					.getJSONObject(0)
					.getString("id")
			);
		}
		verify(api, times(1)).getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any());
	}

	@Test
	public void currentIdentityUnavailable_sendsWithQueuedIdentity() {
		ExtensionApi api = mock(ExtensionApi.class);
		HitQueuing bypass = mock(HitQueuing.class);
		when(api.getSharedState(eq(EdgeConstants.SharedState.CONFIGURATION), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, configuration));
		when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, identity));
		when(bypass.queue(any())).thenReturn(true);
		EdgeExtension extension = new EdgeExtension(api, mock(HitQueuing.class), bypass);
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		extension.handleBypassConsentRequest(event);
		ArgumentCaptor<DataEntity> queued = ArgumentCaptor.forClass(DataEntity.class);
		verify(bypass).queue(queued.capture());
		stubNetwork(BypassConsentNetworkOutcome.success(204));
		for (SharedStateResult unavailable : Arrays.asList(
			null,
			new SharedStateResult(SharedStateStatus.PENDING, identity),
			new SharedStateResult(SharedStateStatus.SET, Collections.<String, Object>emptyMap())
		)) {
			when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
				.thenReturn(unavailable);
			outcomes.clear();
			assertTrue(process(queued.getValue()));
			assertOutcome(event, "success", null, 204);
		}
		verify(network, times(3)).doBypassConsentRequest(anyString(), anyString(), anyMap());
		verify(api, times(1)).getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any());
	}

	@Test
	public void terminalHTTPFailure_emitsOneFailedOutcome() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		stubNetwork(BypassConsentNetworkOutcome.failure(403));
		assertTrue(process(entity(event, configuration, identity)));
		assertOutcome(event, "failed", "network", 403);
	}

	@Test
	public void nullAttributes_survivePersistenceAndAppearAsJSONNullAtRootAndNestedPaths() throws Exception {
		Map<String, Object> tokens = new HashMap<>();
		tokens.put("pushNotification", null);
		Map<String, Object> data = new HashMap<>();
		data.put("timezone", null);
		data.put("tokens", tokens);

		Event event = request(data);
		DataEntity persisted = entity(event, configuration, identity);
		EdgeDataEntity restored = EdgeDataEntity.fromDataEntity(persisted);
		assertNotNull(restored);
		Map<String, Object> restoredData = restored.getEvent().getEventData();
		assertTrue(restoredData.containsKey("timezone"));
		assertTrue(restoredData.containsKey("tokens"));
		assertTrue(restoredData.get("timezone") == null || restoredData.get("timezone") == JSONObject.NULL);
		Map<?, ?> restoredTokens = (Map<?, ?>) restoredData.get("tokens");
		assertTrue(restoredTokens.containsKey("pushNotification"));
		assertTrue(
			restoredTokens.get("pushNotification") == null || restoredTokens.get("pushNotification") == JSONObject.NULL
		);

		stubNetwork(BypassConsentNetworkOutcome.success(202));
		assertTrue(process(persisted));
		ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
		verify(network).doBypassConsentRequest(anyString(), body.capture(), anyMap());
		JSONObject payload = new JSONObject(body.getValue());
		assertTrue(payload.has("timezone"));
		assertTrue(payload.isNull("timezone"));
		assertTrue(payload.getJSONObject("tokens").has("pushNotification"));
		assertTrue(payload.getJSONObject("tokens").isNull("pushNotification"));
	}

	@Test
	public void nullPreservingSerialization_preservesNativeJsonValuesAndExplicitNulls() throws Exception {
		JSONObject object = new JSONObject().put("pushNotification", JSONObject.NULL);
		JSONArray array = new JSONArray().put(JSONObject.NULL).put("token");
		Map<String, Object> values = new HashMap<>();
		values.put("tokens", object);
		values.put("values", array);
		values.put("clear", JSONObject.NULL);

		JSONObject result = EdgeJson.toJsonObjectPreservingNulls(values);

		assertEquals(3, result.length());
		assertSame(object, result.getJSONObject("tokens"));
		assertTrue(result.getJSONObject("tokens").has("pushNotification"));
		assertTrue(result.getJSONObject("tokens").isNull("pushNotification"));
		assertSame(array, result.getJSONArray("values"));
		assertEquals(2, result.getJSONArray("values").length());
		assertTrue(result.getJSONArray("values").isNull(0));
		assertEquals("token", result.getJSONArray("values").getString(1));
		assertTrue(result.has("clear"));
		assertTrue(result.isNull("clear"));
	}

	@Test
	public void nullPreservingSerialization_handlesObjectPrimitiveAndEmptyArrays() throws Exception {
		Map<String, Object> values = new HashMap<>();
		values.put(
			"objects",
			new Object[] { null, Collections.singletonMap("pushNotification", null), new String[] { "UTC", null } }
		);
		values.put("numbers", new int[] { 1, 2 });
		values.put("empty", new Object[0]);

		JSONObject result = EdgeJson.toJsonObjectPreservingNulls(values);

		assertEquals(3, result.length());
		JSONArray objects = result.getJSONArray("objects");
		assertEquals(3, objects.length());
		assertTrue(objects.isNull(0));
		assertTrue(objects.getJSONObject(1).has("pushNotification"));
		assertTrue(objects.getJSONObject(1).isNull("pushNotification"));
		assertEquals(2, objects.getJSONArray(2).length());
		assertEquals("UTC", objects.getJSONArray(2).getString(0));
		assertTrue(objects.getJSONArray(2).isNull(1));
		JSONArray numbers = result.getJSONArray("numbers");
		assertEquals(2, numbers.length());
		assertEquals(1, numbers.getInt(0));
		assertEquals(2, numbers.getInt(1));
		assertEquals(0, result.getJSONArray("empty").length());
	}

	@Test
	public void missingConfig_emitsExplicitFailure() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		assertTrue(process(entity(event, null, identity)));
		assertOutcome(event, "dropped", "missingConfig", null);
		verifyNoInteractions(network);
	}

	@Test
	public void invalidPayload_emitsExplicitFailure() {
		Event event = request(null);
		assertTrue(process(entity(event, configuration, identity)));
		assertOutcome(event, "dropped", "invalidPayload", null);
		verifyNoInteractions(network);
	}

	@Test
	public void ingressConfigurationUnavailable_emitsCorrelatedDropWithoutQueuing() {
		for (SharedStateResult unavailable : Arrays.asList(
			null,
			new SharedStateResult(SharedStateStatus.PENDING, configuration)
		)) {
			assertIngressFailure(
				request(Collections.singletonMap("timezone", "UTC")),
				unavailable,
				new SharedStateResult(SharedStateStatus.SET, identity),
				"dropped",
				"missingConfig"
			);
		}
	}

	@Test
	public void ingressConfigIdMissingOrEmpty_emitsCorrelatedDropWithoutQueuing() {
		for (Map<String, Object> invalidConfig : Arrays.asList(
			Collections.<String, Object>emptyMap(),
			Collections.<String, Object>singletonMap("edge.configId", ""),
			Collections.<String, Object>singletonMap("edge.configId", null)
		)) {
			assertIngressFailure(
				request(Collections.singletonMap("timezone", "UTC")),
				new SharedStateResult(SharedStateStatus.SET, invalidConfig),
				new SharedStateResult(SharedStateStatus.SET, identity),
				"dropped",
				"missingConfig"
			);
		}
	}

	@Test
	public void ingressIdentityUnavailable_emitsCorrelatedDropWithoutQueuing() {
		for (SharedStateResult unavailable : Arrays.asList(
			null,
			new SharedStateResult(SharedStateStatus.PENDING, identity)
		)) {
			assertIngressFailure(
				request(Collections.singletonMap("timezone", "UTC")),
				new SharedStateResult(SharedStateStatus.SET, configuration),
				unavailable,
				"dropped",
				"invalidPayload"
			);
		}
	}

	@Test
	public void ingressSerializationFailure_emitsCorrelatedDropWithoutQueuing() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		try (MockedStatic<EventCoder> coder = mockStatic(EventCoder.class)) {
			coder.when(() -> EventCoder.encode(any(Event.class))).thenReturn("{");
			assertIngressFailure(
				event,
				new SharedStateResult(SharedStateStatus.SET, configuration),
				new SharedStateResult(SharedStateStatus.SET, identity),
				"dropped",
				"invalidPayload"
			);
		}
	}

	private void assertIngressFailure(
		Event event,
		SharedStateResult configurationState,
		SharedStateResult identityState,
		String status,
		String reason
	) {
		outcomes.clear();
		ExtensionApi api = mock(ExtensionApi.class);
		HitQueuing ordinary = mock(HitQueuing.class);
		HitQueuing bypass = mock(HitQueuing.class);
		when(api.getSharedState(eq(EdgeConstants.SharedState.CONFIGURATION), any(), eq(false), any()))
			.thenReturn(configurationState);
		when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
			.thenReturn(identityState);
		EdgeExtension extension = new EdgeExtension(api, ordinary, bypass);

		extension.handleBypassConsentRequest(event);

		assertOutcome(event, status, reason, null);
		assertEquals(1, outcomes.size());
		assertFalse(outcomes.get(0).getEventData().containsKey("httpStatus"));
		verify(ordinary, never()).queue(any(DataEntity.class));
		verify(bypass, never()).queue(any(DataEntity.class));
	}

	@Test
	public void earlyDrops_emitOneOutcome_andOrdinaryQueueIsIndependent() {
		ExtensionApi api = mock(ExtensionApi.class);
		HitQueuing ordinary = mock(HitQueuing.class);
		when(api.getSharedState(eq(EdgeConstants.SharedState.CONFIGURATION), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, configuration));
		when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, identity));
		EdgeExtension extension = new EdgeExtension(api, ordinary, null);
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		extension.handleBypassConsentRequest(event);
		assertOutcome(event, "failed", "queueUnavailable", null);
		outcomes.clear();
		extension.handleExperienceEventRequest(
			new Event.Builder("ordinary", EventType.EDGE, EventSource.REQUEST_CONTENT)
				.setEventData(Collections.singletonMap("xdm", Collections.singletonMap("test", "data")))
				.build()
		);
		verify(ordinary).queue(any());
		assertTrue(outcomes.isEmpty());
		outcomes.clear();
		// The bypass queue is checked before the payload, matching iOS.
		Event empty = request(null);
		extension.handleBypassConsentRequest(empty);
		assertOutcome(empty, "failed", "queueUnavailable", null);
	}

	@Test
	public void enqueueFailure_emitsExplicitFailure() {
		ExtensionApi api = mock(ExtensionApi.class);
		HitQueuing bypass = mock(HitQueuing.class);
		when(api.getSharedState(eq(EdgeConstants.SharedState.CONFIGURATION), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, configuration));
		when(api.getXDMSharedState(eq(EdgeConstants.SharedState.IDENTITY), any(), eq(false), any()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, identity));
		EdgeExtension extension = new EdgeExtension(api, mock(HitQueuing.class), bypass);
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		extension.handleBypassConsentRequest(event);
		assertOutcome(event, "failed", "queueUnavailable", null);
	}

	@Test
	public void bypassResultConstants_preserveWireValues() {
		assertEquals("Edge bypass result", EdgeConstants.EventName.BYPASS_RESULT);
		assertEquals("status", EdgeConstants.EventDataKeys.BypassResult.STATUS);
		assertEquals("reason", EdgeConstants.EventDataKeys.BypassResult.REASON);
		assertEquals("httpStatus", EdgeConstants.EventDataKeys.BypassResult.HTTP_STATUS);
		assertEquals("requestEventId", EdgeConstants.EventDataKeys.BypassResult.REQUEST_EVENT_ID);
		assertEquals("success", EdgeConstants.BypassResult.Status.SUCCESS);
		assertEquals("failed", EdgeConstants.BypassResult.Status.FAILED);
		assertEquals("dropped", EdgeConstants.BypassResult.Status.DROPPED);
		assertEquals("missingConfig", EdgeConstants.BypassResult.Reason.MISSING_CONFIG);
		assertEquals("invalidPayload", EdgeConstants.BypassResult.Reason.INVALID_PAYLOAD);
		assertEquals("queueUnavailable", EdgeConstants.BypassResult.Reason.QUEUE_UNAVAILABLE);
		assertEquals("network", EdgeConstants.BypassResult.Reason.NETWORK);
	}

	@Test
	public void dropResult_isUnchained_andCarriesRequestEventId() {
		Event result = BypassConsentOutcome.createDropResult("persisted-id");

		assertEquals(EdgeConstants.EventName.BYPASS_RESULT, result.getName());
		assertEquals(EdgeConstants.EventType.EDGE_BYPASS_CONSENT, result.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, result.getSource());
		assertNull(result.getParentID());
		assertEquals("dropped", result.getEventData().get("status"));
		assertEquals("invalidPayload", result.getEventData().get("reason"));
		assertEquals("persisted-id", result.getEventData().get("requestEventId"));
	}

	@Test
	public void bypassResult_isChainedToRequest() {
		Event request = request(Collections.singletonMap("timezone", "UTC"));
		Event result = BypassConsentOutcome.createResult(request, "failed", "network", 207);

		assertEquals(EdgeConstants.EventName.BYPASS_RESULT, result.getName());
		assertEquals(request.getUniqueIdentifier(), result.getParentID());
		assertEquals(207, result.getEventData().get("httpStatus"));
	}
}
