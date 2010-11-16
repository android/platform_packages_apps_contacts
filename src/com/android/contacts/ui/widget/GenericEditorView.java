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

package com.android.contacts.ui.widget;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.Editor;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.model.ContactsSource.EditField;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.ViewIdGenerator;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.PhoneNumberFormattingTextWatcher;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Simple editor that handles labels and any {@link EditField} defined for
 * the entry. Uses {@link ValuesDelta} to read any existing
 * {@link Entity} values, and to correctly write any changes values.
 */
public class GenericEditorView extends RelativeLayout implements Editor, View.OnClickListener {
    protected static final int RES_FIELD = R.layout.item_editor_field;
    protected static final int RES_LABEL_ITEM = android.R.layout.simple_list_item_1;

    protected LayoutInflater mInflater;

    protected static final int INPUT_TYPE_CUSTOM = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;

    protected TextView mLabel;
    protected ViewGroup mFields;
    protected View mDelete;
    protected View mMore;
    protected View mLess;

    protected DataKind mKind;
    protected ValuesDelta mEntry;
    protected EntityDelta mState;
    protected boolean mReadOnly;

    protected boolean mHideOptional = true;

    protected EditType mType;
    // Used only when a user tries to use custom label.
    private EditType mPendingType;

    private ViewIdGenerator mViewIdGenerator;

    public GenericEditorView(Context context) {
        super(context);
    }

    public GenericEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        mInflater = (LayoutInflater)getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mLabel = (TextView)findViewById(R.id.edit_label);
        mLabel.setOnClickListener(this);

        mFields = (ViewGroup)findViewById(R.id.edit_fields);

        mDelete = findViewById(R.id.edit_delete);
        mDelete.setOnClickListener(this);

        mMore = findViewById(R.id.edit_more);
        mMore.setOnClickListener(this);

