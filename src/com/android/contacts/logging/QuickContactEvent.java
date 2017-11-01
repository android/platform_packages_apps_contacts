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
 * Describes how user views and takes action in Quick contact
 */
public final class QuickContactEvent {

    /** The package name that QuickContact is launched from. **/
    public String referrer;

    /** The type of the contact displayed in QuickContact. **/
    public int contactType;

    /** The type of the card displayed in QuickContact. **/
    public int cardType;

    /** The type of the user action in QuickContact. **/
    public int actionType;

    /** The third party action that a user takes. **/
    public String thirdPartyAction;

    // Should match ContactsExtension.QuickContactEvent values in
    // http://cs/google3/logs/proto/wireless/android/contacts/contacts_extensions.proto
    public static final class ContactType {
        public static final int UNKNOWN_TYPE = 0;
        public static final int EDITABLE = 1;
        public static final int INVISIBLE_AND_ADDABLE = 2;
        public static final int DIRECTORY = 3;
    }

    public static final class CardType {
        public static final int UNKNOWN_CARD = 0;
        public static final int NO_CONTACT = 1;
        public static final int CONTACT = 2;
        public static final int RECENT = 3;
        public static final int ABOUT = 4;
        public static final int PERMISSION = 5;
    }

    public static final class ActionType {
        public static final int UNKNOWN_ACTION = 0;
        public static final int START = 1;
        public static final int STAR = 2;
        public static final int UNSTAR = 3;
        public static final int EDIT = 4;
        public static final int ADD = 5;
        public static final int REMOVE = 6;
        public static final int SHARE = 7;
        public static final int SHORTCUT = 8;
        public static final int HELP = 9;
        public static final int CALL = 10;
        public static final int SMS = 11;
        public static final int VIDEOCALL = 12;
        public static final int EMAIL = 13;
        public static final int SIPCALL = 14;
        public static final int ADDRESS = 15;
        public static final int DIRECTIONS = 16;
        public static final int THIRD_PARTY = 17;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("referrer", referrer)
                .add("contactType", contactType)
                .add("cardType", cardType)
                .add("actionType", actionType)
                .add("thirdPartyAction", thirdPartyAction)
                .toString();
    }
}
