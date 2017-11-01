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

package com.android.contacts.model.account;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import com.google.common.base.Objects;


/**
 * Encapsulates an "account type" string and a "data set" string.
 */
public class AccountTypeWithDataSet {

    private static final String[] ID_PROJECTION = new String[] {BaseColumns._ID};
    private static final Uri RAW_CONTACTS_URI_LIMIT_1 = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, "1").build();

    /** account type.  Can be null for fallback type. */
    public final String accountType;

    /** dataSet may be null, but never be "". */
    public final String dataSet;

    private AccountTypeWithDataSet(String accountType, String dataSet) {
        this.accountType = TextUtils.isEmpty(accountType) ? null : accountType;
        this.dataSet = TextUtils.isEmpty(dataSet) ? null : dataSet;
    }

    public static AccountTypeWithDataSet get(String accountType, String dataSet) {
        return new AccountTypeWithDataSet(accountType, dataSet);
    }

    /**
     * Return true if there are any contacts in the database with this account type and data set.
     * Touches DB. Don't use in the UI thread.
     */
    public boolean hasData(Context context) {
        final String BASE_SELECTION = RawContacts.ACCOUNT_TYPE + " = ?";
        final String selection;
        final String[] args;
        if (TextUtils.isEmpty(dataSet)) {
            selection = BASE_SELECTION + " AND " + RawContacts.DATA_SET + " IS NULL";
            args = new String[] {accountType};
        } else {
            selection = BASE_SELECTION + " AND " + RawContacts.DATA_SET + " = ?";
            args = new String[] {accountType, dataSet};
        }

        final Cursor c = context.getContentResolver().query(RAW_CONTACTS_URI_LIMIT_1,
                ID_PROJECTION, selection, args, null);
        if (c == null) return false;
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AccountTypeWithDataSet)) return false;

        AccountTypeWithDataSet other = (AccountTypeWithDataSet) o;
        return Objects.equal(accountType, other.accountType)
                && Objects.equal(dataSet, other.dataSet);
    }

    @Override
    public int hashCode() {
        return (accountType == null ? 0 : accountType.hashCode())
                ^ (dataSet == null ? 0 : dataSet.hashCode());
    }

    @Override
    public String toString() {
        return "[" + accountType + "/" + dataSet + "]";
    }
}
