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

package com.android.contacts.datepicker;

// This is a fork of the standard Android DatePicker that additionally allows toggling the year
// on/off.

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.android.contacts.R;
import com.android.contacts.datepicker.DatePicker.OnDateChangedListener;
import com.android.contacts.util.DateUtils;

import java.text.DateFormat;
import java.util.Calendar;

/**
 * A simple dialog containing an {@link DatePicker}.
 *
 * <p>See the <a href="{@docRoot}resources/tutorials/views/hello-datepicker.html">Date Picker
 * tutorial</a>.</p>
 */
public class DatePickerDialog extends AlertDialog implements OnClickListener,
        OnDateChangedListener {

    /** Magic year that represents "no year" */
    public static int NO_YEAR = DatePicker.NO_YEAR;

    private static final String YEAR = "year";
    private static final String MONTH = "month";
    private static final String DAY = "day";
    private static final String YEAR_OPTIONAL = "year_optional";

    private final DatePicker mDatePicker;
    private final OnDateSetListener mCallBack;
    private final DateFormat mTitleDateFormat;
    private final DateFormat mTitleNoYearDateFormat;

    private int mInitialYear;
    private int mInitialMonth;
    private int mInitialDay;

    /**
     * The callback used to indicate the user is done filling in the date.
     */
    public interface OnDateSetListener {
        /**
         * @param view The view associated with this listener.
         * @param year The year that was set or {@link DatePickerDialog#NO_YEAR} if the user has
         *  not specified a year
         * @param monthOfYear The month that was set (0-11) for compatibility
         *  with {@link java.util.Calendar}.
         * @param dayOfMonth The day of the month that was set.
         */
        void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth);
    }

    /**
     * @param context The context the dialog is to run in.
     * @param callBack How the parent is notified that the date is set.
     * @param year The initial year of the dialog or {@link DatePickerDialog#NO_YEAR} if no year
     *  has been specified
     * @param monthOfYear The initial month of the dialog.
     * @param dayOfMonth The initial day of the dialog.
     * @param yearOptional Whether the year can be toggled by the user
     */
    public DatePickerDialog(Context context,
            OnDateSetListener callBack,
            int year,
            int monthOfYear,
            int dayOfMonth,
            boolean yearOptional) {
        // Don't pass a theme id. Instead use the default alert dialog theme.
        this(context, /* themeId = */ 0, callBack, year, monthOfYear, dayOfMonth,
                yearOptional);
    }

    /**
     * @param context The context the dialog is to run in.
     * @param theme the theme to apply to this dialog
     * @param callBack How the parent is notified that the date is set.
     * @param year The initial year of the dialog or {@link DatePickerDialog#NO_YEAR} if no
     *  year has been specified.
     * @param monthOfYear The initial month of the dialog.
     * @param dayOfMonth The initial day of the dialog.
     * @param yearOptional Whether the year can be toggled by the user
     */
    public DatePickerDialog(Context context,
            int theme,
            OnDateSetListener callBack,
            int year,
            int monthOfYear,
            int dayOfMonth,
            boolean yearOptional) {
        super(context, theme);

        mCallBack = callBack;
        mInitialYear = year;
        mInitialMonth = monthOfYear;
        mInitialDay = dayOfMonth;

        mTitleDateFormat = DateFormat.getDateInstance(DateFormat.FULL);
        mTitleNoYearDateFormat = DateUtils.getLocalizedDateFormatWithoutYear(getContext());
        updateTitle(mInitialYear, mInitialMonth, mInitialDay);

        setButton(BUTTON_POSITIVE, context.getText(R.string.date_time_set),
                this);
        setButton(BUTTON_NEGATIVE, context.getText(android.R.string.cancel),
                (OnClickListener) null);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.date_picker_dialog, null);
        setView(view);
        mDatePicker = (DatePicker) view.findViewById(R.id.datePicker);
        mDatePicker.init(mInitialYear, mInitialMonth, mInitialDay, yearOptional, this);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (mCallBack != null) {
            mDatePicker.clearFocus();
            mCallBack.onDateSet(mDatePicker, mDatePicker.getYear(),
                    mDatePicker.getMonth(), mDatePicker.getDayOfMonth());
        }
    }

    @Override
    public void onDateChanged(DatePicker view, int year, int month, int day) {
        updateTitle(year, month, day);
    }

    private void updateTitle(int year, int month, int day) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        final DateFormat dateFormat =
                year == NO_YEAR ? mTitleNoYearDateFormat : mTitleDateFormat;
        setTitle(dateFormat.format(calendar.getTime()));
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(YEAR, mDatePicker.getYear());
        state.putInt(MONTH, mDatePicker.getMonth());
        state.putInt(DAY, mDatePicker.getDayOfMonth());
        state.putBoolean(YEAR_OPTIONAL, mDatePicker.isYearOptional());
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int year = savedInstanceState.getInt(YEAR);
        int month = savedInstanceState.getInt(MONTH);
        int day = savedInstanceState.getInt(DAY);
        boolean yearOptional = savedInstanceState.getBoolean(YEAR_OPTIONAL);
        mDatePicker.init(year, month, day, yearOptional, this);
        updateTitle(year, month, day);
    }
}
