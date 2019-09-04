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
package com.android.contacts;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.os.Debug;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Runs a single static method specified via the arguments.
 *
 * Useful for manipulating the app state during manual testing. If the class argument is omitted
 * this class will attempt to invoke a method in
 * {@link com.android.contacts.tests.AdbHelpers}
 *
 * Valid signatures: void f(Context, Bundle), void f(Context), void f()
 *
 * Example usage:
 * $ adb shell am instrument -e class com.android.contacts.Foo -e method bar -e someArg someValue\
 *   -w com.google.android.contacts.tests/com.android.contacts.RunMethodInstrumentation
 */
public class RunMethodInstrumentation extends Instrumentation {

    private static final String TAG = "RunMethod";

    private static final String DEFAULT_CLASS = "AdbHelpers";

    private String className;
    private String methodName;
    private Bundle args;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);

        InstrumentationRegistry.registerInstance(this, arguments);

        className = arguments.getString("class", getContext().getPackageName() + "." +
                DEFAULT_CLASS);
        methodName = arguments.getString("method");
        args = arguments;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Running " + className + "." + methodName);
            Log.d(TAG, "args=" + args);
        }

        if (arguments.containsKey("debug") && Boolean.parseBoolean(arguments.getString("debug"))) {
            Debug.waitForDebugger();
        }
        start();
    }

    @Override
    public void onStart() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onStart");
        }
        super.onStart();

        if (className == null || methodName == null) {
            Log.e(TAG, "Must supply class and method");
            finish(Activity.RESULT_CANCELED, null);
            return;
        }

        // Wait for the Application to finish creating.
        runOnMainSync(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "acquired main thread from instrumentation");
                }
            }
        });

        try {
            invokeMethod(args, className, methodName);
        } catch (Exception e) {
            e.printStackTrace();
            finish(Activity.RESULT_CANCELED, null);
            return;
        }
        // Maybe should let the method determine when this is called.
        finish(Activity.RESULT_OK, null);
    }

    private void invokeMethod(Bundle args, String className, String methodName) throws
            InvocationTargetException, IllegalAccessException, NoSuchMethodException,
            ClassNotFoundException {
        Context context;
        Class<?> clazz = null;
        try {
            // Try to load from App's code
            clazz = getTargetContext().getClassLoader().loadClass(className);
            context = getTargetContext();
        } catch (Exception e) {
            // Try to load from Test App's code
            clazz = getContext().getClassLoader().loadClass(className);
            context = getContext();
        }

        Object[] methodArgs = null;
        Method method = null;

        try {
            method = clazz.getMethod(methodName, Context.class, Bundle.class);
            methodArgs = new Object[] { context, args };
        } catch (NoSuchMethodException e) {
        }

        if (method != null) {
            method.invoke(clazz, methodArgs);
            return;
        }

        try {
            method = clazz.getMethod(methodName, Context.class);
            methodArgs = new Object[] { context };
        } catch (NoSuchMethodException e) {
        }

        if (method != null) {
            method.invoke(clazz, methodArgs);
            return;
        }

        method = clazz.getMethod(methodName);
        method.invoke(clazz);
    }
}
