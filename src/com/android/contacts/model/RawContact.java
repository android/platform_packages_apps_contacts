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

package com.android.contacts.model;

import android.content.ContentValues;
import android.content.Context;
import android.content.Entity;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;

import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.dataitem.DataItem;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;

/**
 * RawContact represents a single raw contact in the raw contacts database.
 * It has specialized getters/setters for raw contact
 * items, and also contains a collection of DataItem objects.  A RawContact contains the information
 * from a single account.
 *
 * This allows RawContact objects to be thought of as a class with raw contact
 * fields (like account type, name, data set, sync state, etc.) and a list of
 * DataItem objects that represent contact information elements (like phone
 * numbers, email, address, etc.).
 */
final public class RawContact implements Parcelable {

    private AccountTypeManager mAccountTypeManager;
    private final ContentValues mValues;
    private final ArrayList<NamedDataItem> mDataItems;

    final public static class NamedDataItem implements Parcelable {
        public final Uri mUri;

        // This use to be a DataItem. DataItem creation is now delayed until the point of request
        // since there is no benefit to storing them here due to the multiple inheritance.
        // Eventually instanceof still has to be used anyways to determine which sub-class of
        // DataItem it is. And having parent DataItem's here makes it very difficult to serialize or
        // parcelable.
        //
        // Instead of having a common DataItem super class, we should refactor this to be a generic
        // Object where the object is a concrete class that no longer relies on ContentValues.
        // (this will also make the classes easier to use).
        // Since instanceof is used later anyways, having a list of Objects won't hurt and is no
        // worse than having a DataItem.
        public final ContentValues mContentValues;

        public NamedDataItem(Uri uri, ContentValues values) {
            this.mUri = uri;
            this.mContentValues = values;
        }

        public NamedDataItem(Parcel parcel) {
            this.mUri = parcel.readParcelable(Uri.class.getClassLoader());
            this.mContentValues = parcel.readParcelable(ContentValues.class.getClassLoader());
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel parcel, int i) {
            parcel.writeParcelable(mUri, i);
            parcel.writeParcelable(mContentValues, i);
        }

        public static final Parcelable.Creator<NamedDataItem> CREATOR
                = new Parcelable.Creator<NamedDataItem>() {

            @Override
            public NamedDataItem createFromParcel(Parcel parcel) {
                return new NamedDataItem(parcel);
            }

            @Override
            public NamedDataItem[] newArray(int i) {
                return new NamedDataItem[i];
            }
        };

        @Override
        public int hashCode() {
            return Objects.hashCode(mUri, mContentValues);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;

            final NamedDataItem other = (NamedDataItem) obj;
            return Objects.equal(mUri, other.mUri) &&
                    Objects.equal(mContentValues, other.mContentValues);
        }
    }

    public static RawContact createFrom(Entity entity) {
        final ContentValues values = entity.getEntityValues();
        final ArrayList<Entity.NamedContentValues> subValues = entity.getSubValues();

        RawContact rawContact = new RawContact(values);
        for (Entity.NamedContentValues subValue : subValues) {
            rawContact.addNamedDataItemValues(subValue.uri, subValue.values);
        }
        return rawContact;
    }

    /**
     * A RawContact object can be created with or without a context.
     */
    public RawContact() {
        this(new ContentValues());
    }

    public RawContact(ContentValues values) {
        mValues = values;
        mDataItems = new ArrayList<NamedDataItem>();
    }

    /**
     * Constructor for the parcelable.
     *
     * @param parcel The parcel to de-serialize from.
     */
    private RawContact(Parcel parcel) {
        mValues = parcel.readParcelable(ContentValues.class.getClassLoader());
        mDataItems = Lists.newArrayList();
        parcel.readTypedList(mDataItems, NamedDataItem.CREATOR);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(mValues, i);
        parcel.writeTypedList(mDataItems);
    }

    /**
     * Create for building the parcelable.
     */
    public static final Parcelable.Creator<RawContact> CREATOR
            = new Parcelable.Creator<RawContact>() {

        @Override
        public RawContact createFromParcel(Parcel parcel) {
            return new RawContact(parcel);
        }

        @Override
        public RawContact[] newArray(int i) {
            return new RawContact[i];
        }
    };

    public AccountTypeManager getAccountTypeManager(Context context) {
        if (mAccountTypeManager == null) {
            mAccountTypeManager = AccountTypeManager.getInstance(context);
        }
        return mAccountTypeManager;
    }

    public ContentValues getValues() {
        return mValues;
    }

    /**
     * Returns the id of the raw contact.
     */
    public Long getId() {
        return getValues().getAsLong(RawContacts._ID);
    }

