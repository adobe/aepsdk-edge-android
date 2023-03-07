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
import androidx.annotation.VisibleForTesting;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.PersistentHitQueue;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
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
		public void createSharedState(Map<String, Object> state, Event event) {
			getApi().createSharedState(state, event);
		}
	};

	@VisibleForTesting
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

	@NonNull
	@Override
	protected String getFriendlyName() {
		return EdgeConstants.FRIENDLY_NAME;
	}

	@NonNull
	@Override
	protected String getVersion() {
		return EdgeConstants.EXTENSION_VERSION;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * The following listeners are registered during this extension's registration:
	 * <ul>
	 *     <li> EventType {@link EventType#EDGE} and EventSource {@link EventSource#REQUEST_CONTENT}</li>
	 *     <li> EventType {@link EventType#CONSENT} and EventSource {@link EventSource#RESPONSE_CONTENT}</li>
	 *     <li> EventType {@link EventType#EDGE} and EventSource {@link EventSource#UPDATE_CONSENT}</li>
	 *     <li> EventType {@link EventType#EDGE_IDENTITY} and EventSource {@link EventSource#RESET_COMPLETE}</li>
	 *     <li> EventType {@link EventType#EDGE} and EventSource {@link EventSource#REQUEST_IDENTITY}</li>
	 *     <li> EventType {@link EventType#EDGE} and EventSource {@link EventSource#UPDATE_IDENTITY}</li>
	 * </ul>
	 * </p>
	 */
	@Override
	protected void onRegistered() {
		super.onRegistered();

		// register a listener for Edge request events
		getApi().registerEventListener(EventType.EDGE, EventSource.REQUEST_CONTENT, this::handleExperienceEventRequest);

		// register listener for consent preferences updates to update the queue state
		getApi()
			.registerEventListener(
				EventType.CONSENT,
				EventSource.RESPONSE_CONTENT,
				this::handleConsentPreferencesUpdate
			);

		// register listener for consent update request events
		getApi().registerEventListener(EventType.EDGE, EventSource.UPDATE_CONSENT, this::handleConsentUpdate);

		// register listener for identity reset complete
		getApi().registerEventListener(EventType.EDGE_IDENTITY, EventSource.RESET_COMPLETE, this::handleResetComplete);

		// register listener for edge get location hint
		getApi().registerEventListener(EventType.EDGE, EventSource.REQUEST_IDENTITY, this::handleGetLocationHint);

		// register listener for edge update location hint
		getApi().registerEventListener(EventType.EDGE, EventSource.UPDATE_IDENTITY, this::handleSetLocationHint);
	}

	@Override
	protected void onUnregistered() {
		super.onUnregistered();
		hitQueue.close();
	}

	@Override
	public boolean readyForEvent(@NonNull Event event) {
		if (!state.bootupIfNeeded()) {
			return false;
		}

		if (EventUtils.isExperienceEvent(event) || EventUtils.isUpdateConsentEvent(event)) {
			return getConfigurationState(event) != null && getIdentityXDMState(event) != null;
		} else if (EventUtils.isResetComplete(event)) {
			// use barrier to wait for EdgeIdentity to handle the reset
			return getConfigurationState(event) != null && getIdentityXDMState(event, true) != null;
		}

		return true;
	}

	/**
	 * Handler for Experience Edge Request Content events.
	 * Valid Configuration and Identity shared states are required for processing the event. If a valid Configuration shared state is
	 * available, but no {@code edge.configId } is found or {@link #shouldIgnore(Event)}` returns true, the event is dropped.
	 *
	 * @param event an event containing {@link ExperienceEvent} data for processing; the event data should not be null/empty
	 */
	void handleExperienceEventRequest(@NonNull final Event event) {
		if (MapUtils.isNullOrEmpty(event.getEventData())) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Event with id %s contained no data, ignoring.",
				event.getUniqueIdentifier()
			);
			return;
		}

		if (shouldIgnore(event)) {
			return;
		}
		processAndQueueEvent(event);
	}

	/**
	 * Handles the Consent Update event
	 *
	 * @param event current event to process; the event data should not be null/empty
	 */
	void handleConsentUpdate(@NonNull final Event event) {
		if (MapUtils.isNullOrEmpty(event.getEventData())) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Consent update request with id %s contained no data, ignoring.",
				event.getUniqueIdentifier()
			);
			return;
		}

		processAndQueueEvent(event);
	}

	/**
	 * Handles the {@link EventType#CONSENT} - {@link EventSource#RESPONSE_CONTENT} event for the collect consent change
	 *
	 * @param event current event to process; the event data should not be null/empty
	 */
	void handleConsentPreferencesUpdate(@NonNull final Event event) {
		if (MapUtils.isNullOrEmpty(event.getEventData())) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Consent preferences with id %s contained no data, ignoring.",
				event.getUniqueIdentifier()
			);
			return;
		}
		state.updateCurrentConsent(ConsentStatus.getCollectConsentOrDefault(event.getEventData()));
	}

	/**
	 * Handles the {@link EventType#EDGE_IDENTITY} - {@link EventSource#RESET_COMPLETE} event for the identities reset completion
	 *
	 * @param event current event to process
	 */
	void handleResetComplete(@NonNull final Event event) {
		getNetworkResponseHandler().setLastResetDate(event.getTimestamp()); // set last reset date

		if (hitQueue == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Hit queue is null, unable to queue reset complete event with id (%s).",
				event.getUniqueIdentifier()
			);
			return;
		}

		EdgeDataEntity entity = new EdgeDataEntity(event);
		hitQueue.queue(entity.toDataEntity());
	}

	/**
	 * Handles the {@link EventType#EDGE} - {@link EventSource#REQUEST_IDENTITY} event for getting the location hint.
	 *
	 * @param event current event to process
	 */
	void handleGetLocationHint(@NonNull final Event event) {
		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EventType.EDGE,
			EventSource.RESPONSE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKeys.LOCATION_HINT, state.getLocationHint());
					}
				}
			)
			.inResponseToEvent(event)
			.build();

		getApi().dispatch(responseEvent);
	}

	/**
	 * Handles the {@link EventType#EDGE} - {@link EventSource#UPDATE_IDENTITY} event for setting the location hint.
	 *
	 * @param event current event to process
	 */
	void handleSetLocationHint(@NonNull final Event event) {
		final Map<String, Object> eventData = event.getEventData();
		if (MapUtils.isNullOrEmpty(eventData)) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Location Hint update request event with id %s contained no data, ignoring.",
				event.getUniqueIdentifier()
			);
			return;
		}

		try {
			final String hint = DataReader.getString(eventData, EdgeConstants.EventDataKeys.LOCATION_HINT);
			state.setLocationHint(hint, EdgeConstants.Defaults.LOCATION_HINT_TTL_SEC);
		} catch (DataReaderException e) {
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
	 * Processes an Experience Event or Consent Update Event and adds it to the hit queue.
	 */
	void processAndQueueEvent(@NonNull final Event event) {
		Map<String, Object> configReady = getConfigurationState(event);

		if (configReady == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to process the event '%s', Configuration shared state is null.",
				event.getUniqueIdentifier()
			);
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

		Map<String, Object> identityReady = getIdentityXDMState(event);

		if (identityReady == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to process the event '%s', Identity shared state is null.",
				event.getUniqueIdentifier()
			);
			return; // Shouldn't get here as Identity state is checked in readyForEvent
		}

		if (hitQueue == null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Hit queue is null, unable to queue Edge event with id (%s).",
				event.getUniqueIdentifier()
			);
			return;
		}

		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, identityReady);
		hitQueue.queue(entity.toDataEntity());
	}

	/**
	 * Retrieves Configuration Shared State for the provided event.
	 *
	 * @param event current event to process
	 * @return the Configuration shared state or null if it is pending
	 */
	private Map<String, Object> getConfigurationState(@NonNull final Event event) {
		SharedStateResult sharedStateResult = getApi()
			.getSharedState(EdgeConstants.SharedState.CONFIGURATION, event, false, SharedStateResolution.ANY);
		if (sharedStateResult == null || sharedStateResult.getStatus() != SharedStateStatus.SET) {
			return null;
		}

		return sharedStateResult.getValue();
	}

	/**
	 * Retrieves Identity XDM Shared State for the provided event.
	 * @param event current event to process
	 * @return the Identity shared state or null if it is pending
	 */
	private Map<String, Object> getIdentityXDMState(@NonNull final Event event) {
		return getIdentityXDMState(event, false);
	}

	/**
	 * Retrieves Identity XDM Shared State for the provided event.
	 * @param event current event to process
	 * @param barrier if true, returns next Identity state at or past the {@code event}, but not the state before {@code event}.
	 * @return the Identity shared state or null if it is pending
	 */
	private Map<String, Object> getIdentityXDMState(@NonNull final Event event, final boolean barrier) {
		SharedStateResult sharedStateResult = getApi()
			.getXDMSharedState(EdgeConstants.SharedState.IDENTITY, event, barrier, SharedStateResolution.ANY);
		if (sharedStateResult == null || sharedStateResult.getStatus() != SharedStateStatus.SET) {
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
	private boolean shouldIgnore(@NonNull final Event event) {
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
	private ConsentStatus getConsentForEvent(@NonNull final Event event) {
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
