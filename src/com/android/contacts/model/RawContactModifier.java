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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
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
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Intents.Insert;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import com.android.contacts.ContactsUtils;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountType.EditField;
import com.android.contacts.model.account.AccountType.EditType;
import com.android.contacts.model.account.AccountType.EventEditType;
import com.android.contacts.model.account.GoogleAccountType;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contacts.model.dataitem.PhoneDataItem;
import com.android.contacts.model.dataitem.StructuredNameDataItem;
import com.android.contacts.util.CommonDateUtils;
import com.android.contacts.util.DateUtils;
import com.android.contacts.util.NameConverter;

import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helper methods for modifying an {@link RawContactDelta}, such as inserting
 * new rows, or enforcing {@link AccountType}.
 */
public class RawContactModifier {
    private static final String TAG = RawContactModifier.class.getSimpleName();

    /** Set to true in order to view logs on entity operations */
    private static final boolean DEBUG = false;

    /**
     * For the given {@link RawContactDelta}, determine if the given
     * {@link DataKind} could be inserted under specific
     * {@link AccountType}.
     */
    public static boolean canInsert(RawContactDelta state, DataKind kind) {
        // Insert possible when have valid types and under overall maximum
        final int visibleCount = state.getMimeEntriesCount(kind.mimeType, true);
        final boolean validTypes = hasValidTypes(state, kind);
        final boolean validOverall = (kind.typeOverallMax == -1)
                || (visibleCount < kind.typeOverallMax);
        return (validTypes && validOverall);
    }

    public static boolean hasValidTypes(RawContactDelta state, DataKind kind) {
        if (RawContactModifier.hasEditTypes(kind)) {
            return (getValidTypes(state, kind, null, true, null, true).size() > 0);
        } else {
            return true;
        }
    }

    /**
     * Ensure that at least one of the given {@link DataKind} exists in the
     * given {@link RawContactDelta} state, and try creating one if none exist.
     * @return The child (either newly created or the first existing one), or null if the
     *     account doesn't support this {@link DataKind}.
     */
    public static ValuesDelta ensureKindExists(
            RawContactDelta state, AccountType accountType, String mimeType) {
        final DataKind kind = accountType.getKindForMimetype(mimeType);
        final boolean hasChild = state.getMimeEntriesCount(mimeType, true) > 0;

        if (kind != null) {
            if (hasChild) {
                // Return the first entry.
                return state.getMimeEntries(mimeType).get(0);
            } else {
                // Create child when none exists and valid kind
                final ValuesDelta child = insertChild(state, kind);
                if (kind.mimeType.equals(Photo.CONTENT_ITEM_TYPE)) {
                    child.setFromTemplate(true);
                }
                return child;
            }
        }
        return null;
    }

    /**
     * For the given {@link RawContactDelta} and {@link DataKind}, return the
     * list possible {@link EditType} options available based on
     * {@link AccountType}.
     *
     * @param forceInclude Always include this {@link EditType} in the returned
     *            list, even when an otherwise-invalid choice. This is useful
     *            when showing a dialog that includes the current type.
     * @param includeSecondary If true, include any valid types marked as
     *            {@link EditType#secondary}.
     * @param typeCount When provided, will be used for the frequency count of
     *            each {@link EditType}, otherwise built using
     *            {@link #getTypeFrequencies(RawContactDelta, DataKind)}.
     * @param checkOverall If true, check if the overall number of types is under limit.
     */
    public static ArrayList<EditType> getValidTypes(RawContactDelta state, DataKind kind,
            EditType forceInclude, boolean includeSecondary, SparseIntArray typeCount,
            boolean checkOverall) {
        final ArrayList<EditType> validTypes = new ArrayList<EditType>();

        // Bail early if no types provided
        if (!hasEditTypes(kind)) return validTypes;

        if (typeCount == null) {
            // Build frequency counts if not provided
            typeCount = getTypeFrequencies(state, kind);
        }

        // Build list of valid types
        boolean validOverall = true;
        if (checkOverall) {
            final int overallCount = typeCount.get(FREQUENCY_TOTAL);
            validOverall = (kind.typeOverallMax == -1 ? true
                    : overallCount < kind.typeOverallMax);
        }

        for (EditType type : kind.typeList) {
            final boolean validSpecific = (type.specificMax == -1 ? true : typeCount
                    .get(type.rawValue) < type.specificMax);
            final boolean validSecondary = (includeSecondary ? true : !type.secondary);
            final boolean forcedInclude = type.equals(forceInclude);
            if (forcedInclude || (validOverall && validSpecific && validSecondary)) {
                // Type is valid when no limit, under limit, or forced include
                validTypes.add(type);
            }
        }

        return validTypes;
    }

    private static final int FREQUENCY_TOTAL = Integer.MIN_VALUE;

    /**
     * Count up the frequency that each {@link EditType} appears in the given
     * {@link RawContactDelta}. The returned {@link SparseIntArray} maps from
     * {@link EditType#rawValue} to counts, with the total overall count stored
     * as {@link #FREQUENCY_TOTAL}.
     */
    private static SparseIntArray getTypeFrequencies(RawContactDelta state, DataKind kind) {
        final SparseIntArray typeCount = new SparseIntArray();

        // Find all entries for this kind, bailing early if none found
        final List<ValuesDelta> mimeEntries = state.getMimeEntries(kind.mimeType);
        if (mimeEntries == null) return typeCount;

        int totalCount = 0;
        for (ValuesDelta entry : mimeEntries) {
            // Only count visible entries
            if (!entry.isVisible()) continue;
            totalCount++;

            final EditType type = getCurrentType(entry, kind);
            if (type != null) {
                final int count = typeCount.get(type.rawValue);
                typeCount.put(type.rawValue, count + 1);
            }
        }
        typeCount.put(FREQUENCY_TOTAL, totalCount);
        return typeCount;
    }

    /**
     * Check if the given {@link DataKind} has multiple types that should be
     * displayed for users to pick.
     */
    public static boolean hasEditTypes(DataKind kind) {
        return kind != null && kind.typeList != null && kind.typeList.size() > 0;
    }

