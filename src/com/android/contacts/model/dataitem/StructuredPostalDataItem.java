/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.model.dataitem;

import android.content.ContentValues;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;

/**
 * Represents a structured postal data item, wrapping the columns in
 * {@link ContactsContract.CommonDataKinds.StructuredPostal}.
 */
public class StructuredPostalDataItem extends DataItem {

    /* package */ StructuredPostalDataItem(ContentValues values) {
        super(values);
    }

    public String getFormattedAddress() {
        return getContentValues().getAsString(StructuredPostal.FORMATTED_ADDRESS);
    }

    public String getLabel() {
        return getContentValues().getAsString(StructuredPostal.LABEL);
    }

    public String getStreet() {
        return getContentValues().getAsString(StructuredPostal.STREET);
    }

    public String getPOBox() {
        return getContentValues().getAsString(StructuredPostal.POBOX);
    }

    public String getNeighborhood() {
        return getContentValues().getAsString(StructuredPostal.NEIGHBORHOOD);
    }

    public String getCity() {
        return getContentValues().getAsString(StructuredPostal.CITY);
    }

    public String getRegion() {
        return getContentValues().getAsString(StructuredPostal.REGION);
    }

    public String getPostcode() {
        return getContentValues().getAsString(StructuredPostal.POSTCODE);
    }

    public String getCountry() {
        return getContentValues().getAsString(StructuredPostal.COUNTRY);
    }
}
