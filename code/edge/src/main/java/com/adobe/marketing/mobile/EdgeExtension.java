/*
  Copyright 2019 Adobe. All rights reserved.
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
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.PersistentHitQueue;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class {@code EdgeExtension} is an implementation of {@link Extension} and is responsible
 * for registering event listeners and processing events
 * heard by those listeners. The extension is registered to the Mobile SDK by calling
 * {@link MobileCore#registerExtensions(List, AdobeCallback)}.
 */
class EdgeExtension extends Extension {

	private final String LOG_SOURCE = "EdgeExtension";
	/* used for creating the networkResponseHandler on demand */
	private final Object networkResponseHandlerMutex = new Object();
	private NetworkResponseHandler networkResponseHandler;
	private NamedCollection dataStore;
	private final HitQueuing hitQueue;

	/*
	 * An {@code EdgeSharedStateCallback} to create and retrieve shared states.
	 */
	private final EdgeSharedStateCallback sharedStateCallback = new EdgeSharedStateCallback() {
		@Override
		public SharedStateResult getSharedState(final String stateOwner, final Event event) {
			return getApi().getSharedState(stateOwner, event, false, SharedStateResolution.ANY);
		}

		@Override
		public void setSharedState(Map<String, Object> state, Event event) {
			getApi().createSharedState(state, event);
		}
	};

	// package protected for testing
	final EdgeState state;

	/**
	 * Called by the Mobile SDK when registering the extension.
	 * Initialize the extension and register event listeners.
	 * It is recommended to listen for each specific event the extension is interested in.
	 * Use of a wildcard listener is discouraged in production environments.
	 *
	 * @param extensionApi the {@link ExtensionApi} instance for this extension
	 */
	protected EdgeExtension(final ExtensionApi extensionApi) {
		this(extensionApi, null);
	}

	/**
	 * Called by the Mobile SDK when registering the extension.
	 * Initialize the extension and register event listeners.
	 * It is recommended to listen for each specific event the extension is interested in.
	 * Use of a wildcard listener is discouraged in production environments.
	 * <p>
	 * This constructor is intended to be used for testing purposes.
	 *
	 * @param extensionApi the {@link ExtensionApi} instance for this extension
	 * @param hitQueue the {@link HitQueuing} instance for this extension for queuing received events.
	 *                 If null, a new {@code HitQueuing} instance is created.
	 */
	protected EdgeExtension(final ExtensionApi extensionApi, final HitQueuing hitQueue) {
		super(extensionApi);
		if (hitQueue == null) {
			final EdgeHitProcessor hitProcessor = new EdgeHitProcessor(
				getNetworkResponseHandler(),
				new EdgeNetworkService(ServiceProvider.getInstance().getNetworkService()),
				getNamedCollection(),
				sharedStateCallback,
				new EdgeExtensionStateCallback()
			);

			final DataQueue dataQueue = ServiceProvider.getInstance().getDataQueueService().getDataQueue(getName());
			this.hitQueue = new PersistentHitQueue(dataQueue, hitProcessor);
		} else {
			this.hitQueue = hitQueue;
		}

		state = new EdgeState(this.hitQueue, new EdgeProperties(getNamedCollection()), sharedStateCallback);
	}

	@NonNull
	@Override
	protected String getName() {
		return EdgeConstants.EXTENSION_NAME;
	}

	@Override
	protected String getFriendlyName() {
		return EdgeConstants.FRIENDLY_NAME;
	}

