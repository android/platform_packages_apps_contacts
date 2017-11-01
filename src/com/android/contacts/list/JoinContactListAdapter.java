/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.preference.ContactsPreferences;

public class JoinContactListAdapter extends ContactListAdapter {

    /** Maximum number of suggestions shown for joining aggregates */
    private static final int MAX_SUGGESTIONS = 4;

    public static final int PARTITION_SUGGESTIONS = 0;
    public static final int PARTITION_ALL_CONTACTS = 1;

    private long mTargetContactId;

    public JoinContactListAdapter(Context context) {
        super(context);
        setPinnedPartitionHeadersEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setIndexedPartition(PARTITION_ALL_CONTACTS);
        setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_NONE);
    }

    @Override
    protected void addPartitions() {
        // Partition 0: suggestions
        addPartition(false, true);

        // Partition 1: All contacts
        addPartition(createDefaultDirectoryPartition());
    }

    public void setTargetContactId(long targetContactId) {
        this.mTargetContactId = targetContactId;
    }

    @Override
    public void configureLoader(CursorLoader cursorLoader, long directoryId) {
        JoinContactLoader loader = (JoinContactLoader) cursorLoader;

        final Builder builder = Contacts.CONTENT_URI.buildUpon();
        builder.appendEncodedPath(String.valueOf(mTargetContactId));
        builder.appendEncodedPath(AggregationSuggestions.CONTENT_DIRECTORY);

        final String filter = getQueryString();
        if (!TextUtils.isEmpty(filter)) {
            builder.appendEncodedPath(Uri.encode(filter));
        }

        builder.appendQueryParameter("limit", String.valueOf(MAX_SUGGESTIONS));

        loader.setSuggestionUri(builder.build());

        // TODO simplify projection
        loader.setProjection(getProjection(false));
        final Uri allContactsUri;
        if (!TextUtils.isEmpty(filter)) {
            allContactsUri = buildSectionIndexerUri(Contacts.CONTENT_FILTER_URI).buildUpon()
                .appendEncodedPath(Uri.encode(filter))
                .appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                .build();
        } else {
            allContactsUri = buildSectionIndexerUri(Contacts.CONTENT_URI).buildUpon()
                .appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(Directory.DEFAULT))
                .build();
        }
        loader.setUri(allContactsUri);
        loader.setSelection(Contacts._ID + "!=?");
        loader.setSelectionArgs(new String[]{ String.valueOf(mTargetContactId) });
        if (getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY) {
            loader.setSortOrder(Contacts.SORT_KEY_PRIMARY);
        } else {
            loader.setSortOrder(Contacts.SORT_KEY_ALTERNATIVE);
        }
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    public void setSuggestionsCursor(Cursor cursor) {
        changeCursor(PARTITION_SUGGESTIONS, cursor);
    }

    @Override
    public void changeCursor(Cursor cursor) {
        changeCursor(PARTITION_ALL_CONTACTS, cursor);
    }

    @Override
    public void configureDefaultPartition(boolean showIfEmpty, boolean hasHeader) {
         // Don't change default partition parameters from these defaults
        super.configureDefaultPartition(false, true);
    }

    @Override
    public int getViewTypeCount() {
        return super.getViewTypeCount();
    }

    @Override
    public int getItemViewType(int partition, int position) {
        return super.getItemViewType(partition, position);
    }

    @Override
    protected View newHeaderView(Context context, int partition, Cursor cursor,
            ViewGroup parent) {
        switch (partition) {
            case PARTITION_SUGGESTIONS: {
                View view = inflate(R.layout.join_contact_picker_section_header, parent);
                ((TextView) view.findViewById(R.id.text)).setText(
                        R.string.separatorJoinAggregateSuggestions);
                return view;
            }
            case PARTITION_ALL_CONTACTS: {
                View view = inflate(R.layout.join_contact_picker_section_header, parent);
                ((TextView) view.findViewById(R.id.text)).setText(
                        R.string.separatorJoinAggregateAll);
                return view;
            }
        }

        return null;
    }

    @Override
    protected void bindHeaderView(View view, int partitionIndex, Cursor cursor) {
        // Header views are static - nothing needs to be bound
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        switch (partition) {
            case PARTITION_SUGGESTIONS:
            case PARTITION_ALL_CONTACTS:
                return super.newView(context, partition, cursor, position, parent);
        }
        return null;
    }

    private View inflate(int layoutId, ViewGroup parent) {
        return LayoutInflater.from(getContext()).inflate(layoutId, parent, false);
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        super.bindView(itemView, partition, cursor, position);
        switch (partition) {
            case PARTITION_SUGGESTIONS: {
                final ContactListItemView view = (ContactListItemView) itemView;
                view.setSectionHeader(null);
                bindPhoto(view, partition, cursor);
                bindNameAndViewId(view, cursor);
                break;
            }
            case PARTITION_ALL_CONTACTS: {
                final ContactListItemView view = (ContactListItemView) itemView;
                bindSectionHeaderAndDivider(view, position, cursor);
                bindPhoto(view, partition, cursor);
                bindNameAndViewId(view, cursor);
                break;
            }
        }
    }

    @Override
    public Uri getContactUri(int partitionIndex, Cursor cursor) {
        long contactId = cursor.getLong(ContactQuery.CONTACT_ID);
        String lookupKey = cursor.getString(ContactQuery.CONTACT_LOOKUP_KEY);
        return Contacts.getLookupUri(contactId, lookupKey);
    }
}
