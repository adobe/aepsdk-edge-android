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

import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.NetworkCallback
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.NetworkServiceHelper
import com.adobe.marketing.mobile.services.TestableNetworkRequest

internal class RealNetworkService: NetworkServiceHelper() {
    private val helper = TestNetworkService()
    companion object {
        private const val LOG_SOURCE = "RealNetworkService"
    }

    override fun connectAsync(networkRequest: NetworkRequest, resultCallback: NetworkCallback?) {
        val request = TestableNetworkRequest(networkRequest)
        helper.recordSentNetworkRequest(request)
        super.connectAsync(networkRequest) {
            helper.setResponseConnectionFor(request, it)
            helper.countDownExpected(request)

            // Call the original callback
            resultCallback?.call(it)
        }
    }

    // Passthrough for shared helper APIs
    fun assertAllNetworkRequestExpectations() {
        helper.assertAllNetworkRequestExpectations()
    }

    /**
     * Returns the [TestableNetworkRequest](s) sent through the
     * Core NetworkService, or empty if none was found. Use this API after calling
     * [.setExpectationNetworkRequest] to wait for each request.
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

    fun reset() {
        helper.reset()
    }

    /**
     * Set a network request expectation.
     * @param url the url string for which to set the expectation
     * @param method the HTTP method for which to set the expectation
     * @param expectedCount how many times a request with this `url` and `method` is expected to be sent
     */
    fun setExpectationForNetworkRequest(
        url: String?,
        method: HttpMethod?,
        expectedCount: Int
    ) {
        helper.setExpectedNetworkRequest(
            TestableNetworkRequest(
                url,
                method
            ), expectedCount
        )
    }
}