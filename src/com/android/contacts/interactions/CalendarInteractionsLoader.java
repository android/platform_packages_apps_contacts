package com.android.contacts.interactions;

import android.Manifest.permission;
import android.content.AsyncTaskLoader;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.util.Log;

import com.android.contacts.util.PermissionsUtil;

import com.google.common.base.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Loads a list of calendar interactions showing shared calendar events with everyone passed in
 * {@param emailAddresses}.
 *
 * Note: the calendar provider treats mailing lists as atomic email addresses.
 */
public class CalendarInteractionsLoader extends AsyncTaskLoader<List<ContactInteraction>> {
    private static final String TAG = "CalendarInteractions";

    private List<String> mEmailAddresses;
    private int mMaxFutureToRetrieve;
    private int mMaxPastToRetrieve;
    private long mNumberFutureMillisecondToSearchLocalCalendar;
    private long mNumberPastMillisecondToSearchLocalCalendar;
    private List<ContactInteraction> mData;


    /**
     * @param maxFutureToRetrieve The maximum number of future events to retrieve
     * @param maxPastToRetrieve The maximum number of past events to retrieve
     */
    public CalendarInteractionsLoader(Context context, List<String> emailAddresses,
            int maxFutureToRetrieve, int maxPastToRetrieve,
            long numberFutureMillisecondToSearchLocalCalendar,
            long numberPastMillisecondToSearchLocalCalendar) {
        super(context);
        mEmailAddresses = emailAddresses;
        mMaxFutureToRetrieve = maxFutureToRetrieve;
        mMaxPastToRetrieve = maxPastToRetrieve;
        mNumberFutureMillisecondToSearchLocalCalendar =
                numberFutureMillisecondToSearchLocalCalendar;
        mNumberPastMillisecondToSearchLocalCalendar = numberPastMillisecondToSearchLocalCalendar;
    }

