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

import static com.android.contacts.ContactEntryAdapter.CONTACT_CUSTOM_RINGTONE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_NAME_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_NOTES_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_PHONETIC_NAME_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.CONTACT_SEND_TO_VOICEMAIL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.CONTACT_STARRED_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_AUX_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_DATA_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_KIND_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_STATUS_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_TYPE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.METHODS_WITH_PRESENCE_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_COMPANY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_TITLE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.ORGANIZATIONS_TYPE_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_ID_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_ISPRIMARY_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_LABEL_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_NUMBER_COLUMN;
import static com.android.contacts.ContactEntryAdapter.PHONES_PROJECTION;
import static com.android.contacts.ContactEntryAdapter.PHONES_TYPE_COLUMN;

import com.android.internal.telephony.ITelephony;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.provider.Contacts;
import android.provider.Im;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.Organizations;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Presence;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the details of a specific contact.
 */
public class ViewContactActivity extends ListActivity 
        implements View.OnCreateContextMenuListener, View.OnClickListener,
        DialogInterface.OnClickListener {
    private static final String TAG = "ViewContact";
    private static final String SHOW_BARCODE_INTENT = "com.google.zxing.client.android.ENCODE";

    private static final boolean SHOW_SEPARATORS = false;
    
    private static final String[] PHONE_KEYS = {
        Contacts.Intents.Insert.PHONE,
        Contacts.Intents.Insert.SECONDARY_PHONE,
        Contacts.Intents.Insert.TERTIARY_PHONE
    };

    private static final String[] EMAIL_KEYS = {
        Contacts.Intents.Insert.EMAIL,
        Contacts.Intents.Insert.SECONDARY_EMAIL,
        Contacts.Intents.Insert.TERTIARY_EMAIL
    };

    private static final int DIALOG_CONFIRM_DELETE = 1;

    public static final int MENU_ITEM_DELETE = 1;
    public static final int MENU_ITEM_MAKE_DEFAULT = 2;
    public static final int MENU_ITEM_SHOW_BARCODE = 3;

    private Uri mUri;
    private ContentResolver mResolver;
    private ViewAdapter mAdapter;
    private int mNumPhoneNumbers = 0;

    /* package */ ArrayList<ViewEntry> mPhoneEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mSmsEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mEmailEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mPostalEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mImEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOrganizationEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ViewEntry> mOtherEntries = new ArrayList<ViewEntry>();
    /* package */ ArrayList<ArrayList<ViewEntry>> mSections = new ArrayList<ArrayList<ViewEntry>>();

    private Cursor mCursor;
    private boolean mObserverRegistered;
    
    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (mCursor != null && !mCursor.isClosed()){
                dataChanged();
            }
        }
    };

    public void onClick(DialogInterface dialog, int which) {
        if (mCursor != null) {
            if (mObserverRegistered) {
                mCursor.unregisterContentObserver(mObserver);
                mObserverRegistered = false;
            }
            mCursor.close();
            mCursor = null;
        }
        getContentResolver().delete(mUri, null, null);
        finish();
    }

    public void onClick(View view) {
        if (!mObserverRegistered) {
            return;
        }
        switch (view.getId()) {
            case R.id.star: {
                int oldStarredState = mCursor.getInt(CONTACT_STARRED_COLUMN);
                ContentValues values = new ContentValues(1);
                values.put(People.STARRED, oldStarredState == 1 ? 0 : 1);
                getContentResolver().update(mUri, values, null, null);
                break;
            }
        }
    }

    private TextView mNameView;
    private TextView mPhoneticNameView;  // may be null in some locales
    private ImageView mPhotoView;
    private int mNoPhotoResource;
    private CheckBox mStarView;
    private boolean mShowSmsLinksForAllPhones;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.view_contact);
        getListView().setOnCreateContextMenuListener(this);

        mNameView = (TextView) findViewById(R.id.name);
        mPhoneticNameView = (TextView) findViewById(R.id.phonetic_name);
        mPhotoView = (ImageView) findViewById(R.id.photo);
        mStarView = (CheckBox) findViewById(R.id.star);
        mStarView.setOnClickListener(this);

        // Set the photo with a random "no contact" image
        long now = SystemClock.elapsedRealtime();
        int num = (int) now & 0xf;
        if (num < 9) {
            // Leaning in from right, common
            mNoPhotoResource = R.drawable.ic_contact_picture;
        } else if (num < 14) {
            // Leaning in from left uncommon
            mNoPhotoResource = R.drawable.ic_contact_picture_2;
        } else {
            // Coming in from the top, rare
            mNoPhotoResource = R.drawable.ic_contact_picture_3;
        }

        mUri = getIntent().getData();
        mResolver = getContentResolver();

        // Build the list of sections. The order they're added to mSections dictates the
        // order they are displayed in the list.
        mSections.add(mPhoneEntries);
        mSections.add(mSmsEntries);
        mSections.add(mEmailEntries);
        mSections.add(mImEntries);
        mSections.add(mPostalEntries);
        mSections.add(mOrganizationEntries);
        mSections.add(mOtherEntries);

        //TODO Read this value from a preference
        mShowSmsLinksForAllPhones = true;

        mCursor = mResolver.query(mUri, CONTACT_PROJECTION, null, null, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mObserverRegistered = true;
        mCursor.registerContentObserver(mObserver);
        dataChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCursor != null) {
            if (mObserverRegistered) {
                mObserverRegistered = false;
                mCursor.unregisterContentObserver(mObserver);
            }
            mCursor.deactivate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mCursor != null) {
            if (mObserverRegistered) {
                mCursor.unregisterContentObserver(mObserver);
                mObserverRegistered = false;
            }
            mCursor.close();
        }
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
        }
        return null;
    }

    private void dataChanged() {
        mCursor.requery();
        if (mCursor.moveToFirst()) {
            // Set the name
            String name = mCursor.getString(CONTACT_NAME_COLUMN);
            if (TextUtils.isEmpty(name)) {
                mNameView.setText(getText(android.R.string.unknownName));
            } else {
                mNameView.setText(name);
            }

            if (mPhoneticNameView != null) {
                String phoneticName = mCursor.getString(CONTACT_PHONETIC_NAME_COLUMN);
                mPhoneticNameView.setText(phoneticName);
            }

            // Load the photo
            mPhotoView.setImageBitmap(People.loadContactPhoto(this, mUri, mNoPhotoResource,
                    null /* use the default options */));

            // Set the star
            mStarView.setChecked(mCursor.getInt(CONTACT_STARRED_COLUMN) == 1 ? true : false);

            // Build up the contact entries
            buildEntries(mCursor);
            if (mAdapter == null) {
                mAdapter = new ViewAdapter(this, mSections);
                setListAdapter(mAdapter);
            } else {
                mAdapter.setSections(mSections, SHOW_SEPARATORS);
            }
        } else {
            Toast.makeText(this, R.string.invalidContactMessage, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "invalid contact uri: " + mUri);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 0, 0, R.string.menu_editContact)
                .setIcon(android.R.drawable.ic_menu_edit)
                .setIntent(new Intent(Intent.ACTION_EDIT, mUri))
                .setAlphabeticShortcut('e');
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.menu_deleteContact)
                .setIcon(android.R.drawable.ic_menu_delete);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        // Perform this check each time the menu is about to be shown, because the Barcode Scanner
        // could be installed or uninstalled at any time.
        if (isBarcodeScannerInstalled()) {
            if (menu.findItem(MENU_ITEM_SHOW_BARCODE) == null) {
                menu.add(0, MENU_ITEM_SHOW_BARCODE, 0, R.string.menu_showBarcode)
                        .setIcon(R.drawable.ic_menu_show_barcode);
            }
        } else {
            menu.removeItem(MENU_ITEM_SHOW_BARCODE);
        }
        return true;
    }

    private boolean isBarcodeScannerInstalled() {
        final Intent intent = new Intent(SHOW_BARCODE_INTENT);
        ResolveInfo ri = getPackageManager().resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null;
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
        switch (entry.kind) {
            case Contacts.KIND_PHONE: {
                menu.add(0, 0, 0, R.string.menu_call).setIntent(entry.intent);
                menu.add(0, 0, 0, R.string.menu_sendSMS).setIntent(entry.auxIntent);
                if (entry.primaryIcon == -1) {
                    menu.add(0, MENU_ITEM_MAKE_DEFAULT, 0, R.string.menu_makeDefaultNumber);
                }
                break;
            }

            case Contacts.KIND_EMAIL: {
                menu.add(0, 0, 0, R.string.menu_sendEmail).setIntent(entry.intent);
                break;
            }

            case Contacts.KIND_POSTAL: {
                menu.add(0, 0, 0, R.string.menu_viewAddress).setIntent(entry.intent);
                break;
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_DELETE: {
                // Get confirmation
                showDialog(DIALOG_CONFIRM_DELETE);
                return true;
            }
            case MENU_ITEM_SHOW_BARCODE:
                if (mCursor.moveToFirst()) {
                    Intent intent = new Intent(SHOW_BARCODE_INTENT);
                    intent.putExtra("ENCODE_TYPE", "CONTACT_TYPE");
                    Bundle bundle = new Bundle();
                    String name = mCursor.getString(CONTACT_NAME_COLUMN);
                    if (!TextUtils.isEmpty(name)) {
                        bundle.putString(Contacts.Intents.Insert.NAME, name);
                        // The 0th ViewEntry in each ArrayList below is a separator item
                        int entriesToAdd = Math.min(mPhoneEntries.size() - 1, PHONE_KEYS.length);
                        for (int x = 0; x < entriesToAdd; x++) {
                            ViewEntry entry = mPhoneEntries.get(x + 1);
                            bundle.putString(PHONE_KEYS[x], entry.data);
                        }
                        entriesToAdd = Math.min(mEmailEntries.size() - 1, EMAIL_KEYS.length);
                        for (int x = 0; x < entriesToAdd; x++) {
                            ViewEntry entry = mEmailEntries.get(x + 1);
                            bundle.putString(EMAIL_KEYS[x], entry.data);
                        }
                        if (mPostalEntries.size() >= 2) {
                            ViewEntry entry = mPostalEntries.get(1);
                            bundle.putString(Contacts.Intents.Insert.POSTAL, entry.data);
                        }
                        intent.putExtra("ENCODE_DATA", bundle);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            // The check in onPrepareOptionsMenu() should make this impossible, but
                            // for safety I'm catching the exception rather than crashing. Ideally
                            // I'd call Menu.removeItem() here too, but I don't see a way to get
                            // the options menu.
                            Log.e(TAG, "Show barcode menu item was clicked but Barcode Scanner " +
                                    "was not installed.");
                        }
                        return true;
                    }
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_MAKE_DEFAULT: {
                AdapterView.AdapterContextMenuInfo info;
                try {
                     info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
                } catch (ClassCastException e) {
                    Log.e(TAG, "bad menuInfo", e);
                    break;
                }

                ViewEntry entry = ContactEntryAdapter.getEntry(mSections, info.position,
                        SHOW_SEPARATORS);
                ContentValues values = new ContentValues(1);
                values.put(People.PRIMARY_PHONE_ID, entry.id);
                getContentResolver().update(mUri, values, null, null);
                dataChanged();
                return true;
            }
        }
        return super.onContextItemSelected(item);
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

                int index = getListView().getSelectedItemPosition();
                if (index != -1) {
                    ViewEntry entry = ViewAdapter.getEntry(mSections, index, SHOW_SEPARATORS);
                    if (entry.kind == Contacts.KIND_PHONE) {
                        Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, entry.uri);
                        startActivity(intent);
                    }
                } else if (mNumPhoneNumbers != 0) {
                    // There isn't anything selected, call the default number
                    Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, mUri);
                    startActivity(intent);
                }
                return true;
            }

            case KeyEvent.KEYCODE_DEL: {
                showDialog(DIALOG_CONFIRM_DELETE);
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
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
     * Build separator entries for all of the sections.
     */
    private void buildSeparators() {
        ViewEntry separator;
        
        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorCallNumber);
        mPhoneEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorSendSmsMms);
        mSmsEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorSendEmail);
        mEmailEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorSendIm);
        mImEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorMapAddress);
        mPostalEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorOrganizations);
        mOrganizationEntries.add(separator);

        separator = new ViewEntry();
        separator.kind = ViewEntry.KIND_SEPARATOR;
        separator.data = getString(R.string.listSeparatorOtherInformation);
        mOtherEntries.add(separator);
    }

    private Uri constructImToUrl(String host, String data) {
	    // don't encode the url, because the Activity Manager can't find using the encoded url
        StringBuilder buf = new StringBuilder("imto://");
        buf.append(host);
        buf.append('/');
        buf.append(data);
        return Uri.parse(buf.toString());
    }

    /**
     * Build up the entries to display on the screen.
     * 
     * @param personCursor the URI for the contact being displayed
     */
    private final void buildEntries(Cursor personCursor) {
        // Clear out the old entries
        final int numSections = mSections.size();
        for (int i = 0; i < numSections; i++) {
            mSections.get(i).clear();
        }

        if (SHOW_SEPARATORS) {
            buildSeparators();
        }

        // Build up the phone entries
        final Uri phonesUri = Uri.withAppendedPath(mUri, People.Phones.CONTENT_DIRECTORY);
        final Cursor phonesCursor = mResolver.query(phonesUri, PHONES_PROJECTION, null, null,
                Phones.ISPRIMARY + " DESC");

        if (phonesCursor != null) {
            while (phonesCursor.moveToNext()) {
                final int type = phonesCursor.getInt(PHONES_TYPE_COLUMN);
                final String number = phonesCursor.getString(PHONES_NUMBER_COLUMN);
                final String label = phonesCursor.getString(PHONES_LABEL_COLUMN);
                final boolean isPrimary = phonesCursor.getInt(PHONES_ISPRIMARY_COLUMN) == 1;
                final long id = phonesCursor.getLong(PHONES_ID_COLUMN);
                final Uri uri = ContentUris.withAppendedId(phonesUri, id);

                // Don't crash if the number is bogus
                if (TextUtils.isEmpty(number)) {
                    Log.w(TAG, "empty number for phone " + id);
                    continue;
                }

                mNumPhoneNumbers++;
                
                // Add a phone number entry
                final ViewEntry entry = new ViewEntry();
                final CharSequence displayLabel = Phones.getDisplayLabel(this, type, label);
                entry.label = buildActionString(R.string.actionCall, displayLabel, true);
                entry.data = number;
                entry.id = id;
                entry.uri = uri;
                entry.intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, entry.uri);
                entry.auxIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.fromParts("sms", number, null));
                entry.kind = Contacts.KIND_PHONE;
                if (isPrimary) {
                    entry.primaryIcon = R.drawable.ic_default_number;
                }
                entry.actionIcon = android.R.drawable.sym_action_call;
                mPhoneEntries.add(entry);

                if (type == Phones.TYPE_MOBILE || mShowSmsLinksForAllPhones) {
                    // Add an SMS entry
                    ViewEntry smsEntry = new ViewEntry();
                    smsEntry.label = buildActionString(R.string.actionText, displayLabel, true);
                    smsEntry.data = number;
                    smsEntry.id = id;
                    smsEntry.uri = uri;
                    smsEntry.intent = entry.auxIntent;
                    smsEntry.kind = ViewEntry.KIND_SMS;
                    smsEntry.actionIcon = R.drawable.sym_action_sms;
                    mSmsEntries.add(smsEntry);
                }
            }

            phonesCursor.close();
        }

        // Build the contact method entries
        final Uri methodsUri = Uri.withAppendedPath(mUri, People.ContactMethods.CONTENT_DIRECTORY);
        Cursor methodsCursor = mResolver.query(
                Uri.withAppendedPath(mUri, "contact_methods_with_presence"),
                METHODS_WITH_PRESENCE_PROJECTION, null, null, null);

        if (methodsCursor != null) {
            String[] protocolStrings = getResources().getStringArray(android.R.array.imProtocols);

            while (methodsCursor.moveToNext()) {
                final int kind = methodsCursor.getInt(METHODS_KIND_COLUMN);
                final String label = methodsCursor.getString(METHODS_LABEL_COLUMN);
                final String data = methodsCursor.getString(METHODS_DATA_COLUMN);
                final int type = methodsCursor.getInt(METHODS_TYPE_COLUMN);
                final long id = methodsCursor.getLong(METHODS_ID_COLUMN);
                final Uri uri = ContentUris.withAppendedId(methodsUri, id);

                // Don't crash if the data is bogus
                if (TextUtils.isEmpty(data)) {
                    Log.w(TAG, "empty data for contact method " + id);
                    continue;
                }

                ViewEntry entry = new ViewEntry();
                entry.id = id;
                entry.uri = uri;
                entry.kind = kind;

                switch (kind) {
                    case Contacts.KIND_EMAIL:
                        entry.label = buildActionString(R.string.actionEmail,
                                ContactMethods.getDisplayLabel(this, kind, type, label), true);
                        entry.data = data;
                        entry.intent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts("mailto", data, null));
                        entry.actionIcon = android.R.drawable.sym_action_email;
                        mEmailEntries.add(entry);
                        break;

                    case Contacts.KIND_POSTAL:
                        entry.label = buildActionString(R.string.actionMap,
                                ContactMethods.getDisplayLabel(this, kind, type, label), true);
                        entry.data = data;
                        entry.maxLines = 4;
                        entry.intent = new Intent(Intent.ACTION_VIEW, uri);
                        entry.actionIcon = R.drawable.sym_action_map;
                        mPostalEntries.add(entry);
                        break;

                    case Contacts.KIND_IM: {
                        Object protocolObj = ContactMethods.decodeImProtocol(
                                methodsCursor.getString(METHODS_AUX_DATA_COLUMN));
                        String host;
                        if (protocolObj instanceof Number) {
                            int protocol = ((Number) protocolObj).intValue();
                            entry.label = buildActionString(R.string.actionChat,
                                    protocolStrings[protocol], false);
                            host = ContactMethods.lookupProviderNameFromId(protocol).toLowerCase();
                            if (protocol == ContactMethods.PROTOCOL_GOOGLE_TALK
                                    || protocol == ContactMethods.PROTOCOL_MSN) {
                                entry.maxLabelLines = 2;
                            }
                        } else {
                            String providerName = (String) protocolObj;
                            entry.label = buildActionString(R.string.actionChat,
                                    providerName, false);
                            host = providerName.toLowerCase();
                        }

                        // Only add the intent if there is a valid host
                        if (!TextUtils.isEmpty(host)) {
                            entry.intent = new Intent(Intent.ACTION_SENDTO,
                                    constructImToUrl(host, data));
                        }
                        entry.data = data;
                        if (!methodsCursor.isNull(METHODS_STATUS_COLUMN)) {
                            entry.presenceIcon = Presence.getPresenceIconResourceId(
                                    methodsCursor.getInt(METHODS_STATUS_COLUMN));
                        }
                        entry.actionIcon = android.R.drawable.sym_action_chat;
                        mImEntries.add(entry);
                        break;
                    }
                }
            }

            methodsCursor.close();
        }

        // Build IM entries for things we have presence info about but not explicit IM entries for
        long personId = ContentUris.parseId(mUri);
        String[] projection = new String[] {
                Presence.IM_HANDLE, // 0
                Presence.IM_PROTOCOL, // 1
                Presence.PRESENCE_STATUS, // 2
        };
        Cursor presenceCursor = mResolver.query(Presence.CONTENT_URI, projection,
                Presence.PERSON_ID + "=" + personId, null, null);
        if (presenceCursor != null) {
            try {
                while (presenceCursor.moveToNext()) {
                    // Find the display info for the provider
                    String data = presenceCursor.getString(0);
                    String label;
                    Object protocolObj = ContactMethods.decodeImProtocol(
                            presenceCursor.getString(1));
                    String host;
                    if (protocolObj instanceof Number) {
                        int protocol = ((Number) protocolObj).intValue();
                        label = getResources().getStringArray(
                                android.R.array.imProtocols)[protocol];
                        host = ContactMethods.lookupProviderNameFromId(protocol).toLowerCase();
                    } else {
                        String providerName = (String) protocolObj;
                        label = providerName;
                        host = providerName.toLowerCase();
                    }

                    if (TextUtils.isEmpty(host)) {
                        // A valid provider name is required
                        continue;
                    }


                    Intent intent = new Intent(Intent.ACTION_SENDTO, constructImToUrl(host, data));

                    // Check to see if there is already an entry for this IM account
                    boolean addEntry = true;
                    int numImEntries = mImEntries.size();
                    for (int i = 0; i < numImEntries; i++) {
                        // Check to see if the intent point to the same thing, if so we won't
                        // add this entry to the list since there is already an explict entry
                        // for the IM account
                        Intent existingIntent = mImEntries.get(i).intent;
                        if (intent.filterEquals(existingIntent)) {
                            addEntry = false;
                            break;
                        }
                    }

                    // Add the entry if an existing one wasn't found
                    if (addEntry) {
                        ViewEntry entry = new ViewEntry();
                        entry.kind = Contacts.KIND_IM;
                        entry.data = data;
                        entry.label = label;
                        entry.intent = intent;
                        entry.actionIcon = android.R.drawable.sym_action_chat;
                        entry.presenceIcon = Presence.getPresenceIconResourceId(
                                presenceCursor.getInt(2));
                        entry.maxLabelLines = 2;
                        mImEntries.add(entry);
                    }
                }
            } finally {
                presenceCursor.close();
            }
        }

        // Build the organization entries
        final Uri organizationsUri = Uri.withAppendedPath(mUri, Organizations.CONTENT_DIRECTORY);
        Cursor organizationsCursor = mResolver.query(organizationsUri, ORGANIZATIONS_PROJECTION,
                null, null, null);

        if (organizationsCursor != null) {
            while (organizationsCursor.moveToNext()) {
                ViewEntry entry = new ViewEntry();
                entry.id = organizationsCursor.getLong(ORGANIZATIONS_ID_COLUMN);
                entry.uri = ContentUris.withAppendedId(organizationsUri, entry.id);
                entry.kind = Contacts.KIND_ORGANIZATION;
                entry.label = organizationsCursor.getString(ORGANIZATIONS_COMPANY_COLUMN);
                entry.data = organizationsCursor.getString(ORGANIZATIONS_TITLE_COLUMN);
                entry.actionIcon = R.drawable.sym_action_organization;
/*
                entry.label = Organizations.getDisplayLabel(this,
                        organizationsCursor.getInt(ORGANIZATIONS_TYPE_COLUMN),
                        organizationsCursor.getString(ORGANIZATIONS_LABEL_COLUMN)).toString();
*/
                mOrganizationEntries.add(entry);
            }

            organizationsCursor.close();
        }


        // Build the other entries
        String note = personCursor.getString(CONTACT_NOTES_COLUMN);
        if (!TextUtils.isEmpty(note)) {
            ViewEntry entry = new ViewEntry();
            entry.label = getString(R.string.label_notes);
            entry.data = note;
            entry.id = 0;
            entry.kind = ViewEntry.KIND_CONTACT;
            entry.uri = null;
            entry.intent = null;
            entry.maxLines = 10;
            entry.actionIcon = R.drawable.sym_note;
            mOtherEntries.add(entry);
        }
        
        // Build the ringtone entry
        String ringtoneStr = personCursor.getString(CONTACT_CUSTOM_RINGTONE_COLUMN);
        if (!TextUtils.isEmpty(ringtoneStr)) {
            // Get the URI
            Uri ringtoneUri = Uri.parse(ringtoneStr);
            if (ringtoneUri != null) {
                Ringtone ringtone = RingtoneManager.getRingtone(this, ringtoneUri);
                if (ringtone != null) {
                    ViewEntry entry = new ViewEntry();
                    entry.label = getString(R.string.label_ringtone);
                    entry.data = ringtone.getTitle(this);
                    entry.kind = ViewEntry.KIND_CONTACT;
                    entry.uri = ringtoneUri;
                    entry.actionIcon = R.drawable.sym_ringtone;
                    mOtherEntries.add(entry);
                }
            }
        }

        // Build the send directly to voice mail entry
        boolean sendToVoicemail = personCursor.getInt(CONTACT_SEND_TO_VOICEMAIL_COLUMN) == 1;
        if (sendToVoicemail) {
            ViewEntry entry = new ViewEntry();
            entry.label = getString(R.string.actionIncomingCall);
            entry.data = getString(R.string.detailIncomingCallsGoToVoicemail);
            entry.kind = ViewEntry.KIND_CONTACT;
            entry.actionIcon = R.drawable.sym_send_to_voicemail;
            mOtherEntries.add(entry);
        }
    }

    String buildActionString(int actionResId, CharSequence type, boolean lowerCase) {
        if (lowerCase) {
            return getString(actionResId, type.toString().toLowerCase());
        } else {
            return getString(actionResId, type.toString());
        }
    }
    
    /**
     * A basic structure with the data for a contact entry in the list.
     */
    final static class ViewEntry extends ContactEntryAdapter.Entry {
        public int primaryIcon = -1;
        public Intent intent;
        public Intent auxIntent = null;
        public int presenceIcon = -1;
        public int actionIcon = -1;
        public int maxLabelLines = 1;
    }

    private static final class ViewAdapter extends ContactEntryAdapter<ViewEntry> {
        /** Cache of the children views of a row */
        static class ViewCache {
            public TextView label;
            public TextView data;
            public ImageView actionIcon;
            public ImageView presenceIcon;
            
            // Need to keep track of this too
            ViewEntry entry;
        }
        
        ViewAdapter(Context context, ArrayList<ArrayList<ViewEntry>> sections) {
            super(context, sections, SHOW_SEPARATORS);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewEntry entry = getEntry(mSections, position, false); 
            View v;

            // Handle separators specially
            if (entry.kind == ViewEntry.KIND_SEPARATOR) {
                TextView separator = (TextView) mInflater.inflate(
                        R.layout.list_separator, parent, SHOW_SEPARATORS);
                separator.setText(entry.data);
                return separator;
            }

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
                views.actionIcon = (ImageView) v.findViewById(R.id.icon1);
                views.presenceIcon = (ImageView) v.findViewById(R.id.icon2);
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
                data.setText(entry.data);
                setMaxLines(data, entry.maxLines);
            }

            // Set the action icon
            ImageView action = views.actionIcon;
            if (entry.actionIcon != -1) {
                action.setImageDrawable(resources.getDrawable(entry.actionIcon));
                action.setVisibility(View.VISIBLE);
            } else {
                // Things should still line up as if there was an icon, so make it invisible
                action.setVisibility(View.INVISIBLE);
            }

            // Set the presence icon
            Drawable presenceIcon = null;
            if (entry.primaryIcon != -1) {
                presenceIcon = resources.getDrawable(entry.primaryIcon);
            } else if (entry.presenceIcon != -1) {
                presenceIcon = resources.getDrawable(entry.presenceIcon);
            }

            ImageView presence = views.presenceIcon;
            if (presenceIcon != null) {
                presence.setImageDrawable(presenceIcon);
                presence.setVisibility(View.VISIBLE);
            } else {
                presence.setVisibility(View.GONE);
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
}
