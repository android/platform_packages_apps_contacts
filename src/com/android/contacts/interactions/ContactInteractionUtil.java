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

import android.content.Context;
import android.text.format.DateUtils;

import com.android.contacts.R;

import com.google.common.base.Preconditions;

import java.text.DateFormat;
import java.util.Calendar;

/**
 * Utility methods for interactions and their loaders
 */
public class ContactInteractionUtil {

    /**
     * @return a string like (?,?,?...) with {@param count} question marks.
     */
    public static String questionMarks(int count) {
        Preconditions.checkArgument(count > 0);
        StringBuilder sb = new StringBuilder("(?");
        for (int i = 1; i < count; i++) {
            sb.append(",?");
        }
        return sb.append(")").toString();
    }

    /**
     * Same as {@link formatDateStringFromTimestamp(long, Context, Calendar)} but uses the current
     * time.
     */
    public static String formatDateStringFromTimestamp(long timestamp, Context context) {
        return formatDateStringFromTimestamp(timestamp, context, Calendar.getInstance());
    }

    /**
     * Takes in a timestamp and outputs a human legible date. This checks the timestamp against
     * compareCalendar.
     * This formats the date based on a few conditions:
     * 1. If the timestamp is today, the time is shown
     * 2. Otherwise show full date and time
     */
    public static String formatDateStringFromTimestamp(long timestamp, Context context,
            Calendar compareCalendar) {
        Calendar interactionCalendar = Calendar.getInstance();
        interactionCalendar.setTimeInMillis(timestamp);

        // compareCalendar is initialized to today
        if (compareCalendarDayYear(interactionCalendar, compareCalendar)) {
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(
                    interactionCalendar.getTime());
        }

        return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_SHOW_YEAR);
    }

    /**
     * Compares the day and year of two calendars.
     */
    private static boolean compareCalendarDayYear(Calendar c1, Calendar c2) {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    /**
     * Takes duration of the call in seconds.
     * Return the formatted duration in hr, min, sec order if they exist.
     */
    public static String formatDuration(long callDuration, Context context) {
        final int hours = (int) callDuration / 3600;
        final int minutes = (int) (callDuration % 3600) / 60;
        final int seconds = (int) (callDuration % 60);

        if (hours > 0) {
            return context.getString(R.string.callDurationHourFormat, hours, minutes, seconds);
        } else if (minutes > 0) {
            return context.getString(R.string.callDurationMinuteFormat, minutes, seconds);
        } else {
            return context.getString(R.string.callDurationSecondFormat, seconds);
        }
    }
}
