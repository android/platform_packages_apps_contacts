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

import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;

import com.android.contacts.R;

/**
 * Asks the user whether to cancel editing the contact.
 */
public class CancelEditDialogFragment extends DialogFragment {

    private static final String TAG = "cancelEditor";

    /**
     * Shows a {@link CancelEditDialogFragment} after setting the given Fragment as the
     * target of the dialog.
     */
    public static void show(ContactEditorFragment fragment) {
        final CancelEditDialogFragment dialog = new CancelEditDialogFragment();
        dialog.setTargetFragment(fragment, 0);
        dialog.show(fragment.getFragmentManager(), TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getActivity())
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setMessage(R.string.cancel_confirmation_dialog_message)
                .setPositiveButton(R.string.cancel_confirmation_dialog_cancel_editing_button,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int which) {
                                final Listener targetListener = (Listener) getTargetFragment();
                                targetListener.onCancelEditConfirmed();
                            }
                        }
                )
                .setNegativeButton(R.string.cancel_confirmation_dialog_keep_editing_button, null)
                .create();
    }

    /**
     * Callbacks for {@link CancelEditDialogFragment} hosts.
     */
    public interface Listener {

        /**
         * Invoked when the user confirms that they want to cancel editing the contact.
         */
        void onCancelEditConfirmed();
    }
}
