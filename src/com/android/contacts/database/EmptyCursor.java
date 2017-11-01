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

package com.android.contacts.database;

import android.database.AbstractCursor;
import android.database.CursorIndexOutOfBoundsException;

/**
 * A cursor that is empty.
 * <p>
 * If you want an empty cursor, this class is better than a MatrixCursor because it has less
 * overhead.
 */
final public class EmptyCursor extends AbstractCursor {

    private String[] mColumns;

    public EmptyCursor(String[] columns) {
        this.mColumns = columns;
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public String[] getColumnNames() {
        return mColumns;
    }

    @Override
    public String getString(int column) {
        throw cursorException();
    }

    @Override
    public short getShort(int column) {
        throw cursorException();
    }

    @Override
    public int getInt(int column) {
        throw cursorException();
    }

    @Override
    public long getLong(int column) {
        throw cursorException();
    }

    @Override
    public float getFloat(int column) {
        throw cursorException();
    }

    @Override
    public double getDouble(int column) {
        throw cursorException();
    }

    @Override
    public boolean isNull(int column) {
        throw cursorException();
    }

    private CursorIndexOutOfBoundsException cursorException() {
        return new CursorIndexOutOfBoundsException("Operation not permitted on an empty cursor.");
    }
}
