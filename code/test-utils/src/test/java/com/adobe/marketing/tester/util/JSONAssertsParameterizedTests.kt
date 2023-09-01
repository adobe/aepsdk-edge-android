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
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Suite


@RunWith(Suite::class)
@Suite.SuiteClasses(
    JSONAssertsParameterizedTests.ValueMatchingTest::class,
    JSONAssertsParameterizedTests.TypeMatchingTest::class,
    JSONAssertsParameterizedTests.FlexibleTypeMatchingTest::class,
    JSONAssertsParameterizedTests.FailureTests::class,
    JSONAssertsParameterizedTests.SpecialKeyTest::class,
    JSONAssertsParameterizedTests.AlternatePathValueTest::class,
    JSONAssertsParameterizedTests.AlternatePathTypeTest::class,
    JSONAssertsParameterizedTests.SpecialKeyAlternatePathTest::class,
    JSONAssertsParameterizedTests.ExpectedArrayLargerTest::class,
    JSONAssertsParameterizedTests.ExpectedDictionaryLargerTest::class
)
class JSONAssertsParameterizedTests {
    @RunWith(Parameterized::class)
    class ValueMatchingTest(private val expected: Any, private val actual: Any) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with expected={0}, actual={1}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    Array(2) { 5 }, // Int
                    Array(2) { 5.0 }, // Double
                    Array(2) { true }, // Boolean
                    Array(2) { "a" }, // String
                    Array(2) { "안녕하세요" }, // Non-Latin String
                    Array(2) { JSONArray() },
                    Array(2) { JSONArray("""[[[]]]""") }, // Nested arrays
                    Array(2) { JSONObject() },
                    Array(2) { JSONObject.NULL },
                    Array(2) { JSONObject("""{ "key1": 1 }""") }, // Key value pair
                    Array(2) { JSONObject("""{ "key1": { "key2": {} } }""") }, // Nested objects
                )
            }
        }

        @Test
        fun `should match basic values`() {
            assertEqual(expected, actual)
            assertExactMatch(expected, actual)
            assertTypeMatch(expected, actual)
        }
    }

    @RunWith(Parameterized::class)
    class TypeMatchingTest(private val expected: Any, private val actual: Any) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with expected={0}, actual={1}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    arrayOf(5, 10), // Int
                    arrayOf(5.0, 10.0), // Double
                    arrayOf(true, false), // Boolean
                    arrayOf("a", "b"), // String
                    arrayOf("안", "안녕하세요"), // Non-Latin String
                    arrayOf(JSONObject("""{ "key1": 1 }"""),
                            JSONObject("""{ "key1": 2 }""")), // Key value pair
                    arrayOf(JSONObject("""{ "key1": { "key2": "a" } }"""),
                            JSONObject("""{ "key1": { "key2": "b", "key3": 3 } }""")), // Nested partial by type
                )
            }
        }

        @Test
        fun `should match only by type for values of the same type`() {
            Assert.assertThrows(AssertionError::class.java) {
                assertEqual(expected, actual)
            }
            Assert.assertThrows(AssertionError::class.java) {
                assertExactMatch(expected, actual)
            }
            assertTypeMatch(expected, actual)
        }
    }

    @RunWith(Parameterized::class)
    class FlexibleTypeMatchingTest(private val expected: Any, private val actual: Any) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with expected={0}, actual={1}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    arrayOf(JSONArray(), JSONArray(listOf(1))),
                    arrayOf(JSONObject(), JSONObject(mapOf("k" to "v"))),
                )
            }
        }

        @Test
        fun `should pass flexible matching when expected is a subset`() {
            Assert.assertThrows(AssertionError::class.java) {
                assertEqual(expected, actual)
            }
            assertExactMatch(expected, actual)
            assertTypeMatch(expected, actual)
        }
    }

    @RunWith(Parameterized::class)
    class FailureTests(private val expected: Any, private val actual: Any) {
        companion object {
            private fun combinationsOfTwo(input: List<Any>): Array<Array<Any>> {
                val result = mutableListOf<Array<Any>>()
                for (i in input.indices) {
                    for (j in i + 1 until input.size) {
                        result.add(arrayOf(input[i], input[j]))
                    }
                }
                return result.toTypedArray()
            }

            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with expected={0}, actual={1}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    // Creates all unique 2 element combinations
                    *combinationsOfTwo(listOf(1, 2.0, "a", true, JSONObject(), JSONArray(), JSONObject.NULL)),
                    arrayOf(JSONObject("""{ "key1": 1 }"""),
                            JSONObject("""{ "key2": 1 }""")), // Key name mismatch
                )
            }
        }

        @Test
        fun `should error when not matching`() {
            Assert.assertThrows(AssertionError::class.java) {
                assertEqual(expected, actual)
            }
            Assert.assertThrows(AssertionError::class.java) {
                assertExactMatch(expected, actual)
            }
            Assert.assertThrows(AssertionError::class.java) {
                assertTypeMatch(expected, actual)
            }
        }
    }

    @RunWith(Parameterized::class)
    class SpecialKeyTest(expectedJSONString: String, actualJSONString: String) {
        private val expected: JSONObject = JSONObject(expectedJSONString)
        private val actual: JSONObject = JSONObject(actualJSONString)

        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with expected={0}, actual={1}")
            fun data(): Collection<Array<String>> {
                return listOf(
                    Array(2) {"""{ "": 1 }"""}, // Empty string
                    Array(2) {"""{ "\\": 1 }"""}, // Backslash
                    Array(2) {"""{ "\\\\": 1 }"""}, // Double backslash
                    Array(2) {"""{ ".": 1 }"""}, // Dot
                    Array(2) {"""{ "k.1.2.3": 1 }"""}, // Dot in key
                    Array(2) {"""{ "k.": 1 }"""}, // Dot at the end of key
                    Array(2) {"""{ "\"": 1 }"""}, // Escaped double quote
                    Array(2) {"""{ "'": 1 }"""}, // Single quote
                    Array(2) {"""{ "\'": 1 }"""}, // Escaped single quote
                    Array(2) {"""{ "key with space": 1 }"""}, // Space in key
                    Array(2) {"""{ "\n": 1 }"""}, // Control character
                    Array(2) {"""{ "key \t \n newline": 1 }"""}, // Control characters in key
                    Array(2) {"""{ "안녕하세요": 1 }"""}, // Non-Latin characters
                )
            }
        }

        @Test
        fun `should match special key JSONs`() {
            assertEqual(expected, actual)
            assertExactMatch(expected, actual)
            assertTypeMatch(expected, actual)
        }
    }

    @RunWith(Parameterized::class)
    class AlternatePathValueTest(private val keypath: String, expectedJSONString: String, actualJSONString: String) {
        private inline fun <T> tryOrNull(block: () -> T): T? {
            return try {
                block()
            } catch (e: Throwable) {
                null
            }
        }

        private val expected: Any = (tryOrNull { JSONObject(expectedJSONString) } ?: tryOrNull { JSONArray(expectedJSONString) })!!
        private val actual: Any? = tryOrNull { JSONObject(actualJSONString) } ?: tryOrNull { JSONArray(actualJSONString) }

        companion object {
            private fun createParameterCase(key: String, value: Any?, transform: (Any?) -> String): Array<String> {
                return arrayOf(key, transform(value), transform(value))
            }

            private val values = listOf(1, 2.0, "a", true, JSONObject(), JSONArray(), JSONObject.NULL, null)

            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with key={0}, expected={1}, actual={2}")
            fun data(): Collection<Array<String>> {
                return listOf(
                    *values.map { value -> createParameterCase("key1", value) { """{ "key1": $it }""" } }.toTypedArray(),
                    *values.map { value -> createParameterCase("[0]", value) { """[$it]""" } }.toTypedArray(),
                    *values.map { value -> createParameterCase("[*]", value) { """[$it]""" } }.toTypedArray(),
                )
            }
        }

        @Test
        fun `should not fail because of alternate path`() {
            assertExactMatch(expected, actual, typeMatchPaths = listOf(keypath))
            assertTypeMatch(expected, actual, exactMatchPaths = listOf(keypath))
        }
    }

    @RunWith(Parameterized::class)
    class AlternatePathTypeTest(private val keypath: String, expectedJSONString: String, actualJSONString: String) {
        private inline fun <T> tryOrNull(block: () -> T): T? {
            return try {
                block()
            } catch (e: Throwable) {
                null
            }
        }

        private val expected: Any = (tryOrNull { JSONObject(expectedJSONString) } ?: tryOrNull { JSONArray(expectedJSONString) })!!
        private val actual: Any? = tryOrNull { JSONObject(actualJSONString) } ?: tryOrNull { JSONArray(actualJSONString) }

        companion object {
            private fun createParameterCase(key: String, pair: Pair<Any, Any>, transform: (Any) -> String): Array<String> {
                return arrayOf(key, transform(pair.first), transform(pair.second))
            }

            private val values = listOf(
                Pair(1, 2),
                Pair("a", "b"),
                Pair(1.0, 2.0),
                Pair(true, false),
            )

            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with key={0}, expected={1}, actual={2}")
            fun data(): Collection<Array<String>> {
                return listOf(
                    *values.map { pair -> createParameterCase("key1", pair) { """{ "key1": $it }""" } }.toTypedArray(),
                    *values.map { pair -> createParameterCase("[0]", pair) { """[$it]""" } }.toTypedArray(),
                    *values.map { pair -> createParameterCase("[*]", pair) { """[$it]""" } }.toTypedArray(),
                )
            }
        }

        @Test
        fun `should apply alternate path to matching logic`() {
            assertExactMatch(expected, actual, typeMatchPaths = listOf(keypath))
            Assert.assertThrows(AssertionError::class.java) {
                assertTypeMatch(expected, actual, exactMatchPaths = listOf(keypath))
            }
        }
    }

    @RunWith(Parameterized::class)
    class SpecialKeyAlternatePathTest(private val keypath: String, expectedJSONString: String, actualJSONString: String) {
        private val expected: JSONObject = JSONObject(expectedJSONString)
        private val actual: JSONObject = JSONObject(actualJSONString)

        companion object {
            private inline fun <reified R> IntRange.mapToArray(transform: (Int) -> R): Array<R> {
                return Array(this.last - this.first + 1) { index -> transform(this.first + index) }
            }

            private fun createParameterCase(key: String, transform: (Int) -> String): Array<String> {
                return arrayOf(key, *(1..2).mapToArray(transform))
            }

            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with key={0}, expected={1}, actual={2}")
            fun data(): Collection<Array<String>> {
                return listOf(
                    createParameterCase("key1.") { """{ "key1": { "": $it } }""" }, // Nested empty string
                    createParameterCase("key1..key3") { """{ "key1": { "": { "key3": $it } } }""" }, // Non-empty strings surrounding empty string
                    createParameterCase(".key2.") { """{ "": { "key2": { "": $it } } }""" }, // Empty strings surrounding non-empty string
                    createParameterCase("\\\\.") { """{ "\\.": $it }""" }, // Backslash before dot
                    createParameterCase("") { """{ "": $it }""" }, // Empty key
                    createParameterCase(".") { """{ "": { "": $it } }""" }, // Nested empty keys
                    createParameterCase("...") { """{ "": { "": { "": { "": $it } } } }""" }, // Multiple nested empty keys
                    createParameterCase("\\") { """{ "\\": $it }""" }, // Single backslash
                    createParameterCase("\\\\") { """{ "\\\\": $it }""" }, // Double backslashes
                    createParameterCase("\\.") { """{ ".": $it }""" }, // Escaped dot
                    createParameterCase("k\\.1\\.2\\.3") { """{ "k.1.2.3": $it }""" }, // Dots in key
                    createParameterCase("k\\.") { """{ "k.": $it }""" }, // Dot at the end of the key
                    createParameterCase("\"") { """{ "\"": $it }""" }, // Escaped double quote
                    createParameterCase("\'") { """{ "\'": $it }""" }, // Escaped single quote
                    createParameterCase("'") { """{ "'": $it }""" }, // Single quote
                    createParameterCase("key with space") { """{ "key with space": $it }""" }, // Space in key
                    createParameterCase("\n") { """{ "\n": $it }""" }, // Control character
                    createParameterCase("key \t \n newline") { """{ "key \t \n newline": $it }""" },  // Control characters in key
                    createParameterCase("안녕하세요") { """{ "안녕하세요": $it }""" }, // Non-Latin characters
                    createParameterCase("a]") { """{ "a]": $it }""" }, // Closing square bracket in key
                    createParameterCase("a[") { """{ "a[": $it }""" }, // Opening square bracket in key
                    createParameterCase("a[1]b") { """{ "a[1]b": $it }""" }, // Array style access in key
                    createParameterCase("key1\\[0\\]") { """{ "key1[0]": $it }""" }, // Array style access at the end of key
                    createParameterCase("\\[1\\][0]") { """{ "[1]": [$it] }""" }, // Array style key then actual array style access
                    createParameterCase("\\[1\\\\][0]") { """{ "[1\\]": [$it] }""" } // Incomplete array style access then actual array style access
                )
            }
        }

        @Test
        fun `should handle special keys in alternate paths`() {
            assertExactMatch(expected, actual, typeMatchPaths = listOf(keypath))
            Assert.assertThrows(AssertionError::class.java) {
                assertTypeMatch(expected, actual, exactMatchPaths = listOf(keypath))
            }
        }
    }

    @RunWith(Parameterized::class)
    class ExpectedArrayLargerTest(private val alternateMatchPaths: List<String>) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with alternateMatchPaths={0}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    arrayOf(emptyList<String>()),
                    arrayOf(listOf("[0]")),
                    arrayOf(listOf("[1]")),
                    arrayOf(listOf("[0]", "[1]")),
                    arrayOf(listOf("[*0]")),
                    arrayOf(listOf("[*1]")),
                    arrayOf(listOf("[*]"))
                )
            }
        }

        private val expected = JSONArray("""[1, 2]""")
        private val actual = JSONArray("""[1]""")


        /**
         * Validates that a larger expected array compared to actual will throw errors
         * even when using alternate match paths.
         *
         * Consequence: Guarantees that array size validation isn't affected by alternate paths.
         */
        @Test
        fun `should error on larger expected arrays`() {
            Assert.assertThrows(AssertionError::class.java) {
                assertEqual(expected, actual)
            }
            Assert.assertThrows(AssertionError::class.java) {
                assertExactMatch(expected, actual, typeMatchPaths = alternateMatchPaths)
            }
            Assert.assertThrows(AssertionError::class.java) {
                assertTypeMatch(expected, actual, exactMatchPaths = alternateMatchPaths)
            }
        }
    }

    @RunWith(Parameterized::class)
    class ExpectedDictionaryLargerTest(private val alternateMatchPaths: List<String>) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with alternateMatchPaths={0}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    arrayOf(emptyList<String>()),
                    arrayOf(listOf("key1")),
                    arrayOf(listOf("key2")),
                    arrayOf(listOf("key1", "key2")),
                )
            }
        }

        private val expected = JSONObject("""{ "key1": 1, "key2": 2 }""")
        private val actual = JSONObject("""{ "key1": 1}""")

        /**
         * Validates that a larger expected dictionary compared to actual will throw errors
         * even when using alternate match paths.
         *
         * Consequence: Guarantees that dictionary size validation isn't affected by alternate paths.
         */
        @Test
        fun `should error on larger expected maps`() {
            Assert.assertThrows(AssertionError::class.java) {
                assertEqual(expected, actual)
            }
            Assert.assertThrows(AssertionError::class.java) {
                assertExactMatch(expected, actual, typeMatchPaths = alternateMatchPaths)
            }
            Assert.assertThrows(AssertionError::class.java) {
                assertTypeMatch(expected, actual, exactMatchPaths = alternateMatchPaths)
            }
        }
    }
}