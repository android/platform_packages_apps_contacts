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
package com.android.contacts.compat.telecom;

import android.app.Activity;
import android.content.Intent;
import androidx.annotation.Nullable;
import android.telecom.TelecomManager;

import com.android.contacts.compat.CompatUtils;

/**
 * Compatibility class for {@link android.telecom.TelecomManager}.
 */
public class TelecomManagerCompat {
    /**
     * Places a new outgoing call to the provided address using the system telecom service with
     * the specified intent.
     *
     * @param activity {@link Activity} used to start another activity for the given intent
     * @param telecomManager the {@link TelecomManager} used to place a call, if possible
     * @param intent the intent for the call
     */
    public static void placeCall(@Nullable Activity activity,
            @Nullable TelecomManager telecomManager, @Nullable Intent intent) {
        if (activity == null || telecomManager == null || intent == null) {
            return;
        }
        if (CompatUtils.isMarshmallowCompatible()) {
            telecomManager.placeCall(intent.getData(), intent.getExtras());
            return;
        }
        activity.startActivityForResult(intent, 0);
    }
}
