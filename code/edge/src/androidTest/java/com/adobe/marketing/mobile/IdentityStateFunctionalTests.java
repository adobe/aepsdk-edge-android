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
import static com.adobe.marketing.mobile.util.TestHelper.LogOnErrorRule;
import static com.adobe.marketing.mobile.util.TestHelper.SetupCoreRule;
import static com.adobe.marketing.mobile.util.TestHelper.assertExpectedEvents;
import static com.adobe.marketing.mobile.util.TestHelper.resetTestExpectations;
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.FakeIdentity;
import com.adobe.marketing.mobile.util.JSONUtils;
import com.adobe.marketing.mobile.util.MockNetworkService;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IdentityStateFunctionalTests {

	private static final MockNetworkService mockNetworkService = new MockNetworkService();
	private static final String EXEDGE_INTERACT_URL_STRING = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";

	@Rule
	public RuleChain rule = RuleChain.outerRule(new LogOnErrorRule()).around(new SetupCoreRule());

	@Before
	public void setup() throws Exception {
		ServiceProvider.getInstance().setNetworkService(mockNetworkService);

		setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);
		setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, 3);

		HashMap<String, Object> config = new HashMap<String, Object>() {
			{
				put("edge.configId", CONFIG_ID);
			}
		};
		MobileCore.updateConfiguration(config);

		final CountDownLatch latch = new CountDownLatch(1);
		MobileCore.registerExtensions(
			Arrays.asList(Edge.EXTENSION, FakeIdentity.EXTENSION, MonitorExtension.EXTENSION),
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

	@Test
	public void testSendEvent_withPendingIdentityState_noRequestSent() throws InterruptedException {
		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> requests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		assertTrue(requests.isEmpty());
	}

	@Test
	public void testSendEvent_withPendingIdentityState_thenValidIdentityState_requestSentAfterChange()
		throws Exception {
		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		List<TestableNetworkRequest> requests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST,
			2000
		);
		assertTrue(requests.isEmpty()); // no network requests sent yet

		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			"\u0000{\"test\": \"json\"}",
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		final String jsonStr =
			"{\n" +
			"      \"identityMap\": {\n" +
			"        \"ECID\": [\n" +
			"          {\n" +
			"            \"id\":" +
			"1234" +
			",\n" +
			"            \"authenticatedState\": \"ambiguous\",\n" +
			"            \"primary\": false\n" +
			"          }\n" +
			"        ]\n" +
			"      }\n" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityMap = JSONUtils.toMap(jsonObject);
		// Once the shared state is set, the Edge Extension is expected to reprocess the original
		// Send Event request once the Hub Shared State event is received.
		FakeIdentity.setXDMSharedState(identityMap, FakeIdentity.EVENT_TYPE);

		mockNetworkService.assertAllNetworkRequestExpectations();

		requests = mockNetworkService.getNetworkRequestsWith(EXEDGE_INTERACT_URL_STRING, POST);
		assertEquals(1, requests.size());
		Map<String, String> flattenedRequestBody = mockNetworkService.getFlattenedNetworkRequestBody(requests.get(0));
		assertEquals("1234", flattenedRequestBody.get("xdm.identityMap.ECID[0].id"));
	}

	@Test
	public void testSendEvent_withNoECIDInIdentityState_requestSentWithoutECID() throws Exception {
		final String jsonStr =
			"{\n" +
			"      \"identityMap\": {\n" +
			"        \"USERID\": [\n" +
			"          {\n" +
			"            \"id\":" +
			"someUserID" +
			",\n" +
			"            \"authenticatedState\": \"authenticated\",\n" +
			"            \"primary\": false\n" +
			"          }\n" +
			"        ]\n" +
			"      }\n" +
			"}";

		final JSONObject jsonObject = new JSONObject(jsonStr);
		final Map<String, Object> identityState = JSONUtils.toMap(jsonObject);
		FakeIdentity.setXDMSharedState(identityState, FakeIdentity.EVENT_TYPE); // set state without ECID

		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			"\u0000{\"test\": \"json\"}",
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("testString", "xdm");
					}
				}
			)
			.build();
		Edge.sendEvent(experienceEvent, null);

		mockNetworkService.assertAllNetworkRequestExpectations();

		// Assert network request does not contain an ECID
		List<TestableNetworkRequest> requests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		assertEquals(1, requests.size());
		Map<String, String> flattenedRequestBody = mockNetworkService.getFlattenedNetworkRequestBody(requests.get(0));
		assertNull(flattenedRequestBody.get("xdm.identityMap.ECID[0].id"));
	}
}
