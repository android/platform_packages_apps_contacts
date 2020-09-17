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
package com.android.contacts.activities;

import static com.android.contacts.tests.ContactsMatchers.DataCursor.hasMimeType;
import static com.android.contacts.tests.ContactsMatchers.hasRowMatching;
import static com.android.contacts.tests.ContactsMatchers.hasValueForColumn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.telephony.TelephonyManager;
import android.test.mock.MockContentResolver;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.contacts.SimImportService;
import com.android.contacts.database.SimContactDao;
import com.android.contacts.database.SimContactDaoImpl;
import com.android.contacts.model.SimCard;
import com.android.contacts.model.SimContact;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.test.mocks.ForwardingContentProvider;
import com.android.contacts.test.mocks.MockContentProvider;
import com.android.contacts.tests.AccountsTestHelper;
import com.android.contacts.tests.ContactsMatchers;
import com.android.contacts.tests.FakeSimContactDao;
import com.android.contacts.tests.StringableCursor;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * UI Tests for {@link SimImportActivity}
 *
 * These should probably be converted to espresso tests because espresso does a better job of
 * waiting for the app to be idle once espresso library is added
 */
//@Suppress
@LargeTest
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.M)
@TargetApi(Build.VERSION_CODES.M)
public class SimImportActivityTest {

    public static final int TIMEOUT = 100000;
    private Context mContext;
    private UiDevice mDevice;
    private Instrumentation mInstrumentation;
    private FakeSimContactDao mDao;
    private AccountsTestHelper mAccountHelper;
    private Activity mActivity;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDao = new FakeSimContactDao();
        SimContactDao.setFactoryForTest(Functions.<SimContactDao>constant(mDao));
        mDevice = UiDevice.getInstance(mInstrumentation);

