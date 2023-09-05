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
    JSONAssertsParameterizedTests.FlexibleCollectionMatchingTest::class,
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
    class FlexibleCollectionMatchingTest(private val expected: Any, private val actual: Any) {
        companion object {
            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with expected={0}, actual={1}")
            fun data(): Collection<Array<Any>> {
                return listOf(
                    arrayOf(JSONArray(), JSONArray(listOf(1))),
                    arrayOf(JSONArray(listOf(1,2,3)), JSONArray(listOf(1,2,3,4))),
                    arrayOf(JSONObject(), JSONObject(mapOf("k" to "v"))),
                    arrayOf(JSONObject(mapOf("key1" to 1, "key2" to "a", "key3" to 1.0, "key4" to true)),
                            JSONObject(mapOf("key1" to 1, "key2" to "a", "key3" to 1.0, "key4" to true, "key5" to "extra"))),
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
            private fun testCase(path: String, expected: Any, actual: Any, format: (Any) -> String): Array<String> {
                return arrayOf(path, format(expected), format(actual))
            }

            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with key={0}, expected={1}, actual={2}")
            fun data(): Collection<Array<String>> {
                return listOf(
                    // For each pair of same type different value cases, compare when set as the value in a key value pair
                    testCase("key1", 1, 2) { """{ "key1": $it }""" },
                    testCase("key1", "a", "b") { """{ "key1": $it }""" },
                    testCase("key1", 1.0, 2.0) { """{ "key1": $it }""" },
                    testCase("key1", true, false) { """{ "key1": $it }""" },
                    // Compare all pairs when set as the value in an array using a specific index alternate path
                    testCase("[0]", 1, 2) { """[$it]""" },
                    testCase("[0]", "a", "b") { """[$it]""" },
                    testCase("[0]", 1.0, 2.0) { """[$it]""" },
                    testCase("[0]", true, false) { """[$it]""" },
                    // Compare all pairs when set as the value in an array using a wildcard alternate path
                    testCase("[*]", 1, 2) { """[$it]""" },
                    testCase("[*]", "a", "b") { """[$it]""" },
                    testCase("[*]", 1.0, 2.0) { """[$it]""" },
                    testCase("[*]", true, false) { """[$it]""" }
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
            private fun testCase(path: String, expected: Any, actual: Any, format: (Any) -> String): Array<String> {
                return arrayOf(path, format(expected), format(actual))
            }

            @JvmStatic
            @Parameterized.Parameters(name = "{index}: test with key={0}, expected={1}, actual={2}")
            fun data(): Collection<Array<String>> {
                return listOf(
                    testCase("key1.",1,2) { """{ "key1": { "": $it } }""" }, // Nested empty string
                    testCase("key1..key3",1,2) { """{ "key1": { "": { "key3": $it } } }""" }, // Non-empty strings surrounding empty string
                    testCase(".key2.",1,2) { """{ "": { "key2": { "": $it } } }""" }, // Empty strings surrounding non-empty string
                    testCase("\\\\.",1,2) { """{ "\\.": $it }""" }, // Backslash before dot
                    testCase("",1,2) { """{ "": $it }""" }, // Empty key
                    testCase(".",1,2) { """{ "": { "": $it } }""" }, // Nested empty keys
                    testCase("...",1,2) { """{ "": { "": { "": { "": $it } } } }""" }, // Multiple nested empty keys
                    testCase("\\",1,2) { """{ "\\": $it }""" }, // Single backslash
                    testCase("\\\\",1,2) { """{ "\\\\": $it }""" }, // Double backslashes
                    testCase("\\.",1,2) { """{ ".": $it }""" }, // Escaped dot
                    testCase("k\\.1\\.2\\.3",1,2) { """{ "k.1.2.3": $it }""" }, // Dots in key
                    testCase("k\\.",1,2) { """{ "k.": $it }""" }, // Dot at the end of the key
                    testCase("\"",1,2) { """{ "\"": $it }""" }, // Escaped double quote
                    testCase("\'",1,2) { """{ "\'": $it }""" }, // Escaped single quote
                    testCase("'",1,2) { """{ "'": $it }""" }, // Single quote
                    testCase("key with space",1,2) { """{ "key with space": $it }""" }, // Space in key
                    testCase("\n",1,2) { """{ "\n": $it }""" }, // Control character
                    testCase("key \t \n newline",1,2) { """{ "key \t \n newline": $it }""" },  // Control characters in key
                    testCase("안녕하세요",1,2) { """{ "안녕하세요": $it }""" }, // Non-Latin characters
                    testCase("a]",1,2) { """{ "a]": $it }""" }, // Closing square bracket in key
                    testCase("a[",1,2) { """{ "a[": $it }""" }, // Opening square bracket in key
                    testCase("a[1]b",1,2) { """{ "a[1]b": $it }""" }, // Array style access in key
                    testCase("key1\\[0\\]",1,2) { """{ "key1[0]": $it }""" }, // Array style access at the end of key
                    testCase("\\[1\\][0]",1,2) { """{ "[1]": [$it] }""" }, // Array style key then actual array style access
                    testCase("\\[1\\\\][0]",1,2) { """{ "[1\\]": [$it] }""" } // Incomplete array style access then actual array style access
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