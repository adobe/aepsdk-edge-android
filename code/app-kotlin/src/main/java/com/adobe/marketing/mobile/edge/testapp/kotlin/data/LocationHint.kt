/*
  Copyright 2023 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.edge.testapp.kotlin.data

// List of Location Hint values used in drop-down spinner
enum class LocationHint(private val value: String?) {
    OR2("or2"), VA6("va6"), IRL1("irl1"), IND1("ind1"), JPN3("jpn3"), SGP3("sgp3"), AUS3("aus3"),
    NULL(null), EMPTY(""), INVALID("invalid");

    companion object {
        private val lookup: MutableMap<String?, LocationHint> = HashMap()
        fun value(forLocationHint: LocationHint) = forLocationHint.value

        init {
            for (type in LocationHint.values()) {
                lookup[type.value] = type
            }
        }

        /**
         * Get `LocationHint` instance from String name.
         * @param name the name of a `LocationHint`
         * @return `LocationHint` for the given name, or `LocationHint#NULL`
         * if given name doesn't match a valid `LocationHint`
         */
        fun fromString(name: String?): LocationHint {
            if (name == null) {
                return NULL
            }
            val hint = lookup[name]
            return hint ?: NULL
        }

        /**
         * Get `LocationHint` instance from ordinal position.
         * @param ordinal the position of the `LocationHint` as it appears in the definition.
         * @return `LocationHint` for the given ordinal, or `LocationHint#NULL`
         * if given ordinal is out of range.
         */
        fun fromOrdinal(ordinal: Int): LocationHint {
            val values: Array<LocationHint> = LocationHint.values()
            return if (ordinal < 0 || ordinal > values.size) {
                NULL
            } else values[ordinal]
        }
    }
}
