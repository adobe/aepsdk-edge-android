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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EdgeBatchingConfig] — parsing of the grouped `edge.batching` object and the
 * strict `xdm.eventType` allow-list matcher (exact + wildcard).
 */
class EdgeBatchingConfigTest {

    private val edgeBatchingKey = EdgeConstants.SharedState.Configuration.EDGE_BATCHING

    /** Wraps a batching object in the enclosing edge-configuration map and parses it. */
    private fun parse(batching: Map<String, Any?>): EdgeBatchingConfig =
        EdgeBatchingConfig.from(mapOf(edgeBatchingKey to batching))

    /** One `{xdmEventType, enabled}` entry. */
    private fun entry(type: String, enabled: Boolean): Map<String, Any?> =
        mapOf("xdmEventType" to type, "enabled" to enabled)

    // -------------------------------------------------------------------------
    // Missing / malformed object → DISABLED
    // -------------------------------------------------------------------------

    @Test
    fun `from null configuration is disabled`() {
        val config = EdgeBatchingConfig.from(null)
        assertFalse(config.isEnabled)
        assertEquals(EdgeConstants.Defaults.MAX_BATCH_SIZE, config.maxBatchSize)
        assertFalse(config.isEventTypeBatchable("media.play"))
    }

    @Test
    fun `configuration without edge_batching key is disabled`() {
        val config = EdgeBatchingConfig.from(mapOf("edge.configId" to "abc"))
        assertFalse(config.isEnabled)
        assertFalse(config.isEventTypeBatchable("media.play"))
    }

    @Test
    fun `edge_batching value that is not a map is disabled`() {
        val config = EdgeBatchingConfig.from(mapOf(edgeBatchingKey to "not-a-map"))
        assertFalse(config.isEnabled)
        assertFalse(config.isEventTypeBatchable("media.play"))
    }

    // -------------------------------------------------------------------------
    // enabled flag
    // -------------------------------------------------------------------------

    @Test
    fun `enabled true is parsed`() {
        assertTrue(parse(mapOf("enabled" to true)).isEnabled)
    }

    @Test
    fun `enabled false and absent are both disabled`() {
        assertFalse(parse(mapOf("enabled" to false)).isEnabled)
        assertFalse(parse(mapOf("maxBatchSize" to 5)).isEnabled)
    }

    // -------------------------------------------------------------------------
    // maxBatchSize: default / clamp
    // -------------------------------------------------------------------------

    @Test
    fun `maxBatchSize absent uses default`() {
        assertEquals(EdgeConstants.Defaults.MAX_BATCH_SIZE, parse(mapOf("enabled" to true)).maxBatchSize)
    }

    @Test
    fun `maxBatchSize valid value passes through`() {
        assertEquals(5, parse(mapOf("enabled" to true, "maxBatchSize" to 5)).maxBatchSize)
    }

    @Test
    fun `maxBatchSize non-positive falls back to default`() {
        assertEquals(EdgeConstants.Defaults.MAX_BATCH_SIZE, parse(mapOf("maxBatchSize" to 0)).maxBatchSize)
        assertEquals(EdgeConstants.Defaults.MAX_BATCH_SIZE, parse(mapOf("maxBatchSize" to -3)).maxBatchSize)
    }

    @Test
    fun `maxBatchSize above limit is clamped`() {
        assertEquals(EdgeConstants.Defaults.MAX_BATCH_SIZE_LIMIT, parse(mapOf("maxBatchSize" to 50)).maxBatchSize)
    }

    // -------------------------------------------------------------------------
    // Exact matching
    // -------------------------------------------------------------------------

    @Test
    fun `enabled exact entry matches only its type`() {
        val config = parse(mapOf("edgeMedia" to listOf(entry("media.play", true))))
        assertTrue(config.isEventTypeBatchable("media.play"))
        assertFalse(config.isEventTypeBatchable("media.pause"))
    }

    @Test
    fun `disabled exact entry is not batchable`() {
        val config = parse(mapOf("edgeMedia" to listOf(entry("media.play", false))))
        assertFalse(config.isEventTypeBatchable("media.play"))
    }

    @Test
    fun `entry with missing or empty xdmEventType is ignored`() {
        val config = parse(
            mapOf(
                "g" to listOf(
                    mapOf("enabled" to true), // no xdmEventType
                    mapOf("xdmEventType" to "", "enabled" to true) // empty xdmEventType
                )
            )
        )
        assertFalse(config.isEventTypeBatchable(""))
        assertFalse(config.isEventTypeBatchable("anything"))
    }

