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

import com.adobe.marketing.mobile.util.StringUtils;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

class EdgeEndpoint {

	/**
	 * Represents all the known Edge Network environment types.
	 * <ul>
	 *     <li>PROD - the Edge Network production environment</li>
	 *     <li>PRE_PROD - the Edge Network pre-production environment</li>
	 *     <li>INT - the Edge Network integration environment</li>
	 * </ul>
	 */
	enum EdgeEnvironmentType {
		PROD("prod"),
		PRE_PROD("pre-prod"),
		INT("int");

		private final String typeName;

		private static final Map<String, EdgeEnvironmentType> lookup = new HashMap<>();

		static {
			for (EdgeEnvironmentType type : EdgeEnvironmentType.values()) {
				lookup.put(type.typeName, type);
			}
		}

		EdgeEnvironmentType(final String typeName) {
			this.typeName = typeName;
		}

		static EdgeEnvironmentType get(final String typeName) {
			if (typeName == null) {
				return PROD;
			}

			EdgeEnvironmentType type = lookup.get(typeName.toLowerCase(Locale.ROOT));
			return type == null ? PROD : type;
		}
	}

	private final String endpoint;

	/**
	 * Construct a new {@code EdgeEndpoint} based on the Edge request environment and custom domain.
	 * If the given {@code environment} is not a known value, the {@code EdgeEnvironmentType#PROD} is used.
	 * If the given {@code domain} is null, then the default Edge domain is used.
	 * @param requestType the {@link EdgeNetworkService.RequestType} used to determine the Experience Edge endpoint
	 * @param environment the Edge request environment
	 * @param domain the custom Edge domain, or null if using the default domain
	 * @param locationHint an optional location hint for the {@code EdgeEndpoint} which hints at the
	 *                        Edge Network cluster to send requests.
	 */
	EdgeEndpoint(
		final EdgeNetworkService.RequestType requestType,
		final String environment,
		final String domain,
		final String path,
		final String locationHint
	) {
		final StringBuilder endpointBuilder = new StringBuilder();
		final EdgeEnvironmentType type = EdgeEnvironmentType.get(environment);
		final String edgeDomain = !StringUtils.isNullOrEmpty(domain)
			? domain
			: EdgeConstants.NetworkKeys.DEFAULT_DOMAIN;
		final String edgeLocationHint = !StringUtils.isNullOrEmpty(locationHint) ? "/" + locationHint : "";

		switch (type) {
			case PRE_PROD:
				endpointBuilder
					.append(EdgeConstants.NetworkKeys.SCHEME_HTTPS)
					.append(edgeDomain)
					.append(EdgeConstants.NetworkKeys.REQUEST_URL_PRE_PROD_PATH);
				break;
			case INT:
				// Integration endpoint does not support custom domains
				endpointBuilder
					.append(EdgeConstants.NetworkKeys.SCHEME_HTTPS)
					.append(EdgeConstants.NetworkKeys.REQUEST_DOMAIN_INT)
					.append(EdgeConstants.NetworkKeys.REQUEST_URL_PROD_PATH);
				break;
			default:
				endpointBuilder
					.append(EdgeConstants.NetworkKeys.SCHEME_HTTPS)
					.append(edgeDomain)
					.append(EdgeConstants.NetworkKeys.REQUEST_URL_PROD_PATH);
		}

		// Append locationHint
		endpointBuilder.append(edgeLocationHint);

		if (!StringUtils.isNullOrEmpty(path)) {
			// path should contain the leading "/"
			endpointBuilder.append(path);
		} else {
			endpointBuilder.append(EdgeConstants.NetworkKeys.REQUEST_URL_VERSION);
			endpointBuilder.append("/").append(requestType.type);
		}

		endpoint = endpointBuilder.toString();
	}

	/**
	 * Get the Edge endpoint URL.
	 * @return the endpoint URL
	 */
	String getEndpoint() {
		return endpoint;
	}
}
