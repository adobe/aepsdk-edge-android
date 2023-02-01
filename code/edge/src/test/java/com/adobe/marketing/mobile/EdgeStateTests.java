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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.util.FakeNamedCollection;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class EdgeStateTests {

	private EdgeState state;
	private EdgeProperties properties;
	private FakeNamedCollection fakeNamedCollection;

	@Mock
	HitQueuing mockHitQueue;

	@Mock
	EdgeSharedStateCallback mockSharedStateCallback;

	@Before
	public void setUp() {
		fakeNamedCollection = new FakeNamedCollection();
		properties = new EdgeProperties(fakeNamedCollection);
		mockSharedStateCallback = mock(EdgeSharedStateCallback.class);
		state = new EdgeState(mockHitQueue, properties, mockSharedStateCallback);
	}

	@Test
	public void testBootupIfNeeded_LoadsPropertiesFromPersistence_AndCreatesSharedState_WithLocationHint() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, new HashMap<>()));

		setLocationHintInMockDataStore("or2", 100);
		state.bootupIfNeeded();

		assertEquals("or2", properties.getLocationHint());

		verify(mockSharedStateCallback).getSharedState(eq(EdgeConstants.SharedState.HUB), isNull());
		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback).createSharedState(stateDataCaptor.capture(), isNull());
		assertNotNull(stateDataCaptor.getValue());
		assertEquals("or2", stateDataCaptor.getValue().get(EdgeConstants.SharedState.Edge.LOCATION_HINT));
	}

	@Test
	public void testBootupIfNeeded_LoadsPropertiesFromPersistence_AndCreatesSharedState_WithExpiredLocationHint()
		throws InterruptedException {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, new HashMap<>()));
		setLocationHintInMockDataStore("or2", 1);
		Thread.sleep(1100); // wait for hint to expire
		state.bootupIfNeeded();
		assertNull(properties.getLocationHint());

		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback).createSharedState(stateDataCaptor.capture(), isNull());
		assertNotNull(stateDataCaptor.getValue());
		assertTrue(stateDataCaptor.getValue().isEmpty()); // empty shared state created
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStatePending_returnsFalse() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.PENDING, new HashMap<>()));

		assertFalse(state.bootupIfNeeded());
		verify(mockSharedStateCallback, never()).createSharedState(anyMap(), isNull());
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStateNull_returnsFalse() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull())).thenReturn(null);

		assertFalse(state.bootupIfNeeded());
		verify(mockSharedStateCallback, never()).createSharedState(anyMap(), isNull());
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStateSetAndValid_returnsTrue() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(false)));

		assertTrue(state.bootupIfNeeded());

		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback).createSharedState(stateDataCaptor.capture(), isNull());
		assertNotNull(stateDataCaptor.getValue());
		assertTrue(stateDataCaptor.getValue().isEmpty()); // empty shared state created
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedStateSetAndNoData_returnsTrue() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, null));

		assertTrue(state.bootupIfNeeded());

		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback).createSharedState(stateDataCaptor.capture(), isNull());
		assertNotNull(stateDataCaptor.getValue());
		assertTrue(stateDataCaptor.getValue().isEmpty()); // empty shared state created
	}

	@Test
	public void testBootupIfNeeded_whenConsentNotRegistered_updatesConsentYes() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(false)));

		assertTrue(state.bootupIfNeeded());
		assertEquals(ConsentStatus.YES, state.getCurrentCollectConsent());
	}

	@Test
	public void testBootupIfNeeded_whenConsentRegistered_keepsConsentPendingAndWaits() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(true)));

		assertTrue(state.bootupIfNeeded());
		assertEquals(ConsentStatus.PENDING, state.getCurrentCollectConsent());
	}

	@Test
	public void testBootupIfNeeded_whenHubSharedState_updatesImplementationDetails() {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull()))
			.thenReturn(new SharedStateResult(SharedStateStatus.SET, getMockEventHubSharedState(true)));
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
		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback).createSharedState(stateDataCaptor.capture(), isNull());
		assertNotNull(stateDataCaptor.getValue());
		assertEquals("or2", stateDataCaptor.getValue().get(EdgeConstants.SharedState.Edge.LOCATION_HINT));

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
		clearInvocations(mockSharedStateCallback); // clear received state from previous call

		long expectedExpiryDate = getTimestampForNowPlusSeconds(1800);
		state.setLocationHint("or2", 1800);
		assertEquals("or2", state.getLocationHint());
		verify(mockSharedStateCallback, never()).createSharedState(anyMap(), isNull()); // state not created
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
		clearInvocations(mockSharedStateCallback); // clear received state from previous call
		Thread.sleep(1100); // let hint expire

		long expectedExpiryDate = getTimestampForNowPlusSeconds(1800);
		state.setLocationHint("or2", 1800); // set same hint
		assertEquals("or2", state.getLocationHint());
		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback, times(1)).createSharedState(stateDataCaptor.capture(), isNull());
		assertNotNull(stateDataCaptor.getValue());
		assertEquals("or2", stateDataCaptor.getValue().get(EdgeConstants.SharedState.Edge.LOCATION_HINT));
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
		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback, times(2)).createSharedState(stateDataCaptor.capture(), isNull());
		List<Map<String, Object>> sharedStateData = stateDataCaptor.getAllValues();
		assertNotNull(sharedStateData);
		assertTrue(sharedStateData.get(1).isEmpty()); // empty state shared
		assertFalse(fakeNamedCollection.contains(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT));
		assertFalse(fakeNamedCollection.contains(EdgeConstants.DataStoreKeys.PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP));
	}

	@Test
	public void testSetLocationHint_withNull_notHitSet_doesNotCreateSharedState() {
		state.setLocationHint(null, 100);
		verify(mockSharedStateCallback, never()).createSharedState(anyMap(), isNull()); // no state shared as hint did not change
	}

	@Test
	public void testSetLocationHint_withNull_clearsHintAndExpiryDate_andSharesState_whenHintHasExpired()
		throws InterruptedException {
		state.setLocationHint("or2", 100); // set initial hint
		Thread.sleep(1100); // wait for hit to expire

		state.setLocationHint(null, 100);
		assertNull(state.getLocationHint());
		ArgumentCaptor<Map<String, Object>> stateDataCaptor = ArgumentCaptor.forClass(Map.class);
		verify(mockSharedStateCallback, times(2)).createSharedState(stateDataCaptor.capture(), isNull());
		List<Map<String, Object>> sharedStateData = stateDataCaptor.getAllValues();
		assertNotNull(sharedStateData);
		assertTrue(sharedStateData.get(1).isEmpty()); // empty state shared
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
