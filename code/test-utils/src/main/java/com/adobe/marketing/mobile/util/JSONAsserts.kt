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

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Assert.assertEquals
import java.util.regex.PatternSyntaxException

object JSONAsserts {
    /**
     * Asserts exact equality between two [JSONObject] or [JSONArray] instances.
     *
     * In the event of an assertion failure, this function provides a trace of the key path,
     * which includes dictionary keys and array indexes, to aid debugging.
     *
     * @param expected The expected [JSONObject] or [JSONArray] to compare.
     * @param actual The actual [JSONObject] or [JSONArray] to compare.
     *
     * @throws AssertionError If the [expected] and [actual] JSON structures are not exactly equal.
     */
    @JvmStatic
    fun assertEqual(expected: Any?, actual: Any?) {
        assertEqual(expected = expected, actual = actual, keyPath = mutableListOf(), shouldAssert = true)
    }

    /**
     * Performs a flexible JSON comparison where only the key-value pairs from the expected JSON are required.
     * By default, the function validates that both values are of the same type.
     *
     * Alternate mode paths enable switching from the default type matching mode to exact value matching
     * mode for specified paths onward.
     *
     * For example, given an expected JSON like:
     * ```
     * {
     *   "key1": "value1",
     *   "key2": [{ "nest1": 1}, {"nest2": 2}],
     *   "key3": { "key4": 1 },
     *   "key.name": 1,
     *   "key[123]": 1
     * }
     * ```
     * An example [exactMatchPaths] path for this JSON would be: `"key2[1].nest2"`.
     *
     * Alternate mode paths must begin from the top level of the expected JSON.
     * Multiple paths can be defined. If two paths collide, the shorter one takes priority.
     *
     * Formats for keys:
     * - Nested keys: Use dot notation, e.g., "key3.key4".
     * - Keys with dots: Escape the dot, e.g., "key\.name".
     *
     * Formats for arrays:
     * - Index specification: `[<INT>]` (e.g., `[0]`, `[28]`).
     * - Keys with array brackets: Escape the brackets, e.g., `key\[123\]`.
     *
     * For wildcard array matching, where position doesn't matter:
     * 1. Specific index with wildcard: `[*<INT>]` or `[<INT>*]` (ex: `[*1]`, `[28*]`). The element
     * at the given index in [expected] will use wildcard matching in [actual].
     * 2. Universal wildcard: `[*]`. All elements in [expected] will use wildcard matching in [actual].
     *
     * In array comparisons, elements are compared in order, up to the last element of the expected array.
     * When combining wildcard and standard indexes, regular indexes are validated first.
     *
     * @param expected The expected JSON in [JSONObject] or [JSONArray] format to compare.
     * @param actual The actual JSON in [JSONObject] or [JSONArray] format to compare.
     * @param exactMatchPaths The key paths in the expected JSON that should use exact matching mode, where values require both the same type and literal value.
     */
    @JvmStatic
    fun assertTypeMatch(expected: Any, actual: Any?, exactMatchPaths: List<String> = emptyList()) {
        val pathTree = generatePathTree(paths = exactMatchPaths)
        assertFlexibleEqual(expected = expected, actual = actual, pathTree = pathTree, exactMatchMode = false)
    }

    /**
     * Performs a flexible JSON comparison where only the key-value pairs from the expected JSON are required.
     * By default, the function uses exact match mode, validating that both values are of the same type
     * and have the same literal value.
     *
     * Alternate mode paths enable switching from the default exact matching mode to type matching
     * mode for specified paths onward.
     *
     * For example, given an expected JSON like:
     * ```
     * {
     *   "key1": "value1",
     *   "key2": [{ "nest1": 1}, {"nest2": 2}],
     *   "key3": { "key4": 1 },
     *   "key.name": 1,
     *   "key[123]": 1
     * }
     * ```
     * An example [typeMatchPaths] path for this JSON would be: `"key2[1].nest2"`.
     *
     * Alternate mode paths must begin from the top level of the expected JSON.
     * Multiple paths can be defined. If two paths collide, the shorter one takes priority.
     *
     * Formats for keys:
     * - Nested keys: Use dot notation, e.g., "key3.key4".
     * - Keys with dots: Escape the dot, e.g., "key\.name".
     *
     * Formats for arrays:
     * - Index specification: `[<INT>]` (e.g., `[0]`, `[28]`).
     * - Keys with array brackets: Escape the brackets, e.g., `key\[123\]`.
     *
     * For wildcard array matching, where position doesn't matter:
     * 1. Specific index with wildcard: `[*<INT>]` or `[<INT>*]` (ex: `[*1]`, `[28*]`). The element
     * at the given index in [expected] will use wildcard matching in [actual].
     * 2. Universal wildcard: `[*]`. All elements in [expected] will use wildcard matching in [actual].
     *
     * In array comparisons, elements are compared in order, up to the last element of the expected array.
     * When combining wildcard and standard indexes, regular indexes are validated first.
     *
     * @param expected The expected JSON in [JSONObject] or [JSONArray] format to compare.
     * @param actual The actual JSON in [JSONObject] or [JSONArray] format to compare.
     * @param typeMatchPaths The key paths in the expected JSON that should use type matching mode, where values require only the same type (and are non-null if the expected value is not null).
     */
    @JvmStatic
    @JvmOverloads
    fun assertExactMatch(expected: Any, actual: Any?, typeMatchPaths: List<String> = emptyList()) {
        val pathTree = generatePathTree(paths = typeMatchPaths)
        assertFlexibleEqual(expected = expected, actual = actual, pathTree = pathTree, exactMatchMode = true)
    }

