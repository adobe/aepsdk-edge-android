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
        const val EMPTY_STRING = "Empty string: \"\""
        const val NONE = "(None)"
    }

    object MobilePropertyId {
        const val PROD = "94f571f308d5/6b1be84da76a/launch-023a1b64f561-development"
    }

    object NetworkKeys {
        const val DEFAULT_EDGE_DOMAIN = "edge.adobedc.net"
    }
}