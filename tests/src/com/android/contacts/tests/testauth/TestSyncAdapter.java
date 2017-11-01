/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.android.contacts.tests.testauth;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import java.util.ArrayList;

/**
 * Simple (minimal) sync adapter.
 *
 */
public class TestSyncAdapter extends AbstractThreadedSyncAdapter {

    private static final String TEXT_CONTENT_ITEM_TYPE =
            "vnd.android.cursor.item/vnd.contactstest.profile";

    private final Context mContext;

    public TestSyncAdapter(Context context, boolean autoInitialize) {
        super(context, autoInitialize);
        mContext = context.getApplicationContext();
    }

    /**
     * Doesn't actually sync, but sweep up all existing local-only contacts.
     */
    @Override
    public void onPerformSync(Account account, Bundle extras, String authority,
            ContentProviderClient provider, SyncResult syncResult) {
        if (Log.isLoggable(TestauthConstants.LOG_TAG, Log.VERBOSE)) {
            Log.v(TestauthConstants.LOG_TAG, "TestSyncAdapter.onPerformSync() account=" + account);
        }

        final ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        final ContentResolver contentResolver = mContext.getContentResolver();
        final Cursor cursor = contentResolver.query(RawContacts.CONTENT_URI,
                new String[] { RawContacts._ID },
                RawContacts.ACCOUNT_NAME + " IS NULL AND " + RawContacts.ACCOUNT_TYPE + " IS NULL",
                null, null);
        try {
            while (cursor.moveToNext()) {
                final String rawContactId = Long.toString(cursor.getLong(0));

                // Claim all local-only contacts for the test account
                ops.add(ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_NAME, account.name)
                        .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                        .withSelection(RawContacts._ID+"=?", new String[] { rawContactId })
                        .build());

                // Create custom QuickContact action data rows
                final Uri dataUri = Data.CONTENT_URI.buildUpon()
                        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                        .build();
                ops.add(ContentProviderOperation.newInsert(dataUri)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, TEXT_CONTENT_ITEM_TYPE)
                        .withValue(Data.DATA3, "Contacts test action")
                        .withValue(Data.DATA5, "view")
                        .build());
            }
        } finally {
            cursor.close();
        }
        if (ops.isEmpty()) return;

        // TODO: Clear isDirty flag
        // TODO: Remove isDeleted raw contacts

        if (Log.isLoggable(TestauthConstants.LOG_TAG, Log.VERBOSE)) {
            Log.v(TestauthConstants.LOG_TAG, "Claiming " + ops.size() + " local raw contacts");
            for (ContentProviderOperation op : ops) {
                Log.v(TestauthConstants.LOG_TAG, op.toString());
            }
        }
        try {
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (Exception e ) {
            Log.e(TestauthConstants.LOG_TAG, "Failed to claim local raw contacts", e);
        }
    }
}
