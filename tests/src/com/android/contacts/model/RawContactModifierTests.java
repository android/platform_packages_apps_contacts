/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.RawContacts;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.contacts.R;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountType.EditType;
import com.android.contacts.model.account.ExchangeAccountType;
import com.android.contacts.model.account.GoogleAccountType;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.test.mocks.ContactsMockContext;
import com.android.contacts.test.mocks.MockAccountTypeManager;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link RawContactModifier} to verify that {@link AccountType}
 * constraints are being enforced correctly.
 */
@LargeTest
public class RawContactModifierTests extends AndroidTestCase {
    public static final String TAG = "EntityModifierTests";

    // From android.content.ContentProviderOperation
    public static final int TYPE_INSERT = 1;

    public static final long VER_FIRST = 100;

    private static final long TEST_ID = 4;
    private static final String TEST_PHONE = "218-555-1212";
    private static final String TEST_NAME = "Adam Young";
    private static final String TEST_NAME2 = "Breanne Duren";
    private static final String TEST_IM = "example@example.com";
    private static final String TEST_POSTAL = "1600 Amphitheatre Parkway";

    private static final String TEST_ACCOUNT_NAME = "unittest@example.com";
    private static final String TEST_ACCOUNT_TYPE = "com.example.unittest";

    private static final String EXCHANGE_ACCT_TYPE = "com.android.exchange";

    @Override
    public void setUp() {
        mContext = getContext();
    }

    public static class MockContactsSource extends AccountType {

        MockContactsSource() {
            try {
                this.accountType = TEST_ACCOUNT_TYPE;

                final DataKind nameKind = new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                        R.string.nameLabelsGroup, -1, true);
                nameKind.typeOverallMax = 1;
                addKind(nameKind);

                // Phone allows maximum 2 home, 1 work, and unlimited other, with
                // constraint of 5 numbers maximum.
                final DataKind phoneKind = new DataKind(
                        Phone.CONTENT_ITEM_TYPE, -1, 10, true);

                phoneKind.typeOverallMax = 5;
                phoneKind.typeColumn = Phone.TYPE;
                phoneKind.typeList = Lists.newArrayList();
                phoneKind.typeList.add(new EditType(Phone.TYPE_HOME, -1).setSpecificMax(2));
                phoneKind.typeList.add(new EditType(Phone.TYPE_WORK, -1).setSpecificMax(1));
                phoneKind.typeList.add(new EditType(Phone.TYPE_FAX_WORK, -1).setSecondary(true));
                phoneKind.typeList.add(new EditType(Phone.TYPE_OTHER, -1));

                phoneKind.fieldList = Lists.newArrayList();
                phoneKind.fieldList.add(new EditField(Phone.NUMBER, -1, -1));
                phoneKind.fieldList.add(new EditField(Phone.LABEL, -1, -1));

                addKind(phoneKind);

                // Email is unlimited
                final DataKind emailKind = new DataKind(Email.CONTENT_ITEM_TYPE, -1, 10, true);
                emailKind.typeOverallMax = -1;
                emailKind.fieldList = Lists.newArrayList();
                emailKind.fieldList.add(new EditField(Email.DATA, -1, -1));
                addKind(emailKind);

                // IM is only one
                final DataKind imKind = new DataKind(Im.CONTENT_ITEM_TYPE, -1, 10, true);
                imKind.typeOverallMax = 1;
                imKind.fieldList = Lists.newArrayList();
                imKind.fieldList.add(new EditField(Im.DATA, -1, -1));
                addKind(imKind);

                // Organization is only one
                final DataKind orgKind = new DataKind(Organization.CONTENT_ITEM_TYPE, -1, 10, true);
                orgKind.typeOverallMax = 1;
                orgKind.fieldList = Lists.newArrayList();
                orgKind.fieldList.add(new EditField(Organization.COMPANY, -1, -1));
                orgKind.fieldList.add(new EditField(Organization.TITLE, -1, -1));
                addKind(orgKind);
            } catch (DefinitionException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return false;
        }

        @Override
        public boolean areContactsWritable() {
            return true;
        }
    }

    /**
     * Build a {@link AccountType} that has various odd constraints for
     * testing purposes.
     */
    protected AccountType getAccountType() {
        return new MockContactsSource();
    }

    /**
     * Build {@link AccountTypeManager} instance.
     */
    protected AccountTypeManager getAccountTypes(AccountType... types) {
        return new MockAccountTypeManager(types, null);
    }

