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

import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contactsbind.FeedbackHelper;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Class for processing one export request from a user. Dropped after exporting requested Uri(s).
 * {@link VCardService} will create another object when there is another export request.
 */
public class ExportProcessor extends ProcessorBase {
    private static final String LOG_TAG = "VCardExport";
    private static final boolean DEBUG = VCardService.DEBUG;

    private final VCardService mService;
    private final ContentResolver mResolver;
    private final NotificationManager mNotificationManager;
    private final ExportRequest mExportRequest;
    private final int mJobId;
    private final String mCallingActivity;

    private volatile boolean mCanceled;
    private volatile boolean mDone;

    private final int SHOW_READY_TOAST = 1;
    private final Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.arg1 == SHOW_READY_TOAST) {
                // This message is long, so we set the duration to LENGTH_LONG.
                Toast.makeText(mService,
                        R.string.exporting_vcard_finished_toast, Toast.LENGTH_LONG).show();
            }

        }
    };

    public ExportProcessor(VCardService service, ExportRequest exportRequest, int jobId,
            String callingActivity) {
        mService = service;
        mResolver = service.getContentResolver();
        mNotificationManager =
                (NotificationManager)mService.getSystemService(Context.NOTIFICATION_SERVICE);
        mExportRequest = exportRequest;
        mJobId = jobId;
        mCallingActivity = callingActivity;
    }

    @Override
    public final int getType() {
        return VCardService.TYPE_EXPORT;
    }

    @Override
    public void run() {
        // ExecutorService ignores RuntimeException, so we need to show it here.
        try {
            runInternal();

            if (isCancelled()) {
                doCancelNotification();
            }
        } catch (OutOfMemoryError|RuntimeException e) {
            FeedbackHelper.sendFeedback(mService, LOG_TAG, "Failed to process vcard export", e);
            throw e;
        } finally {
            synchronized (this) {
                mDone = true;
            }
        }
    }

    private void runInternal() {
        if (DEBUG) Log.d(LOG_TAG, String.format("vCard export (id: %d) has started.", mJobId));
        final ExportRequest request = mExportRequest;
        VCardComposer composer = null;
        Writer writer = null;
        boolean successful = false;
        try {
            if (isCancelled()) {
                Log.i(LOG_TAG, "Export request is cancelled before handling the request");
                return;
            }
            final Uri uri = request.destUri;
            final OutputStream outputStream;
            try {
                outputStream = mResolver.openOutputStream(uri);
            } catch (FileNotFoundException e) {
                Log.w(LOG_TAG, "FileNotFoundException thrown", e);
                // Need concise title.

                final String errorReason =
                    mService.getString(R.string.fail_reason_could_not_open_file,
                            uri, e.getMessage());
                doFinishNotification(errorReason, null);
                return;
            }

            final String exportType = request.exportType;
            final int vcardType;
            if (TextUtils.isEmpty(exportType)) {
                vcardType = VCardConfig.getVCardTypeFromString(
                        mService.getString(R.string.config_export_vcard_type));
            } else {
                vcardType = VCardConfig.getVCardTypeFromString(exportType);
            }

            composer = new VCardComposer(mService, vcardType, true);

            // for test
            // int vcardType = (VCardConfig.VCARD_TYPE_V21_GENERIC |
            //     VCardConfig.FLAG_USE_QP_TO_PRIMARY_PROPERTIES);
            // composer = new VCardComposer(ExportVCardActivity.this, vcardType, true);

            writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            final Uri contentUriForRawContactsEntity = RawContactsEntity.CONTENT_URI;
            // TODO: should provide better selection.
            if (!composer.init(Contacts.CONTENT_URI, new String[] {Contacts._ID},
                    null, null,
                    null, contentUriForRawContactsEntity)) {
                final String errorReason = composer.getErrorReason();
                Log.e(LOG_TAG, "initialization of vCard composer failed: " + errorReason);
                final String translatedErrorReason =
                        translateComposerError(errorReason);
                final String title =
                        mService.getString(R.string.fail_reason_could_not_initialize_exporter,
                                translatedErrorReason);
                doFinishNotification(title, null);
                return;
            }

            final int total = composer.getCount();
            if (total == 0) {
                final String title =
                        mService.getString(R.string.fail_reason_no_exportable_contact);
                doFinishNotification(title, null);
                return;
            }

            int current = 1;  // 1-origin
            while (!composer.isAfterLast()) {
                if (isCancelled()) {
                    Log.i(LOG_TAG, "Export request is cancelled during composing vCard");
                    return;
                }
                try {
                    writer.write(composer.createOneEntry());
                } catch (IOException e) {
                    final String errorReason = composer.getErrorReason();
                    Log.e(LOG_TAG, "Failed to read a contact: " + errorReason);
                    final String translatedErrorReason =
                            translateComposerError(errorReason);
                    final String title =
                            mService.getString(R.string.fail_reason_error_occurred_during_export,
                                    translatedErrorReason);
                    doFinishNotification(title, null);
                    return;
                }

                // vCard export is quite fast (compared to import), and frequent notifications
                // bother notification bar too much.
                if (current % 100 == 1) {
                    doProgressNotification(uri, total, current);
                }
                current++;
            }
            Log.i(LOG_TAG, "Successfully finished exporting vCard " + request.destUri);

            if (DEBUG) {
                Log.d(LOG_TAG, "Ask MediaScanner to scan the file: " + request.destUri.getPath());
            }
            mService.updateMediaScanner(request.destUri.getPath());

            successful = true;
            final String filename = ExportVCardActivity.getOpenableUriDisplayName(mService, uri);
            // If it is a local file (i.e. not a file from Drive), we need to allow user to share
            // the file by pressing the notification; otherwise, it would be a file in Drive, we
            // don't need to enable this action in notification since the file is already uploaded.
            if (isLocalFile(uri)) {
                final Message msg = handler.obtainMessage();
                msg.arg1 = SHOW_READY_TOAST;
                handler.sendMessage(msg);
                doFinishNotificationWithShareAction(
                        mService.getString(R.string.exporting_vcard_finished_title_fallback),
                        mService.getString(R.string.touch_to_share_contacts), uri);
            } else {
                final String title = filename == null
                        ? mService.getString(R.string.exporting_vcard_finished_title_fallback)
                        : mService.getString(R.string.exporting_vcard_finished_title, filename);
                doFinishNotification(title, null);
            }
        } finally {
            if (composer != null) {
                composer.terminate();
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    Log.w(LOG_TAG, "IOException is thrown during close(). Ignored. " + e);
                }
            }
            mService.handleFinishExportNotification(mJobId, successful);
        }
    }

    private boolean isLocalFile(Uri uri) {
        final String authority = uri.getAuthority();
        return mService.getString(R.string.contacts_file_provider_authority).equals(authority);
    }

    private String translateComposerError(String errorMessage) {
        final Resources resources = mService.getResources();
        if (VCardComposer.FAILURE_REASON_FAILED_TO_GET_DATABASE_INFO.equals(errorMessage)) {
            return resources.getString(R.string.composer_failed_to_get_database_infomation);
        } else if (VCardComposer.FAILURE_REASON_NO_ENTRY.equals(errorMessage)) {
            return resources.getString(R.string.composer_has_no_exportable_contact);
        } else if (VCardComposer.FAILURE_REASON_NOT_INITIALIZED.equals(errorMessage)) {
            return resources.getString(R.string.composer_not_initialized);
        } else {
            return errorMessage;
        }
    }

    private void doProgressNotification(Uri uri, int totalCount, int currentCount) {
        final String displayName = uri.getLastPathSegment();
        final String description =
                mService.getString(R.string.exporting_contact_list_message, displayName);
        final String tickerText =
                mService.getString(R.string.exporting_contact_list_title);
        final Notification notification =
                NotificationImportExportListener.constructProgressNotification(mService,
                        VCardService.TYPE_EXPORT, description, tickerText, mJobId, displayName,
                        totalCount, currentCount);
        mService.startForeground(mJobId, notification);
    }

    private void doCancelNotification() {
        if (DEBUG) Log.d(LOG_TAG, "send cancel notification");
        final String description = mService.getString(R.string.exporting_vcard_canceled_title,
                mExportRequest.destUri.getLastPathSegment());
        final Notification notification =
                NotificationImportExportListener.constructCancelNotification(mService, description);
        mNotificationManager.notify(NotificationImportExportListener.DEFAULT_NOTIFICATION_TAG,
                mJobId, notification);
    }

    private void doFinishNotification(final String title, final String description) {
        if (DEBUG) Log.d(LOG_TAG, "send finish notification: " + title + ", " + description);
        final Intent intent = new Intent();
        intent.setClassName(mService, mCallingActivity);
        final Notification notification =
                NotificationImportExportListener.constructFinishNotification(mService, title,
                        description, intent);
        mNotificationManager.notify(NotificationImportExportListener.DEFAULT_NOTIFICATION_TAG,
                mJobId, notification);
    }

    /**
     * Pass intent with ACTION_SEND to notification so that user can press the notification to
     * share contacts.
     */
    private void doFinishNotificationWithShareAction(final String title, final String
            description, Uri uri) {
        if (DEBUG) Log.d(LOG_TAG, "send finish notification: " + title + ", " + description);
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(Contacts.CONTENT_VCARD_TYPE);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        // Securely grant access using temporary access permissions
        // Use FLAG_ACTIVITY_NEW_TASK to set it as new task, to get rid of cached files.
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        // Build notification
        final Notification notification =
                NotificationImportExportListener.constructFinishNotification(
                        mService, title, description, intent);
        mNotificationManager.notify(NotificationImportExportListener.DEFAULT_NOTIFICATION_TAG,
                mJobId, notification);
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (DEBUG) Log.d(LOG_TAG, "received cancel request");
        if (mDone || mCanceled) {
            return false;
        }
        mCanceled = true;
        return true;
    }

    @Override
    public synchronized boolean isCancelled() {
        return mCanceled;
    }

    @Override
    public synchronized boolean isDone() {
        return mDone;
    }

    public ExportRequest getRequest() {
        return mExportRequest;
    }
}
