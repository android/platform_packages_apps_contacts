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
package com.android.contacts;

import android.app.Activity;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.SpeedDial;
import android.provider.ContactsContract.Intents.UI;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.android.internal.widget.ContactHeaderWidget;

import java.util.ArrayList;

/**
 * Displays the details of a specific contact.
 * User select the phone number to assign to speed dial
 */
public class SpeedDialViewContactActivity extends Activity {

    // List to hold contact name
    private ArrayList<String> mPhoneNumber = null;

    //List to hold phone type
    private ArrayList<String> mPhoneType = null;

    // List to hold phone ID
    private ArrayList<String> mPhoneId = null;

    // List to hold phone defualt number
    private ArrayList<Integer> mIsPrimary = null;

    // Creates header widget for contacts
    protected ContactHeaderWidget mContactHeaderWidget;

    // Default phone number value.
    private static final int DEFAULT_NUMBER = 1;

    protected Uri mLookupUri;

    private ListView mListView;
    /**
     * onCreate called when the Activity is created.
     * @param aSavedInstanceState Bundle which maintains the Activity state.
     */
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.contact_card_layoutspeeddial);
        mContactHeaderWidget = (ContactHeaderWidget) findViewById(R.id.contact_header_widget);

        mListView = (ListView) findViewById(R.id.contact_data);

    }
    /**
     * onResume called when the SpeedDialGrid activity comes in the foreground.
     */
    protected void onResume() {

        super.onResume();

        Intent intent = getIntent();
        String contactId = intent.getStringExtra(UI.SPEED_DIAL_CONTACT_ID);

        //Get Contacts Content URI for a given contact id
        Uri baseUri =
                ContentUris.withAppendedId(Contacts.CONTENT_URI,Integer.valueOf(contactId));
        Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);
        mLookupUri = dataUri;
        mContactHeaderWidget.bindFromContactLookupUri(mLookupUri);

        //Get phone number and phone type for a given contact id
        getPhoneTypeAndNumber(contactId);

        SpeedDialViewContactActivity.SpeedDialAdapter viewNumberAdapter = new SpeedDialAdapter();
        mListView.setAdapter(viewNumberAdapter);

        //Listener for list item is clicked.
        mListView.setOnItemClickListener(new OnItemClickListener() {

            public void onItemClick(AdapterView<?> arg0, View v, int position,
                    long id) {

                if (getIntent().getAction().equals(UI.EDIT_SPEED_DIAL_VIEW_CONTACT_ACTION)) {
                    String keyId = getIntent().getStringExtra(UI.SPEED_DIAL_KEY);

                    ContentValues values = new ContentValues();
                    values.put(ContactsContract.SpeedDial.PHONE_ID, mPhoneId.get(position));
                    values.put(ContactsContract.SpeedDial.KEY_ID, keyId);
                    getContentResolver().update(ContactsContract.SpeedDial.CONTENT_URI,
                            values, ContactsContract.SpeedDial.KEY_ID +"= ?", new String[] {keyId});

                    Intent speedDialListIntent = new Intent();
                    speedDialListIntent.setAction(UI.SPEED_DIAL_LIST_ACTION);
                    startActivity(speedDialListIntent);
                }
                else if (getIntent().getAction().equals(UI.SPEED_DIAL_VIEW_CONTACT_ACTION)) {
                    Intent speedDialGridIntent = new Intent();
                    speedDialGridIntent.putExtra(UI.SPEED_DIAL_PHONE_ID,mPhoneId.get(position));
                    speedDialGridIntent.setAction(UI.SPEED_DIAL_GRID_ACTION);
                    startActivity(speedDialGridIntent);
                }
            }
        });
    }
    /**
     * Add the phone number, phone type and photo Id to the list for given contactId
     * @param contactId contacts contact id
     */
    private void getPhoneTypeAndNumber(String contactId) {

        if (mPhoneNumber == null) {
            mPhoneNumber = new ArrayList<String>();
        } else {
            mPhoneNumber.clear();
        }

        if (mPhoneType == null) {
            mPhoneType  = new ArrayList<String>();
        } else {
            mPhoneType.clear();
        }

        if(mPhoneId == null) {
            mPhoneId = new ArrayList<String>();
        } else {
            mPhoneId.clear();
        }

        if(mIsPrimary == null) {
            mIsPrimary = new ArrayList<Integer>();
        } else {
            mIsPrimary.clear();
        }

        String whereClause =
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "= '" + contactId + "'" ;
        Cursor cursor =
                getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone._ID,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY}, whereClause,  null, null);

        if(cursor != null) {

            while(cursor.moveToNext()) {
                String phoneNumber = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
                mPhoneNumber.add(phoneNumber);

                int phoneType = cursor.getInt(
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
                mPhoneType.add(getPhoneType(phoneType));

                String phoneId = cursor.getString(
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID));
                mPhoneId.add(phoneId);

                int isPrimary = cursor.getInt(
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_PRIMARY));
                mIsPrimary.add(isPrimary);
            }
            //Close the cursor after reading the data
            cursor.close();
        }
    }
    /**
     * Get phone type  for phone  id
     * @param aPhoneType is phone type id
     * @return string
     */
    private String getPhoneType(int phoneType) {
        switch(phoneType) {
            case ContactsContract.CommonDataKinds.Phone.TYPE_HOME: {
                return getString(R.string.call_home);
            }
            case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE: {
                return getString(R.string.call_mobile);
            }
            case ContactsContract.CommonDataKinds.Phone.TYPE_WORK: {
                return getString(R.string.call_work);
            }
            case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER: {
                return getString(R.string.call_other);
            }
            default : {
                return getString(R.string.call_home);
            }
        }
    }

