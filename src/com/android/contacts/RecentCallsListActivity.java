/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.contacts;

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteDiskIOException;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteFullException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.PhoneLookup;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;

/**
 * Displays a list of call log entries.
 */
public class RecentCallsListActivity extends ListActivity
        implements View.OnCreateContextMenuListener {
    private static final String TAG = "RecentCallsList";

    /** The projection to use when querying the call log table */
    static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL
    };

    static final int ID_COLUMN_INDEX = 0;
    static final int NUMBER_COLUMN_INDEX = 1;
    static final int DATE_COLUMN_INDEX = 2;
    static final int DURATION_COLUMN_INDEX = 3;
    static final int CALL_TYPE_COLUMN_INDEX = 4;
    static final int CALLER_NAME_COLUMN_INDEX = 5;
    static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;

    /** The projection to use when querying the phones table */
    static final String[] PHONES_PROJECTION = new String[] {
            PhoneLookup._ID,
            PhoneLookup.DISPLAY_NAME,
            PhoneLookup.TYPE,
            PhoneLookup.LABEL,
            PhoneLookup.NUMBER
    };

    static final int PERSON_ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int PHONE_TYPE_COLUMN_INDEX = 2;
    static final int LABEL_COLUMN_INDEX = 3;
    static final int MATCHED_NUMBER_COLUMN_INDEX = 4;

    private static final int MENU_ITEM_DELETE_ALL = 1;
    private static final int CONTEXT_MENU_ITEM_DELETE = 1;
    private static final int CONTEXT_MENU_CALL_CONTACT = 2;

    private static final int QUERY_TOKEN = 53;
    private static final int UPDATE_TOKEN = 54;

    private static final int DIALOG_CONFIRM_DELETE_ALL = 1;

    RecentCallsAdapter mAdapter;
    private QueryHandler mQueryHandler;
    String mVoiceMailNumber;

    private boolean mScrollToTop;

    static final class ContactInfo {
        public long personId;
        public String name;
        public int type;
        public String label;
        public String number;
        public String formattedNumber;

        public static ContactInfo EMPTY = new ContactInfo();
    }

    public static final class RecentCallsListItemViews {
        TextView line1View;
        TextView labelView;
        TextView numberView;
        TextView dateView;
        ImageView iconView;
        View callView;
        ImageView groupIndicator;
        TextView groupSize;
    }

    static final class CallerInfoQuery {
        String number;
        int position;
        String name;
        int numberType;
        String numberLabel;
    }

    /**
     * Shared builder used by {@link #formatPhoneNumber(String)} to minimize
     * allocations when formatting phone numbers.
     */
    private static final SpannableStringBuilder sEditable = new SpannableStringBuilder();

    /**
     * Invalid formatting type constant for {@link #sFormattingType}.
     */
    private static final int FORMATTING_TYPE_INVALID = -1;

    /**
     * Cached formatting type for current {@link Locale}, as provided by
     * {@link PhoneNumberUtils#getFormatTypeForLocale(Locale)}.
     */
    private static int sFormattingType = FORMATTING_TYPE_INVALID;

    /** Adapter class to fill in data for the Call Log */
    final class RecentCallsAdapter extends GroupingListAdapter
            implements Runnable, ViewTreeObserver.OnPreDrawListener, View.OnClickListener {
        HashMap<String,ContactInfo> mContactInfo;
        private final LinkedList<CallerInfoQuery> mRequests;
        private volatile boolean mDone;
        private boolean mLoading = true;
        ViewTreeObserver.OnPreDrawListener mPreDrawListener;
        private static final int REDRAW = 1;
        private static final int START_THREAD = 2;
        private boolean mFirst;
        private Thread mCallerIdThread;

        private CharSequence[] mLabelArray;

        private Drawable mDrawableIncoming;
        private Drawable mDrawableOutgoing;
        private Drawable mDrawableMissed;

        /**
         * Reusable char array buffers.
         */
        private CharArrayBuffer mBuffer1 = new CharArrayBuffer(128);
        private CharArrayBuffer mBuffer2 = new CharArrayBuffer(128);

        public void onClick(View view) {
            String number = (String) view.getTag();
            if (!TextUtils.isEmpty(number)) {
                // Here, "number" can either be a PSTN phone number or a
                // SIP address.  So turn it into either a tel: URI or a
                // sip: URI, as appropriate.
                Uri callUri;
                if (PhoneNumberUtils.isUriNumber(number)) {
                    callUri = Uri.fromParts("sip", number, null);
                } else {
                    callUri = Uri.fromParts("tel", number, null);
                }
                StickyTabs.saveTab(RecentCallsListActivity.this, getIntent());
                startActivity(new Intent(Intent.ACTION_CALL_PRIVILEGED, callUri));
            }
        }

        public boolean onPreDraw() {
            if (mFirst) {
                mHandler.sendEmptyMessageDelayed(START_THREAD, 1000);
                mFirst = false;
            }
            return true;
        }

        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REDRAW:
                        notifyDataSetChanged();
                        break;
                    case START_THREAD:
                        startRequestProcessing();
                        break;
                }
            }
        };

        public RecentCallsAdapter() {
            super(RecentCallsListActivity.this);

            mContactInfo = new HashMap<String,ContactInfo>();
            mRequests = new LinkedList<CallerInfoQuery>();
            mPreDrawListener = null;

            mDrawableIncoming = getResources().getDrawable(
                    R.drawable.ic_call_log_list_incoming_call);
            mDrawableOutgoing = getResources().getDrawable(
                    R.drawable.ic_call_log_list_outgoing_call);
            mDrawableMissed = getResources().getDrawable(
                    R.drawable.ic_call_log_list_missed_call);
            mLabelArray = getResources().getTextArray(com.android.internal.R.array.phoneTypes);
        }

        /**
         * Requery on background thread when {@link Cursor} changes.
         */
        @Override
        protected void onContentChanged() {
            // Start async requery
            startQuery();
        }

        void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        public ContactInfo getContactInfo(String number) {
            return mContactInfo.get(number);
        }

        public void startRequestProcessing() {
            mDone = false;
            mCallerIdThread = new Thread(this);
            mCallerIdThread.setPriority(Thread.MIN_PRIORITY);
            mCallerIdThread.start();
        }

        public void stopRequestProcessing() {
            mDone = true;
            if (mCallerIdThread != null) mCallerIdThread.interrupt();
        }

        public void clearCache() {
            synchronized (mContactInfo) {
                mContactInfo.clear();
            }
        }

        private void updateCallLog(CallerInfoQuery ciq, ContactInfo ci) {
            // Check if they are different. If not, don't update.
            if (TextUtils.equals(ciq.name, ci.name)
                    && TextUtils.equals(ciq.numberLabel, ci.label)
                    && ciq.numberType == ci.type) {
                return;
            }
            ContentValues values = new ContentValues(3);
            values.put(Calls.CACHED_NAME, ci.name);
            values.put(Calls.CACHED_NUMBER_TYPE, ci.type);
            values.put(Calls.CACHED_NUMBER_LABEL, ci.label);

            try {
                RecentCallsListActivity.this.getContentResolver().update(Calls.CONTENT_URI, values,
                        Calls.NUMBER + "='" + ciq.number + "'", null);
            } catch (SQLiteDiskIOException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteFullException e) {
                Log.w(TAG, "Exception while updating call info", e);
            } catch (SQLiteDatabaseCorruptException e) {
                Log.w(TAG, "Exception while updating call info", e);
            }
        }

        private void enqueueRequest(String number, int position,
                String name, int numberType, String numberLabel) {
            CallerInfoQuery ciq = new CallerInfoQuery();
            ciq.number = number;
            ciq.position = position;
            ciq.name = name;
            ciq.numberType = numberType;
            ciq.numberLabel = numberLabel;
            synchronized (mRequests) {
                mRequests.add(ciq);
                mRequests.notifyAll();
            }
        }

        private boolean queryContactInfo(CallerInfoQuery ciq) {
            // First check if there was a prior request for the same number
            // that was already satisfied
            ContactInfo info = mContactInfo.get(ciq.number);
            boolean needNotify = false;
            if (info != null && info != ContactInfo.EMPTY) {
                return true;
            } else {
                // Ok, do a fresh Contacts lookup for ciq.number.
                boolean infoUpdated = false;

                if (PhoneNumberUtils.isUriNumber(ciq.number)) {
                    // This "number" is really a SIP address.

                    // TODO: This code is duplicated from the
                    // CallerInfoAsyncQuery class.  To avoid that, could the
                    // code here just use CallerInfoAsyncQuery, rather than
                    // manually running ContentResolver.query() itself?

                    // We look up SIP addresses directly in the Data table:
                    Uri contactRef = Data.CONTENT_URI;

                    // Note Data.DATA1 and SipAddress.SIP_ADDRESS are equivalent.
                    //
                    // Also note we use "upper(data1)" in the WHERE clause, and
                    // uppercase the incoming SIP address, in order to do a
                    // case-insensitive match.
                    //
                    // TODO: May also need to normalize by adding "sip:" as a
                    // prefix, if we start storing SIP addresses that way in the
                    // database.
                    String selection = "upper(" + Data.DATA1 + ")=?"
                            + " AND "
                            + Data.MIMETYPE + "='" + SipAddress.CONTENT_ITEM_TYPE + "'";
                    String[] selectionArgs = new String[] { ciq.number.toUpperCase() };

                    Cursor dataTableCursor =
                            RecentCallsListActivity.this.getContentResolver().query(
                                    contactRef,
                                    null,  // projection
                                    selection,  // selection
                                    selectionArgs,  // selectionArgs
                                    null);  // sortOrder

                    if (dataTableCursor != null) {
                        if (dataTableCursor.moveToFirst()) {
                            info = new ContactInfo();

                            // TODO: we could slightly speed this up using an
                            // explicit projection (and thus not have to do
                            // those getColumnIndex() calls) but the benefit is
                            // very minimal.

                            // Note the Data.CONTACT_ID column here is
                            // equivalent to the PERSON_ID_COLUMN_INDEX column
                            // we use with "phonesCursor" below.
                            info.personId = dataTableCursor.getLong(
                                    dataTableCursor.getColumnIndex(Data.CONTACT_ID));
                            info.name = dataTableCursor.getString(
                                    dataTableCursor.getColumnIndex(Data.DISPLAY_NAME));
                            // "type" and "label" are currently unused for SIP addresses
                            info.type = SipAddress.TYPE_OTHER;
                            info.label = null;

                            // And "number" is the SIP address.
                            // Note Data.DATA1 and SipAddress.SIP_ADDRESS are equivalent.
                            info.number = dataTableCursor.getString(
                                    dataTableCursor.getColumnIndex(Data.DATA1));

                            infoUpdated = true;
                        }
                        dataTableCursor.close();
                    }
                } else {
                    // "number" is a regular phone number, so use the
                    // PhoneLookup table:
                    Cursor phonesCursor =
                            RecentCallsListActivity.this.getContentResolver().query(
                                Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                                     Uri.encode(ciq.number)),
                                PHONES_PROJECTION, null, null, null);
                    if (phonesCursor != null) {
                        if (phonesCursor.moveToFirst()) {
                            info = new ContactInfo();
                            info.personId = phonesCursor.getLong(PERSON_ID_COLUMN_INDEX);
                            info.name = phonesCursor.getString(NAME_COLUMN_INDEX);
                            info.type = phonesCursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                            info.label = phonesCursor.getString(LABEL_COLUMN_INDEX);
                            info.number = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);

                            infoUpdated = true;
                        }
                        phonesCursor.close();
                    }
                }

                if (infoUpdated) {
                    // New incoming phone number invalidates our formatted
                    // cache. Any cache fills happen only on the GUI thread.
                    info.formattedNumber = null;

                    mContactInfo.put(ciq.number, info);

                    // Inform list to update this item, if in view
                    needNotify = true;
                }
            }
            if (info != null) {
                updateCallLog(ciq, info);
            }
            return needNotify;
        }

        /*
         * Handles requests for contact name and number type
         * @see java.lang.Runnable#run()
         */
        public void run() {
            boolean needNotify = false;
            while (!mDone) {
                CallerInfoQuery ciq = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        ciq = mRequests.removeFirst();
                    } else {
                        if (needNotify) {
                            needNotify = false;
                            mHandler.sendEmptyMessage(REDRAW);
                        }
                        try {
                            mRequests.wait(1000);
                        } catch (InterruptedException ie) {
                            // Ignore and continue processing requests
                        }
                    }
                }
                if (ciq != null && queryContactInfo(ciq)) {
                    needNotify = true;
                }
            }
        }

        @Override
        protected void addGroups(Cursor cursor) {

            int count = cursor.getCount();
            if (count == 0) {
                return;
            }

            int groupItemCount = 1;

            CharArrayBuffer currentValue = mBuffer1;
            CharArrayBuffer value = mBuffer2;
            cursor.moveToFirst();
            cursor.copyStringToBuffer(NUMBER_COLUMN_INDEX, currentValue);
            int currentCallType = cursor.getInt(CALL_TYPE_COLUMN_INDEX);
            for (int i = 1; i < count; i++) {
                cursor.moveToNext();
                cursor.copyStringToBuffer(NUMBER_COLUMN_INDEX, value);
                boolean sameNumber = equalPhoneNumbers(value, currentValue);

                // Group adjacent calls with the same number. Make an exception
                // for the latest item if it was a missed call.  We don't want
                // a missed call to be hidden inside a group.
                if (sameNumber && currentCallType != Calls.MISSED_TYPE) {
                    groupItemCount++;
                } else {
                    if (groupItemCount > 1) {
                        addGroup(i - groupItemCount, groupItemCount, false);
                    }

                    groupItemCount = 1;

                    // Swap buffers
                    CharArrayBuffer temp = currentValue;
                    currentValue = value;
                    value = temp;

                    // If we have just examined a row following a missed call, make
                    // sure that it is grouped with subsequent calls from the same number
                    // even if it was also missed.
                    if (sameNumber && currentCallType == Calls.MISSED_TYPE) {
                        currentCallType = 0;       // "not a missed call"
                    } else {
                        currentCallType = cursor.getInt(CALL_TYPE_COLUMN_INDEX);
                    }
                }
            }
            if (groupItemCount > 1) {
                addGroup(count - groupItemCount, groupItemCount, false);
            }
        }

        protected boolean equalPhoneNumbers(CharArrayBuffer buffer1, CharArrayBuffer buffer2) {

            // TODO add PhoneNumberUtils.compare(CharSequence, CharSequence) to avoid
            // string allocation
            return PhoneNumberUtils.compare(new String(buffer1.data, 0, buffer1.sizeCopied),
                    new String(buffer2.data, 0, buffer2.sizeCopied));
        }


        @Override
        protected View newStandAloneView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.recent_calls_list_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @Override
        protected void bindStandAloneView(View view, Context context, Cursor cursor) {
            bindView(context, view, cursor);
        }

        @Override
        protected View newChildView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.recent_calls_list_child_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @Override
        protected void bindChildView(View view, Context context, Cursor cursor) {
            bindView(context, view, cursor);
        }

        @Override
        protected View newGroupView(Context context, ViewGroup parent) {
            LayoutInflater inflater =
                    (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.recent_calls_list_group_item, parent, false);
            findAndCacheViews(view);
            return view;
        }

        @Override
        protected void bindGroupView(View view, Context context, Cursor cursor, int groupSize,
                boolean expanded) {
            final RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();
            int groupIndicator = expanded
                    ? com.android.internal.R.drawable.expander_ic_maximized
                    : com.android.internal.R.drawable.expander_ic_minimized;
            views.groupIndicator.setImageResource(groupIndicator);
            views.groupSize.setText("(" + groupSize + ")");
            bindView(context, view, cursor);
        }

        private void findAndCacheViews(View view) {

            // Get the views to bind to
            RecentCallsListItemViews views = new RecentCallsListItemViews();
            views.line1View = (TextView) view.findViewById(R.id.line1);
            views.labelView = (TextView) view.findViewById(R.id.label);
            views.numberView = (TextView) view.findViewById(R.id.number);
            views.dateView = (TextView) view.findViewById(R.id.date);
            views.iconView = (ImageView) view.findViewById(R.id.call_type_icon);
            views.callView = view.findViewById(R.id.call_icon);
            views.callView.setOnClickListener(this);
            views.groupIndicator = (ImageView) view.findViewById(R.id.groupIndicator);
            views.groupSize = (TextView) view.findViewById(R.id.groupSize);
            view.setTag(views);
        }

        public void bindView(Context context, View view, Cursor c) {
            final RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String formattedNumber = null;
            String callerName = c.getString(CALLER_NAME_COLUMN_INDEX);
            int callerNumberType = c.getInt(CALLER_NUMBERTYPE_COLUMN_INDEX);
            String callerNumberLabel = c.getString(CALLER_NUMBERLABEL_COLUMN_INDEX);

            // Store away the number so we can call it directly if you click on the call icon
            views.callView.setTag(number);

            // Lookup contacts with this number
            ContactInfo info = mContactInfo.get(number);
            if (info == null) {
                // Mark it as empty and queue up a request to find the name
                // The db request should happen on a non-UI thread
                info = ContactInfo.EMPTY;
                mContactInfo.put(number, info);
                enqueueRequest(number, c.getPosition(),
                        callerName, callerNumberType, callerNumberLabel);
            } else if (info != ContactInfo.EMPTY) { // Has been queried
                // Check if any data is different from the data cached in the
                // calls db. If so, queue the request so that we can update
                // the calls db.
                if (!TextUtils.equals(info.name, callerName)
                        || info.type != callerNumberType
                        || !TextUtils.equals(info.label, callerNumberLabel)) {
                    // Something is amiss, so sync up.
                    enqueueRequest(number, c.getPosition(),
                            callerName, callerNumberType, callerNumberLabel);
                }

                // Format and cache phone number for found contact
                if (info.formattedNumber == null) {
                    info.formattedNumber = formatPhoneNumber(info.number);
                }
                formattedNumber = info.formattedNumber;
            }

            String name = info.name;
            int ntype = info.type;
            String label = info.label;
            // If there's no name cached in our hashmap, but there's one in the
            // calls db, use the one in the calls db. Otherwise the name in our
            // hashmap is more recent, so it has precedence.
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(callerName)) {
                name = callerName;
                ntype = callerNumberType;
                label = callerNumberLabel;

                // Format the cached call_log phone number
                formattedNumber = formatPhoneNumber(number);
            }
            // Set the text lines and call icon.
            // Assumes the call back feature is on most of the
            // time. For private and unknown numbers: hide it.
            views.callView.setVisibility(View.VISIBLE);

            if (!TextUtils.isEmpty(name)) {
                views.line1View.setText(name);
                views.labelView.setVisibility(View.VISIBLE);

                // "type" and "label" are currently unused for SIP addresses.
                CharSequence numberLabel = null;
                if (!PhoneNumberUtils.isUriNumber(number)) {
                    numberLabel = Phone.getDisplayLabel(context, ntype, label,
                            mLabelArray);
                }
                views.numberView.setVisibility(View.VISIBLE);
                views.numberView.setText(formattedNumber);
                if (!TextUtils.isEmpty(numberLabel)) {
                    views.labelView.setText(numberLabel);
                    views.labelView.setVisibility(View.VISIBLE);

                    // Zero out the numberView's left margin (see below)
                    ViewGroup.MarginLayoutParams numberLP =
                            (ViewGroup.MarginLayoutParams) views.numberView.getLayoutParams();
                    numberLP.leftMargin = 0;
                    views.numberView.setLayoutParams(numberLP);
                } else {
                    // There's nothing to display in views.labelView, so hide it.
                    // We can't set it to View.GONE, since it's the anchor for
                    // numberView in the RelativeLayout, so make it INVISIBLE.
                    //   Also, we need to manually *subtract* some left margin from
                    // numberView to compensate for the right margin built in to
                    // labelView (otherwise the number will be indented by a very
                    // slight amount).
                    //   TODO: a cleaner fix would be to contain both the label and
                    // number inside a LinearLayout, and then set labelView *and*
                    // its padding to GONE when there's no label to display.
                    views.labelView.setText(null);
                    views.labelView.setVisibility(View.INVISIBLE);

                    ViewGroup.MarginLayoutParams labelLP =
                            (ViewGroup.MarginLayoutParams) views.labelView.getLayoutParams();
                    ViewGroup.MarginLayoutParams numberLP =
                            (ViewGroup.MarginLayoutParams) views.numberView.getLayoutParams();
                    // Equivalent to setting android:layout_marginLeft in XML
                    numberLP.leftMargin = -labelLP.rightMargin;
                    views.numberView.setLayoutParams(numberLP);
                }
            } else {
                if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
                    number = getString(R.string.unknown);
                    views.callView.setVisibility(View.INVISIBLE);
                } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
                    number = getString(R.string.private_num);
                    views.callView.setVisibility(View.INVISIBLE);
                } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                    number = getString(R.string.payphone);
                } else if (PhoneNumberUtils.extractNetworkPortion(number)
                                .equals(mVoiceMailNumber)) {
                    number = getString(R.string.voicemail);
                } else {
                    // Just a raw number, and no cache, so format it nicely
                    number = formatPhoneNumber(number);
                }

                views.line1View.setText(number);
                views.numberView.setVisibility(View.GONE);
                views.labelView.setVisibility(View.GONE);
            }

            long date = c.getLong(DATE_COLUMN_INDEX);

            // Set the date/time field by mixing relative and absolute times.
            int flags = DateUtils.FORMAT_ABBREV_RELATIVE;

            views.dateView.setText(DateUtils.getRelativeTimeSpanString(date,
                    System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, flags));

            if (views.iconView != null) {
                int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
                // Set the icon
                switch (type) {
                    case Calls.INCOMING_TYPE:
                        views.iconView.setImageDrawable(mDrawableIncoming);
                        break;

                    case Calls.OUTGOING_TYPE:
                        views.iconView.setImageDrawable(mDrawableOutgoing);
                        break;

                    case Calls.MISSED_TYPE:
                        views.iconView.setImageDrawable(mDrawableMissed);
                        break;
                }
            }

            // Listen for the first draw
            if (mPreDrawListener == null) {
                mFirst = true;
                mPreDrawListener = this;
                view.getViewTreeObserver().addOnPreDrawListener(this);
            }
        }
    }

    private static final class QueryHandler extends AsyncQueryHandler {
        private final WeakReference<RecentCallsListActivity> mActivity;

        /**
         * Simple handler that wraps background calls to catch
         * {@link SQLiteException}, such as when the disk is full.
         */
        protected class CatchingWorkerHandler extends AsyncQueryHandler.WorkerHandler {
            public CatchingWorkerHandler(Looper looper) {
                super(looper);
            }

            @Override
            public void handleMessage(Message msg) {
                try {
                    // Perform same query while catching any exceptions
                    super.handleMessage(msg);
                } catch (SQLiteDiskIOException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteFullException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                } catch (SQLiteDatabaseCorruptException e) {
                    Log.w(TAG, "Exception on background worker thread", e);
                }
            }
        }

        @Override
        protected Handler createHandler(Looper looper) {
            // Provide our special handler that catches exceptions
            return new CatchingWorkerHandler(looper);
        }

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<RecentCallsListActivity>(
                    (RecentCallsListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final RecentCallsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                final RecentCallsListActivity.RecentCallsAdapter callsAdapter = activity.mAdapter;
                callsAdapter.setLoading(false);
                callsAdapter.changeCursor(cursor);
                if (activity.mScrollToTop) {
                    if (activity.mList.getFirstVisiblePosition() > 5) {
                        activity.mList.setSelection(5);
                    }
                    activity.mList.smoothScrollToPosition(0);
                    activity.mScrollToTop = false;
                }
            } else {
                cursor.close();
            }
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.recent_calls);

        // Typing here goes to the dialer
        setDefaultKeyMode(DEFAULT_KEYS_DIALER);

        mAdapter = new RecentCallsAdapter();
        getListView().setOnCreateContextMenuListener(this);
        setListAdapter(mAdapter);

        mVoiceMailNumber = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
                .getVoiceMailNumber();
        mQueryHandler = new QueryHandler(this);

        // Reset locale-based formatting cache
        sFormattingType = FORMATTING_TYPE_INVALID;
    }

    @Override
    protected void onStart() {
        mScrollToTop = true;
        super.onStart();
    }

    @Override
    protected void onResume() {
        // The adapter caches looked up numbers, clear it so they will get
        // looked up again.
        if (mAdapter != null) {
            mAdapter.clearCache();
        }

        startQuery();
        resetNewCallsFlag();

        super.onResume();

        mAdapter.startRequestProcessing();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAdapter.stopRequestProcessing();
        mAdapter.changeCursor(null);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        // Clear notifications only when window gains focus.  This activity won't
        // immediately receive focus if the keyguard screen is above it.
        if (hasFocus) {
            try {
                ITelephony iTelephony =
                        ITelephony.Stub.asInterface(ServiceManager.getService("phone"));
                if (iTelephony != null) {
                    iTelephony.cancelMissedCallsNotification();
                } else {
                    Log.w(TAG, "Telephony service is null, can't call " +
                            "cancelMissedCallsNotification");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to clear missed calls notification due to remote exception");
            }
        }
    }

    /**
     * Format the given phone number using
     * {@link PhoneNumberUtils#formatNumber(android.text.Editable, int)}. This
     * helper method uses {@link #sEditable} and {@link #sFormattingType} to
     * prevent allocations between multiple calls.
     * <p>
     * Because of the shared {@link #sEditable} builder, <b>this method is not
     * thread safe</b>, and should only be called from the GUI thread.
     * <p>
     * If the given String object is null or empty, return an empty String.
     */
    private String formatPhoneNumber(String number) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }

        // If "number" is really a SIP address, don't try to do any formatting at all.
        if (PhoneNumberUtils.isUriNumber(number)) {
            return number;
        }

        // Cache formatting type if not already present
        if (sFormattingType == FORMATTING_TYPE_INVALID) {
            sFormattingType = PhoneNumberUtils.getFormatTypeForLocale(Locale.getDefault());
        }

        sEditable.clear();
        sEditable.append(number);

        PhoneNumberUtils.formatNumber(sEditable, sFormattingType);
        return sEditable.toString();
    }

    private void resetNewCallsFlag() {
        // Mark all "new" missed calls as not new anymore
        StringBuilder where = new StringBuilder("type=");
        where.append(Calls.MISSED_TYPE);
        where.append(" AND new=1");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");
        mQueryHandler.startUpdate(UPDATE_TOKEN, null, Calls.CONTENT_URI,
                values, where.toString(), null);
    }

    private void startQuery() {
        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.startQuery(QUERY_TOKEN, null, Calls.CONTENT_URI,
                CALL_LOG_PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ITEM_DELETE_ALL, 0, R.string.recentCalls_deleteAll)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
             menuInfo = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(menuInfo.position);

        String number = cursor.getString(NUMBER_COLUMN_INDEX);
        Uri numberUri = null;
        boolean isVoicemail = false;
        boolean isSipNumber = false;
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            number = getString(R.string.unknown);
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            number = getString(R.string.private_num);
        } else if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            number = getString(R.string.payphone);
        } else if (PhoneNumberUtils.extractNetworkPortion(number).equals(mVoiceMailNumber)) {
            number = getString(R.string.voicemail);
            numberUri = Uri.parse("voicemail:x");
            isVoicemail = true;
        } else if (PhoneNumberUtils.isUriNumber(number)) {
            numberUri = Uri.fromParts("sip", number, null);
            isSipNumber = true;
        } else {
            numberUri = Uri.fromParts("tel", number, null);
        }

        ContactInfo info = mAdapter.getContactInfo(number);
        boolean contactInfoPresent = (info != null && info != ContactInfo.EMPTY);
        if (contactInfoPresent) {
            menu.setHeaderTitle(info.name);
        } else {
            menu.setHeaderTitle(number);
        }

        if (numberUri != null) {
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, numberUri);
            menu.add(0, CONTEXT_MENU_CALL_CONTACT, 0,
                    getResources().getString(R.string.recentCalls_callNumber, number))
                    .setIntent(intent);
        }

        if (contactInfoPresent) {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    ContentUris.withAppendedId(Contacts.CONTENT_URI, info.personId));
            StickyTabs.setTab(intent, getIntent());
            menu.add(0, 0, 0, R.string.menu_viewContact).setIntent(intent);
        }

        if (numberUri != null && !isVoicemail && !isSipNumber) {
            menu.add(0, 0, 0, R.string.recentCalls_editNumberBeforeCall)
                    .setIntent(new Intent(Intent.ACTION_DIAL, numberUri));
            menu.add(0, 0, 0, R.string.menu_sendTextMessage)
                    .setIntent(new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts("sms", number, null)));
        }

        // "Add to contacts" item, if this entry isn't already associated with a contact
        if (!contactInfoPresent && numberUri != null && !isVoicemail && !isSipNumber) {
            // TODO: This item is currently disabled for SIP addresses, because
            // the Insert.PHONE extra only works correctly for PSTN numbers.
            //
            // To fix this for SIP addresses, we need to:
            // - define ContactsContract.Intents.Insert.SIP_ADDRESS, and use it here if
            //   the current number is a SIP address
            // - update the contacts UI code to handle Insert.SIP_ADDRESS by
            //   updating the SipAddress field
            // and then we can remove the "!isSipNumber" check above.

            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            intent.putExtra(Insert.PHONE, number);
            menu.add(0, 0, 0, R.string.recentCalls_addToContact)
                    .setIntent(intent);
        }
        menu.add(0, CONTEXT_MENU_ITEM_DELETE, 0, R.string.recentCalls_removeFromRecentList);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {
            case DIALOG_CONFIRM_DELETE_ALL:
                return new AlertDialog.Builder(this)
                    .setTitle(R.string.clearCallLogConfirmation_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.clearCallLogConfirmation)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            getContentResolver().delete(Calls.CONTENT_URI, null, null);
                            // TODO The change notification should do this automatically, but it
                            // isn't working right now. Remove this when the change notification
                            // is working properly.
                            startQuery();
                        }
                    })
                    .setCancelable(false)
                    .create();
        }
        return null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_DELETE_ALL: {
                showDialog(DIALOG_CONFIRM_DELETE_ALL);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CONTEXT_MENU_ITEM_DELETE: {
                // Convert the menu info to the proper type
                AdapterView.AdapterContextMenuInfo menuInfo;
                try {
                     menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                } catch (ClassCastException e) {
                    Log.e(TAG, "bad menuInfoIn", e);
                    return false;
                }

                Cursor cursor = (Cursor)mAdapter.getItem(menuInfo.position);
                int groupSize = 1;
                if (mAdapter.isGroupHeader(menuInfo.position)) {
                    groupSize = mAdapter.getGroupSize(menuInfo.position);
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < groupSize; i++) {
                    if (i != 0) {
                        sb.append(",");
                        cursor.moveToNext();
                    }
                    long id = cursor.getLong(ID_COLUMN_INDEX);
                    sb.append(id);
                }

                getContentResolver().delete(Calls.CONTENT_URI, Calls._ID + " IN (" + sb + ")",
                        null);
                return true;
            }
            case CONTEXT_MENU_CALL_CONTACT: {
                StickyTabs.saveTab(this, getIntent());
                startActivity(item.getIntent());
                return true;
            }
            default: {
                return super.onContextItemSelected(item);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                long callPressDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (callPressDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Launch voice dialer
                    Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                    }
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                try {
                    ITelephony phone = ITelephony.Stub.asInterface(
                            ServiceManager.checkService("phone"));
                    if (phone != null && !phone.isIdle()) {
                        // Let the super class handle it
                        break;
                    }
                } catch (RemoteException re) {
                    // Fall through and try to call the contact
                }

                callEntry(getListView().getSelectedItemPosition());
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    private String getBetterNumberFromContacts(String number) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        ContactInfo ci = mAdapter.mContactInfo.get(number);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor =
                    RecentCallsListActivity.this.getContentResolver().query(
                            Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                                    number),
                    PHONES_PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber) &&
                (matchingNumber.startsWith("+")
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }

    private void callEntry(int position) {
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor != null) {
            String number = cursor.getString(NUMBER_COLUMN_INDEX);
            if (TextUtils.isEmpty(number)
                    || number.equals(CallerInfo.UNKNOWN_NUMBER)
                    || number.equals(CallerInfo.PRIVATE_NUMBER)
                    || number.equals(CallerInfo.PAYPHONE_NUMBER)) {
                // This number can't be called, do nothing
                return;
            }
            Intent intent;
            // If "number" is really a SIP address, construct a sip: URI.
            if (PhoneNumberUtils.isUriNumber(number)) {
                intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                    Uri.fromParts("sip", number, null));
            } else {
                // We're calling a regular PSTN phone number.
                // Construct a tel: URI, but do some other possible cleanup first.
                int callType = cursor.getInt(CALL_TYPE_COLUMN_INDEX);
                if (!number.startsWith("+") &&
                       (callType == Calls.INCOMING_TYPE
                                || callType == Calls.MISSED_TYPE)) {
                    // If the caller-id matches a contact with a better qualified number, use it
                    number = getBetterNumberFromContacts(number);
                }
                intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                    Uri.fromParts("tel", number, null));
            }
            StickyTabs.saveTab(this, getIntent());
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        if (mAdapter.isGroupHeader(position)) {
            mAdapter.toggleGroup(position);
        } else {
            Intent intent = new Intent(this, CallDetailActivity.class);
            intent.setData(ContentUris.withAppendedId(CallLog.Calls.CONTENT_URI, id));
            StickyTabs.setTab(intent, getIntent());
            startActivity(intent);
        }
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }
}
