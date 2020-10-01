/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.contacts.drawer;

import android.app.Activity;
import android.graphics.PorterDuff;
import androidx.annotation.LayoutRes;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity.ContactsView;
import com.android.contacts.group.GroupListItem;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.model.account.AccountDisplayInfo;
import com.android.contacts.model.account.AccountDisplayInfoFactory;
import com.android.contacts.util.SharedPreferenceUtil;
import com.android.contactsbind.HelpUtils;
import com.android.contactsbind.ObjectFactory;

import java.util.ArrayList;
import java.util.List;

public class DrawerAdapter extends BaseAdapter {

    private static final int VIEW_TYPE_PRIMARY_ITEM = 0;
    private static final int VIEW_TYPE_MISC_ITEM = 1;
    private static final int VIEW_TYPE_HEADER_ITEM = 2;
    private static final int VIEW_TYPE_GROUP_ENTRY = 3;
    private static final int VIEW_TYPE_ACCOUNT_ENTRY = 4;
    private static final int VIEW_TYPE_CREATE_LABEL = 5;
    private static final int VIEW_TYPE_NAV_SPACER = 6;
    private static final int VIEW_TYPE_NAV_DIVIDER = 7;

    // This count must be updated if we add more view types.
    private static final int VIEW_TYPE_COUNT = 9;

    private static final int TYPEFACE_STYLE_ACTIVATE = R.style.DrawerItemTextActiveStyle;
    private static final int TYPEFACE_STYLE_INACTIVE = R.style.DrawerItemTextInactiveStyle;

    private final Activity mActivity;
    private final LayoutInflater mInflater;
    private ContactsView mSelectedView;
    private boolean mAreGroupWritableAccountsAvailable;

    // The group/account that was last clicked.
    private long mSelectedGroupId;
    private ContactListFilter mSelectedAccount;

    // Adapter elements, ordered in this way mItemsList. The ordering is based on:
    //  [Navigation spacer item]
    //  [Primary items] (Contacts, Suggestions)
    //  [Group Header]
    //  [Groups]
    //  [Create Label button]
    //  [Account Header]
    //  [Accounts]
    //  [Misc items] (a divider, Settings, Help & Feedback)
    //  [Navigation spacer item]
    private NavSpacerItem mNavSpacerItem = null;
    private List<PrimaryItem> mPrimaryItems = new ArrayList<>();
    private HeaderItem mGroupHeader = null;
    private List<GroupEntryItem> mGroupEntries = new ArrayList<>();
    private BaseDrawerItem mCreateLabelButton = null;
    private HeaderItem mAccountHeader = null;
    private List<AccountEntryItem> mAccountEntries = new ArrayList<>();
    private List<BaseDrawerItem> mMiscItems = new ArrayList<>();

    private List<BaseDrawerItem> mItemsList = new ArrayList<>();
    private AccountDisplayInfoFactory mAccountDisplayFactory;

    public DrawerAdapter(Activity activity) {
        super();
        mInflater = LayoutInflater.from(activity);
        mActivity = activity;
        initializeDrawerMenuItems();
    }

    private void initializeDrawerMenuItems() {
        // Spacer item for dividing sections in drawer
        mNavSpacerItem = new NavSpacerItem(R.id.nav_drawer_spacer);
        // Primary items
        mPrimaryItems.add(new PrimaryItem(R.id.nav_all_contacts, R.string.contactsList,
                R.drawable.quantum_ic_account_circle_vd_theme_24, ContactsView.ALL_CONTACTS));
        if (ObjectFactory.getAssistantFragment() != null) {
            mPrimaryItems.add(new PrimaryItem(R.id.nav_assistant, R.string.menu_assistant,
                    R.drawable.quantum_ic_assistant_vd_theme_24, ContactsView.ASSISTANT));
        }
        // Group Header
        mGroupHeader = new HeaderItem(R.id.nav_groups, R.string.menu_title_groups);
        // Account Header
        mAccountHeader = new HeaderItem(R.id.nav_filters, R.string.menu_title_filters);
        // Create Label Button
        mCreateLabelButton = new BaseDrawerItem(VIEW_TYPE_CREATE_LABEL, R.id.nav_create_label,
                R.string.menu_new_group_action_bar, R.drawable.quantum_ic_add_vd_theme_24);
        // Misc Items
        mMiscItems.add(new DividerItem());
        mMiscItems.add(new MiscItem(R.id.nav_settings, R.string.menu_settings,
                R.drawable.quantum_ic_settings_vd_theme_24));
        if (HelpUtils.isHelpAndFeedbackAvailable()) {
            mMiscItems.add(new MiscItem(R.id.nav_help, R.string.menu_help,
                    R.drawable.quantum_ic_help_vd_theme_24));
        }
        rebuildItemsList();
    }

