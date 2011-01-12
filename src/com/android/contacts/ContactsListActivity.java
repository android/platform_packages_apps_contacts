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

import com.android.contacts.TextHighlightingAnimation.TextWithHighlighting;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.ui.ContactsPreferences;
import com.android.contacts.ui.ContactsPreferencesActivity;
import com.android.contacts.ui.ContactsPreferencesActivity.Prefs;
import com.android.contacts.util.AccountSelectionUtil;
import com.android.contacts.util.Constants;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.SearchManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.IContentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.provider.Contacts.PeopleColumns;
import android.provider.Contacts.Phones;
import android.provider.ContactsContract.ContactCounts;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.ProviderStatus;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.SearchSnippetColumns;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.Intents.UI;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Html;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Displays a list of contacts. Usually is embedded into the ContactsActivity.
 */
@SuppressWarnings("deprecation")
public class ContactsListActivity extends ListActivity implements View.OnCreateContextMenuListener,
        View.OnClickListener, View.OnKeyListener, TextWatcher, TextView.OnEditorActionListener,
        OnFocusChangeListener, OnTouchListener {

    public static class JoinContactActivity extends ContactsListActivity {

    }

    public static class ContactsSearchActivity extends ContactsListActivity {

    }

    private static final String TAG = "ContactsListActivity";

    private static final boolean ENABLE_ACTION_ICON_OVERLAYS = true;

    private static final String LIST_STATE_KEY = "liststate";
    private static final String SHORTCUT_ACTION_KEY = "shortcutAction";

    static final int MENU_ITEM_VIEW_CONTACT = 1;
    static final int MENU_ITEM_CALL = 2;
    static final int MENU_ITEM_EDIT_BEFORE_CALL = 3;
    static final int MENU_ITEM_SEND_SMS = 4;
    static final int MENU_ITEM_SEND_IM = 5;
    static final int MENU_ITEM_EDIT = 6;
    static final int MENU_ITEM_DELETE = 7;
    static final int MENU_ITEM_TOGGLE_STAR = 8;

    private static final int SUBACTIVITY_NEW_CONTACT = 1;
    private static final int SUBACTIVITY_VIEW_CONTACT = 2;
    private static final int SUBACTIVITY_DISPLAY_GROUP = 3;
    private static final int SUBACTIVITY_SEARCH = 4;
    private static final int SUBACTIVITY_FILTER = 5;

    private static final int TEXT_HIGHLIGHTING_ANIMATION_DURATION = 350;

    /**
     * The action for the join contact activity.
     * <p>
     * Input: extra field {@link #EXTRA_AGGREGATE_ID} is the aggregate ID.
     *
     * TODO: move to {@link ContactsContract}.
     */
    public static final String JOIN_AGGREGATE =
            "com.android.contacts.action.JOIN_AGGREGATE";

    /**
     * Used with {@link #JOIN_AGGREGATE} to give it the target for aggregation.
     * <p>
     * Type: LONG
     */
    public static final String EXTRA_AGGREGATE_ID =
            "com.android.contacts.action.AGGREGATE_ID";

    /**
     * Used with {@link #JOIN_AGGREGATE} to give it the name of the aggregation target.
     * <p>
     * Type: STRING
     */
    @Deprecated
    public static final String EXTRA_AGGREGATE_NAME =
            "com.android.contacts.action.AGGREGATE_NAME";

    public static final String AUTHORITIES_FILTER_KEY = "authorities";

    private static final Uri CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS =
            buildSectionIndexerUri(Contacts.CONTENT_URI);

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
    /** Mask for hiding additional information e.g. primary phone number in the list */
    static final int MODE_MASK_NO_DATA = 0x04000000;
    /** Mask for showing a call button in the list */
    static final int MODE_MASK_SHOW_CALL_BUTTON = 0x02000000;
    /** Mask to disable quickcontact (images will show as normal images) */
    static final int MODE_MASK_DISABLE_QUIKCCONTACT = 0x01000000;
    /** Mask to show the total number of contacts at the top */
    static final int MODE_MASK_SHOW_NUMBER_OF_CONTACTS = 0x00800000;

    /** Unknown mode */
    static final int MODE_UNKNOWN = 0;
    /** Default mode */
    static final int MODE_DEFAULT = 4 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Custom mode */
    static final int MODE_CUSTOM = 8;
    /** Show all starred contacts */
    static final int MODE_STARRED = 20 | MODE_MASK_SHOW_PHOTOS;
    /** Show frequently contacted contacts */
    static final int MODE_FREQUENT = 30 | MODE_MASK_SHOW_PHOTOS;
    /** Show starred and the frequent */
    static final int MODE_STREQUENT = 35 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_SHOW_CALL_BUTTON;
    /** Show all contacts and pick them when clicking */
    static final int MODE_PICK_CONTACT = 40 | MODE_MASK_PICKER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts as well as the option to create a new one */
    static final int MODE_PICK_OR_CREATE_CONTACT = 42 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider and pick them when clicking */
    static final int MODE_LEGACY_PICK_PERSON = 43 | MODE_MASK_PICKER
            | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all people through the legacy provider as well as the option to create a new one */
    static final int MODE_LEGACY_PICK_OR_CREATE_PERSON = 44 | MODE_MASK_PICKER
            | MODE_MASK_CREATE_NEW | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all contacts and pick them when clicking, and allow creating a new contact */
    static final int MODE_INSERT_OR_EDIT_CONTACT = 45 | MODE_MASK_PICKER | MODE_MASK_CREATE_NEW
            | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;
    /** Show all phone numbers and pick them when clicking */
    static final int MODE_PICK_PHONE = 50 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE;
    /** Show all phone numbers through the legacy provider and pick them when clicking */
    static final int MODE_LEGACY_PICK_PHONE =
            51 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    static final int MODE_PICK_POSTAL =
            55 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    /** Show all postal addresses and pick them when clicking */
    static final int MODE_LEGACY_PICK_POSTAL =
            56 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE | MODE_MASK_NO_FILTER;
    static final int MODE_GROUP = 57 | MODE_MASK_SHOW_PHOTOS;
    /** Run a search query */
    static final int MODE_QUERY = 60 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;
    /** Run a search query in PICK mode, but that still launches to VIEW */
    static final int MODE_QUERY_PICK_TO_VIEW = 65 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Show join suggestions followed by an A-Z list */
    static final int MODE_JOIN_CONTACT = 70 | MODE_MASK_PICKER | MODE_MASK_NO_PRESENCE
            | MODE_MASK_NO_DATA | MODE_MASK_SHOW_PHOTOS | MODE_MASK_DISABLE_QUIKCCONTACT;

    /** Run a search query in a PICK mode */
    static final int MODE_QUERY_PICK = 75 | MODE_MASK_SHOW_PHOTOS | MODE_MASK_NO_FILTER
            | MODE_MASK_PICKER | MODE_MASK_DISABLE_QUIKCCONTACT | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in a PICK_PHONE mode */
    static final int MODE_QUERY_PICK_PHONE = 80 | MODE_MASK_NO_FILTER | MODE_MASK_PICKER
            | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /** Run a search query in PICK mode, but that still launches to EDIT */
    static final int MODE_QUERY_PICK_TO_EDIT = 85 | MODE_MASK_NO_FILTER | MODE_MASK_SHOW_PHOTOS
            | MODE_MASK_PICKER | MODE_MASK_SHOW_NUMBER_OF_CONTACTS;

    /**
     * An action used to do perform search while in a contact picker.  It is initiated
     * by the ContactListActivity itself.
     */
    private static final String ACTION_SEARCH_INTERNAL = "com.android.contacts.INTERNAL_SEARCH";

    /** Maximum number of suggestions shown for joining aggregates */
    static final int MAX_SUGGESTIONS = 4;

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
    };
    static final String[] CONTACTS_SUMMARY_PROJECTION_FROM_EMAIL = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        // email lookup doesn't included HAS_PHONE_NUMBER in projection
    };

    static final String[] CONTACTS_SUMMARY_FILTER_PROJECTION = new String[] {
        Contacts._ID,                       // 0
        Contacts.DISPLAY_NAME_PRIMARY,      // 1
        Contacts.DISPLAY_NAME_ALTERNATIVE,  // 2
        Contacts.SORT_KEY_PRIMARY,          // 3
        Contacts.STARRED,                   // 4
        Contacts.TIMES_CONTACTED,           // 5
        Contacts.CONTACT_PRESENCE,          // 6
        Contacts.PHOTO_ID,                  // 7
        Contacts.LOOKUP_KEY,                // 8
        Contacts.PHONETIC_NAME,             // 9
        Contacts.HAS_PHONE_NUMBER,          // 10
        SearchSnippetColumns.SNIPPET_MIMETYPE, // 11
        SearchSnippetColumns.SNIPPET_DATA1,     // 12
        SearchSnippetColumns.SNIPPET_DATA4,     // 13
    };

    static final String[] LEGACY_PEOPLE_PROJECTION = new String[] {
        People._ID,                         // 0
        People.DISPLAY_NAME,                // 1
        People.DISPLAY_NAME,                // 2
        People.DISPLAY_NAME,                // 3
        People.STARRED,                     // 4
        PeopleColumns.TIMES_CONTACTED,      // 5
        People.PRESENCE_STATUS,             // 6
    };
    static final int SUMMARY_ID_COLUMN_INDEX = 0;
    static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 1;
    static final int SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX = 2;
    static final int SUMMARY_SORT_KEY_PRIMARY_COLUMN_INDEX = 3;
    static final int SUMMARY_STARRED_COLUMN_INDEX = 4;
    static final int SUMMARY_TIMES_CONTACTED_COLUMN_INDEX = 5;
    static final int SUMMARY_PRESENCE_STATUS_COLUMN_INDEX = 6;
    static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 7;
    static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 8;
    static final int SUMMARY_PHONETIC_NAME_COLUMN_INDEX = 9;
    static final int SUMMARY_HAS_PHONE_COLUMN_INDEX = 10;
    static final int SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX = 11;
    static final int SUMMARY_SNIPPET_DATA1_COLUMN_INDEX = 12;
    static final int SUMMARY_SNIPPET_DATA4_COLUMN_INDEX = 13;

    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, //0
        Phone.TYPE, //1
        Phone.LABEL, //2
        Phone.NUMBER, //3
        Phone.DISPLAY_NAME, // 4
        Phone.CONTACT_ID, // 5
    };
    static final String[] LEGACY_PHONES_PROJECTION = new String[] {
        Phones._ID, //0
        Phones.TYPE, //1
        Phones.LABEL, //2
        Phones.NUMBER, //3
        People.DISPLAY_NAME, // 4
    };
    static final int PHONE_ID_COLUMN_INDEX = 0;
    static final int PHONE_TYPE_COLUMN_INDEX = 1;
    static final int PHONE_LABEL_COLUMN_INDEX = 2;
    static final int PHONE_NUMBER_COLUMN_INDEX = 3;
    static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 4;
    static final int PHONE_CONTACT_ID_COLUMN_INDEX = 5;

    static final String[] POSTALS_PROJECTION = new String[] {
        StructuredPostal._ID, //0
        StructuredPostal.TYPE, //1
        StructuredPostal.LABEL, //2
        StructuredPostal.DATA, //3
        StructuredPostal.DISPLAY_NAME, // 4
    };
    static final String[] LEGACY_POSTALS_PROJECTION = new String[] {
        ContactMethods._ID, //0
        ContactMethods.TYPE, //1
        ContactMethods.LABEL, //2
        ContactMethods.DATA, //3
        People.DISPLAY_NAME, // 4
    };
    static final String[] RAW_CONTACTS_PROJECTION = new String[] {
        RawContacts._ID, //0
        RawContacts.CONTACT_ID, //1
        RawContacts.ACCOUNT_TYPE, //2
    };

    static final int POSTAL_ID_COLUMN_INDEX = 0;
    static final int POSTAL_TYPE_COLUMN_INDEX = 1;
    static final int POSTAL_LABEL_COLUMN_INDEX = 2;
    static final int POSTAL_ADDRESS_COLUMN_INDEX = 3;
    static final int POSTAL_DISPLAY_NAME_COLUMN_INDEX = 4;

    private static final int QUERY_TOKEN = 42;

    static final String KEY_PICKER_MODE = "picker_mode";

    private ContactItemListAdapter mAdapter;

    int mMode = MODE_DEFAULT;

    private QueryHandler mQueryHandler;
    private boolean mJustCreated;
    private boolean mSyncEnabled;
    Uri mSelectedContactUri;

