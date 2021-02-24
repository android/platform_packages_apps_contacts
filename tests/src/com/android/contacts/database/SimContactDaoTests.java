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
package com.android.contacts.database;

import static android.os.Build.VERSION_CODES;

import static com.android.contacts.tests.ContactsMatchers.DataCursor.hasEmail;
import static com.android.contacts.tests.ContactsMatchers.DataCursor.hasName;
import static com.android.contacts.tests.ContactsMatchers.DataCursor.hasPhone;
import static com.android.contacts.tests.ContactsMatchers.isSimContactWithNameAndPhone;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.SimRecords;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;

import androidx.annotation.RequiresApi;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.filters.Suppress;

import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.test.mocks.MockContentProvider;
import com.android.contacts.tests.AccountsTestHelper;
import com.android.contacts.tests.ContactsMatchers;
import com.android.contacts.tests.SimContactsTestHelper;
import com.android.contacts.tests.StringableCursor;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@RunWith(Enclosed.class)
public class SimContactDaoTests {

    // Some random area codes for generating realistic US phones when
    // generating fake data for the SIM contacts or CP2
    private static final String[] AREA_CODES = 
            {"360", "509", "416", "831", "212", "208"};
    private static final Random sRandom = new Random();

    // Approximate maximum number of contacts that can be stored on a SIM card for testing
    // boundary cases
    public static final int MAX_SIM_CONTACTS = 600;

    // On pre-M addAccountExplicitly (which we call via AccountsTestHelper) causes a
    // SecurityException to be thrown unless we add AUTHENTICATE_ACCOUNTS permission to the app
    // manifest. Instead of adding the extra permission just for tests we'll just only run them
    // on M or newer
    @SdkSuppress(minSdkVersion = VERSION_CODES.M)
    // Lollipop MR1 is required for removeAccountExplicitly
    @RequiresApi(api = VERSION_CODES.LOLLIPOP_MR1)
    @LargeTest
    @RunWith(AndroidJUnit4.class)
    public static class ImportIntegrationTest {
        private AccountWithDataSet mAccount;
        private AccountsTestHelper mAccountsHelper;
        private ContentResolver mResolver;

        @Before
        public void setUp() throws Exception {
            mAccountsHelper = new AccountsTestHelper();
            mAccount = mAccountsHelper.addTestAccount();
            mResolver = getContext().getContentResolver();
        }

        @After
        public void tearDown() throws Exception {
            mAccountsHelper.cleanup();
        }

        @Test
        public void importFromSim() throws Exception {
            final SimContactDao sut = SimContactDao.create(getContext());

            sut.importContacts(Arrays.asList(
                    new SimContact(1, "Test One", "15095550101"),
                    new SimContact(2, "Test Two", "15095550102"),
                    new SimContact(3, "Test Three", "15095550103", new String[] {
                            "user@example.com", "user2@example.com"
                    })
            ), mAccount);

            Cursor cursor = queryContactWithName("Test One");
            assertThat(cursor, ContactsMatchers.hasCount(2));
            assertThat(cursor, hasName("Test One"));
            assertThat(cursor, hasPhone("15095550101"));
            cursor.close();

            cursor = queryContactWithName("Test Two");
            assertThat(cursor, ContactsMatchers.hasCount(2));
            assertThat(cursor, hasName("Test Two"));
            assertThat(cursor, hasPhone("15095550102"));
            cursor.close();

            cursor = queryContactWithName("Test Three");
            assertThat(cursor, ContactsMatchers.hasCount(4));
            assertThat(cursor, hasName("Test Three"));
            assertThat(cursor, hasPhone("15095550103"));
            assertThat(cursor, allOf(hasEmail("user@example.com"), hasEmail("user2@example.com")));
            cursor.close();
        }

