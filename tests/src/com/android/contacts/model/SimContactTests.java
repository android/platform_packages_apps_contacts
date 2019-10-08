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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import android.os.Parcel;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SimContactTests {
    @Test
    public void parcelRoundtrip() {
        assertParcelsCorrectly(new SimContact(1, "name1", "phone1",
                new String[] { "email1a", "email1b" }));
        assertParcelsCorrectly(new SimContact(2, "name2", "phone2", null));
        assertParcelsCorrectly(new SimContact(3, "name3", null,
                new String[] { "email3" }));
        assertParcelsCorrectly(new SimContact(4, null, "phone4",
                new String[] { "email4" }));
        assertParcelsCorrectly(new SimContact(5, null, null, null));
        assertParcelsCorrectly(new SimContact(6, "name6", "phone6",
                new String[0]));
    }

    private void assertParcelsCorrectly(SimContact contact) {
        final Parcel parcel = Parcel.obtain();
        parcel.writeParcelable(contact, 0);
        parcel.setDataPosition(0);
        final SimContact unparceled = parcel.readParcelable(
                SimContact.class.getClassLoader());
        assertThat(unparceled, equalTo(contact));
        parcel.recycle();
    }
}
