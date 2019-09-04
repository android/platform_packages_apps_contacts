/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.test.mocks;

import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A programmable mock content provider.
 */
public class MockContentProvider extends android.test.mock.MockContentProvider {
    private static final String TAG = "MockContentProvider";

    public static class Query {

        private final Uri mUri;
        private UriMatcher mMatcher;

        private String[] mProjection;
        private String[] mDefaultProjection;
        private String mSelection;
        private String[] mSelectionArgs;
        private String mSortOrder;
        private List<Object> mRows = new ArrayList<>();
        private boolean mAnyProjection;
        private boolean mAnySelection;
        private boolean mAnySortOrder;
        private boolean mAnyNumberOfTimes;

        private boolean mExecuted;

        private Query() {
            mUri = null;
        }

        private Query(UriMatcher matcher) {
            mUri = null;
            mMatcher = matcher;
        }

        public Query(Uri uri) {
            mUri = uri;
        }

        @Override
        public String toString() {
            return queryToString(mUri, mProjection, mSelection, mSelectionArgs, mSortOrder);
        }

        public Query withProjection(String... projection) {
            mProjection = projection;
            return this;
        }

        public Query withDefaultProjection(String... projection) {
            mDefaultProjection = projection;
            return this;
        }

        public Query withAnyProjection() {
            mAnyProjection = true;
            return this;
        }

        public Query withSelection(String selection, String... selectionArgs) {
            mSelection = selection;
            mSelectionArgs = selectionArgs;
            return this;
        }

        public Query withAnySelection() {
            mAnySelection = true;
            return this;
        }

        public Query withSortOrder(String sortOrder) {
            mSortOrder = sortOrder;
            return this;
        }

        public Query withAnySortOrder() {
            mAnySortOrder = true;
            return this;
        }

        public Query returnRow(ContentValues values) {
            mRows.add(values);
            return this;
        }

        public Query returnRow(Object... row) {
            mRows.add(row);
            return this;
        }

        public Query returnEmptyCursor() {
            mRows.clear();
            return this;
        }

        public Query anyNumberOfTimes() {
            mAnyNumberOfTimes = true;
            return this;
        }

        public boolean equals(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            if (mUri == null) {
                if (mMatcher != null && mMatcher.match(uri) == UriMatcher.NO_MATCH) {
                    return false;
                }
            } else if (!uri.equals(mUri)) {
                return false;
            }

            if (!mAnyProjection && !Arrays.equals(projection, mProjection)) {
                return false;
            }

            if (!mAnySelection && !Objects.equals(selection, mSelection)) {
                return false;
            }

            if (!mAnySelection && !Arrays.equals(selectionArgs, mSelectionArgs)) {
                return false;
            }

            if (!mAnySortOrder && !Objects.equals(sortOrder, mSortOrder)) {
                return false;
            }

            return true;
        }

        public Cursor getResult(String[] projection) {
            String[] columnNames;
            if (mAnyProjection) {
                columnNames = projection != null ? projection : mDefaultProjection;
            } else {
                columnNames = mProjection != null ? mProjection : mDefaultProjection;
            }

            MatrixCursor cursor = new MatrixCursor(columnNames);
            for (Object row : mRows) {
                if (row instanceof Object[]) {
                    cursor.addRow((Object[]) row);
                } else {
                    ContentValues values = (ContentValues) row;
                    Object[] columns = new Object[columnNames.length];
                    for (int i = 0; i < columnNames.length; i++) {
                        columns[i] = values.get(columnNames[i]);
                    }
                    cursor.addRow(columns);
                }
            }
            return cursor;
        }

        public static Query forAnyUri() {
            return new Query();
        }

        public static Query forUrisMatching(UriMatcher matcher) {
            return new Query(matcher);
        }

        public static Query forUrisMatching(String authority, String... paths) {
            final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
            for (int i = 0; i < paths.length; i++) {
                matcher.addURI(authority, paths[i], i);
            }
            return new Query(matcher);
        }

    }

    public static class TypeQuery {
        private final Uri mUri;
        private final String mType;

        public TypeQuery(Uri uri, String type) {
            mUri = uri;
            mType = type;
        }

        public Uri getUri() {
            return mUri;
        }

        public String getType() {
            return mType;
        }

        @Override
        public String toString() {
            return mUri + " --> " + mType;
        }

        public boolean equals(Uri uri) {
            return getUri().equals(uri);
        }
    }

    public static class Insert {
        private final Uri mUri;
        private final ContentValues mContentValues;
        private final Uri mResultUri;
        private boolean mAnyNumberOfTimes;
        private boolean mIsExecuted;

