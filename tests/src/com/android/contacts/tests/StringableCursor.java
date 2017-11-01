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

import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.DatabaseUtils;

/**
 * Wrapper around a cursor with a custom toString that dumps the entire cursor data
 *
 * This is for providing more useful info during debugging and testing.
 */
public class StringableCursor extends CursorWrapper {
    public StringableCursor(Cursor cursor) {
        super(cursor);
    }

    @Override
    public String toString() {
        final Cursor wrapped = getWrappedCursor();

        if (wrapped.getCount() == 0) {
            return "Empty Cursor";
        }

        return DatabaseUtils.dumpCursorToString(wrapped);
    }
}
