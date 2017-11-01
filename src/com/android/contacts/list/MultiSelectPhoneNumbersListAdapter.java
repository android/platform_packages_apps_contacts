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
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.group.GroupUtil;
import com.android.contacts.preference.ContactsPreferences;

/** Phone Numbers multi-select cursor adapter. */
public class MultiSelectPhoneNumbersListAdapter extends MultiSelectEntryContactListAdapter {

    public static class PhoneQuery {
        public static final String[] PROJECTION_PRIMARY = new String[] {
                Phone._ID,                          // 0
                Phone.TYPE,                         // 1
                Phone.LABEL,                        // 2
                Phone.NUMBER,                       // 3
                Phone.CONTACT_ID,                   // 4
                Phone.LOOKUP_KEY,                   // 5
                Phone.PHOTO_ID,                     // 6
                Phone.DISPLAY_NAME_PRIMARY,         // 7
                Phone.PHOTO_THUMBNAIL_URI,          // 8
        };

        public static final String[] PROJECTION_ALTERNATIVE = new String[] {
                Phone._ID,                          // 0
                Phone.TYPE,                         // 1
                Phone.LABEL,                        // 2
                Phone.NUMBER,                       // 3
                Phone.CONTACT_ID,                   // 4
                Phone.LOOKUP_KEY,                   // 5
                Phone.PHOTO_ID,                     // 6
                Phone.DISPLAY_NAME_ALTERNATIVE,     // 7
                Phone.PHOTO_THUMBNAIL_URI,          // 8
        };

        public static final int PHONE_ID                = 0;
        public static final int PHONE_TYPE              = 1;
        public static final int PHONE_LABEL             = 2;
        public static final int PHONE_NUMBER            = 3;
        public static final int CONTACT_ID              = 4;
        public static final int LOOKUP_KEY              = 5;
        public static final int PHOTO_ID                = 6;
        public static final int DISPLAY_NAME            = 7;
        public static final int PHOTO_URI               = 8;
    }

    private final CharSequence mUnknownNameText;
    private long[] mContactIdsFilter = null;

    public MultiSelectPhoneNumbersListAdapter(Context context) {
        super(context, PhoneQuery.PHONE_ID);

        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    public void setArguments(Bundle bundle) {
        mContactIdsFilter = bundle.getLongArray(UiIntentActions.SELECTION_ITEM_LIST);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        final Builder builder;
        if (isSearchMode()) {
            builder = Phone.CONTENT_FILTER_URI.buildUpon();
            final String query = getQueryString();
            builder.appendPath(TextUtils.isEmpty(query) ? "" : query);
        } else {
            builder = Phone.CONTENT_URI.buildUpon();
            if (isSectionHeaderDisplayEnabled()) {
                builder.appendQueryParameter(Phone.EXTRA_ADDRESS_BOOK_INDEX, "true");
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
            loader.setProjection(PhoneQuery.PROJECTION_PRIMARY);
        } else {
            loader.setProjection(PhoneQuery.PROJECTION_ALTERNATIVE);
        }

        if (getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Phone.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(Phone.SORT_KEY_ALTERNATIVE);
        }
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(PhoneQuery.DISPLAY_NAME);
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the current cursor position.
     */
    public Uri getDataUri(int position) {
        final long id = ((Cursor) getItem(position)).getLong(PhoneQuery.PHONE_ID);
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
        final long currentContactId = cursor.getLong(PhoneQuery.CONTACT_ID);
        if (cursor.moveToPrevious() && !cursor.isBeforeFirst()) {
            final long previousContactId = cursor.getLong(PhoneQuery.CONTACT_ID);
            if (currentContactId == previousContactId) {
                isFirstEntry = false;
            }
        }
        cursor.moveToPosition(position);

        bindViewId(view, cursor, PhoneQuery.PHONE_ID);
        if (isFirstEntry) {
            bindName(view, cursor);
            bindPhoto(view, cursor, PhoneQuery.PHOTO_ID, PhoneQuery.LOOKUP_KEY,
                PhoneQuery.DISPLAY_NAME);
        } else {
            unbindName(view);
            view.removePhotoView(true, false);
        }
        bindPhoneNumber(view, cursor);
    }

    protected void unbindName(final ContactListItemView view) {
        view.hideDisplayName();
    }

    protected void bindPhoneNumber(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(PhoneQuery.PHONE_TYPE)) {
            final int type = cursor.getInt(PhoneQuery.PHONE_TYPE);
            final String customLabel = cursor.getString(PhoneQuery.PHONE_LABEL);

            // TODO cache
            label = Phone.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, PhoneQuery.PHONE_NUMBER);
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, PhoneQuery.DISPLAY_NAME, getContactNameDisplayOrder());
    }
}