        /**
         * Creates a new Insert to expect.
         *
         * @param uri the uri of the insertion request.
         * @param contentValues the ContentValues to insert.
         * @param resultUri the {@link Uri} for the newly inserted item.
         * @throws NullPointerException if any parameter is {@code null}.
         */
        public Insert(Uri uri, ContentValues contentValues, Uri resultUri) {
            mUri = Preconditions.checkNotNull(uri);
            mContentValues = Preconditions.checkNotNull(contentValues);
            mResultUri = Preconditions.checkNotNull(resultUri);
        }

        /**
         * Causes this insert expectation to be useable for mutliple calls to insert, rather than
         * just one.
         *
         * @return this
         */
        public Insert anyNumberOfTimes() {
            mAnyNumberOfTimes = true;
            return this;
        }

        private boolean equals(Uri uri, ContentValues contentValues) {
            return mUri.equals(uri) && mContentValues.equals(contentValues);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Insert insert = (Insert) o;
            return mAnyNumberOfTimes == insert.mAnyNumberOfTimes &&
                    mIsExecuted == insert.mIsExecuted &&
                    Objects.equals(mUri, insert.mUri) &&
                    Objects.equals(mContentValues, insert.mContentValues) &&
                    Objects.equals(mResultUri, insert.mResultUri);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUri, mContentValues, mResultUri, mAnyNumberOfTimes, mIsExecuted);
        }

