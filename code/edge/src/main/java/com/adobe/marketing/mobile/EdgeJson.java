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

class EdgeJson {

	private EdgeJson() {}

	static class Event {

		static final String XDM = "xdm";
		static final String DATA = "data";
		static final String METADATA = "meta";
		static final String QUERY = "query";

		static class Metadata {

			static final String COLLECT = "collect";
			static final String DATASET_ID = "datasetId";

			private Metadata() {}
		}

		static class Query {

			static final String OPERATION = "operation";
			static final String OPERATION_UPDATE = "update";

			private Query() {}
		}

		static class Xdm {

			static final String EVENT_ID = "_id";
			static final String EVENT_MERGE_ID = "eventMergeId";
			static final String EVENT_TYPE = "eventType";
			static final String TIMESTAMP = "timestamp";
			static final String IDENTITY_MAP = "identityMap";
			static final String COMMERCE = "commerce";
			static final String BEACON = "beacon";
			static final String PLACE_CONTEXT = "placeContext";

			private Xdm() {}
		}

		static class Consent {

			static final String VERSION_KEY = "version";
			static final String VERSION_VALUE = "2.0";
			static final String VALUE_KEY = "value";
			static final String CONSENT_KEY = "consent";
			static final String STANDARD_KEY = "standard";
			static final String STANDARD_VALUE = "Adobe";

			private Consent() {}
		}

		static class ImplementationDetails {

			// Keys defined by ImplementationDetails XDM data type
			static final String IMPLEMENTATION_DETAILS = "implementationDetails";
			static final String NAME = "name";
			static final String VERSION = "version";
			static final String ENVIRONMENT = "environment";

			// Values
			static final String BASE_NAMESPACE = "https://ns.adobe.com/experience/mobilesdk/android";
			static final String ENVIRONMENT_VALUE_APP = "app";
			static final String VALUE_UNKNOWN = "unknown";
			static final String WRAPPER_REACT_NATIVE = "reactnative";
			static final String WRAPPER_CORDOVA = "cordova";
			static final String WRAPPER_FLUTTER = "flutter";
			static final String WRAPPER_UNITY = "unity";
			static final String WRAPPER_XAMARIN = "xamarin";

			private ImplementationDetails() {}
		}

		private Event() {}
	}

	static class Response {

		static final String HANDLE = "handle";
		static final String ERRORS = "errors";
		static final String WARNINGS = "warnings";

		static class EventHandle {

			static final String TYPE = "type";
			static final String PAYLOAD = "payload";
			static final String EVENT_INDEX = "eventIndex";
			static final String REPORT = "report";

			static class Store {

				static final String TYPE = "state:store";
				static final String KEY = "key";
				static final String VALUE = "value";
				static final String MAX_AGE = "maxAge";
				static final String EXPIRY_DATE = "expiryDate";

				private Store() {}
			}

			static class LocationHint {

				static final String TYPE = "locationHint:result";
				static final String HINT = "hint";
				static final String SCOPE = "scope";
				static final String TTL_SECONDS = "ttlSeconds";
				static final String EDGE_NETWORK = "EdgeNetwork";

				private LocationHint() {}
			}

			private EventHandle() {}
		}

		static class Error {

			static final String SEVERITY = "severity";
			static final String STATUS = "status";
			static final String TITLE = "title";
			static final String EVENT_INDEX = "eventIndex";
			static final String TYPE = "type";

			private Error() {}
		}

		private Response() {}
	}
}
