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
 * Callback interface used for returning response from the Adobe Experience Edge to the mobile application.
 */
public interface EdgeCallback {
	/**
	 * This method is called when the response is successfully fetched from the Adobe Experience Edge.
	 * It can be called with an empty list, one or multiple event handles.
	 *
	 * @param handles response from the server
	 */
	void onComplete(final List<EdgeEventHandle> handles);
}
