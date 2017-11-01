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

import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;

import com.android.contacts.ContactsUtils;

/**
 * Compatibility class for {@link ContactsContract.Contacts}
 */
public class ContactsCompat {
    /**
     * Not instantiable.
     */
    private ContactsCompat() {
    }

    // TODO: Use N APIs
    private static final Uri ENTERPRISE_CONTENT_FILTER_URI =
            Uri.withAppendedPath(Contacts.CONTENT_URI, "filter_enterprise");

    // Copied from ContactsContract.Contacts#ENTERPRISE_CONTACT_ID_BASE, which is hidden.
    private static final long ENTERPRISE_CONTACT_ID_BASE = 1000000000;

    public static Uri getContentUri() {
        if (ContactsUtils.FLAG_N_FEATURE) {
            return ENTERPRISE_CONTENT_FILTER_URI;
        }
        return Contacts.CONTENT_FILTER_URI;
    }

    /**
     * Return {@code true} if a contact ID is from the contacts provider on the enterprise profile.
     */
    public static boolean isEnterpriseContactId(long contactId) {
        if (CompatUtils.isLollipopCompatible()) {
            return Contacts.isEnterpriseContactId(contactId);
        } else {
            // copied from ContactsContract.Contacts.isEnterpriseContactId
            return (contactId >= ENTERPRISE_CONTACT_ID_BASE) &&
                    (contactId < ContactsContract.Profile.MIN_ID);
        }
    }
}
