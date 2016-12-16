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

package com.android.contacts.activities;

import android.accounts.Account;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncStatusObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.ProviderStatus;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsDrawerActivity;
import com.android.contacts.R;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.group.GroupMembersFragment;
import com.android.contacts.group.GroupMetaData;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController.ContactListFilterListener;
import com.android.contacts.list.ContactsIntentResolver;
import com.android.contacts.list.ContactsRequest;
import com.android.contacts.list.ContactsUnavailableFragment;
import com.android.contacts.list.DefaultContactBrowseListFragment;
import com.android.contacts.list.ProviderStatusWatcher;
import com.android.contacts.list.ProviderStatusWatcher.ProviderStatusListener;
import com.android.contacts.logging.Logger;
import com.android.contacts.logging.ScreenEvent.ScreenType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.AccountFilterUtil;
import com.android.contacts.util.Constants;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.SyncUtil;
import com.android.contacts.widget.FloatingActionButtonController;
import com.android.contactsbind.FeatureHighlightHelper;
import com.android.contactsbind.ObjectFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Displays a list to browse contacts.
 */
public class PeopleActivity extends ContactsDrawerActivity {

    private static final String TAG = "PeopleActivity";
    private static final String TAG_ALL = "contacts-all";
    private static final String TAG_UNAVAILABLE = "contacts-unavailable";
    private static final String TAG_GROUP_VIEW = "contacts-groups";
    public static final String TAG_ASSISTANT = "contacts-assistant";
    public static final String TAG_SECOND_LEVEL = "second-level";
    public static final String TAG_THIRD_LEVEL = "third-level";

    public static final String TAG_DUPLICATES = "DuplicatesFragment";
    public static final String TAG_DUPLICATES_UTIL = "DuplicatesUtilFragment";

    private static final String KEY_GROUP_URI = "groupUri";

    private ContactsIntentResolver mIntentResolver;
    private ContactsRequest mRequest;
    private AccountTypeManager mAccountTypeManager;

    private FloatingActionButtonController mFloatingActionButtonController;
    private View mFloatingActionButtonContainer;
    private boolean wasLastFabAnimationScaleIn = false;

    private ProviderStatusWatcher mProviderStatusWatcher;
    private Integer mProviderStatus;

    private BroadcastReceiver mSaveServiceListener;

    private boolean mShouldSwitchToGroupView;

    private CoordinatorLayout mLayoutRoot;

    /**
     * Showing a list of Contacts. Also used for showing search results in search mode.
     */
    private DefaultContactBrowseListFragment mAllFragment;

    private GroupMembersFragment mMembersFragment;
    private Uri mGroupUri;

    /**
     * True if this activity instance is a re-created one.  i.e. set true after orientation change.
     */
    private boolean mIsRecreatedInstance;

    private boolean mShouldSwitchToAllContacts;

    /** Sequential ID assigned to each instance; used for logging */
    private final int mInstanceId;
    private static final AtomicInteger sNextInstanceId = new AtomicInteger();

    private Object mStatusChangeListenerHandle;

    private final Handler mHandler = new Handler();

    private SyncStatusObserver mSyncStatusObserver = new SyncStatusObserver() {
        public void onStatusChanged(int which) {
            mHandler.post(new Runnable() {
                public void run() {
                    onSyncStateUpdated();
                }
            });
        }
    };

