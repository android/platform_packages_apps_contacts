/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager.LoaderCallbacks;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.GoogleAccountType;
import com.android.contacts.util.EmptyService;
import com.android.contacts.util.LocalizedNameResolver;
import com.android.contacts.util.WeakAsyncTask;
import com.android.contacts.util.concurrent.ContactsExecutors;
import com.android.contacts.util.concurrent.ListenableFutureLoader;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Shows a list of all available {@link Groups} available, letting the user
 * select which ones they want to be visible.
 */
public class CustomContactListFilterActivity extends Activity implements
        ExpandableListView.OnChildClickListener,
        LoaderCallbacks<CustomContactListFilterActivity.AccountSet> {
    private static final String TAG = "CustomContactListFilter";

    public static final String EXTRA_CURRENT_LIST_FILTER_TYPE = "currentListFilterType";

    private static final int ACCOUNT_SET_LOADER_ID = 1;

    private ExpandableListView mList;
    private DisplayAdapter mAdapter;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.contact_list_filter_custom);

        mList = (ExpandableListView) findViewById(android.R.id.list);
        mList.setOnChildClickListener(this);
        mList.setHeaderDividersEnabled(true);
        mList.setChildDivider(new ColorDrawable(Color.TRANSPARENT));

        mList.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(final View v, final int left, final int top, final int right,
                    final int bottom, final int oldLeft, final int oldTop, final int oldRight,
                    final int oldBottom) {
                mList.setIndicatorBounds(
                        mList.getWidth() - getResources().getDimensionPixelSize(
                                R.dimen.contact_filter_indicator_padding_end),
                        mList.getWidth() - getResources().getDimensionPixelSize(
                                R.dimen.contact_filter_indicator_padding_start));
            }
        });

        mAdapter = new DisplayAdapter(this);

        mList.setOnCreateContextMenuListener(this);

        mList.setAdapter(mAdapter);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class CustomFilterConfigurationLoader extends ListenableFutureLoader<AccountSet> {

        private AccountTypeManager mAccountTypeManager;

        public CustomFilterConfigurationLoader(Context context) {
            super(context, new IntentFilter(AccountTypeManager.BROADCAST_ACCOUNTS_CHANGED));
            mAccountTypeManager = AccountTypeManager.getInstance(context);
        }

        @Override
        public ListenableFuture<AccountSet> loadData() {
            return Futures.transform(mAccountTypeManager.getAccountsAsync(),
                    new Function<List<AccountInfo>, AccountSet>() {
                @Nullable
                @Override
                public AccountSet apply(@Nullable List<AccountInfo> input) {
                    return createAccountSet(input);
                }
            }, ContactsExecutors.getDefaultThreadPoolExecutor());
        }

        private AccountSet createAccountSet(List<AccountInfo> sourceAccounts) {
            final Context context = getContext();
            final ContentResolver resolver = context.getContentResolver();

            final AccountSet accounts = new AccountSet();

            // Don't include the null account because it doesn't support writing to
            // ContactsContract.Settings
            for (AccountInfo info : sourceAccounts) {
                final AccountWithDataSet account = info.getAccount();
                // Don't include the null account because it doesn't support writing to
                // ContactsContract.Settings
                if (account.isNullAccount()) {
                    continue;
                }

                final AccountDisplay accountDisplay = new AccountDisplay(resolver, info);

                final Uri.Builder groupsUri = Groups.CONTENT_URI.buildUpon()
                        .appendQueryParameter(Groups.ACCOUNT_NAME, account.name)
                        .appendQueryParameter(Groups.ACCOUNT_TYPE, account.type);
                if (account.dataSet != null) {
                    groupsUri.appendQueryParameter(Groups.DATA_SET, account.dataSet).build();
                }
                final Cursor cursor = resolver.query(groupsUri.build(), null,
                        Groups.DELETED + "=0", null, null);
                if (cursor == null) {
                    continue;
                }
                android.content.EntityIterator iterator =
                        ContactsContract.Groups.newEntityIterator(cursor);
                try {
                    boolean hasGroups = false;

                    // Create entries for each known group
                    while (iterator.hasNext()) {
                        final ContentValues values = iterator.next().getEntityValues();
                        final GroupDelta group = GroupDelta.fromBefore(values);
                        accountDisplay.addGroup(group);
                        hasGroups = true;
                    }
                    // Create single entry handling ungrouped status
                    accountDisplay.mUngrouped =
                        GroupDelta.fromSettings(resolver, account.name, account.type,
                                account.dataSet, hasGroups);
                    accountDisplay.addGroup(accountDisplay.mUngrouped);
                } finally {
                    iterator.close();
                }

                accounts.add(accountDisplay);
            }

            return accounts;
        }
    }

    @Override
    protected void onStart() {
        getLoaderManager().initLoader(ACCOUNT_SET_LOADER_ID, null, this);
        super.onStart();
    }

    @Override
    public Loader<AccountSet> onCreateLoader(int id, Bundle args) {
        return new CustomFilterConfigurationLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<AccountSet> loader, AccountSet data) {
        mAdapter.setAccounts(data);
    }

    @Override
    public void onLoaderReset(Loader<AccountSet> loader) {
        mAdapter.setAccounts(null);
    }

    private static final int DEFAULT_SHOULD_SYNC = 1;
    private static final int DEFAULT_VISIBLE = 0;

    /**
     * Entry holding any changes to {@link Groups} or {@link Settings} rows,
     * such as {@link Groups#SHOULD_SYNC} or {@link Groups#GROUP_VISIBLE}.
     */
    protected static class GroupDelta extends ValuesDelta {
        private boolean mUngrouped = false;
        private boolean mAccountHasGroups;

        private GroupDelta() {
            super();
        }

        /**
         * Build {@link GroupDelta} from the {@link Settings} row for the given
         * {@link Settings#ACCOUNT_NAME}, {@link Settings#ACCOUNT_TYPE}, and
         * {@link Settings#DATA_SET}.
         */
        public static GroupDelta fromSettings(ContentResolver resolver, String accountName,
                String accountType, String dataSet, boolean accountHasGroups) {
            final Uri.Builder settingsUri = Settings.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Settings.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(Settings.ACCOUNT_TYPE, accountType);
            if (dataSet != null) {
                settingsUri.appendQueryParameter(Settings.DATA_SET, dataSet);
            }
            final Cursor cursor = resolver.query(settingsUri.build(), new String[] {
                    Settings.SHOULD_SYNC, Settings.UNGROUPED_VISIBLE
            }, null, null, null);

            try {
                final ContentValues values = new ContentValues();
                values.put(Settings.ACCOUNT_NAME, accountName);
                values.put(Settings.ACCOUNT_TYPE, accountType);
                values.put(Settings.DATA_SET, dataSet);

                if (cursor != null && cursor.moveToFirst()) {
                    // Read existing values when present
                    values.put(Settings.SHOULD_SYNC, cursor.getInt(0));
                    values.put(Settings.UNGROUPED_VISIBLE, cursor.getInt(1));
                    return fromBefore(values).setUngrouped(accountHasGroups);
                } else {
                    // Nothing found, so treat as create
                    values.put(Settings.SHOULD_SYNC, DEFAULT_SHOULD_SYNC);
                    values.put(Settings.UNGROUPED_VISIBLE, DEFAULT_VISIBLE);
                    return fromAfter(values).setUngrouped(accountHasGroups);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        public static GroupDelta fromBefore(ContentValues before) {
            final GroupDelta entry = new GroupDelta();
            entry.mBefore = before;
            entry.mAfter = new ContentValues();
            return entry;
        }

        public static GroupDelta fromAfter(ContentValues after) {
            final GroupDelta entry = new GroupDelta();
            entry.mBefore = null;
            entry.mAfter = after;
            return entry;
        }

        protected GroupDelta setUngrouped(boolean accountHasGroups) {
            mUngrouped = true;
            mAccountHasGroups = accountHasGroups;
            return this;
        }

        @Override
        public boolean beforeExists() {
            return mBefore != null;
        }

        public boolean getShouldSync() {
            return getAsInteger(mUngrouped ? Settings.SHOULD_SYNC : Groups.SHOULD_SYNC,
                    DEFAULT_SHOULD_SYNC) != 0;
        }

        public boolean getVisible() {
            return getAsInteger(mUngrouped ? Settings.UNGROUPED_VISIBLE : Groups.GROUP_VISIBLE,
                    DEFAULT_VISIBLE) != 0;
        }

        public void putShouldSync(boolean shouldSync) {
            put(mUngrouped ? Settings.SHOULD_SYNC : Groups.SHOULD_SYNC, shouldSync ? 1 : 0);
        }

        public void putVisible(boolean visible) {
            put(mUngrouped ? Settings.UNGROUPED_VISIBLE : Groups.GROUP_VISIBLE, visible ? 1 : 0);
        }

        private String getAccountType() {
            return (mBefore == null ? mAfter : mBefore).getAsString(Settings.ACCOUNT_TYPE);
        }

        public CharSequence getTitle(Context context) {
            if (mUngrouped) {
                final String customAllContactsName =
                        LocalizedNameResolver.getAllContactsName(context, getAccountType());
                if (customAllContactsName != null) {
                    return customAllContactsName;
                }
                if (mAccountHasGroups) {
                    return context.getText(R.string.display_ungrouped);
                } else {
                    return context.getText(R.string.display_all_contacts);
                }
            } else {
                final Integer titleRes = getAsInteger(Groups.TITLE_RES);
                if (titleRes != null && titleRes != 0) {
                    final String packageName = getAsString(Groups.RES_PACKAGE);
                    if (packageName != null) {
                        return context.getPackageManager().getText(packageName, titleRes, null);
                    }
                }
                return getAsString(Groups.TITLE);
            }
        }

        /**
         * Build a possible {@link ContentProviderOperation} to persist any
         * changes to the {@link Groups} or {@link Settings} row described by
         * this {@link GroupDelta}.
         */
        public ContentProviderOperation buildDiff() {
            if (isInsert()) {
                // Only allow inserts for Settings
                if (mUngrouped) {
                    mAfter.remove(mIdColumn);
                    return ContentProviderOperation.newInsert(Settings.CONTENT_URI)
                            .withValues(mAfter)
                            .build();
                }
                else {
                    throw new IllegalStateException("Unexpected diff");
                }
            } else if (isUpdate()) {
                if (mUngrouped) {
                    String accountName = this.getAsString(Settings.ACCOUNT_NAME);
                    String accountType = this.getAsString(Settings.ACCOUNT_TYPE);
                    String dataSet = this.getAsString(Settings.DATA_SET);
                    StringBuilder selection = new StringBuilder(Settings.ACCOUNT_NAME + "=? AND "
                            + Settings.ACCOUNT_TYPE + "=?");
                    String[] selectionArgs;
                    if (dataSet == null) {
                        selection.append(" AND " + Settings.DATA_SET + " IS NULL");
                        selectionArgs = new String[] {accountName, accountType};
                    } else {
                        selection.append(" AND " + Settings.DATA_SET + "=?");
                        selectionArgs = new String[] {accountName, accountType, dataSet};
                    }
                    return ContentProviderOperation.newUpdate(Settings.CONTENT_URI)
                            .withSelection(selection.toString(), selectionArgs)
                            .withValues(mAfter)
                            .build();
                } else {
                    return ContentProviderOperation.newUpdate(
                                    addCallerIsSyncAdapterParameter(Groups.CONTENT_URI))
                            .withSelection(Groups._ID + "=" + this.getId(), null)
                            .withValues(mAfter)
                            .build();
                }
            } else {
                return null;
            }
        }
    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri) {
        return uri.buildUpon()
            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
            .build();
    }

    /**
     * {@link Comparator} to sort by {@link Groups#_ID}.
     */
    private static Comparator<GroupDelta> sIdComparator = new Comparator<GroupDelta>() {
        public int compare(GroupDelta object1, GroupDelta object2) {
            final Long id1 = object1.getId();
            final Long id2 = object2.getId();
            if (id1 == null && id2 == null) {
                return 0;
            } else if (id1 == null) {
                return -1;
            } else if (id2 == null) {
                return 1;
            } else if (id1 < id2) {
                return -1;
            } else if (id1 > id2) {
                return 1;
            } else {
                return 0;
            }
        }
    };

    /**
     * Set of all {@link AccountDisplay} entries, one for each source.
     */
    protected static class AccountSet extends ArrayList<AccountDisplay> {
        public ArrayList<ContentProviderOperation> buildDiff() {
            final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();
            for (AccountDisplay account : this) {
                account.buildDiff(diff);
            }
            return diff;
        }
    }

    /**
     * {@link GroupDelta} details for a single {@link AccountWithDataSet}, usually shown as
     * children under a single expandable group.
     */
    protected static class AccountDisplay {
        public final String mName;
        public final String mType;
        public final String mDataSet;
        public final AccountInfo mAccountInfo;

        public GroupDelta mUngrouped;
        public ArrayList<GroupDelta> mSyncedGroups = Lists.newArrayList();
        public ArrayList<GroupDelta> mUnsyncedGroups = Lists.newArrayList();

        public GroupDelta getGroup(int position) {
            if (position < mSyncedGroups.size()) {
                return mSyncedGroups.get(position);
            }
            position -= mSyncedGroups.size();
            return mUnsyncedGroups.get(position);
        }

        /**
         * Build an {@link AccountDisplay} covering all {@link Groups} under the
         * given {@link AccountWithDataSet}.
         */
        public AccountDisplay(ContentResolver resolver, AccountInfo accountInfo) {
            mName = accountInfo.getAccount().name;
            mType = accountInfo.getAccount().type;
            mDataSet = accountInfo.getAccount().dataSet;
            mAccountInfo = accountInfo;
        }

        /**
         * Add the given {@link GroupDelta} internally, filing based on its
         * {@link GroupDelta#getShouldSync()} status.
         */
        private void addGroup(GroupDelta group) {
            if (group.getShouldSync()) {
                mSyncedGroups.add(group);
            } else {
                mUnsyncedGroups.add(group);
            }
        }

        /**
         * Set the {@link GroupDelta#putShouldSync(boolean)} value for all
         * children {@link GroupDelta} rows.
         */
        public void setShouldSync(boolean shouldSync) {
            final Iterator<GroupDelta> oppositeChildren = shouldSync ?
                    mUnsyncedGroups.iterator() : mSyncedGroups.iterator();
            while (oppositeChildren.hasNext()) {
                final GroupDelta child = oppositeChildren.next();
                setShouldSync(child, shouldSync, false);
                oppositeChildren.remove();
            }
        }

        public void setShouldSync(GroupDelta child, boolean shouldSync) {
            setShouldSync(child, shouldSync, true);
        }

        /**
         * Set {@link GroupDelta#putShouldSync(boolean)}, and file internally
         * based on updated state.
         */
        public void setShouldSync(GroupDelta child, boolean shouldSync, boolean attemptRemove) {
            child.putShouldSync(shouldSync);
            if (shouldSync) {
                if (attemptRemove) {
                    mUnsyncedGroups.remove(child);
                }
                mSyncedGroups.add(child);
                Collections.sort(mSyncedGroups, sIdComparator);
            } else {
                if (attemptRemove) {
                    mSyncedGroups.remove(child);
                }
                mUnsyncedGroups.add(child);
            }
        }

        /**
         * Build set of {@link ContentProviderOperation} to persist any user
         * changes to {@link GroupDelta} rows under this {@link AccountWithDataSet}.
         */
        public void buildDiff(ArrayList<ContentProviderOperation> diff) {
            for (GroupDelta group : mSyncedGroups) {
                final ContentProviderOperation oper = group.buildDiff();
                if (oper != null) diff.add(oper);
            }
            for (GroupDelta group : mUnsyncedGroups) {
                final ContentProviderOperation oper = group.buildDiff();
                if (oper != null) diff.add(oper);
            }
        }
    }

    /**
     * {@link ExpandableListAdapter} that shows {@link GroupDelta} settings,
     * grouped by {@link AccountWithDataSet} type. Shows footer row when any groups are
     * unsynced, as determined through {@link AccountDisplay#mUnsyncedGroups}.
     */
    protected static class DisplayAdapter extends BaseExpandableListAdapter {
        private Context mContext;
        private LayoutInflater mInflater;
        private AccountTypeManager mAccountTypes;
        private AccountSet mAccounts;

        private boolean mChildWithPhones = false;

        public DisplayAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mAccountTypes = AccountTypeManager.getInstance(context);
        }

        public void setAccounts(AccountSet accounts) {
            mAccounts = accounts;
            notifyDataSetChanged();
        }

        /**
         * In group descriptions, show the number of contacts with phone
         * numbers, in addition to the total contacts.
         */
        public void setChildDescripWithPhones(boolean withPhones) {
            mChildWithPhones = withPhones;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(
                        R.layout.custom_contact_list_filter_account, parent, false);
            }

            final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

            final AccountDisplay account = (AccountDisplay)this.getGroup(groupPosition);

            text1.setText(account.mAccountInfo.getNameLabel());
            text1.setVisibility(!account.mAccountInfo.isDeviceAccount()
                    || account.mAccountInfo.hasDistinctName()
                    ? View.VISIBLE : View.GONE);
            text2.setText(account.mAccountInfo.getTypeLabel());

            final int textColor = mContext.getResources().getColor(isExpanded
                    ? R.color.dialtacts_theme_color
                    : R.color.account_filter_text_color);
            text1.setTextColor(textColor);
            text2.setTextColor(textColor);

            return convertView;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(
                        R.layout.custom_contact_list_filter_group, parent, false);
            }

            final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);
            final CheckBox checkbox = (CheckBox)convertView.findViewById(android.R.id.checkbox);

            final AccountDisplay account = mAccounts.get(groupPosition);
            final GroupDelta child = (GroupDelta)this.getChild(groupPosition, childPosition);
            if (child != null) {
                // Handle normal group, with title and checkbox
                final boolean groupVisible = child.getVisible();
                checkbox.setVisibility(View.VISIBLE);
                checkbox.setChecked(groupVisible);

                final CharSequence groupTitle = child.getTitle(mContext);
                text1.setText(groupTitle);
                text2.setVisibility(View.GONE);
            } else {
                // When unknown child, this is "more" footer view
                checkbox.setVisibility(View.GONE);
                text1.setText(R.string.display_more_groups);
                text2.setVisibility(View.GONE);
            }

            // Show divider at bottom only for the last child.
            final View dividerBottom = convertView.findViewById(R.id.adapter_divider_bottom);
            dividerBottom.setVisibility(isLastChild ? View.VISIBLE : View.GONE);

            return convertView;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition) {
            final AccountDisplay account = mAccounts.get(groupPosition);
            final boolean validChild = childPosition >= 0
                    && childPosition < account.mSyncedGroups.size()
                    + account.mUnsyncedGroups.size();
            if (validChild) {
                return account.getGroup(childPosition);
            } else {
                return null;
            }
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            final GroupDelta child = (GroupDelta)getChild(groupPosition, childPosition);
            if (child != null) {
                final Long childId = child.getId();
                return childId != null ? childId : Long.MIN_VALUE;
            } else {
                return Long.MIN_VALUE;
            }
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            // Count is any synced groups, plus possible footer
            final AccountDisplay account = mAccounts.get(groupPosition);
            return account.mSyncedGroups.size() + account.mUnsyncedGroups.size();
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mAccounts.get(groupPosition);
        }

        @Override
        public int getGroupCount() {
            if (mAccounts == null) {
                return 0;
            }
            return mAccounts.size();
        }

        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    /**
     * Handle any clicks on {@link ExpandableListAdapter} children, which
     * usually mean toggling its visible state.
     */
    @Override
    public boolean onChildClick(ExpandableListView parent, View view, int groupPosition,
            int childPosition, long id) {
        final CheckBox checkbox = (CheckBox)view.findViewById(android.R.id.checkbox);

        final AccountDisplay account = (AccountDisplay)mAdapter.getGroup(groupPosition);
        final GroupDelta child = (GroupDelta)mAdapter.getChild(groupPosition, childPosition);
        if (child != null) {
            checkbox.toggle();
            child.putVisible(checkbox.isChecked());
        } else {
            // Open context menu for bringing back unsynced
            this.openContextMenu(view);
        }
        return true;
    }

    // TODO: move these definitions to framework constants when we begin
    // defining this mode through <sync-adapter> tags
    private static final int SYNC_MODE_UNSUPPORTED = 0;
    private static final int SYNC_MODE_UNGROUPED = 1;
    private static final int SYNC_MODE_EVERYTHING = 2;

    protected int getSyncMode(AccountDisplay account) {
        // TODO: read sync mode through <sync-adapter> definition
        if (GoogleAccountType.ACCOUNT_TYPE.equals(account.mType) && account.mDataSet == null) {
            return SYNC_MODE_EVERYTHING;
        } else {
            return SYNC_MODE_UNSUPPORTED;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        // Bail if not working with expandable long-press, or if not child
        if (!(menuInfo instanceof ExpandableListContextMenuInfo)) return;

        final ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
        final int groupPosition = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        final int childPosition = ExpandableListView.getPackedPositionChild(info.packedPosition);

        // Skip long-press on expandable parents
        if (childPosition == -1) return;

        final AccountDisplay account = (AccountDisplay)mAdapter.getGroup(groupPosition);
        final GroupDelta child = (GroupDelta)mAdapter.getChild(groupPosition, childPosition);

        // Ignore when selective syncing unsupported
        final int syncMode = getSyncMode(account);
        if (syncMode == SYNC_MODE_UNSUPPORTED) return;

        if (child != null) {
            showRemoveSync(menu, account, child, syncMode);
        } else {
            showAddSync(menu, account, syncMode);
        }
    }

    protected void showRemoveSync(ContextMenu menu, final AccountDisplay account,
            final GroupDelta child, final int syncMode) {
        final CharSequence title = child.getTitle(this);

        menu.setHeaderTitle(title);
        menu.add(R.string.menu_sync_remove).setOnMenuItemClickListener(
                new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        handleRemoveSync(account, child, syncMode, title);
                        return true;
                    }
                });
    }

    protected void handleRemoveSync(final AccountDisplay account, final GroupDelta child,
            final int syncMode, CharSequence title) {
        final boolean shouldSyncUngrouped = account.mUngrouped.getShouldSync();
        if (syncMode == SYNC_MODE_EVERYTHING && shouldSyncUngrouped
                && !child.equals(account.mUngrouped)) {
            // Warn before removing this group when it would cause ungrouped to stop syncing
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final CharSequence removeMessage = this.getString(
                    R.string.display_warn_remove_ungrouped, title);
            builder.setTitle(R.string.menu_sync_remove);
            builder.setMessage(removeMessage);
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    // Mark both this group and ungrouped to stop syncing
                    account.setShouldSync(account.mUngrouped, false);
                    account.setShouldSync(child, false);
                    mAdapter.notifyDataSetChanged();
                }
            });
            builder.show();
        } else {
            // Mark this group to not sync
            account.setShouldSync(child, false);
            mAdapter.notifyDataSetChanged();
        }
    }

    protected void showAddSync(ContextMenu menu, final AccountDisplay account, final int syncMode) {
        menu.setHeaderTitle(R.string.dialog_sync_add);

        // Create item for each available, unsynced group
        for (final GroupDelta child : account.mUnsyncedGroups) {
            if (!child.getShouldSync()) {
                final CharSequence title = child.getTitle(this);
                menu.add(title).setOnMenuItemClickListener(new OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        // Adding specific group for syncing
                        if (child.mUngrouped && syncMode == SYNC_MODE_EVERYTHING) {
                            account.setShouldSync(true);
                        } else {
                            account.setShouldSync(child, true);
                        }
                        mAdapter.notifyDataSetChanged();
                        return true;
                    }
                });
            }
        }
    }

    private boolean hasUnsavedChanges() {
        if (mAdapter == null || mAdapter.mAccounts == null) {
            return false;
        }
        if (getCurrentListFilterType() != ContactListFilter.FILTER_TYPE_CUSTOM) {
            return true;
        }
        final ArrayList<ContentProviderOperation> diff = mAdapter.mAccounts.buildDiff();
        if (diff.isEmpty()) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void doSaveAction() {
        if (mAdapter == null || mAdapter.mAccounts == null) {
            finish();
            return;
        }

        setResult(RESULT_OK);

        final ArrayList<ContentProviderOperation> diff = mAdapter.mAccounts.buildDiff();
        if (diff.isEmpty()) {
            finish();
            return;
        }

        new UpdateTask(this).execute(diff);
    }

    /**
     * Background task that persists changes to {@link Groups#GROUP_VISIBLE},
     * showing spinner dialog to user while updating.
     */
    public static class UpdateTask extends
            WeakAsyncTask<ArrayList<ContentProviderOperation>, Void, Void, Activity> {
        private ProgressDialog mProgress;

        public UpdateTask(Activity target) {
            super(target);
        }

        /** {@inheritDoc} */
        @Override
        protected void onPreExecute(Activity target) {
            final Context context = target;

            mProgress = ProgressDialog.show(
                    context, null, context.getText(R.string.savingDisplayGroups));

            // Before starting this task, start an empty service to protect our
            // process from being reclaimed by the system.
            context.startService(new Intent(context, EmptyService.class));
        }

        /** {@inheritDoc} */
        @Override
        protected Void doInBackground(
                Activity target, ArrayList<ContentProviderOperation>... params) {
            final Context context = target;
            final ContentValues values = new ContentValues();
            final ContentResolver resolver = context.getContentResolver();

            try {
                final ArrayList<ContentProviderOperation> diff = params[0];
                resolver.applyBatch(ContactsContract.AUTHORITY, diff);
            } catch (RemoteException e) {
                Log.e(TAG, "Problem saving display groups", e);
            } catch (OperationApplicationException e) {
                Log.e(TAG, "Problem saving display groups", e);
            }

            return null;
        }

        /** {@inheritDoc} */
        @Override
        protected void onPostExecute(Activity target, Void result) {
            final Context context = target;

            try {
                mProgress.dismiss();
            } catch (Exception e) {
                Log.e(TAG, "Error dismissing progress dialog", e);
            }

            target.finish();

            // Stop the service that was protecting us
            context.stopService(new Intent(context, EmptyService.class));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        final MenuItem menuItem = menu.add(Menu.NONE, R.id.menu_save, Menu.NONE,
                R.string.menu_custom_filter_save);
        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            confirmFinish();
            return true;
        } else if (id == R.id.menu_save) {
            this.doSaveAction();
            return true;
        } else {
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        confirmFinish();
    }

    private void confirmFinish() {
        // Prompt the user whether they want to discard there customizations unless
        // nothing will be changed.
        if (hasUnsavedChanges()) {
            new ConfirmNavigationDialogFragment().show(getFragmentManager(),
                    "ConfirmNavigationDialog");
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private int getCurrentListFilterType() {
        return getIntent().getIntExtra(EXTRA_CURRENT_LIST_FILTER_TYPE,
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
    }

    public static class ConfirmNavigationDialogFragment
            extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity(), getTheme())
                    .setMessage(R.string.leave_customize_confirmation_dialog_message)
                    .setNegativeButton(android.R.string.no, null)
                    .setPositiveButton(android.R.string.yes, this)
                    .create();
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            if (i == DialogInterface.BUTTON_POSITIVE) {
                getActivity().setResult(RESULT_CANCELED);
                getActivity().finish();
            }
        }
    }
}
