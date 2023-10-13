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

package com.adobe.marketing.mobile

import com.adobe.marketing.mobile.edge.Datastream
import com.adobe.marketing.mobile.edge.SDKConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class SDKConfigTest {

    @Test
    fun test_datastream_toMap_happy() {
        // setup
        val datastream = Datastream("test")

        // test
        val map = datastream.toMap()

        // verify
        assertEquals("test", map["original"])
    }

    @Test
    fun test_sdkConfig_toMap_happy() {
        // setup
        val datastream = Datastream("test")
        val sdkConfig = SDKConfig(datastream)
        val expectedMap = mapOf("datastream" to mapOf("original" to "test"))

        // test
        val map = sdkConfig.toMap()

        // verify
        assertEquals(expectedMap, map)
    }

    @Test
    fun test_sdkConfig_toMap_emptyOriginalDatastreamID_returnsEmptyString() {
        // setup
        val datastream = Datastream("")
        val sdkConfig = SDKConfig(datastream)
        val expectedMap = mapOf("datastream" to mapOf("original" to ""))

        // test
        val map = sdkConfig.toMap()

        // verify
        assertEquals(expectedMap, map)
    }
}
