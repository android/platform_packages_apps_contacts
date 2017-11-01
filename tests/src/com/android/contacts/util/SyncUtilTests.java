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
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Tests for SyncUtil.
 */
@SmallTest
public class SyncUtilTests extends AndroidTestCase {
    private static final String TAG = "SyncUtilTests";

    private static final String GOOGLE_TYPE = "com.google";
    private static final String NOT_GOOGLE_TYPE = "com.abc";
    private static final String ACCOUNT_NAME = "ACCOUNT_NAME";

    private final Account mGoogleAccount;
    private final Account mOtherAccount;

    public SyncUtilTests() {
        mGoogleAccount = new Account(ACCOUNT_NAME, GOOGLE_TYPE);
        mOtherAccount = new Account(ACCOUNT_NAME, NOT_GOOGLE_TYPE);
    }

    public void testIsUnsyncableGoogleAccount() throws Exception {
        // The account names of mGoogleAccount and mOtherAccount are not valid, so both accounts
        // are not syncable.
        assertTrue(SyncUtil.isUnsyncableGoogleAccount(mGoogleAccount));
        assertFalse(SyncUtil.isUnsyncableGoogleAccount(mOtherAccount));
        assertFalse(SyncUtil.isUnsyncableGoogleAccount(null));
    }
}
