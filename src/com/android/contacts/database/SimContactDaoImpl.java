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
package com.android.contacts.database;

import android.annotation.TargetApi;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.SimRecords;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.SparseArray;

import androidx.collection.ArrayMap;

import com.android.contacts.R;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.PermissionsUtil;
import com.android.contacts.util.SharedPreferenceUtil;

import com.google.common.base.Joiner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides data access methods for loading contacts from a SIM card and and migrating these
 * SIM contacts to a CP2 account.
 */
public class SimContactDaoImpl extends SimContactDao {
    private static final String TAG = "SimContactDao";

    // Maximum number of SIM contacts to import in a single ContentResolver.applyBatch call.
    // This is necessary to avoid TransactionTooLargeException when there are a large number of
    // contacts. This has been tested on Nexus 6 NME70B and is probably be conservative enough
    // to work on any phone.
    private static final int IMPORT_MAX_BATCH_SIZE = 300;

    // How many SIM contacts to consider in a single query. This prevents hitting the SQLite
    // query parameter limit.
    static final int QUERY_MAX_BATCH_SIZE = 100;

    private final Context mContext;
    private final ContentResolver mResolver;
    private final TelephonyManager mTelephonyManager;

    public SimContactDaoImpl(Context context) {
        this(context, context.getContentResolver(),
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
    }

    public SimContactDaoImpl(Context context, ContentResolver resolver,
            TelephonyManager telephonyManager) {
        mContext = context;
        mResolver = resolver;
        mTelephonyManager = telephonyManager;
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    public boolean canReadSimContacts() {
        // Require SIM_STATE_READY because the TelephonyManager methods related to SIM require
        // this state
        return hasTelephony() && hasPermissions() &&
                mTelephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    @Override
    public List<SimCard> getSimCards() {
        if (!canReadSimContacts()) {
            return Collections.emptyList();
        }
        final List<SimCard> sims = CompatUtils.isMSIMCompatible() ?
                getSimCardsFromSubscriptions() :
                Collections.singletonList(SimCard.create(mTelephonyManager,
                        mContext.getString(R.string.single_sim_display_label)));
        return SharedPreferenceUtil.restoreSimStates(mContext, sims);
    }

    @Override
    public ArrayList<SimContact> loadContactsForSim(SimCard sim) {
        if (sim.hasValidSubscriptionId()) {
            return loadSimContacts(sim.getSubscriptionId());
        }
        // Return an empty list.
        return new ArrayList<>(0);
    }

    public ArrayList<SimContact> loadSimContacts(int subscriptionId) {
        return loadFrom(
                SimRecords.getContentUri(
                        subscriptionId, SimPhonebookContract.ElementaryFiles.EF_ADN));
    }

    @Override
    public ContentProviderResult[] importContacts(List<SimContact> contacts,
            AccountWithDataSet targetAccount)
            throws RemoteException, OperationApplicationException {
        if (contacts.size() < IMPORT_MAX_BATCH_SIZE) {
            return importBatch(contacts, targetAccount);
        }
        final List<ContentProviderResult> results = new ArrayList<>();
        for (int i = 0; i < contacts.size(); i += IMPORT_MAX_BATCH_SIZE) {
            results.addAll(Arrays.asList(importBatch(
                    contacts.subList(i, Math.min(contacts.size(), i + IMPORT_MAX_BATCH_SIZE)),
                    targetAccount)));
        }
        return results.toArray(new ContentProviderResult[results.size()]);
    }

    public void persistSimState(SimCard sim) {
        SharedPreferenceUtil.persistSimStates(mContext, Collections.singletonList(sim));
    }

    @Override
    public void persistSimStates(List<SimCard> simCards) {
        SharedPreferenceUtil.persistSimStates(mContext, simCards);
    }

    @Override
    public SimCard getSimBySubscriptionId(int subscriptionId) {
        final List<SimCard> sims = SharedPreferenceUtil.restoreSimStates(mContext, getSimCards());
        if (subscriptionId == SimCard.NO_SUBSCRIPTION_ID && !sims.isEmpty()) {
            return sims.get(0);
        }
        for (SimCard sim : getSimCards()) {
            if (sim.getSubscriptionId() == subscriptionId) {
                return sim;
            }
        }
        return null;
    }

    /**
     * Finds SIM contacts that exist in CP2 and associates the account of the CP2 contact with
     * the SIM contact
     */
    public Map<AccountWithDataSet, Set<SimContact>> findAccountsOfExistingSimContacts(
            List<SimContact> contacts) {
        final Map<AccountWithDataSet, Set<SimContact>> result = new ArrayMap<>();
        for (int i = 0; i < contacts.size(); i += QUERY_MAX_BATCH_SIZE) {
            findAccountsOfExistingSimContacts(
                    contacts.subList(i, Math.min(contacts.size(), i + QUERY_MAX_BATCH_SIZE)),
                    result);
        }
        return result;
    }

    private void findAccountsOfExistingSimContacts(List<SimContact> contacts,
            Map<AccountWithDataSet, Set<SimContact>> result) {
        final Map<Long, List<SimContact>> rawContactToSimContact = new HashMap<>();
        Collections.sort(contacts, SimContact.compareByPhoneThenName());

        final Cursor dataCursor = queryRawContactsForSimContacts(contacts);

        try {
            while (dataCursor.moveToNext()) {
                final String number = DataQuery.getPhoneNumber(dataCursor);
                final String name = DataQuery.getDisplayName(dataCursor);

                final int index = SimContact.findByPhoneAndName(contacts, number, name);
                if (index < 0) {
                    continue;
                }
                final SimContact contact = contacts.get(index);
                final long id = DataQuery.getRawContactId(dataCursor);
                if (!rawContactToSimContact.containsKey(id)) {
                    rawContactToSimContact.put(id, new ArrayList<SimContact>());
                }
                rawContactToSimContact.get(id).add(contact);
            }
        } finally {
            dataCursor.close();
        }

        final Cursor accountsCursor = queryAccountsOfRawContacts(rawContactToSimContact.keySet());
        try {
            while (accountsCursor.moveToNext()) {
                final AccountWithDataSet account = AccountQuery.getAccount(accountsCursor);
                final long id = AccountQuery.getId(accountsCursor);
                if (!result.containsKey(account)) {
                    result.put(account, new HashSet<SimContact>());
                }
                for (SimContact contact : rawContactToSimContact.get(id)) {
                    result.get(account).add(contact);
                }
            }
        } finally {
            accountsCursor.close();
        }
    }


    private ContentProviderResult[] importBatch(List<SimContact> contacts,
            AccountWithDataSet targetAccount)
            throws RemoteException, OperationApplicationException {
        final ArrayList<ContentProviderOperation> ops =
                createImportOperations(contacts, targetAccount);
        return mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    private List<SimCard> getSimCardsFromSubscriptions() {
        final SubscriptionManager subscriptionManager = (SubscriptionManager)
                mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
        final List<SubscriptionInfo> subscriptions = subscriptionManager
                .getActiveSubscriptionInfoList();
        final ArrayList<SimCard> result = new ArrayList<>();
        for (SubscriptionInfo subscriptionInfo : subscriptions) {
            result.add(SimCard.create(subscriptionInfo));
        }
        return result;
    }

    private List<SimContact> getContactsForSim(SimCard sim) {
        final List<SimContact> contacts = sim.getContacts();
        return contacts != null ? contacts : loadContactsForSim(sim);
    }

    // See b/32831092
    // Sometimes the SIM contacts provider seems to get stuck if read from multiple threads
    // concurrently. So we just have a global lock around it to prevent potential issues.
    private static final Object SIM_READ_LOCK = new Object();
    private ArrayList<SimContact> loadFrom(Uri uri) {
        synchronized (SIM_READ_LOCK) {
            final Cursor cursor = mResolver.query(uri,
                    new String[]{
                            SimRecords.RECORD_NUMBER,
                            SimRecords.NAME,
                            SimRecords.PHONE_NUMBER
                    }, null, null);
            if (cursor == null) {
                // Assume null means there are no SIM contacts.
                return new ArrayList<>(0);
            }

            try {
                return loadFromCursor(cursor);
            } finally {
                cursor.close();
            }
        }
    }

    private ArrayList<SimContact> loadFromCursor(Cursor cursor) {
        final int colRecordNumber = cursor.getColumnIndex(SimRecords.RECORD_NUMBER);
        final int colName = cursor.getColumnIndex(SimRecords.NAME);
        final int colNumber = cursor.getColumnIndex(SimRecords.PHONE_NUMBER);

        final ArrayList<SimContact> result = new ArrayList<>();

        while (cursor.moveToNext()) {
            final int recordNumber = cursor.getInt(colRecordNumber);
            final String name = cursor.getString(colName);
            final String number = cursor.getString(colNumber);

            final SimContact contact = new SimContact(recordNumber, name, number, null);
            // Only include contact if it has some useful data
            if (contact.hasName() || contact.hasPhone()) {
                result.add(contact);
            }
        }
        return result;
    }

    private Cursor queryRawContactsForSimContacts(List<SimContact> contacts) {
        final StringBuilder selectionBuilder = new StringBuilder();

        int phoneCount = 0;
        int nameCount = 0;
        for (SimContact contact : contacts) {
            if (contact.hasPhone()) {
                phoneCount++;
            } else if (contact.hasName()) {
                nameCount++;
            }
        }
        List<String> selectionArgs = new ArrayList<>(phoneCount + 1);

        selectionBuilder.append('(');
        selectionBuilder.append(Data.MIMETYPE).append("=? AND ");
        selectionArgs.add(Phone.CONTENT_ITEM_TYPE);

        selectionBuilder.append(Phone.NUMBER).append(" IN (")
                .append(Joiner.on(',').join(Collections.nCopies(phoneCount, '?')))
                .append(')');
        for (SimContact contact : contacts) {
            if (contact.hasPhone()) {
                selectionArgs.add(contact.getPhone());
            }
        }
        selectionBuilder.append(')');

        if (nameCount > 0) {
            selectionBuilder.append(" OR (");

            selectionBuilder.append(Data.MIMETYPE).append("=? AND ");
            selectionArgs.add(StructuredName.CONTENT_ITEM_TYPE);

            selectionBuilder.append(Data.DISPLAY_NAME).append(" IN (")
                    .append(Joiner.on(',').join(Collections.nCopies(nameCount, '?')))
                    .append(')');
            for (SimContact contact : contacts) {
                if (!contact.hasPhone() && contact.hasName()) {
                    selectionArgs.add(contact.getName());
                }
            }
            selectionBuilder.append(')');
        }

        return mResolver.query(Data.CONTENT_URI.buildUpon()
                        .appendQueryParameter(Data.VISIBLE_CONTACTS_ONLY, "true")
                        .build(),
                DataQuery.PROJECTION,
                selectionBuilder.toString(),
                selectionArgs.toArray(new String[selectionArgs.size()]),
                null);
    }

    private Cursor queryAccountsOfRawContacts(Set<Long> ids) {
        final StringBuilder selectionBuilder = new StringBuilder();

        final String[] args = new String[ids.size()];

        selectionBuilder.append(RawContacts._ID).append(" IN (")
                .append(Joiner.on(',').join(Collections.nCopies(args.length, '?')))
                .append(")");
        int i = 0;
        for (long id : ids) {
            args[i++] = String.valueOf(id);
        }
        return mResolver.query(RawContacts.CONTENT_URI,
                AccountQuery.PROJECTION,
                selectionBuilder.toString(),
                args,
                null);
    }

    private ArrayList<ContentProviderOperation> createImportOperations(List<SimContact> contacts,
            AccountWithDataSet targetAccount) {
        final ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (SimContact contact : contacts) {
            contact.appendCreateContactOperations(ops, targetAccount);
        }
        return ops;
    }

    private boolean hasTelephony() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private boolean hasPermissions() {
        return PermissionsUtil.hasContactsPermissions(mContext) &&
                PermissionsUtil.hasPhonePermissions(mContext);
    }

    // TODO remove this class and the USE_FAKE_INSTANCE flag once this code is not under
    // active development or anytime after 3/1/2017
    public static class DebugImpl extends SimContactDaoImpl {

        private List<SimCard> mSimCards = new ArrayList<>();
        private SparseArray<SimCard> mCardsBySubscription = new SparseArray<>();

        public DebugImpl(Context context) {
            super(context);
        }

        public DebugImpl addSimCard(SimCard sim) {
            mSimCards.add(sim);
            mCardsBySubscription.put(sim.getSubscriptionId(), sim);
            return this;
        }

        @Override
        public List<SimCard> getSimCards() {
            return SharedPreferenceUtil.restoreSimStates(getContext(), mSimCards);
        }

        @Override
        public ArrayList<SimContact> loadContactsForSim(SimCard card) {
            return new ArrayList<>(card.getContacts());
        }

        @Override
        public boolean canReadSimContacts() {
            return true;
        }
    }

    // Query used for detecting existing contacts that may match a SimContact.
    private static final class DataQuery {

        public static final String[] PROJECTION = new String[] {
                Data.RAW_CONTACT_ID, Phone.NUMBER, Data.DISPLAY_NAME, Data.MIMETYPE
        };

        public static final int RAW_CONTACT_ID = 0;
        public static final int PHONE_NUMBER = 1;
        public static final int DISPLAY_NAME = 2;
        public static final int MIMETYPE = 3;

        public static long getRawContactId(Cursor cursor) {
            return cursor.getLong(RAW_CONTACT_ID);
        }

        public static String getPhoneNumber(Cursor cursor) {
            return isPhoneNumber(cursor) ? cursor.getString(PHONE_NUMBER) : null;
        }

        public static String getDisplayName(Cursor cursor) {
            return cursor.getString(DISPLAY_NAME);
        }

        public static boolean isPhoneNumber(Cursor cursor) {
            return Phone.CONTENT_ITEM_TYPE.equals(cursor.getString(MIMETYPE));
        }
    }

    private static final class AccountQuery {
        public static final String[] PROJECTION = new String[] {
                RawContacts._ID, RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE,
                RawContacts.DATA_SET
        };

        public static long getId(Cursor cursor) {
            return cursor.getLong(0);
        }

        public static AccountWithDataSet getAccount(Cursor cursor) {
            return new AccountWithDataSet(cursor.getString(1), cursor.getString(2),
                    cursor.getString(3));
        }
    }
}
