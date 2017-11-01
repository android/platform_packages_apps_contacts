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
package com.android.contacts.model.account;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Loader;
import android.os.Bundle;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.util.concurrent.ListenableFutureLoader;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;

/**
 * Loads the accounts from AccountTypeManager
 */
public class AccountsLoader extends ListenableFutureLoader<List<AccountInfo>> {
    private final AccountTypeManager mAccountTypeManager;
    private final Predicate<AccountInfo> mFilter;

    public AccountsLoader(Context context) {
        this(context, Predicates.<AccountInfo>alwaysTrue());
    }

    public AccountsLoader(Context context, Predicate<AccountInfo> filter) {
        super(context, new IntentFilter(AccountTypeManager.BROADCAST_ACCOUNTS_CHANGED));
        mAccountTypeManager = AccountTypeManager.getInstance(context);
        mFilter = filter;
    }

    @Override
    protected ListenableFuture<List<AccountInfo>> loadData() {
        return mAccountTypeManager.filterAccountsAsync(mFilter);
    }

    @Override
    protected boolean isSameData(List<AccountInfo> previous, List<AccountInfo> next) {
        return Objects.equal(AccountInfo.extractAccounts(previous),
                AccountInfo.extractAccounts(next));
    }


    public interface AccountsListener {
        void onAccountsLoaded(List<AccountInfo> accounts);
    }

    /**
     * Loads the accounts into the target fragment using {@link LoaderManager}
     *
     * <p>This is a convenience method to reduce the
     * boilerplate needed when implementing {@link android.app.LoaderManager.LoaderCallbacks}
     * in the simple case that the fragment wants to just load the accounts directly</p>
     * <p>Note that changing the filter between invocations in the same component will not work
     * properly because the loader is cached.</p>
     */
    public static <FragmentType extends Fragment & AccountsListener> void loadAccounts(
            final FragmentType fragment, int loaderId, final Predicate<AccountInfo> filter) {
        loadAccounts(
                fragment.getActivity(), fragment.getLoaderManager(), loaderId, filter, fragment);
    }

    /**
     * Same as {@link #loadAccounts(Fragment, int, Predicate)} for an Activity
     */
    public static <ActivityType extends Activity & AccountsListener> void loadAccounts(
            final ActivityType activity, int id, final Predicate<AccountInfo> filter) {
        loadAccounts(activity, activity.getLoaderManager(), id, filter, activity);
    }

    private static void loadAccounts(final Context context, LoaderManager loaderManager, int id,
            final Predicate<AccountInfo> filter, final AccountsListener listener) {
        loaderManager.initLoader(id, null,
                new LoaderManager.LoaderCallbacks<List<AccountInfo>>() {
                    @Override
                    public Loader<List<AccountInfo>> onCreateLoader(int id, Bundle args) {
                        return new AccountsLoader(context, filter);
                    }

                    @Override
                    public void onLoadFinished(
                            Loader<List<AccountInfo>> loader, List<AccountInfo> data) {
                        listener.onAccountsLoaded(data);
                    }

                    @Override
                    public void onLoaderReset(Loader<List<AccountInfo>> loader) {
                    }
                });
    }
}
