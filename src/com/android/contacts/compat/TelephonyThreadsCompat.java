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
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.BaseColumns;
import android.provider.Telephony;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class contains static utility methods and variables extracted from Telephony and
 * SqliteWrapper, and the methods were made visible in API level 23. In this way, we could
 * enable the corresponding functionality for pre-M devices. We need maintain this class and keep
 * it synced with Telephony and SqliteWrapper.
 */
public class TelephonyThreadsCompat {
    /**
     * Not instantiable.
     */
    private TelephonyThreadsCompat() {}

    private static final String TAG = "TelephonyThreadsCompat";

    public static long getOrCreateThreadId(Context context, String recipient) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return Telephony.Threads.getOrCreateThreadId(context, recipient);
        } else {
            return getOrCreateThreadIdInternal(context, recipient);
        }
    }

    // Below is code copied from Telephony and SqliteWrapper
    /**
     * Private {@code content://} style URL for this table. Used by
     * {@link #getOrCreateThreadId(Context, Set)}.
     */
    private static final Uri THREAD_ID_CONTENT_URI = Uri.parse("content://mms-sms/threadID");

    private static final String[] ID_PROJECTION = { BaseColumns._ID };

    /**
     * Regex pattern for names and email addresses.
     * <ul>
     *     <li><em>mailbox</em> = {@code name-addr}</li>
     *     <li><em>name-addr</em> = {@code [display-name] angle-addr}</li>
     *     <li><em>angle-addr</em> = {@code [CFWS] "<" addr-spec ">" [CFWS]}</li>
     * </ul>
     */
    private static final Pattern NAME_ADDR_EMAIL_PATTERN =
            Pattern.compile("\\s*(\"[^\"]*\"|[^<>\"]+)\\s*<([^<>]+)>\\s*");

    /**
     * Copied from {@link Telephony.Threads#getOrCreateThreadId(Context, String)}
     */
    private static long getOrCreateThreadIdInternal(Context context, String recipient) {
        Set<String> recipients = new HashSet<String>();

        recipients.add(recipient);
        return getOrCreateThreadIdInternal(context, recipients);
    }

    /**
     * Given the recipients list and subject of an unsaved message,
     * return its thread ID.  If the message starts a new thread,
     * allocate a new thread ID.  Otherwise, use the appropriate
     * existing thread ID.
     *
     * <p>Find the thread ID of the same set of recipients (in any order,
     * without any additions). If one is found, return it. Otherwise,
     * return a unique thread ID.</p>
     */
    private static long getOrCreateThreadIdInternal(Context context, Set<String> recipients) {
        Uri.Builder uriBuilder = THREAD_ID_CONTENT_URI.buildUpon();

        for (String recipient : recipients) {
            if (isEmailAddress(recipient)) {
                recipient = extractAddrSpec(recipient);
            }

            uriBuilder.appendQueryParameter("recipient", recipient);
        }

        Uri uri = uriBuilder.build();

        Cursor cursor = query(
                context.getContentResolver(), uri, ID_PROJECTION, null, null, null);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(0);
                } else {
                    Log.e(TAG, "getOrCreateThreadId returned no rows!");
                }
            } finally {
                cursor.close();
            }
        }

        Log.e(TAG, "getOrCreateThreadId failed with uri " + uri.toString());
        throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
    }

    /**
     * Copied from {@link SqliteWrapper#query}
     */
    private static Cursor query(ContentResolver resolver, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        try {
            return resolver.query(uri, projection, selection, selectionArgs, sortOrder);
        } catch (Exception e) {
            Log.e(TAG, "Catch an exception when query: ", e);
            return null;
        }
    }

    /**
     * Is the specified address an email address?
     *
     * @param address the input address to test
     * @return true if address is an email address; false otherwise.
     */
    private static boolean isEmailAddress(String address) {
        if (TextUtils.isEmpty(address)) {
            return false;
        }

        String s = extractAddrSpec(address);
        Matcher match = Patterns.EMAIL_ADDRESS.matcher(s);
        return match.matches();
    }

    /**
     * Helper method to extract email address from address string.
     */
    private static String extractAddrSpec(String address) {
        Matcher match = NAME_ADDR_EMAIL_PATTERN.matcher(address);

        if (match.matches()) {
            return match.group(2);
        }
        return address;
    }
}
