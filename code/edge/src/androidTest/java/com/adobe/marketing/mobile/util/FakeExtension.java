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

package com.adobe.marketing.mobile.util;

import com.adobe.marketing.mobile.AdobeCallbackWithError;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.ExtensionError;
import com.adobe.marketing.mobile.ExtensionErrorCallback;
import com.adobe.marketing.mobile.ExtensionListener;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for creating fake extensions.
 * To use, extend class, override {@code #getName()} and {@code getEventType()}.
 */
abstract class FakeExtension extends Extension {

	private static String LOG_TAG = "FakeExtension";
	private static final String EVENT_SOURCE_SET_STATE = "com.adobe.eventSource.setState";
	private static final String EVENT_SOURCE_SET_STATE_XDM = "com.adobe.eventSource.setState.xdm";
	private static final String EVENT_SOURCE_RESPONSE = "com.adobe.eventSource.response";

	protected FakeExtension(ExtensionApi extensionApi) {
		super(extensionApi);
		extensionApi.registerEventListener(
			getEventType(),
			EVENT_SOURCE_SET_STATE,
			FakeExtensionListener.class,
			new ExtensionErrorCallback<ExtensionError>() {
				@Override
				public void error(ExtensionError extensionError) {
					MobileCore.log(LoggingMode.WARNING, LOG_TAG, "Failed to register listener: " + extensionError);
				}
			}
		);

		extensionApi.registerEventListener(
			getEventType(),
			EVENT_SOURCE_SET_STATE_XDM,
			FakeExtensionListener.class,
			new ExtensionErrorCallback<ExtensionError>() {
				@Override
				public void error(ExtensionError extensionError) {
					MobileCore.log(LoggingMode.WARNING, LOG_TAG, "Failed to register listener: " + extensionError);
				}
			}
		);
	}

	/**
	 * Override in child class with unique name for Event Type.
	 * @return
	 */
	public abstract String getEventType();

	/**
	 * Set a shared state for this fake extension.
	 * @param state the state to set for this extension
	 * @param eventType the event type to send the "set shared state event", should be unique among all extensions
	 * @throws InterruptedException
	 */
	public static void setSharedState(final Map<String, Object> state, final String eventType)
		throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Event event = new Event.Builder("Set Shared State", eventType, EVENT_SOURCE_SET_STATE)
			.setEventData(state)
			.build();
		MobileCore.dispatchEventWithResponseCallback(
			event,
			5000,
			new AdobeCallbackWithError<Event>() {
				@Override
				public void call(final Event event) {
					latch.countDown();
				}

				@Override
				public void fail(final AdobeError adobeError) {
					MobileCore.log(
						LoggingMode.WARNING,
						LOG_TAG,
						"Failed to dispatch set shared state event: " + adobeError
					);
				}
			}
		);

		latch.await(5, TimeUnit.SECONDS);
	}

	/**
	 * Set the XDM shared state for this fake extension.
	 * @param state the XDM state to set for this extension
	 * @param eventType the event type to send the "set xdm shared state event", should be unique among all extensions
	 * @throws InterruptedException
	 */
	public static void setXDMSharedState(final Map<String, Object> state, final String eventType)
		throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Event event = new Event.Builder("Set XDM Shared State", eventType, EVENT_SOURCE_SET_STATE_XDM)
			.setEventData(state)
			.build();
		MobileCore.dispatchEventWithResponseCallback(
			event,
			5000,
			new AdobeCallbackWithError<Event>() {
				@Override
				public void call(final Event event) {
					latch.countDown();
				}

				@Override
				public void fail(final AdobeError adobeError) {
					MobileCore.log(
						LoggingMode.WARNING,
						LOG_TAG,
						"Failed to dispatch XDM set shared state event: " + adobeError
					);
				}
			}
		);

		latch.await(5, TimeUnit.SECONDS);
	}

	public void setSharedState(final Event event) {
		Map<String, Object> eventData = event.getEventData();
		getApi()
			.setSharedEventState(
				eventData,
				event,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(LoggingMode.WARNING, LOG_TAG, "Failed to create shared state: " + event);
					}
				}
			);

		Event responseEvent = new Event.Builder("Set Shared State Response", getEventType(), EVENT_SOURCE_RESPONSE)
			.inResponseToEvent(event)
			.build();
		getApi().dispatch(responseEvent);
	}

	public void setXDMSharedState(final Event event) {
		Map<String, Object> eventData = event.getEventData();
		getApi()
			.setXDMSharedEventState(
				eventData,
				event,
				new ExtensionErrorCallback<ExtensionError>() {
					@Override
					public void error(ExtensionError extensionError) {
						MobileCore.log(LoggingMode.WARNING, LOG_TAG, "Failed to create XDM shared state: " + event);
					}
				}
			);

		Event responseEvent = new Event.Builder("Set XDM Shared State Response", getEventType(), EVENT_SOURCE_RESPONSE)
			.inResponseToEvent(event)
			.build();
		getApi().dispatch(responseEvent);
	}

	static class FakeExtensionListener extends ExtensionListener {

		protected FakeExtensionListener(ExtensionApi extension, String type, String source) {
			super(extension, type, source);
		}

		@Override
		public void hear(Event event) {
			FakeExtension parentExtension = getParentExtension();

			if (parentExtension == null) {
				return;
			}

			if (event.getSource().equalsIgnoreCase(EVENT_SOURCE_SET_STATE)) {
				parentExtension.setSharedState(event);
			} else if (event.getSource().equalsIgnoreCase(EVENT_SOURCE_SET_STATE_XDM)) {
				parentExtension.setXDMSharedState(event);
			}
		}

		@Override
		protected FakeExtension getParentExtension() {
			return (FakeExtension) super.getParentExtension();
		}
	}
}
