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

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Intents.UI;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * Speed dial grid assigning speed dial number.
 */
public class SpeedDialGrid extends Activity {

    /* Key id pressed */
    private int mKeyIdClicked;
    /* Database cursor  */
    private Cursor mSpeeDialCursor = null;
    /* speed dial grid */
    private GridView mGridMain;
    /* Dialog box */
    private AlertDialog.Builder mAlert;
    /*  Map to hold key id and contact name. */
    private Map<Integer, String> mKeyIdContactNameHashMap;
    /* Map to hold key id and phone type. */
    private Map<Integer, Integer> mKeyIdPhoneTypeDrawableIdHashMap;
    /* Map to hold key id and photo id.  */
    private Map<Integer, Long> mKeyIdPhotoIdHashMap;
    /* Data intent is send from view contact activity. */
    private Intent mDataIntent;
    /* PhotoLoader loads photos and maintain cache of photos. */
    private ContactPhotoLoader mPhotoLoader;
    /* Maximum entries supported in speed dial grid, 1-9 */
    private static final int SPEED_DIAL_MAX_ENTRIES = 8;
    private static final String EMPTY=" Empty ";
    /**
     * Called when the Activity is created.
     * @param aSavedInstanceState Bundle which maintains the Activity state.
     */
    protected  void onCreate(Bundle asavedInstanceState) {

        super.onCreate(asavedInstanceState);

        mPhotoLoader = new ContactPhotoLoader(this, R.drawable.ic_contact_list_picture);

        setContentView(R.layout.grid);

        mGridMain = (GridView) findViewById(R.id.GridView01);
        mDataIntent = getIntent();

        DialogInterface.OnClickListener proceedListener = new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {

                String phoneId = mDataIntent.getStringExtra(UI.SPEED_DIAL_PHONE_ID);

                ContentValues values = new ContentValues();
                values.put(ContactsContract.SpeedDial.PHONE_ID, phoneId);
                values.put(ContactsContract.SpeedDial.KEY_ID, mKeyIdClicked);
                getContentResolver().update(ContactsContract.SpeedDial.CONTENT_URI,
                        values, ContactsContract.SpeedDial.KEY_ID +"= ?",
                        new String[] {Integer.toString(mKeyIdClicked)});
                finish();
            }
        };

