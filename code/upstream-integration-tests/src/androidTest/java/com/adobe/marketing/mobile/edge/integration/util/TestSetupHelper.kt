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
import org.junit.Assert.fail

object TestSetupHelper {
    /**
     * Retrieves the Edge location hint from the shell environment.
     *
     * @return The Edge location hint if set in the environment, or `""` if not set.
     */
    val defaultLocationHint: String?
        get() {
            return when (val locationHint = BuildConfig.EDGE_LOCATION_HINT) {
                IntegrationTestConstants.LocationHintMapping.NONE -> null
                IntegrationTestConstants.LocationHintMapping.EMPTY_STRING -> ""
                else -> locationHint
            }
        }

    /**
     * Retrieves the tags mobile property ID from the shell environment.
     *
     * @return The tags mobile property ID if set in the environment, or a default value if not set.
     */
    val defaultTagsMobilePropertyId: String
        get() {
            val tagsMobilePropertyId = BuildConfig.TAGS_MOBILE_PROPERTY_ID
            return tagsMobilePropertyId.takeIf { it.isNotEmpty() } ?: IntegrationTestConstants.MobilePropertyId.PROD
        }

    /**
     * Sets the initial Edge location hint for the test suite if a valid, non-null, and non-empty location hint is provided.
     *
     * @param locationHint An optional string representing the location hint to be set. Must be non-null and non-empty to be applied.
     */
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
     * Creates a valid interact URL using the provided location hint.
     *
     * @param locationHint The location hint String to use in the URL.
     * @return The interact URL with location hint applied.
     */
    fun createInteractURL(locationHint: String?): String {
        // Timeout is in milliseconds
        val sharedState = TestHelper.getSharedStateFor(IntegrationTestConstants.ExtensionName.CONFIGURATION, 10_000)
        val edgeDomain = sharedState?.get(IntegrationTestConstants.ConfigurationKey.EDGE_DOMAIN) as? String
        if (edgeDomain.isNullOrEmpty()) {
            fail("Edge domain could not be fetched from the configuration shared state.")
        }
        return if (locationHint.isNullOrEmpty()) {
            "https://$edgeDomain/ee/v1/interact"
        } else {
            "https://$edgeDomain/ee/$locationHint/v1/interact"
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
