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

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.HttpMethod;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.Networking;
import com.adobe.marketing.mobile.util.StringUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Network service for requests to the Adobe Edge Network.
 */
class EdgeNetworkService {

	private static final String LOG_SOURCE = "EdgeNetworkService";

	/**
	 * Edge Request Type.
	 * <ul>
	 *     <li>INTERACT - makes request and expects a response</li>
	 *     <li>CONSENT - makes request to the consent endpoint, expects a response</li>
	 * </ul>
	 */
	public enum RequestType {
		INTERACT("interact"),
		CONSENT("privacy/set-consent");

		public final String type;

		RequestType(final String type) {
			this.type = type;
		}
	}

	public enum Retry {
		YES("YES"),
		NO("NO");

		public final String retryString;

		Retry(final String type) {
			this.retryString = type;
		}
	}

	interface ResponseCallback {
		/**
		 * This method is called when the response was successfully fetched from the Adobe Experience Edge
		 * for the associated event; this callback may be called multiple times for the same event, based on the
		 * data coming from the server
		 * @param jsonResponse response from the server as JSON formatted string
		 */
		void onResponse(final String jsonResponse);

		/**
		 * This method is called when the Adobe Experience Edge returns an error for the associated event
		 * @param jsonError error from server as JSON formatted string, or null
		 *                  if the server sent no useful data.
		 */
		void onError(final String jsonError);

		/**
		 * This method is called when the network connection was closed and there is no more stream
		 * pending for marking a network request as complete. This can be used for running cleanup jobs
		 * after a network response is received.
		 */
		void onComplete();
	}

	private static final String DEFAULT_NAMESPACE = "global";
	private static final String DEFAULT_GENERIC_ERROR_MESSAGE =
		"Request to Edge Network failed with an unknown exception";

	static final List<Integer> recoverableNetworkErrorCodes = new ArrayList<>(
		Arrays.asList(
			-1, // returned for SocketTimeoutException
			429, // too many requests - The user has sent too many requests in a given amount of time ("rate limiting").
			HttpURLConnection.HTTP_CLIENT_TIMEOUT,
			HttpURLConnection.HTTP_BAD_GATEWAY,
			HttpURLConnection.HTTP_UNAVAILABLE,
			HttpURLConnection.HTTP_GATEWAY_TIMEOUT
		)
	);

	private final Networking networkService;

	/**
	 * Construct a new {@code EdgeNetworkService} instance.
	 * @param service non-null platform {@link Networking} service
	 */
	EdgeNetworkService(final Networking service) {
		if (service == null) {
			throw new IllegalArgumentException("NetworkService cannot be null.");
		}

		this.networkService = service;
	}

	/**
	 * Make a request to the Adobe Edge Network. On successful request, response content
	 * is sent to the given {@link ResponseCallback#onResponse(String)} handler. If streaming is enabled on
	 * request, {@link ResponseCallback#onResponse(String)} is called multiple times.
	 * On error, the given {@link ResponseCallback#onError(String)} is called with an
	 * error message.  The {@code propertyId} is required.
	 * @param url url to the Adobe Edge Network
	 * @param jsonRequest the request body as a JSON formatted string
	 * @param requestHeaders the HTTP headers to attach to the request
	 * @param responseCallback optional callback to receive the Adobe Edge Network response; the
	 *                         {@link ResponseCallback#onComplete()} callback is invoked when no retry is required,
	 *                         otherwise it is the caller's responsibility to do any necessary cleanup
	 * @return {@link Retry} status indicating if the request failed due to a recoverable error and should be retried
	 */
	RetryResult doRequest(
		final String url,
		final String jsonRequest,
		final Map<String, String> requestHeaders,
		final ResponseCallback responseCallback
	) {
		if (StringUtils.isNullOrEmpty(url)) {
			Log.error(LOG_TAG, LOG_SOURCE, "Could not send request to a null url");

			if (responseCallback != null) {
				responseCallback.onComplete();
			}

			return new RetryResult(Retry.NO);
		}

		HttpConnecting connection = doConnect(url, jsonRequest, requestHeaders);

		if (connection == null) {
			final RetryResult retryResult = new RetryResult(Retry.YES);
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Network request returned null connection. Will retry request in %d seconds.",
				retryResult.getRetryIntervalSeconds()
			);
			return retryResult;
		}

