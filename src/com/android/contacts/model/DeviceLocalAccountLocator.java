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
package com.android.contacts.model;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;

import com.android.contacts.Experiments;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.GoogleAccountType;
import com.android.contactsbind.ObjectFactory;
import com.android.contactsbind.experiments.Flags;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Attempts to detect accounts for device contacts
 */
public abstract class DeviceLocalAccountLocator {

    /**
     * Returns a list of device local accounts
     */
    public abstract List<AccountWithDataSet> getDeviceLocalAccounts();

    // This works on Nexus and AOSP because the local device account is the null account but most
    // OEMs have a special account name and type for their device account.
    public static final DeviceLocalAccountLocator NULL_ONLY = new DeviceLocalAccountLocator() {
        @Override
        public List<AccountWithDataSet> getDeviceLocalAccounts() {
            return Collections.singletonList(AccountWithDataSet.getNullAccount());
        }
    };

    public static DeviceLocalAccountLocator create(Context context,
            Set<String> knownAccountTypes) {
        if (Flags.getInstance().getBoolean(Experiments.CP2_DEVICE_ACCOUNT_DETECTION_ENABLED)) {
            return new Cp2DeviceLocalAccountLocator(context.getContentResolver(),
                    ObjectFactory.getDeviceLocalAccountTypeFactory(context), knownAccountTypes);
        }
        return NULL_ONLY;
    }

    public static DeviceLocalAccountLocator create(Context context) {
        final AccountManager accountManager =
                (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        final Set<String> knownTypes = new HashSet<>();
        for (Account account : accountManager.getAccounts()) {
            knownTypes.add(account.type);
        }
        if (Flags.getInstance().getBoolean(Experiments.CP2_DEVICE_ACCOUNT_DETECTION_ENABLED)) {
            return new Cp2DeviceLocalAccountLocator(context.getContentResolver(),
                    ObjectFactory.getDeviceLocalAccountTypeFactory(context), knownTypes);
        } else {
            return new NexusDeviceAccountLocator(context, accountManager);
        }
    }

    /**
     * On Nexus the "device" account uses "null" values for the account name and type columns
     *
     * <p>However, the focus sync adapter migrates contacts from this null account to a Google
     * account if one exists. Hence, the device account should be returned only when there is no
     * Google Account added or when there already exists contacts in the null account.
     * </p>
     */
    public static class NexusDeviceAccountLocator extends DeviceLocalAccountLocator {
        private final Context mContext;
        private final AccountManager mAccountManager;


        public NexusDeviceAccountLocator(Context context, AccountManager accountManager) {
            mContext = context;
            mAccountManager = accountManager;
        }

        @Override
        public List<AccountWithDataSet> getDeviceLocalAccounts() {
            @SuppressWarnings("MissingPermission")
            final Account[] accounts = mAccountManager
                    .getAccountsByType(GoogleAccountType.ACCOUNT_TYPE);

            if (accounts.length > 0 && !AccountWithDataSet.getNullAccount().hasData(mContext)) {
                return Collections.emptyList();
            } else {
                return Collections.singletonList(AccountWithDataSet.getNullAccount());
            }
        }
    }
}
