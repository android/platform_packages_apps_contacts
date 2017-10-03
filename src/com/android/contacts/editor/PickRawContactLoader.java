package com.android.contacts.editor;

import android.content.AsyncTaskLoader;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;

import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Loader for the pick a raw contact to edit activity. Loads all raw contact metadata for the
 * given Contact {@link Uri}.
 */
public class PickRawContactLoader extends
        AsyncTaskLoader<PickRawContactLoader.RawContactsMetadata> {
    private Uri mContactUri;
    private RawContactsMetadata mCachedResult;

    private static final String[] RAW_CONTACT_PROJECTION = new String[] {
            RawContacts.ACCOUNT_NAME,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            RawContacts._ID,
            RawContacts.DISPLAY_NAME_PRIMARY,
            RawContacts.DISPLAY_NAME_ALTERNATIVE
    };

    private static final String RAW_CONTACT_SELECTION = RawContacts.CONTACT_ID + "=?";

    private static final int ACCOUNT_NAME = 0;
    private static final int ACCOUNT_TYPE = 1;
    private static final int DATA_SET = 2;
    private static final int RAW_CONTACT_ID = 3;
    private static final int DISPLAY_NAME_PRIMARY = 4;
    private static final int DISPLAY_NAME_ALTERNATIVE = 5;

    private static final String PHOTO_SELECTION_PREFIX =
            ContactsContract.Data.RAW_CONTACT_ID + " IN (";
    private static final String PHOTO_SELECTION_SUFFIX = ") AND " + ContactsContract.Data.MIMETYPE
            + "=\"" + ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE + "\"";

    public PickRawContactLoader(Context context, Uri contactUri) {
        super(context);
        mContactUri = ensureIsContactUri(contactUri);
    }

    @Override
    public RawContactsMetadata loadInBackground() {
        final ContentResolver resolver = getContext().getContentResolver();
        // Get the id of the contact we're looking at.
        final Cursor contactCursor = resolver.query(
                mContactUri, new String[] {Contacts._ID, Contacts.IS_USER_PROFILE}, null,
                null, null);

        if (contactCursor == null) {
            return null;
        }

        if (contactCursor.getCount() < 1) {
            contactCursor.close();
            return null;
        }

        final RawContactsMetadata result = new RawContactsMetadata();
        try {
            contactCursor.moveToFirst();
            result.contactId = contactCursor.getLong(/* Contacts._ID */ 0);
            result.isUserProfile = contactCursor.getInt(/* Contacts.IS_USER_PROFILE */ 1) == 1;
        } finally {
            contactCursor.close();
        }

        // Load RawContact data
        final Uri rawContactUri;
        if (result.isUserProfile) {
            rawContactUri = ContactsContract.Profile.CONTENT_RAW_CONTACTS_URI;
        } else {
            rawContactUri = RawContacts.CONTENT_URI;
        }

        final Cursor rawContactCursor = resolver.query(
                rawContactUri, RAW_CONTACT_PROJECTION, RAW_CONTACT_SELECTION,
                new String[] {Long.toString(result.contactId)}, null);

        if (rawContactCursor == null) {
            return null;
        }

        if (rawContactCursor.getCount() < 1) {
            rawContactCursor.close();
            return null;
        }

        rawContactCursor.moveToPosition(-1);
        final StringBuilder photoSelection = new StringBuilder(PHOTO_SELECTION_PREFIX);
        final Map<Long, RawContact> rawContactMap = new HashMap<>();
        try {
            while (rawContactCursor.moveToNext()) {
                RawContact rawContact = new RawContact();
                rawContact.id = rawContactCursor.getLong(RAW_CONTACT_ID);
                photoSelection.append(rawContact.id).append(',');
                rawContact.displayName = rawContactCursor.getString(DISPLAY_NAME_PRIMARY);
                rawContact.displayNameAlt = rawContactCursor.getString(DISPLAY_NAME_ALTERNATIVE);
                rawContact.accountName = rawContactCursor.getString(ACCOUNT_NAME);
                rawContact.accountType = rawContactCursor.getString(ACCOUNT_TYPE);
                rawContact.accountDataSet = rawContactCursor.getString(DATA_SET);
                result.rawContacts.add(rawContact);
                rawContactMap.put(rawContact.id, rawContact);
            }
        } finally {
            rawContactCursor.close();
        }

        // Remove the last ','
        if (photoSelection.length() > 0) {
            photoSelection.deleteCharAt(photoSelection.length() - 1);
        }
        photoSelection.append(PHOTO_SELECTION_SUFFIX);

        final Uri dataUri = result.isUserProfile
                ? Uri.withAppendedPath(Profile.CONTENT_URI, Data.CONTENT_URI.getPath())
                : Data.CONTENT_URI;
        final Cursor photoCursor = resolver.query(
                dataUri,
                new String[] {Data.RAW_CONTACT_ID, Contacts.Photo._ID},
                photoSelection.toString(), null, null);

        if (photoCursor != null) {
            try {
                photoCursor.moveToPosition(-1);
                while (photoCursor.moveToNext()) {
                    final long rawContactId = photoCursor.getLong(/* Data.RAW_CONTACT_ID */ 0);
                    rawContactMap.get(rawContactId).photoId =
                            photoCursor.getLong(/* PHOTO._ID */ 1);
                }
            } finally {
                photoCursor.close();
            }
        }
        return result;
    }

    @Override
    public void deliverResult(RawContactsMetadata data) {
        mCachedResult = data;
        if (isStarted()) {
            super.deliverResult(data);
        }
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        if (mCachedResult == null) {
            forceLoad();
        } else {
            deliverResult(mCachedResult);
        }
    }

    /**
     * Ensures that this is a valid contact URI. If invalid, then an exception is
     * thrown. Otherwise, the original URI is returned.
     */
    private static Uri ensureIsContactUri(final Uri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri must not be null");
        }
        if (!uri.toString().startsWith(Contacts.CONTENT_URI.toString()) &&
                !uri.toString().equals(Profile.CONTENT_URI.toString())) {
            throw new IllegalArgumentException("Invalid contact Uri: " + uri);
        }
        return uri;
    }

    public static class RawContactsMetadata implements Parcelable {
        public static final Parcelable.Creator<RawContactsMetadata> CREATOR =
                new Parcelable.Creator<RawContactsMetadata>() {
                    @Override
                    public RawContactsMetadata createFromParcel(Parcel source) {
                        return new RawContactsMetadata(source);
                    }

                    @Override
                    public RawContactsMetadata[] newArray(int size) {
                        return new RawContactsMetadata[size];
                    }
                };

        public long contactId;
        public boolean isUserProfile;
        public boolean showReadOnly = false;
        public ArrayList<RawContact> rawContacts = new ArrayList<>();

        public RawContactsMetadata() {}

        private RawContactsMetadata(Parcel in) {
            contactId = in.readLong();
            isUserProfile = in.readInt() == 1;
            showReadOnly = in.readInt() == 1;
            in.readTypedList(rawContacts, RawContact.CREATOR);
        }

        /**
         * Removes all read-only raw contacts.
         */
        public void trimReadOnly(AccountTypeManager accountManager) {
            for (int i = rawContacts.size() - 1; i >= 0 ; i--) {
                final RawContact rawContact = rawContacts.get(i);
                final AccountType account = accountManager.getAccountType(
                        rawContact.accountType, rawContact.accountDataSet);
                if (!account.areContactsWritable()) {
                    rawContacts.remove(i);
                }
            }
        }

        /**
         * Returns the index of the first writable account in this contact or -1 if none exist.
         */
        public int getIndexOfFirstWritableAccount(AccountTypeManager accountManager) {
            for (int i = 0; i < rawContacts.size(); i++) {
                final RawContact rawContact = rawContacts.get(i);
                final AccountType account = accountManager.getAccountType(
                        rawContact.accountType, rawContact.accountDataSet);
                if (account.areContactsWritable()) {
                    return i;
                }
            }

            return -1;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(contactId);
            dest.writeInt(isUserProfile ? 1 : 0);
            dest.writeInt(showReadOnly ? 1 : 0);
            dest.writeTypedList(rawContacts);
        }
    }

    public static class RawContact implements Parcelable {
        public static final Parcelable.Creator<RawContact> CREATOR =
                new Parcelable.Creator<RawContact>() {
                    @Override
                    public RawContact createFromParcel(Parcel source) {
                        return new RawContact(source);
                    }

                    @Override
                    public RawContact[] newArray(int size) {
                        return new RawContact[size];
                    }
                };

        public long id;
        public long photoId;
        public String displayName;
        public String displayNameAlt;
        public String accountName;
        public String accountType;
        public String accountDataSet;

        public RawContact() {}

        private RawContact(Parcel in) {
            id = in.readLong();
            photoId = in.readLong();
            displayName = in.readString();
            displayNameAlt = in.readString();
            accountName = in.readString();
            accountType = in.readString();
            accountDataSet = in.readString();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeLong(photoId);
            dest.writeString(displayName);
            dest.writeString(displayNameAlt);
            dest.writeString(accountName);
            dest.writeString(accountType);
            dest.writeString(accountDataSet);
        }
    }
}
