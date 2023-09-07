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
import static com.adobe.marketing.mobile.util.TestHelper.assertExpectedEvents;
import static com.adobe.marketing.mobile.util.TestHelper.resetTestExpectations;
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.adobe.marketing.mobile.edge.consent.Consent;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.ADBCountDownLatch;
import com.adobe.marketing.mobile.util.MockNetworkService;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class RestartFunctionalTests {

	private static final MockNetworkService mockNetworkService = new MockNetworkService();
	private static final String EXEDGE_INTERACT_URL_STRING = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";
	private static final int EVENTS_COUNT = 5;
	private static final int TIMEOUT_MILLIS = 5000;

	@Rule
	public RuleChain rule = RuleChain
		.outerRule(new TestHelper.LogOnErrorRule())
		.around(new TestHelper.SetupCoreRule())
		.around(new TestHelper.RegisterMonitorExtensionRule());

	@Before
	public void setup() throws Exception {
		ServiceProvider.getInstance().setNetworkService(mockNetworkService);
		setupCore(false);
		resetTestExpectations(mockNetworkService);
	}

	@Test
	public void testAddEventsToPendingQueue_restartSDK_verifyEventsDispatched_whenConsentYes() throws Exception {
		// initial pending
		updateCollectConsent(ConsentStatus.PENDING);
		getConsentsSync();
		fireManyEvents();

		//verify
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			2000
		);
		assertEquals(0, resultRequests.size());

		// reset
		resetCore();
		resetTestExpectations(mockNetworkService);
		setupCore(true);

		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, EVENTS_COUNT);

		// test - change to yes
		updateCollectConsent(ConsentStatus.YES);
		getConsentsSync();

		// verify
		resultRequests = mockNetworkService.getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, TIMEOUT_MILLIS);
		assertEquals(EVENTS_COUNT, resultRequests.size());
	}

	@Test
	public void testAddEventsToPendingQueue_restartSDK_queueMoreEvents_verifyEventsDispatched_whenConsentYes()
		throws Exception {
		// initial pending
		updateCollectConsent(ConsentStatus.PENDING);
		getConsentsSync();
		fireManyEvents();

		//verify
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			2000
		);
		assertEquals(0, resultRequests.size());

		// reset
		resetCore();
		resetTestExpectations(mockNetworkService);
		setupCore(true);

		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, EVENTS_COUNT * 2);

		// more pending events
		fireManyEvents();

		// test - change to yes
		updateCollectConsent(ConsentStatus.YES);
		getConsentsSync();

		// verify
		resultRequests = mockNetworkService.getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST, 10000);
		assertEquals(EVENTS_COUNT * 2, resultRequests.size());
	}

	@Test
	public void testRetryResponseQueuesEvents_restartSDK_verifyEventsDispatched_whenNetworkResponseSuccess()
		throws Exception {
		// initial pending
		updateCollectConsent(ConsentStatus.YES);
		getConsentsSync();

		// Setup network response error to trigger retry of queued events
		String edgeResponse =
			"\u0000{\"requestId\": \"test-req-id\",\"handle\": [],\"errors\": [],\"warnings\": [{\"type\": \"https://ns.adobe.com/aep/errors/EXEG-0204-502\",\"status\": 503,\"title\": \"A warning occurred.\",\"report\": {\"cause\": {\"message\": \"Unavailable\",\"code\": 503}}}]}";

		// bad connection, hits will be retried
		HttpConnecting responseConnection = mockNetworkService.createNetworkResponse(
			null,
			edgeResponse,
			503,
			null,
			null
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);

		// Expect the one initial request
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		fireManyEvents();

		// verify the one initial request sent
		mockNetworkService.assertAllNetworkRequestExpectations();

		// reset
		resetCore();
		resetTestExpectations(mockNetworkService); // Clears "bad connection" network response

		// Expect all queued hits to be sent
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, EVENTS_COUNT);
		setupCore(true);

		// verify all events sent
		mockNetworkService.assertAllNetworkRequestExpectations();
	}

	public void resetCore() throws Exception {
		TestHelper.resetCoreHelper();
		ServiceProvider.getInstance().setNetworkService(mockNetworkService);
	}

	/**
	 * Setup Mobile Core for testing. Registers Edge, Edge Identity, Consent, and Monitor extensions.
	 *
	 * @param restart true if setup for restart case of Core, false if clean start of Core
	 * @throws InterruptedException in case the latch wait fails
	 */
	public void setupCore(final boolean restart) throws InterruptedException {
		if (restart) {
			// On restart, expect Consent to load preferences from persistence and dispatch them to Hub
			setExpectationEvent(EventType.CONSENT, EventSource.RESPONSE_CONTENT, 1);
		} else {
			setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1);
		}

		setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);

		// hub shared state update for extensions Edge, Config, Consent, Identity, Hub)
		// Expect 4 shared state events on clean start. On restart, Consent also sets a shared state
		setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, restart ? 5 : 4);

		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, Identity.EXTENSION, Consent.EXTENSION, MonitorExtension.EXTENSION),
			o -> {
				if (!restart) {
					// Set configuration on clean start. On restart Configuration will load from persistence.
					HashMap<String, Object> config = new HashMap<String, Object>() {
						{
							put("edge.configId", CONFIG_ID);
						}
					};
					MobileCore.updateConfiguration(config);
				}
				latch.countDown();
			}
		);
		assertTrue(latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

		assertExpectedEvents(false);
		// Note: Should not call resetTestExpectations so this helper can be used for testing scenarios
		// where events are queued prior to the restart and unblocked soon after restart
	}

	private void fireManyEvents() {
		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("test", "xdm");
					}
				}
			)
			.build();

		for (int i = 0; i < EVENTS_COUNT; i++) {
			Edge.sendEvent(experienceEvent, null);
		}
	}

	private void updateCollectConsent(final ConsentStatus status) {
		if (status == null) {
			return;
		}

		updateCollectConsent(status.getValue());
	}

	private void updateCollectConsent(final String status) {
		final Map<String, Object> consentStatus = new HashMap<String, Object>() {
			{
				put("val", status);
			}
		};

		final Map<String, Object> consentCollect = new HashMap<String, Object>() {
			{
				put("collect", consentStatus);
			}
		};

		final Map<String, Object> consents = new HashMap<String, Object>() {
			{
				put("consents", consentCollect);
			}
		};

		Consent.update(consents);
	}

	private void getConsentsSync() throws Exception {
		final ADBCountDownLatch latch = new ADBCountDownLatch(1);
		Consent.getConsents(stringObjectMap -> latch.countDown());

		latch.await(2000, TimeUnit.MILLISECONDS);
	}
}
