package com.android.contacts.activities;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.Intent;
import android.content.Loader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.widget.Toast;

import com.android.contacts.AppCompatContactsActivity;
import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.editor.ContactEditorFragment;
import com.android.contacts.editor.EditorIntents;
import com.android.contacts.editor.PickRawContactDialogFragment;
import com.android.contacts.editor.PickRawContactLoader;
import com.android.contacts.editor.PickRawContactLoader.RawContactsMetadata;
import com.android.contacts.editor.SplitContactConfirmationDialogFragment;
import com.android.contacts.logging.EditorEvent;
import com.android.contacts.logging.Logger;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.quickcontact.QuickContactActivity;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.MaterialColorMapUtils.MaterialPalette;
import com.android.contactsbind.FeedbackHelper;

/**
 * Transparent springboard activity that hosts a dialog to select a raw contact to edit.
 * All intents coming out from this activity have {@code FLAG_ACTIVITY_FORWARD_RESULT} set.
 */
public class ContactEditorSpringBoardActivity extends AppCompatContactsActivity implements
        PickRawContactDialogFragment.PickRawContactListener,
        SplitContactConfirmationDialogFragment.Listener {

    private static final String TAG = "EditorSpringBoard";
    private static final String TAG_RAW_CONTACTS_DIALOG = "rawContactsDialog";
    private static final String KEY_RAW_CONTACTS_METADATA = "rawContactsMetadata";
    private static final int LOADER_RAW_CONTACTS = 1;

    public static final String EXTRA_SHOW_READ_ONLY = "showReadOnly";

    private Uri mUri;
    private RawContactsMetadata mResult;
    private MaterialPalette mMaterialPalette;
    private boolean mHasWritableAccount;
    private boolean mShowReadOnly;
    private int mWritableAccountPosition;

    /**
     * The contact data loader listener.
     */
    protected final LoaderManager.LoaderCallbacks<RawContactsMetadata> mRawContactLoaderListener =
            new LoaderManager.LoaderCallbacks<RawContactsMetadata>() {

                @Override
                public Loader<RawContactsMetadata> onCreateLoader(int id, Bundle args) {
                    return new PickRawContactLoader(ContactEditorSpringBoardActivity.this, mUri);
                }

                @Override
                public void onLoadFinished(Loader<RawContactsMetadata> loader,
                        RawContactsMetadata result) {
                    if (result == null) {
                        toastErrorAndFinish();
                        return;
                    }
                    mResult = result;
                    onLoad();
                }

                @Override
                public void onLoaderReset(Loader<RawContactsMetadata> loader) {
                }
            };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (RequestPermissionsActivity.startPermissionActivityIfNeeded(this)) {
            return;
        }

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (!Intent.ACTION_EDIT.equals(action)) {
            finish();
            return;
        }
        // Just for shorter variable names.
        final String primary = ContactEditorFragment.INTENT_EXTRA_MATERIAL_PALETTE_PRIMARY_COLOR;
        final String secondary =
                ContactEditorFragment.INTENT_EXTRA_MATERIAL_PALETTE_SECONDARY_COLOR;
        if (intent.hasExtra(primary) && intent.hasExtra(secondary)) {
            mMaterialPalette = new MaterialPalette(intent.getIntExtra(primary, -1),
                    intent.getIntExtra(secondary, -1));
        }
        mShowReadOnly = intent.getBooleanExtra(EXTRA_SHOW_READ_ONLY, false);

        mUri = intent.getData();
        final String authority = mUri.getAuthority();
        final String type = getContentResolver().getType(mUri);
        // Go straight to editor if we're passed a raw contact Uri.
        if (ContactsContract.AUTHORITY.equals(authority) &&
                RawContacts.CONTENT_ITEM_TYPE.equals(type)) {
            Logger.logEditorEvent(
                    EditorEvent.EventType.SHOW_RAW_CONTACT_PICKER, /* numberRawContacts */ 0);
            final long rawContactId = ContentUris.parseId(mUri);
            startEditorAndForwardExtras(getIntentForRawContact(rawContactId));
        } else if (android.provider.Contacts.AUTHORITY.equals(authority)) {
            // Fail if given a legacy URI.
            FeedbackHelper.sendFeedback(this, TAG,
                    "Legacy Uri was passed to editor.", new IllegalArgumentException());
            toastErrorAndFinish();
        } else {
            getLoaderManager().initLoader(LOADER_RAW_CONTACTS, null, mRawContactLoaderListener);
        }
    }

    @Override
    public void onPickRawContact(long rawContactId) {
        startEditorAndForwardExtras(getIntentForRawContact(rawContactId));
    }

    /**
     * Once the load is finished, decide whether to show the dialog or load the editor directly.
     */
    private void onLoad() {
        maybeTrimReadOnly();
        setHasWritableAccount();
        if (mShowReadOnly || (mResult.rawContacts.size() > 1 && mHasWritableAccount)) {
            showDialog();
        } else {
            loadEditor();
        }
    }

    /**
     * If not configured to show read only raw contact, trim them from the result.
     */
    private void maybeTrimReadOnly() {
        mResult.showReadOnly = mShowReadOnly;
        if (mShowReadOnly) {
            return;
        }

        mResult.trimReadOnly(AccountTypeManager.getInstance(this));
    }

    /**
     * Start the dialog to pick the raw contact to edit.
     */
    private void showDialog() {
        final FragmentManager fm = getFragmentManager();
        final SplitContactConfirmationDialogFragment split =
                (SplitContactConfirmationDialogFragment) fm
                        .findFragmentByTag(SplitContactConfirmationDialogFragment.TAG);
        // If we were showing the split confirmation before show it again.
        if (split != null && split.isAdded()) {
            fm.beginTransaction().show(split).commitAllowingStateLoss();
            return;
        }
        PickRawContactDialogFragment pick = (PickRawContactDialogFragment) fm
                .findFragmentByTag(TAG_RAW_CONTACTS_DIALOG);
        if (pick == null) {
            pick = PickRawContactDialogFragment.getInstance(mResult);
            final FragmentTransaction ft = fm.beginTransaction();
            ft.add(pick, TAG_RAW_CONTACTS_DIALOG);
            // commitAllowingStateLoss is safe in this activity because the fragment entirely
            // depends on the result of the loader. Even if we lose the fragment because the
            // activity was in the background, when it comes back onLoadFinished will be called
            // again which will have all the state the picker needs. This situation should be
            // very rare, since the load should be quick.
            ft.commitAllowingStateLoss();
        }
    }

    /**
     * Starts the editor for the only writable raw contact in the cursor if one exists. Otherwise,
     * the editor is started normally and handles creation of a new writable raw contact.
     */
    private void loadEditor() {
        Logger.logEditorEvent(
                EditorEvent.EventType.SHOW_RAW_CONTACT_PICKER, /* numberRawContacts */ 0);
        final Intent intent;
        if (mHasWritableAccount) {
            intent = getIntentForRawContact(mResult.rawContacts.get(mWritableAccountPosition).id);
        } else {
            // If the contact has only read-only raw contacts, we'll want to let the editor create
            // the writable raw contact for it.
            intent = EditorIntents.createEditContactIntent(this, mUri, mMaterialPalette, -1);
            intent.setClass(this, ContactEditorActivity.class);
        }
        startEditorAndForwardExtras(intent);
    }

    /**
     * Determines if this contact has a writable account.
     */
    private void setHasWritableAccount() {
        mWritableAccountPosition = mResult.getIndexOfFirstWritableAccount(
                AccountTypeManager.getInstance(this));
        mHasWritableAccount = mWritableAccountPosition != -1;
    }

    /**
     * Returns an intent to load the editor for the given raw contact. Sets
     * {@code FLAG_ACTIVITY_FORWARD_RESULT} in case the activity that started us expects a result.
     * @param rawContactId Raw contact to edit
     */
    private Intent getIntentForRawContact(long rawContactId) {
        final Intent intent = EditorIntents.createEditContactIntentForRawContact(
                this, mUri, rawContactId, mMaterialPalette);
        intent.setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        return intent;
    }

    /**
     * Starts the given intent within the app, attaching any extras to it that were passed to us.
     */
    private void startEditorAndForwardExtras(Intent intent) {
        final Bundle extras = getIntent().getExtras();
        if (extras != null) {
            intent.putExtras(extras);
        }
        ImplicitIntentsUtil.startActivityInApp(this, intent);
        finish();
    }

    private void toastErrorAndFinish() {
        Toast.makeText(ContactEditorSpringBoardActivity.this,
                R.string.editor_failed_to_load, Toast.LENGTH_SHORT).show();
        setResult(RESULT_CANCELED, null);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignore failed requests
        if (resultCode != Activity.RESULT_OK) {
            finish();
        }
        if (data != null) {
            final Intent intent = ContactSaveService.createJoinContactsIntent(
                    this, mResult.contactId, ContentUris.parseId(data.getData()),
                    QuickContactActivity.class, Intent.ACTION_VIEW);
            startService(intent);
            finish();
        }
    }

    @Override
    public void onSplitContactConfirmed(boolean hasPendingChanges) {
        final long[][] rawContactIds = getRawContactIds();
        final Intent intent = ContactSaveService.createHardSplitContactIntent(this, rawContactIds);
        startService(intent);
        finish();
    }

    @Override
    public void onSplitContactCanceled() {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_RAW_CONTACTS_METADATA, mResult);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mResult = savedInstanceState.getParcelable(KEY_RAW_CONTACTS_METADATA);
    }

    private long[][] getRawContactIds() {
        final long[][] result = new long[mResult.rawContacts.size()][1];
        for (int i = 0; i < mResult.rawContacts.size(); i++) {
            result[i][0] = mResult.rawContacts.get(i).id;
        }
        return result;
    }
}
