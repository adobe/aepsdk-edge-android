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

import static com.adobe.marketing.mobile.services.HttpMethod.POST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
import com.adobe.marketing.mobile.util.TestUtils;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SampleFunctionalTests {

	private static final Event event1 = new Event.Builder("e1", "eventType", "eventSource").build();
	private static final Event event2 = new Event.Builder("e2", "eventType", "eventSource")
		.setEventData(
			new HashMap<String, Object>() {
				{
					put("test", "data");
				}
			}
		)
		.build();
	private static final Event event3 = new Event.Builder("e3", "unexpectedType", "unexpectedSource")
		.setEventData(
			new HashMap<String, Object>() {
				{
					put("test", "data");
				}
			}
		)
		.build();
	private static final String exEdgeInteractUrlString = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String responseBody = "{\"test\": \"json\"}";

	@Rule
	public RuleChain rule = RuleChain
		.outerRule(new TestHelper.LogOnErrorRule())
		.around(new TestHelper.SetupCoreRule())
		.around(new TestHelper.RegisterMonitorExtensionRule());

	@Before
	public void setup() throws Exception {
		// expectations for update config request&response events
		TestHelper.setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1);
		TestHelper.setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);
		// hub shared state update Edge, EventHub, Configuration, and Identity
		TestHelper.setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, 4);

		HashMap<String, Object> config = new HashMap<String, Object>() {
			{
				put("edge.configId", "12345-example");
			}
		};
		MobileCore.updateConfiguration(config);

		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.registerExtensions(Arrays.asList(Edge.EXTENSION, Identity.EXTENSION), o -> latch.countDown());
		latch.await();

		// Wait for and verify all expected events are received
		TestHelper.assertExpectedEvents(false);
		TestHelper.resetTestExpectations();
	}

	@Test
	public void testSample_AssertUnexpectedEvents() throws InterruptedException {
		// set event expectations specifying the event type, source and the count (count should be > 0)
		TestHelper.setExpectationEvent("eventType", "eventSource", 2);
		MobileCore.dispatchEvent(event1);
		MobileCore.dispatchEvent(event1);

		// assert that no unexpected event was received
		TestHelper.assertUnexpectedEvents();
	}

	@Test
	public void testSample_AssertExpectedEvents() throws InterruptedException {
		TestHelper.setExpectationEvent("eventType", "eventSource", 2);
		Event unexpectedEvent = new Event.Builder("e3", "unexpectedType", "unexpectedSource")
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("test", "data");
					}
				}
			)
			.build();
		MobileCore.dispatchEvent(event1);
		MobileCore.dispatchEvent(unexpectedEvent);
		MobileCore.dispatchEvent(event1);

		// assert all expected events were received and ignore any unexpected events
		// when ignoreUnexpectedEvents is set on false, an extra assertUnexpectedEvents step is performed
		TestHelper.assertExpectedEvents(true);
	}

	@Test
	public void testSample_DispatchedEvents() throws InterruptedException {
		MobileCore.dispatchEvent(event1); // eventType and eventSource
		MobileCore.dispatchEvent(event2); // eventType and eventSource
		MobileCore.dispatchEvent(event3); // unexpectedType and unexpectedSource

		// assert on count and data for events of a certain type, source
		List<Event> dispatchedEvents = TestHelper.getDispatchedEventsWith("eventType", "eventSource");

		assertEquals(2, dispatchedEvents.size());

		Map<String, Object> eventData = dispatchedEvents.get(1).getEventData();
		assertNotNull(eventData);

		assertEquals(1, TestUtils.flattenMap(eventData).size());
	}

	@Test
	public void testSample_AssertNetworkRequestsCount() throws InterruptedException {
		HttpConnecting responseConnection = TestHelper.createNetworkResponse(responseBody, 200);
		TestHelper.setNetworkResponseFor(exEdgeInteractUrlString, POST, responseConnection);
		TestHelper.setExpectationNetworkRequest(exEdgeInteractUrlString, POST, 2);

		ExperienceEvent experienceEvent1 = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("test1", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent1, null);

		ExperienceEvent experienceEvent2 = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("test2", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent2, null);

		TestHelper.assertNetworkRequestCount();
	}

	@Test
	public void testSample_AssertNetworkRequestAndResponseEvent() throws InterruptedException {
		TestHelper.setExpectationEvent(EventType.EDGE, EventSource.REQUEST_CONTENT, 1);
		TestHelper.setExpectationEvent(EventType.EDGE, "identity:exchange", 1);

		final String responseBody =
			"\u0000{\"requestId\":\"ded17427-c993-4182-8d94-2a169c1a23e2\",\"handle\":[{\"type\":\"identity:exchange\",\"payload\":[{\"type\":\"url\",\"id\":411,\"spec\":{\"url\":\"//cm.everesttech.net/cm/dd?d_uuid=42985602780892980519057012517360930936\",\"hideReferrer\":false,\"ttlMinutes\":10080}}]}]}\n";
		HttpConnecting responseConnection = TestHelper.createNetworkResponse(responseBody, 200);
		TestHelper.setNetworkResponseFor(exEdgeInteractUrlString, POST, responseConnection);
		TestHelper.setExpectationNetworkRequest(exEdgeInteractUrlString, POST, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "testType");
						put("test", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> requests = TestHelper.getNetworkRequestsWith(
			exEdgeInteractUrlString,
			POST
		);
		assertEquals(1, requests.size());

		Map<String, String> flattendRequestBody = TestHelper.getFlattenedNetworkRequestBody(requests.get(0));
		assertEquals("testType", flattendRequestBody.get("events[0].xdm.eventType"));

		TestHelper.assertExpectedEvents(true);
	}
}
