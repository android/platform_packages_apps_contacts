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

package com.android.contacts;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.DisplayPhoto;
import androidx.annotation.IntDef;
import android.text.TextUtils;
import android.util.Pair;

import com.android.contacts.compat.ContactsCompat;
import com.android.contacts.compat.DirectoryCompat;
import com.android.contacts.model.dataitem.ImDataItem;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class ContactsUtils {
    private static final String TAG = "ContactsUtils";

    // Telecomm related schemes are in CallUtil
    public static final String SCHEME_IMTO = "imto";
    public static final String SCHEME_MAILTO = "mailto";
    public static final String SCHEME_SMSTO = "smsto";

    private static final int DEFAULT_THUMBNAIL_SIZE = 96;

    private static int sThumbnailSize = -1;

    public static final boolean FLAG_N_FEATURE = Build.VERSION.SDK_INT >= 24;

    // TODO find a proper place for the canonical version of these
    public interface ProviderNames {
        String YAHOO = "Yahoo";
        String GTALK = "GTalk";
        String MSN = "MSN";
        String ICQ = "ICQ";
        String AIM = "AIM";
        String XMPP = "XMPP";
        String JABBER = "JABBER";
        String SKYPE = "SKYPE";
        String QQ = "QQ";
    }

    /**
     * This looks up the provider name defined in
     * ProviderNames from the predefined IM protocol id.
     * This is used for interacting with the IM application.
     *
     * @param protocol the protocol ID
     * @return the provider name the IM app uses for the given protocol, or null if no
     * provider is defined for the given protocol
     * @hide
     */
    public static String lookupProviderNameFromId(int protocol) {
        switch (protocol) {
            case Im.PROTOCOL_GOOGLE_TALK:
                return ProviderNames.GTALK;
            case Im.PROTOCOL_AIM:
                return ProviderNames.AIM;
            case Im.PROTOCOL_MSN:
                return ProviderNames.MSN;
            case Im.PROTOCOL_YAHOO:
                return ProviderNames.YAHOO;
            case Im.PROTOCOL_ICQ:
                return ProviderNames.ICQ;
            case Im.PROTOCOL_JABBER:
                return ProviderNames.JABBER;
            case Im.PROTOCOL_SKYPE:
                return ProviderNames.SKYPE;
            case Im.PROTOCOL_QQ:
                return ProviderNames.QQ;
        }
        return null;
    }


    public static final long USER_TYPE_CURRENT = 0;
    public static final long USER_TYPE_WORK = 1;

    /**
     * UserType indicates the user type of the contact. If the contact is from Work User (Work
     * Profile in Android Multi-User System), it's {@link #USER_TYPE_WORK}, otherwise,
     * {@link #USER_TYPE_CURRENT}. Please note that current user can be in work profile, where the
     * dialer is running inside Work Profile.
     */
    @Retention(RetentionPolicy.SOURCE)
    // TODO: Switch to @LongDef once @LongDef is available in the support library
    @IntDef({(int)USER_TYPE_CURRENT, (int)USER_TYPE_WORK})
    public @interface UserType {}

    /**
     * Test if the given {@link CharSequence} contains any graphic characters,
     * first checking {@link TextUtils#isEmpty(CharSequence)} to handle null.
     */
    public static boolean isGraphic(CharSequence str) {
        return !TextUtils.isEmpty(str) && TextUtils.isGraphic(str);
    }

    /**
     * Returns true if two objects are considered equal.  Two null references are equal here.
     */
    public static boolean areObjectsEqual(Object a, Object b) {
        return a == b || (a != null && a.equals(b));
    }

    /**
     * Returns true if two {@link Intent}s are both null, or have the same action.
     */
    public static final boolean areIntentActionEqual(Intent a, Intent b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return TextUtils.equals(a.getAction(), b.getAction());
    }

    /**
     * Returns the size (width and height) of thumbnail pictures as configured in the provider. This
     * can safely be called from the UI thread, as the provider can serve this without performing
     * a database access
     */
    public static int getThumbnailSize(Context context) {
        if (sThumbnailSize == -1) {
            final Cursor c = context.getContentResolver().query(
                    DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI,
                    new String[] { DisplayPhoto.THUMBNAIL_MAX_DIM }, null, null, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        sThumbnailSize = c.getInt(0);
                    }
                } finally {
                    c.close();
                }
            }
        }
        return sThumbnailSize != -1 ? sThumbnailSize : DEFAULT_THUMBNAIL_SIZE;
    }

    private static Intent getCustomImIntent(ImDataItem im, int protocol) {
        String host = im.getCustomProtocol();
        final String data = im.getData();
        if (TextUtils.isEmpty(data)) {
            return null;
        }
        if (protocol != Im.PROTOCOL_CUSTOM) {
            // Try bringing in a well-known host for specific protocols
            host = ContactsUtils.lookupProviderNameFromId(protocol);
        }
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        final String authority = host.toLowerCase();
        final Uri imUri = new Uri.Builder().scheme(SCHEME_IMTO).authority(
                authority).appendPath(data).build();
        final Intent intent = new Intent(Intent.ACTION_SENDTO, imUri);
        return intent;
    }

    /**
     * Returns the proper Intent for an ImDatItem. If available, a secondary intent is stored
     * in the second Pair slot
     */
    public static Pair<Intent, Intent> buildImIntent(Context context, ImDataItem im) {
        Intent intent = null;
        Intent secondaryIntent = null;
        final boolean isEmail = im.isCreatedFromEmail();

        if (!isEmail && !im.isProtocolValid()) {
            return new Pair<>(null, null);
        }

        final String data = im.getData();
        if (TextUtils.isEmpty(data)) {
            return new Pair<>(null, null);
        }

        final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK : im.getProtocol();

        if (protocol == Im.PROTOCOL_GOOGLE_TALK) {
            final int chatCapability = im.getChatCapability();
            if ((chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0) {
                intent = new Intent(Intent.ACTION_SENDTO,
                                Uri.parse("xmpp:" + data + "?message"));
                secondaryIntent = new Intent(Intent.ACTION_SENDTO,
                        Uri.parse("xmpp:" + data + "?call"));
            } else if ((chatCapability & Im.CAPABILITY_HAS_VOICE) != 0) {
                // Allow Talking and Texting
                intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
                secondaryIntent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
            } else {
                intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
            }
        } else {
            // Build an IM Intent
            intent = getCustomImIntent(im, protocol);
        }
        return new Pair<>(intent, secondaryIntent);
    }

    /**
     * Determine UserType from directory id and contact id.
     *
     * 3 types of query
     *
     * 1. 2 profile query: content://com.android.contacts/phone_lookup_enterprise/1234567890
     * personal and work contact are mixed into one cursor. no directory id. contact_id indicates if
     * it's work contact
     *
     * 2. work local query:
     * content://com.android.contacts/phone_lookup_enterprise/1234567890?directory=1000000000
     * either directory_id or contact_id is enough to identify work contact
     *
     * 3. work remote query:
     * content://com.android.contacts/phone_lookup_enterprise/1234567890?directory=1000000003
     * contact_id is random. only directory_id is available
     *
     * Summary: If directory_id is not null, always use directory_id to identify work contact.
     * (which is the case here) Otherwise, use contact_id.
     *
     * @param directoryId directory id of ContactsProvider query
     * @param contactId contact id
     * @return UserType indicates the user type of the contact. A directory id or contact id larger
     *         than a thredshold indicates that the contact is stored in Work Profile, but not in
     *         current user. It's a contract by ContactsProvider and check by
     *         Contacts.isEnterpriseDirectoryId and Contacts.isEnterpriseContactId. Currently, only
     *         2 kinds of users can be detected from the directoryId and contactId as
     *         ContactsProvider can only access current and work user's contacts
     */
    public static @UserType long determineUserType(Long directoryId, Long contactId) {
        // First check directory id
        if (directoryId != null) {
            return DirectoryCompat.isEnterpriseDirectoryId(directoryId) ? USER_TYPE_WORK
                    : USER_TYPE_CURRENT;
        }
        // Only check contact id if directory id is null
        if (contactId != null && contactId != 0L
                && ContactsCompat.isEnterpriseContactId(contactId)) {
            return USER_TYPE_WORK;
        } else {
            return USER_TYPE_CURRENT;
        }

    }
}
