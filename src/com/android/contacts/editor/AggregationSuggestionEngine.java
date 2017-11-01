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
 * limitations under the License
 */

package com.android.contacts.editor;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.provider.ContactsContract.Contacts.AggregationSuggestions.Builder;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import com.android.contacts.compat.AggregationSuggestionsCompat;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountWithDataSet;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs asynchronous queries to obtain aggregation suggestions in the as-you-type mode.
 */
public class AggregationSuggestionEngine extends HandlerThread {
    public interface Listener {
        void onAggregationSuggestionChange();
    }

    public static final class Suggestion {
        public long contactId;
        public String contactLookupKey;
        public long rawContactId;
        public long photoId = -1;
        public String name;
        public String phoneNumber;
        public String emailAddress;
        public String nickname;

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(Suggestion.class)
                    .add("contactId", contactId)
                    .add("contactLookupKey", contactLookupKey)
                    .add("rawContactId", rawContactId)
                    .add("photoId", photoId)
                    .add("name", name)
                    .add("phoneNumber", phoneNumber)
                    .add("emailAddress", emailAddress)
                    .add("nickname", nickname)
                    .toString();
        }
    }

    private final class SuggestionContentObserver extends ContentObserver {
        private SuggestionContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            scheduleSuggestionLookup();
        }
    }

    private static final int MESSAGE_RESET = 0;
    private static final int MESSAGE_NAME_CHANGE = 1;
    private static final int MESSAGE_DATA_CURSOR = 2;

    private static final long SUGGESTION_LOOKUP_DELAY_MILLIS = 300;

    private static final int SUGGESTIONS_LIMIT = 3;

    private final Context mContext;

    private long[] mSuggestedContactIds = new long[0];
    private Handler mMainHandler;
    private Handler mHandler;
    private long mContactId;
    private AccountWithDataSet mAccountFilter;
    private Listener mListener;
    private Cursor mDataCursor;
    private ContentObserver mContentObserver;
    private Uri mSuggestionsUri;

    public AggregationSuggestionEngine(Context context) {
        super("AggregationSuggestions", Process.THREAD_PRIORITY_BACKGROUND);
        mContext = context.getApplicationContext();
        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                AggregationSuggestionEngine.this.deliverNotification((Cursor) msg.obj);
            }
        };
    }

    protected Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler(getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    AggregationSuggestionEngine.this.handleMessage(msg);
                }
            };
        }
        return mHandler;
    }

    public void setContactId(long contactId) {
        if (contactId != mContactId) {
            mContactId = contactId;
            reset();
        }
    }

    public void setAccountFilter(AccountWithDataSet account) {
        mAccountFilter = account;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean quit() {
        if (mDataCursor != null) {
            mDataCursor.close();
        }
        mDataCursor = null;
        if (mContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        return super.quit();
    }

    public void reset() {
        Handler handler = getHandler();
        handler.removeMessages(MESSAGE_NAME_CHANGE);
        handler.sendEmptyMessage(MESSAGE_RESET);
    }

    public void onNameChange(ValuesDelta values) {
        mSuggestionsUri = buildAggregationSuggestionUri(values);
        if (mSuggestionsUri != null) {
            if (mContentObserver == null) {
                mContentObserver = new SuggestionContentObserver(getHandler());
                mContext.getContentResolver().registerContentObserver(
                        Contacts.CONTENT_URI, true, mContentObserver);
            }
        } else if (mContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        scheduleSuggestionLookup();
    }

    protected void scheduleSuggestionLookup() {
        Handler handler = getHandler();
        handler.removeMessages(MESSAGE_NAME_CHANGE);

        if (mSuggestionsUri == null) {
            return;
        }

        Message msg = handler.obtainMessage(MESSAGE_NAME_CHANGE, mSuggestionsUri);
        handler.sendMessageDelayed(msg, SUGGESTION_LOOKUP_DELAY_MILLIS);
    }

    private Uri buildAggregationSuggestionUri(ValuesDelta values) {
        StringBuilder nameSb = new StringBuilder();
        appendValue(nameSb, values, StructuredName.PREFIX);
        appendValue(nameSb, values, StructuredName.GIVEN_NAME);
        appendValue(nameSb, values, StructuredName.MIDDLE_NAME);
        appendValue(nameSb, values, StructuredName.FAMILY_NAME);
        appendValue(nameSb, values, StructuredName.SUFFIX);

        StringBuilder phoneticNameSb = new StringBuilder();
        appendValue(phoneticNameSb, values, StructuredName.PHONETIC_FAMILY_NAME);
        appendValue(phoneticNameSb, values, StructuredName.PHONETIC_MIDDLE_NAME);
        appendValue(phoneticNameSb, values, StructuredName.PHONETIC_GIVEN_NAME);

        if (nameSb.length() == 0 && phoneticNameSb.length() == 0) {
            return null;
        }

        // AggregationSuggestions.Builder() became visible in API level 23, so use it if applicable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Builder uriBuilder = new AggregationSuggestions.Builder()
                    .setLimit(SUGGESTIONS_LIMIT)
                    .setContactId(mContactId);
            if (nameSb.length() != 0) {
                uriBuilder.addNameParameter(nameSb.toString());
            }
            if (phoneticNameSb.length() != 0) {
                uriBuilder.addNameParameter(phoneticNameSb.toString());
            }
            return uriBuilder.build();
        }

        // For previous SDKs, use the backup plan.
        final AggregationSuggestionsCompat.Builder uriBuilder =
                new AggregationSuggestionsCompat.Builder()
                .setLimit(SUGGESTIONS_LIMIT)
                .setContactId(mContactId);
        if (nameSb.length() != 0) {
            uriBuilder.addNameParameter(nameSb.toString());
        }
        if (phoneticNameSb.length() != 0) {
            uriBuilder.addNameParameter(phoneticNameSb.toString());
        }
        return uriBuilder.build();
    }

    private void appendValue(StringBuilder sb, ValuesDelta values, String column) {
        String value = values.getAsString(column);
        if (!TextUtils.isEmpty(value)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(value);
        }
    }

    protected void handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_RESET:
                mSuggestedContactIds = new long[0];
                break;
            case MESSAGE_NAME_CHANGE:
                loadAggregationSuggestions((Uri) msg.obj);
                break;
        }
    }

    private static final class DataQuery {

        public static final String SELECTION_PREFIX =
                Data.MIMETYPE + " IN ('"
                        + Phone.CONTENT_ITEM_TYPE + "','"
                        + Email.CONTENT_ITEM_TYPE + "','"
                        + StructuredName.CONTENT_ITEM_TYPE + "','"
                        + Nickname.CONTENT_ITEM_TYPE + "','"
                        + Photo.CONTENT_ITEM_TYPE + "')"
                        + " AND " + Data.CONTACT_ID + " IN (";

        public static final String[] COLUMNS = {
                Data.CONTACT_ID,
                Data.LOOKUP_KEY,
                Data.RAW_CONTACT_ID,
                Data.MIMETYPE,
                Data.DATA1,
                Data.IS_SUPER_PRIMARY,
                RawContacts.ACCOUNT_TYPE,
                RawContacts.ACCOUNT_NAME,
                RawContacts.DATA_SET,
                Contacts.Photo._ID
        };

        public static final int CONTACT_ID = 0;
        public static final int LOOKUP_KEY = 1;
        public static final int RAW_CONTACT_ID = 2;
        public static final int MIMETYPE = 3;
        public static final int DATA1 = 4;
        public static final int IS_SUPERPRIMARY = 5;
        public static final int ACCOUNT_TYPE = 6;
        public static final int ACCOUNT_NAME = 7;
        public static final int DATA_SET = 8;
        public static final int PHOTO_ID = 9;
    }

    private void loadAggregationSuggestions(Uri uri) {
        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor cursor = contentResolver.query(uri, new String[]{Contacts._ID}, null, null, null);
        if (cursor == null) {
            return;
        }
        try {
            // If a new request is pending, chuck the result of the previous request
            if (getHandler().hasMessages(MESSAGE_NAME_CHANGE)) {
                return;
            }

            boolean changed = updateSuggestedContactIds(cursor);
            if (!changed) {
                return;
            }

            StringBuilder sb = new StringBuilder(DataQuery.SELECTION_PREFIX);
            int count = mSuggestedContactIds.length;
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(mSuggestedContactIds[i]);
            }
            sb.append(')');

            Cursor dataCursor = contentResolver.query(Data.CONTENT_URI,
                    DataQuery.COLUMNS, sb.toString(), null, Data.CONTACT_ID);
            if (dataCursor != null) {
                mMainHandler.sendMessage(
                        mMainHandler.obtainMessage(MESSAGE_DATA_CURSOR, dataCursor));
            }
        } finally {
            cursor.close();
        }
    }

    private boolean updateSuggestedContactIds(final Cursor cursor) {
        final int count = cursor.getCount();
        boolean changed = count != mSuggestedContactIds.length;
        final ArrayList<Long> newIds = new ArrayList<Long>(count);
        while (cursor.moveToNext()) {
            final long contactId = cursor.getLong(0);
            if (!changed && Arrays.binarySearch(mSuggestedContactIds, contactId) < 0) {
                changed = true;
            }
            newIds.add(contactId);
        }

        if (changed) {
            mSuggestedContactIds = new long[newIds.size()];
            int i = 0;
            for (final Long newId : newIds) {
                mSuggestedContactIds[i++] = newId;
            }
            Arrays.sort(mSuggestedContactIds);
        }

        return changed;
    }

    protected void deliverNotification(Cursor dataCursor) {
        if (mDataCursor != null) {
            mDataCursor.close();
        }
        mDataCursor = dataCursor;
        if (mListener != null) {
            mListener.onAggregationSuggestionChange();
        }
    }

    public int getSuggestedContactCount() {
        return mDataCursor != null ? mDataCursor.getCount() : 0;
    }

    public List<Suggestion> getSuggestions() {
        final ArrayList<Suggestion> list = Lists.newArrayList();

        if (mDataCursor != null && mAccountFilter != null) {
            Suggestion suggestion = null;
            long currentRawContactId = -1;
            mDataCursor.moveToPosition(-1);
            while (mDataCursor.moveToNext()) {
                final long rawContactId = mDataCursor.getLong(DataQuery.RAW_CONTACT_ID);
                if (rawContactId != currentRawContactId) {
                    suggestion = new Suggestion();
                    suggestion.rawContactId = rawContactId;
                    suggestion.contactId = mDataCursor.getLong(DataQuery.CONTACT_ID);
                    suggestion.contactLookupKey = mDataCursor.getString(DataQuery.LOOKUP_KEY);
                    final String accountName = mDataCursor.getString(DataQuery.ACCOUNT_NAME);
                    final String accountType = mDataCursor.getString(DataQuery.ACCOUNT_TYPE);
                    final String dataSet = mDataCursor.getString(DataQuery.DATA_SET);
                    final AccountWithDataSet account = new AccountWithDataSet(
                            accountName, accountType, dataSet);
                    if (mAccountFilter.equals(account)) {
                        list.add(suggestion);
                    }
                    currentRawContactId = rawContactId;
                }

                final String mimetype = mDataCursor.getString(DataQuery.MIMETYPE);
                if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    final String data = mDataCursor.getString(DataQuery.DATA1);
                    int superprimary = mDataCursor.getInt(DataQuery.IS_SUPERPRIMARY);
                    if (!TextUtils.isEmpty(data)
                            && (superprimary != 0 || suggestion.phoneNumber == null)) {
                        suggestion.phoneNumber = data;
                    }
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    final String data = mDataCursor.getString(DataQuery.DATA1);
                    int superprimary = mDataCursor.getInt(DataQuery.IS_SUPERPRIMARY);
                    if (!TextUtils.isEmpty(data)
                            && (superprimary != 0 || suggestion.emailAddress == null)) {
                        suggestion.emailAddress = data;
                    }
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    final String data = mDataCursor.getString(DataQuery.DATA1);
                    if (!TextUtils.isEmpty(data)) {
                        suggestion.nickname = data;
                    }
                } else if (StructuredName.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    // DATA1 stores the display name for the raw contact.
                    final String data = mDataCursor.getString(DataQuery.DATA1);
                    if (!TextUtils.isEmpty(data) && suggestion.name == null) {
                        suggestion.name = data;
                    }
                } else if (Photo.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    final Long id = mDataCursor.getLong(DataQuery.PHOTO_ID);
                    if (suggestion.photoId == -1) {
                        suggestion.photoId = id;
                    }
                }
            }
        }
        return list;
    }
}
