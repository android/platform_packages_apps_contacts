package com.android.contacts.profile;

/**
 * Meta-data for a contact profile.
 */
public final class ProfileItem {

    private final String mDisplayName;
    private final long mContactId;
    private final long mPhotoId;
    private final String mPhotoUri;
    private final int mDisplayNameSource;
    private final boolean mHasProfile;

    public ProfileItem(String displayName, long contactId, long photoId, String photoUri,
                         int displayNameSource, boolean hasProfile) {
        mDisplayName = displayName;
        mContactId = contactId;
        mPhotoId = photoId;
        mPhotoUri = photoUri;
        mDisplayNameSource = displayNameSource;
        mHasProfile = hasProfile;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public long getContactId() {
        return mContactId;
    }

    public long getPhotoId() {
        return mPhotoId;
    }

    public String getPhotoUri() {
        return mPhotoUri;
    }

    public int getDisplayNameSource() {
        return mDisplayNameSource;
    }

    public boolean HasProfile() {
        return mHasProfile;
    }
}