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
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.contacts.compat.CompatUtils;
import com.android.contacts.model.account.AccountType;

import com.google.common.collect.Lists;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Tests for {@link RawContactDeltaList} which focus on "diff" operations that should
 * create {@link AggregationExceptions} in certain cases.
 */
@LargeTest
public class RawContactDeltaListTests extends AndroidTestCase {
    // From android.content.ContentProviderOperation
    public static final int TYPE_INSERT = 1;
    public static final int TYPE_UPDATE = 2;
    public static final int TYPE_DELETE = 3;
    public static final int TYPE_ASSERT = 4;

    private static final long CONTACT_FIRST = 1;
    private static final long CONTACT_SECOND = 2;

    public static final long CONTACT_BOB = 10;
    public static final long CONTACT_MARY = 11;

    public static final long INSERTED_CONTACT_ID = 3;

    public static final long PHONE_RED = 20;
    public static final long PHONE_GREEN = 21;
    public static final long PHONE_BLUE = 22;

    public static final long EMAIL_YELLOW = 25;

    public static final long VER_FIRST = 100;
    public static final long VER_SECOND = 200;

    public static final String TEST_PHONE = "555-1212";
    public static final String TEST_ACCOUNT = "org.example.test";

    public RawContactDeltaListTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    /**
     * Build a {@link AccountType} that has various odd constraints for
     * testing purposes.
     */
    protected AccountType getAccountType() {
        return new RawContactModifierTests.MockContactsSource();
    }

    static ContentValues getValues(ContentProviderOperation operation)
            throws NoSuchFieldException, IllegalAccessException {
        final Field field = ContentProviderOperation.class.getDeclaredField("mValues");
        field.setAccessible(true);
        return (ContentValues) field.get(operation);
    }

    static RawContactDelta getUpdate(Context context, long rawContactId) {
        final RawContact before = RawContactDeltaTests.getRawContact(context, rawContactId,
                RawContactDeltaTests.TEST_PHONE_ID);
        return RawContactDelta.fromBefore(before);
    }

    static RawContactDelta getInsert() {
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, RawContactDeltaTests.TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final ValuesDelta values = ValuesDelta.fromAfter(after);
        return new RawContactDelta(values);
    }

    static RawContactDeltaList buildSet(RawContactDelta... deltas) {
        final RawContactDeltaList set = new RawContactDeltaList();
        Collections.addAll(set, deltas);
        return set;
    }

    static RawContactDelta buildBeforeEntity(Context context, long rawContactId, long version,
            ContentValues... entries) {
        // Build an existing contact read from database
        final ContentValues contact = new ContentValues();
        contact.put(RawContacts.VERSION, version);
        contact.put(RawContacts._ID, rawContactId);
        final RawContact before = new RawContact(contact);
        for (ContentValues entry : entries) {
            before.addDataItemValues(entry);
        }
        return RawContactDelta.fromBefore(before);
    }

    static RawContactDelta buildAfterEntity(ContentValues... entries) {
        // Build an existing contact read from database
        final ContentValues contact = new ContentValues();
        contact.put(RawContacts.ACCOUNT_TYPE, TEST_ACCOUNT);
        final RawContactDelta after = new RawContactDelta(ValuesDelta.fromAfter(contact));
        for (ContentValues entry : entries) {
            after.addEntry(ValuesDelta.fromAfter(entry));
        }
        return after;
    }

    static ContentValues buildPhone(long phoneId) {
        return buildPhone(phoneId, Long.toString(phoneId));
    }

    static ContentValues buildPhone(long phoneId, String value) {
        final ContentValues values = new ContentValues();
        values.put(Data._ID, phoneId);
        values.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        values.put(Phone.NUMBER, value);
        values.put(Phone.TYPE, Phone.TYPE_HOME);
        return values;
    }

