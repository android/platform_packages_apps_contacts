/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.contacts.util;

import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.core.os.BuildCompat;

import com.android.contacts.R;

@TargetApi(Build.VERSION_CODES.O)
public class ContactsNotificationChannelsUtil {
    public static String DEFAULT_CHANNEL = "DEFAULT_CHANNEL";

    private ContactsNotificationChannelsUtil() {}

    public static void createDefaultChannel(Context context) {
        if (!BuildCompat.isAtLeastO()) {
            return;
        }
        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        final NotificationChannel channel = new NotificationChannel(DEFAULT_CHANNEL,
                context.getString(R.string.contacts_default_notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
    }
}
