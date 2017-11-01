/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.sip.SipManager;
import android.provider.MediaStore;
import android.provider.Telephony;
import android.telephony.TelephonyManager;

import com.android.contacts.ContactsUtils;
import com.android.contacts.compat.TelephonyManagerCompat;

import java.util.List;

/**
 * Provides static functions to quickly test the capabilities of this device. The static
 * members are not safe for threading
 */
public final class PhoneCapabilityTester {
    private static boolean sIsInitialized;
    private static boolean sIsPhone;
    private static boolean sIsSipPhone;

    /**
     * Tests whether the Intent has a receiver registered. This can be used to show/hide
     * functionality (like Phone, SMS)
     */
    public static boolean isIntentRegistered(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        final List<ResolveInfo> receiverList = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receiverList.size() > 0;
    }

    /**
     * Returns true if this device can be used to make phone calls
     */
    public static boolean isPhone(Context context) {
        if (!sIsInitialized) initialize(context);
        // Is the device physically capabable of making phone calls?
        return sIsPhone;
    }

    private static void initialize(Context context) {
        sIsPhone = TelephonyManagerCompat.isVoiceCapable(
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE));
        sIsSipPhone = sIsPhone && SipManager.isVoipSupported(context);
        sIsInitialized = true;
    }

    /**
     * Returns true if this device can be used to make sip calls
     */
    public static boolean isSipPhone(Context context) {
        if (!sIsInitialized) initialize(context);
        return sIsSipPhone;
    }

    /**
     * Returns the component name to use for sending to sms or null.
     */
    public static ComponentName getSmsComponent(Context context) {
        String smsPackage = Telephony.Sms.getDefaultSmsPackage(context);
        if (smsPackage != null) {
            final PackageManager packageManager = context.getPackageManager();
            final Intent intent = new Intent(Intent.ACTION_SENDTO,
                    Uri.fromParts(ContactsUtils.SCHEME_SMSTO, "", null));
            final List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
            for (ResolveInfo resolveInfo : resolveInfos) {
                if (smsPackage.equals(resolveInfo.activityInfo.packageName)) {
                    return new ComponentName(smsPackage, resolveInfo.activityInfo.name);
                }
            }
        }
        return null;
    }

    /**
     * Returns true if there is a camera on the device
     */
    public static boolean isCameraIntentRegistered(Context context) {
        final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        return isIntentRegistered(context, intent);
    }
}
