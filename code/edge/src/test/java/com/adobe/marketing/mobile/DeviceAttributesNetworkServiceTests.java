/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NetworkCallback;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.Networking;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DeviceAttributesNetworkServiceTests {

	private static final String URL = "https://edge.adobedc.net/ee/v1/mobile/device-attributes?configId=a&requestId=b";

	private Networking mockNetworking;
	private DeviceAttributesNetworkService networkService;

	@Before
	public void setUp() {
		mockNetworking = mock(Networking.class);
		networkService = new DeviceAttributesNetworkService(mockNetworking);
	}

	@Test
	public void testBuildUrl_appendsConfigIdAndRequestId() {
		final EdgeEndpoint endpoint = new EdgeEndpoint(
			EdgeNetworkService.RequestType.DEVICE_ATTRIBUTES,
			null,
			null,
			null,
			"or2"
		);

		assertEquals(
			"https://edge.adobedc.net/ee/or2/v1/mobile/device-attributes?configId=ds&requestId=req",
			networkService.buildUrl(endpoint, "ds", "req")
		);
	}

	@Test
	public void testBuildUrl_customDomain_buildsUrl() {
		assertEquals(
			"https://my.edge.com/ee/v1/mobile/device-attributes?configId=ds&requestId=req",
			networkService.buildUrl(endpoint(null, "my.edge.com", null), "ds", "req")
		);
		assertEquals(
			"https://my.edge.com/ee-pre-prd/or2/v1/mobile/device-attributes?configId=ds&requestId=req",
			networkService.buildUrl(endpoint("pre-prod", "my.edge.com", "or2"), "ds", "req")
		);
	}

	@Test
	public void testBuildUrl_intEnvironmentIgnoresCustomDomain_buildsUrl() {
		assertEquals(
			"https://edge-int.adobedc.net/ee/v1/mobile/device-attributes?configId=ds&requestId=req",
			networkService.buildUrl(endpoint("int", "my.edge.com", null), "ds", "req")
		);
	}

	@Test
	public void testBuildUrl_emptyDomain_usesDefaultDomain() {
		// EdgeEndpoint falls back to the default domain when the configured domain is empty
		assertEquals(
			"https://edge.adobedc.net/ee/v1/mobile/device-attributes?configId=ds&requestId=req",
			networkService.buildUrl(endpoint(null, "", null), "ds", "req")
		);
	}

	@Test
	public void testBuildUrl_invalidDomain_returnsNull() {
		for (final String domain : new String[] { "invalid:port", "invalid domain.com", "%zz", "a/b" }) {
			assertNull("domain " + domain, networkService.buildUrl(endpoint(null, domain, null), "ds", "req"));
		}
	}

	private static EdgeEndpoint endpoint(final String environment, final String domain, final String locationHint) {
		return new EdgeEndpoint(
			EdgeNetworkService.RequestType.DEVICE_ATTRIBUTES,
			environment,
			domain,
			null,
			locationHint
		);
	}

	@Test
	public void testSend_postsJsonWithMergedHeaders() {
		final Map<String, String> headers = new HashMap<>();
		headers.put(EdgeConstants.NetworkKeys.HEADER_KEY_AEP_VALIDATION_TOKEN, "token");
		final List<DeviceAttributesNetworkResult> results = new ArrayList<>();

		networkService.send(URL, "{\"a\":1}", headers, results::add);

		final ArgumentCaptor<NetworkRequest> requestCaptor = ArgumentCaptor.forClass(NetworkRequest.class);
		final ArgumentCaptor<NetworkCallback> callbackCaptor = ArgumentCaptor.forClass(NetworkCallback.class);
		verify(mockNetworking).connectAsync(requestCaptor.capture(), callbackCaptor.capture());
		final NetworkRequest request = requestCaptor.getValue();
		assertEquals(URL, request.getUrl());
		assertEquals("{\"a\":1}", new String(request.getBody(), StandardCharsets.UTF_8));
		assertEquals(
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON,
			request.getHeaders().get(EdgeConstants.NetworkKeys.HEADER_KEY_CONTENT_TYPE)
		);
		assertEquals(
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON,
			request.getHeaders().get(EdgeConstants.NetworkKeys.HEADER_KEY_ACCEPT)
		);
		assertEquals("token", request.getHeaders().get(EdgeConstants.NetworkKeys.HEADER_KEY_AEP_VALIDATION_TOKEN));

		final HttpConnecting connection = connection(202, "{}");
		callbackCaptor.getValue().call(connection);

		assertEquals(1, results.size());
		assertEquals(DeviceAttributesNetworkResult.Type.SUCCESS, results.get(0).getType());
		verify(connection).close();
	}

	@Test
	public void testSend_classifyThrows_callsBackOnceWithFailureAndCloses() {
		final HttpConnecting connection = mock(HttpConnecting.class);
		when(connection.getResponseCode()).thenThrow(new RuntimeException("boom"));

		final List<DeviceAttributesNetworkResult> results = sendAndRespond(connection);

		assertEquals(1, results.size());
		assertEquals(DeviceAttributesNetworkResult.Type.FAILED, results.get(0).getType());
		assertNull(results.get(0).getHttpStatus());
		verify(connection).close();
	}

	@Test
	public void testSend_classifyThrowsAfterReadingCode_keepsHttpStatus() {
		final HttpConnecting connection = connection(503, null);
		when(connection.getResponsePropertyValue(EdgeConstants.NetworkKeys.HEADER_KEY_RETRY_AFTER))
			.thenThrow(new RuntimeException("boom"));

		final List<DeviceAttributesNetworkResult> results = sendAndRespond(connection);

		assertEquals(1, results.size());
		assertEquals(DeviceAttributesNetworkResult.Type.FAILED, results.get(0).getType());
		assertEquals(Integer.valueOf(503), results.get(0).getHttpStatus());
		verify(connection).close();
	}

	@Test
	public void testSend_closeThrows_callsBackOnceWithClassifiedResult() {
		final HttpConnecting connection = connection(200, "{}");
		doThrow(new RuntimeException("boom")).when(connection).close();

		final List<DeviceAttributesNetworkResult> results = sendAndRespond(connection);

		assertEquals(1, results.size());
		assertEquals(DeviceAttributesNetworkResult.Type.SUCCESS, results.get(0).getType());
		assertEquals(Integer.valueOf(200), results.get(0).getHttpStatus());
	}

	private List<DeviceAttributesNetworkResult> sendAndRespond(final HttpConnecting connection) {
		final List<DeviceAttributesNetworkResult> results = new ArrayList<>();
		networkService.send(URL, "{}", null, results::add);

		final ArgumentCaptor<NetworkCallback> callbackCaptor = ArgumentCaptor.forClass(NetworkCallback.class);
		verify(mockNetworking).connectAsync(any(NetworkRequest.class), callbackCaptor.capture());
		callbackCaptor.getValue().call(connection);
		return results;
	}

	@Test
	public void testClassify_success_readsBody() {
		final DeviceAttributesNetworkResult result = networkService.classify(connection(200, "{\"handle\":[]}"), URL);

		assertEquals(DeviceAttributesNetworkResult.Type.SUCCESS, result.getType());
		assertEquals(Integer.valueOf(200), result.getHttpStatus());
		assertEquals("{\"handle\":[]}", result.getBody());
	}

	@Test
	public void testClassify_204And207_areFailed() {
		assertFailed(204);
		assertFailed(207);
	}

	@Test
	public void testClassify_unrecoverableCodes_areFailed() {
		for (final int code : new int[] { 400, 401, 403, 404, 413, 500, 501 }) {
			assertFailed(code);
		}
	}

	@Test
	public void testClassify_recoverableCodes_matchInteractPath() {
		for (final int code : EdgeNetworkService.recoverableNetworkErrorCodes) {
			final DeviceAttributesNetworkResult result = networkService.classify(connection(code, null), URL);
			assertEquals("code " + code, DeviceAttributesNetworkResult.Type.RETRY, result.getType());
			assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, result.getRetryIntervalSeconds());
		}
	}

	@Test
	public void testClassify_responseCodeMinusOne_socketTimeout_retries() {
		// Core returns a connection with response code -1 when the request times out
		final DeviceAttributesNetworkResult result = networkService.classify(connection(-1, null), URL);

		assertEquals(DeviceAttributesNetworkResult.Type.RETRY, result.getType());
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, result.getRetryIntervalSeconds());
		assertNull(result.getHttpStatus());
	}

	@Test
	public void testClassify_nullConnection_retries() {
		// Core returns a null connection when the device is offline or the connection could not be created
		final DeviceAttributesNetworkResult result = networkService.classify(null, URL);

		assertEquals(DeviceAttributesNetworkResult.Type.RETRY, result.getType());
		assertEquals(EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS, result.getRetryIntervalSeconds());
		assertNull(result.getHttpStatus());
	}

	@Test
	public void testClassify_retryAfterHeader_isHonored() {
		final HttpConnecting connection = connection(429, null);
		when(connection.getResponsePropertyValue(EdgeConstants.NetworkKeys.HEADER_KEY_RETRY_AFTER)).thenReturn("42");

		assertEquals(42, networkService.classify(connection, URL).getRetryIntervalSeconds());
	}

	private void assertFailed(final int code) {
		final DeviceAttributesNetworkResult result = networkService.classify(
			connection(code, "{\"title\":\"t\"}"),
			URL
		);
		assertEquals("code " + code, DeviceAttributesNetworkResult.Type.FAILED, result.getType());
		assertEquals(Integer.valueOf(code), result.getHttpStatus());
		assertNull(result.getBody());
	}

	private static HttpConnecting connection(final int code, final String body) {
		final HttpConnecting connection = mock(HttpConnecting.class);
		when(connection.getResponseCode()).thenReturn(code);

		if (body != null) {
			when(connection.getInputStream())
				.thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
			when(connection.getErrorStream())
				.thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
		}

		return connection;
	}
}
