/************************************************************************************
 *
 *  Copyright (C) 2009-2010 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ************************************************************************************/

package com.android.contacts;

import static android.view.Window.PROGRESS_VISIBILITY_OFF;
import static android.view.Window.PROGRESS_VISIBILITY_ON;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextUtils;
import android.text.method.DialerKeyListener;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.Photo;

import android.content.ContentUris;
import android.provider.Telephony.Mms;
import android.content.ContentProviderOperation;
import java.util.ArrayList;
import android.content.OperationApplicationException;
import android.os.RemoteException;
import android.content.AsyncQueryHandler;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;


import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.account.LocalAccountType;
import com.android.contacts.common.SimContactsReadyHelper;
import com.android.contacts.common.BrcmIccUtils;
import com.android.internal.telephony.IccProvider;
import com.android.internal.telephony.RILConstants.SimCardID;

import android.os.SystemProperties;

public class EditAdnContactActivity extends Activity {
    private static final String LOG_TAG = "EditAdnContactActivity";
    private static final boolean DBG = true;

    private boolean mAddContact;
    private boolean mSaveToPhonebook;
    private boolean mSaveToSIM;

    private EditText mNameField;
    private EditText mNumberField;
    private EditText mEmailField;
    private EditText mAnrField;
    private Button mButton;

    private long mRawContactId = -1;
    private int mRecordIndex=-1;
    private int mNameDataId;
    private int mNumberDataId;
    private int mEmailDataId;
    private int mNumberAnrDataId;

    private String mName;
    private String mNumber;
    private String mNumberAnr;
    private String mEmail;
    private String mNewNumber;
    private String mNewName;
    private String mNewNumberAnr;
    private String mNewEmail;
    private SimCardID mSimId;
    private String mNumberToInsert;
    private String mEmailToInsert;
    private Uri mContactUri;

    protected UpdateHandler mIccContactsUpdateHandler;
    private Handler mHandler = new Handler();

    boolean mSupportEmail = false;
    boolean mSupportAnr = false;
    private static final String INTENT_EXTRA_RAWCONTACT_ID = "rawContactId";
    private static final String INTENT_EXTRA_SAVE_TO_PB = "saveToPhonebook";
    private static final String INTENT_EXTRA_SAVE_TO_SIM = "saveToSIM";
    private static final String INTENT_EXTRA_SIM_ID = "simId";

    /**
     * Constants used in importing from contacts
     */

    /** flag to track saving state */
    private boolean mDataBusy;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mRawContactId = -1;
        mRecordIndex = -1;
        mName = "";
        mNumber = "";
        mEmail="";
        mNumberAnr="";
        mNewName = "";
        mNewNumber = "";
        mNewEmail = "";
        mNewNumberAnr = "";
        mNameDataId = -1;
        mNumberDataId = -1;
        mEmailDataId = -1;
        mNumberAnrDataId = -1;
        mSaveToPhonebook = false;
        mAddContact = false;
        mSaveToSIM = false;
        mSimId = SimCardID.ID_ZERO;
        mNumberToInsert = null;
        mEmailToInsert = null;

        mContactUri = getIntent().getData();

        resolveIntent();

        SimContactsReadyHelper simReadyHelper = new SimContactsReadyHelper(null, false);
        mSupportEmail = simReadyHelper.supportEmail(mSimId.toInt());
        mSupportAnr = simReadyHelper.supportAnr(mSimId.toInt());

