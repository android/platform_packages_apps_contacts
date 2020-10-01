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
 * limitations under the License
 */

package com.android.contacts;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.compat.CompatUtils;
import com.android.contacts.compat.PhoneAccountSdkCompat;
import com.android.contacts.util.PermissionsUtil;
import com.android.contacts.util.PhoneNumberHelper;
import com.android.contactsbind.FeedbackHelper;
import com.android.contactsbind.experiments.Flags;
import com.android.phone.common.PhoneConstants;

import java.util.List;

/**
 * Utilities related to calls that can be used by non system apps. These
 * use {@link Intent#ACTION_CALL} instead of ACTION_CALL_PRIVILEGED.
 *
 * The privileged version of this util exists inside Dialer.
 */
public class CallUtil {

    public static final String TAG = "CallUtil";

    /**
     * Indicates that the video calling is not available.
     */
    public static final int VIDEO_CALLING_DISABLED = 0;

    /**
     * Indicates that video calling is enabled, regardless of presence status.
     */
    public static final int VIDEO_CALLING_ENABLED = 1;

    /**
     * Indicates that video calling is enabled, but the availability of video call affordances is
     * determined by the presence status associated with contacts.
     */
    public static final int VIDEO_CALLING_PRESENCE = 2;

    /** {@link PhoneAccount#EXTRA_SUPPORTS_VIDEO_CALLING_FALLBACK} */
    private static final String EXTRA_SUPPORTS_VIDEO_CALLING_FALLBACK =
            "android.telecom.extra.SUPPORTS_VIDEO_CALLING_FALLBACK";

    /** {@link CarrierConfigManager#CONFIG_ALLOW_VIDEO_CALLING_FALLBACK} */
    private static final String CONFIG_ALLOW_VIDEO_CALLING_FALLBACK =
            "allow_video_calling_fallback_bool";

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallWithSubjectIntent(String number,
            PhoneAccountHandle phoneAccountHandle, String callSubject) {

        final Intent intent = getCallIntent(getCallUri(number));
        intent.putExtra(TelecomManager.EXTRA_CALL_SUBJECT, callSubject);
        if (phoneAccountHandle != null) {
            intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        }
        return intent;
    }

    /**
     * Return an Intent for making a phone call. Scheme (e.g. tel, sip) will be determined
     * automatically.
     */
    public static Intent getCallIntent(String number) {
        Uri uri = getCallUri(number);
        return PhoneNumberUtils.isEmergencyNumber(number)
                ? getCallIntentForEmergencyNumber(uri) : getCallIntent(uri);
    }

    /**
     * Return an Intent to directly start Dialer when calling an emergency number. Scheme is always
     * PhoneAccount.SCHEME_TEL.
     */
    private static Intent getCallIntentForEmergencyNumber(Uri uri) {
        return new Intent(Intent.ACTION_DIAL, uri);
    }

    /**
     * Return an Intent for making a phone call. A given Uri will be used as is (without any
     * quick check).
     */
    public static Intent getCallIntent(Uri uri) {
        return new Intent(Intent.ACTION_CALL, uri);
    }

