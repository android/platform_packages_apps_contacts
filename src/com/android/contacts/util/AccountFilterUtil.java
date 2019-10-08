/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.content.ActivityNotFoundException;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.list.AccountFilterActivity;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.Contact;
import com.android.contacts.model.account.AccountDisplayInfo;
import com.android.contacts.model.account.AccountDisplayInfoFactory;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.concurrent.ContactsExecutors;
import com.android.contacts.util.concurrent.ListenableFutureLoader;
import com.android.contactsbind.ObjectFactory;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Utility class for account filter manipulation.
 */
public class AccountFilterUtil {
    private static final String TAG = AccountFilterUtil.class.getSimpleName();

     /**
      * Launches account filter setting Activity using
      * {@link Fragment#startActivityForResult(Intent, int)}.
      *
      * @param requestCode requestCode for {@link Activity#startActivityForResult(Intent, int)}
      * @param currentFilter currently-selected filter, so that it can be displayed as activated.
      */
     public static void startAccountFilterActivityForResult(
             Fragment fragment, int requestCode, ContactListFilter currentFilter) {
         final Activity activity = fragment.getActivity();
         if (activity != null) {
             final Intent intent = new Intent(activity, AccountFilterActivity.class);
             fragment.startActivityForResult(intent, requestCode);
         } else {
             Log.w(TAG, "getActivity() returned null. Ignored");
         }
     }

    /**
     * Useful method to handle onActivityResult() for
     * {@link #startAccountFilterActivityForResult(Fragment, int, ContactListFilter)}.
     *
     * This will update filter via a given ContactListFilterController.
     */
    public static void handleAccountFilterResult(
            ContactListFilterController filterController, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            final ContactListFilter filter = (ContactListFilter)
                    data.getParcelableExtra(AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER);
            if (filter == null) {
                return;
            }
            if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                filterController.selectCustomFilter();
            } else {
                filterController.setContactListFilter(filter, /* persistent */
                        filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
            }
        }
    }

    /**
     * Loads a list of contact list filters
     */
    public static class FilterLoader extends ListenableFutureLoader<List<ContactListFilter>> {
        private AccountTypeManager mAccountTypeManager;
        private DeviceLocalAccountTypeFactory mDeviceLocalFactory;

        public FilterLoader(Context context) {
            super(context, new IntentFilter(AccountTypeManager.BROADCAST_ACCOUNTS_CHANGED));
            mAccountTypeManager = AccountTypeManager.getInstance(context);
            mDeviceLocalFactory = ObjectFactory.getDeviceLocalAccountTypeFactory(context);
        }


        @Override
        protected ListenableFuture<List<ContactListFilter>> loadData() {
            return Futures.transform(mAccountTypeManager.filterAccountsAsync(
                    AccountTypeManager.writableFilter()),
                    new Function<List<AccountInfo>, List<ContactListFilter>>() {
                        @Override
                        public List<ContactListFilter> apply(List<AccountInfo> input) {
                            return getFiltersForAccounts(input);
                        }
                    }, ContactsExecutors.getDefaultThreadPoolExecutor());
        }

        private List<ContactListFilter> getFiltersForAccounts(List<AccountInfo> accounts) {
            final ArrayList<ContactListFilter> accountFilters = new ArrayList<>();
            AccountInfo.sortAccounts(getDefaultAccount(getContext()), accounts);

            for (AccountInfo accountInfo : accounts) {
                final AccountType accountType = accountInfo.getType();
                final AccountWithDataSet account = accountInfo.getAccount();
                if ((accountType.isExtension() ||
                        DeviceLocalAccountTypeFactory.Util.isLocalAccountType(
                                mDeviceLocalFactory, account.type)) &&
                        !account.hasData(getContext())) {
                    // Hide extensions and device accounts with no raw_contacts.
                    continue;
                }
                final Drawable icon = accountType != null ?
                        accountType.getDisplayIcon(getContext()) : null;
                if (DeviceLocalAccountTypeFactory.Util.isLocalAccountType(
                        mDeviceLocalFactory, account.type)) {
                    accountFilters.add(ContactListFilter.createDeviceContactsFilter(icon, account));
                } else {
                    accountFilters.add(ContactListFilter.createAccountFilter(
                            account.type, account.name, account.dataSet, icon));
                }
            }

            return accountFilters;
        }
    }

    private static AccountWithDataSet getDefaultAccount(Context context) {
        return new ContactsPreferences(context).getDefaultAccount();
    }

    /**
     * Returns a {@link ContactListFilter} of type
     * {@link ContactListFilter#FILTER_TYPE_ALL_ACCOUNTS}, or if a custom "Contacts to display"
     * filter has been set, then one of type {@link ContactListFilter#FILTER_TYPE_CUSTOM}.
     */
    public static ContactListFilter createContactsFilter(Context context) {
        final int filterType =
                ContactListFilterController.getInstance(context).isCustomFilterPersisted()
                        ? ContactListFilter.FILTER_TYPE_CUSTOM
                        : ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS;
        return ContactListFilter.createFilterWithType(filterType);
    }

    /**
     * Start editor intent; and if filter is an account filter, we pass account info to editor so
     * as to create a contact in that account.
     */
    public static void startEditorIntent(Context context, Intent src, ContactListFilter filter) {
        final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
        intent.putExtras(src);

        // If we are in account view, we pass the account explicitly in order to
        // create contact in the account. This will prevent the default account dialog
        // from being displayed.
        if (!isAllContactsFilter(filter) && filter.accountName != null
                && filter.accountType != null) {
            final Account account = new Account(filter.accountName, filter.accountType);
            intent.putExtra(Intents.Insert.EXTRA_ACCOUNT, account);
            intent.putExtra(Intents.Insert.EXTRA_DATA_SET, filter.dataSet);
        } else if (isDeviceContactsFilter(filter)) {
            intent.putExtra(ContactEditorActivity.EXTRA_ACCOUNT_WITH_DATA_SET,
                    filter.toAccountWithDataSet());
        }

        try {
            ImplicitIntentsUtil.startActivityInApp(context, intent);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(context, R.string.missing_app, Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isAllContactsFilter(ContactListFilter filter) {
        return filter != null && filter.isContactsFilterType();
    }

    public static boolean isDeviceContactsFilter(ContactListFilter filter) {
        return filter.filterType == ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS;
    }

    /**
     * Returns action bar title for filter and returns default title "Contacts" if filter is empty.
     */
    public static String getActionBarTitleForFilter(Context context, ContactListFilter filter) {
        if (filter.filterType == ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS) {
            return context.getString(R.string.account_phone);
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT &&
                !TextUtils.isEmpty(filter.accountName)) {
            return getActionBarTitleForAccount(context, filter);
        }
        return context.getString(R.string.contactsList);
    }

    private static String getActionBarTitleForAccount(Context context, ContactListFilter filter) {
        final AccountInfo info = AccountTypeManager.getInstance(context)
                .getAccountInfoForAccount(filter.toAccountWithDataSet());
        if (info == null) {
            return context.getString(R.string.contactsList);
        }

        if (info.hasGoogleAccountType()) {
            return context.getString(R.string.title_from_google);
        }
        return context.getString(R.string.title_from_other_accounts,
                info.getNameLabel().toString());
    }
}
