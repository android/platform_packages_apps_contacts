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

package com.android.contacts.tests.allintents;

import android.accounts.Account;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.RawContacts;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.android.contacts.group.GroupUtil;
import com.android.contacts.list.UiIntentActions;
import com.android.contacts.tests.R;
import com.android.contacts.tests.quickcontact.QuickContactTestsActivity;

import java.util.ArrayList;

/**
 * An activity that provides access to various modes of the contacts application.
 * Useful for manual and scripted tests.
 * <p>
 * Note: this class cannot depend (directly on indirectly) on anything outside the test package.
 */
@SuppressWarnings("deprecation")
public class AllIntentsActivity extends ListActivity
        implements SelectAccountDialogFragment.Listener {

    /** The name of the package of the contacts application. */
    private String mContactsPackageName;

    private static final String CONTACT_LIST_ACTIVITY_CLASS_NAME =
            "com.android.contacts.activities.PeopleActivity";

    public enum ContactsIntent {
        ACTION_PICK_CONTACT,
        ACTION_PICK_CONTACT_LEGACY,
        ACTION_PICK_PHONE,
        ACTION_PICK_PHONE_LEGACY,
        ACTION_PICK_POSTAL,
        ACTION_PICK_POSTAL_LEGACY,
        ACTION_PICK_EMAIL,
        ACTION_CREATE_SHORTCUT_CONTACT,
        ACTION_CREATE_SHORTCUT_DIAL,
        ACTION_CREATE_SHORTCUT_MESSAGE,
        ACTION_GET_CONTENT_CONTACT,
        ACTION_GET_CONTENT_CONTACT_LEGACY,
        ACTION_GET_CONTENT_PHONE,
        ACTION_GET_CONTENT_PHONE_LEGACY,
        ACTION_GET_CONTENT_POSTAL,
        ACTION_GET_CONTENT_POSTAL_LEGACY,
        ACTION_INSERT_OR_EDIT,
        ACTION_INSERT_OR_EDIT_PHONE_NUMBER,
        ACTION_INSERT_OR_EDIT_EMAIL_ADDRESS,
        ACTION_INSERT_GROUP,
        ACTION_SEARCH_CALL,
        ACTION_SEARCH_CONTACT,
        ACTION_SEARCH_EMAIL,
        ACTION_SEARCH_PHONE,
        ACTION_SEARCH_GENERAL,
        SEARCH_SUGGESTION_CLICKED_CONTACT,
        EDIT_CONTACT,
        EDIT_CONTACT_LOOKUP,
        EDIT_CONTACT_LOOKUP_ID,
        EDIT_RAW_CONTACT,
        EDIT_LEGACY,
        EDIT_NEW_CONTACT,
        EDIT_NEW_CONTACT_WITH_DATA,
        EDIT_NEW_CONTACT_FOR_ACCOUNT,
        EDIT_NEW_CONTACT_FOR_ACCOUNT_WITH_DATA,
        EDIT_NEW_RAW_CONTACT,
        EDIT_NEW_LEGACY,
        EDIT_GROUP,
        VIEW_CONTACT_WITHOUT_ID,
        VIEW_PERSON_WITHOUT_ID,
        VIEW_CONTACT,
        VIEW_CONTACT_LOOKUP,
        VIEW_CONTACT_LOOKUP_ID,
        VIEW_RAW_CONTACT,
        VIEW_LEGACY,
        VIEW_GROUP,
        QUICK_CONTACT_TESTS_ACTIVITY,
        LIST_DEFAULT,
        LIST_CONTACTS,
        LIST_ALL_CONTACTS,
        LIST_CONTACTS_WITH_PHONES,
        LIST_STARRED,
        LIST_FREQUENT,
        LIST_STREQUENT;

        public static ContactsIntent get(int ordinal) {
            return values()[ordinal];
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setListAdapter(new ArrayAdapter<String>(this, R.layout.intent_list_item,
                getResources().getStringArray(R.array.allIntents)));
        mContactsPackageName = getResources().getString(
                R.string.target_package_name);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        switch (ContactsIntent.get(position)) {
            case ACTION_PICK_CONTACT: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Contacts.CONTENT_URI));
                break;
            }
            case ACTION_PICK_CONTACT_LEGACY: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, People.CONTENT_URI));
                break;
            }
            case ACTION_PICK_PHONE: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Phone.CONTENT_URI));
                break;
            }
            case ACTION_PICK_PHONE_LEGACY: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Phones.CONTENT_URI));
                break;
            }
            case ACTION_PICK_POSTAL: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, StructuredPostal.CONTENT_URI));
                break;
            }
            case ACTION_PICK_POSTAL_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactMethods.CONTENT_POSTAL_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_PICK_EMAIL: {
                startContactSelectionActivityForResult(
                        new Intent(Intent.ACTION_PICK, Email.CONTENT_URI));
                break;
            }
            case ACTION_CREATE_SHORTCUT_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_CREATE_SHORTCUT_DIAL: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                bindIntentToClass(intent, "alias.DialShortcut");
                startActivityForResult(intent, 0);
                break;
            }
            case ACTION_CREATE_SHORTCUT_MESSAGE: {
                Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT);
                bindIntentToClass(intent, "alias.MessageShortcut");
                startActivityForResult(intent, 0);
                break;
            }
            case ACTION_GET_CONTENT_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_CONTACT_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(People.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_PHONE: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Phone.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_PHONE_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(Phones.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_POSTAL: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(StructuredPostal.CONTENT_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_GET_CONTENT_POSTAL_LEGACY: {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType(ContactMethods.CONTENT_POSTAL_ITEM_TYPE);
                startContactSelectionActivityForResult(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                putDataExtra(intent);
                startActivity(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT_PHONE_NUMBER: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                intent.putExtra(Insert.PHONE, "5123456789");
                startActivity(intent);
                break;
            }
            case ACTION_INSERT_OR_EDIT_EMAIL_ADDRESS: {
                Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
                intent.setType(Contacts.CONTENT_ITEM_TYPE);
                intent.putExtra(Insert.EMAIL, "android@android.com");
                startActivity(intent);
                break;
            }
            case ACTION_INSERT_GROUP: {
                final Intent intent = new Intent(Intent.ACTION_INSERT);
                intent.setType(Groups.CONTENT_TYPE);
                startActivity(intent);
                break;
            }
            case ACTION_SEARCH_CALL: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.ACTION_MSG, "call");
                intent.putExtra(SearchManager.QUERY, "800-4664-411");
                startSearchResultActivity(intent);
                break;
            }
            case ACTION_SEARCH_CONTACT: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.QUERY, "a");
                intent.setType(Contacts.CONTENT_TYPE);
                startSearchResultActivity(intent);
                break;
            }
            case ACTION_SEARCH_EMAIL: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(Insert.EMAIL, "a");
                startSearchResultActivity(intent);
                break;
            }
            case ACTION_SEARCH_PHONE: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(Insert.PHONE, "800");
                startSearchResultActivity(intent);
                break;
            }
            case ACTION_SEARCH_GENERAL: {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.QUERY, "a");
                startSearchResultActivity(intent);
                break;
            }
            case SEARCH_SUGGESTION_CLICKED_CONTACT: {
                long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    Intent intent = new Intent(Intents.SEARCH_SUGGESTION_CLICKED);
                    intent.setData(contactUri);
                    startContactListActivity(intent);
                }
                break;
            }
            case EDIT_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    final Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                    startActivity(intent);
                }
                break;
            }
            case EDIT_CONTACT_LOOKUP: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                    final String lookupKey = lookupUri.getPathSegments().get(2);
                    final Uri lookupWithoutIdUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                            lookupKey);
                    final Intent intent = new Intent(Intent.ACTION_EDIT, lookupWithoutIdUri);
                    startActivity(intent);
                }
                break;
            }
            case EDIT_CONTACT_LOOKUP_ID: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                    final Intent intent = new Intent(Intent.ACTION_EDIT, lookupUri);
                    startActivity(intent);
                }
                break;
            }
            case EDIT_RAW_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final long rawContactId = findArbitraryRawContactOfContact(contactId);
                    if (rawContactId != -1) {
                        final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                                rawContactId);
                        final Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                        startActivity(intent);
                    }
                }
                break;
            }
            case EDIT_LEGACY: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final long rawContactId = findArbitraryRawContactOfContact(contactId);
                    if (rawContactId != -1) {
                        final Uri legacyContentUri = Uri.parse("content://contacts/people");
                        final Uri uri = ContentUris.withAppendedId(legacyContentUri, rawContactId);
                        final Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                        startActivity(intent);
                    }
                }
                break;
            }
            case EDIT_NEW_CONTACT: {
                startActivity(new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI));
                break;
            }
            case EDIT_NEW_CONTACT_WITH_DATA: {
                Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                putDataExtra(intent);
                startActivity(intent);
                break;
            }
            case EDIT_NEW_CONTACT_FOR_ACCOUNT:
            case EDIT_NEW_CONTACT_FOR_ACCOUNT_WITH_DATA: {
                final SelectAccountDialogFragment dialog = new SelectAccountDialogFragment();
                dialog.setArguments(SelectAccountDialogFragment.createBundle(position));
                dialog.show(getFragmentManager(), SelectAccountDialogFragment.TAG);
                break;
            }
            case EDIT_NEW_RAW_CONTACT: {
                startActivity(new Intent(Intent.ACTION_INSERT, RawContacts.CONTENT_URI));
                break;
            }
            case EDIT_NEW_LEGACY: {
                final Uri legacyContentUri = Uri.parse("content://contacts/people");
                startActivity(new Intent(Intent.ACTION_INSERT, legacyContentUri));
                break;
            }
            case EDIT_GROUP: {
                final Intent intent = findArbitraryGroupIntent(Intent.ACTION_EDIT);
                if (intent != null) {
                    startActivity(intent);
                }
                break;
            }
            case VIEW_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    startActivity(intent);
                }
                break;
            }
            case VIEW_CONTACT_WITHOUT_ID: {
                startActivity(new Intent(Intent.ACTION_VIEW, Contacts.CONTENT_URI));
                break;
            }
            case VIEW_PERSON_WITHOUT_ID: {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setType("vnd.android.cursor.dir/person");
                startActivity(intent);
                break;
            }
            case VIEW_CONTACT_LOOKUP: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                    final String lookupKey = lookupUri.getPathSegments().get(2);
                    final Uri lookupWithoutIdUri = Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                            lookupKey);
                    final Intent intent = new Intent(Intent.ACTION_VIEW, lookupWithoutIdUri);
                    startActivity(intent);
                }
                break;
            }
            case VIEW_CONTACT_LOOKUP_ID: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final Uri uri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                    final Uri lookupUri = Contacts.getLookupUri(getContentResolver(), uri);
                    final Intent intent = new Intent(Intent.ACTION_VIEW, lookupUri);
                    startActivity(intent);
                }
                break;
            }
            case VIEW_RAW_CONTACT: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final long rawContactId = findArbitraryRawContactOfContact(contactId);
                    if (rawContactId != -1) {
                        final Uri uri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                                rawContactId);
                        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    }
                }
                break;
            }
            case VIEW_LEGACY: {
                final long contactId = findArbitraryContactWithPhoneNumber();
                if (contactId != -1) {
                    final long rawContactId = findArbitraryRawContactOfContact(contactId);
                    if (rawContactId != -1) {
                        final Uri legacyContentUri = Uri.parse("content://contacts/people");
                        final Uri uri = ContentUris.withAppendedId(legacyContentUri, rawContactId);
                        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    }
                }
                break;
            }
            case VIEW_GROUP: {
                final Intent intent = findArbitraryGroupIntent(Intent.ACTION_VIEW);
                if (intent != null) {
                    startActivity(intent);
                }
                break;
            }
            case QUICK_CONTACT_TESTS_ACTIVITY: {
                startActivity(new Intent(this, QuickContactTestsActivity.class));
                break;
            }
            case LIST_DEFAULT: {
                startActivity(new Intent(UiIntentActions.LIST_DEFAULT));
                break;
            }
            case LIST_CONTACTS: {
                startActivity(new Intent(UiIntentActions.LIST_CONTACTS));
                break;
            }
            case LIST_ALL_CONTACTS: {
                startActivity(new Intent(UiIntentActions.LIST_ALL_CONTACTS_ACTION));
                break;
            }
            case LIST_CONTACTS_WITH_PHONES: {
                startActivity(new Intent(UiIntentActions.LIST_CONTACTS_WITH_PHONES_ACTION));
                break;
            }
            case LIST_STARRED: {
                startActivity(new Intent(UiIntentActions.LIST_STARRED_ACTION));
                break;
            }
            case LIST_FREQUENT: {
                startActivity(new Intent(UiIntentActions.LIST_FREQUENT_ACTION));
                break;
            }
            case LIST_STREQUENT: {
                startActivity(new Intent(UiIntentActions.LIST_STREQUENT_ACTION));
                break;
            }

            default: {
                Toast.makeText(this, "Sorry, we forgot to write this...", Toast.LENGTH_LONG).show();
            }
        }
    }

    /** Creates an intent that is bound to a specific activity by name. */
    private Intent bindIntentToClass(Intent intent, String activityClassName) {
        intent.setComponent(new ComponentName(mContactsPackageName,
                    activityClassName));
        return intent;
    }

    private void startContactListActivity(Intent intent) {
        bindIntentToClass(intent, CONTACT_LIST_ACTIVITY_CLASS_NAME);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void startContactSelectionActivityForResult(Intent intent) {
        startActivityForResult(intent, 12);
    }

    private void startSearchResultActivity(Intent intent) {
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra("resultCode", resultCode);
        intent.putExtra("data", data);
        startActivity(intent);
    }

    private long findArbitraryContactWithPhoneNumber() {
        final Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI,
                new String[] { Contacts._ID },
                Contacts.HAS_PHONE_NUMBER + "!=0",
                null, "RANDOM() LIMIT 1");
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        Toast.makeText(this, "Failed to find a contact with a phone number. Aborting.",
                Toast.LENGTH_SHORT).show();
        return -1;
    }

    private long findArbitraryRawContactOfContact(long contactId) {
        final Cursor cursor = getContentResolver().query(RawContacts.CONTENT_URI,
                new String[] { RawContacts._ID },
                RawContacts.CONTACT_ID + "=?",
                new String[] { String.valueOf(contactId) },
                RawContacts._ID + " LIMIT 1");
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        Toast.makeText(this, "Failed to find a raw contact of contact with ID " + contactId +
                ". Aborting", Toast.LENGTH_SHORT).show();
        return -1;
    }

    private Intent findArbitraryGroupIntent(String action) {
        final long groupId = findArbitraryGroup();
        if (groupId == -1) return  null;
        final Intent intent = new Intent(action) ;
        intent.setData(ContentUris.withAppendedId(Groups.CONTENT_URI, groupId));
        // TODO: ContactsProvider2#getType does handle the group mimetype
        intent.setClassName("com.google.android.contacts",
                "com.android.contacts.activities.PeopleActivity");
        return intent;
    }

    private long findArbitraryGroup() {
        final Cursor cursor = getContentResolver().query(Groups.CONTENT_URI,
                new String[] { Groups._ID },
                GroupUtil.DEFAULT_SELECTION,
                null,
                "RANDOM() LIMIT 1");
        try {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        Toast.makeText(this, "Failed to find any group. Aborting.", Toast.LENGTH_SHORT).show();
        return -1;
    }

    @Override
    public void onAccountChosen(Account account, String dataSet, int tag) {
        switch (ContactsIntent.get(tag)) {
            case EDIT_NEW_CONTACT_FOR_ACCOUNT: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                intent.putExtra(Insert.EXTRA_ACCOUNT, account);
                intent.putExtra(Insert.EXTRA_DATA_SET, dataSet);
                startActivity(intent);
                break;
            }
            case EDIT_NEW_CONTACT_FOR_ACCOUNT_WITH_DATA: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);

                intent.putExtra(Insert.EXTRA_ACCOUNT, account);
                intent.putExtra(Insert.EXTRA_DATA_SET, dataSet);
                putDataExtra(intent);

                startActivity(intent);
                break;
            }
            default:
                break;
        }
    }

    public void putDataExtra(final Intent intent) {
        ContentValues row1 = new ContentValues();
        row1.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
        row1.put(Organization.COMPANY, "Android");

        ContentValues row2 = new ContentValues();
        row2.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        row2.put(Email.TYPE, Email.TYPE_CUSTOM);
        row2.put(Email.LABEL, "Green Bot");
        row2.put(Email.ADDRESS, "android@android.com");

        final ArrayList<ContentValues> rows = new ArrayList<>();
        rows.add(row1);
        rows.add(row2);

        intent.putParcelableArrayListExtra(Insert.DATA, rows);
    }
}
