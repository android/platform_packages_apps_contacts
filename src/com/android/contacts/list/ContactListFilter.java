/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.list;

import android.accounts.Account;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import com.android.contacts.logging.ListEvent;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.GoogleAccountType;

import java.util.ArrayList;
import java.util.List;

/**
 * Contact list filter parameters.
 */
public final class ContactListFilter implements Comparable<ContactListFilter>, Parcelable {

    public static final int FILTER_TYPE_DEFAULT = -1;
    public static final int FILTER_TYPE_ALL_ACCOUNTS = -2;
    public static final int FILTER_TYPE_CUSTOM = -3;
    public static final int FILTER_TYPE_STARRED = -4;
    public static final int FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY = -5;
    public static final int FILTER_TYPE_SINGLE_CONTACT = -6;
    public static final int FILTER_TYPE_GROUP_MEMBERS = -7;
    public static final int FILTER_TYPE_DEVICE_CONTACTS = -8;

    public static final int FILTER_TYPE_ACCOUNT = 0;

    /**
     * Obsolete filter which had been used in Honeycomb. This may be stored in
     * {@link SharedPreferences}, but should be replaced with ALL filter when it is found.
     *
     * TODO: "group" filter and relevant variables are all obsolete. Remove them.
     */
    private static final int FILTER_TYPE_GROUP = 1;

    private static final String KEY_FILTER_TYPE = "filter.type";
    private static final String KEY_ACCOUNT_NAME = "filter.accountName";
    private static final String KEY_ACCOUNT_TYPE = "filter.accountType";
    private static final String KEY_DATA_SET = "filter.dataSet";

    public final int filterType;
    public final String accountType;
    public final String accountName;
    public final String dataSet;
    public final Drawable icon;
    private String mId;

    public ContactListFilter(int filterType, String accountType, String accountName, String dataSet,
            Drawable icon) {
        this.filterType = filterType;
        this.accountType = accountType;
        this.accountName = accountName;
        this.dataSet = dataSet;
        this.icon = icon;
    }

    public static ContactListFilter createFilterWithType(int filterType) {
        return new ContactListFilter(filterType, null, null, null, null);
    }

