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

import android.net.Uri;
import androidx.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;

public class TelephonyManagerCompat {
    public static final String TELEPHONY_MANAGER_CLASS = "android.telephony.TelephonyManager";

    /**
     * @param telephonyManager The telephony manager instance to use for method calls.
     * @return true if the current device is "voice capable".
     * <p>
     * "Voice capable" means that this device supports circuit-switched
     * (i.e. voice) phone calls over the telephony network, and is allowed
     * to display the in-call UI while a cellular voice call is active.
     * This will be false on "data only" devices which can't make voice
     * calls and don't support any in-call UI.
     * <p>
     * Note: the meaning of this flag is subtly different from the
     * PackageManager.FEATURE_TELEPHONY system feature, which is available
     * on any device with a telephony radio, even if the device is
     * data-only.
     */
    public static boolean isVoiceCapable(@Nullable TelephonyManager telephonyManager) {
        if (telephonyManager == null) {
            return false;
        }
        if (CompatUtils.isLollipopMr1Compatible()
                || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS, "isVoiceCapable")) {
            // isVoiceCapable was unhidden in L-MR1
            return telephonyManager.isVoiceCapable();
        }
        final int phoneType = telephonyManager.getPhoneType();
        return phoneType == TelephonyManager.PHONE_TYPE_CDMA ||
                phoneType == TelephonyManager.PHONE_TYPE_GSM;
    }

    /**
     * Returns the number of phones available.
     * Returns 1 for Single standby mode (Single SIM functionality)
     * Returns 2 for Dual standby mode.(Dual SIM functionality)
     *
     * Returns 1 if the method or telephonyManager is not available.
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     */
    public static int getPhoneCount(@Nullable TelephonyManager telephonyManager) {
        if (telephonyManager == null) {
            return 1;
        }
        if (CompatUtils.isMarshmallowCompatible() || CompatUtils
                .isMethodAvailable(TELEPHONY_MANAGER_CLASS, "getPhoneCount")) {
            return telephonyManager.getPhoneCount();
        }
        return 1;
    }

    /**
     * Returns the unique device ID of a subscription, for example, the IMEI for
     * GSM and the MEID for CDMA phones. Return null if device ID is not available.
     *
     * <p>Requires Permission:
     *   {@link android.Manifest.permission#READ_PHONE_STATE READ_PHONE_STATE}
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     * @param slotId of which deviceID is returned
     */
    public static String getDeviceId(@Nullable TelephonyManager telephonyManager, int slotId) {
        if (telephonyManager == null) {
            return null;
        }
        if (CompatUtils.isMarshmallowCompatible()
                || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS,
                        "getDeviceId", Integer.class)) {
            return telephonyManager.getDeviceId(slotId);
        }
        return null;
    }

    /**
     * Whether the phone supports TTY mode.
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     * @return {@code true} if the device supports TTY mode, and {@code false} otherwise.
     */

    public static boolean isTtyModeSupported(@Nullable TelephonyManager telephonyManager) {
        if (telephonyManager == null) {
            return false;
        }
        if (CompatUtils.isMarshmallowCompatible()
                || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS, "isTtyModeSupported")) {
            return telephonyManager.isTtyModeSupported();
        }
        return false;
    }

    /**
     * Whether the phone supports hearing aid compatibility.
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     * @return {@code true} if the device supports hearing aid compatibility, and {@code false}
     * otherwise.
     */
    public static boolean isHearingAidCompatibilitySupported(
            @Nullable TelephonyManager telephonyManager) {
        if (telephonyManager == null) {
            return false;
        }
        if (CompatUtils.isMarshmallowCompatible()
                || CompatUtils.isMethodAvailable(TELEPHONY_MANAGER_CLASS,
                        "isHearingAidCompatibilitySupported")) {
            return telephonyManager.isHearingAidCompatibilitySupported();
        }
        return false;
    }

    /**
     * Returns the URI for the per-account voicemail ringtone set in Phone settings.
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     * @param accountHandle The handle for the {@link android.telecom.PhoneAccount} for which to
     * retrieve the voicemail ringtone.
     * @return The URI for the ringtone to play when receiving a voicemail from a specific
     * PhoneAccount.
     */
    @Nullable
    public static Uri getVoicemailRingtoneUri(TelephonyManager telephonyManager,
            PhoneAccountHandle accountHandle) {
        if (!CompatUtils.isNCompatible()) {
            return null;
        }
        return TelephonyManagerSdkCompat
                .getVoicemailRingtoneUri(telephonyManager, accountHandle);
    }

    /**
     * Returns whether vibration is set for voicemail notification in Phone settings.
     *
     * @param telephonyManager The telephony manager instance to use for method calls.
     * @param accountHandle The handle for the {@link android.telecom.PhoneAccount} for which to
     * retrieve the voicemail vibration setting.
     * @return {@code true} if the vibration is set for this PhoneAccount, {@code false} otherwise.
     */
    public static boolean isVoicemailVibrationEnabled(TelephonyManager telephonyManager,
            PhoneAccountHandle accountHandle) {
        if (!CompatUtils.isNCompatible()) {
            return true;
        }
        return TelephonyManagerSdkCompat
                .isVoicemailVibrationEnabled(telephonyManager, accountHandle);
    }
}
