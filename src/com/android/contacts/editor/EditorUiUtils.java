/*
 * Copyright (C) 2012 The Android Open Source Project
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

import static android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;

import static com.android.contacts.util.MaterialColorMapUtils.getDefaultPrimaryAndSecondaryColors;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import androidx.core.content.res.ResourcesCompat;
import android.text.TextUtils;
import android.widget.ImageView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactPhotoManager.DefaultImageProvider;
import com.android.contacts.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountDisplayInfo;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.ContactPhotoUtils;
import com.android.contacts.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.widget.QuickContactImageView;

import com.google.common.collect.Maps;

import java.io.FileNotFoundException;
import java.util.HashMap;

/**
 * Utility methods for creating contact editor.
 */
public class EditorUiUtils {

    // Maps DataKind.mimeType to editor view layouts.
    private static final HashMap<String, Integer> mimetypeLayoutMap = Maps.newHashMap();
    static {
        // Generally there should be a layout mapped to each existing DataKind mimetype but lots of
        // them use the default text_fields_editor_view which we return as default so they don't
        // need to be mapped.
        //
        // Other possible mime mappings are:
        // DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME
        // Nickname.CONTENT_ITEM_TYPE
        // Email.CONTENT_ITEM_TYPE
        // StructuredPostal.CONTENT_ITEM_TYPE
        // Im.CONTENT_ITEM_TYPE
        // Note.CONTENT_ITEM_TYPE
        // Organization.CONTENT_ITEM_TYPE
        // Phone.CONTENT_ITEM_TYPE
        // SipAddress.CONTENT_ITEM_TYPE
        // Website.CONTENT_ITEM_TYPE
        // Relation.CONTENT_ITEM_TYPE
        //
        // Un-supported mime types need to mapped with -1.
        mimetypeLayoutMap.put(StructuredName.CONTENT_ITEM_TYPE,
                R.layout.structured_name_editor_view);
        mimetypeLayoutMap.put(GroupMembership.CONTENT_ITEM_TYPE, -1);
        mimetypeLayoutMap.put(Photo.CONTENT_ITEM_TYPE, -1);
        mimetypeLayoutMap.put(Event.CONTENT_ITEM_TYPE, R.layout.event_field_editor_view);
    }

    /**
     * Fetches a layout for a given mimetype.
     *
     * @param mimetype The mime type (e.g. StructuredName.CONTENT_ITEM_TYPE)
     * @return The layout resource id.
     */
    public static int getLayoutResourceId(String mimetype) {
        final Integer id = mimetypeLayoutMap.get(mimetype);
        if (id == null) {
            return R.layout.text_fields_editor_view;
        }
        return id;
    }


    public static String getAccountHeaderLabelForMyProfile(Context context,
            AccountInfo accountInfo) {
        if (accountInfo.isDeviceAccount()) {
            return context.getString(R.string.local_profile_title);
        } else {
            return context.getString(R.string.external_profile_title,
                    accountInfo.getTypeLabel());
        }
    }

    public static String getAccountTypeHeaderLabel(Context context, AccountDisplayInfo
            displayableAccount)  {
        if (displayableAccount.isDeviceAccount()) {
            // Do nothing. Type label should be "Device"
            return displayableAccount.getTypeLabel().toString();
        } else if (displayableAccount.isGoogleAccount()) {
            return context.getString(R.string.google_account_type_format,
                    displayableAccount.getTypeLabel());
        } else {
            return context.getString(R.string.account_type_format,
                    displayableAccount.getTypeLabel());
        }
    }

    /**
     * Returns a content description String for the container of the account information
     * returned by {@link #getAccountTypeHeaderLabel(Context, AccountDisplayInfo)}.
     */
    public static String getAccountInfoContentDescription(CharSequence accountName,
            CharSequence accountType) {
        final StringBuilder builder = new StringBuilder();
        if (!TextUtils.isEmpty(accountType)) {
            builder.append(accountType).append('\n');
        }
        if (!TextUtils.isEmpty(accountName)) {
            builder.append(accountName);
        }
        return builder.toString();
    }

