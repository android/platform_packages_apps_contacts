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

import android.app.backup.BackupAgent;
import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;

import com.android.contacts.R;
import com.android.contacts.model.account.AccountWithDataSet;

import java.util.List;

/**
 * Manages user preferences for contacts.
 */
public class ContactsPreferences implements OnSharedPreferenceChangeListener {

    /**
     * The value for the DISPLAY_ORDER key to show the given name first.
     */
    public static final int DISPLAY_ORDER_PRIMARY = 1;

    /**
     * The value for the DISPLAY_ORDER key to show the family name first.
     */
    public static final int DISPLAY_ORDER_ALTERNATIVE = 2;

    public static final String DISPLAY_ORDER_KEY = "android.contacts.DISPLAY_ORDER";

    /**
     * The value for the SORT_ORDER key corresponding to sort by given name first.
     */
    public static final int SORT_ORDER_PRIMARY = 1;

    public static final String SORT_ORDER_KEY = "android.contacts.SORT_ORDER";

    /**
     * The value for the SORT_ORDER key corresponding to sort by family name first.
     */
    public static final int SORT_ORDER_ALTERNATIVE = 2;

    public static final String PREF_DISPLAY_ONLY_PHONES = "only_phones";

    public static final boolean PREF_DISPLAY_ONLY_PHONES_DEFAULT = false;

    public static final String PHONETIC_NAME_DISPLAY_KEY = "Phonetic_name_display";

    /**
     * Value to use when a preference is unassigned and needs to be read from the shared preferences
     */
    private static final int PREFERENCE_UNASSIGNED = -1;

    private final Context mContext;
    private int mSortOrder = PREFERENCE_UNASSIGNED;
    private int mDisplayOrder = PREFERENCE_UNASSIGNED;
    private int mPhoneticNameDisplayPreference = PREFERENCE_UNASSIGNED;

    private AccountWithDataSet mDefaultAccount = null;
    private ChangeListener mListener = null;
    private Handler mHandler;
    private final SharedPreferences mPreferences;
    private final BackupManager mBackupManager;
    private final boolean mIsDefaultAccountUserChangeable;
    private String mDefaultAccountKey;

    public ContactsPreferences(Context context) {
        this(context,
                context.getResources().getBoolean(R.bool.config_default_account_user_changeable));
    }

    @VisibleForTesting
    ContactsPreferences(Context context, boolean isDefaultAccountUserChangeable) {
        mContext = context;
        mIsDefaultAccountUserChangeable = isDefaultAccountUserChangeable;

        mBackupManager = new BackupManager(mContext);

        mHandler = new Handler(Looper.getMainLooper());
        mPreferences = mContext.getSharedPreferences(context.getPackageName(),
                Context.MODE_PRIVATE);
        mDefaultAccountKey = mContext.getResources().getString(
                R.string.contact_editor_default_account_key);
        maybeMigrateSystemSettings();
    }

