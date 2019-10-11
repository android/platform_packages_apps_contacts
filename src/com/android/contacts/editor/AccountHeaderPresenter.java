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
package com.android.contacts.editor;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListPopupWindow;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.AccountsListAdapter;
import com.android.contacts.util.UiClosables;

import java.util.List;

/**
 * Controls the display of an account selector or header.
 *
 * TODO: This was mostly copied from {@link RawContactEditorView}. The code in that class
 * should probably be modified to use this instead of leaving it duplicated.
 */
public class AccountHeaderPresenter {

    private static final String KEY_SELECTED_ACCOUNT = "accountHeaderSelectedAccount";

    public interface Observer {
        void onChange(AccountHeaderPresenter sender);

        public static final Observer NONE = new Observer() {
            @Override
            public void onChange(AccountHeaderPresenter sender) {
            }
        };
    }

    private final Context mContext;

    private List<AccountInfo> mAccounts;
    private AccountWithDataSet mCurrentAccount;

    // Account header
    private final View mAccountHeaderContainer;
    private TextView mAccountHeaderType;
    private TextView mAccountHeaderName;
    private ImageView mAccountHeaderIcon;
    private ImageView mAccountHeaderExpanderIcon;

    // This would be different if the account was readonly
    @StringRes
    private int mSelectorTitle = R.string.editor_account_selector_title;

    private Observer mObserver = Observer.NONE;

    public AccountHeaderPresenter(View container) {
        mContext = container.getContext();
        mAccountHeaderContainer = container;
        // mAccountHeaderType is optional and may not be in the container view in which case
        // the variable will be null
        mAccountHeaderType = (TextView) container.findViewById(R.id.account_type);
        mAccountHeaderName = (TextView) container.findViewById(R.id.account_name);
        mAccountHeaderIcon = (ImageView) container.findViewById(R.id.account_type_icon);
        mAccountHeaderExpanderIcon = (ImageView) container.findViewById(R.id.account_expander_icon);
    }

    public void setObserver(Observer observer) {
        mObserver = observer;
    }

    public void setCurrentAccount(@NonNull AccountWithDataSet account) {
        if (mCurrentAccount != null && mCurrentAccount.equals(account)) {
            return;
        }
        mCurrentAccount = account;
        if (mObserver != null) {
            mObserver.onChange(this);
        }
        updateDisplayedAccount();
    }

    public void setAccounts(List<AccountInfo> accounts) {
        mAccounts = accounts;
        // If the current account hasn't been set or it has been removed just use the first
        // account.
        if (mCurrentAccount == null || !AccountInfo.contains(mAccounts, mCurrentAccount)) {
            mCurrentAccount = mAccounts.isEmpty() ? null : accounts.get(0).getAccount();
            mObserver.onChange(this);
        }
        updateDisplayedAccount();
    }

    public AccountWithDataSet getCurrentAccount() {
        return mCurrentAccount != null ? mCurrentAccount : null;
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putParcelable(KEY_SELECTED_ACCOUNT, mCurrentAccount);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        if (mCurrentAccount == null) {
            mCurrentAccount = savedInstanceState.getParcelable(KEY_SELECTED_ACCOUNT);
        }
        updateDisplayedAccount();
    }

    private void updateDisplayedAccount() {
        mAccountHeaderContainer.setVisibility(View.GONE);
        if (mCurrentAccount == null) return;
        if (mAccounts == null) return;

        final String accountLabel = getAccountLabel(mCurrentAccount);

        if (mAccounts.size() > 1) {
            addAccountSelector(accountLabel);
        } else {
            addAccountHeader(accountLabel);
        }
    }

    private void addAccountHeader(String accountLabel) {
        mAccountHeaderContainer.setVisibility(View.VISIBLE);

        // Set the account name
        mAccountHeaderName.setVisibility(View.VISIBLE);
        mAccountHeaderName.setText(accountLabel);

        // Set the account type
        final String selectorTitle = mContext.getResources().getString(mSelectorTitle);
        if (mAccountHeaderType != null) {
            mAccountHeaderType.setText(selectorTitle);
        }

        final AccountInfo accountInfo = AccountInfo.getAccount(mAccounts, mCurrentAccount);

        // Set the icon
        mAccountHeaderIcon.setImageDrawable(accountInfo.getIcon());

        // Set the content description
        mAccountHeaderContainer.setContentDescription(
                EditorUiUtils.getAccountInfoContentDescription(accountLabel,
                        selectorTitle));
    }

    private void addAccountSelector(CharSequence nameLabel) {
        final View.OnClickListener onClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPopup();
            }
        };
        setUpAccountSelector(nameLabel.toString(), onClickListener);
    }

    private void showPopup() {
        final ListPopupWindow popup = new ListPopupWindow(mContext);
        final AccountsListAdapter adapter =
                new AccountsListAdapter(mContext, mAccounts, mCurrentAccount);
        popup.setWidth(mAccountHeaderContainer.getWidth());
        popup.setAnchorView(mAccountHeaderContainer);
        popup.setAdapter(adapter);
        popup.setModal(true);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                    long id) {
                UiClosables.closeQuietly(popup);
                final AccountWithDataSet newAccount = adapter.getItem(position);
                setCurrentAccount(newAccount);
                // Make sure the new selection will be announced once it's changed
                mAccountHeaderContainer.setAccessibilityLiveRegion(
                        View.ACCESSIBILITY_LIVE_REGION_POLITE);
            }
        });
        mAccountHeaderContainer.post(new Runnable() {
            @Override
            public void run() {
                popup.show();
            }
        });
    }

    private void setUpAccountSelector(String nameLabel, View.OnClickListener listener) {
        addAccountHeader(nameLabel);
        // Add handlers for choosing another account to save to.
        mAccountHeaderExpanderIcon.setVisibility(View.VISIBLE);
        // Add the listener to the icon so that it will be announced by talkback as a clickable
        // element
        mAccountHeaderExpanderIcon.setOnClickListener(listener);
        mAccountHeaderContainer.setOnClickListener(listener);
    }

    private String getAccountLabel(AccountWithDataSet account) {
        final AccountInfo accountInfo = AccountInfo.getAccount(mAccounts, account);
        return accountInfo != null ? accountInfo.getNameLabel().toString() : null;
    }
}
