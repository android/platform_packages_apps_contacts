/*
 * Copyright (c) 2019, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of The Linux Foundation nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;

import java.util.List;
import java.util.TreeSet;
/**
 * An interaction invoked to share multiple contacts.
 */
public class ContactMultiShareInteraction extends Fragment
        implements LoaderCallbacks<String> {

    public static final int ACTIVITY_REQUEST_CODE_SHARE = 0;

    private static final String FRAGMENT_TAG = "shareMultipleContacts";
    private static final String TAG = "ContactMultiShare";
    private static final String KEY_ACTIVE = "active";
    private static final String KEY_CONTACTS_IDS = "contactIds";
    private static final String ARG_CONTACT_IDS = "contactIds";

    private boolean mIsLoaderActive;
    private TreeSet<Long> mContactIds;
    private Context mContext;
    private static ProgressDialog mProgressDialog;

    /**
     * Starts the interaction.
     *
     * @param hostFragment the fragment within which to start the interaction
     * @param contactIds the IDs of contacts to be shared
     * @return the newly created interaction
     */
    public static ContactMultiShareInteraction start(
            Fragment hostFragment, TreeSet<Long> contactIds) {
        if (contactIds == null) {
            return null;
        }

        final FragmentManager fragmentManager = hostFragment.getFragmentManager();
        ContactMultiShareInteraction fragment =
                (ContactMultiShareInteraction) fragmentManager.findFragmentByTag(FRAGMENT_TAG);
        if (fragment == null) {
            fragment = new ContactMultiShareInteraction();
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
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.setOnDismissListener(null);
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }

    public void setContactIds(TreeSet<Long> contactIds) {
        mContactIds = contactIds;
        mIsLoaderActive = true;
        if (isStarted()) {
            Bundle args = new Bundle();
            args.putSerializable(ARG_CONTACT_IDS, mContactIds);
            getLoaderManager().restartLoader(R.id.dialog_share_multiple_contact_loader_id,
                    args, this);
            showDialog();
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
                    R.id.dialog_share_multiple_contact_loader_id, args, this);
            showDialog();
        }
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
    }

    @Override
    public Loader<String> onCreateLoader(int id, Bundle args) {
        final TreeSet<Long> contactIds = (TreeSet<Long>) args.getSerializable(ARG_CONTACT_IDS);
        return new ShareContactsLoader(mContext, contactIds);
    }

    @Override
    public void onLoadFinished(Loader<String> loader, String uriList) {
        if (mProgressDialog != null){
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }

        if (!mIsLoaderActive) {
            return;
        }

        if (TextUtils.isEmpty(uriList)) {
            Log.e(TAG, "Failed to load contacts");
            return;
        }

        final Uri uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_MULTI_VCARD_URI,
                Uri.encode(uriList));
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(ContactsContract.Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        try {
            startActivityForResult(Intent.createChooser(intent, mContext.getResources().getQuantityString(
                    R.plurals.title_share_via,/* quantity */ mContactIds.size()))
                    , ACTIVITY_REQUEST_CODE_SHARE);
        } catch (final ActivityNotFoundException ex) {
            Toast.makeText(getContext(), R.string.share_error, Toast.LENGTH_SHORT).show();
        }

        // We don't want onLoadFinished() calls any more, which may come when the database is
        // updating.
        getLoaderManager().destroyLoader(R.id.dialog_share_multiple_contact_loader_id);
    }

    @Override
    public void onLoaderReset(Loader<String> loader) {
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

    private void cancelLoad() {
        Loader loader = getLoaderManager().getLoader(R.id.dialog_share_multiple_contact_loader_id);
        if (loader != null) {
            loader.cancelLoad();
        }
    }

    private void showDialog() {
        CharSequence title = getString(R.string.exporting_contact_list_title);

        mProgressDialog = new ProgressDialog(mContext);
        mProgressDialog.setTitle(title);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                getString(android.R.string.cancel), (OnClickListener)null);
        mProgressDialog.setProgress(0);
        mProgressDialog.setMax(mContactIds.size());
        mProgressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                cancelLoad();
                mIsLoaderActive = false;
                mProgressDialog = null;
            }
        });

        mProgressDialog.show();
    }

    private static class ShareContactsLoader extends AsyncTaskLoader<String>{
        private TreeSet<Long> mSelectedContactIds;

        public ShareContactsLoader(Context context, TreeSet<Long> contactIds) {
            super(context);
            mSelectedContactIds = contactIds;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }

        @Override
        public String loadInBackground() {
            final StringBuilder uriListBuilder = new StringBuilder();
            for (Long contactId : mSelectedContactIds) {
                if (!isLoadInBackgroundCanceled()) {
                    final Uri contactUri = ContentUris.withAppendedId(
                            ContactsContract.Contacts.CONTENT_URI, contactId);
                    final Uri lookupUri = ContactsContract.Contacts.getLookupUri(
                            getContext().getContentResolver(), contactUri);
                    if (lookupUri == null) {
                        continue;
                    }
                    final List<String> pathSegments = lookupUri.getPathSegments();
                    if (pathSegments.size() < 2) {
                        continue;
                    }
                    final String lookupKey = pathSegments.get(pathSegments.size() - 2);
                    if (uriListBuilder.length() > 0) {
                        uriListBuilder.append(':');
                    }
                    uriListBuilder.append(Uri.encode(lookupKey));
                    if (mProgressDialog != null) {
                        mProgressDialog.incrementProgressBy(1);
                    }
                }

            }
            return uriListBuilder.toString();
        }
    }
}