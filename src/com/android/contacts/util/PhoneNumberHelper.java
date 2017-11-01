/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.contacts.util;

import android.telephony.PhoneNumberUtils;
import android.util.Log;

/**
 * This class wraps several PhoneNumberUtil calls and TelephonyManager calls. Some of them are
 * the same as the ones in the framework's code base. We can remove those once they are part of
 * the public API.
 */
public class PhoneNumberHelper {

    private static final String LOG_TAG = PhoneNumberHelper.class.getSimpleName();

    private static final String KOREA_ISO_COUNTRY_CODE = "KR";
    /**
     * Determines if the specified number is actually a URI (i.e. a SIP address) rather than a
     * regular PSTN phone number, based on whether or not the number contains an "@" character.
     *
     * @param number Phone number
     * @return true if number contains @
     *
     * TODO: Remove if PhoneNumberUtils.isUriNumber(String number) is made public.
     */
    public static boolean isUriNumber(String number) {
        // Note we allow either "@" or "%40" to indicate a URI, in case
        // the passed-in string is URI-escaped.  (Neither "@" nor "%40"
        // will ever be found in a legal PSTN number.)
        return number != null && (number.contains("@") || number.contains("%40"));
    }

    /**
     * Normalize a phone number by removing the characters other than digits. If
     * the given number has keypad letters, the letters will be converted to
     * digits first.
     *
     * @param phoneNumber The number to be normalized.
     * @return The normalized number.
     *
     * TODO: Remove if PhoneNumberUtils.normalizeNumber(String phoneNumber) is made public.
     */
    public static String normalizeNumber(String phoneNumber) {
        StringBuilder sb = new StringBuilder();
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            // Character.digit() supports ASCII and Unicode digits (fullwidth, Arabic-Indic, etc.)
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                sb.append(digit);
            } else if (i == 0 && c == '+') {
                sb.append(c);
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return normalizeNumber(PhoneNumberUtils.convertKeypadLettersToDigits(phoneNumber));
            }
        }
        return sb.toString();
    }

    /**
     * @return the "username" part of the specified SIP address, i.e. the part before the "@"
     * character (or "%40").
     *
     * @param number SIP address of the form "username@domainname" (or the URI-escaped equivalent
     * "username%40domainname")
     *
     * TODO: Remove if PhoneNumberUtils.getUsernameFromUriNumber(String number) is made public.
     */
    public static String getUsernameFromUriNumber(String number) {
        // The delimiter between username and domain name can be
        // either "@" or "%40" (the URI-escaped equivalent.)
        int delimiterIndex = number.indexOf('@');
        if (delimiterIndex < 0) {
            delimiterIndex = number.indexOf("%40");
        }
        if (delimiterIndex < 0) {
            Log.w(LOG_TAG,
                    "getUsernameFromUriNumber: no delimiter found in SIP addr '" + number + "'");
            return number;
        }
        return number.substring(0, delimiterIndex);
    }
}
