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
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.NamedCollection;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Tests for {@link EdgeHitProcessor#processBatch} — covering the unified single = batch-of-1
 * path, terminal-400 delivery (regression guard), explosion flow, and Consent/Reset terminal-400.
 *
 * <p>Single-event 400s are no longer classified as {@code EXPLODE_400} by (the real)
 * {@link EdgeNetworkService#doRequest}; only a real N&gt;1 batch is. So tests exercising a
 * single-event terminal-400 simulate what the real network layer does — invoking the injected
 * {@link EdgeNetworkService.ResponseCallback}'s {@code onError}/{@code onComplete} — the same way
 * {@code EdgeHitProcessorTests} does for the non-batching build.
 */
@RunWith(MockitoJUnitRunner.class)
public class EdgeHitProcessorBatchTests {

	// Error JSON used as the 400 body in network stubs.
	private static final String ERROR_BODY =
		"{\"type\":\"https://ns.adobe.com/aep/errors/EXEG-0305-400\",\"status\":400,\"title\":\"Bad Request\"}";

	private EdgeHitProcessor hitProcessor;
	private Map<String, Object> edgeConfig;

	private final Map<String, Object> identityMap = new HashMap<String, Object>() {
		{
			put(
				"identityMap",
				new HashMap<String, Object>() {
					{
						put(
							"ECID",
							Collections.singletonList(
								new HashMap<String, Object>() {
									{
										put("id", "test-ecid");
									}
								}
							)
						);
					}
				}
			);
		}
	};

	private static MockedStatic<CompletionCallbacksManager> mockCallbacksManagerStatic;

	@Mock
	EdgeNetworkService mockEdgeNetworkService;

	@Mock
	NetworkResponseHandler mockNetworkResponseHandler;

	@Mock
	NamedCollection mockNamedCollection;

	@Mock
	CompletionCallbacksManager mockCompletionCallbacksManager;

	@Before
	public void setup() {
		mockCallbacksManagerStatic = mockStatic(CompletionCallbacksManager.class);
		mockCallbacksManagerStatic
			.when(CompletionCallbacksManager::getInstance)
			.thenReturn(mockCompletionCallbacksManager);

		edgeConfig = new HashMap<>();
		edgeConfig.put("edge.configId", "test-config-id");
		Map<String, Object> batchingEvent = new HashMap<>();
		batchingEvent.put(EdgeConstants.Batching.XDM_EVENT_TYPE, "test.event");
		batchingEvent.put(EdgeConstants.Batching.ENABLED, true);
		Map<String, Object> batching = new HashMap<>();
		batching.put("events", Collections.singletonList(batchingEvent));
		edgeConfig.put(EdgeConstants.SharedState.Configuration.EDGE_BATCHING, batching);

		hitProcessor =
			new EdgeHitProcessor(mockNetworkResponseHandler, mockEdgeNetworkService, mockNamedCollection, null, null);
	}

	@After
	public void tearDown() {
		mockCallbacksManagerStatic.close();
	}

	// -------------------------------------------------------------------------
	// WI-1b: single ExperienceEvent goes through the batch path (no redirect)
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_singleExperienceEvent_success_returnsOutcomeDone() {
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));
		DataEntity entity = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		assertEquals(0, outcome.getRetryDelaySeconds()); // done -> process immediately, no retry delay
		// Regression guard: DONE must report exactly how many entities were resolved, so the queue
		// removes only those — never a stale/full peeked-window count.
		assertEquals(1, outcome.getRemoveCount());
		// Waiting events registered before the network call
		verify(mockNetworkResponseHandler, times(1)).addWaitingEvents(anyString(), any());
		// No error path triggered
		verify(mockNetworkResponseHandler, never()).processResponseOnError(anyString(), anyString());
		verify(mockNetworkResponseHandler, never()).processResponseOnComplete(anyString());
	}

	@Test
	public void testProcessBatch_twoExperienceEvents_success_resolvedHeadCountIsTwo() {
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));
		DataEntity entity1 = buildExperienceEventEntity();
		DataEntity entity2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(entity1, entity2));

		assertEquals(0, outcome.getRetryDelaySeconds()); // done -> process immediately, no retry delay
		assertEquals(2, outcome.getRemoveCount());
	}

	@Test
	public void testProcessBatch_experienceEventFollowedByConsent_resolvesOnlyLeadingExperienceRun() {
		// Regression guard: a mixed window (ExperienceEvent then Consent) must truncate the batch at
		// the Consent boundary and report a resolvedHeadCount matching only the entities actually
		// sent (1), not the full peeked window (2) — otherwise EdgeBatchingHitQueue's DONE handling
		// would remove the untouched Consent entity from the queue without ever processing it.
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));
		DataEntity experienceEntity = buildExperienceEventEntity();
		DataEntity consentEntity = buildConsentEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(experienceEntity, consentEntity));

		assertEquals(0, outcome.getRetryDelaySeconds()); // done -> process immediately, no retry delay
		assertEquals(1, outcome.getRemoveCount());
		// Only one network request — for the ExperienceEvent alone; the Consent entity was never sent.
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.anyBoolean(),
				any(EdgeNetworkService.ResponseCallback.class)
			);
	}

	@Test
	public void testProcessBatch_twoExperienceEvents_differentConfig_resolvesOnlyHead() {
		// Regression guard: a batched request is built entirely from the head's snapshotted config
		// (see buildExperienceEventHit) — a single request can only carry one datastream ID/override.
		// Two events with different configs must not be combined into one request, or the second
		// would silently lose its own config and be sent under the head's. The run truncates at the
		// mismatch, same as a Consent boundary; the second entity becomes its own head on a later cycle.
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));

		Map<String, Object> otherConfig = new HashMap<>(edgeConfig);
		otherConfig.put("edge.configId", "a-different-config-id");

		DataEntity experienceEntity = buildExperienceEventEntity();
		DataEntity differentConfigEntity = buildExperienceEventEntity(otherConfig);

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(experienceEntity, differentConfigEntity));

		assertEquals(0, outcome.getRetryDelaySeconds()); // done -> process immediately, no retry delay
		assertEquals(1, outcome.getRemoveCount());
		// Only one network request — for the head alone; the differently-configured entity was never sent.
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.anyBoolean(),
				any(EdgeNetworkService.ResponseCallback.class)
			);
	}

	@Test
	public void testProcessBatch_singleExperienceEvent_retry_returnsOutcomeRetry() {
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.RETRY, 5));
		DataEntity entity = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		assertEquals(0, outcome.getRemoveCount()); // retry -> nothing removed
		assertEquals(5, outcome.getRetryDelaySeconds());
		// Waiting events registered before the send
		verify(mockNetworkResponseHandler, times(1)).addWaitingEvents(anyString(), any());
		// ...and removed on RETRY (the retried send re-registers under a fresh requestId), so the old
		// entry does not leak.
		verify(mockNetworkResponseHandler, times(1)).removeWaitingEvents(anyString());
	}

	@Test
	public void testProcessBatch_twoEvents_retry_removesWaitingEventsToAvoidLeak() {
		// A recoverable failure on a real N>1 batch: the N-event waiting-events entry registered under
		// this batch's requestId must be removed on RETRY, otherwise every retry orphans N events.
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.RETRY, 5));
		DataEntity e1 = buildExperienceEventEntity();
		DataEntity e2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(e1, e2));

		assertEquals(0, outcome.getRemoveCount()); // retry -> whole batch stays queued
		assertEquals(5, outcome.getRetryDelaySeconds());
		verify(mockNetworkResponseHandler, times(1)).addWaitingEvents(anyString(), any());
		verify(mockNetworkResponseHandler, times(1)).removeWaitingEvents(anyString());
	}

	// -------------------------------------------------------------------------
	// WI-1b: terminal-400 — regression guard (the core fix)
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_singleExperienceEvent_400_deliversTerminalError() {
		mockNetworkReturnsTerminalError(ERROR_BODY);

		DataEntity entity = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		// Must return DONE (event is fully resolved — error delivered)
		assertEquals(0, outcome.getRetryDelaySeconds()); // done -> process immediately, no retry delay

		// Capture the requestId assigned during addWaitingEvents so we can assert the same
		// requestId is passed to processResponseOnError and processResponseOnComplete.
		ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockNetworkResponseHandler, times(1)).addWaitingEvents(requestIdCaptor.capture(), any());
		final String capturedRequestId = requestIdCaptor.getValue();
		assertNotNull(capturedRequestId);

		// Terminal-400 must deliver the real error body and fire completion
		verify(mockNetworkResponseHandler, times(1)).processResponseOnError(eq(ERROR_BODY), eq(capturedRequestId));
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(eq(capturedRequestId));
	}

	@Test
	public void testProcessBatch_singleExperienceEvent_400_batchingFlagOff_deliversTerminalError() {
		// Explicitly disable batching in config — single-event path must still deliver terminal error
		edgeConfig = new HashMap<>();
		edgeConfig.put("edge.configId", "test-config-id");
		edgeConfig.put("edge.batching.enabled", false);

		mockNetworkReturnsTerminalError(ERROR_BODY);

		DataEntity entity = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		assertEquals(0, outcome.getRetryDelaySeconds()); // done -> process immediately, no retry delay
		verify(mockNetworkResponseHandler, times(1)).processResponseOnError(eq(ERROR_BODY), anyString());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(anyString());
	}

	// -------------------------------------------------------------------------
	// WI-1b: N>1 batch 400 — clean up waiting state, resend each entity individually inline
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_twoEvents_400_resendsIndividually_allResolve_returnsDone() {
		// Batch of 2 gets a 400 — nothing ingested. processBatch removes the batch's waiting-event
		// registration and resends each entity individually (isBatchRequest == false); both resends
		// succeed, so the whole prefix is resolved and returned as done(2). Mirrors aepsdk-edge-ios's
		// EdgeHitProcessor.explodeAndResend.
		when(mockEdgeNetworkService.buildUrl(any(EdgeEndpoint.class), anyString(), anyString()))
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(true),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0));
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(false),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));

		DataEntity entity1 = buildExperienceEventEntity();
		DataEntity entity2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(entity1, entity2));

		// Both resolved individually → remove the whole prefix, continue immediately.
		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());
		assertEquals(2, outcome.getRemoveCount());
		assertEquals(0, outcome.getRetryDelaySeconds());

		// One batch attempt (isBatchRequest == true) + two individual resends (isBatchRequest == false).
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(true),
				any(EdgeNetworkService.ResponseCallback.class)
			);
		verify(mockEdgeNetworkService, times(2))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(false),
				any(EdgeNetworkService.ResponseCallback.class)
			);

		// Batch waiting events cleaned up once; no terminal error delivered for the failed batch id.
		verify(mockNetworkResponseHandler, times(1)).removeWaitingEvents(anyString());
		verify(mockNetworkResponseHandler, never()).processResponseOnError(anyString(), anyString());
	}

	@Test
	public void testProcessBatch_twoEvents_400_recoverableFailureMidResend_returnsPartialRemove() {
		// Batch of 2 gets a 400; the first entity resolves individually, the second hits a recoverable
		// failure. processBatch returns partialRemove(1, retryDelay): remove the one resolved entity and
		// retry the second — never skipped. Mirrors aepsdk-edge-ios's explodeAndResend partial path.
		when(mockEdgeNetworkService.buildUrl(any(EdgeEndpoint.class), anyString(), anyString()))
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(true),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0));
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(false),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0))
			.thenReturn(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.RETRY, 7));

		DataEntity entity1 = buildExperienceEventEntity();
		DataEntity entity2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(entity1, entity2));

		// First resolved (remove 1), second failed recoverably → retry after its own interval (7).
		assertEquals(BatchOutcome.Kind.PARTIAL_REMOVE, outcome.getKind());
		assertEquals(1, outcome.getRemoveCount());
		assertEquals(7, outcome.getRetryDelaySeconds());
	}

	// -------------------------------------------------------------------------
	// WI-1d: Consent/Reset terminal-400 via sendNetworkRequest
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_consentEvent_400_deliversTerminalError() {
		mockNetworkReturnsTerminalError(ERROR_BODY);

		DataEntity entity = buildConsentEventEntity();

		// Consent event goes through processHit → processUpdateConsentEventHit → sendNetworkRequest,
		// identical to a non-batching build — the real network layer delivers the terminal error via
		// the injected callback, simulated here the same way.
		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		assertEquals(0, outcome.getRetryDelaySeconds()); // done -> process immediately, no retry delay

		// Consent events register with addWaitingEvent (singular)
		ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockNetworkResponseHandler, times(1)).addWaitingEvent(requestIdCaptor.capture(), any());

		verify(mockNetworkResponseHandler, times(1))
			.processResponseOnError(eq(ERROR_BODY), eq(requestIdCaptor.getValue()));
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(eq(requestIdCaptor.getValue()));
	}

	// -------------------------------------------------------------------------
	// WI-1e: no waiting-event / callback leak after terminal and batch flows
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_singleEvent_400_allThreeStepsComplete() {
		// addWaitingEvents, processResponseOnError, and processResponseOnComplete must all fire
		// exactly once for a terminal-400 on a batch-of-1.
		mockNetworkReturnsTerminalError(ERROR_BODY);

		DataEntity entity = buildExperienceEventEntity();
		hitProcessor.processBatch(Collections.singletonList(entity));

		verify(mockNetworkResponseHandler, times(1)).addWaitingEvents(anyString(), any());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnError(anyString(), anyString());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(anyString());
	}

	// -------------------------------------------------------------------------
	// Allow-list gate (xdm.eventType) — unit level
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_headNotAllowlisted_processedAloneViaSinglePath() {
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));
		// Batching enabled, but both events carry a non-whitelisted xdm.eventType.
		DataEntity e1 = buildExperienceEventEntity(edgeConfig, "not.allowed");
		DataEntity e2 = buildExperienceEventEntity(edgeConfig, "not.allowed");

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(e1, e2));

		// Head is not batchable → single-entity path → only the head resolved this cycle.
		assertEquals(1, outcome.getRemoveCount());
		// Sent as a single (non-batch) request: isBatchRequest == false.
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(false),
				any(EdgeNetworkService.ResponseCallback.class)
			);
	}

	@Test
	public void testProcessBatch_nonAllowlistedMiddleEvent_truncatesRun() {
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));
		DataEntity allow1 = buildExperienceEventEntity(); // whitelisted "test.event"
		DataEntity allow2 = buildExperienceEventEntity();
		DataEntity notAllowed = buildExperienceEventEntity(edgeConfig, "not.allowed");

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(allow1, allow2, notAllowed));

		// Run truncates at the non-whitelisted event → the leading 2 are batched, the 3rd stays.
		assertEquals(2, outcome.getRemoveCount());
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(true),
				any(EdgeNetworkService.ResponseCallback.class)
			);
	}

	// -------------------------------------------------------------------------
	// Custom request path gate (hasSameRequestConfig) — F1 regression guard
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_sameCustomRequestPath_batchesTogether() {
		// Two batchable events sharing the same custom data.request.path are combined into one batch.
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));
		DataEntity e1 = buildExperienceEventEntity(edgeConfig, "test.event", "/va/v1/samePath");
		DataEntity e2 = buildExperienceEventEntity(edgeConfig, "test.event", "/va/v1/samePath");

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(e1, e2));

		// Same path → both fold into one batch request (isBatchRequest == true).
		assertEquals(2, outcome.getRemoveCount());
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(true),
				any(EdgeNetworkService.ResponseCallback.class)
			);
	}

	@Test
	public void testProcessBatch_differentCustomRequestPath_truncatesRun() {
		// The batch request is built from — and sent to — the head's custom path (getRequestProperties).
		// An event with a different data.request.path must not be folded in, or it would be misrouted to
		// the head's path; so the run truncates and the head is sent alone. Regression guard for the
		// hasSameRequestConfig path check; mirrors aepsdk-edge-ios.
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0));
		DataEntity head = buildExperienceEventEntity(edgeConfig, "test.event", "/va/v1/pathA");
		DataEntity different = buildExperienceEventEntity(edgeConfig, "test.event", "/va/v1/pathB");

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(head, different));

		// Truncates at the differing-path event → only the head resolved this cycle, sent alone.
		assertEquals(1, outcome.getRemoveCount());
		verify(mockEdgeNetworkService, times(1))
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.eq(false),
				any(EdgeNetworkService.ResponseCallback.class)
			);
	}

	// -------------------------------------------------------------------------
	// Batch DROP / drop-whole-batch (no send)
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_batchNonRecoverableDrop_removesAll() {
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0));
		DataEntity e1 = buildExperienceEventEntity();
		DataEntity e2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(e1, e2));

		// Non-recoverable error on a batch → drop all, nothing retried.
		assertEquals(2, outcome.getRemoveCount());
		assertEquals(0, outcome.getRetryDelaySeconds());
	}

	@Test
	public void testProcessBatch_missingDatastreamId_dropsWholeBatchWithoutSending() {
		final Map<String, Object> noConfigId = new HashMap<>(edgeConfig);
		noConfigId.remove("edge.configId");
		DataEntity e1 = buildExperienceEventEntity(noConfigId);
		DataEntity e2 = buildExperienceEventEntity(noConfigId);

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(e1, e2));

		// buildExperienceEventHit returns null (no datastream ID) → whole batch dropped, no network call.
		assertEquals(2, outcome.getRemoveCount());
		verify(mockEdgeNetworkService, never())
			.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.anyBoolean(),
				any(EdgeNetworkService.ResponseCallback.class)
			);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private DataEntity buildExperienceEventEntity() {
		return buildExperienceEventEntity(edgeConfig);
	}

	private DataEntity buildExperienceEventEntity(final Map<String, Object> config) {
		return buildExperienceEventEntity(config, "test.event");
	}

	private DataEntity buildExperienceEventEntity(final Map<String, Object> config, final String xdmEventType) {
		return buildExperienceEventEntity(config, xdmEventType, null);
	}

	private DataEntity buildExperienceEventEntity(
		final Map<String, Object> config,
		final String xdmEventType,
		final String customRequestPath
	) {
		Map<String, Object> xdmData = new HashMap<>();
		xdmData.put("test", "data");
		xdmData.put("eventType", xdmEventType);
		Map<String, Object> eventData = new HashMap<>();
		eventData.put("xdm", xdmData);
		if (customRequestPath != null) {
			Map<String, Object> requestData = new HashMap<>();
			requestData.put("path", customRequestPath);
			eventData.put("request", requestData);
		}

		Event event = new Event.Builder("test-event", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(eventData)
			.build();

		return new EdgeDataEntity(event, config, identityMap).toDataEntity();
	}

	private DataEntity buildConsentEventEntity() {
		Map<String, Object> collectMap = new HashMap<>();
		collectMap.put("val", "y");
		Map<String, Object> consentsMap = new HashMap<>();
		consentsMap.put("collect", collectMap);
		Map<String, Object> eventData = new HashMap<>();
		eventData.put("consents", consentsMap);

		Event event = new Event.Builder("test-consent", EventType.EDGE, EventSource.UPDATE_CONSENT)
			.setEventData(eventData)
			.build();

		return new EdgeDataEntity(event, edgeConfig, identityMap).toDataEntity();
	}

	private void mockNetworkReturns(final RetryResult retryResult) {
		when(mockEdgeNetworkService.buildUrl(any(EdgeEndpoint.class), anyString(), anyString()))
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.anyBoolean(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(retryResult);
	}

	/**
	 * Stubs a single (non-batch) doRequest call to behave exactly like the real
	 * {@code EdgeNetworkService.doRequest} does for a single-event terminal error: invokes the
	 * injected callback's {@code onError} then {@code onComplete} synchronously, and returns a
	 * {@code DROP} result — mirroring what a real 400 (or any other unrecoverable code) does when
	 * {@code isBatchRequest} is {@code false}.
	 */
	private void mockNetworkReturnsTerminalError(final String errorBody) {
		when(mockEdgeNetworkService.buildUrl(any(EdgeEndpoint.class), anyString(), anyString()))
			.thenReturn("https://test.com");
		when(
			mockEdgeNetworkService.doRequest(
				anyString(),
				anyString(),
				ArgumentMatchers.anyMap(),
				ArgumentMatchers.anyBoolean(),
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenAnswer(invocation -> {
				final EdgeNetworkService.ResponseCallback callback = invocation.getArgument(4);
				callback.onError(errorBody);
				callback.onComplete();
				return new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0);
			});
	}
}
