package com.adobe.marketing.mobile.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.ExperienceEvent
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.integration.util.TestSetupHelper
import com.adobe.marketing.mobile.integration.util.TestSetupHelper.createInteractURL
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import com.adobe.marketing.mobile.util.RealNetworkService
import com.adobe.marketing.mobile.util.TestHelper
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals

import com.adobe.marketing.mobile.integration.util.TestSetupHelper.getEnvironmentFileID
import com.adobe.marketing.mobile.util.JSONAsserts
import com.adobe.marketing.mobile.util.JSONAsserts.assertExactMatch
import org.json.JSONObject
import org.junit.Ignore

/**
 * Performs validation on integration with the Edge Network upstream service with configOverrides.
 */
@RunWith(AndroidJUnit4::class)
class ConfigOverridesIntegrationTests {
    private val realNetworkService = RealNetworkService()
    private val edgeLocationHint: String = BuildConfig.EDGE_LOCATION_HINT
    private val edgeEnvironment: String = BuildConfig.EDGE_ENVIRONMENT
    private val VALID_DATASTREAM_ID_OVERRIDE = "15d7bce0-3e2c-447b-bbda-129c57c60820"
    private val VALID_DATASET_ID_CONFIGURED_AS_OVERRIDE = "6515e1dbfeb3b128d19bb1e4"
    private val VALID_DATASET_ID_NOT_CONFIGURED_AS_OVERRIDE = "6515e1f6296d1e28d3209b9f"
    private val VALID_RSID_CONFIGURED_AS_OVERRIDE = "mobile5.e2e.rsid2"
    private val VALID_RSID_NOT_CONFIGURED_AS_OVERRIDE = "mobile5e2e.rsid3"

    @JvmField
    @Rule
    var rule: RuleChain = RuleChain
        .outerRule(TestHelper.LogOnErrorRule())
        .around(TestHelper.SetupCoreRule())
        .around(TestHelper.RegisterMonitorExtensionRule())

    @Before
    @Throws(Exception::class)
    fun setup() {
        println("Environment var - Edge Network environment: $edgeEnvironment")
        println("Environment var - Edge Network location hint: $edgeLocationHint")
        ServiceProvider.getInstance().networkService = realNetworkService

        // Set environment file ID for specific Edge Network environment
        MobileCore.configureWithAppID(getEnvironmentFileID(edgeEnvironment))
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

        resetTestExpectations()
    }

    @After
    fun tearDown() {
        realNetworkService.reset()
        TestHelper.resetCoreHelper()
    }

