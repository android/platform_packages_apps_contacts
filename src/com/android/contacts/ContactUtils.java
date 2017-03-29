/*
 * Copyright (C) 2017, The Linux Foundation. All Rights Reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are
 met:
     * Redistributions of source code must retain the above copyright
       notice, this list of conditions and the following disclaimer.
     * Redistributions in binary form must reproduce the above
       copyright notice, this list of conditions and the following
       disclaimer in the documentation and/or other materials provided
       with the distribution.
     * Neither the name of The Linux Foundation nor the names of its
       contributors may be used to endorse or promote products derived
       from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */

package com.android.contacts;

import android.accounts.Account;
import android.content.Context;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.net.Uri;

import com.android.contacts.model.account.SimAccountType;
import com.android.contacts.SimContactsConstants;

import org.codeaurora.wrapper.UiccPhoneBookController_Wrapper;

import java.util.ArrayList;
/**
 * Shared static SIM contact methods.
 */
public class ContactUtils {

    private static final String TAG = "ContactUtils";
    private static final int NAME_POS = 0;
    private static final int NUMBER_POS = 1;
    private static final int EMAIL_POS = 2;
    private static final int ANR_POS = 3;
    private static final int ADN_COUNT_POS = 0;
    private static final int ADN_USED_POS = 1;
    private static final int EMAIL_COUNT_POS = 2;
    private static final int EMAIL_USED_POS = 3;
    private static final int ANR_COUNT_POS = 4;
    private static final int ANR_USED_POS = 5;
    public static final int NAME_LENGTH_POS = 6;
    public static final int NUMBER_LENGTH_POS = 7;
    public static final int EMAIL_LENGTH_POS = 8;
    public static final int ANR_LENGTH_POS = 9;

    public static int getSubscription(String accountType, String accountName) {
        int subscription = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        if (accountType == null || accountName == null)
            return subscription;
        if (accountType.equals(SimAccountType.ACCOUNT_TYPE)) {
            if (accountName.equals(SimContactsConstants.SIM_NAME)
                    || accountName.equals(SimContactsConstants.SIM_NAME_1)) {
                subscription = SimContactsConstants.SLOT1;
            } else if (accountName.equals(SimContactsConstants.SIM_NAME_2)) {
                subscription = SimContactsConstants.SLOT2;
            }
        }
        return subscription;
    }