        // Add some test accounts so that account picker is exercised
        mAccountHelper = new AccountsTestHelper();
        mAccountHelper.addTestAccount(mAccountHelper.generateAccountName("SimImportActivity1_"));
        mAccountHelper.addTestAccount(mAccountHelper.generateAccountName("SimImportActivity2_"));
        mAccountHelper.addTestAccount(mAccountHelper.generateAccountName("SimImportActivity3_"));
    }

    @After
    public void tearDown() throws Exception {
        SimContactDao.setFactoryForTest(SimContactDao.DEFAULT_FACTORY);
        mAccountHelper.cleanup();
        if (mActivity != null) {
            mActivity.finish();
            mInstrumentation.waitForIdleSync();
        }
    }

    @AfterClass
    public static void tearDownClass() {
        AccountsTestHelper.removeAccountsWithPrefix(
                InstrumentationRegistry.getTargetContext(), "SimImportActivity");
    }

    @Test
    public void shouldDisplaySimContacts() {
        mDao.addSim(someSimCard(),
                        new SimContact(1, "Sim One", "5550101"),
                        new SimContact(2, "Sim Two", null),
                        new SimContact(3, null, "5550103")
                );
        mActivity = mInstrumentation.startActivitySync(new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        mDevice.waitForIdle();

        assertTrue(mDevice.wait(Until.hasObject(By.text("Sim One")), TIMEOUT));

        assertTrue(mDevice.hasObject(By.text("Sim One")));
        assertTrue(mDevice.hasObject(By.text("Sim Two")));
        assertTrue(mDevice.hasObject(By.text("5550103")));
    }

    @Test
    public void shouldHaveEmptyState() {
        mDao.addSim(someSimCard());

        mInstrumentation.startActivitySync(new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        mDevice.waitForIdle();

        assertTrue(mDevice.wait(Until.hasObject(By.textStartsWith("No contacts")), TIMEOUT));
    }

    @Test
    public void smokeRotateInEmptyState() {
        mDao.addSim(someSimCard());

        mActivity = mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.textStartsWith("No contacts")), TIMEOUT));

        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mDevice.waitForIdle();

        assertTrue(mDevice.wait(Until.hasObject(By.textStartsWith("No contacts")), TIMEOUT));
    }

    @Test
    public void smokeRotateInNonEmptyState() throws Exception {
        mDao.addSim(someSimCard(), new SimContact(1, "Name One", "5550101"),
                new SimContact(2, "Name Two", "5550102"));

        mActivity = mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.textStartsWith("Name One")), TIMEOUT));

        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        mDevice.waitForIdle();

        assertTrue(mDevice.wait(Until.hasObject(By.textStartsWith("Name One")), TIMEOUT));
    }

    /**
     * Tests a complete import flow
     *
     * <p>Test case outline:</p>
     * <ul>
     * <li>Load SIM contacts
     * <li>Change to a specific target account
     * <li>Deselect 3 specific SIM contacts
     * <li>Rotate the screen to landscape
     * <li>Rotate the screen back to portrait
     * <li>Press the import button
     * <li>Wait for import to complete
     * <li>Query contacts in target account and verify that they match selected contacts
     * <li>Start import activity again
     * <li>Switch to target account
     * <li>Verify that previously imported contacts are disabled and not checked
     * </ul>
     *
     * <p>This mocks out the IccProvider and stubs the canReadSimContacts method to make it work on
     * an emulator but otherwise uses real dependency.
     * </p>
     */
    @Test
    public void selectionsAreImportedAndDisabledOnSubsequentImports() throws Exception {
        final AccountWithDataSet targetAccount = mAccountHelper.addTestAccount(
                mAccountHelper.generateAccountName("SimImportActivity0_targetAccount_"));

        final MockContentProvider iccProvider = new MockContentProvider();
        iccProvider.expect(MockContentProvider.Query.forAnyUri())
                .withDefaultProjection(new String[] {SimContactDaoImpl._ID, SimContactDaoImpl.NAME,
                        SimContactDaoImpl.NUMBER, SimContactDaoImpl.EMAILS })
                .anyNumberOfTimes()
                .returnRow(toCursorRow(new SimContact(1, "Import One", "5550101")))
                .returnRow(toCursorRow(new SimContact(2, "Skip Two", "5550102")))
                .returnRow(toCursorRow(new SimContact(3, "Import Three", "5550103")))
                .returnRow(toCursorRow(new SimContact(4, "Skip Four", "5550104")))
                .returnRow(toCursorRow(new SimContact(5, "Skip Five", "5550105")))
                .returnRow(toCursorRow(new SimContact(6, "Import Six", "5550106")));
        final MockContentResolver mockResolver = new MockContentResolver();
        mockResolver.addProvider("icc", iccProvider);
        final ContentProviderClient contactsProviderClient = mContext.getContentResolver()
                .acquireContentProviderClient(ContactsContract.AUTHORITY);
        mockResolver.addProvider(ContactsContract.AUTHORITY, new ForwardingContentProvider(
                contactsProviderClient));

        SimContactDao.setFactoryForTest(new Function<Context, SimContactDao>() {
            @Override
            public SimContactDao apply(Context input) {
                final SimContactDaoImpl spy = spy(new SimContactDaoImpl(
                        mContext, mockResolver,
                        (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE)));
                final SimCard sim = someSimCard();
                doReturn(true).when(spy).canReadSimContacts();
                doReturn(Collections.singletonList(sim)).when(spy).getSimCards();
                doReturn(sim).when(spy).getSimBySubscriptionId(anyInt());
                return spy;
            }
        });

        mActivity = mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.desc("Show more")), TIMEOUT));

        mDevice.findObject(By.desc("Show more")).clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.findObject(By.textContains("_targetAccount_")).click();

        assertTrue(mDevice.wait(Until.hasObject(By.text("Skip Two")), TIMEOUT));

        mDevice.findObject(By.text("Skip Two")).click();
        mDevice.findObject(By.text("Skip Four")).click();
        mDevice.findObject(By.text("Skip Five")).click();
        mDevice.waitForIdle();

        assertTrue(mDevice.hasObject(By.text("Skip Two").checked(false)));
        assertTrue(mDevice.hasObject(By.text("Skip Five").checked(false)));

        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        mDevice.wait(Until.hasObject(By.text("Import One")), TIMEOUT);
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
        mDevice.wait(Until.hasObject(By.text("Import One")), TIMEOUT);

        ListenableFuture<?> nextImportFuture = nextImportCompleteBroadcast();

        mDevice.findObject(By.text("IMPORT").clickable(true)).click();

        // Block until import completes
        nextImportFuture.get(TIMEOUT, TimeUnit.MILLISECONDS);

        final Cursor cursor = new StringableCursor(
                mContext.getContentResolver().query(Data.CONTENT_URI, null,
                        ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
                                ContactsContract.RawContacts.ACCOUNT_TYPE+ "=?",
                        new String[] {
                                targetAccount.name,
                                targetAccount.type
                        }, null));
        // 3 contacts imported with one row for name and one for phone
        assertThat(cursor, ContactsMatchers.hasCount(3 * 2));

        assertThat(cursor, hasRowMatching(allOf(
                hasMimeType(Phone.CONTENT_ITEM_TYPE),
                hasValueForColumn(Phone.DISPLAY_NAME, "Import One"),
                hasValueForColumn(Phone.NUMBER, "5550101")
        )));
        assertThat(cursor, hasRowMatching(allOf(
                hasMimeType(Phone.CONTENT_ITEM_TYPE),
                hasValueForColumn(Phone.DISPLAY_NAME, "Import Three"),
                hasValueForColumn(Phone.NUMBER, "5550103")
        )));
        assertThat(cursor, hasRowMatching(allOf(
                hasMimeType(Phone.CONTENT_ITEM_TYPE),
                hasValueForColumn(Phone.DISPLAY_NAME, "Import Six"),
                hasValueForColumn(Phone.NUMBER, "5550106")
        )));

        cursor.close();


        mActivity = mInstrumentation.startActivitySync(
                new Intent(mContext, SimImportActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        assertTrue(mDevice.wait(Until.hasObject(By.text("Import One")), TIMEOUT));

        mDevice.findObject(By.descStartsWith("Show more")).clickAndWait(Until.newWindow(), TIMEOUT);
        mDevice.findObject(By.textContains(targetAccount.name)).click();
        mDevice.waitForIdle();

        assertTrue(mDevice.wait(Until.hasObject(By.text("Import One").checked(false)), TIMEOUT));
        assertTrue(mDevice.hasObject(By.text("Import Three").checked(false)));
        assertTrue(mDevice.hasObject(By.text("Import Six").checked(false)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            contactsProviderClient.close();
        }
    }

    private ListenableFuture<Intent> nextImportCompleteBroadcast() {
        final SettableFuture<Intent> result = SettableFuture.create();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                result.set(intent);
                LocalBroadcastManager.getInstance(mContext).unregisterReceiver(this);
            }
        };
        LocalBroadcastManager.getInstance(mContext).registerReceiver(receiver, new IntentFilter(
                SimImportService.BROADCAST_SIM_IMPORT_COMPLETE));
        return result;
    }

    private Object[] toCursorRow(SimContact contact) {
        return new Object[] { contact.getId(), contact.getName(), contact.getPhone(), null };
    }

    private SimCard someSimCard() {
        return new SimCard("id", 1, "Carrier", "SIM", "18005550101", "us");
    }
}
