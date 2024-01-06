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

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.MapUtils;
import com.adobe.marketing.mobile.util.StringUtils;
import com.adobe.marketing.mobile.xdm.Schema;
import java.util.HashMap;
import java.util.Map;

/**
 * Used for testing. Property that holds the XDM event context data within an Experience Event object.
 */
class TestExperienceEventSchema implements Schema {

	private static final String LOG_TAG = "UnitTestsFramework";
	private static final String LOG_SOURCE = "TestExperienceEventSchema";
	private static final String EVENT_TYPE = "eventType";
	private static final String COMMERCE = "commerce";
	private static final String EVENT_MERGE_ID = "eventMergeId";

	private Map<String, Object> commerce;
	private String eventMergeId;
	private String eventType;

	private TestExperienceEventSchema() {
		commerce = new HashMap<>();
	}

	@Override
	public String getSchemaVersion() {
		return "1";
	}

	@Override
	public String getSchemaIdentifier() {
		return "schemaId";
	}

	@Override
	public String getDatasetIdentifier() {
		return "datasetId";
	}

	@Override
	public Map<String, Object> serializeToXdm() {
		return toObjectMap();
	}

	/**
	 * The entities related to buying and selling activity.
	 *
	 * @return map of commerce data
	 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/commerce.schema.md">Commerce Schema</a>
	 */
	public Map<String, Object> getCommerceData() {
		return commerce;
	}

	/**
	 * The type of this event.
	 *
	 * @return the type of this event
	 */
	public String getEventType() {
		return eventType;
	}

	/**
	 * Converts current TestExperienceEventSchema into map.
	 * @return map containing the {@link TestExperienceEventSchema} data
	 */
	Map<String, Object> toObjectMap() {
		Map<String, Object> serializedMap = new HashMap<>();

		MapUtils.putIfNotEmpty(serializedMap, EVENT_TYPE, eventType);
		MapUtils.putIfNotEmpty(serializedMap, EVENT_MERGE_ID, eventMergeId);
		MapUtils.putIfNotEmpty(serializedMap, COMMERCE, commerce);
		return serializedMap;
	}

	public static class Builder {

		private final TestExperienceEventSchema experienceEventContextData;
		private boolean didBuild;

		public Builder() {
			experienceEventContextData = new TestExperienceEventSchema();
			didBuild = false;
		}

		/**
		 * The entities related to buying and selling activity.
		 *
		 * @param commerce map of commerce data
		 * @throws UnsupportedOperationException if this instance was already built
		 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/commerce.schema.md">Commerce Schema</a>
		 */
		public TestExperienceEventSchema.Builder setCommerceData(final Map<String, Object> commerce) {
			throwIfAlreadyBuilt();
			experienceEventContextData.commerce = commerce == null ? new HashMap<>() : new HashMap<>(commerce);
			return this;
		}

		/**
		 * An ID to correlate or merge multiple Experience Events together that are essentially
		 * the same event or should be merged. This is intended to be populated by the data producer
		 * prior to ingestion.
		 *
		 * @param eventMergeId the merge id for this event
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public TestExperienceEventSchema.Builder setEventMergeId(final String eventMergeId) {
			throwIfAlreadyBuilt();
			experienceEventContextData.eventMergeId = eventMergeId;
			return this;
		}

		/**
		 * The type of this event.
		 *
		 * @param eventType the type of this event
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public TestExperienceEventSchema.Builder setEventType(final String eventType) {
			throwIfAlreadyBuilt();
			experienceEventContextData.eventType = eventType;
			return this;
		}

		private void throwIfAlreadyBuilt() {
			if (didBuild) {
				throw new UnsupportedOperationException(
					"TestExperienceEventSchema.Builder - attempt to call setters after build() was called."
				);
			}
		}

		/**
		 * Builds and returns a new instance of {@code TestExperienceEventSchema}.
		 *
		 * @return a new instance of {@code TestExperienceEventSchema} or null if one of the required parameters is missing
		 * @throws UnsupportedOperationException if this instance was already built
		 */
		public TestExperienceEventSchema build() {
			throwIfAlreadyBuilt();

			// The SDK considers EventType a required property
			if (StringUtils.isNullOrEmpty(experienceEventContextData.eventType)) {
				Log.warning(
					LOG_TAG,
					LOG_SOURCE,
					"Unable to create TestExperienceEventSchema without required 'eventType', use setEventType API to set it."
				);
				return null;
			}

			didBuild = true;

			return experienceEventContextData;
		}
	}
}
