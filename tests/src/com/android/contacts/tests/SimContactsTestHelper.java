/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.contacts.tests;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.contacts.database.SimContactDao;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;

import java.util.ArrayList;
import java.util.List;

public class SimContactsTestHelper {

    private final Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final ContentResolver mResolver;
    private final SimContactDao mSimDao;
    private final int mSubscriptionId;
    private final Uri mDefaultSimAdnUri;

    public SimContactsTestHelper() {
        this(InstrumentationRegistry.getTargetContext());
    }

    public SimContactsTestHelper(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mSimDao = SimContactDao.create(context);
        mSubscriptionId = mTelephonyManager.getSubscriptionId();
        mDefaultSimAdnUri = SimRecords.getContentUri(
                mTelephonyManager.getSubscriptionId(), ElementaryFiles.EF_ADN);
    }

    public int getSimContactCount() {
        Cursor cursor = mContext.getContentResolver().query(mDefaultSimAdnUri,
                null, null, null, null);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    public Uri addSimContact(String name, String number) {
        ContentValues values = new ContentValues();
        if (name != null) {
            values.put(SimRecords.NAME, name);
        }
        if (number != null) {
            values.put(SimRecords.PHONE_NUMBER, number);
        }
        return mResolver.insert(mDefaultSimAdnUri, values);
    }

    public ContentProviderResult[] deleteAllSimContacts()
            throws RemoteException, OperationApplicationException {
        final List<SimCard> sims = mSimDao.getSimCards();
        if (sims.isEmpty()) {
            throw new IllegalStateException("Expected SIM card");
        }
        final List<SimContact> contacts = mSimDao.loadContactsForSim(sims.get(0));
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (SimContact contact : contacts) {
            ops.add(ContentProviderOperation
                    .newDelete(SimRecords.getItemUri(
                            mSubscriptionId, ElementaryFiles.EF_ADN, contact.getRecordNumber()))
                    .build());
        }
        return mResolver.applyBatch(SimPhonebookContract.AUTHORITY, ops);
    }

    public ContentProviderResult[] restore(ArrayList<ContentProviderOperation> restoreOps)
            throws RemoteException, OperationApplicationException {
        if (restoreOps == null) return null;

        // Remove SIM contacts because we assume that caller wants the data to be in the exact
        // state as when the restore ops were captured.
        deleteAllSimContacts();
        return mResolver.applyBatch(SimPhonebookContract.AUTHORITY, restoreOps);
    }

    public ArrayList<ContentProviderOperation> captureRestoreSnapshot() {
        final List<SimCard> sims = mSimDao.getSimCards();
        if (sims.isEmpty()) {
            throw new IllegalStateException("Expected SIM card");
        }
        final ArrayList<SimContact> contacts = mSimDao.loadContactsForSim(sims.get(0));

        final ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (SimContact contact : contacts) {
            final String[] emails = contact.getEmails();
            if (emails != null && emails.length > 0) {
                throw new IllegalStateException("Cannot restore emails." +
                        " Please manually remove SIM contacts with emails.");
            }
            ops.add(ContentProviderOperation
                    .newInsert(mDefaultSimAdnUri)
                    .withValue(SimRecords.NAME, contact.getName())
                    .withValue(SimRecords.PHONE_NUMBER, contact.getPhone())
                    .build());
        }
        return ops;
    }

    public int deleteSimContact(@NonNull Uri recordUri) {
        return mResolver.delete(recordUri, null);
    }

    public boolean isSimReady() {
        return mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    public boolean doesSimHaveContacts() {
        return isSimReady() && getSimContactCount() > 0;
    }

    public boolean isSimWritable() {
        if (!isSimReady()) return false;
        final String name = "writabeProbe" + System.nanoTime();
        final Uri uri = addSimContact(name, "15095550101");
        return uri != null && deleteSimContact(uri) == 1;
    }

    public void assumeSimReady() {
        assumeTrue(isSimReady());
    }

    public void assumeHasSimContacts() {
        assumeTrue(doesSimHaveContacts());
    }

    public void assumeSimCardAbsent() {
        assumeThat(mTelephonyManager.getSimState(), equalTo(TelephonyManager.SIM_STATE_ABSENT));
    }

    // The emulator reports SIM_STATE_READY but writes are ignored. This verifies that the
    // device will actually persist writes to the SIM card.
    public void assumeSimWritable() {
        assumeSimReady();
        assumeTrue(isSimWritable());
    }
}
