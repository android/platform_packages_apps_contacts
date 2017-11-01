/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.StatusUpdates;

/**
 * Define the contact present show policy in Contacts
 */
public class ContactPresenceIconUtil {
    /**
     * Get the presence icon resource according the status.
     *
     * @return null means don't show the status icon.
     */
    public static Drawable getPresenceIcon (Context context, int status) {
        // We don't show the offline status in Contacts
        switch(status) {
            case StatusUpdates.AVAILABLE:
            case StatusUpdates.IDLE:
            case StatusUpdates.AWAY:
            case StatusUpdates.DO_NOT_DISTURB:
            case StatusUpdates.INVISIBLE:
                return context.getResources().getDrawable(
                        StatusUpdates.getPresenceIconResourceId(status));
            case StatusUpdates.OFFLINE:
            // The undefined status is treated as OFFLINE in getPresenceIconResourceId();
            default:
                return null;
        }
    }
}
