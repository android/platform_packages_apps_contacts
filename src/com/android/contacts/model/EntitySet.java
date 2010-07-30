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
import android.content.ContentResolver;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.ContentProviderOperation.Builder;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;

import com.google.android.collect.Lists;

import com.android.contacts.model.EntityDelta.ValuesDelta;

import java.util.ArrayList;

/**
 * Container for multiple {@link EntityDelta} objects, usually when editing
 * together as an entire aggregate. Provides convenience methods for parceling
 * and applying another {@link EntitySet} over it.
 */
public class EntitySet extends ArrayList<EntityDelta> implements Parcelable {
    private boolean mSplitRawContacts;

    private EntitySet() {
    }

    /**
     * Create an {@link EntitySet} that contains the given {@link EntityDelta},
     * usually when inserting a new {@link Contacts} entry.
     */
    public static EntitySet fromSingle(EntityDelta delta) {
        final EntitySet state = new EntitySet();
        state.add(delta);
        return state;
    }

    /**
     * Create an {@link EntitySet} based on {@link Contacts} specified by the
     * given query parameters. This closes the {@link EntityIterator} when
     * finished, so it doesn't subscribe to updates.
     */
    public static EntitySet fromQuery(ContentResolver resolver, String selection,
            String[] selectionArgs, String sortOrder) {
        EntityIterator iterator = RawContacts.newEntityIterator(resolver.query(
                RawContactsEntity.CONTENT_URI, null, selection, selectionArgs,
                sortOrder));
        try {
            final EntitySet state = new EntitySet();
            // Perform background query to pull contact details
            while (iterator.hasNext()) {
                // Read all contacts into local deltas to prepare for edits
                final Entity before = iterator.next();
                final EntityDelta entity = EntityDelta.fromBefore(before);
                state.add(entity);
            }
            return state;
        } finally {
            iterator.close();
        }
    }

    /**
     * Merge the "after" values from the given {@link EntitySet}, discarding any
     * previous "after" states. This is typically used when re-parenting user
     * edits onto an updated {@link EntitySet}.
     */
    public static EntitySet mergeAfter(EntitySet local, EntitySet remote) {
        if (local == null) local = new EntitySet();

        // For each entity in the remote set, try matching over existing
        for (EntityDelta remoteEntity : remote) {
            final Long rawContactId = remoteEntity.getValues().getId();

            // Find or create local match and merge
            final EntityDelta localEntity = local.getByRawContactId(rawContactId);
            final EntityDelta merged = EntityDelta.mergeAfter(localEntity, remoteEntity);

            if (localEntity == null && merged != null) {
                // No local entry before, so insert
                local.add(merged);
            }
        }

        return local;
    }