    @Test
    fun testSendEvent_withValidDatastreamIdOverride_receivesExpectedNetworkResponse() {
        val interactNetworkRequest = TestableNetworkRequest(createInteractURL(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .setDatastreamIdOverride(VALID_DATASTREAM_ID_OVERRIDE)
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        assertEquals(1, matchingResponses?.size)
        assertEquals(200, matchingResponses?.firstOrNull()?.responseCode)
    }

    @Test
    fun testSendEvent_withInvalidDatastreamIdOverride_receivesErrorResponse() {
        val interactNetworkRequest = TestableNetworkRequest(createInteractURL(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .setDatastreamIdOverride("DummyDatastreamID")
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        assertEquals(1, matchingResponses?.size)
        assertEquals(400, matchingResponses?.firstOrNull()?.responseCode)

        val expectedErrorJSON = """
        {
            "status": 400,
            "title": "Invalid datastream ID",
            "type": "https://ns.adobe.com/aep/errors/EXEG-0003-400"
        }
        """

        val errorEvents = TestSetupHelper.getEdgeResponseErrors()

        assertEquals(1, errorEvents.size)

        val errorEvent = errorEvents.first()
        JSONAsserts.assertExactMatch(
            expected = JSONObject(expectedErrorJSON),
            actual = JSONObject(errorEvent.eventData)
        )
    }

    @Test
    fun testSendEvent_withValidDatastreamConfigOverride_receivesExpectedNetworkResponse() {
        val interactNetworkRequest = TestableNetworkRequest(createInteractURL(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val configOverrides = mapOf(
            "com_adobe_experience_platform" to mapOf(
                "datasets" to mapOf(
                    "event" to mapOf(
                        "datasetId" to VALID_DATASET_ID_CONFIGURED_AS_OVERRIDE
                    )
                )
            ),
            "com_adobe_analytics" to mapOf(
                "reportSuites" to listOf(
                    VALID_RSID_CONFIGURED_AS_OVERRIDE
                )
            )
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .setDatastreamConfigOverride(configOverrides)
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        assertEquals(1, matchingResponses?.size)
        assertEquals(200, matchingResponses?.firstOrNull()?.responseCode)
    }

    // TODO: Enable after PDCL-11131 issue is fixed
    @Ignore
    @Test
    fun testSendEvent_withInvalidDatastreamConfigOverride_dummyValues_receivesErrorResponse() {
        val interactNetworkRequest = TestableNetworkRequest(createInteractURL(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val configOverridesWithDummyValues = mapOf(
            "com_adobe_experience_platform" to mapOf(
                "datasets" to mapOf(
                    "event" to mapOf(
                        "datasetId" to "DummyDataset"
                    )
                )
            ),
            "com_adobe_analytics" to mapOf(
                "reportSuites" to listOf(
                    "DummyRSID1",
                    "DummyRSID2"
                )
            )
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .setDatastreamConfigOverride(configOverridesWithDummyValues)
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        assertEquals(1, matchingResponses?.size)
        assertEquals(400, matchingResponses?.firstOrNull()?.responseCode)

        val expectedErrorJSON = """
        {
            "status": 400,
            "title": "Invalid request",
            "type": "https://ns.adobe.com/aep/errors/EXEG-0113-400"
        }
        """

        val errorEvents = TestSetupHelper.getEdgeResponseErrors()

        assertEquals(1, errorEvents.size)

        val errorEvent = errorEvents.first()
        JSONAsserts.assertExactMatch(
            expected = JSONObject(expectedErrorJSON),
            actual = JSONObject(errorEvent.eventData)
        )
    }

    // TODO: Enable after PDCL-11131 issue is fixed
    @Ignore
    @Test
    fun testSendEvent_withInvalidDatastreamConfigOverride_notConfiguredValues_receivesErrorResponse() {
        val interactNetworkRequest = TestableNetworkRequest(createInteractURL(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val configOverridesWithUnconfiguredValues = mapOf(
            "com_adobe_experience_platform" to mapOf(
                "datasets" to mapOf(
                    "event" to mapOf(
                        "datasetId" to VALID_DATASET_ID_NOT_CONFIGURED_AS_OVERRIDE
                    )
                )
            ),
            "com_adobe_analytics" to mapOf(
                "reportSuites" to listOf(
                   VALID_RSID_NOT_CONFIGURED_AS_OVERRIDE
                )
            )
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .setDatastreamConfigOverride(configOverridesWithUnconfiguredValues)
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        assertEquals(1, matchingResponses?.size)
        assertEquals(400, matchingResponses?.firstOrNull()?.responseCode)

        val expectedErrorJSON = """
        {
            "status": 400,
            "title": "Invalid request",
            "type": "https://ns.adobe.com/aep/errors/EXEG-0113-400"
        }
        """

        val errorEvents = TestSetupHelper.getEdgeResponseErrors()

        assertEquals(1, errorEvents.size)

        val errorEvent = errorEvents.first()
        JSONAsserts.assertExactMatch(
            expected = JSONObject(expectedErrorJSON),
            actual = JSONObject(errorEvent.eventData)
        )
    }

    // TODO: Enable after PDCL-11131 issue is fixed
    @Ignore
    @Test
    fun testSendEvent_withInvalidDatastreamConfigOverride_validAndDummyValues_receivesExpectedNetworkResponse() {
        val interactNetworkRequest = TestableNetworkRequest(createInteractURL(locationHint = edgeLocationHint), HttpMethod.POST)

        realNetworkService.setExpectationForNetworkRequest(interactNetworkRequest, expectedCount = 1)

        val configOverrides = mapOf(
            "com_adobe_experience_platform" to mapOf(
                "datasets" to mapOf(
                    "event" to mapOf(
                        "datasetId" to VALID_DATASET_ID_CONFIGURED_AS_OVERRIDE
                    )
                )
            ),
            "com_adobe_analytics" to mapOf(
                "reportSuites" to listOf(
                    VALID_RSID_CONFIGURED_AS_OVERRIDE,
                    "DummyRSID2"
                )
            )
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("xdmtest" to "data"))
            .setData(mapOf("data" to mapOf("test" to "data")))
            .setDatastreamConfigOverride(configOverrides)
            .build()

        Edge.sendEvent(experienceEvent) {}

        realNetworkService.assertAllNetworkRequestExpectations()
        val matchingResponses = realNetworkService.getResponsesFor(interactNetworkRequest)

        assertEquals(1, matchingResponses?.size)
        assertEquals(400, matchingResponses?.firstOrNull()?.responseCode)

        val expectedErrorJSON = """
        {
            "status": 400,
            "title": "Invalid request",
            "type": "https://ns.adobe.com/aep/errors/EXEG-0113-400"
        }
        """

        val errorEvents = TestSetupHelper.getEdgeResponseErrors()

        assertEquals(1, errorEvents.size)

        val errorEvent = errorEvents.first()
        JSONAsserts.assertExactMatch(
            expected = JSONObject(expectedErrorJSON),
            actual = JSONObject(errorEvent.eventData)
        )
    }

    /**
     * Resets all test helper expectations and recorded data
     */
    private fun resetTestExpectations() {
        realNetworkService.reset()
        TestHelper.resetTestExpectations()
    }
}