        if (mSaveToPhonebook) {
            if (-1 == mRawContactId) {
                Log.e(LOG_TAG, "onCreate(): mSaveToPhonebook == true but -1 == mRawContactId");
                finish();
            }
            Log.i(LOG_TAG, "onCreate(): mSaveToPhonebook");
            mNewName = mName;
            mNewEmail = mEmail;
            mNewNumber = mNumber;
            mNewNumberAnr = mNumberAnr;
            setContentView(R.layout.nothing_screen);

            addContactsDatabase();
            finish();
        } else if (mSaveToSIM) {
            if ((-1 == mRawContactId)||(!mAddContact)) {
                Log.e(LOG_TAG, "onCreate(): mSaveToSIM == true but -1 == mRawContactId or mAddContact == false");
                finish();
            }
            Log.i(LOG_TAG, "onCreate(): mSaveToSIM");
            mNewName = mName;
            mNewNumber = mNumber;
            if (!mSupportEmail) {
                mNewEmail = "";
            } else {
                mNewEmail = mEmail;
            }
            if (!mSupportAnr) {
                mNewNumberAnr = "";
            } else {
                mNewNumberAnr = mNumberAnr;
            }
            setContentView(R.layout.nothing_screen);

            mIccContactsUpdateHandler = new UpdateHandler(getContentResolver());
            if (null == mIccContactsUpdateHandler) {
                handleResult(false,null);
                return;
            }

            IccContactsUpdateThread updateThread = new IccContactsUpdateThread();
            updateThread.start();
        } else {
            setContentView(R.layout.edit_adn_contact_screen);
            setupView();
            if (SimCardID.ID_ONE == mSimId) {
                setTitle(mAddContact ?R.string.add_adn_contact_sim2 : R.string.edit_adn_contact_sim2);
            } else {
                if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
                    setTitle(mAddContact ?R.string.add_adn_contact_sim1 : R.string.edit_adn_contact_sim1);
                }else {
                    setTitle(mAddContact ?R.string.add_adn_contact : R.string.edit_adn_contact);
                }
            }
            mDataBusy = false;
        }
    }

    /**
     * Allow the menu to be opened ONLY if we're not busy.
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        return mDataBusy ? false : result;
    }

    private void resolveIntent() {
        Intent intent = getIntent();

        mRawContactId = intent.getLongExtra(INTENT_EXTRA_RAWCONTACT_ID,((long) -1));
        mSaveToPhonebook = intent.getBooleanExtra(INTENT_EXTRA_SAVE_TO_PB, false);
        mSaveToSIM = intent.getBooleanExtra(INTENT_EXTRA_SAVE_TO_SIM, false);
        if (intent.hasExtra(INTENT_EXTRA_SIM_ID)) {
            mSimId = (SimCardID)(intent.getExtra(INTENT_EXTRA_SIM_ID, SimCardID.ID_ZERO));
        } else {
            Log.e(LOG_TAG, "resolveIntent(): no INTENT_EXTRA_SIM_ID extra");
        }

        if (mSaveToSIM) {
            mAddContact = true;
        }

        if (-1 == mRawContactId) {
            mAddContact = true;
            return;
        }

        Bundle extras = intent.getExtras();
        if (null != extras && 0 < extras.size()) {
            CharSequence csNumber = extras.getCharSequence(android.provider.ContactsContract.Intents.Insert.PHONE);
            if (null != csNumber) {
                mNumberToInsert = csNumber.toString();
                if (DBG) log("resolveIntent(): number to insert = " + mNumberToInsert);
                if (null != mNumberToInsert) {
                    mNumberToInsert = mNumberToInsert.replaceAll("-","");
                    mNumberToInsert = mNumberToInsert.replaceAll(" ","");
                }
            }

            CharSequence csEmail = extras.getCharSequence(android.provider.ContactsContract.Intents.Insert.EMAIL);
            if (null != csEmail) {
                mEmailToInsert = csEmail.toString();
                if (DBG) log("resolveIntent(): email to insert = " + mEmailToInsert);
            }
        }

        Cursor c;
        int id;

        Uri baseUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, mRawContactId);
        Uri dataUri = Uri.withAppendedPath(baseUri, RawContacts.Data.CONTENT_DIRECTORY);

        c = getContentResolver().query(dataUri,
                                       new String[] {Phone.NUMBER,
                                               Phone.IS_PRIMARY,
                                               Phone.TYPE,
                                               Data._ID},
                                       Data.MIMETYPE + "=?",
                                       new String[] {Phone.CONTENT_ITEM_TYPE},
                                       null);

        if (c != null && c.getCount() > 0) {
            String number;
            int primary;
            int type;
            c.moveToFirst();
            do {
                number = c.getString(0);

                primary = c.getInt(1);

                type = c.getInt(2);

                id = c.getInt(3);

                if (!TextUtils.isEmpty(number)) {
                    if (mSaveToSIM) {
                        if (primary == 1) {
                            mNumber = number;
                            mNumberDataId = id;
                        } else if (primary == 0) {
                            mNumberAnr = number;
                            mNumberAnrDataId = id;
                        }

                        if ((!TextUtils.isEmpty(mNumber)) && (!TextUtils.isEmpty(mNumberAnr))) {
                            break;
                        }
                    } else {
                        if (primary == 1 && type == Phone.TYPE_MOBILE) {
                            mNumber = number;
                            mNumberDataId = id;
                        } else if (primary == 0 && type == Phone.TYPE_HOME) {
                            mNumberAnr = number;
                            mNumberAnrDataId = id;
                        }
                    }
                }
            } while(c.moveToNext());
        }

        if (c != null)
            c.close();

        if (mSaveToSIM) {
            if ((TextUtils.isEmpty(mNumber)) && (!TextUtils.isEmpty(mNumberAnr))) {
                mNumber = mNumberAnr;
                mNumberAnr = "";
            }

            mNumber = mNumber.replaceAll("-","");
            mNumber = mNumber.replaceAll(" ","");
            mNumberAnr = mNumber.replaceAll("-","");
            mNumberAnr = mNumber.replaceAll(" ","");
        }

        c = getContentResolver().query(dataUri,
                                       new String[] {StructuredName.DISPLAY_NAME,Data._ID},
                                       Data.MIMETYPE + "=?",
                                       new String[] {StructuredName.CONTENT_ITEM_TYPE},
                                       null);

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            String name;

            name = c.getString(0);

            id = c.getInt(1);

            if (!TextUtils.isEmpty(name)) {
                mName = name;
                mNameDataId = id;
            }
        }

        if (c != null)
            c.close();

        c = getContentResolver().query(dataUri,
                                       new String[] {Email.DATA,Data._ID},
                                       Data.MIMETYPE + "=?",
                                       new String[] {Email.CONTENT_ITEM_TYPE},
                                       null);

        if (c != null && c.getCount() > 0) {
            c.moveToFirst();
            String email;

            email = c.getString(0);
            id = c.getInt(1);

            if (!TextUtils.isEmpty(email)) {
                mEmail = email;
                mEmailDataId = id;
            }
        }

        if (c != null)
            c.close();

        if (!mSaveToSIM) {
            c = getContentResolver().query(RawContacts.CONTENT_URI,
                                           new String[] {RawContacts.ACCOUNT_TYPE,
                                                   RawContacts.ACCOUNT_NAME,
                                                   RawContacts.SOURCE_ID},
                                           RawContacts._ID + "=?",
                                           new String[] {String.valueOf(mRawContactId)},
                                           null);

            if (c != null && c.getCount() > 0) {
                c.moveToFirst();

                String accountType;
                String accountName;
                String sourceId;

                accountType = c.getString(0);
                if (!BrcmIccUtils.ACCOUNT_TYPE_SIM.equals(accountType)) {
                    Log.e(LOG_TAG, "Account Type Error!");
                }

                accountName = c.getString(1);

                sourceId = c.getString(2);

                if (null != sourceId) {
                    mRecordIndex = Integer.valueOf(sourceId);
                }
            }

            if (c != null)
                c.close();
        }

        if (DBG) log("resolveIntent():"+mName+":"+mNumber+":"+mEmail+":"+mNumberAnr+":"+mAddContact+", mSaveToPhonebook="+mSaveToPhonebook+", mSaveToSIM = " + mSaveToSIM);
    }

    /**
     * We have multiple layouts, one to indicate that the user needs to
     * open the keyboard to enter information (if the keybord is hidden).
     * So, we need to make sure that the layout here matches that in the
     * layout file.
     */
    private void setupView() {
        mNameField = (EditText) findViewById(R.id.adn_name);
        if (mNameField != null) {
            mNameField.setOnFocusChangeListener(mOnFocusChangeHandler);
            mNameField.setOnClickListener(mClicked);
        }

        mNumberField = (EditText) findViewById(R.id.adn_number);
        if (mNumberField != null) {
            mNumberField.setKeyListener(DialerKeyListener.getInstance());
            mNumberField.setOnFocusChangeListener(mOnFocusChangeHandler);
            mNumberField.setOnClickListener(mClicked);
        }

        //usim anr and email
        mEmailField = (EditText) findViewById(R.id.usim_email);
        if(mSupportEmail) {
            if (mEmailField != null) {
                mEmailField.setOnFocusChangeListener(mOnFocusChangeHandler);
                mEmailField.setOnClickListener(mClicked);
            }
        } else {
            View v=findViewById(R.id.email_layout);
            v.setVisibility(View.GONE);
        }
        mAnrField = (EditText) findViewById(R.id.usim_anrnumber);
        if(mSupportAnr) {
            if (mAnrField != null) {
                mAnrField.setKeyListener(DialerKeyListener.getInstance());
                mAnrField.setOnFocusChangeListener(mOnFocusChangeHandler);
                mAnrField.setOnClickListener(mClicked);
            }
        } else {
            View v=findViewById(R.id.anr_layout);
            v.setVisibility(View.GONE);
        }

        if (!mAddContact) {
            if (mNameField != null) {
                mNameField.setText(mName);
            }
            if (mNumberField != null) {
                mNumberField.setText(mNumber);
            }
            if (mEmailField != null) {
                mEmailField.setText(mEmail);
            }
            if (mAnrField != null) {
                mAnrField.setText(mNumberAnr);
            }

            if (null != mNumberToInsert) {
                if (TextUtils.isEmpty(mNumber)) {
                    if (mNumberField != null) {
                        mNumberField.setText(mNumberToInsert);
                    }
                } else {
                    if (mSupportAnr) {
                        if (mAnrField != null) {
                            mAnrField.setText(mNumberToInsert); // put mNumberToInsert to ANR number no matter any existing ADN number
                        }
                    } else {
                        if (mNumberField != null) {
                            mNumberField.setText(mNumberToInsert); // put mNumberToInsert to ADN number no matter any existing ADN number
                        }
                    }
                }
            }

            if ((mSupportEmail) && (null != mEmailToInsert)) {
                mEmailField.setText(mEmailToInsert); // put mEmailToInsert to email field no matter any existing email
            }
        }

        mButton = (Button) findViewById(R.id.button);
        if (mButton != null) {
            mButton.setOnClickListener(mClicked);
        }
    }
