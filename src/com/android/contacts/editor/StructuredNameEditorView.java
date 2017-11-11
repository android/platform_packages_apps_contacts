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

package com.android.contacts.editor;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.Editable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.android.contacts.R;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.dataitem.DataItem;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.model.dataitem.StructuredNameDataItem;
import com.android.contacts.util.NameConverter;

/**
 * A dedicated editor for structured name.
 */
public class StructuredNameEditorView extends TextFieldsEditorView {

    private StructuredNameDataItem mSnapshot;
    private boolean mChanged;

    private TextFieldsEditorView mPhoneticView;

    public StructuredNameEditorView(Context context) {
        super(context);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StructuredNameEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        final Resources res = getResources();
        mCollapseButtonDescription = res
                .getString(R.string.collapse_name_fields_description);
        mExpandButtonDescription = res
                .getString(R.string.expand_name_fields_description);
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, RawContactDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        super.setValues(kind, entry, state, readOnly, vig);
        if (mSnapshot == null) {
            mSnapshot = (StructuredNameDataItem) DataItem.createFrom(
                    new ContentValues(getValues().getCompleteValues()));
            mChanged = entry.isInsert();
        } else {
            mChanged = false;
        }
        updateEmptiness();
        // Right alien with rest of the editors. As this view has an extra expand/collapse view on
        // the right, we need to free the space from deleteContainer
        mDeleteContainer.setVisibility(View.GONE);
    }

    @Override
    public void onFieldChanged(String column, String value) {
        if (!isFieldChanged(column, value)) {
            return;
        }

        // First save the new value for the column.
        saveValue(column, value);
        mChanged = true;

        // Then notify the listener.
        notifyEditorListener();
    }

    public void updatePhonetic(String column, String value) {
        EditText view = null;

        if (mPhoneticView != null) {
            ViewGroup fields = (ViewGroup) mPhoneticView.findViewById(R.id.editors);

            if (StructuredName.FAMILY_NAME.equals(column)) {
                view = (EditText) fields.getChildAt(0);
            } else if (StructuredName.GIVEN_NAME.equals(column)) {
                view = (EditText) fields.getChildAt(2);
            } else if (StructuredName.MIDDLE_NAME.equals(column)) {
                view = (EditText) fields.getChildAt(1);
            }

            if (view != null) {
                view.setText(value);
            }
        }
    }

    @Override
    public String getPhonetic(String column) {
        String input = "";
        EditText view = null;

        if (mPhoneticView != null) {
            ViewGroup fields = (ViewGroup) mPhoneticView.findViewById(R.id.editors);

            if (StructuredName.FAMILY_NAME.equals(column)) {
                view = (EditText) fields.getChildAt(0);
            } else if (StructuredName.GIVEN_NAME.equals(column)) {
                view = (EditText) fields.getChildAt(2);
            } else if (StructuredName.MIDDLE_NAME.equals(column)) {
                view = (EditText) fields.getChildAt(1);
            }

            if (view != null) {
                input = view.getText().toString();
            }
        }
        return input;
    }

    public void setPhoneticView(TextFieldsEditorView phoneticNameEditor) {
        mPhoneticView = phoneticNameEditor;
    }

    /**
     * Returns the display name currently displayed in the editor.
     */
    public String getDisplayName() {
        return NameConverter.structuredNameToDisplayName(getContext(),
                getValues().getCompleteValues());
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.mChanged = mChanged;
        state.mSnapshot = mSnapshot.getContentValues();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.mSuperState);

        mChanged = ss.mChanged;
        mSnapshot = (StructuredNameDataItem) DataItem.createFrom(ss.mSnapshot);
    }

    private static class SavedState implements Parcelable {
        public boolean mChanged;
        public ContentValues mSnapshot;
        public Parcelable mSuperState;

        SavedState(Parcelable superState) {
            mSuperState = superState;
        }

        private SavedState(Parcel in) {
            ClassLoader loader = getClass().getClassLoader();
            mSuperState = in.readParcelable(loader);

            mChanged = in.readInt() != 0;
            mSnapshot = in.readParcelable(loader);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            out.writeParcelable(mSuperState, 0);

            out.writeInt(mChanged ? 1 : 0);
            out.writeParcelable(mSnapshot, 0);
        }

        @SuppressWarnings({"unused"})
        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }
    }
}
