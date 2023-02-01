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

package com.adobe.marketing.mobile.xdm;

import java.util.Map;

/**
 * Interface representing an XDM Event Data schema.
 */
public interface Schema {
	/**
	 * Returns the version of this schema as defined in the Adobe Experience Platform.
	 * @return the version of this schema
	 */
	String getSchemaVersion();

	/**
	 * Returns the identifier for this schema as defined in the Adobe Experience Platform.
	 * The identifier is a URI where this schema is defined.
	 * @return the URI identifier for this schema
	 */
	String getSchemaIdentifier();

	/**
	 * Returns the identifier for this dataset as defined in the Adobe Experience Platform.
	 * @return the dataset ID
	 */
	String getDatasetIdentifier();

	/**
	 * Serialize this {@code Schema} object to a map equivalent of its XDM schema.
	 * @return XDM formatted map of this {@code Schema} object
	 */
	Map<String, Object> serializeToXdm();
}
