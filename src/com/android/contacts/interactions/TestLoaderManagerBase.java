/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;

/**
 * A {@link LoaderManager} that records which loaders have been completed.
 * <p>
 * You should wrap the existing LoaderManager with an instance of this class, which will then
 * delegate to the original object.
 * <p>
 * Typically, one would override {@link android.app.Activity#getLoaderManager()} to return the
 * TestLoaderManager and ensuring it wraps the {@link LoaderManager} for this object, e.g.:
 * <pre>
 *   private TestLoaderManager mTestLoaderManager;
 *
 *   public LoaderManager getLoaderManager() {
 *     LoaderManager loaderManager = super.getLoaderManager();
 *     if (mTestLoaderManager != null) {
 *       mTestLoaderManager.setDelegate(loaderManager);
 *       return mTestLoaderManager;
 *     } else {
 *       return loaderManager;
 *     }
 *   }
 *
 *   void setTestLoaderManager(TestLoaderManager testLoaderManager) {
 *     mTestLoaderManager = testLoaderManager;
 *   }
 * </pre>
 * In the tests, one would set the TestLoaderManager upon creating the activity, and then wait for
 * the loader to complete.
 * <pre>
 *   public void testLoadedCorrect() {
 *     TestLoaderManager testLoaderManager = new TestLoaderManager();
 *     getActivity().setTestLoaderManager(testLoaderManager);
 *     runOnUiThread(new Runnable() { public void run() { getActivity().startLoading(); } });
 *     testLoaderManager.waitForLoader(R.id.test_loader_id);
 *   }
 * </pre>
 * If the loader completes before the call to {@link #waitForLoaders(int...)}, the TestLoaderManager
 * will have stored the fact that the loader has completed and correctly terminate immediately.
 * <p>
 * It one needs to wait for the same loader multiple times, call {@link #reset()} between the them
 * as in:
 * <pre>
 *   public void testLoadedCorrect() {
 *     TestLoaderManager testLoaderManager = new TestLoaderManager();
 *     getActivity().setTestLoaderManager(testLoaderManager);
 *     runOnUiThread(new Runnable() { public void run() { getActivity().startLoading(); } });
 *     testLoaderManager.waitForLoader(R.id.test_loader_id);
 *     testLoaderManager.reset();
 *     // Load and wait again.
 *     runOnUiThread(new Runnable() { public void run() { getActivity().startLoading(); } });
 *     testLoaderManager.waitForLoader(R.id.test_loader_id);
 *   }
 * </pre>
 */
@VisibleForTesting
abstract class TestLoaderManagerBase extends LoaderManager {

    /**
     * Waits for the specified loaders to complete loading.
     */
    public abstract void waitForLoaders(int... loaderIds);

    /**
     * Sets the object to which we delegate the actual work.
     * <p>
     * It can not be set to null. Once set, it cannot be changed (but it allows setting it to the
     * same value again).
     */
    @VisibleForTesting
    public abstract void setDelegate(LoaderManager delegate);

}
