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
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitProcessing;
import com.adobe.marketing.mobile.services.HitProcessingResult;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@link HitProcessing} implementation for the consent-independent device attributes queue.
 * Dispatches exactly one device attributes response event per hit once it succeeds, fails or is dropped; recoverable
 * failures are retried without a response event. Before each send attempt, the ECID in the hit's queued identity map is compared with the ECID in the latest
 * Identity shared state, and the hit is dropped if they differ.
 */
class DeviceAttributesHitProcessor implements HitProcessing {

	/**
	 * Provides the identity map from the latest Identity XDM shared state.
	 */
	interface LatestIdentityMapProvider {
		/**
		 * @return the latest identity map (empty if the state has none), or null if the state is not available
		 */
		@Nullable Map<String, Object> getLatestIdentityMap();
	}

	private static final String LOG_SOURCE = "DeviceAttributesHitProcessor";
	private static final String ECID_NAMESPACE = "ECID";
	private static final String IDENTITY_ID = "id";

	private final DeviceAttributesNetworkService networkService;
	private final DeviceAttributesRequestBuilder requestBuilder;
	private final EdgeSharedStateCallback sharedStateCallback;
	private final EdgeStateCallback stateCallback;
	private final LatestIdentityMapProvider latestIdentityMapProvider;
	private final AdobeCallback<Event> dispatcher;
	private final Map<String, Integer> entityRetryIntervalMapping = new ConcurrentHashMap<>();

	/**
	 * @param networkService sender for the device attributes request
	 * @param sharedStateCallback used to read the Assurance integration id
	 * @param stateCallback used to read the implementation details and location hint
	 * @param namedCollection the Edge data store, holding the {@code state:store} payloads sent as {@code meta.state}
	 * @param latestIdentityMapProvider provides the latest Identity identity map for the ECID check
	 * @param dispatcher dispatches the device attributes response event
	 */
	DeviceAttributesHitProcessor(
		final DeviceAttributesNetworkService networkService,
		final EdgeSharedStateCallback sharedStateCallback,
		final EdgeStateCallback stateCallback,
		final NamedCollection namedCollection,
		final LatestIdentityMapProvider latestIdentityMapProvider,
		final AdobeCallback<Event> dispatcher
	) {
		this.networkService = networkService;
		this.requestBuilder = new DeviceAttributesRequestBuilder(namedCollection);
		this.sharedStateCallback = sharedStateCallback;
		this.stateCallback = stateCallback;
		this.latestIdentityMapProvider = latestIdentityMapProvider;
		this.dispatcher = dispatcher;
	}

	@Override
	public int retryInterval(@NonNull final DataEntity entity) {
		final Integer retryInterval = entityRetryIntervalMapping.get(entity.getUniqueIdentifier());
		return retryInterval != null ? retryInterval : EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
	}

	@Override
	public void processHit(@NonNull final DataEntity entity, @NonNull final HitProcessingResult processingResult) {
		final HitAttempt attempt = new HitAttempt(entity, processingResult);

		try {
			processHit(attempt);
		} catch (final Throwable t) {
			attempt.fail(t);
		}
	}

	private void processHit(final HitAttempt attempt) {
		final DataEntity entity = attempt.entity;
		final EdgeDataEntity edgeEntity = EdgeDataEntity.fromDataEntity(entity);

		if (edgeEntity == null) {
			Log.error(
				LOG_TAG,
				LOG_SOURCE,
				"Dropping undecodable device attributes hit '%s'.",
				entity.getUniqueIdentifier()
			);
			attempt.finish(DeviceAttributesEvents.createDroppedResponse(entity.getUniqueIdentifier()));
			return;
		}

		final Event event = edgeEntity.getEvent();
		attempt.requestEvent = event;
		final Map<String, Object> identityMap = DataReader.optTypedMap(
			Object.class,
			edgeEntity.getIdentityMap(),
			EdgeConstants.EventDataKeys.IDENTITY_MAP,
			null
		);

		// Checked on every attempt so hits waiting in retry backoff or restored after relaunch are covered.
		final Map<String, Object> latestIdentityMap = getLatestIdentityMap();

		if (latestIdentityMap == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Latest Identity shared state is unavailable, will retry device attributes hit '%s'.",
				entity.getUniqueIdentifier()
			);
			attempt.retry();
			return;
		}

