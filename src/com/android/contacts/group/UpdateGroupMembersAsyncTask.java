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

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;

/**
 * Starts an Intent to add/remove the raw contacts for the given contact IDs to/from a group.
 * Only the raw contacts that belong to the specified account are added or removed.
 */
public class UpdateGroupMembersAsyncTask extends AsyncTask<Void, Void, Intent> {
    static final int TYPE_ADD = 0;
    static final int TYPE_REMOVE = 1;

    private final Context mContext;
    private final int mType;
    private final long[] mContactIds;
    private final long mGroupId;
    private final String mAccountName;
    private final String mAccountType;
    private final String mDataSet;

    public UpdateGroupMembersAsyncTask(int type, Context context, long[] contactIds,
            long groupId, String accountName, String accountType, String dataSet) {
        mContext = context;
        mType = type;
        mContactIds = contactIds;
        mGroupId = groupId;
        mAccountName = accountName;
        mAccountType = accountType;
        mDataSet = dataSet;
    }

    @Override
    protected Intent doInBackground(Void... params) {
        final long[] rawContactIds = getRawContactIds();
        if (rawContactIds.length == 0) {
            return null;
        }
        final long[] rawContactIdsToAdd;
        final long[] rawContactIdsToRemove;
        final String action;
        if (mType == TYPE_ADD) {
            rawContactIdsToAdd = rawContactIds;
            rawContactIdsToRemove = null;
            action = GroupUtil.ACTION_ADD_TO_GROUP;
        } else if (mType == TYPE_REMOVE) {
            rawContactIdsToAdd = null;
            rawContactIdsToRemove = rawContactIds;
            action = GroupUtil.ACTION_REMOVE_FROM_GROUP;
        } else {
            throw new IllegalStateException("Unrecognized type " + mType);
        }
        return ContactSaveService.createGroupUpdateIntent(
                mContext, mGroupId, /* newLabel */ null, rawContactIdsToAdd,
                rawContactIdsToRemove, PeopleActivity.class, action);
    }

    // TODO(wjang): prune raw contacts that are already in the group; ContactSaveService will
    // log a warning if the raw contact is already a member and keep going but it is not ideal.
    private long[] getRawContactIds() {
        final Uri.Builder builder = RawContacts.CONTENT_URI.buildUpon();
        // null account names are not valid, see ContactsProvider2#appendAccountFromParameter
        if (mAccountName != null) {
            builder.appendQueryParameter(RawContacts.ACCOUNT_NAME, mAccountName);
            builder.appendQueryParameter(RawContacts.ACCOUNT_TYPE, mAccountType);
        }
        if (mDataSet != null) {
            builder.appendQueryParameter(RawContacts.DATA_SET, mDataSet);
        }
        final Uri rawContactUri = builder.build();
        final String[] projection = new String[]{ContactsContract.RawContacts._ID};
        final StringBuilder selection = new StringBuilder();
        final String[] selectionArgs = new String[mContactIds.length];
        for (int i = 0; i < mContactIds.length; i++) {
            if (i > 0) {
                selection.append(" OR ");
            }
            selection.append(ContactsContract.RawContacts.CONTACT_ID).append("=?");
            selectionArgs[i] = Long.toString(mContactIds[i]);
        }
        final Cursor cursor = mContext.getContentResolver().query(
                rawContactUri, projection, selection.toString(), selectionArgs, null, null);
        final long[] rawContactIds = new long[cursor.getCount()];
        try {
            int i = 0;
            while (cursor.moveToNext()) {
                rawContactIds[i] = cursor.getLong(0);
                i++;
            }
        } finally {
            cursor.close();
        }
        return rawContactIds;
    }

    @Override
    protected void onPostExecute(Intent intent) {
        if (intent == null) {
            Toast.makeText(mContext, R.string.groupSavedErrorToast, Toast.LENGTH_SHORT).show();
        } else {
            mContext.startService(intent);
        }
    }
}
