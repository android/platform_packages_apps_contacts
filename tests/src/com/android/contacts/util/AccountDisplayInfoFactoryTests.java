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
package com.android.contacts.util;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountDisplayInfo;
import com.android.contacts.model.account.AccountDisplayInfoFactory;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.test.mocks.MockAccountTypeManager;
import com.android.contacts.tests.FakeAccountType;
import com.android.contacts.tests.FakeDeviceAccountTypeFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@SmallTest
public class AccountDisplayInfoFactoryTests extends AndroidTestCase {

    private Map<AccountWithDataSet, AccountType> mKnownTypes;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mKnownTypes = new HashMap<>();
    }

    public void test_displayableAccount_hasIconFromAccountType() {
        final Drawable comExampleIcon = someDrawable();

        addTypeMapping(account("user", "com.example"), "title", comExampleIcon);
        addTypeMapping(account(null, null), "device", someDrawable());
        addTypeMapping(account("foo", "bar.type"), "bar", someDrawable());
        addTypeMapping(account("user2", "com.example"), "title", comExampleIcon);

        final AccountDisplayInfoFactory sut = createFactoryForKnownTypes();

        final AccountDisplayInfo displayable = sut.getAccountDisplayInfo(
                account("user", "com.example"));
        assertEquals(comExampleIcon, displayable.getIcon());
    }

    public void test_displayableAccount_hasNameFromAccount() {
        final Drawable comExampleIcon = someDrawable();

        addTypeMapping(account("user@example.com", "com.example"), "title", comExampleIcon);
        addTypeMapping(account(null, null), "device", someDrawable());
        addTypeMapping(account("foo", "bar.type"), "bar", someDrawable());
        addTypeMapping(account("user2@example.com", "com.example"), "title", comExampleIcon);

        final AccountDisplayInfoFactory sut = createFactoryForKnownTypes();

        final AccountDisplayInfo displayable = sut.getAccountDisplayInfo(
                account("user@example.com", "com.example"));
        assertEquals("user@example.com", displayable.getNameLabel());
    }

    public void test_displayableAccountForNullAccount_hasNameFromAccountType() {
        addSomeKnownAccounts();
        addTypeMapping(account(null, null), "Device Display Label", someDrawable());

        final AccountDisplayInfoFactory sut = createFactoryForKnownTypes();

        final AccountDisplayInfo displayable = sut.getAccountDisplayInfo(
                account(null, null));
        assertEquals("Device Display Label", displayable.getNameLabel());
    }

    public void test_displayableAccountForDeviceAccount_hasNameFromAccountType() {
        addSomeKnownAccounts();
        addTypeMapping(account("some.device.account.name", "device.account.type"), "Device Label",
                someDrawable());

        final AccountDisplayInfoFactory sut = createFactoryForKnownTypes(
                new FakeDeviceAccountTypeFactory().withDeviceTypes("device.account.type"));

        final AccountDisplayInfo displayable = sut.getAccountDisplayInfo(
                account("some.device.account.name", "device.account.type"));
        assertEquals("Device Label", displayable.getNameLabel());
    }

    public void test_displayableAccountForDeviceAccountWhenMultiple_hasNameFromAccount() {
        addSomeKnownAccounts();
        addTypeMapping(account("first.device.account.name", "a.device.account.type"),
                "Device Display Label", someDrawable());
        addTypeMapping(account("second.device.account.name", "b.device.account.type"),
                "Device Display Label", someDrawable());
        addTypeMapping(account("another.device.account.name", "a.device.account.type"),
                "Device Display Label", someDrawable());

        final AccountDisplayInfoFactory sut = createFactoryForKnownTypes(
                new FakeDeviceAccountTypeFactory().withDeviceTypes("a.device.account.type",
                        "b.device.account.type"));

        final AccountDisplayInfo displayable = sut.getAccountDisplayInfo(
                account("first.device.account.name", "a.device.account.type"));
        assertEquals("first.device.account.name", displayable.getNameLabel());

        final AccountDisplayInfo displayable2 = sut.getAccountDisplayInfo(
                account("second.device.account.name", "b.device.account.type"));
        assertEquals("second.device.account.name", displayable2.getNameLabel());
    }

    public void test_displayableAccountForSimAccount_hasNameFromAccountType() {
        addSomeKnownAccounts();
        addTypeMapping(account("sim.account.name", "sim.account.type"), "SIM", someDrawable());

        final AccountDisplayInfoFactory sut = createFactoryForKnownTypes(
                new FakeDeviceAccountTypeFactory().withSimTypes("sim.account.type"));

        final AccountDisplayInfo displayable = sut.getAccountDisplayInfo(
                account("sim.account.name", "sim.account.type"));
        assertEquals("SIM", displayable.getNameLabel());
    }

    public void test_displayableAccountForSimAccountWhenMultiple_hasNameFromAccount() {
        addSomeKnownAccounts();
        addTypeMapping(account("sim.account.name", "sim.account.type"), "SIM", someDrawable());
        addTypeMapping(account("sim2.account.name", "sim.account.type"), "SIM", someDrawable());

        final AccountDisplayInfoFactory sut = createFactoryForKnownTypes(
                new FakeDeviceAccountTypeFactory().withSimTypes("sim.account.type"));

        final AccountDisplayInfo displayable = sut.getAccountDisplayInfo(
                account("sim.account.name", "sim.account.type"));
        assertEquals("sim.account.name", displayable.getNameLabel());
    }

    private void addSomeKnownAccounts() {
        final Drawable comExampleIcon = someDrawable();
        addTypeMapping(account("user@example.com", "com.example"), "Example Title", comExampleIcon);
        addTypeMapping(account("foo", "bar.type"), "Bar", someDrawable());
        addTypeMapping(account("user2@example.com", "com.example"), "Example Title", comExampleIcon);
        addTypeMapping(account("user", "com.example.two"), "Some Account", someDrawable());
    }

    private AccountDisplayInfoFactory createFactoryForKnownTypes() {
        return createFactoryForKnownTypes(new DeviceLocalAccountTypeFactory.Default(getContext()));
    }

    private AccountDisplayInfoFactory createFactoryForKnownTypes(DeviceLocalAccountTypeFactory
            typeFactory) {
        return new AccountDisplayInfoFactory(getContext(),
                createFakeAccountTypeManager(mKnownTypes), typeFactory,
                new ArrayList<>(mKnownTypes.keySet()));
    }

    private AccountWithDataSet account(String name, String type) {
        return new AccountWithDataSet(name, type, /* dataSet */ null);
    }

    private void addTypeMapping(AccountWithDataSet account, String label, Drawable icon) {
        mKnownTypes.put(account, FakeAccountType.create(account, label, icon));
    }

    private AccountTypeManager createFakeAccountTypeManager(
            final Map<AccountWithDataSet, AccountType> mapping) {
        return new MockAccountTypeManager(mapping.values().toArray(new AccountType[mapping.size()]),
                mapping.keySet().toArray(new AccountWithDataSet[mapping.size()]));
    }

    private Drawable someDrawable() {
        return new Drawable() {
            @Override
            public void draw(Canvas canvas) {
            }

            @Override
            public void setAlpha(int i) {
            }

            @Override
            public void setColorFilter(ColorFilter colorFilter) {
            }

            @Override
            public int getOpacity() {
                return PixelFormat.OPAQUE;
            }
        };
    }

}
