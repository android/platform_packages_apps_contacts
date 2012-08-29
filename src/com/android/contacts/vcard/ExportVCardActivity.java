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

import com.android.contacts.R;
import com.android.vcard.VCardComposer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

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
    private static final boolean DEBUG = VCardService.DEBUG;

    /**
     * Handler used when some Message has come from {@link VCardService}.
     */
    private class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (DEBUG) Log.d(LOG_TAG, "IncomingHandler received message.");

            if (msg.arg1 != 0) {
                Log.i(LOG_TAG, "Message returned from vCard server contains error code.");
                if (msg.obj != null) {
                    mErrorReason = (String)msg.obj;
                }
                showDialog(msg.arg1);
                return;
            }

            switch (msg.what) {
            case VCardService.MSG_SET_AVAILABLE_EXPORT_DESTINATION:
                if (msg.obj == null) {
                    Log.w(LOG_TAG, "Message returned from vCard server doesn't contain valid path");
                    mErrorReason = getString(R.string.fail_reason_unknown);
                    showDialog(R.id.dialog_fail_to_export_with_reason);
                } else {
                    mTargetFileName = (String)msg.obj;
                    if (TextUtils.isEmpty(mTargetFileName)) {
                        Log.w(LOG_TAG, "Destination file name coming from vCard service is empty.");
                        mErrorReason = getString(R.string.fail_reason_unknown);
                        showDialog(R.id.dialog_fail_to_export_with_reason);
                    } else {
                        if (DEBUG) {
                            Log.d(LOG_TAG,
                                    String.format("Target file name is set (%s). " +
                                            "Show confirmation dialog", mTargetFileName));
                        }
                        showDialog(R.id.dialog_export_confirmation);
                    }
                }
                break;
            default:
                Log.w(LOG_TAG, "Unknown message type: " + msg.what);
                super.handleMessage(msg);
            }
        }
    }

    /**
     * True when this Activity is connected to {@link VCardService}.
     *
     * Should be touched inside synchronized block.
     */
    private boolean mConnected;

    /**
     * True when users need to do something and this Activity should not disconnect from
     * VCardService. False when all necessary procedures are done (including sending export request)
     * or there's some error occured.
     */
    private volatile boolean mProcessOngoing = true;

    private VCardService mService;
    private final Messenger mIncomingMessenger = new Messenger(new IncomingHandler());

    // Used temporarily when asking users to confirm the file name
    private String mTargetFileName;

    // String for storing error reason temporarily.
    private String mErrorReason;

    private class ExportConfirmationListener implements DialogInterface.OnClickListener {
        private final Uri mDestinationUri;

        public ExportConfirmationListener(String path) {
            this(Uri.parse("file://" + path));
        }

        public ExportConfirmationListener(Uri uri) {
            mDestinationUri = uri;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (DEBUG) {
                    Log.d(LOG_TAG,
                            String.format("Try sending export request (uri: %s)", mDestinationUri));
                }
                final ExportRequest request = new ExportRequest(mDestinationUri);
                // The connection object will call finish().
                mService.handleExportRequest(request, new NotificationImportExportListener(
                        ExportVCardActivity.this));
            }
            unbindAndFinish();
        }
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // Check directory is available.
        final File targetDirectory = Environment.getExternalStorageDirectory();
        if (!(targetDirectory.exists() &&
                targetDirectory.isDirectory() &&
                targetDirectory.canRead()) &&
                !targetDirectory.mkdirs()) {
            showDialog(R.id.dialog_sdcard_not_found);
            return;
        }

        Intent intent = new Intent(this, VCardService.class);

        if (startService(intent) == null) {
            Log.e(LOG_TAG, "Failed to start vCard service");
            mErrorReason = getString(R.string.fail_reason_unknown);
            showDialog(R.id.dialog_fail_to_export_with_reason);
            return;
        }

        if (!bindService(intent, this, Context.BIND_AUTO_CREATE)) {
            Log.e(LOG_TAG, "Failed to connect to vCard service.");
            mErrorReason = getString(R.string.fail_reason_unknown);
            showDialog(R.id.dialog_fail_to_export_with_reason);
        }
        // Continued to onServiceConnected()
    }

    @Override
    public synchronized void onServiceConnected(ComponentName name, IBinder binder) {
        if (DEBUG) Log.d(LOG_TAG, "connected to service, requesting a destination file name");
        mConnected = true;
        mService = ((VCardService.MyBinder) binder).getService();
        mService.handleRequestAvailableExportDestination(mIncomingMessenger);
        // Wait until MSG_SET_AVAILABLE_EXPORT_DESTINATION message is available.
    }

    // Use synchronized since we don't want to call unbindAndFinish() just after this call.
    @Override
    public synchronized void onServiceDisconnected(ComponentName name) {
        if (DEBUG) Log.d(LOG_TAG, "onServiceDisconnected()");
        mService = null;
        mConnected = false;
        if (mProcessOngoing) {
            // Unexpected disconnect event.
            Log.w(LOG_TAG, "Disconnected from service during the process ongoing.");
            mErrorReason = getString(R.string.fail_reason_unknown);
            showDialog(R.id.dialog_fail_to_export_with_reason);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.dialog_export_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.confirm_export_title)
                        .setMessage(getString(R.string.confirm_export_message, mTargetFileName))
                        .setPositiveButton(android.R.string.ok,
                                new ExportConfirmationListener(mTargetFileName))
                        .setNegativeButton(android.R.string.cancel, this)
                        .setOnCancelListener(this)
                        .create();
            }
            case R.string.fail_reason_too_many_vcard: {
                mProcessOngoing = false;
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.exporting_contact_failed_title)
                        .setMessage(getString(R.string.exporting_contact_failed_message,
                                getString(R.string.fail_reason_too_many_vcard)))
                        .setPositiveButton(android.R.string.ok, this)
                        .create();
            }
            case R.id.dialog_fail_to_export_with_reason: {
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
            case R.id.dialog_sdcard_not_found: {
                mProcessOngoing = false;
                return new AlertDialog.Builder(this)
                        .setIconAttribute(android.R.attr.alertDialogIcon)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, this).create();
            }
        }
        return super.onCreateDialog(id, bundle);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        if (id == R.id.dialog_fail_to_export_with_reason) {
            ((AlertDialog)dialog).setMessage(mErrorReason);
        } else if (id == R.id.dialog_export_confirmation) {
            ((AlertDialog)dialog).setMessage(
                    getString(R.string.confirm_export_message, mTargetFileName));
        } else {
            super.onPrepareDialog(id, dialog, args);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (!isFinishing()) {
            unbindAndFinish();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (DEBUG) Log.d(LOG_TAG, "ExportVCardActivity#onClick() is called");
        unbindAndFinish();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        if (DEBUG) Log.d(LOG_TAG, "ExportVCardActivity#onCancel() is called");
        mProcessOngoing = false;
        unbindAndFinish();
    }

    @Override
    public void unbindService(ServiceConnection conn) {
        mProcessOngoing = false;
        super.unbindService(conn);
    }

    private synchronized void unbindAndFinish() {
        if (mConnected) {
            unbindService(this);
            mConnected = false;
        }
        finish();
    }
}
