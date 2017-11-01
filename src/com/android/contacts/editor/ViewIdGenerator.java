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

package com.android.contacts.editor;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.dataitem.DataKind;

/**
 * A class that provides unique view ids for {@link ContentEditorView},
 * {@link LabeledEditorView} and {@link EditView} on {@link EditContactActivity}.
 * It is used to assign a unique but consistent id to each view across {@link EditContactActivity}'s
 * lifecycle, so that we can re-construct view state (e.g. focused view) when the screen rotates.
 *
 * <p>This class is not thread safe.
 */
public final class ViewIdGenerator implements Parcelable {
    private static final int INVALID_VIEW_ID = 0;
    private static final int INITIAL_VIEW_ID = 1;

    public static final int NO_VIEW_INDEX = -1;

    private int mNextId;

    /**
     * Used as a map from the "key" of the views to actual ids.  {@link #getId()} generates keys for
     * the views.
     */
    private Bundle mIdMap = new Bundle();

    private static final char KEY_SEPARATOR = '*';

    private final static StringBuilder sWorkStringBuilder = new StringBuilder();

    public ViewIdGenerator() {
        mNextId = INITIAL_VIEW_ID;
    }

    /** {@inheritDoc} */
    public int describeContents() {
        return 0;
    }

    /**
     * Returns an id for a view associated with specified contact field.
     *
     * @param entity {@link RawContactDelta} associated with the view
     * @param kind {@link DataKind} associated with the view, or null if none exists.
     * @param values {@link ValuesDelta} associated with the view, or null if none exists.
     * @param viewIndex index of the view in the parent {@link Editor}, if it's a leave view.
     *     Otherwise, pass {@link #NO_VIEW_INDEX}.
     */
    public int getId(RawContactDelta entity, DataKind kind, ValuesDelta values,
            int viewIndex) {
        final String k = getMapKey(entity, kind, values, viewIndex);

        int id = mIdMap.getInt(k, INVALID_VIEW_ID);
        if (id == INVALID_VIEW_ID) {
            // Make sure the new id won't conflict with auto-generated ids by masking with 0xffff.
            id = (mNextId++) & 0xFFFF;
            mIdMap.putInt(k, id);
        }
        return id;
    }

    private static String getMapKey(RawContactDelta entity, DataKind kind, ValuesDelta values,
            int viewIndex) {
        sWorkStringBuilder.setLength(0);
        if (entity != null) {
            sWorkStringBuilder.append(entity.getValues().getId());

            if (kind != null) {
                sWorkStringBuilder.append(KEY_SEPARATOR);
                sWorkStringBuilder.append(kind.mimeType);

                if (values != null) {
                    sWorkStringBuilder.append(KEY_SEPARATOR);
                    sWorkStringBuilder.append(values.getId());

                    if (viewIndex != NO_VIEW_INDEX) {
                        sWorkStringBuilder.append(KEY_SEPARATOR);
                        sWorkStringBuilder.append(viewIndex);
                    }
                }
            }
        }
        return sWorkStringBuilder.toString();
    }

    /** {@Override} */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mNextId);
        dest.writeBundle(mIdMap);
    }

    private void readFromParcel(Parcel src) {
        mNextId = src.readInt();
        mIdMap = src.readBundle();
    }

    public static final Parcelable.Creator<ViewIdGenerator> CREATOR =
            new Parcelable.Creator<ViewIdGenerator>() {
        public ViewIdGenerator createFromParcel(Parcel in) {
            final ViewIdGenerator vig = new ViewIdGenerator();
            vig.readFromParcel(in);
            return vig;
        }

        public ViewIdGenerator[] newArray(int size) {
            return new ViewIdGenerator[size];
        }
    };
}
