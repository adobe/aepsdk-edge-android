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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.adobe.marketing.mobile.services.DataEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;

public class EdgeDataEntityTests {

	private final Event event = new Event.Builder("test", "all", "things").build();
	private final Map<String, Object> edgeConfig = new HashMap<String, Object>() {
		{
			put("edge.configId", "sample");
		}
	};
	private final Map<String, Object> identityMap = new HashMap<String, Object>() {
		{
			put("identityMap", "example");
		}
	};

	@Test
	public void testConstructor_allParams() {
		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, identityMap);
		assertEquals(event, entity.getEvent());
		assertEquals(edgeConfig, entity.getConfiguration());
		assertEquals(identityMap, entity.getIdentityMap());
	}

	@Test
	public void testConstructor_withEvent() {
		EdgeDataEntity entity = new EdgeDataEntity(event);
		assertEquals(event, entity.getEvent());
		assertTrue(entity.getConfiguration().isEmpty());
		assertTrue(entity.getIdentityMap().isEmpty());
	}

	@Test
	public void testConstructor_copiesIdentityMap() {
		Map<String, Object> identityMap = new HashMap<String, Object>() {
			{
				put("identityMap", "example");
			}
		};
		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, identityMap);
		assertEquals(event, entity.getEvent());
		assertEquals(identityMap, entity.getIdentityMap());
		identityMap.put("newkey", "shouldnotaffect");
		assertNotEquals(identityMap, entity.getIdentityMap());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor_whenNullEvent() {
		new EdgeDataEntity(null, edgeConfig, identityMap);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testConstructor2_whenNullEvent() {
		new EdgeDataEntity(null);
	}

	@Test
	public void testConstructor_whenNullConfig() {
		EdgeDataEntity entity = new EdgeDataEntity(event, null, identityMap);
		assertNotNull(entity.getEvent());
		assertNotNull(entity.getIdentityMap());
		assertFalse(entity.getIdentityMap().isEmpty());
		assertNotNull(entity.getConfiguration());
		assertTrue(entity.getConfiguration().isEmpty());
	}

	@Test
	public void testConstructor_whenNullIdentity() {
		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, null);
		assertNotNull(entity.getEvent());
		assertNotNull(entity.getIdentityMap());
		assertTrue(entity.getIdentityMap().isEmpty());
		assertNotNull(entity.getConfiguration());
		assertFalse(entity.getConfiguration().isEmpty());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testUpdateIdentityMap() {
		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, identityMap);
		entity.getIdentityMap().put("update", "shouldnotwork");
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testUpdateConfig() {
		EdgeDataEntity entity = new EdgeDataEntity(event, edgeConfig, identityMap);
		entity.getConfiguration().put("update", "shouldnotwork");
	}

	@Test
	public void testToFromDataEntity() {
		Event event = new Event.Builder("name", "type", "source")
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("key", "value");
					}
				}
			)
			.build();

		Map<String, Object> configuration = new HashMap<String, Object>() {
			{
				put("configuration", "value");
			}
		};

		Map<String, Object> identityMap = new HashMap<String, Object>() {
			{
				put(
					"ECID",
					new ArrayList<Object>() {
						{
							add(
								new HashMap<String, Object>() {
									{
										put("id", "12345");
										put("authenticatedState", "ambiguous");
										put("primary", false);
									}
								}
							);
						}
					}
				);
			}
		};

		EdgeDataEntity entity = new EdgeDataEntity(event, configuration, identityMap);
		DataEntity serializedEntity = entity.toDataEntity();
		assertNotNull(serializedEntity);

		EdgeDataEntity deserializedEntity = EdgeDataEntity.fromDataEntity(serializedEntity);
		assertNotNull(deserializedEntity);
		assertNotNull(deserializedEntity.getEvent());
		assertNotNull(deserializedEntity.getConfiguration());
		assertNotNull(deserializedEntity.getIdentityMap());

		// Assert Event
		Event deserializedEvent = deserializedEntity.getEvent();
		assertEquals(event.getName(), deserializedEvent.getName());
		assertEquals(event.getType(), deserializedEvent.getType());
		assertEquals(event.getSource(), deserializedEvent.getSource());
		assertEquals(event.getUniqueIdentifier(), deserializedEvent.getUniqueIdentifier());
		assertEquals(event.getTimestamp(), deserializedEvent.getTimestamp());
		assertEquals(event.getEventData(), deserializedEvent.getEventData());

		// Assert Configuration
		assertEquals(configuration, deserializedEntity.getConfiguration());

		// Assert IdentityMap
		assertEquals(identityMap, deserializedEntity.getIdentityMap());
	}

	@Test
	public void testToFromDataEntity_bypassPreservesEventMetadataAndExplicitNulls() throws Exception {
		Map<String, Object> tokens = new HashMap<>();
		tokens.put("pushNotification", null);
		Map<String, Object> data = new HashMap<>();
		data.put("timezone", null);
		data.put("tokens", tokens);
		data.put("values", Arrays.asList(null, "UTC"));
		String[] mask = { "timezone", "tokens.pushNotification" };
		Event parent = new Event.Builder("parent", "type", "source").build();
		Event request = new Event.Builder(
			"Device Attributes",
			EdgeConstants.EventType.EDGE_BYPASS_CONSENT,
			EventSource.REQUEST_CONTENT,
			mask
		)
			.setUniqueIdentifier("persisted-request")
			.setTimestamp(123456789L)
			.setEventData(data)
			.inResponseToEvent(event)
			.chainToParentEvent(parent)
			.build();
		assertEquals(parent.getUniqueIdentifier(), request.getParentID());
		assertEquals(event.getUniqueIdentifier(), request.getResponseID());

		EdgeDataEntity entity = new EdgeDataEntity(request, edgeConfig, identityMap);
		for (int attempt = 0; attempt < 2; attempt++) {
			DataEntity persisted = entity.toDataEntity();
			assertNotNull(persisted);
			entity = EdgeDataEntity.fromDataEntity(persisted);
			assertNotNull(entity);
			Event restored = entity.getEvent();
			assertEquals(request.getName(), restored.getName());
			assertEquals(request.getType(), restored.getType());
			assertEquals(request.getSource(), restored.getSource());
			assertEquals(request.getUniqueIdentifier(), restored.getUniqueIdentifier());
			assertEquals(request.getTimestamp(), restored.getTimestamp());
			assertEquals(request.getParentID(), restored.getParentID());
			assertEquals(request.getResponseID(), restored.getResponseID());
			assertArrayEquals(mask, restored.getMask());
			assertEquals(edgeConfig, entity.getConfiguration());
			assertEquals(identityMap, entity.getIdentityMap());
			JSONObject restoredData = EdgeJson.toJsonObjectPreservingNulls(restored.getEventData());
			assertTrue(restoredData.has("timezone"));
			assertTrue(restoredData.isNull("timezone"));
			assertTrue(restoredData.getJSONObject("tokens").has("pushNotification"));
			assertTrue(restoredData.getJSONObject("tokens").isNull("pushNotification"));
			assertEquals(2, restoredData.getJSONArray("values").length());
			assertTrue(restoredData.getJSONArray("values").isNull(0));
			assertEquals("UTC", restoredData.getJSONArray("values").getString(1));
		}
	}

	@Test
	public void testFromDataEntity_bypassWithoutNullPreservingDataPreservesEventMetadata() throws Exception {
		Map<String, Object> data = new HashMap<>();
		data.put("timezone", "UTC");
		String[] mask = { "timezone" };
		Event request = new Event.Builder(
			"Device Attributes",
			EdgeConstants.EventType.EDGE_BYPASS_CONSENT,
			EventSource.REQUEST_CONTENT,
			mask
		)
			.setEventData(data)
			.inResponseToEvent(event)
			.chainToParentEvent(event)
			.build();
		DataEntity persisted = new EdgeDataEntity(request, edgeConfig, identityMap).toDataEntity();
		assertNotNull(persisted);
		JSONObject serialized = new JSONObject(persisted.getData());
		serialized.remove("bypassEventData");

		EdgeDataEntity restored = EdgeDataEntity.fromDataEntity(new DataEntity(serialized.toString()));
		assertNotNull(restored);
		assertEquals(request.getUniqueIdentifier(), restored.getEvent().getUniqueIdentifier());
		assertEquals(request.getTimestamp(), restored.getEvent().getTimestamp());
		assertEquals(request.getParentID(), restored.getEvent().getParentID());
		assertEquals(request.getResponseID(), restored.getEvent().getResponseID());
		assertArrayEquals(mask, restored.getEvent().getMask());
		assertEquals(data, restored.getEvent().getEventData());
		assertEquals(edgeConfig, restored.getConfiguration());
		assertEquals(identityMap, restored.getIdentityMap());
	}

	@Test
	public void testToFromDataEntity_withNullIdentityMap_withNullConfiguration() {
		EdgeDataEntity entity = new EdgeDataEntity(event, null, null);
		DataEntity serializedEntity = entity.toDataEntity();
		assertNotNull(serializedEntity);

		EdgeDataEntity deserializedEntity = EdgeDataEntity.fromDataEntity(serializedEntity);
		assertNotNull(deserializedEntity);

		assertNotNull(deserializedEntity.getEvent());
		assertNotNull(deserializedEntity.getConfiguration());
		assertNotNull(deserializedEntity.getIdentityMap());

		// Assert Event
		Event deserializedEvent = deserializedEntity.getEvent();
		assertEquals(event.getName(), deserializedEvent.getName());
		assertEquals(event.getType(), deserializedEvent.getType());
		assertEquals(event.getSource(), deserializedEvent.getSource());
		assertEquals(event.getUniqueIdentifier(), deserializedEvent.getUniqueIdentifier());
		assertEquals(event.getTimestamp(), deserializedEvent.getTimestamp());
		assertEquals(event.getEventData(), deserializedEvent.getEventData());

		// Assert Configuration
		assertTrue(deserializedEntity.getConfiguration().isEmpty());

		// Assert IdentityMap
		assertTrue(deserializedEntity.getIdentityMap().isEmpty());
	}

	@Test
	public void testFromDataEntity_whenDataEntityWithNullData_returnsNull() {
		assertNull(EdgeDataEntity.fromDataEntity(new DataEntity(null)));
	}

	@Test
	public void testFromDataEntity_whenDataEntityWithEmptyData_returnsNull() {
		assertNull(EdgeDataEntity.fromDataEntity(new DataEntity("")));
	}

	@Test
	public void testFromDataEntity_whenInvalidDataEntity_returnsNull() {
		assertNull(EdgeDataEntity.fromDataEntity(new DataEntity("abc")));
	}
}