        @Test
        public void importContactWhichOnlyHasName() throws Exception {
            final SimContactDao sut = SimContactDao.create(getContext());

            sut.importContacts(Arrays.asList(
                    new SimContact(1, "Test importJustName", null, null)
            ), mAccount);

            Cursor cursor = queryAllDataInAccount();

            assertThat(cursor, ContactsMatchers.hasCount(1));
            assertThat(cursor, hasName("Test importJustName"));
            cursor.close();
        }

        @Test
        public void importContactWhichOnlyHasPhone() throws Exception {
            final SimContactDao sut = SimContactDao.create(getContext());

            sut.importContacts(Arrays.asList(
                    new SimContact(1, null, "15095550111", null)
            ), mAccount);

            Cursor cursor = queryAllDataInAccount();

            assertThat(cursor, ContactsMatchers.hasCount(1));
            assertThat(cursor, hasPhone("15095550111"));
            cursor.close();
        }

        @Test
        public void ignoresEmptyContacts() throws Exception {
            final SimContactDao sut = SimContactDao.create(getContext());

            // This probably isn't possible but we'll test it to demonstrate expected behavior and
            // just in case it does occur
            sut.importContacts(Arrays.asList(
                    new SimContact(1, null, null, null),
                    new SimContact(2, null, null, null),
                    new SimContact(3, null, null, null),
                    new SimContact(4, "Not null", null, null)
            ), mAccount);

            final Cursor contactsCursor = queryAllRawContactsInAccount();
            assertThat(contactsCursor, ContactsMatchers.hasCount(1));
            contactsCursor.close();

            final Cursor dataCursor = queryAllDataInAccount();
            assertThat(dataCursor, ContactsMatchers.hasCount(1));

            dataCursor.close();
        }

        /**
         * Tests importing a large number of contacts
         *
         * Make sure that {@link android.os.TransactionTooLargeException} is not thrown
         */
        @Test
        public void largeImport() throws Exception {
            final SimContactDao sut = SimContactDao.create(getContext());

            final List<SimContact> contacts = new ArrayList<>();

            for (int i = 0; i < MAX_SIM_CONTACTS; i++) {
                contacts.add(new SimContact(i + 1, "Contact " + (i + 1), randomPhone(),
                        new String[] { randomEmail("contact" + (i + 1) + "_")}));
            }

            sut.importContacts(contacts, mAccount);

            final Cursor contactsCursor = queryAllRawContactsInAccount();
            assertThat(contactsCursor, ContactsMatchers.hasCount(MAX_SIM_CONTACTS));
            contactsCursor.close();

            final Cursor dataCursor = queryAllDataInAccount();
            // Each contact has one data row for each of name, phone and email
            assertThat(dataCursor, ContactsMatchers.hasCount(MAX_SIM_CONTACTS * 3));

            dataCursor.close();
        }

        private Cursor queryAllRawContactsInAccount() {
            return new StringableCursor(mResolver.query(ContactsContract.RawContacts.CONTENT_URI,
                    null, ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
                            ContactsContract.RawContacts.ACCOUNT_TYPE+ "=?",
                    new String[] {
                            mAccount.name,
                            mAccount.type
                    }, null));
        }

        private Cursor queryAllDataInAccount() {
            return new StringableCursor(mResolver.query(Data.CONTENT_URI, null,
                    ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
                            ContactsContract.RawContacts.ACCOUNT_TYPE+ "=?",
                    new String[] {
                            mAccount.name,
                            mAccount.type
                    }, null));
        }

        private Cursor queryContactWithName(String name) {
            return new StringableCursor(mResolver.query(Data.CONTENT_URI, null,
                    ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
                            ContactsContract.RawContacts.ACCOUNT_TYPE+ "=? AND " +
                            Data.DISPLAY_NAME + "=?",
                    new String[] {
                            mAccount.name,
                            mAccount.type,
                            name
                    }, null));
        }
    }

