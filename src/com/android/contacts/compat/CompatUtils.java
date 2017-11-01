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
 * limitations under the License.
 */
package com.android.contacts.compat;

import android.os.Build;
import android.os.Build.VERSION;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.model.CPOWrapper;

import java.lang.reflect.InvocationTargetException;

public final class CompatUtils {

    private static final String TAG = CompatUtils.class.getSimpleName();

    /**
     * These 4 variables are copied from ContentProviderOperation for compatibility.
     */
    public final static int TYPE_INSERT = 1;

    public final static int TYPE_UPDATE = 2;

    public final static int TYPE_DELETE = 3;

    public final static int TYPE_ASSERT = 4;

    /**
     * Returns whether the operation in CPOWrapper is of TYPE_INSERT;
     */
    public static boolean isInsertCompat(CPOWrapper cpoWrapper) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return cpoWrapper.getOperation().isInsert();
        }
        return (cpoWrapper.getType() == TYPE_INSERT);
    }

    /**
     * Returns whether the operation in CPOWrapper is of TYPE_UPDATE;
     */
    public static boolean isUpdateCompat(CPOWrapper cpoWrapper) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return cpoWrapper.getOperation().isUpdate();
        }
        return (cpoWrapper.getType() == TYPE_UPDATE);
    }

    /**
     * Returns whether the operation in CPOWrapper is of TYPE_DELETE;
     */
    public static boolean isDeleteCompat(CPOWrapper cpoWrapper) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return cpoWrapper.getOperation().isDelete();
        }
        return (cpoWrapper.getType() == TYPE_DELETE);
    }
    /**
     * Returns whether the operation in CPOWrapper is of TYPE_ASSERT;
     */
    public static boolean isAssertQueryCompat(CPOWrapper cpoWrapper) {
        if (SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M) >= Build.VERSION_CODES.M) {
            return cpoWrapper.getOperation().isAssertQuery();
        }
        return (cpoWrapper.getType() == TYPE_ASSERT);
    }

    /**
     * PrioritizedMimeType is added in API level 23.
     */
    public static boolean hasPrioritizedMimeType() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with multi-SIM and the phone account APIs. Can also
     * force the version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if multi-SIM capability is available, {@code false} otherwise.
     */
    public static boolean isMSIMCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    /**
     * Determines if this version is compatible with video calling. Can also force the version to be
     * lower through SdkVersionOverride.
     *
     * @return {@code true} if video calling is allowed, {@code false} otherwise.
     */
    public static boolean isVideoCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is capable of using presence checking for video calling. Support
     * for video call presence indication is added in SDK 24.
     *
     * @return {@code true} if video presence checking is allowed, {@code false} otherwise.
     */
    public static boolean isVideoPresenceCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.M)
                > Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with call subject. Can also force the version to be
     * lower through SdkVersionOverride.
     *
     * @return {@code true} if call subject is a feature on this device, {@code false} otherwise.
     */
    public static boolean isCallSubjectCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with a default dialer. Can also force the version to
     * be lower through {@link SdkVersionOverride}.
     *
     * @return {@code true} if default dialer is a feature on this device, {@code false} otherwise.
     */
    public static boolean isDefaultDialerCompatible() {
        return isMarshmallowCompatible();
    }

    /**
     * Determines if this version is compatible with Lollipop Mr1-specific APIs. Can also force the
     * version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if runtime sdk is compatible with Lollipop MR1, {@code false} otherwise.
     */
    public static boolean isLollipopMr1Compatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP_MR1)
                >= Build.VERSION_CODES.LOLLIPOP_MR1;
    }

    /**
     * Determines if this version is compatible with Marshmallow-specific APIs. Can also force the
     * version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if runtime sdk is compatible with Marshmallow, {@code false} otherwise.
     */
    public static boolean isMarshmallowCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.M;
    }

    /**
     * Determines if this version is compatible with N-specific APIs.
     *
     * @return {@code true} if runtime sdk is compatible with N and the app is built with N, {@code
     * false} otherwise.
     */
    public static boolean isNCompatible() {
        return VERSION.SDK_INT >= 24;
    }


    public static boolean isNougatMr1Compatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.N_MR1)
                >= Build.VERSION_CODES.N_MR1;
    }

    public static boolean isLauncherShortcutCompatible() {
        return isNougatMr1Compatible();
    }

    /**
     * Determines if the given class is available. Can be used to check if system apis exist at
     * runtime.
     *
     * @param className the name of the class to look for.
     * @return {@code true} if the given class is available, {@code false} otherwise or if className
     * is empty.
     */
    public static boolean isClassAvailable(@Nullable String className) {
        if (TextUtils.isEmpty(className)) {
            return false;
        }
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception when checking if class:" + className + " exists at "
                    + "runtime", t);
            return false;
        }
    }

    /**
     * Determines if the given class's method is available to call. Can be used to check if system
     * apis exist at runtime.
     *
     * @param className the name of the class to look for
     * @param methodName the name of the method to look for
     * @param parameterTypes the needed parameter types for the method to look for
     * @return {@code true} if the given class is available, {@code false} otherwise or if className
     * or methodName are empty.
     */
    public static boolean isMethodAvailable(@Nullable String className, @Nullable String methodName,
            Class<?>... parameterTypes) {
        if (TextUtils.isEmpty(className) || TextUtils.isEmpty(methodName)) {
            return false;
        }

        try {
            Class.forName(className).getMethod(methodName, parameterTypes);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Could not find method: " + className + "#" + methodName);
            }
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception when checking if method: " + className + "#"
                    + methodName + " exists at runtime", t);
            return false;
        }
    }

    /**
     * Invokes a given class's method using reflection. Can be used to call system apis that exist
     * at runtime but not in the SDK.
     *
     * @param instance The instance of the class to invoke the method on.
     * @param methodName The name of the method to invoke.
     * @param parameterTypes The needed parameter types for the method.
     * @param parameters The parameter values to pass into the method.
     * @return The result of the invocation or {@code null} if instance or methodName are empty, or
     * if the reflection fails.
     */
    @Nullable
    public static Object invokeMethod(@Nullable Object instance, @Nullable String methodName,
            Class<?>[] parameterTypes, Object[] parameters) {
        if (instance == null || TextUtils.isEmpty(methodName)) {
            return null;
        }

        String className = instance.getClass().getName();
        try {
            return Class.forName(className).getMethod(methodName, parameterTypes)
                    .invoke(instance, parameters);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalArgumentException
                | IllegalAccessException | InvocationTargetException e) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Could not invoke method: " + className + "#" + methodName);
            }
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception when invoking method: " + className
                    + "#" + methodName + " at runtime", t);
            return null;
        }
    }

    /**
     * Determines if this version is compatible with Lollipop-specific APIs. Can also force the
     * version to be lower through SdkVersionOverride.
     *
     * @return {@code true} if call subject is a feature on this device, {@code false} otherwise.
     */
    public static boolean isLollipopCompatible() {
        return SdkVersionOverride.getSdkVersion(Build.VERSION_CODES.LOLLIPOP)
                >= Build.VERSION_CODES.LOLLIPOP;
    }
}
