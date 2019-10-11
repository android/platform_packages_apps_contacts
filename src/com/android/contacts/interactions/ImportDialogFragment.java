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
import android.os.Bundle;
import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.SimImportActivity;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.compat.PhoneNumberUtilsCompat;
import com.android.contacts.database.SimContactDao;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.AccountSelectionUtil;
import com.google.common.util.concurrent.Futures;

import java.util.List;
import java.util.concurrent.Future;

/**
 * An dialog invoked to import/export contacts.
 */
public class ImportDialogFragment extends DialogFragment {
    public static final String TAG = "ImportDialogFragment";

    public static final String KEY_RES_ID = "resourceId";
    public static final String KEY_SUBSCRIPTION_ID = "subscriptionId";

    public static final String EXTRA_SIM_ONLY = "extraSimOnly";

    public static final String EXTRA_SIM_CONTACT_COUNT_PREFIX = "simContactCount_";

    private boolean mSimOnly = false;
    private SimContactDao mSimDao;

    private Future<List<AccountInfo>> mAccountsFuture;

    private static BidiFormatter sBidiFormatter = BidiFormatter.getInstance();

    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager) {
        final ImportDialogFragment fragment = new ImportDialogFragment();
        fragment.show(fragmentManager, TAG);
    }

    public static void show(FragmentManager fragmentManager, List<SimCard> sims,
            boolean includeVcf) {
        final ImportDialogFragment fragment = new ImportDialogFragment();
        final Bundle args = new Bundle();
        args.putBoolean(EXTRA_SIM_ONLY, !includeVcf);
        for (SimCard sim : sims) {
            final List<SimContact> contacts = sim.getContacts();
            if (contacts == null) {
                continue;
            }
            args.putInt(EXTRA_SIM_CONTACT_COUNT_PREFIX + sim.getSimId(), contacts.size());
        }

        fragment.setArguments(args);
        fragment.show(fragmentManager, TAG);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setStyle(STYLE_NORMAL, R.style.ContactsAlertDialogTheme);

        final Bundle args = getArguments();
        mSimOnly = args != null && args.getBoolean(EXTRA_SIM_ONLY, false);
        mSimDao = SimContactDao.create(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();

        // Start loading the accounts. This is done in onResume in case they were refreshed.
        mAccountsFuture = AccountTypeManager.getInstance(getActivity()).filterAccountsAsync(
                AccountTypeManager.writableFilter());
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
        final LayoutInflater dialogInflater = (LayoutInflater)
                getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<AdapterEntry> adapter = new ArrayAdapter<AdapterEntry>(getActivity(),
                R.layout.select_dialog_item) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final View result = convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false);
                final TextView primaryText = (TextView) result.findViewById(R.id.primary_text);
                final TextView secondaryText = (TextView) result.findViewById(R.id.secondary_text);
                final AdapterEntry entry = getItem(position);
                secondaryText.setVisibility(View.GONE);
                if (entry.mChoiceResourceId == R.string.import_from_sim) {
                    final CharSequence secondary = getSimSecondaryText(entry.mSim);
                    if (TextUtils.isEmpty(secondary)) {
                        secondaryText.setVisibility(View.GONE);
                    } else {
                        secondaryText.setText(secondary);
                        secondaryText.setVisibility(View.VISIBLE);
                    }
                }
                primaryText.setText(entry.mLabel);
                return result;
            }

            CharSequence getSimSecondaryText(SimCard sim) {
                int count = getSimContactCount(sim);

                CharSequence phone = sim.getFormattedPhone();
                if (phone == null) {
                    phone = sim.getPhone();
                }
                if (phone != null) {
                    phone = sBidiFormatter.unicodeWrap(
                            PhoneNumberUtilsCompat.createTtsSpannable(phone),
                            TextDirectionHeuristicsCompat.LTR);
                }

                if (count != -1 && phone != null) {
                    // We use a template instead of format string so that the TTS span is preserved
                    final CharSequence template = getResources()
                            .getQuantityString(R.plurals.import_from_sim_secondary_template, count);
                    return TextUtils.expandTemplate(template, String.valueOf(count), phone);
                } else if (phone != null) {
                    return phone;
                } else if (count != -1) {
                    // count != -1
                    return getResources()
                            .getQuantityString(
                                    R.plurals.import_from_sim_secondary_contact_count_fmt, count,
                                    count);
                } else {
                    return null;
                }
            }
        };

        addItems(adapter);

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final int resId = adapter.getItem(which).mChoiceResourceId;
                if (resId == R.string.import_from_sim) {
                    handleSimImportRequest(adapter.getItem(which).mSim);
                } else if (resId == R.string.import_from_vcf_file) {
                    handleImportRequest(resId, SimCard.NO_SUBSCRIPTION_ID);
                } else {
                    Log.e(TAG, "Unexpected resource: "
                            + getActivity().getResources().getResourceEntryName(resId));
                }
                dialog.dismiss();
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), getTheme())
                .setTitle(R.string.dialog_import)
                .setNegativeButton(android.R.string.cancel, null);
        if (adapter.isEmpty()) {
            // Handle edge case; e.g. SIM card was removed.
            builder.setMessage(R.string.nothing_to_import_message);
        } else {
            builder.setSingleChoiceItems(adapter, -1, clickListener);
        }

        return builder.create();
    }

    private int getSimContactCount(SimCard sim) {
        if (sim.getContacts() != null) {
            return sim.getContacts().size();
        }
        final Bundle args = getArguments();
        if (args == null) {
            return -1;
        }
        return args.getInt(EXTRA_SIM_CONTACT_COUNT_PREFIX + sim.getSimId(), -1);
    }

    private void addItems(ArrayAdapter<AdapterEntry> adapter) {
        final Resources res = getActivity().getResources();
        if (res.getBoolean(R.bool.config_allow_import_from_vcf_file) && !mSimOnly) {
            adapter.add(new AdapterEntry(getString(R.string.import_from_vcf_file),
                    R.string.import_from_vcf_file));
        }
        final List<SimCard> sims = mSimDao.getSimCards();

        if (sims.size() == 1) {
            adapter.add(new AdapterEntry(getString(R.string.import_from_sim),
                    R.string.import_from_sim, sims.get(0)));
            return;
        }
        for (int i = 0; i < sims.size(); i++) {
            final SimCard sim = sims.get(i);
            adapter.add(new AdapterEntry(getSimDescription(sim, i), R.string.import_from_sim, sim));
        }
    }

    private void handleSimImportRequest(SimCard sim) {
        startActivity(new Intent(getActivity(), SimImportActivity.class)
                .putExtra(SimImportActivity.EXTRA_SUBSCRIPTION_ID, sim.getSubscriptionId()));
    }

    /**
     * Handle "import from SD".
     */
    private void handleImportRequest(int resId, int subscriptionId) {
        // Get the accounts. Because this only happens after a user action this should pretty
        // much never block since it will usually be at least several seconds before the user
        // interacts with the view
        final List<AccountWithDataSet> accountList = AccountInfo.extractAccounts(
                Futures.getUnchecked(mAccountsFuture));

        // There are three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final int size = accountList.size();
        if (size > 1) {
            // Send over to the account selector
            final Bundle args = new Bundle();
            args.putInt(KEY_RES_ID, resId);
            args.putInt(KEY_SUBSCRIPTION_ID, subscriptionId);
            SelectAccountDialogFragment.show(
                    getFragmentManager(), R.string.dialog_new_contact_account,
                    AccountTypeManager.AccountFilter.CONTACTS_WRITABLE, args);
        } else {
            AccountSelectionUtil.doImport(getActivity(), resId,
                    (size == 1 ? accountList.get(0) : null),
                    (CompatUtils.isMSIMCompatible() ? subscriptionId : -1));
        }
    }

    private CharSequence getSimDescription(SimCard sim, int index) {
        final CharSequence name = sim.getDisplayName();
        if (name != null) {
            return getString(R.string.import_from_sim_summary_fmt, name);
        } else {
            return getString(R.string.import_from_sim_summary_fmt, String.valueOf(index));
        }
    }

    private static class AdapterEntry {
        public final CharSequence mLabel;
        public final int mChoiceResourceId;
        public final SimCard mSim;

        public AdapterEntry(CharSequence label, int resId, SimCard sim) {
            mLabel = label;
            mChoiceResourceId = resId;
            mSim = sim;
        }

        public AdapterEntry(String label, int resId) {
            // Store a nonsense value for mSubscriptionId. If this constructor is used,
            // the mSubscriptionId value should not be read later.
            this(label, resId, /* sim= */ null);
        }
    }
}
