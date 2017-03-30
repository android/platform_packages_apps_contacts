package com.android.contacts.profile;

import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.Profile;
import android.text.TextUtils;
import com.android.contacts.R;
import com.android.contacts.preference.ContactsPreferences;

/**
 * Load MyProfile information for display on the navigation drawer.
 */
public final class ProfileLoader extends CursorLoader {

    /**
     * The projections that are used to obtain user profile
     */
    public static final class ProfileQuery {
        /**
         * Not instantiable.
         */
        private ProfileQuery() {}

        public static final String[] PROFILE_PROJECTION_PRIMARY = new String[] {
                Contacts._ID,                           // 0
                Contacts.DISPLAY_NAME_PRIMARY,          // 1
                Contacts.IS_USER_PROFILE,               // 2
                Contacts.PHOTO_ID,                      // 3
                Contacts.PHOTO_THUMBNAIL_URI,           // 4
                Contacts.DISPLAY_NAME_SOURCE            // 5
        };

        public static final String[] PROFILE_PROJECTION_ALTERNATIVE = new String[] {
                Contacts._ID,                           // 0
                Contacts.DISPLAY_NAME_ALTERNATIVE,      // 1
                Contacts.IS_USER_PROFILE,               // 2
                Contacts.PHOTO_ID,                      // 3
                Contacts.PHOTO_THUMBNAIL_URI,           // 4
                Contacts.DISPLAY_NAME_SOURCE            // 5
        };

        public static final int CONTACT_ID               = 0;
        public static final int CONTACT_DISPLAY_NAME     = 1;
        public static final int CONTACT_IS_USER_PROFILE  = 2;
        public static final int CONTACT_PHOTO_ID         = 3;
        public static final int CONTACT_PHOTO_URI        = 4;
        public static final int DISPLAY_NAME_SOURCE      = 5;
    }

    /*
     * Apply the Name format setting(First name first/Last name first)
     * to the display name of MyInfo at the navigation drawer.
     * Settings->MyInfo also behaves the same.
     */
    public static String[] getProjection(Context context) {
        final ContactsPreferences contactsPrefs = new ContactsPreferences(context);
        final int displayOrder = contactsPrefs.getDisplayOrder();
        if (displayOrder == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            return ProfileQuery.PROFILE_PROJECTION_PRIMARY;
        }
        return ProfileQuery.PROFILE_PROJECTION_ALTERNATIVE;
    }

    /** Returns a {@link ProfileItem} read from the given cursor */
    public static ProfileItem getProfileItem(Context context, Cursor cursor) {
        boolean hasProfile = false;
        String displayName = null;
        long contactId = -1;
        long photoId = 0;
        String photoUri = null;
        int displayNameSource = DisplayNameSources.UNDEFINED;
        if (cursor != null && cursor.moveToFirst()) {
            hasProfile = cursor.getInt(ProfileQuery.CONTACT_IS_USER_PROFILE) == 1;
            displayName = cursor.getString(ProfileQuery.CONTACT_DISPLAY_NAME);
            contactId = cursor.getLong(ProfileQuery.CONTACT_ID);
            photoId = cursor.getLong(ProfileQuery.CONTACT_PHOTO_ID);
            photoUri = cursor.getString(ProfileQuery.CONTACT_PHOTO_URI);
            displayNameSource = cursor.getInt(ProfileQuery.DISPLAY_NAME_SOURCE);
        }
        if (hasProfile && TextUtils.isEmpty(displayName)) {
            displayName = context.getResources().getString(R.string.missing_name);
        }
        return new ProfileItem(displayName, contactId, photoId, photoUri, displayNameSource,
                hasProfile);
    }

    public ProfileLoader(Context context, String[] projection) {
        super(context,
                Profile.CONTENT_URI,
                projection,
                null,
                null,
                null);
    }

    @Override
    public Cursor loadInBackground() {
        try {
            return super.loadInBackground();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
