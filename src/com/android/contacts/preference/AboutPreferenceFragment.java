/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.activities.LicenseActivity;

/**
 * This fragment shows the preferences for "about".
 */
public class AboutPreferenceFragment extends PreferenceFragment {

    public static final String PRIVACY_POLICY_URL = "http://www.google.com/policies/privacy";
    public static final String TERMS_OF_SERVICE_URL = "http://www.google.com/policies/terms";

    public static AboutPreferenceFragment newInstance() {
        return new AboutPreferenceFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preference_about);

        // Set build version of Contacts App.
        final PackageManager manager = getActivity().getPackageManager();
        try {
            final PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);
            final Preference versionPreference = findPreference(
                    getString(R.string.pref_build_version_key));
            versionPreference.setSummary(info.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            // Nothing
        }

        final Preference licensePreference = findPreference(
                getString(R.string.pref_open_source_licenses_key));
        licensePreference.setIntent(new Intent(getActivity(), LicenseActivity.class));

        final Preference privacyPolicyPreference = findPreference("pref_privacy_policy");
        final Preference termsOfServicePreference = findPreference("pref_terms_of_service");

        final Preference.OnPreferenceClickListener listener =
                new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    if (preference == privacyPolicyPreference) {
                        startActivityForUrl(PRIVACY_POLICY_URL);
                    } else if (preference == termsOfServicePreference) {
                        startActivityForUrl(TERMS_OF_SERVICE_URL);
                    }
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(getContext(), getString(R.string.url_open_error_toast),
                            Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        };

        privacyPolicyPreference.setOnPreferenceClickListener(listener);
        termsOfServicePreference.setOnPreferenceClickListener(listener);
    }

    @Override
    public Context getContext() {
        return getActivity();
    }

    private void startActivityForUrl(String urlString) {
        final Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(urlString));
        startActivity(intent);
    }
}

