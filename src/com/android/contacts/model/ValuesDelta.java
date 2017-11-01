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

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract;

import com.android.contacts.compat.CompatUtils;

import com.google.common.collect.Sets;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Type of {@link android.content.ContentValues} that maintains both an original state and a
 * modified version of that state. This allows us to build insert, update,
 * or delete operations based on a "before" {@link Entity} snapshot.
 */
public class ValuesDelta implements Parcelable {
    protected ContentValues mBefore;
    protected ContentValues mAfter;
    protected String mIdColumn = BaseColumns._ID;
    private boolean mFromTemplate;

    /**
     * Next value to assign to {@link #mIdColumn} when building an insert
     * operation through {@link #fromAfter(android.content.ContentValues)}. This is used so
     * we can concretely reference this {@link ValuesDelta} before it has
     * been persisted.
     */
    protected static int sNextInsertId = -1;

    protected ValuesDelta() {
    }

    /**
     * Create {@link ValuesDelta}, using the given object as the
     * "before" state, usually from an {@link Entity}.
     */
    public static ValuesDelta fromBefore(ContentValues before) {
        final ValuesDelta entry = new ValuesDelta();
        entry.mBefore = before;
        entry.mAfter = new ContentValues();
        return entry;
    }

    /**
     * Create {@link ValuesDelta}, using the given object as the "after"
     * state, usually when we are inserting a row instead of updating.
     */
    public static ValuesDelta fromAfter(ContentValues after) {
        final ValuesDelta entry = new ValuesDelta();
        entry.mBefore = null;
        entry.mAfter = after;

        // Assign temporary id which is dropped before insert.
        entry.mAfter.put(entry.mIdColumn, sNextInsertId--);
        return entry;
    }

    public ContentValues getAfter() {
        return mAfter;
    }

    public ContentValues getBefore() {
        return mBefore;
    }

    public boolean containsKey(String key) {
        return ((mAfter != null && mAfter.containsKey(key)) ||
                (mBefore != null && mBefore.containsKey(key)));
    }

    public String getAsString(String key) {
        if (mAfter != null && mAfter.containsKey(key)) {
            return mAfter.getAsString(key);
        } else if (mBefore != null && mBefore.containsKey(key)) {
            return mBefore.getAsString(key);
        } else {
            return null;
        }
    }

    public byte[] getAsByteArray(String key) {
        if (mAfter != null && mAfter.containsKey(key)) {
            return mAfter.getAsByteArray(key);
        } else if (mBefore != null && mBefore.containsKey(key)) {
            return mBefore.getAsByteArray(key);
        } else {
            return null;
        }
    }

    public Long getAsLong(String key) {
        if (mAfter != null && mAfter.containsKey(key)) {
            return mAfter.getAsLong(key);
        } else if (mBefore != null && mBefore.containsKey(key)) {
            return mBefore.getAsLong(key);
        } else {
            return null;
        }
    }

    public Integer getAsInteger(String key) {
        return getAsInteger(key, null);
    }

    public Integer getAsInteger(String key, Integer defaultValue) {
        if (mAfter != null && mAfter.containsKey(key)) {
            return mAfter.getAsInteger(key);
        } else if (mBefore != null && mBefore.containsKey(key)) {
            return mBefore.getAsInteger(key);
        } else {
            return defaultValue;
        }
    }

    public boolean isChanged(String key) {
        if (mAfter == null || !mAfter.containsKey(key)) {
            return false;
        }

        Object newValue = mAfter.get(key);
        Object oldValue = mBefore.get(key);

        if (oldValue == null) {
            return newValue != null;
        }

        return !oldValue.equals(newValue);
    }

    public String getMimetype() {
        return getAsString(ContactsContract.Data.MIMETYPE);
    }

    public Long getId() {
        return getAsLong(mIdColumn);
    }

    public void setIdColumn(String idColumn) {
        mIdColumn = idColumn;
    }

