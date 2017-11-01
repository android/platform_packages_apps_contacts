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
package com.android.contacts.model.account;

import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;

import com.android.contacts.R;
import com.android.contacts.model.dataitem.DataKind;

import com.google.common.collect.Lists;

import java.util.Collections;

/**
 * Account type for SIM card contacts
 */
public class SimAccountType extends BaseAccountType {

    public SimAccountType(Context context) {
        this.titleRes = R.string.account_sim;
        this.iconRes = R.drawable.quantum_ic_sim_card_vd_theme_24;

        try {
            addDataKindStructuredName(context);
            addDataKindName(context);
            final DataKind phoneKind = addDataKindPhone(context);
            phoneKind.typeOverallMax = 1;
            // SIM card contacts don't necessarily support separate types (based on data exposed
            // in Samsung and LG Contacts Apps.
            phoneKind.typeList = Collections.emptyList();

            mIsInitialized = true;
        } catch (DefinitionException e) {
            // Just fail fast. Because we're explicitly adding the fields in this class this
            // exception should only happen in case of a bug.
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }

    @Override
    public boolean isGroupMembershipEditable() {
        return false;
    }

    @Override
    public void initializeFieldsFromAuthenticator(AuthenticatorDescription authenticator) {
        // Do nothing. We want to use our local icon and title
    }

    @Override
    protected DataKind addDataKindStructuredName(Context context) throws DefinitionException {
        final DataKind kind = addKind(new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                R.string.nameLabelsGroup, Weight.NONE, true));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);
        kind.typeOverallMax = 1;


        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                FLAGS_PERSON_NAME));
        kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                FLAGS_PERSON_NAME));

        return kind;
    }

    @Override
    protected DataKind addDataKindName(Context context) throws DefinitionException {
        final DataKind kind = addKind(new DataKind(DataKind.PSEUDO_MIME_TYPE_NAME,
                R.string.nameLabelsGroup, Weight.NONE, true));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);
        kind.typeOverallMax = 1;

        final boolean displayOrderPrimary =
                context.getResources().getBoolean(R.bool.config_editor_field_order_primary);

        kind.fieldList = Lists.newArrayList();
        if (!displayOrderPrimary) {
            kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                    FLAGS_PERSON_NAME));
            kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                    FLAGS_PERSON_NAME));
        } else {
            kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                    FLAGS_PERSON_NAME));
            kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                    FLAGS_PERSON_NAME));
        }

        return kind;
    }

    @Override
    public AccountInfo wrapAccount(Context context, AccountWithDataSet account) {
        // Use the "SIM" type label for the name as well because on OEM phones the "name" is
        // not always user-friendly
        return new AccountInfo(
                new AccountDisplayInfo(account, getDisplayLabel(context), getDisplayLabel(context),
                        getDisplayIcon(context), true), this);
    }
}
