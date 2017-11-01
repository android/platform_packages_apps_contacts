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

public class DirectorySdkCompat {

    private static final String TAG = "DirectorySdkCompat";

    public static final Uri ENTERPRISE_CONTENT_URI = Directory.ENTERPRISE_CONTENT_URI;
    public static final long ENTERPRISE_LOCAL_DEFAULT = Directory.ENTERPRISE_DEFAULT;
    public static final long ENTERPRISE_LOCAL_INVISIBLE = Directory.ENTERPRISE_LOCAL_INVISIBLE;

    public static boolean isRemoteDirectoryId(long directoryId) {
        return CompatUtils.isNCompatible() ? Directory.isRemoteDirectoryId(directoryId) : false;
    }

    public static boolean isEnterpriseDirectoryId(long directoryId) {
        return CompatUtils.isNCompatible() ? Directory.isEnterpriseDirectoryId(directoryId) : false;
    }
}
