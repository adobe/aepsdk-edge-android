package com.adobe.marketing.mobile.edge.testapp.kotlin

import android.app.Application
import com.adobe.marketing.mobile.Assurance
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.LoggingMode
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.consent.Consent
import com.adobe.marketing.mobile.edge.identity.Identity

class TestApplication : Application() {
    // TODO: Set up the preferred Environment File ID from your mobile property configured in Data Collection UI
    private var ENVIRONMENT_FILE_ID: String = ""

    override fun onCreate() {
        super.onCreate()

        MobileCore.setLogLevel(LoggingMode.VERBOSE)
        MobileCore.setApplication(this)

        MobileCore.registerExtensions(
            listOf(
                Consent.EXTENSION,
                Identity.EXTENSION,
                Edge.EXTENSION,
                Assurance.EXTENSION
            )
        ) {
            MobileCore.configureWithAppID(ENVIRONMENT_FILE_ID)
        }
    }
}