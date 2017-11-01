/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.compat;

import android.content.ContentResolver;
import android.provider.ContactsContract;
import android.provider.ContactsContract.PinnedPositions;

/**
 * Compatibility class for {@link android.provider.ContactsContract.PinnedPositions}
 */
public class PinnedPositionsCompat {
    /**
     * Not instantiable.
     */
    private PinnedPositionsCompat() {
    }

    /**
     * copied from android.provider.ContactsContract.PinnedPositions#UNDEMOTE_METHOD
     */
    private static final String UNDEMOTE_METHOD = "undemote";

    /**
     * Compatibility method for {@link android.provider.ContactsContract.PinnedPositions#undemote}
     */
    public static void undemote(ContentResolver contentResolver, long contactId) {
        if (contentResolver == null) {
            return;
        }
        if (CompatUtils.isLollipopCompatible()) {
            PinnedPositions.undemote(contentResolver, contactId);
        } else {
            // copied from android.provider.ContactsContract.PinnedPositions.undemote()
            contentResolver.call(ContactsContract.AUTHORITY_URI, UNDEMOTE_METHOD,
                    String.valueOf(contactId), null);
        }
    }

}
