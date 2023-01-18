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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ImplementationDetailsTests {

	static final String BASE_NAMESPACE = "https://ns.adobe.com/experience/mobilesdk/android";
	static final String WRAPPER_REACT_NATIVE = "reactnative";
	static final String WRAPPER_CORDOVA = "cordova";
	static final String WRAPPER_FLUTTER = "flutter";
	static final String WRAPPER_UNITY = "unity";
	static final String WRAPPER_XAMARIN = "xamarin";

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperNone() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", WrapperType.NONE.getWrapperTag());

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperReactNative() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", WrapperType.REACT_NATIVE.getWrapperTag());

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/" + WRAPPER_REACT_NATIVE, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperFlutter() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", WrapperType.FLUTTER.getWrapperTag());

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/" + WRAPPER_FLUTTER, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperCordova() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", WrapperType.CORDOVA.getWrapperTag());

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/" + WRAPPER_CORDOVA, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperUnity() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", WrapperType.UNITY.getWrapperTag());

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/" + WRAPPER_UNITY, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperXamarin() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", WrapperType.XAMARIN.getWrapperTag());

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/" + WRAPPER_XAMARIN, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperUnknown() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", "A");

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/unknown", details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andNoWrapper() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", null);

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andWrapperEmpty() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", "");

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/unknown", details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andNoWrapperType() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", "R");
		// Has "wrapper" but no "type" object
		((Map<String, Object>) hubState.get("wrapper")).remove("type");

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/unknown", details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersion_andMalformedWrapper() {
		Map<String, Object> hubState = getEventHubSharedState("1.1.1", "R");
		hubState.put("wrapper", "not map"); // set as String, not expected Map type

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("1.1.1+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE + "/unknown", details.name);
	}

	@Test
	public void implementationDetails_hubstate_withNoVersion_andWrapperNone() {
		Map<String, Object> hubState = getEventHubSharedState(null, "N");

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("unknown+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withVersionEmpty_andWrapperNone() {
		Map<String, Object> hubState = getEventHubSharedState("", "N");

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("unknown+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE, details.name);
	}

	@Test
	public void implementationDetails_hubstate_withMalformedVersion_andWrapperNone() {
		Map<String, Object> hubState = getEventHubSharedState("1", "N");
		// set version as Map, not expected String type
		hubState.put(
			"version",
			new HashMap<String, Object>() {
				{
					put("key", "value");
				}
			}
		);

		Map<String, Object> map = ImplementationDetails.fromEventHubState(hubState);
		TestImplementationDetails details = new TestImplementationDetails(map);

		assertNotNull(details);
		assertEquals("app", details.environment);
		assertEquals("unknown+" + EdgeConstants.EXTENSION_VERSION, details.version);
		assertEquals(BASE_NAMESPACE, details.name);
	}

	@Test
	public void implementationDetails_hubstate_null() {
		Map<String, Object> map = ImplementationDetails.fromEventHubState(null);
		assertNull(map);
	}

	@Test
	public void implementationDetails_hubstate_empty() {
		Map<String, Object> map = ImplementationDetails.fromEventHubState(Collections.<String, Object>emptyMap());
		assertNull(map);
	}

	/**
	 * Helper to create a mock Event Hub shared state.
	 * @param coreVersion Core version to set, or null to not include "version" in state
	 * @param wrapperType Wrapper type to set, or null to not include "wrapper" in state
	 * @return mocked Event Hub shared state with given Core version and Wrapper type
	 */
	private Map<String, Object> getEventHubSharedState(final String coreVersion, final String wrapperType) {
		Map<String, Object> hubState = new HashMap<>();

		if (coreVersion != null) {
			hubState.put("version", coreVersion);
		}

		if (wrapperType != null) {
			hubState.put(
				"wrapper",
				new HashMap<String, Object>() {
					{
						put("type", wrapperType);
					}
				}
			);
		}

		return hubState;
	}

	private static class TestImplementationDetails {

		final String name;
		final String version;
		final String environment;

		public TestImplementationDetails(final Map<String, Object> map) {
			if (map == null || map.isEmpty()) {
				name = null;
				version = null;
				environment = null;
				return;
			}

			Map<String, Object> details = (Map<String, Object>) map.get("implementationDetails");
			name = (String) details.get("name");
			version = (String) details.get("version");
			environment = (String) details.get("environment");
		}
	}
}
