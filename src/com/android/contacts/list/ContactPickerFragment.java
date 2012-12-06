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
package com.android.contacts.list;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.android.contacts.R;
import com.android.contacts.common.list.ContactEntryListAdapter;
import com.android.contacts.common.list.ContactEntryListFragment;
import com.android.contacts.common.list.ContactListAdapter;
import com.android.contacts.common.list.ContactListFilter;
import com.android.contacts.common.list.DefaultContactListAdapter;
import com.android.contacts.common.list.DirectoryListLoader;
import com.android.contacts.common.list.ShortcutIntentBuilder;
import com.android.contacts.common.list.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;
import android.app.Activity;
import com.android.contacts.common.BrcmIccUtils;
import com.android.contacts.common.SimContactsReadyHelper;
import com.android.internal.telephony.RILConstants.SimCardID;
import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.content.Context;
import android.app.Activity;
import android.content.ContentUris;
import android.widget.Toast;
import android.text.TextUtils;
import java.util.HashMap;
import java.util.Iterator;

/**
 * Fragment for the contact list used for browsing contacts (as compared to
 * picking a contact with one of the PICK or SHORTCUT intents).
 */
public class ContactPickerFragment extends ContactEntryListFragment<ContactEntryListAdapter>
        implements OnShortcutIntentCreatedListener {

    private static final String KEY_EDIT_MODE = "editMode";
    private static final String KEY_CREATE_CONTACT_ENABLED = "createContactEnabled";
    private static final String KEY_SHORTCUT_REQUESTED = "shortcutRequested";

    private OnContactPickerActionListener mListener;
    private boolean mCreateContactEnabled;
    private boolean mEditMode;
    private boolean mShortcutRequested;
    private int mSimIdToExport = -1;
    private boolean mIsExportToSim = false;
    private HashMap<Long, Boolean> mSelectedContacts;
    private ProgressDialog mProgressDialog;
    private int mTotalSelected=0;

    public ContactPickerFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setVisibleScrollbarEnabled(true);
        setQuickContactEnabled(false);
        setDirectorySearchMode(DirectoryListLoader.SEARCH_MODE_CONTACT_SHORTCUT);
        setSimIdToExport(-1);
        mSelectedContacts = new HashMap<Long, Boolean>();
    }

    public void setOnContactPickerActionListener(OnContactPickerActionListener listener) {
        mListener = listener;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setEditMode(boolean flag) {
        mEditMode = flag;
    }

    public void setShortcutRequested(boolean flag) {
        mShortcutRequested = flag;
    }

    public int getSimIdToExport() {
        return mSimIdToExport;
    }

    public void setSimIdToExport(int simId) {
        mSimIdToExport = simId;
    }

    private boolean isExportToSim() {
        return mIsExportToSim;
    }

    public void setExportToSimFlag(boolean isExportToSim) {
        mIsExportToSim = isExportToSim;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_EDIT_MODE, mEditMode);
        outState.putBoolean(KEY_CREATE_CONTACT_ENABLED, mCreateContactEnabled);
        outState.putBoolean(KEY_SHORTCUT_REQUESTED, mShortcutRequested);
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mEditMode = savedState.getBoolean(KEY_EDIT_MODE);
        mCreateContactEnabled = savedState.getBoolean(KEY_CREATE_CONTACT_ENABLED);
        mShortcutRequested = savedState.getBoolean(KEY_SHORTCUT_REQUESTED);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);
        if (mCreateContactEnabled) {
            getListView().addHeaderView(inflater.inflate(R.layout.create_new_contact, null, false));
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (position == 0 && mCreateContactEnabled) {
            mListener.onCreateNewContactAction();
        } else {
            super.onItemClick(parent, view, position, id);
        }
    }

    @Override
    protected void onItemClick(int position, long id) {
        Uri uri;
        if (isLegacyCompatibilityMode()) {
            uri = ((LegacyContactListAdapter)getAdapter()).getPersonUri(position);
        } else {
            uri = ((ContactListAdapter)getAdapter()).getContactUri(position);
        }
        if (mEditMode) {
            editContact(uri);
        } else  if (mShortcutRequested) {
            ShortcutIntentBuilder builder = new ShortcutIntentBuilder(getActivity(), this);
            builder.createContactShortcutIntent(uri);
        } else if (-1 != mSimIdToExport && isExportToSim()) {
            /* export picked contact to sim */
            mSelectedContacts.clear();
            long contactId = ContentUris.parseId(uri);
            mSelectedContacts.put(contactId, true);
            exportSelectedContacts();
        } else {
            pickContact(uri);
        }
    }

    public void createNewContact() {
        mListener.onCreateNewContactAction();
    }

    public void editContact(Uri contactUri) {
        mListener.onEditContactAction(contactUri);
    }

    public void pickContact(Uri uri) {
        mListener.onPickContactAction(uri);
    }

    @Override
    protected ContactEntryListAdapter createListAdapter() {
        if (!isLegacyCompatibilityMode()) {
            DefaultContactListAdapter adapter = new DefaultContactListAdapter(getActivity());
            if (-1 != mSimIdToExport) {
                adapter.setFilter(ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_NOT_SIM_CONTACTS));
            } else {
                adapter.setFilter(ContactListFilter.createFilterWithType(
                        ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
            }
            adapter.setSectionHeaderDisplayEnabled(true);
            adapter.setDisplayPhotos(true);
            adapter.setQuickContactEnabled(false);
            return adapter;
        } else {
            LegacyContactListAdapter adapter = new LegacyContactListAdapter(getActivity());
            adapter.setSectionHeaderDisplayEnabled(false);
            adapter.setDisplayPhotos(false);
            return adapter;
        }
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        ContactEntryListAdapter adapter = getAdapter();

        // If "Create new contact" is shown, don't display the empty list UI
        adapter.setEmptyListEnabled(!isCreateContactEnabled());
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contact_picker_content, null);
    }

    @Override
    public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
        mListener.onShortcutIntentCreated(shortcutIntent);
    }

    @Override
    public void onPickerResult(Intent data) {
        mListener.onPickContactAction(data.getData());
    }

    public void exportAllContacts() {
        mSelectedContacts.clear();

        ContactListAdapter adapter = (ContactListAdapter) getAdapter();
        int count = adapter.getCount();
        int headerViewCount = getListView().getHeaderViewsCount();
        Uri uri;
        long contactId;

        SimContactsReadyHelper simReadyHelper;
        simReadyHelper = new SimContactsReadyHelper(null, false);

        if(simReadyHelper.getFreeADNCount(mSimIdToExport)<count){
            Toast.makeText(getActivity(), R.string.adnNoSimSpaceForExportAll,Toast.LENGTH_LONG).show();
            return;
        }

        for (int position=headerViewCount; position<count; position++) {
            uri = adapter.getContactUri(position);
            if(uri!=null) {
                contactId = ContentUris.parseId(uri);
                mSelectedContacts.put(contactId, true);
            }
        }

        exportSelectedContacts();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mProgressDialog!=null) {
            mProgressDialog.dismiss();
            mProgressDialog=null;
        }
    }


    private void exportSelectedContacts() {

        mTotalSelected = mSelectedContacts.size();
        if (0 == mTotalSelected) {
            if(getActivity()!=null) {
                getActivity().setResult(Activity.RESULT_OK, null);
                getActivity().finish();
            }
            return;
        }

        //final ProgressDialog
        if(mTotalSelected>1) {
            mProgressDialog = ProgressDialog.show(getActivity(),getString(R.string.export_to_sim),"", true, false);
        }else {
            mProgressDialog=null;
        }

        final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            long contactId;
        //ArrayList<ContactUsimInfo> contacts = new ArrayList<ContactUsimInfo>();
        String[] tags = new String[mTotalSelected];
        String[] numbers = new String[mTotalSelected];
        String[] emails = new String[mTotalSelected];
        String[] anrs = new String[mTotalSelected];
        int exportCount = 0;
            @Override
            protected Void doInBackground(Void... params) {
                    for (Iterator iter = mSelectedContacts.keySet().iterator(); iter.hasNext();) {
                        contactId = (Long) iter.next();

                        if (getActivity()!=null && BrcmIccUtils.getContactInfoForExportToSim(getActivity(), contactId, exportCount,mSimIdToExport, tags, numbers, emails, anrs)) {
                            exportCount++;
                        }

                    }

                    if (exportCount > 0) {
                        String[] tagsToExport = new String[exportCount];
                        String[] numbersToExport = new String[exportCount];
                        String[] emailsToExport = new String[exportCount];
                        String[] anrsToExport = new String[exportCount];

                        System.arraycopy(tags, 0, tagsToExport, 0, exportCount);
                        System.arraycopy(numbers, 0, numbersToExport, 0, exportCount);
                        System.arraycopy(emails, 0, emailsToExport, 0, exportCount);
                        System.arraycopy(anrs, 0, anrsToExport, 0, exportCount);

                        Intent intent = new Intent(Intent.ACTION_EDIT);
                        intent.setClassName("com.android.contacts", "com.android.contacts.AdnContactActionScreen");
                        intent.putExtra(BrcmIccUtils.SIM_ACTION, 1);
                        intent.putExtra(BrcmIccUtils.INTENT_EXTRA_NAME, tagsToExport);
                        intent.putExtra(BrcmIccUtils.INTENT_EXTRA_NUMBER, numbersToExport);
                        intent.putExtra(BrcmIccUtils.INTENT_EXTRA_EMAILS, emailsToExport);
                        intent.putExtra(BrcmIccUtils.INTENT_EXTRA_ANRS, anrsToExport);

                        if (SimCardID.ID_ONE.toInt() == mSimIdToExport) {
                            intent.putExtra(BrcmIccUtils.INTENT_EXTRA_SIM_ID, SimCardID.ID_ONE);
                        } else if (SimCardID.ID_ZERO.toInt() == mSimIdToExport) {
                            intent.putExtra(BrcmIccUtils.INTENT_EXTRA_SIM_ID, SimCardID.ID_ZERO);
                        }

                        if(getActivity()!=null) {
                            startActivity(intent);
                        }
                    } else {
                         if(getActivity()!=null) {
                            getActivity().setResult(Activity.RESULT_CANCELED, null);
                         }
                    }

                    mSelectedContacts.clear();
                return null;
            }
            @Override
            protected void onPostExecute(Void result) {
                if(mProgressDialog!=null) mProgressDialog.dismiss();
            }
        };

        if(mProgressDialog!=null) mProgressDialog.setMessage(getString(R.string.exportToSimReadingContacts));

        if(mProgressDialog!=null) mProgressDialog.show();
        task.execute();

   }
}
