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

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.TtsSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.compat.PhoneNumberUtilsCompat;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountType.EditField;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.PhoneNumberFormatter;

/**
 * Simple editor that handles labels and any {@link EditField} defined for the
 * entry. Uses {@link ValuesDelta} to read any existing {@link RawContact} values,
 * and to correctly write any changes values.
 */
public class TextFieldsEditorView extends LabeledEditorView {
    private static final String TAG = TextFieldsEditorView.class.getSimpleName();

    private EditText[] mFieldEditTexts = null;
    private ViewGroup mFields = null;
    protected View mExpansionViewContainer;
    protected ImageView mExpansionView;
    protected String mCollapseButtonDescription;
    protected String mExpandButtonDescription;
    protected String mCollapsedAnnouncement;
    protected String mExpandedAnnouncement;
    private boolean mHideOptional = true;
    private boolean mHasShortAndLongForms;
    private int mMinFieldHeight;
    private int mPreviousViewHeight;
    private int mHintTextColorUnfocused;
    private String mFixedPhonetic = "";
    private String mFixedDisplayName = "";
    private boolean needInputInitialize;


    public TextFieldsEditorView(Context context) {
        super(context);
    }

    public TextFieldsEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TextFieldsEditorView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /** {@inheritDoc} */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mMinFieldHeight = getContext().getResources().getDimensionPixelSize(
                R.dimen.editor_min_line_item_height);
        mFields = (ViewGroup) findViewById(R.id.editors);
        mHintTextColorUnfocused = getResources().getColor(R.color.editor_disabled_text_color);
        mExpansionView = (ImageView) findViewById(R.id.expansion_view);
        mCollapseButtonDescription = getResources()
                .getString(R.string.collapse_fields_description);
        mCollapsedAnnouncement = getResources()
                .getString(R.string.announce_collapsed_fields);
        mExpandButtonDescription = getResources()
                .getString(R.string.expand_fields_description);
        mExpandedAnnouncement = getResources()
                .getString(R.string.announce_expanded_fields);

