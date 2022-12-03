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
	public void testHear_WhenParentExtensionNull() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Request Content",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_CONTENT
		)
			.build();
		doReturn(null).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleExperienceEventRequest(any(Event.class));
	}

	@Test
	public void testHear_WhenEventNull() throws Exception {
		// setup
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(null);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleExperienceEventRequest(any(Event.class));
		verify(mockEdgeExtension, times(0)).handleConsentUpdate(any(Event.class));
		verify(mockEdgeExtension, times(0)).handleConsentPreferencesUpdate(any(Event.class));
		verify(mockEdgeExtension, times(0)).handleSharedStateUpdate(any(Event.class));
	}

	@Test
	public void testHear_edgeRequestContent() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Request Content",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_CONTENT
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("xdm", "example");
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
		verify(mockEdgeExtension, times(1)).handleExperienceEventRequest(event);
	}

	@Test
	public void testHear_edgeRequestContent_whenEmptyData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Request Content",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_CONTENT
		)
			.setEventData(new HashMap<String, Object>())
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleExperienceEventRequest(event);
	}

	@Test
	public void testHear_edgeUpdateConsent() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Update Consent",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_CONSENT
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
		verify(mockEdgeExtension, times(1)).handleConsentUpdate(event);
	}

	@Test
	public void testHear_edgeUpdateConsent_whenEmptyData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Update Consent",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_CONSENT
		)
			.setEventData(new HashMap<String, Object>())
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleConsentUpdate(event);
	}

	@Test
	public void testHear_consentPreferencesUpdated() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Consent preferences",
			EdgeConstants.EventType.CONSENT,
			EdgeConstants.EventSource.RESPONSE_CONTENT
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
	public void testHear_consentPreferencesUpdated_whenEmptyData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Consent preferences",
			EdgeConstants.EventType.CONSENT,
			EdgeConstants.EventSource.RESPONSE_CONTENT
		)
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleConsentPreferencesUpdate(event);
	}

	@Test
	public void testHear_sharedStateUpdate() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Shared State Update",
			EdgeConstants.EventType.ADOBE_HUB,
			EdgeConstants.EventSource.ADOBE_SHARED_STATE
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
	public void testHear_edgeIdentityResetComplete() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Identity Reset Complete",
			EdgeConstants.EventType.EDGE_IDENTITY,
			EdgeConstants.EventSource.RESET_COMPLETE
		)
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(1)).handleResetComplete(event);
	}

	@Test
	public void testHear_sharedStateUpdate_whenEmptyData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Shared State Update",
			EdgeConstants.EventType.ADOBE_HUB,
			EdgeConstants.EventSource.ADOBE_SHARED_STATE
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

	@Test
	public void testHear_getLocationHintEvent() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Get Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKey.LOCATION_HINT, true);
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
		verify(mockEdgeExtension, times(1)).handleGetLocationHint(event);
	}

	@Test
	public void testHear_getLocationHintEvent_whenFalseLocationHint() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Get Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKey.LOCATION_HINT, false);
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
		verify(mockEdgeExtension, times(0)).handleGetLocationHint(event);
	}

	@Test
	public void testHear_getLocationHintEvent_whenNoLocationHint() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Get Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKey.IDENTITY_MAP, false);
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
		verify(mockEdgeExtension, times(0)).handleGetLocationHint(event);
	}

	@Test
	public void testHear_getLocationHintEvent_whenEmptyData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Get Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_IDENTITY
		)
			.setEventData(new HashMap<String, Object>())
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleGetLocationHint(event);
	}

	@Test
	public void testHear_getLocationHintEvent_whenNoData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Get Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.REQUEST_IDENTITY
		)
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleGetLocationHint(event);
	}

	@Test
	public void testHear_setLocationHintEvent() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Set Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKey.LOCATION_HINT, "or2");
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
		verify(mockEdgeExtension, times(1)).handleSetLocationHint(event);
	}

	@Test
	public void testHear_setLocationHintEvent_whenNoLocationHint() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Set Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKey.IDENTITY_MAP, false);
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
		verify(mockEdgeExtension, times(0)).handleSetLocationHint(event);
	}

	@Test
	public void testHear_setLocationHintEvent_whenEmptyData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Set Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.setEventData(new HashMap<String, Object>())
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleSetLocationHint(event);
	}

	@Test
	public void testHear_setLocationHintEvent_whenNoData() throws Exception {
		// setup
		Event event = new Event.Builder(
			"Edge Set Location Hint",
			EdgeConstants.EventType.EDGE,
			EdgeConstants.EventSource.UPDATE_IDENTITY
		)
			.build();
		doReturn(testExecutor).when(mockEdgeExtension).getExecutor();
		doReturn(mockEdgeExtension).when(listener).getParentExtension();

		// test
		listener.hear(event);

		// verify
		testExecutor.awaitTermination(100, TimeUnit.MILLISECONDS);
		verify(mockEdgeExtension, times(0)).handleSetLocationHint(event);
	}
	
	 */
}