private class SpeedDialAdapter extends BaseAdapter {

        /**
         * The number of items in the list
         */
        public int getCount() {
            return mPhoneNumber.size();
        }

        public Object getItem(int position) {
            return position;
        }
        /**
         * Use the array index as a unique id
         */
        public long getItemId(int position) {
            return position;
        }
        /**
         * Make a view to hold each row
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            // A ViewHolder keeps references to children views to avoid unneccessary calls
            // to findViewById() on each row.
            ViewHolder holder;
            if (convertView == null) {

                LayoutInflater li = getLayoutInflater();
                convertView = li.inflate(R.layout.speed_dial_view_contact, parent, false);

                holder = new ViewHolder();

                holder.phoneType = (TextView) convertView.findViewById(R.id.phoneType);
                holder.phoneNumber = (TextView) convertView.findViewById(R.id.phoneNumber);
                holder.speedDialIcon = (ImageView)convertView.findViewById(R.id.speed_dail_icon);
                holder.primaryIcon = (ImageView)convertView.findViewById(R.id.primary_icon);

                convertView.setTag(holder);

            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.phoneType.setText(mPhoneType.get(position));
            holder.phoneNumber.setText(mPhoneNumber.get(position));

            String phoneId = mPhoneId.get(position);
            String whereClause = ContactsContract.SpeedDial.PHONE_ID + "= '" + phoneId + "'" ;

            Cursor speeDialCursor = getContentResolver().query(SpeedDial.CONTENT_URI,
                    new String[]{ContactsContract.SpeedDial.PHONE_ID}, whereClause, null, null);

            if (speeDialCursor != null) {

                if(speeDialCursor.moveToFirst()) {
                    holder.speedDialIcon.setVisibility(View.VISIBLE);
                }
                else {
                    holder.speedDialIcon.setVisibility(View.GONE);
                }
                speeDialCursor.close();
            }
            else {
                holder.speedDialIcon.setVisibility(View.GONE);
                speeDialCursor.close();
            }

            int primaryId = mIsPrimary.get(position);
            if(primaryId == DEFAULT_NUMBER) {
                holder.primaryIcon.setVisibility(View.VISIBLE);
            }
            else {
                holder.primaryIcon.setVisibility(View.GONE);
            }

            return convertView;
        }
    }
    //Cache the child views
    private static class ViewHolder {
        public TextView phoneType;
        public TextView phoneNumber;
        public ImageView speedDialIcon;
        public ImageView primaryIcon;
    }

    protected void onDestroy() {
        super.onDestroy();

        if(mListView != null) {
            mListView = null;
        }
    }
}