        DialogInterface.OnClickListener cancelListener = new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        };

        //Build alert dialog box
        mAlert = new AlertDialog.Builder(this);
        mAlert.setCancelable(true);
        mAlert.setMessage(R.string.reassign_speed_dial_message);
        mAlert.setPositiveButton(android.R.string.ok, proceedListener);
        mAlert.setNegativeButton(android.R.string.cancel, cancelListener);
        mAlert.setTitle(R.string.reassign_speed_dial_title);
        mAlert.setIcon(android.R.drawable.ic_dialog_alert);

    }

    /**
     * Called when the SpeedDialGrid activity comes in the foreground.
     */
    @Override
    protected void onResume() {
        super.onResume();

        mPhotoLoader.resume();

        if (mSpeeDialCursor == null) {
            mSpeeDialCursor =
                    getContentResolver().query(ContactsContract.SpeedDial.CONTENT_URI,
                    new String [] {ContactsContract.SpeedDial.KEY_ID,
                    ContactsContract.SpeedDial.PHONE_ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.SpeedDial.PHONE_TYPE,
                    ContactsContract.SpeedDial.PHONE_NUMBER,
                    ContactsContract.Contacts.PHOTO_ID},
                    null,
                    null,
                    ContactsContract.SpeedDial.KEY_ID);
        }
        else {
            mSpeeDialCursor.requery();
        }
        updateHashMapWithData(mSpeeDialCursor);

        SpeedDialGridAdapter gridAdapter = new SpeedDialGridAdapter();
        mGridMain.setAdapter(gridAdapter);
    }

    private void showDialog() {
        AlertDialog alertDialog = mAlert.create();
        alertDialog.show();
    }

    /**
     * Called when the Activity is destroyed.
     */
    protected void onDestroy() {
        super.onDestroy();

        mPhotoLoader.stop();
        if (mSpeeDialCursor != null) {
            mSpeeDialCursor.close();
        }
    }

    /**
     * Maintains key id and Name in a KeyIdContactNameHashMap.
     * and also maintains key id and PhoneTypeDrawableId in a KeyIdPhoneTypeDrawableIdHashMap.
     */
    private void updateHashMapWithData(Cursor cursor) {

        if (cursor != null) {
            if (null == mKeyIdContactNameHashMap) {
                mKeyIdContactNameHashMap = new HashMap<Integer, String>();
            } else {
                mKeyIdContactNameHashMap.clear();
            }

            if (mKeyIdPhoneTypeDrawableIdHashMap == null) {
                mKeyIdPhoneTypeDrawableIdHashMap = new HashMap<Integer, Integer>();
            } else {
                mKeyIdPhoneTypeDrawableIdHashMap.clear();
            }

            if(mKeyIdPhotoIdHashMap == null){
                mKeyIdPhotoIdHashMap = new HashMap<Integer, Long>();
            } else {
                mKeyIdPhotoIdHashMap.clear();
            }
            startQueryAction(cursor);
        }
    }

    /**
    * Populate the map and ArrayList with data from Speed Dial database
    */
    private void startQueryAction(Cursor speedDialCursor) {

        if(speedDialCursor != null) {
            while(speedDialCursor.moveToNext()) {

                int keyId = speedDialCursor.getInt(speedDialCursor
                        .getColumnIndex(ContactsContract.SpeedDial.KEY_ID));

                int phoneType = speedDialCursor.getInt(speedDialCursor
                       .getColumnIndex(ContactsContract.SpeedDial.PHONE_TYPE));

                int phoneTypeId = getPhoneTypeDrawableIdBasedOnPhoneType(phoneType);

                String displayName = speedDialCursor.getString(speedDialCursor
                        .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                Long photoId = speedDialCursor.getLong(speedDialCursor
                        .getColumnIndex(ContactsContract.Contacts.PHOTO_ID));

                if(photoId != null) {
                    mKeyIdPhotoIdHashMap.put(keyId ,photoId);
                } else {
                    mKeyIdPhotoIdHashMap.put(keyId ,null);
                }

                mKeyIdContactNameHashMap.put(keyId, displayName);
                mKeyIdPhoneTypeDrawableIdHashMap.put(keyId, phoneTypeId);
            }

            speedDialCursor.close();
        }

    }

    /**
     * getPhoneTypeDrawableIdBasedOnPhoneType which returns id for a phone type.
     */
    private int getPhoneTypeDrawableIdBasedOnPhoneType(int phoneType) {
       switch(phoneType) {
           case ContactsContract.CommonDataKinds.Phone.TYPE_HOME: {
               return R.string.Home;
           }
           case ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE: {
               return R.string.Mobile;
           }
           case ContactsContract.CommonDataKinds.Phone.TYPE_WORK: {
               return R.string.Work;
           }
           case ContactsContract.CommonDataKinds.Phone.TYPE_OTHER: {
               return R.string.Other;
           }
           default : {
               return R.string.Home;
           }
       }
    }

    /**
     * Custom Adapter class used to show grid in a row having two columns .
     *
     */
    class SpeedDialGridAdapter extends BaseAdapter implements OnClickListener {

        int mSpeedDialCount = SPEED_DIAL_MAX_ENTRIES;

        public int getCount() {
            return mSpeedDialCount;
        }

        /**
         * Make a view to hold each row.
        */
        public View getView(int position, View convertView, ViewGroup parent) {

            // A ViewHolder keeps references to children views to avoid
            // unnecessary calls
            // to findViewById() on each row.

            ViewHolder holder;

            // When convertView is not null, we can reuse it directly, there is
            // no need
            // to re-inflate it. We only inflate a new View when the convertView
            // supplied
            // by ListView is null.
            if (convertView == null) {

                LayoutInflater li = getLayoutInflater();
                convertView = li.inflate(R.layout.grid_cell, null);
                // Creates a ViewHolder and store references to the children
                // views
                // we want to bind data to.
                holder = new ViewHolder();

                holder.text = (TextView) convertView.findViewById(R.id.icon_text);
                holder.phoneType = (TextView) convertView.findViewById(R.id.icon_phone_type);
                holder.icon = (ImageView) convertView.findViewById(R.id.contact_photo);
                holder.gridNumber = (TextView)convertView.findViewById(R.id.grid_number);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            holder.icon.setOnClickListener(this);
            //initial value of position is 0.
            //Speed dial grid starts with grid 2 to 9.
            //keyIdPosition has to starts with 2.
            int keyIdPosition = position + 1;
            keyIdPosition++;
            Long photoId = mKeyIdPhotoIdHashMap.get(keyIdPosition);

            if (photoId == null) {
                mPhotoLoader.loadPhoto(holder.icon, 0);
            }
            else {
                mPhotoLoader.loadPhoto(holder.icon, photoId);
            }

            if (mKeyIdContactNameHashMap.get(keyIdPosition) == null
                    && mKeyIdPhoneTypeDrawableIdHashMap.get(keyIdPosition) == null ) {

                holder.text.setText(EMPTY);
                holder.phoneType.setText(EMPTY);
            } else {
                holder.text.setText(mKeyIdContactNameHashMap.get(keyIdPosition));
                holder.phoneType.setText(mKeyIdPhoneTypeDrawableIdHashMap.get(keyIdPosition));
            }

            holder.gridNumber.setText(""+keyIdPosition);
            return convertView;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }
        /**
         * Called when the user presses the grid.
         * checks if the grid is assigned with speed dial number then
         * older number will be replaced with
         * new number. Otherwise new speed dial number will be
         * assigned for the empty grid.
         */
        public void onClick(View view) {

            int position = mGridMain.getPositionForView(view);
            mKeyIdClicked = position + 1;
            mKeyIdClicked++;
            String contactName = mKeyIdContactNameHashMap.get(mKeyIdClicked);

            if (contactName != null) {
                showDialog();
            } else {
                String phoneId = mDataIntent.getStringExtra(UI.SPEED_DIAL_PHONE_ID);

                ContentValues values = new ContentValues();
                values.put(ContactsContract.SpeedDial.PHONE_ID, phoneId);
                values.put(ContactsContract.SpeedDial.KEY_ID, mKeyIdClicked);

                getContentResolver().insert(ContactsContract.SpeedDial.CONTENT_URI, values);
                finish();
            }

        }
    }

    /* Cache the children view */
    static private class ViewHolder {

        public TextView text;
        public ImageView icon;
        public TextView phoneType;
        public TextView gridNumber;

    }
}
