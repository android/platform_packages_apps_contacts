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

package com.android.contacts.model.account;

import android.content.Context;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.tests.R;

/**
 * Test case for {@link AccountType}.
 *
 * adb shell am instrument -w -e class com.android.contacts.model.AccountTypeTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class AccountTypeTest extends InstrumentationTestCase {
    public void testGetResourceText() {
        // In this test we use the test package itself as an external package.
        final String packageName = getInstrumentation().getContext().getPackageName();

        final Context c = getInstrumentation().getTargetContext();
        final String DEFAULT = "ABC";

        // Package name null, resId -1, use the default
        assertEquals(DEFAULT, AccountType.getResourceText(c, null, -1, DEFAULT));

        // Resource ID -1, use the default
        assertEquals(DEFAULT, AccountType.getResourceText(c, packageName, -1, DEFAULT));

        // Load from an external package.  (here, we use this test package itself)
        final int externalResID = R.string.test_string;
        assertEquals(getInstrumentation().getContext().getString(externalResID),
                AccountType.getResourceText(c, packageName, externalResID, DEFAULT));

        // Load from the contacts package itself.
        final int internalResId = com.android.contacts.R.string.contactsList;
        assertEquals(c.getString(internalResId),
                AccountType.getResourceText(c, null, internalResId, DEFAULT));
    }

    /**
     * Verify if {@link AccountType#getInviteContactActionLabel} correctly gets the resource ID
     * from {@link AccountType#getInviteContactActionResId}
     */
    public void testGetInviteContactActionLabel() {
        final String packageName = getInstrumentation().getContext().getPackageName();
        final Context c = getInstrumentation().getTargetContext();

        final int externalResID = R.string.test_string;

        AccountType accountType = new AccountType() {
            {
                resourcePackageName = packageName;
                syncAdapterPackageName = packageName;
            }
            @Override protected int getInviteContactActionResId() {
                return externalResID;
            }

            @Override public boolean isGroupMembershipEditable() {
                return false;
            }

            @Override public boolean areContactsWritable() {
                return false;
            }
        };

        assertEquals(getInstrumentation().getContext().getString(externalResID),
                accountType.getInviteContactActionLabel(c));
    }

    public void testDisplayLabelComparator() {
        final AccountTypeForDisplayLabelTest EMPTY = new AccountTypeForDisplayLabelTest("");
        final AccountTypeForDisplayLabelTest NULL = new AccountTypeForDisplayLabelTest(null);
        final AccountTypeForDisplayLabelTest AA = new AccountTypeForDisplayLabelTest("aa");
        final AccountTypeForDisplayLabelTest BBB = new AccountTypeForDisplayLabelTest("bbb");
        final AccountTypeForDisplayLabelTest C = new AccountTypeForDisplayLabelTest("c");

        assertTrue(compareDisplayLabel(AA, BBB) < 0);
        assertTrue(compareDisplayLabel(BBB, C) < 0);
        assertTrue(compareDisplayLabel(AA, C) < 0);
        assertTrue(compareDisplayLabel(AA, AA) == 0);
        assertTrue(compareDisplayLabel(BBB, AA) > 0);

        assertTrue(compareDisplayLabel(EMPTY, AA) < 0);
        assertTrue(compareDisplayLabel(EMPTY, NULL) == 0);
    }

    private int compareDisplayLabel(AccountType lhs, AccountType rhs) {
        return new AccountType.DisplayLabelComparator(
                getInstrumentation().getTargetContext()).compare(lhs, rhs);
    }

    private class AccountTypeForDisplayLabelTest extends AccountType {
        private final String mDisplayLabel;

        public AccountTypeForDisplayLabelTest(String displayLabel) {
            mDisplayLabel = displayLabel;
        }

        @Override
        public CharSequence getDisplayLabel(Context context) {
            return mDisplayLabel;
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return false;
        }

        @Override
        public boolean areContactsWritable() {
            return false;
        }
    }
}