    /**
     * Tests for {@link SimContactDao#findAccountsOfExistingSimContacts(List)}
     *
     * These are integration tests that query CP2 so that the SQL will be validated in addition
     * to the detection algorithm
     */
    @SdkSuppress(minSdkVersion = VERSION_CODES.M)
    // Lollipop MR1 is required for removeAccountExplicitly
    @RequiresApi(api = VERSION_CODES.LOLLIPOP_MR1)
    @LargeTest
    @RunWith(AndroidJUnit4.class)
    public static class FindAccountsIntegrationTests {

        private Context mContext;
        private AccountsTestHelper mAccountHelper;
        private List<AccountWithDataSet> mAccounts;
        // We need to generate something distinct to prevent flakiness on devices that may not
        // start with an empty CP2 DB
        private String mNameSuffix;

        private static AccountWithDataSet sSeedAccount;

        @BeforeClass
        public static void setUpClass() throws Exception {
            final AccountsTestHelper helper = new AccountsTestHelper(
                    InstrumentationRegistry.getContext());
            sSeedAccount = helper.addTestAccount(helper.generateAccountName("seedAccount"));

            seedCp2();
        }

        @AfterClass
        public static void tearDownClass() {
            final AccountsTestHelper helper = new AccountsTestHelper(
                    InstrumentationRegistry.getContext());
            helper.removeTestAccount(sSeedAccount);
            sSeedAccount = null;
        }

        @Before
        public void setUp() throws Exception {
            mContext = InstrumentationRegistry.getTargetContext();
            mAccountHelper = new AccountsTestHelper(InstrumentationRegistry.getContext());
            mAccounts = new ArrayList<>();
            mNameSuffix = getClass().getSimpleName() + "At" + System.nanoTime();

            seedCp2();
        }

        @After
        public void tearDown() {
            for (AccountWithDataSet account : mAccounts) {
                mAccountHelper.removeTestAccount(account);
            }
        }

        @Test
        public void returnsEmptyMapWhenNoMatchingContactsExist() {
            mAccounts.add(mAccountHelper.addTestAccount());

            final SimContactDao sut = createDao();

            final List<SimContact> contacts = Arrays.asList(
                    new SimContact(1, "Name 1 " + mNameSuffix, "5550101"),
                    new SimContact(2, "Name 2 " + mNameSuffix, "5550102"),
                    new SimContact(3, "Name 3 " + mNameSuffix, "5550103"),
                    new SimContact(4, "Name 4 " + mNameSuffix, "5550104"));

            final Map<AccountWithDataSet, Set<SimContact>> existing = sut
                    .findAccountsOfExistingSimContacts(contacts);

            assertTrue(existing.isEmpty());
        }

        @Test
        public void hasAccountWithMatchingContactsWhenSingleMatchingContactExists()
                throws Exception {
            final SimContactDao sut = createDao();

            final AccountWithDataSet account = mAccountHelper.addTestAccount(
                    mAccountHelper.generateAccountName("primary_"));
            mAccounts.add(account);

            final SimContact existing1 =
                    new SimContact(2, "Exists 2 " + mNameSuffix, "5550102");
            final SimContact existing2 =
                    new SimContact(4, "Exists 4 " + mNameSuffix, "5550104");

            final List<SimContact> contacts = Arrays.asList(
                    new SimContact(1, "Missing 1 " + mNameSuffix, "5550101"),
                    new SimContact(existing1),
                    new SimContact(3, "Missing 3 " + mNameSuffix, "5550103"),
                    new SimContact(existing2));

            sut.importContacts(Arrays.asList(
                    new SimContact(existing1),
                    new SimContact(existing2)
            ), account);


            final Map<AccountWithDataSet, Set<SimContact>> existing = sut
                    .findAccountsOfExistingSimContacts(contacts);

            assertThat(existing.size(), equalTo(1));
            assertThat(existing.get(account),
                    Matchers.<Set<SimContact>>equalTo(ImmutableSet.of(existing1, existing2)));
        }

