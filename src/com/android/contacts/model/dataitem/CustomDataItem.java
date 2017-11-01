/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.model.dataitem;

import android.content.ContentValues;
import android.provider.ContactsContract.Data;

/**
 * Represents a custom field data item.
 */
public class CustomDataItem extends DataItem {

    /**
     * MIME type for custom field data defined in Contact Provider.
     */
    public static final String MIMETYPE_CUSTOM_FIELD =
            "vnd.com.google.cursor.item/contact_user_defined_field";

    CustomDataItem(ContentValues values) {super(values);}

    public String getSummary() {
        return getContentValues().getAsString(Data.DATA1);
    }

    public String getContent() {
        return getContentValues().getAsString(Data.DATA2);
    }
}
