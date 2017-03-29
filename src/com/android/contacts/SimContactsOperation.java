/*
 * Copyright (C) 2015-2017, The Linux Foundation. All Rights Reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of The Linux Foundation nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package com.android.contacts;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.SimContactsConstants;
import com.android.contacts.ContactUtils;

public class SimContactsOperation {

    private static final String  TAG = "SimContactsOperation";
    private static final boolean DBG = true;
    public static final String[] CONTACT_PROJECTION = new String[] {
        Contacts.Entity.RAW_CONTACT_ID,
        Contacts.Entity.CONTACT_ID,
        RawContacts.ACCOUNT_NAME,
        RawContacts.ACCOUNT_TYPE,
        Data.DATA1,
        Data.DATA2,
        Data.MIMETYPE,
    };

    private static final int CONTACT_COLUMN_RAW_ID = 0;
    private static final int CONTACT_COLUMN_CONTACT_ID = 1;
    private static final int CONTACT_COLUMN_ACCOUNT_NAME = 2;
    private static final int CONTACT_COLUMN_ACCOUNT_TYPE = 3;
    private static final int CONTACT_COLUMN_DATA = 4;
    private static final int CONTACT_COLUMN_DATA_TYPE = 5;
    private static final int CONTACT_COLUMN_DATA_MIMETYPE = 6;

    private static Context mContext;
    private ContentResolver mResolver;
    private ContentValues mValues = new ContentValues();

    public SimContactsOperation(Context context) {
        mContext = context;
        this.mResolver = context.getContentResolver();
    }

    public Uri insert(ContentValues values, int subscription) {

        Uri uri = getContentUri(subscription);
        if (uri == null)
            return null;
        String number = values.getAsString(SimContactsConstants.STR_NUMBER);
        String anrs = values.getAsString(SimContactsConstants.STR_ANRS);
        if (!TextUtils.isEmpty(anrs)) {
            anrs = anrs.replaceAll("[^0123456789PWN\\,\\;\\*\\#\\+\\:]", "");
        }
        String emails = values.getAsString(SimContactsConstants.STR_EMAILS);
        values.put(SimContactsConstants.STR_NUMBER,PhoneNumberUtils.stripSeparators(number));
        values.put(SimContactsConstants.STR_ANRS,anrs);
        values.put(SimContactsConstants.STR_EMAILS,emails);

        Uri resultUri;
        resultUri = mResolver.insert(uri,values);
        return resultUri;
    }

    public int update(ContentValues values,int subscription) {
        Uri uri = getContentUri(subscription);

        int result = 0;
        if (uri == null)
            return result;
        String oldNumber = values.getAsString(SimContactsConstants.STR_NUMBER);
        String newNumber = values.getAsString(SimContactsConstants.STR_NEW_NUMBER);
        String oldAnrs = values.getAsString(SimContactsConstants.STR_ANRS);
        String newAnrs = values.getAsString(SimContactsConstants.STR_NEW_ANRS);
        values.put(SimContactsConstants.STR_NUMBER,PhoneNumberUtils.stripSeparators(oldNumber));
        values.put(SimContactsConstants.STR_NEW_NUMBER,PhoneNumberUtils.stripSeparators(newNumber));
        if (!TextUtils.isEmpty(oldAnrs)) {
            oldAnrs = oldAnrs.replaceAll("[^0123456789PWN\\,\\;\\*\\#\\+\\:]", "");
        }
        if (!TextUtils.isEmpty(newAnrs)) {
            newAnrs = newAnrs.replaceAll("[^0123456789PWN\\,\\;\\*\\#\\+\\:]", "");
        }
        values.put(SimContactsConstants.STR_ANRS,oldAnrs);
        values.put(SimContactsConstants.STR_NEW_ANRS,newAnrs);

        result = mResolver.update(uri,values,null,null);
        return result;

    }

    public int delete(ContentValues values, int subscription) {
        Uri uri = getContentUri(subscription);

        int result = 0;
        if (uri == null)
            return result;
        StringBuilder buf = new StringBuilder();
        String num = null;
        String name = values.getAsString(SimContactsConstants.STR_TAG);
        String number = values.getAsString(SimContactsConstants.STR_NUMBER);
        String emails = values.getAsString(SimContactsConstants.STR_EMAILS);
        String anrs = values.getAsString(SimContactsConstants.STR_ANRS);
        if (number != null)
            num = PhoneNumberUtils.stripSeparators(number);
        if (anrs != null)
            anrs = anrs.replaceAll("[^0123456789PWN\\,\\;\\*\\#\\+\\:]", "");
        if (!TextUtils.isEmpty(name)) {
            buf.append("tag='");
            buf.append(name);
            buf.append("'");
        }
        if (!TextUtils.isEmpty(num)) {
            buf.append(" AND number='");
            buf.append(num);
            buf.append("'");
        }
        if (!TextUtils.isEmpty(emails)) {
            buf.append(" AND emails='");
            buf.append(emails);
            buf.append("'");
        }
        if (!TextUtils.isEmpty(anrs)) {
            buf.append(" AND anrs='");
            buf.append(anrs);
            buf.append("'");
        }
        result = mResolver.delete(uri,buf.toString(),null);
        return result;
    }

    private Uri getContentUri(int slot) {
        Uri uri = null;
        int sub = ContactUtils.getActiveSubId(mContext, slot);
        if (sub > 0) {
            uri = Uri.parse(SimContactsConstants.SIM_SUB_URI + sub);
        }
        return uri;
    }

    private static Cursor setupContactCursor(long contactId) {
        ContentResolver resolver = mContext.getContentResolver();
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                contactId);
        Uri entityUri = Uri.withAppendedPath(contactUri,
                Contacts.Entity.CONTENT_DIRECTORY);
        Cursor cursor = null;
        try {
            cursor = resolver.query(entityUri,
                    CONTACT_PROJECTION, null, null, null);
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor;
            }
            if (cursor != null) {
                cursor.close();
            }
            return null;
        }
    }

    public static ContentValues getSimAccountValues(long contactId) {
        ContentValues mValues = new ContentValues();
        Cursor cursor = setupContactCursor(contactId);
        StringBuilder anr = new StringBuilder();
        StringBuilder email = new StringBuilder();
        if (cursor == null) {
            mValues.clear();
            return mValues;
        }

        try {
            if (cursor.getCount() == 0 || !cursor.moveToFirst()) {
                cursor.close();
                return mValues;
            }
            do {
                String accountType = cursor
                        .getString(CONTACT_COLUMN_ACCOUNT_TYPE);
                String accountName = cursor.getString(CONTACT_COLUMN_ACCOUNT_NAME);
                String mimeType = cursor
                        .getString(CONTACT_COLUMN_DATA_MIMETYPE);
                String data = cursor.getString(CONTACT_COLUMN_DATA);
                String nameAndType = cursor.getString(CONTACT_COLUMN_DATA_TYPE);
                mValues.put(SimContactsConstants.ACCOUNT_TYPE, accountType);
                mValues.put(SimContactsConstants.ACCOUNT_NAME, accountName);
                if (TextUtils.isEmpty(data))
                    continue;
                if (SimContactsConstants.ACCOUNT_TYPE_SIM.equals(accountType)) {
                    if (mimeType.equals(StructuredName.CONTENT_ITEM_TYPE)) {
                        mValues.put(SimContactsConstants.STR_TAG, nameAndType);
                    } else if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
                        if (Integer.parseInt(nameAndType) == Phone.TYPE_MOBILE) {
                            mValues.put(SimContactsConstants.STR_NUMBER, data);
                        } else {
                            if (!TextUtils.isEmpty(anr.toString())) {
                                anr.append(SimContactsConstants.ANR_SEP);
                            }
                            anr.append(data);
                        }
                    } else if (mimeType.equals(Email.CONTENT_ITEM_TYPE)) {
                        if (!TextUtils.isEmpty(email.toString())) {
                            email.append(SimContactsConstants.EMAIL_SEP);
                        }
                        email.append(data);
                    }
                }
            } while (cursor.moveToNext());

            if(!TextUtils.isEmpty(anr.toString()))
                mValues.put(SimContactsConstants.STR_ANRS, anr.toString());
            if (!TextUtils.isEmpty(email.toString()))
                mValues.put(SimContactsConstants.STR_EMAILS, email.toString());
        } catch (Exception e) {
            Log.d(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
        } finally {
            cursor.close();
        } Log.d(TAG,"getSimAccountValue: " + mValues.toString());
        return mValues;
    }

    public static int getSimSubscription(long contactId) {
        int subscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        Cursor cursor = setupContactCursor(contactId);
        try {
            if (cursor == null || cursor.getCount() == 0) {
                return subscription;
            }
            String accountName = cursor.getString(cursor
                    .getColumnIndex(RawContacts.ACCOUNT_NAME));
            String accountType = cursor.getString(cursor
                    .getColumnIndex(RawContacts.ACCOUNT_TYPE));
            if (accountType == null || accountName == null) {
                cursor.close();
                return subscription;
            }
            if (SimContactsConstants.ACCOUNT_TYPE_SIM.equals(accountType)) {
                subscription = ContactUtils.getSubscription(accountType,
                        accountName);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return subscription;
    }

}
