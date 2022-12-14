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
import static org.junit.Assert.fail;

import com.adobe.marketing.mobile.util.FakeNamedCollection;
import com.adobe.marketing.mobile.util.MockHitQueue;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;

public class EdgeStateTests {

	private EdgeState state;
	private EdgeProperties properties;
	private FakeNamedCollection fakeNamedCollection;
	private MockHitQueue mockHitQueue;

	private EdgeSharedStateCallback mockSharedStateCallback;
	private Map<String, Object> mockSharedState;

	@Before
	public void setUp() {
		fakeNamedCollection = new FakeNamedCollection();
		mockHitQueue = new MockHitQueue();

		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					fail("EdgeState is not expected to call getSharedState.");
					return null;
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
				}
			};

		properties = new EdgeProperties(fakeNamedCollection);
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);
	}

	@Test
	public void testBootupIfNeeded_LoadsPropertiesFromPersistence_AndCreatesSharedState_WithLocationHint()
		throws InterruptedException {
		CountDownLatch getSharedStateCalled = new CountDownLatch(1);
		CountDownLatch createSharedStateCalled = new CountDownLatch(1);
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					getSharedStateCalled.countDown();
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						return null;
					}

					return new SharedStateResult(SharedStateStatus.SET, new HashMap<>());
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
					createSharedStateCalled.countDown();
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);

		long expectedExpiryTimestamp = setLocationHintInMockDataStore("or2", 100);
		state.bootupIfNeeded();

		assertEquals("or2", properties.getLocationHint());

		assertTrue(getSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertTrue(createSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertNotNull(mockSharedState);
		assertEquals("or2", mockSharedState.get(EdgeConstants.SharedState.Edge.LOCATION_HINT));
	}

	@Test
	public void testBootupIfNeeded_LoadsPropertiesFromPersistence_AndCreatesSharedState_WithExpiredLocationHint()
		throws InterruptedException {
		CountDownLatch getSharedStateCalled = new CountDownLatch(1);
		CountDownLatch createSharedStateCalled = new CountDownLatch(1);
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					getSharedStateCalled.countDown();
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						return null;
					}

					return new SharedStateResult(SharedStateStatus.SET, new HashMap<>());
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
					createSharedStateCalled.countDown();
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);
		long expectedExpiryTimestamp = setLocationHintInMockDataStore("or2", 1);
		Thread.sleep(1100); // wait for hint to expire
		state.bootupIfNeeded();
		assertNull(properties.getLocationHint());

		assertTrue(getSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertTrue(createSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertNotNull(mockSharedState);
		assertTrue(mockSharedState.isEmpty()); // empty shared state created
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStatePending_returnsFalse() throws InterruptedException {
		CountDownLatch getSharedStateCalled = new CountDownLatch(1);
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					getSharedStateCalled.countDown();
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						fail("Unexpected getSharedState call for " + stateOwner);
						return null;
					}

					return new SharedStateResult(SharedStateStatus.PENDING, new HashMap<>());
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					fail("Unexpected call to createSharedState");
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);

		assertFalse(state.bootupIfNeeded());
		assertTrue(getSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStateNull_returnsFalse() throws InterruptedException {
		CountDownLatch getSharedStateCalled = new CountDownLatch(1);
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					getSharedStateCalled.countDown();
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						fail("Unexpected getSharedState call for " + stateOwner);
						return null;
					}

					return null;
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					fail("Unexpected call to createSharedState");
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);

		assertFalse(state.bootupIfNeeded());
		assertTrue(getSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStateSetAndValid_returnsTrue() throws InterruptedException {
		CountDownLatch getSharedStateCalled = new CountDownLatch(1);
		CountDownLatch createSharedStateCalled = new CountDownLatch(1);
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					getSharedStateCalled.countDown();
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						fail("Unexpected getSharedState call for " + stateOwner);
						return null;
					}

					return new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(false));
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
					createSharedStateCalled.countDown();
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);

		assertTrue(state.bootupIfNeeded());

		assertTrue(getSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertTrue(createSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertNotNull(mockSharedState);
		assertTrue(mockSharedState.isEmpty()); // empty shared state created
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStateSetAndNoData_returnsTrue() throws InterruptedException {
		CountDownLatch getSharedStateCalled = new CountDownLatch(1);
		CountDownLatch createSharedStateCalled = new CountDownLatch(1);
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					getSharedStateCalled.countDown();
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						fail("Unexpected getSharedState call for " + stateOwner);
						return null;
					}

					return new SharedStateResult(SharedStateStatus.SET, null);
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
					createSharedStateCalled.countDown();
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);

		assertTrue(state.bootupIfNeeded());

		assertTrue(getSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertTrue(createSharedStateCalled.await(200, TimeUnit.MILLISECONDS));
		assertNotNull(mockSharedState);
		assertTrue(mockSharedState.isEmpty()); // empty shared state created
	}

	@Test
	public void testBootupIfNeeded_whenConsentNotRegistered_updatesConsentYes() {
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						fail("Unexpected getSharedState call for " + stateOwner);
						return null;
					}

					return new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(false));
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);

		assertTrue(state.bootupIfNeeded());
		assertEquals(ConsentStatus.YES, state.getCurrentCollectConsent());
	}

	@Test
	public void testBootupIfNeeded_whenConsentRegistered_keepsConsentPendingAndWaits() {
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						fail("Unexpected getSharedState call for " + stateOwner);
						return null;
					}

					return new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(true));
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);

		assertTrue(state.bootupIfNeeded());
		assertEquals(ConsentStatus.PENDING, state.getCurrentCollectConsent());
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedState_updatesImplementationDetails() {
		mockSharedStateCallback =
			new EdgeSharedStateCallback() {
				@Override
				public SharedStateResult getSharedState(final String stateOwner, final Event event) {
					if (!EdgeConstants.SharedState.HUB.equals(stateOwner)) {
						fail("Unexpected getSharedState call for " + stateOwner);
						return null;
					}

					return new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(true));
				}

				@Override
				public void createSharedState(final Map<String, Object> state, final Event event) {
					mockSharedState = state;
				}
			};
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);
		state.bootupIfNeeded();
		assertNotNull(state.getImplementationDetails());
		Map<String, Object> details = (Map<String, Object>) state
			.getImplementationDetails()
			.get("implementationDetails");
		assertNotNull(details);
		assertEquals("app", details.get("environment"));
		assertEquals("2.0.0+" + EdgeConstants.EXTENSION_VERSION, details.get("version"));
		assertEquals(EdgeJson.Event.ImplementationDetails.BASE_NAMESPACE, details.get("name"));
	}

	@Test
	public void testGetLocationHint_returnsHint_forValidHint() {
		state.setLocationHint("or2", 100);
		assertEquals("or2", state.getLocationHint());
	}

	@Test
	public void testGetLocationHint_returnsNull_forExpireHint() throws InterruptedException {
		state.setLocationHint("or2", 1);
		Thread.sleep(1100);
		assertNull(state.getLocationHint());
	}

	@Test
	public void testSetLocationHint_updatesHintAndExpiryDate_andSharesState() {
		long expectedExpiryDate = getTimestampForNowPlusSeconds(100);
		state.setLocationHint("or2", 100);
		assertEquals("or2", state.getLocationHint());
		assertNotNull(mockSharedState);
		assertEquals("or2", mockSharedState.get(EdgeConstants.SharedState.Edge.LOCATION_HINT));
		assertEquals("or2", fakeNamedCollection.getString(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT, null));
		assertEquals(
			expectedExpiryDate,
			fakeNamedCollection.getLong(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP, 0),
			2
		);
	}

	@Test
	public void testSetLocationHint_updatesHintAndExpiryDate_andDoesNotShareState_whenHintDoesNotChange() {
		state.setLocationHint("or2", 100); // set initial hint
		mockSharedState = null; // clear received state from previous call

		long expectedExpiryDate = getTimestampForNowPlusSeconds(1800);
		state.setLocationHint("or2", 1800);
		assertEquals("or2", state.getLocationHint());
		assertNull(mockSharedState); // state not created
		assertEquals("or2", fakeNamedCollection.getString(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT, null));
		assertEquals(
			expectedExpiryDate,
			fakeNamedCollection.getLong(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP, 0),
			2
		);
	}

	@Test
	public void testSetLocationHint_updatesHintAndExpiryDate_andSharesState_whenHintExpired()
		throws InterruptedException {
		state.setLocationHint("or2", 1); // set initial hint
		mockSharedState = null; // clear received state from previous call
		Thread.sleep(1100); // let hint expire

		long expectedExpiryDate = getTimestampForNowPlusSeconds(1800);
		state.setLocationHint("or2", 1800); // set same hint
		assertEquals("or2", state.getLocationHint());
		assertNotNull(mockSharedState);
		assertEquals("or2", mockSharedState.get(EdgeConstants.SharedState.Edge.LOCATION_HINT));
		assertEquals("or2", fakeNamedCollection.getString(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT, null));
		assertEquals(
			expectedExpiryDate,
			fakeNamedCollection.getLong(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP, 0),
			2
		);
	}

	@Test
	public void testSetLocationHint_withNull_clearsHintAndExpiryDate_andSharesState() {
		state.setLocationHint("or2", 100); // set initial hint

		state.setLocationHint(null, 100);
		assertNull(state.getLocationHint());
		assertNotNull(mockSharedState);
		assertTrue(mockSharedState.isEmpty()); // empty state shared
		assertFalse(fakeNamedCollection.contains(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT));
		assertFalse(fakeNamedCollection.contains(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP));
	}

	@Test
	public void testSetLocationHint_withNull_notHitSet_doesNotCreateSharedState() {
		state.setLocationHint(null, 100);
		assertNull(mockSharedState); // no state shared as hint did not change
	}

	@Test
	public void testSetLocationHint_withNull_clearsHintAndExpiryDate_andSharesState_whenHintHasExpired()
		throws InterruptedException {
		state.setLocationHint("or2", 100); // set initial hint
		Thread.sleep(1100); // wait for hit to expire

		state.setLocationHint(null, 100);
		assertNull(state.getLocationHint());
		assertNotNull(mockSharedState);
		assertTrue(mockSharedState.isEmpty()); // empty state shared
		assertFalse(fakeNamedCollection.contains(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT));
		assertFalse(fakeNamedCollection.contains(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP));
	}

	private long setLocationHintInMockDataStore(final String hint, final int ttlSeconds) {
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.add(Calendar.SECOND, ttlSeconds);
		fakeNamedCollection.setLong(
			EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP,
			calendar.getTimeInMillis()
		);
		fakeNamedCollection.setString(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT, hint);
		return calendar.getTimeInMillis();
	}

	private long getTimestampForNowPlusSeconds(final int ttlSeconds) {
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.add(Calendar.SECOND, ttlSeconds);
		return calendar.getTimeInMillis();
	}

	private Map<String, Object> getMockEventHubSharedState(final boolean consentRegistered) {
		Map<String, Object> hubState = new HashMap<>();
		hubState.put("version", "2.0.0");
		hubState.put(
			"wrapper",
			new HashMap<String, Object>() {
				{
					put("type", "N");
					put("friendlyName", "None");
				}
			}
		);
		final Map<String, Object> registeredExtensions = new HashMap<>();
		registeredExtensions.put(
			EdgeConstants.SharedState.CONFIGURATION,
			new HashMap<String, Object>() {
				{
					put("version", "2.0.0");
					put("friendlyName", "Configuration");
				}
			}
		);
		registeredExtensions.put(
			EdgeConstants.EXTENSION_NAME,
			new HashMap<String, Object>() {
				{
					put("version", "2.0.0");
					put("friendlyName", "Edge");
				}
			}
		);
		if (consentRegistered) {
			registeredExtensions.put(
				EdgeConstants.SharedState.CONSENT,
				new HashMap<String, Object>() {
					{
						put("version", "2.0.0");
					}
				}
			);
		}
		hubState.put("extensions", registeredExtensions);
		return hubState;
	}
}
