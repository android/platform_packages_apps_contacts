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

package com.android.contacts.ui;

import com.android.contacts.ContactsListActivity;
import com.android.contacts.ContactsSearchManager;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Editor;
import com.android.contacts.model.EntityDelta;
import com.android.contacts.model.EntityModifier;
import com.android.contacts.model.EntitySet;
import com.android.contacts.model.GoogleSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.EditType;
import com.android.contacts.model.Editor.EditorListener;
import com.android.contacts.model.EntityDelta.ValuesDelta;
import com.android.contacts.ui.widget.BaseContactEditorView;
import com.android.contacts.ui.widget.PhotoEditorView;
import com.android.contacts.util.EmptyService;
import com.android.contacts.util.WeakAsyncTask;
import com.google.android.collect.Lists;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Entity;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.ContentProviderOperation.Builder;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.ContactsContract.AggregationExceptions;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts.Data;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

/**
 * Activity for editing or inserting a contact.
 */
public final class EditContactActivity extends Activity
        implements View.OnClickListener, Comparator<EntityDelta> {

    private static final String TAG = "EditContactActivity";

    /** The launch code when picking a photo and the raw data is returned */
    private static final int PHOTO_PICKED_WITH_DATA = 3021;

    /** The launch code when a contact to join with is returned */
    private static final int REQUEST_JOIN_CONTACT = 3022;

    /** The launch code when taking a picture */
    private static final int CAMERA_WITH_DATA = 3023;

    private static final String KEY_EDIT_STATE = "state";
    private static final String KEY_RAW_CONTACT_ID_REQUESTING_PHOTO = "photorequester";
    private static final String KEY_VIEW_ID_GENERATOR = "viewidgenerator";
    private static final String KEY_CURRENT_PHOTO_FILE = "currentphotofile";
    private static final String KEY_QUERY_SELECTION = "queryselection";
    private static final String KEY_CONTACT_ID_FOR_JOIN = "contactidforjoin";

    /** The result code when view activity should close after edit returns */
    public static final int RESULT_CLOSE_VIEW_ACTIVITY = 777;

    public static final int SAVE_MODE_DEFAULT = 0;
    public static final int SAVE_MODE_SPLIT = 1;
    public static final int SAVE_MODE_JOIN = 2;

    private long mRawContactIdRequestingPhoto = -1;

    private static final int DIALOG_CONFIRM_DELETE = 1;
    private static final int DIALOG_CONFIRM_READONLY_DELETE = 2;
    private static final int DIALOG_CONFIRM_MULTIPLE_DELETE = 3;
    private static final int DIALOG_CONFIRM_READONLY_HIDE = 4;

    private static final int ICON_SIZE = 96;

    private static final File PHOTO_DIR = new File(
            Environment.getExternalStorageDirectory() + "/DCIM/Camera");

    private File mCurrentPhotoFile;

    String mQuerySelection;

    private long mContactIdForJoin;

    private static final int STATUS_LOADING = 0;
    private static final int STATUS_EDITING = 1;
    private static final int STATUS_SAVING = 2;

    private int mStatus;

    EntitySet mState;

    /** The linear layout holding the ContactEditorViews */
    LinearLayout mContent;

    private ArrayList<Dialog> mManagedDialogs = Lists.newArrayList();

    private ViewIdGenerator mViewIdGenerator;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        setContentView(R.layout.act_edit);

        // Build editor and listen for photo requests
        mContent = (LinearLayout) findViewById(R.id.editors);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        // Handle initial actions only when existing state missing
        final boolean hasIncomingState = icicle != null && icicle.containsKey(KEY_EDIT_STATE);

        if (Intent.ACTION_EDIT.equals(action) && !hasIncomingState) {
            setTitle(R.string.editContact_title_edit);
            mStatus = STATUS_LOADING;

            // Read initial state from database
            new QueryEntitiesTask(this).execute(intent);
        } else if (Intent.ACTION_INSERT.equals(action) && !hasIncomingState) {
            setTitle(R.string.editContact_title_insert);
            mStatus = STATUS_EDITING;
            // Trigger dialog to pick account type
            doAddAction();
        }

        if (icicle == null) {
            // If icicle is non-null, onRestoreInstanceState() will restore the generator.
            mViewIdGenerator = new ViewIdGenerator();
        }
    }

    private static class QueryEntitiesTask extends
            WeakAsyncTask<Intent, Void, EntitySet, EditContactActivity> {

        private String mSelection;

        public QueryEntitiesTask(EditContactActivity target) {
            super(target);
        }

        @Override
        protected EntitySet doInBackground(EditContactActivity target, Intent... params) {
            final Intent intent = params[0];

            final ContentResolver resolver = target.getContentResolver();

            // Handle both legacy and new authorities
            final Uri data = intent.getData();
            final String authority = data.getAuthority();
            final String mimeType = intent.resolveType(resolver);

            mSelection = "0";
            if (ContactsContract.AUTHORITY.equals(authority)) {
                if (Contacts.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    // Handle selected aggregate
                    final long contactId = ContentUris.parseId(data);
                    mSelection = RawContacts.CONTACT_ID + "=" + contactId;
                } else if (RawContacts.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    final long rawContactId = ContentUris.parseId(data);
                    final long contactId = ContactsUtils.queryForContactId(resolver, rawContactId);
                    mSelection = RawContacts.CONTACT_ID + "=" + contactId;
                }
            } else if (android.provider.Contacts.AUTHORITY.equals(authority)) {
                final long rawContactId = ContentUris.parseId(data);
                mSelection = Data.RAW_CONTACT_ID + "=" + rawContactId;
            }

            return EntitySet.fromQuery(target.getContentResolver(), mSelection, null, null);
        }

        @Override
        protected void onPostExecute(EditContactActivity target, EntitySet entitySet) {
            target.mQuerySelection = mSelection;

            // Load edit details in background
            final Context context = target;
            final Sources sources = Sources.getInstance(context);

            // Handle any incoming values that should be inserted
            final Bundle extras = target.getIntent().getExtras();
            final boolean hasExtras = extras != null && extras.size() > 0;
            final boolean hasState = entitySet.size() > 0;
            if (hasExtras && hasState) {
                // Find source defining the first RawContact found
                final EntityDelta state = entitySet.get(0);
                final String accountType = state.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
                final ContactsSource source = sources.getInflatedSource(accountType,
                        ContactsSource.LEVEL_CONSTRAINTS);
                EntityModifier.parseExtras(context, source, state, extras);
            }

            target.mState = entitySet;

            // Bind UI to new background state
            target.bindEditors();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (hasValidState()) {
            // Store entities with modifications
            outState.putParcelable(KEY_EDIT_STATE, mState);
        }

        outState.putLong(KEY_RAW_CONTACT_ID_REQUESTING_PHOTO, mRawContactIdRequestingPhoto);
        outState.putParcelable(KEY_VIEW_ID_GENERATOR, mViewIdGenerator);
        if (mCurrentPhotoFile != null) {
            outState.putString(KEY_CURRENT_PHOTO_FILE, mCurrentPhotoFile.toString());
        }
        outState.putString(KEY_QUERY_SELECTION, mQuerySelection);
        outState.putLong(KEY_CONTACT_ID_FOR_JOIN, mContactIdForJoin);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // Read modifications from instance
        mState = savedInstanceState.<EntitySet> getParcelable(KEY_EDIT_STATE);
        mRawContactIdRequestingPhoto = savedInstanceState.getLong(
                KEY_RAW_CONTACT_ID_REQUESTING_PHOTO);
        mViewIdGenerator = savedInstanceState.getParcelable(KEY_VIEW_ID_GENERATOR);
        String fileName = savedInstanceState.getString(KEY_CURRENT_PHOTO_FILE);
        if (fileName != null) {
            mCurrentPhotoFile = new File(fileName);
        }
        mQuerySelection = savedInstanceState.getString(KEY_QUERY_SELECTION);
        mContactIdForJoin = savedInstanceState.getLong(KEY_CONTACT_ID_FOR_JOIN);

        bindEditors();

        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        for (Dialog dialog : mManagedDialogs) {
            dismissDialog(dialog);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case DIALOG_CONFIRM_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.deleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
            case DIALOG_CONFIRM_READONLY_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
            case DIALOG_CONFIRM_MULTIPLE_DELETE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.multipleContactDeleteConfirmation)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
            case DIALOG_CONFIRM_READONLY_HIDE:
                return new AlertDialog.Builder(this)
                        .setTitle(R.string.deleteConfirmation_title)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.readOnlyContactWarning)
                        .setPositiveButton(android.R.string.ok, new DeleteClickListener())
                        .setCancelable(false)
                        .create();
        }
        return null;
    }

    /**
     * Start managing this {@link Dialog} along with the {@link Activity}.
     */
    private void startManagingDialog(Dialog dialog) {
        synchronized (mManagedDialogs) {
            mManagedDialogs.add(dialog);
        }
    }

    /**
     * Show this {@link Dialog} and manage with the {@link Activity}.
     */
    void showAndManageDialog(Dialog dialog) {
        startManagingDialog(dialog);
        dialog.show();
    }

    /**
     * Dismiss the given {@link Dialog}.
     */
    static void dismissDialog(Dialog dialog) {
        try {
            // Only dismiss when valid reference and still showing
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        } catch (Exception e) {
            Log.w(TAG, "Ignoring exception while dismissing dialog: " + e.toString());
        }
    }

    /**
     * Check if our internal {@link #mState} is valid, usually checked before
     * performing user actions.
     */
    protected boolean hasValidState() {
        return mStatus == STATUS_EDITING && mState != null && mState.size() > 0;
    }

    /**
     * Rebuild the editors to match our underlying {@link #mState} object, usually
     * called once we've parsed {@link Entity} data or have inserted a new
     * {@link RawContacts}.
     */
    protected void bindEditors() {
        if (mState == null) {
            return;
        }

        final LayoutInflater inflater = (LayoutInflater) getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        final Sources sources = Sources.getInstance(this);

        // Sort the editors
        Collections.sort(mState, this);

        // Remove any existing editors and rebuild any visible
        mContent.removeAllViews();
        int size = mState.size();
        for (int i = 0; i < size; i++) {
            // TODO ensure proper ordering of entities in the list
            EntityDelta entity = mState.get(i);
            final ValuesDelta values = entity.getValues();
            if (!values.isVisible()) continue;

            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final ContactsSource source = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_CONSTRAINTS);
            final long rawContactId = values.getAsLong(RawContacts._ID);

            BaseContactEditorView editor;
            if (!source.readOnly) {
                editor = (BaseContactEditorView) inflater.inflate(R.layout.item_contact_editor,
                        mContent, false);
            } else {
                editor = (BaseContactEditorView) inflater.inflate(
                        R.layout.item_read_only_contact_editor, mContent, false);
            }
            PhotoEditorView photoEditor = editor.getPhotoEditor();
            photoEditor.setEditorListener(new PhotoListener(rawContactId, source.readOnly,
                    photoEditor));

            mContent.addView(editor);
            editor.setState(entity, source, mViewIdGenerator);
        }

        // Show editor now that we've loaded state
        mContent.setVisibility(View.VISIBLE);
        mStatus = STATUS_EDITING;
    }

    /**
     * Class that listens to requests coming from photo editors
     */
    private class PhotoListener implements EditorListener, DialogInterface.OnClickListener {
        private long mRawContactId;
        private boolean mReadOnly;
        private PhotoEditorView mEditor;

        public PhotoListener(long rawContactId, boolean readOnly, PhotoEditorView editor) {
            mRawContactId = rawContactId;
            mReadOnly = readOnly;
            mEditor = editor;
        }

        public void onDeleted(Editor editor) {
            // Do nothing
        }

        public void onRequest(int request) {
            if (!hasValidState()) return;

            if (request == EditorListener.REQUEST_PICK_PHOTO) {
                if (mEditor.hasSetPhoto()) {
                    // There is an existing photo, offer to remove, replace, or promoto to primary
                    createPhotoDialog().show();
                } else if (!mReadOnly) {
                    // No photo set and not read-only, try to set the photo
                    doPickPhotoAction(mRawContactId);
                }
            }
        }

        /**
         * Prepare dialog for picking a new {@link EditType} or entering a
         * custom label. This dialog is limited to the valid types as determined
         * by {@link EntityModifier}.
         */
        public Dialog createPhotoDialog() {
            Context context = EditContactActivity.this;

            // Wrap our context to inflate list items using correct theme
            final Context dialogContext = new ContextThemeWrapper(context,
                    android.R.style.Theme_Light);

            String[] choices;
            if (mReadOnly) {
                choices = new String[1];
                choices[0] = getString(R.string.use_photo_as_primary);
            } else {
                choices = new String[3];
                choices[0] = getString(R.string.use_photo_as_primary);
                choices[1] = getString(R.string.removePicture);
                choices[2] = getString(R.string.changePicture);
            }
            final ListAdapter adapter = new ArrayAdapter<String>(dialogContext,
                    android.R.layout.simple_list_item_1, choices);

            final AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
            builder.setTitle(R.string.attachToContact);
            builder.setSingleChoiceItems(adapter, -1, this);
            return builder.create();
        }

        /**
         * Called when something in the dialog is clicked
         */
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();

            switch (which) {
                case 0:
                    // Set the photo as super primary
                    mEditor.setSuperPrimary(true);

                    // And set all other photos as not super primary
                    int count = mContent.getChildCount();
                    for (int i = 0; i < count; i++) {
                        View childView = mContent.getChildAt(i);
                        if (childView instanceof BaseContactEditorView) {
                            BaseContactEditorView editor = (BaseContactEditorView) childView;
                            PhotoEditorView photoEditor = editor.getPhotoEditor();
                            if (!photoEditor.equals(mEditor)) {
                                photoEditor.setSuperPrimary(false);
                            }
                        }
                    }
                    break;

                case 1:
                    // Remove the photo
                    mEditor.setPhotoBitmap(null);
                    break;

                case 2:
                    // Pick a new photo for the contact
                    doPickPhotoAction(mRawContactId);
                    break;
            }
        }
    }

    /** {@inheritDoc} */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_done:
                doSaveAction(SAVE_MODE_DEFAULT);
                break;
            case R.id.btn_discard:
                doRevertAction();
                break;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onBackPressed() {
        doSaveAction(SAVE_MODE_DEFAULT);
    }

    /** {@inheritDoc} */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignore failed requests
        if (resultCode != RESULT_OK) return;

        switch (requestCode) {
            case PHOTO_PICKED_WITH_DATA: {
                BaseContactEditorView requestingEditor = null;
                for (int i = 0; i < mContent.getChildCount(); i++) {
                    View childView = mContent.getChildAt(i);
                    if (childView instanceof BaseContactEditorView) {
                        BaseContactEditorView editor = (BaseContactEditorView) childView;
                        if (editor.getRawContactId() == mRawContactIdRequestingPhoto) {
                            requestingEditor = editor;
                            break;
                        }
                    }
                }

                if (requestingEditor != null) {
                    final Bitmap photo = data.getParcelableExtra("data");
                    requestingEditor.setPhotoBitmap(photo);
                    mRawContactIdRequestingPhoto = -1;
                } else {
                    // The contact that requested the photo is no longer present.
                    // TODO: Show error message
                }

                break;
            }

            case CAMERA_WITH_DATA: {
                doCropPhoto(mCurrentPhotoFile);
                break;
            }

            case REQUEST_JOIN_CONTACT: {
                if (resultCode == RESULT_OK && data != null) {
                    final long contactId = ContentUris.parseId(data.getData());
                    joinAggregate(contactId);
                }
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit, menu);


        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.menu_split).setVisible(mState != null && mState.size() > 1);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_done:
                return doSaveAction(SAVE_MODE_DEFAULT);
            case R.id.menu_discard:
                return doRevertAction();
            case R.id.menu_add:
                return doAddAction();
            case R.id.menu_delete:
                return doDeleteAction();
            case R.id.menu_split:
                return doSplitContactAction();
            case R.id.menu_join:
                return doJoinContactAction();
        }
        return false;
    }

    /**
     * Background task for persisting edited contact data, using the changes
     * defined by a set of {@link EntityDelta}. This task starts
     * {@link EmptyService} to make sure the background thread can finish
     * persisting in cases where the system wants to reclaim our process.
     */
    public static class PersistTask extends
            WeakAsyncTask<EntitySet, Void, Integer, EditContactActivity> {
        private static final int PERSIST_TRIES = 3;

        private static final int RESULT_UNCHANGED = 0;
        private static final int RESULT_SUCCESS = 1;
        private static final int RESULT_FAILURE = 2;

        private WeakReference<ProgressDialog> mProgress;

        private int mSaveMode;
        private Uri mContactLookupUri = null;

        public PersistTask(EditContactActivity target, int saveMode) {
            super(target);
            mSaveMode = saveMode;
        }

        /** {@inheritDoc} */
        @Override
        protected void onPreExecute(EditContactActivity target) {
            mProgress = new WeakReference<ProgressDialog>(ProgressDialog.show(target, null,
                    target.getText(R.string.savingContact)));

            // Before starting this task, start an empty service to protect our
            // process from being reclaimed by the system.
            final Context context = target;
            context.startService(new Intent(context, EmptyService.class));
        }

        /** {@inheritDoc} */
        @Override
        protected Integer doInBackground(EditContactActivity target, EntitySet... params) {
            final Context context = target;
            final ContentResolver resolver = context.getContentResolver();

            EntitySet state = params[0];

            // Trim any empty fields, and RawContacts, before persisting
            final Sources sources = Sources.getInstance(context);
            EntityModifier.trimEmpty(state, sources);

            // Attempt to persist changes
            int tries = 0;
            Integer result = RESULT_FAILURE;
            while (tries++ < PERSIST_TRIES) {
                try {
                    // Build operations and try applying
                    final ArrayList<ContentProviderOperation> diff = state.buildDiff();
                    ContentProviderResult[] results = null;
                    if (!diff.isEmpty()) {
                         results = resolver.applyBatch(ContactsContract.AUTHORITY, diff);
                    }

                    final long rawContactId = getRawContactId(state, diff, results);
                    if (rawContactId != -1) {
                        final Uri rawContactUri = ContentUris.withAppendedId(
                                RawContacts.CONTENT_URI, rawContactId);

                        // convert the raw contact URI to a contact URI
                        mContactLookupUri = RawContacts.getContactLookupUri(resolver,
                                rawContactUri);
                    }
                    result = (diff.size() > 0) ? RESULT_SUCCESS : RESULT_UNCHANGED;
                    break;

                } catch (RemoteException e) {
                    // Something went wrong, bail without success
                    Log.e(TAG, "Problem persisting user edits", e);
                    break;

                } catch (OperationApplicationException e) {
                    // Version consistency failed, re-parent change and try again
                    Log.w(TAG, "Version consistency failed, re-parenting: " + e.toString());
                    final EntitySet newState = EntitySet.fromQuery(resolver,
                            target.mQuerySelection, null, null);
                    state = EntitySet.mergeAfter(newState, state);
                }
            }

            return result;
        }

        private long getRawContactId(EntitySet state,
                final ArrayList<ContentProviderOperation> diff,
                final ContentProviderResult[] results) {
            long rawContactId = state.findRawContactId();
            if (rawContactId != -1) {
                return rawContactId;
            }

            // we gotta do some searching for the id
            final int diffSize = diff.size();
            for (int i = 0; i < diffSize; i++) {
                ContentProviderOperation operation = diff.get(i);
                if (operation.getType() == ContentProviderOperation.TYPE_INSERT
                        && operation.getUri().getEncodedPath().contains(
                                RawContacts.CONTENT_URI.getEncodedPath())) {
                    return ContentUris.parseId(results[i].uri);
                }
            }
            return -1;
        }

        /** {@inheritDoc} */
        @Override
        protected void onPostExecute(EditContactActivity target, Integer result) {
            final Context context = target;
            final ProgressDialog progress = mProgress.get();

            if (result == RESULT_SUCCESS && mSaveMode != SAVE_MODE_JOIN) {
                Toast.makeText(context, R.string.contactSavedToast, Toast.LENGTH_SHORT).show();
            } else if (result == RESULT_FAILURE) {
                Toast.makeText(context, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }

            dismissDialog(progress);

            // Stop the service that was protecting us
            context.stopService(new Intent(context, EmptyService.class));

            target.onSaveCompleted(result != RESULT_FAILURE, mSaveMode, mContactLookupUri);
        }
    }

    /**
     * Saves or creates the contact based on the mode, and if successful
     * finishes the activity.
     */
    boolean doSaveAction(int saveMode) {
        if (!hasValidState()) {
            return false;
        }

        mStatus = STATUS_SAVING;
        final PersistTask task = new PersistTask(this, saveMode);
        task.execute(mState);

        return true;
    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {

        public void onClick(DialogInterface dialog, int which) {
            Sources sources = Sources.getInstance(EditContactActivity.this);
            // Mark all raw contacts for deletion
            for (EntityDelta delta : mState) {
                delta.markDeleted();
            }
            // Save the deletes
            doSaveAction(SAVE_MODE_DEFAULT);
            finish();
        }
    }

    private void onSaveCompleted(boolean success, int saveMode, Uri contactLookupUri) {
        switch (saveMode) {
            case SAVE_MODE_DEFAULT:
                if (success && contactLookupUri != null) {
                    final Intent resultIntent = new Intent();

                    final Uri requestData = getIntent().getData();
                    final String requestAuthority = requestData == null ? null : requestData
                            .getAuthority();

                    if (android.provider.Contacts.AUTHORITY.equals(requestAuthority)) {
                        // Build legacy Uri when requested by caller
                        final long contactId = ContentUris.parseId(Contacts.lookupContact(
                                getContentResolver(), contactLookupUri));
                        final Uri legacyUri = ContentUris.withAppendedId(
                                android.provider.Contacts.People.CONTENT_URI, contactId);
                        resultIntent.setData(legacyUri);
                    } else {
                        // Otherwise pass back a lookup-style Uri
                        resultIntent.setData(contactLookupUri);
                    }

                    setResult(RESULT_OK, resultIntent);
                } else {
                    setResult(RESULT_CANCELED, null);
                }
                finish();
                break;

            case SAVE_MODE_SPLIT:
                if (success) {
                    Intent intent = new Intent();
                    intent.setData(contactLookupUri);
                    setResult(RESULT_CLOSE_VIEW_ACTIVITY, intent);
                }
                finish();
                break;

            case SAVE_MODE_JOIN:
                mStatus = STATUS_EDITING;
                if (success) {
                    showJoinAggregateActivity(contactLookupUri);
                }
                break;
        }
    }

    /**
     * Shows a list of aggregates that can be joined into the currently viewed aggregate.
     *
     * @param contactLookupUri the fresh URI for the currently edited contact (after saving it)
     */
    public void showJoinAggregateActivity(Uri contactLookupUri) {
        if (contactLookupUri == null) {
            return;
        }

        mContactIdForJoin = ContentUris.parseId(contactLookupUri);
        Intent intent = new Intent(ContactsListActivity.JOIN_AGGREGATE);
        intent.putExtra(ContactsListActivity.EXTRA_AGGREGATE_ID, mContactIdForJoin);
        startActivityForResult(intent, REQUEST_JOIN_CONTACT);
    }

    private interface JoinContactQuery {
        String[] PROJECTION = {
                RawContacts._ID,
                RawContacts.CONTACT_ID,
                RawContacts.NAME_VERIFIED,
        };

        String SELECTION = RawContacts.CONTACT_ID + "=? OR " + RawContacts.CONTACT_ID + "=?";

        int _ID = 0;
        int CONTACT_ID = 1;
        int NAME_VERIFIED = 2;
    }

    /**
     * Performs aggregation with the contact selected by the user from suggestions or A-Z list.
     */
    private void joinAggregate(final long contactId) {
        ContentResolver resolver = getContentResolver();

        // Load raw contact IDs for all raw contacts involved - currently edited and selected
        // in the join UIs
        Cursor c = resolver.query(RawContacts.CONTENT_URI,
                JoinContactQuery.PROJECTION,
                JoinContactQuery.SELECTION,
                new String[]{String.valueOf(contactId), String.valueOf(mContactIdForJoin)}, null);

        long rawContactIds[];
        long verifiedNameRawContactId = -1;
        try {
            rawContactIds = new long[c.getCount()];
            for (int i = 0; i < rawContactIds.length; i++) {
                c.moveToNext();
                long rawContactId = c.getLong(JoinContactQuery._ID);
                rawContactIds[i] = rawContactId;
                if (c.getLong(JoinContactQuery.CONTACT_ID) == mContactIdForJoin) {
                    if (verifiedNameRawContactId == -1
                            || c.getInt(JoinContactQuery.NAME_VERIFIED) != 0) {
                        verifiedNameRawContactId = rawContactId;
                    }
                }
            }
        } finally {
            c.close();
        }

        // For each pair of raw contacts, insert an aggregation exception
        ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
        for (int i = 0; i < rawContactIds.length; i++) {
            for (int j = 0; j < rawContactIds.length; j++) {
                if (i != j) {
                    buildJoinContactDiff(operations, rawContactIds[i], rawContactIds[j]);
                }
            }
        }

        // Mark the original contact as "name verified" to make sure that the contact
        // display name does not change as a result of the join
        Builder builder = ContentProviderOperation.newUpdate(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, verifiedNameRawContactId));
        builder.withValue(RawContacts.NAME_VERIFIED, 1);
        operations.add(builder.build());

        // Apply all aggregation exceptions as one batch
        try {
            getContentResolver().applyBatch(ContactsContract.AUTHORITY, operations);

            // We can use any of the constituent raw contacts to refresh the UI - why not the first
            Intent intent = new Intent();
            intent.setData(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactIds[0]));

            // Reload the new state from database
            new QueryEntitiesTask(this).execute(intent);

            Toast.makeText(this, R.string.contactsJoinedMessage, Toast.LENGTH_LONG).show();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            Toast.makeText(this, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
        } catch (OperationApplicationException e) {
            Log.e(TAG, "Failed to apply aggregation exception batch", e);
            Toast.makeText(this, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Construct a {@link AggregationExceptions#TYPE_KEEP_TOGETHER} ContentProviderOperation.
     */
    private void buildJoinContactDiff(ArrayList<ContentProviderOperation> operations,
            long rawContactId1, long rawContactId2) {
        Builder builder =
                ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI);
        builder.withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID1, rawContactId1);
        builder.withValue(AggregationExceptions.RAW_CONTACT_ID2, rawContactId2);
        operations.add(builder.build());
    }

    /**
     * Revert any changes the user has made, and finish the activity.
     */
    private boolean doRevertAction() {
        finish();
        return true;
    }

    /**
     * Create a new {@link RawContacts} which will exist as another
     * {@link EntityDelta} under the currently edited {@link Contacts}.
     */
    private boolean doAddAction() {
        if (mStatus != STATUS_EDITING) {
            return false;
        }

        // Adding is okay when missing state
        new AddContactTask(this).execute();
        return true;
    }

    /**
     * Delete the entire contact currently being edited, which usually asks for
     * user confirmation before continuing.
     */
    private boolean doDeleteAction() {
        if (!hasValidState())
            return false;
        int readOnlySourcesCnt = 0;
        int writableSourcesCnt = 0;
        Sources sources = Sources.getInstance(EditContactActivity.this);
        for (EntityDelta delta : mState) {
            final String accountType = delta.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
            final ContactsSource contactsSource = sources.getInflatedSource(accountType,
                    ContactsSource.LEVEL_CONSTRAINTS);
            if (contactsSource != null && contactsSource.readOnly) {
                readOnlySourcesCnt += 1;
            } else {
                writableSourcesCnt += 1;
            }
        }

        if (readOnlySourcesCnt > 0 && writableSourcesCnt > 0) {
            showDialog(DIALOG_CONFIRM_READONLY_DELETE);
        } else if (readOnlySourcesCnt > 0 && writableSourcesCnt == 0) {
            showDialog(DIALOG_CONFIRM_READONLY_HIDE);
        } else if (readOnlySourcesCnt == 0 && writableSourcesCnt > 1) {
            showDialog(DIALOG_CONFIRM_MULTIPLE_DELETE);
        } else {
            showDialog(DIALOG_CONFIRM_DELETE);
        }
        return true;
    }

    /**
     * Pick a specific photo to be added under the currently selected tab.
     */
    boolean doPickPhotoAction(long rawContactId) {
        if (!hasValidState()) return false;

        mRawContactIdRequestingPhoto = rawContactId;

        showAndManageDialog(createPickPhotoDialog());

        return true;
    }

    /**
     * Creates a dialog offering two options: take a photo or pick a photo from the gallery.
     */
    private Dialog createPickPhotoDialog() {
        Context context = EditContactActivity.this;

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(context,
                android.R.style.Theme_Light);

        String[] choices;
        choices = new String[2];
        choices[0] = getString(R.string.take_photo);
        choices[1] = getString(R.string.pick_photo);
        final ListAdapter adapter = new ArrayAdapter<String>(dialogContext,
                android.R.layout.simple_list_item_1, choices);

        final AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
        builder.setTitle(R.string.attachToContact);
        builder.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                switch(which) {
                    case 0:
                        doTakePhoto();
                        break;
                    case 1:
                        doPickPhotoFromGallery();
                        break;
                }
            }
        });
        return builder.create();
    }

    /**
     * Create a file name for the icon photo using current time.
     */
    private String getPhotoFileName() {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat dateFormat = new SimpleDateFormat("'IMG'_yyyyMMdd_HHmmss");
        return dateFormat.format(date) + ".jpg";
    }

    /**
     * Launches Camera to take a picture and store it in a file.
     */
    protected void doTakePhoto() {
        try {
            // Launch camera to take photo for selected contact
            PHOTO_DIR.mkdirs();
            mCurrentPhotoFile = new File(PHOTO_DIR, getPhotoFileName());
            final Intent intent = getTakePickIntent(mCurrentPhotoFile);
            startActivityForResult(intent, CAMERA_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Constructs an intent for capturing a photo and storing it in a temporary file.
     */
    public static Intent getTakePickIntent(File f) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE, null);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(f));
        return intent;
    }

    /**
     * Sends a newly acquired photo to Gallery for cropping
     */
    protected void doCropPhoto(File f) {
        try {

            // Add the image to the media store
            MediaScannerConnection.scanFile(
                    this,
                    new String[] { f.getAbsolutePath() },
                    new String[] { null },
                    null);

            // Launch gallery to crop the photo
            final Intent intent = getCropImageIntent(Uri.fromFile(f));
            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (Exception e) {
            Log.e(TAG, "Cannot crop image", e);
            Toast.makeText(this, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Constructs an intent for image cropping.
     */
    public static Intent getCropImageIntent(Uri photoUri) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(photoUri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", ICON_SIZE);
        intent.putExtra("outputY", ICON_SIZE);
        intent.putExtra("return-data", true);
        return intent;
    }

    /**
     * Launches Gallery to pick a photo.
     */
    protected void doPickPhotoFromGallery() {
        try {
            // Launch picker to choose photo for selected contact
            final Intent intent = getPhotoPickIntent();
            startActivityForResult(intent, PHOTO_PICKED_WITH_DATA);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.photoPickerNotFoundText, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Constructs an intent for picking a photo from Gallery, cropping it and returning the bitmap.
     */
    public static Intent getPhotoPickIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT, null);
        intent.setType("image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 1);
        intent.putExtra("aspectY", 1);
        intent.putExtra("outputX", ICON_SIZE);
        intent.putExtra("outputY", ICON_SIZE);
        intent.putExtra("return-data", true);
        return intent;
    }

    /** {@inheritDoc} */
    public void onDeleted(Editor editor) {
        // Ignore any editor deletes
    }

    private boolean doSplitContactAction() {
        if (!hasValidState()) return false;

        showAndManageDialog(createSplitDialog());
        return true;
    }

    private Dialog createSplitDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.splitConfirmation_title);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setMessage(R.string.splitConfirmation);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // Split the contacts
                mState.splitRawContacts();
                doSaveAction(SAVE_MODE_SPLIT);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.setCancelable(false);
        return builder.create();
    }

    private boolean doJoinContactAction() {
        return doSaveAction(SAVE_MODE_JOIN);
    }

    /**
     * Build dialog that handles adding a new {@link RawContacts} after the user
     * picks a specific {@link ContactsSource}.
     */
    private static class AddContactTask extends
            WeakAsyncTask<Void, Void, ArrayList<Account>, EditContactActivity> {

        public AddContactTask(EditContactActivity target) {
            super(target);
        }

        @Override
        protected ArrayList<Account> doInBackground(final EditContactActivity target,
                Void... params) {
            return Sources.getInstance(target).getAccounts(true);
        }

        @Override
        protected void onPostExecute(final EditContactActivity target, ArrayList<Account> accounts) {
            target.selectAccountAndCreateContact(accounts);
        }
    }

    public void selectAccountAndCreateContact(ArrayList<Account> accounts) {
        // No Accounts available.  Create a phone-local contact.
        if (accounts.isEmpty()) {
            createContact(null);
            return;  // Don't show a dialog.
        }

        // In the common case of a single account being writable, auto-select
        // it without showing a dialog.
        if (accounts.size() == 1) {
            createContact(accounts.get(0));
            return;  // Don't show a dialog.
        }

        // Wrap our context to inflate list items using correct theme
        final Context dialogContext = new ContextThemeWrapper(this, android.R.style.Theme_Light);
        final LayoutInflater dialogInflater =
            (LayoutInflater)dialogContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        final Sources sources = Sources.getInstance(this);

        final ArrayAdapter<Account> accountAdapter = new ArrayAdapter<Account>(this,
                android.R.layout.simple_list_item_2, accounts) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = dialogInflater.inflate(android.R.layout.simple_list_item_2,
                            parent, false);
                }

                // TODO: show icon along with title
                final TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
                final TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

                final Account account = this.getItem(position);
                final ContactsSource source = sources.getInflatedSource(account.type,
                        ContactsSource.LEVEL_SUMMARY);

                text1.setText(account.name);
                text2.setText(source.getDisplayLabel(EditContactActivity.this));

                return convertView;
            }
        };

        final DialogInterface.OnClickListener clickListener =
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                // Create new contact based on selected source
                final Account account = accountAdapter.getItem(which);
                createContact(account);
            }
        };

        final DialogInterface.OnCancelListener cancelListener =
                new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                // If nothing remains, close activity
                if (!hasValidState()) {
                    finish();
                }
            }
        };

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.dialog_new_contact_account);
        builder.setSingleChoiceItems(accountAdapter, 0, clickListener);
        builder.setOnCancelListener(cancelListener);
        showAndManageDialog(builder.create());
    }

    /**
     * @param account may be null to signal a device-local contact should
     *     be created.
     */
    private void createContact(Account account) {
        final Sources sources = Sources.getInstance(this);
        final ContentValues values = new ContentValues();
        if (account != null) {
            values.put(RawContacts.ACCOUNT_NAME, account.name);
            values.put(RawContacts.ACCOUNT_TYPE, account.type);
        } else {
            values.putNull(RawContacts.ACCOUNT_NAME);
            values.putNull(RawContacts.ACCOUNT_TYPE);
        }

        // Parse any values from incoming intent
        EntityDelta insert = new EntityDelta(ValuesDelta.fromAfter(values));
        final ContactsSource source = sources.getInflatedSource(
            account != null ? account.type : null,
            ContactsSource.LEVEL_CONSTRAINTS);
        final Bundle extras = getIntent().getExtras();
        EntityModifier.parseExtras(this, source, insert, extras);

        // Ensure we have some default fields
        EntityModifier.ensureKindExists(insert, source, Phone.CONTENT_ITEM_TYPE);
        EntityModifier.ensureKindExists(insert, source, Email.CONTENT_ITEM_TYPE);

        // Create "My Contacts" membership for Google contacts
        // TODO: move this off into "templates" for each given source
        if (GoogleSource.ACCOUNT_TYPE.equals(source.accountType)) {
            GoogleSource.attemptMyContactsMembership(insert, this);
        }

        if (mState == null) {
            // Create state if none exists yet
            mState = EntitySet.fromSingle(insert);
        } else {
            // Add contact onto end of existing state
            mState.add(insert);
        }

        bindEditors();
    }

    /**
     * Compare EntityDeltas for sorting the stack of editors.
     */
    public int compare(EntityDelta one, EntityDelta two) {
        // Check direct equality
        if (one.equals(two)) {
            return 0;
        }

        final Sources sources = Sources.getInstance(this);
        String accountType = one.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        final ContactsSource oneSource = sources.getInflatedSource(accountType,
                ContactsSource.LEVEL_SUMMARY);
        accountType = two.getValues().getAsString(RawContacts.ACCOUNT_TYPE);
        final ContactsSource twoSource = sources.getInflatedSource(accountType,
                ContactsSource.LEVEL_SUMMARY);

        // Check read-only
        if (oneSource.readOnly && !twoSource.readOnly) {
            return 1;
        } else if (twoSource.readOnly && !oneSource.readOnly) {
            return -1;
        }

        // Check account type
        boolean skipAccountTypeCheck = false;
        boolean oneIsGoogle = oneSource instanceof GoogleSource;
        boolean twoIsGoogle = twoSource instanceof GoogleSource;
        if (oneIsGoogle && !twoIsGoogle) {
            return -1;
        } else if (twoIsGoogle && !oneIsGoogle) {
            return 1;
        } else if (oneIsGoogle && twoIsGoogle){
            skipAccountTypeCheck = true;
        }

        int value;
        if (!skipAccountTypeCheck) {
            if (oneSource.accountType == null) {
                return 1;
            }
            value = oneSource.accountType.compareTo(twoSource.accountType);
            if (value != 0) {
                return value;
            }
        }

        // Check account name
        ValuesDelta oneValues = one.getValues();
        String oneAccount = oneValues.getAsString(RawContacts.ACCOUNT_NAME);
        if (oneAccount == null) oneAccount = "";
        ValuesDelta twoValues = two.getValues();
        String twoAccount = twoValues.getAsString(RawContacts.ACCOUNT_NAME);
        if (twoAccount == null) twoAccount = "";
        value = oneAccount.compareTo(twoAccount);
        if (value != 0) {
            return value;
        }

        // Both are in the same account, fall back to contact ID
        Long oneId = oneValues.getAsLong(RawContacts._ID);
        Long twoId = twoValues.getAsLong(RawContacts._ID);
        if (oneId == null) {
            return -1;
        } else if (twoId == null) {
            return 1;
        }

        return (int)(oneId - twoId);
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