    /**
     * Build a list of {@link ContentProviderOperation} that will transform all
     * the "before" {@link Entity} states into the modified state which all
     * {@link EntityDelta} objects represent. This method specifically creates
     * any {@link AggregationExceptions} rules needed to groups edits together.
     */
    public ArrayList<ContentProviderOperation> buildDiff() {
        final ArrayList<ContentProviderOperation> diff = Lists.newArrayList();

        final long rawContactId = this.findRawContactId();
        int firstInsertRow = -1;

        // First pass enforces versions remain consistent
        for (EntityDelta delta : this) {
            delta.buildAssert(diff);
        }

        final int assertMark = diff.size();
        ArrayList<Integer> backRefs = new ArrayList<Integer>(size());

        // Second pass builds actual operations
        for (EntityDelta delta : this) {
            final int firstBatch = diff.size();

            if (firstBatch > 0) {
                backRefs.add(firstBatch);
            }

            delta.buildDiff(diff);

            // Only create rules for inserts
            if (!delta.isContactInsert()) continue;

            // If we are going to split all contacts, there is no point in first combining them
            if (mSplitRawContacts) continue;

            if (rawContactId != -1) {
                // Has existing contact, so bind to it strongly
                final Builder builder = beginKeepTogether();
                builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId);
                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, firstBatch);
                diff.add(builder.build());

            } else if (firstInsertRow == -1) {
                // First insert case, so record row
                firstInsertRow = firstBatch;

            } else {
                // Additional insert case, so point at first insert
                final Builder builder = beginKeepTogether();
                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID1, firstInsertRow);
                builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, firstBatch);
                diff.add(builder.build());
            }
        }

        if (mSplitRawContacts) {
            buildSplitContactDiff(diff, backRefs);
        }

        // No real changes if only left with asserts
        if (diff.size() == assertMark) {
            diff.clear();
        }

        return diff;
    }

    /**
     * Start building a {@link ContentProviderOperation} that will keep two
     * {@link RawContacts} together.
     */
    protected Builder beginKeepTogether() {
        final Builder builder = ContentProviderOperation
                .newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        return builder;
    }

    /**
     * Builds {@link AggregationExceptions} to split all constituent raw contacts into
     * separate contacts.
     */
    private void buildSplitContactDiff(final ArrayList<ContentProviderOperation> diff,
            ArrayList<Integer> backRefs) {
        int count = size();
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                if (i != j) {
                    buildSplitContactDiff(diff, i, j, backRefs);
                }
            }
        }
    }

    /**
     * Construct a {@link AggregationExceptions#TYPE_KEEP_SEPARATE}.
     */
    private void buildSplitContactDiff(ArrayList<ContentProviderOperation> diff, int index1,
            int index2, ArrayList<Integer> backRefs) {
        Builder builder =
                ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_SEPARATE);

        Long rawContactId1 = get(index1).getValues().getAsLong(RawContacts._ID);
        if (rawContactId1 != null && rawContactId1 >= 0) {
            builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        } else if (index1 < backRefs.size()) {
            builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID1,
                    backRefs.get(index1));
        } else {
            return;
        }

        Long rawContactId2 = get(index2).getValues().getAsLong(RawContacts._ID);
        if (rawContactId2 != null && rawContactId2 >= 0) {
            builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        } else if (index2 < backRefs.size()) {
            builder.withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2,
                    backRefs.get(index2));
        } else {
            return;
        }

        diff.add(builder.build());
    }

    /**
     * Search all contained {@link EntityDelta} for the first one with an
     * existing {@link RawContacts#_ID} value. Usually used when creating
     * {@link AggregationExceptions} during an update.
     */
    public long findRawContactId() {
        for (EntityDelta delta : this) {
            final Long rawContactId = delta.getValues().getAsLong(RawContacts._ID);
            if (rawContactId != null && rawContactId >= 0) {
                return rawContactId;
            }
        }
        return -1;
    }

    /**
     * Find {@link RawContacts#_ID} of the requested {@link EntityDelta}.
     */
    public Long getRawContactId(int index) {
        if (index >= 0 && index < this.size()) {
            final EntityDelta delta = this.get(index);
            final ValuesDelta values = delta.getValues();
            if (values.isVisible()) {
                return values.getAsLong(RawContacts._ID);
            }
        }
        return null;
    }

    public EntityDelta getByRawContactId(Long rawContactId) {
        final int index = this.indexOfRawContactId(rawContactId);
        return (index == -1) ? null : this.get(index);
    }

    /**
     * Find index of given {@link RawContacts#_ID} when present.
     */
    public int indexOfRawContactId(Long rawContactId) {
        if (rawContactId == null) return -1;
        final int size = this.size();
        for (int i = 0; i < size; i++) {
            final Long currentId = getRawContactId(i);
            if (rawContactId.equals(currentId)) {
                return i;
            }
        }
        return -1;
    }

    public ValuesDelta getSuperPrimaryEntry(final String mimeType) {
        ValuesDelta primary = null;
        ValuesDelta randomEntry = null;
        for (EntityDelta delta : this) {
            final ArrayList<ValuesDelta> mimeEntries = delta.getMimeEntries(mimeType);
            if (mimeEntries == null) return null;

            for (ValuesDelta entry : mimeEntries) {
                if (entry.isSuperPrimary()) {
                    return entry;
                } else if (primary == null && entry.isPrimary()) {
                    primary = entry;
                } else if (randomEntry == null) {
                    randomEntry = entry;
                }
            }
        }
        // When no direct super primary, return something
        if (primary != null) {
            return primary;
        }
        return randomEntry;
    }

    public void splitRawContacts() {
        mSplitRawContacts = true;
    }

    /** {@inheritDoc} */
    public int describeContents() {
        // Nothing special about this parcel
        return 0;
    }

    /** {@inheritDoc} */
    public void writeToParcel(Parcel dest, int flags) {
        final int size = this.size();
        dest.writeInt(size);
        for (EntityDelta delta : this) {
            dest.writeParcelable(delta, flags);
        }
    }

    public void readFromParcel(Parcel source) {
        final ClassLoader loader = getClass().getClassLoader();
        final int size = source.readInt();
        for (int i = 0; i < size; i++) {
            this.add(source.<EntityDelta> readParcelable(loader));
        }
    }

    public static final Parcelable.Creator<EntitySet> CREATOR = new Parcelable.Creator<EntitySet>() {
        public EntitySet createFromParcel(Parcel in) {
            final EntitySet state = new EntitySet();
            state.readFromParcel(in);
            return state;
        }

        public EntitySet[] newArray(int size) {
            return new EntitySet[size];
        }
    };
}
