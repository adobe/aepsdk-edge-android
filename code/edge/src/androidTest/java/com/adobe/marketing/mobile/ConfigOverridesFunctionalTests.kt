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

package com.adobe.marketing.mobile

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.ServiceProvider
import com.adobe.marketing.mobile.util.JSONAsserts.assertExactMatch
import com.adobe.marketing.mobile.util.KeyMustBeAbsent
import com.adobe.marketing.mobile.util.MockNetworkService
import com.adobe.marketing.mobile.util.MonitorExtension
import com.adobe.marketing.mobile.util.TestConstants
import com.adobe.marketing.mobile.util.TestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.util.Arrays
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class ConfigOverridesFunctionalTests {

    private val mockNetworkService = MockNetworkService()
    private val EXEDGE_INTERACT_URL_STRING = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING
    private val CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef"
    private val DEFAULT_RESPONSE_STRING = "\u0000{\"test\": \"json\"}"
    private val TIMEOUT_MILLIS = 5000

    @JvmField
    @Rule
    var rule: RuleChain = RuleChain
        .outerRule(TestHelper.LogOnErrorRule())
        .around(TestHelper.SetupCoreRule())

    @Before
    @Throws(Exception::class)
    fun setup() {
        resetTestExpectations()

        ServiceProvider.getInstance().networkService =
            mockNetworkService
        TestHelper.setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1)
        TestHelper.setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1)
        TestHelper.setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, 4)
        val config = mapOf("edge.configId" to CONFIG_ID)

        MobileCore.updateConfiguration(config)
        val latch = CountDownLatch(1)
        MobileCore.registerExtensions(
            Arrays.asList(Edge.EXTENSION, Identity.EXTENSION, MonitorExtension.EXTENSION),
            AdobeCallback { o: Any? -> latch.countDown() }
        )
        latch.await()
        TestHelper.assertExpectedEvents(false)
        resetTestExpectations()
    }

    @After
    fun tearDown() {
        mockNetworkService.reset()
    }

    // --------------------------------------------------------------------------------------------
    // test network request data
    // --------------------------------------------------------------------------------------------
    @Test
    @Throws(InterruptedException::class)
    fun testSendEvent_withXDMDataAndCustomDataAndDatastreamConfigOverrides_sendsNetworkRequestWithConfigOverridesMetadata() {
        val responseConnection = mockNetworkService.createMockNetworkResponse(
            DEFAULT_RESPONSE_STRING,
            200
        )
        mockNetworkService.setMockResponseFor(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            responseConnection
        )
        mockNetworkService.setExpectationForNetworkRequest(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            1
        )

        val configOverrides: Map<String, Any> = mapOf(
            "com_adobe_experience_platform" to mapOf(
                "datasets" to mapOf(
                    "event" to mapOf("datasetId" to "eventDatasetIdOverride"),
                    "profile" to mapOf("datasetId" to "profileDatasetIdOverride")
                )
            ),
            "com_adobe_analytics" to mapOf(
                "reportSuites" to listOf("rsid1", "rsid2", "rsid3")
            ),
            "com_adobe_identity" to mapOf(
                "idSyncContainerId" to "1234567"
            ),
            "com_adobe_target" to mapOf(
                "propertyToken" to "samplePropertyToken"
            )
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("testXdmKey" to "testXdmValue"))
            .setData(mapOf("testCustomKey" to "testCustomValue"))
            .setDatastreamConfigOverride(configOverrides)
            .build()
        Edge.sendEvent(experienceEvent, null)

        // verify
        mockNetworkService.assertAllNetworkRequestExpectations()
        val resultRequests = mockNetworkService.getNetworkRequestsWith(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            TIMEOUT_MILLIS
        )
        assertEquals(1, resultRequests.size.toLong())

        val expected = """
        {
          "events": [
            {
              "data": {
                "testCustomKey": "testCustomValue"
              },
              "xdm": {
                "testXdmKey": "testXdmValue"
              }
            }
          ],
          "meta": {
            "configOverrides": {
              "com_adobe_analytics": {
                "reportSuites": [
                  "rsid1",
                  "rsid2",
                  "rsid3"
                ]
              },
              "com_adobe_experience_platform": {
                "datasets": {
                  "event": {
                    "datasetId": "eventDatasetIdOverride"
                  },
                  "profile": {
                    "datasetId": "profileDatasetIdOverride"
                  }
                }
              },
              "com_adobe_identity": {
                "idSyncContainerId": "1234567"
              },
              "com_adobe_target": {
                "propertyToken": "samplePropertyToken"
              }
            }
          }
        }
        """
        assertExactMatch(expected, resultRequests.getOrNull(0)?.getBodyJson())
    }

    @Test
    @Throws(InterruptedException::class)
    fun testSendEvent_withXDMDataAndEmptyDatastreamConfigOverrides_sendsNetworkRequestWithoutConfigOverridesMetadata() {
        val responseConnection = mockNetworkService.createMockNetworkResponse(
            DEFAULT_RESPONSE_STRING,
            200
        )
        mockNetworkService.setMockResponseFor(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            responseConnection
        )
        mockNetworkService.setExpectationForNetworkRequest(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("testXdmKey" to "testXdmValue"))
            .setDatastreamConfigOverride(mapOf())
            .build()
        Edge.sendEvent(experienceEvent, null)

        // verify
        mockNetworkService.assertAllNetworkRequestExpectations()
        val resultRequests = mockNetworkService.getNetworkRequestsWith(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            TIMEOUT_MILLIS
        )
        assertEquals(1, resultRequests.size.toLong())

        val expected = """
        {
          "events": [
            {
              "xdm": {
                "testXdmKey": "testXdmValue"
              }
            }
          ],
          "meta": {
            "konductorConfig": {}
          }
        }
        """
        // Verify that configOverrides is not present in the meta data
        assertExactMatch(expected, resultRequests.getOrNull(0)?.getBodyJson(), KeyMustBeAbsent("meta.configOverrides"))
    }

    @Test
    @Throws(InterruptedException::class)
    fun testSendEvent_withXDMDataAndNullDatastreamConfigOverrides_sendsNetworkRequestWithoutConfigOverridesMetadata() {
        val responseConnection = mockNetworkService.createMockNetworkResponse(
            DEFAULT_RESPONSE_STRING,
            200
        )
        mockNetworkService.setMockResponseFor(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            responseConnection
        )
        mockNetworkService.setExpectationForNetworkRequest(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("testXdmKey" to "testXdmValue"))
            .setDatastreamConfigOverride(null)
            .build()

        Edge.sendEvent(experienceEvent, null)

        // verify
        mockNetworkService.assertAllNetworkRequestExpectations()
        val resultRequests = mockNetworkService.getNetworkRequestsWith(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            TIMEOUT_MILLIS
        )
        assertEquals(1, resultRequests.size.toLong())

        val expected = """
        {
          "events": [
            {
              "xdm": {
                "testXdmKey": "testXdmValue"
              }
            }
          ],
          "meta": {
            "konductorConfig": {}
          }
        }
        """
        // Verify that configOverrides is not present in the meta data
        assertExactMatch(expected, resultRequests.getOrNull(0)?.getBodyJson(), KeyMustBeAbsent("meta.configOverrides"))
    }

    @Test
    @Throws(InterruptedException::class)
    fun testSendEvent_withXDMDataAndCustomDataAndDatastreamIdOverride_usesDatastreamIdOverride_sendsNetworkRequestWithOriginalSDKConfig() {
        val responseConnection = mockNetworkService.createMockNetworkResponse(
            DEFAULT_RESPONSE_STRING,
            200
        )
        mockNetworkService.setMockResponseFor(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            responseConnection
        )
        mockNetworkService.setExpectationForNetworkRequest(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("testXdmKey" to "testXdmValue"))
            .setData(mapOf("testCustomKey" to "testCustomValue"))
            .setDatastreamIdOverride("5678abcd-abcd-1234-5678-123456abcdef")
            .build()

        Edge.sendEvent(experienceEvent, null)

        // verify
        mockNetworkService.assertAllNetworkRequestExpectations()
        val resultRequests = mockNetworkService.getNetworkRequestsWith(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            TIMEOUT_MILLIS
        )
        assertEquals(1, resultRequests.size.toLong())

        // Assert that provided datastreamIdOverride value is in the URL query param for configId
        assertEquals("5678abcd-abcd-1234-5678-123456abcdef", resultRequests.getOrNull(0)?.queryParam("configId"))

        val expected = """
        {
          "events": [
            {
              "data": {
                "testCustomKey": "testCustomValue"
              },
              "xdm": {
                "testXdmKey": "testXdmValue"
              }
            }
          ],
          "meta": {
            "sdkConfig": {
              "datastream": {
                "original": "1234abcd-abcd-1234-5678-123456abcdef"
              }
            }
          }
        }
        """
        assertExactMatch(expected, resultRequests.getOrNull(0)?.getBodyJson())
    }

    @Test
    @Throws(InterruptedException::class)
    fun testSendEvent_withXDMDataAndEmptyDatastreamIdOverride_usesDefaultDatastreamId_sendsNetworkRequestWithoutSDKConfigMetadata() {
        val responseConnection = mockNetworkService.createMockNetworkResponse(
            DEFAULT_RESPONSE_STRING,
            200
        )
        mockNetworkService.setMockResponseFor(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            responseConnection
        )
        mockNetworkService.setExpectationForNetworkRequest(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("testXdmKey" to "testXdmValue"))
            .setDatastreamIdOverride("")
            .build()

        Edge.sendEvent(experienceEvent, null)

        // verify
        mockNetworkService.assertAllNetworkRequestExpectations()
        val resultRequests = mockNetworkService.getNetworkRequestsWith(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            TIMEOUT_MILLIS
        )
        assertEquals(1, resultRequests.size.toLong())

        // Assert that default datastream ID value is in the URL query param for configId
        assertEquals(CONFIG_ID, resultRequests.getOrNull(0)?.queryParam("configId"))

        val expected = """
        {
          "events": [
            {
              "xdm": {
                "testXdmKey": "testXdmValue"
              }
            }
          ]
        }
        """
        // Verify that sdkConfig is not present in the meta data
        assertExactMatch(expected, resultRequests.getOrNull(0)?.getBodyJson(), KeyMustBeAbsent("meta.sdkConfig"))
    }

    @Test
    @Throws(InterruptedException::class)
    fun testSendEvent_withXDMDataAndNullDatastreamIdOverride_usesDefaultDatastreamId_sendsNetworkRequestWithoutSDKConfigMetadata() {
        val responseConnection = mockNetworkService.createMockNetworkResponse(
            DEFAULT_RESPONSE_STRING,
            200
        )
        mockNetworkService.setMockResponseFor(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            responseConnection
        )
        mockNetworkService.setExpectationForNetworkRequest(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            1
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("testXdmKey" to "testXdmValue"))
            .setDatastreamIdOverride(null)
            .build()

        Edge.sendEvent(experienceEvent, null)

        // verify
        mockNetworkService.assertAllNetworkRequestExpectations()
        val resultRequests = mockNetworkService.getNetworkRequestsWith(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            TIMEOUT_MILLIS
        )
        assertEquals(1, resultRequests.size.toLong())

        // Assert that default datastream ID value is in the URL query param for configId
        assertEquals(CONFIG_ID, resultRequests.getOrNull(0)?.queryParam("configId"))

        val expected = """
        {
          "events": [
            {
              "xdm": {
                "testXdmKey": "testXdmValue"
              }
            }
          ]
        }
        """
        // Verify that sdkConfig is not present in the meta data
        assertExactMatch(expected, resultRequests.getOrNull(0)?.getBodyJson(), KeyMustBeAbsent("meta.sdkConfig"))
    }

    @Test
    @Throws(InterruptedException::class)
    fun testSendEvent_withXDMDataAndCustomDataAndDatastreamIdAndConfigOverrides_usesDatastreamIdOverride_sendsNetworkRequestWithSDKConfigAndConfigOverridesMetadata() {
        val responseConnection = mockNetworkService.createMockNetworkResponse(
            DEFAULT_RESPONSE_STRING,
            200
        )
        mockNetworkService.setMockResponseFor(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            responseConnection
        )
        mockNetworkService.setExpectationForNetworkRequest(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            1
        )

        // Actual data
        val configOverrides: Map<String, Any> = mapOf(
            "com_adobe_experience_platform" to mapOf(
                "datasets" to mapOf(
                    "event" to mapOf("datasetId" to "eventDatasetIdOverride"),
                    "profile" to mapOf("datasetId" to "profileDatasetIdOverride")
                )
            ),
            "com_adobe_analytics" to mapOf(
                "reportSuites" to listOf("rsid1", "rsid2", "rsid3")
            ),
            "com_adobe_identity" to mapOf(
                "idSyncContainerId" to "1234567"
            ),
            "com_adobe_target" to mapOf(
                "propertyToken" to "samplePropertyToken"
            )
        )

        val experienceEvent = ExperienceEvent.Builder()
            .setXdmSchema(mapOf("testXdmKey" to "testXdmValue"))
            .setData(mapOf("testCustomKey" to "testCustomValue"))
            .setDatastreamConfigOverride(configOverrides)
            .setDatastreamIdOverride("5678abcd-abcd-1234-5678-123456abcdef")
            .build()

        Edge.sendEvent(experienceEvent, null)

        // verify
        mockNetworkService.assertAllNetworkRequestExpectations()
        val resultRequests = mockNetworkService.getNetworkRequestsWith(
            EXEDGE_INTERACT_URL_STRING,
            HttpMethod.POST,
            TIMEOUT_MILLIS
        )
        assertEquals(1, resultRequests.size.toLong())

        // Assert that provided datastreamIdOverride value is in the URL query param for configId
        assertEquals("5678abcd-abcd-1234-5678-123456abcdef", resultRequests.getOrNull(0)?.queryParam("configId"))

        val expected = """
        {
          "events": [
            {
              "data": {
                "testCustomKey": "testCustomValue"
              },
              "xdm": {
                "testXdmKey": "testXdmValue"
              }
            }
          ],
          "meta": {
            "configOverrides": {
              "com_adobe_analytics": {
                "reportSuites": [
                  "rsid1",
                  "rsid2",
                  "rsid3"
                ]
              },
              "com_adobe_experience_platform": {
                "datasets": {
                  "event": {
                    "datasetId": "eventDatasetIdOverride"
                  },
                  "profile": {
                    "datasetId": "profileDatasetIdOverride"
                  }
                }
              },
              "com_adobe_identity": {
                "idSyncContainerId": "1234567"
              },
              "com_adobe_target": {
                "propertyToken": "samplePropertyToken"
              }
            },
            "sdkConfig": {
              "datastream": {
                "original": "1234abcd-abcd-1234-5678-123456abcdef"
              }
            }
          }
        }            
        """
        assertExactMatch(expected, resultRequests.getOrNull(0)?.getBodyJson())
    }

    /**
     * Resets all test helper expectations and recorded data
     */
    private fun resetTestExpectations() {
        mockNetworkService.reset()
        TestHelper.resetTestExpectations()
    }
}
