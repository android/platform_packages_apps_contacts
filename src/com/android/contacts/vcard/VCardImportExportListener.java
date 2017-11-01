/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.contacts.vcard;

import android.app.Notification;
import android.net.Uri;

import com.android.vcard.VCardEntry;

interface VCardImportExportListener {
    Notification onImportProcessed(ImportRequest request, int jobId, int sequence);
    Notification onImportParsed(ImportRequest request, int jobId, VCardEntry entry, int currentCount,
            int totalCount);
    void onImportFinished(ImportRequest request, int jobId, Uri uri);
    void onImportFailed(ImportRequest request);
    void onImportCanceled(ImportRequest request, int jobId);

    Notification onExportProcessed(ExportRequest request, int jobId);
    void onExportFailed(ExportRequest request);

    void onCancelRequest(CancelRequest request, int type);
}
