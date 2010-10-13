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
import com.android.contacts.Collapser.Collapsible;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.ui.EditContactActivity;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DataStatus;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.android.internal.telephony.ITelephony;
import com.android.internal.widget.ContactHeaderWidget;
import com.google.android.collect.Lists;
import com.google.android.collect.Maps;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.EntityIterator;
import android.content.Intent;
import android.content.Entity.NamedContentValues;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.ContactsContract;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Intents.UI;
import android.provider.ContactsContract.SpeedDial;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Displays the details of a specific contact.
 */
public class ViewContactActivity extends Activity
        implements View.OnCreateContextMenuListener, DialogInterface.OnClickListener,
        AdapterView.OnItemClickListener, NotifyingAsyncQueryHandler.AsyncQueryListener {
    private static final String TAG = "ViewContact";

    private static final boolean SHOW_SEPARATORS = false;

    private static final int DIALOG_CONFIRM_DELETE = 1;
    private static final int DIALOG_CONFIRM_READONLY_DELETE = 2;
    private static final int DIALOG_CONFIRM_MULTIPLE_DELETE = 3;
    private static final int DIALOG_CONFIRM_READONLY_HIDE = 4;

    private static final int REQUEST_JOIN_CONTACT = 1;
    private static final int REQUEST_EDIT_CONTACT = 2;

    public static final int MENU_ITEM_MAKE_DEFAULT = 3;

    public static final int REMOVE_SPEED_DIAL = 4; //Code added for Speed Dial support

    protected Uri mLookupUri;
    private ContentResolver mResolver;
    private ViewAdapter mAdapter;
    private int mNumPhoneNumbers = 0;

    /**
     * A list of distinct contact IDs included in the current contact.
     */
    private ArrayList<Long> mRawContactIds = new ArrayList<Long>();

    /* package */ ArrayList<ViewEntry> mPhoneEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mSmsEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mEmailEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mPostalEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mImEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mNicknameEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOrganizationEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mGroupEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOtherEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ArrayList<ViewEntry>> mSections = new ArrayList<ArrayList<ViewEntry>>();

    private Cursor mCursor;

    protected ContactHeaderWidget mContactHeaderWidget;
    private NotifyingAsyncQueryHandler mHandler;

    protected LayoutInflater mInflater;

    protected int mReadOnlySourcesCnt;
    protected int mWritableSourcesCnt;
    protected boolean mAllRestricted;

    protected Uri mPrimaryPhoneUri = null;

    protected ArrayList<Long> mWritableRawContactIds = new ArrayList<Long>();

    private static final int TOKEN_ENTITIES = 0;
    private static final int TOKEN_STATUSES = 1;

    private boolean mHasEntities = false;
    private boolean mHasStatuses = false;

    private long mNameRawContactId = -1;
    private int mDisplayNameSource = DisplayNameSources.UNDEFINED;

    private ArrayList<Entity> mEntities = Lists.newArrayList();
    private HashMap<Long, DataStatus> mStatuses = Maps.newHashMap();

    /**
     * The view shown if the detail list is empty.
     * We set this to the list view when first bind the adapter, so that it won't be shown while
     * we're loading data.
     */
    private View mEmptyView;

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mCursor != null && !mCursor.isClosed()) {
                startEntityQuery();
            }
        }
    };

    public void onClick(DialogInterface dialog, int which) {
        closeCursor();
        getContentResolver().delete(mLookupUri, null, null);
        finish();
    }

    private ListView mListView;
    private boolean mShowSmsLinksForAllPhones;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();
        Uri data = intent.getData();
        String authority = data.getAuthority();
        if (ContactsContract.AUTHORITY.equals(authority)) {
            mLookupUri = data;
        } else if (android.provider.Contacts.AUTHORITY.equals(authority)) {
            final long rawContactId = ContentUris.parseId(data);
            mLookupUri = RawContacts.getContactLookupUri(getContentResolver(),
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId));

        }
        mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.contact_card_layout);

        mContactHeaderWidget = (ContactHeaderWidget) findViewById(R.id.contact_header_widget);
        mContactHeaderWidget.showStar(true);
        mContactHeaderWidget.setExcludeMimes(new String[] {
            Contacts.CONTENT_ITEM_TYPE
        });

        mHandler = new NotifyingAsyncQueryHandler(this, this);

        mListView = (ListView) findViewById(R.id.contact_data);
        mListView.setOnCreateContextMenuListener(this);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);
        mListView.setOnItemClickListener(this);
        // Don't set it to mListView yet.  We do so later when we bind the adapter.
        mEmptyView = findViewById(android.R.id.empty);

        mResolver = getContentResolver();

        // Build the list of sections. The order they're added to mSections dictates the
        // order they are displayed in the list.
        mSections.add(mPhoneEntries);
        mSections.add(mSmsEntries);
        mSections.add(mEmailEntries);
        mSections.add(mImEntries);
        mSections.add(mPostalEntries);
        mSections.add(mNicknameEntries);
        mSections.add(mOrganizationEntries);
        mSections.add(mGroupEntries);
        mSections.add(mOtherEntries);

        //TODO Read this value from a preference
        mShowSmsLinksForAllPhones = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        startEntityQuery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCursor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCursor();
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_CONFIRM_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .setCancelable(false)
                        .create();
            case DIALOG_CONFIRM_READONLY_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .setCancelable(false)
                        .create();
            case DIALOG_CONFIRM_MULTIPLE_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, this)
                        .setCancelable(false)
                        .create();
            case DIALOG_CONFIRM_READONLY_HIDE: {
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setPositiveButton(android.R.string.ok, this)
                        .create();
            }

        }
        return null;
    }

    /** {@inheritDoc} */
    public void onQueryComplete(int token, Object cookie, final Cursor cursor) {
        if (token == TOKEN_STATUSES) {
            try {
                // Read available social rows and consider binding
                readStatuses(cursor);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            considerBindData();
            return;
        }

        // One would think we could just iterate over the Cursor
        // directly here, as the result set should be small, and we've
        // already run the query in an AsyncTask, but a lot of ANRs
        // were being reported in this code nonetheless.  See bug
        // 2539603 for details.  The real bug which makes this result
        // set huge and CPU-heavy may be elsewhere.
        // TODO: if we keep this async, perhaps the entity iteration
        // should also be original AsyncTask, rather than ping-ponging
        // between threads like this.
        final ArrayList<Entity> oldEntities = mEntities;
        (new AsyncTask<Void, Void, ArrayList<Entity>>() {
            @Override
            protected ArrayList<Entity> doInBackground(Void... params) {
                ArrayList<Entity> newEntities = new ArrayList<Entity>(cursor.getCount());
                EntityIterator iterator = RawContacts.newEntityIterator(cursor);
                try {
                    while (iterator.hasNext()) {
                        Entity entity = iterator.next();
                        newEntities.add(entity);
                    }
                } finally {
                    iterator.close();
                }
                return newEntities;
            }

            @Override
            protected void onPostExecute(ArrayList<Entity> newEntities) {
                if (newEntities == null) {
                    // There was an error loading.
                    return;
                }
                synchronized (ViewContactActivity.this) {
                    if (mEntities != oldEntities) {
                        // Multiple async tasks were in flight and we
                        // lost the race.
                        return;
                    }
                    mEntities = newEntities;
                    mHasEntities = true;
                }
                considerBindData();
            }
        }).execute();
    }

    private long getRefreshedContactId() {
        Uri freshContactUri = Contacts.lookupContact(getContentResolver(), mLookupUri);
        if (freshContactUri != null) {
            return ContentUris.parseId(freshContactUri);
        }
        return -1;
    }

    /**
     * Read from the given {@link Cursor} and build a set of {@link DataStatus}
     * objects to match any valid statuses found.
     */
    private synchronized void readStatuses(Cursor cursor) {
        mStatuses.clear();

        // Walk found statuses, creating internal row for each
        while (cursor.moveToNext()) {
            final DataStatus status = new DataStatus(cursor);
            final long dataId = cursor.getLong(StatusQuery._ID);
            mStatuses.put(dataId, status);
        }

        mHasStatuses = true;
    }

    private static Cursor setupContactCursor(ContentResolver resolver, Uri lookupUri) {
        if (lookupUri == null) {
            return null;
        }
        final List<String> segments = lookupUri.getPathSegments();
        if (segments.size() != 4) {
            return null;
        }

        // Contains an Id.
        final long uriContactId = Long.parseLong(segments.get(3));
        final String uriLookupKey = Uri.encode(segments.get(2));
        final Uri dataUri = Uri.withAppendedPath(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, uriContactId),
                Contacts.Data.CONTENT_DIRECTORY);

        // This cursor has several purposes:
        // - Fetch NAME_RAW_CONTACT_ID and DISPLAY_NAME_SOURCE
        // - Fetch the lookup-key to ensure we are looking at the right record
        // - Watcher for change events
        Cursor cursor = resolver.query(dataUri,
                new String[] {
                    Contacts.NAME_RAW_CONTACT_ID,
                    Contacts.DISPLAY_NAME_SOURCE,
                    Contacts.LOOKUP_KEY
                }, null, null, null);

        if (cursor.moveToFirst()) {
            String lookupKey =
                    cursor.getString(cursor.getColumnIndex(Contacts.LOOKUP_KEY));
            if (!lookupKey.equals(uriLookupKey)) {
                // ID and lookup key do not match
                cursor.close();
                return null;
            }
            return cursor;
        } else {
            cursor.close();
            return null;
        }
    }

    private synchronized void startEntityQuery() {
        closeCursor();

        // Interprete mLookupUri
        mCursor = setupContactCursor(mResolver, mLookupUri);

        // If mCursor is null now we did not succeed in using the Uri's Id (or it didn't contain
        // a Uri). Instead we now have to use the lookup key to find the record
        if (mCursor == null) {
            mLookupUri = Contacts.getLookupUri(getContentResolver(), mLookupUri);
            mCursor = setupContactCursor(mResolver, mLookupUri);
        }

        // If mCursor is still null, we were unsuccessful in finding the record
        if (mCursor == null) {
            mNameRawContactId = -1;
            mDisplayNameSource = DisplayNameSources.UNDEFINED;
            // TODO either figure out a way to prevent a flash of black background or
            // use some other UI than a toast
            Toast.makeText(this, R.string.invalidContactMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "invalid contact uri: " + mLookupUri);
            finish();
            return;
        }

        final long contactId = ContentUris.parseId(mLookupUri);

        mNameRawContactId =
                mCursor.getLong(mCursor.getColumnIndex(Contacts.NAME_RAW_CONTACT_ID));
        mDisplayNameSource =
                mCursor.getInt(mCursor.getColumnIndex(Contacts.DISPLAY_NAME_SOURCE));

        mCursor.registerContentObserver(mObserver);

        // Clear flags and start queries to data and status
        mHasEntities = false;
        mHasStatuses = false;

        mHandler.startQuery(TOKEN_ENTITIES, null, RawContactsEntity.CONTENT_URI, null,
                RawContacts.CONTACT_ID + "=?", new String[] {
                    String.valueOf(contactId)
                }, null);
        final Uri dataUri = Uri.withAppendedPath(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                Contacts.Data.CONTENT_DIRECTORY);
        mHandler.startQuery(TOKEN_STATUSES, null, dataUri, StatusQuery.PROJECTION,
                        StatusUpdates.PRESENCE + " IS NOT NULL OR " + StatusUpdates.STATUS
                                + " IS NOT NULL", null, null);

        mContactHeaderWidget.bindFromContactLookupUri(mLookupUri);
    }

    private void closeCursor() {
        if (mCursor != null) {
            mCursor.unregisterContentObserver(mObserver);
            mCursor.close();
            mCursor = null;
        }
    }

    /**
     * Consider binding views after any of several background queries has
     * completed. We check internal flags and only bind when all data has
     * arrived.
     */
    private void considerBindData() {
        if (mHasEntities && mHasStatuses) {
            bindData();
        }
    }

    private void bindData() {

        // Build up the contact entries
        buildEntries();

        // Collapse similar data items in select sections.
        Collapser.collapseList(mPhoneEntries);
        Collapser.collapseList(mSmsEntries);
        Collapser.collapseList(mEmailEntries);
        Collapser.collapseList(mPostalEntries);
        Collapser.collapseList(mImEntries);

        if (mAdapter == null) {
            mAdapter = new ViewAdapter(this, mSections);
            mListView.setAdapter(mAdapter);
        } else {
            mAdapter.setSections(mSections, SHOW_SEPARATORS);
        }
        mListView.setEmptyView(mEmptyView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.view, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        // Only allow edit when we have at least one raw_contact id
        final boolean hasRawContact = (mRawContactIds.size() > 0);
        menu.findItem(R.id.menu_edit).setEnabled(hasRawContact);

        // Only allow share when unrestricted contacts available
        menu.findItem(R.id.menu_share).setEnabled(!mAllRestricted);

        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        // This can be null sometimes, don't crash...
        if (info == null) {
            Log.e(TAG, "bad menuInfo");
            return;
        }

        ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position, SHOW_SEPARATORS);
        menu.setHeaderTitle(R.string.contactOptionsTitle);
        if (entry.mimetype.equals(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)) {
            menu.add(0, 0, 0, R.string.menu_call).setIntent(entry.intent);
            menu.add(0, 0, 0, R.string.menu_sendSMS).setIntent(entry.secondaryIntent);

            //Code added for Speed Dial support starts
            //added the menu item either to assign speed dial or remove speed dial
            if (entry.isSpeedDial == false) {
                menu.add(0, 0, 0, R.string.assign_speed_dial).setIntent(entry.tertiaryIntent);
            } else {
                menu.add(0, REMOVE_SPEED_DIAL, 0, R.string.remove_speed_dial);
            }
            //Code added for Speed Dial support ends

        if (!entry.isPrimary) {
                menu.add(0, MENU_ITEM_MAKE_DEFAULT, 0, R.string.menu_makeDefaultNumber);
            }
        } else if (entry.mimetype.equals(CommonDataKinds.Email.CONTENT_ITEM_TYPE)) {
            menu.add(0, 0, 0, R.string.menu_sendEmail).setIntent(entry.intent);
            if (!entry.isPrimary) {
                menu.add(0, MENU_ITEM_MAKE_DEFAULT, 0, R.string.menu_makeDefaultEmail);
            }
        } else if (entry.mimetype.equals(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)) {
            menu.add(0, 0, 0, R.string.menu_viewAddress).setIntent(entry.intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_edit: {
                Long rawContactIdToEdit = null;
                if (mRawContactIds.size() > 0) {
                    rawContactIdToEdit = mRawContactIds.get(0);
                } else {
                    // There is no rawContact to edit.
                    break;
                }
                Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI,
                        rawContactIdToEdit);
                startActivityForResult(new Intent(Intent.ACTION_EDIT, rawContactUri),
                        REQUEST_EDIT_CONTACT);
                break;
            }
            case R.id.menu_delete: {
                // Get confirmation
                if (mReadOnlySourcesCnt > 0 & mWritableSourcesCnt > 0) {
                    showDialog(DIALOG_CONFIRM_READONLY_DELETE);
                } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
                    showDialog(DIALOG_CONFIRM_READONLY_HIDE);
                } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
                    showDialog(DIALOG_CONFIRM_MULTIPLE_DELETE);
                } else {
                    showDialog(DIALOG_CONFIRM_DELETE);
                }
                return true;
            }
            case R.id.menu_join: {
                showJoinAggregateActivity();
                return true;
            }
            case R.id.menu_options: {
                showOptionsActivity();
                return true;
            }
            case R.id.menu_share: {
                if (mAllRestricted) return false;

                // TODO: Keep around actual LOOKUP_KEY, or formalize method of extracting
                final String lookupKey = Uri.encode(mLookupUri.getPathSegments().get(2));
                final Uri shareUri = Uri.withAppendedPath(Contacts.CONTENT_VCARD_URI, lookupKey);

                final Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType(Contacts.CONTENT_VCARD_TYPE);
                intent.putExtra(Intent.EXTRA_STREAM, shareUri);

                // Launch chooser to share contact via
                final CharSequence chooseTitle = getText(R.string.share_via);
                final Intent chooseIntent = Intent.createChooser(intent, chooseTitle);

                try {
                    startActivity(chooseIntent);
                } catch (ActivityNotFoundException ex) {
                    Toast.makeText(this, R.string.share_error, Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_MAKE_DEFAULT: {
                if (makeItemDefault(item)) {
                    return true;
                }
                break;
            }
            //Code added for Speed Dial support starts
            // removes the assigned speed dial of the phone number
            case REMOVE_SPEED_DIAL: {
                if (removeSpeedDial(item)) {
                    return true;
                }
                break;
            }
            //Code added for Speed Dial support ends
        }

        return super.onContextItemSelected(item);
    }

    //Code added for Speed Dial support starts
    // removes the assigned speed dial of the phone number
    private boolean removeSpeedDial(MenuItem item) {
        ViewEntry entry = getViewEntryForMenuItem(item);
        if (entry == null) {
            return false;
        }

        getContentResolver().delete(ContactsContract.SpeedDial.CONTENT_URI, ContactsContract.SpeedDial.PHONE_ID +"= ?", new String[] {Long.toString(entry.id)});
        return true;

    }
    //Code added for Speed Dial support ends

    private boolean makeItemDefault(MenuItem item) {
        ViewEntry entry = getViewEntryForMenuItem(item);
        if (entry == null) {
            return false;
        }

        // Update the primary values in the data record.
        ContentValues values = new ContentValues(1);
        values.put(Data.IS_SUPER_PRIMARY, 1);
        getContentResolver().update(ContentUris.withAppendedId(Data.CONTENT_URI, entry.id),
                values, null, null);
        startEntityQuery();
        return true;
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     */
    public void showJoinAggregateActivity() {
        long freshId = getRefreshedContactId();
        if (freshId > 0) {
            String displayName = null;
            if (mCursor.moveToFirst()) {
                displayName = mCursor.getString(0);
            }
            Intent intent = new Intent(ContactsListActivity.JOIN_AGGREGATE);
            intent.putExtra(ContactsListActivity.EXTRA_AGGREGATE_ID, freshId);
            if (displayName != null) {
                intent.putExtra(ContactsListActivity.EXTRA_AGGREGATE_NAME, displayName);
            }
            startActivityForResult(intent, REQUEST_JOIN_CONTACT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_JOIN_CONTACT) {
            if (resultCode == RESULT_OK && intent != null) {
                final long contactId = ContentUris.parseId(intent.getData());
                joinAggregate(contactId);
            }
        } else if (requestCode == REQUEST_EDIT_CONTACT) {
            if (resultCode == EditContactActivity.RESULT_CLOSE_VIEW_ACTIVITY) {
                finish();
            } else if (resultCode == Activity.RESULT_OK) {
                mLookupUri = intent.getData();
                if (mLookupUri == null) {
                    finish();
                }
            }
        }
    }

    private void joinAggregate(final long contactId) {
        Cursor c = mResolver.query(RawContacts.CONTENT_URI, new String[] {RawContacts._ID},
                RawContacts.CONTACT_ID + "=" + contactId, null, null);

        try {
            while(c.moveToNext()) {
                long rawContactId = c.getLong(0);
                setAggregationException(rawContactId, AggregationExceptions.TYPE_KEEP_TOGETHER);
            }
        } finally {
            c.close();
        }

        Toast.makeText(this, R.string.contactsJoinedMessage, Toast.LENGTH_LONG).show();
        startEntityQuery();
    }

    /**
     * Given a contact ID sets an aggregation exception to either join the contact with the
     * current aggregate or split off.
     */
    protected void setAggregationException(long rawContactId, int exceptionType) {
        ContentValues values = new ContentValues(3);
        for (long aRawContactId : mRawContactIds) {
            if (aRawContactId != rawContactId) {
                values.put(AggregationExceptions.RAW_CONTACT_ID1, aRawContactId);
                values.put(AggregationExceptions.RAW_CONTACT_ID2, rawContactId);
                values.put(AggregationExceptions.TYPE, exceptionType);
                mResolver.update(AggregationExceptions.CONTENT_URI, values, null, null);
            }
        }
    }

    private void showOptionsActivity() {
        final Intent intent = new Intent(this, ContactOptionsActivity.class);
        intent.setData(mLookupUri);
        startActivity(intent);
    }

    private ViewEntry getViewEntryForMenuItem(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
             info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return null;
        }

        return ContactEntryAdapter.getEntry(mSections, info.position, SHOW_SEPARATORS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                try {
                    ITelephony phone = ITelephony.Stub.asInterface(
                            ServiceManager.checkService("phone"));
                    if (phone != null && !phone.isIdle()) {
                        // Skip out and let the key be handled at a higher level
                        break;
                    }
                } catch (RemoteException re) {
                    // Fall through and try to call the contact
                }

                int index = mListView.getSelectedItemPosition();
                if (index != -1) {
                    ViewEntry entry = ViewAdapter.getEntry(mSections, index, SHOW_SEPARATORS);
                    if (entry != null &&
                            entry.intent.getAction() == Intent.ACTION_CALL_PRIVILEGED) {
                        startActivity(entry.intent);
                        return true;
                    }
                } else if (mPrimaryPhoneUri != null) {
                    // There isn't anything selected, call the default number
                    final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                            mPrimaryPhoneUri);
                    startActivity(intent);
                    return true;
                }
                return false;
            }

            case KeyEvent.KEYCODE_DEL: {
                if (mReadOnlySourcesCnt > 0 & mWritableSourcesCnt > 0) {
                    showDialog(DIALOG_CONFIRM_READONLY_DELETE);
                } else if (mReadOnlySourcesCnt > 0 && mWritableSourcesCnt == 0) {
                    showDialog(DIALOG_CONFIRM_READONLY_HIDE);
                } else if (mReadOnlySourcesCnt == 0 && mWritableSourcesCnt > 1) {
                    showDialog(DIALOG_CONFIRM_MULTIPLE_DELETE);
                } else {
                    showDialog(DIALOG_CONFIRM_DELETE);
                }
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    public void onItemClick(AdapterView parent, View v, int position, long id) {
        ViewEntry entry = ViewAdapter.getEntry(mSections, position, SHOW_SEPARATORS);
        if (entry != null) {
            Intent intent = entry.intent;
            if (intent != null) {
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "No activity found for intent: " + intent);
                    signalError();
                }
            } else {
                signalError();
            }
        } else {
            signalError();
        }
    }

    /**
     * Signal an error to the user via a beep, or some other method.
     */
    private void signalError() {
        //TODO: implement this when we have the sonification APIs
    }

    /**
     * Build up the entries to display on the screen.
     *
     * @param personCursor the URI for the contact being displayed
     */
    private final void buildEntries() {
        // Clear out the old entries
        final int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        mRawContactIds.clear();

        mReadOnlySourcesCnt = 0;
        mWritableSourcesCnt = 0;
        mAllRestricted = true;
        mPrimaryPhoneUri = null;

        mWritableRawContactIds.clear();

        final Context context = this;
        final Sources sources = Sources.getInstance(context);

        // Build up method entries
        if (mLookupUri != null) {
            for (Entity entity: mEntities) {
                final ContentValues entValues = entity.getEntityValues();
                final String accountType = entValues.getAsString(RawContacts.ACCOUNT_TYPE);
                final long rawContactId = entValues.getAsLong(RawContacts._ID);

                // Mark when this contact has any unrestricted components
                final boolean isRestricted = entValues.getAsInteger(RawContacts.IS_RESTRICTED) != 0;
                if (!isRestricted) mAllRestricted = false;

                if (!mRawContactIds.contains(rawContactId)) {
                    mRawContactIds.add(rawContactId);
                }
                ContactsSource contactsSource = sources.getInflatedSource(accountType,
                        ContactsSource.LEVEL_SUMMARY);
                if (contactsSource != null && contactsSource.readOnly) {
                    mReadOnlySourcesCnt += 1;
                } else {
                    mWritableSourcesCnt += 1;
                    mWritableRawContactIds.add(rawContactId);
                }


                for (NamedContentValues subValue : entity.getSubValues()) {
                    final ContentValues entryValues = subValue.values;
                    entryValues.put(Data.RAW_CONTACT_ID, rawContactId);

                    final long dataId = entryValues.getAsLong(Data._ID);
                    final String mimeType = entryValues.getAsString(Data.MIMETYPE);
                    if (mimeType == null) continue;

                    final DataKind kind = sources.getKindOrFallback(accountType, mimeType, this,
                            ContactsSource.LEVEL_MIMETYPES);
                    if (kind == null) continue;

                    final ViewEntry entry = ViewEntry.fromValues(context, mimeType, kind,
                            rawContactId, dataId, entryValues);

                    final boolean hasData = !TextUtils.isEmpty(entry.data);
                    final boolean isSuperPrimary = entryValues.getAsInteger(
                            Data.IS_SUPER_PRIMARY) != 0;

                    if (Phone.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                        // Build phone entries
                        mNumPhoneNumbers++;

                        entry.intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                Uri.fromParts(Constants.SCHEME_TEL, entry.data, null));
                        entry.secondaryIntent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts(Constants.SCHEME_SMSTO, entry.data, null));

                        //Code added for Speed Dial support starts
                        entry.tertiaryIntent = new Intent(UI.SPEED_DIAL_GRID_ACTION)
                                .putExtra(UI.SPEED_DIAL_PHONE_ID, Long.toString(entry.id));
                        //Code added for Speed Dial support ends


                        // Remember super-primary phone
                        if (isSuperPrimary) mPrimaryPhoneUri = entry.uri;

                        entry.isPrimary = isSuperPrimary;
                        mPhoneEntries.add(entry);

                        if (entry.type == CommonDataKinds.Phone.TYPE_MOBILE
                                || mShowSmsLinksForAllPhones) {
                            // Add an SMS entry
                            if (kind.iconAltRes > 0) {
                                entry.secondaryActionIcon = kind.iconAltRes;
                            }
                        }
                    } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                        // Build email entries
                        entry.intent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts(Constants.SCHEME_MAILTO, entry.data, null));
                        entry.isPrimary = isSuperPrimary;
                        mEmailEntries.add(entry);

                        // When Email rows have status, create additional Im row
                        final DataStatus status = mStatuses.get(entry.id);
                        if (status != null) {
                            final String imMime = Im.CONTENT_ITEM_TYPE;
                            final DataKind imKind = sources.getKindOrFallback(accountType,
                                    imMime, this, ContactsSource.LEVEL_MIMETYPES);
                            final ViewEntry imEntry = ViewEntry.fromValues(context,
                                    imMime, imKind, rawContactId, dataId, entryValues);
                            imEntry.intent = ContactsUtils.buildImIntent(entryValues);
                            imEntry.applyStatus(status, false);
                            mImEntries.add(imEntry);
                        }
                    } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                        // Build postal entries
                        entry.maxLines = 4;
                        entry.intent = new Intent(Intent.ACTION_VIEW, entry.uri);
                        mPostalEntries.add(entry);
                    } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                        // Build IM entries
                        entry.intent = ContactsUtils.buildImIntent(entryValues);
                        if (TextUtils.isEmpty(entry.label)) {
                            entry.label = getString(R.string.chat).toLowerCase();
                        }

                        // Apply presence and status details when available
                        final DataStatus status = mStatuses.get(entry.id);
                        if (status != null) {
                            entry.applyStatus(status, false);
                        }
                        mImEntries.add(entry);
                    } else if (Organization.CONTENT_ITEM_TYPE.equals(mimeType) &&
                            (hasData || !TextUtils.isEmpty(entry.label))) {
                        // Build organization entries
                        final boolean isNameRawContact = (mNameRawContactId == rawContactId);

                        final boolean duplicatesTitle =
                            isNameRawContact
                            && mDisplayNameSource == DisplayNameSources.ORGANIZATION
                            && (!hasData || TextUtils.isEmpty(entry.label));

                        if (!duplicatesTitle) {
                            entry.uri = null;

                            if (TextUtils.isEmpty(entry.label)) {
                                entry.label = entry.data;
                                entry.data = "";
                            }

                            mOrganizationEntries.add(entry);
                        }
                    } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                        // Build nickname entries
                        final boolean isNameRawContact = (mNameRawContactId == rawContactId);

                        final boolean duplicatesTitle =
                            isNameRawContact
                            && mDisplayNameSource == DisplayNameSources.NICKNAME;

                        if (!duplicatesTitle) {
                            entry.uri = null;
                            mNicknameEntries.add(entry);
                        }
                    } else if (Note.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                        // Build note entries
                        entry.uri = null;
                        entry.maxLines = 100;
                        mOtherEntries.add(entry);
                    } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType) && hasData) {
                        // Build note entries
                        entry.uri = null;
                        entry.maxLines = 10;
                        try {
                            WebAddress webAddress = new WebAddress(entry.data);
                            entry.intent = new Intent(Intent.ACTION_VIEW,
                                    Uri.parse(webAddress.toString()));
                        } catch (ParseException e) {
                            Log.e(TAG, "Couldn't parse website: " + entry.data);
                        }
                        mOtherEntries.add(entry);
                    } else {
                        // Handle showing custom rows
                        entry.intent = new Intent(Intent.ACTION_VIEW, entry.uri);

                        // Use social summary when requested by external source
                        final DataStatus status = mStatuses.get(entry.id);
                        final boolean hasSocial = kind.actionBodySocial && status != null;
                        if (hasSocial) {
                            entry.applyStatus(status, true);
                        }

                        if (hasSocial || hasData) {
                            mOtherEntries.add(entry);
                        }
                    }
                }
            }
        }
    }

    static String buildActionString(DataKind kind, ContentValues values, boolean lowerCase,
            Context context) {
        if (kind.actionHeader == null) {
            return null;
        }
        CharSequence actionHeader = kind.actionHeader.inflateUsing(context, values);
        if (actionHeader == null) {
            return null;
        }
        return lowerCase ? actionHeader.toString().toLowerCase() : actionHeader.toString();
    }

    static String buildDataString(DataKind kind, ContentValues values, Context context) {
        if (kind.actionBody == null) {
            return null;
        }
        CharSequence actionBody = kind.actionBody.inflateUsing(context, values);
        return actionBody == null ? null : actionBody.toString();
    }

    /**
     * A basic structure with the data for a contact entry in the list.
     */
    static class ViewEntry extends ContactEntryAdapter.Entry implements Collapsible<ViewEntry> {
        public Context context = null;
        public String resPackageName = null;
        public int actionIcon = -1;
        public boolean isPrimary = false;
        public int secondaryActionIcon = -1;
        public Intent intent;
        public Intent secondaryIntent = null;
        //Code added for Speed Dial support starts
        public Intent tertiaryIntent = null;
        public boolean isSpeedDial = false;
        //Code added for Speed Dial support ends

        public int maxLabelLines = 1;
        public ArrayList<Long> ids = new ArrayList<Long>();
        public int collapseCount = 0;

        public int presence = -1;

        public CharSequence footerLine = null;

        private ViewEntry() {
        }

        /**
         * Build new {@link ViewEntry} and populate from the given values.
         */
        public static ViewEntry fromValues(Context context, String mimeType, DataKind kind,
                long rawContactId, long dataId, ContentValues values) {
            final ViewEntry entry = new ViewEntry();
            entry.context = context;
            entry.contactId = rawContactId;
            entry.id = dataId;
            entry.uri = ContentUris.withAppendedId(Data.CONTENT_URI, entry.id);
            entry.mimetype = mimeType;
            entry.label = buildActionString(kind, values, false, context);
            entry.data = buildDataString(kind, values, context);

            if (kind.typeColumn != null && values.containsKey(kind.typeColumn)) {
                entry.type = values.getAsInteger(kind.typeColumn);
            }
            if (kind.iconRes > 0) {
                entry.resPackageName = kind.resPackageName;
                entry.actionIcon = kind.iconRes;
            }

            return entry;
        }

        /**
         * Apply given {@link DataStatus} values over this {@link ViewEntry}
         *
         * @param fillData When true, the given status replaces {@link #data}
         *            and {@link #footerLine}. Otherwise only {@link #presence}
         *            is updated.
         */
        public ViewEntry applyStatus(DataStatus status, boolean fillData) {
            presence = status.getPresence();
            if (fillData && status.isValid()) {
                this.data = status.getStatus().toString();
                this.footerLine = status.getTimestampLabel(context);
            }

            return this;
        }

        public boolean collapseWith(ViewEntry entry) {
            // assert equal collapse keys
            if (!shouldCollapseWith(entry)) {
                return false;
            }

            // Choose the label associated with the highest type precedence.
            if (TypePrecedence.getTypePrecedence(mimetype, type)
                    > TypePrecedence.getTypePrecedence(entry.mimetype, entry.type)) {
                type = entry.type;
                label = entry.label;
            }

            // Choose the max of the maxLines and maxLabelLines values.
            maxLines = Math.max(maxLines, entry.maxLines);
            maxLabelLines = Math.max(maxLabelLines, entry.maxLabelLines);

            // Choose the presence with the highest precedence.
            if (StatusUpdates.getPresencePrecedence(presence)
                    < StatusUpdates.getPresencePrecedence(entry.presence)) {
                presence = entry.presence;
            }

            // If any of the collapsed entries are primary make the whole thing primary.
            isPrimary = entry.isPrimary ? true : isPrimary;

            // uri, and contactdId, shouldn't make a difference. Just keep the original.
            // Keep track of all the ids that have been collapsed with this one.
            ids.add(entry.id);
            collapseCount++;
            return true;
        }

        public boolean shouldCollapseWith(ViewEntry entry) {
            if (entry == null) {
                return false;
            }

            if (!ContactsUtils.shouldCollapse(context, mimetype, data, entry.mimetype,
                    entry.data)) {
                return false;
            }

            if (!TextUtils.equals(mimetype, entry.mimetype)
                    || !ContactsUtils.areIntentActionEqual(intent, entry.intent)
                    || !ContactsUtils.areIntentActionEqual(secondaryIntent, entry.secondaryIntent)
                    || actionIcon != entry.actionIcon) {
                return false;
            }

            return true;
        }
    }

    /** Cache of the children views of a row */
    static class ViewCache {
        public TextView label;
        public TextView data;
        public TextView footer;
        public ImageView actionIcon;
        public ImageView presenceIcon;
        public ImageView primaryIcon;
        public ImageView speedDialIcon; //Code added for Speed Dial support
        public ImageView secondaryActionButton;
        public View secondaryActionDivider;

        // Need to keep track of this too
        ViewEntry entry;
    }

    private final class ViewAdapter extends ContactEntryAdapter<ViewEntry>
            implements View.OnClickListener {


        ViewAdapter(Context context, ArrayList<ArrayList<ViewEntry>> sections) {
            super(context, sections, SHOW_SEPARATORS);
        }

        public void onClick(View v) {
            Intent intent = (Intent) v.getTag();
            startActivity(intent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewEntry entry = getEntry(mSections, position, false);
            View v;

            ViewCache views;

            // Check to see if we can reuse convertView
            if (convertView != null) {
                v = convertView;
                views = (ViewCache) v.getTag();
            } else {
                // Create a new view if needed
                v = mInflater.inflate(R.layout.list_item_text_icons, parent, false);

                // Cache the children
                views = new ViewCache();
                views.label = (TextView) v.findViewById(android.R.id.text1);
                views.data = (TextView) v.findViewById(android.R.id.text2);
                views.footer = (TextView) v.findViewById(R.id.footer);
                views.actionIcon = (ImageView) v.findViewById(R.id.action_icon);
                views.primaryIcon = (ImageView) v.findViewById(R.id.primary_icon);

                //Code added for Speed Dial support starts
                views.speedDialIcon = (ImageView) v.findViewById(R.id.speed_dail_icon);
                //Code added for Speed Dial support ends
                views.presenceIcon = (ImageView) v.findViewById(R.id.presence_icon);
                views.secondaryActionButton = (ImageView) v.findViewById(
                        R.id.secondary_action_button);
                views.secondaryActionButton.setOnClickListener(this);
                views.secondaryActionDivider = v.findViewById(R.id.divider);
                v.setTag(views);
            }

            // Update the entry in the view cache
            views.entry = entry;

            // Bind the data to the view
            bindView(v, entry);
            return v;
        }

        @Override
        protected View newView(int position, ViewGroup parent) {
            // getView() handles this
            throw new UnsupportedOperationException();
        }

        @Override
        protected void bindView(View view, ViewEntry entry) {
            final Resources resources = mContext.getResources();
            ViewCache views = (ViewCache) view.getTag();

            // Set the label
            TextView label = views.label;
            setMaxLines(label, entry.maxLabelLines);
            label.setText(entry.label);

            // Set the data
            TextView data = views.data;
            if (data != null) {
                if (entry.mimetype.equals(Phone.CONTENT_ITEM_TYPE)
                        || entry.mimetype.equals(Constants.MIME_SMS_ADDRESS)) {
                    data.setText(PhoneNumberUtils.formatNumber(entry.data));
                } else {
                    data.setText(entry.data);
                }
                setMaxLines(data, entry.maxLines);
            }

            // Set the footer
            if (!TextUtils.isEmpty(entry.footerLine)) {
                views.footer.setText(entry.footerLine);
                views.footer.setVisibility(View.VISIBLE);
            } else {
                views.footer.setVisibility(View.GONE);
            }

            // Set the primary icon
            views.primaryIcon.setVisibility(entry.isPrimary ? View.VISIBLE : View.GONE);

            //Code added for Speed Dial support starts
            //Set the visibility of speed dial icon
            String whereClause = ContactsContract.SpeedDial.PHONE_ID + "='"+entry.id+"'";
            Cursor speeDialCursor = getContentResolver().query(SpeedDial.CONTENT_URI,
                    new String[]{ContactsContract.SpeedDial.PHONE_ID}, whereClause, null, null);
            if (speeDialCursor != null) {
                if(speeDialCursor.moveToFirst()) {
                    views.speedDialIcon.setVisibility(View.VISIBLE);
                    entry.isSpeedDial = true;
                } else {
                    views.speedDialIcon.setVisibility(View.GONE);
                }
                speeDialCursor.close();
             } else {
                views.speedDialIcon.setVisibility(View.GONE);
            }
            //Code added for Speed Dial support ends

            // Set the action icon
            ImageView action = views.actionIcon;
            if (entry.actionIcon != -1) {
                Drawable actionIcon;
                if (entry.resPackageName != null) {
                    // Load external resources through PackageManager
                    actionIcon = mContext.getPackageManager().getDrawable(entry.resPackageName,
                            entry.actionIcon, null);
                } else {
                    actionIcon = resources.getDrawable(entry.actionIcon);
                }
                action.setImageDrawable(actionIcon);
                action.setVisibility(View.VISIBLE);
            } else {
                // Things should still line up as if there was an icon, so make it invisible
                action.setVisibility(View.INVISIBLE);
            }

            // Set the presence icon
            Drawable presenceIcon = ContactPresenceIconUtil.getPresenceIcon(
                    mContext, entry.presence);
            ImageView presenceIconView = views.presenceIcon;
            if (presenceIcon != null) {
                presenceIconView.setImageDrawable(presenceIcon);
                presenceIconView.setVisibility(View.VISIBLE);
            } else {
                presenceIconView.setVisibility(View.GONE);
            }

            // Set the secondary action button
            ImageView secondaryActionView = views.secondaryActionButton;
            Drawable secondaryActionIcon = null;
            if (entry.secondaryActionIcon != -1) {
                secondaryActionIcon = resources.getDrawable(entry.secondaryActionIcon);
            }
            if (entry.secondaryIntent != null && secondaryActionIcon != null) {
                secondaryActionView.setImageDrawable(secondaryActionIcon);
                secondaryActionView.setTag(entry.secondaryIntent);
                secondaryActionView.setVisibility(View.VISIBLE);
                views.secondaryActionDivider.setVisibility(View.VISIBLE);
            } else {
                secondaryActionView.setVisibility(View.GONE);
                views.secondaryActionDivider.setVisibility(View.GONE);
            }
        }

        private void setMaxLines(TextView textView, int maxLines) {
            if (maxLines == 1) {
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                textView.setSingleLine(false);
                textView.setMaxLines(maxLines);
                textView.setEllipsize(null);
            }
        }
    }

    private interface StatusQuery {
        final String[] PROJECTION = new String[] {
                Data._ID,
                Data.STATUS,
                Data.STATUS_RES_PACKAGE,
                Data.STATUS_ICON,
                Data.STATUS_LABEL,
                Data.STATUS_TIMESTAMP,
                Data.PRESENCE,
        };

        final int _ID = 0;
    }

    @Override
    public void startSearch(String initialQuery, boolean selectInitialQuery, Bundle appSearchData,
            boolean globalSearch) {
        if (globalSearch) {
            super.startSearch(initialQuery, selectInitialQuery, appSearchData, globalSearch);
        } else {
            ContactsSearchManager.startSearch(this, initialQuery);
        }
    }
}
