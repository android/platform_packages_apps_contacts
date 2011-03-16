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

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.widget.Toast;

import com.android.contacts.model.ExchangeSource;
import com.android.contacts.model.GoogleSource;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

/**
 * Provides an external interface for other applications to attach images
 * to contacts. It will first present a contact picker and then run the
 * image that is handed to it through the cropper to make the image the proper
 * size and give the user a chance to use the face detector.
 */
public class AttachImage extends Activity {
    private static final int REQUEST_PICK_CONTACT = 1;
    private static final int REQUEST_CROP_PHOTO = 2;

    private static final String RAW_CONTACT_URIS_KEY = "raw_contact_uris";

    public AttachImage() {

    }

    private Long[] mRawContactIds;

    private ContentResolver mContentResolver;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mRawContactIds = toClassArray(icicle.getLongArray(RAW_CONTACT_URIS_KEY));
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            startActivityForResult(intent, REQUEST_PICK_CONTACT);
        }

        mContentResolver = getContentResolver();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mRawContactIds != null && mRawContactIds.length != 0) {
            outState.putLongArray(RAW_CONTACT_URIS_KEY, toPrimativeArray(mRawContactIds));
        }
    }

    private static long[] toPrimativeArray(Long[] in) {
        if (in == null) {
            return null;
        }
        long[] out = new long[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    private static Long[] toClassArray(long[] in) {
        if (in == null) {
            return null;
        }
        Long[] out = new Long[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i];
        }
        return out;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (resultCode != RESULT_OK) {
            finish();
            return;
        }

        if (requestCode == REQUEST_PICK_CONTACT) {
            // A contact was picked. Launch the cropper to get face detection, the right size, etc.
            // TODO: get these values from constants somewhere
            Intent myIntent = getIntent();
            Intent intent = new Intent("com.android.camera.action.CROP", myIntent.getData());
            if (myIntent.getStringExtra("mimeType") != null) {
                intent.setDataAndType(myIntent.getData(), myIntent.getStringExtra("mimeType"));
            }
            intent.putExtra("crop", "true");
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("outputX", 96);
            intent.putExtra("outputY", 96);
            intent.putExtra("return-data", true);
            startActivityForResult(intent, REQUEST_CROP_PHOTO);

            // while they're cropping, convert the contact into a raw_contact
            final long contactId = ContentUris.parseId(result.getData());
            final ArrayList<Long> rawContactIdsList = ContactsUtils.queryForAllRawContactIds(
                    mContentResolver, contactId);
            mRawContactIds = new Long[rawContactIdsList.size()];
            mRawContactIds = rawContactIdsList.toArray(mRawContactIds);

            if (mRawContactIds == null || rawContactIdsList.isEmpty()) {
                Toast.makeText(this, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CROP_PHOTO) {
            final Bundle extras = result.getExtras();
            if (extras != null && mRawContactIds != null) {
                Bitmap photo = extras.getParcelable("data");
                if (photo != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    photo.compress(Bitmap.CompressFormat.JPEG, 75, stream);

                    final ContentValues imageValues = new ContentValues();
                    imageValues.put(Photo.PHOTO, stream.toByteArray());
                    imageValues.put(RawContacts.Data.IS_SUPER_PRIMARY, 1);

                    // attach the photo to every raw contact
                    for (Long rawContactId : mRawContactIds) {

                        final Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                                rawContactId);
                        final Uri rawContactDataUri = Uri.withAppendedPath(rawContactUri,
                                RawContacts.Data.CONTENT_DIRECTORY);
                        insertPhoto(imageValues, rawContactDataUri, true);
                    }
                }
            }
            finish();
        }
    }

    /**
     * Inserts a photo on the raw contact.
     * @param values the photo values
     * @param assertAccount if true, will check to verify that no photos exist for Google,
     *     Exchange and unsynced phone account types. These account types only take one picture,
     *     so if one exists, the account will be updated with the new photo.
     */
    private void insertPhoto(ContentValues values, Uri rawContactDataUri,
            boolean assertAccount) {

        ArrayList<ContentProviderOperation> operations =
            new ArrayList<ContentProviderOperation>();

        if (assertAccount) {
            // Make sure no pictures exist for Google, Exchange and unsynced phone accounts.
            operations.add(ContentProviderOperation.newAssertQuery(rawContactDataUri)
                    .withSelection(Photo.MIMETYPE + "=? AND ("
                            + RawContacts.ACCOUNT_TYPE + " IN (?,?) OR "
                            + RawContacts.ACCOUNT_TYPE + " IS NULL)",
                            new String[] {Photo.CONTENT_ITEM_TYPE, GoogleSource.ACCOUNT_TYPE,
                            ExchangeSource.ACCOUNT_TYPE})
                            .withExpectedCount(0).build());
        }

        // insert the photo
        values.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
        operations.add(ContentProviderOperation.newInsert(rawContactDataUri)
                .withValues(values).build());

        try {
            mContentResolver.applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (RemoteException e) {
            throw new IllegalStateException("Problem querying raw_contacts/data", e);
        } catch (OperationApplicationException e) {
            // the account doesn't allow multiple photos, so update
            if (assertAccount) {
                updatePhoto(values, rawContactDataUri, false);
            } else {
                throw new IllegalStateException("Problem inserting photo into raw_contacts/data", e);
            }
        }
    }

    /**
     * Tries to update the photo on the raw_contact.  If no photo exists, and allowInsert == true,
     * then will try to {@link #updatePhoto(ContentValues, boolean)}
     */
    private void updatePhoto(ContentValues values, Uri rawContactDataUri,
            boolean allowInsert) {
        ArrayList<ContentProviderOperation> operations =
            new ArrayList<ContentProviderOperation>();

        values.remove(Photo.MIMETYPE);

        // check that a photo exists
        operations.add(ContentProviderOperation.newAssertQuery(rawContactDataUri)
                .withSelection(Photo.MIMETYPE + "=?", new String[] {
                    Photo.CONTENT_ITEM_TYPE
                }).withExpectedCount(1).build());

        // update that photo
        operations.add(ContentProviderOperation.newUpdate(rawContactDataUri).withSelection(Photo.MIMETYPE + "=?", new String[] {
                    Photo.CONTENT_ITEM_TYPE}).withValues(values).build());

        try {
            mContentResolver.applyBatch(ContactsContract.AUTHORITY, operations);
        } catch (RemoteException e) {
            throw new IllegalStateException("Problem querying raw_contacts/data", e);
        } catch (OperationApplicationException e) {
            if (allowInsert) {
                // they deleted the photo between insert and update, so insert one
                insertPhoto(values, rawContactDataUri, false);
            } else {
                throw new IllegalStateException("Problem inserting photo raw_contacts/data", e);
            }
        }
    }
}
