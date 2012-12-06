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

import com.android.contacts.R;

import com.android.contacts.ContactSaveService;

import android.app.Dialog;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.DialogInterface.OnClickListener;

import android.os.Bundle;
import android.os.SystemProperties;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.BrcmIccUtils;
import com.android.contacts.common.SimContactsReadyHelper;
import com.android.internal.telephony.RILConstants.SimCardID;
import com.android.contacts.common.model.account.LocalAccountType;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.AccountTypeManager;

import java.util.ArrayList;
import java.util.List;


/**
 * An dialog invoked to import/export contacts.
 */
public class AllContactsDeleteDialogFragment  extends DialogFragment {
    public static final String TAG = "AllContactsDeleteDialogFragment ";
    private Context mContext;
    private static FragmentManager mFragmentManager;
    /** Preferred way to show this dialog */
    public static void show(FragmentManager fragmentManager) {
        final AllContactsDeleteDialogFragment  fragment = new AllContactsDeleteDialogFragment ();
        fragment.show(fragmentManager, AllContactsDeleteDialogFragment .TAG);
        mFragmentManager=fragmentManager;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Wrap our context to inflate list items using the correct theme
        final Resources res = getActivity().getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)getActivity()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(getActivity(),
                R.layout.select_dialog_item) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TextView result = (TextView)(convertView != null ? convertView :
                        dialogInflater.inflate(R.layout.select_dialog_item, parent, false));

                final int resId = getItem(position);
                result.setText(resId);
                return result;
            }
        };

        // BRCM DualSim and local/sim phonebook support start
        final SimContactsReadyHelper simReadyHelper = new SimContactsReadyHelper(null, false);

        /* dual sim */
        if (TelephonyManager.getDefault(SimCardID.ID_ZERO).hasIccCard()
                && res.getBoolean(R.bool.config_allow_sim_deleteAll) && simReadyHelper.getSimContactsLoaded(SimCardID.ID_ZERO.toInt())) {
            if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
                adapter.add(R.string.delete_sim1_all_contacts);
            }else {
                adapter.add(R.string.delete_sim_all_contacts);
            }
        }

        if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
            if (TelephonyManager.getDefault(SimCardID.ID_ONE).hasIccCard()
                    && res.getBoolean(R.bool.config_allow_sim_deleteAll) && simReadyHelper.getSimContactsLoaded(SimCardID.ID_ONE.toInt())) {
                adapter.add(R.string.delete_sim2_all_contacts);
            }
        }

        adapter.add(R.string.delete_local_all_contacts);

        String localAccountType = null;
        String localAccountName = null;
        AccountTypeManager atm = AccountTypeManager.getInstance(mContext);
        AccountType type = atm.getAccountType(LocalAccountType.ACCOUNT_TYPE, null);
        if ((null!=type) && (type instanceof LocalAccountType)) {
            localAccountType = type.accountType;
            localAccountName = LocalAccountType.LOCAL_CONTACTS_ACCOUNT_NAME;
        } else {
            localAccountType = null;
            localAccountName = null;
        }

        final String accountType=localAccountType;
        final String accountName=localAccountName;

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final int resId = adapter.getItem(which);
                switch (resId) {
                    case R.string.delete_sim_all_contacts:
                    case R.string.delete_sim1_all_contacts:
                            ConfirmDeleteAllContactsFragment.show(mContext,mFragmentManager,BrcmIccUtils.ACCOUNT_TYPE_SIM,BrcmIccUtils.ACCOUNT_NAME_SIM1);
                        break;
                    case R.string.delete_sim2_all_contacts:
                            ConfirmDeleteAllContactsFragment.show(mContext,mFragmentManager,BrcmIccUtils.ACCOUNT_TYPE_SIM,BrcmIccUtils.ACCOUNT_NAME_SIM2);
                        break;
                    case R.string.delete_local_all_contacts: {
                           ConfirmDeleteAllContactsFragment.show(mContext,mFragmentManager,accountType,accountName);
                        break;
                    }
                    default: {
                        Log.e(TAG, "Unexpected resource: "
                                + getActivity().getResources().getResourceEntryName(resId));
                    }
                }
                dialog.dismiss();
            }
        };
        return new AlertDialog.Builder(getActivity())
                .setTitle(R.string.menu_delete_all_contacts)
                .setSingleChoiceItems(adapter, -1, clickListener)
                .create();
    }
}
