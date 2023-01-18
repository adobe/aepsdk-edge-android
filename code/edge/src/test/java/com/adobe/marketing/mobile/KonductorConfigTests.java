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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;

import org.junit.Test;

public class KonductorConfigTests {

	@Test
	public void testKonductorConfig_fromJsonRequestNull() {
		assertNull(KonductorConfig.fromJsonRequest(null));
	}

	@Test
	public void testKonductorConfig_fromJsonRequestValid() {
		String jsonString =
			"{\"xdm\":{\"environment\":{\"operatingSystemVersion\":\"9\",\"carrier\":\"Android\",\"operatingSystem\":\"Android 9\"},\"identityMap\":{\"ECID\":[{\"id\":\"17166732221897949268328316449120339327\"}]},\"device\":{\"screenWidth\":\"1440\",\"screenOrientation\":\"portrait\",\"screenHeight\":\"2392\",\"model\":\"Android SDK built for x86\",\"modelNumber\":\"PSR1.180720.075\",\"type\":\"phone\",\"manufacturer\":\"Google\"}},\"meta\":{\"konductorConfig\":{\"streaming\":{\"lineFeed\":\"\\n\",\"enabled\":true,\"recordSeparator\":\"\\u0000\"}}},\"events\":[{\"xdm\":{\"eventId\":\"e763522e-4197-4cbf-bdda-3bbc547a9fa6\",\"eventType\":\"commerce.purchases\",\"commerce\":{\"productListAdds\":{\"value\":21},\"purchases\":{\"value\":1},\"order\":{\"priceTotal\":261.5199890136719,\"currencyCode\":\"RON\"}},\"timestamp\":\"2019-12-03T13:43:31-07:00\"},\"data\":{\"listExample\":[\"val1\",\"val2\"],\"test\":\"request\",\"customText\":\"Hello World\"},\"meta\":{\"personalization\":{\"sessionId\":\"personalSessionId\"}},\"query\":{\"personalization\":{\"prefetch\":{\"mboxes\":[\"Hollister\",\"SantaCruz\"]},\"execute\":{\"pageLoad\":false,\"mboxes\":[\"geoColors\"]}}}}]}";
		KonductorConfig konductorConfig = KonductorConfig.fromJsonRequest(jsonString);
		assertTrue(konductorConfig.isStreamingEnabled());
		assertEquals("\n", konductorConfig.getLineFeed());
		assertEquals("\u0000", konductorConfig.getRecordSeparator());
	}

	@Test
	public void testKonductorConfig_fromJsonRequestInvalidMissingStreaming() {
		String jsonString =
			"{\"xdm\":{\"environment\":{\"operatingSystemVersion\":\"9\",\"carrier\":\"Android\",\"operatingSystem\":\"Android 9\"},\"identityMap\":{\"ECID\":[{\"id\":\"17166732221897949268328316449120339327\"}]},\"device\":{\"screenWidth\":\"1440\",\"screenOrientation\":\"portrait\",\"screenHeight\":\"2392\",\"model\":\"Android SDK built for x86\",\"modelNumber\":\"PSR1.180720.075\",\"type\":\"phone\",\"manufacturer\":\"Google\"}},\"meta\":{\"konductorConfig\":{\"imsOrgId\":\"6C83C8A154888E2A0A4C98BC@AdobeOrg\"}},\"events\":[{\"xdm\":{\"eventId\":\"e763522e-4197-4cbf-bdda-3bbc547a9fa6\",\"eventType\":\"commerce.purchases\",\"commerce\":{\"productListAdds\":{\"value\":21},\"purchases\":{\"value\":1},\"order\":{\"priceTotal\":261.5199890136719,\"currencyCode\":\"RON\"}},\"timestamp\":\"2019-12-03T13:43:31-07:00\"},\"data\":{\"listExample\":[\"val1\",\"val2\"],\"test\":\"request\",\"customText\":\"Hello World\"},\"meta\":{\"personalization\":{\"sessionId\":\"personalSessionId\"}},\"query\":{\"personalization\":{\"prefetch\":{\"mboxes\":[\"Hollister\",\"SantaCruz\"]},\"execute\":{\"pageLoad\":false,\"mboxes\":[\"geoColors\"]}}}}]}";
		KonductorConfig konductorConfig = KonductorConfig.fromJsonRequest(jsonString);
		assertNull(konductorConfig);
	}
}
