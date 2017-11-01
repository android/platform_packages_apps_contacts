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

package com.android.contacts.model;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.DisplayNameSources;

import com.android.contacts.group.GroupMetaData;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.SimAccountType;
import com.android.contacts.util.DataStatus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;

/**
 * A Contact represents a single person or logical entity as perceived by the user.  The information
 * about a contact can come from multiple data sources, which are each represented by a RawContact
 * object.  Thus, a Contact is associated with a collection of RawContact objects.
 *
 * The aggregation of raw contacts into a single contact is performed automatically, and it is
 * also possible for users to manually split and join raw contacts into various contacts.
 *
 * Only the {@link ContactLoader} class can create a Contact object with various flags to allow
 * partial loading of contact data.  Thus, an instance of this class should be treated as
 * a read-only object.
 */
public class Contact {
    private enum Status {
        /** Contact is successfully loaded */
        LOADED,
        /** There was an error loading the contact */
        ERROR,
        /** Contact is not found */
        NOT_FOUND,
    }

    private final Uri mRequestedUri;
    private final Uri mLookupUri;
    private final Uri mUri;
    private final long mDirectoryId;
    private final String mLookupKey;
    private final long mId;
    private final long mNameRawContactId;
    private final int mDisplayNameSource;
    private final long mPhotoId;
    private final String mPhotoUri;
    private final String mDisplayName;
    private final String mAltDisplayName;
    private final String mPhoneticName;
    private final boolean mStarred;
    private final Integer mPresence;
    private ImmutableList<RawContact> mRawContacts;
    private ImmutableMap<Long,DataStatus> mStatuses;

    private String mDirectoryDisplayName;
    private String mDirectoryType;
    private String mDirectoryAccountType;
    private String mDirectoryAccountName;
    private int mDirectoryExportSupport;

    private ImmutableList<GroupMetaData> mGroups;

    private byte[] mPhotoBinaryData;
    /**
     * Small version of the contact photo loaded from a blob instead of from a file. If a large
     * contact photo is not available yet, then this has the same value as mPhotoBinaryData.
     */
    private byte[] mThumbnailPhotoBinaryData;
    private final boolean mSendToVoicemail;
    private final String mCustomRingtone;
    private final boolean mIsUserProfile;

    private final Contact.Status mStatus;
    private final Exception mException;

    /**
     * Constructor for special results, namely "no contact found" and "error".
     */
    private Contact(Uri requestedUri, Contact.Status status, Exception exception) {
        if (status == Status.ERROR && exception == null) {
            throw new IllegalArgumentException("ERROR result must have exception");
        }
        mStatus = status;
        mException = exception;
        mRequestedUri = requestedUri;
        mLookupUri = null;
        mUri = null;
        mDirectoryId = -1;
        mLookupKey = null;
        mId = -1;
        mRawContacts = null;
        mStatuses = null;
        mNameRawContactId = -1;
        mDisplayNameSource = DisplayNameSources.UNDEFINED;
        mPhotoId = -1;
        mPhotoUri = null;
        mDisplayName = null;
        mAltDisplayName = null;
        mPhoneticName = null;
        mStarred = false;
        mPresence = null;
        mSendToVoicemail = false;
        mCustomRingtone = null;
        mIsUserProfile = false;
    }

    public static Contact forError(Uri requestedUri, Exception exception) {
        return new Contact(requestedUri, Status.ERROR, exception);
    }

    public static Contact forNotFound(Uri requestedUri) {
        return new Contact(requestedUri, Status.NOT_FOUND, null);
    }

    /**
     * Constructor to call when contact was found
     */
    public Contact(Uri requestedUri, Uri uri, Uri lookupUri, long directoryId, String lookupKey,
            long id, long nameRawContactId, int displayNameSource, long photoId,
            String photoUri, String displayName, String altDisplayName, String phoneticName,
            boolean starred, Integer presence, boolean sendToVoicemail, String customRingtone,
            boolean isUserProfile) {
        mStatus = Status.LOADED;
        mException = null;
        mRequestedUri = requestedUri;
        mLookupUri = lookupUri;
        mUri = uri;
        mDirectoryId = directoryId;
        mLookupKey = lookupKey;
        mId = id;
        mRawContacts = null;
        mStatuses = null;
        mNameRawContactId = nameRawContactId;
        mDisplayNameSource = displayNameSource;
        mPhotoId = photoId;
        mPhotoUri = photoUri;
        mDisplayName = displayName;
        mAltDisplayName = altDisplayName;
        mPhoneticName = phoneticName;
        mStarred = starred;
        mPresence = presence;
        mSendToVoicemail = sendToVoicemail;
        mCustomRingtone = customRingtone;
        mIsUserProfile = isUserProfile;
    }

