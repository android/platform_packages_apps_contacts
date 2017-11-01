/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.list;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;

/**
 * Contact list filter parameters.
 */
public class ContactListFilterView extends LinearLayout {

    private static final String TAG = ContactListFilterView.class.getSimpleName();

    private ImageView mIcon;
    private TextView mAccountType;
    private TextView mAccountUserName;
    private RadioButton mRadioButton;
    private ContactListFilter mFilter;
    private boolean mSingleAccount;

    public ContactListFilterView(Context context) {
        super(context);
    }

    public ContactListFilterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setContactListFilter(ContactListFilter filter) {
        mFilter = filter;
    }

    public ContactListFilter getContactListFilter() {
        return mFilter;
    }

    public void setSingleAccount(boolean flag) {
        this.mSingleAccount = flag;
    }

    @Override
    public void setActivated(boolean activated) {
        super.setActivated(activated);
        if (mRadioButton != null) {
            mRadioButton.setChecked(activated);
        } else {
            // We're guarding against null-pointer exceptions,
            // but otherwise this code is not expected to work
            // properly if the button hasn't been initialized.
            Log.wtf(TAG, "radio-button cannot be activated because it is null");
        }
        setContentDescription(generateContentDescription());
    }

    public boolean isChecked() {
        return mRadioButton.isChecked();
    }

    public void bindView(AccountTypeManager accountTypes) {
        if (mAccountType == null) {
            mIcon = (ImageView) findViewById(R.id.icon);
            mAccountType = (TextView) findViewById(R.id.accountType);
            mAccountUserName = (TextView) findViewById(R.id.accountUserName);
            mRadioButton = (RadioButton) findViewById(R.id.radioButton);
            mRadioButton.setChecked(isActivated());
        }

        if (mFilter == null) {
            mAccountType.setText(R.string.contactsList);
            return;
        }

        mAccountUserName.setVisibility(View.GONE);
        switch (mFilter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                bindView(0, R.string.list_filter_all_accounts);
                break;
            }
            case ContactListFilter.FILTER_TYPE_STARRED: {
                bindView(R.drawable.quantum_ic_star_vd_theme_24, R.string.list_filter_all_starred);
                break;
            }
            case ContactListFilter.FILTER_TYPE_CUSTOM: {
                bindView(0, R.string.list_filter_customize);
                break;
            }
            case ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY: {
                bindView(0, R.string.list_filter_phones);
                break;
            }
            case ContactListFilter.FILTER_TYPE_SINGLE_CONTACT: {
                bindView(0, R.string.list_filter_single);
                break;
            }
            case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                mAccountUserName.setVisibility(View.VISIBLE);
                mIcon.setVisibility(View.VISIBLE);
                if (mFilter.icon != null) {
                    mIcon.setImageDrawable(mFilter.icon);
                } else {
                    mIcon.setImageResource(R.drawable.unknown_source);
                }
                final AccountType accountType =
                        accountTypes.getAccountType(mFilter.accountType, mFilter.dataSet);
                mAccountUserName.setText(mFilter.accountName);
                mAccountType.setText(accountType.getDisplayLabel(getContext()));
                break;
            }
        }
        setContentDescription(generateContentDescription());
    }

    private void bindView(int iconResource, int textResource) {
        if (iconResource != 0) {
            mIcon.setVisibility(View.VISIBLE);
            mIcon.setImageResource(iconResource);
        } else {
            mIcon.setVisibility(View.GONE);
        }

        mAccountType.setText(textResource);
    }

    String generateContentDescription() {
        final StringBuilder sb = new StringBuilder();
        if (!TextUtils.isEmpty(mAccountType.getText())) {
            sb.append(mAccountType.getText());
        }
        if (!TextUtils.isEmpty(mAccountUserName.getText())) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(mAccountUserName.getText());
        }
        return getContext().getString(isActivated() ? R.string.account_filter_view_checked :
                R.string.account_filter_view_not_checked, sb.toString());
    }
}
