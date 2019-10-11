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

package com.android.contacts.preference;

import android.content.res.Configuration;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.ProviderStatus;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;
import com.android.contacts.editor.SelectAccountDialogFragment;
import com.android.contacts.interactions.ImportDialogFragment;
import com.android.contacts.list.ProviderStatusWatcher;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.preference.DisplayOptionsPreferenceFragment.ProfileListener;
import com.android.contacts.preference.DisplayOptionsPreferenceFragment.ProfileQuery;
import com.android.contacts.util.AccountSelectionUtil;

/**
 * Contacts settings.
 */
public final class ContactsPreferenceActivity extends PreferenceActivity
        implements ProfileListener, SelectAccountDialogFragment.Listener {

    private static final String TAG_ABOUT = "about_contacts";
    private static final String TAG_DISPLAY_OPTIONS = "display_options";

    private String mNewLocalProfileExtra;
    private boolean mAreContactsAvailable;

    private ProviderStatusWatcher mProviderStatusWatcher;

    private AppCompatDelegate mCompatDelegate;

    public static final String EXTRA_NEW_LOCAL_PROFILE = "newLocalProfile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mCompatDelegate = AppCompatDelegate.create(this, null);

        super.onCreate(savedInstanceState);
        mCompatDelegate.onCreate(savedInstanceState);


        final ActionBar actionBar = mCompatDelegate.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }

        mProviderStatusWatcher = ProviderStatusWatcher.getInstance(this);

        mNewLocalProfileExtra = getIntent().getStringExtra(EXTRA_NEW_LOCAL_PROFILE);
        final int providerStatus = mProviderStatusWatcher.getProviderStatus();
        mAreContactsAvailable = providerStatus == ProviderStatus.STATUS_NORMAL;

        if (savedInstanceState == null) {
            final DisplayOptionsPreferenceFragment fragment = DisplayOptionsPreferenceFragment
                    .newInstance(mNewLocalProfileExtra, mAreContactsAvailable);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, fragment, TAG_DISPLAY_OPTIONS)
                    .commit();
            setActivityTitle(R.string.activity_title_settings);
        } else {
            final AboutPreferenceFragment aboutFragment = (AboutPreferenceFragment)
                    getFragmentManager().findFragmentByTag(TAG_ABOUT);

            if (aboutFragment != null) {
                setActivityTitle(R.string.setting_about);
            } else {
                setActivityTitle(R.string.activity_title_settings);
            }
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mCompatDelegate.onPostCreate(savedInstanceState);
    }

    public void setSupportActionBar(Toolbar toolbar) {
        mCompatDelegate.setSupportActionBar(toolbar);
    }

    @NonNull
    @Override
    public MenuInflater getMenuInflater() {
        return mCompatDelegate.getMenuInflater();
    }

    @Override
    public void setContentView(@LayoutRes int layoutRes) {
        mCompatDelegate.setContentView(layoutRes);
    }

    @Override
    public void setContentView(View view) {
        mCompatDelegate.setContentView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        mCompatDelegate.setContentView(view, params);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        mCompatDelegate.addContentView(view, params);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        mCompatDelegate.onPostResume();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        mCompatDelegate.setTitle(title);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCompatDelegate.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCompatDelegate.onDestroy();
    }

    @Override
    public void invalidateOptionsMenu() {
        mCompatDelegate.invalidateOptionsMenu();
    }

    protected void showAboutFragment() {
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, AboutPreferenceFragment.newInstance(), TAG_ABOUT)
                .addToBackStack(null)
                .commit();
        setActivityTitle(R.string.setting_about);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onBackPressed() {
        if (getFragmentManager().getBackStackEntryCount() > 0) {
            setActivityTitle(R.string.activity_title_settings);
            getFragmentManager().popBackStack();
        } else {
            super.onBackPressed();
        }
    }

    private void setActivityTitle(@StringRes int res) {
        final ActionBar actionBar = mCompatDelegate.getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(res);
        }
    }

    @Override
    public void onProfileLoaded(Cursor cursor) {
        boolean hasProfile = false;
        String displayName = null;
        long contactId = -1;
        int displayNameSource = DisplayNameSources.UNDEFINED;
        if (cursor != null && cursor.moveToFirst()) {
            hasProfile = cursor.getInt(ProfileQuery.CONTACT_IS_USER_PROFILE) == 1;
            displayName = cursor.getString(ProfileQuery.CONTACT_DISPLAY_NAME);
            contactId = cursor.getLong(ProfileQuery.CONTACT_ID);
            displayNameSource = cursor.getInt(ProfileQuery.DISPLAY_NAME_SOURCE);
        }
        if (hasProfile && TextUtils.isEmpty(displayName)) {
            displayName = getString(R.string.missing_name);
        }
        final DisplayOptionsPreferenceFragment fragment = (DisplayOptionsPreferenceFragment)
                getFragmentManager().findFragmentByTag(TAG_DISPLAY_OPTIONS);
        fragment.updateMyInfoPreference(hasProfile, displayName, contactId, displayNameSource);
    }

    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        AccountSelectionUtil.doImport(this, extraArgs.getInt(ImportDialogFragment
                .KEY_RES_ID), account, extraArgs.getInt(ImportDialogFragment.KEY_SUBSCRIPTION_ID));
    }

    @Override
    public void onAccountSelectorCancelled() {
    }
}
