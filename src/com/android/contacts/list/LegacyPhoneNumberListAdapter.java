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
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.view.View;
import android.view.ViewGroup;

/**
 * A cursor adapter for the Phones.CONTENT_TYPE content type.
 */
@SuppressWarnings("deprecation")
public class LegacyPhoneNumberListAdapter extends ContactEntryListAdapter {

    private static final String[] PHONES_PROJECTION = new String[] {
        Phones._ID,             // 0
        Phones.TYPE,            // 1
        Phones.LABEL,           // 2
        Phones.NUMBER,          // 3
        People.DISPLAY_NAME,    // 4
        People.PHONETIC_NAME,   // 5
    };

    private static final int PHONE_ID_COLUMN_INDEX = 0;
    private static final int PHONE_TYPE_COLUMN_INDEX = 1;
    private static final int PHONE_LABEL_COLUMN_INDEX = 2;
    private static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    private static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;
    private static final int PHONE_PHONETIC_NAME_COLUMN_INDEX = 5;

    private CharSequence mUnknownNameText;

    public LegacyPhoneNumberListAdapter(Context context) {
        super(context);
        mUnknownNameText = context.getText(android.R.string.unknownName);
    }

    @Override
    public void configureLoader(CursorLoader loader, long directoryId) {
        loader.setUri(Phones.CONTENT_URI);
        loader.setProjection(PHONES_PROJECTION);
        loader.setSortOrder(Phones.DISPLAY_NAME);
    }

    @Override
    public String getContactDisplayName(int position) {
        return ((Cursor)getItem(position)).getString(PHONE_DISPLAY_NAME_COLUMN_INDEX);
    }

    public Uri getPhoneUri(int position) {
        Cursor cursor = ((Cursor)getItem(position));
        long id = cursor.getLong(PHONE_ID_COLUMN_INDEX);
        return ContentUris.withAppendedId(Phones.CONTENT_URI, id);
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view =
                super.newView(context, partition, cursor, position, parent);
        view.setUnknownNameText(mUnknownNameText);
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        ContactListItemView view = (ContactListItemView)itemView;
        bindName(view, cursor);
        bindViewId(view, cursor, PHONE_ID_COLUMN_INDEX);
        bindPhoneNumber(view, cursor);
    }

    protected void bindName(final ContactListItemView view, Cursor cursor) {
        view.showDisplayName(cursor, PHONE_DISPLAY_NAME_COLUMN_INDEX, getContactNameDisplayOrder());
        view.showPhoneticName(cursor, PHONE_PHONETIC_NAME_COLUMN_INDEX);
    }

    protected void bindPhoneNumber(ContactListItemView view, Cursor cursor) {
        CharSequence label = null;
        if (!cursor.isNull(PHONE_TYPE_COLUMN_INDEX)) {
            final int type = cursor.getInt(PHONE_TYPE_COLUMN_INDEX);
            final String customLabel = cursor.getString(PHONE_LABEL_COLUMN_INDEX);

            // TODO cache
            label = Phone.getTypeLabel(getContext().getResources(), type, customLabel);
        }
        view.setLabel(label);
        view.setPhoneNumber(cursor.getString(PHONE_NUMBER_COLUMN_INDEX), /* countryIso */ null);
    }
}
