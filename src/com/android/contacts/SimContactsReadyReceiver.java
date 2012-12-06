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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.android.contacts.common.SimContactsReadyHelper;

public class SimContactsReadyReceiver extends BroadcastReceiver {
    public static final String ACTION_SIM_CAPABILITY_INFO = "com.android.phone.SIM_CAPABILITY_INFO";
    private static final String TAG = SimContactsReadyReceiver.class.getSimpleName();
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_SIM_CAPABILITY_INFO.equals(intent.getAction())) {
            String sim1CapabilityInfo = intent.getStringExtra("sim1Info");
            String sim2CapabilityInfo = intent.getStringExtra("sim2Info");
            Log.i(TAG, "onReceive(): ACTION_SIM_CAPABILITY_INFO: sim1CapabilityInfo = " + sim1CapabilityInfo + ", sim2CapabilityInfo = " + sim2CapabilityInfo);

            SimContactsReadyHelper helper = new SimContactsReadyHelper(context, false);
            helper.setSimCapabilityInfo(sim1CapabilityInfo, sim2CapabilityInfo);
        }
    }
}
