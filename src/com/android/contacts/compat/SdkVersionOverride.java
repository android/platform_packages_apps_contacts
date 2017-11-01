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
 * limitations under the License
 */

package com.android.contacts.compat;

import android.os.Build.VERSION;

/**
 * Class used to override the current sdk version to test specific branches of compatibility
 * logic. When such branching occurs, use {@link #getSdkVersion(int)} rather than explicitly
 * calling {@link VERSION#SDK_INT}. This allows the sdk version to be forced to a specific value.
 */
public class SdkVersionOverride {

    /**
     * Flag used to determine if override sdk versions are returned.
     */
    private static final boolean ALLOW_OVERRIDE_VERSION = false;

    private SdkVersionOverride() {}

    /**
     * Gets the sdk version
     *
     * @param overrideVersion the version to attempt using
     * @return overrideVersion if the {@link #ALLOW_OVERRIDE_VERSION} flag is set to {@code true},
     * otherwise the current version
     */
    public static int getSdkVersion(int overrideVersion) {
        return ALLOW_OVERRIDE_VERSION ? overrideVersion : VERSION.SDK_INT;
    }
}
