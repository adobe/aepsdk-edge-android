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
import java.util.regex.PatternSyntaxException

class JSONObjectAsserts {
    /**
     * Performs exact equality testing assertions between two `JSONObject`/`JSONArray` instances.
     * Provides a trace of the key path, including dictionary keys and array indices, on assertion failure to facilitate easier debugging.
     *
     * This is the main entry point for exact equality JSON testing assertions.
     */
    fun assertEqual(expected: Any?, actual: Any?) {
        assertEqual(expected = expected, actual = actual, keyPath = mutableListOf(), shouldAssert = true)
    }

    /**
     * Performs a flexible JSON comparison where only the key-value pairs on the expected side are required.
     * Uses value type match as the default validation mode, ensuring values are of the same type
     * (and are non-null if the expected value is not null).
     *
     * For example, given an expected JSON like:
     * ```
     * {
     *   "key1": "value1",
     *   "key2": [{ "nest1": 1}, {"nest2": 2}]
     * }
     * ```
     * An alternate mode path for the example JSON could be: `"key2[1].nest2"`.
     *
     * Alternate mode paths must begin from the top level of the expected JSON. From the specified key by the path onward, the alternate match mode is applied.
     *
     * There are three ways to specify alternate mode paths for arrays:
     * 1. Specific index: `[<INT>]` (e.g., `[0]`, `[28]`). The element at the specified index will use the alternate mode.
     * 2. Wildcard index: `[*<INT>]` (e.g., `[*1]`, `[*12]`). The element at the specified index will use the alternate mode and apply wildcard matching logic.
     * 3. General wildcard: `[*]`. Every element not explicitly specified by methods 1 or 2 will use the alternate mode and apply wildcard matching logic. This option is mutually exclusive with the default behavior.
     *    - By default, elements from the expected JSON are compared in order, up to the last element of the expected array.
     *
     * @param expected The expected JSON in `JSONObject`/`JSONArray` format used to perform the assertions.
     * @param actual The actual JSON in `JSONObject`/`JSONArray` format that is validated against `expected`.
     * @param exactMatchPaths The key paths in the expected JSON that should use exact matching mode, where values require both the same type and literal value.
     */
    fun assertTypeMatch(expected: Any, actual: Any?, exactMatchPaths: List<String> = emptyList()) {
        val pathTree = generatePathTree(paths = exactMatchPaths)
        assertFlexibleEqual(expected = expected, actual = actual, pathTree = pathTree, exactMatchMode = false)
    }

    /**
     * Performs a flexible JSON comparison where only the key-value pairs on the expected side are required.
     * Uses exact match as the default validation mode, ensuring values are of the same type and literal value.
     *
     * For example, given an expected JSON like:
     * ```
     * {
     *   "key1": "value1",
     *   "key2": [{ "nest1": 1}, {"nest2": 2}]
     * }
     * ```
     * An alternate mode path for the example JSON could be: `"key2[1].nest2"`.
     *
     * Alternate mode paths must begin from the top level of the expected JSON. From the specified key by the path onward, the alternate match mode is applied.
     *
     * There are three ways to specify alternate mode paths for arrays:
     * 1. Specific index: `[<INT>]` (e.g., `[0]`, `[28]`). The element at the specified index will use the alternate mode.
     * 2. Wildcard index: `[*<INT>]` (e.g., `[*1]`, `[*12]`). The element at the specified index will use the alternate mode and apply wildcard matching logic.
     * 3. General wildcard: `[*]`. Every element not explicitly specified by methods 1 or 2 will use the alternate mode and apply wildcard matching logic. This option is mutually exclusive with the default behavior.
     *    - By default, elements from the expected JSON are compared in order, up to the last element of the expected array.
     *
     * @param expected The expected JSON in `JSONObject`/`JSONArray` format used to perform the assertions.
     * @param actual The actual JSON in `JSONObject`/`JSONArray` format that is validated against `expected`.
     * @param typeMatchPaths The key paths in the expected JSON that should use type matching mode, where values require only the same type (and are non-null if the expected value is not null).
     */
    fun assertExactMatch(expected: Any, actual: Any?, typeMatchPaths: List<String> = emptyList()) {
        val pathTree = generatePathTree(paths = typeMatchPaths)
        assertFlexibleEqual(expected = expected, actual = actual, pathTree = pathTree, exactMatchMode = true)
    }

