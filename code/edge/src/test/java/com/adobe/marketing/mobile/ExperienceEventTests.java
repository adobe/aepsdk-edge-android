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
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import com.adobe.marketing.mobile.xdm.Schema;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ExperienceEventTests {

	private final String xdmKey = "xdm";
	private final String dataKey = "data";
	private final String datasetKey = "datasetId";
	private final String configKey = "config";
	private final String datastreamIdOverrideKey = "datastreamIdOverride";
	private final String datastreamConfigOverrideKey = "datastreamConfigOverride";

	private Map<String, Object> generateXdmData() {
		Map<String, Object> xdmData = new HashMap<>();
		xdmData.put("testKey", "testValue");
		xdmData.put("testKey1", "testValue1");

		return xdmData;
	}

	private Map<String, Object> generateEventData() {
		String dataToSend = "testCustomText";
		float orderTotal = 110.10f;
		List<String> valuesList = new ArrayList<String>();
		valuesList.add("val1");
		valuesList.add("val2");

		Map<String, Object> eventData = new HashMap<String, Object>();
		eventData.put("test", "request");
		eventData.put("customText", dataToSend);
		eventData.put("listExample", valuesList);

		return eventData;
	}

	private class MobileSDKSchema implements Schema {

		public String version = "1.4";
		public String schemaId = "https://ns.adobe.com/acopprod1/schemas/e1af53c26439f963fbfebe50330323ae";
		public String datasetId = "5dd603781b95cc18a83d42ce";
		public Map<String, Object> data = new HashMap<String, Object>() {
			{
				put("schemaKey", "schemaValue");
			}
		};

		@Override
		public String getSchemaVersion() {
			return version;
		}

		@Override
		public String getSchemaIdentifier() {
			return schemaId;
		}

		@Override
		public String getDatasetIdentifier() {
			return datasetId;
		}

		@Override
		public Map<String, Object> serializeToXdm() {
			return data;
		}
	}

	@Test
	public void testExperienceEvent_withXdmMapAndDataMap_toObjectMapIsCorrect() {
		final Map<String, Object> expectedXdm = new HashMap<String, Object>() {
			{
				put("xdmKey", "xdmValue");
			}
		};
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder().setXdmSchema(expectedXdm).setData(expectedData).build();

		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedXdm);
				put(dataKey, expectedData);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmMapAndDatasetIdAndDataMap_toObjectMapIsCorrect() {
		final Map<String, Object> expectedXdm = new HashMap<String, Object>() {
			{
				put("xdmKey", "xdmValue");
			}
		};
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedXdm, "5dd603781b95cc18a83d42ce")
			.setData(expectedData)
			.build();

		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedXdm);
				put(dataKey, expectedData);
				put(datasetKey, "5dd603781b95cc18a83d42ce");
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmMapAndEmptyDatasetIdAndDataMap_toObjectMapIsCorrect() {
		final Map<String, Object> expectedXdm = new HashMap<String, Object>() {
			{
				put("xdmKey", "xdmValue");
			}
		};
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedXdm, "")
			.setData(expectedData)
			.build();

		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedXdm);
				put(dataKey, expectedData);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmMapAndNullDatasetIdAndDataMap_toObjectMapIsCorrect() {
		final Map<String, Object> expectedXdm = new HashMap<String, Object>() {
			{
				put("xdmKey", "xdmValue");
			}
		};
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedXdm, null)
			.setData(expectedData)
			.build();

		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedXdm);
				put(dataKey, expectedData);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmMapAndDataMapAndDatastreamIdOverride_toObjectMapIsCorrect() {
		final Map<String, Object> expectedXdm = new HashMap<String, Object>() {
			{
				put("xdmKey", "xdmValue");
			}
		};
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};
		final String expectedDatastreamIdOverride = "testDatastreamId";

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedXdm)
			.setData(expectedData)
			.setDatastreamIdOverride(expectedDatastreamIdOverride)
			.build();

		Map<String, Object> expectedConfigMap = new HashMap<String, Object>() {
			{
				put(datastreamIdOverrideKey, expectedDatastreamIdOverride);
			}
		};
		Map<String, Object> expectedMap = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedXdm);
				put(dataKey, expectedData);
				put(configKey, expectedConfigMap);
			}
		};

		assertEquals(expectedMap, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmMapAndDataMapAndDatastreamConfigOverride_toObjectMapIsCorrect() {
		final Map<String, Object> expectedXdm = new HashMap<String, Object>() {
			{
				put("xdmKey", "xdmValue");
			}
		};
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		final Map<String, Object> expectedDatastreamConfigOverride = new HashMap<String, Object>() {
			{
				put("key", "value");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedXdm)
			.setData(expectedData)
			.setDatastreamConfigOverride(expectedDatastreamConfigOverride)
			.build();

		Map<String, Object> expectedConfigMap = new HashMap<String, Object>() {
			{
				put(datastreamConfigOverrideKey, expectedDatastreamConfigOverride);
			}
		};
		Map<String, Object> expectedMap = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedXdm);
				put(dataKey, expectedData);
				put(configKey, expectedConfigMap);
			}
		};

		assertEquals(expectedMap, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmMapAndDataMapAndDatastreamIdOverrideAndDatastreamConfigOverride_toObjectMapIsCorrect() {
		final Map<String, Object> expectedXdm = new HashMap<String, Object>() {
			{
				put("xdmKey", "xdmValue");
			}
		};
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		final Map<String, Object> expectedDatastreamConfigOverride = new HashMap<String, Object>() {
			{
				put("key", "value");
			}
		};

		final String expectedDatastreamIdOverride = "testDatastreamId";

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedXdm)
			.setData(expectedData)
			.setDatastreamConfigOverride(expectedDatastreamConfigOverride)
			.setDatastreamIdOverride(expectedDatastreamIdOverride)
			.build();

		Map<String, Object> expectedConfigMap = new HashMap<String, Object>() {
			{
				put(datastreamIdOverrideKey, expectedDatastreamIdOverride);
				put(datastreamConfigOverrideKey, expectedDatastreamConfigOverride);
			}
		};
		Map<String, Object> expectedMap = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedXdm);
				put(dataKey, expectedData);
				put(configKey, expectedConfigMap);
			}
		};

		assertEquals(expectedMap, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmSchemaAndDataMap_toObjectMapIsCorrect() {
		final MobileSDKSchema expectedSchema = new MobileSDKSchema();
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedSchema)
			.setData(expectedData)
			.build();

		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedSchema.data);
				put(dataKey, expectedData);
				put(datasetKey, expectedSchema.datasetId);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmSchemaWithEmptyDatasetIdAndDataMap_toObjectMapIsCorrect() {
		final MobileSDKSchema expectedSchema = new MobileSDKSchema();
		expectedSchema.datasetId = "";
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedSchema)
			.setData(expectedData)
			.build();

		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedSchema.data);
				put(dataKey, expectedData);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmSchemaWithNullDatasetIdAndDataMap_toObjectMapIsCorrect() {
		final MobileSDKSchema expectedSchema = new MobileSDKSchema();
		expectedSchema.datasetId = null;
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedSchema)
			.setData(expectedData)
			.build();

		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedSchema.data);
				put(dataKey, expectedData);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmSchemaAndDataMapAndDatastreamIdOverride_toObjectMapIsCorrect() {
		final MobileSDKSchema expectedSchema = new MobileSDKSchema();
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		final String expectedDatastreamIdOverride = "testDatastreamId";

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedSchema)
			.setData(expectedData)
			.setDatastreamIdOverride(expectedDatastreamIdOverride)
			.build();

		Map<String, Object> expectedConfigMap = new HashMap<String, Object>() {
			{
				put(datastreamIdOverrideKey, expectedDatastreamIdOverride);
			}
		};
		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedSchema.data);
				put(dataKey, expectedData);
				put(configKey, expectedConfigMap);
				put(datasetKey, expectedSchema.datasetId);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmSchemaAndDataMapAndDatastreamConfigOverride_toObjectMapIsCorrect() {
		final MobileSDKSchema expectedSchema = new MobileSDKSchema();
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		final Map<String, Object> expectedDatastreamConfigOverride = new HashMap<String, Object>() {
			{
				put("key", "value");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedSchema)
			.setData(expectedData)
			.setDatastreamConfigOverride(expectedDatastreamConfigOverride)
			.build();

		Map<String, Object> expectedConfigMap = new HashMap<String, Object>() {
			{
				put(datastreamConfigOverrideKey, expectedDatastreamConfigOverride);
			}
		};
		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedSchema.data);
				put(dataKey, expectedData);
				put(configKey, expectedConfigMap);
				put(datasetKey, expectedSchema.datasetId);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withXdmSchemaAndDataMapAndDatastreamIdOverrideAndDatastreamConfigOverride_toObjectMapIsCorrect() {
		final MobileSDKSchema expectedSchema = new MobileSDKSchema();
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		final String expectedDatastreamIdOverride = "testDatastreamId";

		final Map<String, Object> expectedDatastreamConfigOverride = new HashMap<String, Object>() {
			{
				put("key", "value");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(expectedSchema)
			.setData(expectedData)
			.setDatastreamIdOverride(expectedDatastreamIdOverride)
			.setDatastreamConfigOverride(expectedDatastreamConfigOverride)
			.build();

		Map<String, Object> expectedConfigMap = new HashMap<String, Object>() {
			{
				put(datastreamIdOverrideKey, expectedDatastreamIdOverride);
				put(datastreamConfigOverrideKey, expectedDatastreamConfigOverride);
			}
		};
		Map<String, Object> expectedObj = new HashMap<String, Object>() {
			{
				put(xdmKey, expectedSchema.data);
				put(dataKey, expectedData);
				put(configKey, expectedConfigMap);
				put(datasetKey, expectedSchema.datasetId);
			}
		};

		assertEquals(expectedObj, event.toObjectMap());
	}

	@Test
	public void testExperienceEvent_withNullXdmMapAndDataMap_buildReturnsNull() {
		final Map<String, Object> expectedData = new HashMap<String, Object>() {
			{
				put("dataKey", "dataValue");
			}
		};

		ExperienceEvent event = new ExperienceEvent.Builder().setXdmSchema(null, null).setData(expectedData).build();
		assertNull(event);
	}

	@Test
	public void testExperienceEvent_setDataMutated() {
		Map<String, Object> eventData = generateEventData();

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(generateXdmData())
			.setData(eventData)
			.build();

		// This change should NOT be reflected in the event's copy of eventData
		eventData.put("newKey", "newValue");
		assertTrue(eventData.size() > event.getData().size());
	}

	@Test
	public void testExperienceEvent_setDatastreamConfigOverrideMutated() {
		Map<String, Object> configOverrides = generateEventData();

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(generateXdmData())
			.setDatastreamConfigOverride(configOverrides)
			.build();

		// This change should NOT be reflected in the event's copy of eventData
		configOverrides.put("newKey", "newValue");
		assertTrue(configOverrides.size() > event.getDatastreamConfigOverride().size());
	}

	@Test
	public void testExperienceEvent_setXdmSchema() {
		Map<String, Object> xdmSchema = generateXdmData();

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(xdmSchema)
			.setData(generateEventData())
			.build();

		// This change should NOT be reflected in the event's copy of xdm schema
		xdmSchema.put("newKey", "newValue");
		assertTrue(xdmSchema.size() > event.getXdmSchema().size());
	}

	@Test
	public void testExperienceEvent_getDataDeepCopies() {
		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(generateXdmData())
			.setData(generateEventData())
			.build();

		// This change should NOT be reflected in the event's copy of eventData
		event.getData().put("newKey", "newValue");
		assertFalse(event.getData().containsKey("newKey"));
	}

	@Test
	public void testExperienceEvent_getDatastreamId() {
		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(generateXdmData())
			.setDatastreamIdOverride("testDatastreamId")
			.build();

		assertEquals("testDatastreamId", event.getDatastreamIdOverride());
	}

	@Test
	public void testExperienceEvent_getDatastreamIdOverrideDeepCopies() {
		Map<String, Object> configOverrides = generateEventData();

		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(generateXdmData())
			.setDatastreamConfigOverride(configOverrides)
			.build();

		// This change should NOT be reflected in the event's copy of datastreamConfigOverrides
		event.getDatastreamConfigOverride().put("newKey", "newValue");
		assertFalse(event.getData().containsKey("newKey"));
	}

	@Test
	public void testExperienceEventBuilderBuild_withoutXdm_returnsNull() {
		assertNull(new ExperienceEvent.Builder().build());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testExperienceEventBuilderBuild_whenBuiltTwice_throws() {
		ExperienceEvent.Builder builder = new ExperienceEvent.Builder();
		builder.setXdmSchema(new MobileSDKSchema());
		ExperienceEvent event = builder.build();
		assertNotNull(event);
		builder.build(); // expect exception
	}
}