    /**
     * A variant of {@link #getCallIntent} for starting a video call.
     */
    public static Intent getVideoCallIntent(String number, String callOrigin) {
        final Intent intent = new Intent(Intent.ACTION_CALL, getCallUri(number));
        intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_BIDIRECTIONAL);
        if (!TextUtils.isEmpty(callOrigin)) {
            intent.putExtra(PhoneConstants.EXTRA_CALL_ORIGIN, callOrigin);
        }
        return intent;
    }

    /**
     * Return Uri with an appropriate scheme, accepting both SIP and usual phone call
     * numbers.
     */
    public static Uri getCallUri(String number) {
        if (PhoneNumberHelper.isUriNumber(number)) {
             return Uri.fromParts(PhoneAccount.SCHEME_SIP, number, null);
        }
        return Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null);
    }

    /**
     * Determines if video calling is available, and if so whether presence checking is available
     * as well.
     *
     * Returns a bitmask with {@link #VIDEO_CALLING_ENABLED} to indicate that video calling is
     * available, and {@link #VIDEO_CALLING_PRESENCE} if presence indication is also available.
     *
     * @param context The context
     * @return A bit-mask describing the current video capabilities.
     */
    public static int getVideoCallingAvailability(Context context) {
        if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                || !CompatUtils.isVideoCompatible()) {
            return VIDEO_CALLING_DISABLED;
        }
        TelecomManager telecommMgr = (TelecomManager)
                context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return VIDEO_CALLING_DISABLED;
        }

        try {
            List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
            for (PhoneAccountHandle accountHandle : accountHandles) {
                PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
                if (account != null) {
                    if (account.hasCapabilities(PhoneAccount.CAPABILITY_VIDEO_CALLING)) {
                        // Builds prior to N do not have presence support.
                        if (!CompatUtils.isVideoPresenceCompatible()) {
                            return VIDEO_CALLING_ENABLED;
                        }

                        int videoCapabilities = VIDEO_CALLING_ENABLED;
                        if (account.hasCapabilities(PhoneAccountSdkCompat
                                .CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE)) {
                            videoCapabilities |= VIDEO_CALLING_PRESENCE;
                        }
                        return videoCapabilities;
                    }
                }
            }
            return VIDEO_CALLING_DISABLED;
        } catch (SecurityException e) {
            FeedbackHelper.sendFeedback(context, TAG,
                    "Security exception when getting call capable phone accounts", e);
            return VIDEO_CALLING_DISABLED;
        }
    }

    /**
     * Determines if one of the call capable phone accounts defined supports calling with a subject
     * specified.
     *
     * @param context The context.
     * @return {@code true} if one of the call capable phone accounts supports calling with a
     *      subject specified, {@code false} otherwise.
     */
    public static boolean isCallWithSubjectSupported(Context context) {
        if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                || !CompatUtils.isCallSubjectCompatible()) {
            return false;
        }
        TelecomManager telecommMgr = (TelecomManager)
                context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }

        try {
            List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
            for (PhoneAccountHandle accountHandle : accountHandles) {
                PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
                if (account != null && account.hasCapabilities(PhoneAccount.CAPABILITY_CALL_SUBJECT)) {
                    return true;
                }
            }
            return false;
        } catch (SecurityException e) {
            FeedbackHelper.sendFeedback(context, TAG,
                    "Security exception when getting call capable phone accounts", e);
            return false;
        }

    }

    /**
     * Determines if we're able to use Tachyon as a fallback for video calling.
     *
     * @param context The context.
     * @return {@code true} if there exists a call capable phone account which supports using a
     * fallback for video calling, the carrier configuration supports a fallback, and the
     * experiment for using a fallback is enabled. Otherwise {@code false} is returned.
     */
    public static boolean isTachyonEnabled(Context context) {
        // Need to be able to read phone state, and be on at least N to check PhoneAccount extras.
        if (!PermissionsUtil.hasPermission(context, android.Manifest.permission.READ_PHONE_STATE)
                || !CompatUtils.isNCompatible()) {
            return false;
        }
        TelecomManager telecommMgr = (TelecomManager)
                context.getSystemService(Context.TELECOM_SERVICE);
        if (telecommMgr == null) {
            return false;
        }
        try {
            List<PhoneAccountHandle> accountHandles = telecommMgr.getCallCapablePhoneAccounts();
            for (PhoneAccountHandle accountHandle : accountHandles) {
                PhoneAccount account = telecommMgr.getPhoneAccount(accountHandle);
                if (account == null) {
                    continue;
                }
                // Check availability for the device config.
                final Bundle accountExtras = account.getExtras();
                final boolean deviceEnabled = accountExtras != null && accountExtras.getBoolean(
                        EXTRA_SUPPORTS_VIDEO_CALLING_FALLBACK);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Device video fallback config: " + deviceEnabled);
                }

                // Check availability from carrier config.
                final PersistableBundle carrierConfig = context.getSystemService(
                        CarrierConfigManager.class).getConfig();
                final boolean carrierEnabled =
                        carrierConfig != null && carrierConfig.getBoolean(
                                CONFIG_ALLOW_VIDEO_CALLING_FALLBACK);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Carrier video fallback config: " + carrierEnabled);
                }

                // Check experiment value.
                final boolean experimentEnabled = Flags.getInstance().getBoolean(
                        Experiments.QUICK_CONTACT_VIDEO_CALL);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Experiment video fallback config: " + experimentEnabled);
                }

                // All three checks above must be true to enable Tachyon calling.
                return deviceEnabled && carrierEnabled && experimentEnabled;
            }
            return false;
        } catch (SecurityException e) {
            FeedbackHelper.sendFeedback(context, TAG,
                    "Security exception when getting call capable phone accounts", e);
            return false;
        }
    }
}
