/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.Service;
import android.content.Intent;
import android.content.Loader;
import android.content.Loader.OnLoadCompleteListener;
import android.os.IBinder;
import android.util.Log;

import com.android.contacts.model.Contact;
import com.android.contacts.model.ContactLoader;


/**
 * Service that sends out a view notification for a contact. At the moment, this is only
 * supposed to be used by the Phone app
 */
public class ViewNotificationService extends Service {
    private static final String TAG = ViewNotificationService.class.getSimpleName();

    private static final boolean DEBUG = false;

    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        if (DEBUG) { Log.d(TAG, "onHandleIntent(). Intent: " + intent); }

        // We simply need to start a Loader here. When its done, it will send out the
        // View-Notification automatically.
        final ContactLoader contactLoader = new ContactLoader(this, intent.getData(), true);
        contactLoader.registerListener(0, new OnLoadCompleteListener<Contact>() {
            @Override
            public void onLoadComplete(Loader<Contact> loader, Contact data) {
                try {
                    loader.reset();
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error reseting loader", e);
                }
                try {
                    // This is not 100% accurate actually. If we get several calls quickly,
                    // we might be stopping out-of-order, in which case the call with the last
                    // startId will stop this service. In practice, this shouldn't be a problem,
                    // as this service is supposed to be called by the Phone app which only sends
                    // out the notification once per phonecall. And even if there is a problem,
                    // the worst that should happen is a missing view notification
                    stopSelfResult(startId);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Error stopping service", e);
                }
            }
        });
        contactLoader.startLoading();
        return START_REDELIVER_INTENT;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
