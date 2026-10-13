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
import static org.mockito.Mockito.*;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.NamedCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

public class BypassConsentTests {

	private final Map<String, Object> configuration = Collections.singletonMap("edge.configId", "config");
	private final Map<String, Object> identity = identity("sdk", "other");
	private SharedStateResult latest;
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
		latest = new SharedStateResult(SharedStateStatus.SET, identity);
		network = mock(EdgeNetworkService.class);
		responses = mock(NetworkResponseHandler.class);
		sharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(String owner, Event event) {
					return null;
				}

				@Override
				public SharedStateResult getXDMSharedState(String owner, Event event) {
					assertEquals(EdgeConstants.SharedState.IDENTITY, owner);
					assertNull(event);
					return latest;
				}

				@Override
				public void createSharedState(Map<String, Object> state, Event event) {}
			};
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

	private void stubNetwork(RetryResult... results) {
		when(network.buildUrl(any(), anyString(), anyString()))
			.thenReturn("https://edge.adobedc.net/ee/v1/mobile/device-attributes");
		when(network.doBypassConsentRequest(anyString(), anyString(), anyMap()))
			.thenReturn(results[0], Arrays.copyOfRange(results, 1, results.length));
	}

	private void assertOutcome(Event original, String status, String reason, Integer httpStatus) {
		assertEquals(1, outcomes.size());
		Event outcome = outcomes.get(0);
		assertEquals(original.getUniqueIdentifier(), outcome.getParentID());
		assertNull(outcome.getResponseID());
		assertEquals(EdgeConstants.EventType.EDGE_BYPASS_CONSENT, outcome.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, outcome.getSource());
		assertEquals(status, outcome.getEventData().get("status"));
		assertEquals(reason, outcome.getEventData().get("reason"));
		assertEquals(httpStatus, outcome.getEventData().get("httpStatus"));
		verifyNoInteractions(responses);
	}

	@Test
	public void retryThen202_emitsExactlyOneOutcome_preservesBothECIDsAndCorrelation() throws Exception {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		DataEntity persisted = entity(event, configuration, identity);
		stubNetwork(
			new RetryResult(EdgeNetworkService.NetworkRequestOutcome.RETRY, 17).withHttpStatus(503),
			new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0).withHttpStatus(202)
		);
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
	public void identityResetAfterRestoredHit_dropsOldHitWithoutResending() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		DataEntity persisted = entity(event, configuration, identity);
		stubNetwork(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.RETRY, 5));
		assertFalse(process(persisted));
		assertTrue(outcomes.isEmpty());
		latest = new SharedStateResult(SharedStateStatus.SET, identity("newSDK", "other"));
		processor = newProcessor();
		assertTrue(process(persisted));
		assertOutcome(event, "dropped", "identityChanged", null);
		verify(network, times(1)).doBypassConsentRequest(anyString(), anyString(), anyMap());
	}

	@Test
	public void latestIdentityUnavailable_retriesWithoutNetworkOrOutcome_thenSends() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		DataEntity persisted = entity(event, configuration, identity);
		stubNetwork(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0).withHttpStatus(204));
		for (SharedStateResult unavailable : Arrays.asList(
			null,
			new SharedStateResult(SharedStateStatus.PENDING, identity),
			new SharedStateResult(SharedStateStatus.SET, Collections.<String, Object>emptyMap())
		)) {
			latest = unavailable;
			assertFalse(process(persisted));
			assertTrue(outcomes.isEmpty());
		}
		verify(network, never()).doBypassConsentRequest(anyString(), anyString(), anyMap());
		latest = new SharedStateResult(SharedStateStatus.SET, identity("sdk", "changedSecondary"));
		assertTrue(process(persisted));
		assertOutcome(event, "success", null, 204);
	}

	@Test
	public void terminalHTTPFailure_emitsOneFailedOutcome() {
		Event event = request(Collections.singletonMap("timezone", "UTC"));
		stubNetwork(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0).withHttpStatus(403));
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

		stubNetwork(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0).withHttpStatus(202));
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
		Event empty = request(null);
		extension.handleBypassConsentRequest(empty);
		assertOutcome(empty, "dropped", "invalidPayload", null);
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
}
