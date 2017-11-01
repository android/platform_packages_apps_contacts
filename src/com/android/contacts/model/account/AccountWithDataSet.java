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

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.preference.ContactsPreferences;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wrapper for an account that includes a data set (which may be null).
 */
public class AccountWithDataSet implements Parcelable {
    private static final String STRINGIFY_SEPARATOR = "\u0001";
    private static final String ARRAY_STRINGIFY_SEPARATOR = "\u0002";

    private static final Pattern STRINGIFY_SEPARATOR_PAT =
            Pattern.compile(Pattern.quote(STRINGIFY_SEPARATOR));
    private static final Pattern ARRAY_STRINGIFY_SEPARATOR_PAT =
            Pattern.compile(Pattern.quote(ARRAY_STRINGIFY_SEPARATOR));

    public final String name;
    public final String type;
    public final String dataSet;
    private final AccountTypeWithDataSet mAccountTypeWithDataSet;

    private static final String[] ID_PROJECTION = new String[] {BaseColumns._ID};
    private static final Uri RAW_CONTACTS_URI_LIMIT_1 = RawContacts.CONTENT_URI.buildUpon()
            .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, "1").build();

    public static final String LOCAL_ACCOUNT_SELECTION = RawContacts.ACCOUNT_TYPE + " IS NULL AND "
            + RawContacts.ACCOUNT_NAME + " IS NULL AND "
            + RawContacts.DATA_SET + " IS NULL";

    public AccountWithDataSet(String name, String type, String dataSet) {
        this.name = emptyToNull(name);
        this.type = emptyToNull(type);
        this.dataSet = emptyToNull(dataSet);
        mAccountTypeWithDataSet = AccountTypeWithDataSet.get(type, dataSet);
    }

    private static final String emptyToNull(String text) {
        return TextUtils.isEmpty(text) ? null : text;
    }

    public AccountWithDataSet(Parcel in) {
        this.name = in.readString();
        this.type = in.readString();
        this.dataSet = in.readString();
        mAccountTypeWithDataSet = AccountTypeWithDataSet.get(type, dataSet);
    }

    public boolean isNullAccount() {
        return name == null && type == null && dataSet == null;
    }

    public static AccountWithDataSet getNullAccount() {
        return new AccountWithDataSet(null, null, null);
    }

    public Account getAccountOrNull() {
        if (name != null && type != null) {
            return new Account(name, type);
        }
        return null;
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(type);
        dest.writeString(dataSet);
    }

    // For Parcelable
    public static final Creator<AccountWithDataSet> CREATOR = new Creator<AccountWithDataSet>() {
        public AccountWithDataSet createFromParcel(Parcel source) {
            return new AccountWithDataSet(source);
        }

        public AccountWithDataSet[] newArray(int size) {
            return new AccountWithDataSet[size];
        }
    };

    public AccountTypeWithDataSet getAccountTypeWithDataSet() {
        return mAccountTypeWithDataSet;
    }

    /**
     * Return {@code true} if this account has any contacts in the database.
     * Touches DB.  Don't use in the UI thread.
     */
    public boolean hasData(Context context) {
        String selection;
        String[] args = null;
        if (isNullAccount()) {
            selection = LOCAL_ACCOUNT_SELECTION;
        } else {
            final String BASE_SELECTION =
                    RawContacts.ACCOUNT_TYPE + " = ?" + " AND " + RawContacts.ACCOUNT_NAME + " = ?";
            if (TextUtils.isEmpty(dataSet)) {
                selection = BASE_SELECTION + " AND " + RawContacts.DATA_SET + " IS NULL";
                args = new String[] {type, name};
            } else {
                selection = BASE_SELECTION + " AND " + RawContacts.DATA_SET + " = ?";
                args = new String[] {type, name, dataSet};
            }
        }
        selection += " AND " + RawContacts.DELETED + "=0";

        final Cursor c = context.getContentResolver().query(RAW_CONTACTS_URI_LIMIT_1,
                ID_PROJECTION, selection, args, null);
        if (c == null) return false;
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    public boolean equals(Object obj) {
        if (obj instanceof AccountWithDataSet) {
            AccountWithDataSet other = (AccountWithDataSet) obj;
            return Objects.equal(name, other.name)
                    && Objects.equal(type, other.type)
                    && Objects.equal(dataSet, other.dataSet);
        }
        return false;
    }

    public int hashCode() {
        int result = 17;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (dataSet != null ? dataSet.hashCode() : 0);
        return result;
    }

    public String toString() {
        return "AccountWithDataSet {name=" + name + ", type=" + type + ", dataSet=" + dataSet + "}";
    }

    private static StringBuilder addStringified(StringBuilder sb, AccountWithDataSet account) {
        if (!TextUtils.isEmpty(account.name)) sb.append(account.name);
        sb.append(STRINGIFY_SEPARATOR);
        if (!TextUtils.isEmpty(account.type)) sb.append(account.type);
        sb.append(STRINGIFY_SEPARATOR);
        if (!TextUtils.isEmpty(account.dataSet)) sb.append(account.dataSet);

        return sb;
    }

    /**
     * Pack the instance into a string.
     */
    public String stringify() {
        return addStringified(new StringBuilder(), this).toString();
    }

    /**
     * Returns a {@link ContentProviderOperation} that will create a RawContact in this account
     */
    public ContentProviderOperation newRawContactOperation() {
        final ContentProviderOperation.Builder builder =
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                        .withValue(RawContacts.ACCOUNT_NAME, name)
                        .withValue(RawContacts.ACCOUNT_TYPE, type);
        if (dataSet != null) {
            builder.withValue(RawContacts.DATA_SET, dataSet);
        }
        return builder.build();
    }

    /**
     * Unpack a string created by {@link #stringify}.
     *
     * @throws IllegalArgumentException if it's an invalid string.
     */
    public static AccountWithDataSet unstringify(String s) {
        final String[] array = STRINGIFY_SEPARATOR_PAT.split(s, 3);
        if (array.length < 3) {
            throw new IllegalArgumentException("Invalid string " + s);
        }
        return new AccountWithDataSet(array[0], array[1],
                TextUtils.isEmpty(array[2]) ? null : array[2]);
    }

    /**
     * Pack a list of {@link AccountWithDataSet} into a string.
     */
    public static String stringifyList(List<AccountWithDataSet> accounts) {
        final StringBuilder sb = new StringBuilder();

        for (AccountWithDataSet account : accounts) {
            if (sb.length() > 0) {
                sb.append(ARRAY_STRINGIFY_SEPARATOR);
            }
            addStringified(sb, account);
        }

        return sb.toString();
    }

    /**
     * Unpack a list of {@link AccountWithDataSet} into a string.
     *
     * @throws IllegalArgumentException if it's an invalid string.
     */
    public static List<AccountWithDataSet> unstringifyList(String s) {
        final ArrayList<AccountWithDataSet> ret = Lists.newArrayList();
        if (TextUtils.isEmpty(s)) {
            return ret;
        }

        final String[] array = ARRAY_STRINGIFY_SEPARATOR_PAT.split(s);

        for (int i = 0; i < array.length; i++) {
            ret.add(unstringify(array[i]));
        }

        return ret;
    }
}

