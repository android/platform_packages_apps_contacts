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
 * limitations under the License
 */

package com.android.contacts.editor;

import android.accounts.Account;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.RawContacts;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.Toast;

import com.android.contacts.ContactSaveService;
import com.android.contacts.GroupMetaDataLoader;
import com.android.contacts.R;
import com.android.contacts.activities.ContactEditorAccountsChangedActivity;
import com.android.contacts.activities.ContactEditorActivity;
import com.android.contacts.activities.ContactEditorActivity.ContactEditor;
import com.android.contacts.activities.ContactSelectionActivity;
import com.android.contacts.activities.RequestPermissionsActivity;
import com.android.contacts.editor.AggregationSuggestionEngine.Suggestion;
import com.android.contacts.group.GroupUtil;
import com.android.contacts.list.UiIntentActions;
import com.android.contacts.logging.ScreenEvent.ScreenType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.Contact;
import com.android.contacts.model.ContactLoader;
import com.android.contacts.model.RawContact;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.RawContactDeltaList;
import com.android.contacts.model.RawContactModifier;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.AccountsLoader;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.quickcontact.InvisibleContactUtil;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.ContactDisplayUtils;
import com.android.contacts.util.ContactPhotoUtils;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.MaterialColorMapUtils;
import com.android.contacts.util.UiClosables;
import com.android.contactsbind.HelpUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Contact editor with only the most important fields displayed initially.
 */
