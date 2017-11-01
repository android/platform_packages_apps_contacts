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
package com.android.contacts.logging;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.common.base.MoreObjects;

/**
 * Describes the results of a user search for a particular contact.
 */
public final class SearchState implements Parcelable {

    /** The length of the query string input by the user. */
    public int queryLength;

    /** The number of partitions (groups of results) presented to the user. */
    public int numPartitions;

    /** The total number of results (across all partitions) presented to the user. */
    public int numResults;

    /** The number of results presented to the user in the partition that was selected. */
    public int numResultsInSelectedPartition = -1;

    /** The zero-based index of the partition in which the clicked query result resides. */
    public int selectedPartition = -1;

    /** The index of the clicked query result within its partition. */
    public int selectedIndexInPartition = -1;

    /**
     * The zero-based index of the clicked query result among all results displayed to the user
     * (across partitions).
     */
    public int selectedIndex = -1;

    public static final Creator<SearchState> CREATOR = new Creator<SearchState>() {
        @Override
        public SearchState createFromParcel(Parcel in) {
            return new SearchState(in);
        }

        @Override
        public SearchState[] newArray(int size) {
            return new SearchState[size];
        }
    };

    public SearchState() {
    }

    protected SearchState(Parcel source) {
        readFromParcel(source);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("queryLength", queryLength)
                .add("numPartitions", numPartitions)
                .add("numResults", numResults)
                .add("numResultsInSelectedPartition", numResultsInSelectedPartition)
                .add("selectedPartition", selectedPartition)
                .add("selectedIndexInPartition", selectedIndexInPartition)
                .add("selectedIndex", selectedIndex)
                .toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(queryLength);
        dest.writeInt(numPartitions);
        dest.writeInt(numResults);
        dest.writeInt(numResultsInSelectedPartition);
        dest.writeInt(selectedPartition);
        dest.writeInt(selectedIndexInPartition);
        dest.writeInt(selectedIndex);
    }

    private void readFromParcel(Parcel source) {
        queryLength = source.readInt();
        numPartitions = source.readInt();
        numResults = source.readInt();
        numResultsInSelectedPartition = source.readInt();
        selectedPartition = source.readInt();
        selectedIndexInPartition = source.readInt();
        selectedIndex = source.readInt();
    }
}
