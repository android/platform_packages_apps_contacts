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

import android.accounts.Account;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Directory;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.Experiments;
import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter;
import com.android.contacts.activities.PeopleActivity;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.interactions.ContactDeletionInteraction;
import com.android.contacts.interactions.ContactMultiDeletionInteraction;
import com.android.contacts.interactions.ContactMultiDeletionInteraction.MultiContactDeleteListener;
import com.android.contacts.logging.ListEvent;
import com.android.contacts.logging.Logger;
import com.android.contacts.logging.ScreenEvent;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.AccountFilterUtil;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contacts.util.SyncUtil;
import com.android.contactsbind.FeatureHighlightHelper;
import com.android.contactsbind.experiments.Flags;
import com.google.common.util.concurrent.Futures;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment
        implements EnableGlobalSyncDialogFragment.Listener {

    private static final String TAG = "DefaultListFragment";
    private static final String ENABLE_DEBUG_OPTIONS_HIDDEN_CODE = "debug debug!";
    private static final String KEY_DELETION_IN_PROGRESS = "deletionInProgress";
    private static final String KEY_SEARCH_RESULT_CLICKED = "search_result_clicked";

    private static final int ACTIVITY_REQUEST_CODE_SHARE = 0;

    private View mSearchHeaderView;
    private View mSearchProgress;
    private View mEmptyAccountView;
    private View mEmptyHomeView;
    private View mAccountFilterContainer;
    private TextView mSearchProgressText;

    private SwipeRefreshLayout mSwipeRefreshLayout;
    private final Handler mHandler = new Handler();
    private final Runnable mCancelRefresh = new Runnable() {
        @Override
        public void run() {
            if (mSwipeRefreshLayout.isRefreshing()) {
                mSwipeRefreshLayout.setRefreshing(false);
            }
        }
    };

    private View mAlertContainer;
    private TextView mAlertText;
    private ImageView mAlertDismissIcon;
    private int mReasonSyncOff = SyncUtil.SYNC_SETTING_SYNC_ON;

    private boolean mContactsAvailable;
    private boolean mEnableDebugMenuOptions;
    private boolean mIsRecreatedInstance;
    private boolean mOptionsMenuContactsAvailable;

    private boolean mCanSetActionBar = false;

    /**
     * If {@link #configureFragment()} is already called. Used to avoid calling it twice
     * in {@link #onResume()}.
     * (This initialization only needs to be done once in onResume() when the Activity was just
     * created from scratch -- i.e. onCreate() was just called)
     */
    private boolean mFragmentInitialized;

    private boolean mFromOnNewIntent;

    /**
     * This is to tell whether we need to restart ContactMultiDeletionInteraction and set listener.
     * if screen is rotated while deletion dialog is shown.
     */
    private boolean mIsDeletionInProgress;

    /**
     * This is to disable {@link #onOptionsItemSelected} when we trying to stop the
     * activity/fragment.
     */
    private boolean mDisableOptionItemSelected;

    private boolean mSearchResultClicked;

    private ActionBarAdapter mActionBarAdapter;
    private PeopleActivity mActivity;
    private ContactsRequest mContactsRequest;
    private ContactListFilterController mContactListFilterController;

    private Future<List<AccountInfo>> mWritableAccountsFuture;

    private final ActionBarAdapter.Listener mActionBarListener = new ActionBarAdapter.Listener() {
        @Override
        public void onAction(int action) {
            switch (action) {
                case ActionBarAdapter.Listener.Action.START_SELECTION_MODE:
                    displayCheckBoxes(true);
                    startSearchOrSelectionMode();
                    break;
                case ActionBarAdapter.Listener.Action.START_SEARCH_MODE:
                    if (!mIsRecreatedInstance) {
                        Logger.logScreenView(mActivity, ScreenEvent.ScreenType.SEARCH);
                    }
                    startSearchOrSelectionMode();
                    break;
                case ActionBarAdapter.Listener.Action.BEGIN_STOPPING_SEARCH_AND_SELECTION_MODE:
                    mActivity.showFabWithAnimation(/* showFab */ true);
                    break;
                case ActionBarAdapter.Listener.Action.STOP_SEARCH_AND_SELECTION_MODE:
                    // If queryString is empty, fragment data will not be reloaded,
                    // so hamburger promo should be checked now.
                    // Otherwise, promo should be checked and displayed after reloading, b/30706521.
                    if (TextUtils.isEmpty(getQueryString())) {
                        maybeShowHamburgerFeatureHighlight();
                    }
                    setQueryTextToFragment("");
                    maybeHideCheckBoxes();
                    mActivity.invalidateOptionsMenu();
                    mActivity.showFabWithAnimation(/* showFab */ true);

                    // Alert user if sync is off and not dismissed before
                    setSyncOffAlert();

                    // Determine whether the account has pullToRefresh feature
                    setSwipeRefreshLayoutEnabledOrNot(getFilter());
                    break;
                case ActionBarAdapter.Listener.Action.CHANGE_SEARCH_QUERY:
                    final String queryString = mActionBarAdapter.getQueryString();
                    setQueryTextToFragment(queryString);
                    updateDebugOptionsVisibility(
                            ENABLE_DEBUG_OPTIONS_HIDDEN_CODE.equals(queryString));
                    break;
                default:
                    throw new IllegalStateException("Unknown ActionBarAdapter action: " + action);
            }
        }

        private void startSearchOrSelectionMode() {
            configureContactListFragment();
            maybeHideCheckBoxes();
            mActivity.invalidateOptionsMenu();
            mActivity.showFabWithAnimation(/* showFab */ false);

            final Context context = getContext();
            if (!SharedPreferenceUtil.getHamburgerPromoTriggerActionHappenedBefore(context)) {
                SharedPreferenceUtil.setHamburgerPromoTriggerActionHappenedBefore(context);
            }
        }

        private void updateDebugOptionsVisibility(boolean visible) {
            if (mEnableDebugMenuOptions != visible) {
                mEnableDebugMenuOptions = visible;
                mActivity.invalidateOptionsMenu();
            }
        }

        private void setQueryTextToFragment(String query) {
            setQueryString(query, true);
            setVisibleScrollbarEnabled(!isSearchMode());
        }

        @Override
        public void onUpButtonPressed() {
            mActivity.onBackPressed();
        }
    };

    private final View.OnClickListener mAddContactListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            AccountFilterUtil.startEditorIntent(getContext(), mActivity.getIntent(), getFilter());
        }
    };

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        // Don't use a QuickContactBadge. Just use a regular ImageView. Using a QuickContactBadge
        // inside the ListView prevents us from using MODE_FULLY_EXPANDED and messes up ripples.
        setQuickContactEnabled(false);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
        setDisplayDirectoryHeader(false);
        setHasOptionsMenu(true);
    }

    /**
     * Whether a search result was clicked by the user. Tracked so that we can distinguish
     * between exiting the search mode after a result was clicked from exiting w/o clicking
     * any search result.
     */
    public boolean wasSearchResultClicked() {
        return mSearchResultClicked;
    }

    /**
     * Resets whether a search result was clicked by the user to false.
     */
    public void resetSearchResultClicked() {
        mSearchResultClicked = false;
    }

    @Override
    public CursorLoader createCursorLoader(Context context) {
        return new FavoritesAndContactsLoader(context);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (loader.getId() == Directory.DEFAULT) {
            bindListHeader(data == null ? 0 : data.getCount());
        }
        super.onLoadFinished(loader, data);
        if (!isSearchMode()) {
            maybeShowHamburgerFeatureHighlight();
        }
        if (mActionBarAdapter != null) {
            mActionBarAdapter.updateOverflowButtonColor();
        }
    }

    private void maybeShowHamburgerFeatureHighlight() {
        if (mActionBarAdapter!= null && !mActionBarAdapter.isSearchMode()
                && !mActionBarAdapter.isSelectionMode()
                && !isTalkbackOnAndOnPreLollipopMr1()
                && SharedPreferenceUtil.getShouldShowHamburgerPromo(getContext())) {
            if (FeatureHighlightHelper.showHamburgerFeatureHighlight(mActivity)) {
                SharedPreferenceUtil.setHamburgerPromoDisplayedBefore(getContext());
            }
        }
    }

    // There's a crash if we show feature highlight when Talkback is on, on API 21 and below.
    // See b/31180524.
    private boolean isTalkbackOnAndOnPreLollipopMr1(){
        return ((AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE))
                .isTouchExplorationEnabled()
                    && !CompatUtils.isLollipopMr1Compatible();
    }

    private void bindListHeader(int numberOfContacts) {
        final ContactListFilter filter = getFilter();
        // If the phone has at least one Google account whose sync status is unsyncable or pending
        // or active, we have to make mAccountFilterContainer visible.
        if (!isSearchMode() && numberOfContacts <= 0 && shouldShowEmptyView(filter)) {
            if (filter != null && filter.isContactsFilterType()) {
                makeViewVisible(mEmptyHomeView);
            } else {
                makeViewVisible(mEmptyAccountView);
            }
            return;
        }
        makeViewVisible(mAccountFilterContainer);
        if (isSearchMode()) {
            hideHeaderAndAddPadding(getContext(), getListView(), mAccountFilterContainer);
        } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            bindListHeaderCustom(getListView(), mAccountFilterContainer);
        } else if (filter.filterType != ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                    filter.accountName, filter.accountType, filter.dataSet);
            bindListHeader(getContext(), getListView(), mAccountFilterContainer,
                    accountWithDataSet, numberOfContacts);
        } else {
            hideHeaderAndAddPadding(getContext(), getListView(), mAccountFilterContainer);
        }
    }

    /**
     * If at least one Google account is unsyncable or its sync status is pending or active, we
     * should not show empty view even if the number of contacts is 0. We should show sync status
     * with empty list instead.
     */
    private boolean shouldShowEmptyView(ContactListFilter filter) {
        if (filter == null) {
            return true;
        }
        // TODO(samchen) : Check ContactListFilter.FILTER_TYPE_CUSTOM
        if (ContactListFilter.FILTER_TYPE_DEFAULT == filter.filterType
                || ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS == filter.filterType) {
            final List<AccountInfo> syncableAccounts =
                    AccountTypeManager.getInstance(getContext()).getWritableGoogleAccounts();

            if (syncableAccounts != null && syncableAccounts.size() > 0) {
                for (AccountInfo info : syncableAccounts) {
                    // Won't be null because Google accounts have a non-null name and type.
                    final Account account = info.getAccount().getAccountOrNull();
                    if (SyncUtil.isSyncStatusPendingOrActive(account)
                            || SyncUtil.isUnsyncableGoogleAccount(account)) {
                        return false;
                    }
                }
            }
        } else if (ContactListFilter.FILTER_TYPE_ACCOUNT == filter.filterType) {
            final Account account = new Account(filter.accountName, filter.accountType);
            return !(SyncUtil.isSyncStatusPendingOrActive(account)
                    || SyncUtil.isUnsyncableGoogleAccount(account));
        }
        return true;
    }

    // Show the view that's specified by id and hide the other two.
    private void makeViewVisible(View view) {
        mEmptyAccountView.setVisibility(view == mEmptyAccountView ? View.VISIBLE : View.GONE);
        mEmptyHomeView.setVisibility(view == mEmptyHomeView ? View.VISIBLE : View.GONE);
        mAccountFilterContainer.setVisibility(
                view == mAccountFilterContainer ? View.VISIBLE : View.GONE);
    }

    public void scrollToTop() {
        if (getListView() != null) {
            getListView().setSelection(0);
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        final Uri uri = getAdapter().getContactUri(position);
        if (uri == null) {
            return;
        }
        if (getAdapter().isDisplayingCheckBoxes()) {
            super.onItemClick(position, id);
            return;
        } else {
            if (isSearchMode()) {
                mSearchResultClicked = true;
                Logger.logSearchEvent(createSearchStateForSearchResultClick(position));
            }
        }
        viewContact(position, uri, getAdapter().isEnterpriseContact(position));
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        adapter.setPhotoPosition(
                ContactListItemView.getDefaultPhotoPosition(/* opposite = */ false));
        return adapter;
    }

    @Override
    public ContactListFilter getFilter() {
        return mContactListFilterController.getFilter();
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        final View view = inflater.inflate(R.layout.contact_list_content, null);

        mAccountFilterContainer = view.findViewById(R.id.account_filter_header_container);

        // Add empty main view and account view to list.
        final FrameLayout contactListLayout = (FrameLayout) view.findViewById(R.id.contact_list);
        mEmptyAccountView = getEmptyAccountView(inflater);
        mEmptyHomeView = getEmptyHomeView(inflater);
        contactListLayout.addView(mEmptyAccountView);
        contactListLayout.addView(mEmptyHomeView);

        return view;
    }

    private View getEmptyHomeView(LayoutInflater inflater) {
        final View emptyHomeView = inflater.inflate(R.layout.empty_home_view, null);
        // Set image margins.
        final ImageView image = (ImageView) emptyHomeView.findViewById(R.id.empty_home_image);
        final LayoutParams params = (LayoutParams) image.getLayoutParams();
        final int screenHeight = getResources().getDisplayMetrics().heightPixels;
        final int marginTop = screenHeight / 2 -
                getResources().getDimensionPixelSize(R.dimen.empty_home_view_image_offset) ;
        params.setMargins(0, marginTop, 0, 0);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        image.setLayoutParams(params);

        // Set up add contact button.
        final Button addContactButton =
                (Button) emptyHomeView.findViewById(R.id.add_contact_button);
        addContactButton.setOnClickListener(mAddContactListener);
        return emptyHomeView;
    }

    private View getEmptyAccountView(LayoutInflater inflater) {
        final View emptyAccountView = inflater.inflate(R.layout.empty_account_view, null);
        // Set image margins.
        final ImageView image = (ImageView) emptyAccountView.findViewById(R.id.empty_account_image);
        final LayoutParams params = (LayoutParams) image.getLayoutParams();
        final int height = getResources().getDisplayMetrics().heightPixels;
        final int divisor =
                getResources().getInteger(R.integer.empty_account_view_image_margin_divisor);
        final int offset =
                getResources().getDimensionPixelSize(R.dimen.empty_account_view_image_offset);
        params.setMargins(0, height / divisor + offset, 0, 0);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        image.setLayoutParams(params);

        // Set up add contact button.
        final Button addContactButton =
                (Button) emptyAccountView.findViewById(R.id.add_contact_button);
        addContactButton.setOnClickListener(mAddContactListener);
        return emptyAccountView;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mIsRecreatedInstance = (savedState != null);
        mContactListFilterController = ContactListFilterController.getInstance(getContext());
        mContactListFilterController.checkFilterValidity(false);
        // Use FILTER_TYPE_ALL_ACCOUNTS filter if the instance is not a re-created one.
        // This is useful when user upgrades app while an account filter was
        // stored in sharedPreference in a previous version of Contacts app.
        final ContactListFilter filter = mIsRecreatedInstance
                ? getFilter()
                : AccountFilterUtil.createContactsFilter(getContext());
        setContactListFilter(filter);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        initSwipeRefreshLayout();

        // Putting the header view inside a container will allow us to make
        // it invisible later. See checkHeaderViewVisibility()
        final FrameLayout headerContainer = new FrameLayout(inflater.getContext());
        mSearchHeaderView = inflater.inflate(R.layout.search_header, null, false);
        headerContainer.addView(mSearchHeaderView);
        getListView().addHeaderView(headerContainer, null, false);
        checkHeaderViewVisibility();

        mSearchProgress = getView().findViewById(R.id.search_progress);
        mSearchProgressText = (TextView) mSearchHeaderView.findViewById(R.id.totalContactsText);

        mAlertContainer = getView().findViewById(R.id.alert_container);
        mAlertText = (TextView) mAlertContainer.findViewById(R.id.alert_text);
        mAlertDismissIcon = (ImageView) mAlertContainer.findViewById(R.id.alert_dismiss_icon);
        mAlertText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnSyncOn();
            }
        });
        mAlertDismissIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        mAlertContainer.setVisibility(View.GONE);
    }

    private void turnSyncOn() {
        final ContactListFilter filter = getFilter();
        if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT
                && mReasonSyncOff == SyncUtil.SYNC_SETTING_ACCOUNT_SYNC_OFF) {
            ContentResolver.setSyncAutomatically(
                    new Account(filter.accountName, filter.accountType),
                    ContactsContract.AUTHORITY, true);
            mAlertContainer.setVisibility(View.GONE);
        } else {
            final EnableGlobalSyncDialogFragment dialog = new
                    EnableGlobalSyncDialogFragment();
            dialog.show(this, filter);
        }
    }

    @Override
    public void onEnableAutoSync(ContactListFilter filter) {
        // Turn on auto-sync
        ContentResolver.setMasterSyncAutomatically(true);

        // This should be OK (won't block) because this only happens after a user action
        final List<AccountInfo> accountInfos = Futures.getUnchecked(mWritableAccountsFuture);
        // Also enable Contacts sync
        final List<AccountWithDataSet> accounts = AccountInfo.extractAccounts(accountInfos);
        final List<Account> syncableAccounts = filter.getSyncableAccounts(accounts);
        if (syncableAccounts != null && syncableAccounts.size() > 0) {
            for (Account account : syncableAccounts) {
                ContentResolver.setSyncAutomatically(new Account(account.name, account.type),
                        ContactsContract.AUTHORITY, true);
            }
        }
        mAlertContainer.setVisibility(View.GONE);
    }

    private void dismiss() {
        if (mReasonSyncOff == SyncUtil.SYNC_SETTING_GLOBAL_SYNC_OFF) {
            SharedPreferenceUtil.incNumOfDismissesForAutoSyncOff(getContext());
        } else if (mReasonSyncOff == SyncUtil.SYNC_SETTING_ACCOUNT_SYNC_OFF) {
            SharedPreferenceUtil.incNumOfDismissesForAccountSyncOff(
                    getContext(), getFilter().accountName);
        }
        mAlertContainer.setVisibility(View.GONE);
    }

    private void initSwipeRefreshLayout() {
        mSwipeRefreshLayout = (SwipeRefreshLayout) mView.findViewById(R.id.swipe_refresh);
        if (mSwipeRefreshLayout == null) {
            return;
        }

        mSwipeRefreshLayout.setEnabled(true);
        // Request sync contacts
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mHandler.removeCallbacks(mCancelRefresh);

                final boolean isNetworkConnected = SyncUtil.isNetworkConnected(getContext());
                if (!isNetworkConnected) {
                    mSwipeRefreshLayout.setRefreshing(false);
                    ((PeopleActivity)getActivity()).showConnectionErrorMsg();
                    return;
                }

                syncContacts(getFilter());
                mHandler.postDelayed(mCancelRefresh, Flags.getInstance()
                        .getInteger(Experiments.PULL_TO_REFRESH_CANCEL_REFRESH_MILLIS));
            }
        });
        mSwipeRefreshLayout.setColorSchemeResources(
                R.color.swipe_refresh_color1,
                R.color.swipe_refresh_color2,
                R.color.swipe_refresh_color3,
                R.color.swipe_refresh_color4);
        mSwipeRefreshLayout.setDistanceToTriggerSync(
                (int) getResources().getDimension(R.dimen.pull_to_refresh_distance));
    }

    /**
     * Request sync for the Google accounts (not include Google+ accounts) specified by the given
     * filter.
     */
    private void syncContacts(ContactListFilter filter) {
        if (filter == null) {
            return;
        }

        final Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);

        final List<AccountWithDataSet> accounts = AccountInfo.extractAccounts(
                Futures.getUnchecked(mWritableAccountsFuture));
        final List<Account> syncableAccounts = filter.getSyncableAccounts(accounts);
        if (syncableAccounts != null && syncableAccounts.size() > 0) {
            for (Account account : syncableAccounts) {
                // We can prioritize Contacts sync if sync is not initialized yet.
                if (!SyncUtil.isSyncStatusPendingOrActive(account)
                        || SyncUtil.isUnsyncableGoogleAccount(account)) {
                    ContentResolver.requestSync(account, ContactsContract.AUTHORITY, bundle);
                }
            }
        }
    }

    private void setSyncOffAlert() {
        final ContactListFilter filter = getFilter();
        final Account account =  filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT
                && filter.isGoogleAccountType()
                ? new Account(filter.accountName, filter.accountType) : null;

        if (account == null && !filter.isContactsFilterType()) {
            mAlertContainer.setVisibility(View.GONE);
        } else {
            mReasonSyncOff = SyncUtil.calculateReasonSyncOff(getContext(), account);
            final boolean isAlertVisible =
                    SyncUtil.isAlertVisible(getContext(), account, mReasonSyncOff);
            setSyncOffMsg(mReasonSyncOff);
            mAlertContainer.setVisibility(isAlertVisible ? View.VISIBLE : View.GONE);
        }
    }

    private void setSyncOffMsg(int reason) {
        final Resources resources = getResources();
        switch (reason) {
            case SyncUtil.SYNC_SETTING_GLOBAL_SYNC_OFF:
                mAlertText.setText(resources.getString(R.string.auto_sync_off));
                break;
            case SyncUtil.SYNC_SETTING_ACCOUNT_SYNC_OFF:
                mAlertText.setText(resources.getString(R.string.account_sync_off));
                break;
            default:
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mActivity = (PeopleActivity) getActivity();
        mActionBarAdapter = new ActionBarAdapter(mActivity, mActionBarListener,
                mActivity.getSupportActionBar(), mActivity.getToolbar(),
                R.string.enter_contact_name);
        mActionBarAdapter.setShowHomeIcon(true);
        initializeActionBarAdapter(savedInstanceState);
        if (isSearchMode()) {
            mActionBarAdapter.setFocusOnSearchView();
        }

        setCheckBoxListListener(new CheckBoxListListener());
        setOnContactListActionListener(new ContactBrowserActionListener());
        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean(KEY_DELETION_IN_PROGRESS)) {
                deleteSelectedContacts();
            }
            mSearchResultClicked = savedInstanceState.getBoolean(KEY_SEARCH_RESULT_CLICKED);
        }

        setDirectorySearchMode();
        mCanSetActionBar = true;
    }

    public void initializeActionBarAdapter(Bundle savedInstanceState) {
        if (mActionBarAdapter != null) {
            mActionBarAdapter.initialize(savedInstanceState, mContactsRequest);
        }
    }

    private void configureFragment() {
        if (mFragmentInitialized && !mFromOnNewIntent) {
            return;
        }

        mFragmentInitialized = true;

        if (mFromOnNewIntent || !mIsRecreatedInstance) {
            mFromOnNewIntent = false;
            configureFragmentForRequest();
        }

        configureContactListFragment();
    }

    private void configureFragmentForRequest() {
        ContactListFilter filter = null;
        final int actionCode = mContactsRequest.getActionCode();
        boolean searchMode = mContactsRequest.isSearchMode();
        switch (actionCode) {
            case ContactsRequest.ACTION_ALL_CONTACTS:
                filter = AccountFilterUtil.createContactsFilter(getContext());
                break;
            case ContactsRequest.ACTION_CONTACTS_WITH_PHONES:
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_WITH_PHONE_NUMBERS_ONLY);
                break;

            case ContactsRequest.ACTION_FREQUENT:
            case ContactsRequest.ACTION_STREQUENT:
            case ContactsRequest.ACTION_STARRED:
            case ContactsRequest.ACTION_VIEW_CONTACT:
            default:
                break;
        }

        if (filter != null) {
            setContactListFilter(filter);
            searchMode = false;
        }

        if (mContactsRequest.getContactUri() != null) {
            searchMode = false;
        }

        mActionBarAdapter.setSearchMode(searchMode);
        configureContactListFragmentForRequest();
    }

    private void configureContactListFragmentForRequest() {
        final Uri contactUri = mContactsRequest.getContactUri();
        if (contactUri != null) {
            setSelectedContactUri(contactUri);
        }

        setQueryString(mActionBarAdapter.getQueryString(), true);
        setVisibleScrollbarEnabled(!isSearchMode());
    }

    private void setDirectorySearchMode() {
        if (mContactsRequest != null && mContactsRequest.isDirectorySearchEnabled()) {
            setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_DEFAULT);
        } else {
            setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        configureFragment();
        maybeShowHamburgerFeatureHighlight();
        // Re-register the listener, which may have been cleared when onSaveInstanceState was
        // called. See also: onSaveInstanceState
        mActionBarAdapter.setListener(mActionBarListener);
        mDisableOptionItemSelected = false;
        maybeHideCheckBoxes();

        mWritableAccountsFuture = AccountTypeManager.getInstance(getContext()).filterAccountsAsync(
                AccountTypeManager.writableFilter());
    }

    private void maybeHideCheckBoxes() {
        if (!mActionBarAdapter.isSelectionMode()) {
            displayCheckBoxes(false);
        }
    }

    public ActionBarAdapter getActionBarAdapter(){
        return mActionBarAdapter;
    }

    @Override
    protected void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        checkHeaderViewVisibility();
        if (!flag) showSearchProgress(false);
    }

    /** Show or hide the directory-search progress spinner. */
    private void showSearchProgress(boolean show) {
        if (mSearchProgress != null) {
            mSearchProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void checkHeaderViewVisibility() {
        // Hide the search header by default.
        if (mSearchHeaderView != null) {
            mSearchHeaderView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void setListHeader() {
        if (!isSearchMode()) {
            return;
        }
        ContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return;
        }

        // In search mode we only display the header if there is nothing found
        if (TextUtils.isEmpty(getQueryString()) || !adapter.areAllPartitionsEmpty()) {
            mSearchHeaderView.setVisibility(View.GONE);
            showSearchProgress(false);
        } else {
            mSearchHeaderView.setVisibility(View.VISIBLE);
            if (adapter.isLoading()) {
                mSearchProgressText.setText(R.string.search_results_searching);
                showSearchProgress(true);
            } else {
                mSearchProgressText.setText(R.string.listFoundAllContactsZero);
                mSearchProgressText.sendAccessibilityEvent(
                        AccessibilityEvent.TYPE_VIEW_SELECTED);
                showSearchProgress(false);
            }
        }
    }

    public SwipeRefreshLayout getSwipeRefreshLayout() {
        return mSwipeRefreshLayout;
    }

    private final class CheckBoxListListener implements OnCheckBoxListActionListener {
        @Override
        public void onStartDisplayingCheckBoxes() {
            mActionBarAdapter.setSelectionMode(true);
            mActivity.invalidateOptionsMenu();
        }

        @Override
        public void onSelectedContactIdsChanged() {
            mActionBarAdapter.setSelectionCount(getSelectedContactIds().size());
            mActivity.invalidateOptionsMenu();
            mActionBarAdapter.updateOverflowButtonColor();
        }

        @Override
        public void onStopDisplayingCheckBoxes() {
            mActionBarAdapter.setSelectionMode(false);
        }
    }

    public void setFilterAndUpdateTitle(ContactListFilter filter) {
        setFilterAndUpdateTitle(filter, true);
    }

    private void setFilterAndUpdateTitle(ContactListFilter filter, boolean restoreSelectedUri) {
        setContactListFilter(filter);
        updateListFilter(filter, restoreSelectedUri);
        mActivity.setTitle(AccountFilterUtil.getActionBarTitleForFilter(mActivity, filter));

        // Alert user if sync is off and not dismissed before
        setSyncOffAlert();

        // Determine whether the account has pullToRefresh feature
        setSwipeRefreshLayoutEnabledOrNot(filter);
    }

    private void setSwipeRefreshLayoutEnabledOrNot(ContactListFilter filter) {
        final SwipeRefreshLayout swipeRefreshLayout = getSwipeRefreshLayout();
        if (swipeRefreshLayout == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Can not load swipeRefreshLayout, swipeRefreshLayout is null");
            }
            return;
        }

        swipeRefreshLayout.setRefreshing(false);
        swipeRefreshLayout.setEnabled(false);

        if (filter != null && !mActionBarAdapter.isSearchMode()
                && !mActionBarAdapter.isSelectionMode()) {
            if (filter.isSyncable()
                    || (filter.shouldShowSyncState()
                    && SyncUtil.hasSyncableAccount(AccountTypeManager.getInstance(getContext())))) {
                swipeRefreshLayout.setEnabled(true);
            }
        }
    }

    private void configureContactListFragment() {
        // Filter may be changed when activity is in background.
        setFilterAndUpdateTitle(getFilter());
        setVerticalScrollbarPosition(getScrollBarPosition());
        setSelectionVisible(false);
        mActivity.invalidateOptionsMenu();
    }

    private int getScrollBarPosition() {
        final Locale locale = Locale.getDefault();
        final boolean isRTL =
                TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL;
        return isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT;
    }

    private final class ContactBrowserActionListener implements OnContactBrowserActionListener {
        ContactBrowserActionListener() {}

        @Override
        public void onSelectionChange() {
        }

        @Override
        public void onViewContactAction(int position, Uri contactLookupUri,
                boolean isEnterpriseContact) {
            if (isEnterpriseContact) {
                // No implicit intent as user may have a different contacts app in work profile.
                ContactsContract.QuickContact.showQuickContact(getContext(), new Rect(),
                        contactLookupUri, QuickContactActivity.MODE_FULLY_EXPANDED, null);
            } else {
                final int previousScreen;
                if (isSearchMode()) {
                    previousScreen = ScreenEvent.ScreenType.SEARCH;
                } else {
                    if (isAllContactsFilter(getFilter())) {
                        if (position < getAdapter().getNumberOfFavorites()) {
                            previousScreen = ScreenEvent.ScreenType.FAVORITES;
                        } else {
                            previousScreen = ScreenEvent.ScreenType.ALL_CONTACTS;
                        }
                    } else {
                        previousScreen = ScreenEvent.ScreenType.LIST_ACCOUNT;
                    }
                }

                Logger.logListEvent(ListEvent.ActionType.CLICK,
                        /* listType */ getListTypeIncludingSearch(),
                        /* count */ getAdapter().getCount(),
                        /* clickedIndex */ position, /* numSelected */ 0);

                ImplicitIntentsUtil.startQuickContact(
                        getActivity(), contactLookupUri, previousScreen);
            }
        }

        @Override
        public void onDeleteContactAction(Uri contactUri) {
            ContactDeletionInteraction.start(mActivity, contactUri, false);
        }

        @Override
        public void onFinishAction() {
            mActivity.onBackPressed();
        }

        @Override
        public void onInvalidSelection() {
            ContactListFilter filter;
            ContactListFilter currentFilter = getFilter();
            if (currentFilter != null
                    && currentFilter.filterType == ContactListFilter.FILTER_TYPE_SINGLE_CONTACT) {
                filter = AccountFilterUtil.createContactsFilter(getContext());
                setFilterAndUpdateTitle(filter);
            } else {
                filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_SINGLE_CONTACT);
                setFilterAndUpdateTitle(filter, /* restoreSelectedUri */ false);
            }
            setContactListFilter(filter);
        }
    }

    private boolean isAllContactsFilter(ContactListFilter filter) {
        return filter != null && filter.isContactsFilterType();
    }

    public void setContactsAvailable(boolean contactsAvailable) {
        mContactsAvailable = contactsAvailable;
    }

    /**
     * Set filter via ContactListFilterController
     */
    private void setContactListFilter(ContactListFilter filter) {
        mContactListFilterController.setContactListFilter(filter,
                /* persistent */ isAllContactsFilter(filter));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (!mContactsAvailable || mActivity.isInSecondLevel()) {
            // If contacts aren't available or this fragment is not visible, hide all menu items.
            return;
        }
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.people_options, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mOptionsMenuContactsAvailable = mContactsAvailable;
        if (!mOptionsMenuContactsAvailable) {
            return;
        }

        final boolean isSearchOrSelectionMode = mActionBarAdapter.isSearchMode()
                || mActionBarAdapter.isSelectionMode();
        makeMenuItemVisible(menu, R.id.menu_search, !isSearchOrSelectionMode);

        final boolean showSelectedContactOptions = mActionBarAdapter.isSelectionMode()
                && getSelectedContactIds().size() != 0;
        makeMenuItemVisible(menu, R.id.menu_share, showSelectedContactOptions);
        makeMenuItemVisible(menu, R.id.menu_delete, showSelectedContactOptions);
        final boolean showLinkContactsOptions = mActionBarAdapter.isSelectionMode()
                && getSelectedContactIds().size() > 1;
        makeMenuItemVisible(menu, R.id.menu_join, showLinkContactsOptions);

        // Debug options need to be visible even in search mode.
        makeMenuItemVisible(menu, R.id.export_database, mEnableDebugMenuOptions &&
                hasExportIntentHandler());

        // Light tint the icons for normal mode, dark tint for search or selection mode.
        for (int i = 0; i < menu.size(); ++i) {
            final Drawable icon = menu.getItem(i).getIcon();
            if (icon != null && !isSearchOrSelectionMode) {
                icon.mutate().setColorFilter(ContextCompat.getColor(getContext(),
                        R.color.actionbar_icon_color), PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    private void makeMenuItemVisible(Menu menu, int itemId, boolean visible) {
        final MenuItem item = menu.findItem(itemId);
        if (item != null) {
            item.setVisible(visible);
        }
    }

    private boolean hasExportIntentHandler() {
        final Intent intent = new Intent();
        intent.setAction("com.android.providers.contacts.DUMP_DATABASE");
        final List<ResolveInfo> receivers =
                getContext().getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receivers != null && receivers.size() > 0;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDisableOptionItemSelected) {
            return false;
        }

        final int id = item.getItemId();
        if (id == android.R.id.home) {
            if (mActionBarAdapter.isUpShowing()) {
                // "UP" icon press -- should be treated as "back".
                mActivity.onBackPressed();
            }
            return true;
        } else if (id == R.id.menu_search) {
            if (!mActionBarAdapter.isSelectionMode()) {
                mActionBarAdapter.setSearchMode(true);
            }
            return true;
        } else if (id == R.id.menu_share) {
            shareSelectedContacts();
            return true;
        } else if (id == R.id.menu_join) {
            Logger.logListEvent(ListEvent.ActionType.LINK,
                        /* listType */ getListTypeIncludingSearch(),
                        /* count */ getAdapter().getCount(), /* clickedIndex */ -1,
                        /* numSelected */ getAdapter().getSelectedContactIds().size());
            joinSelectedContacts();
            return true;
        } else if (id == R.id.menu_delete) {
            deleteSelectedContacts();
            return true;
        } else if (id == R.id.export_database) {
            final Intent intent = new Intent("com.android.providers.contacts.DUMP_DATABASE");
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
            ImplicitIntentsUtil.startActivityOutsideApp(getContext(), intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Share all contacts that are currently selected. This method is pretty inefficient for
     * handling large numbers of contacts. I don't expect this to be a problem.
     */
    private void shareSelectedContacts() {
        final StringBuilder uriListBuilder = new StringBuilder();
        for (Long contactId : getSelectedContactIds()) {
            final Uri contactUri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, contactId);
            final Uri lookupUri = ContactsContract.Contacts.getLookupUri(
                    getContext().getContentResolver(), contactUri);
            if (lookupUri == null) {
                continue;
            }
            final List<String> pathSegments = lookupUri.getPathSegments();
            if (pathSegments.size() < 2) {
                continue;
            }
            final String lookupKey = pathSegments.get(pathSegments.size() - 2);
            if (uriListBuilder.length() > 0) {
                uriListBuilder.append(':');
            }
            uriListBuilder.append(Uri.encode(lookupKey));
        }
        if (uriListBuilder.length() == 0) {
            return;
        }
        final Uri uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
                Uri.encode(uriListBuilder.toString()));
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        try {
            startActivityForResult(Intent.createChooser(intent, getResources().getQuantityString(
                    R.plurals.title_share_via,/* quantity */ getSelectedContactIds().size()))
                    , ACTIVITY_REQUEST_CODE_SHARE);
        } catch (final ActivityNotFoundException ex) {
            Toast.makeText(getContext(), R.string.share_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void joinSelectedContacts() {
        final Context context = getContext();
        final Intent intent = ContactSaveService.createJoinSeveralContactsIntent(
                context, getSelectedContactIdsArray());
        context.startService(intent);

        mActionBarAdapter.setSelectionMode(false);
    }

    private void deleteSelectedContacts() {
        final ContactMultiDeletionInteraction multiDeletionInteraction =
                ContactMultiDeletionInteraction.start(this, getSelectedContactIds());
        multiDeletionInteraction.setListener(new MultiDeleteListener());
        mIsDeletionInProgress = true;
    }

    private final class MultiDeleteListener implements MultiContactDeleteListener {
        @Override
        public void onDeletionFinished() {
            // The parameters count and numSelected are both the number of contacts before deletion.
            Logger.logListEvent(ListEvent.ActionType.DELETE,
                /* listType */ getListTypeIncludingSearch(),
                /* count */ getAdapter().getCount(), /* clickedIndex */ -1,
                /* numSelected */ getSelectedContactIds().size());
            mActionBarAdapter.setSelectionMode(false);
            mIsDeletionInProgress = false;
        }
    }

    private int getListTypeIncludingSearch() {
        return isSearchMode() ? ListEvent.ListType.SEARCH_RESULT : getListType();
    }

    public void setParameters(ContactsRequest contactsRequest, boolean fromOnNewIntent) {
        mContactsRequest = contactsRequest;
        mFromOnNewIntent = fromOnNewIntent;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // TODO: Using the new startActivityWithResultFromFragment API this should not be needed
            // anymore
            case ContactEntryListFragment.ACTIVITY_REQUEST_CODE_PICKER:
                if (resultCode == Activity.RESULT_OK) {
                    onPickerResult(data);
                }
            case ACTIVITY_REQUEST_CODE_SHARE:
                Logger.logListEvent(ListEvent.ActionType.SHARE,
                    /* listType */ getListTypeIncludingSearch(),
                    /* count */ getAdapter().getCount(), /* clickedIndex */ -1,
                    /* numSelected */ getAdapter().getSelectedContactIds().size());

// TODO fix or remove multipicker code: ag/54762
//                else if (resultCode == RESULT_CANCELED && mMode == MODE_PICK_MULTIPLE_PHONES) {
//                    // Finish the activity if the sub activity was canceled as back key is used
//                    // to confirm user selection in MODE_PICK_MULTIPLE_PHONES.
//                    finish();
//                }
//                break;
        }
    }

    public boolean getOptionsMenuContactsAvailable() {
        return mOptionsMenuContactsAvailable;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Clear the listener to make sure we don't get callbacks after onSaveInstanceState,
        // in order to avoid doing fragment transactions after it.
        // TODO Figure out a better way to deal with the issue (ag/120686).
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
            mActionBarAdapter.onSaveInstanceState(outState);
        }
        mDisableOptionItemSelected = true;
        outState.putBoolean(KEY_DELETION_IN_PROGRESS, mIsDeletionInProgress);
        outState.putBoolean(KEY_SEARCH_RESULT_CLICKED, mSearchResultClicked);
    }

    @Override
    public void onPause() {
        mOptionsMenuContactsAvailable = false;
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mActionBarAdapter != null) {
            mActionBarAdapter.setListener(null);
        }
        super.onDestroy();
    }

    public boolean onKeyDown(int unicodeChar) {
        if (mActionBarAdapter != null && mActionBarAdapter.isSelectionMode()) {
            // Ignore keyboard input when in selection mode.
            return true;
        }

        if (mActionBarAdapter != null && !mActionBarAdapter.isSearchMode()) {
            final String query = new String(new int[]{unicodeChar}, 0, 1);
            mActionBarAdapter.setSearchMode(true);
            mActionBarAdapter.setQueryString(query);
            return true;
        }

        return false;
    }

    public boolean canSetActionBar() {
        return mCanSetActionBar;
    }
}
