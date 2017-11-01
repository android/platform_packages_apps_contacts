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

package com.android.contactsbind;

import android.app.Activity;

/**
 * Utility for starting help and feedback activity. This stub class is designed to be overwritten
 * by an overlay.
 */
public class HelpUtils {

    /**
     * Returns {@code TRUE} if {@link @launchHelpAndFeedbackForMainScreen} and
     * {@link @launchHelpAndFeedbackForContactScreen} are implemented to start help and feedback
     * activities.
     */
    public static boolean isHelpAndFeedbackAvailable() {
        return false;
    }

    public static void launchHelpAndFeedbackForMainScreen(Activity activity) { }

    public static void launchHelpAndFeedbackForContactScreen(Activity activity) { }

}