		if (!Objects.equals(getEcid(identityMap), getEcid(latestIdentityMap))) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"ECID changed since device attributes hit '%s' was queued, dropping.",
				entity.getUniqueIdentifier()
			);
			attempt.finish(
				DeviceAttributesEvents.createResponse(
					event,
					DeviceAttributesConstants.Status.DROPPED,
					DeviceAttributesConstants.Reason.IDENTITY_CHANGED,
					null
				)
			);
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
			attempt.finish(
				DeviceAttributesEvents.createResponse(
					event,
					DeviceAttributesConstants.Status.DROPPED,
					DeviceAttributesConstants.Reason.MISSING_CONFIG,
					null
				)
			);
			return;
		}

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
			attempt.finish(
				DeviceAttributesEvents.createResponse(
					event,
					DeviceAttributesConstants.Status.DROPPED,
					DeviceAttributesConstants.Reason.INVALID_PAYLOAD,
					null
				)
			);
			return;
		}

		networkService.send(
			url,
			body,
			getRequestHeaders(),
			result -> {
				try {
					handle(result, attempt, event);
				} catch (final Throwable t) {
					attempt.fail(t);
				}
			}
		);
	}

	private void handle(final DeviceAttributesNetworkResult result, final HitAttempt attempt, final Event event) {
		switch (result.getType()) {
			case SUCCESS:
				// Success is decided by the status code only; the response body is not processed.
				attempt.finish(
					DeviceAttributesEvents.createResponse(
						event,
						DeviceAttributesConstants.Status.SUCCESS,
						null,
						result.getHttpStatus()
					)
				);
				break;
			case RETRY:
				entityRetryIntervalMapping.put(attempt.entity.getUniqueIdentifier(), result.getRetryIntervalSeconds());
				attempt.retry();
				break;
			case FAILED:
			default:
				attempt.finish(
					DeviceAttributesEvents.createResponse(
						event,
						DeviceAttributesConstants.Status.FAILED,
						DeviceAttributesConstants.Reason.NETWORK,
						result.getHttpStatus()
					)
				);
				break;
		}
	}

	/**
	 * Tracks one processing attempt of a hit so that it is completed exactly once and at most one response event is
	 * dispatched for it, including when an unexpected exception or error is thrown while processing the hit or while
	 * handling the network result.
	 */
	private final class HitAttempt {

		private final DataEntity entity;
		private final HitProcessingResult processingResult;
		private final AtomicBoolean completed = new AtomicBoolean(false);
		private final AtomicBoolean responseDispatched = new AtomicBoolean(false);

		/** The decoded request event, set once the hit has been decoded. */
		private volatile Event requestEvent;

		HitAttempt(final DataEntity entity, final HitProcessingResult processingResult) {
			this.entity = entity;
			this.processingResult = processingResult;
		}

		/**
		 * Dispatches the device attributes response event and removes the hit from the queue.
		 */
		void finish(final Event response) {
			if (completed.get()) {
				return;
			}

			dispatchOnce(response);
			complete(true);
		}

		/**
		 * Keeps the hit in the queue so it is retried.
		 */
		void retry() {
			complete(false);
		}

		/**
		 * Handles an unexpected exception or error: dispatches a {@code dropped} / {@code invalidPayload} response unless
		 * one was already dispatched, and removes the hit so the queue keeps moving.
		 */
		void fail(final Throwable error) {
			Log.error(
				LOG_TAG,
				LOG_SOURCE,
				"Unexpected error processing device attributes hit '%s', dropping: %s",
				entity.getUniqueIdentifier(),
				error.getLocalizedMessage()
			);

			if (completed.get()) {
				return;
			}

			try {
				dispatchOnce(
					requestEvent != null
						? DeviceAttributesEvents.createResponse(
							requestEvent,
							DeviceAttributesConstants.Status.DROPPED,
							DeviceAttributesConstants.Reason.INVALID_PAYLOAD,
							null
						)
						: DeviceAttributesEvents.createDroppedResponse(entity.getUniqueIdentifier())
				);
			} catch (final Throwable dispatchError) {
				Log.error(
					LOG_TAG,
					LOG_SOURCE,
					"Failed to dispatch the response for device attributes hit '%s': %s",
					entity.getUniqueIdentifier(),
					dispatchError.getLocalizedMessage()
				);
			}

			complete(true);
		}

		// The flag is set before dispatching so that a failing dispatch is never retried as a second response.
		private void dispatchOnce(final Event response) {
			if (responseDispatched.compareAndSet(false, true)) {
				dispatcher.call(response);
			}
		}

		private void complete(final boolean remove) {
			if (!completed.compareAndSet(false, true)) {
				return;
			}

			if (remove) {
				entityRetryIntervalMapping.remove(entity.getUniqueIdentifier());
			}

			processingResult.complete(remove);
		}
	}

	/**
	 * @return the latest Identity identity map, or null if it is unavailable
	 */
	@Nullable private Map<String, Object> getLatestIdentityMap() {
		return latestIdentityMapProvider != null ? latestIdentityMapProvider.getLatestIdentityMap() : null;
	}

	/**
	 * Reads {@code identityMap["ECID"][0]["id"]}. Index 0 is Edge Identity's current ECID; a legacy ECID, if any,
	 * follows it.
	 *
	 * @param identityMap the identity map
	 * @return the current ECID, or null if not present
	 */
	@Nullable private static String getEcid(@Nullable final Map<String, Object> identityMap) {
		final List<Map<String, Object>> ecids = DataReader.optTypedListOfMap(
			Object.class,
			identityMap,
			ECID_NAMESPACE,
			null
		);

		if (ecids == null || ecids.isEmpty()) {
			return null;
		}

		return DataReader.optString(ecids.get(0), IDENTITY_ID, null);
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
