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

package com.android.contacts.list;

import com.android.contacts.ContactsSearchManager;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Intents.UI;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.common.BrcmIccUtils;
import com.android.internal.telephony.RILConstants.SimCardID;
import android.os.SystemProperties;

/**
 * Parses a Contacts intent, extracting all relevant parts and packaging them
 * as a {@link ContactsRequest} object.
 */
@SuppressWarnings("deprecation")
public class ContactsIntentResolver {

    private static final String TAG = "ContactsIntentResolver";

    private final Activity mContext;

    public ContactsIntentResolver(Activity context) {
        this.mContext = context;
    }

    public ContactsRequest resolveIntent(Intent intent) {
        ContactsRequest request = new ContactsRequest();

        String action = intent.getAction();

        Log.i(TAG, "Called with action: " + action);

        if (UI.LIST_DEFAULT.equals(action) ) {
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
        } else if (UI.LIST_ALL_CONTACTS_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_ALL_CONTACTS);
        } else if (UI.LIST_CONTACTS_WITH_PHONES_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_CONTACTS_WITH_PHONES);
        } else if (UI.LIST_STARRED_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_STARRED);
        } else if (UI.LIST_FREQUENT_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_FREQUENT);
        } else if (UI.LIST_STREQUENT_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_STREQUENT);
        } else if (UI.LIST_GROUP_ACTION.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_GROUP);
            // We no longer support UI.GROUP_NAME_EXTRA_KEY
        } else if (Intent.ACTION_PICK.equals(action)) {
            final String resolvedType = intent.resolveType(mContext);
            if (Contacts.CONTENT_TYPE.equals(resolvedType)) {
                boolean phonebookOnly = intent.getBooleanExtra(BrcmIccUtils.PHONEBOOK_CONTACTS_ONLY, false);
                boolean isExportToSIM = intent.getBooleanExtra(BrcmIccUtils.EXPORT_To_SIM, false);
                if (phonebookOnly) {
                    request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT_PHONEBOOK);
                    SimCardID simId = (SimCardID) (intent.getExtra(BrcmIccUtils.INTENT_EXTRA_SIM_ID, SimCardID.ID_ZERO));
                    request.setSimId(simId.toInt());
                    request.setExportToSIMEnabled(isExportToSIM);
                } else {
                    request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT);
                }
            } else if (People.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_CONTACT);
                request.setLegacyCompatibilityMode(true);
            } else if (Phone.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
            } else if (Phones.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
                request.setLegacyCompatibilityMode(true);
            } else if (StructuredPostal.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
            } else if (ContactMethods.CONTENT_POSTAL_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
                request.setLegacyCompatibilityMode(true);
            } else if (Email.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_EMAIL);
            }
        } else if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            String component = intent.getComponent().getClassName();
            if (component.equals("alias.DialShortcut")) {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_CALL);
            } else if (component.equals("alias.MessageShortcut")) {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_SMS);
            } else {
                request.setActionCode(ContactsRequest.ACTION_CREATE_SHORTCUT_CONTACT);
            }
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            String type = intent.getType();
            if (Contacts.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT);
            } else if (Phone.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
            } else if (Phones.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_PHONE);
                request.setLegacyCompatibilityMode(true);
            } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
            } else if (ContactMethods.CONTENT_POSTAL_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_POSTAL);
                request.setLegacyCompatibilityMode(true);
            }  else if (People.CONTENT_ITEM_TYPE.equals(type)) {
                request.setActionCode(ContactsRequest.ACTION_PICK_OR_CREATE_CONTACT);
                request.setLegacyCompatibilityMode(true);
            }
        } else if (Intent.ACTION_INSERT_OR_EDIT.equals(action)) {
            request.setActionCode(ContactsRequest.ACTION_INSERT_OR_EDIT_CONTACT);
        } else if (Intent.ACTION_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            // If the {@link SearchManager.QUERY} is empty, then check if a phone number
            // or email is specified, in that priority.
            if (TextUtils.isEmpty(query)) {
                query = intent.getStringExtra(Insert.PHONE);
            }
            if (TextUtils.isEmpty(query)) {
                query = intent.getStringExtra(Insert.EMAIL);
            }
            request.setQueryString(query);
            request.setSearchMode(true);
        } else if (Intent.ACTION_VIEW.equals(action)) {
            final String resolvedType = intent.resolveType(mContext);
            if (ContactsContract.Contacts.CONTENT_TYPE.equals(resolvedType)
                    || android.provider.Contacts.People.CONTENT_TYPE.equals(resolvedType)) {
                request.setActionCode(ContactsRequest.ACTION_ALL_CONTACTS);
            } else {
                request.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
                request.setContactUri(intent.getData());
                intent.setAction(Intent.ACTION_DEFAULT);
                intent.setData(null);
            }
        } else if (UI.FILTER_CONTACTS_ACTION.equals(action)) {
            // When we get a FILTER_CONTACTS_ACTION, it represents search in the context
            // of some other action. Let's retrieve the original action to provide proper
            // context for the search queries.
            request.setActionCode(ContactsRequest.ACTION_DEFAULT);
            Bundle extras = intent.getExtras();
            if (extras != null) {
                request.setQueryString(extras.getString(UI.FILTER_TEXT_EXTRA_KEY));

                ContactsRequest originalRequest =
                        (ContactsRequest)extras.get(ContactsSearchManager.ORIGINAL_REQUEST_KEY);
                if (originalRequest != null) {
                    request.copyFrom(originalRequest);
                }
            }

            request.setSearchMode(true);

        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        } else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            Uri data = intent.getData();
            request.setActionCode(ContactsRequest.ACTION_VIEW_CONTACT);
            request.setContactUri(data);
            intent.setAction(Intent.ACTION_DEFAULT);
            intent.setData(null);
        }
        // Allow the title to be set to a custom String using an extra on the intent
        String title = intent.getStringExtra(UI.TITLE_EXTRA_KEY);
        if (title != null) {
            request.setActivityTitle(title);
        }
        return request;
    }
}
