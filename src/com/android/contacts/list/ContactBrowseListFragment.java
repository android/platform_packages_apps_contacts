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
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;

import com.android.common.widget.CompositeCursorAdapter.Partition;
import com.android.contacts.util.ContactLoaderUtils;

import java.util.List;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public abstract class ContactBrowseListFragment extends
        MultiSelectContactsListFragment<ContactListAdapter> {

    private static final String TAG = "ContactList";

    private static final String KEY_SELECTED_URI = "selectedUri";
    private static final String KEY_SELECTION_VERIFIED = "selectionVerified";
    private static final String KEY_FILTER = "filter";
    private static final String KEY_LAST_SELECTED_POSITION = "lastSelected";

    private static final String PERSISTENT_SELECTION_PREFIX = "defaultContactBrowserSelection";

    /**
     * The id for a delayed message that triggers automatic selection of the first
     * found contact in search mode.
     */
    private static final int MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT = 1;

    /**
     * The delay that is used for automatically selecting the first found contact.
     */
    private static final int DELAY_AUTOSELECT_FIRST_FOUND_CONTACT_MILLIS = 500;

    /**
     * The minimum number of characters in the search query that is required
     * before we automatically select the first found contact.
     */
    private static final int AUTOSELECT_FIRST_FOUND_CONTACT_MIN_QUERY_LENGTH = 2;

    private SharedPreferences mPrefs;
    private Handler mHandler;

    private boolean mStartedLoading;
    private boolean mSelectionRequired;
    private boolean mSelectionToScreenRequested;
    private boolean mSmoothScrollRequested;
    private boolean mSelectionPersistenceRequested;
    private Uri mSelectedContactUri;
    private long mSelectedContactDirectoryId;
    private String mSelectedContactLookupKey;
    private long mSelectedContactId;
    private boolean mSelectionVerified;
    private int mLastSelectedPosition = -1;
    private boolean mRefreshingContactUri;
    private ContactListFilter mFilter;
    private String mPersistentSelectionPrefix = PERSISTENT_SELECTION_PREFIX;

    protected OnContactBrowserActionListener mListener;
    private ContactLookupTask mContactLookupTask;

    private final class ContactLookupTask extends AsyncTask<Void, Void, Uri> {

        private final Uri mUri;
        private boolean mIsCancelled;

        public ContactLookupTask(Uri uri) {
            mUri = uri;
        }

        @Override
        protected Uri doInBackground(Void... args) {
            Cursor cursor = null;
            try {
                final ContentResolver resolver = getContext().getContentResolver();
                final Uri uriCurrentFormat = ContactLoaderUtils.ensureIsContactUri(resolver, mUri);
                cursor = resolver.query(uriCurrentFormat,
                        new String[] { Contacts._ID, Contacts.LOOKUP_KEY }, null, null, null);

                if (cursor != null && cursor.moveToFirst()) {
                    final long contactId = cursor.getLong(0);
                    final String lookupKey = cursor.getString(1);
                    if (contactId != 0 && !TextUtils.isEmpty(lookupKey)) {
                        return Contacts.getLookupUri(contactId, lookupKey);
                    }
                }

                Log.e(TAG, "Error: No contact ID or lookup key for contact " + mUri);
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Error loading the contact: " + mUri, e);
                return null;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        public void cancel() {
            super.cancel(true);
            // Use a flag to keep track of whether the {@link AsyncTask} was cancelled or not in
            // order to ensure onPostExecute() is not executed after the cancel request. The flag is
            // necessary because {@link AsyncTask} still calls onPostExecute() if the cancel request
            // came after the worker thread was finished.
            mIsCancelled = true;
        }

        @Override
        protected void onPostExecute(Uri uri) {
            // Make sure the {@link Fragment} is at least still attached to the {@link Activity}
            // before continuing. Null URIs should still be allowed so that the list can be
            // refreshed and a default contact can be selected (i.e. the case of deleted
            // contacts).
            if (mIsCancelled || !isAdded()) {
                return;
            }
            onContactUriQueryFinished(uri);
        }
    }

    private boolean mDelaySelection;

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    switch (msg.what) {
                        case MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT:
                            selectDefaultContact();
                            break;
                    }
                }
            };
        }
        return mHandler;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
        restoreFilter();
        restoreSelectedUri(false);
    }

    @Override
    protected void setSearchMode(boolean flag) {
        if (isSearchMode() != flag) {
            if (!flag) {
                restoreSelectedUri(true);
            }
            super.setSearchMode(flag);
        }
    }

    public void updateListFilter(ContactListFilter filter, boolean restoreSelectedUri) {
        if (mFilter == null && filter == null) {
            return;
        }

        if (mFilter != null && mFilter.equals(filter)) {
            setLogListEvents(false);
            return;
        }

        if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "New filter: " + filter);

        setListType(filter.toListType());
        setLogListEvents(true);
        mFilter = filter;
        mLastSelectedPosition = -1;

        if (restoreSelectedUri) {
            mSelectedContactUri = null;
            restoreSelectedUri(true);
        }
        reloadData();
    }

    public ContactListFilter getFilter() {
        return mFilter;
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mFilter = savedState.getParcelable(KEY_FILTER);
        mSelectedContactUri = savedState.getParcelable(KEY_SELECTED_URI);
        mSelectionVerified = savedState.getBoolean(KEY_SELECTION_VERIFIED);
        mLastSelectedPosition = savedState.getInt(KEY_LAST_SELECTED_POSITION);
        parseSelectedContactUri();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_FILTER, mFilter);
        outState.putParcelable(KEY_SELECTED_URI, mSelectedContactUri);
        outState.putBoolean(KEY_SELECTION_VERIFIED, mSelectionVerified);
        outState.putInt(KEY_LAST_SELECTED_POSITION, mLastSelectedPosition);
    }

    protected void refreshSelectedContactUri() {
        if (mContactLookupTask != null) {
            mContactLookupTask.cancel();
        }

        if (!isSelectionVisible()) {
            return;
        }

        mRefreshingContactUri = true;

        if (mSelectedContactUri == null) {
            onContactUriQueryFinished(null);
            return;
        }

        if (mSelectedContactDirectoryId != Directory.DEFAULT
                && mSelectedContactDirectoryId != Directory.LOCAL_INVISIBLE) {
            onContactUriQueryFinished(mSelectedContactUri);
        } else {
            mContactLookupTask = new ContactLookupTask(mSelectedContactUri);
            mContactLookupTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
        }
    }

    protected void onContactUriQueryFinished(Uri uri) {
        mRefreshingContactUri = false;
        mSelectedContactUri = uri;
        parseSelectedContactUri();
        checkSelection();
    }

    public Uri getSelectedContactUri() {
        return mSelectedContactUri;
    }

    /**
     * Sets the new selection for the list.
     */
    public void setSelectedContactUri(Uri uri) {
        setSelectedContactUri(uri, true, false /* no smooth scroll */, true, false);
    }

    @Override
    public void setQueryString(String queryString, boolean delaySelection) {
        mDelaySelection = delaySelection;
        super.setQueryString(queryString, delaySelection);
    }

    /**
     * Sets whether or not a contact selection must be made.
     * @param required if true, we need to check if the selection is present in
     *            the list and if not notify the listener so that it can load a
     *            different list.
     * TODO: Figure out how to reconcile this with {@link #setSelectedContactUri},
     * without causing unnecessary loading of the list if the selected contact URI is
     * the same as before.
     */
    public void setSelectionRequired(boolean required) {
        mSelectionRequired = required;
    }

    /**
     * Sets the new contact selection.
     *
     * @param uri the new selection
     * @param required if true, we need to check if the selection is present in
     *            the list and if not notify the listener so that it can load a
     *            different list
     * @param smoothScroll if true, the UI will roll smoothly to the new
     *            selection
     * @param persistent if true, the selection will be stored in shared
     *            preferences.
     * @param willReloadData if true, the selection will be remembered but not
     *            actually shown, because we are expecting that the data will be
     *            reloaded momentarily
     */
    private void setSelectedContactUri(Uri uri, boolean required, boolean smoothScroll,
            boolean persistent, boolean willReloadData) {
        mSmoothScrollRequested = smoothScroll;
        mSelectionToScreenRequested = true;

        if ((mSelectedContactUri == null && uri != null)
                || (mSelectedContactUri != null && !mSelectedContactUri.equals(uri))) {
            mSelectionVerified = false;
            mSelectionRequired = required;
            mSelectionPersistenceRequested = persistent;
            mSelectedContactUri = uri;
            parseSelectedContactUri();

            if (!willReloadData) {
                // Configure the adapter to show the selection based on the
                // lookup key extracted from the URI
                ContactListAdapter adapter = getAdapter();
                if (adapter != null) {
                    adapter.setSelectedContact(mSelectedContactDirectoryId,
                            mSelectedContactLookupKey, mSelectedContactId);
                    getListView().invalidateViews();
                }
            }

            // Also, launch a loader to pick up a new lookup URI in case it has changed
            refreshSelectedContactUri();
        }
    }

    private void parseSelectedContactUri() {
        if (mSelectedContactUri != null) {
            String directoryParam =
                    mSelectedContactUri.getQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY);
            mSelectedContactDirectoryId = TextUtils.isEmpty(directoryParam) ? Directory.DEFAULT
                    : Long.parseLong(directoryParam);
            if (mSelectedContactUri.toString().startsWith(Contacts.CONTENT_LOOKUP_URI.toString())) {
                List<String> pathSegments = mSelectedContactUri.getPathSegments();
                mSelectedContactLookupKey = Uri.encode(pathSegments.get(2));
                if (pathSegments.size() == 4) {
                    mSelectedContactId = ContentUris.parseId(mSelectedContactUri);
                }
            } else if (mSelectedContactUri.toString().startsWith(Contacts.CONTENT_URI.toString()) &&
                    mSelectedContactUri.getPathSegments().size() >= 2) {
                mSelectedContactLookupKey = null;
                mSelectedContactId = ContentUris.parseId(mSelectedContactUri);
            } else {
                Log.e(TAG, "Unsupported contact URI: " + mSelectedContactUri);
                mSelectedContactLookupKey = null;
                mSelectedContactId = 0;
            }

        } else {
            mSelectedContactDirectoryId = Directory.DEFAULT;
            mSelectedContactLookupKey = null;
            mSelectedContactId = 0;
        }
    }

    @Override
    public ContactListAdapter getAdapter() {
        return (ContactListAdapter) super.getAdapter();
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        boolean searchMode = isSearchMode();
        if (!searchMode && mFilter != null) {
            adapter.setFilter(mFilter);
            if (mSelectionRequired
                    || mFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                adapter.setSelectedContact(
                        mSelectedContactDirectoryId, mSelectedContactLookupKey, mSelectedContactId);
            }
        }

        adapter.setIncludeFavorites(!searchMode && mFilter.isContactsFilterType());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        super.onLoadFinished(loader, data);
        mSelectionVerified = false;

        // Refresh the currently selected lookup in case it changed while we were sleeping
        refreshSelectedContactUri();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void checkSelection() {
        if (mSelectionVerified) {
            return;
        }

        if (mRefreshingContactUri) {
            return;
        }

        if (isLoadingDirectoryList()) {
            return;
        }

        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        boolean directoryLoading = true;
        int count = adapter.getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = adapter.getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directory = (DirectoryPartition) partition;
                if (directory.getDirectoryId() == mSelectedContactDirectoryId) {
                    directoryLoading = directory.isLoading();
                    break;
                }
            }
        }

        if (directoryLoading) {
            return;
        }

        adapter.setSelectedContact(
                mSelectedContactDirectoryId, mSelectedContactLookupKey, mSelectedContactId);

        final int selectedPosition = adapter.getSelectedContactPosition();
        if (selectedPosition != -1) {
            mLastSelectedPosition = selectedPosition;
        } else {
            if (isSearchMode()) {
                if (mDelaySelection) {
                    selectFirstFoundContactAfterDelay();
                    if (mListener != null) {
                        mListener.onSelectionChange();
                    }
                    return;
                }
            } else if (mSelectionRequired) {
                // A specific contact was requested, but it's not in the loaded list.

                // Try reconfiguring and reloading the list that will hopefully contain
                // the requested contact. Only take one attempt to avoid an infinite loop
                // in case the contact cannot be found at all.
                mSelectionRequired = false;

                // If we were looking at a different specific contact, just reload
                // FILTER_TYPE_ALL_ACCOUNTS is needed for the case where a new contact is added
                // on a tablet and the loader is returning a stale list.  In this case, the contact
                // will not be found until the next load. b/7621855 This will only fix the most
                // common case where all accounts are shown. It will not fix the one account case.
                // TODO: we may want to add more FILTER_TYPEs or relax this check to fix all other
                // FILTER_TYPE cases.
                if (mFilter != null
                        && (mFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT
                        || mFilter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS)) {
                    reloadData();
                } else {
                    // Otherwise, call the listener, which will adjust the filter.
                    notifyInvalidSelection();
                }
                return;
            } else if (mFilter != null
                    && mFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                // If we were trying to load a specific contact, but that contact no longer
                // exists, call the listener, which will adjust the filter.
                notifyInvalidSelection();
                return;
            }

            saveSelectedUri(null);
            selectDefaultContact();
        }

        mSelectionRequired = false;
        mSelectionVerified = true;

        if (mSelectionPersistenceRequested) {
            saveSelectedUri(mSelectedContactUri);
            mSelectionPersistenceRequested = false;
        }

        if (mSelectionToScreenRequested) {
            requestSelectionToScreen(selectedPosition);
        }

        getListView().invalidateViews();

        if (mListener != null) {
            mListener.onSelectionChange();
        }
    }

    /**
     * Automatically selects the first found contact in search mode.  The selection
     * is updated after a delay to allow the user to type without to much UI churn
     * and to save bandwidth on directory queries.
     */
    public void selectFirstFoundContactAfterDelay() {
        Handler handler = getHandler();
        handler.removeMessages(MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT);

        String queryString = getQueryString();
        if (queryString != null
                && queryString.length() >= AUTOSELECT_FIRST_FOUND_CONTACT_MIN_QUERY_LENGTH) {
            handler.sendEmptyMessageDelayed(MESSAGE_AUTOSELECT_FIRST_FOUND_CONTACT,
                    DELAY_AUTOSELECT_FIRST_FOUND_CONTACT_MILLIS);
        } else {
            setSelectedContactUri(null, false, false, false, false);
        }
    }

    protected void selectDefaultContact() {
        Uri contactUri = null;
        ContactListAdapter adapter = getAdapter();
        if (mLastSelectedPosition != -1) {
            int count = adapter.getCount();
            int pos = mLastSelectedPosition;
            if (pos >= count && count > 0) {
                pos = count - 1;
            }
            contactUri = adapter.getContactUri(pos);
        }

        if (contactUri == null) {
            contactUri = adapter.getFirstContactUri();
        }

        setSelectedContactUri(contactUri, false, mSmoothScrollRequested, false, false);
    }

    protected void requestSelectionToScreen(int selectedPosition) {
        if (selectedPosition != -1) {
            AutoScrollListView listView = (AutoScrollListView)getListView();
            listView.requestPositionToScreen(
                    selectedPosition + listView.getHeaderViewsCount(), mSmoothScrollRequested);
            mSelectionToScreenRequested = false;
        }
    }

    @Override
    public boolean isLoading() {
        return mRefreshingContactUri || super.isLoading();
    }

    @Override
    protected void startLoading() {
        mStartedLoading = true;
        mSelectionVerified = false;
        super.startLoading();
    }

    public void reloadDataAndSetSelectedUri(Uri uri) {
        setSelectedContactUri(uri, true, true, true, true);
        reloadData();
    }

    @Override
    public void reloadData() {
        if (mStartedLoading) {
            mSelectionVerified = false;
            mLastSelectedPosition = -1;
            super.reloadData();
        }
    }

    public void setOnContactListActionListener(OnContactBrowserActionListener listener) {
        mListener = listener;
    }

    public void viewContact(int position, Uri contactUri, boolean isEnterpriseContact) {
        setSelectedContactUri(contactUri, false, false, true, false);
        if (mListener != null) mListener.onViewContactAction(position, contactUri,
                isEnterpriseContact);
    }

    public void deleteContact(Uri contactUri) {
        if (mListener != null) mListener.onDeleteContactAction(contactUri);
    }

    private void notifyInvalidSelection() {
        if (mListener != null) mListener.onInvalidSelection();
    }

    @Override
    protected void finish() {
        super.finish();
        if (mListener != null) mListener.onFinishAction();
    }

    private void saveSelectedUri(Uri contactUri) {
        if (isSearchMode()) {
            return;
        }

        ContactListFilter.storeToPreferences(mPrefs, mFilter);

        Editor editor = mPrefs.edit();
        if (contactUri == null) {
            editor.remove(getPersistentSelectionKey());
        } else {
            editor.putString(getPersistentSelectionKey(), contactUri.toString());
        }
        editor.apply();
    }

    private void restoreSelectedUri(boolean willReloadData) {
        // The meaning of mSelectionRequired is that we need to show some
        // selection other than the previous selection saved in shared preferences
        if (mSelectionRequired) {
            return;
        }

        String selectedUri = mPrefs.getString(getPersistentSelectionKey(), null);
        if (selectedUri == null) {
            setSelectedContactUri(null, false, false, false, willReloadData);
        } else {
            setSelectedContactUri(Uri.parse(selectedUri), false, false, false, willReloadData);
        }
    }

    private void saveFilter() {
        ContactListFilter.storeToPreferences(mPrefs, mFilter);
    }

    private void restoreFilter() {
        mFilter = ContactListFilter.restoreDefaultPreferences(mPrefs);
    }

    private String getPersistentSelectionKey() {
        if (mFilter == null) {
            return mPersistentSelectionPrefix;
        } else {
            return mPersistentSelectionPrefix + "-" + mFilter.getId();
        }
    }
}