    /**
     * Build an {@link RawContact} with the requested set of phone numbers.
     */
    protected RawContactDelta getRawContact(Long existingId, ContentValues... entries) {
        final ContentValues contact = new ContentValues();
        if (existingId != null) {
            contact.put(RawContacts._ID, existingId);
        }
        contact.put(RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        contact.put(RawContacts.ACCOUNT_TYPE, TEST_ACCOUNT_TYPE);

        final RawContact before = new RawContact(contact);
        for (ContentValues values : entries) {
            before.addDataItemValues(values);
        }
        return RawContactDelta.fromBefore(before);
    }

    /**
     * Assert this {@link List} contains the given {@link Object}.
     */
    protected void assertContains(List<?> list, Object object) {
        assertTrue("Missing expected value", list.contains(object));
    }

    /**
     * Assert this {@link List} does not contain the given {@link Object}.
     */
    protected void assertNotContains(List<?> list, Object object) {
        assertFalse("Contained unexpected value", list.contains(object));
    }

    /**
     * Insert various rows to test
     * {@link RawContactModifier#getValidTypes(RawContactDelta, DataKind, EditType)}
     */
    public void testValidTypes() {
        // Build a source and pull specific types
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);
        final EditType typeWork = RawContactModifier.getType(kindPhone, Phone.TYPE_WORK);
        final EditType typeOther = RawContactModifier.getType(kindPhone, Phone.TYPE_OTHER);

        List<EditType> validTypes;

        // Add first home, first work
        final RawContactDelta state = getRawContact(TEST_ID);
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        RawContactModifier.insertChild(state, kindPhone, typeWork);

        // Expecting home, other
        validTypes = RawContactModifier.getValidTypes(state, kindPhone, null, true, null, true);
        assertContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertContains(validTypes, typeOther);

        // Add second home
        RawContactModifier.insertChild(state, kindPhone, typeHome);

        // Expecting other
        validTypes = RawContactModifier.getValidTypes(state, kindPhone, null, true, null, true);
        assertNotContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertContains(validTypes, typeOther);

        // Add third and fourth home (invalid, but possible)
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        RawContactModifier.insertChild(state, kindPhone, typeHome);

        // Expecting none
        validTypes = RawContactModifier.getValidTypes(state, kindPhone, null, true, null, true);
        assertNotContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertNotContains(validTypes, typeOther);
    }

    /**
     * Test which valid types there are when trying to update the editor type.
     * {@link RawContactModifier#getValidTypes(RawContactDelta, DataKind, EditType, Boolean)}
     */
    public void testValidTypesWhenUpdating() {
        // Build a source and pull specific types
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);
        final EditType typeWork = RawContactModifier.getType(kindPhone, Phone.TYPE_WORK);
        final EditType typeOther = RawContactModifier.getType(kindPhone, Phone.TYPE_OTHER);

        List<EditType> validTypes;

        // Add first home, first work
        final RawContactDelta state = getRawContact(TEST_ID);
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        RawContactModifier.insertChild(state, kindPhone, typeWork);

        // Update editor type for home.
        validTypes = RawContactModifier.getValidTypes(state, kindPhone, null, true, null, false);
        assertContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertContains(validTypes, typeOther);

        // Add another 3 types. Overall limit is 5.
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        RawContactModifier.insertChild(state, kindPhone, typeOther);
        RawContactModifier.insertChild(state, kindPhone, typeOther);

