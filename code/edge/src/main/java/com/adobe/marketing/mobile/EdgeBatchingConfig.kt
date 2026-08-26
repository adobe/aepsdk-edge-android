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

package com.adobe.marketing.mobile

import com.adobe.marketing.mobile.util.DataReader

/**
 * Parsed, immutable view of the Edge batching configuration object (`edge.batching`).
 *
 * The same grouped format is used whether the object arrives via Configuration shared state
 * (remote/Launch) or the bundled asset file, so this parser is the single source of truth for both:
 *
 * ```
 * {
 *   "_meta": { ... },            // ignored
 *   "enabled": true,             // master switch
 *   "maxBatchSize": 10,          // clamped to MAX_BATCH_SIZE_LIMIT
 *   "wildcards": [ { "xdmEventType": "media.*", "enabled": true } ],
 *   "<anyExtensionName>": [ { "xdmEventType": "media.play", "enabled": true } ]
 * }
 * ```
 *
 * Reserved top-level keys are `_meta`, `enabled`, `maxBatchSize` and `wildcards`; every other
 * top-level key is treated as an *extension group*: an array of event objects. Extension grouping is
 * cosmetic — all groups are flattened (OR-deduped) into a single allow-list of enabled `xdmEventType`
 * values. An event is batchable strictly by whitelist: only if its `xdm.eventType` matches an enabled
 * exact entry or an enabled wildcard.
 */
class EdgeBatchingConfig private constructor(
    val isEnabled: Boolean,
    val maxBatchSize: Int,
    private val enabledEventTypes: Set<String>,
    private val enabledWildcards: Set<String>
) {

    /**
     * Strict allow-list check: whether an outgoing event's `xdm.eventType` is batchable. An event with
     * no `xdm.eventType`, or one matched only by disabled entries, is not batchable.
     *
     * @param xdmEventType the `xdm.eventType` of the outgoing Experience Event (may be null)
     * @return true if the event type is whitelisted (exact or wildcard) for batching
     */
    fun isEventTypeBatchable(xdmEventType: String?): Boolean {
        if (xdmEventType.isNullOrEmpty()) {
            return false
        }
        if (enabledEventTypes.contains(xdmEventType)) {
            return true
        }
        return enabledWildcards.any { matchesWildcard(it, xdmEventType) }
    }

    companion object {
        /** Disabled, empty config used when no batching object is present. */
        private val DISABLED = EdgeBatchingConfig(false, EdgeConstants.Defaults.MAX_BATCH_SIZE, emptySet(), emptySet())

        /**
         * Parses the `edge.batching` object out of the provided (already-resolved) Edge configuration
         * map. Returns a disabled, empty config when the object is missing or not a map.
         *
         * @param edgeConfiguration the Edge configuration map snapshotted on an entity (see
         *     `EventUtils.getEdgeConfiguration`)
         * @return an immutable parsed view; never null
         */
        @JvmStatic
        fun from(edgeConfiguration: Map<String, Any?>?): EdgeBatchingConfig {
            val batching = DataReader.optTypedMap(
                Any::class.java,
                edgeConfiguration,
                EdgeConstants.SharedState.Configuration.EDGE_BATCHING,
                null
            ) ?: return DISABLED

            val enabled = DataReader.optBoolean(batching, EdgeConstants.Batching.ENABLED, false)

            var maxBatchSize = DataReader.optInt(
                batching,
                EdgeConstants.Batching.MAX_BATCH_SIZE,
                EdgeConstants.Defaults.MAX_BATCH_SIZE
            )
            if (maxBatchSize <= 0) {
                maxBatchSize = EdgeConstants.Defaults.MAX_BATCH_SIZE
            }
            maxBatchSize = minOf(maxBatchSize, EdgeConstants.Defaults.MAX_BATCH_SIZE_LIMIT)

            val exactTypes = mutableSetOf<String>()
            val wildcards = mutableSetOf<String>()

            // Wildcards: dedicated reserved array, parsed as patterns.
            collectEnabled(batching, EdgeConstants.Batching.WILDCARDS, wildcards)

            // Every other top-level key (i.e. not a reserved key) is an extension group of exact entries.
            for (key in batching.keys) {
                if (isReservedKey(key)) {
                    continue
                }
                collectEnabled(batching, key, exactTypes)
            }

            return EdgeBatchingConfig(enabled, maxBatchSize, exactTypes, wildcards)
        }

        /**
         * Reads the array under [key] as event objects and adds the `xdmEventType` of each enabled
         * entry to [target]. Malformed / missing arrays are ignored.
         */
        private fun collectEnabled(batching: Map<String, Any?>, key: String, target: MutableSet<String>) {
            val entries = DataReader.optTypedListOfMap(Any::class.java, batching, key, null) ?: return
            for (entry in entries) {
                if (entry == null || !DataReader.optBoolean(entry, EdgeConstants.Batching.ENABLED, false)) {
                    continue
                }
                val type = DataReader.optString(entry, EdgeConstants.Batching.XDM_EVENT_TYPE, null)
                if (!type.isNullOrEmpty()) {
                    target.add(type)
                }
            }
        }

        private fun isReservedKey(key: String): Boolean =
            key == EdgeConstants.Batching.META ||
                key == EdgeConstants.Batching.ENABLED ||
                key == EdgeConstants.Batching.MAX_BATCH_SIZE ||
                key == EdgeConstants.Batching.WILDCARDS

        /**
         * Case-sensitive wildcard match. Supports a single trailing `*` (prefix match), a single leading
         * `*` (suffix match), and a bare `*` (match all). Any other pattern (including an infix `*` or no
         * `*`) is compared for exact equality.
         */
        private fun matchesWildcard(pattern: String, value: String): Boolean {
            if (pattern == "*") {
                return true
            }
            val leadingStar = pattern.startsWith("*")
            val trailingStar = pattern.endsWith("*")
            return when {
                trailingStar && !leadingStar -> value.startsWith(pattern.substring(0, pattern.length - 1))
                leadingStar && !trailingStar -> value.endsWith(pattern.substring(1))
                else -> pattern == value
            }
        }
    }
}
