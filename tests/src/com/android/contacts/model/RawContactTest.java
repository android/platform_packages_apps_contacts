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
 * limitations under the License
 */

package com.android.contacts.model;

import android.content.ContentValues;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

/**
 * Unit test for {@link RawContact}.
 */
@SmallTest
public class RawContactTest extends TestCase {

    private RawContact buildRawContact() {
        final ContentValues values = new ContentValues();
        values.put("key1", "value1");
        values.put("key2", "value2");

        final ContentValues dataItem = new ContentValues();
        dataItem.put("key3", "value3");
        dataItem.put("key4", "value4");

        final RawContact contact = new RawContact(values);
        contact.addDataItemValues(dataItem);

        return contact;
    }

    private RawContact buildRawContact2() {
        final ContentValues values = new ContentValues();
        values.put("key11", "value11");
        values.put("key22", "value22");

        final ContentValues dataItem = new ContentValues();
        dataItem.put("key33", "value33");
        dataItem.put("key44", "value44");

        final RawContact contact = new RawContact(values);
        contact.addDataItemValues(dataItem);

        return contact;
    }

    public void testNotEquals() {
        final RawContact one = buildRawContact();
        final RawContact two = buildRawContact2();
        assertFalse(one.equals(two));
    }

    public void testEquals() {
        assertEquals(buildRawContact(), buildRawContact());
    }

    public void testParcelable() {
        assertParcelableEquals(buildRawContact());
    }

    private RawContact.NamedDataItem buildNamedDataItem() {
        final ContentValues values = new ContentValues();
        values.put("key1", "value1");
        values.put("key2", "value2");
        final Uri uri = Uri.fromParts("content:", "ssp", "fragment");

        return new RawContact.NamedDataItem(uri, values);
    }

    private RawContact.NamedDataItem buildNamedDataItem2() {
        final ContentValues values = new ContentValues();
        values.put("key11", "value11");
        values.put("key22", "value22");
        final Uri uri = Uri.fromParts("content:", "blah", "blah");

        return new RawContact.NamedDataItem(uri, values);
    }

    public void testNamedDataItemEquals() {
        assertEquals(buildNamedDataItem(), buildNamedDataItem());
    }

    public void testNamedDataItemNotEquals() {
        assertFalse(buildNamedDataItem().equals(buildNamedDataItem2()));
    }

    public void testNamedDataItemParcelable() {
        assertParcelableEquals(buildNamedDataItem());
    }

    private void assertParcelableEquals(Parcelable parcelable) {
        final Parcel parcel = Parcel.obtain();
        try {
            parcel.writeParcelable(parcelable, 0);
            parcel.setDataPosition(0);

            Parcelable out = parcel.readParcelable(parcelable.getClass().getClassLoader());
            assertEquals(parcelable, out);
        } finally {
            parcel.recycle();
        }
    }
}
