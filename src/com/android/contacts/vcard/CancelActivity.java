/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.android.contacts.R;

/**
 * The Activity for canceling vCard import/export.
 */
public class CancelActivity extends Activity implements ServiceConnection {
    private final String LOG_TAG = "VCardCancel";

    /* package */ final static String JOB_ID = "job_id";
    /* package */ final static String DISPLAY_NAME = "display_name";

    /**
     * Type of the process to be canceled. Only used for choosing appropriate title/message.
     * Must be {@link VCardService#TYPE_IMPORT} or {@link VCardService#TYPE_EXPORT}.
     */
    /* package */ final static String TYPE = "type";

    private class RequestCancelListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            bindService(new Intent(CancelActivity.this,
                    VCardService.class), CancelActivity.this, Context.BIND_AUTO_CREATE);
        }
    }

    private class CancelListener
            implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }
        @Override
        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private final CancelListener mCancelListener = new CancelListener();
    private int mJobId;
    private String mDisplayName;
    private int mType;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Uri uri = getIntent().getData();
        mJobId = Integer.parseInt(uri.getQueryParameter(JOB_ID));
        mDisplayName = uri.getQueryParameter(DISPLAY_NAME);
        mType = Integer.parseInt(uri.getQueryParameter(TYPE));
        showDialog(R.id.dialog_cancel_confirmation);
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        if (id == R.id.dialog_cancel_confirmation) {
            final String message;
            if (mType == VCardService.TYPE_IMPORT) {
                message = getString(R.string.cancel_import_confirmation_message, mDisplayName);
            } else {
                message = getString(R.string.cancel_export_confirmation_message, mDisplayName);
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setMessage(message)
                    .setPositiveButton(R.string.yes_button, new RequestCancelListener())
                    .setOnCancelListener(mCancelListener)
                    .setNegativeButton(R.string.no_button, mCancelListener);
            return builder.create();
        } else if (id == R.id.dialog_cancel_failed) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.cancel_vcard_import_or_export_failed)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(getString(R.string.fail_reason_unknown))
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
            return builder.create();
        } else {
            Log.w(LOG_TAG, "Unknown dialog id: " + id);
            return super.onCreateDialog(id, bundle);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        VCardService service = ((VCardService.MyBinder) binder).getService();

        try {
            final CancelRequest request = new CancelRequest(mJobId, mDisplayName);
            service.handleCancelRequest(request, null);
        } finally {
            unbindService(this);
        }

        finish();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // do nothing
    }
}
