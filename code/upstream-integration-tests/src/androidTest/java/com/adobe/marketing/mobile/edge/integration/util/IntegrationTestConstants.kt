/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.edge.integration.util

internal object IntegrationTestConstants {
    object ConfigurationKey {
        const val EDGE_DOMAIN = "edge.domain"
    }

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
    }

    object ExtensionName {
        const val CONFIGURATION = "com.adobe.module.configuration"
    }

    // Primarily used in the context of GitHub Action workflows to transform preset location hint
    // options into the intended actual location hint value.
    object LocationHintMapping {
        const val EMPTY_STRING = "EmptyString"
        const val NONE = "None"
    }

    object MobilePropertyId {
        const val PROD = "94f571f308d5/6b1be84da76a/launch-023a1b64f561-development"
    }

    object NetworkKeys {
        const val DEFAULT_EDGE_DOMAIN = "edge.adobedc.net"
    }
}
