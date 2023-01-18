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

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

// Imports the Edge extension for use in the code below.
/* Edge Tutorial - code section (1/3)
import com.adobe.marketing.mobile.Edge;
import com.adobe.marketing.mobile.EdgeCallback;
import com.adobe.marketing.mobile.EdgeEventHandle;
import com.adobe.marketing.mobile.ExperienceEvent;
// Edge Tutorial - code section (1/3) */

import com.adobe.marketing.mobile.xdm.Commerce;
import com.adobe.marketing.mobile.xdm.MobileSDKCommerceSchema;
import com.adobe.marketing.mobile.xdm.ProductListAdds;
import com.adobe.marketing.mobile.xdm.ProductListItemsItem;

import com.adobe.marketing.mobile.tutorial.databinding.FragmentFirstBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EdgeFragment extends Fragment {
    private static final String LOG_TAG = "EdgeFragment";

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Product add event - button action
        // Creates and sends an add to cart commerce event to the Adobe Experience Edge, using an XDM object.
        /* Edge Tutorial - code section (2/3)
        binding.productAddEventButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ProductListItemsItem product = new ProductListItemsItem();
                product.setName("wide_brim_sunhat");
                product.setPriceTotal(50);
                product.setSKU("12345");
                product.setQuantity(1);
                product.setCurrencyCode("USD");

                List<ProductListItemsItem> productListItems = new ArrayList<>();
                productListItems.add(product);

                ProductListAdds productAdd = new ProductListAdds();
                productAdd.setValue(1);

                Commerce commerce = new Commerce();
                commerce.setProductListAdds(productAdd);

                MobileSDKCommerceSchema xdmData = new MobileSDKCommerceSchema();
                xdmData.setEventType("commerce.productListAdds");
                xdmData.setCommerce(commerce);
                xdmData.setProductListItems(productListItems);

                /// Creates an Experience Event with an event payload that conforms to the XDM schema set
                // up in the Adobe Experience Platform. This event is an example of a product add.
                ExperienceEvent event = new ExperienceEvent.Builder()
                        .setXdmSchema(xdmData)
                        .build();
                Log.d(LOG_TAG, "Sending event");
                Edge.sendEvent(event, new EdgeCallback() {

                    @Override
                    public void onComplete(final List<EdgeEventHandle> handles) {
                        Log.d(LOG_TAG, "Edge event callback called");
                    }
                });
            }
        });
        // Edge Tutorial - code section (2/3) */

        // Product view event - button action
        // Creates and sends an add to cart commerce event to the Adobe Experience Edge, using a custom map.
        /* Edge Tutorial - code section (3/3)
        binding.productViewEventButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Construct commerce data body
                Map<String, Object> productListViewsData = new HashMap<>();
                productListViewsData.put("value", 1);

                Map<String, Object> productListViews = new HashMap<>();
                productListViews.put("productListViews", productListViewsData);

                // Construct productListItems data body
                Map<String, Object> productListItem = new HashMap<>();
                productListItem.put("name", "wide_brim_sunhat");
                productListItem.put("SKU", "12345");

                List<Object> productList = new ArrayList<>();
                productList.add(productListItem);

                // Put together final XDM event data payload
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("eventType", "commerce.productViews");
                eventData.put("commerce", productListViews);
                eventData.put("productListItems", productList);
                // Creates an Experience Event with an event payload that conforms to the XDM schema set
                // up in the Adobe Experience Platform. This event is an example of a product view.
                ExperienceEvent event = new ExperienceEvent.Builder()
                        .setXdmSchema(eventData)
                        .build();
                Log.d(LOG_TAG, "Sending event");
                Edge.sendEvent(event, new EdgeCallback() {

                    @Override
                    public void onComplete(final List<EdgeEventHandle> handles) {
                        Log.d(LOG_TAG, "Edge event callback called");
                    }
                });

            }
        });
        // Edge Tutorial - code section (3/3) */
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}