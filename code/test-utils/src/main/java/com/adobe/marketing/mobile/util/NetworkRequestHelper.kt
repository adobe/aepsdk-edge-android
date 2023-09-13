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
import com.adobe.marketing.mobile.services.HttpMethod
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.mobile.services.TestableNetworkRequest
import org.junit.Assert
import java.util.concurrent.TimeUnit

/**
 * Provides shared utilities and logic for implementations of `Networking` classes used for testing.
 *
 * @see [MockNetworkService]
 * @see [RealNetworkService]
 */
class NetworkRequestHelper {
	private val sentTestableNetworkRequests: MutableMap<TestableNetworkRequest, MutableList<TestableNetworkRequest>>
	private val networkResponses: MutableMap<TestableNetworkRequest, HttpConnecting>
	private val expectedTestableNetworkRequests: MutableMap<TestableNetworkRequest, ADBCountDownLatch>

	companion object {
		private const val LOG_SOURCE = "NetworkRequestHelper"
	}

	init {
		sentTestableNetworkRequests = HashMap()
		networkResponses = HashMap()
		expectedTestableNetworkRequests = HashMap()
	}

	/**
	 * Resets the helper state by clearing all test expectations and stored data.
	 */
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

	/**
	 * Sets an expectation for a network request's send count.
	 *
	 * @param request The [TestableNetworkRequest] instance representing the network request for which the expectation is being set.
	 * @param count The number of times the provided network request is expected to be sent.
	 * @see [assertAllNetworkRequestExpectations]
	 */
	fun setExpectationForNetworkRequest(request: TestableNetworkRequest, count: Int) {
		expectedTestableNetworkRequests[request] = ADBCountDownLatch(count)
	}

	fun getExpectedNetworkRequests(): Map<TestableNetworkRequest, ADBCountDownLatch> {
		return expectedTestableNetworkRequests
	}

	fun getSentNetworkRequestsMatching(request: TestableNetworkRequest): List<TestableNetworkRequest> {
		for ((key, value) in sentTestableNetworkRequests) {
			if (key == request) {
				return value
			}
		}
		return emptyList()
	}

	/**
	 * Returns the [TestableNetworkRequest](s) sent through the Core NetworkService, or empty if
	 * none was found. Use this API after calling [setExpectationForNetworkRequest] to wait for each request.
	 *
	 * @param url The url string for which to retrieved the network requests sent
	 * @param method the HTTP method for which to retrieve the network requests
	 * @param timeoutMillis how long should this method wait for the expected network requests, in milliseconds
	 * @return list of network requests with the provided `url` and `command`, or empty if none was dispatched
	 * @throws InterruptedException
	 */
	@Throws(InterruptedException::class)
	fun getNetworkRequestsWith(
		url: String?,
		method: HttpMethod?,
		timeoutMillis: Int
	): List<TestableNetworkRequest?> {
		val networkRequest = TestableNetworkRequest(url, method)
		if (isNetworkRequestExpected(networkRequest)) {
			Assert.assertTrue(
		"Time out waiting for network request(s) with URL '" +
				networkRequest.url +
				"' and method '" +
				networkRequest.method.name +
				"'",
				awaitFor(networkRequest, timeoutMillis)
			)
		} else {
			TestHelper.sleep(timeoutMillis)
		}
		return getSentNetworkRequestsMatching(networkRequest)
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

	/**
	 * Asserts that the correct number of network requests were sent based on previously set expectations.
	 *
	 * @throws InterruptedException
	 * @see [setExpectationForNetworkRequest]
	 */
	@Throws(InterruptedException::class)
	fun assertAllNetworkRequestExpectations() {
		TestHelper.waitForThreads(2000) // allow for some extra time for threads to finish before asserts
		val expectedNetworkRequests: Map<TestableNetworkRequest, ADBCountDownLatch> =
		getExpectedNetworkRequests()
		if (expectedNetworkRequests.isEmpty()) {
			Assert.fail(
				"There are no network request expectations set, use this API after calling setExpectationForNetworkRequest"
			)
			return
		}
		for ((key, value) in expectedNetworkRequests) {
			val awaitResult = value.await(5, TimeUnit.SECONDS)
			Assert.assertTrue(
		"Time out waiting for network request with URL '" +
				key.url +
				"' and method '" +
				key.method.name +
				"'",
				awaitResult
			)
			val expectedCount = value.initialCount
			val receivedCount = value.currentCount
			val message = String.format(
				"Expected %d network requests for URL %s (%s), but received %d",
				expectedCount,
				key.url,
				key.method,
				receivedCount
			)
			Assert.assertEquals(message, expectedCount.toLong(), receivedCount.toLong())
		}
	}

	fun recordSentNetworkRequest(networkRequest: TestableNetworkRequest) {
		Log.trace(
			TestConstants.LOG_TAG,
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

	fun countDownExpected(request: TestableNetworkRequest) {
		for ((key, value) in expectedTestableNetworkRequests) {
			if (key == request) {
				value.countDown()
			}
		}
	}

	/**
	 * Returns the associated [HttpConnecting] response for a given [TestableNetworkRequest].
	 *
	 * @param request The [TestableNetworkRequest] whose [HttpConnecting] response should be returned.
	 * @return The [HttpConnecting] response for the given request or `null` if not found.
	 */
	fun getResponseFor(request: TestableNetworkRequest): HttpConnecting? {
		for ((key, value) in networkResponses) {
			if (key == request) {
				return value
			}
		}
		return null
	}
}