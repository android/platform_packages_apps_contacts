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

import android.widget.EdgeEffect;

/**
 * Compatibility class for {@link android.widget.EdgeEffect}
 * The androidx.core.widget.EdgeEffectCompat doesn't support customized color, so we write
 * our own and keep using EdgeEffect to customize color.
 */
public class EdgeEffectCompat {
    /**
     * Compatibility method for {@link EdgeEffect#onPull(float, float)}, which is only available
     * on Lollipop+.
     */
    public static void onPull(EdgeEffect edgeEffect, float deltaDistance, float displacement) {
        if (CompatUtils.isLollipopCompatible()) {
            edgeEffect.onPull(deltaDistance, displacement);
        } else {
            edgeEffect.onPull(deltaDistance);
        }
    }
}
