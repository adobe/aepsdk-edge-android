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

package com.adobe.marketing.mobile.integration.util

import com.adobe.marketing.mobile.integration.BuildConfig

/**
 * All location hint values available for the Edge Network extension
 */
enum class EdgeLocationHint(val rawValue: String) {
    /** Oregon, USA */
    OR2("or2"),

    /** Virginia, USA */
    VA6("va6"),

    /** Ireland */
    IRL1("irl1"),

    /** India */
    IND1("ind1"),

    /** Japan */
    JPN3("jpn3"),

    /** Singapore */
    SGP3("sgp3"),

    /** Australia */
    AUS3("aus3");

    companion object {
        /**
         * Returns the corresponding `EdgeLocationHint` enum for a given string value.
         */
        fun fromString(value: String): EdgeLocationHint? {
            return values().find { it.rawValue == value }
        }
    }
}

fun extractEnvironmentVariable(keyName: String, enumClass: Class<EdgeLocationHint>): EdgeLocationHint? {
    // Mocked function as the actual implementation is not provided.
    // You would replace this with the actual implementation.
    return null
}