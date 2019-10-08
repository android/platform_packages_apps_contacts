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
 * limitations under the License
 */
package com.android.contacts.compat;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import androidx.annotation.Nullable;
import android.telecom.PhoneAccount;
import android.util.Log;

/**
 * Compatiblity class for {@link android.telecom.PhoneAccount}
 */
public class PhoneAccountCompat {

    private static final String TAG = PhoneAccountCompat.class.getSimpleName();

    /**
     * Gets the {@link Icon} associated with the given {@link PhoneAccount}
     *
     * @param phoneAccount the PhoneAccount from which to retrieve the Icon
     * @return the Icon, or null
     */
    @Nullable
    public static Icon getIcon(@Nullable PhoneAccount phoneAccount) {
        if (phoneAccount == null) {
            return null;
        }

        if (CompatUtils.isMarshmallowCompatible()) {
            return phoneAccount.getIcon();
        }

        return null;
    }

    /**
     * Builds and returns an icon {@code Drawable} to represent this {@code PhoneAccount} in a user
     * interface.
     *
     * @param phoneAccount the PhoneAccount from which to build the icon.
     * @param context A {@code Context} to use for loading Drawables.
     *
     * @return An icon for this PhoneAccount, or null
     */
    @Nullable
    public static Drawable createIconDrawable(@Nullable PhoneAccount phoneAccount,
            @Nullable Context context) {
        if (phoneAccount == null || context == null) {
            return null;
        }

        if (CompatUtils.isMarshmallowCompatible()) {
            return createIconDrawableMarshmallow(phoneAccount, context);
        }

        if (CompatUtils.isLollipopMr1Compatible()) {
            return createIconDrawableLollipopMr1(phoneAccount, context);
        }
        return null;
    }

    @Nullable
    private static Drawable createIconDrawableMarshmallow(PhoneAccount phoneAccount,
            Context context) {
        Icon accountIcon = getIcon(phoneAccount);
        if (accountIcon == null) {
            return null;
        }
        return accountIcon.loadDrawable(context);
    }

    @Nullable
    private static Drawable createIconDrawableLollipopMr1(PhoneAccount phoneAccount,
            Context context) {
        try {
            return (Drawable) PhoneAccount.class.getMethod("createIconDrawable", Context.class)
                    .invoke(phoneAccount, context);
        } catch (ReflectiveOperationException e) {
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "Unexpected exception when attempting to call "
                    + "android.telecom.PhoneAccount#createIconDrawable", t);
            return null;
        }
    }
}
