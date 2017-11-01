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

package com.android.contacts.activities;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayPhoto;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.editor.ContactEditorUtils;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.Contact;
import com.android.contacts.model.ContactLoader;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.RawContactDeltaList;
import com.android.contacts.model.RawContactModifier;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.ContactPhotoUtils;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * Provides an external interface for other applications to attach images
 * to contacts. It will first present a contact picker and then run the
 * image that is handed to it through the cropper to make the image the proper
 * size and give the user a chance to use the face detector.
 */
public class AttachPhotoActivity extends ContactsActivity {
    private static final String TAG = AttachPhotoActivity.class.getSimpleName();

    private static final int REQUEST_PICK_CONTACT = 1;
    private static final int REQUEST_CROP_PHOTO = 2;
    private static final int REQUEST_PICK_DEFAULT_ACCOUNT_FOR_NEW_CONTACT = 3;

    private static final String KEY_CONTACT_URI = "contact_uri";
    private static final String KEY_TEMP_PHOTO_URI = "temp_photo_uri";
    private static final String KEY_CROPPED_PHOTO_URI = "cropped_photo_uri";

    private Uri mTempPhotoUri;
    private Uri mCroppedPhotoUri;

    private ContentResolver mContentResolver;

    private ListenableFuture<List<AccountInfo>> mAccountsFuture;

    // Height and width (in pixels) to request for the photo - queried from the provider.
    private static int mPhotoDim;
    // Default photo dimension to use if unable to query the provider.
    private static final int mDefaultPhotoDim = 720;

    private Uri mContactUri;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (RequestPermissionsActivity.startPermissionActivityIfNeeded(this)) {
            return;
        }

        if (icicle != null) {
            final String uri = icicle.getString(KEY_CONTACT_URI);
            mContactUri = (uri == null) ? null : Uri.parse(uri);
            mTempPhotoUri = Uri.parse(icicle.getString(KEY_TEMP_PHOTO_URI));
            mCroppedPhotoUri = Uri.parse(icicle.getString(KEY_CROPPED_PHOTO_URI));
        } else {
            mTempPhotoUri = ContactPhotoUtils.generateTempImageUri(this);
            mCroppedPhotoUri = ContactPhotoUtils.generateTempCroppedImageUri(this);
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(Contacts.CONTENT_TYPE);
            intent.setPackage(getPackageName());
            startActivityForResult(intent, REQUEST_PICK_CONTACT);
        }

        mContentResolver = getContentResolver();

