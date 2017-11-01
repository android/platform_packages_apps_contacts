/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.contacts.compat;

import android.telephony.PhoneNumberUtils;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.style.TtsSpan;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * This class contains static utility methods extracted from PhoneNumberUtils, and the
 * methods were added in API level 23. In this way, we could enable the corresponding functionality
 * for pre-M devices. We need maintain this class and keep it synced with PhoneNumberUtils.
 * Another thing to keep in mind is that we use com.google.i18n rather than com.android.i18n in
 * here, so we need make sure the application behavior is preserved.
 */
public class PhoneNumberUtilsCompat {
    /**
     * Not instantiable.
     */
    private PhoneNumberUtilsCompat() {}

    public static String normalizeNumber(String phoneNumber) {
        if (CompatUtils.isLollipopCompatible()) {
            return PhoneNumberUtils.normalizeNumber(phoneNumber);
        } else {
            return normalizeNumberInternal(phoneNumber);
        }
    }

    /**
     * Implementation copied from {@link PhoneNumberUtils#normalizeNumber}
     */
    private static String normalizeNumberInternal(String phoneNumber) {
        if (TextUtils.isEmpty(phoneNumber)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int len = phoneNumber.length();
        for (int i = 0; i < len; i++) {
            char c = phoneNumber.charAt(i);
            // Character.digit() supports ASCII and Unicode digits (fullwidth, Arabic-Indic, etc.)
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                sb.append(digit);
            } else if (sb.length() == 0 && c == '+') {
                sb.append(c);
            } else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
                return normalizeNumber(PhoneNumberUtils.convertKeypadLettersToDigits(phoneNumber));
            }
        }
        return sb.toString();
    }

    public static String formatNumber(
            String phoneNumber, String phoneNumberE164, String defaultCountryIso) {
        if (CompatUtils.isLollipopCompatible()) {
            return PhoneNumberUtils.formatNumber(phoneNumber, phoneNumberE164, defaultCountryIso);
        } else {
            // This method was deprecated in API level 21, so it's only used on pre-L SDKs.
            return PhoneNumberUtils.formatNumber(phoneNumber);
        }
    }

    public static CharSequence createTtsSpannable(CharSequence phoneNumber) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return PhoneNumberUtils.createTtsSpannable(phoneNumber);
        } else {
            return createTtsSpannableInternal(phoneNumber);
        }
    }

    public static TtsSpan createTtsSpan(String phoneNumber) {
        if (CompatUtils.isMarshmallowCompatible()) {
            return PhoneNumberUtils.createTtsSpan(phoneNumber);
        } else if (CompatUtils.isLollipopCompatible()) {
            return createTtsSpanLollipop(phoneNumber);
        } else {
            return null;
        }
    }

    /**
     * Copied from {@link PhoneNumberUtils#createTtsSpannable}
     */
    private static CharSequence createTtsSpannableInternal(CharSequence phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        Spannable spannable = Spannable.Factory.getInstance().newSpannable(phoneNumber);
        addTtsSpanInternal(spannable, 0, spannable.length());
        return spannable;
    }

    /**
     * Compat method for addTtsSpan, see {@link PhoneNumberUtils#addTtsSpan}
     */
    public static void addTtsSpan(Spannable s, int start, int endExclusive) {
        if (CompatUtils.isMarshmallowCompatible()) {
            PhoneNumberUtils.addTtsSpan(s, start, endExclusive);
        } else {
            addTtsSpanInternal(s, start, endExclusive);
        }
    }

    /**
     * Copied from {@link PhoneNumberUtils#addTtsSpan}
     */
    private static void addTtsSpanInternal(Spannable s, int start, int endExclusive) {
        s.setSpan(createTtsSpan(s.subSequence(start, endExclusive).toString()),
                start,
                endExclusive,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * Copied from {@link PhoneNumberUtils#createTtsSpan}
     */
    private static TtsSpan createTtsSpanLollipop(String phoneNumberString) {
        if (phoneNumberString == null) {
            return null;
        }

        // Parse the phone number
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        PhoneNumber phoneNumber = null;
        try {
            // Don't supply a defaultRegion so this fails for non-international numbers because
            // we don't want to TalkBalk to read a country code (e.g. +1) if it is not already
            // present
            phoneNumber = phoneNumberUtil.parse(phoneNumberString, /* defaultRegion */ null);
        } catch (NumberParseException ignored) {
        }

        // Build a telephone tts span
        final TtsSpan.TelephoneBuilder builder = new TtsSpan.TelephoneBuilder();
        if (phoneNumber == null) {
            // Strip separators otherwise TalkBack will be silent
            // (this behavior was observed with TalkBalk 4.0.2 from their alpha channel)
            builder.setNumberParts(splitAtNonNumerics(phoneNumberString));
        } else {
            if (phoneNumber.hasCountryCode()) {
                builder.setCountryCode(Integer.toString(phoneNumber.getCountryCode()));
            }
            builder.setNumberParts(Long.toString(phoneNumber.getNationalNumber()));
        }
        return builder.build();
    }



    /**
     * Split a phone number using spaces, ignoring anything that is not a digit
     * @param number A {@code CharSequence} before splitting, e.g., "+20(123)-456#"
     * @return A {@code String} after splitting, e.g., "20 123 456".
     */
    private static String splitAtNonNumerics(CharSequence number) {
        StringBuilder sb = new StringBuilder(number.length());
        for (int i = 0; i < number.length(); i++) {
            sb.append(PhoneNumberUtils.isISODigit(number.charAt(i))
                    ? number.charAt(i)
                    : " ");
        }
        // It is very important to remove extra spaces. At time of writing, any leading or trailing
        // spaces, or any sequence of more than one space, will confuse TalkBack and cause the TTS
        // span to be non-functional!
        return sb.toString().replaceAll(" +", " ").trim();
    }

}
