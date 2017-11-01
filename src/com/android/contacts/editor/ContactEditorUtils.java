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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;

import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferences;

import java.util.List;

/**
 * Utility methods for the "account changed" notification in the new contact creation flow.
 */
public class ContactEditorUtils {
    private static final String TAG = "ContactEditorUtils";

    private final ContactsPreferences mContactsPrefs;

    private ContactEditorUtils(Context context) {
        mContactsPrefs = new ContactsPreferences(context);
    }

    public static ContactEditorUtils create(Context context) {
        return new ContactEditorUtils(context.getApplicationContext());
    }

    /**
     * Returns a legacy version of the given contactLookupUri if a legacy Uri was originally
     * passed to the contact editor.
     *
     * @param contactLookupUri The Uri to possibly convert to legacy format.
     * @param requestLookupUri The lookup Uri originally passed to the contact editor
     *                         (via Intent data), may be null.
     */
    static Uri maybeConvertToLegacyLookupUri(Context context, Uri contactLookupUri,
            Uri requestLookupUri) {
        final String legacyAuthority = "contacts";
        final String requestAuthority = requestLookupUri == null
                ? null : requestLookupUri.getAuthority();
        if (legacyAuthority.equals(requestAuthority)) {
            // Build a legacy Uri if that is what was requested by caller
            final long contactId = ContentUris.parseId(ContactsContract.Contacts.lookupContact(
                    context.getContentResolver(), contactLookupUri));
            final Uri legacyContentUri = Uri.parse("content://contacts/people");
            return ContentUris.withAppendedId(legacyContentUri, contactId);
        }
        // Otherwise pass back a lookup-style Uri
        return contactLookupUri;
    }

    void cleanupForTest() {
        mContactsPrefs.clearDefaultAccount();
    }

    void removeDefaultAccountForTest() {
        mContactsPrefs.clearDefaultAccount();
    }

    /**
     * Saves the default account, which can later be obtained with {@link #getOnlyOrDefaultAccount}.
     *
     * This should be called when saving a newly created contact.
     *
     * @param defaultAccount the account used to save a newly created contact.
     */
    public void saveDefaultAccount(AccountWithDataSet defaultAccount) {
        if (defaultAccount == null) {
            mContactsPrefs.clearDefaultAccount();
        } else {
            mContactsPrefs.setDefaultAccount(defaultAccount);
        }
    }

    /**
     * @return the first account if there is only a single account or the default account saved
     * with {@link #saveDefaultAccount}.
     *
     * A null return value indicates that there is multiple accounts and a default hasn't been set
     *
     * Also note that the returned account may have been removed already.
     */
    public AccountWithDataSet getOnlyOrDefaultAccount(
            List<AccountWithDataSet> currentWritableAccounts) {
        if (currentWritableAccounts.size() == 1) {
            return currentWritableAccounts.get(0);
        }

        return mContactsPrefs.getDefaultAccount();
    }

    public boolean shouldShowAccountChangedNotification(List<AccountWithDataSet> writableAccounts) {
        return mContactsPrefs.shouldShowAccountChangedNotification(writableAccounts);
    }

    /**
     * Sets the only non-device account to be default if it is not already.
     */
    public void maybeUpdateDefaultAccount(List<AccountWithDataSet> currentWritableAccounts) {
        if (currentWritableAccounts.size() == 1) {
            final AccountWithDataSet onlyAccount = currentWritableAccounts.get(0);
            if (!onlyAccount.isNullAccount()
                    && !onlyAccount.equals(mContactsPrefs.getDefaultAccount())) {
                mContactsPrefs.setDefaultAccount(onlyAccount);
            }
        }
    }

    /**
     * Parses a result from {@link AccountManager#newChooseAccountIntent(Account, List, String[],
     *     String, String, String[], Bundle)} and returns the created {@link Account}, or null if
     * the user has canceled the wizard.
     *
     * <p>Pass the {@code resultCode} and {@code data} parameters passed to
     * {@link Activity#onActivityResult} or {@link android.app.Fragment#onActivityResult}.
     * </p>
     *
     * <p>
     * Note although the return type is {@link AccountWithDataSet}, return values from this method
     * will never have {@link AccountWithDataSet#dataSet} set, as there's no way to create an
     * extension package account from setup wizard.
     * </p>
     */
    public AccountWithDataSet getCreatedAccount(int resultCode, Intent resultData) {
        // Javadoc doesn't say anything about resultCode but that the data intent will be non null
        // on success.
        if (resultData == null) return null;

        final String accountType = resultData.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
        final String accountName = resultData.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);

        // Just in case
        if (TextUtils.isEmpty(accountType) || TextUtils.isEmpty(accountName)) return null;

        return new AccountWithDataSet(accountName, accountType, null);
    }
}
