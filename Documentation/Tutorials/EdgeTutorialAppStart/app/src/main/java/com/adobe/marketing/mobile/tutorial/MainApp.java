/*
 Copyright 2022 Adobe. All rights reserved.

 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
*/
package com.adobe.marketing.mobile.tutorial;

import android.app.Application;
import android.util.Log;

// Imports the various Edge extensions and other AEP extensions that enable sending event
// data to the Edge Network, and power other features. The `import` statement makes it available
// to use in the code below.
/* Edge Tutorial - code section (1/2)
import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.Assurance;
import com.adobe.marketing.mobile.Edge;
import com.adobe.marketing.mobile.InvalidInitException;
import com.adobe.marketing.mobile.Lifecycle;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.edge.consent.Consent;
import com.adobe.marketing.mobile.edge.identity.Identity;
// Edge Tutorial - code section (1/2) */

public class MainApp extends Application {
    private static final String LOG_TAG = "Test Application";

    // TODO: Set the Environment File ID from your mobile property configured in Data Collection UI
    private final String ENVIRONMENT_FILE_ID = "";

    @Override
    public void onCreate() {
        super.onCreate();
        /* Edge Tutorial - code section (2/2)
        // Passes the app reference to Core, which allows it to access the the app `Context` and monitor the lifecycle
        // of the Android application. See the [API reference](https://aep-sdks.gitbook.io/docs/foundation-extensions/mobile-core/mobile-core-api-reference#setapplication-android-only).
        MobileCore.setApplication(this);
        // Sets the log level of Core (which handles the core functionality used by extensions like networking,
        // data conversions, etc.) to `verbose`, which provides more granular details on app logic; this can be
        // helpful in debugging or troubleshooting issues.
        MobileCore.setLogLevel(LoggingMode.VERBOSE);
        // This sets the environment file ID which is the mobile property configuration set up in the first section;
        // this will apply the extension settings in our app.
        MobileCore.configureWithAppID(ENVIRONMENT_FILE_ID);

        // Registers the extensions with Core, getting them ready to run in the app.
        try {
            // Register AEP extensions
            Assurance.registerExtension();
            Consent.registerExtension();
            Edge.registerExtension();
            Identity.registerExtension();
            Lifecycle.registerExtension();

            // Once all the extensions are registered, call MobileCore.start(...) to start processing the events
            MobileCore.start(new AdobeCallback() {

                @Override
                public void call(Object o) {
                    Log.d(LOG_TAG, "AEP Mobile SDK is initialized");

                }
            });
        } catch (InvalidInitException e) {
            e.printStackTrace();
        }
        // Edge Tutorial - code section (2/2) */
    }
}
