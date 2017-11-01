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
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;

import com.android.common.widget.CompositeCursorAdapter.Partition;
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.logging.ListEvent.ActionType;
import com.android.contacts.logging.Logger;
import com.android.contacts.preference.ContactsPreferences;

import java.util.Locale;

/**
 * Common base class for various contact-related list fragments.
 */
public abstract class ContactEntryListFragment<T extends ContactEntryListAdapter>
        extends Fragment
        implements OnItemClickListener, OnScrollListener, OnFocusChangeListener, OnTouchListener,
                OnItemLongClickListener, LoaderCallbacks<Cursor> {
    private static final String TAG = "ContactEntryList";

    // TODO: Make this protected. This should not be used from the PeopleActivity but
    // instead use the new startActivityWithResultFromFragment API
    public static final int ACTIVITY_REQUEST_CODE_PICKER = 1;

    private static final String KEY_LIST_STATE = "liststate";
    private static final String KEY_SECTION_HEADER_DISPLAY_ENABLED = "sectionHeaderDisplayEnabled";
    private static final String KEY_PHOTO_LOADER_ENABLED = "photoLoaderEnabled";
    private static final String KEY_QUICK_CONTACT_ENABLED = "quickContactEnabled";
    private static final String KEY_ADJUST_SELECTION_BOUNDS_ENABLED =
            "adjustSelectionBoundsEnabled";
    private static final String KEY_SEARCH_MODE = "searchMode";
    private static final String KEY_DISPLAY_DIRECTORY_HEADER = "displayDirectoryHeader";
    private static final String KEY_VISIBLE_SCROLLBAR_ENABLED = "visibleScrollbarEnabled";
    private static final String KEY_SCROLLBAR_POSITION = "scrollbarPosition";
    private static final String KEY_QUERY_STRING = "queryString";
    private static final String KEY_DIRECTORY_SEARCH_MODE = "directorySearchMode";
    private static final String KEY_SELECTION_VISIBLE = "selectionVisible";
    private static final String KEY_REQUEST = "request";
    private static final String KEY_DARK_THEME = "darkTheme";
    private static final String KEY_LEGACY_COMPATIBILITY = "legacyCompatibility";
    private static final String KEY_DIRECTORY_RESULT_LIMIT = "directoryResultLimit";
    private static final String KEY_LOGS_LIST_EVENTS = "logsListEvents";
    private static final String KEY_DATA_LOADED = "dataLoaded";

    private static final String DIRECTORY_ID_ARG_KEY = "directoryId";

    private static final int DIRECTORY_LOADER_ID = -1;

    private static final int DIRECTORY_SEARCH_DELAY_MILLIS = 300;
    private static final int DIRECTORY_SEARCH_MESSAGE = 1;

    private static final int DEFAULT_DIRECTORY_RESULT_LIMIT = 20;

    private boolean mSectionHeaderDisplayEnabled;
    private boolean mPhotoLoaderEnabled;
    private boolean mQuickContactEnabled = true;
    private boolean mAdjustSelectionBoundsEnabled = true;
    private boolean mIncludeFavorites;
    private boolean mSearchMode;
    private boolean mDisplayDirectoryHeader = true;
    private boolean mVisibleScrollbarEnabled;
    private boolean mShowEmptyListForEmptyQuery;
    private int mVerticalScrollbarPosition = getDefaultVerticalScrollbarPosition();
    private String mQueryString;
    private int mDirectorySearchMode = DirectoryListLoader.SEARCH_MODE_NONE;
    private boolean mSelectionVisible;
    private boolean mLegacyCompatibility;
    // Whether we should log list LOAD events. It may be modified when list filter is changed.
    private boolean mLogListEvents = true;
    // Whether data has been loaded ever. It will stay true once it's set to true in the lifecycle.
    // We use this flag to log LOAD events when the activity/fragment is initialized.
    private boolean mDataLoaded;

    private boolean mEnabled = true;

    private T mAdapter;
    protected View mView;
    private ListView mListView;

    /**
     * Used to save the scrolling state of the list when the fragment is not recreated.
     */
    private int mListViewTopIndex;
    private int mListViewTopOffset;

    /**
     * Used for keeping track of the scroll state of the list.
     */
    private Parcelable mListState;

    /**
     * The type of the contacts list.
     */
    private int mListType;

    private int mDisplayOrder;
    private int mSortOrder;
    private int mDirectoryResultLimit = DEFAULT_DIRECTORY_RESULT_LIMIT;

    private ContactPhotoManager mPhotoManager;
    private ContactsPreferences mContactsPrefs;

    private boolean mForceLoad;

    private boolean mDarkTheme;

    private static final int STATUS_NOT_LOADED = 0;
    private static final int STATUS_LOADING = 1;
    private static final int STATUS_LOADED = 2;

    private int mDirectoryListStatus = STATUS_NOT_LOADED;

    /**
     * Indicates whether we are doing the initial complete load of data (false) or
     * a refresh caused by a change notification (true)
     */
    private boolean mLoadPriorityDirectoriesOnly;

    private Context mContext;

    private LoaderManager mLoaderManager;

    private Handler mDelayedDirectorySearchHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == DIRECTORY_SEARCH_MESSAGE) {
                loadDirectoryPartition(msg.arg1, (DirectoryPartition) msg.obj);
            }
        }
    };
    private int defaultVerticalScrollbarPosition;

    protected abstract View inflateView(LayoutInflater inflater, ViewGroup container);
    protected abstract T createListAdapter();

    /**
     * @param position Please note that the position is already adjusted for
     *            header views, so "0" means the first list item below header
     *            views.
     */
    protected abstract void onItemClick(int position, long id);

    /**
     * @param position Please note that the position is already adjusted for
     *            header views, so "0" means the first list item below header
     *            views.
     */
    protected boolean onItemLongClick(int position, long id) {
        return false;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        setContext(activity);
        setLoaderManager(super.getLoaderManager());
    }

    /**
     * Sets a context for the fragment in the unit test environment.
     */
    public void setContext(Context context) {
        mContext = context;
        configurePhotoLoader();
    }

    public Context getContext() {
        return mContext;
    }

    public void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;
            if (mAdapter != null) {
                if (mEnabled) {
                    reloadData();
                } else {
                    mAdapter.clearPartitions();
                }
            }
        }
    }

    /**
     * Overrides a loader manager for use in unit tests.
     */
    public void setLoaderManager(LoaderManager loaderManager) {
        mLoaderManager = loaderManager;
    }

    @Override
    public LoaderManager getLoaderManager() {
        return mLoaderManager;
    }

    public T getAdapter() {
        return mAdapter;
    }

    @Override
    public View getView() {
        return mView;
    }

    public ListView getListView() {
        return mListView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_SECTION_HEADER_DISPLAY_ENABLED, mSectionHeaderDisplayEnabled);
        outState.putBoolean(KEY_PHOTO_LOADER_ENABLED, mPhotoLoaderEnabled);
        outState.putBoolean(KEY_QUICK_CONTACT_ENABLED, mQuickContactEnabled);
        outState.putBoolean(KEY_ADJUST_SELECTION_BOUNDS_ENABLED, mAdjustSelectionBoundsEnabled);
        outState.putBoolean(KEY_SEARCH_MODE, mSearchMode);
        outState.putBoolean(KEY_DISPLAY_DIRECTORY_HEADER, mDisplayDirectoryHeader);
        outState.putBoolean(KEY_VISIBLE_SCROLLBAR_ENABLED, mVisibleScrollbarEnabled);
        outState.putInt(KEY_SCROLLBAR_POSITION, mVerticalScrollbarPosition);
        outState.putInt(KEY_DIRECTORY_SEARCH_MODE, mDirectorySearchMode);
        outState.putBoolean(KEY_SELECTION_VISIBLE, mSelectionVisible);
        outState.putBoolean(KEY_LEGACY_COMPATIBILITY, mLegacyCompatibility);
        outState.putString(KEY_QUERY_STRING, mQueryString);
        outState.putInt(KEY_DIRECTORY_RESULT_LIMIT, mDirectoryResultLimit);
        outState.putBoolean(KEY_DARK_THEME, mDarkTheme);
        outState.putBoolean(KEY_LOGS_LIST_EVENTS, mLogListEvents);
        outState.putBoolean(KEY_DATA_LOADED, mDataLoaded);

        if (mListView != null) {
            outState.putParcelable(KEY_LIST_STATE, mListView.onSaveInstanceState());
        }
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        restoreSavedState(savedState);
        mAdapter = createListAdapter();
        mContactsPrefs = new ContactsPreferences(mContext);
    }

    public void restoreSavedState(Bundle savedState) {
        if (savedState == null) {
            return;
        }

        mSectionHeaderDisplayEnabled = savedState.getBoolean(KEY_SECTION_HEADER_DISPLAY_ENABLED);
        mPhotoLoaderEnabled = savedState.getBoolean(KEY_PHOTO_LOADER_ENABLED);
        mQuickContactEnabled = savedState.getBoolean(KEY_QUICK_CONTACT_ENABLED);
        mAdjustSelectionBoundsEnabled = savedState.getBoolean(KEY_ADJUST_SELECTION_BOUNDS_ENABLED);
        mSearchMode = savedState.getBoolean(KEY_SEARCH_MODE);
        mDisplayDirectoryHeader = savedState.getBoolean(KEY_DISPLAY_DIRECTORY_HEADER);
        mVisibleScrollbarEnabled = savedState.getBoolean(KEY_VISIBLE_SCROLLBAR_ENABLED);
        mVerticalScrollbarPosition = savedState.getInt(KEY_SCROLLBAR_POSITION);
        mDirectorySearchMode = savedState.getInt(KEY_DIRECTORY_SEARCH_MODE);
        mSelectionVisible = savedState.getBoolean(KEY_SELECTION_VISIBLE);
        mLegacyCompatibility = savedState.getBoolean(KEY_LEGACY_COMPATIBILITY);
        mQueryString = savedState.getString(KEY_QUERY_STRING);
        mDirectoryResultLimit = savedState.getInt(KEY_DIRECTORY_RESULT_LIMIT);
        mDarkTheme = savedState.getBoolean(KEY_DARK_THEME);

        // Retrieve list state. This will be applied in onLoadFinished
        mListState = savedState.getParcelable(KEY_LIST_STATE);
    }

    @Override
    public void onStart() {
        super.onStart();

        mContactsPrefs.registerChangeListener(mPreferencesChangeListener);

        mForceLoad = loadPreferences();

        mDirectoryListStatus = STATUS_NOT_LOADED;
        mLoadPriorityDirectoriesOnly = true;

        startLoading();
    }

    protected void startLoading() {
        if (mAdapter == null) {
            // The method was called before the fragment was started
            return;
        }

        configureAdapter();
        int partitionCount = mAdapter.getPartitionCount();
        for (int i = 0; i < partitionCount; i++) {
            Partition partition = mAdapter.getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                if (directoryPartition.getStatus() == DirectoryPartition.STATUS_NOT_LOADED) {
                    if (directoryPartition.isPriorityDirectory() || !mLoadPriorityDirectoriesOnly) {
                        startLoadingDirectoryPartition(i);
                    }
                }
            } else {
                getLoaderManager().initLoader(i, null, this);
            }
        }

        // Next time this method is called, we should start loading non-priority directories
        mLoadPriorityDirectoriesOnly = false;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == DIRECTORY_LOADER_ID) {
            DirectoryListLoader loader = new DirectoryListLoader(mContext);
            loader.setDirectorySearchMode(mAdapter.getDirectorySearchMode());
            loader.setLocalInvisibleDirectoryEnabled(
                    ContactEntryListAdapter.LOCAL_INVISIBLE_DIRECTORY_ENABLED);
            return loader;
        } else {
            CursorLoader loader = createCursorLoader(mContext);
            long directoryId = args != null && args.containsKey(DIRECTORY_ID_ARG_KEY)
                    ? args.getLong(DIRECTORY_ID_ARG_KEY)
                    : Directory.DEFAULT;
            mAdapter.configureLoader(loader, directoryId);
            return loader;
        }
    }

    public CursorLoader createCursorLoader(Context context) {
        return new CursorLoader(context, null, null, null, null, null) {
            @Override
            protected Cursor onLoadInBackground() {
                try {
                    return super.onLoadInBackground();
                } catch (RuntimeException e) {
                    // We don't even know what the projection should be, so no point trying to
                    // return an empty MatrixCursor with the correct projection here.
                    Log.w(TAG, "RuntimeException while trying to query ContactsProvider.");
                    return null;
                }
            }
        };
    }

    private void startLoadingDirectoryPartition(int partitionIndex) {
        DirectoryPartition partition = (DirectoryPartition)mAdapter.getPartition(partitionIndex);
        partition.setStatus(DirectoryPartition.STATUS_LOADING);
        long directoryId = partition.getDirectoryId();
        if (mForceLoad) {
            if (directoryId == Directory.DEFAULT) {
                loadDirectoryPartition(partitionIndex, partition);
            } else {
                loadDirectoryPartitionDelayed(partitionIndex, partition);
            }
        } else {
            Bundle args = new Bundle();
            args.putLong(DIRECTORY_ID_ARG_KEY, directoryId);
            getLoaderManager().initLoader(partitionIndex, args, this);
        }
    }

    /**
     * Queues up a delayed request to search the specified directory. Since
     * directory search will likely introduce a lot of network traffic, we want
     * to wait for a pause in the user's typing before sending a directory request.
     */
    private void loadDirectoryPartitionDelayed(int partitionIndex, DirectoryPartition partition) {
        mDelayedDirectorySearchHandler.removeMessages(DIRECTORY_SEARCH_MESSAGE, partition);
        Message msg = mDelayedDirectorySearchHandler.obtainMessage(
                DIRECTORY_SEARCH_MESSAGE, partitionIndex, 0, partition);
        mDelayedDirectorySearchHandler.sendMessageDelayed(msg, DIRECTORY_SEARCH_DELAY_MILLIS);
    }

    /**
     * Loads the directory partition.
     */
    protected void loadDirectoryPartition(int partitionIndex, DirectoryPartition partition) {
        Bundle args = new Bundle();
        args.putLong(DIRECTORY_ID_ARG_KEY, partition.getDirectoryId());
        getLoaderManager().restartLoader(partitionIndex, args, this);
    }

    /**
     * Cancels all queued directory loading requests.
     */
    private void removePendingDirectorySearchRequests() {
        mDelayedDirectorySearchHandler.removeMessages(DIRECTORY_SEARCH_MESSAGE);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (!mEnabled) {
            return;
        }

        getListView().setVisibility(View.VISIBLE);
        getView().setVisibility(View.VISIBLE);

        int loaderId = loader.getId();
        if (loaderId == DIRECTORY_LOADER_ID) {
            mDirectoryListStatus = STATUS_LOADED;
            mAdapter.changeDirectories(data);
            startLoading();
        } else {
            onPartitionLoaded(loaderId, data);
            if (isSearchMode()) {
                int directorySearchMode = getDirectorySearchMode();
                if (directorySearchMode != DirectoryListLoader.SEARCH_MODE_NONE) {
                    if (mDirectoryListStatus == STATUS_NOT_LOADED) {
                        mDirectoryListStatus = STATUS_LOADING;
                        getLoaderManager().initLoader(DIRECTORY_LOADER_ID, null, this);
                    } else {
                        startLoading();
                    }
                }
            } else {
                maybeLogListEvent();
                mDirectoryListStatus = STATUS_NOT_LOADED;
                getLoaderManager().destroyLoader(DIRECTORY_LOADER_ID);
            }
        }
    }

    protected void maybeLogListEvent() {
        if (!mDataLoaded || mLogListEvents) {
            Logger.logListEvent(ActionType.LOAD, getListType(), getAdapter().getCount(),
                        /* clickedIndex */ -1, /* numSelected */ 0);
            mLogListEvents = false;
            mDataLoaded = true;
        }
    }

    public void onLoaderReset(Loader<Cursor> loader) {
    }

    protected void onPartitionLoaded(int partitionIndex, Cursor data) {
        if (partitionIndex >= mAdapter.getPartitionCount()) {
            // When we get unsolicited data, ignore it.  This could happen
            // when we are switching from search mode to the default mode.
            return;
        }

        mAdapter.changeCursor(partitionIndex, data);
        setListHeader();

        if (!isLoading()) {
            completeRestoreInstanceState();
        }
    }

    public boolean isLoading() {
        if (mAdapter != null && mAdapter.isLoading()) {
            return true;
        }

        if (isLoadingDirectoryList()) {
            return true;
        }

        return false;
    }

    public boolean isLoadingDirectoryList() {
        return isSearchMode() && getDirectorySearchMode() != DirectoryListLoader.SEARCH_MODE_NONE
                && (mDirectoryListStatus == STATUS_NOT_LOADED
                        || mDirectoryListStatus == STATUS_LOADING);
    }

    @Override
    public void onStop() {
        super.onStop();
        mContactsPrefs.unregisterChangeListener();
        mAdapter.clearPartitions();
    }

    protected void reloadData() {
        removePendingDirectorySearchRequests();
        mAdapter.onDataReload();
        mLoadPriorityDirectoriesOnly = true;
        mForceLoad = true;
        startLoading();
    }

    /**
     * Shows a view at the top of the list.
     */
    protected void setListHeader() {}

    /**
     * Provides logic that dismisses this fragment. The default implementation
     * does nothing.
     */
    protected void finish() {
    }

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        if (mSectionHeaderDisplayEnabled != flag) {
            mSectionHeaderDisplayEnabled = flag;
            if (mAdapter != null) {
                mAdapter.setSectionHeaderDisplayEnabled(flag);
            }
            configureVerticalScrollbar();
        }
    }

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setVisibleScrollbarEnabled(boolean flag) {
        if (mVisibleScrollbarEnabled != flag) {
            mVisibleScrollbarEnabled = flag;
            configureVerticalScrollbar();
        }
    }

    public boolean isVisibleScrollbarEnabled() {
        return mVisibleScrollbarEnabled;
    }

    public void setVerticalScrollbarPosition(int position) {
        if (mVerticalScrollbarPosition != position) {
            mVerticalScrollbarPosition = position;
            configureVerticalScrollbar();
        }
    }

    private void configureVerticalScrollbar() {
        boolean hasScrollbar = isVisibleScrollbarEnabled() && isSectionHeaderDisplayEnabled();

        if (mListView != null) {
            mListView.setFastScrollEnabled(hasScrollbar);
            mListView.setVerticalScrollbarPosition(mVerticalScrollbarPosition);
            mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        }
    }

    public void setPhotoLoaderEnabled(boolean flag) {
        mPhotoLoaderEnabled = flag;
        configurePhotoLoader();
    }

    public boolean isPhotoLoaderEnabled() {
        return mPhotoLoaderEnabled;
    }

    /**
     * Returns true if the list is supposed to visually highlight the selected item.
     */
    public boolean isSelectionVisible() {
        return mSelectionVisible;
    }

    public void setSelectionVisible(boolean flag) {
        this.mSelectionVisible = flag;
    }

    public void setQuickContactEnabled(boolean flag) {
        this.mQuickContactEnabled = flag;
    }

    public void setAdjustSelectionBoundsEnabled(boolean flag) {
        mAdjustSelectionBoundsEnabled = flag;
    }

    public void setIncludeFavorites(boolean flag) {
        mIncludeFavorites = flag;
        if (mAdapter != null) {
            mAdapter.setIncludeFavorites(flag);
        }
    }

    public void setDisplayDirectoryHeader(boolean flag) {
        mDisplayDirectoryHeader = flag;
    }

    /**
     * Enter/exit search mode. This is method is tightly related to the current query, and should
     * only be called by {@link #setQueryString}.
     *
     * Also note this method doesn't call {@link #reloadData()}; {@link #setQueryString} does it.
     */
    protected void setSearchMode(boolean flag) {
        if (mSearchMode != flag) {
            mSearchMode = flag;
            setSectionHeaderDisplayEnabled(!mSearchMode);

            if (!flag) {
                mDirectoryListStatus = STATUS_NOT_LOADED;
                getLoaderManager().destroyLoader(DIRECTORY_LOADER_ID);
            }

            if (mAdapter != null) {
                mAdapter.setSearchMode(flag);

                mAdapter.clearPartitions();
                if (!flag) {
                    // If we are switching from search to regular display, remove all directory
                    // partitions after default one, assuming they are remote directories which
                    // should be cleaned up on exiting the search mode.
                    mAdapter.removeDirectoriesAfterDefault();
                }
                mAdapter.configureDefaultPartition(false, shouldDisplayDirectoryHeader());
            }

            if (mListView != null) {
                mListView.setFastScrollEnabled(!flag);
            }
        }
    }

    /**
     * When not in search mode, directory header should always be hidden.
     * When in search mode, directory header should be displayed when mDisplayDirectoryHeader is
     * set to true. (mDisplayDirectoryHeader default value is true)
     */
    private boolean shouldDisplayDirectoryHeader() {
        if (!mSearchMode) {
            return false;
        }
        return mDisplayDirectoryHeader;
    }

    public final boolean isSearchMode() {
        return mSearchMode;
    }

    public final String getQueryString() {
        return mQueryString;
    }

    // TODO: the paramter delaySelection is not in use, and let's remove it.
    public void setQueryString(String queryString, boolean delaySelection) {
        if (!TextUtils.equals(mQueryString, queryString)) {
            if (mShowEmptyListForEmptyQuery && mAdapter != null && mListView != null) {
                if (TextUtils.isEmpty(mQueryString)) {
                    // Restore the adapter if the query used to be empty.
                    mListView.setAdapter(mAdapter);
                } else if (TextUtils.isEmpty(queryString)) {
                    // Instantly clear the list view if the new query is empty.
                    mListView.setAdapter(null);
                }
            }

            mQueryString = queryString;
            setSearchMode(!TextUtils.isEmpty(mQueryString) || mShowEmptyListForEmptyQuery);

            if (mAdapter != null) {
                mAdapter.setQueryString(queryString);
                reloadData();
            }
        }
    }

    public void setShowEmptyListForNullQuery(boolean show) {
        mShowEmptyListForEmptyQuery = show;
    }

    public int getDirectoryLoaderId() {
        return DIRECTORY_LOADER_ID;
    }

    public int getDirectorySearchMode() {
        return mDirectorySearchMode;
    }

    public void setDirectorySearchMode(int mode) {
        mDirectorySearchMode = mode;
    }

    public boolean isLegacyCompatibilityMode() {
        return mLegacyCompatibility;
    }

    public void setLegacyCompatibilityMode(boolean flag) {
        mLegacyCompatibility = flag;
    }

    protected int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    protected void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        if (mAdapter != null) {
            mAdapter.setContactNameDisplayOrder(displayOrder);
        }
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        if (mAdapter != null) {
            mAdapter.setSortOrder(sortOrder);
        }
    }

    public void setDirectoryResultLimit(int limit) {
        mDirectoryResultLimit = limit;
    }

    protected boolean loadPreferences() {
        boolean changed = false;
        if (getContactNameDisplayOrder() != mContactsPrefs.getDisplayOrder()) {
            setContactNameDisplayOrder(mContactsPrefs.getDisplayOrder());
            changed = true;
        }

        if (getSortOrder() != mContactsPrefs.getSortOrder()) {
            setSortOrder(mContactsPrefs.getSortOrder());
            changed = true;
        }

        return changed;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        onCreateView(inflater, container);

        boolean searchMode = isSearchMode();
        mAdapter.setSearchMode(searchMode);
        mAdapter.configureDefaultPartition(false, shouldDisplayDirectoryHeader());
        mAdapter.setPhotoLoader(mPhotoManager);
        mListView.setAdapter(mAdapter);

        if (!isSearchMode()) {
            mListView.setFocusableInTouchMode(true);
            mListView.requestFocus();
        }

        if (savedInstanceState != null) {
            mLogListEvents = savedInstanceState.getBoolean(KEY_LOGS_LIST_EVENTS, true);
            mDataLoaded = savedInstanceState.getBoolean(KEY_DATA_LOADED, false);
        }

        return mView;
    }

    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        mView = inflateView(inflater, container);

        mListView = (ListView)mView.findViewById(android.R.id.list);
        if (mListView == null) {
            throw new RuntimeException(
                    "Your content must have a ListView whose id attribute is " +
                    "'android.R.id.list'");
        }

        View emptyView = mView.findViewById(android.R.id.empty);
        if (emptyView != null) {
            mListView.setEmptyView(emptyView);
        }

        mListView.setOnItemClickListener(this);
        mListView.setOnItemLongClickListener(this);
        mListView.setOnFocusChangeListener(this);
        mListView.setOnTouchListener(this);
        mListView.setFastScrollEnabled(!isSearchMode());

        // Tell list view to not show dividers. We'll do it ourself so that we can *not* show
        // them when an A-Z headers is visible.
        mListView.setDividerHeight(0);

        // We manually save/restore the listview state
        mListView.setSaveEnabled(false);

        configureVerticalScrollbar();
        configurePhotoLoader();

        getAdapter().setFragmentRootView(getView());
    }

    protected void configurePhotoLoader() {
        if (isPhotoLoaderEnabled() && mContext != null) {
            if (mPhotoManager == null) {
                mPhotoManager = ContactPhotoManager.getInstance(mContext);
            }
            if (mListView != null) {
                mListView.setOnScrollListener(this);
            }
            if (mAdapter != null) {
                mAdapter.setPhotoLoader(mPhotoManager);
            }
        }
    }

    protected void configureAdapter() {
        if (mAdapter == null) {
            return;
        }

        mAdapter.setQuickContactEnabled(mQuickContactEnabled);
        mAdapter.setAdjustSelectionBoundsEnabled(mAdjustSelectionBoundsEnabled);
        mAdapter.setIncludeFavorites(mIncludeFavorites);
        mAdapter.setQueryString(mQueryString);
        mAdapter.setDirectorySearchMode(mDirectorySearchMode);
        mAdapter.setPinnedPartitionHeadersEnabled(false);
        mAdapter.setContactNameDisplayOrder(mDisplayOrder);
        mAdapter.setSortOrder(mSortOrder);
        mAdapter.setSectionHeaderDisplayEnabled(mSectionHeaderDisplayEnabled);
        mAdapter.setSelectionVisible(mSelectionVisible);
        mAdapter.setDirectoryResultLimit(mDirectoryResultLimit);
        mAdapter.setDarkTheme(mDarkTheme);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
            mPhotoManager.pause();
        } else if (isPhotoLoaderEnabled()) {
            mPhotoManager.resume();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        hideSoftKeyboard();

        int adjPosition = position - mListView.getHeaderViewsCount();
        if (adjPosition >= 0) {
            onItemClick(adjPosition, id);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        int adjPosition = position - mListView.getHeaderViewsCount();

        if (adjPosition >= 0) {
            return onItemLongClick(adjPosition, id);
        }
        return false;
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mListView.getWindowToken(), 0);
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == mListView && hasFocus) {
            hideSoftKeyboard();
        }
    }

    /**
     * Dismisses the soft keyboard when the list is touched.
     */
    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (view == mListView) {
            hideSoftKeyboard();
        }
        return false;
    }

    @Override
    public void onPause() {
        // Save the scrolling state of the list view
        mListViewTopIndex = mListView.getFirstVisiblePosition();
        View v = mListView.getChildAt(0);
        mListViewTopOffset = (v == null) ? 0 : (v.getTop() - mListView.getPaddingTop());

        super.onPause();
        removePendingDirectorySearchRequests();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Restore the selection of the list view. See b/19982820.
        // This has to be done manually because if the list view has its emptyView set,
        // the scrolling state will be reset when clearPartitions() is called on the adapter.
        mListView.setSelectionFromTop(mListViewTopIndex, mListViewTopOffset);
    }

    /**
     * Restore the list state after the adapter is populated.
     */
    protected void completeRestoreInstanceState() {
        if (mListState != null) {
            mListView.onRestoreInstanceState(mListState);
            mListState = null;
        }
    }

    public void setDarkTheme(boolean value) {
        mDarkTheme = value;
        if (mAdapter != null) mAdapter.setDarkTheme(value);
    }

    /**
     * Processes a result returned by the contact picker.
     */
    public void onPickerResult(Intent data) {
        throw new UnsupportedOperationException("Picker result handler is not implemented.");
    }

    private ContactsPreferences.ChangeListener mPreferencesChangeListener =
            new ContactsPreferences.ChangeListener() {
        @Override
        public void onChange() {
            loadPreferences();
            reloadData();
        }
    };

    private int getDefaultVerticalScrollbarPosition() {
        final Locale locale = Locale.getDefault();
        final int layoutDirection = TextUtils.getLayoutDirectionFromLocale(locale);
        switch (layoutDirection) {
            case View.LAYOUT_DIRECTION_RTL:
                return View.SCROLLBAR_POSITION_LEFT;
            case View.LAYOUT_DIRECTION_LTR:
            default:
                return View.SCROLLBAR_POSITION_RIGHT;
        }
    }

    public void setListType(int listType) {
        mListType = listType;
    }

    public int getListType() {
        return mListType;
    }

    public void setLogListEvents(boolean logListEvents) {
        mLogListEvents = logListEvents;
    }
}
