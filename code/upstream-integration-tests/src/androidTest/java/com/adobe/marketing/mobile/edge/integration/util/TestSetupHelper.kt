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

package com.adobe.marketing.mobile.edge.integration.util

import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.edge.integration.BuildConfig
import com.adobe.marketing.mobile.util.TestConstants
import com.adobe.marketing.mobile.util.TestHelper

object TestSetupHelper {

    val defaultLocationHint: String?
        get() {
            return when (val locationHint = BuildConfig.EDGE_LOCATION_HINT) {
                IntegrationTestConstants.LocationHintMapping.EMPTY_STRING -> ""
                IntegrationTestConstants.LocationHintMapping.INVALID -> locationHint
                IntegrationTestConstants.LocationHintMapping.NONE -> null
                else -> locationHint
            }
        }

    val defaultMobilePropertyId: String
        get() {
            val mobilePropertyId = BuildConfig.MOBILE_PROPERTY_ID
            return mobilePropertyId.takeIf { it.isNotEmpty() } ?: IntegrationTestConstants.MobilePropertyId.PROD
        }

    fun setInitialLocationHint(locationHint: String?) {
        // Location hint is non-null and non-empty
        if (!locationHint.isNullOrEmpty()) {
            println("Setting Edge location hint to: $locationHint")
            Edge.setLocationHint(locationHint)
            return
        }
        println("No preset Edge location hint is being used for this test.")
    }

    /**
     * Returns the environment file ID for the provided edgeEnvironment value.
     *
     * @param edgeEnvironment The edgeEnvironment value to use.
     * @return The environment file ID for the provided edgeEnvironment value.
     */
    fun getEnvironmentFileID(edgeEnvironment: String): String {
        when (edgeEnvironment) {
            "prod" -> {
                return "94f571f308d5/6b1be84da76a/launch-023a1b64f561-development"
            }

            "pre-prod" -> {
                return "94f571f308d5/6b1be84da76a/launch-023a1b64f561-development"
            }

            "int" -> {
                // TODO: create integration environment environment file ID
                return "94f571f308d5/6b1be84da76a/launch-023a1b64f561-development"
            }

            else -> {
                // Catchall for any other values
                println("Unsupported edgeEnvironment value: $edgeEnvironment. Using prod as default.")
                return "94f571f308d5/6b1be84da76a/launch-023a1b64f561-development"
            }
        }
    }

    /**
     * Creates a valid interact URL using the provided location hint.
     *
     * @param locationHint The location hint String to use in the URL.
     * @return The interact URL with location hint applied.
     */
    fun createInteractURL(locationHint: String?): String {
        return if (locationHint.isNullOrEmpty()) {
            "https://obumobile5.data.adobedc.net/ee/v1/interact"
        } else {
            "https://obumobile5.data.adobedc.net/ee/$locationHint/v1/interact"
        }
    }

    /**
     * Retrieves Edge response errors.
     *
     * @return A list of events that represent Edge response errors.
     */
    fun getEdgeResponseErrors(): List<Event> {
        return TestHelper.getDispatchedEventsWith(
            TestConstants.EventType.EDGE,
            TestConstants.EventSource.ERROR_RESPONSE_CONTENT
        )
    }

    /**
     * Sets the expectation for an Edge event handle.
     *
     * @param expectedHandleType The expected handle type for the event.
     * @param expectedCount The expected number of occurrences for the event. Default value is 1.
     */
    fun expectEdgeEventHandle(expectedHandleType: String, expectedCount: Int = 1) {
        TestHelper.setExpectationEvent(
            TestConstants.EventType.EDGE,
            expectedHandleType,
            expectedCount
        )
    }

    /**
     * Retrieves Edge event handles for a specified handle type.
     *
     * @param expectedHandleType The handle type to filter events.
     * @return A list of events matching the given handle type.
     */
    fun getEdgeEventHandles(expectedHandleType: String): List<Event> {
        return TestHelper.getDispatchedEventsWith(TestConstants.EventType.EDGE, expectedHandleType)
    }

    /**
     * Extracts the Edge location hint from the location hint result.
     *
     * @return The last location hint result value, if available. Otherwise, returns `null`.
     */
    fun getLastLocationHintResultValue(): String? {
        val locationHintResultEvent = getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT).lastOrNull()
        val payload = locationHintResultEvent?.eventData?.get("payload") as? List<Map<String, Any>> ?: return null
        if (payload.indices.contains(2)) {
            return payload[2]["hint"] as? String
        }
        return null
    }
}
