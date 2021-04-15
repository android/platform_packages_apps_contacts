/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.vcard;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.android.contacts.R;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.AccountSelectionUtil;

import java.util.List;

public class SelectAccountActivity extends Activity {
    private static final String LOG_TAG = "SelectAccountActivity";

    public static final String ACCOUNT_NAME = "account_name";
    public static final String ACCOUNT_TYPE = "account_type";
    public static final String DATA_SET = "data_set";

    private class CancelListener
            implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private AccountSelectionUtil.AccountSelectedListener mAccountSelectionListener;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        getWindow().addSystemFlags(android.view.WindowManager.LayoutParams
            .SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        // There's three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final int resId = R.string.import_from_vcf_file;
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(this);
        final List<AccountWithDataSet> accountList = accountTypes.blockForWritableAccounts();
        if (accountList.size() == 0) {
            Log.w(LOG_TAG, "Account does not exist");
            finish();
            return;
        } else if (accountList.size() == 1) {
            final AccountWithDataSet account = accountList.get(0);
            final Intent intent = new Intent();
            intent.putExtra(ACCOUNT_NAME, account.name);
            intent.putExtra(ACCOUNT_TYPE, account.type);
            intent.putExtra(DATA_SET, account.dataSet);
            setResult(RESULT_OK, intent);
            finish();
            return;
        }

        Log.i(LOG_TAG, "The number of available accounts: " + accountList.size());

        // Multiple accounts. Let users to select one.
        mAccountSelectionListener =
                new AccountSelectionUtil.AccountSelectedListener(
                        this, accountList, resId) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        final AccountWithDataSet account = mAccountList.get(which);
                        final Intent intent = new Intent();
                        intent.putExtra(ACCOUNT_NAME, account.name);
                        intent.putExtra(ACCOUNT_TYPE, account.type);
                        intent.putExtra(DATA_SET, account.dataSet);
                        setResult(RESULT_OK, intent);
                        finish();
                    }
                };
        showDialog(resId);
        return;
    }

    @Override
    protected Dialog onCreateDialog(int resId, Bundle bundle) {
        if (resId == R.string.import_from_vcf_file) {
            if (mAccountSelectionListener == null) {
                throw new NullPointerException(
                        "mAccountSelectionListener must not be null.");
            }
            return AccountSelectionUtil.getSelectAccountDialog(this, resId,
                    mAccountSelectionListener,
                    new CancelListener());
        }
        return super.onCreateDialog(resId, bundle);
    }
}
