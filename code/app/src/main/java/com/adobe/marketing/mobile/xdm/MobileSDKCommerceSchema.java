/*
  Copyright 2020 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.xdm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class {@code MobileSDKCommerceSchema}
 *
 * <p/>
 * XDM Schema Java Object Generated 2020-10-01 15:22:47.692811 -0700 PDT m=+1.794683589 by XDMTool
 *
 * Title		:	Mobile SDK Commerce Schema
 * Version		:	1.1
 * Type			:	schemas
 */
public class MobileSDKCommerceSchema implements com.adobe.marketing.mobile.xdm.Schema {

	private Commerce commerce;
	private String eventType;
	private List<ProductListItemsItem> productListItems;
	private java.util.Date timestamp;

	public MobileSDKCommerceSchema() {}

	/**
	 * Returns the version number of this schema.
	 *
	 * @return the schema version number
	 */
	@Override
	public String getSchemaVersion() {
		return "1.1";
	}

	/**
	 * Returns the unique schema identifier.
	 *
	 * @return the schema ID
	 */
	@Override
	public String getSchemaIdentifier() {
		return "";
	}

	/**
	 * Returns the unique dataset identifier.
	 *
	 * @return the dataset ID
	 */
	@Override
	public String getDatasetIdentifier() {
		return "";
	}

	@Override
	public Map<String, Object> serializeToXdm() {
		Map<String, Object> map = new HashMap<>();

		if (this.commerce != null) {
			map.put("commerce", this.commerce.serializeToXdm());
		}

		if (this.eventType != null) {
			map.put("eventType", this.eventType);
		}

		if (this.productListItems != null) {
			map.put(
				"productListItems",
				com.adobe.marketing.mobile.xdm.Formatters.serializeFromList(this.productListItems)
			);
		}

		if (this.timestamp != null) {
			map.put("timestamp", com.adobe.marketing.mobile.xdm.Formatters.dateToISO8601String(this.timestamp));
		}

		return map;
	}

	/**
	 * Returns the Commerce property
	 * Commerce specific data related to this event.
	 * @return {@link Commerce} value or null if the property is not set
	 */
	public Commerce getCommerce() {
		return this.commerce;
	}

	/**
	 * Sets the Commerce property
	 * Commerce specific data related to this event.
	 * @param newValue the new Commerce value
	 */
	public void setCommerce(final Commerce newValue) {
		this.commerce = newValue;
	}

	/**
	 * Returns the Event Type property
	 * The primary event type for this time-series record.
	 * @return {@link String} value or null if the property is not set
	 */
	public String getEventType() {
		return this.eventType;
	}

	/**
	 * Sets the Event Type property
	 * The primary event type for this time-series record.
	 * @param newValue the new Event Type value
	 */
	public void setEventType(final String newValue) {
		this.eventType = newValue;
	}

	/**
	 * Returns the Product list items property
	 * A list of items representing a product selected by a customer with specific options and pricing that are for that usage context at a specific point of time and may differ from the product record.
	 * @return list of {@link ProductListItemsItem} values or null if the list is not set
	 */
	public List<ProductListItemsItem> getProductListItems() {
		return this.productListItems;
	}

	/**
	 * Sets the Product list items property
	 * A list of items representing a product selected by a customer with specific options and pricing that are for that usage context at a specific point of time and may differ from the product record.
	 * @param newValue the new Product list items value
	 */
	public void setProductListItems(final List<ProductListItemsItem> newValue) {
		this.productListItems = newValue;
	}

	/**
	 * Returns the Timestamp property
	 * The time when an event or observation occurred.
	 * @return {@link java.util.Date} value or null if the property is not set
	 */
	public java.util.Date getTimestamp() {
		return this.timestamp;
	}

	/**
	 * Sets the Timestamp property
	 * The time when an event or observation occurred.
	 * @param newValue the new Timestamp value
	 */
	public void setTimestamp(final java.util.Date newValue) {
		this.timestamp = newValue;
	}
}
