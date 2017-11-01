/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Background {@link Service} that is used to keep our process alive long enough
 * for background threads to finish. Started and stopped directly by specific
 * background tasks when needed.
 */
public class EmptyService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
