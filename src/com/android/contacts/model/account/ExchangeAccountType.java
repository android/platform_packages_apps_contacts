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

package com.android.contacts.model.account;

import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;

import com.android.contacts.R;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.CommonDateUtils;
import com.android.contactsbind.FeedbackHelper;

import com.google.common.collect.Lists;

import java.util.Locale;

public class ExchangeAccountType extends BaseAccountType {
    private static final String TAG = "ExchangeAccountType";

    private static final String ACCOUNT_TYPE_AOSP = "com.android.exchange";
    private static final String ACCOUNT_TYPE_GOOGLE_1 = "com.google.android.exchange";
    private static final String ACCOUNT_TYPE_GOOGLE_2 = "com.google.android.gm.exchange";

    public ExchangeAccountType(Context context, String authenticatorPackageName, String type) {
        this.accountType = type;
        this.resourcePackageName = null;
        this.syncAdapterPackageName = authenticatorPackageName;

        try {
            addDataKindStructuredName(context);
            addDataKindName(context);
            addDataKindPhoneticName(context);
            addDataKindNickname(context);
            addDataKindPhone(context);
            addDataKindEmail(context);
            addDataKindStructuredPostal(context);
            addDataKindIm(context);
            addDataKindOrganization(context);
            addDataKindPhoto(context);
            addDataKindNote(context);
            addDataKindEvent(context);
            addDataKindWebsite(context);
            addDataKindGroupMembership(context);

            mIsInitialized = true;
        } catch (DefinitionException e) {
            // TODO: Change this to fail fast if there are no feedback reports
            FeedbackHelper.sendFeedback(context, TAG, "Failed to build exchange account type", e);
        }
    }

    public static boolean isExchangeType(String type) {
        return ACCOUNT_TYPE_AOSP.equals(type) || ACCOUNT_TYPE_GOOGLE_1.equals(type)
                || ACCOUNT_TYPE_GOOGLE_2.equals(type);
    }

