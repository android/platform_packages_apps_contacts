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

import com.android.contacts.model.account.AccountType;
import com.android.contacts.util.DeviceLocalAccountTypeFactory;

import java.util.HashMap;
import java.util.Map;

public class FakeDeviceAccountTypeFactory implements DeviceLocalAccountTypeFactory {

    private final Map<String, AccountType> mDeviceAccountTypes = new HashMap<>();
    private final Map<String, AccountType> mSimAccountTypes = new HashMap<>();

    @Override
    public int classifyAccount(String accountType) {
        if (mDeviceAccountTypes.containsKey(accountType)) {
            return TYPE_DEVICE;
        } else if (mSimAccountTypes.containsKey(accountType)) {
            return TYPE_SIM;
        } else {
            return TYPE_OTHER;
        }
    }

    @Override
    public AccountType getAccountType(String accountType) {
        final AccountType type = mDeviceAccountTypes.get(accountType);
        return type == null ? mSimAccountTypes.get(accountType) : type;
    }

    public FakeDeviceAccountTypeFactory withSimTypes(String... types) {
        for (String type : types) {
            mSimAccountTypes.put(type, new FakeAccountType(type));
        }
        return this;
    }

    public FakeDeviceAccountTypeFactory withSimTypes(AccountType... types) {
        for (AccountType type : types) {
            mSimAccountTypes.put(type.accountType, type);
        }
        return this;
    }

    public FakeDeviceAccountTypeFactory withDeviceTypes(String... types) {
        for (String type : types) {
            mDeviceAccountTypes.put(type, new FakeAccountType(type));
        }
        return this;
    }

    public FakeDeviceAccountTypeFactory withDeviceTypes(AccountType... types) {
        for (AccountType type : types) {
            mDeviceAccountTypes.put(type.accountType, type);
        }
        return this;
    }
}