    /**
     * Compares the given [expected] and [actual] values for equality. If they are not equal and [shouldAssert] is true,
     * an assertion error is thrown. This method is also capable of handling nested JSON objects and arrays.
     *
     * @param expected The expected value to compare.
     * @param actual The actual value to compare.
     * @param keyPath A list of keys indicating the path to the current value being compared. This is particularly
     * useful for nested JSON objects and arrays. Defaults to an empty list.
     * @param shouldAssert Indicates whether an assertion error should be thrown if [expected] and [actual] are not equal.
     * Defaults to true.
     *
     * @return Returns true if [expected] and [actual] are equal, otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] values are not equal.
     */
    private fun assertEqual(expected: Any?, actual: Any?, keyPath: MutableList<Any> = mutableListOf(), shouldAssert: Boolean): Boolean {
        if (expected == null && actual == null) {
            return true
        }
        if (expected == null || actual == null) {
            if (shouldAssert) {
                fail(
                    """
                    ${if (expected == null) "Expected is null" else "Actual is null"} and 
                    ${if (expected == null) "Actual" else "Expected"} is non-null.
                    Expected: $expected
                    Actual: $actual
                    Key path: ${keyPathAsString(keyPath)}
                """.trimIndent()
                )
            }
            return false
        }

        return when {
            expected is String && actual is String -> {
                if (shouldAssert) assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
                expected == actual
            }
            expected is Boolean && actual is Boolean -> {
                if (shouldAssert) assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
                expected == actual
            }
            expected is Int && actual is Int -> {
                if (shouldAssert) assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
                expected == actual
            }
            expected is Double && actual is Double -> {
                if (shouldAssert) assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
                expected == actual
            }
            expected is JSONObject && actual is JSONObject -> assertEqual(expected, actual, keyPath, shouldAssert = shouldAssert)
            expected is JSONArray && actual is JSONArray -> assertEqual(expected, actual, keyPath, shouldAssert = shouldAssert)
            else -> {
                if (shouldAssert) {
                    fail(
                        """
                        Expected and Actual types do not match.
                        Expected: $expected
                        Actual: $actual
                        Key path: ${keyPathAsString(keyPath)}
                    """.trimIndent()
                    )
                }
                false
            }
        }
    }

    /**
     * Compares two `JSONObject` instances for exact equality. If they are not equal and [shouldAssert] is true,
     * an assertion error is thrown, providing detailed comparison results.
     *
     * This method recursively compares nested JSON objects and provides the key path in case of mismatches,
     * aiding in easier debugging.
     *
     * @param expected The expected JSON object to compare.
     * @param actual The actual JSON object to compare.
     * @param keyPath A list of keys indicating the path to the current value being compared. This is particularly
     * useful for nested JSON objects. Defaults to an empty list.
     * @param shouldAssert Indicates whether an assertion error should be thrown if [expected] and [actual] are not equal.
     * Defaults to true.
     *
     * @return Returns true if [expected] and [actual] are exactly equal, otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] JSON objects are not equal.
     */
    private fun assertEqual(expected: JSONObject?, actual: JSONObject?, keyPath: MutableList<Any> = mutableListOf(), shouldAssert: Boolean): Boolean {
        if (expected == null && actual == null) {
            return true
        }
        if (expected == null || actual == null) {
            if (shouldAssert) {
                fail(
                    """
                    ${if (expected == null) "Expected is null" else "Actual is null"} and 
                    ${if (expected == null) "Actual" else "Expected"} is non-null.
                    Expected: $expected
                    Actual: $actual
                    Key path: ${keyPathAsString(keyPath)}
                """.trimIndent()
                )
            }
            return false
        }
        if (expected.length() != actual.length()) {
            if (shouldAssert) {
                fail(
                    """
                    Expected and Actual counts do not match (exact equality).
                    Expected count: ${expected.length()}
                    Actual count: ${actual.length()}
                    Expected: $expected
                    Actual: $actual
                    Key path: ${keyPathAsString(keyPath)}
                """.trimIndent()
                )
            }
            return false
        }
        var finalResult = true
        for (key in expected.keys()) {
            val newKeyPath = keyPath.toMutableList()
            newKeyPath.add(key)
            finalResult = assertEqual(
                expected = expected.get(key),
                actual = actual.opt(key),
                keyPath = newKeyPath,
                shouldAssert = shouldAssert
            ) && finalResult
        }
        return finalResult
    }

