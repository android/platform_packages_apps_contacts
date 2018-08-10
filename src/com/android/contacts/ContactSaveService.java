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

import static android.Manifest.permission.WRITE_CONTACTS;

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
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ResultReceiver;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.compat.PinnedPositionsCompat;
import com.android.contacts.database.ContactUpdateUtils;
import com.android.contacts.database.SimContactDao;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.CPOWrapper;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.RawContactDeltaList;
import com.android.contacts.model.RawContactModifier;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.ContactDisplayUtils;
import com.android.contacts.util.ContactPhotoUtils;
import com.android.contacts.util.PermissionsUtil;
import com.android.contactsbind.FeedbackHelper;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
    public static final String EXTRA_ACCOUNT = "account";
    public static final String EXTRA_CONTENT_VALUES = "contentValues";
    public static final String EXTRA_CALLBACK_INTENT = "callbackIntent";
    public static final String EXTRA_RESULT_RECEIVER = "resultReceiver";
    public static final String EXTRA_RAW_CONTACT_IDS = "rawContactIds";

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
    public static final String ACTION_DELETE_MULTIPLE_CONTACTS = "deleteMultipleContacts";
    public static final String EXTRA_CONTACT_URI = "contactUri";
    public static final String EXTRA_CONTACT_IDS = "contactIds";
    public static final String EXTRA_STARRED_FLAG = "starred";
    public static final String EXTRA_DISPLAY_NAME = "extraDisplayName";
    public static final String EXTRA_DISPLAY_NAME_ARRAY = "extraDisplayNameArray";

    public static final String ACTION_SET_SUPER_PRIMARY = "setSuperPrimary";
    public static final String ACTION_CLEAR_PRIMARY = "clearPrimary";
    public static final String EXTRA_DATA_ID = "dataId";

    public static final String ACTION_SPLIT_CONTACT = "splitContact";
    public static final String EXTRA_HARD_SPLIT = "extraHardSplit";

    public static final String ACTION_JOIN_CONTACTS = "joinContacts";
    public static final String ACTION_JOIN_SEVERAL_CONTACTS = "joinSeveralContacts";
    public static final String EXTRA_CONTACT_ID1 = "contactId1";
    public static final String EXTRA_CONTACT_ID2 = "contactId2";

    public static final String ACTION_SET_SEND_TO_VOICEMAIL = "sendToVoicemail";
    public static final String EXTRA_SEND_TO_VOICEMAIL_FLAG = "sendToVoicemailFlag";

    public static final String ACTION_SET_RINGTONE = "setRingtone";
    public static final String EXTRA_CUSTOM_RINGTONE = "customRingtone";

    public static final String ACTION_UNDO = "undo";
    public static final String EXTRA_UNDO_ACTION = "undoAction";
    public static final String EXTRA_UNDO_DATA = "undoData";

    // For debugging and testing what happens when requests are queued up.
    public static final String ACTION_SLEEP = "sleep";
    public static final String EXTRA_SLEEP_DURATION = "sleepDuration";

    public static final String BROADCAST_GROUP_DELETED = "groupDeleted";
    public static final String BROADCAST_LINK_COMPLETE = "linkComplete";
    public static final String BROADCAST_UNLINK_COMPLETE = "unlinkComplete";

    public static final String BROADCAST_SERVICE_STATE_CHANGED = "serviceStateChanged";

    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_COUNT = "count";

    public static final int CP2_ERROR = 0;
    public static final int CONTACTS_LINKED = 1;
    public static final int CONTACTS_SPLIT = 2;
    public static final int BAD_ARGUMENTS = 3;
    public static final int RESULT_UNKNOWN = 0;
    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAILURE = 2;

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

    private static final int MAX_CONTACTS_PROVIDER_BATCH_SIZE = 499;

    public interface Listener {
        public void onServiceCompleted(Intent callbackIntent);
    }

    private static final CopyOnWriteArrayList<Listener> sListeners =
            new CopyOnWriteArrayList<Listener>();

    // Holds the current state of the service
    private static final State sState = new State();

    private Handler mMainHandler;
    private GroupsDao mGroupsDao;
    private SimContactDao mSimContactDao;

    public ContactSaveService() {
        super(TAG);
        setIntentRedelivery(true);
        mMainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mGroupsDao = new GroupsDaoImpl(this);
        mSimContactDao = SimContactDao.create(this);
    }

    public static void registerListener(Listener listener) {
        if (!(listener instanceof Activity)) {
            throw new ClassCastException("Only activities can be registered to"
                    + " receive callback from " + ContactSaveService.class.getName());
        }
        sListeners.add(0, listener);
    }

    public static boolean canUndo(Intent resultIntent) {
        return resultIntent.hasExtra(EXTRA_UNDO_DATA);
    }

    public static void unregisterListener(Listener listener) {
        sListeners.remove(listener);
    }

    public static State getState() {
        return sState;
    }

    private void notifyStateChanged() {
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(BROADCAST_SERVICE_STATE_CHANGED));
    }

    /**
     * Returns true if the ContactSaveService was started successfully and false if an exception
     * was thrown and a Toast error message was displayed.
     */
    public static boolean startService(Context context, Intent intent, int saveMode) {
        try {
            context.startService(intent);
        } catch (Exception exception) {
            final int resId;
            switch (saveMode) {
                case ContactEditorActivity.ContactEditor.SaveMode.SPLIT:
                    resId = R.string.contactUnlinkErrorToast;
                    break;
                case ContactEditorActivity.ContactEditor.SaveMode.RELOAD:
                    resId = R.string.contactJoinErrorToast;
                    break;
                case ContactEditorActivity.ContactEditor.SaveMode.CLOSE:
                    resId = R.string.contactSavedErrorToast;
                    break;
                default:
                    resId = R.string.contactGenericErrorToast;
            }
            Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * Utility method that starts service and handles exception.
     */
    public static void startService(Context context, Intent intent) {
        try {
            context.startService(intent);
        } catch (Exception exception) {
            Toast.makeText(context, R.string.contactGenericErrorToast, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public Object getSystemService(String name) {
        Object service = super.getSystemService(name);
        if (service != null) {
            return service;
        }

        return getApplicationContext().getSystemService(name);
    }

    // Parent classes Javadoc says not to override this method but we're doing it just to update
    // our state which should be OK since we're still doing the work in onHandleIntent
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sState.onStart(intent);
        notifyStateChanged();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        if (intent == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onHandleIntent: could not handle null intent");
            }
            return;
        }
        if (!PermissionsUtil.hasPermission(this, WRITE_CONTACTS)) {
            Log.w(TAG, "No WRITE_CONTACTS permission, unable to write to CP2");
            // TODO: add more specific error string such as "Turn on Contacts
            // permission to update your contacts"
            showToast(R.string.contactSavedErrorToast);
            return;
        }

        // Call an appropriate method. If we're sure it affects how incoming phone calls are
        // handled, then notify the fact to in-call screen.
        String action = intent.getAction();
        if (ACTION_NEW_RAW_CONTACT.equals(action)) {
            createRawContact(intent);
        } else if (ACTION_SAVE_CONTACT.equals(action)) {
            saveContact(intent);
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
        } else if (ACTION_DELETE_MULTIPLE_CONTACTS.equals(action)) {
            deleteMultipleContacts(intent);
        } else if (ACTION_DELETE_CONTACT.equals(action)) {
            deleteContact(intent);
        } else if (ACTION_SPLIT_CONTACT.equals(action)) {
            splitContact(intent);
        } else if (ACTION_JOIN_CONTACTS.equals(action)) {
            joinContacts(intent);
        } else if (ACTION_JOIN_SEVERAL_CONTACTS.equals(action)) {
            joinSeveralContacts(intent);
        } else if (ACTION_SET_SEND_TO_VOICEMAIL.equals(action)) {
            setSendToVoicemail(intent);
        } else if (ACTION_SET_RINGTONE.equals(action)) {
            setRingtone(intent);
        } else if (ACTION_UNDO.equals(action)) {
            undo(intent);
        } else if (ACTION_SLEEP.equals(action)) {
            sleepForDebugging(intent);
        }

        sState.onFinish(intent);
        notifyStateChanged();
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
                callbackActivity, callbackAction, bundle,
                /* joinContactIdExtraKey */ null, /* joinContactId */ null);
    }

    /**
     * Creates an intent that can be sent to this service to create a new raw contact
     * using data presented as a set of ContentValues.
     * This variant is used when multiple contacts' photos may be updated, as in the
     * Contact Editor.
     *
     * @param updatedPhotos maps each raw-contact's ID to the file-path of the new photo.
     * @param joinContactIdExtraKey the key used to pass the joinContactId in the callback intent.
     * @param joinContactId the raw contact ID to join to the contact after doing the save.
     */
    public static Intent createSaveContactIntent(Context context, RawContactDeltaList state,
            String saveModeExtraKey, int saveMode, boolean isProfile,
            Class<? extends Activity> callbackActivity, String callbackAction,
            Bundle updatedPhotos, String joinContactIdExtraKey, Long joinContactId) {
        Intent serviceIntent = new Intent(
                context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SAVE_CONTACT);
        serviceIntent.putExtra(EXTRA_CONTACT_STATE, (Parcelable) state);
        serviceIntent.putExtra(EXTRA_SAVE_IS_PROFILE, isProfile);
        serviceIntent.putExtra(EXTRA_SAVE_MODE, saveMode);

        if (updatedPhotos != null) {
            serviceIntent.putExtra(EXTRA_UPDATED_PHOTOS, (Parcelable) updatedPhotos);
        }

        if (callbackActivity != null) {
            // Callback intent will be invoked by the service once the contact is
            // saved.  The service will put the URI of the new contact as "data" on
            // the callback intent.
            Intent callbackIntent = new Intent(context, callbackActivity);
            callbackIntent.putExtra(saveModeExtraKey, saveMode);
            if (joinContactIdExtraKey != null && joinContactId != null) {
                callbackIntent.putExtra(joinContactIdExtraKey, joinContactId);
            }
            callbackIntent.setAction(callbackAction);
            serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);
        }
        return serviceIntent;
    }

    private void saveContact(Intent intent) {
        RawContactDeltaList state = intent.getParcelableExtra(EXTRA_CONTACT_STATE);
        boolean isProfile = intent.getBooleanExtra(EXTRA_SAVE_IS_PROFILE, false);
        Bundle updatedPhotos = intent.getParcelableExtra(EXTRA_UPDATED_PHOTOS);

        if (state == null) {
            Log.e(TAG, "Invalid arguments for saveContact request");
            return;
        }

        int saveMode = intent.getIntExtra(EXTRA_SAVE_MODE, -1);
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
                // Build operations and try applying
                final ArrayList<CPOWrapper> diffWrapper = state.buildDiffWrapper();

                final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();

                for (CPOWrapper cpoWrapper : diffWrapper) {
                    diff.add(cpoWrapper.getOperation());
                }

                if (DEBUG) {
                    Log.v(TAG, "Content Provider Operations:");
                    for (ContentProviderOperation operation : diff) {
                        Log.v(TAG, operation.toString());
                    }
                }

                int numberProcessed = 0;
                boolean batchFailed = false;
                final ContentProviderResult[] results = new ContentProviderResult[diff.size()];
                while (numberProcessed < diff.size()) {
                    final int subsetCount = applyDiffSubset(diff, numberProcessed, results, resolver);
                    if (subsetCount == -1) {
                        Log.w(TAG, "Resolver.applyBatch failed in saveContacts");
                        batchFailed = true;
                        break;
                    } else {
                        numberProcessed += subsetCount;
                    }
                }

                if (batchFailed) {
                    // Retry save
                    continue;
                }

                final long rawContactId = getRawContactId(state, diffWrapper, results);
                if (rawContactId == -1) {
                    throw new IllegalStateException("Could not determine RawContact ID after save");
                }
                // We don't have to check to see if the value is still -1.  If we reach here,
                // the previous loop iteration didn't succeed, so any ID that we obtained is bogus.
                insertedRawContactId = getInsertedRawContactId(diffWrapper, results);
                if (isProfile) {
                    // Since the profile supports local raw contacts, which may have been completely
                    // removed if all information was removed, we need to do a special query to
                    // get the lookup URI for the profile contact (if it still exists).
                    Cursor c = resolver.query(Profile.CONTENT_URI,
                            new String[] {Contacts._ID, Contacts.LOOKUP_KEY},
                            null, null, null);
                    if (c == null) {
                        continue;
                    }
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
                if (lookupUri != null && Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, "Saved contact. New URI: " + lookupUri);
                }

                // We can change this back to false later, if we fail to save the contact photo.
                succeeded = true;
                break;

            } catch (RemoteException e) {
                // Something went wrong, bail without success
                FeedbackHelper.sendFeedback(this, TAG, "Problem persisting user edits", e);
                break;

            } catch (IllegalArgumentException e) {
                // This is thrown by applyBatch on malformed requests
                FeedbackHelper.sendFeedback(this, TAG, "Problem persisting user edits", e);
                showToast(R.string.contactSavedErrorToast);
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
                    throw new IllegalStateException(
                            "Version consistency failed for a new contact", e);
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
                }

                // If the save failed, insertedRawContactId will be -1
                if (rawContactId < 0 || !saveUpdatedPhoto(rawContactId, photoUri, saveMode)) {
                    succeeded = false;
                }
            }
        }

        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
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
     * Splits "diff" into subsets based on "MAX_CONTACTS_PROVIDER_BATCH_SIZE", applies each of the
     * subsets, adds the returned array to "results".
     *
     * @return the size of the array, if not null; -1 when the array is null.
     */
    private int applyDiffSubset(ArrayList<ContentProviderOperation> diff, int offset,
            ContentProviderResult[] results, ContentResolver resolver)
            throws RemoteException, OperationApplicationException {
        final int subsetCount = Math.min(diff.size() - offset, MAX_CONTACTS_PROVIDER_BATCH_SIZE);
        final ArrayList<ContentProviderOperation> subset = new ArrayList<>();
        subset.addAll(diff.subList(offset, offset + subsetCount));
        final ContentProviderResult[] subsetResult = resolver.applyBatch(ContactsContract
                .AUTHORITY, subset);
        if (subsetResult == null || (offset + subsetResult.length) > results.length) {
            return -1;
        }
        for (ContentProviderResult c : subsetResult) {
            results[offset++] = c;
        }
        return subsetResult.length;
    }

    /**
     * Save updated photo for the specified raw-contact.
     * @return true for success, false for failure
     */
    private boolean saveUpdatedPhoto(long rawContactId, Uri photoUri, int saveMode) {
        final Uri outputUri = Uri.withAppendedPath(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                RawContacts.DisplayPhoto.CONTENT_DIRECTORY);

        return ContactPhotoUtils.savePhotoFromUriToUri(this, photoUri, outputUri, (saveMode == 0));
    }

    /**
     * Find the ID of an existing or newly-inserted raw-contact.  If none exists, return -1.
     */
    private long getRawContactId(RawContactDeltaList state,
            final ArrayList<CPOWrapper> diffWrapper,
            final ContentProviderResult[] results) {
        long existingRawContactId = state.findRawContactId();
        if (existingRawContactId != -1) {
            return existingRawContactId;
        }

        return getInsertedRawContactId(diffWrapper, results);
    }

    /**
     * Find the ID of a newly-inserted raw-contact.  If none exists, return -1.
     */
    private long getInsertedRawContactId(
            final ArrayList<CPOWrapper> diffWrapper, final ContentProviderResult[] results) {
        if (results == null) {
            return -1;
        }
        final int diffSize = diffWrapper.size();
        final int numResults = results.length;
        for (int i = 0; i < diffSize && i < numResults; i++) {
            final CPOWrapper cpoWrapper = diffWrapper.get(i);
            final boolean isInsert = CompatUtils.isInsertCompat(cpoWrapper);
            if (isInsert && cpoWrapper.getOperation().getUri().getEncodedPath().contains(
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

        // Create the new group
        final Uri groupUri = mGroupsDao.create(label,
                new AccountWithDataSet(accountName, accountType, dataSet));
        final ContentResolver resolver = getContentResolver();

        // If there's no URI, then the insertion failed. Abort early because group members can't be
        // added if the group doesn't exist
        if (groupUri == null) {
            Log.e(TAG, "Couldn't create group with label " + label);
            return;
        }

        // Add new group members
        addMembersToGroup(resolver, rawContactsToAdd, ContentUris.parseId(groupUri));

        ContentValues values = new ContentValues();
        // TODO: Move this into the contact editor where it belongs. This needs to be integrated
        // with the way other intent extras that are passed to the
        // {@link ContactEditorActivity}.
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
        final Intent serviceIntent = new Intent(context, ContactSaveService.class);
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
        final Uri groupUri = ContentUris.withAppendedId(Groups.CONTENT_URI, groupId);

        final Intent callbackIntent = new Intent(BROADCAST_GROUP_DELETED);
        final Bundle undoData = mGroupsDao.captureDeletionUndoData(groupUri);
        callbackIntent.putExtra(EXTRA_UNDO_ACTION, ACTION_DELETE_GROUP);
        callbackIntent.putExtra(EXTRA_UNDO_DATA, undoData);

        mGroupsDao.delete(groupUri);

        LocalBroadcastManager.getInstance(this).sendBroadcast(callbackIntent);
    }

    public static Intent createUndoIntent(Context context, Intent resultIntent) {
        final Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_UNDO);
        serviceIntent.putExtras(resultIntent);
        return serviceIntent;
    }

    private void undo(Intent intent) {
        final String actionToUndo = intent.getStringExtra(EXTRA_UNDO_ACTION);
        if (ACTION_DELETE_GROUP.equals(actionToUndo)) {
            mGroupsDao.undoDeletion(intent.getBundleExtra(EXTRA_UNDO_DATA));
        }
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

    private void addMembersToGroup(ContentResolver resolver, long[] rawContactsToAdd,
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
                FeedbackHelper.sendFeedback(this, TAG,
                        "Problem persisting user edits for raw contact ID " +
                                String.valueOf(rawContactId), e);
            } catch (OperationApplicationException e) {
                // The assert could have failed because the contact is already in the group,
                // just continue to the next contact
                FeedbackHelper.sendFeedback(this, TAG,
                        "Assert failed in adding raw contact ID " +
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
        if (c == null) {
            return;
        }
        try {
            if (c.moveToFirst()) {
                final long id = c.getLong(0);

                // Don't bother undemoting if this contact is the user's profile.
                if (id < Profile.MIN_ID) {
                    PinnedPositionsCompat.undemote(getContentResolver(), id);
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

    /**
     * Creates an intent that can be sent to this service to delete multiple contacts.
     */
    public static Intent createDeleteMultipleContactsIntent(Context context,
            long[] contactIds, final String[] names) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_DELETE_MULTIPLE_CONTACTS);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_IDS, contactIds);
        serviceIntent.putExtra(ContactSaveService.EXTRA_DISPLAY_NAME_ARRAY, names);
        return serviceIntent;
    }

    private void deleteContact(Intent intent) {
        Uri contactUri = intent.getParcelableExtra(EXTRA_CONTACT_URI);
        if (contactUri == null) {
            Log.e(TAG, "Invalid arguments for deleteContact request");
            return;
        }

        getContentResolver().delete(contactUri, null, null);
    }

    private void deleteMultipleContacts(Intent intent) {
        final long[] contactIds = intent.getLongArrayExtra(EXTRA_CONTACT_IDS);
        if (contactIds == null) {
            Log.e(TAG, "Invalid arguments for deleteMultipleContacts request");
            return;
        }
        for (long contactId : contactIds) {
            final Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
            getContentResolver().delete(contactUri, null, null);
        }
        final String[] names = intent.getStringArrayExtra(
                ContactSaveService.EXTRA_DISPLAY_NAME_ARRAY);
        final String deleteToastMessage;
        if (contactIds.length != names.length || names.length == 0) {
            deleteToastMessage = getResources().getQuantityString(
                    R.plurals.contacts_deleted_toast, contactIds.length);
        } else if (names.length == 1) {
            deleteToastMessage = getResources().getString(
                    R.string.contacts_deleted_one_named_toast, (Object[]) names);
        } else if (names.length == 2) {
            deleteToastMessage = getResources().getString(
                    R.string.contacts_deleted_two_named_toast, (Object[]) names);
        } else {
            deleteToastMessage = getResources().getString(
                    R.string.contacts_deleted_many_named_toast, (Object[]) names);
        }

        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ContactSaveService.this, deleteToastMessage, Toast.LENGTH_LONG)
                        .show();
            }
        });
    }

    /**
     * Creates an intent that can be sent to this service to split a contact into it's constituent
     * pieces. This will set the raw contact ids to {@link AggregationExceptions#TYPE_AUTOMATIC} so
     * they may be re-merged by the auto-aggregator.
     */
    public static Intent createSplitContactIntent(Context context, long[][] rawContactIds,
            ResultReceiver receiver) {
        final Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SPLIT_CONTACT);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACT_IDS, rawContactIds);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RESULT_RECEIVER, receiver);
        return serviceIntent;
    }

    /**
     * Creates an intent that can be sent to this service to split a contact into it's constituent
     * pieces. This will explicitly set the raw contact ids to
     * {@link AggregationExceptions#TYPE_KEEP_SEPARATE}.
     */
    public static Intent createHardSplitContactIntent(Context context, long[][] rawContactIds) {
        final Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_SPLIT_CONTACT);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RAW_CONTACT_IDS, rawContactIds);
        serviceIntent.putExtra(ContactSaveService.EXTRA_HARD_SPLIT, true);
        return serviceIntent;
    }

    private void splitContact(Intent intent) {
        final long rawContactIds[][] = (long[][]) intent
                .getSerializableExtra(EXTRA_RAW_CONTACT_IDS);
        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);
        final boolean hardSplit = intent.getBooleanExtra(EXTRA_HARD_SPLIT, false);
        if (rawContactIds == null) {
            Log.e(TAG, "Invalid argument for splitContact request");
            if (receiver != null) {
                receiver.send(BAD_ARGUMENTS, new Bundle());
            }
            return;
        }
        final int batchSize = MAX_CONTACTS_PROVIDER_BATCH_SIZE;
        final ContentResolver resolver = getContentResolver();
        final ArrayList<ContentProviderOperation> operations = new ArrayList<>(batchSize);
        for (int i = 0; i < rawContactIds.length; i++) {
            for (int j = 0; j < rawContactIds.length; j++) {
                if (i != j) {
                    if (!buildSplitTwoContacts(operations, rawContactIds[i], rawContactIds[j],
                            hardSplit)) {
                        if (receiver != null) {
                            receiver.send(CP2_ERROR, new Bundle());
                            return;
                        }
                    }
                }
            }
        }
        if (operations.size() > 0 && !applyOperations(resolver, operations)) {
            if (receiver != null) {
                receiver.send(CP2_ERROR, new Bundle());
            }
            return;
        }
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(BROADCAST_UNLINK_COMPLETE));
        if (receiver != null) {
            receiver.send(CONTACTS_SPLIT, new Bundle());
        } else {
            showToast(R.string.contactUnlinkedToast);
        }
    }

    /**
     * Insert aggregation exception ContentProviderOperations between {@param rawContactIds1}
     * and {@param rawContactIds2} to {@param operations}.
     * @return false if an error occurred, true otherwise.
     */
    private boolean buildSplitTwoContacts(ArrayList<ContentProviderOperation> operations,
            long[] rawContactIds1, long[] rawContactIds2, boolean hardSplit) {
        if (rawContactIds1 == null || rawContactIds2 == null) {
            Log.e(TAG, "Invalid arguments for splitContact request");
            return false;
        }
        // For each pair of raw contacts, insert an aggregation exception
        final ContentResolver resolver = getContentResolver();
        // The maximum number of operations per batch (aka yield point) is 500. See b/22480225
        final int batchSize = MAX_CONTACTS_PROVIDER_BATCH_SIZE;
        for (int i = 0; i < rawContactIds1.length; i++) {
            for (int j = 0; j < rawContactIds2.length; j++) {
                buildSplitContactDiff(operations, rawContactIds1[i], rawContactIds2[j], hardSplit);
                // Before we get to 500 we need to flush the operations list
                if (operations.size() > 0 && operations.size() % batchSize == 0) {
                    if (!applyOperations(resolver, operations)) {
                        return false;
                    }
                    operations.clear();
                }
            }
        }
        return true;
    }

    /**
     * Creates an intent that can be sent to this service to join two contacts.
     * The resulting contact uses the name from {@param contactId1} if possible.
     */
    public static Intent createJoinContactsIntent(Context context, long contactId1,
            long contactId2, Class<? extends Activity> callbackActivity, String callbackAction) {
        Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_JOIN_CONTACTS);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_ID1, contactId1);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_ID2, contactId2);

        // Callback intent will be invoked by the service once the contacts are joined.
        Intent callbackIntent = new Intent(context, callbackActivity);
        callbackIntent.setAction(callbackAction);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CALLBACK_INTENT, callbackIntent);

        return serviceIntent;
    }

    /**
     * Creates an intent to join all raw contacts inside {@param contactIds}'s contacts.
     * No special attention is paid to where the resulting contact's name is taken from.
     */
    public static Intent createJoinSeveralContactsIntent(Context context, long[] contactIds,
            ResultReceiver receiver) {
        final Intent serviceIntent = new Intent(context, ContactSaveService.class);
        serviceIntent.setAction(ContactSaveService.ACTION_JOIN_SEVERAL_CONTACTS);
        serviceIntent.putExtra(ContactSaveService.EXTRA_CONTACT_IDS, contactIds);
        serviceIntent.putExtra(ContactSaveService.EXTRA_RESULT_RECEIVER, receiver);
        return serviceIntent;
    }

    /**
     * Creates an intent to join all raw contacts inside {@param contactIds}'s contacts.
     * No special attention is paid to where the resulting contact's name is taken from.
     */
    public static Intent createJoinSeveralContactsIntent(Context context, long[] contactIds) {
        return createJoinSeveralContactsIntent(context, contactIds, /* receiver = */ null);
    }

    private interface JoinContactQuery {
        String[] PROJECTION = {
                RawContacts._ID,
                RawContacts.CONTACT_ID,
                RawContacts.DISPLAY_NAME_SOURCE,
        };

        int _ID = 0;
        int CONTACT_ID = 1;
        int DISPLAY_NAME_SOURCE = 2;
    }

    private interface ContactEntityQuery {
        String[] PROJECTION = {
                Contacts.Entity.DATA_ID,
                Contacts.Entity.CONTACT_ID,
                Contacts.Entity.IS_SUPER_PRIMARY,
        };
        String SELECTION = Data.MIMETYPE + " = '" + StructuredName.CONTENT_ITEM_TYPE + "'" +
                " AND " + StructuredName.DISPLAY_NAME + "=" + Contacts.DISPLAY_NAME +
                " AND " + StructuredName.DISPLAY_NAME + " IS NOT NULL " +
                " AND " + StructuredName.DISPLAY_NAME + " != '' ";

        int DATA_ID = 0;
        int CONTACT_ID = 1;
        int IS_SUPER_PRIMARY = 2;
    }

    private void joinSeveralContacts(Intent intent) {
        final long[] contactIds = intent.getLongArrayExtra(EXTRA_CONTACT_IDS);

        final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER);

        // Load raw contact IDs for all contacts involved.
        final long rawContactIds[] = getRawContactIdsForAggregation(contactIds);
        final long[][] separatedRawContactIds = getSeparatedRawContactIds(contactIds);
        if (rawContactIds == null) {
            Log.e(TAG, "Invalid arguments for joinSeveralContacts request");
            if (receiver != null) {
                receiver.send(BAD_ARGUMENTS, new Bundle());
            }
            return;
        }

        // For each pair of raw contacts, insert an aggregation exception
        final ContentResolver resolver = getContentResolver();
        // The maximum number of operations per batch (aka yield point) is 500. See b/22480225
        final int batchSize = MAX_CONTACTS_PROVIDER_BATCH_SIZE;
        final ArrayList<ContentProviderOperation> operations = new ArrayList<>(batchSize);
        for (int i = 0; i < rawContactIds.length; i++) {
            for (int j = 0; j < rawContactIds.length; j++) {
                if (i != j) {
                    buildJoinContactDiff(operations, rawContactIds[i], rawContactIds[j]);
                }
                // Before we get to 500 we need to flush the operations list
                if (operations.size() > 0 && operations.size() % batchSize == 0) {
                    if (!applyOperations(resolver, operations)) {
                        if (receiver != null) {
                            receiver.send(CP2_ERROR, new Bundle());
                        }
                        return;
                    }
                    operations.clear();
                }
            }
        }
        if (operations.size() > 0 && !applyOperations(resolver, operations)) {
            if (receiver != null) {
                receiver.send(CP2_ERROR, new Bundle());
            }
            return;
        }


        final String name = queryNameOfLinkedContacts(contactIds);
        if (name != null) {
            if (receiver != null) {
                final Bundle result = new Bundle();
                result.putSerializable(EXTRA_RAW_CONTACT_IDS, separatedRawContactIds);
                result.putString(EXTRA_DISPLAY_NAME, name);
                receiver.send(CONTACTS_LINKED, result);
            } else {
                if (TextUtils.isEmpty(name)) {
                    showToast(R.string.contactsJoinedMessage);
                } else {
                    showToast(R.string.contactsJoinedNamedMessage, name);
                }
            }
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(BROADCAST_LINK_COMPLETE));
        } else {
            if (receiver != null) {
                receiver.send(CP2_ERROR, new Bundle());
            }
            showToast(R.string.contactJoinErrorToast);
        }
    }

    /** Get the display name of the top-level contact after the contacts have been linked. */
    private String queryNameOfLinkedContacts(long[] contactIds) {
        final StringBuilder whereBuilder = new StringBuilder(Contacts._ID).append(" IN (");
        final String[] whereArgs = new String[contactIds.length];
        for (int i = 0; i < contactIds.length; i++) {
            whereArgs[i] = String.valueOf(contactIds[i]);
            whereBuilder.append("?,");
        }
        whereBuilder.deleteCharAt(whereBuilder.length() - 1).append(')');
        final Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI,
                new String[]{Contacts._ID, Contacts.DISPLAY_NAME,
                        Contacts.DISPLAY_NAME_ALTERNATIVE},
                whereBuilder.toString(), whereArgs, null);

        String name = null;
        String nameAlt = null;
        long contactId = 0;
        try {
            if (cursor.moveToFirst()) {
                contactId = cursor.getLong(0);
                name = cursor.getString(1);
                nameAlt = cursor.getString(2);
            }
            while(cursor.moveToNext()) {
                if (cursor.getLong(0) != contactId) {
                    return null;
                }
            }

            final String formattedName = ContactDisplayUtils.getPreferredDisplayName(name, nameAlt,
                    new ContactsPreferences(getApplicationContext()));
            return formattedName == null ? "" : formattedName;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /** Returns true if the batch was successfully applied and false otherwise. */
    private boolean applyOperations(ContentResolver resolver,
            ArrayList<ContentProviderOperation> operations) {
        try {
            final ContentProviderResult[] result =
                    resolver.applyBatch(ContactsContract.AUTHORITY, operations);
            for (int i = 0; i < result.length; ++i) {
                // if no rows were modified in the operation then we count it as fail.
                if (result[i].count < 0) {
                    throw new OperationApplicationException();
                }
            }
            return true;
        } catch (RemoteException | OperationApplicationException e) {
            FeedbackHelper.sendFeedback(this, TAG,
                    "Failed to apply aggregation exception batch", e);
            showToast(R.string.contactSavedErrorToast);
            return false;
        }
    }

    private void joinContacts(Intent intent) {
        long contactId1 = intent.getLongExtra(EXTRA_CONTACT_ID1, -1);
        long contactId2 = intent.getLongExtra(EXTRA_CONTACT_ID2, -1);

        // Load raw contact IDs for all raw contacts involved - currently edited and selected
        // in the join UIs.
        long rawContactIds[] = getRawContactIdsForAggregation(contactId1, contactId2);
        if (rawContactIds == null) {
            Log.e(TAG, "Invalid arguments for joinContacts request");
            return;
        }

        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();

        // For each pair of raw contacts, insert an aggregation exception
        for (int i = 0; i < rawContactIds.length; i++) {
            for (int j = 0; j < rawContactIds.length; j++) {
                if (i != j) {
                    buildJoinContactDiff(operations, rawContactIds[i], rawContactIds[j]);
                }
            }
        }

        final ContentResolver resolver = getContentResolver();

        // Use the name for contactId1 as the name for the newly aggregated contact.
        final Uri contactId1Uri = ContentUris.withAppendedId(
                Contacts.CONTENT_URI, contactId1);
        final Uri entityUri = Uri.withAppendedPath(
                contactId1Uri, Contacts.Entity.CONTENT_DIRECTORY);
        Cursor c = resolver.query(entityUri,
                ContactEntityQuery.PROJECTION, ContactEntityQuery.SELECTION, null, null);
        if (c == null) {
            Log.e(TAG, "Unable to open Contacts DB cursor");
            showToast(R.string.contactSavedErrorToast);
            return;
        }
        long dataIdToAddSuperPrimary = -1;
        try {
            if (c.moveToFirst()) {
                dataIdToAddSuperPrimary = c.getLong(ContactEntityQuery.DATA_ID);
            }
        } finally {
            c.close();
        }

        // Mark the name from contactId1 IS_SUPER_PRIMARY to make sure that the contact
        // display name does not change as a result of the join.
        if (dataIdToAddSuperPrimary != -1) {
            Builder builder = ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(Data.CONTENT_URI, dataIdToAddSuperPrimary));
            builder.withValue(Data.IS_SUPER_PRIMARY, 1);
            builder.withValue(Data.IS_PRIMARY, 1);
            operations.add(builder.build());
        }

        // Apply all aggregation exceptions as one batch
        final boolean success = applyOperations(resolver, operations);

        final String name = queryNameOfLinkedContacts(new long[] {contactId1, contactId2});
        Intent callbackIntent = intent.getParcelableExtra(EXTRA_CALLBACK_INTENT);
        if (success && name != null) {
            if (TextUtils.isEmpty(name)) {
                showToast(R.string.contactsJoinedMessage);
            } else {
                showToast(R.string.contactsJoinedNamedMessage, name);
            }
            Uri uri = RawContacts.getContactLookupUri(resolver,
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactIds[0]));
            callbackIntent.setData(uri);
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(BROADCAST_LINK_COMPLETE));
        }
        deliverCallback(callbackIntent);
    }

    /**
     * Gets the raw contact ids for each contact id in {@param contactIds}. Each index of the outer
     * array of the return value holds an array of raw contact ids for one contactId.
     * @param contactIds
     * @return
     */
    private long[][] getSeparatedRawContactIds(long[] contactIds) {
        final long[][] rawContactIds = new long[contactIds.length][];
        for (int i = 0; i < contactIds.length; i++) {
            rawContactIds[i] = getRawContactIds(contactIds[i]);
        }
        return rawContactIds;
    }

    /**
     * Gets the raw contact ids associated with {@param contactId}.
     * @param contactId
     * @return Array of raw contact ids.
     */
    private long[] getRawContactIds(long contactId) {
        final ContentResolver resolver = getContentResolver();
        long rawContactIds[];

        final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append(RawContacts.CONTACT_ID)
                    .append("=")
                    .append(String.valueOf(contactId));

        final Cursor c = resolver.query(RawContacts.CONTENT_URI,
                JoinContactQuery.PROJECTION,
                queryBuilder.toString(),
                null, null);
        if (c == null) {
            Log.e(TAG, "Unable to open Contacts DB cursor");
            return null;
        }
        try {
            rawContactIds = new long[c.getCount()];
            for (int i = 0; i < rawContactIds.length; i++) {
                c.moveToPosition(i);
                final long rawContactId = c.getLong(JoinContactQuery._ID);
                rawContactIds[i] = rawContactId;
            }
        } finally {
            c.close();
        }
        return rawContactIds;
    }

    private long[] getRawContactIdsForAggregation(long[] contactIds) {
        if (contactIds == null) {
            return null;
        }

        final ContentResolver resolver = getContentResolver();

        final StringBuilder queryBuilder = new StringBuilder();
        final String stringContactIds[] = new String[contactIds.length];
        for (int i = 0; i < contactIds.length; i++) {
            queryBuilder.append(RawContacts.CONTACT_ID + "=?");
            stringContactIds[i] = String.valueOf(contactIds[i]);
            if (contactIds[i] == -1) {
                return null;
            }
            if (i == contactIds.length -1) {
                break;
            }
            queryBuilder.append(" OR ");
        }

        final Cursor c = resolver.query(RawContacts.CONTENT_URI,
                JoinContactQuery.PROJECTION,
                queryBuilder.toString(),
                stringContactIds, null);
        if (c == null) {
            Log.e(TAG, "Unable to open Contacts DB cursor");
            showToast(R.string.contactSavedErrorToast);
            return null;
        }
        long rawContactIds[];
        try {
            if (c.getCount() < 2) {
                Log.e(TAG, "Not enough raw contacts to aggregate together.");
                return null;
            }
            rawContactIds = new long[c.getCount()];
            for (int i = 0; i < rawContactIds.length; i++) {
                c.moveToPosition(i);
                long rawContactId = c.getLong(JoinContactQuery._ID);
                rawContactIds[i] = rawContactId;
            }
        } finally {
            c.close();
        }
        return rawContactIds;
    }

    private long[] getRawContactIdsForAggregation(long contactId1, long contactId2) {
        return getRawContactIdsForAggregation(new long[] {contactId1, contactId2});
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
     * Construct a {@link AggregationExceptions#TYPE_AUTOMATIC} or a
     * {@link AggregationExceptions#TYPE_KEEP_SEPARATE} ContentProviderOperation if a hard split is
     * requested.
     */
    private void buildSplitContactDiff(ArrayList<ContentProviderOperation> operations,
            long rawContactId1, long rawContactId2, boolean hardSplit) {
        final Builder builder =
                ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE,
                hardSplit
                        ? AggregationExceptions.TYPE_KEEP_SEPARATE
                        : AggregationExceptions.TYPE_AUTOMATIC);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        operations.add(builder.build());
    }

    /**
     * Returns an intent that can start this service and cause it to sleep for the specified time.
     *
     * This exists purely for debugging and manual testing. Since this service uses a single thread
     * it is useful to have a way to test behavior when work is queued up and most of the other
     * operations complete too quickly to simulate that under normal conditions.
     */
    public static Intent createSleepIntent(Context context, long millis) {
        return new Intent(context, ContactSaveService.class).setAction(ACTION_SLEEP)
                .putExtra(EXTRA_SLEEP_DURATION, millis);
    }

    private void sleepForDebugging(Intent intent) {
        long duration = intent.getLongExtra(EXTRA_SLEEP_DURATION, 1000);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "sleeping for " + duration + "ms");
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "finished sleeping");
        }
    }

    /**
     * Shows a toast on the UI thread by formatting messageId using args.
     * @param messageId id of message string
     * @param args args to format string
     */
    private void showToast(final int messageId, final Object... args) {
        final String message = getResources().getString(messageId, args);
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ContactSaveService.this, message, Toast.LENGTH_LONG).show();
            }
        });
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

    public interface GroupsDao {
        Uri create(String title, AccountWithDataSet account);
        int delete(Uri groupUri);
        Bundle captureDeletionUndoData(Uri groupUri);
        Uri undoDeletion(Bundle undoData);
    }

    public static class GroupsDaoImpl implements GroupsDao {
        public static final String KEY_GROUP_DATA = "groupData";
        public static final String KEY_GROUP_MEMBERS = "groupMemberIds";

        private static final String TAG = "GroupsDao";
        private final Context context;
        private final ContentResolver contentResolver;

        public GroupsDaoImpl(Context context) {
            this(context, context.getContentResolver());
        }

        public GroupsDaoImpl(Context context, ContentResolver contentResolver) {
            this.context = context;
            this.contentResolver = contentResolver;
        }

        public Bundle captureDeletionUndoData(Uri groupUri) {
            final long groupId = ContentUris.parseId(groupUri);
            final Bundle result = new Bundle();

            final Cursor cursor = contentResolver.query(groupUri,
                    new String[]{
                            Groups.TITLE, Groups.NOTES, Groups.GROUP_VISIBLE,
                            Groups.ACCOUNT_TYPE, Groups.ACCOUNT_NAME, Groups.DATA_SET,
                            Groups.SHOULD_SYNC
                    },
                    Groups.DELETED + "=?", new String[] { "0" }, null);
            try {
                if (cursor.moveToFirst()) {
                    final ContentValues groupValues = new ContentValues();
                    DatabaseUtils.cursorRowToContentValues(cursor, groupValues);
                    result.putParcelable(KEY_GROUP_DATA, groupValues);
                } else {
                    // Group doesn't exist.
                    return result;
                }
            } finally {
                cursor.close();
            }

            final Cursor membersCursor = contentResolver.query(
                    Data.CONTENT_URI, new String[] { Data.RAW_CONTACT_ID },
                    Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                    new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId) }, null);
            final long[] memberIds = new long[membersCursor.getCount()];
            int i = 0;
            while (membersCursor.moveToNext()) {
                memberIds[i++] = membersCursor.getLong(0);
            }
            result.putLongArray(KEY_GROUP_MEMBERS, memberIds);
            return result;
        }

        public Uri undoDeletion(Bundle deletedGroupData) {
            final ContentValues groupData = deletedGroupData.getParcelable(KEY_GROUP_DATA);
            if (groupData == null) {
                return null;
            }
            final Uri groupUri = contentResolver.insert(Groups.CONTENT_URI, groupData);
            final long groupId = ContentUris.parseId(groupUri);

            final long[] memberIds = deletedGroupData.getLongArray(KEY_GROUP_MEMBERS);
            if (memberIds == null) {
                return groupUri;
            }
            final ContentValues[] memberInsertions = new ContentValues[memberIds.length];
            for (int i = 0; i < memberIds.length; i++) {
                memberInsertions[i] = new ContentValues();
                memberInsertions[i].put(Data.RAW_CONTACT_ID, memberIds[i]);
                memberInsertions[i].put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
                memberInsertions[i].put(GroupMembership.GROUP_ROW_ID, groupId);
            }
            final int inserted = contentResolver.bulkInsert(Data.CONTENT_URI, memberInsertions);
            if (inserted != memberIds.length) {
                Log.e(TAG, "Could not recover some members for group deletion undo");
            }

            return groupUri;
        }

        public Uri create(String title, AccountWithDataSet account) {
            final ContentValues values = new ContentValues();
            values.put(Groups.TITLE, title);
            values.put(Groups.ACCOUNT_NAME, account.name);
            values.put(Groups.ACCOUNT_TYPE, account.type);
            values.put(Groups.DATA_SET, account.dataSet);
            return contentResolver.insert(Groups.CONTENT_URI, values);
        }

        public int delete(Uri groupUri) {
            return contentResolver.delete(groupUri, null, null);
        }
    }

    /**
     * Keeps track of which operations have been requested but have not yet finished for this
     * service.
     */
    public static class State {
        private final CopyOnWriteArrayList<Intent> mPending;

        public State() {
            mPending = new CopyOnWriteArrayList<>();
        }

        public State(Collection<Intent> pendingActions) {
            mPending = new CopyOnWriteArrayList<>(pendingActions);
        }

        public boolean isIdle() {
            return mPending.isEmpty();
        }

        public Intent getCurrentIntent() {
            return mPending.isEmpty() ? null : mPending.get(0);
        }

        /**
         * Returns the first intent requested that has the specified action or null if no intent
         * with that action has been requested.
         */
        public Intent getNextIntentWithAction(String action) {
            for (Intent intent : mPending) {
                if (action.equals(intent.getAction())) {
                    return intent;
                }
            }
            return null;
        }

        public boolean isActionPending(String action) {
            return getNextIntentWithAction(action) != null;
        }

        private void onFinish(Intent intent) {
            if (mPending.isEmpty()) {
                return;
            }
            final String action = mPending.get(0).getAction();
            if (action.equals(intent.getAction())) {
                mPending.remove(0);
            }
        }

        private void onStart(Intent intent) {
            if (intent.getAction() == null) {
                return;
            }
            mPending.add(intent);
        }
    }
}
