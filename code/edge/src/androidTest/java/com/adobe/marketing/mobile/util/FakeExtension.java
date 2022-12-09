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

import static com.adobe.marketing.mobile.util.FunctionalTestConstants.LOG_TAG;

import com.adobe.marketing.mobile.AdobeCallbackWithError;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.Extension;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.services.Log;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Abstract class for creating fake extensions.
 * To use, extend class, override {@code #getName()} and {@code getEventType()}.
 */
abstract class FakeExtension extends Extension {

	private static String LOG_SOURCE = "FakeExtension";
	private static final String EVENT_SOURCE_SET_STATE = "com.adobe.eventSource.setState";
	private static final String EVENT_SOURCE_SET_STATE_XDM = "com.adobe.eventSource.setState.xdm";
	private static final String EVENT_SOURCE_RESPONSE = "com.adobe.eventSource.response";

	protected FakeExtension(ExtensionApi extensionApi) {
		super(extensionApi);
	}

	@Override
	protected void onRegistered() {
		super.onRegistered();

		getApi().registerEventListener(getEventType(), EVENT_SOURCE_SET_STATE, this::setSharedState);
		getApi().registerEventListener(getEventType(), EVENT_SOURCE_SET_STATE_XDM, this::setXDMSharedState);
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
					Log.warning(
						LOG_TAG,
						LOG_SOURCE,
						"Failed to dispatch set shared state event: %s",
						adobeError.getErrorName()
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
					Log.warning(
						LOG_TAG,
						LOG_SOURCE,
						"Failed to dispatch XDM set shared state event: %s",
						adobeError.getErrorName()
					);
				}
			}
		);

		latch.await(5, TimeUnit.SECONDS);
	}

	public void setSharedState(final Event event) {
		Map<String, Object> eventData = event.getEventData();
		getApi().createSharedState(eventData, event);

		Event responseEvent = new Event.Builder("Set Shared State Response", getEventType(), EVENT_SOURCE_RESPONSE)
			.inResponseToEvent(event)
			.build();
		getApi().dispatch(responseEvent);
	}

	public void setXDMSharedState(final Event event) {
		Map<String, Object> eventData = event.getEventData();
		getApi().createXDMSharedState(eventData, event);

		Event responseEvent = new Event.Builder("Set XDM Shared State Response", getEventType(), EVENT_SOURCE_RESPONSE)
			.inResponseToEvent(event)
			.build();
		getApi().dispatch(responseEvent);
	}
}
