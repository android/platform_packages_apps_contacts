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

/**
 * Stores constants identifying individual screens/dialogs/fragments in the application, and also
 * provides a mapping of integer id -> screen name mappings for analytics purposes.
 */
public class ScreenEvent {

    // Should match ContactsExtension.ScreenEvent.ScreenType values in
    // http://cs/google3/logs/proto/wireless/android/contacts/contacts_extensions.proto
    public static class ScreenType {
        public static final int UNKNOWN = 0;
        public static final int SEARCH = 1;
        public static final int SEARCH_EXIT = 2;
        public static final int FAVORITES = 3;
        public static final int ALL_CONTACTS = 4;
        public static final int QUICK_CONTACT = 5;
        public static final int EDITOR = 6;
        public static final int LIST_ACCOUNT = 8;
        public static final int LIST_GROUP = 9;
        public static final int ME_CONTACT = 10;
    }
}