    private void rebuildItemsList() {
        mItemsList.clear();
        mItemsList.add(mNavSpacerItem);
        mItemsList.addAll(mPrimaryItems);
        if (mAreGroupWritableAccountsAvailable || !mGroupEntries.isEmpty()) {
            mItemsList.add(mGroupHeader);
        }
        mItemsList.addAll(mGroupEntries);
        if (mAreGroupWritableAccountsAvailable) {
            mItemsList.add(mCreateLabelButton);
        }
        if (mAccountEntries.size() > 0) {
            mItemsList.add(mAccountHeader);
        }
        mItemsList.addAll(mAccountEntries);
        mItemsList.addAll(mMiscItems);
        mItemsList.add(mNavSpacerItem);
    }

    public void setGroups(List<GroupListItem> groupListItems, boolean areGroupWritable) {
        final ArrayList<GroupEntryItem> groupEntries = new ArrayList<GroupEntryItem>();
        for (GroupListItem group : groupListItems) {
            groupEntries.add(new GroupEntryItem(R.id.nav_group, group));
        }
        mGroupEntries.clear();
        mGroupEntries.addAll(groupEntries);
        mAreGroupWritableAccountsAvailable = areGroupWritable;
        notifyChangeAndRebuildList();
    }

    public void setAccounts(List<ContactListFilter> accountFilterItems) {
        ArrayList<AccountEntryItem> accountItems = new ArrayList<AccountEntryItem>();
        for (ContactListFilter filter : accountFilterItems) {
            accountItems.add(new AccountEntryItem(R.id.nav_filter, filter));
        }
        mAccountDisplayFactory = AccountDisplayInfoFactory.fromListFilters(mActivity,
                accountFilterItems);
        mAccountEntries.clear();
        mAccountEntries.addAll(accountItems);
        // TODO investigate performance of calling notifyDataSetChanged
        notifyChangeAndRebuildList();
    }

    @Override
    public int getCount() {
        return  mItemsList.size();
    }

