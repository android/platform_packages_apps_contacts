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

package com.android.contacts.list;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.view.View;
import android.widget.CheckBox;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.group.GroupUtil;

import java.util.TreeSet;

/**
 * An extension of the default contact adapter that adds checkboxes and the ability
 * to select multiple contacts.
 */
public abstract class MultiSelectEntryContactListAdapter extends ContactEntryListAdapter {

    private SelectedContactsListener mSelectedContactsListener;
    private DeleteContactListener mDeleteContactListener;
    private TreeSet<Long> mSelectedContactIds = new TreeSet<>();
    private boolean mDisplayCheckBoxes;
    private final int mContactIdColumnIndex;

    public interface SelectedContactsListener {
        void onSelectedContactsChanged();
    }

    public interface DeleteContactListener {
        void onContactDeleteClicked(int position);
    }

    /**
     * @param contactIdColumnIndex the column index of the contact ID in the underlying cursor;
     *         it is passed in so that this adapter can support different kinds of contact
     *         lists (e.g. aggregate contacts or raw contacts).
     */
    public MultiSelectEntryContactListAdapter(Context context, int contactIdColumnIndex) {
        super(context);
        mContactIdColumnIndex = contactIdColumnIndex;
    }

    /**
     * Returns the column index of the contact ID in the underlying cursor; the contact ID
     * retrieved using this index is the value that is selected by this adapter (and returned
     * by {@link #getSelectedContactIds}).
     */
    public int getContactColumnIdIndex() {
        return mContactIdColumnIndex;
    }

    public DeleteContactListener getDeleteContactListener() {
        return mDeleteContactListener;
    }

    public void setDeleteContactListener(DeleteContactListener deleteContactListener) {
        mDeleteContactListener = deleteContactListener;
    }

    public void setSelectedContactsListener(SelectedContactsListener listener) {
        mSelectedContactsListener = listener;
    }

    /**
     * Returns set of selected contacts.
     */
    public TreeSet<Long> getSelectedContactIds() {
        return mSelectedContactIds;
    }

    public boolean hasSelectedItems() {
        return mSelectedContactIds.size() > 0;
    }

    /**
     * Returns the selected contacts as an array.
     */
    public long[] getSelectedContactIdsArray() {
        return GroupUtil.convertLongSetToLongArray(mSelectedContactIds);
    }

    /**
     * Update set of selected contacts. This changes which checkboxes are set.
     */
    public void setSelectedContactIds(TreeSet<Long> selectedContactIds) {
        this.mSelectedContactIds = selectedContactIds;
        notifyDataSetChanged();
        if (mSelectedContactsListener != null) {
            mSelectedContactsListener.onSelectedContactsChanged();
        }
    }

    /**
     * Shows checkboxes beside contacts if {@param displayCheckBoxes} is {@code TRUE}.
     * Not guaranteed to work with all configurations of this adapter.
     */
    public void setDisplayCheckBoxes(boolean showCheckBoxes) {
        mDisplayCheckBoxes = showCheckBoxes;
        notifyDataSetChanged();
        if (mSelectedContactsListener != null) {
            mSelectedContactsListener.onSelectedContactsChanged();
        }
    }

    /**
     * Checkboxes are being displayed beside contacts.
     */
    public boolean isDisplayingCheckBoxes() {
        return mDisplayCheckBoxes;
    }

    /**
     * Toggle the checkbox beside the contact for {@param contactId}.
     */
    public void toggleSelectionOfContactId(long contactId) {
        if (mSelectedContactIds.contains(contactId)) {
            mSelectedContactIds.remove(contactId);
        } else {
            mSelectedContactIds.add(contactId);
        }
        notifyDataSetChanged();
        if (mSelectedContactsListener != null) {
            mSelectedContactsListener.onSelectedContactsChanged();
        }
    }

    @Override
    public long getItemId(int position) {
        Cursor cursor = (Cursor) getItem(position);
        if (cursor != null) {
            return cursor.getLong(getContactColumnIdIndex());
        }
        return 0;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        final ContactListItemView view = (ContactListItemView) itemView;
        bindViewId(view, cursor, getContactColumnIdIndex());
        bindCheckBox(view, cursor, partition == ContactsContract.Directory.DEFAULT);
    }

    /**
      * Loads the photo for the photo view.
      * @param photoIdColumn Index of the photo id column
      * @param lookUpKeyColumn Index of the lookup key column
      * @param displayNameColumn Index of the display name column
      */
    protected void bindPhoto(final ContactListItemView view, final Cursor cursor,
           final int photoIdColumn, final int lookUpKeyColumn, final int displayNameColumn) {
        final long photoId = cursor.isNull(photoIdColumn)
            ? 0 : cursor.getLong(photoIdColumn);
        final ContactPhotoManager.DefaultImageRequest imageRequest = photoId == 0
            ? getDefaultImageRequestFromCursor(cursor, displayNameColumn,
            lookUpKeyColumn)
            : null;
        getPhotoLoader().loadThumbnail(view.getPhotoView(), photoId, false, getCircularPhotos(),
                imageRequest);
    }

    private void bindCheckBox(ContactListItemView view, Cursor cursor, boolean isLocalDirectory) {
        // Disable clicking on all contacts from remote directories when showing check boxes. We do
        // this by telling the view to handle clicking itself.
        view.setClickable(!isLocalDirectory && mDisplayCheckBoxes);
        // Only show checkboxes if mDisplayCheckBoxes is enabled. Also, never show the
        // checkbox for other directory contacts except local directory.
        if (!mDisplayCheckBoxes || !isLocalDirectory) {
            view.hideCheckBox();
            return;
        }
        final CheckBox checkBox = view.getCheckBox();
        final long contactId = cursor.getLong(mContactIdColumnIndex);
        checkBox.setChecked(mSelectedContactIds.contains(contactId));
        checkBox.setClickable(false);
        checkBox.setTag(contactId);
    }
}
