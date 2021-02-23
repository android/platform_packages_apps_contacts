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
package com.android.contacts;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Loader;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;

import androidx.collection.ArrayMap;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.appcompat.widget.Toolbar;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.compat.CompatUtils;
import com.android.contacts.database.SimContactDao;
import com.android.contacts.editor.AccountHeaderPresenter;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.concurrent.ContactsExecutors;
import com.android.contacts.util.concurrent.ListenableFutureLoader;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Dialog that presents a list of contacts from a SIM card that can be imported into a selected
 * account
 */
public class SimImportFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<SimImportFragment.LoaderResult>,
        AdapterView.OnItemClickListener, AbsListView.OnScrollListener {

    private static final String KEY_SUFFIX_SELECTED_IDS = "_selectedIds";
    private static final String ARG_SUBSCRIPTION_ID = "subscriptionId";

    private ContactsPreferences mPreferences;
    private AccountTypeManager mAccountTypeManager;
    private SimContactAdapter mAdapter;
    private View mAccountHeaderContainer;
    private AccountHeaderPresenter mAccountHeaderPresenter;
    private float mAccountScrolledElevationPixels;
    private ContentLoadingProgressBar mLoadingIndicator;
    private Toolbar mToolbar;
    private ListView mListView;
    private View mImportButton;

    private Bundle mSavedInstanceState;

    private final Map<AccountWithDataSet, long[]> mPerAccountCheckedIds = new ArrayMap<>();

    private int mSubscriptionId;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSavedInstanceState = savedInstanceState;
        mPreferences = new ContactsPreferences(getContext());
        mAccountTypeManager = AccountTypeManager.getInstance(getActivity());
        mAdapter = new SimContactAdapter(getActivity());

        final Bundle args = getArguments();
        mSubscriptionId = args == null ? SimCard.NO_SUBSCRIPTION_ID :
                args.getInt(ARG_SUBSCRIPTION_ID, SimCard.NO_SUBSCRIPTION_ID);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_sim_import, container, false);

        mAccountHeaderContainer = view.findViewById(R.id.account_header_container);
        mAccountScrolledElevationPixels = getResources()
                .getDimension(R.dimen.contact_list_header_elevation);
        mAccountHeaderPresenter = new AccountHeaderPresenter(
                mAccountHeaderContainer);
        if (savedInstanceState != null) {
            mAccountHeaderPresenter.onRestoreInstanceState(savedInstanceState);
        } else {
            // Default may be null in which case the first account in the list will be selected
            // after they are loaded.
            mAccountHeaderPresenter.setCurrentAccount(mPreferences.getDefaultAccount());
        }
        mAccountHeaderPresenter.setObserver(new AccountHeaderPresenter.Observer() {
            @Override
            public void onChange(AccountHeaderPresenter sender) {
                rememberSelectionsForCurrentAccount();
                mAdapter.setAccount(sender.getCurrentAccount());
                showSelectionsForCurrentAccount();
                updateToolbarWithCurrentSelections();
            }
        });
        mAdapter.setAccount(mAccountHeaderPresenter.getCurrentAccount());

        mListView = (ListView) view.findViewById(R.id.list);
        mListView.setOnScrollListener(this);
        mListView.setAdapter(mAdapter);
        mListView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
        mListView.setOnItemClickListener(this);
        mImportButton = view.findViewById(R.id.import_button);
        mImportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                importCurrentSelections();
                // Do we wait for import to finish?
                getActivity().setResult(Activity.RESULT_OK);
                getActivity().finish();
            }
        });

        mToolbar = (Toolbar) view.findViewById(R.id.toolbar);
        mToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().setResult(Activity.RESULT_CANCELED);
                getActivity().finish();
            }
        });

        mLoadingIndicator = (ContentLoadingProgressBar) view.findViewById(R.id.loading_progress);

        return view;
    }

    private void rememberSelectionsForCurrentAccount() {
        final AccountWithDataSet current = mAdapter.getAccount();
        if (current == null) {
            return;
        }
        final long[] ids = mListView.getCheckedItemIds();
        Arrays.sort(ids);
        mPerAccountCheckedIds.put(current, ids);
    }

    private void showSelectionsForCurrentAccount() {
        final long[] ids = mPerAccountCheckedIds.get(mAdapter.getAccount());
        if (ids == null) {
            selectAll();
            return;
        }
        for (int i = 0, len = mListView.getCount(); i < len; i++) {
            mListView.setItemChecked(i,
                    Arrays.binarySearch(ids, mListView.getItemIdAtPosition(i)) >= 0);
        }
    }

    private void selectAll() {
        for (int i = 0, len = mListView.getCount(); i < len; i++) {
            mListView.setItemChecked(i, true);
        }
    }

    private void updateToolbarWithCurrentSelections() {
        // The ListView keeps checked state for items that are disabled but we only want  to
        // consider items that don't exist in the current account when updating the toolbar
        int importableCount = 0;
        final SparseBooleanArray checked = mListView.getCheckedItemPositions();
        for (int i = 0; i < checked.size(); i++) {
            if (checked.valueAt(i) && !mAdapter.existsInCurrentAccount(checked.keyAt(i))) {
                importableCount++;
            }
        }

        if (importableCount == 0) {
            mImportButton.setVisibility(View.GONE);
            mToolbar.setTitle(R.string.sim_import_title_none_selected);
        } else {
            mToolbar.setTitle(String.valueOf(importableCount));
            mImportButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mAdapter.isEmpty() && getLoaderManager().getLoader(0).isStarted()) {
            mLoadingIndicator.show();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        rememberSelectionsForCurrentAccount();
        // We'll restore this manually so we don't need the list to preserve it's own state.
        mListView.clearChoices();
        super.onSaveInstanceState(outState);
        mAccountHeaderPresenter.onSaveInstanceState(outState);
        saveAdapterSelectedStates(outState);
    }

    @Override
    public Loader<LoaderResult> onCreateLoader(int id, Bundle args) {
        return new SimContactLoader(getContext(), mSubscriptionId);
    }

    @Override
    public void onLoadFinished(Loader<LoaderResult> loader,
            LoaderResult data) {
        mLoadingIndicator.hide();
        if (data == null) {
            return;
        }
        mAccountHeaderPresenter.setAccounts(data.accounts);
        restoreAdapterSelectedStates(data.accounts);
        mAdapter.setData(data);
        mListView.setEmptyView(getView().findViewById(R.id.empty_message));

        showSelectionsForCurrentAccount();
        updateToolbarWithCurrentSelections();
    }

    @Override
    public void onLoaderReset(Loader<LoaderResult> loader) {
    }

    private void restoreAdapterSelectedStates(List<AccountInfo> accounts) {
        if (mSavedInstanceState == null) {
            return;
        }

        for (AccountInfo account : accounts) {
            final long[] selections = mSavedInstanceState.getLongArray(
                    account.getAccount().stringify() + KEY_SUFFIX_SELECTED_IDS);
            mPerAccountCheckedIds.put(account.getAccount(), selections);
        }
        mSavedInstanceState = null;
    }

    private void saveAdapterSelectedStates(Bundle outState) {
        if (mAdapter == null) {
            return;
        }

        // Make sure the selections are up-to-date
        for (Map.Entry<AccountWithDataSet, long[]> entry : mPerAccountCheckedIds.entrySet()) {
            outState.putLongArray(entry.getKey().stringify() + KEY_SUFFIX_SELECTED_IDS,
                    entry.getValue());
        }
    }

    private void importCurrentSelections() {
        final SparseBooleanArray checked = mListView.getCheckedItemPositions();
        final ArrayList<SimContact> importableContacts = new ArrayList<>(checked.size());
        for (int i = 0; i < checked.size(); i++) {
            // It's possible for existing contacts to be "checked" but we only want to import the
            // ones that don't already exist
            if (checked.valueAt(i) && !mAdapter.existsInCurrentAccount(i)) {
                importableContacts.add(mAdapter.getItem(checked.keyAt(i)));
            }
        }
        SimImportService.startImport(getContext(), mSubscriptionId, importableContacts,
                mAccountHeaderPresenter.getCurrentAccount());
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mAdapter.existsInCurrentAccount(position)) {
            Snackbar.make(getView(), R.string.sim_import_contact_exists_toast,
                    Snackbar.LENGTH_LONG).show();
        } else {
            updateToolbarWithCurrentSelections();
        }
    }

    public Context getContext() {
        if (CompatUtils.isMarshmallowCompatible()) {
            return super.getContext();
        }
        return getActivity();
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) { }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        int firstCompletelyVisibleItem = firstVisibleItem;
        if (view != null && view.getChildAt(0) != null && view.getChildAt(0).getTop() < 0) {
            firstCompletelyVisibleItem++;
        }

        if (firstCompletelyVisibleItem == 0) {
            ViewCompat.setElevation(mAccountHeaderContainer, 0);
        } else {
            ViewCompat.setElevation(mAccountHeaderContainer, mAccountScrolledElevationPixels);
        }
    }

    /**
     * Creates a fragment that will display contacts stored on the default SIM card
     */
    public static SimImportFragment newInstance() {
        return new SimImportFragment();
    }

    /**
     * Creates a fragment that will display the contacts stored on the SIM card that has the
     * provided subscriptionId
     */
    public static SimImportFragment newInstance(int subscriptionId) {
        final SimImportFragment fragment = new SimImportFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_SUBSCRIPTION_ID, subscriptionId);
        fragment.setArguments(args);
        return fragment;
    }

    private static class SimContactAdapter extends ArrayAdapter<SimContact> {
        private Map<AccountWithDataSet, Set<SimContact>> mExistingMap;
        private AccountWithDataSet mSelectedAccount;
        private LayoutInflater mInflater;

        public SimContactAdapter(Context context) {
            super(context, 0);
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public long getItemId(int position) {
            // This can be called by the framework when the adapter hasn't been initialized for
            // checking the checked state of items. See b/33108913
            if (position < 0 || position >= getCount()) {
                return View.NO_ID;
            }
            return getItem(position).getRecordNumber();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return !existsInCurrentAccount(position) ? 0 : 1;
        }

        @NonNull
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView text = (TextView) convertView;
            if (text == null) {
                final int layoutRes = existsInCurrentAccount(position) ?
                        R.layout.sim_import_list_item_disabled :
                        R.layout.sim_import_list_item;
                text = (TextView) mInflater.inflate(layoutRes, parent, false);
            }
            text.setText(getItemLabel(getItem(position)));

            return text;
        }

        public void setData(LoaderResult result) {
            clear();
            addAll(result.contacts);
            mExistingMap = result.accountsMap;
        }

        public void setAccount(AccountWithDataSet account) {
            mSelectedAccount = account;
            notifyDataSetChanged();
        }

        public AccountWithDataSet getAccount() {
            return mSelectedAccount;
        }

        public boolean existsInCurrentAccount(int position) {
            return existsInCurrentAccount(getItem(position));
        }

        public boolean existsInCurrentAccount(SimContact contact) {
            if (mSelectedAccount == null || !mExistingMap.containsKey(mSelectedAccount)) {
                return false;
            }
            return mExistingMap.get(mSelectedAccount).contains(contact);
        }

        private String getItemLabel(SimContact contact) {
            if (contact.hasName()) {
                return contact.getName();
            } else if (contact.hasPhone()) {
                return contact.getPhone();
            } else if (contact.hasEmails()) {
                return contact.getEmails()[0];
            } else {
                // This isn't really possible because we skip empty SIM contacts during loading
                return "";
            }
        }
    }


    private static class SimContactLoader extends ListenableFutureLoader<LoaderResult> {
        private SimContactDao mDao;
        private AccountTypeManager mAccountTypeManager;
        private final int mSubscriptionId;

        public SimContactLoader(Context context, int subscriptionId) {
            super(context, new IntentFilter(AccountTypeManager.BROADCAST_ACCOUNTS_CHANGED));
            mDao = SimContactDao.create(context);
            mAccountTypeManager = AccountTypeManager.getInstance(getContext());
            mSubscriptionId = subscriptionId;
        }

        @Override
        protected ListenableFuture<LoaderResult> loadData() {
            final ListenableFuture<List<Object>> future = Futures.<Object>allAsList(
                    mAccountTypeManager
                            .filterAccountsAsync(AccountTypeManager.writableFilter()),
                    ContactsExecutors.getSimReadExecutor().<Object>submit(
                            new Callable<Object>() {
                        @Override
                        public LoaderResult call() throws Exception {
                            return loadFromSim();
                        }
                    }));
            return Futures.transform(future, new Function<List<Object>, LoaderResult>() {
                @Override
                public LoaderResult apply(List<Object> input) {
                    final List<AccountInfo> accounts = (List<AccountInfo>) input.get(0);
                    final LoaderResult simLoadResult = (LoaderResult) input.get(1);
                    simLoadResult.accounts = accounts;
                    return simLoadResult;
                }
            }, MoreExecutors.directExecutor());
        }

        private LoaderResult loadFromSim() {
            final SimCard sim = mDao.getSimBySubscriptionId(mSubscriptionId);
            LoaderResult result = new LoaderResult();
            if (sim == null) {
                result.contacts = new ArrayList<>();
                result.accountsMap = Collections.emptyMap();
                return result;
            }
            result.contacts = mDao.loadContactsForSim(sim);
            result.accountsMap = mDao.findAccountsOfExistingSimContacts(result.contacts);
            return result;
        }
    }

    public static class LoaderResult {
        public List<AccountInfo> accounts;
        public ArrayList<SimContact> contacts;
        public Map<AccountWithDataSet, Set<SimContact>> accountsMap;
    }
}
