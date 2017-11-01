/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.model.dataitem;

import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.text.TextUtils;

/**
 * Represents an IM data item, wrapping the columns in
 * {@link ContactsContract.CommonDataKinds.Im}.
 */
public class ImDataItem extends DataItem {

    private final boolean mCreatedFromEmail;

    /* package */ ImDataItem(ContentValues values) {
        super(values);
        mCreatedFromEmail = false;
    }

    private ImDataItem(ContentValues values, boolean createdFromEmail) {
        super(values);
        mCreatedFromEmail = createdFromEmail;
    }

    public static ImDataItem createFromEmail(EmailDataItem item) {
        final ImDataItem im = new ImDataItem(new ContentValues(item.getContentValues()), true);
        im.setMimeType(Im.CONTENT_ITEM_TYPE);
        return im;
    }

    public String getData() {
        if (mCreatedFromEmail) {
            return getContentValues().getAsString(Email.DATA);
        } else {
            return getContentValues().getAsString(Im.DATA);
        }
    }

    public String getLabel() {
        return getContentValues().getAsString(Im.LABEL);
    }

    /**
     * Values are one of Im.PROTOCOL_
     */
    public Integer getProtocol() {
        return getContentValues().getAsInteger(Im.PROTOCOL);
    }

    public boolean isProtocolValid() {
        return getProtocol() != null;
    }

    public String getCustomProtocol() {
        return getContentValues().getAsString(Im.CUSTOM_PROTOCOL);
    }

    public int getChatCapability() {
        Integer result = getContentValues().getAsInteger(Im.CHAT_CAPABILITY);
        return result == null ? 0 : result;
    }

    public boolean isCreatedFromEmail() {
        return mCreatedFromEmail;
    }

    @Override
    public boolean shouldCollapseWith(DataItem t, Context context) {
        if (!(t instanceof ImDataItem) || mKind == null || t.getDataKind() == null) {
            return false;
        }
        final ImDataItem that = (ImDataItem) t;
        // IM can have the same data put different protocol. These should not collapse.
        if (!getData().equals(that.getData())) {
            return false;
        } else if (!isProtocolValid() || !that.isProtocolValid()) {
            // Deal with invalid protocol as if it was custom. If either has a non valid
            // protocol, check to see if the other has a valid that is not custom
            if (isProtocolValid()) {
                return getProtocol() == Im.PROTOCOL_CUSTOM;
            } else if (that.isProtocolValid()) {
                return that.getProtocol() == Im.PROTOCOL_CUSTOM;
            }
            return true;
        } else if (getProtocol() != that.getProtocol()) {
            return false;
        } else if (getProtocol() == Im.PROTOCOL_CUSTOM &&
                !TextUtils.equals(getCustomProtocol(), that.getCustomProtocol())) {
            // Check if custom protocols are not the same
            return false;
        }
        return true;
    }
}
