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

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class RequestMetadataTest {

    @Test
    fun test_getter_setter_for_sdkConfig_nonEmptyMap() {
        // test
        val requestMetadata = RequestMetadata.Builder()
            .setSdkConfig(mapOf("key" to "value"))
            .build()

        // verify
        assertEquals(mapOf("key" to "value"), requestMetadata.sdkConfig)
    }

    @Test
    fun test_getter_setter_for_sdkConfig_emptyMap() {
        // test
        val requestMetadata = RequestMetadata.Builder()
            .setSdkConfig(mapOf())
            .build()

        // verify
        assertTrue(requestMetadata.sdkConfig.isEmpty())
    }

    @Test
    fun test_getter_setter_for_configOverrides_nonEmptyMap() {
        // test
        val requestMetadata = RequestMetadata.Builder()
            .setConfigOverrides(mapOf("key" to "value"))
            .build()

        // verify
        assertEquals(mapOf("key" to "value"), requestMetadata.configOverrides)
    }

    @Test
    fun test_getter_setter_for_configOverrides_emptyMap() {
        // test
        val requestMetadata = RequestMetadata.Builder()
            .setConfigOverrides(mapOf())
            .build()

        // verify
        assertTrue(requestMetadata.configOverrides.isEmpty())
    }
}
