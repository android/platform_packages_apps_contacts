/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;

/**
 * Equivalent to DefaultContactListAdapter, except with an optional header entry that has the same
 * formatting as the other entries in the list.
 *
 * This header entry is hidden when in search mode. Should not be used with lists that contain a
 * "Me" contact.
 */
public class HeaderEntryContactListAdapter extends DefaultContactListAdapter {

    private boolean mShowCreateContact;

    public HeaderEntryContactListAdapter(Context context) {
        super(context);
    }

    private int getHeaderEntryCount() {
        return isSearchMode() || !mShowCreateContact ? 0 : 1;
    }

    /**
     * Whether the first entry should be "Create contact", when not in search mode.
     */
    public void setShowCreateContact(boolean showCreateContact) {
        mShowCreateContact = showCreateContact;
        invalidate();
    }

    @Override
    public int getCount() {
        return super.getCount() + getHeaderEntryCount();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position == 0 && getHeaderEntryCount() > 0) {
            final ContactListItemView itemView;
            if (convertView == null) {
                // Pass the cursor down. Don't worry, it isn't used.
                itemView = newView(getContext(), 0, getCursor(0), 0, parent);
            } else {
                itemView = (ContactListItemView) convertView;
            }
            itemView.setDrawableResource(R.drawable.quantum_ic_person_add_vd_theme_24);
            itemView.setDisplayName(getContext().getResources().getString(
                    R.string.header_entry_contact_list_adapter_header_title));
            return itemView;
        }
        return super.getView(position - getHeaderEntryCount(), convertView, parent);
    }

    @Override
    public Object getItem(int position) {
        return super.getItem(position - getHeaderEntryCount());
    }

    @Override
    public boolean isEnabled(int position) {
        return position < getHeaderEntryCount() || super
                .isEnabled(position - getHeaderEntryCount());
    }

    @Override
    public int getPartitionForPosition(int position) {
        return super.getPartitionForPosition(position - getHeaderEntryCount());
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position + getHeaderEntryCount());
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && getHeaderEntryCount() > 0) {
            return getViewTypeCount() - 1;
        }
        return super.getItemViewType(position - getHeaderEntryCount());
    }

    @Override
    public int getViewTypeCount() {
        // One additional view type, for the header entry.
        return super.getViewTypeCount() + 1;
    }

    @Override
    protected boolean getExtraStartingSection() {
        return getHeaderEntryCount() > 0;
    }
}
