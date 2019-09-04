/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.contacts.model.dataitem;

import android.content.ContentValues;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Contacts.Data;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import com.android.contacts.Collapser;
import com.android.contacts.model.account.AccountType.EditType;
import com.android.contacts.model.account.BaseAccountType;
import com.android.contacts.model.account.GoogleAccountType;
import java.util.ArrayList;
import java.util.List;

/**
 * Test case for {@link DataItem}.
 */
@SmallTest
public class DataItemTests extends AndroidTestCase {

    private ContentValues mValues1;
    private ContentValues mValues2;
    private ContentValues mValues3;
    private ContentValues mValues4;
    private GoogleAccountType mGoogleAccountType;

    @Override
    protected void setUp() {
        mValues1 = new ContentValues();
        mValues2 = new ContentValues();
        mValues3 = new ContentValues();
        mValues4 = new ContentValues();

        mValues1.put(Data._ID, 1);
        mValues2.put(Data._ID, 2);
        mValues3.put(Data._ID, 3);
        mValues4.put(Data._ID, 4);

        mGoogleAccountType = new GoogleAccountType(getContext(), "packageName");
    }

    private List<DataItem> createDataItemsAndCollapse(DataKind kind, ContentValues... values) {
        final List<DataItem> dataList = new ArrayList<>(values.length);
        for (ContentValues value : values) {
            final DataItem data = DataItem.createFrom(value);
            data.setDataKind(kind);
            dataList.add(data);
        }
        Collapser.collapseList(dataList, getContext());
        return dataList;
    }

    public void testDataItemCollapsing_genericDataItemFields() {
        mValues1.put(Data.IS_SUPER_PRIMARY, 1);
        mValues2.put(Data.IS_PRIMARY, 0);

        final DataKind kind = new DataKind("test.mimetype", 0, 0, false);
        kind.actionBody = new BaseAccountType.SimpleInflater(0);
        kind.typeList = new ArrayList<>();
        kind.typeList.add(new EditType(1, -1));
        kind.typeList.add(new EditType(2, -1));
        kind.typeColumn = Data.DATA2;

        mValues1.put(kind.typeColumn, 2);
        mValues2.put(kind.typeColumn, 1);

        final List<DataItem> dataList = createDataItemsAndCollapse(kind, mValues1, mValues2);

        assertEquals(1, dataList.size());
        assertEquals(true, dataList.get(0).isSuperPrimary());
        assertEquals(true, dataList.get(0).isPrimary());
        assertEquals(1, dataList.get(0).getKindTypeColumn(kind));
    }

    public void testDataItemCollapsing_email() {
        final String email1 = "email1@google.com";
        final String email2 = "email2@google.com";

        mValues1.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE);

        mValues1.put(Email.ADDRESS, email1);
        mValues2.put(Email.ADDRESS, email1);
        mValues3.put(Email.ADDRESS, email2);

        mValues1.put(Email.TYPE, Email.TYPE_MOBILE);
        mValues2.put(Email.TYPE, Email.TYPE_HOME);
        mValues3.put(Email.TYPE, Email.TYPE_WORK);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(Email.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);

