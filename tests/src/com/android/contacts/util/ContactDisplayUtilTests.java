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

package com.android.contacts.util;

import static android.provider.ContactsContract.CommonDataKinds.Phone;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.R;
import com.android.contacts.preference.ContactsPreferences;

import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for (@link ContactDisplayUtils}
 */
@SmallTest
public class ContactDisplayUtilTests extends AndroidTestCase {

    private static final String NAME_PRIMARY = "Name Primary";
    private static final String NAME_ALTERNATIVE = "Name Alternative";

    @Mock private ContactsPreferences mContactsPreferences;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
    }

    public void testIsCustomPhoneTypeReturnsTrue() {
        assertTrue(ContactDisplayUtils.isCustomPhoneType(Phone.TYPE_CUSTOM));
        assertTrue(ContactDisplayUtils.isCustomPhoneType(Phone.TYPE_ASSISTANT));
    }

    public void testIsCustomPhoneTypeReturnsFalse() {
        assertFalse(ContactDisplayUtils.isCustomPhoneType(Phone.TYPE_HOME));
        assertFalse(ContactDisplayUtils.isCustomPhoneType(Phone.TYPE_FAX_WORK));
        assertFalse(ContactDisplayUtils.isCustomPhoneType(Phone.TYPE_MOBILE));
        assertFalse(ContactDisplayUtils.isCustomPhoneType(Phone.TYPE_OTHER));
    }

    public void testGetPhoneLabelResourceIdReturnsOther() {
        assertEquals(R.string.call_other, ContactDisplayUtils.getPhoneLabelResourceId(null));
    }

    public void testGetPhoneLabelResourceIdReturnsMatchHome() {
        assertEquals(R.string.call_home, ContactDisplayUtils.getPhoneLabelResourceId(
                Phone.TYPE_HOME));
    }

    public void testGetSmsLabelResourceIdReturnsOther() {
        assertEquals(R.string.sms_other, ContactDisplayUtils.getSmsLabelResourceId(null));
    }

    public void testGetSmsLabelResourceIdReturnsMatchHome() {
        assertEquals(R.string.sms_home, ContactDisplayUtils.getSmsLabelResourceId(Phone.TYPE_HOME));
    }

    public void testGetPreferredDisplayName_NullContactsPreferences() {
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredDisplayName(NAME_PRIMARY,
                NAME_ALTERNATIVE, null));
    }

    public void testGetPreferredDisplayName_NullContactsPreferences_NullAlternative() {
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredDisplayName(NAME_PRIMARY, null,
                null));
    }

    public void testGetPreferredDisplayName_NullContactsPreferences_NullPrimary() {
        assertEquals(NAME_ALTERNATIVE, ContactDisplayUtils.getPreferredDisplayName(null,
                NAME_ALTERNATIVE, null));
    }

    public void testGetPreferredDisplayName_NullContactsPreferences_BothNull() {
        assertNull(ContactDisplayUtils.getPreferredDisplayName(null, null, null));
    }

    public void testGetPreferredDisplayName_EmptyAlternative() {
        Mockito.when(mContactsPreferences.getDisplayOrder())
                .thenReturn(ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE);
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredDisplayName(NAME_PRIMARY, "",
                mContactsPreferences));
    }

    public void testGetPreferredDisplayName_InvalidPreference() {
        Mockito.when(mContactsPreferences.getDisplayOrder()).thenReturn(-1);
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredDisplayName(NAME_PRIMARY,
                NAME_ALTERNATIVE, mContactsPreferences));
    }

    public void testGetPreferredDisplayName_Primary() {
        Mockito.when(mContactsPreferences.getDisplayOrder())
                .thenReturn(ContactsPreferences.DISPLAY_ORDER_PRIMARY);
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredDisplayName(NAME_PRIMARY,
                NAME_ALTERNATIVE, mContactsPreferences));
    }

    public void testGetPreferredDisplayName_Alternative() {
        Mockito.when(mContactsPreferences.getDisplayOrder())
                .thenReturn(ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE);
        assertEquals(NAME_ALTERNATIVE, ContactDisplayUtils.getPreferredDisplayName(NAME_PRIMARY,
                NAME_ALTERNATIVE, mContactsPreferences));
    }

    public void testGetPreferredSortName_NullContactsPreferences() {
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredSortName(NAME_PRIMARY,
                NAME_ALTERNATIVE, null));
    }

    public void testGetPreferredSortName_NullContactsPreferences_NullAlternative() {
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredSortName(NAME_PRIMARY, null,
                null));
    }

    public void testGetPreferredSortName_NullContactsPreferences_NullPrimary() {
        assertEquals(NAME_ALTERNATIVE, ContactDisplayUtils.getPreferredSortName(null,
                NAME_ALTERNATIVE, null));
    }

    public void testGetPreferredSortName_NullContactsPreferences_BothNull() {
        assertNull(ContactDisplayUtils.getPreferredSortName(null, null, null));
    }

    public void testGetPreferredSortName_EmptyAlternative() {
        Mockito.when(mContactsPreferences.getSortOrder())
                .thenReturn(ContactsPreferences.SORT_ORDER_ALTERNATIVE);
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredSortName(NAME_PRIMARY, "",
                mContactsPreferences));
    }

    public void testGetPreferredSortName_InvalidPreference() {
        Mockito.when(mContactsPreferences.getSortOrder()).thenReturn(-1);
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredSortName(NAME_PRIMARY,
                NAME_ALTERNATIVE, mContactsPreferences));
    }

    public void testGetPreferredSortName_Primary() {
        Mockito.when(mContactsPreferences.getSortOrder())
                .thenReturn(ContactsPreferences.SORT_ORDER_PRIMARY);
        assertEquals(NAME_PRIMARY, ContactDisplayUtils.getPreferredSortName(NAME_PRIMARY,
                NAME_ALTERNATIVE, mContactsPreferences));
    }

    public void testGetPreferredSortName_Alternative() {
        Mockito.when(mContactsPreferences.getSortOrder())
                .thenReturn(ContactsPreferences.SORT_ORDER_ALTERNATIVE);
        assertEquals(NAME_ALTERNATIVE, ContactDisplayUtils.getPreferredSortName(NAME_PRIMARY,
                NAME_ALTERNATIVE, mContactsPreferences));
    }
}