	@Override
	protected String getVersion() {
		return EdgeConstants.EXTENSION_VERSION;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * The following listeners are registered during this extension's registration.
	 * <ul>
	 *     <li> EventType {@link EdgeConstants.EventType#EDGE} and EventSource {@link EdgeConstants.EventSource#REQUEST_CONTENT}</li>
	 *     <li> EventType {@link EdgeConstants.EventType#CONSENT} and EventSource {@link EdgeConstants.EventSource#RESPONSE_CONTENT}</li>
	 *     <li> EventType {@link EdgeConstants.EventType#EDGE} and EventSource {@link EdgeConstants.EventSource#UPDATE_CONSENT}</li>
	 *     <li> EventType {@link EdgeConstants.EventType#EDGE_IDENTITY} and EventSource {@link EdgeConstants.EventSource#RESET_COMPLETE}</li>
	 *     <li> EventType {@link EdgeConstants.EventType#EDGE} and EventSource {@link EdgeConstants.EventSource#REQUEST_IDENTITY}</li>
	 *     <li> EventType {@link EdgeConstants.EventType#EDGE} and EventSource {@link EdgeConstants.EventSource#UPDATE_IDENTITY}</li>
	 * </ul>
	 * </p>
	 */
	@Override
	protected void onRegistered() {
		// register a listener for Edge request events
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.REQUEST_CONTENT,
				this::handleExperienceEventRequest
			);

		// register listener for consent preferences updates to update the queue state
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.CONSENT,
				EdgeConstants.EventSource.RESPONSE_CONTENT,
				this::handleConsentPreferencesUpdate
			);

		// register listener for consent update request events
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.UPDATE_CONSENT,
				this::handleConsentUpdate
			);

		// register listener for identity reset complete
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE_IDENTITY,
				EdgeConstants.EventSource.RESET_COMPLETE,
				this::handleResetComplete
			);

		// register listener for edge get location hint
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.REQUEST_IDENTITY,
				this::handleGetLocationHint
			);

		// register listener for edge update location hint
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.UPDATE_IDENTITY,
				this::handleSetLocationHint
			);
	}

	@Override
	public boolean readyForEvent(@NonNull Event event) {
		if (!state.bootupIfNeeded()) {
			return false;
		}

		if (EventUtils.isExperienceEvent(event) || EventUtils.isUpdateConsentEvent(event)) {
			SharedStateResult configurationState = getApi()
				.getSharedState(EdgeConstants.SharedState.CONFIGURATION, event, false, SharedStateResolution.ANY);
			SharedStateResult identityState = getApi()
				.getXDMSharedState(EdgeConstants.SharedState.IDENTITY, event, false, SharedStateResolution.ANY);
			return (
				configurationState != null &&
				identityState != null &&
				configurationState.getStatus() == SharedStateStatus.SET &&
				identityState.getStatus() == SharedStateStatus.SET
			);
		} else if (EventUtils.isResetComplete(event)) {
			SharedStateResult configurationState = getApi()
				.getSharedState(EdgeConstants.SharedState.CONFIGURATION, event, false, SharedStateResolution.ANY);
			// use barrier to wait for EdgeIdentity to handle the reset
			SharedStateResult identityState = getApi()
				.getXDMSharedState(EdgeConstants.SharedState.IDENTITY, event, true, SharedStateResolution.ANY);
			return (
				configurationState != null &&
				identityState != null &&
				configurationState.getStatus() == SharedStateStatus.SET &&
				identityState.getStatus() == SharedStateStatus.SET
			);
		}

		return true;
	}

	/**
	 * Handler for Experience Edge Request Content events.
	 * Valid Configuration and Identity shared states are required for processing the event. If a valid Configuration shared state is
	 * available, but no {@code edge.configId } is found or {@link #shouldIgnore(Event)}` returns true, the event is dropped.
	 *
	 * @param event an event containing {@link ExperienceEvent} data for processing; the event and the event data should not be null, checking in listener
	 */
	void handleExperienceEventRequest(@NonNull final Event event) {
		if (shouldIgnore(event)) {
			return;
		}
		processEdgeEvent(event);
	}

	/**
	 * Handles the Consent Update event
	 *
	 * @param event current event to process; the event and the event data should not be null, checking in listener
	 */
	void handleConsentUpdate(@NonNull final Event event) {
		processEdgeEvent(event);
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#CONSENT} - {@link EdgeConstants.EventSource#RESPONSE_CONTENT} event for the collect consent change
	 *
	 * @param event current event to process; the event and the event data should not be null, checking in listener
	 */
	void handleConsentPreferencesUpdate(@NonNull final Event event) {
		state.updateCurrentConsent(ConsentStatus.getCollectConsentOrDefault(event.getEventData()));
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#EDGE_IDENTITY} - {@link EdgeConstants.EventSource#RESET_COMPLETE} event for the identities reset completion
	 *
	 * @param event current event to process
	 */
	void handleResetComplete(@NonNull final Event event) {
		EdgeDataEntity entity = new EdgeDataEntity(event);
		networkResponseHandler.setLastResetDate(event.getTimestamp()); // set last reset date

		if (hitQueue == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Hit queue is null, unable to queue reset complete event with id (%s).",
				event.getUniqueIdentifier()
			);
			return;
		}

		hitQueue.queue(
			new DataEntity(
				event.getUniqueIdentifier(),
				new Date(event.getTimestamp()),
				EdgeDataEntitySerializer.serialize(entity)
			)
		);
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#EDGE} - {@link EdgeConstants.EventSource#REQUEST_IDENTITY} event for getting the location hint.
	 *
	 * @param event current event to process
	 */
	void handleGetLocationHint(@NonNull final Event event) {
		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.RESPONSE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKey.LOCATION_HINT, state.getLocationHint());
					}
				}
			)
			.inResponseToEvent(event)
			.build();

