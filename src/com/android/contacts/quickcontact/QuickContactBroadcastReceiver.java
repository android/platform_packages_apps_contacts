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

package com.android.contacts.quickcontact;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract.QuickContact;

import com.android.contacts.util.ImplicitIntentsUtil;

/**
 * Broadcast receiver for invoking QuickContact using the widget. The purpose of this pass-through
 * intent receiver is to disable the animation that RemoveViews typically do, which interfere
 * with our own animation
 */
public class QuickContactBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Uri dataUri = intent.getData();
        final Intent newIntent = new Intent(QuickContact.ACTION_QUICK_CONTACT);
        newIntent.setSourceBounds(intent.getSourceBounds());
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        newIntent.setData(dataUri);
        ImplicitIntentsUtil.startActivityInApp(context, newIntent);
    }
}
