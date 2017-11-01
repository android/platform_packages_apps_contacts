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
package com.android.contacts.list;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.list.JoinContactLoader.JoinContactLoaderResult;
import com.android.contacts.logging.ListEvent;

/**
 * Fragment for the Join Contact list.
 */
public class JoinContactListFragment extends ContactEntryListFragment<JoinContactListAdapter> {

    private static final int DISPLAY_NAME_LOADER = -2;

    private static final String KEY_TARGET_CONTACT_ID = "targetContactId";

    private OnContactPickerActionListener mListener;
    private long mTargetContactId;

    private final LoaderCallbacks<Cursor> mLoaderCallbacks = new LoaderCallbacks<Cursor>() {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            switch (id) {
                case DISPLAY_NAME_LOADER: {
                    // Loader for the display name of the target contact
                    return new CursorLoader(getActivity(),
                            ContentUris.withAppendedId(Contacts.CONTENT_URI, mTargetContactId),
                            new String[] { Contacts.DISPLAY_NAME }, null, null, null);
                }
                case JoinContactListAdapter.PARTITION_ALL_CONTACTS: {
                    JoinContactLoader loader = new JoinContactLoader(getActivity());
                    JoinContactListAdapter adapter = getAdapter();
                    if (adapter != null) {
                        adapter.configureLoader(loader, 0);
                    }
                    return loader;
                }
            }
            throw new IllegalArgumentException("No loader for ID=" + id);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            switch (loader.getId()) {
                case DISPLAY_NAME_LOADER: {
                    if (data != null && data.moveToFirst()) {
                        showTargetContactName(data.getString(0));
                    }
                    break;
                }
                case JoinContactListAdapter.PARTITION_ALL_CONTACTS: {
                    if (data != null) {
                        final Cursor suggestionsCursor =
                                ((JoinContactLoaderResult) data).suggestionCursor;
                        onContactListLoaded(suggestionsCursor, data);
                        maybeLogListEvent();
                    }
                    break;
                }
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    public JoinContactListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(false);
        setQuickContactEnabled(false);
        setListType(ListEvent.ListType.PICK_JOIN);
        setLogListEvents(true);
    }

    public void setOnContactPickerActionListener(OnContactPickerActionListener listener) {
        mListener = listener;
    }

    @Override
    protected void startLoading() {
        configureAdapter();

        getLoaderManager().initLoader(DISPLAY_NAME_LOADER, null, mLoaderCallbacks);

        // When this method is called, Uri to be used may be changed. We should use restartLoader()
        // to load the parameter again.
        getLoaderManager().restartLoader(JoinContactListAdapter.PARTITION_ALL_CONTACTS,
                null, mLoaderCallbacks);
    }

    private void onContactListLoaded(Cursor suggestionsCursor, Cursor allContactsCursor) {
        JoinContactListAdapter adapter = getAdapter();
        adapter.setSuggestionsCursor(suggestionsCursor);
        setVisibleScrollbarEnabled(true);
        onPartitionLoaded(JoinContactListAdapter.PARTITION_ALL_CONTACTS, allContactsCursor);
    }

    private void showTargetContactName(String displayName) {
        Activity activity = getActivity();
        TextView blurbView = (TextView) activity.findViewById(R.id.join_contact_blurb);
        final String name = !TextUtils.isEmpty(displayName) ? displayName
            : activity.getString(R.string.missing_name);
        String blurb = activity.getString(R.string.blurbJoinContactDataWith, name);
        blurbView.setText(blurb);
    }

    public void setTargetContactId(long targetContactId) {
        mTargetContactId = targetContactId;
    }

    @Override
    public JoinContactListAdapter createListAdapter() {
        JoinContactListAdapter adapter = new JoinContactListAdapter(getActivity());
        adapter.setPhotoPosition(ContactListItemView.getDefaultPhotoPosition(false /* opposite */));
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        JoinContactListAdapter adapter = getAdapter();
        adapter.setTargetContactId(mTargetContactId);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.join_contact_picker_list_content, null);
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri contactUri = getAdapter().getContactUri(position);
        if (contactUri != null) mListener.onPickContactAction(contactUri);
    }

    @Override
    public void onPickerResult(Intent data) {
        final Uri contactUri = data.getData();
        if (contactUri != null) mListener.onPickContactAction(contactUri);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_TARGET_CONTACT_ID, mTargetContactId);
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);
        if (savedState != null) {
            mTargetContactId = savedState.getLong(KEY_TARGET_CONTACT_ID);
        }
    }

    @Override
    public void setQueryString(String queryString, boolean delaySelection) {
        super.setQueryString(queryString, delaySelection);

        setSearchMode(!TextUtils.isEmpty(queryString));
    }
}
