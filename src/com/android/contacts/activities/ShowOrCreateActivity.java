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

package com.android.contacts.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.RawContacts;
import android.telecom.PhoneAccount;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.ContactsActivity;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.NotifyingAsyncQueryHandler;

/**
 * Handle several edge cases around showing or possibly creating contacts in
 * connected with a specific E-mail address or phone number. Will search based
 * on incoming {@link Intent#getData()} as described by
 * {@link Intents#SHOW_OR_CREATE_CONTACT}.
 * <ul>
 * <li>If no matching contacts found, will prompt user with dialog to add to a
 * contact, then will use {@link Intent#ACTION_INSERT_OR_EDIT} to let create new
 * contact or edit new data into an existing one.
 * <li>If one matching contact found, directly show {@link Intent#ACTION_VIEW}
 * that specific contact.
 * <li>If more than one matching found, show list of matching contacts using
 * {@link Intent#ACTION_SEARCH}.
 * </ul>
 */
public final class ShowOrCreateActivity extends ContactsActivity
        implements NotifyingAsyncQueryHandler.AsyncQueryListener {
    static final String TAG = "ShowOrCreateActivity";
    static final boolean LOGD = false;

    static final String[] PHONES_PROJECTION = new String[] {
        PhoneLookup._ID,
        PhoneLookup.LOOKUP_KEY,
    };

    static final String[] CONTACTS_PROJECTION = new String[] {
        Email.CONTACT_ID,
        Email.LOOKUP_KEY,
    };

    static final int CONTACT_ID_INDEX = 0;
    static final int LOOKUP_KEY_INDEX = 1;

    static final int CREATE_CONTACT_DIALOG = 1;

    static final int QUERY_TOKEN = 42;

    private NotifyingAsyncQueryHandler mQueryHandler;

    private Bundle mCreateExtras;
    private String mCreateDescrip;
    private boolean mCreateForce;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (RequestPermissionsActivity.startPermissionActivityIfNeeded(this)) {
            return;
        }

        // Create handler if doesn't exist, otherwise cancel any running
        if (mQueryHandler == null) {
            mQueryHandler = new NotifyingAsyncQueryHandler(this, this);
        } else {
            mQueryHandler.cancelOperation(QUERY_TOKEN);
        }

        final Intent intent = getIntent();
        final Uri data = intent.getData();

        // Unpack scheme and target data from intent
        String scheme = null;
        String ssp = null;
        if (data != null) {
            scheme = data.getScheme();
            ssp = data.getSchemeSpecificPart();
        }

        // Build set of extras for possible use when creating contact
        mCreateExtras = new Bundle();
        Bundle originalExtras = intent.getExtras();
        if (originalExtras != null) {
            mCreateExtras.putAll(originalExtras);
        }

        // Read possible extra with specific title
        mCreateDescrip = intent.getStringExtra(Intents.EXTRA_CREATE_DESCRIPTION);
        if (mCreateDescrip == null) {
            mCreateDescrip = ssp;
        }

        // Allow caller to bypass dialog prompt
        mCreateForce = intent.getBooleanExtra(Intents.EXTRA_FORCE_CREATE, false);

        // Handle specific query request
        if (ContactsUtils.SCHEME_MAILTO.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.EMAIL, ssp);

            Uri uri = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, Uri.encode(ssp));
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, CONTACTS_PROJECTION, null, null, null);

        } else if (PhoneAccount.SCHEME_TEL.equals(scheme)) {
            mCreateExtras.putString(Intents.Insert.PHONE, ssp);

            Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, ssp);
            mQueryHandler.startQuery(QUERY_TOKEN, null, uri, PHONES_PROJECTION, null, null, null);

        } else {
            Log.w(TAG, "Invalid intent:" + getIntent());
            finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mQueryHandler != null) {
            mQueryHandler.cancelOperation(QUERY_TOKEN);
        }
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (cursor == null) {
            // Bail when problem running query in background
            finish();
            return;
        }

        // Count contacts found by query
        int count = 0;
        long contactId = -1;
        String lookupKey = null;
        try {
            count = cursor.getCount();
            if (count == 1 && cursor.moveToFirst()) {
                // Try reading ID if only one contact returned
                contactId = cursor.getLong(CONTACT_ID_INDEX);
                lookupKey = cursor.getString(LOOKUP_KEY_INDEX);
            }
        } finally {
            cursor.close();
        }

        if (count == 1 && contactId != -1 && !TextUtils.isEmpty(lookupKey)) {
            // If we only found one item, jump right to viewing it
            final Uri contactUri = Contacts.getLookupUri(contactId, lookupKey);
            final Intent viewIntent = new Intent(Intent.ACTION_VIEW, contactUri);
            ImplicitIntentsUtil.startActivityInApp(this, viewIntent);
            finish();

        } else if (count > 1) {
            // If more than one, show pick list
            Intent listIntent = new Intent(Intent.ACTION_SEARCH);
            listIntent.setComponent(new ComponentName(this, PeopleActivity.class));
            listIntent.putExtras(mCreateExtras);
            startActivity(listIntent);
            finish();

        } else {
            // No matching contacts found
            if (mCreateForce) {
                // Forced to create new contact
                Intent createIntent = new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI);
                createIntent.putExtras(mCreateExtras);
                createIntent.setType(RawContacts.CONTENT_TYPE);

                ImplicitIntentsUtil.startActivityInApp(this, createIntent);
                finish();

            } else {
                showDialog(CREATE_CONTACT_DIALOG);
            }
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch(id) {
	    case CREATE_CONTACT_DIALOG:
                // Prompt user to insert or edit contact
                final Intent createIntent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                createIntent.putExtras(mCreateExtras);
                createIntent.setType(RawContacts.CONTENT_ITEM_TYPE);

                final CharSequence message = getResources().getString(
                        R.string.add_contact_dlg_message_fmt, mCreateDescrip);

                return new AlertDialog.Builder(this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok,
                                new IntentClickListener(this, createIntent))
                        .setNegativeButton(android.R.string.cancel,
                                new IntentClickListener(this, null))
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    finish(); // Close the activity.
                                }})
                        .create();
        }
	return super.onCreateDialog(id);
    }

    /**
     * Listener for {@link DialogInterface} that launches a given {@link Intent}
     * when clicked. When clicked, this also closes the parent using
     * {@link Activity#finish()}.
     */
    private static class IntentClickListener implements DialogInterface.OnClickListener {
        private Activity mParent;
        private Intent mIntent;

        /**
         * @param parent {@link Activity} to use for launching target.
         * @param intent Target {@link Intent} to launch when clicked.
         */
        public IntentClickListener(Activity parent, Intent intent) {
            mParent = parent;
            mIntent = intent;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (mIntent != null) {
                ImplicitIntentsUtil.startActivityInApp(mParent, mIntent);
            }
            mParent.finish();
        }
    }
}
