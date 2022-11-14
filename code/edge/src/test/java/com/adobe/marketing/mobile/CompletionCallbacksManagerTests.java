/*
  Copyright 2020 Adobe. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CompletionCallbacksManagerTests {

	private CountDownLatch latchOfOne;
	private CountDownLatch anotherLatchOfOne;

	private final Map<String, Object> expectedPayload = new HashMap<String, Object>() {
		{
			put("key1", "value1");
			put("key2", 2);
			put("key3", true);
		}
	};

	private final EdgeEventHandle eventHandle = new EdgeEventHandle(
		new JSONObject() {
			{
				try {
					put("type", "testType");
					put(
						"payload",
						new JSONArray() {
							{
								put(new JSONObject(expectedPayload));
							}
						}
					);
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
	);

	private final String uniqueEventId = "123";
	private final String uniqueEventId2 = "888";
	private final String uniqueEventId3 = "999";

	@Before
	public void setup() {
		latchOfOne = new CountDownLatch(1);
		anotherLatchOfOne = new CountDownLatch(1);
	}

	@After
	public void tearDown() {
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId2);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId3);
	}

	// ----------- register completion handler, expect event handles

	@Test
	public void testRegisterCallback_thenEventHandleReceived_completionCalled() throws InterruptedException {
		final List<EdgeEventHandle> receivedData = new ArrayList<>();
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						receivedData.addAll(handles);
						latchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);

		assertTrue(latchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertEquals(1, receivedData.size());
		assertEquals("testType", receivedData.get(0).getType());
		assertEquals(expectedPayload, receivedData.get(0).getPayload().get(0));
	}

	@Test
	public void testRegisterTwoCallbacks_thenEventHandleReceived_completionCalledForCorrectOne()
		throws InterruptedException {
		final List<EdgeEventHandle> receivedData = new ArrayList<>();

		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId2,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						latchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						receivedData.addAll(handles);
						anotherLatchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);

		assertFalse(latchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertTrue(anotherLatchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertEquals(1, receivedData.size());
	}

	@Test
	public void testRegisterCallbacks_thenEventHandleMultipleTimes_completionCalled() throws InterruptedException {
		final List<EdgeEventHandle> receivedData1 = new ArrayList<>();
		final List<EdgeEventHandle> receivedData2 = new ArrayList<>();

		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						receivedData1.addAll(handles);
						latchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId2,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						receivedData2.addAll(handles);
						anotherLatchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId2, eventHandle);
		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId2, eventHandle);
		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId2);

		assertTrue(latchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertTrue(anotherLatchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertEquals(3, receivedData1.size());
		assertEquals(2, receivedData2.size());
	}

	// ----------- unregister completion handlers
	@Test
	public void testRegisterCallback_thenUnregister_thenNewEventHandleReceived() throws InterruptedException {
		final List<EdgeEventHandle> receivedData = new ArrayList<>();

		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						receivedData.addAll(handles);
						latchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);
		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);

		assertTrue(latchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertEquals(1, receivedData.size());
	}

	@Test
	public void testRegisterCallback_thenUnregister_completionCalled() throws InterruptedException {
		final List<EdgeEventHandle> receivedData = new ArrayList<>();

		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						receivedData.addAll(handles);
						latchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);

		assertTrue(latchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertEquals(0, receivedData.size());
	}

	@Test
	public void testRegisterCallback_thenUnregisterMultipleTimes_completionCalledOnce() throws InterruptedException {
		final List<EdgeEventHandle> receivedData = new ArrayList<>();

		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						receivedData.addAll(handles);
						latchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId);

		assertTrue(latchOfOne.await(100, TimeUnit.MILLISECONDS));
		assertEquals(0, receivedData.size());
	}

	@Test
	public void testRegisterCallback_thenUnregisterForOtherEventIds_completionNotCalled() throws InterruptedException {
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				uniqueEventId,
				new EdgeCallback() {
					@Override
					public void onComplete(final List<EdgeEventHandle> handles) {
						latchOfOne.countDown();
					}
				}
			);

		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId2);
		CompletionCallbacksManager.getInstance().unregisterCallback(uniqueEventId3);

		assertFalse(latchOfOne.await(100, TimeUnit.MILLISECONDS));
	}

	// ----------- null, empty
	@Test
	public void testUnregisterCallback_withNullEmptyUniqueEvent_doesNotCrash() {
		CompletionCallbacksManager.getInstance().unregisterCallback(null);
		CompletionCallbacksManager.getInstance().unregisterCallback("");
	}

	@Test
	public void testRegisterCallback_withNullEmptyUniqueEvent_doesNotCrash() {
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				null,
				new EdgeCallback() {
					@Override
					public void onComplete(List<EdgeEventHandle> handles) {}
				}
			);
		CompletionCallbacksManager
			.getInstance()
			.registerCallback(
				"",
				new EdgeCallback() {
					@Override
					public void onComplete(List<EdgeEventHandle> handles) {}
				}
			);
	}

	@Test
	public void testRegisterCallback_withNullCallback_doesNotCrash() {
		CompletionCallbacksManager.getInstance().registerCallback(uniqueEventId, null);
	}

	@Test
	public void testEventHandleReceived_withNullEmptyUniqueEvent_doesNotCrash() {
		CompletionCallbacksManager.getInstance().eventHandleReceived(null, eventHandle);
		CompletionCallbacksManager.getInstance().eventHandleReceived("", eventHandle);
	}

	@Test
	public void testEventHandleReceived_withUnregisteredUniqueEvent_doesNotCrash() {
		CompletionCallbacksManager.getInstance().eventHandleReceived(uniqueEventId, eventHandle);
	}
}
