/*
  Copyright 2023 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.integration

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.integration.util.EdgeLocationHint
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.util.RealNetworkService
import com.adobe.marketing.mobile.util.TestHelper
import com.adobe.marketing.mobile.util.TestHelper.LogOnErrorRule
import com.adobe.marketing.mobile.util.TestHelper.RegisterMonitorExtensionRule
import com.adobe.marketing.mobile.util.TestHelper.SetupCoreRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch

/**
 * Performs validation on integration with the Edge Network upstream service.
 */
@RunWith(AndroidJUnit4::class)
class UpstreamIntegrationTests {
    private val realNetworkService = RealNetworkService()
    private val edgeLocationHint: String = BuildConfig.EDGE_LOCATION_HINT
    private val edgeEnvironment: String = BuildConfig.EDGE_ENVIRONMENT

    @JvmField
    @Rule
    var rule: RuleChain = RuleChain
        .outerRule(LogOnErrorRule())
        .around(SetupCoreRule())
        .around(RegisterMonitorExtensionRule())

    @Before
    @Throws(Exception::class)
    fun setup() {
        println("Environment var - Edge Network environment: $edgeEnvironment")
        println("Environment var - Edge Network location hint: $edgeLocationHint")
        realNetworkService.reset()
        ServiceProvider.getInstance().networkService = realNetworkService

        // Set environment file ID for specific Edge Network environment
        setMobileCoreEnvironmentFileID(edgeEnvironment)
        val latch = CountDownLatch(1)
        MobileCore.registerExtensions(listOf(Edge.EXTENSION, Identity.EXTENSION)) {
            latch.countDown()
        }
        latch.await()

        // Set Edge location hint if one is set for the test suite
        if (edgeLocationHint.isNotEmpty()) {
            print("Setting Edge location hint to: $edgeLocationHint")
            Edge.setLocationHint(edgeLocationHint)
        }
        else {
            print("No preset Edge location hint is being used for this test.")
        }

        realNetworkService.reset()
        TestHelper.resetTestExpectations(realNetworkService)
    }

    @After
    fun tearDown() {
        realNetworkService.reset()
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.adobe.marketing.mobile.integration", appContext.packageName)
    }

    private fun setMobileCoreEnvironmentFileID(edgeEnvironment: String) {
        when (edgeEnvironment) {
            "prod" -> {
                MobileCore.configureWithAppID("94f571f308d5/6b1be84da76a/launch-023a1b64f561-development")
            }
            "preProd" -> {
                MobileCore.configureWithAppID("94f571f308d5/6b1be84da76a/launch-023a1b64f561-development")
            }
            "int" -> {
                // TODO: create integration environment environment file ID
                MobileCore.configureWithAppID("94f571f308d5/6b1be84da76a/launch-023a1b64f561-development")
            }
            else -> {
                // Catchall for any other values
                fail("Unsupported edgeEnvironment value: $edgeEnvironment")
            }
        }
    }
}