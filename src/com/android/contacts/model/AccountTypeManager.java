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

package com.android.contacts.model;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SyncStatusObserver;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.Experiments;
import com.android.contacts.R;
import com.android.contacts.list.ContactListFilterController;
import com.android.contacts.model.account.AccountInfo;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountTypeProvider;
import com.android.contacts.model.account.AccountTypeWithDataSet;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.FallbackAccountType;
import com.android.contacts.model.account.GoogleAccountType;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.concurrent.ContactsExecutors;
import com.android.contactsbind.experiments.Flags;
import com.google.common.base.Preconditions;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * Singleton holder for all parsed {@link AccountType} available on the
 * system, typically filled through {@link PackageManager} queries.
 */
public abstract class AccountTypeManager {
    static final String TAG = "AccountTypeManager";

    private static final Object mInitializationLock = new Object();
    private static AccountTypeManager mAccountTypeManager;

    public static final String BROADCAST_ACCOUNTS_CHANGED = AccountTypeManager.class.getName() +
            ".AccountsChanged";

    public enum AccountFilter implements Predicate<AccountInfo> {
        ALL {
            @Override
            public boolean apply(@Nullable AccountInfo input) {
                return input != null;
            }
        },
        CONTACTS_WRITABLE {
            @Override
            public boolean apply(@Nullable AccountInfo input) {
                return input != null && input.getType().areContactsWritable();
            }
        },
        GROUPS_WRITABLE {
            @Override
            public boolean apply(@Nullable AccountInfo input) {
                return input != null && input.getType().isGroupMembershipEditable();
            }
        };
    }

    /**
     * Requests the singleton instance of {@link AccountTypeManager} with data bound from
     * the available authenticators. This method can safely be called from the UI thread.
     */
    public static AccountTypeManager getInstance(Context context) {
        if (!hasRequiredPermissions(context)) {
            // Hopefully any component that depends on the values returned by this class
            // will be restarted if the permissions change.
            return EMPTY;
        }
        synchronized (mInitializationLock) {
            if (mAccountTypeManager == null) {
                context = context.getApplicationContext();
                mAccountTypeManager = new AccountTypeManagerImpl(context);
            }
        }
        return mAccountTypeManager;
    }

    /**
     * Set the instance of account type manager.  This is only for and should only be used by unit
     * tests.  While having this method is not ideal, it's simpler than the alternative of
     * holding this as a service in the ContactsApplication context class.
     *
     * @param mockManager The mock AccountTypeManager.
     */
    public static void setInstanceForTest(AccountTypeManager mockManager) {
        synchronized (mInitializationLock) {
            mAccountTypeManager = mockManager;
        }
    }