///usim anr and email
    private String getEmailFromTextField() {
        String email = mEmailField.getText().toString();
        if (null == email)
            return "";
        return email;
    }

    private String getAnrNumberFromTextField() {
        String anr = mAnrField.getText().toString();
        if (null == anr)
            return "";
        return anr;
    }

    private String getNameFromTextField() {
        String name = mNameField.getText().toString();
        if (null == name)
            return "";
        return name;
    }

    private String getNumberFromTextField() {
        String number = mNumberField.getText().toString();
        if (null == number)
            return "";
        return number;
    }

    private Uri getContentURI() {
        String uriData;

        if (SimCardID.ID_ONE == mSimId) {
            uriData = "content://icc2/adn";
        } else {
            uriData = "content://icc/adn";
        }

        return Uri.parse(uriData);
    }

    /**
      * @param number is adn number
      * @return true if number length is less than 20-digit limit
      */
    private boolean isValidNumber(String number) {
        return ((number.length() <= 20)
                && !number.contains("-")
                && !number.contains("(")
                && !number.contains(")")
                && !number.contains(".")
                && !number.contains("/")
                && !number.contains(";")
                && !number.contains(" "));
    }

    /**
      * @param name is adn name
      * @return true if name include at least one unicode and need encode as unicode
      */
    private boolean isUnicodeCoding(String name) {
        boolean isUcs2Coding = false;
        for (int i =0; i<name.length(); i++) {
            int d = name.codePointAt(i);
            if(d>=0xff || d<0) {
                if (DBG) log("----It's Unicode char-----------");
                isUcs2Coding = true;
                break;
            }
        }

        return isUcs2Coding;
    }


    /**
     * @param name is adn name
     * @return true if name length is no more than 14 char or 6 unicode
     */
    private boolean isValidName(String name) {
        SimContactsReadyHelper simReadyHelper = new SimContactsReadyHelper(null, false);
        int adnStrMaxLen=simReadyHelper.getAdnStrMaxLen(mSimId.toInt());
        if(isUnicodeCoding(name)) {
            return (name.length() <= (adnStrMaxLen/2));
        } else {
            return (name.length() <= adnStrMaxLen);
        }
    }

    private void addContact() {
        if (DBG) log("addContact");
    }

    private boolean checkNewDataValid() {
        if ((mSaveToPhonebook) || (mSaveToSIM))
            return true;

        mNewNumber = getNumberFromTextField();
        if ((!TextUtils.isEmpty(mNewNumber)) && (!isValidNumber(mNewNumber))) {
            showStatus(getResources().getText(R.string.adn_invalid_number));
            return false;
        }
        mNewName = getNameFromTextField();
        if ((!TextUtils.isEmpty(mNewName)) && (!isValidName(mNewName))) {
            showStatus(getResources().getText(R.string.adn_invalid_name));
            return false;
        }

        mNewNumberAnr = getAnrNumberFromTextField();
        if (!TextUtils.isEmpty(mNewNumberAnr) && !isValidNumber(mNewNumberAnr)) {
            showStatus(getResources().getText(R.string.adn_invalid_number));
            return false;
        }

        mNewEmail = getEmailFromTextField();
        if (!TextUtils.isEmpty(mNewEmail) && (!Mms.isEmailAddress(mNewEmail))) {
            showStatus(getResources().getText(R.string.usim_invalid_email));
            return false;
        }

        return true;
    }

    private boolean addContactsDatabase() {
        if (DBG) log("addContactsDatabase");

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        int rawContactInsertIndex = ops.size();

        int aggregationMode = RawContacts.AGGREGATION_MODE_DISABLED;
        String accountType = BrcmIccUtils.ACCOUNT_TYPE_SIM;
        String accountName = BrcmIccUtils.ACCOUNT_NAME_SIM1;
        String dataSet = BrcmIccUtils.ACCOUNT_NAME_SIM1;
        if (mSaveToPhonebook) {
            aggregationMode = RawContacts.AGGREGATION_MODE_DEFAULT;
            AccountTypeManager atm = AccountTypeManager.getInstance(this);
            AccountType type = atm.getAccountType(LocalAccountType.ACCOUNT_TYPE, null);
            if ((null!=type) && (type instanceof LocalAccountType)) {
                accountType = type.accountType;
                accountName = LocalAccountType.LOCAL_CONTACTS_ACCOUNT_NAME;
            } else {
                accountType = null;
                accountName = null;
            }
            dataSet = null;
        } else {
            if (SimCardID.ID_ONE == mSimId) {
                accountName = BrcmIccUtils.ACCOUNT_NAME_SIM2;
                dataSet = BrcmIccUtils.ACCOUNT_NAME_SIM2;
            }
        }

        // insert record
        ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.AGGREGATION_MODE, aggregationMode)
                .withValue(RawContacts.DELETED, 0)
                .withValue(RawContacts.ACCOUNT_TYPE, accountType)
                .withValue(RawContacts.ACCOUNT_NAME, accountName)
                .withValue(RawContacts.DATA_SET, dataSet)
