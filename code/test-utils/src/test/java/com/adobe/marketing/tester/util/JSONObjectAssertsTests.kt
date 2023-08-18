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

import com.adobe.marketing.tester.util.JSONObjectAsserts.assertEqual
import com.adobe.marketing.tester.util.JSONObjectAsserts.assertExactMatch
import com.adobe.marketing.tester.util.JSONObjectAsserts.assertTypeMatch
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class JSONObjectAssertsTests {

    // Value matching validation
    @Test
    fun `test basic value matching int`() {
        val expected = 5
        val actual = 5

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic value matching double`() {
        val expected = 5.0
        val actual = 5.0

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic value matching bool`() {
        val expected = true
        val actual = true

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic value matching string`() {
        val expected = "a"
        val actual = "a"

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic value matching string non latin`() {
        val expected = "안녕하세요"
        val actual = "안녕하세요"

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test complex value matching array`() {
        val expected = JSONArray()
        val actual = JSONArray()

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test complex value matching object`() {
        val expected = JSONObject()
        val actual = JSONObject()

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic value matching null`() {
        val expected = JSONObject.NULL
        val actual = JSONObject.NULL

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    // Type matching validation
    @Test
    fun `test basic type matching int`() {
        val expected = 5
        val actual = 10

        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic type matching double`() {
        val expected = 5.0
        val actual = 10.0

        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic type matching bool`() {
        val expected = true
        val actual = false
        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic type matching string`() {
        val expected = "a"
        val actual = "b"
        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic type matching string non latin`() {
        val expected = "안"
        val actual = "안녕하세요"
        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test complex type matching array`() {
        val expected = JSONArray()
        val actual = JSONArray(listOf(1))
        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test complex type matching object`() {
        val expected = JSONObject()
        val actual = JSONObject(mapOf("k" to "v"))
        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test mismatched types`() {
        val expected = "Hello"
        val actual = 10

        // Should fail since expected is String and actual is Int
        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual)
        }
    }

    // Validates a larger expected array fails as expected
    // Consequence: all other tests can rely on alternate path to not affect array size validation
    @Test
    fun `test expected array has more elements fails`() {
        val expectedJSONString = """
        [1, 2]
        """.trimIndent()

        val actualJSONString = """
        [1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }

        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[0]"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[1]"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[0]", "[1]"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[*0]"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[*1]"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[*]"))
        }

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0]"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[1]"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0]", "[1]"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*0]"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*1]"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*]"))
        }
    }

    // Validates a larger expected map fails as expected
    // Consequence: all other tests can rely on alternate path to not affect map size validation
    @Test
    fun `test expected dictionary has more elements fails`() {
        val expectedJSONString = """
        {
            "key1": 1,
            "key2": 2
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": 1
        }
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }

        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("key1"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("key2"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("key1", "key2"))
        }

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key1"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key2"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key1", "key2"))
        }
    }

    @Test
    fun `test basic value matching as key value`() {
        val expectedJSONString = """
        {
            "key1": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": "a"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test basic type matching as key value`() {
        val expectedJSONString = """
        {
            "key1": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test nested object matching`() {
        val expectedJSONString = """
        {
            "key1": {
                "key2": {}
            }
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": {
                "key2": {}
            }
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test nested array matching`() {
        val expectedJSONString = """
        [[[]]]
        """.trimIndent()

        val actualJSONString = """
        [[[]]]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name empty string`() {
        val expectedJSONString = """
        {
            "": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name backslash`() {
        val expectedJSONString = """
        {
            "\\": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\\": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name double backslash`() {
        val expectedJSONString = """
        {
            "\\\\": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\\\\": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name period`() {
        val expectedJSONString = """
        {
            ".": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            ".": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name inner period`() {
        val expectedJSONString = """
        {
            "k.1.2.3": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "k.1.2.3": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name trailing period`() {
        val expectedJSONString = """
        {
            "k.": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "k.": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name escaped double quote`() {
        val expectedJSONString = """
        {
            "\"": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\"": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name single quote`() {
        val expectedJSONString = """
        {
            "'": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "'": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name escaped single quote`() {
        val expectedJSONString = """
        {
            "\'": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\'": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name inner space`() {
        val expectedJSONString = """
        {
            "key with space": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key with space": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name control character`() {
        val expectedJSONString = """
        {
            "\n": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\n": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name inner control character`() {
        val expectedJSONString = """
        {
            "key \t \n newline": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key \t \n newline": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test special key name non latin`() {
        val expectedJSONString = """
        {
            "안녕하세요": 1
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "안녕하세요": 1
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertEqual(expected, actual)
        assertExactMatch(expected, actual)
        assertTypeMatch(expected, actual)
    }

    @Test
    fun `test key name matching fails when missing`() {
        val expectedJSONString = """
        {
            "key1": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key2": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual)
        }
    }

    @Test
    fun `test partial validation`() {
        val expectedJSONString = """
        {
            "key1": {
                "key2": "a"
            }
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": {
                "key2": "b",
                "key3": 3
            }
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertThrows(AssertionError::class.java) {
            assertEqual(expected, actual)
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual)
        }
        assertExactMatch(expected, actual, typeMatchPaths = listOf("key1"))
        assertTypeMatch(expected, actual)
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key1"))
        }
    }

    // Alternate path tests - assertEqual does not handle alternate paths and is not tested here
    @Test
    fun `test alternate path matching`() {
        val expectedJSONString = """
        {
            "key1": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": "a"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("key1"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("key1"))
    }

    // Validates alternate path wildcards are order independent
    // Consequence: all other tests can use increasing index values without checking all permutations
    @Test
    fun `test alternate path array wildcard order independent`() {
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

    // Validates that general wildcard and specific index wildcard are both:
    // 1. Compatible together, and work to mark an index as using wildcard matching
    // 2. The general wildcard acts as a superset
    // Consequence: all other tests that need to specify all expected indexes as wildcard
    // can simply rely on the general wildcard
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

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*0]", "[*1]", "[*]"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*0]", "[*1]", "[*]"))
    }

    // Validates that wildcard character can be placed in front or behind the integer
    // Consequence: all other tests can use a standard format for asterisk placement
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

    // Validates alternate paths do not violate type mismatch validation
    @Test
    fun `test alternate path array type mismatch fails`() {
        val expectedJSONString = """
        ["a"]
        """.trimIndent()

        val actualJSONString = """
        [1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[0]"))
        }
        assertThrows(AssertionError::class.java) {
            assertExactMatch(expected, actual, typeMatchPaths = listOf("[*]"))
        }

        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0]"))
        }
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*]"))
        }
    }

    @Test
    fun `test alternate path special key empty string`() {
        val expectedJSONString = """
        {
            "": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf(""))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf(""))
        }
    }

    @Test
    fun `test alternate path special key backslash`() {
        val expectedJSONString = """
        {
            "\\": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\\": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("\\"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("\\"))
        }
    }

    @Test
    fun `test alternate path special key double backslash`() {
        val expectedJSONString = """
        {
            "\\\\": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\\\\": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("\\\\"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("\\\\"))
        }
    }

    @Test
    fun `test alternate path special key period`() {
        val expectedJSONString = """
        {
            ".": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            ".": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("\\."))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("\\."))
        }
    }

    @Test
    fun `test alternate path special key inner period`() {
        val expectedJSONString = """
        {
            "k.1.2.3": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "k.1.2.3": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("k\\.1\\.2\\.3"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("k\\.1\\.2\\.3"))
        }
    }

    @Test
    fun `test alternate path special key trailing period`() {
        val expectedJSONString = """
        {
            "k.": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "k.": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("k\\."))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("k\\."))
        }
    }

    @Test
    fun `test alternate path special key escaped double quote`() {
        val expectedJSONString = """
        {
            "\"": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\"": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("\""))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("\""))
        }
    }

    @Test
    fun `test alternate path special key escaped single quote`() {
        val expectedJSONString = """
        {
            "\'": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\'": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("\'"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("\'"))
        }
    }

    @Test
    fun `test alternate path special key single quote`() {
        val expectedJSONString = """
        {
            "'": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "'": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("'"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("'"))
        }
    }

    @Test
    fun `test alternate path special key with space`() {
        val expectedJSONString = """
        {
            "key with space": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key with space": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("key with space"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key with space"))
        }
    }

    @Test
    fun `test alternate path special key control character`() {
        val expectedJSONString = """
        {
            "\n": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "\n": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("\n"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("\n"))
        }
    }

    @Test
    fun `test alternate path special key inner control character`() {
        val expectedJSONString = """
        {
            "key \t \n newline": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key \t \n newline": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("key \t \n newline"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key \t \n newline"))
        }
    }

    @Test
    fun `test alternate path special key non latin`() {
        val expectedJSONString = """
        {
            "안녕하세요": "a"
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "안녕하세요": "b"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("안녕하세요"))
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("안녕하세요"))
        }
    }

    // Array tests
    @Test
    fun `test alternate path array`() {
        val expectedJSONString = """
        [1]
        """.trimIndent()

        val actualJSONString = """
        [1]
        """.trimIndent()
        val expected = JSONArray(expectedJSONString)
        val actual = JSONArray(actualJSONString)

        assertExactMatch(expected, actual, typeMatchPaths = listOf("[0]"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*]"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0]"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*]"))
    }

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
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*0].key1"))
        assertExactMatch(expected, actual, typeMatchPaths = listOf("[*].key1"))

        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[0].key1"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*0].key1"))
        assertTypeMatch(expected, actual, exactMatchPaths = listOf("[*].key1"))
    }
}