    @Override
    public List<ContactInteraction> loadInBackground() {
        if (!PermissionsUtil.hasPermission(getContext(), permission.READ_CALENDAR)
                || mEmailAddresses == null || mEmailAddresses.size() < 1) {
            return Collections.emptyList();
        }
        // Perform separate calendar queries for events in the past and future.
        Cursor cursor = getSharedEventsCursor(/* isFuture= */ true, mMaxFutureToRetrieve);
        List<ContactInteraction> interactions = getInteractionsFromEventsCursor(cursor);
        cursor = getSharedEventsCursor(/* isFuture= */ false, mMaxPastToRetrieve);
        List<ContactInteraction> interactions2 = getInteractionsFromEventsCursor(cursor);

        ArrayList<ContactInteraction> allInteractions = new ArrayList<ContactInteraction>(
                interactions.size() + interactions2.size());
        allInteractions.addAll(interactions);
        allInteractions.addAll(interactions2);

        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "# ContactInteraction Loaded: " + allInteractions.size());
        }
        return allInteractions;
    }

    /**
     * @return events inside phone owners' calendars, that are shared with people inside mEmails
     */
    private Cursor getSharedEventsCursor(boolean isFuture, int limit) {
        List<String> calendarIds = getOwnedCalendarIds();
        if (calendarIds == null) {
            return null;
        }
        long timeMillis = System.currentTimeMillis();

        List<String> selectionArgs = new ArrayList<>();
        selectionArgs.addAll(mEmailAddresses);
        selectionArgs.addAll(calendarIds);

        // Add time constraints to selectionArgs
        String timeOperator = isFuture ? " > " : " < ";
        long pastTimeCutoff = timeMillis - mNumberPastMillisecondToSearchLocalCalendar;
        long futureTimeCutoff = timeMillis
                + mNumberFutureMillisecondToSearchLocalCalendar;
        String[] timeArguments = {String.valueOf(timeMillis), String.valueOf(pastTimeCutoff),
                String.valueOf(futureTimeCutoff)};
        selectionArgs.addAll(Arrays.asList(timeArguments));

        // When LAST_SYNCED = 1, the event is not a real event. We should ignore all such events.
        String IS_NOT_TEMPORARY_COPY_OF_LOCAL_EVENT
                = CalendarContract.Attendees.LAST_SYNCED + " = 0";

        String orderBy = CalendarContract.Attendees.DTSTART + (isFuture ? " ASC " : " DESC ");
        String selection = caseAndDotInsensitiveEmailComparisonClause(mEmailAddresses.size())
                + " AND " + CalendarContract.Attendees.CALENDAR_ID
                + " IN " + ContactInteractionUtil.questionMarks(calendarIds.size())
                + " AND " + CalendarContract.Attendees.DTSTART + timeOperator + " ? "
                + " AND " + CalendarContract.Attendees.DTSTART + " > ? "
                + " AND " + CalendarContract.Attendees.DTSTART + " < ? "
                + " AND " + IS_NOT_TEMPORARY_COPY_OF_LOCAL_EVENT;

        return getContext().getContentResolver().query(CalendarContract.Attendees.CONTENT_URI,
                /* projection = */ null, selection,
                selectionArgs.toArray(new String[selectionArgs.size()]),
                orderBy + " LIMIT " + limit);
    }

    /**
     * Returns a clause that checks whether an attendee's email is equal to one of
     * {@param count} values. The comparison is insensitive to dots and case.
     *
     * NOTE #1: This function is only needed for supporting non google accounts. For calendars
     * synced by a google account, attendee email values will be be modified by the server to ensure
     * they match an entry in contacts.google.com.
     *
     * NOTE #2: This comparison clause can result in false positives. Ex#1, test@gmail.com will
     * match test@gmailco.m. Ex#2, a.2@exchange.com will match a2@exchange.com (exchange addresses
     * should be dot sensitive). This probably isn't a large concern.
     */
    private String caseAndDotInsensitiveEmailComparisonClause(int count) {
        Preconditions.checkArgument(count > 0, "Count needs to be positive");
        final String COMPARISON
                = " REPLACE(" + CalendarContract.Attendees.ATTENDEE_EMAIL
                + ", '.', '') = REPLACE(?, '.', '') COLLATE NOCASE";
        StringBuilder sb = new StringBuilder("( " + COMPARISON);
        for (int i = 1; i < count; i++) {
            sb.append(" OR " + COMPARISON);
        }
        return sb.append(")").toString();
    }

    /**
     * @return A list with upto one Card. The Card contains events from {@param Cursor}.
     * Only returns unique events.
     */
    private List<ContactInteraction> getInteractionsFromEventsCursor(Cursor cursor) {
        try {
            if (cursor == null || cursor.getCount() == 0) {
                return Collections.emptyList();
            }
            Set<String> uniqueUris = new HashSet<String>();
            ArrayList<ContactInteraction> interactions = new ArrayList<ContactInteraction>();
            while (cursor.moveToNext()) {
                ContentValues values = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, values);
                CalendarInteraction calendarInteraction = new CalendarInteraction(values);
                if (!uniqueUris.contains(calendarInteraction.getIntent().getData().toString())) {
                    uniqueUris.add(calendarInteraction.getIntent().getData().toString());
                    interactions.add(calendarInteraction);
                }
            }

            return interactions;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * @return the Ids of calendars that are owned by accounts on the phone.
     */
    private List<String> getOwnedCalendarIds() {
        String[] projection = new String[] {Calendars._ID, Calendars.CALENDAR_ACCESS_LEVEL};
        Cursor cursor = getContext().getContentResolver().query(Calendars.CONTENT_URI, projection,
                Calendars.VISIBLE + " = 1 AND " + Calendars.CALENDAR_ACCESS_LEVEL + " = ? ",
                new String[] {String.valueOf(Calendars.CAL_ACCESS_OWNER)}, null);
        try {
            if (cursor == null || cursor.getCount() < 1) {
                return null;
            }
            cursor.moveToPosition(-1);
            List<String> calendarIds = new ArrayList<>(cursor.getCount());
            while (cursor.moveToNext()) {
                calendarIds.add(String.valueOf(cursor.getInt(0)));
            }
            return calendarIds;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
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
    protected void onReset() {
        super.onReset();

        // Ensure the loader is stopped
        onStopLoading();
        if (mData != null) {
            mData.clear();
        }
    }

    @Override
    public void deliverResult(List<ContactInteraction> data) {
        mData = data;
        if (isStarted()) {
            super.deliverResult(data);
        }
    }
}
