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
import static org.mockito.Mockito.mockStatic;

import com.adobe.marketing.mobile.util.JSONAsserts;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

public class EventUtilsTests {

	private MockedStatic<EdgeBundledBatchingConfig> mockBundledBatchingConfigStatic;

	@Before
	public void setup() {
		mockBundledBatchingConfigStatic = mockStatic(EdgeBundledBatchingConfig.class);
		mockBundledBatchingConfigStatic.when(EdgeBundledBatchingConfig::get).thenReturn(Collections.emptyMap());
	}

	@After
	public void tearDown() {
		mockBundledBatchingConfigStatic.close();
	}

	@Test
	public void testGetEdgeConfiguration_allValidConfigs() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.configId", "123");
					put("edge.environment", "prod");
					put("edge.domain", "my.domain.com");
					put("anotherkey", true);
				}
			}
		);

		String expected =
			"{\n" +
			"  \"edge.configId\": \"123\",\n" +
			"  \"edge.domain\": \"my.domain.com\",\n" +
			"  \"edge.environment\": \"prod\"\n" +
			"}";
		JSONAsserts.assertEquals(expected, result);
	}

	@Test
	public void testGetEdgeConfiguration_validEdgeConfigId() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.configId", "123");
					put("anotherkey", true);
				}
			}
		);

		String expected = "{ \"edge.configId\": \"123\" }";
		JSONAsserts.assertEquals(expected, result);
	}

	@Test
	public void testGetEdgeConfiguration_nullEdgeConfigId() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.configId", null);
					put("anotherkey", true);
				}
			}
		);

		assertEquals(0, result.size());
	}

	@Test
	public void testGetEdgeConfiguration_EdgeConfigIdWrongValueType() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.configId", 5);
					put("anotherkey", true);
				}
			}
		);

		assertEquals(0, result.size());
	}

	@Test
	public void testGetEdgeConfiguration_validEdgeEnvironment() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.environment", "prod");
					put("anotherkey", true);
				}
			}
		);

		String expected = "{ \"edge.environment\": \"prod\" }";
		JSONAsserts.assertEquals(expected, result);
	}

	@Test
	public void testGetEdgeConfiguration_nullEdgeEnvironment() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.environment", null);
					put("anotherkey", true);
				}
			}
		);

		assertEquals(0, result.size());
	}

	@Test
	public void testGetEdgeConfiguration_EdgeEnvironmentWrongValueType() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.environment", 5);
					put("anotherkey", true);
				}
			}
		);

		assertEquals(0, result.size());
	}

	@Test
	public void testGetEdgeConfiguration_validEdgeDomain() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.domain", "my.domain.com");
					put("anotherkey", true);
				}
			}
		);

		String expected = "{ \"edge.domain\": \"my.domain.com\" }";
		JSONAsserts.assertEquals(expected, result);
	}

	@Test
	public void testGetEdgeConfiguration_nullEdgeDomain() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.domain", null);
					put("anotherkey", true);
				}
			}
		);

		assertEquals(0, result.size());
	}

	@Test
	public void testGetEdgeConfiguration_EdgeDomainWrongValueType() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("edge.domain", 5);
					put("anotherkey", true);
				}
			}
		);

		assertEquals(0, result.size());
	}

	@Test
	public void testGetEdgeConfiguration_missingEdgeConfigurationKeys() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("something", "else");
					put("anotherkey", true);
				}
			}
		);

		assertEquals(0, result.size());
	}

	// -------------------------------------------------------------------------
	// Bundled batching config fallback (per-key)
	// -------------------------------------------------------------------------

	@Test
	public void testGetEdgeConfiguration_batchingEnabled_presentInSharedState_bundledConfigIgnored() {
		mockBundledBatchingConfigStatic
			.when(EdgeBundledBatchingConfig::get)
			.thenReturn(Collections.singletonMap("edge.batching.enabled", false));

		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.batching.enabled", true);
				}
			}
		);

		assertEquals(true, result.get("edge.batching.enabled"));
	}

	@Test
	public void testGetEdgeConfiguration_batchingEnabled_absentFromSharedState_usesBundledConfig() {
		mockBundledBatchingConfigStatic
			.when(EdgeBundledBatchingConfig::get)
			.thenReturn(Collections.singletonMap("edge.batching.enabled", true));

		Map<String, Object> result = EventUtils.getEdgeConfiguration(new HashMap<>());

		assertEquals(true, result.get("edge.batching.enabled"));
	}

	@Test
	public void testGetEdgeConfiguration_batchingEnabled_absentFromBoth_keyNotInResult() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(new HashMap<>());

		assertEquals(false, result.containsKey("edge.batching.enabled"));
	}

	@Test
	public void testGetEdgeConfiguration_eventNameAllowlist_presentInSharedState_bundledConfigIgnored() {
		mockBundledBatchingConfigStatic
			.when(EdgeBundledBatchingConfig::get)
			.thenReturn(Collections.singletonMap("edge.batching.eventNameAllowlist", Arrays.asList("BundledEvent")));

		Map<String, Object> result = EventUtils.getEdgeConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.batching.eventNameAllowlist", Arrays.asList("SharedStateEvent"));
				}
			}
		);

		assertEquals(Arrays.asList("SharedStateEvent"), result.get("edge.batching.eventNameAllowlist"));
	}

	@Test
	public void testGetEdgeConfiguration_eventNameAllowlist_absentFromSharedState_usesBundledConfig() {
		mockBundledBatchingConfigStatic
			.when(EdgeBundledBatchingConfig::get)
			.thenReturn(Collections.singletonMap("edge.batching.eventNameAllowlist", Arrays.asList("BundledEvent")));

		Map<String, Object> result = EventUtils.getEdgeConfiguration(new HashMap<>());

		assertEquals(Arrays.asList("BundledEvent"), result.get("edge.batching.eventNameAllowlist"));
	}

	@Test
	public void testGetEdgeConfiguration_eventNameAllowlist_absentFromBoth_keyNotInResult() {
		Map<String, Object> result = EventUtils.getEdgeConfiguration(new HashMap<>());

		assertEquals(false, result.containsKey("edge.batching.eventNameAllowlist"));
	}
}
