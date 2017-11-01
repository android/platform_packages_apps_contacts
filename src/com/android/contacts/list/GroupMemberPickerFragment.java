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
package com.android.contacts.list;

import android.app.Activity;
import android.content.Loader;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.activities.ContactSelectionActivity;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.list.ContactListAdapter.ContactQuery;
import com.android.contacts.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment containing raw contacts for a specified account that are not already in a group.
 */
public class GroupMemberPickerFragment extends
        MultiSelectContactsListFragment<DefaultContactListAdapter> {

    public static final String TAG = "GroupMemberPicker";

    private static final String KEY_ACCOUNT_NAME = "accountName";
    private static final String KEY_ACCOUNT_TYPE = "accountType";
    private static final String KEY_ACCOUNT_DATA_SET = "accountDataSet";
    private static final String KEY_CONTACT_IDS = "contactIds";

    private static final String ARG_ACCOUNT_NAME = "accountName";
    private static final String ARG_ACCOUNT_TYPE = "accountType";
    private static final String ARG_ACCOUNT_DATA_SET = "accountDataSet";
    private static final String ARG_CONTACT_IDS = "contactIds";

    /** Callbacks for host of {@link GroupMemberPickerFragment}. */
    public interface Listener {

        /** Invoked when a potential group member is selected. */
        void onGroupMemberClicked(long contactId);

        /** Invoked when user has initiated multiple selection mode. */
        void onSelectGroupMembers();
    }

    /** Filters out raw contacts that are already in the group. */
    private class FilterCursorWrapper extends CursorWrapper {

        private int[] mIndex;
        private int mCount = 0;
        private int mPos = 0;

        public FilterCursorWrapper(Cursor cursor) {
            super(cursor);

            mCount = super.getCount();
            mIndex = new int[mCount];

            final List<Integer> indicesToFilter = new ArrayList<>();

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "RawContacts CursorWrapper start: " + mCount);
            }

            final Bundle bundle = cursor.getExtras();
            final String sections[] = bundle.getStringArray(Contacts
                    .EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            final int counts[] = bundle.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
            final ContactsSectionIndexer indexer = (sections == null || counts == null)
                    ? null : new ContactsSectionIndexer(sections, counts);

            for (int i = 0; i < mCount; i++) {
                super.moveToPosition(i);
                final String contactId = getString(ContactQuery.CONTACT_ID);
                if (!mContactIds.contains(contactId)) {
                    mIndex[mPos++] = i;
                } else {
                    indicesToFilter.add(i);
                }
            }

            if (indexer != null && GroupUtil.needTrimming(mCount, counts, indexer.getPositions())) {
                GroupUtil.updateBundle(bundle, indexer, indicesToFilter, sections, counts);
            }

            mCount = mPos;
            mPos = 0;
            super.moveToFirst();

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "RawContacts CursorWrapper end: " + mCount);
            }
        }

        @Override
        public boolean move(int offset) {
            return moveToPosition(mPos + offset);
        }

        @Override
        public boolean moveToNext() {
            return moveToPosition(mPos + 1);
        }

        @Override
        public boolean moveToPrevious() {
            return moveToPosition(mPos - 1);
        }

        @Override
        public boolean moveToFirst() {
            return moveToPosition(0);
        }

        @Override
        public boolean moveToLast() {
            return moveToPosition(mCount - 1);
        }

        @Override
        public boolean moveToPosition(int position) {
            if (position >= mCount) {
                mPos = mCount;
                return false;
            } else if (position < 0) {
                mPos = -1;
                return false;
            }
            mPos = mIndex[position];
            return super.moveToPosition(mPos);
        }

        @Override
        public int getCount() {
            return mCount;
        }

        @Override
        public int getPosition() {
            return mPos;
        }
    }

    private String mAccountName;
    private String mAccountType;
    private String mAccountDataSet;
    private ArrayList<String> mContactIds;
    private Listener mListener;

    public static GroupMemberPickerFragment newInstance(String accountName, String accountType,
            String accountDataSet, ArrayList<String> contactIds) {
        final Bundle args = new Bundle();
        args.putString(ARG_ACCOUNT_NAME, accountName);
        args.putString(ARG_ACCOUNT_TYPE, accountType);
        args.putString(ARG_ACCOUNT_DATA_SET, accountDataSet);
        args.putStringArrayList(ARG_CONTACT_IDS, contactIds);

        final GroupMemberPickerFragment fragment = new GroupMemberPickerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public GroupMemberPickerFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setHasOptionsMenu(true);
        setDisplayDirectoryHeader(false);
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState == null) {
            mAccountName = getArguments().getString(ARG_ACCOUNT_NAME);
            mAccountType = getArguments().getString(ARG_ACCOUNT_TYPE);
            mAccountDataSet = getArguments().getString(ARG_ACCOUNT_DATA_SET);
            mContactIds = getArguments().getStringArrayList(ARG_CONTACT_IDS);
        } else {
            mAccountName = savedState.getString(KEY_ACCOUNT_NAME);
            mAccountType = savedState.getString(KEY_ACCOUNT_TYPE);
            mAccountDataSet = savedState.getString(KEY_ACCOUNT_DATA_SET);
            mContactIds = savedState.getStringArrayList(KEY_CONTACT_IDS);
        }
        super.onCreate(savedState);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_ACCOUNT_NAME, mAccountName);
        outState.putString(KEY_ACCOUNT_TYPE, mAccountType);
        outState.putString(KEY_ACCOUNT_DATA_SET, mAccountDataSet);
        outState.putStringArrayList(KEY_CONTACT_IDS, mContactIds);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_list_content, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null) {
            // Wait until contacts are loaded before showing the scrollbar
            setVisibleScrollbarEnabled(true);

            final FilterCursorWrapper cursorWrapper = new FilterCursorWrapper(data);
            final View accountFilterContainer = getView().findViewById(
                    R.id.account_filter_header_container);
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(mAccountName,
                    mAccountType, mAccountDataSet);
            bindListHeader(getContext(), getListView(), accountFilterContainer,
                    accountWithDataSet, cursorWrapper.getCount());

            super.onLoadFinished(loader, cursorWrapper);
        }
    }

    @Override
    protected DefaultContactListAdapter createListAdapter() {
        final DefaultContactListAdapter adapter = new DefaultContactListAdapter(getActivity());
        adapter.setFilter(ContactListFilter.createGroupMembersFilter(
                mAccountType, mAccountName, mAccountDataSet));
        adapter.setSectionHeaderDisplayEnabled(true);
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    protected void onItemClick(int position, long id) {
        if (getAdapter().isDisplayingCheckBoxes()) {
            super.onItemClick(position, id);
            return;
        }
        if (mListener != null) {
            final long contactId = getAdapter().getContactId(position);
            if (contactId > 0) {
                mListener.onGroupMemberClicked(contactId);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.group_member_picker, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final ContactSelectionActivity activity = getContactSelectionActivity();
        final boolean hasContacts = mContactIds == null ? false : mContactIds.size() > 0;
        final boolean isSearchMode = activity == null ? false : activity.isSearchMode();
        final boolean isSelectionMode = activity == null ? false : activity.isSelectionMode();

        // Added in ContactSelectionActivity but we must account for selection mode
        setVisible(menu, R.id.menu_search, !isSearchMode && !isSelectionMode);
        setVisible(menu, R.id.menu_select, hasContacts && !isSearchMode && !isSelectionMode);
    }

    private ContactSelectionActivity getContactSelectionActivity() {
        final Activity activity = getActivity();
        if (activity != null && activity instanceof ContactSelectionActivity) {
            return (ContactSelectionActivity) activity;
        }
        return null;
    }

    private static void setVisible(Menu menu, int id, boolean visible) {
        final MenuItem menuItem = menu.findItem(id);
        if (menuItem != null) {
            menuItem.setVisible(visible);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.onBackPressed();
            }
            return true;
        } else if (id == R.id.menu_select) {
            if (mListener != null) {
                mListener.onSelectGroupMembers();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
