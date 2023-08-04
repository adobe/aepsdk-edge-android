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
    fun assertEqual(expected: Any?, actual: Any?) {
        assertEqual(expected = expected, actual = actual, keyPath = mutableListOf())
    }
    fun assertTypeMatch(expected: Any, actual: Any?, exactMatchPaths: List<String> = emptyList()) {
        val pathTree = generatePathTree(paths = exactMatchPaths)
        assertFlexibleEqual(expected = expected, actual = actual, pathTree = pathTree, exactMatchMode = false)
    }

    fun assertExactMatch(expected: Any, actual: Any?, typeMatchPaths: List<String> = emptyList()) {
        val pathTree = generatePathTree(paths = typeMatchPaths)
        assertFlexibleEqual(expected = expected, actual = actual, pathTree = pathTree, exactMatchMode = true)
    }

    /**
     * Performs equality testing assertions between two `JSONObject` instances.
     */
    fun assertEqual(expected: Any?, actual: Any?, keyPath: MutableList<Any> = mutableListOf()) {
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
                Key path: ${keyPathAsString(keyPath)}
            """.trimIndent()
            )
            return
        }

        when {
            expected is String && actual is String -> assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
            expected is Boolean && actual is Boolean -> assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
            expected is Int && actual is Int -> assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
            expected is Double && actual is Double -> assertEquals("Key path: ${keyPathAsString(keyPath)}", expected, actual)
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
                Key path: ${keyPathAsString(keyPath)}
            """.trimIndent()
            )
        }
    }

    /**
     * Performs equality testing assertions between two `Map<String, Any>` instances.
     */
    fun assertEqual(expected: JSONObject?, actual: JSONObject?, keyPath: MutableList<Any> = mutableListOf()) {
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
                Key path: ${keyPathAsString(keyPath)}
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
                Key path: ${keyPathAsString(keyPath)}
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
        keyPath: MutableList<Any>
    ) {
        if (expected == null && actual == null) {
            return
        }
        if (expected == null || actual == null) {
            fail("""
            ${if (expected == null) "Expected is null" else "Actual is nil"} and ${if (expected == null) "Actual" else "Expected"} is non-nil.

            Expected: ${expected.toString()}

            Actual: ${actual.toString()}

            Key path: ${keyPathAsString(keyPath)}
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

            Key path: ${keyPathAsString(keyPath)}
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

    // region Flexible assertion methods
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
                    assertEqual(expected, actual, keyPath)
                }
                expected == actual
            }
            else -> {
                true // Value type matching already passed by virtue of passing the where condition in the switch case
            }
        }
    }

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

        val unmatchedLHSIndices: Set<Int> = (0 until expected.length()).toSet()
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

        var unmatchedRHSElements = (0 until actual.length()).toSet()
            .subtract(exactIndexes.toSet())
            .sorted()
            .map { Pair(it, actual.opt(it)) }
            .toMutableList()

        fun performWildcardMatch(expectedIndexes: List<Int>, isGeneralWildcard: Boolean) {
            for (index in expectedIndexes) {
                keyPath.add(index)
                val matchTreeValue = if (isGeneralWildcard) pathTree?.get("[*]") else pathTree?.get("[*$index]")

                val isPathEnd = matchTreeValue is String

                val result = unmatchedRHSElements.indexOfFirst {
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

                    Actual (remaining unmatched elements): ${unmatchedRHSElements.map { it.second }}

                    Key path: ${keyPathAsString(keyPath)}
                """.trimIndent())
                    finalResult = false
                    continue
                }
                unmatchedRHSElements.removeAt(result)

                finalResult = finalResult && true
            }
        }

        // Handle alternate match paths with format: [*<INT>]
        performWildcardMatch(expectedIndexes = wildcardIndexes.sorted(), isGeneralWildcard = false)

        // Handle alternate match paths with format: [*]
        if (hasWildcardAny) {
            performWildcardMatch(expectedIndexes = unmatchedLHSIndices.sorted(), isGeneralWildcard = true)
        } else {
            for (index in unmatchedRHSElements.map { it.first }.sorted()) {
                keyPath.add(index)

                if (unmatchedRHSElements.any { it.first == index }) {
                    fail("""
                    Actual side's index $index has already been taken by a wildcard match. Verify the test setup for correctness.

                    Expected: ${expected.opt(index)}

                    Actual (remaining unmatched elements): ${unmatchedRHSElements.map { it.second }}

                    Key path: ${keyPathAsString(keyPath)}
                """.trimIndent())
                    finalResult = false
                    continue
                }

                finalResult = assertFlexibleEqual(
                    expected = expected.opt(index),
                    actual = actual.opt(index),
                    keyPath = keyPath,
                    pathTree = null,
                    exactMatchMode = exactMatchMode,
                    shouldAssert = shouldAssert) && finalResult
            }
        }

        return finalResult
    }
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
    // Convenience function that outputs a given key path as a pretty string
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

    // Performs regex match on the provided String, returning the original match and non-nil capture group results
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

    // Applies the provided regex pattern to the text and returns all the capture groups from the regex pattern
    private fun getCapturedRegexGroups(text: String, regexPattern: String): List<String> {
        val captureGroups = extractRegexCaptureGroups(text, regexPattern)?.flatMap { it.second }
        return captureGroups ?: listOf()
    }

    // Extracts all key path components from a given key path string
    private fun getKeyPathComponents(text: String): List<String> {
        // The empty string is a special case that the regex doesn't handle
        if (text.isEmpty()) return listOf("")

        // Capture groups:
        // 1. Any characters, or empty string before a `.` NOT preceded by a `\`
        // OR
        // 2. Any non-empty text preceding the end of the string
        //
        // Matches key path access in the style of: "key0\.key1.key2[1][2].key3". Captures each of the groups separated by `.` character and ignores `\.` as nesting.
        // the path example would result in: ["key0\.key1", "key2[1][2]", "key3"]
        val jsonNestingRegex = "(.*?)(?<!\\\\)(?:\\.)|(.+?)(?:$)"

        val matchResult = extractRegexCaptureGroups(text, jsonNestingRegex) ?: return listOf()

        var captureGroups = matchResult.flatMap { it.second }

        if (matchResult.last().first.last() == '.') {
            captureGroups += ""
        }
        return captureGroups
    }

    // Merges two constructed key path dictionaries, replacing `current` values with `new` ones, with the exception
