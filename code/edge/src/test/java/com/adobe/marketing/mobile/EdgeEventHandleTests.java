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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class EdgeEventHandleTests {

	@Test(expected = IllegalArgumentException.class)
	public void testEdgeEventHandle_whenNullHandle_throws() {
		new EdgeEventHandle(null);
	}

	@Test
	public void testEdgeEventHandle_whenHandleWithAllParams_parsesCorrectly() {
		JSONObject handleJson = new JSONObject() {
			{
				try {
					put("type", "testType");
					put("eventIndex", 10);
					put(
						"payload",
						new JSONArray() {
							{
								put(
									new JSONObject() {
										{
											put("key1", "value1");
											put("key2", 2);
											put("key3", true);
											put("key4", 13.66);
											put(
												"key5",
												new JSONArray() {
													{
														put("abc");
														put("def");
													}
												}
											);
										}
									}
								);
								put(
									new JSONObject() {
										{
											put(
												"key6",
												new JSONObject() {
													{
														put("multilevel", "payload");
													}
												}
											);
										}
									}
								);
							}
						}
					);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};

		List<Map<String, Object>> expectedPayload = new ArrayList<>();
		expectedPayload.add(
			new HashMap<String, Object>() {
				{
					put("key1", "value1");
					put("key2", 2);
					put("key3", true);
					put("key4", 13.66);
					put(
						"key5",
						new ArrayList<Object>() {
							{
								add("abc");
								add("def");
							}
						}
					);
				}
			}
		);
		expectedPayload.add(
			new HashMap<String, Object>() {
				{
					put(
						"key6",
						new HashMap<String, Object>() {
							{
								put("multilevel", "payload");
							}
						}
					);
				}
			}
		);
		EdgeEventHandle handle = new EdgeEventHandle(handleJson);

		assertNotNull(handle);
		assertEquals("testType", handle.getType());
		assertEquals(10, handle.getEventIndex());
		assertEquals(expectedPayload, handle.getPayload());
	}

	@Test
	public void testEdgeEventHandle_whenHandleWithMissingType_parsesCorrectly() {
		JSONObject handleJson = new JSONObject() {
			{
				try {
					put("eventIndex", 10);
					put(
						"payload",
						new JSONArray() {
							{
								put(
									new JSONObject() {
										{
											put("key1", "value1");
											put("key2", 2);
										}
									}
								);
							}
						}
					);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};

		List<Map<String, Object>> expectedPayload = new ArrayList<>();
		expectedPayload.add(
			new HashMap<String, Object>() {
				{
					put("key1", "value1");
					put("key2", 2);
				}
			}
		);
		EdgeEventHandle handle = new EdgeEventHandle(handleJson);

		assertNotNull(handle);
		assertNull(handle.getType());
		assertEquals(10, handle.getEventIndex());
		assertEquals(expectedPayload, handle.getPayload());
	}

	@Test
	public void testEdgeEventHandle_whenHandleWithMissingEventIndex_parsesCorrectly_defaultIndex0() {
		JSONObject handleJson = new JSONObject() {
			{
				try {
					put("type", "testType");
					put(
						"payload",
						new JSONArray() {
							{
								put(
									new JSONObject() {
										{
											put("key1", "value1");
											put("key2", 2);
										}
									}
								);
							}
						}
					);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		};

		List<Map<String, Object>> expectedPayload = new ArrayList<>();
		expectedPayload.add(
			new HashMap<String, Object>() {
				{
					put("key1", "value1");
					put("key2", 2);
				}
			}
		);
		EdgeEventHandle handle = new EdgeEventHandle(handleJson);

		assertNotNull(handle);
		assertEquals("testType", handle.getType());
		assertEquals(0, handle.getEventIndex());
		assertEquals(expectedPayload, handle.getPayload());
	}

	@Test
	public void testEdgeEventHandle_whenHandleWithMissingPayload_parsesCorrectly() throws Exception {
		JSONObject handleJson = new JSONObject() {
			{
				put("type", "testType");
			}
		};

		EdgeEventHandle handle = new EdgeEventHandle(handleJson);

		assertNotNull(handle);
		assertEquals("testType", handle.getType());
		assertEquals(0, handle.getEventIndex());
		assertNull(handle.getPayload());
	}

	@Test
	public void testEdgeEventHandle_whenHandleWithIncorrectPayloadType_parsesCorrectly() throws Exception {
		JSONObject handleJson = new JSONObject() {
			{
				put("type", "testType");
				put(
					"payload",
					new JSONObject() {
						{
							put("key1", "value1");
							put("key2", 2);
						}
					}
				);
			}
		};

		EdgeEventHandle handle = new EdgeEventHandle(handleJson);

		assertNotNull(handle);
		assertEquals("testType", handle.getType());
		assertEquals(0, handle.getEventIndex());
		assertNull(handle.getPayload());
	}
}
