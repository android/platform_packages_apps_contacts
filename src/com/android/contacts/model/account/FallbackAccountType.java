/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.android.contacts.R;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contactsbind.FeedbackHelper;

public class FallbackAccountType extends BaseAccountType {
    private static final String TAG = "FallbackAccountType";

    private FallbackAccountType(Context context, String resPackageName) {
        this.accountType = null;
        this.dataSet = null;
        this.titleRes = R.string.account_phone;
        this.iconRes = R.drawable.quantum_ic_smartphone_vd_theme_24;

        // Note those are only set for unit tests.
        this.resourcePackageName = resPackageName;
        this.syncAdapterPackageName = resPackageName;

        try {
            addDataKindStructuredName(context);
            addDataKindName(context);
            addDataKindPhoneticName(context);
            addDataKindNickname(context);
            addDataKindPhone(context);
            addDataKindEmail(context);
            addDataKindStructuredPostal(context);
            addDataKindIm(context);
            addDataKindOrganization(context);
            addDataKindPhoto(context);
            addDataKindNote(context);
            addDataKindWebsite(context);
            addDataKindSipAddress(context);
            addDataKindGroupMembership(context);

            mIsInitialized = true;
        } catch (DefinitionException e) {
            FeedbackHelper.sendFeedback(context, TAG, "Failed to build fallback account type", e);
        }
    }

    @Override
    public Drawable getDisplayIcon(Context context) {
        final Drawable icon = ResourcesCompat.getDrawable(context.getResources(), iconRes, null);
        icon.mutate().setColorFilter(ContextCompat.getColor(context,
                R.color.actionbar_icon_color_grey), PorterDuff.Mode.SRC_ATOP);
        return icon;
    }

    public FallbackAccountType(Context context) {
        this(context, null);
    }

    /**
     * Used to compare with an {@link ExternalAccountType} built from a test contacts.xml.
     * In order to build {@link DataKind}s with the same resource package name,
     * {@code resPackageName} is injectable.
     */
    static AccountType createWithPackageNameForTest(Context context, String resPackageName) {
        return new FallbackAccountType(context, resPackageName);
    }

    @Override
    public void initializeFieldsFromAuthenticator(AuthenticatorDescription authenticator) {
        // Do nothing. For "Device" accounts we want to just display them using our own strings
        // and icons.
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }


    /**
     * {@inheritDoc}
     *
     * <p>This is overriden because the base class validates that the account.type matches
     * {@link #accountType} but for the fallback case we want to be more permissive</p>
     */
    @Override
    public AccountInfo wrapAccount(Context context, AccountWithDataSet account) {
        return new AccountInfo(
                new AccountDisplayInfo(account, account.name,
                        getDisplayLabel(context), getDisplayIcon(context), false), this);
    }
}