// of existing values that are String types, which mean that it is a final key path from a different path string
// Merge order doesn't matter, the final result should always be the same
    private fun merge(current: MutableMap<String, Any>, new: Map<String, Any>): Map<String, Any> {
        for ((key, newValue) in new) {
            val currentValue = current[key]
            if (currentValue is MutableMap<*, *> && newValue is Map<*, *>) {
                current[key] = merge(currentValue as MutableMap<String, Any>, newValue as Map<String, Any>)
            } else {
                if (current[key] is String) {
                    continue
                }
                current[key] = newValue
            }
        }
        return current
    }

    // Constructs a key path dictionary from a given key path component array, and the final value is
// assigned the original path string used to construct the path
    private fun construct(path: MutableList<String>, pathString: String): Map<String, Any> {
        if (path.isEmpty()) return mapOf()
        val first = path.removeAt(0)
        return if (path.isEmpty()) {
            mapOf(first to pathString)
        } else {
            mapOf(first to construct(path, pathString))
        }
    }

    private fun generatePathTree(paths: List<String>): Map<String, Any>? {
        // Matches array subscripts and all the inner content. Captures the surrounding brackets and inner content: ex: "[123]", "[*123]"
        val arrayIndexRegex = "(\\[.*?\\])"
        val tree: MutableMap<String, Any> = mutableMapOf()

        for (exactValuePath in paths) {
            var allPathComponents: MutableList<String> = mutableListOf()

            // Break the path string into its component parts
            val keyPathComponents = getKeyPathComponents(exactValuePath)
            for (pathComponent in keyPathComponents) {
                val pathComponent = pathComponent.replace("\\.", ".")

                // Get all array access levels for the given pathComponent, if any
                // KNOWN LIMITATION: this regex only extracts all open+close square brackets and inner content ("[___]") regardless
                // of their relative position within the path component, ex: "key0[2]key1[3]" will be interpreted as: "key0" with array component "[2][3]"
                val arrayComponents = getCapturedRegexGroups(pathComponent, arrayIndexRegex)

                // If no array components are detected, just add the path as-is
                if (arrayComponents.isEmpty()) {
                    allPathComponents.add(pathComponent)
                }
                // Otherwise, extract just the path component before array components if it exists
                else {
                    val bracketIndex = pathComponent.indexOf("[")
                    if (bracketIndex == -1) {
                        println("TEST ERROR: unable to get bracket position from path: $pathComponent. Skipping exact path: $exactValuePath")
                        continue
                    }
                    val extractedPathComponent = pathComponent.substring(0, bracketIndex)
                    // It is possible the path itself starts with an array index: "[0][1]"
                    // in that case, do not insert an empty string; all array components will be handled by the arrayComponents extraction
                    if (extractedPathComponent.isNotEmpty()) {
                        allPathComponents.add(extractedPathComponent)
                    }
                    allPathComponents.addAll(arrayComponents)
                }
            }

            val constructedTree = construct(allPathComponents, exactValuePath)
            tree.putAll(merge(tree, constructedTree))
        }
        return if (tree.isEmpty()) null else tree
    }
    // endregion
}

