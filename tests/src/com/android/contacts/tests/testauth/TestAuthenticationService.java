/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.contacts.tests.testauth;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public abstract class TestAuthenticationService extends Service {

    private TestAuthenticator mAuthenticator;

    @Override
    public void onCreate() {
        if (Log.isLoggable(TestauthConstants.LOG_TAG, Log.VERBOSE)) {
            Log.v(TestauthConstants.LOG_TAG, this + " Service started.");
        }
        mAuthenticator = new TestAuthenticator(this);
    }

    @Override
    public void onDestroy() {
        if (Log.isLoggable(TestauthConstants.LOG_TAG, Log.VERBOSE)) {
            Log.v(TestauthConstants.LOG_TAG, this + " Service stopped.");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (Log.isLoggable(TestauthConstants.LOG_TAG, Log.VERBOSE)) {
            Log.v(TestauthConstants.LOG_TAG, this + " getBinder() intent=" + intent);
        }
        return mAuthenticator.getIBinder();
    }

    public static class Basic extends TestAuthenticationService {
    }
}
