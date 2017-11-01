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
package com.android.contacts.logging;

import com.google.common.base.MoreObjects;

public class EditorEvent {

    /** The editor event type that is logged. */
    public int eventType;

    /** The number of raw contacts shown in the raw contacts picker. */
    public int numberRawContacts;

    public static final class EventType {
        public static final int UNKNOWN = 0;
        public static final int SHOW_RAW_CONTACT_PICKER = 1;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("eventType", eventType)
                .add("numberRawContacts", numberRawContacts)
                .toString();
    }
}
