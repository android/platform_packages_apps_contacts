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
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Displays the contacts list to assign speed dial.
 * User selects the contact and assign one of the phone number as speed dial
 */
public class SpeedDialContactList extends ListActivity {

    //List to hold contact Id
    private ArrayList<String> mContactIdList = null;

    //List to hold contact name
    private ArrayList<String> mContactNamelist = null;

    //List to hold photo Id
    private ArrayList<Long> mphotoIdList = null;

    //Position for contact Id
    private String mContactIdPosition = null;

    //Alertdialog box object.
    private AlertDialog.Builder mAlert;

    //Sets adapter for speedDialContactList
    SpeedDialContactList.SpeedDialAdapter mSpeedDialAdapter;

    //ContactPhotoLoader loads photos and maintain cache of them.
    private ContactPhotoLoader mPhotoLoader;

    /**
     * onCreate called when the Activity is created.
     * @param aSavedInstanceState Bundle which maintains the Activity state.
     */
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        mPhotoLoader = new ContactPhotoLoader(this, R.drawable.ic_contact_list_picture);
        DialogInterface.OnClickListener proceedListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                String keyId = getIntent().getStringExtra(UI.SPEED_DIAL_KEY);
                Intent intent = new Intent();
                intent.putExtra(UI.SPEED_DIAL_CONTACT_ID, mContactIdPosition);
                intent.setAction(UI.EDIT_SPEED_DIAL_VIEW_CONTACT_ACTION);
                if (keyId != null) {
                    intent.putExtra(UI.SPEED_DIAL_KEY, keyId);
                    startActivity(intent);
                }
            }
         };

        DialogInterface.OnClickListener cancelListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        };

        mAlert = new AlertDialog.Builder(this);
        mAlert.setCancelable(true);
        mAlert.setMessage(R.string.contact_to_assign_speed_dial_message);
        mAlert.setPositiveButton(android.R.string.ok, proceedListener);
        mAlert.setNegativeButton(android.R.string.cancel, cancelListener);
        mAlert.setTitle(R.string.contact_to_assign_speed_dial_title);
        mAlert.setIcon(android.R.drawable.ic_dialog_alert);
    }

    /**
     * onResume called when the  activity comes in the foreground.
     */
    protected void onResume() {

        super.onResume();

        mPhotoLoader.resume();

        //Add all contacts.
        addContact();

        mSpeedDialAdapter = new SpeedDialContactList.SpeedDialAdapter();
        setListAdapter(mSpeedDialAdapter);

    }

    /*
     *  Adds all contacts contcat_ID, Contacts.DISPLAY_NAME,Contacts.PHOTO_ID to list
     */
    private void addContact() {

        String contactName = null;
        Long photoId = null;
        int contactId = 0;
        Cursor cursor;

        if(mContactIdList == null) {
            mContactIdList = new ArrayList<String>();
        }
        else {
            mContactIdList.clear();
        }

        if(mContactNamelist == null) {
            mContactNamelist = new ArrayList<String>();
        }
        else {
            mContactNamelist.clear();
        }

        if(mphotoIdList == null) {
            mphotoIdList = new ArrayList<Long>();
        }
        else {
            mphotoIdList.clear();
        }

        cursor = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI,
                new String[] { ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_ID },
                null, null, null);

        if(cursor != null) {
            while(cursor.moveToNext()) {
                contactId = cursor.getInt(
                        cursor.getColumnIndex(ContactsContract.Contacts._ID));
                mContactIdList.add(String.valueOf(contactId));

                contactName = cursor.getString(
                        cursor.getColumnIndex( ContactsContract.Contacts.DISPLAY_NAME));
                mContactNamelist.add(contactName);

                photoId = cursor.getLong(
                        cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_ID));
                if(photoId!=null) {
                    mphotoIdList.add(photoId);
                }
                else {
                    mphotoIdList.add(null);
                }
            }

            cursor.close();
        }

    }

    /**
     * SpeedDialAdapter is a class Custom Adapter  shows contact list
     */
    private class SpeedDialAdapter extends BaseAdapter {
        /**
         * The number of items in the list
         * @see android.widget.ListAdapter#getCount()
         */
        public int getCount() {
            return mContactNamelist.size();
        }
        /**
         * Since the data comes from an array, just returning the index is
         * sufficent to get at the data. If we were using a more complex data
         * structure, we would return whatever object represents one row in the
         * list.
         */
        public Object getItem(int position) {
            return position;
        }
        /**
         * Use the array index as a unique id.
         */
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view to hold each row.
         */
        public View getView(int position, View convertView, ViewGroup parent) {
            // A ViewHolder keeps references to children views to avoid unneccessary calls
            // to findViewById() on each row.
            ViewHolder holder;
            if (convertView == null) {
                LayoutInflater li = getLayoutInflater();
                convertView = li.inflate(R.layout.speed_dial_contact_list,parent, false);
                // Creates a ViewHolder and store references to the two children views
                // we want to bind data to.
                holder = new ViewHolder();

                holder.text = (TextView) convertView.findViewById(R.id.icon_text);
                holder.icon = (ImageView) convertView.findViewById(R.id.contact_photo);

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            //Photo id for position clicked
            Long photoId = null;
            photoId = mphotoIdList.get(position);
            if (photoId == null) {
                mPhotoLoader.loadPhoto(holder.icon, 0);
            } else {
                mPhotoLoader.loadPhoto(holder.icon, photoId);
            }

            //Contact Name for position clicked
            holder.text.setText(mContactNamelist.get(position));
            return convertView;

        }

    }
   /**
    * View  holder to text and image.
    */
    private static class ViewHolder {
         public TextView text;
         public ImageView icon;
     }

     protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        showDialog(mContactIdList.get(position));
    }

    /*
    *  Shows the Dialog box with two option "Ok" and "Cancle"
    * when clicked on a contact.
    */
    private void showDialog(String position) {
       this.mContactIdPosition = position;

       AlertDialog alertDialog = mAlert.create();
       alertDialog.show();
    }
    /**
     * onDestroy called when the Activity is destroyed.
     */
    protected void onDestroy() {
        super.onDestroy();

        mPhotoLoader.stop();
    }
}
