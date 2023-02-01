/*
 Copyright 2022 Adobe. All rights reserved.

 This file is licensed to you under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License. You may obtain a copy
 of the License at http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing, software distributed under
 the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
 OF ANY KIND, either express or implied. See the License for the specific language
 governing permissions and limitations under the License.
*/
package com.adobe.marketing.mobile.tutorial;

import static com.adobe.marketing.mobile.tutorial.MainApp.LOG_TAG;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

// Imports the Assurance and Core extensions for use in the code below.
//* Edge Tutorial - code section (1/4)
import com.adobe.marketing.mobile.Assurance;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.services.Log;
// Edge Tutorial - code section (1/4) */

import com.adobe.marketing.mobile.tutorial.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_SOURCE = "MainActivity";

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Handle deep linking, and connecting to Assurance
        final Intent intent = getIntent();
        final Uri data = intent.getData();

        if (data != null) {
            // Enables deep linking to connect to Assurance.
            //* Edge Tutorial - code section (2/4)
            Assurance.startSession(data.toString());
            Log.debug(LOG_TAG, LOG_SOURCE, "Deeplink for Assurance received: " + data.toString());
            // Edge Tutorial - code section (2/4) */
        }

        setSupportActionBar(binding.toolbar);
    }

    @Override
    public void onPause() {
        super.onPause();
        // The next two code sections are functionality that is enabled by the AEP Lifecycle
        // extension. The extension's main purpose is to track the app's state; basically when the app starts or is closed.
        // Enables the `lifecyclePause` API that tracks when the app is closed.
        //* Edge Tutorial - code section (3/4)
        MobileCore.lifecyclePause();
        // Edge Tutorial - code section (3/4) */
    }

    @Override
    public void onResume() {
        super.onResume();
        // Enables the `lifecycleStart` API that tracks when the app is opened.
        //* Edge Tutorial - code section (4/4)
        MobileCore.setApplication(getApplication());
        MobileCore.lifecycleStart(null);
        // Edge Tutorial - code section (4/4) */
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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
}