        assertEquals(2, dataList.size());
        assertEquals(email1, ((EmailDataItem) dataList.get(0)).getAddress());
        assertEquals(email2, ((EmailDataItem) dataList.get(1)).getAddress());
        assertEquals(Math.min(Email.TYPE_MOBILE, Email.TYPE_HOME),
                ((EmailDataItem) dataList.get(0)).getKindTypeColumn(kind));
    }

    public void testDataItemCollapsing_event() {
        final String date1 = "2014-01-01";
        final String date2 = "2014-02-02";
        final String customLabel1 = "custom label1";
        final String customLabel2 = "custom label2";

        mValues1.put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE);
        mValues4.put(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE);

        mValues1.put(Event.START_DATE, date1);
        mValues2.put(Event.START_DATE, date1);
        mValues3.put(Event.START_DATE, date1);
        mValues4.put(Event.START_DATE, date2);

        mValues1.put(Event.TYPE, Event.TYPE_CUSTOM);
        mValues2.put(Event.TYPE, Event.TYPE_CUSTOM);
        mValues3.put(Event.TYPE, Event.TYPE_CUSTOM);
        mValues4.put(Event.TYPE, Event.TYPE_ANNIVERSARY);

        mValues1.put(Event.LABEL, customLabel1);
        mValues2.put(Event.LABEL, customLabel1);
        mValues3.put(Event.LABEL, customLabel2);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(Event.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3, mValues4);

        assertEquals(3, dataList.size());
        assertEquals(customLabel1, ((EventDataItem) dataList.get(0)).getLabel());
        assertEquals(customLabel2, ((EventDataItem) dataList.get(1)).getLabel());
        assertEquals(date2, ((EventDataItem) dataList.get(2)).getStartDate());
    }

    public void testDataItemCollapsing_im() {
        final String address1 = "address 1";
        final String address2 = "address 2";
        final String customProtocol1 = "custom 1";
        final String customProtocol2 = "custom 2";

        mValues1.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        mValues4.put(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE);

        mValues1.put(Im.DATA, address1);
        mValues2.put(Im.DATA, address1);
        mValues3.put(Im.DATA, address1);
        mValues4.put(Im.DATA, address2);

        mValues1.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
        mValues2.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
        mValues3.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
        mValues4.put(Im.PROTOCOL, Im.PROTOCOL_AIM);

        mValues1.put(Im.CUSTOM_PROTOCOL, customProtocol1);
        mValues2.put(Im.CUSTOM_PROTOCOL, customProtocol1);
        mValues3.put(Im.CUSTOM_PROTOCOL, customProtocol2);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(Im.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3, mValues4);

        assertEquals(3, dataList.size());
        assertEquals(address1, ((ImDataItem) dataList.get(0)).getData());
        assertEquals(address1, ((ImDataItem) dataList.get(1)).getData());
        assertEquals(address2, ((ImDataItem) dataList.get(2)).getData());

        assertEquals(customProtocol1, ((ImDataItem) dataList.get(0)).getCustomProtocol());
        assertEquals(customProtocol2, ((ImDataItem) dataList.get(1)).getCustomProtocol());
        assertEquals(Im.PROTOCOL_AIM, (int) ((ImDataItem) dataList.get(2)).getProtocol());
    }

    public void testDataItemCollapsing_nickname() {
        final String nickname1 = "nickname 1";
        final String nickname2 = "nickname 2";

        mValues1.put(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE);

        mValues1.put(Nickname.NAME, nickname1);
        mValues2.put(Nickname.NAME, nickname1);
        mValues3.put(Nickname.NAME, nickname2);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(Nickname.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);

        assertEquals(2, dataList.size());
        assertEquals(nickname1, ((NicknameDataItem) dataList.get(0)).getName());
        assertEquals(nickname2, ((NicknameDataItem) dataList.get(1)).getName());
    }

    public void testDataItemCollapsing_note() {
        final String note1 = "note 1";
        final String note2 = "note 2";

        mValues1.put(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE);

        mValues1.put(Note.NOTE, note1);
        mValues2.put(Note.NOTE, note1);
        mValues3.put(Note.NOTE, note2);

        DataKind kind = mGoogleAccountType.getKindForMimetype(Note.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);

        assertEquals(2, dataList.size());
        assertEquals(note1, ((NoteDataItem) dataList.get(0)).getNote());
        assertEquals(note2, ((NoteDataItem) dataList.get(1)).getNote());
    }

    public void testDataItemCollapsing_organization() {
        final String company1 = "company1";
        final String company2 = "company2";
        final String title1 = "title1";
        final String title2 = "title2";

        mValues1.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);
        mValues4.put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE);

        mValues1.put(Organization.COMPANY, company1);
        mValues2.put(Organization.COMPANY, company1);
        mValues3.put(Organization.COMPANY, company1);
        mValues4.put(Organization.COMPANY, company2);

        mValues1.put(Organization.TITLE, title1);
        mValues2.put(Organization.TITLE, title1);
        mValues3.put(Organization.TITLE, title2);
        mValues4.put(Organization.TITLE, title1);

        final DataKind kind =
                mGoogleAccountType.getKindForMimetype(Organization.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3, mValues4);

        assertEquals(3, dataList.size());
        assertEquals(company1, ((OrganizationDataItem) dataList.get(0)).getCompany());
        assertEquals(company1, ((OrganizationDataItem) dataList.get(1)).getCompany());
        assertEquals(company2, ((OrganizationDataItem) dataList.get(2)).getCompany());

        assertEquals(title1, ((OrganizationDataItem) dataList.get(0)).getTitle());
        assertEquals(title2, ((OrganizationDataItem) dataList.get(1)).getTitle());
    }

    public void testDataItemCollapsing_phone() {
        final String phone1 = "111-111-1111";
        final String phone1a = "1111111111";
        final String phone2 = "222-222-2222";

        mValues1.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE);

        mValues1.put(Phone.NUMBER, phone1);
        mValues2.put(Phone.NUMBER, phone1a);
        mValues3.put(Phone.NUMBER, phone2);

        mValues1.put(Phone.TYPE, Phone.TYPE_MOBILE);
        mValues2.put(Phone.TYPE, Phone.TYPE_HOME);
        mValues3.put(Phone.TYPE, Phone.TYPE_WORK);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);
        assertEquals(2, dataList.size());
        assertEquals(phone1, ((PhoneDataItem) dataList.get(0)).getNumber());
        assertEquals(phone2, ((PhoneDataItem) dataList.get(1)).getNumber());
        assertEquals(Phone.TYPE_MOBILE,
                ((PhoneDataItem) dataList.get(0)).getKindTypeColumn(kind));
    }

    public void testDataItemCollapsing_relation() {
        final String name1 = "name1";
        final String name2 = "name2";
        final String customRelation1 = "custom relation 1";
        final String customRelation2 = "custom relation 2";

        mValues1.put(Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE);
        mValues4.put(Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE);

        mValues1.put(Relation.NAME, name1);
        mValues2.put(Relation.NAME, name1);
        mValues3.put(Relation.NAME, name1);
        mValues4.put(Relation.NAME, name2);

        mValues1.put(Relation.TYPE, Relation.TYPE_CUSTOM);
        mValues2.put(Relation.TYPE, Relation.TYPE_CUSTOM);
        mValues3.put(Relation.TYPE, Relation.TYPE_CUSTOM);
        mValues4.put(Relation.TYPE, Relation.TYPE_BROTHER);

        mValues1.put(Relation.LABEL, customRelation1);
        mValues2.put(Relation.LABEL, customRelation1);
        mValues3.put(Relation.LABEL, customRelation2);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(Relation.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3, mValues4);

        assertEquals(3, dataList.size());
        assertEquals(name1, ((RelationDataItem) dataList.get(0)).getName());
        assertEquals(name2, ((RelationDataItem) dataList.get(2)).getName());

        assertEquals(customRelation1, ((RelationDataItem) dataList.get(0)).getLabel());
        assertEquals(customRelation2, ((RelationDataItem) dataList.get(1)).getLabel());
    }

    public void testDataItemCollapsing_sip() {
        final String sip1 = "sip 1";
        final String sip2 = "sip 2";

        mValues1.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE);

        mValues1.put(SipAddress.SIP_ADDRESS, sip1);
        mValues2.put(SipAddress.SIP_ADDRESS, sip1);
        mValues3.put(SipAddress.SIP_ADDRESS, sip2);

        mValues1.put(SipAddress.TYPE, SipAddress.TYPE_WORK);
        mValues2.put(SipAddress.TYPE, SipAddress.TYPE_HOME);
        mValues3.put(SipAddress.TYPE, SipAddress.TYPE_WORK);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(SipAddress.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);

        assertEquals(2, dataList.size());
        assertEquals(sip1, ((SipAddressDataItem) dataList.get(0)).getSipAddress());
        assertEquals(sip2, ((SipAddressDataItem) dataList.get(1)).getSipAddress());
    }

    public void testDataItemCollapsing_structuredName() {
        final String displayName1 = "Display Name 1";
        final String displayName2 = "Display Name 2";

        mValues1.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);

        mValues1.put(StructuredName.DISPLAY_NAME, displayName1);
        mValues2.put(StructuredName.DISPLAY_NAME, displayName1);
        mValues3.put(StructuredName.DISPLAY_NAME, displayName2);

        final DataKind kind =
                mGoogleAccountType.getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);

        assertEquals(2, dataList.size());
        assertEquals(displayName1, ((StructuredNameDataItem) dataList.get(0)).getDisplayName());
        assertEquals(displayName2, ((StructuredNameDataItem) dataList.get(1)).getDisplayName());
    }

    public void testDataItemCollapsing_structuredPostal() {
        final String formattedAddress1 = "Formatted Address 1";
        final String formattedAddress2 = "Formatted Address 2";

        mValues1.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE);

        mValues1.put(StructuredPostal.FORMATTED_ADDRESS, formattedAddress1);
        mValues2.put(StructuredPostal.FORMATTED_ADDRESS, formattedAddress1);
        mValues3.put(StructuredPostal.FORMATTED_ADDRESS, formattedAddress2);

        final DataKind kind =
                mGoogleAccountType.getKindForMimetype(StructuredPostal.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);

        assertEquals(2, dataList.size());
        assertEquals(formattedAddress1,
                ((StructuredPostalDataItem) dataList.get(0)).getFormattedAddress());
        assertEquals(formattedAddress2,
                ((StructuredPostalDataItem) dataList.get(1)).getFormattedAddress());
    }

    public void testDataItemCollapsing_website() {
        final String url1 = "www.url1.com";
        final String url2 = "www.url2.com";

        mValues1.put(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE);
        mValues2.put(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE);
        mValues3.put(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE);

        mValues1.put(Website.URL, url1);
        mValues2.put(Website.URL, url1);
        mValues3.put(Website.URL, url2);

        final DataKind kind = mGoogleAccountType.getKindForMimetype(Website.CONTENT_ITEM_TYPE);

        final List<DataItem> dataList =
                createDataItemsAndCollapse(kind, mValues1, mValues2, mValues3);

        assertEquals(2, dataList.size());
        assertEquals(url1, ((WebsiteDataItem) dataList.get(0)).getUrl());
        assertEquals(url2, ((WebsiteDataItem) dataList.get(1)).getUrl());
    }
}
