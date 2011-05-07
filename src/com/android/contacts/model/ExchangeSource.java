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

package com.android.contacts.model;

import com.android.contacts.R;
import com.google.android.collect.Lists;

import android.content.ContentValues;
import android.content.Context;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;

import java.util.Locale;

public class ExchangeSource extends FallbackSource {

    public static final String ACCOUNT_TYPE = "com.android.exchange";

    public ExchangeSource(String resPackageName) {
        this.accountType = ACCOUNT_TYPE;
        this.resPackageName = null;
        this.summaryResPackageName = resPackageName;
    }

    @Override
    protected void inflate(Context context, int inflateLevel) {

        inflateStructuredName(context, inflateLevel);
        inflateNickname(context, inflateLevel);
        inflatePhone(context, inflateLevel);
        inflateEmail(context, inflateLevel);
        inflateStructuredPostal(context, inflateLevel);
        inflateIm(context, inflateLevel);
        inflateOrganization(context, inflateLevel);
        inflatePhoto(context, inflateLevel);
        inflateNote(context, inflateLevel);
        inflateWebsite(context, inflateLevel);

        setInflatedLevel(inflateLevel);
    }

    @Override
    protected DataKind inflateStructuredName(Context context, int inflateLevel) {
        final DataKind kind = super.inflateStructuredName(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            boolean displayOrderPrimary =
                    context.getResources().getBoolean(R.bool.config_editor_field_order_primary);
            kind.typeOverallMax = 1;

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                    FLAGS_PERSON_NAME).setOptional(true));
            if (!displayOrderPrimary) {
                kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME,
                        R.string.name_family, FLAGS_PERSON_NAME));
                kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME,
                        R.string.name_middle, FLAGS_PERSON_NAME).setOptional(true));
                kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME,
                        R.string.name_given, FLAGS_PERSON_NAME));
                kind.fieldList.add(new EditField(StructuredName.SUFFIX,
                        R.string.name_suffix, FLAGS_PERSON_NAME).setOptional(true));
                kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                        R.string.name_phonetic_family, FLAGS_PHONETIC).setOptional(true));
                kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                        R.string.name_phonetic_given, FLAGS_PHONETIC).setOptional(true));
            } else {
                kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME,
                        R.string.name_given, FLAGS_PERSON_NAME));
                kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME,
                        R.string.name_middle, FLAGS_PERSON_NAME).setOptional(true));
                kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME,
                        R.string.name_family, FLAGS_PERSON_NAME));
                kind.fieldList.add(new EditField(StructuredName.SUFFIX,
                        R.string.name_suffix, FLAGS_PERSON_NAME).setOptional(true));
                kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                        R.string.name_phonetic_given, FLAGS_PHONETIC).setOptional(true));
                kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                        R.string.name_phonetic_family, FLAGS_PHONETIC).setOptional(true));
            }
        }

        return kind;
    }

    @Override
    protected DataKind inflateNickname(Context context, int inflateLevel) {
        final DataKind kind = super.inflateNickname(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.isList = false;

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup,
                    FLAGS_PERSON_NAME));
        }

        return kind;
    }

    @Override
    protected DataKind inflatePhone(Context context, int inflateLevel) {
        final DataKind kind = super.inflatePhone(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
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
                    .setSpecificMax(1).setCustomColumn(Phone.LABEL));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));
        }

        return kind;
    }

    @Override
    protected DataKind inflateEmail(Context context, int inflateLevel) {
        final DataKind kind = super.inflateEmail(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeOverallMax = 3;

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));
        }

        return kind;
    }

    @Override
    protected DataKind inflateStructuredPostal(Context context, int inflateLevel) {
        final DataKind kind = super.inflateStructuredPostal(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
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
        }

        return kind;
    }

    @Override
    protected DataKind inflateIm(Context context, int inflateLevel) {
        final DataKind kind = super.inflateIm(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeOverallMax = 3;

            // NOTE: even though a traditional "type" exists, for editing
            // purposes we're using the protocol to pick labels

            kind.defaultValues = new ContentValues();
            kind.defaultValues.put(Im.TYPE, Im.TYPE_OTHER);

            kind.typeColumn = Im.PROTOCOL;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(buildImType(Im.PROTOCOL_AIM));
            kind.typeList.add(buildImType(Im.PROTOCOL_MSN));
            kind.typeList.add(buildImType(Im.PROTOCOL_YAHOO));
            kind.typeList.add(buildImType(Im.PROTOCOL_SKYPE));
            kind.typeList.add(buildImType(Im.PROTOCOL_QQ));
            kind.typeList.add(buildImType(Im.PROTOCOL_GOOGLE_TALK));
            kind.typeList.add(buildImType(Im.PROTOCOL_ICQ));
            kind.typeList.add(buildImType(Im.PROTOCOL_JABBER));
            kind.typeList.add(buildImType(Im.PROTOCOL_CUSTOM).setSecondary(true).setCustomColumn(
                    Im.CUSTOM_PROTOCOL));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Im.DATA, R.string.imLabelsGroup, FLAGS_EMAIL));
        }

        return kind;
    }

    @Override
    protected DataKind inflateOrganization(Context context, int inflateLevel) {
        final DataKind kind = super.inflateOrganization(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.isList = false;
            kind.typeColumn = Organization.TYPE;
            kind.typeList = Lists.newArrayList();
            kind.typeList.add(buildOrgType(Organization.TYPE_WORK).setSpecificMax(1));
            kind.typeList.add(buildOrgType(Organization.TYPE_OTHER).setSpecificMax(1));
            kind.typeList.add(buildOrgType(Organization.TYPE_CUSTOM).setSecondary(true)
                    .setSpecificMax(1));

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Organization.COMPANY, R.string.ghostData_company,
                    FLAGS_GENERIC_NAME));
            kind.fieldList.add(new EditField(Organization.TITLE, R.string.ghostData_title,
                    FLAGS_GENERIC_NAME));
        }

        return kind;
    }

    @Override
    protected DataKind inflatePhoto(Context context, int inflateLevel) {
        final DataKind kind = super.inflatePhoto(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.typeOverallMax = 1;

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Photo.PHOTO, -1, -1));
        }

        return kind;
    }

    @Override
    protected DataKind inflateNote(Context context, int inflateLevel) {
        final DataKind kind = super.inflateNote(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Note.NOTE, R.string.label_notes, FLAGS_NOTE));
        }

        return kind;
    }

    @Override
    protected DataKind inflateWebsite(Context context, int inflateLevel) {
        final DataKind kind = super.inflateWebsite(context, ContactsSource.LEVEL_MIMETYPES);

        if (inflateLevel >= ContactsSource.LEVEL_CONSTRAINTS) {
            kind.isList = false;

            kind.fieldList = Lists.newArrayList();
            kind.fieldList.add(new EditField(Website.URL, R.string.websiteLabelsGroup, FLAGS_WEBSITE));
        }

        return kind;
    }

    @Override
    public int getHeaderColor(Context context) {
        return 0xffd5ba96;
    }

    @Override
    public int getSideBarColor(Context context) {
        return 0xffb58e59;
    }
}
