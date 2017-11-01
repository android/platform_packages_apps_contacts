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
import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;

import com.android.contacts.compat.CompatUtils;

import com.google.common.collect.Lists;

import java.util.ArrayList;

/**
 * Tests for {@link RawContactDelta} and {@link ValuesDelta}. These tests
 * focus on passing changes across {@link Parcel}, and verifying that they
 * correctly build expected "diff" operations.
 */
@LargeTest
public class RawContactDeltaTests extends AndroidTestCase {
    public static final String TAG = "EntityDeltaTests";

    public static final long TEST_CONTACT_ID = 12;
    public static final long TEST_PHONE_ID = 24;

    public static final String TEST_PHONE_NUMBER_1 = "218-555-1111";
    public static final String TEST_PHONE_NUMBER_2 = "218-555-2222";

    public static final String TEST_ACCOUNT_NAME = "TEST";

    public RawContactDeltaTests() {
        super();
    }

    @Override
    public void setUp() {
        mContext = getContext();
    }

    public static RawContact getRawContact(Context context, long contactId, long phoneId) {
        // Build an existing contact read from database
        final ContentValues contact = new ContentValues();
        contact.put(RawContacts.VERSION, 43);
        contact.put(RawContacts._ID, contactId);

        final ContentValues phone = new ContentValues();
        phone.put(Data._ID, phoneId);
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);
        phone.put(Phone.TYPE, Phone.TYPE_HOME);

        final RawContact before = new RawContact(contact);
        before.addDataItemValues(phone);
        return before;
    }

    /**
     * Test that {@link RawContactDelta#mergeAfter(RawContactDelta)} correctly passes
     * any changes through the {@link Parcel} object. This enforces that
     * {@link RawContactDelta} should be identical when serialized against the same
     * "before" {@link RawContact}.
     */
    public void testParcelChangesNone() {
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);
        final RawContactDelta dest = RawContactDelta.fromBefore(before);

        // Merge modified values and assert they match
        final RawContactDelta merged = RawContactDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    public void testParcelChangesInsert() {
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);
        final RawContactDelta dest = RawContactDelta.fromBefore(before);

        // Add a new row and pass across parcel, should be same
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Merge modified values and assert they match
        final RawContactDelta merged = RawContactDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    public void testParcelChangesUpdate() {
        // Update existing row and pass across parcel, should be same
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);
        final RawContactDelta dest = RawContactDelta.fromBefore(before);

        final ValuesDelta child = source.getEntry(TEST_PHONE_ID);
        child.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        // Merge modified values and assert they match
        final RawContactDelta merged = RawContactDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    public void testParcelChangesDelete() {
        // Delete a row and pass across parcel, should be same
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);
        final RawContactDelta dest = RawContactDelta.fromBefore(before);

        final ValuesDelta child = source.getEntry(TEST_PHONE_ID);
        child.markDeleted();

        // Merge modified values and assert they match
        final RawContactDelta merged = RawContactDelta.mergeAfter(dest, source);
        assertEquals("Unexpected change when merging", source, merged);
    }

    public void testValuesDiffDelete() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final ValuesDelta values = ValuesDelta.fromBefore(before);
        values.markDeleted();

        // Should produce a delete action
        final BuilderWrapper builderWrapper = values.buildDiffWrapper(Data.CONTENT_URI);
        final boolean isDelete = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? builderWrapper.getBuilder().build().isDelete()
                : builderWrapper.getType() == CompatUtils.TYPE_DELETE;
        assertTrue("Didn't produce delete action", isDelete);
    }

    /**
     * Test that {@link RawContactDelta#buildDiffWrapper(ArrayList)} is correctly built for
     * insert, update, and delete cases. This only tests a subset of possible
     * {@link Data} row changes.
     */
    public void testEntityDiffNone() {
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);

        // Assert that writing unchanged produces few operations
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        source.buildDiffWrapper(diff);

        assertTrue("Created changes when none needed", (diff.size() == 0));
    }

    public void testEntityDiffNoneInsert() {
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Assert two operations: insert Data row and enforce version
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        source.buildAssertWrapper(diff);
        source.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 4, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            assertTrue("Expected version enforcement", CompatUtils.isAssertQueryCompat(cpoWrapper));
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isInsertCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(3);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffUpdateInsert() {
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);

        // Update parent contact values
        source.getValues().put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Assert three operations: update Contact, insert Data row, enforce version
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        source.buildAssertWrapper(diff);
        source.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 5, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            assertTrue("Expected version enforcement", CompatUtils.isAssertQueryCompat(cpoWrapper));
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(3);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isInsertCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(4);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffNoneUpdate() {
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);

        // Update existing phone number
        final ValuesDelta child = source.getEntry(TEST_PHONE_ID);
        child.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        // Assert that version is enforced
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        source.buildAssertWrapper(diff);
        source.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 4, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            assertTrue("Expected version enforcement", CompatUtils.isAssertQueryCompat(cpoWrapper));
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(2);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", Data.CONTENT_URI, oper.getUri());
        }
        {
            final CPOWrapper cpoWrapper = diff.get(3);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Expected aggregation mode change", CompatUtils.isUpdateCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffDelete() {
        final RawContact before = getRawContact(mContext, TEST_CONTACT_ID, TEST_PHONE_ID);
        final RawContactDelta source = RawContactDelta.fromBefore(before);

        // Delete entire entity
        source.getValues().markDeleted();

        // Assert two operations: delete Contact and enforce version
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        source.buildAssertWrapper(diff);
        source.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            assertTrue("Expected version enforcement", CompatUtils.isAssertQueryCompat(cpoWrapper));
        }
        {
            final CPOWrapper cpoWrapper = diff.get(1);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isDeleteCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffInsert() {
        // Insert a RawContact
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final ValuesDelta values = ValuesDelta.fromAfter(after);
        final RawContactDelta source = new RawContactDelta(values);

        // Assert two operations: insert Contact and enforce version
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        source.buildAssertWrapper(diff);
        source.buildDiffWrapper(diff);
        assertEquals("Unexpected operations", 2, diff.size());
        {
            final CPOWrapper cpoWrapper = diff.get(0);
            final ContentProviderOperation oper = cpoWrapper.getOperation();
            assertTrue("Incorrect type", CompatUtils.isInsertCompat(cpoWrapper));
            assertEquals("Incorrect target", RawContacts.CONTENT_URI, oper.getUri());
        }
    }

    public void testEntityDiffInsertInsert() {
        // Insert a RawContact
        final ContentValues after = new ContentValues();
        after.put(RawContacts.ACCOUNT_NAME, TEST_ACCOUNT_NAME);
        after.put(RawContacts.SEND_TO_VOICEMAIL, 1);

        final ValuesDelta values = ValuesDelta.fromAfter(after);
        final RawContactDelta source = new RawContactDelta(values);

        // Insert a new phone number
        final ContentValues phone = new ContentValues();
        phone.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        phone.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);
        phone.put(Phone.TYPE, Phone.TYPE_WORK);
        source.addEntry(ValuesDelta.fromAfter(phone));

        // Assert two operations: delete Contact and enforce version
        final ArrayList<CPOWrapper> diff = Lists.newArrayList();
        source.buildAssertWrapper(diff);
        source.buildDiffWrapper(diff);
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
    }
}
