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
import static org.junit.Assert.assertNull;

import com.adobe.marketing.mobile.util.FakeNamedCollection;
import org.junit.Before;
import org.junit.Test;

public class DeviceAttributesResponseHandlerTests {

	private FakeNamedCollection fakeNamedCollection;
	private DeviceAttributesResponseHandler handler;

	@Before
	public void setUp() {
		fakeNamedCollection = new FakeNamedCollection();
		handler = new DeviceAttributesResponseHandler(fakeNamedCollection);
	}

	@Test
	public void testHandle_locationHint_isIgnored() {
		handler.handle(
			"{\"handle\":[{\"type\":\"locationHint:result\",\"payload\":[" +
			"{\"scope\":\"EdgeNetwork\",\"hint\":\"or2\",\"ttlSeconds\":1800}]}," +
			"{\"type\":\"state:store\",\"payload\":[{\"key\":\"kndctr_org_cluster\",\"value\":\"or2\",\"maxAge\":1800}]}]}",
			true
		);

		// only the state:store handle is applied
		assertEquals(1, new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores().size());
	}

	@Test
	public void testHandle_storePayloads_areSaved() {
		handler.handle(storeBody(), true);

		assertEquals(2, new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores().size());
	}

	@Test
	public void testHandle_storePayloads_areIgnoredWhenNotApplied() {
		handler.handle(storeBody(), false);

		assertNull(new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores());
	}

	@Test
	public void testHandle_otherHandleTypes_areIgnored() {
		handler.handle(
			"{\"handle\":[{\"type\":\"personalization:decisions\",\"payload\":[{\"id\":\"1\"}]}," +
			"{\"type\":\"identity:result\",\"payload\":[{\"namespace\":{\"code\":\"ECID\"}}]}]}",
			true
		);

		assertNull(new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores());
	}

	@Test
	public void testHandle_invalidOrEmptyBody_doesNothing() {
		for (final String body : new String[] { null, "", "not json", "{}", "{\"handle\":{}}", "{\"handle\":[1]}" }) {
			handler.handle(body, true);
		}

		assertNull(new StoreResponsePayloadManager(fakeNamedCollection).getActiveStores());
	}

	private static String storeBody() {
		return (
			"{\"requestId\":\"r1\",\"handle\":[{\"type\":\"state:store\",\"payload\":[" +
			"{\"key\":\"kndctr_org_cluster\",\"value\":\"or2\",\"maxAge\":1800}," +
			"{\"key\":\"kndctr_org_identity\",\"value\":\"id\",\"maxAge\":34128000}]}]}"
		);
	}
}
