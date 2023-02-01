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

package com.adobe.marketing.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EdgeEndpointTests {

	@Test
	public void testEnvironmentProdCreatesEdgeEndpointProd() {
		final String expected = "https://edge.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("prod", null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentProdAndLocationHintCreatesEdgeEndpointProd() {
		final String expected = "https://edge.adobedc.net/ee/lh1/v1";
		assertEquals(expected, (new EdgeEndpoint("prod", null, "lh1")).getEndpoint());
	}

	@Test
	public void testEnvironmentProdCapitalizedCreatesEdgeEndpointProd() {
		final String expected = "https://edge.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("PROD", null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentProdAndCustomDomainCreatesEdgeEndpointProd() {
		final String expected = "https://my.awesome.site/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("prod", "my.awesome.site", null)).getEndpoint());
	}

	@Test
	public void testEnvironmentProdAndCustomDomainAndLocationHintCreatesEdgeEndpointProd() {
		final String expected = "https://my.awesome.site/ee/lh1/v1";
		assertEquals(expected, (new EdgeEndpoint("prod", "my.awesome.site", "lh1")).getEndpoint());
	}

	@Test
	public void testEnvironmentPreProdCreatesEdgeEndpointPreProd() {
		final String expected = "https://edge.adobedc.net/ee-pre-prd/v1";
		assertEquals(expected, (new EdgeEndpoint("pre-prod", null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentPreProdAndLocationHintCreatesEdgeEndpointPreProd() {
		final String expected = "https://edge.adobedc.net/ee-pre-prd/lh1/v1";
		assertEquals(expected, (new EdgeEndpoint("pre-prod", null, "lh1")).getEndpoint());
	}

	@Test
	public void testEnvironmentPreProdCapitalizedCreatesEdgeEndpointPreProd() {
		final String expected = "https://edge.adobedc.net/ee-pre-prd/v1";
		assertEquals(expected, (new EdgeEndpoint("PRE-PROD", null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentPreProdAndCustomDomainCreatesEdgeEndpointPreProd() {
		final String expected = "https://my.awesome.site/ee-pre-prd/v1";
		assertEquals(expected, (new EdgeEndpoint("pre-prod", "my.awesome.site", null)).getEndpoint());
	}

	@Test
	public void testEnvironmentPreProdAndCustomDomainAndLocationHintCreatesEdgeEndpointPreProd() {
		final String expected = "https://my.awesome.site/ee-pre-prd/lh1/v1";
		assertEquals(expected, (new EdgeEndpoint("pre-prod", "my.awesome.site", "lh1")).getEndpoint());
	}

	@Test
	public void testEnvironmentIntCreatesEdgeEndpointInt() {
		final String expected = "https://edge-int.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("int", null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentIntCreatesAndLocationHintEdgeEndpointInt() {
		final String expected = "https://edge-int.adobedc.net/ee/lh1/v1";
		assertEquals(expected, (new EdgeEndpoint("int", null, "lh1")).getEndpoint());
	}

	@Test
	public void testEnvironmentIntCapitalizedCreatesEdgeEndpointInt() {
		final String expected = "https://edge-int.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("INT", null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentIntAndCustomDomainCreatesEdgeEndpointInt() {
		// Integration endpoint does not support custom domains
		final String expected = "https://edge-int.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("int", "my.awesome.site", null)).getEndpoint());
	}

	@Test
	public void testEnvironmentIntAndCustomDomainAndLocationHintCreatesEdgeEndpointInt() {
		// Integration endpoint does not support custom domains
		final String expected = "https://edge-int.adobedc.net/ee/lh1/v1";
		assertEquals(expected, (new EdgeEndpoint("int", "my.awesome.site", "lh1")).getEndpoint());
	}

	@Test
	public void testEnvironmentNullCreatesEdgeEndpointProd() {
		final String expected = "https://edge.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint((String) null, null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentEmptyCreatesEdgeEndpointProd() {
		final String expected = "https://edge.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("", null, null)).getEndpoint());
	}

	@Test
	public void testEnvironmentInvalidCreatesEdgeEndpointProd() {
		final String expected = "https://edge.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("unknown", null, null)).getEndpoint());
	}

	@Test
	public void testDefaultDomainIsUsedIfEmpty() {
		final String expected = "https://edge.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("prod", "", null)).getEndpoint());
	}

	@Test
	public void testEmptyLocationHintIsNotUsed() {
		final String expected = "https://edge.adobedc.net/ee/v1";
		assertEquals(expected, (new EdgeEndpoint("prod", null, "")).getEndpoint());
	}
}
