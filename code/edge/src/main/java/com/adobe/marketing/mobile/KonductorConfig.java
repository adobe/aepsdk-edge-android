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

import com.adobe.marketing.mobile.services.Log;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 * Metadata for Edge Network.
 */
class KonductorConfig {

	private static final String LOG_SOURCE = "KonductorConfig";

	private static final String JSON_KEY_GATEWAY = "konductorConfig";
	private static final String JSON_KEY_STREAMING = "streaming";
	private static final String JSON_KEY_STREAMING_ENABLED = "enabled";
	private static final String JSON_KEY_STREAMING_RECORD_SEPARATOR = "recordSeparator";
	private static final String JSON_KEY_STREAMING_LINE_FEED = "lineFeed";

	private boolean streamingEnabled;
	private String recordSeparator;
	private String lineFeed;

	KonductorConfig() {
		streamingEnabled = false;
	}

	/**
	 * Enables the streaming of the response fragments (HTTP 1.1/chunked, IETF RFC 7464).
	 *
	 * @param recordSeparator control character used before each response record (fragment)
	 * @param lineFeed control character used at the end of each response record (fragment)
	 * @throws IllegalArgumentException if either argument is null
	 */
	void enableStreaming(final String recordSeparator, final String lineFeed) {
		if (recordSeparator == null || lineFeed == null) {
			throw new IllegalArgumentException("Streaming record separator and line feed shall not be null.");
		}

		this.streamingEnabled = true;
		this.recordSeparator = recordSeparator;
		this.lineFeed = lineFeed;
	}

	boolean isStreamingEnabled() {
		return streamingEnabled;
	}

	String getRecordSeparator() {
		return recordSeparator;
	}

	String getLineFeed() {
		return lineFeed;
	}

	Map<String, Object> toObjectMap() {
		Map<String, Object> streaming = new HashMap<>();
		streaming.put(JSON_KEY_STREAMING_ENABLED, streamingEnabled);

		if (streamingEnabled) {
			streaming.put(JSON_KEY_STREAMING_RECORD_SEPARATOR, recordSeparator);
			streaming.put(JSON_KEY_STREAMING_LINE_FEED, lineFeed);
		}

		Map<String, Object> gateway = new HashMap<>();

		gateway.put(JSON_KEY_STREAMING, streaming);

		return gateway;
	}

	static KonductorConfig fromJsonRequest(final String jsonRequest) {
		JSONObject metadataObject;

		try {
			metadataObject = new JSONObject(jsonRequest).optJSONObject(EdgeJson.Event.METADATA);
		} catch (Exception e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Failed to read KonductorConfig from json request.");
			return null;
		}

		if (metadataObject == null) {
			return null;
		}

		JSONObject gatewayJsonObject = metadataObject.optJSONObject(JSON_KEY_GATEWAY);

		if (gatewayJsonObject == null) {
			return null;
		}

		JSONObject streamingJsonObject = gatewayJsonObject.optJSONObject(JSON_KEY_STREAMING);

		if (streamingJsonObject == null) {
			return null;
		}

		KonductorConfig konductorConfig = new KonductorConfig();
		konductorConfig.streamingEnabled = streamingJsonObject.optBoolean(JSON_KEY_STREAMING_ENABLED);
		konductorConfig.recordSeparator = streamingJsonObject.optString(JSON_KEY_STREAMING_RECORD_SEPARATOR);
		konductorConfig.lineFeed = streamingJsonObject.optString(JSON_KEY_STREAMING_LINE_FEED);

		return konductorConfig;
	}
}
