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
 * limitations under the License
 */
package com.android.contacts;

import android.content.Context;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.ContactsContract.Groups;

import com.android.contacts.group.GroupUtil;

/**
 * Group meta-data loader. Loads all groups or just a single group from the
 * database (if given a {@link Uri}).
 */
public final class GroupMetaDataLoader extends CursorLoader {

    public final static String[] COLUMNS = new String[] {
        Groups.ACCOUNT_NAME,
        Groups.ACCOUNT_TYPE,
        Groups.DATA_SET,
        Groups._ID,
        Groups.TITLE,
        Groups.AUTO_ADD,
        Groups.FAVORITES,
        Groups.GROUP_IS_READ_ONLY,
        Groups.DELETED,
    };

    public final static int ACCOUNT_NAME = 0;
    public final static int ACCOUNT_TYPE = 1;
    public final static int DATA_SET = 2;
    public final static int GROUP_ID = 3;
    public final static int TITLE = 4;
    public final static int AUTO_ADD = 5;
    public final static int FAVORITES = 6;
    public final static int IS_READ_ONLY = 7;
    public final static int DELETED = 8;

    public GroupMetaDataLoader(Context context, Uri groupUri) {
        super(context, ensureIsGroupUri(groupUri), COLUMNS, GroupUtil.DEFAULT_SELECTION, null,
                GroupUtil.getGroupsSortOrder());
    }

    public GroupMetaDataLoader(Context context, Uri groupUri, String selection) {
        super(context, ensureIsGroupUri(groupUri), COLUMNS, selection, null,
                GroupUtil.getGroupsSortOrder());
    }

    /**
     * Ensures that this is a valid group URI. If invalid, then an exception is
     * thrown. Otherwise, the original URI is returned.
     */
    private static Uri ensureIsGroupUri(final Uri groupUri) {
        // TODO: Fix ContactsProvider2 getType method to resolve the group Uris
        if (groupUri == null) {
            throw new IllegalArgumentException("Uri must not be null");
        }
        if (!GroupUtil.isGroupUri(groupUri)) {
            throw new IllegalArgumentException("Invalid group Uri: " + groupUri);
        }
        return groupUri;
    }
}
