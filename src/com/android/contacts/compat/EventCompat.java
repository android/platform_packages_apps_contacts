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

import android.content.res.Resources;
import android.provider.ContactsContract.CommonDataKinds.BaseTypes;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.text.TextUtils;

/**
 * Compatibility class for {@link Event}
 */
public class EventCompat {
    /**
     * Not instantiable.
     */
    private EventCompat() {
    }

    /**
     * Return a {@link CharSequence} that best describes the given type, possibly substituting
     * the given label value for TYPE_CUSTOM.
     */
    public static CharSequence getTypeLabel(Resources res, int type, CharSequence label) {
        if (CompatUtils.isLollipopCompatible()) {
            return Event.getTypeLabel(res, type, label);
        } else {
            return getTypeLabelInternal(res, type, label);
        }
    }

    /**
     * The method was added in API level 21, and below is the implementation copied from
     * {@link Event#getTypeLabel(Resources, int, CharSequence)}
     */
    private static CharSequence getTypeLabelInternal(Resources res, int type, CharSequence label) {
        if (type == BaseTypes.TYPE_CUSTOM && !TextUtils.isEmpty(label)) {
            return label;
        } else {
            return res.getText(Event.getTypeResource(type));
        }
    }

}
