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

import android.net.Uri;
import android.provider.ContactsContract;

import java.util.List;

/**
 * Utility methods for dealing with URIs.
 */
public class UriUtils {
    /** Static helper, not instantiable. */
    private UriUtils() {}

    /** Checks whether two URI are equal, taking care of the case where either is null. */
    public static boolean areEqual(Uri uri1, Uri uri2) {
        if (uri1 == null && uri2 == null) {
            return true;
        }
        if (uri1 == null || uri2 == null) {
            return false;
        }
        return uri1.equals(uri2);
    }

    /** Parses a string into a URI and returns null if the given string is null. */
    public static Uri parseUriOrNull(String uriString) {
        if (uriString == null) {
            return null;
        }
        return Uri.parse(uriString);
    }

    /** Converts a URI into a string, returns null if the given URI is null. */
    public static String uriToString(Uri uri) {
        return uri == null ? null : uri.toString();
    }

    public static boolean isEncodedContactUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        final String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment == null) {
            return false;
        }
        return lastPathSegment.equals(Constants.LOOKUP_URI_ENCODED);
    }

    /**
     * @return {@code uri} as-is if the authority is of contacts provider.  Otherwise
     * or {@code uri} is null, return null otherwise
     */
    public static Uri nullForNonContactsUri(Uri uri) {
        if (uri == null) {
            return null;
        }
        return ContactsContract.AUTHORITY.equals(uri.getAuthority()) ? uri : null;
    }

    /**
     * Parses the given URI to determine the original lookup key of the contact.
     */
    public static String getLookupKeyFromUri(Uri lookupUri) {
        // Would be nice to be able to persist the lookup key somehow to avoid having to parse
        // the uri entirely just to retrieve the lookup key, but every uri is already parsed
        // once anyway to check if it is an encoded JSON uri, so this has negligible effect
        // on performance.
        if (lookupUri != null && !UriUtils.isEncodedContactUri(lookupUri)) {
            final List<String> segments = lookupUri.getPathSegments();
            // This returns the third path segment of the uri, where the lookup key is located.
            // See {@link android.provider.ContactsContract.Contacts#CONTENT_LOOKUP_URI}.
            return (segments.size() < 3) ? null : Uri.encode(segments.get(2));
        } else {
            return null;
        }
    }
}
