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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public final class Formatters {

	/**
	 * Serialize a list of {@code Property} elements to a list of XDM formatted maps.
	 * Calls {@link Property#serializeToXdm()} on each element in the list.
	 *
	 * @param listProperty list of {@link Property} elements
	 * @return a list of {@link Property} elements serialized to XDM map structure
	 */
	public static List<Map<String, Object>> serializeFromList(final List<? extends Property> listProperty) {
		List<Map<String, Object>> serializedList = new ArrayList<>();

		if (listProperty == null) {
			return serializedList;
		}

		for (Property property : listProperty) {
			if (property != null) {
				serializedList.add(property.serializeToXdm());
			}
		}

		return serializedList;
	}

	// example: 2017-09-26T15:52:25-07:00
	private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	private static final String DATE_FORMAT = "yyyy-MM-dd";

	/**
	 * Formats a {@code Date} to an ISO 8601 date-time string in UTC as defined in
	 * <a href="https://tools.ietf.org/html/rfc3339#section-5.6">RFC 3339, section 5.6</a>
	 * For example, 2017-09-26T15:52:25Z
	 *
	 * @param timestamp a timestamp
	 * @return {@code timestamp} formatted to a string in the format of 'yyyy-MM-dd'T'HH:mm:ss'Z'',
	 * or an empty string if {@code timestamp} is null
	 *
	 * @deprecated as of 2.0.0, replaced by {@code TimeUtils.getISO8601UTCDateWithMilliseconds} from Mobile Core
	 */
	@Deprecated
	public static String dateToISO8601String(final Date timestamp) {
		if (timestamp == null) {
			return "";
		}

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(TIMESTAMP_FORMAT, Locale.US);
		simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
		return simpleDateFormat.format(timestamp);
	}

	/**
	 * Formats a {@code Date} to a full-date string defined in
	 * <a href="https://tools.ietf.org/html/rfc3339#section-5.6">RFC 3339, section 5.6</a>,
	 * representing a date without time.
	 * For example, 2017-09-26
	 *
	 * @param date a date
	 * @return {@code date} formatted to a string in the format of 'yyy-MM-dd', or an empty string
	 * if {@code date} is null
	 *
	 * @deprecated as of 2.0.0, replaced by {@code TimeUtils.getISO8601FullDate} from Mobile Core
	 */
	@Deprecated
	public static String dateToShortDateString(final Date date) {
		if (date == null) {
			return "";
		}

		SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT, Locale.US);
		return simpleDateFormat.format(date);
	}

	private Formatters() {}
}
