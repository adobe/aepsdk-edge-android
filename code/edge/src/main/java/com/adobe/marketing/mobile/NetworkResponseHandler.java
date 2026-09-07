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
import com.adobe.marketing.mobile.util.DataReader;
import com.adobe.marketing.mobile.util.DataReaderException;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
	private final ConcurrentMap<String, List<Event>> sentEventsWaitingResponse;
	// Early per-event completion progress, keyed by requestId: number of leading events already
	// completed (monotonic; guarantees each event completes exactly once). A batched event's completion
	// fires as soon as a response fragment for a higher eventIndex is observed — all lower-index events
	// are then known complete — instead of waiting for the whole batch's stream to close. The highest
	// index and anything still pending complete at stream close. Single events / consent / batch-of-1
	// simply complete at stream close, since no higher index is ever observed to advance the boundary.
	private final ConcurrentMap<String, Integer> nextCompletionIndex = new ConcurrentHashMap<>();
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
		if (StringUtils.isNullOrEmpty(requestId) || batchedEvents == null || batchedEvents.isEmpty()) {
			return;
		}

		if (sentEventsWaitingResponse.put(requestId, batchedEvents) != null) {
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
	 * @return the list of unique events associated with the requestId that were removed, or null
	 * if no events are associated with the {@code requestId}
	 */
	List<Event> removeWaitingEvents(final String requestId) {
		if (StringUtils.isNullOrEmpty(requestId)) {
			return null;
		}

		synchronized (mutex) {
			nextCompletionIndex.remove(requestId);
			return sentEventsWaitingResponse.remove(requestId);
		}
	}

	/**
	 * Returns the list of unique event ids associated with the provided requestId or empty if not found.
	 *
	 * @param requestId batch request id
	 * @return the list of unique event ids associated with the requestId that were removed
	 */
	List<String> getWaitingEvents(final String requestId) {
		if (StringUtils.isNullOrEmpty(requestId)) {
			return Collections.emptyList();
		}

		synchronized (mutex) {
			final List<Event> temp = sentEventsWaitingResponse.get(requestId);

			if (temp == null) {
				return Collections.emptyList();
			}

			final List<String> eventIds = new ArrayList<>();

			for (Event event : temp) {
				eventIds.add(event.getUniqueIdentifier());
			}

			return eventIds;
		}
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
	 * @see #processResponseItems(JSONArray, JSONArray, JSONArray, String, boolean)
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

		if (!JSONUtils.isNullOrEmpty(json)) {
			final boolean ignoreStorePayloads = shouldIgnoreStorePayload(requestId);
			final JSONArray eventHandleArray = json.optJSONArray(EdgeJson.Response.HANDLE);
			final JSONArray errorsArray = json.optJSONArray(EdgeJson.Response.ERRORS);
			final JSONArray warningsArray = json.optJSONArray(EdgeJson.Response.WARNINGS);
			processResponseItems(eventHandleArray, errorsArray, warningsArray, requestId, ignoreStorePayloads);
		}
		// Early per-event completion happens inline in processResponseItems: the handles, errors and
		// warnings in this record are processed together in ascending eventIndex order, so observing
		// eventIndex M sweeps completions for events 0..M-1 only after every M-and-below handle/error/
		// warning has been recorded. The highest index seen (and anything still pending) completes at
		// stream close in processResponseOnComplete.
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
				JSONArray errorsArray = new JSONArray();
				errorsArray.put(json);
				dispatchEventErrors(errorsArray, true, requestId);
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
	 * Process the on complete response from the network layer by unregistering request callbacks for
	 * each event and dispatching completion events for the paired events which requested one.
	 *
	 * @param requestId the request id used to identify the request events
	 */
	void processResponseOnComplete(final String requestId) {
		// Capture progress BEFORE removeWaitingEvents clears the per-request state.
		final int alreadyCompleted = completedCountFor(requestId);

		// removeWaitingEvents returns the full ordered event list for this request and clears state.
		List<Event> removedWaitingEvents = removeWaitingEvents(requestId);
		if (removedWaitingEvents == null) {
			return;
		}

		// Complete everything not already early-completed (the highest index plus anything still
		// pending). For single events / consent nothing was early-completed → the whole request
		// completes here at stream close.
		for (int i = alreadyCompleted; i < removedWaitingEvents.size(); i++) {
			completeEvent(requestId, removedWaitingEvents.get(i));
		}
	}

	/**
	 * @return the number of leading events already early-completed for {@code requestId}, or 0 if none.
	 */
	private int completedCountFor(final String requestId) {
		final Integer completed = nextCompletionIndex.get(requestId);
		return completed == null ? 0 : completed;
	}

	/**
	 * Completes a single waiting {@code event}: unregisters its response callback (firing the
	 * internal {@link EdgeCallback} onComplete/onError with the handles/errors accumulated for it) and,
	 * if the event requested it via {@code request.sendCompletion}, dispatches its {@code
	 * CONTENT_COMPLETE} response event. This is the per-event completion action, called either early
	 * (index-advance) or at stream close.
	 *
	 * @param requestId the batch request id, attached to the completion event data
	 * @param event the waiting event to complete
	 */
	private void completeEvent(final String requestId, final Event event) {
		CompletionCallbacksManager.getInstance().unregisterCallback(event.getUniqueIdentifier());

		if (sendCompletionRequested(event)) {
			// send completion event
			Map<String, Object> eventData = new HashMap<>();
			addEventAndRequestIdToData(eventData, requestId, null);

			Event responseEvent = new Event.Builder(
				EdgeConstants.EventName.CONTENT_COMPLETE,
				EventType.EDGE,
				EventSource.CONTENT_COMPLETE
			)
				.setEventData(eventData)
				.inResponseToEvent(event)
				.build();

			MobileCore.dispatchEvent(responseEvent);
		}
	}

	/**
	 * Completes every waiting event for {@code requestId} whose position is at or after the current
	 * completion boundary ({@code nextCompletionIndex}) and strictly below {@code exclusiveUpperBound},
	 * advancing the boundary so each event completes exactly once. Called with a handle/error/warning's
	 * {@code eventIndex}: observing index M means events 0..M-1 have no more data coming (Konductor
	 * streams fragments grouped by event, in index order), so they can complete.
	 *
	 * <p>If {@code exclusiveUpperBound} is below the boundary already reached, a lower index arrived
	 * after those events were completed — this violates the assumed grouping/ordering; it is logged at
	 * WARNING and ignored (the boundary is never moved backwards, so no event completes twice).
	 *
	 * <p>Events to complete are collected under {@code mutex}; the actual completion (which dispatches
	 * events) is performed outside the lock.
	 *
	 * @param requestId the batch request id
	 * @param exclusiveUpperBound complete events with index in {@code [nextCompletionIndex,
	 *     min(exclusiveUpperBound, size))}
	 */
	private void sweepCompletions(final String requestId, final int exclusiveUpperBound) {
		final List<Event> toComplete = new ArrayList<>();
		synchronized (mutex) {
			final List<Event> events = sentEventsWaitingResponse.get(requestId);
			if (events == null) {
				return;
			}
			int next = nextCompletionIndex.containsKey(requestId) ? nextCompletionIndex.get(requestId) : 0;
			if (exclusiveUpperBound < next) {
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Unexpected response ordering: eventIndex %d arrived for request id (%s) after events through index %d were already completed. Not re-completing; investigate response grouping/ordering.",
					exclusiveUpperBound,
					requestId,
					next - 1
				);
				return;
			}
			final int limit = Math.min(exclusiveUpperBound, events.size());
			while (next < limit) {
				toComplete.add(events.get(next));
				next++;
			}
			nextCompletionIndex.put(requestId, next);
		}

		for (final Event event : toComplete) {
			completeEvent(requestId, event);
		}
	}

	/**
	 * Determines whether a completion event has been requested based on the boolean value of
	 * {@code request.sendCompletion} in the provided {@code event}.
	 *
	 * @param event The {@code Event} whose data is checked for a completion event request.
	 * @return true if the {@code event} is requesting a completion event; false otherwise.
	 */
	private boolean sendCompletionRequested(final Event event) {
		Map<String, Object> eventData = event.getEventData();
		Map<String, Object> requestProperties = DataReader.optTypedMap(
			Object.class,
			eventData,
			EdgeConstants.EventDataKeys.Request.KEY,
			null
		);
		return DataReader.optBoolean(requestProperties, EdgeConstants.EventDataKeys.Request.SEND_COMPLETION, false);
	}

	/** One handle/error/warning of a success response, tagged with its {@code eventIndex} and kind. */
	private static final class ResponseItem {

		static final int HANDLE = 0;
		static final int ERROR = 1;
		static final int WARNING = 2;

		final int eventIndex;
		final int kind;
		final EdgeEventHandle handle; // set when kind == HANDLE
		final Map<String, Object> errorData; // set when kind == ERROR/WARNING
		final JSONObject rawError; // set when kind == ERROR/WARNING

		private ResponseItem(
			final int eventIndex,
			final int kind,
			final EdgeEventHandle handle,
			final Map<String, Object> errorData,
			final JSONObject rawError
		) {
			this.eventIndex = eventIndex;
			this.kind = kind;
			this.handle = handle;
			this.errorData = errorData;
			this.rawError = rawError;
		}

		static ResponseItem forHandle(final EdgeEventHandle handle) {
			return new ResponseItem(handle.getEventIndex(), HANDLE, handle, null, null);
		}

		static ResponseItem forError(
			final int eventIndex,
			final boolean isError,
			final Map<String, Object> errorData,
			final JSONObject rawError
		) {
			return new ResponseItem(eventIndex, isError ? ERROR : WARNING, null, errorData, rawError);
		}
	}

	/**
	 * Processes the handles, errors and warnings of one success response together, in ascending
	 * {@code eventIndex} order, so that for each index every handle/error/warning at that index is
	 * recorded before the completion boundary advances past it. This guarantees a lower-index error is
	 * recorded before a higher-index handle completes the lower event (so {@code onError} is never
	 * dropped and never leaks), and keeps the completion sweep monotonic within a record (no spurious
	 * out-of-order warnings). No-{@code eventIndex} global handles are processed first (side effects
	 * applied up front); no-{@code eventIndex} broadcast errors/warnings are processed last (they apply
	 * to the whole request and do not advance completion).
	 *
	 * @param eventHandleArray handles to process (may be null/empty)
	 * @param errorsArray errors to process (may be null/empty)
	 * @param warningsArray warnings to process (may be null/empty)
	 * @param requestId the request identifier, used to identify the request events for this response
	 * @param ignoreStorePayloads if true, {@code state:store} payloads for this response are not persisted
	 */
	private void processResponseItems(
		final JSONArray eventHandleArray,
		final JSONArray errorsArray,
		final JSONArray warningsArray,
		final String requestId,
		final boolean ignoreStorePayloads
	) {
		final List<EdgeEventHandle> noIndexHandles = new ArrayList<>();
		final List<ResponseItem> indexedItems = new ArrayList<>();
		final List<ResponseItem> noIndexErrors = new ArrayList<>();

		if (!JSONUtils.isNullOrEmpty(eventHandleArray)) {
			for (int i = 0; i < eventHandleArray.length(); i++) {
				final JSONObject jsonEventHandle = eventHandleArray.optJSONObject(i);
				if (jsonEventHandle == null) {
					continue;
				}
				final EdgeEventHandle handle = new EdgeEventHandle(jsonEventHandle);
				if (handle.getEventIndex() == EdgeEventHandle.ABSENT_EVENT_INDEX) {
					noIndexHandles.add(handle);
				} else {
					indexedItems.add(ResponseItem.forHandle(handle));
				}
			}
		}

		collectErrorItems(errorsArray, true, indexedItems, noIndexErrors);
		collectErrorItems(warningsArray, false, indexedItems, noIndexErrors);

		// 1) Global (no-index) handles first — apply side effects and dispatch up front.
		for (final EdgeEventHandle handle : noIndexHandles) {
			processSingleHandle(handle, requestId, ignoreStorePayloads);
		}

		// 2) Indexed items in ascending index order (handle < error < warning within an index). Sweep to
		// each index (completing lower events, whose data is now fully recorded) before dispatching it.
		Collections.sort(
			indexedItems,
			(a, b) ->
				a.eventIndex != b.eventIndex
					? Integer.compare(a.eventIndex, b.eventIndex)
					: Integer.compare(a.kind, b.kind)
		);
		for (final ResponseItem item : indexedItems) {
			sweepCompletions(requestId, item.eventIndex);
			if (item.kind == ResponseItem.HANDLE) {
				processSingleHandle(item.handle, requestId, ignoreStorePayloads);
			} else {
				processIndexedError(
					item.errorData,
					item.rawError,
					item.kind == ResponseItem.ERROR,
					item.eventIndex,
					requestId
				);
			}
		}

		// 3) No-index broadcast errors/warnings last — whole-request, do not advance completion.
		for (final ResponseItem item : noIndexErrors) {
			processBroadcastError(item.errorData, item.rawError, item.kind == ResponseItem.ERROR, requestId);
		}
	}

	/**
	 * Parses an errors/warnings array into {@link ResponseItem}s, splitting indexed entries (added to
	 * {@code indexedItems}) from no-{@code eventIndex} broadcast entries (added to {@code noIndexErrors}).
	 */
	private void collectErrorItems(
		final JSONArray errorsArray,
		final boolean isError,
		final List<ResponseItem> indexedItems,
		final List<ResponseItem> noIndexErrors
	) {
		if (JSONUtils.isNullOrEmpty(errorsArray)) {
			return;
		}
		for (int i = 0; i < errorsArray.length(); i++) {
			final JSONObject currentError = errorsArray.optJSONObject(i);
			if (currentError == null) {
				continue;
			}
			Map<String, Object> eventDataResponse;
			try {
				eventDataResponse = JSONUtils.toMap(currentError);
			} catch (JSONException e) {
				Log.trace(
					LOG_TAG,
					LOG_SOURCE,
					"%s entry at index %d could not be parsed (JSONException): %s",
					isError ? "Error" : "Warning",
					i,
					e.getLocalizedMessage()
				);
				continue;
			}
			if (MapUtils.isNullOrEmpty(eventDataResponse)) {
				continue;
			}
			final int eventIndex = readEventIndex(eventDataResponse);
			final ResponseItem item = ResponseItem.forError(eventIndex, isError, eventDataResponse, currentError);
			if (eventIndex == EdgeEventHandle.ABSENT_EVENT_INDEX) {
				noIndexErrors.add(item);
			} else {
				indexedItems.add(item);
			}
		}
	}

	/** Reads the {@code report.eventIndex} of an error/warning; absent → {@link EdgeEventHandle#ABSENT_EVENT_INDEX}. */
	private static int readEventIndex(final Map<String, Object> eventDataResponse) {
		final Map<String, Object> report = DataReader.optTypedMap(
			Object.class,
			eventDataResponse,
			EdgeJson.Response.EventHandle.REPORT,
			null
		);
		final boolean hasEventIndex = report != null && report.containsKey(EdgeJson.Response.EventHandle.EVENT_INDEX);
		return hasEventIndex
			? DataReader.optInt(report, EdgeJson.Response.EventHandle.EVENT_INDEX, EdgeEventHandle.ABSENT_EVENT_INDEX)
			: EdgeEventHandle.ABSENT_EVENT_INDEX;
	}

	/**
	 * Dispatches a single event handle as an event through the Event Hub, applies its store/locationHint
	 * side effect if applicable, and records it against its originating request event. Does not advance
	 * the completion boundary — the caller sweeps in index order.
	 *
	 * @param handle the event handle to process
	 * @param requestId the request identifier
	 * @param ignoreStorePayloads if true, {@code state:store} payloads are not persisted
	 */
	private void processSingleHandle(
		final EdgeEventHandle handle,
		final String requestId,
		final boolean ignoreStorePayloads
	) {
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

		// Resolve the originating event for this handle using the routing policy:
		// - Indexed handle  → route to the specific event at that position.
		// - No eventIndex, 1 waiting event → unambiguous; route to it (backward compat).
		// - No eventIndex, global handle type (state:store / locationHint:result)
		//   → broadcast: apply side-effect globally, dispatch with null parentId.
		// - No eventIndex, other type, batch > 1 → log and skip per-event attribution.
		final String requestEventId;
		final int handleEventIndex = handle.getEventIndex();

		if (handleEventIndex != EdgeEventHandle.ABSENT_EVENT_INDEX) {
			requestEventId = extractRequestEventId(handleEventIndex, requestId);
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Handle type '%s' eventIndex=%d → event id: %s (requestId: %s)",
				handle.getType(),
				handleEventIndex,
				requestEventId,
				requestId
			);
		} else {
			final List<String> waitingIds = getWaitingEvents(requestId);
			if (waitingIds.size() == 1) {
				requestEventId = waitingIds.get(0);
				Log.trace(
					LOG_TAG,
					LOG_SOURCE,
					"Handle type '%s' has no eventIndex; single-event request → routed to event id: %s (requestId: %s)",
					handle.getType(),
					requestEventId,
					requestId
				);
			} else if (isGlobalHandleType(handle.getType())) {
				requestEventId = null;
				Log.trace(
					LOG_TAG,
					LOG_SOURCE,
					"Handle type '%s' is a global handle (no eventIndex) → broadcast with null parentId (requestId: %s)",
					handle.getType(),
					requestId
				);
			} else {
				requestEventId = null;
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Handle type '%s' has no eventIndex in a batch of %d events and is not a known global type — skipping per-event attribution (requestId: %s)",
					handle.getType(),
					waitingIds.size(),
					requestId
				);
			}
		}

		// Dispatched events add the event and request IDs to the data, so use a copy of the data
		// so the IDs are not in the data when invoking the response callback
		dispatchEventResponse(handle.toMap(), requestId, requestEventId, handle.getType());
		CompletionCallbacksManager.getInstance().eventHandleReceived(requestEventId, handle);
	}

	/**
	 * Dispatches a new event with the provided {@code eventData} as responseContent or as errorResponseContent based on the {@code isError} setting
	 * @param eventData Event data to be dispatched, should not be empty
	 * @param parentId The triggering parent event identifier associated with this response event
	 * @param isError indicates if this should be dispatched as an error or regular response content event
	 * @param eventSource an optional {@link String} to be used as the event source.
	 *        If {@code eventSource} is nil, either {@link EventSource#ERROR_RESPONSE_CONTENT} or
	 *        {@link EventSource#RESPONSE_CONTENT} is used for the event source depending on {@code isError}.
	 */
	private void dispatchResponse(
		final Map<String, Object> eventData,
		final String parentId,
		final boolean isError,
		final String eventSource
	) {
		if (MapUtils.isNullOrEmpty(eventData)) {
			return;
		}

		String source = isError ? EventSource.ERROR_RESPONSE_CONTENT : EventSource.RESPONSE_CONTENT;

		if (!StringUtils.isNullOrEmpty(eventSource)) {
			source = eventSource;
		}

		Event responseEvent = new Event.Builder(
			isError ? EdgeConstants.EventName.ERROR_RESPONSE_CONTENT : EdgeConstants.EventName.RESPONSE_CONTENT,
			EventType.EDGE,
			source
		)
			.setEventData(eventData)
			.setParentId(parentId)
			.build();

		if (responseEvent.getParentID() == null) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"dispatchResponse - Parent Event is null, dispatching response event without chained parent."
			);
		}

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
		final List<String> requestEventIdsList = getWaitingEvents(requestId);

		if (eventIndex >= 0 && eventIndex < requestEventIdsList.size()) {
			return requestEventIdsList.get(eventIndex);
		}

		if (eventIndex != EdgeEventHandle.ABSENT_EVENT_INDEX) {
			// Diagnostic only — logged at trace level to avoid polluting the warning channel
			// (matches iOS, which silently returns nil for an out-of-range index).
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"eventIndex %d is out of range for waiting events list of size %d (requestId: %s)",
				eventIndex,
				requestEventIdsList.size(),
				requestId
			);
		}

		return null;
	}

	/**
	 * Returns true for handle types that are session/batch scoped and carry no {@code eventIndex}
	 * by design — these are broadcast globally rather than attributed to a specific event.
	 */
	private boolean isGlobalHandleType(final String handleType) {
		return (
			EdgeJson.Response.EventHandle.Store.TYPE.equals(handleType) ||
			EdgeJson.Response.EventHandle.LocationHint.TYPE.equals(handleType)
		);
	}

	private void dispatchEventResponse(
		final Map<String, Object> eventData,
		final String requestId,
		final String eventId,
		final String eventSource
	) {
		addEventAndRequestIdToData(eventData, requestId, eventId);
		dispatchResponse(eventData, eventId, false, eventSource);
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
		eventData.put(EdgeConstants.EventDataKeys.EDGE_REQUEST_ID, requestId);

		if (!StringUtils.isNullOrEmpty(eventId)) {
			eventData.put(EdgeConstants.EventDataKeys.REQUEST_EVENT_ID, eventId);
		}
	}

	/**
	 * If handle is of type "state:store" persist it to data store
	 * @param handle the event handle
	 */
	private void handleStoreEventHandle(final EdgeEventHandle handle) {
		if (handle == null || StringUtils.isNullOrEmpty(handle.getType())) {
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
		if (handle == null || StringUtils.isNullOrEmpty(handle.getType())) {
			return;
		}

		if (!EdgeJson.Response.EventHandle.LocationHint.TYPE.equals(handle.getType())) {
			return;
		}

		for (Map<String, Object> locationHint : handle.getPayload()) {
			String scope = DataReader.optString(locationHint, EdgeJson.Response.EventHandle.LocationHint.SCOPE, null);

			if (EdgeJson.Response.EventHandle.LocationHint.EDGE_NETWORK.equals(scope)) {
				try {
					String hint = DataReader.getString(locationHint, EdgeJson.Response.EventHandle.LocationHint.HINT);
					int ttlSeconds = DataReader.getInt(
						locationHint,
						EdgeJson.Response.EventHandle.LocationHint.TTL_SECONDS
					);

					if (!StringUtils.isNullOrEmpty(hint) && edgeStateCallback != null) {
						edgeStateCallback.setLocationHint(hint, ttlSeconds);
					}
				} catch (DataReaderException e) {
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
		if (JSONUtils.isNullOrEmpty(errorsArray)) {
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"Received null/empty %s array, nothing to handle",
				isError ? "errors" : "warnings"
			);
			return;
		}

		int size = errorsArray.length();
		Log.trace(
			LOG_TAG,
			LOG_SOURCE,
			"Processing %d %s(s) for request id: %s",
			size,
			isError ? "error" : "warning",
			requestId
		);

		for (int i = 0; i < size; i++) {
			JSONObject currentError = null;
			Map<String, Object> eventDataResponse = null;

			try {
				currentError = errorsArray.getJSONObject(i);
				eventDataResponse = JSONUtils.toMap(currentError);
			} catch (JSONException e) {
				Log.trace(
					LOG_TAG,
					LOG_SOURCE,
					"%s entry at index %d could not be parsed (JSONException): %s",
					isError ? "Error" : "Warning",
					i,
					e.getLocalizedMessage()
				);
			}
			if (MapUtils.isNullOrEmpty(eventDataResponse)) {
				continue;
			}

			final int eventIndex = readEventIndex(eventDataResponse);
			if (eventIndex != EdgeEventHandle.ABSENT_EVENT_INDEX) {
				// Complete any lower-indexed events still pending BEFORE dispatching this error's data.
				sweepCompletions(requestId, eventIndex);
				processIndexedError(eventDataResponse, currentError, isError, eventIndex, requestId);
			} else {
				processBroadcastError(eventDataResponse, currentError, isError, requestId);
			}
		}
	}

	/**
	 * Records and dispatches one indexed error/warning: logs it, routes its response event to the
	 * originating request event, and accumulates the error for that event's {@code onError}. Does not
	 * advance the completion boundary — the caller sweeps in index order.
	 */
	private void processIndexedError(
		final Map<String, Object> eventDataResponse,
		final JSONObject currentError,
		final boolean isError,
		final int eventIndex,
		final String requestId
	) {
		logErrorMessage(currentError, isError, requestId);

		final EdgeEventError edgeEventError = isError ? buildEdgeEventError(eventDataResponse) : null;
		final String eventId = extractRequestEventId(eventIndex, requestId);
		Log.trace(
			LOG_TAG,
			LOG_SOURCE,
			"%s eventIndex=%d → event id: %s (requestId: %s)",
			isError ? "Error" : "Warning",
			eventIndex,
			eventId,
			requestId
		);
		final Map<String, Object> copy = new HashMap<>(eventDataResponse);
		removeEventIndexFromReport(copy);
		addEventAndRequestIdToData(copy, requestId, eventId);
		// Both errors and warnings dispatch on the error-response channel (iOS parity);
		// the isError flag only controls log level and onError delivery, not the channel.
		dispatchResponse(copy, eventId, true, null);
		if (edgeEventError != null && !StringUtils.isNullOrEmpty(eventId)) {
			CompletionCallbacksManager.getInstance().eventErrorReceived(eventId, edgeEventError);
		}
	}

	/**
	 * Records and dispatches one no-{@code eventIndex} (root/broadcast) error/warning. A root-level
	 * error means the whole request failed atomically and does not advance completion; it is delivered
	 * to every waiting event (or as a null-parent broadcast when none are registered).
	 */
	private void processBroadcastError(
		final Map<String, Object> eventDataResponse,
		final JSONObject currentError,
		final boolean isError,
		final String requestId
	) {
		logErrorMessage(currentError, isError, requestId);

		final EdgeEventError edgeEventError = isError ? buildEdgeEventError(eventDataResponse) : null;

		// A no-eventIndex error/warning applies to the whole request. Per the Konductor team, a
		// root-level error means the whole batch failed atomically — it should not arrive after events
		// already streamed successful per-event data. If some events already early-completed, flag it.
		final Integer completed;
		synchronized (mutex) {
			completed = nextCompletionIndex.get(requestId);
		}
		if (completed != null && completed > 0) {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				"Received a no-eventIndex %s for request id (%s) after %d event(s) were already early-completed; those events did not receive it. Root-level errors are expected only when the whole batch fails — investigate.",
				isError ? "error" : "warning",
				requestId,
				completed
			);
		}

		final List<String> waitingIds = getWaitingEvents(requestId);
		if (waitingIds.isEmpty()) {
			// No registered events for this requestId — dispatch as broadcast (null parentId).
			// This preserves backward compatibility for callers that do not register waiting events.
			final Map<String, Object> copy = new HashMap<>(eventDataResponse);
			removeEventIndexFromReport(copy);
			addEventAndRequestIdToData(copy, requestId, null);
			dispatchResponse(copy, null, true, null);
		} else {
			// Deliver one error event per waiting event. For N==1 this naturally routes to event[0],
			// preserving the single-event contract. For N>1 every caller learns their event failed.
			Log.trace(
				LOG_TAG,
				LOG_SOURCE,
				"%s has no eventIndex → dispatching to all %d waiting event(s) (requestId: %s)",
				isError ? "Error" : "Warning",
				waitingIds.size(),
				requestId
			);
			for (final String eventId : waitingIds) {
				final Map<String, Object> copy = new HashMap<>(eventDataResponse);
				removeEventIndexFromReport(copy);
				addEventAndRequestIdToData(copy, requestId, eventId);
				dispatchResponse(copy, eventId, true, null);
				if (edgeEventError != null) {
					CompletionCallbacksManager.getInstance().eventErrorReceived(eventId, edgeEventError);
				}
			}
		}
	}

	/**
	 * Removes the eventIndex from the report object of the provided {@code eventDataResponse}.
	 * If the report object is empty after removing the eventIndex, it is removed from the response.
	 * @param eventDataResponse the event data response for the error or warning
	 */
	private void removeEventIndexFromReport(Map<String, Object> eventDataResponse) {
		Map<String, Object> report = null;
		try {
			report = (Map<String, Object>) eventDataResponse.get(EdgeJson.Response.EventHandle.REPORT);
		} catch (ClassCastException e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Failed to cast 'report' to Map<String, Object>");
		}
		if (report != null) {
			report.remove(EdgeJson.Response.EventHandle.EVENT_INDEX);
			if (report.isEmpty()) {
				eventDataResponse.remove(EdgeJson.Response.EventHandle.REPORT);
			}
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
				String.format("Received event error for request id (%s), error details:\n %s", requestId, errorToLog)
			);
		} else {
			Log.warning(
				LOG_TAG,
				LOG_SOURCE,
				String.format("Received event error for request id (%s), error details:\n %s", requestId, errorToLog)
			);
		}
	}

	/**
	 * Builds an {@link EdgeEventError} from the error/warning data map returned by the server.
	 * Fields not present in the map default to empty string / 0.
	 */
	private EdgeEventError buildEdgeEventError(final Map<String, Object> errorData) {
		final String type = DataReader.optString(errorData, EdgeJson.Response.Error.TYPE, "");
		final int status = DataReader.optInt(errorData, EdgeJson.Response.Error.STATUS, 0);
		final String title = DataReader.optString(errorData, EdgeJson.Response.Error.TITLE, "");
		final String detail = DataReader.optString(errorData, EdgeJson.Response.Error.DETAIL, null);
		return new EdgeEventError(type, status, title, detail);
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
			final List<Event> contexts = sentEventsWaitingResponse.get(requestId);

			if (contexts == null || contexts.isEmpty()) {
				return false;
			}

			final Event firstEvent = contexts.get(0);
			return firstEvent.getTimestamp() < lastResetDate;
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
}
