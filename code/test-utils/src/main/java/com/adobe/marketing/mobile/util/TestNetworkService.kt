/*
  Copyright 2021 Adobe. All rights reserved.
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
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class TestNetworkService {
    private val sentTestableNetworkRequests: MutableMap<TestableNetworkRequest, MutableList<TestableNetworkRequest>>
    private val networkResponses: MutableMap<TestableNetworkRequest, HttpConnecting>
    private val expectedTestableNetworkRequests: MutableMap<TestableNetworkRequest, ADBCountDownLatch>

    companion object {
        private const val LOG_SOURCE = "TestNetworkService"
    }

    init {
        sentTestableNetworkRequests = HashMap()
        networkResponses = HashMap()
        expectedTestableNetworkRequests = HashMap()
    }

    fun reset() {
        Log.trace(
            TestConstants.LOG_TAG,
            LOG_SOURCE,
            "Reset received and expected network requests."
        )
        sentTestableNetworkRequests.clear()
        networkResponses.clear()
        expectedTestableNetworkRequests.clear()
    }

    fun setResponseConnectionFor(
        request: TestableNetworkRequest,
        responseConnection: HttpConnecting
    ) {
        networkResponses[request] = responseConnection
    }

    fun setExpectedNetworkRequest(request: TestableNetworkRequest, count: Int) {
        expectedTestableNetworkRequests[request] = ADBCountDownLatch(count)
    }

    fun getExpectedNetworkRequests(): Map<TestableNetworkRequest, ADBCountDownLatch> {
        return expectedTestableNetworkRequests
    }

    fun getReceivedNetworkRequestsMatching(request: TestableNetworkRequest): List<TestableNetworkRequest> {
        for ((key, value) in sentTestableNetworkRequests) {
            if (key == request) {
                return value
            }
        }
        return emptyList()
    }

    fun isNetworkRequestExpected(request: TestableNetworkRequest): Boolean {
        return expectedTestableNetworkRequests.containsKey(request)
    }

    @Throws(InterruptedException::class)
    fun awaitFor(request: TestableNetworkRequest, timeoutMillis: Int): Boolean {
        for ((key, value) in expectedTestableNetworkRequests) {
            if (key == request) {
                return value.await(timeoutMillis.toLong(), TimeUnit.MILLISECONDS)
            }
        }
        return true
    }

    fun recordSentNetworkRequest(networkRequest: TestableNetworkRequest) {
        Log.trace(TestConstants.LOG_TAG,
            LOG_SOURCE,
            "Received connectAsync to URL ${networkRequest.url} and HTTPMethod ${networkRequest.method}")

        val equalNetworkRequest = sentTestableNetworkRequests.entries.firstOrNull { (key, _) ->
            key == networkRequest
        }

        if (equalNetworkRequest != null) {
            sentTestableNetworkRequests[equalNetworkRequest.key]?.add(networkRequest)
        } else {
            sentTestableNetworkRequests[networkRequest] = mutableListOf(networkRequest)
        }
    }

    /**
     * Records a mock network request and retrieves a matched response.
     *
     * This function adds the provided [networkRequest] to the list of sent testable network requests.
     *
     * @param networkRequest The mock network request to be recorded.
     * @param defaultResponse The default response to be returned if no matched response is found.
     * @return The matched response for the provided [networkRequest] or the [defaultResponse] if none is found.
     */
    fun setMockNetworkRequest(networkRequest: TestableNetworkRequest, defaultResponse: HttpConnecting): HttpConnecting {
        if (!sentTestableNetworkRequests.containsKey(networkRequest)) {
            sentTestableNetworkRequests[networkRequest] = ArrayList()
        }
        sentTestableNetworkRequests[networkRequest]!!.add(networkRequest)
        val response = getMatchedResponse(networkRequest)
        countDownExpected(networkRequest)
        return response ?: defaultResponse
    }

    fun countDownExpected(request: TestableNetworkRequest) {
        for ((key, value) in expectedTestableNetworkRequests) {
            if (key == request) {
                value.countDown()
            }
        }
    }

    private fun getMatchedResponse(request: TestableNetworkRequest): HttpConnecting? {
        for ((key, value) in networkResponses) {
            if (key == request) {
                return value
            }
        }
        return null
    }
}