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

package com.android.contacts.editor;

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountType.EditType;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.RawContactModifier;
import com.google.common.base.Objects;

import java.util.ArrayList;

/**
 * Custom view that provides all the editor interaction for a specific
 * {@link Contacts} represented through an {@link RawContactDelta}. Callers can
 * reuse this view and quickly rebuild its contents through
 * {@link #setState(RawContactDelta, AccountType, ViewIdGenerator)}.
 * <p>
 * Internal updates are performed against {@link ValuesDelta} so that the
 * source {@link RawContact} can be swapped out. Any state-based changes, such as
 * adding {@link Data} rows or changing {@link EditType}, are performed through
 * {@link RawContactModifier} to ensure that {@link AccountType} are enforced.
 */
public class RawContactEditorView extends BaseRawContactEditorView {
    private static final String KEY_ORGANIZATION_VIEW_EXPANDED = "organizationViewExpanded";
    private static final String KEY_SUPER_INSTANCE_STATE = "superInstanceState";

    private LayoutInflater mInflater;

    private StructuredNameEditorView mName;
    private PhoneticNameEditorView mPhoneticName;
    private GroupMembershipView mGroupMembershipView;

    private ViewGroup mOrganizationSectionViewContainer;
    private View mAddOrganizationButton;
    private View mOrganizationView;
    private boolean mOrganizationViewExpanded = false;

    private ViewGroup mFields;

    private ImageView mAccountIcon;
    private TextView mAccountTypeTextView;
    private TextView mAccountNameTextView;

    private Button mAddFieldButton;

    private long mRawContactId = -1;
    private boolean mAutoAddToDefaultGroup = true;
    private Cursor mGroupMetaData;
    private DataKind mGroupMembershipKind;
    private RawContactDelta mState;

    private boolean mPhoneticNameAdded;

    public RawContactEditorView(Context context) {
        super(context);
    }

    public RawContactEditorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        View view = getPhotoEditor();
        if (view != null) {
            view.setEnabled(enabled);
        }

        if (mName != null) {
            mName.setEnabled(enabled);
        }

        if (mPhoneticName != null) {
            mPhoneticName.setEnabled(enabled);
        }

        if (mFields != null) {
            int count = mFields.getChildCount();
            for (int i = 0; i < count; i++) {
                mFields.getChildAt(i).setEnabled(enabled);
            }
        }

        if (mGroupMembershipView != null) {
            mGroupMembershipView.setEnabled(enabled);
        }

        mAddFieldButton.setEnabled(enabled);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mInflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mName = (StructuredNameEditorView)findViewById(R.id.edit_name);
        mName.setDeletable(false);

        mPhoneticName = (PhoneticNameEditorView)findViewById(R.id.edit_phonetic_name);
        mPhoneticName.setDeletable(false);

        mFields = (ViewGroup)findViewById(R.id.sect_fields);

        mAccountIcon = (ImageView) findViewById(R.id.account_icon);
        mAccountTypeTextView = (TextView) findViewById(R.id.account_type);
        mAccountNameTextView = (TextView) findViewById(R.id.account_name);

        mOrganizationView = mInflater.inflate(
                R.layout.organization_editor_view_switcher, mFields, false);
        mAddOrganizationButton = mOrganizationView.findViewById(
                R.id.add_organization_button);
        mOrganizationSectionViewContainer =
                (ViewGroup) mOrganizationView.findViewById(R.id.container);

