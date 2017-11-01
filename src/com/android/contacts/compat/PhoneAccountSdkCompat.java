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

import android.os.Bundle;
import android.telecom.PhoneAccount;

public class PhoneAccountSdkCompat {

    private static final String TAG = "PhoneAccountSdkCompat";

    public static final String EXTRA_CALL_SUBJECT_MAX_LENGTH =
            PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH;
    public static final String EXTRA_CALL_SUBJECT_CHARACTER_ENCODING =
            PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING;

    public static final int CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE =
            PhoneAccount.CAPABILITY_VIDEO_CALLING_RELIES_ON_PRESENCE;

    public static Bundle getExtras(PhoneAccount account) {
        return CompatUtils.isNCompatible() ? account.getExtras() : null;
    }
}
