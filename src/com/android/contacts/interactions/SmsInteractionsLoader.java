/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.AsyncTaskLoader;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.provider.Telephony;
import android.util.Log;

import com.android.contacts.compat.TelephonyThreadsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads the most recent sms between the passed in phone numbers.
 *
 * This is a two part process. The first step is retrieving the threadIds for each of the phone
 * numbers using fuzzy matching. The next step is to run another query against these threadIds
 * to retrieve the actual sms.
 */
public class SmsInteractionsLoader extends AsyncTaskLoader<List<ContactInteraction>> {

    private static final String TAG = SmsInteractionsLoader.class.getSimpleName();

    private String[] mPhoneNums;
    private int mMaxToRetrieve;
    private List<ContactInteraction> mData;

    /**
     * Loads a list of SmsInteraction from the supplied phone numbers.
     */
    public SmsInteractionsLoader(Context context, String[] phoneNums,
            int maxToRetrieve) {
        super(context);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "SmsInteractionsLoader");
        }
        mPhoneNums = phoneNums;
        mMaxToRetrieve = maxToRetrieve;
    }

    @Override
    public List<ContactInteraction> loadInBackground() {
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "loadInBackground");
        }
        // Confirm the device has Telephony and numbers were provided before proceeding
        if (!getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY)
                || mPhoneNums == null || mPhoneNums.length == 0) {
            return Collections.emptyList();
        }

        // Retrieve the thread IDs
        List<String> threadIdStrings = new ArrayList<>();
        for (String phone : mPhoneNums) {
            // TODO: the phone numbers added to the ContactInteraction result should retain their
            // original formatting since TalkBack is not reading the normalized numbers correctly
            try {
                threadIdStrings.add(String.valueOf(
                        TelephonyThreadsCompat.getOrCreateThreadId(getContext(), phone)));
            } catch (Exception e) {
                // Do nothing. Telephony.Threads.getOrCreateThreadId() throws exceptions when
                // it can't find/create a threadId (b/17657656).
            }
        }

        // Query the SMS database for the threads
        Cursor cursor = getSmsCursorFromThreads(threadIdStrings);
        if (cursor != null) {
            try {
                List<ContactInteraction> interactions = new ArrayList<>();
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    DatabaseUtils.cursorRowToContentValues(cursor, values);
                    interactions.add(new SmsInteraction(values));
                }

                return interactions;
            } finally {
                cursor.close();
            }
        }

        return Collections.emptyList();
    }

    /**
     * Return the most recent messages between a list of threads
     */
    private Cursor getSmsCursorFromThreads(List<String> threadIds) {
        if (threadIds.size() == 0) {
            return null;
        }
        String selection = Telephony.Sms.THREAD_ID + " IN "
                + ContactInteractionUtil.questionMarks(threadIds.size());

        return getContext().getContentResolver().query(
                Telephony.Sms.CONTENT_URI,
                /* projection = */ null,
                selection,
                threadIds.toArray(new String[threadIds.size()]),
                Telephony.Sms.DEFAULT_SORT_ORDER
                        + " LIMIT " + mMaxToRetrieve);
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();

        if (mData != null) {
            deliverResult(mData);
        }

        if (takeContentChanged() || mData == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad();
    }

    @Override
    public void deliverResult(List<ContactInteraction> data) {
        mData = data;
        if (isStarted()) {
            super.deliverResult(data);
        }
    }

    @Override
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();
        if (mData != null) {
            mData.clear();
        }
    }
}