    public static Account getAcount(Context c , int slot) {
        Account account = null;
        TelephonyManager tm = (TelephonyManager) c
                .getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getPhoneCount() > 1) {
            if (slot == SimContactsConstants.SLOT1) {
                account = new Account(SimContactsConstants.SIM_NAME_1,
                        SimAccountType.ACCOUNT_TYPE);
            } else if (slot == SimContactsConstants.SLOT2) {
                account = new Account(SimContactsConstants.SIM_NAME_2,
                        SimAccountType.ACCOUNT_TYPE);
            }
        } else {
            if (slot == SimContactsConstants.SLOT1)
                account = new Account(SimContactsConstants.SIM_NAME,
                        SimAccountType.ACCOUNT_TYPE);
        }
        return account;
    }

    public static int[] getAdnRecordsCapacity(Context c, int slot) {
        int subId = getActiveSubId(c, slot);
        return UiccPhoneBookController_Wrapper
                .getAdnRecordsCapacityForSubscriber(subId);
    }

    /**
     * Returns the subscription's card can save anr or not.
     */
    public static boolean canSaveAnr(Context c, int slot) {
        int adnCapacity[] = getAdnRecordsCapacity(c, slot);
        return adnCapacity[ANR_COUNT_POS] > 0;
    }

    /**
     * Returns the subscription's card can save email or not.
     */
    public static boolean canSaveEmail(Context c, int slot) {
        int adnCapacity[] = getAdnRecordsCapacity(c, slot);
        return adnCapacity[EMAIL_COUNT_POS] > 0;
    }

    public static int getOneSimAnrCount(Context c, int slot) {
        int count = 0;
        int adnCapacity[] = getAdnRecordsCapacity(c, slot);
        int anrCount = adnCapacity[ANR_COUNT_POS];
        int adnCount = adnCapacity[ADN_COUNT_POS];
        if (adnCount > 0) {
            count = anrCount % adnCount != 0 ? (anrCount / adnCount + 1)
                    : (anrCount / adnCount);
        }
        return count;
    }

    public static int getOneSimEmailCount(Context c, int slot) {
        int count = 0;
        int adnCapacity[] = getAdnRecordsCapacity(c, slot);
        int emailCount = adnCapacity[EMAIL_COUNT_POS];
        int adnCount = adnCapacity[ADN_COUNT_POS];
        if (adnCount > 0) {
            count = emailCount % adnCount != 0 ? (emailCount / adnCount + 1)
                    : (emailCount / adnCount);
        }
        return count;
    }

    public static int getSimFreeCount(Context context, int slot) {
        int adnCapacity[] = getAdnRecordsCapacity(context, slot);
        int count = adnCapacity[ADN_COUNT_POS]-adnCapacity[ADN_USED_POS];
        Log.d(TAG, "spare adn:" + count);
        return count;
    }

    public static int getSpareAnrCount(Context c, int slot) {
        int adnCapacity[] = getAdnRecordsCapacity(c, slot);
        int spareCount = adnCapacity[ANR_COUNT_POS]-adnCapacity[ANR_USED_POS];
        Log.d(TAG, "spare anr:" + spareCount);
        return spareCount;
    }

    public static int getSpareEmailCount(Context c, int slot) {
        int adnCapacity[] = getAdnRecordsCapacity(c, slot);
        int spareCount = adnCapacity[EMAIL_COUNT_POS]-adnCapacity[EMAIL_USED_POS];
        Log.d(TAG, "spare email:" + spareCount);
        return spareCount;
    }

    public static int getActiveSubId(Context c , int slot) {
        SubscriptionInfo subInfoRecord = null;
        try {
            SubscriptionManager sm = SubscriptionManager.from(c);
            subInfoRecord = sm.getActiveSubscriptionInfoForSimSlotIndex(slot);
        } catch (Exception e) {
        }
        if (subInfoRecord != null)
            return subInfoRecord.getSubscriptionId();
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public static int getActiveSlotId(Context c , int subId) {
        SubscriptionInfo subInfoRecord = null;
        try {
            SubscriptionManager sm = SubscriptionManager.from(c);
            subInfoRecord = sm.getActiveSubscriptionInfo(subId);
        } catch (Exception e) {
        }
        if (subInfoRecord != null)
            return subInfoRecord.getSimSlotIndex();
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    public static boolean insertToPhone(String[] values, Context c, int slot) {
        Account account = getAcount(c, slot);
        final String name = values[NAME_POS];
        final String phoneNumber = values[NUMBER_POS];
        final String emailAddresses = values[EMAIL_POS];
        final String anrs = values[ANR_POS];
        final String[] emailAddressArray;
        final String[] anrArray;
        boolean success = true;
        if (!TextUtils.isEmpty(emailAddresses)) {
            emailAddressArray = emailAddresses.split(",");
        } else {
            emailAddressArray = null;
        }
        if (!TextUtils.isEmpty(anrs)) {
            anrArray = anrs.split(SimContactsConstants.ANR_SEP);
        } else {
            anrArray = null;
        }
        Log.d(TAG, "insertToPhone: name= " + name + ", phoneNumber= "
                + phoneNumber + ", emails= " + emailAddresses + ", anrs= "
                + anrs + ", account= " + account);
        final ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        ContentProviderOperation.Builder builder = ContentProviderOperation
                .newInsert(RawContacts.CONTENT_URI);
        builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);

        if (account != null) {
            builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
            builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
        }
        operationList.add(builder.build());

        // do not allow empty value insert into database.
        if (!TextUtils.isEmpty(name)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            builder.withValue(StructuredName.GIVEN_NAME, name);
            operationList.add(builder.build());
        }
        if (!TextUtils.isEmpty(phoneNumber)) {
            builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
            builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
            builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
            builder.withValue(Phone.NUMBER, phoneNumber);
            builder.withValue(Data.IS_PRIMARY, 1);
            operationList.add(builder.build());
        }
        if (anrArray != null) {
            for (String anr : anrArray) {
                if (!TextUtils.isEmpty(anr)) {
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    builder.withValueBackReference(Phone.RAW_CONTACT_ID, 0);
                    builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                    builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                    builder.withValue(Phone.NUMBER, anr);
                    operationList.add(builder.build());
                }
            }
        }
        if (emailAddressArray != null) {
            for (String emailAddress : emailAddressArray) {
                if (!TextUtils.isEmpty(emailAddress)) {
                    builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                    builder.withValueBackReference(Email.RAW_CONTACT_ID, 0);
                    builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                    builder.withValue(Email.ADDRESS, emailAddress);
                    operationList.add(builder.build());
                }
            }
        }

        try {
            ContentProviderResult[] results =
                    c.getContentResolver().applyBatch(ContactsContract.AUTHORITY, operationList);
            for (ContentProviderResult result: results) {
                if (result.uri == null) {
                    success = false;
                    break;
                }
            }
            return success;
        } catch (Exception e) {
            Log.e(TAG, String.format("%s: %s", e.toString(), e.getMessage()));
            return false;
        }
    }

    public static Uri insertToCard(Context context, String name, String number, String emails,
            String anrNumber, int slot) {
        return insertToCard(context, name, number, emails, anrNumber,slot, true);
    }

    public static Uri insertToCard(Context context, String name, String number, String emails,
            String anrNumber, int slot, boolean insertToPhone) {
        Uri result;
        ContentValues mValues = new ContentValues();
        mValues.clear();
        mValues.put(SimContactsConstants.STR_TAG, name);
        if (!TextUtils.isEmpty(number)) {
            number = PhoneNumberUtils.stripSeparators(number);
            mValues.put(SimContactsConstants.STR_NUMBER, number);
        }
        if (!TextUtils.isEmpty(emails)) {
            mValues.put(SimContactsConstants.STR_EMAILS, emails);
        }
        if (!TextUtils.isEmpty(anrNumber)) {
            anrNumber = anrNumber.replaceAll("[^0123456789PWN\\,\\;\\*\\#\\+\\:]", "");
            mValues.put(SimContactsConstants.STR_ANRS, anrNumber);
        }

        SimContactsOperation mSimContactsOperation = new SimContactsOperation(context);
        result = mSimContactsOperation.insert(mValues, slot);
        if (result != null) {
            if (insertToPhone) {
                // we should import the contact to the sim account at the same
                // time.
                String[] value = new String[] { name, number, emails, anrNumber };
                insertToPhone(value, context, slot);
            }
        } else {
            Log.e(TAG, "export contact: [" + name + ", " + number + ", " + emails + "] to slot "
                    + slot + " failed");
        }
        return result;
    }

    //judge the max length of number,anr,email,name for sim contact
    public static boolean isInValidData(String data, int maxLength) {
        if (!TextUtils.isEmpty(data)) {
            if (data.getBytes().length > data.length()) {
                if (data.length() > ((maxLength - 1) / 2))
                    return true;
            } else if (data.length() > maxLength)
                return true;
        }
        return false;
    }
}
