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

package com.android.contacts;

import com.android.contacts.model.Sources;
import com.android.contacts.util.AccountSelectionUtil;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.pim.vcard.VCardConfig;
import android.pim.vcard.VCardEntryCommitter;
import android.pim.vcard.VCardEntryConstructor;
import android.pim.vcard.VCardEntryCounter;
import android.pim.vcard.VCardInterpreter;
import android.pim.vcard.VCardInterpreterCollection;
import android.pim.vcard.VCardParser;
import android.pim.vcard.VCardParser_V21;
import android.pim.vcard.VCardParser_V30;
import android.pim.vcard.VCardSourceDetector;
import android.pim.vcard.exception.VCardException;
import android.pim.vcard.exception.VCardNestedException;
import android.pim.vcard.exception.VCardNotSupportedException;
import android.pim.vcard.exception.VCardVersionException;
import android.provider.ContactsContract.RawContacts;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

class VCardFile {
    private String mName;
    private String mCanonicalPath;
    private long mLastModified;

    public VCardFile(String name, String canonicalPath, long lastModified) {
        mName = name;
        mCanonicalPath = canonicalPath;
        mLastModified = lastModified;
    }

    public String getName() {
        return mName;
    }

    public String getCanonicalPath() {
        return mCanonicalPath;
    }

    public long getLastModified() {
        return mLastModified;
    }
}

/**
 * Class for importing vCard. Several user interaction will be required while reading
 * (selecting a file, waiting a moment, etc.)
 *
 * Note that this Activity assumes that the instance is a "one-shot Activity", which will be
 * finished (with the method {@link Activity#finish()}) after the import and never reuse
 * any Dialog in the instance. So this code is careless about the management around managed
 * dialogs stuffs (like how onCreateDialog() is used).
 */
public class ImportVCardActivity extends Activity {
    private static final String LOG_TAG = "ImportVCardActivity";
    private static final boolean DO_PERFORMANCE_PROFILE = false;

    // Run on the UI thread. Must not be null except after onDestroy().
    private Handler mHandler = new Handler();

    private AccountSelectionUtil.AccountSelectedListener mAccountSelectionListener;
    private Account mAccount;

    private ProgressDialog mProgressDialogForScanVCard;

    private List<VCardFile> mAllVCardFileList;
    private VCardScanThread mVCardScanThread;
    private VCardReadThread mVCardReadThread;
    private ProgressDialog mProgressDialogForReadVCard;

    private String mErrorMessage;

