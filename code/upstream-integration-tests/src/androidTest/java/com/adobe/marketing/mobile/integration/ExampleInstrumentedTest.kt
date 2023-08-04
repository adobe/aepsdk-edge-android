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

package com.adobe.marketing.mobile.integration

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adobe.marketing.tester.util.JSONObjectAsserts
import org.json.JSONObject

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.adobe.marketing.mobile.integration.test", appContext.packageName)
    }

    @Test
    fun testExample() {
        val jsonExpected = """
        {
          "key4": [1,2]
        }
        """.trimIndent()

        val jsonActual = """
        {
          "key4": [1,2,3]
        }
        """.trimIndent()

        val expected = JSONObject(jsonExpected)
        val actual = JSONObject(jsonActual)

        val testclass = JSONObjectAsserts()
//        testclass.assertEqual(expected, actual)
        testclass.assertTypeMatch(expected, actual)
    }
}