    public static ContactListFilter createAccountFilter(String accountType, String accountName,
            String dataSet, Drawable icon) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_ACCOUNT, accountType,
                accountName, dataSet, icon);
    }

    public static ContactListFilter createGroupMembersFilter(String accountType, String accountName,
            String dataSet) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_GROUP_MEMBERS, accountType,
                accountName, dataSet, /* icon */ null);
    }

    public static ContactListFilter createDeviceContactsFilter(Drawable icon) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS,
                /* accountType= */ null, /* accountName= */ null, /* dataSet= */ null, icon);
    }

    public static ContactListFilter createDeviceContactsFilter(Drawable icon,
            AccountWithDataSet account) {
        return new ContactListFilter(ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS,
                account.type, account.name, account.dataSet, icon);
    }

    /**
     * Whether the given {@link ContactListFilter} has a filter type that should be displayed as
     * the default contacts list view.
     */
    public boolean isContactsFilterType() {
        return filterType == ContactListFilter.FILTER_TYPE_DEFAULT
                || filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
                || filterType == ContactListFilter.FILTER_TYPE_CUSTOM;
    }

    /** Returns the {@link ListEvent.ListType} for the type of this filter. */
    public int toListType() {
        switch (filterType) {
            case FILTER_TYPE_DEFAULT:
                // Fall through
            case FILTER_TYPE_ALL_ACCOUNTS:
                return ListEvent.ListType.ALL_CONTACTS;
            case FILTER_TYPE_CUSTOM:
                return ListEvent.ListType.CUSTOM;
            case FILTER_TYPE_STARRED:
                return ListEvent.ListType.STARRED;
            case FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                return ListEvent.ListType.PHONE_NUMBERS;
            case FILTER_TYPE_SINGLE_CONTACT:
                return ListEvent.ListType.SINGLE_CONTACT;
            case FILTER_TYPE_ACCOUNT:
                return ListEvent.ListType.ACCOUNT;
            case FILTER_TYPE_GROUP_MEMBERS:
                return ListEvent.ListType.GROUP;
            case FILTER_TYPE_DEVICE_CONTACTS:
                return ListEvent.ListType.DEVICE;
        }
        return ListEvent.ListType.UNKNOWN_LIST;
    }


    /**
     * Returns true if this filter is based on data and may become invalid over time.
     */
    public boolean isValidationRequired() {
        return filterType == FILTER_TYPE_ACCOUNT;
    }

    @Override
    public String toString() {
        switch (filterType) {
            case FILTER_TYPE_DEFAULT:
                return "default";
            case FILTER_TYPE_ALL_ACCOUNTS:
                return "all_accounts";
            case FILTER_TYPE_CUSTOM:
                return "custom";
            case FILTER_TYPE_STARRED:
                return "starred";
            case FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                return "with_phones";
            case FILTER_TYPE_SINGLE_CONTACT:
                return "single";
            case FILTER_TYPE_ACCOUNT:
                return "account: " + accountType + (dataSet != null ? "/" + dataSet : "")
                        + " " + accountName;
            case FILTER_TYPE_GROUP_MEMBERS:
                return "group_members";
            case FILTER_TYPE_DEVICE_CONTACTS:
                return "device_contacts";
        }
        return super.toString();
    }

    @Override
    public int compareTo(ContactListFilter another) {
        int res = accountName.compareTo(another.accountName);
        if (res != 0) {
            return res;
        }

        res = accountType.compareTo(another.accountType);
        if (res != 0) {
            return res;
        }

        return filterType - another.filterType;
    }

    @Override
    public int hashCode() {
        int code = filterType;
        if (accountType != null) {
            code = code * 31 + accountType.hashCode();
        }
        if (accountName != null) {
            code = code * 31 + accountName.hashCode();
        }
        if (dataSet != null) {
            code = code * 31 + dataSet.hashCode();
        }
        return code;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other instanceof ContactListFilter)) {
            return false;
        }

        ContactListFilter otherFilter = (ContactListFilter) other;
        if (filterType != otherFilter.filterType
                || !TextUtils.equals(accountName, otherFilter.accountName)
                || !TextUtils.equals(accountType, otherFilter.accountType)
                || !TextUtils.equals(dataSet, otherFilter.dataSet)) {
            return false;
        }

        return true;
    }

    /**
     * Store the given {@link ContactListFilter} to preferences. If the requested filter is
     * of type {@link #FILTER_TYPE_SINGLE_CONTACT} then do not save it to preferences because
     * it is a temporary state.
     */
    public static void storeToPreferences(SharedPreferences prefs, ContactListFilter filter) {
        if (filter != null && filter.filterType == FILTER_TYPE_SINGLE_CONTACT) {
            return;
        }
        prefs.edit()
            .putInt(KEY_FILTER_TYPE, filter == null ? FILTER_TYPE_DEFAULT : filter.filterType)
            .putString(KEY_ACCOUNT_NAME, filter == null ? null : filter.accountName)
            .putString(KEY_ACCOUNT_TYPE, filter == null ? null : filter.accountType)
            .putString(KEY_DATA_SET, filter == null ? null : filter.dataSet)
            .apply();
    }

    /**
     * Try to obtain ContactListFilter object saved in SharedPreference.
     * If there's no info there, return ALL filter instead.
     */
    public static ContactListFilter restoreDefaultPreferences(SharedPreferences prefs) {
        ContactListFilter filter = restoreFromPreferences(prefs);
        if (filter == null) {
            filter = ContactListFilter.createFilterWithType(FILTER_TYPE_ALL_ACCOUNTS);
        }
        // "Group" filter is obsolete and thus is not exposed anymore. The "single contact mode"
        // should also not be stored in preferences anymore since it is a temporary state.
        if (filter.filterType == FILTER_TYPE_GROUP ||
                filter.filterType == FILTER_TYPE_SINGLE_CONTACT) {
            filter = ContactListFilter.createFilterWithType(FILTER_TYPE_ALL_ACCOUNTS);
        }
        return filter;
    }

    private static ContactListFilter restoreFromPreferences(SharedPreferences prefs) {
        int filterType = prefs.getInt(KEY_FILTER_TYPE, FILTER_TYPE_DEFAULT);
        if (filterType == FILTER_TYPE_DEFAULT) {
            return null;
        }

        String accountName = prefs.getString(KEY_ACCOUNT_NAME, null);
        String accountType = prefs.getString(KEY_ACCOUNT_TYPE, null);
        String dataSet = prefs.getString(KEY_DATA_SET, null);
        return new ContactListFilter(filterType, accountType, accountName, dataSet, null);
    }


    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(filterType);
        dest.writeString(accountName);
        dest.writeString(accountType);
        dest.writeString(dataSet);
    }

    public static final Parcelable.Creator<ContactListFilter> CREATOR =
            new Parcelable.Creator<ContactListFilter>() {
        @Override
        public ContactListFilter createFromParcel(Parcel source) {
            int filterType = source.readInt();
            String accountName = source.readString();
            String accountType = source.readString();
            String dataSet = source.readString();
            return new ContactListFilter(filterType, accountType, accountName, dataSet, null);
        }

        @Override
        public ContactListFilter[] newArray(int size) {
            return new ContactListFilter[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns a string that can be used as a stable persistent identifier for this filter.
     */
    public String getId() {
        if (mId == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(filterType);
            if (accountType != null) {
                sb.append('-').append(accountType);
            }
            if (dataSet != null) {
                sb.append('/').append(dataSet);
            }
            if (accountName != null) {
                sb.append('-').append(accountName.replace('-', '_'));
            }
            mId = sb.toString();
        }
        return mId;
    }

    /**
     * Adds the account query parameters to the given {@code uriBuilder}.
     *
     * @throws IllegalStateException if the filter type is not {@link #FILTER_TYPE_ACCOUNT} or
     * {@link #FILTER_TYPE_GROUP_MEMBERS}.
     */
    public Uri.Builder addAccountQueryParameterToUrl(Uri.Builder uriBuilder) {
        if (filterType != FILTER_TYPE_ACCOUNT
                && filterType != FILTER_TYPE_GROUP_MEMBERS) {
            throw new IllegalStateException(
                    "filterType must be FILTER_TYPE_ACCOUNT or FILER_TYPE_GROUP_MEMBERS");
        }
        // null account names are not valid, see ContactsProvider2#appendAccountFromParameter
        if (accountName != null) {
            uriBuilder.appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName);
            uriBuilder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType);
        }
        if (dataSet != null) {
            uriBuilder.appendQueryParameter(RawContacts.DATA_SET, dataSet);
        }
        return uriBuilder;
    }

    public AccountWithDataSet toAccountWithDataSet() {
        if (filterType == FILTER_TYPE_ACCOUNT || filterType == FILTER_TYPE_DEVICE_CONTACTS) {
            return new AccountWithDataSet(accountName, accountType, dataSet);
        } else {
            throw new IllegalStateException("Cannot create Account from filter type " +
                    filterTypeToString(filterType));
        }
    }

    public String toDebugString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("[filter type: " + filterType + " (" + filterTypeToString(filterType) + ")");
        if (filterType == FILTER_TYPE_ACCOUNT) {
            builder.append(", accountType: " + accountType)
                    .append(", accountName: " + accountName)
                    .append(", dataSet: " + dataSet);
        }
        builder.append(", icon: " + icon + "]");
        return builder.toString();
    }

    public static final String filterTypeToString(int filterType) {
        switch (filterType) {
            case FILTER_TYPE_DEFAULT:
                return "FILTER_TYPE_DEFAULT";
            case FILTER_TYPE_ALL_ACCOUNTS:
                return "FILTER_TYPE_ALL_ACCOUNTS";
            case FILTER_TYPE_CUSTOM:
                return "FILTER_TYPE_CUSTOM";
            case FILTER_TYPE_STARRED:
                return "FILTER_TYPE_STARRED";
            case FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY:
                return "FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY";
            case FILTER_TYPE_SINGLE_CONTACT:
                return "FILTER_TYPE_SINGLE_CONTACT";
            case FILTER_TYPE_ACCOUNT:
                return "FILTER_TYPE_ACCOUNT";
            case FILTER_TYPE_GROUP_MEMBERS:
                return "FILTER_TYPE_GROUP_MEMBERS";
            case FILTER_TYPE_DEVICE_CONTACTS:
                return "FILTER_TYPE_DEVICE_CONTACTS";
            default:
                return "(unknown)";
        }
    }

    public boolean isSyncable() {
        return isGoogleAccountType() && filterType == FILTER_TYPE_ACCOUNT;
    }

    /**
     * Returns true if this ContactListFilter contains at least one Google account.
     * (see {@link #isGoogleAccountType)
     */
    public boolean isSyncable(List<AccountWithDataSet> accounts) {
        if (isSyncable()) {
            return true;
        }
        // Since we don't know which group is selected until the actual contacts loading, we
        // consider a custom filter syncable as long as there is a Google account on the device,
        // and don't check if there is any group that belongs to a Google account is selected.
        if (filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
                || filterType == ContactListFilter.FILTER_TYPE_CUSTOM
                || filterType == ContactListFilter.FILTER_TYPE_DEFAULT) {
            if (accounts != null && accounts.size() > 0) {
                // If we're showing all contacts and there is any Google account on the device then
                // we're syncable.
                for (AccountWithDataSet account : accounts) {
                    if (GoogleAccountType.ACCOUNT_TYPE.equals(account.type)
                            && account.dataSet == null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean shouldShowSyncState() {
        return (isGoogleAccountType() && filterType == ContactListFilter.FILTER_TYPE_ACCOUNT)
                || filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
                || filterType == ContactListFilter.FILTER_TYPE_CUSTOM
                || filterType == ContactListFilter.FILTER_TYPE_DEFAULT;
    }

    /**
     * Returns the Google accounts (see {@link #isGoogleAccountType) for this ContactListFilter.
     */
    public List<Account> getSyncableAccounts(List<AccountWithDataSet> accounts) {
        final List<Account> syncableAccounts = new ArrayList<>();

        if (isGoogleAccountType() && filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
            syncableAccounts.add(new Account(accountName, accountType));
        } else if (filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
                || filterType == ContactListFilter.FILTER_TYPE_CUSTOM
                || filterType == ContactListFilter.FILTER_TYPE_DEFAULT) {
            if (accounts != null && accounts.size() > 0) {
                for (AccountWithDataSet account : accounts) {
                    if (GoogleAccountType.ACCOUNT_TYPE.equals(account.type)
                            && account.dataSet == null) {
                        syncableAccounts.add(new Account(account.name, account.type));
                    }
                }
            }
        }
        return syncableAccounts;
    }

    /**
     * Returns true if this ContactListFilter is Google account type. (i.e. where
     * accountType = "com.google" and dataSet = null)
     */
    public boolean isGoogleAccountType() {
        return GoogleAccountType.ACCOUNT_TYPE.equals(accountType) && dataSet == null;
    }
}
