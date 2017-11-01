package com.android.contacts.interactions;

import android.content.Context;
import android.content.res.Resources;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;

import com.android.contacts.R;

import java.util.Formatter;
import java.util.Locale;

/**
 * The following methods were pulled from
 * {@link com.android.calendar.EventInfoFragment.updateEvent(View view)}
 * TODO: Move this to frameworks/opt
 */
public class CalendarInteractionUtils {

    // Using int constants as a return value instead of an enum to minimize resources.
    private static final int TODAY = 1;
    private static final int TOMORROW = 2;
    private static final int NONE = 0;

    /**
     * Returns a string description of the specified time interval.
     */
    public static String getDisplayedDatetime(long startMillis, long endMillis, long currentMillis,
            String localTimezone, boolean allDay, Context context) {
        // Configure date/time formatting.
        int flagsDate = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY;
        int flagsTime = DateUtils.FORMAT_SHOW_TIME;
        if (DateFormat.is24HourFormat(context)) {
            flagsTime |= DateUtils.FORMAT_24HOUR;
        }

        Time currentTime = new Time(localTimezone);
        currentTime.set(currentMillis);
        Resources resources = context.getResources();
        String datetimeString = null;
        if (allDay) {
            // All day events require special timezone adjustment.
            long localStartMillis = convertAlldayUtcToLocal(null, startMillis, localTimezone);
            long localEndMillis = convertAlldayUtcToLocal(null, endMillis, localTimezone);
            if (singleDayEvent(localStartMillis, localEndMillis, currentTime.gmtoff)) {
                // If possible, use "Today" or "Tomorrow" instead of a full date string.
                int todayOrTomorrow = isTodayOrTomorrow(context.getResources(),
                        localStartMillis, currentMillis, currentTime.gmtoff);
                if (TODAY == todayOrTomorrow) {
                    datetimeString = resources.getString(R.string.today);
                } else if (TOMORROW == todayOrTomorrow) {
                    datetimeString = resources.getString(R.string.tomorrow);
                }
            }
            if (datetimeString == null) {
                // For multi-day allday events or single-day all-day events that are not
                // today or tomorrow, use framework formatter.
                Formatter f = new Formatter(new StringBuilder(50), Locale.getDefault());
                datetimeString = DateUtils.formatDateRange(context, f, startMillis,
                        endMillis, flagsDate, Time.TIMEZONE_UTC).toString();
            }
        } else {
            if (singleDayEvent(startMillis, endMillis, currentTime.gmtoff)) {
                // Format the time.
                String timeString = formatDateRange(context, startMillis, endMillis,
                        flagsTime);

                // If possible, use "Today" or "Tomorrow" instead of a full date string.
                int todayOrTomorrow = isTodayOrTomorrow(context.getResources(), startMillis,
                        currentMillis, currentTime.gmtoff);
                if (TODAY == todayOrTomorrow) {
                    // Example: "Today at 1:00pm - 2:00 pm"
                    datetimeString = resources.getString(R.string.today_at_time_fmt,
                            timeString);
                } else if (TOMORROW == todayOrTomorrow) {
                    // Example: "Tomorrow at 1:00pm - 2:00 pm"
                    datetimeString = resources.getString(R.string.tomorrow_at_time_fmt,
                            timeString);
                } else {
                    // Format the full date. Example: "Thursday, April 12, 1:00pm - 2:00pm"
                    String dateString = formatDateRange(context, startMillis, endMillis,
                            flagsDate);
                    datetimeString = resources.getString(R.string.date_time_fmt, dateString,
                            timeString);
                }
            } else {
                // For multiday events, shorten day/month names.
                // Example format: "Fri Apr 6, 5:00pm - Sun, Apr 8, 6:00pm"
                int flagsDatetime = flagsDate | flagsTime | DateUtils.FORMAT_ABBREV_MONTH |
                        DateUtils.FORMAT_ABBREV_WEEKDAY;
                datetimeString = formatDateRange(context, startMillis, endMillis,
                        flagsDatetime);
            }
        }
        return datetimeString;
    }

    /**
     * Convert given UTC time into current local time. This assumes it is for an
     * allday event and will adjust the time to be on a midnight boundary.
     *
     * @param recycle Time object to recycle, otherwise null.
     * @param utcTime Time to convert, in UTC.
     * @param tz The time zone to convert this time to.
     */
    private static long convertAlldayUtcToLocal(Time recycle, long utcTime, String tz) {
        if (recycle == null) {
            recycle = new Time();
        }
        recycle.timezone = Time.TIMEZONE_UTC;
        recycle.set(utcTime);
        recycle.timezone = tz;
        return recycle.normalize(true);
    }

    public static long convertAlldayLocalToUTC(Time recycle, long localTime, String tz) {
        if (recycle == null) {
            recycle = new Time();
        }
        recycle.timezone = tz;
        recycle.set(localTime);
        recycle.timezone = Time.TIMEZONE_UTC;
        return recycle.normalize(true);
    }

    /**
     * Returns whether the specified time interval is in a single day.
     */
    private static boolean singleDayEvent(long startMillis, long endMillis, long localGmtOffset) {
        if (startMillis == endMillis) {
            return true;
        }

        // An event ending at midnight should still be a single-day event, so check
        // time end-1.
        int startDay = Time.getJulianDay(startMillis, localGmtOffset);
        int endDay = Time.getJulianDay(endMillis - 1, localGmtOffset);
        return startDay == endDay;
    }

    /**
     * Returns TODAY or TOMORROW if applicable.  Otherwise returns NONE.
     */
    private static int isTodayOrTomorrow(Resources r, long dayMillis,
            long currentMillis, long localGmtOffset) {
        int startDay = Time.getJulianDay(dayMillis, localGmtOffset);
        int currentDay = Time.getJulianDay(currentMillis, localGmtOffset);

        int days = startDay - currentDay;
        if (days == 1) {
            return TOMORROW;
        } else if (days == 0) {
            return TODAY;
        } else {
            return NONE;
        }
    }

    /**
     * Formats a date or a time range according to the local conventions.
     *
     * This formats a date/time range using Calendar's time zone and the
     * local conventions for the region of the device.
     *
     * If the {@link DateUtils#FORMAT_UTC} flag is used it will pass in
     * the UTC time zone instead.
     *
     * @param context the context is required only if the time is shown
     * @param startMillis the start time in UTC milliseconds
     * @param endMillis the end time in UTC milliseconds
     * @param flags a bit mask of options See
     * {@link DateUtils#formatDateRange(Context, Formatter, long, long, int, String) formatDateRange}
     * @return a string containing the formatted date/time range.
     */
    private static String formatDateRange(Context context, long startMillis,
            long endMillis, int flags) {
        String date;
        String tz;
        if ((flags & DateUtils.FORMAT_UTC) != 0) {
            tz = Time.TIMEZONE_UTC;
        } else {
            tz = Time.getCurrentTimezone();
        }
        StringBuilder sb = new StringBuilder(50);
        Formatter f = new Formatter(sb, Locale.getDefault());
        sb.setLength(0);
        date = DateUtils.formatDateRange(context, f, startMillis, endMillis, flags,
                tz).toString();
        return date;
    }
}
