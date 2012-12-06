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

import android.net.Uri;

import com.android.internal.telephony.RILConstants.SimCardID;

/**
 * Action callbacks that can be sent by a contact list.
 */
public interface OnContactBrowserActionListener  {

    /**
     * Notification of selection change, invoked when the selection of activated
     * item(s) is change by either a user action or some other event, e.g. sync.
     */
    void onSelectionChange();

    /**
     * Opens the specified contact for viewing.
     *
     * @param contactLookupUri The lookup-uri of the Contact that should be opened
     */
    void onViewContactAction(Uri contactLookupUri);

    /**
     * Creates a new contact.
     */
    void onCreateNewContactAction();

    /**
     * Opens the specified contact for editing.
     */
    void onEditContactAction(Uri contactLookupUri);

    /**
     * Initiates the contact deletion process.
     */
    void onDeleteContactAction(Uri contactUri);

    /**
     * Adds the specified contact to favorites
     */
    void onAddToFavoritesAction(Uri contactUri);

    /**
     * Removes the specified contact from favorites.
     */
    void onRemoveFromFavoritesAction(Uri contactUri);

    /**
     * Closes the contact browser.
     */
    void onFinishAction();

    /**
     * Invoked if the requested selected contact is not found in the list.
     */
    void onInvalidSelection();

    /**
     * Saves the specified phonebook contact to sim.
     */
    void onSaveToSimContactAction(Uri contactLookupUri, SimCardID simId);

    /**
     * Saves the specified sim contact to phonebook.
     */
    void onSaveToPbContactAction(Uri contactLookupUri);
}
