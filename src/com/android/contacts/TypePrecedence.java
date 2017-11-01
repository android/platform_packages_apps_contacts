/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.accounts.Account;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;

import com.android.contacts.model.RawContactModifier;

/**
 * This class contains utility functions for determining the precedence of
 * different types associated with contact data items.
 *
 * @deprecated use {@link RawContactModifier#getTypePrecedence} instead, since this
 *             list isn't {@link Account} based.
 */
@Deprecated
public final class TypePrecedence {

    public static final String MIME_TYPE_VIDEO_CHAT = "vnd.android.cursor.item/video-chat-address";

    /* This utility class has cannot be instantiated.*/
    private TypePrecedence() {}

    //TODO These may need to be tweaked.
    private static final int[] TYPE_PRECEDENCE_PHONES = {
            Phone.TYPE_CUSTOM,
            Phone.TYPE_MAIN,
            Phone.TYPE_MOBILE,
            Phone.TYPE_HOME,
            Phone.TYPE_WORK,
            Phone.TYPE_OTHER,
            Phone.TYPE_FAX_HOME,
            Phone.TYPE_FAX_WORK,
            Phone.TYPE_PAGER};

    private static final int[] TYPE_PRECEDENCE_EMAIL = {
            Email.TYPE_CUSTOM,
            Email.TYPE_HOME,
            Email.TYPE_WORK,
            Email.TYPE_OTHER};

    private static final int[] TYPE_PRECEDENCE_POSTAL = {
            StructuredPostal.TYPE_CUSTOM,
            StructuredPostal.TYPE_HOME,
            StructuredPostal.TYPE_WORK,
            StructuredPostal.TYPE_OTHER};

    private static final int[] TYPE_PRECEDENCE_IM = {
            Im.TYPE_CUSTOM,
            Im.TYPE_HOME,
            Im.TYPE_WORK,
            Im.TYPE_OTHER};

    private static final int[] TYPE_PRECEDENCE_ORG = {
            Organization.TYPE_CUSTOM,
            Organization.TYPE_WORK,
            Organization.TYPE_OTHER};

    /**
     * Returns the precedence (1 being the highest) of a type in the context of it's mimetype.
     *
     * @param mimetype The mimetype of the data with which the type is associated.
     * @param type The integer type as defined in {@Link ContactsContract#CommonDataKinds}.
     * @return The integer precedence, where 1 is the highest.
     */
    @Deprecated
    public static int getTypePrecedence(String mimetype, int type) {
        int[] typePrecedence = getTypePrecedenceList(mimetype);
        if (typePrecedence == null) {
            return -1;
        }

        for (int i = 0; i < typePrecedence.length; i++) {
            if (typePrecedence[i] == type) {
                return i;
            }
        }
        return typePrecedence.length;
    }

    @Deprecated
    private static int[] getTypePrecedenceList(String mimetype) {
        if (mimetype.equals(Phone.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_PHONES;
        } else if (mimetype.equals(Email.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_EMAIL;
        } else if (mimetype.equals(StructuredPostal.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_POSTAL;
        } else if (mimetype.equals(Im.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_IM;
        } else if (mimetype.equals(MIME_TYPE_VIDEO_CHAT)) {
            return TYPE_PRECEDENCE_IM;
        } else if (mimetype.equals(Organization.CONTENT_ITEM_TYPE)) {
            return TYPE_PRECEDENCE_ORG;
        } else {
            return null;
        }
    }


}
