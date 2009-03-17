/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IContentProvider;
import android.content.ISyncAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Contacts;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.ContactMethodsColumns;
import android.provider.Contacts.Groups;
import android.provider.Contacts.Intents;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Presence;
import android.provider.Contacts.Intents.UI;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Displays a list of contacts. Usually is embedded into the ContactsActivity.
 */
public final class ContactsListActivity extends ListActivity
        implements View.OnCreateContextMenuListener, DialogInterface.OnClickListener {
    private static final String TAG = "ContactsListActivity";

    private static final String LIST_STATE_KEY = "liststate";
    private static final String FOCUS_KEY = "focused";
    
    static final int MENU_ITEM_VIEW_CONTACT = 1;
    static final int MENU_ITEM_CALL = 2;
    static final int MENU_ITEM_EDIT_BEFORE_CALL = 3;
    static final int MENU_ITEM_SEND_SMS = 4;
    static final int MENU_ITEM_SEND_IM = 5;
    static final int MENU_ITEM_EDIT = 6;
    static final int MENU_ITEM_DELETE = 7;
    static final int MENU_ITEM_TOGGLE_STAR = 8;

    public static final int MENU_SEARCH = 1;
    public static final int MENU_DIALER = 9;
    public static final int MENU_NEW_CONTACT = 10;
    public static final int MENU_DISPLAY_GROUP = 11;

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    
    /** Mask for picker mode */
    static final int MODE_MASK_PICKER = 0x80000000;
    /** Mask for no presence mode */
    static final int MODE_MASK_NO_PRESENCE = 0x40000000;
    /** Mask for enabling list filtering */
    static final int MODE_MASK_NO_FILTER = 0x20000000;
    /** Mask for having a "create new contact" header in the list */
    static final int MODE_MASK_CREATE_NEW = 0x10000000;
    /** Mask for showing photos in the list */
    static final int MODE_MASK_SHOW_PHOTOS = 0x08000000;

    /** Unknown mode */
    static final int MODE_UNKNOWN = 0;
    /** Show members of the "Contacts" group */
    static final int MODE_GROUP = 5;
    /** Show all contacts sorted alphabetically */
    static final int MODE_ALL_CONTACTS = 10;
    /** Show all contacts with phone numbers, sorted alphabetically */
    static final int MODE_WITH_PHONES = 15;
    /** Show all starred contacts */
    static final int MODE_STARRED = 20;
    /** Show frequently contacted contacts */
    static final int MODE_FREQUENT = 30;
    /** Show starred and the frequent */
    static final int MODE_STREQUENT = 35 | MODE_MASK_SHOW_PHOTOS;
    /** Show all contacts and pick them when clicking */
    static final int MODE_PICK_CONTACT = 40 | MODE_MASK_PICKER;
    /** Show all contacts as well as the option to create a new one */
    static final int MODE_PICK_OR_CREATE_CONTACT = 42 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW;
    /** Show all contacts and pick them when clicking, and allow creating a new contact */
    static final int MODE_INSERT_OR_EDIT_CONTACT = 45 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW;
    /** Show all phone numbers and pick them when clicking */
    static final int MODE_PICK_PHONE = 50 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE;
    /** Show all postal addresses and pick them when clicking */
    static final int MODE_PICK_POSTAL =
            55 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Run a search query */
    static final int MODE_QUERY = 60 | MODE_MASK_NO_FILTER;
    /** Show all contacts matching query, or handle special cases where 0 or 1 contacts found. */
    static final int MODE_SHOW_OR_CREATE_CONTACT = 65 | MODE_MASK_PICKER | MODE_MASK_NO_FILTER;

    static final int DEFAULT_MODE = MODE_ALL_CONTACTS;

    /**
     * The type of data to display in the main contacts list. 
     */
    static final String PREF_DISPLAY_TYPE = "display_system_group";

    /** Unknown display type. */
    static final int DISPLAY_TYPE_UNKNOWN = -1;
    /** Display all contacts */
    static final int DISPLAY_TYPE_ALL = 0;
    /** Display all contacts that have phone numbers */
    static final int DISPLAY_TYPE_ALL_WITH_PHONES = 1;
    /** Display a system group */
    static final int DISPLAY_TYPE_SYSTEM_GROUP = 2;
    /** Display a user group */
    static final int DISPLAY_TYPE_USER_GROUP = 3;

    /**
     * Info about what to display. If {@link #PREF_DISPLAY_TYPE}
     * is {@link #DISPLAY_TYPE_SYSTEM_GROUP} then this will be the system id.
     * If {@link #PREF_DISPLAY_TYPE} is {@link #DISPLAY_TYPE_USER_GROUP} then this will
     * be the group name.
     */ 
    static final String PREF_DISPLAY_INFO = "display_group";

    
    static final String NAME_COLUMN = People.DISPLAY_NAME;
    static final String SORT_STRING = People.SORT_STRING;
    
    static final String[] CONTACTS_PROJECTION = new String[] {
        People._ID, // 0
        NAME_COLUMN, // 1
        People.NUMBER, // 2
        People.TYPE, // 3
        People.LABEL, // 4
        People.STARRED, // 5
        People.PRIMARY_PHONE_ID, // 6
        People.PRIMARY_EMAIL_ID, // 7
        People.PRESENCE_STATUS, // 8
        SORT_STRING, // 9
    };

    static final String[] STREQUENT_PROJECTION = new String[] {
        People._ID, // 0
        NAME_COLUMN, // 1
        People.NUMBER, // 2
        People.TYPE, // 3
        People.LABEL, // 4
        People.STARRED, // 5
        People.PRIMARY_PHONE_ID, // 6
        People.PRIMARY_EMAIL_ID, // 7
        People.PRESENCE_STATUS, // 8
        "photo_data", // 9
        People.TIMES_CONTACTED, // 10 (not displayed, but required for the order by to work)
    };

    static final String[] PHONES_PROJECTION = new String[] {
        Phones._ID, // 0
        NAME_COLUMN, // 1
        Phones.NUMBER, // 2
        Phones.TYPE, // 3
        Phones.LABEL, // 4
        Phones.STARRED, // 5
        Phones.PERSON_ID, // 6
    };

    static final String[] CONTACT_METHODS_PROJECTION = new String[] {
        ContactMethods._ID, // 0
        NAME_COLUMN, // 1
        ContactMethods.DATA, // 2
        ContactMethods.TYPE, // 3
        ContactMethods.LABEL, // 4
        ContactMethods.STARRED, // 5
        ContactMethods.PERSON_ID, // 6
    };

    static final int ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int NUMBER_COLUMN_INDEX = 2;
    static final int DATA_COLUMN_INDEX = 2;
    static final int TYPE_COLUMN_INDEX = 3;
    static final int LABEL_COLUMN_INDEX = 4;
    static final int STARRED_COLUMN_INDEX = 5;
    static final int PRIMARY_PHONE_ID_COLUMN_INDEX = 6;
    static final int PRIMARY_EMAIL_ID_COLUMN_INDEX = 7;
    static final int SERVER_STATUS_COLUMN_INDEX = 8;
    static final int PHOTO_COLUMN_INDEX = 9;
    static final int SORT_STRING_INDEX = 9;

    static final int PHONES_PERSON_ID_INDEX = 6;
    static final int CONTACT_METHODS_PERSON_ID_INDEX = 6;
    
    static final int DISPLAY_GROUP_INDEX_ALL_CONTACTS = 0;
    static final int DISPLAY_GROUP_INDEX_ALL_CONTACTS_WITH_PHONES = 1;
    static final int DISPLAY_GROUP_INDEX_MY_CONTACTS = 2;

    private static final int QUERY_TOKEN = 42;

    private static final String[] GROUPS_PROJECTION = new String[] {
        Groups.SYSTEM_ID, // 0
        Groups.NAME, // 1
    };
    private static final int GROUPS_COLUMN_INDEX_SYSTEM_ID = 0;
    private static final int GROUPS_COLUMN_INDEX_NAME = 1;
    
    static final String GROUP_WITH_PHONES = "android_smartgroup_phone";

    ContactItemListAdapter mAdapter;

    int mMode = DEFAULT_MODE;
    // The current display group
    private String mDisplayInfo;
    private int mDisplayType;
    // The current list of display groups, during selection from menu
    private CharSequence[] mDisplayGroups;
    // If true position 2 in mDisplayGroups is the MyContacts group
    private boolean mDisplayGroupsIncludesMyContacts = false;

    private int mDisplayGroupOriginalSelection;
    private int mDisplayGroupCurrentSelection;
    
    private QueryHandler mQueryHandler;
    private String mQuery;
    private Uri mGroupFilterUri;
    private Uri mGroupUri;
    private boolean mJustCreated;
    private boolean mSyncEnabled;
    
    /**
     * {@link Bundle} of extras to include when launching
     * {@link Intents.Insert#ACTION}, usually when responding to
     * {@link Intents#SHOW_OR_CREATE_CONTACT}.
     */
    private Bundle mCreateExtras;
    
    /**
     * Title to display to user when asking if a contact should be created,
     * usually the requested E-mail address or phone number when responding to a
     * {@link Intents#SHOW_OR_CREATE_CONTACT}.
     */
    private String mCreateData;

    /**
     * Cursor row index that holds reference back to {@link People#_ID}, such as
     * {@link ContactMethods#PERSON_ID}. Used when responding to a
     * {@link Intents#SHOW_OR_CREATE_CONTACT}.
     */
    private int mCreatePersonIndex;

    /**
     * Used to keep track of the scroll state of the list.
     */
    private Parcelable mListState = null;
    private boolean mListHasFocus;

    private boolean mCreateShortcut;
    private boolean mDefaultMode = false;

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        private Uri mUri;

        public DeleteClickListener(Uri uri) {
            mUri = uri;
        }

        public void onClick(DialogInterface dialog, int which) {
            getContentResolver().delete(mUri, null, null);
        }
    }
    
    private class IntentClickListener implements DialogInterface.OnClickListener {
        private Activity mParent;
        private Intent mIntent;

        public IntentClickListener(Activity parent, Intent intent) {
            mParent = parent;
            mIntent = intent;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (mIntent != null) {
                mParent.startActivity(mIntent);
            }
            mParent.finish();
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Resolve the intent
        final Intent intent = getIntent();

        // Allow the title to be set to a custom String using an extra on the intent
        String title = intent.getStringExtra(Contacts.Intents.UI.TITLE_EXTRA_KEY);
        if (title != null) {
            setTitle(title);
        }
        
        final String action = intent.getAction();
        mMode = MODE_UNKNOWN;
        
        setContentView(R.layout.contacts_list_content);

        if (UI.LIST_DEFAULT.equals(action)) {
            mDefaultMode = true;
            // When mDefaultMode is true the mode is set in onResume(), since the preferneces
            // activity may change it whenever this activity isn't running
        } else if (UI.LIST_GROUP_ACTION.equals(action)) {
            mMode = MODE_GROUP;
            String groupName = intent.getStringExtra(UI.GROUP_NAME_EXTRA_KEY);
            if (TextUtils.isEmpty(groupName)) {
                finish();
                return;
            }
            buildUserGroupUris(groupName);
        } else if (UI.LIST_ALL_CONTACTS_ACTION.equals(action)) {
            mMode = MODE_ALL_CONTACTS;
        } else if (UI.LIST_STARRED_ACTION.equals(action)) {
            mMode = MODE_STARRED;
        } else if (UI.LIST_FREQUENT_ACTION.equals(action)) {
            mMode = MODE_FREQUENT;
        } else if (UI.LIST_STREQUENT_ACTION.equals(action)) {
            mMode = MODE_STREQUENT;
        } else if (UI.LIST_CONTACTS_WITH_PHONES_ACTION.equals(action)) {
            mMode = MODE_WITH_PHONES;
        } else if (Intent.ACTION_PICK.equals(action)) {
            // XXX These should be showing the data from the URI given in
            // the Intent.
            final String type = intent.resolveType(this);
            if (People.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_CONTACT;
            } else if (Phones.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_PHONE;
            } else if (ContactMethods.CONTENT_POSTAL_TYPE.equals(type)) {
                mMode = MODE_PICK_POSTAL;
            }
        } else if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            mMode = MODE_PICK_OR_CREATE_CONTACT;
            mCreateShortcut = true;
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            final String type = intent.resolveType(this);
            if (People.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_OR_CREATE_CONTACT;
            } else if (Phones.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_PHONE;
            } else if (ContactMethods.CONTENT_POSTAL_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_POSTAL;
            }
        } else if (Intent.ACTION_INSERT_OR_EDIT.equals(action)) {
            mMode = MODE_INSERT_OR_EDIT_CONTACT;
        } else if (Intent.ACTION_SEARCH.equals(action)) {
            // See if the suggestion was clicked with a search action key (call button)
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG))) {
                String query = intent.getStringExtra(SearchManager.QUERY);
                if (!TextUtils.isEmpty(query)) {
                    Intent newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            Uri.fromParts("tel", query, null));
                    startActivity(newIntent);
                }
                finish();
                return;
            }
            // Otherwise handle the more normal search case
            mMode = MODE_QUERY;

        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        } else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            // See if the suggestion was clicked with a search action key (call button)
            Intent newIntent;
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG))) {
                newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData());
            } else {
                newIntent = new Intent(Intent.ACTION_VIEW, intent.getData());
            }
            startActivity(newIntent);
            finish();
            return;
        } else if (Intents.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED.equals(action)) {
            Intent newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, intent.getData());
            startActivity(newIntent);
            finish();
            return;
        } else if (Intents.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED.equals(action)) {
            String number = intent.getData().getSchemeSpecificPart();
            Intent newIntent = new Intent(Intent.ACTION_INSERT, People.CONTENT_URI);
            newIntent.putExtra(Intents.Insert.PHONE, number);
            startActivity(newIntent);
            finish();
            return;
        } else if (Intents.SHOW_OR_CREATE_CONTACT.equals(action)) {
            mMode = MODE_SHOW_OR_CREATE_CONTACT;
        }

        if (mMode == MODE_UNKNOWN) {
            mMode = DEFAULT_MODE;
        }

        // Setup the UI
        final ListView list = getListView();
        list.setFocusable(true);
        list.setOnCreateContextMenuListener(this);
        if ((mMode & MODE_MASK_NO_FILTER) != MODE_MASK_NO_FILTER) {
            list.setTextFilterEnabled(true);
        }

        if ((mMode & MODE_MASK_CREATE_NEW) != 0) {
            // Add the header for creating a new contact
            final LayoutInflater inflater = getLayoutInflater();
            View header = inflater.inflate(android.R.layout.simple_list_item_1, list, false);
            TextView text = (TextView) header.findViewById(android.R.id.text1);
            text.setText(R.string.pickerNewContactHeader);
            list.addHeaderView(header);
        }

        // Set the proper empty string
        setEmptyText();
        
        mAdapter = new ContactItemListAdapter(this);
        setListAdapter(mAdapter);

        // We manually save/restore the listview state
        list.setSaveEnabled(false);

        mQueryHandler = new QueryHandler(this);
        mJustCreated = true;

        // Check to see if sync is enabled
        final ContentResolver resolver = getContentResolver();
        IContentProvider provider = resolver.acquireProvider(Contacts.CONTENT_URI);
        if (provider == null) {
            // No contacts provider, bail.
            finish();
            return;
        }

        try {
            ISyncAdapter sa = provider.getSyncAdapter();
            mSyncEnabled = sa != null;
        } catch (RemoteException e) {
            mSyncEnabled = false;
        } finally {
            resolver.releaseProvider(provider);
        }
    }

    /**
     * Handles special cases of the {@link Intents#SHOW_OR_CREATE_CONTACT}
     * intent. When zero results found, prompt user to create new contact. When
     * one contact found, shortcut to viewing that contact.
     * 
     * @param c Valid cursor after performing query
     */
    private void handleShowOrCreate(final Cursor c) {
        int count = 0;
        long personId = 0;
        if (c != null) {
            count = c.getCount();
            if (count == 1 && c.moveToFirst()) {
                // Try reading ID for the only contact returned
                personId = c.getLong(mCreatePersonIndex);
            }
        }
        
        if (count == 1) {
            // If we only found one item, jump right to viewing it
            Intent showIntent = new Intent(Intent.ACTION_VIEW,
                    ContentUris.withAppendedId(People.CONTENT_URI, personId));
            startActivity(showIntent);
            finish();
            
        } else if (count == 0) {
            // Insert create values coming from our request
            Intent createIntent = new Intent(Intent.ACTION_INSERT, People.CONTENT_URI);
            createIntent.putExtras(mCreateExtras);
            
            // Prompt user if they really want to start creating a new
            // contact. Skip this step if the caller requested {@link
            // Contacts#EXTRA_FORCE_CREATE}.
            boolean forceCreate = getIntent().getBooleanExtra(Intents.EXTRA_FORCE_CREATE, false);
            
            if (forceCreate) {
                startActivity(createIntent);
                finish();
            } else {
                CharSequence message = getResources().getString(
                        R.string.add_contact_dlg_message_fmt, mCreateData);
                
                new AlertDialog.Builder(this)
                        .setTitle(R.string.add_contact_dlg_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok,
                                new IntentClickListener(this, createIntent))
                        .setNegativeButton(android.R.string.cancel,
                                new IntentClickListener(this, null))
                        .show();
            }
        }
    }

    private void setEmptyText() {
        TextView empty = (TextView) findViewById(R.id.emptyText);
        // Center the text by default
        int gravity = Gravity.CENTER;
        switch (mMode) {
            case MODE_GROUP:
                if (Groups.GROUP_MY_CONTACTS.equals(mDisplayInfo)) {
                    if (mSyncEnabled) {
                        empty.setText(getText(R.string.noContactsHelpTextWithSync));
                    } else {
                        empty.setText(getText(R.string.noContactsHelpText));
                    }
                    gravity = Gravity.NO_GRAVITY;
                } else {
                    empty.setText(getString(R.string.groupEmpty, mDisplayInfo));
                }
                break;

            case MODE_STARRED:
            case MODE_STREQUENT:
            case MODE_FREQUENT:
                empty.setText(getText(R.string.noFavorites));
                break;

            case MODE_WITH_PHONES:
                empty.setText(getText(R.string.noContactsWithPhoneNumbers));
                break;

            default:
                empty.setText(getText(R.string.noContacts));
                break;
        }
        empty.setGravity(gravity);
    }

    /**
     * Builds the URIs to query when displaying a user group
     * 
     * @param groupName the group being displayed
     */
    private void buildUserGroupUris(String groupName) {
        mGroupFilterUri = Uri.parse("content://contacts/groups/name/" + groupName
                + "/members/filter/");
        mGroupUri = Uri.parse("content://contacts/groups/name/" + groupName + "/members");
    }

    /**
     * Builds the URIs to query when displaying a system group
     * 
     * @param systemId the system group's ID 
     */
    private void buildSystemGroupUris(String systemId) {
        mGroupFilterUri = Uri.parse("content://contacts/groups/system_id/" + systemId
                + "/members/filter/");
        mGroupUri = Uri.parse("content://contacts/groups/system_id/" + systemId + "/members");
    }

    /**
     * Sets the mode when the request is for "default"
     */
    private void setDefaultMode() {
        // Load the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        
        // Lookup the group to display
        mDisplayType = prefs.getInt(PREF_DISPLAY_TYPE, DISPLAY_TYPE_UNKNOWN);
        switch (mDisplayType) {
            case DISPLAY_TYPE_ALL_WITH_PHONES: {
                mMode = MODE_WITH_PHONES;
                mDisplayInfo = null;
                break;
            }

            case DISPLAY_TYPE_SYSTEM_GROUP: {
                String systemId = prefs.getString(
                        PREF_DISPLAY_INFO, null);
                if (!TextUtils.isEmpty(systemId)) {
                    // Display the selected system group
                    mMode = MODE_GROUP;
                    buildSystemGroupUris(systemId);
                    mDisplayInfo = systemId;
                } else {
                    // No valid group is present, display everything
                    mMode = MODE_WITH_PHONES;
                    mDisplayInfo = null;
                    mDisplayType = DISPLAY_TYPE_ALL;
                }
                break;
            }

            case DISPLAY_TYPE_USER_GROUP: {
                String displayGroup = prefs.getString(
                        PREF_DISPLAY_INFO, null);
                if (!TextUtils.isEmpty(displayGroup)) {
                    // Display the selected user group
                    mMode = MODE_GROUP;
                    buildUserGroupUris(displayGroup);
                    mDisplayInfo = displayGroup;
                } else {
                    // No valid group is present, display everything
                    mMode = MODE_WITH_PHONES;
                    mDisplayInfo = null;
                    mDisplayType = DISPLAY_TYPE_ALL;
                }
                break;
            }

            case DISPLAY_TYPE_ALL: {
                mMode = MODE_ALL_CONTACTS;
                mDisplayInfo = null;
                break;
            }

            default: {
                // We don't know what to display, default to My Contacts
                mMode = MODE_GROUP;
                mDisplayType = DISPLAY_TYPE_SYSTEM_GROUP;
                buildSystemGroupUris(Groups.GROUP_MY_CONTACTS);
                mDisplayInfo = Groups.GROUP_MY_CONTACTS;
                break;
            }
        }

        // Update the empty text view with the proper string, as the group may have changed
        setEmptyText();
    }

    @Override
    protected void onResume() {
        super.onResume();

        boolean runQuery = true;
        Activity parent = getParent();
        
        // Do this before setting the filter. The filter thread relies
        // on some state that is initialized in setDefaultMode
        if (mDefaultMode) {
            // If we're in default mode we need to possibly reset the mode due to a change
            // in the preferences activity while we weren't running
            setDefaultMode();
        }
        
        // See if we were invoked with a filter
        if (parent != null && parent instanceof DialtactsActivity) {
            String filterText = ((DialtactsActivity) parent).getAndClearFilterText();
            if (filterText != null && filterText.length() > 0) {
                getListView().setFilterText(filterText);
                // Don't start a new query since it will conflict with the filter
                runQuery = false;
            } else if (mJustCreated) {
                getListView().clearTextFilter();
            }
        }

        if (mJustCreated && runQuery) {
            // We need to start a query here the first time the activity is launched, as long
            // as we aren't doing a filter.
            startQuery();
        }
        mJustCreated = false;
    }
    
    @Override
    protected void onRestart() {
        super.onRestart();

        // The cursor was killed off in onStop(), so we need to get a new one here
        startQuery();
    }
    
    private void updateGroup() {
        if (mDefaultMode) {
            setDefaultMode();
        }

        // Calling requery here may cause an ANR, so always do the async query
        startQuery();
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // Save list state in the bundle so we can restore it after the QueryHandler has run
        icicle.putParcelable(LIST_STATE_KEY, mList.onSaveInstanceState());
        icicle.putBoolean(FOCUS_KEY, mList.hasFocus());
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        super.onRestoreInstanceState(icicle);
        // Retrieve list state. This will be applied after the QueryHandler has run
        mListState = icicle.getParcelable(LIST_STATE_KEY);
        mListHasFocus = icicle.getBoolean(FOCUS_KEY);
    }

    @Override
    protected void onStop() {
        super.onStop();

        // We don't want the list to display the empty state, since when we resume it will still
        // be there and show up while the new query is happening. After the async query finished
        // in response to onRestart() setLoading(false) will be called.
        mAdapter.setLoading(true);
        mAdapter.changeCursor(null);

        if (mMode == MODE_QUERY) {
            // Make sure the search box is closed
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            searchManager.stopSearch();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the options menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return false;
        }

        // Search
        menu.add(0, MENU_SEARCH, 0, R.string.menu_search)
                .setIcon(android.R.drawable.ic_menu_search);

        // New contact
        menu.add(0, MENU_NEW_CONTACT, 0, R.string.menu_newContact)
                .setIcon(android.R.drawable.ic_menu_add)
                .setIntent(new Intent(Intents.Insert.ACTION, People.CONTENT_URI))
                .setAlphabeticShortcut('n');

        // Display group
        if (mDefaultMode) {
            menu.add(0, MENU_DISPLAY_GROUP, 0, R.string.menu_displayGroup)
                    .setIcon(com.android.internal.R.drawable.ic_menu_allfriends);
        }

        // Sync settings
        if (mSyncEnabled) {
            Intent syncIntent = new Intent(Intent.ACTION_VIEW);
            syncIntent.setClass(this, ContactsGroupSyncSelector.class);
            menu.add(0, 0, 0, R.string.syncGroupPreference)
                    .setIcon(com.android.internal.R.drawable.ic_menu_refresh)
                    .setIntent(syncIntent);
        }
        
        // SIM import
        Intent importIntent = new Intent(Intent.ACTION_VIEW);
        importIntent.setType("vnd.android.cursor.item/sim-contact");
        importIntent.setClassName("com.android.phone", "com.android.phone.SimContacts");
        menu.add(0, 0, 0, R.string.importFromSim)
                .setIcon(R.drawable.ic_menu_import_contact)
                .setIntent(importIntent);

        return super.onCreateOptionsMenu(menu);
    }

    /*
     * Implements the handler for display group selection.
     */
    public void onClick(DialogInterface dialogInterface, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            // The OK button was pressed
            if (mDisplayGroupOriginalSelection != mDisplayGroupCurrentSelection) {
                // Set the group to display
                if (mDisplayGroupCurrentSelection == DISPLAY_GROUP_INDEX_ALL_CONTACTS) {
                    // Display all
                    mDisplayType = DISPLAY_TYPE_ALL;
                    mDisplayInfo = null;
                } else if (mDisplayGroupCurrentSelection
                        == DISPLAY_GROUP_INDEX_ALL_CONTACTS_WITH_PHONES) {
                    // Display all with phone numbers
                    mDisplayType = DISPLAY_TYPE_ALL_WITH_PHONES;
                    mDisplayInfo = null;
                } else if (mDisplayGroupsIncludesMyContacts &&
                        mDisplayGroupCurrentSelection == DISPLAY_GROUP_INDEX_MY_CONTACTS) {
                    mDisplayType = DISPLAY_TYPE_SYSTEM_GROUP;
                    mDisplayInfo = Groups.GROUP_MY_CONTACTS;
                } else {
                    mDisplayType = DISPLAY_TYPE_USER_GROUP;
                    mDisplayInfo = mDisplayGroups[mDisplayGroupCurrentSelection].toString();
                }

                // Save the changes to the preferences
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit()
                        .putInt(PREF_DISPLAY_TYPE, mDisplayType)
                        .putString(PREF_DISPLAY_INFO, mDisplayInfo)
                        .commit();

                // Update the display state
                updateGroup();
            }
        } else {
            // A list item was selected, cache the position
            mDisplayGroupCurrentSelection = which;
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_DISPLAY_GROUP:
                AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.select_group_title)
                    .setPositiveButton(android.R.string.ok, this)
                    .setNegativeButton(android.R.string.cancel, null);
                
                setGroupEntries(builder);
                
                builder.show();
                return true;

            case MENU_SEARCH:
                startSearch(null, false, null, false);
                return true;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_NEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    // Contact was created, pass it back
                    returnPickerResult(data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME),
                            data.getData());
                }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the context menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return;
        }

        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }
        long id = info.id;
        Uri personUri = ContentUris.withAppendedId(People.CONTENT_URI, id);

        // Setup the menu header
        menu.setHeaderTitle(cursor.getString(NAME_COLUMN_INDEX));

        // View contact details
        menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact)
                .setIntent(new Intent(Intent.ACTION_VIEW, personUri));

        // Calling contact
        long phoneId = cursor.getLong(PRIMARY_PHONE_ID_COLUMN_INDEX);
        if (phoneId > 0) {
            // Get the display label for the number
            CharSequence label = cursor.getString(LABEL_COLUMN_INDEX);
            int type = cursor.getInt(TYPE_COLUMN_INDEX);
            label = Phones.getDisplayLabel(this, type, label);
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                    ContentUris.withAppendedId(Phones.CONTENT_URI, phoneId));
            menu.add(0, MENU_ITEM_CALL, 0, String.format(getString(R.string.menu_callNumber), label))
                    .setIntent(intent);

            // Send SMS item
            menu.add(0, MENU_ITEM_SEND_SMS, 0, R.string.menu_sendSMS)
                    .setIntent(new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts("sms", cursor.getString(NUMBER_COLUMN_INDEX), null)));
        }

        // Star toggling
        int starState = cursor.getInt(STARRED_COLUMN_INDEX);
        if (starState == 0) {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_addStar);
        } else {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_removeStar);
        }

        // Contact editing
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_editContact)
                .setIntent(new Intent(Intent.ACTION_EDIT, personUri));
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);

        switch (item.getItemId()) {
            case MENU_ITEM_TOGGLE_STAR: {
                // Toggle the star
                ContentValues values = new ContentValues(1);
                values.put(People.STARRED, cursor.getInt(STARRED_COLUMN_INDEX) == 0 ? 1 : 0);
                Uri personUri = ContentUris.withAppendedId(People.CONTENT_URI,
                        cursor.getInt(ID_COLUMN_INDEX));
                getContentResolver().update(personUri, values, null, null);
                return true;
            }

            case MENU_ITEM_DELETE: {
                // Get confirmation
                Uri uri = ContentUris.withAppendedId(People.CONTENT_URI,
                        cursor.getLong(ID_COLUMN_INDEX));
                //TODO make this dialog persist across screen rotations
                new AlertDialog.Builder(ContactsListActivity.this)
                    .setTitle(R.string.deleteConfirmation_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.deleteConfirmation)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DeleteClickListener(uri))
                    .show();
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                if (callSelection()) {
                    return true;
                }
                break;
            }

            case KeyEvent.KEYCODE_DEL: {
                Object o = getListView().getSelectedItem();
                if (o != null) {
                    Cursor cursor = (Cursor) o;
                    Uri uri = ContentUris.withAppendedId(People.CONTENT_URI,
                            cursor.getLong(ID_COLUMN_INDEX));
                    //TODO make this dialog persist across screen rotations
                    new AlertDialog.Builder(ContactsListActivity.this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener(uri))
                        .setCancelable(false)
                        .show();
                    return true;
                }
                break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mList.getWindowToken(), 0);

        if (mMode == MODE_INSERT_OR_EDIT_CONTACT) {
            Intent intent;
            if (position == 0) {
                // Insert
                intent = new Intent(Intent.ACTION_INSERT, People.CONTENT_URI);
            } else {
                // Edit
                intent = new Intent(Intent.ACTION_EDIT,
                        ContentUris.withAppendedId(People.CONTENT_URI, id));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            final Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            startActivity(intent);
            finish();
        } else if (id != -1) {
            if ((mMode & MODE_MASK_PICKER) == 0) {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(People.CONTENT_URI, id));
                startActivity(intent);
            } else if (mMode == MODE_SHOW_OR_CREATE_CONTACT) {
                // Figure out person ID because we might be using indirect cursor
                Cursor c = (Cursor) mAdapter.getItem(position);
                long personId = c.getLong(mCreatePersonIndex);
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(People.CONTENT_URI, personId));
                startActivity(intent);
                finish();
            } else if (mMode == MODE_PICK_CONTACT 
                    || mMode == MODE_PICK_OR_CREATE_CONTACT) {
                Uri uri = ContentUris.withAppendedId(People.CONTENT_URI, id);
                if (mCreateShortcut) {
                    // Subtract one if we have Create Contact at the top
                    Cursor c = (Cursor) mAdapter.getItem(position
                            - (mMode == MODE_PICK_OR_CREATE_CONTACT? 1:0));
                    returnPickerResult(c.getString(NAME_COLUMN_INDEX), uri);
                } else {
                    returnPickerResult(null, uri);
                }
            } else if (mMode == MODE_PICK_PHONE) {
                setResult(RESULT_OK, new Intent().setData(
                        ContentUris.withAppendedId(Phones.CONTENT_URI, id)));
                finish();
            } else if (mMode == MODE_PICK_POSTAL) {
                setResult(RESULT_OK, new Intent().setData(
                        ContentUris.withAppendedId(ContactMethods.CONTENT_URI, id)));
                finish();
            }
        } else if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW
                && position == 0) {
            Intent newContact = new Intent(Intents.Insert.ACTION, People.CONTENT_URI);
            startActivityForResult(newContact, SUBACTIVITY_NEW_CONTACT);
        } else {
            signalError();
        }
    }

    private void returnPickerResult(String name, Uri uri) {
        final Intent intent = new Intent();
    
        if (mCreateShortcut) {
            Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, uri);
            shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            final Bitmap icon = People.loadContactPhoto(this, uri, 0, null);
            if (icon != null) {
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
            } else {
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this,
                                R.drawable.ic_launcher_contacts));
            }
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_OK, intent.setData(uri));
        }
        finish();
    }

    String[] getProjection() {
        switch (mMode) {
            case MODE_GROUP:
            case MODE_ALL_CONTACTS:
            case MODE_WITH_PHONES:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_QUERY:
            case MODE_STARRED:
            case MODE_FREQUENT:
            case MODE_INSERT_OR_EDIT_CONTACT:
                return CONTACTS_PROJECTION;

            case MODE_STREQUENT:
                return STREQUENT_PROJECTION;

            case MODE_PICK_PHONE:
                return PHONES_PROJECTION;

            case MODE_PICK_POSTAL:
                return CONTACT_METHODS_PROJECTION;
        }
        return null;
    }

    private Uri getPeopleFilterUri(String filter) {
        if (!TextUtils.isEmpty(filter)) {
            return Uri.withAppendedPath(People.CONTENT_FILTER_URI, Uri.encode(filter));
        } else {
            return People.CONTENT_URI;
        }
    }

    private static String getSortOrder(String[] projectionType) {
        if (Locale.getDefault().equals(Locale.JAPAN) &&
                projectionType == CONTACTS_PROJECTION) {
            return SORT_STRING + " ASC";
        } else {
            return NAME_COLUMN + " COLLATE LOCALIZED ASC";
        }
    }
    
    void startQuery() {
        mAdapter.setLoading(true);
        
        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);

        // Kick off the new query
        switch (mMode) {
            case MODE_GROUP:
                mQueryHandler.startQuery(QUERY_TOKEN, null,
                        mGroupUri, CONTACTS_PROJECTION, null, null,
                        getSortOrder(CONTACTS_PROJECTION));
                break;

            case MODE_ALL_CONTACTS:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, People.CONTENT_URI, CONTACTS_PROJECTION,
                        null, null, getSortOrder(CONTACTS_PROJECTION));
                break;

            case MODE_WITH_PHONES:
                mQueryHandler.startQuery(QUERY_TOKEN, null, People.CONTENT_URI, CONTACTS_PROJECTION,
                        People.PRIMARY_PHONE_ID + " IS NOT NULL", null,
                        getSortOrder(CONTACTS_PROJECTION));
                break;

            case MODE_QUERY: {
                mQuery = getIntent().getStringExtra(SearchManager.QUERY);
                mQueryHandler.startQuery(QUERY_TOKEN, null, getPeopleFilterUri(mQuery),
                        CONTACTS_PROJECTION, null, null,
                        getSortOrder(CONTACTS_PROJECTION));
                break;
            }
            
            case MODE_SHOW_OR_CREATE_CONTACT: {
                // Caller requested a specific E-mail address or phone number,
                // so show only matching entries. Once the query finishes we
                // handle edge cases of 0 or 1 items.
                Uri data = getIntent().getData();
                String scheme = null;
                String ssp = null;
                if (data != null) {
                    scheme = data.getScheme();
                    ssp = data.getSchemeSpecificPart();
                    mCreateData = ssp;
                }
                
                mCreateExtras = new Bundle();
                Bundle originalExtras = getIntent().getExtras();
                if (originalExtras != null) {
                    mCreateExtras.putAll(originalExtras);
                }

                if ("mailto".equals(scheme)) {
                    mCreateExtras.putString(Intents.Insert.EMAIL, ssp);
                    mCreatePersonIndex = CONTACT_METHODS_PERSON_ID_INDEX;
                    
                    mQueryHandler.startQuery(QUERY_TOKEN, null,
                            ContactMethods.CONTENT_URI, CONTACT_METHODS_PROJECTION,
                            ContactMethodsColumns.KIND + "=" + Contacts.KIND_EMAIL + " AND " +
                            ContactMethods.DATA + "=?", new String[] { ssp },
                            getSortOrder(CONTACT_METHODS_PROJECTION));
                    
                } else if ("tel".equals(scheme)) {
                    mCreateExtras.putString(Intents.Insert.PHONE, ssp);
                    mCreatePersonIndex = PHONES_PERSON_ID_INDEX;
                    
                    mQueryHandler.startQuery(QUERY_TOKEN, null,
                            Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, ssp),
                            PHONES_PROJECTION, null, null,
                            getSortOrder(PHONES_PROJECTION));
                    
                } else {
                    Log.w(TAG, "Invalid intent:" + getIntent());
                    finish();
                }
                
                break;
            }

            case MODE_STARRED:
                mQueryHandler.startQuery(QUERY_TOKEN, null, People.CONTENT_URI,
                        CONTACTS_PROJECTION,
                        People.STARRED + "=1", null, getSortOrder(CONTACTS_PROJECTION));
                break;

            case MODE_FREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null,
                        People.CONTENT_URI, CONTACTS_PROJECTION,
                        People.TIMES_CONTACTED + " > 0", null,
                        People.TIMES_CONTACTED + " DESC, " + getSortOrder(CONTACTS_PROJECTION));
                break;

            case MODE_STREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null,
                        Uri.withAppendedPath(People.CONTENT_URI, "strequent"), STREQUENT_PROJECTION,
                        null, null, null);
                break;

            case MODE_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, Phones.CONTENT_URI, PHONES_PROJECTION,
                        null, null, getSortOrder(PHONES_PROJECTION));
                break;

            case MODE_PICK_POSTAL:
                mQueryHandler.startQuery(QUERY_TOKEN, null, ContactMethods.CONTENT_URI,
                        CONTACT_METHODS_PROJECTION,
                        ContactMethods.KIND + "=" + Contacts.KIND_POSTAL, null,
                        getSortOrder(CONTACT_METHODS_PROJECTION));
                break;
        }
    }

    /**
     * Called from a background thread to do the filter and return the resulting cursor.
     * 
     * @param filter the text that was entered to filter on
     * @return a cursor with the results of the filter
     */
    Cursor doFilter(String filter) {
        final ContentResolver resolver = getContentResolver();

        switch (mMode) {
            case MODE_GROUP: {
                Uri uri;
                if (TextUtils.isEmpty(filter)) {
                    uri = mGroupUri;
                } else {
                    uri = Uri.withAppendedPath(mGroupFilterUri, Uri.encode(filter));
                }
                return resolver.query(uri, CONTACTS_PROJECTION, null, null,
                        getSortOrder(CONTACTS_PROJECTION));
            }

            case MODE_ALL_CONTACTS:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT: {
                return resolver.query(getPeopleFilterUri(filter), CONTACTS_PROJECTION, null, null,
                        getSortOrder(CONTACTS_PROJECTION));
            }

            case MODE_WITH_PHONES: {
                return resolver.query(getPeopleFilterUri(filter), CONTACTS_PROJECTION,
                        People.PRIMARY_PHONE_ID + " IS NOT NULL", null,
                        getSortOrder(CONTACTS_PROJECTION));
            }

            case MODE_STARRED: {
                return resolver.query(getPeopleFilterUri(filter), CONTACTS_PROJECTION,
                        People.STARRED + "=1", null, getSortOrder(CONTACTS_PROJECTION));
            }

            case MODE_FREQUENT: {
                return resolver.query(getPeopleFilterUri(filter), CONTACTS_PROJECTION,
                        People.TIMES_CONTACTED + " > 0", null,
                        People.TIMES_CONTACTED + " DESC, " + getSortOrder(CONTACTS_PROJECTION));
                
            }

            case MODE_STREQUENT: {
                Uri uri;
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(People.CONTENT_URI, "strequent/filter/"
                            + Uri.encode(filter));
                } else {
                    uri = Uri.withAppendedPath(People.CONTENT_URI, "strequent");
                }
                return resolver.query(uri, STREQUENT_PROJECTION, null, null, null);
            }

            case MODE_PICK_PHONE: {
                Uri uri;
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Phones.CONTENT_URI, "filter_name/"
                            + Uri.encode(filter));
                } else {
                    uri = Phones.CONTENT_URI;
                }
                return resolver.query(uri, PHONES_PROJECTION, null, null,
                        getSortOrder(PHONES_PROJECTION));
            }
        }
        throw new UnsupportedOperationException("filtering not allowed in mode " + mMode);
    }

    /**
     * Calls the currently selected list item.
     * @return true if the call was initiated, false otherwise
     */
    boolean callSelection() {
        ListView list = getListView();
        if (list.hasFocus()) {
            Cursor cursor = (Cursor) list.getSelectedItem();
            if (cursor != null) {
                long phoneId = cursor.getLong(PRIMARY_PHONE_ID_COLUMN_INDEX);
                if (phoneId == 0) {
                    // There is no phone number.
                    signalError();
                    return false;
                }
                Uri uri = ContentUris.withAppendedId(Phones.CONTENT_URI, phoneId);
                Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, uri);
                startActivity(intent);
                return true;
            }
        }

        return false;
    }

    /**
     * Signal an error to the user.
     */
    void signalError() {
        //TODO play an error beep or something...
    }

    Cursor getItemForView(View view) {
        ListView listView = getListView();
        int index = listView.getPositionForView(view);
        if (index < 0) {
            return null;
        }
        return (Cursor) listView.getAdapter().getItem(index);
    }

    private void setGroupEntries(AlertDialog.Builder builder) {
        boolean syncEverything;
        // For now we only support a single account and the UI doesn't know what
        // the account name is, so we're using a global setting for SYNC_EVERYTHING.
        // Some day when we add multiple accounts to the UI this should use the per
        // account setting.
        String value = Contacts.Settings.getSetting(getContentResolver(), null,
                Contacts.Settings.SYNC_EVERYTHING);
        if (value == null) {
            // If nothing is set yet we default to syncing everything
            syncEverything = true;
        } else {
            syncEverything = !TextUtils.isEmpty(value) && !"0".equals(value);
        }

        Cursor cursor;
        if (!syncEverything) {
            cursor = getContentResolver().query(Groups.CONTENT_URI, GROUPS_PROJECTION,
                    Groups.SHOULD_SYNC + " != 0", null, Groups.DEFAULT_SORT_ORDER);
        } else {
            cursor = getContentResolver().query(Groups.CONTENT_URI, GROUPS_PROJECTION,
                    null, null, Groups.DEFAULT_SORT_ORDER);
        }
        try {
            ArrayList<CharSequence> groups = new ArrayList<CharSequence>();
            ArrayList<CharSequence> prefStrings = new ArrayList<CharSequence>();

            // Add All Contacts
            groups.add(DISPLAY_GROUP_INDEX_ALL_CONTACTS, getString(R.string.showAllGroups));
            prefStrings.add("");
            
            // Add Contacts with phones
            groups.add(DISPLAY_GROUP_INDEX_ALL_CONTACTS_WITH_PHONES,
                    getString(R.string.groupNameWithPhones));
            prefStrings.add(GROUP_WITH_PHONES);
            
            int i = 3;
            int currentIndex = DISPLAY_GROUP_INDEX_ALL_CONTACTS;
            while (cursor.moveToNext()) {
                String systemId = cursor.getString(GROUPS_COLUMN_INDEX_SYSTEM_ID);
                String name = cursor.getString(GROUPS_COLUMN_INDEX_NAME);
                if (cursor.isNull(GROUPS_COLUMN_INDEX_SYSTEM_ID)
                        && !Groups.GROUP_MY_CONTACTS.equals(systemId)) {
                    // All groups that aren't My Contacts, since that one is localized on the phone
                    groups.add(name);
                    if (name.equals(mDisplayInfo)) {
                        currentIndex = i;
                    }
                    i++;
                } else {
                    // The My Contacts group
                    groups.add(DISPLAY_GROUP_INDEX_MY_CONTACTS,
                            getString(R.string.groupNameMyContacts));
                    if (mDisplayType == DISPLAY_TYPE_SYSTEM_GROUP
                            && Groups.GROUP_MY_CONTACTS.equals(mDisplayInfo)) {
                        currentIndex = DISPLAY_GROUP_INDEX_MY_CONTACTS;
                    }
                    mDisplayGroupsIncludesMyContacts = true;
                }
            }
            if (mMode == MODE_ALL_CONTACTS) {
                currentIndex = DISPLAY_GROUP_INDEX_ALL_CONTACTS;
            } else if (mMode == MODE_WITH_PHONES) {
                currentIndex = DISPLAY_GROUP_INDEX_ALL_CONTACTS_WITH_PHONES;
            }
            mDisplayGroups = groups.toArray(new CharSequence[groups.size()]);
            builder.setSingleChoiceItems(mDisplayGroups,
                    currentIndex, this);
            mDisplayGroupOriginalSelection = currentIndex;
        } finally {
            cursor.close();
        }
    }

    private static final class QueryHandler extends AsyncQueryHandler {
        private final WeakReference<ContactsListActivity> mActivity;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<ContactsListActivity>((ContactsListActivity) context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final ContactsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {
                activity.mAdapter.setLoading(false);
                activity.getListView().clearTextFilter();                
                activity.mAdapter.changeCursor(cursor);
                
                // Handle SHOW_OR_CREATE now that we have valid cursor
                if (activity.mMode == MODE_SHOW_OR_CREATE_CONTACT) {
                    activity.handleShowOrCreate(cursor);
                }
    
                // Now that the cursor is populated again, it's possible to restore the list state
                if (activity.mListState != null) {
                    activity.mList.onRestoreInstanceState(activity.mListState);
                    if (activity.mListHasFocus) {
                        activity.mList.requestFocus();
                    }
                    activity.mListHasFocus = false;
                    activity.mListState = null;
                }
            } else {
                cursor.close();
            }
        }
    }

    final static class ContactListItemCache {
        public TextView nameView;
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public TextView labelView;
        public CharArrayBuffer labelBuffer = new CharArrayBuffer(128);
        public TextView numberView;
        public CharArrayBuffer numberBuffer = new CharArrayBuffer(128);
        public ImageView presenceView;
        public ImageView photoView;
    }

    private final class ContactItemListAdapter extends ResourceCursorAdapter 
            implements SectionIndexer {
        private SectionIndexer mIndexer;
        private String mAlphabet;
        private boolean mLoading = true;
        private CharSequence mUnknownNameText;
        private CharSequence[] mLocalizedLabels;
        private boolean mDisplayPhotos = false;
        private SparseArray<SoftReference<Bitmap>> mBitmapCache = null;

        public ContactItemListAdapter(Context context) {
            super(context, R.layout.contacts_list_item, null, false);
            
            mAlphabet = context.getString(com.android.internal.R.string.fast_scroll_alphabet);
            
            mUnknownNameText = context.getText(android.R.string.unknownName);
            switch (mMode) {
                case MODE_PICK_POSTAL:
                    mLocalizedLabels = EditContactActivity.getLabelsForKind(mContext,
                            Contacts.KIND_POSTAL);
                    break;
                default:
                    mLocalizedLabels = EditContactActivity.getLabelsForKind(mContext,
                            Contacts.KIND_PHONE);
                    break;
            }
            
            if ((mMode & MODE_MASK_SHOW_PHOTOS) == MODE_MASK_SHOW_PHOTOS) {
                mDisplayPhotos = true;
                setViewResource(R.layout.contacts_list_item_photo);
                mBitmapCache = new SparseArray<SoftReference<Bitmap>>();
            }
        }

        private SectionIndexer getNewIndexer(Cursor cursor) {
            if (Locale.getDefault().equals(Locale.JAPAN)) {
                return new JapaneseContactListIndexer(cursor, SORT_STRING_INDEX);
            } else {
                return new AlphabetIndexer(cursor, NAME_COLUMN_INDEX, mAlphabet);
            }
        }
        
        /**
         * Callback on the UI thread when the content observer on the backing cursor fires.
         * Instead of calling requery we need to do an async query so that the requery doesn't
         * block the UI thread for a long time. 
         */
        @Override
        protected void onContentChanged() {
            CharSequence constraint = getListView().getTextFilter();
            if (!TextUtils.isEmpty(constraint)) {
                // Reset the filter state then start an async filter operation
                Filter filter = getFilter();
                filter.filter(constraint);
            } else {
                // Start an async query
                startQuery();
            }
        }
        
        public void setLoading(boolean loading) {
            mLoading = loading;
        }

        @Override
        public boolean isEmpty() {
            if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW) {
                // This mode mask adds a header and we always want it to show up, even
                // if the list is empty, so always claim the list is not empty.
                return false;
            } else {
                if (mLoading) {
                    // We don't want the empty state to show when loading.
                    return false;
                } else {
                    return super.isEmpty();
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final View view = super.newView(context, cursor, parent);

            final ContactListItemCache cache = new ContactListItemCache();
            cache.nameView = (TextView) view.findViewById(R.id.name);
            cache.labelView = (TextView) view.findViewById(R.id.label);
            cache.numberView = (TextView) view.findViewById(R.id.number);
            cache.presenceView = (ImageView) view.findViewById(R.id.presence);
            cache.photoView = (ImageView) view.findViewById(R.id.photo);
            view.setTag(cache);

            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();
            
            // Set the name           
            cursor.copyStringToBuffer(NAME_COLUMN_INDEX, cache.nameBuffer);
            int size = cache.nameBuffer.sizeCopied;
            if (size != 0) {
                cache.nameView.setText(cache.nameBuffer.data, 0, size);
            } else {
                cache.nameView.setText(mUnknownNameText);
            }
            
            // Set the phone number
            TextView numberView = cache.numberView;
            TextView labelView = cache.labelView;
            cursor.copyStringToBuffer(NUMBER_COLUMN_INDEX, cache.numberBuffer);
            size = cache.numberBuffer.sizeCopied;
            if (size != 0) {
                numberView.setText(cache.numberBuffer.data, 0, size);
                numberView.setVisibility(View.VISIBLE);
                labelView.setVisibility(View.VISIBLE);
            } else {
                numberView.setVisibility(View.GONE);
                labelView.setVisibility(View.GONE);
            }

            // Set the label
            if (!cursor.isNull(TYPE_COLUMN_INDEX)) {
                int type = cursor.getInt(TYPE_COLUMN_INDEX);

                if (type != People.Phones.TYPE_CUSTOM) {
                    try {
                        labelView.setText(mLocalizedLabels[type - 1]);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        labelView.setText(mLocalizedLabels[People.Phones.TYPE_HOME - 1]);
                    }
                } else {
                    cursor.copyStringToBuffer(LABEL_COLUMN_INDEX, cache.labelBuffer);
                    // Don't check size, if it's zero just don't show anything
                    labelView.setText(cache.labelBuffer.data, 0, cache.labelBuffer.sizeCopied);
                }
            } else {
                // There is no label, hide the the view
                labelView.setVisibility(View.GONE);
            }

            // Set the proper icon (star or presence or nothing)
            ImageView presenceView = cache.presenceView;
            if (mMode != MODE_STREQUENT) {
                if ((mMode & MODE_MASK_NO_PRESENCE) == 0) {
                    int serverStatus;
                    if (!cursor.isNull(SERVER_STATUS_COLUMN_INDEX)) {
                        serverStatus = cursor.getInt(SERVER_STATUS_COLUMN_INDEX);
                        presenceView.setImageResource(
                                Presence.getPresenceIconResourceId(serverStatus));
                        presenceView.setVisibility(View.VISIBLE);
                    } else {
                        presenceView.setVisibility(View.GONE);
                    }
                } else {
                    presenceView.setVisibility(View.GONE);
                }
            } else {
                if (cursor.getInt(STARRED_COLUMN_INDEX) != 0) {
                    presenceView.setImageResource(R.drawable.star_on);
                    presenceView.setVisibility(View.VISIBLE);
                } else {
                    presenceView.setVisibility(View.GONE);
                }
            }

            // Set the photo, if requested
            if (mDisplayPhotos) {
                Bitmap photo = null;

                // Look for the cached bitmap
                int pos = cursor.getPosition();
                SoftReference<Bitmap> ref = mBitmapCache.get(pos);
                if (ref != null) {
                    photo = ref.get();
                }

                if (photo == null) {
                    // Bitmap cache miss, decode it from the cursor
                    if (!cursor.isNull(PHOTO_COLUMN_INDEX)) {
                        try {
                            byte[] photoData = cursor.getBlob(PHOTO_COLUMN_INDEX);
                            photo = BitmapFactory.decodeByteArray(photoData, 0,
                                    photoData.length);
                            mBitmapCache.put(pos, new SoftReference<Bitmap>(photo));
                        } catch (OutOfMemoryError e) {
                            // Not enough memory for the photo, use the default one instead
                            photo = null;
                        }
                    }
                }

                // Bind the photo, or use the fallback no photo resource
                if (photo != null) {
                    cache.photoView.setImageBitmap(photo);
                } else {
                    cache.photoView.setImageResource(R.drawable.ic_contact_picture);
                }
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);

            // Update the indexer for the fast scroll widget
            updateIndexer(cursor);

            // Clear the photo bitmap cache, if there is one
            if (mBitmapCache != null) {
                mBitmapCache.clear();
            }
        }
        
        private void updateIndexer(Cursor cursor) {
            if (mIndexer == null) {
                mIndexer = getNewIndexer(cursor);
            } else {
                if (Locale.getDefault().equals(Locale.JAPAN)) {
                    if (mIndexer instanceof JapaneseContactListIndexer) {
                        ((JapaneseContactListIndexer)mIndexer).setCursor(cursor);
                    } else {
                        mIndexer = getNewIndexer(cursor);
                    }
                } else {
                    if (mIndexer instanceof AlphabetIndexer) {
                        ((AlphabetIndexer)mIndexer).setCursor(cursor);
                    } else {
                        mIndexer = getNewIndexer(cursor);
                    }
                }
            }
        }
        
        /**
         * Run the query on a helper thread. Beware that this code does not run
         * on the main UI thread!
         */
        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            return doFilter(constraint.toString());
        }
        
        public Object [] getSections() {
            if (mMode == MODE_STREQUENT) {
                return new String[] { " " };
            } else {
                return mIndexer.getSections();
           }
        }
        
        public int getPositionForSection(int sectionIndex) {
            if (mMode == MODE_STREQUENT) {
                return 0;
            }

            if (mIndexer == null) {
                Cursor cursor = mAdapter.getCursor();
                if (cursor == null) {
                    // No cursor, the section doesn't exist so just return 0
                    return 0;
                }
                mIndexer = getNewIndexer(cursor);
            }

            return mIndexer.getPositionForSection(sectionIndex);
        }
        
        public int getSectionForPosition(int position) {
            // Note: JapaneseContactListIndexer depends on the fact
            // this method always returns 0. If you change this,
            // please care it too.
            return 0;
        }        
    }
}


