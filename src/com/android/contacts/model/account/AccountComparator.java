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
package com.android.contacts.model.account;

import com.google.common.base.Objects;

import java.util.Comparator;

/**
 * Orders accounts for display such that the default account is first
 */
public class AccountComparator implements Comparator<AccountWithDataSet> {
    private AccountWithDataSet mDefaultAccount;

    public AccountComparator(AccountWithDataSet defaultAccount) {
        mDefaultAccount = defaultAccount;
    }

    @Override
    public int compare(AccountWithDataSet a, AccountWithDataSet b) {
        if (Objects.equal(a.name, b.name) && Objects.equal(a.type, b.type)
                && Objects.equal(a.dataSet, b.dataSet)) {
            return 0;
        } else if (b.name == null || b.type == null) {
            return -1;
        } else if (a.name == null || a.type == null) {
            return 1;
        } else if (isWritableGoogleAccount(a) && a.equals(mDefaultAccount)) {
            return -1;
        } else if (isWritableGoogleAccount(b) && b.equals(mDefaultAccount)) {
            return 1;
        } else if (isWritableGoogleAccount(a) && !isWritableGoogleAccount(b)) {
            return -1;
        } else if (isWritableGoogleAccount(b) && !isWritableGoogleAccount(a)) {
            return 1;
        } else {
            int diff = a.name.compareToIgnoreCase(b.name);
            if (diff != 0) {
                return diff;
            }
            diff = a.type.compareToIgnoreCase(b.type);
            if (diff != 0) {
                return diff;
            }

            // Accounts without data sets get sorted before those that have them.
            if (a.dataSet != null) {
                return b.dataSet == null ? 1 : a.dataSet.compareToIgnoreCase(b.dataSet);
            } else {
                return -1;
            }
        }
    }

    private static boolean isWritableGoogleAccount(AccountWithDataSet account) {
        return GoogleAccountType.ACCOUNT_TYPE.equals(account.type) && account.dataSet == null;
    }
}
