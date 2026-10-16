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

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.HttpMethod;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.Networking;
import com.adobe.marketing.mobile.util.StreamUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.util.UrlUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Sends device attributes requests using the Core {@link Networking} service and classifies the outcome.
 */
class DeviceAttributesNetworkService {

	private static final String LOG_SOURCE = "DeviceAttributesNetworkService";

	private final Networking networkService;

	/**
	 * @param networkService the platform {@link Networking} service
	 */
	DeviceAttributesNetworkService(final Networking networkService) {
		this.networkService = networkService;
	}

	/**
	 * Builds the device attributes URL: {@code <endpoint>?configId=<datastreamId>&requestId=<requestId>}.
	 *
	 * @param endpoint the {@link EdgeEndpoint} for the device attributes request type
	 * @param datastreamId the datastream identifier
	 * @param requestId the request identifier
	 * @return the URL, or null if it is not a valid HTTPS URL whose host matches the endpoint host
	 */
	@Nullable String buildUrl(
		@NonNull final EdgeEndpoint endpoint,
		@NonNull final String datastreamId,
		@NonNull final String requestId
	) {
		final String url =
			endpoint.getEndpoint() +
			"?" +
			EdgeConstants.NetworkKeys.REQUEST_PARAMETER_KEY_CONFIG_ID +
			"=" +
			datastreamId +
			"&" +
			EdgeConstants.NetworkKeys.REQUEST_PARAMETER_KEY_REQUEST_ID +
			"=" +
			requestId;

		if (!UrlUtils.isValidUrl(url) || !url.startsWith("https") || !hasExpectedHost(url, endpoint.getHost())) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Device attributes request URL is invalid, '%s'.", url);
			return null;
		}

