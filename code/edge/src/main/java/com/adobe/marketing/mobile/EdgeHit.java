/*
  Copyright 2021 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import java.util.UUID;
import org.json.JSONObject;

/**
 * Class defining hits to Experience Edge service
 */
class EdgeHit {

	private final String configId;
	private final String requestId;
	private final JSONObject payload;
	private final EdgeNetworkService.RequestType requestType;
	private final EdgeEndpoint edgeEndpoint;

	/**
	 * Creates an {@link EdgeHit} instance with the provided configId and payload, and generates an unique identifier
	 * to be used as {@code requestId}.
	 *  @param configId the Edge configuration identifier (obtained from Configuration); should not be null
	 * @param payload the network request payload
	 * @param requestType the hit {@link EdgeNetworkService.RequestType}; if not provided, by default it is {@code RequestType.INTERACT}
	 * @param edgeEndpoint the endpoint URL for this hit
	 */
	EdgeHit(
		final String configId,
		final JSONObject payload,
		final EdgeNetworkService.RequestType requestType,
		final EdgeEndpoint edgeEndpoint
	) {
		this.configId = configId;
		this.payload = payload;
		this.requestType = requestType != null ? requestType : EdgeNetworkService.RequestType.INTERACT;
		this.edgeEndpoint = edgeEndpoint;
		this.requestId = UUID.randomUUID().toString();
	}

	/**
	 * The Edge configuration identifier (obtained from Configuration)
	 * @return the configId for this hit
	 */
	String getConfigId() {
		return configId;
	}

	/**
	 * Unique identifier to identify current network request that is being sent to Experience Edge Network;
	 * It is used when all the responses are returned from the server and the events need to be removed from
	 * {@code sentEventsWaitingResponse} and also as requestId in the request.
	 *
	 * @return the unique request identifier for this hit
	 */
	String getRequestId() {
		return requestId;
	}

	/**
	 * @return the {@link EdgeNetworkService.RequestType} to be used for this {@link EdgeHit}
	 */
	EdgeNetworkService.RequestType getType() {
		return requestType;
	}

	/**
	 * Get the {@link EdgeEndpoint} passed to this {@code EdgeHit}s constructor.
	 * @return the {@link EdgeEndpoint} for this {@code EdgeHit}
	 */
	EdgeEndpoint getEdgeEndpoint() {
		return edgeEndpoint;
	}

	/**
	 * The network request payload for this {@link EdgeHit}
	 *
	 * @return the payload as {@link JSONObject}
	 */
	JSONObject getPayload() {
		return payload;
	}
}
