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
 * limitations under the License
 */
package com.android.contactsbind.experiments;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides getters for experiment flags.
 * This stub class is designed to be overwritten by an overlay.
 */
public final class Flags {

    private static Flags sInstance;

    private Map<String, Object> mMap;

    public static Flags getInstance() {
        if (sInstance == null) {
            sInstance = new Flags();
        }
        return sInstance;
    }

    private Flags() {
        mMap = new HashMap<>();
    }

    public boolean getBoolean(String flagName) {
        return mMap.containsKey(flagName) ? (boolean) mMap.get(flagName) : false;
    }

    public int getInteger(String flagName) {
        return mMap.containsKey(flagName) ? ((Integer) mMap.get(flagName)).intValue() : 0;
    }
}
