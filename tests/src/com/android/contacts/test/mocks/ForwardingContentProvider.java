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
package com.android.contacts.test.mocks;

import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.util.ArrayList;

/**
 * Forwards calls to a {@link ContentProviderClient}
 *
 * <p>This allows mixing use of the system content providers in a
 * {@link android.test.mock.MockContentResolver}
 * </p>
 */
public class ForwardingContentProvider extends android.test.mock.MockContentProvider {

    private final ContentProviderClient mClient;

    public ForwardingContentProvider(ContentProviderClient client) {
        mClient = client;
    }

    @Override
    public synchronized Cursor query(Uri url, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        try {
            return mClient.query(url, projection, selection, selectionArgs, sortOrder);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public synchronized Cursor query(Uri url, String[] projection, String selection,
            String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        try {
            return mClient.query(url, projection, selection, selectionArgs, sortOrder,
                    cancellationSignal);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized String getType(Uri url) {
        try {
            return mClient.getType(url);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized String[] getStreamTypes(Uri url, String mimeTypeFilter) {
        try {
            return mClient.getStreamTypes(url, mimeTypeFilter);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized Uri insert(Uri url, ContentValues initialValues) {
        try {
            return mClient.insert(url, initialValues);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized int bulkInsert(Uri url, ContentValues[] initialValues) {
        try {
            return mClient.bulkInsert(url, initialValues);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized int delete(Uri url, String selection, String[] selectionArgs) {
        try {
            return mClient.delete(url, selection, selectionArgs);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized int update(Uri url, ContentValues values,
            String selection, String[] selectionArgs) {
        try {
            return mClient.update(url, values, selection, selectionArgs);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public synchronized ParcelFileDescriptor openFile(Uri url, String mode) {
        try {
            return mClient.openFile(url, mode);
        } catch (RemoteException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public synchronized ParcelFileDescriptor openFile(Uri url, String mode,
            CancellationSignal signal) {
        try {
            return mClient.openFile(url, mode, signal);
        } catch (RemoteException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public synchronized AssetFileDescriptor openAssetFile(Uri url, String mode) {
        try {
            return mClient.openAssetFile(url, mode);
        } catch (RemoteException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public synchronized AssetFileDescriptor openAssetFile(Uri url, String mode,
            CancellationSignal signal) {
        try {
            return mClient.openAssetFile(url, mode, signal);
        } catch (RemoteException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType,
            Bundle opts) {
        try {
            return mClient.openTypedAssetFileDescriptor(uri, mimeType, opts);
        } catch (RemoteException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized AssetFileDescriptor openTypedAssetFileDescriptor(Uri uri, String mimeType,
            Bundle opts, CancellationSignal signal) {
        try {
            return mClient.openTypedAssetFileDescriptor(uri, mimeType, opts, signal);
        } catch (RemoteException|FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized ContentProviderResult[] applyBatch(
            ArrayList<ContentProviderOperation> operations) {
        try {
            return mClient.applyBatch(operations);
        } catch (RemoteException|OperationApplicationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public synchronized Bundle call(String method, String arg, Bundle extras) {
        try {
            return mClient.call(method, arg, extras);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public IBinder getIContentProviderBinder() {
        return new Binder();
    }
}
