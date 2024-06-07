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
import static com.adobe.marketing.mobile.util.JSONAsserts.assertExactMatch;
import static com.adobe.marketing.mobile.util.NodeConfig.Scope.Subtree;
import static com.adobe.marketing.mobile.util.TestHelper.assertExpectedEvents;
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.edge.consent.Consent;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.ADBCountDownLatch;
import com.adobe.marketing.mobile.util.CollectionEqualCount;
import com.adobe.marketing.mobile.util.MockNetworkService;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
import com.adobe.marketing.mobile.util.ValueTypeMatch;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ConsentStatusChangeFunctionalTests {

	private static final MockNetworkService mockNetworkService = new MockNetworkService();
	private static final String EXEDGE_INTERACT_URL_STRING = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String EXEDGE_CONSENT_URL_STRING = TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING;
	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";
	private static final int EVENTS_COUNT = 5;

	@Rule
	public RuleChain rule = RuleChain.outerRule(new TestHelper.LogOnErrorRule()).around(new TestHelper.SetupCoreRule());

	@Before
	public void setup() throws Exception {
		ServiceProvider.getInstance().setNetworkService(mockNetworkService);

		setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);

		// hub shared state update for 4 extensions Edge, Config, Identity, Hub)
		setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, 4);

		HashMap<String, Object> config = new HashMap<String, Object>() {
			{
				put("edge.configId", CONFIG_ID);
			}
		};
		MobileCore.updateConfiguration(config);

		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, Identity.EXTENSION, Consent.EXTENSION, MonitorExtension.EXTENSION),
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

	// Test sendNetworkRequest(final EdgeHit edgeHit, final Map<String, String> requestHeaders, final int attemptCount)
	@Test
	public void testCollectConsent_whenNo_thenHits_hitsCleared() throws Exception {
		// setup
		updateCollectConsent(ConsentStatus.NO);
		getConsentsSync();
		resetTestExpectations();

		// test
		fireManyEvents();

		// verify
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testCollectConsent_whenYes_thenHits_hitsSent() throws Exception {
		// setup
		updateCollectConsent(ConsentStatus.YES);
		getConsentsSync();
		resetTestExpectations();

		// test
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, EVENTS_COUNT);
		fireManyEvents();

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
	}

	@Test
	public void testCollectConsent_whenPending_thenHits_thenYes_hitsSent() throws Exception {
		// initial pending
		updateCollectConsent(ConsentStatus.PENDING);
		getConsentsSync();
		fireManyEvents();

		//verify
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, resultRequests.size());

		// test - change to yes
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, EVENTS_COUNT);
		updateCollectConsent(ConsentStatus.YES);
		getConsentsSync();

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
	}

	@Test
	public void testCollectConsent_whenPending_thenHits_thenNo_hitsCleared() throws Exception {
		// initial pending
		updateCollectConsent(ConsentStatus.PENDING);
		getConsentsSync();
		fireManyEvents();

		// test - change to no
		updateCollectConsent(ConsentStatus.NO);
		getConsentsSync();

		// verify
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testCollectConsent_whenYes_thenPending_thenHits_thenNo_hitsCleared() throws Exception {
		// initial yes, pending
		updateCollectConsent(ConsentStatus.YES);
		updateCollectConsent(ConsentStatus.PENDING);
		getConsentsSync();
		fireManyEvents();

		// test - change to no
		updateCollectConsent(ConsentStatus.NO);
		getConsentsSync();

		// verify
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testCollectConsent_whenNo_thenPending_thenHits_thenNo_hitsCleared() throws Exception {
		// initial no, pending
		updateCollectConsent(ConsentStatus.NO);
		updateCollectConsent(ConsentStatus.PENDING);
		getConsentsSync();
		fireManyEvents();

		// test - change to no
		updateCollectConsent(ConsentStatus.NO);

		// verify
		List<TestableNetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, resultRequests.size());
	}

	@Test
	public void testCollectConsent_whenNo_thenPending_thenHits_thenYes_hitsSent() throws Exception {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, EVENTS_COUNT);

		// initial no, pending
		updateCollectConsent(ConsentStatus.NO);
		updateCollectConsent(ConsentStatus.PENDING);
		getConsentsSync();
		fireManyEvents();

		// test - change to yes
		updateCollectConsent(ConsentStatus.YES);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
	}

	// Test consent events are being sent to Edge Network
	@Test
	public void testCollectConsentNo_sendsRequestToEdgeNetwork() throws Exception {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_CONSENT_URL_STRING, POST, 1);

		// test
		updateCollectConsent(ConsentStatus.NO);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> interactRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, interactRequests.size());
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_CONSENT_URL_STRING,
			POST,
			1000
		);
		assertEquals(1, consentRequests.size());
		assertEquals(POST, consentRequests.get(0).getMethod());

		String expected =
			"{" +
			"  \"query\": {" +
			"    \"consent\": {" +
			"      \"operation\": \"update\"" +
			"    }" +
			"  }," +
			"  \"identityMap\": {" +
			"    \"ECID\": [" +
			"      {" +
			"        \"id\": \"STRING_TYPE\"," +
			"        \"authenticatedState\": \"ambiguous\"," +
			"        \"primary\": false" +
			"      }" +
			"    ]" +
			"  }," +
			"  \"consent\": [" +
			"    {" +
			"      \"standard\": \"Adobe\"," +
			"      \"version\": \"2.0\"," +
			"      \"value\": {" +
			"        \"collect\": {" +
			"          \"val\": \"n\"" +
			"        }," +
			"        \"metadata\": {" +
			"          \"time\": \"STRING_TYPE\"" +
			"        }" +
			"      }" +
			"    }" +
			"  ]," +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": true," +
			"        \"lineFeed\": \"\\n\"," +
			"        \"recordSeparator\": \"\\u0000\"" +
			"      }" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(
			expected,
			consentRequests.get(0).getBodyJson(),
			new ValueTypeMatch("identityMap.ECID[0].id", "consent[0].value.metadata.time"),
			new CollectionEqualCount(Subtree)
		);
	}

	@Test
	public void testCollectConsentYes_sendsRequestToEdgeNetwork() throws Exception {
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_CONSENT_URL_STRING, POST, 1);

		// test
		updateCollectConsent(ConsentStatus.YES);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> interactRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, interactRequests.size());
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_CONSENT_URL_STRING,
			POST,
			1000
		);
		assertEquals(1, consentRequests.size());
		assertEquals(POST, consentRequests.get(0).getMethod());

		String expected =
			"{" +
			"  \"query\": {" +
			"    \"consent\": {" +
			"      \"operation\": \"update\"" +
			"    }" +
			"  }," +
			"  \"identityMap\": {" +
			"    \"ECID\": [" +
			"      {" +
			"        \"id\": \"STRING_TYPE\"," +
			"        \"authenticatedState\": \"ambiguous\"," +
			"        \"primary\": false" +
			"      }" +
			"    ]" +
			"  }," +
			"  \"consent\": [" +
			"    {" +
			"      \"standard\": \"Adobe\"," +
			"      \"version\": \"2.0\"," +
			"      \"value\": {" +
			"        \"collect\": {" +
			"          \"val\": \"y\"" +
			"        }," +
			"        \"metadata\": {" +
			"          \"time\": \"STRING_TYPE\"" +
			"        }" +
			"      }" +
			"    }" +
			"  ]," +
			"  \"meta\": {" +
			"    \"konductorConfig\": {" +
			"      \"streaming\": {" +
			"        \"enabled\": true," +
			"        \"lineFeed\": \"\\n\"," +
			"        \"recordSeparator\": \"\\u0000\"" +
			"      }" +
			"    }" +
			"  }" +
			"}";
		assertExactMatch(
			expected,
			consentRequests.get(0).getBodyJson(),
			new ValueTypeMatch("identityMap.ECID[0].id", "consent[0].value.metadata.time"),
			new CollectionEqualCount(Subtree)
		);
	}

	@Test
	public void testCollectConsentOtherThanYesNo_doesNotSendRequestToEdgeNetwork() throws Exception {
		// test
		updateCollectConsent(ConsentStatus.PENDING);
		updateCollectConsent("u");
		updateCollectConsent("some value");

		// verify
		List<TestableNetworkRequest> interactRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, interactRequests.size());
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_CONSENT_URL_STRING,
			POST,
			1000
		);
		assertEquals(0, consentRequests.size());
	}

	@Test
	public void testCollectConsent_withConfigurableEndpoint_withEmptyConfigEndpoint_UsesProduction() throws Exception {
		mockNetworkService.setExpectationForNetworkRequest(TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING, POST, 1);

		// test
		updateCollectConsent(ConsentStatus.YES);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING,
			POST,
			1000
		);
		assertEquals(1, consentRequests.size());
		assertEquals(POST, consentRequests.get(0).getMethod());
		assertTrue(consentRequests.get(0).getUrl().startsWith(TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING));
	}

	@Test
	public void testCollectConsent_withConfigurableEndpoint_withInvalidConfigEndpoint_UsesProduction()
		throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "invalid");
				}
			}
		);

		mockNetworkService.setExpectationForNetworkRequest(TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING, POST, 1);

		// test
		updateCollectConsent(ConsentStatus.YES);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING,
			POST,
			1000
		);
		assertEquals(1, consentRequests.size());
		assertEquals(POST, consentRequests.get(0).getMethod());
		assertTrue(consentRequests.get(0).getUrl().startsWith(TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING));
	}

	@Test
	public void testCollectConsent_withConfigurableEndpoint_withProductionConfigEndpoint_UsesProduction()
		throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "prod");
				}
			}
		);

		mockNetworkService.setExpectationForNetworkRequest(TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING, POST, 1);

		// test
		updateCollectConsent(ConsentStatus.YES);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING,
			POST,
			1000
		);
		assertEquals(1, consentRequests.size());
		assertEquals(POST, consentRequests.get(0).getMethod());
		assertTrue(consentRequests.get(0).getUrl().startsWith(TestConstants.Defaults.EXEDGE_CONSENT_URL_STRING));
	}

	@Test
	public void testCollectConsent_withConfigurableEndpoint_withPreProductionConfigEndpoint_UsesPreProduction()
		throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "pre-prod");
				}
			}
		);

		mockNetworkService.setExpectationForNetworkRequest(
			TestConstants.Defaults.EXEDGE_CONSENT_PRE_PROD_URL_STRING,
			POST,
			1
		);

		// test
		updateCollectConsent(ConsentStatus.YES);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_CONSENT_PRE_PROD_URL_STRING,
			POST,
			1000
		);
		assertEquals(1, consentRequests.size());
		assertEquals(POST, consentRequests.get(0).getMethod());
		assertTrue(
			consentRequests.get(0).getUrl().startsWith(TestConstants.Defaults.EXEDGE_CONSENT_PRE_PROD_URL_STRING)
		);
	}

	@Test
	public void testCollectConsent_withConfigurableEndpoint_withIntegrationConfigEndpoint_usesIntegration()
		throws Exception {
		updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", "int");
				}
			}
		);

		mockNetworkService.setExpectationForNetworkRequest(
			TestConstants.Defaults.EXEDGE_CONSENT_INT_URL_STRING,
			POST,
			1
		);

		// test
		updateCollectConsent(ConsentStatus.YES);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<TestableNetworkRequest> consentRequests = mockNetworkService.getNetworkRequestsWith(
			TestConstants.Defaults.EXEDGE_CONSENT_INT_URL_STRING,
			POST,
			1000
		);
		assertEquals(1, consentRequests.size());
		assertEquals(POST, consentRequests.get(0).getMethod());
		assertTrue(consentRequests.get(0).getUrl().startsWith(TestConstants.Defaults.EXEDGE_CONSENT_INT_URL_STRING));
	}

	/**
	 * Resets all test helper expectations and recorded data
	 */
	private void resetTestExpectations() {
		mockNetworkService.reset();
		TestHelper.resetTestExpectations();
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
		Consent.update(
			new HashMap<String, Object>() {
				{
					put(
						"consents",
						new HashMap<String, Object>() {
							{
								put(
									"collect",
									new HashMap<String, Object>() {
										{
											put("val", status);
										}
									}
								);
							}
						}
					);
				}
			}
		);
	}

	private void getConsentsSync() throws Exception {
		final ADBCountDownLatch latch = new ADBCountDownLatch(1);
		Consent.getConsents(stringObjectMap -> latch.countDown());

		latch.await(1000, TimeUnit.MILLISECONDS);
	}

	private void updateConfiguration(final Map<String, Object> config) throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.updateConfiguration(config);
		MobileCore.getPrivacyStatus(mobilePrivacyStatus -> latch.countDown());

		assertTrue(latch.await(2, TimeUnit.SECONDS));
	}
}
