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
 * limitations under the License.
 */
package com.android.contacts.model;

import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import androidx.annotation.VisibleForTesting;

import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.DeviceLocalAccountTypeFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Attempts to create accounts for "Device" contacts by querying
 * CP2 for records with {@link android.provider.ContactsContract.RawContacts#ACCOUNT_TYPE} columns
 * that do not exist for any account returned by {@link AccountManager#getAccounts()}
 *
 * This class should be used from a background thread since it does DB queries
 */
public class Cp2DeviceLocalAccountLocator extends DeviceLocalAccountLocator {

    // Note this class is assuming ACCOUNT_NAME and ACCOUNT_TYPE have same values in
    // RawContacts, Groups, and Settings. This assumption simplifies the code somewhat and it
    // is true right now and unlikely to ever change.
    @VisibleForTesting
    static String[] PROJECTION = new String[] {
            ContactsContract.RawContacts.ACCOUNT_NAME, ContactsContract.RawContacts.ACCOUNT_TYPE,
            ContactsContract.RawContacts.DATA_SET
    };

    private static final int COL_NAME = 0;
    private static final int COL_TYPE = 1;
    private static final int COL_DATA_SET = 2;

    private final ContentResolver mResolver;
    private final DeviceLocalAccountTypeFactory mAccountTypeFactory;

    private final String mSelection;
    private final String[] mSelectionArgs;

    public Cp2DeviceLocalAccountLocator(ContentResolver contentResolver,
            DeviceLocalAccountTypeFactory factory,
            Set<String> knownAccountTypes) {
        mResolver = contentResolver;
        mAccountTypeFactory = factory;

        mSelection = getSelection(knownAccountTypes);
        mSelectionArgs = getSelectionArgs(knownAccountTypes);
    }

    @Override
    public List<AccountWithDataSet> getDeviceLocalAccounts() {

        final Set<AccountWithDataSet> localAccounts = new HashSet<>();

        // Many device accounts have default groups associated with them.
        addAccountsFromQuery(ContactsContract.Groups.CONTENT_URI, localAccounts);
        addAccountsFromQuery(ContactsContract.Settings.CONTENT_URI, localAccounts);
        addAccountsFromQuery(ContactsContract.RawContacts.CONTENT_URI, localAccounts);

        return new ArrayList<>(localAccounts);
    }

    private void addAccountsFromQuery(Uri uri, Set<AccountWithDataSet> accounts) {
        final Cursor cursor = mResolver.query(uri, PROJECTION, mSelection, mSelectionArgs, null);

        if (cursor == null) return;

        try {
            addAccountsFromCursor(cursor, accounts);
        } finally {
            cursor.close();
        }
    }

    private void addAccountsFromCursor(Cursor cursor, Set<AccountWithDataSet> accounts) {
        while (cursor.moveToNext()) {
            final String name = cursor.getString(COL_NAME);
            final String type = cursor.getString(COL_TYPE);
            final String dataSet = cursor.getString(COL_DATA_SET);

            if (DeviceLocalAccountTypeFactory.Util.isLocalAccountType(
                    mAccountTypeFactory, type)) {
                accounts.add(new AccountWithDataSet(name, type, dataSet));
            }
        }
    }

    @VisibleForTesting
    public String getSelection() {
        return mSelection;
    }

    @VisibleForTesting
    public String[] getSelectionArgs() {
        return mSelectionArgs;
    }

    private static String getSelection(Set<String> knownAccountTypes) {
        final StringBuilder sb = new StringBuilder()
                .append(ContactsContract.RawContacts.ACCOUNT_TYPE).append(" IS NULL");
        if (knownAccountTypes.isEmpty()) {
            return sb.toString();
        }
        sb.append(" OR ").append(ContactsContract.RawContacts.ACCOUNT_TYPE).append(" NOT IN (");
        for (String ignored : knownAccountTypes) {
            sb.append("?,");
        }
        // Remove trailing ','
        sb.deleteCharAt(sb.length() - 1).append(')');
        return sb.toString();
    }

    private static String[] getSelectionArgs(Set<String> knownAccountTypes) {
        if (knownAccountTypes.isEmpty()) return null;

        return knownAccountTypes.toArray(new String[knownAccountTypes.size()]);
    }
}
