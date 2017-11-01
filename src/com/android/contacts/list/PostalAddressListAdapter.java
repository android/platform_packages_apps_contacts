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
package com.android.contacts.list;

import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Data;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.preference.ContactsPreferences;

/**
 * A cursor adapter for the {@link StructuredPostal#CONTENT_TYPE} content type.
 */
public class PostalAddressListAdapter extends ContactEntryListAdapter {

    protected static class PostalQuery {
        private static final String[] PROJECTION_PRIMARY = new String[] {
            StructuredPostal._ID,                       // 0
            StructuredPostal.TYPE,                      // 1
            StructuredPostal.LABEL,                     // 2
            StructuredPostal.DATA,                      // 3
            StructuredPostal.PHOTO_ID,                  // 4
            StructuredPostal.LOOKUP_KEY,                // 5
            StructuredPostal.DISPLAY_NAME_PRIMARY,      // 6
        };

        private static final String[] PROJECTION_ALTERNATIVE = new String[] {
            StructuredPostal._ID,                       // 0
            StructuredPostal.TYPE,                      // 1
            StructuredPostal.LABEL,                     // 2
            StructuredPostal.DATA,                      // 3
            StructuredPostal.PHOTO_ID,                  // 4
            StructuredPostal.LOOKUP_KEY,                // 5
            StructuredPostal.DISPLAY_NAME_ALTERNATIVE,  // 6
        };

        public static final int POSTAL_ID           = 0;
        public static final int POSTAL_TYPE         = 1;
        public static final int POSTAL_LABEL        = 2;
        public static final int POSTAL_ADDRESS      = 3;
        public static final int POSTAL_PHOTO_ID     = 4;
        public static final int POSTAL_LOOKUP_KEY   = 5;
        public static final int POSTAL_DISPLAY_NAME = 6;
    }

    private final CharSequence mUnknownNameText;

    public PostalAddressListAdapter(Context context) {
        super(context);

        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        final Builder builder = StructuredPostal.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true");
        if (isSectionHeaderDisplayEnabled()) {
            builder.appendQueryParameter(StructuredPostal.EXTRA_ADDRESS_BOOK_INDEX, "true");
        }
        loader.setUri(builder.build());

        if (getContactNameDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            loader.setProjection(PostalQuery.PROJECTION_PRIMARY);
        } else {
            loader.setProjection(PostalQuery.PROJECTION_ALTERNATIVE);
        }

        if (getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(StructuredPostal.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(StructuredPostal.SORT_KEY_ALTERNATIVE);
        }
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor) getItem(position)).getString(PostalQuery.POSTAL_DISPLAY_NAME);
    }

    /**
     * Builds a {@link Data#CONTENT_URI} for the current cursor
     * position.
     */
    public Uri getDataUri(int position) {
        long id = ((Cursor)getItem(position)).getLong(PostalQuery.POSTAL_ID);
        return ContentUris.withAppendedId(Data.CONTENT_URI, id);
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        ContactListItemView view = super.newView(context, partition, cursor, position, parent);
        view.setUnknownNameText(mUnknownNameText);
        view.setQuickContactEnabled(isQuickContactEnabled());
        view.setIsSectionHeaderEnabled(isSectionHeaderDisplayEnabled());
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        ContactListItemView view = (ContactListItemView)itemView;
        bindSectionHeaderAndDivider(view, position);
        bindName(view, cursor);
        bindViewId(view, cursor, PostalQuery.POSTAL_ID);
        bindPhoto(view, cursor);
        bindPostalAddress(view, cursor);
    }

    protected void bindPostalAddress(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(PostalQuery.POSTAL_TYPE)) {
            final int type = cursor.getInt(PostalQuery.POSTAL_TYPE);
            final String customLabel = cursor.getString(PostalQuery.POSTAL_LABEL);

            // TODO cache
            label = StructuredPostal.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, PostalQuery.POSTAL_ADDRESS);
    }

    protected void bindSectionHeaderAndDivider(final ContactListItemView view, int position) {
        final int section = getSectionForPosition(position);
        if (getPositionForSection(section) == position) {
            String title = (String)getSections()[section];
            view.setSectionHeader(title);
        } else {
            view.setSectionHeader(null);
        }
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, PostalQuery.POSTAL_DISPLAY_NAME, getContactNameDisplayOrder());
    }

    protected void bindPhoto(final ContactListItemView view, Cursor cursor) {
        long photoId = 0;
        if (!cursor.isNull(PostalQuery.POSTAL_PHOTO_ID)) {
            photoId = cursor.getLong(PostalQuery.POSTAL_PHOTO_ID);
        }

        DefaultImageRequest request = null;
        if (photoId == 0) {
            request = getDefaultImageRequestFromCursor(cursor, PostalQuery.POSTAL_DISPLAY_NAME,
                    PostalQuery.POSTAL_LOOKUP_KEY);
        }

        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, getCircularPhotos(),
                request);
    }
//
//    protected void bindSearchSnippet(final ContactListItemView view, Cursor cursor) {
//        view.showSnippet(cursor, SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX,
//                SUMMARY_SNIPPET_DATA1_COLUMN_INDEX, SUMMARY_SNIPPET_DATA4_COLUMN_INDEX);
//    }

}
