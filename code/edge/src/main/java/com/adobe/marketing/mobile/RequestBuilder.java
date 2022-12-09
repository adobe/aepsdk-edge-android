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
import com.adobe.marketing.mobile.util.CloneFailedException;
import com.adobe.marketing.mobile.util.EventDataUtils;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import org.json.JSONObject;

class RequestBuilder {

	private static final String LOG_SOURCE = "RequestBuilder";

	// Note: Response streaming is enabled when both streamingRecordSeparator and streamingLineFeed are non nil.
	// Control character used before each response fragment.
	private String streamingRecordSeparator;

	// Control character used at the end of each response fragment
	private String streamingLineFeed;

	private static final SimpleDateFormat iso6801DateFormat;

	static {
		final Locale posixLocale = new Locale(Locale.US.getLanguage(), Locale.US.getCountry(), "POSIX");
		iso6801DateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", posixLocale);
		iso6801DateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
	}

	private static final Map<String, Object> consentQueryOptions;

	static {
		consentQueryOptions = new HashMap<>();
		consentQueryOptions.put(EdgeJson.Event.Query.OPERATION, EdgeJson.Event.Query.OPERATION_UPDATE);
	}

	// Data store manager for retrieving store response payloads for StateMetadata
	private final StoreResponsePayloadManager storeResponsePayloadManager;

	// XDM payloads to be attached to the request
	private final Map<String, Object> xdmPayloads;

	RequestBuilder(final NamedCollection namedCollection) {
		storeResponsePayloadManager = new StoreResponsePayloadManager(namedCollection);
		xdmPayloads = new HashMap<>();
	}

	/**
	 * Enables streaming of the Edge Response. If either {@code recordSeparator} or
	 * {@code lineFeed} is empty or null, response streaming is not enabled.
	 *
	 * @param recordSeparator the record separator used to delimit the start of a response chunk
	 * @param lineFeed the line feed used to delimit the end of a response chunk
	 */
	void enableResponseStreaming(final String recordSeparator, final String lineFeed) {
		this.streamingRecordSeparator = recordSeparator;
		this.streamingLineFeed = lineFeed;
	}

	/**
	 * Adds an XDM payload to the current known XDM payloads
	 * Overwrites any existing duplicate XDM payloads with the new payloads in xdmPayload
	 * @param xdmPayload an XDM payload to be attached to the request
	 */
	void addXdmPayload(final Map<String, Object> xdmPayload) {
		if (Utils.isNullOrEmpty(xdmPayload)) {
			return;
		}

		xdmPayloads.putAll(xdmPayload);
	}

	/**
	 * Builds the request payload with all the provided parameters and experience events
	 *
	 * @param events list of experience events, should not be null/empty
	 * @return the request payload in {@link JSONObject} format or null if events list is null/empty
	 */
	JSONObject getPayloadWithExperienceEvents(final List<Event> events) {
		if (events == null || events.isEmpty()) {
			return null;
		}

		EdgeRequest request = new EdgeRequest();

		// set gateway metadata to request if exists
		KonductorConfig konductorConfig = buildKonductorConfig();
		request.setRequestMetadata(
			new RequestMetadata.Builder()
				.setKonductorConfig(konductorConfig.toObjectMap())
				.setStateMetadata(new StateMetadata(storeResponsePayloadManager.getActiveStores()).toObjectMap())
				.build()
		);

		request.setXdmPayloads(xdmPayloads);

		List<Map<String, Object>> experienceEvents = extractExperienceEvents(events);

		return request.asJsonObject(experienceEvents);
	}

