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

package com.android.contacts.preference;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.android.contacts.R;

/**
 * Custom preference: sort-by.
 */
public final class SortOrderPreference extends ListPreference {

    private ContactsPreferences mPreferences;
    private Context mContext;

    public SortOrderPreference(Context context) {
        super(context);
        prepare();
    }

    public SortOrderPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    private void prepare() {
        mContext = getContext();
        mPreferences = new ContactsPreferences(mContext);
        setEntries(new String[]{
                mContext.getString(R.string.display_options_sort_by_given_name),
                mContext.getString(R.string.display_options_sort_by_family_name),
        });
        setEntryValues(new String[]{
                String.valueOf(ContactsPreferences.SORT_ORDER_PRIMARY),
                String.valueOf(ContactsPreferences.SORT_ORDER_ALTERNATIVE),
        });
        setValue(String.valueOf(mPreferences.getSortOrder()));
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    public CharSequence getSummary() {
        switch (mPreferences.getSortOrder()) {
            case ContactsPreferences.SORT_ORDER_PRIMARY:
                return mContext.getString(R.string.display_options_sort_by_given_name);
            case ContactsPreferences.SORT_ORDER_ALTERNATIVE:
                return mContext.getString(R.string.display_options_sort_by_family_name);
        }
        return null;
    }

    @Override
    protected boolean persistString(String value) {
        int newValue = Integer.parseInt(value);
        if (newValue != mPreferences.getSortOrder()) {
            mPreferences.setSortOrder(newValue);
            notifyChanged();
        }
        return true;
    }

    @Override
    // UX recommendation is not to show cancel button on such lists.
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNegativeButton(null, null);
    }
}
