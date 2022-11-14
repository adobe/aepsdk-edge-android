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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class UtilsTests {

	@Test
	public void testUtils_deepCopyNull() {
		assertNull(Utils.deepCopy((Map) null));
	}

	@Test
	public void testUtils_deepCopyEmpty() {
		Map<String, Object> emptyMap = new HashMap<>();
		assertEquals(0, Utils.deepCopy(emptyMap).size());
	}

	@Test
	public void testUtils_deepCopyValidSimpleNull() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		Map<String, Object> deepCopy = Utils.deepCopy(map);

		map = null;
		assertNotNull(deepCopy);
	}

	@Test
	public void testUtils_deepCopyValidSimple() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");

		Map<String, Object> deepCopy = Utils.deepCopy(map);
		deepCopy.remove("key1");

		assertTrue(map.containsKey("key1"));
	}

	@Test
	public void testUtils_deepCopyValidNested() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");

		Map<String, Object> nested = new HashMap<>();
		nested.put("nestedKey", "nestedValue");
		map.put("nestedMap", nested);

		Map<String, Object> deepCopy = Utils.deepCopy(map);
		Map<String, Object> nestedDeepCopy = (Map<String, Object>) deepCopy.get("nestedMap");
		nestedDeepCopy.put("newKey", "newValue");

		assertFalse(nested.size() == nestedDeepCopy.size());
	}

	@Test
	public void testUtils_deepCopyNullKey_returnsNull() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		map.put(null, "null");

		Map<String, Object> nested = new HashMap<>();
		nested.put("nestedKey", "nestedValue");
		map.put(null, "nestednull");

		assertNull(Utils.deepCopy(map));
	}

	@Test
	public void testPutIfNotNull_NonNull_InsertsCorrectly() {
		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotNull(map, "testKey", new Integer(2));

		assertEquals(1, map.size());
		assertEquals(2, map.get("testKey"));
	}

	@Test
	public void testPutIfNotNull_Null_NoInsert() {
		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotNull(map, "testKey", null);

		assertEquals(0, map.size());
	}

	@Test
	public void testUtils_deepCopyListOfMaps_Null() {
		assertNull(Utils.deepCopy((List) null));
	}

	@Test
	public void testUtils_deepCopyListOfMaps_Empty() {
		assertEquals(0, Utils.deepCopy(new ArrayList<Map<String, Object>>()).size());
	}

	@Test
	public void testUtils_deepCopyListOfMaps_ValidSimpleNull() {
		List<Map<String, Object>> list = new ArrayList<>();
		list.add(new HashMap<String, Object>());
		assertNotNull(Utils.deepCopy(list));
	}

	@Test
	public void testUtils_deepCopyListOfMaps_ValidSimple() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		List<Map<String, Object>> list = new ArrayList<>();
		list.add(map);

		List<Map<String, Object>> deepCopy = Utils.deepCopy(list);
		deepCopy.remove(0);

		assertEquals(1, list.size());
	}

	@Test
	public void testUtils_deepCopyListOfMaps_ValidNested() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");

		Map<String, Object> nested = new HashMap<>();
		nested.put("nestedKey", "nestedValue");
		map.put("nestedMap", nested);

		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		list.add(map);

		Map<String, Object> deepCopy = Utils.deepCopy(map);
		List<Map<String, Object>> nestedDeepCopy = Utils.deepCopy(list);
		nestedDeepCopy.get(0).put("newKey", "newValue");
		((Map<String, Object>) nestedDeepCopy.get(0).get("nestedMap")).put("nestedKey2", 2222);

		assertEquals(3, nestedDeepCopy.get(0).size());
		assertEquals(2, list.get(0).size());
		assertTrue(list.get(0).containsKey("key1"));
		assertEquals(1, ((Map<String, Object>) list.get(0).get("nestedMap")).size());
		assertEquals(2, ((Map<String, Object>) nestedDeepCopy.get(0).get("nestedMap")).size());
	}
}
