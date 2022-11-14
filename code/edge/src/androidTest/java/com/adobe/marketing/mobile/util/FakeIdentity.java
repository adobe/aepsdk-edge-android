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

import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.ExtensionError;
import com.adobe.marketing.mobile.ExtensionErrorCallback;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;

/**
 * A fake Identity extension. Test cases can use this extension to act in place of the real
 * Identity extension by setting an Identity shared state in the Event Hub.
 */
public class FakeIdentity extends FakeExtension {

	// Add a static const to retrieve the event type in tests
	public static final String EVENT_TYPE = "com.adobe.eventType.fakeIdentity";

	protected FakeIdentity(ExtensionApi extensionApi) {
		super(extensionApi);
	}

	/**
	 * Override from base class. The event type should be unique among all extensions to avoid
	 * other extensions hearing the events.
	 * @return the event type for this extension
	 */
	@Override
	public String getEventType() {
		return EVENT_TYPE;
	}

	/**
	 * @return the real name of the Edge Identity extension
	 */
	@Override
	protected String getName() {
		return "com.adobe.edge.identity";
	}

	/**
	 * Register this fake Identity extension with the Event Hub.
	 */
	public static void registerExtension() {
		MobileCore.registerExtension(
			FakeIdentity.class,
			new ExtensionErrorCallback<ExtensionError>() {
				@Override
				public void error(ExtensionError extensionError) {
					MobileCore.log(
						LoggingMode.ERROR,
						"FakeIdentity",
						"There was an error registering the Fake Identity extension: " + extensionError.getErrorName()
					);
				}
			}
		);
	}
}
