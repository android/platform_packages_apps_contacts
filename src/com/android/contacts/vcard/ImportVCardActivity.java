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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.activities.RequestImportVCardPermissionsActivity;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contactsbind.FeedbackHelper;
import com.android.vcard.VCardEntryCounter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardVersionException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The class letting users to import vCard. This includes the UI part for letting them select
 * an Account and posssibly a file if there's no Uri is given from its caller Activity.
 *
 * Note that this Activity assumes that the instance is a "one-shot Activity", which will be
 * finished (with the method {@link Activity#finish()}) after the import and never reuse
 * any Dialog in the instance. So this code is careless about the management around managed
 * dialogs stuffs (like how onCreateDialog() is used).
 */
public class ImportVCardActivity extends Activity implements ImportVCardDialogFragment.Listener {
    private static final String LOG_TAG = "VCardImport";

    private static final int SELECT_ACCOUNT = 0;

    /* package */ final static int VCARD_VERSION_AUTO_DETECT = 0;
    /* package */ final static int VCARD_VERSION_V21 = 1;
    /* package */ final static int VCARD_VERSION_V30 = 2;

    private static final int REQUEST_OPEN_DOCUMENT = 100;

    /**
     * Notification id used when error happened before sending an import request to VCardServer.
     */
    private static final int FAILURE_NOTIFICATION_ID = 1;

    private static final String LOCAL_TMP_FILE_NAME_EXTRA =
            "com.android.contacts.vcard.LOCAL_TMP_FILE_NAME";

    private static final String SOURCE_URI_DISPLAY_NAME =
            "com.android.contacts.vcard.SOURCE_URI_DISPLAY_NAME";

    private static final String STORAGE_VCARD_URI_PREFIX = "file:///storage";

    private AccountWithDataSet mAccount;

    private ProgressDialog mProgressDialogForCachingVCard;

    private VCardCacheThread mVCardCacheThread;
    private ImportRequestConnection mConnection;
    /* package */ VCardImportExportListener mListener;

    private String mErrorMessage;

    private Handler mHandler = new Handler();

    // Runs on the UI thread.
    private class DialogDisplayer implements Runnable {
        private final int mResId;
        public DialogDisplayer(int resId) {
            mResId = resId;
        }
        public DialogDisplayer(String errorMessage) {
            mResId = R.id.dialog_error_with_message;
            mErrorMessage = errorMessage;
        }
        @Override
        public void run() {
            if (!isFinishing()) {
                showDialog(mResId);
            }
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

    private CancelListener mCancelListener = new CancelListener();

    private class ImportRequestConnection implements ServiceConnection {
        private VCardService mService;

        public void sendImportRequest(final List<ImportRequest> requests) {
            Log.i(LOG_TAG, "Send an import request");
            mService.handleImportRequest(requests, mListener);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mService = ((VCardService.MyBinder) binder).getService();
            Log.i(LOG_TAG,
                    String.format("Connected to VCardService. Kick a vCard cache thread (uri: %s)",
                            Arrays.toString(mVCardCacheThread.getSourceUris())));
            mVCardCacheThread.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(LOG_TAG, "Disconnected from VCardService");
        }
    }

    /**
     * Caches given vCard files into a local directory, and sends actual import request to
     * {@link VCardService}.
     *
     * We need to cache given files into local storage. One of reasons is that some data (as Uri)
     * may have special permissions. Callers may allow only this Activity to access that content,
     * not what this Activity launched (like {@link VCardService}).
     */
    private class VCardCacheThread extends Thread
            implements DialogInterface.OnCancelListener {
        private boolean mCanceled;
        private PowerManager.WakeLock mWakeLock;
        private VCardParser mVCardParser;
        private final Uri[] mSourceUris;  // Given from a caller.
        private final String[] mSourceDisplayNames; // Display names for each Uri in mSourceUris.
        private final byte[] mSource;
        private final String mDisplayName;

        public VCardCacheThread(final Uri[] sourceUris, String[] sourceDisplayNames) {
            mSourceUris = sourceUris;
            mSourceDisplayNames = sourceDisplayNames;
            mSource = null;
            final Context context = ImportVCardActivity.this;
            final PowerManager powerManager =
                    (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
            mDisplayName = null;
        }

        @Override
        public void finalize() {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                Log.w(LOG_TAG, "WakeLock is being held.");
                mWakeLock.release();
            }
        }

        @Override
        public void run() {
            Log.i(LOG_TAG, "vCard cache thread starts running.");
            if (mConnection == null) {
                throw new NullPointerException("vCard cache thread must be launched "
                        + "after a service connection is established");
            }

            mWakeLock.acquire();
            try {
                if (mCanceled == true) {
                    Log.i(LOG_TAG, "vCard cache operation is canceled.");
                    return;
                }

                final Context context = ImportVCardActivity.this;
                // Uris given from caller applications may not be opened twice: consider when
                // it is not from local storage (e.g. "file:///...") but from some special
                // provider (e.g. "content://...").
                // Thus we have to once copy the content of Uri into local storage, and read
                // it after it.
                //
                // We may be able to read content of each vCard file during copying them
                // to local storage, but currently vCard code does not allow us to do so.
                int cache_index = 0;
                ArrayList<ImportRequest> requests = new ArrayList<ImportRequest>();
                if (mSource != null) {
                    try {
                        requests.add(constructImportRequest(mSource, null, mDisplayName));
                    } catch (VCardException e) {
                        FeedbackHelper.sendFeedback(ImportVCardActivity.this, LOG_TAG,
                                "Failed to cache vcard", e);
                        showFailureNotification(R.string.fail_reason_not_supported);
                        return;
                    }
                } else {
                    int i = 0;
                    for (Uri sourceUri : mSourceUris) {
                        if (mCanceled) {
                            Log.i(LOG_TAG, "vCard cache operation is canceled.");
                            break;
                        }

                        String sourceDisplayName = mSourceDisplayNames[i++];

                        final ImportRequest request;
                        try {
                            request = constructImportRequest(null, sourceUri, sourceDisplayName);
                        } catch (VCardException e) {
                            FeedbackHelper.sendFeedback(ImportVCardActivity.this, LOG_TAG,
                                    "Failed to cache vcard", e);
                            showFailureNotification(R.string.fail_reason_not_supported);
                            return;
                        } catch (IOException e) {
                            FeedbackHelper.sendFeedback(ImportVCardActivity.this, LOG_TAG,
                                    "Failed to cache vcard", e);
                            showFailureNotification(R.string.fail_reason_io_error);
                            return;
                        }
                        if (mCanceled) {
                            Log.i(LOG_TAG, "vCard cache operation is canceled.");
                            return;
                        }
                        requests.add(request);
                    }
                }
                if (!requests.isEmpty()) {
                    mConnection.sendImportRequest(requests);
                } else {
                    Log.w(LOG_TAG, "Empty import requests. Ignore it.");
                }
            } catch (OutOfMemoryError e) {
                FeedbackHelper.sendFeedback(ImportVCardActivity.this, LOG_TAG,
                        "OutOfMemoryError occured during caching vCard", e);
                System.gc();
                runOnUiThread(new DialogDisplayer(
                        getString(R.string.fail_reason_low_memory_during_import)));
            } catch (IOException e) {
                FeedbackHelper.sendFeedback(ImportVCardActivity.this, LOG_TAG,
                        "IOException during caching vCard", e);
                runOnUiThread(new DialogDisplayer(
                        getString(R.string.fail_reason_io_error)));
            } finally {
                Log.i(LOG_TAG, "Finished caching vCard.");
                mWakeLock.release();
                try {
                    unbindService(mConnection);
                } catch (IllegalArgumentException e) {
                    FeedbackHelper.sendFeedback(ImportVCardActivity.this, LOG_TAG,
                            "Cannot unbind service connection", e);
                }
                mProgressDialogForCachingVCard.dismiss();
                mProgressDialogForCachingVCard = null;
                finish();
            }
        }

        /**
         * Reads localDataUri (possibly multiple times) and constructs {@link ImportRequest} from
         * its content.
         *
         * @arg localDataUri Uri actually used for the import. Should be stored in
         * app local storage, as we cannot guarantee other types of Uris can be read
         * multiple times. This variable populates {@link ImportRequest#uri}.
         * @arg displayName Used for displaying information to the user. This variable populates
         * {@link ImportRequest#displayName}.
         */
        private ImportRequest constructImportRequest(final byte[] data,
                final Uri localDataUri, final String displayName)
                throws IOException, VCardException {
            final ContentResolver resolver = ImportVCardActivity.this.getContentResolver();
            VCardEntryCounter counter = null;
            VCardSourceDetector detector = null;
            int vcardVersion = VCARD_VERSION_V21;
            try {
                boolean shouldUseV30 = false;
                InputStream is;
                if (data != null) {
                    is = new ByteArrayInputStream(data);
                } else {
                    is = resolver.openInputStream(localDataUri);
                }
                mVCardParser = new VCardParser_V21();
                try {
                    counter = new VCardEntryCounter();
                    detector = new VCardSourceDetector();
                    mVCardParser.addInterpreter(counter);
                    mVCardParser.addInterpreter(detector);
                    mVCardParser.parse(is);
                } catch (VCardVersionException e1) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }

                    shouldUseV30 = true;
                    if (data != null) {
                        is = new ByteArrayInputStream(data);
                    } else {
                        is = resolver.openInputStream(localDataUri);
                    }
                    mVCardParser = new VCardParser_V30();
                    try {
                        counter = new VCardEntryCounter();
                        detector = new VCardSourceDetector();
                        mVCardParser.addInterpreter(counter);
                        mVCardParser.addInterpreter(detector);
                        mVCardParser.parse(is);
                    } catch (VCardVersionException e2) {
                        throw new VCardException("vCard with unspported version.");
                    }
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                        }
                    }
                }

                vcardVersion = shouldUseV30 ? VCARD_VERSION_V30 : VCARD_VERSION_V21;
            } catch (VCardNestedException e) {
                Log.w(LOG_TAG, "Nested Exception is found (it may be false-positive).");
                // Go through without throwing the Exception, as we may be able to detect the
                // version before it
            }
            return new ImportRequest(mAccount,
                    data, localDataUri, displayName,
                    detector.getEstimatedType(),
                    detector.getEstimatedCharset(),
                    vcardVersion, counter.getCount());
        }

        public Uri[] getSourceUris() {
            return mSourceUris;
        }

        public void cancel() {
            mCanceled = true;
            if (mVCardParser != null) {
                mVCardParser.cancel();
            }
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            Log.i(LOG_TAG, "Cancel request has come. Abort caching vCard.");
            cancel();
        }
    }

    private void importVCard(final Uri uri, final String sourceDisplayName) {
        importVCard(new Uri[] {uri}, new String[] {sourceDisplayName});
    }

    private void importVCard(final Uri[] uris, final String[] sourceDisplayNames) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing()) {
                    mVCardCacheThread = new VCardCacheThread(uris, sourceDisplayNames);
                    mListener = new NotificationImportExportListener(ImportVCardActivity.this);
                    showDialog(R.id.dialog_cache_vcard);
                }
            }
        });
    }

    private String getDisplayName(Uri sourceUri) {
        if (sourceUri == null) {
            return null;
        }
        final ContentResolver resolver = ImportVCardActivity.this.getContentResolver();
        String displayName = null;
        Cursor cursor = null;
        // Try to get a display name from the given Uri. If it fails, we just
        // pick up the last part of the Uri.
        try {
            cursor = resolver.query(sourceUri,
                    new String[] { OpenableColumns.DISPLAY_NAME },
                    null, null, null);
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                if (cursor.getCount() > 1) {
                    Log.w(LOG_TAG, "Unexpected multiple rows: "
                            + cursor.getCount());
                }
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    displayName = cursor.getString(index);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (TextUtils.isEmpty(displayName)){
            displayName = sourceUri.getLastPathSegment();
        }
        return displayName;
    }

    /**
     * Copy the content of sourceUri to the destination.
     */
    private Uri copyTo(final Uri sourceUri, String filename) throws IOException {
        Log.i(LOG_TAG, String.format("Copy a Uri to app local storage (%s -> %s)",
                sourceUri, filename));
        final Context context = ImportVCardActivity.this;
        final ContentResolver resolver = context.getContentResolver();
        ReadableByteChannel inputChannel = null;
        WritableByteChannel outputChannel = null;
        Uri destUri = null;
        try {
            inputChannel = Channels.newChannel(resolver.openInputStream(sourceUri));
            destUri = Uri.parse(context.getFileStreamPath(filename).toURI().toString());
            outputChannel = context.openFileOutput(filename, Context.MODE_PRIVATE).getChannel();
            final ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
            while (inputChannel.read(buffer) != -1) {
                buffer.flip();
                outputChannel.write(buffer);
                buffer.compact();
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                outputChannel.write(buffer);
            }
        } finally {
            if (inputChannel != null) {
                try {
                    inputChannel.close();
                } catch (IOException e) {
                    Log.w(LOG_TAG, "Failed to close inputChannel.");
                }
            }
            if (outputChannel != null) {
                try {
                    outputChannel.close();
                } catch(IOException e) {
                    Log.w(LOG_TAG, "Failed to close outputChannel");
                }
            }
        }
        return destUri;
    }

    /**
     * Reads the file from {@param sourceUri} and copies it to local cache file.
     * Returns the local file name which stores the file from sourceUri.
     */
    private String readUriToLocalFile(Uri sourceUri) {
        // Read the uri to local first.
        int cache_index = 0;
        String localFilename = null;
        // Note: caches are removed by VCardService.
        while (true) {
            localFilename = VCardService.CACHE_FILE_PREFIX + cache_index + ".vcf";
            final File file = getFileStreamPath(localFilename);
            if (!file.exists()) {
                break;
            } else {
                if (cache_index == Integer.MAX_VALUE) {
                    throw new RuntimeException("Exceeded cache limit");
                }
                cache_index++;
            }
        }
        try {
            copyTo(sourceUri, localFilename);
        } catch (IOException|SecurityException e) {
            FeedbackHelper.sendFeedback(this, LOG_TAG, "Failed to copy vcard to local file", e);
            showFailureNotification(R.string.fail_reason_io_error);
            return null;
        }

        if (localFilename == null) {
            Log.e(LOG_TAG, "Cannot load uri to local storage.");
            showFailureNotification(R.string.fail_reason_io_error);
            return null;
        }

        return localFilename;
    }

    private Uri readUriToLocalUri(Uri sourceUri) {
        final String fileName = readUriToLocalFile(sourceUri);
        if (fileName == null) {
            return null;
        }
        return Uri.parse(getFileStreamPath(fileName).toURI().toString());
    }

    // Returns true if uri is from Storage.
    private boolean isStorageUri(Uri uri) {
        return uri != null && uri.toString().startsWith(STORAGE_VCARD_URI_PREFIX);
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        getWindow().addSystemFlags(android.view.WindowManager.LayoutParams
            .SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);

        Uri sourceUri = getIntent().getData();

        // Reading uris from non-storage needs the permission granted from the source intent,
        // instead of permissions from RequestImportVCardPermissionActivity. So skipping requesting
        // permissions from RequestImportVCardPermissionActivity for uris from non-storage source.
        if (isStorageUri(sourceUri) && RequestImportVCardPermissionsActivity
                .startPermissionActivity(this, isCallerSelf(this))) {
            return;
        }

        String sourceDisplayName = null;
        if (sourceUri != null) {
            // Read the uri to local first.
            String localTmpFileName = getIntent().getStringExtra(LOCAL_TMP_FILE_NAME_EXTRA);
            sourceDisplayName = getIntent().getStringExtra(SOURCE_URI_DISPLAY_NAME);
            if (TextUtils.isEmpty(localTmpFileName)) {
                localTmpFileName = readUriToLocalFile(sourceUri);
                sourceDisplayName = getDisplayName(sourceUri);
                if (localTmpFileName == null) {
                    Log.e(LOG_TAG, "Cannot load uri to local storage.");
                    showFailureNotification(R.string.fail_reason_io_error);
                    return;
                }
                getIntent().putExtra(LOCAL_TMP_FILE_NAME_EXTRA, localTmpFileName);
                getIntent().putExtra(SOURCE_URI_DISPLAY_NAME, sourceDisplayName);
            }
            sourceUri = Uri.parse(getFileStreamPath(localTmpFileName).toURI().toString());
        }

        // Always request required permission for contacts before importing the vcard.
        if (RequestImportVCardPermissionsActivity.startPermissionActivity(this,
                isCallerSelf(this))) {
            return;
        }

        String accountName = null;
        String accountType = null;
        String dataSet = null;
        final Intent intent = getIntent();
        if (intent != null) {
            accountName = intent.getStringExtra(SelectAccountActivity.ACCOUNT_NAME);
            accountType = intent.getStringExtra(SelectAccountActivity.ACCOUNT_TYPE);
            dataSet = intent.getStringExtra(SelectAccountActivity.DATA_SET);
        } else {
            Log.e(LOG_TAG, "intent does not exist");
        }

        if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
            mAccount = new AccountWithDataSet(accountName, accountType, dataSet);
        } else {
            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(this);
            final List<AccountWithDataSet> accountList = accountTypes.blockForWritableAccounts();
            if (accountList.size() == 0) {
                mAccount = null;
            } else if (accountList.size() == 1) {
                mAccount = accountList.get(0);
            } else {
                startActivityForResult(new Intent(this, SelectAccountActivity.class),
                        SELECT_ACCOUNT);
                return;
            }
        }

        if (isCallerSelf(this)) {
            startImport(sourceUri, sourceDisplayName);
        } else {
            ImportVCardDialogFragment.show(this, sourceUri, sourceDisplayName);
        }
    }

    private static boolean isCallerSelf(Activity activity) {
        // {@link Activity#getCallingActivity()} is a safer alternative to
        // {@link Activity#getCallingPackage()} that works around a
        // framework bug where getCallingPackage() can sometimes return null even when the
        // current activity *was* in fact launched via a startActivityForResult() call.
        //
        // (The bug happens if the task stack needs to be re-created by the framework after
        // having been killed due to memory pressure or by the "Don't keep activities"
        // developer option; see bug 7494866 for the full details.)
        //
        // Turns out that {@link Activity#getCallingActivity()} *does* return correct info
        // even in the case where getCallingPackage() is broken, so the workaround is simply
        // to get the package name from getCallingActivity().getPackageName() instead.
        final ComponentName callingActivity = activity.getCallingActivity();
        if (callingActivity == null) return false;
        final String packageName = callingActivity.getPackageName();
        if (packageName == null) return false;
        return packageName.equals(activity.getApplicationContext().getPackageName());
    }

    @Override
    public void onImportVCardConfirmed(Uri sourceUri, String sourceDisplayName) {
        startImport(sourceUri, sourceDisplayName);
    }

    @Override
    public void onImportVCardDenied() {
        finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == SELECT_ACCOUNT) {
            if (resultCode == Activity.RESULT_OK) {
                mAccount = new AccountWithDataSet(
                        intent.getStringExtra(SelectAccountActivity.ACCOUNT_NAME),
                        intent.getStringExtra(SelectAccountActivity.ACCOUNT_TYPE),
                        intent.getStringExtra(SelectAccountActivity.DATA_SET));
                final Uri sourceUri = getIntent().getData();
                if (sourceUri == null) {
                    startImport(sourceUri, /* sourceDisplayName =*/ null);
                } else {
                    final String sourceDisplayName = getIntent().getStringExtra(
                            SOURCE_URI_DISPLAY_NAME);
                    final String localFileName = getIntent().getStringExtra(
                            LOCAL_TMP_FILE_NAME_EXTRA);
                    final Uri localUri = Uri.parse(
                            getFileStreamPath(localFileName).toURI().toString());
                    startImport(localUri, sourceDisplayName);
                }
            } else {
                if (resultCode != Activity.RESULT_CANCELED) {
                    Log.w(LOG_TAG, "Result code was not OK nor CANCELED: " + resultCode);
                }
                finish();
            }
        } else if (requestCode == REQUEST_OPEN_DOCUMENT) {
            if (resultCode == Activity.RESULT_OK) {
                final ClipData clipData = intent.getClipData();
                if (clipData != null) {
                    final ArrayList<Uri> uris = new ArrayList<>();
                    final ArrayList<String> sourceDisplayNames = new ArrayList<>();
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        final Uri uri = item.getUri();
                        if (uri != null) {
                            final Uri localUri = readUriToLocalUri(uri);
                            if (localUri != null) {
                                final String sourceDisplayName = getDisplayName(uri);
                                uris.add(localUri);
                                sourceDisplayNames.add(sourceDisplayName);
                            }
                        }
                    }
                    if (uris.isEmpty()) {
                        Log.w(LOG_TAG, "No vCard was selected for import");
                        finish();
                    } else {
                        Log.i(LOG_TAG, "Multiple vCards selected for import: " + uris);
                        importVCard(uris.toArray(new Uri[0]),
                                sourceDisplayNames.toArray(new String[0]));
                    }
                } else {
                    final Uri uri = intent.getData();
                    if (uri != null) {
                        Log.i(LOG_TAG, "vCard selected for import: " + uri);
                        final Uri localUri = readUriToLocalUri(uri);
                        if (localUri != null) {
                            final String sourceDisplayName = getDisplayName(uri);
                            importVCard(localUri, sourceDisplayName);
                        } else {
                            Log.w(LOG_TAG, "No local URI for vCard import");
                            finish();
                        }
                    } else {
                        Log.w(LOG_TAG, "No vCard was selected for import");
                        finish();
                    }
                }
            } else {
                if (resultCode != Activity.RESULT_CANCELED) {
                    Log.w(LOG_TAG, "Result code was not OK nor CANCELED" + resultCode);
                }
                finish();
            }
        }
    }

    private void startImport(Uri uri, String sourceDisplayName) {
        // Handle inbound files
        if (uri != null) {
            Log.i(LOG_TAG, "Starting vCard import using Uri " + uri);
            importVCard(uri, sourceDisplayName);
        } else {
            Log.i(LOG_TAG, "Start vCard without Uri. The user will select vCard manually.");
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(VCardService.X_VCARD_MIME_TYPE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, REQUEST_OPEN_DOCUMENT);
        }
    }

    @Override
    protected Dialog onCreateDialog(int resId, Bundle bundle) {
        if (resId == R.id.dialog_cache_vcard) {
            if (mProgressDialogForCachingVCard == null) {
                final String title = getString(R.string.caching_vcard_title);
                final String message = getString(R.string.caching_vcard_message);
                mProgressDialogForCachingVCard = new ProgressDialog(this);
                mProgressDialogForCachingVCard.setTitle(title);
                mProgressDialogForCachingVCard.setMessage(message);
                mProgressDialogForCachingVCard.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                mProgressDialogForCachingVCard.setOnCancelListener(mVCardCacheThread);
                startVCardService();
            }
            return mProgressDialogForCachingVCard;
        } else if (resId == R.id.dialog_error_with_message) {
            String message = mErrorMessage;
            if (TextUtils.isEmpty(message)) {
                Log.e(LOG_TAG, "Error message is null while it must not.");
                message = getString(R.string.fail_reason_unknown);
            }
            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.reading_vcard_failed_title))
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(message)
                .setOnCancelListener(mCancelListener)
                .setPositiveButton(android.R.string.ok, mCancelListener);
            return builder.create();
        }

        return super.onCreateDialog(resId, bundle);
    }

    /* package */ void startVCardService() {
        mConnection = new ImportRequestConnection();

        Log.i(LOG_TAG, "Bind to VCardService.");
        // We don't want the service finishes itself just after this connection.
        Intent intent = new Intent(this, VCardService.class);
        startService(intent);
        bindService(new Intent(this, VCardService.class),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (mProgressDialogForCachingVCard != null) {
            Log.i(LOG_TAG, "Cache thread is still running. Show progress dialog again.");
            showDialog(R.id.dialog_cache_vcard);
        }
    }

    /* package */ void showFailureNotification(int reasonId) {
        final NotificationManager notificationManager =
                (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        final Notification notification =
                NotificationImportExportListener.constructImportFailureNotification(
                        ImportVCardActivity.this,
                        getString(reasonId));
        notificationManager.notify(NotificationImportExportListener.FAILURE_NOTIFICATION_TAG,
                FAILURE_NOTIFICATION_ID, notification);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ImportVCardActivity.this,
                        getString(R.string.vcard_import_failed), Toast.LENGTH_LONG).show();
            }
        });
    }
}
