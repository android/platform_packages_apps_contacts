/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.database.Cursor;
import android.graphics.Bitmap;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.android.contacts.R;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountType.EditType;

/**
 * Base view that provides common code for the editor interaction for a specific
 * RawContact represented through an {@link RawContactDelta}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link RawContact} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link RawContactModifier} to ensure that {@link AccountType} are enforced.
 */
public abstract class BaseRawContactEditorView extends LinearLayout {

    private PhotoEditorView mPhoto;
    private boolean mHasPhotoEditor = false;

    private View mBody;
    private View mDivider;

    private boolean mExpanded = true;

    public BaseRawContactEditorView(Context context) {
        super(context);
    }

    public BaseRawContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBody = findViewById(R.id.body);
        mDivider = findViewById(R.id.divider);

        mPhoto = (PhotoEditorView)findViewById(R.id.edit_photo);
        mPhoto.setEnabled(isEnabled());
    }

    public void setGroupMetaData(Cursor groupMetaData) {
    }

    /**
     * Assign the given {@link Bitmap} to the internal {@link PhotoEditorView}
     * for the {@link RawContactDelta} currently being edited.
     */
    public void setPhotoBitmap(Bitmap bitmap) {
        mPhoto.setPhotoBitmap(bitmap);
    }

    protected void setHasPhotoEditor(boolean hasPhotoEditor) {
        mHasPhotoEditor = hasPhotoEditor;
        mPhoto.setVisibility(hasPhotoEditor ? View.VISIBLE : View.GONE);
    }

    /**
     * Return true if the current {@link RawContacts} supports {@link Photo},
     * which means that {@link PhotoEditorView} is enabled.
     */
    public boolean hasPhotoEditor() {
        return mHasPhotoEditor;
    }

    /**
     * Return true if internal {@link PhotoEditorView} has a {@link Photo} set.
     */
    public boolean hasSetPhoto() {
        return mPhoto.hasSetPhoto();
    }

    public PhotoEditorView getPhotoEditor() {
        return mPhoto;
    }

    /**
     * @return the RawContact ID that this editor is editing.
     */
    public abstract long getRawContactId();

    /**
     * Set the internal state for this view, given a current
     * {@link RawContactDelta} state and the {@link AccountType} that
     * apply to that state.
     */
    public abstract void setState(RawContactDelta state, AccountType source, ViewIdGenerator vig,
            boolean isProfile);
    public abstract void setExistDataState(boolean IsEmailExisted,boolean IsSecondaryPhoneExisted, int existedPhoneType );

    /* package */ void setExpanded(boolean value) {
        // only allow collapsing if we are one of several children
        final boolean newValue;
        if (getParent() instanceof ViewGroup && ((ViewGroup) getParent()).getChildCount() == 1) {
            newValue = true;
        } else {
            newValue = value;
        }

        if (newValue == mExpanded) return;
        mExpanded = newValue;
        mBody.setVisibility(newValue ? View.VISIBLE : View.GONE);
        mDivider.setVisibility(newValue ? View.GONE : View.VISIBLE);
    }
}
