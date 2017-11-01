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

package com.android.contacts.activities;

import android.Manifest.permission;
import android.app.Activity;

/**
 * Activity that requests permissions needed for ImportVCardActivity.
 */
public class RequestImportVCardPermissionsActivity extends RequestPermissionsActivity {

    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            // Contacts group
            permission.GET_ACCOUNTS,
            permission.READ_CONTACTS,
            permission.WRITE_CONTACTS,
            // Storage group
            permission.READ_EXTERNAL_STORAGE,
    };

    @Override
    protected String[] getPermissions() {
        return REQUIRED_PERMISSIONS;
    }

    /**
     * If any permissions the Contacts app needs are missing, open an Activity
     * to prompt the user for these permissions. Moreover, finish the current activity.
     *
     * This is designed to be called inside {@link android.app.Activity#onCreate}
     *
     * @param isCallerSelf whether the vcard import was started from the contacts app itself.
     */
    public static boolean startPermissionActivity(Activity activity, boolean isCallerSelf) {
        return startPermissionActivity(activity, REQUIRED_PERMISSIONS, isCallerSelf,
                RequestImportVCardPermissionsActivity.class);
    }
}