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

package com.android.contacts.detail;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;

import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.activities.PhotoSelectionActivity;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.util.ImageViewDrawableSetter;

import android.text.TextUtils;
import com.android.contacts.common.BrcmIccUtils;
/**
 * Extends superclass with methods specifically for setting the contact-detail
 * photo.
 */
public class ContactDetailPhotoSetter extends ImageViewDrawableSetter {
    public OnClickListener setupContactPhotoForClick(Context context, Contact contactData,
            ImageView photoView, boolean expandPhotoOnClick) {
        setTarget(photoView);
        Bitmap bitmap = setCompressedImage(contactData.getPhotoBinaryData());
        return setupClickListener(context, contactData, bitmap, expandPhotoOnClick);
    }

    private static final class PhotoClickListener implements OnClickListener {

        private final Context mContext;
        private final Contact mContactData;
        private final Bitmap mPhotoBitmap;
        private final byte[] mPhotoBytes;
        private final boolean mExpandPhotoOnClick;

        public PhotoClickListener(Context context, Contact contactData, Bitmap photoBitmap,
                byte[] photoBytes, boolean expandPhotoOnClick) {
            mContext = context;
            mContactData = contactData;
            mPhotoBitmap = photoBitmap;
            mPhotoBytes = photoBytes;
            mExpandPhotoOnClick = expandPhotoOnClick;
        }

        @Override
        public void onClick(View v) {
            // Assemble the intent.
            RawContactDeltaList delta = mContactData.createRawContactDeltaList();

            // Find location and bounds of target view, adjusting based on the
            // assumed local density.
            final float appScale =
                    mContext.getResources().getCompatibilityInfo().applicationScale;
            final int[] pos = new int[2];
            v.getLocationOnScreen(pos);

            // rect is the bounds (in pixels) of the photo view in screen coordinates
            final Rect rect = new Rect();
            rect.left = (int) (pos[0] * appScale + 0.5f);
            rect.top = (int) (pos[1] * appScale + 0.5f);
            rect.right = (int) ((pos[0] + v.getWidth()) * appScale + 0.5f);
            rect.bottom = (int) ((pos[1] + v.getHeight()) * appScale + 0.5f);

            Uri photoUri = null;
            if (mContactData.getPhotoUri() != null) {
                photoUri = Uri.parse(mContactData.getPhotoUri());
            }
            String accountType=BrcmIccUtils.getRawContactAccountTypeFromContactId(mContext.getContentResolver(), mContactData.getNameRawContactId());
            boolean isSimContact=false;
            if ((!TextUtils.isEmpty(accountType))&&(accountType.equals(BrcmIccUtils.ACCOUNT_TYPE_SIM))) {
                isSimContact = true;
            }

            Intent photoSelectionIntent = PhotoSelectionActivity.buildIntent(mContext,
                    photoUri, mPhotoBitmap, mPhotoBytes, rect, delta, mContactData.isUserProfile(),
                    mContactData.isDirectoryEntry(), mExpandPhotoOnClick,isSimContact);
            // Cache the bitmap directly, so the activity can pull it from the
            // photo manager.
            if (mPhotoBitmap != null) {
                ContactPhotoManager.getInstance(mContext).cacheBitmap(
                        photoUri, mPhotoBitmap, mPhotoBytes);
            }
            mContext.startActivity(photoSelectionIntent);
        }
    }

    private OnClickListener setupClickListener(Context context, Contact contactData, Bitmap bitmap,
            boolean expandPhotoOnClick) {
        final ImageView target = getTarget();
        if (target == null) return null;

        return new PhotoClickListener(
                context, contactData, bitmap, getCompressedImage(), expandPhotoOnClick);
    }
}
