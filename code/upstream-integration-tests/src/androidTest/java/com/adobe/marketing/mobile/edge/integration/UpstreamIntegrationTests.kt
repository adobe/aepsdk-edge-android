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

package com.adobe.marketing.mobile.edge.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.ExperienceEvent
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.edge.integration.util.EdgeLocationHint
import com.adobe.marketing.mobile.edge.integration.util.TestSetupHelper
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import com.adobe.marketing.mobile.util.JSONAsserts
import com.adobe.marketing.mobile.util.MonitorExtension
import com.adobe.marketing.mobile.util.RealNetworkService
import com.adobe.marketing.mobile.util.TestConstants
import com.adobe.marketing.mobile.util.TestHelper
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
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
    var rule: RuleChain = RuleChain.outerRule(TestHelper.LogOnErrorRule())
        .around(TestHelper.SetupCoreRule())

    @Before
    @Throws(Exception::class)
    fun setup() {
        println("Environment var - Edge Network environment: $edgeEnvironment")
        println("Environment var - Edge Network location hint: $edgeLocationHint")
        ServiceProvider.getInstance().networkService = realNetworkService

        // Set environment file ID for specific Edge Network environment
        MobileCore.configureWithAppID(TestSetupHelper.getEnvironmentFileID(edgeEnvironment))

        val latch = CountDownLatch(1)
        MobileCore.registerExtensions(
            listOf(
                Edge.EXTENSION,
                Identity.EXTENSION,
                MonitorExtension.EXTENSION
            )
        ) {
            latch.countDown()
        }
        latch.await()

        // Set Edge location hint if one is set for the test suite
        if (edgeLocationHint.isNotEmpty()) {
            print("Setting Edge location hint to: $edgeLocationHint")
            Edge.setLocationHint(edgeLocationHint)
        } else {
            print("No preset Edge location hint is being used for this test.")
        }

        resetTestExpectations()
    }

    @After
    fun tearDown() {
        realNetworkService.reset()
        // Clear any updated configuration
        MobileCore.clearUpdatedConfiguration()
    }

    /**
     * Tests that a standard sendEvent receives a single network response with HTTP code 200.
     */
    @Test
    fun testSendEvent_receivesExpectedNetworkResponse() {
        val interactNetworkRequest = TestableNetworkRequest(
            TestSetupHelper.createInteractURL(locationHint = edgeLocationHint),
            HttpMethod.POST
        )

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        Assert.assertEquals(1, matchingResponses?.size)
        Assert.assertEquals(200, matchingResponses?.firstOrNull()?.responseCode)
    }

    /**
     * Tests that a standard sendEvent receives a single network response with HTTP code 200.
     */
    @Test
    fun testSendEvent_whenComplexEvent_receivesExpectedNetworkResponse() {
        // Setup
        val interactNetworkRequest = TestableNetworkRequest(
            TestSetupHelper.createInteractURL(locationHint = edgeLocationHint),
            HttpMethod.POST
        )

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
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        Assert.assertEquals(1, matchingResponses?.size)
        Assert.assertEquals(200, matchingResponses?.firstOrNull()?.responseCode)
    }

    /**
     * Tests that a standard sendEvent() receives a single network response with HTTP code 200
     */
    @Test
    fun testSendEvent_whenComplexXDMEvent_receivesExpectedNetworkResponse() {
        // Setup
        val interactNetworkRequest = TestableNetworkRequest(
            TestSetupHelper.createInteractURL(locationHint = edgeLocationHint),
            HttpMethod.POST
        )

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
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        Assert.assertEquals(1, matchingResponses?.size)
        Assert.assertEquals(200, matchingResponses?.firstOrNull()?.responseCode)
    }

    /**
     * Tests that a standard sendEvent receives the expected event handles.
     */
    @Test
    fun testSendEvent_receivesExpectedEventHandles() {
        // Setup
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT,
            expectedCount = 1
        )
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.STATE_STORE,
            expectedCount = 1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        TestHelper.assertExpectedEvents(true)
    }

    /**
     * Tests that a standard sendEvent receives the expected event handles and does not receive an error event.
     */
    @Test
    fun testSendEvent_doesNotReceivesErrorEvent() {
        // Setup
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT,
            expectedCount = 1
        )
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.STATE_STORE,
            expectedCount = 1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        TestHelper.assertExpectedEvents(true)

        val errorEvents =
            TestSetupHelper.getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.ERROR_RESPONSE_CONTENT)
        Assert.assertEquals(0, errorEvents.size)
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

        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT,
            expectedCount = 1
        )

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
        val locationHintResult = TestSetupHelper.getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT)
            .first()

        JSONAsserts.assertTypeMatch(
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

            TestSetupHelper.expectEdgeEventHandle(
                expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT,
                expectedCount = 1
            )

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
            val locationHintResult = TestSetupHelper.getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT)
                .first()
            JSONAsserts.assertTypeMatch(
                expected = JSONObject(expectedLocationHintJSON),
                actual = JSONObject(locationHintResult.eventData),
                exactMatchPaths = listOf("payload[*].scope", "payload[*].hint")
            )

            resetTestExpectations()
        }
    }

    /**
     * Tests that a standard sendEvent with no prior state receives the expected state store event handle.
     */
    @Test
    fun testSendEvent_with_NO_priorState_receivesExpectedStateStoreEventHandle() {
        // Setup
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.STATE_STORE,
            expectedCount = 1
        )

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
        val stateStoreEvent = TestSetupHelper.getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.STATE_STORE)
            .last()

        // Exact match used here to strictly validate `payload` array element count == 2
        JSONAsserts.assertExactMatch(
            expected = JSONObject(expectedStateStoreJSON),
            actual = JSONObject(stateStoreEvent.eventData),
            typeMatchPaths = listOf(
                "payload[0].maxAge",
                "payload[0].value",
                "payload[1].maxAge",
                "payload[1].value"
            )
        )
    }

    /**
     * Tests that a standard sendEvent with prior state receives the expected state store event handle.
     */
    @Test
    fun testSendEvent_withPriorState_receivesExpectedStateStoreEventHandle() {
        // Setup
        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        Edge.sendEvent(experienceEvent) {}

        resetTestExpectations()

        EdgeLocationHint.values().map { it.rawValue }.shuffled().forEach { locationHint ->
            // Set location hint
            Edge.setLocationHint(locationHint)

            TestSetupHelper.expectEdgeEventHandle(
                expectedHandleType = TestConstants.EventSource.STATE_STORE,
                expectedCount = 1
            )

            // Test
            Edge.sendEvent(experienceEvent) {}

            // Verify
            val expectedStateStoreJSON = """
            {
              "payload": [
                {
                  "maxAge": 123,
                  "key": "kndctr_972C898555E9F7BC7F000101_AdobeOrg_cluster",
                  "value": "$locationHint"
                }
              ]
            }
            """.trimIndent()

            // Unsafe access used since testSendEvent_receivesExpectedEventHandles guarantees existence
            val stateStoreEvent = TestSetupHelper.getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.STATE_STORE)
                .last()

            // Exact match used here to strictly validate `payload` array element count == 2
            JSONAsserts.assertExactMatch(
                expected = JSONObject(expectedStateStoreJSON),
                actual = JSONObject(stateStoreEvent.eventData),
                typeMatchPaths = listOf("payload[0].maxAge")
            )

            resetTestExpectations()
        }
    }

    // 2nd event tests
    /**
     * Tests that sending two standard sendEvents receives the expected network response.
     */
    @Test
    fun testSendEventx2_receivesExpectedNetworkResponse() {
        // Setup
        // These expectations are used as a barrier for the event processing to complete
        TestSetupHelper.expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Extract location hint from Edge Network location hint response event
        val locationHintResult = TestSetupHelper.getLastLocationHintResultValue()

        if (locationHintResult.isNullOrEmpty()) {
            Assert.fail("Unable to extract valid location hint from location hint result event handle.")
        }

        // If there is a location hint preset for the test suite, check consistency between it and the
        // value from the Edge Network
        if (edgeLocationHint.isNotEmpty()) {
            Assert.assertEquals(edgeLocationHint, locationHintResult)
        }

        // Wait on all expectations to finish processing before clearing expectations
        TestHelper.assertExpectedEvents(true)

        // Reset all test expectations
        resetTestExpectations()

        // Set actual testing expectations
        // If test suite level location hint is not set, uses the value extracted from location hint result
        val locationHintNetworkRequest = TestableNetworkRequest(
            TestSetupHelper.createInteractURL(locationHintResult),
            HttpMethod.POST
        )

        realNetworkService.setExpectationForNetworkRequest(locationHintNetworkRequest, expectedCount = 1)

        // Test
        // 2nd event
        Edge.sendEvent(experienceEvent) {}

        // Verify
        // Network response assertions
        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(locationHintNetworkRequest)

        Assert.assertEquals(1, matchingResponses?.size)
        Assert.assertEquals(200, matchingResponses?.firstOrNull()?.responseCode)
    }

    /**
     * Tests that sending two standard sendEvents receives the expected event handles.
     */
    @Test
    fun testSendEventx2_receivesExpectedEventHandles() {
        // Setup
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT,
            expectedCount = 2
        )
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.STATE_STORE,
            expectedCount = 2
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}
        Edge.sendEvent(experienceEvent) {}

        // Verify
        TestHelper.assertExpectedEvents(true)
    }

    /**
     * Tests that sending two standard sendEvents does not receive any error event.
     */
    @Test
    fun testSendEventx2_doesNotReceivesErrorEvent() {
        // Setup
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT,
            expectedCount = 2
        )
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.STATE_STORE,
            expectedCount = 2
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}
        Edge.sendEvent(experienceEvent) {}

        // Verify
        TestHelper.assertExpectedEvents(true)

        val errorEvents =
            TestSetupHelper.getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.ERROR_RESPONSE_CONTENT)
        Assert.assertEquals(0, errorEvents.size)
    }

    /**
     * Tests that sending two standard sendEvents receives the expected location hint event handle.
     * It verifies the consistency of location hint between the first and second event handles.
     */
    @Test
    fun testSendEventx2_receivesExpectedLocationHintEventHandle() {
        // Setup
        TestSetupHelper.expectEdgeEventHandle(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        Edge.sendEvent(experienceEvent) {}

        // Extract location hint from Edge Network location hint response event
        val locationHintResult = TestSetupHelper.getLastLocationHintResultValue()
        if (locationHintResult.isNullOrEmpty()) {
            Assert.fail("Unable to extract valid location hint from location hint result event handle.")
        }

        // Reset all test expectations
        resetTestExpectations()

        // Set actual testing expectations
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT,
            expectedCount = 1
        )

        // Test
        // 2nd event
        Edge.sendEvent(experienceEvent) {}

        // Verify
        // If there is a location hint preset for the test suite, check consistency between it and the
        // value from the Edge Network
        if (edgeLocationHint.isNotEmpty()) {
            Assert.assertEquals(edgeLocationHint, locationHintResult)
        }

        // Verify location hint consistency between 1st and 2nd event handles
        val expectedLocationHintJSON = """
        {
          "payload": [
            {
              "ttlSeconds" : 123,
              "scope" : "EdgeNetwork",
              "hint" : "$locationHintResult"
            }
          ]
        }
        """.trimIndent()

        // Unsafe access used since testSendEvent_receivesExpectedEventHandles guarantees existence
        val locationHintResultEvent = TestSetupHelper.getEdgeEventHandles(expectedHandleType = TestConstants.EventSource.LOCATION_HINT_RESULT)
            .first()
        JSONAsserts.assertTypeMatch(
            expected = JSONObject(expectedLocationHintJSON),
            actual = JSONObject(locationHintResultEvent.eventData),
            exactMatchPaths = listOf("payload[*].scope", "payload[*].hint")
        )
    }

    /**
     * Tests that an invalid datastream ID returns the expected error.
     */
    @Test
    fun testSendEvent_withInvalidDatastreamID_receivesExpectedError() {
        // Setup
        // Waiting for configuration response content event solves the potential race condition of
        // configuration's shared state not reaching Edge before it starts processing the sendEvent event
        // 1st: from remote config download(?)
        // 2nd: from updateConfiguration
        TestHelper.setExpectationEvent(
            TestConstants.EventType.CONFIGURATION,
            TestConstants.EventSource.RESPONSE_CONTENT,
            2
        )

        MobileCore.updateConfiguration(mapOf("edge.configId" to "12345-example"))

        TestHelper.assertExpectedEvents(true)

        val interactNetworkRequest = TestableNetworkRequest(
            TestSetupHelper.createInteractURL(locationHint = edgeLocationHint),
            HttpMethod.POST
        )
        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.ERROR_RESPONSE_CONTENT,
            expectedCount = 1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        // Network response assertions
        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        Assert.assertEquals(1, matchingResponses?.size)
        Assert.assertEquals(400, matchingResponses?.firstOrNull()?.responseCode)

        // Event assertions
        val expectedErrorJSON = """
        {
            "status": 400,
            "detail": "stringType",
            "report": {
                "requestId": "stringType"
            },
            "requestEventId": "stringType",
            "title": "Invalid datastream ID",
            "type": "https://ns.adobe.com/aep/errors/EXEG-0003-400",
            "requestId": "stringType"
        }
        """.trimIndent()

        val errorEvents = TestSetupHelper.getEdgeResponseErrors()

        Assert.assertEquals(1, errorEvents.size)

        val errorEvent = errorEvents.first()
        JSONAsserts.assertTypeMatch(
            expected = JSONObject(expectedErrorJSON),
            actual = JSONObject(errorEvent.eventData),
            exactMatchPaths = listOf("status", "title", "type")
        )
    }

    @Test
    fun testSendEvent_withInvalidLocationHint_receivesExpectedError() {
        // Tests that an invalid location hint returns the expected error with 0 byte data body

        // Setup
        val invalidNetworkRequest = TestableNetworkRequest(
            TestSetupHelper.createInteractURL(locationHint = "invalid"),
            HttpMethod.POST
        )

        realNetworkService.setExpectationForNetworkRequest(networkRequest = invalidNetworkRequest, expectedCount = 1)
        TestSetupHelper.expectEdgeEventHandle(
            expectedHandleType = TestConstants.EventSource.ERROR_RESPONSE_CONTENT,
            expectedCount = 1
        )

        Edge.setLocationHint("invalid")

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .build()

        // Test
        Edge.sendEvent(experienceEvent) {}

        // Verify
        // Network response assertions
        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(networkRequest = invalidNetworkRequest)

        Assert.assertEquals(1, matchingResponses?.size)
        // Convenience to assert directly on the first element in the rest of the test case
        val matchingResponse = matchingResponses?.firstOrNull()
        Assert.assertEquals(404, matchingResponse?.responseCode)

        val contentLengthHeader = matchingResponse?.getResponsePropertyValue("Content-Length")
        val contentLength = contentLengthHeader?.toIntOrNull()

        if (contentLength != null) {
            println("Content-Length: $contentLength")
            Assert.assertEquals(0, contentLength)
        } else {
            println("Content-Length header not found or not a valid integer")
        }

        // Should be null when there is no response body from the server
        val responseBodySize = matchingResponse?.inputStream?.readBytes()?.size
        Assert.assertNull(responseBodySize)

        // Error event assertions
        TestHelper.assertExpectedEvents(true)
    }

    /**
     * Resets all test helper expectations and recorded data
     */
    private fun resetTestExpectations() {
        realNetworkService.reset()
        TestHelper.resetTestExpectations()
    }
}
