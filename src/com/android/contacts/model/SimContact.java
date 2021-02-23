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
package com.android.contacts.model;

import android.content.ContentProviderOperation;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.model.account.AccountWithDataSet;

import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Holds data for contacts loaded from the SIM card.
 */
public class SimContact implements Parcelable {
    private final int mRecordNumber;
    private final String mName;
    private final String mPhone;
    private final String[] mEmails;

    public SimContact(int recordNumber, String name, String phone) {
        this(recordNumber, name, phone, null);
    }

    public SimContact(int recordNumber, String name, String phone, String[] emails) {
        mRecordNumber = recordNumber;
        mName = name;
        mPhone = phone == null ? "" : phone.trim();
        mEmails = emails;
    }

    public SimContact(SimContact other) {
        this(other.mRecordNumber, other.mName, other.mPhone, other.mEmails);
    }

    public int getRecordNumber() {
        return mRecordNumber;
    }

    public String getName() {
        return mName;
    }

    public String getPhone() {
        return mPhone;
    }

    public String[] getEmails() {
        return mEmails;
    }

    public void appendCreateContactOperations(List<ContentProviderOperation> ops,
            AccountWithDataSet targetAccount) {
        // There is nothing to save so skip it.
        if (!hasName() && !hasPhone() && !hasEmails()) return;

        final int rawContactOpIndex = ops.size();
        ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withYieldAllowed(true)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, targetAccount.name)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, targetAccount.type)
                .withValue(ContactsContract.RawContacts.DATA_SET, targetAccount.dataSet)
                .build());
        if (mName != null) {
            ops.add(createInsertOp(rawContactOpIndex, StructuredName.CONTENT_ITEM_TYPE,
                    StructuredName.DISPLAY_NAME, mName));
        }
        if (!mPhone.isEmpty()) {
            ops.add(createInsertOp(rawContactOpIndex, Phone.CONTENT_ITEM_TYPE,
                    Phone.NUMBER, mPhone));
        }
        if (mEmails != null) {
            for (String email : mEmails) {
                ops.add(createInsertOp(rawContactOpIndex, Email.CONTENT_ITEM_TYPE,
                        Email.ADDRESS, email));
            }
        }
    }

    private ContentProviderOperation createInsertOp(int rawContactOpIndex, String mimeType,
            String column, String value) {
        return ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawContactOpIndex)
                .withValue(ContactsContract.Data.MIMETYPE, mimeType)
                .withValue(column, value)
                .build();
    }

    public void appendAsContactRow(MatrixCursor cursor) {
        cursor.newRow().add(ContactsContract.Contacts._ID, mRecordNumber)
                .add(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, mName)
                .add(ContactsContract.Contacts.LOOKUP_KEY, getLookupKey());
    }

    public boolean hasName() {
        return !TextUtils.isEmpty(mName);
    }

    public boolean hasPhone() {
        return !mPhone.isEmpty();
    }

    public boolean hasEmails() {
        return mEmails != null && mEmails.length > 0;
    }

    /**
     * Generate a "fake" lookup key. This is needed because
     * {@link ContactPhotoManager} will only generate a letter avatar
     * if the contact has a lookup key.
     */
    private String getLookupKey() {
        if (mName != null) {
            return "sim-n-" + Uri.encode(mName);
        } else if (mPhone != null) {
            return "sim-p-" + Uri.encode(mPhone);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "SimContact{" +
                "mId=" + mRecordNumber +
                ", mName='" + mName + '\'' +
                ", mPhone='" + mPhone + '\'' +
                ", mEmails=" + Arrays.toString(mEmails) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final SimContact that = (SimContact) o;

        return mRecordNumber == that.mRecordNumber && Objects.equals(mName, that.mName) &&
                Objects.equals(mPhone, that.mPhone) && Arrays.equals(mEmails, that.mEmails);
    }

    @Override
    public int hashCode() {
        int result = (int) (mRecordNumber ^ (mRecordNumber >>> 32));
        result = 31 * result + (mName != null ? mName.hashCode() : 0);
        result = 31 * result + (mPhone != null ? mPhone.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(mEmails);
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mRecordNumber);
        dest.writeString(mName);
        dest.writeString(mPhone);
        dest.writeStringArray(mEmails);
    }

    public static final Creator<SimContact> CREATOR = new Creator<SimContact>() {
        @Override
        public SimContact createFromParcel(Parcel source) {
            final int recordNumber = source.readInt();
            final String name = source.readString();
            final String phone = source.readString();
            final String[] emails = source.createStringArray();
            return new SimContact(recordNumber, name, phone, emails);
        }

        @Override
        public SimContact[] newArray(int size) {
            return new SimContact[size];
        }
    };

    /**
     * Convert a collection of SIM contacts to a Cursor matching a query from
     * {@link android.provider.ContactsContract.Contacts#CONTENT_URI} with the provided projection.
     *
     * This allows a collection of SIM contacts to be displayed using the existing adapters for
     * contacts.
     */
    public static final MatrixCursor convertToContactsCursor(Collection<SimContact> contacts,
            String[] projection) {
        final MatrixCursor result = new MatrixCursor(projection);
        for (SimContact contact : contacts) {
            contact.appendAsContactRow(result);
        }
        return result;
    }

    /**
     * Returns the index of a contact with a matching name and phone
     * @param contacts list to search. Should be sorted using
     * {@link SimContact#compareByPhoneThenName()}
     * @param phone the phone to search for
     * @param name the name to search for
     */
    public static int findByPhoneAndName(List<SimContact> contacts, String phone, String name) {
        return Collections.binarySearch(contacts, new SimContact(-1, name, phone, null),
                compareByPhoneThenName());
    }

    public static final Comparator<SimContact> compareByPhoneThenName() {
        return new Comparator<SimContact>() {
            @Override
            public int compare(SimContact lhs, SimContact rhs) {
                return ComparisonChain.start()
                        .compare(lhs.mPhone, rhs.mPhone)
                        .compare(lhs.mName, rhs.mName, Ordering.<String>natural().nullsFirst())
                        .result();
            }
        };
    }

    public static final Comparator<SimContact> compareById() {
        return new Comparator<SimContact>() {
            @Override
            public int compare(SimContact lhs, SimContact rhs) {
                // We assume ids are unique.
                return Long.compare(lhs.mRecordNumber, rhs.mRecordNumber);
            }
        };
    }
}
