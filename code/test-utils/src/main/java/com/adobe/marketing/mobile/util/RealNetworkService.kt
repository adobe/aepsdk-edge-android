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
import com.adobe.marketing.mobile.services.NetworkCallback
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.NetworkServiceHelper
import com.adobe.marketing.mobile.services.TestableNetworkRequest

/**
 * An override of `NetworkService` used for tests that require real outgoing network requests. Provides
 * methods to set expectations on network requests and perform assertions against those expectations.
 */
class RealNetworkService: NetworkServiceHelper(), TestResettable {
    private val helper = NetworkRequestHelper()
    companion object {
        private const val LOG_SOURCE = "RealNetworkService"
    }

    override fun connectAsync(request: NetworkRequest?, callback: NetworkCallback?) {
        val testableNetworkRequest = TestableNetworkRequest(request)
        helper.recordSentNetworkRequest(testableNetworkRequest)
        super.connectAsync(testableNetworkRequest) {
            helper.setResponseConnectionFor(testableNetworkRequest, it)
            helper.countDownExpected(testableNetworkRequest)

            callback?.call(it)
        }
    }

    // Passthrough for shared helper APIs
    /**
     * Asserts that the correct number of network requests were sent based on the expectations set
     * using [setExpectationForNetworkRequest].
     *
     * @throws InterruptedException
     * @see [setExpectationForNetworkRequest]
     */
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

    override fun reset() {
        helper.reset()
    }


    /**
     * Returns the associated [HttpConnecting] response for a given [NetworkRequest], and only if an
     * expectation is set for the [NetworkRequest], waiting up to the specified timeout.
     *
     * @param networkRequest The [NetworkRequest] for which the response should be returned.
     * @param timeoutMillis The maximum time, in milliseconds, to wait for the [NetworkRequest] to complete. Defaults to [TestConstants.Defaults.WAIT_NETWORK_REQUEST_TIMEOUT_MS].
     * @return The associated [HttpConnecting] response for the given request or `null` if not found.
     * @throws InterruptedException if the current thread is interrupted while waiting.
     */
    fun getResponsesFor(networkRequest: NetworkRequest, timeoutMillis: Int = TestConstants.Defaults.WAIT_NETWORK_REQUEST_TIMEOUT_MS): HttpConnecting? {
        helper.awaitFor(TestableNetworkRequest(networkRequest), timeoutMillis)
        return helper.getResponsesFor(TestableNetworkRequest(networkRequest))
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
}