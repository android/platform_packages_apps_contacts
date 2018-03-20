/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 */

package com.android.contacts.group;

import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.text.TextUtils;

import com.android.contacts.ContactsUtils;
import com.android.contacts.GroupListLoader;
import com.android.contacts.activities.ContactSelectionActivity;
import com.android.contacts.list.ContactsSectionIndexer;
import com.android.contacts.list.UiIntentActions;
import com.android.contacts.model.account.GoogleAccountType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Group utility methods.
 */
public final class GroupUtil {

    public final static String ALL_GROUPS_SELECTION =
            Groups.ACCOUNT_TYPE + " NOT NULL AND " + Groups.ACCOUNT_NAME + " NOT NULL AND "
                    + Groups.DELETED + "=0";

    public final static String DEFAULT_SELECTION = ALL_GROUPS_SELECTION + " AND "
            + Groups.AUTO_ADD + "=0 AND " + Groups.FAVORITES + "=0";

    public static final String ACTION_ADD_TO_GROUP = "addToGroup";
    public static final String ACTION_CREATE_GROUP = "createGroup";
    public static final String ACTION_DELETE_GROUP = "deleteGroup";
    public static final String ACTION_REMOVE_FROM_GROUP = "removeFromGroup";
    public static final String ACTION_SWITCH_GROUP = "switchGroup";
    public static final String ACTION_UPDATE_GROUP = "updateGroup";

    public static final int RESULT_GROUP_ADD_MEMBER = 100;
    public static final int RESULT_SEND_TO_SELECTION = 200;

    // System IDs of FFC groups in Google accounts
    private static final Set<String> FFC_GROUPS =
            new HashSet(Arrays.asList("Friends", "Family", "Coworkers"));

    private GroupUtil() {
    }

    /** Returns a {@link GroupListItem} read from the given cursor and position. */
    public static GroupListItem getGroupListItem(Cursor cursor, int position) {
        if (cursor == null || cursor.isClosed() || !cursor.moveToPosition(position)) {
            return null;
        }
        String accountName = cursor.getString(GroupListLoader.ACCOUNT_NAME);
        String accountType = cursor.getString(GroupListLoader.ACCOUNT_TYPE);
        String dataSet = cursor.getString(GroupListLoader.DATA_SET);
        long groupId = cursor.getLong(GroupListLoader.GROUP_ID);
        String title = cursor.getString(GroupListLoader.TITLE);
        int memberCount = cursor.getInt(GroupListLoader.MEMBER_COUNT);
        boolean isReadOnly = cursor.getInt(GroupListLoader.IS_READ_ONLY) == 1;
        String systemId = cursor.getString(GroupListLoader.SYSTEM_ID);

        // Figure out if this is the first group for this account name / account type pair by
        // checking the previous entry. This is to determine whether or not we need to display an
        // account header in this item.
        int previousIndex = position - 1;
        boolean isFirstGroupInAccount = true;
        if (previousIndex >= 0 && cursor.moveToPosition(previousIndex)) {
            String previousGroupAccountName = cursor.getString(GroupListLoader.ACCOUNT_NAME);
            String previousGroupAccountType = cursor.getString(GroupListLoader.ACCOUNT_TYPE);
            String previousGroupDataSet = cursor.getString(GroupListLoader.DATA_SET);

            if (TextUtils.equals(accountName, previousGroupAccountName)
                    && TextUtils.equals(accountType, previousGroupAccountType)
                    && TextUtils.equals(dataSet, previousGroupDataSet)) {
                isFirstGroupInAccount = false;
            }
        }

        return new GroupListItem(accountName, accountType, dataSet, groupId, title,
                isFirstGroupInAccount, memberCount, isReadOnly, systemId);
    }

    public static List<String> getSendToDataForIds(Context context, long[] ids, String scheme) {
        final List<String> items = new ArrayList<>();
        final String sIds = GroupUtil.convertArrayToString(ids);
        final String select = (ContactsUtils.SCHEME_MAILTO.equals(scheme)
                ? GroupMembersFragment.Query.EMAIL_SELECTION
                + " AND " + ContactsContract.CommonDataKinds.Email._ID + " IN (" + sIds + ")"
                : GroupMembersFragment.Query.PHONE_SELECTION
                + " AND " + ContactsContract.CommonDataKinds.Phone._ID + " IN (" + sIds + ")");
        final ContentResolver contentResolver = context.getContentResolver();
        final Cursor cursor = contentResolver.query(ContactsContract.Data.CONTENT_URI,
                ContactsUtils.SCHEME_MAILTO.equals(scheme)
                        ? GroupMembersFragment.Query.EMAIL_PROJECTION
                        : GroupMembersFragment.Query.PHONE_PROJECTION,
                select, null, null);

        if (cursor == null) {
            return items;
        }

        try {
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                final String data = cursor.getString(GroupMembersFragment.Query.DATA1);

                if (!TextUtils.isEmpty(data)) {
                    items.add(data);
                }
            }
        } finally {
            cursor.close();
        }

