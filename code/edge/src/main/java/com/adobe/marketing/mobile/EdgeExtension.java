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

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.PersistentHitQueue;
import com.adobe.marketing.mobile.services.ServiceProvider;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Class {@code EdgeExtension} is an implementation of {@link Extension} and is responsible
 * for registering {@link com.adobe.marketing.mobile.ExtensionListener}s and processing events
 * heard by those listeners. The extension is registered to the Mobile SDK by calling
 * {@link MobileCore#registerExtension(Class, ExtensionErrorCallback)}.
 */
class EdgeExtension extends Extension {

	private ExecutorService executorService;
	private final Object executorMutex = new Object();
	/* used for creating the networkResponseHandler on demand */
	private final Object mutex = new Object();
	/* cached events in memory until required shared states are resolved */
	private final ConcurrentLinkedQueue<Event> cachedEvents;
	private NamedCollection dataStore;
	private NetworkResponseHandler networkResponseHandler;
	private final HitQueuing hitQueue;

	// package protected for testing
	final EdgeState state;

	/**
	 * Called by the Mobile SDK when registering the extension.
	 * Initialize the extension and register event listeners.
	 * The example below uses {@link EdgeExtensionListener} to handle all interesting events,
	 * however separate {@link com.adobe.marketing.mobile.ExtensionListener} classes may be used
	 * instead.
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
	 * The example below uses {@link EdgeExtensionListener} to handle all interesting events,
	 * however separate {@link com.adobe.marketing.mobile.ExtensionListener} classes may be used
	 * instead.
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
		EdgeSharedStateCallback sharedStateCallback = new EdgeSharedStateCallback() {
			@Override
			public Map<String, Object> getSharedState(final String stateOwner, final Event event) {
				if (extensionApi == null) {
					MobileCore.log(
						LoggingMode.WARNING,
						LOG_TAG,
						"EdgeSharedStateCallback - Unable to get shared state, Extension API instance is null."
					);
					return null;
				}

				return extensionApi.getSharedEventState(
					stateOwner,
					event,
					new ExtensionErrorCallback<ExtensionError>() {
						@Override
						public void error(ExtensionError extensionError) {
							MobileCore.log(
								LoggingMode.WARNING,
								LOG_TAG,
								"EdgeSharedStateCallback - Unable to fetch shared state, failed with error: " +
								extensionError.getErrorName()
							);
						}
					}
				);
			}

			@Override
			public void setSharedState(Map<String, Object> state, Event event) {
				if (extensionApi == null) {
					MobileCore.log(
						LoggingMode.WARNING,
						LOG_TAG,
						"EdgeSharedStateCallback - Unable to set shared state, Extension API instance is null."
					);
					return;
				}

				extensionApi.setSharedEventState(
					state,
					event,
					new ExtensionErrorCallback<ExtensionError>() {
						@Override
						public void error(ExtensionError extensionError) {
							MobileCore.log(
								LoggingMode.WARNING,
								LOG_TAG,
								"EdgeSharedStateCallback - Unable to set shared state, failed with error: " +
								extensionError.getErrorName()
							);
						}
					}
				);
			}
		};

		final EdgeHitProcessor hitProcessor = new EdgeHitProcessor(
			getNetworkResponseHandler(),
			new EdgeNetworkService(ServiceProvider.getInstance().getNetworkService()),
			getNamedCollection(),
			sharedStateCallback,
			new EdgeExtensionStateCallback()
		);

		if (hitQueue == null) {
			final DataQueue dataQueue = ServiceProvider.getInstance().getDataQueueService().getDataQueue(getName());
			this.hitQueue = new PersistentHitQueue(dataQueue, hitProcessor);
		} else {
			this.hitQueue = hitQueue;
		}

		state = new EdgeState(this.hitQueue, new EdgeProperties(getNamedCollection()), sharedStateCallback);
		cachedEvents = new ConcurrentLinkedQueue<>();

		registerListeners();
	}

	private void registerListeners() {
		// register a listener for shared state changes
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.ADOBE_HUB,
				EdgeConstants.EventSource.ADOBE_SHARED_STATE,
				EdgeExtensionListener.class,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - There was an error registering Extension Listener for shared state events: " +
							extensionError.getErrorName()
						);
					}
				}
			);

		// register a listener for Edge request events
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.REQUEST_CONTENT,
				EdgeExtensionListener.class,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - There was an error registering Extension Listener for extension request content events: " +
							extensionError.getErrorName()
						);
					}
				}
			);

		// register listener for consent preferences updates to update the queue state
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.CONSENT,
				EdgeConstants.EventSource.RESPONSE_CONTENT,
				EdgeExtensionListener.class,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - There was an error registering Extension Listener for Consent response events: " +
							extensionError.getErrorName()
						);
					}
				}
			);

		// register listener for consent update request events
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.UPDATE_CONSENT,
				EdgeExtensionListener.class,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - There was an error registering Extension Listener for consent updates: " +
							extensionError.getErrorName()
						);
					}
				}
			);

		// register listener for identity reset complete
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE_IDENTITY,
				EdgeConstants.EventSource.RESET_COMPLETE,
				EdgeExtensionListener.class,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - There was an error registering Extension Listener for reset complete events: " +
							extensionError.getErrorName()
						);
					}
				}
			);

		// register listener for edge get location hint
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.REQUEST_IDENTITY,
				EdgeExtensionListener.class,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - There was an error registering Extension Listener for get location hint events: " +
							extensionError.getErrorName()
						);
					}
				}
			);

		// register listener for edge update location hint
		getApi()
			.registerEventListener(
				EdgeConstants.EventType.EDGE,
				EdgeConstants.EventSource.UPDATE_IDENTITY,
				EdgeExtensionListener.class,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - There was an error registering Extension Listener for set location hint events: " +
							extensionError.getErrorName()
						);
					}
				}
			);
	}

	/**
	 * Required override. Each extension must have a unique name within the application.
	 * @return unique name of this extension
	 */
	@Override
	protected String getName() {
		return EdgeConstants.EXTENSION_NAME;
	}

	/**
	 * Optional override.
	 * @return the version of this extension
	 */
	@Override
	protected String getVersion() {
		return EdgeConstants.EXTENSION_VERSION;
	}

	/**
	 * Optional override. Clean up any open resources before extension is deleted.
	 */
	@Override
	protected void onUnregistered() {
		super.onUnregistered();

		// the extension was unregistered
		// if the shared states are not used in the next registration they can be cleared in this method
		getApi().clearSharedEventStates(null);
	}

	/**
	 * Optional override but recommended to handle notifications of unexpected errors
	 * generated from the Mobile SDK.
	 * @param unexpectedError the error instance
	 */
	@Override
	protected void onUnexpectedError(final ExtensionUnexpectedError unexpectedError) {
		super.onUnexpectedError(unexpectedError);
	}

	/**
	 * Handler for Shared State updates of the {@code EventHub} to be used to determine if Consent extension is registered on bootup
	 * and updates on Configuration and Identity shared states to resume processing `cachedEvents`.
	 *
	 * @param event the update event; the event and the event data should not be null, checking in listener
	 */
	void handleSharedStateUpdate(final Event event) {
		if (EventUtils.isSharedStateUpdateFor(EdgeConstants.SharedState.HUB, event)) {
			final Map<String, Object> registeredExtensions = getApi()
				.getSharedEventState(EdgeConstants.SharedState.HUB, event, null);
			state.bootupIfNeeded(registeredExtensions);
		}

		if (
			EventUtils.isSharedStateUpdateFor(EdgeConstants.SharedState.CONFIGURATION, event) ||
			EventUtils.isSharedStateUpdateFor(EdgeConstants.SharedState.IDENTITY, event)
		) {
			processCachedEvents();
		}
	}

	/**
	 * Handler for Experience Edge Request Content events.
	 * Valid Configuration and Identity shared states are required for processing the event. If a valid Configuration shared state is
	 * available, but no {@code edge.configId } is found or {@link #shouldIgnore(Event)}` returns true, the event is dropped.
	 *
	 * @param event an event containing {@link ExperienceEvent} data for processing; the event and the event data should not be null, checking in listener
	 */
	void handleExperienceEventRequest(final Event event) {
		if (shouldIgnore(event)) {
			return;
		}

		processAddEvent(event);
	}

	/**
	 * Handles the Consent Update event
	 *
	 * @param event current event to process; the event and the event data should not be null, checking in listener
	 */
	void handleConsentUpdate(final Event event) {
		processAddEvent(event);
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#CONSENT} - {@link EdgeConstants.EventSource#RESPONSE_CONTENT} event for the collect consent change
	 *
	 * @param event current event to process; the event and the event data should not be null, checking in listener
	 */
	void handleConsentPreferencesUpdate(final Event event) {
		state.updateCurrentConsent(ConsentStatus.getCollectConsentOrDefault(event.getEventData()));
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#EDGE_IDENTITY} - {@link EdgeConstants.EventSource#RESET_COMPLETE} event for the identities reset completion
	 *
	 * @param event current event to process
	 */
	void handleResetComplete(final Event event) {
		processAddEvent(event);
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#EDGE} - {@link EdgeConstants.EventSource#REQUEST_IDENTITY} event for getting the location hint.
	 *
	 * @param event current event to process
	 */
	void handleGetLocationHint(final Event event) {
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
			.build();

		ExtensionErrorCallback<ExtensionError> errorCallback = new ExtensionErrorCallback<ExtensionError>() {
			@Override
			public void error(ExtensionError extensionError) {
				MobileCore.log(
					LoggingMode.DEBUG,
					LOG_TAG,
					"EdgeExtension - Failed to dispatch Edge response event for event " +
					event.getUniqueIdentifier() +
					" with error " +
					extensionError.getErrorName()
				);
			}
		};

		MobileCore.dispatchResponseEvent(responseEvent, event, errorCallback);
	}

	/**
	 * Handles the {@link EdgeConstants.EventType#EDGE} - {@link EdgeConstants.EventSource#UPDATE_IDENTITY} event for setting the location hint.
	 *
	 * @param event current event to process
	 */
	void handleSetLocationHint(final Event event) {
		final Map<String, Object> eventData = event.getEventData(); // do not need to null check on eventData, as they are done on listeners

		try {
			final String hint = (String) eventData.get(EdgeConstants.EventDataKey.LOCATION_HINT);
			state.setLocationHint(hint, EdgeConstants.Defaults.LOCATION_HINT_TTL_SEC);
		} catch (ClassCastException e) {
			MobileCore.log(
				LoggingMode.DEBUG,
				LOG_TAG,
				"EdgeExtension - Failed to update location hint for request event " +
				event.getUniqueIdentifier() +
				" with error " +
				e.getLocalizedMessage()
			);
		}
	}

	/**
	 * Adds an event to the event queue and starts processing the queue.
	 * @param event the received event to be added in the events queue; should not be null
	 */
	void processAddEvent(final Event event) {
		if (event == null) {
			return;
		}

		cachedEvents.add(event);
		processCachedEvents();
	}

	/**
	 * Processes the cached events in the order they were received.
	 */
	void processCachedEvents() {
		while (!cachedEvents.isEmpty()) {
			final Event event = cachedEvents.peek();

			EdgeDataEntity entity;

			if (EventUtils.isResetComplete(event)) {
				entity = new EdgeDataEntity(event);
				networkResponseHandler.setLastResetDate(event.getTimestamp()); // set last reset date
			} else { // experience event / consent update
				Map<String, Object> configReady = getConfig(event);

				if (configReady == null) {
					return; // wait for Configuration shared state
				}

				Map<String, Object> edgeConfig = EventUtils.getEdgeConfiguration(configReady);

				if (
					Utils.isNullOrEmpty((String) edgeConfig.get(EdgeConstants.SharedState.Configuration.EDGE_CONFIG_ID))
				) {
					// drop this event if configId is invalid (config id is a mandatory parameters for requests to Konductor)
					MobileCore.log(
						LoggingMode.DEBUG,
						LOG_TAG,
						String.format(
							"EdgeExtension - Missing edge.configId in Configuration, dropping event with unique id (%s)",
							event.getUniqueIdentifier()
						)
					);
					cachedEvents.poll();
					continue; // drop current, keep processing queued events
				}

				Map<String, Object> identityReady = getIdentity(event);

				if (identityReady == null) {
					return; // wait for Identity shared state
				}

				entity = new EdgeDataEntity(event, edgeConfig, identityReady);
			}

			if (hitQueue == null) {
				MobileCore.log(
					LoggingMode.WARNING,
					LOG_TAG,
					String.format(
						"EdgeExtension - hit queue is null, unable to queue event with id (%s).",
						event.getUniqueIdentifier()
					)
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
			cachedEvents.poll();
		}
	}

	/**
	 * Called by {@link EdgeExtensionListener} to retrieve an {@code ExecutorService}.
	 * The {@code ExecutorService} is used to process events on a separate thread than the
	 * {@code EventHub} thread on which they were received. Processing events on a separate
	 * thread prevents blocking of the {@code EventHub}.
	 *
	 * @return this extension's instance of a single thread executor
	 */
	ExecutorService getExecutor() {
		synchronized (executorMutex) {
			if (executorService == null) {
				executorService = Executors.newSingleThreadExecutor();
			}

			return executorService;
		}
	}

	/**
	 * Used only for testing
	 * @return an unmodifiable collection of the cached {@code Events}
	 */
	Collection<Event> getCachedEvents() {
		return Collections.unmodifiableCollection(this.cachedEvents);
	}

	/**
	 * Retrieves Configuration Shared State for the provided event.
	 *
	 * @param event current event to process
	 * @return the Configuration shared state or null if it is pending
	 */
	private Map<String, Object> getConfig(final Event event) {
		Map<String, Object> configSharedState = getApi()
			.getSharedEventState(
				EdgeConstants.SharedState.CONFIGURATION,
				event,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - Failed to retrieve Configuration shared state with error " +
							extensionError.getErrorName()
						);
					}
				}
			);

		// configuration is required for edge.configId, so if shared state is null (pending) stop processing events
		if (configSharedState == null) {
			MobileCore.log(
				LoggingMode.DEBUG,
				LOG_TAG,
				"EdgeExtension - Configuration is pending, couldn't process event at this time, waiting..."
			);
		}

		return configSharedState;
	}

	/**
	 * Retrieves Identity XDM Shared State for the provided event.
	 *
	 * @param event current event to process
	 * @return the Identity shared state or null if it is pending
	 */
	private Map<String, Object> getIdentity(final Event event) {
		Map<String, Object> identityXDMSharedState = getApi()
			.getXDMSharedEventState(
				EdgeConstants.SharedState.IDENTITY,
				event,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(
							LoggingMode.WARNING,
							LOG_TAG,
							"EdgeExtension - Failed to retrieve Identity shared state with error " +
							extensionError.getErrorName()
						);
					}
				}
			);

		if (identityXDMSharedState == null) {
			MobileCore.log(
				LoggingMode.DEBUG,
				LOG_TAG,
				"EdgeExtension - Identity shared state is pending, could not process queued events at this time, waiting..."
			);
		}

		return identityXDMSharedState;
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
			MobileCore.log(
				LoggingMode.DEBUG,
				LOG_TAG,
				String.format(
					"EdgeExtension - Ignoring event with id %s due to collect consent setting (n).",
					event.getUniqueIdentifier()
				)
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
		final Map<String, Object> consentXDMSharedState = getApi()
			.getXDMSharedEventState(EdgeConstants.SharedState.CONSENT, event, null);

		if (consentXDMSharedState == null) {
			MobileCore.log(
				LoggingMode.DEBUG,
				LOG_TAG,
				String.format(
					"EdgeExtension - Consent XDM Shared state is unavailable for event %s, using current consent.",
					event.getUniqueIdentifier()
				)
			);
			return state.getCurrentCollectConsent();
		}

		return ConsentStatus.getCollectConsentOrDefault(consentXDMSharedState);
	}

	private NamedCollection getNamedCollection() {
		if (dataStore == null) {
			dataStore =
				ServiceProvider.getInstance().getDataStoreService().getNamedCollection(EdgeConstants.EDGE_DATA_STORAGE);
		}

		return dataStore;
	}

	private NetworkResponseHandler getNetworkResponseHandler() {
		synchronized (mutex) {
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