    public Contact(Uri requestedUri, Contact from) {
        mRequestedUri = requestedUri;

        mStatus = from.mStatus;
        mException = from.mException;
        mLookupUri = from.mLookupUri;
        mUri = from.mUri;
        mDirectoryId = from.mDirectoryId;
        mLookupKey = from.mLookupKey;
        mId = from.mId;
        mNameRawContactId = from.mNameRawContactId;
        mDisplayNameSource = from.mDisplayNameSource;
        mPhotoId = from.mPhotoId;
        mPhotoUri = from.mPhotoUri;
        mDisplayName = from.mDisplayName;
        mAltDisplayName = from.mAltDisplayName;
        mPhoneticName = from.mPhoneticName;
        mStarred = from.mStarred;
        mPresence = from.mPresence;
        mRawContacts = from.mRawContacts;
        mStatuses = from.mStatuses;

        mDirectoryDisplayName = from.mDirectoryDisplayName;
        mDirectoryType = from.mDirectoryType;
        mDirectoryAccountType = from.mDirectoryAccountType;
        mDirectoryAccountName = from.mDirectoryAccountName;
        mDirectoryExportSupport = from.mDirectoryExportSupport;

        mGroups = from.mGroups;

        mPhotoBinaryData = from.mPhotoBinaryData;
        mSendToVoicemail = from.mSendToVoicemail;
        mCustomRingtone = from.mCustomRingtone;
        mIsUserProfile = from.mIsUserProfile;
    }

    /**
     * @param exportSupport See {@link Directory#EXPORT_SUPPORT}.
     */
    public void setDirectoryMetaData(String displayName, String directoryType,
            String accountType, String accountName, int exportSupport) {
        mDirectoryDisplayName = displayName;
        mDirectoryType = directoryType;
        mDirectoryAccountType = accountType;
        mDirectoryAccountName = accountName;
        mDirectoryExportSupport = exportSupport;
    }

    /* package */ void setPhotoBinaryData(byte[] photoBinaryData) {
        mPhotoBinaryData = photoBinaryData;
    }

    /* package */ void setThumbnailPhotoBinaryData(byte[] photoBinaryData) {
        mThumbnailPhotoBinaryData = photoBinaryData;
    }

    /**
     * Returns the URI for the contact that contains both the lookup key and the ID. This is
     * the best URI to reference a contact.
     * For directory contacts, this is the same a the URI as returned by {@link #getUri()}
     */
    public Uri getLookupUri() {
        return mLookupUri;
    }

    public String getLookupKey() {
        return mLookupKey;
    }

    /**
     * Returns the contact Uri that was passed to the provider to make the query. This is
     * the same as the requested Uri, unless the requested Uri doesn't specify a Contact:
     * If it either references a Raw-Contact or a Person (a pre-Eclair style Uri), this Uri will
     * always reference the full aggregate contact.
     */
    public Uri getUri() {
        return mUri;
    }

    /**
     * Returns the URI for which this {@link ContactLoader) was initially requested.
     */
    public Uri getRequestedUri() {
        return mRequestedUri;
    }

    /**
     * Instantiate a new RawContactDeltaList for this contact.
     */
    public RawContactDeltaList createRawContactDeltaList() {
        return RawContactDeltaList.fromIterator(getRawContacts().iterator());
    }

    /**
     * Returns the contact ID.
     */
    @VisibleForTesting
    public long getId() {
        return mId;
    }

    /**
     * @return true when an exception happened during loading, in which case
     *     {@link #getException} returns the actual exception object.
     *     Note {@link #isNotFound()} and {@link #isError()} are mutually exclusive; If
     *     {@link #isError()} is {@code true}, {@link #isNotFound()} is always {@code false},
     *     and vice versa.
     */
    public boolean isError() {
        return mStatus == Status.ERROR;
    }

    public Exception getException() {
        return mException;
    }

    /**
     * @return true when the specified contact is not found.
     *     Note {@link #isNotFound()} and {@link #isError()} are mutually exclusive; If
     *     {@link #isError()} is {@code true}, {@link #isNotFound()} is always {@code false},
     *     and vice versa.
     */
    public boolean isNotFound() {
        return mStatus == Status.NOT_FOUND;
    }

    /**
     * @return true if the specified contact is successfully loaded.
     *     i.e. neither {@link #isError()} nor {@link #isNotFound()}.
     */
    public boolean isLoaded() {
        return mStatus == Status.LOADED;
    }

    public long getNameRawContactId() {
        return mNameRawContactId;
    }

    public int getDisplayNameSource() {
        return mDisplayNameSource;
    }

