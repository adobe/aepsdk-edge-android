/*
  Copyright 2023 Adobe. All rights reserved.
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
import static com.adobe.marketing.mobile.util.TestHelper.resetTestExpectations;
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.TestableNetworkRequest;
import com.adobe.marketing.mobile.util.CollectionEqualCount;
import com.adobe.marketing.mobile.util.MockNetworkService;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
import com.adobe.marketing.mobile.util.ValueTypeMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EdgePathOverwriteTests {

	private static final MockNetworkService mockNetworkService = new MockNetworkService();
	private static final String EXEDGE_MEDIA_URL_STRING = TestConstants.Defaults.EXEDGE_MEDIA_PROD_URL_STRING;
	private static final String EXEDGE_MEDIA_OR2_LOC_URL_STRING =
		TestConstants.Defaults.EXEDGE_MEDIA_OR2_LOC_URL_STRING;
	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";
	private static final String DEFAULT_RESPONSE_STRING = "\u0000{\"test\": \"json\"}";
	private static final int TIMEOUT_MILLIS = 5000;

	@Rule
	public RuleChain rule = RuleChain.outerRule(new TestHelper.LogOnErrorRule()).around(new TestHelper.SetupCoreRule());

	@Before
	public void setup() throws Exception {
		ServiceProvider.getInstance().setNetworkService(mockNetworkService);

		setExpectationEvent(EventType.CONFIGURATION, EventSource.REQUEST_CONTENT, 1);
		setExpectationEvent(EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT, 1);
		setExpectationEvent(EventType.HUB, EventSource.SHARED_STATE, 4);

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

	@Test
	public void testSendEvent_withXDMData_withOverwritePath_overwritesRequestPathAndSendsExEdgeNetworkRequest()
		throws Exception {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_MEDIA_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_MEDIA_URL_STRING, POST, 1);

		Event experienceEvent = new Event.Builder("test-experience-event", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(
							"xdm",
							new HashMap<String, Object>() {
								{
									put("testString", "xdmValue");
									put("testInt", 10);
									put("testBool", false);
									put("testDouble", 12.89);
									put(
										"testArray",
										new ArrayList<String>() {
											{
												add("elem1");
												add("elem2");
											}
										}
									);
									put(
										"testMap",
										new HashMap<String, String>() {
											{
												put("key", "value");
											}
										}
									);
								}
							}
						);

						put(
							"request",
							new HashMap<String, Object>() {
								{
									put("path", "/va/v1/sessionstart");
								}
							}
						);
					}
				}
			)
			.build();
		MobileCore.dispatchEvent(experienceEvent);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<NetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_MEDIA_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"xdm\": {" +
			"        \"_id\": \"STRING_TYPE\"," +
			"        \"testArray\": [\"elem1\", \"elem2\"]," +
			"        \"testBool\": false," +
			"        \"testDouble\": 12.89," +
			"        \"testInt\": 10," +
			"        \"testMap\": {" +
			"          \"key\": \"value\"" +
			"        }," +
			"        \"testString\": \"xdmValue\"," +
			"        \"timestamp\": \"STRING_TYPE\"" +
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
			"  }," +
			"  \"xdm\": {" +
			"    \"identityMap\": {" +
			"      \"ECID\": [" +
			"        {" +
			"          \"authenticatedState\": \"ambiguous\"," +
			"          \"id\": \"STRING_TYPE\"," +
			"          \"primary\": false" +
			"        }" +
			"      ]" +
			"    }," +
			"    \"implementationDetails\": {" +
			"      \"environment\": \"app\"," +
			"      \"name\": \"" +
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE +
			"\"," +
			"      \"version\": \"" +
			MobileCore.extensionVersion() +
			"+" +
			Edge.extensionVersion() +
			"\"" +
			"    }" +
			"  }" +
			"}";

		assertExactMatch(
			expected,
			getPayloadJson(resultRequests.get(0)),
			new CollectionEqualCount(Subtree),
			new ValueTypeMatch("xdm.identityMap.ECID[0].id", "events[0].xdm._id", "events[0].xdm.timestamp")
		);

		TestableNetworkRequest testableNetworkRequest = new TestableNetworkRequest(resultRequests.get(0));
		assertTrue(testableNetworkRequest.getUrl().startsWith(EXEDGE_MEDIA_URL_STRING));
		assertEquals(CONFIG_ID, testableNetworkRequest.queryParam("configId"));
		assertNotNull(testableNetworkRequest.queryParam("requestId"));
	}

	@Test
	public void testSendEvent_withXDMData_withOverwritePath_withLocationHintSet_overwritesRequestPathAndSendsExEdgeNetworkRequestWithSetLocationHint()
		throws Exception {
		Edge.setLocationHint("or2");
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(DEFAULT_RESPONSE_STRING, 200);
		mockNetworkService.setMockResponseFor(EXEDGE_MEDIA_OR2_LOC_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_MEDIA_OR2_LOC_URL_STRING, POST, 1);

		Event experienceEvent = new Event.Builder("test-experience-event", EventType.EDGE, EventSource.REQUEST_CONTENT)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(
							"xdm",
							new HashMap<String, Object>() {
								{
									put("testString", "xdmValue");
									put("testInt", 10);
									put("testBool", false);
									put("testDouble", 12.89);
									put(
										"testArray",
										new ArrayList<String>() {
											{
												add("elem1");
												add("elem2");
											}
										}
									);
									put(
										"testMap",
										new HashMap<String, String>() {
											{
												put("key", "value");
											}
										}
									);
								}
							}
						);

						put(
							"request",
							new HashMap<String, Object>() {
								{
									put("path", "/va/v1/sessionstart");
								}
							}
						);
					}
				}
			)
			.build();
		MobileCore.dispatchEvent(experienceEvent);

		// verify
		mockNetworkService.assertAllNetworkRequestExpectations();
		List<NetworkRequest> resultRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_MEDIA_OR2_LOC_URL_STRING,
			POST,
			TIMEOUT_MILLIS
		);
		assertEquals(1, resultRequests.size());

		String expected =
			"{" +
			"  \"events\": [" +
			"    {" +
			"      \"xdm\": {" +
			"        \"_id\": \"STRING_TYPE\"," +
			"        \"testArray\": [\"elem1\", \"elem2\"]," +
			"        \"testBool\": false," +
			"        \"testDouble\": 12.89," +
			"        \"testInt\": 10," +
			"        \"testMap\": {" +
			"          \"key\": \"value\"" +
			"        }," +
			"        \"testString\": \"xdmValue\"," +
			"        \"timestamp\": \"STRING_TYPE\"" +
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
			"  }," +
			"  \"xdm\": {" +
			"    \"identityMap\": {" +
			"      \"ECID\": [" +
			"        {" +
			"          \"authenticatedState\": \"ambiguous\"," +
			"          \"id\": \"STRING_TYPE\"," +
			"          \"primary\": false" +
			"        }" +
			"      ]" +
			"    }," +
			"    \"implementationDetails\": {" +
			"      \"environment\": \"app\"," +
			"      \"name\": \"" +
			EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE +
			"\"," +
			"      \"version\": \"" +
			MobileCore.extensionVersion() +
			"+" +
			Edge.extensionVersion() +
			"\"" +
			"    }" +
			"  }" +
			"}";

		assertExactMatch(
			expected,
			getPayloadJson(resultRequests.get(0)),
			new CollectionEqualCount(Subtree),
			new ValueTypeMatch("xdm.identityMap.ECID[0].id", "events[0].xdm._id", "events[0].xdm.timestamp")
		);

		TestableNetworkRequest testableNetworkRequest = new TestableNetworkRequest(resultRequests.get(0));
		assertTrue(testableNetworkRequest.getUrl().startsWith(EXEDGE_MEDIA_OR2_LOC_URL_STRING));
		assertEquals(CONFIG_ID, testableNetworkRequest.queryParam("configId"));
		assertNotNull(testableNetworkRequest.queryParam("requestId"));
	}

	private JSONObject getPayloadJson(NetworkRequest networkRequest) {
		if (networkRequest == null || networkRequest.getBody() == null) {
			return null;
		}

		String payload = new String(networkRequest.getBody());
		try {
			return new JSONObject(payload);
		} catch (Exception e) {
			fail("Failed to create JSONObject from payload: " + e.getMessage());
			return null;
		}
	}
}
