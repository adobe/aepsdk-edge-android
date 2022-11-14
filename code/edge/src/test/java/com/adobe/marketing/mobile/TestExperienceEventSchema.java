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

import com.adobe.marketing.mobile.xdm.Schema;
import java.util.HashMap;
import java.util.Map;

/**
 * Used for testing. Property that holds the XDM event context data within an Experience Event object.
 */
class TestExperienceEventSchema implements Schema {

	private static final String LOG_TAG = "TestExperienceEventSchema";

	private Map<String, Object> identityMap;
	private Map<String, Object> placeContext;
	private Map<String, Object> commerce;
	private Map<String, Object> beacon;
	private String eventMergeId;
	private String eventType;

	private TestExperienceEventSchema() {
		placeContext = new HashMap<>();
		commerce = new HashMap<>();
		beacon = new HashMap<>();
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
	 * Defines a map containing a set of end user identities, keyed on either namespace
	 * integration code or the namespace ID of the identity. Within each namespace, the
	 * identity is unique. The values of the map are an array, meaning that more than one
	 * identity of each namespace may be carried.
	 *
	 * @return map containing a list of identifiers keyed by namespace
	 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/identitymap.schema.md">IdentityMap Schema</a>
	 */
	public Map<String, Object> getIdentityMap() {
		return identityMap;
	}

	/**
	 * The transient circumstances related to the place or physical location of the observation.
	 * Examples include location specific information such as weather, local time, traffic,
	 * day of the week, workday vs. holiday, working hours.
	 *
	 * @return map of location context data
	 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/placecontext.schema.md">PlaceContext Schema</a>
	 */
	public Map<String, Object> getPlaceContext() {
		return placeContext;
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
	 * Beacon, a wireless device that communicates identity information to mobile applications as
	 * mobile devices come within range.
	 *
	 * @return map of beacon data
	 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/beacon-interaction-details.schema.md">Beacon Schema</a>
	 */
	public Map<String, Object> getBeaconData() {
		return beacon;
	}

	/**
	 * An ID to correlate or merge multiple Experience Events together that are essentially
	 * the same event or should be merged. This is intended to be populated by the data producer
	 * prior to ingestion.
	 *
	 * @return the merge id for this event
	 */
	public String getEventMergeId() {
		return eventMergeId;
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

		if (identityMap != null) {
			Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.Xdm.IDENTITY_MAP, identityMap);
		}

		Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.Xdm.EVENT_TYPE, eventType);
		Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.Xdm.EVENT_MERGE_ID, eventMergeId);
		Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.Xdm.COMMERCE, commerce);
		Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.Xdm.BEACON, beacon);
		Utils.putIfNotEmpty(serializedMap, EdgeJson.Event.Xdm.PLACE_CONTEXT, placeContext);
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
		 * Defines a map containing a set of end user identities, keyed on either namespace
		 * integration code or the namespace ID of the identity. Within each namespace, the
		 * identity is unique. The values of the map are an array, meaning that more than one
		 * identity of each namespace may be carried.
		 *
		 * @param identityMap identifiers keyed by namespace
		 * @throws UnsupportedOperationException if this instance was already built
		 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/identitymap.schema.md">IdentityMap Schema</a>
		 */
		public TestExperienceEventSchema.Builder setIdentityMap(final Map<String, Object> identityMap) {
			throwIfAlreadyBuilt();
			experienceEventContextData.identityMap = identityMap;
			return this;
		}

		/**
		 * The transient circumstances related to the place or physical location of the observation.
		 * Examples include location specific information such as weather, local time, traffic,
		 * day of the week, workday vs. holiday, working hours.
		 *
		 * @param placeContext map of location context data
		 * @throws UnsupportedOperationException if this instance was already built
		 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/placecontext.schema.md">PlaceContext Schema</a>
		 */
		public TestExperienceEventSchema.Builder setPlaceContext(final Map<String, Object> placeContext) {
			throwIfAlreadyBuilt();
			experienceEventContextData.placeContext =
				placeContext == null ? new HashMap<String, Object>() : new HashMap<String, Object>(placeContext);
			return this;
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
			experienceEventContextData.commerce =
				commerce == null ? new HashMap<String, Object>() : new HashMap<String, Object>(commerce);
			return this;
		}

		/**
		 * Beacon, a wireless device that communicates identity information to mobile applications as
		 * mobile devices come within range.
		 *
		 * @param beacon map of beacon data
		 * @throws UnsupportedOperationException if this instance was already built
		 * @see <a href="https://github.com/adobe/xdm/blob/master/docs/reference/context/beacon-interaction-details.schema.md">Beacon Schema</a>
		 */
		public TestExperienceEventSchema.Builder setBeaconData(final Map<String, Object> beacon) {
			throwIfAlreadyBuilt();
			experienceEventContextData.beacon =
				beacon == null ? new HashMap<String, Object>() : new HashMap<String, Object>(beacon);
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
			if (Utils.isNullOrEmpty(experienceEventContextData.eventType)) {
				MobileCore.log(
					LoggingMode.WARNING,
					LOG_TAG,
					"Unable to create TestExperienceEventSchema without required 'eventType', use setEventType API to set it."
				);
				return null;
			}

			didBuild = true;

			return experienceEventContextData;
		}
	}
}
