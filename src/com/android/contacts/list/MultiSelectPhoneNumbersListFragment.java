/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.logging.ListEvent;

import java.util.List;
import java.util.TreeSet;

/** Displays a list of phone numbers with check boxes. */
public class MultiSelectPhoneNumbersListFragment
        extends MultiSelectContactsListFragment<MultiSelectPhoneNumbersListAdapter> {

    public MultiSelectPhoneNumbersListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(false);
        setSearchMode(false);
        setHasOptionsMenu(true);
        setListType(ListEvent.ListType.PICK_PHONE);
    }

    @Override
    public MultiSelectPhoneNumbersListAdapter createListAdapter() {
        final MultiSelectPhoneNumbersListAdapter adapter =
                new MultiSelectPhoneNumbersListAdapter(getActivity());
        adapter.setArguments(getArguments());
        return adapter;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.items_multi_select, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        final MenuItem item = menu.findItem(R.id.menu_send);
        item.setVisible(getAdapter().hasSelectedItems());
        item.getActionView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onOptionsItemSelected(item);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        getActivity().finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_send) {
            final String scheme = getActivity().getIntent().getStringExtra(
                    UiIntentActions.SELECTION_SEND_SCHEME);
            final String title = getActivity().getIntent().getStringExtra(
                    UiIntentActions.SELECTION_SEND_TITLE);
            final List<String> items = GroupUtil.getSendToDataForIds(
                    getActivity(), getAdapter().getSelectedContactIdsArray(), scheme);
            final String list = TextUtils.join(",", items);
            GroupUtil.startSendToSelectionActivity(this, list, scheme, title);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final long[] selectedIds = getActivity().getIntent().getLongArrayExtra(
                UiIntentActions.SELECTION_DEFAULT_SELECTION);
        if (selectedIds != null && selectedIds.length != 0) {
            final TreeSet<Long> selectedIdsTree = new TreeSet<>();
            for (int i = 0; i < selectedIds.length; i++) {
                selectedIdsTree.add(selectedIds[i]);
            }
            getAdapter().setSelectedContactIds(selectedIdsTree);
            onSelectedContactsChanged();
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        displayCheckBoxes(true);
    }

    @Override
    protected boolean onItemLongClick(int position, long id) {
        return true;
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(com.android.contacts.R.layout.contact_list_content, null);
    }
}
