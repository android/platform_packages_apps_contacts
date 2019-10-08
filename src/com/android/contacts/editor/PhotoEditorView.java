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

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contacts.util.SchedulingUtils;
import com.android.contacts.widget.QuickContactImageView;

/**
 * Displays a photo and calls the host back when the user clicks it.
 */
public class PhotoEditorView extends RelativeLayout implements View.OnClickListener {

    /**
     * Callbacks for the host of this view.
     */
    public interface Listener {

        /**
         * Invoked when the user wants to change their photo.
         */
        void onPhotoEditorViewClicked();
    }

    private Listener mListener;

    private final float mLandscapePhotoRatio;
    private final float mPortraitPhotoRatio;
    private final boolean mIsTwoPanel;

    private QuickContactImageView mPhotoImageView;
    private View mPhotoIcon;
    private View mPhotoIconOverlay;
    private View mPhotoTouchInterceptOverlay;
    private MaterialPalette mMaterialPalette;

    private boolean mReadOnly;
    private boolean mIsNonDefaultPhotoBound;

    public PhotoEditorView(Context context) {
        this(context, null);
    }

    public PhotoEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mLandscapePhotoRatio = getTypedFloat(R.dimen.quickcontact_landscape_photo_ratio);
        mPortraitPhotoRatio = getTypedFloat(R.dimen.editor_portrait_photo_ratio);
        mIsTwoPanel = getResources().getBoolean(R.bool.contacteditor_two_panel);
    }

    private float getTypedFloat(int resourceId) {
        final TypedValue typedValue = new TypedValue();
        getResources().getValue(resourceId, typedValue, /* resolveRefs =*/ true);
        return typedValue.getFloat();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mPhotoImageView = (QuickContactImageView) findViewById(R.id.photo);
        mPhotoIcon = findViewById(R.id.photo_icon);
        mPhotoIconOverlay = findViewById(R.id.photo_icon_overlay);
        mPhotoTouchInterceptOverlay = findViewById(R.id.photo_touch_intercept_overlay);

    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void setReadOnly(boolean readOnly) {
        mReadOnly = readOnly;
        if (mReadOnly) {
            mPhotoIcon.setVisibility(View.GONE);
            mPhotoIconOverlay.setVisibility(View.GONE);
            mPhotoTouchInterceptOverlay.setClickable(false);
            mPhotoTouchInterceptOverlay.setContentDescription(getContext().getString(
                    R.string.editor_contact_photo_content_description));
        } else {
            mPhotoIcon.setVisibility(View.VISIBLE);
            mPhotoIconOverlay.setVisibility(View.VISIBLE);
            mPhotoTouchInterceptOverlay.setOnClickListener(this);
            updatePhotoDescription();
        }
    }

    public void setPalette(MaterialPalette palette) {
        mMaterialPalette = palette;
    }

    /**
     * Tries to bind a full size photo or a bitmap loaded from the given ValuesDelta,
     * and falls back to the default avatar, tinted using the given MaterialPalette (if it's not
     * null);
     */
    public void setPhoto(ValuesDelta valuesDelta) {
        // Check if we can update to the full size photo immediately
        final Long photoFileId = EditorUiUtils.getPhotoFileId(valuesDelta);
        if (photoFileId != null) {
            final Uri photoUri = ContactsContract.DisplayPhoto.CONTENT_URI.buildUpon()
                    .appendPath(photoFileId.toString()).build();
            setFullSizedPhoto(photoUri);
            adjustDimensions();
            return;
        }

        // Use the bitmap image from the values delta
        final Bitmap bitmap = EditorUiUtils.getPhotoBitmap(valuesDelta);
        if (bitmap != null) {
            setPhoto(bitmap);
            adjustDimensions();
            return;
        }

        setDefaultPhoto(mMaterialPalette);
        adjustDimensions();
    }

    private void adjustDimensions() {
        // Follow the same logic as MultiShrinkScroll.initialize
        SchedulingUtils.doOnPreDraw(this, /* drawNextFrame =*/ false, new Runnable() {
            @Override
            public void run() {
                final int photoHeight, photoWidth;
                if (mIsTwoPanel) {
                    photoHeight = getHeight();
                    photoWidth = (int) (photoHeight * mLandscapePhotoRatio);
                } else {
                    // Make the photo slightly shorter that it is wide
                    photoWidth = getWidth();
                    photoHeight = (int) (photoWidth / mPortraitPhotoRatio);
                }
                final ViewGroup.LayoutParams layoutParams = getLayoutParams();
                layoutParams.height = photoHeight;
                layoutParams.width = photoWidth;
                setLayoutParams(layoutParams);
            }
        });
    }

    /**
     * Whether a removable, non-default photo is bound to this view.
     */
    public boolean isWritablePhotoSet() {
        return !mReadOnly && mIsNonDefaultPhotoBound;
    }

    /**
     * Binds the given bitmap.
     */
    private void setPhoto(Bitmap bitmap) {
        mPhotoImageView.setImageBitmap(bitmap);
        mIsNonDefaultPhotoBound = true;
        updatePhotoDescription();
    }

    private void setDefaultPhoto(MaterialPalette materialPalette) {
        mIsNonDefaultPhotoBound = false;
        updatePhotoDescription();
        EditorUiUtils.setDefaultPhoto(mPhotoImageView, getResources(), materialPalette);
    }

    private void updatePhotoDescription() {
        mPhotoTouchInterceptOverlay.setContentDescription(getContext().getString(
                mIsNonDefaultPhotoBound
                        ? R.string.editor_change_photo_content_description
                        : R.string.editor_add_photo_content_description));
    }
    /**
     * Binds a full size photo loaded from the given Uri.
     */
    public void setFullSizedPhoto(Uri photoUri) {
        mPhotoImageView.setImageURI(photoUri);
        mIsNonDefaultPhotoBound = true;
        updatePhotoDescription();
    }

    /**
     * Removes the current bound photo bitmap.
     */
    public void removePhoto() {
        setDefaultPhoto(mMaterialPalette);
    }

    @Override
    public void onClick(View view) {
        if (mListener != null) {
            mListener.onPhotoEditorViewClicked();
        }
    }
}
