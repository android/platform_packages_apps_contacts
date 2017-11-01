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

package com.android.contacts;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.model.account.AccountType;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

/**
 * Shared static contact utility methods.
 */
public class MoreContactUtils {

    private static final String WAIT_SYMBOL_AS_STRING = String.valueOf(PhoneNumberUtils.WAIT);

    /**
     * Returns true if two data with mimetypes which represent values in contact entries are
     * considered equal for collapsing in the GUI. For caller-id, use
     * {@link android.telephony.PhoneNumberUtils#compare(android.content.Context, String, String)}
     * instead
     */
    public static boolean shouldCollapse(CharSequence mimetype1, CharSequence data1,
              CharSequence mimetype2, CharSequence data2) {
        // different mimetypes? don't collapse
        if (!TextUtils.equals(mimetype1, mimetype2)) return false;

        // exact same string? good, bail out early
        if (TextUtils.equals(data1, data2)) return true;

        // so if either is null, these two must be different
        if (data1 == null || data2 == null) return false;

        // if this is not about phone numbers, we know this is not a match (of course, some
        // mimetypes could have more sophisticated matching is the future, e.g. addresses)
        if (!TextUtils.equals(ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                mimetype1)) {
            return false;
        }

        return shouldCollapsePhoneNumbers(data1.toString(), data2.toString());
    }

    // TODO: Move this to PhoneDataItem.shouldCollapse override
    private static boolean shouldCollapsePhoneNumbers(String number1, String number2) {
        // Work around to address b/20724444. We want to distinguish between #555, *555 and 555.
        // This makes no attempt to distinguish between 555 and 55*5, since 55*5 is an improbable
        // number. PhoneNumberUtil already distinguishes between 555 and 55#5.
        if (number1.contains("#") != number2.contains("#")
                || number1.contains("*") != number2.contains("*")) {
            return false;
        }

        // Now do the full phone number thing. split into parts, separated by waiting symbol
        // and compare them individually
        final String[] dataParts1 = number1.split(WAIT_SYMBOL_AS_STRING);
        final String[] dataParts2 = number2.split(WAIT_SYMBOL_AS_STRING);
        if (dataParts1.length != dataParts2.length) return false;
        final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
        for (int i = 0; i < dataParts1.length; i++) {
            // Match phone numbers represented by keypad letters, in which case prefer the
            // phone number with letters.
            final String dataPart1 = PhoneNumberUtils.convertKeypadLettersToDigits(dataParts1[i]);
            final String dataPart2 = dataParts2[i];

            // substrings equal? shortcut, don't parse
            if (TextUtils.equals(dataPart1, dataPart2)) continue;

            // do a full parse of the numbers
            final PhoneNumberUtil.MatchType result = util.isNumberMatch(dataPart1, dataPart2);
            switch (result) {
                case NOT_A_NUMBER:
                    // don't understand the numbers? let's play it safe
                    return false;
                case NO_MATCH:
                    return false;
                case EXACT_MATCH:
                    break;
                case NSN_MATCH:
                    try {
                        // For NANP phone numbers, match when one has +1 and the other does not.
                        // In this case, prefer the +1 version.
                        if (util.parse(dataPart1, null).getCountryCode() == 1) {
                            // At this point, the numbers can be either case 1 or 2 below....
                            //
                            // case 1)
                            // +14155551212    <--- country code 1
                            //  14155551212    <--- 1 is trunk prefix, not country code
                            //
                            // and
                            //
                            // case 2)
                            // +14155551212
                            //   4155551212
                            //
                            // From b/7519057, case 2 needs to be equal.  But also that bug, case 3
                            // below should not be equal.
                            //
                            // case 3)
                            // 14155551212
                            //  4155551212
                            //
                            // So in order to make sure transitive equality is valid, case 1 cannot
                            // be equal.  Otherwise, transitive equality breaks and the following
                            // would all be collapsed:
                            //   4155551212  |
                            //  14155551212  |---->   +14155551212
                            // +14155551212  |
                            //
                            // With transitive equality, the collapsed values should be:
                            //   4155551212  |         14155551212
                            //  14155551212  |---->   +14155551212
                            // +14155551212  |

                            // Distinguish between case 1 and 2 by checking for trunk prefix '1'
                            // at the start of number 2.
                            if (dataPart2.trim().charAt(0) == '1') {
                                // case 1
                                return false;
                            }
                            break;
                        }
                    } catch (NumberParseException e) {
                        // This is the case where the first number does not have a country code.
                        // examples:
                        // (123) 456-7890   &   123-456-7890  (collapse)
                        // 0049 (8092) 1234   &   +49/80921234  (unit test says do not collapse)

                        // Check the second number.  If it also does not have a country code, then
                        // we should collapse.  If it has a country code, then it's a different
                        // number and we should not collapse (this conclusion is based on an
                        // existing unit test).
                        try {
                            util.parse(dataPart2, null);
                        } catch (NumberParseException e2) {
                            // Number 2 also does not have a country.  Collapse.
                            break;
                        }
                    }
                    return false;
                case SHORT_NSN_MATCH:
                    return false;
                default:
                    throw new IllegalStateException("Unknown result value from phone number " +
                            "library");
            }
        }
        return true;
    }

