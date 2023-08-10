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

package com.adobe.marketing.mobile.util

object TestConstants {
    const val EVENT_NAME_REQUEST_CONTENT = "AEP Request Event"
    const val EVENT_NAME_RESPONSE_CONTENT = "AEP Response Event Handle"
    const val EVENT_NAME_ERROR_RESPONSE_CONTENT = "AEP Error Response"
    const val NETWORK_REQUEST_MAX_RETRIES = 5
    const val EDGE_DATA_STORAGE = "EdgeDataStorage"
    const val LOG_TAG = "FunctionalTestsFramework"
    const val EXTENSION_NAME = "com.adobe.edge"

    // Event type and sources used by Monitor Extension
    object EventType {
        const val MONITOR = "com.adobe.functional.eventType.monitor"
    }

    object EventSource {
        const val SHARED_STATE_REQUEST = "com.adobe.eventSource.sharedStateRequest"
        const val SHARED_STATE_RESPONSE = "com.adobe.eventSource.sharedStateResponse"
        const val UNREGISTER = "com.adobe.eventSource.unregister"
    }

    object Defaults {
        const val WAIT_TIMEOUT_MS = 1000
        const val WAIT_NETWORK_REQUEST_TIMEOUT_MS = 2000
        const val WAIT_EVENT_TIMEOUT_MS = 2000
        const val WAIT_SHARED_STATE_TIMEOUT_MS = 3000
        const val EXEDGE_INTERACT_URL_STRING = "https://edge.adobedc.net/ee/v1/interact"
        const val EXEDGE_INTERACT_OR2_LOC_URL_STRING = "https://edge.adobedc.net/ee/or2/v1/interact"
        const val EXEDGE_INTERACT_PRE_PROD_URL_STRING =
            "https://edge.adobedc.net/ee-pre-prd/v1/interact"
        const val EXEDGE_INTERACT_INT_URL_STRING = "https://edge-int.adobedc.net/ee/v1/interact"
        const val EXEDGE_CONSENT_URL_STRING = "https://edge.adobedc.net/ee/v1/privacy/set-consent"
        const val EXEDGE_CONSENT_PRE_PROD_URL_STRING =
            "https://edge.adobedc.net/ee-pre-prd/v1/privacy/set-consent"
        const val EXEDGE_CONSENT_INT_URL_STRING =
            "https://edge-int.adobedc.net/ee/v1/privacy/set-consent"
        const val EXEDGE_MEDIA_PROD_URL_STRING = "https://edge.adobedc.net/ee/va/v1/sessionstart"
        const val EXEDGE_MEDIA_OR2_LOC_URL_STRING =
            "https://edge.adobedc.net/ee/or2/va/v1/sessionstart"
    }

    object EventDataKey {
        const val EDGE_REQUEST_ID = "requestId"
        const val REQUEST_EVENT_ID = "requestEventId"
        const val DATASET_ID = "datasetId"

        // Used by Monitor Extension
        const val STATE_OWNER = "stateowner"
    }

    object DataStoreKeys {
        const val STORE_PAYLOADS = "storePayloads"
    }

    object SharedState {
        const val STATE_OWNER = "stateowner"
        const val EDGE = "com.adobe.edge"
        const val CONFIGURATION = "com.adobe.module.configuration"
        const val CONSENT = "com.adobe.edge.consent"
        const val ASSURANCE = "com.adobe.assurance"
        const val IDENTITY = "com.adobe.module.identity"
        const val LIFECYCLE = "com.adobe.module.lifecycle"

        internal object Configuration {
            const val EDGE_CONFIG_ID = "edge.configId"
        }

        internal object Identity {
            const val ECID = "mid"
            const val BLOB = "blob"
            const val LOCATION_HINT = "locationhint"
            const val VISITOR_IDS_LIST = "visitoridslist"
        }

        internal object Assurance {
            const val INTEGRATION_ID = "integrationid"
        }
    }

    object NetworkKeys {
        const val REQUEST_URL = "https://edge.adobedc.net/ee/v1"
        const val REQUEST_PARAMETER_KEY_CONFIG_ID = "configId"
        const val REQUEST_PARAMETER_KEY_REQUEST_ID = "requestId"
        const val REQUEST_HEADER_KEY_REQUEST_ID = "X-Request-ID"
        const val HEADER_KEY_AEP_VALIDATION_TOKEN = "X-Adobe-AEP-Validation-Token"
        const val DEFAULT_CONNECT_TIMEOUT_SECONDS = 5
        const val DEFAULT_READ_TIMEOUT_SECONDS = 5
        const val HEADER_KEY_ACCEPT = "accept"
        const val HEADER_KEY_CONTENT_TYPE = "Content-Type"
        const val HEADER_VALUE_APPLICATION_JSON = "application/json"
    }
}