    private boolean mNeedReview = false;

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
        public void run() {
            showDialog(mResId);
        }
    }

    private class CancelListener
        implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
        public void onClick(DialogInterface dialog, int which) {
            finish();
        }

        public void onCancel(DialogInterface dialog) {
            finish();
        }
    }

    private CancelListener mCancelListener = new CancelListener();

    private class VCardReadThread extends Thread
            implements DialogInterface.OnCancelListener {
        private ContentResolver mResolver;
        private VCardParser mVCardParser;
        private boolean mCanceled;
        private PowerManager.WakeLock mWakeLock;
        private Uri mUri;
        private File mTempFile;

        private List<VCardFile> mSelectedVCardFileList;
        private List<String> mErrorFileNameList;

        public VCardReadThread(Uri uri) {
            mUri = uri;
            init();
        }

        public VCardReadThread(final List<VCardFile> selectedVCardFileList) {
            mSelectedVCardFileList = selectedVCardFileList;
            mErrorFileNameList = new ArrayList<String>();
            init();
        }

        private void init() {
            Context context = ImportVCardActivity.this;
            mResolver = context.getContentResolver();
            PowerManager powerManager = (PowerManager)context.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void finalize() {
            if (mWakeLock != null && mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }

        @Override
        public void run() {
            boolean shouldCallFinish = true;
            mWakeLock.acquire();
            Uri createdUri = null;
            mTempFile = null;
            // Some malicious vCard data may make this thread broken
            // (e.g. OutOfMemoryError).
            // Even in such cases, some should be done.
            try {
                if (mUri != null) {  // Read one vCard expressed by mUri
                    final Uri targetUri = mUri;
                    mProgressDialogForReadVCard.setProgressNumberFormat("");
                    mProgressDialogForReadVCard.setProgress(0);

                    // Count the number of VCard entries
                    mProgressDialogForReadVCard.setIndeterminate(true);
                    long start;
                    if (DO_PERFORMANCE_PROFILE) {
                        start = System.currentTimeMillis();
                    }
                    final VCardEntryCounter counter = new VCardEntryCounter();
                    final VCardSourceDetector detector = new VCardSourceDetector();
                    final VCardInterpreterCollection builderCollection =
                            new VCardInterpreterCollection(Arrays.asList(counter, detector));
                    boolean result;
                    try {
                        // We don't know which type should be useld to parse the Uri.
                        // It is possble to misinterpret the vCard, but we expect the parser
                        // lets VCardSourceDetector detect the type before the misinterpretation.
                        result = readOneVCardFile(targetUri, VCardConfig.VCARD_TYPE_UNKNOWN,
                                builderCollection, true, null);
                    } catch (VCardNestedException e) {
                        try {
                            final int estimatedVCardType = detector.getEstimatedType();
                            // Assume that VCardSourceDetector was able to detect the source.
                            // Try again with the detector.
                            result = readOneVCardFile(targetUri, estimatedVCardType,
                                    counter, false, null);
                        } catch (VCardNestedException e2) {
                            result = false;
                            Log.e(LOG_TAG, "Must not reach here. " + e2);
                        }
                    }
                    if (DO_PERFORMANCE_PROFILE) {
                        long time = System.currentTimeMillis() - start;
                        Log.d(LOG_TAG, "time for counting the number of vCard entries: " +
                                time + " ms");
                    }
                    if (!result) {
                        shouldCallFinish = false;
                        return;
                    }

                    mProgressDialogForReadVCard.setProgressNumberFormat(
                            getString(R.string.reading_vcard_contacts));
                    mProgressDialogForReadVCard.setIndeterminate(false);
                    mProgressDialogForReadVCard.setMax(counter.getCount());
                    createdUri = doActuallyReadOneVCard(targetUri, mAccount, true, detector,
                            mErrorFileNameList);
                } else {  // Read multiple files.
                    mProgressDialogForReadVCard.setProgressNumberFormat(
                            getString(R.string.reading_vcard_files));
                    mProgressDialogForReadVCard.setMax(mSelectedVCardFileList.size());
                    mProgressDialogForReadVCard.setProgress(0);

                    for (VCardFile vcardFile : mSelectedVCardFileList) {
                        if (mCanceled) {
                            return;
                        }
                        // TODO: detect scheme!
                        final Uri targetUri = Uri.parse("file://" + vcardFile.getCanonicalPath());
                        VCardSourceDetector detector = new VCardSourceDetector();
                        try {
                            if (!readOneVCardFile(targetUri, VCardConfig.VCARD_TYPE_UNKNOWN,
                                    detector, true, mErrorFileNameList)) {
                                continue;
                            }
                        } catch (VCardNestedException e) {
                            // Assume that VCardSourceDetector was able to detect the source.
                        }
                        doActuallyReadOneVCard(targetUri, mAccount,
                                false, detector, mErrorFileNameList);
                        mProgressDialogForReadVCard.incrementProgressBy(1);
                    }
                }
            } finally {
                mWakeLock.release();
                mProgressDialogForReadVCard.dismiss();
                if (mTempFile != null) {
                    if (!mTempFile.delete()) {
                        Log.w(LOG_TAG, "Failed to delete a cache file.");
                    }
                    mTempFile = null;
                }
                // finish() is called via mCancelListener, which is used in DialogDisplayer.
                if (shouldCallFinish && !isFinishing()) {
                    if (mErrorFileNameList == null || mErrorFileNameList.isEmpty()) {
                        finish();
                        if (mNeedReview) {
                            mNeedReview = false;
                            Log.v("importVCardActivity", "Prepare to review the imported contact");

                            if (createdUri != null) {
                                // get contact_id of this raw_contact
                                final long rawContactId = ContentUris.parseId(createdUri);
                                Uri contactUri = RawContacts.getContactLookupUri(
                                        getContentResolver(), ContentUris.withAppendedId(
                                                RawContacts.CONTENT_URI, rawContactId));

                                Intent viewIntent = new Intent(Intent.ACTION_VIEW, contactUri);
                                startActivity(viewIntent);
                            }
                        }
                    } else {
                        StringBuilder builder = new StringBuilder();
                        boolean first = true;
                        for (String fileName : mErrorFileNameList) {
                            if (first) {
                                first = false;
                            } else {
                                builder.append(", ");
                            }
                            builder.append(fileName);
                        }

                        runOnUIThread(new DialogDisplayer(
                                getString(R.string.fail_reason_failed_to_read_files,
                                        builder.toString())));
                    }
                }
            }
        }

        private Uri doActuallyReadOneVCard(Uri uri, Account account,
                boolean showEntryParseProgress,
                VCardSourceDetector detector, List<String> errorFileNameList) {
            final Context context = ImportVCardActivity.this;
            int vcardType = detector.getEstimatedType();
            if (vcardType == VCardConfig.VCARD_TYPE_UNKNOWN) {
                vcardType = VCardConfig.getVCardTypeFromString(
                        context.getString(R.string.config_import_vcard_type));
            }
            final String estimatedCharset = detector.getEstimatedCharset();
            VCardEntryConstructor builder;
            builder = new VCardEntryConstructor(vcardType, mAccount, estimatedCharset);
            final VCardEntryCommitter committer = new VCardEntryCommitter(mResolver);
            builder.addEntryHandler(committer);
            if (showEntryParseProgress) {
                builder.addEntryHandler(new ProgressShower(mProgressDialogForReadVCard,
                        context.getString(R.string.reading_vcard_message),
                        ImportVCardActivity.this,
                        mHandler));
            }

            try {
                if (!readOneVCardFile(uri, vcardType, builder, false, null)) {
                    return null;
                }
            } catch (VCardNestedException e) {
                Log.e(LOG_TAG, "Never reach here.");
            }
            final ArrayList<Uri> createdUris = committer.getCreatedUris();
            return (createdUris == null || createdUris.size() != 1) ? null : createdUris.get(0);
        }

        /**
         * Charset should be handled by {@link VCardEntryConstructor}. 
         */
        private boolean readOneVCardFile(Uri uri, int vcardType,
                VCardInterpreter interpreter,
                boolean throwNestedException, List<String> errorFileNameList)
                throws VCardNestedException {
            InputStream is;
            try {
                is = mResolver.openInputStream(uri);
                mVCardParser = new VCardParser_V21(vcardType);

                try {
                    mVCardParser.parse(is, interpreter);
                } catch (VCardVersionException e1) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                    if (interpreter instanceof VCardEntryConstructor) {
                        // Let the object clean up internal temporal objects,
                        ((VCardEntryConstructor)interpreter).clear();
                    } else if (interpreter instanceof VCardInterpreterCollection) {
                        for (VCardInterpreter elem :
                            ((VCardInterpreterCollection) interpreter).getCollection()) {
                            if (elem instanceof VCardEntryConstructor) {
                                ((VCardEntryConstructor)elem).clear();
                            }
                        }
                    }

                    is = mResolver.openInputStream(uri);

                    try {
                        mVCardParser = new VCardParser_V30(vcardType);
                        mVCardParser.parse(is, interpreter);
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
            } catch (IOException e) {
                Log.e(LOG_TAG, "IOException was emitted: " + e.getMessage());

                mProgressDialogForReadVCard.dismiss();

                if (errorFileNameList != null) {
                    errorFileNameList.add(uri.toString());
                } else {
                    runOnUIThread(new DialogDisplayer(
                            getString(R.string.fail_reason_io_error) +
                                    ": " + e.getLocalizedMessage()));
                }
                return false;
            } catch (VCardNotSupportedException e) {
                if ((e instanceof VCardNestedException) && throwNestedException) {
                    throw (VCardNestedException)e;
                }
                if (errorFileNameList != null) {
                    errorFileNameList.add(uri.toString());
                } else {
                    runOnUIThread(new DialogDisplayer(
                            getString(R.string.fail_reason_vcard_not_supported_error) +
                            " (" + e.getMessage() + ")"));
                }
                return false;
            } catch (VCardException e) {
                if (errorFileNameList != null) {
                    errorFileNameList.add(uri.toString());
                } else {
                    runOnUIThread(new DialogDisplayer(
                            getString(R.string.fail_reason_vcard_parse_error) +
                            " (" + e.getMessage() + ")"));
                }
                return false;
            }
            return true;
        }

        public void cancel() {
            mCanceled = true;
            if (mVCardParser != null) {
                mVCardParser.cancel();
            }
        }

        public void onCancel(DialogInterface dialog) {
            cancel();
        }
    }

    private class ImportTypeSelectedListener implements
            DialogInterface.OnClickListener {
        public static final int IMPORT_ONE = 0;
        public static final int IMPORT_MULTIPLE = 1;
        public static final int IMPORT_ALL = 2;
        public static final int IMPORT_TYPE_SIZE = 3;

        private int mCurrentIndex;

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                switch (mCurrentIndex) {
                case IMPORT_ALL:
                    importMultipleVCardFromSDCard(mAllVCardFileList);
                    break;
                case IMPORT_MULTIPLE:
                    showDialog(R.id.dialog_select_multiple_vcard);
                    break;
                default:
                    showDialog(R.id.dialog_select_one_vcard);
                    break;
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                finish();
            } else {
                mCurrentIndex = which;
            }
        }
    }

    private class VCardSelectedListener implements
            DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
        private int mCurrentIndex;
        private Set<Integer> mSelectedIndexSet;

        public VCardSelectedListener(boolean multipleSelect) {
            mCurrentIndex = 0;
            if (multipleSelect) {
                mSelectedIndexSet = new HashSet<Integer>();
            }
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                if (mSelectedIndexSet != null) {
                    List<VCardFile> selectedVCardFileList = new ArrayList<VCardFile>();
                    int size = mAllVCardFileList.size();
                    // We'd like to sort the files by its index, so we do not use Set iterator.
                    for (int i = 0; i < size; i++) {
                        if (mSelectedIndexSet.contains(i)) {
                            selectedVCardFileList.add(mAllVCardFileList.get(i));
                        }
                    }
                    importMultipleVCardFromSDCard(selectedVCardFileList);
                } else {
                    String canonicalPath = mAllVCardFileList.get(mCurrentIndex).getCanonicalPath();
                    final Uri uri = Uri.parse("file://" + canonicalPath);
                    importOneVCardFromSDCard(uri);
                }
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                finish();
            } else {
                // Some file is selected.
                mCurrentIndex = which;
                if (mSelectedIndexSet != null) {
                    if (mSelectedIndexSet.contains(which)) {
                        mSelectedIndexSet.remove(which);
                    } else {
                        mSelectedIndexSet.add(which);
                    }
                }
            }
        }

        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
            if (mSelectedIndexSet == null || (mSelectedIndexSet.contains(which) == isChecked)) {
                Log.e(LOG_TAG, String.format("Inconsist state in index %d (%s)", which,
                        mAllVCardFileList.get(which).getCanonicalPath()));
            } else {
                onClick(dialog, which);
            }
        }
    }

    /**
     * Thread scanning VCard from SDCard. After scanning, the dialog which lets a user select
     * a vCard file is shown. After the choice, VCardReadThread starts running.
     */
    private class VCardScanThread extends Thread implements OnCancelListener, OnClickListener {
        private boolean mCanceled;
        private boolean mGotIOException;
        private File mRootDirectory;

        // To avoid recursive link.
        private Set<String> mCheckedPaths;
        private PowerManager.WakeLock mWakeLock;

        private class CanceledException extends Exception {
            private static final long serialVersionUID = 1L;
        }

        public VCardScanThread(File sdcardDirectory) {
            mCanceled = false;
            mGotIOException = false;
            mRootDirectory = sdcardDirectory;
            mCheckedPaths = new HashSet<String>();
            PowerManager powerManager = (PowerManager)ImportVCardActivity.this.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void run() {
            mAllVCardFileList = new Vector<VCardFile>();
            try {
                mWakeLock.acquire();
                getVCardFileRecursively(mRootDirectory);
            } catch (CanceledException e) {
                mCanceled = true;
            } catch (IOException e) {
                mGotIOException = true;
            } finally {
                mWakeLock.release();
            }

            if (mCanceled) {
                mAllVCardFileList = null;
            }

            mProgressDialogForScanVCard.dismiss();
            mProgressDialogForScanVCard = null;

            if (mGotIOException) {
                runOnUIThread(new DialogDisplayer(R.id.dialog_io_exception));
            } else if (mCanceled) {
                finish();
            } else {
                int size = mAllVCardFileList.size();
                if (size == 0) {
                    runOnUIThread(new DialogDisplayer(R.id.dialog_vcard_not_found));
                } else {
                    startVCardSelectAndImport();
                }
            }
        }

        private void getVCardFileRecursively(File directory)
                throws CanceledException, IOException {
            if (mCanceled) {
                throw new CanceledException();
            }

            // e.g. secured directory may return null toward listFiles().
            final File[] files = directory.listFiles();
            if (files == null) {
                Log.w(LOG_TAG, "listFiles() returned null (directory: " + directory + ")");
                return;
            }
            for (File file : directory.listFiles()) {
                if (mCanceled) {
                    throw new CanceledException();
                }
                String canonicalPath = file.getCanonicalPath();
                if (mCheckedPaths.contains(canonicalPath)) {
                    continue;
                }

                mCheckedPaths.add(canonicalPath);

                if (file.isDirectory()) {
                    getVCardFileRecursively(file);
                } else if (canonicalPath.toLowerCase().endsWith(".vcf") &&
                        file.canRead()){
                    String fileName = file.getName();
                    VCardFile vcardFile = new VCardFile(
                            fileName, canonicalPath, file.lastModified());
                    mAllVCardFileList.add(vcardFile);
                }
            }
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
            }
        }
    }

    private void startVCardSelectAndImport() {
        int size = mAllVCardFileList.size();
        if (getResources().getBoolean(R.bool.config_import_all_vcard_from_sdcard_automatically)) {
            importMultipleVCardFromSDCard(mAllVCardFileList);
        } else if (size == 1) {
            String canonicalPath = mAllVCardFileList.get(0).getCanonicalPath();
            Uri uri = Uri.parse("file://" + canonicalPath);
            importOneVCardFromSDCard(uri);
        } else if (getResources().getBoolean(R.bool.config_allow_users_select_all_vcard_import)) {
            runOnUIThread(new DialogDisplayer(R.id.dialog_select_import_type));
        } else {
            runOnUIThread(new DialogDisplayer(R.id.dialog_select_one_vcard));
        }
    }

    private void importMultipleVCardFromSDCard(final List<VCardFile> selectedVCardFileList) {
        runOnUIThread(new Runnable() {
            public void run() {
                mVCardReadThread = new VCardReadThread(selectedVCardFileList);
                showDialog(R.id.dialog_reading_vcard);
            }
        });
    }

    private void importOneVCardFromSDCard(final Uri uri) {
        runOnUIThread(new Runnable() {
            public void run() {
                mVCardReadThread = new VCardReadThread(uri);
                showDialog(R.id.dialog_reading_vcard);
            }
        });
    }

    private Dialog getSelectImportTypeDialog() {
        DialogInterface.OnClickListener listener =
            new ImportTypeSelectedListener();
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(R.string.select_vcard_title)
            .setPositiveButton(android.R.string.ok, listener)
            .setOnCancelListener(mCancelListener)
            .setNegativeButton(android.R.string.cancel, mCancelListener);

        String[] items = new String[ImportTypeSelectedListener.IMPORT_TYPE_SIZE];
        items[ImportTypeSelectedListener.IMPORT_ONE] =
            getString(R.string.import_one_vcard_string);
        items[ImportTypeSelectedListener.IMPORT_MULTIPLE] =
            getString(R.string.import_multiple_vcard_string);
        items[ImportTypeSelectedListener.IMPORT_ALL] =
            getString(R.string.import_all_vcard_string);
        builder.setSingleChoiceItems(items, ImportTypeSelectedListener.IMPORT_ONE, listener);
        return builder.create();
    }

    private Dialog getVCardFileSelectDialog(boolean multipleSelect) {
        int size = mAllVCardFileList.size();
        VCardSelectedListener listener = new VCardSelectedListener(multipleSelect);
        AlertDialog.Builder builder =
            new AlertDialog.Builder(this)
                .setTitle(R.string.select_vcard_title)
                .setPositiveButton(android.R.string.ok, listener)
                .setOnCancelListener(mCancelListener)
                .setNegativeButton(android.R.string.cancel, mCancelListener);

        CharSequence[] items = new CharSequence[size];
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < size; i++) {
            VCardFile vcardFile = mAllVCardFileList.get(i);
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            stringBuilder.append(vcardFile.getName());
            stringBuilder.append('\n');
            int indexToBeSpanned = stringBuilder.length();
            // Smaller date text looks better, since each file name becomes easier to read.
            // The value set to RelativeSizeSpan is arbitrary. You can change it to any other
            // value (but the value bigger than 1.0f would not make nice appearance :)
            stringBuilder.append(
                        "(" + dateFormat.format(new Date(vcardFile.getLastModified())) + ")");
            stringBuilder.setSpan(
                    new RelativeSizeSpan(0.7f), indexToBeSpanned, stringBuilder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            items[i] = stringBuilder;
        }
        if (multipleSelect) {
            builder.setMultiChoiceItems(items, (boolean[])null, listener);
        } else {
            builder.setSingleChoiceItems(items, 0, listener);
        }
        return builder.create();
    }

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        final Intent intent = getIntent();
        if (intent != null) {
            final String accountName = intent.getStringExtra("account_name");
            final String accountType = intent.getStringExtra("account_type");
            if (!TextUtils.isEmpty(accountName) && !TextUtils.isEmpty(accountType)) {
                mAccount = new Account(accountName, accountType);
            }
        } else {
            Log.e(LOG_TAG, "intent does not exist");
        }

        // The caller often does not know account information at all, so we show the UI instead.
        if (mAccount == null) {
            // There's three possibilities:
            // - more than one accounts -> ask the user
            // - just one account -> use the account without asking the user
            // - no account -> use phone-local storage without asking the user
            final Sources sources = Sources.getInstance(this);
            final List<Account> accountList = sources.getAccounts(true);
            final int size = accountList.size();
            if (size > 1) {
                final int resId = R.string.import_from_sdcard;
                mAccountSelectionListener =
                    new AccountSelectionUtil.AccountSelectedListener(
                            this, accountList, resId) {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        mAccount = mAccountList.get(which);
                        // Instead of using Intent mechanism, call the relevant private method,
                        // to avoid throwing an Intent to itself again.
                        startImport();
                    }
                };
                showDialog(resId);
                return;
            } else {
                mAccount = size > 0 ? accountList.get(0) : null;
            }
        }

        startImport();
    }

    private void startImport() {
        Intent intent = getIntent();
        final String action = intent.getAction();
        final Uri uri = intent.getData();
        Log.v(LOG_TAG, "action = " + action + " ; path = " + uri);
        if (Intent.ACTION_VIEW.equals(action)) {
            // Import the file directly and then go to EDIT screen
            mNeedReview = true;
        }

        if (uri != null) {
            importOneVCardFromSDCard(uri);
        } else {
            doScanExternalStorageAndImportVCard();
        }
    }

    @Override
    protected Dialog onCreateDialog(int resId, Bundle args) {
        switch (resId) {
            case R.string.import_from_sdcard: {
                if (mAccountSelectionListener == null) {
                    throw new NullPointerException(
                            "mAccountSelectionListener must not be null.");
                }
                return AccountSelectionUtil.getSelectAccountDialog(this, resId,
                        mAccountSelectionListener,
                        new CancelListener());
            }
            case R.id.dialog_searching_vcard: {
                if (mProgressDialogForScanVCard == null) {
                    String title = getString(R.string.searching_vcard_title);
                    String message = getString(R.string.searching_vcard_message);
                    mProgressDialogForScanVCard =
                        ProgressDialog.show(this, title, message, true, false);
                    mProgressDialogForScanVCard.setOnCancelListener(mVCardScanThread);
                    mVCardScanThread.start();
                }
                return mProgressDialogForScanVCard;
            }
            case R.id.dialog_sdcard_not_found: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.no_sdcard_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.no_sdcard_message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
            case R.id.dialog_vcard_not_found: {
                String message = (getString(R.string.scanning_sdcard_failed_message,
                        getString(R.string.fail_reason_no_vcard_file)));
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.scanning_sdcard_failed_title)
                    .setMessage(message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
            case R.id.dialog_select_import_type: {
                return getSelectImportTypeDialog();
            }
            case R.id.dialog_select_multiple_vcard: {
                return getVCardFileSelectDialog(true);
            }
            case R.id.dialog_select_one_vcard: {
                return getVCardFileSelectDialog(false);
            }
            case R.id.dialog_reading_vcard: {
                if (mProgressDialogForReadVCard == null) {
                    String title = getString(R.string.reading_vcard_title);
                    String message = getString(R.string.reading_vcard_message);
                    mProgressDialogForReadVCard = new ProgressDialog(this);
                    mProgressDialogForReadVCard.setTitle(title);
                    mProgressDialogForReadVCard.setMessage(message);
                    mProgressDialogForReadVCard.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialogForReadVCard.setOnCancelListener(mVCardReadThread);
                    mVCardReadThread.start();
                }
                return mProgressDialogForReadVCard;
            }
            case R.id.dialog_io_exception: {
                String message = (getString(R.string.scanning_sdcard_failed_message,
                        getString(R.string.fail_reason_io_error)));
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.scanning_sdcard_failed_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
            case R.id.dialog_error_with_message: {
                String message = mErrorMessage;
                if (TextUtils.isEmpty(message)) {
                    Log.e(LOG_TAG, "Error message is null while it must not.");
                    message = getString(R.string.fail_reason_unknown);
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.reading_vcard_failed_title))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(message)
                    .setOnCancelListener(mCancelListener)
                    .setPositiveButton(android.R.string.ok, mCancelListener);
                return builder.create();
            }
        }

        return super.onCreateDialog(resId, args);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVCardReadThread != null) {
            // The Activity is no longer visible. Stop the thread.
            mVCardReadThread.cancel();
            mVCardReadThread = null;
        }

        // ImportVCardActivity should not be persistent. In other words, if there's some
        // event calling onPause(), this Activity should finish its work and give the main
        // screen back to the caller Activity.
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        // The code assumes the handler runs on the UI thread. If not,
        // clearing the message queue is not enough, one would have to
        // make sure that the handler does not run any callback when
        // this activity isFinishing().

        // Need to make sure any worker thread is done before we flush and
        // nullify the message handler.
        if (mVCardReadThread != null) {
            Log.w(LOG_TAG, "VCardReadThread exists while this Activity is now being killed!");
            mVCardReadThread.cancel();
            int attempts = 0;
            while (mVCardReadThread.isAlive() && attempts < 10) {
                try {
                    Thread.currentThread().sleep(20);
                } catch (InterruptedException ie) {
                    // Keep on going until max attempts is reached.
                }
                attempts++;
            }
            if (mVCardReadThread.isAlive()) {
                // Find out why the thread did not exit in a timely
                // fashion. Last resort: increase the sleep duration
                // and/or the number of attempts.
                Log.e(LOG_TAG, "VCardReadThread is still alive after max attempts.");
            }
            mVCardReadThread = null;
        }

        // Callbacks messages have what == 0.
        if (mHandler.hasMessages(0)) {
            mHandler.removeMessages(0);
        }

        mHandler = null;  // Prevents memory leaks by breaking any circular dependency.
        super.onDestroy();
    }

    /**
     * Tries to run a given Runnable object when the UI thread can. Ignore it otherwise
     */
    private void runOnUIThread(Runnable runnable) {
        if (mHandler == null) {
            Log.w(LOG_TAG, "Handler object is null. No dialog is shown.");
        } else {
            mHandler.post(runnable);
        }
    }

    @Override
    public void finalize() {
        // TODO: This should not be needed. Throw exception instead.
        if (mVCardReadThread != null) {
            // Not sure this procedure is really needed, but just in case...
            Log.e(LOG_TAG, "VCardReadThread exists while this Activity is now being killed!");
            mVCardReadThread.cancel();
            mVCardReadThread = null;
        }
    }

    /**
     * Scans vCard in external storage (typically SDCard) and tries to import it.
     * - When there's no SDCard available, an error dialog is shown.
     * - When multiple vCard files are available, asks a user to select one.
     */
    private void doScanExternalStorageAndImportVCard() {
        // TODO: should use getExternalStorageState().
        final File file = Environment.getExternalStorageDirectory();
        if (!file.exists() || !file.isDirectory() || !file.canRead()) {
            showDialog(R.id.dialog_sdcard_not_found);
        } else {
            mVCardScanThread = new VCardScanThread(file);
            showDialog(R.id.dialog_searching_vcard);
        }
    }
}
