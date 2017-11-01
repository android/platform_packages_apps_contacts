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
package com.android.contacts.util;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.ContactsContract;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.GoogleAccountType;

import java.util.List;

/**
 * Utilities related to sync.
 */
public final class SyncUtil {
    private static final String TAG = "SyncUtil";

    public static final int SYNC_SETTING_SYNC_ON = 0;
    public static final int SYNC_SETTING_GLOBAL_SYNC_OFF = 1;
    public static final int SYNC_SETTING_ACCOUNT_SYNC_OFF = 2;

    private SyncUtil() {
    }

    public static final boolean isSyncStatusPendingOrActive(Account account) {
        if (account == null) {
            return false;
        }
        return ContentResolver.isSyncPending(account, ContactsContract.AUTHORITY)
                || ContentResolver.isSyncActive(account, ContactsContract.AUTHORITY);
    }

    /**
     * Returns true {@link ContentResolver#isSyncPending(Account, String)} or
     * {@link ContentResolver#isSyncActive(Account, String)} is true for any account in accounts
     */
    public static final boolean isAnySyncing(List<AccountWithDataSet> accounts) {
        for (AccountWithDataSet accountWithDataSet : accounts) {
            if (isSyncStatusPendingOrActive(accountWithDataSet.getAccountOrNull())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given Google account is not syncable.
     */
    public static final boolean isUnsyncableGoogleAccount(Account account) {
        if (account == null || !GoogleAccountType.ACCOUNT_TYPE.equals(account.type)) {
            return false;
        }
        return ContentResolver.getIsSyncable(account, ContactsContract.AUTHORITY) <= 0;
    }

    public static final boolean hasSyncableAccount(AccountTypeManager accountTypeManager) {
        return !accountTypeManager.getWritableGoogleAccounts().isEmpty();
    }

    public static boolean isAlertVisible(Context context, Account account, int reason) {
        if (reason == SYNC_SETTING_GLOBAL_SYNC_OFF) {
            return (SharedPreferenceUtil.getNumOfDismissesForAutoSyncOff(context) == 0);
        } else if (reason == SYNC_SETTING_ACCOUNT_SYNC_OFF && account != null) {
            return (SharedPreferenceUtil.getNumOfDismissesforAccountSyncOff(
                    context, account.name) == 0);
        }
        return false;
    }

    public static int calculateReasonSyncOff(Context context, Account account) {
        // Global sync is turned off
        if (!ContentResolver.getMasterSyncAutomatically()) {
            if (account != null) {
                SharedPreferenceUtil.resetNumOfDismissesForAccountSyncOff(
                        context, account.name);
            }
            return SYNC_SETTING_GLOBAL_SYNC_OFF;
        }

        // Global sync is on, clear the number of times users has dismissed this
        // alert so that next time global sync is off, alert gets displayed again.
        SharedPreferenceUtil.resetNumOfDismissesForAutoSyncOff(context);
        if (account != null) {
            // Account level sync is off
            if (!ContentResolver.getSyncAutomatically(account, ContactsContract.AUTHORITY)) {
                return SYNC_SETTING_ACCOUNT_SYNC_OFF;
            }
            // Account sync is on, clear the number of times users has dismissed this
            // alert so that next time sync is off, alert gets displayed again.
            SharedPreferenceUtil.resetNumOfDismissesForAccountSyncOff(
                    context, account.name);
        }
        return SYNC_SETTING_SYNC_ON;
    }

    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}