        @Override
        public String toString() {
            return "Insert{" +
                    "mUri=" + mUri +
                    ", mContentValues=" + mContentValues +
                    ", mResultUri=" + mResultUri +
                    ", mAnyNumberOfTimes=" + mAnyNumberOfTimes +
                    ", mIsExecuted=" + mIsExecuted +
                    '}';
        }
    }

    public static class Delete {
        private final Uri mUri;

        private boolean mAnyNumberOfTimes;
        private boolean mAnySelection;
        @Nullable private String mSelection;
        @Nullable private String[] mSelectionArgs;
        private boolean mIsExecuted;
        private int mRowsAffected;

        /**
         * Creates a new Delete to expect.
         * @param uri the uri of the delete request.
         * @throws NullPointerException if uri is {@code null}.
         */
        public Delete(Uri uri) {
            mUri = Preconditions.checkNotNull(uri);
        }

        /**
         * Sets the given information as expected selection arguments.
         *
         * @param selection The selection to expect.
         * @param selectionArgs The selection args to expect.
         * @return this.
         */
        public Delete withSelection(String selection, @Nullable String[] selectionArgs) {
            mSelection = Preconditions.checkNotNull(selection);
            mSelectionArgs = selectionArgs;
            mAnySelection = false;
            return this;
        }

        /**
         * Sets this delete to expect any selection arguments.
         *
         * @return this.
         */
        public Delete withAnySelection() {
            mAnySelection = true;
            return this;
        }

        /**
         * Sets this delete to return the given number of rows affected.
         *
         * @param rowsAffected The value to return when this expected delete is executed.
         * @return this.
         */
        public Delete returnRowsAffected(int rowsAffected) {
            mRowsAffected = rowsAffected;
            return this;
        }

        /**
         * Causes this delete expectation to be useable for multiple calls to delete, rather than
         * just one.
         *
         * @return this.
         */
        public Delete anyNumberOfTimes() {
            mAnyNumberOfTimes = true;
            return this;
        }

        private boolean equals(Uri uri, String selection, String[] selectionArgs) {
            return mUri.equals(uri) && Objects.equals(mSelection, selection)
                    && Arrays.equals(mSelectionArgs, selectionArgs);
        }
    }

    public static class Update {
        private final Uri mUri;
        private final ContentValues mContentValues;
        @Nullable private String mSelection;
        @Nullable private String[] mSelectionArgs;
        private boolean mAnyNumberOfTimes;
        private boolean mIsExecuted;
        private int mRowsAffected;

        /**
         * Creates a new Update to expect.
         *
         * @param uri the uri of the update request.
         * @param contentValues the ContentValues to update.
         *
         * @throws NullPointerException if any parameter is {@code null}.
         */
        public Update(Uri uri,
                      ContentValues contentValues,
                      @Nullable String selection,
                      @Nullable String[] selectionArgs) {
            mUri = Preconditions.checkNotNull(uri);
            mContentValues = Preconditions.checkNotNull(contentValues);
            mSelection = selection;
            mSelectionArgs = selectionArgs;
        }

        /**
         * Causes this update expectation to be useable for mutliple calls to update, rather than
         * just one.
         *
         * @return this
         */
        public Update anyNumberOfTimes() {
            mAnyNumberOfTimes = true;
            return this;
        }

        /**
         * Sets this update to return the given number of rows affected.
         *
         * @param rowsAffected The value to return when this expected update is executed.
         * @return this.
         */
        public Update returnRowsAffected(int rowsAffected) {
            mRowsAffected = rowsAffected;
            return this;
        }

        private boolean equals(Uri uri,
                               ContentValues contentValues,
                               @Nullable String selection,
                               @Nullable String[] selectionArgs) {
            return mUri.equals(uri) && mContentValues.equals(contentValues) &&
                    Objects.equals(mSelection, selection) &&
                    Arrays.equals(mSelectionArgs, selectionArgs);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Update update = (Update) o;
            return mAnyNumberOfTimes == update.mAnyNumberOfTimes &&
                    mIsExecuted == update.mIsExecuted &&
                    Objects.equals(mUri, update.mUri) &&
                    Objects.equals(mContentValues, update.mContentValues) &&
                    Objects.equals(mSelection, update.mSelection) &&
                    Arrays.equals(mSelectionArgs, update.mSelectionArgs);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUri, mContentValues, mAnyNumberOfTimes, mIsExecuted, mSelection,
                    Arrays.hashCode(mSelectionArgs));
        }

        @Override
        public String toString() {
            return "Update{" +
                    "mUri=" + mUri +
                    ", mContentValues=" + mContentValues +
                    ", mAnyNumberOfTimes=" + mAnyNumberOfTimes +
                    ", mIsExecuted=" + mIsExecuted +
                    ", mSelection=" + mSelection +
                    ", mSelectionArgs=" + Arrays.toString(mSelectionArgs) +
                    '}';
        }
    }

    private List<Query> mExpectedQueries = new ArrayList<>();
    private Map<Uri, String> mExpectedTypeQueries = Maps.newHashMap();
    private List<Insert> mExpectedInserts = new ArrayList<>();
    private List<Delete> mExpectedDeletes = new ArrayList<>();
    private List<Update> mExpectedUpdates = new ArrayList<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    public Query expect(Query query) {
        mExpectedQueries.add(query);
        return query;
    }

    public Query expectQuery(Uri contentUri) {
        return expect(new Query(contentUri));
    }

    public Query expectQuery(String contentUri) {
        return expectQuery(Uri.parse(contentUri));
    }

    public void expectTypeQuery(Uri uri, String type) {
        mExpectedTypeQueries.put(uri, type);
    }

    public void expectInsert(Uri contentUri, ContentValues contentValues, Uri resultUri) {
        mExpectedInserts.add(new Insert(contentUri, contentValues, resultUri));
    }

    public Update expectUpdate(Uri contentUri,
                               ContentValues contentValues,
                               @Nullable String selection,
                               @Nullable String[] selectionArgs) {
        Update update = new Update(contentUri, contentValues, selection, selectionArgs);
        mExpectedUpdates.add(update);
        return update;
    }

    public Delete expectDelete(Uri contentUri) {
        Delete delete = new Delete(contentUri);
        mExpectedDeletes.add(delete);
        return delete;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (mExpectedQueries.isEmpty()) {
            Assert.fail("Unexpected query: Actual:"
                    + queryToString(uri, projection, selection, selectionArgs, sortOrder));
        }

        for (Iterator<Query> iterator = mExpectedQueries.iterator(); iterator.hasNext();) {
            Query query = iterator.next();
            if (query.equals(uri, projection, selection, selectionArgs, sortOrder)) {
                query.mExecuted = true;
                if (!query.mAnyNumberOfTimes) {
                    iterator.remove();
                }
                return query.getResult(projection);
            }
        }

        Assert.fail("Incorrect query. Expected one of: " + mExpectedQueries + ". Actual: " +
                queryToString(uri, projection, selection, selectionArgs, sortOrder));
        return null;
    }

    @Override
    public String getType(Uri uri) {
        if (mExpectedTypeQueries.isEmpty()) {
            Assert.fail("Unexpected getType query: " + uri);
        }

        String mimeType = mExpectedTypeQueries.get(uri);
        if (mimeType != null) {
            return mimeType;
        }

        Assert.fail("Unknown mime type for: " + uri);
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (mExpectedInserts.isEmpty()) {
            Assert.fail("Unexpected insert. Actual: " + insertToString(uri, values));
        }
        for (Iterator<Insert> iterator = mExpectedInserts.iterator(); iterator.hasNext(); ) {
            Insert insert = iterator.next();
            if (insert.equals(uri, values)) {
                insert.mIsExecuted = true;
                if (!insert.mAnyNumberOfTimes) {
                    iterator.remove();
                }
                return insert.mResultUri;
            }
        }

        Assert.fail("Incorrect insert. Expected one of: " + mExpectedInserts + ". Actual: "
                + insertToString(uri, values));
        return null;
    }

    private String insertToString(Uri uri, ContentValues contentValues) {
        return "Insert { uri=" + uri + ", contentValues=" + contentValues + '}';
    }

    @Override
    public int update(Uri uri,
                      ContentValues values,
                      @Nullable String selection,
                      @Nullable String[] selectionArgs) {
        if (mExpectedUpdates.isEmpty()) {
            Assert.fail("Unexpected update. Actual: "
                    + updateToString(uri, values, selection, selectionArgs));
        }
        for (Iterator<Update> iterator = mExpectedUpdates.iterator(); iterator.hasNext(); ) {
            Update update = iterator.next();
            if (update.equals(uri, values, selection, selectionArgs)) {
                update.mIsExecuted = true;
                if (!update.mAnyNumberOfTimes) {
                    iterator.remove();
                }
                return update.mRowsAffected;
            }
        }

        Assert.fail("Incorrect update. Expected one of: " + mExpectedUpdates + ". Actual: "
                + updateToString(uri, values, selection, selectionArgs));
        return - 1;
    }

    private String updateToString(Uri uri,
                                  ContentValues contentValues,
                                  @Nullable String selection,
                                  @Nullable String[] selectionArgs) {
        return "Update { uri=" + uri + ", contentValues=" + contentValues + ", selection=" +
                selection + ", selectionArgs" + Arrays.toString(selectionArgs) + '}';
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (mExpectedDeletes.isEmpty()) {
            Assert.fail("Unexpected delete. Actual: " + deleteToString(uri, selection,
                    selectionArgs));
        }
        for (Iterator<Delete> iterator = mExpectedDeletes.iterator(); iterator.hasNext(); ) {
            Delete delete = iterator.next();
            if (delete.equals(uri, selection, selectionArgs)) {
                delete.mIsExecuted = true;
                if (!delete.mAnyNumberOfTimes) {
                    iterator.remove();
                }
                return delete.mRowsAffected;
            }
        }
        Assert.fail("Incorrect delete. Expected one of: " + mExpectedDeletes + ". Actual: "
                + deleteToString(uri, selection, selectionArgs));
        return -1;
    }

    private String deleteToString(Uri uri, String selection, String[] selectionArgs) {
        return "Delete { uri=" + uri + ", selection=" + selection + ", selectionArgs"
                + Arrays.toString(selectionArgs) + '}';
    }

    private static String queryToString(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        StringBuilder sb = new StringBuilder();
        sb.append(uri == null ? "<Any Uri>" : uri).append(" ");
        if (projection != null) {
            sb.append(Arrays.toString(projection));
        } else {
            sb.append("[]");
        }
        if (selection != null) {
            sb.append(" selection: '").append(selection).append("'");
            if (selectionArgs != null) {
                sb.append(Arrays.toString(selectionArgs));
            } else {
                sb.append("[]");
            }
        }
        if (sortOrder != null) {
            sb.append(" sort: '").append(sortOrder).append("'");
        }
        return sb.toString();
    }

    public void verify() {
        verifyQueries();
        verifyInserts();
        verifyDeletes();
    }

    private void verifyQueries() {
        List<Query> missedQueries = new ArrayList<>();
        for (Query query : mExpectedQueries) {
            if (!query.mExecuted) {
                missedQueries.add(query);
            }
        }
        Assert.assertTrue("Not all expected queries have been called: " + missedQueries,
                missedQueries.isEmpty());
    }

    private void verifyInserts() {
        List<Insert> missedInserts = new ArrayList<>();
        for (Insert insert : mExpectedInserts) {
            if (!insert.mIsExecuted) {
                missedInserts.add(insert);
            }
        }
        Assert.assertTrue("Not all expected inserts have been called: " + missedInserts,
                missedInserts.isEmpty());
    }

    private void verifyDeletes() {
        List<Delete> missedDeletes = new ArrayList<>();
        for (Delete delete : mExpectedDeletes) {
            if (!delete.mIsExecuted) {
                missedDeletes.add(delete);
            }
        }
        Assert.assertTrue("Not all expected deletes have been called: " + missedDeletes,
                missedDeletes.isEmpty());
    }
}
