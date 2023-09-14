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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.ExperienceEvent
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.integration.util.EdgeLocationHint
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import com.adobe.marketing.mobile.util.JSONAsserts.assertExactMatch
import com.adobe.marketing.mobile.util.JSONAsserts.assertTypeMatch
import com.adobe.marketing.mobile.util.RealNetworkService
import com.adobe.marketing.mobile.util.TestConstants
import com.adobe.marketing.mobile.util.TestHelper
import com.adobe.marketing.mobile.util.TestHelper.LogOnErrorRule
import com.adobe.marketing.mobile.util.TestHelper.RegisterMonitorExtensionRule
import com.adobe.marketing.mobile.util.TestHelper.SetupCoreRule
import com.adobe.marketing.mobile.util.TestHelper.assertExpectedEvents
import com.adobe.marketing.mobile.util.TestHelper.getDispatchedEventsWith
import com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        TestHelper.resetTestExpectations(null)
    }

    @After
    fun tearDown() {
        realNetworkService.reset()
    }

    /**
     * Tests that a standard sendEvent receives a single network response with HTTP code 200.
     */
    @Test
    fun testSendEvent_receivesExpectedNetworkResponse() {
        val interactNetworkRequest = TestableNetworkRequest(createURLWith(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponse = realNetworkService.getResponseFor(interactNetworkRequest)

        assertNotNull(matchingResponse)
        assertEquals(200, matchingResponse?.responseCode)
    }

    /**
     * Tests that a standard sendEvent receives a single network response with HTTP code 200.
     */
    @Test
    fun testSendEvent_whenComplexEvent_receivesExpectedNetworkResponse() {
        // Setup
        val interactNetworkRequest = TestableNetworkRequest(createURLWith(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val xdm = mapOf(
            "testString" to "xdm"
        )

        val data = mapOf(
            "testDataString" to "stringValue",
            "testDataInt" to 101,
            "testDataBool" to true,
            "testDataDouble" to 13.66,
            "testDataArray" to listOf("arrayElem1", 2, true),
            "testDataDictionary" to mapOf("key" to "val")
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(xdm)
            .setData(data)
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        // Network response assertions
        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponse = realNetworkService.getResponseFor(interactNetworkRequest)

        assertNotNull(matchingResponse)
        assertEquals(200, matchingResponse?.responseCode)
    }

    /**
     * Tests that a standard sendEvent() receives a single network response with HTTP code 200
     */
    @Test
    fun testSendEvent_whenComplexXDMEvent_receivesExpectedNetworkResponse() {
        // Setup
        val interactNetworkRequest = TestableNetworkRequest(createURLWith(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val xdm: Map<String, Any> = mapOf(
            "testString" to "xdm",
            "testInt" to 10,
            "testBool" to false,
            "testDouble" to 12.89,
            "testArray" to listOf("arrayElem1", 2, true),
            "testDictionary" to mapOf("key" to "val")
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(xdm)
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        // Network response assertions
        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponseFor(interactNetworkRequest)

        assertNotNull(matchingResponses)
        assertEquals(200, matchingResponses?.responseCode)
    }

    /**
     * Tests that a standard sendEvent receives the expected event handles.
     */
    @Test
    fun testSendEvent_receivesExpectedEventHandles() {
        // Setup
        expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT, expectedCount = 1)
        expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.STATE_STORE, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        assertExpectedEvents(true)
    }

    /**
     * Tests that a standard sendEvent receives the expected event handles and does not receive an error event.
     */
    @Test
    fun testSendEvent_doesNotReceivesErrorEvent() {
        // Setup
        expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT, expectedCount = 1)
        expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.STATE_STORE, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        assertExpectedEvents(true)

        val errorEvents = getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.ERROR_RESPONSE_CONTENT)
        assertEquals(0, errorEvents.size)
    }

    /**
     * Tests that a standard sendEvent with no prior location hint value set receives the expected location hint event handle.
     * That is, checks for a string type location hint.
     */
    @Test
    fun testSendEvent_with_NO_priorLocationHint_receivesExpectedLocationHintEventHandle() {
        // Setup
        // Clear any existing location hint
        Edge.setLocationHint(null)

        expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        val expectedLocationHintJSON = """
        {
          "payload": [
            {
              "ttlSeconds" : 123,
              "scope" : "EdgeNetwork",
              "hint" : "stringType"
            }
          ]
        }
         """.trimIndent()

        // Unsafe access used since testSendEvent_receivesExpectedEventHandles guarantees existence
        val locationHintResult = getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT).first()

        assertTypeMatch(
            expected = JSONObject(expectedLocationHintJSON),
            actual = JSONObject(locationHintResult.eventData),
            exactMatchPaths = listOf("payload[*].scope")
        )
    }

    /**
     * Tests that a standard sendEvent WITH prior location hint value set receives the expected location hint event handle.
     * That is, checks for consistency between prior location hint value and received location hint result.
     */
    @Test
    fun testSendEvent_withPriorLocationHint_receivesExpectedLocationHintEventHandle() {
        // Uses all the valid location hint cases in random order to prevent order dependent edge cases slipping through
        EdgeLocationHint.values().map { it.rawValue }.shuffled().forEach { locationHint ->
            // Setup
            Edge.setLocationHint(locationHint)

            expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT, expectedCount = 1)

            val experienceEvent = ExperienceEvent.Builder()
                .setXdmSchema(mapOf("xdmtest" to "data"))
                .setData(mapOf("data" to mapOf("test" to "data")))
                .build()

            // Test
            Edge.sendEvent(experienceEvent) {}

            // Verify
            val expectedLocationHintJSON = """
            {
              "payload": [
                {
                  "ttlSeconds" : 123,
                  "scope" : "EdgeNetwork",
                  "hint" : "$locationHint"
                }
              ]
            }
            """.trimIndent()

            // Unsafe access used since testSendEvent_receivesExpectedEventHandles guarantees existence
            val locationHintResult = getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT).first()
            assertTypeMatch(
                expected = JSONObject(expectedLocationHintJSON),
                actual = JSONObject(locationHintResult.eventData),
                exactMatchPaths = listOf("payload[*].scope", "payload[*].hint")
            )

            realNetworkService.reset()
            TestHelper.resetTestExpectations(null)
        }
    }

    /**
     * Tests that a standard sendEvent with no prior state receives the expected state store event handle.
     */
    @Test
    fun testSendEvent_with_NO_priorState_receivesExpectedStateStoreEventHandle() {
        // Setup
        expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.STATE_STORE, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        val expectedStateStoreJSON = """
        {
          "payload": [
            {
              "maxAge": 123,
              "key": "kndctr_972C898555E9F7BC7F000101_AdobeOrg_cluster",
              "value": "stringType"
            },
            {
              "maxAge": 123,
              "key": "kndctr_972C898555E9F7BC7F000101_AdobeOrg_identity",
              "value": "stringType"
            }
          ]
        }
        """.trimIndent()

        // Unsafe access used since testSendEvent_receivesExpectedEventHandles guarantees existence
        val stateStoreEvent = getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.STATE_STORE).last()

        // Exact match used here to strictly validate `payload` array element count == 2
        assertExactMatch(
            expected = JSONObject(expectedStateStoreJSON),
            actual = JSONObject(stateStoreEvent.eventData),
            typeMatchPaths = listOf("payload[0].maxAge", "payload[0].value", "payload[1].maxAge", "payload[1].value")
        )
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
                println("Unsupported edgeEnvironment value: $edgeEnvironment. Using prod as default.")
                MobileCore.configureWithAppID("94f571f308d5/6b1be84da76a/launch-023a1b64f561-development")
            }
        }
    }

    /**
     * Creates a valid interact URL using the provided location hint. If location hint is invalid, returns default URL with no location hint.
     *
     * @param locationHint The `EdgeLocationHint`'s raw value to use in the URL.
     * @return The interact URL with location hint applied, default URL if location hint is invalid.
     */
    private fun createURLWith(locationHint: EdgeLocationHint?): String {
        locationHint?.let {
            return createURLWith(locationHint = it.rawValue)
        }
        return "https://obumobile5.data.adobedc.net/ee/v1/interact"
    }

    /**
     * Creates a valid interact URL using the provided location hint.
     *
     * @param locationHint The location hint String to use in the URL.
     * @return The interact URL with location hint applied.
     */
    private fun createURLWith(locationHint: String?): String {
        return if (locationHint.isNullOrEmpty()) {
            "https://obumobile5.data.adobedc.net/ee/v1/interact"
        } else {
            "https://obumobile5.data.adobedc.net/ee/$locationHint/v1/interact"
        }
    }

    /**
     * Sets the expectation for an Edge event handle.
     *
     * @param expectedHandleType The expected handle type for the event.
     * @param expectedCount The expected number of occurrences for the event. Default value is 1.
     */
    private fun expectEdgeEventHandle(expectedHandleType: String, expectedCount: Int = 1) {
        setExpectationEvent(TestConstants.EventType.EDGE, expectedHandleType, expectedCount)
    }

    /**
     * Retrieves Edge event handles for a specified handle type.
     *
     * @param expectedHandleType The handle type to filter events.
     * @return A list of events matching the given handle type.
     */
    private fun getEdgeEventHandles(expectedHandleType: String): List<Event> {
        return getDispatchedEventsWith(TestConstants.EventType.EDGE, expectedHandleType)
    }

    /**
     * Retrieves Edge response errors.
     *
     * @return A list of events that represent Edge response errors.
     */
    private fun getEdgeResponseErrors(): List<Event> {
        return getDispatchedEventsWith(TestConstants.EventType.EDGE, TestConstants.EventSource.ERROR_RESPONSE_CONTENT)
    }

    /**
     * Extracts the Edge location hint from the location hint result.
     *
     * @return The last location hint result value, if available. Otherwise, returns `null`.
     */
    private fun getLastLocationHintResultValue(): String? {
        val locationHintResultEvent = getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT).lastOrNull()
        val payload = locationHintResultEvent?.eventData?.get("payload") as? List<Map<String, Any>> ?: return null
        if (payload.indices.contains(2)) {
            return payload[2]["hint"] as? String
        }
        return null
    }

}