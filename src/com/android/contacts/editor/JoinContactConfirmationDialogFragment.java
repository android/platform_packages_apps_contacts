/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.contacts.R;

/**
 * Shows a dialog asking the user whether to apply pending changes before joining the contact.
 */
public class JoinContactConfirmationDialogFragment extends DialogFragment {

    private static final String ARG_JOIN_CONTACT_ID = "joinContactId";

    /**
     * Callbacks for the host of this dialog fragment.
     */
    public interface Listener {

        /**
         * Invoked after the user confirms they want to save pending changes before
         * joining the contact.
         *
         * @param joinContactId The raw contact ID of the contact to join to.
         */
        void onJoinContactConfirmed(long joinContactId);
    }

    /**
     * @param joinContactId The raw contact ID of the contact to join to after confirmation.
     */
    public static void show(ContactEditorFragment fragment, long joinContactId) {
        final Bundle args = new Bundle();
        args.putLong(ARG_JOIN_CONTACT_ID, joinContactId);

        final JoinContactConfirmationDialogFragment dialog = new
                JoinContactConfirmationDialogFragment();
        dialog.setTargetFragment(fragment, 0);
        dialog.setArguments(args);
        dialog.show(fragment.getFragmentManager(), "joinContactConfirmationDialog");
    }

    private long mContactId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContactId = getArguments().getLong(ARG_JOIN_CONTACT_ID);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.joinConfirmation);
        builder.setPositiveButton(R.string.joinConfirmation_positive_button,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final Listener targetListener = (Listener) getTargetFragment();
                        targetListener.onJoinContactConfirmed(mContactId);
                    }
                });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setCancelable(false);
        return builder.create();
    }
}
