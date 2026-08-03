/*
  Copyright 2024 Adobe. All rights reserved.
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
import com.adobe.marketing.mobile.util.MapUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
		mockCallbacksManagerStatic.when(CompletionCallbacksManager::getInstance).thenReturn(mockCompletionCallbacksManager);

		edgeConfig = new HashMap<>();
		edgeConfig.put("edge.configId", "test-config-id");
		edgeConfig.put("edge.batching.eventNameAllowlist", Collections.singletonList("test-event"));

		hitProcessor = new EdgeHitProcessor(
			mockNetworkResponseHandler,
			mockEdgeNetworkService,
			mockNamedCollection,
			null,
			null
		);
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

		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());
		// Regression guard: DONE must report exactly how many entities were resolved, so the queue
		// removes only those — never a stale/full peeked-window count.
		assertEquals(1, outcome.getResolvedHeadCount());
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

		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());
		assertEquals(2, outcome.getResolvedHeadCount());
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

		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());
		assertEquals(1, outcome.getResolvedHeadCount());
		// Only one network request — for the ExperienceEvent alone; the Consent entity was never sent.
		verify(mockEdgeNetworkService, times(1))
			.doRequest(anyString(), anyString(), ArgumentMatchers.anyMap(), any(EdgeNetworkService.ResponseCallback.class));
	}

	@Test
	public void testProcessBatch_singleExperienceEvent_retry_returnsOutcomeRetry() {
		mockNetworkReturns(new RetryResult(EdgeNetworkService.NetworkRequestOutcome.RETRY, 5));
		DataEntity entity = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		assertEquals(BatchOutcome.Kind.RETRY_BATCH, outcome.getKind());
		assertEquals(5, outcome.getRetryAfterSeconds());
		// Waiting events registered
		verify(mockNetworkResponseHandler, times(1)).addWaitingEvents(anyString(), any());
	}

	// -------------------------------------------------------------------------
	// WI-1b: terminal-400 — regression guard (the core fix)
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_singleExperienceEvent_400_deliversTerminalError() {
		RetryResult explodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		explodeResult.setResponseBody(ERROR_BODY);
		mockNetworkReturns(explodeResult);

		DataEntity entity = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		// Must return DONE (event is fully resolved — error delivered)
		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());

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

		RetryResult explodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		explodeResult.setResponseBody(ERROR_BODY);
		mockNetworkReturns(explodeResult);

		DataEntity entity = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnError(eq(ERROR_BODY), anyString());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(anyString());
	}

	// -------------------------------------------------------------------------
	// WI-1b: N>1 batch 400 — clean up waiting state, then explode to singles
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_twoEvents_400_removesWaitingEventsAndExplodes() {
		// Batch of 2 gets 400; each individual resend succeeds
		RetryResult explodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		explodeResult.setResponseBody(ERROR_BODY);
		RetryResult successResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0);

		mockNetworkReturnsSequence(explodeResult, successResult, successResult);

		DataEntity entity1 = buildExperienceEventEntity();
		DataEntity entity2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(entity1, entity2));

		// All resolved
		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());

		// 3 network requests: 1 original batch + 2 individual resends
		verify(mockEdgeNetworkService, times(3))
			.doRequest(anyString(), anyString(), ArgumentMatchers.anyMap(), any(EdgeNetworkService.ResponseCallback.class));

		// Original batch waiting events cleaned up (no callbacks, just removal)
		verify(mockNetworkResponseHandler, times(1)).removeWaitingEvents(anyString());

		// No terminal error delivery for the original batch request ID
		verify(mockNetworkResponseHandler, never()).processResponseOnError(eq(ERROR_BODY), anyString());

		// 3 addWaitingEvents calls: 1 for the original batch + 2 for individual resends
		verify(mockNetworkResponseHandler, times(3)).addWaitingEvents(anyString(), any());
	}

	@Test
	public void testProcessBatch_twoEvents_400_oneIndividualGets400_oneSuceeds() {
		// Batch of 2 gets 400; first individual gets another 400 (terminal), second succeeds
		RetryResult batchExplodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		batchExplodeResult.setResponseBody(ERROR_BODY);
		RetryResult singleExplodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		singleExplodeResult.setResponseBody(ERROR_BODY);
		RetryResult successResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.SUCCESS, 0);

		mockNetworkReturnsSequence(batchExplodeResult, singleExplodeResult, successResult);

		DataEntity entity1 = buildExperienceEventEntity();
		DataEntity entity2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(entity1, entity2));

		// Both resolved (first via terminal error delivery, second via success)
		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());

		// 3 network requests total
		verify(mockEdgeNetworkService, times(3))
			.doRequest(anyString(), anyString(), ArgumentMatchers.anyMap(), any(EdgeNetworkService.ResponseCallback.class));

		// Terminal error for the bad single event
		verify(mockNetworkResponseHandler, times(1)).processResponseOnError(eq(ERROR_BODY), anyString());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(anyString());
	}

	@Test
	public void testProcessBatch_twoEvents_400_oneIndividualNeedsRetry() {
		// Batch of 2 gets 400; first individual needs retry
		RetryResult batchExplodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		RetryResult retryResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.RETRY, 10);

		mockNetworkReturnsSequence(batchExplodeResult, retryResult);

		DataEntity entity1 = buildExperienceEventEntity();
		DataEntity entity2 = buildExperienceEventEntity();

		BatchOutcome outcome = hitProcessor.processBatch(Arrays.asList(entity1, entity2));

		// Nothing resolved before the retry — head entity is the retrying one
		assertEquals(BatchOutcome.Kind.RETRY_BATCH, outcome.getKind());
		assertEquals(10, outcome.getRetryAfterSeconds());
	}

	// -------------------------------------------------------------------------
	// WI-1d: Consent/Reset terminal-400 via sendNetworkRequest
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_consentEvent_400_deliversTerminalError() {
		RetryResult explodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		explodeResult.setResponseBody(ERROR_BODY);
		mockNetworkReturns(explodeResult);

		DataEntity entity = buildConsentEventEntity();

		// Consent event goes through processHit → processUpdateConsentEventHit → sendNetworkRequest
		// sendNetworkRequest must handle EXPLODE_400 as terminal
		BatchOutcome outcome = hitProcessor.processBatch(Collections.singletonList(entity));

		assertEquals(BatchOutcome.Kind.DONE, outcome.getKind());

		// Consent events register with addWaitingEvent (singular)
		ArgumentCaptor<String> requestIdCaptor = ArgumentCaptor.forClass(String.class);
		verify(mockNetworkResponseHandler, times(1)).addWaitingEvent(requestIdCaptor.capture(), any());

		verify(mockNetworkResponseHandler, times(1)).processResponseOnError(eq(ERROR_BODY), eq(requestIdCaptor.getValue()));
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(eq(requestIdCaptor.getValue()));
	}

	// -------------------------------------------------------------------------
	// WI-1e: no waiting-event / callback leak after terminal and batch flows
	// -------------------------------------------------------------------------

	@Test
	public void testProcessBatch_singleEvent_400_allThreeStepsComplete() {
		// addWaitingEvents, processResponseOnError, and processResponseOnComplete must all fire
		// exactly once for a terminal-400 on a batch-of-1.
		RetryResult explodeResult = new RetryResult(EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400, 0);
		explodeResult.setResponseBody(ERROR_BODY);
		mockNetworkReturns(explodeResult);

		DataEntity entity = buildExperienceEventEntity();
		hitProcessor.processBatch(Collections.singletonList(entity));

		verify(mockNetworkResponseHandler, times(1)).addWaitingEvents(anyString(), any());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnError(anyString(), anyString());
		verify(mockNetworkResponseHandler, times(1)).processResponseOnComplete(anyString());
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private DataEntity buildExperienceEventEntity() {
		Map<String, Object> xdmData = new HashMap<>();
		xdmData.put("test", "data");
		Map<String, Object> eventData = new HashMap<>();
		eventData.put("xdm", xdmData);

		Event event = new Event.Builder("test-event", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(eventData)
			.build();

		return new EdgeDataEntity(event, edgeConfig, identityMap).toDataEntity();
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
				any(EdgeNetworkService.ResponseCallback.class)
			)
		)
			.thenReturn(retryResult);
	}

	/** Stubs doRequest to return the given results in sequence (last value is repeated if needed). */
	private void mockNetworkReturnsSequence(final RetryResult first, final RetryResult... rest) {
		when(mockEdgeNetworkService.buildUrl(any(EdgeEndpoint.class), anyString(), anyString()))
			.thenReturn("https://test.com");

		if (rest == null || rest.length == 0) {
			when(
				mockEdgeNetworkService.doRequest(
					anyString(),
					anyString(),
					ArgumentMatchers.anyMap(),
					any(EdgeNetworkService.ResponseCallback.class)
				)
			)
				.thenReturn(first);
		} else {
			when(
				mockEdgeNetworkService.doRequest(
					anyString(),
					anyString(),
					ArgumentMatchers.anyMap(),
					any(EdgeNetworkService.ResponseCallback.class)
				)
			)
				.thenReturn(first, rest);
		}
	}
}
