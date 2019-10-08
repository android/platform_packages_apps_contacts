/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.util;

import static android.provider.ContactsContract.CommonDataKinds.Phone;

import android.content.res.Resources;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.util.Patterns;
import androidx.annotation.Nullable;
import com.android.contacts.R;
import com.android.contacts.compat.PhoneNumberUtilsCompat;
import com.android.contacts.preference.ContactsPreferences;

/**
 * Methods for handling various contact data labels.
 */
public class ContactDisplayUtils {

    private static final String TAG = ContactDisplayUtils.class.getSimpleName();

    /**
     * Checks if the given data type is a custom type.
     *
     * @param type Phone data type.
     * @return {@literal true} if the type is custom.  {@literal false} if not.
     */
    public static boolean isCustomPhoneType(Integer type) {
        return type == Phone.TYPE_CUSTOM || type == Phone.TYPE_ASSISTANT;
    }

    /**
     * Find a label for calling.
     *
     * @param type The type of number.
     * @return An appropriate string label.
     */
    public static int getPhoneLabelResourceId(Integer type) {
        if (type == null) return R.string.call_other;
        switch (type) {
            case Phone.TYPE_HOME:
                return R.string.call_home;
            case Phone.TYPE_MOBILE:
                return R.string.call_mobile;
            case Phone.TYPE_WORK:
                return R.string.call_work;
            case Phone.TYPE_FAX_WORK:
                return R.string.call_fax_work;
            case Phone.TYPE_FAX_HOME:
                return R.string.call_fax_home;
            case Phone.TYPE_PAGER:
                return R.string.call_pager;
            case Phone.TYPE_OTHER:
                return R.string.call_other;
            case Phone.TYPE_CALLBACK:
                return R.string.call_callback;
            case Phone.TYPE_CAR:
                return R.string.call_car;
            case Phone.TYPE_COMPANY_MAIN:
                return R.string.call_company_main;
            case Phone.TYPE_ISDN:
                return R.string.call_isdn;
            case Phone.TYPE_MAIN:
                return R.string.call_main;
            case Phone.TYPE_OTHER_FAX:
                return R.string.call_other_fax;
            case Phone.TYPE_RADIO:
                return R.string.call_radio;
            case Phone.TYPE_TELEX:
                return R.string.call_telex;
            case Phone.TYPE_TTY_TDD:
                return R.string.call_tty_tdd;
            case Phone.TYPE_WORK_MOBILE:
                return R.string.call_work_mobile;
            case Phone.TYPE_WORK_PAGER:
                return R.string.call_work_pager;
            case Phone.TYPE_ASSISTANT:
                return R.string.call_assistant;
            case Phone.TYPE_MMS:
                return R.string.call_mms;
            default:
                return R.string.call_custom;
        }

    }

    /**
     * Find a label for sending an sms.
     *
     * @param type The type of number.
     * @return An appropriate string label.
     */
    public static int getSmsLabelResourceId(Integer type) {
        if (type == null) return R.string.sms_other;
        switch (type) {
            case Phone.TYPE_HOME:
                return R.string.sms_home;
            case Phone.TYPE_MOBILE:
                return R.string.sms_mobile;
            case Phone.TYPE_WORK:
                return R.string.sms_work;
            case Phone.TYPE_FAX_WORK:
                return R.string.sms_fax_work;
            case Phone.TYPE_FAX_HOME:
                return R.string.sms_fax_home;
            case Phone.TYPE_PAGER:
                return R.string.sms_pager;
            case Phone.TYPE_OTHER:
                return R.string.sms_other;
            case Phone.TYPE_CALLBACK:
                return R.string.sms_callback;
            case Phone.TYPE_CAR:
                return R.string.sms_car;
            case Phone.TYPE_COMPANY_MAIN:
                return R.string.sms_company_main;
            case Phone.TYPE_ISDN:
                return R.string.sms_isdn;
            case Phone.TYPE_MAIN:
                return R.string.sms_main;
            case Phone.TYPE_OTHER_FAX:
                return R.string.sms_other_fax;
            case Phone.TYPE_RADIO:
                return R.string.sms_radio;
            case Phone.TYPE_TELEX:
                return R.string.sms_telex;
            case Phone.TYPE_TTY_TDD:
                return R.string.sms_tty_tdd;
            case Phone.TYPE_WORK_MOBILE:
                return R.string.sms_work_mobile;
            case Phone.TYPE_WORK_PAGER:
                return R.string.sms_work_pager;
            case Phone.TYPE_ASSISTANT:
                return R.string.sms_assistant;
            case Phone.TYPE_MMS:
                return R.string.sms_mms;
            default:
                return R.string.sms_custom;
        }
    }

