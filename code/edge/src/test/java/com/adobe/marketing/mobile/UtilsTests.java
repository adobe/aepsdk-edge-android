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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class UtilsTests {

	@Test
	public void testDeepCopy_whenNull() {
		assertNull(Utils.deepCopy((Map) null));
	}

	@Test
	public void testDeepCopy_whenEmpty() {
		Map<String, Object> emptyMap = new HashMap<>();
		assertEquals(0, Utils.deepCopy(emptyMap).size());
	}

	@Test
	public void testDeepCopy_whenValidSimple_thenSetOriginalNull() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		Map<String, Object> deepCopy = Utils.deepCopy(map);

		map = null;
		assertNotNull(deepCopy);
	}

	@Test
	public void testDeepCopy_whenValidSimple_thenMutateOriginal() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");

		Map<String, Object> deepCopy = Utils.deepCopy(map);
		deepCopy.remove("key1");

		assertTrue(map.containsKey("key1"));
	}

	@Test
	public void testDeepCopy_whenValidNested() {
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
	public void testDeepCopy_whenNullKey_ignoresKey() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		map.put(null, "null");

		Map<String, Object> result = Utils.deepCopy(map);
		assertEquals(1, result.size());
		assertEquals("value1", result.get("key1"));
	}

	@Test
	public void testDeepCopy_whenNullListOfMaps() {
		assertNull(Utils.deepCopy((List) null));
	}

	@Test
	public void testDeepCopy_whenEmptyListOfMaps() {
		assertEquals(0, Utils.deepCopy(new ArrayList<Map<String, Object>>()).size());
	}

	@Test
	public void testDeepCopy_whenListOfMapsEmpty() {
		List<Map<String, Object>> list = new ArrayList<>();
		list.add(new HashMap<String, Object>());
		assertNotNull(Utils.deepCopy(list));
	}

	@Test
	public void testDeepCopy_whenListOfMapsValidSimple() {
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		List<Map<String, Object>> list = new ArrayList<>();
		list.add(map);

		List<Map<String, Object>> deepCopy = Utils.deepCopy(list);
		deepCopy.remove(0);

		assertEquals(1, list.size());
	}

	@Test
	public void testDeepCopy_whenListOfMaps_ValidNested() {
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

	@Test
	public void testDeepCopy_whenInvalidMapWithCustomObjects_returnsNullAndNoThrow() {
		class CustomObj {

			private final int value;

			CustomObj(int value) {
				this.value = value;
			}
		}
		Map<String, Object> map = new HashMap<>();
		map.put("key1", "value1");
		map.put("key2", new CustomObj(1000));

		Map<String, Object> deepCopy = Utils.deepCopy(map);
		assertNull(deepCopy);
	}

	@Test
	public void testIsNullOrEmptyMap_whenNull() {
		assertTrue(Utils.isNullOrEmpty((Map) null));
	}

	@Test
	public void testIsNullOrEmptyMap_whenEmpty() {
		assertTrue(Utils.isNullOrEmpty(new HashMap<>()));
	}

	@Test
	public void testIsNullOrEmptyMap_whenNonEmpty() {
		Map<String, Object> test = new HashMap<>();
		test.put("key", "value");
		assertFalse(Utils.isNullOrEmpty(test));
	}

	@Test
	public void testIsNullOrEmptyJSONObject_whenNull() {
		assertTrue(Utils.isNullOrEmpty((JSONObject) null));
	}

	@Test
	public void testIsNullOrEmptyJSONObject_whenEmpty() {
		assertTrue(Utils.isNullOrEmpty(new JSONObject()));
	}

	@Test
	public void testIsNullOrEmptyJSONObject_whenNonEmpty() throws JSONException {
		JSONObject test = new JSONObject();
		test.put("key", "value");
		assertFalse(Utils.isNullOrEmpty(test));
	}

	@Test
	public void testIsNullOrEmptyJSONArray_whenNull() {
		assertTrue(Utils.isNullOrEmpty((JSONArray) null));
	}

	@Test
	public void testIsNullOrEmptyJSONArray_whenEmpty() {
		assertTrue(Utils.isNullOrEmpty(new JSONArray()));
	}

	@Test
	public void testIsNullOrEmptyJSONArray_whenNonEmpty() {
		JSONArray test = new JSONArray();
		test.put("test");
		assertFalse(Utils.isNullOrEmpty(test));
	}

	@Test
	public void testPutIfNotEmptyString_whenNullMap() {
		Utils.putIfNotEmpty(null, "key", "value");
	}

	@Test
	public void testPutIfNotEmptyString_whenNullKey() {
		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotEmpty(map, null, "value");
		assertTrue(map.isEmpty());
	}

	@Test
	public void testPutIfNotEmptyString_whenNullValue() {
		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotEmpty(map, "key", (String) null);
		assertTrue(map.isEmpty());
	}

	@Test
	public void testPutIfNotEmptyString_whenValidValue() {
		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotEmpty(map, "key", "value");
		assertEquals(1, map.size());
		assertEquals("value", map.get("key"));
	}

	@Test
	public void testPutIfNotEmptyMap_whenNullMap() {
		Map<String, Object> innerMap = new HashMap<>();
		innerMap.put("test", true);
		Utils.putIfNotEmpty(null, "key", innerMap);
	}

	@Test
	public void testPutIfNotEmptyMap_whenNullKey() {
		Map<String, Object> innerMap = new HashMap<>();
		innerMap.put("test", true);

		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotEmpty(map, null, innerMap);
		assertTrue(map.isEmpty());
	}

	@Test
	public void testPutIfNotEmptyMap_whenNullValue() {
		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotEmpty(map, "key", (Map) null);
		assertTrue(map.isEmpty());
	}

	@Test
	public void testPutIfNotEmptyMap_whenValidValue() {
		Map<String, Object> innerMap = new HashMap<>();
		innerMap.put("test", true);

		Map<String, Object> map = new HashMap<>();
		Utils.putIfNotEmpty(map, "key", innerMap);
		assertEquals(1, map.size());
		assertEquals(innerMap, map.get("key"));
	}

	@Test
	public void testToListOfMaps_whenNull() {
		assertNull(Utils.toListOfMaps(null));
	}

	@Test
	public void testToListOfMaps_whenNullElements() throws JSONException {
		JSONObject elem = new JSONObject();
		elem.put("test", "value");
		JSONArray test = new JSONArray();
		test.put(elem);
		test.put(null);
		test.put(elem);

		List<Map<String, Object>> result = Utils.toListOfMaps(test);
		assertEquals(2, result.size());
		assertEquals("value", result.get(0).get("test"));
		assertEquals("value", result.get(1).get("test"));
	}

	@Test
	public void testToListOfMaps_whenNotJSONObjects() throws JSONException {
		JSONObject elem = new JSONObject();
		elem.put("test", "value");
		JSONArray test = new JSONArray();
		test.put(100);
		test.put(null);
		test.put(elem);
		test.put("hello");

		List<Map<String, Object>> result = Utils.toListOfMaps(test);
		assertEquals(1, result.size());
		assertEquals("value", result.get(0).get("test"));
	}
}
