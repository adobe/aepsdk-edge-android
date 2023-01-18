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

package com.adobe.marketing.mobile.util;

import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NetworkCallback;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.Networking;
import java.util.ArrayList;
import java.util.List;

public class MockNetworkService implements Networking {

	public int connectAsyncWasCalledTimes = 0;
	public List<NetworkRequest> connectAsyncParamNetworkRequest = new ArrayList<>();
	public HttpConnecting mockConnectAsyncConnection;

	@Override
	public void connectAsync(NetworkRequest networkRequest, NetworkCallback networkCallback) {
		connectAsyncWasCalledTimes += 1;
		connectAsyncParamNetworkRequest.add(networkRequest);

		if (networkCallback != null) {
			networkCallback.call(mockConnectAsyncConnection);
		}
	}
}
