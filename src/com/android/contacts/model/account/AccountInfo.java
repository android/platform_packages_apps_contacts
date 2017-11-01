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

import android.graphics.drawable.Drawable;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Holds an {@link AccountWithDataSet} and the corresponding {@link AccountType} for an account.
 */
public class AccountInfo {

    private final AccountDisplayInfo mDisplayInfo;
    private final AccountType mType;

    public AccountInfo(AccountDisplayInfo displayInfo, AccountType type) {
        this.mDisplayInfo = displayInfo;
        this.mType = type;
    }

    public AccountType getType() {
        return mType;
    }

    public AccountWithDataSet getAccount() {
        return mDisplayInfo.getSource();
    }

    /**
     * Returns the displayable account name label for the account
     */
    public CharSequence getNameLabel() {
        return mDisplayInfo.getNameLabel();
    }

    /**
     * Returns the displayable account type label for the account
     */
    public CharSequence getTypeLabel() {
        return mDisplayInfo.getTypeLabel();
    }

    /**
     * Returns the icon for the account type
     */
    public Drawable getIcon() {
        return mDisplayInfo.getIcon();
    }

    public boolean hasDistinctName() {
        return mDisplayInfo.hasDistinctName();
    }

    public boolean isDeviceAccount() {
        return mDisplayInfo.isDeviceAccount();
    }

    public boolean hasGoogleAccountType() {
        return mDisplayInfo.hasGoogleAccountType();
    }

    public boolean sameAccount(AccountInfo other) {
        return sameAccount(other.getAccount());
    }

    public boolean sameAccount(AccountWithDataSet other) {
        return Objects.equals(getAccount(), other);
    }

    /**
     * Returns whether accounts contains an account that is the same as account
     *
     * <p>This does not use equality rather checks whether the source account ({@link #getAccount()}
     * is the same</p>
     */
    public static boolean contains(List<AccountInfo> accounts, AccountInfo account) {
        return contains(accounts, account.getAccount());
    }

    /**
     * Returns whether accounts contains an account that is the same as account
     *
     * <p>This does not use equality rather checks whether the source account ({@link #getAccount()}
     * is the same</p>
     */
    public static boolean contains(List<AccountInfo> accounts, AccountWithDataSet account) {
        return getAccount(accounts, account) != null;
    }

    /**
     * Returns the AccountInfo from the list that has the specified account as it's source account
     */
    public static AccountInfo getAccount(List<AccountInfo> accounts, AccountWithDataSet account) {
        Preconditions.checkNotNull(accounts);

        for (AccountInfo info : accounts) {
            if (info.sameAccount(account)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Sorts the accounts using the same ordering as {@link AccountComparator}
     */
    public static void sortAccounts(AccountWithDataSet defaultAccount, List<AccountInfo> accounts) {
        Collections.sort(accounts, sourceComparator(defaultAccount));
    }

    /**
     * Gets a list of the AccountWithDataSet for accounts
     */
    public static List<AccountWithDataSet> extractAccounts(List<AccountInfo> accounts) {
        return Lists.transform(accounts, ACCOUNT_EXTRACTOR);
    }

    private static Comparator<AccountInfo> sourceComparator(AccountWithDataSet defaultAccount) {
        final AccountComparator accountComparator = new AccountComparator(defaultAccount);
        return new Comparator<AccountInfo>() {
            @Override
            public int compare(AccountInfo o1, AccountInfo o2) {
                return accountComparator.compare(o1.getAccount(), o2.getAccount());
            }
        };
    }

    public static final Function<AccountInfo, AccountWithDataSet> ACCOUNT_EXTRACTOR =
            new Function<AccountInfo, AccountWithDataSet>() {
                @Override
                public AccountWithDataSet apply(AccountInfo from) {
                    return from.getAccount();
                }
            };
}
