/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.provider.ContactsContract.StatusUpdates;

/**
 * Provides static function to get default contact status message.
 */
public class ContactStatusUtil {

    private static final String TAG = "ContactStatusUtil";

    public static String getStatusString(Context context, int presence) {
        Resources resources = context.getResources();
        switch (presence) {
            case StatusUpdates.AVAILABLE:
                return resources.getString(R.string.status_available);
            case StatusUpdates.IDLE:
            case StatusUpdates.AWAY:
                return resources.getString(R.string.status_away);
            case StatusUpdates.DO_NOT_DISTURB:
                return resources.getString(R.string.status_busy);
            case StatusUpdates.OFFLINE:
            case StatusUpdates.INVISIBLE:
            default:
                return null;
        }
    }

}
