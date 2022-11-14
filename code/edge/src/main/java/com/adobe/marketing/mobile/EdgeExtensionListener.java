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

/**
 * An instance of {@link ExtensionListener}, the {@code EdgeExtensionListener} is registered
 * by its "parent" {@code EdgeExtension} to hear specific events which are dispatched by
 * the Mobile SDK Event Hub. Multiple {@link ExtensionListener} classes may be used to organize
 * the handling of different events, or, as shown in this example, a single class may be used
 * by checking the {@link com.adobe.marketing.mobile.EventType} and {@link com.adobe.marketing.mobile.EventSource}
 * of the heard event.
 * <p>
 * When handling {@link Event}s received by the Mobile SDK Event Hub, it is important to process
 * them as quickly as possible. In this example, an {@code ExecutorService} is used to process
 * the events on a separate thread, making the execution time of the {@code hear} method
 * relatively quick.
 */
class EdgeExtensionListener extends ExtensionListener {

	protected EdgeExtensionListener(final ExtensionApi extension, final String type, final String source) {
		super(extension, type, source);
	}

	/**
	 * Called by SDK {@code EventHub} when an event is received of the same type and source
	 * as this listener is registered.
	 *
	 * @param event the {@link Event} received by the {@code EventHub}
	 */
	@Override
	public void hear(final Event event) {
		if (EventUtils.isNullEventOrEmptyData(event) && !EventUtils.isResetComplete(event)) {
			MobileCore.log(
				LoggingMode.VERBOSE,
				EdgeConstants.LOG_TAG,
				"EdgeExtensionListener - Event / event data was null, ignoring this event."
			);
			return;
		}

		final EdgeExtension parentExtension = getParentExtension();

		if (parentExtension == null) {
			MobileCore.log(
				LoggingMode.WARNING,
				EdgeConstants.LOG_TAG,
				"EdgeExtensionListener - Unable to process event, parent extension instance is null."
			);
			return;
		}

		// handle SharedState events
		if (EdgeConstants.EventType.ADOBE_HUB.equalsIgnoreCase(event.getType())) {
			parentExtension
				.getExecutor()
				.execute(
					new Runnable() {
						@Override
						public void run() {
							parentExtension.handleSharedStateUpdate(event);
						}
					}
				);
		} else if (EventUtils.isExperienceEvent(event)) {
			// handle Edge extension events
			parentExtension
				.getExecutor()
				.execute(
					new Runnable() {
						@Override
						public void run() {
							parentExtension.handleExperienceEventRequest(event);
						}
					}
				);
		} else if (EventUtils.isConsentPreferencesUpdatedEvent(event)) {
			// handle Consent updated
			parentExtension
				.getExecutor()
				.execute(
					new Runnable() {
						@Override
						public void run() {
							parentExtension.handleConsentPreferencesUpdate(event);
						}
					}
				);
		} else if (EventUtils.isUpdateConsentEvent(event)) {
			// handle consent update request
			parentExtension
				.getExecutor()
				.execute(
					new Runnable() {
						@Override
						public void run() {
							parentExtension.handleConsentUpdate(event);
						}
					}
				);
		} else if (EventUtils.isResetComplete(event)) {
			// handle reset complete event
			parentExtension
				.getExecutor()
				.execute(
					new Runnable() {
						@Override
						public void run() {
							parentExtension.handleResetComplete(event);
						}
					}
				);
		} else if (EventUtils.isGetLocationHintEvent(event)) {
			// handle get location hint
			parentExtension
				.getExecutor()
				.execute(
					new Runnable() {
						@Override
						public void run() {
							parentExtension.handleGetLocationHint(event);
						}
					}
				);
		} else if (EventUtils.isUpdateLocationHintEvent(event)) {
			// handle update location hint
			parentExtension
				.getExecutor()
				.execute(
					new Runnable() {
						@Override
						public void run() {
							parentExtension.handleSetLocationHint(event);
						}
					}
				);
		}
	}

	/**
	 * Returns the parent extension that owns this listener.
	 *
	 * @return the extension which registered this listener
	 */
	@Override
	protected EdgeExtension getParentExtension() {
		return (EdgeExtension) super.getParentExtension();
	}
}