    // Update sync status for accounts in current ContactListFilter
    private void onSyncStateUpdated() {
        if (isAllFragmentInSearchMode() || isAllFragmentInSelectionMode()) {
            return;
        }

        final ContactListFilter filter = mContactListFilterController.getFilter();
        if (filter != null) {
            final SwipeRefreshLayout swipeRefreshLayout = mAllFragment.getSwipeRefreshLayout();
            if (swipeRefreshLayout == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Can not load swipeRefreshLayout, swipeRefreshLayout is null");
                }
                return;
            }

            final List<AccountWithDataSet> accounts;
            if (filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT &&
                    filter.isGoogleAccountType()) {
                accounts = Collections.singletonList(new AccountWithDataSet(filter.accountName,
                        filter.accountType, null));
            } else if (filter.shouldShowSyncState()) {
                accounts = mAccountTypeManager.getWritableGoogleAccounts();
            } else {
                accounts = Collections.emptyList();
            }
            if (SyncUtil.isAnySyncing(accounts)) {
                return;
            }
            swipeRefreshLayout.setRefreshing(false);
        }
    }

    public void showConnectionErrorMsg() {
        Snackbar.make(mLayoutRoot, R.string.connection_error_message, Snackbar.LENGTH_LONG).show();
    }

    private final ContactListFilterListener mFilterListener = new ContactListFilterListener() {
        @Override
        public void onContactListFilterChanged() {
            final ContactListFilter filter = mContactListFilterController.getFilter();
            handleFilterChangeForFragment(filter);
            handleFilterChangeForActivity(filter);
        }
    };

    private final ProviderStatusListener mProviderStatusListener = new ProviderStatusListener() {
        @Override
        public void onProviderStatusChange() {
            reloadGroupsAndFiltersIfNeeded();
            updateViewConfiguration(false);
        }
    };

    public PeopleActivity() {
        mInstanceId = sNextInstanceId.getAndIncrement();
        mIntentResolver = new ContactsIntentResolver(this);
        mProviderStatusWatcher = ProviderStatusWatcher.getInstance(this);
    }

    @Override
    public String toString() {
        // Shown on logcat
        return String.format("%s@%d", getClass().getSimpleName(), mInstanceId);
    }

    private boolean areContactsAvailable() {
        return (mProviderStatus != null) && mProviderStatus.equals(ProviderStatus.STATUS_NORMAL);
    }

    @Override
    protected void onCreate(Bundle savedState) {
        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate start");
        }
        super.onCreate(savedState);
        mAccountTypeManager = AccountTypeManager.getInstance(this);

        if (RequestPermissionsActivity.startPermissionActivity(this)) {
            return;
        }

        if (!processIntent(false)) {
            finish();
            return;
        }

        mContactListFilterController.addListener(mFilterListener);
        mProviderStatusWatcher.addListener(mProviderStatusListener);

        mIsRecreatedInstance = (savedState != null);

        if (mIsRecreatedInstance) {
            mGroupUri = savedState.getParcelable(KEY_GROUP_URI);
        }

        createViewsAndFragments();

        if (Log.isLoggable(Constants.PERFORMANCE_TAG, Log.DEBUG)) {
            Log.d(Constants.PERFORMANCE_TAG, "PeopleActivity.onCreate finish");
        }
        getWindow().setBackgroundDrawable(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        final String action = intent.getAction();
        if (GroupUtil.ACTION_CREATE_GROUP.equals(action)) {
            mGroupUri = intent.getData();
            if (mGroupUri == null) {
                toast(R.string.groupSavedErrorToast);
                return;
            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Received group URI " + mGroupUri);
            switchView(ContactsView.GROUP_VIEW);
            mMembersFragment.toastForSaveAction(action);
            return;
        }

        if (isGroupDeleteAction(action)) {
            popSecondLevel();
            mMembersFragment.toastForSaveAction(action);
            mCurrentView = ContactsView.ALL_CONTACTS;
            showFabWithAnimation(/* showFab */ true);
            return;
        }

        if (isGroupSaveAction(action)) {
            mGroupUri = intent.getData();
            if (mGroupUri == null) {
                popSecondLevel();
                toast(R.string.groupSavedErrorToast);
                return;
            }
            if (Log.isLoggable(TAG, Log.VERBOSE)) Log.v(TAG, "Received group URI " + mGroupUri);
            // ACTION_REMOVE_FROM_GROUP doesn't reload data, so it shouldn't cause b/32223934
            // but it's necessary to use the previous fragment since
            // GroupMembersFragment#mIsEditMode needs to be persisted between remove actions.
            if (GroupUtil.ACTION_REMOVE_FROM_GROUP.equals(action)) {
                switchToOrUpdateGroupView(action);
            } else {
                switchView(ContactsView.GROUP_VIEW);
            }
            mMembersFragment.toastForSaveAction(action);
        }

        setIntent(intent);

        if (!processIntent(true)) {
            finish();
            return;
        }

        mContactListFilterController.checkFilterValidity(false);

        if (!isInSecondLevel()) {
            // Re-initialize ActionBarAdapter because {@link #onNewIntent(Intent)} doesn't invoke
            // {@link Fragment#onActivityCreated(Bundle)} where we initialize ActionBarAdapter
            // initially.
            mAllFragment.setParameters(/* ContactsRequest */ mRequest, /* fromOnNewIntent */ true);
            mAllFragment.initializeActionBarAdapter(null);
        }

        initializeFabVisibility();
        invalidateOptionsMenuIfNeeded();
    }

    private static boolean isGroupDeleteAction(String action) {
        return GroupUtil.ACTION_DELETE_GROUP.equals(action);
    }

    private static boolean isGroupSaveAction(String action) {
        return GroupUtil.ACTION_UPDATE_GROUP.equals(action)
                || GroupUtil.ACTION_ADD_TO_GROUP.equals(action)
                || GroupUtil.ACTION_REMOVE_FROM_GROUP.equals(action);
    }

    private void toast(int resId) {
        if (resId >= 0) {
            Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Resolve the intent and initialize {@link #mRequest}, and launch another activity if redirect
     * is needed.
     *
     * @param forNewIntent set true if it's called from {@link #onNewIntent(Intent)}.
     * @return {@code true} if {@link PeopleActivity} should continue running.  {@code false}
     *         if it shouldn't, in which case the caller should finish() itself and shouldn't do
     *         farther initialization.
     */
    private boolean processIntent(boolean forNewIntent) {
        // Extract relevant information from the intent
        mRequest = mIntentResolver.resolveIntent(getIntent());
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, this + " processIntent: forNewIntent=" + forNewIntent
                    + " intent=" + getIntent() + " request=" + mRequest);
        }
        if (!mRequest.isValid()) {
            setResult(RESULT_CANCELED);
            return false;
        }

        switch (mRequest.getActionCode()) {
            case ContactsRequest.ACTION_VIEW_CONTACT: {
                ImplicitIntentsUtil.startQuickContact(
                        this, mRequest.getContactUri(), ScreenType.UNKNOWN);
                return false;
            }
            case ContactsRequest.ACTION_INSERT_GROUP: {
                onCreateGroupMenuItemClicked();
                return true;
            }
            case ContactsRequest.ACTION_VIEW_GROUP:
            case ContactsRequest.ACTION_EDIT_GROUP: {
                mShouldSwitchToGroupView = true;
                return true;
            }
        }
        return true;
    }

    private void createViewsAndFragments() {
        setContentView(R.layout.people_activity);

        final FragmentManager fragmentManager = getFragmentManager();

        setUpAllFragment(fragmentManager);

        mMembersFragment = (GroupMembersFragment) fragmentManager.findFragmentByTag(TAG_GROUP_VIEW);

        // Configure floating action button
        mFloatingActionButtonContainer = findViewById(R.id.floating_action_button_container);
        final ImageButton floatingActionButton
                = (ImageButton) findViewById(R.id.floating_action_button);
        floatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AccountFilterUtil.startEditorIntent(PeopleActivity.this, getIntent(),
                        mContactListFilterController.getFilter());
            }
        });
        mFloatingActionButtonController = new FloatingActionButtonController(this,
                mFloatingActionButtonContainer, floatingActionButton);

        invalidateOptionsMenuIfNeeded();

        mLayoutRoot = (CoordinatorLayout) findViewById(R.id.root);

        if (mShouldSwitchToGroupView && !mIsRecreatedInstance) {
            mGroupUri = mRequest.getContactUri();
            switchToOrUpdateGroupView(GroupUtil.ACTION_SWITCH_GROUP);
            mShouldSwitchToGroupView = false;
        }
    }

    private void setUpAllFragment(FragmentManager fragmentManager) {
        mAllFragment = (DefaultContactBrowseListFragment)
                fragmentManager.findFragmentByTag(TAG_ALL);

        if (mAllFragment == null) {
            mAllFragment = new DefaultContactBrowseListFragment();
            mAllFragment.setAnimateOnLoad(true);
            fragmentManager.beginTransaction()
                    .add(R.id.contacts_list_container, mAllFragment, TAG_ALL)
                    .commit();
            fragmentManager.executePendingTransactions();
        }

        mAllFragment.setContactsAvailable(areContactsAvailable());
        mAllFragment.setListType(mContactListFilterController.getFilterListType());
        mAllFragment.setParameters(/* ContactsRequest */ mRequest, /* fromOnNewIntent */ false);
    }

    @Override
    protected void onPause() {
        mProviderStatusWatcher.stop();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(mSaveServiceListener);

        super.onPause();

        ContentResolver.removeStatusChangeListener(mStatusChangeListenerHandle);
        onSyncStateUpdated();
    }

    @Override
    public void onMultiWindowModeChanged(boolean entering) {
        initializeHomeVisibility();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mShouldSwitchToAllContacts) {
            switchToAllContacts();
        }

        mProviderStatusWatcher.start();
        updateViewConfiguration(true);

        mStatusChangeListenerHandle = ContentResolver.addStatusChangeListener(
                ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                        | ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                        | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS,
                mSyncStatusObserver);
        onSyncStateUpdated();

        initializeFabVisibility();
        initializeHomeVisibility();

        mSaveServiceListener = new SaveServiceListener();
        LocalBroadcastManager.getInstance(this).registerReceiver(mSaveServiceListener,
                new IntentFilter(ContactSaveService.BROADCAST_GROUP_DELETED));
    }

    @Override
    protected void onDestroy() {
        mProviderStatusWatcher.removeListener(mProviderStatusListener);
        mContactListFilterController.removeListener(mFilterListener);
        super.onDestroy();
    }

    private void initializeFabVisibility() {
        mFloatingActionButtonContainer.setVisibility(shouldHideFab() ? View.GONE : View.VISIBLE);
        mFloatingActionButtonController.resetIn();
        wasLastFabAnimationScaleIn = !shouldHideFab();
    }

    private void initializeHomeVisibility() {
        // Remove the navigation icon if we return to the fragment in a search or select state
        if (getToolbar() != null && (isAllFragmentInSelectionMode()
                || isAllFragmentInSearchMode() || isGroupsFragmentInSelectionMode()
                || isGroupsFragmentInSearchMode())) {
            getToolbar().setNavigationIcon(null);
        }
    }

    private boolean shouldHideFab() {
        if (mAllFragment != null && mAllFragment.getActionBarAdapter() == null
                || isInSecondLevel()) {
            return true;
        }
        return isAllFragmentInSearchMode() || isAllFragmentInSelectionMode();
    }

    public void showFabWithAnimation(boolean showFab) {
        if (mFloatingActionButtonContainer == null) {
            return;
        }
        if (showFab) {
            if (!wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleIn(0);
            }
            wasLastFabAnimationScaleIn = true;

        } else {
            if (wasLastFabAnimationScaleIn) {
                mFloatingActionButtonContainer.setVisibility(View.VISIBLE);
                mFloatingActionButtonController.scaleOut();
            }
            wasLastFabAnimationScaleIn = false;
        }
    }

    private void reloadGroupsAndFiltersIfNeeded() {
        final int providerStatus = mProviderStatusWatcher.getProviderStatus();
        final Menu menu = mNavigationView.getMenu();
        final MenuItem groupsMenuItem = menu.findItem(R.id.nav_groups);
        final SubMenu subMenu = groupsMenuItem.getSubMenu();

        // Reload groups and filters if provider status changes to "normal" and there's no groups
        // loaded or just a "Create new..." menu item is in the menu.
        if (subMenu != null && subMenu.size() <= 1 && providerStatus == ProviderStatus.STATUS_NORMAL
                && !mProviderStatus.equals(providerStatus)) {
            loadGroupsAndFilters();
        }
    }

    private void updateViewConfiguration(boolean forceUpdate) {
        int providerStatus = mProviderStatusWatcher.getProviderStatus();
        if (!forceUpdate && (mProviderStatus != null)
                && (mProviderStatus.equals(providerStatus))) return;
        mProviderStatus = providerStatus;

        final FragmentManager fragmentManager= getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();

        // Change in CP2's provider status may not take effect immediately, see b/30566908.
        // So we need to handle the case where provider status is STATUS_EMPTY and there is
        // actually at least one real account (not "local" account) on device.
        if (shouldShowList()) {
            if (mAllFragment != null) {
                final Fragment unavailableFragment = fragmentManager
                        .findFragmentByTag(TAG_UNAVAILABLE);
                if (unavailableFragment != null) {
                    transaction.remove(unavailableFragment);
                }
                if (mAllFragment.isHidden()) {
                    transaction.show(mAllFragment);
                }
                mAllFragment.setContactsAvailable(areContactsAvailable());
                mAllFragment.setEnabled(true);
            }
        } else {
            // Setting up the page so that the user can still use the app
            // even without an account.
            if (mAllFragment != null) {
                mAllFragment.setEnabled(false);
            }
            final ContactsUnavailableFragment fragment = new ContactsUnavailableFragment();
            transaction.hide(mAllFragment);
            transaction.replace(R.id.contacts_unavailable_container, fragment, TAG_UNAVAILABLE);
            fragment.updateStatus(mProviderStatus);
        }
        if (!transaction.isEmpty()) {
            transaction.commit();
            fragmentManager.executePendingTransactions();
        }

        invalidateOptionsMenuIfNeeded();
    }

    private boolean shouldShowList() {
        return mProviderStatus != null
                && ((mProviderStatus.equals(ProviderStatus.STATUS_EMPTY)
                && mAccountTypeManager.hasNonLocalAccount())
                || mProviderStatus.equals(ProviderStatus.STATUS_NORMAL));
    }

    private void invalidateOptionsMenuIfNeeded() {
        if (mAllFragment != null
                && mAllFragment.getOptionsMenuContactsAvailable() != areContactsAvailable()) {
            invalidateOptionsMenu();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Bring up the search UI if the user starts typing
        final int unicodeChar = event.getUnicodeChar();
        if ((unicodeChar != 0)
                // If COMBINING_ACCENT is set, it's not a unicode character.
                && ((unicodeChar & KeyCharacterMap.COMBINING_ACCENT) == 0)
                && !Character.isWhitespace(unicodeChar)) {
            if (mAllFragment.onKeyDown(unicodeChar)) {
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (!isSafeToCommitTransactions()) {
            return;
        }

        // Handle the back event in drawer first.
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
            return;
        }

        // Handle the back event in "second level".
        if (isGroupView()) {
            onBackPressedGroupView();
            return;
        }

        if (isAssistantView()) {
            onBackPressedAssistantView();
            return;
        }

        // If feature highlight is present, let it handle the back event before mAllFragment.
        if (FeatureHighlightHelper.tryRemoveHighlight(this)) {
            return;
        }

        // Handle the back event in "first level" - mAllFragment.
        if (maybeHandleInAllFragment()) {
            return;
        }

        super.onBackPressed();
    }

    private void onBackPressedGroupView() {
        if (mMembersFragment.isEditMode()) {
            mMembersFragment.exitEditMode();
        } else if (mMembersFragment.getActionBarAdapter().isSelectionMode()) {
            mMembersFragment.getActionBarAdapter().setSelectionMode(false);
            mMembersFragment.displayCheckBoxes(false);
        } else if (mMembersFragment.getActionBarAdapter().isSearchMode()) {
            mMembersFragment.getActionBarAdapter().setSearchMode(false);
        } else {
            switchToAllContacts();
        }
    }

    private void onBackPressedAssistantView() {
        if (!popThirdLevel()) {
            switchToAllContacts();
        } else {
            setDrawerLockMode(/* enabled */ true);
        }
    }

    // Returns true if back event is handled in this method.
    private boolean maybeHandleInAllFragment() {
        if (isAllFragmentInSelectionMode()) {
            mAllFragment.getActionBarAdapter().setSelectionMode(false);
            return true;
        }

        if (isAllFragmentInSearchMode()) {
            mAllFragment.getActionBarAdapter().setSearchMode(false);
            if (mAllFragment.wasSearchResultClicked()) {
                mAllFragment.resetSearchResultClicked();
            } else {
                Logger.logScreenView(this, ScreenType.SEARCH_EXIT);
                Logger.logSearchEvent(mAllFragment.createSearchState());
            }
            return true;
        }

        if (!AccountFilterUtil.isAllContactsFilter(mContactListFilterController.getFilter())
                && !mAllFragment.isHidden()) {
            // If mAllFragment is hidden, then mContactsUnavailableFragment is visible so we
            // don't need to switch to all contacts.
            switchToAllContacts();
            return true;
        }

        return false;
    }

    private boolean isAllFragmentInSelectionMode() {
        return mAllFragment != null && mAllFragment.getActionBarAdapter() != null
                && mAllFragment.getActionBarAdapter().isSelectionMode();
    }

    private boolean isAllFragmentInSearchMode() {
        return mAllFragment != null && mAllFragment.getActionBarAdapter() != null
                && mAllFragment.getActionBarAdapter().isSearchMode();
    }

    private boolean isGroupsFragmentInSelectionMode() {
        return mMembersFragment != null && mMembersFragment.getActionBarAdapter() != null
                && mMembersFragment.getActionBarAdapter().isSelectionMode();
    }

    private boolean isGroupsFragmentInSearchMode() {
        return mMembersFragment != null && mMembersFragment.getActionBarAdapter() != null
                && mMembersFragment.getActionBarAdapter().isSearchMode();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_GROUP_URI, mGroupUri);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mGroupUri = savedInstanceState.getParcelable(KEY_GROUP_URI);
    }

    private void onGroupDeleted(final Intent intent) {
        if (!ContactSaveService.canUndo(intent)) return;

        final AccessibilityManager am =
                (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        //TODO set to INDEFINITE and track user interaction to dismiss b/33208886
        final int accessibilityLength = 15000;
        final int length = am.isEnabled() ? accessibilityLength : Snackbar.LENGTH_LONG;
        final String message = getString(R.string.groupDeletedToast);

        final Snackbar snackbar = Snackbar.make(mLayoutRoot, message, length)
                .setAction(R.string.undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ContactSaveService.startService(PeopleActivity.this,
                                ContactSaveService.createUndoIntent(PeopleActivity.this, intent));
                    }
                }).setActionTextColor(ContextCompat.getColor(this, R.color.snackbar_action_text));

        // Announce for a11y talkback
        mLayoutRoot.announceForAccessibility(message);
        mLayoutRoot.announceForAccessibility(getString(R.string.undo));

        snackbar.show();
    }

    private class SaveServiceListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case ContactSaveService.BROADCAST_GROUP_DELETED:
                    onGroupDeleted(intent);
                    break;
            }
        }
    }

    @Override
    protected void onGroupMenuItemClicked(long groupId, String title) {
        if (isGroupView() && mMembersFragment != null
                && mMembersFragment.isCurrentGroup(groupId)) {
            return;
        }
        mGroupUri = ContentUris.withAppendedId(ContactsContract.Groups.CONTENT_URI, groupId);
        switchToOrUpdateGroupView(GroupUtil.ACTION_SWITCH_GROUP);
    }

    @Override
    protected void onFilterMenuItemClicked(Intent intent) {
        // We must pop second level first to "restart" mAllFragment, before changing filter.
        if (isInSecondLevel()) {
            popSecondLevel();
            showFabWithAnimation(/* showFab */ true);
            // HACK: swap the current filter to force listeners to update because the group
            // member view no longer changes the filter. Fix for b/32223767
            final ContactListFilter current = mContactListFilterController.getFilter();
            mContactListFilterController.setContactListFilter(
                    AccountFilterUtil.createContactsFilter(this), false);
            mContactListFilterController.setContactListFilter(current, false);
        }
        mCurrentView = ContactsView.ACCOUNT_VIEW;
        super.onFilterMenuItemClicked(intent);
    }

    private void switchToOrUpdateGroupView(String action) {
        // If group fragment is active and visible, we simply update it.
        if (mMembersFragment != null && !mMembersFragment.isInactive()) {
            mMembersFragment.updateExistingGroupFragment(mGroupUri, action);
        } else {
            switchView(ContactsView.GROUP_VIEW);
        }
    }

    @Override
    protected void launchAssistant() {
        switchView(ContactsView.ASSISTANT);
    }

    private void switchView(ContactsView contactsView) {
        mCurrentView = contactsView;

        final FragmentManager fragmentManager =  getFragmentManager();
        final FragmentTransaction transaction = fragmentManager.beginTransaction();
        if (isGroupView()) {
            mMembersFragment = GroupMembersFragment.newInstance(mGroupUri);
            transaction.replace(
                    R.id.contacts_list_container, mMembersFragment, TAG_GROUP_VIEW);
        } else if (isAssistantView()) {
            Fragment uiFragment = fragmentManager.findFragmentByTag(TAG_ASSISTANT);
            Fragment unavailableFragment = fragmentManager.findFragmentByTag(TAG_UNAVAILABLE);
            if (uiFragment == null) {
                uiFragment = ObjectFactory.getAssistantFragment();
            }
            if (unavailableFragment != null) {
                transaction.remove(unavailableFragment);
            }
            transaction.replace(R.id.contacts_list_container, uiFragment, TAG_ASSISTANT);
            resetToolBarStatusBarColor();
        }
        transaction.addToBackStack(TAG_SECOND_LEVEL);
        transaction.commit();
        fragmentManager.executePendingTransactions();

        showFabWithAnimation(/* showFab */ false);
    }

    @Override
    public void switchToAllContacts() {
        if (isInSecondLevel()) {
            popSecondLevel();
        }
        mShouldSwitchToAllContacts = false;
        mCurrentView = ContactsView.ALL_CONTACTS;
        showFabWithAnimation(/* showFab */ true);
        mAllFragment.scrollToTop();

        super.switchToAllContacts();
    }

    private boolean popThirdLevel() {
        return getFragmentManager().popBackStackImmediate(
                TAG_THIRD_LEVEL, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void popSecondLevel() {
        getFragmentManager().popBackStackImmediate(
                TAG_SECOND_LEVEL, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        mMembersFragment = null;
        resetToolBarStatusBarColor();
    }

    // Reset toolbar and status bar color to Contacts theme color.
    private void resetToolBarStatusBarColor() {
        findViewById(R.id.toolbar_frame).setBackgroundColor(
                ContextCompat.getColor(this, R.color.primary_color));
        updateStatusBarBackground(ContextCompat.getColor(this, R.color.primary_color_dark));
    }

    @Override
    protected DefaultContactBrowseListFragment getAllFragment() {
        return mAllFragment;
    }

    @Override
    protected GroupMembersFragment getGroupFragment() {
        return mMembersFragment;
    }

    @Override
    protected GroupMetaData getGroupMetaData() {
        return mMembersFragment == null ? null : mMembersFragment.getGroupMetaData();
    }

    private void handleFilterChangeForFragment(ContactListFilter filter) {
        if (mAllFragment.canSetActionBar()) {
            mAllFragment.setFilterAndUpdateTitle(filter);
            // Scroll to top after filter is changed.
            mAllFragment.scrollToTop();
        }
    }

    private void handleFilterChangeForActivity(ContactListFilter filter) {
        // The filter was changed while this activity was in the background. If we're in the
        // assistant view Switch to the main contacts list when we resume to prevent
        // b/31838582 and b/31829161
        // TODO: this is a hack; we need to do some cleanup of the contact list filter stuff
        if (isAssistantView() && filter.isContactsFilterType()) {
            mShouldSwitchToAllContacts = true;
        }

        // Check menu in navigation drawer.
        updateFilterMenu(filter);

        if (CompatUtils.isNCompatible()) {
            getWindow().getDecorView()
                    .sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        }
        invalidateOptionsMenu();
    }
}