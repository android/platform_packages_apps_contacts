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
 * limitations under the License
 */

package com.android.contacts.editor;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.contacts.R;

/**
 * Shows a dialog asking the user whether to split the contact. The result is passed back
 * to the Fragment that is configured by {@link Fragment#setTargetFragment(Fragment, int)}, which
 * has to implement {@link SplitContactConfirmationDialogFragment.Listener}.
 * Does not split the contact itself.
 */
public class SplitContactConfirmationDialogFragment extends DialogFragment {

    private static final String ARG_HAS_PENDING_CHANGES = "hasPendingChanges";
    public static final String TAG = "SplitConfirmation";

    /**
     * Callbacks for the dialog host.
     */
    public interface Listener {

        /**
         * Invoked after the user has confirmed that they want to proceed with the split.
         *
         * @param hasPendingChanges whether there are unsaved changes in the underlying contact
         *         that should be saved before the split.
         */
        void onSplitContactConfirmed(boolean hasPendingChanges);

        /**
         * Invoked if the user has canceled or dismissed the dialog without making a choice.
         */
        void onSplitContactCanceled();
    }

    public static void show(ContactEditorFragment fragment, boolean hasPendingChanges) {
        final Bundle args = new Bundle();
        args.putBoolean(ARG_HAS_PENDING_CHANGES, hasPendingChanges);

        final SplitContactConfirmationDialogFragment dialog = new
                SplitContactConfirmationDialogFragment();
        dialog.setTargetFragment(fragment, 0);
        dialog.setArguments(args);
        dialog.show(fragment.getFragmentManager(), "splitContact");
    }

    private boolean mHasPendingChanges;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHasPendingChanges = getArguments() != null
                && getArguments().getBoolean(ARG_HAS_PENDING_CHANGES);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(mHasPendingChanges
                ? R.string.splitConfirmationWithPendingChanges
                : R.string.splitConfirmation);
        builder.setPositiveButton(mHasPendingChanges
                ? R.string.splitConfirmationWithPendingChanges_positive_button
                : R.string.splitConfirmation_positive_button,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getListener().onSplitContactConfirmed(mHasPendingChanges);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                onCancel(dialog);
            }
        });
        builder.setCancelable(false);
        return builder.create();
    }

    private Listener getListener() {
        return getTargetFragment() == null
                ? (Listener) getActivity()
                : (Listener) getTargetFragment();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        getListener().onSplitContactCanceled();
    }
}