        return items;
    }

    /** Returns an Intent to send emails/phones to some activity/app */
    public static void startSendToSelectionActivity(
            Fragment fragment, String itemsList, String sendScheme, String title) {
        final Intent intent = new Intent(Intent.ACTION_SENDTO,
                Uri.fromParts(sendScheme, itemsList, null));
        fragment.startActivityForResult(
                Intent.createChooser(intent, title), RESULT_SEND_TO_SELECTION);
    }

    /** Returns an Intent to pick emails/phones to send to selection (or group) */
    public static Intent createSendToSelectionPickerIntent(Context context, long[] ids,
            long[] defaultSelection, String sendScheme, String title) {
        final Intent intent = new Intent(context, ContactSelectionActivity.class);
        intent.setAction(UiIntentActions.ACTION_SELECT_ITEMS);
        intent.setType(ContactsUtils.SCHEME_MAILTO.equals(sendScheme)
                ? ContactsContract.CommonDataKinds.Email.CONTENT_TYPE
                : ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        intent.putExtra(UiIntentActions.SELECTION_ITEM_LIST, ids);
        intent.putExtra(UiIntentActions.SELECTION_DEFAULT_SELECTION, defaultSelection);
        intent.putExtra(UiIntentActions.SELECTION_SEND_SCHEME, sendScheme);
        intent.putExtra(UiIntentActions.SELECTION_SEND_TITLE, title);

        return intent;
    }

    /** Returns an Intent to pick contacts to add to a group. */
    public static Intent createPickMemberIntent(Context context,
            GroupMetaData groupMetaData, ArrayList<String> memberContactIds) {
        final Intent intent = new Intent(context, ContactSelectionActivity.class);
        intent.setAction(Intent.ACTION_PICK);
        intent.setType(Groups.CONTENT_TYPE);
        intent.putExtra(UiIntentActions.GROUP_ACCOUNT_NAME, groupMetaData.accountName);
        intent.putExtra(UiIntentActions.GROUP_ACCOUNT_TYPE, groupMetaData.accountType);
        intent.putExtra(UiIntentActions.GROUP_ACCOUNT_DATA_SET, groupMetaData.dataSet);
        intent.putExtra(UiIntentActions.GROUP_CONTACT_IDS, memberContactIds);
        return intent;
    }

    public static String convertArrayToString(long[] list) {
        if (list == null || list.length == 0) return "";
        return Arrays.toString(list).replace("[", "").replace("]", "");
    }

    public static long[] convertLongSetToLongArray(Set<Long> set) {
        final Long[] contactIds = set.toArray(new Long[set.size()]);
        final long[] result = new long[contactIds.length];
        for (int i = 0; i < contactIds.length; i++) {
            result[i] = contactIds[i];
        }
        return result;
    }

    public static long[] convertStringSetToLongArray(Set<String> set) {
        final String[] contactIds = set.toArray(new String[set.size()]);
        final long[] result = new long[contactIds.length];
        for (int i = 0; i < contactIds.length; i++) {
            try {
                result[i] = Long.parseLong(contactIds[i]);
            } catch (NumberFormatException e) {
                result[i] = -1;
            }
        }
        return result;
    }

    /**
     * Returns true if it's an empty and read-only group and the system ID of
     * the group is one of "Friends", "Family" and "Coworkers".
     */
    public static boolean isEmptyFFCGroup(GroupListItem groupListItem) {
        return groupListItem.isReadOnly()
                && isSystemIdFFC(groupListItem.getSystemId())
                && (groupListItem.getMemberCount() <= 0);
    }

    private static boolean isSystemIdFFC(String systemId) {
        return !TextUtils.isEmpty(systemId) && FFC_GROUPS.contains(systemId);
    }

    /**
     * Returns true the URI is a group URI.
     */
    public static boolean isGroupUri(Uri uri) {
        return  uri != null && uri.toString().startsWith(Groups.CONTENT_URI.toString());
    }

    /**
     * Sort groups alphabetically and in a localized way.
     */
    public static String getGroupsSortOrder() {
        return Groups.TITLE + " COLLATE LOCALIZED ASC";
    }

    /**
     * The sum of the last element in counts[] and the last element in positions[] is the total
     * number of remaining elements in cursor. If count is more than what's in the indexer now,
     * then we don't need to trim.
     */
    public static boolean needTrimming(int count, int[] counts, int[] positions) {
        // The sum of the last element in counts[] and the last element in positions[] is
        // the total number of remaining elements in cursor. If mCount is more than
        // what's in the indexer now, then we don't need to trim.
        return positions.length > 0 && counts.length > 0
                && count <= (counts[counts.length - 1] + positions[positions.length - 1]);
    }

    /**
     * Update Bundle extras so as to update indexer.
     */
    public static void updateBundle(Bundle bundle, ContactsSectionIndexer indexer,
            List<Integer> subscripts, String[] sections, int[] counts) {
        for (int i : subscripts) {
            final int filteredContact = indexer.getSectionForPosition(i);
            if (filteredContact < counts.length && filteredContact >= 0) {
                counts[filteredContact]--;
                if (counts[filteredContact] == 0) {
                    sections[filteredContact] = "";
                }
            }
        }
        final String[] newSections = clearEmptyString(sections);
        bundle.putStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES, newSections);
        final int[] newCounts = clearZeros(counts);
        bundle.putIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS, newCounts);
    }

    private static String[] clearEmptyString(String[] strings) {
        final List<String> list = new ArrayList<>();
        for (String s : strings) {
            if (!TextUtils.isEmpty(s)) {
                list.add(s);
            }
        }
        return list.toArray(new String[list.size()]);
    }

    private static int[] clearZeros(int[] numbers) {
        final List<Integer> list = new ArrayList<>();
        for (int n : numbers) {
            if (n > 0) {
                list.add(n);
            }
        }
        final int[] array = new int[list.size()];
        for(int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    /**
     * Stores column ordering for the projection of a query of ContactsContract.Groups
     */
    public static final class GroupsProjection {
        public final int groupId;
        public final int title;
        public final int summaryCount;
        public final int systemId;
        public final int accountName;
        public final int accountType;
        public final int dataSet;
        public final int autoAdd;
        public final int favorites;
        public final int isReadOnly;
        public final int deleted;

        public GroupsProjection(Cursor cursor) {
            groupId = cursor.getColumnIndex(Groups._ID);
            title = cursor.getColumnIndex(Groups.TITLE);
            summaryCount = cursor.getColumnIndex(Groups.SUMMARY_COUNT);
            systemId = cursor.getColumnIndex(Groups.SYSTEM_ID);
            accountName = cursor.getColumnIndex(Groups.ACCOUNT_NAME);
            accountType = cursor.getColumnIndex(Groups.ACCOUNT_TYPE);
            dataSet = cursor.getColumnIndex(Groups.DATA_SET);
            autoAdd = cursor.getColumnIndex(Groups.AUTO_ADD);
            favorites = cursor.getColumnIndex(Groups.FAVORITES);
            isReadOnly = cursor.getColumnIndex(Groups.GROUP_IS_READ_ONLY);
            deleted = cursor.getColumnIndex(Groups.DELETED);
        }

        public GroupsProjection(String[] projection) {
            List<String> list = Arrays.asList(projection);
            groupId = list.indexOf(Groups._ID);
            title = list.indexOf(Groups.TITLE);
            summaryCount = list.indexOf(Groups.SUMMARY_COUNT);
            systemId = list.indexOf(Groups.SYSTEM_ID);
            accountName = list.indexOf(Groups.ACCOUNT_NAME);
            accountType = list.indexOf(Groups.ACCOUNT_TYPE);
            dataSet = list.indexOf(Groups.DATA_SET);
            autoAdd = list.indexOf(Groups.AUTO_ADD);
            favorites = list.indexOf(Groups.FAVORITES);
            isReadOnly = list.indexOf(Groups.GROUP_IS_READ_ONLY);
            deleted = list.indexOf(Groups.DELETED);
        }

        public String getTitle(Cursor cursor) {
            return cursor.getString(title);
        }

        public long getId(Cursor cursor) {
            return cursor.getLong(groupId);
        }

        public String getSystemId(Cursor cursor) {
            return cursor.getString(systemId);
        }

        public int getSummaryCount(Cursor cursor) {
            return cursor.getInt(summaryCount);
        }

        public boolean isEmptyFFCGroup(Cursor cursor) {
            if (accountType == -1 || isReadOnly == -1 ||
                    systemId == -1 || summaryCount == -1) {
                throw new IllegalArgumentException("Projection is missing required columns");
            }
            return GoogleAccountType.ACCOUNT_TYPE.equals(cursor.getString(accountType))
                    && cursor.getInt(isReadOnly) != 0
                    && isSystemIdFFC(cursor.getString(systemId))
                    && cursor.getInt(summaryCount) <= 0;
        }
    }
}