    private static final AccountTypeManager EMPTY = new AccountTypeManager() {

        @Override
        public ListenableFuture<List<AccountInfo>> getAccountsAsync() {
            return Futures.immediateFuture(Collections.<AccountInfo>emptyList());
        }

        @Override
        public ListenableFuture<List<AccountInfo>> filterAccountsAsync(
                Predicate<AccountInfo> filter) {
            return Futures.immediateFuture(Collections.<AccountInfo>emptyList());
        }

        @Override
        public AccountInfo getAccountInfoForAccount(AccountWithDataSet account) {
            return null;
        }

        @Override
        public Account getDefaultGoogleAccount() {
            return null;
        }

        @Override
        public AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet) {
            return null;
        }
    };

    /**
     * Returns the list of all accounts (if contactWritableOnly is false) or just the list of
     * contact writable accounts (if contactWritableOnly is true).
     *
     * <p>TODO(mhagerott) delete this method. It's left in place to prevent build breakages when
     * this change is automerged. Usages of this method in downstream branches should be
     * replaced with an asynchronous account loading pattern</p>
     */
    public List<AccountWithDataSet> getAccounts(boolean contactWritableOnly) {
        return contactWritableOnly
                ? blockForWritableAccounts()
                : AccountInfo.extractAccounts(Futures.getUnchecked(getAccountsAsync()));
    }

    /**
     * Returns all contact writable accounts
     *
     * <p>In general this method should be avoided. It exists to support some legacy usages of
     * accounts in infrequently used features where refactoring to asynchronous loading is
     * not justified. The chance that this will actually block is pretty low if the app has been
     * launched previously</p>
     */
    public List<AccountWithDataSet> blockForWritableAccounts() {
        return AccountInfo.extractAccounts(
                Futures.getUnchecked(filterAccountsAsync(AccountFilter.CONTACTS_WRITABLE)));
    }

    /**
     * Loads accounts in background and returns future that will complete with list of all accounts
     */
    public abstract ListenableFuture<List<AccountInfo>> getAccountsAsync();

    /**
     * Loads accounts and applies the fitler returning only for which the predicate is true
     */
    public abstract ListenableFuture<List<AccountInfo>> filterAccountsAsync(
            Predicate<AccountInfo> filter);

    public abstract AccountInfo getAccountInfoForAccount(AccountWithDataSet account);

    /**
     * Returns the default google account.
     */
    public abstract Account getDefaultGoogleAccount();

    /**
     * Returns the Google Accounts.
     *
     * <p>This method exists in addition to filterAccountsByTypeAsync because it should be safe
     * to call synchronously.
     * </p>
     */
    public List<AccountInfo> getWritableGoogleAccounts() {
        // This implementation may block and should be overridden by the Impl class
        return Futures.getUnchecked(filterAccountsAsync(new Predicate<AccountInfo>() {
            @Override
            public boolean apply(@Nullable AccountInfo input) {
                return  input.getType().areContactsWritable() &&
                        GoogleAccountType.ACCOUNT_TYPE.equals(input.getType().accountType);
            }
        }));
    }

    /**
     * Returns true if there are real accounts (not "local" account) in the list of accounts.
     */
    public boolean hasNonLocalAccount() {
        final List<AccountWithDataSet> allAccounts =
                AccountInfo.extractAccounts(Futures.getUnchecked(getAccountsAsync()));
        if (allAccounts == null || allAccounts.size() == 0) {
            return false;
        }
        if (allAccounts.size() > 1) {
            return true;
        }
        return !allAccounts.get(0).isNullAccount();
    }

    static Account getDefaultGoogleAccount(AccountManager accountManager,
            SharedPreferences prefs, String defaultAccountKey) {
        // Get all the google accounts on the device
        final Account[] accounts = accountManager.getAccountsByType(
                GoogleAccountType.ACCOUNT_TYPE);
        if (accounts == null || accounts.length == 0) {
            return null;
        }

        // Get the default account from preferences
        final String defaultAccount = prefs.getString(defaultAccountKey, null);
        final AccountWithDataSet accountWithDataSet = defaultAccount == null ? null :
                AccountWithDataSet.unstringify(defaultAccount);

        // Look for an account matching the one from preferences
        if (accountWithDataSet != null) {
            for (int i = 0; i < accounts.length; i++) {
                if (TextUtils.equals(accountWithDataSet.name, accounts[i].name)
                        && TextUtils.equals(accountWithDataSet.type, accounts[i].type)) {
                    return accounts[i];
                }
            }
        }

        // Just return the first one
        return accounts[0];
    }

    public abstract AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet);

    public final AccountType getAccountType(String accountType, String dataSet) {
        return getAccountType(AccountTypeWithDataSet.get(accountType, dataSet));
    }

    public final AccountType getAccountTypeForAccount(AccountWithDataSet account) {
        if (account != null) {
            return getAccountType(account.getAccountTypeWithDataSet());
        }
        return getAccountType(null, null);
    }

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link AccountType#accountType}, {@link AccountType#dataSet}, and {@link DataKind#mimeType}.
     * If no direct match found, we try searching {@link FallbackAccountType}.
     */
    public DataKind getKindOrFallback(AccountType type, String mimeType) {
        return type == null ? null : type.getKindForMimetype(mimeType);
    }

    /**
     * Returns whether the specified account still exists
     */
    public boolean exists(AccountWithDataSet account) {
        final List<AccountWithDataSet> accounts =
                AccountInfo.extractAccounts(Futures.getUnchecked(getAccountsAsync()));
        return accounts.contains(account);
    }

    /**
     * Returns whether the specified account is writable
     *
     * <p>This checks that the account still exists and that
     * {@link AccountType#areContactsWritable()} is true</p>
     */
    public boolean isWritable(AccountWithDataSet account) {
        return exists(account) && getAccountInfoForAccount(account).getType().areContactsWritable();
    }

    public boolean hasGoogleAccount() {
        return getDefaultGoogleAccount() != null;
    }

    private static boolean hasRequiredPermissions(Context context) {
        final boolean canGetAccounts = ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED;
        final boolean canReadContacts = ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        return canGetAccounts && canReadContacts;
    }

    public static Predicate<AccountInfo> writableFilter() {
        return AccountFilter.CONTACTS_WRITABLE;
    }

    public static Predicate<AccountInfo> groupWritableFilter() {
        return AccountFilter.GROUPS_WRITABLE;
    }
}

