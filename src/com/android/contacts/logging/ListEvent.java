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

/**
 * Describes how user view and use a list
 */
public final class ListEvent {

    /** The type of action taken by the user. **/
    public int actionType;

    /** The type of list the user is viewing. **/
    public int listType;

    /** The number of contacts in the list. **/
    public int count;

    /** The index of contact clicked by user. **/
    public int clickedIndex = -1;

    /** The number of contact selected when user takes an action (link, delete, share, etc). **/
    public int numSelected;

    // Should match ContactsExtension.ListEvent.ActionType values in
    // http://cs/google3/logs/proto/wireless/android/contacts/contacts_extensions.proto
    public static final class ActionType {
        public static final int UNKNOWN = 0;
        public static final int LOAD = 1;
        public static final int CLICK = 2;
        public static final int SELECT = 3;
        public static final int SHARE = 4;
        public static final int DELETE = 5;
        public static final int LINK = 6;
        public static final int REMOVE_LABEL = 7;

        private ActionType() {
        }
    }

    // Should match ContactsExtension.ListEvent.ListType values in
    // http://cs/google3/logs/proto/wireless/android/contacts/contacts_extensions.proto
    public static final class ListType {
        public static final int UNKNOWN_LIST = 0;
        public static final int ALL_CONTACTS = 1;
        public static final int ACCOUNT = 2;
        public static final int GROUP = 3;
        public static final int SEARCH_RESULT = 4;
        public static final int DEVICE = 5;
        public static final int CUSTOM = 6;
        public static final int STARRED = 7;
        public static final int PHONE_NUMBERS = 8;
        public static final int SINGLE_CONTACT = 9;
        public static final int PICK_CONTACT = 10;
        public static final int PICK_CONTACT_FOR_SHORTCUT = 11;
        public static final int PICK_PHONE = 12;
        public static final int PICK_EMAIL = 13;
        public static final int PICK_POSTAL = 14;
        public static final int PICK_JOIN = 15;
        public static final int PICK_GROUP_MEMBERS = 16;

        private ListType() {
        }
    }

    public ListEvent() {
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("actionType", actionType)
                .add("listType", listType)
                .add("count", count)
                .add("clickedIndex", clickedIndex)
                .add("numSelected", numSelected)
                .toString();
    }
}
