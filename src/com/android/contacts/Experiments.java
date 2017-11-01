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
package com.android.contacts;

/**
 * Experiment flag names.
 */
public final class Experiments {

    /**
     * Experiment to enable device account detection using CP2 queries
     */
    public static final String CP2_DEVICE_ACCOUNT_DETECTION_ENABLED =
            "Account__cp2_device_account_detection_enabled";

    /**
     * Flags for maximum content update time
     */
    public static final String DYNAMIC_MAX_CONTENT_CHANGE_UPDATE_DELAY_MILLIS =
            "Shortcuts__dynamic_max_content_change_update_delay_millis";

    /**
     * Flags for minimum content update time
     */
    public static final String DYNAMIC_MIN_CONTENT_CHANGE_UPDATE_DELAY_MILLIS =
            "Shortcuts__dynamic_min_content_change_update_delay_millis";

    /**
     * Flags for enabling video call from quick contact.
     */
    public static final String QUICK_CONTACT_VIDEO_CALL =
            "QuickContact__video_call_integration";

    /**
     * Flags for maximum time to show spinner for a contacts sync.
     */
    public static final String PULL_TO_REFRESH_CANCEL_REFRESH_MILLIS =
            "PullToRefresh__cancel_refresh_millis";

    private Experiments() {
    }
}
