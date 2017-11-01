/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.contacts;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.RawContacts;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * A list view for constituent contacts of an aggregate.  Shows the contact name, source icon
 * and additional data such as a nickname, email address or phone number, whichever
 * is available.
 */
public class SplitAggregateView extends ListView {

    private static final String TAG = "SplitAggregateView";

    private interface SplitQuery {
        String[] COLUMNS = new String[] {
                Data.MIMETYPE, RawContacts.ACCOUNT_TYPE, RawContacts.DATA_SET, Data.RAW_CONTACT_ID,
                Data.IS_PRIMARY, StructuredName.DISPLAY_NAME, Nickname.NAME, Email.DATA,
                Phone.NUMBER
        };

        int MIMETYPE = 0;
        int ACCOUNT_TYPE = 1;
        int DATA_SET = 2;
        int RAW_CONTACT_ID = 3;
        int IS_PRIMARY = 4;
        int DISPLAY_NAME = 5;
        int NICKNAME = 6;
        int EMAIL = 7;
        int PHONE = 8;
    }

    private final Uri mAggregateUri;
    private OnContactSelectedListener mListener;
    private AccountTypeManager mAccountTypes;

    /**
     * Listener interface that gets the contact ID of the user-selected contact.
     */
    public interface OnContactSelectedListener {
        void onContactSelected(long rawContactId);
    }

    /**
     * Constructor.
     */
    public SplitAggregateView(Context context, Uri aggregateUri) {
        super(context);

        mAggregateUri = aggregateUri;

        mAccountTypes = AccountTypeManager.getInstance(context);

        final List<RawContactInfo> list = loadData();

        setAdapter(new SplitAggregateAdapter(context, list));
        setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mListener.onContactSelected(list.get(position).rawContactId);
            }
        });
    }

    /**
     * Sets a contact selection listener.
     */
    public void setOnContactSelectedListener(OnContactSelectedListener listener) {
        mListener = listener;
    }

    /**
     * Contact information loaded from the content provider.
     */
    private static class RawContactInfo implements Comparable<RawContactInfo> {
        final long rawContactId;
        String accountType;
        String dataSet;
        String name;
        String phone;
        String email;
        String nickname;

        public RawContactInfo(long rawContactId) {
            this.rawContactId = rawContactId;
        }

        public String getAdditionalData() {
            if (nickname != null) {
                return nickname;
            }

            if (email != null) {
                return email;
            }

            if (phone != null) {
                return phone;
            }

            return "";
        }

        public int compareTo(RawContactInfo another) {
            String thisAccount = accountType != null ? accountType : "";
            String thatAccount = another.accountType != null ? another.accountType : "";
            return thisAccount.compareTo(thatAccount);
        }
    }

    /**
     * Loads data from the content provider, organizes it into {@link RawContactInfo} objects
     * and returns a sorted list of {@link RawContactInfo}'s.
     */
    private List<RawContactInfo> loadData() {
        HashMap<Long, RawContactInfo> rawContactInfos = new HashMap<Long, RawContactInfo>();
        Uri dataUri = Uri.withAppendedPath(mAggregateUri, Data.CONTENT_DIRECTORY);
        Cursor cursor = getContext().getContentResolver().query(dataUri,
                SplitQuery.COLUMNS, null, null, null);
        if (cursor == null) {
            return Collections.emptyList();
        }
        try {
            while (cursor.moveToNext()) {
                long rawContactId = cursor.getLong(SplitQuery.RAW_CONTACT_ID);
                RawContactInfo info = rawContactInfos.get(rawContactId);
                if (info == null) {
                    info = new RawContactInfo(rawContactId);
                    rawContactInfos.put(rawContactId, info);
                    info.accountType = cursor.getString(SplitQuery.ACCOUNT_TYPE);
                    info.dataSet = cursor.getString(SplitQuery.DATA_SET);
                }

                String mimetype = cursor.getString(SplitQuery.MIMETYPE);
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadStructuredName(cursor, info);
                } else if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadPhoneNumber(cursor, info);
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadEmail(cursor, info);
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    loadNickname(cursor, info);
                }
            }
        } finally {
            cursor.close();
        }

        List<RawContactInfo> list = new ArrayList<RawContactInfo>(rawContactInfos.values());
        Collections.sort(list);
        return list;
    }

    private void loadStructuredName(Cursor cursor, RawContactInfo info) {
        info.name = cursor.getString(SplitQuery.DISPLAY_NAME);
    }

    private void loadNickname(Cursor cursor, RawContactInfo info) {
        if (info.nickname == null || cursor.getInt(SplitQuery.IS_PRIMARY) != 0) {
            info.nickname = cursor.getString(SplitQuery.NICKNAME);
        }
    }

    private void loadEmail(Cursor cursor, RawContactInfo info) {
        if (info.email == null || cursor.getInt(SplitQuery.IS_PRIMARY) != 0) {
            info.email = cursor.getString(SplitQuery.EMAIL);
        }
    }

    private void loadPhoneNumber(Cursor cursor, RawContactInfo info) {
        if (info.phone == null || cursor.getInt(SplitQuery.IS_PRIMARY) != 0) {
            info.phone = cursor.getString(SplitQuery.PHONE);
        }
    }

    private static class SplitAggregateItemCache  {
        TextView name;
        TextView additionalData;
        ImageView sourceIcon;
    }

    /**
     * List adapter for the list of {@link RawContactInfo} objects.
     */
    private class SplitAggregateAdapter extends ArrayAdapter<RawContactInfo> {

        private LayoutInflater mInflater;

        public SplitAggregateAdapter(Context context, List<RawContactInfo> sources) {
            super(context, 0, sources);
            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.split_aggregate_list_item, parent, false);
            }

            SplitAggregateItemCache cache = (SplitAggregateItemCache)convertView.getTag();
            if (cache == null) {
                cache = new SplitAggregateItemCache();
                cache.name = (TextView)convertView.findViewById(R.id.name);
                cache.additionalData = (TextView)convertView.findViewById(R.id.additionalData);
                cache.sourceIcon = (ImageView)convertView.findViewById(R.id.sourceIcon);
                convertView.setTag(cache);
            }

            final RawContactInfo info = getItem(position);
            cache.name.setText(info.name);
            cache.additionalData.setText(info.getAdditionalData());

            Drawable icon = null;
            AccountType accountType = mAccountTypes.getAccountType(info.accountType, info.dataSet);
            if (accountType != null) {
                icon = accountType.getDisplayIcon(getContext());
            }
            if (icon != null) {
                cache.sourceIcon.setImageDrawable(icon);
            } else {
                cache.sourceIcon.setImageResource(R.drawable.unknown_source);
            }
            return convertView;
        }
    }
}
