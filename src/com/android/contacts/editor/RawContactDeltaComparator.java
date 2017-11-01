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

package com.android.contacts.editor;

import android.content.Context;
import android.provider.ContactsContract.RawContacts;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.GoogleAccountType;

import java.util.Comparator;

/**
 * Compares {@link RawContactDelta}s
 */
class RawContactDeltaComparator implements Comparator<RawContactDelta> {

    private Context mContext;

    public RawContactDeltaComparator(Context context) {
        mContext = context;
    }

    @Override
    public int compare(RawContactDelta one, RawContactDelta two) {
        // Check direct equality
        if (one.equals(two)) {
            return 0;
        }

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        String accountType1 = one.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        String dataSet1 = one.getValues().getAsString(RawContacts.DATA_SET);
        final AccountType type1 = accountTypes.getAccountType(accountType1, dataSet1);
        String accountType2 = two.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        String dataSet2 = two.getValues().getAsString(RawContacts.DATA_SET);
        final AccountType type2 = accountTypes.getAccountType(accountType2, dataSet2);

        // Check read-only. Sort read/write before read-only.
        if (!type1.areContactsWritable() && type2.areContactsWritable()) {
            return 1;
        } else if (type1.areContactsWritable() && !type2.areContactsWritable()) {
            return -1;
        }

        // Check account type. Sort Google before non-Google.
        boolean skipAccountTypeCheck = false;
        boolean isGoogleAccount1 = type1 instanceof GoogleAccountType;
        boolean isGoogleAccount2 = type2 instanceof GoogleAccountType;
        if (isGoogleAccount1 && !isGoogleAccount2) {
            return -1;
        } else if (!isGoogleAccount1 && isGoogleAccount2) {
            return 1;
        } else if (isGoogleAccount1 && isGoogleAccount2) {
            skipAccountTypeCheck = true;
        }

        int value;
        if (!skipAccountTypeCheck) {
            // Sort accounts with type before accounts without types.
            if (type1.accountType != null && type2.accountType == null) {
                return -1;
            } else if (type1.accountType == null && type2.accountType != null) {
                return 1;
            }

            if (type1.accountType != null && type2.accountType != null) {
                value = type1.accountType.compareTo(type2.accountType);
                if (value != 0) {
                    return value;
                }
            }

            // Fall back to data set. Sort accounts with data sets before
            // those without.
            if (type1.dataSet != null && type2.dataSet == null) {
                return -1;
            } else if (type1.dataSet == null && type2.dataSet != null) {
                return 1;
            }

            if (type1.dataSet != null && type2.dataSet != null) {
                value = type1.dataSet.compareTo(type2.dataSet);
                if (value != 0) {
                    return value;
                }
            }
        }

        // Check account name
        String oneAccount = one.getAccountName();
        if (oneAccount == null) {
            oneAccount = "";
        }
        String twoAccount = two.getAccountName();
        if (twoAccount == null) {
            twoAccount = "";
        }
        value = oneAccount.compareTo(twoAccount);
        if (value != 0) {
            return value;
        }

        // Both are in the same account, fall back to contact ID
        Long oneId = one.getRawContactId();
        Long twoId = two.getRawContactId();
        if (oneId == null && twoId == null) {
            return 0;
        } else if (oneId == null) {
            return -1;
        } else if (twoId == null) {
            return 1;
        }

        return Long.compare(oneId, twoId);
    }
}
