/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import androidx.annotation.NonNull;
import com.adobe.marketing.mobile.edge.Datastream;
import com.adobe.marketing.mobile.edge.SDKConfig;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitProcessing;
import com.adobe.marketing.mobile.services.HitProcessingResult;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles the processing of {@link EdgeDataEntity}s, sending network requests.
 */
class EdgeHitProcessor implements HitProcessing {

	private static final String LOG_SOURCE = "EdgeHitProcessor";

	private final NetworkResponseHandler networkResponseHandler;
	private final NamedCollection namedCollection;
	private final EdgeSharedStateCallback sharedStateCallback;
	private final EdgeStateCallback stateCallback;
	private final ConcurrentHashMap<String, Integer> entityRetryIntervalMapping = new ConcurrentHashMap<>();
	static EdgeNetworkService networkService;
	private static final String VALID_PATH_REGEX_PATTERN = "^\\/[/.a-zA-Z0-9-~_]+$";
	private static final Pattern pattern = Pattern.compile(VALID_PATH_REGEX_PATTERN);

	EdgeHitProcessor(
		final NetworkResponseHandler networkResponseHandler,
		final EdgeNetworkService networkService,
		final NamedCollection namedCollection,
		final EdgeSharedStateCallback callback,
		final EdgeStateCallback stateCallback
	) {
		this.networkResponseHandler = networkResponseHandler;
		this.networkService = networkService;
		this.namedCollection = namedCollection;
		this.sharedStateCallback = callback;
		this.stateCallback = stateCallback;
	}

	@Override
	public int retryInterval(@NonNull final DataEntity dataEntity) {
		Integer retryInterval = entityRetryIntervalMapping.get(dataEntity.getUniqueIdentifier());
		return retryInterval != null ? retryInterval : EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
	}