    /**
     * Returns the {@link android.graphics.Rect} with left, top, right, and bottom coordinates
     * that are equivalent to the given {@link android.view.View}'s bounds. This is equivalent to
     * how the target {@link android.graphics.Rect} is calculated in
     * {@link android.provider.ContactsContract.QuickContact#showQuickContact}.
     */
    public static Rect getTargetRectFromView(View view) {
        final int[] pos = new int[2];
        view.getLocationOnScreen(pos);

        final Rect rect = new Rect();
        rect.left = pos[0];
        rect.top = pos[1];
        rect.right = pos[0] + view.getWidth();
        rect.bottom = pos[1] + view.getHeight();
        return rect;
    }

    /**
     * Returns a header view based on the R.layout.list_separator, where the
     * containing {@link android.widget.TextView} is set using the given textResourceId.
     */
    public static TextView createHeaderView(Context context, int textResourceId) {
        final TextView textView = (TextView) View.inflate(context, R.layout.list_separator, null);
        textView.setText(context.getString(textResourceId));
        return textView;
    }

    /**
     * Set the top padding on the header view dynamically, based on whether the header is in
     * the first row or not.
     */
    public static void setHeaderViewBottomPadding(Context context, TextView textView,
            boolean isFirstRow) {
        final int topPadding;
        if (isFirstRow) {
            topPadding = (int) context.getResources().getDimension(
                    R.dimen.frequently_contacted_title_top_margin_when_first_row);
        } else {
            topPadding = (int) context.getResources().getDimension(
                    R.dimen.frequently_contacted_title_top_margin);
        }
        textView.setPaddingRelative(textView.getPaddingStart(), topPadding,
                textView.getPaddingEnd(), textView.getPaddingBottom());
    }


    /**
     * Returns the intent to launch for the given invitable account type and contact lookup URI.
     * This will return null if the account type is not invitable (i.e. there is no
     * {@link AccountType#getInviteContactActivityClassName()} or
     * {@link AccountType#syncAdapterPackageName}).
     */
    public static Intent getInvitableIntent(AccountType accountType, Uri lookupUri) {
        String syncAdapterPackageName = accountType.syncAdapterPackageName;
        String className = accountType.getInviteContactActivityClassName();
        if (TextUtils.isEmpty(syncAdapterPackageName) || TextUtils.isEmpty(className)) {
            return null;
        }
        Intent intent = new Intent();
        intent.setClassName(syncAdapterPackageName, className);

        intent.setAction(ContactsContract.Intents.INVITE_CONTACT);

        // Data is the lookup URI.
        intent.setData(lookupUri);
        return intent;
    }
}
