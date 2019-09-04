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
package com.android.contacts.tests;

import static junit.framework.Assert.assertTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.provider.ContactsContract.RawContacts;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.test.InstrumentationRegistry;

import com.android.contacts.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("MissingPermission")
public class AccountsTestHelper {
    private static final String TAG = "AccountsTestHelper";

    public static final String TEST_ACCOUNT_TYPE = "com.android.contacts.tests.testauth.basic";

    private final Context mContext;
    private final AccountManager mAccountManager;
    private final ContentResolver mResolver;

    private List<Account> mAddedAccounts;

    public AccountsTestHelper() {
        // Use context instead of target context because the test package has the permissions needed
        // to add and remove accounts.
        this(InstrumentationRegistry.getContext());
    }

    public AccountsTestHelper(Context context) {
        mContext = context;
        mAccountManager = AccountManager.get(mContext);
        mResolver = mContext.getContentResolver();
        mAddedAccounts = new ArrayList<>();
    }

    public void addTestAccount(AccountWithDataSet account) {
        Account newAccount = new Account(account.name, account.type);
        assertTrue(mAccountManager.addAccountExplicitly(newAccount, null, null));
        mAddedAccounts.add(newAccount);
    }

    public AccountWithDataSet addTestAccount() {
        return addTestAccount(generateAccountName());
    }

    public AccountWithDataSet addTestAccount(@NonNull String name) {
        // remember the most recent one. If the caller wants to add multiple accounts they will
        // have to keep track of them themselves.
        final AccountWithDataSet account = new AccountWithDataSet(name, TEST_ACCOUNT_TYPE, null);
        addTestAccount(account);
        return account;
    }

    public String generateAccountName(String prefix) {
        return prefix + "_t" + System.nanoTime();
    }

    public String generateAccountName() {
        return generateAccountName("test");
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public void removeTestAccount(AccountWithDataSet account) {
        final Account remove = account.getAccountOrNull();
        mAccountManager.removeAccountExplicitly(remove);
        mAddedAccounts.remove(remove);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public void removeTestAccount(String accountName) {
        removeTestAccount(new AccountWithDataSet(accountName, TEST_ACCOUNT_TYPE, null));
    }

    public boolean hasTestAccount(String name) {
        final List<Account> accounts = Arrays.asList(
                mAccountManager.getAccountsByType(TEST_ACCOUNT_TYPE));
        return accounts.contains(new Account(name, TEST_ACCOUNT_TYPE));
    }

    public void removeContactsForAccount(AccountWithDataSet account) {
        mResolver.delete(RawContacts.CONTENT_URI,
                RawContacts.ACCOUNT_NAME + "=? AND " + RawContacts.ACCOUNT_TYPE + "=?",
                new String[] { account.name, account.type });
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public void cleanup() {
        // Note that we don't need to cleanup up the contact data associated with the account.
        // CP2 will eventually do that automatically so as long as we're using unique account
        // names we should be safe. Note that cleanup is not done synchronously when the account
        // is removed so if multiple tests are using the same account name then the data should
        // be manually deleted after each test run.

        for (Account account : mAddedAccounts) {
            mAccountManager.removeAccountExplicitly(account);
        }
        mAddedAccounts.clear();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public static void removeAccountsWithPrefix(Context context, String prefix) {
        final AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        final Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNT_TYPE);
        for (Account account : accounts) {
            if (account.name.startsWith(prefix)) {
                accountManager.removeAccountExplicitly(account);
            }
        }


    }
}
