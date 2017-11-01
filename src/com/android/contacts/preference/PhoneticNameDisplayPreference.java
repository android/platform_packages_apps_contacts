/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * Custom preference: phonetic name fields.
 */
public final class PhoneticNameDisplayPreference extends ListPreference {

    public static final int SHOW_ALWAYS = 0;
    public static final int HIDE_IF_EMPTY = 1;

    private Context mContext;
    private ContactsPreferences mPreferences;

    public PhoneticNameDisplayPreference(Context context) {
        super(context);
        prepare();
    }

    public PhoneticNameDisplayPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        prepare();
    }

    private void prepare() {
        mContext = getContext();
        mPreferences = new ContactsPreferences(mContext);
        setEntries(new String[]{
                mContext.getString(R.string.editor_options_always_show_phonetic_names),
                mContext.getString(R.string.editor_options_hide_phonetic_names_if_empty)
        });
        setEntryValues(new String[]{
                String.valueOf(SHOW_ALWAYS),
                String.valueOf(HIDE_IF_EMPTY),
        });
        setValue(String.valueOf(mPreferences.getPhoneticNameDisplayPreference()));
    }

    @Override
    protected boolean shouldPersist() {
        return false;   // This preference takes care of its own storage
    }

    @Override
    public CharSequence getSummary() {
        switch (mPreferences.getPhoneticNameDisplayPreference()) {
            case SHOW_ALWAYS:
                return mContext.getString(R.string.editor_options_always_show_phonetic_names);
            case HIDE_IF_EMPTY:
                return mContext.getString(R.string.editor_options_hide_phonetic_names_if_empty);
        }
        return null;
    }

    @Override
    protected boolean persistString(String value) {
        final int newValue = Integer.parseInt(value);
        if (newValue != mPreferences.getPhoneticNameDisplayPreference()) {
            mPreferences.setPhoneticNameDisplayPreference(newValue);
            notifyChanged();
        }
        return true;
    }

    // UX recommendation is not to show cancel button on such lists.
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setNegativeButton(null, null);
    }
}
