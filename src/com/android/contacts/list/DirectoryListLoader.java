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
package com.android.contacts.list;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.R;
import com.android.contacts.compat.DirectoryCompat;

/**
 * A specialized loader for the list of directories, see {@link Directory}.
 */
public class DirectoryListLoader extends AsyncTaskLoader<Cursor> {

    private static final String TAG = "ContactEntryListAdapter";

    public static final int SEARCH_MODE_NONE = 0;
    public static final int SEARCH_MODE_DEFAULT = 1;
    public static final int SEARCH_MODE_CONTACT_SHORTCUT = 2;
    public static final int SEARCH_MODE_DATA_SHORTCUT = 3;

    private static final class DirectoryQuery {
        public static final String ORDER_BY = Directory._ID;

        public static final String[] PROJECTION = {
            Directory._ID,
            Directory.PACKAGE_NAME,
            Directory.TYPE_RESOURCE_ID,
            Directory.DISPLAY_NAME,
            Directory.PHOTO_SUPPORT,
        };

        public static final int ID = 0;
        public static final int PACKAGE_NAME = 1;
        public static final int TYPE_RESOURCE_ID = 2;
        public static final int DISPLAY_NAME = 3;
        public static final int PHOTO_SUPPORT = 4;

        public static Uri getDirectoryUri(int mode) {
            if (mode == SEARCH_MODE_DATA_SHORTCUT || mode == SEARCH_MODE_CONTACT_SHORTCUT) {
                return Directory.CONTENT_URI;
            } else {
                return DirectoryCompat.getContentUri();
            }
        }
    }

    // This is a virtual column created for a MatrixCursor.
    public static final String DIRECTORY_TYPE = "directoryType";

    private static final String[] RESULT_PROJECTION = {
        Directory._ID,
        DIRECTORY_TYPE,
        Directory.DISPLAY_NAME,
        Directory.PHOTO_SUPPORT,
    };

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            forceLoad();
        }
    };

    private int mDirectorySearchMode;
    private boolean mLocalInvisibleDirectoryEnabled;

    private MatrixCursor mDefaultDirectoryList;

    public DirectoryListLoader(Context context) {
        super(context);
    }

    public void setDirectorySearchMode(int mode) {
        mDirectorySearchMode = mode;
    }

    /**
     * A flag that indicates whether the {@link Directory#LOCAL_INVISIBLE} directory should
     * be included in the results.
     */
    public void setLocalInvisibleDirectoryEnabled(boolean flag) {
        this.mLocalInvisibleDirectoryEnabled = flag;
    }

    @Override
    protected void onStartLoading() {
        getContext().getContentResolver().
                registerContentObserver(DirectoryQuery.getDirectoryUri(mDirectorySearchMode),
                        false, mObserver);
        forceLoad();
    }

    @Override
    protected void onStopLoading() {
        getContext().getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public Cursor loadInBackground() {
        if (mDirectorySearchMode == SEARCH_MODE_NONE) {
            return getDefaultDirectories();
        }

        MatrixCursor result = new MatrixCursor(RESULT_PROJECTION);
        Context context = getContext();
        PackageManager pm = context.getPackageManager();
        String selection;
        switch (mDirectorySearchMode) {
            case SEARCH_MODE_DEFAULT:
                selection = null;
                break;

            case SEARCH_MODE_CONTACT_SHORTCUT:
                selection = Directory.SHORTCUT_SUPPORT + "=" + Directory.SHORTCUT_SUPPORT_FULL;
                break;

            case SEARCH_MODE_DATA_SHORTCUT:
                selection = Directory.SHORTCUT_SUPPORT + " IN ("
                        + Directory.SHORTCUT_SUPPORT_FULL + ", "
                        + Directory.SHORTCUT_SUPPORT_DATA_ITEMS_ONLY + ")";
                break;

            default:
                throw new RuntimeException(
                        "Unsupported directory search mode: " + mDirectorySearchMode);
        }
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    DirectoryQuery.getDirectoryUri(mDirectorySearchMode),
                    DirectoryQuery.PROJECTION, selection, null, DirectoryQuery.ORDER_BY);

            if (cursor == null) {
                return result;
            }

            while(cursor.moveToNext()) {
                long directoryId = cursor.getLong(DirectoryQuery.ID);
                if (!mLocalInvisibleDirectoryEnabled
                        && DirectoryCompat.isInvisibleDirectory(directoryId)) {
                    continue;
                }
                String directoryType = null;

                String packageName = cursor.getString(DirectoryQuery.PACKAGE_NAME);
                int typeResourceId = cursor.getInt(DirectoryQuery.TYPE_RESOURCE_ID);
                if (!TextUtils.isEmpty(packageName) && typeResourceId != 0) {
                    try {
                        directoryType = pm.getResourcesForApplication(packageName)
                                .getString(typeResourceId);
                    } catch (Exception e) {
                        Log.e(TAG, "Cannot obtain directory type from package: " + packageName);
                    }
                }
                String displayName = cursor.getString(DirectoryQuery.DISPLAY_NAME);
                int photoSupport = cursor.getInt(DirectoryQuery.PHOTO_SUPPORT);
                result.addRow(new Object[]{directoryId, directoryType, displayName, photoSupport});
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Runtime Exception when querying directory");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    private Cursor getDefaultDirectories() {
        if (mDefaultDirectoryList == null) {
            mDefaultDirectoryList = new MatrixCursor(RESULT_PROJECTION);
            mDefaultDirectoryList.addRow(new Object[] {
                    Directory.DEFAULT,
                    getContext().getString(R.string.contactsList),
                    null,
                    null
            });
            mDefaultDirectoryList.addRow(new Object[] {
                    Directory.LOCAL_INVISIBLE,
                    getContext().getString(R.string.local_invisible_directory),
                    null,
                    null
            });
        }
        return mDefaultDirectoryList;
    }

    @Override
    protected void onReset() {
        stopLoading();
    }
}
