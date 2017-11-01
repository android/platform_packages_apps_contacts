/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.android.contacts.vcard;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.util.Log;

import com.android.contacts.R;
import com.android.contacts.activities.RequestImportVCardPermissionsActivity;

import java.util.List;

/**
 * Shows a dialog confirming the export and asks actual vCard export to {@link VCardService}
 *
 * This Activity first connects to VCardService and ask an available file name and shows it to
 * a user. After the user's confirmation, it send export request with the file name, assuming the
 * file name is not reserved yet.
 */
public class ExportVCardActivity extends Activity implements ServiceConnection,
        DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private static final String LOG_TAG = "VCardExport";
    protected static final boolean DEBUG = VCardService.DEBUG;
    private static final int REQUEST_CREATE_DOCUMENT = 100;

    /**
     * True when this Activity is connected to {@link VCardService}.
     *
     * Should be touched inside synchronized block.
     */
    protected boolean mConnected;

    /**
     * True when users need to do something and this Activity should not disconnect from
     * VCardService. False when all necessary procedures are done (including sending export request)
     * or there's some error occured.
     */
    private volatile boolean mProcessOngoing = true;

    protected VCardService mService;
    private static final BidiFormatter mBidiFormatter = BidiFormatter.getInstance();

    // String for storing error reason temporarily.
    private String mErrorReason;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        if (RequestImportVCardPermissionsActivity.startPermissionActivityIfNeeded(this)) {
            return;
        }

        if (!hasExportIntentHandler()) {
            Log.e(LOG_TAG, "Couldn't find export intent handler");
            showErrorDialog();
            return;
        }

        connectVCardService();
    }

    private void connectVCardService() {
        final String callingActivity = getIntent().getExtras()
                .getString(VCardCommonArguments.ARG_CALLING_ACTIVITY);
        Intent intent = new Intent(this, VCardService.class);
        intent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY, callingActivity);

        if (startService(intent) == null) {
            Log.e(LOG_TAG, "Failed to start vCard service");
            showErrorDialog();
            return;
        }

        if (!bindService(intent, this, Context.BIND_AUTO_CREATE)) {
            Log.e(LOG_TAG, "Failed to connect to vCard service.");
            showErrorDialog();
        }
        // Continued to onServiceConnected()
    }

    private boolean hasExportIntentHandler() {
        final Intent intent = getCreateDocIntent();
        final List<ResolveInfo> receivers = getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return receivers != null && receivers.size() > 0;
    }

    private Intent getCreateDocIntent() {
        final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(VCardService.X_VCARD_MIME_TYPE);
        intent.putExtra(Intent.EXTRA_TITLE, mBidiFormatter.unicodeWrap(
                getString(R.string.exporting_vcard_filename), TextDirectionHeuristics.LTR));
        return intent;
    }

    private void showErrorDialog() {
        mErrorReason = getString(R.string.fail_reason_unknown);
        showDialog(R.id.dialog_fail_to_export_with_reason);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CREATE_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK && mService != null &&
                    data != null && data.getData() != null) {
                final Uri targetFileName = data.getData();
                if (DEBUG) Log.d(LOG_TAG, "exporting to " + targetFileName);
                final ExportRequest request = new ExportRequest(targetFileName);
                // The connection object will call finish().
                mService.handleExportRequest(request, new NotificationImportExportListener(
                        ExportVCardActivity.this));
            } else if (DEBUG) {
                if (mService == null) {
                    Log.d(LOG_TAG, "No vCard service.");
                } else {
                    Log.d(LOG_TAG, "create document cancelled or no data returned");
                }
            }
            finish();
        }
    }

    @Override
    public synchronized void onServiceConnected(ComponentName name, IBinder binder) {
        if (DEBUG) Log.d(LOG_TAG, "connected to service, requesting a destination file name");
        mConnected = true;
        mService = ((VCardService.MyBinder) binder).getService();

        // Have the user choose where vcards will be exported to
        startActivityForResult(getCreateDocIntent(), REQUEST_CREATE_DOCUMENT);
    }

    // Use synchronized since we don't want to call finish() just after this call.
    @Override
    public synchronized void onServiceDisconnected(ComponentName name) {
        if (DEBUG) Log.d(LOG_TAG, "onServiceDisconnected()");
        mService = null;
        mConnected = false;
        if (mProcessOngoing) {
            // Unexpected disconnect event.
            Log.w(LOG_TAG, "Disconnected from service during the process ongoing.");
            showErrorDialog();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        if (id == R.id.dialog_fail_to_export_with_reason) {
            mProcessOngoing = false;
            return new AlertDialog.Builder(this)
                    .setTitle(R.string.exporting_contact_failed_title)
                    .setMessage(getString(R.string.exporting_contact_failed_message,
                            mErrorReason != null ? mErrorReason :
                                    getString(R.string.fail_reason_unknown)))
                    .setPositiveButton(android.R.string.ok, this)
                    .setOnCancelListener(this)
                    .create();
        }
        return super.onCreateDialog(id, bundle);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        if (id == R.id.dialog_fail_to_export_with_reason) {
            ((AlertDialog)dialog).setMessage(mErrorReason);
        } else {
            super.onPrepareDialog(id, dialog, args);
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (DEBUG) Log.d(LOG_TAG, "ExportVCardActivity#onClick() is called");
        finish();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (DEBUG) Log.d(LOG_TAG, "ExportVCardActivity#onCancel() is called");
        mProcessOngoing = false;
        finish();
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        mProcessOngoing = false;
        super.unbindService(conn);
    }

    @Override
    protected void onDestroy() {
        if (mConnected) {
            unbindService(this);
            mConnected = false;
        }
        super.onDestroy();
    }

    /**
     * Returns the display name for the given openable Uri or null if it could not be resolved. */
    static String getOpenableUriDisplayName(Context context, Uri uri) {
        if (uri == null) return null;
        final Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            }
        } finally {
            if (cursor != null)  {
                cursor.close();
            }
        }
        return null;
    }
}
