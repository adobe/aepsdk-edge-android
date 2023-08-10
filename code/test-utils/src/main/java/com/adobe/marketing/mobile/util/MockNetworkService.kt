package com.adobe.marketing.mobile.util

import com.adobe.marketing.mobile.services.HttpConnecting
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.NetworkCallback
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.Networking
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import java.io.ByteArrayInputStream
import java.io.InputStream

class MockNetworkService: Networking {
    private val helper = TestNetworkService()

    companion object {
        private const val LOG_SOURCE = "MockNetworkService"
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

    override fun connectAsync(networkRequest: NetworkRequest, resultCallback: NetworkCallback) {
        Log.trace(
            TestConstants.LOG_TAG,
            LOG_SOURCE,
            "Received connectUrlAsync to URL '%s' and HttpMethod '%s'.",
            networkRequest.url,
            networkRequest.method.name
        )
        helper.executorService.submit {
            val response = helper.setNetworkRequest(
                TestableNetworkRequest(
                    networkRequest.url,
                    networkRequest.method,
                    networkRequest.body,
                    networkRequest.headers,
                    networkRequest.connectTimeout,
                    networkRequest.readTimeout
                )
            )
            if (resultCallback != null) {
                if (delayedResponse > 0) {
                    try {
                        Thread.sleep((delayedResponse * 1000).toLong())
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
                resultCallback.call(response ?: defaultResponse)
            }
        }
    }
}