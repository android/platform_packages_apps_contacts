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

package com.android.contacts.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.media.ThumbnailUtils;
import android.text.TextUtils;
import android.widget.ImageView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.lettertiles.LetterTileDrawable;
import com.android.contacts.model.Contact;

import java.util.Arrays;

/**
 * Initialized with a target ImageView. When provided with a compressed image
 * (i.e. a byte[]), it appropriately updates the ImageView's Drawable.
 */
public class ImageViewDrawableSetter {
    private ImageView mTarget;
    private byte[] mCompressed;
    private Drawable mPreviousDrawable;
    private int mDurationInMillis = 0;
    private Contact mContact;
    private static final String TAG = "ImageViewDrawableSetter";

    public ImageViewDrawableSetter() {
    }

    public ImageViewDrawableSetter(ImageView target) {
        mTarget = target;
    }

    public Bitmap setupContactPhoto(Contact contactData, ImageView photoView) {
        mContact = contactData;
        setTarget(photoView);
        return setCompressedImage(contactData.getPhotoBinaryData());
    }

    public void setTransitionDuration(int durationInMillis) {
        mDurationInMillis = durationInMillis;
    }

    public ImageView getTarget() {
        return mTarget;
    }

    /**
     * Re-initialize to use new target. As a result, the next time a new image
     * is set, it will immediately be applied to the target (there will be no
     * fade transition).
     */
    protected void setTarget(ImageView target) {
        if (mTarget != target) {
            mTarget = target;
            mCompressed = null;
            mPreviousDrawable = null;
        }
    }

    protected byte[] getCompressedImage() {
        return mCompressed;
    }

    protected Bitmap setCompressedImage(byte[] compressed) {
        if (mPreviousDrawable == null) {
            // If we don't already have a drawable, skip the exit-early test
            // below; otherwise we might not end up setting the default image.
        } else if (mPreviousDrawable != null
                && mPreviousDrawable instanceof BitmapDrawable
                && Arrays.equals(mCompressed, compressed)) {
            // TODO: the worst case is when the arrays are equal but not
            // identical. This takes about 1ms (more with high-res photos). A
            // possible optimization is to sparsely sample chunks of the arrays
            // to compare.
            return previousBitmap();
        }

        Drawable newDrawable = decodedBitmapDrawable(compressed);
        if (newDrawable == null) {
            newDrawable = defaultDrawable();
        }

        // Remember this for next time, so that we can check if it changed.
        mCompressed = compressed;

        // If we don't have a new Drawable, something went wrong... bail out.
        if (newDrawable == null) return previousBitmap();

        if (mPreviousDrawable == null || mDurationInMillis == 0) {
            // Set the new one immediately.
            mTarget.setImageDrawable(newDrawable);
        } else {
            // Set up a transition from the previous Drawable to the new one.
            final Drawable[] beforeAndAfter = new Drawable[2];
            beforeAndAfter[0] = mPreviousDrawable;
            beforeAndAfter[1] = newDrawable;
            final TransitionDrawable transition = new TransitionDrawable(beforeAndAfter);
            mTarget.setImageDrawable(transition);
            transition.startTransition(mDurationInMillis);
        }

        // Remember this for next time, so that we can transition from it to the
        // new one.
        mPreviousDrawable = newDrawable;

        return previousBitmap();
    }

    private Bitmap previousBitmap() {
        return (mPreviousDrawable == null) ? null
                : mPreviousDrawable instanceof LetterTileDrawable ? null
                : ((BitmapDrawable) mPreviousDrawable).getBitmap();
    }

    /**
     * Obtain the default drawable for a contact when no photo is available. If this is a local
     * contact, then use the contact's display name and lookup key (as a unique identifier) to
     * retrieve a default drawable for this contact. If not, then use the name as the contact
     * identifier instead.
     */
    private Drawable defaultDrawable() {
        Resources resources = mTarget.getResources();
        DefaultImageRequest request;
        int contactType = ContactPhotoManager.TYPE_DEFAULT;

        if (mContact.isDisplayNameFromOrganization()) {
            contactType = ContactPhotoManager.TYPE_BUSINESS;
        }

        if (TextUtils.isEmpty(mContact.getLookupKey())) {
            request = new DefaultImageRequest(null, mContact.getDisplayName(), contactType,
                    false /* isCircular */);
        } else {
            request = new DefaultImageRequest(mContact.getDisplayName(), mContact.getLookupKey(),
                    contactType, false /* isCircular */);
        }
        return ContactPhotoManager.getDefaultAvatarDrawableForContact(resources, true, request);
    }

    private BitmapDrawable decodedBitmapDrawable(byte[] compressed) {
        if (compressed == null) {
            return null;
        }
        final Resources rsrc = mTarget.getResources();
        Bitmap bitmap = BitmapFactory.decodeByteArray(compressed, 0, compressed.length);
        if (bitmap == null) {
            return null;
        }
        if (bitmap.getHeight() != bitmap.getWidth()) {
            // Crop the bitmap into a square.
            final int size = Math.min(bitmap.getWidth(), bitmap.getHeight());
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, size, size);
        }
        return new BitmapDrawable(rsrc, bitmap);
    }
}