	/**
	 * Processes a batch of {@link DataEntity}s as a single network request when the batch contains
	 * more than one ExperienceEvent; otherwise delegates to the existing single-entity path.
	 *
	 * <p>Routing rules:
	 * <ul>
	 *   <li>Head is not an ExperienceEvent → process head alone (consent, reset, etc.).</li>
	 *   <li>Batch size == 1 OR only head qualifies → single-entity path.</li>
	 *   <li>Multiple consecutive ExperienceEvents → build a batch request; on 400, explode to
	 *       individual sends; on other non-recoverable, drop all and error.</li>
	 * </ul>
	 *
	 * @param entities ordered list of entities to process; never null, never empty
	 * @return {@link BatchOutcome} instructing the queue how to advance
	 */
	BatchOutcome processBatch(@NonNull final List<DataEntity> entities) {
		if(entities.isEmpty()) return BatchOutcome.done(0);

		final DataEntity headEntity = entities.get(0);
		final EdgeDataEntity headEdgeEntity = EdgeDataEntity.fromDataEntity(headEntity);

		if (headEdgeEntity == null) {
			Log.debug(LOG_TAG, LOG_SOURCE,
					"Unable to deserialize head entity to EdgeDataEntity, dropping.");
			return BatchOutcome.done(1);
		}

		// Non-experience events (consent, reset) must be processed alone.
		if (!EventUtils.isExperienceEvent(headEdgeEntity.getEvent())) {
			Log.trace(LOG_TAG, LOG_SOURCE,
					"Head entity is not an ExperienceEvent (%s); processing alone.",
					headEdgeEntity.getEvent().getType());
			return processSingleEntity(headEntity);
		}

		// Events whose name isn't in the configured allowlist must also be processed alone.
		if (!isEventNameAllowlistedForBatching(headEdgeEntity)) {
			Log.trace(LOG_TAG, LOG_SOURCE,
					"Head entity's event name (%s) is not in the batching allowlist; processing alone.",
					headEdgeEntity.getEvent().getName());
			return processSingleEntity(headEntity);
		}

		// Collect the consecutive ExperienceEvent run from the front.
		final List<DataEntity> batchEntities = new ArrayList<>();
		final List<Event> batchEvents = new ArrayList<>();

		for (final DataEntity dataEntity : entities) {
			final EdgeDataEntity edgeEntity = EdgeDataEntity.fromDataEntity(dataEntity);
			if (
				edgeEntity == null ||
				!EventUtils.isExperienceEvent(edgeEntity.getEvent()) ||
				!isEventNameAllowlistedForBatching(edgeEntity)
			) {
				break;
			}
			batchEntities.add(dataEntity);
			batchEvents.add(edgeEntity.getEvent());
		}

		Log.debug(LOG_TAG, LOG_SOURCE,
				"Processing batch of %d ExperienceEvent(s).", batchEntities.size());

		// Build batch request using the head entity's snapshotted configuration.
		final RequestBuilder request = new RequestBuilder(namedCollection);
		request.addXdmPayload(headEdgeEntity.getIdentityMap());
		request.enableResponseStreaming(
				EdgeConstants.Defaults.REQUEST_CONFIG_RECORD_SEPARATOR,
				EdgeConstants.Defaults.REQUEST_CONFIG_LINE_FEED
		);

		if (stateCallback != null) {
			request.addXdmPayload(stateCallback.getImplementationDetails());
		}

		final Map<String, Object> edgeConfig = headEdgeEntity.getConfiguration();
		String datastreamId = DataReader.optString(
				edgeConfig, EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID, null);
		final Map<String, Object> headEventConfigMap = EventUtils.getConfig(headEdgeEntity.getEvent());
		datastreamId = processEventConfigOverrides(headEventConfigMap, request, datastreamId);

		if (StringUtils.isNullOrEmpty(datastreamId)) {
			Log.debug(LOG_TAG, LOG_SOURCE,
					"Cannot process batch: Edge config ID is null/empty, dropping %d events.",
					batchEntities.size());
			return BatchOutcome.done(batchEntities.size());
		}

		final JSONObject requestPayload = request.getPayloadWithExperienceEvents(batchEvents);
		if (requestPayload == null) {
			Log.warning(LOG_TAG, LOG_SOURCE,
					"Failed to build batch request payload, dropping %d events.", batchEntities.size());
			return BatchOutcome.done(batchEntities.size());
		}

		final Map<String, Object> requestProperties = getRequestProperties(headEdgeEntity.getEvent());
		final EdgeEndpoint edgeEndpoint = getEdgeEndpoint(
				EdgeNetworkService.RequestType.INTERACT, edgeConfig, requestProperties);
		final EdgeHit edgeHit = new EdgeHit(datastreamId, requestPayload, edgeEndpoint);

		// Register all events before the network call so response fragments can be routed.
		networkResponseHandler.addWaitingEvents(edgeHit.getRequestId(), batchEvents);

		final Map<String, String> requestHeaders = getRequestHeaders();
		final RetryResult result = sendBatchNetworkRequest(
				headEntity.getUniqueIdentifier(), edgeHit, requestHeaders);

		switch (result.getNetworkRequestOutcome()) {
			case SUCCESS:
				Log.debug(LOG_TAG, LOG_SOURCE,
						"Batch of %d events sent and processed successfully.", batchEntities.size());
				return BatchOutcome.done(batchEntities.size());

			case RETRY:
				Log.debug(LOG_TAG, LOG_SOURCE,
						"Batch of %d events will be retried in %d seconds.",
						batchEntities.size(), result.getRetryIntervalSeconds());
				return BatchOutcome.retryBatch(result.getRetryIntervalSeconds());

			case EXPLODE_400:
				if (batchEntities.size() == 1) {
					// Terminal: single event got a 400 — deliver the real error and fire completion.
					Log.warning(LOG_TAG, LOG_SOURCE,
							"Single event received 400; delivering terminal error for request id (%s).",
							edgeHit.getRequestId());
					deliverTerminalBadRequest(edgeHit.getRequestId(), result.getResponseBody());
					return BatchOutcome.done(1);
				} else {
					// Batch of N>1: nothing ingested — clean up waiting state then explode.
					Log.warning(LOG_TAG, LOG_SOURCE,
							"Batch of %d events received 400; exploding to individual requests.",
							batchEntities.size());
					networkResponseHandler.removeWaitingEvents(edgeHit.getRequestId());
					return explodeAndResend(batchEntities);
				}

			case DROP:
				Log.warning(LOG_TAG, LOG_SOURCE,
						"Batch of %d events received non-recoverable error; dropping all.",
						batchEntities.size());
				return BatchOutcome.done(batchEntities.size());

			default:
				return BatchOutcome.done(batchEntities.size());
		}
	}