    @Override
    protected DataKind addDataKindStructuredName(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                R.string.nameLabelsGroup, Weight.NONE, true));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);

        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                FLAGS_PERSON_NAME).setOptional(true));
        kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME,
                R.string.name_family, FLAGS_PERSON_NAME));
        kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME,
                R.string.name_middle, FLAGS_PERSON_NAME));
        kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME,
                R.string.name_given, FLAGS_PERSON_NAME));
        kind.fieldList.add(new EditField(StructuredName.SUFFIX,
                R.string.name_suffix, FLAGS_PERSON_NAME));

        kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                R.string.name_phonetic_family, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                R.string.name_phonetic_given, FLAGS_PHONETIC));

        return kind;
    }

    @Override
    protected DataKind addDataKindPhoneticName(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME,
                R.string.name_phonetic, Weight.NONE, true));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);

        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                R.string.name_phonetic_family, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                R.string.name_phonetic_given, FLAGS_PHONETIC));

        return kind;
    }

    @Override
    protected DataKind addDataKindNickname(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindNickname(context);

        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup,
                FLAGS_PERSON_NAME));

        return kind;
    }

    @Override
    protected DataKind addDataKindPhone(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindPhone(context);

        kind.typeColumn = Phone.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildPhoneType(Phone.TYPE_MOBILE).setSpecificMax(1));
        kind.typeList.add(buildPhoneType(Phone.TYPE_HOME).setSpecificMax(2));
        kind.typeList.add(buildPhoneType(Phone.TYPE_WORK).setSpecificMax(2));
        kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_WORK).setSecondary(true)
                .setSpecificMax(1));
        kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_HOME).setSecondary(true)
                .setSpecificMax(1));
        kind.typeList
                .add(buildPhoneType(Phone.TYPE_PAGER).setSecondary(true).setSpecificMax(1));
        kind.typeList.add(buildPhoneType(Phone.TYPE_CAR).setSecondary(true).setSpecificMax(1));
        kind.typeList.add(buildPhoneType(Phone.TYPE_COMPANY_MAIN).setSecondary(true)
                .setSpecificMax(1));
        kind.typeList.add(buildPhoneType(Phone.TYPE_MMS).setSecondary(true).setSpecificMax(1));
        kind.typeList
                .add(buildPhoneType(Phone.TYPE_RADIO).setSecondary(true).setSpecificMax(1));
        kind.typeList.add(buildPhoneType(Phone.TYPE_ASSISTANT).setSecondary(true)
                .setSpecificMax(1));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));

        return kind;
    }

    @Override
    protected DataKind addDataKindEmail(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindEmail(context);

        kind.typeOverallMax = 3;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));

        return kind;
    }

    @Override
    protected DataKind addDataKindStructuredPostal(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindStructuredPostal(context);

        final boolean useJapaneseOrder =
            Locale.JAPANESE.getLanguage().equals(Locale.getDefault().getLanguage());
        kind.typeColumn = StructuredPostal.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_WORK).setSpecificMax(1));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_HOME).setSpecificMax(1));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_OTHER).setSpecificMax(1));

        kind.fieldList = Lists.newArrayList();
        if (useJapaneseOrder) {
            kind.fieldList.add(new EditField(StructuredPostal.COUNTRY,
                    R.string.postal_country, FLAGS_POSTAL).setOptional(true));
            kind.fieldList.add(new EditField(StructuredPostal.POSTCODE,
                    R.string.postal_postcode, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.REGION,
                    R.string.postal_region, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.CITY,
                    R.string.postal_city,FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.STREET,
                    R.string.postal_street, FLAGS_POSTAL));
        } else {
            kind.fieldList.add(new EditField(StructuredPostal.STREET,
                    R.string.postal_street, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.CITY,
                    R.string.postal_city,FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.REGION,
                    R.string.postal_region, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.POSTCODE,
                    R.string.postal_postcode, FLAGS_POSTAL));
            kind.fieldList.add(new EditField(StructuredPostal.COUNTRY,
                    R.string.postal_country, FLAGS_POSTAL).setOptional(true));
        }

        return kind;
    }

    @Override
    protected DataKind addDataKindIm(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindIm(context);

        // Types are not supported for IM. There can be 3 IMs, but OWA only shows only the first
        kind.typeOverallMax = 3;

        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Im.TYPE, Im.TYPE_OTHER);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Im.DATA, R.string.imLabelsGroup, FLAGS_EMAIL));

        return kind;
    }

    @Override
    protected DataKind addDataKindOrganization(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindOrganization(context);

        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Organization.COMPANY, R.string.ghostData_company,
                FLAGS_GENERIC_NAME));
        kind.fieldList.add(new EditField(Organization.TITLE, R.string.ghostData_title,
                FLAGS_GENERIC_NAME));

        return kind;
    }

    @Override
    protected DataKind addDataKindPhoto(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindPhoto(context);

        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Photo.PHOTO, -1, -1));

        return kind;
    }

    @Override
    protected DataKind addDataKindNote(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindNote(context);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Note.NOTE, R.string.label_notes, FLAGS_NOTE));

        return kind;
    }

    protected DataKind addDataKindEvent(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Event.CONTENT_ITEM_TYPE, R.string.eventLabelsGroup,
                Weight.EVENT, true));
        kind.actionHeader = new EventActionInflater();
        kind.actionBody = new SimpleInflater(Event.START_DATE);

        kind.typeOverallMax = 1;

        kind.typeColumn = Event.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildEventType(Event.TYPE_BIRTHDAY, false).setSpecificMax(1));

        kind.dateFormatWithYear = CommonDateUtils.DATE_AND_TIME_FORMAT;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Event.DATA, R.string.eventLabelsGroup, FLAGS_EVENT));

        return kind;
    }

    @Override
    protected DataKind addDataKindWebsite(Context context) throws DefinitionException {
        final DataKind kind = super.addDataKindWebsite(context);

        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Website.URL, R.string.websiteLabelsGroup, FLAGS_WEBSITE));

        return kind;
    }

    @Override
    public boolean isGroupMembershipEditable() {
        return true;
    }

    @Override
    public boolean areContactsWritable() {
        return true;
    }
}