    static ContentValues buildEmail(long emailId) {
        final ContentValues values = new ContentValues();
        values.put(Data._ID, emailId);
        values.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.DATA, Long.toString(emailId));
        values.put(Email.TYPE, Email.TYPE_HOME);
        return values;
    }

    static void insertPhone(RawContactDeltaList set, long rawContactId, ContentValues values) {
        final RawContactDelta match = set.getByRawContactId(rawContactId);
        match.addEntry(ValuesDelta.fromAfter(values));
    }

    static ValuesDelta getPhone(RawContactDeltaList set, long rawContactId, long dataId) {
        final RawContactDelta match = set.getByRawContactId(rawContactId);
        return match.getEntry(dataId);
    }

    static void assertDiffPattern(RawContactDelta delta, CPOWrapper... pattern) {
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        delta.buildAssertWrapper(diff);
        delta.buildDiffWrapper(diff);
        assertDiffPattern(diff, pattern);
    }

    static void assertDiffPattern(RawContactDeltaList set, CPOWrapper... pattern) {
        assertDiffPattern(set.buildDiffWrapper(), pattern);
    }

    static void assertDiffPattern(ArrayList<CPOWrapper> diff, CPOWrapper... pattern) {
        assertEquals("Unexpected operations", pattern.length, diff.size());

        ContentProviderResult[] fakeBackReferences = new ContentProviderResult[diff.size()];
        for (int i = 0; i < pattern.length; i++) {
            final CPOWrapper expected = pattern[i];
            final CPOWrapper found = diff.get(i);

            assertEquals("Unexpected uri",
                    expected.getOperation().getUri(), found.getOperation().getUri());

            final String expectedType = getTypeString(expected);
            final String foundType = getTypeString(found);
            assertEquals("Unexpected type", expectedType, foundType);

            if (CompatUtils.isDeleteCompat(expected)) {
                continue;
            }

            if (CompatUtils.isInsertCompat(found)) {
                fakeBackReferences[i] = new ContentProviderResult(
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, INSERTED_CONTACT_ID));
            } else if (CompatUtils.isUpdateCompat(found)) {
                fakeBackReferences[i] = new ContentProviderResult(1);
            }


            ContentValues expectedValues = expected.getOperation().resolveValueBackReferences(
                    new ContentProviderResult[0], 0);
            ContentValues foundValues = found.getOperation().resolveValueBackReferences(
                    fakeBackReferences, fakeBackReferences.length);
            expectedValues.remove(BaseColumns._ID);
            foundValues.remove(BaseColumns._ID);
            assertEquals("Unexpected values", expectedValues, foundValues);
        }
    }

    static String getTypeString(CPOWrapper cpoWrapper) {
        if (CompatUtils.isAssertQueryCompat(cpoWrapper)) {
            return "TYPE_ASSERT";
        } else if (CompatUtils.isInsertCompat(cpoWrapper)) {
            return "TYPE_INSERT";
        } else if (CompatUtils.isUpdateCompat(cpoWrapper)) {
            return "TYPE_UPDATE";
        } else if (CompatUtils.isDeleteCompat(cpoWrapper)) {
            return "TYPE_DELETE";
        }
        return "TYPE_UNKNOWN";
    }

    static CPOWrapper buildAssertVersion(long version) {
        final ContentValues values = new ContentValues();
        values.put(RawContacts.VERSION, version);
        return buildCPOWrapper(RawContacts.CONTENT_URI, TYPE_ASSERT, values);
    }

    static CPOWrapper buildAggregationModeUpdate(int mode) {
        final ContentValues values = new ContentValues();
        values.put(RawContacts.AGGREGATION_MODE, mode);
        return buildCPOWrapper(RawContacts.CONTENT_URI, TYPE_UPDATE, values);
    }

    static CPOWrapper buildUpdateAggregationSuspended() {
        return buildAggregationModeUpdate(RawContacts.AGGREGATION_MODE_SUSPENDED);
    }

    static CPOWrapper buildUpdateAggregationDefault() {
        return buildAggregationModeUpdate(RawContacts.AGGREGATION_MODE_DEFAULT);
    }

    static CPOWrapper buildUpdateAggregationKeepTogether(long rawContactId) {
        final ContentValues values = new ContentValues();
        values.put(AggregationExceptions.RAW_CONTACT_ID1, rawContactId);
        values.put(AggregationExceptions.RAW_CONTACT_ID2, INSERTED_CONTACT_ID);
        values.put(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        return buildCPOWrapper(AggregationExceptions.CONTENT_URI, TYPE_UPDATE, values);
    }

    static ContentValues buildDataInsert(ValuesDelta values, long rawContactId) {
        final ContentValues insertValues = values.getCompleteValues();
        insertValues.put(Data.RAW_CONTACT_ID, rawContactId);
        return insertValues;
    }

    static CPOWrapper buildDelete(Uri uri) {
        return buildCPOWrapper(uri, TYPE_DELETE, (ContentValues) null);
    }

    static ContentProviderOperation buildOper(Uri uri, int type, ValuesDelta values) {
        return buildOper(uri, type, values.getCompleteValues());
    }

    static ContentProviderOperation buildOper(Uri uri, int type, ContentValues values) {
        switch (type) {
            case TYPE_ASSERT:
                return ContentProviderOperation.newAssertQuery(uri).withValues(values).build();
            case TYPE_INSERT:
                return ContentProviderOperation.newInsert(uri).withValues(values).build();
            case TYPE_UPDATE:
                return ContentProviderOperation.newUpdate(uri).withValues(values).build();
            case TYPE_DELETE:
                return ContentProviderOperation.newDelete(uri).build();
        }
        return null;
    }

    static CPOWrapper buildCPOWrapper(Uri uri, int type, ContentValues values) {
        if (type == TYPE_ASSERT || type == TYPE_INSERT || type == TYPE_UPDATE
                || type == TYPE_DELETE) {
            return new CPOWrapper(buildOper(uri, type, values), type);
        }
        return null;
    }

    static Long getVersion(RawContactDeltaList set, Long rawContactId) {
        return set.getByRawContactId(rawContactId).getValues().getAsLong(RawContacts.VERSION);
    }

    /**
     * Count number of {@link AggregationExceptions} updates contained in the
     * given list of {@link CPOWrapper}.
     */
    static int countExceptionUpdates(ArrayList<CPOWrapper> diff) {
        int updateCount = 0;
        for (CPOWrapper cpoWrapper : diff) {
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            if (AggregationExceptions.CONTENT_URI.equals(oper.getUri())
                    && CompatUtils.isUpdateCompat(cpoWrapper)) {
                updateCount++;
            }
        }
        return updateCount;
    }

    public void testInsert() {
        final RawContactDelta insert = getInsert();
        final RawContactDeltaList set = buildSet(insert);

        // Inserting single shouldn't create rules
        final ArrayList<CPOWrapper> diff = set.buildDiffWrapper();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 0, exceptionCount);
    }

    public void testUpdateUpdate() {
        final RawContactDelta updateFirst = getUpdate(mContext, CONTACT_FIRST);
        final RawContactDelta updateSecond = getUpdate(mContext, CONTACT_SECOND);
        final RawContactDeltaList set = buildSet(updateFirst, updateSecond);

        // Updating two existing shouldn't create rules
        final ArrayList<CPOWrapper> diff = set.buildDiffWrapper();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 0, exceptionCount);
    }

    public void testUpdateInsert() {
        final RawContactDelta update = getUpdate(mContext, CONTACT_FIRST);
        final RawContactDelta insert = getInsert();
        final RawContactDeltaList set = buildSet(update, insert);

        // New insert should only create one rule
        final ArrayList<CPOWrapper> diff = set.buildDiffWrapper();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 1, exceptionCount);
    }

    public void testInsertUpdateInsert() {
        final RawContactDelta insertFirst = getInsert();
        final RawContactDelta update = getUpdate(mContext, CONTACT_FIRST);
        final RawContactDelta insertSecond = getInsert();
        final RawContactDeltaList set = buildSet(insertFirst, update, insertSecond);

        // Two inserts should create two rules to bind against single existing
        final ArrayList<CPOWrapper> diff = set.buildDiffWrapper();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 2, exceptionCount);
    }

    public void testInsertInsertInsert() {
        final RawContactDelta insertFirst = getInsert();
        final RawContactDelta insertSecond = getInsert();
        final RawContactDelta insertThird = getInsert();
        final RawContactDeltaList set = buildSet(insertFirst, insertSecond, insertThird);

        // Three new inserts should create only two binding rules
        final ArrayList<CPOWrapper> diff = set.buildDiffWrapper();
        final int exceptionCount = countExceptionUpdates(diff);
        assertEquals("Unexpected exception updates", 2, exceptionCount);
    }

    public void testMergeDataRemoteInsert() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildPhone(PHONE_RED), buildPhone(PHONE_GREEN)));

        // Merge in second version, verify they match
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertEquals("Unexpected change when merging", second, merged);
    }

    public void testMergeDataLocalUpdateRemoteInsert() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildPhone(PHONE_RED), buildPhone(PHONE_GREEN)));

        // Change the local number to trigger update
        final ValuesDelta phone = getPhone(first, CONTACT_BOB, PHONE_RED);
        phone.put(Phone.NUMBER, TEST_PHONE);

        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_UPDATE, phone.getAfter()),
                buildUpdateAggregationDefault());

        // Merge in the second version, verify diff matches
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildAssertVersion(VER_SECOND),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_UPDATE, phone.getAfter()),
                buildUpdateAggregationDefault());
    }

    public void testMergeDataLocalUpdateRemoteDelete() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildPhone(PHONE_GREEN)));

        // Change the local number to trigger update
        final ValuesDelta phone = getPhone(first, CONTACT_BOB, PHONE_RED);
        phone.put(Phone.NUMBER, TEST_PHONE);

        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_UPDATE, phone.getAfter()),
                buildUpdateAggregationDefault());

        // Merge in the second version, verify that our update changed to
        // insert, since RED was deleted on remote side
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildAssertVersion(VER_SECOND),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT, buildDataInsert(phone, CONTACT_BOB)),
                buildUpdateAggregationDefault());
    }

    public void testMergeDataLocalDeleteRemoteUpdate() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildPhone(PHONE_RED, TEST_PHONE)));

        // Delete phone locally
        final ValuesDelta phone = getPhone(first, CONTACT_BOB, PHONE_RED);
        phone.markDeleted();

        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildUpdateAggregationSuspended(),
                buildDelete(Data.CONTENT_URI),
                buildUpdateAggregationDefault());

        // Merge in the second version, verify that our delete remains
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildAssertVersion(VER_SECOND),
                buildUpdateAggregationSuspended(),
                buildDelete(Data.CONTENT_URI),
                buildUpdateAggregationDefault());
    }

    public void testMergeDataLocalInsertRemoteInsert() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildPhone(PHONE_RED), buildPhone(PHONE_GREEN)));

        // Insert new phone locally
        final ValuesDelta bluePhone = ValuesDelta.fromAfter(buildPhone(PHONE_BLUE));
        first.getByRawContactId(CONTACT_BOB).addEntry(bluePhone);
        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT,
                        buildDataInsert(bluePhone, CONTACT_BOB)),
                buildUpdateAggregationDefault());

        // Merge in the second version, verify that our insert remains
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildAssertVersion(VER_SECOND),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT,
                        buildDataInsert(bluePhone, CONTACT_BOB)),
                buildUpdateAggregationDefault());
    }

    public void testMergeRawContactLocalInsertRemoteInsert() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildPhone(PHONE_RED)), buildBeforeEntity(mContext, CONTACT_MARY,
                VER_SECOND, buildPhone(PHONE_RED)));

        // Add new contact locally, should remain insert
        final ContentValues joePhoneInsert = buildPhone(PHONE_BLUE);
        joePhoneInsert.put(Data.RAW_CONTACT_ID, INSERTED_CONTACT_ID);
        final RawContactDelta joeContact = buildAfterEntity(joePhoneInsert);
        final ContentValues joeContactInsert = joeContact.getValues().getCompleteValues();
        joeContactInsert.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        first.add(joeContact);
        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildCPOWrapper(RawContacts.CONTENT_URI, TYPE_INSERT, joeContactInsert),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT, joePhoneInsert),
                buildAggregationModeUpdate(RawContacts.AGGREGATION_MODE_DEFAULT),
                buildUpdateAggregationKeepTogether(CONTACT_BOB));

        // Merge in the second version, verify that our insert remains
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildAssertVersion(VER_SECOND),
                buildAssertVersion(VER_SECOND),
                buildCPOWrapper(RawContacts.CONTENT_URI, TYPE_INSERT, joeContactInsert),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT, joePhoneInsert),
                buildAggregationModeUpdate(RawContacts.AGGREGATION_MODE_DEFAULT),
                buildUpdateAggregationKeepTogether(CONTACT_BOB));
    }

    public void testMergeRawContactLocalDeleteRemoteDelete() {
        final RawContactDeltaList first = buildSet(
                buildBeforeEntity(mContext, CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)),
                buildBeforeEntity(mContext, CONTACT_MARY, VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(
                buildBeforeEntity(mContext, CONTACT_BOB, VER_SECOND, buildPhone(PHONE_RED)));

        // Remove contact locally
        first.getByRawContactId(CONTACT_MARY).markDeleted();
        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildAssertVersion(VER_FIRST),
                buildDelete(RawContacts.CONTENT_URI));

        // Merge in the second version, verify that our delete isn't needed
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged);
    }

    public void testMergeRawContactLocalUpdateRemoteDelete() {
        final RawContactDeltaList first = buildSet(
                buildBeforeEntity(mContext, CONTACT_BOB, VER_FIRST, buildPhone(PHONE_RED)),
                buildBeforeEntity(mContext, CONTACT_MARY, VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(
                buildBeforeEntity(mContext, CONTACT_BOB, VER_SECOND, buildPhone(PHONE_RED)));

        // Perform local update
        final ValuesDelta phone = getPhone(first, CONTACT_MARY, PHONE_RED);
        phone.put(Phone.NUMBER, TEST_PHONE);
        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildAssertVersion(VER_FIRST),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_UPDATE, phone.getAfter()),
                buildUpdateAggregationDefault());

        final ContentValues phoneInsert = phone.getCompleteValues();
        phoneInsert.put(Data.RAW_CONTACT_ID, INSERTED_CONTACT_ID);
        final ContentValues contactInsert = first.getByRawContactId(CONTACT_MARY).getValues()
                .getCompleteValues();
        contactInsert.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);

        // Merge and verify that update turned into insert
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged,
                buildAssertVersion(VER_SECOND),
                buildCPOWrapper(RawContacts.CONTENT_URI, TYPE_INSERT, contactInsert),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT, phoneInsert),
                buildAggregationModeUpdate(RawContacts.AGGREGATION_MODE_DEFAULT),
                buildUpdateAggregationKeepTogether(CONTACT_BOB));
    }

    public void testMergeUsesNewVersion() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildPhone(PHONE_RED)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildPhone(PHONE_RED)));

        assertEquals((Long) VER_FIRST, getVersion(first, CONTACT_BOB));
        assertEquals((Long) VER_SECOND, getVersion(second, CONTACT_BOB));

        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertEquals((Long) VER_SECOND, getVersion(merged, CONTACT_BOB));
    }

    public void testMergeAfterEnsureAndTrim() {
        final RawContactDeltaList first = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_FIRST, buildEmail(EMAIL_YELLOW)));
        final RawContactDeltaList second = buildSet(buildBeforeEntity(mContext, CONTACT_BOB,
                VER_SECOND, buildEmail(EMAIL_YELLOW)));

        // Ensure we have at least one phone
        final AccountType source = getAccountType();
        final RawContactDelta bobContact = first.getByRawContactId(CONTACT_BOB);
        RawContactModifier.ensureKindExists(bobContact, source, Phone.CONTENT_ITEM_TYPE);
        final ValuesDelta bobPhone = bobContact.getSuperPrimaryEntry(Phone.CONTENT_ITEM_TYPE, true);

        // Make sure the update would insert a row
        assertDiffPattern(first,
                buildAssertVersion(VER_FIRST),
                buildUpdateAggregationSuspended(),
                buildCPOWrapper(Data.CONTENT_URI, TYPE_INSERT,
                        buildDataInsert(bobPhone, CONTACT_BOB)),
                buildUpdateAggregationDefault());

        // Trim values and ensure that we don't insert things
        RawContactModifier.trimEmpty(bobContact, source);
        assertDiffPattern(first);

        // Now re-parent the change, which should remain no-op
        final RawContactDeltaList merged = RawContactDeltaList.mergeAfter(second, first);
        assertDiffPattern(merged);
    }
}