    /**
     * Return an icon that represents {@param mimeType}.
     */
    public static Drawable getMimeTypeDrawable(Context context, String mimeType) {
        switch (mimeType) {
            case StructuredName.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_person_vd_theme_24, null);
            case StructuredPostal.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_place_vd_theme_24, null);
            case SipAddress.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_dialer_sip_vd_theme_24, null);
            case Phone.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_phone_vd_theme_24, null);
            case Im.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_message_vd_theme_24, null);
            case Event.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_event_vd_theme_24, null);
            case Email.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_email_vd_theme_24, null);
            case Website.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_public_vd_theme_24, null);
            case Photo.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_camera_alt_vd_theme_24, null);
            case GroupMembership.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_label_vd_theme_24, null);
            case Organization.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_business_vd_theme_24, null);
            case Note.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_insert_comment_vd_theme_24, null);
            case Relation.CONTENT_ITEM_TYPE:
                return ResourcesCompat.getDrawable(context.getResources(),
                        R.drawable.quantum_ic_circles_ext_vd_theme_24, null);
            default:
                return null;
        }
    }

    /**
     * Returns a ringtone string based on the ringtone URI and version #.
     */
    public static String getRingtoneStringFromUri(Uri pickedUri, int currentVersion) {
        if (isNewerThanM(currentVersion)) {
            if (pickedUri == null) return ""; // silent ringtone
            if (RingtoneManager.isDefault(pickedUri)) return null; // default ringtone
        }
        if (pickedUri == null || RingtoneManager.isDefault(pickedUri)) return null;
        return pickedUri.toString();
    }

    /**
     * Returns a ringtone URI, based on the string and version #.
     */
    public static Uri getRingtoneUriFromString(String str, int currentVersion) {
        if (str != null) {
            if (isNewerThanM(currentVersion) && TextUtils.isEmpty(str)) return null;
            return Uri.parse(str);
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    private static boolean isNewerThanM(int currentVersion) {
        return currentVersion > Build.VERSION_CODES.M;
    }

    /** Returns the {@link Photo#PHOTO_FILE_ID} from the given ValuesDelta. */
    public static Long getPhotoFileId(ValuesDelta valuesDelta) {
        if (valuesDelta == null) return null;
        if (valuesDelta.getAfter() == null || valuesDelta.getAfter().get(Photo.PHOTO) == null) {
            return valuesDelta.getAsLong(Photo.PHOTO_FILE_ID);
        }
        return null;
    }

    /** Binds the full resolution image at the given Uri to the provided ImageView. */
    static void loadPhoto(ContactPhotoManager contactPhotoManager, ImageView imageView,
            Uri photoUri) {
        final DefaultImageProvider fallbackToPreviousImage = new DefaultImageProvider() {
            @Override
            public void applyDefaultImage(ImageView view, int extent, boolean darkTheme,
                    DefaultImageRequest defaultImageRequest) {
                // Before we finish setting the full sized image, don't change the current
                // image that is set in any way.
            }
        };
        contactPhotoManager.loadPhoto(imageView, photoUri, imageView.getWidth(),
                /* darkTheme =*/ false, /* isCircular =*/ false,
                /* defaultImageRequest =*/ null, fallbackToPreviousImage);
    }

    /** Decodes the Bitmap from the photo bytes from the given ValuesDelta. */
    public static Bitmap getPhotoBitmap(ValuesDelta valuesDelta) {
        if (valuesDelta == null) return null;
        final byte[] bytes = valuesDelta.getAsByteArray(Photo.PHOTO);
        if (bytes == null) return null;
        return BitmapFactory.decodeByteArray(bytes, /* offset =*/ 0, bytes.length);
    }

    /** Binds the default avatar to the given ImageView and tints it to match QuickContacts. */
    public static void setDefaultPhoto(ImageView imageView , Resources resources,
            MaterialPalette materialPalette) {
        // Use the default avatar drawable
        imageView.setImageDrawable(ContactPhotoManager.getDefaultAvatarDrawableForContact(
                resources, /* hires =*/ false, /* defaultImageRequest =*/ null));

        // Tint it to match the quick contacts
        if (imageView instanceof QuickContactImageView) {
            ((QuickContactImageView) imageView).setTint(materialPalette == null
                    ? getDefaultPrimaryAndSecondaryColors(resources).mPrimaryColor
                    : materialPalette.mPrimaryColor);
        }
    }

    /**  Returns compressed bitmap bytes from the given Uri, scaled to the thumbnail dimensions. */
    public static byte[] getCompressedThumbnailBitmapBytes(Context context, Uri uri)
            throws FileNotFoundException {
        final Bitmap bitmap = ContactPhotoUtils.getBitmapFromUri(context, uri);
        final int size = ContactsUtils.getThumbnailSize(context);
        final Bitmap bitmapScaled = Bitmap.createScaledBitmap(
                bitmap, size, size, /* filter =*/ false);
        return ContactPhotoUtils.compressBitmap(bitmapScaled);
    }

}
