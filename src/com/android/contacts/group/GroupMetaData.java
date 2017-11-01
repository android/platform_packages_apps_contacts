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
 * limitations under the License
 */
package com.android.contacts.group;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.text.TextUtils;

import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;

import com.google.common.base.MoreObjects;

/** Meta data for a contact group. */
public final class GroupMetaData implements Parcelable {

    public static final Creator<GroupMetaData> CREATOR = new Creator<GroupMetaData>() {

        public GroupMetaData createFromParcel(Parcel in) {
            return new GroupMetaData(in);
        }

        public GroupMetaData[] newArray(int size) {
            return new GroupMetaData[size];
        }
    };

    public final Uri uri;
    public final String accountName;
    public final String accountType;
    public final String dataSet;
    public final long groupId;
    public final String groupName;
    public final boolean readOnly;
    public final boolean defaultGroup;
    public final boolean favorites;
    public final boolean editable;

    /**
     * @param cursor Cursor loaded with {@link GroupMetaDataLoader#COLUMNS} as the projection.
     */
    public GroupMetaData(Context context, Cursor cursor) {
        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(context);
        final long groupId = cursor.getLong(GroupMetaDataLoader.GROUP_ID);
        final Uri groupUri = ContentUris.withAppendedId(
                ContactsContract.Groups.CONTENT_URI, groupId);
        final AccountType accountType = accountTypeManager.getAccountType(
                cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE),
                cursor.getString(GroupMetaDataLoader.DATA_SET));
        final boolean editable = accountType == null
                ? false : accountType.isGroupMembershipEditable();

        this.uri = groupUri;
        this.accountName = cursor.getString(GroupMetaDataLoader.ACCOUNT_NAME);
        this.accountType = cursor.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
        this.dataSet = cursor.getString(GroupMetaDataLoader.DATA_SET);
        this.groupId = groupId;
        this.groupName = cursor.getString(GroupMetaDataLoader.TITLE);
        this.readOnly = getBoolean(cursor, GroupMetaDataLoader.IS_READ_ONLY);
        this.defaultGroup = getBoolean(cursor, GroupMetaDataLoader.AUTO_ADD);
        this.favorites = getBoolean(cursor, GroupMetaDataLoader.FAVORITES);
        this.editable = editable;
    }

    private static boolean getBoolean(Cursor cursor, int columnIndex) {
         return cursor.isNull(columnIndex) ? false : cursor.getInt(columnIndex) != 0;
    }

    private GroupMetaData(Parcel source) {
        uri = source.readParcelable(Uri.class.getClassLoader());
        accountName = source.readString();
        accountType = source.readString();
        dataSet = source.readString();
        groupId = source.readLong();
        groupName = source.readString();
        readOnly = source.readInt() == 1;
        defaultGroup = source.readInt() == 1;
        favorites = source.readInt() == 1;
        editable = source.readInt() == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(uri, 0);
        dest.writeString(accountName);
        dest.writeString(accountType);
        dest.writeString(dataSet);
        dest.writeLong(groupId);
        dest.writeString(groupName);
        dest.writeInt(readOnly ? 1 : 0);
        dest.writeInt(defaultGroup ? 1 : 0);
        dest.writeInt(favorites ? 1 : 0);
        dest.writeInt(editable ? 1 : 0);
    }

    /** Whether all metadata fields are set. */
    public boolean isValid() {
        return uri != null
                && !TextUtils.isEmpty(accountName)
                && !TextUtils.isEmpty(groupName)
                && groupId > 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("accountName", accountName)
                .add("accountType", accountType)
                .add("dataSet", dataSet)
                .add("groupId", groupId)
                .add("groupName", groupName)
                .add("readOnly", readOnly)
                .add("defaultGroup", defaultGroup)
                .add("favorites", favorites)
                .add("editable", editable)
                .add("isValid", isValid())
                .toString();
    }
}