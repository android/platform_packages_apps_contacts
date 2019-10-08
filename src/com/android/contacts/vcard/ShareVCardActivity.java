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
package com.android.contacts.vcard;

import android.content.ComponentName;
import android.net.Uri;
import android.os.IBinder;
import androidx.core.content.FileProvider;
import android.util.Log;

import com.android.contacts.R;
import com.android.contactsbind.FeedbackHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * This activity connects to VCardService, creates a .vcf file in cache directory and send export
 * request with the file URI so as to write contacts data to the file in background.
 */
public class ShareVCardActivity extends ExportVCardActivity {
    private static final String LOG_TAG = "VCardShare";
    private final String EXPORT_FILE_PREFIX = "vcards_";
    private final long A_DAY_IN_MILLIS = 1000 * 60 * 60 * 24;

    @Override
    public synchronized void onServiceConnected(ComponentName name, IBinder binder) {
        if (DEBUG) Log.d(LOG_TAG, "connected to service, requesting a destination file name");
        mConnected = true;
        mService = ((VCardService.MyBinder) binder).getService();

        clearExportFiles();

        final File file = getLocalFile();
        try {
            file.createNewFile();
        } catch (IOException e) {
            FeedbackHelper.sendFeedback(this, LOG_TAG, "Failed to create .vcf file", e);
            finish();
            return;
        }

        final Uri contentUri = FileProvider.getUriForFile(this,
                getString(R.string.contacts_file_provider_authority), file);
        if (DEBUG) Log.d(LOG_TAG, "exporting to " + contentUri);

        final ExportRequest request = new ExportRequest(contentUri);
        // The connection object will call finish().
        mService.handleExportRequest(request, new NotificationImportExportListener(
                ShareVCardActivity.this));
        finish();
    }

    /**
     * Delete the files (that are untouched for more than 1 day) in the cache directory.
     * We cannot rely on VCardService to delete export files because it will delete export files
     * right after finishing writing so no files could be shared. Therefore, our approach to
     * deleting export files is:
     * 1. put export files in cache directory so that Android may delete them;
     * 2. manually delete the files that are older than 1 day when service is connected.
     */
    private void clearExportFiles() {
        for (File file : getCacheDir().listFiles()) {
            final long ageInMillis = System.currentTimeMillis() - file.lastModified();
            if (file.getName().startsWith(EXPORT_FILE_PREFIX) && ageInMillis > A_DAY_IN_MILLIS) {
                file.delete();
            }
        }
    }

    private File getLocalFile() {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        final String currentDateString = dateFormat.format(new Date()).toString();
        final String localFilename = EXPORT_FILE_PREFIX + currentDateString + ".vcf";
        return new File(getCacheDir(), localFilename);
    }
}