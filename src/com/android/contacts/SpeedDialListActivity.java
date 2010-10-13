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
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Intents.UI;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Display list of the all speed dial assigned to the contact and its details.
 */
public class SpeedDialListActivity extends ListActivity implements
        View.OnCreateContextMenuListener {
    //Tag to show name of the activity
    private static final String TAG = "SpeeddialListActivity";
    //Constant field to show options of Edit speed dial in context menu
    static final int MENU_ITEM_EDIT_SPEED_DIAL = 6;
    //Constant field to show options of Remove speed dial in context menu
    static final int MENU_ITEM_REMOVE_SPEED_DIAL = 7;
    //Constant field to show options of View Contact in context menu
    static final int MENU_ITEM_VIEW_CONTACT = 8;
    // Lookup Uri
    protected Uri mLookupUri;
    //Layout inflater
    protected LayoutInflater mInflater;
    // Cursor pointing to the result set from database
    private Cursor mSpeeDialCursor;
    // Map to hold key id and contact name
    private HashMap<Integer, String> mKeydisplayName;
    // Map to hold key id and contact id
    private HashMap<Integer, Long> mKeyContactId;
    // Map to hold key id and phone type
    private Map<Integer, Integer> mKeyIdPhoneTypeDrawableIdHashMap;
    // Map to hold key id and photo id
    private Map<Integer, Long> mKeyIdPhotoIdHashMap;
    // Variable of type ArrayList to hold all speed dial assigned key's
    private ArrayList<Integer> mKeyId;
    // map to hold key id , phone number
    private Map<Integer,String> mKeyIdPhoneNumberMap;

    private AlertDialog.Builder mAlertBuilder;
    // hold the status of speed dial like enable or disable
    private Boolean mIsSpeedDialDisabled = false;
    // ContactPhotoLoader which loads photos and maintain cache of them
    private ContactPhotoLoader mPhotoLoader;
    // Voice mail number
    private static final String EMPTY_NUMBER = "";

    private static final int VOICE_MAIL_GRID = 1;

    private static final String VOICE_MAIL = "voicemail";

    private static final int VOICE_MAIL_POSITION = 0;

    /**
     * onDestroy called when the Activity is destroyed.
     */
    protected void onDestroy() {

        super.onDestroy();
        mPhotoLoader.stop();
        if (mSpeeDialCursor != null) {
            mSpeeDialCursor.close();
        }
    }

    /**
     * onCreate called when the Activity is created.
     *
     * @param icicle
     *            Bundle which maintains the Activity state.
     */
    public void onCreate(Bundle icicle) {

        super.onCreate(icicle);
        getListView().setOnCreateContextMenuListener(this);

        mPhotoLoader = new ContactPhotoLoader(this,
                R.drawable.ic_contact_list_picture);

        DialogInterface.OnClickListener proceedListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                getContentResolver().delete(ContactsContract.SpeedDial.CONTENT_URI,
                        null, null);
                finish();
            }
        };

        DialogInterface.OnClickListener cancelListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        };

        mAlertBuilder = new AlertDialog.Builder(this);
        mAlertBuilder.setCancelable(true).setMessage(
                R.string.removeAllSpeedDial_message).setPositiveButton(
                android.R.string.ok, proceedListener).setNegativeButton(
                android.R.string.cancel, cancelListener).setTitle(
                R.string.removeAllSpeedDial_title).setIcon(
                android.R.drawable.ic_dialog_alert);
    }

    private void showDialog() {
        AlertDialog alertDialog = mAlertBuilder.create();
        alertDialog.show();
    }

    /**
     * onResume called when the SpeedDialListActivity comes in the foreground.
     */
    protected void onResume() {
        super.onResume();

        mPhotoLoader.resume();

        speedDialListQuery();
        initializeData(mSpeeDialCursor);
        setTitle(R.string.speed_dial_list_title);
        SpeedDialListActivity.SpeedDialAdapter speedAdapter;
        speedAdapter = new SpeedDialListActivity.SpeedDialAdapter(this);
        setListAdapter(speedAdapter);

        SharedPreferences prefs = getSharedPreferences(
                getString(R.string.speed_dial), MODE_WORLD_READABLE);
        mIsSpeedDialDisabled = prefs.getBoolean(
                getString(R.string.is_speed_dial_disabled), false);
        if (!mIsSpeedDialDisabled) {
            setTitle(R.string.speed_dial_list_title);
        } else {
            setTitle(R.string.speed_dial_list_activity_disabled_title);
        }
    }

    private void speedDialListQuery() {
            mSpeeDialCursor = getContentResolver().query(
                    ContactsContract.SpeedDial.CONTENT_URI,
                    new String[] { ContactsContract.SpeedDial.KEY_ID,
                    ContactsContract.Contacts.DISPLAY_NAME,
                    ContactsContract.SpeedDial.PHONE_TYPE,
                    ContactsContract.Contacts.PHOTO_ID,
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.SpeedDial.PHONE_NUMBER},
                    null, null, ContactsContract.SpeedDial.KEY_ID);
    }

    /**
     * method to initialize the map and ArrayList
     */
    private void initializeData(Cursor cursor) {
        Log.i(TAG, "initializeData");
        if (cursor != null) {

            if (mKeydisplayName == null && mKeyId == null) {
                mKeydisplayName = new HashMap<Integer, String>();
                mKeyId = new ArrayList<Integer>();
            } else {
                mKeydisplayName.clear();
                mKeyId.clear();
            }

            if (mKeyContactId == null) {
                mKeyContactId = new HashMap<Integer, Long>();
            } else {
                mKeyContactId.clear();
            }

            if (mKeyIdPhoneTypeDrawableIdHashMap == null && mKeyIdPhoneNumberMap == null) {
                mKeyIdPhoneTypeDrawableIdHashMap = new HashMap<Integer, Integer>();
                mKeyIdPhoneNumberMap=new HashMap<Integer, String>();
            } else {
                mKeyIdPhoneTypeDrawableIdHashMap.clear();
                mKeyIdPhoneNumberMap.clear();
            }

            if (mKeyIdPhotoIdHashMap == null) {
                mKeyIdPhotoIdHashMap = new HashMap<Integer, Long>();
            } else {
                mKeyIdPhotoIdHashMap.clear();
            }

            mKeydisplayName.put(VOICE_MAIL_GRID, VOICE_MAIL);
            mKeyId.add(VOICE_MAIL_GRID);
            mKeyIdPhoneTypeDrawableIdHashMap.put(VOICE_MAIL_GRID, VOICE_MAIL_POSITION);
            mKeyIdPhotoIdHashMap.put(VOICE_MAIL_GRID, null);
            mKeyContactId.put(VOICE_MAIL_GRID, null);
        }
        startQueryAction(cursor);
    }

    /**
     * populate the map and ArrayList with data from database
     */
    private void startQueryAction(Cursor cursor) {
        Log.i(TAG, "startQueryAction");
        if (cursor != null) {
            while (cursor.moveToNext()) {

                int keyId = cursor.getInt(cursor
                        .getColumnIndex(ContactsContract.SpeedDial.KEY_ID));

                String displayname = cursor.getString(cursor
                        .getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                int phoneType = cursor.getInt(cursor
                        .getColumnIndex(ContactsContract.SpeedDial.PHONE_TYPE));

                Long photoId = cursor.getLong(cursor
                        .getColumnIndex(ContactsContract.Contacts.PHOTO_ID));

                Long contactId = cursor.getLong(cursor
                        .getColumnIndex(ContactsContract.Data.CONTACT_ID));

                String phoneNumber = cursor.getString(cursor
                        .getColumnIndex(ContactsContract.SpeedDial.PHONE_NUMBER));

                if (photoId != null) {
                    mKeyIdPhotoIdHashMap.put(keyId, photoId);
                } else {
                    mKeyIdPhotoIdHashMap.put(keyId, null);
                }
                int lStringId = getPhoneTypeDrawableIdBasedOnPhoneType(phoneType);
                mKeyId.add(keyId);
                mKeydisplayName.put(keyId, displayname);
                mKeyIdPhoneTypeDrawableIdHashMap.put(keyId, lStringId);
                mKeyContactId.put(keyId, contactId);
                mKeyIdPhoneNumberMap.put(keyId,phoneNumber);
            }

            //Close the cursor after reading the data
            cursor.close();
        }
    }

    /**
     * getPhoneTypeDrawableIdBasedOnPhoneType which returns the id for a phone
     * type.
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
     * Method to inflate the menu for speed dial list once only.
     *
     * @param amenu
     *            Menu.
     * @return boolean
     */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.speed_dial_edit, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        SharedPreferences prefs = getSharedPreferences(
                getString(R.string.speed_dial), MODE_WORLD_READABLE);
        mIsSpeedDialDisabled = prefs.getBoolean(
                getString(R.string.is_speed_dial_disabled), false);

        MenuItem speedDialStatusMenu = (MenuItem) menu
                .findItem(R.id.disable_speed_dial);
        if (mIsSpeedDialDisabled) {
            speedDialStatusMenu.setTitle(R.string.enable_speed_dial);
        } else {
            speedDialStatusMenu
                    .setTitle(R.string.disable_speed_dial);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.remove_speed_dial_list:
            deleteSpeedDialAction();
            return true;

        case R.id.disable_speed_dial:
            doDisableSpeedDialAction();
            return true;
        }
        return false;
    }

    /**
     * method to remove all speed dial .
     *
     */
    private void deleteSpeedDialAction() {
        showDialog();
    }

    /**
     * Method to call speed dial number
     * @param atelNumber phone number
     * @return boolean
     */

    private boolean dialNumber(String telNumber) {
        if (telNumber!=null) {
            Intent dialIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,Uri.parse("tel:" +telNumber));
            /** Use NEW_TASK_LAUNCH to launch the Dialer Activity */
            dialIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK );
            /** Finally start the Activity */
            startActivity(dialIntent);
            finish();
            return true;
        }
        return false;
    }

    /**
     * SpeedDialAdapter is a class Custom Adapter used to show the speed dial
     * number in list
     */
    private class SpeedDialAdapter extends BaseAdapter implements
            OnClickListener {
        private LayoutInflater mInflater;

        public SpeedDialAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);
        }

        public void onClick(View v) {
            int position = SpeedDialListActivity.this.getListView().getPositionForView(v);
            if(position == VOICE_MAIL_POSITION) {
                callVoicemail();
            }
            else {
                int keyId = mKeyId.get(position);
                String phoneNumber=mKeyIdPhoneNumberMap.get(keyId);
                if(phoneNumber != null) {
                    dialNumber(phoneNumber);
                }
            }
        }

        /**
         * The number of items in the list is determined by the number of key id
         * in ArrayList.
         * @see android.widget.ListAdapter#getCount()
         */
        public int getCount() {
            return mKeydisplayName.size();
        }

        /**
         * Since the data comes from an array list, just returning the index is
         * sufficient to get at the data. If we were using a more complex data
         * structure, we would return whatever object represents one row in the
         * list.
         * @see android.widget.ListAdapter#getItem(int)
         */
        public Object getItem(int position) {
            return position;
        }

        public boolean isEnabled(int position){
            if (position == VOICE_MAIL_POSITION){
                return false;
           }
            return true;
        }
        /**
         * Use the array list index as a unique id.
         */
        public long getItemId(int position) {
            return position;
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
                convertView = mInflater.inflate(R.layout.speed_dial_list,
                        parent, false);
                // Creates a ViewHolder and store references to the children
                // views
                // we want to bind data to.
                holder = new ViewHolder();
                holder.text = (TextView) convertView
                        .findViewById(R.id.icon_text);
                holder.phoneType = (TextView) convertView
                        .findViewById(R.id.icon_phone_type);
                holder.icon = (ImageView) convertView
                        .findViewById(R.id.phoneTypeImage);
                holder.secondaryActionDivider = convertView
                        .findViewById(R.id.divider);
                holder.secondaryActionDivider.setVisibility(View.VISIBLE);
                holder.gridNumber = (TextView)convertView.findViewById(R.id.grid_number);
                holder.callIcon = (ImageView) convertView.findViewById(R.id.call_icon);
                holder.callIcon.setOnClickListener(this);

                convertView.setTag(holder);
            } else {
                // Get the ViewHolder back to get fast access to the TextView
                // and the ImageView.
                holder = (ViewHolder) convertView.getTag();
            }
            // Bind the data efficiently with the holder.

            int keyId = mKeyId.get(position);
            Long photoId = mKeyIdPhotoIdHashMap.get(keyId);
            if (photoId == null) {
                mPhotoLoader.loadPhoto(holder.icon, 0);
            }
            else {
                mPhotoLoader.loadPhoto(holder.icon, photoId);
            }

            holder.text.setText(mKeydisplayName.get(keyId));
            if (mKeyIdPhoneTypeDrawableIdHashMap.get(keyId) == 0) {
                holder.phoneType.setText("");
            } else {
                holder.phoneType.setText(mKeyIdPhoneTypeDrawableIdHashMap
                        .get(keyId));
            }
            holder.gridNumber.setText("" + keyId);
            if (position == 0) {
                convertView.setClickable(false);
                convertView.setLongClickable(false);
            }
            return convertView;
        }
    }

    /* Cache of the children views of a row */
    static private class ViewHolder {
        TextView text;
        TextView phoneType;
        ImageView icon;
        View secondaryActionDivider;
        TextView gridNumber;
        ImageView callIcon;
    }

    /**
     * method to show the options of View Contact, Edit Speed Dial, Remove from
     * Speed dial on the context menu
     */
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }
        // This can be null sometimes, don't crash...
        if (info == null) {
            Log.e(TAG, "bad menuInfo");
            return;
        }
        int position = this.getListView().getPositionForView(view);
        menu.setHeaderTitle(R.string.contactOptionsTitle);
        menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_view_contact);
        menu.add(0, MENU_ITEM_EDIT_SPEED_DIAL, 0, R.string.menu_edit_speeddial);
        menu.add(0, MENU_ITEM_REMOVE_SPEED_DIAL, 0,
                R.string.menu_remove_speeddial);
    }

    /**
     * method gets called when the options of the context menu selected
     */
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }
        switch (item.getItemId()) {
        case MENU_ITEM_VIEW_CONTACT: {
            int keyId = mKeyId.get(info.position);
            Long contactId = mKeyContactId.get(keyId);
            if (contactId != null) {
                Uri contactUri = ContentUris.withAppendedId(
                        Contacts.CONTENT_URI, contactId);
                Intent viewContact = new Intent(Intent.ACTION_VIEW, contactUri);
                startActivity(viewContact);
            }
            return true;
        }
        case MENU_ITEM_EDIT_SPEED_DIAL: {
            int keyId = mKeyId.get(info.position);
            Intent contactList = new Intent(UI.SPEED_DIAL_CONTACT_LIST_ACTION);
            contactList.putExtra(UI.SPEED_DIAL_KEY, Integer.toString(keyId));
            startActivity(contactList);
            return true;
        }
        case MENU_ITEM_REMOVE_SPEED_DIAL: {
            deleteFromSpeedDail(info.position);
            return true;
        }
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Deletes selected speed dial from the context menu .
     */
    private void deleteFromSpeedDail(int position) {
        getContentResolver().delete(ContactsContract.SpeedDial.CONTENT_URI,
                ContactsContract.SpeedDial.KEY_ID + "= ?",
                new String[] { Integer.toString(mKeyId.get(position)) });
        //((BaseAdapter) getListAdapter()).notifyDataSetChanged();

        mPhotoLoader.resume();

        speedDialListQuery();
        initializeData(mSpeeDialCursor);

        SpeedDialListActivity.SpeedDialAdapter speedAdapter;
        speedAdapter = new SpeedDialListActivity.SpeedDialAdapter(this);
        setListAdapter(speedAdapter);
    }

    /*
     * Disables all the speed dial number from the menu options .
     */
    private void doDisableSpeedDialAction() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setIcon(android.R.drawable.ic_dialog_alert);

        builder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        toggleSpeedDialStatus();
                    }
                });

        builder.setNegativeButton(android.R.string.cancel,
                new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });

        if (mIsSpeedDialDisabled) {
            builder.setTitle(R.string.enable_speed_dial);
            builder.setMessage(R.string._to_enable_speed_dial);
        } else {
            builder.setTitle(R.string.disable_speed_dial);
            builder.setMessage(R.string._to_disable_speed_dial);
        }
        AlertDialog mAlertDialog = builder.create();
        mAlertDialog.show();
    }

    void callVoicemail() {
        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                Uri.fromParts(VOICE_MAIL, EMPTY_NUMBER, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    /*
     * toggles the current speed dial status from on to off and vice versa
     */
    private void toggleSpeedDialStatus() {
        SharedPreferences.Editor prefsEditor = getSharedPreferences(
                getString(R.string.speed_dial), MODE_WORLD_READABLE).edit();
        if (!mIsSpeedDialDisabled) {
            prefsEditor.putBoolean(getString(R.string.is_speed_dial_disabled),
                    true);
            prefsEditor.commit();
            setTitle(R.string.speed_dial_list_activity_disabled_title);
        } else {
            prefsEditor.putBoolean(getString(R.string.is_speed_dial_disabled),
                    false);
            prefsEditor.commit();
            setTitle(R.string.speed_dial_list_title);
        }
    }
}
