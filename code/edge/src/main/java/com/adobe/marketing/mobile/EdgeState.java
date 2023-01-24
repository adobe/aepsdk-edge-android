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

import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.MapUtils;
import java.util.Map;

/**
 * Updates the state of the  {@link EdgeExtension} based on the Collect consent status.
 */
class EdgeState {

	private final String LOG_SOURCE = "EdgeState";
	private final Object mutex = new Object();
	private ConsentStatus currentCollectConsent;
	private final HitQueuing hitQueue;
	private boolean hasBooted;
	private Map<String, Object> implementationDetails;
	private final EdgeSharedStateCallback sharedStateCallback;
	private final EdgeProperties edgeProperties;

	/**
	 * Constructor.
	 *  @param hitQueue instance of type {@link HitQueuing}
	 * @param edgeProperties instance of type {@link EdgeProperties}
	 * @param sharedStateCallback callback for setting shared states
	 */
	EdgeState(final HitQueuing hitQueue, EdgeProperties edgeProperties, EdgeSharedStateCallback sharedStateCallback) {
		currentCollectConsent = EdgeConstants.Defaults.COLLECT_CONSENT_PENDING;
		this.edgeProperties = edgeProperties;
		this.sharedStateCallback = sharedStateCallback;
		this.hitQueue = hitQueue;
		handleCollectConsentChange(currentCollectConsent);
	}

	/**
	 * Completes init for the {@link EdgeExtension}.
	 * The collect consent is set by checking if the {@code Consent} extension is registered with the Event Hub,
	 * otherwise uses the default value {@link EdgeConstants.Defaults#COLLECT_CONSENT_YES} as {@code currentCollectConsent} if this extension is not registered.
	 * <p>
	 * Builds the Implementation Details map from the Event Hub shared state.
	 * <p>
	 * After {@code EdgeState} has initialized, further calls to this method are a no-op.
	 * <p>
	 * Loads any persisted Edge properties and creates an initial shared state.
	 *
	 * @return true if bootup is complete
	 */
	boolean bootupIfNeeded() {
		if (hasBooted) {
			return true;
		}

		// Get Hub's shared state needed to build Implementation Details from the Core version and
		// wrapper type, and set the default consent if the Consent extension is not registered.
		// If not set, return false and attempt bootup at next event
		final SharedStateResult eventHubStateResult = sharedStateCallback.getSharedState(
			EdgeConstants.SharedState.HUB,
			null
		);
		if (eventHubStateResult == null || eventHubStateResult.getStatus() != SharedStateStatus.SET) {
			return false;
		}

		synchronized (mutex) {
			// load data from local storage
			edgeProperties.loadFromPersistence();

			// Parse shared state and build XDM Implementation Details
			// Note, Implementation Details must be set before processing Consent and starting the Hit Queue
			// so it is available in the EdgeHitProcessor
			implementationDetails = ImplementationDetails.fromEventHubState(eventHubStateResult.getValue());

			// If Consent is not registered, update Collect Consent to Yes
			updateCollectConsentIfNotRegistered(eventHubStateResult.getValue());

			// Important - Using null Event here which creates a shared state at the next available Event number.
			//             An extension should NOT mix creating shared states using null and using received events
			//             as it can cause shared state generation to fail due to received events having potentially
			//             lower event numbers than states using null.
			sharedStateCallback.createSharedState(edgeProperties.toEventData(), null);
		}

		hasBooted = true;
		Log.debug(LOG_TAG, LOG_SOURCE, "Edge has successfully booted up");

		return hasBooted;
	}

	/**
	 * @return current consent preferences known by the {@link EdgeExtension}
	 */
	ConsentStatus getCurrentCollectConsent() {
		synchronized (mutex) {
			return currentCollectConsent;
		}
	}

	/**
	 * Updates {@code currentCollectConsent} value and updates the hitQueue state based on it.
	 *
	 * @param status the new collect consent status
	 */
	void updateCurrentConsent(final ConsentStatus status) {
		synchronized (mutex) {
			currentCollectConsent = status;
			handleCollectConsentChange(status);
		}
	}