	/**
	 * Checks whether {@code entity}'s underlying {@link Event} name is present in the
	 * {@code edge.batching.eventNameAllowlist} configuration snapshotted on this entity at
	 * enqueue time. An absent or empty allowlist means no event names are eligible for
	 * batching (opt-in allowlist semantics) — {@code edge.batching.enabled} alone is not
	 * sufficient to batch a given event.
	 *
	 * @param entity the {@link EdgeDataEntity} whose event name is being checked
	 * @return true if the entity's event name is explicitly allowlisted for batching
	 */
	private boolean isEventNameAllowlistedForBatching(@NonNull final EdgeDataEntity entity) {
		final List<String> allowlist = DataReader.optStringList(
			entity.getConfiguration(),
			EdgeConstants.SharedState.Configuration.EDGE_BATCHING_EVENT_NAME_ALLOWLIST,
			null
		);

		if (allowlist == null || allowlist.isEmpty()) {
			return false;
		}

		return allowlist.contains(entity.getEvent().getName());
	}

	/**
	 * Delegates a single {@link DataEntity} to the existing {@link #processHit} path and
	 * converts the boolean result to a {@link BatchOutcome}.
	 */
	private BatchOutcome processSingleEntity(@NonNull final DataEntity entity) {
		final boolean[] done = { true };
		processHit(entity, result -> done[0] = result);
		if (done[0]) {
			return BatchOutcome.done(1);
		}
		return BatchOutcome.retryBatch(retryInterval(entity));
	}

	/**
	 * Sends a batch network request and returns the full {@link RetryResult} (including
	 * {@link EdgeNetworkService.NetworkRequestOutcome}) so {@link #processBatch} can act on
	 * fine-grained outcomes like {@code EXPLODE_400}.
	 *
	 * <p>Mirrors the validation and logging logic of {@link #sendNetworkRequest}.
	 *
	 * @param entityId unique identifier of the head entity, used for retry-interval tracking
	 * @param edgeHit  the assembled batch hit
	 * @param requestHeaders HTTP headers to attach
	 * @return {@link RetryResult} describing the outcome
	 */
	private RetryResult sendBatchNetworkRequest(
			final String entityId,
			final EdgeHit edgeHit,
			final Map<String, String> requestHeaders) {

		if (edgeHit == null || edgeHit.getPayload() == null || edgeHit.getPayload().length() == 0) {
			Log.warning(LOG_TAG, LOG_SOURCE, "Batch request body was null/empty, dropping.");
			return new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0);
		}

		final EdgeNetworkService.ResponseCallback responseCallback = new EdgeNetworkService.ResponseCallback() {
			@Override
			public void onResponse(final String jsonResponse) {
				networkResponseHandler.processResponseOnSuccess(jsonResponse, edgeHit.getRequestId());
			}

			@Override
			public void onError(final String jsonError) {
				networkResponseHandler.processResponseOnError(jsonError, edgeHit.getRequestId());
			}

			@Override
			public void onComplete() {
				networkResponseHandler.processResponseOnComplete(edgeHit.getRequestId());
			}
		};

		final String url = networkService.buildUrl(
				edgeHit.getEdgeEndpoint(), edgeHit.getDatastreamId(), edgeHit.getRequestId());

