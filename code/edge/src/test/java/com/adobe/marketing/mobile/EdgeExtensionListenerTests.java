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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.Mockito;

public class EdgeExtensionListenerTests {

	@Mock
	private EdgeExtension mockEdgeExtension;

	private ExecutorService testExecutor;

	@Before
	public void setup() {
		testExecutor = Executors.newSingleThreadExecutor();
		mockEdgeExtension = Mockito.mock(EdgeExtension.class);
		MobileCore.start(null);
	}
	/* TODO revisit event listener tests to see if they can be reused

	@Test
	public void testHear_consentPreferencesUpdated() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Consent preferences",
			EventType.CONSENT,
			EventSource.RESPONSE_CONTENT
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("consents", "example");
					}
				}
			)
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(1)).handleConsentPreferencesUpdate(event);
	}

	@Test
	public void testHear_sharedStateUpdate() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Shared State Update",
			EventType.HUB,
			EventSource.SHARED_STATE
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("stateowner", EdgeConstants.SharedState.IDENTITY);
					}
				}
			)
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(1)).handleSharedStateUpdate(event);
	}

	@Test
	public void testHear_sharedStateUpdate_whenEmptyData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Shared State Update",
			EventType.HUB,
			EventSource.SHARED_STATE
		)
			.setEventData(new HashMap<String, Object>())
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleSharedStateUpdate(event);
	}
	
	 */
}
