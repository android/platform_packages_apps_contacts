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

import com.android.contacts.R;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.google.android.collect.Lists;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;

import java.util.ArrayList;

public class GoogleSource extends FallbackSource {
    public static final String ACCOUNT_TYPE = "com.google";

    private static final String SELECTION_GROUPS_BY_TITLE_AND_ACCOUNT =
            Groups.TITLE + "=? AND " + Groups.ACCOUNT_NAME + "=? AND " + Groups.ACCOUNT_TYPE + "=?";

    public GoogleSource(String resPackageName) {
        this.accountType = ACCOUNT_TYPE;
        this.resPackageName = null;
        this.summaryResPackageName = resPackageName;
    }

    @Override
    protected void inflate(Context context, int inflateLevel) {

        inflateStructuredName(context, inflateLevel);
        inflateNickname(context, inflateLevel);
        inflatePhone(context, inflateLevel);
        inflateEmail(context, inflateLevel);
        inflateStructuredPostal(context, inflateLevel);
        inflateIm(context, inflateLevel);
        inflateOrganization(context, inflateLevel);
        inflatePhoto(context, inflateLevel);
        inflateNote(context, inflateLevel);
        inflateWebsite(context, inflateLevel);
        inflateEvent(context, inflateLevel);
        inflateSipAddress(context, inflateLevel);

        // TODO: GOOGLE: GROUPMEMBERSHIP

        setInflatedLevel(inflateLevel);

    }

    @Override
    protected DataKind inflatePhone(Context context, int inflateLevel) {
        final DataKind kind = super.inflatePhone(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeColumn = Phone.TYPE;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(buildPhoneType(Phone.TYPE_MOBILE));
            kind.typeList.add(buildPhoneType(Phone.TYPE_HOME));
            kind.typeList.add(buildPhoneType(Phone.TYPE_WORK));
            kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_WORK).setSecondary(true));
            kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_HOME).setSecondary(true));
            kind.typeList.add(buildPhoneType(Phone.TYPE_PAGER).setSecondary(true));
            kind.typeList.add(buildPhoneType(Phone.TYPE_OTHER));
            kind.typeList.add(buildPhoneType(Phone.TYPE_CUSTOM).setSecondary(true).setCustomColumn(
                    Phone.LABEL));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));
        }

        return kind;
    }

    @Override
    protected DataKind inflateEmail(Context context, int inflateLevel) {
        final DataKind kind = super.inflateEmail(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeColumn = Email.TYPE;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(buildEmailType(Email.TYPE_HOME));
            kind.typeList.add(buildEmailType(Email.TYPE_WORK));
            kind.typeList.add(buildEmailType(Email.TYPE_OTHER));
            kind.typeList.add(buildEmailType(Email.TYPE_CUSTOM).setSecondary(true).setCustomColumn(
                    Email.LABEL));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));
        }

        return kind;
    }

    // TODO: this should come from resource in the future
    // Note that frameworks/base/core/java/android/pim/vcard/VCardEntry.java also wants
    // this String.
    private static final String GOOGLE_MY_CONTACTS_GROUP = "System Group: My Contacts";

    public static final void attemptMyContactsMembership(EntityDelta state, Context context) {
        final ValuesDelta stateValues = state.getValues();
	stateValues.setFromTemplate(true);
        final String accountName = stateValues.getAsString(RawContacts.ACCOUNT_NAME);
        final String accountType = stateValues.getAsString(RawContacts.ACCOUNT_TYPE);
        attemptMyContactsMembership(state, accountName, accountType, context, true);
    }

    public static final void createMyContactsIfNotExist(Account account, Context context) {
        attemptMyContactsMembership(null, account.name, account.type, context, true);
    }

    /**
     *
     * @param allowRecur If the group is created between querying/about to create, we recur.  But
     *     to prevent excess recursion, we provide a flag to make sure we only do the recursion loop
     *     once
     */
    private static final void attemptMyContactsMembership(EntityDelta state,
                final String accountName, final String accountType, Context context,
                boolean allowRecur) {
        final ContentResolver resolver = context.getContentResolver();

        Cursor cursor = resolver.query(Groups.CONTENT_URI,
                new String[] {Groups.TITLE, Groups.SOURCE_ID, Groups.SHOULD_SYNC},
                Groups.ACCOUNT_NAME + " =? AND " + Groups.ACCOUNT_TYPE + " =?",
                new String[] {accountName, accountType}, null);

        boolean myContactsExists = false;
        long assignToGroupSourceId = -1;
        while (cursor.moveToNext()) {
            if (GOOGLE_MY_CONTACTS_GROUP.equals(cursor.getString(0))) {
                myContactsExists = true;
            }
            if (assignToGroupSourceId == -1 && cursor.getInt(2) != 0) {
                assignToGroupSourceId = cursor.getInt(1);
            }

            if (myContactsExists && assignToGroupSourceId != -1) {
                break;
            }
        }

        if (myContactsExists && state == null) {
            return;
        }

        try {
            final ContentValues values = new ContentValues();
            values.put(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);

            if (!myContactsExists) {
                // create the group if it doesn't exist
                final ContentValues newGroup = new ContentValues();
                newGroup.put(Groups.TITLE, GOOGLE_MY_CONTACTS_GROUP);

                newGroup.put(Groups.ACCOUNT_NAME, accountName);
                newGroup.put(Groups.ACCOUNT_TYPE, accountType);
                newGroup.put(Groups.GROUP_VISIBLE, "1");

                ArrayList<ContentProviderOperation> operations =
                    new ArrayList<ContentProviderOperation>();

                operations.add(ContentProviderOperation
                        .newAssertQuery(Groups.CONTENT_URI)
                        .withSelection(SELECTION_GROUPS_BY_TITLE_AND_ACCOUNT,
                                new String[] {GOOGLE_MY_CONTACTS_GROUP, accountName, accountType})
                        .withExpectedCount(0).build());
                operations.add(ContentProviderOperation
                        .newInsert(Groups.CONTENT_URI)
                        .withValues(newGroup)
                        .build());
                try {
                    ContentProviderResult[] results = resolver.applyBatch(
                            ContactsContract.AUTHORITY, operations);
                    values.put(GroupMembership.GROUP_ROW_ID, ContentUris.parseId(results[1].uri));
                } catch (RemoteException e) {
                    throw new IllegalStateException("Problem querying for groups", e);
                } catch (OperationApplicationException e) {
                    // the group was created after the query but before we tried to create it
                    if (allowRecur) {
                        attemptMyContactsMembership(
                                state, accountName, accountType, context, false);
                    }
                    return;
                }
            } else {
                if (assignToGroupSourceId != -1) {
                    values.put(GroupMembership.GROUP_SOURCE_ID, assignToGroupSourceId);
                } else {
                    // there are no Groups to add this contact to, so don't apply any membership
                    // TODO: alert user that their contact will be dropped?
                }
            }
            if (state != null) {
                state.addEntry(ValuesDelta.fromAfter(values));
            }
        } finally {
            cursor.close();
        }
    }

    @Override
    public int getHeaderColor(Context context) {
        return 0xff89c2c2;
    }

    @Override
    public int getSideBarColor(Context context) {
        return 0xff5bb4b4;
    }
}
