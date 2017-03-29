/**
 * Copyright (C) 2013-2017, The Linux Foundation. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.activities;

import android.accounts.Account;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.ContactUtils;
import com.android.contacts.SimContactsConstants;
import com.android.contacts.SimContactsOperation;
import com.android.contacts.activities.RequestPermissionsActivity;
import com.android.contacts.list.ContactsFragment;
import com.android.contacts.list.ContactsPickMode;
import com.android.contacts.list.OnCheckListActionListener;
import com.android.contacts.R;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class MultiPickContactsActivity extends Activity implements
        OnCheckListActionListener, View.OnClickListener, View.OnFocusChangeListener {

    private final static String TAG = "MultiPickContactsActivity";
    private final static boolean DEBUG = true;

    private ContactsPickMode mPickMode;
    private int mSelectedNums = 0;
    private ContactsFragment mContactsFragment;
    private boolean mSearchUiVisible = false;
    // contains data ids
    private Bundle mChoiceSet;
    private Bundle mBackupChoiceSet;
    private TextView mOKButton;
    private ActionBar mActionBar;
    private EditText mSearchView;
    private ViewGroup mSearchViewContainer;
    private View mSelectionContainer;
    private Button mSelectionButton;
    private MenuItem searchItem;
    private SelectionMenu mSelectionMenu;
    private Context mContext;
    private ProgressDialog mProgressDialog;
    private SimContactsOperation mSimContactsOperation;
    private boolean mDelete = false;
    private int mExportSub = -1;

    private static final int ACCOUNT_TYPE_COLUMN_ID = 5;
    private static final int ACCOUNT_NAME_COLUMN_ID = 6;
    // reduce the value to avoid too large transaction.

    private static final int BUFFER_LENGTH = 400;

    private static final int TOAST_EXPORT_FINISHED = 0;
    // only for sim card is full
    private static final int TOAST_SIM_CARD_FULL = 1;
    // there is a case export is canceled by user
    private static final int TOAST_EXPORT_CANCELED = 2;
    // only for not have phone number or email address
    private static final int TOAST_EXPORT_NO_PHONE_OR_EMAIL = 3;
    // only for export failed in exporting progress
    private static final int TOAST_SIM_EXPORT_FAILED = 4;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (RequestPermissionsActivity.startPermissionActivityIfNeeded(this)) {
            return;
        }

        setContentView(R.layout.multi_pick_activity);
        mChoiceSet = new Bundle();
        mContext = getApplicationContext();
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        mContactsFragment = (ContactsFragment) fragmentManager
                .findFragmentByTag("tab-contacts");
        if (mContactsFragment == null) {
            mContactsFragment = new ContactsFragment();
            transaction.add(R.id.pick_layout, mContactsFragment, "tab-contacts");
        }
        mContactsFragment.setCheckListListener(this);
        transaction.commitAllowingStateLoss();
        mActionBar = getActionBar();
        mActionBar.setDisplayShowHomeEnabled(true);
        mActionBar.setDisplayHomeAsUpEnabled(true);
        mActionBar.setTitle(null);
        mPickMode = ContactsPickMode.getInstance();
        mPickMode.setMode(getIntent());
        mDelete = getIntent().getBooleanExtra("delete", false);
        mExportSub = getIntent().getIntExtra("exportSub", -1);
        inflateSearchView();
        mSimContactsOperation = new SimContactsOperation(this);
        initResource();
        mActionBar.setElevation(4 * getResources().getDisplayMetrics().density);
    }

    private void initResource() {
        mOKButton = (TextView) findViewById(R.id.btn_ok);
        mOKButton.setOnClickListener(this);
        setOkStatus();
    }

    private void inflateSearchView() {
        LayoutInflater inflater = LayoutInflater.from(mActionBar.getThemedContext());
        mSearchViewContainer = (ViewGroup) inflater.inflate(R.layout.custom_pick_action_bar, null);
        mSearchView = (EditText) mSearchViewContainer.findViewById(R.id.search_view);
        mSearchView.setHintTextColor(getColor(R.color.searchbox_phone_hint_text_color));
        mSearchView.setTextColor(getColor(R.color.searchbox_phone_text_color));
        mSelectionContainer = inflater.inflate(R.layout.action_mode, null);
        mSelectionButton = (Button) mSelectionContainer.findViewById(R.id.selection_menu);
        mSelectionButton.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        String countTitle = mContext.getResources().getString(R.string.contacts_selected,
                mSelectedNums);
        mSelectionButton.setText(countTitle);
        mSelectionButton.setElevation(4 * getResources().getDisplayMetrics().density);
        // Setup selection bar
        if (mSelectionMenu == null) {
            mSelectionMenu = new SelectionMenu(this, mSelectionButton,
                    new PopupList.OnPopupItemClickListener() {
                        @Override
                        public boolean onPopupItemClick(int itemId) {
                            if (itemId == SelectionMenu.SELECT_OR_DESELECT) {
                                setAllSelected();
                            }
                            return true;
                        }
                    });
            mSelectionMenu.getPopupList().addItem(
                    SelectionMenu.SELECT_OR_DESELECT,
                    getString(R.string.menu_select_all));
        }
        mActionBar.setDisplayShowCustomEnabled(true);
        configureSearchMode();
        mSearchView.setHint(getString(R.string.enter_contact_name));
        mSearchView.setFocusable(true);
        mSearchView.setOnFocusChangeListener(this);
        mSearchView.addTextChangedListener(new SearchTextWatcher());
    }

    private class SearchTextWatcher implements TextWatcher {
        @Override
        public void onTextChanged(CharSequence queryString, int start, int before, int count) {
            updateState(queryString.toString());
        }

        @Override
        public void afterTextChanged(Editable s) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }
    }

    private void setAllSelected() {
        boolean selectAll = false;
        int checkNum = mChoiceSet.size();
        int num = mContactsFragment.getListView().getCount() - 1;
        if (checkNum < num) {
            selectAll = true;
        }
        mContactsFragment.setSelectedAll(selectAll);
    }

    private void addSelectionMenuPopupListItem(String countTitle) {
        mSelectionMenu.getPopupList().addItem(SelectionMenu.SELECTED, countTitle);
        boolean selectAll = true;
        int checkNum = mChoiceSet.size();
        int num = mContactsFragment.getListView().getCount() - 1;
        if (checkNum == num && num > 0) {
            selectAll = false;
        }
        mSelectionMenu.getPopupList().addItem(
                SelectionMenu.SELECT_OR_DESELECT,
                getString(selectAll ? R.string.menu_select_all
                        : R.string.menu_select_none));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.search_menu, menu);
        searchItem = menu.findItem(R.id.menu_search);
        searchItem.setVisible(!mSearchUiVisible);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_search:
                mSearchUiVisible = true;
                enterSearchMode();
                configureSearchMode();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void configureSearchMode() {
        TextView topDividerLine = (TextView) findViewById(R.id.multi_pick_top_divider);
        if (mSearchUiVisible) {
            topDividerLine.setVisibility(View.VISIBLE);
            mActionBar.setHomeAsUpIndicator(R.drawable.quantum_ic_arrow_back_vd_theme_24);
            int searchboxStatusBarColor = getColor(R.color.searchbox_phone_background_color);
            ColorDrawable searchboxStatusBarDrawable = new ColorDrawable(searchboxStatusBarColor);
            mActionBar.setBackgroundDrawable(searchboxStatusBarDrawable);
            mSelectionContainer.setVisibility(View.GONE);
            mSearchViewContainer.setVisibility(View.VISIBLE);
            mActionBar.setCustomView(mSearchViewContainer, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT));
            mSearchView.requestFocus();
        } else {
            topDividerLine.setVisibility(View.GONE);
            mActionBar.setHomeAsUpIndicator(null);
            int normalStatusBarColor = getColor(R.color.primary_color);
            getActionBar().setBackgroundDrawable(new ColorDrawable(normalStatusBarColor));
            mSearchViewContainer.setVisibility(View.GONE);
            mSelectionContainer.setVisibility(View.VISIBLE);
            mActionBar.setCustomView(mSelectionContainer, new ActionBar.LayoutParams(
                    ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT));
            if (mSearchView != null && !TextUtils.isEmpty(mSearchView.getText().toString())) {
                mSearchView.setText(null);
            }
        }
    }

    private void updateActionBar() {
        mSelectedNums = (mChoiceSet.isEmpty() ? 0 : mChoiceSet.size());
        String countTitle = mContext.getResources().getString(R.string.contacts_selected,
                mSelectedNums);
        mSelectionButton.setText(countTitle);
        mSelectionMenu.getPopupList().clearItems();
        addSelectionMenuPopupListItem(countTitle);
        invalidateOptionsMenu();
    }

    @Override
    public void onBackPressed() {
        if (mSearchUiVisible) {
            mSearchUiVisible = false;
            exitSearchMode();
            configureSearchMode();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onFocusChange(View view, boolean hasFocus) {
        switch (view.getId()) {
            case R.id.search_view: {
                if (hasFocus) {
                    final InputMethodManager imm = (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    imm.showSoftInput(mSearchView.findFocus(), 0);
                    updateState(null);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mProgressDialog != null) {
            mProgressDialog.cancel();
        }
        super.onDestroy();
    }

    private void updateState(String query) {
        if (!TextUtils.isEmpty(query)) {
            if (!mPickMode.isSearchMode()) {
                mSearchUiVisible = true;
                enterSearchMode();
                configureSearchMode();
            }
        }
        mContactsFragment.doFilter(query);
    }

    private void setOkStatus() {
        if (0 == mChoiceSet.size()) {
            mOKButton.setEnabled(false);
            mOKButton.setTextColor(
                    mContext.getResources().getColor(R.color.ok_or_clear_button_disable_color));
        } else {
            mOKButton.setEnabled(true);
            mOKButton.setTextColor(
                    mContext.getResources().getColor(R.color.ok_or_clear_button_normal_color));
        }
    }

    private void enterSearchMode() {
        mOKButton.setVisibility(View.GONE);
        searchItem.setVisible(false);
        mPickMode.enterSearchMode();
    }

    private void exitSearchMode() {
        mOKButton.setVisibility(View.VISIBLE);
        searchItem.setVisible(true);
        mPickMode.exitSearchMode();
        mContactsFragment.startQuery();
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle bundle) {
        switch (id) {
            case R.id.dialog_delete_contact_confirmation: {
                return new AlertDialog.Builder(this)
                    .setTitle(R.string.deleteConfirmation_title)
                    .setIconAttribute(android.R.attr.alertDialogIcon)
                    .setMessage(R.string.ContactMultiDeleteConfirmation)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok,
                            new DeleteClickListener()).create();
            }

        }
        return super.onCreateDialog(id, bundle);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        switch (id) {
            case R.id.btn_ok:
                if (mPickMode.isSearchMode()) {
                    exitSearchMode();
                }
                if (mDelete) {
                    showDialog(R.id.dialog_delete_contact_confirmation);
                } else if(mExportSub > -1) {
                    new ExportToSimThread().start();
                } else {
                    Intent intent = new Intent();
                    Bundle bundle = new Bundle();
                    bundle.putBundle(SimContactsConstants.RESULT_KEY, mChoiceSet);
                    intent.putExtras(bundle);
                    this.setResult(RESULT_OK, intent);
                    finish();
                }
                break;
        }
    }

    private void hideSoftKeyboard() {
        // Hide soft keyboard, if visible
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(mSearchView.getWindowToken(), 0);
    }

    protected static void log(String msg) {
        if (DEBUG)
            Log.d(TAG, msg);
    }

    private class DeleteClickListener implements DialogInterface.OnClickListener {
        public void onClick(DialogInterface dialog, int which) {
            CharSequence title = getString(R.string.delete_contacts_title);
            CharSequence message = getString(R.string.delete_contacts_message);
            Thread thread = new DeleteContactsThread();

            DialogInterface.OnKeyListener keyListener = new DialogInterface.OnKeyListener() {
                public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                    switch (keyCode) {
                        case KeyEvent.KEYCODE_SEARCH:
                        case KeyEvent.KEYCODE_CALL:
                            return true;
                        default:
                            return false;
                    }
                }
            };

            mProgressDialog = new ProgressDialog(MultiPickContactsActivity.this);
            mProgressDialog.setTitle(title);
            mProgressDialog.setMessage(message);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    getString(R.string.btn_cancel), (OnClickListener) thread);
            mProgressDialog.setOnCancelListener((OnCancelListener) thread);
            mProgressDialog.setOnKeyListener(keyListener);
            mProgressDialog.setProgress(0);
            mProgressDialog.setMax(mChoiceSet.size());

            // set dialog can not be canceled by touching outside area of
            // dialog.
            mProgressDialog.setCanceledOnTouchOutside(false);
            mProgressDialog.show();

            mOKButton.setEnabled(false);
            mOKButton.setTextColor(mContext.getResources().getColor(
                    R.color.ok_or_clear_button_disable_color));

            thread.start();
        }
    }

    /**
     * Delete contacts thread
     */
    public class DeleteContactsThread extends Thread
            implements DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
        boolean mCanceled = false;
        // Use to save the operated contacts.
        private ArrayList<ContentProviderOperation> mOpsContacts = null;
        public DeleteContactsThread() {
        }

        @Override
        public void run() {
            Bundle choiceSet = (Bundle) mChoiceSet.clone();
            Set<String> keySet = choiceSet.keySet();
            Iterator<String> iterator = keySet.iterator();
            ContentProviderOperation cpo = null;
            ContentProviderOperation.Builder builder = null;
            // Current contact count we can delete.
            int count = 0;
            // The contacts we batch delete once.
            final int BATCH_DELETE_CONTACT_NUMBER = 400;
            mOpsContacts = new ArrayList<ContentProviderOperation>();
            while (!mCanceled & iterator.hasNext()) {
                String id = String.valueOf(iterator.next());
                String[] value = choiceSet.getStringArray(id);
                long longId = Long.parseLong(id);
                String accountType = value[ACCOUNT_TYPE_COLUMN_ID];
                String accountName = value[ACCOUNT_NAME_COLUMN_ID];
                Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI, id);
                //delete sim contacts first
                if (accountType != null && accountType
                                .equals(SimContactsConstants.ACCOUNT_TYPE_SIM)) {
                    int subscription = ContactUtils.getSubscription(
                            accountType, accountName);
                    ContentValues values = mSimContactsOperation
                            .getSimAccountValues(longId);
                    int result = mSimContactsOperation.delete(values,
                            subscription);
                    if (result == 0) {
                        mProgressDialog.incrementProgressBy(1);
                        continue;
                    }
                }
                builder = ContentProviderOperation.newDelete(uri);
                cpo = builder.build();
                mOpsContacts.add(cpo);
                mProgressDialog.incrementProgressBy(1);
                if (count % BATCH_DELETE_CONTACT_NUMBER == 0) {
                    batchDelete();
                }
                count++;
            }
            batchDelete();
            mOpsContacts = null;
            finish();
        }

        /**
         * Batch delete contacts more efficient than one by one.
         */
        private void batchDelete() {
            try {
                mContext.getContentResolver().applyBatch(
                        android.provider.ContactsContract.AUTHORITY, mOpsContacts);
                mOpsContacts.clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void onCancel(DialogInterface dialogInterface) {
            // Cancel delete operate.
            mCanceled = true;
            Toast.makeText(mContext, R.string.delete_termination, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            if (i == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
            }
        }
    }

    /**
     * A thread that export contacts to sim card
     */
    public class ExportToSimThread extends Thread {
        private int slot;
        private boolean canceled = false;
        private int freeSimCount = 0;
        private ArrayList<ContentProviderOperation> operationList =
                new ArrayList<ContentProviderOperation>();
        private Account account;
        final int BATCH_INSERT_NUMBER = 400;

        public ExportToSimThread() {
            slot = ContactUtils.getActiveSlotId(mContext, mExportSub);
            account = ContactUtils.getAcount(mContext, slot);
            showExportProgressDialog();
        }

        @Override
        public void run() {
            boolean isSimCardFull = false;
            // in case export is stopped, record the count of inserted
            // successfully
            int insertCount = 0;
            freeSimCount = ContactUtils.getSimFreeCount(mContext, slot);
            boolean canSaveAnr = ContactUtils.canSaveAnr(mContext, slot);
            boolean canSaveEmail = ContactUtils.canSaveEmail(mContext, slot);
            int emailCountInOneSimContact = ContactUtils
                    .getOneSimEmailCount(mContext, slot);
            int phoneCountInOneSimContact = ContactUtils.getOneSimAnrCount(
                    mContext, slot) + 1;
            int emptyAnr = ContactUtils.getSpareAnrCount(mContext, slot);
            int emptyEmail = ContactUtils.getSpareEmailCount(mContext, slot);
            int emptyNumber = freeSimCount + emptyAnr;

            Log.d(TAG, "freeSimCount = " + freeSimCount);
            Bundle choiceSet = (Bundle) mChoiceSet.clone();
            Set<String> set = choiceSet.keySet();
            Iterator<String> i = set.iterator();
            while (i.hasNext() && !canceled) {
                String id = String.valueOf(i.next());
                String name = "";
                ArrayList<String> arrayNumber = new ArrayList<String>();
                ArrayList<String> arrayEmail = new ArrayList<String>();

                Uri dataUri = Uri.withAppendedPath(
                        ContentUris.withAppendedId(Contacts.CONTENT_URI,
                                Long.parseLong(id)),
                        Contacts.Data.CONTENT_DIRECTORY);
                final String[] projection = new String[] { Contacts._ID,
                        Contacts.Data.MIMETYPE, Contacts.Data.DATA1, };
                Cursor c = mContext.getContentResolver().query(dataUri,
                        projection, null, null, null);
                try {
                    if (c != null && c.moveToFirst()) {
                        do {
                            String mimeType = c.getString(1);
                            if (StructuredName.CONTENT_ITEM_TYPE
                                    .equals(mimeType)) {
                                name = c.getString(2);
                            }
                            if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                String number = c.getString(2);
                                if (!TextUtils.isEmpty(number)
                                        && emptyNumber-- > 0) {
                                    arrayNumber.add(number);
                                }
                            }
                            if (canSaveEmail) {
                                if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                                    String email = c.getString(2);
                                    if (!TextUtils.isEmpty(email)
                                            && emptyEmail-- > 0) {
                                        arrayEmail.add(email);
                                    }
                                }
                            }
                        } while (c.moveToNext());
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                if (freeSimCount > 0 && 0 == arrayNumber.size()
                        && 0 == arrayEmail.size()) {
                    mToastHandler.sendMessage(mToastHandler.obtainMessage(
                            TOAST_EXPORT_NO_PHONE_OR_EMAIL, name));
                    continue;
                }

                int nameCount = (name != null && !name.equals("")) ? 1 : 0;
                int groupNumCount = (arrayNumber.size() % phoneCountInOneSimContact) != 0 ?
                        (arrayNumber.size() / phoneCountInOneSimContact + 1)
                        : (arrayNumber.size() / phoneCountInOneSimContact);
                int groupEmailCount = emailCountInOneSimContact == 0 ? 0
                        : ((arrayEmail.size() % emailCountInOneSimContact) != 0 ? (arrayEmail
                                .size() / emailCountInOneSimContact + 1)
                                : (arrayEmail.size() / emailCountInOneSimContact));
                // recalute the group when spare anr is not enough
                if (canSaveAnr && emptyAnr >= 0 && emptyAnr <= groupNumCount) {
                    groupNumCount = arrayNumber.size() - emptyAnr;
                }
                int groupCount = Math.max(groupEmailCount,
                        Math.max(nameCount, groupNumCount));

                Uri result = null;
                if (DEBUG) {
                    Log.d(TAG, "GroupCount = " + groupCount);
                }
                for (int k = 0; k < groupCount; k++) {
                    if (freeSimCount > 0) {
                        String num = arrayNumber.size() > 0 ? arrayNumber
                                .remove(0) : null;
                        StringBuilder anrNum = new StringBuilder();
                        StringBuilder email = new StringBuilder();
                        if (canSaveAnr) {
                            for (int j = 1; j < phoneCountInOneSimContact; j++) {
                                if (arrayNumber.size() > 0 && emptyAnr-- > 0) {
                                    String s = arrayNumber.remove(0);
                                    anrNum.append(s);
                                    anrNum.append(SimContactsConstants.ANR_SEP);
                                }
                            }
                        }
                        if (canSaveEmail) {
                            for (int j = 0; j < emailCountInOneSimContact; j++) {
                                if (arrayEmail.size() > 0) {
                                    String s = arrayEmail.remove(0);
                                    email.append(s);
                                    email.append(SimContactsConstants.EMAIL_SEP);
                                }
                            }
                        }

                        result = ContactUtils.insertToCard(mContext, name,
                                num, email.toString(), anrNum.toString(),
                                slot, false);

                        if (null == result) {
                            // Failed to insert to SIM card
                            int anrNumber = 0;
                            if (!TextUtils.isEmpty(anrNum)) {
                                anrNumber += anrNum.toString().split(
                                        SimContactsConstants.ANR_SEP).length;
                            }
                            // reset emptyNumber and emptyAnr to the value
                            // before
                            // the insert operation
                            emptyAnr += anrNumber;
                            emptyNumber += anrNumber;
                            if (!TextUtils.isEmpty(num)) {
                                emptyNumber++;
                            }

                            if (!TextUtils.isEmpty(email)) {
                                // reset emptyEmail to the value before the
                                // insert
                                // operation
                                emptyEmail += email.toString().split(
                                        SimContactsConstants.EMAIL_SEP).length;
                            }

                            mToastHandler.sendMessage(mToastHandler
                                    .obtainMessage(
                                            TOAST_SIM_EXPORT_FAILED,
                                            new String[] { name, num,
                                                    email.toString() }));

                        } else {
                            if (DEBUG) {
                                Log.d(TAG, "Exported contact [" + name + ", "
                                        + id + "] to slot " + slot);
                            }
                            insertCount++;
                            freeSimCount--;
                            batchInsert(name, num, anrNum.toString(),
                                    email.toString());
                        }
                    } else {
                        isSimCardFull = true;
                        mToastHandler.sendMessage(mToastHandler.obtainMessage(
                                TOAST_SIM_CARD_FULL, insertCount, 0));
                        break;
                    }
                }

                if (isSimCardFull) {
                    break;
                }
            }

            if (operationList.size() > 0) {
                try {
                    mContext.getContentResolver().applyBatch(
                            android.provider.ContactsContract.AUTHORITY,
                            operationList);
                } catch (Exception e) {
                    Log.e(TAG,
                            String.format("%s: %s", e.toString(),
                                    e.getMessage()));
                } finally {
                    operationList.clear();
                }
            }

            if (!isSimCardFull) {
                // if canceled, show toast indicating export is interrupted.
                if (canceled) {
                    mToastHandler.sendMessage(mToastHandler.obtainMessage(TOAST_EXPORT_CANCELED,
                            insertCount, 0));
                } else {
                    mToastHandler.sendEmptyMessage(TOAST_EXPORT_FINISHED);
                }
            }
            finish();
        }

        private void batchInsert(String name, String phoneNumber, String anrs,
                String emailAddresses) {
            final String[] emailAddressArray;
            final String[] anrArray;
            if (!TextUtils.isEmpty(emailAddresses)) {
                emailAddressArray = emailAddresses.split(",");
            } else {
                emailAddressArray = null;
            }
            if (!TextUtils.isEmpty(anrs)) {
                anrArray = anrs.split(SimContactsConstants.ANR_SEP);
            } else {
                anrArray = null;
            }
            Log.d(TAG, "insertToPhone: name= " + name + ", phoneNumber= " + phoneNumber
                    + ", emails= " + emailAddresses + ", anrs= " + anrs + ", account= " + account);
            ContentProviderOperation.Builder builder = ContentProviderOperation
                    .newInsert(RawContacts.CONTENT_URI);
            builder.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED);

            int ref = operationList.size();
            if (account != null) {
                builder.withValue(RawContacts.ACCOUNT_NAME, account.name);
                builder.withValue(RawContacts.ACCOUNT_TYPE, account.type);
            }
            operationList.add(builder.build());
            // do not allow empty value insert into database.
            if (!TextUtils.isEmpty(name)) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(StructuredName.RAW_CONTACT_ID, ref);
                builder.withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
                builder.withValue(StructuredName.GIVEN_NAME, name);
                operationList.add(builder.build());
            }

            if (!TextUtils.isEmpty(phoneNumber)) {
                builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                builder.withValueBackReference(Phone.RAW_CONTACT_ID, ref);
                builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                builder.withValue(Phone.TYPE, Phone.TYPE_MOBILE);
                builder.withValue(Phone.NUMBER, phoneNumber);
                builder.withValue(Data.IS_PRIMARY, 1);
                operationList.add(builder.build());
            }

            if (anrArray != null) {
                for (String anr : anrArray) {
                    if (!TextUtils.isEmpty(anr)) {
                        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                        builder.withValueBackReference(Phone.RAW_CONTACT_ID, ref);
                        builder.withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
                        builder.withValue(Phone.TYPE, Phone.TYPE_HOME);
                        builder.withValue(Phone.NUMBER, anr);
                        operationList.add(builder.build());
                    }
                }
            }

            if (emailAddressArray != null) {
                for (String emailAddress : emailAddressArray) {
                    if (!TextUtils.isEmpty(emailAddress)) {
                        builder = ContentProviderOperation.newInsert(Data.CONTENT_URI);
                        builder.withValueBackReference(Email.RAW_CONTACT_ID, ref);
                        builder.withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
                        builder.withValue(Email.TYPE, Email.TYPE_MOBILE);
                        builder.withValue(Email.ADDRESS, emailAddress);
                        operationList.add(builder.build());
                    }
                }
            }

            if (BATCH_INSERT_NUMBER - operationList.size() < 10) {
                try {
                    mContext.getContentResolver().applyBatch(
                            android.provider.ContactsContract.AUTHORITY,
                            operationList);
                } catch (Exception e) {
                    Log.e(TAG,
                            String.format("%s: %s", e.toString(),
                                    e.getMessage()));
                } finally {
                    operationList.clear();
                }
            }
        }

        private Handler mToastHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                int exportCount = 0;
                switch (msg.what) {
                case TOAST_EXPORT_FINISHED:
                    Toast.makeText(mContext, R.string.export_finished,
                            Toast.LENGTH_SHORT).show();
                    break;
                case TOAST_SIM_CARD_FULL:
                    exportCount = msg.arg1;
                    Toast.makeText(
                            mContext,
                            mContext.getString(
                                    R.string.export_sim_card_full, exportCount),
                            Toast.LENGTH_SHORT).show();
                    break;
                case TOAST_EXPORT_CANCELED:
                    exportCount = msg.arg1;
                    Toast.makeText(
                            mContext,
                            mContext.getString(R.string.export_cancelled,
                                    String.valueOf(exportCount)),
                            Toast.LENGTH_SHORT).show();
                    break;
                case TOAST_EXPORT_NO_PHONE_OR_EMAIL:
                    String name = (String) msg.obj;
                    Toast.makeText(
                            mContext,
                            mContext.getString(
                                    R.string.export_no_phone_or_email, name),
                            Toast.LENGTH_SHORT).show();
                    break;
                case TOAST_SIM_EXPORT_FAILED:
                    String[] contactInfos = (String[]) msg.obj;
                    if (contactInfos != null && contactInfos.length == 3) {
                        String toastS = mContext.getString(
                                R.string.sim_contact_export_failed,
                                contactInfos[0] == null ? "" : contactInfos[0],
                                contactInfos[1] == null ? "" : contactInfos[1],
                                contactInfos[2] == null ? "" : contactInfos[2]);

                        Toast.makeText(mContext, toastS,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                }
            }
        };

        public void showExportProgressDialog(){
            mProgressDialog = new ProgressDialog(MultiPickContactsActivity.this);
            mProgressDialog.setTitle(R.string.export_to_sim);
            mProgressDialog.setOnCancelListener(new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    Log.d(TAG, "Cancel exporting contacts");
                    canceled = true;
                }
            });
            mProgressDialog.setMessage(mContext.getString(R.string.exporting));
            mProgressDialog.setMax(mChoiceSet.size());
            mProgressDialog.setCanceledOnTouchOutside(false);

            // add a cancel button to let user cancel explicitly.
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                    mContext.getString(R.string.progressdialog_cancel),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Log.d(TAG, "Cancel exporting contacts by click button");
                            canceled = true;
                        }
                    });

            mProgressDialog.show();
            mOKButton.setEnabled(false);
            mOKButton.setTextColor(
                    mContext.getResources().getColor(R.color.ok_or_clear_button_disable_color));
        }
    }

    @Override
    public boolean onContainsKey(String key) {
        return mChoiceSet.containsKey(key);
    }

    @Override
    public void putValue(String key, String[] value) {
        mChoiceSet.putStringArray(key, value);
        setOkStatus();
    }

    @Override
    public void onRemove(String key) {
        mChoiceSet.remove(key);
        setOkStatus();
    }

    @Override
    public void onClear() {
        mChoiceSet.clear();
        setOkStatus();
    }

    @Override
    public void onHideSoftKeyboard() {
        hideSoftKeyboard();
    }

    @Override
    public void onUpdateActionBar() {
        updateActionBar();
    }

    @Override
    public void exitSearch() {
        if (mPickMode.isSearchMode()) {
            mSearchUiVisible = false;
            exitSearchMode();
            configureSearchMode();
        }
    }
}
