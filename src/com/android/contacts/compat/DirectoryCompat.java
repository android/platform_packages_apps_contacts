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

package com.android.contacts.compat;

import android.net.Uri;
import android.provider.ContactsContract.Directory;

import com.android.contacts.ContactsUtils;

public class DirectoryCompat {

    public static Uri getContentUri() {
        if (ContactsUtils.FLAG_N_FEATURE) {
            return DirectorySdkCompat.ENTERPRISE_CONTENT_URI;
        }
        return Directory.CONTENT_URI;
    }

    public static boolean isInvisibleDirectory(long directoryId) {
        if (ContactsUtils.FLAG_N_FEATURE) {
            return (directoryId == Directory.LOCAL_INVISIBLE
                    || directoryId == DirectorySdkCompat.ENTERPRISE_LOCAL_INVISIBLE);
        }
        return directoryId == Directory.LOCAL_INVISIBLE;
    }

    public static boolean isRemoteDirectoryId(long directoryId) {
        if (ContactsUtils.FLAG_N_FEATURE) {
            return DirectorySdkCompat.isRemoteDirectoryId(directoryId);
        }
        return !(directoryId == Directory.DEFAULT || directoryId == Directory.LOCAL_INVISIBLE);
    }

    public static boolean isEnterpriseDirectoryId(long directoryId) {
        return ContactsUtils.FLAG_N_FEATURE
                ? DirectorySdkCompat.isEnterpriseDirectoryId(directoryId)
                : false;
    }
}
