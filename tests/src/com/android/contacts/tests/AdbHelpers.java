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

import android.content.Context;
import android.content.OperationApplicationException;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.test.InstrumentationRegistry;

import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.SharedPreferenceUtil;

/**
 * Contains utility methods that can be invoked directly from adb using RunMethodInstrumentation.
 *
 * Example usage:
 * adb shell am instrument -e method addTestAccount -e accountName fooAccount\
 *   -w com.android.contacts.tests/com.android.contacts.RunMethodInstrumentation
 */
public class AdbHelpers {
    private static final String TAG = "AdbHelpers";

    public static void addTestAccount(Context context, Bundle args) {
        final String accountName = args.getString("name");
        if (accountName == null) {
            Log.e(TAG, "args must contain extra \"name\"");
            return;
        }

        new AccountsTestHelper(context).addTestAccount(accountName);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP_MR1)
    public static void removeTestAccount(Context context, Bundle args) {
        final String accountName = args.getString("name");
        if (accountName == null) {
            Log.e(TAG, "args must contain extra \"name\"");
            return;
        }

        final AccountWithDataSet account = new AccountWithDataSet(accountName,
                AccountsTestHelper.TEST_ACCOUNT_TYPE, null);
        new AccountsTestHelper(context).removeTestAccount(account);
    }

    public static void setDefaultAccount(Context context, Bundle args) {
        final String name = args.getString("name");
        final String type = args.getString("type");

        if (name == null || type == null) {
            Log.e(TAG, "args must contain extras \"name\" and \"type\"");
            return;
        }

        new ContactsPreferences(context).setDefaultAccount(
                new AccountWithDataSet(name, type, null));
    }

    public static void clearDefaultAccount(Context context) {
        new ContactsPreferences(context).clearDefaultAccount();
    }

    public static void clearPreferences(Context context) {
        SharedPreferenceUtil.clear(context);
    }

    public static void dumpPreferences(Context context) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "preferences=" + getAppContext().getSharedPreferences(
                    getAppContext().getPackageName(), Context.MODE_PRIVATE).getAll());
        }
    }

    public static void clearSimCard(Context context)
            throws RemoteException, OperationApplicationException {
        new SimContactsTestHelper(context).deleteAllSimContacts();
    }

    private static Context getAppContext() {
        return InstrumentationRegistry.getTargetContext();
    }
}