    /**
     * Find the {@link EditType} that describes the given
     * {@link ValuesDelta} row, assuming the given {@link DataKind} dictates
     * the possible types.
     */
    public static EditType getCurrentType(ValuesDelta entry, DataKind kind) {
        final Long rawValue = entry.getAsLong(kind.typeColumn);
        if (rawValue == null) return null;
        return getType(kind, rawValue.intValue());
    }

    /**
     * Find the {@link EditType} that describes the given {@link ContentValues} row,
     * assuming the given {@link DataKind} dictates the possible types.
     */
    public static EditType getCurrentType(ContentValues entry, DataKind kind) {
        if (kind.typeColumn == null) return null;
        final Integer rawValue = entry.getAsInteger(kind.typeColumn);
        if (rawValue == null) return null;
        return getType(kind, rawValue);
    }

    /**
     * Find the {@link EditType} that describes the given {@link Cursor} row,
     * assuming the given {@link DataKind} dictates the possible types.
     */
    public static EditType getCurrentType(Cursor cursor, DataKind kind) {
        if (kind.typeColumn == null) return null;
        final int index = cursor.getColumnIndex(kind.typeColumn);
        if (index == -1) return null;
        final int rawValue = cursor.getInt(index);
        return getType(kind, rawValue);
    }

    /**
     * Find the {@link EditType} with the given {@link EditType#rawValue}.
     */
    public static EditType getType(DataKind kind, int rawValue) {
        for (EditType type : kind.typeList) {
            if (type.rawValue == rawValue) {
                return type;
            }
        }
        return null;
    }

