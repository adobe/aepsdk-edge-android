package com.adobe.marketing.mobile.util

import com.adobe.marketing.mobile.services.HttpConnecting
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class TestNetworkService {
    private val receivedTestableNetworkRequests: MutableMap<TestableNetworkRequest, MutableList<TestableNetworkRequest>>
    private val responseMatchers: MutableMap<TestableNetworkRequest, HttpConnecting>
    private val expectedTestableNetworkRequests: MutableMap<TestableNetworkRequest, ADBCountDownLatch>
    // Simulating the async network service
    val executorService: ExecutorService
    private var delayedResponse = 0

    companion object {
        private const val LOG_SOURCE = "TestNetworkService"
    }

    init {
        receivedTestableNetworkRequests = HashMap()
        responseMatchers = HashMap()
        expectedTestableNetworkRequests = HashMap()
        executorService = Executors.newCachedThreadPool()
    }

    fun reset() {
        Log.trace(
            TestConstants.LOG_TAG,
            LOG_SOURCE,
            "Reset received and expected network requests."
        )
        receivedTestableNetworkRequests.clear()
        responseMatchers.clear()
        expectedTestableNetworkRequests.clear()
        delayedResponse = 0
    }

    fun setResponseConnectionFor(
        request: TestableNetworkRequest,
        responseConnection: HttpConnecting
    ) {
        responseMatchers[request] = responseConnection
    }

    fun setExpectedNetworkRequest(request: TestableNetworkRequest, count: Int) {
        expectedTestableNetworkRequests[request] = ADBCountDownLatch(count)
    }

    fun getExpectedNetworkRequests(): Map<TestableNetworkRequest, ADBCountDownLatch> {
        return expectedTestableNetworkRequests
    }

    fun getReceivedNetworkRequestsMatching(request: TestableNetworkRequest): List<TestableNetworkRequest> {
        for ((key, value) in receivedTestableNetworkRequests) {
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

    fun enableDelayedResponse(delaySec: Int) {
        if (delaySec < 0) {
            return
        }
        delayedResponse = delaySec
    }

//    override fun connectAsync(networkRequest: NetworkRequest, resultCallback: NetworkCallback) {
//        Log.trace(
//            TestConstants.LOG_TAG,
//            LOG_SOURCE,
//            "Received connectUrlAsync to URL '%s' and HttpMethod '%s'.",
//            networkRequest.url,
//            networkRequest.method.name
//        )
//        executorService.submit {
//            val response = setNetworkRequest(
//                TestableNetworkRequest(
//                    networkRequest.url,
//                    networkRequest.method,
//                    networkRequest.body,
//                    networkRequest.headers,
//                    networkRequest.connectTimeout,
//                    networkRequest.readTimeout
//                )
//            )
//            if (resultCallback != null) {
//                if (delayedResponse > 0) {
//                    try {
//                        Thread.sleep((delayedResponse * 1000).toLong())
//                    } catch (e: InterruptedException) {
//                        e.printStackTrace()
//                    }
//                }
//                resultCallback.call(response ?: defaultResponse)
//            }
//        }
//    }

    /**
     * Add the network request to the list of received requests. Returns the matching response, or
     * the default response if no matching response was found.
     */
    fun setNetworkRequest(networkRequest: TestableNetworkRequest): HttpConnecting {
        if (!receivedTestableNetworkRequests.containsKey(networkRequest)) {
            receivedTestableNetworkRequests[networkRequest] = ArrayList()
        }
        receivedTestableNetworkRequests[networkRequest]!!.add(networkRequest)
        val response = getMatchedResponse(networkRequest)
        countDownExpected(networkRequest)
        return response ?: defaultResponse
    }

    private fun countDownExpected(request: TestableNetworkRequest) {
        for ((key, value) in expectedTestableNetworkRequests) {
            if (key == request) {
                value.countDown()
            }
        }
    }

    private fun getMatchedResponse(request: TestableNetworkRequest): HttpConnecting? {
        for ((key, value) in responseMatchers) {
            if (key == request) {
                return value
            }
        }
        return null
    }
}