    /**
     * Compares two `JSONArray` instances for exact equality. If they are not equal and [shouldAssert] is true,
     * an assertion error is thrown, providing detailed comparison results.
     *
     * This method recursively compares nested JSON arrays and provides the index path in case of mismatches,
     * aiding in easier debugging.
     *
     * @param expected The expected JSON array to compare.
     * @param actual The actual JSON array to compare.
     * @param keyPath A list representing the index path to the current value being compared. This is especially
     * useful for nested JSON arrays.
     * @param shouldAssert Indicates whether an assertion error should be thrown if [expected] and [actual] are not equal.
     *
     * @return Returns true if [expected] and [actual] are exactly equal, otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] JSON arrays are not equal.
     */
    private fun assertEqual(
        expected: JSONArray?,
        actual: JSONArray?,
        keyPath: MutableList<Any>,
        shouldAssert: Boolean
    ): Boolean {
        if (expected == null && actual == null) {
            return true
        }
        if (expected == null || actual == null) {
            if (shouldAssert) {
                fail("""
                ${if (expected == null) "Expected is null" else "Actual is nil"} and ${if (expected == null) "Actual" else "Expected"} is non-nil.
    
                Expected: ${expected.toString()}
    
                Actual: ${actual.toString()}
    
                Key path: ${keyPathAsString(keyPath)}
                """.trimIndent())
            }
            return false
        }
        if (expected.length() != actual.length()) {
            if (shouldAssert) {
                fail("""
                Expected and Actual counts do not match (exact equality).
    
                Expected count: ${expected.length()}
                Actual count: ${actual.length()}
    
                Expected: $expected
    
                Actual: $actual
    
                Key path: ${keyPathAsString(keyPath)}
                """.trimIndent())
            }
            return false
        }
        var finalResult = true
        for (index in 0 until expected.length()) {
            val newKeyPath = keyPath.toMutableList()
            newKeyPath.add(index)
            finalResult = assertEqual(
                expected = expected.get(index),
                actual = actual.get(index),
                keyPath = keyPath,
                shouldAssert = shouldAssert
            ) && finalResult
        }
        return finalResult
    }

    // region Flexible assertion methods

    /**
     * Performs a flexible comparison between the given [expected] and [actual] values, optionally using exact match
     * or value type match modes. In case of a mismatch and if [shouldAssert] is true, an assertion error is thrown.
     *
     * This method is capable of handling nested JSON objects and arrays and provides the key path on assertion failures for
     * easier debugging. It also allows for more granular matching behavior through the [pathTree] and [exactMatchMode] parameters.
     *
     * @param expected The expected value to compare.
     * @param actual The actual value to compare.
     * @param keyPath A list of keys or indices indicating the path to the current value being compared. This is particularly
     * useful for nested JSON objects and arrays. Defaults to an empty list.
     * @param pathTree A map representing specific paths within the JSON structure that should be compared using the alternate mode.
     * @param exactMatchMode If true, performs an exact match comparison; otherwise, uses value type matching.
     * @param shouldAssert Indicates whether an assertion error should be thrown if [expected] and [actual] are not equal.
     * Defaults to true.
     *
     * @return Returns true if [expected] and [actual] are equal based on the matching mode, otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] values are not equal.
     */
    private fun assertFlexibleEqual(
        expected: Any?,
        actual: Any?,
        keyPath: MutableList<Any> = mutableListOf(),
        pathTree: Map<String, Any>?,
        exactMatchMode: Boolean,
        shouldAssert: Boolean = true): Boolean {
        if (expected == null) {
            return true
        }
        if (actual == null) {
            if (shouldAssert) {
                fail("""
                    Expected JSON is non-nil but Actual JSON is nil.

                    Expected: $expected

                    Actual: $actual

                    Key path: ${keyPathAsString(keyPath)}
                """)
            }
            return false
        }

        when {
            expected is String && actual is String -> return compareValues(expected, actual, keyPath, exactMatchMode, shouldAssert)
            expected is Boolean && actual is Boolean -> return compareValues(expected, actual, keyPath, exactMatchMode, shouldAssert)
            expected is Int && actual is Int -> return compareValues(expected, actual, keyPath, exactMatchMode, shouldAssert)
            expected is Double && actual is Double -> return compareValues(expected, actual, keyPath, exactMatchMode, shouldAssert)
            expected is JSONArray && actual is JSONArray -> return if (exactMatchMode) {
                assertEqual(expected, actual, keyPath = keyPath, shouldAssert = shouldAssert)
            } else {
                assertFlexibleEqual(
                    expected = expected,
                    actual = actual,
                    keyPath = keyPath,
                    pathTree = pathTree,
                    exactMatchMode = exactMatchMode,
                    shouldAssert = shouldAssert)
            }
            expected is JSONObject && actual is JSONObject -> return if (exactMatchMode) {
                assertEqual(expected, actual, keyPath = keyPath, shouldAssert = shouldAssert)
            } else {
                assertFlexibleEqual(
                    expected = expected,
                    actual = actual,
                    keyPath = keyPath,
                    pathTree = pathTree,
                    exactMatchMode = exactMatchMode,
                    shouldAssert = shouldAssert)
            }
            else -> {
                if (shouldAssert) {
                    fail("""
                    Expected and Actual types do not match.

                    Expected: $expected

                    Actual: $actual

                    Key path: ${keyPathAsString(keyPath)}
                """)
                }
                return false
            }
        }
    }

