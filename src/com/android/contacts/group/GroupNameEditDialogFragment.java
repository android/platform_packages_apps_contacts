/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, softwareateCre
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package com.android.contacts.group;

import android.app.Dialog;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract.Groups;
import com.google.android.material.textfield.TextInputLayout;
import androidx.appcompat.app.AlertDialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.model.account.AccountWithDataSet;

import com.google.common.base.Strings;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Edits the name of a group.
 */
public final class GroupNameEditDialogFragment extends DialogFragment implements
        LoaderManager.LoaderCallbacks<Cursor> {

    private static final String KEY_GROUP_NAME = "groupName";

    private static final String ARG_IS_INSERT = "isInsert";
    private static final String ARG_GROUP_NAME = "groupName";
    private static final String ARG_ACCOUNT = "account";
    private static final String ARG_CALLBACK_ACTION = "callbackAction";
    private static final String ARG_GROUP_ID = "groupId";

    private static final long NO_GROUP_ID = -1;


    /** Callbacks for hosts of the {@link GroupNameEditDialogFragment}. */
    public interface Listener {
        void onGroupNameEditCancelled();
        void onGroupNameEditCompleted(String name);

        public static final Listener None = new Listener() {
            @Override
            public void onGroupNameEditCancelled() { }

            @Override
            public void onGroupNameEditCompleted(String name) { }
        };
    }

    private boolean mIsInsert;
    private String mGroupName;
    private long mGroupId;
    private Listener mListener;
    private AccountWithDataSet mAccount;
    private EditText mGroupNameEditText;
    private TextInputLayout mGroupNameTextLayout;
    private Set<String> mExistingGroups = Collections.emptySet();

    public static GroupNameEditDialogFragment newInstanceForCreation(
            AccountWithDataSet account, String callbackAction) {
        return newInstance(account, callbackAction, NO_GROUP_ID, null);
    }

    public static GroupNameEditDialogFragment newInstanceForUpdate(
            AccountWithDataSet account, String callbackAction, long groupId, String groupName) {
        return newInstance(account, callbackAction, groupId, groupName);
    }

    private static GroupNameEditDialogFragment newInstance(
            AccountWithDataSet account, String callbackAction, long groupId, String groupName) {
        if (account == null || account.name == null || account.type == null) {
            throw new IllegalArgumentException("Invalid account");
        }
        final boolean isInsert = groupId == NO_GROUP_ID;
        final Bundle args = new Bundle();
        args.putBoolean(ARG_IS_INSERT, isInsert);
        args.putLong(ARG_GROUP_ID, groupId);
        args.putString(ARG_GROUP_NAME, groupName);
        args.putParcelable(ARG_ACCOUNT, account);
        args.putString(ARG_CALLBACK_ACTION, callbackAction);

        final GroupNameEditDialogFragment dialog = new GroupNameEditDialogFragment();
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.ContactsAlertDialogThemeAppCompat);
        final Bundle args = getArguments();
        if (savedInstanceState == null) {
            mGroupName = args.getString(KEY_GROUP_NAME);
        } else {
            mGroupName = savedInstanceState.getString(ARG_GROUP_NAME);
        }

        mGroupId = args.getLong(ARG_GROUP_ID, NO_GROUP_ID);
        mIsInsert = args.getBoolean(ARG_IS_INSERT, true);
        mAccount = getArguments().getParcelable(ARG_ACCOUNT);

        // There is only one loader so the id arg doesn't matter.
        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Build a dialog with two buttons and a view of a single EditText input field
        final TextView title = (TextView) View.inflate(getActivity(), R.layout.dialog_title, null);
        title.setText(mIsInsert
                ? R.string.group_name_dialog_insert_title
                : R.string.group_name_dialog_update_title);
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), getTheme())
                .setCustomTitle(title)
                .setView(R.layout.group_name_edit_dialog)
                .setNegativeButton(android.R.string.cancel, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        hideInputMethod();
                        getListener().onGroupNameEditCancelled();
                        dismiss();
                    }
                })
                // The Positive button listener is defined below in the OnShowListener to
                // allow for input validation
                .setPositiveButton(android.R.string.ok, null);

        // Disable the create button when the name is empty
        final AlertDialog alertDialog = builder.create();
        alertDialog.getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                mGroupNameEditText = (EditText) alertDialog.findViewById(android.R.id.text1);
                mGroupNameTextLayout =
                        (TextInputLayout) alertDialog.findViewById(R.id.text_input_layout);
                if (!TextUtils.isEmpty(mGroupName)) {
                    mGroupNameEditText.setText(mGroupName);
                    // Guard against already created group names that are longer than the max
                    final int maxLength = getResources().getInteger(
                            R.integer.group_name_max_length);
                    mGroupNameEditText.setSelection(
                            mGroupName.length() > maxLength ? maxLength : mGroupName.length());
                }
                showInputMethod(mGroupNameEditText);

                final Button createButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                createButton.setEnabled(!TextUtils.isEmpty(getGroupName()));

                // Override the click listener to prevent dismissal if creating a duplicate group.
                createButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        maybePersistCurrentGroupName(v);
                    }
                });
                mGroupNameEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    }

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        mGroupNameTextLayout.setError(null);
                        createButton.setEnabled(!TextUtils.isEmpty(s));
                    }
                });
            }
        });

        return alertDialog;
    }

    /**
     * Sets the listener for the rename
     *
     * Setting a listener on a fragment is error prone since it will be lost if the fragment
     * is recreated. This exists because it is used from a view class (GroupMembersView) which
     * needs to modify it's state when this fragment updates the name.
     *
     * @param listener the listener. can be null
     */
    public void setListener(Listener listener) {
        mListener = listener;
    }

    private boolean hasNameChanged() {
        final String name = Strings.nullToEmpty(getGroupName());
        final String originalName = getArguments().getString(ARG_GROUP_NAME);
        return (mIsInsert && !name.isEmpty()) || !name.equals(originalName);
    }

    private void maybePersistCurrentGroupName(View button) {
        if (!hasNameChanged()) {
            dismiss();
            return;
        }
        String name = getGroupName();
        // Trim group name, when group is saved.
        // When "Group" exists, do not save " Group ". This behavior is the same as Google Contacts.
        if (!TextUtils.isEmpty(name)) {
            name = name.trim();
        }
        // Note we don't check if the loader finished populating mExistingGroups. It's not the
        // end of the world if the user ends up with a duplicate group and in practice it should
        // never really happen (the query should complete much sooner than the user can edit the
        // label)
        if (mExistingGroups.contains(name)) {
            mGroupNameTextLayout.setError(
                    getString(R.string.groupExistsErrorMessage));
            button.setEnabled(false);
            return;
        }
        final String callbackAction = getArguments().getString(ARG_CALLBACK_ACTION);
        final Intent serviceIntent;
        if (mIsInsert) {
            serviceIntent = ContactSaveService.createNewGroupIntent(getActivity(), mAccount,
                    name, null, getActivity().getClass(), callbackAction);
        } else {
            serviceIntent = ContactSaveService.createGroupRenameIntent(getActivity(), mGroupId,
                    name, getActivity().getClass(), callbackAction);
        }
        ContactSaveService.startService(getActivity(), serviceIntent);
        getListener().onGroupNameEditCompleted(mGroupName);
        dismiss();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        getListener().onGroupNameEditCancelled();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_GROUP_NAME, getGroupName());
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Only a single loader so id is ignored.
        return new CursorLoader(getActivity(), Groups.CONTENT_SUMMARY_URI,
                new String[] { Groups.TITLE, Groups.SYSTEM_ID, Groups.ACCOUNT_TYPE,
                        Groups.SUMMARY_COUNT, Groups.GROUP_IS_READ_ONLY},
                getSelection(), getSelectionArgs(), null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        mExistingGroups = new HashSet<>();
        final GroupUtil.GroupsProjection projection = new GroupUtil.GroupsProjection(data);
        // Initialize cursor's position. If Activity relaunched by orientation change,
        // only onLoadFinished is called. OnCreateLoader is not called.
        // The cursor's position is remain end position by moveToNext when the last onLoadFinished
        // was called. Therefore, if cursor position was not initialized mExistingGroups is empty.
        data.moveToPosition(-1);
        while (data.moveToNext()) {
            String title = projection.getTitle(data);
            // Trim existing group name.
            // When " Group " exists, do not save "Group".
            // This behavior is the same as Google Contacts.
            if (!TextUtils.isEmpty(title)) {
                title = title.trim();
            }
            // Empty system groups aren't shown in the nav drawer so it would be confusing to tell
            // the user that they already exist. Instead we allow them to create a duplicate
            // group in this case. This is how the web handles this case as well (it creates a
            // new non-system group if a new group with a title that matches a system group is
            // create).
            if (projection.isEmptyFFCGroup(data)) {
                continue;
            }
            mExistingGroups.add(title);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
    }

    private void showInputMethod(View view) {
        if (getActivity() == null) {
            return;
        }
        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, /* flags */ 0);
        }
    }

    private void hideInputMethod() {
        final InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(
                Context.INPUT_METHOD_SERVICE);
        if (imm != null && mGroupNameEditText != null) {
            imm.hideSoftInputFromWindow(mGroupNameEditText.getWindowToken(), /* flags */ 0);
        }
    }

    private Listener getListener() {
        if (mListener != null) {
            return mListener;
        } else if (getActivity() instanceof Listener) {
            return (Listener) getActivity();
        } else {
            return Listener.None;
        }
    }

    private String getGroupName() {
        return mGroupNameEditText == null || mGroupNameEditText.getText() == null
                ? null : mGroupNameEditText.getText().toString();
    }

    private String getSelection() {
        final StringBuilder builder = new StringBuilder();
        builder.append(Groups.ACCOUNT_NAME).append("=? AND ")
               .append(Groups.ACCOUNT_TYPE).append("=? AND ")
               .append(Groups.DELETED).append("=?");
        if (mAccount.dataSet != null) {
            builder.append(" AND ").append(Groups.DATA_SET).append("=?");
        }
        return builder.toString();
    }

    private String[] getSelectionArgs() {
        final int len = mAccount.dataSet == null ? 3 : 4;
        final String[] args = new String[len];
        args[0] = mAccount.name;
        args[1] = mAccount.type;
        args[2] = "0"; // Not deleted
        if (mAccount.dataSet != null) {
            args[3] = mAccount.dataSet;
        }
        return args;
    }
}
