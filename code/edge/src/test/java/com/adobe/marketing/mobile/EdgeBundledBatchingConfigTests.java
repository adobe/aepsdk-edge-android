/*
  Copyright 2026 Adobe. All rights reserved.
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
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.DeviceInforming;
import com.adobe.marketing.mobile.services.ServiceProvider;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

public class EdgeBundledBatchingConfigTests {

	@Mock
	private ServiceProvider mockServiceProvider;

	@Mock
	private DeviceInforming mockDeviceInfoService;

	private MockedStatic<ServiceProvider> mockServiceProviderStatic;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
		mockServiceProviderStatic = mockStatic(ServiceProvider.class);
		mockServiceProviderStatic.when(ServiceProvider::getInstance).thenReturn(mockServiceProvider);
		when(mockServiceProvider.getDeviceInfoService()).thenReturn(mockDeviceInfoService);
		EdgeBundledBatchingConfig.resetForTesting();
	}

	@After
	public void tearDown() {
		mockServiceProviderStatic.close();
		EdgeBundledBatchingConfig.resetForTesting();
	}

	private InputStream streamOf(final String content) {
		return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void testGet_validJson_parsesBothKeys() {
		when(mockDeviceInfoService.getAsset(EdgeBundledBatchingConfig.BUNDLED_CONFIG_FILE_NAME))
			.thenReturn(
				streamOf(
					"{" +
					"\"edge.batching.enabled\": true," +
					"\"edge.batching.eventNameAllowlist\": [\"Edge Optimize Proposition Interaction Request\"]" +
					"}"
				)
			);

		Map<String, Object> result = EdgeBundledBatchingConfig.get();

		assertEquals(true, result.get("edge.batching.enabled"));
		assertEquals(
			Arrays.asList("Edge Optimize Proposition Interaction Request"),
			result.get("edge.batching.eventNameAllowlist")
		);
	}

	@Test
	public void testGet_fileNotPresentInAssets_returnsEmptyMap() {
		when(mockDeviceInfoService.getAsset(anyString())).thenReturn(null);

		Map<String, Object> result = EdgeBundledBatchingConfig.get();

		assertTrue(result.isEmpty());
	}

	@Test
	public void testGet_malformedJson_returnsEmptyMap() {
		when(mockDeviceInfoService.getAsset(anyString())).thenReturn(streamOf("not valid json"));

		Map<String, Object> result = EdgeBundledBatchingConfig.get();

		assertTrue(result.isEmpty());
	}

	@Test
	public void testGet_emptyFileContent_returnsEmptyMap() {
		when(mockDeviceInfoService.getAsset(anyString())).thenReturn(streamOf(""));

		Map<String, Object> result = EdgeBundledBatchingConfig.get();

		assertTrue(result.isEmpty());
	}

	@Test
	public void testGet_calledTwice_onlyReadsAssetOnce() {
		when(mockDeviceInfoService.getAsset(anyString())).thenReturn(streamOf("{\"edge.batching.enabled\": true}"));

		EdgeBundledBatchingConfig.get();
		EdgeBundledBatchingConfig.get();

		verify(mockDeviceInfoService, times(1)).getAsset(EdgeBundledBatchingConfig.BUNDLED_CONFIG_FILE_NAME);
	}
}
