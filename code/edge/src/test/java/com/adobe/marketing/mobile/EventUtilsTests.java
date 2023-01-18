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

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class EventUtilsTests {

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

		assertEquals(3, result.size());
		assertEquals("123", result.get("edge.configId"));
		assertEquals("prod", result.get("edge.environment"));
		assertEquals("my.domain.com", result.get("edge.domain"));
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

		assertEquals(1, result.size());
		assertEquals("123", result.get("edge.configId"));
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

		assertEquals(1, result.size());
		assertEquals("prod", result.get("edge.environment"));
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

		assertEquals(1, result.size());
		assertEquals("my.domain.com", result.get("edge.domain"));
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
}
