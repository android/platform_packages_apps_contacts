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

package com.android.contacts.list;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.contacts.R;

/**
 * Confirmation dialog for turning global auto-sync setting on.
 */
public class EnableGlobalSyncDialogFragment extends DialogFragment{

    private static final String ARG_FILTER = "filter";
    private ContactListFilter mFilter;

    /**
     * Callbacks for the dialog host.
     */
    public interface Listener {

        /**
         * Invoked after the user has confirmed that they want to turn on sync.
         *
         * @param filter the filter of current contacts list.
         */
        void onEnableAutoSync(ContactListFilter filter);
    }

    public static void show(DefaultContactBrowseListFragment fragment, ContactListFilter filter) {
        final Bundle args = new Bundle();
        args.putParcelable(ARG_FILTER, filter);

        final EnableGlobalSyncDialogFragment dialog = new
                EnableGlobalSyncDialogFragment();
        dialog.setTargetFragment(fragment, 0);
        dialog.setArguments(args);
        dialog.show(fragment.getFragmentManager(), "globalSync");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFilter = getArguments().getParcelable(ARG_FILTER);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Listener targetListener = (Listener) getTargetFragment();
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.turn_auto_sync_on_dialog_title)
                .setMessage(R.string.turn_auto_sync_on_dialog_body)
                .setPositiveButton(R.string.turn_auto_sync_on_dialog_confirm_btn,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                    if (targetListener != null) {
                                        targetListener.onEnableAutoSync(mFilter);
                                    }
                                }
                            });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setCancelable(false);
        return builder.create();
    }
}
