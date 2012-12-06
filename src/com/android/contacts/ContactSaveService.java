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
import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.PinnedPositions;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.common.database.ContactUpdateUtils;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.util.CallerInfoCacheUtils;
import com.android.contacts.util.ContactPhotoUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.android.internal.telephony.RILConstants.SimCardID;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.BrcmIccUtils;
import com.android.internal.telephony.IccProvider;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import java.io.ByteArrayOutputStream;
import android.text.TextUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * A service responsible for saving changes to the content provider.
 */
public class ContactSaveService extends IntentService {
    private static final String TAG = "ContactSaveService";

    /** Set to true in order to view logs on content provider operations */
    private static final boolean DEBUG = false;

    public static final String ACTION_NEW_RAW_CONTACT = "newRawContact";

    public static final String EXTRA_ACCOUNT_NAME = "accountName";
    public static final String EXTRA_ACCOUNT_TYPE = "accountType";
    public static final String EXTRA_DATA_SET = "dataSet";
    public static final String EXTRA_CONTENT_VALUES = "contentValues";
    public static final String EXTRA_CALLBACK_INTENT = "callbackIntent";

    public static final String ACTION_SAVE_CONTACT = "saveContact";
    public static final String EXTRA_CONTACT_STATE = "state";
    public static final String EXTRA_SAVE_MODE = "saveMode";
    public static final String EXTRA_SAVE_IS_PROFILE = "saveIsProfile";
    public static final String EXTRA_SAVE_SUCCEEDED = "saveSucceeded";
    public static final String EXTRA_UPDATED_PHOTOS = "updatedPhotos";

    public static final String ACTION_CREATE_GROUP = "createGroup";
    public static final String ACTION_RENAME_GROUP = "renameGroup";
    public static final String ACTION_DELETE_GROUP = "deleteGroup";
    public static final String ACTION_UPDATE_GROUP = "updateGroup";
    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_GROUP_LABEL = "groupLabel";
    public static final String EXTRA_RAW_CONTACTS_TO_ADD = "rawContactsToAdd";
    public static final String EXTRA_RAW_CONTACTS_TO_REMOVE = "rawContactsToRemove";

    public static final String ACTION_SET_STARRED = "setStarred";
    public static final String ACTION_DELETE_CONTACT = "delete";
    public static final String EXTRA_CONTACT_URI = "contactUri";
    public static final String EXTRA_STARRED_FLAG = "starred";

    public static final String ACTION_SET_SUPER_PRIMARY = "setSuperPrimary";
    public static final String ACTION_CLEAR_PRIMARY = "clearPrimary";
    public static final String EXTRA_DATA_ID = "dataId";

    public static final String ACTION_JOIN_CONTACTS = "joinContacts";
    public static final String EXTRA_CONTACT_ID1 = "contactId1";
    public static final String EXTRA_CONTACT_ID2 = "contactId2";
    public static final String EXTRA_CONTACT_WRITABLE = "contactWritable";

    public static final String ACTION_SET_SEND_TO_VOICEMAIL = "sendToVoicemail";
    public static final String EXTRA_SEND_TO_VOICEMAIL_FLAG = "sendToVoicemailFlag";

    public static final String ACTION_SET_RINGTONE = "setRingtone";
    public static final String EXTRA_CUSTOM_RINGTONE = "customRingtone";

    private static final HashSet<String> ALLOWED_DATA_COLUMNS = Sets.newHashSet(
        Data.MIMETYPE,
        Data.IS_PRIMARY,
        Data.DATA1,
        Data.DATA2,
        Data.DATA3,
        Data.DATA4,
        Data.DATA5,
        Data.DATA6,
        Data.DATA7,
        Data.DATA8,
        Data.DATA9,
        Data.DATA10,
        Data.DATA11,
        Data.DATA12,
        Data.DATA13,
        Data.DATA14,
        Data.DATA15
    );

    private static final int PERSIST_TRIES = 3;

    public interface Listener {
        public void onServiceCompleted(Intent callbackIntent);
    }

    private static final CopyOnWriteArrayList<Listener> sListeners =
            new CopyOnWriteArrayList<Listener>();

    private Handler mMainHandler;

