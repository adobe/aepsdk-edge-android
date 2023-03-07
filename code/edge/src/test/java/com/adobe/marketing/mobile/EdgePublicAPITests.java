/*
  Copyright 2022 Adobe. All rights reserved.
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class EdgePublicAPITests {

	private static String GRADLE_PROPERTIES_PATH = "../gradle.properties";
	private static String PROPERTY_MODULE_VERSION = "moduleVersion";

	private MockedStatic<MobileCore> mockCore;

	@Before
	public void setup() {
		mockCore = mockStatic(MobileCore.class);
	}

	@After
	public void tearDown() {
		mockCore.close();
	}

	@SuppressWarnings({ "deprecation", "rawtypes" })
	@Test
	public void testRegisterExtension_registersWithMobileCore() {
		Edge.registerExtension();

		final ArgumentCaptor<Class> extensionClassCaptor = ArgumentCaptor.forClass(Class.class);
		final ArgumentCaptor<ExtensionErrorCallback> callbackCaptor = ArgumentCaptor.forClass(
			ExtensionErrorCallback.class
		);
		mockCore.verify(
			() -> MobileCore.registerExtension(extensionClassCaptor.capture(), callbackCaptor.capture()),
			times(1)
		);

		assertEquals(EdgeExtension.class, extensionClassCaptor.getValue());
		assertNotNull(callbackCaptor.getValue());
	}

	@Test
	public void testExtensionVersion_verifyModuleVersionInPropertiesFile_asEqual() {
		Properties properties = loadProperties(GRADLE_PROPERTIES_PATH);

		assertNotNull(Edge.extensionVersion());
		assertFalse(Edge.extensionVersion().isEmpty());

		String moduleVersion = properties.getProperty(PROPERTY_MODULE_VERSION);
		assertNotNull(moduleVersion);

		assertEquals(moduleVersion, Edge.extensionVersion());
	}

	@Test
	public void testSendEvent_whenNullEvent_ignored() {
		Edge.sendEvent(null, null);
		mockCore.verify(() -> MobileCore.dispatchEvent(any()), never());
	}

	@Test
	public void testSendEvent_whenNullXDMData_ignored() {
		Edge.sendEvent(new ExperienceEvent.Builder().build(), null);
		mockCore.verify(() -> MobileCore.dispatchEvent(any()), never());
	}

	@Test
	public void testSendEvent_whenValidData_sendsEvent() {
		ExperienceEvent event = new ExperienceEvent.Builder()
			.setXdmSchema(
				new HashMap<String, Object>() {
					{
						put("myXdm", "data example");
					}
				}
			)
			.setData(
				new HashMap<String, Object>() {
					{
						put("myData", "raw data");
					}
				}
			)
			.build();
		Edge.sendEvent(event, null);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(requestEventCaptor.capture()), times(1));

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_CONTENT, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("xdm"));
		assertTrue(requestEvent.getEventData().containsKey("data"));
		assertFalse(requestEvent.getEventData().containsKey("datasetId"));
	}

	@Test
	public void testSetLocationHint_whenHintIsValue_dispatchesEdgeUpdateIdentity() throws InterruptedException {
		Edge.setLocationHint("or2");

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(requestEventCaptor.capture()), times(1));

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.UPDATE_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals("or2", requestEvent.getEventData().get("locationHint"));
	}

	@Test
	public void testSetLocationHint_whenHintIsNull_dispatchesEdgeUpdateIdentity() throws InterruptedException {
		Edge.setLocationHint(null);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(requestEventCaptor.capture()), times(1));

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.UPDATE_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertNull(requestEvent.getEventData().get("locationHint"));
	}

	@Test
	public void testSetLocationHint_whenHintIsEmpty_dispatchesEdgeUpdateIdentity() throws InterruptedException {
		Edge.setLocationHint("");

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(() -> MobileCore.dispatchEvent(requestEventCaptor.capture()), times(1));

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.UPDATE_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals("", requestEvent.getEventData().get("locationHint"));
	}

	@Test
	public void testGetLocationHint_whenNullCallback_ignores() {
		Edge.getLocationHint(null);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					any(Event.class),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			never()
		);
	}

	@Test
	public void testGetLocationHint_whenAdobeCallback_AndResponseIsValueHint_returnsValueHint()
		throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.getLocationHint(s -> {
			assertEquals("hint", s);
			latch.countDown();
		});

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					requestEventCaptor.capture(),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			times(1)
		);

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals(true, requestEvent.getEventData().get("locationHint"));

		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EventType.EDGE,
			EventSource.RESPONSE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKeys.LOCATION_HINT, "hint");
					}
				}
			)
			.inResponseToEvent(requestEvent)
			.build();

		MobileCore.dispatchEvent(responseEvent);
		latch.await(2000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testGetLocationHint_whenAdobeCallbackWithError_AndResponseIsValueHint_returnsValueHint()
		throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.getLocationHint(
			new AdobeCallbackWithError<String>() {
				@Override
				public void call(String s) {
					assertEquals("hint", s);
					latch.countDown();
				}

				@Override
				public void fail(AdobeError adobeError) {
					Assert.fail("Unexpected AdobeError: " + adobeError.getErrorName());
					latch.countDown();
				}
			}
		);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					requestEventCaptor.capture(),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			times(1)
		);

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals(true, requestEvent.getEventData().get("locationHint"));

		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EventType.EDGE,
			EventSource.RESPONSE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKeys.LOCATION_HINT, "hint");
					}
				}
			)
			.inResponseToEvent(requestEvent)
			.build();

		MobileCore.dispatchEvent(responseEvent);
		latch.await(2000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testGetLocationHint_whenResponseIsNullHint_returnsNullHint() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.getLocationHint(
			new AdobeCallbackWithError<String>() {
				@Override
				public void call(String s) {
					assertNull(s);
					latch.countDown();
				}

				@Override
				public void fail(AdobeError adobeError) {
					Assert.fail("Unexpected AdobeError: " + adobeError.getErrorName());
					latch.countDown();
				}
			}
		);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					requestEventCaptor.capture(),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			times(1)
		);

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals(true, requestEvent.getEventData().get("locationHint"));

		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EventType.EDGE,
			EventSource.RESPONSE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKeys.LOCATION_HINT, null);
					}
				}
			)
			.inResponseToEvent(requestEvent)
			.build();

		MobileCore.dispatchEvent(responseEvent);
		latch.await(2000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testGetLocationHint_whenNoResponse_returnsTimeoutError() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.getLocationHint(
			new AdobeCallbackWithError<String>() {
				@Override
				public void call(String s) {
					Assert.fail("Unexpected response returned with value: " + s);
					latch.countDown();
				}

				@Override
				public void fail(AdobeError adobeError) {
					assertEquals(AdobeError.CALLBACK_TIMEOUT, adobeError);
					latch.countDown();
				}
			}
		);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					requestEventCaptor.capture(),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			times(1)
		);

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals(true, requestEvent.getEventData().get("locationHint"));

		// todo:
		// 1. check assertTrue(latch.await(5500, TimeUnit.MILLISECONDS));
		// 2. assert on timeout value
		latch.await(5500, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testGetLocationHint_whenResponseWithNoData_returnsUnexpectedError() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.getLocationHint(
			new AdobeCallbackWithError<String>() {
				@Override
				public void call(String s) {
					Assert.fail("Unexpected response returned with value: " + s);
					latch.countDown();
				}

				@Override
				public void fail(AdobeError adobeError) {
					assertEquals(AdobeError.UNEXPECTED_ERROR, adobeError);
					latch.countDown();
				}
			}
		);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					requestEventCaptor.capture(),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			times(1)
		);

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals(true, requestEvent.getEventData().get("locationHint"));

		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EventType.EDGE,
			EventSource.RESPONSE_IDENTITY
		)
			.setEventData(null)
			.inResponseToEvent(requestEvent)
			.build();

		MobileCore.dispatchEvent(responseEvent);
		latch.await(2000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testGetLocationHint_whenResponseHasWrongKey_returnsUnexpectedError() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.getLocationHint(
			new AdobeCallbackWithError<String>() {
				@Override
				public void call(String s) {
					Assert.fail("Unexpected response returned with value: " + s);
					latch.countDown();
				}

				@Override
				public void fail(AdobeError adobeError) {
					assertEquals(AdobeError.UNEXPECTED_ERROR, adobeError);
					latch.countDown();
				}
			}
		);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					requestEventCaptor.capture(),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			times(1)
		);

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals(true, requestEvent.getEventData().get("locationHint"));

		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EventType.EDGE,
			EventSource.RESPONSE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put("Key", null);
					}
				}
			)
			.inResponseToEvent(requestEvent)
			.build();

		MobileCore.dispatchEvent(responseEvent);
		latch.await(2000, TimeUnit.MILLISECONDS);
	}

	@Test
	public void testGetLocationHint_whenResponseHasWrongType_returnsUnexpectedError() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		Edge.getLocationHint(
			new AdobeCallbackWithError<String>() {
				@Override
				public void call(String s) {
					Assert.fail("Unexpected response returned with value: " + s);
					latch.countDown();
				}

				@Override
				public void fail(AdobeError adobeError) {
					assertEquals(AdobeError.UNEXPECTED_ERROR, adobeError);
					latch.countDown();
				}
			}
		);

		final ArgumentCaptor<Event> requestEventCaptor = ArgumentCaptor.forClass(Event.class);
		mockCore.verify(
			() ->
				MobileCore.dispatchEventWithResponseCallback(
					requestEventCaptor.capture(),
					any(Long.class),
					any(AdobeCallbackWithError.class)
				),
			times(1)
		);

		final Event requestEvent = requestEventCaptor.getValue();
		assertEquals(EventType.EDGE, requestEvent.getType());
		assertEquals(EventSource.REQUEST_IDENTITY, requestEvent.getSource());
		assertTrue(requestEvent.getEventData().containsKey("locationHint"));
		assertEquals(true, requestEvent.getEventData().get("locationHint"));

		Event responseEvent = new Event.Builder(
			EdgeConstants.EventName.RESPONSE_LOCATION_HINT,
			EventType.EDGE,
			EventSource.RESPONSE_IDENTITY
		)
			.setEventData(
				new HashMap<String, Object>() {
					{
						put(EdgeConstants.EventDataKeys.LOCATION_HINT, 5);
					}
				}
			)
			.inResponseToEvent(requestEvent)
			.build();

		MobileCore.dispatchEvent(responseEvent);
		latch.await(2000, TimeUnit.MILLISECONDS);
	}

	private Properties loadProperties(final String filepath) {
		Properties properties = new Properties();
		InputStream input = null;

		try {
			input = new FileInputStream(filepath);

			properties.load(input);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return properties;
	}
}