    /**
     * Compares the given [expected] and [actual] values for exact equality. If they are not equal and [shouldAssert] is `true`,
     * an assertion error is thrown.
     *
     * @param expected The expected value to compare.
     * @param actual The actual value to compare.
     * @param keyPath A list of keys indicating the path to the current value being compared. This is particularly
     * useful for nested JSON objects and arrays. Defaults to an empty list.
     * @param shouldAssert Indicates if an assertion error should be thrown if [expected] and [actual] are not equal.
     *
     * @return Returns true if [expected] and [actual] are equal, otherwise returns false.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] values are not equal.
     */
    private fun assertEqual(expected: Any?, actual: Any?, keyPath: List<Any> = listOf(), shouldAssert: Boolean): Boolean {
        val expectedIsNull = expected == null || expected == JSONObject.NULL
        val actualIsNull = actual == null || actual == JSONObject.NULL
        if (expectedIsNull && actualIsNull) {
            return true
        }
        if (expectedIsNull || actualIsNull) {
            if (shouldAssert) {
                fail(
                    """
                    ${if (expectedIsNull) "Expected is null" else "Actual is null"} and 
                    ${if (expectedIsNull) "Actual" else "Expected"} is non-null.
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
     * Compares two [JSONObject] instances for exact equality. If they are not equal and [shouldAssert] is `true`,
     * an assertion error is thrown.
     *
     * @param expected The expected [JSONObject] to compare.
     * @param actual The actual [JSONObject] to compare.
     * @param keyPath A list of keys or array indexes representing the path to the current value being compared.
     * @param shouldAssert Indicates if an assertion error should be thrown if [expected] and [actual] are not equal.
     *
     * @return Returns `true` if [expected] and [actual] are exactly equal, otherwise returns `false`.
     *
     * @throws AssertionError If [shouldAssert] is true and the [expected] and [actual] JSON objects are not equal.
     */
    private fun assertEqual(expected: JSONObject?, actual: JSONObject?, keyPath: List<Any>, shouldAssert: Boolean): Boolean {
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
            finalResult = assertEqual(
                expected = expected.get(key),
                actual = actual.opt(key),
                keyPath = keyPath.plus(key),
                shouldAssert = shouldAssert
            ) && finalResult
        }
        return finalResult
    }

    /**
     * Compares two [JSONArray] instances for exact equality. If they are not equal and [shouldAssert] is `true`,
     * an assertion error is thrown.
     *
     * @param expected The expected [JSONArray] to compare.
     * @param actual The actual [JSONArray] to compare.
     * @param keyPath A list of keys or array indexes representing the path to the current value being compared.
     * @param shouldAssert Indicates if an assertion error should be thrown if [expected] and [actual] are not equal.
     *
     * @return Returns `true` if [expected] and [actual] are exactly equal, otherwise returns `false`.
     *
     * @throws AssertionError If [shouldAssert] is `true` and the [expected] and [actual] JSON arrays are not equal.
     */
    private fun assertEqual(
        expected: JSONArray?,
        actual: JSONArray?,
        keyPath: List<Any>,
        shouldAssert: Boolean
    ): Boolean {
        if (expected == null && actual == null) {
            return true
        }
        if (expected == null || actual == null) {
            if (shouldAssert) {
                fail("""
                ${if (expected == null) "Expected is null" else "Actual is null"} and ${if (expected == null) "Actual" else "Expected"} is non-null.
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
            finalResult = assertEqual(
                expected = expected.get(index),
                actual = actual.get(index),
                keyPath = keyPath.plus(index),
                shouldAssert = shouldAssert
            ) && finalResult
        }
        return finalResult
    }

    // region Flexible assertion methods

    /**
     * Performs a flexible comparison between the given [expected] and [actual] values, optionally using exact match
     * or value type match modes. In case of a mismatch and if [shouldAssert] is `true`, an assertion error is thrown.
     *
     * It allows for customized matching behavior through the [pathTree] and [exactMatchMode] parameters.
     *
     * @param expected The expected value to compare.
     * @param actual The actual value to compare.
     * @param keyPath A list of keys or array indexes representing the path to the current value being compared. Defaults to an empty list.
     * @param pathTree A map representing specific paths within the JSON structure that should be compared using the alternate mode.
     * @param exactMatchMode If `true`, performs an exact match comparison; otherwise, uses value type matching.
     * @param shouldAssert Indicates if an assertion error should be thrown if [expected] and [actual] are not equal.
     * Defaults to true.
     *
     * @return Returns `true` if [expected] and [actual] are equal based on the matching mode and the [pathTree], otherwise returns `false`.
     *
     * @throws AssertionError If [shouldAssert] is `true` and the [expected] and [actual] values are not equal.
     */
    private fun assertFlexibleEqual(
        expected: Any?,
        actual: Any?,
        keyPath: List<Any> = listOf(),
        pathTree: Map<String, Any>?,
        exactMatchMode: Boolean,
        shouldAssert: Boolean = true): Boolean {
        val expectedIsNull = expected == null || expected == JSONObject.NULL
        val actualIsNull = actual == null || actual == JSONObject.NULL
        if (expectedIsNull) {
            return true
        }
        if (actualIsNull) {
            if (shouldAssert) {
                fail("""
                    Expected JSON is non-null but Actual JSON is null.
                    Expected: $expected
                    Actual: $actual
                    Key path: ${keyPathAsString(keyPath)}
                """)
            }
            return false
        }

        /**
         * Compares the [expected] and [actual] values for equality based on the [exactMatchMode].
         */
        fun compareValuesAssumingTypeMatch(): Boolean {
            if (exactMatchMode) {
                if (shouldAssert) {
                    assertEqual(expected, actual, keyPath, shouldAssert = shouldAssert)
                }
                return expected == actual
            }
            // The value type matching has already succeeded due to meeting the conditions in the switch case
            return true
        }

        when {
            expected is String && actual is String -> return compareValuesAssumingTypeMatch()
            expected is Boolean && actual is Boolean -> return compareValuesAssumingTypeMatch()
            expected is Int && actual is Int -> return compareValuesAssumingTypeMatch()
            expected is Double && actual is Double -> return compareValuesAssumingTypeMatch()
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
     * Performs a flexible comparison between the given [expected] and [actual] [JSONArray]s, optionally using exact match
     * or value type match modes. In case of a mismatch and if [shouldAssert] is `true`, an assertion error is thrown.
     *
     * It allows for customized matching behavior through the [pathTree] and [exactMatchMode] parameters.
     *
     * @param expected The expected [JSONArray] to compare.
     * @param actual The actual [JSONArray] to compare.
     * @param keyPath A list of keys or array indexes representing the path to the current value being compared.
     * @param pathTree A map representing specific paths within the JSON structure that should be compared using the alternate mode.
     * @param exactMatchMode If `true`, performs an exact match comparison; otherwise, uses value type matching.
     * @param shouldAssert Indicates if an assertion error should be thrown if [expected] and [actual] are not equal.
     *
     * @return Returns `true` if [expected] and [actual] are equal based on the matching mode and the [pathTree], otherwise returns `false`.
     *
     * @throws AssertionError If [shouldAssert] is `true` and the [expected] and [actual] JSON arrays are not equal.
     */
    private fun assertFlexibleEqual(
        expected: JSONArray?,
        actual: JSONArray?,
        keyPath: List<Any>,
        pathTree: Map<String, Any>?,
        exactMatchMode: Boolean,
        shouldAssert: Boolean
    ): Boolean {
        if (expected == null) {
            return true
        }

        if (actual == null) {
            if (shouldAssert) {
                fail("""
                Expected JSON is non-null but Actual JSON is null.
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
        // Convert the `actual` array into a mutable map, where the key is the array index and the
        // value is the corresponding element. Used to prevent double matching.
        val actualMap = (0 until actual.length()).associateBy({ it }, { actual[it] }).toMutableMap()

        var expectedIndexes = (0 until expected.length()).toSet()
        val wildcardIndexes: Set<Int>

        // Collect all the keys from `pathTree` that either:
        // 1. Mark the path end (where the value is a `String`), or
        // 2. Contain the asterisk (*) character.
        val pathEndKeys = pathTree?.filter{ (key, value) ->
            value is String || key.contains('*')
        }?.keys ?: setOf()

        // If general wildcard is present, it supersedes other paths
        if (pathEndKeys.contains("[*]")) {
            wildcardIndexes = (0 until expected.length()).toSet()
            expectedIndexes = setOf()
        }
        else {
            // TODO: update this to be flat? since there's only 1 operation now instead of 3
            // Strongly validates index notation: "[123]"
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
        // Expected side indexes that do not have alternate paths specified are matched first
        // to their corresponding actual side index
        for (index in expectedIndexes) {
            val isPathEnd = pathTree?.get("[$index]") is String
            finalResult = assertFlexibleEqual(
                expected = expected.opt(index),
                actual = actual.opt(index),
                keyPath = keyPath.plus(index),
                pathTree = pathTree?.get("[$index]") as? Map<String, Any>,
                exactMatchMode = isPathEnd != exactMatchMode,
                shouldAssert = shouldAssert) && finalResult
            actualMap.remove(index)
        }

        // Wildcard indexes are allowed to match the remaining actual side elements
        for (index in wildcardIndexes) {
            val pathTreeValue = pathTree?.get("[*]")
                ?: pathTree?.get("[*$index]")
                ?: pathTree?.get("[$index*]")

            val isPathEnd = pathTreeValue is String

            val result = actualMap.toList().indexOfFirst {
                assertFlexibleEqual(
                    expected = expected.opt(index),
                    actual = it.second,
                    keyPath = keyPath.plus(index),
                    pathTree = pathTreeValue as? Map<String, Any>,
                    exactMatchMode = isPathEnd != exactMatchMode,
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
     * Performs a flexible comparison between the given [expected] and [actual] [JSONObject]s, optionally using exact match
     * or value type match modes. In case of a mismatch and if [shouldAssert] is `true`, an assertion error is thrown.
     *
     * It allows for customized matching behavior through the [pathTree] and [exactMatchMode] parameters.
     *
     * @param expected The expected [JSONObject] to compare.
     * @param actual The actual [JSONObject] to compare.
     * @param keyPath A list of keys or array indexes representing the path to the current value being compared.
     * @param pathTree A map representing specific paths within the JSON structure that should be compared using the alternate mode.
     * @param exactMatchMode If `true`, performs an exact match comparison; otherwise, uses value type matching.
     * @param shouldAssert Indicates if an assertion error should be thrown if [expected] and [actual] are not equal.
     *
     * @return Returns `true` if [expected] and [actual] are equal based on the matching mode and the [pathTree], otherwise returns `false`.
     *
     * @throws AssertionError If [shouldAssert] is `true` and the [expected] and [actual] JSON objects are not equal.
     */
    private fun assertFlexibleEqual(
        expected: JSONObject?,
        actual: JSONObject?,
        keyPath: List<Any>,
        pathTree: Map<String, Any>?,
        exactMatchMode: Boolean,
        shouldAssert: Boolean): Boolean {
        if (expected == null) {
            return true
        }
        if (actual == null) {
            if (shouldAssert) {
                fail("""
                    Expected JSON is non-null but Actual JSON is null.
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

            val pathTreeValue = pathTree?.get(key)

            val isPathEnd = pathTreeValue is String

            finalResult = assertFlexibleEqual(
                expected = expected.opt(key),
                actual = actual.opt(key),
                keyPath = keyPath.plus(key),
                pathTree = pathTreeValue as? Map<String, Any>,
                exactMatchMode = isPathEnd != exactMatchMode,
                shouldAssert = shouldAssert) && finalResult
        }
        return finalResult
    }
    // endregion

    // region Private helpers

    /**
     * Converts a key path represented by a list of JSON object keys and array indexes into a human-readable string format.
     *
     * The key path is used to trace the recursive traversal of a nested JSON structure.
     * For instance, the key path for the value "Hello" in the JSON `{ "a": { "b": [ "World", "Hello" ] } }`
     * would be ["a", "b", 1].
     * This method would convert it to the string "a.b[1]".
     *
     * Special considerations:
     * 1. If a key in the JSON object contains a dot (.), it will be escaped with a backslash in the resulting string.
     * 2. Empty keys in the JSON object will be represented as "" in the resulting string.
     *
     * @param keyPath A list of keys or array indexes representing the path to a value in a nested JSON structure.
     *
     * @return A human-readable string representation of the key path.
     */
    private fun keyPathAsString(keyPath: List<Any>): String {
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
     * and its corresponding non-null capture groups.
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
            fail("TEST ERROR: Invalid regex: ${e.message}")
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
            // Since we're iterating in reverse, the "next" character is at i - 1
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

