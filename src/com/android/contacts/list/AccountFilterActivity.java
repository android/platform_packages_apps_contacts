/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;

import com.android.contacts.R;
import com.android.contacts.model.AccountTypeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a list of all available accounts, letting the user select under which account to view
 * contacts.
 */
public class AccountFilterActivity extends Activity implements AdapterView.OnItemClickListener {

    private static final int SUBACTIVITY_CUSTOMIZE_FILTER = 0;

    public static final String EXTRA_CONTACT_LIST_FILTER = "contactListFilter";

    private ListView mListView;

    // The default contact list type, it should be either FILTER_TYPE_ALL_ACCOUNTS or
    // FILTER_TYPE_CUSTOM, since those are the only two options we give the user.
    private int mCurrentFilterType;

    private ContactListFilterView mCustomFilterView; // the "Customize" filter

    private boolean mIsCustomFilterViewSelected;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.contact_list_filter);

        mListView = (ListView) findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mCurrentFilterType = ContactListFilterController.getInstance(this).isCustomFilterPersisted()
                ? ContactListFilter.FILTER_TYPE_CUSTOM
                : ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS;

        // We don't need to use AccountFilterUtil.FilterLoader since we only want to show
        // the "All contacts" and "Customize" options.
        final List<ContactListFilter> filters = new ArrayList<>();
        filters.add(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
        filters.add(ContactListFilter.createFilterWithType(
                ContactListFilter.FILTER_TYPE_CUSTOM));
        mListView.setAdapter(new FilterListAdapter(this, filters, mCurrentFilterType));
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final ContactListFilterView listFilterView = (ContactListFilterView) view;
        final ContactListFilter filter = (ContactListFilter) view.getTag();
        if (filter == null) return; // Just in case
        if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
            mCustomFilterView = listFilterView;
            mIsCustomFilterViewSelected = listFilterView.isChecked();
            final Intent intent = new Intent(this, CustomContactListFilterActivity.class)
                    .putExtra(CustomContactListFilterActivity.EXTRA_CURRENT_LIST_FILTER_TYPE,
                            mCurrentFilterType);
            listFilterView.setActivated(true);
            // Switching activity has the highest priority. So when we open another activity, the
            // announcement that indicates an account is checked will be interrupted. This is the
            // way to overcome -- View.announceForAccessibility(CharSequence text);
            listFilterView.announceForAccessibility(listFilterView.generateContentDescription());
            startActivityForResult(intent, SUBACTIVITY_CUSTOMIZE_FILTER);
        } else {
            listFilterView.setActivated(true);
            listFilterView.announceForAccessibility(listFilterView.generateContentDescription());
            final Intent intent = new Intent();
            intent.putExtra(EXTRA_CONTACT_LIST_FILTER, filter);
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_CANCELED && mCustomFilterView != null &&
                !mIsCustomFilterViewSelected) {
            mCustomFilterView.setActivated(false);
            return;
        }

        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        switch (requestCode) {
            case SUBACTIVITY_CUSTOMIZE_FILTER: {
                final Intent intent = new Intent();
                ContactListFilter filter = ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_CUSTOM);
                intent.putExtra(EXTRA_CONTACT_LIST_FILTER, filter);
                setResult(Activity.RESULT_OK, intent);
                finish();
                break;
            }
        }
    }

    private static class FilterListAdapter extends BaseAdapter {
        private final List<ContactListFilter> mFilters;
        private final LayoutInflater mLayoutInflater;
        private final AccountTypeManager mAccountTypes;
        private final int mCurrentFilter;

        public FilterListAdapter(
                Context context, List<ContactListFilter> filters, int current) {
            mLayoutInflater = (LayoutInflater) context.getSystemService
                    (Context.LAYOUT_INFLATER_SERVICE);
            mFilters = filters;
            mCurrentFilter = current;
            mAccountTypes = AccountTypeManager.getInstance(context);
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public ContactListFilter getItem(int position) {
            return mFilters.get(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            final ContactListFilterView view;
            if (convertView != null) {
                view = (ContactListFilterView) convertView;
            } else {
                view = (ContactListFilterView) mLayoutInflater.inflate(
                        R.layout.contact_list_filter_item, parent, false);
            }
            view.setSingleAccount(mFilters.size() == 1);
            final ContactListFilter filter = mFilters.get(position);
            view.setContactListFilter(filter);
            view.bindView(mAccountTypes);
            view.setTag(filter);
            view.setActivated(filter.filterType == mCurrentFilter);
            return view;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // We have two logical "up" Activities: People and Phone.
                // Instead of having one static "up" direction, behave like back as an
                // exceptional case.
                onBackPressed();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
