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

package com.android.contacts.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * List-Adapter for Account selection
 */
public final class AccountsListAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private List<AccountInfo> mAccounts;
    private int mCustomLayout = -1;

    public AccountsListAdapter(Context context) {
        this(context, Collections.<AccountInfo>emptyList(), null);
    }

    public AccountsListAdapter(Context context, List<AccountInfo> accounts) {
        this(context, accounts, null);
    }

    /**
     * @param currentAccount the Account currently selected by the user, which should come
     * first in the list. Can be null.
     */
    public AccountsListAdapter(Context context, List<AccountInfo> accounts,
            AccountWithDataSet currentAccount) {
        mInflater = LayoutInflater.from(context);

        mAccounts = new ArrayList<>(accounts.size());
        setAccounts(accounts, currentAccount);
    }

    public void setAccounts(List<AccountInfo> accounts, AccountWithDataSet currentAccount) {
        // If it's not empty use the previous "current" account (the first one in the list)
        final AccountInfo currentInfo = mAccounts.isEmpty()
                ? AccountInfo.getAccount(accounts, currentAccount)
                : AccountInfo.getAccount(accounts, mAccounts.get(0).getAccount());

        mAccounts.clear();
        mAccounts.addAll(accounts);

        if (currentInfo != null
                && !mAccounts.isEmpty()
                && !mAccounts.get(0).sameAccount(currentAccount)
                && mAccounts.remove(currentInfo)) {
            mAccounts.add(0, currentInfo);
        }
        notifyDataSetChanged();
    }

    public void setCustomLayout(int customLayout) {
        mCustomLayout = customLayout;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View resultView = convertView != null ? convertView :
                mInflater.inflate(mCustomLayout > 0 ? mCustomLayout :
                        R.layout.account_selector_list_item_condensed, parent, false);

        final TextView text1 = (TextView) resultView.findViewById(android.R.id.text1);
        final TextView text2 = (TextView) resultView.findViewById(android.R.id.text2);
        final ImageView icon = (ImageView) resultView.findViewById(android.R.id.icon);

        text1.setText(mAccounts.get(position).getTypeLabel());
        text2.setText(mAccounts.get(position).getNameLabel());

        icon.setImageDrawable(mAccounts.get(position).getIcon());

        return resultView;
    }

    @Override
    public int getCount() {
        return mAccounts.size();
    }

    @Override
    public AccountWithDataSet getItem(int position) {
        return mAccounts.get(position).getAccount();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }
}