    /**
     * Whether the given text could be a phone number.
     *
     * Note this will miss many things that are legitimate phone numbers, for example,
     * phone numbers with letters.
     */
    public static boolean isPossiblePhoneNumber(CharSequence text) {
        return text == null ? false : Patterns.PHONE.matcher(text.toString()).matches();
    }

    /**
     * Returns a Spannable for the given message with a telephone {@link TtsSpan} set for
     * the given phone number text wherever it is found within the message.
     */
    public static Spannable getTelephoneTtsSpannable(String message, String phoneNumber) {
        if (message == null) {
            return null;
        }
        final Spannable spannable = new SpannableString(message);
        int start = phoneNumber == null ? -1 : message.indexOf(phoneNumber);
        while (start >= 0) {
            final int end = start + phoneNumber.length();
            final TtsSpan ttsSpan = PhoneNumberUtilsCompat.createTtsSpan(phoneNumber);
            spannable.setSpan(ttsSpan, start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);             // this is consistenly done in a misleading way..
            start = message.indexOf(phoneNumber, end);
        }
        return spannable;
    }

    /**
     * Retrieves a string from a string template that takes 1 phone number as argument,
     * span the number with a telephone {@link TtsSpan}, and return the spanned string.
     *
     * @param resources to retrieve the string from
     * @param stringId ID of the string
     * @param number to pass in the template
     * @return CharSequence with the phone number wrapped in a TtsSpan
     */
    public static CharSequence getTtsSpannedPhoneNumber(Resources resources,
            int stringId, String number){
        String msg = resources.getString(stringId, number);
        return ContactDisplayUtils.getTelephoneTtsSpannable(msg, number);
    }

    /**
     * Returns either namePrimary or nameAlternative based on the {@link ContactsPreferences}.
     * Defaults to the name that is non-null.
     *
     * @param namePrimary the primary name.
     * @param nameAlternative the alternative name.
     * @param contactsPreferences the ContactsPreferences used to determine the preferred
     * display name.
     * @return namePrimary or nameAlternative depending on the value of displayOrderPreference.
     */
    public static String getPreferredDisplayName(String namePrimary, String nameAlternative,
            @Nullable ContactsPreferences contactsPreferences) {
        if (contactsPreferences == null) {
            return namePrimary != null ? namePrimary : nameAlternative;
        }
        if (contactsPreferences.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            return namePrimary;
        }

        if (contactsPreferences.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_ALTERNATIVE
                && !TextUtils.isEmpty(nameAlternative)) {
            return nameAlternative;
        }

        return namePrimary;
    }

    /**
     * Returns either namePrimary or nameAlternative based on the {@link ContactsPreferences}.
     * Defaults to the name that is non-null.
     *
     * @param namePrimary the primary name.
     * @param nameAlternative the alternative name.
     * @param contactsPreferences the ContactsPreferences used to determine the preferred sort
     * order.
     * @return namePrimary or nameAlternative depending on the value of displayOrderPreference.
     */
    public static String getPreferredSortName(String namePrimary, String nameAlternative,
            @Nullable ContactsPreferences contactsPreferences) {
        if (contactsPreferences == null) {
            return namePrimary != null ? namePrimary : nameAlternative;
        }

        if (contactsPreferences.getSortOrder() == ContactsPreferences.SORT_ORDER_PRIMARY) {
            return namePrimary;
        }

        if (contactsPreferences.getSortOrder() == ContactsPreferences.SORT_ORDER_ALTERNATIVE &&
                !TextUtils.isEmpty(nameAlternative)) {
            return nameAlternative;
        }

        return namePrimary;
    }
}
