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

package com.adobe.marketing.mobile;

import static com.adobe.marketing.mobile.EdgeConstants.LOG_TAG;

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Use this class to register {@link EdgeCallback}s for a specific event id
 * and get notified once a response is received from the Adobe Experience Edge
 */
class CompletionCallbacksManager {

	private static final String LOG_SOURCE = "CompletionCallbacksManager";

	private final ConcurrentMap<String, EdgeCallback> completionCallbacks;

	// edge response handles for a event request id (key)
	private final ConcurrentMap<String, List<EdgeEventHandle>> edgeEventHandles;

	private CompletionCallbacksManager() {
		completionCallbacks = new ConcurrentHashMap<>();
		edgeEventHandles = new ConcurrentHashMap<>();
	}

	/**
	 * Lazy initialization helper for the CompletionCallbacksManager singleton
	 */
	private static class SingletonHelper {

		private static final CompletionCallbacksManager INSTANCE = new CompletionCallbacksManager();
	}

	/**
	 * @return Singleton instance of the {@link CompletionCallbacksManager}
	 */
	static CompletionCallbacksManager getInstance() {
		return SingletonHelper.INSTANCE;
	}

	/**
	 * Registers a {@link EdgeCallback} for the specified {@code uniqueEventId}. This callback is invoked
	 * when the Edge response content has been handled entirely by the Edge extension, containing a list
	 * of {@link EdgeEventHandle}(s). This list can be empty or can contain one or multiple items
	 * based on the request and the server side response.
	 *
	 * @param requestEventId unique event identifier for which the completion callback is registered; should not be null/empty
	 * @param callback {@code EdgeCallback} that needs to be registered; should not be null
	 */
	void registerCallback(final String requestEventId, final EdgeCallback callback) {
		if (callback == null) {
			return;
		}

		if (StringUtils.isNullOrEmpty(requestEventId)) {
			Log.warning(LOG_TAG, LOG_SOURCE, "Failed to register response callback because of null/empty event id.");
			return;
		}

		Log.trace(LOG_TAG, LOG_SOURCE, "Registering callback for Edge response with unique id " + requestEventId);
		completionCallbacks.put(requestEventId, callback);
	}

	/**
	 * Calls the registered completion callback (if any) with the collected {@link EdgeEventHandle}(s). After this operation,
	 * the associated completion callback is removed and no longer called.
	 *
	 * @param requestEventId unique event identifier for Experience events; should not be null/empty
	 */
	void unregisterCallback(final String requestEventId) {
		if (StringUtils.isNullOrEmpty(requestEventId)) {
			return;
		}

		EdgeCallback callback = completionCallbacks.remove(requestEventId);

		if (callback != null) {
			final List<EdgeEventHandle> handles = edgeEventHandles.get(requestEventId);
			try {
				callback.onComplete(handles != null ? handles : new ArrayList<>());
			} catch (Exception ex) {
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Exception thrown when invoking completion callback for request event id %s: %s",
					requestEventId,
					android.util.Log.getStackTraceString(ex)
				);
			}
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Removing callback for Edge response with request event id " + requestEventId
			);
		}

		edgeEventHandles.remove(requestEventId);
	}

	/**
	 * Updates the list of {@link EdgeEventHandle}(s) for current {@code requestEventId}.
	 *
	 * @param requestEventId the request event identifier associated with this event handle
	 * @param eventHandle newly received event handle
	 */
	void eventHandleReceived(final String requestEventId, final EdgeEventHandle eventHandle) {
		if (StringUtils.isNullOrEmpty(requestEventId) || eventHandle == null) {
			return;
		}

		edgeEventHandles.putIfAbsent(requestEventId, new ArrayList<>());
		edgeEventHandles.get(requestEventId).add(eventHandle);
	}
}
