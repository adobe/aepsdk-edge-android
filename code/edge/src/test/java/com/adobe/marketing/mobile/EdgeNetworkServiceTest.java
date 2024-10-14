/*
  Copyright 2019 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.adobe.marketing.mobile.services.HttpMethod;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.NetworkingConstants;
import com.adobe.marketing.mobile.util.MockConnection;
import com.adobe.marketing.mobile.util.MockNetworkService;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EdgeNetworkServiceTest {

	private static final int DEFAULT_TIMEOUT = 5;
	private static final String RECORD_SEPARATOR = "\u0000";
	private static final String ERROR_TYPE = "type";
	private static final String ERROR_TITLE = "title";
	private static final String DEFAULT_ERROR_NAMESPACE = "global";
	private static final String DEFAULT_ERROR_MESSAGE = "Request to Edge Network failed with an unknown exception";
	private static final String TEST_URL = "https://test.com";
	private static final String HEADER_CONTENT_TYPE = "Content-Type";
	private static final String HEADER_CONTENT_TYPE_JSON_VALUE = "application/json";
	private static final String HEADER_RETRY_AFTER = "Retry-After";
	private final Map<String, String> requestHeaders = new HashMap<String, String>() {
		{
			put("key1", "value1");
			put("key2", "value2");
		}
	};

	/** This class encapsulates the response for {@link EdgeNetworkService#doRequest(String, String, Map, EdgeNetworkService.ResponseCallback)}
	 * API, and it can be used with tests running in parallel.
	 */
	private class DoRequestResult {

		final String[] onResponseCallback = new String[2];
		final String[] onErrorCallback = new String[2];
		final String[] onCompleteCallback = new String[1];
		RetryResult retryResult;

		DoRequestResult() {}
	}

	/** This class encapsulates the response for {@link EdgeNetworkService#handleStreamingResponse(InputStream, String, String, EdgeNetworkService.ResponseCallback)}
	 * API, and it can be used with tests running in parallel.
	 */
	private class StreamingResponseResult {

		final List<String> onResponseCallback = new ArrayList<>();

		StreamingResponseResult() {}
	}

	/** This class encapsulates the response for {@link EdgeNetworkService#handleNonStreamingResponse(InputStream, EdgeNetworkService.ResponseCallback)}
	 * API, and it can be used with tests running in parallel.
	 */
	private class NonStreamingResponseResult {

		String onResponseCallback = null;

		NonStreamingResponseResult() {}
	}

	MockNetworkService mockNetworkService;
	EdgeNetworkService networkService;
	CountDownLatch latchOfOne;
	CountDownLatch latchOfTwo;
	CountDownLatch latchOfThree;

	static Map<String, String> DEFAULT_RESPONSE_HEADERS = new HashMap<String, String>() {
		{
			put(HEADER_CONTENT_TYPE, HEADER_CONTENT_TYPE_JSON_VALUE);
		}
	};

	@Before
	public void setup() {
		latchOfOne = new CountDownLatch(1);
		latchOfTwo = new CountDownLatch(2);
		latchOfThree = new CountDownLatch(3);
		mockNetworkService = new MockNetworkService();
	}

	@Test
	public void testEdgeNetworkServiceConstructor_whenNullNetworkService_throws() {
		// setup
		Exception thrownException = null;

		// test
		try {
			new EdgeNetworkService(null);
		} catch (Exception e) {
			thrownException = e;
		}

		// verify
		assertNotNull(thrownException);
		assertTrue(thrownException instanceof IllegalArgumentException);
		assertEquals("NetworkService cannot be null.", thrownException.getMessage());
	}

	@Test
	public void testDoRequest_whenUrlNull_ReturnsRetryNo() {
		// setup
		String url = null;
		String jsonRequest = "{}";
		Map<String, String> requestProperty = new HashMap<String, String>();
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		RetryResult retryResult = networkService.doRequest(
			url,
			jsonRequest,
			requestProperty,
			new EdgeNetworkService.ResponseCallback() {
				@Override
				public void onResponse(String jsonResponse) {}

				@Override
				public void onError(String jsonError) {}

				@Override
				public void onComplete() {}
			}
		);

		// verify
		assertEquals(EdgeNetworkService.Retry.NO, retryResult.getShouldRetry());
		assertEquals(0, mockNetworkService.getConnectAsyncCallCount());
	}

	@Test
	public void testDoRequest_whenNullConnection_ReturnsRetryYes() {
		// setup
		final String jsonRequest = "{}";
		final Map<String, String> requestProperty = new HashMap<String, String>();
		mockNetworkService.setDefaultResponse(null);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		RetryResult retryResult = networkService.doRequest(
			TEST_URL,
			jsonRequest,
			requestProperty,
			new EdgeNetworkService.ResponseCallback() {
				@Override
				public void onResponse(String jsonResponse) {}

				@Override
				public void onError(String jsonError) {}

				@Override
				public void onComplete() {}
			}
		);

		// verify
		assertEquals(1, mockNetworkService.getConnectAsyncCallCount());
		assertEquals(EdgeNetworkService.Retry.YES, retryResult.getShouldRetry());
	}

	@Test
	public void test_doRequest_whenRequestHeadersAreNull_setsDefaultHeaders() {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		final Map<String, String> expectedHeaders = new HashMap<>();
		expectedHeaders.put(
			EdgeConstants.NetworkKeys.HEADER_KEY_ACCEPT,
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON
		);
		expectedHeaders.put(
			EdgeConstants.NetworkKeys.HEADER_KEY_CONTENT_TYPE,
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON
		);

		// test
		networkService = new EdgeNetworkService(mockNetworkService);
		networkService.doRequest(url, jsonRequest, null, null);

		// verify
		assertNetworkRequestsEqual(
			new NetworkRequest(
				url,
				HttpMethod.POST,
				jsonRequest.getBytes(),
				expectedHeaders,
				DEFAULT_TIMEOUT,
				DEFAULT_TIMEOUT
			),
			mockNetworkService.getAllNetworkRequests(2000).get(0)
		);
	}

	@Test
	public void test_doRequest_whenRequestHeadersExist_RequestHeadersAppendedOnNetworkCall() {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		final Map<String, String> expectedHeaders = new HashMap<>();
		expectedHeaders.put(
			EdgeConstants.NetworkKeys.HEADER_KEY_ACCEPT,
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON
		);
		expectedHeaders.put(
			EdgeConstants.NetworkKeys.HEADER_KEY_CONTENT_TYPE,
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON
		);
		expectedHeaders.putAll(requestHeaders);

		// test
		networkService = new EdgeNetworkService(mockNetworkService);
		RetryResult retryResult = networkService.doRequest(
			url,
			jsonRequest,
			requestHeaders,
			new EdgeNetworkService.ResponseCallback() {
				@Override
				public void onResponse(String jsonResponse) {}

				@Override
				public void onError(String jsonError) {}

				@Override
				public void onComplete() {}
			}
		);

		// verify
		assertNetworkRequestsEqual(
			new NetworkRequest(
				url,
				HttpMethod.POST,
				jsonRequest.getBytes(),
				expectedHeaders,
				DEFAULT_TIMEOUT,
				DEFAULT_TIMEOUT
			),
			mockNetworkService.getAllNetworkRequests(2000).get(0)
		);
	}

	@Test
	public void testDoRequest_whenConnection_ResponseCode200_ReturnsRetryNo_AndCallsResponseCallback_AndNoErrorCallback() {
		// setup
		final String jsonRequest = "{}";
		final String responseStr = "{\"key\":\"value\"}";
		MockConnection mockConnection = new MockConnection(200, responseStr, null);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(TEST_URL, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.NO, result.retryResult.getShouldRetry());
		assertEquals("called", result.onResponseCallback[0]);
		assertEquals(responseStr, result.onResponseCallback[1]);
		assertNull(result.onErrorCallback[0]);
		assertEquals(1, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenConnection_ResponseCode204_ReturnsRetryNo_AndNoResponseCallback_AndNoErrorCallback() {
		// setup
		final String jsonRequest = "{}";
		final String responseStr = "OK";
		MockConnection mockConnection = new MockConnection(204, responseStr, null);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(TEST_URL, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.NO, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNull(result.onErrorCallback[0]);
		assertEquals(0, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenConnection_502ResponseCode_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		final String errorStr = "Service Unavailable";
		MockConnection mockConnection = new MockConnection(502, null, errorStr);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.YES, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNull(result.onErrorCallback[0]);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCode_negative1_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		testRecoverableNetworkResponse(-1, null);
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCode_408_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		testRecoverableNetworkResponse(408, "Request Timeout");
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCode_429_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		testRecoverableNetworkResponse(429, "Too Many Requests");
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCode_502_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		testRecoverableNetworkResponse(502, "Bad Gateway");
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCode_503_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		testRecoverableNetworkResponse(503, "Service Unavailable");
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCode_504_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		testRecoverableNetworkResponse(504, "Gateway Timeout");
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCode_507_ReturnsRetryYes_AndNoResponseCallback_AndNoErrorCallback() {
		testRecoverableNetworkResponse(507, "Gateway Timeout");
	}

	@Test
	public void testDoRequest_whenConnection_RecoverableResponseCodeAndRetryAfter_ReturnsRetryTimeout() {
		Set<Integer> recoverableNetworkErrorCodes = new HashSet<>();
		recoverableNetworkErrorCodes.addAll(NetworkingConstants.RECOVERABLE_ERROR_CODES);
		recoverableNetworkErrorCodes.addAll(Arrays.asList(429, 502, 507, -1));

		for (int responseCode : recoverableNetworkErrorCodes) {
			testRecoverableWithRetryAfter(responseCode, "30", 30);
		}
	}

	@Test
	public void testDoRequest_whenConnection_InvalidRetryAfter_ReturnsDefaultRetryTimeout() {
		testRecoverableNetworkResponse(507, "Gateway Timeout");
		String[] invalidRetryAfter = {"InvalidRetryAfter", "A", "", "-1", "0", "     "};

		for (String retryAfter : invalidRetryAfter) {
			testRecoverableWithRetryAfter(503, retryAfter, 5); // expecting default timeout
		}
	}

	@Test
	public void testDoRequest_whenConnection_ValidRetryAfter_ReturnsCorrectRetryTimeout() {
		testRecoverableNetworkResponse(507, "Gateway Timeout");
		String[] invalidRetryAfter = {"1", "5", "30", "60", "180", "300"};

		for (String retryAfter : invalidRetryAfter) {
			testRecoverableWithRetryAfter(503, retryAfter, Integer.parseInt(retryAfter)); // expecting provided timeout
		}
	}

	private void testRecoverableWithRetryAfter(final int responseCode, final String retryAfter, final int expectedRetryAfter) {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		final Map<String, String> headers = new HashMap<>();
		headers.put(HEADER_RETRY_AFTER, retryAfter);
		MockConnection mockConnection = new MockConnection(responseCode, null, "error", headers);
		mockNetworkService.mockConnectAsyncConnection = mockConnection;
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.YES, result.retryResult.getShouldRetry());
		assertEquals(expectedRetryAfter, result.retryResult.getRetryIntervalSeconds());
	}

	private void testRecoverableNetworkResponse(final int responseCode, final String errorString) {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		MockConnection mockConnection = new MockConnection(responseCode, null, errorString, null);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.YES, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNull(result.onErrorCallback[0]);
		assertEquals(0, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenRequestProcessed_CallsOnComplete() throws Exception {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		MockConnection mockConnection = new MockConnection(200, "{}", null);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify

		assertEquals(EdgeNetworkService.Retry.NO, result.retryResult.getShouldRetry());
		assertNotNull(result.onResponseCallback[0]);
		assertNotNull(result.onCompleteCallback[0]);
		assertEquals(1, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenRequestNotProcessed_NoCallOnComplete() throws Exception {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		final String errorStr = "Service Unavailable";
		mockNetworkService.setDefaultResponse(new MockConnection(503, null, errorStr, null));
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify

		assertEquals(EdgeNetworkService.Retry.YES, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNull(result.onCompleteCallback[0]);
	}

	@Test
	public void testDoRequest_whenConnection_UnrecoverableResponseCode_WhenContentTypeJson_WithInvalidJsonContent() {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		final String errorStr = "Internal server error";
		MockConnection mockConnection = new MockConnection(500, null, errorStr, null);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.NO, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNotNull(result.onErrorCallback[0]);
		assertGenericJsonError(errorStr, DEFAULT_ERROR_NAMESPACE, result.onErrorCallback[1]);
		assertEquals(0, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenConnection_UnrecoverableResponseCode_WhenContentTypeJson_WithNullError_ShouldReturnGenericError() {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		MockConnection mockConnection = new MockConnection(500, null, null);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.NO, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNotNull(result.onErrorCallback[0]);
		assertGenericJsonError(DEFAULT_ERROR_MESSAGE, DEFAULT_ERROR_NAMESPACE, result.onErrorCallback[1]);
		assertEquals(0, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.getErrorStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenConnection_UnrecoverableResponseCode_WhenContentTypeJson_WithEmptyError_ShouldReturnGenericError() {
		// setup
		final String url = "https://test.com";
		final String jsonRequest = "{}";
		MockConnection mockConnection = new MockConnection(500, null, "");
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(url, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.NO, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNotNull(result.onErrorCallback[0]);
		assertGenericJsonError(DEFAULT_ERROR_MESSAGE, DEFAULT_ERROR_NAMESPACE, result.onErrorCallback[1]);
		assertEquals(0, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.getErrorStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testDoRequest_whenConnection_UnrecoverableResponseCode_WhenContentTypeJson_WithValidJsonContent() {
		// setup
		final String jsonRequest = "{}";
		final String errorStr =
			"{\n" +
			"      \"requestId\": \"d81c93e5-7558-4996-a93c-489d550748b8\",\n" +
			"      \"handle\": [],\n" +
			"      \"errors\": [\n" +
			"        {\n" +
			"          \"code\": \"global:0\",\n" +
			"          \"namespace\": \"global\",\n" +
			"          \"severity\": \"0\",\n" +
			"          \"message\": \"Failed due to unrecoverable system error: java.lang.IllegalStateException: Expected BEGIN_ARRAY but was BEGIN_OBJECT at path $.commerce.purchases\"\n" +
			"        }\n" +
			"      ]\n" +
			"    }";

		MockConnection mockConnection = new MockConnection(500, null, errorStr);
		mockNetworkService.setDefaultResponse(mockConnection);
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		DoRequestResult result = doRequestSync(TEST_URL, jsonRequest);

		// verify
		assertEquals(EdgeNetworkService.Retry.NO, result.retryResult.getShouldRetry());
		assertNull(result.onResponseCallback[0]);
		assertNotNull(result.onErrorCallback[0]);
		assertEquals(errorStr, result.onErrorCallback[1]);
		assertEquals(0, mockConnection.getInputStreamCalledTimes);
		assertEquals(1, mockConnection.getErrorStreamCalledTimes);
		assertEquals(1, mockConnection.closeCalledTimes);
	}

	@Test
	public void testHandleStreamingResponse_NullParams() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = null;
		final String lineFeed = null;
		final String responseStr = "";

		ArrayList<String> expectedResponses = new ArrayList<>();

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_EmptyResponse() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = "";
		final String lineFeed = "\n";
		final String responseStr = "{}";

		ArrayList<String> expectedResponses = new ArrayList<>();
		expectedResponses.add("{}");

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_SimpleStreamingResponse() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = "<RS>";
		final String lineFeed = "<LF>";
		final String responseStr =
			"<RS>{\"some\":\"thing\\n\"}<LF>" +
			"<RS>{\n" +
			"  \"may\": {\n" +
			"    \"include\": \"nested\",\n" +
			"    \"objects\": [\n" +
			"      \"and\",\n" +
			"      \"arrays\"\n" +
			"    ]\n" +
			"  }\n" +
			"}<LF>";

		ArrayList<String> expectedResponses = new ArrayList<>();
		expectedResponses.add("{\"some\":\"thing\\n\"}");
		expectedResponses.add(
			"{\n" +
			"  \"may\": {\n" +
			"    \"include\": \"nested\",\n" +
			"    \"objects\": [\n" +
			"      \"and\",\n" +
			"      \"arrays\"\n" +
			"    ]\n" +
			"  }\n" +
			"}"
		);

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_SimpleStreamingResponse2() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = "\u00A9";
		final String lineFeed = "\u00F8";
		final String responseStr =
			"\u00A9{\"some\":\"thing\\n\"}\u00F8" +
			"\u00A9{\n" +
			"  \"may\": {\n" +
			"    \"include\": \"nested\",\n" +
			"    \"objects\": [\n" +
			"      \"and\",\n" +
			"      \"arrays\"\n" +
			"    ]\n" +
			"  }\n" +
			"}\u00F8";

		ArrayList<String> expectedResponses = new ArrayList<>();
		expectedResponses.add("{\"some\":\"thing\\n\"}");
		expectedResponses.add(
			"{\n" +
			"  \"may\": {\n" +
			"    \"include\": \"nested\",\n" +
			"    \"objects\": [\n" +
			"      \"and\",\n" +
			"      \"arrays\"\n" +
			"    ]\n" +
			"  }\n" +
			"}"
		);

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_SimpleStreamingResponse3() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = "\u00A9";
		final String lineFeed = "\u00FF";
		final String responseStr =
			"\u00A9{\"some\":\"thing\\n\"}\u00FF" +
			"\u00A9{\n" +
			"  \"may\": {\n" +
			"    \"include\": \"nested\",\n" +
			"    \"objects\": [\n" +
			"      \"and\",\n" +
			"      \"arrays\"\n" +
			"    ]\n" +
			"  }\n" +
			"}\u00FF";

		ArrayList<String> expectedResponses = new ArrayList<>();
		expectedResponses.add("{\"some\":\"thing\\n\"}");
		expectedResponses.add(
			"{\n" +
			"  \"may\": {\n" +
			"    \"include\": \"nested\",\n" +
			"    \"objects\": [\n" +
			"      \"and\",\n" +
			"      \"arrays\"\n" +
			"    ]\n" +
			"  }\n" +
			"}"
		);

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_catchesIndexOutOfBoundsException() {
		// When processing a response, the response is parsed into chunks using the line feed delimiter.
		// Each chunk then removes the record separator to produce the expected response.
		// If a chunk length is less than the record separator length, an IndexOutOfBounds exception is thrown.
		// This test verifies an IndexOutOfBounds exception is caught and handled.

		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = "<RS>";
		final String lineFeed = "<LF>";
		final String responseStr =
			"<RS>{\"some\":\"thing1\\n\"}<LF>" +
			"<RS>{\"some\":\"thing2\\n\"}<LF>" +
			"<RS>{\"some\":\"thing3\\n\"}<LF>."; // note the trailing '.' character

		ArrayList<String> expectedResponses = new ArrayList<>();
		expectedResponses.add("{\"some\":\"thing1\\n\"}");
		expectedResponses.add("{\"some\":\"thing2\\n\"}");
		expectedResponses.add("{\"some\":\"thing3\\n\"}");

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_SingleStreamingResponse() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = "\u0000";
		final String lineFeed = "\n";
		final String responseStr =
			"\u0000{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}\n";

		ArrayList<String> expectedResponses = new ArrayList<>();
		expectedResponses.add(
			"{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}"
		);

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_TwoStreamingResponses() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String recordSeparator = "\u0000";
		final String lineFeed = "\n";
		String responseStr =
			"\u0000{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}\n";
		responseStr += responseStr;

		ArrayList<String> expectedResponses = new ArrayList<>();
		expectedResponses.add(
			"{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}"
		);
		expectedResponses.add(expectedResponses.get(0));

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleStreamingResponse_ManyStreamingResponses() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		int responseCount = 20;
		final String recordSeparator = "\u0000";
		final String lineFeed = "\n";
		String responseStr =
			"\u0000{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}\n";
		responseStr = String.join("", Collections.nCopies(responseCount, responseStr));

		String expectedResponse =
			"{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}";
		List<String> expectedResponses = Collections.nCopies(responseCount, expectedResponse);

		// test
		StreamingResponseResult result = DoStreamingResponse(recordSeparator, lineFeed, responseStr);

		// verify
		assertEquals(expectedResponses, result.onResponseCallback);
	}

	@Test
	public void testHandleNonStreamingResponse_WhenValidJson_ShouldReturnEntireResponse() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		String responseStr =
			"{\"some\":\"thing\"}," +
			"{" +
			"  \"may\": {" +
			"    \"include\": \"nested\"," +
			"    \"objects\": [" +
			"      \"and\"," +
			"      \"arrays\"" +
			"    ]" +
			"  }" +
			"}";
		responseStr = responseStr.replaceAll("\\s+", "");

		// test
		NonStreamingResponseResult result = DoNonStreamingResponse(responseStr);

		// verify
		assertEquals(responseStr, result.onResponseCallback);
	}

	@Test
	public void testHandleNonStreamingResponse_WhenValidJsonWithNewLine_ShouldReturnResponse() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		String responseStr =
			"{\"some\":\"thing\"},\n" +
			"{" +
			"  \"may\": {" +
			"    \"include\": \"nested\"," +
			"    \"objects\": [" +
			"      \"and\"," +
			"      \"arrays\"" +
			"    ]" +
			"  }" +
			"}\n";
		responseStr = responseStr.replaceAll("\\s+", "");

		// test
		NonStreamingResponseResult result = DoNonStreamingResponse(responseStr);

		// verify
		assertEquals(responseStr, result.onResponseCallback);
	}

	@Test
	public void testHandleNonStreamingResponse_WhenOneJsonObject_ShouldReturnEntireResponse() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		final String responseStr =
			"{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}";

		// test
		NonStreamingResponseResult result = DoNonStreamingResponse(responseStr);

		// verify
		assertEquals(responseStr, result.onResponseCallback);
	}

	@Test
	public void testHandleNonStreamingResponse_WhenTwoJsonObjects_ShouldReturnEntireResponse() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		String responseStr =
			"{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":358,\"spec\":{\"url\":\"//ib.adnxs.com/getuid?https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D358%26dpuuid%3D%24UID\",\"hideReferrer\":true,\"ttlMinutes\":10080}},{\"type\":\"url\",\"id\":477,\"spec\":{\"url\":\"//idsync.rlcdn.com/365868.gif?partner_uid=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":14400}},{\"type\":\"url\",\"id\":540,\"spec\":{\"url\":\"//pixel.tapad.com/idsync/ex/receive?partner_id=ADB&partner_url=https%3A%2F%2Fdpm.demdex.net%2Fibs%3Adpid%3D540%26dpuuid%3D%24%7BTA_DEVICE_ID%7D&partner_device_id=42985602780892980519057012517360930936\",\"hideReferrer\":true,\"ttlMinutes\":1440}},{\"type\":\"url\",\"id\":771,\"spec\":{\"url\":\"https://cm.g.doubleclick.net/pixel?google_nid=adobe_dmp&google_cm&gdpr=0&gdpr_consent=\",\"hideReferrer\":true,\"ttlMinutes\":20160}},{\"type\":\"url\",\"id\":1123,\"spec\":{\"url\":\"//analytics.twitter.com/i/adsct?p_user_id=42985602780892980519057012517360930936&p_id=38594\",\"hideReferrer\":true,\"ttlMinutes\":10080}}]}]}";
		responseStr += responseStr;

		// test
		NonStreamingResponseResult result = DoNonStreamingResponse(responseStr);

		// verify
		assertEquals(responseStr, result.onResponseCallback);
	}

	@Test
	public void testHandleNonStreamingResponse_WhenResponseIsEmptyObject_ShouldReturnEmptyObject() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		String responseStr = "{}";
		responseStr += responseStr;

		// test
		NonStreamingResponseResult result = DoNonStreamingResponse(responseStr);

		// verify
		assertEquals(responseStr, result.onResponseCallback);
	}

	@Test
	public void testHandleNonStreamingResponse_WhenResponseIsEmptyString_ShouldReturnEmptyString() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);
		String responseStr = "";
		responseStr += responseStr;

		// test
		NonStreamingResponseResult result = DoNonStreamingResponse(responseStr);

		// verify
		assertEquals("", result.onResponseCallback);
	}

	@Test
	public void testHandleNonStreamingResponse_WhenResponseIsNull_ShouldNotThrowException() {
		// setup
		networkService = new EdgeNetworkService(mockNetworkService);

		// test
		NonStreamingResponseResult result = DoNonStreamingResponse(null);

		// verify
		assertNull(result.onResponseCallback);
	}

	/**
	 * Executes a call to {@link EdgeNetworkService#doRequest(String, String, Map, EdgeNetworkService.ResponseCallback)}
	 * and waits for up to 200ms to check if the onResponse / onError callbacks get called
	 * @param url as {@link String} to send the betwork request to
	 * @param body JSON represented as {@link String}
	 * @return {@link DoRequestResult} containing the retry result, onResponse callback if called and the value,
	 * onError callback if called and the value.
	 */
	private DoRequestResult doRequestSync(final String url, final String body) {
		final DoRequestResult result = new DoRequestResult();
		final Map<String, String> requestProperty = new HashMap<>();
		result.retryResult =
			networkService.doRequest(
				url,
				body,
				requestProperty,
				new EdgeNetworkService.ResponseCallback() {
					@Override
					public void onResponse(final String jsonResponse) {
						result.onResponseCallback[0] = "called";
						result.onResponseCallback[1] = jsonResponse;
						latchOfThree.countDown();
					}

					@Override
					public void onError(String jsonError) {
						result.onErrorCallback[0] = "called";
						result.onErrorCallback[1] = jsonError;
						latchOfThree.countDown();
					}

					@Override
					public void onComplete() {
						result.onCompleteCallback[0] = "called";
						latchOfThree.countDown();
					}
				}
			);

		try {
			latchOfThree.await(300, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {} //nothing

		return result;
	}

	/**
	 * Executes a call to {@link EdgeNetworkService#handleStreamingResponse(InputStream, String, String, EdgeNetworkService.ResponseCallback)}
	 * and waits for up to 200ms to check if the onResponse callback to get called
	 * @param recordSeparator a {@link String} used as the record separator for streamed responses
	 * @param lineFeed the line feed delimiter used for streamed responses {@link String}
	 * @return {@link StreamingResponseResult} containing the onResponse callback containing the responses
	 */
	private StreamingResponseResult DoStreamingResponse(
		final String recordSeparator,
		final String lineFeed,
		final String responseStr
	) {
		final StreamingResponseResult result = new StreamingResponseResult();

		InputStream inputStream = new ByteArrayInputStream(responseStr.getBytes());
		networkService.handleStreamingResponse(
			inputStream,
			recordSeparator,
			lineFeed,
			new EdgeNetworkService.ResponseCallback() {
				@Override
				public void onResponse(final String jsonResponse) {
					result.onResponseCallback.add(jsonResponse);
					latchOfTwo.countDown();
				}

				@Override
				public void onError(String jsonError) {}

				@Override
				public void onComplete() {}
			}
		);

		try {
			latchOfTwo.await(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// nothing
		}

		return result;
	}

	/**
	 * Executes a call to {@link EdgeNetworkService#handleNonStreamingResponse(InputStream, EdgeNetworkService.ResponseCallback)}
	 * and waits for up to 200ms to check if the onResponse callback to get called
	 * @return {@link StreamingResponseResult} containing the onResponse callback containing the responses
	 */
	private NonStreamingResponseResult DoNonStreamingResponse(final String responseStr) {
		final NonStreamingResponseResult result = new NonStreamingResponseResult();

		InputStream inputStream = responseStr == null ? null : new ByteArrayInputStream(responseStr.getBytes());
		networkService.handleNonStreamingResponse(
			inputStream,
			new EdgeNetworkService.ResponseCallback() {
				@Override
				public void onResponse(final String jsonResponse) {
					result.onResponseCallback = jsonResponse;
					latchOfOne.countDown();
				}

				@Override
				public void onError(String jsonError) {}

				@Override
				public void onComplete() {}
			}
		);

		try {
			latchOfOne.await(200, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			// nothing
		}

		return result;
	}

	private void assertGenericJsonError(
		final String expectedErrorMesage,
		final String expectedErrorNamespace,
		final String actualErrorJson
	) {
		if (actualErrorJson == null) {
			return;
		}

		try {
			JSONObject jsonError = new JSONObject(actualErrorJson);
			assertEquals(expectedErrorNamespace, jsonError.getString(ERROR_TYPE));
			assertEquals(expectedErrorMesage, jsonError.getString(ERROR_TITLE));
		} catch (JSONException e) {
			e.printStackTrace();
			fail("Error message has incorrect format, expected json object, found " + actualErrorJson);
		}
	}

	private void assertNetworkRequestsEqual(final NetworkRequest expected, final NetworkRequest actual) {
		if (expected == null || actual == null) {
			return;
		}

		assertEquals(expected.getUrl(), actual.getUrl());
		assertArrayEquals(expected.getBody(), actual.getBody());
		assertEquals(expected.getMethod(), actual.getMethod());
		assertEquals(expected.getReadTimeout(), actual.getReadTimeout());
		assertEquals(expected.getConnectTimeout(), actual.getConnectTimeout());
		assertEquals(expected.getHeaders(), actual.getHeaders());
	}
}
