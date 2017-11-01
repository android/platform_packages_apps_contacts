/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.contacts.drawer;

import android.graphics.drawable.ColorDrawable;

/**
 * Create a simple scrim that covers just the status bar area when necessary.
 * Copied from com.google.android.gms.people.accountswitcherview.ScrimDrawable;
 */
public class ScrimDrawable extends ColorDrawable {
    public static final int DEFAULT_COLOR = 0x33000000;

    /**
     * Default constructor. Uses default color.
     */
    public ScrimDrawable() {
        this(DEFAULT_COLOR);
    }

    /**
     * Set a color if necessary.
     *
     * @param color
     */
    public ScrimDrawable(int color) {
        super(color);
    }

    private int mIntrinsicHeight;

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicHeight;
    }

    public void setIntrinsicHeight(int intrinsicHeight) {
        mIntrinsicHeight = intrinsicHeight;
    }
}