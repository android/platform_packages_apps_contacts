/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.ContentProviderOperation.Builder;
import android.content.ContentValues;
import android.os.Build;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.compat.CompatUtils;

import junit.framework.TestCase;

/**
 * Tests for  {@link ValuesDelta}. These tests
 * focus on passing changes across {@link android.os.Parcel}, and verifying that they
 * correctly build expected "diff" operations.
 */
@SmallTest
public class ValuesDeltaTests extends TestCase {

    public static final long TEST_PHONE_ID = 24;

    public static final String TEST_PHONE_NUMBER_1 = "218-555-1111";
    public static final String TEST_PHONE_NUMBER_2 = "218-555-2222";

    public void testValuesDiffInsert() {
        final ContentValues after = new ContentValues();
        after.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        final ValuesDelta values = ValuesDelta.fromAfter(after);

        // Should produce an insert action
        final BuilderWrapper builderWrapper = values.buildDiffWrapper(Data.CONTENT_URI);
        final boolean isInsert = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? builderWrapper.getBuilder().build().isInsert()
                : builderWrapper.getType() == CompatUtils.TYPE_INSERT;
        assertTrue("Didn't produce insert action", isInsert);
    }

    /**
     * Test that {@link ValuesDelta#buildDiff(android.net.Uri)} is correctly
     * built for insert, update, and delete cases. Note this only tests behavior
     * for individual {@link Data} rows.
     */
    public void testValuesDiffNone() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final ValuesDelta values = ValuesDelta.fromBefore(before);

        // None action shouldn't produce a builder
        final Builder builder = values.buildDiff(Data.CONTENT_URI);
        assertNull("None action produced a builder", builder);
    }

    public void testValuesDiffUpdate() {
        final ContentValues before = new ContentValues();
        before.put(Data._ID, TEST_PHONE_ID);
        before.put(Phone.NUMBER, TEST_PHONE_NUMBER_1);

        final ValuesDelta values = ValuesDelta.fromBefore(before);
        values.put(Phone.NUMBER, TEST_PHONE_NUMBER_2);

        // Should produce an update action
        final BuilderWrapper builderWrapper = values.buildDiffWrapper(Data.CONTENT_URI);
        final boolean isUpdate = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? builderWrapper.getBuilder().build().isUpdate()
                : builderWrapper.getType() == CompatUtils.TYPE_UPDATE;
        assertTrue("Didn't produce update action", isUpdate);
    }
}
