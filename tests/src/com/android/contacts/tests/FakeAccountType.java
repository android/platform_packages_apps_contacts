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
package com.android.contacts.tests;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;

public class FakeAccountType extends AccountType {
    public boolean areContactsWritable = false;
    public boolean isGroupMembershipEditable = false;
    public String displayLabel = "The Default Label";
    public Drawable displayIcon = new Drawable() {
        @Override
        public void draw(Canvas canvas) {
        }

        @Override
        public void setAlpha(int alpha) {
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }
    };

    public FakeAccountType() {
    }

    public FakeAccountType(String type) {
        accountType = type;
    }

    @Override
    public Drawable getDisplayIcon(Context context) {
        return displayIcon;
    }

    @Override
    public String getDisplayLabel(Context context) {
        return displayLabel;
    }

    @Override
    public boolean areContactsWritable() {
        return areContactsWritable;
    }

    @Override
    public boolean isGroupMembershipEditable() {
        return isGroupMembershipEditable;
    }

    public static FakeAccountType create(String accountType, String label) {
        final FakeAccountType result = new FakeAccountType();
        result.accountType = accountType;
        result.displayLabel = label;
        return result;
    }

    public static FakeAccountType create(String accountType, String label, Drawable icon) {
        final FakeAccountType result = new FakeAccountType();
        result.accountType = accountType;
        result.displayIcon = icon;
        result.displayLabel = label;
        return result;
    }

    public static AccountType create(AccountWithDataSet account, String label, Drawable icon) {
        final FakeAccountType result = create(account.type, label, icon);
        result.accountType = account.type;
        result.dataSet = account.dataSet;
        return result;
    }
}
