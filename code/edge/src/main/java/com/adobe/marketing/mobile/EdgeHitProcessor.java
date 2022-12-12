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
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitProcessing;
import com.adobe.marketing.mobile.services.HitProcessingResult;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

		final Map<String, String> requestHeaders = getRequestHeaders();

		// Enable response streaming for all events
		request.enableResponseStreaming(
			EdgeConstants.Defaults.REQUEST_CONFIG_RECORD_SEPARATOR,
			EdgeConstants.Defaults.REQUEST_CONFIG_LINE_FEED
		);

		final String locationHint = stateCallback != null ? stateCallback.getLocationHint() : null;

		if (EventUtils.isExperienceEvent(entity.getEvent())) {
			try {
				if (stateCallback != null) {
					// Add Implementation Details to request (global) level
					request.addXdmPayload(stateCallback.getImplementationDetails());
				}

				final List<Event> listOfEvents = new ArrayList<>();
				listOfEvents.add(entity.getEvent());
				final JSONObject requestPayload = request.getPayloadWithExperienceEvents(listOfEvents);

				if (requestPayload != null) {
					Map<String, Object> edgeConfig = entity.getConfiguration();
					String edgeConfigId = (String) edgeConfig.get(
						EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID
					);
					String requestEnvironment = (String) edgeConfig.get(
						EdgeConstants.SharedState.Configuration.EDGE_REQUEST_ENVIRONMENT
					);
					String requestDomain = (String) edgeConfig.get(EdgeConstants.SharedState.Configuration.EDGE_DOMAIN);
					final EdgeHit edgeHit = new EdgeHit(
						edgeConfigId,
						requestPayload,
						EdgeNetworkService.RequestType.INTERACT,
						new EdgeEndpoint(requestEnvironment, requestDomain, locationHint)
					);

					// NOTE: the order of these events need to be maintained as they were sent in the network request
					// otherwise the response callback cannot be matched
					networkResponseHandler.addWaitingEvents(edgeHit.getRequestId(), listOfEvents);
					processingResult.complete(
						sendNetworkRequest(dataEntity.getUniqueIdentifier(), edgeHit, requestHeaders)
					);
					return;
				} else {
					Log.debug(
						LOG_TAG,
						LOG_SOURCE,
						"Failed to build the request payload, dropping current event (%s).",
						entity.getEvent().getUniqueIdentifier()
					);
				}
			} catch (Exception e) {
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Failed to parse JSON requestPayload for event id (%s), skipping.",
					entity.getEvent().getUniqueIdentifier()
				);
			}
		} else if (EventUtils.isUpdateConsentEvent(entity.getEvent())) {
			// Build and send the consent network request to Experience Edge
			final JSONObject consentPayload = request.getConsentPayload(entity.getEvent());

			if (consentPayload != null) {
				Map<String, Object> edgeConfig = entity.getConfiguration();
				String edgeConfigId = (String) edgeConfig.get(EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID);
				String requestEnvironment = (String) edgeConfig.get(
					EdgeConstants.SharedState.Configuration.EDGE_REQUEST_ENVIRONMENT
				);
				String requestDomain = (String) edgeConfig.get(EdgeConstants.SharedState.Configuration.EDGE_DOMAIN);
				final EdgeHit edgeHit = new EdgeHit(
					edgeConfigId,
					consentPayload,
					EdgeNetworkService.RequestType.CONSENT,
					new EdgeEndpoint(requestEnvironment, requestDomain, locationHint)
				);

				networkResponseHandler.addWaitingEvent(edgeHit.getRequestId(), entity.getEvent());
				processingResult.complete(
					sendNetworkRequest(dataEntity.getUniqueIdentifier(), edgeHit, requestHeaders)
				);
				return;
			} else {
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Failed to build the consent payload, dropping current event (%s).",
					entity.getEvent().getUniqueIdentifier()
				);
			}
		} else if (EventUtils.isResetComplete(entity.getEvent())) {
			// clear state store
			final StoreResponsePayloadManager payloadManager = new StoreResponsePayloadManager(namedCollection);
			payloadManager.deleteAllStorePayloads();
		}

		processingResult.complete(true); // Request complete, don't retry hit
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
				List<String> removedWaitingEvents = networkResponseHandler.removeWaitingEvents(edgeHit.getRequestId());

				// unregister currently known completion callbacks
				if (removedWaitingEvents != null) {
					for (String eventId : removedWaitingEvents) {
						CompletionCallbacksManager.getInstance().unregisterCallback(eventId);
					}
				}
			}
		};

		String url = networkService.buildUrl(
			edgeHit.getType(),
			edgeHit.getEdgeEndpoint(),
			edgeHit.getConfigId(),
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
