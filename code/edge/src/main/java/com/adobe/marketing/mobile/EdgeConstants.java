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

final class EdgeConstants {

	static final String EDGE_DATA_STORAGE = "EdgeDataStorage";
	static final String EXTENSION_VERSION = "2.4.0";
	static final String EXTENSION_NAME = "com.adobe.edge";
	static final String FRIENDLY_NAME = "Edge";
	static final String LOG_TAG = FRIENDLY_NAME;

	static final class EventName {

		static final String REQUEST_CONTENT = "AEP Request Event";
		static final String RESPONSE_CONTENT = "AEP Response Event Handle";
		static final String CONTENT_COMPLETE = "AEP Response Complete";
		static final String ERROR_RESPONSE_CONTENT = "AEP Error Response";
		static final String REQUEST_LOCATION_HINT = "Edge Request Location Hint";
		static final String RESPONSE_LOCATION_HINT = "Edge Location Hint Response";
		static final String UPDATE_LOCATION_HINT = "Edge Update Location Hint";

		private EventName() {}
	}

	static final class Defaults {

		static final long API_CALLBACK_TIMEOUT_MILLIS = 5000L;
		static final String REQUEST_CONFIG_RECORD_SEPARATOR = "\u0000";
		static final String REQUEST_CONFIG_LINE_FEED = "\n";
		static final int RETRY_INTERVAL_SECONDS = 5;
		static final int LOCATION_HINT_TTL_SEC = 1800;

		static final ConsentStatus COLLECT_CONSENT_YES = ConsentStatus.YES; // used if Consent extension is not registered
		static final ConsentStatus COLLECT_CONSENT_PENDING = ConsentStatus.PENDING; // used when Consent encoding failed or the value different than y/n

		private Defaults() {}
	}

	static final class EventDataKeys {

		static final String EDGE_REQUEST_ID = "requestId";
		static final String REQUEST_EVENT_ID = "requestEventId";
		static final String DATASET_ID = "datasetId";
		static final String CONSENTS = "consents";
		static final String IDENTITY_MAP = "identityMap";
		static final String LOCATION_HINT = "locationHint";

		static final class Config {

			static final String KEY = "config";
			static final String DATASTREAM_ID_OVERRIDE = "datastreamIdOverride";
			static final String DATASTREAM_CONFIG_OVERRIDE = "datastreamConfigOverride";
		}

		static final class Request {

			static final String KEY = "request";
			static final String PATH = "path";
			// sendCompletion - boolean flag to determine if a "complete" event is requested
			static final String SEND_COMPLETION = "sendCompletion";

			private Request() {}
		}

		private EventDataKeys() {}
	}

	static final class DataStoreKeys {

		static final String STORE_PAYLOADS = "storePayloads";
		static final String RESET_IDENTITIES_DATE = "resetIdentitiesDate";
		static final String PROPERTY_LOCATION_HINT = "locationHint";
		static final String PROPERTY_LOCATION_HINT_EXPIRY_TIMESTAMP = "locationHintExpiryTimestamp";

		private DataStoreKeys() {}
	}

	static final class SharedState {

		static final String STATE_OWNER = "stateowner";
		static final String CONFIGURATION = "com.adobe.module.configuration";
		static final String ASSURANCE = "com.adobe.assurance";
		static final String IDENTITY = "com.adobe.edge.identity";
		static final String HUB = "com.adobe.module.eventhub";
		static final String CONSENT = "com.adobe.edge.consent";

		static final class Edge {

			static final String LOCATION_HINT = "locationHint";

			private Edge() {}
		}

		static final class Configuration {

			static final String EDGE_CONFIG_ID = "edge.configId";
			static final String EDGE_DOMAIN = "edge.domain";
			static final String EDGE_REQUEST_ENVIRONMENT = "edge.environment";

			private Configuration() {}
		}

		static final class Assurance {

			static final String INTEGRATION_ID = "integrationid";

			private Assurance() {}
		}

		static final class Consent {

			static final String CONSENTS = "consents";
			static final String COLLECT = "collect";
			static final String VAL = "val";

			private Consent() {}
		}

		static final class Hub {

			static final String EXTENSIONS = "extensions";
			static final String VERSION = "version";
			static final String WRAPPER = "wrapper";
			static final String TYPE = "type";

			private Hub() {}
		}

		private SharedState() {}
	}

	static final class NetworkKeys {

		static final String SCHEME_HTTPS = "https://";
		static final String DEFAULT_DOMAIN = "edge.adobedc.net";
		static final String REQUEST_DOMAIN_INT = "edge-int.adobedc.net";
		static final String REQUEST_URL_VERSION = "/v1";
		static final String REQUEST_URL_PROD_PATH = "/ee";
		static final String REQUEST_URL_PRE_PROD_PATH = "/ee-pre-prd";
		static final String REQUEST_PARAMETER_KEY_CONFIG_ID = "configId";
		static final String REQUEST_PARAMETER_KEY_REQUEST_ID = "requestId";

		static final String HEADER_KEY_AEP_VALIDATION_TOKEN = "X-Adobe-AEP-Validation-Token";
		static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
		static final int DEFAULT_READ_TIMEOUT_SECONDS = 5;
		static final String HEADER_KEY_ACCEPT = "accept";
		static final String HEADER_KEY_CONTENT_TYPE = "Content-Type";
		static final String HEADER_VALUE_APPLICATION_JSON = "application/json";
		static final String HEADER_KEY_RETRY_AFTER = "Retry-After";

		private NetworkKeys() {}
	}

	private EdgeConstants() {}
}
