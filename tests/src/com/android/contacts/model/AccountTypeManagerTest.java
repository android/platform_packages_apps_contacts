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

package com.android.contacts.model;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.SharedPreferences;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountTypeWithDataSet;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.GoogleAccountType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Test case for {@link com.android.contacts.model.AccountTypeManager}.
 *
 * adb shell am instrument -w -e class com.android.contacts.model.AccountTypeManagerTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class AccountTypeManagerTest extends AndroidTestCase {

    private static final Account[] ACCOUNTS = new Account[2];
    static {
        ACCOUNTS[0] = new Account("name1", GoogleAccountType.ACCOUNT_TYPE);
        ACCOUNTS[1] = new Account("name2", GoogleAccountType.ACCOUNT_TYPE);
    }

    @Mock private AccountManager mAccountManager;
    @Mock private SharedPreferences mPrefs;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());
        MockitoAnnotations.initMocks(this);
    }

    private static AccountWithDataSet createAccountWithDataSet(String name, AccountType type) {
        return new AccountWithDataSet(name, type.accountType, type.dataSet);
    }

    /**
     * Array of {@link AccountType} -> {@link Map}
     */
    private static Map<AccountTypeWithDataSet, AccountType> buildAccountTypes(AccountType... types) {
        final HashMap<AccountTypeWithDataSet, AccountType> result = Maps.newHashMap();
        for (AccountType type : types) {
            result.put(type.getAccountTypeAndDataSet(), type);
        }
        return result;
    }

    /**
     * Array of {@link AccountWithDataSet} -> {@link Collection}
     */
    private static Collection<AccountWithDataSet> buildAccounts(AccountWithDataSet... accounts) {
        final List<AccountWithDataSet> result = Lists.newArrayList();
        for (AccountWithDataSet account : accounts) {
            result.add(account);
        }
        return result;
    }

    public void testGetDefaultAccount_NoAccounts() {
        assertNull(getDefaultGoogleAccountName());
    }

    public void testGetDefaultAccount_NoAccounts_DefaultPreferenceSet() {
        when(mPrefs.getString(Mockito.anyString(), Mockito.any())).thenReturn(
                getDefaultAccountPreference("name1", GoogleAccountType.ACCOUNT_TYPE));
        assertNull(getDefaultGoogleAccountName());
    }

    public void testGetDefaultAccount_NoDefaultAccountPreferenceSet() {
        when(mAccountManager.getAccountsByType(Mockito.anyString())).thenReturn(ACCOUNTS);
        assertEquals("name1", getDefaultGoogleAccountName());
    }

    public void testGetDefaultAccount_DefaultAccountPreferenceSet() {
        when(mAccountManager.getAccountsByType(Mockito.anyString())).thenReturn(ACCOUNTS);
        when(mPrefs.getString(Mockito.anyString(), Mockito.any())).thenReturn(
                getDefaultAccountPreference("name2", GoogleAccountType.ACCOUNT_TYPE));
        assertEquals("name2", getDefaultGoogleAccountName());
    }

    public void testGetDefaultAccount_DefaultAccountPreferenceSet_NonGoogleAccountType() {
        when(mAccountManager.getAccountsByType(Mockito.anyString())).thenReturn(ACCOUNTS);
        when(mPrefs.getString(Mockito.anyString(), Mockito.any())).thenReturn(
                getDefaultAccountPreference("name3", "type3"));
        assertEquals("name1", getDefaultGoogleAccountName());
    }

    public void testGetDefaultAccount_DefaultAccountPreferenceSet_UnknownName() {
        when(mAccountManager.getAccountsByType(Mockito.anyString())).thenReturn(ACCOUNTS);
        when(mPrefs.getString(Mockito.anyString(), Mockito.any())).thenReturn(
                getDefaultAccountPreference("name4",GoogleAccountType.ACCOUNT_TYPE));
        assertEquals("name1", getDefaultGoogleAccountName());
    }

    private final String getDefaultGoogleAccountName() {
        // We don't need the real preference key value since it's mocked
        final Account account = AccountTypeManager.getDefaultGoogleAccount(
                mAccountManager, mPrefs, "contact_editor_default_account_key");
        return account == null ? null : account.name;
    }

    private static final String getDefaultAccountPreference(String name, String type) {
        return new AccountWithDataSet(name, type, /* dataSet */ null).stringify();
    }
}
