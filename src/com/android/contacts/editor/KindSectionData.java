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
 * limitations under the License.
 */

package com.android.contacts.editor;

import android.text.TextUtils;

import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountType.EditField;
import com.android.contacts.model.dataitem.DataKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Holder for the multi account raw contact data needed to back an editor input field.
 */
public final class KindSectionData {

    private final AccountType mAccountType;
    private final DataKind mDataKind;
    private final RawContactDelta mRawContactDelta;

    public KindSectionData(AccountType accountType, DataKind dataKind,
            RawContactDelta rawContactDelta) {
        mAccountType = accountType;
        mDataKind = dataKind;
        mRawContactDelta = rawContactDelta;
    }

    public AccountType getAccountType() {
        return mAccountType;
    }

    /** Returns all ValuesDeltas for the data kind this section represents.*/
    public List<ValuesDelta> getValuesDeltas() {
        final List<ValuesDelta> valuesDeltas = mRawContactDelta.getMimeEntries(mDataKind.mimeType);
        return valuesDeltas == null ? new ArrayList<ValuesDelta>() : valuesDeltas;
    }

    /** Returns visible and non deleted ValuesDeltas for the data kind this section represents. */
    public List<ValuesDelta> getVisibleValuesDeltas() {
        final ArrayList<ValuesDelta> valuesDeltas = new ArrayList<> ();
        for (ValuesDelta valuesDelta : getValuesDeltas()) {
            // Same conditions as KindSectionView#rebuildFromState
            if (valuesDelta.isVisible() && !valuesDelta.isDelete()) {
                valuesDeltas.add(valuesDelta);
            }
        }
        return valuesDeltas;
    }

    /** Returns non-empty ValuesDeltas for the data kind this section represents. */
    public List<ValuesDelta> getNonEmptyValuesDeltas() {
        final ArrayList<ValuesDelta> valuesDeltas = new ArrayList<> ();
        for (ValuesDelta valuesDelta : getValuesDeltas()) {
            if (!isEmpty(valuesDelta)) {
                valuesDeltas.add(valuesDelta);
            }
        }
        return valuesDeltas;
    }

    /** Returns the super primary ValuesDelta for the data kind this section represents. */
    public ValuesDelta getSuperPrimaryValuesDelta() {
        for (ValuesDelta valuesDelta : getValuesDeltas()) {
            if (valuesDelta.isSuperPrimary()) return valuesDelta;
        }
        return null;
    }

    /** Returns the ValuesDelta with the given ID. */
    public ValuesDelta getValuesDeltaById(Long id) {
        for (ValuesDelta valuesDelta : getValuesDeltas()) {
            if (valuesDelta.getId().equals(id)) return valuesDelta;
        }
        return null;
    }

    /** Returns the first non empty ValuesDelta for the data kind this section represents. */
    public ValuesDelta getFirstNonEmptyValuesDelta() {
        for (ValuesDelta valuesDelta : getValuesDeltas()) {
            if (!isEmpty(valuesDelta)) return valuesDelta;
        }
        return null;
    }

    private boolean isEmpty(ValuesDelta valuesDelta) {
        if (mDataKind.fieldList != null) {
            for (EditField editField : mDataKind.fieldList) {
                final String column = editField.column;
                final String value = valuesDelta.getAsString(column);
                if (!TextUtils.isEmpty(value)) return false;
            }
        }
        return true;
    }

    public DataKind getDataKind() {
        return mDataKind;
    }

    public RawContactDelta getRawContactDelta() {
        return mRawContactDelta;
    }

    public String getMimeType() {
        return mDataKind.mimeType;
    }
}
