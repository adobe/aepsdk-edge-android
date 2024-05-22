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
import static com.adobe.marketing.mobile.util.TestHelper.setExpectationEvent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.JSONAsserts;
import com.adobe.marketing.mobile.util.MockNetworkService;
import com.adobe.marketing.mobile.util.MonitorExtension;
import com.adobe.marketing.mobile.util.TestConstants;
import com.adobe.marketing.mobile.util.TestHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CompletionHandlerFunctionalTests {

	private static final MockNetworkService mockNetworkService = new MockNetworkService();
	private static final String EXEDGE_INTERACT_URL_STRING = TestConstants.Defaults.EXEDGE_INTERACT_URL_STRING;
	private static final String CONFIG_ID = "1234abcd-abcd-1234-5678-123456abcdef";

	private static final String RESPONSE_BODY_WITH_HANDLE =
		"\u0000{\"requestId\": \"0ee43289-4a4e-469a-bf5c-1d8186919a26\",\"handle\": [{\"payload\": [{\"id\": \"AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9\",\"scope\": \"buttonColor\",\"items\": [{                           \"schema\": \"https://ns.adobe.com/personalization/json-content-item\",\"data\": {\"content\": {\"value\": \"#D41DBA\"}}}]}],\"type\": \"personalization:decisions\"}]}\n";
	private static final String RESPONSE_BODY_WITH_TWO_ERRORS =
		"\u0000{\"requestId\": \"0ee43289-4a4e-469a-bf5c-1d8186919a27\",\"errors\": [{\"message\": \"An error occurred while calling the 'X' service for this request. Please try again.\", \"code\": \"502\"}, {\"message\": \"An error occurred while calling the 'Y', service unavailable\", \"code\": \"503\"}]}\n";

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

	@Test
	public void testSendEvent_withCompletionHandler_callsCompletionCorrectly() throws InterruptedException {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			RESPONSE_BODY_WITH_HANDLE,
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "personalizationEvent");
						put("test", "xdm");
					}
				}
			)
			.build();
		final CountDownLatch latch = new CountDownLatch(1);
		final List<EdgeEventHandle> receivedHandles = new ArrayList<>();
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				receivedHandles.addAll(handles);
				latch.countDown();
			}
		);

		mockNetworkService.assertAllNetworkRequestExpectations();

		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch.await(1, TimeUnit.SECONDS));

		List<NetworkRequest> resultNetworkRequests = mockNetworkService.getNetworkRequestsWith(
			EXEDGE_INTERACT_URL_STRING,
			POST
		);
		assertEquals(1, resultNetworkRequests.size());
		assertEquals(1, receivedHandles.size());


		EdgeEventHandle edgeEventHandle = receivedHandles.get(0);
		assertEquals("personalization:decisions", edgeEventHandle.getType());
		assertEquals(1, edgeEventHandle.getPayload().size());

		String expected = "{" +
			"  \"id\": \"AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9\"," +
			"  \"items\": [" +
			"    {" +
			"      \"data\": {" +
			"        \"content\": {" +
			"          \"value\": \"#D41DBA\"" +
			"        }" +
			"      }," +
			"      \"schema\": \"https://ns.adobe.com/personalization/json-content-item\"" +
			"    }" +
			"  ]," +
			"  \"scope\": \"buttonColor\"" +
			"}";
		JSONAsserts.assertEquals(expected, edgeEventHandle.getPayload().get(0));
	}

	@Test
	public void testSendEventx2_withCompletionHandler_whenResponseHandle_callsCompletionCorrectly()
		throws InterruptedException {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			RESPONSE_BODY_WITH_HANDLE,
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 2);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "personalizationEvent");
						put("test", "xdm");
					}
				}
			)
			.build();

		final CountDownLatch latch1 = new CountDownLatch(1);
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				assertEquals(1, handles.size());
				latch1.countDown();
			}
		);

		final CountDownLatch latch2 = new CountDownLatch(1);
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				assertEquals(1, handles.size());
				latch2.countDown();
			}
		);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch1.await(1, TimeUnit.SECONDS));
		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch2.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void testSendEventx2_withCompletionHandler_whenServerError_callsCompletion() throws InterruptedException {
		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "personalizationEvent");
						put("test", "xdm");
					}
				}
			)
			.build();

		// set expectations and send first event
		HttpConnecting responseConnection1 = mockNetworkService.createMockNetworkResponse(
			RESPONSE_BODY_WITH_HANDLE,
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection1);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		final CountDownLatch latch1 = new CountDownLatch(1);
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				assertEquals(1, handles.size());
				latch1.countDown();
			}
		);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch1.await(1, TimeUnit.SECONDS));

		resetTestExpectations();

		// set expectations and send second event
		HttpConnecting responseConnection2 = mockNetworkService.createMockNetworkResponse(
			RESPONSE_BODY_WITH_TWO_ERRORS,
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection2);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		final CountDownLatch latch2 = new CountDownLatch(1);
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				// 0 handles, received errors but still called completion
				assertEquals(0, handles.size());
				latch2.countDown();
			}
		);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch2.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void testSendEvent_withCompletionHandler_whenServerErrorAndHandle_callsCompletion()
		throws InterruptedException {
		final String responseBodyWithHandleAndError =
			"\u0000{\"requestId\": \"0ee43289-4a4e-469a-bf5c-1d8186919a26\",\"handle\": [{\"payload\": [{\"id\": \"AT:eyJhY3Rpdml0eUlkIjoiMTE3NTg4IiwiZXhwZXJpZW5jZUlkIjoiMSJ9\",\"scope\": \"buttonColor\",\"items\": [{                           \"schema\": \"https://ns.adobe.com/personalization/json-content-item\",\"data\": {\"content\": {\"value\": \"#D41DBA\"}}}]}],\"type\": \"personalization:decisions\"}],\"errors\": [{\"message\": \"An error occurred while calling the 'X' service for this request. Please try again.\", \"code\": \"502\"}, {\"message\": \"An error occurred while calling the 'Y', service unavailable\", \"code\": \"503\"}]}\n";
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			responseBodyWithHandleAndError,
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 1);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "personalizationEvent");
						put("test", "xdm");
					}
				}
			)
			.build();
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				assertEquals(1, handles.size());
				latch.countDown();
			}
		);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch.await(1, TimeUnit.SECONDS));
	}

	@Test
	public void testSendEvent_withCompletionHandler_whenExceptionThrownFromCallback_callsCompletionCorrectly()
		throws InterruptedException {
		HttpConnecting responseConnection = mockNetworkService.createMockNetworkResponse(
			RESPONSE_BODY_WITH_HANDLE,
			200
		);
		mockNetworkService.setMockResponseFor(EXEDGE_INTERACT_URL_STRING, POST, responseConnection);
		mockNetworkService.setExpectationForNetworkRequest(EXEDGE_INTERACT_URL_STRING, POST, 2);

		ExperienceEvent experienceEvent = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("eventType", "personalizationEvent");
						put("test", "xdm");
					}
				}
			)
			.build();

		final CountDownLatch latch1 = new CountDownLatch(1);
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				assertEquals(1, handles.size());
				latch1.countDown();
				throw new NullPointerException();
			}
		);

		// If the Exception is not caught, subsequent requests will fail to process.
		final CountDownLatch latch2 = new CountDownLatch(1);
		Edge.sendEvent(
			experienceEvent,
			handles -> {
				assertEquals(1, handles.size());
				latch2.countDown();
			}
		);

		mockNetworkService.assertAllNetworkRequestExpectations();
		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch1.await(1, TimeUnit.SECONDS));
		assertTrue("Timeout waiting for EdgeCallback completion handler.", latch2.await(1, TimeUnit.SECONDS));
	}

	/**
	 * Resets all test helper expectations and recorded data
	 */
	private void resetTestExpectations() {
		mockNetworkService.reset();
		TestHelper.resetTestExpectations();
	}
}
