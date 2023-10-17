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

package com.adobe.marketing.mobile.edge

/**
 * SDK configuration metadata. Used to pass the original SDK configuration
 * to the Edge Network request when the configuration is overridden.
 */
data class SDKConfig(val datastream: Datastream) {
    fun toMap(): MutableMap<String, Any> {
        return mutableMapOf(
            "datastream" to datastream.toMap()
        )
    }
}

/**
 * Field of the SDKConfig metadata. Used to pass the original datastream ID to the Edge Network request
 * when the datastream ID is overridden.
 */
data class Datastream(val original: String) {
    fun toMap(): MutableMap<String, Any> {
        return mutableMapOf(
            "original" to original
        )
    }
}
