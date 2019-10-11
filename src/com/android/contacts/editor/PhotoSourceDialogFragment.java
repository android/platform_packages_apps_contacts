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

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.editor.PhotoActionPopup.ChoiceListItem;

import java.util.ArrayList;

/**
 * Displays the options for changing the contact photo.
 */
public class PhotoSourceDialogFragment extends DialogFragment {

    private static final String ARG_PHOTO_MODE = "photoMode";

    /**
     * Callbacks for the host of the {@link PhotoSourceDialogFragment}.
     */
    public interface Listener {
        void onRemovePictureChosen();
        void onTakePhotoChosen();
        void onPickFromGalleryChosen();
    }

    public static void show(Activity activity, int photoMode) {
        if (!(activity instanceof Listener)) {
            throw new IllegalArgumentException(
                    "Activity must implement " + Listener.class.getName());
        }
        final Bundle args = new Bundle();
        args.putInt(ARG_PHOTO_MODE, photoMode);

        PhotoSourceDialogFragment dialog = new PhotoSourceDialogFragment();
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), "photoSource");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Get the available options for changing the photo
        final int photoMode = getArguments().getInt(ARG_PHOTO_MODE);
        final ArrayList<ChoiceListItem> choices =
                PhotoActionPopup.getChoices(getActivity(), photoMode);

        // Prepare the AlertDialog items and click listener
        final CharSequence[] items = new CharSequence[choices.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = choices.get(i).toString();
        }
        final OnClickListener clickListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                final Listener listener = (Listener) getActivity();
                final ChoiceListItem choice = choices.get(which);
                switch (choice.getId()) {
                    case ChoiceListItem.ID_REMOVE:
                        listener.onRemovePictureChosen();
                        break;
                    case ChoiceListItem.ID_TAKE_PHOTO:
                        listener.onTakePhotoChosen();
                        break;
                    case ChoiceListItem.ID_PICK_PHOTO:
                        listener.onPickFromGalleryChosen();
                        break;
                }
                dismiss();
            }
        };

        // Build the AlertDialog
        final TextView title = (TextView) View.inflate(getActivity(), R.layout.dialog_title, null);
        title.setText(R.string.menu_change_photo);
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setCustomTitle(title);
        builder.setItems(items, clickListener);
        builder.setNegativeButton(android.R.string.cancel, /* listener =*/ null);
        return builder.create();
    }
}
