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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.android.contacts.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity that requests permissions needed for activities exported from Contacts.
 */
public class RequestPermissionsActivity extends RequestPermissionsActivityBase {

    public static final String BROADCAST_PERMISSIONS_GRANTED = "broadcastPermissionsGranted";

    private static String[] sRequiredPermissions;

    @Override
    protected String[] getPermissions() {
        return getPermissions(getPackageManager());
    }

    /**
     * Method to check if the required permissions are given.
     */
    public static boolean hasRequiredPermissions(Context context) {
        return hasPermissions(context, getPermissions(context.getPackageManager()));
    }

    public static boolean startPermissionActivityIfNeeded(Activity activity) {
        return startPermissionActivity(activity,
                getPermissions(activity.getPackageManager()),
                RequestPermissionsActivity.class);
    }

    private static String[] getPermissions(PackageManager packageManager) {
        if (sRequiredPermissions == null) {
            final List<String> permissions = new ArrayList<>();
            // Contacts group
            permissions.add(permission.GET_ACCOUNTS);
            permissions.add(permission.READ_CONTACTS);
            permissions.add(permission.WRITE_CONTACTS);

            if (packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
                // Phone group
                // These are only used in a few places such as QuickContactActivity and
                // ImportExportDialogFragment.  We work around missing this permission when
                // telephony is not available on the device (i.e. on tablets).
                permissions.add(permission.CALL_PHONE);
                permissions.add(permission.READ_PHONE_STATE);
            }
            sRequiredPermissions = permissions.toArray(new String[0]);
        }
        return sRequiredPermissions;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String permissions[], int[] grantResults) {
        if (permissions != null && permissions.length > 0
                && isAllGranted(permissions, grantResults)) {
            mPreviousActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (mIsCallerSelf) {
                startActivityForResult(mPreviousActivityIntent, 0);
            } else {
                startActivity(mPreviousActivityIntent);
            }
            finish();
            overridePendingTransition(0, 0);

            LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(BROADCAST_PERMISSIONS_GRANTED));
        } else {
            Toast.makeText(this, R.string.missing_required_permission, Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
