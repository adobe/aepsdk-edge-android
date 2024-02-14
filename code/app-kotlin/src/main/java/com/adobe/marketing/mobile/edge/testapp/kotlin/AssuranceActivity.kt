package com.adobe.marketing.mobile.edge.testapp.kotlin

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.adobe.marketing.mobile.Assurance

class AssuranceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assurance)

        findViewById<Button>(R.id.btnConnectToAssuranceSession).setOnClickListener {
            val connectUrl = findViewById<EditText>(R.id.txtAssuranceSessionURL).text.toString()
            if (!connectUrl.isNullOrBlank()) {
                Assurance.startSession(connectUrl)
            }
        }
    }
}