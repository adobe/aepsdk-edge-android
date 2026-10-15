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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.util.FakeNamedCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

public class DeviceAttributesEdgeStateTests {

	private HitQueuing mockHitQueue;
	private HitQueuing mockBypassQueue;
	private EdgeSharedStateCallback mockSharedStateCallback;
	private FakeNamedCollection fakeNamedCollection;
	private EdgeProperties properties;
	private EdgeState state;

	@Before
	public void setUp() {
		mockHitQueue = mock(HitQueuing.class);
		mockBypassQueue = mock(HitQueuing.class);
		mockSharedStateCallback = mock(EdgeSharedStateCallback.class);
		fakeNamedCollection = new FakeNamedCollection();
		properties = new EdgeProperties(fakeNamedCollection);
		state = new EdgeState(mockHitQueue, mockBypassQueue, properties, mockSharedStateCallback);
	}

	@Test
	public void testInit_doesNotStartBypassQueue() {
		verifyNoInteractions(mockBypassQueue);
		// the primary queue keeps its existing pending-consent behavior
		verify(mockHitQueue).suspend();
	}

	@Test
	public void testBootup_startsBypassQueueOnceAfterImplementationDetailsAndPropertiesAreLoaded() {
		final EdgeProperties persisted = new EdgeProperties(fakeNamedCollection);
		persisted.setLocationHint("or2", 1800);
		persisted.saveToPersistence();
		final Map<String, Object> hubState = new HashMap<>();
		hubState.put("version", "3.0.0");
		mockHubState(new SharedStateResult(SharedStateStatus.SET, hubState));
		final List<Object> observedAtStart = new ArrayList<>();
		doAnswer(invocation -> {
				observedAtStart.add(state.getImplementationDetails());
				observedAtStart.add(state.getLocationHint());
				return null;
			})
			.when(mockBypassQueue)
			.beginProcessing();

		assertTrue(state.bootupIfNeeded());
		assertTrue(state.bootupIfNeeded());

		verify(mockBypassQueue, times(1)).beginProcessing();
		assertEquals(2, observedAtStart.size());
		assertTrue(observedAtStart.get(0) instanceof Map);
		assertEquals("or2", observedAtStart.get(1));
		final InOrder inOrder = inOrder(mockSharedStateCallback, mockBypassQueue);
		inOrder.verify(mockSharedStateCallback).createSharedState(org.mockito.ArgumentMatchers.anyMap(), isNull());
		inOrder.verify(mockBypassQueue).beginProcessing();
	}

	@Test
	public void testBootupNotComplete_doesNotStartBypassQueue() {
		mockHubState(new SharedStateResult(SharedStateStatus.PENDING, null));

		assertFalse(state.bootupIfNeeded());

		verify(mockBypassQueue, never()).beginProcessing();
	}

	@Test
	public void testCollectConsentChanges_doNotAffectBypassQueue() {
		for (final ConsentStatus status : new ConsentStatus[] {
			ConsentStatus.YES,
			ConsentStatus.NO,
			ConsentStatus.PENDING,
			ConsentStatus.YES,
		}) {
			state.updateCurrentConsent(status);
		}

		verifyNoInteractions(mockBypassQueue);
	}

	@Test
	public void testMissingBypassQueue_doesNotChangePrimaryConsentLifecycle() {
		final HitQueuing primary = mock(HitQueuing.class);
		final EdgeState stateWithoutBypass = new EdgeState(primary, null, properties, mockSharedStateCallback);
		mockHubState(new SharedStateResult(SharedStateStatus.SET, new HashMap<>()));

		assertTrue(stateWithoutBypass.bootupIfNeeded());
		stateWithoutBypass.updateCurrentConsent(ConsentStatus.YES);
		stateWithoutBypass.updateCurrentConsent(ConsentStatus.NO);

		// pending at init -> suspend; bootup defaults consent to yes -> begin; yes -> begin; no -> clear + begin
		final InOrder inOrder = inOrder(primary);
		inOrder.verify(primary).suspend();
		inOrder.verify(primary, times(2)).beginProcessing();
		inOrder.verify(primary).clear();
		inOrder.verify(primary).beginProcessing();
		inOrder.verifyNoMoreInteractions();
	}

	private void mockHubState(final SharedStateResult result) {
		when(mockSharedStateCallback.getSharedState(eq(EdgeConstants.SharedState.HUB), isNull())).thenReturn(result);
	}
}
