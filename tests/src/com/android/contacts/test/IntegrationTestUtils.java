/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.PowerManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Preconditions;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/** Some utility methods for making integration testing smoother. */
@ThreadSafe
public class IntegrationTestUtils {
    private static final String TAG = "IntegrationTestUtils";

    private final Instrumentation mInstrumentation;
    private final Object mLock = new Object();
    @GuardedBy("mLock") private PowerManager.WakeLock mWakeLock;

    public IntegrationTestUtils(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    /**
     * Find a view by a given resource id, from the given activity, and click it, iff it is
     * enabled according to {@link View#isEnabled()}.
     */
    public void clickButton(final Activity activity, final int buttonResourceId) throws Throwable {
        runOnUiThreadAndGetTheResult(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                View view = activity.findViewById(buttonResourceId);
                Assert.assertNotNull(view);
                if (view.isEnabled()) {
                    view.performClick();
                }
                return null;
            }
        });
    }

    /** Returns the result of running {@link TextView#getText()} on the ui thread. */
    public CharSequence getText(final TextView view) throws Throwable {
        return runOnUiThreadAndGetTheResult(new Callable<CharSequence>() {
            @Override
            public CharSequence call() {
                return view.getText();
            }
        });
    }

    // TODO: Move this class and the appropriate documentation into a test library, having checked
    // first to see if exactly this code already exists or not.
    /**
     * Execute a callable on the ui thread, returning its result synchronously.
     * <p>
     * Waits for an idle sync on the main thread (see {@link Instrumentation#waitForIdle(Runnable)})
     * before executing this callable.
     */
    public <T> T runOnUiThreadAndGetTheResult(Callable<T> callable) throws Throwable {
        FutureTask<T> future = new FutureTask<T>(callable);
        mInstrumentation.waitForIdle(future);
        try {
            return future.get();
        } catch (ExecutionException e) {
            // Unwrap the cause of the exception and re-throw it.
            throw e.getCause();
        }
    }

    /**
     * Wake up the screen, useful in tests that want or need the screen to be on.
     * <p>
     * This is usually called from setUp() for tests that require it.  After calling this method,
     * {@link #releaseScreenWakeLock()} must be called, this is usually done from tearDown().
     */
    public void acquireScreenWakeLock(Context context) {
        synchronized (mLock) {
            Preconditions.checkState(mWakeLock == null, "mWakeLock was already held");
            mWakeLock = ((PowerManager) context.getSystemService(Context.POWER_SERVICE))
                    .newWakeLock(
                            PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE | PowerManager.FULL_WAKE_LOCK, TAG);
            mWakeLock.acquire();
        }
    }

    /** Release the wake lock previously acquired with {@link #acquireScreenWakeLock(Context)}. */
    public void releaseScreenWakeLock() {
        synchronized (mLock) {
            // We don't use Preconditions to force you to have acquired before release.
            // This is because we don't want unnecessary exceptions in tearDown() since they'll
            // typically mask the actual exception that happened during the test.
            // The other reason is that this method is most likely to be called from tearDown(),
            // which is invoked within a finally block, so it's not infrequently the case that
            // the setUp() method fails before getting the lock, at which point we don't want
            // to fail in tearDown().
            if (mWakeLock != null) {
                mWakeLock.release();
                mWakeLock = null;
            }
        }
    }

    /**
     * Gets all {@link TextView} objects whose {@link TextView#getText()} contains the given text as
     * a substring.
     */
    public List<TextView> getTextViewsWithString(final Activity activity, final String text)
            throws Throwable {
        return getTextViewsWithString(getRootView(activity), text);
    }

    /**
     * Gets all {@link TextView} objects whose {@link TextView#getText()} contains the given text as
     * a substring for the given root view.
     */
    public List<TextView> getTextViewsWithString(final View rootView, final String text)
            throws Throwable {
        return runOnUiThreadAndGetTheResult(new Callable<List<TextView>>() {
            @Override
            public List<TextView> call() throws Exception {
                List<TextView> matchingViews = new ArrayList<TextView>();
                for (TextView textView : getAllViews(TextView.class, rootView)) {
                    if (textView.getText().toString().contains(text)) {
                        matchingViews.add(textView);
                    }
                }
                return matchingViews;
            }
        });
    }

    /** Find the root view for a given activity. */
    public static View getRootView(Activity activity) {
        return activity.findViewById(android.R.id.content).getRootView();
    }

    /**
     * Gets a list of all views of a given type, rooted at the given parent.
     * <p>
     * This method will recurse down through all {@link ViewGroup} instances looking for
     * {@link View} instances of the supplied class type. Specifically it will use the
     * {@link Class#isAssignableFrom(Class)} method as the test for which views to add to the list,
     * so if you provide {@code View.class} as your type, you will get every view. The parent itself
     * will be included also, should it be of the right type.
     * <p>
     * This call manipulates the ui, and as such should only be called from the application's main
     * thread.
     */
    private static <T extends View> List<T> getAllViews(final Class<T> clazz, final View parent) {
        List<T> results = new ArrayList<T>();
        if (parent.getClass().equals(clazz)) {
            results.add(clazz.cast(parent));
        }
        if (parent instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) parent;
            for (int i = 0; i < viewGroup.getChildCount(); ++i) {
                results.addAll(getAllViews(clazz, viewGroup.getChildAt(i)));
            }
        }
        return results;
    }
}
