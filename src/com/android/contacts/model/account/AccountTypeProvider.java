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
package com.android.contacts.model.account;

import static com.android.contacts.util.DeviceLocalAccountTypeFactory.Util.isLocalAccountType;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncAdapterType;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.util.DeviceLocalAccountTypeFactory;
import com.android.contactsbind.ObjectFactory;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Provides access to {@link AccountType}s with contact data
 *
 * This class parses the contacts.xml for third-party accounts and caches the result.
 * This means that {@link AccountTypeProvider#getAccountTypes(String)}} should be called from a
 * background thread.
 */
public class AccountTypeProvider {
    private static final String TAG = "AccountTypeProvider";

    private final Context mContext;
    private final DeviceLocalAccountTypeFactory mLocalAccountTypeFactory;
    private final ImmutableMap<String, AuthenticatorDescription> mAuthTypes;

    private final ConcurrentMap<String, List<AccountType>> mCache = new ConcurrentHashMap<>();

    public AccountTypeProvider(Context context) {
        this(context,
                ObjectFactory.getDeviceLocalAccountTypeFactory(context),
                ContentResolver.getSyncAdapterTypes(),
                ((AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE))
                        .getAuthenticatorTypes());
    }

    public AccountTypeProvider(Context context, DeviceLocalAccountTypeFactory localTypeFactory,
            SyncAdapterType[] syncAdapterTypes,
            AuthenticatorDescription[] authenticatorDescriptions) {
        mContext = context;
        mLocalAccountTypeFactory = localTypeFactory;

        mAuthTypes = onlyContactSyncable(authenticatorDescriptions, syncAdapterTypes);
    }

    /**
     * Returns all account types associated with the provided type
     *
     * <p>There are many {@link AccountType}s for each accountType because {@AccountType} includes
     * a dataSet and accounts can declare extension packages in contacts.xml that provide additional
     * data sets for a particular type
     * </p>
     */
    public List<AccountType> getAccountTypes(String accountType) {
        // ConcurrentHashMap doesn't support null keys
        if (accountType == null) {
            AccountType type = mLocalAccountTypeFactory.getAccountType(accountType);
            // Just in case the DeviceLocalAccountTypeFactory doesn't handle the null type
            if (type == null) {
                type = new FallbackAccountType(mContext);
            }
            return Collections.singletonList(type);
        }

        List<AccountType> types = mCache.get(accountType);
        if (types == null) {
            types = loadTypes(accountType);
            mCache.put(accountType, types);
        }
        return types;
    }

    public boolean hasTypeForAccount(AccountWithDataSet account) {
        return getTypeForAccount(account) != null;
    }

    public boolean hasTypeWithDataset(String type, String dataSet) {
        // getAccountTypes() never returns null
        final List<AccountType> accountTypes = getAccountTypes(type);
        for (AccountType accountType : accountTypes) {
            if (Objects.equal(accountType.dataSet, dataSet)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the AccountType with the matching type and dataSet or null if no account with those
     * members exists
     */
    public AccountType getType(String type, String dataSet) {
        final List<AccountType> accountTypes = getAccountTypes(type);
        for (AccountType accountType : accountTypes) {
            if (Objects.equal(accountType.dataSet, dataSet)) {
                return accountType;
            }
        }
        return null;
    }

    /**
     * Returns the AccountType for a particular account or null if no account type exists for the
     * account
     */
    public AccountType getTypeForAccount(AccountWithDataSet account) {
        return getType(account.type, account.dataSet);
    }

    public boolean shouldUpdate(AuthenticatorDescription[] auths, SyncAdapterType[] syncTypes) {
        Map<String, AuthenticatorDescription> contactsAuths = onlyContactSyncable(auths, syncTypes);
        if (!contactsAuths.keySet().equals(mAuthTypes.keySet())) {
            return true;
        }
        for (AuthenticatorDescription auth : contactsAuths.values()) {
            if (!deepEquals(mAuthTypes.get(auth.type), auth)) {
                return true;
            }
        }
        return false;
    }

    public boolean supportsContactsSyncing(String accountType) {
        return mAuthTypes.containsKey(accountType);
    }

    private List<AccountType> loadTypes(String type) {
        final AuthenticatorDescription auth = mAuthTypes.get(type);
        if (auth == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Null auth type for " + type);
            }
            return Collections.emptyList();
        }

        AccountType accountType;
        if (GoogleAccountType.ACCOUNT_TYPE.equals(type)) {
            accountType = new GoogleAccountType(mContext, auth.packageName);
        } else if (ExchangeAccountType.isExchangeType(type)) {
            accountType = new ExchangeAccountType(mContext, auth.packageName, type);
        } else if (SamsungAccountType.isSamsungAccountType(mContext, type,
                auth.packageName)) {
            accountType = new SamsungAccountType(mContext, auth.packageName, type);
        } else if (!ExternalAccountType.hasContactsXml(mContext, auth.packageName)
                && isLocalAccountType(mLocalAccountTypeFactory, type)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Registering local account type=" + type
                        + ", packageName=" + auth.packageName);
            }
            accountType = mLocalAccountTypeFactory.getAccountType(type);
        } else {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Registering external account type=" + type
                        + ", packageName=" + auth.packageName);
            }
            accountType = new ExternalAccountType(mContext, auth.packageName, false);
        }
        if (!accountType.isInitialized()) {
            if (accountType.isEmbedded()) {
                throw new IllegalStateException("Problem initializing embedded type "
                        + accountType.getClass().getCanonicalName());
            } else {
                // Skip external account types that couldn't be initialized
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Skipping external account type=" + type
                            + ", packageName=" + auth.packageName);
                }
                return Collections.emptyList();
            }
        }

        accountType.initializeFieldsFromAuthenticator(auth);

        final ImmutableList.Builder<AccountType> result = ImmutableList.builder();
        result.add(accountType);

        for (String extensionPackage : accountType.getExtensionPackageNames()) {
            final ExternalAccountType extensionType =
                    new ExternalAccountType(mContext, extensionPackage, true);
            if (!extensionType.isInitialized()) {
                // Skip external account types that couldn't be initialized.
                continue;
            }
            if (!extensionType.hasContactsMetadata()) {
                Log.w(TAG, "Skipping extension package " + extensionPackage + " because"
                        + " it doesn't have the CONTACTS_STRUCTURE metadata");
                continue;
            }
            if (TextUtils.isEmpty(extensionType.accountType)) {
                Log.w(TAG, "Skipping extension package " + extensionPackage + " because"
                        + " the CONTACTS_STRUCTURE metadata doesn't have the accountType"
                        + " attribute");
                continue;
            }
            if (!Objects.equal(extensionType.accountType, type)) {
                Log.w(TAG, "Skipping extension package " + extensionPackage + " because"
                        + " the account type + " + extensionType.accountType +
                        " doesn't match expected type " + type);
                continue;
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Registering extension package account type="
                        + accountType.accountType + ", dataSet=" + accountType.dataSet
                        + ", packageName=" + extensionPackage);
            }

            result.add(extensionType);
        }
        return result.build();
    }

    private static ImmutableMap<String, AuthenticatorDescription> onlyContactSyncable(
            AuthenticatorDescription[] auths, SyncAdapterType[] syncTypes) {
        final Set<String> mContactSyncableTypes = new HashSet<>();
        for (SyncAdapterType type : syncTypes) {
            if (type.authority.equals(ContactsContract.AUTHORITY)) {
                mContactSyncableTypes.add(type.accountType);
            }
        }

        final ImmutableMap.Builder<String, AuthenticatorDescription> builder =
                ImmutableMap.builder();
        for (AuthenticatorDescription auth : auths) {
            if (mContactSyncableTypes.contains(auth.type)) {
                builder.put(auth.type, auth);
            }
        }
        return builder.build();
    }

    /**
     * Compares all fields in auth1 and auth2
     *
     * <p>By default {@link AuthenticatorDescription#equals(Object)} only checks the type</p>
     */
    private boolean deepEquals(AuthenticatorDescription auth1, AuthenticatorDescription auth2) {
        return Objects.equal(auth1, auth2) &&
                Objects.equal(auth1.packageName, auth2.packageName) &&
                auth1.labelId == auth2.labelId &&
                auth1.iconId == auth2.iconId &&
                auth1.smallIconId == auth2.smallIconId &&
                auth1.accountPreferencesId == auth2.accountPreferencesId &&
                auth1.customTokens == auth2.customTokens;
    }

}
