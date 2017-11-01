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

import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.ShortcutIntentBuilder;
import com.android.contacts.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;

/**
 * Fragment containing a phone number list for picking.
 */
public class PhoneNumberPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements OnShortcutIntentCreatedListener, PhoneNumberListAdapter.Listener {
    private static final String TAG = "PhoneNumberPicker";

    private static final String KEY_SHORTCUT_ACTION = "shortcutAction";

    private OnPhoneNumberPickerActionListener mListener;
    private String mShortcutAction;

    private ContactListFilter mFilter;

    private static final String KEY_FILTER = "filter";

    /** true if the loader has started at least once. */
    private boolean mLoaderStarted;

    private boolean mUseCallableUri;

    private ContactListItemView.PhotoPosition mPhotoPosition =
            ContactListItemView.getDefaultPhotoPosition(false /* normal/non opposite */);

    /**
     * Handles a click on the video call icon for a row in the list.
     *
     * @param position The position in the list where the click ocurred.
     */
    @Override
    public void onVideoCallIconClicked(int position) {
        callNumber(position, true /* isVideoCall */);
    }

    public PhoneNumberPickerFragment() {
        setQuickContactEnabled(false);
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);

        // Show nothing instead of letting caller Activity show something.
        setHasOptionsMenu(true);
    }

    public void setDirectorySearchEnabled(boolean flag) {
        setDirectorySearchMode(flag ? DirectoryListLoader.SEARCH_MODE_DEFAULT
                : DirectoryListLoader.SEARCH_MODE_NONE);
    }

    public void setOnPhoneNumberPickerActionListener(OnPhoneNumberPickerActionListener listener) {
        this.mListener = listener;
    }

    public OnPhoneNumberPickerActionListener getOnPhoneNumberPickerListener() {
        return mListener;
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        setVisibleScrollbarEnabled(getVisibleScrollbarEnabled());
    }

    protected boolean getVisibleScrollbarEnabled() {
        return true;
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mFilter = savedState.getParcelable(KEY_FILTER);
        mShortcutAction = savedState.getString(KEY_SHORTCUT_ACTION);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_FILTER, mFilter);
        outState.putString(KEY_SHORTCUT_ACTION, mShortcutAction);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            if (mListener != null) {
                mListener.onHomeInActionBarSelected();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * @param shortcutAction either {@link Intent#ACTION_CALL} or
     *            {@link Intent#ACTION_SENDTO} or null.
     */
    public void setShortcutAction(String shortcutAction) {
        this.mShortcutAction = shortcutAction;
    }

    @Override
    protected void onItemClick(int position, long id) {
        callNumber(position, false /* isVideoCall */);
    }

    /**
     * Initiates a call to the number at the specified position.
     *
     * @param position The position.
     * @param isVideoCall {@code true} if the call should be initiated as a video call,
     *      {@code false} otherwise.
     */
    private void callNumber(int position, boolean isVideoCall) {
        final Uri phoneUri = getPhoneUri(position);

        if (phoneUri != null) {
            pickPhoneNumber(phoneUri, isVideoCall);
        } else {
            final String number = getPhoneNumber(position);
            if (!TextUtils.isEmpty(number)) {
                cacheContactInfo(position);
                mListener.onPickPhoneNumber(number, isVideoCall,
                        getCallInitiationType(true /* isRemoteDirectory */));
            } else {
                Log.w(TAG, "Item at " + position + " was clicked before"
                        + " adapter is ready. Ignoring");
            }
        }
    }

    protected void cacheContactInfo(int position) {
        // Not implemented. Hook for child classes
    }

    protected String getPhoneNumber(int position) {
        final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        return adapter.getPhoneNumber(position);
    }

    protected Uri getPhoneUri(int position) {
        final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        return adapter.getDataUri(position);
    }

    protected String getLookupKey(int position) {
        final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        return adapter.getLookupKey(position);
    }

    @Override
    protected void startLoading() {
        mLoaderStarted = true;
        super.startLoading();
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);

        // disable scroll bar if there is no data
        setVisibleScrollbarEnabled(data != null && !data.isClosed() && data.getCount() > 0);
    }

    public void setUseCallableUri(boolean useCallableUri) {
        mUseCallableUri = useCallableUri;
    }

    public boolean usesCallableUri() {
        return mUseCallableUri;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        PhoneNumberListAdapter adapter = new PhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        adapter.setUseCallableUri(mUseCallableUri);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        final ContactEntryListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        if (!isSearchMode() && mFilter != null) {
            adapter.setFilter(mFilter);
        }

        setPhotoPosition(adapter);
    }

    protected void setPhotoPosition(ContactEntryListAdapter adapter) {
        ((PhoneNumberListAdapter) adapter).setPhotoPosition(mPhotoPosition);
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_list_content, null);
    }

    public void pickPhoneNumber(Uri uri, boolean isVideoCall) {
        if (mShortcutAction == null) {
            mListener.onPickDataUri(uri, isVideoCall,
                    getCallInitiationType(false /* isRemoteDirectory */));
        } else {
            startPhoneNumberShortcutIntent(uri, isVideoCall);
        }
    }

    protected void startPhoneNumberShortcutIntent(Uri uri, boolean isVideoCall) {
        ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getActivity(), this);
        builder.createPhoneNumberShortcutIntent(uri, mShortcutAction);
    }

    @Override
    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
        mListener.onShortcutIntentCreated(shortcutIntent);
    }

    @Override
    public void onPickerResult(Intent data) {
        mListener.onPickDataUri(data.getData(), false /* isVideoCall */,
                getCallInitiationType(false /* isRemoteDirectory */));
    }

    public void setFilter(ContactListFilter filter) {
        if ((mFilter == null && filter == null) ||
                (mFilter != null && mFilter.equals(filter))) {
            return;
        }

        mFilter = filter;
        if (mLoaderStarted) {
            reloadData();
        }
    }

    public void setPhotoPosition(ContactListItemView.PhotoPosition photoPosition) {
        mPhotoPosition = photoPosition;

        final PhoneNumberListAdapter adapter = (PhoneNumberListAdapter) getAdapter();
        if (adapter != null) {
            adapter.setPhotoPosition(photoPosition);
        }
    }

    /**
     * @param isRemoteDirectory {@code true} if the call was initiated using a contact/phone number
     *         not in the local contacts database
     */
    protected int getCallInitiationType(boolean isRemoteDirectory) {
        return OnPhoneNumberPickerActionListener.CALL_INITIATION_UNKNOWN;
    }
}
