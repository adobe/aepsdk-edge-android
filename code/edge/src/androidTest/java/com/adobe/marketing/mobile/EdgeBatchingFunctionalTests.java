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

import static com.adobe.marketing.mobile.services.HttpMethod.POST;
import static com.adobe.marketing.mobile.util.TestHelper.LogOnErrorRule;
import static com.adobe.marketing.mobile.util.TestHelper.SetupCoreRule;
import static com.adobe.marketing.mobile.util.TestHelper.assertExpectedEvents;
import static com.adobe.marketing.mobile.util.TestHelper.resetTestExpectations;
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.MockNetworkService;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * End-to-end functional coverage for Experience Event batching: config plumbing through the real
 * {@link EdgeExtension} / {@link EdgeBatchingHitQueue}, allowlist gating, the non-batching (default)
 * path, and per-event completion-callback consistency when a batch is coalesced into one request.
 *
 * <p>Batching is timing-sensitive: a batch only forms when more than one event is queued before a
 * processing cycle sends the head. These tests make batch formation deterministic with
 * {@link MockNetworkService#enableNetworkResponseDelay(int)}, which blocks the queue's single worker
 * thread inside the first network call long enough for the remaining events to enqueue and coalesce
 * on the next cycle. Assertions are written to hold regardless of exactly how the events distribute
 * across the (1 or 2) resulting requests, so they are not flaky.
 *
 * <p>All events are sent via {@link Edge#sendEvent}, which names the dispatched Edge request event
 * {@code "AEP Request Event"} — hence the allowlist value used here.
 */
@RunWith(AndroidJUnit4.class)
public class EdgeBatchingFunctionalTests {

	private static final MockNetworkService mockNetworkService = new MockNetworkService();
	private static final String EXEDGE_INTERACT_URL_STRING = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";

	// Name of the Edge request event dispatched by Edge.sendEvent (EdgeConstants.EventName.REQUEST_CONTENT).
	private static final String EDGE_REQUEST_EVENT_NAME = "AEP Request Event";

	@Rule
	public RuleChain rule = RuleChain.outerRule(new LogOnErrorRule()).around(new SetupCoreRule());

	@Before
	public void setup() throws Exception {
		ServiceProvider.getInstance().setNetworkService(mockNetworkService);

		setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);
		setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, 4); // Edge, Config, Identity, Hub

		HashMap<String, Object> config = new HashMap<String, Object>() {
			{
				put("edge.configId", CONFIG_ID);
			}
		};
		MobileCore.updateConfiguration(config);

		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, Identity.EXTENSION, MonitorExtension.EXTENSION),
			o -> latch.countDown()
		);
		latch.await();
		assertExpectedEvents(false);
		resetTestExpectations();
	}

	@After
	public void tearDown() {
		mockNetworkService.reset();
	}

	// -------------------------------------------------------------------------
	// Non-batching (default) — regression guard for users who never enable batching
	// -------------------------------------------------------------------------

	@Test
	public void testBatchingDisabled_multipleEvents_sentAsIndividualRequests() throws InterruptedException {
		setDefaultResponse();

		final CountDownLatch callbacks = sendEvents(3);

		assertTrue("Timeout waiting for all event callbacks.", callbacks.await(5, TimeUnit.SECONDS));

		final List<TestableNetworkRequest> requests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		// Batching off (default): one request per event, each carrying exactly one event.
		assertEquals(3, requests.size());
		for (final TestableNetworkRequest request : requests) {
			assertEquals(1, eventCountInBody(request));
		}
	}

	// -------------------------------------------------------------------------
	// Batching enabled + allowlisted — events coalesce into fewer requests
	// -------------------------------------------------------------------------

	@Test
	public void testBatchingEnabledAndAllowlisted_multipleEvents_coalescedIntoFewerRequests()
		throws InterruptedException {
		applyBatchingConfig(true, Arrays.asList(EDGE_REQUEST_EVENT_NAME));
		setDefaultResponse();
		// Hold the first cycle in the network so the remaining events enqueue and coalesce.
		mockNetworkService.enableNetworkResponseDelay(1);

		final CountDownLatch callbacks = sendEvents(3);

		assertTrue("Timeout waiting for all event callbacks.", callbacks.await(10, TimeUnit.SECONDS));

		final List<TestableNetworkRequest> requests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);

		// Batching happened → fewer requests than events, and at least one request carries > 1 event.
		assertTrue("Expected fewer requests than events when batching, got " + requests.size(), requests.size() < 3);

		int totalEvents = 0;
		int maxEventsInOneRequest = 0;
		for (final TestableNetworkRequest request : requests) {
			final int count = eventCountInBody(request);
			totalEvents += count;
			maxEventsInOneRequest = Math.max(maxEventsInOneRequest, count);
		}
		// No event is lost or duplicated regardless of how they distribute across requests.
		assertEquals(3, totalEvents);
		assertTrue("Expected at least one coalesced request with >1 event.", maxEventsInOneRequest > 1);
		// Every event's completion callback still fired exactly once (per-event completion consistency).
	}

	// -------------------------------------------------------------------------
	// Batching enabled but event name not allowlisted — allowlist gate
	// -------------------------------------------------------------------------

	@Test
	public void testBatchingEnabledButNotAllowlisted_multipleEvents_sentAsIndividualRequests()
		throws InterruptedException {
		applyBatchingConfig(true, Arrays.asList("some.other.event.name"));
		setDefaultResponse();

		final CountDownLatch callbacks = sendEvents(3);

		assertTrue("Timeout waiting for all event callbacks.", callbacks.await(5, TimeUnit.SECONDS));

		final List<TestableNetworkRequest> requests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		// Batching enabled but the event name is not allowlisted → still one event per request.
		assertEquals(3, requests.size());
		for (final TestableNetworkRequest request : requests) {
			assertEquals(1, eventCountInBody(request));
		}
	}

	// -------------------------------------------------------------------------
	// Batching + server error — each batched event still completes its callback
	// -------------------------------------------------------------------------

	@Test
	public void testBatchingEnabledAndAllowlisted_serverError_stillCompletesEachEventCallback()
		throws InterruptedException {
		applyBatchingConfig(true, Arrays.asList(EDGE_REQUEST_EVENT_NAME));
		// Non-recoverable server error for every request; each event must still complete its callback.
		final HttpConnecting errorResponse = mockNetworkService.createMockNetworkResponse(
			"{\"type\":\"https://ns.adobe.com/aep/errors/EXEG-0104-422\",\"status\":422,\"title\":\"Unprocessable\"}",
			422
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, errorResponse);
		mockNetworkService.enableNetworkResponseDelay(1);

		final CountDownLatch callbacks = sendEvents(3);

		// Every event's completion callback fires even though the batch got a non-recoverable error.
		assertTrue(
			"Timeout waiting for all event callbacks after server error.",
			callbacks.await(10, TimeUnit.SECONDS)
		);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Sends {@code count} identical Experience Events and returns a latch that reaches zero once every
	 * event's completion callback has fired.
	 */
	private CountDownLatch sendEvents(final int count) {
		final CountDownLatch latch = new CountDownLatch(count);
		final AtomicInteger index = new AtomicInteger(0);
		for (int i = 0; i < count; i++) {
			final int n = index.getAndIncrement();
			final ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
				.setXdmSchema(
					new HashMap<String, Object>() {
						{
							put("eventType", "batchingFunctionalTest");
							put("index", n);
						}
					}
				)
				.build();
			Edge.sendEvent(experienceEvent, handles -> latch.countDown());
		}
		return latch;
	}

	/** Merges the batching keys into the Configuration shared state and waits for it to apply. */
	private void applyBatchingConfig(final boolean enabled, final List<String> allowlist) throws InterruptedException {
		setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);
		final HashMap<String, Object> config = new HashMap<>();
		config.put("edge.batching.enabled", enabled);
		config.put("edge.batching.eventNameAllowlist", allowlist);
		MobileCore.updateConfiguration(config);
		// Wait for the configuration response (barrier: config shared state is now applied), ignoring the
		// other events updateConfiguration also emits (CONFIGURATION request, HUB shared state).
		assertExpectedEvents(true);
		resetTestExpectations();
	}

	private void setDefaultResponse() {
		final HttpConnecting response = mockNetworkService.createMockNetworkResponse("{}", 200);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, response);
	}

	/** Parses a recorded request body and returns the number of events in its {@code events} array. */
	private int eventCountInBody(final TestableNetworkRequest request) {
		try {
			final String body = new String(request.getBody());
			final JSONObject json = new JSONObject(body);
			return json.getJSONArray("events").length();
		} catch (final Exception e) {
			throw new AssertionError("Failed to parse request body as JSON with an 'events' array", e);
		}
	}
}
