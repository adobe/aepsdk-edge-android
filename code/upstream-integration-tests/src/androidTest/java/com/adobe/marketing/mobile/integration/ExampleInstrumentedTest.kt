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
          "key1.key2": {
            "key3": "value2"
          },
          "key4": [1,2]
        }
        """.trimIndent()

        val jsonActual = """
        {
          "key1.key2": {
            "key3": "value1"
          },
          "key4": [1,2,3]
        }
        """.trimIndent()

        val expected = JSONObject(jsonExpected)
        val actual = JSONObject(jsonActual)

        val testclass = JSONObjectAsserts()
        testclass.assertEqual(expected, actual)
    }
}