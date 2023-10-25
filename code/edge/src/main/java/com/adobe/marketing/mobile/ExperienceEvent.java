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
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.xdm.Schema;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public final class ExperienceEvent {

	private static final String LOG_SOURCE = "ExperienceEvent";

	/// Optional free-form data associated with this event
	private Map<String, Object> data;

	// XDM formatted data associated with this event
	private Map<String, Object> xdmData;

	// Adobe Experience Platform dataset identifier, if not set the default dataset identifier set in the Edge Configuration is used
	private String datasetIdentifier;

	// Datastream identifier used to override the default datastream identifier set in the Edge configuration for this event
	private String datastreamIdOverride;

	// Datastream configuration used to override individual settings from the default datastream configuration for this event
	private Map<String, Object> datastreamConfigOverride;

	private ExperienceEvent() {}

	public static class Builder {

		private final ExperienceEvent experienceEvent;
		private boolean didBuild;

		public Builder() {
			experienceEvent = new ExperienceEvent();
			didBuild = false;
		}

		/**
		 * Override the default datastream identifier to send this event's data to a different datastream.
		 *
		 * When using {@link Edge#sendEvent}, this event is sent to the Experience Platform using the datastream identifier {@code datastreamIdOverride}
		 * instead of the default Experience Edge configuration ID set in the SDK Configuration key {@code edge.configId}.
		 *
		 * @param datastreamIdOverride Datastream identifier to override the default datastream identifier set in the Edge configuration
		 * @return instance of current builder
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public Builder setDatastreamIdOverride(final String datastreamIdOverride) {
			throwIfAlreadyBuilt();
			experienceEvent.datastreamIdOverride = datastreamIdOverride;
			return this;
		}

		/**
		 * Override the default datastream configuration settings for individual services for this event.
		 *
		 * When using {@link Edge#sendEvent}, this event is sent to the Experience Platform along with the
		 * datastream overrides defined in {@code datastreamConfigOverride}.
		 *
		 * @param datastreamConfigOverride Map defining datastream configuration overrides for this Experience Event
		 * @return instance of current builder
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public Builder setDatastreamConfigOverride(final Map<String, Object> datastreamConfigOverride) {
			throwIfAlreadyBuilt();
			experienceEvent.datastreamConfigOverride =
				datastreamConfigOverride == null ? null : Utils.deepCopy(datastreamConfigOverride);
			return this;
		}

		/**
		 * Sets free form data associated with this event to be passed to Adobe Experience Edge.
		 *
		 * @param data free form data, JSON like types are accepted
		 * @return instance of current builder
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public Builder setData(final Map<String, Object> data) {
			throwIfAlreadyBuilt();
			experienceEvent.data = data == null ? null : Utils.deepCopy(data);
			return this;
		}

		/**
		 * Solution specific XDM event data for this event.
		 * If XDM schema is set multiple times using either this API or {@link #setXdmSchema(Map)},
		 * the value will be overwritten and only the last changes are applied.
		 * Setting {@code xdm} to null clears the value.
		 *
		 * This event is sent to the Experience Platform dataset defined by {@link Schema#getDatasetIdentifier()}.
		 *
		 * @param xdm {@link Schema} information
		 * @return instance of current builder
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public Builder setXdmSchema(final Schema xdm) {
			throwIfAlreadyBuilt();

			if (xdm == null) {
				experienceEvent.xdmData = null;
				experienceEvent.datasetIdentifier = null;
			} else {
				experienceEvent.xdmData = Utils.deepCopy(xdm.serializeToXdm());
				experienceEvent.datasetIdentifier = xdm.getDatasetIdentifier();
			}

			return this;
		}

		/**
		 * Solution specific XDM event data for this event, passed as raw mapping of keys and
		 * Object values.
		 * If XDM schema is set multiple times using either this API or {@link #setXdmSchema(Schema)},
		 * the value will be overwritten and only the last changes are applied.
		 * Setting {@code xdm} to null clears the value.
		 *
		 * When using {@link Edge#sendEvent}
		 * this event is sent to the Experience Platform {@code datasetIdentifier} if provided,
		 * or to the default Experience Platform dataset defined when generating the Experience Edge
		 * configuration ID if {@code datasetIdentifier} is null.
		 *
		 * @param xdm {@code Map<String, Object>} of raw XDM schema data
		 * @param datasetIdentifier The Experience Platform dataset identifier where this event is sent.
		 *                          If not provided, the default dataset defined in the configuration ID is used
		 * @return instance of current builder
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public Builder setXdmSchema(final Map<String, Object> xdm, final String datasetIdentifier) {
			throwIfAlreadyBuilt();
			experienceEvent.xdmData = xdm == null ? null : Utils.deepCopy(xdm);
			experienceEvent.datasetIdentifier = datasetIdentifier;
			return this;
		}

		/**
		 * Solution specific XDM event data for this event, passed as raw mapping of keys and
		 * Object values.
		 * If XDM schema is set multiple times using either this API or {@link #setXdmSchema(Schema)},
		 * the value will be overwritten and only the last changes are applied.
		 * Setting {@code xdm} to null clears the value.
		 *
		 * When using {@link Edge#sendEvent} this event is sent to the default
		 * Experience Platform dataset defined when generating the Experience Edge configuration ID.
		 *
		 * @param xdm {@code Map<String, Object>} of raw XDM schema data
		 * @return instance of current builder
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public Builder setXdmSchema(final Map<String, Object> xdm) {
			return this.setXdmSchema(xdm, null);
		}

		private void throwIfAlreadyBuilt() {
			if (didBuild) {
				throw new UnsupportedOperationException(
					"ExperienceEvent - attempted to call methods on ExperienceEvent.Builder after build() was called"
				);
			}
		}

		/**
		 * Builds and returns a new instance of {@code ExperienceEvent}.
		 *
		 * @return a new instance of {@code ExperienceEvent} or null if one of the required parameters is missing
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public ExperienceEvent build() {
			throwIfAlreadyBuilt();

			if (experienceEvent.xdmData == null) {
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Unable to create the ExperienceEvent without required 'XdmSchema', use setXdmSchema API to set it."
				);
				return null;
			}

			didBuild = true;
			return experienceEvent;
		}
	}

	/**
	 * @return the datastream id override
	 */
	public String getDatastreamIdOverride() {
		return datastreamIdOverride;
	}

	/**
	 * @return the datastream config override
	 */
	public Map<String, Object> getDatastreamConfigOverride() {
		return datastreamConfigOverride;
	}

	/**
	 * @return the free-form data associated with this {@link ExperienceEvent}
	 */
	public Map<String, Object> getData() {
		if (data != null) {
			return Utils.deepCopy(data);
		}

		return Collections.emptyMap();
	}

	/**
	 * XDM formatted data associated with this {@link ExperienceEvent}. Use a {@link Schema} implementation
	 * for a better XDM data ingestion and format control.
	 *
	 * @return returns the XDM formatted data as a Map
	 */
	public Map<String, Object> getXdmSchema() {
		if (xdmData != null) {
			return Utils.deepCopy(xdmData);
		}

		return Collections.emptyMap();
	}

	/**
	 * Converts current ExperienceEvent into map to be passed as EventData
	 *
	 * @return map containing the {@link ExperienceEvent} data
	 */
	Map<String, Object> toObjectMap() {
		Map<String, Object> serializedMap = new HashMap<>();
		MapUtils.putIfNotEmpty(serializedMap, EdgeJson.Event.DATA, data);

		if (xdmData != null) {
			MapUtils.putIfNotEmpty(serializedMap, EdgeJson.Event.XDM, xdmData);
		}

		Map<String, Object> configMap = new HashMap<>();

		if (!StringUtils.isNullOrEmpty(datastreamIdOverride)) {
			configMap.put(EdgeConstants.EventDataKeys.Config.DATASTREAM_ID_OVERRIDE, datastreamIdOverride);
		}

		if (datastreamConfigOverride != null) {
			configMap.put(EdgeConstants.EventDataKeys.Config.DATASTREAM_CONFIG_OVERRIDE, datastreamConfigOverride);
		}

		if (!configMap.isEmpty()) {
			serializedMap.put(EdgeConstants.EventDataKeys.Config.KEY, configMap);
		}

		if (!StringUtils.isNullOrEmpty(datasetIdentifier)) {
			serializedMap.put(EdgeConstants.EventDataKeys.DATASET_ID, datasetIdentifier);
		}

		return serializedMap;
	}
}
