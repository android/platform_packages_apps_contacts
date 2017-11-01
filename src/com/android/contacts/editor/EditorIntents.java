/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.contacts.editor;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;

import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.activities.ContactEditorSpringBoardActivity;
import com.android.contacts.model.RawContactDeltaList;
import com.android.contacts.util.MaterialColorMapUtils.MaterialPalette;

import java.util.ArrayList;

/**
 * Creates Intents to edit contacts.
 */
public class EditorIntents {

    private EditorIntents() {
    }

    /**
     * Returns an Intent to start the {@link ContactEditorSpringBoardActivity} for an
     * existing contact.
     */
    public static Intent createEditContactIntent(Context context, Uri uri,
            MaterialPalette materialPalette, long photoId) {
        final Intent intent = new Intent(Intent.ACTION_EDIT, uri, context,
                ContactEditorSpringBoardActivity.class);
        putMaterialPalette(intent, materialPalette);
        putPhotoId(intent, photoId);
        return intent;
    }

    public static Intent createViewLinkedContactsIntent(Context context, Uri uri,
            MaterialPalette materialPalette) {
        final Intent intent = createEditContactIntent(context, uri, materialPalette,
                /* photoId */ -1);
        intent.putExtra(ContactEditorSpringBoardActivity.EXTRA_SHOW_READ_ONLY, true);

        return intent;
    }

    /**
     * Returns an Intent to start the {@link ContactEditorActivity} for the given raw contact.
     */
    public static Intent createEditContactIntentForRawContact(Context context,
            Uri uri, long rawContactId, MaterialPalette materialPalette) {
        final Intent intent = new Intent(Intent.ACTION_EDIT, uri, context,
                ContactEditorActivity.class);
        intent.putExtra(ContactEditorFragment.INTENT_EXTRA_RAW_CONTACT_ID_TO_DISPLAY_ALONE,
                rawContactId);
        putMaterialPalette(intent, materialPalette);
        return intent;
    }

    /**
     * Returns an Intent to start the {@link ContactEditorActivity} for a new contact with
     * the field values specified by rawContactDeltaList pre-populate in the form.
     */
    public static Intent createInsertContactIntent(Context context,
            RawContactDeltaList rawContactDeltaList, String displayName, String phoneticName,
            /* Bundle updatedPhotos, */ boolean isNewLocalProfile) {
        final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI,
                context, ContactEditorActivity.class);
        intent.putExtra(
                ContactEditorFragment.INTENT_EXTRA_NEW_LOCAL_PROFILE, isNewLocalProfile);
        putRawContactDeltaValues(intent, rawContactDeltaList, displayName, phoneticName);
        return intent;
    }

    /**
     * Returns an Intent to edit a different raw contact in the editor with whatever
     * values were already entered on the current editor.
     */
    public static Intent createEditOtherRawContactIntent(Context context, Uri uri,
            long rawContactId, ArrayList<ContentValues> contentValues) {
        final Intent intent = new Intent(Intent.ACTION_EDIT, uri, context,
                ContactEditorActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                | Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.putExtra(ContactEditorFragment.INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY, "");
        intent.putExtra(ContactEditorFragment.INTENT_EXTRA_RAW_CONTACT_ID_TO_DISPLAY_ALONE,
                rawContactId);
        // Pass on all the data that has been entered so far
        if (contentValues != null && contentValues.size() != 0) {
            intent.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, contentValues);
        }
        return intent;
    }

    private static void putMaterialPalette(Intent intent, MaterialPalette materialPalette) {
        if (materialPalette != null) {
            intent.putExtra(
                    ContactEditorFragment.INTENT_EXTRA_MATERIAL_PALETTE_PRIMARY_COLOR,
                    materialPalette.mPrimaryColor);
            intent.putExtra(
                    ContactEditorFragment.INTENT_EXTRA_MATERIAL_PALETTE_SECONDARY_COLOR,
                    materialPalette.mSecondaryColor);
        }
    }

    private static void putPhotoId(Intent intent, long photoId) {
        if (photoId >= 0) {
            intent.putExtra(ContactEditorFragment.INTENT_EXTRA_PHOTO_ID, photoId);
        }
    }

    private static void putRawContactDeltaValues(Intent intent,
            RawContactDeltaList rawContactDeltaList, String displayName, String phoneticName) {
        // Pass on all the data that has been entered so far
        if (rawContactDeltaList != null && !rawContactDeltaList.isEmpty()) {
            ArrayList<ContentValues> contentValues = rawContactDeltaList.get(0).getContentValues();
            if (contentValues != null && contentValues.size() != 0) {
                intent.putParcelableArrayListExtra(
                        ContactsContract.Intents.Insert.DATA, contentValues);
            }
        }
        // Names must be passed separately since they are skipped in RawContactModifier.parseValues
        if (!TextUtils.isEmpty(displayName)) {
            intent.putExtra(ContactsContract.Intents.Insert.NAME, displayName);
        }
        if (!TextUtils.isEmpty(phoneticName)) {
            intent.putExtra(ContactsContract.Intents.Insert.PHONETIC_NAME, phoneticName);
        }
    }
}