	/**
	 * Returns a Map formatted to the XDM Implementation Details data type.
	 * Method call is thread-safe.
	 * @return the implementation details for the SDK, or null if called before this extension
	 * is not booted.
	 */
	Map<String, Object> getImplementationDetails() {
		synchronized (mutex) {
			return implementationDetails;
		}
	}

	/**
	 * Retrieves the Edge Network location hint. Returns null if location hint expired or is not set.
	 * @return the Edge Network location hint or null if location hint expired or is not set.
	 */
	String getLocationHint() {
		synchronized (mutex) {
			return edgeProperties.getLocationHint();
		}
	}

	/**
	 * Update the Edge Network location hint and persist the new hint to the data store. If
	 * {@code hint} is null, then the stored location hint and expiry date is removed from memory
	 * and persistent storage.
	 * If the updated location hint is different from the previous, then a shared state is also
	 * created with the new location hint.
	 * @param hint the Edge Network location hint to set
	 * @param ttlSeconds the time-to-live in seconds for the given location hint
	 */
	void setLocationHint(final String hint, final int ttlSeconds) {
		synchronized (mutex) {
			if (edgeProperties.setLocationHint(hint, ttlSeconds)) {
				// Create shared state if location hint changed
				// Important - Using null Event here which creates a shared state at the next available Event number.
				//             An extension should NOT mix creating shared states using null and using received events
				//             as it can cause shared state generation to fail due to received events having potentially
				//             lower event numbers than states using null. If this extension later needs to create shared
				//             states from received events, then this code must be refactored to also use received
				//             events as the state version.
				sharedStateCallback.createSharedState(edgeProperties.toEventData(), null);
			}
		}
	}

	/**
	 * Parse the Event Hub shared state for Consent extension. If not listed, update the current
	 * Consent to YES.
	 * @param registeredExtensionsWithHub the Event Hub shared state
	 */
	private void updateCollectConsentIfNotRegistered(final Map<String, Object> registeredExtensionsWithHub) {
		// check if consent extension is registered
		Map<String, Object> consentExtensionInfo = null;

		if (registeredExtensionsWithHub != null) {
			final Map<String, Object> extensions = DataReader.optTypedMap(
				Object.class,
				registeredExtensionsWithHub,
				EdgeConstants.SharedState.Hub.EXTENSIONS,
				null
			);

			if (extensions != null) {
				consentExtensionInfo =
					DataReader.optTypedMap(Object.class, extensions, EdgeConstants.SharedState.CONSENT, null);
			}
		}

		if (MapUtils.isNullOrEmpty(consentExtensionInfo)) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Consent extension is not registered yet, using default collect status (yes)"
			);
			updateCurrentConsent(EdgeConstants.Defaults.COLLECT_CONSENT_YES);
		}
		// otherwise keep consent pending until the consent preferences update event is received
	}

	/**
	 * Based on {@code status} determines if it should continue processing hits or if it should suspend processing and clear hits
	 *
	 * @param status the current collect consent status
	 */
	private void handleCollectConsentChange(final ConsentStatus status) {
		if (hitQueue == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to update hit queue with consent status. HitQueuing instance is null."
			);
			return;
		}

		switch (status) {
			case YES:
				hitQueue.beginProcessing();
				Log.debug(LOG_TAG, LOG_SOURCE, "Collect consent set to (y), resuming the Edge queue.");
				break;
			case NO:
				hitQueue.clear();
				hitQueue.beginProcessing();
				Log.debug(LOG_TAG, LOG_SOURCE, "Collect consent set to (n), clearing the Edge queue.");
				break;
			case PENDING:
				hitQueue.suspend();
				Log.debug(LOG_TAG, LOG_SOURCE, "Collect consent is pending, suspending the Edge queue until (y/n).");
				break;
		}
	}
}
