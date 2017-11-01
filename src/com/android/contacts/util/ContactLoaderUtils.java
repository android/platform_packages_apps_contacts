/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.net.Uri;
import android.provider.Contacts;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;

/**
 * Utility methods for the {@link ContactLoader}.
 */
public final class ContactLoaderUtils {

    /** Static helper, not instantiable. */
    private ContactLoaderUtils() {}

    /**
     * Transforms the given Uri and returns a Lookup-Uri that represents the contact.
     * For legacy contacts, a raw-contact lookup is performed. An {@link IllegalArgumentException}
     * can be thrown if the URI is null or the authority is not recognized.
     *
     * Do not call from the UI thread.
     */
    @SuppressWarnings("deprecation")
    public static Uri ensureIsContactUri(final ContentResolver resolver, final Uri uri)
            throws IllegalArgumentException {
        if (uri == null) throw new IllegalArgumentException("uri must not be null");

        final String authority = uri.getAuthority();

        // Current Style Uri?
        if (ContactsContract.AUTHORITY.equals(authority)) {
            final String type = resolver.getType(uri);
            // Contact-Uri? Good, return it
            if (ContactsContract.Contacts.CONTENT_ITEM_TYPE.equals(type)) {
                return uri;
            }

            // RawContact-Uri? Transform it to ContactUri
            if (RawContacts.CONTENT_ITEM_TYPE.equals(type)) {
                final long rawContactId = ContentUris.parseId(uri);
                return RawContacts.getContactLookupUri(resolver,
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
            }

            // Anything else? We don't know what this is
            throw new IllegalArgumentException("uri format is unknown");
        }

        // Legacy Style? Convert to RawContact
        final String OBSOLETE_AUTHORITY = Contacts.AUTHORITY;
        if (OBSOLETE_AUTHORITY.equals(authority)) {
            // Legacy Format. Convert to RawContact-Uri and then lookup the contact
            final long rawContactId = ContentUris.parseId(uri);
            return RawContacts.getContactLookupUri(resolver,
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));
        }

        throw new IllegalArgumentException("uri authority is unknown");
    }
}