    public boolean isPrimary() {
        final Long isPrimary = getAsLong(ContactsContract.Data.IS_PRIMARY);
        return isPrimary == null ? false : isPrimary != 0;
    }

    public void setFromTemplate(boolean isFromTemplate) {
        mFromTemplate = isFromTemplate;
    }

    public boolean isFromTemplate() {
        return mFromTemplate;
    }

    public boolean isSuperPrimary() {
        final Long isSuperPrimary = getAsLong(ContactsContract.Data.IS_SUPER_PRIMARY);
        return isSuperPrimary == null ? false : isSuperPrimary != 0;
    }

    public boolean beforeExists() {
        return (mBefore != null && mBefore.containsKey(mIdColumn));
    }

    /**
     * When "after" is present, then visible
     */
    public boolean isVisible() {
        return (mAfter != null);
    }

    /**
     * When "after" is wiped, action is "delete"
     */
    public boolean isDelete() {
        return beforeExists() && (mAfter == null);
    }

    /**
     * When no "before" or "after", is transient
     */
    public boolean isTransient() {
        return (mBefore == null) && (mAfter == null);
    }

    /**
     * When "after" has some changes, action is "update"
     */
    public boolean isUpdate() {
        if (!beforeExists() || mAfter == null || mAfter.size() == 0) {
            return false;
        }
        for (String key : mAfter.keySet()) {
            Object newValue = mAfter.get(key);
            Object oldValue = mBefore.get(key);
            if (oldValue == null) {
                if (newValue != null) {
                    return true;
                }
            } else if (!oldValue.equals(newValue)) {
                return true;
            }
        }
        return false;
    }

    /**
     * When "after" has no changes, action is no-op
     */
    public boolean isNoop() {
        return beforeExists() && (mAfter != null && mAfter.size() == 0);
    }

    /**
     * When no "before" id, and has "after", action is "insert"
     */
    public boolean isInsert() {
        return !beforeExists() && (mAfter != null);
    }

    public void markDeleted() {
        mAfter = null;
    }

    /**
     * Ensure that our internal structure is ready for storing updates.
     */
    private void ensureUpdate() {
        if (mAfter == null) {
            mAfter = new ContentValues();
        }
    }

    public void put(String key, String value) {
        ensureUpdate();
        mAfter.put(key, value);
    }

    public void put(String key, byte[] value) {
        ensureUpdate();
        mAfter.put(key, value);
    }

    public void put(String key, int value) {
        ensureUpdate();
        mAfter.put(key, value);
    }

    public void put(String key, long value) {
        ensureUpdate();
        mAfter.put(key, value);
    }

    public void putNull(String key) {
        ensureUpdate();
        mAfter.putNull(key);
    }

    public void copyStringFrom(ValuesDelta from, String key) {
        ensureUpdate();
        if (containsKey(key) || from.containsKey(key)) {
            put(key, from.getAsString(key));
        }
    }

    /**
     * Return set of all keys defined through this object.
     */
    public Set<String> keySet() {
        final HashSet<String> keys = Sets.newHashSet();

        if (mBefore != null) {
            for (Map.Entry<String, Object> entry : mBefore.valueSet()) {
                keys.add(entry.getKey());
            }
        }

        if (mAfter != null) {
            for (Map.Entry<String, Object> entry : mAfter.valueSet()) {
                keys.add(entry.getKey());
            }
        }

        return keys;
    }

    /**
     * Return complete set of "before" and "after" values mixed together,
     * giving full state regardless of edits.
     */
    public ContentValues getCompleteValues() {
        final ContentValues values = new ContentValues();
        if (mBefore != null) {
            values.putAll(mBefore);
        }
        if (mAfter != null) {
            values.putAll(mAfter);
        }
        if (values.containsKey(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)) {
            // Clear to avoid double-definitions, and prefer rows
            values.remove(ContactsContract.CommonDataKinds.GroupMembership.GROUP_SOURCE_ID);
        }

        return values;
    }

