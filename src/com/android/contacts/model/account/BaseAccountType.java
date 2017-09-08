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
import android.content.res.Resources;
import android.provider.ContactsContract.CommonDataKinds.BaseTypes;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Event;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Organization;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.Relation;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.provider.ContactsContract.Data;
import android.util.AttributeSet;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import com.android.contacts.R;
import com.android.contacts.model.dataitem.CustomDataItem;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.util.CommonDateUtils;
import com.android.contacts.util.ContactDisplayUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public abstract class BaseAccountType extends AccountType {
    private static final String TAG = "BaseAccountType";

    protected static final int FLAGS_PHONE = EditorInfo.TYPE_CLASS_PHONE;
    protected static final int FLAGS_EMAIL = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
    protected static final int FLAGS_PERSON_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS | EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME;
    protected static final int FLAGS_PHONETIC = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_PHONETIC;
    protected static final int FLAGS_GENERIC_NAME = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS;
    protected static final int FLAGS_NOTE = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
    protected static final int FLAGS_EVENT = EditorInfo.TYPE_CLASS_TEXT;
    protected static final int FLAGS_WEBSITE = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_URI;
    protected static final int FLAGS_POSTAL = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS
            | EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE;
    protected static final int FLAGS_SIP_ADDRESS = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;  // since SIP addresses have the same
                                                             // basic format as email addresses
    protected static final int FLAGS_RELATION = EditorInfo.TYPE_CLASS_TEXT
            | EditorInfo.TYPE_TEXT_FLAG_CAP_WORDS | EditorInfo.TYPE_TEXT_VARIATION_PERSON_NAME;

    // Specify the maximum number of lines that can be used to display various field types.  If no
    // value is specified for a particular type, we use the default value from {@link DataKind}.
    protected static final int MAX_LINES_FOR_POSTAL_ADDRESS = 10;
    protected static final int MAX_LINES_FOR_GROUP = 10;
    protected static final int MAX_LINES_FOR_NOTE = 100;

    private interface Tag {
        static final String DATA_KIND = "DataKind";
        static final String TYPE = "Type";
    }

    private interface Attr {
        static final String MAX_OCCURRENCE = "maxOccurs";
        static final String DATE_WITH_TIME = "dateWithTime";
        static final String YEAR_OPTIONAL = "yearOptional";
        static final String KIND = "kind";
        static final String TYPE = "type";
    }

    protected interface Weight {
        static final int NONE = -1;
        static final int PHONE = 10;
        static final int EMAIL = 15;
        static final int STRUCTURED_POSTAL = 25;
        static final int NICKNAME = 111;
        static final int EVENT = 120;
        static final int ORGANIZATION = 125;
        static final int NOTE = 130;
        static final int IM = 140;
        static final int SIP_ADDRESS = 145;
        static final int GROUP_MEMBERSHIP = 150;
        static final int WEBSITE = 160;
        static final int RELATIONSHIP = 999;
    }

    public BaseAccountType() {
        this.accountType = null;
        this.dataSet = null;
        this.titleRes = R.string.account_phone;
        this.iconRes = R.mipmap.ic_contacts_launcher;
    }

    protected static EditType buildPhoneType(int type) {
        return new EditType(type, Phone.getTypeLabelResource(type));
    }

    protected static EditType buildEmailType(int type) {
        return new EditType(type, Email.getTypeLabelResource(type));
    }

    protected static EditType buildPostalType(int type) {
        return new EditType(type, StructuredPostal.getTypeLabelResource(type));
    }

    protected static EditType buildImType(int type) {
        return new EditType(type, Im.getProtocolLabelResource(type));
    }

    protected static EditType buildEventType(int type, boolean yearOptional) {
        return new EventEditType(type, Event.getTypeResource(type)).setYearOptional(yearOptional);
    }

    protected static EditType buildRelationType(int type) {
        return new EditType(type, Relation.getTypeLabelResource(type));
    }

    protected DataKind addDataKindStructuredName(Context context) throws DefinitionException {
        final DataKind kind = addKind(new DataKind(StructuredName.CONTENT_ITEM_TYPE,
                R.string.nameLabelsGroup, Weight.NONE, true));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);
        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                FLAGS_PERSON_NAME));
        kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                FLAGS_PERSON_NAME));
        kind.fieldList.add(new EditField(StructuredName.SUFFIX, R.string.name_suffix,
                FLAGS_PERSON_NAME).setLongForm(true));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                R.string.name_phonetic_family, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_MIDDLE_NAME,
                R.string.name_phonetic_middle, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                R.string.name_phonetic_given, FLAGS_PHONETIC));

        return kind;
    }

    protected DataKind addDataKindName(Context context) throws DefinitionException {
        final DataKind kind = addKind(new DataKind(DataKind.PSEUDO_MIME_TYPE_NAME,
                R.string.nameLabelsGroup, Weight.NONE, true));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);
        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        final boolean displayOrderPrimary =
                context.getResources().getBoolean(R.bool.config_editor_field_order_primary);

        kind.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                FLAGS_PERSON_NAME).setOptional(true));
        if (!displayOrderPrimary) {
            kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                    FLAGS_PERSON_NAME).setPhoneticsColumn(StructuredName.PHONETIC_FAMILY_NAME));
            kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                    FLAGS_PERSON_NAME).setOptional(true).setPhoneticsColumn(StructuredName.PHONETIC_MIDDLE_NAME));
            kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                    FLAGS_PERSON_NAME).setPhoneticsColumn(StructuredName.PHONETIC_GIVEN_NAME));
        } else {
            kind.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                    FLAGS_PERSON_NAME).setPhoneticsColumn(StructuredName.PHONETIC_GIVEN_NAME));
            kind.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                    FLAGS_PERSON_NAME).setOptional(true).setPhoneticsColumn(StructuredName.PHONETIC_MIDDLE_NAME));
            kind.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                    FLAGS_PERSON_NAME).setPhoneticsColumn(StructuredName.PHONETIC_FAMILY_NAME));
        }
        kind.fieldList.add(new EditField(StructuredName.SUFFIX, R.string.name_suffix,
                FLAGS_PERSON_NAME).setOptional(true));

        return kind;
    }

    protected DataKind addDataKindPhoneticName(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME,
                R.string.name_phonetic, Weight.NONE, true));
        kind.actionHeader = new SimpleInflater(R.string.nameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);
        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();

        kind.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                R.string.name_phonetic_family, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_MIDDLE_NAME,
                R.string.name_phonetic_middle, FLAGS_PHONETIC));
        kind.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                R.string.name_phonetic_given, FLAGS_PHONETIC));

        return kind;
    }

    protected DataKind addDataKindNickname(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Nickname.CONTENT_ITEM_TYPE,
                    R.string.nicknameLabelsGroup, Weight.NICKNAME, true));
        kind.typeOverallMax = 1;
        kind.actionHeader = new SimpleInflater(R.string.nicknameLabelsGroup);
        kind.actionBody = new SimpleInflater(Nickname.NAME);
        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Nickname.TYPE, Nickname.TYPE_DEFAULT);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup,
                FLAGS_PERSON_NAME));

        return kind;
    }

    protected DataKind addDataKindPhone(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Phone.CONTENT_ITEM_TYPE, R.string.phoneLabelsGroup,
                Weight.PHONE, true));
        kind.iconAltRes = R.drawable.quantum_ic_message_vd_theme_24;
        kind.iconAltDescriptionRes = R.string.sms;
        kind.actionHeader = new PhoneActionInflater();
        kind.actionAltHeader = new PhoneActionAltInflater();
        kind.actionBody = new SimpleInflater(Phone.NUMBER);
        kind.typeColumn = Phone.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildPhoneType(Phone.TYPE_MOBILE));
        kind.typeList.add(buildPhoneType(Phone.TYPE_HOME));
        kind.typeList.add(buildPhoneType(Phone.TYPE_WORK));
        kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_WORK).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_FAX_HOME).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_PAGER).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_OTHER));
        kind.typeList.add(
                buildPhoneType(Phone.TYPE_CUSTOM).setSecondary(true).setCustomColumn(Phone.LABEL));
        kind.typeList.add(buildPhoneType(Phone.TYPE_CALLBACK).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_CAR).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_COMPANY_MAIN).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_ISDN).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_MAIN).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_OTHER_FAX).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_RADIO).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_TELEX).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_TTY_TDD).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_WORK_MOBILE).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_WORK_PAGER).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_ASSISTANT).setSecondary(true));
        kind.typeList.add(buildPhoneType(Phone.TYPE_MMS).setSecondary(true));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));

        return kind;
    }

    protected DataKind addDataKindEmail(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Email.CONTENT_ITEM_TYPE, R.string.emailLabelsGroup,
                Weight.EMAIL, true));
        kind.actionHeader = new EmailActionInflater();
        kind.actionBody = new SimpleInflater(Email.DATA);
        kind.typeColumn = Email.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildEmailType(Email.TYPE_HOME));
        kind.typeList.add(buildEmailType(Email.TYPE_WORK));
        kind.typeList.add(buildEmailType(Email.TYPE_OTHER));
        kind.typeList.add(buildEmailType(Email.TYPE_MOBILE));
        kind.typeList.add(
                buildEmailType(Email.TYPE_CUSTOM).setSecondary(true).setCustomColumn(Email.LABEL));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));

        return kind;
    }

    protected DataKind addDataKindStructuredPostal(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(StructuredPostal.CONTENT_ITEM_TYPE,
                R.string.postalLabelsGroup, Weight.STRUCTURED_POSTAL, true));
        kind.actionHeader = new PostalActionInflater();
        kind.actionBody = new SimpleInflater(StructuredPostal.FORMATTED_ADDRESS);
        kind.typeColumn = StructuredPostal.TYPE;
        kind.typeList = Lists.newArrayList();
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_HOME));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_WORK));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_OTHER));
        kind.typeList.add(buildPostalType(StructuredPostal.TYPE_CUSTOM).setSecondary(true)
                .setCustomColumn(StructuredPostal.LABEL));

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(
                new EditField(StructuredPostal.FORMATTED_ADDRESS, R.string.postal_address,
                        FLAGS_POSTAL));

        kind.maxLinesForDisplay = MAX_LINES_FOR_POSTAL_ADDRESS;

        return kind;
    }

    protected DataKind addDataKindIm(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Im.CONTENT_ITEM_TYPE, R.string.imLabelsGroup,
                Weight.IM, true));
        kind.actionHeader = new ImActionInflater();
        kind.actionBody = new SimpleInflater(Im.DATA);

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

        return kind;
    }

    protected DataKind addDataKindOrganization(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Organization.CONTENT_ITEM_TYPE,
                    R.string.organizationLabelsGroup, Weight.ORGANIZATION, true));
        kind.actionHeader = new SimpleInflater(R.string.organizationLabelsGroup);
        kind.actionBody = ORGANIZATION_BODY_INFLATER;
        kind.typeOverallMax = 1;

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Organization.COMPANY, R.string.ghostData_company,
                FLAGS_GENERIC_NAME));
        kind.fieldList.add(new EditField(Organization.TITLE, R.string.ghostData_title,
                FLAGS_GENERIC_NAME));

        return kind;
    }

    protected DataKind addDataKindPhoto(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Photo.CONTENT_ITEM_TYPE, -1, Weight.NONE, true));
        kind.typeOverallMax = 1;
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Photo.PHOTO, -1, -1));
        return kind;
    }

    protected DataKind addDataKindNote(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Note.CONTENT_ITEM_TYPE, R.string.label_notes,
                Weight.NOTE, true));
        kind.typeOverallMax = 1;
        kind.actionHeader = new SimpleInflater(R.string.label_notes);
        kind.actionBody = new SimpleInflater(Note.NOTE);
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Note.NOTE, R.string.label_notes, FLAGS_NOTE));

        kind.maxLinesForDisplay = MAX_LINES_FOR_NOTE;

        return kind;
    }

    protected DataKind addDataKindWebsite(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(Website.CONTENT_ITEM_TYPE,
                R.string.websiteLabelsGroup, Weight.WEBSITE, true));
        kind.actionHeader = new SimpleInflater(R.string.websiteLabelsGroup);
        kind.actionBody = new SimpleInflater(Website.URL);
        kind.defaultValues = new ContentValues();
        kind.defaultValues.put(Website.TYPE, Website.TYPE_OTHER);

        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(Website.URL, R.string.websiteLabelsGroup, FLAGS_WEBSITE));

        return kind;
    }

    protected DataKind addDataKindSipAddress(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(SipAddress.CONTENT_ITEM_TYPE,
                    R.string.label_sip_address, Weight.SIP_ADDRESS, true));

        kind.actionHeader = new SimpleInflater(R.string.label_sip_address);
        kind.actionBody = new SimpleInflater(SipAddress.SIP_ADDRESS);
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(SipAddress.SIP_ADDRESS,
                                         R.string.label_sip_address, FLAGS_SIP_ADDRESS));
        kind.typeOverallMax = 1;

        return kind;
    }

    protected DataKind addDataKindGroupMembership(Context context) throws DefinitionException {
        DataKind kind = addKind(new DataKind(GroupMembership.CONTENT_ITEM_TYPE,
                R.string.groupsLabel, Weight.GROUP_MEMBERSHIP, true));

        kind.typeOverallMax = 1;
        kind.fieldList = Lists.newArrayList();
        kind.fieldList.add(new EditField(GroupMembership.GROUP_ROW_ID, -1, -1));

        kind.maxLinesForDisplay = MAX_LINES_FOR_GROUP;

        return kind;
    }

    protected DataKind addDataKindCustomField(Context context) throws DefinitionException {
        final DataKind kind = addKind(new DataKind(CustomDataItem.MIMETYPE_CUSTOM_FIELD,
                R.string.label_custom_field, Weight.NONE, /* editable */ false));
        kind.actionBody = new SimpleInflater(Data.DATA2);
        return kind;
    }

    /**
     * Simple inflater that assumes a string resource has a "%s" that will be
     * filled from the given column.
     */
    public static class SimpleInflater implements StringInflater {
        private final int mStringRes;
        private final String mColumnName;

        public SimpleInflater(int stringRes) {
            this(stringRes, null);
        }

        public SimpleInflater(String columnName) {
            this(-1, columnName);
        }

        public SimpleInflater(int stringRes, String columnName) {
            mStringRes = stringRes;
            mColumnName = columnName;
        }

        @Override
        public CharSequence inflateUsing(Context context, ContentValues values) {
            final boolean validColumn = values.containsKey(mColumnName);
            final boolean validString = mStringRes > 0;

            final CharSequence stringValue = validString ? context.getText(mStringRes) : null;
            final CharSequence columnValue = validColumn ? values.getAsString(mColumnName) : null;

            if (validString && validColumn) {
                return String.format(stringValue.toString(), columnValue);
            } else if (validString) {
                return stringValue;
            } else if (validColumn) {
                return columnValue;
            } else {
                return null;
            }
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
                    + " mStringRes=" + mStringRes
                    + " mColumnName" + mColumnName;
        }

        public String getColumnNameForTest() {
            return mColumnName;
        }
    }

    public static abstract class CommonInflater implements StringInflater {
        protected abstract int getTypeLabelResource(Integer type);

        protected boolean isCustom(Integer type) {
            return type == BaseTypes.TYPE_CUSTOM;
        }

        protected String getTypeColumn() {
            return Phone.TYPE;
        }

        protected String getLabelColumn() {
            return Phone.LABEL;
        }

        protected CharSequence getTypeLabel(Resources res, Integer type, CharSequence label) {
            final int labelRes = getTypeLabelResource(type);
            if (type == null) {
                return res.getText(labelRes);
            } else if (isCustom(type)) {
                return res.getString(labelRes, label == null ? "" : label);
            } else {
                return res.getText(labelRes);
            }
        }

        @Override
        public CharSequence inflateUsing(Context context, ContentValues values) {
            final Integer type = values.getAsInteger(getTypeColumn());
            final String label = values.getAsString(getLabelColumn());
            return getTypeLabel(context.getResources(), type, label);
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName();
        }
    }

    public static class PhoneActionInflater extends CommonInflater {
        @Override
        protected boolean isCustom(Integer type) {
            return ContactDisplayUtils.isCustomPhoneType(type);
        }

        @Override
        protected int getTypeLabelResource(Integer type) {
            return ContactDisplayUtils.getPhoneLabelResourceId(type);
        }
    }

    public static class PhoneActionAltInflater extends CommonInflater {
        @Override
        protected boolean isCustom(Integer type) {
            return ContactDisplayUtils.isCustomPhoneType(type);
        }

        @Override
        protected int getTypeLabelResource(Integer type) {
            return ContactDisplayUtils.getSmsLabelResourceId(type);
        }
    }

    public static class EmailActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.email;
            switch (type) {
                case Email.TYPE_HOME: return R.string.email_home;
                case Email.TYPE_WORK: return R.string.email_work;
                case Email.TYPE_OTHER: return R.string.email_other;
                case Email.TYPE_MOBILE: return R.string.email_mobile;
                default: return R.string.email_custom;
            }
        }
    }

    public static class EventActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            return Event.getTypeResource(type);
        }
    }

    public static class RelationActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            return Relation.getTypeLabelResource(type == null ? Relation.TYPE_CUSTOM : type);
        }
    }

    public static class PostalActionInflater extends CommonInflater {
        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.map_other;
            switch (type) {
                case StructuredPostal.TYPE_HOME: return R.string.map_home;
                case StructuredPostal.TYPE_WORK: return R.string.map_work;
                case StructuredPostal.TYPE_OTHER: return R.string.map_other;
                default: return R.string.map_custom;
            }
        }
    }

    public static class ImActionInflater extends CommonInflater {
        @Override
        protected String getTypeColumn() {
            return Im.PROTOCOL;
        }

        @Override
        protected String getLabelColumn() {
            return Im.CUSTOM_PROTOCOL;
        }

        @Override
        protected int getTypeLabelResource(Integer type) {
            if (type == null) return R.string.chat;
            switch (type) {
                case Im.PROTOCOL_AIM: return R.string.chat_aim;
                case Im.PROTOCOL_MSN: return R.string.chat_msn;
                case Im.PROTOCOL_YAHOO: return R.string.chat_yahoo;
                case Im.PROTOCOL_SKYPE: return R.string.chat_skype;
                case Im.PROTOCOL_QQ: return R.string.chat_qq;
                case Im.PROTOCOL_GOOGLE_TALK: return R.string.chat_gtalk;
                case Im.PROTOCOL_ICQ: return R.string.chat_icq;
                case Im.PROTOCOL_JABBER: return R.string.chat_jabber;
                case Im.PROTOCOL_NETMEETING: return R.string.chat;
                default: return R.string.chat;
            }
        }
    }

    public static final StringInflater ORGANIZATION_BODY_INFLATER = new StringInflater() {
        @Override
        public CharSequence inflateUsing(Context context, ContentValues values) {
            final CharSequence companyValue = values.containsKey(Organization.COMPANY) ?
                    values.getAsString(Organization.COMPANY) : null;
            final CharSequence titleValue = values.containsKey(Organization.TITLE) ?
                    values.getAsString(Organization.TITLE) : null;

            if (companyValue != null && titleValue != null) {
                return companyValue +  ": " + titleValue;
            } else if (companyValue == null) {
                return titleValue;
            } else {
                return companyValue;
            }
        }
    };

    @Override
    public boolean isGroupMembershipEditable() {
        return false;
    }

    /**
     * Parses the content of the EditSchema tag in contacts.xml.
     */
    protected final void parseEditSchema(Context context, XmlPullParser parser, AttributeSet attrs)
            throws XmlPullParserException, IOException, DefinitionException {

        final int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            final int depth = parser.getDepth();
            if (type != XmlPullParser.START_TAG || depth != outerDepth + 1) {
                continue; // Not direct child tag
            }

            final String tag = parser.getName();

            if (Tag.DATA_KIND.equals(tag)) {
                for (DataKind kind : KindParser.INSTANCE.parseDataKindTag(context, parser, attrs)) {
                    addKind(kind);
                }
            } else {
                Log.w(TAG, "Skipping unknown tag " + tag);
            }
        }
    }

    // Utility methods to keep code shorter.
    private static boolean getAttr(AttributeSet attrs, String attribute, boolean defaultValue) {
        return attrs.getAttributeBooleanValue(null, attribute, defaultValue);
    }

    private static int getAttr(AttributeSet attrs, String attribute, int defaultValue) {
        return attrs.getAttributeIntValue(null, attribute, defaultValue);
    }

    private static String getAttr(AttributeSet attrs, String attribute) {
        return attrs.getAttributeValue(null, attribute);
    }

    // TODO Extract it to its own class, and move all KindBuilders to it as well.
    private static class KindParser {
        public static final KindParser INSTANCE = new KindParser();

        private final Map<String, KindBuilder> mBuilders = Maps.newHashMap();

        private KindParser() {
            addBuilder(new NameKindBuilder());
            addBuilder(new NicknameKindBuilder());
            addBuilder(new PhoneKindBuilder());
            addBuilder(new EmailKindBuilder());
            addBuilder(new StructuredPostalKindBuilder());
            addBuilder(new ImKindBuilder());
            addBuilder(new OrganizationKindBuilder());
            addBuilder(new PhotoKindBuilder());
            addBuilder(new NoteKindBuilder());
            addBuilder(new WebsiteKindBuilder());
            addBuilder(new SipAddressKindBuilder());
            addBuilder(new GroupMembershipKindBuilder());
            addBuilder(new EventKindBuilder());
            addBuilder(new RelationshipKindBuilder());
        }

        private void addBuilder(KindBuilder builder) {
            mBuilders.put(builder.getTagName(), builder);
        }

        /**
         * Takes a {@link XmlPullParser} at the start of a DataKind tag, parses it and returns
         * {@link DataKind}s.  (Usually just one, but there are three for the "name" kind.)
         *
         * This method returns a list, because we need to add 3 kinds for the name data kind.
         * (structured, display and phonetic)
         */
        public List<DataKind> parseDataKindTag(Context context, XmlPullParser parser,
                AttributeSet attrs)
                throws DefinitionException, XmlPullParserException, IOException {
            final String kind = getAttr(attrs, Attr.KIND);
            final KindBuilder builder = mBuilders.get(kind);
            if (builder != null) {
                return builder.parseDataKind(context, parser, attrs);
            } else {
                throw new DefinitionException("Undefined data kind '" + kind + "'");
            }
        }
    }

    private static abstract class KindBuilder {

        public abstract String getTagName();

        /**
         * DataKind tag parser specific to each kind.  Subclasses must implement it.
         */
        public abstract List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException, IOException;

        /**
         * Creates a new {@link DataKind}, and also parses the child Type tags in the DataKind
         * tag.
         */
        protected final DataKind newDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs, boolean isPseudo, String mimeType, String typeColumn,
                int titleRes, int weight, StringInflater actionHeader, StringInflater actionBody)
                throws DefinitionException, XmlPullParserException, IOException {

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Adding DataKind: " + mimeType);
            }

            final DataKind kind = new DataKind(mimeType, titleRes, weight, true);
            kind.typeColumn = typeColumn;
            kind.actionHeader = actionHeader;
            kind.actionBody = actionBody;
            kind.fieldList = Lists.newArrayList();

            // Get more information from the tag...
            // A pseudo data kind doesn't have corresponding tag the XML, so we skip this.
            if (!isPseudo) {
                kind.typeOverallMax = getAttr(attrs, Attr.MAX_OCCURRENCE, -1);

                // Process "Type" tags.
                // If a kind has the type column, contacts.xml must have at least one type
                // definition.  Otherwise, it mustn't have a type definition.
                if (kind.typeColumn != null) {
                    // Parse and add types.
                    kind.typeList = Lists.newArrayList();
                    parseTypes(context, parser, attrs, kind, true);
                    if (kind.typeList.size() == 0) {
                        throw new DefinitionException(
                                "Kind " + kind.mimeType + " must have at least one type");
                    }
                } else {
                    // Make sure it has no types.
                    parseTypes(context, parser, attrs, kind, false /* can't have types */);
                }
            }

            return kind;
        }

        /**
         * Parses Type elements in a DataKind element, and if {@code canHaveTypes} is true adds
         * them to the given {@link DataKind}. Otherwise the {@link DataKind} can't have a type,
         * so throws {@link DefinitionException}.
         */
        private void parseTypes(Context context, XmlPullParser parser, AttributeSet attrs,
                DataKind kind, boolean canHaveTypes)
                throws DefinitionException, XmlPullParserException, IOException {
            final int outerDepth = parser.getDepth();
            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                final int depth = parser.getDepth();
                if (type != XmlPullParser.START_TAG || depth != outerDepth + 1) {
                    continue; // Not direct child tag
                }

                final String tag = parser.getName();
                if (Tag.TYPE.equals(tag)) {
                    if (canHaveTypes) {
                        kind.typeList.add(parseTypeTag(parser, attrs, kind));
                    } else {
                        throw new DefinitionException(
                                "Kind " + kind.mimeType + " can't have types");
                    }
                } else {
                    throw new DefinitionException("Unknown tag: " + tag);
                }
            }
        }

        /**
         * Parses a single Type element and returns an {@link EditType} built from it.  Uses
         * {@link #buildEditTypeForTypeTag} defined in subclasses to actually build an
         * {@link EditType}.
         */
        private EditType parseTypeTag(XmlPullParser parser, AttributeSet attrs, DataKind kind)
                throws DefinitionException {

            final String typeName = getAttr(attrs, Attr.TYPE);

            final EditType et = buildEditTypeForTypeTag(attrs, typeName);
            if (et == null) {
                throw new DefinitionException(
                        "Undefined type '" + typeName + "' for data kind '" + kind.mimeType + "'");
            }
            et.specificMax = getAttr(attrs, Attr.MAX_OCCURRENCE, -1);

            return et;
        }

        /**
         * Returns an {@link EditType} for the given "type".  Subclasses may optionally use
         * the attributes in the tag to set optional values.
         * (e.g. "yearOptional" for the event kind)
         */
        protected EditType buildEditTypeForTypeTag(AttributeSet attrs, String type) {
            return null;
        }

        protected final void throwIfList(DataKind kind) throws DefinitionException {
            if (kind.typeOverallMax != 1) {
                throw new DefinitionException(
                        "Kind " + kind.mimeType + " must have 'overallMax=\"1\"'");
            }
        }
    }

    /**
     * DataKind parser for Name. (structured, display, phonetic)
     */
    private static class NameKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "name";
        }

        private static void checkAttributeTrue(boolean value, String attrName)
                throws DefinitionException {
            if (!value) {
                throw new DefinitionException(attrName + " must be true");
            }
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {

            // Build 3 data kinds:
            // - StructuredName.CONTENT_ITEM_TYPE
            // - DataKind.PSEUDO_MIME_TYPE_NAME
            // - DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME

            final boolean displayOrderPrimary =
                    context.getResources().getBoolean(R.bool.config_editor_field_order_primary);

            final boolean supportsPrefix = getAttr(attrs, "supportsPrefix", false);
            final boolean supportsMiddleName = getAttr(attrs, "supportsMiddleName", false);
            final boolean supportsSuffix = getAttr(attrs, "supportsSuffix", false);
            final boolean supportsPhoneticFamilyName =
                    getAttr(attrs, "supportsPhoneticFamilyName", false);
            final boolean supportsPhoneticMiddleName =
                    getAttr(attrs, "supportsPhoneticMiddleName", false);
            final boolean supportsPhoneticGivenName =
                    getAttr(attrs, "supportsPhoneticGivenName", false);

            // For now, every thing must be supported.
            checkAttributeTrue(supportsPrefix, "supportsPrefix");
            checkAttributeTrue(supportsMiddleName, "supportsMiddleName");
            checkAttributeTrue(supportsSuffix, "supportsSuffix");
            checkAttributeTrue(supportsPhoneticFamilyName, "supportsPhoneticFamilyName");
            checkAttributeTrue(supportsPhoneticMiddleName, "supportsPhoneticMiddleName");
            checkAttributeTrue(supportsPhoneticGivenName, "supportsPhoneticGivenName");

            final List<DataKind> kinds = Lists.newArrayList();

            // Structured name
            final DataKind ks = newDataKind(context, parser, attrs, false,
                    StructuredName.CONTENT_ITEM_TYPE, null, R.string.nameLabelsGroup, Weight.NONE,
                    new SimpleInflater(R.string.nameLabelsGroup),
                    new SimpleInflater(Nickname.NAME));

            ks.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                    FLAGS_PERSON_NAME).setLongForm(true));
            ks.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                    FLAGS_PERSON_NAME));
            ks.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                    FLAGS_PERSON_NAME).setLongForm(true));
            ks.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                    FLAGS_PERSON_NAME));
            ks.fieldList.add(new EditField(StructuredName.SUFFIX, R.string.name_suffix,
                    FLAGS_PERSON_NAME).setLongForm(true));
            ks.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                    R.string.name_phonetic_family, FLAGS_PHONETIC));
            ks.fieldList.add(new EditField(StructuredName.PHONETIC_MIDDLE_NAME,
                    R.string.name_phonetic_middle, FLAGS_PHONETIC));
            ks.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                    R.string.name_phonetic_given, FLAGS_PHONETIC));

            throwIfList(ks);
            kinds.add(ks);

            // Name
            final DataKind kn = newDataKind(context, parser, attrs, true,
                    DataKind.PSEUDO_MIME_TYPE_NAME, null,
                    R.string.nameLabelsGroup, Weight.NONE,
                    new SimpleInflater(R.string.nameLabelsGroup),
                    new SimpleInflater(Nickname.NAME));
            kn.typeOverallMax = 1;
            throwIfList(kn);
            kinds.add(kn);

            kn.fieldList.add(new EditField(StructuredName.PREFIX, R.string.name_prefix,
                    FLAGS_PERSON_NAME).setOptional(true));
            if (!displayOrderPrimary) {
                kn.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                        FLAGS_PERSON_NAME));
                kn.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                        FLAGS_PERSON_NAME).setOptional(true));
                kn.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                        FLAGS_PERSON_NAME));
            } else {
                kn.fieldList.add(new EditField(StructuredName.GIVEN_NAME, R.string.name_given,
                        FLAGS_PERSON_NAME));
                kn.fieldList.add(new EditField(StructuredName.MIDDLE_NAME, R.string.name_middle,
                        FLAGS_PERSON_NAME).setOptional(true));
                kn.fieldList.add(new EditField(StructuredName.FAMILY_NAME, R.string.name_family,
                        FLAGS_PERSON_NAME));
            }
            kn.fieldList.add(new EditField(StructuredName.SUFFIX, R.string.name_suffix,
                    FLAGS_PERSON_NAME).setOptional(true));

            // Phonetic name
            final DataKind kp = newDataKind(context, parser, attrs, true,
                    DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME, null,
                    R.string.name_phonetic, Weight.NONE,
                    new SimpleInflater(R.string.nameLabelsGroup),
                    new SimpleInflater(Nickname.NAME));
            kp.typeOverallMax = 1;
            kinds.add(kp);

            // We may want to change the order depending on displayOrderPrimary too.
            kp.fieldList.add(new EditField(StructuredName.PHONETIC_FAMILY_NAME,
                    R.string.name_phonetic_family, FLAGS_PHONETIC));
            kp.fieldList.add(new EditField(StructuredName.PHONETIC_MIDDLE_NAME,
                    R.string.name_phonetic_middle, FLAGS_PHONETIC));
            kp.fieldList.add(new EditField(StructuredName.PHONETIC_GIVEN_NAME,
                    R.string.name_phonetic_given, FLAGS_PHONETIC));
            return kinds;
        }
    }

    private static class NicknameKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "nickname";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Nickname.CONTENT_ITEM_TYPE, null, R.string.nicknameLabelsGroup, Weight.NICKNAME,
                    new SimpleInflater(R.string.nicknameLabelsGroup),
                    new SimpleInflater(Nickname.NAME));

            kind.fieldList.add(new EditField(Nickname.NAME, R.string.nicknameLabelsGroup,
                    FLAGS_PERSON_NAME));

            kind.defaultValues = new ContentValues();
            kind.defaultValues.put(Nickname.TYPE, Nickname.TYPE_DEFAULT);

            throwIfList(kind);
            return Lists.newArrayList(kind);
        }
    }

    private static class PhoneKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "phone";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Phone.CONTENT_ITEM_TYPE, Phone.TYPE, R.string.phoneLabelsGroup, Weight.PHONE,
                    new PhoneActionInflater(), new SimpleInflater(Phone.NUMBER));

            kind.iconAltRes = R.drawable.quantum_ic_message_vd_theme_24;
            kind.iconAltDescriptionRes = R.string.sms;
            kind.actionAltHeader = new PhoneActionAltInflater();

            kind.fieldList.add(new EditField(Phone.NUMBER, R.string.phoneLabelsGroup, FLAGS_PHONE));

            return Lists.newArrayList(kind);
        }

        /** Just to avoid line-wrapping... */
        protected static EditType build(int type, boolean secondary) {
            return new EditType(type, Phone.getTypeLabelResource(type)).setSecondary(secondary);
        }

        @Override
        protected EditType buildEditTypeForTypeTag(AttributeSet attrs, String type) {
            if ("home".equals(type)) return build(Phone.TYPE_HOME, false);
            if ("mobile".equals(type)) return build(Phone.TYPE_MOBILE, false);
            if ("work".equals(type)) return build(Phone.TYPE_WORK, false);
            if ("fax_work".equals(type)) return build(Phone.TYPE_FAX_WORK, true);
            if ("fax_home".equals(type)) return build(Phone.TYPE_FAX_HOME, true);
            if ("pager".equals(type)) return build(Phone.TYPE_PAGER, true);
            if ("other".equals(type)) return build(Phone.TYPE_OTHER, false);
            if ("callback".equals(type)) return build(Phone.TYPE_CALLBACK, true);
            if ("car".equals(type)) return build(Phone.TYPE_CAR, true);
            if ("company_main".equals(type)) return build(Phone.TYPE_COMPANY_MAIN, true);
            if ("isdn".equals(type)) return build(Phone.TYPE_ISDN, true);
            if ("main".equals(type)) return build(Phone.TYPE_MAIN, true);
            if ("other_fax".equals(type)) return build(Phone.TYPE_OTHER_FAX, true);
            if ("radio".equals(type)) return build(Phone.TYPE_RADIO, true);
            if ("telex".equals(type)) return build(Phone.TYPE_TELEX, true);
            if ("tty_tdd".equals(type)) return build(Phone.TYPE_TTY_TDD, true);
            if ("work_mobile".equals(type)) return build(Phone.TYPE_WORK_MOBILE, true);
            if ("work_pager".equals(type)) return build(Phone.TYPE_WORK_PAGER, true);

            // Note "assistant" used to be a custom column for the fallback type, but not anymore.
            if ("assistant".equals(type)) return build(Phone.TYPE_ASSISTANT, true);
            if ("mms".equals(type)) return build(Phone.TYPE_MMS, true);
            if ("custom".equals(type)) {
                return build(Phone.TYPE_CUSTOM, true).setCustomColumn(Phone.LABEL);
            }
            return null;
        }
    }

    private static class EmailKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "email";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Email.CONTENT_ITEM_TYPE, Email.TYPE, R.string.emailLabelsGroup, Weight.EMAIL,
                    new EmailActionInflater(), new SimpleInflater(Email.DATA));
            kind.fieldList.add(new EditField(Email.DATA, R.string.emailLabelsGroup, FLAGS_EMAIL));

            return Lists.newArrayList(kind);
        }

        @Override
        protected EditType buildEditTypeForTypeTag(AttributeSet attrs, String type) {
            // EditType is mutable, so we need to create a new instance every time.
            if ("home".equals(type)) return buildEmailType(Email.TYPE_HOME);
            if ("work".equals(type)) return buildEmailType(Email.TYPE_WORK);
            if ("other".equals(type)) return buildEmailType(Email.TYPE_OTHER);
            if ("mobile".equals(type)) return buildEmailType(Email.TYPE_MOBILE);
            if ("custom".equals(type)) {
                return buildEmailType(Email.TYPE_CUSTOM)
                        .setSecondary(true).setCustomColumn(Email.LABEL);
            }
            return null;
        }
    }

    private static class StructuredPostalKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "postal";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    StructuredPostal.CONTENT_ITEM_TYPE, StructuredPostal.TYPE,
                    R.string.postalLabelsGroup, Weight.STRUCTURED_POSTAL,
                    new PostalActionInflater(),
                    new SimpleInflater(StructuredPostal.FORMATTED_ADDRESS));

            if (getAttr(attrs, "needsStructured", false)) {
                if (Locale.JAPANESE.getLanguage().equals(Locale.getDefault().getLanguage())) {
                    // Japanese order
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
                    // Generic order
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
            } else {
                kind.maxLinesForDisplay= MAX_LINES_FOR_POSTAL_ADDRESS;
                kind.fieldList.add(
                        new EditField(StructuredPostal.FORMATTED_ADDRESS, R.string.postal_address,
                                FLAGS_POSTAL));
            }

            return Lists.newArrayList(kind);
        }

        @Override
        protected EditType buildEditTypeForTypeTag(AttributeSet attrs, String type) {
            // EditType is mutable, so we need to create a new instance every time.
            if ("home".equals(type)) return buildPostalType(StructuredPostal.TYPE_HOME);
            if ("work".equals(type)) return buildPostalType(StructuredPostal.TYPE_WORK);
            if ("other".equals(type)) return buildPostalType(StructuredPostal.TYPE_OTHER);
            if ("custom".equals(type)) {
                return buildPostalType(StructuredPostal.TYPE_CUSTOM)
                        .setSecondary(true).setCustomColumn(Email.LABEL);
            }
            return null;
        }
    }

    private static class ImKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "im";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {

            // IM is special:
            // - It uses "protocol" as the custom label field
            // - Its TYPE is fixed to TYPE_OTHER

            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Im.CONTENT_ITEM_TYPE, Im.PROTOCOL, R.string.imLabelsGroup, Weight.IM,
                    new ImActionInflater(), new SimpleInflater(Im.DATA) // header / action
                    );
            kind.fieldList.add(new EditField(Im.DATA, R.string.imLabelsGroup, FLAGS_EMAIL));

            kind.defaultValues = new ContentValues();
            kind.defaultValues.put(Im.TYPE, Im.TYPE_OTHER);

            return Lists.newArrayList(kind);
        }

        @Override
        protected EditType buildEditTypeForTypeTag(AttributeSet attrs, String type) {
            if ("aim".equals(type)) return buildImType(Im.PROTOCOL_AIM);
            if ("msn".equals(type)) return buildImType(Im.PROTOCOL_MSN);
            if ("yahoo".equals(type)) return buildImType(Im.PROTOCOL_YAHOO);
            if ("skype".equals(type)) return buildImType(Im.PROTOCOL_SKYPE);
            if ("qq".equals(type)) return buildImType(Im.PROTOCOL_QQ);
            if ("google_talk".equals(type)) return buildImType(Im.PROTOCOL_GOOGLE_TALK);
            if ("icq".equals(type)) return buildImType(Im.PROTOCOL_ICQ);
            if ("jabber".equals(type)) return buildImType(Im.PROTOCOL_JABBER);
            if ("custom".equals(type)) {
                return buildImType(Im.PROTOCOL_CUSTOM).setSecondary(true)
                        .setCustomColumn(Im.CUSTOM_PROTOCOL);
            }
            return null;
        }
    }

    private static class OrganizationKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "organization";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Organization.CONTENT_ITEM_TYPE, null, R.string.organizationLabelsGroup,
                    Weight.ORGANIZATION,
                    new SimpleInflater(R.string.organizationLabelsGroup),
                    ORGANIZATION_BODY_INFLATER);

            kind.fieldList.add(new EditField(Organization.COMPANY, R.string.ghostData_company,
                    FLAGS_GENERIC_NAME));
            kind.fieldList.add(new EditField(Organization.TITLE, R.string.ghostData_title,
                    FLAGS_GENERIC_NAME));

            throwIfList(kind);

            return Lists.newArrayList(kind);
        }
    }

    private static class PhotoKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "photo";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Photo.CONTENT_ITEM_TYPE, null /* no type */, Weight.NONE, -1,
                    null, null // no header, no body
                    );

            kind.fieldList.add(new EditField(Photo.PHOTO, -1, -1));

            throwIfList(kind);

            return Lists.newArrayList(kind);
        }
    }

    private static class NoteKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "note";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Note.CONTENT_ITEM_TYPE, null, R.string.label_notes, Weight.NOTE,
                    new SimpleInflater(R.string.label_notes), new SimpleInflater(Note.NOTE));

            kind.fieldList.add(new EditField(Note.NOTE, R.string.label_notes, FLAGS_NOTE));
            kind.maxLinesForDisplay = MAX_LINES_FOR_NOTE;

            throwIfList(kind);

            return Lists.newArrayList(kind);
        }
    }

    private static class WebsiteKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "website";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Website.CONTENT_ITEM_TYPE, null, R.string.websiteLabelsGroup, Weight.WEBSITE,
                    new SimpleInflater(R.string.websiteLabelsGroup),
                    new SimpleInflater(Website.URL));

            kind.fieldList.add(new EditField(Website.URL, R.string.websiteLabelsGroup,
                    FLAGS_WEBSITE));

            kind.defaultValues = new ContentValues();
            kind.defaultValues.put(Website.TYPE, Website.TYPE_OTHER);

            return Lists.newArrayList(kind);
        }
    }

    private static class SipAddressKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "sip_address";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    SipAddress.CONTENT_ITEM_TYPE, null, R.string.label_sip_address,
                    Weight.SIP_ADDRESS,
                    new SimpleInflater(R.string.label_sip_address),
                    new SimpleInflater(SipAddress.SIP_ADDRESS));

            kind.fieldList.add(new EditField(SipAddress.SIP_ADDRESS,
                    R.string.label_sip_address, FLAGS_SIP_ADDRESS));

            throwIfList(kind);

            return Lists.newArrayList(kind);
        }
    }

    private static class GroupMembershipKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "group_membership";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    GroupMembership.CONTENT_ITEM_TYPE, null,
                    R.string.groupsLabel, Weight.GROUP_MEMBERSHIP, null, null);

            kind.fieldList.add(new EditField(GroupMembership.GROUP_ROW_ID, -1, -1));
            kind.maxLinesForDisplay = MAX_LINES_FOR_GROUP;

            throwIfList(kind);

            return Lists.newArrayList(kind);
        }
    }

    /**
     * Event DataKind parser.
     *
     * Event DataKind is used only for Google/Exchange types, so this parser is not used for now.
     */
    private static class EventKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "event";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Event.CONTENT_ITEM_TYPE, Event.TYPE, R.string.eventLabelsGroup, Weight.EVENT,
                    new EventActionInflater(), new SimpleInflater(Event.START_DATE));

            kind.fieldList.add(new EditField(Event.DATA, R.string.eventLabelsGroup, FLAGS_EVENT));

            if (getAttr(attrs, Attr.DATE_WITH_TIME, false)) {
                kind.dateFormatWithoutYear = CommonDateUtils.NO_YEAR_DATE_AND_TIME_FORMAT;
                kind.dateFormatWithYear = CommonDateUtils.DATE_AND_TIME_FORMAT;
            } else {
                kind.dateFormatWithoutYear = CommonDateUtils.NO_YEAR_DATE_FORMAT;
                kind.dateFormatWithYear = CommonDateUtils.FULL_DATE_FORMAT;
            }

            return Lists.newArrayList(kind);
        }

        @Override
        protected EditType buildEditTypeForTypeTag(AttributeSet attrs, String type) {
            final boolean yo = getAttr(attrs, Attr.YEAR_OPTIONAL, false);

            if ("birthday".equals(type)) {
                return buildEventType(Event.TYPE_BIRTHDAY, yo).setSpecificMax(1);
            }
            if ("anniversary".equals(type)) return buildEventType(Event.TYPE_ANNIVERSARY, yo);
            if ("other".equals(type)) return buildEventType(Event.TYPE_OTHER, yo);
            if ("custom".equals(type)) {
                return buildEventType(Event.TYPE_CUSTOM, yo)
                        .setSecondary(true).setCustomColumn(Event.LABEL);
            }
            return null;
        }
    }

    /**
     * Relationship DataKind parser.
     *
     * Relationship DataKind is used only for Google/Exchange types, so this parser is not used for
     * now.
     */
    private static class RelationshipKindBuilder extends KindBuilder {
        @Override
        public String getTagName() {
            return "relationship";
        }

        @Override
        public List<DataKind> parseDataKind(Context context, XmlPullParser parser,
                AttributeSet attrs) throws DefinitionException, XmlPullParserException,
                IOException {
            final DataKind kind = newDataKind(context, parser, attrs, false,
                    Relation.CONTENT_ITEM_TYPE, Relation.TYPE,
                    R.string.relationLabelsGroup, Weight.RELATIONSHIP,
                    new RelationActionInflater(), new SimpleInflater(Relation.NAME));

            kind.fieldList.add(new EditField(Relation.DATA, R.string.relationLabelsGroup,
                    FLAGS_RELATION));

            kind.defaultValues = new ContentValues();
            kind.defaultValues.put(Relation.TYPE, Relation.TYPE_SPOUSE);

            return Lists.newArrayList(kind);
        }

        @Override
        protected EditType buildEditTypeForTypeTag(AttributeSet attrs, String type) {
            // EditType is mutable, so we need to create a new instance every time.
            if ("assistant".equals(type)) return buildRelationType(Relation.TYPE_ASSISTANT);
            if ("brother".equals(type)) return buildRelationType(Relation.TYPE_BROTHER);
            if ("child".equals(type)) return buildRelationType(Relation.TYPE_CHILD);
            if ("domestic_partner".equals(type)) {
                    return buildRelationType(Relation.TYPE_DOMESTIC_PARTNER);
            }
            if ("father".equals(type)) return buildRelationType(Relation.TYPE_FATHER);
            if ("friend".equals(type)) return buildRelationType(Relation.TYPE_FRIEND);
            if ("manager".equals(type)) return buildRelationType(Relation.TYPE_MANAGER);
            if ("mother".equals(type)) return buildRelationType(Relation.TYPE_MOTHER);
            if ("parent".equals(type)) return buildRelationType(Relation.TYPE_PARENT);
            if ("partner".equals(type)) return buildRelationType(Relation.TYPE_PARTNER);
            if ("referred_by".equals(type)) return buildRelationType(Relation.TYPE_REFERRED_BY);
            if ("relative".equals(type)) return buildRelationType(Relation.TYPE_RELATIVE);
            if ("sister".equals(type)) return buildRelationType(Relation.TYPE_SISTER);
            if ("spouse".equals(type)) return buildRelationType(Relation.TYPE_SPOUSE);
            if ("custom".equals(type)) {
                return buildRelationType(Relation.TYPE_CUSTOM).setSecondary(true)
                        .setCustomColumn(Relation.LABEL);
            }
            return null;
        }
    }
}
