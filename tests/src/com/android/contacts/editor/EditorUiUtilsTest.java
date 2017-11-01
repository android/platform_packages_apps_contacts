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
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.R;
import com.android.contacts.model.account.AccountDisplayInfo;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.DeviceLocalAccountType;
import com.android.contacts.tests.FakeAccountType;

/**
 * Tests {@link EditorUiUtils}.
 */
@SmallTest
public class EditorUiUtilsTest extends AndroidTestCase {

    private static final String ACCOUNT_NAME = "somebody@lunkedin.com";
    private static final String DISPLAY_LABEL = "LunkedIn";

    private static final String GOOGLE_ACCOUNT_NAME = "somebody@gmail.com";
    private static final String GOOGLE_DISPLAY_LABEL = "Google";

    private static final String RINGTONE = "content://media/external/audio/media/31";

    private static final AccountWithDataSet ACCOUNT =
            new AccountWithDataSet(ACCOUNT_NAME, "some.account.type", null);
    private static final AccountWithDataSet GOOGLE_ACCOUNT =
            new AccountWithDataSet(ACCOUNT_NAME, "com.google", null);

    private static final class MockAccountType extends AccountType {

        private final String mDisplayLabel;

        private MockAccountType(String displayLabel) {
            mDisplayLabel = displayLabel;
        }

        @Override
        public boolean areContactsWritable() {
            return false;
        }

        @Override
        public boolean isGroupMembershipEditable() {
            return false;
        }

        @Override
        public CharSequence getDisplayLabel(Context context) {
            return mDisplayLabel;
        }
    }

    public void testGetProfileAccountInfo_NonLocalAccount() {
        final AccountInfo account = new AccountInfo(new AccountDisplayInfo(ACCOUNT, ACCOUNT_NAME,
                DISPLAY_LABEL, null, /* isDeviceAccount */ false),
                new FakeAccountType("com.example.account"));

        final String label = EditorUiUtils.getAccountHeaderLabelForMyProfile(getContext(),
                account);

        // My LunkedIn profile
        final String expected = getContext()
                .getString(R.string.external_profile_title, DISPLAY_LABEL);
        assertEquals(expected, label);
    }


    public void testGetProfileAccountInfo_DeviceLocalAccount() {
        final AccountInfo account = new AccountInfo(new AccountDisplayInfo(ACCOUNT, "Device",
                "Device", null, true), new DeviceLocalAccountType(mContext));

        final String label = EditorUiUtils.getAccountHeaderLabelForMyProfile(getContext(),
                account);

        // "My local profile"
        final String expected = getContext().getString(R.string.local_profile_title);
        assertEquals(expected, label);
    }

    public void testGetAccountInfo_AccountType_NonGoogle() {
        final AccountDisplayInfo account = new AccountDisplayInfo(ACCOUNT, ACCOUNT_NAME,
                DISPLAY_LABEL, /*icon*/ null, /*isDeviceAccount*/ false);

        final String label = EditorUiUtils.getAccountTypeHeaderLabel(getContext(), account);

        // LunkedIn Contact
        final String expected = getContext().getString(R.string.account_type_format, DISPLAY_LABEL);
        assertEquals(expected, label);
    }

    public void testGetAccountInfo_AccountType_Google() {
        final AccountDisplayInfo account = new AccountDisplayInfo(GOOGLE_ACCOUNT, ACCOUNT_NAME,
                GOOGLE_DISPLAY_LABEL, /*icon*/ null, /*isDeviceAccount*/ false);

        final String label = EditorUiUtils.getAccountTypeHeaderLabel(getContext(), account);

        // Google Account
        final String expected = getContext().getString(R.string.google_account_type_format,
                GOOGLE_DISPLAY_LABEL);
        assertEquals(expected, label);
    }

  public void testGetAccountInfo_AccountType_DeviceAccount() {
      final AccountWithDataSet deviceAccount = AccountWithDataSet.getNullAccount();
      final AccountDisplayInfo account = new AccountDisplayInfo(deviceAccount, "Device",
              "Device", /*icon*/ null, /*isDeviceAccount*/ true);

      final String label = EditorUiUtils.getAccountTypeHeaderLabel(getContext(), account);

      // "Device"
      final String expected = getContext().getString(R.string.account_phone);
      assertEquals(expected, label);
    }

    public void testGetRingtongStrFromUri_lessThanOrEqualsToM() {
        final int currentVersion = Build.VERSION_CODES.M;
        assertNull(EditorUiUtils.getRingtoneStringFromUri(null, currentVersion));
        assertNull(EditorUiUtils.getRingtoneStringFromUri(Settings.System.DEFAULT_RINGTONE_URI,
                currentVersion));
        assertEquals(RINGTONE, EditorUiUtils.getRingtoneStringFromUri(Uri.parse(RINGTONE),
                        currentVersion));
    }

    public void testGetRingtongStrFromUri_nOrGreater() {
        final int currentVersion = Build.VERSION_CODES.M + 1;
        assertEquals("", EditorUiUtils.getRingtoneStringFromUri(null, currentVersion));
        assertNull(EditorUiUtils.getRingtoneStringFromUri(Settings.System.DEFAULT_RINGTONE_URI,
                currentVersion));
        assertEquals(RINGTONE, EditorUiUtils.getRingtoneStringFromUri(Uri.parse(RINGTONE),
                        currentVersion));
    }

    public void testGetRingtongUriFromStr_lessThanOrEqualsToM() {
        final int currentVersion = Build.VERSION_CODES.M;
        assertEquals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), EditorUiUtils
                        .getRingtoneUriFromString(null, currentVersion));
        assertEquals(Uri.parse(""), EditorUiUtils.getRingtoneUriFromString("", currentVersion));
        assertEquals(Uri.parse(RINGTONE), EditorUiUtils.getRingtoneUriFromString(RINGTONE,
                currentVersion));
    }

    public void testGetRingtongUriFromStr_nOrGreater() {
        final int currentVersion = Build.VERSION_CODES.M + 1;
        assertEquals(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE), EditorUiUtils
                        .getRingtoneUriFromString(null, currentVersion));
        assertNull(EditorUiUtils.getRingtoneUriFromString("", currentVersion));
        assertEquals(Uri.parse(RINGTONE), EditorUiUtils.getRingtoneUriFromString(RINGTONE,
                currentVersion));
    }

    private AccountDisplayInfo createDisplayableAccount() {
        return new AccountDisplayInfo(ACCOUNT, ACCOUNT_NAME, DISPLAY_LABEL, null, false);
    }

}
