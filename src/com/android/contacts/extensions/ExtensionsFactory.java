/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.contacts.extensions;

import android.content.Context;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/*
 * A framework for adding extensions to Dialer. This class reads a property file from
 * assets/contacts_extensions.properties and loads extension classes that an app has defined. If
 * an extension class was not defined, null is returned.
 */
public class ExtensionsFactory {

    private static String TAG = "ExtensionsFactory";

    // Config filename for mappings of various class names to their custom
    // implementations.
    private static final String EXTENSIONS_PROPERTIES = "contacts_extensions.properties";

    private static final String EXTENDED_PHONE_DIRECTORIES_KEY = "extendedPhoneDirectories";

    private static Properties sProperties = null;
    private static ExtendedPhoneDirectoriesManager mExtendedPhoneDirectoriesManager = null;

    public static void init(Context context) {
        if (sProperties != null) {
            return;
        }
        try {
            final InputStream fileStream = context.getAssets().open(EXTENSIONS_PROPERTIES);
            sProperties = new Properties();
            sProperties.load(fileStream);
            fileStream.close();

            final String className = sProperties.getProperty(EXTENDED_PHONE_DIRECTORIES_KEY);
            if (className != null) {
                mExtendedPhoneDirectoriesManager = createInstance(className);
            } else {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, EXTENDED_PHONE_DIRECTORIES_KEY + " not found in properties file.");
                }
            }

        } catch (FileNotFoundException e) {
            // No custom extensions. Ignore.
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No custom extensions.");
            }
        } catch (IOException e) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, e.toString());
            }
        }
    }

    private static <T> T createInstance(String className) {
        try {
            Class<?> c = Class.forName(className);
            //noinspection unchecked
            return (T) c.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, className + ": unable to create instance.", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, className + ": unable to create instance.", e);
        } catch (InstantiationException e) {
            Log.e(TAG, className + ": unable to create instance.", e);
        }
        return null;
    }

    public static ExtendedPhoneDirectoriesManager getExtendedPhoneDirectoriesManager() {
        return mExtendedPhoneDirectoriesManager;
    }
}