	/**
	 * Builds the request payload to update the consent.
	 *
	 * @param event The Consent Update event containing XDM formatted data
	 * @return the consent update payload in {@link JSONObject} format or null if the consent payload is empty
	 */
	JSONObject getConsentPayload(final Event event) {
		if (event == null || Utils.isNullOrEmpty(event.getEventData())) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"RequestBuilder - Unable to process the consent update request, event/event data is null"
			);
			return null;
		}

		if (!event.getEventData().containsKey(EdgeConstants.EventDataKey.CONSENTS)) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Unable to process the consent update request, no consents data");
			return null;
		}

		Map<String, Object> consentMap;

		try {
			consentMap = (Map<String, Object>) event.getEventData().get(EdgeConstants.EventDataKey.CONSENTS);
		} catch (ClassCastException e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Failed to read consents from event data, not a map");
			return null;
		}

		EdgeConsentUpdate consents = new EdgeConsentUpdate(consentMap);

		// Add query with operation update to specify the consent update should be
		// executed as an incremental update and not enforce collect consent settings to be provided all the time
		QueryOptions query = new QueryOptions();
		query.setConsentOptions(consentQueryOptions);
		consents.setQueryOptions(query);

		try {
			if (xdmPayloads != null && xdmPayloads.get(EdgeConstants.EventDataKey.IDENTITY_MAP) != null) {
				consents.setIdentityMap((Map<String, Object>) xdmPayloads.get(EdgeConstants.EventDataKey.IDENTITY_MAP));
			}
		} catch (ClassCastException e) {
			Log.debug(LOG_TAG, LOG_SOURCE, "Failed to read IdentityMap from request payload, not a map");
		}

		// Enable response streaming
		KonductorConfig konductorConfig = buildKonductorConfig();
		consents.setRequestMetadata(
			new RequestMetadata.Builder().setKonductorConfig(konductorConfig.toObjectMap()).build()
		);

		return consents.asJsonObject();
	}

	private KonductorConfig buildKonductorConfig() {
		KonductorConfig konductorConfig = new KonductorConfig();

		if (!Utils.isNullOrEmpty(streamingRecordSeparator) && !Utils.isNullOrEmpty(streamingLineFeed)) {
			konductorConfig.enableStreaming(streamingRecordSeparator, streamingLineFeed);
		}

		return konductorConfig;
	}

	/**
	 * Extract the {@code ExperienceEvent} data from each SDK {@code Event} and return as a list
	 * of maps.  The timestamp for each {@link Event} is set as the timestamp for its containing {@link ExperienceEvent}
	 * data. The {@link Event#getUniqueIdentifier()} is set as the event ID for its containing
	 * {@link ExperienceEvent} data.
	 *
	 * @param events a list of SDK {@link Event}s which contain a {@link ExperienceEvent} as event data
	 * @return a list of {@link ExperienceEvent}s as maps
	 */
	private List<Map<String, Object>> extractExperienceEvents(final List<Event> events) {
		List<Map<String, Object>> experienceEvents = new ArrayList<>();

		for (Event e : events) {
			try {
				Map<String, Object> data = EventDataUtils.clone(e.getEventData());
				if (!Utils.isNullOrEmpty(data)) {
					setDatasetIdToExperienceEvent(data);
					setTimestampToExperienceEvent(data, e);
					setEventIdToExperienceEvent(data, e);
					experienceEvents.add(data);
				}
			} catch (CloneFailedException ex) {
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Failed to extract and clone data for an experience event (id: %s), skipping. Exception details: %s",
					e.getUniqueIdentifier(),
					ex.getLocalizedMessage()
				);
			}
		}

		return experienceEvents;
	}

	/**
	 * Set the event timestamp as XDM event timestamp if no timestamp value is provided in the xdm payload under {@code data}.
	 * If a timestamp value exists, it will not be overwritten.
	 * @param data the request payload containing the {@code xdm} object.
	 * @param event the {@code Event} used to retrieve the timestamp for this experience event if no timestamp is set in the XDM payload.
	 */
	@SuppressWarnings("unchecked")
	private void setTimestampToExperienceEvent(final Map<String, Object> data, final Event event) {
		// get experience event XDM data
		Map<String, Object> xdm = (Map<String, Object>) data.get(EdgeJson.Event.XDM);

		if (xdm == null) {
			xdm = new HashMap<>();
			data.put(EdgeJson.Event.XDM, xdm);
		}

		String timestampFromPayload = null;

		try {
			timestampFromPayload = (String) xdm.get(EdgeJson.Event.Xdm.TIMESTAMP);
		} catch (ClassCastException e) {
			Log.debug(
				LOG_TAG,
				LOG_SOURCE,
				"Unable to read the timestamp from the XDM payload due to unexpected format. Expected String."
			);
		}

		// if no timestamp is provided in the xdm event payload, set the event timestamp
		if (timestampFromPayload == null || timestampFromPayload.isEmpty()) {
			long eventTimestamp = event.getTimestamp();
			String eventTimestampString = iso6801DateFormat.format(new Date(eventTimestamp));
			xdm.put(EdgeJson.Event.Xdm.TIMESTAMP, eventTimestampString);
		}
	}

	@SuppressWarnings("unchecked")
	private void setEventIdToExperienceEvent(final Map<String, Object> data, final Event event) {
		// get experience event XDM data
		Map<String, Object> xdm = (Map<String, Object>) data.get(EdgeJson.Event.XDM);

		if (xdm == null) {
			xdm = new HashMap<>();
			data.put(EdgeJson.Event.XDM, xdm);
		}

		xdm.put(EdgeJson.Event.Xdm.EVENT_ID, event.getUniqueIdentifier());
	}

	@SuppressWarnings("unchecked")
	private void setDatasetIdToExperienceEvent(final Map<String, Object> data) {
		String datasetId = (String) data.remove(EdgeConstants.EventDataKey.DATASET_ID);

		if (datasetId == null || (datasetId = datasetId.trim()).isEmpty()) {
			return;
		}

		// get experience event meta data
		Map<String, Object> meta = (Map<String, Object>) data.get(EdgeJson.Event.METADATA);

		if (meta == null) {
			meta = new HashMap<>();
			data.put(EdgeJson.Event.METADATA, meta);
		}

		Map<String, Object> collectMeta = new HashMap<>();
		collectMeta.put(EdgeJson.Event.Metadata.DATASET_ID, datasetId);
		meta.put(EdgeJson.Event.Metadata.COLLECT, collectMeta);
	}
}
