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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class EdgeDataEntityTests {

	private Event event = new Event.Builder("test", "all", "things").build();
	private Map<String, Object> edgeConfig = new HashMap<String, Object>() {
		{
			put("edge.configId", "sample");
		}
	};
	private Map<String, Object> identityMap = new HashMap<String, Object>() {
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
	public void testSerializeDeserialize() {
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
		String serializedEntity = EdgeDataEntitySerializer.serialize(entity);
		EdgeDataEntity deserializedEntity = EdgeDataEntitySerializer.deserialize(serializedEntity);

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
	public void testSerializeDeserialize_withNullIdentityMap_withNullConfiguration() {
		EdgeDataEntity entity = new EdgeDataEntity(event, null, null);
		String serializedEntity = EdgeDataEntitySerializer.serialize(entity);
		EdgeDataEntity deserializedEntity = EdgeDataEntitySerializer.deserialize(serializedEntity);

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
	public void testSerializeNullEntity_returnsNull() {
		assertNull(EdgeDataEntitySerializer.serialize(null));
	}

	@Test
	public void testDeserializeNullEntity_returnsNull() {
		assertNull(EdgeDataEntitySerializer.deserialize(null));
	}

	@Test
	public void testDeserializeEmptyEntity_returnsNull() {
		assertNull(EdgeDataEntitySerializer.deserialize(""));
	}

	@Test
	public void testDeserializeInvalidJSONEntity_returnsNull() {
		assertNull(EdgeDataEntitySerializer.deserialize("abc"));
	}
}