        mLess = findViewById(R.id.edit_less);
        mLess.setOnClickListener(this);
    }

    protected EditorListener mListener;

    public void setEditorListener(EditorListener listener) {
        mListener = listener;
    }

    public void setDeletable(boolean deletable) {
        mDelete.setVisibility(deletable ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    public void setEnabled(boolean enabled) {
        mLabel.setEnabled(enabled);
        final int count = mFields.getChildCount();
        for (int pos = 0; pos < count; pos++) {
            final View v = mFields.getChildAt(pos);
            v.setEnabled(enabled);
        }
        mMore.setEnabled(enabled);
        mLess.setEnabled(enabled);
    }

    /**
     * Build the current label state based on selected {@link EditType} and
     * possible custom label string.
     */
    private void rebuildLabel() {
        // Handle undetected types
        if (mType == null) {
            mLabel.setText(R.string.unknown);
            return;
        }

        if (mType.customColumn != null) {
            // Use custom label string when present
            final String customText = mEntry.getAsString(mType.customColumn);
            if (customText != null) {
                mLabel.setText(customText);
                return;
            }
        }

        // Otherwise fall back to using default label
        mLabel.setText(mType.labelRes);
    }

    /** {@inheritDoc} */
    public void onFieldChanged(String column, String value) {
        // Field changes are saved directly
        mEntry.put(column, value);
        if (mListener != null) {
            mListener.onRequest(EditorListener.FIELD_CHANGED);
        }
    }

    public boolean isAnyFieldFilledOut() {
        int childCount = mFields.getChildCount();
        for (int i = 0; i < childCount; i++) {
            EditText editorView = (EditText) mFields.getChildAt(i);
            if (!TextUtils.isEmpty(editorView.getText())) {
                return true;
            }
        }
        return false;
    }

    private void rebuildValues() {
        setValues(mKind, mEntry, mState, mReadOnly, mViewIdGenerator);
    }

    /**
     * Prepare this editor using the given {@link DataKind} for defining
     * structure and {@link ValuesDelta} describing the content to edit.
     */
    public void setValues(DataKind kind, ValuesDelta entry, EntityDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        mKind = kind;
        mEntry = entry;
        mState = state;
        mReadOnly = readOnly;
        mViewIdGenerator = vig;
        setId(vig.getId(state, kind, entry, ViewIdGenerator.NO_VIEW_INDEX));

        final boolean enabled = !readOnly;

        if (!entry.isVisible()) {
            // Hide ourselves entirely if deleted
            setVisibility(View.GONE);
            return;
        } else {
            setVisibility(View.VISIBLE);
        }

        // Display label selector if multiple types available
        final boolean hasTypes = EntityModifier.hasEditTypes(kind);
        mLabel.setVisibility(hasTypes ? View.VISIBLE : View.GONE);
        mLabel.setEnabled(enabled);
        if (hasTypes) {
            mType = EntityModifier.getCurrentType(entry, kind);
            rebuildLabel();
        }

        // Build out set of fields
        mFields.removeAllViews();
        boolean hidePossible = false;
        int n = 0;
        for (EditField field : kind.fieldList) {
            // Inflate field from definition
            EditText fieldView = (EditText)mInflater.inflate(RES_FIELD, mFields, false);
            fieldView.setId(vig.getId(state, kind, entry, n++));
            if (field.titleRes > 0) {
                fieldView.setHint(field.titleRes);
            }
            int inputType = field.inputType;
            fieldView.setInputType(inputType);
            if (inputType == InputType.TYPE_CLASS_PHONE) {
                fieldView.addTextChangedListener(new PhoneNumberFormattingTextWatcher());
            }
            fieldView.setMinLines(field.minLines);

            // Read current value from state
            final String column = field.column;
            final String value = entry.getAsString(column);
            fieldView.setText(value);

            // Prepare listener for writing changes
            fieldView.addTextChangedListener(new TextWatcher() {
                public void afterTextChanged(Editable s) {
                    // Trigger event for newly changed value
                    onFieldChanged(column, s.toString());
                }

                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
            });

            // Hide field when empty and optional value
            final boolean couldHide = (!ContactsUtils.isGraphic(value) && field.optional);
            final boolean willHide = (mHideOptional && couldHide);
            fieldView.setVisibility(willHide ? View.GONE : View.VISIBLE);
            fieldView.setEnabled(enabled);
            hidePossible = hidePossible || couldHide;

            mFields.addView(fieldView);
        }

        // When hiding fields, place expandable
        if (hidePossible) {
            mMore.setVisibility(mHideOptional ? View.VISIBLE : View.GONE);
            mLess.setVisibility(mHideOptional ? View.GONE : View.VISIBLE);
        } else {
            mMore.setVisibility(View.GONE);
            mLess.setVisibility(View.GONE);
        }
        mMore.setEnabled(enabled);
        mLess.setEnabled(enabled);
    }

    /**
     * Prepare dialog for entering a custom label. The input value is trimmed: white spaces before
     * and after the input text is removed.
     * <p>
     * If the final value is empty, this change request is ignored;
     * no empty text is allowed in any custom label.
     */
    private Dialog createCustomDialog() {
        final EditText customType = new EditText(mContext);
        customType.setInputType(INPUT_TYPE_CUSTOM);
        customType.requestFocus();

        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.customLabelPickerTitle);
        builder.setView(customType);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                final String customText = customType.getText().toString().trim();
                if (ContactsUtils.isGraphic(customText)) {
                    // Now we're sure it's ok to actually change the type value.
                    mType = mPendingType;
                    mPendingType = null;
                    mEntry.put(mKind.typeColumn, mType.rawValue);
                    mEntry.put(mType.customColumn, customText);
                    rebuildLabel();
                    if (!mFields.hasFocus())
                        mFields.requestFocus();
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);

        return builder.create();
    }

    /**
     * Prepare dialog for picking a new {@link EditType} or entering a
     * custom label. This dialog is limited to the valid types as determined
     * by {@link EntityModifier}.
     */
    public Dialog createLabelDialog() {
        // Build list of valid types, including the current value
        final List<EditType> validTypes = EntityModifier.getValidTypes(mState, mKind, mType);

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(mContext,
                android.R.style.Theme_Light);
        final LayoutInflater dialogInflater = mInflater.cloneInContext(dialogContext);

        final ListAdapter typeAdapter = new ArrayAdapter<EditType>(mContext, RES_LABEL_ITEM,
                validTypes) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(RES_LABEL_ITEM, parent, false);
                }

                final EditType type = this.getItem(position);
                final TextView textView = (TextView)convertView;
                textView.setText(type.labelRes);
                return textView;
            }
        };

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                final EditType selected = validTypes.get(which);
                if (selected.customColumn != null) {
                    // Show custom label dialog if requested by type.
                    //
                    // Only when the custum value input in the next step is correct one.
                    // this method also set the type value to what the user requested here.
                    mPendingType = selected;
                    createCustomDialog().show();
                } else {
                    // User picked type, and we're sure it's ok to actually write the entry.
                    mType = selected;
                    mEntry.put(mKind.typeColumn, mType.rawValue);
                    rebuildLabel();
                    if (!mFields.hasFocus())
                        mFields.requestFocus();
                }
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(R.string.selectLabel);
        builder.setSingleChoiceItems(typeAdapter, 0, clickListener);
        return builder.create();
    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.edit_label: {
                createLabelDialog().show();
                break;
            }
            case R.id.edit_delete: {
                // Keep around in model, but mark as deleted
                mEntry.markDeleted();

                // Remove editor from parent view
                final ViewGroup parent = (ViewGroup)getParent();
                if (parent != null) {
                    parent.removeView(this);
                    if (mListener != null) {
                        // Notify listener when present
                        mListener.onDeleted(this);
                    }
                }
                break;
            }
            case R.id.edit_more:
            case R.id.edit_less: {
                mHideOptional = !mHideOptional;
                rebuildValues();
                break;
            }
        }
    }

    private static class SavedState extends BaseSavedState {
        public boolean mHideOptional;
        public int[] mVisibilities;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            mVisibilities = new int[in.readInt()];
            in.readIntArray(mVisibilities);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mVisibilities.length);
            out.writeIntArray(mVisibilities);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /**
     * Saves the visibility of the child EditTexts, and mHideOptional.
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);

        ss.mHideOptional = mHideOptional;

        final int numChildren = mFields.getChildCount();
        ss.mVisibilities = new int[numChildren];
        for (int i = 0; i < numChildren; i++) {
            ss.mVisibilities[i] = mFields.getChildAt(i).getVisibility();
        }

        return ss;
    }

    /**
     * Restores the visibility of the child EditTexts, and mHideOptional.
     */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());

        mHideOptional = ss.mHideOptional;

        int numChildren = Math.min(mFields.getChildCount(), ss.mVisibilities.length);
        for (int i = 0; i < numChildren; i++) {
            mFields.getChildAt(i).setVisibility(ss.mVisibilities[i]);
        }
    }
}