public class ContactEditorFragment extends Fragment implements
        ContactEditor, SplitContactConfirmationDialogFragment.Listener,
        JoinContactConfirmationDialogFragment.Listener,
        AggregationSuggestionEngine.Listener, AggregationSuggestionView.Listener,
        CancelEditDialogFragment.Listener,
        RawContactEditorView.Listener, PhotoEditorView.Listener,
        AccountsLoader.AccountsListener {

    static final String TAG = "ContactEditor";

    private static final int LOADER_CONTACT = 1;
    private static final int LOADER_GROUPS = 2;
    private static final int LOADER_ACCOUNTS = 3;

    // How long to delay before attempting to restore focus and keyboard
    // visibility after view state has been restored (e.g. after rotation)
    // See b/77246197
    private static final long RESTORE_FOCUS_DELAY_MILLIS = 100L;

    private static final String KEY_PHOTO_RAW_CONTACT_ID = "photo_raw_contact_id";
    private static final String KEY_UPDATED_PHOTOS = "updated_photos";

    private static final List<String> VALID_INTENT_ACTIONS = new ArrayList<String>() {{
        add(Intent.ACTION_EDIT);
        add(Intent.ACTION_INSERT);
        add(ContactEditorActivity.ACTION_SAVE_COMPLETED);
    }};

    private static final String KEY_ACTION = "action";
    private static final String KEY_URI = "uri";
    private static final String KEY_AUTO_ADD_TO_DEFAULT_GROUP = "autoAddToDefaultGroup";
    private static final String KEY_DISABLE_DELETE_MENU_OPTION = "disableDeleteMenuOption";
    private static final String KEY_NEW_LOCAL_PROFILE = "newLocalProfile";
    private static final String KEY_MATERIAL_PALETTE = "materialPalette";
    private static final String KEY_ACCOUNT = "saveToAccount";
    private static final String KEY_VIEW_ID_GENERATOR = "viewidgenerator";

    private static final String KEY_RAW_CONTACTS = "rawContacts";

    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_STATUS = "status";

    private static final String KEY_HAS_NEW_CONTACT = "hasNewContact";
    private static final String KEY_NEW_CONTACT_READY = "newContactDataReady";

    private static final String KEY_IS_EDIT = "isEdit";
    private static final String KEY_EXISTING_CONTACT_READY = "existingContactDataReady";

    private static final String KEY_IS_USER_PROFILE = "isUserProfile";

    private static final String KEY_ENABLED = "enabled";

    // Aggregation PopupWindow
    private static final String KEY_AGGREGATION_SUGGESTIONS_RAW_CONTACT_ID =
            "aggregationSuggestionsRawContactId";

    // Join Activity
    private static final String KEY_CONTACT_ID_FOR_JOIN = "contactidforjoin";

    private static final String KEY_READ_ONLY_DISPLAY_NAME_ID = "readOnlyDisplayNameId";
    private static final String KEY_COPY_READ_ONLY_DISPLAY_NAME = "copyReadOnlyDisplayName";

    private static final String KEY_FOCUSED_VIEW_ID = "focusedViewId";

    private static final String KEY_RESTORE_SOFT_INPUT = "restoreSoftInput";

    protected static final int REQUEST_CODE_JOIN = 0;
    protected static final int REQUEST_CODE_ACCOUNTS_CHANGED = 1;

    /**
     * An intent extra that forces the editor to add the edited contact
     * to the default group (e.g. "My Contacts").
     */
    public static final String INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY = "addToDefaultDirectory";

    public static final String INTENT_EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";

    public static final String INTENT_EXTRA_DISABLE_DELETE_MENU_OPTION =
            "disableDeleteMenuOption";

    /**
     * Intent key to pass the photo palette primary color calculated by
     * {@link com.android.contacts.quickcontact.QuickContactActivity} to the editor.
     */
    public static final String INTENT_EXTRA_MATERIAL_PALETTE_PRIMARY_COLOR =
            "material_palette_primary_color";

    /**
     * Intent key to pass the photo palette secondary color calculated by
     * {@link com.android.contacts.quickcontact.QuickContactActivity} to the editor.
     */
    public static final String INTENT_EXTRA_MATERIAL_PALETTE_SECONDARY_COLOR =
            "material_palette_secondary_color";

    /**
     * Intent key to pass the ID of the photo to display on the editor.
     */
    // TODO: This can be cleaned up if we decide to not pass the photo id through
    // QuickContactActivity.
    public static final String INTENT_EXTRA_PHOTO_ID = "photo_id";

    /**
     * Intent key to pass the ID of the raw contact id that should be displayed in the full editor
     * by itself.
     */
    public static final String INTENT_EXTRA_RAW_CONTACT_ID_TO_DISPLAY_ALONE =
            "raw_contact_id_to_display_alone";

    /**
     * Intent extra to specify a {@link ContactEditor.SaveMode}.
     */
    public static final String SAVE_MODE_EXTRA_KEY = "saveMode";

    /**
     * Intent extra key for the contact ID to join the current contact to after saving.
     */
    public static final String JOIN_CONTACT_ID_EXTRA_KEY = "joinContactId";

    /**
     * Callbacks for Activities that host contact editors Fragments.
     */
    public interface Listener {

        /**
         * Contact was not found, so somehow close this fragment. This is raised after a contact
         * is removed via Menu/Delete
         */
        void onContactNotFound();

        /**
         * Contact was split, so we can close now.
         *
         * @param newLookupUri The lookup uri of the new contact that should be shown to the user.
         *                     The editor tries best to chose the most natural contact here.
         */
        void onContactSplit(Uri newLookupUri);

        /**
         * User has tapped Revert, close the fragment now.
         */
        void onReverted();

        /**
         * Contact was saved and the Fragment can now be closed safely.
         */
        void onSaveFinished(Intent resultIntent);

        /**
         * User switched to editing a different raw contact (a suggestion from the
         * aggregation engine).
         */
        void onEditOtherRawContactRequested(Uri contactLookupUri, long rawContactId,
                ArrayList<ContentValues> contentValues);

        /**
         * User has requested that contact be deleted.
         */
        void onDeleteRequested(Uri contactUri);
    }

    /**
     * Adapter for aggregation suggestions displayed in a PopupWindow when
     * editor fields change.
     */
    private static final class AggregationSuggestionAdapter extends BaseAdapter {
        private final LayoutInflater mLayoutInflater;
        private final AggregationSuggestionView.Listener mListener;
        private final List<AggregationSuggestionEngine.Suggestion> mSuggestions;

        public AggregationSuggestionAdapter(Activity activity,
                AggregationSuggestionView.Listener listener, List<Suggestion> suggestions) {
            mLayoutInflater = activity.getLayoutInflater();
            mListener = listener;
            mSuggestions = suggestions;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final Suggestion suggestion = (Suggestion) getItem(position);
            final AggregationSuggestionView suggestionView =
                    (AggregationSuggestionView) mLayoutInflater.inflate(
                            R.layout.aggregation_suggestions_item, null);
            suggestionView.setListener(mListener);
            suggestionView.bindSuggestion(suggestion);
            return suggestionView;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return mSuggestions.get(position);
        }

        @Override
        public int getCount() {
            return mSuggestions.size();
        }
    }

    protected Context mContext;
    protected Listener mListener;

    //
    // Views
    //
    protected LinearLayout mContent;
    protected ListPopupWindow mAggregationSuggestionPopup;

    //
    // Parameters passed in on {@link #load}
    //
    protected String mAction;
    protected Uri mLookupUri;
    protected Bundle mIntentExtras;
    protected boolean mAutoAddToDefaultGroup;
    protected boolean mDisableDeleteMenuOption;
    protected boolean mNewLocalProfile;
    protected MaterialColorMapUtils.MaterialPalette mMaterialPalette;

    //
    // Helpers
    //
    protected ContactEditorUtils mEditorUtils;
    protected RawContactDeltaComparator mComparator;
    protected ViewIdGenerator mViewIdGenerator;
    private AggregationSuggestionEngine mAggregationSuggestionEngine;

    //
    // Loaded data
    //
    // Used to store existing contact data so it can be re-applied during a rebind call,
    // i.e. account switch.
    protected Contact mContact;
    protected ImmutableList<RawContact> mRawContacts;
    protected Cursor mGroupMetaData;

    //
    // Editor state
    //
    protected RawContactDeltaList mState;
    protected int mStatus;
    protected long mRawContactIdToDisplayAlone = -1;

    // Whether to show the new contact blank form and if it's corresponding delta is ready.
    protected boolean mHasNewContact;
    protected AccountWithDataSet mAccountWithDataSet;
    protected List<AccountInfo> mWritableAccounts = Collections.emptyList();
    protected boolean mNewContactDataReady;
    protected boolean mNewContactAccountChanged;

    // Whether it's an edit of existing contact and if it's corresponding delta is ready.
    protected boolean mIsEdit;
    protected boolean mExistingContactDataReady;

    // Whether we are editing the "me" profile
    protected boolean mIsUserProfile;

    // Whether editor views and options menu items should be enabled
    private boolean mEnabled = true;

    // Aggregation PopupWindow
    private long mAggregationSuggestionsRawContactId;

    // Join Activity
    protected long mContactIdForJoin;

    // Used to pre-populate the editor with a display name when a user edits a read-only contact.
    protected long mReadOnlyDisplayNameId;
    protected boolean mCopyReadOnlyName;

    /**
     * The contact data loader listener.
     */
    protected final LoaderManager.LoaderCallbacks<Contact> mContactLoaderListener =
            new LoaderManager.LoaderCallbacks<Contact>() {

                protected long mLoaderStartTime;

                @Override
                public Loader<Contact> onCreateLoader(int id, Bundle args) {
                    mLoaderStartTime = SystemClock.elapsedRealtime();
                    return new ContactLoader(mContext, mLookupUri,
                            /* postViewNotification */ true,
                            /* loadGroupMetaData */ true);
                }

                @Override
                public void onLoadFinished(Loader<Contact> loader, Contact contact) {
                    final long loaderCurrentTime = SystemClock.elapsedRealtime();
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG,
                                "Time needed for loading: " + (loaderCurrentTime-mLoaderStartTime));
                    }
                    if (!contact.isLoaded()) {
                        // Item has been deleted. Close activity without saving again.
                        Log.i(TAG, "No contact found. Closing activity");
                        mStatus = Status.CLOSING;
                        if (mListener != null) mListener.onContactNotFound();
                        return;
                    }

                    mStatus = Status.EDITING;
                    mLookupUri = contact.getLookupUri();
                    final long setDataStartTime = SystemClock.elapsedRealtime();
                    setState(contact);
                    final long setDataEndTime = SystemClock.elapsedRealtime();
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Time needed for setting UI: "
                                + (setDataEndTime - setDataStartTime));
                    }
                }

                @Override
                public void onLoaderReset(Loader<Contact> loader) {
                }
            };

    /**
     * The groups meta data loader listener.
     */
    protected final LoaderManager.LoaderCallbacks<Cursor> mGroupsLoaderListener =
            new LoaderManager.LoaderCallbacks<Cursor>() {

                @Override
                public CursorLoader onCreateLoader(int id, Bundle args) {
                    return new GroupMetaDataLoader(mContext, ContactsContract.Groups.CONTENT_URI,
                            GroupUtil.ALL_GROUPS_SELECTION);
                }

                @Override
                public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
                    mGroupMetaData = data;
                    setGroupMetaData();
                }

                @Override
                public void onLoaderReset(Loader<Cursor> loader) {
                }
            };

    private long mPhotoRawContactId;
    private Bundle mUpdatedPhotos = new Bundle();

    private InputMethodManager inputMethodManager;

    @Override
    public Context getContext() {
        return getActivity();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mEditorUtils = ContactEditorUtils.create(mContext);
        mComparator = new RawContactDeltaComparator(mContext);
    }

    @Override
    public void onCreate(Bundle savedState) {
        if (savedState != null) {
            // Restore mUri before calling super.onCreate so that onInitializeLoaders
            // would already have a uri and an action to work with
            mAction = savedState.getString(KEY_ACTION);
            mLookupUri = savedState.getParcelable(KEY_URI);
        }

        super.onCreate(savedState);

        inputMethodManager =
            (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        if (savedState == null) {
            mViewIdGenerator = new ViewIdGenerator();

            // mState can still be null because it may not have have finished loading before
            // onSaveInstanceState was called.
            mState = new RawContactDeltaList();
        } else {
            mViewIdGenerator = savedState.getParcelable(KEY_VIEW_ID_GENERATOR);

            mAutoAddToDefaultGroup = savedState.getBoolean(KEY_AUTO_ADD_TO_DEFAULT_GROUP);
            mDisableDeleteMenuOption = savedState.getBoolean(KEY_DISABLE_DELETE_MENU_OPTION);
            mNewLocalProfile = savedState.getBoolean(KEY_NEW_LOCAL_PROFILE);
            mMaterialPalette = savedState.getParcelable(KEY_MATERIAL_PALETTE);
            mAccountWithDataSet = savedState.getParcelable(KEY_ACCOUNT);
            mRawContacts = ImmutableList.copyOf(savedState.<RawContact>getParcelableArrayList(
                    KEY_RAW_CONTACTS));
            // NOTE: mGroupMetaData is not saved/restored

            // Read state from savedState. No loading involved here
            mState = savedState.<RawContactDeltaList> getParcelable(KEY_EDIT_STATE);
            mStatus = savedState.getInt(KEY_STATUS);

            mHasNewContact = savedState.getBoolean(KEY_HAS_NEW_CONTACT);
            mNewContactDataReady = savedState.getBoolean(KEY_NEW_CONTACT_READY);

            mIsEdit = savedState.getBoolean(KEY_IS_EDIT);
            mExistingContactDataReady = savedState.getBoolean(KEY_EXISTING_CONTACT_READY);

            mIsUserProfile = savedState.getBoolean(KEY_IS_USER_PROFILE);

            mEnabled = savedState.getBoolean(KEY_ENABLED);

            // Aggregation PopupWindow
            mAggregationSuggestionsRawContactId = savedState.getLong(
                    KEY_AGGREGATION_SUGGESTIONS_RAW_CONTACT_ID);

            // Join Activity
            mContactIdForJoin = savedState.getLong(KEY_CONTACT_ID_FOR_JOIN);

            mReadOnlyDisplayNameId = savedState.getLong(KEY_READ_ONLY_DISPLAY_NAME_ID);
            mCopyReadOnlyName = savedState.getBoolean(KEY_COPY_READ_ONLY_DISPLAY_NAME, false);

            mPhotoRawContactId = savedState.getLong(KEY_PHOTO_RAW_CONTACT_ID);
            mUpdatedPhotos = savedState.getParcelable(KEY_UPDATED_PHOTOS);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        setHasOptionsMenu(true);

        final View view = inflater.inflate(
                R.layout.contact_editor_fragment, container, false);
        mContent = (LinearLayout) view.findViewById(R.id.raw_contacts_editor_view);
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        validateAction(mAction);

        if (mState.isEmpty()) {
            // The delta list may not have finished loading before orientation change happens.
            // In this case, there will be a saved state but deltas will be missing.  Reload from
            // database.
            if (Intent.ACTION_EDIT.equals(mAction)) {
                // Either
                // 1) orientation change but load never finished.
                // 2) not an orientation change so data needs to be loaded for first time.
                getLoaderManager().initLoader(LOADER_CONTACT, null, mContactLoaderListener);
                getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupsLoaderListener);
            }
        } else {
            // Orientation change, we already have mState, it was loaded by onCreate
            bindEditors();
        }

        // Handle initial actions only when existing state missing
        if (savedInstanceState == null) {
            if (mIntentExtras != null) {
                final Account account = mIntentExtras == null ? null :
                        (Account) mIntentExtras.getParcelable(Intents.Insert.EXTRA_ACCOUNT);
                final String dataSet = mIntentExtras == null ? null :
                        mIntentExtras.getString(Intents.Insert.EXTRA_DATA_SET);
                mAccountWithDataSet = account != null
                        ? new AccountWithDataSet(account.name, account.type, dataSet)
                        : mIntentExtras.<AccountWithDataSet>getParcelable(
                                ContactEditorActivity.EXTRA_ACCOUNT_WITH_DATA_SET);
            }

            if (Intent.ACTION_EDIT.equals(mAction)) {
                mIsEdit = true;
            } else if (Intent.ACTION_INSERT.equals(mAction)) {
                mHasNewContact = true;
                if (mAccountWithDataSet != null) {
                    createContact(mAccountWithDataSet);
                } // else wait for accounts to be loaded
            }
        }

        if (mHasNewContact) {
            AccountsLoader.loadAccounts(this, LOADER_ACCOUNTS, AccountTypeManager.writableFilter());
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState == null) {
            return;
        }
        maybeRestoreFocus(savedInstanceState);
    }

    /**
     * Checks if the requested action is valid.
     *
     * @param action The action to test.
     * @throws IllegalArgumentException when the action is invalid.
     */
    private static void validateAction(String action) {
        if (VALID_INTENT_ACTIONS.contains(action)) {
            return;
        }
        throw new IllegalArgumentException(
                "Unknown action " + action + "; Supported actions: " + VALID_INTENT_ACTIONS);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_ACTION, mAction);
        outState.putParcelable(KEY_URI, mLookupUri);
        outState.putBoolean(KEY_AUTO_ADD_TO_DEFAULT_GROUP, mAutoAddToDefaultGroup);
        outState.putBoolean(KEY_DISABLE_DELETE_MENU_OPTION, mDisableDeleteMenuOption);
        outState.putBoolean(KEY_NEW_LOCAL_PROFILE, mNewLocalProfile);
        if (mMaterialPalette != null) {
            outState.putParcelable(KEY_MATERIAL_PALETTE, mMaterialPalette);
        }
        outState.putParcelable(KEY_VIEW_ID_GENERATOR, mViewIdGenerator);

        outState.putParcelableArrayList(KEY_RAW_CONTACTS, mRawContacts == null ?
                Lists.<RawContact>newArrayList() : Lists.newArrayList(mRawContacts));
        // NOTE: mGroupMetaData is not saved

        outState.putParcelable(KEY_EDIT_STATE, mState);
        outState.putInt(KEY_STATUS, mStatus);
        outState.putBoolean(KEY_HAS_NEW_CONTACT, mHasNewContact);
        outState.putBoolean(KEY_NEW_CONTACT_READY, mNewContactDataReady);
        outState.putBoolean(KEY_IS_EDIT, mIsEdit);
        outState.putBoolean(KEY_EXISTING_CONTACT_READY, mExistingContactDataReady);
        outState.putParcelable(KEY_ACCOUNT, mAccountWithDataSet);
        outState.putBoolean(KEY_IS_USER_PROFILE, mIsUserProfile);

        outState.putBoolean(KEY_ENABLED, mEnabled);

        // Aggregation PopupWindow
        outState.putLong(KEY_AGGREGATION_SUGGESTIONS_RAW_CONTACT_ID,
                mAggregationSuggestionsRawContactId);

        // Join Activity
        outState.putLong(KEY_CONTACT_ID_FOR_JOIN, mContactIdForJoin);

        outState.putLong(KEY_READ_ONLY_DISPLAY_NAME_ID, mReadOnlyDisplayNameId);
        outState.putBoolean(KEY_COPY_READ_ONLY_DISPLAY_NAME, mCopyReadOnlyName);

        outState.putLong(KEY_PHOTO_RAW_CONTACT_ID, mPhotoRawContactId);
        outState.putParcelable(KEY_UPDATED_PHOTOS, mUpdatedPhotos);

        // For b/77246197
        View focusedView = getView() == null ? null : getView().findFocus();
        if (focusedView != null) {
            outState.putInt(KEY_FOCUSED_VIEW_ID, focusedView.getId());
            outState.putBoolean(KEY_RESTORE_SOFT_INPUT, inputMethodManager.isActive(focusedView));
        }

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        super.onStop();
        UiClosables.closeQuietly(mAggregationSuggestionPopup);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAggregationSuggestionEngine != null) {
            mAggregationSuggestionEngine.quit();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_JOIN: {
                // Ignore failed requests
                if (resultCode != Activity.RESULT_OK) return;
                if (data != null) {
                    final long contactId = ContentUris.parseId(data.getData());
                    if (hasPendingChanges()) {
                        // Ask the user if they want to save changes before doing the join
                        JoinContactConfirmationDialogFragment.show(this, contactId);
                    } else {
                        // Do the join immediately
                        joinAggregate(contactId);
                    }
                }
                break;
            }
            case REQUEST_CODE_ACCOUNTS_CHANGED: {
                // Bail if the account selector was not successful.
                if (resultCode != Activity.RESULT_OK || data == null ||
                        !data.hasExtra(Intents.Insert.EXTRA_ACCOUNT)) {
                    if (mListener != null) {
                        mListener.onReverted();
                    }
                    return;
                }
                AccountWithDataSet account = data.getParcelableExtra(
                        Intents.Insert.EXTRA_ACCOUNT);
                createContact(account);
                break;
            }
        }
    }

    @Override
    public void onAccountsLoaded(List<AccountInfo> data) {
        mWritableAccounts = data;
        // The user may need to select a new account to save to
        if (mAccountWithDataSet == null && mHasNewContact) {
            selectAccountAndCreateContact();
        }

        final RawContactEditorView view = getContent();
        if (view == null) {
            return;
        }
        view.setAccounts(data);
        if (mAccountWithDataSet == null && view.getCurrentRawContactDelta() == null) {
            return;
        }

        final AccountWithDataSet account = mAccountWithDataSet != null
                ? mAccountWithDataSet
                : view.getCurrentRawContactDelta().getAccountWithDataSet();

        // The current account was removed
        if (!AccountInfo.contains(data, account) && !data.isEmpty()) {
            if (isReadyToBindEditors()) {
                onRebindEditorsForNewContact(getContent().getCurrentRawContactDelta(),
                        account, data.get(0).getAccount());
            } else {
                mAccountWithDataSet = data.get(0).getAccount();
            }
        }
    }

    //
    // Options menu
    //

    @Override
    public void onCreateOptionsMenu(Menu menu, final MenuInflater inflater) {
        inflater.inflate(R.menu.edit_contact, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // This supports the keyboard shortcut to save changes to a contact but shouldn't be visible
        // because the custom action bar contains the "save" button now (not the overflow menu).
        // TODO: Find a better way to handle shortcuts, i.e. onKeyDown()?
        final MenuItem saveMenu = menu.findItem(R.id.menu_save);
        final MenuItem splitMenu = menu.findItem(R.id.menu_split);
        final MenuItem joinMenu = menu.findItem(R.id.menu_join);
        final MenuItem deleteMenu = menu.findItem(R.id.menu_delete);

        // TODO: b/30771904, b/31827701, temporarily disable these items until we get them to work
        // on a raw contact level.
        joinMenu.setVisible(false);
        splitMenu.setVisible(false);
        deleteMenu.setVisible(false);
        // Save menu is invisible when there's only one read only contact in the editor.
        saveMenu.setVisible(!isEditingReadOnlyRawContact());
        if (saveMenu.isVisible()) {
            // Since we're using a custom action layout we have to manually hook up the handler.
            saveMenu.getActionView().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onOptionsItemSelected(saveMenu);
                }
            });
        }

        final MenuItem helpMenu = menu.findItem(R.id.menu_help);
        helpMenu.setVisible(HelpUtils.isHelpAndFeedbackAvailable());

        int size = menu.size();
        for (int i = 0; i < size; i++) {
            menu.getItem(i).setEnabled(mEnabled);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return revert();
        }

        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            // If we no longer are attached to a running activity want to
            // drain this event.
            return true;
        }

        final int id = item.getItemId();
        if (id == R.id.menu_save) {
            return save(SaveMode.CLOSE);
        } else if (id == R.id.menu_delete) {
            if (mListener != null) mListener.onDeleteRequested(mLookupUri);
            return true;
        } else if (id == R.id.menu_split) {
            return doSplitContactAction();
        } else if (id == R.id.menu_join) {
            return doJoinContactAction();
        } else if (id == R.id.menu_help) {
            HelpUtils.launchHelpAndFeedbackForContactScreen(getActivity());
            return true;
        }

        return false;
    }

    @Override
    public boolean revert() {
        if (mState.isEmpty() || !hasPendingChanges()) {
            onCancelEditConfirmed();
        } else {
            CancelEditDialogFragment.show(this);
        }
        return true;
    }

    @Override
    public void onCancelEditConfirmed() {
        // When this Fragment is closed we don't want it to auto-save
        mStatus = Status.CLOSING;
        if (mListener != null) {
            mListener.onReverted();
        }
    }

    @Override
    public void onSplitContactConfirmed(boolean hasPendingChanges) {
        if (mState.isEmpty()) {
            // This may happen when this Fragment is recreated by the system during users
            // confirming the split action (and thus this method is called just before onCreate()),
            // for example.
            Log.e(TAG, "mState became null during the user's confirming split action. " +
                    "Cannot perform the save action.");
            return;
        }

        if (!hasPendingChanges && mHasNewContact) {
            // If the user didn't add anything new, we don't want to split out the newly created
            // raw contact into a name-only contact so remove them.
            final Iterator<RawContactDelta> iterator = mState.iterator();
            while (iterator.hasNext()) {
                final RawContactDelta rawContactDelta = iterator.next();
                if (rawContactDelta.getRawContactId() < 0) {
                    iterator.remove();
                }
            }
        }
        mState.markRawContactsForSplitting();
        save(SaveMode.SPLIT);
    }

    @Override
    public void onSplitContactCanceled() {}

    private boolean doSplitContactAction() {
        if (!hasValidState()) return false;

        SplitContactConfirmationDialogFragment.show(this, hasPendingChanges());
        return true;
    }

    private boolean doJoinContactAction() {
        if (!hasValidState() || mLookupUri == null) {
            return false;
        }

        // If we just started creating a new contact and haven't added any data, it's too
        // early to do a join
        if (mState.size() == 1 && mState.get(0).isContactInsert()
                && !hasPendingChanges()) {
            Toast.makeText(mContext, R.string.toast_join_with_empty_contact,
                    Toast.LENGTH_LONG).show();
            return true;
        }

        showJoinAggregateActivity(mLookupUri);
        return true;
    }

    @Override
    public void onJoinContactConfirmed(long joinContactId) {
        doSaveAction(SaveMode.JOIN, joinContactId);
    }

    @Override
    public boolean save(int saveMode) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            return false;
        }

        // If we are about to close the editor - there is no need to refresh the data
        if (saveMode == SaveMode.CLOSE || saveMode == SaveMode.EDITOR
                || saveMode == SaveMode.SPLIT) {
            getLoaderManager().destroyLoader(LOADER_CONTACT);
        }

        mStatus = Status.SAVING;

        if (!hasPendingChanges()) {
            if (mLookupUri == null && saveMode == SaveMode.RELOAD) {
                // We don't have anything to save and there isn't even an existing contact yet.
                // Nothing to do, simply go back to editing mode
                mStatus = Status.EDITING;
                return true;
            }
            onSaveCompleted(/* hadChanges =*/ false, saveMode,
                    /* saveSucceeded =*/ mLookupUri != null, mLookupUri, /* joinContactId =*/ null);
            return true;
        }

        setEnabled(false);
        hideSoftKeyboard();
        return doSaveAction(saveMode, /* joinContactId */ null);
    }

    //
    // State accessor methods
    //

    /**
     * Check if our internal {@link #mState} is valid, usually checked before
     * performing user actions.
     */
    private boolean hasValidState() {
        return mState.size() > 0;
    }

    private boolean isEditingUserProfile() {
        return mNewLocalProfile || mIsUserProfile;
    }

    /**
     * Whether the contact being edited is composed of read-only raw contacts
     * aggregated with a newly created writable raw contact.
     */
    private boolean isEditingReadOnlyRawContactWithNewContact() {
        return mHasNewContact && mState.size() > 1;
    }

    /**
     * @return true if the single raw contact we're looking at is read-only.
     */
    private boolean isEditingReadOnlyRawContact() {
        return hasValidState() && mRawContactIdToDisplayAlone > 0
                && !mState.getByRawContactId(mRawContactIdToDisplayAlone)
                        .getAccountType(AccountTypeManager.getInstance(mContext))
                                .areContactsWritable();
    }

    /**
     * Return true if there are any edits to the current contact which need to
     * be saved.
     */
    private boolean hasPendingRawContactChanges(Set<String> excludedMimeTypes) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        return RawContactModifier.hasChanges(mState, accountTypes, excludedMimeTypes);
    }

    /**
     * Determines if changes were made in the editor that need to be saved, while taking into
     * account that name changes are not real for read-only contacts.
     * See go/editing-read-only-contacts
     */
    private boolean hasPendingChanges() {
        if (isEditingReadOnlyRawContactWithNewContact()) {
            // We created a new raw contact delta with a default display name.
            // We must test for pending changes while ignoring the default display name.
            final RawContactDelta beforeRawContactDelta = mState
                    .getByRawContactId(mReadOnlyDisplayNameId);
            final ValuesDelta beforeDelta = beforeRawContactDelta == null ? null :
                  beforeRawContactDelta.getSuperPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
            final ValuesDelta pendingDelta = mState
                    .getSuperPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
            if (structuredNamesAreEqual(beforeDelta, pendingDelta)) {
                final Set<String> excludedMimeTypes = new HashSet<>();
                excludedMimeTypes.add(StructuredName.CONTENT_ITEM_TYPE);
                return hasPendingRawContactChanges(excludedMimeTypes);
            }
            return true;
        }
        return hasPendingRawContactChanges(/* excludedMimeTypes =*/ null);
    }

    /**
     * Compares the two {@link ValuesDelta} to see if the structured name is changed. We made a copy
     * of a read only delta and now we want to check if the copied delta has changes.
     *
     * @param before original {@link ValuesDelta}
     * @param after copied {@link ValuesDelta}
     * @return true if the copied {@link ValuesDelta} has all the same values in the structured
     * name fields as the original.
     */
    private boolean structuredNamesAreEqual(ValuesDelta before, ValuesDelta after) {
        if (before == after) return true;
        if (before == null || after == null) return false;
        final ContentValues original = before.getBefore();
        final ContentValues pending = after.getAfter();
        if (original != null && pending != null) {
            final String beforeDisplayName = original.getAsString(StructuredName.DISPLAY_NAME);
            final String afterDisplayName = pending.getAsString(StructuredName.DISPLAY_NAME);
            if (!TextUtils.equals(beforeDisplayName, afterDisplayName)) return false;

            final String beforePrefix = original.getAsString(StructuredName.PREFIX);
            final String afterPrefix = pending.getAsString(StructuredName.PREFIX);
            if (!TextUtils.equals(beforePrefix, afterPrefix)) return false;

            final String beforeFirstName = original.getAsString(StructuredName.GIVEN_NAME);
            final String afterFirstName = pending.getAsString(StructuredName.GIVEN_NAME);
            if (!TextUtils.equals(beforeFirstName, afterFirstName)) return false;

            final String beforeMiddleName = original.getAsString(StructuredName.MIDDLE_NAME);
            final String afterMiddleName = pending.getAsString(StructuredName.MIDDLE_NAME);
            if (!TextUtils.equals(beforeMiddleName, afterMiddleName)) return false;

            final String beforeLastName = original.getAsString(StructuredName.FAMILY_NAME);
            final String afterLastName = pending.getAsString(StructuredName.FAMILY_NAME);
            if (!TextUtils.equals(beforeLastName, afterLastName)) return false;

            final String beforeSuffix = original.getAsString(StructuredName.SUFFIX);
            final String afterSuffix = pending.getAsString(StructuredName.SUFFIX);
            return TextUtils.equals(beforeSuffix, afterSuffix);
        }
        return false;
    }

    //
    // Account creation
    //

    private void selectAccountAndCreateContact() {
        Preconditions.checkNotNull(mWritableAccounts, "Accounts must be loaded first");
        // If this is a local profile, then skip the logic about showing the accounts changed
        // activity and create a phone-local contact.
        if (mNewLocalProfile) {
            createContact(null);
            return;
        }

        final List<AccountWithDataSet> accounts = AccountInfo.extractAccounts(mWritableAccounts);
        // If there is no default account or the accounts have changed such that we need to
        // prompt the user again, then launch the account prompt.
        if (mEditorUtils.shouldShowAccountChangedNotification(accounts)) {
            Intent intent = new Intent(mContext, ContactEditorAccountsChangedActivity.class);
            // Prevent a second instance from being started on rotates
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            mStatus = Status.SUB_ACTIVITY;
            startActivityForResult(intent, REQUEST_CODE_ACCOUNTS_CHANGED);
        } else {
            // Make sure the default account is automatically set if there is only one non-device
            // account.
            mEditorUtils.maybeUpdateDefaultAccount(accounts);
            // Otherwise, there should be a default account. Then either create a local contact
            // (if default account is null) or create a contact with the specified account.
            AccountWithDataSet defaultAccount = mEditorUtils.getOnlyOrDefaultAccount(accounts);
            createContact(defaultAccount);
        }
    }

    /**
     * Shows account creation screen associated with a given account.
     *
     * @param account may be null to signal a device-local contact should be created.
     */
    private void createContact(AccountWithDataSet account) {
        final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        final AccountType accountType = accountTypes.getAccountTypeForAccount(account);

        setStateForNewContact(account, accountType, isEditingUserProfile());
    }

    //
    // Data binding
    //

    private void setState(Contact contact) {
        // If we have already loaded data, we do not want to change it here to not confuse the user
        if (!mState.isEmpty()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Ignoring background change. This will have to be rebased later");
            }
            return;
        }
        mContact = contact;
        mRawContacts = contact.getRawContacts();

        // Check for writable raw contacts.  If there are none, then we need to create one so user
        // can edit.  For the user profile case, there is already an editable contact.
        if (!contact.isUserProfile() && !contact.isWritableContact(mContext)) {
            mHasNewContact = true;
            mReadOnlyDisplayNameId = contact.getNameRawContactId();
            mCopyReadOnlyName = true;
            // This is potentially an asynchronous call and will add deltas to list.
            selectAccountAndCreateContact();
        } else {
            mHasNewContact = false;
        }

        setStateForExistingContact(contact.isUserProfile(), mRawContacts);
        if (mAutoAddToDefaultGroup
                && InvisibleContactUtil.isInvisibleAndAddable(contact, getContext())) {
            InvisibleContactUtil.markAddToDefaultGroup(contact, mState, getContext());
        }
    }

    /**
     * Prepare {@link #mState} for a newly created phone-local contact.
     */
    private void setStateForNewContact(AccountWithDataSet account, AccountType accountType,
            boolean isUserProfile) {
        setStateForNewContact(account, accountType, /* oldState =*/ null,
                /* oldAccountType =*/ null, isUserProfile);
    }

    /**
     * Prepare {@link #mState} for a newly created phone-local contact, migrating the state
     * specified by oldState and oldAccountType.
     */
    private void setStateForNewContact(AccountWithDataSet account, AccountType accountType,
            RawContactDelta oldState, AccountType oldAccountType, boolean isUserProfile) {
        mStatus = Status.EDITING;
        mAccountWithDataSet = account;
        mState.add(createNewRawContactDelta(account, accountType, oldState, oldAccountType));
        mIsUserProfile = isUserProfile;
        mNewContactDataReady = true;
        bindEditors();
    }

    /**
     * Returns a {@link RawContactDelta} for a new contact suitable for addition into
     * {@link #mState}.
     *
     * If oldState and oldAccountType are specified, the state specified by those parameters
     * is migrated to the result {@link RawContactDelta}.
     */
    private RawContactDelta createNewRawContactDelta(AccountWithDataSet account,
            AccountType accountType, RawContactDelta oldState, AccountType oldAccountType) {
        final RawContact rawContact = new RawContact();
        if (account != null) {
            rawContact.setAccount(account);
        } else {
            rawContact.setAccountToLocal();
        }

        final RawContactDelta result = new RawContactDelta(
                ValuesDelta.fromAfter(rawContact.getValues()));
        if (oldState == null) {
            // Parse any values from incoming intent
            RawContactModifier.parseExtras(mContext, accountType, result, mIntentExtras);
        } else {
            RawContactModifier.migrateStateForNewContact(
                    mContext, oldState, result, oldAccountType, accountType);
        }

        // Ensure we have some default fields (if the account type does not support a field,
        // ensureKind will not add it, so it is safe to add e.g. Event)
        RawContactModifier.ensureKindExists(result, accountType, StructuredName.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(result, accountType, Phone.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(result, accountType, Email.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(result, accountType, Organization.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(result, accountType, Event.CONTENT_ITEM_TYPE);
        RawContactModifier.ensureKindExists(result, accountType,
                StructuredPostal.CONTENT_ITEM_TYPE);

        // Set the correct URI for saving the contact as a profile
        if (mNewLocalProfile) {
            result.setProfileQueryUri();
        }

        return result;
    }

    /**
     * Prepare {@link #mState} for an existing contact.
     */
    private void setStateForExistingContact(boolean isUserProfile,
            ImmutableList<RawContact> rawContacts) {
        setEnabled(true);

        mState.addAll(rawContacts.iterator());
        setIntentExtras(mIntentExtras);
        mIntentExtras = null;

        // For user profile, change the contacts query URI
        mIsUserProfile = isUserProfile;
        boolean localProfileExists = false;

        if (mIsUserProfile) {
            for (RawContactDelta rawContactDelta : mState) {
                // For profile contacts, we need a different query URI
                rawContactDelta.setProfileQueryUri();
                // Try to find a local profile contact
                if (rawContactDelta.getValues().getAsString(RawContacts.ACCOUNT_TYPE) == null) {
                    localProfileExists = true;
                }
            }
            // Editor should always present a local profile for editing
            // TODO(wjang): Need to figure out when this case comes up.  We can't do this if we're
            // going to prune all but the one raw contact that we're trying to display by itself.
            if (!localProfileExists && mRawContactIdToDisplayAlone <= 0) {
                mState.add(createLocalRawContactDelta());
            }
        }
        mExistingContactDataReady = true;
        bindEditors();
    }

    /**
     * Set the enabled state of editors.
     */
    private void setEnabled(boolean enabled) {
        if (mEnabled != enabled) {
            mEnabled = enabled;

            // Enable/disable editors
            if (mContent != null) {
                int count = mContent.getChildCount();
                for (int i = 0; i < count; i++) {
                    mContent.getChildAt(i).setEnabled(enabled);
                }
            }

            // Maybe invalidate the options menu
            final Activity activity = getActivity();
            if (activity != null) activity.invalidateOptionsMenu();
        }
    }

    /**
     * Returns a {@link RawContactDelta} for a local contact suitable for addition into
     * {@link #mState}.
     */
    private static RawContactDelta createLocalRawContactDelta() {
        final RawContact rawContact = new RawContact();
        rawContact.setAccountToLocal();

        final RawContactDelta result = new RawContactDelta(
                ValuesDelta.fromAfter(rawContact.getValues()));
        result.setProfileQueryUri();

        return result;
    }

    private void copyReadOnlyName() {
        // We should only ever be doing this if we're creating a new writable contact to attach to
        // a read only contact.
        if (!isEditingReadOnlyRawContactWithNewContact()) {
            return;
        }
        final int writableIndex = mState.indexOfFirstWritableRawContact(getContext());
        final RawContactDelta writable = mState.get(writableIndex);
        final RawContactDelta readOnly = mState.getByRawContactId(mContact.getNameRawContactId());
        final ValuesDelta writeNameDelta = writable
                .getSuperPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
        final ValuesDelta readNameDelta = readOnly
                .getSuperPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);
        mCopyReadOnlyName = false;
        if (writeNameDelta == null || readNameDelta == null) {
            return;
        }
        writeNameDelta.copyStructuredNameFieldsFrom(readNameDelta);
    }

    /**
     * Bind editors using {@link #mState} and other members initialized from the loaded (or new)
     * Contact.
     */
    protected void bindEditors() {
        if (!isReadyToBindEditors()) {
            return;
        }

        // Add input fields for the loaded Contact
        final RawContactEditorView editorView = getContent();
        editorView.setListener(this);
        if (mCopyReadOnlyName) {
            copyReadOnlyName();
        }
        editorView.setState(mState, mMaterialPalette, mViewIdGenerator,
                mHasNewContact, mIsUserProfile, mAccountWithDataSet,
                mRawContactIdToDisplayAlone);
        if (isEditingReadOnlyRawContact()) {
            final Toolbar toolbar = getEditorActivity().getToolbar();
            if (toolbar != null) {
                toolbar.setTitle(R.string.contact_editor_title_read_only_contact);
                // Set activity title for Talkback
                getEditorActivity().setTitle(R.string.contact_editor_title_read_only_contact);
                toolbar.setNavigationIcon(R.drawable.quantum_ic_arrow_back_vd_theme_24);
                toolbar.setNavigationContentDescription(R.string.back_arrow_content_description);
                toolbar.getNavigationIcon().setAutoMirrored(true);
            }
        }

        // Set up the photo widget
        editorView.setPhotoListener(this);
        mPhotoRawContactId = editorView.getPhotoRawContactId();
        // If there is an updated full resolution photo apply it now, this will be the case if
        // the user selects or takes a new photo, then rotates the device.
        final Uri uri = (Uri) mUpdatedPhotos.get(String.valueOf(mPhotoRawContactId));
        if (uri != null) {
            editorView.setFullSizePhoto(uri);
        }
        final StructuredNameEditorView nameEditor = editorView.getNameEditorView();
        final TextFieldsEditorView phoneticNameEditor = editorView.getPhoneticEditorView();
        final boolean useJapaneseOrder = 
                       Locale.JAPANESE.getLanguage().equals(Locale.getDefault().getLanguage());
        if (useJapaneseOrder && nameEditor != null && phoneticNameEditor != null) {
            nameEditor.setPhoneticView(phoneticNameEditor);
        }

        // The editor is ready now so make it visible
        editorView.setEnabled(mEnabled);
        editorView.setVisibility(View.VISIBLE);

        // Refresh the ActionBar as the visibility of the join command
        // Activity can be null if we have been detached from the Activity.
        invalidateOptionsMenu();
    }

    /**
     * Invalidates the options menu if we are still associated with an Activity.
     */
    private void invalidateOptionsMenu() {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.invalidateOptionsMenu();
        }
    }

    private boolean isReadyToBindEditors() {
        if (mState.isEmpty()) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "No data to bind editors");
            }
            return false;
        }
        if (mIsEdit && !mExistingContactDataReady) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Existing contact data is not ready to bind editors.");
            }
            return false;
        }
        if (mHasNewContact && !mNewContactDataReady) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "New contact data is not ready to bind editors.");
            }
            return false;
        }
        // Don't attempt to bind anything if we have no permissions.
        return RequestPermissionsActivity.hasRequiredPermissions(mContext);
    }

    /**
     * Removes a current editor ({@link #mState}) and rebinds new editor for a new account.
     * Some of old data are reused with new restriction enforced by the new account.
     *
     * @param oldState Old data being edited.
     * @param oldAccount Old account associated with oldState.
     * @param newAccount New account to be used.
     */
    private void rebindEditorsForNewContact(
            RawContactDelta oldState, AccountWithDataSet oldAccount,
            AccountWithDataSet newAccount) {
        AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
        AccountType oldAccountType = accountTypes.getAccountTypeForAccount(oldAccount);
        AccountType newAccountType = accountTypes.getAccountTypeForAccount(newAccount);

        mExistingContactDataReady = false;
        mNewContactDataReady = false;
        mState = new RawContactDeltaList();
        setStateForNewContact(newAccount, newAccountType, oldState, oldAccountType,
                isEditingUserProfile());
        if (mIsEdit) {
            setStateForExistingContact(isEditingUserProfile(), mRawContacts);
        }
    }

    //
    // ContactEditor
    //

    @Override
    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public void load(String action, Uri lookupUri, Bundle intentExtras) {
        mAction = action;
        mLookupUri = lookupUri;
        mIntentExtras = intentExtras;

        if (mIntentExtras != null) {
            mAutoAddToDefaultGroup =
                    mIntentExtras.containsKey(INTENT_EXTRA_ADD_TO_DEFAULT_DIRECTORY);
            mNewLocalProfile =
                    mIntentExtras.getBoolean(INTENT_EXTRA_NEW_LOCAL_PROFILE);
            mDisableDeleteMenuOption =
                    mIntentExtras.getBoolean(INTENT_EXTRA_DISABLE_DELETE_MENU_OPTION);
            if (mIntentExtras.containsKey(INTENT_EXTRA_MATERIAL_PALETTE_PRIMARY_COLOR)
                    && mIntentExtras.containsKey(INTENT_EXTRA_MATERIAL_PALETTE_SECONDARY_COLOR)) {
                mMaterialPalette = new MaterialColorMapUtils.MaterialPalette(
                        mIntentExtras.getInt(INTENT_EXTRA_MATERIAL_PALETTE_PRIMARY_COLOR),
                        mIntentExtras.getInt(INTENT_EXTRA_MATERIAL_PALETTE_SECONDARY_COLOR));
            }
            mRawContactIdToDisplayAlone = mIntentExtras
                    .getLong(INTENT_EXTRA_RAW_CONTACT_ID_TO_DISPLAY_ALONE);
        }
    }

    @Override
    public void setIntentExtras(Bundle extras) {
        getContent().setIntentExtras(extras);
    }

    @Override
    public void onJoinCompleted(Uri uri) {
        onSaveCompleted(false, SaveMode.RELOAD, uri != null, uri, /* joinContactId */ null);
    }


    private String getNameToDisplay(Uri contactUri) {
        // The contact has been deleted or the uri is otherwise no longer right.
        if (contactUri == null) {
            return null;
        }
        final ContentResolver resolver = mContext.getContentResolver();
        final Cursor cursor = resolver.query(contactUri, new String[]{
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE}, null, null, null);

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    final String displayName = cursor.getString(0);
                    final String displayNameAlt = cursor.getString(1);
                    cursor.close();
                    return ContactDisplayUtils.getPreferredDisplayName(displayName, displayNameAlt,
                            new ContactsPreferences(mContext));
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }


    @Override
    public void onSaveCompleted(boolean hadChanges, int saveMode, boolean saveSucceeded,
            Uri contactLookupUri, Long joinContactId) {
        if (hadChanges) {
            if (saveSucceeded) {
                switch (saveMode) {
                    case SaveMode.JOIN:
                        break;
                    case SaveMode.SPLIT:
                        Toast.makeText(mContext, R.string.contactUnlinkedToast, Toast.LENGTH_SHORT)
                                .show();
                        break;
                    default:
                        final String displayName = getNameToDisplay(contactLookupUri);
                        final String toastMessage;
                        if (!TextUtils.isEmpty(displayName)) {
                            toastMessage = getResources().getString(
                                    R.string.contactSavedNamedToast, displayName);
                        } else {
                            toastMessage = getResources().getString(R.string.contactSavedToast);
                        }
                        Toast.makeText(mContext, toastMessage, Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(mContext, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }
        }
        switch (saveMode) {
            case SaveMode.CLOSE: {
                final Intent resultIntent;
                if (saveSucceeded && contactLookupUri != null) {
                    final Uri lookupUri = ContactEditorUtils.maybeConvertToLegacyLookupUri(
                            mContext, contactLookupUri, mLookupUri);
                    resultIntent = ImplicitIntentsUtil.composeQuickContactIntent(
                            mContext, lookupUri, ScreenType.EDITOR);
                    resultIntent.putExtra(QuickContactActivity.EXTRA_CONTACT_EDITED, true);
                } else {
                    resultIntent = null;
                }
                // It is already saved, so prevent it from being saved again
                mStatus = Status.CLOSING;
                if (mListener != null) mListener.onSaveFinished(resultIntent);
                break;
            }
            case SaveMode.EDITOR: {
                // It is already saved, so prevent it from being saved again
                mStatus = Status.CLOSING;
                if (mListener != null) mListener.onSaveFinished(/* resultIntent= */ null);
                break;
            }
            case SaveMode.JOIN:
                if (saveSucceeded && contactLookupUri != null && joinContactId != null) {
                    joinAggregate(joinContactId);
                }
                break;
            case SaveMode.RELOAD:
                if (saveSucceeded && contactLookupUri != null) {
                    // If this was in INSERT, we are changing into an EDIT now.
                    // If it already was an EDIT, we are changing to the new Uri now
                    mState = new RawContactDeltaList();
                    load(Intent.ACTION_EDIT, contactLookupUri, null);
                    mStatus = Status.LOADING;
                    getLoaderManager().restartLoader(LOADER_CONTACT, null, mContactLoaderListener);
                }
                break;

            case SaveMode.SPLIT:
                mStatus = Status.CLOSING;
                if (mListener != null) {
                    mListener.onContactSplit(contactLookupUri);
                } else if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "No listener registered, can not call onSplitFinished");
                }
                break;
        }
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     *
     * @param contactLookupUri the fresh URI for the currently edited contact (after saving it)
     */
    private void showJoinAggregateActivity(Uri contactLookupUri) {
        if (contactLookupUri == null || !isAdded()) {
            return;
        }

        mContactIdForJoin = ContentUris.parseId(contactLookupUri);
        final Intent intent = new Intent(mContext, ContactSelectionActivity.class);
        intent.setAction(UiIntentActions.PICK_JOIN_CONTACT_ACTION);
        intent.putExtra(UiIntentActions.TARGET_CONTACT_ID_EXTRA_KEY, mContactIdForJoin);
        startActivityForResult(intent, REQUEST_CODE_JOIN);
    }

    //
    // Aggregation PopupWindow
    //

    /**
     * Triggers an asynchronous search for aggregation suggestions.
     */
    protected void acquireAggregationSuggestions(Context context,
            long rawContactId, ValuesDelta valuesDelta) {
        mAggregationSuggestionsRawContactId = rawContactId;

        if (mAggregationSuggestionEngine == null) {
            mAggregationSuggestionEngine = new AggregationSuggestionEngine(context);
            mAggregationSuggestionEngine.setListener(this);
            mAggregationSuggestionEngine.start();
        }

        mAggregationSuggestionEngine.setContactId(getContactId());
        mAggregationSuggestionEngine.setAccountFilter(
                getContent().getCurrentRawContactDelta().getAccountWithDataSet());

        mAggregationSuggestionEngine.onNameChange(valuesDelta);
    }

    /**
     * Returns the contact ID for the currently edited contact or 0 if the contact is new.
     */
    private long getContactId() {
        for (RawContactDelta rawContact : mState) {
            Long contactId = rawContact.getValues().getAsLong(RawContacts.CONTACT_ID);
            if (contactId != null) {
                return contactId;
            }
        }
        return 0;
    }

    @Override
    public void onAggregationSuggestionChange() {
        final Activity activity = getActivity();
        if ((activity != null && activity.isFinishing())
                || !isVisible() ||  mState.isEmpty() || mStatus != Status.EDITING) {
            return;
        }

        UiClosables.closeQuietly(mAggregationSuggestionPopup);

        if (mAggregationSuggestionEngine.getSuggestedContactCount() == 0) {
            return;
        }

        final View anchorView = getAggregationAnchorView();
        if (anchorView == null) {
            return; // Raw contact deleted?
        }
        mAggregationSuggestionPopup = new ListPopupWindow(mContext, null);
        mAggregationSuggestionPopup.setAnchorView(anchorView);
        mAggregationSuggestionPopup.setWidth(anchorView.getWidth());
        mAggregationSuggestionPopup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        mAggregationSuggestionPopup.setAdapter(
                new AggregationSuggestionAdapter(
                        getActivity(),
                        /* listener =*/ this,
                        mAggregationSuggestionEngine.getSuggestions()));
        mAggregationSuggestionPopup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final AggregationSuggestionView suggestionView = (AggregationSuggestionView) view;
                suggestionView.handleItemClickEvent();
                UiClosables.closeQuietly(mAggregationSuggestionPopup);
                mAggregationSuggestionPopup = null;
            }
        });
        mAggregationSuggestionPopup.show();
    }

    /**
     * Returns the editor view that should be used as the anchor for aggregation suggestions.
     */
    protected View getAggregationAnchorView() {
        return getContent().getAggregationAnchorView();
    }

    /**
     * Joins the suggested contact (specified by the id's of constituent raw
     * contacts), save all changes, and stay in the editor.
     */
    public void doJoinSuggestedContact(long[] rawContactIds) {
        if (!hasValidState() || mStatus != Status.EDITING) {
            return;
        }

        mState.setJoinWithRawContacts(rawContactIds);
        save(SaveMode.RELOAD);
    }

    @Override
    public void onEditAction(Uri contactLookupUri, long rawContactId) {
        SuggestionEditConfirmationDialogFragment.show(this, contactLookupUri, rawContactId);
    }

    /**
     * Abandons the currently edited contact and switches to editing the selected raw contact,
     * transferring all the data there
     */
    public void doEditSuggestedContact(Uri contactUri, long rawContactId) {
        if (mListener != null) {
            // make sure we don't save this contact when closing down
            mStatus = Status.CLOSING;
            mListener.onEditOtherRawContactRequested(contactUri, rawContactId,
                    getContent().getCurrentRawContactDelta().getContentValues());
        }
    }

    /**
     * Sets group metadata on all bound editors.
     */
    protected void setGroupMetaData() {
        if (mGroupMetaData != null) {
            getContent().setGroupMetaData(mGroupMetaData);
        }
    }

    /**
     * Persist the accumulated editor deltas.
     *
     * @param joinContactId the raw contact ID to join the contact being saved to after the save,
     *         may be null.
     */
    protected boolean doSaveAction(int saveMode, Long joinContactId) {
        final Intent intent = ContactSaveService.createSaveContactIntent(mContext, mState,
                SAVE_MODE_EXTRA_KEY, saveMode, isEditingUserProfile(),
                ((Activity) mContext).getClass(),
                ContactEditorActivity.ACTION_SAVE_COMPLETED, mUpdatedPhotos,
                JOIN_CONTACT_ID_EXTRA_KEY, joinContactId);
        return startSaveService(mContext, intent, saveMode);
    }

    private boolean startSaveService(Context context, Intent intent, int saveMode) {
        final boolean result = ContactSaveService.startService(
                context, intent, saveMode);
        if (!result) {
            onCancelEditConfirmed();
        }
        return result;
    }

    //
    // Join Activity
    //

    /**
     * Performs aggregation with the contact selected by the user from suggestions or A-Z list.
     */
    protected void joinAggregate(final long contactId) {
        final Intent intent = ContactSaveService.createJoinContactsIntent(
                mContext, mContactIdForJoin, contactId, ContactEditorActivity.class,
                ContactEditorActivity.ACTION_JOIN_COMPLETED);
        mContext.startService(intent);
    }

    public void removePhoto() {
        getContent().removePhoto();
        mUpdatedPhotos.remove(String.valueOf(mPhotoRawContactId));
    }

    public void updatePhoto(Uri uri) throws FileNotFoundException {
        final Bitmap bitmap = ContactPhotoUtils.getBitmapFromUri(getActivity(), uri);
        if (bitmap == null || bitmap.getHeight() <= 0 || bitmap.getWidth() <= 0) {
            Toast.makeText(mContext, R.string.contactPhotoSavedErrorToast,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        mUpdatedPhotos.putParcelable(String.valueOf(mPhotoRawContactId), uri);
        getContent().updatePhoto(uri);
    }

    public void setPrimaryPhoto() {
        getContent().setPrimaryPhoto();
    }

    @Override
    public void onNameFieldChanged(long rawContactId, ValuesDelta valuesDelta) {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        acquireAggregationSuggestions(activity, rawContactId, valuesDelta);
    }

    @Override
    public void onRebindEditorsForNewContact(RawContactDelta oldState,
            AccountWithDataSet oldAccount, AccountWithDataSet newAccount) {
        mNewContactAccountChanged = true;
        rebindEditorsForNewContact(oldState, oldAccount, newAccount);
    }

    @Override
    public void onBindEditorsFailed() {
        final Activity activity = getActivity();
        if (activity != null && !activity.isFinishing()) {
            Toast.makeText(activity, R.string.editor_failed_to_load,
                    Toast.LENGTH_SHORT).show();
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
        }
    }

    @Override
    public void onEditorsBound() {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        getLoaderManager().initLoader(LOADER_GROUPS, null, mGroupsLoaderListener);
    }

    @Override
    public void onPhotoEditorViewClicked() {
        // For contacts composed of a single writable raw contact, or raw contacts have no more
        // than 1 photo, clicking the photo view simply opens the source photo dialog
        getEditorActivity().changePhoto(getPhotoMode());
    }

    private int getPhotoMode() {
        return getContent().isWritablePhotoSet() ? PhotoActionPopup.Modes.WRITE_ABLE_PHOTO
                : PhotoActionPopup.Modes.NO_PHOTO;
    }

    private ContactEditorActivity getEditorActivity() {
        return (ContactEditorActivity) getActivity();
    }

    private RawContactEditorView getContent() {
        return (RawContactEditorView) mContent;
    }

    // TODO(b/77246197): figure out a better way to address focus being lost on rotation.
    private void maybeRestoreFocus(Bundle savedInstanceState) {
        int focusedViewId = savedInstanceState.getInt(KEY_FOCUSED_VIEW_ID, View.NO_ID);
        if (focusedViewId == View.NO_ID) {
            return;
        }
        boolean shouldRestoreSoftInput = savedInstanceState.getBoolean(KEY_RESTORE_SOFT_INPUT);
        new Handler()
            .postDelayed(
                    () -> {
                        if (!isResumed()) {
                            return;
                        }
                        View root = getView();
                        if (root == null) {
                            return;
                        }
                        View focusedView = root.findFocus();
                        if (focusedView != null) {
                            return;
                        }
                        focusedView = getView().findViewById(focusedViewId);
                        if (focusedView == null) {
                            return;
                        }
                        boolean didFocus = focusedView.requestFocus();
                        if (!didFocus) {
                            Log.i(TAG, "requestFocus failed");
                            return;
                        }
                        if (shouldRestoreSoftInput) {
                            boolean didShow = inputMethodManager
                                .showSoftInput(focusedView, InputMethodManager.SHOW_IMPLICIT);
                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                Log.d(TAG, "showSoftInput -> " + didShow);
                            }
                        }
                    },
            RESTORE_FOCUS_DELAY_MILLIS);
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) mContext.getSystemService(
            Context.INPUT_METHOD_SERVICE);
        if (imm != null && mContent != null) {
            imm.hideSoftInputFromWindow(
                mContent.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }
}