    public boolean isSortOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_sort_order_user_changeable);
    }

    public int getDefaultSortOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_sort_order_primary)) {
            return SORT_ORDER_PRIMARY;
        } else {
            return SORT_ORDER_ALTERNATIVE;
        }
    }

    public int getSortOrder() {
        if (!isSortOrderUserChangeable()) {
            return getDefaultSortOrder();
        }
        if (mSortOrder == PREFERENCE_UNASSIGNED) {
            mSortOrder = mPreferences.getInt(SORT_ORDER_KEY, getDefaultSortOrder());
        }
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
        final Editor editor = mPreferences.edit();
        editor.putInt(SORT_ORDER_KEY, sortOrder);
        editor.commit();
        mBackupManager.dataChanged();
    }

    public boolean isDisplayOrderUserChangeable() {
        return mContext.getResources().getBoolean(R.bool.config_display_order_user_changeable);
    }

    public int getDefaultDisplayOrder() {
        if (mContext.getResources().getBoolean(R.bool.config_default_display_order_primary)) {
            return DISPLAY_ORDER_PRIMARY;
        } else {
            return DISPLAY_ORDER_ALTERNATIVE;
        }
    }

    public int getDisplayOrder() {
        if (!isDisplayOrderUserChangeable()) {
            return getDefaultDisplayOrder();
        }
        if (mDisplayOrder == PREFERENCE_UNASSIGNED) {
            mDisplayOrder = mPreferences.getInt(DISPLAY_ORDER_KEY, getDefaultDisplayOrder());
        }
        return mDisplayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
        final Editor editor = mPreferences.edit();
        editor.putInt(DISPLAY_ORDER_KEY, displayOrder);
        editor.commit();
        mBackupManager.dataChanged();
    }

    public int getDefaultPhoneticNameDisplayPreference() {
        if (mContext.getResources().getBoolean(R.bool.config_default_hide_phonetic_name_if_empty)) {
            return PhoneticNameDisplayPreference.HIDE_IF_EMPTY;
        } else {
            return PhoneticNameDisplayPreference.SHOW_ALWAYS;
        }
    }

    public boolean isPhoneticNameDisplayPreferenceChangeable() {
        return mContext.getResources().getBoolean(
                R.bool.config_phonetic_name_display_user_changeable);
    }

    public void setPhoneticNameDisplayPreference(int phoneticNameDisplayPreference) {
        mPhoneticNameDisplayPreference = phoneticNameDisplayPreference;
        final Editor editor = mPreferences.edit();
        editor.putInt(PHONETIC_NAME_DISPLAY_KEY, phoneticNameDisplayPreference);
        editor.commit();
        mBackupManager.dataChanged();
    }

    public int getPhoneticNameDisplayPreference() {
        if (!isPhoneticNameDisplayPreferenceChangeable()) {
            return getDefaultPhoneticNameDisplayPreference();
        }
        if (mPhoneticNameDisplayPreference == PREFERENCE_UNASSIGNED) {
            mPhoneticNameDisplayPreference = mPreferences.getInt(PHONETIC_NAME_DISPLAY_KEY,
                    getDefaultPhoneticNameDisplayPreference());
        }
        return mPhoneticNameDisplayPreference;
    }

    public boolean shouldHidePhoneticNamesIfEmpty() {
        return getPhoneticNameDisplayPreference() == PhoneticNameDisplayPreference.HIDE_IF_EMPTY;
    }

    public boolean isDefaultAccountUserChangeable() {
        return mIsDefaultAccountUserChangeable;
    }

    public AccountWithDataSet getDefaultAccount() {
        if (!isDefaultAccountUserChangeable()) {
            return mDefaultAccount;
        }
        if (mDefaultAccount == null) {
            final String accountString = mPreferences
                    .getString(mDefaultAccountKey, null);
            if (!TextUtils.isEmpty(accountString)) {
                mDefaultAccount = AccountWithDataSet.unstringify(accountString);
            }
        }
        return mDefaultAccount;
    }

    public void clearDefaultAccount() {
        mDefaultAccount = null;
        mPreferences.edit().remove(mDefaultAccountKey).commit();
    }

    public void setDefaultAccount(@NonNull AccountWithDataSet accountWithDataSet) {
        if (accountWithDataSet == null) {
            throw new IllegalArgumentException(
                    "argument should not be null");
        }
        mDefaultAccount = accountWithDataSet;
        mPreferences.edit().putString(mDefaultAccountKey, accountWithDataSet.stringify()).commit();
    }

    public boolean isDefaultAccountSet() {
        return mDefaultAccount != null || mPreferences.contains(mDefaultAccountKey);
    }

    /**
     * @return false if there is only one writable account or no requirement to return true is met.
     *         true if the contact editor should show the "accounts changed" notification, that is:
     *              - If it's the first launch.
     *              - Or, if the default account has been removed.
     *              (And some extra soundness check)
     *
     * Note if this method returns {@code false}, the caller can safely assume that
     * {@link #getDefaultAccount} will return a valid account.  (Either an account which still
     * exists, or {@code null} which should be interpreted as "local only".)
     */
    public boolean shouldShowAccountChangedNotification(List<AccountWithDataSet>
            currentWritableAccounts) {
        final AccountWithDataSet defaultAccount = getDefaultAccount();

        // This shouldn't occur anymore because a "device" account is added in the case that there
        // are no other accounts but if there are no writable accounts then the default has been
        // initialized if it is "device"
        if (currentWritableAccounts.isEmpty()) {
            return defaultAccount == null || !defaultAccount.isNullAccount();
        }

        if (currentWritableAccounts.size() == 1
                && !currentWritableAccounts.get(0).isNullAccount()) {
            return false;
        }

        if (defaultAccount == null) {
            return true;
        }

        if (!currentWritableAccounts.contains(defaultAccount)) {
            return true;
        }

        // All good.
        return false;
    }

    public void registerChangeListener(ChangeListener listener) {
        if (mListener != null) unregisterChangeListener();

        mListener = listener;

        // Reset preferences to "unknown" because they may have changed while the
        // listener was unregistered.
        mDisplayOrder = PREFERENCE_UNASSIGNED;
        mSortOrder = PREFERENCE_UNASSIGNED;
        mPhoneticNameDisplayPreference = PREFERENCE_UNASSIGNED;
        mDefaultAccount = null;

        mPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void unregisterChangeListener() {
        if (mListener != null) {
            mListener = null;
        }

        mPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, final String key) {
        // This notification is not sent on the Ui thread. Use the previously created Handler
        // to switch to the Ui thread
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                refreshValue(key);
            }
        });
    }

    /**
     * Forces the value for the given key to be looked up from shared preferences and notifies
     * the registered {@link ChangeListener}
     *
     * @param key the {@link SharedPreferences} key to look up
     */
    public void refreshValue(String key) {
        if (DISPLAY_ORDER_KEY.equals(key)) {
            mDisplayOrder = PREFERENCE_UNASSIGNED;
            mDisplayOrder = getDisplayOrder();
        } else if (SORT_ORDER_KEY.equals(key)) {
            mSortOrder = PREFERENCE_UNASSIGNED;
            mSortOrder = getSortOrder();
        } else if (PHONETIC_NAME_DISPLAY_KEY.equals(key)) {
            mPhoneticNameDisplayPreference = PREFERENCE_UNASSIGNED;
            mPhoneticNameDisplayPreference = getPhoneticNameDisplayPreference();
        } else if (mDefaultAccountKey.equals(key)) {
            mDefaultAccount = null;
            mDefaultAccount = getDefaultAccount();
        }
        if (mListener != null) mListener.onChange();
    }

    public interface ChangeListener {
        void onChange();
    }

    /**
     * If there are currently no preferences (which means this is the first time we are run),
     * For sort order and display order, check to see if there are any preferences stored in
     * system settings (pre-L) which can be copied into our own SharedPreferences.
     * For default account setting, check to see if there are any preferences stored in the previous
     * SharedPreferences which can be copied into current SharedPreferences.
     */
    private void maybeMigrateSystemSettings() {
        if (!mPreferences.contains(SORT_ORDER_KEY)) {
            int sortOrder = getDefaultSortOrder();
            try {
                 sortOrder = Settings.System.getInt(mContext.getContentResolver(),
                        SORT_ORDER_KEY);
            } catch (SettingNotFoundException e) {
            }
            setSortOrder(sortOrder);
        }

        if (!mPreferences.contains(DISPLAY_ORDER_KEY)) {
            int displayOrder = getDefaultDisplayOrder();
            try {
                displayOrder = Settings.System.getInt(mContext.getContentResolver(),
                        DISPLAY_ORDER_KEY);
            } catch (SettingNotFoundException e) {
            }
            setDisplayOrder(displayOrder);
        }

        if (!mPreferences.contains(PHONETIC_NAME_DISPLAY_KEY)) {
            int phoneticNameFieldsDisplay = getDefaultPhoneticNameDisplayPreference();
            try {
                phoneticNameFieldsDisplay = Settings.System.getInt(mContext.getContentResolver(),
                        PHONETIC_NAME_DISPLAY_KEY);
            } catch (SettingNotFoundException e) {
            }
            setPhoneticNameDisplayPreference(phoneticNameFieldsDisplay);
        }

        if (!mPreferences.contains(mDefaultAccountKey)) {
            final SharedPreferences previousPrefs =
                    PreferenceManager.getDefaultSharedPreferences(mContext);
            final String defaultAccount = previousPrefs.getString(mDefaultAccountKey, null);
            if (!TextUtils.isEmpty(defaultAccount)) {
                final AccountWithDataSet accountWithDataSet = AccountWithDataSet.unstringify(
                        defaultAccount);
                setDefaultAccount(accountWithDataSet);
            }
        }
    }

}
