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
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.view.View;
import android.view.ViewGroup;

/**
 * A cursor adapter for the ContactMethods.CONTENT_TYPE content type.
 */
@SuppressWarnings("deprecation")
public class LegacyPostalAddressListAdapter extends ContactEntryListAdapter {

    static final String[] POSTALS_PROJECTION = new String[] {
        ContactMethods._ID,     // 0
        ContactMethods.TYPE,    // 1
        ContactMethods.LABEL,   // 2
        ContactMethods.DATA,    // 3
        People.DISPLAY_NAME,    // 4
        People.PHONETIC_NAME,   // 5
    };

    public static final int POSTAL_ID_COLUMN_INDEX = 0;
    public static final int POSTAL_TYPE_COLUMN_INDEX = 1;
    public static final int POSTAL_LABEL_COLUMN_INDEX = 2;
    public static final int POSTAL_NUMBER_COLUMN_INDEX = 3;
    public static final int POSTAL_DISPLAY_NAME_COLUMN_INDEX = 4;
    public static final int POSTAL_PHONETIC_NAME_COLUMN_INDEX = 5;

    private CharSequence mUnknownNameText;

    public LegacyPostalAddressListAdapter(Context context) {
        super(context);
        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        loader.setUri(ContactMethods.CONTENT_URI);
        loader.setProjection(POSTALS_PROJECTION);
        loader.setSortOrder(People.DISPLAY_NAME);
        loader.setSelection(ContactMethods.KIND + "=" + android.provider.Contacts.KIND_POSTAL);
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor)getItem(position)).getString(POSTAL_DISPLAY_NAME_COLUMN_INDEX);
    }

    public Uri getContactMethodUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        long id = cursor.getLong(POSTAL_ID_COLUMN_INDEX);
        return ContentUris.withAppendedId(ContactMethods.CONTENT_URI, id);
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setUnknownNameText(mUnknownNameText);
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        ContactListItemView view = (ContactListItemView)itemView;
        bindName(view, cursor);
        bindViewId(view, cursor, POSTAL_ID_COLUMN_INDEX);
        bindPostalAddress(view, cursor);
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, POSTAL_DISPLAY_NAME_COLUMN_INDEX,
                getContactNameDisplayOrder());
        view.showPhoneticName(cursor, POSTAL_PHONETIC_NAME_COLUMN_INDEX);
    }

    protected void bindPostalAddress(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(POSTAL_TYPE_COLUMN_INDEX)) {
            final int type = cursor.getInt(POSTAL_TYPE_COLUMN_INDEX);
            final String customLabel = cursor.getString(POSTAL_LABEL_COLUMN_INDEX);

            // TODO cache
            label = StructuredPostal.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.showData(cursor, POSTAL_NUMBER_COLUMN_INDEX);
    }
}
