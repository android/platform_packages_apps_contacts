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
import android.telephony.TelephonyManager;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.contacts.database.SimContactDao;
import com.android.contacts.database.SimContactDaoImpl;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;

import java.util.ArrayList;
import java.util.List;

public class SimContactsTestHelper {

    private final Context mContext;
    private final TelephonyManager mTelephonyManager;
    private final ContentResolver mResolver;
    private final SimContactDao mSimDao;

    public SimContactsTestHelper() {
        this(InstrumentationRegistry.getTargetContext());
    }

    public SimContactsTestHelper(Context context) {
        mContext = context;
        mResolver = context.getContentResolver();
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mSimDao = SimContactDao.create(context);
    }

    public int getSimContactCount() {
        Cursor cursor = mContext.getContentResolver().query(SimContactDaoImpl.ICC_CONTENT_URI,
                null, null, null, null);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    public Uri addSimContact(String name, String number) {
        ContentValues values = new ContentValues();
        // Oddly even though it's called name when querying we have to use "tag" for it to work
        // when inserting.
        if (name != null) {
            values.put("tag", name);
        }
        if (number != null) {
            values.put(SimContactDaoImpl.NUMBER, number);
        }
        return mResolver.insert(SimContactDaoImpl.ICC_CONTENT_URI, values);
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
                    .newDelete(SimContactDaoImpl.ICC_CONTENT_URI)
                    .withSelection(getWriteSelection(contact), null)
                    .build());
        }
        return mResolver.applyBatch(SimContactDaoImpl.ICC_CONTENT_URI.getAuthority(), ops);
    }

    public ContentProviderResult[] restore(ArrayList<ContentProviderOperation> restoreOps)
            throws RemoteException, OperationApplicationException {
        if (restoreOps == null) return null;

        // Remove SIM contacts because we assume that caller wants the data to be in the exact
        // state as when the restore ops were captured.
        deleteAllSimContacts();
        return mResolver.applyBatch(SimContactDaoImpl.ICC_CONTENT_URI.getAuthority(), restoreOps);
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
                    .newInsert(SimContactDaoImpl.ICC_CONTENT_URI)
                    .withValue("tag", contact.getName())
                    .withValue("number", contact.getPhone())
                    .build());
        }
        return ops;
    }

    public String getWriteSelection(SimContact simContact) {
        return "tag='" + simContact.getName() + "' AND " + SimContactDaoImpl.NUMBER + "='" +
                simContact.getPhone() + "'";
    }

    public int deleteSimContact(@NonNull  String name, @NonNull  String number) {
        // IccProvider doesn't use the selection args.
        final String selection = "tag='" + name + "' AND " +
                SimContactDaoImpl.NUMBER + "='" + number + "'";
        return mResolver.delete(SimContactDaoImpl.ICC_CONTENT_URI, selection, null);
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
        return uri != null && deleteSimContact(name, "15095550101") == 1;
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