class AccountTypeManagerImpl extends AccountTypeManager
        implements OnAccountsUpdateListener, SyncStatusObserver {

    private final Context mContext;
    private final AccountManager mAccountManager;
    private final DeviceLocalAccountLocator mLocalAccountLocator;
    private final Executor mMainThreadExecutor;
    private final ListeningExecutorService mExecutor;
    private AccountTypeProvider mTypeProvider;

    private final AccountType mFallbackAccountType;

    private ListenableFuture<List<AccountWithDataSet>> mLocalAccountsFuture;
    private ListenableFuture<AccountTypeProvider> mAccountTypesFuture;

    private List<AccountWithDataSet> mLocalAccounts = new ArrayList<>();
    private List<AccountWithDataSet> mAccountManagerAccounts = new ArrayList<>();

    private final Handler mMainThreadHandler = new Handler(Looper.getMainLooper());

    private final Function<AccountTypeProvider, List<AccountWithDataSet>> mAccountsExtractor =
            new Function<AccountTypeProvider, List<AccountWithDataSet>>() {
                @Nullable
                @Override
                public List<AccountWithDataSet> apply(@Nullable AccountTypeProvider typeProvider) {
                    return getAccountsWithDataSets(mAccountManager.getAccounts(), typeProvider);
                }
            };


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Don't use reloadAccountTypesIfNeeded when packages change in case a contacts.xml
            // was updated.
            reloadAccountTypes();
        }
    };

    /**
     * Internal constructor that only performs initial parsing.
     */
    public AccountTypeManagerImpl(Context context) {
        mContext = context;
        mLocalAccountLocator = DeviceLocalAccountLocator.create(context);
        mTypeProvider = new AccountTypeProvider(context);
        mFallbackAccountType = new FallbackAccountType(context);

        mAccountManager = AccountManager.get(mContext);

        mExecutor = ContactsExecutors.getDefaultThreadPoolExecutor();
        mMainThreadExecutor = ContactsExecutors.newHandlerExecutor(mMainThreadHandler);

        // Request updates when packages or accounts change
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mBroadcastReceiver, filter);
        IntentFilter sdFilter = new IntentFilter();
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        sdFilter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        mContext.registerReceiver(mBroadcastReceiver, sdFilter);

        // Request updates when locale is changed so that the order of each field will
        // be able to be changed on the locale change.
        filter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mBroadcastReceiver, filter);

        mAccountManager.addOnAccountsUpdatedListener(this, mMainThreadHandler, false);

        ContentResolver.addStatusChangeListener(ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this);

        // Observe changes to RAW_CONTACTS so that we will update the list of "Device" accounts
        // if a new device contact is added or removed.
        mContext.getContentResolver().registerContentObserver(
                ContactsContract.RawContacts.CONTENT_URI, /* notifyDescendents */ true,
                new ContentObserver(mMainThreadHandler) {
                    @Override
                    public boolean deliverSelfNotifications() {
                        return true;
                    }

                    @Override
                    public void onChange(boolean selfChange) {
                        reloadLocalAccounts();
                    }

                    @Override
                    public void onChange(boolean selfChange, Uri uri) {
                        reloadLocalAccounts();
                    }
                });
        loadAccountTypes();
    }

    @Override
    public void onStatusChanged(int which) {
        reloadAccountTypesIfNeeded();
    }

    /* This notification will arrive on the UI thread */
    public void onAccountsUpdated(Account[] accounts) {
        reloadLocalAccounts();
        maybeNotifyAccountsUpdated(mAccountManagerAccounts,
                getAccountsWithDataSets(accounts, mTypeProvider));
    }

    private void maybeNotifyAccountsUpdated(List<AccountWithDataSet> current,
            List<AccountWithDataSet> update) {
        if (Objects.equal(current, update)) {
            return;
        }
        current.clear();
        current.addAll(update);
        notifyAccountsChanged();
    }

    private void notifyAccountsChanged() {
        ContactListFilterController.getInstance(mContext).checkFilterValidity(true);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(
                new Intent(BROADCAST_ACCOUNTS_CHANGED));
    }

    private synchronized void startLoadingIfNeeded() {
        if (mTypeProvider == null && mAccountTypesFuture == null) {
            reloadAccountTypesIfNeeded();
        }
        if (mLocalAccountsFuture == null) {
            reloadLocalAccounts();
        }
    }

    private synchronized void loadAccountTypes() {
        mTypeProvider = new AccountTypeProvider(mContext);

        mAccountTypesFuture = mExecutor.submit(new Callable<AccountTypeProvider>() {
            @Override
            public AccountTypeProvider call() throws Exception {
                // This will request the AccountType for each Account forcing them to be loaded
                getAccountsWithDataSets(mAccountManager.getAccounts(), mTypeProvider);
                return mTypeProvider;
            }
        });
    }

    private FutureCallback<List<AccountWithDataSet>> newAccountsUpdatedCallback(
            final List<AccountWithDataSet> currentAccounts) {
        return new FutureCallback<List<AccountWithDataSet>>() {
            @Override
            public void onSuccess(List<AccountWithDataSet> result) {
                maybeNotifyAccountsUpdated(currentAccounts, result);
            }

            @Override
            public void onFailure(Throwable t) {
            }
        };
    }

    private synchronized void reloadAccountTypesIfNeeded() {
        if (mTypeProvider == null || mTypeProvider.shouldUpdate(
                mAccountManager.getAuthenticatorTypes(), ContentResolver.getSyncAdapterTypes())) {
            reloadAccountTypes();
        }
    }

    private synchronized void reloadAccountTypes() {
        loadAccountTypes();
        Futures.addCallback(
                Futures.transform(mAccountTypesFuture, mAccountsExtractor,
                        MoreExecutors.directExecutor()),
                newAccountsUpdatedCallback(mAccountManagerAccounts),
                mMainThreadExecutor);
    }

    private synchronized void loadLocalAccounts() {
        mLocalAccountsFuture = mExecutor.submit(new Callable<List<AccountWithDataSet>>() {
            @Override
            public List<AccountWithDataSet> call() throws Exception {
                return mLocalAccountLocator.getDeviceLocalAccounts();
            }
        });
    }

    private synchronized void reloadLocalAccounts() {
        loadLocalAccounts();
        Futures.addCallback(mLocalAccountsFuture, newAccountsUpdatedCallback(mLocalAccounts),
                mMainThreadExecutor);
    }

    @Override
    public ListenableFuture<List<AccountInfo>> getAccountsAsync() {
        return getAllAccountsAsyncInternal();
    }

    private synchronized ListenableFuture<List<AccountInfo>> getAllAccountsAsyncInternal() {
        startLoadingIfNeeded();
        final AccountTypeProvider typeProvider = mTypeProvider;
        final ListenableFuture<List<List<AccountWithDataSet>>> all =
                Futures.nonCancellationPropagating(
                        Futures.successfulAsList(
                                Futures.transform(mAccountTypesFuture, mAccountsExtractor,
                                        MoreExecutors.directExecutor()),
                                mLocalAccountsFuture));

        return Futures.transform(all, new Function<List<List<AccountWithDataSet>>,
                List<AccountInfo>>() {
            @Nullable
            @Override
            public List<AccountInfo> apply(@Nullable List<List<AccountWithDataSet>> input) {
                // input.get(0) contains accounts from AccountManager
                // input.get(1) contains device local accounts
                Preconditions.checkArgument(input.size() == 2,
                        "List should have exactly 2 elements");

                final List<AccountInfo> result = new ArrayList<>();
                for (AccountWithDataSet account : input.get(0)) {
                    result.add(
                            typeProvider.getTypeForAccount(account).wrapAccount(mContext, account));
                }

                for (AccountWithDataSet account : input.get(1)) {
                    result.add(
                            typeProvider.getTypeForAccount(account).wrapAccount(mContext, account));
                }
                AccountInfo.sortAccounts(null, result);
                return result;
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    public ListenableFuture<List<AccountInfo>> filterAccountsAsync(
            final Predicate<AccountInfo> filter) {
        return Futures.transform(getAllAccountsAsyncInternal(), new Function<List<AccountInfo>,
                List<AccountInfo>>() {
            @Override
            public List<AccountInfo> apply(List<AccountInfo> input) {
                return new ArrayList<>(Collections2.filter(input, filter));
            }
        }, mExecutor);
    }

    @Override
    public AccountInfo getAccountInfoForAccount(AccountWithDataSet account) {
        if (account == null) {
            return null;
        }
        AccountType type = mTypeProvider.getTypeForAccount(account);
        if (type == null) {
            type = mFallbackAccountType;
        }
        return type.wrapAccount(mContext, account);
    }

    private List<AccountWithDataSet> getAccountsWithDataSets(Account[] accounts,
            AccountTypeProvider typeProvider) {
        List<AccountWithDataSet> result = new ArrayList<>();
        for (Account account : accounts) {
            final List<AccountType> types = typeProvider.getAccountTypes(account.type);
            for (AccountType type : types) {
                result.add(new AccountWithDataSet(
                        account.name, account.type, type.dataSet));
            }
        }
        return result;
    }

    /**
     * Returns the default google account specified in preferences, the first google account
     * if it is not specified in preferences or is no longer on the device, and null otherwise.
     */
    @Override
    public Account getDefaultGoogleAccount() {
        final SharedPreferences sharedPreferences =
                mContext.getSharedPreferences(mContext.getPackageName(), Context.MODE_PRIVATE);
        final String defaultAccountKey =
                mContext.getResources().getString(R.string.contact_editor_default_account_key);
        return getDefaultGoogleAccount(mAccountManager, sharedPreferences, defaultAccountKey);
    }

    @Override
    public List<AccountInfo> getWritableGoogleAccounts() {
        final Account[] googleAccounts =
                mAccountManager.getAccountsByType(GoogleAccountType.ACCOUNT_TYPE);
        final List<AccountInfo> result = new ArrayList<>();
        for (Account account : googleAccounts) {
            final AccountWithDataSet accountWithDataSet = new AccountWithDataSet(
                    account.name, account.type, null);
            final AccountType type = mTypeProvider.getTypeForAccount(accountWithDataSet);
            if (type != null) {
                // Accounts with a dataSet (e.g. Google plus accounts) are not writable.
                result.add(type.wrapAccount(mContext, accountWithDataSet));
            }
        }
        return result;
    }

    /**
     * Returns true if there are real accounts (not "local" account) in the list of accounts.
     *
     * <p>This is overriden for performance since the default implementation blocks until all
     * accounts are loaded
     * </p>
     */
    @Override
    public boolean hasNonLocalAccount() {
        final Account[] accounts = mAccountManager.getAccounts();
        if (accounts == null) {
            return false;
        }
        for (Account account : accounts) {
            if (mTypeProvider.supportsContactsSyncing(account.type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the best {@link DataKind} matching the requested
     * {@link AccountType#accountType}, {@link AccountType#dataSet}, and {@link DataKind#mimeType}.
     * If no direct match found, we try searching {@link FallbackAccountType}.
     */
    @Override
    public DataKind getKindOrFallback(AccountType type, String mimeType) {
        DataKind kind = null;

        // Try finding account type and kind matching request
        if (type != null) {
            kind = type.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            // Nothing found, so try fallback as last resort
            kind = mFallbackAccountType.getKindForMimetype(mimeType);
        }

        if (kind == null) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Unknown type=" + type + ", mime=" + mimeType);
            }
        }

        return kind;
    }

    /**
     * Returns whether the account still exists on the device
     *
     * <p>This is overridden for performance. The default implementation loads all accounts then
     * searches through them for specified. This implementation will only load the types for the
     * specified AccountType (it may still require blocking on IO in some cases but it shouldn't
     * be as bad as blocking for all accounts).
     * </p>
     */
    @Override
    public boolean exists(AccountWithDataSet account) {
        final Account[] accounts = mAccountManager.getAccountsByType(account.type);
        for (Account existingAccount : accounts) {
            if (existingAccount.name.equals(account.name)) {
                return mTypeProvider.getTypeForAccount(account) != null;
            }
        }
        return false;
    }

    /**
     * Return {@link AccountType} for the given account type and data set.
     */
    @Override
    public AccountType getAccountType(AccountTypeWithDataSet accountTypeWithDataSet) {
        final AccountType type = mTypeProvider.getType(
                accountTypeWithDataSet.accountType, accountTypeWithDataSet.dataSet);
        return type != null ? type : mFallbackAccountType;
    }
}
