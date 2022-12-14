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

package com.adobe.marketing.mobile.util;

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitQueuing;
import com.adobe.marketing.mobile.services.PersistentHitQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Mock class used for testing with {@link PersistentHitQueue}
 */
public class MockHitQueue extends HitQueuing {

	List<DataEntity> queueParams = new ArrayList<>();
	public boolean wasQueueCalled = false;
	public boolean wasClearCalled = false;
	public boolean wasSuspendCalled = false;
	public boolean wasBeginProcessingCalled = false;

	@Override
	public boolean queue(final DataEntity entity) {
		wasQueueCalled = true;
		queueParams.add(entity);
		return true;
	}

	@Override
	public void beginProcessing() {
		wasBeginProcessingCalled = true;
	}

	@Override
	public void suspend() {
		wasSuspendCalled = true;
	}

	@Override
	public void clear() {
		wasClearCalled = true;
	}

	@Override
	public int count() {
		return queueParams.size();
	}

	@Override
	public void close() {}

	public Collection<DataEntity> getCachedEntities() {
		return queueParams;
	}
}