//    private boolean mDisplayAll;
    private boolean mDisplayOnlyPhones;

    private Uri mGroupUri;

    private long mQueryAggregateId;

    private ArrayList<Long> mWritableRawContactIds = new ArrayList<Long>();
    private int  mWritableSourcesCnt;
    private int  mReadOnlySourcesCnt;

    /**
     * Used to keep track of the scroll state of the list.
     */
    private Parcelable mListState = null;

    private String mShortcutAction;

    /**
     * Internal query type when in mode {@link #MODE_QUERY_PICK_TO_VIEW}.
     */
    private int mQueryMode = QUERY_MODE_NONE;

    private static final int QUERY_MODE_NONE = -1;
    private static final int QUERY_MODE_MAILTO = 1;
    private static final int QUERY_MODE_TEL = 2;

    private int mProviderStatus = ProviderStatus.STATUS_NORMAL;

    private boolean mSearchMode;
    private boolean mSearchResultsMode;
    private boolean mShowNumberOfContacts;

    private boolean mShowSearchSnippets;
    private boolean mSearchInitiated;

    private String mInitialFilter;

    private static final String CLAUSE_ONLY_VISIBLE = Contacts.IN_VISIBLE_GROUP + "=1";
    private static final String CLAUSE_ONLY_PHONES = Contacts.HAS_PHONE_NUMBER + "=1";

    /**
     * In the {@link #MODE_JOIN_CONTACT} determines whether we display a list item with the label
     * "Show all contacts" or actually show all contacts
     */
    private boolean mJoinModeShowAllContacts;

    /**
     * The ID of the special item described above.
     */
    private static final long JOIN_MODE_SHOW_ALL_CONTACTS_ID = -2;

    // Uri matcher for contact id
    private static final int CONTACTS_ID = 1001;
    private static final UriMatcher sContactsIdMatcher;

    private ContactPhotoLoader mPhotoLoader;

    final String[] sLookupProjection = new String[] {
            Contacts.LOOKUP_KEY
    };

    static {
        sContactsIdMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sContactsIdMatcher.addURI(ContactsContract.AUTHORITY, "contacts/#", CONTACTS_ID);
    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            if (mSelectedContactUri != null) {
                getContentResolver().delete(mSelectedContactUri, null, null);
            }
        }
    }

    /**
     * A {@link TextHighlightingAnimation} that redraws just the contact display name in a
     * list item.
     */
    private static class NameHighlightingAnimation extends TextHighlightingAnimation {
        private final ListView mListView;

        private NameHighlightingAnimation(ListView listView, int duration) {
            super(duration);
            this.mListView = listView;
        }

        /**
         * Redraws all visible items of the list corresponding to contacts
         */
        @Override
        protected void invalidate() {
            int childCount = mListView.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View itemView = mListView.getChildAt(i);
                if (itemView instanceof ContactListItemView) {
                    final ContactListItemView view = (ContactListItemView)itemView;
                    view.getNameTextView().invalidate();
                }
            }
        }

        @Override
        protected void onAnimationStarted() {
            mListView.setScrollingCacheEnabled(false);
        }

        @Override
        protected void onAnimationEnded() {
            mListView.setScrollingCacheEnabled(true);
        }
    }

    // The size of a home screen shortcut icon.
    private int mIconSize;
    private ContactsPreferences mContactsPrefs;
    private int mDisplayOrder;
    private int mSortOrder;
    private boolean mHighlightWhenScrolling;
    private TextHighlightingAnimation mHighlightingAnimation;
    private SearchEditText mSearchEditText;

    /**
     * An approximation of the background color of the pinned header. This color
     * is used when the pinned header is being pushed up.  At that point the header
     * "fades away".  Rather than computing a faded bitmap based on the 9-patch
     * normally used for the background, we will use a solid color, which will
     * provide better performance and reduced complexity.
     */
    private int mPinnedHeaderBackgroundColor;

    private ContentObserver mProviderStatusObserver = new ContentObserver(new Handler()) {

        @Override
        public void onChange(boolean selfChange) {
            checkProviderState(true);
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mIconSize = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size);
        mContactsPrefs = new ContactsPreferences(this);
        mPhotoLoader = new ContactPhotoLoader(this, R.drawable.ic_contact_list_picture);

        // Resolve the intent
        final Intent intent = getIntent();

        // Allow the title to be set to a custom String using an extra on the intent
        String title = intent.getStringExtra(UI.TITLE_EXTRA_KEY);
        if (title != null) {
            setTitle(title);
        }

        String action = intent.getAction();
        String component = intent.getComponent().getClassName();

        // When we get a FILTER_CONTACTS_ACTION, it represents search in the context
        // of some other action. Let's retrieve the original action to provide proper
        // context for the search queries.
        if (UI.FILTER_CONTACTS_ACTION.equals(action)) {
            mSearchMode = true;
            mShowSearchSnippets = true;
            Bundle extras = intent.getExtras();
            if (extras != null) {
                mInitialFilter = extras.getString(UI.FILTER_TEXT_EXTRA_KEY);
                String originalAction =
                        extras.getString(ContactsSearchManager.ORIGINAL_ACTION_EXTRA_KEY);
                if (originalAction != null) {
                    action = originalAction;
                }
                String originalComponent =
                        extras.getString(ContactsSearchManager.ORIGINAL_COMPONENT_EXTRA_KEY);
                if (originalComponent != null) {
                    component = originalComponent;
                }
            } else {
                mInitialFilter = null;
            }
        }

        Log.i(TAG, "Called with action: " + action);
        mMode = MODE_UNKNOWN;
        if (UI.LIST_DEFAULT.equals(action) || UI.FILTER_CONTACTS_ACTION.equals(action)) {
            mMode = MODE_DEFAULT;
            // When mDefaultMode is true the mode is set in onResume(), since the preferneces
            // activity may change it whenever this activity isn't running
        } else if (UI.LIST_GROUP_ACTION.equals(action)) {
            mMode = MODE_GROUP;
            String groupName = intent.getStringExtra(UI.GROUP_NAME_EXTRA_KEY);
            if (TextUtils.isEmpty(groupName)) {
                finish();
                return;
            }
            buildUserGroupUri(groupName);
        } else if (UI.LIST_ALL_CONTACTS_ACTION.equals(action)) {
            mMode = MODE_CUSTOM;
            mDisplayOnlyPhones = false;
        } else if (UI.LIST_STARRED_ACTION.equals(action)) {
            mMode = mSearchMode ? MODE_DEFAULT : MODE_STARRED;
        } else if (UI.LIST_FREQUENT_ACTION.equals(action)) {
            mMode = mSearchMode ? MODE_DEFAULT : MODE_FREQUENT;
        } else if (UI.LIST_STREQUENT_ACTION.equals(action)) {
            mMode = mSearchMode ? MODE_DEFAULT : MODE_STREQUENT;
        } else if (UI.LIST_CONTACTS_WITH_PHONES_ACTION.equals(action)) {
            mMode = MODE_CUSTOM;
            mDisplayOnlyPhones = true;
        } else if (Intent.ACTION_PICK.equals(action)) {
            // XXX These should be showing the data from the URI given in
            // the Intent.
            final String type = intent.resolveType(this);
            if (Contacts.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_CONTACT;
            } else if (People.CONTENT_TYPE.equals(type)) {
                mMode = MODE_LEGACY_PICK_PERSON;
            } else if (Phone.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_PHONE;
            } else if (Phones.CONTENT_TYPE.equals(type)) {
                mMode = MODE_LEGACY_PICK_PHONE;
            } else if (StructuredPostal.CONTENT_TYPE.equals(type)) {
                mMode = MODE_PICK_POSTAL;
            } else if (ContactMethods.CONTENT_POSTAL_TYPE.equals(type)) {
                mMode = MODE_LEGACY_PICK_POSTAL;
            }
        } else if (Intent.ACTION_CREATE_SHORTCUT.equals(action)) {
            if (component.equals("alias.DialShortcut")) {
                mMode = MODE_PICK_PHONE;
                mShortcutAction = Intent.ACTION_CALL;
                mShowSearchSnippets = false;
                setTitle(R.string.callShortcutActivityTitle);
            } else if (component.equals("alias.MessageShortcut")) {
                mMode = MODE_PICK_PHONE;
                mShortcutAction = Intent.ACTION_SENDTO;
                mShowSearchSnippets = false;
                setTitle(R.string.messageShortcutActivityTitle);
            } else if (mSearchMode) {
                mMode = MODE_PICK_CONTACT;
                mShortcutAction = Intent.ACTION_VIEW;
                setTitle(R.string.shortcutActivityTitle);
            } else {
                mMode = MODE_PICK_OR_CREATE_CONTACT;
                mShortcutAction = Intent.ACTION_VIEW;
                setTitle(R.string.shortcutActivityTitle);
            }
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            final String type = intent.resolveType(this);
            if (Contacts.CONTENT_ITEM_TYPE.equals(type)) {
                if (mSearchMode) {
                    mMode = MODE_PICK_CONTACT;
                } else {
                    mMode = MODE_PICK_OR_CREATE_CONTACT;
                }
            } else if (Phone.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_PHONE;
            } else if (Phones.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_LEGACY_PICK_PHONE;
            } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(type)) {
                mMode = MODE_PICK_POSTAL;
            } else if (ContactMethods.CONTENT_POSTAL_ITEM_TYPE.equals(type)) {
                mMode = MODE_LEGACY_PICK_POSTAL;
            }  else if (People.CONTENT_ITEM_TYPE.equals(type)) {
                if (mSearchMode) {
                    mMode = MODE_LEGACY_PICK_PERSON;
                } else {
                    mMode = MODE_LEGACY_PICK_OR_CREATE_PERSON;
                }
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

            // See if search request has extras to specify query
            if (intent.hasExtra(Insert.EMAIL)) {
                mMode = MODE_QUERY_PICK_TO_VIEW;
                mQueryMode = QUERY_MODE_MAILTO;
                mInitialFilter = intent.getStringExtra(Insert.EMAIL);
            } else if (intent.hasExtra(Insert.PHONE)) {
                mMode = MODE_QUERY_PICK_TO_VIEW;
                mQueryMode = QUERY_MODE_TEL;
                mInitialFilter = intent.getStringExtra(Insert.PHONE);
            } else {
                // Otherwise handle the more normal search case
                mMode = MODE_QUERY;
                mShowSearchSnippets = true;
                mInitialFilter = getIntent().getStringExtra(SearchManager.QUERY);
            }
            mSearchResultsMode = true;
        } else if (ACTION_SEARCH_INTERNAL.equals(action)) {
            String originalAction = null;
            Bundle extras = intent.getExtras();
            if (extras != null) {
                originalAction = extras.getString(ContactsSearchManager.ORIGINAL_ACTION_EXTRA_KEY);
            }
            mShortcutAction = intent.getStringExtra(SHORTCUT_ACTION_KEY);

            if (Intent.ACTION_INSERT_OR_EDIT.equals(originalAction)) {
                mMode = MODE_QUERY_PICK_TO_EDIT;
                mShowSearchSnippets = true;
                mInitialFilter = getIntent().getStringExtra(SearchManager.QUERY);
            } else if (mShortcutAction != null && intent.hasExtra(Insert.PHONE)) {
                mMode = MODE_QUERY_PICK_PHONE;
                mQueryMode = QUERY_MODE_TEL;
                mInitialFilter = intent.getStringExtra(Insert.PHONE);
            } else {
                mMode = MODE_QUERY_PICK;
                mQueryMode = QUERY_MODE_NONE;
                mShowSearchSnippets = true;
                mInitialFilter = getIntent().getStringExtra(SearchManager.QUERY);
            }
            mSearchResultsMode = true;
        // Since this is the filter activity it receives all intents
        // dispatched from the SearchManager for security reasons
        // so we need to re-dispatch from here to the intended target.
        } else if (Intents.SEARCH_SUGGESTION_CLICKED.equals(action)) {
            Uri data = intent.getData();
            Uri telUri = null;
            if (sContactsIdMatcher.match(data) == CONTACTS_ID) {
                long contactId = Long.valueOf(data.getLastPathSegment());
                final Cursor cursor = queryPhoneNumbers(contactId);
                if (cursor != null) {
                    if (cursor.getCount() == 1 && cursor.moveToFirst()) {
                        int phoneNumberIndex = cursor.getColumnIndex(Phone.NUMBER);
                        String phoneNumber = cursor.getString(phoneNumberIndex);
                        telUri = Uri.parse("tel:" + phoneNumber);
                    }
                    cursor.close();
                }
            }
            // See if the suggestion was clicked with a search action key (call button)
            Intent newIntent;
            if ("call".equals(intent.getStringExtra(SearchManager.ACTION_MSG)) && telUri != null) {
                newIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, telUri);
            } else {
                newIntent = new Intent(Intent.ACTION_VIEW, data);
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
            // TODO actually support this in EditContactActivity.
            String number = intent.getData().getSchemeSpecificPart();
            Intent newIntent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            newIntent.putExtra(Intents.Insert.PHONE, number);
            startActivity(newIntent);
            finish();
            return;
        }

        if (JOIN_AGGREGATE.equals(action)) {
            if (mSearchMode) {
                mMode = MODE_PICK_CONTACT;
            } else {
                mMode = MODE_JOIN_CONTACT;
                mQueryAggregateId = intent.getLongExtra(EXTRA_AGGREGATE_ID, -1);
                if (mQueryAggregateId == -1) {
                    Log.e(TAG, "Intent " + action + " is missing required extra: "
                            + EXTRA_AGGREGATE_ID);
                    setResult(RESULT_CANCELED);
                    finish();
                }
            }
        }

        if (mMode == MODE_UNKNOWN) {
            mMode = MODE_DEFAULT;
        }

        if (((mMode & MODE_MASK_SHOW_NUMBER_OF_CONTACTS) != 0 || mSearchMode)
                && !mSearchResultsMode) {
            mShowNumberOfContacts = true;
        }

        if (mMode == MODE_JOIN_CONTACT) {
            setContentView(R.layout.contacts_list_content_join);
            TextView blurbView = (TextView)findViewById(R.id.join_contact_blurb);

            String blurb = getString(R.string.blurbJoinContactDataWith,
                    getContactDisplayName(mQueryAggregateId));
            blurbView.setText(blurb);
            mJoinModeShowAllContacts = true;
        } else if (mSearchMode) {
            setContentView(R.layout.contacts_search_content);
        } else if (mSearchResultsMode) {
            setContentView(R.layout.contacts_list_search_results);
            TextView titleText = (TextView)findViewById(R.id.search_results_for);
            titleText.setText(Html.fromHtml(getString(R.string.search_results_for,
                    "<b>" + mInitialFilter + "</b>")));
        } else {
            setContentView(R.layout.contacts_list_content);
        }

        setupListView();
        if (mSearchMode) {
            setupSearchView();
        }

        mQueryHandler = new QueryHandler(this);
        mJustCreated = true;

        mSyncEnabled = true;
    }

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void registerProviderStatusObserver() {
        getContentResolver().registerContentObserver(ProviderStatus.CONTENT_URI,
                false, mProviderStatusObserver);
    }

    /**
     * Register an observer for provider status changes - we will need to
     * reflect them in the UI.
     */
    private void unregisterProviderStatusObserver() {
        getContentResolver().unregisterContentObserver(mProviderStatusObserver);
    }

    private void setupListView() {
        final ListView list = getListView();
        final LayoutInflater inflater = getLayoutInflater();

        mHighlightingAnimation =
                new NameHighlightingAnimation(list, TEXT_HIGHLIGHTING_ANIMATION_DURATION);

        // Tell list view to not show dividers. We'll do it ourself so that we can *not* show
        // them when an A-Z headers is visible.
        list.setDividerHeight(0);
        list.setOnCreateContextMenuListener(this);

        mAdapter = new ContactItemListAdapter(this);
        setListAdapter(mAdapter);

        if (list instanceof PinnedHeaderListView && mAdapter.getDisplaySectionHeadersEnabled()) {
            mPinnedHeaderBackgroundColor =
                    getResources().getColor(R.color.pinned_header_background);
            PinnedHeaderListView pinnedHeaderList = (PinnedHeaderListView)list;
            View pinnedHeader = inflater.inflate(R.layout.list_section, list, false);
            pinnedHeaderList.setPinnedHeaderView(pinnedHeader);
        }

        list.setOnScrollListener(mAdapter);
        list.setOnKeyListener(this);
        list.setOnFocusChangeListener(this);
        list.setOnTouchListener(this);

        // We manually save/restore the listview state
        list.setSaveEnabled(false);
    }

    /**
     * Configures search UI.
     */
    private void setupSearchView() {
        mSearchEditText = (SearchEditText)findViewById(R.id.search_src_text);
        mSearchEditText.addTextChangedListener(this);
        mSearchEditText.setOnEditorActionListener(this);
        mSearchEditText.setText(mInitialFilter);
    }

    private String getContactDisplayName(long contactId) {
        String contactName = null;
        Cursor c = getContentResolver().query(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                new String[] {Contacts.DISPLAY_NAME}, null, null, null);
        try {
            if (c != null && c.moveToFirst()) {
                contactName = c.getString(0);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (contactName == null) {
            contactName = "";
        }

        return contactName;
    }

    private int getSummaryDisplayNameColumnIndex() {
        if (mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            return SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
        } else {
            return SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
        }
    }

    /** {@inheritDoc} */
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            // TODO a better way of identifying the button
            case android.R.id.button1: {
                final int position = (Integer)v.getTag();
                Cursor c = mAdapter.getCursor();
                if (c != null) {
                    c.moveToPosition(position);
                    callContact(c);
                }
                break;
            }
        }
    }

    private void setEmptyText() {
        if (mMode == MODE_JOIN_CONTACT || mSearchMode) {
            return;
        }

        TextView empty = (TextView) findViewById(R.id.emptyText);
        if (mDisplayOnlyPhones) {
            empty.setText(getText(R.string.noContactsWithPhoneNumbers));
        } else if (mMode == MODE_STREQUENT || mMode == MODE_STARRED) {
            empty.setText(getText(R.string.noFavoritesHelpText));
        } else if (mMode == MODE_QUERY || mMode == MODE_QUERY_PICK
                || mMode == MODE_QUERY_PICK_PHONE || mMode == MODE_QUERY_PICK_TO_VIEW
                || mMode == MODE_QUERY_PICK_TO_EDIT) {
            empty.setText(getText(R.string.noMatchingContacts));
        } else {
            boolean hasSim = ((TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE))
                    .hasIccCard();
            boolean createShortcut = Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction());
            if (isSyncActive()) {
                if (createShortcut) {
                    // Help text is the same no matter whether there is SIM or not.
                    empty.setText(getText(R.string.noContactsHelpTextWithSyncForCreateShortcut));
                } else if (hasSim) {
                    empty.setText(getText(R.string.noContactsHelpTextWithSync));
                } else {
                    empty.setText(getText(R.string.noContactsNoSimHelpTextWithSync));
                }
            } else {
                if (createShortcut) {
                    // Help text is the same no matter whether there is SIM or not.
                    empty.setText(getText(R.string.noContactsHelpTextForCreateShortcut));
                } else if (hasSim) {
                    empty.setText(getText(R.string.noContactsHelpText));
                } else {
                    empty.setText(getText(R.string.noContactsNoSimHelpText));
                }
            }
        }
    }

    private boolean isSyncActive() {
        Account[] accounts = AccountManager.get(this).getAccounts();
        if (accounts != null && accounts.length > 0) {
            IContentService contentService = ContentResolver.getContentService();
            for (Account account : accounts) {
                try {
                    if (contentService.isSyncActive(account, ContactsContract.AUTHORITY)) {
                        return true;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Could not get the sync status");
                }
            }
        }
        return false;
    }

    private void buildUserGroupUri(String group) {
        mGroupUri = Uri.withAppendedPath(Contacts.CONTENT_GROUP_URI, group);
    }

    /**
     * Sets the mode when the request is for "default"
     */
    private void setDefaultMode() {
        // Load the preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mDisplayOnlyPhones = prefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                Prefs.DISPLAY_ONLY_PHONES_DEFAULT);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPhotoLoader.stop();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mContactsPrefs.registerChangeListener(mPreferencesChangeListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterProviderStatusObserver();
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerProviderStatusObserver();
        mPhotoLoader.resume();

        Activity parent = getParent();

        // Do this before setting the filter. The filter thread relies
        // on some state that is initialized in setDefaultMode
        if (mMode == MODE_DEFAULT) {
            // If we're in default mode we need to possibly reset the mode due to a change
            // in the preferences activity while we weren't running
            setDefaultMode();
        }

        // See if we were invoked with a filter
        if (mSearchMode) {
            mSearchEditText.requestFocus();
        }

        if (!mSearchMode && !checkProviderState(mJustCreated)) {
            return;
        }

        if (mJustCreated) {
            // We need to start a query here the first time the activity is launched, as long
            // as we aren't doing a filter.
            startQuery();
        }
        mJustCreated = false;
        mSearchInitiated = false;
    }

    /**
     * Obtains the contacts provider status and configures the UI accordingly.
     *
     * @param loadData true if the method needs to start a query when the
     *            provider is in the normal state
     * @return true if the provider status is normal
     */
    private boolean checkProviderState(boolean loadData) {
        View importFailureView = findViewById(R.id.import_failure);
        if (importFailureView == null) {
            return true;
        }

        TextView messageView = (TextView) findViewById(R.id.emptyText);

        // This query can be performed on the UI thread because
        // the API explicitly allows such use.
        Cursor cursor = getContentResolver().query(ProviderStatus.CONTENT_URI, new String[] {
                ProviderStatus.STATUS, ProviderStatus.DATA1
        }, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                int status = cursor.getInt(0);
                if (status != mProviderStatus) {
                    mProviderStatus = status;
                    switch (status) {
                        case ProviderStatus.STATUS_NORMAL:
                            mAdapter.notifyDataSetInvalidated();
                            if (loadData) {
                                startQuery();
                            }
                            break;

                        case ProviderStatus.STATUS_CHANGING_LOCALE:
                            messageView.setText(R.string.locale_change_in_progress);
                            mAdapter.changeCursor(null);
                            mAdapter.notifyDataSetInvalidated();
                            break;

                        case ProviderStatus.STATUS_UPGRADING:
                            messageView.setText(R.string.upgrade_in_progress);
                            mAdapter.changeCursor(null);
                            mAdapter.notifyDataSetInvalidated();
                            break;

                        case ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY:
                            long size = cursor.getLong(1);
                            String message = getResources().getString(
                                    R.string.upgrade_out_of_memory, new Object[] {size});
                            messageView.setText(message);
                            configureImportFailureView(importFailureView);
                            mAdapter.changeCursor(null);
                            mAdapter.notifyDataSetInvalidated();
                            break;
                    }
                }
            }
        } finally {
            cursor.close();
        }

        importFailureView.setVisibility(
                mProviderStatus == ProviderStatus.STATUS_UPGRADE_OUT_OF_MEMORY
                        ? View.VISIBLE
                        : View.GONE);
        return mProviderStatus == ProviderStatus.STATUS_NORMAL;
    }

    private void configureImportFailureView(View importFailureView) {

        OnClickListener listener = new OnClickListener(){

            public void onClick(View v) {
                switch(v.getId()) {
                    case R.id.import_failure_uninstall_apps: {
                        startActivity(new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
                        break;
                    }
                    case R.id.import_failure_retry_upgrade: {
                        // Send a provider status update, which will trigger a retry
                        ContentValues values = new ContentValues();
                        values.put(ProviderStatus.STATUS, ProviderStatus.STATUS_UPGRADING);
                        getContentResolver().update(ProviderStatus.CONTENT_URI, values, null, null);
                        break;
                    }
                }
            }};

        Button uninstallApps = (Button) findViewById(R.id.import_failure_uninstall_apps);
        uninstallApps.setOnClickListener(listener);

        Button retryUpgrade = (Button) findViewById(R.id.import_failure_retry_upgrade);
        retryUpgrade.setOnClickListener(listener);
    }

    private String getTextFilter() {
        if (mSearchEditText != null) {
            return mSearchEditText.getText().toString();
        }
        return null;
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        if (!checkProviderState(false)) {
            return;
        }

        // The cursor was killed off in onStop(), so we need to get a new one here
        // We do not perform the query if a filter is set on the list because the
        // filter will cause the query to happen anyway
        if (TextUtils.isEmpty(getTextFilter())) {
            startQuery();
        } else {
            // Run the filtered query on the adapter
            mAdapter.onContentChanged();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle icicle) {
        super.onSaveInstanceState(icicle);
        // Save list state in the bundle so we can restore it after the QueryHandler has run
        if (mList != null) {
            icicle.putParcelable(LIST_STATE_KEY, mList.onSaveInstanceState());
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle icicle) {
        super.onRestoreInstanceState(icicle);
        // Retrieve list state. This will be applied after the QueryHandler has run
        mListState = icicle.getParcelable(LIST_STATE_KEY);
    }

    @Override
    protected void onStop() {
        super.onStop();

        mContactsPrefs.unregisterChangeListener();
        mAdapter.setSuggestionsCursor(null);
        mAdapter.changeCursor(null);

        if (mMode == MODE_QUERY) {
            // Make sure the search box is closed
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            searchManager.stopSearch();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // If Contacts was invoked by another Activity simply as a way of
        // picking a contact, don't show the options menu
        if ((mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER) {
            return false;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean defaultMode = (mMode == MODE_DEFAULT);
        menu.findItem(R.id.menu_display_groups).setVisible(defaultMode);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_display_groups: {
                final Intent intent = new Intent(this, ContactsPreferencesActivity.class);
                startActivityForResult(intent, SUBACTIVITY_DISPLAY_GROUP);
                return true;
            }
            case R.id.menu_search: {
                onSearchRequested();
                return true;
            }
            case R.id.menu_add: {
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivity(intent);
                return true;
            }
            case R.id.menu_import_export: {
                displayImportExportDialog();
                return true;
            }
            case R.id.menu_accounts: {
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    ContactsContract.AUTHORITY
                });
                startActivity(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
            return;
        }

        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0) {
                if ((mMode & MODE_MASK_PICKER) != 0) {
                    ContactsSearchManager.startSearchForResult(this, initialQuery,
                            SUBACTIVITY_FILTER);
                } else {
                    ContactsSearchManager.startSearch(this, initialQuery);
                }
            }
        }
    }

    /**
     * Performs filtering of the list based on the search query entered in the
     * search text edit.
     */
    protected void onSearchTextChanged() {
        // Set the proper empty string
        setEmptyText();

        Filter filter = mAdapter.getFilter();
        filter.filter(getTextFilter());
    }

    /**
     * Starts a new activity that will run a search query and display search results.
     */
    private void doSearch() {
        String query = getTextFilter();
        if (TextUtils.isEmpty(query)) {
            return;
        }

        Intent intent = new Intent(this, SearchResultsActivity.class);
        Intent originalIntent = getIntent();
        Bundle originalExtras = originalIntent.getExtras();
        if (originalExtras != null) {
            intent.putExtras(originalExtras);
        }

        intent.putExtra(SearchManager.QUERY, query);
        if ((mMode & MODE_MASK_PICKER) != 0) {
            intent.setAction(ACTION_SEARCH_INTERNAL);
            intent.putExtra(SHORTCUT_ACTION_KEY, mShortcutAction);
            if (mShortcutAction != null) {
                if (Intent.ACTION_CALL.equals(mShortcutAction)
                        || Intent.ACTION_SENDTO.equals(mShortcutAction)) {
                    intent.putExtra(Insert.PHONE, query);
                }
            } else {
                switch (mQueryMode) {
                    case QUERY_MODE_MAILTO:
                        intent.putExtra(Insert.EMAIL, query);
                        break;
                    case QUERY_MODE_TEL:
                        intent.putExtra(Insert.PHONE, query);
                        break;
                }
            }
            startActivityForResult(intent, SUBACTIVITY_SEARCH);
        } else {
            intent.setAction(Intent.ACTION_SEARCH);
            startActivity(intent);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.string.import_from_sim:
            case R.string.import_from_sdcard: {
                return AccountSelectionUtil.getSelectAccountDialog(this, id);
            }
            case R.id.dialog_sdcard_not_found: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.no_sdcard_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.no_sdcard_message)
                        .setPositiveButton(android.R.string.ok, null).create();
            }
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_hide_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_readonly_contact_delete_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
            case R.id.dialog_multiple_contact_delete_confirmation: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok,
                                new DeleteClickListener()).create();
            }
        }
        return super.onCreateDialog(id, bundle);
    }

    /**
     * Create a {@link Dialog} that allows the user to pick from a bulk import
     * or bulk export task across all contacts.
     */
    private void displayImportExportDialog() {
        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(this, android.R.style.Theme_Light);
        final Resources res = dialogContext.getResources();
        final LayoutInflater dialogInflater = (LayoutInflater)dialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // Adapter that shows a list of string resources
        final ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this,
                android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_1,
                            parent, false);
                }

                final int resId = this.getItem(position);
                ((TextView)convertView).setText(resId);
                return convertView;
            }
        };

        boolean hasIccCard = false;
        for (int i = 0; i < TelephonyManager.getPhoneCount(); i++) {
            hasIccCard = TelephonyManager.getDefault().hasIccCard(i);
            if (hasIccCard) {
               break;
            }
        }
        if (hasIccCard) {
            adapter.add(R.string.import_from_sim);
        }
        if (res.getBoolean(R.bool.config_allow_import_from_sdcard)) {
            adapter.add(R.string.import_from_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_export_to_sdcard)) {
            adapter.add(R.string.export_to_sdcard);
        }
        if (res.getBoolean(R.bool.config_allow_share_visible_contacts)) {
            adapter.add(R.string.share_visible_contacts);
        }

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                final int resId = adapter.getItem(which);
                switch (resId) {
                    case R.string.import_from_sim:
                    case R.string.import_from_sdcard: {
                        handleImportRequest(resId);
                        break;
                    }
                    case R.string.export_to_sdcard: {
                        Context context = ContactsListActivity.this;
                        Intent exportIntent = new Intent(context, ExportVCardActivity.class);
                        context.startActivity(exportIntent);
                        break;
                    }
                    case R.string.share_visible_contacts: {
                        doShareVisibleContacts();
                        break;
                    }
                    default: {
                        Log.e(TAG, "Unexpected resource: " +
                                getResources().getResourceEntryName(resId));
                    }
                }
            }
        };

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_import_export)
            .setNegativeButton(android.R.string.cancel, null)
            .setSingleChoiceItems(adapter, -1, clickListener)
            .show();
    }

    private void doShareVisibleContacts() {
        final Cursor cursor = getContentResolver().query(Contacts.CONTENT_URI,
                sLookupProjection, getContactSelection(), null, null);
        try {
            if (!cursor.moveToFirst()) {
                Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
                return;
            }

            StringBuilder uriListBuilder = new StringBuilder();
            int index = 0;
            for (;!cursor.isAfterLast(); cursor.moveToNext()) {
                if (index != 0)
                    uriListBuilder.append(':');
                uriListBuilder.append(cursor.getString(0));
                index++;
            }
            Uri uri = Uri.withAppendedPath(
                    Contacts.CONTENT_MULTI_VCARD_URI,
                    Uri.encode(uriListBuilder.toString()));

            final Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(Contacts.CONTENT_VCARD_TYPE);
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            startActivity(intent);
        } finally {
            cursor.close();
        }
    }

    private void handleImportRequest(int resId) {
        // There's three possibilities:
        // - more than one accounts -> ask the user
        // - just one account -> use the account without asking the user
        // - no account -> use phone-local storage without asking the user
        final Sources sources = Sources.getInstance(this);
        final List<Account> accountList = sources.getAccounts(true);
        final int size = accountList.size();
        if (size > 1) {
            showDialog(resId);
            return;
        }

        AccountSelectionUtil.doImport(this, resId, (size == 1 ? accountList.get(0) : null));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case SUBACTIVITY_NEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    returnPickerResult(null, data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME),
                            data.getData(), (mMode & MODE_MASK_PICKER) != 0
                            ? Intent.FLAG_GRANT_READ_URI_PERMISSION : 0);
                }
                break;

            case SUBACTIVITY_VIEW_CONTACT:
                if (resultCode == RESULT_OK) {
                    mAdapter.notifyDataSetChanged();
                }
                break;

            case SUBACTIVITY_DISPLAY_GROUP:
                // Mark as just created so we re-run the view query
                mJustCreated = true;
                break;

            case SUBACTIVITY_FILTER:
            case SUBACTIVITY_SEARCH:
                // Pass through results of filter or search UI
                if (resultCode == RESULT_OK) {
                    setResult(RESULT_OK, data);
                    finish();
                }
                break;
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
        Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, id);
        long rawContactId = ContactsUtils.queryForRawContactId(getContentResolver(), id);
        Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);

        // Setup the menu header
        menu.setHeaderTitle(cursor.getString(getSummaryDisplayNameColumnIndex()));

        // View contact details
        final Intent viewContactIntent = new Intent(Intent.ACTION_VIEW, contactUri);
        StickyTabs.setTab(viewContactIntent, getIntent());
        menu.add(0, MENU_ITEM_VIEW_CONTACT, 0, R.string.menu_viewContact)
                .setIntent(viewContactIntent);

        if (cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0) {
            // Calling contact
            menu.add(0, MENU_ITEM_CALL, 0, getString(R.string.menu_call));
            // Send SMS item
            menu.add(0, MENU_ITEM_SEND_SMS, 0, getString(R.string.menu_sendSMS));
        }

        // Star toggling
        int starState = cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX);
        if (starState == 0) {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_addStar);
        } else {
            menu.add(0, MENU_ITEM_TOGGLE_STAR, 0, R.string.menu_removeStar);
        }

        // Contact editing
        menu.add(0, MENU_ITEM_EDIT, 0, R.string.menu_editContact)
                .setIntent(new Intent(Intent.ACTION_EDIT, rawContactUri));
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
                values.put(Contacts.STARRED, cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX) == 0 ? 1 : 0);
                final Uri selectedUri = this.getContactUri(info.position);
                getContentResolver().update(selectedUri, values, null, null);
                return true;
            }

            case MENU_ITEM_CALL: {
                callContact(cursor);
                return true;
            }

            case MENU_ITEM_SEND_SMS: {
                smsContact(cursor);
                return true;
            }

            case MENU_ITEM_DELETE: {
                doContactDelete(getContactUri(info.position));
                return true;
            }
        }

        return super.onContextItemSelected(item);
    }

    /**
     * Event handler for the use case where the user starts typing without
     * bringing up the search UI first.
     */
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (!mSearchMode && (mMode & MODE_MASK_NO_FILTER) == 0 && !mSearchInitiated) {
            int unicodeChar = event.getUnicodeChar();
            if (unicodeChar != 0) {
                mSearchInitiated = true;
                startSearch(new String(new int[]{unicodeChar}, 0, 1), false, null, false);
                return true;
            }
        }
        return false;
    }

    /**
     * Event handler for search UI.
     */
    public void afterTextChanged(Editable s) {
        onSearchTextChanged();
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /**
     * Event handler for search UI.
     */
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            hideSoftKeyboard();
            if (TextUtils.isEmpty(getTextFilter())) {
                finish();
            }
            return true;
        }
        return false;
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
                if (deleteSelection()) {
                    return true;
                }
                break;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    private boolean deleteSelection() {
        if ((mMode & MODE_MASK_PICKER) != 0) {
            return false;
        }

        final int position = getListView().getSelectedItemPosition();
        if (position != ListView.INVALID_POSITION) {
            Uri contactUri = getContactUri(position);
            if (contactUri != null) {
                doContactDelete(contactUri);
                return true;
            }
        }
        return false;
    }

    /**
     * Prompt the user before deleting the given {@link Contacts} entry.
     */
    protected void doContactDelete(Uri contactUri) {
        mReadOnlySourcesCnt = 0;
        mWritableSourcesCnt = 0;
        mWritableRawContactIds.clear();

        Sources sources = Sources.getInstance(ContactsListActivity.this);
        Cursor c = getContentResolver().query(RawContacts.CONTENT_URI, RAW_CONTACTS_PROJECTION,
                RawContacts.CONTACT_ID + "=" + ContentUris.parseId(contactUri), null,
                null);
        if (c != null) {
            try {
                while (c.moveToNext()) {
                    final String accountType = c.getString(2);
                    final long rawContactId = c.getLong(0);
                    ContactsSource contactsSource = sources.getInflatedSource(accountType,
                            ContactsSource.LEVEL_SUMMARY);
                    if (contactsSource != null && contactsSource.readOnly) {
                        mReadOnlySourcesCnt += 1;
                    } else {
                        mWritableSourcesCnt += 1;
                        mWritableRawContactIds.add(rawContactId);
                    }
                }
            } finally {
                c.close();
            }
        }

        mSelectedContactUri = contactUri;
        if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt > 0) {
            showDialog(R.id.dialog_readonly_contact_delete_confirmation);
        } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
            showDialog(R.id.dialog_readonly_contact_hide_confirmation);
        } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
            showDialog(R.id.dialog_multiple_contact_delete_confirmation);
        } else {
            showDialog(R.id.dialog_delete_contact_confirmation);
        }
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public void onFocusChange(View view, boolean hasFocus) {
        if (view == getListView() && hasFocus) {
            hideSoftKeyboard();
        }
    }

    /**
     * Dismisses the soft keyboard when the list takes focus.
     */
    public boolean onTouch(View view, MotionEvent event) {
        if (view == getListView()) {
            hideSoftKeyboard();
        }
        return false;
    }

    /**
     * Dismisses the search UI along with the keyboard if the filter text is empty.
     */
    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        if (mSearchMode && keyCode == KeyEvent.KEYCODE_BACK && TextUtils.isEmpty(getTextFilter())) {
            hideSoftKeyboard();
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        hideSoftKeyboard();

        if (mSearchMode && mAdapter.isSearchAllContactsItemPosition(position)) {
            doSearch();
        } else if (mMode == MODE_INSERT_OR_EDIT_CONTACT || mMode == MODE_QUERY_PICK_TO_EDIT) {
            Intent intent;
            if (position == 0 && !mSearchMode && mMode != MODE_QUERY_PICK_TO_EDIT) {
                intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            } else {
                intent = new Intent(Intent.ACTION_EDIT, getSelectedUri(position));
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            Bundle extras = getIntent().getExtras();
            if (extras != null) {
                intent.putExtras(extras);
            }
            intent.putExtra(KEY_PICKER_MODE, (mMode & MODE_MASK_PICKER) == MODE_MASK_PICKER);

            startActivity(intent);
            finish();
        } else if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW
                && position == 0) {
            Intent newContact = new Intent(Intents.Insert.ACTION, Contacts.CONTENT_URI);
            startActivityForResult(newContact, SUBACTIVITY_NEW_CONTACT);
        } else if (mMode == MODE_JOIN_CONTACT && id == JOIN_MODE_SHOW_ALL_CONTACTS_ID) {
            mJoinModeShowAllContacts = false;
            startQuery();
        } else if (id > 0) {
            final Uri uri = getSelectedUri(position);
            if ((mMode & MODE_MASK_PICKER) == 0) {
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                StickyTabs.setTab(intent, getIntent());
                startActivityForResult(intent, SUBACTIVITY_VIEW_CONTACT);
            } else if (mMode == MODE_JOIN_CONTACT) {
                returnPickerResult(null, null, uri, 0);
            } else if (mMode == MODE_QUERY_PICK_TO_VIEW) {
                // Started with query that should launch to view contact
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                finish();
            } else if (mMode == MODE_PICK_PHONE || mMode == MODE_QUERY_PICK_PHONE) {
                Cursor c = (Cursor) mAdapter.getItem(position);
                returnPickerResult(c, c.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX), uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if ((mMode & MODE_MASK_PICKER) != 0) {
                Cursor c = (Cursor) mAdapter.getItem(position);
                returnPickerResult(c, c.getString(getSummaryDisplayNameColumnIndex()), uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else if (mMode == MODE_PICK_POSTAL
                    || mMode == MODE_LEGACY_PICK_POSTAL
                    || mMode == MODE_LEGACY_PICK_PHONE) {
                returnPickerResult(null, null, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        } else {
            signalError();
        }
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager)
                getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mList.getWindowToken(), 0);
    }

    /**
     * @param selectedUri In most cases, this should be a lookup {@link Uri}, possibly
     *            generated through {@link Contacts#getLookupUri(long, String)}.
     */
    private void returnPickerResult(Cursor c, String name, Uri selectedUri, int uriPerms) {
        final Intent intent = new Intent();

        if (mShortcutAction != null) {
            Intent shortcutIntent;
            if (Intent.ACTION_VIEW.equals(mShortcutAction)) {
                // This is a simple shortcut to view a contact.
                shortcutIntent = new Intent(ContactsContract.QuickContact.ACTION_QUICK_CONTACT);
                shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

                shortcutIntent.setData(selectedUri);
                shortcutIntent.putExtra(ContactsContract.QuickContact.EXTRA_MODE,
                        ContactsContract.QuickContact.MODE_LARGE);
                shortcutIntent.putExtra(ContactsContract.QuickContact.EXTRA_EXCLUDE_MIMES,
                        (String[]) null);

                final Bitmap icon = framePhoto(loadContactPhoto(selectedUri, null));
                if (icon != null) {
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, scaleToAppIconSize(icon));
                } else {
                    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                            Intent.ShortcutIconResource.fromContext(this,
                                    R.drawable.ic_launcher_shortcut_contact));
                }
            } else {
                // This is a direct dial or sms shortcut.
                String number = c.getString(PHONE_NUMBER_COLUMN_INDEX);
                int type = c.getInt(PHONE_TYPE_COLUMN_INDEX);
                String scheme;
                int resid;
                if (Intent.ACTION_CALL.equals(mShortcutAction)) {
                    scheme = Constants.SCHEME_TEL;
                    resid = R.drawable.badge_action_call;
                } else {
                    scheme = Constants.SCHEME_SMSTO;
                    resid = R.drawable.badge_action_sms;
                }

                // Make the URI a direct tel: URI so that it will always continue to work
                Uri phoneUri = Uri.fromParts(scheme, number, null);
                shortcutIntent = new Intent(mShortcutAction, phoneUri);

                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON,
                        generatePhoneNumberIcon(selectedUri, type, resid));
            }
            shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            setResult(RESULT_OK, intent);
        } else {
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
            intent.addFlags(uriPerms);
            setResult(RESULT_OK, intent.setData(selectedUri));
        }
        finish();
    }

    private Bitmap framePhoto(Bitmap photo) {
        final Resources r = getResources();
        final Drawable frame = r.getDrawable(com.android.internal.R.drawable.quickcontact_badge);

        final int width = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_width);
        final int height = r.getDimensionPixelSize(R.dimen.contact_shortcut_frame_height);

        frame.setBounds(0, 0, width, height);

        final Rect padding = new Rect();
        frame.getPadding(padding);

        final Rect source = new Rect(0, 0, photo.getWidth(), photo.getHeight());
        final Rect destination = new Rect(padding.left, padding.top,
                width - padding.right, height - padding.bottom);

        final int d = Math.max(width, height);
        final Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(b);

        c.translate((d - width) / 2.0f, (d - height) / 2.0f);
        frame.draw(c);
        c.drawBitmap(photo, source, destination, new Paint(Paint.FILTER_BITMAP_FLAG));

        return b;
    }

    /**
     * Generates a phone number shortcut icon. Adds an overlay describing the type of the phone
     * number, and if there is a photo also adds the call action icon.
     *
     * @param lookupUri The person the phone number belongs to
     * @param type The type of the phone number
     * @param actionResId The ID for the action resource
     * @return The bitmap for the icon
     */
    private Bitmap generatePhoneNumberIcon(Uri lookupUri, int type, int actionResId) {
        final Resources r = getResources();
        boolean drawPhoneOverlay = true;
        final float scaleDensity = getResources().getDisplayMetrics().scaledDensity;

        Bitmap photo = loadContactPhoto(lookupUri, null);
        if (photo == null) {
            // If there isn't a photo use the generic phone action icon instead
            Bitmap phoneIcon = getPhoneActionIcon(r, actionResId);
            if (phoneIcon != null) {
                photo = phoneIcon;
                drawPhoneOverlay = false;
            } else {
                return null;
            }
        }

        // Setup the drawing classes
        Bitmap icon = createShortcutBitmap();
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0,0, photo.getWidth(),photo.getHeight());
        Rect dst = new Rect(0,0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        // Create an overlay for the phone number type
        String overlay = null;
        switch (type) {
            case Phone.TYPE_HOME:
                overlay = getString(R.string.type_short_home);
                break;

            case Phone.TYPE_MOBILE:
                overlay = getString(R.string.type_short_mobile);
                break;

            case Phone.TYPE_WORK:
                overlay = getString(R.string.type_short_work);
                break;

            case Phone.TYPE_PAGER:
                overlay = getString(R.string.type_short_pager);
                break;

            case Phone.TYPE_OTHER:
                overlay = getString(R.string.type_short_other);
                break;
        }
        if (overlay != null) {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            textPaint.setTextSize(20.0f * scaleDensity);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setColor(r.getColor(R.color.textColorIconOverlay));
            textPaint.setShadowLayer(3f, 1, 1, r.getColor(R.color.textColorIconOverlayShadow));
            canvas.drawText(overlay, 2 * scaleDensity, 16 * scaleDensity, textPaint);
        }

        // Draw the phone action icon as an overlay
        if (ENABLE_ACTION_ICON_OVERLAYS && drawPhoneOverlay) {
            Bitmap phoneIcon = getPhoneActionIcon(r, actionResId);
            if (phoneIcon != null) {
                src.set(0, 0, phoneIcon.getWidth(), phoneIcon.getHeight());
                int iconWidth = icon.getWidth();
                dst.set(iconWidth - ((int) (20 * scaleDensity)), -1,
                        iconWidth, ((int) (19 * scaleDensity)));
                canvas.drawBitmap(phoneIcon, src, dst, photoPaint);
            }
        }

        return icon;
    }

    private Bitmap scaleToAppIconSize(Bitmap photo) {
        // Setup the drawing classes
        Bitmap icon = createShortcutBitmap();
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect src = new Rect(0,0, photo.getWidth(),photo.getHeight());
        Rect dst = new Rect(0,0, mIconSize, mIconSize);
        canvas.drawBitmap(photo, src, dst, photoPaint);

        return icon;
    }

    private Bitmap createShortcutBitmap() {
        return Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
    }

    /**
     * Returns the icon for the phone call action.
     *
     * @param r The resources to load the icon from
     * @param resId The resource ID to load
     * @return the icon for the phone call action
     */
    private Bitmap getPhoneActionIcon(Resources r, int resId) {
        Drawable phoneIcon = r.getDrawable(resId);
        if (phoneIcon instanceof BitmapDrawable) {
            BitmapDrawable bd = (BitmapDrawable) phoneIcon;
            return bd.getBitmap();
        } else {
            return null;
        }
    }

    private Uri getUriToQuery() {
        switch(mMode) {
            case MODE_JOIN_CONTACT:
                return getJoinSuggestionsUri(null);
            case MODE_FREQUENT:
            case MODE_STARRED:
                return Contacts.CONTENT_URI;

            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_INSERT_OR_EDIT_CONTACT:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:{
                return CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS;
            }
            case MODE_STREQUENT: {
                return Contacts.CONTENT_STREQUENT_URI;
            }
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return People.CONTENT_URI;
            }
            case MODE_PICK_PHONE: {
                return buildSectionIndexerUri(Phone.CONTENT_URI);
            }
            case MODE_LEGACY_PICK_PHONE: {
                return Phones.CONTENT_URI;
            }
            case MODE_PICK_POSTAL: {
                return buildSectionIndexerUri(StructuredPostal.CONTENT_URI);
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return ContactMethods.CONTENT_URI;
            }
            case MODE_QUERY_PICK_TO_VIEW: {
                if (mQueryMode == QUERY_MODE_MAILTO) {
                    return Uri.withAppendedPath(Email.CONTENT_FILTER_URI,
                            Uri.encode(mInitialFilter));
                } else if (mQueryMode == QUERY_MODE_TEL) {
                    return Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,
                            Uri.encode(mInitialFilter));
                }
                return CONTACTS_CONTENT_URI_WITH_LETTER_COUNTS;
            }
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_TO_EDIT: {
                return getContactFilterUri(mInitialFilter);
            }
            case MODE_QUERY_PICK_PHONE: {
                return Uri.withAppendedPath(Phone.CONTENT_FILTER_URI,
                        Uri.encode(mInitialFilter));
            }
            case MODE_GROUP: {
                return mGroupUri;
            }
            default: {
                throw new IllegalStateException("Can't generate URI: Unsupported Mode.");
            }
        }
    }

    /**
     * Build the {@link Contacts#CONTENT_LOOKUP_URI} for the given
     * {@link ListView} position, using {@link #mAdapter}.
     */
    private Uri getContactUri(int position) {
        if (position == ListView.INVALID_POSITION) {
            throw new IllegalArgumentException("Position not in list bounds");
        }

        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        if (cursor == null) {
            return null;
        }

        switch(mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                final long personId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                return ContentUris.withAppendedId(People.CONTENT_URI, personId);
            }

            default: {
                // Build and return soft, lookup reference
                final long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                final String lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                return Contacts.getLookupUri(contactId, lookupKey);
            }
        }
    }

    /**
     * Build the {@link Uri} for the given {@link ListView} position, which can
     * be used as result when in {@link #MODE_MASK_PICKER} mode.
     */
    private Uri getSelectedUri(int position) {
        if (position == ListView.INVALID_POSITION) {
            throw new IllegalArgumentException("Position not in list bounds");
        }

        final long id = mAdapter.getItemId(position);
        switch(mMode) {
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return ContentUris.withAppendedId(People.CONTENT_URI, id);
            }
            case MODE_PICK_PHONE:
            case MODE_QUERY_PICK_PHONE: {
                return ContentUris.withAppendedId(Data.CONTENT_URI, id);
            }
            case MODE_LEGACY_PICK_PHONE: {
                return ContentUris.withAppendedId(Phones.CONTENT_URI, id);
            }
            case MODE_PICK_POSTAL: {
                return ContentUris.withAppendedId(Data.CONTENT_URI, id);
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return ContentUris.withAppendedId(ContactMethods.CONTENT_URI, id);
            }
            default: {
                return getContactUri(position);
            }
        }
    }

    String[] getProjectionForQuery() {
        switch(mMode) {
            case MODE_JOIN_CONTACT:
            case MODE_STREQUENT:
            case MODE_FREQUENT:
            case MODE_STARRED:
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_INSERT_OR_EDIT_CONTACT:
            case MODE_GROUP:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT: {
                return mSearchMode
                        ? CONTACTS_SUMMARY_FILTER_PROJECTION
                        : CONTACTS_SUMMARY_PROJECTION;
            }
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_TO_EDIT: {
                return CONTACTS_SUMMARY_FILTER_PROJECTION;
            }
            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return LEGACY_PEOPLE_PROJECTION ;
            }
            case MODE_QUERY_PICK_PHONE:
            case MODE_PICK_PHONE: {
                return PHONES_PROJECTION;
            }
            case MODE_LEGACY_PICK_PHONE: {
                return LEGACY_PHONES_PROJECTION;
            }
            case MODE_PICK_POSTAL: {
                return POSTALS_PROJECTION;
            }
            case MODE_LEGACY_PICK_POSTAL: {
                return LEGACY_POSTALS_PROJECTION;
            }
            case MODE_QUERY_PICK_TO_VIEW: {
                if (mQueryMode == QUERY_MODE_MAILTO) {
                    return CONTACTS_SUMMARY_PROJECTION_FROM_EMAIL;
                } else if (mQueryMode == QUERY_MODE_TEL) {
                    return PHONES_PROJECTION;
                }
                break;
            }
        }

        // Default to normal aggregate projection
        return CONTACTS_SUMMARY_PROJECTION;
    }

    private Bitmap loadContactPhoto(Uri selectedUri, BitmapFactory.Options options) {
        Uri contactUri = null;
        if (Contacts.CONTENT_ITEM_TYPE.equals(getContentResolver().getType(selectedUri))) {
            // TODO we should have a "photo" directory under the lookup URI itself
            contactUri = Contacts.lookupContact(getContentResolver(), selectedUri);
        } else {

            Cursor cursor = getContentResolver().query(selectedUri,
                    new String[] { Data.CONTACT_ID }, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    final long contactId = cursor.getLong(0);
                    contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        Cursor cursor = null;
        Bitmap bm = null;

        try {
            Uri photoUri = Uri.withAppendedPath(contactUri, Contacts.Photo.CONTENT_DIRECTORY);
            cursor = getContentResolver().query(photoUri, new String[] {Photo.PHOTO},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                bm = ContactsUtils.loadContactPhoto(cursor, 0, options);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (bm == null) {
            final int[] fallbacks = {
                R.drawable.ic_contact_picture,
                R.drawable.ic_contact_picture_2,
                R.drawable.ic_contact_picture_3
            };
            bm = BitmapFactory.decodeResource(getResources(),
                    fallbacks[new Random().nextInt(fallbacks.length)]);
        }

        return bm;
    }

    /**
     * Return the selection arguments for a default query based on the
     * {@link #mDisplayOnlyPhones} flag.
     */
    private String getContactSelection() {
        if (mDisplayOnlyPhones) {
            return CLAUSE_ONLY_VISIBLE + " AND " + CLAUSE_ONLY_PHONES;
        } else {
            return CLAUSE_ONLY_VISIBLE;
        }
    }

    private Uri getContactFilterUri(String filter) {
        Uri baseUri;
        if (!TextUtils.isEmpty(filter)) {
            baseUri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(filter));
        } else {
            baseUri = Contacts.CONTENT_URI;
        }

        if (mAdapter.getDisplaySectionHeadersEnabled()) {
            return buildSectionIndexerUri(baseUri);
        } else {
            return baseUri;
        }
    }

    private Uri getPeopleFilterUri(String filter) {
        if (!TextUtils.isEmpty(filter)) {
            return Uri.withAppendedPath(People.CONTENT_FILTER_URI, Uri.encode(filter));
        } else {
            return People.CONTENT_URI;
        }
    }

    private static Uri buildSectionIndexerUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactCounts.ADDRESS_BOOK_INDEX_EXTRAS, "true").build();
    }

    private Uri getJoinSuggestionsUri(String filter) {
        Builder builder = Contacts.CONTENT_URI.buildUpon();
        builder.appendEncodedPath(String.valueOf(mQueryAggregateId));
        builder.appendEncodedPath(AggregationSuggestions.CONTENT_DIRECTORY);
        if (!TextUtils.isEmpty(filter)) {
            builder.appendEncodedPath(Uri.encode(filter));
        }
        builder.appendQueryParameter("limit", String.valueOf(MAX_SUGGESTIONS));
        return builder.build();
    }

    private String getSortOrder(String[] projectionType) {
        if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY) {
            return Contacts.SORT_KEY_PRIMARY;
        } else {
            return Contacts.SORT_KEY_ALTERNATIVE;
        }
    }

    void startQuery() {
        // Set the proper empty string
        setEmptyText();

        if (mSearchResultsMode) {
            TextView foundContactsText = (TextView)findViewById(R.id.search_results_found);
            foundContactsText.setText(R.string.search_results_searching);
        }

        mAdapter.setLoading(true);

        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.setLoadingJoinSuggestions(false);

        mSortOrder = mContactsPrefs.getSortOrder();
        mDisplayOrder = mContactsPrefs.getDisplayOrder();

        // When sort order and display order contradict each other, we want to
        // highlight the part of the name used for sorting.
        mHighlightWhenScrolling = false;
        if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_PRIMARY &&
                mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_ALTERNATIVE) {
            mHighlightWhenScrolling = true;
        } else if (mSortOrder == ContactsContract.Preferences.SORT_ORDER_ALTERNATIVE &&
                mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
            mHighlightWhenScrolling = true;
        }

        String[] projection = getProjectionForQuery();
        if (mSearchMode && TextUtils.isEmpty(getTextFilter())) {
            mAdapter.changeCursor(new MatrixCursor(projection));
            return;
        }

        String callingPackage = getCallingPackage();
        Uri uri = getUriToQuery();
        if (!TextUtils.isEmpty(callingPackage)) {
            uri = uri.buildUpon()
                    .appendQueryParameter(ContactsContract.REQUESTING_PACKAGE_PARAM_KEY,
                            callingPackage)
                    .build();
        }

        // Kick off the new query
        switch (mMode) {
            case MODE_GROUP:
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, getContactSelection(),
                        null, getSortOrder(projection));
                break;

            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null,
                        People.DISPLAY_NAME);
                break;
            }
            case MODE_PICK_POSTAL:
            case MODE_QUERY:
            case MODE_QUERY_PICK:
            case MODE_QUERY_PICK_PHONE:
            case MODE_QUERY_PICK_TO_VIEW:
            case MODE_QUERY_PICK_TO_EDIT: {
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null,
                        getSortOrder(projection));
                break;
            }

            case MODE_STARRED:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, Contacts.STARRED + "=1", null,
                        getSortOrder(projection));
                break;

            case MODE_FREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection,
                        Contacts.TIMES_CONTACTED + " > 0", null,
                        Contacts.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(projection));
                break;

            case MODE_STREQUENT:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, null, null, null);
                break;

            case MODE_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, CLAUSE_ONLY_VISIBLE, null, getSortOrder(projection));
                break;

            case MODE_LEGACY_PICK_PHONE:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection, null, null, Phones.DISPLAY_NAME);
                break;

            case MODE_LEGACY_PICK_POSTAL:
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri,
                        projection,
                        ContactMethods.KIND + "=" + android.provider.Contacts.KIND_POSTAL, null,
                        ContactMethods.DISPLAY_NAME);
                break;

            case MODE_JOIN_CONTACT:
                mQueryHandler.setLoadingJoinSuggestions(true);
                mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection,
                        null, null, null);
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
        String[] projection = getProjectionForQuery();
        if (mSearchMode && TextUtils.isEmpty(getTextFilter())) {
            return new MatrixCursor(projection);
        }

        final ContentResolver resolver = getContentResolver();
        switch (mMode) {
            case MODE_DEFAULT:
            case MODE_CUSTOM:
            case MODE_PICK_CONTACT:
            case MODE_PICK_OR_CREATE_CONTACT:
            case MODE_INSERT_OR_EDIT_CONTACT: {
                return resolver.query(getContactFilterUri(filter), projection,
                        getContactSelection(), null, getSortOrder(projection));
            }

            case MODE_LEGACY_PICK_PERSON:
            case MODE_LEGACY_PICK_OR_CREATE_PERSON: {
                return resolver.query(getPeopleFilterUri(filter), projection, null, null,
                        People.DISPLAY_NAME);
            }

            case MODE_STARRED: {
                return resolver.query(getContactFilterUri(filter), projection,
                        Contacts.STARRED + "=1", null,
                        getSortOrder(projection));
            }

            case MODE_FREQUENT: {
                return resolver.query(getContactFilterUri(filter), projection,
                        Contacts.TIMES_CONTACTED + " > 0", null,
                        Contacts.TIMES_CONTACTED + " DESC, "
                        + getSortOrder(projection));
            }

            case MODE_STREQUENT: {
                Uri uri;
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Contacts.CONTENT_STREQUENT_FILTER_URI,
                            Uri.encode(filter));
                } else {
                    uri = Contacts.CONTENT_STREQUENT_URI;
                }
                return resolver.query(uri, projection, null, null, null);
            }

            case MODE_PICK_PHONE: {
                Uri uri = getUriToQuery();
                if (!TextUtils.isEmpty(filter)) {
                    uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(filter));
                }
                return resolver.query(uri, projection, CLAUSE_ONLY_VISIBLE, null,
                        getSortOrder(projection));
            }

            case MODE_LEGACY_PICK_PHONE: {
                //TODO: Support filtering here (bug 2092503)
                break;
            }

            case MODE_JOIN_CONTACT: {

                // We are on a background thread. Run queries one after the other synchronously
                Cursor cursor = resolver.query(getJoinSuggestionsUri(filter), projection, null,
                        null, null);
                mAdapter.setSuggestionsCursor(cursor);
                mJoinModeShowAllContacts = false;
                return resolver.query(getContactFilterUri(filter), projection,
                        Contacts._ID + " != " + mQueryAggregateId + " AND " + CLAUSE_ONLY_VISIBLE,
                        null, getSortOrder(projection));
            }
        }
        throw new UnsupportedOperationException("filtering not allowed in mode " + mMode);
    }

    private Cursor getShowAllContactsLabelCursor(String[] projection) {
        MatrixCursor matrixCursor = new MatrixCursor(projection);
        Object[] row = new Object[projection.length];
        // The only columns we care about is the id
        row[SUMMARY_ID_COLUMN_INDEX] = JOIN_MODE_SHOW_ALL_CONTACTS_ID;
        matrixCursor.addRow(row);
        return matrixCursor;
    }

    /**
     * Calls the currently selected list item.
     * @return true if the call was initiated, false otherwise
     */
    boolean callSelection() {
        ListView list = getListView();
        if (list.hasFocus()) {
            Cursor cursor = (Cursor) list.getSelectedItem();
            return callContact(cursor);
        }
        return false;
    }

    boolean callContact(Cursor cursor) {
        return callOrSmsContact(cursor, false /*call*/);
    }

    boolean smsContact(Cursor cursor) {
        return callOrSmsContact(cursor, true /*sms*/);
    }

    /**
     * Calls the contact which the cursor is point to.
     * @return true if the call was initiated, false otherwise
     */
    boolean callOrSmsContact(Cursor cursor, boolean sendSms) {
        if (cursor == null) {
            return false;
        }

        switch (mMode) {
            case MODE_PICK_PHONE:
            case MODE_LEGACY_PICK_PHONE:
            case MODE_QUERY_PICK_PHONE: {
                String phone = cursor.getString(PHONE_NUMBER_COLUMN_INDEX);
                if (sendSms) {
                    ContactsUtils.initiateSms(this, phone);
                } else {
                    ContactsUtils.initiateCall(this, phone);
                }
                return true;
            }

            case MODE_PICK_POSTAL:
            case MODE_LEGACY_PICK_POSTAL: {
                return false;
            }

            default: {

                boolean hasPhone = cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;
                if (!hasPhone) {
                    // There is no phone number.
                    signalError();
                    return false;
                }

                String phone = null;
                Cursor phonesCursor = null;
                phonesCursor = queryPhoneNumbers(cursor.getLong(SUMMARY_ID_COLUMN_INDEX));
                if (phonesCursor == null || phonesCursor.getCount() == 0) {
                    // No valid number
                    signalError();
                    return false;
                } else if (phonesCursor.getCount() == 1) {
                    // only one number, call it.
                    phone = phonesCursor.getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                } else {
                    phonesCursor.moveToPosition(-1);
                    while (phonesCursor.moveToNext()) {
                        if (phonesCursor.getInt(phonesCursor.
                                getColumnIndex(Phone.IS_SUPER_PRIMARY)) != 0) {
                            // Found super primary, call it.
                            phone = phonesCursor.
                            getString(phonesCursor.getColumnIndex(Phone.NUMBER));
                            break;
                        }
                    }
                }

                if (phone == null) {
                    // Display dialog to choose a number to call.
                    PhoneDisambigDialog phoneDialog = new PhoneDisambigDialog(
                            this, phonesCursor, sendSms, StickyTabs.getTab(getIntent()));
                    phoneDialog.show();
                } else {
                    if (sendSms) {
                        ContactsUtils.initiateSms(this, phone);
                    } else {
                        StickyTabs.saveTab(this, getIntent());
                        ContactsUtils.initiateCall(this, phone);
                    }
                }
            }
        }
        return true;
    }

    private Cursor queryPhoneNumbers(long contactId) {
        Uri baseUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, Contacts.Data.CONTENT_DIRECTORY);

        Cursor c = getContentResolver().query(dataUri,
                new String[] {Phone._ID, Phone.NUMBER, Phone.IS_SUPER_PRIMARY,
                        RawContacts.ACCOUNT_TYPE, Phone.TYPE, Phone.LABEL},
                Data.MIMETYPE + "=?", new String[] {Phone.CONTENT_ITEM_TYPE}, null);
        if (c != null) {
            if (c.moveToFirst()) {
                return c;
            }
            c.close();
        }
        return null;
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    protected String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getString(zeroResourceId);
        } else {
            String format = getResources().getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
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

    private static class QueryHandler extends AsyncQueryHandler {
        protected final WeakReference<ContactsListActivity> mActivity;
        protected boolean mLoadingJoinSuggestions = false;

        public QueryHandler(Context context) {
            super(context.getContentResolver());
            mActivity = new WeakReference<ContactsListActivity>((ContactsListActivity) context);
        }

        public void setLoadingJoinSuggestions(boolean flag) {
            mLoadingJoinSuggestions = flag;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            final ContactsListActivity activity = mActivity.get();
            if (activity != null && !activity.isFinishing()) {

                // Whenever we get a suggestions cursor, we need to immediately kick off
                // another query for the complete list of contacts
                if (cursor != null && mLoadingJoinSuggestions) {
                    mLoadingJoinSuggestions = false;
                    if (cursor.getCount() > 0) {
                        activity.mAdapter.setSuggestionsCursor(cursor);
                    } else {
                        cursor.close();
                        activity.mAdapter.setSuggestionsCursor(null);
                    }

                    if (activity.mAdapter.mSuggestionsCursorCount == 0
                            || !activity.mJoinModeShowAllContacts) {
                        startQuery(QUERY_TOKEN, null, activity.getContactFilterUri(
                                        activity.getTextFilter()),
                                CONTACTS_SUMMARY_PROJECTION,
                                Contacts._ID + " != " + activity.mQueryAggregateId
                                        + " AND " + CLAUSE_ONLY_VISIBLE, null,
                                activity.getSortOrder(CONTACTS_SUMMARY_PROJECTION));
                        return;
                    }

                    cursor = activity.getShowAllContactsLabelCursor(CONTACTS_SUMMARY_PROJECTION);
                }

                activity.mAdapter.changeCursor(cursor);

                // Now that the cursor is populated again, it's possible to restore the list state
                if (activity.mListState != null) {
                    activity.mList.onRestoreInstanceState(activity.mListState);
                    activity.mListState = null;
                }
            } else {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    final static class ContactListItemCache {
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer dataBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer highlightedTextBuffer = new CharArrayBuffer(128);
        public TextWithHighlighting textWithHighlighting;
        public CharArrayBuffer phoneticNameBuffer = new CharArrayBuffer(128);
    }

    final static class PinnedHeaderCache {
        public TextView titleView;
        public ColorStateList textColor;
        public Drawable background;
    }

    private final class ContactItemListAdapter extends CursorAdapter
            implements SectionIndexer, OnScrollListener, PinnedHeaderListView.PinnedHeaderAdapter {
        private SectionIndexer mIndexer;
        private boolean mLoading = true;
        private CharSequence mUnknownNameText;
        private boolean mDisplayPhotos = false;
        private boolean mDisplayCallButton = false;
        private boolean mDisplayAdditionalData = true;
        private int mFrequentSeparatorPos = ListView.INVALID_POSITION;
        private boolean mDisplaySectionHeaders = true;
        private Cursor mSuggestionsCursor;
        private int mSuggestionsCursorCount;

        public ContactItemListAdapter(Context context) {
            super(context, null, false);

            mUnknownNameText = context.getText(android.R.string.unknownName);
            switch (mMode) {
                case MODE_LEGACY_PICK_POSTAL:
                case MODE_PICK_POSTAL:
                case MODE_LEGACY_PICK_PHONE:
                case MODE_PICK_PHONE:
                case MODE_STREQUENT:
                case MODE_FREQUENT:
                    mDisplaySectionHeaders = false;
                    break;
            }

            if (mSearchMode) {
                mDisplaySectionHeaders = false;
            }

            // Do not display the second line of text if in a specific SEARCH query mode, usually for
            // matching a specific E-mail or phone number. Any contact details
            // shown would be identical, and columns might not even be present
            // in the returned cursor.
            if (mMode != MODE_QUERY_PICK_PHONE && mQueryMode != QUERY_MODE_NONE) {
                mDisplayAdditionalData = false;
            }

            if ((mMode & MODE_MASK_NO_DATA) == MODE_MASK_NO_DATA) {
                mDisplayAdditionalData = false;
            }

            if ((mMode & MODE_MASK_SHOW_CALL_BUTTON) == MODE_MASK_SHOW_CALL_BUTTON) {
                mDisplayCallButton = true;
            }

            if ((mMode & MODE_MASK_SHOW_PHOTOS) == MODE_MASK_SHOW_PHOTOS) {
                mDisplayPhotos = true;
            }
        }

        public boolean getDisplaySectionHeadersEnabled() {
            return mDisplaySectionHeaders;
        }

        public void setSuggestionsCursor(Cursor cursor) {
            if (mSuggestionsCursor != null) {
                mSuggestionsCursor.close();
            }
            mSuggestionsCursor = cursor;
            mSuggestionsCursorCount = cursor == null ? 0 : cursor.getCount();
        }

        /**
         * Callback on the UI thread when the content observer on the backing cursor fires.
         * Instead of calling requery we need to do an async query so that the requery doesn't
         * block the UI thread for a long time.
         */
        @Override
        protected void onContentChanged() {
            CharSequence constraint = getTextFilter();
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
            if (mProviderStatus != ProviderStatus.STATUS_NORMAL) {
                return true;
            }

            if (mSearchMode) {
                return TextUtils.isEmpty(getTextFilter());
            } else if ((mMode & MODE_MASK_CREATE_NEW) == MODE_MASK_CREATE_NEW) {
                // This mode mask adds a header and we always want it to show up, even
                // if the list is empty, so always claim the list is not empty.
                return false;
            } else {
                if (mCursor == null || mLoading) {
                    // We don't want the empty state to show when loading.
                    return false;
                } else {
                    return super.isEmpty();
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0 && (mShowNumberOfContacts || (mMode & MODE_MASK_CREATE_NEW) != 0)) {
                return IGNORE_ITEM_VIEW_TYPE;
            }

            if (isShowAllContactsItemPosition(position)) {
                return IGNORE_ITEM_VIEW_TYPE;
            }

            if (isSearchAllContactsItemPosition(position)) {
                return IGNORE_ITEM_VIEW_TYPE;
            }

            if (getSeparatorId(position) != 0) {
                // We don't want the separator view to be recycled.
                return IGNORE_ITEM_VIEW_TYPE;
            }

            return super.getItemViewType(position);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException(
                        "this should only be called when the cursor is valid");
            }

            // handle the total contacts item
            if (position == 0 && mShowNumberOfContacts) {
                return getTotalContactCountView(parent);
            }

            if (position == 0 && (mMode & MODE_MASK_CREATE_NEW) != 0) {
                // Add the header for creating a new contact
                return getLayoutInflater().inflate(R.layout.create_new_contact, parent, false);
            }

            if (isShowAllContactsItemPosition(position)) {
                return getLayoutInflater().
                        inflate(R.layout.contacts_list_show_all_item, parent, false);
            }

            if (isSearchAllContactsItemPosition(position)) {
                return getLayoutInflater().
                        inflate(R.layout.contacts_list_search_all_item, parent, false);
            }

            // Handle the separator specially
            int separatorId = getSeparatorId(position);
            if (separatorId != 0) {
                TextView view = (TextView) getLayoutInflater().
                        inflate(R.layout.list_separator, parent, false);
                view.setText(separatorId);
                return view;
            }

            boolean showingSuggestion;
            Cursor cursor;
            if (mSuggestionsCursorCount != 0 && position < mSuggestionsCursorCount + 2) {
                showingSuggestion = true;
                cursor = mSuggestionsCursor;
            } else {
                showingSuggestion = false;
                cursor = mCursor;
            }

            int realPosition = getRealPosition(position);
            if (!cursor.moveToPosition(realPosition)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }

            boolean newView;
            View v;
            if (convertView == null || convertView.getTag() == null) {
                newView = true;
                v = newView(mContext, cursor, parent);
            } else {
                newView = false;
                v = convertView;
            }
            bindView(v, mContext, cursor);
            bindSectionHeader(v, realPosition, mDisplaySectionHeaders && !showingSuggestion);
            return v;
        }

        private View getTotalContactCountView(ViewGroup parent) {
            final LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.total_contacts, parent, false);

            TextView totalContacts = (TextView) view.findViewById(R.id.totalContactsText);

            String text;
            int count = getRealCount();

            if (mSearchMode && !TextUtils.isEmpty(getTextFilter())) {
                text = getQuantityText(count, R.string.listFoundAllContactsZero,
                        R.plurals.searchFoundContacts);
            } else {
                if (mDisplayOnlyPhones) {
                    text = getQuantityText(count, R.string.listTotalPhoneContactsZero,
                            R.plurals.listTotalPhoneContacts);
                } else {
                    text = getQuantityText(count, R.string.listTotalAllContactsZero,
                            R.plurals.listTotalAllContacts);
                }
            }
            totalContacts.setText(text);
            return view;
        }

        private boolean isShowAllContactsItemPosition(int position) {
            return mMode == MODE_JOIN_CONTACT && mJoinModeShowAllContacts
                    && mSuggestionsCursorCount != 0 && position == mSuggestionsCursorCount + 2;
        }

        private boolean isSearchAllContactsItemPosition(int position) {
            return mSearchMode && position == getCount() - 1;
        }

        private int getSeparatorId(int position) {
            int separatorId = 0;
            if (position == mFrequentSeparatorPos) {
                separatorId = R.string.favoritesFrquentSeparator;
            }
            if (mSuggestionsCursorCount != 0) {
                if (position == 0) {
                    separatorId = R.string.separatorJoinAggregateSuggestions;
                } else if (position == mSuggestionsCursorCount + 1) {
                    separatorId = R.string.separatorJoinAggregateAll;
                }
            }
            return separatorId;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final ContactListItemView view = new ContactListItemView(context, null);
            view.setOnCallButtonClickListener(ContactsListActivity.this);
            view.setTag(new ContactListItemCache());
            return view;
        }

        @Override
        public void bindView(View itemView, Context context, Cursor cursor) {
            final ContactListItemView view = (ContactListItemView)itemView;
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();

            int typeColumnIndex;
            int dataColumnIndex;
            int labelColumnIndex;
            int defaultType;
            int nameColumnIndex;
            int phoneticNameColumnIndex;
            boolean displayAdditionalData = mDisplayAdditionalData;
            boolean highlightingEnabled = false;
            switch(mMode) {
                case MODE_PICK_PHONE:
                case MODE_LEGACY_PICK_PHONE:
                case MODE_QUERY_PICK_PHONE: {
                    nameColumnIndex = PHONE_DISPLAY_NAME_COLUMN_INDEX;
                    phoneticNameColumnIndex = -1;
                    dataColumnIndex = PHONE_NUMBER_COLUMN_INDEX;
                    typeColumnIndex = PHONE_TYPE_COLUMN_INDEX;
                    labelColumnIndex = PHONE_LABEL_COLUMN_INDEX;
                    defaultType = Phone.TYPE_HOME;
                    break;
                }
                case MODE_PICK_POSTAL:
                case MODE_LEGACY_PICK_POSTAL: {
                    nameColumnIndex = POSTAL_DISPLAY_NAME_COLUMN_INDEX;
                    phoneticNameColumnIndex = -1;
                    dataColumnIndex = POSTAL_ADDRESS_COLUMN_INDEX;
                    typeColumnIndex = POSTAL_TYPE_COLUMN_INDEX;
                    labelColumnIndex = POSTAL_LABEL_COLUMN_INDEX;
                    defaultType = StructuredPostal.TYPE_HOME;
                    break;
                }
                default: {
                    nameColumnIndex = getSummaryDisplayNameColumnIndex();
                    if (mMode == MODE_LEGACY_PICK_PERSON
                            || mMode == MODE_LEGACY_PICK_OR_CREATE_PERSON) {
                        phoneticNameColumnIndex = -1;
                    } else {
                        phoneticNameColumnIndex = SUMMARY_PHONETIC_NAME_COLUMN_INDEX;
                    }
                    dataColumnIndex = -1;
                    typeColumnIndex = -1;
                    labelColumnIndex = -1;
                    defaultType = Phone.TYPE_HOME;
                    displayAdditionalData = false;
                    highlightingEnabled = mHighlightWhenScrolling && mMode != MODE_STREQUENT;
                }
            }

            // Set the name
            cursor.copyStringToBuffer(nameColumnIndex, cache.nameBuffer);
            TextView nameView = view.getNameTextView();
            int size = cache.nameBuffer.sizeCopied;
            if (size != 0) {
                if (highlightingEnabled) {
                    if (cache.textWithHighlighting == null) {
                        cache.textWithHighlighting =
                                mHighlightingAnimation.createTextWithHighlighting();
                    }
                    buildDisplayNameWithHighlighting(nameView, cursor, cache.nameBuffer,
                            cache.highlightedTextBuffer, cache.textWithHighlighting);
                } else {
                    nameView.setText(cache.nameBuffer.data, 0, size);
                }
            } else {
                nameView.setText(mUnknownNameText);
            }

            boolean hasPhone = cursor.getColumnCount() >= SUMMARY_HAS_PHONE_COLUMN_INDEX
                    && cursor.getInt(SUMMARY_HAS_PHONE_COLUMN_INDEX) != 0;

            // Make the call button visible if requested.
            if (mDisplayCallButton && hasPhone) {
                int pos = cursor.getPosition();
                view.showCallButton(android.R.id.button1, pos);
            } else {
                view.hideCallButton();
            }

            // Set the photo, if requested
            if (mDisplayPhotos) {
                boolean useQuickContact = (mMode & MODE_MASK_DISABLE_QUIKCCONTACT) == 0;

                long photoId = 0;
                if (!cursor.isNull(SUMMARY_PHOTO_ID_COLUMN_INDEX)) {
                    photoId = cursor.getLong(SUMMARY_PHOTO_ID_COLUMN_INDEX);
                }

                ImageView viewToUse;
                if (useQuickContact) {
                    // Build soft lookup reference
                    final long contactId = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                    final String lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                    QuickContactBadge quickContact = view.getQuickContact();
                    quickContact.assignContactUri(Contacts.getLookupUri(contactId, lookupKey));
                    quickContact.setSelectedContactsAppTabIndex(StickyTabs.getTab(getIntent()));
                    viewToUse = quickContact;
                } else {
                    viewToUse = view.getPhotoView();
                }

                final int position = cursor.getPosition();
                mPhotoLoader.loadPhoto(viewToUse, photoId);
            }

            if ((mMode & MODE_MASK_NO_PRESENCE) == 0) {
                // Set the proper icon (star or presence or nothing)
                int serverStatus;
                if (!cursor.isNull(SUMMARY_PRESENCE_STATUS_COLUMN_INDEX)) {
                    serverStatus = cursor.getInt(SUMMARY_PRESENCE_STATUS_COLUMN_INDEX);
                    Drawable icon = ContactPresenceIconUtil.getPresenceIcon(mContext, serverStatus);
                    if (icon != null) {
                        view.setPresence(icon);
                    } else {
                        view.setPresence(null);
                    }
                } else {
                    view.setPresence(null);
                }
            } else {
                view.setPresence(null);
            }

            if (mShowSearchSnippets) {
                boolean showSnippet = false;
                String snippetMimeType = cursor.getString(SUMMARY_SNIPPET_MIMETYPE_COLUMN_INDEX);
                if (Email.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
                    String email = cursor.getString(SUMMARY_SNIPPET_DATA1_COLUMN_INDEX);
                    if (!TextUtils.isEmpty(email)) {
                        view.setSnippet(email);
                        showSnippet = true;
                    }
                } else if (Organization.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
                    String company = cursor.getString(SUMMARY_SNIPPET_DATA1_COLUMN_INDEX);
                    String title = cursor.getString(SUMMARY_SNIPPET_DATA4_COLUMN_INDEX);
                    if (!TextUtils.isEmpty(company)) {
                        if (!TextUtils.isEmpty(title)) {
                            view.setSnippet(company + " / " + title);
                        } else {
                            view.setSnippet(company);
                        }
                        showSnippet = true;
                    } else if (!TextUtils.isEmpty(title)) {
                        view.setSnippet(title);
                        showSnippet = true;
                    }
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(snippetMimeType)) {
                    String nickname = cursor.getString(SUMMARY_SNIPPET_DATA1_COLUMN_INDEX);
                    if (!TextUtils.isEmpty(nickname)) {
                        view.setSnippet(nickname);
                        showSnippet = true;
                    }
                }

                if (!showSnippet) {
                    view.setSnippet(null);
                }
            }

            if (!displayAdditionalData) {
                if (phoneticNameColumnIndex != -1) {

                    // Set the name
                    cursor.copyStringToBuffer(phoneticNameColumnIndex, cache.phoneticNameBuffer);
                    int phoneticNameSize = cache.phoneticNameBuffer.sizeCopied;
                    if (phoneticNameSize != 0) {
                        view.setLabel(cache.phoneticNameBuffer.data, phoneticNameSize);
                    } else {
                        view.setLabel(null);
                    }
                } else {
                    view.setLabel(null);
                }
                return;
            }

            // Set the data.
            cursor.copyStringToBuffer(dataColumnIndex, cache.dataBuffer);

            size = cache.dataBuffer.sizeCopied;
            view.setData(cache.dataBuffer.data, size);

            // Set the label.
            if (!cursor.isNull(typeColumnIndex)) {
                final int type = cursor.getInt(typeColumnIndex);
                final String label = cursor.getString(labelColumnIndex);

                if (mMode == MODE_LEGACY_PICK_POSTAL || mMode == MODE_PICK_POSTAL) {
                    // TODO cache
                    view.setLabel(StructuredPostal.getTypeLabel(context.getResources(), type,
                            label));
                } else {
                    // TODO cache
                    view.setLabel(Phone.getTypeLabel(context.getResources(), type, label));
                }
            } else {
                view.setLabel(null);
            }
        }

        /**
         * Computes the span of the display name that has highlighted parts and configures
         * the display name text view accordingly.
         */
        private void buildDisplayNameWithHighlighting(TextView textView, Cursor cursor,
                CharArrayBuffer buffer1, CharArrayBuffer buffer2,
                TextWithHighlighting textWithHighlighting) {
            int oppositeDisplayOrderColumnIndex;
            if (mDisplayOrder == ContactsContract.Preferences.DISPLAY_ORDER_PRIMARY) {
                oppositeDisplayOrderColumnIndex = SUMMARY_DISPLAY_NAME_ALTERNATIVE_COLUMN_INDEX;
            } else {
                oppositeDisplayOrderColumnIndex = SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX;
            }
            cursor.copyStringToBuffer(oppositeDisplayOrderColumnIndex, buffer2);

            textWithHighlighting.setText(buffer1, buffer2);
            textView.setText(textWithHighlighting);
        }

        private void bindSectionHeader(View itemView, int position, boolean displaySectionHeaders) {
            final ContactListItemView view = (ContactListItemView)itemView;
            final ContactListItemCache cache = (ContactListItemCache) view.getTag();
            if (!displaySectionHeaders) {
                view.setSectionHeader(null);
                view.setDividerVisible(true);
            } else {
                final int section = getSectionForPosition(position);
                if (getPositionForSection(section) == position) {
                    String title = (String)mIndexer.getSections()[section];
                    view.setSectionHeader(title);
                } else {
                    view.setDividerVisible(false);
                    view.setSectionHeader(null);
                }

                // move the divider for the last item in a section
                if (getPositionForSection(section + 1) - 1 == position) {
                    view.setDividerVisible(false);
                } else {
                    view.setDividerVisible(true);
                }
            }
        }

        @Override
        public void changeCursor(Cursor cursor) {
            if (cursor != null) {
                setLoading(false);
            }

            // Get the split between starred and frequent items, if the mode is strequent
            mFrequentSeparatorPos = ListView.INVALID_POSITION;
            int cursorCount = 0;
            if (cursor != null && (cursorCount = cursor.getCount()) > 0
                    && mMode == MODE_STREQUENT) {
                cursor.move(-1);
                for (int i = 0; cursor.moveToNext(); i++) {
                    int starred = cursor.getInt(SUMMARY_STARRED_COLUMN_INDEX);
                    if (starred == 0) {
                        if (i > 0) {
                            // Only add the separator when there are starred items present
                            mFrequentSeparatorPos = i;
                        }
                        break;
                    }
                }
            }

            if (cursor != null && mSearchResultsMode) {
                TextView foundContactsText = (TextView)findViewById(R.id.search_results_found);
                String text = getQuantityText(cursor.getCount(), R.string.listFoundAllContactsZero,
                        R.plurals.listFoundAllContacts);
                foundContactsText.setText(text);
            }

            super.changeCursor(cursor);
            // Update the indexer for the fast scroll widget
            updateIndexer(cursor);
        }

        private void updateIndexer(Cursor cursor) {
            if (cursor == null) {
                mIndexer = null;
                return;
            }

            Bundle bundle = cursor.getExtras();
            if (bundle.containsKey(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)) {
                String sections[] =
                    bundle.getStringArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
                int counts[] = bundle.getIntArray(ContactCounts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
                mIndexer = new ContactsSectionIndexer(sections, counts);
            } else {
                mIndexer = null;
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
            if (mIndexer == null) {
                return new String[] { " " };
            } else {
                return mIndexer.getSections();
            }
        }

        public int getPositionForSection(int sectionIndex) {
            if (mIndexer == null) {
                return -1;
            }

            return mIndexer.getPositionForSection(sectionIndex);
        }

        public int getSectionForPosition(int position) {
            if (mIndexer == null) {
                return -1;
            }

            return mIndexer.getSectionForPosition(position);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return mMode != MODE_STARRED
                && !mShowNumberOfContacts
                && mSuggestionsCursorCount == 0;
        }

        @Override
        public boolean isEnabled(int position) {
            if (mShowNumberOfContacts) {
                if (position == 0) {
                    return false;
                }
                position--;
            }

            if (mSuggestionsCursorCount > 0) {
                return position != 0 && position != mSuggestionsCursorCount + 1;
            }
            return position != mFrequentSeparatorPos;
        }

        @Override
        public int getCount() {
            if (!mDataValid) {
                return 0;
            }
            int superCount = super.getCount();

            if (mShowNumberOfContacts && (mSearchMode || superCount > 0)) {
                // We don't want to count this header if it's the only thing visible, so that
                // the empty text will display.
                superCount++;
            }

            if (mSearchMode) {
                // Last element in the list is the "Find
                superCount++;
            }

            // We do not show the "Create New" button in Search mode
            if ((mMode & MODE_MASK_CREATE_NEW) != 0 && !mSearchMode) {
                // Count the "Create new contact" line
                superCount++;
            }

            if (mSuggestionsCursorCount != 0) {
                // When showing suggestions, we have 2 additional list items: the "Suggestions"
                // and "All contacts" headers.
                return mSuggestionsCursorCount + superCount + 2;
            }
            else if (mFrequentSeparatorPos != ListView.INVALID_POSITION) {
                // When showing strequent list, we have an additional list item - the separator.
                return superCount + 1;
            } else {
                return superCount;
            }
        }

        /**
         * Gets the actual count of contacts and excludes all the headers.
         */
        public int getRealCount() {
            return super.getCount();
        }

        private int getRealPosition(int pos) {
            if (mShowNumberOfContacts) {
                pos--;
            }

            if ((mMode & MODE_MASK_CREATE_NEW) != 0 && !mSearchMode) {
                return pos - 1;
            } else if (mSuggestionsCursorCount != 0) {
                // When showing suggestions, we have 2 additional list items: the "Suggestions"
                // and "All contacts" separators.
                if (pos < mSuggestionsCursorCount + 2) {
                    // We are in the upper partition (Suggestions). Adjusting for the "Suggestions"
                    // separator.
                    return pos - 1;
                } else {
                    // We are in the lower partition (All contacts). Adjusting for the size
                    // of the upper partition plus the two separators.
                    return pos - mSuggestionsCursorCount - 2;
                }
            } else if (mFrequentSeparatorPos == ListView.INVALID_POSITION) {
                // No separator, identity map
                return pos;
            } else if (pos <= mFrequentSeparatorPos) {
                // Before or at the separator, identity map
                return pos;
            } else {
                // After the separator, remove 1 from the pos to get the real underlying pos
                return pos - 1;
            }
        }

        @Override
        public Object getItem(int pos) {
            if (mSuggestionsCursorCount != 0 && pos <= mSuggestionsCursorCount) {
                mSuggestionsCursor.moveToPosition(getRealPosition(pos));
                return mSuggestionsCursor;
            } else if (isSearchAllContactsItemPosition(pos)){
                return null;
            } else {
                int realPosition = getRealPosition(pos);
                if (realPosition < 0) {
                    return null;
                }
                return super.getItem(realPosition);
            }
        }

        @Override
        public long getItemId(int pos) {
            if (mSuggestionsCursorCount != 0 && pos < mSuggestionsCursorCount + 2) {
                if (mSuggestionsCursor.moveToPosition(pos - 1)) {
                    return mSuggestionsCursor.getLong(mRowIDColumn);
                } else {
                    return 0;
                }
            } else if (isSearchAllContactsItemPosition(pos)) {
                return 0;
            }
            int realPosition = getRealPosition(pos);
            if (realPosition < 0) {
                return 0;
            }
            return super.getItemId(realPosition);
        }

        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
                int totalItemCount) {
            if (view instanceof PinnedHeaderListView) {
                ((PinnedHeaderListView)view).configureHeaderView(firstVisibleItem);
            }
        }

        public void onScrollStateChanged(AbsListView view, int scrollState) {
            if (mHighlightWhenScrolling) {
                if (scrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                    mHighlightingAnimation.startHighlighting();
                } else {
                    mHighlightingAnimation.stopHighlighting();
                }
            }

            if (scrollState == OnScrollListener.SCROLL_STATE_FLING) {
                mPhotoLoader.pause();
            } else if (mDisplayPhotos) {
                mPhotoLoader.resume();
            }
        }

        /**
         * Computes the state of the pinned header.  It can be invisible, fully
         * visible or partially pushed up out of the view.
         */
        public int getPinnedHeaderState(int position) {
            if (mIndexer == null || mCursor == null || mCursor.getCount() == 0) {
                return PINNED_HEADER_GONE;
            }

            int realPosition = getRealPosition(position);
            if (realPosition < 0) {
                return PINNED_HEADER_GONE;
            }

            // The header should get pushed up if the top item shown
            // is the last item in a section for a particular letter.
            int section = getSectionForPosition(realPosition);
            int nextSectionPosition = getPositionForSection(section + 1);
            if (nextSectionPosition != -1 && realPosition == nextSectionPosition - 1) {
                return PINNED_HEADER_PUSHED_UP;
            }

            return PINNED_HEADER_VISIBLE;
        }

        /**
         * Configures the pinned header by setting the appropriate text label
         * and also adjusting color if necessary.  The color needs to be
         * adjusted when the pinned header is being pushed up from the view.
         */
        public void configurePinnedHeader(View header, int position, int alpha) {
            PinnedHeaderCache cache = (PinnedHeaderCache)header.getTag();
            if (cache == null) {
                cache = new PinnedHeaderCache();
                cache.titleView = (TextView)header.findViewById(R.id.header_text);
                cache.textColor = cache.titleView.getTextColors();
                cache.background = header.getBackground();
                header.setTag(cache);
            }

            int realPosition = getRealPosition(position);
            int section = getSectionForPosition(realPosition);

            String title = (String)mIndexer.getSections()[section];
            cache.titleView.setText(title);

            if (alpha == 255) {
                // Opaque: use the default background, and the original text color
                header.setBackgroundDrawable(cache.background);
                cache.titleView.setTextColor(cache.textColor);
            } else {
                // Faded: use a solid color approximation of the background, and
                // a translucent text color
                header.setBackgroundColor(Color.rgb(
                        Color.red(mPinnedHeaderBackgroundColor) * alpha / 255,
                        Color.green(mPinnedHeaderBackgroundColor) * alpha / 255,
                        Color.blue(mPinnedHeaderBackgroundColor) * alpha / 255));

                int textColor = cache.textColor.getDefaultColor();
                cache.titleView.setTextColor(Color.argb(alpha,
                        Color.red(textColor), Color.green(textColor), Color.blue(textColor)));
            }
        }
    }

    private ContactsPreferences.ChangeListener mPreferencesChangeListener =
            new ContactsPreferences.ChangeListener() {
        @Override
        public void onChange() {
            // When returning from DisplayOptions, onActivityResult ensures that we reload the list,
            // so we do not have to do anything here. However, ContactsPreferences requires a change
            // listener, otherwise it would not reload its settings.
        }
    };
}
