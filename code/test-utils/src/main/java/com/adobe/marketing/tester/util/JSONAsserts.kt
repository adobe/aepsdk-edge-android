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
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import java.util.regex.PatternSyntaxException

object JSONAsserts {
    /**
     * Performs exact equality testing assertions between two `JSONObject`/`JSONArray` instances.
     * Provides a trace of the key path, including dictionary keys and array indices, on assertion failure to facilitate easier debugging.
     *
     * This is the main entry point for exact equality JSON testing assertions.
     */
    @JvmStatic
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
    @JvmStatic
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
    @JvmStatic
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
        if ((expected == null || expected == JSONObject.NULL) && (actual == null || actual == JSONObject.NULL)) {
            return true
        }
        if (expected == null || expected == JSONObject.NULL || actual == null || actual == JSONObject.NULL) {
            if (shouldAssert) {
                fail(
                    """
                    ${if (expected == null || expected == JSONObject.NULL) "Expected is null" else "Actual is null"} and 
                    ${if (expected == null || actual == JSONObject.NULL) "Actual" else "Expected"} is non-null.
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
                if (shouldAssert) assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual,
                    0.0
                )
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
        if (expected == null || expected == JSONObject.NULL) {
            return true
        }
        if (actual == null || actual == JSONObject.NULL) {
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
            expected is JSONArray && actual is JSONArray -> return assertFlexibleEqual(
                expected = expected,
                actual = actual,
                keyPath = keyPath,
                pathTree = pathTree,
                exactMatchMode = exactMatchMode,
                shouldAssert = shouldAssert)
            expected is JSONObject && actual is JSONObject -> return assertFlexibleEqual(
                expected = expected,
                actual = actual,
                keyPath = keyPath,
                pathTree = pathTree,
                exactMatchMode = exactMatchMode,
                shouldAssert = shouldAssert)
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
        // Convert the array into a map
        var actualMap = (0 until actual.length()).associateBy({ it }, { actual[it] }).toMutableMap()

        // collect sets of all:
        // specific index [0]
        // wildcard specific [*0] - means any position, the fact that it terminates at the wildcard means
        // that the alternate match mode should be used there...

        // subtract all of these indices from the full set of expected indices
        var expectedIndexes = (0 until expected.length()).toSet()
        var wildcardIndexes: Set<Int>

        // check for general wildcards: [*] or [^], which override all other settings
        // they are just special cases of the specific indexes

        // this is collecting path end (regardless of index) and asterisk regardless of index
        val pathEndKeys = pathTree?.filter{ (key, value) ->
            value is String || key.contains('*')
        }?.keys ?: setOf()
        if (pathEndKeys.contains("[*]")) {
            wildcardIndexes = (0 until expected.length()).toSet()
            expectedIndexes = setOf()
        }
        else {
            // TODO: update this to be flat? since there's only 1 operation now instead of 3
            // this strongly validates index notation
            val arrayIndexValueRegex = """^\[(.*?)\]$"""
            val indexValues = pathEndKeys
                .flatMap { key -> getCapturedRegexGroups(text = key, regexPattern = arrayIndexValueRegex) }
                .toSet()

            fun filterConvertAndIntersect(
                condition: (String) -> Boolean,
                replacement: (String) -> String = { it }
            ): Set<Int> {
                var result = indexValues.filter(condition).mapNotNull { replacement(it).toIntOrNull() }.toSet()
                val intersection = expectedIndexes.intersect(result)
                result = intersection
                expectedIndexes = expectedIndexes - intersection
                return result
            }

            wildcardIndexes = filterConvertAndIntersect({ it.contains('*') }, { it.replace("*", "") })
        }

        var finalResult = true
        for (index in expectedIndexes) {
            var nextKeyPath = keyPath.toMutableList()
            nextKeyPath.add(index)
            val isPathEnd = pathTree?.get("[$index]") is String
            finalResult = assertFlexibleEqual(
                expected = expected.opt(index),
                actual = actual.opt(index),
                keyPath = nextKeyPath,
                pathTree = pathTree?.get("[$index]") as? Map<String, Any>,
                exactMatchMode = isPathEnd xor exactMatchMode,
                shouldAssert = shouldAssert) && finalResult
            actualMap.remove(index)
        }

        for (index in wildcardIndexes) {
            var nextKeyPath = keyPath.toMutableList()
            nextKeyPath.add(index)
            val pathTreeValue = pathTree?.get("[*]")
                ?: pathTree?.get("[*$index]")
                ?: pathTree?.get("[$index*]")

            val isPathEnd = pathTreeValue is String

            val result = actualMap.toList().indexOfFirst {
                assertFlexibleEqual(
                    expected = expected.opt(index),
                    actual = it.second,
                    keyPath = nextKeyPath,
                    pathTree = pathTreeValue as? Map<String, Any>,
                    exactMatchMode = isPathEnd xor exactMatchMode,
                    shouldAssert = false)
            }
            if (result == -1) {
                if (shouldAssert) {
                    fail("""
                            Wildcard ${if (isPathEnd != exactMatchMode) "exact" else "type"} match found no matches on Actual side satisfying the Expected requirement.
                            Requirement: $pathTreeValue
                            Expected: ${expected.opt(index)}
                            Actual (remaining unmatched elements): ${actualMap.values}
                            Key path: ${keyPathAsString(keyPath)}
                        """.trimIndent())
                }
                finalResult = false
                break
            }
            actualMap.remove(result)
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
                    Expected: $expected
                    Actual: $actual
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
                    Expected: $expected
                    Actual: $actual
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

            // Create an empty mutable list to store matched strings and their corresponding capture groups
            val matchResult: MutableList<Pair<String, List<String>>> = mutableListOf()

            for (match in matchResults) {
                // Get all groups for the current match, including the entire match itself
                val groups = match.groupValues
                // If no groups are found, skip the current iteration
                if (groups.isEmpty()) continue
                // The first item in the groups list is the entire matched string
                val matchString = groups[0]
                // Drop the first item to get only the capture groups
                val captureGroups = groups.drop(1)
                // Add the matched string and its capture groups to the result list
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
        // Handle edge case where input is empty
        if (text.isEmpty()) return listOf("")

        val segments = mutableListOf<String>()
        var startIndex = 0
        var inEscapeSequence = false

        // Iterate over each character in the input string with its index
        for ((index, char) in text.withIndex()) {
            // If current character is a backslash and we're not already in an escape sequence
            if (char == '\\') {
                inEscapeSequence = true
            }
            // If current character is a dot and we're not in an escape sequence
            else if (char == '.' && !inEscapeSequence) {
                // Add the segment from the start index to current index (excluding the dot)
                segments.add(text.substring(startIndex, index))

                // Update the start index for the next segment
                startIndex = index + 1
            }
            // Any other character or if we're ending an escape sequence
            else {
                inEscapeSequence = false
            }
        }

        // Add the remaining segment after the last dot (if any)
        segments.add(text.substring(startIndex))

        // Handle edge case where input ends with a dot (but not an escaped dot)
        if (text.endsWith(".") && !text.endsWith("\\.") && segments.last() != "") {
            segments.add("")
        }

        return segments
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

    /**
     * Generates a tree structure from a list of path strings.
     *
     * This function processes each path in [paths], extracts its individual components using [processPathComponents], and
     * constructs a nested map structure. The constructed map is then merged into the main tree. If the resulting tree
     * is empty after processing all paths, this function returns `null`.
     *
     * @param paths A list of path strings to be processed. Each path represents a nested structure to be transformed
     * into a tree-like map.
     *
     * @return A tree-like map structure representing the nested structure of the provided paths. Returns `null` if the
     * resulting tree is empty.
     *
     * @see processPathComponents
     * @see construct
     * @see merge
     */
    private fun generatePathTree(paths: List<String>): Map<String, Any>? {
        val tree: MutableMap<String, Any> = mutableMapOf()

        for (exactValuePath in paths) {
            val allPathComponents = processPathComponents(exactValuePath)

            val constructedTree = construct(allPathComponents, exactValuePath)
            tree.putAll(merge(tree, constructedTree))
        }

        return if (tree.isEmpty()) null else tree
    }


    /**
     * Processes a given key path string to extract individual path components.
     *
     * This function divides the input key path into its components, handling the following special cases:
     * 1. Array style access: "key1[0][1]" is split into "key1", "[0]", and "[1]".
     * 2. Dot notation for nesting: "key1.key2" is split into "key1" and "key2".
     *
     * Special cases can be escaped using a preceding backslash character. For instance:
     * - "key1\[0\]" is interpreted as the single component "key1[0]".
     * - "key1\.key2" is interpreted as the single key "key1.key2".
     *
     * @param path The input key path string to be split into components.
     *
     * @return A list of strings representing the individual components of the key path. If [path] is empty,
     * the function returns an empty list.
     */
    private fun processPathComponents(path: String): MutableList<String> {
        val allPathComponents: MutableList<String> = mutableListOf()

        val keyPathComponents = getKeyPathComponents(path)
        for (pathComponent in keyPathComponents) {
            val cleanedPathComponent = pathComponent.replace("\\.", ".")

            val components = extractArrayStyleComponents(cleanedPathComponent)
            allPathComponents.addAll(components)
        }
        return allPathComponents
    }
    private fun extractArrayStyleComponents(str: String): List<String> {
        // Handle edge case where input is empty
        if (str.isEmpty()) return listOf("")

        val components = mutableListOf<String>()
        var bracketCount = 0
        var componentBuilder = StringBuilder()
        var nextCharIsBackslash = false
        var lastArrayAccessEnd = str.length // to track the end of the last valid array-style access

        fun isNextCharBackslash(i: Int): Boolean {
            // Since we're iterating in reverse, the "previous" character is at i + 1
            nextCharIsBackslash = if (i - 1 >= 0) str[i - 1] == '\\' else false
            return nextCharIsBackslash
        }

        for (i in str.indices.reversed()) {
            when {
                str[i] == ']' && !isNextCharBackslash(i) -> {
                    bracketCount++
                    componentBuilder.append(']')
                }
                str[i] == '[' && !isNextCharBackslash(i) -> {
                    bracketCount--
                    componentBuilder.append('[')
                    if (bracketCount == 0) {
                        components.add(0, componentBuilder.reverse().toString())
                        componentBuilder = StringBuilder()
                        lastArrayAccessEnd = i
                    }
                }
                str[i] == '\\' -> {
                    if (nextCharIsBackslash) {
                        nextCharIsBackslash = false
                        continue
                    }
                    else {
                        componentBuilder.append('\\')
                    }
                }
                else -> {
                    // if we encounter a character outside brackets after valid array access,
                    // we treat all subsequent characters as one component until the start of the string or next valid array access.
                    if (bracketCount == 0 && i < lastArrayAccessEnd) {
                        components.add(0, str.substring(0, i + 1))
                        break
                    }
                    componentBuilder.append(str[i])
                }
            }
        }

        // Add any remaining component that's not yet added
        if (componentBuilder.isNotEmpty()) {
            components.add(0, componentBuilder.reverse().toString())
        }
        if (components.isNotEmpty()) {
            components[0] = components[0].replace("\\[", "[").replace("\\]", "]")
        }
        return components
    }
    // endregion
}

