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

package com.adobe.marketing.tester.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.adobe.marketing.mobile.*
import com.adobe.marketing.mobile.edge.consent.Consent
import com.adobe.marketing.mobile.edge.identity.AuthenticatedState
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.edge.identity.IdentityItem
import com.adobe.marketing.mobile.edge.identity.IdentityMap
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.tester.app.data.LocationHint
import com.adobe.marketing.tester.xdm.commerce.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder

class MainActivity : AppCompatActivity() {

    private var hasSpinnerBooted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handleDeepLink()
        setUpLocationHintSpinner()
        setOnClickListeners()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.app_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val intent: Intent
        if (item.itemId == R.id.connectToAssurance) {
            intent =
                Intent(this@MainActivity, AssuranceActivity::class.java)
            startActivity(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // Set up drop-down spinner to select location hint passed to setLocationHint api
    private fun setUpLocationHintSpinner() {
        hasSpinnerBooted = false
        val setHintSpinner = findViewById<Spinner>(R.id.set_hint_spinner)
        setHintSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener{
            // Item selector handler for drop-down spinner which calls setLocationHint
            override fun onItemSelected(adapterView: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (hasSpinnerBooted)
                {
                    val locationHint: LocationHint = LocationHint.fromOrdinal(pos)
                    Edge.setLocationHint(LocationHint.value(locationHint))
                }

                hasSpinnerBooted = true
            }

            // Item selector handler for drop-down spinner which calls setLocationHint
            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                // do nothing
            }
        }

        val adapter: ArrayAdapter<LocationHint> =
            ArrayAdapter<LocationHint>(
                this,
                android.R.layout.simple_spinner_item,
                LocationHint.values()
            )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        setHintSpinner.adapter = adapter
    }

    // Deep links handling
    private fun handleDeepLink() {
        val intent = intent
        val data = intent.data

        if (data != null) {
            Assurance.startSession(data.toString())
            Log.trace(
                "TestApp",
                "MainActivity",
                "Deep link received $data"
            )
        }
    }

    private fun setOnClickListeners() {
        findViewById<Button>(R.id.btn_env_prod).setOnClickListener {
            updateEnvironment("prod")
        }

        findViewById<Button>(R.id.btn_env_pre_prod).setOnClickListener {
            updateEnvironment("pre-prod")
        }

        findViewById<Button>(R.id.btn_env_int).setOnClickListener {
            updateEnvironment("int")
        }

        findViewById<Button>(R.id.btn_get_hint).setOnClickListener {
            Edge.getLocationHint {
                val textViewGetData: TextView = findViewById(R.id.tvGetData)
                runOnUiThread{ textViewGetData.text = "'$it'" }

            }
        }

        findViewById<Button>(R.id.btnCollectYes).setOnClickListener {
            collectConsentUpdate("y")
        }

        findViewById<Button>(R.id.btnCollectNo).setOnClickListener {
            collectConsentUpdate("n")
        }

        findViewById<Button>(R.id.btnCollectPending).setOnClickListener {
            collectConsentUpdate("p")
        }

        findViewById<Button>(R.id.btnGetConsent).setOnClickListener {
            Consent.getConsents { map: Map<String?, Any?>? ->
                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(map)
                Log.debug(
                    "TestApp",
                    "MainActivity",
                    String.format("Received Consent from API = %s", json)
                )

                val textViewGetData: TextView = findViewById(R.id.tvGetData)
                runOnUiThread { textViewGetData.text = json }
            }
        }

        findViewById<Button>(R.id.btnIdentityUpdate).setOnClickListener {
            val map = IdentityMap()
            map.addItem(
                IdentityItem("primary@email.com", AuthenticatedState.AMBIGUOUS, true),
                "Email"
            )
            map.addItem(IdentityItem("secondary@email.com"), "Email")
            map.addItem(IdentityItem("zzzyyyxxx"), "UserId")
            map.addItem(IdentityItem("John Doe"), "UserName")
            Identity.updateIdentities(map)
        }

        findViewById<Button>(R.id.btnIdentityRemove).setOnClickListener {
            Identity.removeIdentity(IdentityItem("secondary@email.com"), "Email")
        }

        findViewById<Button>(R.id.btnGetIdentity).setOnClickListener {
            Identity.getIdentities { map: IdentityMap? ->
                val gson = GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(map)
                val textViewGetData: TextView = findViewById(R.id.tvGetData)
                runOnUiThread { textViewGetData.text = json }
            }
        }

        findViewById<Button>(R.id.btnResetIdentities).setOnClickListener {
            MobileCore.resetIdentities()
        }

        findViewById<Button>(R.id.btnSubmitSingleEvent).setOnClickListener {

            val valuesList: MutableList<String> = ArrayList()
            valuesList.add("val1")
            valuesList.add("val2")
            val eventData: MutableMap<String, Any> = java.util.HashMap()
            eventData["test"] = "request"
            eventData["customText"] = "mytext"
            eventData["listExample"] = valuesList

            val event: ExperienceEvent =
                ExperienceEvent.Builder().setXdmSchema(createCommerceXDM()).setData(eventData).build()
            Edge.sendEvent(
                event
            ) { handles: List<EdgeEventHandle?>? ->
                Log.trace(
                    "TestApp",
                    "MainActivity",
                    "Data received in the callback, updating UI"
                )
                if (handles == null) {
                    return@sendEvent
                }

                val gson: Gson = GsonBuilder().setPrettyPrinting().create()
                val json: String = gson.toJson(handles)
                Log.debug(
                    "TestApp",
                    "MainActivity",
                    String.format("Received Edge event handle are : %s", json)
                )

                val textViewGetData: TextView = findViewById(R.id.tvGetData)
                runOnUiThread{ textViewGetData.text = json }
            }
        }
    }

    private fun createCommerceXDM() : MobileSDKCommerceSchema {
        // Create XDM data with Commerce data for purchases action
        val xdmData = MobileSDKCommerceSchema()
        val order = Order()
        order.currencyCode = "RON"
        order.priceTotal = 20.0
        val purchases = Purchases()
        purchases.value = 1.0
        val products = ProductListAdds()
        products.value = 21.0
        val commerce = Commerce()
        commerce.order = order
        commerce.productListAdds = products
        commerce.purchases = purchases
        xdmData.eventType = "commerce.purchases"
        xdmData.commerce = commerce

        return xdmData
    }

    private fun updateEnvironment(value: String) {
        if (value.isEmpty()) {
            return
        }

        val config: MutableMap<String, Any> = HashMap()
        config["edge.environment"] = value
        MobileCore.updateConfiguration(config)
    }

    private fun collectConsentUpdate(value: String) {
        if (value.isEmpty()) {
            return
        }

        val collectConsents: MutableMap<String, Any> = HashMap()
        collectConsents["val"] = value

        val consents: MutableMap<String, Any> = HashMap()
        consents["collect"] = collectConsents

        val updateConsents: MutableMap<String, Any> = HashMap()
        updateConsents["consents"] = consents

        Consent.update(updateConsents)
    }
}