    /**
     * Used by various classes to determine whether or not this contact should be displayed as
     * a business rather than a person.
     */
    public boolean isDisplayNameFromOrganization() {
        return DisplayNameSources.ORGANIZATION == mDisplayNameSource;
    }

    public long getPhotoId() {
        return mPhotoId;
    }

    public String getPhotoUri() {
        return mPhotoUri;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public String getAltDisplayName() {
        return mAltDisplayName;
    }

    public String getPhoneticName() {
        return mPhoneticName;
    }

    public boolean getStarred() {
        return mStarred;
    }

    public Integer getPresence() {
        return mPresence;
    }

    public ImmutableList<RawContact> getRawContacts() {
        return mRawContacts;
    }

    public ImmutableMap<Long, DataStatus> getStatuses() {
        return mStatuses;
    }

    public long getDirectoryId() {
        return mDirectoryId;
    }

    public boolean isDirectoryEntry() {
        return mDirectoryId != -1 && mDirectoryId != Directory.DEFAULT
                && mDirectoryId != Directory.LOCAL_INVISIBLE;
    }

    /**
     * @return true if this is a contact (not group, etc.) with at least one
     *         writable raw-contact, and false otherwise.
     */
    public boolean isWritableContact(final Context context) {
        return getFirstWritableRawContactId(context) != -1;
    }

    /**
     * Return the ID of the first raw-contact in the contact data that belongs to a
     * contact-writable account, or -1 if no such entity exists.
     */
    public long getFirstWritableRawContactId(final Context context) {
        // Directory entries are non-writable
        if (isDirectoryEntry()) return -1;

        // Iterate through raw-contacts; if we find a writable on, return its ID.
        for (RawContact rawContact : getRawContacts()) {
            AccountType accountType = rawContact.getAccountType(context);
            if (accountType != null && accountType.areContactsWritable()) {
                return rawContact.getId();
            }
        }
        // No writable raw-contact was found.
        return -1;
    }

    public int getDirectoryExportSupport() {
        return mDirectoryExportSupport;
    }

    public String getDirectoryDisplayName() {
        return mDirectoryDisplayName;
    }

    public String getDirectoryType() {
        return mDirectoryType;
    }

    public String getDirectoryAccountType() {
        return mDirectoryAccountType;
    }

    public String getDirectoryAccountName() {
        return mDirectoryAccountName;
    }

    public byte[] getPhotoBinaryData() {
        return mPhotoBinaryData;
    }

    public byte[] getThumbnailPhotoBinaryData() {
        return mThumbnailPhotoBinaryData;
    }

    public ArrayList<ContentValues> getContentValues() {
        if (mRawContacts.size() != 1) {
            throw new IllegalStateException(
                    "Cannot extract content values from an aggregated contact");
        }

        RawContact rawContact = mRawContacts.get(0);
        ArrayList<ContentValues> result = rawContact.getContentValues();

        // If the photo was loaded using the URI, create an entry for the photo
        // binary data.
        if (mPhotoId == 0 && mPhotoBinaryData != null) {
            ContentValues photo = new ContentValues();
            photo.put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
            photo.put(Photo.PHOTO, mPhotoBinaryData);
            result.add(photo);
        }

        return result;
    }

    /**
     * This can return non-null group meta-data only if the {@link ContactLoader} was configured to
     * load group metadata in its constructor.
     * @return
     */
    public ImmutableList<GroupMetaData> getGroupMetaData() {
        return mGroups;
    }

    public boolean isSendToVoicemail() {
        return mSendToVoicemail;
    }

    public String getCustomRingtone() {
        return mCustomRingtone;
    }

    public boolean isUserProfile() {
        return mIsUserProfile;
    }

    public boolean isMultipleRawContacts() {
        return mRawContacts.size() > 1;
    }

    /**
     * @return true if all the raw contacts are from SIM accounts, and false otherwise.
     */
    public boolean areAllRawContactsSimAccounts(final Context context) {
        if (getRawContacts() == null) return false;

        for (RawContact rawContact : getRawContacts()) {
            final AccountType accountType = rawContact.getAccountType(context);
            if (!(accountType instanceof SimAccountType)) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "{requested=" + mRequestedUri + ",lookupkey=" + mLookupKey +
                ",uri=" + mUri + ",status=" + mStatus + "}";
    }

    /* package */ void setRawContacts(ImmutableList<RawContact> rawContacts) {
        mRawContacts = rawContacts;
    }

    /* package */ void setStatuses(ImmutableMap<Long, DataStatus> statuses) {
        mStatuses = statuses;
    }

    /* package */ void setGroupMetaData(ImmutableList<GroupMetaData> groups) {
        mGroups = groups;
    }
}