        // "Other" is valid when updating the editor type.
        validTypes = RawContactModifier.getValidTypes(state, kindPhone, null, true, null, false);
        assertNotContains(validTypes, typeHome);
        assertNotContains(validTypes, typeWork);
        assertContains(validTypes, typeOther);
    }

    /**
     * Test {@link RawContactModifier#canInsert(RawContactDelta, DataKind)} by
     * inserting various rows.
     */
    public void testCanInsert() {
        // Build a source and pull specific types
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);
        final EditType typeWork = RawContactModifier.getType(kindPhone, Phone.TYPE_WORK);
        final EditType typeOther = RawContactModifier.getType(kindPhone, Phone.TYPE_OTHER);

        // Add first home, first work
        final RawContactDelta state = getRawContact(TEST_ID);
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        RawContactModifier.insertChild(state, kindPhone, typeWork);
        assertTrue("Unable to insert", RawContactModifier.canInsert(state, kindPhone));

        // Add two other, which puts us just under "5" overall limit
        RawContactModifier.insertChild(state, kindPhone, typeOther);
        RawContactModifier.insertChild(state, kindPhone, typeOther);
        assertTrue("Unable to insert", RawContactModifier.canInsert(state, kindPhone));

        // Add second home, which should push to snug limit
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        assertFalse("Able to insert", RawContactModifier.canInsert(state, kindPhone));
    }

    /**
     * Test
     * {@link RawContactModifier#getBestValidType(RawContactDelta, DataKind, boolean, int)}
     * by asserting expected best options in various states.
     */
    public void testBestValidType() {
        // Build a source and pull specific types
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);
        final EditType typeWork = RawContactModifier.getType(kindPhone, Phone.TYPE_WORK);
        final EditType typeFaxWork = RawContactModifier.getType(kindPhone, Phone.TYPE_FAX_WORK);
        final EditType typeOther = RawContactModifier.getType(kindPhone, Phone.TYPE_OTHER);

        EditType suggested;

        // Default suggestion should be home
        final RawContactDelta state = getRawContact(TEST_ID);
        suggested = RawContactModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeHome, suggested);

        // Add first home, should now suggest work
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        suggested = RawContactModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeWork, suggested);

        // Add work fax, should still suggest work
        RawContactModifier.insertChild(state, kindPhone, typeFaxWork);
        suggested = RawContactModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeWork, suggested);

        // Add other, should still suggest work
        RawContactModifier.insertChild(state, kindPhone, typeOther);
        suggested = RawContactModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeWork, suggested);

        // Add work, now should suggest other
        RawContactModifier.insertChild(state, kindPhone, typeWork);
        suggested = RawContactModifier.getBestValidType(state, kindPhone, false, Integer.MIN_VALUE);
        assertEquals("Unexpected suggestion", typeOther, suggested);
    }

    public void testIsEmptyEmpty() {
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);

        // Test entirely empty row
        final ContentValues after = new ContentValues();
        final ValuesDelta values = ValuesDelta.fromAfter(after);

        assertTrue("Expected empty", RawContactModifier.isEmpty(values, kindPhone));
    }

    public void testIsEmptyDirectFields() {
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values, but core fields are empty
        final RawContactDelta state = getRawContact(TEST_ID);
        final ValuesDelta values = RawContactModifier.insertChild(state, kindPhone, typeHome);

        assertTrue("Expected empty", RawContactModifier.isEmpty(values, kindPhone));

        // Insert some data to trigger non-empty state
        values.put(Phone.NUMBER, TEST_PHONE);

        assertFalse("Expected non-empty", RawContactModifier.isEmpty(values, kindPhone));
    }

    public void testTrimEmptySingle() {
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values, but core fields are empty
        final RawContactDelta state = getRawContact(TEST_ID);
        RawContactModifier.insertChild(state, kindPhone, typeHome);

        // Build diff, expecting insert for data row and update enforcement
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isInsertCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Trim empty rows and try again, expecting delete of overall contact
        RawContactModifier.trimEmpty(state, source);
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 1, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isDeleteCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testTrimEmptySpaces() {
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values, but values are spaces
        final RawContactDelta state = RawContactDeltaListTests.buildBeforeEntity(mContext, TEST_ID,
                VER_FIRST);
        final ValuesDelta values = RawContactModifier.insertChild(state, kindPhone, typeHome);
        values.put(Phone.NUMBER, "   ");

        // Build diff, expecting insert for data row and update enforcement
        RawContactDeltaListTests.assertDiffPattern(state,
                RawContactDeltaListTests.buildAssertVersion(VER_FIRST),
                RawContactDeltaListTests.buildUpdateAggregationSuspended(),
                RawContactDeltaListTests.buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT,
                        RawContactDeltaListTests.buildDataInsert(values, TEST_ID)),
                RawContactDeltaListTests.buildUpdateAggregationDefault());

        // Trim empty rows and try again, expecting delete of overall contact
        RawContactModifier.trimEmpty(state, source);
        RawContactDeltaListTests.assertDiffPattern(state,
                RawContactDeltaListTests.buildAssertVersion(VER_FIRST),
                RawContactDeltaListTests.buildDelete(RawContacts.CONTENT_URI));
    }

    public void testTrimLeaveValid() {
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Test row that has type values with valid number
        final RawContactDelta state = RawContactDeltaListTests.buildBeforeEntity(mContext, TEST_ID,
                VER_FIRST);
        final ValuesDelta values = RawContactModifier.insertChild(state, kindPhone, typeHome);
        values.put(Phone.NUMBER, TEST_PHONE);

        // Build diff, expecting insert for data row and update enforcement
        RawContactDeltaListTests.assertDiffPattern(state,
                RawContactDeltaListTests.buildAssertVersion(VER_FIRST),
                RawContactDeltaListTests.buildUpdateAggregationSuspended(),
                RawContactDeltaListTests.buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT,
                        RawContactDeltaListTests.buildDataInsert(values, TEST_ID)),
                RawContactDeltaListTests.buildUpdateAggregationDefault());

        // Trim empty rows and try again, expecting no differences
        RawContactModifier.trimEmpty(state, source);
        RawContactDeltaListTests.assertDiffPattern(state,
                RawContactDeltaListTests.buildAssertVersion(VER_FIRST),
                RawContactDeltaListTests.buildUpdateAggregationSuspended(),
                RawContactDeltaListTests.buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT,
                        RawContactDeltaListTests.buildDataInsert(values, TEST_ID)),
                RawContactDeltaListTests.buildUpdateAggregationDefault());
    }

    public void testTrimEmptyUntouched() {
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" that has empty row
        final RawContactDelta state = getRawContact(TEST_ID);
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_ID);
        before.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        state.addEntry(ValuesDelta.fromBefore(before));

        // Build diff, expecting no changes
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Try trimming existing empty, which we shouldn't touch
        RawContactModifier.trimEmpty(state, source);
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 0, diff.size());
    }

    public void testTrimEmptyAfterUpdate() {
        final AccountType source = getAccountType();
        final DataKind kindPhone = source.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" that has row with some phone number
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_ID);
        before.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        before.put(kindPhone.typeColumn, typeHome.rawValue);
        before.put(Phone.NUMBER, TEST_PHONE);
        final RawContactDelta state = getRawContact(TEST_ID, before);

        // Build diff, expecting no changes
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Now update row by changing number to empty string, expecting single update
        final ValuesDelta child = state.getEntry(TEST_ID);
        child.put(Phone.NUMBER, "");
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Now run trim, which should turn that update into delete
        RawContactModifier.trimEmpty(state, source);
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 1, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isDeleteCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testTrimInsertEmpty() {
        final AccountType accountType = getAccountType();
        final AccountTypeManager accountTypes = getAccountTypes(accountType);
        final DataKind kindPhone = accountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Try creating a contact without any child entries
        final RawContactDelta state = getRawContact(null);
        final RawContactDeltaList set = new RawContactDeltaList();
        set.add(state);

        // Build diff, expecting single insert
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isInsertCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Trim empty rows and try again, expecting no insert
        RawContactModifier.trimEmpty(set, accountTypes);
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 0, diff.size());
    }

    public void testTrimInsertInsert() {
        final AccountType accountType = getAccountType();
        final AccountTypeManager accountTypes = getAccountTypes(accountType);
        final DataKind kindPhone = accountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Try creating a contact with single empty entry
        final RawContactDelta state = getRawContact(null);
        RawContactModifier.insertChild(state, kindPhone, typeHome);
        final RawContactDeltaList set = new RawContactDeltaList();
        set.add(state);

        // Build diff, expecting two insert operations
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isInsertCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isInsertCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }

        // Trim empty rows and try again, expecting silence
        RawContactModifier.trimEmpty(set, accountTypes);
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 0, diff.size());
    }

    public void testTrimUpdateRemain() {
        final AccountType accountType = getAccountType();
        final AccountTypeManager accountTypes = getAccountTypes(accountType);
        final DataKind kindPhone = accountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" with two phone numbers
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        first.put(kindPhone.typeColumn, typeHome.rawValue);
        first.put(Phone.NUMBER, TEST_PHONE);

        final ContentValues second = new ContentValues();
        second.put(Data._ID, TEST_ID);
        second.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        second.put(kindPhone.typeColumn, typeHome.rawValue);
        second.put(Phone.NUMBER, TEST_PHONE);

        final RawContactDelta state = getRawContact(TEST_ID, first, second);
        final RawContactDeltaList set = new RawContactDeltaList();
        set.add(state);

        // Build diff, expecting no changes
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Now update row by changing number to empty string, expecting single update
        final ValuesDelta child = state.getEntry(TEST_ID);
        child.put(Phone.NUMBER, "");
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Now run trim, which should turn that update into delete
        RawContactModifier.trimEmpty(set, accountTypes);
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isDeleteCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testTrimUpdateUpdate() {
        final AccountType accountType = getAccountType();
        final AccountTypeManager accountTypes = getAccountTypes(accountType);
        final DataKind kindPhone = accountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
        final EditType typeHome = RawContactModifier.getType(kindPhone, Phone.TYPE_HOME);

        // Build "before" with two phone numbers
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        first.put(kindPhone.typeColumn, typeHome.rawValue);
        first.put(Phone.NUMBER, TEST_PHONE);

        final RawContactDelta state = getRawContact(TEST_ID, first);
        final RawContactDeltaList set = new RawContactDeltaList();
        set.add(state);

        // Build diff, expecting no changes
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 0, diff.size());

        // Now update row by changing number to empty string, expecting single update
        final ValuesDelta child = state.getEntry(TEST_ID);
        child.put(Phone.NUMBER, "");
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 3, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }

        // Now run trim, which should turn into deleting the whole contact
        RawContactModifier.trimEmpty(set, accountTypes);
        diff.clear();
        state.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 1, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isDeleteCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testParseExtrasExistingName() {
        final AccountType accountType = getAccountType();

        // Build "before" name
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        first.put(StructuredName.GIVEN_NAME, TEST_NAME);

        // Parse extras, making sure we keep single name
        final RawContactDelta state = getRawContact(TEST_ID, first);
        final Bundle extras = new Bundle();
        extras.putString(Insert.NAME, TEST_NAME2);
        RawContactModifier.parseExtras(mContext, accountType, state, extras);

        final int nameCount = state.getMimeEntriesCount(StructuredName.CONTENT_ITEM_TYPE, true);
        assertEquals("Unexpected names", 1, nameCount);
    }

    public void testParseExtrasIgnoreLimit() {
        final AccountType accountType = getAccountType();

        // Build "before" IM
        final ContentValues first = new ContentValues();
        first.put(Data._ID, TEST_ID);
        first.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        first.put(Im.DATA, TEST_IM);

        final RawContactDelta state = getRawContact(TEST_ID, first);
        final int beforeCount = state.getMimeEntries(Im.CONTENT_ITEM_TYPE).size();

        // We should ignore data that doesn't fit account type rules, since account type
        // only allows single Im
        final Bundle extras = new Bundle();
        extras.putInt(Insert.IM_PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        extras.putString(Insert.IM_HANDLE, TEST_IM);
        RawContactModifier.parseExtras(mContext, accountType, state, extras);

        final int afterCount = state.getMimeEntries(Im.CONTENT_ITEM_TYPE).size();
        assertEquals("Broke account type rules", beforeCount, afterCount);
    }

    public void testParseExtrasIgnoreUnhandled() {
        final AccountType accountType = getAccountType();
        final RawContactDelta state = getRawContact(TEST_ID);

        // We should silently ignore types unsupported by account type
        final Bundle extras = new Bundle();
        extras.putString(Insert.POSTAL, TEST_POSTAL);
        RawContactModifier.parseExtras(mContext, accountType, state, extras);

        assertNull("Broke accoun type rules",
                state.getMimeEntries(StructuredPostal.CONTENT_ITEM_TYPE));
    }

    public void testParseExtrasJobTitle() {
        final AccountType accountType = getAccountType();
        final RawContactDelta state = getRawContact(TEST_ID);

        // Make sure that we create partial Organizations
        final Bundle extras = new Bundle();
        extras.putString(Insert.JOB_TITLE, TEST_NAME);
        RawContactModifier.parseExtras(mContext, accountType, state, extras);

        final int count = state.getMimeEntries(Organization.CONTENT_ITEM_TYPE).size();
        assertEquals("Expected to create organization", 1, count);
    }

    public void testMigrateNameFromGoogleToExchange() {
        AccountType oldAccountType = new GoogleAccountType(getContext(), "");
        AccountType newAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        DataKind kind = newAccountType.getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE);

        ContactsMockContext context = new ContactsMockContext(getContext());

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        mockNameValues.put(StructuredName.PREFIX, "prefix");
        mockNameValues.put(StructuredName.GIVEN_NAME, "given");
        mockNameValues.put(StructuredName.MIDDLE_NAME, "middle");
        mockNameValues.put(StructuredName.FAMILY_NAME, "family");
        mockNameValues.put(StructuredName.SUFFIX, "suffix");
        mockNameValues.put(StructuredName.PHONETIC_FAMILY_NAME, "PHONETIC_FAMILY");
        mockNameValues.put(StructuredName.PHONETIC_MIDDLE_NAME, "PHONETIC_MIDDLE");
        mockNameValues.put(StructuredName.PHONETIC_GIVEN_NAME, "PHONETIC_GIVEN");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migrateStructuredName(context, oldState, newState, kind);
        List<ValuesDelta> list = newState.getMimeEntries(StructuredName.CONTENT_ITEM_TYPE);
        assertEquals(1, list.size());

        ContentValues output = list.get(0).getAfter();
        assertEquals("prefix", output.getAsString(StructuredName.PREFIX));
        assertEquals("given", output.getAsString(StructuredName.GIVEN_NAME));
        assertEquals("middle", output.getAsString(StructuredName.MIDDLE_NAME));
        assertEquals("family", output.getAsString(StructuredName.FAMILY_NAME));
        assertEquals("suffix", output.getAsString(StructuredName.SUFFIX));
        // Phonetic middle name isn't supported by Exchange.
        assertEquals("PHONETIC_FAMILY", output.getAsString(StructuredName.PHONETIC_FAMILY_NAME));
        assertEquals("PHONETIC_GIVEN", output.getAsString(StructuredName.PHONETIC_GIVEN_NAME));
    }

    public void testMigratePostalFromGoogleToExchange() {
        AccountType oldAccountType = new GoogleAccountType(getContext(), "");
        AccountType newAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        DataKind kind = newAccountType.getKindForMimetype(StructuredPostal.CONTENT_ITEM_TYPE);

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        mockNameValues.put(StructuredPostal.FORMATTED_ADDRESS, "formatted_address");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migratePostal(oldState, newState, kind);

        List<ValuesDelta> list = newState.getMimeEntries(StructuredPostal.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(1, list.size());
        ContentValues outputValues = list.get(0).getAfter();
        // FORMATTED_ADDRESS isn't supported by Exchange.
        assertNull(outputValues.getAsString(StructuredPostal.FORMATTED_ADDRESS));
        assertEquals("formatted_address", outputValues.getAsString(StructuredPostal.STREET));
    }

    public void testMigratePostalFromExchangeToGoogle() {
        AccountType oldAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        AccountType newAccountType = new GoogleAccountType(getContext(), "");
        DataKind kind = newAccountType.getKindForMimetype(StructuredPostal.CONTENT_ITEM_TYPE);

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        mockNameValues.put(StructuredPostal.COUNTRY, "country");
        mockNameValues.put(StructuredPostal.POSTCODE, "postcode");
        mockNameValues.put(StructuredPostal.REGION, "region");
        mockNameValues.put(StructuredPostal.CITY, "city");
        mockNameValues.put(StructuredPostal.STREET, "street");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migratePostal(oldState, newState, kind);

        List<ValuesDelta> list = newState.getMimeEntries(StructuredPostal.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(1, list.size());
        ContentValues outputValues = list.get(0).getAfter();

        // Check FORMATTED_ADDRESS contains all info.
        String formattedAddress = outputValues.getAsString(StructuredPostal.FORMATTED_ADDRESS);
        assertNotNull(formattedAddress);
        assertTrue(formattedAddress.contains("country"));
        assertTrue(formattedAddress.contains("postcode"));
        assertTrue(formattedAddress.contains("region"));
        assertTrue(formattedAddress.contains("postcode"));
        assertTrue(formattedAddress.contains("city"));
        assertTrue(formattedAddress.contains("street"));
    }

    public void testMigrateEventFromGoogleToExchange1() {
        testMigrateEventCommon(new GoogleAccountType(getContext(), ""),
                new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE));
    }

    public void testMigrateEventFromExchangeToGoogle() {
        testMigrateEventCommon(new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE),
                new GoogleAccountType(getContext(), ""));
    }

    private void testMigrateEventCommon(AccountType oldAccountType, AccountType newAccountType) {
        DataKind kind = newAccountType.getKindForMimetype(Event.CONTENT_ITEM_TYPE);

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE);
        mockNameValues.put(Event.START_DATE, "1972-02-08");
        mockNameValues.put(Event.TYPE, Event.TYPE_BIRTHDAY);
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migrateEvent(oldState, newState, kind, 1990);

        List<ValuesDelta> list = newState.getMimeEntries(Event.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(1, list.size());  // Anniversary should be dropped.
        ContentValues outputValues = list.get(0).getAfter();

        assertEquals("1972-02-08", outputValues.getAsString(Event.START_DATE));
        assertEquals(Event.TYPE_BIRTHDAY, outputValues.getAsInteger(Event.TYPE).intValue());
    }

    public void testMigrateEventFromGoogleToExchange2() {
        AccountType oldAccountType = new GoogleAccountType(getContext(), "");
        AccountType newAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        DataKind kind = newAccountType.getKindForMimetype(Event.CONTENT_ITEM_TYPE);

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE);
        // No year format is not supported by Exchange.
        mockNameValues.put(Event.START_DATE, "--06-01");
        mockNameValues.put(Event.TYPE, Event.TYPE_BIRTHDAY);
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE);
        mockNameValues.put(Event.START_DATE, "1980-08-02");
        // Anniversary is not supported by Exchange
        mockNameValues.put(Event.TYPE, Event.TYPE_ANNIVERSARY);
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migrateEvent(oldState, newState, kind, 1990);

        List<ValuesDelta> list = newState.getMimeEntries(Event.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(1, list.size());  // Anniversary should be dropped.
        ContentValues outputValues = list.get(0).getAfter();

        // Default year should be used.
        assertEquals("1990-06-01", outputValues.getAsString(Event.START_DATE));
        assertEquals(Event.TYPE_BIRTHDAY, outputValues.getAsInteger(Event.TYPE).intValue());
    }

    public void testMigrateEmailFromGoogleToExchange() {
        AccountType oldAccountType = new GoogleAccountType(getContext(), "");
        AccountType newAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        DataKind kind = newAccountType.getKindForMimetype(Email.CONTENT_ITEM_TYPE);

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        mockNameValues.put(Email.TYPE, Email.TYPE_CUSTOM);
        mockNameValues.put(Email.LABEL, "custom_type");
        mockNameValues.put(Email.ADDRESS, "address1");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        mockNameValues.put(Email.TYPE, Email.TYPE_HOME);
        mockNameValues.put(Email.ADDRESS, "address2");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        mockNameValues.put(Email.TYPE, Email.TYPE_WORK);
        mockNameValues.put(Email.ADDRESS, "address3");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        // Exchange can have up to 3 email entries. This 4th entry should be dropped.
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        mockNameValues.put(Email.TYPE, Email.TYPE_OTHER);
        mockNameValues.put(Email.ADDRESS, "address4");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migrateGenericWithTypeColumn(oldState, newState, kind);

        List<ValuesDelta> list = newState.getMimeEntries(Email.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(3, list.size());

        ContentValues outputValues = list.get(0).getAfter();
        assertEquals(Email.TYPE_CUSTOM, outputValues.getAsInteger(Email.TYPE).intValue());
        assertEquals("custom_type", outputValues.getAsString(Email.LABEL));
        assertEquals("address1", outputValues.getAsString(Email.ADDRESS));

        outputValues = list.get(1).getAfter();
        assertEquals(Email.TYPE_HOME, outputValues.getAsInteger(Email.TYPE).intValue());
        assertEquals("address2", outputValues.getAsString(Email.ADDRESS));

        outputValues = list.get(2).getAfter();
        assertEquals(Email.TYPE_WORK, outputValues.getAsInteger(Email.TYPE).intValue());
        assertEquals("address3", outputValues.getAsString(Email.ADDRESS));
    }

    public void testMigrateImFromGoogleToExchange() {
        AccountType oldAccountType = new GoogleAccountType(getContext(), "");
        AccountType newAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        DataKind kind = newAccountType.getKindForMimetype(Im.CONTENT_ITEM_TYPE);

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        // Exchange doesn't support TYPE_HOME
        mockNameValues.put(Im.TYPE, Im.TYPE_HOME);
        mockNameValues.put(Im.PROTOCOL, Im.PROTOCOL_JABBER);
        mockNameValues.put(Im.DATA, "im1");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        // Exchange doesn't support TYPE_WORK
        mockNameValues.put(Im.TYPE, Im.TYPE_WORK);
        mockNameValues.put(Im.PROTOCOL, Im.PROTOCOL_YAHOO);
        mockNameValues.put(Im.DATA, "im2");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        mockNameValues.put(Im.TYPE, Im.TYPE_OTHER);
        mockNameValues.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
        mockNameValues.put(Im.CUSTOM_PROTOCOL, "custom_protocol");
        mockNameValues.put(Im.DATA, "im3");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        // Exchange can have up to 3 IM entries. This 4th entry should be dropped.
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        mockNameValues.put(Im.TYPE, Im.TYPE_OTHER);
        mockNameValues.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        mockNameValues.put(Im.DATA, "im4");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migrateGenericWithTypeColumn(oldState, newState, kind);

        List<ValuesDelta> list = newState.getMimeEntries(Im.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(3, list.size());

        assertNotNull(kind.defaultValues.getAsInteger(Im.TYPE));

        int defaultType = kind.defaultValues.getAsInteger(Im.TYPE);

        ContentValues outputValues = list.get(0).getAfter();
        // HOME should become default type.
        assertEquals(defaultType, outputValues.getAsInteger(Im.TYPE).intValue());
        assertEquals(Im.PROTOCOL_JABBER, outputValues.getAsInteger(Im.PROTOCOL).intValue());
        assertEquals("im1", outputValues.getAsString(Im.DATA));

        outputValues = list.get(1).getAfter();
        assertEquals(defaultType, outputValues.getAsInteger(Im.TYPE).intValue());
        assertEquals(Im.PROTOCOL_YAHOO, outputValues.getAsInteger(Im.PROTOCOL).intValue());
        assertEquals("im2", outputValues.getAsString(Im.DATA));

        outputValues = list.get(2).getAfter();
        assertEquals(defaultType, outputValues.getAsInteger(Im.TYPE).intValue());
        assertEquals(Im.PROTOCOL_CUSTOM, outputValues.getAsInteger(Im.PROTOCOL).intValue());
        assertEquals("custom_protocol", outputValues.getAsString(Im.CUSTOM_PROTOCOL));
        assertEquals("im3", outputValues.getAsString(Im.DATA));
    }

    public void testMigratePhoneFromGoogleToExchange() {
        AccountType oldAccountType = new GoogleAccountType(getContext(), "");
        AccountType newAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        DataKind kind = newAccountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);

        // Create 5 numbers.
        // - "1" -- HOME
        // - "2" -- WORK
        // - "3" -- CUSTOM
        // - "4" -- WORK
        // - "5" -- WORK_MOBILE
        // Then we convert it to Exchange account type.
        // - "1" -- HOME
        // - "2" -- WORK
        // - "3" -- Because CUSTOM is not supported, it'll be changed to the default, MOBILE
        // - "4" -- WORK
        // - "5" -- WORK_MOBILE not suppoted again, so will be MOBILE.
        // But then, Exchange doesn't support multiple MOBILE numbers, so "5" will be removed.
        // i.e. the result will be:
        // - "1" -- HOME
        // - "2" -- WORK
        // - "3" -- MOBILE
        // - "4" -- WORK

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        mockNameValues.put(Phone.TYPE, Phone.TYPE_HOME);
        mockNameValues.put(Phone.NUMBER, "1");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        mockNameValues.put(Phone.TYPE, Phone.TYPE_WORK);
        mockNameValues.put(Phone.NUMBER, "2");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        // Exchange doesn't support this type. Default to MOBILE
        mockNameValues.put(Phone.TYPE, Phone.TYPE_CUSTOM);
        mockNameValues.put(Phone.LABEL, "custom_type");
        mockNameValues.put(Phone.NUMBER, "3");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        mockNameValues.put(Phone.TYPE, Phone.TYPE_WORK);
        mockNameValues.put(Phone.NUMBER, "4");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));
        mockNameValues = new ContentValues();

        mockNameValues.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        mockNameValues.put(Phone.TYPE, Phone.TYPE_WORK_MOBILE);
        mockNameValues.put(Phone.NUMBER, "5");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migrateGenericWithTypeColumn(oldState, newState, kind);

        List<ValuesDelta> list = newState.getMimeEntries(Phone.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(4, list.size());

        int defaultType = Phone.TYPE_MOBILE;

        ContentValues outputValues = list.get(0).getAfter();
        assertEquals(Phone.TYPE_HOME, outputValues.getAsInteger(Phone.TYPE).intValue());
        assertEquals("1", outputValues.getAsString(Phone.NUMBER));
        outputValues = list.get(1).getAfter();
        assertEquals(Phone.TYPE_WORK, outputValues.getAsInteger(Phone.TYPE).intValue());
        assertEquals("2", outputValues.getAsString(Phone.NUMBER));
        outputValues = list.get(2).getAfter();
        assertEquals(defaultType, outputValues.getAsInteger(Phone.TYPE).intValue());
        assertNull(outputValues.getAsInteger(Phone.LABEL));
        assertEquals("3", outputValues.getAsString(Phone.NUMBER));
        outputValues = list.get(3).getAfter();
        assertEquals(Phone.TYPE_WORK, outputValues.getAsInteger(Phone.TYPE).intValue());
        assertEquals("4", outputValues.getAsString(Phone.NUMBER));
    }

    public void testMigrateOrganizationFromGoogleToExchange() {
        AccountType oldAccountType = new GoogleAccountType(getContext(), "");
        AccountType newAccountType = new ExchangeAccountType(getContext(), "", EXCHANGE_ACCT_TYPE);
        DataKind kind = newAccountType.getKindForMimetype(Organization.CONTENT_ITEM_TYPE);

        RawContactDelta oldState = new RawContactDelta();
        ContentValues mockNameValues = new ContentValues();
        mockNameValues.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
        mockNameValues.put(Organization.COMPANY, "company1");
        mockNameValues.put(Organization.DEPARTMENT, "department1");
        oldState.addEntry(ValuesDelta.fromAfter(mockNameValues));

        RawContactDelta newState = new RawContactDelta();
        RawContactModifier.migrateGenericWithoutTypeColumn(oldState, newState, kind);

        List<ValuesDelta> list = newState.getMimeEntries(Organization.CONTENT_ITEM_TYPE);
        assertNotNull(list);
        assertEquals(1, list.size());

        ContentValues outputValues = list.get(0).getAfter();
        assertEquals("company1", outputValues.getAsString(Organization.COMPANY));
        assertEquals("department1", outputValues.getAsString(Organization.DEPARTMENT));
    }
}
