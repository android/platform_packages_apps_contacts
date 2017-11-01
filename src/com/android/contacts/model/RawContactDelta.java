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

package com.android.contacts.model;

import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.util.Log;

import com.android.contacts.compat.CompatUtils;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Contains a {@link RawContact} and records any modifications separately so the
 * original {@link RawContact} can be swapped out with a newer version and the
 * changes still cleanly applied.
 * <p>
 * One benefit of this approach is that we can build changes entirely on an
 * empty {@link RawContact}, which then becomes an insert {@link RawContacts} case.
 * <p>
 * When applying modifications over an {@link RawContact}, we try finding the
 * original {@link Data#_ID} rows where the modifications took place. If those
 * rows are missing from the new {@link RawContact}, we know the original data must
 * be deleted, but to preserve the user modifications we treat as an insert.
 */
public class RawContactDelta implements Parcelable {
    // TODO: optimize by using contentvalues pool, since we allocate so many of them

    private static final String TAG = "EntityDelta";
    private static final boolean DEBUG = false;

    /**
     * Direct values from {@link Entity#getEntityValues()}.
     */
    private ValuesDelta mValues;

    /**
     * URI used for contacts queries, by default it is set to query raw contacts.
     * It can be set to query the profile's raw contact(s).
     */
    private Uri mContactsQueryUri = RawContacts.CONTENT_URI;

    /**
     * Internal map of children values from {@link Entity#getSubValues()}, which
     * we store here sorted into {@link Data#MIMETYPE} bins.
     */
    private final HashMap<String, ArrayList<ValuesDelta>> mEntries = Maps.newHashMap();

    public RawContactDelta() {
    }

    public RawContactDelta(ValuesDelta values) {
        mValues = values;
    }

    /**
     * Build an {@link RawContactDelta} using the given {@link RawContact} as a
     * starting point; the "before" snapshot.
     */
    public static RawContactDelta fromBefore(RawContact before) {
        final RawContactDelta rawContactDelta = new RawContactDelta();
        rawContactDelta.mValues = ValuesDelta.fromBefore(before.getValues());
        rawContactDelta.mValues.setIdColumn(RawContacts._ID);
        for (final ContentValues values : before.getContentValues()) {
            rawContactDelta.addEntry(ValuesDelta.fromBefore(values));
        }
        return rawContactDelta;
    }

    /**
     * Merge the "after" values from the given {@link RawContactDelta} onto the
     * "before" state represented by this {@link RawContactDelta}, discarding any
     * existing "after" states. This is typically used when re-parenting changes
     * onto an updated {@link Entity}.
     */
    public static RawContactDelta mergeAfter(RawContactDelta local, RawContactDelta remote) {
        // Bail early if trying to merge delete with missing local
        final ValuesDelta remoteValues = remote.mValues;
        if (local == null && (remoteValues.isDelete() || remoteValues.isTransient())) return null;

        // Create local version if none exists yet
        if (local == null) local = new RawContactDelta();

        if (DEBUG) {
            final Long localVersion = (local.mValues == null) ? null : local.mValues
                    .getAsLong(RawContacts.VERSION);
            final Long remoteVersion = remote.mValues.getAsLong(RawContacts.VERSION);
            Log.d(TAG, "Re-parenting from original version " + remoteVersion + " to "
                    + localVersion);
        }

        // Create values if needed, and merge "after" changes
        local.mValues = ValuesDelta.mergeAfter(local.mValues, remote.mValues);

        // Find matching local entry for each remote values, or create
        for (ArrayList<ValuesDelta> mimeEntries : remote.mEntries.values()) {
            for (ValuesDelta remoteEntry : mimeEntries) {
                final Long childId = remoteEntry.getId();

                // Find or create local match and merge
                final ValuesDelta localEntry = local.getEntry(childId);
                final ValuesDelta merged = ValuesDelta.mergeAfter(localEntry, remoteEntry);

                if (localEntry == null && merged != null) {
                    // No local entry before, so insert
                    local.addEntry(merged);
                }
            }
        }

        return local;
    }

    public ValuesDelta getValues() {
        return mValues;
    }

    public boolean isContactInsert() {
        return mValues.isInsert();
    }

    /**
     * Get the {@link ValuesDelta} child marked as {@link Data#IS_PRIMARY},
     * which may return null when no entry exists.
     */
    public ValuesDelta getPrimaryEntry(String mimeType) {
        final ArrayList<ValuesDelta> mimeEntries = getMimeEntries(mimeType, false);
        if (mimeEntries == null) return null;

        for (ValuesDelta entry : mimeEntries) {
            if (entry.isPrimary()) {
                return entry;
            }
        }

        // When no direct primary, return something
        return mimeEntries.size() > 0 ? mimeEntries.get(0) : null;
    }

    /**
     * calls {@link #getSuperPrimaryEntry(String, boolean)} with true
     * @see #getSuperPrimaryEntry(String, boolean)
     */
    public ValuesDelta getSuperPrimaryEntry(String mimeType) {
        return getSuperPrimaryEntry(mimeType, true);
    }

    /**
     * Returns the super-primary entry for the given mime type
     * @param forceSelection if true, will try to return some value even if a super-primary
     *     doesn't exist (may be a primary, or just a random item
     * @return
     */
    public ValuesDelta getSuperPrimaryEntry(String mimeType, boolean forceSelection) {
        final ArrayList<ValuesDelta> mimeEntries = getMimeEntries(mimeType, false);
        if (mimeEntries == null) return null;

        ValuesDelta primary = null;
        for (ValuesDelta entry : mimeEntries) {
            if (entry.isSuperPrimary()) {
                return entry;
            } else if (entry.isPrimary()) {
                primary = entry;
            }
        }

        if (!forceSelection) {
            return null;
        }

        // When no direct super primary, return something
        if (primary != null) {
            return primary;
        }
        return mimeEntries.size() > 0 ? mimeEntries.get(0) : null;
    }

    /**
     * Return the AccountType that this raw-contact belongs to.
     */
    public AccountType getRawContactAccountType(Context context) {
        ContentValues entityValues = getValues().getCompleteValues();
        String type = entityValues.getAsString(RawContacts.ACCOUNT_TYPE);
        String dataSet = entityValues.getAsString(RawContacts.DATA_SET);
        return AccountTypeManager.getInstance(context).getAccountType(type, dataSet);
    }

    public Long getRawContactId() {
        return getValues().getAsLong(RawContacts._ID);
    }

    public String getAccountName() {
        return getValues().getAsString(RawContacts.ACCOUNT_NAME);
    }

    public String getAccountType() {
        return getValues().getAsString(RawContacts.ACCOUNT_TYPE);
    }

    public String getDataSet() {
        return getValues().getAsString(RawContacts.DATA_SET);
    }

    public AccountType getAccountType(AccountTypeManager manager) {
        return manager.getAccountType(getAccountType(), getDataSet());
    }

    public AccountWithDataSet getAccountWithDataSet() {
        return new AccountWithDataSet(getAccountName(), getAccountType(), getDataSet());
    }

    public boolean isVisible() {
        return getValues().isVisible();
    }

    /**
     * Return the list of child {@link ValuesDelta} from our optimized map,
     * creating the list if requested.
     */
    private ArrayList<ValuesDelta> getMimeEntries(String mimeType, boolean lazyCreate) {
        ArrayList<ValuesDelta> mimeEntries = mEntries.get(mimeType);
        if (mimeEntries == null && lazyCreate) {
            mimeEntries = Lists.newArrayList();
            mEntries.put(mimeType, mimeEntries);
        }
        return mimeEntries;
    }

    public ArrayList<ValuesDelta> getMimeEntries(String mimeType) {
        return getMimeEntries(mimeType, false);
    }

    public int getMimeEntriesCount(String mimeType, boolean onlyVisible) {
        final ArrayList<ValuesDelta> mimeEntries = getMimeEntries(mimeType);
        if (mimeEntries == null) return 0;

        int count = 0;
        for (ValuesDelta child : mimeEntries) {
            // Skip deleted items when requesting only visible
            if (onlyVisible && !child.isVisible()) continue;
            count++;
        }
        return count;
    }

    public boolean hasMimeEntries(String mimeType) {
        return mEntries.containsKey(mimeType);
    }

    public ValuesDelta addEntry(ValuesDelta entry) {
        final String mimeType = entry.getMimetype();
        getMimeEntries(mimeType, true).add(entry);
        return entry;
    }

    public ArrayList<ContentValues> getContentValues() {
        ArrayList<ContentValues> values = Lists.newArrayList();
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta entry : mimeEntries) {
                if (!entry.isDelete()) {
                    values.add(entry.getCompleteValues());
                }
            }
        }
        return values;
    }

    /**
     * Find entry with the given {@link BaseColumns#_ID} value.
     */
    public ValuesDelta getEntry(Long childId) {
        if (childId == null) {
            // Requesting an "insert" entry, which has no "before"
            return null;
        }

        // Search all children for requested entry
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta entry : mimeEntries) {
                if (childId.equals(entry.getId())) {
                    return entry;
                }
            }
        }
        return null;
    }

    /**
     * Return the total number of {@link ValuesDelta} contained.
     */
    public int getEntryCount(boolean onlyVisible) {
        int count = 0;
        for (String mimeType : mEntries.keySet()) {
            count += getMimeEntriesCount(mimeType, onlyVisible);
        }
        return count;
    }

    @Override
    public boolean equals(Object object) {
        if (object instanceof RawContactDelta) {
            final RawContactDelta other = (RawContactDelta)object;

            // Equality failed if parent values different
            if (!other.mValues.equals(mValues)) return false;

            for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
                for (ValuesDelta child : mimeEntries) {
                    // Equality failed if any children unmatched
                    if (!other.containsEntry(child)) return false;
                }
            }

            // Passed all tests, so equal
            return true;
        }
        return false;
    }

    private boolean containsEntry(ValuesDelta entry) {
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                // Contained if we find any child that matches
                if (child.equals(entry)) return true;
            }
        }
        return false;
    }

    /**
     * Mark this entire object deleted, including any {@link ValuesDelta}.
     */
    public void markDeleted() {
        this.mValues.markDeleted();
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                child.markDeleted();
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("\n(");
        builder.append("Uri=");
        builder.append(mContactsQueryUri);
        builder.append(", Values=");
        builder.append(mValues != null ? mValues.toString() : "null");
        builder.append(", Entries={");
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                builder.append("\n\t");
                child.toString(builder);
            }
        }
        builder.append("\n})\n");
        return builder.toString();
    }

    /**
     * Consider building the given {@link ContentProviderOperation.Builder} and
     * appending it to the given list, which only happens if builder is valid.
     */
    private void possibleAdd(ArrayList<ContentProviderOperation> diff,
            ContentProviderOperation.Builder builder) {
        if (builder != null) {
            diff.add(builder.build());
        }
    }

    /**
     * For compatibility purpose, this method is copied from {@link #possibleAdd} and takes
     * BuilderWrapper and an ArrayList of CPOWrapper as parameters.
     */
    private void possibleAddWrapper(ArrayList<CPOWrapper> diff, BuilderWrapper bw) {
        if (bw != null && bw.getBuilder() != null) {
            diff.add(new CPOWrapper(bw.getBuilder().build(), bw.getType()));
        }
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will assert any
     * "before" state hasn't changed. This is maintained separately so that all
     * asserts can take place before any updates occur.
     */
    public void buildAssert(ArrayList<ContentProviderOperation> buildInto) {
        final Builder builder = buildAssertHelper();
        if (builder != null) {
            buildInto.add(builder.build());
        }
    }

    /**
     * For compatibility purpose, this method is copied from {@link #buildAssert} and takes an
     * ArrayList of CPOWrapper as parameter.
     */
    public void buildAssertWrapper(ArrayList<CPOWrapper> buildInto) {
        final Builder builder = buildAssertHelper();
        if (builder != null) {
            buildInto.add(new CPOWrapper(builder.build(), CompatUtils.TYPE_ASSERT));
        }
    }

    private Builder buildAssertHelper() {
        final boolean isContactInsert = mValues.isInsert();
        ContentProviderOperation.Builder builder = null;
        if (!isContactInsert) {
            // Assert version is consistent while persisting changes
            final Long beforeId = mValues.getId();
            final Long beforeVersion = mValues.getAsLong(RawContacts.VERSION);
            if (beforeId == null || beforeVersion == null) return builder;
            builder = ContentProviderOperation.newAssertQuery(mContactsQueryUri);
            builder.withSelection(RawContacts._ID + "=" + beforeId, null);
            builder.withValue(RawContacts.VERSION, beforeVersion);
        }
        return builder;
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will transform the
     * current "before" {@link Entity} state into the modified state which this
     * {@link RawContactDelta} represents.
     */
    public void buildDiff(ArrayList<ContentProviderOperation> buildInto) {
        final int firstIndex = buildInto.size();

        final boolean isContactInsert = mValues.isInsert();
        final boolean isContactDelete = mValues.isDelete();
        final boolean isContactUpdate = !isContactInsert && !isContactDelete;

        final Long beforeId = mValues.getId();

        Builder builder;

        if (isContactInsert) {
            // TODO: for now simply disabling aggregation when a new contact is
            // created on the phone.  In the future, will show aggregation suggestions
            // after saving the contact.
            mValues.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        }

        // Build possible operation at Contact level
        builder = mValues.buildDiff(mContactsQueryUri);
        possibleAdd(buildInto, builder);

        // Build operations for all children
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                // Ignore children if parent was deleted
                if (isContactDelete) continue;

                // Use the profile data URI if the contact is the profile.
                if (mContactsQueryUri.equals(Profile.CONTENT_RAW_CONTACTS_URI)) {
                    builder = child.buildDiff(Uri.withAppendedPath(Profile.CONTENT_URI,
                            RawContacts.Data.CONTENT_DIRECTORY));
                } else {
                    builder = child.buildDiff(Data.CONTENT_URI);
                }

                if (child.isInsert()) {
                    if (isContactInsert) {
                        // Parent is brand new insert, so back-reference _id
                        builder.withValueBackReference(Data.RAW_CONTACT_ID, firstIndex);
                    } else {
                        // Inserting under existing, so fill with known _id
                        builder.withValue(Data.RAW_CONTACT_ID, beforeId);
                    }
                } else if (isContactInsert && builder != null) {
                    // Child must be insert when Contact insert
                    throw new IllegalArgumentException("When parent insert, child must be also");
                }
                possibleAdd(buildInto, builder);
            }
        }

        final boolean addedOperations = buildInto.size() > firstIndex;
        if (addedOperations && isContactUpdate) {
            // Suspend aggregation while persisting updates
            builder = buildSetAggregationMode(beforeId, RawContacts.AGGREGATION_MODE_SUSPENDED);
            buildInto.add(firstIndex, builder.build());

            // Restore aggregation mode as last operation
            builder = buildSetAggregationMode(beforeId, RawContacts.AGGREGATION_MODE_DEFAULT);
            buildInto.add(builder.build());
        } else if (isContactInsert) {
            // Restore aggregation mode as last operation
            builder = ContentProviderOperation.newUpdate(mContactsQueryUri);
            builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT);
            builder.withSelection(RawContacts._ID + "=?", new String[1]);
            builder.withSelectionBackReference(0, firstIndex);
            buildInto.add(builder.build());
        }
    }

    /**
     * For compatibility purpose, this method is copied from {@link #buildDiff} and takes an
     * ArrayList of CPOWrapper as parameter.
     */
    public void buildDiffWrapper(ArrayList<CPOWrapper> buildInto) {
        final int firstIndex = buildInto.size();

        final boolean isContactInsert = mValues.isInsert();
        final boolean isContactDelete = mValues.isDelete();
        final boolean isContactUpdate = !isContactInsert && !isContactDelete;

        final Long beforeId = mValues.getId();

        if (isContactInsert) {
            // TODO: for now simply disabling aggregation when a new contact is
            // created on the phone.  In the future, will show aggregation suggestions
            // after saving the contact.
            mValues.put(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED);
        }

        // Build possible operation at Contact level
        BuilderWrapper bw = mValues.buildDiffWrapper(mContactsQueryUri);
        possibleAddWrapper(buildInto, bw);

        // Build operations for all children
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                // Ignore children if parent was deleted
                if (isContactDelete) continue;

                // Use the profile data URI if the contact is the profile.
                if (mContactsQueryUri.equals(Profile.CONTENT_RAW_CONTACTS_URI)) {
                    bw = child.buildDiffWrapper(Uri.withAppendedPath(Profile.CONTENT_URI,
                            RawContacts.Data.CONTENT_DIRECTORY));
                } else {
                    bw = child.buildDiffWrapper(Data.CONTENT_URI);
                }

                if (child.isInsert()) {
                    if (isContactInsert) {
                        // Parent is brand new insert, so back-reference _id
                        bw.getBuilder().withValueBackReference(Data.RAW_CONTACT_ID, firstIndex);
                    } else {
                        // Inserting under existing, so fill with known _id
                        bw.getBuilder().withValue(Data.RAW_CONTACT_ID, beforeId);
                    }
                } else if (isContactInsert && bw != null && bw.getBuilder() != null) {
                    // Child must be insert when Contact insert
                    throw new IllegalArgumentException("When parent insert, child must be also");
                }
                possibleAddWrapper(buildInto, bw);
            }
        }

        final boolean addedOperations = buildInto.size() > firstIndex;
        if (addedOperations && isContactUpdate) {
            // Suspend aggregation while persisting updates
            Builder builder =
                    buildSetAggregationMode(beforeId, RawContacts.AGGREGATION_MODE_SUSPENDED);
            buildInto.add(firstIndex, new CPOWrapper(builder.build(), CompatUtils.TYPE_UPDATE));

            // Restore aggregation mode as last operation
            builder = buildSetAggregationMode(beforeId, RawContacts.AGGREGATION_MODE_DEFAULT);
            buildInto.add(new CPOWrapper(builder.build(), CompatUtils.TYPE_UPDATE));
        } else if (isContactInsert) {
            // Restore aggregation mode as last operation
            Builder builder = ContentProviderOperation.newUpdate(mContactsQueryUri);
            builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT);
            builder.withSelection(RawContacts._ID + "=?", new String[1]);
            builder.withSelectionBackReference(0, firstIndex);
            buildInto.add(new CPOWrapper(builder.build(), CompatUtils.TYPE_UPDATE));
        }
    }

    /**
     * Build a {@link ContentProviderOperation} that changes
     * {@link RawContacts#AGGREGATION_MODE} to the given value.
     */
    protected Builder buildSetAggregationMode(Long beforeId, int mode) {
        Builder builder = ContentProviderOperation.newUpdate(mContactsQueryUri);
        builder.withValue(RawContacts.AGGREGATION_MODE, mode);
        builder.withSelection(RawContacts._ID + "=" + beforeId, null);
        return builder;
    }

    /** {@inheritDoc} */
    public int describeContents() {
        // Nothing special about this parcel
        return 0;
    }

    /** {@inheritDoc} */
    public void writeToParcel(Parcel dest, int flags) {
        final int size = this.getEntryCount(false);
        dest.writeInt(size);
        dest.writeParcelable(mValues, flags);
        dest.writeParcelable(mContactsQueryUri, flags);
        for (ArrayList<ValuesDelta> mimeEntries : mEntries.values()) {
            for (ValuesDelta child : mimeEntries) {
                dest.writeParcelable(child, flags);
            }
        }
    }

    public void readFromParcel(Parcel source) {
        final ClassLoader loader = getClass().getClassLoader();
        final int size = source.readInt();
        mValues = source.<ValuesDelta> readParcelable(loader);
        mContactsQueryUri = source.<Uri> readParcelable(loader);
        for (int i = 0; i < size; i++) {
            final ValuesDelta child = source.<ValuesDelta> readParcelable(loader);
            this.addEntry(child);
        }
    }

    /**
     * Used to set the query URI to the profile URI to store profiles.
     */
    public void setProfileQueryUri() {
        mContactsQueryUri = Profile.CONTENT_RAW_CONTACTS_URI;
    }

    public static final Parcelable.Creator<RawContactDelta> CREATOR =
            new Parcelable.Creator<RawContactDelta>() {
        public RawContactDelta createFromParcel(Parcel in) {
            final RawContactDelta state = new RawContactDelta();
            state.readFromParcel(in);
            return state;
        }

        public RawContactDelta[] newArray(int size) {
            return new RawContactDelta[size];
        }
    };

}