		RetryResult retryResult = new RetryResult(Retry.NO);

		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Interact connection to Experience Edge successful. Response message: " +
				connection.getResponseMessage()
			);

			KonductorConfig konductorConfig = KonductorConfig.fromJsonRequest(jsonRequest);
			boolean shouldStreamResponse = konductorConfig != null && konductorConfig.isStreamingEnabled();

			handleContent(
				connection.getInputStream(),
				shouldStreamResponse ? konductorConfig.getRecordSeparator() : null,
				shouldStreamResponse ? konductorConfig.getLineFeed() : null,
				responseCallback
			);
		} else if (connection.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
			// Successful collect requests do not return content
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Interact connection to Experience Edge successful. Response message: " +
				connection.getResponseMessage()
			);
		} else if (recoverableNetworkErrorCodes.contains(connection.getResponseCode())) {
			retryResult = new RetryResult(Retry.YES, computeRetryInterval(connection));

			if (connection.getResponseCode() == -1) {
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Connection to Experience Edge failed. Failed to read message/error code from NetworkService. Will retry request in %d seconds.",
					retryResult.getRetryIntervalSeconds()
				);
			} else {
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Connection to Experience Edge returned recoverable error code (%d). Response message: %s. Will retry request in %d seconds.",
					connection.getResponseCode(),
					connection.getResponseMessage(),
					retryResult.getRetryIntervalSeconds()
				);
			}
		} else if (connection.getResponseCode() == 207) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Interact connection to Experience Edge successful but encountered non-fatal errors/warnings. Response message: %s",
				connection.getResponseMessage()
			);

			KonductorConfig konductorConfig = KonductorConfig.fromJsonRequest(jsonRequest);
			boolean shouldStreamResponse = konductorConfig != null && konductorConfig.isStreamingEnabled();

			handleContent(
				connection.getInputStream(),
				shouldStreamResponse ? konductorConfig.getRecordSeparator() : null,
				shouldStreamResponse ? konductorConfig.getLineFeed() : null,
				responseCallback
			);
		} else {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Connection to Experience Edge returned unrecoverable error code (%d). Response message: %s",
				connection.getResponseCode(),
				connection.getResponseMessage()
			);
			handleError(connection.getErrorStream(), responseCallback);
		}

		connection.close();

		if (retryResult.getShouldRetry() == Retry.NO && responseCallback != null) {
			responseCallback.onComplete();
		}

		return retryResult;
	}

	/**
	 * Computes the retry interval for the given network connection
	 * @param connection the network connection that needs to be retried
	 * @return the retry interval in seconds
	 */
	private int computeRetryInterval(final HttpConnecting connection) {
		String header = connection.getResponsePropertyValue(EdgeConstants.NetworkKeys.HEADER_KEY_RETRY_AFTER);

		if (header != null && header.matches("\\d+")) {
			try {
				return Integer.parseInt(header);
			} catch (NumberFormatException e) {
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Failed to parse Retry-After header with value of '%s' to an int with error: %s",
					header,
					e.getLocalizedMessage()
				);
			}
		}

		return EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
	}

	/**
	 *
	 * @param edgeEndpoint the {@link EdgeEndpoint} containing the base Experience Edge endpoint
	 * @param configId required globally unique identifier
	 * @param requestId optional request ID. If one is not given, the Adobe Edge Network generates one in the response
	 * @return the computed URL
	 */
	public String buildUrl(final EdgeEndpoint edgeEndpoint, final String configId, final String requestId) {
		StringBuilder url = new StringBuilder(edgeEndpoint.getEndpoint());
		url.append("?").append(EdgeConstants.NetworkKeys.REQUEST_PARAMETER_KEY_CONFIG_ID).append("=").append(configId);

		if (requestId != null && !requestId.isEmpty()) {
			url
				.append("&")
				.append(EdgeConstants.NetworkKeys.REQUEST_PARAMETER_KEY_REQUEST_ID)
				.append("=")
				.append(requestId);
		}

		return url.toString();
	}

	/**
	 * Make a network request to the Adobe Edge Network and return the connection object.
	 * @param url URL to the Adobe Edge Network. Must contain the required config ID as a query parameter
	 * @param jsonRequest the request body as a JSON formatted string
	 * @param requestHeaders HTTP headers to be included with the request
	 * @return {@link HttpConnecting} object once the connection was initiated or null if an error occurred
	 */
	private HttpConnecting doConnect(
		final String url,
		final String jsonRequest,
		final Map<String, String> requestHeaders
	) {
		Map<String, String> headers = getDefaultHeaders();

		if ((requestHeaders != null) && !(requestHeaders.isEmpty())) {
			headers.putAll(requestHeaders);
		}

		Log.trace(LOG_TAG, LOG_SOURCE, "HTTP Headers: " + headers);
		NetworkRequest networkRequest = new NetworkRequest(
			url,
			HttpMethod.POST,
			jsonRequest.getBytes(),
			headers,
			EdgeConstants.NetworkKeys.DEFAULT_CONNECT_TIMEOUT_SECONDS,
			EdgeConstants.NetworkKeys.DEFAULT_READ_TIMEOUT_SECONDS
		);

		final CountDownLatch countDownLatch = new CountDownLatch(1);
		final HttpConnecting[] httpConnecting = new HttpConnecting[1];
		networkService.connectAsync(
			networkRequest,
			connection -> {
				httpConnecting[0] = connection;
				countDownLatch.countDown();
			}
		);

		try {
			countDownLatch.await();
			return httpConnecting[0];
		} catch (final InterruptedException | IllegalArgumentException e) {
			Log.warning(LOG_TAG, LOG_SOURCE, "Connection failure for url (%s), error: (%s)", url, e);
		}

		return null;
	}

	/**
	 * Attempt the read the response from the connection's {@link InputStream} and return the content
	 * in the {@link ResponseCallback}. This method should be used for handling 2xx server response.
	 *
	 * In the eventuality of an error, this method returns false and an error message is logged.
	 *
	 * @param inputStream the content to process
	 * @param recordSeparator the record separator
	 * @param lineFeedDelimiter line feed delimiter
	 * @param responseCallback {@code ResponseCallback} used for returning the response content
	 */
	private void handleContent(
		final InputStream inputStream,
		final String recordSeparator,
		final String lineFeedDelimiter,
		final ResponseCallback responseCallback
	) {
		if (responseCallback == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Callback is null, processing of response content aborted.");
			return;
		}

		if (inputStream == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Network response contains no data, InputStream is null.");
			return;
		}

		if (recordSeparator != null && lineFeedDelimiter != null) {
			handleStreamingResponse(inputStream, recordSeparator, lineFeedDelimiter, responseCallback);
		} else {
			handleNonStreamingResponse(inputStream, responseCallback);
		}
	}

	/**
	 * Attempt to read the streamed response from the {@link InputStream} and return the content
	 * via the {@link ResponseCallback}.
	 *
	 *
	 * @param inputStream the content to process
	 * @param recordSeparator the record separator
	 * @param lineFeedDelimiter line feed delimiter
	 * @param responseCallback {@code ResponseCallback} used for returning the streamed response content
	 */
	void handleStreamingResponse(
		final InputStream inputStream,
		final String recordSeparator,
		final String lineFeedDelimiter,
		final ResponseCallback responseCallback
	) {
		if (inputStream == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Network response contains no data, InputStream is null.");
			return;
		}

		if (recordSeparator == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "record separator is null, processing of response content aborted.");
			return;
		}

		if (lineFeedDelimiter == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "line feed is null, processing of response content aborted.");
			return;
		}

		if (responseCallback == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Callback is null, processing of response content aborted.");
			return;
		}

		final Scanner scanner = new Scanner(inputStream, "UTF-8");
		scanner.useDelimiter(lineFeedDelimiter);

		final int trimLength = recordSeparator.length();

		while (scanner.hasNext()) {
			final String jsonResult = scanner.next();
			if (jsonResult.length() - trimLength < 0) {
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Unexpected network response chunk is shorter than record separator '%s'. Ignoring response '%s'.",
					recordSeparator,
					jsonResult
				);
				continue;
			}

			responseCallback.onResponse(jsonResult.substring(trimLength));
		}
	}

	/**
	 * Attempt to read the response from the connection's {@link InputStream} and returns it
	 * in the {@link ResponseCallback}.
	 *
	 * @param inputStream to read the response content from
	 * @param responseCallback invoked by calling {@code onResponse} with the parsed response content
	 */
	void handleNonStreamingResponse(final InputStream inputStream, final ResponseCallback responseCallback) {
		if (responseCallback == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Callback is null, processing of response content aborted.");
			return;
		}

		if (inputStream == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Network response contains no data, InputStream is null.");
			return;
		}

		responseCallback.onResponse(readInputStream(inputStream));
	}

	/**
	 * Attempt to read the error response from the connection's error {@link InputStream} and returns it
	 * in the {@link ResponseCallback#onError(String)}.
	 *
	 * @param inputStream to read the connection error from
	 * @param responseCallback invoked by calling {@code onError} with the parsed error information
	 */
	private void handleError(final InputStream inputStream, final ResponseCallback responseCallback) {
		if (responseCallback == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Callback is null, processing of error content aborted.");
			return;
		}

		if (inputStream == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Network response contains no data, error InputStream is null.");
			responseCallback.onError(composeGenericErrorAsJson(null));
			return;
		}

		/*
		 * Assuming single JSON response and not a stream; the generic errors are be sent as one chunk
		 */
		String responseStr = readInputStream(inputStream);

		/*
		 * The generic errors can be provided by Edge Network or JAG. JAG doesn't guarantee that the
		 * response has JSON format, it can also be plain text, need to handle both situations.
		 */
		try {
			if (responseStr != null) {
				new JSONObject(responseStr); // test if valid JSON
			} else {
				responseStr = composeGenericErrorAsJson(null);
			}
		} catch (JSONException e) {
			responseStr = composeGenericErrorAsJson(responseStr);
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Network response has Content-Type application/json, but cannot be parsed as JSON, returning generic error"
			);
		}

		responseCallback.onError(responseStr);
	}

	private Map<String, String> getDefaultHeaders() {
		Map<String, String> defaultHeaders = new HashMap<>();
		defaultHeaders.put(
			EdgeConstants.NetworkKeys.HEADER_KEY_ACCEPT,
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON
		);
		defaultHeaders.put(
			EdgeConstants.NetworkKeys.HEADER_KEY_CONTENT_TYPE,
			EdgeConstants.NetworkKeys.HEADER_VALUE_APPLICATION_JSON
		);
		return defaultHeaders;
	}

	/**
	 * Composes a generic error (string with JSON format), containing generic namespace and the provided
	 * error message, after removing the leading and trailing spaces.
	 *
	 * @param plainTextErrorMessage error message to be formatted; if null/empty is provided, a default
	 *                              error message is returned.
	 * @return the JSON formatted error
	 */
	private String composeGenericErrorAsJson(final String plainTextErrorMessage) {
		// trim the error message or set the default generic message if no value is provided
		String errorMessage = StringUtils.isNullOrEmpty(plainTextErrorMessage)
			? DEFAULT_GENERIC_ERROR_MESSAGE
			: plainTextErrorMessage.trim();
		errorMessage = errorMessage.isEmpty() ? DEFAULT_GENERIC_ERROR_MESSAGE : errorMessage;

		JSONObject json = new JSONObject();

		try {
			json.put(EdgeJson.Response.Error.TITLE, errorMessage);
			json.put(EdgeJson.Response.Error.TYPE, DEFAULT_NAMESPACE);
		} catch (JSONException e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Failed to create the generic error json " + e.getLocalizedMessage());
		}

		return json.toString();
	}

	/**
	 * Reads all the contents of an input stream out into a {@link String}
	 *
	 * @param inputStream the input stream to be read from
	 *
	 * @return the contents of the input stream as a string
	 */
	private String readInputStream(final InputStream inputStream) {
		if (inputStream == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Network response contains no data, InputStream is null.");
			return null;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

		try {
			String newLine = System.getProperty("line.separator");
			StringBuilder stringBuilder = new StringBuilder();
			String line;

			boolean newLineFlag = false;

			while ((line = reader.readLine()) != null) {
				stringBuilder.append(newLineFlag ? newLine : "").append(line);
				newLineFlag = true;
			}

			return stringBuilder.toString();
		} catch (IOException e) {
			Log.warning(LOG_TAG, LOG_SOURCE, "Exception reading network error response: " + e.getLocalizedMessage());
			return composeGenericErrorAsJson(e.getMessage());
		}
	}
}
