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
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.xdm.Schema;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public final class ExperienceEvent {

	private static final String LOG_SOURCE = "ExperienceEvent";

	private Map<String, Object> data;
	private Map<String, Object> xdmData;
	private String datasetIdentifier;

	private ExperienceEvent() {}

	public static class Builder {

		private final ExperienceEvent experienceEvent;
		private boolean didBuild;

		public Builder() {
			experienceEvent = new ExperienceEvent();
			didBuild = false;
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
				return this;
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
		Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.DATA, data);

		if (xdmData != null) {
			Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.XDM, xdmData);
		}

		if (!StringUtils.isNullOrEmpty(datasetIdentifier)) {
			serializedMap.put(EdgeConstants.EventDataKey.DATASET_ID, datasetIdentifier);
		}

		return serializedMap;
	}
}