    /**
     * Compares two values for equality based on the [exactMatchMode]. If they are not equal and [shouldAssert] is true,
     * an assertion error is thrown.
     *
     * @param expected The expected value to compare.
     * @param actual The actual value to compare.
     * @param keyPath A list of keys or indices indicating the path to the current value being compared.
     * @param exactMatchMode If true, performs an exact match comparison; otherwise, uses value type matching.
     * @param shouldAssert Indicates whether an assertion error should be thrown if [expected] and [actual] are not equal.
     *
     * @return Returns true if [expected] and [actual] are equal based on the matching mode, otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] values are not equal.
     */
    private fun compareValues(
        expected: Any,
        actual: Any,
        keyPath: MutableList<Any>,
        exactMatchMode: Boolean,
        shouldAssert: Boolean
    ): Boolean {
        return when {
            exactMatchMode -> {
                if (shouldAssert) {
                    assertEqual(expected, actual, keyPath, shouldAssert = shouldAssert)
                }
                expected == actual
            }
            else -> {
                true // Value type matching already passed by virtue of passing the where condition in the switch case
            }
        }
    }

    /**
     * Performs a flexible comparison between the given [expected] and [actual] JSON arrays, optionally using exact match
     * or value type match modes based on the provided [pathTree]. In case of a mismatch and if [shouldAssert] is true,
     * an assertion error is thrown.
     *
     * This method is capable of handling nested JSON arrays, and it provides the index path on assertion failures for
     * easier debugging. The [pathTree] allows for more granular matching behavior.
     *
     * @param expected The expected JSON array to compare.
     * @param actual The actual JSON array to compare.
     * @param keyPath A list representing the index path to the current value being compared, especially
     * useful for nested JSON arrays.
     * @param pathTree A map representing specific paths within the JSON structure that should be compared using the alternate mode.
     * @param exactMatchMode If true, performs an exact match comparison; otherwise, uses value type matching.
     * @param shouldAssert Indicates whether an assertion error should be thrown if [expected] and [actual] are not equal.
     * Defaults to true.
     *
     * @return Returns true if [expected] and [actual] are equal based on the matching mode and the [pathTree], otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] JSON arrays are not equal.
     */
    private fun assertFlexibleEqual(
        expected: JSONArray?,
        actual: JSONArray?,
        keyPath: MutableList<Any>,
        pathTree: Map<String, Any>?,
        exactMatchMode: Boolean,
        shouldAssert: Boolean = true
    ): Boolean {
        if (expected == null) {
            return true
        }

        if (actual == null) {
            if (shouldAssert) {
                fail("""
                Expected JSON is non-nil but Actual JSON is nil.

                Expected: $expected

                Actual: $actual

                Key path: ${keyPathAsString(keyPath)}
            """.trimIndent())
            }
            return false
        }

        if (expected.length() > actual.length()) {
            if (shouldAssert) {
                fail("""
                Expected JSON has more elements than Actual JSON. Impossible for Actual to fulfill Expected requirements.

                Expected count: ${expected.length()}
                Actual count: ${actual.length()}

                Expected: $expected

                Actual: $actual

                Key path: ${keyPathAsString(keyPath)}
            """.trimIndent())
            }
            return false
        }

        val arrayIndexValueRegex = "\\[(.*?)\\]"
        val indexValues = pathTree?.keys
            ?.flatMap { key -> getCapturedRegexGroups(text = key, regexPattern = arrayIndexValueRegex) }
            ?.filterNotNull() ?: emptyList()

        var exactIndexes: List<Int> = indexValues
            .filter { !it.contains("*") }
            .mapNotNull { it.toIntOrNull() }

        var wildcardIndexes: List<Int> = indexValues
            .filter { it.contains("*") }
            .mapNotNull { it.replace("*", "").toIntOrNull() }

        val hasWildcardAny: Boolean = indexValues.contains("*")

        var seenIndexes: MutableSet<Int> = mutableSetOf()

        fun createSortedValidatedRange(range: List<Int>): List<Int> {
            val result: MutableList<Int> = mutableListOf()
            for (index in range) {
                if (index !in 0 until expected.length()) {
                    fail("TEST ERROR: alternate match path using index ($index) is out of bounds. Verify the test setup for correctness.")
                    continue
                }
                if (!seenIndexes.add(index)) {
                    fail("TEST ERROR: index already seen: $index. Verify the test setup for correctness.")
                    continue
                }
                result.add(index)
            }
            return result.sorted()
        }

        exactIndexes = createSortedValidatedRange(exactIndexes)
        wildcardIndexes = createSortedValidatedRange(wildcardIndexes)

        val unmatchedExpectedIndices: Set<Int> = (0 until expected.length()).toSet()
            .subtract(exactIndexes.toSet())
            .subtract(wildcardIndexes.toSet())

        var finalResult = true

        // Handle alternate match paths with format: [0]
        for (index in exactIndexes) {
            keyPath.add(index)
            val matchTreeValue = pathTree?.get("[$index]")

            val isPathEnd = matchTreeValue is String

            finalResult = assertFlexibleEqual(
                expected = expected.opt(index),
                actual = actual.opt(index),
                keyPath = keyPath,
                pathTree = if (isPathEnd) null else matchTreeValue as Map<String, Any>,
                exactMatchMode = if (isPathEnd) !exactMatchMode else exactMatchMode,
                shouldAssert = shouldAssert) && finalResult
        }

        var unmatchedActualElements = (0 until actual.length()).toSet()
            .subtract(exactIndexes.toSet())
            .sorted()
            .map { Pair(it, actual.opt(it)) }
            .toMutableList()

        fun performWildcardMatch(expectedIndexes: List<Int>, isGeneralWildcard: Boolean) {
            for (index in expectedIndexes) {
                keyPath.add(index)
                val matchTreeValue = if (isGeneralWildcard) pathTree?.get("[*]") else pathTree?.get("[*$index]")

                val isPathEnd = matchTreeValue is String

                val result = unmatchedActualElements.indexOfFirst {
                    assertFlexibleEqual(
                        expected = expected.opt(index),
                        actual = it.second,
                        keyPath = keyPath,
                        pathTree = if (isPathEnd) null else matchTreeValue as MutableMap<String, Any>,
                        exactMatchMode = if (isPathEnd) !exactMatchMode else exactMatchMode,
                        shouldAssert = false)
                }

                if (result == -1) {
                    fail("""
                    Wildcard ${if (isPathEnd != exactMatchMode) "exact" else "type"} match found no matches on Actual side satisfying the Expected requirement.

                    Requirement: $matchTreeValue

                    Expected: ${expected.opt(index)}

                    Actual (remaining unmatched elements): ${unmatchedActualElements.map { it.second }}

                    Key path: ${keyPathAsString(keyPath)}
                """.trimIndent())
                    finalResult = false
                    continue
                }
                unmatchedActualElements.removeAt(result)

                finalResult = finalResult && true
            }
        }

        // Handle alternate match paths with format: [*<INT>]
        performWildcardMatch(expectedIndexes = wildcardIndexes.sorted(), isGeneralWildcard = false)

        // Handle alternate match paths with format: [*]
        if (hasWildcardAny) {
            performWildcardMatch(expectedIndexes = unmatchedExpectedIndices.sorted(), isGeneralWildcard = true)
        } else {
            for (index in unmatchedExpectedIndices.sorted()) {
                keyPath.add(index)

                if (unmatchedActualElements.any { it.first == index }) {
                    finalResult = assertFlexibleEqual(
                        expected = expected.opt(index),
                        actual = actual.opt(index),
                        keyPath = keyPath,
                        pathTree = null,
                        exactMatchMode = exactMatchMode,
                        shouldAssert = shouldAssert) && finalResult
                }
                else {
                    fail("""
                    Actual side's index $index has already been taken by a wildcard match. Verify the test setup for correctness.

                    Expected: ${expected.opt(index)}

                    Actual (remaining unmatched elements): ${unmatchedActualElements.map { it.second }}

                    Key path: ${keyPathAsString(keyPath)}
                """.trimIndent())
                    finalResult = false
                    continue
                }
            }
        }

        return finalResult
    }