    public ContactSaveService() {
        super(TAG);
        setIntentRedelivery(true);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    public static void registerListener(Listener listener) {
        if (!(listener instanceof Activity)) {
            throw new ClassCastException("Only activities can be registered to"
                    + " receive callback from " + ContactSaveService.class.getName());
        }
        sListeners.add(0, listener);
    }

    public static void unregisterListener(Listener listener) {
        sListeners.remove(listener);
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service != null) {
            return service;
        }

        return getApplicationContext().getSystemService(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Call an appropriate method. If we're sure it affects how incoming phone calls are
        // handled, then notify the fact to in-call screen.
        String action = intent.getAction();
        if (ACTION_NEW_RAW_CONTACT.equals(action)) {
            createRawContact(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_SAVE_CONTACT.equals(action)) {
            saveContact(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_CREATE_GROUP.equals(action)) {
            createGroup(intent);
        } else if (ACTION_RENAME_GROUP.equals(action)) {
            renameGroup(intent);
        } else if (ACTION_DELETE_GROUP.equals(action)) {
            deleteGroup(intent);
        } else if (ACTION_UPDATE_GROUP.equals(action)) {
            updateGroup(intent);
        } else if (ACTION_SET_STARRED.equals(action)) {
            setStarred(intent);
        } else if (ACTION_SET_SUPER_PRIMARY.equals(action)) {
            setSuperPrimary(intent);
        } else if (ACTION_CLEAR_PRIMARY.equals(action)) {
            clearPrimary(intent);
        } else if (ACTION_DELETE_CONTACT.equals(action)) {
            deleteContact(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_JOIN_CONTACTS.equals(action)) {
            joinContacts(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_SET_SEND_TO_VOICEMAIL.equals(action)) {
            setSendToVoicemail(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        } else if (ACTION_SET_RINGTONE.equals(action)) {
            setRingtone(intent);
            CallerInfoCacheUtils.sendUpdateCallerInfoCacheIntent(this);
        }
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     */
    public static Intent createNewRawContactIntent(Context context,
            ArrayList<ContentValues> values, AccountWithDataSet account,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(
                context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_NEW_RAW_CONTACT);
        if (account != null) {
            serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_NAME, account.name);
            serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE, account.type);
            serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_SET, account.dataSet);
        }
        serviceIntent.putParcelableArrayListExtra(
                ContactSaveService.EXTRA_CONTENT_VALUES, values);

        // Callback intent will be invoked by the service once the new contact is
        // created.  The service will put the URI of the new contact as "data" on
        // the callback intent.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);
        return serviceIntent;
    }

    private void createRawContact(Intent intent) {
        String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
        String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
        String dataSet = intent.getStringExtra(EXTRA_DATA_SET);
        List<ContentValues> valueList = intent.getParcelableArrayListExtra(EXTRA_CONTENT_VALUES);
        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);

        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        operations.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(RawContacts.DATA_SET, dataSet)
                .build());

        int size = valueList.size();
        for (int i = 0; i < size; i++) {
            ContentValues values = valueList.get(i);
            values.keySet().retainAll(ALLOWED_DATA_COLUMNS);
            operations.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValues(values)
                    .build());
        }

        ContentResolver resolver = getContentResolver();
        ContentProviderResult[] results;
        try {
            results = resolver.applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (Exception e) {
            throw new RuntimeException("Failed to store new contact", e);
        }

        Uri rawContactUri = results[0].uri;
        callbackIntent.setData(RawContacts.getContactLookupUri(resolver, rawContactUri));

        deliverCallback(callbackIntent);
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     * This variant is more convenient to use when there is only one photo that can
     * possibly be updated, as in the Contact Details screen.
     * @param rawContactId identifies a writable raw-contact whose photo is to be updated.
     * @param updatedPhotoPath denotes a temporary file containing the contact's new photo.
     */
    public static Intent createSaveContactIntent(Context context, RawContactDeltaList state,
            String saveModeExtraKey, int saveMode, boolean isProfile,
            Class<? extends Activity> callbackActivity, String callbackAction, long rawContactId,
            Uri updatedPhotoPath) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(String.valueOf(rawContactId), updatedPhotoPath);
        return createSaveContactIntent(context, state, saveModeExtraKey, saveMode, isProfile,
                callbackActivity, callbackAction, bundle);
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     * This variant is used when multiple contacts' photos may be updated, as in the
     * Contact Editor.
     * @param updatedPhotos maps each raw-contact's ID to the file-path of the new photo.
     */
    public static Intent createSaveContactIntent(Context context, RawContactDeltaList state,
            String saveModeExtraKey, int saveMode, boolean isProfile,
            Class<? extends Activity> callbackActivity, String callbackAction,
            Bundle updatedPhotos) {
        Intent serviceIntent = new Intent(
                context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SAVE_CONTACT);
        serviceIntent.putExtra(EXTRA_CONTACT_STATE, (Parcelable) state);
        serviceIntent.putExtra(EXTRA_SAVE_IS_PROFILE, isProfile);
        if (updatedPhotos != null) {
            serviceIntent.putExtra(EXTRA_UPDATED_PHOTOS, (Parcelable) updatedPhotos);
        }

        if (callbackActivity != null) {
            // Callback intent will be invoked by the service once the contact is
            // saved.  The service will put the URI of the new contact as "data" on
            // the callback intent.
            Intent callbackIntent = new Intent(context, callbackActivity);
            callbackIntent.putExtra(saveModeExtraKey, saveMode);
            callbackIntent.setAction(callbackAction);
            serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);
        }
        return serviceIntent;
    }


    private long insertSimContactToContactsProvider(ContentResolver resolver, SimCardID simId, String newName, String newNumber, String newEmail, String newNumberAnr) {
        if (DEBUG) Log.d(TAG, "=>insertSimContactToContactsProvider()");

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        int rawContactInsertIndex = ops.size();

        int aggregationMode = RawContacts.AGGREGATION_MODE_DISABLED;
        String accountType = BrcmIccUtils.ACCOUNT_TYPE_SIM;
        String accountName = BrcmIccUtils.ACCOUNT_NAME_SIM1;
        if (SimCardID.ID_ONE == simId) {
            accountName = BrcmIccUtils.ACCOUNT_NAME_SIM2;
        }

        // insert record
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.AGGREGATION_MODE, aggregationMode)
                .withValue(RawContacts.DELETED, 0)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .withValue(RawContacts.DATA_SET, accountName)
                .withValue(RawContacts.VERSION, 1)
                .withValue(RawContacts.DIRTY, 0)
                .withValue(RawContacts.SYNC1, null)
                .withValue(RawContacts.SYNC2, null)
                .withValue(RawContacts.SYNC3, null)
                .withValue(RawContacts.SYNC4, null)
                .withValues(new ContentValues())
                .build());

        // insert photo
        byte[] photoIcon = null;
        Bitmap photo;

        if (SimCardID.ID_ONE == simId) {
            photo = BitmapFactory.decodeResource(getResources(),R.drawable.ic_sim2_picture);
        } else {
            photo = BitmapFactory.decodeResource(getResources(),R.drawable.ic_sim1_picture);
        }
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        photo.compress(Bitmap.CompressFormat.JPEG, 75, stream);
        photoIcon = stream.toByteArray();

        if (null != photoIcon) {
            if (DEBUG) Log.d(TAG, "insertSimContactToContactsProvider(): [" + rawContactInsertIndex + "].photo");

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Photo.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Photo.PHOTO, photoIcon)
                    .withValue(Data.IS_SUPER_PRIMARY, 1)
                    .build());
        }

        // insert name
        if (!TextUtils.isEmpty(newName)) {
            if (DEBUG) Log.d(TAG, "insertSimContactToContactsProvider(): [" + rawContactInsertIndex + "].name=" + newName);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(StructuredName.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, newName)
                    .build());
        }

        // insert number
        if (!TextUtils.isEmpty(newNumber)) {
            if (DEBUG) Log.d(TAG, "insertSimContactToContactsProvider(): [" + rawContactInsertIndex + "].number=" + newNumber);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Phone.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .withValue(Phone.NUMBER, newNumber)
                    .withValue(Data.IS_PRIMARY, 1)
                    .build());
        }

        // insert allEmails
        if (!TextUtils.isEmpty(newEmail)) {
            if (DEBUG) Log.d(TAG, "insertSimContactToContactsProvider(): [" + rawContactInsertIndex + "].email=" + newEmail);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Email.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.TYPE, Email.TYPE_MOBILE)
                    .withValue(Email.DATA, newEmail)
                    .build());
        }


        // insert ANR
        if (!TextUtils.isEmpty(newNumberAnr)) {
            if (DEBUG) Log.d(TAG, "insertSimContactToContactsProvider(): [" + rawContactInsertIndex + "].anr=" + newNumberAnr);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Phone.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.TYPE, Phone.TYPE_HOME)
                    .withValue(Phone.NUMBER, newNumberAnr)
                    .withValue(Data.IS_PRIMARY, 0)
                    .build());
        }

        ContentProviderResult[] results;
        int operationSize = ops.size();
        if (0 < operationSize) {
            try {
                results = resolver.applyBatch(ContactsContract.AUTHORITY, ops);
                for (int i = 0; i < operationSize; i++) {
                    ContentProviderOperation operation = ops.get(i);
                    if (operation.getType() == ContentProviderOperation.TYPE_INSERT
                            && operation.getUri().getEncodedPath().contains(
                                    RawContacts.CONTENT_URI.getEncodedPath())) {
                        return ContentUris.parseId(results[i].uri);
                    }
                }
            } catch (OperationApplicationException e) {
                if (DEBUG) Log.d(TAG, "insertSimContactToContactsProvider():applyBatch(): OperationApplicationException: " + e.toString() + ". message = " + e.getMessage());
            } catch (RemoteException e) {
                if (DEBUG) Log.d(TAG, "insertSimContactToContactsProvider():applyBatch(): RemoteException: " + e.toString() + ". message = " + e.getMessage());
            }
        }

        return -1;
    }

    private boolean updateSimContactToContactsProvider(ContentResolver resolver,
                                                       long rawContactId,
                                                       String[] oldIccConatctInfo,
                                                       int[] dataId,
                                                       String newName,
                                                       String newNumber,
                                                       String newEmail,
                                                       String newNumberAnr) {
        if (DEBUG) Log.d(TAG, "=>updateSimContactToContactsProvider()");

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        ops.add(ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI)
                .withSelection(RawContacts._ID + "=" + rawContactId, null)
                .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED)
                .build());

        // update name
        if (!oldIccConatctInfo[1].equals(newName)) {
            if (-1==dataId[0]) {
                // insert
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): insert [" + rawContactId + "].name=" + newName);
                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.DISPLAY_NAME, newName)
                        .build());
            }else if (-1!=dataId[0] && TextUtils.isEmpty(newName)) {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): delete [" + rawContactId + "].name=" + newName);
                ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[0], null)
                        .build());
            }else {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): [" + rawContactId + "].name=" + newName + ", nameDataId = " + dataId[0]);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[0], null)
                        .withValue(StructuredName.GIVEN_NAME, "")
                        .withValue(StructuredName.FAMILY_NAME, "")
                        .withValue(StructuredName.PREFIX, "")
                        .withValue(StructuredName.MIDDLE_NAME, "")
                        .withValue(StructuredName.SUFFIX, "")
                        .withValue(StructuredName.DISPLAY_NAME, newName)
                        .build());
            }
        }

        // update number
        if (!oldIccConatctInfo[2].equals(newNumber)) {
            if (-1==dataId[1]) {
                // insert number
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): insert [" + rawContactId + "].number=" + newNumber);

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                        .withValue(Phone.NUMBER, newNumber)
                        .withValue(Data.IS_PRIMARY, 1)
                        .build());
            }else if (-1!=dataId[1] && TextUtils.isEmpty(newNumber)) {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): delete [" + rawContactId + "].number=" + newNumber);
                ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[1], null)
                        .build());
            }else {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): [" + rawContactId + "].number=" + newNumber);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[1], null)
                        .withValue(Phone.NUMBER, newNumber)
                        .build());
            }
        }

        // update email
        if (!oldIccConatctInfo[3].equals(newEmail)) {
            // insert email
            if (-1==dataId[2]) {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): insert [" + rawContactId + "].email=" + newEmail);

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.TYPE, Email.TYPE_MOBILE)
                        .withValue(Email.DATA, newEmail)
                        .build());
            }else if (-1!=dataId[2] && TextUtils.isEmpty(newEmail)) {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider():delete  [" + rawContactId + "].email=" + newEmail);
                ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[2], null)
                        .build());
            }else {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): [" + rawContactId + "].email=" + newEmail);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[2], null)
                        .withValue(Email.DATA, newEmail)
                        .build());
            }
        }

        // update ANR
        if (!oldIccConatctInfo[4].equals(newNumberAnr)) {
            // insert ANR
            if (-1 == dataId[3]) {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): insert [" + rawContactId + "].anr=" + newNumberAnr);

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.TYPE, Phone.TYPE_HOME)
                        .withValue(Data.IS_PRIMARY, 0)
                        .withValue(Phone.NUMBER, newNumberAnr)
                        .build());
            }else if (-1!=dataId[3] && TextUtils.isEmpty(newNumberAnr)) {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): delete [" + rawContactId + "].anr=" + newNumberAnr);
                ops.add(ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[3], null)
                        .build());
            }else {
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider(): [" + rawContactId + "].anr=" + newNumberAnr);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + dataId[3], null)
                        .withValue(Phone.NUMBER, newNumberAnr)
                        .build());
            }
        }

        ops.add(ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI)
                .withSelection(RawContacts._ID + "=" + rawContactId, null)
                .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED)
                .build());

        boolean exceptionOccurs = false;
        if (2 < ops.size()) {
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (OperationApplicationException e) {
                exceptionOccurs = true;
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider():applyBatch(): OperationApplicationException: " + e.toString() + ". message = " + e.getMessage());
            } catch (RemoteException e) {
                exceptionOccurs = true;
                if (DEBUG) Log.d(TAG, "updateSimContactToContactsProvider():applyBatch(): RemoteException: " + e.toString() + ". message = " + e.getMessage());
            }
        }

        return !exceptionOccurs;
    }

    private long saveSimContact(ContentResolver resolver, RawContactDelta state) {
        if (DEBUG) Log.d(TAG, "=>saveSimContact(): state = " + state);

        final ValuesDelta values = state.getValues();

        boolean isInsert = state.isContactInsert();
        long rawContactId = values.getAsLong(RawContacts._ID);

        if (DEBUG) Log.d(TAG, "saveSimContact(): rawContactId = " + rawContactId + ", isInsert = " + isInsert);

        final String accountName = values.getAsString(RawContacts.ACCOUNT_NAME);
        if (DEBUG) Log.d(TAG, "saveSimContact(): accountName = " + accountName);

        final SimCardID simId;
        if (BrcmIccUtils.ACCOUNT_NAME_SIM2.equals(accountName)) {
            simId = SimCardID.ID_ONE;
        } else {
            simId = SimCardID.ID_ZERO;
        }

        String displayName = "";
        ValuesDelta primary = state.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
        if (null != primary) {
            String name;
            name = primary.getAsString(StructuredName.DISPLAY_NAME);
            if (null != name) {
                displayName = name;
            }
        }
        if (DEBUG) Log.d(TAG, "saveSimContact(): displayName = " + displayName);

        String newNumber = "";
        String newNumberAnr = "";
        ArrayList<ValuesDelta> phones = state.getMimeEntries(Phone.CONTENT_ITEM_TYPE);
        if (phones != null) {
            for (int i = 0; i < phones.size(); i++) {
                ValuesDelta phone = phones.get(i);
                if (phone.containsKey(Phone.TYPE) && phone.containsKey(Phone.NUMBER)) {
                    int phoneType = phone.getAsInteger(Phone.TYPE);
                    String phoneNumber = phone.getAsString(Phone.NUMBER);
                    if (DEBUG) Log.d(TAG, "saveSimContact(): phones["+i+"]: phoneNumber: " + phoneNumber
                            + ", phoneType = " + phoneType);
                    if (!TextUtils.isEmpty(phoneNumber)) {
                        boolean isDelete = phone.isDelete();
                        if (isDelete) {
                            if (DEBUG) Log.d(TAG, "saveSimContact(): phones["+i+"] is delete");
                            phoneNumber = "";
                        } else {
                            phoneNumber = phoneNumber.replaceAll("[\\(\\)\\-\\./; ]", "");
                        }
                        if (phoneType == Phone.TYPE_MOBILE) {
                            if (!TextUtils.isEmpty(newNumber)) {
                                newNumberAnr = phoneNumber;
                            } else {
                                newNumber = phoneNumber;
                            }
                        } else if (phoneType == Phone.TYPE_HOME) {
                            if (!TextUtils.isEmpty(newNumberAnr)) {
                                if (TextUtils.isEmpty(newNumber)) {
                                    newNumber = phoneNumber;
                                } else {
                                    newNumberAnr = phoneNumber;
                                }
                            } else {
                                newNumberAnr = phoneNumber;
                            }
                        }
                    }
                }
            }
        }

        String newEmail = "";
        ArrayList<ValuesDelta> emails = state.getMimeEntries(Email.CONTENT_ITEM_TYPE);
        if (emails != null && 0 < emails.size()) {
            ValuesDelta email = emails.get(0);
            String emailAddress;
            if (email.containsKey(Email.DATA)) {
                emailAddress = email.getAsString(Email.DATA);
                if (email.isDelete()) {
                    newEmail = "";
                } else if (!TextUtils.isEmpty(emailAddress)) {
                    newEmail = emailAddress;
                }
            }
        }
        if (DEBUG) Log.d(TAG, "saveSimContact(): newEmail = " + newEmail);

        StringBuilder groupString = new StringBuilder();
        ArrayList<Long> origGroupIds = BrcmIccUtils.getGroupIdsFromRawContactId(resolver, rawContactId);
        ArrayList<Long> groupIdToAdd = new ArrayList<Long>();
        ArrayList<Long> groupIdToRemove = new ArrayList<Long>();
        ArrayList<ValuesDelta> groups = state.getMimeEntries(GroupMembership.CONTENT_ITEM_TYPE);
        if (groups != null) {
            if (DEBUG) Log.d(TAG, "saveSimContact(): groups size = " + groups.size());
            for (ValuesDelta group : groups) {
                if (DEBUG) Log.d(TAG, "saveSimContact(): group = " + group);
                if (group.containsKey(GroupMembership.GROUP_ROW_ID)) {
                    long groupId = group.getAsLong(GroupMembership.GROUP_ROW_ID);

                    if (!group.isDelete()) {
                        if (DEBUG) Log.d(TAG, "saveSimContact(): not deleted groupId = " + groupId);
                        if (!origGroupIds.contains(groupId)) {
                            groupIdToAdd.add(groupId);
                        }

                        String iccGroupId = BrcmIccUtils.getIccGroupIdFromGroupId(resolver, groupId);
                        if (!TextUtils.isEmpty(iccGroupId)) {
                            groupString.append(iccGroupId);
                            groupString.append(",");
                        }
                    } else {
                        if (DEBUG) Log.d(TAG, "saveSimContact(): deleted groupId = "+ groupId);
                        if (!origGroupIds.contains(groupId)) {
                            Log.e(TAG, "saveSimContact(): delete a group not exist. groupId = " + groupId);
                        }
                        groupIdToRemove.add(groupId);
                    }
                }
            }
        } else {
            if (DEBUG) Log.d(TAG, "saveSimContact(): groups == null");
        }

        String newIccGroupId = groupString.toString();
        if (DEBUG) Log.d(TAG, "saveSimContact(): newIccGroupId = " + newIccGroupId);

        if (isInsert) {
            if (!BrcmIccUtils.insertIccCardContact(resolver, simId, displayName, newNumber, newEmail, newNumberAnr, newIccGroupId)) {
                return -1;
            }

            rawContactId = insertSimContactToContactsProvider(resolver, simId, displayName, newNumber, newEmail, newNumberAnr);
            if (DEBUG) Log.d(TAG, "saveSimContact(): rawContactId = " + rawContactId);
        } else {
            int[] dataId = new int[4];
            String[] contactInfo = BrcmIccUtils.getIccContactInfo(resolver, rawContactId, dataId);

            if (!BrcmIccUtils.updateIccCardContact(resolver,
                                                   simId,
                                                   contactInfo[1],
                                                   displayName,
                                                   contactInfo[2],
                                                   newNumber,
                                                   contactInfo[3],
                                                   newEmail,
                                                   contactInfo[4],
                                                   newNumberAnr,
                                                   contactInfo[5],
                                                   newIccGroupId,
                                                   Integer.valueOf(contactInfo[6]))) {
                Log.e(TAG, "saveSimContact(): updateIccCardContact() == false");
                return -1;
            }

            if (!updateSimContactToContactsProvider(resolver, rawContactId, contactInfo, dataId, displayName, newNumber, newEmail, newNumberAnr)) {
                Log.e(TAG, "saveSimContact(): updateSimContactToContactsProvider() == false");
                return -1;
            }
        }

        long[] rawContactIdAry = new long[] {rawContactId};
        for (Long groupId : groupIdToAdd) {
            addMembersToGroup(resolver, rawContactIdAry, groupId);
        }

        for (Long groupId : groupIdToRemove) {
            removeMembersFromGroup(resolver, rawContactIdAry, groupId);
        }

        return rawContactId;
    }
    private void saveContact(Intent intent) {
        RawContactDeltaList state = intent.getParcelableExtra(EXTRA_CONTACT_STATE);
        boolean isProfile = intent.getBooleanExtra(EXTRA_SAVE_IS_PROFILE, false);
        Bundle updatedPhotos = intent.getParcelableExtra(EXTRA_UPDATED_PHOTOS);
        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);

        // Trim any empty fields, and RawContacts, before persisting
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(this);
        RawContactModifier.trimEmpty(state, accountTypes);

        Uri lookupUri = null;

        final ContentResolver resolver = getContentResolver();
        boolean succeeded = false;

        // Keep track of the id of a newly raw-contact (if any... there can be at most one).
        long insertedRawContactId = -1;

        // Attempt to persist changes
        int tries = 0;
        while (tries++ < PERSIST_TRIES) {
            try {
                if (0 < state.size()) {
                    final ValuesDelta values = state.get(0).getValues();
                    final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
                    if (BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(accountType)) {
                        final long rawContactId = saveSimContact(resolver, state.get(0));
                        if (-1 != rawContactId) {
                            final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                                    rawContactId);

                            lookupUri = RawContacts.getContactLookupUri(resolver, rawContactUri);
                            Log.v(TAG, "Saved contact. New URI: " + lookupUri);
                            // Mark the intent to indicate that the save was successful (even if the lookup URI
                            // is now null).  For local contacts or the local profile, it's possible that the
                            // save triggered removal of the contact, so no lookup URI would exist..
                            callbackIntent.putExtra(EXTRA_SAVE_SUCCEEDED, true);
                        }
                        break;
                    }
                }

                // Build operations and try applying
                final ArrayList<ContentProviderOperation> diff = state.buildDiff();
                if (DEBUG) {
                    Log.v(TAG, "Content Provider Operations:");
                    for (ContentProviderOperation operation : diff) {
                        Log.v(TAG, operation.toString());
                    }
                }

                ContentProviderResult[] results = null;
                if (!diff.isEmpty()) {
                    results = resolver.applyBatch(ContactsContract.AUTHORITY, diff);
                }

                final long rawContactId = getRawContactId(state, diff, results);
                if (rawContactId == -1) {
                    throw new IllegalStateException("Could not determine RawContact ID after save");
                }
                // We don't have to check to see if the value is still -1.  If we reach here,
                // the previous loop iteration didn't succeed, so any ID that we obtained is bogus.
                insertedRawContactId = getInsertedRawContactId(diff, results);
                if (isProfile) {
                    // Since the profile supports local raw contacts, which may have been completely
                    // removed if all information was removed, we need to do a special query to
                    // get the lookup URI for the profile contact (if it still exists).
                    Cursor c = resolver.query(Profile.CONTENT_URI,
                            new String[] {Contacts._ID, Contacts.LOOKUP_KEY},
                            null, null, null);
                    try {
                        if (c.moveToFirst()) {
                            final long contactId = c.getLong(0);
                            final String lookupKey = c.getString(1);
                            lookupUri = Contacts.getLookupUri(contactId, lookupKey);
                        }
                    } finally {
                        c.close();
                    }
                } else {
                    final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                                    rawContactId);
                    lookupUri = RawContacts.getContactLookupUri(resolver, rawContactUri);
                }
                Log.v(TAG, "Saved contact. New URI: " + lookupUri);

                // We can change this back to false later, if we fail to save the contact photo.
                succeeded = true;
                break;

            } catch (RemoteException e) {
                // Something went wrong, bail without success
                Log.e(TAG, "Problem persisting user edits", e);
                break;

            } catch (OperationApplicationException e) {
                // Version consistency failed, re-parent change and try again
                Log.w(TAG, "Version consistency failed, re-parenting: " + e.toString());
                final StringBuilder sb = new StringBuilder(RawContacts._ID + " IN(");
                boolean first = true;
                final int count = state.size();
                for (int i = 0; i < count; i++) {
                    Long rawContactId = state.getRawContactId(i);
                    if (rawContactId != null && rawContactId != -1) {
                        if (!first) {
                            sb.append(',');
                        }
                        sb.append(rawContactId);
                        first = false;
                    }
                }
                sb.append(")");

                if (first) {
                    throw new IllegalStateException("Version consistency failed for a new contact");
                }

                final RawContactDeltaList newState = RawContactDeltaList.fromQuery(
                        isProfile
                                ? RawContactsEntity.PROFILE_CONTENT_URI
                                : RawContactsEntity.CONTENT_URI,
                        resolver, sb.toString(), null, null);
                state = RawContactDeltaList.mergeAfter(newState, state);

                // Update the new state to use profile URIs if appropriate.
                if (isProfile) {
                    for (RawContactDelta delta : state) {
                        delta.setProfileQueryUri();
                    }
                }
            }
        }

        // Now save any updated photos.  We do this at the end to ensure that
        // the ContactProvider already knows about newly-created contacts.
        if (updatedPhotos != null) {
            for (String key : updatedPhotos.keySet()) {
                Uri photoUri = updatedPhotos.getParcelable(key);
                long rawContactId = Long.parseLong(key);

                // If the raw-contact ID is negative, we are saving a new raw-contact;
                // replace the bogus ID with the new one that we actually saved the contact at.
                if (rawContactId < 0) {
                    rawContactId = insertedRawContactId;
                    if (rawContactId == -1) {
                        throw new IllegalStateException(
                                "Could not determine RawContact ID for image insertion");
                    }
                }

                if (!saveUpdatedPhoto(rawContactId, photoUri)) succeeded = false;
            }
        }


        if (callbackIntent != null) {
            if (succeeded) {
                // Mark the intent to indicate that the save was successful (even if the lookup URI
                // is now null).  For local contacts or the local profile, it's possible that the
                // save triggered removal of the contact, so no lookup URI would exist..
                callbackIntent.putExtra(EXTRA_SAVE_SUCCEEDED, true);
            }
            callbackIntent.setData(lookupUri);
            deliverCallback(callbackIntent);
        }
    }

    /**
     * Save updated photo for the specified raw-contact.
     * @return true for success, false for failure
     */
    private boolean saveUpdatedPhoto(long rawContactId, Uri photoUri) {
        final Uri outputUri = Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.DisplayPhoto.CONTENT_DIRECTORY);

        return ContactPhotoUtils.savePhotoFromUriToUri(this, photoUri, outputUri, true);
    }

    /**
     * Find the ID of an existing or newly-inserted raw-contact.  If none exists, return -1.
     */
    private long getRawContactId(RawContactDeltaList state,
            final ArrayList<ContentProviderOperation> diff,
            final ContentProviderResult[] results) {
        long existingRawContactId = state.findRawContactId();
        if (existingRawContactId != -1) {
            return existingRawContactId;
        }

        return getInsertedRawContactId(diff, results);
    }

    /**
     * Find the ID of a newly-inserted raw-contact.  If none exists, return -1.
     */
    private long getInsertedRawContactId(
            final ArrayList<ContentProviderOperation> diff,
            final ContentProviderResult[] results) {
        final int diffSize = diff.size();
        for (int i = 0; i < diffSize; i++) {
            ContentProviderOperation operation = diff.get(i);
            if (operation.getType() == ContentProviderOperation.TYPE_INSERT
                    && operation.getUri().getEncodedPath().contains(
                            RawContacts.CONTENT_URI.getEncodedPath())) {
                return ContentUris.parseId(results[i].uri);
            }
        }
        return -1;
    }

    /**
     * Creates an intent that can be sent to this service to create a new group as
     * well as add new members at the same time.
     *
     * @param context of the application
     * @param account in which the group should be created
     * @param label is the name of the group (cannot be null)
     * @param rawContactsToAdd is an array of raw contact IDs for contacts that
     *            should be added to the group
     * @param callbackActivity is the activity to send the callback intent to
     * @param callbackAction is the intent action for the callback intent
     */
    public static Intent createNewGroupIntent(Context context, AccountWithDataSet account,
            String label, long[] rawContactsToAdd, Class<? extends Activity> callbackActivity,
            String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_CREATE_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_TYPE, account.type);
        serviceIntent.putExtra(ContactSaveService.EXTRA_ACCOUNT_NAME, account.name);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_SET, account.dataSet);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, label);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACTS_TO_ADD, rawContactsToAdd);

        // Callback intent will be invoked by the service once the new group is
        // created.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    private void createGroup(Intent intent) {
        String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
        String accountName = intent.getStringExtra(EXTRA_ACCOUNT_NAME);
        String dataSet = intent.getStringExtra(EXTRA_DATA_SET);
        String label = intent.getStringExtra(EXTRA_GROUP_LABEL);
        final long[] rawContactsToAdd = intent.getLongArrayExtra(EXTRA_RAW_CONTACTS_TO_ADD);

        ContentValues values = new ContentValues();
        values.put(Groups.ACCOUNT_TYPE, accountType);
        values.put(Groups.ACCOUNT_NAME, accountName);
        values.put(Groups.DATA_SET, dataSet);
        values.put(Groups.TITLE, label);

        final ContentResolver resolver = getContentResolver();

        Uri iccGroupUri = null;
        if (BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(accountType)) {
            iccGroupUri = BrcmIccUtils.insertIccCardGroup(resolver, accountName, label);
            if(null == iccGroupUri) {
                Log.e(TAG, "Couldn't insert group with label " + label);
                return;
            }
            if (DEBUG) Log.d(TAG, "createGroup(): iccGroupUri = " + iccGroupUri);

            values.put(Groups.SOURCE_ID, String.valueOf(ContentUris.parseId(iccGroupUri)));
        }

        // Create the new group
        final Uri groupUri = resolver.insert(Groups.CONTENT_URI, values);

        // If there's no URI, then the insertion failed. Abort early because group members can't be
        // added if the group doesn't exist
        if (groupUri == null) {
            Log.e(TAG, "Couldn't create group with label " + label);
            return;
        }
        if (DEBUG) Log.d(TAG, "createGroup(): groupUri = " + groupUri);

        if (BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(accountType)) {
             BrcmIccUtils.addIccMembersToIccGroup(resolver, accountName, rawContactsToAdd, ContentUris.parseId(iccGroupUri));
        }

        // Add new group members
        addMembersToGroup(resolver, rawContactsToAdd, ContentUris.parseId(groupUri));

        // TODO: Move this into the contact editor where it belongs. This needs to be integrated
        // with the way other intent extras that are passed to the {@link ContactEditorActivity}.
        values.clear();
        values.put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
        values.put(GroupMembership.GROUP_ROW_ID, ContentUris.parseId(groupUri));

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        callbackIntent.setData(groupUri);
        // TODO: This can be taken out when the above TODO is addressed
        callbackIntent.putExtra(ContactsContract.Intents.Insert.DATA, Lists.newArrayList(values));
        deliverCallback(callbackIntent);
    }

    /**
     * Creates an intent that can be sent to this service to rename a group.
     */
    public static Intent createGroupRenameIntent(Context context, long groupId, String newLabel,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_RENAME_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_ID, groupId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, newLabel);

        // Callback intent will be invoked by the service once the group is renamed.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    private void renameGroup(Intent intent) {
        long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
        String label = intent.getStringExtra(EXTRA_GROUP_LABEL);

        if (groupId == -1) {
            Log.e(TAG, "Invalid arguments for renameGroup request");
            return;
        }

        final ContentResolver resolver = getContentResolver();
        String[] groupInfo = BrcmIccUtils.getIccGroupInfoFromGroupId(resolver, groupId);
        if(BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(groupInfo[0])) {
            final SimCardID simId;
            if (BrcmIccUtils.ACCOUNT_NAME_SIM2.equals(groupInfo[1])) {
                simId = SimCardID.ID_ONE;
            } else {
                simId = SimCardID.ID_ZERO;
            }
            BrcmIccUtils.updateIccCardGroup(resolver, simId, groupInfo[2], label, Long.valueOf(groupInfo[3]));
        }

        ContentValues values = new ContentValues();
        values.put(Groups.TITLE, label);
        final Uri groupUri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);
        getContentResolver().update(groupUri, values, null, null);

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        callbackIntent.setData(groupUri);
        deliverCallback(callbackIntent);
    }

    /**
     * Creates an intent that can be sent to this service to delete a group.
     */
    public static Intent createGroupDeletionIntent(Context context, long groupId) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_DELETE_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_ID, groupId);
        return serviceIntent;
    }

    private void deleteGroup(Intent intent) {
        long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
        if (groupId == -1) {
            Log.e(TAG, "Invalid arguments for deleteGroup request");
            return;
        }

        if (DEBUG) Log.d(TAG, "deleteGroup(): groupId = " + groupId);
        final ContentResolver resolver = getContentResolver();
        long[] rawContactIds = BrcmIccUtils.getRawContactIdsFromGroupId(resolver, groupId);
        String[] groupInfo = BrcmIccUtils.getIccGroupInfoFromGroupId(resolver, groupId);
        if (BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(groupInfo[0])) {
            final SimCardID simId;
            if (BrcmIccUtils.ACCOUNT_NAME_SIM2.equals(groupInfo[1])) {
                simId = SimCardID.ID_ONE;
            } else {
                simId = SimCardID.ID_ZERO;
            }

            BrcmIccUtils.removeIccMembersFromIccGroup(resolver, groupInfo[1], rawContactIds, Long.valueOf(groupInfo[3]));
            removeMembersFromGroup(resolver, rawContactIds, groupId);

            BrcmIccUtils.removeIccCardGroup(resolver, simId, Long.valueOf(groupInfo[3]));
        } else if (BrcmIccUtils.ACCOUNT_TYPE_LOCAL.equals(groupInfo[0])) {
            removeMembersFromGroup(resolver, rawContactIds, groupId);
        }

        getContentResolver().delete(
                ContentUris.withAppendedId(Groups.CONTENT_URI, groupId), null, null);
    }

    /**
     * Creates an intent that can be sent to this service to rename a group as
     * well as add and remove members from the group.
     *
     * @param context of the application
     * @param groupId of the group that should be modified
     * @param newLabel is the updated name of the group (can be null if the name
     *            should not be updated)
     * @param rawContactsToAdd is an array of raw contact IDs for contacts that
     *            should be added to the group
     * @param rawContactsToRemove is an array of raw contact IDs for contacts
     *            that should be removed from the group
     * @param callbackActivity is the activity to send the callback intent to
     * @param callbackAction is the intent action for the callback intent
     */
    public static Intent createGroupUpdateIntent(Context context, long groupId, String newLabel,
            long[] rawContactsToAdd, long[] rawContactsToRemove,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_UPDATE_GROUP);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_ID, groupId);
        serviceIntent.putExtra(ContactSaveService.EXTRA_GROUP_LABEL, newLabel);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACTS_TO_ADD, rawContactsToAdd);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACTS_TO_REMOVE,
                rawContactsToRemove);

        // Callback intent will be invoked by the service once the group is updated
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    private void updateGroup(Intent intent) {
        long groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1);
        String label = intent.getStringExtra(EXTRA_GROUP_LABEL);
        long[] rawContactsToAdd = intent.getLongArrayExtra(EXTRA_RAW_CONTACTS_TO_ADD);
        long[] rawContactsToRemove = intent.getLongArrayExtra(EXTRA_RAW_CONTACTS_TO_REMOVE);

        if (groupId == -1) {
            Log.e(TAG, "Invalid arguments for updateGroup request");
            return;
        }

        final ContentResolver resolver = getContentResolver();
        String[] groupInfo = BrcmIccUtils.getIccGroupInfoFromGroupId(resolver, groupId);
        if (BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(groupInfo[0])) {
            //check if group name been changed
            if(null!=label && !label.equals(groupInfo[2])) {
                if (DEBUG) Log.d(TAG, "updateGroup(): group name change from " + groupInfo[2] + " to " + label);

                final SimCardID simId;
                if (BrcmIccUtils.ACCOUNT_NAME_SIM2.equals(groupInfo[1])) {
                    simId = SimCardID.ID_ONE;
                } else {
                    simId = SimCardID.ID_ZERO;
                }

                BrcmIccUtils.updateIccCardGroup(resolver, simId, groupInfo[2], label, Long.valueOf(groupInfo[3]));
            }
            BrcmIccUtils.removeIccMembersFromIccGroup(resolver, groupInfo[1], rawContactsToRemove, Long.valueOf(groupInfo[3]));
            BrcmIccUtils.addIccMembersToIccGroup(resolver, groupInfo[1], rawContactsToAdd, Long.valueOf(groupInfo[3]));
        }

        final Uri groupUri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);

        // Update group name if necessary
        if (label != null) {
            ContentValues values = new ContentValues();
            values.put(Groups.TITLE, label);
            resolver.update(groupUri, values, null, null);
        }

        // Add and remove members if necessary
        addMembersToGroup(resolver, rawContactsToAdd, groupId);
        removeMembersFromGroup(resolver, rawContactsToRemove, groupId);

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        callbackIntent.setData(groupUri);
        deliverCallback(callbackIntent);
    }

    private static void addMembersToGroup(ContentResolver resolver, long[] rawContactsToAdd,
            long groupId) {
        if (rawContactsToAdd == null) {
            return;
        }
        for (long rawContactId : rawContactsToAdd) {
            try {
                final ArrayList<ContentProviderOperation> rawContactOperations =
                        new ArrayList<ContentProviderOperation>();

                // Build an assert operation to ensure the contact is not already in the group
                final ContentProviderOperation.Builder assertBuilder = ContentProviderOperation
                        .newAssertQuery(Data.CONTENT_URI);
                assertBuilder.withSelection(Data.RAW_CONTACT_ID + "=? AND " +
                        Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                        new String[] { String.valueOf(rawContactId),
                        GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId)});
                assertBuilder.withExpectedCount(0);
                rawContactOperations.add(assertBuilder.build());

                // Build an insert operation to add the contact to the group
                final ContentProviderOperation.Builder insertBuilder = ContentProviderOperation
                        .newInsert(Data.CONTENT_URI);
                insertBuilder.withValue(Data.RAW_CONTACT_ID, rawContactId);
                insertBuilder.withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
                insertBuilder.withValue(GroupMembership.GROUP_ROW_ID, groupId);
                rawContactOperations.add(insertBuilder.build());

                if (DEBUG) {
                    for (ContentProviderOperation operation : rawContactOperations) {
                        Log.v(TAG, operation.toString());
                    }
                }

                // Apply batch
                if (!rawContactOperations.isEmpty()) {
                    resolver.applyBatch(ContactsContract.AUTHORITY, rawContactOperations);
                }
            } catch (RemoteException e) {
                // Something went wrong, bail without success
                Log.e(TAG, "Problem persisting user edits for raw contact ID " +
                        String.valueOf(rawContactId), e);
            } catch (OperationApplicationException e) {
                // The assert could have failed because the contact is already in the group,
                // just continue to the next contact
                Log.w(TAG, "Assert failed in adding raw contact ID " +
                        String.valueOf(rawContactId) + ". Already exists in group " +
                        String.valueOf(groupId), e);
            }
        }
    }

    private static void removeMembersFromGroup(ContentResolver resolver, long[] rawContactsToRemove,
            long groupId) {
        if (rawContactsToRemove == null) {
            return;
        }
        for (long rawContactId : rawContactsToRemove) {
            // Apply the delete operation on the data row for the given raw contact's
            // membership in the given group. If no contact matches the provided selection, then
            // nothing will be done. Just continue to the next contact.
            resolver.delete(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=? AND " +
                    Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                    new String[] { String.valueOf(rawContactId),
                    GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId)});
        }
    }

    /**
     * Creates an intent that can be sent to this service to star or un-star a contact.
     */
    public static Intent createSetStarredIntent(Context context, Uri contactUri, boolean value) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_STARRED);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        serviceIntent.putExtra(ContactSaveService.EXTRA_STARRED_FLAG, value);

        return serviceIntent;
    }

    private void setStarred(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        boolean value = intent.getBooleanExtra(EXTRA_STARRED_FLAG, false);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for setStarred request");
            return;
        }

        final ContentValues values = new ContentValues(1);
        values.put(Contacts.STARRED, value);
        getContentResolver().update(contactUri, values, null, null);

        // Undemote the contact if necessary
        final Cursor c = getContentResolver().query(contactUri, new String[] {Contacts._ID},
                null, null, null);
        try {
            if (c.moveToFirst()) {
                final long id = c.getLong(0);

                // Don't bother undemoting if this contact is the user's profile.
                if (id < Profile.MIN_ID) {
                    values.clear();
                    values.put(String.valueOf(id), PinnedPositions.UNDEMOTE);
                    getContentResolver().update(PinnedPositions.UPDATE_URI, values, null, null);
                }
            }
        } finally {
            c.close();
        }
    }

    /**
     * Creates an intent that can be sent to this service to set the redirect to voicemail.
     */
    public static Intent createSetSendToVoicemail(Context context, Uri contactUri,
            boolean value) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_SEND_TO_VOICEMAIL);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        serviceIntent.putExtra(ContactSaveService.EXTRA_SEND_TO_VOICEMAIL_FLAG, value);

        return serviceIntent;
    }

    private void setSendToVoicemail(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        boolean value = intent.getBooleanExtra(EXTRA_SEND_TO_VOICEMAIL_FLAG, false);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for setRedirectToVoicemail");
            return;
        }

        final ContentValues values = new ContentValues(1);
        values.put(Contacts.SEND_TO_VOICEMAIL, value);
        getContentResolver().update(contactUri, values, null, null);
    }

    /**
     * Creates an intent that can be sent to this service to save the contact's ringtone.
     */
    public static Intent createSetRingtone(Context context, Uri contactUri,
            String value) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_RINGTONE);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CUSTOM_RINGTONE, value);

        return serviceIntent;
    }

    private void setRingtone(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        String value = intent.getStringExtra(EXTRA_CUSTOM_RINGTONE);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for setRingtone");
            return;
        }
        ContentValues values = new ContentValues(1);
        values.put(Contacts.CUSTOM_RINGTONE, value);
        getContentResolver().update(contactUri, values, null, null);
    }

    /**
     * Creates an intent that sets the selected data item as super primary (default)
     */
    public static Intent createSetSuperPrimaryIntent(Context context, long dataId) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SET_SUPER_PRIMARY);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_ID, dataId);
        return serviceIntent;
    }

    private void setSuperPrimary(Intent intent) {
        long dataId = intent.getLongExtra(EXTRA_DATA_ID, -1);
        if (dataId == -1) {
            Log.e(TAG, "Invalid arguments for setSuperPrimary request");
            return;
        }

        ContactUpdateUtils.setSuperPrimary(this, dataId);
    }

    /**
     * Creates an intent that clears the primary flag of all data items that belong to the same
     * raw_contact as the given data item. Will only clear, if the data item was primary before
     * this call
     */
    public static Intent createClearPrimaryIntent(Context context, long dataId) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_CLEAR_PRIMARY);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DATA_ID, dataId);
        return serviceIntent;
    }

    private void clearPrimary(Intent intent) {
        long dataId = intent.getLongExtra(EXTRA_DATA_ID, -1);
        if (dataId == -1) {
            Log.e(TAG, "Invalid arguments for clearPrimary request");
            return;
        }

        // Update the primary values in the data record.
        ContentValues values = new ContentValues(1);
        values.put(Data.IS_SUPER_PRIMARY, 0);
        values.put(Data.IS_PRIMARY, 0);

        getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI, dataId),
                values, null, null);
    }

    /**
     * Creates an intent that can be sent to this service to delete a contact.
     */
    public static Intent createDeleteContactIntent(Context context, Uri contactUri) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_DELETE_CONTACT);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_URI, contactUri);
        return serviceIntent;
    }

    private void deleteContact(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for deleteContact request");
            return;
        }

        final ContentResolver resolver = getContentResolver();
        final long contactId = ContentUris.parseId(contactUri);
        final long rawContactId = BrcmIccUtils.getRawContactId(resolver, contactId);
        String accountType = BrcmIccUtils.getRawContactAccountTypeFromContactId(resolver, contactId);

        ArrayList<Long> groupIds = BrcmIccUtils.getGroupIdsFromRawContactId(resolver, rawContactId);
        final long[] rawContactIdAry = new long[]{rawContactId};
        if (BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(accountType)) {
            BrcmIccUtils.deleteIccContact(resolver, contactId);
            for (Long groupId : groupIds) {
                removeMembersFromGroup(resolver, rawContactIdAry, groupId);
            }
        } else if (BrcmIccUtils.ACCOUNT_TYPE_LOCAL.equals(accountType)) {
            for (Long groupId : groupIds) {
                removeMembersFromGroup(resolver, rawContactIdAry, groupId);
            }
        }

        getContentResolver().delete(contactUri, null, null);
    }

    /**
     * Creates an intent that can be sent to this service to join two contacts.
     */
    public static Intent createJoinContactsIntent(Context context, long contactId1,
            long contactId2, boolean contactWritable,
            Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_JOIN_CONTACTS);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_ID1, contactId1);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_ID2, contactId2);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_WRITABLE, contactWritable);

        // Callback intent will be invoked by the service once the contacts are joined.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }


    private interface JoinContactQuery {
        String[] PROJECTION = {
                RawContacts._ID,
                RawContacts.CONTACT_ID,
                RawContacts.NAME_VERIFIED,
                RawContacts.DISPLAY_NAME_SOURCE,
        };

        String SELECTION = RawContacts.CONTACT_ID + "=? OR " + RawContacts.CONTACT_ID + "=?";

        int _ID = 0;
        int CONTACT_ID = 1;
        int NAME_VERIFIED = 2;
        int DISPLAY_NAME_SOURCE = 3;
    }

    private void joinContacts(Intent intent) {
        long contactId1 = intent.getLongExtra(EXTRA_CONTACT_ID1, -1);
        long contactId2 = intent.getLongExtra(EXTRA_CONTACT_ID2, -1);
        boolean writable = intent.getBooleanExtra(EXTRA_CONTACT_WRITABLE, false);
        if (contactId1 == -1 || contactId2 == -1) {
            Log.e(TAG, "Invalid arguments for joinContacts request");
            return;
        }

        final ContentResolver resolver = getContentResolver();

        // Load raw contact IDs for all raw contacts involved - currently edited and selected
        // in the join UIs
        Cursor c = resolver.query(RawContacts.CONTENT_URI,
                JoinContactQuery.PROJECTION,
                JoinContactQuery.SELECTION,
                new String[]{String.valueOf(contactId1), String.valueOf(contactId2)}, null);

        long rawContactIds[];
        long verifiedNameRawContactId = -1;
        try {
            if (c.getCount() == 0) {
                return;
            }
            int maxDisplayNameSource = -1;
            rawContactIds = new long[c.getCount()];
            for (int i = 0; i < rawContactIds.length; i++) {
                c.moveToPosition(i);
                long rawContactId = c.getLong(JoinContactQuery._ID);
                rawContactIds[i] = rawContactId;
                int nameSource = c.getInt(JoinContactQuery.DISPLAY_NAME_SOURCE);
                if (nameSource > maxDisplayNameSource) {
                    maxDisplayNameSource = nameSource;
                }
            }

            // Find an appropriate display name for the joined contact:
            // if should have a higher DisplayNameSource or be the name
            // of the original contact that we are joining with another.
            if (writable) {
                for (int i = 0; i < rawContactIds.length; i++) {
                    c.moveToPosition(i);
                    if (c.getLong(JoinContactQuery.CONTACT_ID) == contactId1) {
                        int nameSource = c.getInt(JoinContactQuery.DISPLAY_NAME_SOURCE);
                        if (nameSource == maxDisplayNameSource
                                && (verifiedNameRawContactId == -1
                                        || c.getInt(JoinContactQuery.NAME_VERIFIED) != 0)) {
                            verifiedNameRawContactId = c.getLong(JoinContactQuery._ID);
                        }
                    }
                }
            }
        } finally {
            c.close();
        }

        // For each pair of raw contacts, insert an aggregation exception
        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        for (int i = 0; i < rawContactIds.length; i++) {
            for (int j = 0; j < rawContactIds.length; j++) {
                if (i != j) {
                    buildJoinContactDiff(operations, rawContactIds[i], rawContactIds[j]);
                }
            }
        }

        // Mark the original contact as "name verified" to make sure that the contact
        // display name does not change as a result of the join
        if (verifiedNameRawContactId != -1) {
            Builder builder = ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, verifiedNameRawContactId));
            builder.withValue(RawContacts.NAME_VERIFIED, 1);
            operations.add(builder.build());
        }

        boolean success = false;
        // Apply all aggregation exceptions as one batch
        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, operations);
            showToast(R.string.contactsJoinedMessage);
            success = true;
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        } catch (OperationApplicationException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
        }

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        if (success) {
            Uri uri = RawContacts.getContactLookupUri(resolver,
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactIds[0]));
            callbackIntent.setData(uri);
        }
        deliverCallback(callbackIntent);
    }

    /**
     * Construct a {@link AggregationExceptions#TYPE_KEEP_TOGETHER} ContentProviderOperation.
     */
    private void buildJoinContactDiff(ArrayList<ContentProviderOperation> operations,
            long rawContactId1, long rawContactId2) {
        Builder builder =
                ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        operations.add(builder.build());
    }

    /**
     * Shows a toast on the UI thread.
     */
    private void showToast(final int message) {
        mMainHandler.post(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(ContactSaveService.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void deliverCallback(final Intent callbackIntent) {
        mMainHandler.post(new Runnable() {

            @Override
            public void run() {
                deliverCallbackOnUiThread(callbackIntent);
            }
        });
    }

    void deliverCallbackOnUiThread(final Intent callbackIntent) {
        // TODO: this assumes that if there are multiple instances of the same
        // activity registered, the last one registered is the one waiting for
        // the callback. Validity of this assumption needs to be verified.
        for (Listener listener : sListeners) {
            if (callbackIntent.getComponent().equals(
                    ((Activity) listener).getIntent().getComponent())) {
                listener.onServiceCompleted(callbackIntent);
                return;
            }
        }
    }
}
