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

package com.adobe.marketing.mobile.xdm;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.Test;

public class FormattersTests {

	@Test
	public void dateToISO8601String_onValidTimestamp_returnsFormattedString() {
		Calendar cal = new Calendar.Builder()
			.set(Calendar.YEAR, 2019)
			.set(Calendar.MONTH, Calendar.SEPTEMBER)
			.set(Calendar.DAY_OF_MONTH, 23)
			.set(Calendar.HOUR, 11)
			.set(Calendar.MINUTE, 15)
			.set(Calendar.SECOND, 45)
			.setTimeZone(TimeZone.getTimeZone("America/Los_Angeles"))
			.build();

		String serializedDate = Formatters.dateToISO8601String(cal.getTime());
		// Expected time in UTC which is +7 hours from America/Los_Angeles during Daylight Savings
		assertEquals("2019-09-23T18:15:45Z", serializedDate);
	}

	@Test
	public void dateToISO8601String_onNull_returnsEmptyString() {
		String serializedDate = Formatters.dateToISO8601String(null);
		assertEquals("", serializedDate);
	}

	@Test
	public void dateToShortDateString_onValidTimestamp_returnsFormattedString() {
		Calendar cal = new Calendar.Builder()
			.set(Calendar.YEAR, 2019)
			.set(Calendar.MONTH, Calendar.SEPTEMBER)
			.set(Calendar.DAY_OF_MONTH, 23)
			.set(Calendar.HOUR, 11)
			.set(Calendar.MINUTE, 15)
			.set(Calendar.SECOND, 45)
			.build();

		String serializedDate = Formatters.dateToShortDateString(cal.getTime());
		assertEquals("2019-09-23", serializedDate);
	}

	@Test
	public void dateToShortDateString_onNull_returnsEmptyString() {
		String serializedDate = Formatters.dateToShortDateString(null);
		assertEquals("", serializedDate);
	}

	@Test
	public void serializeFromList_singlePropertyList_returnsMapWithSingleProperty() {
		List<TestPropertyA> propertyList = new ArrayList<>();
		propertyList.add(new TestPropertyA("single"));

		List<Map<String, Object>> result = Formatters.serializeFromList(propertyList);

		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("A", result.get(0).get("id"));
		assertEquals("single", result.get(0).get("key"));
	}

	@Test
	public void serializeFromList_multiplePropertyList_returnsMapWithMultipleProperties() {
		List<Property> propertyList = new ArrayList<>();
		propertyList.add(new TestPropertyA("one"));
		propertyList.add(new TestPropertyB("two"));

		List<Map<String, Object>> result = Formatters.serializeFromList(propertyList);

		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("one", result.get(0).get("key"));
		assertEquals("A", result.get(0).get("id"));
		assertEquals("two", result.get(1).get("key"));
		assertEquals("B", result.get(1).get("id"));
	}

	@Test
	public void serializeFromList_listWithNullProperties_returnsMapWhichIgnoresNullProperties() {
		List<Property> propertyList = new ArrayList<>();
		propertyList.add(new TestPropertyA("one"));
		propertyList.add(null);
		propertyList.add(new TestPropertyB("two"));

		List<Map<String, Object>> result = Formatters.serializeFromList(propertyList);

		assertNotNull(result);
		assertEquals(2, result.size());
		assertEquals("one", result.get(0).get("key"));
		assertEquals("A", result.get(0).get("id"));
		assertEquals("two", result.get(1).get("key"));
		assertEquals("B", result.get(1).get("id"));
	}

	@Test
	public void serializeFromList_onNull_returnsEmptyMap() {
		List<Map<String, Object>> result = Formatters.serializeFromList(null);
		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	public void serializeFromList_onEmpty_returnsEmptyMap() {
		List<Map<String, Object>> result = Formatters.serializeFromList(new ArrayList<Property>());
		assertNotNull(result);
		assertEquals(0, result.size());
	}

	private static class TestPropertyA implements Property {

		private String value;

		TestPropertyA(final String value) {
			this.value = value;
		}

		@Override
		public Map<String, Object> serializeToXdm() {
			Map<String, Object> map = new HashMap<>();
			map.put("id", "A");
			map.put("key", this.value);
			return map;
		}
	}

	private static class TestPropertyB implements Property {

		private String value;

		TestPropertyB(final String value) {
			this.value = value;
		}

		@Override
		public Map<String, Object> serializeToXdm() {
			Map<String, Object> map = new HashMap<>();
			map.put("id", "B");
			map.put("key", this.value);
			return map;
		}
	}
}
