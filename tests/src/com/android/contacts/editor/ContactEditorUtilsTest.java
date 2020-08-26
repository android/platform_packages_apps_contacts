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

package com.android.contacts.editor;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;

import static org.junit.Assert.assertTrue;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Test case for {@link ContactEditorUtils}.
 *
 * adb shell am instrument -w -e class com.android.contacts.editor.ContactEditorUtilsTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner

 * <p>It may make sense to just delete or move these tests since the code under test just forwards
 * calls to {@link com.android.contacts.preference.ContactsPreferences} and that logic is already
 * covered by {@link com.android.contacts.preference.ContactsPreferencesTest}
 * </p>
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ContactEditorUtilsTest {
    private ContactEditorUtils mTarget;

    private static final String TYPE1 = "type1";
    private static final String TYPE2 = "type2";
    private static final String TYPE2_EXT = "ext";

    private static final AccountWithDataSet ACCOUNT_1_A = new AccountWithDataSet("a", TYPE1, null);
    private static final AccountWithDataSet ACCOUNT_1_B = new AccountWithDataSet("b", TYPE1, null);

    private static final AccountWithDataSet ACCOUNT_2_A = new AccountWithDataSet("a", TYPE2, null);
    private static final AccountWithDataSet ACCOUNT_2EX_A = new AccountWithDataSet(
            "a", TYPE2, TYPE2_EXT);

    @Before
    public void setUp() throws Exception {
        mTarget = ContactEditorUtils.create(InstrumentationRegistry.getTargetContext());

        // Clear the preferences.
        mTarget.cleanupForTest();
    }

    /**
     * Test for
     * - {@link ContactEditorUtils#saveDefaultAccount}
     * - {@link ContactEditorUtils#getOnlyOrDefaultAccount}
     */
    @Test
    public void testSaveDefaultAccount() {
        mTarget.saveDefaultAccount(null);
        assertNull(mTarget.getOnlyOrDefaultAccount(Collections.<AccountWithDataSet>emptyList()));

        mTarget.saveDefaultAccount(ACCOUNT_1_A);
        assertEquals(ACCOUNT_1_A, mTarget.getOnlyOrDefaultAccount(Collections.
                <AccountWithDataSet>emptyList()));
    }

    /**
     * Tests for
     * {@link ContactEditorUtils#shouldShowAccountChangedNotification(List<AccountWithDataSet>)},
     * starting with 0 accounts.
     */
    @Test
    public void testShouldShowAccountChangedNotification_0Accounts() {
        List<AccountWithDataSet> currentAccounts = new ArrayList<>();
        assertTrue(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // We show the notification here, and user clicked "add account"
        currentAccounts.add(ACCOUNT_1_A);

        // Now we open the contact editor with the new account.

        // When closing the editor, we save the default account.
        mTarget.saveDefaultAccount(ACCOUNT_1_A);

        // Next time the user creates a contact, we don't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // User added a new writable account, ACCOUNT_1_B.
        currentAccounts.add(ACCOUNT_1_B);

        // Since default account is still ACCOUNT_1_A, we don't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // User saved a new contact.  We update the account list and the default account.
        mTarget.saveDefaultAccount(ACCOUNT_1_B);

        // User created another contact.  Now we don't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // User installed a new contact sync adapter...

        // Add new accounts: ACCOUNT_2_A, ACCOUNT_2EX_A.
        currentAccounts.add(ACCOUNT_2_A);
        currentAccounts.add(ACCOUNT_2EX_A);

        // New added account but default account is still not changed, so no notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // User saves a new contact, with a different default account.
        mTarget.saveDefaultAccount(ACCOUNT_2_A);

        // Next time user creates a contact, no notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // Remove ACCOUNT_2EX_A.
        currentAccounts.remove(ACCOUNT_2EX_A);

        // ACCOUNT_2EX_A was not default, so no notification either.
        assertFalse(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // Remove ACCOUNT_2_A, which is default.
        currentAccounts.remove(ACCOUNT_2_A);

        // Now we show the notification.
        assertTrue(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // Do not save the default account, and add a new account now.
        currentAccounts.add(ACCOUNT_2EX_A);

        // No default account, so show notification.
        assertTrue(mTarget.shouldShowAccountChangedNotification(currentAccounts));
    }

    /**
     * Tests for
     * {@link ContactEditorUtils#shouldShowAccountChangedNotification(List<AccountWithDataSet>)},
     * starting with 1 accounts.
     */
    @Test
    public void testShouldShowAccountChangedNotification_1Account() {
        // Always returns false when 1 writable account.
        assertFalse(mTarget.shouldShowAccountChangedNotification(
                Collections.singletonList(ACCOUNT_1_A)));

        // User saves a new contact.
        mTarget.saveDefaultAccount(ACCOUNT_1_A);

        // Next time, no notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification(
                Collections.singletonList(ACCOUNT_1_A)));

        // The rest is the same...
    }

    /**
     * Tests for
     * {@link ContactEditorUtils#shouldShowAccountChangedNotification(List<AccountWithDataSet>)},
     * starting with 0 accounts, and the user selected "local only".
     */
    @Test
    public void testShouldShowAccountChangedNotification_0Account_localOnly() {
        // First launch -- always true.
        assertTrue(mTarget.shouldShowAccountChangedNotification(Collections.
                <AccountWithDataSet>emptyList()));

        // We show the notification here, and user clicked "keep local" and saved an contact.
        mTarget.saveDefaultAccount(AccountWithDataSet.getNullAccount());

        // Now there are no accounts, and default account is null.

        // The user created another contact, but this we shouldn't show the notification.
        assertFalse(mTarget.shouldShowAccountChangedNotification(Collections.
                <AccountWithDataSet>emptyList()));
    }

    @Test
    public void testShouldShowAccountChangedNotification_initial_check() {
        // Prepare 1 account and save it as the default.
        mTarget.saveDefaultAccount(ACCOUNT_1_A);

        // Right after a save, the dialog shouldn't show up.
        assertFalse(mTarget.shouldShowAccountChangedNotification(
                Collections.singletonList(ACCOUNT_1_A)));

        // Remove the default account to emulate broken preferences.
        mTarget.removeDefaultAccountForTest();

        // The dialog shouldn't show up.
        // The logic is, if there's a writable account, we'll pick it as default
        assertFalse(mTarget.shouldShowAccountChangedNotification(
                Collections.singletonList(ACCOUNT_1_A)));
    }

    @Test
    public void testShouldShowAccountChangedNotification_nullAccount() {
        final List<AccountWithDataSet> currentAccounts = new ArrayList<>();
        final AccountWithDataSet nullAccount = AccountWithDataSet.getNullAccount();
        currentAccounts.add(nullAccount);

        assertTrue(mTarget.shouldShowAccountChangedNotification(currentAccounts));

        // User chooses to keep the "device" account as the default
        mTarget.saveDefaultAccount(nullAccount);

        // Right after a save, the dialog shouldn't show up.
        assertFalse(mTarget.shouldShowAccountChangedNotification(currentAccounts));
    }

    private static class MockAccountType extends AccountType {
        private boolean mAreContactsWritable;

        public MockAccountType(String accountType, String dataSet, boolean areContactsWritable) {
            this.accountType = accountType;
            this.dataSet = dataSet;
            mAreContactsWritable = areContactsWritable;
        }

        @Override
        public boolean areContactsWritable() {
            return mAreContactsWritable;
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return true;
        }
    }
}
