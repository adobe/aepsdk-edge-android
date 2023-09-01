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
package com.adobe.marketing.tester.util

import com.adobe.marketing.tester.util.JSONAsserts.assertEqual
import com.adobe.marketing.tester.util.JSONAsserts.assertExactMatch
import com.adobe.marketing.tester.util.JSONAsserts.assertTypeMatch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test
class JSONAssertsTests {
    // Value matching validation
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
     *  Validates the behavior of general and specific index wildcards:
     * 1. Both are compatible and can mark an index for wildcard matching.
     * 2. The general wildcard acts as a superset of any specific index wildcard.
     *
     * Consequence: Tests that require wildcard matching for all expected indexes
     * can use the general wildcard alone.
     */
    @Test
    fun `test alternate path array wildcard specific and general`() {
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
     * Validates that wildcard character can be placed in front or behind the index
     *
     * Consequence: all other tests can use a standard format for asterisk placement, without
     * having to test all variations.
     */
    @Test
    fun `test alternate path array wildcard character placement`() {
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
    @Test
    fun `test alternate path array wildcard`() {
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

    // This test validates wildcard paths do not take up standard index matches
    @Test
    fun `test alternate path array wildcard order`() {
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

    @Test
    fun `test alternate path array multi wildcard`() {
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

    @Test
    fun `test alternate path array index specific wildcard`() {
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

    @Test
    fun `test alternate path array chained wildcard`() {
        val expectedJSONString = """
        [
            {
                "key1": 1
            }
        ]
        """.trimIndent()

        val actualJSONString = """
        [
            {
                "key1": 1
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

    @Test
    fun `test alternate path 2D array chained wildcard`() {
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

    @Test
    fun `test alternate path 4D array chained wildcard`() {
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

    @Test
    fun `test alternate path dictionary chained wildcard`() {
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