        mExpansionViewContainer = findViewById(R.id.expansion_view_container);
        if (mExpansionViewContainer != null) {
            mExpansionViewContainer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPreviousViewHeight = mFields.getHeight();

                    // Save focus
                    final View focusedChild = findFocus();
                    final int focusedViewId = focusedChild == null ? -1 : focusedChild.getId();

                    // Reconfigure GUI
                    mHideOptional = !mHideOptional;
                    onOptionalFieldVisibilityChange();
                    rebuildValues();

                    // Restore focus
                    View newFocusView = findViewById(focusedViewId);
                    if (newFocusView == null || newFocusView.getVisibility() == GONE) {
                        // find first visible child
                        newFocusView = TextFieldsEditorView.this;
                    }
                    newFocusView.requestFocus();

                    EditorAnimator.getInstance().slideAndFadeIn(mFields, mPreviousViewHeight);
                    announceForAccessibility(mHideOptional ?
                            mCollapsedAnnouncement : mExpandedAnnouncement);
                }
            });
        }
    }

    @Override
    public void editNewlyAddedField() {
        // Some editors may have multiple fields (eg: first-name/last-name), but since the user
        // has not selected a particular one, it is reasonable to simply pick the first.
        final View editor = mFields.getChildAt(0);

        // Show the soft-keyboard.
        InputMethodManager imm =
                (InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            if (!imm.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)) {
                Log.w(TAG, "Failed to show soft input method.");
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        if (mFieldEditTexts != null) {
            for (int index = 0; index < mFieldEditTexts.length; index++) {
                mFieldEditTexts[index].setEnabled(!isReadOnly() && enabled);
            }
        }
        if (mExpansionView != null) {
            mExpansionView.setEnabled(!isReadOnly() && enabled);
        }
    }

    private OnFocusChangeListener mTextFocusChangeListener = new OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (getEditorListener() != null) {
                getEditorListener().onRequest(EditorListener.EDITOR_FOCUS_CHANGED);
            }
            // Rebuild the label spinner using the new colors.
            rebuildLabel();

            if (hasFocus) {
                needInputInitialize = true;
            }
        }
    };

    /**
     * Creates or removes the type/label button. Doesn't do anything if already correctly configured
     */
    private void setupExpansionView(boolean shouldExist, boolean collapsed) {
        final Drawable expandIcon = getContext().getDrawable(collapsed
                ? R.drawable.quantum_ic_expand_more_vd_theme_24
                : R.drawable.quantum_ic_expand_less_vd_theme_24);
        mExpansionView.setImageDrawable(expandIcon);
        mExpansionView.setContentDescription(collapsed ? mExpandButtonDescription
                : mCollapseButtonDescription);
        mExpansionViewContainer.setVisibility(shouldExist ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void requestFocusForFirstEditField() {
        if (mFieldEditTexts != null && mFieldEditTexts.length != 0) {
            EditText firstField = null;
            boolean anyFieldHasFocus = false;
            for (EditText editText : mFieldEditTexts) {
                if (firstField == null && editText.getVisibility() == View.VISIBLE) {
                    firstField = editText;
                }
                if (editText.hasFocus()) {
                    anyFieldHasFocus = true;
                    break;
                }
            }
            if (!anyFieldHasFocus && firstField != null) {
                firstField.requestFocus();
            }
        }
    }

    public void setValue(int field, String value) {
        mFieldEditTexts[field].setText(value);
    }

    private boolean isUnFixed(Editable input) {
        boolean unfixed = false;
        Object[] spanned = input.getSpans(0, input.length(), Object.class);
        if (spanned != null) {
            for (Object obj : spanned) {
                if ((input.getSpanFlags(obj) & Spanned.SPAN_COMPOSING) == Spanned.SPAN_COMPOSING) {
                    unfixed = true;
                }
            }
        }
        return unfixed;
    }

    private String getNameField(String column) {

      EditText editText = null;

      if (StructuredName.FAMILY_NAME.equals(column)) {
          editText = (EditText) mFields.getChildAt(1);
      } else if (StructuredName.GIVEN_NAME.equals(column)) {
          editText = (EditText) mFields.getChildAt(3);
      } else if (StructuredName.MIDDLE_NAME.equals(column)) {
          editText = (EditText) mFields.getChildAt(2);
      }

      if (editText != null) {
          return editText.getText().toString();
      }

      return "";
    }

    @Override
    public void setValues(DataKind kind, ValuesDelta entry, RawContactDelta state, boolean readOnly,
            ViewIdGenerator vig) {
        super.setValues(kind, entry, state, readOnly, vig);
        // Remove edit texts that we currently have
        if (mFieldEditTexts != null) {
            for (EditText fieldEditText : mFieldEditTexts) {
                mFields.removeView(fieldEditText);
            }
        }
        boolean hidePossible = false;

        int fieldCount = kind.fieldList == null ? 0 : kind.fieldList.size();
        mFieldEditTexts = new EditText[fieldCount];
        for (int index = 0; index < fieldCount; index++) {
            final EditField field = kind.fieldList.get(index);
            final EditText fieldView = new EditText(getContext());
            fieldView.setLayoutParams(new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.WRAP_CONTENT));
            fieldView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    getResources().getDimension(R.dimen.editor_form_text_size));
            fieldView.setHintTextColor(mHintTextColorUnfocused);
            mFieldEditTexts[index] = fieldView;
            fieldView.setId(vig.getId(state, kind, entry, index));
            if (field.titleRes > 0) {
                fieldView.setHint(field.titleRes);
            }
            int inputType = field.inputType;
            fieldView.setInputType(inputType);
            if (inputType == InputType.TYPE_CLASS_PHONE) {
                PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(
                        getContext(), fieldView,
                        /* formatAfterWatcherSet =*/ state.isContactInsert());
                fieldView.setTextDirection(View.TEXT_DIRECTION_LTR);
            }
            fieldView.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

            // Set either a minimum line requirement or a minimum height (because {@link TextView}
            // only takes one or the other at a single time).
            if (field.minLines > 1) {
                fieldView.setMinLines(field.minLines);
            } else {
                // This needs to be called after setInputType. Otherwise, calling setInputType
                // will unset this value.
                fieldView.setMinHeight(mMinFieldHeight);
            }

            // Show the "next" button in IME to navigate between text fields
            // TODO: Still need to properly navigate to/from sections without text fields,
            // See Bug: 5713510
            fieldView.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_FULLSCREEN);

            // Read current value from state
            final String column = field.column;
            final String value = entry.getAsString(column);
            if (ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(kind.mimeType)) {
                fieldView.setText(PhoneNumberUtilsCompat.createTtsSpannable(value));
            } else {
                fieldView.setText(value);
            }

            // Show the delete button if we have a non-empty value
            setDeleteButtonVisible(!TextUtils.isEmpty(value));

            // Prepare listener for writing changes
            fieldView.addTextChangedListener(new TextWatcher() {
                private int mStart = 0;
                @Override
                public void afterTextChanged(Editable s) {
                    // Trigger event for newly changed value
                    onFieldChanged(column, s.toString());

                    if (!DataKind.PSEUDO_MIME_TYPE_NAME.equals(getKind().mimeType)){
                        return;
                    }

                    String displayNameField = s.toString();

                    int nonFixedLen = displayNameField.length() - mFixedDisplayName.length();
                    if (isUnFixed(s) || nonFixedLen == 0) {
                        String tmpString = mFixedPhonetic
                             + displayNameField.substring(mStart, displayNameField.length());

                        updatePhonetic(column, tmpString);
                    } else {
                        mFixedPhonetic = getPhonetic(column);
                        mFixedDisplayName = displayNameField;
                    }
                }

                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    if (!DataKind.PSEUDO_MIME_TYPE_NAME.equals(getKind().mimeType)){
                        return;
                    }
                    if (needInputInitialize) {
                        mFixedPhonetic = getPhonetic(column);
                        mFixedDisplayName = getNameField(column);
                        needInputInitialize = false;
                    }
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    mStart = start;
                    if (!ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(
                            getKind().mimeType) || !(s instanceof Spannable)) {
                        return;
                    }
                    final Spannable spannable = (Spannable) s;
                    final TtsSpan[] spans = spannable.getSpans(0, s.length(), TtsSpan.class);
                    for (int i = 0; i < spans.length; i++) {
                        spannable.removeSpan(spans[i]);
                    }
                    PhoneNumberUtilsCompat.addTtsSpan(spannable, 0, s.length());
                }
            });

            fieldView.setEnabled(isEnabled() && !readOnly);
            fieldView.setOnFocusChangeListener(mTextFocusChangeListener);

            if (field.shortForm) {
                hidePossible = true;
                mHasShortAndLongForms = true;
                fieldView.setVisibility(mHideOptional ? View.VISIBLE : View.GONE);
            } else if (field.longForm) {
                hidePossible = true;
                mHasShortAndLongForms = true;
                fieldView.setVisibility(mHideOptional ? View.GONE : View.VISIBLE);
            } else {
                // Hide field when empty and optional value
                final boolean couldHide = (!ContactsUtils.isGraphic(value) && field.optional);
                final boolean willHide = (mHideOptional && couldHide);
                fieldView.setVisibility(willHide ? View.GONE : View.VISIBLE);
                hidePossible = hidePossible || couldHide;
            }

            mFields.addView(fieldView);
        }

        if (mExpansionView != null) {
            // When hiding fields, place expandable
            setupExpansionView(hidePossible, mHideOptional);
            mExpansionView.setEnabled(!readOnly && isEnabled());
        }
        updateEmptiness();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < mFields.getChildCount(); i++) {
            EditText editText = (EditText) mFields.getChildAt(i);
            if (!TextUtils.isEmpty(editText.getText())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the editor is currently configured to show optional fields.
     */
    public boolean areOptionalFieldsVisible() {
        return !mHideOptional;
    }

    public boolean hasShortAndLongForms() {
        return mHasShortAndLongForms;
    }

    /**
     * Populates the bound rectangle with the bounds of the last editor field inside this view.
     */
    public void acquireEditorBounds(Rect bounds) {
        if (mFieldEditTexts != null) {
            for (int i = mFieldEditTexts.length; --i >= 0;) {
                EditText editText = mFieldEditTexts[i];
                if (editText.getVisibility() == View.VISIBLE) {
                    bounds.set(editText.getLeft(), editText.getTop(), editText.getRight(),
                            editText.getBottom());
                    return;
                }
            }
        }
    }

    /**
     * Saves the visibility of the child EditTexts, and mHideOptional.
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);

        ss.mHideOptional = mHideOptional;

        final int numChildren = mFieldEditTexts == null ? 0 : mFieldEditTexts.length;
        ss.mVisibilities = new int[numChildren];
        for (int i = 0; i < numChildren; i++) {
            ss.mVisibilities[i] = mFieldEditTexts[i].getVisibility();
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

        int numChildren = Math.min(mFieldEditTexts == null ? 0 : mFieldEditTexts.length,
                ss.mVisibilities == null ? 0 : ss.mVisibilities.length);
        for (int i = 0; i < numChildren; i++) {
            mFieldEditTexts[i].setVisibility(ss.mVisibilities[i]);
        }
        rebuildValues();
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

        @SuppressWarnings({"unused", "hiding" })
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
    }

    @Override
    public void clearAllFields() {
        if (mFieldEditTexts != null) {
            for (EditText fieldEditText : mFieldEditTexts) {
                // Update UI (which will trigger a state change through the {@link TextWatcher})
                fieldEditText.setText("");
            }
        }
    }
}
