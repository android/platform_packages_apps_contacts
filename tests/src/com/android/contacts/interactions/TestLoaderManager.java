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

package com.android.contacts.interactions;

import android.app.LoaderManager;
import android.content.AsyncTaskLoader;
import android.content.Loader;
import android.os.Bundle;
import android.util.Log;

import com.google.common.annotations.VisibleForTesting;

import junit.framework.Assert;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * This implementation of TestLoaderManagerBase uses hidden APIs and must therefore
 * be kept outside of the main Contacts apk.
 */
public class TestLoaderManager extends TestLoaderManagerBase {
    private static final String TAG = "TestLoaderManager";

    private final HashSet<Integer> mFinishedLoaders;

    private LoaderManager mDelegate;

    @VisibleForTesting
    public TestLoaderManager() {
        mFinishedLoaders = new HashSet<Integer>();
    }

    /**
     * Sets the object to which we delegate the actual work.
     * <p>
     * It can not be set to null. Once set, it cannot be changed (but it allows setting it to the
     * same value again).
     */
    public void setDelegate(LoaderManager delegate) {
        if (delegate == null || (mDelegate != null && mDelegate != delegate)) {
            throw new IllegalArgumentException("TestLoaderManager cannot be shared");
        }

        mDelegate = delegate;
    }

    public LoaderManager getDelegate() {
        return mDelegate;
    }

    public void reset() {
        mFinishedLoaders.clear();
    }

    /**
     * Waits for the specified loaders to complete loading.
     * <p>
     * If one of the loaders has already completed since the last call to {@link #reset()}, it will
     * not wait for it to complete again.
     */
    @VisibleForTesting
    public synchronized void waitForLoaders(int... loaderIds) {
        List<Loader<?>> loaders = new ArrayList<Loader<?>>(loaderIds.length);
        for (int loaderId : loaderIds) {
            if (mFinishedLoaders.contains(loaderId)) {
                // This loader has already completed since the last reset, do not wait for it.
                continue;
            }

            final AsyncTaskLoader<?> loader =
                    (AsyncTaskLoader<?>) mDelegate.getLoader(loaderId);
            if (loader == null) {
                Assert.fail("Loader does not exist: " + loaderId);
                return;
            }

            loaders.add(loader);
        }

        waitForLoaders(loaders.toArray(new Loader<?>[0]));
    }

    /**
     * Waits for the specified loaders to complete loading.
     */
    public static void waitForLoaders(Loader<?>... loaders) {
        // We want to wait for each loader using a separate thread, so that we can
        // simulate race conditions.
        Thread[] waitThreads = new Thread[loaders.length];
        for (int i = 0; i < loaders.length; i++) {
            final AsyncTaskLoader<?> loader = (AsyncTaskLoader<?>) loaders[i];
            waitThreads[i] = new Thread("LoaderWaitingThread" + i) {
                @Override
                public void run() {
                    try {
                        AsyncTaskLoader.class.getMethod("waitForLoader").invoke(loader);
                    } catch (Throwable e) {
                        Log.e(TAG, "Exception while waiting for loader: " + loader.getId(), e);
                        Assert.fail("Exception while waiting for loader: " + loader.getId());
                    }
                }
            };
            waitThreads[i].start();
        }

        // Now we wait for all these threads to finish
        for (Thread thread : waitThreads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    }

    @Override
    public <D> Loader<D> initLoader(final int id, Bundle args, final LoaderCallbacks<D> callback) {
        return mDelegate.initLoader(id, args, new LoaderManager.LoaderCallbacks<D>() {
            @Override
            public Loader<D> onCreateLoader(int id, Bundle args) {
                return callback.onCreateLoader(id, args);
            }

            @Override
            public void onLoadFinished(Loader<D> loader, D data) {
                callback.onLoadFinished(loader, data);
                synchronized (this) {
                    mFinishedLoaders.add(id);
                }
            }

            @Override
            public void onLoaderReset(Loader<D> loader) {
                callback.onLoaderReset(loader);
            }
        });
    }

    @Override
    public <D> Loader<D> restartLoader(int id, Bundle args, LoaderCallbacks<D> callback) {
        return mDelegate.restartLoader(id, args, callback);
    }

    @Override
    public void destroyLoader(int id) {
        mDelegate.destroyLoader(id);
    }

    @Override
    public <D> Loader<D> getLoader(int id) {
        return mDelegate.getLoader(id);
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        mDelegate.dump(prefix, fd, writer, args);
    }
}
