package com.adobe.marketing.mobile.edge.testapp.kotlin

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.adobe.marketing.mobile.AdobeCallbackWithError
import com.adobe.marketing.mobile.AdobeError
import com.adobe.marketing.mobile.Assurance
import com.adobe.marketing.mobile.Edge
import com.adobe.marketing.mobile.EdgeEventHandle
import com.adobe.marketing.mobile.Event
import com.adobe.marketing.mobile.EventSource
import com.adobe.marketing.mobile.EventType
import com.adobe.marketing.mobile.ExperienceEvent
import com.adobe.marketing.mobile.MobileCore
import com.adobe.marketing.mobile.edge.consent.Consent
import com.adobe.marketing.mobile.edge.identity.AuthenticatedState
import com.adobe.marketing.mobile.edge.identity.Identity
import com.adobe.marketing.mobile.edge.identity.IdentityItem
import com.adobe.marketing.mobile.edge.identity.IdentityMap
import com.adobe.marketing.mobile.edge.testapp.kotlin.data.LocationHint
import com.adobe.marketing.mobile.services.Log
import com.adobe.marketing.tester.xdm.commerce.Commerce
import com.adobe.marketing.tester.xdm.commerce.MobileSDKCommerceSchema
import com.adobe.marketing.tester.xdm.commerce.Order
import com.adobe.marketing.tester.xdm.commerce.ProductListAdds
import com.adobe.marketing.tester.xdm.commerce.Purchases
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
        setHintSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
                runOnUiThread { textViewGetData.text = "'$it'" }

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

            val valuesList = mutableListOf<String>()
            valuesList.add("val1")
            valuesList.add("val2")
            val eventData = mutableMapOf<String , Any>()
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
                runOnUiThread { textViewGetData.text = json }
            }
        }

        findViewById<Button>(R.id.btnSubmitCompleteEvent).setOnClickListener {
            val eventData = mapOf<String, Any>(
                    "xdm" to createCommerceXDM().serializeToXdm(),
                    "request" to mapOf<String, Any>("sendCompletion" to true)
            )

            val event = Event.Builder(
                "Edge Event Send Completion Request",
                EventType.EDGE,
                EventSource.REQUEST_CONTENT
            )
                    .setEventData(eventData)
                    .build()

            MobileCore.dispatchEventWithResponseCallback(
                event,
                2000L,
                object : AdobeCallbackWithError<Event?> {
                    @SuppressLint("SetTextI18n")
                    override fun call(completeEvent: Event?) {
                        val textViewGetData: TextView = findViewById(R.id.tvGetData)
                        runOnUiThread {
                            textViewGetData.text =
                                "Completion Event received: ${completeEvent?.eventData}"
                        }
                    }

                    @SuppressLint("SetTextI18n")
                    override fun fail(error: AdobeError?) {
                        val textViewGetData: TextView = findViewById(R.id.tvGetData)
                        runOnUiThread {
                            textViewGetData.text = "Dispatch Event Failed '${error?.errorName}"
                        }
                    }
                })
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

        val config = mutableMapOf<String, Any>()
        config["edge.environment"] = value
        MobileCore.updateConfiguration(config)
    }

    private fun collectConsentUpdate(value: String) {
        if (value.isEmpty()) {
            return
        }

        val collectConsents = mutableMapOf<String, Any>()
        collectConsents["val"] = value

        val consents = mutableMapOf<String, Any>()
        consents["collect"] = collectConsents

        val updateConsents = mutableMapOf<String, Any>()
        updateConsents["consents"] = consents

        Consent.update(updateConsents)
    }
}