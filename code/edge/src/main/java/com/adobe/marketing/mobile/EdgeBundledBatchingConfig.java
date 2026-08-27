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

import androidx.annotation.VisibleForTesting;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.StreamUtils;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Loads the bundled fallback for the Edge batching configuration ({@code edge.batching}) from a JSON
 * file in the app's assets folder. The file uses the same grouped format as the Configuration
 * shared-state value (see {@link EdgeBatchingConfig}), so both are parsed identically.
 *
 * <p>This is a <em>wholesale</em> fallback, not a per-key merge: {@link EventUtils#getEdgeConfiguration}
 * uses this bundled object only when {@code edge.batching} is absent from the Configuration shared
 * state at the time an event is queued. A batching config present in the Configuration shared state —
 * whether set programmatically via {@code MobileCore.updateConfiguration()} or delivered by a
 * remote/Launch-published configuration — wins in its entirety over the bundled file.
 *
 * <p>Unlike Mobile Core's own {@code ADBMobileConfig.json} bundled-configuration mechanism (which this
 * mirrors in spirit), this file is scoped specifically to the Edge batching configuration and is not a
 * general-purpose configuration bundle.
 */
final class EdgeBundledBatchingConfig {

	private static final String LOG_SOURCE = "EdgeBundledBatchingConfig";

	/** Name of the JSON file expected in the app's assets folder. */
	static final String BUNDLED_CONFIG_FILE_NAME = "ADBMobileEdgeBatchingConfig.json";

	private static final Object loadLock = new Object();
	private static volatile Map<String, Object> cachedConfig;
	private static volatile boolean loadAttempted = false;

	private EdgeBundledBatchingConfig() {}

	/**
	 * Returns the parsed contents of the bundled batching config file, loading and caching it the
	 * first time this is called. Subsequent calls return the cached result without re-reading the
	 * file. Returns an empty map (never null) if the file is missing, empty, or malformed.
	 *
	 * @return a read-only {@code Map} of whatever keys the bundled file contains
	 */
	static Map<String, Object> get() {
		if (!loadAttempted) {
			synchronized (loadLock) {
				if (!loadAttempted) {
					cachedConfig = load();
					loadAttempted = true;
				}
			}
		}

		return cachedConfig;
	}

	/**
	 * Clears the cached result so the next call to {@link #get()} re-reads the bundled file.
	 * Test-only; production code should never need to force a re-read.
	 */
	@VisibleForTesting
	static void resetForTesting() {
		synchronized (loadLock) {
			cachedConfig = null;
			loadAttempted = false;
		}
	}

	private static Map<String, Object> load() {
		final InputStream inputStream = ServiceProvider
			.getInstance()
			.getDeviceInfoService()
			.getAsset(BUNDLED_CONFIG_FILE_NAME);
		final String content = StreamUtils.readAsString(inputStream);

		if (content == null || content.isEmpty()) {
			Log.trace(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"No bundled batching config file '%s' found in assets; skipping.",
				BUNDLED_CONFIG_FILE_NAME
			);
			return Collections.emptyMap();
		}

		try {
			final Map<String, Object> parsed = JSONUtils.toMap(new JSONObject(content));
			if (parsed == null) {
				return Collections.emptyMap();
			}

			Log.debug(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Loaded bundled batching config from '%s': %s",
				BUNDLED_CONFIG_FILE_NAME,
				parsed
			);
			return parsed;
		} catch (final JSONException e) {
			Log.warning(
				EdgeConstants.LOG_TAG,
				LOG_SOURCE,
				"Failed to parse bundled batching config file '%s': %s",
				BUNDLED_CONFIG_FILE_NAME,
				e.getLocalizedMessage()
			);
			return Collections.emptyMap();
		}
	}
}
