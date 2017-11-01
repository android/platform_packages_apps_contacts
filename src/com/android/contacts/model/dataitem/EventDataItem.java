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
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.text.TextUtils;

/**
 * Represents an event data item, wrapping the columns in
 * {@link ContactsContract.CommonDataKinds.Event}.
 */
public class EventDataItem extends DataItem {

    /* package */ EventDataItem(ContentValues values) {
        super(values);
    }

    public String getStartDate() {
        return getContentValues().getAsString(Event.START_DATE);
    }

    public String getLabel() {
        return getContentValues().getAsString(Event.LABEL);
    }

    @Override
    public boolean shouldCollapseWith(DataItem t, Context context) {
        if (!(t instanceof EventDataItem) || mKind == null || t.getDataKind() == null) {
            return false;
        }
        final EventDataItem that = (EventDataItem) t;
        // Events can be different (anniversary, birthday) but have the same start date
        if (!TextUtils.equals(getStartDate(), that.getStartDate())) {
            return false;
        } else if (!hasKindTypeColumn(mKind) || !that.hasKindTypeColumn(that.getDataKind())) {
            return hasKindTypeColumn(mKind) == that.hasKindTypeColumn(that.getDataKind());
        } else if (getKindTypeColumn(mKind) != that.getKindTypeColumn(that.getDataKind())) {
            return false;
        } else if (getKindTypeColumn(mKind) == Event.TYPE_CUSTOM &&
                !TextUtils.equals(getLabel(), that.getLabel())) {
            // Check if custom types are not the same
            return false;
        }
        return true;
    }
}
