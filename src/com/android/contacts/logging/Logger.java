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

import android.app.Activity;

import com.android.contacts.logging.ScreenEvent.ScreenType;
import com.android.contactsbind.ObjectFactory;

/**
 * Logs analytics events.
 */
public abstract class Logger {
    public static final String TAG = "Logger";

    private static Logger getInstance() {
        return ObjectFactory.getLogger();
    }

    /**
     * Logs an event indicating that a screen was displayed.
     *
     * @param screenType integer identifier of the displayed screen
     * @param activity Parent activity of the displayed screen.
     */
    public static void logScreenView(Activity activity, int screenType) {
        logScreenView(activity, screenType, ScreenType.UNKNOWN);
    }

    /**
     * @param previousScreenType integer identifier of the displayed screen the user came from.
     */
    public static void logScreenView(Activity activity, int screenType, int previousScreenType) {
        final Logger logger = getInstance();
        if (logger != null) {
            logger.logScreenViewImpl(screenType, previousScreenType);
        }
    }

    /**
     * Logs the results of a user search for a particular contact.
     */
    public static void logSearchEvent(SearchState searchState) {
        final Logger logger = getInstance();
        if (logger != null) {
            logger.logSearchEventImpl(searchState);
        }
    }

    /**
     * Logs how users view and use a contacts list. See {@link ListEvent} for definition of
     * parameters.
     */
    public static void logListEvent(int actionType, int listType, int count, int clickedIndex,
            int numSelected) {
        final ListEvent event = new ListEvent();
        event.actionType = actionType;
        event.listType = listType;
        event.count = count;
        event.clickedIndex = clickedIndex;
        event.numSelected = numSelected;

        final Logger logger = getInstance();
        if (logger != null) {
            logger.logListEventImpl(event);
        }
    }

    /**
     * Logs an event on QuickContact. See {@link QuickContactEvent} for definition of parameters.
     */
    public static void logQuickContactEvent(String referrer, int contactType, int cardType,
            int actionType, String thirdPartyAction) {
        final Logger logger = getInstance();
        if (logger != null) {
            final QuickContactEvent event = new QuickContactEvent();
            event.referrer = referrer == null ? "Unknown" : referrer;
            event.contactType = contactType;
            event.cardType = cardType;
            event.actionType = actionType;
            event.thirdPartyAction = thirdPartyAction == null ? "" : thirdPartyAction;
            logger.logQuickContactEventImpl(event);
        }
    }

    public static void logEditorEvent(int eventType, int numberRawContacts) {
        final Logger logger = getInstance();
        if (logger != null) {
            final EditorEvent event = new EditorEvent();
            event.eventType = eventType;
            event.numberRawContacts = numberRawContacts;
            logger.logEditorEventImpl(event);
        }
    }

    public abstract void logScreenViewImpl(int screenType, int previousScreenType);
    public abstract void logSearchEventImpl(SearchState searchState);
    public abstract void logListEventImpl(ListEvent event);
    public abstract void logQuickContactEventImpl(QuickContactEvent event);
    public abstract void logEditorEventImpl(EditorEvent event);
}
