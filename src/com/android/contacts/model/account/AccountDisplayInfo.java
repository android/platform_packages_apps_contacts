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

import android.content.Context;
import android.graphics.drawable.Drawable;
import androidx.annotation.StringRes;
import android.text.TextUtils;

/**
 * Wrapper around AccountWithDataSet that contains user-friendly labels and an icon.
 *
 * The raw values for name and type in AccountWithDataSet are not always (or even usually)
 * appropriate for direct display to the user.
 */
public class AccountDisplayInfo {
    private final AccountWithDataSet mSource;

    private final CharSequence mName;
    private final CharSequence mType;
    private final Drawable mIcon;

    private final boolean mIsDeviceAccount;

    public AccountDisplayInfo(AccountWithDataSet account, CharSequence name, CharSequence type,
            Drawable icon, boolean isDeviceAccount) {
        mSource = account;
        mName = name;
        mType = type;
        mIcon = icon;
        mIsDeviceAccount = isDeviceAccount;
    }

    public AccountWithDataSet getSource() {
        return mSource;
    }

    public CharSequence getNameLabel() {
        return mName;
    }

    public CharSequence getTypeLabel() {
        return mType;
    }

    public Drawable getIcon() {
        return mIcon;
    }

    public boolean hasGoogleAccountType() {
        return GoogleAccountType.ACCOUNT_TYPE.equals(mSource.type);
    }

    public boolean isGoogleAccount() {
        return GoogleAccountType.ACCOUNT_TYPE.equals(mSource.type) && mSource.dataSet == null;
    }

    public boolean isDeviceAccount() {
        return mIsDeviceAccount;
    }

    public boolean hasDistinctName() {
        return !TextUtils.equals(mName, mType);
    }

    public AccountDisplayInfo withName(CharSequence name) {
        return withNameAndType(name, mType);
    }

    public AccountDisplayInfo withType(CharSequence type) {
        return withNameAndType(mName, type);
    }

    public AccountDisplayInfo withNameAndType(CharSequence name, CharSequence type) {
        return new AccountDisplayInfo(mSource, name, type, mIcon, mIsDeviceAccount);
    }

    public AccountDisplayInfo formatted(Context context, @StringRes int nameFormat,
            @StringRes int typeFormat) {
        return new AccountDisplayInfo(mSource, context.getString(nameFormat, mName),
                context.getString(typeFormat, mType), mIcon, mIsDeviceAccount);
    }

    public AccountDisplayInfo withFormattedName(Context context, @StringRes int nameFormat) {
        return withName(context.getString(nameFormat, mName));
    }
}
