/*
  Copyright 2023 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.util

import com.adobe.marketing.mobile.services.NetworkCallback
import com.adobe.marketing.mobile.services.NetworkRequest
import com.adobe.marketing.mobile.services.NetworkServiceHelper
import com.adobe.marketing.mobile.services.TestableNetworkRequest

internal class RealNetworkService: NetworkServiceHelper() {
    private val helper = TestNetworkService()
    companion object {
        private const val LOG_SOURCE = "RealNetworkService"
    }

    override fun connectAsync(networkRequest: NetworkRequest, resultCallback: NetworkCallback?) {
        val request = TestableNetworkRequest(networkRequest)
        helper.recordSentNetworkRequest(request)
        super.connectAsync(networkRequest) {
            helper.setResponseConnectionFor(request, it)
            helper.countDownExpected(request)

            // Call the original callback
            resultCallback?.call(it)
        }
    }

    // Passthrough for shared helper APIs
    fun reset() {
        helper.reset()
    }

    fun assertAllNetworkRequestExpectations() {
        helper.assertAllNetworkRequestExpectations()
    }


}