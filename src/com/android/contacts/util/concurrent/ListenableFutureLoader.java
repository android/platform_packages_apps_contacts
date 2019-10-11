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
package com.android.contacts.util.concurrent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

/**
 * Wraps a ListenableFuture for integration with {@link android.app.LoaderManager}
 *
 * <p>Using a loader ensures that the result is delivered while the receiving component (activity
 * or fragment) is resumed and also prevents leaking references these components
 * </p>
 */
public abstract class ListenableFutureLoader<D> extends Loader<D> {
    private static final String TAG = "FutureLoader";

    private final IntentFilter mReloadFilter;
    private final Executor mUiExecutor;
    private final LocalBroadcastManager mLocalBroadcastManager;

    private ListenableFuture<D> mFuture;
    private D mLoadedData;

    private BroadcastReceiver mReceiver;

    /**
     * Stores away the application context associated with context.
     * Since Loaders can be used across multiple activities it's dangerous to
     * store the context directly; always use {@link #getContext()} to retrieve
     * the Loader's Context, don't use the constructor argument directly.
     * The Context returned by {@link #getContext} is safe to use across
     * Activity instances.
     *
     * @param context used to retrieve the application context.
     */
    public ListenableFutureLoader(Context context) {
        this(context, null);
    }

    public ListenableFutureLoader(Context context, IntentFilter reloadBroadcastFilter) {
        super(context);
        mUiExecutor = ContactsExecutors.newUiThreadExecutor();
        mReloadFilter = reloadBroadcastFilter;
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(context);
    }

    @Override
    protected void onStartLoading() {
        if (mReloadFilter != null && mReceiver == null) {
            mReceiver = new ForceLoadReceiver();
            mLocalBroadcastManager.registerReceiver(mReceiver, mReloadFilter);
        }

        if (mLoadedData != null) {
            deliverResult(mLoadedData);
        }
        if (mFuture == null) {
            takeContentChanged();
            forceLoad();
        } else if (takeContentChanged()) {
            forceLoad();
        }
    }

    @Override
    protected void onForceLoad() {
        mFuture = loadData();
        Futures.addCallback(mFuture, new FutureCallback<D>() {
            @Override
            public void onSuccess(D result) {
                if (mLoadedData == null || !isSameData(mLoadedData, result)) {
                    deliverResult(result);
                }
                mLoadedData = result;
                commitContentChanged();
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof CancellationException) {
                    Log.i(TAG, "Loading cancelled", t);
                    rollbackContentChanged();
                } else {
                    Log.e(TAG, "Loading failed", t);
                }
            }
        }, mUiExecutor);
    }

    @Override
    protected void onStopLoading() {
        if (mFuture != null) {
            mFuture.cancel(false);
            mFuture = null;
        }
    }

    @Override
    protected void onReset() {
        mFuture = null;
        mLoadedData = null;
        if (mReceiver != null) {
            mLocalBroadcastManager.unregisterReceiver(mReceiver);
        }
    }

    protected abstract ListenableFuture<D> loadData();

    /**
     * Returns whether the newly loaded data is the same as the cached value
     *
     * <p>This allows subclasses to suppress delivering results when the data hasn't
     * actually changed. By default it will always return false.
     * </p>
     */
    protected boolean isSameData(D previousData, D newData) {
        return false;
    }

    public final D getLoadedData() {
        return mLoadedData;
    }

    public class ForceLoadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            onContentChanged();
        }
    }
}
