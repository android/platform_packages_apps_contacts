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

package com.android.contacts.editor;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.group.GroupNameEditDialogFragment;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.RawContactModifier;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.UiClosables;

import com.google.common.base.Objects;

import java.util.ArrayList;

/**
 * An editor for group membership.  Displays the current group membership list and
 * brings up a dialog to change it.
 */
public class GroupMembershipView extends LinearLayout
        implements OnClickListener, OnItemClickListener {

    public static final String TAG_CREATE_GROUP_FRAGMENT = "createGroupDialog";

    private static final int CREATE_NEW_GROUP_GROUP_ID = 133;

    public static final class GroupSelectionItem {
        private final long mGroupId;
        private final String mTitle;
        private boolean mChecked;

        public GroupSelectionItem(long groupId, String title, boolean checked) {
            this.mGroupId = groupId;
            this.mTitle = title;
            mChecked = checked;
        }

        public long getGroupId() {
            return mGroupId;
        }

        public boolean isChecked() {
            return mChecked;
        }

        public void setChecked(boolean checked) {
            mChecked = checked;
        }

        @Override
        public String toString() {
            return mTitle;
        }
    }

    /**
     * Extends the array adapter to show checkmarks on all but the last list item for
     * the group membership popup.  Note that this is highly specific to the fact that the
     * group_membership_list_item.xml is a CheckedTextView object.
     */
    private class GroupMembershipAdapter<T> extends ArrayAdapter<T> {

        // The position of the group with the largest group ID
        private int mNewestGroupPosition;

        public GroupMembershipAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        public boolean getItemIsCheckable(int position) {
            // Item is checkable if it is NOT the last one in the list
            return position != getCount()-1;
        }

        @Override
        public int getItemViewType(int position) {
            return getItemIsCheckable(position) ? 0 : 1;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View itemView = super.getView(position, convertView, parent);
            if (itemView == null) {
                return null;
            }

            // Hide the checkable drawable.  This assumes that the item views
            // are CheckedTextView objects
            final CheckedTextView checkedTextView = (CheckedTextView)itemView;
            if (!getItemIsCheckable(position)) {
                checkedTextView.setCheckMarkDrawable(null);
            }
            checkedTextView.setTextColor(mPrimaryTextColor);

            return checkedTextView;
        }

        public int getNewestGroupPosition() {
            return mNewestGroupPosition;
        }

        public void setNewestGroupPosition(int newestGroupPosition) {
            mNewestGroupPosition = newestGroupPosition;
        }

    }

    private RawContactDelta mState;
    private Cursor mGroupMetaData;
    private boolean mAccountHasGroups;
    private String mAccountName;
    private String mAccountType;
    private String mDataSet;
    private TextView mGroupList;
    private GroupMembershipAdapter<GroupSelectionItem> mAdapter;
    private long mDefaultGroupId;
    private long mFavoritesGroupId;
    private ListPopupWindow mPopup;
    private DataKind mKind;
    private boolean mDefaultGroupVisibilityKnown;
    private boolean mDefaultGroupVisible;
    private boolean mCreatedNewGroup;
    private GroupNameEditDialogFragment mGroupNameEditDialogFragment;
    private GroupNameEditDialogFragment.Listener mListener =
            new GroupNameEditDialogFragment.Listener() {
                @Override
                public void onGroupNameEditCancelled() {
                }

                @Override
                public void onGroupNameEditCompleted(String name) {
                    mCreatedNewGroup = true;
                }
            };

    private String mNoGroupString;
    private int mPrimaryTextColor;
    private int mHintTextColor;

    public GroupMembershipView(Context context) {
        super(context);
    }

    public GroupMembershipView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Resources resources = getContext().getResources();
        mPrimaryTextColor = resources.getColor(R.color.primary_text_color);
        mHintTextColor = resources.getColor(R.color.editor_disabled_text_color);
        mNoGroupString = getContext().getString(R.string.group_edit_field_hint_text);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    private void setGroupNameEditDialogFragment() {
        final FragmentManager fragmentManager = ((Activity) getContext()).getFragmentManager();
        mGroupNameEditDialogFragment = (GroupNameEditDialogFragment)
                fragmentManager.findFragmentByTag(TAG_CREATE_GROUP_FRAGMENT);
        if (mGroupNameEditDialogFragment != null) {
            mGroupNameEditDialogFragment.setListener(mListener);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mGroupList != null) {
            mGroupList.setEnabled(enabled);
        }
    }

    public void setKind(DataKind kind) {
        mKind = kind;
        final ImageView imageView = (ImageView) findViewById(R.id.kind_icon);
        imageView.setContentDescription(getResources().getString(kind.titleRes));
    }

    public void setGroupMetaData(Cursor groupMetaData) {
        this.mGroupMetaData = groupMetaData;
        updateView();
        // Open up the list of groups if a new group was just created.
        if (mCreatedNewGroup) {
            mCreatedNewGroup = false;
            onClick(this); // This causes the popup to open.
            if (mPopup != null) {
                // Ensure that the newly created group is checked.
                final int position = mAdapter.getNewestGroupPosition();
                ListView listView = mPopup.getListView();
                if (listView != null && !listView.isItemChecked(position)) {
                    // Newly created group is not checked, so check it.
                    listView.setItemChecked(position, true);
                    onItemClick(listView, null, position, listView.getItemIdAtPosition(position));
                }
            }
        }
    }

    /** Whether {@link #setGroupMetaData} has been invoked yet. */
    public boolean wasGroupMetaDataBound() {
        return mGroupMetaData != null;
    }

    /**
     * Return true if the account has groups to edit group membership for contacts
     * belong to the account.
     */
    public boolean accountHasGroups() {
        return mAccountHasGroups;
    }

    public void setState(RawContactDelta state) {
        mState = state;
        mAccountType = mState.getAccountType();
        mAccountName = mState.getAccountName();
        mDataSet = mState.getDataSet();
        mDefaultGroupVisibilityKnown = false;
        mCreatedNewGroup = false;
        updateView();
        setGroupNameEditDialogFragment();
    }

    private void updateView() {
        if (mGroupMetaData == null || mGroupMetaData.isClosed() || mAccountType == null
                || mAccountName == null) {
            setVisibility(GONE);
            return;
        }

        mFavoritesGroupId = 0;
        mDefaultGroupId = 0;

        StringBuilder sb = new StringBuilder();
        mGroupMetaData.moveToPosition(-1);
        while (mGroupMetaData.moveToNext()) {
            String accountName = mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_NAME);
            String accountType = mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            String dataSet = mGroupMetaData.getString(GroupMetaDataLoader.DATA_SET);
            if (accountName.equals(mAccountName) && accountType.equals(mAccountType)
                    && Objects.equal(dataSet, mDataSet)) {
                long groupId = mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID);
                if (!mGroupMetaData.isNull(GroupMetaDataLoader.FAVORITES)
                        && mGroupMetaData.getInt(GroupMetaDataLoader.FAVORITES) != 0) {
                    mFavoritesGroupId = groupId;
                } else if (!mGroupMetaData.isNull(GroupMetaDataLoader.AUTO_ADD)
                            && mGroupMetaData.getInt(GroupMetaDataLoader.AUTO_ADD) != 0) {
                    mDefaultGroupId = groupId;
                } else {
                    mAccountHasGroups = true;
                }

                // Exclude favorites from the list - they are handled with special UI (star)
                // Also exclude the default group.
                if (groupId != mFavoritesGroupId && groupId != mDefaultGroupId
                        && hasMembership(groupId)) {
                    String title = mGroupMetaData.getString(GroupMetaDataLoader.TITLE);
                    if (!TextUtils.isEmpty(title)) {
                        if (sb.length() != 0) {
                            sb.append(", ");
                        }
                        sb.append(title);
                    }
                }
            }
        }

        if (!mAccountHasGroups) {
            setVisibility(GONE);
            return;
        }

        if (mGroupList == null) {
            mGroupList = (TextView) findViewById(R.id.group_list);
            mGroupList.setOnClickListener(this);
        }

        mGroupList.setEnabled(isEnabled());
        if (sb.length() == 0) {
            mGroupList.setText(mNoGroupString);
            mGroupList.setTextColor(mHintTextColor);
        } else {
            mGroupList.setText(sb);
            mGroupList.setTextColor(mPrimaryTextColor);
        }
        setVisibility(VISIBLE);

        if (!mDefaultGroupVisibilityKnown) {
            // Only show the default group (My Contacts) if the contact is NOT in it
            mDefaultGroupVisible = mDefaultGroupId != 0 && !hasMembership(mDefaultGroupId);
            mDefaultGroupVisibilityKnown = true;
        }
    }

    @Override
    public void onClick(View v) {
        if (UiClosables.closeQuietly(mPopup)) {
            mPopup = null;
            return;
        }

        requestFocus();
        mAdapter = new GroupMembershipAdapter<GroupSelectionItem>(
                getContext(), R.layout.group_membership_list_item);

        long newestGroupId = -1;

        mGroupMetaData.moveToPosition(-1);
        while (mGroupMetaData.moveToNext()) {
            String accountName = mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_NAME);
            String accountType = mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            String dataSet = mGroupMetaData.getString(GroupMetaDataLoader.DATA_SET);
            if (accountName.equals(mAccountName) && accountType.equals(mAccountType)
                    && Objects.equal(dataSet, mDataSet)) {
                long groupId = mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID);
                if (groupId != mFavoritesGroupId
                        && (groupId != mDefaultGroupId || mDefaultGroupVisible)) {
                    if (groupId > newestGroupId) {
                        newestGroupId = groupId;
                        mAdapter.setNewestGroupPosition(mAdapter.getCount());
                    }
                    String title = mGroupMetaData.getString(GroupMetaDataLoader.TITLE);
                    boolean checked = hasMembership(groupId);
                    mAdapter.add(new GroupSelectionItem(groupId, title, checked));
                }
            }
        }

        mAdapter.add(new GroupSelectionItem(CREATE_NEW_GROUP_GROUP_ID,
                getContext().getString(R.string.create_group_item_label), false));

        mPopup = new ListPopupWindow(getContext(), null);
        mPopup.setAnchorView(mGroupList);
        mPopup.setAdapter(mAdapter);
        mPopup.setModal(true);
        mPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        mPopup.show();

        ListView listView = mPopup.getListView();
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        listView.setOverScrollMode(OVER_SCROLL_ALWAYS);
        int count = mAdapter.getCount();
        for (int i = 0; i < count; i++) {
            listView.setItemChecked(i, mAdapter.getItem(i).isChecked());
        }

        listView.setOnItemClickListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        UiClosables.closeQuietly(mPopup);
        mPopup = null;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ListView list = (ListView) parent;
        int count = mAdapter.getCount();

        if (list.isItemChecked(count - 1)) {
            list.setItemChecked(count - 1, false);
            createNewGroup();
            return;
        }

        for (int i = 0; i < count; i++) {
            mAdapter.getItem(i).setChecked(list.isItemChecked(i));
        }

        // First remove the memberships that have been unchecked
        ArrayList<ValuesDelta> entries = mState.getMimeEntries(GroupMembership.CONTENT_ITEM_TYPE);
        if (entries != null) {
            for (ValuesDelta entry : entries) {
                if (!entry.isDelete()) {
                    Long groupId = entry.getGroupRowId();
                    if (groupId != null && groupId != mFavoritesGroupId
                            && (groupId != mDefaultGroupId || mDefaultGroupVisible)
                            && !isGroupChecked(groupId)) {
                        entry.markDeleted();
                    }
                }
            }
        }

        // Now add the newly selected items
        for (int i = 0; i < count; i++) {
            GroupSelectionItem item = mAdapter.getItem(i);
            long groupId = item.getGroupId();
            if (item.isChecked() && !hasMembership(groupId)) {
                ValuesDelta entry = RawContactModifier.insertChild(mState, mKind);
                if (entry != null) {
                    entry.setGroupRowId(groupId);
                }
            }
        }

        updateView();
    }

    private boolean isGroupChecked(long groupId) {
        int count = mAdapter.getCount();
        for (int i = 0; i < count; i++) {
            GroupSelectionItem item = mAdapter.getItem(i);
            if (groupId == item.getGroupId()) {
                return item.isChecked();
            }
        }
        return false;
    }

    private boolean hasMembership(long groupId) {
        if (groupId == mDefaultGroupId && mState.isContactInsert()) {
            return true;
        }

        ArrayList<ValuesDelta> entries = mState.getMimeEntries(GroupMembership.CONTENT_ITEM_TYPE);
        if (entries != null) {
            for (ValuesDelta values : entries) {
                if (!values.isDelete()) {
                    Long id = values.getGroupRowId();
                    if (id != null && id == groupId) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void createNewGroup() {
        UiClosables.closeQuietly(mPopup);
        mPopup = null;
        mGroupNameEditDialogFragment =
                    GroupNameEditDialogFragment.newInstanceForCreation(
                            new AccountWithDataSet(mAccountName, mAccountType, mDataSet), null);
        mGroupNameEditDialogFragment.setListener(mListener);
        mGroupNameEditDialogFragment.show(
                ((Activity) getContext()).getFragmentManager(),
                TAG_CREATE_GROUP_FRAGMENT);
    }
}
