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
	private val networkResponses: MutableMap<TestableNetworkRequest, MutableList<HttpConnecting>>
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

	/**
	 * Adds a network response for the provided network request.
	 *
	 * @param request The [TestableNetworkRequest] for which the response is being set.
	 * @param responseConnection The [HttpConnecting] to set as a response.
	 */
	fun addResponseFor(
		request: TestableNetworkRequest,
		responseConnection: HttpConnecting
	) {
		if (networkResponses[request] != null) {
			networkResponses[request]?.add(responseConnection)
		} else {
			// If there's no response for this request yet, start a new list with the first response
			networkResponses[request] = mutableListOf(responseConnection)
		}
	}

	/**
	 * Sets the expected number of times a network request should be sent.
	 *
	 * @param request The [TestableNetworkRequest] representing the network request to set the expectation for.
	 * @param count The number of times the request is expected to be sent.
	 * @see [assertAllNetworkRequestExpectations]
	 */
	fun setExpectationForNetworkRequest(request: TestableNetworkRequest, count: Int) {
		expectedTestableNetworkRequests[request] = ADBCountDownLatch(count)
	}

	fun getExpectedNetworkRequests(): Map<TestableNetworkRequest, ADBCountDownLatch> {
		return expectedTestableNetworkRequests
	}

	/**
	 * Returns all sent network requests that match the provided network request.
	 *
	 * The matching relies on [TestableNetworkRequest.equals].
	 *
	 * @param request The [TestableNetworkRequest] for which to get matching requests.
	 *
	 * @return A list of [TestableNetworkRequest]s that match the provided [request]. If no matches are found, an empty list is returned.
	 */
	fun getSentNetworkRequestsMatching(request: TestableNetworkRequest): List<TestableNetworkRequest> {
		for ((key, value) in sentTestableNetworkRequests) {
			if (key == request) {
				return value
			}
		}
		return emptyList()
	}

	/**
	 * Returns the network request(s) sent through the Core `NetworkService`, returning an empty list if none were found.
	 *
	 * Use this method after calling [setExpectationForNetworkRequest] to await expected requests.
	 *
	 * @param url The URL `String` of the [TestableNetworkRequest] to get.
	 * @param method The HTTP method of the [TestableNetworkRequest] to get.
	 * @param timeoutMillis The duration (in milliseconds) to wait for the expected network requests.
	 *
	 * @return A list of [TestableNetworkRequest]s that match the provided [url] and [method]. Returns an empty list if
	 * no matching requests were dispatched.
	 *
	 * @throws InterruptedException If the current thread is interrupted while waiting.
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

	/**
	 * Starts the expectation timer for the given network request, validating that all expected responses are received
	 * within the provided timeout duration.
	 *
	 * @param request The [TestableNetworkRequest] for which the expectation timer should be started.
	 * @param timeoutMillis The maximum duration (in milliseconds) to wait for the expected responses before timing out.
	 *
	 * @return `true` if the expected responses are received within the timeout, or if the [TestableNetworkRequest] does not match any expected request. `false` otherwise.
	 *
	 * @throws InterruptedException If the current thread is interrupted while waiting.
	 */
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
	 * @throws InterruptedException If the current thread is interrupted while waiting.
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

	/**
	 * Decrements the expectation count for a given network request.
	 *
	 * @param request The [TestableNetworkRequest] for which the expectation count should be decremented.
	 */
	fun countDownExpected(request: TestableNetworkRequest) {
		for ((key, value) in expectedTestableNetworkRequests) {
			if (key == request) {
				value.countDown()
			}
		}
	}

	/**
	 * Returns the network responses associated with the given network request.
	 *
	 * @param request The [TestableNetworkRequest] for which the associated responses should be returned.
	 * @return The list of [HttpConnecting] responses for the given request or `null` if not found.
	 * @see [TestableNetworkRequest.equals]
	 */
	fun getResponsesFor(request: TestableNetworkRequest): List<HttpConnecting>? {
		for ((key, value) in networkResponses) {
			if (key == request) {
				return value
			}
		}
		return null
	}
}