    /**
     * Merge the "after" values from the given {@link ValuesDelta},
     * discarding any existing "after" state. This is typically used when
     * re-parenting changes onto an updated {@link Entity}.
     */
    public static ValuesDelta mergeAfter(ValuesDelta local, ValuesDelta remote) {
        // Bail early if trying to merge delete with missing local
        if (local == null && (remote.isDelete() || remote.isTransient())) return null;

        // Create local version if none exists yet
        if (local == null) local = new ValuesDelta();

        if (!local.beforeExists()) {
            // Any "before" record is missing, so take all values as "insert"
            local.mAfter = remote.getCompleteValues();
        } else {
            // Existing "update" with only "after" values
            local.mAfter = remote.mAfter;
        }

        return local;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof ValuesDelta) {
            // Only exactly equal with both are identical subsets
            final ValuesDelta other = (ValuesDelta)object;
            return this.subsetEquals(other) && other.subsetEquals(this);
        }
        return false;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        toString(builder);
        return builder.toString();
    }

    /**
     * Helper for building string representation, leveraging the given
     * {@link StringBuilder} to minimize allocations.
     */
    public void toString(StringBuilder builder) {
        builder.append("{ ");
        builder.append("IdColumn=");
        builder.append(mIdColumn);
        builder.append(", FromTemplate=");
        builder.append(mFromTemplate);
        builder.append(", ");
        for (String key : this.keySet()) {
            builder.append(key);
            builder.append("=");
            builder.append(this.getAsString(key));
            builder.append(", ");
        }
        builder.append("}");
    }

    /**
     * Check if the given {@link ValuesDelta} is both a subset of this
     * object, and any defined keys have equal values.
     */
    public boolean subsetEquals(ValuesDelta other) {
        for (String key : this.keySet()) {
            final String ourValue = this.getAsString(key);
            final String theirValue = other.getAsString(key);
            if (ourValue == null) {
                // If they have value when we're null, no match
                if (theirValue != null) return false;
            } else {
                // If both values defined and aren't equal, no match
                if (!ourValue.equals(theirValue)) return false;
            }
        }
        // All values compared and matched
        return true;
    }

    /**
     * Build a {@link android.content.ContentProviderOperation} that will transform our
     * "before" state into our "after" state, using insert, update, or
     * delete as needed.
     */
    public ContentProviderOperation.Builder buildDiff(Uri targetUri) {
        return buildDiffHelper(targetUri);
    }

    /**
     * For compatibility purpose.
     */
    public BuilderWrapper buildDiffWrapper(Uri targetUri) {
        final ContentProviderOperation.Builder builder = buildDiffHelper(targetUri);
        BuilderWrapper bw = null;
        if (isInsert()) {
            bw = new BuilderWrapper(builder, CompatUtils.TYPE_INSERT);
        } else if (isDelete()) {
            bw = new BuilderWrapper(builder, CompatUtils.TYPE_DELETE);
        } else if (isUpdate()) {
            bw = new BuilderWrapper(builder, CompatUtils.TYPE_UPDATE);
        }
        return bw;
    }

    private ContentProviderOperation.Builder buildDiffHelper(Uri targetUri) {
        ContentProviderOperation.Builder builder = null;
        if (isInsert()) {
            // Changed values are "insert" back-referenced to Contact
            mAfter.remove(mIdColumn);
            builder = ContentProviderOperation.newInsert(targetUri);
            builder.withValues(mAfter);
        } else if (isDelete()) {
            // When marked for deletion and "before" exists, then "delete"
            builder = ContentProviderOperation.newDelete(targetUri);
            builder.withSelection(mIdColumn + "=" + getId(), null);
        } else if (isUpdate()) {
            // When has changes and "before" exists, then "update"
            builder = ContentProviderOperation.newUpdate(targetUri);
            builder.withSelection(mIdColumn + "=" + getId(), null);
            builder.withValues(mAfter);
        }
        return builder;
    }

    /** {@inheritDoc} */
    public int describeContents() {
        // Nothing special about this parcel
        return 0;
    }

    /** {@inheritDoc} */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mBefore, flags);
        dest.writeParcelable(mAfter, flags);
        dest.writeString(mIdColumn);
    }

    public void readFromParcel(Parcel source) {
        final ClassLoader loader = getClass().getClassLoader();
        mBefore = source.<ContentValues> readParcelable(loader);
        mAfter = source.<ContentValues> readParcelable(loader);
        mIdColumn = source.readString();
    }

    public static final Creator<ValuesDelta> CREATOR = new Creator<ValuesDelta>() {
        public ValuesDelta createFromParcel(Parcel in) {
            final ValuesDelta values = new ValuesDelta();
            values.readFromParcel(in);
            return values;
        }

        public ValuesDelta[] newArray(int size) {
            return new ValuesDelta[size];
        }
    };

    public void setGroupRowId(long groupId) {
        put(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId);
    }

    public Long getGroupRowId() {
        return getAsLong(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID);
    }

    public void setPhoto(byte[] value) {
        put(ContactsContract.CommonDataKinds.Photo.PHOTO, value);
    }

    public byte[] getPhoto() {
        return getAsByteArray(ContactsContract.CommonDataKinds.Photo.PHOTO);
    }

    public void setSuperPrimary(boolean val) {
        if (val) {
            put(ContactsContract.Data.IS_SUPER_PRIMARY, 1);
        } else {
            put(ContactsContract.Data.IS_SUPER_PRIMARY, 0);
        }
    }

    public void setPhoneticFamilyName(String value) {
        put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME, value);
    }

    public void setPhoneticMiddleName(String value) {
        put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME, value);
    }

    public void setPhoneticGivenName(String value) {
        put(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME, value);
    }

    public String getPhoneticFamilyName() {
        return getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME);
    }

    public String getPhoneticMiddleName() {
        return getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME);
    }

    public String getPhoneticGivenName() {
        return getAsString(ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME);
    }

    public String getDisplayName() {
        return getAsString(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
    }

    public void setDisplayName(String name) {
        if (name == null) {
            putNull(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);
        } else {
            put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name);
        }
    }

    public void copyStructuredNameFieldsFrom(ValuesDelta name) {
        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME);

        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME);
        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME);
        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.PREFIX);
        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME);
        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.SUFFIX);

        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.PHONETIC_GIVEN_NAME);
        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.PHONETIC_MIDDLE_NAME);
        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.PHONETIC_FAMILY_NAME);

        copyStringFrom(name, ContactsContract.CommonDataKinds.StructuredName.FULL_NAME_STYLE);
        copyStringFrom(name, ContactsContract.Data.DATA11);
    }

    public String getPhoneNumber() {
        return getAsString(ContactsContract.CommonDataKinds.Phone.NUMBER);
    }

    public String getPhoneNormalizedNumber() {
        return getAsString(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
    }

    public boolean hasPhoneType() {
        return getPhoneType() != null;
    }

    public Integer getPhoneType() {
        return getAsInteger(ContactsContract.CommonDataKinds.Phone.TYPE);
    }

    public String getPhoneLabel() {
        return getAsString(ContactsContract.CommonDataKinds.Phone.LABEL);
    }

    public String getEmailData() {
        return getAsString(ContactsContract.CommonDataKinds.Email.DATA);
    }

    public boolean hasEmailType() {
        return getEmailType() != null;
    }

    public Integer getEmailType() {
        return getAsInteger(ContactsContract.CommonDataKinds.Email.TYPE);
    }

    public String getEmailLabel() {
        return getAsString(ContactsContract.CommonDataKinds.Email.LABEL);
    }
}
