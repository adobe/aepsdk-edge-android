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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNotNull;

import com.adobe.marketing.mobile.util.CollectionEqualCount;
import com.adobe.marketing.mobile.util.JSONAsserts;
import com.adobe.marketing.mobile.util.NodeConfig;
import com.adobe.marketing.mobile.util.ValueTypeMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ExperienceEventSerializerTest {

	private static final String DATA = "data";
	private static final String TYPE = "eventType";
	private static final String STITCH_ID = "stitchId";
	private static final String MERGE_ID = "eventMergeId";
	private static final String META = "meta";
	private static final String DATASETID = "datasetId";
	private static final String XDM = "xdm";
	private static final String QUERY = "query";
	private static final String PERSONALIZATION = "personalization";
	private static final String COMMERCE = "commerce";

	private static final String COMMERCE_JSON =
		"{\n" +
		"    \"purchases\": [\n" +
		"      {\n" +
		"        \"value\": \"3\"\n" +
		"      },\n" +
		"      {\n" +
		"        \"value\": \"12\"\n" +
		"      }\n" +
		"    ],\n" +
		"    \"order\": {\n" +
		"      \"purchaseID\": \"11124012-5436-4471-8de9-72ed94e90d95\",\n" +
		"      \"priceTotal\": 105.23,\n" +
		"      \"currencyCode\": \"RON\"\n" +
		"    }\n" +
		"  }";

	@Test
	public void testSerialize_returnsValidMap_whenEventWithRequiredParametersOnly() {
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder().setEventType("test").build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(getEventData()).setXdmSchema(contextData).build();
		Map<String, Object> result = event.toObjectMap();

		String expected =
			"{\n" +
			"  \"data\": {\n" +
			"    \"key\": \"value\",\n" +
			"    \"listExample\": [\n" +
			"      \"elem1\",\n" +
			"      \"elem2\"\n" +
			"    ]\n" +
			"  },\n" +
			"  \"datasetId\": \"STRING_TYPE\",\n" +
			"  \"xdm\": {}\n" +
			"}";
		JSONAsserts.assertExactMatch(
			expected,
			result,
			new ValueTypeMatch("datasetId"),
			new CollectionEqualCount(), // Validates top level property count
			new CollectionEqualCount(Collections.singletonList("data"), NodeConfig.Scope.Subtree)
		);
	}

	@Test
	public void testSerialize_returnsNull_whenEventWithoutRequiredParameters() {
		ExperienceEvent event = new ExperienceEvent.Builder().build();
		assertNull(event);
	}

	@Test
	public void testSerialize_returnsNull_whenEventWithoutRequiredXdmSchema() {
		Map<String, Object> eventData = getEventData();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(eventData).build();
		assertNull(event);
	}

	@Test
	public void testSerialize_returnsNull_whenEventContextDataWithNullEventType() {
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder()
			.setEventType((String) null)
			.build();
		assertNull(contextData);
	}

	@Test
	public void testSerialize_returnsNull_whenEventContextDataWithoutRequiredEventType() {
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder().build();
		assertNull(contextData);
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithoutCustomData() {
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder().setEventType("test").build();
		ExperienceEvent event = new ExperienceEvent.Builder().setXdmSchema(contextData).build();
		assertNotNull(event);
		Map<String, Object> result = event.toObjectMap();
		assertEquals(2, result.size());
		assertTrue(result.containsKey(XDM));
		assertTrue(result.containsKey(DATASETID));
		String expected = "{ \"datasetId\": \"STRING_TYPE\", \"xdm\": {} }";
		JSONAsserts.assertTypeMatch(
			expected,
			result,
			new CollectionEqualCount() // Validates top level property count
		);
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithNullParams() {
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder().setEventType("test").build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(getEventData()).setXdmSchema(contextData).build();
		Map<String, Object> result = event.toObjectMap();

		String expected =
			"{\n" +
			"  \"data\": {\n" +
			"    \"key\": \"value\",\n" +
			"    \"listExample\": [\n" +
			"      \"elem1\",\n" +
			"      \"elem2\"\n" +
			"    ]\n" +
			"  },\n" +
			"  \"datasetId\": \"STRING_TYPE\",\n" +
			"  \"xdm\": {}\n" +
			"}";
		JSONAsserts.assertExactMatch(
			expected,
			result,
			new ValueTypeMatch("datasetId"),
			new CollectionEqualCount(), // Validates top level property count
			new CollectionEqualCount(Collections.singletonList("data"), NodeConfig.Scope.Subtree)
		);
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithNullCommerceData() {
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(null)
			.build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(getEventData()).setXdmSchema(contextData).build();
		Map<String, Object> result = event.toObjectMap();

		String expected =
			"{\n" +
			"  \"data\": {\n" +
			"    \"key\": \"value\",\n" +
			"    \"listExample\": [\n" +
			"      \"elem1\",\n" +
			"      \"elem2\"\n" +
			"    ]\n" +
			"  },\n" +
			"  \"datasetId\": \"STRING_TYPE\",\n" +
			"  \"xdm\": {\n" +
			"    \"eventType\": \"test\"\n" +
			"  }\n" +
			"}";
		JSONAsserts.assertExactMatch(
			expected,
			result,
			new ValueTypeMatch("datasetId"),
			new CollectionEqualCount(NodeConfig.Scope.Subtree)
		);
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithCommerceData() {
		Map<String, Object> eventData = getEventData();
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(getCommerceData())
			.build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(eventData).setXdmSchema(contextData).build();

		Map<String, Object> result = event.toObjectMap();

		String expected =
			"{\n" +
			"  \"data\": {\n" +
			"    \"key\": \"value\",\n" +
			"    \"listExample\": [\n" +
			"      \"elem1\",\n" +
			"      \"elem2\"\n" +
			"    ]\n" +
			"  },\n" +
			"  \"datasetId\": \"STRING_TYPE\",\n" +
			"  \"xdm\": {\n" +
			"    \"commerce\": {\n" +
			"      \"order\": {\n" +
			"        \"currencyCode\": \"RON\",\n" +
			"        \"priceTotal\": 105.23,\n" +
			"        \"purchaseID\": \"11124012-5436-4471-8de9-72ed94e90d95\"\n" +
			"      },\n" +
			"      \"purchases\": [\n" +
			"        {\n" +
			"          \"value\": \"3\"\n" +
			"        },\n" +
			"        {\n" +
			"          \"value\": \"12\"\n" +
			"        }\n" +
			"      ]\n" +
			"    },\n" +
			"    \"eventType\": \"test\"\n" +
			"  }\n" +
			"}";
		JSONAsserts.assertExactMatch(
			expected,
			result,
			new ValueTypeMatch("datasetId"),
			new CollectionEqualCount(NodeConfig.Scope.Subtree)
		);
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithCommerceDataAsMap() {
		Map<String, Object> eventData = getEventData();
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(getCommerceData())
			.build();
		ExperienceEvent event = new ExperienceEvent.Builder()
			.setData(eventData)
			.setXdmSchema(contextData.serializeToXdm()) // set as Map
			.build();

		Map<String, Object> result = event.toObjectMap();

		String expected =
			"{\n" +
			"  \"data\": {\n" +
			"    \"key\": \"value\",\n" +
			"    \"listExample\": [\n" +
			"      \"elem1\",\n" +
			"      \"elem2\"\n" +
			"    ]\n" +
			"  },\n" +
			"  \"xdm\": {\n" +
			"    \"commerce\": {\n" +
			"      \"order\": {\n" +
			"        \"currencyCode\": \"RON\",\n" +
			"        \"priceTotal\": 105.23,\n" +
			"        \"purchaseID\": \"11124012-5436-4471-8de9-72ed94e90d95\"\n" +
			"      },\n" +
			"      \"purchases\": [\n" +
			"        {\n" +
			"          \"value\": \"3\"\n" +
			"        },\n" +
			"        {\n" +
			"          \"value\": \"12\"\n" +
			"        }\n" +
			"      ]\n" +
			"    },\n" +
			"    \"eventType\": \"test\"\n" +
			"  }\n" +
			"}";
		JSONAsserts.assertExactMatch(expected, result, new CollectionEqualCount(NodeConfig.Scope.Subtree));
	}

	@Test
	public void testSerialize_returnsMapOfLastXdmSchema_whenSchemaAddedAsSchemaThenMap() {
		Map<String, Object> eventData = getEventData();

		TestExperienceEventSchema xdmSchema = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(getCommerceData())
			.build();

		TestExperienceEventSchema xdmMap = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setEventMergeId("mergeid")
			.build();

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setData(eventData)
			.setXdmSchema(xdmSchema)
			.setXdmSchema(xdmMap.serializeToXdm()) // set as Map
			.build();

		Map<String, Object> result = event.toObjectMap();

		String expected =
			"{\n" +
			"  \"data\": {\n" +
			"    \"key\": \"value\",\n" +
			"    \"listExample\": [\n" +
			"      \"elem1\",\n" +
			"      \"elem2\"\n" +
			"    ]\n" +
			"  },\n" +
			"  \"xdm\": {\n" +
			"    \"eventType\": \"test\",\n" +
			"    \"eventMergeId\": \"mergeid\"\n" +
			"  }\n" +
			"}";
		JSONAsserts.assertExactMatch(expected, result, new CollectionEqualCount(NodeConfig.Scope.Subtree));
	}

	@Test
	public void testSerialize_returnsMapOfLastXdmSchema_whenSchemaAddedAsMapThenSchema() throws Exception {
		Map<String, Object> eventData = getEventData();

		TestExperienceEventSchema xdmMap = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setEventMergeId("mergeid")
			.build();

		TestExperienceEventSchema xdmSchema = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(getCommerceData())
			.build();

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setData(eventData)
			.setXdmSchema(xdmMap.serializeToXdm()) // set as Map
			.setXdmSchema(xdmSchema)
			.build();

		Map<String, Object> result = event.toObjectMap();

		String expected =
			"{\n" +
			"  \"data\": {\n" +
			"    \"key\": \"value\",\n" +
			"    \"listExample\": [\n" +
			"      \"elem1\",\n" +
			"      \"elem2\"\n" +
			"    ]\n" +
			"  },\n" +
			"  \"datasetId\": \"STRING_TYPE\",\n" +
			"  \"xdm\": {\n" +
			"    \"commerce\": {\n" +
			"      \"order\": {\n" +
			"        \"currencyCode\": \"RON\",\n" +
			"        \"priceTotal\": 105.23,\n" +
			"        \"purchaseID\": \"11124012-5436-4471-8de9-72ed94e90d95\"\n" +
			"      },\n" +
			"      \"purchases\": [\n" +
			"        {\n" +
			"          \"value\": \"3\"\n" +
			"        },\n" +
			"        {\n" +
			"          \"value\": \"12\"\n" +
			"        }\n" +
			"      ]\n" +
			"    },\n" +
			"    \"eventType\": \"test\"\n" +
			"  }\n" +
			"}";
		JSONAsserts.assertExactMatch(
			expected,
			result,
			new ValueTypeMatch("datasetId"),
			new CollectionEqualCount(NodeConfig.Scope.Subtree)
		);
	}

	private Map<String, Object> getEventData() {
		return new HashMap<String, Object>() {
			{
				put("key", "value");
				put(
					"listExample",
					Arrays.asList("elem1", "elem2")
				);
			}
		};
	}

	private Map<String, Object> getCommerceData() {
		return new HashMap<String, Object>() {
			{
				put(
					"purchases",
					Arrays.asList(
						new HashMap<String, String>() {
							{
								put("value", "3");
							}
						},
						new HashMap<String, String>() {
							{
								put("value", "12");
							}
						}
					)
				);
				put(
					"order",
					new HashMap<String, Object>() {
						{
							put("purchaseID", "11124012-5436-4471-8de9-72ed94e90d95");
							put("priceTotal", 105.23);
							put("currencyCode", "RON");
						}
					}
				);
			}
		};
	}
}
