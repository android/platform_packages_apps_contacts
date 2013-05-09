/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.app.ExpandableListActivity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.Toast;

import com.android.contacts.R;
import com.android.contacts.vcard.ImportVCardActivity;
import com.android.vcard.VCardConfig;
import com.android.vcard.VCardEntry;
import com.android.vcard.VCardEntryConstructor;
import com.android.vcard.VCardEntryCounter;
import com.android.vcard.VCardEntryHandler;
import com.android.vcard.VCardInterpreter;
import com.android.vcard.VCardParser;
import com.android.vcard.VCardParser_V21;
import com.android.vcard.VCardParser_V30;
import com.android.vcard.VCardSourceDetector;
import com.android.vcard.exception.VCardException;
import com.android.vcard.exception.VCardNestedException;
import com.android.vcard.exception.VCardNotSupportedException;
import com.android.vcard.exception.VCardVersionException;
import com.google.android.collect.Lists;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

public class ViewVCardActivity extends ExpandableListActivity {
    private static final String LOG_TAG = "ViewVCardActivity";
    private static final boolean DBG = false;
    private static final int MAX_FILE_SIZE = 4161830;
    private static final int BEGIN_PARSER = 1000;
    private static final int GET_DATA_ITEM = 1002;
    private static final int MENU_ITEM_SAVE = 0;

    private String mPath = null;
    private boolean mLoadFinish;

    private CharSequence mTitle;

    private static final String CHINESE_LANGUAGE = Locale.CHINESE.getLanguage().toLowerCase();

    private Uri mUri = null;
    List<Map<String, ?>> mGroupData = Lists.newArrayList();
    List<List<Map<String, ?>>> mChindData = Lists.newArrayList();

    public static final int END_PARSER = 1001;
    public static final int GET_VCARD_ENTRY = 1003;

    private Handler mMyHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case BEGIN_PARSER:
                    break;
                case END_PARSER:
                    mLoadFinish = true;
                    setTitle(mTitle);
                    if (mGroupData.size() <= 0) {
                        finish();
                    }
                    break;
                case GET_VCARD_ENTRY:
                    VCardEntry vCardEntry = (VCardEntry)msg.obj;
                    DataItemObject obj = new DataItemObject();
                    createContactItem(vCardEntry, mGroupData, mChindData);
                    setTitle((String)getText(R.string.vcard_view_loaded_number)
                            + mGroupData.size());
                    bindView();
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        log("intent is "+intent);
        mUri = intent.getData();
        mTitle = getTitle();

        ExpandableListView list = getExpandableListView();
        list.setFocusable(true);

