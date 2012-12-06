/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.DataKind;

import java.util.ArrayList;

/**
 * Custom view that displays external contacts in the edit screen.
 */
public class RawContactReadOnlyEditorView extends BaseRawContactEditorView
        implements OnClickListener {
    private LayoutInflater mInflater;

    private TextView mName;
    private Button mEditExternallyButton;
    private ViewGroup mGeneral;

    private View mAccountContainer;
    private ImageView mAccountIcon;
    private TextView mAccountTypeTextView;
    private TextView mAccountNameTextView;

    private String mAccountName;
    private String mAccountType;
    private String mDataSet;
    private long mRawContactId = -1;

    private Listener mListener;

    public interface Listener {
        void onExternalEditorRequest(AccountWithDataSet account, Uri uri);
    }

    public RawContactReadOnlyEditorView(Context context) {
        super(context);
    }

    public RawContactReadOnlyEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mName = (TextView) findViewById(R.id.read_only_name);
        mEditExternallyButton = (Button) findViewById(R.id.button_edit_externally);
        mEditExternallyButton.setOnClickListener(this);
        mGeneral = (ViewGroup)findViewById(R.id.sect_general);

        mAccountContainer = findViewById(R.id.account_container);
        mAccountIcon = (ImageView) findViewById(R.id.account_icon);
        mAccountTypeTextView = (TextView) findViewById(R.id.account_type);
        mAccountNameTextView = (TextView) findViewById(R.id.account_name);
    }

    @Override
    public void setExistDataState(boolean IsEmailExisted,boolean IsSecondaryPhoneExisted,int existedPhoneType) {
    }
    /**
     * Set the internal state for this view, given a current
     * {@link RawContactDelta} state and the {@link AccountType} that
     * apply to that state.
     */
    @Override
    public void setState(RawContactDelta state, AccountType type, ViewIdGenerator vig,
            boolean isProfile) {
        // Remove any existing sections
        mGeneral.removeAllViews();

        // Bail if invalid state or source
        if (state == null || type == null) return;

        // Make sure we have StructuredName
        RawContactModifier.ensureKindExists(state, type, StructuredName.CONTENT_ITEM_TYPE);

        // Fill in the header info
        mAccountName = state.getAccountName();
        mAccountType = state.getAccountType();
        mDataSet = state.getDataSet();

        if (isProfile) {
            if (TextUtils.isEmpty(mAccountName)) {
                mAccountNameTextView.setVisibility(View.GONE);
                mAccountTypeTextView.setText(R.string.local_profile_title);
            } else {
                CharSequence accountType = type.getDisplayLabel(mContext);
                mAccountTypeTextView.setText(mContext.getString(R.string.external_profile_title,
                        accountType));
                mAccountNameTextView.setText(mAccountName);
            }
        } else {
            CharSequence accountType = type.getDisplayLabel(mContext);
            if (TextUtils.isEmpty(accountType)) {
                accountType = mContext.getString(R.string.account_phone);
            }
            if (!TextUtils.isEmpty(mAccountName)) {
                mAccountNameTextView.setVisibility(View.VISIBLE);
                mAccountNameTextView.setText(
                        mContext.getString(R.string.from_account_format, mAccountName));
            } else {
                // Hide this view so the other text view will be centered vertically
                mAccountNameTextView.setVisibility(View.GONE);
            }
            mAccountTypeTextView.setText(mContext.getString(R.string.account_type_format,
                    accountType));
        }
        mAccountTypeTextView.setTextColor(mContext.getResources().getColor(
                R.color.secondary_text_color));

        // TODO: Expose data set in the UI somehow?

        mAccountIcon.setImageDrawable(type.getDisplayIcon(mContext));

        mRawContactId = state.getRawContactId();

        ValuesDelta primary;

        // Photo
        DataKind kind = type.getKindForMimetype(Photo.CONTENT_ITEM_TYPE);
        if (kind != null) {
            RawContactModifier.ensureKindExists(state, type, Photo.CONTENT_ITEM_TYPE);
            boolean hasPhotoEditor = type.getKindForMimetype(Photo.CONTENT_ITEM_TYPE) != null;
            setHasPhotoEditor(hasPhotoEditor);
            primary = state.getPrimaryEntry(Photo.CONTENT_ITEM_TYPE);
            getPhotoEditor().setValues(kind, primary, state, !type.areContactsWritable(), vig);
        }

        // Name
        primary = state.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
        mName.setText(primary != null ? primary.getAsString(StructuredName.DISPLAY_NAME) :
                mContext.getString(R.string.missing_name));

        if (type.getEditContactActivityClassName() != null) {
            mAccountContainer.setBackgroundDrawable(null);
            mAccountContainer.setEnabled(false);
            mEditExternallyButton.setVisibility(View.VISIBLE);
        } else {
            mAccountContainer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Toast.makeText(mContext, mContext.getString(R.string.contact_read_only),
                            Toast.LENGTH_SHORT).show();
                }
            });
            mEditExternallyButton.setVisibility(View.GONE);
        }

        final Resources res = mContext.getResources();
        // Phones
        ArrayList<ValuesDelta> phones = state.getMimeEntries(Phone.CONTENT_ITEM_TYPE);
        if (phones != null) {
            for (int i = 0; i < phones.size(); i++) {
                ValuesDelta phone = phones.get(i);
                final String phoneNumber = PhoneNumberUtils.formatNumber(
                        phone.getPhoneNumber(),
                        phone.getPhoneNormalizedNumber(),
                        GeoUtil.getCurrentCountryIso(getContext()));
                final CharSequence phoneType;
                if (phone.phoneHasType()) {
                    phoneType = Phone.getTypeLabel(
                            res, phone.getPhoneType(), phone.getPhoneLabel());
                } else {
                    phoneType = null;
                }
                bindData(mContext.getText(R.string.phoneLabelsGroup), phoneNumber, phoneType,
                        i == 0, true);
            }
        }

        // Emails
        ArrayList<ValuesDelta> emails = state.getMimeEntries(Email.CONTENT_ITEM_TYPE);
        if (emails != null) {
            for (int i = 0; i < emails.size(); i++) {
                ValuesDelta email = emails.get(i);
                final String emailAddress = email.getEmailData();
                final CharSequence emailType;
                if (email.emailHasType()) {
                    emailType = Email.getTypeLabel(
                            res, email.getEmailType(), email.getEmailLabel());
                } else {
                    emailType = null;
                }
                bindData(mContext.getText(R.string.emailLabelsGroup), emailAddress, emailType,
                        i == 0);
            }
        }

        // Hide mGeneral if it's empty
        if (mGeneral.getChildCount() > 0) {
            mGeneral.setVisibility(View.VISIBLE);
        } else {
            mGeneral.setVisibility(View.GONE);
        }
    }

    private void bindData(CharSequence titleText, CharSequence data, CharSequence type,
            boolean isFirstEntry) {
        bindData(titleText, data, type, isFirstEntry, false);
    }

    private void bindData(CharSequence titleText, CharSequence data, CharSequence type,
            boolean isFirstEntry, boolean forceLTR) {
        final View field = mInflater.inflate(R.layout.item_read_only_field, mGeneral, false);
        final View divider = field.findViewById(R.id.divider);
        if (isFirstEntry) {
            final TextView titleView = (TextView) field.findViewById(R.id.kind_title);
            titleView.setText(titleText);
            divider.setVisibility(View.GONE);
        } else {
            View titleContainer = field.findViewById(R.id.kind_title_layout);
            titleContainer.setVisibility(View.GONE);
            divider.setVisibility(View.VISIBLE);
        }
        final TextView dataView = (TextView) field.findViewById(R.id.data);
        dataView.setText(data);
        if (forceLTR) {
            dataView.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        final TextView typeView = (TextView) field.findViewById(R.id.type);
        if (!TextUtils.isEmpty(type)) {
            typeView.setText(type);
        } else {
            typeView.setVisibility(View.GONE);
        }

        mGeneral.addView(field);
    }

    @Override
    public long getRawContactId() {
        return mRawContactId;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.button_edit_externally) {
            if (mListener != null) {
                mListener.onExternalEditorRequest(
                        new AccountWithDataSet(mAccountName, mAccountType, mDataSet),
                        ContentUris.withAppendedId(RawContacts.CONTENT_URI, mRawContactId));
            }
        }
    }
}
