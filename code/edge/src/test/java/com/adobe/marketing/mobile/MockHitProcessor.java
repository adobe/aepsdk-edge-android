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

import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitProcessingResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock class used for testing with {@link EdgeHitProcessor}
 */
class MockHitProcessor extends EdgeHitProcessor {

	boolean processHitReturns = true;
	List<DataEntity> processHitParams = new ArrayList<>();

	MockHitProcessor() {
		super(null, null, null, null, null);
	}

	@Override
	public void processHit(final DataEntity entity, final HitProcessingResult processingResult) {
		processHitParams.add(entity);
		processingResult.complete(processHitReturns);
	}
}
