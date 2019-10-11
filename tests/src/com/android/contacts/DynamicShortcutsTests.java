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
package com.android.contacts;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.annotation.TargetApi;
import android.app.job.JobScheduler;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.test.AndroidTestCase;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.filters.SdkSuppress;

import com.android.contacts.test.mocks.MockContentProvider;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@TargetApi(Build.VERSION_CODES.N_MR1)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N_MR1)
@SmallTest
public class DynamicShortcutsTests extends AndroidTestCase {


    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // Clean up the job if it was scheduled by these tests.
        final JobScheduler scheduler = (JobScheduler) getContext()
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancel(ContactsJobService.DYNAMIC_SHORTCUTS_JOB_ID);
    }

    // Basic smoke test to make sure the queries executed by DynamicShortcuts are valid as well
    // as the integration with ShortcutManager. Note that this may change the state of the shortcuts
    // on the device it is executed on.
    public void test_refresh_doesntCrash() {
        final DynamicShortcuts sut = new DynamicShortcuts(getContext());
        sut.refresh();
        // Pass because it didn't throw an exception.
    }

    public void test_createShortcutFromRow_hasCorrectResult() {
        final DynamicShortcuts sut = createDynamicShortcuts();

        final Cursor row = queryResult(
                // ID, LOOKUP_KEY, DISPLAY_NAME_PRIMARY
                1l, "lookup_key", "John Smith"
        );

        row.moveToFirst();
        final ShortcutInfo shortcut = sut.builderForContactShortcut(row).build();

        assertEquals("lookup_key", shortcut.getId());
        assertEquals(Contacts.getLookupUri(1, "lookup_key"), shortcut.getIntent().getData());
        assertEquals(ContactsContract.QuickContact.ACTION_QUICK_CONTACT,
                shortcut.getIntent().getAction());
        assertEquals("John Smith", shortcut.getShortLabel());
        assertEquals("John Smith", shortcut.getLongLabel());
        assertEquals(1l, shortcut.getExtras().getLong(Contacts._ID));
    }

    public void test_builderForContactShortcut_returnsNullWhenNameIsNull() {
        final DynamicShortcuts sut = createDynamicShortcuts();

        final ShortcutInfo.Builder shortcut = sut.builderForContactShortcut(1l, "lookup_key", null);

        assertNull(shortcut);
    }

    public void test_builderForContactShortcut_ellipsizesLongNamesForLabels() {
        final DynamicShortcuts sut = createDynamicShortcuts();
        sut.setShortLabelMaxLength(5);
        sut.setLongLabelMaxLength(10);

        final ShortcutInfo shortcut = sut.builderForContactShortcut(1l, "lookup_key",
                "123456789 1011").build();

        assertEquals("1234…", shortcut.getShortLabel());
        assertEquals("123456789…", shortcut.getLongLabel());
    }

    public void test_updatePinned_disablesShortcutsForRemovedContacts() throws Exception {
        final ShortcutManager mockShortcutManager = mock(ShortcutManager.class);
        when(mockShortcutManager.getPinnedShortcuts()).thenReturn(
                Collections.singletonList(makeDynamic(shortcutFor(1l, "key1", "name1"))));

        final DynamicShortcuts sut = createDynamicShortcuts(emptyResolver(), mockShortcutManager);

        sut.updatePinned();

        verify(mockShortcutManager).disableShortcuts(
                eq(Collections.singletonList("key1")), anyString());
    }

    public void test_updatePinned_updatesExistingShortcutsWithMatchingKeys() throws Exception {
        final ShortcutManager mockShortcutManager = mock(ShortcutManager.class);
        when(mockShortcutManager.getPinnedShortcuts()).thenReturn(
                Arrays.asList(
                        makeDynamic(shortcutFor(1l, "key1", "name1")),
                        makeDynamic(shortcutFor(2l, "key2", "name2")),
                        makeDynamic(shortcutFor(3l, "key3", "name3"))
                ));

        final DynamicShortcuts sut = createDynamicShortcuts(resolverWithExpectedQueries(
                queryForSingleRow(Contacts.getLookupUri(1l, "key1"), 11l, "key1", "New Name1"),
                queryForSingleRow(Contacts.getLookupUri(2l, "key2"), 2l, "key2", "name2"),
                queryForSingleRow(Contacts.getLookupUri(3l, "key3"), 33l, "key3", "name3")
        ), mockShortcutManager);

        sut.updatePinned();

        final ArgumentCaptor<List<ShortcutInfo>> updateArgs =
                ArgumentCaptor.forClass((Class) List.class);

        verify(mockShortcutManager).disableShortcuts(
                eq(Collections.<String>emptyList()), anyString());
        verify(mockShortcutManager).updateShortcuts(updateArgs.capture());

        final List<ShortcutInfo> arg = updateArgs.getValue();
        assertThat(arg.size(), equalTo(3));
        assertThat(arg.get(0),
                isShortcutForContact(11l, "key1", "New Name1"));
        assertThat(arg.get(1),
                isShortcutForContact(2l, "key2", "name2"));
        assertThat(arg.get(2),
                isShortcutForContact(33l, "key3", "name3"));
    }

    public void test_refresh_setsDynamicShortcutsToStrequentContacts() {
        final ShortcutManager mockShortcutManager = mock(ShortcutManager.class);
        when(mockShortcutManager.getPinnedShortcuts()).thenReturn(
                Collections.<ShortcutInfo>emptyList());
        final DynamicShortcuts sut = createDynamicShortcuts(resolverWithExpectedQueries(
                queryFor(Contacts.CONTENT_STREQUENT_URI,
                        1l, "starred_key", "starred name",
                        2l, "freq_key", "freq name",
                        3l, "starred_2", "Starred Two")), mockShortcutManager);

        sut.refresh();

        final ArgumentCaptor<List<ShortcutInfo>> updateArgs =
                ArgumentCaptor.forClass((Class) List.class);

        verify(mockShortcutManager).setDynamicShortcuts(updateArgs.capture());

        final List<ShortcutInfo> arg = updateArgs.getValue();
        assertThat(arg.size(), equalTo(3));
        assertThat(arg.get(0), isShortcutForContact(1l, "starred_key", "starred name"));
        assertThat(arg.get(1), isShortcutForContact(2l, "freq_key", "freq name"));
        assertThat(arg.get(2), isShortcutForContact(3l, "starred_2", "Starred Two"));
    }

    public void test_refresh_skipsContactsWithNullName() {
        final ShortcutManager mockShortcutManager = mock(ShortcutManager.class);
        when(mockShortcutManager.getPinnedShortcuts()).thenReturn(
                Collections.<ShortcutInfo>emptyList());
        final DynamicShortcuts sut = createDynamicShortcuts(resolverWithExpectedQueries(
                queryFor(Contacts.CONTENT_STREQUENT_URI,
                        1l, "key1", "first",
                        2l, "key2", "second",
                        3l, "key3", null,
                        4l, null, null,
                        5l, "key5", "fifth",
                        6l, "key6", "sixth")), mockShortcutManager);

        sut.refresh();

        final ArgumentCaptor<List<ShortcutInfo>> updateArgs =
                ArgumentCaptor.forClass((Class) List.class);

        verify(mockShortcutManager).setDynamicShortcuts(updateArgs.capture());

        final List<ShortcutInfo> arg = updateArgs.getValue();
        assertThat(arg.size(), equalTo(3));
        assertThat(arg.get(0), isShortcutForContact(1l, "key1", "first"));
        assertThat(arg.get(1), isShortcutForContact(2l, "key2", "second"));
        assertThat(arg.get(2), isShortcutForContact(5l, "key5", "fifth"));


        // Also verify that it doesn't crash if there are fewer than 3 valid strequent contacts
        createDynamicShortcuts(resolverWithExpectedQueries(
                queryFor(Contacts.CONTENT_STREQUENT_URI,
                        1l, "key1", "first",
                        2l, "key2", "second",
                        3l, "key3", null,
                        4l, null, null)), mock(ShortcutManager.class)).refresh();
    }


    public void test_handleFlagDisabled_stopsJob() {
        final ShortcutManager mockShortcutManager = mock(ShortcutManager.class);
        final JobScheduler mockJobScheduler = mock(JobScheduler.class);
        final DynamicShortcuts sut = createDynamicShortcuts(emptyResolver(), mockShortcutManager,
                mockJobScheduler);

        sut.handleFlagDisabled();

        verify(mockJobScheduler).cancel(eq(ContactsJobService.DYNAMIC_SHORTCUTS_JOB_ID));
    }


    public void test_scheduleUpdateJob_schedulesJob() {
        final DynamicShortcuts sut = new DynamicShortcuts(getContext());
        sut.scheduleUpdateJob();
        assertThat(DynamicShortcuts.isJobScheduled(getContext()), Matchers.is(true));
    }

    private Matcher<ShortcutInfo> isShortcutForContact(final long id,
            final String lookupKey, final String name) {
        return new BaseMatcher<ShortcutInfo>() {
            @Override
            public boolean matches(Object o) {
                if (!(o instanceof  ShortcutInfo)) return false;
                final ShortcutInfo other = (ShortcutInfo)o;
                return id == other.getExtras().getLong(Contacts._ID)
                        && lookupKey.equals(other.getId())
                        && name.equals(other.getLongLabel())
                        && name.equals(other.getShortLabel());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Should be a shortcut for contact with _ID=" + id +
                        " lookup=" + lookupKey + " and display_name=" + name);
            }
        };
    }

    private ShortcutInfo shortcutFor(long contactId, String lookupKey, String name) {
        return new DynamicShortcuts(getContext())
                .builderForContactShortcut(contactId, lookupKey, name).build();
    }

    private ContentResolver emptyResolver() {
        final MockContentProvider provider = new MockContentProvider();
        provider.expect(MockContentProvider.Query.forAnyUri())
                .withAnyProjection()
                .withAnySelection()
                .withAnySortOrder()
                .returnEmptyCursor();
        return resolverWithContactsProvider(provider);
    }

    private MockContentProvider.Query queryFor(Uri uri, Object... rows) {
        final MockContentProvider.Query query = MockContentProvider.Query
                .forUrisMatching(uri.getAuthority(), uri.getPath())
                .withProjection(DynamicShortcuts.PROJECTION)
                .withAnySelection()
                .withAnySortOrder();

        populateQueryRows(query, DynamicShortcuts.PROJECTION.length, rows);
        return query;
    }

    private MockContentProvider.Query queryForSingleRow(Uri uri, Object... row) {
        return new MockContentProvider.Query(uri)
                .withProjection(DynamicShortcuts.PROJECTION)
                .withAnySelection()
                .withAnySortOrder()
                .returnRow(row);
    }

    private ContentResolver resolverWithExpectedQueries(MockContentProvider.Query... queries) {
        final MockContentProvider provider = new MockContentProvider();
        for (MockContentProvider.Query query : queries) {
            provider.expect(query);
        }
        return resolverWithContactsProvider(provider);
    }

    private ContentResolver resolverWithContactsProvider(ContentProvider provider) {
        final MockContentResolver resolver = new MockContentResolver();
        resolver.addProvider(ContactsContract.AUTHORITY, provider);
        return resolver;
    }

    private DynamicShortcuts createDynamicShortcuts() {
        return createDynamicShortcuts(emptyResolver(), mock(ShortcutManager.class));
    }


    private DynamicShortcuts createDynamicShortcuts(ContentResolver resolver,
            ShortcutManager shortcutManager) {
        return createDynamicShortcuts(resolver, shortcutManager, mock(JobScheduler.class));
    }

    private DynamicShortcuts createDynamicShortcuts(ContentResolver resolver,
            ShortcutManager shortcutManager, JobScheduler jobScheduler) {
        final DynamicShortcuts result = new DynamicShortcuts(getContext(), resolver,
                shortcutManager, jobScheduler);
        // Use very long label limits to make checking shortcuts easier to understand
        result.setShortLabelMaxLength(100);
        result.setLongLabelMaxLength(100);
        return result;
    }

    private void populateQueryRows(MockContentProvider.Query query, int numColumns,
            Object... rows) {
        for (int i = 0; i < rows.length; i += numColumns) {
            Object[] row = new Object[numColumns];
            for (int j = 0; j < numColumns; j++) {
                row[j] = rows[i + j];
            }
            query.returnRow(row);
        }
    }

    private Cursor queryResult(Object... values) {
        return queryResult(DynamicShortcuts.PROJECTION, values);
    }

    // Ugly hack because the API is hidden. Alternative is to actually set the shortcut on the real
    // ShortcutManager but this seems simpler for now.
    private ShortcutInfo makeDynamic(ShortcutInfo shortcutInfo) throws Exception {
        final Method addFlagsMethod = ShortcutInfo.class.getMethod("addFlags", int.class);
        // 1 = FLAG_DYNAMIC
        addFlagsMethod.invoke(shortcutInfo, 1);
        return shortcutInfo;
    }

    private Cursor queryResult(String[] columns, Object... values) {
        MatrixCursor result = new MatrixCursor(new String[] {
                Contacts._ID, Contacts.LOOKUP_KEY,
                Contacts.DISPLAY_NAME_PRIMARY
        });
        for (int i = 0; i < values.length; i += columns.length) {
            MatrixCursor.RowBuilder builder = result.newRow();
            for (int j = 0; j < columns.length; j++) {
                builder.add(values[i + j]);
            }
        }
        return result;
    }
}
