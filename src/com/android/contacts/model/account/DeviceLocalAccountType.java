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

public class DeviceLocalAccountType extends FallbackAccountType {

    private final boolean mGroupsEditable;

    public DeviceLocalAccountType(Context context, boolean groupsEditable) {
        super(context);
        mGroupsEditable = groupsEditable;
    }

    public DeviceLocalAccountType(Context context) {
        this(context, false);
    }

    @Override
    public boolean isGroupMembershipEditable() {
        return mGroupsEditable;
    }

    @Override
    public AccountInfo wrapAccount(Context context, AccountWithDataSet account) {
        // Use the "Device" type label for the name as well because on OEM phones the "name" is
        // not always user-friendly
        return new AccountInfo(
                new AccountDisplayInfo(account, getDisplayLabel(context), getDisplayLabel(context),
                        getDisplayIcon(context), true), this);
    }
}
