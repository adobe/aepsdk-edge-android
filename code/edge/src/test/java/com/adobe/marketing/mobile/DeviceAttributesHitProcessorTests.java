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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NetworkCallback;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.Networking;
import com.adobe.marketing.mobile.util.FakeNamedCollection;
import com.adobe.marketing.mobile.util.JSONUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class DeviceAttributesHitProcessorTests {

	private static final String DATASTREAM_ID = "datastream-id";
	private static final String QUEUED_ECID = "ecid-value";
	private static final String SUCCESS_BODY_WITH_HANDLES =
		"{\"requestId\":\"r1\",\"handle\":[" +
		"{\"type\":\"locationHint:result\",\"payload\":[{\"scope\":\"Target\",\"hint\":\"35\",\"ttlSeconds\":1800}," +
		"{\"scope\":\"EdgeNetwork\",\"hint\":\"or2\",\"ttlSeconds\":1800}]}," +
		"{\"type\":\"state:store\",\"payload\":[{\"key\":\"kndctr_org_cluster\",\"value\":\"or2\",\"maxAge\":1800}]}," +
		"{\"type\":\"personalization:decisions\",\"payload\":[{\"id\":\"p1\"}]}" +
		"]}";

	private Networking mockNetworking;
	private EdgeStateCallback mockStateCallback;
	private EdgeSharedStateCallback mockSharedStateCallback;
	private FakeNamedCollection fakeNamedCollection;
	private List<Event> dispatchedEvents;
	private List<NetworkRequest> sentRequests;
	private List<Boolean> processingResults;
	private DeviceAttributesHitProcessor hitProcessor;
	private Event requestEvent;
	private DataEntity entity;
	private Map<String, Object> latestIdentityMap;

	@Before
	public void setUp() {
		mockNetworking = mock(Networking.class);
		mockStateCallback = mock(EdgeStateCallback.class);
		mockSharedStateCallback = mock(EdgeSharedStateCallback.class);
		fakeNamedCollection = new FakeNamedCollection();
		dispatchedEvents = new ArrayList<>();
		sentRequests = new ArrayList<>();
		processingResults = new ArrayList<>();

		final Map<String, Object> implementationDetails = new HashMap<>();
		implementationDetails.put(
			"implementationDetails",
			singletonMap("name", "https://ns.adobe.com/experience/mobilesdk/android")
		);
		when(mockStateCallback.getImplementationDetails()).thenReturn(implementationDetails);
		latestIdentityMap = identityMapWithEcids(QUEUED_ECID);

		hitProcessor =
			new DeviceAttributesHitProcessor(
				new DeviceAttributesNetworkService(mockNetworking),
				mockSharedStateCallback,
				mockStateCallback,
				fakeNamedCollection,
				() -> latestIdentityMap,
				dispatchedEvents::add
			);

		requestEvent = buildRequestEvent(defaultEventData());
		entity = buildEntity(requestEvent, defaultConfig());
	}

	// Response classification

	@Test
	public void testProcessHit_200_dispatchesSuccessAndRemovesHit() {
		respondWith(200, "{}", null);

		processHit(entity);

		assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 200);
		assertEquals(Arrays.asList(true), processingResults);
	}

	@Test
	public void testProcessHit_202_dispatchesSuccess() {
		respondWith(202, null, null);

		processHit(entity);

		assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 202);
		assertEquals(Arrays.asList(true), processingResults);
	}

	@Test
	public void testProcessHit_unsupportedOrUnrecoverableCodes_dispatchFailedNetwork() {
		for (final int code : new int[] { 204, 207, 400, 403, 404, 500 }) {
			setUp();
			respondWith(code, null, null);

			processHit(entity);

			assertResult(DeviceAttributesConstants.Status.FAILED, DeviceAttributesConstants.Reason.NETWORK, code);
			assertEquals(Arrays.asList(true), processingResults);
		}
	}

	@Test
	public void testProcessHit_400ProblemJson_dispatchesFailedNetwork() {
		respondWith(
			400,
			"{\"type\":\"https://ns.adobe.com/aep/errors/EXEG-0003-400\",\"status\":400," +
			"\"title\":\"Invalid request\",\"report\":{\"errors\":[\"Field 'app' is invalid\"]}}",
			null
		);

		processHit(entity);

		assertResult(DeviceAttributesConstants.Status.FAILED, DeviceAttributesConstants.Reason.NETWORK, 400);
	}

	@Test
	public void testProcessHit_recoverableCodes_retryWithoutResponseEvent() {
		for (final int code : new int[] { -1, 408, 429, 502, 503, 504, 507 }) {
			setUp();
			respondWith(code, null, null);

			processHit(entity);

			assertTrue("code " + code, dispatchedEvents.isEmpty());
			assertEquals(Arrays.asList(false), processingResults);
			assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, hitProcessor.retryInterval(entity));
		}
	}

	@Test
	public void testProcessHit_recoverableCodeWithRetryAfter_usesHeaderInterval() {
		respondWith(503, null, "30");

		processHit(entity);

		assertTrue(dispatchedEvents.isEmpty());
		assertEquals(30, hitProcessor.retryInterval(entity));
	}

	@Test
	public void testProcessHit_recoverableCodeWithInvalidRetryAfter_usesDefaultInterval() {
		for (final String retryAfter : new String[] { "0", "-5", "abc", "Wed, 21 Oct 2015 07:28:00 GMT", "" }) {
			setUp();
			respondWith(429, null, retryAfter);

			processHit(entity);

			assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, hitProcessor.retryInterval(entity));
		}
	}

	@Test
	public void testProcessHit_responseCodeMinusOne_offlineOrTimeout_retriesWithoutResponseEvent() {
		// Core returns a connection with response code -1 when the device is offline or the request times out
		respondWith(-1, null, null);

		processHit(entity);

		assertTrue(dispatchedEvents.isEmpty());
		assertEquals(Arrays.asList(false), processingResults);
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, hitProcessor.retryInterval(entity));
	}

	@Test
	public void testProcessHit_nullConnection_dispatchesFailedWithoutHttpStatus() {
		mockNullConnection();

		processHit(entity);

		assertEquals(Arrays.asList(true), processingResults);
		assertEquals(1, dispatchedEvents.size());
		final Map<String, Object> data = dispatchedEvents.get(0).getEventData();
		assertEquals(DeviceAttributesConstants.Status.FAILED, data.get("status"));
		assertEquals(DeviceAttributesConstants.Reason.NETWORK, data.get("reason"));
		assertFalse(data.containsKey("httpStatus"));
	}

	private void mockNullConnection() {
		doAnswer(invocation -> {
				sentRequests.add(invocation.getArgument(0));
				((NetworkCallback) invocation.getArgument(1)).call(null);
				return null;
			})
			.when(mockNetworking)
			.connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
	}

	@Test
	public void testProcessHit_retryThenSuccess_dispatchesExactlyOneResult() {
		respondWith(503, null, "10");
		processHit(entity);
		assertEquals(10, hitProcessor.retryInterval(entity));

		respondWith(200, "{}", null);
		processHit(entity);

		assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 200);
		assertEquals(Arrays.asList(false, true), processingResults);
		assertEquals(2, sentRequests.size());
		// retry interval mapping is cleared once the hit completes
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, hitProcessor.retryInterval(entity));
	}

	// Request

	@Test
	public void testProcessHit_sendsFlatBodyToDeviceAttributesEndpoint() throws Exception {
		respondWith(200, "{}", null);

		processHit(entity);

		assertEquals(1, sentRequests.size());
		final NetworkRequest request = sentRequests.get(0);
		assertTrue(
			request.getUrl(),
			request
				.getUrl()
				.startsWith(
					"https://edge.adobedc.net/ee/v1/mobile/device-attributes?configId=" + DATASTREAM_ID + "&requestId="
				)
		);
		assertEquals(com.adobe.marketing.mobile.services.HttpMethod.POST, request.getMethod());
		assertEquals("application/json", request.getHeaders().get("Content-Type"));
		assertEquals("application/json", request.getHeaders().get("accept"));
		assertNull(request.getHeaders().get(EdgeConstants.NetworkKeys.HEADER_KEY_AEP_VALIDATION_TOKEN));

		final Map<String, Object> body = JSONUtils.toMap(
			new JSONObject(new String(request.getBody(), StandardCharsets.UTF_8))
		);
		assertNull(body.get("events"));
		assertEquals(singletonMap("id", "com.adobe.app"), body.get("app"));
		assertEquals("America/Los_Angeles", body.get("timezone"));
		final Map<String, Object> xdm = (Map<String, Object>) body.get("xdm");
		assertNotNull(xdm.get("implementationDetails"));
		assertNotNull(xdm.get("identityMap"));
		assertNull(body.get("meta"));
	}

	@Test
	public void testProcessHit_usesLocationHintEnvironmentAndCustomDomain() {
		when(mockStateCallback.getLocationHint()).thenReturn("or2");
		final Map<String, Object> config = defaultConfig();
		config.put(EdgeConstants.SharedState.Configuration.EDGE_DOMAIN, "my.domain.com");
		config.put(EdgeConstants.SharedState.Configuration.EDGE_REQUEST_ENVIRONMENT, "pre-prod");
		respondWith(200, "{}", null);

		processHit(buildEntity(requestEvent, config));

		assertTrue(
			sentRequests.get(0).getUrl(),
			sentRequests
				.get(0)
				.getUrl()
				.startsWith("https://my.domain.com/ee-pre-prd/or2/v1/mobile/device-attributes?configId=")
		);
	}

	@Test
	public void testProcessHit_sendsAssuranceHeaderWhenAvailable() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.ASSURANCE), isNull()))
			.thenReturn(
				new SharedStateResult(
					SharedStateStatus.SET,
					singletonMap(EdgeConstants.SharedState.Assurance.INTEGRATION_ID, "assurance-id")
				)
			);
		respondWith(200, "{}", null);

		processHit(entity);

		assertEquals(
			"assurance-id",
			sentRequests.get(0).getHeaders().get(EdgeConstants.NetworkKeys.HEADER_KEY_AEP_VALIDATION_TOKEN)
		);
	}

	// Response handles

	@Test
	public void testProcessHit_success_appliesLocationHintAndStoreHandlesOnly() {
		respondWith(200, SUCCESS_BODY_WITH_HANDLES, null);

		processHit(entity);

		verify(mockStateCallback, times(1)).setLocationHint("or2", 1800);
		assertNotNull(new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores());
		assertEquals(1, new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores().size());
		// only the response event, no Edge response or content complete events
		assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 200);
	}

	@Test
	public void testProcessHit_successWithMalformedOrEmptyBody_stillSucceeds() {
		for (final String body : new String[] { null, "", "not json", "[]", "{\"handle\":\"x\"}" }) {
			setUp();
			respondWith(200, body, null);

			processHit(entity);

			assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 200);
			verify(mockStateCallback, never()).setLocationHint(anyString(), anyInt());
		}
	}

	@Test
	public void testProcessHit_failure_doesNotApplyResponseHandles() {
		respondWith(400, SUCCESS_BODY_WITH_HANDLES, null);

		processHit(entity);

		verify(mockStateCallback, never()).setLocationHint(anyString(), anyInt());
		assertNull(new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores());
		assertResult(DeviceAttributesConstants.Status.FAILED, DeviceAttributesConstants.Reason.NETWORK, 400);
	}

	@Test
	public void testProcessHit_sendsActiveStoresAsMetaState() throws Exception {
		new StoreResponsePayloadManager(fakeNamedCollection)
			.saveStorePayloads(
				Arrays.asList(
					new HashMap<String, Object>() {
						{
							put(EdgeJson.Response.EventHandle.Store.KEY, "kndctr_org_cluster");
							put(EdgeJson.Response.EventHandle.Store.VALUE, "or2");
							put(EdgeJson.Response.EventHandle.Store.MAX_AGE, 1800);
						}
					}
				)
			);
		respondWith(200, "{}", null);

		processHit(entity);

		final Map<String, Object> body = JSONUtils.toMap(
			new JSONObject(new String(sentRequests.get(0).getBody(), StandardCharsets.UTF_8))
		);
		final Map<String, Object> state = (Map<String, Object>) ((Map<String, Object>) body.get("meta")).get("state");
		final List<Object> entries = (List<Object>) state.get("entries");
		assertEquals(1, entries.size());
		assertEquals("kndctr_org_cluster", ((Map<String, Object>) entries.get(0)).get("key"));
	}

	// ECID check

	@Test
	public void testProcessHit_sameEcid_isSent() {
		respondWith(200, "{}", null);

		processHit(entity);

		assertEquals(1, sentRequests.size());
		assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 200);
		assertEquals(Arrays.asList(true), processingResults);
	}

	@Test
	public void testProcessHit_changedEcid_dispatchesDroppedIdentityChangedWithoutRequest() {
		latestIdentityMap = identityMapWithEcids("new-ecid");
		respondWith(200, "{}", null);

		processHit(entity);

		verify(mockNetworking, never()).connectAsync(any(), any());
		assertResult(DeviceAttributesConstants.Status.DROPPED, DeviceAttributesConstants.Reason.IDENTITY_CHANGED, null);
		assertEquals(Arrays.asList(true), processingResults);
	}

	@Test
	public void testProcessHit_latestIdentityUnavailable_retriesWithoutRequestOrResponseEvent() {
		latestIdentityMap = null;
		respondWith(200, "{}", null);

		processHit(entity);

		verify(mockNetworking, never()).connectAsync(any(), any());
		assertTrue(dispatchedEvents.isEmpty());
		assertEquals(Arrays.asList(false), processingResults);
	}

	@Test
	public void testProcessHit_ecidChangesBetweenRetries_isDroppedOnNextAttempt() {
		respondWith(503, null, null);
		processHit(entity);
		assertEquals(Arrays.asList(false), processingResults);
		assertTrue(dispatchedEvents.isEmpty());

		latestIdentityMap = identityMapWithEcids("new-ecid");
		processHit(entity);

		assertEquals(1, sentRequests.size());
		assertResult(DeviceAttributesConstants.Status.DROPPED, DeviceAttributesConstants.Reason.IDENTITY_CHANGED, null);
		assertEquals(Arrays.asList(false, true), processingResults);
	}

	@Test
	public void testProcessHit_legacyEcidAtIndexOneDiffers_isSent() {
		latestIdentityMap = identityMapWithEcids(QUEUED_ECID, "legacy-ecid");
		respondWith(200, SUCCESS_BODY_WITH_HANDLES, null);

		processHit(entity);

		assertEquals(1, sentRequests.size());
		assertEquals(1, new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores().size());
		assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 200);
	}

	@Test
	public void testProcessHit_ecidChangesDuringRequest_dispatchesResponseButIgnoresStoreHandles() {
		doAnswer(invocation -> {
				sentRequests.add(invocation.getArgument(0));
				latestIdentityMap = identityMapWithEcids("new-ecid");
				((NetworkCallback) invocation.getArgument(1)).call(
						mockConnection(200, SUCCESS_BODY_WITH_HANDLES, null)
					);
				return null;
			})
			.when(mockNetworking)
			.connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));

		processHit(entity);

		verify(mockStateCallback, times(1)).setLocationHint("or2", 1800);
		assertNull(new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores());
		assertResult(DeviceAttributesConstants.Status.SUCCESS, null, 200);
	}

	// Drops

	@Test
	public void testProcessHit_missingDatastreamId_dispatchesDroppedMissingConfig() {
		processHit(buildEntity(requestEvent, new HashMap<>()));

		verify(mockNetworking, never()).connectAsync(any(), any());
		assertResult(DeviceAttributesConstants.Status.DROPPED, DeviceAttributesConstants.Reason.MISSING_CONFIG, null);
		assertEquals(Arrays.asList(true), processingResults);
	}

	@Test
	public void testProcessHit_emptyPayload_dispatchesDroppedInvalidPayload() {
		when(mockStateCallback.getImplementationDetails()).thenReturn(null);
		final Map<String, Object> data = new HashMap<>();
		data.put("xdm", singletonMap("producer", "value"));
		requestEvent = buildRequestEvent(data);
		latestIdentityMap = new HashMap<>();

		processHit(new EdgeDataEntity(requestEvent, defaultConfig(), new HashMap<>()).toDataEntity());

		verify(mockNetworking, never()).connectAsync(any(), any());
		assertResult(DeviceAttributesConstants.Status.DROPPED, DeviceAttributesConstants.Reason.INVALID_PAYLOAD, null);
		assertEquals(Arrays.asList(true), processingResults);
	}

	@Test
	public void testProcessHit_undecodableEntity_dispatchesUnchainedDrop() {
		final DataEntity corrupt = new DataEntity("corrupt-id", new Date(), "not json");

		processHit(corrupt);

		verify(mockNetworking, never()).connectAsync(any(), any());
		assertEquals(1, dispatchedEvents.size());
		final Event result = dispatchedEvents.get(0);
		assertEquals(DeviceAttributesConstants.EVENT_NAME_RESPONSE, result.getName());
		assertEquals(DeviceAttributesConstants.EVENT_TYPE, result.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, result.getSource());
		assertNull(result.getParentID());
		assertEquals(DeviceAttributesConstants.Status.DROPPED, result.getEventData().get("status"));
		assertEquals(DeviceAttributesConstants.Reason.INVALID_PAYLOAD, result.getEventData().get("reason"));
		assertEquals("corrupt-id", result.getEventData().get("requestEventId"));
		assertFalse(result.getEventData().containsKey("httpStatus"));
		assertEquals(Arrays.asList(true), processingResults);
	}

	// Helpers

	private void processHit(final DataEntity dataEntity) {
		hitProcessor.processHit(dataEntity, processingResults::add);
	}

	private void respondWith(final int code, final String body, final String retryAfter) {
		doAnswer(invocation -> {
				sentRequests.add(invocation.getArgument(0));
				((NetworkCallback) invocation.getArgument(1)).call(mockConnection(code, body, retryAfter));
				return null;
			})
			.when(mockNetworking)
			.connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
	}

	private static HttpConnecting mockConnection(final int code, final String body, final String retryAfter) {
		final HttpConnecting connection = mock(HttpConnecting.class);
		when(connection.getResponseCode()).thenReturn(code);

		if (body != null) {
			when(connection.getInputStream())
				.thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
			when(connection.getErrorStream())
				.thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		}

		when(connection.getResponsePropertyValue(EdgeConstants.NetworkKeys.HEADER_KEY_RETRY_AFTER))
			.thenReturn(retryAfter);
		return connection;
	}

	/**
	 * Asserts exactly one response event (E4) was dispatched, chained to the request event (E3).
	 */
	private void assertResult(final String status, final String reason, final Integer httpStatus) {
		assertEquals(1, dispatchedEvents.size());
		final Event result = dispatchedEvents.get(0);
		assertEquals(DeviceAttributesConstants.EVENT_NAME_RESPONSE, result.getName());
		assertEquals(DeviceAttributesConstants.EVENT_TYPE, result.getType());
		assertEquals(EventSource.RESPONSE_CONTENT, result.getSource());
		assertEquals(requestEvent.getUniqueIdentifier(), result.getParentID());
		assertEquals(status, result.getEventData().get(DeviceAttributesConstants.EventDataKeys.STATUS));
		assertEquals(reason, result.getEventData().get(DeviceAttributesConstants.EventDataKeys.REASON));
		assertEquals(httpStatus, result.getEventData().get(DeviceAttributesConstants.EventDataKeys.HTTP_STATUS));
	}

	private static Event buildRequestEvent(final Map<String, Object> data) {
		return new Event.Builder(
			"Device Attributes Request",
			DeviceAttributesConstants.EVENT_TYPE,
			EventSource.REQUEST_CONTENT
		)
			.setEventData(data)
			.build();
	}

	private static DataEntity buildEntity(final Event event, final Map<String, Object> config) {
		final Map<String, Object> identityState = singletonMap("identityMap", identityMapWithEcids(QUEUED_ECID));
		return new EdgeDataEntity(event, config, identityState).toDataEntity();
	}

	/**
	 * @return an identityMap whose ECID list holds the given ids in order (index 0 is the current ECID)
	 */
	private static Map<String, Object> identityMapWithEcids(final String... ecids) {
		final List<Object> items = new ArrayList<>();

		for (final String ecid : ecids) {
			items.add(singletonMap("id", ecid));
		}

		return singletonMap("ECID", items);
	}

	private static Map<String, Object> defaultEventData() {
		final Map<String, Object> data = new HashMap<>();
		data.put("app", singletonMap("id", "com.adobe.app"));
		data.put("tokens", singletonMap("fcm", "token"));
		data.put("timezone", "America/Los_Angeles");
		return data;
	}

	private static Map<String, Object> defaultConfig() {
		final Map<String, Object> config = new HashMap<>();
		config.put(EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID, DATASTREAM_ID);
		return config;
	}

	private static Map<String, Object> singletonMap(final String key, final Object value) {
		final Map<String, Object> map = new HashMap<>();
		map.put(key, value);
		return map;
	}
}