		getApi().dispatch(responseEvent);
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#EDGE} - {@link EdgeConstants.EventSource#UPDATE_IDENTITY} event for setting the location hint.
	 *
	 * @param event current event to process
	 */
	void handleSetLocationHint(@NonNull final Event event) {
		final Map<String, Object> eventData = event.getEventData();

		try {
			final String hint = (String) eventData.get(EdgeConstants.EventDataKey.LOCATION_HINT);
			state.setLocationHint(hint, EdgeConstants.Defaults.LOCATION_HINT_TTL_SEC);
		} catch (ClassCastException e) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Failed to update location hint for request event '%s' with error '%s'.",
				event.getUniqueIdentifier(),
				e.getLocalizedMessage()
			);
		}
	}

	/**
	 * Processes an Experience Event or Consent Update Event.
	 */
	void processEdgeEvent(@NonNull final Event event) {
		Map<String, Object> configReady = getConfig(event);

		if (configReady == null) {
			return; // Shouldn't get here as Configuration state is checked in readyForEvent
		}

		Map<String, Object> edgeConfig = EventUtils.getEdgeConfiguration(configReady);

		if (
			StringUtils.isNullOrEmpty(
				DataReader.optString(edgeConfig, EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID, null)
			)
		) {
			// drop this event if configId is invalid (config id is a mandatory parameters for requests to Konductor)
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Missing edge.configId in Configuration, dropping event with unique id (%s)",
				event.getUniqueIdentifier()
			);
			return;
		}

		Map<String, Object> identityReady = getIdentity(event);

		if (identityReady == null) {
			return; // Shouldn't get here as Identity state is checked in readyForEvent
		}

		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, identityReady);

		if (hitQueue == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Hit queue is null, unable to queue Edge event with id (%s).",
				event.getUniqueIdentifier()
			);
			return;
		}

		hitQueue.queue(
			new DataEntity(
				event.getUniqueIdentifier(),
				new Date(event.getTimestamp()),
				EdgeDataEntitySerializer.serialize(entity)
			)
		);
	}

	/**
	 * Retrieves Configuration Shared State for the provided event.
	 *
	 * @param event current event to process
	 * @return the Configuration shared state or null if it is pending
	 */
	private Map<String, Object> getConfig(final Event event) {
		SharedStateResult sharedStateResult = getApi()
			.getSharedState(EdgeConstants.SharedState.CONFIGURATION, event, false, SharedStateResolution.ANY);
		if (sharedStateResult == null || sharedStateResult.getStatus() != SharedStateStatus.SET) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Configuration is pending, couldn't process event at this time, waiting...");
			return null;
		}

		return sharedStateResult.getValue();
	}

	/**
	 * Retrieves Identity XDM Shared State for the provided event.
	 *
	 * @param event current event to process
	 * @return the Identity shared state or null if it is pending
	 */
	private Map<String, Object> getIdentity(final Event event) {
		SharedStateResult sharedStateResult = getApi()
			.getXDMSharedState(EdgeConstants.SharedState.IDENTITY, event, false, SharedStateResolution.ANY);
		if (sharedStateResult == null || sharedStateResult.getStatus() != SharedStateStatus.SET) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Identity shared state is pending, could not process queued events at this time, waiting..."
			);
			return null;
		}

		return sharedStateResult.getValue();
	}

	/**
	 * Determines if the event should be ignored by the Edge extension.
	 *
	 * @param event the event to validate consent for
	 * @return true when collect consent is no, false otherwise
	 */
	private boolean shouldIgnore(final Event event) {
		ConsentStatus consentForEvent = getConsentForEvent(event);

		if (consentForEvent == ConsentStatus.NO) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Ignoring event with id %s due to collect consent setting (n).",
				event.getUniqueIdentifier()
			);
			return true;
		}

		return false;
	}

	/**
	 * Retrieves the {@link ConsentStatus} from the Consent XDM Shared state for current {@code event}
	 *
	 * @param event the current event to check Consent for
	 * @return {@code ConsentStatus} value from shared state or, if not found, current consent value
	 */
	private ConsentStatus getConsentForEvent(final Event event) {
		SharedStateResult sharedStateResult = getApi()
			.getXDMSharedState(EdgeConstants.SharedState.CONSENT, event, false, SharedStateResolution.ANY);
		if (sharedStateResult == null || sharedStateResult.getStatus() != SharedStateStatus.SET) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Consent XDM Shared state is unavailable for event %s, using current consent.",
				event.getUniqueIdentifier()
			);
			return state.getCurrentCollectConsent();
		}

		return ConsentStatus.getCollectConsentOrDefault(sharedStateResult.getValue());
	}

	private NamedCollection getNamedCollection() {
		if (dataStore == null) {
			dataStore =
				ServiceProvider.getInstance().getDataStoreService().getNamedCollection(EdgeConstants.EDGE_DATA_STORAGE);
		}

		return dataStore;
	}

	private NetworkResponseHandler getNetworkResponseHandler() {
		synchronized (networkResponseHandlerMutex) {
			if (networkResponseHandler == null) {
				networkResponseHandler =
					new NetworkResponseHandler(getNamedCollection(), new EdgeExtensionStateCallback());
			}
		}

		return networkResponseHandler;
	}

	/**
	 * Edge Extension's implementation of EdgeStateCallback.
	 */
	private class EdgeExtensionStateCallback implements EdgeStateCallback {

		@Override
		public Map<String, Object> getImplementationDetails() {
			return state != null ? state.getImplementationDetails() : null;
		}

		@Override
		public String getLocationHint() {
			return state != null ? state.getLocationHint() : null;
		}

		@Override
		public void setLocationHint(String hint, int ttlSeconds) {
			if (state != null) {
				state.setLocationHint(hint, ttlSeconds);
			}
		}
	}
}
