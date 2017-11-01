/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.app.IntentService;
import android.content.Intent;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.contacts.GeoUtil;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A service to test various phone number formatters.
 *
   Usage:
     adb shell am startservice -e n PHONE_NUMBER \
       [-e c OPTIONAL COUNTRY CODE]  \
       com.android.contacts.tests/.PhoneNumberTestService

   Example:

   adb shell am startservice -e n '6502530000' \
     com.android.contacts.tests/.PhoneNumberTestService
 */
public class PhoneNumberTestService extends IntentService {
    private static final String TAG = "phonenumber";

    private static final String EXTRA_PHONE_NUMBER = "n";
    private static final String EXTRA_COUNTRY_CODE = "c";

    public PhoneNumberTestService() {
        super("PhoneNumberTestService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String number = intent.getStringExtra(EXTRA_PHONE_NUMBER);
        final String country = intent.getStringExtra(EXTRA_COUNTRY_CODE);
        final String defaultCountry = getCurrentCountryCode();

        Log.i(TAG, "Input phone number: " + number);
        Log.i(TAG, "Input country code: " + country);
        Log.i(TAG, "Current country code: " + defaultCountry);

        // Dump for the given country, the current country, US, GB and JP.
        Set<String> countries = new LinkedHashSet<String>();
        if (country != null) countries.add(country);
        countries.add(defaultCountry);
        countries.add("US");
        countries.add("GB");
        countries.add("JP");

        for (String c : countries) {
            dump(number, c);
        }
    }

    private void dump(String number, String country) {
        Log.i(TAG, "Result for: " + number + " / " +country);
        dump_PhoneNumberUtils_formatNumberToE164(number, country);
        dump_PhoneNumberUtil_format(number, country, PhoneNumberFormat.E164);
        dump_PhoneNumberUtil_format(number, country, PhoneNumberFormat.INTERNATIONAL);
        dump_PhoneNumberUtil_format(number, country, PhoneNumberFormat.NATIONAL);
        dump_PhoneNumberUtil_format(number, country, PhoneNumberFormat.RFC3966);
    }

    private void dump_PhoneNumberUtils_formatNumberToE164(String number, String country) {
        Log.i(TAG, "  formatNumberToE164(" + number + ", " + country
                + ") = " + PhoneNumberUtils.formatNumberToE164(number, country));
    }

    private void dump_PhoneNumberUtil_format(String number, String country,
            PhoneNumberFormat format) {
        String formatted;
        String truncated = "";
        boolean isValid = false;
        try {
            final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            final PhoneNumber pn = util.parse(number, country);
            isValid = util.isValidNumber(pn);
            formatted = util.format(pn, format);
            util.truncateTooLongNumber(pn);
            truncated = util.format(pn, format);
        } catch (NumberParseException e) {
            formatted = "Error: " + e.toString();
        }
        Log.i(TAG, "  PhoneNumberUtil.format(parse(" + number + ", " + country + "), " + format
                + ") = " + formatted + " / truncated = " + truncated
                + (isValid ? " (valid)" : " (invalid)"));
    }

    private String getCurrentCountryCode() {
        return GeoUtil.getCurrentCountryIso(getApplicationContext());
    }
}