//			.withValue(RawContacts.SOURCE_ID, c.getString(4))
                .withValue(RawContacts.VERSION, 1)
                .withValue(RawContacts.DIRTY, 0)
                .withValue(RawContacts.SYNC1, null)
                .withValue(RawContacts.SYNC2, null)
                .withValue(RawContacts.SYNC3, null)
                .withValue(RawContacts.SYNC4, null)
                .withValues(new ContentValues())
                .build());

        if (!mSaveToPhonebook) {
            // insert photo
            byte[] photoIcon = null;
            Bitmap photo;

            if (SimCardID.ID_ONE == mSimId) {
                photo = BitmapFactory.decodeResource(getResources(),R.drawable.ic_sim2_picture);
            } else {
                photo = BitmapFactory.decodeResource(getResources(),R.drawable.ic_sim1_picture);
            }

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            photo.compress(Bitmap.CompressFormat.JPEG, 75, stream);
            photoIcon = stream.toByteArray();

            if (null != photoIcon) {
                log("addContactsDatabase(): [" + rawContactInsertIndex + "].photo");

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Photo.RAW_CONTACT_ID, rawContactInsertIndex)
                        .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        .withValue(Photo.PHOTO, photoIcon)
                        .withValue(Data.IS_SUPER_PRIMARY, 1)
                        .build());
            }
        }

        // insert name
        if (!TextUtils.isEmpty(mNewName)) {
            log("addContactsDatabase(): [" + rawContactInsertIndex + "].name=" + mNewName);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(StructuredName.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.DISPLAY_NAME, mNewName)
                    .build());
        }

        // insert number
        if (!TextUtils.isEmpty(mNewNumber)) {
            log("addContactsDatabase(): [" + rawContactInsertIndex + "].number=" + mNewNumber);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Phone.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .withValue(Phone.NUMBER, mNewNumber)
                    .withValue(Data.IS_PRIMARY, 1)
                    .build());
        }

        // insert allEmails
        if (!TextUtils.isEmpty(mNewEmail)) {
            log("addContactsDatabase(): [" + rawContactInsertIndex + "].email=" + mNewEmail);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Email.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                    .withValue(Email.TYPE, Email.TYPE_MOBILE)
                    .withValue(Email.DATA, mNewEmail)
                    .build());
        }


        // insert ANR
        if (!TextUtils.isEmpty(mNewNumberAnr)) {
            log("addContactsDatabase(): [" + rawContactInsertIndex + "].anr=" + mNewNumberAnr);

            ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Phone.RAW_CONTACT_ID, rawContactInsertIndex)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.TYPE, Phone.TYPE_HOME)
                    .withValue(Phone.NUMBER, mNewNumberAnr)
                    .withValue(Data.IS_PRIMARY, 0)
                    .build());
        }

        boolean exceptionOccurs = false;
        if (0 < ops.size()) {
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (OperationApplicationException e) {
                exceptionOccurs = true;
                log("applyBatch(): OperationApplicationException: " + e.toString() + ". message = " + e.getMessage());
            } catch (RemoteException e) {
                exceptionOccurs = true;
                log("applyBatch(): RemoteException: " + e.toString() + ". message = " + e.getMessage());
            }
        }

        return !exceptionOccurs;
    }

    private boolean updateContactsDatabase() {
        if (DBG) log("updateContactDatabase");

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        ops.add(ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI)
                .withSelection(RawContacts._ID + "=" + mRawContactId, null)
                .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_SUSPENDED)
                .build());

        // update name
        if (!mName.equals(mNewName)) {
            if (-1==mNameDataId) {
                // insert
                log("updateContactDatabase(): insert [" + mRawContactId + "].name=" + mNewName);

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, mRawContactId)
                        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(StructuredName.DISPLAY_NAME, mNewName)
                        .build());
            } else {
                log("updateContactDatabase(): [" + mRawContactId + "].name=" + mNewName + ", mNameDataId = " + mNameDataId);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + mNameDataId, null)
                        .withValue(StructuredName.GIVEN_NAME, "")
                        .withValue(StructuredName.FAMILY_NAME, "")
                        .withValue(StructuredName.PREFIX, "")
                        .withValue(StructuredName.MIDDLE_NAME, "")
                        .withValue(StructuredName.SUFFIX, "")
                        .withValue(StructuredName.DISPLAY_NAME, mNewName)
                        .build());
            }
        }

        // update number
        if (!mNumber.equals(mNewNumber)) {
            if (-1==mNumberDataId) {
                // insert number
                log("updateContactDatabase(): insert [" + mRawContactId + "].number=" + mNewNumber);

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, mRawContactId)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                        .withValue(Phone.NUMBER, mNewNumber)
                        .withValue(Data.IS_PRIMARY, 1)
                        .build());
            } else {
                log("updateContactDatabase(): [" + mRawContactId + "].number=" + mNewNumber);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + mNumberDataId, null)
                        .withValue(Phone.NUMBER, mNewNumber)
                        .build());
            }
        }

        // update meil
        if (!mEmail.equals(mNewEmail)) {
            // insert meil
            if (-1==mEmailDataId) {
                log("updateContactDatabase(): insert [" + mRawContactId + "].email=" + mNewEmail);

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, mRawContactId)
                        .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.TYPE, Email.TYPE_MOBILE)
                        .withValue(Email.DATA, mNewEmail)
                        .build());
            } else {
                log("updateContactDatabase(): [" + mRawContactId + "].email=" + mNewEmail);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + mEmailDataId, null)
                        .withValue(Email.DATA, mNewEmail)
                        .build());
            }
        }

        // update ANR
        if (!mNumberAnr.equals(mNewNumberAnr)) {
            // insert ANR
            if (-1 == mNumberAnrDataId) {
                log("updateContactDatabase(): insert [" + mRawContactId + "].anr=" + mNewNumberAnr);

                ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, mRawContactId)
                        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.TYPE, Phone.TYPE_HOME)
                        .withValue(Data.IS_PRIMARY, 0)
                        .withValue(Phone.NUMBER, mNewNumberAnr)
                        .build());
            } else {
                log("updateContactDatabase(): [" + mRawContactId + "].anr=" + mNewNumberAnr);

                ops.add(ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                        .withSelection(Data._ID + "=" + mNumberAnrDataId, null)
                        .withValue(Phone.NUMBER, mNewNumberAnr)
                        .build());
            }
        }

        ops.add(ContentProviderOperation.newUpdate(RawContacts.CONTENT_URI)
                .withSelection(RawContacts._ID + "=" + mRawContactId, null)
                .withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DISABLED)
                .build());

        boolean exceptionOccurs = false;
        if (2 < ops.size()) {
            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (OperationApplicationException e) {
                exceptionOccurs = true;
                log("applyBatch(): OperationApplicationException: " + e.toString() + ". message = " + e.getMessage());
            } catch (RemoteException e) {
                exceptionOccurs = true;
                log("applyBatch(): RemoteException: " + e.toString() + ". message = " + e.getMessage());
            }
        }

        return !exceptionOccurs;
    }

    private void displayProgress(boolean flag) {
        // indicate we are busy.
        mDataBusy = flag;
        getWindow().setFeatureInt(
            Window.FEATURE_INDETERMINATE_PROGRESS,
            mDataBusy ? PROGRESS_VISIBILITY_ON : PROGRESS_VISIBILITY_OFF);
    }

    /**
     * Removed the status field, with preference to displaying a toast
     * to match the rest of settings UI.
     */
    private void showStatus(CharSequence statusMsg) {
        if (statusMsg != null) {
            Toast.makeText(this, statusMsg, Toast.LENGTH_SHORT)
            .show();
        }
    }

    private void handleResult(boolean success, CharSequence displayMessage) {
        int delay=2000;
        if (success) {
            Intent intent = new Intent(Intent.ACTION_VIEW, mContactUri);
            setResult(RESULT_OK, intent);
            if (null == displayMessage) {
                if (SimCardID.ID_ONE == mSimId) {
                    showStatus(getResources().getText(mAddContact ?
                                                      R.string.adn_contact_added_sim2 : R.string.adn_contact_updated_sim2));
                } else {
                    if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
                        showStatus(getResources().getText(mAddContact ?
                                                          R.string.adn_contact_added_sim1 : R.string.adn_contact_updated_sim1));
                    }else {
                        showStatus(getResources().getText(mAddContact ?
                                                          R.string.adn_contact_added : R.string.adn_contact_added));
                    }
                }
            } else {
                showStatus(displayMessage);
            }
        } else {
            setResult(RESULT_CANCELED);
            delay=3000;
            if (null == displayMessage) {
                if (SimCardID.ID_ONE == mSimId) {
                    showStatus(getResources().getText(mAddContact ?
                                                      R.string.adn_contact_add_failed_sim2 : R.string.adn_contact_update_failed_sim2));
                } else {
                    showStatus(getResources().getText(mAddContact ?
                                                      R.string.adn_contact_add_failed_sim1 : R.string.adn_contact_update_failed_sim1));
                }
            } else {
                showStatus(displayMessage);
            }
        }

        if (mSaveToSIM) {
            finish();
        } else {
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    finish();
                }
            }, delay);
        }
    }

    private void addIccCardContact() {
        Uri uri = getContentURI();
        ContentValues initialValues=new ContentValues();
        initialValues.put(IccProvider.ICC_TAG,mNewName);
        initialValues.put(IccProvider.ICC_NUMBER,mNewNumber);
        if (!TextUtils.isEmpty(mNewEmail)) {
            initialValues.put(IccProvider.ICC_EMAILS,mNewEmail);
        }
        if (!TextUtils.isEmpty(mNewNumberAnr)) {
            initialValues.put(IccProvider.ICC_ANRS,mNewNumberAnr);
        }

        Log.d(LOG_TAG,"addIccCardContact(): mNewName = " + mNewName + ", mNewNumber = " + mNewNumber + ", mNewEmail = " +
              mNewEmail + ", mNewNumberAnr = " + mNewNumberAnr);
        mIccContactsUpdateHandler.startInsert(0, null, uri,initialValues);
    }

    private void updateIccCardContact() {
        Uri uri = getContentURI();
        ContentValues initialValues = new ContentValues();
        initialValues.put(IccProvider.ICC_TAG, mName);
        initialValues.put(IccProvider.ICC_NUMBER, mNumber);
        initialValues.put(IccProvider.ICC_TAG_NEW, mNewName);
        initialValues.put(IccProvider.ICC_NUMBER_NEW, mNewNumber);

        if (!TextUtils.isEmpty(mEmail)) {
            initialValues.put(IccProvider.ICC_EMAILS, mEmail);
            log("old email=" + mEmail);
        }
        if (!TextUtils.isEmpty(mNewEmail)) {
            initialValues.put(IccProvider.ICC_EMAILS_NEW, mNewEmail);
            log("new email=" + mNewEmail);
        }

        if (!TextUtils.isEmpty(mNumberAnr)) {
            initialValues.put(IccProvider.ICC_ANRS, mNumberAnr);
            log("old anr = " + mNumberAnr);
        }
        if (!TextUtils.isEmpty(mNewNumberAnr)) {
            initialValues.put(IccProvider.ICC_ANRS_NEW, mNewNumberAnr);
            log("new anr = " + mNewNumberAnr);
        }

        if(mRecordIndex != -1) {
            initialValues.put(IccProvider.ICC_INDEX, mRecordIndex);
        }

        Log.d(LOG_TAG,"updateIccCardContact(): mName = " + mName + ", mNumber = " + mNumber +
              ", mNewName = " + mNewName + ", mNewNumber = " + mNewNumber +
              ", mNewEmail = " + mNewEmail + ", mNewNumberAnr = " + mNewNumberAnr);
        mIccContactsUpdateHandler.startUpdate(0, null, uri, initialValues, null,null);
    }

    private class IccContactsUpdateThread extends Thread {

        public IccContactsUpdateThread() {
            super("IccContactsUpdateThread");
        }

        @Override
        public void run() {
            log("IccContactsUpdateThread()=>run()");

            try {
                if (mAddContact) {
                    addIccCardContact();
                } else {
                    updateIccCardContact();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG,"icc contacts update exception!");
                handleResult(false,null);
            } finally {
            }

            if (DBG) Log.d(LOG_TAG,"IccContactsUpdateThread():run()=>");
        }
    }

    private View.OnClickListener mClicked = new View.OnClickListener() {
        public void onClick(View v) {
            if (v == mButton) {
                if (checkNewDataValid()) {
                    displayProgress(true);
                    // make sure we don't allow calls to save when we're
                    // not ready for them.
                    mButton.setClickable(!mDataBusy);
                    mIccContactsUpdateHandler = new UpdateHandler(getContentResolver());
                    if (null == mIccContactsUpdateHandler) {
                        handleResult(false,null);
                        return;
                    }

                    IccContactsUpdateThread updateThread = new IccContactsUpdateThread();
                    updateThread.start();
                }
            }
        }
    };

    View.OnFocusChangeListener mOnFocusChangeHandler =
    new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) {
                TextView textView = (TextView) v;
                Selection.selectAll((Spannable) textView.getText());
            }
        }
    };

    private class UpdateHandler extends AsyncQueryHandler {
        public UpdateHandler(ContentResolver cr) {
            super(cr);
        }

        protected void onUpdateComplete(int token, Object cookie, int result) {
            if (DBG) log("=>onUpdateComplete(): result = " + result);
            if(result==0) {
                //failed, show result
                displayProgress(false);
                handleResult(false,null);
            } else {
                boolean updateContactsDB = updateContactsDatabase();
                //show result
                displayProgress(false);
                handleResult(updateContactsDB,null);
            }
        }

        protected void onInsertComplete(int token, Object cookie,Uri uri) {
            if (DBG) log("=>onInsertsComplete(): uri = " + uri);
            if(null==uri) {
                //failed, show result
                displayProgress(false);
                handleResult(false,null);
            } else {
                boolean addContactsDB = addContactsDatabase();
                //show result
                displayProgress(false);
                handleResult(addContactsDB,null);
            }
        }
    }

    private void log(String msg) {
        Log.d(LOG_TAG, msg);
    }
}
