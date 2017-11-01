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

package com.android.contacts.editor;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.R;
import com.android.contacts.editor.AggregationSuggestionEngine.Suggestion;

/**
 * A view that contains a name, picture and other data for a contact aggregation suggestion.
 */
public class AggregationSuggestionView extends LinearLayout {

    public interface Listener {
        /**
         * Callback that passes the contact URI and raw contact ID to edit instead of the
         * current contact.
         */
        void onEditAction(Uri contactLookupUri, long rawContactId);
    }

    private Listener mListener;
    private Suggestion mSuggestion;

    public AggregationSuggestionView(Context context) {
        super(context);
    }

    public AggregationSuggestionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AggregationSuggestionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void bindSuggestion(Suggestion suggestion) {
        mSuggestion = suggestion;
        final ContactPhotoManager.DefaultImageRequest
                request = new ContactPhotoManager.DefaultImageRequest(
                suggestion.name, String.valueOf(suggestion.rawContactId), /* isCircular = */ false);
        final ImageView photoView = (ImageView) findViewById(
                R.id.aggregation_suggestion_photo);
        ContactPhotoManager.getInstance(getContext()).loadThumbnail(photoView,
                suggestion.photoId,
                /* darkTheme = */ false,
                /* isCircular = */ false,
                request);

        final TextView name = (TextView) findViewById(R.id.aggregation_suggestion_name);
        name.setText(suggestion.name);

        final TextView data = (TextView) findViewById(R.id.aggregation_suggestion_data);
        String dataText = null;
        if (suggestion.nickname != null) {
            dataText = suggestion.nickname;
        } else if (suggestion.emailAddress != null) {
            dataText = suggestion.emailAddress;
        } else if (suggestion.phoneNumber != null) {
            dataText = suggestion.phoneNumber;
            // Phone numbers should always be in LTR mode.
            data.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        data.setText(dataText);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public boolean handleItemClickEvent() {
        if (mListener != null && isEnabled()) {
            if (TextUtils.isEmpty(mSuggestion.contactLookupKey)) {
                return false;
            }
            mListener.onEditAction(
                    Contacts.getLookupUri(mSuggestion.contactId, mSuggestion.contactLookupKey),
                    mSuggestion.rawContactId);
            return true;
        }
        return false;
    }
}
