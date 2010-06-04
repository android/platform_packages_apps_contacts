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

package com.android.contacts.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.QuickContact;
import android.util.Log;

/**
 * Stub translucent activity that just shows {@link QuickContactWindow} floating
 * above the caller. This temporary hack should eventually be replaced with
 * direct framework support.
 */
public final class QuickContactActivity extends Activity implements
        QuickContactWindow.OnDismissListener {
    private static final String TAG = "QuickContactActivity";

    static final boolean LOGV = true;
    static final boolean FORCE_CREATE = false;

    private QuickContactWindow mQuickContact;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        if (LOGV) Log.d(TAG, "onCreate");

        this.onNewIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (LOGV) Log.d(TAG, "onNewIntent");

        if (QuickContactWindow.TRACE_LAUNCH) {
            android.os.Debug.startMethodTracing(QuickContactWindow.TRACE_TAG);
        }

        if (mQuickContact == null || FORCE_CREATE) {
            if (LOGV) Log.d(TAG, "Preparing window");
            mQuickContact = new QuickContactWindow(this, this);
        }

        // Use our local window token for now
        final Uri lookupUri = intent.getData();
        final Bundle extras = intent.getExtras();

        // Read requested parameters for displaying
        final Rect target = getTargetRect(intent);
        final int mode = extras.getInt(QuickContact.EXTRA_MODE, QuickContact.MODE_MEDIUM);
        final String[] excludeMimes = extras.getStringArray(QuickContact.EXTRA_EXCLUDE_MIMES);

        mQuickContact.show(lookupUri, target, mode, excludeMimes, this);
    }

    private Rect getTargetRect(Intent intent) {
        Rect target = intent.getSourceBounds();
        if (target != null) {
            return target;
        }
        final Bundle extras = intent.getExtras();
        try {
            target = (Rect)extras.getParcelable(QuickContact.EXTRA_TARGET_RECT);
            if (target != null) {
                return target;
            }
        } catch (ClassCastException ex) {
            // fall through
        }
        return new Rect(0, 0, 0, 0);
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        if (LOGV) Log.w(TAG, "Unexpected back captured by stub activity");
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (LOGV) Log.d(TAG, "onPause");

        // Dismiss any dialog when pausing
        mQuickContact.dismiss();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (LOGV) Log.d(TAG, "onDestroy");
    }

    /** {@inheritDoc} */
    public void onDismiss(QuickContactWindow dialog) {
        if (LOGV) Log.d(TAG, "onDismiss");

        if (isTaskRoot() && !FORCE_CREATE) {
            // Instead of stopping, simply push this to the back of the stack.
            // This is only done when running at the top of the stack;
            // otherwise, we have been launched by someone else so need to
            // allow the user to go back to the caller.
            moveTaskToBack(false);
        } else {
            finish();
        }
    }
}
