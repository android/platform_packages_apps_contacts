/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.quickcontact;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import com.android.contacts.Collapser;

/**
 * Abstract definition of an action that could be performed, along with
 * string description and icon.
 */
public interface Action extends Collapser.Collapsible<Action> {
    public CharSequence getBody();
    public CharSequence getSubtitle();

    public String getMimeType();

    /** Returns an icon that can be clicked for the alternate action. */
    public Drawable getAlternateIcon();

    /** Returns the content description of the icon for the alternate action. */
    public String getAlternateIconDescription();

    /** Build an {@link Intent} that will perform this action. */
    public Intent getIntent();

    /** Build an {@link Intent} that will perform the alternate action. */
    public Intent getAlternateIntent();

    /** Checks if the contact data for this action is primary. */
    public boolean isPrimary();

    /** Checks if the contact data for this action is super primary. */
    public boolean isSuperPrimary();

    /**
     * Returns a lookup (@link Uri) for the contact data item or null if there is no data item
     * corresponding to this row
     */
    public Uri getDataUri();

    /**
     * Returns the id of the contact data item or -1 of there is no data item corresponding to this
     * row
     */
    public long getDataId();

    /** Returns the presence of this item or -1 if it was never set */
    public int getPresence();
}
