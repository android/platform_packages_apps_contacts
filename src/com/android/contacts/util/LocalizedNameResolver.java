/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.contacts.util;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Xml;

import com.android.contacts.R;
import com.android.contacts.model.account.ExternalAccountType;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Retrieves localized names per account type. This allows customizing texts like
 * "All Contacts" for certain account types, but e.g. "All Friends" or "All Connections" for others.
 */
public class LocalizedNameResolver  {
    private static final String TAG = "LocalizedNameResolver";

    private static final String CONTACTS_DATA_KIND = "ContactsDataKind";

    /**
     * Returns the name for All Contacts for the specified account type.
     */
    public static String getAllContactsName(Context context, String accountType) {
        if (context == null) throw new IllegalArgumentException("Context must not be null");
        if (accountType == null) return null;

        return resolveAllContactsName(context, accountType);
     }

    /**
     * Finds "All Contacts"-Name for the specified account type.
     */
    private static String resolveAllContactsName(Context context, String accountType) {
        final AccountManager am = AccountManager.get(context);

        for (AuthenticatorDescription auth : am.getAuthenticatorTypes()) {
            if (accountType.equals(auth.type)) {
                return resolveAllContactsNameFromMetaData(context, auth.packageName);
            }
        }

        return null;
    }

    /**
     * Finds the meta-data XML containing the contacts configuration and
     * reads the picture priority from that file.
     */
    private static String resolveAllContactsNameFromMetaData(Context context, String packageName) {
        final XmlResourceParser parser = ExternalAccountType.loadContactsXml(context, packageName);
        if (parser != null) {
            return loadAllContactsNameFromXml(context, parser, packageName);
        }
        return null;
    }

    private static String loadAllContactsNameFromXml(Context context, XmlPullParser parser,
            String packageName) {
        try {
            final AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Drain comments and whitespace
            }

            if (type != XmlPullParser.START_TAG) {
                throw new IllegalStateException("No start tag found");
            }

            final int depth = parser.getDepth();
            while (((type = parser.next()) != XmlPullParser.END_TAG || parser.getDepth() > depth)
                    && type != XmlPullParser.END_DOCUMENT) {
                String name = parser.getName();
                if (type == XmlPullParser.START_TAG && CONTACTS_DATA_KIND.equals(name)) {
                    final TypedArray typedArray = context.obtainStyledAttributes(attrs,
                            R.styleable.ContactsDataKind);
                    try {
                        // See if a string has been hardcoded directly into the xml
                        final String nonResourceString = typedArray.getNonResourceString(
                                R.styleable.ContactsDataKind_android_allContactsName);
                        if (nonResourceString != null) {
                            return nonResourceString;
                        }

                        // See if a resource is referenced. We can't rely on getString
                        // to automatically resolve it as the resource lives in a different package
                        int id = typedArray.getResourceId(
                                R.styleable.ContactsDataKind_android_allContactsName, 0);
                        if (id == 0) return null;

                        // Resolve the resource Id
                        final PackageManager packageManager = context.getPackageManager();
                        final Resources resources;
                        try {
                            resources = packageManager.getResourcesForApplication(packageName);
                        } catch (NameNotFoundException e) {
                            return null;
                        }
                        try {
                            return resources.getString(id);
                        } catch (NotFoundException e) {
                            return null;
                        }
                    } finally {
                        typedArray.recycle();
                    }
                }
            }
            return null;
        } catch (XmlPullParserException e) {
            throw new IllegalStateException("Problem reading XML", e);
        } catch (IOException e) {
            throw new IllegalStateException("Problem reading XML", e);
        }
    }
}
