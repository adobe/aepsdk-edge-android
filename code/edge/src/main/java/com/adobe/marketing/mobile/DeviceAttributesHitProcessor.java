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

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import androidx.annotation.NonNull;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitProcessing;
import com.adobe.marketing.mobile.services.HitProcessingResult;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link HitProcessing} implementation for the consent-independent device attributes queue.
 * Publishes exactly one result event (E4) per terminal outcome; recoverable failures are retried without a result
 * event, and hits queued before an identities reset are discarded without a result event.
 */
class DeviceAttributesHitProcessor implements HitProcessing {

	private static final String LOG_SOURCE = "DeviceAttributesHitProcessor";

	private final DeviceAttributesNetworkService networkService;
	private final DeviceAttributesResponseHandler responseHandler;
	private final DeviceAttributesRequestBuilder requestBuilder;
	private final EdgeSharedStateCallback sharedStateCallback;
	private final EdgeStateCallback stateCallback;
	private final NamedCollection namedCollection;
	private final AdobeCallback<Event> dispatcher;
	private final Map<String, Integer> entityRetryIntervalMapping = new ConcurrentHashMap<>();

	/**
	 * @param networkService sender for the device attributes request
	 * @param sharedStateCallback used to read the Assurance integration id
	 * @param stateCallback used to read the implementation details and location hint, and to set the location hint
	 *                      received in the response
	 * @param namedCollection the Edge data store, holding the {@code state:store} payloads and the identities reset date
	 * @param dispatcher dispatches the result event (E4)
	 */
	DeviceAttributesHitProcessor(
		final DeviceAttributesNetworkService networkService,
		final EdgeSharedStateCallback sharedStateCallback,
		final EdgeStateCallback stateCallback,
		final NamedCollection namedCollection,
		final AdobeCallback<Event> dispatcher
	) {
		this.networkService = networkService;
		this.responseHandler = new DeviceAttributesResponseHandler(namedCollection, stateCallback);
		this.requestBuilder = new DeviceAttributesRequestBuilder(namedCollection);
		this.sharedStateCallback = sharedStateCallback;
		this.stateCallback = stateCallback;
		this.namedCollection = namedCollection;
		this.dispatcher = dispatcher;
	}

	@Override
	public int retryInterval(@NonNull final DataEntity entity) {
		final Integer retryInterval = entityRetryIntervalMapping.get(entity.getUniqueIdentifier());
		return retryInterval != null ? retryInterval : EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
	}

	@Override
	public void processHit(@NonNull final DataEntity entity, @NonNull final HitProcessingResult processingResult) {
		final EdgeDataEntity edgeEntity = EdgeDataEntity.fromDataEntity(entity);

		if (edgeEntity == null) {
			Log.error(
				LOG_TAG,
				LOG_SOURCE,
				"Dropping undecodable device attributes hit '%s'.",
				entity.getUniqueIdentifier()
			);
			dispatcher.call(DeviceAttributesEvents.createDropResult(entity.getUniqueIdentifier()));
			complete(entity, processingResult);
			return;
		}

		final Event event = edgeEntity.getEvent();

		// Checked on every attempt so hits waiting in retry backoff or restored after relaunch are covered.
		if (isQueuedBeforeIdentitiesReset(entity)) {
			// Identity already reports the reset to the requester, so no result event is dispatched.
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Discarding device attributes hit '%s' queued before identities reset.",
				entity.getUniqueIdentifier()
			);
			complete(entity, processingResult);
			return;
		}

		final Map<String, Object> edgeConfig = edgeEntity.getConfiguration();
		final String datastreamId = DataReader.optString(
			edgeConfig,
			EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID,
			null
		);

