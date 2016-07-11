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
 * limitations under the License
 */

package com.android.contacts.editor;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.activities.CompactContactEditorActivity;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.util.ContactPhotoUtils;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * Contact editor with only the most important fields displayed initially.
 */
public class CompactContactEditorFragment extends ContactEditorBaseFragment implements
        CompactRawContactsEditorView.Listener, CompactPhotoEditorView.Listener {

    private static final String KEY_PHOTO_RAW_CONTACT_ID = "photo_raw_contact_id";
    private static final String KEY_UPDATED_PHOTOS = "updated_photos";
    private static final String KEY_IS_REMOVED_PHOTO = "is_removed_photo";

    private long mPhotoRawContactId;
    private Bundle mUpdatedPhotos = new Bundle();
    private boolean mIsRemovedPhoto = false;

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        if (savedState != null) {
            mPhotoRawContactId = savedState.getLong(KEY_PHOTO_RAW_CONTACT_ID);
            mUpdatedPhotos = savedState.getParcelable(KEY_UPDATED_PHOTOS);
            mIsRemovedPhoto = savedState.getBoolean(KEY_IS_REMOVED_PHOTO);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);

        final View view = inflater.inflate(
                R.layout.compact_contact_editor_fragment, container, false);
        mContent = (LinearLayout) view.findViewById(R.id.raw_contacts_editor_view);
        return view;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putLong(KEY_PHOTO_RAW_CONTACT_ID, mPhotoRawContactId);
        outState.putParcelable(KEY_UPDATED_PHOTOS, mUpdatedPhotos);
        outState.putBoolean(KEY_IS_REMOVED_PHOTO, mIsRemovedPhoto);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return revert();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void bindEditors() {
        if (!isReadyToBindEditors()) {
            return;
        }

        // Add input fields for the loaded Contact
        final CompactRawContactsEditorView editorView = getContent();
        editorView.setListener(this);
        editorView.setState(mState, getMaterialPalette(), mViewIdGenerator, mPhotoId,
                mHasNewContact, mIsUserProfile, mAccountWithDataSet);
        if (mHasNewContact && !TextUtils.isEmpty(mReadOnlyDisplayName)) {
            mReadOnlyNameEditorView = editorView.getPrimaryNameEditorView();
            editorView.maybeSetReadOnlyDisplayNameAsPrimary(mReadOnlyDisplayName);
        }

        // Set up the photo widget
        editorView.setPhotoListener(this);
        mPhotoRawContactId = editorView.getPhotoRawContactId();
        // If there is an updated full resolution photo apply it now, this will be the case if
        // the user selects or takes a new photo, then rotates the device.
        final Uri uri = (Uri) mUpdatedPhotos.get(String.valueOf(mPhotoRawContactId));
        if (uri != null) {
            editorView.setFullSizePhoto(uri);
        }
        if (mIsRemovedPhoto) {
            editorView.removePhoto();
        }

        // The editor is ready now so make it visible
        editorView.setEnabled(isEnabled());
        editorView.setVisibility(View.VISIBLE);

        // Refresh the ActionBar as the visibility of the join command
        // Activity can be null if we have been detached from the Activity.
        invalidateOptionsMenu();
    }

    private boolean isReadyToBindEditors() {
        if (mState.isEmpty()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "No data to bind editors");
            }
            return false;
        }
        if (mIsEdit && !mExistingContactDataReady) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Existing contact data is not ready to bind editors.");
            }
            return false;
        }
        if (mHasNewContact && !mNewContactDataReady) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "New contact data is not ready to bind editors.");
            }
            return false;
        }
        return true;
    }

    @Override
    protected View getAggregationAnchorView(long rawContactId) {
        return getContent().getAggregationAnchorView();
    }

    @Override
    protected void setGroupMetaData() {
        if (mGroupMetaData != null) {
            getContent().setGroupMetaData(mGroupMetaData);
        }
    }

    @Override
    protected boolean doSaveAction(int saveMode, Long joinContactId) {
        final Intent intent = ContactSaveService.createSaveContactIntent(mContext, mState,
                SAVE_MODE_EXTRA_KEY, saveMode, isEditingUserProfile(),
                ((Activity) mContext).getClass(),
                CompactContactEditorActivity.ACTION_SAVE_COMPLETED, mUpdatedPhotos,
                JOIN_CONTACT_ID_EXTRA_KEY, joinContactId);
        return startSaveService(mContext, intent, saveMode);
    }

    @Override
    protected void joinAggregate(final long contactId) {
        final Intent intent = ContactSaveService.createJoinContactsIntent(
                mContext, mContactIdForJoin, contactId, CompactContactEditorActivity.class,
                CompactContactEditorActivity.ACTION_JOIN_COMPLETED);
        mContext.startService(intent);
    }

    public void removePhoto() {
        getContent().removePhoto();
        mUpdatedPhotos.remove(String.valueOf(mPhotoRawContactId));
        mIsRemovedPhoto = true;
    }

    public void updatePhoto(Uri uri) throws FileNotFoundException {
        final Bitmap bitmap = ContactPhotoUtils.getBitmapFromUri(getActivity(), uri);
        if (bitmap == null || bitmap.getHeight() <= 0 || bitmap.getWidth() <= 0) {
            Toast.makeText(mContext, R.string.contactPhotoSavedErrorToast,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        mUpdatedPhotos.putParcelable(String.valueOf(mPhotoRawContactId), uri);
        mIsRemovedPhoto = false;
        getContent().updatePhoto(uri);
    }

    public void setPrimaryPhoto(CompactPhotoSelectionFragment.Photo photo) {
        getContent().setPrimaryPhoto(photo);

        // Update the photo ID we will try to match when selecting the photo to display
        mPhotoId = photo.photoId;
    }

    @Override
    public void onNameFieldChanged(long rawContactId, ValuesDelta valuesDelta) {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        acquireAggregationSuggestions(activity, rawContactId, valuesDelta);
    }

    @Override
    public void onRebindEditorsForNewContact(RawContactDelta oldState,
            AccountWithDataSet oldAccount, AccountWithDataSet newAccount) {
        mNewContactAccountChanged = true;
        mAccountWithDataSet = newAccount;
        rebindEditorsForNewContact(oldState, oldAccount, newAccount);
    }

    @Override
    public void onBindEditorsFailed() {
        final Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            Toast.makeText(activity, R.string.compact_editor_failed_to_load,
                    Toast.LENGTH_SHORT).show();
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        }
    }

    @Override
    public void onEditorsBound() {
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupsLoaderListener);
    }

    @Override
    public void onPhotoEditorViewClicked() {
        if (isEditingMultipleRawContacts()) {
            final ArrayList<CompactPhotoSelectionFragment.Photo> photos = getContent().getPhotos();
            if (photos.size() > 1) {
                updatePrimaryForSelection(photos);
                // For aggregate contacts, the user may select a new super primary photo from among
                // the (non-default) raw contact photos, or source a new photo.
                getEditorActivity().selectPhoto(photos, getPhotoMode());
                return;
            }
        }
        // For contacts composed of a single writable raw contact, or raw contacts have no more
        // than 1 photo, clicking the photo view simply opens the source photo dialog
        getEditorActivity().changePhoto(getPhotoMode());
    }

    // This method override photo's primary flag based on photoId and set the photo currently
    // shown in the editor to be the new primary no matter how many primary photos there are in
    // the photo picker. This is because the photos returned by "getPhoto" may contain 0, 1,
    // or 2+ primary photos and when we link contacts in the editor, the photos returned may change.
    // We need to put check mark on the photo currently shown in editor, so we override "primary".
    // This doesn't modify anything in the database,so there would be no pending changes.
    private void updatePrimaryForSelection(ArrayList<CompactPhotoSelectionFragment.Photo> photos) {
        for (CompactPhotoSelectionFragment.Photo photo : photos) {
            if (photo.photoId == mPhotoId) {
                photo.primary = true;
            } else {
                photo.primary = false;
            }
            updateContentDescription(photo);
        }
    }

    private void updateContentDescription(CompactPhotoSelectionFragment.Photo photo) {
        if (!TextUtils.isEmpty(photo.accountType)) {
            photo.contentDescription = getResources().getString(photo.primary ?
                            R.string.photo_view_description_checked :
                            R.string.photo_view_description_not_checked,
                    photo.accountType, photo.accountName);
            photo.contentDescriptionChecked = getResources().getString(
                    R.string.photo_view_description_checked,
                    photo.accountType, photo.accountName);
        } else {
            photo.contentDescription = getResources().getString(photo.primary ?
                    R.string.photo_view_description_checked_no_info :
                    R.string.photo_view_description_not_checked_no_info);
            photo.contentDescriptionChecked = getResources().getString(
                    R.string.photo_view_description_checked_no_info);
        }
    }

    @Override
    public void onRawContactSelected(Uri uri, long rawContactId, boolean isReadOnly) {
        final Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            final Intent intent = EditorIntents.createEditContactIntentForRawContact(
                    activity, uri, rawContactId, isReadOnly);
            activity.startActivity(intent);
        }
    }

    @Override
    public Bundle getUpdatedPhotos() {
        return mUpdatedPhotos;
    }

    private int getPhotoMode() {
        if (getContent().isWritablePhotoSet()) {
            return isEditingMultipleRawContacts()
                    ? PhotoActionPopup.Modes.MULTIPLE_WRITE_ABLE_PHOTOS
                    : PhotoActionPopup.Modes.WRITE_ABLE_PHOTO;
        }
        return PhotoActionPopup.Modes.NO_PHOTO;
    }

    private CompactContactEditorActivity getEditorActivity() {
        return (CompactContactEditorActivity) getActivity();
    }

    private CompactRawContactsEditorView getContent() {
        return (CompactRawContactsEditorView) mContent;
    }
}
