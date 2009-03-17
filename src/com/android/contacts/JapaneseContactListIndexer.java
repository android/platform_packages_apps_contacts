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

package com.android.contacts;

import android.database.Cursor;
import android.database.DataSetObserver;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.SectionIndexer;

/**
 * SectionIndexer which is for "phonetically sortable" String. This class heavily depends on the
 * algorithm of the SQL function "GET_PHONETICALLY_SORTABLE_STRING", whose implementation
 * is written in C++.
 */
public final class JapaneseContactListIndexer extends DataSetObserver implements SectionIndexer {
    private static String TAG = "JapaneseContactListIndexer";

    static private final String[] sSections = {
            " ", // Sections of SectionIndexer should start with " " (some components assume it).
            "\u3042", "\u304B", "\u3055", "\u305F", "\u306A", // a, ka, sa, ta, na 
            "\u306F", "\u307E", "\u3084", "\u3089", "\u308F", // ha, ma, ya, ra, wa
            "\uFF21", "\uFF22", "\uFF23", "\uFF24", "\uFF25", // full-width ABCDE
            "\uFF26", "\uFF27", "\uFF28", "\uFF29", "\uFF2A", // full-width FGHIJ
            "\uFF2B", "\uFF2C", "\uFF2D", "\uFF2E", "\uFF2F", // full-width KLMNO
            "\uFF30", "\uFF31", "\uFF32", "\uFF33", "\uFF34", // full-width PQRST
            "\uFF35", "\uFF36", "\uFF37", "\uFF38", "\uFF39", // full-width UVWXY
            "\uFF40", // full-width Z
            "\u6570", "\u8A18" // alphabets, numbers, symbols
            };
    static private final int sSectionsLength = sSections.length;
    
    private int mColumnIndex;
    private Cursor mDataCursor;
    private SparseIntArray mStringMap;
    
    public JapaneseContactListIndexer(Cursor cursor, int columnIndex) {
        int len = sSections.length;
        mColumnIndex = columnIndex;
        mDataCursor = cursor;
        mStringMap = new SparseIntArray(sSectionsLength);
        if (cursor != null) {
            cursor.registerDataSetObserver(this);
        }
    }
    
    public void setCursor(Cursor cursor) {
        if (mDataCursor != null) {
            mDataCursor.unregisterDataSetObserver(this);
        }
        mDataCursor = cursor;
        if (cursor != null) {
            mDataCursor.registerDataSetObserver(this);
        }
    }
    
    private int getSectionCodePoint(int index) {
        if (index < sSections.length - 2) {
            return sSections[index].codePointAt(0);
        } else if (index == sSections.length - 2) {
            return 0xFF66;  // Numbers are mapped from 0xFF66.
        } else {  // index == mSections.length - 1
            return 0xFF70;  // Symbols are mapped from 0xFF70.
        }
    }
    
    public int getPositionForSection(int sectionIndex) {
        final SparseIntArray stringMap = mStringMap;
        final Cursor cursor = mDataCursor;

        if (cursor == null || sectionIndex <= 0) {
            return 0;
        }
        
        if (sectionIndex >= sSectionsLength) {
            sectionIndex = sSectionsLength - 1;
        }

        int savedCursorPos = cursor.getPosition();

        String targetLetter = sSections[sectionIndex];
        int key = targetLetter.codePointAt(0);

        // Check cache map
        {
            int tmp = stringMap.get(key, Integer.MIN_VALUE);
            if (Integer.MIN_VALUE != tmp) {
                return tmp;
            }
        }

        int end = cursor.getCount();
        int pos = 0;

        {
            // Note that sectionIndex > 0.
            int prevLetter = sSections[sectionIndex - 1].codePointAt(0);
            int prevLetterPos = stringMap.get(prevLetter, Integer.MIN_VALUE);
            if (prevLetterPos != Integer.MIN_VALUE) {
                pos = prevLetterPos;
            }
        }
        
        // Do rough binary search if there are a lot of entries.
        while (end - pos > 100) {
            int tmp = (end + pos) / 2;
            cursor.moveToPosition(tmp);
            String sort_name;
            do {
                sort_name = cursor.getString(mColumnIndex);
                if (sort_name == null || sort_name.length() == 0) {
                    // This should not happen, since sort_name field is created
                    // automatically when syncing to a server, or creating/editing
                    // the entry...
                    Log.e(TAG, "sort_name is null or its length is 0. index: " + tmp);
                    cursor.moveToNext();
                    tmp++;
                    continue;
                }
                break;
            } while (tmp < end);
            if (tmp == end) {
                break;
            }
            int codePoint = sort_name.codePointAt(0);
            if (codePoint < getSectionCodePoint(sectionIndex)) {
                pos = tmp;
            } else {
                end = tmp;
            }
        }
        
        for (cursor.moveToPosition(pos); !cursor.isAfterLast(); ++pos, cursor.moveToNext()) {
            String sort_name = cursor.getString(mColumnIndex);
            if (sort_name == null || sort_name.length() == 0) {
                // This should not happen, since sort_name field is created
                // automatically when syncing to a server, or creating/editing
                // the entry...
                Log.e(TAG, "sort_name is null or its length is 0. index: " + pos);
                continue;
            }
            int codePoint = sort_name.codePointAt(0);
            if (codePoint >= getSectionCodePoint(sectionIndex)) {
                break;
            }
        }
        
        stringMap.put(key, pos);
        cursor.moveToPosition(savedCursorPos);
        return pos;
    }
    
    public int getSectionForPosition(int position) {
        // Not used in Contacts. Ignore for now.
        return 0;
    }

    public Object[] getSections() {
        return sSections;
    }

    @Override
    public void onChanged() {
        super.onChanged();
        mStringMap.clear();
    }

    @Override
    public void onInvalidated() {
        super.onInvalidated();
        mStringMap.clear();
    }
}
