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

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*

class JSONObjectAsserts {
    /**
     * Performs equality testing assertions between two `JSONObject` instances.
     */
    fun assertEqual(expected: Any?, actual: Any?, keyPath: List<Any> = emptyList()) {
        if (expected == null && actual == null) {
            return
        }
        if (expected == null || actual == null) {
            fail(
                """
                ${if (expected == null) "Expected is null" else "Actual is null"} and 
                ${if (expected == null) "Actual" else "Expected"} is non-null.
                Expected: $expected
                Actual: $actual
                Key path: ${keyPath.joinToString(".")}
            """.trimIndent()
            )
            return
        }

        when {
            expected is String && actual is String -> assertEquals("Key path: ${keyPath.joinToString(".")}", expected, actual)
            expected is Boolean && actual is Boolean -> assertEquals("Key path: ${keyPath.joinToString(".")}", expected, actual)
            expected is Int && actual is Int -> assertEquals("Key path: ${keyPath.joinToString(".")}", expected, actual)
            expected is Double && actual is Double -> assertEquals("Key path: ${keyPath.joinToString(".")}", expected, actual)
            expected is JSONObject && actual is JSONObject -> assertEqual(expected, actual, keyPath)
            expected is JSONArray && actual is JSONArray -> assertEqual(expected, actual, keyPath)

            // Not sure in what situations a raw List or Map would be present in a JSON string decoded using
            // the JSONObject init
//            expected is List<*> && actual is List<*> -> assertEqual(JSONObject(expected.toString()), JSONObject(actual.toString()), keyPath)
//            expected is Map<*, *> && actual is Map<*, *> -> assertEqual(JSONObject(expected.toString()), JSONObject(actual.toString()), keyPath)
            else -> fail(
                """
                Expected and Actual types do not match.
                Expected: $expected
                Actual: $actual
                Key path: ${keyPath.joinToString(".")}
            """.trimIndent()
            )
        }
    }

    /**
     * Performs equality testing assertions between two `Map<String, Any>` instances.
     */
    fun assertEqual(expected: JSONObject?, actual: JSONObject?, keyPath: List<Any> = listOf()) {
        if (expected == null && actual == null) {
            return
        }
        if (expected == null || actual == null) {
            fail(
                """
                ${if (expected == null) "Expected is null" else "Actual is null"} and 
                ${if (expected == null) "Actual" else "Expected"} is non-null.
                Expected: $expected
                Actual: $actual
                Key path: ${keyPath.joinToString(".")}
            """.trimIndent()
            )
            return
        }
        if (expected.length() != actual.length()) {
            fail(
                """
                Expected and Actual counts do not match (exact equality).
                Expected count: ${expected.length()}
                Actual count: ${actual.length()}
                Expected: $expected
                Actual: $actual
                Key path: ${keyPath.joinToString(".")}
            """.trimIndent()
            )
            return
        }
        for (key in expected.keys()) {
            val newKeyPath = keyPath.toMutableList()
            newKeyPath.add(key)
            assertEqual(
                expected = expected.get(key),
                actual = actual.opt(key),
                keyPath = newKeyPath
            )
        }
    }

    // Function to perform equality testing assertions between two `List<Any?>` instances.
    private fun assertEqual(
        expected: JSONArray?,
        actual: JSONArray?,
        keyPath: List<Any>
    ) {
        if (expected == null && actual == null) {
            return
        }
        if (expected == null || actual == null) {
            fail("""
            ${if (expected == null) "Expected is null" else "Actual is nil"} and ${if (expected == null) "Actual" else "Expected"} is non-nil.

            Expected: ${expected.toString()}

            Actual: ${actual.toString()}

            Key path: ${keyPath.joinToString(".")}
            """.trimIndent())
            return
        }
        if (expected.length() != actual.length()) {
            fail("""
            Expected and Actual counts do not match (exact equality).

            Expected count: ${expected.length()}
            Actual count: ${actual.length()}

            Expected: $expected

            Actual: $actual

            Key path: ${keyPath.joinToString(".")}
            """.trimIndent())
            return
        }
        for (index in 0 until expected.length()) {
            val newKeyPath = keyPath.toMutableList()
            newKeyPath.add(index)
            assertEqual(
                expected = expected.get(index),
                actual = actual.get(index),
                keyPath = keyPath
            )
        }
    }
}

