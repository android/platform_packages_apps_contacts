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
 * limitations under the License
 */

package com.android.contacts.database;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * Static methods to update contact information.
 */
public class ContactUpdateUtils {

    private static final String TAG = ContactUpdateUtils.class.getSimpleName();

    public static void setSuperPrimary(Context context, long dataId) {
        if (dataId == -1) {
            Log.e(TAG, "Invalid arguments for setSuperPrimary request");
            return;
        }

        // Update the primary values in the data record.
        ContentValues values = new ContentValues(2);
        values.put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
        values.put(ContactsContract.Data.IS_PRIMARY, 1);

        context.getContentResolver().update(
                ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId),
                values, null, null);
    }
}
