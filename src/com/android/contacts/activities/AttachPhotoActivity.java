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

import android.content.ContentResolver;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayPhoto;
import android.util.Log;

import com.android.contacts.ContactSaveService;
import com.android.contacts.ContactsActivity;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.ContactLoader;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.util.ContactPhotoUtils;

import java.io.File;
import java.io.FileNotFoundException;

import com.android.contacts.common.BrcmIccUtils;

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

    private static final String KEY_CONTACT_URI = "contact_uri";
    private static final String KEY_TEMP_PHOTO_URI = "temp_photo_uri";
    private static final String KEY_CROPPED_PHOTO_URI = "cropped_photo_uri";

    private Uri mTempPhotoUri;
    private Uri mCroppedPhotoUri;

    private ContentResolver mContentResolver;

    // Height and width (in pixels) to request for the photo - queried from the provider.
    private static int mPhotoDim;

    private Uri mContactUri;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

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

            intent.putExtra(BrcmIccUtils.PHONEBOOK_CONTACTS_ONLY, true);
            intent.putExtra(BrcmIccUtils.EXPORT_To_SIM, false);

            startActivityForResult(intent, REQUEST_PICK_CONTACT);
        }

        mContentResolver = getContentResolver();

        // Load the photo dimension to request.
        Cursor c = mContentResolver.query(DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                new String[]{DisplayPhoto.DISPLAY_MAX_DIM}, null, null, null);
        try {
            c.moveToFirst();
            mPhotoDim = c.getInt(0);
        } finally {
            c.close();
        }
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
        if (requestCode == REQUEST_PICK_CONTACT) {
            if (resultCode != RESULT_OK) {
                finish();
                return;
            }
            // A contact was picked. Launch the cropper to get face detection, the right size, etc.
            // TODO: get these values from constants somewhere
            final Intent myIntent = getIntent();
            final Uri inputUri = myIntent.getData();

            final int perm = checkUriPermission(inputUri, android.os.Process.myPid(),
                    android.os.Process.myUid(), Intent.FLAG_GRANT_READ_URI_PERMISSION |
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            final Uri toCrop;

            if (perm == PackageManager.PERMISSION_DENIED) {
                // Work around to save a read-only URI into a temporary file provider URI so that
                // we can add the FLAG_GRANT_WRITE_URI_PERMISSION flag to the eventual
                // crop intent b/10837468
                ContactPhotoUtils.savePhotoFromUriToUri(this, inputUri, mTempPhotoUri, false);
                toCrop = mTempPhotoUri;
            } else {
                toCrop = inputUri;
            }

            final Intent intent = new Intent("com.android.camera.action.CROP", toCrop);
            if (myIntent.getStringExtra("mimeType") != null) {
                intent.setDataAndType(toCrop, myIntent.getStringExtra("mimeType"));
            }
            ContactPhotoUtils.addPhotoPickerExtras(intent, mCroppedPhotoUri);
            ContactPhotoUtils.addCropExtras(intent, mPhotoDim);

            startActivityForResult(intent, REQUEST_CROP_PHOTO);

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

        // Obtain the raw-contact that we will save to.
        RawContactDeltaList deltaList = contact.createRawContactDeltaList();
        RawContactDelta raw = deltaList.getFirstWritableRawContact(this);
        if (raw == null) {
            Log.w(TAG, "no writable raw-contact found");
            return;
        }

        // Create a scaled, compressed bitmap to add to the entity-delta list.
        final int size = ContactsUtils.getThumbnailSize(this);
        Bitmap bitmap;
        try {
            bitmap = ContactPhotoUtils.getBitmapFromUri(this, mCroppedPhotoUri);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Could not find bitmap");
            return;
        }

        final Bitmap scaled = Bitmap.createScaledBitmap(bitmap, size, size, false);
        final byte[] compressed = ContactPhotoUtils.compressBitmap(scaled);
        if (compressed == null) {
            Log.w(TAG, "could not create scaled and compressed Bitmap");
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
            return;
        }
        values.setPhoto(compressed);

        // Finally, invoke the ContactSaveService.
        Log.v(TAG, "all prerequisites met, about to save photo to contact");
        Intent intent = ContactSaveService.createSaveContactIntent(
                this,
                deltaList,
                "", 0,
                contact.isUserProfile(),
                null, null,
                raw.getRawContactId(),
                mCroppedPhotoUri
                );
        startService(intent);
        finish();
    }
}
