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

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.Data;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.android.contacts.model.account.AccountWithDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests of GroupsDaoImpl that perform DB operations directly against CP2
 */
@MediumTest
public class GroupsDaoIntegrationTests extends InstrumentationTestCase {

    private ContentResolver mResolver;
    private List<Uri> mTestRecords;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mTestRecords = new ArrayList<>();
        mResolver = getContext().getContentResolver();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // Cleanup anything leftover by the tests.
        cleanupTestRecords();
        mTestRecords.clear();
    }

    public void test_createGroup_createsGroupWithCorrectTitle() throws Exception {
        final ContactSaveService.GroupsDao sut = createDao();
        final Uri uri = sut.create("Test Create Group", getLocalAccount());

        assertNotNull(uri);
        assertGroupHasTitle(uri, "Test Create Group");
    }

    public void test_deleteEmptyGroup_marksRowDeleted() throws Exception {
        final ContactSaveService.GroupsDao sut = createDao();
        final Uri uri = sut.create("Test Delete Group", getLocalAccount());

        assertEquals(1, sut.delete(uri));

        final Cursor cursor = mResolver.query(uri, null, null, null, null, null);
        try {
            cursor.moveToFirst();
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow(
                    ContactsContract.Groups.DELETED)));
        } finally {
            cursor.close();
        }
    }

    public void test_undoDeleteEmptyGroup_createsGroupWithMatchingTitle() throws Exception {
        final ContactSaveService.GroupsDao sut = createDao();
        final Uri uri = sut.create("Test Undo Delete Empty Group", getLocalAccount());

        final Bundle undoData = sut.captureDeletionUndoData(uri);

        assertEquals(1, sut.delete(uri));

        final Uri groupUri = sut.undoDeletion(undoData);

        assertGroupHasTitle(groupUri, "Test Undo Delete Empty Group");
    }

    public void test_deleteNonEmptyGroup_removesGroupAndMembers() throws Exception {
        final ContactSaveService.GroupsDao sut = createDao();
        final Uri groupUri = sut.create("Test delete non-empty group", getLocalAccount());

        final long groupId = ContentUris.parseId(groupUri);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);

        assertEquals(1, sut.delete(groupUri));

        final Cursor cursor = mResolver.query(Data.CONTENT_URI, null,
                Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(groupId) },
                null, null);

        try {
            cursor.moveToFirst();
            // This is more of a characterization test since our code isn't manually deleting
            // the membership rows just the group but this still helps document the expected
            // behavior.
            assertEquals(0, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    public void test_undoDeleteNonEmptyGroup_restoresGroupAndMembers() throws Exception {
        final ContactSaveService.GroupsDao sut = createDao();
        final Uri groupUri = sut.create("Test undo delete non-empty group", getLocalAccount());

        final long groupId = ContentUris.parseId(groupUri);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);
        addMemberToGroup(ContentUris.parseId(createRawContact()), groupId);

        final Bundle undoData = sut.captureDeletionUndoData(groupUri);

        sut.delete(groupUri);

        final Uri recreatedGroup = sut.undoDeletion(undoData);

        final long newGroupId = ContentUris.parseId(recreatedGroup);

        final Cursor cursor = mResolver.query(Data.CONTENT_URI, null,
                Data.MIMETYPE + "=? AND " + GroupMembership.GROUP_ROW_ID + "=?",
                new String[] { GroupMembership.CONTENT_ITEM_TYPE, String.valueOf(newGroupId) },
                null, null);

        try {
            assertEquals(2, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    public void test_captureUndoDataForDeletedGroup_returnsEmptyBundle() {
        final ContactSaveService.GroupsDao sut = createDao();

        final Uri uri = sut.create("a deleted group", getLocalAccount());
        sut.delete(uri);

        final Bundle undoData = sut.captureDeletionUndoData(uri);

        assertTrue(undoData.isEmpty());
    }

    public void test_captureUndoDataForNonExistentGroup_returnsEmptyBundle() {
        final ContactSaveService.GroupsDao sut = createDao();

        // This test could potentially be flaky if this ID exists for some reason. 10 is subtracted
        // to reduce the likelihood of this happening; some other test may use Integer.MAX_VALUE
        // or nearby values  to cover some special case or boundary condition.
        final long nonExistentId = Integer.MAX_VALUE - 10;

        final Bundle undoData = sut.captureDeletionUndoData(ContentUris
                .withAppendedId(ContactsContract.Groups.CONTENT_URI, nonExistentId));

        assertTrue(undoData.isEmpty());
    }

    public void test_undoWithEmptyBundle_doesNothing() {
        final ContactSaveService.GroupsDao sut = createDao();

        final Uri uri = sut.undoDeletion(new Bundle());

        assertNull(uri);
    }

    public void test_undoDeleteEmptyGroupWithMissingMembersKey_shouldRecreateGroup() {
        final ContactSaveService.GroupsDao sut = createDao();
        final Uri groupUri = sut.create("Test undo delete null memberIds", getLocalAccount());

        final Bundle undoData = sut.captureDeletionUndoData(groupUri);
        undoData.remove(ContactSaveService.GroupsDaoImpl.KEY_GROUP_MEMBERS);
        sut.delete(groupUri);

        sut.undoDeletion(undoData);

        assertGroupWithTitleExists("Test undo delete null memberIds");
    }

    private void assertGroupHasTitle(Uri groupUri, String title) {
        final Cursor cursor = mResolver.query(groupUri,
                new String[] { ContactsContract.Groups.TITLE },
                ContactsContract.Groups.DELETED + "=?",
                new String[] { "0" }, null, null);
        try {
            assertTrue("Group does not have title \"" + title + "\"",
                    cursor.getCount() == 1 && cursor.moveToFirst() &&
                            title.equals(cursor.getString(0)));
        } finally {
            cursor.close();
        }
    }

    private void assertGroupWithTitleExists(String title) {
        final Cursor cursor = mResolver.query(ContactsContract.Groups.CONTENT_URI, null,
                ContactsContract.Groups.TITLE + "=? AND " +
                        ContactsContract.Groups.DELETED + "=?",
                new String[] { title, "0" }, null, null);
        try {
            assertTrue("No group exists with title \"" + title + "\"", cursor.getCount() > 0);
        } finally {
            cursor.close();
        }
    }

    public ContactSaveService.GroupsDao createDao() {
        return new GroupsDaoWrapper(new ContactSaveService.GroupsDaoImpl(getContext()));
    }

    private Uri createRawContact() {
        final ContentValues values = new ContentValues();
        values.putNull(ContactsContract.RawContacts.ACCOUNT_NAME);
        values.putNull(ContactsContract.RawContacts.ACCOUNT_TYPE);
        final Uri result = mResolver.insert(ContactsContract.RawContacts.CONTENT_URI, values);
        mTestRecords.add(result);
        return result;
    }

    private Uri addMemberToGroup(long rawContactId, long groupId) {
        final ContentValues values = new ContentValues();
        values.put(Data.RAW_CONTACT_ID, rawContactId);
        values.put(Data.MIMETYPE,
                GroupMembership.CONTENT_ITEM_TYPE);
        values.put(GroupMembership.GROUP_ROW_ID, groupId);

        // Dont' need to add to testRecords because it will be cleaned up when parent raw_contact
        // is deleted.
        return mResolver.insert(Data.CONTENT_URI, values);
    }

    private Context getContext() {
        return getInstrumentation().getTargetContext();
    }

    private AccountWithDataSet getLocalAccount() {
        return new AccountWithDataSet(null, null, null);
    }

    private void cleanupTestRecords() throws RemoteException, OperationApplicationException {
        final ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        for (Uri uri : mTestRecords) {
            if (uri == null) continue;
            ops.add(ContentProviderOperation
                    .newDelete(uri.buildUpon()
                            .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                            .build())
                    .build());
        }
        mResolver.applyBatch(ContactsContract.AUTHORITY, ops);
    }

    private class GroupsDaoWrapper implements ContactSaveService.GroupsDao {
        private final ContactSaveService.GroupsDao mDelegate;

        public GroupsDaoWrapper(ContactSaveService.GroupsDao delegate) {
            mDelegate = delegate;
        }

        @Override
        public Uri create(String title, AccountWithDataSet account) {
            final Uri result = mDelegate.create(title, account);
            mTestRecords.add(result);
            return result;
        }

        @Override
        public int delete(Uri groupUri) {
            return mDelegate.delete(groupUri);
        }

        @Override
        public Bundle captureDeletionUndoData(Uri groupUri) {
            return mDelegate.captureDeletionUndoData(groupUri);
        }

        @Override
        public Uri undoDeletion(Bundle undoData) {
            final Uri result = mDelegate.undoDeletion(undoData);
            mTestRecords.add(result);
            return result;
        }
    }
}
