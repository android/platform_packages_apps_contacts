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
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Identity;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts.Data;
import android.provider.ContactsContract.Contacts.Entity;

import com.android.contacts.Collapser;
import com.android.contacts.MoreContactUtils;
import com.android.contacts.model.RawContactModifier;

/**
 * This is the base class for data items, which represents a row from the Data table.
 */
public class DataItem implements Collapser.Collapsible<DataItem> {

    private final ContentValues mContentValues;
    protected DataKind mKind;

    protected DataItem(ContentValues values) {
        mContentValues = values;
    }

    /**
     * Factory for creating subclasses of DataItem objects based on the mimetype in the
     * content values.  Raw contact is the raw contact that this data item is associated with.
     */
    public static DataItem createFrom(ContentValues values) {
        final String mimeType = values.getAsString(Data.MIMETYPE);
        if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new GroupMembershipDataItem(values);
        } else if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new StructuredNameDataItem(values);
        } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new PhoneDataItem(values);
        } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new EmailDataItem(values);
        } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new StructuredPostalDataItem(values);
        } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new ImDataItem(values);
        } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new OrganizationDataItem(values);
        } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new NicknameDataItem(values);
        } else if (Note.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new NoteDataItem(values);
        } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new WebsiteDataItem(values);
        } else if (SipAddress.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new SipAddressDataItem(values);
        } else if (Event.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new EventDataItem(values);
        } else if (Relation.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new RelationDataItem(values);
        } else if (Identity.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new IdentityDataItem(values);
        } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
            return new PhotoDataItem(values);
        } else if (CustomDataItem.MIMETYPE_CUSTOM_FIELD.equals(mimeType)) {
            return new CustomDataItem(values);
        }

        // generic
        return new DataItem(values);
    }

    public ContentValues getContentValues() {
        return mContentValues;
    }

    public void setRawContactId(long rawContactId) {
        mContentValues.put(Data.RAW_CONTACT_ID, rawContactId);
    }

    public Long getRawContactId() {
        return mContentValues.getAsLong(Data.RAW_CONTACT_ID);
    }

    /**
     * Returns the data id.
     */
    public long getId() {
        return mContentValues.getAsLong(Data._ID);
    }

    /**
     * Returns the mimetype of the data.
     */
    public String getMimeType() {
        return mContentValues.getAsString(Data.MIMETYPE);
    }

    public void setMimeType(String mimeType) {
        mContentValues.put(Data.MIMETYPE, mimeType);
    }

    public boolean isPrimary() {
        Integer primary = mContentValues.getAsInteger(Data.IS_PRIMARY);
        return primary != null && primary != 0;
    }

    public boolean isSuperPrimary() {
        Integer superPrimary = mContentValues.getAsInteger(Data.IS_SUPER_PRIMARY);
        return superPrimary != null && superPrimary != 0;
    }

    public boolean hasKindTypeColumn(DataKind kind) {
        final String key = kind.typeColumn;
        return key != null && mContentValues.containsKey(key) &&
            mContentValues.getAsInteger(key) != null;
    }

    public int getKindTypeColumn(DataKind kind) {
        final String key = kind.typeColumn;
        return mContentValues.getAsInteger(key);
    }

    /**
     * Indicates the carrier presence value for the current {@link DataItem}.
     *
     * @return {@link Data#CARRIER_PRESENCE_VT_CAPABLE} if the {@link DataItem} supports carrier
     *      video calling, {@code 0} otherwise.
     */
    public int getCarrierPresence() {
        final Integer value = mContentValues.getAsInteger(Data.CARRIER_PRESENCE);
        return value != null ? value.intValue() : 0;
    }

    /**
     * This builds the data string depending on the type of data item by using the generic
     * DataKind object underneath.
     */
    public String buildDataString(Context context, DataKind kind) {
        if (kind.actionBody == null) {
            return null;
        }
        CharSequence actionBody = kind.actionBody.inflateUsing(context, mContentValues);
        return actionBody == null ? null : actionBody.toString();
    }

    /**
     * This builds the data string(intended for display) depending on the type of data item. It
     * returns the same value as {@link #buildDataString} by default, but certain data items can
     * override it to provide their version of formatted data strings.
     *
     * @return Data string representing the data item, possibly formatted for display
     */
    public String buildDataStringForDisplay(Context context, DataKind kind) {
        return buildDataString(context, kind);
    }

    public void setDataKind(DataKind kind) {
        mKind = kind;
    }

    public DataKind getDataKind() {
        return mKind;
    }

    public Integer getTimesUsed() {
        return mContentValues.getAsInteger(Entity.TIMES_USED);
    }

    public Long getLastTimeUsed() {
        return mContentValues.getAsLong(Entity.LAST_TIME_USED);
    }

    @Override
    public void collapseWith(DataItem that) {
        DataKind thisKind = getDataKind();
        DataKind thatKind = that.getDataKind();
        // If this does not have a type and that does, or if that's type is higher precedence,
        // use that's type
        if ((!hasKindTypeColumn(thisKind) && that.hasKindTypeColumn(thatKind)) ||
                that.hasKindTypeColumn(thatKind) &&
                RawContactModifier.getTypePrecedence(thisKind, getKindTypeColumn(thisKind))
                >
                RawContactModifier.getTypePrecedence(thatKind, that.getKindTypeColumn(thatKind))) {
            mContentValues.put(thatKind.typeColumn, that.getKindTypeColumn(thatKind));
            mKind = thatKind;
        }

        // Choose the max of the maxLines and maxLabelLines values.
        mKind.maxLinesForDisplay = Math.max(thisKind.maxLinesForDisplay,
                thatKind.maxLinesForDisplay);

        // If any of the collapsed entries are super primary make the whole thing super primary.
        if (isSuperPrimary() || that.isSuperPrimary()) {
            mContentValues.put(Data.IS_SUPER_PRIMARY, 1);
            mContentValues.put(Data.IS_PRIMARY, 1);
        }

        // If any of the collapsed entries are primary make the whole thing primary.
        if (isPrimary() || that.isPrimary()) {
            mContentValues.put(Data.IS_PRIMARY, 1);
        }

        // Add up the times used
        mContentValues.put(Entity.TIMES_USED, (getTimesUsed() == null ? 0 : getTimesUsed()) +
                (that.getTimesUsed() == null ? 0 : that.getTimesUsed()));

        // Use the most recent time
        mContentValues.put(Entity.LAST_TIME_USED,
                Math.max(getLastTimeUsed() == null ? 0 : getLastTimeUsed(),
                        that.getLastTimeUsed() == null ? 0 : that.getLastTimeUsed()));
    }

    @Override
    public boolean shouldCollapseWith(DataItem t, Context context) {
        if (mKind == null || t.getDataKind() == null) {
            return false;
        }
        return MoreContactUtils.shouldCollapse(getMimeType(), buildDataString(context, mKind),
                t.getMimeType(), t.buildDataString(context, t.getDataKind()));
    }
}
