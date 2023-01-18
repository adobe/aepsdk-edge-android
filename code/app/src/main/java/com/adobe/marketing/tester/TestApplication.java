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

package com.adobe.marketing.tester;

import android.app.Application;
import com.adobe.marketing.mobile.Assurance;
import com.adobe.marketing.mobile.Edge;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.edge.consent.Consent;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.Log;
import java.util.Arrays;

public class TestApplication extends Application {

	private static final String LOG_TAG = "EdgeTestApplication";
	private static final String LOG_SOURCE = "TestApplication";

	// TODO: Set up the preferred Environment File ID from your mobile property configured in Data Collection UI
	private final String ENVIRONMENT_FILE_ID = "";

	@Override
	public void onCreate() {
		super.onCreate();
		MobileCore.setApplication(this);

		MobileCore.setLogLevel(LoggingMode.VERBOSE);
		MobileCore.configureWithAppID(ENVIRONMENT_FILE_ID);

		// Register Adobe extensions
		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, Identity.EXTENSION, Consent.EXTENSION, Assurance.EXTENSION),
			o -> Log.debug(LOG_TAG, LOG_SOURCE, "Mobile SDK was initialized")
		);
	}
}
