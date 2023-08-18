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
package com.adobe.marketing.mobile.util

import com.adobe.marketing.mobile.util.JSONAsserts.assertEqual
import com.adobe.marketing.mobile.util.JSONAsserts.assertExactMatch
import com.adobe.marketing.mobile.util.JSONAsserts.assertTypeMatch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
class JSONAssertsTests {
    // Value matching validation
    /**
     * Validates `null` equated to itself is true
     */
    @Test
    fun `should match when both values are null`() {
        val expected = null
        val actual = null

        assertEqual(expected, actual)
    }

    // Alternate path tests - assertEqual does not handle alternate paths and is not tested here

    /**
     * Validates alternate path wildcards function independently of order.
     *
     * Consequence: Tests can rely on unique sets of wildcard index values without the need to test
     * every variation.
     */
    @Test
    fun `should validate alternate path wildcard order independence`() {
        val expectedJSONString = """
        [1, 2]
        """.trimIndent()

        val actualJSONString = """
        ["a", "b", 1, 2]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*0]", "[*1]"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*1]", "[*0]"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*0]", "[*1]"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*1]", "[*0]"))
    }



    /**
     * Validates the behavior of general and specific index wildcards:
     * 1. Both are compatible and can mark an index for wildcard matching.
     * 2. The general wildcard acts as a superset of any specific index wildcard.
     *
     * Consequence: Tests that require wildcard matching for all expected indexes
     * can use the general wildcard alone.
     */
    @Test
    fun `should verify general wildcard as superset of specific index wildcards`() {
        val expectedJSONString = """
        [1, 2]
        """.trimIndent()

        val actualJSONString = """
        ["a", "b", 1, 2]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*0]", "[*1]"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*]"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*0]", "[*1]"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*]"))
    }


    /**
     * Validates that wildcard character can be placed in front or behind the index.
     *
     * Consequence: Tests can use a standard format for asterisk placement, without
     * having to test all variations.
     */
    @Test
    fun `should validate wildcard placement before and after index`() {
        val expectedJSONString = """
        [1]
        """.trimIndent()

        val actualJSONString = """
        ["a", 1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*0]"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[0*]"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*0]"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0*]"))
    }

    // Array tests
    /**
     * Validates:
     * 1. Specific index alternate path checks only against its paired index, as expected.
     * 2. Wildcard index allows for matching other positions.
     */
    @Test
    fun `should match specific index to paired index and wildcard to any position`() {
        val expectedJSONString = """
        [1]
        """.trimIndent()

        val actualJSONString = """
        ["a", 1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[0]"))
        }
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*]"))

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0]"))
        }
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*]"))
    }

    /**
     * Validates standard index matches take precedence over wildcard matches.
     *
     * Specifically, this checks the value at `actual[1]` is not first matched to the wildcard and
     * fails to satisfy the unspecified index `expected[1]`.
     */
    @Test
    fun `should prioritize standard index matches over wildcard matches`() {
        val expectedJSONString = """
        [1, 1]
        """.trimIndent()

        val actualJSONString = """
        ["a", 1, 1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*0]"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*0]"))
    }

    /**
     * Validates:
     * 1. Specific index alternate paths should correctly match their corresponding indexes.
     * 2. Wildcard matching should correctly match with any appropriate index.
     */
    @Test
    fun `should match specific indexes and align wildcards with appropriate indexes`() {
        val expectedJSONString = """
        [1, 2]
        """.trimIndent()

        val actualJSONString = """
        [4, 3, 2, 1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[0]", "[1]"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*]"))

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0]", "[1]"))
        }
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*]"))
    }

    /**
     * Validates that specific index wildcards only apply to the index specified.
     */
    @Test
    fun `should match specific index wildcard to its designated index only`() {
        val expectedJSONString = """
        [1, 2]
        """.trimIndent()

        val actualJSONString = """
        [1, 3, 2, 1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*1]"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*1]"))
    }

    /**
     * Validates that array-style access chained with key-value style access functions correctly.
     * This covers both specific index and wildcard index styles.
     */
    @Test
    fun `should correctly chain array-style with key-value access`() {
        val expectedJSONString = """
        [
            {
                "key1": 1,
                "key2": 2,
                "key3": 3
            }
        ]
        """.trimIndent()

        val actualJSONString = """
        [
            {
                "key1": 1,
                "key2": 2,
                "key3": 3
            }
        ]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[0].key1"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*].key1"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0].key1"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*].key1"))
    }

    /**
     * Validates that chained array-style access functions correctly.
     */
    @Test
    fun `should correctly chain array-style access 2x`() {
        val expectedJSONString = """
        [
            [1]
        ]
        """.trimIndent()

        val actualJSONString = """
        [
            [2]
        ]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[0][0]"))

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0][0]"))
        }
    }

    /**
     * Validates that longer chained array-style access functions correctly.
     */
    @Test
    fun `should correctly chain array-style access 4x`() {
        val expectedJSONString = """
        [[[[1]]]]
        """.trimIndent()

        val actualJSONString = """
        [[[[2]]]]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[0][0][0][0]"))

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0][0][0][0]"))
        }
    }

    /**
     * Validates that key-value style access chained with array-style access functions correctly.
     * This covers both specific index and wildcard index styles.
     */
    @Test
    fun `should correctly chain key-value with array-style access`() {
        val expectedJSONString = """
        {
            "key1": [1]
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": [2]
        }
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("key1[0]"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("key1[*]"))

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key1[0]"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key1[*]"))
        }
    }
}