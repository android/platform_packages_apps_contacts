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

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.RawContactModifier;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.preference.ContactsPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom view for an entire section of data as segmented by
 * {@link DataKind} around a {@link Data#MIMETYPE}. This view shows a
 * section header and a trigger for adding new {@link Data} rows.
 */
public class KindSectionView extends LinearLayout {

    /**
     * Marks a name as super primary when it is changed.
     *
     * This is for the case when two or more raw contacts with names are joined where neither is
     * marked as super primary.
     */
    private static final class StructuredNameEditorListener implements Editor.EditorListener {

        private final ValuesDelta mValuesDelta;
        private final long mRawContactId;
        private final RawContactEditorView.Listener mListener;

        public StructuredNameEditorListener(ValuesDelta valuesDelta, long rawContactId,
                RawContactEditorView.Listener listener) {
            mValuesDelta = valuesDelta;
            mRawContactId = rawContactId;
            mListener = listener;
        }

        @Override
        public void onRequest(int request) {
            if (request == Editor.EditorListener.FIELD_CHANGED) {
                mValuesDelta.setSuperPrimary(true);
                if (mListener != null) {
                    mListener.onNameFieldChanged(mRawContactId, mValuesDelta);
                }
            } else if (request == Editor.EditorListener.FIELD_TURNED_EMPTY) {
                mValuesDelta.setSuperPrimary(false);
            }
        }

        @Override
        public void onDeleteRequested(Editor editor) {
            editor.clearAllFields();
        }
    }

    /**
     * Clears fields when deletes are requested (on phonetic and nickename fields);
     * does not change the number of editors.
     */
    private static final class OtherNameKindEditorListener implements Editor.EditorListener {

        @Override
        public void onRequest(int request) {
        }

        @Override
        public void onDeleteRequested(Editor editor) {
            editor.clearAllFields();
        }
    }

    /**
     * Updates empty fields when fields are deleted or turns empty.
     * Whether a new empty editor is added is controlled by {@link #setShowOneEmptyEditor} and
     * {@link #setHideWhenEmpty}.
     */
    private class NonNameEditorListener implements Editor.EditorListener {

        @Override
        public void onRequest(int request) {
            // If a field has become empty or non-empty, then check if another row
            // can be added dynamically.
            if (request == FIELD_TURNED_EMPTY || request == FIELD_TURNED_NON_EMPTY) {
                updateEmptyEditors(/* shouldAnimate = */ true);
            }
        }

        @Override
        public void onDeleteRequested(Editor editor) {
            if (mShowOneEmptyEditor && mEditors.getChildCount() == 1) {
                // If there is only 1 editor in the section, then don't allow the user to
                // delete it.  Just clear the fields in the editor.
                editor.clearAllFields();
            } else {
                editor.deleteEditor();
            }
        }
    }

    private class EventEditorListener extends NonNameEditorListener {

        @Override
        public void onRequest(int request) {
            super.onRequest(request);
        }

        @Override
        public void onDeleteRequested(Editor editor) {
            if (editor instanceof EventFieldEditorView){
                final EventFieldEditorView delView = (EventFieldEditorView) editor;
                if (delView.isBirthdayType() && mEditors.getChildCount() > 1) {
                    final EventFieldEditorView bottomView = (EventFieldEditorView) mEditors
                            .getChildAt(mEditors.getChildCount() - 1);
                    bottomView.restoreBirthday();
                }
            }
            super.onDeleteRequested(editor);
        }
    }

    private KindSectionData mKindSectionData;
    private ViewIdGenerator mViewIdGenerator;
    private RawContactEditorView.Listener mListener;

    private boolean mIsUserProfile;
    private boolean mShowOneEmptyEditor = false;
    private boolean mHideIfEmpty = true;

    private LayoutInflater mLayoutInflater;
    private ViewGroup mEditors;
    private ImageView mIcon;

    public KindSectionView(Context context) {
        this(context, /* attrs =*/ null);
    }

    public KindSectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        if (mEditors != null) {
            int childCount = mEditors.getChildCount();
            for (int i = 0; i < childCount; i++) {
                mEditors.getChildAt(i).setEnabled(enabled);
            }
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        setDrawingCacheEnabled(true);
        setAlwaysDrawnWithCacheEnabled(true);

        mLayoutInflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        mEditors = (ViewGroup) findViewById(R.id.kind_editors);
        mIcon = (ImageView) findViewById(R.id.kind_icon);
    }

    public void setIsUserProfile(boolean isUserProfile) {
        mIsUserProfile = isUserProfile;
    }

    /**
     * @param showOneEmptyEditor If true, we will always show one empty editor, otherwise an empty
     *         editor will not be shown until the user enters a value.  Note, this does not apply
     *         to name editors since those are always displayed.
     */
    public void setShowOneEmptyEditor(boolean showOneEmptyEditor) {
        mShowOneEmptyEditor = showOneEmptyEditor;
    }

    /**
     * @param hideWhenEmpty If true, the entire section will be hidden if all inputs are empty,
     *         otherwise one empty input will always be displayed.  Note, this does not apply
     *         to name editors since those are always displayed.
     */
    public void setHideWhenEmpty(boolean hideWhenEmpty) {
        mHideIfEmpty = hideWhenEmpty;
    }

    /** Binds the given group data to every {@link GroupMembershipView}. */
    public void setGroupMetaData(Cursor cursor) {
        for (int i = 0; i < mEditors.getChildCount(); i++) {
            final View view = mEditors.getChildAt(i);
            if (view instanceof GroupMembershipView) {
                ((GroupMembershipView) view).setGroupMetaData(cursor);
            }
        }
    }

    /**
     * Whether this is a name kind section view and all name fields (structured, phonetic,
     * and nicknames) are empty.
     */
    public boolean isEmptyName() {
        if (!StructuredName.CONTENT_ITEM_TYPE.equals(mKindSectionData.getMimeType())) {
            return false;
        }
        for (int i = 0; i < mEditors.getChildCount(); i++) {
            final View view = mEditors.getChildAt(i);
            if (view instanceof Editor) {
                final Editor editor = (Editor) view;
                if (!editor.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public StructuredNameEditorView getNameEditorView() {
        if (!StructuredName.CONTENT_ITEM_TYPE.equals(mKindSectionData.getMimeType())
            || mEditors.getChildCount() == 0) {
            return null;
        }
        return (StructuredNameEditorView) mEditors.getChildAt(0);
    }

    /**
     * Binds views for the given {@link KindSectionData}.
     *
     * We create a structured name and phonetic name editor for each {@link DataKind} with a
     * {@link StructuredName#CONTENT_ITEM_TYPE} mime type.  The number and order of editors are
     * rendered as they are given to {@link #setState}.
     *
     * Empty name editors are never added and at least one structured name editor is always
     * displayed, even if it is empty.
     */
    public void setState(KindSectionData kindSectionData,
            ViewIdGenerator viewIdGenerator, RawContactEditorView.Listener listener) {
        mKindSectionData = kindSectionData;
        mViewIdGenerator = viewIdGenerator;
        mListener = listener;

        // Set the icon using the DataKind
        final DataKind dataKind = mKindSectionData.getDataKind();
        if (dataKind != null) {
            mIcon.setImageDrawable(EditorUiUtils.getMimeTypeDrawable(getContext(),
                    dataKind.mimeType));
            if (mIcon.getDrawable() != null) {
                mIcon.setContentDescription(dataKind.titleRes == -1 || dataKind.titleRes == 0
                        ? "" : getResources().getString(dataKind.titleRes));
            }
        }

        rebuildFromState();

        updateEmptyEditors(/* shouldAnimate = */ false);
    }

    private void rebuildFromState() {
        mEditors.removeAllViews();

        final String mimeType = mKindSectionData.getMimeType();
        if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
            addNameEditorViews(mKindSectionData.getAccountType(),
                    mKindSectionData.getRawContactDelta());
        } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
            addGroupEditorView(mKindSectionData.getRawContactDelta(),
                    mKindSectionData.getDataKind());
        } else {
            final Editor.EditorListener editorListener;
            if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType)) {
                editorListener = new OtherNameKindEditorListener();
            } else if (Event.CONTENT_ITEM_TYPE.equals(mimeType)) {
                editorListener = new EventEditorListener();
            } else {
                editorListener = new NonNameEditorListener();
            }
            final List<ValuesDelta> valuesDeltas = mKindSectionData.getVisibleValuesDeltas();
            for (int i = 0; i < valuesDeltas.size(); i++ ) {
                addNonNameEditorView(mKindSectionData.getRawContactDelta(),
                        mKindSectionData.getDataKind(), valuesDeltas.get(i), editorListener);
            }
        }
    }

    private void addNameEditorViews(AccountType accountType, RawContactDelta rawContactDelta) {
        final boolean readOnly = !accountType.areContactsWritable();
        final ValuesDelta nameValuesDelta = rawContactDelta
                .getSuperPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);

        if (readOnly) {
            final View nameView = mLayoutInflater.inflate(
                    R.layout.structured_name_readonly_editor_view, mEditors,
                    /* attachToRoot =*/ false);

            // Display name
            ((TextView) nameView.findViewById(R.id.display_name))
                    .setText(nameValuesDelta.getDisplayName());

            // Account type info
            final LinearLayout accountTypeLayout = (LinearLayout)
                    nameView.findViewById(R.id.account_type);
            accountTypeLayout.setVisibility(View.VISIBLE);
            ((ImageView) accountTypeLayout.findViewById(R.id.account_type_icon))
                    .setImageDrawable(accountType.getDisplayIcon(getContext()));
            ((TextView) accountTypeLayout.findViewById(R.id.account_type_name))
                    .setText(accountType.getDisplayLabel(getContext()));

            mEditors.addView(nameView);
            return;
        }

        // Structured name
        final StructuredNameEditorView nameView = (StructuredNameEditorView) mLayoutInflater
                .inflate(R.layout.structured_name_editor_view, mEditors, /* attachToRoot =*/ false);
        if (!mIsUserProfile) {
            // Don't set super primary for the me contact
            nameView.setEditorListener(new StructuredNameEditorListener(
                    nameValuesDelta, rawContactDelta.getRawContactId(), mListener));
        }
        nameView.setDeletable(false);
        nameView.setValues(accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_NAME),
                nameValuesDelta, rawContactDelta, /* readOnly =*/ false, mViewIdGenerator);

        // Correct start margin since there is a second icon in the structured name layout
        nameView.findViewById(R.id.kind_icon).setVisibility(View.GONE);
        mEditors.addView(nameView);

        // Phonetic name
        final DataKind phoneticNameKind = accountType
                .getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME);
        // The account type doesn't support phonetic name.
        if (phoneticNameKind == null) return;

        final TextFieldsEditorView phoneticNameView = (TextFieldsEditorView) mLayoutInflater
                .inflate(R.layout.text_fields_editor_view, mEditors, /* attachToRoot =*/ false);
        phoneticNameView.setEditorListener(new OtherNameKindEditorListener());
        phoneticNameView.setDeletable(false);
        phoneticNameView.setValues(
                accountType.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME),
                nameValuesDelta, rawContactDelta, /* readOnly =*/ false, mViewIdGenerator);

        // Fix the start margin for phonetic name views
        final LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, 0);
        phoneticNameView.setLayoutParams(layoutParams);
        mEditors.addView(phoneticNameView);
        // Display of phonetic name fields is controlled from settings preferences.
        mHideIfEmpty = new ContactsPreferences(getContext()).shouldHidePhoneticNamesIfEmpty();
    }

    private void addGroupEditorView(RawContactDelta rawContactDelta, DataKind dataKind) {
        final GroupMembershipView view = (GroupMembershipView) mLayoutInflater.inflate(
                R.layout.item_group_membership, mEditors, /* attachToRoot =*/ false);
        view.setKind(dataKind);
        view.setEnabled(isEnabled());
        view.setState(rawContactDelta);

        // Correct start margin since there is a second icon in the group layout
        view.findViewById(R.id.kind_icon).setVisibility(View.GONE);

        mEditors.addView(view);
    }

    private View addNonNameEditorView(RawContactDelta rawContactDelta, DataKind dataKind,
            ValuesDelta valuesDelta, Editor.EditorListener editorListener) {
        // Inflate the layout
        final View view = mLayoutInflater.inflate(
                EditorUiUtils.getLayoutResourceId(dataKind.mimeType), mEditors, false);
        view.setEnabled(isEnabled());
        if (view instanceof Editor) {
            final Editor editor = (Editor) view;
            editor.setDeletable(true);
            editor.setEditorListener(editorListener);
            editor.setValues(dataKind, valuesDelta, rawContactDelta, !dataKind.editable,
                    mViewIdGenerator);
        }
        mEditors.addView(view);

        return view;
    }

    /**
     * Updates the editors being displayed to the user removing extra empty
     * {@link Editor}s, so there is only max 1 empty {@link Editor} view at a time.
     * If there is only 1 empty editor and {@link #setHideWhenEmpty} was set to true,
     * then the entire section is hidden.
     */
    public void updateEmptyEditors(boolean shouldAnimate) {
        final boolean isNameKindSection = StructuredName.CONTENT_ITEM_TYPE.equals(
                mKindSectionData.getMimeType());
        final boolean isGroupKindSection = GroupMembership.CONTENT_ITEM_TYPE.equals(
                mKindSectionData.getMimeType());

        if (isNameKindSection) {
            // The name kind section is always visible
            setVisibility(VISIBLE);
            updateEmptyNameEditors(shouldAnimate);
        } else if (isGroupKindSection) {
            // Check whether metadata has been bound for all group views
            for (int i = 0; i < mEditors.getChildCount(); i++) {
                final View view = mEditors.getChildAt(i);
                if (view instanceof GroupMembershipView) {
                    final GroupMembershipView groupView = (GroupMembershipView) view;
                    if (!groupView.wasGroupMetaDataBound() || !groupView.accountHasGroups()) {
                        setVisibility(GONE);
                        return;
                    }
                }
            }
            // Check that the user has selected to display all fields
            if (mHideIfEmpty) {
                setVisibility(GONE);
                return;
            }
            setVisibility(VISIBLE);

            // We don't check the emptiness of the group views
        } else {
            // Determine if the entire kind section should be visible
            final int editorCount = mEditors.getChildCount();
            final List<View> emptyEditors = getEmptyEditors();
            if (editorCount == emptyEditors.size() && mHideIfEmpty) {
                setVisibility(GONE);
                return;
            }
            setVisibility(VISIBLE);

            updateEmptyNonNameEditors(shouldAnimate);
        }
    }

    private void updateEmptyNameEditors(boolean shouldAnimate) {
        boolean isEmptyNameEditorVisible = false;

        for (int i = 0; i < mEditors.getChildCount(); i++) {
            final View view = mEditors.getChildAt(i);
            if (view instanceof Editor) {
                final Editor editor = (Editor) view;
                if (view instanceof StructuredNameEditorView) {
                    // We always show one empty structured name view
                    if (editor.isEmpty()) {
                        if (isEmptyNameEditorVisible) {
                            // If we're already showing an empty editor then hide any other empties
                            if (mHideIfEmpty) {
                                view.setVisibility(View.GONE);
                            }
                        } else {
                            isEmptyNameEditorVisible = true;
                        }
                    } else {
                        showView(view, shouldAnimate);
                        isEmptyNameEditorVisible = true;
                    }
                } else {
                    // Since we can't add phonetic names and nicknames, just show or hide them
                    if (mHideIfEmpty && editor.isEmpty()) {
                        hideView(view);
                    } else {
                        showView(view, /* shouldAnimate =*/ false); // Animation here causes jank
                    }
                }
            } else {
                // For read only names, only show them if we're not hiding empty views
                if (mHideIfEmpty) {
                    hideView(view);
                } else {
                    showView(view, shouldAnimate);
                }
            }
        }
    }

    private void updateEmptyNonNameEditors(boolean shouldAnimate) {
        // Prune excess empty editors
        final List<View> emptyEditors = getEmptyEditors();
        if (emptyEditors.size() > 1) {
            // If there is more than 1 empty editor, then remove it from the list of editors.
            int deleted = 0;
            for (int i = 0; i < emptyEditors.size(); i++) {
                final View view = emptyEditors.get(i);
                // If no child {@link View}s are being focused on within this {@link View}, then
                // remove this empty editor. We can assume that at least one empty editor has
                // focus. One way to get two empty editors is by deleting characters from a
                // non-empty editor, in which case this editor has focus.  Another way is if
                // there is more values delta so we must also count number of editors deleted.
                if (view.findFocus() == null) {
                    deleteView(view, shouldAnimate);
                    deleted++;
                    if (deleted == emptyEditors.size() - 1) break;
                }
            }
            return;
        }
        // Determine if we should add a new empty editor
        final DataKind dataKind = mKindSectionData.getDataKind();
        final RawContactDelta rawContactDelta = mKindSectionData.getRawContactDelta();
        if (dataKind == null // There is nothing we can do.
                // We have already reached the maximum number of editors, don't add any more.
                || !RawContactModifier.canInsert(rawContactDelta, dataKind)
                // We have already reached the maximum number of empty editors, don't add any more.
                || emptyEditors.size() == 1) {
            return;
        }
        // Add a new empty editor
        if (mShowOneEmptyEditor) {
            final String mimeType = mKindSectionData.getMimeType();
            if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType) && mEditors.getChildCount() > 0) {
                return;
            }
            final ValuesDelta values = RawContactModifier.insertChild(rawContactDelta, dataKind);
            final Editor.EditorListener editorListener = Event.CONTENT_ITEM_TYPE.equals(mimeType)
                    ? new EventEditorListener() : new NonNameEditorListener();
            final View view = addNonNameEditorView(rawContactDelta, dataKind, values,
                    editorListener);
            showView(view, shouldAnimate);
        }
    }

    private void hideView(View view) {
        view.setVisibility(View.GONE);
    }

    private void deleteView(View view, boolean shouldAnimate) {
        if (shouldAnimate) {
            final Editor editor = (Editor) view;
            editor.deleteEditor();
        } else {
            mEditors.removeView(view);
        }
    }

    private void showView(View view, boolean shouldAnimate) {
        if (shouldAnimate) {
            view.setVisibility(View.GONE);
            EditorAnimator.getInstance().showFieldFooter(view);
        } else {
            view.setVisibility(View.VISIBLE);
        }
    }

    private List<View> getEmptyEditors() {
        final List<View> emptyEditors = new ArrayList<>();
        for (int i = 0; i < mEditors.getChildCount(); i++) {
            final View view = mEditors.getChildAt(i);
            if (view instanceof Editor && ((Editor) view).isEmpty()) {
                emptyEditors.add(view);
            }
        }
        return emptyEditors;
    }
}
