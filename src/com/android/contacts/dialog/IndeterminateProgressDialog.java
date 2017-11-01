/*
 * Copyright (C) 2012 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.dialog;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;

/**
 * Indeterminate progress dialog wrapped up in a DialogFragment to work even when the device
 * orientation is changed. Currently, only supports adding a title and/or message to the progress
 * dialog.  There is an additional parameter of the minimum amount of time to display the progress
 * dialog even after a call to dismiss the dialog {@link #dismiss()} or
 * {@link #dismissAllowingStateLoss()}.
 * <p>
 * To create and show the progress dialog, use
 * {@link #show(FragmentManager, CharSequence, CharSequence, long)} and retain the reference to the
 * IndeterminateProgressDialog instance.
 * <p>
 * To dismiss the dialog, use {@link #dismiss()} or {@link #dismissAllowingStateLoss()} on the
 * instance.  The instance returned by
 * {@link #show(FragmentManager, CharSequence, CharSequence, long)} is guaranteed to be valid
 * after a device orientation change because the {@link #setRetainInstance(boolean)} is called
 * internally with true.
 */
public class IndeterminateProgressDialog extends DialogFragment {
    private static final String TAG = "IndeterminateProgress";

    private CharSequence mTitle;
    private CharSequence mMessage;
    private long mMinDisplayTime;
    private long mShowTime = 0;
    private boolean mActivityReady = false;
    private Dialog mOldDialog;
    private final Handler mHandler = new Handler();
    private boolean mCalledSuperDismiss = false;
    private boolean mAllowStateLoss;
    private final Runnable mDismisser = new Runnable() {
        @Override
        public void run() {
            superDismiss();
        }
    };

    /**
     * Creates and shows an indeterminate progress dialog.  Once the progress dialog is shown, it
     * will be shown for at least the minDisplayTime (in milliseconds), so that the progress dialog
     * does not flash in and out to quickly.
     */
    public static IndeterminateProgressDialog show(FragmentManager fragmentManager,
            CharSequence title, CharSequence message, long minDisplayTime) {
        IndeterminateProgressDialog dialogFragment = new IndeterminateProgressDialog();
        dialogFragment.mTitle = title;
        dialogFragment.mMessage = message;
        dialogFragment.mMinDisplayTime = minDisplayTime;
        dialogFragment.show(fragmentManager, TAG);
        dialogFragment.mShowTime = System.currentTimeMillis();
        dialogFragment.setCancelable(false);

        return dialogFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Create the progress dialog and set its properties
        final ProgressDialog dialog = new ProgressDialog(getActivity());
        dialog.setIndeterminate(true);
        dialog.setIndeterminateDrawable(null);
        dialog.setTitle(mTitle);
        dialog.setMessage(mMessage);

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        mActivityReady = true;

        // Check if superDismiss() had been called before.  This can happen if in a long
        // running operation, the user hits the home button and closes this fragment's activity.
        // Upon returning, we want to dismiss this progress dialog fragment.
        if (mCalledSuperDismiss) {
            superDismiss();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        mActivityReady = false;
    }

    /**
     * There is a race condition that is not handled properly by the DialogFragment class.
     * If we don't check that this onDismiss callback isn't for the old progress dialog from before
     * the device orientation change, then this will cause the newly created dialog after the
     * orientation change to be dismissed immediately.
     */
    @Override
    public void onDismiss(DialogInterface dialog) {
        if (mOldDialog != null && mOldDialog == dialog) {
            // This is the callback from the old progress dialog that was already dismissed before
            // the device orientation change, so just ignore it.
            return;
        }
        super.onDismiss(dialog);
    }

    /**
     * Save the old dialog that is about to get destroyed in case this is due to a change
     * in device orientation.  This will allow us to intercept the callback to
     * {@link #onDismiss(DialogInterface)} in case the callback happens after a new progress dialog
     * instance was created.
     */
    @Override
    public void onDestroyView() {
        mOldDialog = getDialog();
        super.onDestroyView();
    }

    /**
     * This tells the progress dialog to dismiss itself after guaranteeing to be shown for the
     * specified time in {@link #show(FragmentManager, CharSequence, CharSequence, long)}.
     */
    @Override
    public void dismiss() {
        mAllowStateLoss = false;
        dismissWhenReady();
    }

    /**
     * This tells the progress dialog to dismiss itself (with state loss) after guaranteeing to be
     * shown for the specified time in
     * {@link #show(FragmentManager, CharSequence, CharSequence, long)}.
     */
    @Override
    public void dismissAllowingStateLoss() {
        mAllowStateLoss = true;
        dismissWhenReady();
    }

    /**
     * Tells the progress dialog to dismiss itself after guaranteeing that the dialog had been
     * showing for at least the minimum display time as set in
     * {@link #show(FragmentManager, CharSequence, CharSequence, long)}.
     */
    private void dismissWhenReady() {
        // Compute how long the dialog has been showing
        final long shownTime = System.currentTimeMillis() - mShowTime;
        if (shownTime >= mMinDisplayTime) {
            // dismiss immediately
            mHandler.post(mDismisser);
        } else {
            // Need to wait some more, so compute the amount of time to sleep.
            final long sleepTime = mMinDisplayTime - shownTime;
            mHandler.postDelayed(mDismisser, sleepTime);
        }
    }

    /**
     * Actually dismiss the dialog fragment.
     */
    private void superDismiss() {
        mCalledSuperDismiss = true;
        if (mActivityReady) {
            // The fragment is either in onStart or past it, but has not gotten to onStop yet.
            // It is safe to dismiss this dialog fragment.
            if (mAllowStateLoss) {
                super.dismissAllowingStateLoss();
            } else {
                super.dismiss();
            }
        }
        // If mActivityReady is false, then this dialog fragment has already passed the onStop
        // state. This can happen if the user hit the 'home' button before this dialog fragment was
        // dismissed or if there is a configuration change.
        // In the event that this dialog fragment is re-attached and reaches onStart (e.g.,
        // because the user returns to this fragment's activity or the device configuration change
        // has re-attached this dialog fragment), because the mCalledSuperDismiss flag was set to
        // true, this dialog fragment will be dismissed within onStart.  So, there's nothing else
        // that needs to be done.
    }
}