		if (StringUtils.isNullOrEmpty(datastreamId)) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Missing datastream id, dropping device attributes hit '%s'.",
				entity.getUniqueIdentifier()
			);
			finish(
				entity,
				DeviceAttributesEvents.createResult(
					event,
					DeviceAttributesConstants.Status.DROPPED,
					DeviceAttributesConstants.Reason.MISSING_CONFIG,
					null
				),
				processingResult
			);
			return;
		}

		final Map<String, Object> identityMap = DataReader.optTypedMap(
			Object.class,
			edgeEntity.getIdentityMap(),
			EdgeConstants.EventDataKeys.IDENTITY_MAP,
			null
		);
		final EdgeEndpoint endpoint = new EdgeEndpoint(
			EdgeNetworkService.RequestType.DEVICE_ATTRIBUTES,
			DataReader.optString(edgeConfig, EdgeConstants.SharedState.Configuration.EDGE_REQUEST_ENVIRONMENT, null),
			DataReader.optString(edgeConfig, EdgeConstants.SharedState.Configuration.EDGE_DOMAIN, null),
			null,
			stateCallback != null ? stateCallback.getLocationHint() : null
		);
		final String body = requestBuilder.buildRequestBody(
			event.getEventData(),
			stateCallback != null ? stateCallback.getImplementationDetails() : null,
			identityMap
		);
		final String url = body != null
			? networkService.buildUrl(endpoint, datastreamId, UUID.randomUUID().toString())
			: null;

		if (body == null || url == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to build device attributes request, dropping hit '%s'.",
				entity.getUniqueIdentifier()
			);
			finish(
				entity,
				DeviceAttributesEvents.createResult(
					event,
					DeviceAttributesConstants.Status.DROPPED,
					DeviceAttributesConstants.Reason.INVALID_PAYLOAD,
					null
				),
				processingResult
			);
			return;
		}

		networkService.send(url, body, getRequestHeaders(), result -> handle(result, entity, event, processingResult));
	}

	private void handle(
		final DeviceAttributesNetworkResult result,
		final DataEntity entity,
		final Event event,
		final HitProcessingResult processingResult
	) {
		switch (result.getType()) {
			case SUCCESS:
				// Mirrors the interact path: state:store from a request sent before a reset must not overwrite post-reset state.
				responseHandler.handle(result.getBody(), !isQueuedBeforeIdentitiesReset(entity));
				finish(
					entity,
					DeviceAttributesEvents.createResult(
						event,
						DeviceAttributesConstants.Status.SUCCESS,
						null,
						result.getHttpStatus()
					),
					processingResult
				);
				break;
			case RETRY:
				entityRetryIntervalMapping.put(entity.getUniqueIdentifier(), result.getRetryIntervalSeconds());
				processingResult.complete(false);
				break;
			case FAILED:
			default:
				finish(
					entity,
					DeviceAttributesEvents.createResult(
						event,
						DeviceAttributesConstants.Status.FAILED,
						DeviceAttributesConstants.Reason.NETWORK,
						result.getHttpStatus()
					),
					processingResult
				);
				break;
		}
	}

	/**
	 * Dispatches the terminal result event and removes the hit from the queue.
	 */
	private void finish(final DataEntity entity, final Event result, final HitProcessingResult processingResult) {
		dispatcher.call(result);
		complete(entity, processingResult);
	}

	private void complete(final DataEntity entity, final HitProcessingResult processingResult) {
		entityRetryIntervalMapping.remove(entity.getUniqueIdentifier());
		processingResult.complete(true);
	}

	/**
	 * Reads the identities reset date persisted by {@link NetworkResponseHandler#setLastResetDate(long)} (read-only).
	 */
	private boolean isQueuedBeforeIdentitiesReset(final DataEntity entity) {
		if (namedCollection == null) {
			return false;
		}

		final long resetDate = namedCollection.getLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, 0);
		return resetDate > 0 && entity.getTimestamp().getTime() < resetDate;
	}

	/**
	 * Adds the Assurance integration id header when Assurance is enabled.
	 */
	private Map<String, String> getRequestHeaders() {
		final Map<String, String> requestHeaders = new HashMap<>();

		if (sharedStateCallback == null) {
			return requestHeaders;
		}

		final SharedStateResult assuranceStateResult = sharedStateCallback.getSharedState(
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
