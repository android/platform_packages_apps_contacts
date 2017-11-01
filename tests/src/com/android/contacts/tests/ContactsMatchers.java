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

import static org.hamcrest.Matchers.allOf;

import android.database.Cursor;
import android.provider.ContactsContract;

import com.android.contacts.model.SimContact;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;


/**
 * Has useful {@link org.hamcrest.Matchers}s for the Contacts app
 */
public class ContactsMatchers {

    private ContactsMatchers() {
    }

    /**
     * Matchers for {@link Cursor}s returned by queries to
     * {@link android.provider.ContactsContract.Data#CONTENT_URI}
     */
    public static class DataCursor {

        public static Matcher<Cursor> hasMimeType(String type) {
            return hasValueForColumn(ContactsContract.Data.MIMETYPE, type);
        }

        public static Matcher<Cursor> hasName(final String name) {
            return hasRowMatching(allOf(
                    hasMimeType(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                    hasValueForColumn(
                            ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)));
        }

        public static Matcher<Cursor> hasPhone(final String phone) {
            return hasRowMatching(allOf(
                    hasMimeType(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                    hasValueForColumn(
                            ContactsContract.CommonDataKinds.Phone.NUMBER, phone)));
        }

        public static Matcher<Cursor> hasEmail(final String email) {
            return hasRowMatching(allOf(
                    hasMimeType(ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE),
                    hasValueForColumn(
                            ContactsContract.CommonDataKinds.Email.ADDRESS, email)));
        }
    }

    public static Matcher<Cursor> hasCount(final int count) {
        return new BaseMatcher<Cursor>() {
            @Override
            public boolean matches(Object o) {
                if (!(o instanceof Cursor)) return false;
                return ((Cursor)o).getCount() == count;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Cursor with " + count + " rows");
            }
        };
    }

    public static Matcher<Cursor> hasValueForColumn(final String column, final String value) {
        return new BaseMatcher<Cursor>() {

            @Override
            public boolean matches(Object o) {
                if (!(o instanceof Cursor)) return false;
                final Cursor cursor = (Cursor)o;

                final int index = cursor.getColumnIndexOrThrow(column);
                return value.equals(cursor.getString(index));
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Cursor with " + column + "=" + value);
            }
        };
    }

    public static Matcher<Cursor> hasRowMatching(final Matcher<Cursor> rowMatcher) {
        return new BaseMatcher<Cursor>() {
            @Override
            public boolean matches(Object o) {
                if (!(o instanceof Cursor)) return false;
                final Cursor cursor = (Cursor)o;

                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    if (rowMatcher.matches(cursor)) return true;
                }

                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Cursor with row matching ");
                rowMatcher.describeTo(description);
            }
        };
    }

    public static Matcher<SimContact> isSimContactWithNameAndPhone(final String name,
            final String phone) {
        return new BaseMatcher<SimContact>() {
            @Override
            public boolean matches(Object o) {
                if (!(o instanceof SimContact))  return false;

                SimContact other = (SimContact) o;

                return name.equals(other.getName())
                        && phone.equals(other.getPhone());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("SimContact with name=" + name + " and phone=" +
                        phone);
            }
        };
    }
}
