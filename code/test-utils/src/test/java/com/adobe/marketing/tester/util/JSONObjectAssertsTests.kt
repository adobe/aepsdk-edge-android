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

import com.adobe.marketing.tester.util.JSONObjectAsserts.assertTypeMatch
import org.json.JSONObject
import org.junit.Assert.assertThrows
import org.junit.Test

class JSONObjectAssertsTests {
    @Test
    fun `test basic type matching`() {
        val expected = 5
        val actual = 10
        assertTypeMatch(expected, actual) // Should pass since both are Int
    }

    @Test
    fun `test mismatched types`() {
        val expected = "Hello"
        val actual = 10
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual) // Should fail since expected is String and actual is Int
        }
    }

    @Test
    fun `test nested JSON matching`() {
        val expectedJSONString = """
        {
            "key1": "value1",
            "key2": {
                "nestedKey": 5
            }
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": "value1",
            "key2": {
                "nestedKey": 10
            }
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)
        assertTypeMatch(expected, actual) // Should pass since types match even if values don't
    }

    @Test
    fun `test exact path matching`() {
        val expectedJSONString = """
        {
            "key1": "value1",
            "key2": 5
        }
        """.trimIndent()

        val actualJSONString = """
        {
            "key1": "value1",
            "key2": "value2"
        }    
        """.trimIndent()
        val expected = JSONObject(expectedJSONString)
        val actual = JSONObject(actualJSONString)
        assertThrows(AssertionError::class.java) {
            assertTypeMatch(expected, actual, exactMatchPaths = listOf("key2")) // Should fail since exact match is expected for key2
        }
    }
}