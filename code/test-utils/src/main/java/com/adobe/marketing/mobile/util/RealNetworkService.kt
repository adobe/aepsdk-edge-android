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
class RealNetworkService: NetworkServiceHelper() {
    private val helper = NetworkRequestHelper()
    companion object {
        private const val LOG_SOURCE = "RealNetworkService"
    }

    override fun connectAsync(request: NetworkRequest?, callback: NetworkCallback?) {
        val testableNetworkRequest = TestableNetworkRequest(request)
        helper.recordSentNetworkRequest(testableNetworkRequest)
        super.connectAsync(testableNetworkRequest) {
            helper.addResponseFor(testableNetworkRequest, it)
            helper.countDownExpected(testableNetworkRequest)

            callback?.call(it)
        }
    }

    // Passthrough for shared helper APIs
    /**
     * Asserts that the correct number of network requests were sent based on previously set expectations.
     *
     * @throws InterruptedException If the current thread is interrupted while waiting.
     * @see [setExpectationForNetworkRequest]
     */
    fun assertAllNetworkRequestExpectations() {
        helper.assertAllNetworkRequestExpectations()
    }

    /**
     * Returns the network request(s) sent through the Core NetworkService , or an empty list if none was found.
     *
     * Use this method after calling [setExpectationForNetworkRequest] to wait for expected requests.
     *
     * @param url The URL `String` of the [TestableNetworkRequest] to get.
     * @param method The [HttpMethod] of the [TestableNetworkRequest] to get.
     * @param timeoutMillis The duration (in milliseconds) to wait for the expected network requests before
     * timing out. Defaults to [TestConstants.Defaults.WAIT_NETWORK_REQUEST_TIMEOUT_MS].
     *
     * @return A list of [TestableNetworkRequest]s that match the provided [url] and [method]. Returns
     * an empty list if no matching requests were dispatched.
     *
     * @throws InterruptedException If the current thread is interrupted while waiting.
     *
     * @see setExpectationForNetworkRequest
     */
    @Throws(InterruptedException::class)
    @JvmOverloads
    fun getNetworkRequestsWith(url: String?,
                               method: HttpMethod?,
                               timeoutMillis: Int = TestConstants.Defaults.WAIT_NETWORK_REQUEST_TIMEOUT_MS
    ): List<TestableNetworkRequest?> {
        return helper.getNetworkRequestsWith(url, method, timeoutMillis)
    }

    fun reset() {
        helper.reset()
    }


    /**
     * Immediately returns the associated responses (if any) for the provided network request **without awaiting**.
     *
     * Note: To properly await network responses for a given request, make sure to set an expectation
     * using [setExpectationForNetworkRequest] then await the expectation using [assertAllNetworkRequestExpectations].
     *
     * @param networkRequest The [NetworkRequest] for which the response should be returned.
     * @return The list of [HttpConnecting] responses for the given request or `null` if not found.
     * @see [setExpectationForNetworkRequest]
     * @see [assertAllNetworkRequestExpectations]
     */
    fun getResponsesFor(networkRequest: NetworkRequest): List<HttpConnecting>? {
        return helper.getResponsesFor(TestableNetworkRequest(networkRequest))
    }

    /**
     * Sets the expected number of times a network request should be sent.
     *
     * @param url The URL `String` of the [TestableNetworkRequest] for which the expectation is set.
     * @param method The [HttpMethod] of the [TestableNetworkRequest] for which the expectation is set.
     * @param expectedCount The number of times the request is expected to be sent.
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

    /**
     * Sets the expected number of times a network request should be sent.
     *
     * @param networkRequest The [TestableNetworkRequest] for which the expectation is set.
     * @param expectedCount The number of times the request is expected to be sent.
     */
    fun setExpectationForNetworkRequest(
        networkRequest: TestableNetworkRequest,
        expectedCount: Int
    ) {
        setExpectationForNetworkRequest(networkRequest.url, networkRequest.method, expectedCount)
    }
}