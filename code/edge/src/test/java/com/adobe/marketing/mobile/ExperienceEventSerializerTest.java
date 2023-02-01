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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
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
		Map<String, Object> eventData = getEventData();
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder().setEventType("test").build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(eventData).setXdmSchema(contextData).build();
		Map<String, Object> result = event.toObjectMap();
		assertEquals(3, result.size());
		assertEquals(eventData, result.get(DATA));
		assertTrue(result.containsKey(XDM));
		assertTrue(result.containsKey(DATASETID));
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
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithNullParams() {
		Map<String, Object> eventData = getEventData();
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder().setEventType("test").build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(eventData).setXdmSchema(contextData).build();
		Map<String, Object> result = event.toObjectMap();
		assertEquals(3, result.size());
		assertEquals(eventData, result.get(DATA));
		assertTrue(result.containsKey(XDM));
		assertTrue(result.containsKey(DATASETID));
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithNullCommerceData() {
		Map<String, Object> eventData = getEventData();
		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(null)
			.build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(eventData).setXdmSchema(contextData).build();
		Map<String, Object> result = event.toObjectMap();
		assertEquals(3, result.size());
		assertEquals(eventData, result.get(DATA));
		assertTrue(result.containsKey(DATASETID));
		assertTrue(result.containsKey(XDM));
		Map<String, Object> xdmResult = (Map) result.get(XDM);
		assertEquals(1, xdmResult.size());
		assertEquals("test", xdmResult.get(TYPE));
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithCommerceData() throws Exception {
		Map<String, Object> eventData = getEventData();
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> commerceMap = mapper.readValue(COMMERCE_JSON, Map.class);

		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(commerceMap)
			.build();
		ExperienceEvent event = new ExperienceEvent.Builder().setData(eventData).setXdmSchema(contextData).build();

		Map<String, Object> result = event.toObjectMap();
		assertEquals(3, result.size());
		assertEquals(eventData, result.get(DATA));
		assertTrue(result.containsKey(DATASETID));
		Map<String, Object> xdm = (Map) result.get(XDM);
		assertEquals(2, xdm.size());
		assertEquals("test", xdm.get(TYPE));
		assertEquals(commerceMap, xdm.get(COMMERCE));
	}

	@Test
	public void testSerialize_returnsValidMap_whenEventWithCommerceDataAsMap() throws Exception {
		Map<String, Object> eventData = getEventData();
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> commerceMap = mapper.readValue(COMMERCE_JSON, Map.class);

		TestExperienceEventSchema contextData = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(commerceMap)
			.build();
		ExperienceEvent event = new ExperienceEvent.Builder()
			.setData(eventData)
			.setXdmSchema(contextData.serializeToXdm()) // set as Map
			.build();

		Map<String, Object> result = event.toObjectMap();
		assertEquals(2, result.size());
		assertEquals(eventData, result.get(DATA));

		Map<String, Object> xdm = (Map) result.get(XDM);
		assertEquals(2, xdm.size());
		assertEquals("test", xdm.get(TYPE));
		assertEquals(commerceMap, xdm.get(COMMERCE));
	}

	@Test
	public void testSerialize_returnsMapOfLastXdmSchema_whenSchemaAddedAsSchemaThenMap() throws Exception {
		Map<String, Object> eventData = getEventData();
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> commerceMap = mapper.readValue(COMMERCE_JSON, Map.class);

		TestExperienceEventSchema xdmSchema = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(commerceMap)
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
		assertEquals(2, result.size());
		assertEquals(eventData, result.get(DATA));

		Map<String, Object> xdm = (Map) result.get(XDM);
		assertEquals(2, xdm.size());
		assertEquals("test", xdm.get(TYPE));
		assertEquals("mergeid", xdm.get(MERGE_ID));
		assertNull(xdm.get(COMMERCE)); // does not exist
	}

	@Test
	public void testSerialize_returnsMapOfLastXdmSchema_whenSchemaAddedAsMapThenSchema() throws Exception {
		Map<String, Object> eventData = getEventData();
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> commerceMap = mapper.readValue(COMMERCE_JSON, Map.class);

		TestExperienceEventSchema xdmMap = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setEventMergeId("mergeid")
			.build();

		TestExperienceEventSchema xdmSchema = new TestExperienceEventSchema.Builder()
			.setEventType("test")
			.setCommerceData(commerceMap)
			.build();

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setData(eventData)
			.setXdmSchema(xdmMap.serializeToXdm()) // set as Map
			.setXdmSchema(xdmSchema)
			.build();

		Map<String, Object> result = event.toObjectMap();
		assertEquals(3, result.size());
		assertEquals(eventData, result.get(DATA));
		assertTrue(result.containsKey(DATASETID));

		Map<String, Object> xdm = (Map) result.get(XDM);
		assertEquals(2, xdm.size());
		assertEquals("test", xdm.get(TYPE));
		assertEquals(commerceMap, xdm.get(COMMERCE));
		assertNull(xdm.get(MERGE_ID)); // does not exist
	}

	private Map<String, Object> getEventData() {
		List<String> stringList = new ArrayList<String>();
		stringList.add("elem1");
		stringList.add("elem2");
		Map<String, Object> eventData = new HashMap<String, Object>();
		eventData.put("key", "value");
		eventData.put("listExample", stringList);
		return eventData;
	}
}
