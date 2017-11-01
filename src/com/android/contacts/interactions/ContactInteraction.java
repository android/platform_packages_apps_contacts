/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.contacts.interactions;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.Spannable;

/**
 * Represents a default interaction between the phone's owner and a contact
 */
public interface ContactInteraction {
    Intent getIntent();
    long getInteractionDate();
    String getViewHeader(Context context);
    String getViewBody(Context context);
    String getViewFooter(Context context);
    Drawable getIcon(Context context);
    Drawable getBodyIcon(Context context);
    Drawable getFooterIcon(Context context);
    Spannable getContentDescription(Context context);
    /** The resource id for the icon, if available. May be 0 if one is not available. */
    int getIconResourceId();
}