        @Test
        public void hasMultipleAccountsWhenMultipleMatchingContactsExist() throws Exception {
            final SimContactDao sut = createDao();

            final AccountWithDataSet account1 = mAccountHelper.addTestAccount(
                    mAccountHelper.generateAccountName("account1_"));
            mAccounts.add(account1);
            final AccountWithDataSet account2 = mAccountHelper.addTestAccount(
                    mAccountHelper.generateAccountName("account2_"));
            mAccounts.add(account2);

            final SimContact existsInBoth =
                    new SimContact(2, "Exists Both " + mNameSuffix, "5550102");
            final SimContact existsInAccount1 =
                    new SimContact(4, "Exists 1 " + mNameSuffix, "5550104");
            final SimContact existsInAccount2 =
                    new SimContact(5, "Exists 2 " + mNameSuffix, "5550105");

            final List<SimContact> contacts = Arrays.asList(
                    new SimContact(1, "Missing 1 " + mNameSuffix, "5550101"),
                    new SimContact(existsInBoth),
                    new SimContact(3, "Missing 3 " + mNameSuffix, "5550103"),
                    new SimContact(existsInAccount1),
                    new SimContact(existsInAccount2));

            sut.importContacts(Arrays.asList(
                    new SimContact(existsInBoth),
                    new SimContact(existsInAccount1)
            ), account1);

            sut.importContacts(Arrays.asList(
                    new SimContact(existsInBoth),
                    new SimContact(existsInAccount2)
            ), account2);


            final Map<AccountWithDataSet, Set<SimContact>> existing = sut
                    .findAccountsOfExistingSimContacts(contacts);

            assertThat(existing.size(), equalTo(2));
            assertThat(existing, Matchers.<Map<AccountWithDataSet, Set<SimContact>>>equalTo(
                    ImmutableMap.<AccountWithDataSet, Set<SimContact>>of(
                            account1, ImmutableSet.of(existsInBoth, existsInAccount1),
                            account2, ImmutableSet.of(existsInBoth, existsInAccount2))));
        }

        @Test
        public void matchesByNameIfSimContactHasNoPhone() throws Exception {
            final SimContactDao sut = createDao();

            final AccountWithDataSet account = mAccountHelper.addTestAccount(
                    mAccountHelper.generateAccountName("account_"));
            mAccounts.add(account);

            final SimContact noPhone = new SimContact(1, "Nophone " + mNameSuffix, null);
            final SimContact otherExisting = new SimContact(
                    5, "Exists 1 " + mNameSuffix, "5550105");

            final List<SimContact> contacts = Arrays.asList(
                    new SimContact(noPhone),
                    new SimContact(2, "Name 2 " + mNameSuffix, "5550102"),
                    new SimContact(3, "Name 3 " + mNameSuffix, "5550103"),
                    new SimContact(4, "Name 4 " + mNameSuffix, "5550104"),
                    new SimContact(otherExisting));

            sut.importContacts(Arrays.asList(
                    new SimContact(noPhone),
                    new SimContact(otherExisting)
            ), account);

            final Map<AccountWithDataSet, Set<SimContact>> existing = sut
                    .findAccountsOfExistingSimContacts(contacts);

            assertThat(existing.size(), equalTo(1));
            assertThat(existing.get(account), Matchers.<Set<SimContact>>equalTo(
                    ImmutableSet.of(noPhone, otherExisting)));
        }