    public BaseDrawerItem getItem(int position) {
        return mItemsList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).id;
    }

    @Override
    public int getViewTypeCount() {
        return VIEW_TYPE_COUNT;
    }

    @Override
    public View getView(int position, View view, ViewGroup viewGroup) {
        final BaseDrawerItem drawerItem = getItem(position);
        switch (drawerItem.viewType) {
            case VIEW_TYPE_PRIMARY_ITEM:
                return getPrimaryItemView((PrimaryItem) drawerItem, view, viewGroup);
            case VIEW_TYPE_HEADER_ITEM:
                return getHeaderItemView((HeaderItem) drawerItem, view, viewGroup);
            case VIEW_TYPE_CREATE_LABEL:
                return getDrawerItemView(drawerItem, view, viewGroup);
            case VIEW_TYPE_GROUP_ENTRY:
                return getGroupEntryView((GroupEntryItem) drawerItem, view, viewGroup);
            case VIEW_TYPE_ACCOUNT_ENTRY:
                return getAccountItemView((AccountEntryItem) drawerItem, view, viewGroup);
            case VIEW_TYPE_MISC_ITEM:
                return getDrawerItemView(drawerItem, view, viewGroup);
            case VIEW_TYPE_NAV_SPACER:
                return getBaseItemView(R.layout.nav_drawer_spacer, view, viewGroup);
            case VIEW_TYPE_NAV_DIVIDER:
                return getBaseItemView(R.layout.drawer_horizontal_divider, view, viewGroup);
        }
        throw new IllegalStateException("Unknown drawer item " + drawerItem);
    }

    private View getBaseItemView(@LayoutRes int layoutResID, View result,ViewGroup parent) {
        if (result == null) {
            result = mInflater.inflate(layoutResID, parent, false);
        }
        return result;
    }

    private View getPrimaryItemView(PrimaryItem item, View result, ViewGroup parent) {
        if (result == null) {
            result = mInflater.inflate(R.layout.drawer_primary_item, parent, false);
        }
        final TextView titleView = (TextView) result.findViewById(R.id.title);
        titleView.setText(item.text);
        final ImageView iconView = (ImageView) result.findViewById(R.id.icon);
        iconView.setImageResource(item.icon);
        final TextView newBadge = (TextView) result.findViewById(R.id.assistant_new_badge);
        final boolean showWelcomeBadge = !SharedPreferenceUtil.isWelcomeCardDismissed(mActivity);
        newBadge.setVisibility(item.contactsView == ContactsView.ASSISTANT && showWelcomeBadge
                ? View.VISIBLE : View.GONE);
        result.setActivated(item.contactsView == mSelectedView);
        updateSelectedStatus(titleView, iconView, item.contactsView == mSelectedView);
        result.setId(item.id);
        return result;
    }

    private View getHeaderItemView(HeaderItem item, View result, ViewGroup parent) {
        if (result == null) {
            result = mInflater.inflate(R.layout.drawer_header, parent, false);
        }
        final TextView textView = (TextView) result.findViewById(R.id.title);
        textView.setText(item.text);
        result.setId(item.id);
        return result;
    }

    private View getGroupEntryView(GroupEntryItem item, View result, ViewGroup parent) {
        if (result == null || !(result.getTag() instanceof GroupEntryItem)) {
            result = mInflater.inflate(R.layout.drawer_item, parent, false);
            result.setId(item.id);
        }

        final GroupListItem groupListItem = item.group;
        final TextView title = (TextView) result.findViewById(R.id.title);
        title.setText(groupListItem.getTitle());
        final ImageView icon = (ImageView) result.findViewById(R.id.icon);
        icon.setImageResource(R.drawable.quantum_ic_label_vd_theme_24);
        final boolean activated = groupListItem.getGroupId() == mSelectedGroupId &&
                mSelectedView == ContactsView.GROUP_VIEW;
        updateSelectedStatus(title, icon, activated);
        result.setActivated(activated);

        result.setTag(groupListItem);
        result.setContentDescription(
                mActivity.getString(R.string.navigation_drawer_label, groupListItem.getTitle()));
        return result;
    }

    private View getAccountItemView(AccountEntryItem item, View result, ViewGroup parent) {
        if (result == null || !(result.getTag() instanceof ContactListFilter)) {
            result = mInflater.inflate(R.layout.drawer_item, parent, false);
            result.setId(item.id);
        }
        final ContactListFilter account = item.account;
        final AccountDisplayInfo displayableAccount =
                mAccountDisplayFactory.getAccountDisplayInfoFor(item.account);
        final TextView textView = ((TextView) result.findViewById(R.id.title));
        textView.setText(displayableAccount.getNameLabel());
        final boolean activated = account.equals(mSelectedAccount)
                && mSelectedView == ContactsView.ACCOUNT_VIEW;
        textView.setTextAppearance(mActivity, activated
                ? TYPEFACE_STYLE_ACTIVATE : TYPEFACE_STYLE_INACTIVE);

        final ImageView icon = (ImageView) result.findViewById(R.id.icon);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setImageDrawable(displayableAccount.getIcon());

        result.setTag(account);
        result.setActivated(activated);
        result.setContentDescription(
                displayableAccount.getTypeLabel() + " " + item.account.accountName);
        return result;
    }

    private View getDrawerItemView(BaseDrawerItem item, View result, ViewGroup parent) {
        if (result == null) {
            result = mInflater.inflate(R.layout.drawer_item, parent, false);
        }

        final TextView textView = (TextView) result.findViewById(R.id.title);
        textView.setText(item.text);
        final ImageView iconView = (ImageView) result.findViewById(R.id.icon);
        iconView.setImageResource(item.icon);
        result.setId(item.id);
        updateSelectedStatus(textView, iconView, false);
        return result;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).viewType;
    }

    private void updateSelectedStatus(TextView textView, ImageView imageView, boolean activated) {
        textView.setTextAppearance(mActivity, activated
                ? TYPEFACE_STYLE_ACTIVATE : TYPEFACE_STYLE_INACTIVE);
        if (activated) {
            imageView.setColorFilter(mActivity.getResources().getColor(R.color.primary_color),
                    PorterDuff.Mode.SRC_ATOP);
        } else {
            imageView.clearColorFilter();
        }
    }

    private void notifyChangeAndRebuildList() {
        rebuildItemsList();
        notifyDataSetChanged();
    }

    public void setSelectedContactsView(ContactsView contactsView) {
        if (mSelectedView == contactsView) {
            return;
        }
        mSelectedView = contactsView;
        notifyChangeAndRebuildList();
    }


    public void setSelectedGroupId(long groupId) {
        if (mSelectedGroupId == groupId) {
            return;
        }
        mSelectedGroupId = groupId;
        notifyChangeAndRebuildList();
    }

    public long getSelectedGroupId() {
        return mSelectedGroupId;
    }

    public void setSelectedAccount(ContactListFilter filter) {
        if (mSelectedAccount == filter) {
            return;
        }
        mSelectedAccount = filter;
        notifyChangeAndRebuildList();
    }

    public ContactListFilter getSelectedAccount() {
        return mSelectedAccount;
    }

    public static class BaseDrawerItem {
        public final int viewType;
        public final int id;
        public final int text;
        public final int icon;

        public BaseDrawerItem(int adapterViewType, int viewId, int textResId, int iconResId) {
            viewType = adapterViewType;
            id = viewId;
            text = textResId;
            icon = iconResId;
        }
    }

    // Navigation drawer item for Contacts or Suggestions view which contains a name, an icon and
    // contacts view.
    public static class PrimaryItem extends BaseDrawerItem {
        public final ContactsView contactsView;

        public PrimaryItem(int id, int pageName, int iconId, ContactsView contactsView) {
            super(VIEW_TYPE_PRIMARY_ITEM, id, pageName, iconId);
            this.contactsView = contactsView;
        }
    }

    // Navigation drawer item for Settings, help and feedback, etc.
    public static class MiscItem extends BaseDrawerItem {
        public MiscItem(int id, int textId, int iconId) {
            super(VIEW_TYPE_MISC_ITEM, id, textId, iconId);
        }
    }

    // Header for a list of sub-items in the drawer.
    public static class HeaderItem extends BaseDrawerItem {
        public HeaderItem(int id, int textId) {
            super(VIEW_TYPE_HEADER_ITEM, id, textId, /* iconResId */ 0);
        }
    }

    // Navigation drawer item for spacer item for dividing sections in the drawer.
    public static class NavSpacerItem extends BaseDrawerItem {
        public NavSpacerItem(int id) {
            super(VIEW_TYPE_NAV_SPACER, id, /* textResId */ 0, /* iconResId */ 0);
        }
    }

    // Divider for drawing a line between sections in the drawer.
    public static class DividerItem extends BaseDrawerItem {
        public DividerItem() {
            super(VIEW_TYPE_NAV_DIVIDER, /* id */ 0, /* textResId */ 0, /* iconResId */ 0);
        }
    }

    // Navigation drawer item for a group.
    public static class GroupEntryItem extends BaseDrawerItem {
        private final GroupListItem group;

        public GroupEntryItem(int id, GroupListItem group) {
            super(VIEW_TYPE_GROUP_ENTRY, id, /* textResId */ 0, /* iconResId */ 0);
            this.group = group;
        }
    }

    // Navigation drawer item for an account.
    public static class AccountEntryItem extends BaseDrawerItem {
        private final ContactListFilter account;

        public AccountEntryItem(int id, ContactListFilter account) {
            super(VIEW_TYPE_ACCOUNT_ENTRY, id, /* textResId */ 0, /* iconResId */ 0);
            this.account = account;
        }
    }
}
