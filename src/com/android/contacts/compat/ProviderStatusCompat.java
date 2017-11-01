
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

import android.provider.ContactsContract.ProviderStatus;

/**
 * This class contains constants from the pre-M version of ContactsContract.ProviderStatus class
 * and also the mappings between pre-M constants and M constants for compatibility purpose,
 * because ProviderStatus class constant names and values changed and the class became visible in
 * API level 23.
 */
public class ProviderStatusCompat {
    /**
     * Not instantiable.
     */
    private ProviderStatusCompat() {
    }

    public static final boolean USE_CURRENT_VERSION = CompatUtils.isMarshmallowCompatible();

    public static final int STATUS_EMPTY = USE_CURRENT_VERSION ?
            ProviderStatus.STATUS_EMPTY : ProviderStatusCompat.STATUS_NO_ACCOUNTS_NO_CONTACTS;

    public static final int STATUS_BUSY = USE_CURRENT_VERSION ?
            ProviderStatus.STATUS_BUSY : ProviderStatusCompat.STATUS_UPGRADING;

    /**
     * Default status of the provider, using the actual constant to guard against errors
     */
    public static final int STATUS_NORMAL = ProviderStatus.STATUS_NORMAL;

    /**
     * The following three constants are from pre-M.
     *
     * The status used when the provider is in the process of upgrading.  Contacts
     * are temporarily unaccessible.
     */
    private static final int STATUS_UPGRADING = 1;

    /**
     * The status used during a locale change.
     */
    public static final int STATUS_CHANGING_LOCALE = 3;

    /**
     * The status that indicates that there are no accounts and no contacts
     * on the device.
     */
    private static final int STATUS_NO_ACCOUNTS_NO_CONTACTS = 4;
}