        mAddFieldButton = (Button) findViewById(R.id.button_add_field);
        mAddFieldButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddInformationPopupWindow();
            }
        });
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(KEY_ORGANIZATION_VIEW_EXPANDED, mOrganizationViewExpanded);
        // super implementation of onSaveInstanceState returns null
        bundle.putParcelable(KEY_SUPER_INSTANCE_STATE, super.onSaveInstanceState());
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            mOrganizationViewExpanded = bundle.getBoolean(KEY_ORGANIZATION_VIEW_EXPANDED);
            if (mOrganizationViewExpanded) {
                // we have to manually perform the expansion here because
                // onRestoreInstanceState is called after setState. So at the point
                // of the creation of the organization view, mOrganizationViewExpanded
                // does not have the correct value yet.
                mOrganizationSectionViewContainer.setVisibility(VISIBLE);
                mAddOrganizationButton.setVisibility(GONE);
            }
            super.onRestoreInstanceState(bundle.getParcelable(KEY_SUPER_INSTANCE_STATE));
            return;
        }
        super.onRestoreInstanceState(state);
        return;
    }

    /**
     * Set the internal state for this view, given a current
     * {@link RawContactDelta} state and the {@link AccountType} that
     * apply to that state.
     */
    @Override
    public void setState(RawContactDelta state, AccountType type, ViewIdGenerator vig,
            boolean isProfile) {

        mState = state;

        // Remove any existing sections
        mFields.removeAllViews();

        // Bail if invalid state or account type
        if (state == null || type == null) return;

        setId(vig.getId(state, null, null, ViewIdGenerator.NO_VIEW_INDEX));

        // Make sure we have a StructuredName and Organization
        RawContactModifier.ensureKindExists(state, type, StructuredName.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(state, type, Organization.CONTENT_ITEM_TYPE);

        mRawContactId = state.getRawContactId();

        // Fill in the account info
        if (isProfile) {
            String accountName = state.getAccountName();
            if (TextUtils.isEmpty(accountName)) {
                mAccountNameTextView.setVisibility(View.GONE);
                mAccountTypeTextView.setText(R.string.local_profile_title);
            } else {
                CharSequence accountType = type.getDisplayLabel(mContext);
                mAccountTypeTextView.setText(mContext.getString(R.string.external_profile_title,
                        accountType));
                mAccountNameTextView.setText(accountName);
            }
        } else {
            String accountName = state.getAccountName();
            CharSequence accountType = type.getDisplayLabel(mContext);
            if (TextUtils.isEmpty(accountType)) {
                accountType = mContext.getString(R.string.account_phone);
            }
            if (!TextUtils.isEmpty(accountName)) {
                mAccountNameTextView.setVisibility(View.VISIBLE);
                mAccountNameTextView.setText(
                        mContext.getString(R.string.from_account_format, accountName));
            } else {
                // Hide this view so the other text view will be centered vertically
                mAccountNameTextView.setVisibility(View.GONE);
            }
            mAccountTypeTextView.setText(
                    mContext.getString(R.string.account_type_format, accountType));
        }
        mAccountIcon.setImageDrawable(type.getDisplayIcon(mContext));

        // Show photo editor when supported
        RawContactModifier.ensureKindExists(state, type, Photo.CONTENT_ITEM_TYPE);
        setHasPhotoEditor((type.getKindForMimetype(Photo.CONTENT_ITEM_TYPE) != null));
        getPhotoEditor().setEnabled(isEnabled());
        mName.setEnabled(isEnabled());

        mPhoneticName.setEnabled(isEnabled());

        // Show and hide the appropriate views
        mFields.setVisibility(View.VISIBLE);
        mName.setVisibility(View.VISIBLE);
        mPhoneticName.setVisibility(View.VISIBLE);

        mGroupMembershipKind = type.getKindForMimetype(GroupMembership.CONTENT_ITEM_TYPE);
        if (mGroupMembershipKind != null) {
            mGroupMembershipView = (GroupMembershipView)mInflater.inflate(
                    R.layout.item_group_membership, mFields, false);
            mGroupMembershipView.setKind(mGroupMembershipKind);
            mGroupMembershipView.setEnabled(isEnabled());
        }

        // Create editor sections for each possible data kind
        for (DataKind kind : type.getSortedDataKinds()) {
            // Skip kind of not editable
            if (!kind.editable) continue;

            final String mimeType = kind.mimeType;
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for structured name
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                mName.setValues(
                        type.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME),
                        primary, state, false, vig);
                mPhoneticName.setValues(
                        type.getKindForMimetype(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME),
                        primary, state, false, vig);
            } else if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Handle special case editor for photos
                final ValuesDelta primary = state.getPrimaryEntry(mimeType);
                getPhotoEditor().setValues(kind, primary, state, false, vig);
            } else if (GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                if (mGroupMembershipView != null) {
                    mGroupMembershipView.setState(state);
                }
            } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Create the organization section
                final KindSectionView section = (KindSectionView) mInflater.inflate(
                        R.layout.item_kind_section, mFields, false);
                section.setTitleVisible(false);
                section.setEnabled(isEnabled());
                section.setState(kind, state, false, vig);

                // If there is organization info for the contact already, display it
                if (!section.isEmpty()) {
                    mFields.addView(section);
                } else {
                    // Otherwise provide the user with an "add organization" button that shows the
                    // EditText fields only when clicked
                    mOrganizationSectionViewContainer.removeAllViews();
                    mOrganizationSectionViewContainer.addView(section);

                    // Setup the click listener for the "add organization" button
                    mAddOrganizationButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Once the user expands the organization field, the user cannot
                            // collapse them again.
                            EditorAnimator.getInstance().expandOrganization(mAddOrganizationButton,
                                    mOrganizationSectionViewContainer);
                            mOrganizationViewExpanded = true;
                        }
                    });

                    mFields.addView(mOrganizationView);
                }
            } else {
                // Otherwise use generic section-based editors
                if (kind.fieldList == null) continue;
                final KindSectionView section = (KindSectionView)mInflater.inflate(
                        R.layout.item_kind_section, mFields, false);
                section.setEnabled(isEnabled());
                section.setState(kind, state, false, vig);
                mFields.addView(section);
            }
        }

        if (mGroupMembershipView != null) {
            mFields.addView(mGroupMembershipView);
        }

        updatePhoneticNameVisibility();

        addToDefaultGroupIfNeeded();


        final int sectionCount = getSectionViewsWithoutFields().size();
        mAddFieldButton.setVisibility(sectionCount > 0 ? View.VISIBLE : View.GONE);
        mAddFieldButton.setEnabled(isEnabled());
    }

    @Override
    public void setGroupMetaData(Cursor groupMetaData) {
        mGroupMetaData = groupMetaData;
        addToDefaultGroupIfNeeded();
        if (mGroupMembershipView != null) {
            mGroupMembershipView.setGroupMetaData(groupMetaData);
        }
    }

    public void setAutoAddToDefaultGroup(boolean flag) {
        this.mAutoAddToDefaultGroup = flag;
    }

    /**
     * If automatic addition to the default group was requested (see
     * {@link #setAutoAddToDefaultGroup}, checks if the raw contact is in any
     * group and if it is not adds it to the default group (in case of Google
     * contacts that's "My Contacts").
     */
    private void addToDefaultGroupIfNeeded() {
        if (!mAutoAddToDefaultGroup || mGroupMetaData == null || mGroupMetaData.isClosed()
                || mState == null) {
            return;
        }

        boolean hasGroupMembership = false;
        ArrayList<ValuesDelta> entries = mState.getMimeEntries(GroupMembership.CONTENT_ITEM_TYPE);
        if (entries != null) {
            for (ValuesDelta values : entries) {
                Long id = values.getGroupRowId();
                if (id != null && id.longValue() != 0) {
                    hasGroupMembership = true;
                    break;
                }
            }
        }

        if (!hasGroupMembership) {
            long defaultGroupId = getDefaultGroupId();
            if (defaultGroupId != -1) {
                ValuesDelta entry = RawContactModifier.insertChild(mState, mGroupMembershipKind);
                entry.setGroupRowId(defaultGroupId);
            }
        }
    }

    /**
     * Returns the default group (e.g. "My Contacts") for the current raw contact's
     * account.  Returns -1 if there is no such group.
     */
    private long getDefaultGroupId() {
        String accountType = mState.getAccountType();
        String accountName = mState.getAccountName();
        String accountDataSet = mState.getDataSet();
        mGroupMetaData.moveToPosition(-1);
        while (mGroupMetaData.moveToNext()) {
            String name = mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_NAME);
            String type = mGroupMetaData.getString(GroupMetaDataLoader.ACCOUNT_TYPE);
            String dataSet = mGroupMetaData.getString(GroupMetaDataLoader.DATA_SET);
            if (name.equals(accountName) && type.equals(accountType)
                    && Objects.equal(dataSet, accountDataSet)) {
                long groupId = mGroupMetaData.getLong(GroupMetaDataLoader.GROUP_ID);
                if (!mGroupMetaData.isNull(GroupMetaDataLoader.AUTO_ADD)
                            && mGroupMetaData.getInt(GroupMetaDataLoader.AUTO_ADD) != 0) {
                    return groupId;
                }
            }
        }
        return -1;
    }

    public StructuredNameEditorView getNameEditor() {
        return mName;
    }

    public TextFieldsEditorView getPhoneticNameEditor() {
        return mPhoneticName;
    }

    private void updatePhoneticNameVisibility() {
        boolean showByDefault =
                getContext().getResources().getBoolean(R.bool.config_editor_include_phonetic_name);

        if (showByDefault || mPhoneticName.hasData() || mPhoneticNameAdded) {
            mPhoneticName.setVisibility(View.VISIBLE);
        } else {
            mPhoneticName.setVisibility(View.GONE);
        }
    }

    @Override
    public long getRawContactId() {
        return mRawContactId;
    }

    /**
     * Return a list of KindSectionViews that have no fields yet...
     * these are candidates to have fields added in
     * {@link #showAddInformationPopupWindow()}
     */
    private ArrayList<KindSectionView> getSectionViewsWithoutFields() {
        final ArrayList<KindSectionView> fields =
                new ArrayList<KindSectionView>(mFields.getChildCount());
        for (int i = 0; i < mFields.getChildCount(); i++) {
            View child = mFields.getChildAt(i);
            if (child instanceof KindSectionView) {
                final KindSectionView sectionView = (KindSectionView) child;
                // If the section is already visible (has 1 or more editors), then don't offer the
                // option to add this type of field in the popup menu
                if (sectionView.getEditorCount() > 0) {
                    continue;
                }
                DataKind kind = sectionView.getKind();
                // not a list and already exists? ignore
                if ((kind.typeOverallMax == 1) && sectionView.getEditorCount() != 0) {
                    continue;
                }
                if (DataKind.PSEUDO_MIME_TYPE_DISPLAY_NAME.equals(kind.mimeType)) {
                    continue;
                }

                if (DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(kind.mimeType)
                        && mPhoneticName.getVisibility() == View.VISIBLE) {
                    continue;
                }

                fields.add(sectionView);
            }
        }
        return fields;
    }

    private void showAddInformationPopupWindow() {
        final ArrayList<KindSectionView> fields = getSectionViewsWithoutFields();
        final PopupMenu popupMenu = new PopupMenu(getContext(), mAddFieldButton);
        final Menu menu = popupMenu.getMenu();
        for (int i = 0; i < fields.size(); i++) {
            menu.add(Menu.NONE, i, Menu.NONE, fields.get(i).getTitle());
        }

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                final KindSectionView view = fields.get(item.getItemId());
                if (DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(view.getKind().mimeType)) {
                    mPhoneticNameAdded = true;
                    updatePhoneticNameVisibility();
                    mPhoneticName.requestFocus();
                } else {
                    view.addItem();
                }

                // If this was the last section without an entry, we just added one, and therefore
                // there's no reason to show the button.
                if (fields.size() == 1) {
                    mAddFieldButton.setVisibility(View.GONE);
                }

                return true;
            }
        });

        popupMenu.show();
    }
}
