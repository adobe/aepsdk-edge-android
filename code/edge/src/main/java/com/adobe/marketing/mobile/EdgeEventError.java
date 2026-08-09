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

package com.adobe.marketing.mobile;

/**
 * Represents a per-event error returned by the Adobe Experience Edge Network for a batched
 * {@link ExperienceEvent}. Delivered via {@link EdgeCallbackWithError#onError}.
 */
public class EdgeEventError {

	private final String type;
	private final int status;
	private final String title;
	private final String detail;

	EdgeEventError(final String type, final int status, final String title, final String detail) {
		this.type = type;
		this.status = status;
		this.title = title;
		this.detail = detail;
	}

	/** @return the error type URI (e.g. {@code "https://ns.adobe.com/aep/errors/EXEG-0203-502"}) */
	public String getType() {
		return type;
	}

	/** @return the HTTP-like status code embedded in the error */
	public int getStatus() {
		return status;
	}

	/** @return short human-readable error title */
	public String getTitle() {
		return title;
	}

	/** @return detailed error description, or null if not present */
	public String getDetail() {
		return detail;
	}

	@Override
	public String toString() {
		return "EdgeEventError{type='" + type + "', status=" + status + ", title='" + title + "'}";
	}
}