    /**
     * Returns the account name of the raw contact.
     */
    public String getAccountName() {
        return getValues().getAsString(RawContacts.ACCOUNT_NAME);
    }

    /**
     * Returns the account type of the raw contact.
     */
    public String getAccountTypeString() {
        return getValues().getAsString(RawContacts.ACCOUNT_TYPE);
    }

    /**
     * Returns the data set of the raw contact.
     */
    public String getDataSet() {
        return getValues().getAsString(RawContacts.DATA_SET);
    }

    public boolean isDirty() {
        return getValues().getAsBoolean(RawContacts.DIRTY);
    }

    public String getSourceId() {
        return getValues().getAsString(RawContacts.SOURCE_ID);
    }

    public String getSync1() {
        return getValues().getAsString(RawContacts.SYNC1);
    }

    public String getSync2() {
        return getValues().getAsString(RawContacts.SYNC2);
    }

    public String getSync3() {
        return getValues().getAsString(RawContacts.SYNC3);
    }

    public String getSync4() {
        return getValues().getAsString(RawContacts.SYNC4);
    }

    public boolean isDeleted() {
        return getValues().getAsBoolean(RawContacts.DELETED);
    }

    public long getContactId() {
        return getValues().getAsLong(Contacts.Entity.CONTACT_ID);
    }

    public boolean isStarred() {
        return getValues().getAsBoolean(Contacts.STARRED);
    }

    public AccountType getAccountType(Context context) {
        return getAccountTypeManager(context).getAccountType(getAccountTypeString(), getDataSet());
    }

    /**
     * Sets the account name, account type, and data set strings.
     * Valid combinations for account-name, account-type, data-set
     * 1) null, null, null (local account)
     * 2) non-null, non-null, null (valid account without data-set)
     * 3) non-null, non-null, non-null (valid account with data-set)
     */
    private void setAccount(String accountName, String accountType, String dataSet) {
        final ContentValues values = getValues();
        if (accountName == null) {
            if (accountType == null && dataSet == null) {
                // This is a local account
                values.putNull(RawContacts.ACCOUNT_NAME);
                values.putNull(RawContacts.ACCOUNT_TYPE);
                values.putNull(RawContacts.DATA_SET);
                return;
            }
        } else {
            if (accountType != null) {
                // This is a valid account, either with or without a dataSet.
                values.put(RawContacts.ACCOUNT_NAME, accountName);
                values.put(RawContacts.ACCOUNT_TYPE, accountType);
                if (dataSet == null) {
                    values.putNull(RawContacts.DATA_SET);
                } else {
                    values.put(RawContacts.DATA_SET, dataSet);
                }
                return;
            }
        }
        throw new IllegalArgumentException(
                "Not a valid combination of account name, type, and data set.");
    }

    public void setAccount(AccountWithDataSet accountWithDataSet) {
        if (accountWithDataSet != null) {
            setAccount(accountWithDataSet.name, accountWithDataSet.type,
                    accountWithDataSet.dataSet);
        } else {
            setAccount(null, null, null);
        }
    }

    public void setAccountToLocal() {
        setAccount(null, null, null);
    }

    /**
     * Creates and inserts a DataItem object that wraps the content values, and returns it.
     */
    public void addDataItemValues(ContentValues values) {
        addNamedDataItemValues(Data.CONTENT_URI, values);
    }

    public NamedDataItem addNamedDataItemValues(Uri uri, ContentValues values) {
        final NamedDataItem namedItem = new NamedDataItem(uri, values);
        mDataItems.add(namedItem);
        return namedItem;
    }

    public ArrayList<ContentValues> getContentValues() {
        final ArrayList<ContentValues> list = Lists.newArrayListWithCapacity(mDataItems.size());
        for (NamedDataItem dataItem : mDataItems) {
            if (Data.CONTENT_URI.equals(dataItem.mUri)) {
                list.add(dataItem.mContentValues);
            }
        }
        return list;
    }

    public List<DataItem> getDataItems() {
        final ArrayList<DataItem> list = Lists.newArrayListWithCapacity(mDataItems.size());
        for (NamedDataItem dataItem : mDataItems) {
            if (Data.CONTENT_URI.equals(dataItem.mUri)) {
                list.add(DataItem.createFrom(dataItem.mContentValues));
            }
        }
        return list;
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("RawContact: ").append(mValues);
        for (RawContact.NamedDataItem namedDataItem : mDataItems) {
            sb.append("\n  ").append(namedDataItem.mUri);
            sb.append("\n  -> ").append(namedDataItem.mContentValues);
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(mValues, mDataItems);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;

        RawContact other = (RawContact) obj;
        return Objects.equal(mValues, other.mValues) &&
                Objects.equal(mDataItems, other.mDataItems);
    }
}
