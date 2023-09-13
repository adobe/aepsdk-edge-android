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

package com.adobe.marketing.mobile.util

import com.adobe.marketing.mobile.services.HttpConnecting
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.NetworkCallback
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.Networking
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * [Networking] conforming network service utility used for tests that require mocked network requests and mocked responses.
 */
class MockNetworkService: Networking {
    private val helper = NetworkRequestHelper()
    // Simulating the async network service
    private val executorService: ExecutorService = Executors.newCachedThreadPool()

    companion object {
        private const val LOG_SOURCE = "MockNetworkService"
        private var delayedResponse = 0
        private val defaultResponse: HttpConnecting = object : HttpConnecting {
            override fun getInputStream(): InputStream {
                return ByteArrayInputStream("".toByteArray())
            }

            override fun getErrorStream(): InputStream? {
                return null
            }

            override fun getResponseCode(): Int {
                return 200
            }

            override fun getResponseMessage(): String {
                return ""
            }

            override fun getResponsePropertyValue(responsePropertyKey: String): String? {
                return null
            }

            override fun close() {}
        }
    }

    override fun connectAsync(networkRequest: NetworkRequest, resultCallback: NetworkCallback?) {
        Log.trace(
            TestConstants.LOG_TAG,
            LOG_SOURCE,
            "Received connectUrlAsync to URL '%s' and HttpMethod '%s'.",
            networkRequest.url,
            networkRequest.method.name
        )

        val testableNetworkRequest = TestableNetworkRequest(networkRequest)
        helper.recordSentNetworkRequest(testableNetworkRequest)
        helper.countDownExpected(testableNetworkRequest)

        executorService.submit {
            if (resultCallback != null) {
                if (delayedResponse > 0) {
                    try {
                        Thread.sleep((delayedResponse * 1000).toLong())
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                resultCallback.call(helper.getResponseFor(testableNetworkRequest) ?: defaultResponse)
            }
        }
    }

    fun reset() {
        delayedResponse = 0
        helper.reset()
    }

    /**
     * Set a custom mock network response to an Edge network request.
     * @param url the url string for which to return the response
     * @param method the HTTP method for which to return the response
     * @param responseConnection the network response to be returned when a request matching the
     * `url` and `method` is received. If `null` is provided, a default '200' response is used.
     */
    fun setMockResponseFor(
        url: String?,
        method: HttpMethod?,
        responseConnection: HttpConnecting?
    ) {
        helper.setResponseConnectionFor(
            TestableNetworkRequest(
                url,
                method
            ), responseConnection ?: defaultResponse
        )
    }

    /**
     * Sets an expectation for a network request's send count.
     * @param url the url string for which to set the expectation
     * @param method the HTTP method for which to set the expectation
     * @param expectedCount how many times a request with this `url` and `method` is expected to be sent
     */
    fun setExpectationForNetworkRequest(
        url: String?,
        method: HttpMethod?,
        expectedCount: Int
    ) {
        helper.setExpectationForNetworkRequest(
            TestableNetworkRequest(
                url,
                method
            ), expectedCount
        )
    }

    fun assertAllNetworkRequestExpectations() {
        helper.assertAllNetworkRequestExpectations()
    }

    /**
     * Returns the [TestableNetworkRequest](s) sent through the
     * Core NetworkService, or empty if none was found. Use this API after calling
     * [setExpectationForNetworkRequest] to wait for each request.
     *
     * @param url The url string for which to retrieved the network requests sent
     * @param method the HTTP method for which to retrieve the network requests
     * @param timeoutMillis how long should this method wait for the expected network requests, in milliseconds
     * @return list of network requests with the provided `url` and `command`, or empty if none was dispatched
     * @throws InterruptedException
     */
    @Throws(InterruptedException::class)
    @JvmOverloads
    fun getNetworkRequestsWith(url: String?,
                               method: HttpMethod?,
                               timeoutMillis: Int = TestConstants.Defaults.WAIT_NETWORK_REQUEST_TIMEOUT_MS
    ): List<TestableNetworkRequest?> {
        return helper.getNetworkRequestsWith(url, method, timeoutMillis)
    }

    /**
     * Create a mock network response to be used when calling [setMockResponseFor].
     * @param responseString the network response string, returned by [HttpConnecting.getInputStream]
     * @param code the HTTP status code, returned by [HttpConnecting.getResponseCode]
     * @return an [HttpConnecting] object
     * @see setMockResponseFor
     */
    fun createMockNetworkResponse(responseString: String?, code: Int): HttpConnecting? {
        return createMockNetworkResponse(responseString, null, code, null, null)
    }

    /**
     * Create a mock network response to be used when calling [setMockResponseFor].
     * @param responseString the network response string, returned by [HttpConnecting.getInputStream]
     * @param errorString the network error string, returned by [HttpConnecting.getErrorStream]
     * @param code the HTTP status code, returned by [HttpConnecting.getResponseCode]
     * @param responseMessage the network response message, returned by [HttpConnecting.getResponseMessage]
     * @param propertyMap the network response header map, returned by [HttpConnecting.getResponsePropertyValue]
     * @return an [HttpConnecting] object
     * @see setMockResponseFor
     */
    fun createMockNetworkResponse(
        responseString: String?,
        errorString: String?,
        code: Int,
        responseMessage: String?,
        propertyMap: Map<String?, String?>?
    ): HttpConnecting? {
        return object : HttpConnecting {
            override fun getInputStream(): InputStream? {
                return if (responseString != null) {
                    ByteArrayInputStream(responseString.toByteArray(StandardCharsets.UTF_8))
                } else null
            }

            override fun getErrorStream(): InputStream? {
                return if (errorString != null) {
                    ByteArrayInputStream(errorString.toByteArray(StandardCharsets.UTF_8))
                } else null
            }

            override fun getResponseCode(): Int {
                return code
            }

            override fun getResponseMessage(): String? {
                return responseMessage
            }

            override fun getResponsePropertyValue(responsePropertyKey: String): String? {
                return if (propertyMap != null) {
                    propertyMap[responsePropertyKey]
                } else null
            }

            override fun close() {}
        }
    }

    /**
     * Sets the provided delay for all network responses, until reset.
     * @param delaySec delay in seconds
     */
    fun enableNetworkResponseDelay(delaySec: Int) {
        if (delaySec < 0) {
            return
        }
        delayedResponse = delaySec
    }

    /**
     * Use this API for JSON formatted [NetworkRequest] body in order to retrieve a flattened map containing its data.
     * @param networkRequest the [NetworkRequest] to parse
     * @return The JSON request body represented as a flatten map
     */
    fun getFlattenedNetworkRequestBody(networkRequest: NetworkRequest): Map<String?, String?>? {
        return TestUtils.flattenBytes(networkRequest.body)
    }
}