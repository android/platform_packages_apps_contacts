/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.interactions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.ContactDisplayUtils;

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.TreeSet;

/**
 * An interaction invoked to delete multiple contacts.
 *
 * This class is very similar to {@link ContactDeletionInteraction}.
 */
public class ContactMultiDeletionInteraction extends Fragment
        implements LoaderCallbacks<Cursor> {

    public interface MultiContactDeleteListener {
        void onDeletionFinished();
    }

    private static final String FRAGMENT_TAG = "deleteMultipleContacts";
    private static final String TAG = "ContactMultiDeletion";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACTS_IDS = "contactIds";
    public static final String ARG_CONTACT_IDS = "contactIds";

    private static final String[] RAW_CONTACT_PROJECTION = new String[] {
            RawContacts._ID,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            RawContacts.CONTACT_ID,
            RawContacts.DISPLAY_NAME_PRIMARY,
            RawContacts.DISPLAY_NAME_ALTERNATIVE
    };

    private static final int COLUMN_INDEX_RAW_CONTACT_ID = 0;
    private static final int COLUMN_INDEX_ACCOUNT_TYPE = 1;
    private static final int COLUMN_INDEX_DATA_SET = 2;
    private static final int COLUMN_INDEX_CONTACT_ID = 3;
    private static final int COLUMN_INDEX_DISPLAY_NAME = 4;
    private static final int COLUMN_INDEX_DISPLAY_NAME_ALT = 5;

    private boolean mIsLoaderActive;
    private TreeSet<Long> mContactIds;
    private Context mContext;
    private AlertDialog mDialog;
    private MultiContactDeleteListener mListener;

    /**
     * Starts the interaction.
     *
     * @param hostFragment the fragment within which to start the interaction
     * @param contactIds the IDs of contacts to be deleted
     * @return the newly created interaction
     */
    public static ContactMultiDeletionInteraction start(
            Fragment hostFragment, TreeSet<Long> contactIds) {
        if (contactIds == null) {
            return null;
        }

        final FragmentManager fragmentManager = hostFragment.getFragmentManager();
        ContactMultiDeletionInteraction fragment =
                (ContactMultiDeletionInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ContactMultiDeletionInteraction();
            fragment.setContactIds(contactIds);
            fragmentManager.beginTransaction().add(fragment, FRAGMENT_TAG)
                    .commitAllowingStateLoss();
        } else {
            fragment.setContactIds(contactIds);
        }
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mDialog != null && mDialog.isShowing()) {
            mDialog.setOnDismissListener(null);
            mDialog.dismiss();
            mDialog = null;
        }
    }

    public void setContactIds(TreeSet<Long> contactIds) {
        mContactIds = contactIds;
        mIsLoaderActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putSerializable(ARG_CONTACT_IDS, mContactIds);
            getLoaderManager().restartLoader(R.id.dialog_delete_multiple_contact_loader_id,
                    args, this);
        }
    }

    private boolean isStarted() {
        return isAdded();
    }

    @Override
    public void onStart() {
        if (mIsLoaderActive) {
            Bundle args = new Bundle();
            args.putSerializable(ARG_CONTACT_IDS, mContactIds);
            getLoaderManager().initLoader(
                    R.id.dialog_delete_multiple_contact_loader_id, args, this);
        }
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDialog != null) {
            mDialog.hide();
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        final TreeSet<Long> contactIds = (TreeSet<Long>) args.getSerializable(ARG_CONTACT_IDS);
        final Object[] parameterObject = contactIds.toArray();
        final String[] parameters = new String[contactIds.size()];

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contactIds.size(); i++) {
            parameters[i] = String.valueOf(parameterObject[i]);
            builder.append(RawContacts.CONTACT_ID + " =?");
            if (i == contactIds.size() -1) {
                break;
            }
            builder.append(" OR ");
        }
        return new CursorLoader(mContext, RawContacts.CONTENT_URI, RAW_CONTACT_PROJECTION,
                builder.toString(), parameters, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }

        if (!mIsLoaderActive) {
            return;
        }

        if (cursor == null || cursor.isClosed()) {
            Log.e(TAG, "Failed to load contacts");
            return;
        }

        // This cursor may contain duplicate raw contacts, so we need to de-dupe them first
        final HashSet<Long> readOnlyRawContacts = Sets.newHashSet();
        final HashSet<Long> writableRawContacts = Sets.newHashSet();
        final HashSet<Long> contactIds = Sets.newHashSet();
        final HashSet<String> names = Sets.newHashSet();

        final ContactsPreferences contactsPreferences = new ContactsPreferences(mContext);

        AccountTypeManager accountTypes = AccountTypeManager.getInstance(getActivity());
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            final long rawContactId = cursor.getLong(COLUMN_INDEX_RAW_CONTACT_ID);
            final String accountType = cursor.getString(COLUMN_INDEX_ACCOUNT_TYPE);
            final String dataSet = cursor.getString(COLUMN_INDEX_DATA_SET);
            final long contactId = cursor.getLong(COLUMN_INDEX_CONTACT_ID);
            final String displayName = cursor.getString(COLUMN_INDEX_DISPLAY_NAME);
            final String displayNameAlt = cursor.getString(COLUMN_INDEX_DISPLAY_NAME_ALT);

            final String name = ContactDisplayUtils.getPreferredDisplayName(displayName,
                    displayNameAlt, contactsPreferences);
            if (!TextUtils.isEmpty(name)) {
                names.add(name);
            }

            contactIds.add(contactId);
            final AccountType type = accountTypes.getAccountType(accountType, dataSet);
            boolean writable = type == null || type.areContactsWritable();
            if (writable) {
                writableRawContacts.add(rawContactId);
            } else {
                readOnlyRawContacts.add(rawContactId);
            }
        }

        final int readOnlyCount = readOnlyRawContacts.size();
        final int writableCount = writableRawContacts.size();

        final int messageId;
        int positiveButtonId = android.R.string.ok;
        if (readOnlyCount > 0 && writableCount > 0) {
            messageId = R.string.batch_delete_multiple_accounts_confirmation;
        } else if (readOnlyCount > 0 && writableCount == 0) {
            messageId = R.string.batch_delete_read_only_contact_confirmation;
            positiveButtonId = R.string.readOnlyContactWarning_positive_button;
        } else if (writableCount == 1) {
            messageId = R.string.single_delete_confirmation;
            positiveButtonId = R.string.deleteConfirmation_positive_button;
        } else {
            messageId = R.string.batch_delete_confirmation;
            positiveButtonId = R.string.deleteConfirmation_positive_button;
        }

        // Convert set of contact ids into a format that is easily parcellable and iterated upon
        // for the sake of ContactSaveService.
        final Long[] contactIdObjectArray = contactIds.toArray(new Long[contactIds.size()]);
        final long[] contactIdArray = new long[contactIds.size()];
        for (int i = 0; i < contactIds.size(); i++) {
            contactIdArray[i] = contactIdObjectArray[i];
        }

        final String[] namesArray = names.toArray(new String[names.size()]);
        showDialog(messageId, positiveButtonId, contactIdArray, namesArray);

        // We don't want onLoadFinished() calls any more, which may come when the database is
        // updating.
        getLoaderManager().destroyLoader(R.id.dialog_delete_multiple_contact_loader_id);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showDialog(int messageId, int positiveButtonId, final long[] contactIds,
            final String[] namesArray) {
        mDialog = new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(messageId)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(positiveButtonId,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            doDeleteContact(contactIds, namesArray);
                        }
                    }
                )
                .create();

        mDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                mIsLoaderActive = false;
                mDialog = null;
            }
        });
        mDialog.show();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_ACTIVE, mIsLoaderActive);
        outState.putSerializable(KEY_CONTACTS_IDS, mContactIds);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            mIsLoaderActive = savedInstanceState.getBoolean(KEY_ACTIVE);
            mContactIds = (TreeSet<Long>) savedInstanceState.getSerializable(KEY_CONTACTS_IDS);
        }
    }

    protected void doDeleteContact(long[] contactIds, final String[] names) {
        mContext.startService(ContactSaveService.createDeleteMultipleContactsIntent(mContext,
                contactIds, names));
        mListener.onDeletionFinished();
    }

    public void setListener(MultiContactDeleteListener listener) {
        mListener = listener;
    }
}