		if (!isValidUrl(url)) {
			Log.warning(LOG_TAG, LOG_SOURCE,
					"Unable to send batch request for entity (%s): URL is malformed, '%s'.",
					entityId, url);
			return new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0);
		}

		try {
			Log.debug(LOG_TAG, LOG_SOURCE,
					"Sending batch request id (%s) to URL '%s' with body:\n%s",
					edgeHit.getRequestId(), url, edgeHit.getPayload().toString(2));
		} catch (JSONException e) {
			Log.debug(LOG_TAG, LOG_SOURCE,
					"Sending batch request id (%s) to URL '%s'. Error pretty-printing JSON: %s",
					edgeHit.getRequestId(), url, e.getLocalizedMessage());
		}

		final RetryResult retryResult = networkService.doRequest(
				url, edgeHit.getPayload().toString(), requestHeaders, responseCallback);

		if (retryResult == null) {
			return new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0);
		}

		if (retryResult.getShouldRetry() == EdgeNetworkService.Retry.NO) {
			if (entityId != null) {
				entityRetryIntervalMapping.remove(entityId);
			}
		} else if (entityId != null
				&& retryResult.getRetryIntervalSeconds() != EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS) {
			entityRetryIntervalMapping.put(entityId, retryResult.getRetryIntervalSeconds());
		}

		return retryResult;
	}

	/**
	 * Re-sends each entity in {@code batchEntities} individually after a batch 400.
	 *
	 * <p>A 400 means nothing in the batch was ingested, so every event is safe to resend.
	 * Entities are processed FIFO:
	 * <ul>
	 *   <li>Delivered / dropped (i.e. {@link #processHit} returns {@code true}) → counted as resolved.</li>
	 *   <li>Recoverable failure (returns {@code false}) → stop; return the resolved count so the
	 *       queue can remove those entities and schedule a retry for the remainder.</li>
	 * </ul>
	 *
	 * @param batchEntities the entities that made up the failed batch, in original queue order
	 * @return {@link BatchOutcome} directing the queue how to advance
	 */
	private BatchOutcome explodeAndResend(@NonNull final List<DataEntity> batchEntities) {
		Log.debug(LOG_TAG, LOG_SOURCE,
				"Exploding batch of %d events to individual requests.", batchEntities.size());

		int resolvedCount = 0;
		for (final DataEntity entity : batchEntities) {
			// Each entity is sent as a batch-of-1, which goes through the same terminal path
			// (handles SUCCESS, DROP, and the terminal-400 case that fires the error callback).
			final BatchOutcome outcome = processBatch(Collections.singletonList(entity));

			switch (outcome.getKind()) {
				case DONE:
					resolvedCount++;
					Log.trace(LOG_TAG, LOG_SOURCE,
							"Explosion: event %d/%d resolved.", resolvedCount, batchEntities.size());
					break;

				case RETRY_BATCH:
					// Recoverable failure — stop exploding. Leave this entity (and everything after)
					// in the queue so the normal retry cycle handles them.
					final int delay = outcome.getRetryAfterSeconds();
					Log.debug(LOG_TAG, LOG_SOURCE,
							"Explosion: event at position %d/%d needs retry in %d seconds; "
									+ "%d preceding events resolved.",
							resolvedCount + 1, batchEntities.size(), delay, resolvedCount);
					if (resolvedCount > 0) {
						return BatchOutcome.partialRemove(resolvedCount, delay);
					}
					return BatchOutcome.retryBatch(delay);

				default:
					// PARTIAL_REMOVE cannot occur for a size-1 processBatch call.
					resolvedCount++;
					break;
			}
		}

		Log.debug(LOG_TAG, LOG_SOURCE,
				"Explosion complete: all %d events resolved.", batchEntities.size());
		return BatchOutcome.done(resolvedCount);
	}

	/**
	 * Send network requests out with the data encapsulated in {@link DataEntity}.
	 * If configuration is null, the processing is paused.
	 *
	 * @param dataEntity the {@code DataEntity} to be processed at this time; should not be null
	 * @param processingResult the {@code HitProcessingResult} callback to be invoked with the result of the processing. Returns:
	 * 							true when the {@code entity} was processed and it can be removed from the queue,
	 *							false when the processing failed and the {@code entity} should be retried at a later point.
	 */
	@Override
	public void processHit(@NonNull final DataEntity dataEntity, @NonNull final HitProcessingResult processingResult) {
		EdgeDataEntity entity = EdgeDataEntity.fromDataEntity(dataEntity);

		if (entity == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Unable to deserialize DataEntity to EdgeDataEntity. Dropping the hit.");
			processingResult.complete(true);
			return;
		}

		// Add in Identity Map at request (global) level
		RequestBuilder request = new RequestBuilder(namedCollection);
		request.addXdmPayload(entity.getIdentityMap());

		// Enable response streaming for all events
		request.enableResponseStreaming(
			EdgeConstants.Defaults.REQUEST_CONFIG_RECORD_SEPARATOR,
			EdgeConstants.Defaults.REQUEST_CONFIG_LINE_FEED
		);

		boolean hitCompleteResult = true;
		if (EventUtils.isExperienceEvent(entity.getEvent())) {
			hitCompleteResult = processExperienceEventHit(dataEntity.getUniqueIdentifier(), entity, request);
		} else if (EventUtils.isUpdateConsentEvent(entity.getEvent())) {
			hitCompleteResult = processUpdateConsentEventHit(dataEntity.getUniqueIdentifier(), entity, request);
		} else if (EventUtils.isResetComplete(entity.getEvent())) {
			// clear state store
			final StoreResponsePayloadManager payloadManager = new StoreResponsePayloadManager(namedCollection);
			payloadManager.deleteAllStorePayloads();
			hitCompleteResult = true; // Request complete, don't retry hit
		}

		processingResult.complete(hitCompleteResult);
	}

	/**
	 * Delivers a terminal 400 error for the given {@code requestId}: processes the server error
	 * body through the response handler (dispatching error events to waiting events) and then
	 * fires completion (removing waiting state and unregistering callbacks).
	 *
	 * <p>Used for any single-event 400 — whether from {@link #processBatch} (batch-of-1) or from
	 * {@link #sendNetworkRequest} (Consent/Reset paths).
	 *
	 * @param requestId  the Edge request ID whose waiting events should receive the error
	 * @param errorBody  the JSON error body captured from the 400 response; may be null
	 */
	private void deliverTerminalBadRequest(final String requestId, final String errorBody) {
		networkResponseHandler.processResponseOnError(errorBody, requestId);
		networkResponseHandler.processResponseOnComplete(requestId);
	}

	/**
	 * Sends a network call to Experience Edge Network with the provided information in {@link EdgeHit}.
	 * Two response handlers are registered for this network request, for response content and eventual request errors.
	 * @param entityId the unique id of the entity being processed
	 * @param edgeHit the Edge request to be sent; should not be null
	 * @param requestHeaders the headers for the network requests
	 * @return true if sending the hit is complete, false if sending the hit should be retried at a later time
	 */
	boolean sendNetworkRequest(final String entityId, final EdgeHit edgeHit, final Map<String, String> requestHeaders) {
		if (edgeHit == null || edgeHit.getPayload() == null || edgeHit.getPayload().length() == 0) {
			Log.warning(LOG_TAG, LOG_SOURCE, "Request body was null/empty, dropping this request");
			return true;
		}

		EdgeNetworkService.ResponseCallback responseCallback = new EdgeNetworkService.ResponseCallback() {
			@Override
			public void onResponse(final String jsonResponse) {
				networkResponseHandler.processResponseOnSuccess(jsonResponse, edgeHit.getRequestId());
			}

			@Override
			public void onError(final String jsonError) {
				networkResponseHandler.processResponseOnError(jsonError, edgeHit.getRequestId());
			}

			@Override
			public void onComplete() {
				networkResponseHandler.processResponseOnComplete(edgeHit.getRequestId());
			}
		};

		String url = networkService.buildUrl(
			edgeHit.getEdgeEndpoint(),
			edgeHit.getDatastreamId(),
			edgeHit.getRequestId()
		);

		if (!isValidUrl(url)) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to send network request for entity (%s) as URL is malformed or scheme is not 'https', '%s'.",
				entityId,
				url
			);

			return true;
		}

		try {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Sending network request with id (%s) to URL '%s' with body:\n%s",
				edgeHit.getRequestId(),
				url,
				edgeHit.getPayload().toString(2)
			);
		} catch (JSONException e) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Sending network request with id (%s) to URL '%s'\nError parsing JSON request: %s",
				edgeHit.getRequestId(),
				url,
				e.getLocalizedMessage()
			);
		}

		RetryResult retryResult = networkService.doRequest(
			url,
			edgeHit.getPayload().toString(),
			requestHeaders,
			responseCallback
		);

		if (retryResult == null || retryResult.getShouldRetry() == EdgeNetworkService.Retry.NO) {
			if (entityId != null) {
				entityRetryIntervalMapping.remove(entityId);
			}

			// Consent/Reset events are always terminal. A 400 here is never exploded — deliver
			// the real error body directly and fire completion so the caller sees it.
			if (retryResult != null
					&& retryResult.getNetworkRequestOutcome()
						== EdgeNetworkService.NetworkRequestOutcome.EXPLODE_400) {
				deliverTerminalBadRequest(edgeHit.getRequestId(), retryResult.getResponseBody());
			}

			return true; // Hit complete (success, drop, or terminal 400)
		} else {
			if (
				entityId != null &&
				retryResult.getRetryIntervalSeconds() != EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS
			) {
				entityRetryIntervalMapping.put(entityId, retryResult.getRetryIntervalSeconds());
			}

			return false; // Hit failed to send, retry after interval
		}
	}

	/**
	 * Validates a given URL.
	 * Checks that a URL is valid by ensuring:
	 * <ul>
	 *     <li>The URL is not null or empty.</li>
	 *     <li>The URL is parsable by the {@link java.net.URL} class.</li>
	 *     <li>The URL scheme is "HTTPS".</li>
	 * </ul>
	 * @param url the URL string to validate
	 * @return true if the URL is valid, false otherwise.
	 */
	private boolean isValidUrl(final String url) {
		if (!UrlUtils.isValidUrl(url)) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Request invalid, URL is malformed, '%s'.", url);
			return false;
		}

		if (!url.startsWith("https")) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Request invalid, URL scheme must be 'https', '%s'.", url);
			return false;
		}

		return true;
	}

	/**
	 * Processes configuration overrides for the event. Returns datastream Id value to be used
	 * for the current event based on the overrides provided for the event.
	 *
	 * @param eventConfigMap a {@link Map} containing configuration overrides.
	 * @param request a {@link RequestBuilder} instance for the current event.
	 * @param datastreamId the default datastream ID from the SDK configuration.
	 * @return the datastream ID to be used for the current event.
	 */
	private String processEventConfigOverrides(
		Map<String, Object> eventConfigMap,
		RequestBuilder request,
		String datastreamId
	) {
		// Check if datastream ID override is present
		String datastreamIdOverride = DataReader.optString(
			eventConfigMap,
			EdgeConstants.EventDataKeys.Config.DATASTREAM_ID_OVERRIDE,
			null
		);

		if (!StringUtils.isNullOrEmpty(datastreamIdOverride)) {
			// Attach original datastream ID to the outgoing request
			request.addSdkConfig(new SDKConfig(new Datastream(datastreamId)));
		}

		// Check if datastream config override is present
		Map<String, Object> datastreamConfigOverride = DataReader.optTypedMap(
			Object.class,
			eventConfigMap,
			EdgeConstants.EventDataKeys.Config.DATASTREAM_CONFIG_OVERRIDE,
			null
		);

		if (!MapUtils.isNullOrEmpty(datastreamConfigOverride)) {
			// Attach datastream config override to the outgoing request metadata
			request.addConfigOverrides(datastreamConfigOverride);
		}

		return StringUtils.isNullOrEmpty(datastreamIdOverride) ? datastreamId : datastreamIdOverride;
	}

	/**
	 * Process and send an ExperienceEvent network request.
	 *
	 * @param entityId the {@link DataEntity} unique identifier
	 * @param entity the {@link EdgeDataEntity} which encapsulates the request data
	 * @param request a {@link RequestBuilder} instance
	 * @return true if the request processing is complete for this hit or false if processing is
	 * not complete and this hit must be retired.
	 */
	private boolean processExperienceEventHit(
		@NonNull final String entityId,
		@NonNull final EdgeDataEntity entity,
		@NonNull final RequestBuilder request
	) {
		if (stateCallback != null) {
			// Add Implementation Details to request (global) level
			request.addXdmPayload(stateCallback.getImplementationDetails());
		}

		Map<String, Object> edgeConfig = entity.getConfiguration();

		String datastreamId = DataReader.optString(
			edgeConfig,
			EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID,
			null
		);

		// Get config map containing overrides from the event
		Map<String, Object> eventConfigMap = EventUtils.getConfig(entity.getEvent());

		datastreamId = processEventConfigOverrides(eventConfigMap, request, datastreamId);

		if (StringUtils.isNullOrEmpty(datastreamId)) {
			// The Edge configuration ID value should get validated when creating the Hit,
			// so we shouldn't get here in production.
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Cannot process Experience Event hit as the Edge Network configuration ID is null or empty, dropping current event (%s).",
				entity.getEvent().getUniqueIdentifier()
			);
			return true; // Request complete, don't retry hit
		}

		final List<Event> listOfEvents = new ArrayList<>();
		listOfEvents.add(entity.getEvent());
		final JSONObject requestPayload = request.getPayloadWithExperienceEvents(listOfEvents);

		if (requestPayload == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to build the request payload, dropping current event (%s).",
				entity.getEvent().getUniqueIdentifier()
			);

			return true; // Request complete, don't retry hit
		}

		Map<String, Object> requestProperties = getRequestProperties(entity.getEvent());
		final EdgeEndpoint edgeEndpoint = getEdgeEndpoint(
			EdgeNetworkService.RequestType.INTERACT,
			edgeConfig,
			requestProperties
		);

		final EdgeHit edgeHit = new EdgeHit(datastreamId, requestPayload, edgeEndpoint);

		// NOTE: the order of these events need to be maintained as they were sent in the network request
		// otherwise the response callback cannot be matched
		networkResponseHandler.addWaitingEvents(edgeHit.getRequestId(), listOfEvents);

		final Map<String, String> requestHeaders = getRequestHeaders();
		return sendNetworkRequest(entityId, edgeHit, requestHeaders);
	}

	/**
	 * Process and send an Update Consent network request.
	 *
	 * @param entityId the {@link DataEntity} unique identifier
	 * @param entity the {@link EdgeDataEntity} which encapsulates the request data
	 * @param request a {@link RequestBuilder} instance
	 * @return true if the request processing is complete for this hit or false if processing is
	 * not complete and this hit must be retired.
	 */
	private boolean processUpdateConsentEventHit(
		@NonNull final String entityId,
		@NonNull final EdgeDataEntity entity,
		@NonNull final RequestBuilder request
	) {
		// Build and send the consent network request to Experience Edge
		final JSONObject consentPayload = request.getConsentPayload(entity.getEvent());

		if (consentPayload == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to build the consent payload, dropping current event (%s).",
				entity.getEvent().getUniqueIdentifier()
			);

			return true; // Request complete, don't retry hit
		}

		Map<String, Object> edgeConfig = entity.getConfiguration();
		String datastreamId = DataReader.optString(
			edgeConfig,
			EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID,
			null
		);
		if (StringUtils.isNullOrEmpty(datastreamId)) {
			// The Edge configuration ID value should get validated when creating the Hit,
			// so we shouldn't get here in production.
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Cannot process Update Consent hit as the Edge Network configuration ID is null or empty, dropping current event (%s).",
				entity.getEvent().getUniqueIdentifier()
			);
			return true; // Request complete, don't retry hit
		}

		final EdgeEndpoint edgeEndpoint = getEdgeEndpoint(EdgeNetworkService.RequestType.CONSENT, edgeConfig, null);

		final EdgeHit edgeHit = new EdgeHit(datastreamId, consentPayload, edgeEndpoint);

		networkResponseHandler.addWaitingEvent(edgeHit.getRequestId(), entity.getEvent());
		final Map<String, String> requestHeaders = getRequestHeaders();
		return sendNetworkRequest(entityId, edgeHit, requestHeaders);
	}

	/**
	 * Creates a new instance of {@link EdgeEndpoint} using the values provided in {@code edgeConfiguration}.
	 * @param edgeConfiguration the current Edge configuration
	 * @return a new {@code EdgeEndpoint} instance
	 */
	private EdgeEndpoint getEdgeEndpoint(
		final EdgeNetworkService.RequestType requestType,
		final Map<String, Object> edgeConfiguration,
		final Map<String, Object> requestProperties
	) {
		// Use null fallback value, which defaults to Prod environment when building EdgeEndpoint
		String requestEnvironment = DataReader.optString(
			edgeConfiguration,
			EdgeConstants.SharedState.Configuration.EDGE_REQUEST_ENVIRONMENT,
			null
		);
		// Use null fallback value, which defaults to default request domain when building EdgeEndpoint
		String requestDomain = DataReader.optString(
			edgeConfiguration,
			EdgeConstants.SharedState.Configuration.EDGE_DOMAIN,
			null
		);

		final String locationHint = stateCallback != null ? stateCallback.getLocationHint() : null;

		// Use null fallback value for request without custom path value
		String customPath = DataReader.optString(requestProperties, EdgeConstants.EventDataKeys.Request.PATH, null);

		return new EdgeEndpoint(requestType, requestEnvironment, requestDomain, customPath, locationHint);
	}

	/**
	 * Extracts all the custom request properties to overwrite the default values
	 * @param event current event for which the request properties are to be extracted
	 * @return the map of extracted request properties and their custom values
	 */
	private Map<String, Object> getRequestProperties(final Event event) {
		Map<String, Object> requestProperties = new HashMap<>();
		String overwritePath = getCustomRequestPath(event);
		if (!StringUtils.isNullOrEmpty(overwritePath)) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Got custom path:(%s) for event:(%s), which will overwrite the default interaction request path.",
				overwritePath,
				event.getUniqueIdentifier()
			);
			requestProperties.put(EdgeConstants.EventDataKeys.Request.PATH, overwritePath);
		}
		return requestProperties;
	}

	/**
	 * Extracts network request path property to overwrite the default endpoint path value
	 * @param event current event for which the request path property is to be extracted
	 * @return the custom path string
	 */
	private String getCustomRequestPath(final Event event) {
		Map<String, Object> requestData = DataReader.optTypedMap(
			Object.class,
			event.getEventData(),
			EdgeConstants.EventDataKeys.Request.KEY,
			null
		);
		String path = DataReader.optString(requestData, EdgeConstants.EventDataKeys.Request.PATH, null);

		if (StringUtils.isNullOrEmpty(path)) {
			return null;
		}

		if (!isValidPath(path)) {
			Log.error(
				LOG_TAG,
				LOG_SOURCE,
				"Dropping the overwrite path value: (%s), since it contains invalid characters or is empty or null.",
				path
			);
			return null;
		}

		return path;
	}

	/**
	 * Validates a given path does not contain invalid characters.
	 * A 'path'  may only contain alphanumeric characters, forward slash, period, hyphen, underscore, or tilde, but may not contain a double forward slash.
	 * @param path the path to validate
	 * @return true if 'path' passes validation, false if 'path' contains invalid characters.
	 */
	private boolean isValidPath(final String path) {
		if (path.contains("//")) {
			return false;
		}

		Matcher matcher = pattern.matcher(path);

		return matcher.find();
	}

	/**
	 * Computes the request headers, including the {@code Assurance} integration identifier when it is enabled
	 * @return the network request headers or empty if none should be attached to the request
	 */
	private Map<String, String> getRequestHeaders() {
		final Map<String, String> requestHeaders = new HashMap<>();

		if (sharedStateCallback == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Unexpected null sharedStateCallback, unable to fetch Assurance shared state."
			);
			return requestHeaders;
		}

		// get latest Assurance shared state
		SharedStateResult assuranceStateResult = sharedStateCallback.getSharedState(
			EdgeConstants.SharedState.ASSURANCE,
			null
		);

		if (assuranceStateResult == null || assuranceStateResult.getStatus() != SharedStateStatus.SET) {
			return requestHeaders;
		}

		final String assuranceIntegrationId = DataReader.optString(
			assuranceStateResult.getValue(),
			EdgeConstants.SharedState.Assurance.INTEGRATION_ID,
			null
		);

		if (!StringUtils.isNullOrEmpty(assuranceIntegrationId)) {
			requestHeaders.put(EdgeConstants.NetworkKeys.HEADER_KEY_AEP_VALIDATION_TOKEN, assuranceIntegrationId);
		}

		return requestHeaders;
	}
}
