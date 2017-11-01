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

import android.content.res.Configuration;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.Calendar;
import java.util.Locale;

/**
 * Tests for utility functions in {@link ContactInteractionUtil}
 */
@SmallTest
public class ContactInteractionUtilTest extends AndroidTestCase {

    private Locale mOriginalLocale;
    private Calendar calendar;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        calendar = Calendar.getInstance();

        // Time/Date utilities rely on specific locales. Forace US and set back in tearDown()
        mOriginalLocale = Locale.getDefault();
        setLocale(Locale.US);
    }

    @Override
    protected void tearDown() throws Exception {
        setLocale(mOriginalLocale);
        super.tearDown();
    }

    public void testOneQuestionMark() {
        assertEquals("(?)", ContactInteractionUtil.questionMarks(1));
    }

    public void testTwoQuestionMarks() {
        assertEquals("(?,?)", ContactInteractionUtil.questionMarks(2));
    }

    public void testFiveQuestionMarks() {
        assertEquals("(?,?,?,?,?)", ContactInteractionUtil.questionMarks(5));
    }

    public void testFormatDateStringFromTimestamp_todaySingleMinuteAm() {
        // Test today scenario (time shown)
        // Single digit minute & AM
        calendar.set(Calendar.HOUR_OF_DAY, 8);
        calendar.set(Calendar.MINUTE, 8);
        long todayTimestamp = calendar.getTimeInMillis();
        assertEquals("8:08 AM", ContactInteractionUtil.formatDateStringFromTimestamp(
                calendar.getTimeInMillis(), getContext()));
    }

    public void testFormatDateStringFromTimestamp_todayDoubleMinutePm() {
        // Double digit minute & PM
        calendar.set(Calendar.HOUR_OF_DAY, 22);
        calendar.set(Calendar.MINUTE, 18);
        assertEquals("10:18 PM",
                ContactInteractionUtil.formatDateStringFromTimestamp(calendar.getTimeInMillis(),
                        getContext()));
    }

    public void testFormatDateStringFromTimestamp_other() {
        // Test other (Month Date)
        calendar.set(
                /* year = */ 1991,
                /* month = */ Calendar.MONTH,
                /* day = */ 11,
                /* hourOfDay = */ 8,
                /* minute = */ 8);
        assertEquals("Monday, March 11, 1991, 8:08 AM",
                ContactInteractionUtil.formatDateStringFromTimestamp(calendar.getTimeInMillis(),
                        getContext()));
    }

    public void testFormatDuration_zero() {
        assertEquals("0 sec",
                ContactInteractionUtil.formatDuration(0, getContext()));
    }

    public void testFormatDuration_minZeroSec() {
        assertEquals("1 min 0 sec",
                ContactInteractionUtil.formatDuration(60, getContext()));
    }

    public void testFormatDuration_minSec() {
        assertEquals("30 min 9 sec",
                ContactInteractionUtil.formatDuration(1809, getContext()));
    }

    public void testFormatDuration_hrZeroMinZeroSec() {
        assertEquals("1 hr 0 min 0 sec",
                ContactInteractionUtil.formatDuration(3600, getContext()));
    }

    public void testFormatDuration_hrMinSec() {
        assertEquals("2 hr 44 min 36 sec",
                ContactInteractionUtil.formatDuration(9876, getContext()));
    }

    private void setLocale(Locale locale) {
        Locale.setDefault(locale);
        Resources res = getContext().getResources();
        Configuration config = res.getConfiguration();
        config.locale = locale;
        res.updateConfiguration(config, res.getDisplayMetrics());
    }
}