/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Requests permissions that are not absolutely required by the calling Activity;
 * if permissions are denied, the calling Activity is still restarted.
 *
 * Activities that have a set of permissions that must be granted in order for the Activity to
 * function propertly should call
 * {@link RequestPermissionsActivity#startPermissionActivity(Activity, String[], Class)}
 * before calling {@link RequestDesiredPermissionsActivity#startPermissionActivity(Activity)}.
 */
public class RequestDesiredPermissionsActivity extends RequestPermissionsActivityBase {

    private static String[] sDesiredPermissions;

    @Override
    protected String[] getPermissions() {
        return getPermissions(getPackageManager());
    }

    /**
     * If any desired permission that Contacts app needs are missing, open an Activity
     * to prompt user for these permissions. After that calling activity is restarted
     * and in the second run permission check is skipped.
     *
     * This is designed to be called inside {@link android.app.Activity#onCreate}
     */
    public static boolean startPermissionActivity(Activity activity) {
        final Bundle extras = activity.getIntent().getExtras();
        if (extras != null && extras.getBoolean(EXTRA_STARTED_PERMISSIONS_ACTIVITY, false)) {
            return false;
        }
        return startPermissionActivity(activity,
                getPermissions(activity.getPackageManager()),
                RequestDesiredPermissionsActivity.class);
    }

    private static String[] getPermissions(PackageManager packageManager) {
        if (sDesiredPermissions == null) {
            final List<String> permissions = new ArrayList<>();
            // Calendar group
            permissions.add(permission.READ_CALENDAR);

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                // SMS group
                permissions.add(permission.READ_SMS);
            }
            sDesiredPermissions = permissions.toArray(new String[0]);
        }
        return sDesiredPermissions;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(mPreviousActivityIntent);
        overridePendingTransition(0, 0);

        finish();
        overridePendingTransition(0, 0);
    }
}