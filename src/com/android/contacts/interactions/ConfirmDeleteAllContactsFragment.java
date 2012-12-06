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
 * limitations under the License
 */


package com.android.contacts.interactions;

import com.android.contacts.R;


import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.res.Resources;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import com.android.contacts.common.BrcmIccUtils;
import com.android.contacts.ContactSaveService;
import android.widget.Toast;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;

import java.util.ArrayList;

/**
 * Dialog that clears the call log after confirming with the user
 */
public class ConfirmDeleteAllContactsFragment extends DialogFragment {
    private static Context mContext;
    private static String mAccountType;
    private static String mAccountName;
    private static boolean mDeleting=false;
    private ProgressDialog mProgressDialog;
    private long[] mContactIds;
    /** Preferred way to show this dialog */
    public static void show(Context context,FragmentManager fragmentManager,String accountType, String accountName) {
        if(mDeleting==false) {
            ConfirmDeleteAllContactsFragment dialog = new ConfirmDeleteAllContactsFragment();
            dialog.show(fragmentManager, "ConfirmDeleteAllContactsFragment");
            mContext=context;
            mAccountType=accountType;
            mAccountName=accountName;
            mDeleting=true;
        }else {
            Toast.makeText(mContext, R.string.deletingProcessIsRunning,Toast.LENGTH_LONG).show();
        }
    }

    private void removeMembersFromGroup(ContentResolver resolver, long[] rawContactsToRemove,
            long groupId) {
        if (rawContactsToRemove == null) {
            return;
        }
        for (long rawContactId : rawContactsToRemove) {
            mContext.getContentResolver().delete(Data.CONTENT_URI, Data.RAW_CONTACT_ID + "=? AND " +
                    Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                    new String[] { String.valueOf(rawContactId),
                    GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId)});
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final ContentResolver resolver = getActivity().getContentResolver();
        final OnClickListener okListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                mContactIds=BrcmIccUtils.getContactIdsByAccount(mContext.getContentResolver(),mAccountType,mAccountName);
                final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {

                        if(mContactIds!=null) {
                            for(long contactId:mContactIds) {
                               final Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                               final ContentResolver resolver = mContext.getContentResolver();
                               final long rawContactId = BrcmIccUtils.getRawContactId(resolver, contactId);

                                ArrayList<Long> groupIds = BrcmIccUtils.getGroupIdsFromRawContactId(resolver, rawContactId);
                                final long[] rawContactIdAry = new long[]{rawContactId};
                                if (BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(mAccountType)) {
                                    BrcmIccUtils.deleteIccContact(resolver, contactId);
                                    for (Long groupId : groupIds) {
                                        removeMembersFromGroup(resolver, rawContactIdAry, groupId);
                                    }
                                } else if (BrcmIccUtils.ACCOUNT_TYPE_LOCAL.equals(mAccountType)) {
                                    for (Long groupId : groupIds) {
                                        removeMembersFromGroup(resolver, rawContactIdAry, groupId);
                                    }
                                }
                                mContext.getContentResolver().delete(contactUri, null, null);
                               if (mProgressDialog!=null) mProgressDialog.incrementProgressBy(1);
                            }
                        }
                        return null;
                    }
                    @Override
                    protected void onPostExecute(Void result) {
                        if(mProgressDialog!=null) mProgressDialog.dismiss();
                        mDeleting=false;
                    }
                };

                if(mContactIds!=null) {
                    mProgressDialog = new ProgressDialog(getActivity());
                    mProgressDialog.setTitle(getString(R.string.deleteAllContactsProgress_title));
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialog.setOnCancelListener(null);
                    mProgressDialog.setCancelable(false);
                    mProgressDialog.show();
                    mProgressDialog.setMax(mContactIds.length);
                    mProgressDialog.setProgress(0);
                }
                task.execute();
            }
        };

        return new AlertDialog.Builder(getActivity())
            .setTitle(mContext.getResources().getString(R.string.deleteAllContactsConfirmation_title) +" in " +mAccountName + "?")
            .setIconAttribute(android.R.attr.alertDialogIcon)
            .setMessage(mContext.getResources().getString(R.string.deleteAllContactsConfirmation)+ " in " +mAccountName)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, okListener)
            .setCancelable(true)
            .create();
    }
}
