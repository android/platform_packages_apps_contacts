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

import android.content.Context;

import com.android.contacts.list.ContactListFilter;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.util.DeviceLocalAccountTypeFactory;
import com.android.contactsbind.ObjectFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods to get AccountDisplayInfo instances for available accounts.
 *
 * For most accounts the account name will be used for the label but device accounts and
 * SIM accounts have friendly names associated with them unless there is more than one of these
 * types of accounts present in the list.
 */
public class AccountDisplayInfoFactory {

    private final Context mContext;
    private final AccountTypeManager mAccountTypeManager;

    private final DeviceLocalAccountTypeFactory mDeviceAccountTypeFactory;

    private final int mDeviceAccountCount;
    private final int mSimAccountCount;

    public AccountDisplayInfoFactory(Context context, List<AccountWithDataSet> accounts) {
        this(context, AccountTypeManager.getInstance(context),
                ObjectFactory.getDeviceLocalAccountTypeFactory(context), accounts);
    }

    public AccountDisplayInfoFactory(Context context, AccountTypeManager accountTypeManager,
            DeviceLocalAccountTypeFactory deviceAccountTypeFactory,
            List<AccountWithDataSet> accounts) {
        mContext = context;
        mAccountTypeManager = accountTypeManager;
        mDeviceAccountTypeFactory = deviceAccountTypeFactory;

        mSimAccountCount = countOfType(DeviceLocalAccountTypeFactory.TYPE_SIM, accounts);
        mDeviceAccountCount = countOfType(DeviceLocalAccountTypeFactory.TYPE_DEVICE, accounts);
    }

    public AccountDisplayInfo getAccountDisplayInfo(AccountWithDataSet account) {
        final AccountType type = mAccountTypeManager.getAccountTypeForAccount(account);
        final CharSequence name = shouldUseTypeLabelForName(account)
                ? type.getDisplayLabel(mContext)
                : account.name;
        return new AccountDisplayInfo(account, name, type.getDisplayLabel(mContext),
                type.getDisplayIcon(mContext),
                DeviceLocalAccountTypeFactory.Util.isLocalAccountType(mDeviceAccountTypeFactory,
                        type.accountType));
    }

    public AccountDisplayInfo getAccountDisplayInfoFor(ContactListFilter filter) {
        return getAccountDisplayInfo(filter.toAccountWithDataSet());
    }

    public AccountDisplayInfo getAccountDisplayInfoFor(RawContactDelta delta) {
        final AccountWithDataSet account = new AccountWithDataSet(delta.getAccountName(),
                delta.getAccountType(), delta.getDataSet());
        return getAccountDisplayInfo(account);
    }

    public static AccountDisplayInfoFactory fromListFilters(Context context,
            List<ContactListFilter> filters) {
        final List<AccountWithDataSet> accounts = new ArrayList<>(filters.size());
        for (ContactListFilter filter : filters) {
            accounts.add(filter.toAccountWithDataSet());
        }
        return new AccountDisplayInfoFactory(context, accounts);
    }

    private boolean shouldUseTypeLabelForName(AccountWithDataSet account) {
        final int type = mDeviceAccountTypeFactory.classifyAccount(account.type);
        return (type == DeviceLocalAccountTypeFactory.TYPE_SIM && mSimAccountCount == 1)
                || (type == DeviceLocalAccountTypeFactory.TYPE_DEVICE && mDeviceAccountCount == 1)
                || account.name == null;

    }

    private int countOfType(@DeviceLocalAccountTypeFactory.LocalAccountType int type,
            List<AccountWithDataSet> accounts) {
        int count = 0;
        for (AccountWithDataSet account : accounts) {
            if (mDeviceAccountTypeFactory.classifyAccount(account.type) == type) {
                count++;
            }
        }
        return count;
    }
}
