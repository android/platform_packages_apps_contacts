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

package com.android.contacts.ui;

import com.android.contacts.ContactsSearchManager;
import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.GoogleSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.util.EmptyService;
import com.android.contacts.util.WeakAsyncTask;
import com.google.android.collect.Lists;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ExpandableListActivity;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.ContentProviderOperation.Builder;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Settings;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckBox;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Shows a list of all available {@link Groups} available, letting the user
 * select which ones they want to be visible.
 */
public final class ContactsPreferencesActivity extends ExpandableListActivity implements
        AdapterView.OnItemClickListener, View.OnClickListener {
    private static final String TAG = "DisplayGroupsActivity";

    public interface Prefs {
        public static final String DISPLAY_ONLY_PHONES = "only_phones";
        public static final boolean DISPLAY_ONLY_PHONES_DEFAULT = false;

    }

    private static final int DIALOG_SORT_ORDER = 1;
    private static final int DIALOG_DISPLAY_ORDER = 2;

    private ExpandableListView mList;
    private DisplayAdapter mAdapter;

    private SharedPreferences mPrefs;
    private ContactsPreferences mContactsPrefs;

    private CheckBox mDisplayPhones;

    private View mHeaderPhones;
    private View mHeaderSeparator;

    private View mSortOrderView;
    private TextView mSortOrderTextView;
    private int mSortOrder;

    private View mDisplayOrderView;
    private TextView mDisplayOrderTextView;
    private int mDisplayOrder;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.contacts_preferences);

        mList = getExpandableListView();
        mList.setHeaderDividersEnabled(true);
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mContactsPrefs = new ContactsPreferences(this);
        mAdapter = new DisplayAdapter(this);

        final LayoutInflater inflater = getLayoutInflater();

        createWithPhonesOnlyPreferenceView(inflater);
        createSortOrderPreferenceView(inflater);
        createDisplayOrderPreferenceView(inflater);
        createDisplayGroupHeader(inflater);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        // Catch clicks on the header views
        mList.setOnItemClickListener(this);
        mList.setOnCreateContextMenuListener(this);

        mSortOrder = mContactsPrefs.getSortOrder();
        mDisplayOrder = mContactsPrefs.getDisplayOrder();
    }

    private void createWithPhonesOnlyPreferenceView(LayoutInflater inflater) {
        // Add the "Only contacts with phones" header modifier.
        mHeaderPhones = inflater.inflate(R.layout.display_options_phones_only, mList, false);
        mHeaderPhones.setId(R.id.header_phones);
        mDisplayPhones = (CheckBox) mHeaderPhones.findViewById(android.R.id.checkbox);
        mDisplayPhones.setChecked(mPrefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT));
        {
            final TextView text1 = (TextView)mHeaderPhones.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)mHeaderPhones.findViewById(android.R.id.text2);
            text1.setText(R.string.showFilterPhones);
            text2.setText(R.string.showFilterPhonesDescrip);
        }
    }

    private void createSortOrderPreferenceView(LayoutInflater inflater) {
        mSortOrderView = inflater.inflate(R.layout.preference_with_more_button, mList, false);

        View preferenceLayout = mSortOrderView.findViewById(R.id.preference);

        TextView label = (TextView)preferenceLayout.findViewById(R.id.label);
        label.setText(getString(R.string.display_options_sort_list_by));

        mSortOrderTextView = (TextView)preferenceLayout.findViewById(R.id.data);
    }

    private void createDisplayOrderPreferenceView(LayoutInflater inflater) {
        mDisplayOrderView = inflater.inflate(R.layout.preference_with_more_button, mList, false);
        View preferenceLayout = mDisplayOrderView.findViewById(R.id.preference);

        TextView label = (TextView)preferenceLayout.findViewById(R.id.label);
        label.setText(getString(R.string.display_options_view_names_as));

        mDisplayOrderTextView = (TextView)preferenceLayout.findViewById(R.id.data);
    }

    private void createDisplayGroupHeader(LayoutInflater inflater) {
        // Add the separator before showing the detailed group list.
        mHeaderSeparator = inflater.inflate(R.layout.list_separator, mList, false);
        {
            final TextView text1 = (TextView)mHeaderSeparator;
            text1.setText(R.string.headerContactGroups);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mList.removeHeaderView(mHeaderPhones);
        mList.removeHeaderView(mSortOrderView);
        mList.removeHeaderView(mDisplayOrderView);
        mList.removeHeaderView(mHeaderSeparator);

        // List adapter needs to be reset, because header views cannot be added
        // to a list with an existing adapter.
        setListAdapter(null);

        mList.addHeaderView(mHeaderPhones, null, true);
        if (mContactsPrefs.isSortOrderUserChangeable()) {
            mList.addHeaderView(mSortOrderView, null, true);
        }

        if (mContactsPrefs.isSortOrderUserChangeable()) {
            mList.addHeaderView(mDisplayOrderView, null, true);
        }

        mList.addHeaderView(mHeaderSeparator, null, false);

        setListAdapter(mAdapter);

        bindView();

        // Start background query to find account details
        new QueryGroupsTask(this).execute();
    }

    private void bindView() {
        mSortOrderTextView.setText(
                mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY
                        ? getString(R.string.display_options_sort_by_given_name)
                        : getString(R.string.display_options_sort_by_family_name));

        mDisplayOrderTextView.setText(
                mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY
                        ? getString(R.string.display_options_view_given_name_first)
                        : getString(R.string.display_options_view_family_name_first));
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_SORT_ORDER:
                return createSortOrderDialog();
            case DIALOG_DISPLAY_ORDER:
                return createDisplayOrderDialog();
        }

        return null;
    }

    private Dialog createSortOrderDialog() {
        String[] items = new String[] {
                getString(R.string.display_options_sort_by_given_name),
                getString(R.string.display_options_sort_by_family_name),
        };

        return new AlertDialog.Builder(this)
            .setIcon(com.android.internal.R.drawable.ic_dialog_menu_generic)
            .setTitle(R.string.display_options_sort_list_by)
            .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setSortOrder(dialog);
                        dialog.dismiss();
                    }
                })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    }

    private Dialog createDisplayOrderDialog() {
        String[] items = new String[] {
                getString(R.string.display_options_view_given_name_first),
                getString(R.string.display_options_view_family_name_first),
        };

        return new AlertDialog.Builder(this)
            .setIcon(com.android.internal.R.drawable.ic_dialog_menu_generic)
            .setTitle(R.string.display_options_view_names_as)
            .setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        setDisplayOrder(dialog);
                        dialog.dismiss();
                    }
                })
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        switch (id) {
            case DIALOG_SORT_ORDER:
                setCheckedItem(dialog,
                        mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY ? 0 : 1);
                break;
            case DIALOG_DISPLAY_ORDER:
                setCheckedItem(dialog,
                        mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY
                                ? 0 : 1);
                break;
        }
    }

    private void setCheckedItem(Dialog dialog, int position) {
        ListView listView = ((AlertDialog)dialog).getListView();
        listView.setItemChecked(position, true);
        listView.setSelection(position);
    }

    protected void setSortOrder(DialogInterface dialog) {
        ListView listView = ((AlertDialog)dialog).getListView();
        int checked = listView.getCheckedItemPosition();
        mSortOrder = checked == 0
                ? ContactsContract.Preferences.SORT_ORDER_PRIMARY
                : ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE;

        bindView();
    }

    protected void setDisplayOrder(DialogInterface dialog) {
        ListView listView = ((AlertDialog)dialog).getListView();
        int checked = listView.getCheckedItemPosition();
        mDisplayOrder = checked == 0
                ? ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY
                : ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE;

        bindView();
    }

    /**
     * Background operation to build set of {@link AccountDisplay} for each
     * {@link Sources#getAccounts(boolean)} that provides groups.
     */
    private static class QueryGroupsTask extends
            WeakAsyncTask<Void, Void, AccountSet, ContactsPreferencesActivity> {
        public QueryGroupsTask(ContactsPreferencesActivity target) {
            super(target);
        }

        @Override
        protected AccountSet doInBackground(ContactsPreferencesActivity target,
                Void... params) {
            final Context context = target;
            final Sources sources = Sources.getInstance(context);
            final ContentResolver resolver = context.getContentResolver();

            // Inflate groups entry for each account
            final AccountSet accounts = new AccountSet();
            for (Account account : sources.getAccounts(false)) {
                accounts.add(new AccountDisplay(resolver, account.name, account.type));
            }

            return accounts;
        }

        @Override
        protected void onPostExecute(ContactsPreferencesActivity target, AccountSet result) {
            target.mAdapter.setAccounts(result);
        }
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
         * {@link Settings#ACCOUNT_NAME} and {@link Settings#ACCOUNT_TYPE}.
         */
        public static GroupDelta fromSettings(ContentResolver resolver, String accountName,
                String accountType, boolean accountHasGroups) {
            final Uri settingsUri = Settings.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Settings.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(Settings.ACCOUNT_TYPE, accountType).build();
            final Cursor cursor = resolver.query(settingsUri, new String[] {
                    Settings.SHOULD_SYNC, Settings.UNGROUPED_VISIBLE
            }, null, null, null);

            try {
                final ContentValues values = new ContentValues();
                values.put(Settings.ACCOUNT_NAME, accountName);
                values.put(Settings.ACCOUNT_TYPE, accountType);

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

        public CharSequence getTitle(Context context) {
            if (mUngrouped) {
                if (mAccountHasGroups) {
                    return context.getText(R.string.display_ungrouped);
                } else {
                    return context.getText(R.string.display_all_contacts);
                }
            } else {
                final Integer titleRes = getAsInteger(Groups.TITLE_RES);
                if (titleRes != null) {
                    final String packageName = getAsString(Groups.RES_PACKAGE);
                    return context.getPackageManager().getText(packageName, titleRes, null);
                } else {
                    return getAsString(Groups.TITLE);
                }
            }
        }

        /**
         * Build a possible {@link ContentProviderOperation} to persist any
         * changes to the {@link Groups} or {@link Settings} row described by
         * this {@link GroupDelta}.
         */
        public ContentProviderOperation buildDiff() {
            if (isNoop()) {
                return null;
            } else if (isUpdate()) {
                // When has changes and "before" exists, then "update"
                final Builder builder = ContentProviderOperation
                        .newUpdate(mUngrouped ? Settings.CONTENT_URI : addCallerIsSyncAdapterParameter(Groups.CONTENT_URI.buildUpon().appendPath(this.getId().toString()).build()));
                if (mUngrouped) {
                    builder.withSelection(Settings.ACCOUNT_NAME + "=? AND " + Settings.ACCOUNT_TYPE
                            + "=?", new String[] {
                            this.getAsString(Settings.ACCOUNT_NAME),
                            this.getAsString(Settings.ACCOUNT_TYPE)
                    });
                } else {
                    builder.withSelection(Groups._ID + "=" + this.getId(), null);
                }
                builder.withValues(mAfter);
                return builder.build();
            } else if (isInsert() && mUngrouped) {
                // Only allow inserts for Settings
                mAfter.remove(mIdColumn);
                final Builder builder = ContentProviderOperation.newInsert(Settings.CONTENT_URI);
                builder.withValues(mAfter);
                return builder.build();
            } else {
                throw new IllegalStateException("Unexpected delete or insert");
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
     * {@link GroupDelta} details for a single {@link Account}, usually shown as
     * children under a single expandable group.
     */
    protected static class AccountDisplay {
        public String mName;
        public String mType;

        public GroupDelta mUngrouped;
        public ArrayList<GroupDelta> mSyncedGroups = Lists.newArrayList();
        public ArrayList<GroupDelta> mUnsyncedGroups = Lists.newArrayList();

        /**
         * Build an {@link AccountDisplay} covering all {@link Groups} under the
         * given {@link Account}.
         */
        public AccountDisplay(ContentResolver resolver, String accountName, String accountType) {
            mName = accountName;
            mType = accountType;

            final Uri groupsUri = Groups.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Groups.ACCOUNT_NAME, accountName)
                    .appendQueryParameter(Groups.ACCOUNT_TYPE, accountType).build();
            EntityIterator iterator = ContactsContract.Groups.newEntityIterator(resolver.query(
                    groupsUri, null, null, null, null));
            try {
                boolean hasGroups = false;

                // Create entries for each known group
                while (iterator.hasNext()) {
                    final ContentValues values = iterator.next().getEntityValues();
                    final GroupDelta group = GroupDelta.fromBefore(values);
                    addGroup(group);
                    hasGroups = true;
                }
                // Create single entry handling ungrouped status
                mUngrouped = GroupDelta.fromSettings(resolver, accountName, accountType, hasGroups);
                addGroup(mUngrouped);
            } finally {
                iterator.close();
            }
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
         * changes to {@link GroupDelta} rows under this {@link Account}.
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
     * grouped by {@link Account} source. Shows footer row when any groups are
     * unsynced, as determined through {@link AccountDisplay#mUnsyncedGroups}.
     */
    protected static class DisplayAdapter extends BaseExpandableListAdapter {
        private Context mContext;
        private LayoutInflater mInflater;
        private Sources mSources;
        private AccountSet mAccounts;

        private boolean mChildWithPhones = false;

        public DisplayAdapter(Context context) {
            mContext = context;
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mSources = Sources.getInstance(context);
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

        /** {@inheritDoc} */
        public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.display_child, parent, false);
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

//              final int count = cursor.getInt(GroupsQuery.SUMMARY_COUNT);
//              final int withPhones = cursor.getInt(GroupsQuery.SUMMARY_WITH_PHONES);

//              final CharSequence descrip = mContext.getResources().getQuantityString(
//                      mChildWithPhones ? R.plurals.groupDescripPhones : R.plurals.groupDescrip,
//                      count, count, withPhones);

//              text2.setText(descrip);
                text2.setVisibility(View.GONE);
            } else {
                // When unknown child, this is "more" footer view
                checkbox.setVisibility(View.GONE);
                text1.setText(R.string.display_more_groups);
                text2.setVisibility(View.GONE);
            }

            return convertView;
        }

        /** {@inheritDoc} */
        public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.display_group, parent, false);
            }

            final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

            final AccountDisplay account = (AccountDisplay)this.getGroup(groupPosition);

            final ContactsSource source = mSources.getInflatedSource(account.mType,
                    ContactsSource.LEVEL_SUMMARY);

            text1.setText(account.mName);
            text2.setText(source.getDisplayLabel(mContext));
            text2.setVisibility(account.mName == null ? View.GONE : View.VISIBLE);

            return convertView;
        }

        /** {@inheritDoc} */
        public Object getChild(int groupPosition, int childPosition) {
            final AccountDisplay account = mAccounts.get(groupPosition);
            final boolean validChild = childPosition >= 0
                    && childPosition < account.mSyncedGroups.size();
            if (validChild) {
                return account.mSyncedGroups.get(childPosition);
            } else {
                return null;
            }
        }

        /** {@inheritDoc} */
        public long getChildId(int groupPosition, int childPosition) {
            final GroupDelta child = (GroupDelta)getChild(groupPosition, childPosition);
            if (child != null) {
                final Long childId = child.getId();
                return childId != null ? childId : Long.MIN_VALUE;
            } else {
                return Long.MIN_VALUE;
            }
        }

        /** {@inheritDoc} */
        public int getChildrenCount(int groupPosition) {
            // Count is any synced groups, plus possible footer
            final AccountDisplay account = mAccounts.get(groupPosition);
            final boolean anyHidden = account.mUnsyncedGroups.size() > 0;
            return account.mSyncedGroups.size() + (anyHidden ? 1 : 0);
        }

        /** {@inheritDoc} */
        public Object getGroup(int groupPosition) {
            return mAccounts.get(groupPosition);
        }

        /** {@inheritDoc} */
        public int getGroupCount() {
            if (mAccounts == null) {
                return 0;
            }
            return mAccounts.size();
        }

        /** {@inheritDoc} */
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        /** {@inheritDoc} */
        public boolean hasStableIds() {
            return true;
        }

        /** {@inheritDoc} */
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return true;
        }
    }

    /**
     * Handle any clicks on header views added to our {@link #mAdapter}, which
     * are usually the global modifier checkboxes.
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d(TAG, "OnItemClick, position=" + position + ", id=" + id);
        if (view == mHeaderPhones) {
            mDisplayPhones.toggle();
            return;
        }
        if (view == mDisplayOrderView) {
            Log.d(TAG, "Showing Display Order dialog");
            showDialog(DIALOG_DISPLAY_ORDER);
            return;
        }
        if (view == mSortOrderView) {
            Log.d(TAG, "Showing Sort Order dialog");
            showDialog(DIALOG_SORT_ORDER);
            return;
        }
    }

    /** {@inheritDoc} */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_done: {
                this.doSaveAction();
                break;
            }
            case R.id.btn_discard: {
                this.finish();
                break;
            }
        }
    }

    /**
     * Assign a specific value to {@link Prefs#DISPLAY_ONLY_PHONES}, refreshing
     * the visible list as needed.
     */
    protected void setDisplayOnlyPhones(boolean displayOnlyPhones) {
        mDisplayPhones.setChecked(displayOnlyPhones);

        Editor editor = mPrefs.edit();
        editor.putBoolean(Prefs.DISPLAY_ONLY_PHONES, displayOnlyPhones);
        editor.commit();

        mAdapter.setChildDescripWithPhones(displayOnlyPhones);
        mAdapter.notifyDataSetChanged();
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
        if (GoogleSource.ACCOUNT_TYPE.equals(account.mType)) {
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

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        doSaveAction();
    }

    private void doSaveAction() {
        mContactsPrefs.setSortOrder(mSortOrder);
        mContactsPrefs.setDisplayOrder(mDisplayOrder);

        if (mAdapter == null || mAdapter.mAccounts == null) {
            return;
        }
        setDisplayOnlyPhones(mDisplayPhones.isChecked());
        new UpdateTask(this).execute(mAdapter.mAccounts);
    }

    /**
     * Background task that persists changes to {@link Groups#GROUP_VISIBLE},
     * showing spinner dialog to user while updating.
     */
    public static class UpdateTask extends
            WeakAsyncTask<AccountSet, Void, Void, Activity> {
        private WeakReference<ProgressDialog> mProgress;

        public UpdateTask(Activity target) {
            super(target);
        }

        /** {@inheritDoc} */
        @Override
        protected void onPreExecute(Activity target) {
            final Context context = target;

            mProgress = new WeakReference<ProgressDialog>(ProgressDialog.show(context, null,
                    context.getText(R.string.savingDisplayGroups)));

            // Before starting this task, start an empty service to protect our
            // process from being reclaimed by the system.
            context.startService(new Intent(context, EmptyService.class));
        }

        /** {@inheritDoc} */
        @Override
        protected Void doInBackground(Activity target, AccountSet... params) {
            final Context context = target;
            final ContentValues values = new ContentValues();
            final ContentResolver resolver = context.getContentResolver();

            try {
                // Build changes and persist in transaction
                final AccountSet set = params[0];
                final ArrayList<ContentProviderOperation> diff = set.buildDiff();
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

            final ProgressDialog dialog = mProgress.get();
            if (dialog != null) {
                try {
                    dialog.dismiss();
                } catch (Exception e) {
                    Log.e(TAG, "Error dismissing progress dialog", e);
                }
            }

            target.finish();

            // Stop the service that was protecting us
            context.stopService(new Intent(context, EmptyService.class));
        }
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }
}