        @Test
        public void largeNumberOfSimContacts() throws Exception {
            final SimContactDao sut = createDao();

            final List<SimContact> contacts = new ArrayList<>();
            for (int i = 0; i < MAX_SIM_CONTACTS; i++) {
                contacts.add(new SimContact(
                        i + 1, "Contact " + (i + 1) + " " + mNameSuffix, randomPhone()));
            }
            // The work has to be split into batches to avoid hitting SQL query parameter limits
            // so test contacts that will be at boundary points
            final SimContact imported1 = contacts.get(0);
            final SimContact imported2 = contacts.get(99);
            final SimContact imported3 = contacts.get(100);
            final SimContact imported4 = contacts.get(101);
            final SimContact imported5 = contacts.get(MAX_SIM_CONTACTS - 1);

            final AccountWithDataSet account = mAccountHelper.addTestAccount(
                    mAccountHelper.generateAccountName("account_"));
            mAccounts.add(account);

            sut.importContacts(Arrays.asList(imported1, imported2, imported3, imported4, imported5),
                    account);

            mAccounts.add(account);

            final Map<AccountWithDataSet, Set<SimContact>> existing = sut
                    .findAccountsOfExistingSimContacts(contacts);

            assertThat(existing.size(), equalTo(1));
            assertThat(existing.get(account), Matchers.<Set<SimContact>>equalTo(
                    ImmutableSet.of(imported1, imported2, imported3, imported4, imported5)));

        }

        private SimContactDao createDao() {
            return SimContactDao.create(mContext);
        }

        /**
         * Adds a bunch of random contact data to CP2 to make the test environment more realistic
         */
        private static void seedCp2() throws RemoteException, OperationApplicationException {

            final ArrayList<ContentProviderOperation> ops = new ArrayList<>();

            appendCreateContact("John Smith", sSeedAccount, ops);
            appendCreateContact("Marcus Seed", sSeedAccount, ops);
            appendCreateContact("Gary Seed", sSeedAccount, ops);
            appendCreateContact("Michael Seed", sSeedAccount, ops);
            appendCreateContact("Isaac Seed", sSeedAccount, ops);
            appendCreateContact("Sean Seed", sSeedAccount, ops);
            appendCreateContact("Nate Seed", sSeedAccount, ops);
            appendCreateContact("Andrey Seed", sSeedAccount, ops);
            appendCreateContact("Cody Seed", sSeedAccount, ops);
            appendCreateContact("John Seed", sSeedAccount, ops);
            appendCreateContact("Alex Seed", sSeedAccount, ops);

            InstrumentationRegistry.getTargetContext()
                    .getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
        }

        private static void appendCreateContact(String name, AccountWithDataSet account,
                ArrayList<ContentProviderOperation> ops) {
            final int emailCount = sRandom.nextInt(10);
            final int phoneCount = sRandom.nextInt(5);

            final List<String> phones = new ArrayList<>();
            for (int i = 0; i < phoneCount; i++) {
                phones.add(randomPhone());
            }
            final List<String> emails = new ArrayList<>();
            for (int i = 0; i < emailCount; i++) {
                emails.add(randomEmail(name));
            }
            appendCreateContact(name, phones, emails, account, ops);
        }


        private static void appendCreateContact(String name, List<String> phoneNumbers,
                List<String> emails, AccountWithDataSet account, List<ContentProviderOperation> ops) {
            int index = ops.size();

            ops.add(account.newRawContactOperation());
            ops.add(insertIntoData(name, StructuredName.CONTENT_ITEM_TYPE, index));
            for (String phone : phoneNumbers) {
                ops.add(insertIntoData(phone, Phone.CONTENT_ITEM_TYPE, Phone.TYPE_MOBILE, index));
            }
            for (String email : emails) {
                ops.add(insertIntoData(email, Email.CONTENT_ITEM_TYPE, Email.TYPE_HOME, index));
            }
        }

        private static ContentProviderOperation insertIntoData(String value, String mimeType,
                int idBackReference) {
            return ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.DATA1, value)
                    .withValue(Data.MIMETYPE, mimeType)
                    .withValueBackReference(Data.RAW_CONTACT_ID, idBackReference).build();
        }

