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

package com.android.contacts.list;

/**
 * Intent actions related to the Contacts app UI. In the past we decided to store these in a single
 * location in order to easily expose them via ContactsContract. We eventually decided
 * this wasn't useful.
 */
public class UiIntentActions {
    /**
     * The action for the default contacts list tab.
     */
    public static final String LIST_DEFAULT =
            "com.android.contacts.action.LIST_DEFAULT";

    /**
     * The action for contacts list tab.
     */
    public static final String LIST_CONTACTS =
            "com.android.contacts.action.LIST_CONTACTS";

    /**
     * The action for selecting multiple items (email, phone) from a list.
     */
    public static final String ACTION_SELECT_ITEMS =
            "com.android.contacts.action.ACTION_SELECT_ITEMS";

    /**
     * The action for the contacts list tab.
     */
    public static final String LIST_GROUP_ACTION =
            "com.android.contacts.action.LIST_GROUP";

    /**
     * The send scheme for multi email/phone picker fragment
     */
    public static final String SELECTION_SEND_SCHEME =
            "com.android.contacts.extra.SELECTION_SEND_SCHEME";

    /**
     * The send title for multi email/phone picker fragment
     */
    public static final String SELECTION_SEND_TITLE =
            "com.android.contacts.extra.SELECTION_SEND_TITLE";

    /**
     * The item ids for multi select picker fragment/adapter
     */
    public static final String SELECTION_ITEM_LIST =
            "com.android.contacts.extra.SELECTION_ITEM_LIST";

    /**
     * The default selection flags for the multi select picker fragment/adapter
     */
    public static final String SELECTION_DEFAULT_SELECTION =
            "com.android.contacts.extra.SELECTION_DEFAULT_SELECTION";

    /**
     * When in LIST_GROUP_ACTION mode, this is the group to display.
     */
    public static final String GROUP_NAME_EXTRA_KEY = "com.android.contacts.extra.GROUP";

    /**
     * The account name used to filter potential new group members.
     */
    public static final String GROUP_ACCOUNT_NAME =
            "com.android.contacts.extra.GROUP_ACCOUNT_NAME";

    /**
     * The account type used to filter potential new group members.
     */
    public static final String GROUP_ACCOUNT_TYPE =
            "com.android.contacts.extra.GROUP_ACCOUNT_TYPE";

    /**
     * The account data set used to filter potential new group members.
     */
    public static final String GROUP_ACCOUNT_DATA_SET =
            "com.android.contacts.extra.GROUP_ACCOUNT_DATA_SET";

    /**
     * The contact IDs for existing group members.
     */
    public static final String GROUP_CONTACT_IDS =
            "com.android.contacts.extra.GROUP_CONTACT_IDS";

    /**
     * The action for the all contacts list tab.
     */
    public static final String LIST_ALL_CONTACTS_ACTION =
            "com.android.contacts.action.LIST_ALL_CONTACTS";

    /**
     * The action for the contacts with phone numbers list tab.
     */
    public static final String LIST_CONTACTS_WITH_PHONES_ACTION =
            "com.android.contacts.action.LIST_CONTACTS_WITH_PHONES";

    /**
     * The action for the starred contacts list tab.
     */
    public static final String LIST_STARRED_ACTION =
            "com.android.contacts.action.LIST_STARRED";

    /**
     * The action for the frequent contacts list tab.
     */
    public static final String LIST_FREQUENT_ACTION =
            "com.android.contacts.action.LIST_FREQUENT";

    /**
     * The action for the "Join Contact" picker.
     */
    public static final String PICK_JOIN_CONTACT_ACTION =
            "com.android.contacts.action.JOIN_CONTACT";

    /**
     * The action for the "strequent" contacts list tab. It first lists the starred
     * contacts in alphabetical order and then the frequent contacts in descending
     * order of the number of times they have been contacted.
     */
    public static final String LIST_STREQUENT_ACTION =
            "com.android.contacts.action.LIST_STREQUENT";

    /**
     * A key for to be used as an intent extra to set the activity
     * title to a custom String value.
     */
    public static final String TITLE_EXTRA_KEY =
            "com.android.contacts.extra.TITLE_EXTRA";

    /**
     * Used as an int extra field in {@link #FILTER_CONTACTS_ACTION}
     * intents to supply the text on which to filter.
     */
    public static final String FILTER_TEXT_EXTRA_KEY =
            "com.android.contacts.extra.FILTER_TEXT";

    /**
     * Used with JOIN_CONTACT action to set the target for aggregation. This action type
     * uses contact ids instead of contact uris for the sake of backwards compatibility.
     * <p>
     * Type: LONG
     */
    public static final String TARGET_CONTACT_ID_EXTRA_KEY
            = "com.android.contacts.action.CONTACT_ID";

    public static final String TARGET_CONTACT_IDS_EXTRA_KEY
            = "com.android.contacts.action.CONTACT_IDS";
}