        // Load the photo dimension to request. mPhotoDim is a static class
        // member varible so only need to load this if this is the first time
        // through.
        if (mPhotoDim == 0) {
            Cursor c = mContentResolver.query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                    new String[]{DisplayPhoto.DISPLAY_MAX_DIM}, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        mPhotoDim = c.getInt(0);
                    }
                } finally {
                    c.close();
                }
            }
        }

        // Start loading accounts in case they are needed.
        mAccountsFuture = AccountTypeManager.getInstance(this).filterAccountsAsync(
                AccountTypeManager.writableFilter());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mContactUri != null) {
            outState.putString(KEY_CONTACT_URI, mContactUri.toString());
        }
        if (mTempPhotoUri != null) {
            outState.putString(KEY_TEMP_PHOTO_URI, mTempPhotoUri.toString());
        }
        if (mCroppedPhotoUri != null) {
            outState.putString(KEY_CROPPED_PHOTO_URI, mCroppedPhotoUri.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (requestCode == REQUEST_PICK_DEFAULT_ACCOUNT_FOR_NEW_CONTACT) {
            // Bail if the account selector was not successful.
            if (resultCode != Activity.RESULT_OK) {
                Log.w(TAG, "account selector was not successful");
                finish();
                return;
            }
            // If there's an account specified, use it.
            if (result != null) {
                AccountWithDataSet account = result.getParcelableExtra(
                        Intents.Insert.EXTRA_ACCOUNT);
                if (account != null) {
                    createNewRawContact(account);
                    return;
                }
            }
            // If there isn't an account specified, then the user opted to keep the contact local.
            createNewRawContact(null);
        } else if (requestCode == REQUEST_PICK_CONTACT) {
            if (resultCode != RESULT_OK) {
                finish();
                return;
            }
            // A contact was picked. Launch the cropper to get face detection, the right size, etc.
            // TODO: get these values from constants somewhere
            final Intent myIntent = getIntent();
            final Uri inputUri = myIntent.getData();


            // Save the URI into a temporary file provider URI so that
            // we can add the FLAG_GRANT_WRITE_URI_PERMISSION flag to the eventual
            // crop intent for read-only URI's.
            // TODO: With b/10837468 fixed should be able to avoid this copy.
            if (!ContactPhotoUtils.savePhotoFromUriToUri(this, inputUri, mTempPhotoUri, false)) {
                finish();
                return;
            }

            final Intent intent = new Intent("com.android.camera.action.CROP", mTempPhotoUri);
            if (myIntent.getStringExtra("mimeType") != null) {
                intent.setDataAndType(mTempPhotoUri, myIntent.getStringExtra("mimeType"));
            }
            ContactPhotoUtils.addPhotoPickerExtras(intent, mCroppedPhotoUri);
            ContactPhotoUtils.addCropExtras(intent, mPhotoDim != 0 ? mPhotoDim : mDefaultPhotoDim);
            if (!hasIntentHandler(intent)) {
                // No activity supports the crop action. So skip cropping and set the photo
                // without performing any cropping.
                mCroppedPhotoUri = mTempPhotoUri;
                mContactUri = result.getData();
                loadContact(mContactUri, new Listener() {
                    @Override
                    public void onContactLoaded(Contact contact) {
                        saveContact(contact);
                    }
                });
                return;
            }

            try {
                startActivityForResult(intent, REQUEST_CROP_PHOTO);
            } catch (ActivityNotFoundException ex) {
                Toast.makeText(this, R.string.missing_app, Toast.LENGTH_SHORT).show();
                return;
            }

            mContactUri = result.getData();

        } else if (requestCode == REQUEST_CROP_PHOTO) {
            // Delete the temporary photo from cache now that we have a cropped version.
            // We should do this even if the crop failed and we eventually bail
            getContentResolver().delete(mTempPhotoUri, null, null);
            if (resultCode != RESULT_OK) {
                finish();
                return;
            }
            loadContact(mContactUri, new Listener() {
                @Override
                public void onContactLoaded(Contact contact) {
                    saveContact(contact);
                }
            });
        }
    }

    private boolean hasIntentHandler(Intent intent) {
        final List<ResolveInfo> resolveInfo = getPackageManager()
                .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null && resolveInfo.size() > 0;
    }

    // TODO: consider moving this to ContactLoader, especially if we keep adding similar
    // code elsewhere (ViewNotificationService is another case).  The only concern is that,
    // although this is convenient, it isn't quite as robust as using LoaderManager... for
    // instance, the loader doesn't persist across Activity restarts.
    private void loadContact(Uri contactUri, final Listener listener) {
        final ContactLoader loader = new ContactLoader(this, contactUri, true);
        loader.registerListener(0, new OnLoadCompleteListener<Contact>() {
            @Override
            public void onLoadComplete(
                    Loader<Contact> loader, Contact contact) {
                try {
                    loader.reset();
                }
                catch (RuntimeException e) {
                    Log.e(TAG, "Error resetting loader", e);
                }
                listener.onContactLoaded(contact);
            }
        });
        loader.startLoading();
    }

    private interface Listener {
        public void onContactLoaded(Contact contact);
    }

    /**
     * If prerequisites have been met, attach the photo to a raw-contact and save.
     * The prerequisites are:
     * - photo has been cropped
     * - contact has been loaded
     */
    private void saveContact(Contact contact) {

        if (contact.getRawContacts() == null) {
            Log.w(TAG, "No raw contacts found for contact");
            finish();
            return;
        }

        // Obtain the raw-contact that we will save to.
        RawContactDeltaList deltaList = contact.createRawContactDeltaList();
        RawContactDelta raw = deltaList.getFirstWritableRawContact(this);
        if (raw == null) {
            // We can't directly insert this photo since no raw contacts exist in the contact.
            selectAccountAndCreateContact();
            return;
        }

        saveToContact(contact, deltaList, raw);
    }

    private void saveToContact(Contact contact, RawContactDeltaList deltaList,
            RawContactDelta raw) {

        // Create a scaled, compressed bitmap to add to the entity-delta list.
        final int size = ContactsUtils.getThumbnailSize(this);
        Bitmap bitmap;
        try {
            bitmap = ContactPhotoUtils.getBitmapFromUri(this, mCroppedPhotoUri);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Could not find bitmap");
            finish();
            return;
        }
        if (bitmap == null) {
            Log.w(TAG, "Could not decode bitmap");
            finish();
            return;
        }

        final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size, size, false);
        final byte[] compressed = ContactPhotoUtils.compressBitmap(scaled);
        if (compressed == null) {
            Log.w(TAG, "could not create scaled and compressed Bitmap");
            finish();
            return;
        }

        // Add compressed bitmap to entity-delta... this allows us to save to
        // a new contact; otherwise the entity-delta-list would be empty, and
        // the ContactSaveService would not create the new contact, and the
        // full-res photo would fail to be saved to the non-existent contact.
        AccountType account = raw.getRawContactAccountType(this);
        ValuesDelta values =
                RawContactModifier.ensureKindExists(raw, account, Photo.CONTENT_ITEM_TYPE);
        if (values == null) {
            Log.w(TAG, "cannot attach photo to this account type");
            finish();
            return;
        }
        values.setPhoto(compressed);

        // Finally, invoke the ContactSaveService.
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "all prerequisites met, about to save photo to contact");
        }
        Intent intent = ContactSaveService.createSaveContactIntent(
                this,
                deltaList,
                "", 0,
                contact.isUserProfile(),
                null, null,
                raw.getRawContactId() != null ? raw.getRawContactId() : -1,
                mCroppedPhotoUri
        );
        ContactSaveService.startService(this, intent);
        finish();
    }

    private void selectAccountAndCreateContact() {
        Preconditions.checkNotNull(mAccountsFuture, "Accounts future must be initialized first");
        // If there is no default account or the accounts have changed such that we need to
        // prompt the user again, then launch the account prompt.
        final ContactEditorUtils editorUtils = ContactEditorUtils.create(this);

        // Technically this could block but in reality this method won't be called until the user
        // presses the save button which should allow plenty of time for the accounts to
        // finish loading. Note also that this could be stale if the accounts have changed since
        // we requested them but that's OK since ContactEditorAccountsChangedActivity will reload
        // the accounts
        final List<AccountInfo> accountInfos = Futures.getUnchecked(mAccountsFuture);

        final List<AccountWithDataSet> accounts = AccountInfo.extractAccounts(accountInfos);
        if (editorUtils.shouldShowAccountChangedNotification(accounts)) {
            Intent intent = new Intent(this, ContactEditorAccountsChangedActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivityForResult(intent, REQUEST_PICK_DEFAULT_ACCOUNT_FOR_NEW_CONTACT);
        } else {
            // Otherwise, there should be a default account. Then either create a null contact
            // (if default account is null) or create a contact with the specified account.
            final AccountWithDataSet targetAccount = editorUtils.getOnlyOrDefaultAccount(accounts);
            createNewRawContact(targetAccount);
        }
    }

    /**
     * Create a new writeable raw contact to store mCroppedPhotoUri.
     */
    private void createNewRawContact(final AccountWithDataSet account) {
        // Reload the contact from URI instead of trying to pull the contact from a member variable,
        // since this function can be called after the activity stops and resumes.
        loadContact(mContactUri, new Listener() {
            @Override
            public void onContactLoaded(Contact contactToSave) {
                final RawContactDeltaList deltaList = contactToSave.createRawContactDeltaList();
                final ContentValues after = new ContentValues();
                after.put(RawContacts.ACCOUNT_TYPE, account != null ? account.type : null);
                after.put(RawContacts.ACCOUNT_NAME, account != null ? account.name : null);
                after.put(RawContacts.DATA_SET, account != null ? account.dataSet : null);

                final RawContactDelta newRawContactDelta
                        = new RawContactDelta(ValuesDelta.fromAfter(after));
                deltaList.add(newRawContactDelta);
                saveToContact(contactToSave, deltaList, newRawContactDelta);
            }
        });
    }
}
