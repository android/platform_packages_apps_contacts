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

package com.android.contacts;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents.Insert;
import android.telecom.PhoneAccount;
import android.text.TextUtils;

import com.android.contacts.activities.RequestPermissionsActivity;
import com.android.contacts.util.ImplicitIntentsUtil;

/**
 * Activity that intercepts DIAL and VIEW intents for phone numbers for devices that can not
 * be used as a phone. This allows the user to see the phone number
 */
public class NonPhoneActivity extends ContactsActivity {

    private static final String PHONE_NUMBER_KEY = "PHONE_NUMBER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RequestPermissionsActivity.startPermissionActivityIfNeeded(this);

        final String phoneNumber = getPhoneNumber();
        if (TextUtils.isEmpty(phoneNumber)) {
            finish();
            return;
        }

        final NonPhoneDialogFragment fragment = new NonPhoneDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(PHONE_NUMBER_KEY, phoneNumber);
        fragment.setArguments(bundle);
        getFragmentManager().beginTransaction().add(fragment, "Fragment").commitAllowingStateLoss();
    }

    private String getPhoneNumber() {
        if (getIntent() == null) return null;
        final Uri data = getIntent().getData();
        if (data == null) return null;
        final String scheme = data.getScheme();
        if (!PhoneAccount.SCHEME_TEL.equals(scheme)) return null;
        return getIntent().getData().getSchemeSpecificPart();
    }

    public static final class NonPhoneDialogFragment extends DialogFragment
            implements OnClickListener {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final AlertDialog alertDialog;
            alertDialog = new AlertDialog.Builder(getActivity(), R.style.NonPhoneDialogTheme)
                    .create();
            alertDialog.setTitle(R.string.non_phone_caption);
            alertDialog.setMessage(getArgumentPhoneNumber());
            alertDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                    getActivity().getString(R.string.non_phone_add_to_contacts), this);
            alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getActivity().getString(R.string.non_phone_close), this);
            return alertDialog;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                intent.putExtra(Insert.PHONE, getArgumentPhoneNumber());
                ImplicitIntentsUtil.startActivityInApp(getActivity(), intent);
            }
            dismiss();
        }

        private String getArgumentPhoneNumber() {
            return getArguments().getString(PHONE_NUMBER_KEY);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            // During screen rotation, getActivity returns null. In this case we do not
            // want to close the Activity anyway
            final Activity activity = getActivity();
            if (activity != null) activity.finish();
        }
    }
}
