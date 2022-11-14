/*
  Copyright 2019 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.tester;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.AdobeCallbackWithError;
import com.adobe.marketing.mobile.AdobeError;
import com.adobe.marketing.mobile.Assurance;
import com.adobe.marketing.mobile.Edge;
import com.adobe.marketing.mobile.EdgeCallback;
import com.adobe.marketing.mobile.EdgeEventHandle;
import com.adobe.marketing.mobile.ExperienceEvent;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.edge.consent.Consent;
import com.adobe.marketing.mobile.edge.identity.AuthenticatedState;
import com.adobe.marketing.mobile.edge.identity.Identity;
import com.adobe.marketing.mobile.edge.identity.IdentityItem;
import com.adobe.marketing.mobile.edge.identity.IdentityMap;
import com.adobe.marketing.mobile.xdm.Commerce;
import com.adobe.marketing.mobile.xdm.MobileSDKCommerceSchema;
import com.adobe.marketing.mobile.xdm.Order;
import com.adobe.marketing.mobile.xdm.ProductListAdds;
import com.adobe.marketing.mobile.xdm.Purchases;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

	private static final String LOG_TAG = "MainActivity";
	private boolean hasSpinnerBooted = false;

	// List of Location Hint values used in drop-down spinner
	private enum LocationHint {
		OR2("or2"),
		VA6("va6"),
		IRL1("irl1"),
		IND1("ind1"),
		JPN3("jpn3"),
		SGP3("sgp3"),
		AUS3("aus3"),
		NULL(null),
		EMPTY(""),
		INVALID("invalid");

		private final String value;

		LocationHint(final String value) {
			this.value = value;
		}

		private static final Map<String, LocationHint> lookup = new HashMap<>();

		static {
			for (LocationHint type : LocationHint.values()) {
				lookup.put(type.value, type);
			}
		}

		/**
		 * Get {@code LocationHint} instance from String name.
		 * @param name the name of a {@code LocationHint}
		 * @return {@code LocationHint} for the given name, or {@code LocationHint#NULL}
		 * if given name doesn't match a valid {@code LocationHint}
		 */
		static LocationHint fromString(final String name) {
			if (name == null) {
				return NULL;
			}

			LocationHint hint = lookup.get(name);
			return hint == null ? NULL : hint;
		}

		/**
		 * Get {@code LocationHint} instance from ordinal position.
		 * @param ordinal the position of the {@code LocationHint} as it appears in the definition.
		 * @return {@code LocationHint} for the given ordinal, or {@code LocationHint#NULL}
		 * if given ordinal is out of range.
		 */
		static LocationHint fromOrdinal(final int ordinal) {
			LocationHint[] values = LocationHint.values();

			if (ordinal < 0 || ordinal > values.length) {
				return NULL;
			}

			return values[ordinal];
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Deep links handling
		final Intent intent = getIntent();
		final Uri data = intent.getData();

		if (data != null) {
			Assurance.startSession(data.toString());
			MobileCore.log(LoggingMode.VERBOSE, LOG_TAG, "Deep link received " + data);
		}

		// Set up drop-down spinner to select location hint passed to setLocationHint api
		hasSpinnerBooted = false;
		Spinner setHintSpinner = findViewById(R.id.set_hint_spinner);
		setHintSpinner.setOnItemSelectedListener(this);
		ArrayAdapter<LocationHint> adapter = new ArrayAdapter<>(
			this,
			android.R.layout.simple_spinner_item,
			LocationHint.values()
		);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		setHintSpinner.setAdapter(adapter);
	}

	@Override
	public void onResume() {
		super.onResume();
		MobileCore.lifecycleStart(null);
	}

	@Override
	public void onPause() {
		super.onPause();
		MobileCore.lifecyclePause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.app_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;

		if (item.getItemId() == R.id.connectToAssurance) {
			intent = new Intent(MainActivity.this, AssuranceActivity.class);
			startActivity(intent);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	public void onUpdateEnvironmentProd(final View view) {
		updateEnvironment("prod");
	}

	public void onUpdateEnvironmentPreProd(final View view) {
		updateEnvironment("pre-prod");
	}

	public void onUpdateEnvironmentInt(final View view) {
		updateEnvironment("int");
	}

	public void onSubmitSingleEvent(final View view) {
		final TextView textViewGetData = findViewById(R.id.tvGetData);

		List<String> valuesList = new ArrayList<>();
		valuesList.add("val1");
		valuesList.add("val2");
		Map<String, Object> eventData = new HashMap<>();
		eventData.put("test", "request");
		eventData.put("customText", "mytext");
		eventData.put("listExample", valuesList);

		// Create XDM data with Commerce data for purchases action
		MobileSDKCommerceSchema xdmData = new MobileSDKCommerceSchema();
		Order order = new Order();
		order.setCurrencyCode("RON");
		order.setPriceTotal(20);
		Purchases purchases = new Purchases();
		purchases.setValue(1);
		ProductListAdds products = new ProductListAdds();
		products.setValue(21);
		Commerce commerce = new Commerce();
		commerce.setOrder(order);
		commerce.setProductListAdds(products);
		commerce.setPurchases(purchases);
		xdmData.setEventType("commerce.purchases");
		xdmData.setCommerce(commerce);

		ExperienceEvent event = new ExperienceEvent.Builder().setXdmSchema(xdmData).setData(eventData).build();
		Edge.sendEvent(
			event,
			new EdgeCallback() {
				@Override
				public void onComplete(final List<EdgeEventHandle> handles) {
					MobileCore.log(LoggingMode.VERBOSE, LOG_TAG, "Data received in the callback, updating UI");

					if (handles == null) {
						return;
					}

					view.post(
						new Runnable() {
							@Override
							public void run() {
								if (textViewGetData != null) {
									Gson gson = new GsonBuilder().setPrettyPrinting().create();
									String json = gson.toJson(handles);
									Log.d(LOG_TAG, String.format("Received Edge event handle are : %s", json));
									updateTextView(json, view);
								}
							}
						}
					);
				}
			}
		);
	}

	public void onSubmitCollectYes(final View view) {
		collectConsentUpdate("y");
	}

	public void onSubmitCollectNo(final View view) {
		collectConsentUpdate("n");
	}

	public void onSubmitCollectPending(final View view) {
		collectConsentUpdate("p");
	}

	public void getConsentAPIClicked(final View view) {
		Consent.getConsents(
			new AdobeCallback<Map<String, Object>>() {
				@Override
				public void call(Map<String, Object> map) {
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					String json = gson.toJson(map);
					Log.d(LOG_TAG, String.format("Received Consent from API = %s", json));
					updateTextView(json, view);
				}
			}
		);
	}

	public void updateIdentitiesClicked(final View view) {
		IdentityMap map = new IdentityMap();
		map.addItem(new IdentityItem("primary@email.com", AuthenticatedState.AMBIGUOUS, true), "Email");
		map.addItem(new IdentityItem("secondary@email.com"), "Email");
		map.addItem(new IdentityItem("zzzyyyxxx"), "UserId");
		map.addItem(new IdentityItem("John Doe"), "UserName");
		Identity.updateIdentities(map);
	}

	public void removeIdentityClicked(final View view) {
		Identity.removeIdentity(new IdentityItem("secondary@email.com"), "Email");
	}

	public void getIdentitiesClicked(final View view) {
		Identity.getIdentities(
			new AdobeCallback<IdentityMap>() {
				@Override
				public void call(final IdentityMap map) {
					Gson gson = new GsonBuilder().setPrettyPrinting().create();
					String json = gson.toJson(map);
					Log.d(LOG_TAG, String.format("Received Identities from API = %s", json));
					updateTextView(json, view);
				}
			}
		);
	}

	public void resetIdentitiesClicked(final View view) {
		MobileCore.resetIdentities();
	}

	public void onGetLocationHint(final View view) {
		Edge.getLocationHint(
			new AdobeCallbackWithError<String>() {
				@Override
				public void fail(AdobeError adobeError) {
					updateTextView("Location Hint: Error: " + adobeError.getErrorName(), view);
				}

				@Override
				public void call(String s) {
					updateTextView("Location Hint: '" + s + "'", view);
					// update selected value in spinner
					final LocationHint hint = LocationHint.fromString(s);
					final Spinner setHintSpinner = findViewById(R.id.set_hint_spinner);
					setHintSpinner.post(
						new Runnable() {
							@Override
							public void run() {
								setHintSpinner.setSelection(hint.ordinal());
							}
						}
					);
				}
			}
		);
	}

	// Item selector handler for drop-down spinner which calls setLocationHint
	@Override
	public void onItemSelected(AdapterView<?> adapterView, View view, int pos, long id) {
		if (hasSpinnerBooted) {
			final LocationHint locationHint = LocationHint.fromOrdinal(pos);
			Edge.setLocationHint(locationHint.value);
		}

		hasSpinnerBooted = true;
	}

	// Item selector handler for drop-down spinner which calls setLocationHint
	@Override
	public void onNothingSelected(AdapterView<?> adapterView) {
		// do nothing
	}

	private void collectConsentUpdate(final String value) {
		if (value == null || value.isEmpty()) {
			return;
		}

		Consent.update(
			new HashMap<String, Object>() {
				{
					put(
						"consents",
						new HashMap<String, Object>() {
							{
								put(
									"collect",
									new HashMap<String, Object>() {
										{
											put("val", value);
										}
									}
								);
							}
						}
					);
				}
			}
		);
	}

	private void updateEnvironment(final String value) {
		if (value == null || value.isEmpty()) {
			return;
		}

		MobileCore.updateConfiguration(
			new HashMap<String, Object>() {
				{
					put("edge.environment", value);
				}
			}
		);
	}

	private void updateTextView(final String jsonString, final View view) {
		final TextView textViewGetData = findViewById(R.id.tvGetData);
		view.post(
			new Runnable() {
				@Override
				public void run() {
					if (textViewGetData != null) {
						textViewGetData.setText(jsonString);
					}
				}
			}
		);
	}
}
