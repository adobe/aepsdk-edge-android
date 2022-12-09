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
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class is used to process the Experience Edge network responses when the {@link EdgeNetworkService.ResponseCallback}
 * is invoked with a response or error message. The response processing consists in parsing the server
 * response message and dispatching response content and/or error response content events and storing the response payload (if needed).
 */
class NetworkResponseHandler {

	private static final String LOG_SOURCE = "NetworkResponseHandler";

	// the order of the request events matter for matching them with the response events
	private final ConcurrentMap<String, List<WaitingEventContext>> sentEventsWaitingResponse;
	private final Object mutex = new Object();
	private final NamedCollection namedCollection;
	private final EdgeStateCallback edgeStateCallback;
	// Date of the last edge identity reset complete event
	private long lastResetDate;

	NetworkResponseHandler(final NamedCollection namedCollection, final EdgeStateCallback edgeStateCallback) {
		this.edgeStateCallback = edgeStateCallback;
		sentEventsWaitingResponse = new ConcurrentHashMap<>();
		this.namedCollection = namedCollection;
		lastResetDate = loadResetDateFromPersistence();
	}

	/**
	 * Sets the last reset date used to determine if state:store responses should be ignored
	 * @param lastResetDate timestamp of the reset event
	 */
	void setLastResetDate(long lastResetDate) {
		synchronized (mutex) {
			this.lastResetDate = lastResetDate;

			if (namedCollection != null) {
				namedCollection.setLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, lastResetDate);
			} else {
				Log.debug(LOG_TAG, LOG_SOURCE, "Failed to set last reset date, data store is null.");
			}
		}
	}

	/**
	 * Adds the requestId in the internal {@code sentEventsWaitingResponse} with the associated list of events.
	 * This list should maintain the order of the received events for matching with the response event index.
	 * If the same requestId was stored before, the new list will replace the existing events.
	 *
	 * @param requestId batch request id
	 * @param batchedEvents batched events sent to ExEdge
	 */
	void addWaitingEvents(final String requestId, final List<Event> batchedEvents) {
		if (Utils.isNullOrEmpty(requestId) || batchedEvents == null || batchedEvents.isEmpty()) {
			return;
		}

		final List<WaitingEventContext> eventContexts = new ArrayList<>();

		for (Event e : batchedEvents) {
			eventContexts.add(new WaitingEventContext(e.getUniqueIdentifier(), e.getTimestamp()));
		}

		if (sentEventsWaitingResponse.put(requestId, eventContexts) != null) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Name collision for requestId (%s), events list is overwritten.",
				requestId
			);
		}
	}

	/**
	 * Adds the requestId in the internal {@code sentEventsWaitingResponse} with the associated event.
	 * If the same requestId was stored before, the new list will replace the existing event(s).
	 *
	 * @param requestId batch request id
	 * @param event the event sent to ExEdge
	 */
	void addWaitingEvent(final String requestId, final Event event) {
		final List<Event> list = new ArrayList<>();
		list.add(event);
		addWaitingEvents(requestId, list);
	}

	/**
	 * Remove the requestId in the internal {@code sentEventsWaitingResponse} along with the associated list of events.
	 *
	 * @param requestId batch request id
	 * @return the list of unique event ids associated with the requestId that were removed
	 */
	List<String> removeWaitingEvents(final String requestId) {
		if (Utils.isNullOrEmpty(requestId)) {
			return null;
		}

		synchronized (mutex) {
			final List<WaitingEventContext> temp = sentEventsWaitingResponse.remove(requestId);

			if (temp == null) {
				return null;
			}

			final List<String> eventIds = new ArrayList<>();

			for (WaitingEventContext context : temp) {
				eventIds.add(context.getUuid());
			}

			return eventIds;
		}
	}

	/**
	 * Returns the list of unique event ids associated with the provided requestId or empty if not found.
	 *
	 * @param requestId batch request id
	 * @return the list of unique event ids associated with the requestId that were removed
	 */
	List<String> getWaitingEvents(final String requestId) {
		if (Utils.isNullOrEmpty(requestId)) {
			return Collections.emptyList();
		}

		synchronized (mutex) {
			final List<WaitingEventContext> temp = sentEventsWaitingResponse.get(requestId);

			if (temp == null) {
				return Collections.emptyList();
			}

			final List<String> eventIds = new ArrayList<>();

			for (WaitingEventContext context : temp) {
				eventIds.add(context.getUuid());
			}

			if (!temp.isEmpty()) {
				return new ArrayList<>(eventIds);
			}
		}

		return Collections.emptyList();
	}

	/**
	 * Process the onResponse server response from the network layer and dispatches response events for each
	 * event handle in the {@code jsonResponse}. {@link JSONException}s are handled independently, per event handle.
	 *
	 * If there are any errors for current events, each error will be dispatched independently as a new error response event
	 * and logged with the appropriate log level. These errors are collected by the Edge Network from
	 * various solutions consuming that event, if the processing failed in any way.
	 *
	 * @param jsonResponse the response as a JSON formatted {@link String} to be processed
	 * @param requestId the request id for which the response is handled, to be attached in the response event
	 *                  and used to identify the request event identifiers
	 * @see #processEventHandles(JSONArray, String, boolean)
	 * @see #dispatchEventErrors(JSONArray, boolean, String)
	 */
	void processResponseOnSuccess(final String jsonResponse, final String requestId) {
		if (jsonResponse == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Received null response content for request id (%s)", requestId);
			return;
		}

		JSONObject json;

		try {
			json = new JSONObject(jsonResponse);
			Log.debug(LOG_TAG, LOG_SOURCE, "Received server response:\n%s", json.toString(2));
		} catch (JSONException e) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"The conversion to JSONObject failed for server response: (%s), request id (%s) with error: %s",
				jsonResponse,
				requestId,
				e.getLocalizedMessage()
			);
			return;
		}

		try {
			if (!Utils.isNullOrEmpty(json)) {
				final boolean ignoreStorePayloads = shouldIgnoreStorePayload(requestId);
				JSONArray eventHandleArray = json.getJSONArray(EdgeJson.Response.HANDLE);
				processEventHandles(eventHandleArray, requestId, ignoreStorePayloads);
			}
		} catch (JSONException e) {
			// ok, if there are no handles, attempt to process errors & warnings (see below)
		}

		try {
			if (!Utils.isNullOrEmpty(json)) {
				JSONArray errorsArray = json.getJSONArray(EdgeJson.Response.ERRORS);
				dispatchEventErrors(errorsArray, true, requestId);
			}
		} catch (JSONException e) {
			// ok, ignore if there are no errors
		}

		try {
			if (!Utils.isNullOrEmpty(json)) {
				JSONArray warningsArray = json.getJSONArray(EdgeJson.Response.WARNINGS);
				dispatchEventErrors(warningsArray, false, requestId);
			}
		} catch (JSONException e) {
			// ok, ignore if there are no warnings
		}
	}

	/**
	 * Dispatch errors as error events to the Event Hub and log the error message.
	 * <p>
	 * This method should be called with the server response when the server returned an unrecoverable
	 * error code, in one of the following situations:
	 *
	 * <ul>
	 * <li> generic errors from JAG
	 * <li> generic request errors from Edge Network
	 * </ul>
	 *
	 * @param jsonError error as a JSON formatted {@link String}
	 * @param requestId the request identifier used for logging
	 */
	void processResponseOnError(final String jsonError, final String requestId) {
		if (jsonError == null) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Received null error response content, request id (%s)", requestId);
			return;
		}

		try {
			JSONObject json = new JSONObject(jsonError);
			Log.debug(LOG_TAG, LOG_SOURCE, "Processing server error response: %s", json.toString(2));

			/*
			 * Note: if the Edge Network error doesn't have an eventIndex it means that this error is
			 * a generic request error, otherwise it is an event specific error. There can be multiple
			 * errors returned for the same event
			 */
			if (json.has(EdgeJson.Response.ERRORS)) {
				// this is an error coming from Edge Network, read the error from the errors node
				try {
					JSONArray errorsArray = json.getJSONArray(EdgeJson.Response.ERRORS);
					dispatchEventErrors(errorsArray, true, requestId);
				} catch (JSONException e) {
					// ok, ignore if there are no errors
				}
			} else {
				// generic server error, return the error as is
				Map<String, Object> eventDataResponse = Utils.toMap(json);
				eventDataResponse.put(EdgeConstants.EventDataKey.EDGE_REQUEST_ID, requestId);
				Event responseEvent = new Event.Builder(
					EdgeConstants.EventName.ERROR_RESPONSE_CONTENT,
					EventType.EDGE,
					EventSource.ERROR_RESPONSE_CONTENT
				)
					.setEventData(eventDataResponse)
					.build();
				MobileCore.dispatchEvent(responseEvent);
			}
		} catch (JSONException e) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"The conversion to JSONObject failed for server response: (%s), request id (%s) with error: %s",
				jsonError,
				requestId,
				e.getLocalizedMessage()
			);
		}
	}

	/**
	 * Dispatches each event handle in the provided {@code eventHandleArray} as a separate event through the Event Hub
	 * and processes the store event handles (if any) and invokes the response handlers if any are registered.
	 *
	 * @param eventHandleArray {@link JSONArray} containing all the event handles to be processed
	 * @param requestId the request identifier, used for logging and to identify the request events associated with this response
	 * @param ignoreStorePayloads if true, the store payloads for this response will not be processed
	 */
	private void processEventHandles(
		final JSONArray eventHandleArray,
		final String requestId,
		final boolean ignoreStorePayloads
	) {
		if (Utils.isNullOrEmpty(eventHandleArray)) {
			Log.trace(LOG_TAG, LOG_SOURCE, "Received null/empty event handle array, nothing to handle");
			return;
		}

		int size = eventHandleArray.length();
		Log.trace(LOG_TAG, LOG_SOURCE, "Processing %d event handle(s) for request id: %s", size, requestId);

		for (int i = 0; i < size; i++) {
			JSONObject jsonEventHandle = null;

			try {
				jsonEventHandle = eventHandleArray.getJSONObject(i);
			} catch (JSONException e) {
				Log.trace(
					LOG_TAG,
					LOG_SOURCE,
					"Event handle with index %d was not processed due to JSONException: %s",
					i,
					e.getLocalizedMessage()
				);
			}

			if (jsonEventHandle == null) {
				continue;
			}

			EdgeEventHandle handle = new EdgeEventHandle(jsonEventHandle);

			if (ignoreStorePayloads) {
				Log.debug(
					LOG_TAG,
					LOG_SOURCE,
					"Identities were reset recently, ignoring state:store payload for request with id: " + requestId
				);
			} else {
				if (EdgeJson.Response.EventHandle.Store.TYPE.equals(handle.getType())) {
					handleStoreEventHandle(handle);
				} else if (EdgeJson.Response.EventHandle.LocationHint.TYPE.equals(handle.getType())) {
					handleLocationHintEventHandle(handle);
				}
			}

			String requestEventId = extractRequestEventId(handle.getEventIndex(), requestId);

			// Dispatched events add the event and request IDs to the data, so use a copy of the data
			// so the IDs are not in the data when invoking the response callback
			dispatchEventResponse(handle.toMap(), requestId, requestEventId, handle.getType());
			CompletionCallbacksManager.getInstance().eventHandleReceived(requestEventId, handle);
		}
	}

	/**
	 * Dispatches a new event with the provided {@code eventData} as responseContent or as errorResponseContent based on the {@code isError} setting
	 * @param eventData Event data to be dispatched, should not be empty
	 * @param requestId The request identifier associated with this response event, used for logging
	 * @param isError indicates if this should be dispatched as an error or regular response content event
	 * @param eventSource an optional {@link String} to be used as the event source.
	 *        If {@code eventSource} is nil, either {@link EventSource#ERROR_RESPONSE_CONTENT} or
	 *        {@link EventSource#RESPONSE_CONTENT} is used for the event source depending on {@code isError}.
	 */
	private void dispatchResponse(
		final Map<String, Object> eventData,
		final String requestId,
		final boolean isError,
		final String eventSource
	) {
		if (Utils.isNullOrEmpty(eventData)) {
			return;
		}

		String source = isError ? EventSource.ERROR_RESPONSE_CONTENT : EventSource.RESPONSE_CONTENT;

		if (!Utils.isNullOrEmpty(eventSource)) {
			source = eventSource;
		}

		Event responseEvent = new Event.Builder(
			isError ? EdgeConstants.EventName.ERROR_RESPONSE_CONTENT : EdgeConstants.EventName.RESPONSE_CONTENT,
			EventType.EDGE,
			source
		)
			.setEventData(eventData)
			.build();

		MobileCore.dispatchEvent(responseEvent);
	}

	/**
	 * 	Extracts the request event identifiers paired with this event handle based on the index.
	 * 	If no matches found or the event handle index is less than 0, this method returns null.
	 *
	 * @param eventIndex the {@link EdgeEventHandle} event index
	 * @param requestId request ID used to fetch the waiting events, if any
	 * @return the event ID for which this event handle was received, or null if not found
	 */
	private String extractRequestEventId(final int eventIndex, final String requestId) {
		List<String> requestEventIdsList = getWaitingEvents(requestId);

		if (eventIndex >= 0 && eventIndex < requestEventIdsList.size()) {
			return requestEventIdsList.get(eventIndex);
		}

		return null;
	}

	private void dispatchEventResponse(
		final Map<String, Object> eventData,
		final String requestId,
		final String eventId,
		final String eventSource
	) {
		addEventAndRequestIdToData(eventData, requestId, eventId);
		dispatchResponse(eventData, requestId, false, eventSource);
	}

	/**
	 * Extracts the event unique id corresponding to the eventIndex from {@code eventData} and mutates the provided {@code eventData}
	 * by attaching this id to it along with the requestId.
	 *
	 * @param eventData response coming from server, which will be enhanced with eventUniqueId
	 * @param requestId current request id to be added to data
	 * @param eventId the request event id associated with this data
	 */
	private void addEventAndRequestIdToData(
		final Map<String, Object> eventData,
		final String requestId,
		final String eventId
	) {
		eventData.put(EdgeConstants.EventDataKey.EDGE_REQUEST_ID, requestId);

		if (!Utils.isNullOrEmpty(eventId)) {
			eventData.put(EdgeConstants.EventDataKey.REQUEST_EVENT_ID, eventId);
		}
	}

	/**
	 * If handle is of type "state:store" persist it to data store
	 * @param handle the event handle
	 */
	private void handleStoreEventHandle(final EdgeEventHandle handle) {
		if (handle == null || Utils.isNullOrEmpty(handle.getType())) {
			return;
		}

		if (EdgeJson.Response.EventHandle.Store.TYPE.equals(handle.getType())) {
			StoreResponsePayloadManager payloadManager = new StoreResponsePayloadManager(namedCollection);
			payloadManager.saveStorePayloads(handle.getPayload());
		}
	}

	/**
	 * If handle is of type "locationHint:result", persist to data store
	 * @param handle the event handle
	 */
	private void handleLocationHintEventHandle(final EdgeEventHandle handle) {
		if (handle == null || Utils.isNullOrEmpty(handle.getType())) {
			return;
		}

		if (!EdgeJson.Response.EventHandle.LocationHint.TYPE.equals(handle.getType())) {
			return;
		}

		for (Map<String, Object> locationHint : handle.getPayload()) {
			String scope = (String) locationHint.get(EdgeJson.Response.EventHandle.LocationHint.SCOPE);

			if (EdgeJson.Response.EventHandle.LocationHint.EDGE_NETWORK.equals(scope)) {
				try {
					String hint = (String) locationHint.get(EdgeJson.Response.EventHandle.LocationHint.HINT);
					Integer ttlSeconds = (Integer) locationHint.get(
						EdgeJson.Response.EventHandle.LocationHint.TTL_SECONDS
					);

					if (!StringUtils.isNullOrEmpty(hint) && ttlSeconds != null && edgeStateCallback != null) {
						edgeStateCallback.setLocationHint(hint, ttlSeconds);
					}
				} catch (ClassCastException e) {
					Log.warning(
						LOG_TAG,
						LOG_SOURCE,
						"Failed to parse 'locationHint:result' for scope 'EdgeNetwork': %s",
						e.getLocalizedMessage()
					);
				}

				break;
			}
		}
	}

	/**
	 * Iterates over the provided {@code errorsArray} and dispatches a new error event to the Event Hub.
	 * It also logs each error/warning json with the log level set based of {@code isError}.
	 *
	 * @param errorsArray {@link JSONArray} containing all the event errors to be processed
	 * @param isError boolean indicating if this is an error message
	 * @param requestId the event request identifier, used for logging
	 */
	private void dispatchEventErrors(final JSONArray errorsArray, final boolean isError, final String requestId) {
		if (Utils.isNullOrEmpty(errorsArray)) {
			Log.trace(LOG_TAG, LOG_SOURCE, "Received null/empty errors array, nothing to handle");
			return;
		}

		int size = errorsArray.length();
		Log.trace(LOG_TAG, LOG_SOURCE, "Processing %d error(s) for request id: %s", size, requestId);

		for (int i = 0; i < size; i++) {
			JSONObject currentError = null;

			try {
				currentError = errorsArray.getJSONObject(i);
			} catch (JSONException e) {
				Log.trace(
					LOG_TAG,
					LOG_SOURCE,
					"Event error with index %d was not processed due to JSONException: %s",
					i,
					e.getLocalizedMessage()
				);
			}

			// Convert json object to Map<String, Object> to be able to pass it as Event data
			Map<String, Object> eventDataResponse = Utils.toMap(currentError);

			if (Utils.isNullOrEmpty(eventDataResponse)) {
				continue;
			}

			// if eventIndex not found in the response, it fallbacks to 0 as per Edge Network spec
			int eventIndex = currentError.optInt(EdgeJson.Response.EventHandle.EVENT_INDEX, 0);
			String eventId = extractRequestEventId(eventIndex, requestId);

			logErrorMessage(currentError, isError, requestId);

			// set eventRequestId and edge requestId on the response event and dispatch data
			addEventAndRequestIdToData(eventDataResponse, requestId, eventId);
			dispatchResponse(eventDataResponse, requestId, true, null);
		}
	}

	/**
	 * Logs the provided {@code error} message with the log level set based of {@code isError}, as follows:
	 * <ul>
	 * <li> If isError is true, the message is logged as error.
	 * <li> If isError is false, the message is logged as warning.
	 * </ul>
	 *
	 * @param error {@link JSONObject} containing the event error/warning coming from server
	 * @param isError boolean indicating if this is an error message
	 * @param requestId the event request identifier, used for logging
	 */
	private void logErrorMessage(final JSONObject error, final boolean isError, final String requestId) {
		final LoggingMode loggingMode = isError ? LoggingMode.ERROR : LoggingMode.WARNING;
		String errorToLog;

		try {
			errorToLog = error.toString(2);
		} catch (JSONException e) {
			errorToLog = error.toString();
		}

		if (isError) {
			Log.error(
				LOG_TAG,
				LOG_SOURCE,
				"Received event error for request id (%s), error details:\n %s",
				requestId,
				errorToLog
			);
		} else {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Received event error for request id (%s), error details:\n %s",
				requestId,
				errorToLog
			);
		}
	}

	/**
	 * Determines if the store payload response for a given request id should be ignored.
	 * A store payload should be ignored when a reset happened and the persisted state store was removed while processing a network request, in order to avoid an identity overwrite.
	 * The first network request after reset will update the state store with the new information.
	 * @param requestId the request id
	 * @return true if the store payload responses for requestId should be ignored
	 */
	private boolean shouldIgnoreStorePayload(final String requestId) {
		if (requestId == null) {
			return false;
		}

		synchronized (mutex) {
			final List<WaitingEventContext> contexts = sentEventsWaitingResponse.get(requestId);

			if (contexts == null || contexts.isEmpty()) {
				return false;
			}

			final WaitingEventContext firstContext = contexts.get(0);
			return firstContext.getTimestamp() < lastResetDate;
		}
	}

	/**
	 * Loads the reset date from persistence, if not found returns 0
	 * @return the {@link Long} representing the last known reset timestamp (ms), 0 if not found
	 */
	private long loadResetDateFromPersistence() {
		if (namedCollection == null) {
			return 0;
		}

		return namedCollection.getLong(EdgeConstants.DataStoreKeys.RESET_IDENTITIES_DATE, 0);
	}

	/**
	 * Stores a subset of {@link Event} information
	 * required for matching the request events with the response payloads. See the usages of {@code sentEventsWaitingResponse}
	 */
	private class WaitingEventContext {

		private final String uuid;
		private final long timestamp;

		/**
		 * Creates a new context with the {@link Event} id and timestamp
		 * @param uuid unique identifier of the {@code Event}
		 * @param timestamp timestamp of the {@code Event} represented as milliseconds since 1970
		 */
		private WaitingEventContext(final String uuid, final long timestamp) {
			this.uuid = uuid;
			this.timestamp = timestamp;
		}

		/**
		 * @return current unique identifier for this context
		 */
		String getUuid() {
			return uuid;
		}

		/**
		 * @return current timestamp for this context represented as milliseconds since 1970
		 */
		long getTimestamp() {
			return timestamp;
		}
	}
}
