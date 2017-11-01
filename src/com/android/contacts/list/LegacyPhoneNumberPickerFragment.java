/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.net.Uri;
import android.util.Log;

/**
 * Version of PhoneNumberPickerFragment used specifically for legacy support.
 */
public class LegacyPhoneNumberPickerFragment extends PhoneNumberPickerFragment {

    private static final String TAG = "LegacyPhoneNumberPicker";

    @Override
    protected boolean getVisibleScrollbarEnabled() {
        return false;
    }

    @Override
    protected Uri getPhoneUri(int position) {
        final LegacyPhoneNumberListAdapter adapter = (LegacyPhoneNumberListAdapter) getAdapter();
        return adapter.getPhoneUri(position);
    }

    @Override
    protected String getLookupKey(int position) {
        // There is no lookup key for the legacy adapter.
        return null;
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        LegacyPhoneNumberListAdapter adapter = new LegacyPhoneNumberListAdapter(getActivity());
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    protected void setPhotoPosition(ContactEntryListAdapter adapter) {
        // no-op
    }

    @Override
    protected void startPhoneNumberShortcutIntent(Uri uri, boolean isVideoCall) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setPhotoPosition(ContactListItemView.PhotoPosition photoPosition) {
        Log.w(TAG, "setPhotoPosition() is ignored in legacy compatibility mode.");
    }
}