        private static ContentProviderOperation insertIntoData(String value, String mimeType,
                int type, int idBackReference) {
            return ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.DATA1, value)
                    .withValue(ContactsContract.Data.DATA2, type)
                    .withValue(Data.MIMETYPE, mimeType)
                    .withValueBackReference(Data.RAW_CONTACT_ID, idBackReference).build();
        }
    }

    /**
     * Tests for {@link SimContactDao#loadContactsForSim(SimCard)}
     *
     * These are unit tests that verify that {@link SimContact}s are created correctly from
     * the cursors that are returned by queries to the IccProvider
     */
    @SmallTest
    @RunWith(AndroidJUnit4.class)
    public static class LoadContactsUnitTests {

        private MockContentProvider mMockSimPhonebookProvider;
        private Context mContext;

        @Before
        public void setUp() {
            mContext = mock(MockContext.class);
            final MockContentResolver mockResolver = new MockContentResolver();
            mMockSimPhonebookProvider = new MockContentProvider();
            mockResolver.addProvider(SimPhonebookContract.AUTHORITY, mMockSimPhonebookProvider);
            when(mContext.getContentResolver()).thenReturn(mockResolver);
        }


        @Test
        public void createsContactsFromCursor() {
            mMockSimPhonebookProvider.expect(MockContentProvider.Query.forAnyUri())
                    .withDefaultProjection(
                            SimRecords.RECORD_NUMBER, SimRecords.NAME, SimRecords.PHONE_NUMBER)
                    .withAnyProjection()
                    .withAnySelection()
                    .withAnySortOrder()
                    .returnRow(1, "Name One", "5550101")
                    .returnRow(2, "Name Two", "5550102")
                    .returnRow(3, "Name Three", null)
                    .returnRow(4, null, "5550104");

            final SimContactDao sut = SimContactDao.create(mContext);
            final List<SimContact> contacts = sut
                    .loadContactsForSim(new SimCard("123", 1, "carrier", "sim", null, "us"));

            assertThat(contacts, equalTo(
                    Arrays.asList(
                            new SimContact(1, "Name One", "5550101", null),
                            new SimContact(2, "Name Two", "5550102", null),
                            new SimContact(3, "Name Three", null, null),
                            new SimContact(4, null, "5550104", null)
                    )));
        }

        @Test
        public void excludesEmptyContactsFromResult() {
            mMockSimPhonebookProvider.expect(MockContentProvider.Query.forAnyUri())
                    .withDefaultProjection(
                            SimRecords.RECORD_NUMBER, SimRecords.NAME, SimRecords.PHONE_NUMBER)
                    .withAnyProjection()
                    .withAnySelection()
                    .withAnySortOrder()
                    .returnRow(1, "Non Empty1", "5550101")
                    .returnRow(2, "", "")
                    .returnRow(3, "Non Empty2", null)
                    .returnRow(4, null, null)
                    .returnRow(5, "", null)
                    .returnRow(6, null, "5550102");

            final SimContactDao sut = SimContactDao.create(mContext);
            final List<SimContact> contacts = sut
                    .loadContactsForSim(new SimCard("123", 1, "carrier", "sim", null, "us"));

            assertThat(contacts, equalTo(
                    Arrays.asList(
                            new SimContact(1, "Non Empty1", "5550101", null),
                            new SimContact(3, "Non Empty2", null, null),
                            new SimContact(6, null, "5550102", null)
                    )));
        }

        @Test
        public void usesSimCardSubscriptionIdIfAvailable() {
            mMockSimPhonebookProvider.expectQuery(SimRecords.getContentUri(2,
                    SimPhonebookContract.ElementaryFiles.EF_ADN))
                    .withDefaultProjection(
                            SimRecords.RECORD_NUMBER, SimRecords.NAME, SimRecords.PHONE_NUMBER)
                    .withAnyProjection()
                    .withAnySelection()
                    .withAnySortOrder()
                    .returnEmptyCursor();

            final SimContactDao sut = SimContactDao.create(mContext);
            sut.loadContactsForSim(new SimCard("123", 2, "carrier", "sim", null, "us"));
            mMockSimPhonebookProvider.verify();
        }

        @Test
        public void returnsEmptyListForEmptyCursor() {
            mMockSimPhonebookProvider.expect(MockContentProvider.Query.forAnyUri())
                    .withDefaultProjection(
                            SimRecords.RECORD_NUMBER, SimRecords.NAME, SimRecords.PHONE_NUMBER)
                    .withAnyProjection()
                    .withAnySelection()
                    .withAnySortOrder()
                    .returnEmptyCursor();

            final SimContactDao sut = SimContactDao.create(mContext);
            List<SimContact> result = sut
                    .loadContactsForSim(new SimCard("123", 1, "carrier", "sim", null, "us"));
            assertTrue(result.isEmpty());
        }

        @Test
        public void returnsEmptyListForNullCursor() {
            mContext = mock(MockContext.class);
            final MockContentResolver mockResolver = new MockContentResolver();
            final ContentProvider mockProvider = mock(android.test.mock.MockContentProvider.class);
            when(mockProvider.query(any(Uri.class), any(String[].class), anyString(),
                    any(String[].class), anyString()))
                    .thenReturn(null);
            when(mockProvider.query(any(Uri.class), any(String[].class), anyString(),
                    any(String[].class), anyString(), any(CancellationSignal.class)))
                    .thenReturn(null);

            mockResolver.addProvider("icc", mockProvider);
            when(mContext.getContentResolver()).thenReturn(mockResolver);

            final SimContactDao sut = SimContactDao.create(mContext);
            final List<SimContact> result = sut
                    .loadContactsForSim(new SimCard("123", 1, "carrier", "sim", null, "us"));
            assertTrue(result.isEmpty());
        }
    }

    @LargeTest
    // suppressed because failed assumptions are reported as test failures by the build server
    @Suppress
    @RunWith(AndroidJUnit4.class)
    public static class LoadContactsIntegrationTest {
        private SimContactsTestHelper mSimTestHelper;
        private ArrayList<ContentProviderOperation> mSimSnapshot;

        @Before
        public void setUp() throws Exception {
            mSimTestHelper = new SimContactsTestHelper();

            mSimTestHelper.assumeSimWritable();
            if (!mSimTestHelper.isSimWritable()) return;

            mSimSnapshot = mSimTestHelper.captureRestoreSnapshot();
            mSimTestHelper.deleteAllSimContacts();
        }

        @After
        public void tearDown() throws Exception {
            mSimTestHelper.restore(mSimSnapshot);
        }

        @Test
        public void readFromSim() {
            mSimTestHelper.addSimContact("Test Simone", "15095550101");
            mSimTestHelper.addSimContact("Test Simtwo", "15095550102");
            mSimTestHelper.addSimContact("Test Simthree", "15095550103");

            final SimContactDao sut = SimContactDao.create(getContext());
            final SimCard sim = sut.getSimCards().get(0);
            final ArrayList<SimContact> contacts = sut.loadContactsForSim(sim);

            assertThat(contacts.get(0), isSimContactWithNameAndPhone("Test Simone", "15095550101"));
            assertThat(contacts.get(1), isSimContactWithNameAndPhone("Test Simtwo", "15095550102"));
            assertThat(contacts.get(2),
                    isSimContactWithNameAndPhone("Test Simthree", "15095550103"));
        }
    }

    private static String randomPhone() {
        return String.format(Locale.US, "1%s55501%02d",
                AREA_CODES[sRandom.nextInt(AREA_CODES.length)],
                sRandom.nextInt(100));
    }

    private static String randomEmail(String name) {
        return String.format("%s%d@example.com", name.replace(" ", ".").toLowerCase(Locale.US),
                1000 + sRandom.nextInt(1000));
    }


    static Context getContext() {
        return InstrumentationRegistry.getTargetContext();
   }
}
