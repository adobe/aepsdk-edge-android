/*
  Copyright 2019 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import java.util.List;

/**
 * Extended callback interface for {@code Edge.sendEvent} callers that want to receive both
 * success handles and per-event errors in a single callback object.
 *
 * <p>Implementors of this interface can pass an instance to either
 * {@link Edge#sendEvent(ExperienceEvent, EdgeCallback)} or
 * {@link Edge#sendEvent(ExperienceEvent, EdgeCallbackWithError)}. In both cases the SDK checks
 * for this interface at dispatch time and calls {@link #onError} when errors are available.
 */
public interface EdgeCallbackWithError extends EdgeCallback {

	/**
	 * Called when one or more errors are returned for the sent {@link ExperienceEvent}.
	 * May be called alongside {@link #onComplete} if some handles were also returned.
	 *
	 * @param errors list of errors returned by the Adobe Experience Edge; never null, never empty
	 */
	void onError(final List<EdgeEventError> errors);
}
