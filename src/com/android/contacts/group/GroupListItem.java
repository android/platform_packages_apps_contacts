/*
 * Copyright (C) 2011 The Android Open Source Project
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

/**
 * Meta-data for a contact group.  We load all groups associated with the contact's
 * constituent accounts.
 */
public final class GroupListItem {
    private final String mAccountName;
    private final String mAccountType;
    private final String mDataSet;
    private final long mGroupId;
    private final String mTitle;
    private final boolean mIsFirstGroupInAccount;
    private final int mMemberCount;
    private final boolean mIsReadOnly;
    private final String mSystemId;

    public GroupListItem(String accountName, String accountType, String dataSet, long groupId,
            String title, boolean isFirstGroupInAccount, int memberCount, boolean isReadOnly,
            String systemId) {
        mAccountName = accountName;
        mAccountType = accountType;
        mDataSet = dataSet;
        mGroupId = groupId;
        mTitle = title;
        mIsFirstGroupInAccount = isFirstGroupInAccount;
        mMemberCount = memberCount;
        mIsReadOnly = isReadOnly;
        mSystemId = systemId;
    }

    public String getAccountName() {
        return mAccountName;
    }

    public String getAccountType() {
        return mAccountType;
    }

    public String getDataSet() {
        return mDataSet;
    }

    public long getGroupId() {
        return mGroupId;
    }

    public String getTitle() {
        return mTitle;
    }

    public int getMemberCount() {
        return mMemberCount;
    }

    public boolean hasMemberCount() {
        return mMemberCount != -1;
    }

    public boolean isFirstGroupInAccount() {
        return mIsFirstGroupInAccount;
    }

    public boolean isReadOnly() {
        return mIsReadOnly;
    }

    public String getSystemId() {
        return mSystemId;
    }
}