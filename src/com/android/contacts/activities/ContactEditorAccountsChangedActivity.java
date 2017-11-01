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
 * limitations under the License
 */

package com.android.contacts.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.provider.ContactsContract.Intents;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.editor.ContactEditorUtils;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.AccountsLoader;
import com.android.contacts.util.AccountsListAdapter;
import com.android.contacts.util.ImplicitIntentsUtil;

import java.util.List;

/**
 * This activity can be shown to the user when creating a new contact to inform the user about
 * which account the contact will be saved in. There is also an option to add an account at
 * this time. The {@link Intent} in the activity result will contain an extra
 * {@link #Intents.Insert.ACCOUNT} that contains the {@link AccountWithDataSet} to create
 * the new contact in. If the activity result doesn't contain intent data, then there is no
 * account for this contact.
 */
public class ContactEditorAccountsChangedActivity extends Activity
        implements AccountsLoader.AccountsListener {
    private static final int SUBACTIVITY_ADD_NEW_ACCOUNT = 1;

    private AccountsListAdapter mAccountListAdapter;
    private ContactEditorUtils mEditorUtils;
    private AlertDialog mDialog;

    private final OnItemClickListener mAccountListItemClickListener = new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (mAccountListAdapter == null) {
                return;
            }
            saveAccountAndReturnResult(mAccountListAdapter.getItem(position));
        }
    };

    private final OnClickListener mAddAccountClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Intent intent = ImplicitIntentsUtil.getIntentForAddingGoogleAccount();
            startActivityForResult(intent, SUBACTIVITY_ADD_NEW_ACCOUNT);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (mDialog != null && !mDialog.isShowing()) {
            mDialog.show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mEditorUtils = ContactEditorUtils.create(this);
        AccountsLoader.loadAccounts(this, 0, AccountTypeManager.writableFilter());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SUBACTIVITY_ADD_NEW_ACCOUNT) {
            // If the user canceled the account setup process, then keep this activity visible to
            // the user.
            if (resultCode != RESULT_OK) {
                return;
            }
            // Subactivity was successful, so pass the result back and finish the activity.
            AccountWithDataSet account = mEditorUtils.getCreatedAccount(resultCode, data);
            if (account == null) {
                setResult(resultCode);
                finish();
                return;
            }
            saveAccountAndReturnResult(account);
        }
    }

    private void updateDisplayedAccounts(List<AccountInfo> accounts) {
        final int numAccounts = accounts.size();
        if (numAccounts < 0) {
            throw new IllegalStateException("Cannot have a negative number of accounts");
        }

        final View view;
        if (numAccounts >= 2) {
            // When the user has 2+ writable accounts, show a list of accounts so the user can pick
            // which account to create a contact in.
            view = View.inflate(this,
                    R.layout.contact_editor_accounts_changed_activity_with_picker, null);

            final TextView textView = (TextView) view.findViewById(R.id.text);
            textView.setText(getString(R.string.contact_editor_prompt_multiple_accounts));

            final Button button = (Button) view.findViewById(R.id.add_account_button);
            button.setText(getString(R.string.add_new_account));
            button.setOnClickListener(mAddAccountClickListener);

            final ListView accountListView = (ListView) view.findViewById(R.id.account_list);
            mAccountListAdapter = new AccountsListAdapter(this, accounts);
            accountListView.setAdapter(mAccountListAdapter);
            accountListView.setOnItemClickListener(mAccountListItemClickListener);
        } else if (numAccounts == 1 && !accounts.get(0).getAccount().isNullAccount()) {
            // If the user has 1 writable account we will just show the user a message with 2
            // possible action buttons.
            view = View.inflate(this,
                    R.layout.contact_editor_accounts_changed_activity_with_text, null);

            final TextView textView = (TextView) view.findViewById(R.id.text);
            final Button leftButton = (Button) view.findViewById(R.id.left_button);
            final Button rightButton = (Button) view.findViewById(R.id.right_button);

            final AccountInfo accountInfo = accounts.get(0);
            textView.setText(getString(R.string.contact_editor_prompt_one_account,
                    accountInfo.getNameLabel()));

            // This button allows the user to add a new account to the device and return to
            // this app afterwards.
            leftButton.setText(getString(R.string.add_new_account));
            leftButton.setOnClickListener(mAddAccountClickListener);

            // This button allows the user to continue creating the contact in the specified
            // account.
            rightButton.setText(getString(android.R.string.ok));
            rightButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    saveAccountAndReturnResult(accountInfo.getAccount());
                }
            });
        } else {
            // If the user has 0 writable accounts, we will just show the user a message with 2
            // possible action buttons.
            view = View.inflate(this,
                    R.layout.contact_editor_accounts_changed_activity_with_text, null);

            final TextView textView = (TextView) view.findViewById(R.id.text);
            final Button leftButton = (Button) view.findViewById(R.id.left_button);
            final Button rightButton = (Button) view.findViewById(R.id.right_button);

            textView.setText(getString(R.string.contact_editor_prompt_zero_accounts));

            // This button allows the user to continue editing the contact as a phone-only
            // local contact.
            leftButton.setText(getString(android.R.string.cancel));
            leftButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Remember that the user wants to create local contacts, so the user is not
                    // prompted again with this activity.
                    saveAccountAndReturnResult(AccountWithDataSet.getNullAccount());
                    finish();
                }
            });

            // This button allows the user to add a new account to the device and return to
            // this app afterwards.
            rightButton.setText(getString(R.string.add_account));
            rightButton.setOnClickListener(mAddAccountClickListener);
        }

        if (mDialog != null && mDialog.isShowing()) {
            mDialog.dismiss();
        }
        mDialog = new AlertDialog.Builder(this)
                .setView(view)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
                .create();
        mDialog.show();
    }

    private void saveAccountAndReturnResult(AccountWithDataSet account) {
        // Save this as the default account
        mEditorUtils.saveDefaultAccount(account);

        // Pass account info in activity result intent
        Intent intent = new Intent();
        intent.putExtra(Intents.Insert.EXTRA_ACCOUNT, account);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onAccountsLoaded(List<AccountInfo> accounts) {
        updateDisplayedAccounts(accounts);
    }
}