    /**
     * Return the precedence for the the given {@link EditType#rawValue}, where
     * lower numbers are higher precedence.
     */
    public static int getTypePrecedence(DataKind kind, int rawValue) {
        for (int i = 0; i < kind.typeList.size(); i++) {
            final EditType type = kind.typeList.get(i);
            if (type.rawValue == rawValue) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Find the best {@link EditType} for a potential insert. The "best" is the
     * first primary type that doesn't already exist. When all valid types
     * exist, we pick the last valid option.
     */
    public static EditType getBestValidType(RawContactDelta state, DataKind kind,
            boolean includeSecondary, int exactValue) {
        // Shortcut when no types
        if (kind == null || kind.typeColumn == null) return null;

        // Find type counts and valid primary types, bail if none
        final SparseIntArray typeCount = getTypeFrequencies(state, kind);
        final ArrayList<EditType> validTypes = getValidTypes(state, kind, null, includeSecondary,
                typeCount, /*checkOverall=*/ true);
        if (validTypes.size() == 0) return null;

        // Keep track of the last valid type
        final EditType lastType = validTypes.get(validTypes.size() - 1);

        // Remove any types that already exist
        Iterator<EditType> iterator = validTypes.iterator();
        while (iterator.hasNext()) {
            final EditType type = iterator.next();
            final int count = typeCount.get(type.rawValue);

            if (exactValue == type.rawValue) {
                // Found exact value match
                return type;
            }

            if (count > 0) {
                // Type already appears, so don't consider
                iterator.remove();
            }
        }

        // Use the best remaining, otherwise the last valid
        if (validTypes.size() > 0) {
            return validTypes.get(0);
        } else {
            return lastType;
        }
    }

    /**
     * Insert a new child of kind {@link DataKind} into the given
     * {@link RawContactDelta}. Tries using the best {@link EditType} found using
     * {@link #getBestValidType(RawContactDelta, DataKind, boolean, int)}.
     */
    public static ValuesDelta insertChild(RawContactDelta state, DataKind kind) {
        // Bail early if invalid kind
        if (kind == null) return null;
        // First try finding a valid primary
        EditType bestType = getBestValidType(state, kind, false, Integer.MIN_VALUE);
        if (bestType == null) {
            // No valid primary found, so expand search to secondary
            bestType = getBestValidType(state, kind, true, Integer.MIN_VALUE);
        }
        return insertChild(state, kind, bestType);
    }

    /**
     * Insert a new child of kind {@link DataKind} into the given
     * {@link RawContactDelta}, marked with the given {@link EditType}.
     */
    public static ValuesDelta insertChild(RawContactDelta state, DataKind kind, EditType type) {
        // Bail early if invalid kind
        if (kind == null) return null;
        final ContentValues after = new ContentValues();

        // Our parent CONTACT_ID is provided later
        after.put(Data.MIMETYPE, kind.mimeType);

        // Fill-in with any requested default values
        if (kind.defaultValues != null) {
            after.putAll(kind.defaultValues);
        }

        if (kind.typeColumn != null && type != null) {
            // Set type, if provided
            after.put(kind.typeColumn, type.rawValue);
        }

        final ValuesDelta child = ValuesDelta.fromAfter(after);
        state.addEntry(child);
        return child;
    }

    /**
     * Processing to trim any empty {@link ValuesDelta} and {@link RawContactDelta}
     * from the given {@link RawContactDeltaList}, assuming the given {@link AccountTypeManager}
     * dictates the structure for various fields. This method ignores rows not
     * described by the {@link AccountType}.
     */
    public static void trimEmpty(RawContactDeltaList set, AccountTypeManager accountTypes) {
        for (RawContactDelta state : set) {
            ValuesDelta values = state.getValues();
            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final String dataSet = values.getAsString(RawContacts.DATA_SET);
            final AccountType type = accountTypes.getAccountType(accountType, dataSet);
            trimEmpty(state, type);
        }
    }

    public static boolean hasChanges(RawContactDeltaList set, AccountTypeManager accountTypes) {
        return hasChanges(set, accountTypes, /* excludedMimeTypes =*/ null);
    }

    public static boolean hasChanges(RawContactDeltaList set, AccountTypeManager accountTypes,
            Set<String> excludedMimeTypes) {
        if (set.isMarkedForSplitting() || set.isMarkedForJoining()) {
            return true;
        }

        for (RawContactDelta state : set) {
            ValuesDelta values = state.getValues();
            final String accountType = values.getAsString(RawContacts.ACCOUNT_TYPE);
            final String dataSet = values.getAsString(RawContacts.DATA_SET);
            final AccountType type = accountTypes.getAccountType(accountType, dataSet);
            if (hasChanges(state, type, excludedMimeTypes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Processing to trim any empty {@link ValuesDelta} rows from the given
     * {@link RawContactDelta}, assuming the given {@link AccountType} dictates
     * the structure for various fields. This method ignores rows not described
     * by the {@link AccountType}.
     */
    public static void trimEmpty(RawContactDelta state, AccountType accountType) {
        boolean hasValues = false;

        // Walk through entries for each well-known kind
        for (DataKind kind : accountType.getSortedDataKinds()) {
            final String mimeType = kind.mimeType;
            final ArrayList<ValuesDelta> entries = state.getMimeEntries(mimeType);
            if (entries == null) continue;

            for (ValuesDelta entry : entries) {
                // Skip any values that haven't been touched
                final boolean touched = entry.isInsert() || entry.isUpdate();
                if (!touched) {
                    hasValues = true;
                    continue;
                }

                // Test and remove this row if empty and it isn't a photo from google
                final boolean isGoogleAccount = TextUtils.equals(GoogleAccountType.ACCOUNT_TYPE,
                        state.getValues().getAsString(RawContacts.ACCOUNT_TYPE));
                final boolean isPhoto = TextUtils.equals(Photo.CONTENT_ITEM_TYPE, kind.mimeType);
                final boolean isGooglePhoto = isPhoto && isGoogleAccount;

                if (RawContactModifier.isEmpty(entry, kind) && !isGooglePhoto) {
                    if (DEBUG) {
                        Log.v(TAG, "Trimming: " + entry.toString());
                    }
                    entry.markDeleted();
                } else if (!entry.isFromTemplate()) {
                    hasValues = true;
                }
            }
        }
        if (!hasValues) {
            // Trim overall entity if no children exist
            state.markDeleted();
        }
    }

    private static boolean hasChanges(RawContactDelta state, AccountType accountType,
            Set<String> excludedMimeTypes) {
        for (DataKind kind : accountType.getSortedDataKinds()) {
            final String mimeType = kind.mimeType;
            if (excludedMimeTypes != null && excludedMimeTypes.contains(mimeType)) continue;
            final ArrayList<ValuesDelta> entries = state.getMimeEntries(mimeType);
            if (entries == null) continue;

            for (ValuesDelta entry : entries) {
                // An empty Insert must be ignored, because it won't save anything (an example
                // is an empty name that stays empty)
                final boolean isRealInsert = entry.isInsert() && !isEmpty(entry, kind);
                if (isRealInsert || entry.isUpdate() || entry.isDelete()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Test if the given {@link ValuesDelta} would be considered "empty" in
     * terms of {@link DataKind#fieldList}.
     */
    public static boolean isEmpty(ValuesDelta values, DataKind kind) {
        if (Photo.CONTENT_ITEM_TYPE.equals(kind.mimeType)) {
            return values.isInsert() && values.getAsByteArray(Photo.PHOTO) == null;
        }

        // No defined fields mean this row is always empty
        if (kind.fieldList == null) return true;

        for (EditField field : kind.fieldList) {
            // If any field has values, we're not empty
            final String value = values.getAsString(field.column);
            if (ContactsUtils.isGraphic(value)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares corresponding fields in values1 and values2. Only the fields
     * declared by the DataKind are taken into consideration.
     */
    protected static boolean areEqual(ValuesDelta values1, ContentValues values2, DataKind kind) {
        if (kind.fieldList == null) return false;

        for (EditField field : kind.fieldList) {
            final String value1 = values1.getAsString(field.column);
            final String value2 = values2.getAsString(field.column);
            if (!TextUtils.equals(value1, value2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Parse the given {@link Bundle} into the given {@link RawContactDelta} state,
     * assuming the extras defined through {@link Intents}.
     */
    public static void parseExtras(Context context, AccountType accountType, RawContactDelta state,
            Bundle extras) {
        if (extras == null || extras.size() == 0) {
            // Bail early if no useful data
            return;
        }

        parseStructuredNameExtra(context, accountType, state, extras);
        parseStructuredPostalExtra(accountType, state, extras);

        {
            // Phone
            final DataKind kind = accountType.getKindForMimetype(Phone.CONTENT_ITEM_TYPE);
            parseExtras(state, kind, extras, Insert.PHONE_TYPE, Insert.PHONE, Phone.NUMBER);
            parseExtras(state, kind, extras, Insert.SECONDARY_PHONE_TYPE, Insert.SECONDARY_PHONE,
                    Phone.NUMBER);
            parseExtras(state, kind, extras, Insert.TERTIARY_PHONE_TYPE, Insert.TERTIARY_PHONE,
                    Phone.NUMBER);
        }

        {
            // Email
            final DataKind kind = accountType.getKindForMimetype(Email.CONTENT_ITEM_TYPE);
            parseExtras(state, kind, extras, Insert.EMAIL_TYPE, Insert.EMAIL, Email.DATA);
            parseExtras(state, kind, extras, Insert.SECONDARY_EMAIL_TYPE, Insert.SECONDARY_EMAIL,
                    Email.DATA);
            parseExtras(state, kind, extras, Insert.TERTIARY_EMAIL_TYPE, Insert.TERTIARY_EMAIL,
                    Email.DATA);
        }

        {
            // Im
            final DataKind kind = accountType.getKindForMimetype(Im.CONTENT_ITEM_TYPE);
            fixupLegacyImType(extras);
            parseExtras(state, kind, extras, Insert.IM_PROTOCOL, Insert.IM_HANDLE, Im.DATA);
        }

        // Organization
        final boolean hasOrg = extras.containsKey(Insert.COMPANY)
                || extras.containsKey(Insert.JOB_TITLE);
        final DataKind kindOrg = accountType.getKindForMimetype(Organization.CONTENT_ITEM_TYPE);
        if (hasOrg && RawContactModifier.canInsert(state, kindOrg)) {
            final ValuesDelta child = RawContactModifier.insertChild(state, kindOrg);

            final String company = extras.getString(Insert.COMPANY);
            if (ContactsUtils.isGraphic(company)) {
                child.put(Organization.COMPANY, company);
            }

            final String title = extras.getString(Insert.JOB_TITLE);
            if (ContactsUtils.isGraphic(title)) {
                child.put(Organization.TITLE, title);
            }
        }

        // Notes
        final boolean hasNotes = extras.containsKey(Insert.NOTES);
        final DataKind kindNotes = accountType.getKindForMimetype(Note.CONTENT_ITEM_TYPE);
        if (hasNotes && RawContactModifier.canInsert(state, kindNotes)) {
            final ValuesDelta child = RawContactModifier.insertChild(state, kindNotes);

            final String notes = extras.getString(Insert.NOTES);
            if (ContactsUtils.isGraphic(notes)) {
                child.put(Note.NOTE, notes);
            }
        }

        // Arbitrary additional data
        ArrayList<ContentValues> values = extras.getParcelableArrayList(Insert.DATA);
        if (values != null) {
            parseValues(state, accountType, values);
        }
    }

    private static void parseStructuredNameExtra(
            Context context, AccountType accountType, RawContactDelta state, Bundle extras) {
        // StructuredName
        RawContactModifier.ensureKindExists(state, accountType, StructuredName.CONTENT_ITEM_TYPE);
        final ValuesDelta child = state.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE);

        final String name = extras.getString(Insert.NAME);
        if (ContactsUtils.isGraphic(name)) {
            final DataKind kind = accountType.getKindForMimetype(StructuredName.CONTENT_ITEM_TYPE);
            boolean supportsDisplayName = false;
            if (kind.fieldList != null) {
                for (EditField field : kind.fieldList) {
                    if (StructuredName.DISPLAY_NAME.equals(field.column)) {
                        supportsDisplayName = true;
                        break;
                    }
                }
            }

            if (supportsDisplayName) {
                child.put(StructuredName.DISPLAY_NAME, name);
            } else {
                Uri uri = ContactsContract.AUTHORITY_URI.buildUpon()
                        .appendPath("complete_name")
                        .appendQueryParameter(StructuredName.DISPLAY_NAME, name)
                        .build();
                Cursor cursor = context.getContentResolver().query(uri,
                        new String[]{
                                StructuredName.PREFIX,
                                StructuredName.GIVEN_NAME,
                                StructuredName.MIDDLE_NAME,
                                StructuredName.FAMILY_NAME,
                                StructuredName.SUFFIX,
                        }, null, null, null);

                if (cursor != null) {
                    try {
                        if (cursor.moveToFirst()) {
                            child.put(StructuredName.PREFIX, cursor.getString(0));
                            child.put(StructuredName.GIVEN_NAME, cursor.getString(1));
                            child.put(StructuredName.MIDDLE_NAME, cursor.getString(2));
                            child.put(StructuredName.FAMILY_NAME, cursor.getString(3));
                            child.put(StructuredName.SUFFIX, cursor.getString(4));
                        }
                    } finally {
                        cursor.close();
                    }
                }
            }
        }

        final String phoneticName = extras.getString(Insert.PHONETIC_NAME);
        if (ContactsUtils.isGraphic(phoneticName)) {
            StructuredNameDataItem dataItem = NameConverter.parsePhoneticName(phoneticName, null);
            child.put(StructuredName.PHONETIC_FAMILY_NAME, dataItem.getPhoneticFamilyName());
            child.put(StructuredName.PHONETIC_MIDDLE_NAME, dataItem.getPhoneticMiddleName());
            child.put(StructuredName.PHONETIC_GIVEN_NAME, dataItem.getPhoneticGivenName());
        }
    }

    private static void parseStructuredPostalExtra(
            AccountType accountType, RawContactDelta state, Bundle extras) {
        // StructuredPostal
        final DataKind kind = accountType.getKindForMimetype(StructuredPostal.CONTENT_ITEM_TYPE);
        final ValuesDelta child = parseExtras(state, kind, extras, Insert.POSTAL_TYPE,
                Insert.POSTAL, StructuredPostal.FORMATTED_ADDRESS);
        String address = child == null ? null
                : child.getAsString(StructuredPostal.FORMATTED_ADDRESS);
        if (!TextUtils.isEmpty(address)) {
            boolean supportsFormatted = false;
            if (kind.fieldList != null) {
                for (EditField field : kind.fieldList) {
                    if (StructuredPostal.FORMATTED_ADDRESS.equals(field.column)) {
                        supportsFormatted = true;
                        break;
                    }
                }
            }

            if (!supportsFormatted) {
                child.put(StructuredPostal.STREET, address);
                child.putNull(StructuredPostal.FORMATTED_ADDRESS);
            }
        }
    }

    private static void parseValues(
            RawContactDelta state, AccountType accountType,
            ArrayList<ContentValues> dataValueList) {
        for (ContentValues values : dataValueList) {
            String mimeType = values.getAsString(Data.MIMETYPE);
            if (TextUtils.isEmpty(mimeType)) {
                Log.e(TAG, "Mimetype is required. Ignoring: " + values);
                continue;
            }

            // Won't override the contact name
            if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                continue;
            } else if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                values.remove(PhoneDataItem.KEY_FORMATTED_PHONE_NUMBER);
                final Integer type = values.getAsInteger(Phone.TYPE);
                // If the provided phone number provides a custom phone type but not a label,
                // replace it with mobile (by default) to avoid the "Enter custom label" from
                // popping up immediately upon entering the ContactEditorFragment
                if (type != null && type == Phone.TYPE_CUSTOM &&
                        TextUtils.isEmpty(values.getAsString(Phone.LABEL))) {
                    values.put(Phone.TYPE, Phone.TYPE_MOBILE);
                }
            }

            DataKind kind = accountType.getKindForMimetype(mimeType);
            if (kind == null) {
                Log.e(TAG, "Mimetype not supported for account type "
                        + accountType.getAccountTypeAndDataSet() + ". Ignoring: " + values);
                continue;
            }

            ValuesDelta entry = ValuesDelta.fromAfter(values);
            if (isEmpty(entry, kind)) {
                continue;
            }

            ArrayList<ValuesDelta> entries = state.getMimeEntries(mimeType);

            if ((kind.typeOverallMax != 1) || GroupMembership.CONTENT_ITEM_TYPE.equals(mimeType)) {
                // Check for duplicates
                boolean addEntry = true;
                int count = 0;
                if (entries != null && entries.size() > 0) {
                    for (ValuesDelta delta : entries) {
                        if (!delta.isDelete()) {
                            if (areEqual(delta, values, kind)) {
                                addEntry = false;
                                break;
                            }
                            count++;
                        }
                    }
                }

                if (kind.typeOverallMax != -1 && count >= kind.typeOverallMax) {
                    Log.e(TAG, "Mimetype allows at most " + kind.typeOverallMax
                            + " entries. Ignoring: " + values);
                    addEntry = false;
                }

                if (addEntry) {
                    addEntry = adjustType(entry, entries, kind);
                }

                if (addEntry) {
                    state.addEntry(entry);
                }
            } else {
                // Non-list entries should not be overridden
                boolean addEntry = true;
                if (entries != null && entries.size() > 0) {
                    for (ValuesDelta delta : entries) {
                        if (!delta.isDelete() && !isEmpty(delta, kind)) {
                            addEntry = false;
                            break;
                        }
                    }
                    if (addEntry) {
                        for (ValuesDelta delta : entries) {
                            delta.markDeleted();
                        }
                    }
                }

                if (addEntry) {
                    addEntry = adjustType(entry, entries, kind);
                }

                if (addEntry) {
                    state.addEntry(entry);
                } else if (Note.CONTENT_ITEM_TYPE.equals(mimeType)){
                    // Note is most likely to contain large amounts of text
                    // that we don't want to drop on the ground.
                    for (ValuesDelta delta : entries) {
                        if (!isEmpty(delta, kind)) {
                            delta.put(Note.NOTE, delta.getAsString(Note.NOTE) + "\n"
                                    + values.getAsString(Note.NOTE));
                            break;
                        }
                    }
                } else {
                    Log.e(TAG, "Will not override mimetype " + mimeType + ". Ignoring: "
                            + values);
                }
            }
        }
    }

    /**
     * Checks if the data kind allows addition of another entry (e.g. Exchange only
     * supports two "work" phone numbers).  If not, tries to switch to one of the
     * unused types.  If successful, returns true.
     */
    private static boolean adjustType(
            ValuesDelta entry, ArrayList<ValuesDelta> entries, DataKind kind) {
        if (kind.typeColumn == null || kind.typeList == null || kind.typeList.size() == 0) {
            return true;
        }

        Integer typeInteger = entry.getAsInteger(kind.typeColumn);
        int type = typeInteger != null ? typeInteger : kind.typeList.get(0).rawValue;

        if (isTypeAllowed(type, entries, kind)) {
            entry.put(kind.typeColumn, type);
            return true;
        }

        // Specified type is not allowed - choose the first available type that is allowed
        int size = kind.typeList.size();
        for (int i = 0; i < size; i++) {
            EditType editType = kind.typeList.get(i);
            if (isTypeAllowed(editType.rawValue, entries, kind)) {
                entry.put(kind.typeColumn, editType.rawValue);
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if a new entry of the specified type can be added to the raw
     * contact. For example, Exchange only supports two "work" phone numbers, so
     * addition of a third would not be allowed.
     */
    private static boolean isTypeAllowed(int type, ArrayList<ValuesDelta> entries, DataKind kind) {
        int max = 0;
        int size = kind.typeList.size();
        for (int i = 0; i < size; i++) {
            EditType editType = kind.typeList.get(i);
            if (editType.rawValue == type) {
                max = editType.specificMax;
                break;
            }
        }

        if (max == 0) {
            // This type is not allowed at all
            return false;
        }

        if (max == -1) {
            // Unlimited instances of this type are allowed
            return true;
        }

        return getEntryCountByType(entries, kind.typeColumn, type) < max;
    }

    /**
     * Counts occurrences of the specified type in the supplied entry list.
     *
     * @return The count of occurrences of the type in the entry list. 0 if entries is
     * {@literal null}
     */
    private static int getEntryCountByType(ArrayList<ValuesDelta> entries, String typeColumn,
            int type) {
        int count = 0;
        if (entries != null) {
            for (ValuesDelta entry : entries) {
                Integer typeInteger = entry.getAsInteger(typeColumn);
                if (typeInteger != null && typeInteger == type) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Attempt to parse legacy {@link Insert#IM_PROTOCOL} values, replacing them
     * with updated values.
     */
    @SuppressWarnings("deprecation")
    private static void fixupLegacyImType(Bundle bundle) {
        final String encodedString = bundle.getString(Insert.IM_PROTOCOL);
        if (encodedString == null) return;

        try {
            final Object protocol = android.provider.Contacts.ContactMethods
                    .decodeImProtocol(encodedString);
            if (protocol instanceof Integer) {
                bundle.putInt(Insert.IM_PROTOCOL, (Integer)protocol);
            } else {
                bundle.putString(Insert.IM_PROTOCOL, (String)protocol);
            }
        } catch (IllegalArgumentException e) {
            // Ignore exception when legacy parser fails
        }
    }

    /**
     * Parse a specific entry from the given {@link Bundle} and insert into the
     * given {@link RawContactDelta}. Silently skips the insert when missing value
     * or no valid {@link EditType} found.
     *
     * @param typeExtra {@link Bundle} key that holds the incoming
     *            {@link EditType#rawValue} value.
     * @param valueExtra {@link Bundle} key that holds the incoming value.
     * @param valueColumn Column to write value into {@link ValuesDelta}.
     */
    public static ValuesDelta parseExtras(RawContactDelta state, DataKind kind, Bundle extras,
            String typeExtra, String valueExtra, String valueColumn) {
        final CharSequence value = extras.getCharSequence(valueExtra);

        // Bail early if account type doesn't handle this MIME type
        if (kind == null) return null;

        // Bail when can't insert type, or value missing
        final boolean canInsert = RawContactModifier.canInsert(state, kind);
        final boolean validValue = (value != null && TextUtils.isGraphic(value));
        if (!validValue || !canInsert) return null;

        // Find exact type when requested, otherwise best available type
        final boolean hasType = extras.containsKey(typeExtra);
        final int typeValue = extras.getInt(typeExtra, hasType ? BaseTypes.TYPE_CUSTOM
                : Integer.MIN_VALUE);
        final EditType editType = RawContactModifier.getBestValidType(state, kind, true, typeValue);

        // Create data row and fill with value
        final ValuesDelta child = RawContactModifier.insertChild(state, kind, editType);
        child.put(valueColumn, value.toString());

        if (editType != null && editType.customColumn != null) {
            // Write down label when custom type picked
            final String customType = extras.getString(typeExtra);
            child.put(editType.customColumn, customType);
        }

        return child;
    }

    /**
     * Generic mime types with type support (e.g. TYPE_HOME).
     * Here, "type support" means if the data kind has CommonColumns#TYPE or not. Data kinds which
     * have their own migrate methods aren't listed here.
     */
    private static final Set<String> sGenericMimeTypesWithTypeSupport = new HashSet<String>(
            Arrays.asList(Phone.CONTENT_ITEM_TYPE,
                    Email.CONTENT_ITEM_TYPE,
                    Im.CONTENT_ITEM_TYPE,
                    Nickname.CONTENT_ITEM_TYPE,
                    Website.CONTENT_ITEM_TYPE,
                    Relation.CONTENT_ITEM_TYPE,
                    SipAddress.CONTENT_ITEM_TYPE));
    private static final Set<String> sGenericMimeTypesWithoutTypeSupport = new HashSet<String>(
            Arrays.asList(Organization.CONTENT_ITEM_TYPE,
                    Note.CONTENT_ITEM_TYPE,
                    Photo.CONTENT_ITEM_TYPE,
                    GroupMembership.CONTENT_ITEM_TYPE));
    // CommonColumns.TYPE cannot be accessed as it is protected interface, so use
    // Phone.TYPE instead.
    private static final String COLUMN_FOR_TYPE  = Phone.TYPE;
    private static final String COLUMN_FOR_LABEL  = Phone.LABEL;
    private static final int TYPE_CUSTOM = Phone.TYPE_CUSTOM;

    /**
     * Migrates old RawContactDelta to newly created one with a new restriction supplied from
     * newAccountType.
     *
     * This is only for account switch during account creation (which must be insert operation).
     */
    public static void migrateStateForNewContact(Context context,
            RawContactDelta oldState, RawContactDelta newState,
            AccountType oldAccountType, AccountType newAccountType) {
        if (newAccountType == oldAccountType) {
            // Just copying all data in oldState isn't enough, but we can still rely on a lot of
            // shortcuts.
            for (DataKind kind : newAccountType.getSortedDataKinds()) {
                final String mimeType = kind.mimeType;
                // The fields with short/long form capability must be treated properly.
                if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    migrateStructuredName(context, oldState, newState, kind);
                } else {
                    List<ValuesDelta> entryList = oldState.getMimeEntries(mimeType);
                    if (entryList != null && !entryList.isEmpty()) {
                        for (ValuesDelta entry : entryList) {
                            ContentValues values = entry.getAfter();
                            if (values != null) {
                                newState.addEntry(ValuesDelta.fromAfter(values));
                            }
                        }
                    }
                }
            }
        } else {
            // Migrate data supported by the new account type.
            // All the other data inside oldState are silently dropped.
            for (DataKind kind : newAccountType.getSortedDataKinds()) {
                if (!kind.editable) continue;
                final String mimeType = kind.mimeType;
                if (DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME.equals(mimeType) ||
                        DataKind.PSEUDO_MIME_TYPE_NAME.equals(mimeType)) {
                    // Ignore pseudo data.
                    continue;
                } else if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    migrateStructuredName(context, oldState, newState, kind);
                } else if (StructuredPostal.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    migratePostal(oldState, newState, kind);
                } else if (Event.CONTENT_ITEM_TYPE.equals(mimeType)) {
                    migrateEvent(oldState, newState, kind, null /* default Year */);
                } else if (sGenericMimeTypesWithoutTypeSupport.contains(mimeType)) {
                    migrateGenericWithoutTypeColumn(oldState, newState, kind);
                } else if (sGenericMimeTypesWithTypeSupport.contains(mimeType)) {
                    migrateGenericWithTypeColumn(oldState, newState, kind);
                } else {
                    throw new IllegalStateException("Unexpected editable mime-type: " + mimeType);
                }
            }
        }
    }

    /**
     * Checks {@link DataKind#isList} and {@link DataKind#typeOverallMax}, and restricts
     * the number of entries (ValuesDelta) inside newState.
     */
    private static ArrayList<ValuesDelta> ensureEntryMaxSize(RawContactDelta newState,
            DataKind kind, ArrayList<ValuesDelta> mimeEntries) {
        if (mimeEntries == null) {
            return null;
        }

        final int typeOverallMax = kind.typeOverallMax;
        if (typeOverallMax >= 0 && (mimeEntries.size() > typeOverallMax)) {
            ArrayList<ValuesDelta> newMimeEntries = new ArrayList<ValuesDelta>(typeOverallMax);
            for (int i = 0; i < typeOverallMax; i++) {
                newMimeEntries.add(mimeEntries.get(i));
            }
            mimeEntries = newMimeEntries;
        }
        return mimeEntries;
    }

    /** @hide Public only for testing. */
    public static void migrateStructuredName(
            Context context, RawContactDelta oldState, RawContactDelta newState,
            DataKind newDataKind) {
        final ContentValues values =
                oldState.getPrimaryEntry(StructuredName.CONTENT_ITEM_TYPE).getAfter();
        if (values == null) {
            return;
        }

        boolean supportPhoneticFamilyName = false;
        boolean supportPhoneticMiddleName = false;
        boolean supportPhoneticGivenName = false;
        for (EditField editField : newDataKind.fieldList) {
            if (StructuredName.PHONETIC_FAMILY_NAME.equals(editField.column)) {
                supportPhoneticFamilyName = true;
            }
            if (StructuredName.PHONETIC_MIDDLE_NAME.equals(editField.column)) {
                supportPhoneticMiddleName = true;
            }
            if (StructuredName.PHONETIC_GIVEN_NAME.equals(editField.column)) {
                supportPhoneticGivenName = true;
            }
        }

        if (!supportPhoneticFamilyName) {
            values.remove(StructuredName.PHONETIC_FAMILY_NAME);
        }
        if (!supportPhoneticMiddleName) {
            values.remove(StructuredName.PHONETIC_MIDDLE_NAME);
        }
        if (!supportPhoneticGivenName) {
            values.remove(StructuredName.PHONETIC_GIVEN_NAME);
        }

        newState.addEntry(ValuesDelta.fromAfter(values));
    }

    /** @hide Public only for testing. */
    public static void migratePostal(RawContactDelta oldState, RawContactDelta newState,
            DataKind newDataKind) {
        final ArrayList<ValuesDelta> mimeEntries = ensureEntryMaxSize(newState, newDataKind,
                oldState.getMimeEntries(StructuredPostal.CONTENT_ITEM_TYPE));
        if (mimeEntries == null || mimeEntries.isEmpty()) {
            return;
        }

        boolean supportFormattedAddress = false;
        boolean supportStreet = false;
        final String firstColumn = newDataKind.fieldList.get(0).column;
        for (EditField editField : newDataKind.fieldList) {
            if (StructuredPostal.FORMATTED_ADDRESS.equals(editField.column)) {
                supportFormattedAddress = true;
            }
            if (StructuredPostal.STREET.equals(editField.column)) {
                supportStreet = true;
            }
        }

        final Set<Integer> supportedTypes = new HashSet<Integer>();
        if (newDataKind.typeList != null && !newDataKind.typeList.isEmpty()) {
            for (EditType editType : newDataKind.typeList) {
                supportedTypes.add(editType.rawValue);
            }
        }

        for (ValuesDelta entry : mimeEntries) {
            final ContentValues values = entry.getAfter();
            if (values == null) {
                continue;
            }
            final Integer oldType = values.getAsInteger(StructuredPostal.TYPE);
            if (!supportedTypes.contains(oldType)) {
                int defaultType;
                if (newDataKind.defaultValues != null) {
                    defaultType = newDataKind.defaultValues.getAsInteger(StructuredPostal.TYPE);
                } else {
                    defaultType = newDataKind.typeList.get(0).rawValue;
                }
                values.put(StructuredPostal.TYPE, defaultType);
                if (oldType != null && oldType == StructuredPostal.TYPE_CUSTOM) {
                    values.remove(StructuredPostal.LABEL);
                }
            }

            final String formattedAddress = values.getAsString(StructuredPostal.FORMATTED_ADDRESS);
            if (!TextUtils.isEmpty(formattedAddress)) {
                if (!supportFormattedAddress) {
                    // Old data has a formatted address, while the new account doesn't allow it.
                    values.remove(StructuredPostal.FORMATTED_ADDRESS);

                    // Unlike StructuredName we don't have logic to split it, so first
                    // try to use street field and. If the new account doesn't have one,
                    // then select first one anyway.
                    if (supportStreet) {
                        values.put(StructuredPostal.STREET, formattedAddress);
                    } else {
                        values.put(firstColumn, formattedAddress);
                    }
                }
            } else {
                if (supportFormattedAddress) {
                    // Old data does not have formatted address, while the new account requires it.
                    // Unlike StructuredName we don't have logic to join multiple address values.
                    // Use poor join heuristics for now.
                    String[] structuredData;
                    final boolean useJapaneseOrder =
                            Locale.JAPANESE.getLanguage().equals(Locale.getDefault().getLanguage());
                    if (useJapaneseOrder) {
                        structuredData = new String[] {
                                values.getAsString(StructuredPostal.COUNTRY),
                                values.getAsString(StructuredPostal.POSTCODE),
                                values.getAsString(StructuredPostal.REGION),
                                values.getAsString(StructuredPostal.CITY),
                                values.getAsString(StructuredPostal.NEIGHBORHOOD),
                                values.getAsString(StructuredPostal.STREET),
                                values.getAsString(StructuredPostal.POBOX) };
                    } else {
                        structuredData = new String[] {
                                values.getAsString(StructuredPostal.POBOX),
                                values.getAsString(StructuredPostal.STREET),
                                values.getAsString(StructuredPostal.NEIGHBORHOOD),
                                values.getAsString(StructuredPostal.CITY),
                                values.getAsString(StructuredPostal.REGION),
                                values.getAsString(StructuredPostal.POSTCODE),
                                values.getAsString(StructuredPostal.COUNTRY) };
                    }
                    final StringBuilder builder = new StringBuilder();
                    for (String elem : structuredData) {
                        if (!TextUtils.isEmpty(elem)) {
                            builder.append(elem + "\n");
                        }
                    }
                    values.put(StructuredPostal.FORMATTED_ADDRESS, builder.toString());

                    values.remove(StructuredPostal.POBOX);
                    values.remove(StructuredPostal.STREET);
                    values.remove(StructuredPostal.NEIGHBORHOOD);
                    values.remove(StructuredPostal.CITY);
                    values.remove(StructuredPostal.REGION);
                    values.remove(StructuredPostal.POSTCODE);
                    values.remove(StructuredPostal.COUNTRY);
                }
            }

            newState.addEntry(ValuesDelta.fromAfter(values));
        }
    }

    /** @hide Public only for testing. */
    public static void migrateEvent(RawContactDelta oldState, RawContactDelta newState,
            DataKind newDataKind, Integer defaultYear) {
        final ArrayList<ValuesDelta> mimeEntries = ensureEntryMaxSize(newState, newDataKind,
                oldState.getMimeEntries(Event.CONTENT_ITEM_TYPE));
        if (mimeEntries == null || mimeEntries.isEmpty()) {
            return;
        }

        final SparseArray<EventEditType> allowedTypes = new SparseArray<EventEditType>();
        for (EditType editType : newDataKind.typeList) {
            allowedTypes.put(editType.rawValue, (EventEditType) editType);
        }
        for (ValuesDelta entry : mimeEntries) {
            final ContentValues values = entry.getAfter();
            if (values == null) {
                continue;
            }
            final String dateString = values.getAsString(Event.START_DATE);
            final Integer type = values.getAsInteger(Event.TYPE);
            if (type != null && (allowedTypes.indexOfKey(type) >= 0)
                    && !TextUtils.isEmpty(dateString)) {
                EventEditType suitableType = allowedTypes.get(type);

                final ParsePosition position = new ParsePosition(0);
                boolean yearOptional = false;
                Date date = CommonDateUtils.DATE_AND_TIME_FORMAT.parse(dateString, position);
                if (date == null) {
                    yearOptional = true;
                    date = CommonDateUtils.NO_YEAR_DATE_FORMAT.parse(dateString, position);
                }
                if (date != null) {
                    if (yearOptional && !suitableType.isYearOptional()) {
                        // The new EditType doesn't allow optional year. Supply default.
                        final Calendar calendar = Calendar.getInstance(DateUtils.UTC_TIMEZONE,
                                Locale.US);
                        if (defaultYear == null) {
                            defaultYear = calendar.get(Calendar.YEAR);
                        }
                        calendar.setTime(date);
                        final int month = calendar.get(Calendar.MONTH);
                        final int day = calendar.get(Calendar.DAY_OF_MONTH);
                        // Exchange requires 8:00 for birthdays
                        calendar.set(defaultYear, month, day,
                                CommonDateUtils.DEFAULT_HOUR, 0, 0);
                        values.put(Event.START_DATE,
                                CommonDateUtils.FULL_DATE_FORMAT.format(calendar.getTime()));
                    }
                }
                newState.addEntry(ValuesDelta.fromAfter(values));
            } else {
                // Just drop it.
            }
        }
    }

    /** @hide Public only for testing. */
    public static void migrateGenericWithoutTypeColumn(
            RawContactDelta oldState, RawContactDelta newState, DataKind newDataKind) {
        final ArrayList<ValuesDelta> mimeEntries = ensureEntryMaxSize(newState, newDataKind,
                oldState.getMimeEntries(newDataKind.mimeType));
        if (mimeEntries == null || mimeEntries.isEmpty()) {
            return;
        }

        for (ValuesDelta entry : mimeEntries) {
            ContentValues values = entry.getAfter();
            if (values != null) {
                newState.addEntry(ValuesDelta.fromAfter(values));
            }
        }
    }

    /** @hide Public only for testing. */
    public static void migrateGenericWithTypeColumn(
            RawContactDelta oldState, RawContactDelta newState, DataKind newDataKind) {
        final ArrayList<ValuesDelta> mimeEntries = oldState.getMimeEntries(newDataKind.mimeType);
        if (mimeEntries == null || mimeEntries.isEmpty()) {
            return;
        }

        // Note that type specified with the old account may be invalid with the new account, while
        // we want to preserve its data as much as possible. e.g. if a user typed a phone number
        // with a type which is valid with an old account but not with a new account, the user
        // probably wants to have the number with default type, rather than seeing complete data
        // loss.
        //
        // Specifically, this method works as follows:
        // 1. detect defaultType
        // 2. prepare constants & variables for iteration
        // 3. iterate over mimeEntries:
        // 3.1 stop iteration if total number of mimeEntries reached typeOverallMax specified in
        //     DataKind
        // 3.2 replace unallowed types with defaultType
        // 3.3 check if the number of entries is below specificMax specified in AccountType

        // Here, defaultType can be supplied in two ways
        // - via kind.defaultValues
        // - via kind.typeList.get(0).rawValue
        Integer defaultType = null;
        if (newDataKind.defaultValues != null) {
            defaultType = newDataKind.defaultValues.getAsInteger(COLUMN_FOR_TYPE);
        }
        final Set<Integer> allowedTypes = new HashSet<Integer>();
        // key: type, value: the number of entries allowed for the type (specificMax)
        final SparseIntArray typeSpecificMaxMap = new SparseIntArray();
        if (defaultType != null) {
            allowedTypes.add(defaultType);
            typeSpecificMaxMap.put(defaultType, -1);
        }
        // Note: typeList may be used in different purposes when defaultValues are specified.
        // Especially in IM, typeList contains available protocols (e.g. PROTOCOL_GOOGLE_TALK)
        // instead of "types" which we want to treate here (e.g. TYPE_HOME). So we don't add
        // anything other than defaultType into allowedTypes and typeSpecificMapMax.
        if (!Im.CONTENT_ITEM_TYPE.equals(newDataKind.mimeType) &&
                newDataKind.typeList != null && !newDataKind.typeList.isEmpty()) {
            for (EditType editType : newDataKind.typeList) {
                allowedTypes.add(editType.rawValue);
                typeSpecificMaxMap.put(editType.rawValue, editType.specificMax);
            }
            if (defaultType == null) {
                defaultType = newDataKind.typeList.get(0).rawValue;
            }
        }

        if (defaultType == null) {
            Log.w(TAG, "Default type isn't available for mimetype " + newDataKind.mimeType);
        }

        final int typeOverallMax = newDataKind.typeOverallMax;

        // key: type, value: the number of current entries.
        final SparseIntArray currentEntryCount = new SparseIntArray();
        int totalCount = 0;

        for (ValuesDelta entry : mimeEntries) {
            if (typeOverallMax != -1 && totalCount >= typeOverallMax) {
                break;
            }

            final ContentValues values = entry.getAfter();
            if (values == null) {
                continue;
            }

            final Integer oldType = entry.getAsInteger(COLUMN_FOR_TYPE);
            final Integer typeForNewAccount;
            if (!allowedTypes.contains(oldType)) {
                // The new account doesn't support the type.
                if (defaultType != null) {
                    typeForNewAccount = defaultType.intValue();
                    values.put(COLUMN_FOR_TYPE, defaultType.intValue());
                    if (oldType != null && oldType == TYPE_CUSTOM) {
                        values.remove(COLUMN_FOR_LABEL);
                    }
                } else {
                    typeForNewAccount = null;
                    values.remove(COLUMN_FOR_TYPE);
                }
            } else {
                typeForNewAccount = oldType;
            }
            if (typeForNewAccount != null) {
                final int specificMax = typeSpecificMaxMap.get(typeForNewAccount, 0);
                if (specificMax >= 0) {
                    final int currentCount = currentEntryCount.get(typeForNewAccount, 0);
                    if (currentCount >= specificMax) {
                        continue;
                    }
                    currentEntryCount.put(typeForNewAccount, currentCount + 1);
                }
            }
            newState.addEntry(ValuesDelta.fromAfter(values));
            totalCount++;
        }
    }
}
