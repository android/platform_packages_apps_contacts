/*
 * Copyright (C) 2011 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.vcard;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.activities.RequestPermissionsActivity;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contactsbind.FeedbackHelper;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryCounter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardVersionException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NfcImportVCardActivity extends Activity implements ServiceConnection,
        VCardImportExportListener {
    private static final String TAG = "NfcImportVCardActivity";

    private static final int SELECT_ACCOUNT = 1;

    private NdefRecord mRecord;
    private AccountWithDataSet mAccount;
    private Handler mHandler = new Handler();

    /**
     * Notification id used when error happened before sending an import request to VCardServer.
     */
    private static final int FAILURE_NOTIFICATION_ID = 1;

    /* package */ class ImportTask extends AsyncTask<VCardService, Void, ImportRequest> {
        @Override
        public ImportRequest doInBackground(VCardService... services) {
            ImportRequest request = createImportRequest();
            if (request == null) {
                return null;
            }

            ArrayList<ImportRequest> requests = new ArrayList<ImportRequest>();
            requests.add(request);
            services[0].handleImportRequest(requests, NfcImportVCardActivity.this);
            return request;
        }

        @Override
        public void onCancelled() {
            unbindService(NfcImportVCardActivity.this);
        }

        @Override
        public void onPostExecute(ImportRequest request) {
            if (request == null) {
                // Finish the activity in case of error so it doesn't stay in view.
                finish();
            }
            unbindService(NfcImportVCardActivity.this);
        }
    }

    /* package */ ImportRequest createImportRequest() {
        VCardParser parser;
        VCardEntryCounter counter = null;
        VCardSourceDetector detector = null;
        int vcardVersion = ImportVCardActivity.VCARD_VERSION_V21;
        try {
            ByteArrayInputStream is = new ByteArrayInputStream(mRecord.getPayload());
            is.mark(0);
            parser = new VCardParser_V21();
            try {
                counter = new VCardEntryCounter();
                detector = new VCardSourceDetector();
                parser.addInterpreter(counter);
                parser.addInterpreter(detector);
                parser.parse(is);
            } catch (VCardVersionException e1) {
                is.reset();
                vcardVersion = ImportVCardActivity.VCARD_VERSION_V30;
                parser = new VCardParser_V30();
                try {
                    counter = new VCardEntryCounter();
                    detector = new VCardSourceDetector();
                    parser.addInterpreter(counter);
                    parser.addInterpreter(detector);
                    parser.parse(is);
                } catch (VCardVersionException e2) {
                    FeedbackHelper.sendFeedback(this, TAG, "vcard with unsupported version", e2);
                    showFailureNotification(R.string.fail_reason_not_supported);
                    return null;
                }
            } finally {
                try {
                    if (is != null) is.close();
                } catch (IOException e) {
                }
            }
        } catch (IOException e) {
            FeedbackHelper.sendFeedback(this, TAG, "Failed to read vcard data", e);
            showFailureNotification(R.string.fail_reason_io_error);
            return null;
        } catch (VCardNestedException e) {
            Log.w(TAG, "Nested Exception is found (it may be false-positive).");
            // Go through without throwing the Exception, as we may be able to detect the
            // version before it
        } catch (VCardException e) {
            FeedbackHelper.sendFeedback(this, TAG, "Failed to parse vcard", e);
            showFailureNotification(R.string.fail_reason_not_supported);
            return null;
        }

        return new ImportRequest(mAccount, mRecord.getPayload(), null,
                getString(R.string.nfc_vcard_file_name), detector.getEstimatedType(),
                detector.getEstimatedCharset(), vcardVersion, counter.getCount());
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        VCardService service = ((VCardService.MyBinder) binder).getService();
        new ImportTask().execute(service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        // Do nothing
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        if (RequestPermissionsActivity.startPermissionActivityIfNeeded(this)) {
            return;
        }

        Intent intent = getIntent();
        if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
            Log.w(TAG, "Unknowon intent " + intent);
            finish();
            return;
        }

        String type = intent.getType();
        if (type == null ||
                (!"text/x-vcard".equals(type) && !"text/vcard".equals(type))) {
            Log.w(TAG, "Not a vcard");
            //setStatus(getString(R.string.fail_reason_not_supported));
            finish();
            return;
        }
        NdefMessage msg = (NdefMessage) intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES)[0];
        mRecord = msg.getRecords()[0];

        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(this);
        final List<AccountWithDataSet> accountList = accountTypes.blockForWritableAccounts();
        if (accountList.size() == 0) {
            mAccount = null;
        } else if (accountList.size() == 1) {
            mAccount = accountList.get(0);
        } else {
            startActivityForResult(new Intent(this, SelectAccountActivity.class), SELECT_ACCOUNT);
            return;
        }

        startImport();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SELECT_ACCOUNT) {
            if (resultCode == RESULT_OK) {
                mAccount = new AccountWithDataSet(
                        intent.getStringExtra(SelectAccountActivity.ACCOUNT_NAME),
                        intent.getStringExtra(SelectAccountActivity.ACCOUNT_TYPE),
                        intent.getStringExtra(SelectAccountActivity.DATA_SET));
                startImport();
            } else {
                finish();
            }
        }
    }

    private void startImport() {
        // We don't want the service finishes itself just after this connection.
        Intent intent = new Intent(this, VCardService.class);
        startService(intent);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public Notification onImportProcessed(ImportRequest request, int jobId, int sequence) {
        return null;
    }

    @Override
    public Notification onImportParsed(ImportRequest request, int jobId, VCardEntry entry,
            int currentCount, int totalCount) {
        return null;
    }

    @Override
    public void onImportFinished(ImportRequest request, int jobId, Uri uri) {
        if (isFinishing()) {
            Log.i(TAG, "Late import -- ignoring");
            return;
        }

        if (uri != null) {
            Uri contactUri = RawContacts.getContactLookupUri(getContentResolver(), uri);
            Intent intent = new Intent(Intent.ACTION_VIEW, contactUri);
            ImplicitIntentsUtil.startActivityInAppIfPossible(this, intent);
            finish();
        }
    }

    @Override
    public void onImportFailed(ImportRequest request) {
        if (isFinishing()) {
            Log.i(TAG, "Late import failure -- ignoring");
            return;
        }
        showFailureNotification(R.string.vcard_import_request_rejected_message);
        finish();
    }

    @Override
    public void onImportCanceled(ImportRequest request, int jobId) {
        // do nothing
    }

    @Override
    public Notification onExportProcessed(ExportRequest request, int jobId) {
        return null;
    }

    @Override
    public void onExportFailed(ExportRequest request) {
        // do nothing
    }

    @Override
    public void onCancelRequest(CancelRequest request, int type) {
        // do nothing
    }

    /* package */ void showFailureNotification(int reasonId) {
        final NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        final Notification notification =
                NotificationImportExportListener.constructImportFailureNotification(
                        this,
                        getString(reasonId));
        notificationManager.notify(NotificationImportExportListener.FAILURE_NOTIFICATION_TAG,
                FAILURE_NOTIFICATION_ID, notification);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(NfcImportVCardActivity.this,
                        getString(R.string.vcard_import_failed), Toast.LENGTH_LONG).show();
            }
        });
    }
}