    @Test
    fun `exact matching is case sensitive`() {
        val config = parse(mapOf("g" to listOf(entry("media.play", true))))
        assertFalse(config.isEventTypeBatchable("Media.Play"))
    }

    // -------------------------------------------------------------------------
    // OR-dedup across extension groups
    // -------------------------------------------------------------------------

    @Test
    fun `same type across groups is batchable if any entry is enabled`() {
        val config = parse(
            mapOf(
                "optimize" to listOf(entry("decisioning.propositionFetch", false)),
                "messaging" to listOf(entry("decisioning.propositionFetch", true))
            )
        )
        assertTrue(config.isEventTypeBatchable("decisioning.propositionFetch"))
    }

    @Test
    fun `type only in disabled entries across groups is not batchable`() {
        val config = parse(
            mapOf(
                "optimize" to listOf(entry("decisioning.propositionFetch", false)),
                "messaging" to listOf(entry("decisioning.propositionFetch", false))
            )
        )
        assertFalse(config.isEventTypeBatchable("decisioning.propositionFetch"))
    }

    // -------------------------------------------------------------------------
    // Reserved keys are not treated as extension groups
    // -------------------------------------------------------------------------

    @Test
    fun `reserved keys are not parsed as extension groups`() {
        // _meta / enabled / maxBatchSize are reserved; only the real group "g" contributes.
        val config = parse(
            mapOf(
                "_meta" to mapOf("schemaVersion" to 1),
                "enabled" to true,
                "maxBatchSize" to 7,
                "g" to listOf(entry("analytics.track", true))
            )
        )
        assertTrue(config.isEnabled)
        assertEquals(7, config.maxBatchSize)
        assertTrue(config.isEventTypeBatchable("analytics.track"))
    }

    // -------------------------------------------------------------------------
    // Wildcards
    // -------------------------------------------------------------------------

    @Test
    fun `trailing star is a prefix match`() {
        val config = parse(mapOf("wildcards" to listOf(entry("media.*", true))))
        assertTrue(config.isEventTypeBatchable("media.play"))
        assertTrue(config.isEventTypeBatchable("media.sessionStart"))
        assertFalse(config.isEventTypeBatchable("audio.play"))
        assertFalse(config.isEventTypeBatchable("media")) // no trailing dot segment
    }

    @Test
    fun `leading star is a suffix match`() {
        val config = parse(mapOf("wildcards" to listOf(entry("*.propositionFetch", true))))
        assertTrue(config.isEventTypeBatchable("decisioning.propositionFetch"))
        assertFalse(config.isEventTypeBatchable("decisioning.propositionInteract"))
    }

    @Test
    fun `bare star matches any non-empty type`() {
        val config = parse(mapOf("wildcards" to listOf(entry("*", true))))
        assertTrue(config.isEventTypeBatchable("anything.at.all"))
        assertTrue(config.isEventTypeBatchable("x"))
    }

    @Test
    fun `disabled wildcard is ignored`() {
        val config = parse(mapOf("wildcards" to listOf(entry("media.*", false))))
        assertFalse(config.isEventTypeBatchable("media.play"))
    }

    @Test
    fun `infix star degrades to exact match`() {
        val config = parse(mapOf("wildcards" to listOf(entry("a*b", true))))
        assertTrue(config.isEventTypeBatchable("a*b")) // literal only
        assertFalse(config.isEventTypeBatchable("axb"))
    }

    // -------------------------------------------------------------------------
    // Null / empty event type
    // -------------------------------------------------------------------------

    @Test
    fun `null or empty xdmEventType is never batchable even with bare star`() {
        val config = parse(mapOf("wildcards" to listOf(entry("*", true))))
        assertFalse(config.isEventTypeBatchable(null))
        assertFalse(config.isEventTypeBatchable(""))
    }

    // -------------------------------------------------------------------------
    // Malformed group value
    // -------------------------------------------------------------------------

    @Test
    fun `non-list group value is ignored without affecting valid groups`() {
        val config = parse(
            mapOf(
                "bogus" to "not-a-list",
                "g" to listOf(entry("analytics.track", true))
            )
        )
        assertTrue(config.isEventTypeBatchable("analytics.track"))
        assertFalse(config.isEventTypeBatchable("bogus"))
    }
}
