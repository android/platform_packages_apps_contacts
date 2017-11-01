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
package com.android.contacts.test.mocks;

import android.accounts.Account;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountTypeWithDataSet;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.BaseAccountType;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Arrays;
import java.util.List;

/**
 * A mock {@link AccountTypeManager} class.
 */
public class MockAccountTypeManager extends AccountTypeManager {

    public AccountType[] mTypes;
    public AccountWithDataSet[] mAccounts;

    public MockAccountTypeManager(AccountType[] types, AccountWithDataSet[] accounts) {
        this.mTypes = types;
        this.mAccounts = accounts;
    }

    @Override
    public AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet) {
        // Add fallback accountType to mimic the behavior of AccountTypeManagerImpl
        AccountType mFallbackAccountType = new BaseAccountType() {
            @Override
            public boolean areContactsWritable() {
                return false;
            }
        };
        mFallbackAccountType.accountType = "fallback";
        for (AccountType type : mTypes) {
            if (Objects.equal(accountTypeWithDataSet.accountType, type.accountType)
                    && Objects.equal(accountTypeWithDataSet.dataSet, type.dataSet)) {
                return type;
            }
        }
        return mFallbackAccountType;
    }

    @Override
    public List<AccountWithDataSet> blockForWritableAccounts() {
        return Arrays.asList(mAccounts);
    }

    @Override
    public ListenableFuture<List<AccountInfo>> getAccountsAsync() {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ListenableFuture<List<AccountInfo>> filterAccountsAsync(Predicate<AccountInfo> filter) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public AccountInfo getAccountInfoForAccount(AccountWithDataSet account) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Account getDefaultGoogleAccount() {
        return null;
    }
}
