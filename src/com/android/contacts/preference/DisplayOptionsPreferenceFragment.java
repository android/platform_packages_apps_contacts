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

package com.android.contacts.preference;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.provider.BlockedNumberContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.Profile;
import com.google.android.material.snackbar.Snackbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.text.BidiFormatter;
import android.text.TextDirectionHeuristics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.SimImportService;
import com.android.contacts.compat.TelecomManagerUtil;
import com.android.contacts.compat.TelephonyManagerCompat;
import com.android.contacts.interactions.ExportDialogFragment;
import com.android.contacts.interactions.ImportDialogFragment;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.logging.ScreenEvent.ScreenType;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountsLoader;
import com.android.contacts.util.AccountFilterUtil;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contactsbind.HelpUtils;

import java.util.List;

/**
 * This fragment shows the preferences for "display options"
 */
public class DisplayOptionsPreferenceFragment extends PreferenceFragment
        implements Preference.OnPreferenceClickListener, AccountsLoader.AccountsListener {

    private static final int REQUEST_CODE_CUSTOM_CONTACTS_FILTER = 0;

    private static final String ARG_CONTACTS_AVAILABLE = "are_contacts_available";
    private static final String ARG_NEW_LOCAL_PROFILE = "new_local_profile";

    private static final String KEY_ABOUT = "about";
    private static final String KEY_ACCOUNTS = "accounts";
    private static final String KEY_DEFAULT_ACCOUNT = "defaultAccount";
    private static final String KEY_BLOCKED_NUMBERS = "blockedNumbers";
    private static final String KEY_DISPLAY_ORDER = "displayOrder";
    private static final String KEY_CUSTOM_CONTACTS_FILTER = "customContactsFilter";
    private static final String KEY_IMPORT = "import";
    private static final String KEY_EXPORT = "export";
    private static final String KEY_MY_INFO = "myInfo";
    private static final String KEY_SORT_ORDER = "sortOrder";
    private static final String KEY_PHONETIC_NAME_DISPLAY = "phoneticNameDisplay";

    private static final int LOADER_PROFILE = 0;
    private static final int LOADER_ACCOUNTS = 1;

    /**
     * Callbacks for hosts of the {@link DisplayOptionsPreferenceFragment}.
     */
    public interface ProfileListener  {
        /**
         * Invoked after profile has been loaded.
         */
        void onProfileLoaded(Cursor data);
    }

    /**
     * The projections that are used to obtain user profile
     */
    public static class ProfileQuery {
        /**
         * Not instantiable.
         */
        private ProfileQuery() {}

        private static final String[] PROFILE_PROJECTION_PRIMARY = new String[] {
                Contacts._ID,                           // 0
                Contacts.DISPLAY_NAME_PRIMARY,          // 1
                Contacts.IS_USER_PROFILE,               // 2
                Contacts.DISPLAY_NAME_SOURCE,           // 3
        };

        private static final String[] PROFILE_PROJECTION_ALTERNATIVE = new String[] {
                Contacts._ID,                           // 0
                Contacts.DISPLAY_NAME_ALTERNATIVE,      // 1
                Contacts.IS_USER_PROFILE,               // 2
                Contacts.DISPLAY_NAME_SOURCE,           // 3
        };

        public static final int CONTACT_ID               = 0;
        public static final int CONTACT_DISPLAY_NAME     = 1;
        public static final int CONTACT_IS_USER_PROFILE  = 2;
        public static final int DISPLAY_NAME_SOURCE      = 3;
    }

    private String mNewLocalProfileExtra;
    private boolean mAreContactsAvailable;

    private boolean mHasProfile;
    private long mProfileContactId;

    private Preference mMyInfoPreference;

    private ProfileListener mListener;

    private ViewGroup mRootView;
    private SaveServiceResultListener mSaveServiceListener;

    private final LoaderManager.LoaderCallbacks<Cursor> mProfileLoaderListener =
            new LoaderManager.LoaderCallbacks<Cursor>() {

        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            final CursorLoader loader = createCursorLoader(getContext());
            loader.setUri(Profile.CONTENT_URI);
            loader.setProjection(getProjection(getContext()));
            return loader;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (mListener != null) {
                mListener.onProfileLoaded(data);
            }
        }

        public void onLoaderReset(Loader<Cursor> loader) {
        }
    };

    public static DisplayOptionsPreferenceFragment newInstance(String newLocalProfileExtra,
            boolean areContactsAvailable) {
        final DisplayOptionsPreferenceFragment fragment = new DisplayOptionsPreferenceFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_NEW_LOCAL_PROFILE, newLocalProfileExtra);
        args.putBoolean(ARG_CONTACTS_AVAILABLE, areContactsAvailable);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (ProfileListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement ProfileListener");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Wrap the preference view in a FrameLayout so we can show a snackbar
        mRootView = new FrameLayout(getActivity());
        final View list = super.onCreateView(inflater, mRootView, savedInstanceState);
        mRootView.addView(list);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSaveServiceListener = new SaveServiceResultListener();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                mSaveServiceListener,
                new IntentFilter(SimImportService.BROADCAST_SIM_IMPORT_COMPLETE));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_display_options);

        final Bundle args = getArguments();
        mNewLocalProfileExtra = args.getString(ARG_NEW_LOCAL_PROFILE);
        mAreContactsAvailable = args.getBoolean(ARG_CONTACTS_AVAILABLE);

        removeUnsupportedPreferences();

        mMyInfoPreference = findPreference(KEY_MY_INFO);

        final Preference accountsPreference = findPreference(KEY_ACCOUNTS);
        accountsPreference.setOnPreferenceClickListener(this);

        final Preference importPreference = findPreference(KEY_IMPORT);
        importPreference.setOnPreferenceClickListener(this);

        final Preference exportPreference = findPreference(KEY_EXPORT);
        if (exportPreference != null) {
            exportPreference.setOnPreferenceClickListener(this);
        }

        final Preference blockedNumbersPreference = findPreference(KEY_BLOCKED_NUMBERS);
        if (blockedNumbersPreference != null) {
            blockedNumbersPreference.setOnPreferenceClickListener(this);
        }

        final Preference aboutPreference = findPreference(KEY_ABOUT);
        if (aboutPreference != null) {
            aboutPreference.setOnPreferenceClickListener(this);
        }

        final Preference customFilterPreference = findPreference(KEY_CUSTOM_CONTACTS_FILTER);
        if (customFilterPreference != null) {
            customFilterPreference.setOnPreferenceClickListener(this);
            setCustomContactsFilterSummary();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getLoaderManager().initLoader(LOADER_PROFILE, null, mProfileLoaderListener);
        AccountsLoader.loadAccounts(this, LOADER_ACCOUNTS, AccountTypeManager.writableFilter());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mSaveServiceListener);
        mRootView = null;
    }

    public void updateMyInfoPreference(boolean hasProfile, String displayName, long contactId,
            int displayNameSource) {
        final CharSequence summary = !hasProfile ?
                getString(R.string.set_up_profile) :
                displayNameSource == DisplayNameSources.PHONE ?
                BidiFormatter.getInstance().unicodeWrap(displayName, TextDirectionHeuristics.LTR) :
                displayName;
        mMyInfoPreference.setSummary(summary);
        mHasProfile = hasProfile;
        mProfileContactId = contactId;
        mMyInfoPreference.setOnPreferenceClickListener(this);
    }

    private void removeUnsupportedPreferences() {
        // Disable sort order for CJK locales where it is not supported
        final Resources resources = getResources();
        if (!resources.getBoolean(R.bool.config_sort_order_user_changeable)) {
            getPreferenceScreen().removePreference(findPreference(KEY_SORT_ORDER));
        }

        if (!resources.getBoolean(R.bool.config_phonetic_name_display_user_changeable)) {
            getPreferenceScreen().removePreference(findPreference(KEY_PHONETIC_NAME_DISPLAY));
        }

        if (HelpUtils.isHelpAndFeedbackAvailable()) {
            getPreferenceScreen().removePreference(findPreference(KEY_ABOUT));
        }

        // Disable display order for CJK locales as well
        if (!resources.getBoolean(R.bool.config_display_order_user_changeable)) {
            getPreferenceScreen().removePreference(findPreference(KEY_DISPLAY_ORDER));
        }

        final boolean isPhone = TelephonyManagerCompat.isVoiceCapable(
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE));
        final boolean showBlockedNumbers = isPhone && ContactsUtils.FLAG_N_FEATURE
                && BlockedNumberContract.canCurrentUserBlockNumbers(getContext());
        if (!showBlockedNumbers) {
            getPreferenceScreen().removePreference(findPreference(KEY_BLOCKED_NUMBERS));
        }

        if (!mAreContactsAvailable) {
            getPreferenceScreen().removePreference(findPreference(KEY_EXPORT));
        }
    }

    @Override
    public void onAccountsLoaded(List<AccountInfo> accounts) {
        // Hide accounts preferences if no writable accounts exist
        final DefaultAccountPreference preference =
                (DefaultAccountPreference) findPreference(KEY_DEFAULT_ACCOUNT);
        preference.setAccounts(accounts);
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    private CursorLoader createCursorLoader(Context context) {
        return new CursorLoader(context) {
            @Override
            protected Cursor onLoadInBackground() {
                try {
                    return super.onLoadInBackground();
                } catch (RuntimeException e) {
                    return null;
                }
            }
        };
    }

    private String[] getProjection(Context context) {
        final ContactsPreferences contactsPrefs = new ContactsPreferences(context);
        final int displayOrder = contactsPrefs.getDisplayOrder();
        if (displayOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            return ProfileQuery.PROFILE_PROJECTION_PRIMARY;
        }
        return ProfileQuery.PROFILE_PROJECTION_ALTERNATIVE;
    }

    @Override
    public boolean onPreferenceClick(Preference p) {
        final String prefKey = p.getKey();

        if (KEY_ABOUT.equals(prefKey)) {
            ((ContactsPreferenceActivity) getActivity()).showAboutFragment();
            return true;
        } else if (KEY_IMPORT.equals(prefKey)) {
            ImportDialogFragment.show(getFragmentManager());
            return true;
        } else if (KEY_EXPORT.equals(prefKey)) {
            ExportDialogFragment.show(getFragmentManager(), ContactsPreferenceActivity.class,
                    ExportDialogFragment.EXPORT_MODE_ALL_CONTACTS);
            return true;
        } else if (KEY_MY_INFO.equals(prefKey)) {
            if (mHasProfile) {
                final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, mProfileContactId);
                ImplicitIntentsUtil.startQuickContact(getActivity(), uri, ScreenType.ME_CONTACT);
            } else {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                intent.putExtra(mNewLocalProfileExtra, true);
                ImplicitIntentsUtil.startActivityInApp(getActivity(), intent);
            }
            return true;
        } else if (KEY_ACCOUNTS.equals(prefKey)) {
            ImplicitIntentsUtil.startActivityOutsideApp(getContext(),
                    ImplicitIntentsUtil.getIntentForAddingAccount());
            return true;
        } else if (KEY_BLOCKED_NUMBERS.equals(prefKey)) {
            final Intent intent = TelecomManagerUtil.createManageBlockedNumbersIntent(
                    (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE));
            startActivity(intent);
            return true;
        } else if (KEY_CUSTOM_CONTACTS_FILTER.equals(prefKey)) {
            final ContactListFilter filter =
                    ContactListFilterController.getInstance(getContext()).getFilter();
            AccountFilterUtil.startAccountFilterActivityForResult(
                    this, REQUEST_CODE_CUSTOM_CONTACTS_FILTER, filter);
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CUSTOM_CONTACTS_FILTER
                && resultCode == Activity.RESULT_OK) {
            AccountFilterUtil.handleAccountFilterResult(
                    ContactListFilterController.getInstance(getContext()), resultCode, data);
            setCustomContactsFilterSummary();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void setCustomContactsFilterSummary() {
        final Preference customFilterPreference = findPreference(KEY_CUSTOM_CONTACTS_FILTER);
        if (customFilterPreference != null) {
            final ContactListFilter filter =
                    ContactListFilterController.getInstance(getContext()).getPersistedFilter();
            if (filter != null) {
                if (filter.filterType == ContactListFilter.FILTER_TYPE_DEFAULT ||
                        filter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
                    customFilterPreference.setSummary(R.string.list_filter_all_accounts);
                } else if (filter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM) {
                    customFilterPreference.setSummary(R.string.listCustomView);
                } else {
                    customFilterPreference.setSummary(null);
                }
            }
        }
    }

    private class SaveServiceResultListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final long now = System.currentTimeMillis();
            final long opStart = intent.getLongExtra(
                    SimImportService.EXTRA_OPERATION_REQUESTED_AT_TIME, now);

            // If it's been over 30 seconds the user is likely in a different context so suppress
            // the toast message.
            if (now - opStart > 30*1000) return;

            final int code = intent.getIntExtra(SimImportService.EXTRA_RESULT_CODE,
                    SimImportService.RESULT_UNKNOWN);
            final int count = intent.getIntExtra(SimImportService.EXTRA_RESULT_COUNT, -1);
            if (code == SimImportService.RESULT_SUCCESS && count > 0) {
                Snackbar.make(mRootView, getResources().getQuantityString(
                        R.plurals.sim_import_success_toast_fmt, count, count),
                        Snackbar.LENGTH_LONG).show();
            } else if (code == SimImportService.RESULT_FAILURE) {
                Snackbar.make(mRootView, R.string.sim_import_failed_toast,
                        Snackbar.LENGTH_LONG).show();
            }
        }
    }
}

