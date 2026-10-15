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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
	 *   <li>Head is not a batchable ExperienceEvent (wrong type, not allowlisted), or only the head
	 *       qualifies → single-entity path.</li>
	 *   <li>Multiple consecutive batchable ExperienceEvents that also share the head's snapshotted
	 *       config → build one batch request; on 400, drain individually; on other non-recoverable,
	 *       drop all and error.</li>
	 * </ul>
	 *
	 * @param entities ordered list of entities to process; never null, never empty
	 * @return {@link BatchOutcome} instructing the queue how to advance
	 */
	BatchOutcome processBatch(@NonNull final List<DataEntity> entities) {
		if (entities.isEmpty()) {
			return BatchOutcome.done(0);
		}

		final DataEntity headEntity = entities.get(0);
		final EdgeDataEntity headEdgeEntity = EdgeDataEntity.fromDataEntity(headEntity);

		if (headEdgeEntity == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Unable to deserialize head entity to EdgeDataEntity, dropping.");
			return BatchOutcome.done(1);
		}

		// Collect the consecutive run of batchable ExperienceEvents from the front. An entity is
		// batchable only if it decodes, is an ExperienceEvent, has an xdm.eventType whitelisted by the
		// batching config, and shares the head's snapshotted config (a single request can only carry one
		// datastream ID/override, so events queued under a different config cannot be combined with the
		// head's). The first entity that fails any of these (including the head) stops the run.
		final List<DataEntity> batchEntities = new ArrayList<>();
		final List<Event> batchEvents = new ArrayList<>();

		for (final DataEntity dataEntity : entities) {
			final EdgeDataEntity edgeEntity = EdgeDataEntity.fromDataEntity(dataEntity);
			if (
				edgeEntity == null ||
				!EventUtils.isExperienceEvent(edgeEntity.getEvent()) ||
				!isEventTypeBatchable(edgeEntity) ||
				!hasSameRequestConfig(edgeEntity, headEdgeEntity)
			) {
				break;
			}
			batchEntities.add(dataEntity);
			batchEvents.add(edgeEntity.getEvent());
		}

		// Head isn't batchable (non-ExperienceEvent, or xdm.eventType not whitelisted), or only the head
		// qualifies — process the head alone via the existing single-entity path (consent, reset,
		// non-whitelisted, and single-Experience-event cases all funnel here).
		if (batchEntities.size() <= 1) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Head entity (%s) is not batchable or is the only batchable entity; processing alone.",
				headEdgeEntity.getEvent().getName()
			);
			return processSingleEntity(headEntity);
		}

		Log.debug(LOG_TAG, LOG_SOURCE, "Processing batch of %d ExperienceEvent(s).", batchEntities.size());

		final EdgeHit edgeHit = buildExperienceEventHit(headEdgeEntity, batchEvents);
		if (edgeHit == null) {
			// Config ID missing/empty or payload build failed — drop the whole batch.
			return BatchOutcome.done(batchEntities.size());
		}

		// Register all events before the network call so response fragments can be routed.
		networkResponseHandler.addWaitingEvents(edgeHit.getRequestId(), batchEvents);

		final RetryResult result = sendEdgeHit(headEntity.getUniqueIdentifier(), edgeHit, getRequestHeaders(), true);

		switch (result.getNetworkRequestOutcome()) {
			case SUCCESS:
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Batch of %d events sent and processed successfully.",
					batchEntities.size()
				);
				return BatchOutcome.done(batchEntities.size());
			case RETRY:
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Batch of %d events will be retried in %d seconds.",
					batchEntities.size(),
					result.getRetryIntervalSeconds()
				);
				return BatchOutcome.retryBatch(result.getRetryIntervalSeconds());
			case EXPLODE_400:
				// Batch of N>1, nothing ingested — every event is safe to resend. Discard the batch's
				// waiting-event registration (its request id owns no completions now, so the individual
				// resends' new request ids can) and resend each entity individually, in order.
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Batch of %d events received 400; nothing ingested, resending individually.",
					batchEntities.size()
				);
				networkResponseHandler.removeWaitingEvents(edgeHit.getRequestId());
				return explodeAndResend(batchEntities);
			case DROP:
			default:
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Batch of %d events received non-recoverable error; dropping all.",
					batchEntities.size()
				);
				return BatchOutcome.done(batchEntities.size());
		}
	}

	/**
	 * Checks whether {@code entity}'s outgoing Experience Event is whitelisted for batching by its
	 * {@code xdm.eventType}, per the {@code edge.batching} configuration snapshotted on this entity at
	 * enqueue time (see {@link EdgeBatchingConfig}). Strict allow-list semantics: an event whose
	 * {@code xdm.eventType} is absent, or matched only by disabled entries, is not batchable —
	 * {@code enabled} alone is not sufficient.
	 *
	 * @param entity the {@link EdgeDataEntity} whose event is being checked
	 * @return true if the entity's {@code xdm.eventType} is whitelisted (exact or wildcard) for batching
	 */
	private boolean isEventTypeBatchable(@NonNull final EdgeDataEntity entity) {
		final EdgeBatchingConfig batchingConfig = EdgeBatchingConfig.from(entity.getConfiguration());
		return batchingConfig.isEventTypeBatchable(EventUtils.getXdmEventType(entity.getEvent()));
	}

	/**
	 * Checks whether {@code candidate} shares the same request-building config as {@code head}: the
	 * snapshotted Configuration state (datastream ID, environment, domain, ...), the event-level
	 * overrides ({@code datastreamIdOverride}/{@code datastreamConfigOverride}), and the event's custom
	 * {@code data.request.path} override. A batched request can only carry one of each — built entirely
	 * from the head's config (see {@link #buildExperienceEventHit}) and sent to the head's endpoint path
	 * (see {@link #getRequestProperties}) — so an entity whose own snapshot, overrides, or intended
	 * request path differs from the head's must not be folded into the same request; it would silently
	 * lose its own config or be sent to the wrong endpoint under the head's. Mirrors
	 * {@code aepsdk-edge-ios}'s {@code EdgeHitProcessor.hasSameRequestConfig}.
	 *
	 * @param candidate the entity being considered for the batch run
	 * @param head the entity the batch request will actually be built from
	 * @return true if {@code candidate} may be safely combined with {@code head} in one request
	 */
	private boolean hasSameRequestConfig(@NonNull final EdgeDataEntity candidate, @NonNull final EdgeDataEntity head) {
		if (!candidate.getConfiguration().equals(head.getConfiguration())) {
			return false;
		}
		if (!Objects.equals(EventUtils.getConfig(candidate.getEvent()), EventUtils.getConfig(head.getEvent()))) {
			return false;
		}
		return Objects.equals(getCustomRequestPath(candidate.getEvent()), getCustomRequestPath(head.getEvent()));
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
	 * Re-sends each entity in {@code batchEntities} individually after a batch 400. A 400 means nothing
	 * in the batch was ingested, so every event is safe to resend. Entities are processed FIFO: a
	 * resolved entity ({@link BatchOutcome.Kind#DONE}) counts toward the removable head prefix; the first
	 * recoverable failure ({@link BatchOutcome.Kind#RETRY}) stops the drain and reports the resolved
	 * count so the queue can remove those entities and retry the remainder. Mirrors
	 * {@code aepsdk-edge-ios}'s {@code EdgeHitProcessor.explodeAndResend}.
	 *
	 * @param batchEntities the entities from the exploded batch, in order
	 * @return {@link BatchOutcome#done} if all resolved, {@link BatchOutcome#retryBatch} if the very
	 *     first entity needs a retry, or {@link BatchOutcome#partialRemove} if some resolved before a
	 *     recoverable failure
	 */
	private BatchOutcome explodeAndResend(@NonNull final List<DataEntity> batchEntities) {
		int resolvedCount = 0;
		for (final DataEntity entity : batchEntities) {
			// Each entity is sent as a batch-of-1 through the same terminal path (handles success, drop,
			// and the existing single-event 400 behaviour). processSingleEntity only ever returns DONE
			// (resolved) or RETRY (recoverable failure).
			final BatchOutcome outcome = processSingleEntity(entity);
			if (outcome.getKind() == BatchOutcome.Kind.DONE) {
				resolvedCount++;
			} else {
				final int retryDelaySeconds = outcome.getRetryDelaySeconds();
				return resolvedCount > 0
					? BatchOutcome.partialRemove(resolvedCount, retryDelaySeconds)
					: BatchOutcome.retryBatch(retryDelaySeconds);
			}
		}
		return BatchOutcome.done(resolvedCount);
	}

	/**
	 * Builds the {@link EdgeHit} for one or more consecutive ExperienceEvents that share the head
	 * entity's snapshotted configuration (identity map, datastream ID + overrides, implementation
	 * details, endpoint). Used by both the batch path ({@link #processBatch}) and the single-event path
	 * ({@link #processExperienceEventHit}), which is just the one-event case.
	 *
	 * @param headEdgeEntity the entity whose snapshotted config drives the request
	 * @param events the ordered ExperienceEvents to include in the payload
	 * @return the assembled {@link EdgeHit}, or null if the datastream ID is missing/empty or the
	 *     payload could not be built (the caller decides how to treat a dropped hit)
	 */
	private EdgeHit buildExperienceEventHit(
		@NonNull final EdgeDataEntity headEdgeEntity,
		@NonNull final List<Event> events
	) {
		final RequestBuilder request = new RequestBuilder(namedCollection);
		request.addXdmPayload(headEdgeEntity.getIdentityMap());
		request.enableResponseStreaming(
			EdgeConstants.Defaults.REQUEST_CONFIG_RECORD_SEPARATOR,
			EdgeConstants.Defaults.REQUEST_CONFIG_LINE_FEED
		);

		if (stateCallback != null) {
			// Add Implementation Details to request (global) level
			request.addXdmPayload(stateCallback.getImplementationDetails());
		}

		final Map<String, Object> edgeConfig = headEdgeEntity.getConfiguration();
		String datastreamId = DataReader.optString(
			edgeConfig,
			EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID,
			null
		);
		final Map<String, Object> eventConfigMap = EventUtils.getConfig(headEdgeEntity.getEvent());
		datastreamId = processEventConfigOverrides(eventConfigMap, request, datastreamId);

		if (StringUtils.isNullOrEmpty(datastreamId)) {
			// The Edge configuration ID value should get validated when creating the Hit,
			// so we shouldn't get here in production.
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Cannot process Experience Event hit as the Edge Network configuration ID is null or empty, dropping current event (%s).",
				headEdgeEntity.getEvent().getUniqueIdentifier()
			);
			return null;
		}

		final JSONObject requestPayload = request.getPayloadWithExperienceEvents(events);
		if (requestPayload == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to build the request payload, dropping current event (%s).",
				headEdgeEntity.getEvent().getUniqueIdentifier()
			);
			return null;
		}

		final Map<String, Object> requestProperties = getRequestProperties(headEdgeEntity.getEvent());
		final EdgeEndpoint edgeEndpoint = getEdgeEndpoint(
			EdgeNetworkService.RequestType.INTERACT,
			edgeConfig,
			requestProperties
		);
		return new EdgeHit(datastreamId, requestPayload, edgeEndpoint);
	}

	/**
	 * Builds the {@link EdgeNetworkService.ResponseCallback} that forwards streamed responses, errors,
	 * and completion for {@code requestId} to the {@link NetworkResponseHandler}.
	 */
	private EdgeNetworkService.ResponseCallback buildResponseCallback(final String requestId) {
		return new EdgeNetworkService.ResponseCallback() {
			@Override
			public void onResponse(final String jsonResponse) {
				networkResponseHandler.processResponseOnSuccess(jsonResponse, requestId);
			}

			@Override
			public void onError(final String jsonError) {
				networkResponseHandler.processResponseOnError(jsonError, requestId);
			}

			@Override
			public void onComplete() {
				networkResponseHandler.processResponseOnComplete(requestId);
			}
		};
	}

	/**
	 * Sends {@code edgeHit} to the Edge Network and returns the full {@link RetryResult} (including the
	 * granular {@link EdgeNetworkService.NetworkRequestOutcome}). Validates the payload and URL, logs the
	 * request, and maintains {@code entityRetryIntervalMapping}. Never returns null — a null/empty
	 * payload, malformed URL, or null network result all map to a {@code DROP} outcome.
	 *
	 * <p>Shared by the batch path (which acts on the granular outcome directly) and the single-event
	 * wrapper {@link #sendNetworkRequest}.
	 *
	 * @param entityId unique identifier of the head entity, used for retry-interval tracking; may be null
	 * @param edgeHit the assembled hit to send
	 * @param requestHeaders HTTP headers to attach
	 * @param isBatchRequest true if {@code edgeHit} carries more than one event and a 400 should be
	 *                       exploded into individual resends; false for single-event requests, whose
	 *                       400 handling must be identical to a non-batching build
	 * @return the {@link RetryResult} describing the outcome; never null
	 */
	private RetryResult sendEdgeHit(
		final String entityId,
		final EdgeHit edgeHit,
		final Map<String, String> requestHeaders,
		final boolean isBatchRequest
	) {
		if (edgeHit == null || edgeHit.getPayload() == null || edgeHit.getPayload().length() == 0) {
			Log.warning(LOG_TAG, LOG_SOURCE, "Request body was null/empty, dropping this request.");
			return new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0);
		}

		final String url = networkService.buildUrl(
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
			return new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0);
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
				"Sending network request with id (%s) to URL '%s'. Error parsing JSON request: %s",
				edgeHit.getRequestId(),
				url,
				e.getLocalizedMessage()
			);
		}

		final RetryResult retryResult = networkService.doRequest(
			url,
			edgeHit.getPayload().toString(),
			requestHeaders,
			isBatchRequest,
			buildResponseCallback(edgeHit.getRequestId())
		);

		if (retryResult == null) {
			return new RetryResult(EdgeNetworkService.NetworkRequestOutcome.DROP, 0);
		}

		if (retryResult.getShouldRetry() == EdgeNetworkService.Retry.NO) {
			if (entityId != null) {
				entityRetryIntervalMapping.remove(entityId);
			}
		} else {
			// RETRY: doRequest suppresses onComplete, so the completion path never removes this
			// request's waiting-events / completion-index state. The retried send builds a fresh
			// requestId (see EdgeHit) and re-registers these events under it, so drop the now-orphaned
			// entry here — otherwise every retry leaks N waiting events (one per batched event) and
			// grows unbounded under sustained 5xx/429.
			networkResponseHandler.removeWaitingEvents(edgeHit.getRequestId());
			if (
				entityId != null &&
				retryResult.getRetryIntervalSeconds() != EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS
			) {
				entityRetryIntervalMapping.put(entityId, retryResult.getRetryIntervalSeconds());
			}
		}

		return retryResult;
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

		boolean hitCompleteResult = true;
		if (EventUtils.isExperienceEvent(entity.getEvent())) {
			hitCompleteResult = processExperienceEventHit(dataEntity.getUniqueIdentifier(), entity);
		} else if (EventUtils.isUpdateConsentEvent(entity.getEvent())) {
			hitCompleteResult = processUpdateConsentEventHit(dataEntity.getUniqueIdentifier(), entity);
		} else if (EventUtils.isResetComplete(entity.getEvent())) {
			// clear state store
			final StoreResponsePayloadManager payloadManager = new StoreResponsePayloadManager(namedCollection);
			payloadManager.deleteAllStorePayloads();
			hitCompleteResult = true; // Request complete, don't retry hit
		}

		processingResult.complete(hitCompleteResult);
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
		final RetryResult result = sendEdgeHit(entityId, edgeHit, requestHeaders, false);
		return result.getShouldRetry() != EdgeNetworkService.Retry.YES;
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
	 * Process and send a single ExperienceEvent network request. This is the one-event case of a batch;
	 * both share {@link #buildExperienceEventHit}.
	 *
	 * @param entityId the {@link DataEntity} unique identifier
	 * @param entity the {@link EdgeDataEntity} which encapsulates the request data
	 * @return true if the request processing is complete for this hit or false if processing is
	 * not complete and this hit must be retired.
	 */
	private boolean processExperienceEventHit(@NonNull final String entityId, @NonNull final EdgeDataEntity entity) {
		final List<Event> listOfEvents = new ArrayList<>();
		listOfEvents.add(entity.getEvent());

		final EdgeHit edgeHit = buildExperienceEventHit(entity, listOfEvents);
		if (edgeHit == null) {
			return true; // Config ID missing/empty or payload build failed — request complete, don't retry.
		}

		// NOTE: the order of these events need to be maintained as they were sent in the network request
		// otherwise the response callback cannot be matched
		networkResponseHandler.addWaitingEvents(edgeHit.getRequestId(), listOfEvents);

		return sendNetworkRequest(entityId, edgeHit, getRequestHeaders());
	}

	/**
	 * Process and send an Update Consent network request.
	 *
	 * @param entityId the {@link DataEntity} unique identifier
	 * @param entity the {@link EdgeDataEntity} which encapsulates the request data
	 * @return true if the request processing is complete for this hit or false if processing is
	 * not complete and this hit must be retired.
	 */
	private boolean processUpdateConsentEventHit(@NonNull final String entityId, @NonNull final EdgeDataEntity entity) {
		// Add Identity Map (global) and enable response streaming, then build the consent payload.
		final RequestBuilder request = new RequestBuilder(namedCollection);
		request.addXdmPayload(entity.getIdentityMap());
		request.enableResponseStreaming(
			EdgeConstants.Defaults.REQUEST_CONFIG_RECORD_SEPARATOR,
			EdgeConstants.Defaults.REQUEST_CONFIG_LINE_FEED
		);

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
