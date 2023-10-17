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
import java.util.ArrayList;
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

			return true; // Hit sent successfully
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
