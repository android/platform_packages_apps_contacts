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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.telephony.SubscriptionManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.vcard.ExportVCardActivity;
import com.android.contacts.vcard.ShareVCardActivity;
import com.android.contacts.vcard.VCardCommonArguments;

/**
 * An dialog invoked to import/export contacts.
 */
public class ExportDialogFragment extends DialogFragment {
    public static final String TAG = "ExportDialogFragment";

    public static final int EXPORT_MODE_FAVORITES = 0;
    public static final int EXPORT_MODE_ALL_CONTACTS = 1;
    public static final int EXPORT_MODE_DEFAULT = -1;

    private static int mExportMode = EXPORT_MODE_DEFAULT;

    private final String[] LOOKUP_PROJECTION = new String[] {
            Contacts.LOOKUP_KEY
    };

    private SubscriptionManager mSubscriptionManager;

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager, Class callingActivity,
            int exportMode) {
        final ExportDialogFragment fragment = new ExportDialogFragment();
        Bundle args = new Bundle();
        args.putString(VCardCommonArguments.ARG_CALLING_ACTIVITY, callingActivity.getName());
        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
        mExportMode = exportMode;
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        final Resources res = getActivity().getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final String callingActivity = getArguments().getString(
                VCardCommonArguments.ARG_CALLING_ACTIVITY);

        // Adapter that shows a list of string resources
        final ArrayAdapter<AdapterEntry> adapter = new ArrayAdapter<AdapterEntry>(getActivity(),
                R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final View result = convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false);

                final TextView text = (TextView) result.findViewById(R.id.primary_text);
                final View secondaryText = result.findViewById(R.id.secondary_text);
                secondaryText.setVisibility(View.GONE);

                text.setText(getItem(position).mLabel);
                return result;
            }
        };

        if (res.getBoolean(R.bool.config_allow_export)) {
                adapter.add(new AdapterEntry(getString(R.string.export_to_vcf_file),
                        R.string.export_to_vcf_file));
        }
        if (res.getBoolean(R.bool.config_allow_share_contacts)) {
            if (mExportMode == EXPORT_MODE_FAVORITES) {
                // share favorite and frequently contacted contacts from Favorites tab
                adapter.add(new AdapterEntry(getString(R.string.share_favorite_contacts),
                        R.string.share_contacts));
            } else {
                // share "all" contacts (in groups selected in "Customize") from All tab for now
                // TODO: change the string to share_visible_contacts if implemented
                adapter.add(new AdapterEntry(getString(R.string.share_contacts),
                        R.string.share_contacts));
            }
        }

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                boolean dismissDialog;
                final int resId = adapter.getItem(which).mChoiceResourceId;
                if (resId == R.string.export_to_vcf_file) {
                    dismissDialog = true;
                    final Intent exportIntent = new Intent(
                            getActivity(), ExportVCardActivity.class);
                    exportIntent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY,
                            callingActivity);
                    getActivity().startActivity(exportIntent);
                } else if (resId == R.string.share_contacts) {
                    dismissDialog = true;
                    if (mExportMode == EXPORT_MODE_FAVORITES) {
                        doShareFavoriteContacts();
                    } else { // EXPORT_MODE_ALL_CONTACTS
                        final Intent exportIntent = new Intent(
                                getActivity(), ShareVCardActivity.class);
                        exportIntent.putExtra(VCardCommonArguments.ARG_CALLING_ACTIVITY,
                                callingActivity);
                        getActivity().startActivity(exportIntent);
                    }
                } else {
                    dismissDialog = true;
                    Log.e(TAG, "Unexpected resource: "
                            + getActivity().getResources().getResourceEntryName(resId));
                }
                if (dismissDialog) {
                    dialog.dismiss();
                }
            }
        };
        final TextView title = (TextView) View.inflate(getActivity(), R.layout.dialog_title, null);
        title.setText(R.string.dialog_export);
        return new AlertDialog.Builder(getActivity())
                .setCustomTitle(title)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }

    private void doShareFavoriteContacts() {
        try{
            final Cursor cursor = getActivity().getContentResolver().query(
                    Contacts.CONTENT_STREQUENT_URI, LOOKUP_PROJECTION, null, null,
                    Contacts.DISPLAY_NAME + " COLLATE NOCASE ASC");
            if (cursor != null) {
                try {
                    if (!cursor.moveToFirst()) {
                        Toast.makeText(getActivity(), R.string.no_contact_to_share,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Build multi-vcard Uri for sharing
                    final StringBuilder uriListBuilder = new StringBuilder();
                    int index = 0;
                    do {
                        if (index != 0)
                            uriListBuilder.append(':');
                        uriListBuilder.append(cursor.getString(0));
                        index++;
                    } while (cursor.moveToNext());
                    final Uri uri = Uri.withAppendedPath(
                            Contacts.CONTENT_MULTI_VCARD_URI,
                            Uri.encode(uriListBuilder.toString()));

                    final Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType(Contacts.CONTENT_VCARD_TYPE);
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    ImplicitIntentsUtil.startActivityOutsideApp(getActivity(), intent);
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Sharing contacts failed", e);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getContext(), R.string.share_contacts_failure,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static class AdapterEntry {
        public final CharSequence mLabel;
        public final int mChoiceResourceId;
        public final int mSubscriptionId;

        public AdapterEntry(CharSequence label, int resId, int subId) {
            mLabel = label;
            mChoiceResourceId = resId;
            mSubscriptionId = subId;
        }

        public AdapterEntry(String label, int resId) {
            // Store a nonsense value for mSubscriptionId. If this constructor is used,
            // the mSubscriptionId value should not be read later.
            this(label, resId, /* subId = */ -1);
        }
    }
}
