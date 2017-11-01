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
 * limitations under the License.
 */

package com.android.contacts.compat;

import android.telecom.Call;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CallSdkCompat {
    public static class Details {
        // TODO: This property is hidden in the N release; replace with actual when the API is
        // made public.
        public static final int PROPERTY_IS_EXTERNAL_CALL = 0x00000040;
        public static final int PROPERTY_ENTERPRISE_CALL = Call.Details.PROPERTY_ENTERPRISE_CALL;
        // TODO: This capability is hidden in the N release; replace with actual when the API is
        // made public.
        public static final int CAPABILITY_CAN_PULL_CALL = 0x00800000;
        public static final int CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO =
                Call.Details.CAPABILITY_CANNOT_DOWNGRADE_VIDEO_TO_AUDIO;
    }

    /**
     * TODO: This API is hidden in the N release; replace the implementation with a call to the
     * actual once it is made public.
     */
    public static void pullExternalCall(Call call) {
        if (!CompatUtils.isNCompatible()) {
            return;
        }
        Class<?> callClass = Call.class;
        try {
            Method pullExternalCallMethod = callClass.getDeclaredMethod("pullExternalCall");
            pullExternalCallMethod.invoke(call);
        } catch (NoSuchMethodException e) {
            // Ignore requests to pull call if there is a problem.
        } catch (InvocationTargetException e) {
            // Ignore requests to pull call if there is a problem.
        } catch (IllegalAccessException e) {
            // Ignore requests to pull call if there is a problem.
        }
    }
}