    /**
     * Performs a flexible comparison between the given [expected] and [actual] JSON objects, optionally using exact match
     * or value type match modes based on the provided [pathTree]. In case of a mismatch and if [shouldAssert] is true,
     * an assertion error is thrown.
     *
     * This method is capable of handling nested JSON objects, and it provides the key path on assertion failures for
     * easier debugging. The [pathTree] allows for more granular matching behavior.
     *
     * @param expected The expected JSON object to compare.
     * @param actual The actual JSON object to compare.
     * @param keyPath A list representing the key path to the current value being compared, especially
     * useful for nested JSON objects.
     * @param pathTree A map representing specific paths within the JSON structure that should be compared in certain ways.
     * The path tree can include specific keys and their corresponding comparison modes or nested path trees.
     * @param exactMatchMode If true, performs an exact match comparison; otherwise, uses value type matching.
     * @param shouldAssert Indicates whether an assertion error should be thrown if [expected] and [actual] are not equal.
     * Defaults to true.
     *
     * @return Returns true if [expected] and [actual] are equal based on the matching mode and the [pathTree], otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] JSON objects are not equal.
     */
    private fun assertFlexibleEqual(
        expected: JSONObject?,
        actual: JSONObject?,
        keyPath: MutableList<Any>,
        pathTree: Map<String, Any>?,
        exactMatchMode: Boolean,
        shouldAssert: Boolean = true): Boolean {
        if (expected == null) {
            return true
        }
        if (actual == null) {
            if (shouldAssert) {
                fail("""
                    Expected JSON is non-nil but Actual JSON is nil.

                    Expected: ${expected.toString()}

                    Actual: ${actual.toString()}

                    Key path: ${keyPathAsString(keyPath)}
                """)
            }
            return false
        }
        if (expected.length() > actual.length()) {
            if (shouldAssert) {
                fail("""
                    Expected JSON has more elements than Actual JSON.

                    Expected count: ${expected.length()}
                    Actual count: ${actual.length()}

                    Expected: ${expected.toString()}

                    Actual: ${actual.toString()}

                    Key path: ${keyPathAsString(keyPath)}
                """)
            }
            return false
        }
        var finalResult = true
        val iterator: Iterator<String> = expected.keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            keyPath.add(key)
            val pathTreeValue = pathTree?.get(key)
            if (pathTreeValue is String) {
                finalResult = assertFlexibleEqual(
                    expected = expected.opt(key),
                    actual = actual.opt(key),
                    keyPath = keyPath,
                    pathTree = null, // is String means path terminates here
                    exactMatchMode = !exactMatchMode, // Invert default mode
                    shouldAssert = shouldAssert) && finalResult
            } else {
                finalResult = assertFlexibleEqual(
                    expected = expected.opt(key),
                    actual = actual.opt(key),
                    keyPath = keyPath,
                    pathTree = pathTreeValue as? Map<String, Any>,
                    exactMatchMode = exactMatchMode,
                    shouldAssert = shouldAssert) && finalResult
            }
        }
        return finalResult
    }
    // endregion

    // region Private helpers

    /**
     * Converts a key path represented by a list of JSON object keys and array indices into a human-readable string format.
     *
     * The key path is used to trace the recursive traversal of a nested JSON structure. For instance, if we're accessing
     * the value "Hello" in the JSON `{ "a": { "b": [ "World", "Hello" ] } }`, the key path would be ["a", "b", 1]. This method
     * would convert it to the string "a.b[1]".
     *
     * Special considerations:
     * 1. If a key in the JSON object contains a dot (.), it will be escaped with a backslash in the resulting string.
     * 2. Empty keys in the JSON object will be represented as "" in the resulting string.
     *
     * @param keyPath An ordered list of keys and indices representing the path to a value in a nested JSON structure.
     *
     * @return A human-readable string representation of the key path.
     */
    private fun keyPathAsString(keyPath: MutableList<Any>): String {
        var result = ""
        for (item in keyPath) {
            when (item) {
                is String -> {
                    if (result.isNotEmpty()) {
                        result += "."
                    }
                    result += when {
                        item.contains(".") -> item.replace(".", "\\.")
                        item.isEmpty() -> "\"\""
                        else -> item
                    }
                }
                is Int -> result += "[$item]"
            }
        }
        return result
    }

    /**
     * The method finds all matches of the [regexPattern] in the [text] and for each match, it returns the original matched string
     * and its corresponding non-nil capture groups.
     *
     * @param text The input string on which the regex matching is to be performed.
     * @param regexPattern The regex pattern to be used for matching against the [text].
     *
     * @return A list of pairs, where each pair consists of the original matched string and a list of its non-null capture groups.
     * Returns `null` if an invalid regex pattern is provided.
     *
     * @throws PatternSyntaxException If the [regexPattern] is not a valid regex pattern.
     */
    private fun extractRegexCaptureGroups(text: String, regexPattern: String): List<Pair<String, List<String>>>? {
        return try {
            val regex = Regex(regexPattern)
            val matchResults = regex.findAll(text).toList()
            val matchResult: MutableList<Pair<String, List<String>>> = mutableListOf()
            for (match in matchResults) {
                val groups = match.groupValues
                if (groups.isEmpty()) continue
                val matchString = groups[0]
                val captureGroups = groups.drop(1)
                matchResult.add(Pair(matchString, captureGroups))
            }
            matchResult
        } catch (e: PatternSyntaxException) {
            println("TEST ERROR: Invalid regex: ${e.message}")
            null
        }
    }

    /**
     * Extracts and returns all the captured groups from the provided [text] using the specified [regexPattern].
     *
     * @param text The input string on which the regex matching is to be performed.
     * @param regexPattern The regex pattern to be used for matching against the [text].
     *
     * @return A list containing all the captured groups found in the [text] using the [regexPattern]. If no groups are found or
     * if an invalid regex pattern is provided, an empty list is returned.
     */
    private fun getCapturedRegexGroups(text: String, regexPattern: String): List<String> {
        val captureGroups = extractRegexCaptureGroups(text, regexPattern)?.flatMap { it.second }
        return captureGroups ?: listOf()
    }

    /**
     * Extracts and returns the components of a given key path string.
     *
     * The method is designed to handle key paths in a specific style such as "key0\.key1.key2[1][2].key3", which represents
     * a nested structure in JSON objects. The method captures each group separated by the `.` character and treats
     * the sequence "\." as a part of the key itself (that is, it ignores "\." as a nesting indicator).
     *
     * For example, the input "key0\.key1.key2[1][2].key3" would result in the output: ["key0\.key1", "key2[1][2]", "key3"].
     *
     * @param text The input key path string that needs to be split into its components.
     *
     * @return A list of strings representing the individual components of the key path. If the input [text] is empty,
     * a list containing an empty string is returned. If no components are found, an empty list is returned.
     */
    private fun getKeyPathComponents(text: String): List<String> {
        // Handle the special case where the input is an empty string because the regex does not cover this case.
        if (text.isEmpty()) return listOf("")

        // The regex captures groups in two scenarios:
        // 1. Characters (or an empty string) preceding a `.` that is NOT preceded by a `\`.
        // 2. Any non-empty text right before the end of the string.
        //
        // This regex is designed to match key path access in styles like "key0\.key1.key2[1][2].key3".
        // It captures groups separated by the `.` character, but treats "\." as part of the key (i.e., it ignores "\." as a nesting separator).
        // For the given example, the result would be: ["key0\.key1", "key2[1][2]", "key3"].
        val jsonNestingRegex = """(.*?)(?<!\\)(?:\.)|(.+?)(?:$)"""

        val matchResult = extractRegexCaptureGroups(text, jsonNestingRegex) ?: return listOf()

        var captureGroups = matchResult.flatMap { it.second }.filter { it.isNotEmpty() }

        // Address the special case where the `.` character is the last character in the path.
        // In such cases, an empty string ("") is considered to be nested after the preceding key.
        // For instance, "key." would be split into "key" and "".
        if (matchResult.last().first.last() == '.') {
            captureGroups += ""
        }
        return captureGroups
    }

    /**
     * Merges two given mutable maps, with the values from the `new` map overwriting those from the `current` map,
     * unless the value in the `current` map is a string.
     *
     * If both the `current` and `new` maps have a value which is itself a mutable map for the same key,
     * the function will recursively merge these nested maps.
     *
     * @param current The original mutable map that will be updated.
     * @param new The mutable map containing new values that will overwrite or be added to the `current` map.
     *
     * @return The merged map, which is essentially the modified `current` map after merging.
     */
    private fun merge(current: MutableMap<String, Any>, new: MutableMap<String, Any>): Map<String, Any> {
        for ((key, newValue) in new) {
            val currentValue = current[key]
            if (currentValue is MutableMap<*, *> && newValue is MutableMap<*, *>) {
                current[key] = merge(currentValue as MutableMap<String, Any>, newValue as MutableMap<String, Any>)
            } else {
                if (current[key] is String) {
                    continue
                }
                current[key] = newValue
            }
        }
        return current
    }

    /**
     * Constructs a nested mutable map structure based on the provided path, with the deepest nested key pointing to the given `pathString`.
     *
     * For instance, given a path of `["a", "b", "c"]` and a `pathString` of "value", the resulting map would be:
     * `{"a": {"b": {"c": "value"}}}`.
     *
     * @param path A mutable list of strings representing the sequence of nested keys for the map structure.
     * @param pathString The string value that will be associated with the deepest nested key in the constructed map.
     *
     * @return A mutable map representing the nested structure constructed from the `path`, with the deepest nested key pointing to `pathString`.
     */
    private fun construct(path: MutableList<String>, pathString: String): MutableMap<String, Any> {
        if (path.isEmpty()) return mutableMapOf()
        val first = path.removeAt(0)
        return if (path.isEmpty()) {
            mutableMapOf(first to pathString)
        } else {
            mutableMapOf(first to construct(path, pathString))
        }
    }

    private fun generatePathTree(paths: List<String>): Map<String, Any>? {
        // Matches array subscripts, capturing the brackets and inner content (e.g., "[123]", "[*123]").
        val arrayIndexRegex = """(\[.*?\])"""
        val tree: MutableMap<String, Any> = mutableMapOf()

        for (exactValuePath in paths) {
            var allPathComponents: MutableList<String> = mutableListOf()

            // Break the path string into its individual components.
            val keyPathComponents = getKeyPathComponents(exactValuePath)
            for (pathComponent in keyPathComponents) {
                // Convert any escaped periods to actual periods, because the actual key names with periods in the JSON
                // will not be escaped
                val pathComponent = pathComponent.replace("\\.", ".")

                // Extract all array access levels from the pathComponent, if they exist.
                // Note: This regex extracts sequences of square brackets and their contents ("[___]").
                // However, positions of these brackets within the path component are not considered (e.g., "key0[2]key1[3]" will yield "key0" and "[2][3]").
                val arrayComponents = getCapturedRegexGroups(pathComponent, arrayIndexRegex)

                // If no array subscripts are detected, directly add the entire path component.
                if (arrayComponents.isEmpty()) {
                    allPathComponents.add(pathComponent)
                }
                // If array subscripts are detected, extract the path preceding the first subscript.
                else {
                    val bracketIndex = pathComponent.indexOf("[")
                    if (bracketIndex == -1) {
                        println("TEST ERROR: unable to get bracket position from path: $pathComponent. Skipping exact path: $exactValuePath")
                        continue
                    }
                    val extractedPathComponent = pathComponent.substring(0, bracketIndex)
                    // Handle cases where the path starts directly with an array index (e.g., "[0][1]").
                    // Avoid adding an empty string and directly add the array components.
                    if (extractedPathComponent.isNotEmpty()) {
                        allPathComponents.add(extractedPathComponent)
                    }
                    allPathComponents.addAll(arrayComponents)
                }
            }
            // Construct a nested map structure based on the path components.
            val constructedTree = construct(allPathComponents, exactValuePath)
            // Merge the newly constructed tree into the main tree.
            tree.putAll(merge(tree, constructedTree))
        }
        return if (tree.isEmpty()) null else tree
    }
    // endregion
}