        try {
            if (mUri != null) {
                log("mUri is "+mUri.toString());
                setTitle(R.string.contact_list_loading);
                parseVCard(mUri, mMyHandler);
            } else {
                Toast.makeText(this,
                        getString(R.string.view_contact_vcard_failed), Toast.LENGTH_LONG).show();
                finish();
            }
        } catch (Exception e) {
            log("onCreate Exception "+e);
        }
    }

    public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
            int childPosition, long id) {
        if (mLoadFinish == false) {
            return true;
        }
        return super.onChildClick(parent, v, groupPosition, childPosition, id);
    }

    private void createContactItem(VCardEntry cs, List<Map<String, ?>> groupList,
            List<List<Map<String, ?>>> childList) {
        List<Map<String, ?>> chirldren;
        HashMap<String, CharSequence> curGroupMap;

        // make up the people
        curGroupMap = new HashMap<String, CharSequence>();
        groupList.add(curGroupMap);
        String name = cs.getDisplayName();
        if (name == null) {
            cs.consolidateFields();
            name = cs.getDisplayName();
        }
        curGroupMap.put("data", name);
        log("name: "+name);

        chirldren = Lists.newArrayList();
        if (cs.getPhoneList() != null) {
            ListIterator<VCardEntry.PhoneData> phoneIt = cs.getPhoneList().listIterator();
            while (phoneIt.hasNext()) {
                VCardEntry.PhoneData pd = phoneIt.next();
                String type;
                log("phone.data is "+pd.getNumber());
                log("phone.type is "+pd.getType());
                log("phone.label is "+pd.getLabel());
                HashMap<String, String> curChildMap = new HashMap<String, String>();
                chirldren.add(curChildMap);
                curChildMap.put("data", pd.getNumber());
                try {
                    CharSequence typeChar = Phone.getTypeLabel(getResources(), pd.getType(),
                            pd.getLabel());
                    type = typeChar.toString();
                } catch (NotFoundException ex) {
                    log("createContactItem NotFoundException:"+ ex);
                    type = getResources().getStringArray(android.R.array.phoneTypes)[6];
                } catch (Exception e) {
                    log("createContactItem phone Exception:"+ e);
                    type = getResources().getStringArray(android.R.array.phoneTypes)[6];
                }
                curChildMap.put("type", getString(R.string.item_phone) + ": " +type);
            }
        }
        if (cs.getEmailList() != null) {
            ListIterator<VCardEntry.EmailData> emailIt = cs.getEmailList().listIterator();
            while (emailIt.hasNext()) {
                VCardEntry.EmailData emaildata = emailIt.next();
                String type;
                log("email.type is "+emaildata.getLabel());
                log("email.data is "+emaildata.getAddress());
                log("email.auxdata is "+emaildata.getLabel());
                HashMap<String, String> curChildMap = new HashMap<String, String>();
                chirldren.add(curChildMap);
                curChildMap.put("data", emaildata.getAddress());
                try {
                    CharSequence typeChar = Email.getTypeLabel(getResources(),
                            emaildata.getType(),emaildata.getLabel());
                    type = typeChar.toString();
                } catch (NotFoundException ex) {
                    type = getResources().getStringArray(android.R.array.emailAddressTypes)[2];
                } catch (Exception e) {
                    log("createContactItem email Exception:"+ e);
                    type = getResources().getStringArray(android.R.array.emailAddressTypes)[2];
                }
                curChildMap.put("type", getString(R.string.emailLabelsGroup) + ": " +type);
            }
        }
        if (cs.getPostalList() != null) {
            ListIterator<VCardEntry.PostalData> postalIt = cs.getPostalList().listIterator();
            while (postalIt.hasNext()) {
                VCardEntry.PostalData postaldata = postalIt.next();
                String type;
                log("Postal.type is "+postaldata.getType());
                log("Postal.data is "+postaldata.getCountry());
                log("Postal.auxdata is "+postaldata.getLabel());
                HashMap<String, String> curChildMap = new HashMap<String, String>();
                chirldren.add(curChildMap);
                curChildMap.put("data", postaldata.getPobox() + " "
                        + postaldata.getExtendedAddress()
                        + " " + postaldata.getStreet() + " " + postaldata.getLocalty() +  " "
                        + postaldata.getRegion() + " " + postaldata.getPostalCode() + " "
                        + postaldata.getCountry());
                try {
                    type = getResources().
                            getStringArray(android.R.array.postalAddressTypes)
                            [postaldata.getType()-1];
                } catch (NotFoundException ex) {
                    type = getResources().getStringArray(android.R.array.postalAddressTypes)[2];
                } catch (Exception e) {
                    log("createContactItem postal Exception:"+ e);
                    type = getResources().getStringArray(android.R.array.postalAddressTypes)[2];
                }
                curChildMap.put("type", getString(R.string.postalLabelsGroup) + ": " +type);
            }
        }
        if (cs.getImList() != null) {
            ListIterator<VCardEntry.ImData> imIt = cs.getImList().listIterator();
            while (imIt.hasNext()) {
                VCardEntry.ImData imdata = imIt.next();
                String type = "";
                log("im.type is "+imdata.getProtocol());
                log("im.data is "+imdata.getAddress());
                HashMap<String, String> curChildMap = new HashMap<String, String>();
                chirldren.add(curChildMap);
                curChildMap.put("data", imdata.getAddress());
                try { // TODO for fetion
                    type = getResources().
                            getString(Im.getProtocolLabelResource(imdata.getProtocol()));
                } catch (NotFoundException ex) {
                    type = getString(R.string.otherLabelsGroup);
                } catch (Exception e) {
                    log("createContactItem IM Exception:"+ e);
                    type = getString(R.string.otherLabelsGroup);
                }
                curChildMap.put("type", getString(R.string.imLabelsGroup) + ": " +type);
            }
        }
        if (cs.getOrganizationList() != null) {
            ListIterator<VCardEntry.OrganizationData> OrgIt =
                    cs.getOrganizationList().listIterator();
            while (OrgIt.hasNext()) {
                VCardEntry.OrganizationData pd = OrgIt.next();
                String type;
                log("Organization.Organization is "+pd.getOrganizationName());
                log("Organization.type is "+pd.getType());
                HashMap<String, String> curChildMap = new HashMap<String, String>();
                chirldren.add(curChildMap);
                curChildMap.put("data", pd.getOrganizationName() + " " + pd.getTitle());
                try{
                     type = getResources().
                            getString(Organization.getTypeLabelResource(pd.getType()));
                } catch (NotFoundException ex) {
                    //set other kind as "other"
                    type = getResources().getStringArray(android.R.array.organizationTypes)[1];
                } catch (Exception e) {
                    log("createContactItem Organization Exception:"+ e);
                    type = getResources().getStringArray(android.R.array.organizationTypes)[1];
                }
                curChildMap.put("type", getString(R.string.item_organization) + ": " +type);
            }
        }
        if (cs.getWebsiteList() != null ) {
             ListIterator<VCardEntry.WebsiteData> WebIt = cs.getWebsiteList().listIterator();
             while (WebIt.hasNext()){
                 VCardEntry.WebsiteData web = WebIt.next();
                 log("website is "+web);
                 HashMap<String, String> curChildMap = new HashMap<String, String>();
                 if (web != null && TextUtils.isGraphic(web.getWebsite())){
                     chirldren.add(curChildMap);
                     curChildMap.put("data", web.getWebsite());
                    String type = getString(R.string.website_other);
                     curChildMap.put("type", getString(R.string.websiteLabelsGroup)+ ": " +type);
                 }
             }
        }
        if (cs.getBirthday() != null ) {
             String birthday = cs.getBirthday() ;
            HashMap<String, String> curChildMap = new HashMap<String, String>();
             if (TextUtils.isGraphic(birthday)){
                 chirldren.add(curChildMap);
                 curChildMap.put("data", birthday);
                 curChildMap.put("type", getString(R.string.eventTypeBirthday));
             }
        }
        if (cs.getNotes() != null ) {
             ListIterator<VCardEntry.NoteData > NoteIt = cs.getNotes().listIterator();
             while (NoteIt.hasNext()){
                 VCardEntry.NoteData note = NoteIt.next();
                 HashMap<String, String> curChildMap = new HashMap<String, String>();
                 if (TextUtils.isGraphic(note.getNote())){
                     chirldren.add(curChildMap);
                     curChildMap.put("data", note.getNote());
                     curChildMap.put("type", getString(R.string.label_notes));
                 }
             }
        }
        childList.add(chirldren);
    }

    private void bindView() {
        ExpandableListView list = getExpandableListView();
        list.setAdapter(new SimpleExpandableListAdapter(
                    this,
                    mGroupData,
                    R.layout.simple_expandable_list_item_1,
                    new String[] { "data" },
                    new int[] { android.R.id.text1 },
                    mChindData,
                    R.layout.simple_expandable_list_item_2,
                    new String[] {"type", "data" },
                    new int[] { android.R.id.text1, android.R.id.text2 }
                    ));
        list.setFocusable(true);
    }

    @Override
        public boolean onCreateOptionsMenu(Menu menu) {
            menu.add(0, MENU_ITEM_SAVE, 0, R.string.import_vcard_save);
            return true;
        }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mLoadFinish) {
            menu.findItem(MENU_ITEM_SAVE).setVisible(true);
        } else {
            menu.findItem(MENU_ITEM_SAVE).setVisible(false);
        }
        return true;
    }

    @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (item.getItemId() == MENU_ITEM_SAVE) {
                doSaveAction();
                return true;
            } else {
                return true;
            }
        }

    private void doSaveAction() {
        log("save vcard");
        if (mUri != null) {
            Intent intent = new Intent(this, ImportVCardActivity.class);
            intent.setData(mUri);
            startActivity(intent);
        }
    }

    private void parseVCard(final Uri targetUri, final Handler h) {
        new Thread(new Runnable() {

            public void run() {
                // TODO Auto-generated method stub
                final VCardEntryCounter counter = new VCardEntryCounter();
                final VCardSourceDetector detector = new VCardSourceDetector();
                boolean result;
                try {
                    // We don't know which type should be useld to parse the Uri.
                    // It is possble to misinterpret the vCard, but we expect the parser
                    // lets VCardSourceDetector detect the type before the misinterpretation.
                    result = readOneVCardFile(targetUri, VCardConfig.VCARD_TYPE_UNKNOWN,
                            detector, true, null);
                } catch (VCardNestedException e) {
                    try {
                        final int estimatedVCardType = detector.getEstimatedType();
                        // Assume that VCardSourceDetector was able to detect the source.
                        // Try again with the detector.
                        result = readOneVCardFile(targetUri, estimatedVCardType,
                                counter, false, null);
                    } catch (VCardNestedException e2) {
                        result = false;
                        Log.e(LOG_TAG, "Must not reach here. " + e2);
                    }
                }

                if (!result) {
                    return;
                }

                doActuallyReadOneVCard(targetUri, true, detector, null);
            }
        }).start();
    }

    private void doActuallyReadOneVCard(Uri uri, boolean showEntryParseProgress,
            VCardSourceDetector detector, List<String> errorFileNameList) {
        int vcardType = detector.getEstimatedType();
        if (vcardType == VCardConfig.VCARD_TYPE_UNKNOWN) {
            vcardType = VCardConfig.getVCardTypeFromString(
                    this.getString(R.string.config_import_vcard_type));
        }
        final String estimatedCharset = detector.getEstimatedCharset();
        VCardEntryConstructor builder;
        builder = new VCardEntryConstructor(vcardType, null, estimatedCharset);
        builder.addEntryHandler(new ContactVCardEntryHandler(mMyHandler));

        try {
            if (!readOneVCardFile(uri, vcardType, builder, false, null)) {
                return ;
            }
        } catch (VCardNestedException e) {
            Log.e(LOG_TAG, "Never reach here.");
        }
    }

    /**
     * Charset should be handled by {@link VCardEntryConstructor}.
     */
    private boolean readOneVCardFile(Uri uri, int vcardType,
            VCardInterpreter interpreter,
            boolean throwNestedException, List<String> errorFileNameList)
            throws VCardNestedException {
        ContentResolver resolver = this.getContentResolver();
        VCardParser vCardParser;
        InputStream is;
        try {
             is = resolver.openInputStream(uri);
            vCardParser = new VCardParser_V21(vcardType);

            try {
                 vCardParser.parse(is, interpreter);
            } catch (VCardVersionException e1) {
                try {
                    is.close();
                } catch (IOException e) {
                }
                if (interpreter instanceof VCardEntryConstructor) {
                    // Let the object clean up internal temporal objects,
                    ((VCardEntryConstructor)interpreter).clear();
                }

                is = resolver.openInputStream(uri);

                try {
                    vCardParser = new VCardParser_V30(vcardType);
                    vCardParser.parse(is, interpreter);
                } catch (VCardVersionException e2) {
                    throw new VCardException("vCard with unspported version.");
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                    }
                }
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "IOException was emitted: " + e.getMessage());

            if (errorFileNameList != null) {
                errorFileNameList.add(uri.toString());
            }
            return false;
        } catch (VCardNotSupportedException e) {
            if ((e instanceof VCardNestedException) && throwNestedException) {
                throw (VCardNestedException)e;
            }
            if (errorFileNameList != null) {
                errorFileNameList.add(uri.toString());
            }
            return false;
        } catch (VCardException e) {
            if (errorFileNameList != null) {
                errorFileNameList.add(uri.toString());
            }
            return false;
        }
        return true;
    }

    private class DataItemObject {
        List<Map<String, ?>> mGroupData = Lists.newArrayList();
        List<List<Map<String, ?>>> mChindData = Lists.newArrayList();
    }

    private void log(String msg) {
        if (DBG == true) {
            Log.d(LOG_TAG, "[ViewVCardActivity]: " + msg);
        }
    }

    public class ContactVCardEntryHandler implements VCardEntryHandler {
        private Handler mResultHandler;

        public ContactVCardEntryHandler(Handler handler) {
            mResultHandler = handler;
        }

        public void onStart() {
            log("onStart");
        }

        public void onEntryCreated(VCardEntry entry) {
            log("onEntryCreated, display name is " + entry.getDisplayName());
            List<VCardEntry.PhoneData> numberlist = entry.getPhoneList();
            if (numberlist != null) {
                for (VCardEntry.PhoneData pd: numberlist) {
                    log("onEntryCreated, " + pd.toString());
                }
            } else {
                log("onEntryCreated, entry.getPhoneList() is null");
            }

            if (mResultHandler != null) {
                Message msg = mResultHandler.
                        obtainMessage(ViewVCardActivity.GET_VCARD_ENTRY, entry);
                msg.sendToTarget();
            }
        }

        public void onEnd() {
            log("onEnd");
            if (mResultHandler != null) {
                mResultHandler.sendEmptyMessage(ViewVCardActivity.END_PARSER);
            }
        }
    }
}
