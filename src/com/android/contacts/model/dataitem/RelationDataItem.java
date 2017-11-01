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
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.text.TextUtils;

/**
 * Represents a relation data item, wrapping the columns in
 * {@link ContactsContract.CommonDataKinds.Relation}.
 */
public class RelationDataItem extends DataItem {

    /* package */ RelationDataItem(ContentValues values) {
        super(values);
    }

    public String getName() {
        return getContentValues().getAsString(Relation.NAME);
    }

    public String getLabel() {
        return getContentValues().getAsString(Relation.LABEL);
    }

    @Override
    public boolean shouldCollapseWith(DataItem t, Context context) {
        if (!(t instanceof RelationDataItem) || mKind == null || t.getDataKind() == null) {
            return false;
        }
        final RelationDataItem that = (RelationDataItem) t;
        // Relations can have different types (assistant, father) but have the same name
        if (!TextUtils.equals(getName(), that.getName())) {
            return false;
        } else if (!hasKindTypeColumn(mKind) || !that.hasKindTypeColumn(that.getDataKind())) {
            return hasKindTypeColumn(mKind) == that.hasKindTypeColumn(that.getDataKind());
        } else if (getKindTypeColumn(mKind) != that.getKindTypeColumn(that.getDataKind())) {
            return false;
        } else if (getKindTypeColumn(mKind) == Relation.TYPE_CUSTOM &&
                !TextUtils.equals(getLabel(), that.getLabel())) {
            // Check if custom types are not the same
            return false;
        }
        return true;
    }
}
