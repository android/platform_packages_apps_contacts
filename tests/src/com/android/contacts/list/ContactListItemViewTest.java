/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.list;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.test.ActivityInstrumentationTestCase2;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.widget.TextView;

import com.android.contacts.format.SpannedTestUtils;
import com.android.contacts.preference.ContactsPreferences;

/**
 * Unit tests for {@link com.android.contacts.list.ContactListItemView}.
 *
 * It uses an {@link ActivityInstrumentationTestCase2} for {@link PeopleActivity} because we need
 * to have the style properly setup.
 */
@LargeTest
public class ContactListItemViewTest extends AndroidTestCase {

    //private IntegrationTestUtils mUtils;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // This test requires that the screen be turned on.
        //mUtils = new IntegrationTestUtils(getInstrumentation());
        //mUtils.acquireScreenWakeLock(getInstrumentation().getTargetContext());
    }

    @Override
    protected void tearDown() throws Exception {
        //mUtils.releaseScreenWakeLock();
        super.tearDown();
    }

    public void testShowDisplayName_Simple() {
        Cursor cursor = createCursor("John Doe", "Doe John");
        ContactListItemView view = createView();

        view.showDisplayName(cursor, 0, ContactsPreferences.DISPLAY_ORDER_PRIMARY);

        assertEquals(view.getNameTextView().getText().toString(), "John Doe");
    }

    public void testShowDisplayName_Unknown() {
        Cursor cursor = createCursor("", "");
        ContactListItemView view = createView();

        view.setUnknownNameText("unknown");
        view.showDisplayName(cursor, 0, ContactsPreferences.DISPLAY_ORDER_PRIMARY);

        assertEquals(view.getNameTextView().getText().toString(), "unknown");
    }

    public void testShowDisplayName_WithPrefix() {
        Cursor cursor = createCursor("John Doe", "Doe John");
        ContactListItemView view = createView();

        view.setHighlightedPrefix("DOE");
        view.showDisplayName(cursor, 0, ContactsPreferences.DISPLAY_ORDER_PRIMARY);

        CharSequence seq = view.getNameTextView().getText();
        assertEquals("John Doe", seq.toString());
        SpannedTestUtils.assertPrefixSpan(seq, 5, 7);
        // Talback should be without span tags.
        assertEquals("John Doe", view.getNameTextView().getContentDescription());
        assertFalse("John Doe".equals(seq));
    }

    public void testShowDisplayName_WithPrefixReversed() {
        Cursor cursor = createCursor("John Doe", "Doe John");
        ContactListItemView view = createView();

        view.setHighlightedPrefix("DOE");
        view.showDisplayName(cursor, 0, ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE);

        CharSequence seq = view.getNameTextView().getText();
        assertEquals("John Doe", seq.toString());
        SpannedTestUtils.assertPrefixSpan(seq, 5, 7);
    }

    public void testSetSnippet_Prefix() {
        ContactListItemView view = createView();
        view.setHighlightedPrefix("TEST");
        view.setSnippet("This is a test");

        CharSequence seq = view.getSnippetView().getText();

        assertEquals("This is a test", seq.toString());
        SpannedTestUtils.assertPrefixSpan(seq, 10, 13);
    }

    /** Creates the view to be tested. */
    private ContactListItemView createView() {
        ContactListItemView view = new ContactListItemView(getContext());
        // Set the name view to use a Spannable to represent its content.
        view.getNameTextView().setText("", TextView.BufferType.SPANNABLE);
        return view;
    }

    /**
     * Creates a cursor containing a pair of values.
     *
     * @param name the name to insert in the first column of the cursor
     * @param alternateName the alternate name to insert in the second column of the cursor
     * @return the newly created cursor
     */
    private Cursor createCursor(String name, String alternateName) {
        MatrixCursor cursor = new MatrixCursor(new String[]{"Name", "AlternateName"});
        cursor.moveToFirst();
        cursor.addRow(new Object[]{name, alternateName});
        return cursor;
    }
}
