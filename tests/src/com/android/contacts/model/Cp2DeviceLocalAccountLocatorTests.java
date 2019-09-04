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

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import androidx.annotation.Nullable;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.test.mocks.MockContentProvider;
import com.android.contacts.tests.FakeDeviceAccountTypeFactory;
import com.android.contacts.util.DeviceLocalAccountTypeFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@SmallTest
public class Cp2DeviceLocalAccountLocatorTests extends AndroidTestCase {

    // Basic smoke test that just checks that it doesn't throw when loading from CP2. We don't
    // care what CP2 actually contains for this.
    public void testShouldNotCrash() {
        final DeviceLocalAccountLocator sut = new Cp2DeviceLocalAccountLocator(
                getContext().getContentResolver(),
                new DeviceLocalAccountTypeFactory.Default(getContext()),
                Collections.<String>emptySet());
        sut.getDeviceLocalAccounts();
        // We didn't throw so it passed
    }

    public void test_getDeviceLocalAccounts_returnsEmptyListWhenQueryReturnsNull() {
        final DeviceLocalAccountLocator sut = createWithQueryResult(null);
        assertTrue(sut.getDeviceLocalAccounts().isEmpty());
    }

    public void test_getDeviceLocalAccounts_returnsEmptyListWhenNoRawContactsHaveDeviceType() {
        final DeviceLocalAccountLocator sut = createWithQueryResult(queryResult(
                        "user", "com.example",
                        "user", "com.example",
                        "user", "com.example"));
        assertTrue(sut.getDeviceLocalAccounts().isEmpty());
    }

    public void test_getDeviceLocalAccounts_returnsListWithItemForNullAccount() {
        final DeviceLocalAccountLocator sut = createWithQueryResult(queryResult(
                "user", "com.example",
                null, null,
                "user", "com.example",
                null, null));

        assertEquals(1, sut.getDeviceLocalAccounts().size());
    }

    public void test_getDeviceLocalAccounts_containsItemForEachDeviceAccount() {
        final DeviceLocalAccountTypeFactory stubFactory = new FakeDeviceAccountTypeFactory()
                .withDeviceTypes(null, "vnd.sec.contact.phone")
                .withSimTypes("vnd.sec.contact.sim");
        final DeviceLocalAccountLocator sut = createLocator(queryResult(
                "user", "com.example",
                "user", "com.example",
                "phone_account", "vnd.sec.contact.phone",
                null, null,
                "phone_account", "vnd.sec.contact.phone",
                "user", "com.example",
                null, null,
                "sim_account", "vnd.sec.contact.sim",
                "sim_account_2", "vnd.sec.contact.sim"
        ), stubFactory);


        assertEquals(4, sut.getDeviceLocalAccounts().size());
    }

    public void test_getDeviceLocalAccounts_doesNotContainItemsForKnownAccountTypes() {
        final Cp2DeviceLocalAccountLocator sut = new Cp2DeviceLocalAccountLocator(
                getContext().getContentResolver(), new FakeDeviceAccountTypeFactory(),
                new HashSet<>(Arrays.asList("com.example", "com.example.1")));

        assertTrue("Selection should filter known accounts",
                sut.getSelection().contains("NOT IN (?,?)"));

        final List<String> args = Arrays.asList(sut.getSelectionArgs());
        assertEquals(2, args.size());
        assertTrue("Selection args is missing an expected value", args.contains("com.example"));
        assertTrue("Selection args is missing an expected value", args.contains("com.example.1"));
    }

    public void test_getDeviceLocalAccounts_includesAccountsFromSettings() {
        final DeviceLocalAccountTypeFactory stubFactory = new FakeDeviceAccountTypeFactory()
                .withDeviceTypes(null, "vnd.sec.contact.phone")
                .withSimTypes("vnd.sec.contact.sim");
        final DeviceLocalAccountLocator sut = createLocator(new FakeContactsProvider()
                .withQueryResult(ContactsContract.Settings.CONTENT_URI, queryResult(
                        "phone_account", "vnd.sec.contact.phone",
                        "sim_account", "vnd.sec.contact.sim"
                )), stubFactory);

        assertEquals(2, sut.getDeviceLocalAccounts().size());
    }

    public void test_getDeviceLocalAccounts_includesAccountsFromGroups() {
        final DeviceLocalAccountTypeFactory stubFactory = new FakeDeviceAccountTypeFactory()
                .withDeviceTypes(null, "vnd.sec.contact.phone")
                .withSimTypes("vnd.sec.contact.sim");
        final DeviceLocalAccountLocator sut = createLocator(new FakeContactsProvider()
                .withQueryResult(ContactsContract.Groups.CONTENT_URI, queryResult(
                        "phone_account", "vnd.sec.contact.phone",
                        "sim_account", "vnd.sec.contact.sim"
                )), stubFactory);

        assertEquals(2, sut.getDeviceLocalAccounts().size());
    }

    private DeviceLocalAccountLocator createWithQueryResult(
            Cursor cursor) {
        return createLocator(cursor, new DeviceLocalAccountTypeFactory.Default(mContext));
    }

    private DeviceLocalAccountLocator createLocator(ContentProvider contactsProvider,
            DeviceLocalAccountTypeFactory localAccountTypeFactory) {
        final DeviceLocalAccountLocator locator = new Cp2DeviceLocalAccountLocator(
                createContentResolverWithProvider(contactsProvider),
                localAccountTypeFactory, Collections.<String>emptySet());
        return locator;
    }

    private DeviceLocalAccountLocator createLocator(Cursor cursor,
            DeviceLocalAccountTypeFactory localAccountTypeFactory) {
        final DeviceLocalAccountLocator locator = new Cp2DeviceLocalAccountLocator(
                createStubResolverWithContentQueryResult(cursor),
                localAccountTypeFactory,
                Collections.<String>emptySet());
        return locator;
    }

    private ContentResolver createContentResolverWithProvider(ContentProvider contactsProvider) {
        final MockContentResolver resolver = new MockContentResolver();
        resolver.addProvider(ContactsContract.AUTHORITY, contactsProvider);
        return resolver;
    }

    private ContentResolver createStubResolverWithContentQueryResult(Cursor cursor) {
        final MockContentResolver resolver = new MockContentResolver();
        resolver.addProvider(ContactsContract.AUTHORITY, new FakeContactsProvider()
                .withDefaultQueryResult(cursor));
        return resolver;
    }

    private Cursor queryResult(String... nameTypePairs) {
        final MatrixCursor cursor = new MatrixCursor(new String[]
                { RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE, RawContacts.DATA_SET });
        for (int i = 0; i < nameTypePairs.length; i+=2) {
            cursor.newRow().add(nameTypePairs[i]).add(nameTypePairs[i+1])
                    .add(null);
        }
        return cursor;
    }

    private static class FakeContactsProvider extends MockContentProvider {
        public Cursor mNextQueryResult;
        public Map<Uri, Cursor> mNextResultMapping = new HashMap<>();

        public FakeContactsProvider() {}

        public FakeContactsProvider withDefaultQueryResult(Cursor cursor) {
            mNextQueryResult = cursor;
            return this;
        }

        public FakeContactsProvider withQueryResult(Uri uri, Cursor cursor) {
            mNextResultMapping.put(uri, cursor);
            return this;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            return query(uri, projection, selection, selectionArgs, sortOrder, null);
        }

        @Nullable
        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder, CancellationSignal cancellationSignal) {
            final Cursor result = mNextResultMapping.get(uri);
            if (result == null) {
                return mNextQueryResult;
            } else {
                return result;
            }
        }
    }
}
