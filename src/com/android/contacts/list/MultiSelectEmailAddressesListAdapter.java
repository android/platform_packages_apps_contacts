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
 */
package com.android.contacts.list;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.group.GroupUtil;
import com.android.contacts.preference.ContactsPreferences;

/** Email addresses multi-select cursor adapter. */
public class MultiSelectEmailAddressesListAdapter extends MultiSelectEntryContactListAdapter {

    protected static class EmailQuery {
        public static final String[] PROJECTION_PRIMARY = new String[] {
                Email._ID,                          // 0
                Email.TYPE,                         // 1
                Email.LABEL,                        // 2
                Email.ADDRESS,                      // 3
                Email.CONTACT_ID,                   // 4
                Email.LOOKUP_KEY,                   // 5
                Email.PHOTO_ID,                     // 6
                Email.DISPLAY_NAME_PRIMARY,         // 7
                Email.PHOTO_THUMBNAIL_URI,          // 8
        };

        public static final String[] PROJECTION_ALTERNATIVE = new String[] {
                Email._ID,                          // 0
                Email.TYPE,                         // 1
                Email.LABEL,                        // 2
                Email.ADDRESS,                      // 3
                Email.CONTACT_ID,                   // 4
                Email.LOOKUP_KEY,                   // 5
                Email.PHOTO_ID,                     // 6
                Email.DISPLAY_NAME_ALTERNATIVE,     // 7
                Email.PHOTO_THUMBNAIL_URI,          // 8
        };

        public static final int EMAIL_ID                = 0;
        public static final int EMAIL_TYPE              = 1;
        public static final int EMAIL_LABEL             = 2;
        public static final int EMAIL_ADDRESS           = 3;
        public static final int CONTACT_ID              = 4;
        public static final int LOOKUP_KEY              = 5;
        public static final int PHOTO_ID                = 6;
        public static final int DISPLAY_NAME            = 7;
        public static final int PHOTO_URI               = 8;
    }

    private final CharSequence mUnknownNameText;
    private long[] mContactIdsFilter = null;

    public MultiSelectEmailAddressesListAdapter(Context context) {
        super(context, EmailQuery.EMAIL_ID);

        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    public void setArguments(Bundle bundle) {
        mContactIdsFilter = bundle.getLongArray(UiIntentActions.SELECTION_ITEM_LIST);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        final Builder builder;
        if (isSearchMode()) {
            builder = Email.CONTENT_FILTER_URI.buildUpon();
            final String query = getQueryString();
            builder.appendPath(TextUtils.isEmpty(query) ? "" : query);
        } else {
            builder = Email.CONTENT_URI.buildUpon();
            if (isSectionHeaderDisplayEnabled()) {
                builder.appendQueryParameter(Email.EXTRA_ADDRESS_BOOK_INDEX, "true");
            }
        }
        builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                String.valueOf(directoryId));
        loader.setUri(builder.build());

        if (mContactIdsFilter != null) {
            loader.setSelection(ContactsContract.Data.CONTACT_ID
                    + " IN (" + GroupUtil.convertArrayToString(mContactIdsFilter) + ")");
        }

        if (getContactNameDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            loader.setProjection(EmailQuery.PROJECTION_PRIMARY);
        } else {
            loader.setProjection(EmailQuery.PROJECTION_ALTERNATIVE);
        }

        if (getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Email.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(Email.SORT_KEY_ALTERNATIVE);
        }
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(EmailQuery.DISPLAY_NAME);
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the current cursor position.
     */
    public Uri getDataUri(int position) {
        final long id = ((Cursor) getItem(position)).getLong(EmailQuery.EMAIL_ID);
        return ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id);
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = super.newView(context, partition, cursor, position, parent);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        final ContactListItemView view = (ContactListItemView)itemView;

        cursor.moveToPosition(position);
        boolean isFirstEntry = true;
        final long currentContactId = cursor.getLong(EmailQuery.CONTACT_ID);
        if (cursor.moveToPrevious() && !cursor.isBeforeFirst()) {
            final long previousContactId = cursor.getLong(EmailQuery.CONTACT_ID);
            if (currentContactId == previousContactId) {
                isFirstEntry = false;
            }
        }
        cursor.moveToPosition(position);

        bindViewId(view, cursor, EmailQuery.EMAIL_ID);
        if (isFirstEntry) {
            bindName(view, cursor);
            bindPhoto(view, cursor, EmailQuery.PHOTO_ID, EmailQuery.LOOKUP_KEY,
                    EmailQuery.DISPLAY_NAME);
        } else {
            unbindName(view);
            view.removePhotoView(true, false);
        }
        bindEmailAddress(view, cursor);
    }

    protected void unbindName(final ContactListItemView view) {
        view.hideDisplayName();
    }

    protected void bindEmailAddress(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(EmailQuery.EMAIL_TYPE)) {
            final int type = cursor.getInt(EmailQuery.EMAIL_TYPE);
            final String customLabel = cursor.getString(EmailQuery.EMAIL_LABEL);

            // TODO cache
            label = Email.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, EmailQuery.EMAIL_ADDRESS);
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, EmailQuery.DISPLAY_NAME, getContactNameDisplayOrder());
    }
}