		return url;
	}

	/**
	 * {@link UrlUtils#isValidUrl} accepts hosts that cannot be reached (for example with spaces or invalid escapes) and
	 * domains containing path characters (for example {@code a/b} parses with host {@code a}). Such requests would be
	 * retried indefinitely, so the URL must parse as a URI whose host equals the host the endpoint was built for.
	 *
	 * @param url the request URL
	 * @param expectedHost the host the endpoint was built for
	 * @return true if the URL host is non-empty and equals {@code expectedHost}
	 */
	private static boolean hasExpectedHost(final String url, final String expectedHost) {
		try {
			final String host = new URI(url).getHost();
			return !StringUtils.isNullOrEmpty(host) && host.equalsIgnoreCase(expectedHost);
		} catch (final URISyntaxException e) {
			return false;
		}
	}

	/**
	 * Sends the device attributes request and reports the classified outcome.
	 *
	 * @param url request URL
	 * @param body JSON request body
	 * @param headers additional request headers, merged over the default JSON headers
	 * @param callback invoked with the classified {@link DeviceAttributesNetworkResult}
	 */
	void send(
		@NonNull final String url,
		@NonNull final String body,
		@Nullable final Map<String, String> headers,
		@NonNull final AdobeCallback<DeviceAttributesNetworkResult> callback
	) {
		final Map<String, String> requestHeaders = getDefaultHeaders();

		if (headers != null) {
			requestHeaders.putAll(headers);
		}

		final NetworkRequest networkRequest = new NetworkRequest(
			url,
			HttpMethod.POST,
			body.getBytes(StandardCharsets.UTF_8),
			requestHeaders,
			EdgeConstants.NetworkKeys.DEFAULT_CONNECT_TIMEOUT_SECONDS,
			EdgeConstants.NetworkKeys.DEFAULT_READ_TIMEOUT_SECONDS
		);
		Log.debug(LOG_TAG, LOG_SOURCE, "Sending device attributes request to URL %s with body: %s", url, body);

		networkService.connectAsync(
			networkRequest,
			connection -> {
				final DeviceAttributesNetworkResult result = classify(connection, url);

				if (connection != null) {
					connection.close();
				}

				callback.call(result);
			}
		);
	}

	/**
	 * Classifies the {@code connection} into success, retry or failure.
	 *
	 * @param connection the network connection, or null if the connection could not be established
	 * @param url the request URL, used for logging
	 * @return the classified {@link DeviceAttributesNetworkResult}
	 */
	@NonNull DeviceAttributesNetworkResult classify(@Nullable final HttpConnecting connection, final String url) {
		if (connection == null) {
			// Core returns a null connection when the device is offline or the connection could not be created. It is
			// retried indefinitely, like the interact path. Invalid URLs are rejected earlier by buildUrl.
			final int retryInterval = EdgeConstants.Defaults.RETRY_INTERVAL_SECONDS;
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Device attributes request with url %s returned null connection. Will retry in %d seconds.",
				url,
				retryInterval
			);
			return DeviceAttributesNetworkResult.retry(retryInterval);
		}

		final int responseCode = connection.getResponseCode();

		if (DeviceAttributesConstants.SUCCESS_RESPONSE_CODES.contains(responseCode)) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Device attributes request was successful (%d).", responseCode);
			return DeviceAttributesNetworkResult.success(responseCode, readStream(connection, true));
		}

		// Same recoverable set as the interact path, so retry behavior matches
		if (EdgeNetworkService.recoverableNetworkErrorCodes.contains(responseCode)) {
			final int retryInterval = computeRetryInterval(connection);
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Device attributes request returned recoverable code %d. Will retry in %d seconds.",
				responseCode,
				retryInterval
			);
			return DeviceAttributesNetworkResult.retry(retryInterval);
		}

		logUnrecoverableResponse(responseCode, readStream(connection, false));
		return DeviceAttributesNetworkResult.failed(responseCode);
	}

	/**
	 * Computes the retry interval from an integer {@code Retry-After} header, matching the interact path.
	 *
	 * @param connection the network connection that needs to be retried
	 * @return the retry interval in seconds
	 */
	private int computeRetryInterval(@NonNull final HttpConnecting connection) {
		final String header = connection.getResponsePropertyValue(EdgeConstants.NetworkKeys.HEADER_KEY_RETRY_AFTER);

		if (header != null && header.matches("\\d+")) {
			try {
				final int retryAfter = Integer.parseInt(header);

				if (retryAfter > 0) {
					return retryAfter;
				}
			} catch (final NumberFormatException e) {
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
	 * Logs an unrecoverable response, including the problem JSON {@code title} and {@code report.errors}
	 * when present (e.g. HTTP 400).
	 *
	 * @param responseCode the HTTP status code
	 * @param body the response body, if any
	 */
	private void logUnrecoverableResponse(final int responseCode, @Nullable final String body) {
		final StringBuilder details = new StringBuilder();

		if (!StringUtils.isNullOrEmpty(body)) {
			try {
				final JSONObject json = new JSONObject(body);
				final String title = json.optString(DeviceAttributesConstants.ResponseKeys.TITLE, null);

				if (title != null) {
					details.append(" title: ").append(title);
				}

				final JSONObject report = json.optJSONObject(DeviceAttributesConstants.ResponseKeys.REPORT);
				final Object errors = report != null ? report.opt(DeviceAttributesConstants.ResponseKeys.ERRORS) : null;

				if (errors != null) {
					details.append(" errors: ").append(errors);
				}
			} catch (final Exception e) {
				// not a problem JSON body, log the response code only
			}
		}

		Log.warning(
			LOG_TAG,
			LOG_SOURCE,
			"Device attributes request returned unrecoverable code %d.%s",
			responseCode,
			details.toString()
		);
	}

	@Nullable private String readStream(@NonNull final HttpConnecting connection, final boolean success) {
		try {
			return StreamUtils.readAsString(success ? connection.getInputStream() : connection.getErrorStream());
		} catch (final Exception e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Failed to read device attributes response: %s", e.getLocalizedMessage());
			return null;
		}
	}

	private Map<String, String> getDefaultHeaders() {
		final Map<String, String> defaultHeaders = new HashMap<>();
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
}
