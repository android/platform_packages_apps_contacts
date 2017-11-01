/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.detail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.DisplayNameSources;
import android.text.BidiFormatter;
import android.text.Html;
import android.text.TextDirectionHeuristics;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.model.Contact;
import com.android.contacts.model.RawContact;
import com.android.contacts.model.dataitem.DataItem;
import com.android.contacts.model.dataitem.OrganizationDataItem;
import com.android.contacts.preference.ContactsPreferences;
import com.android.contacts.util.MoreMath;
import com.google.common.collect.Iterables;

import java.util.List;

/**
 * This class contains utility methods to bind high-level contact details
 * (meaning name, phonetic name, job, and attribution) from a
 * {@link Contact} data object to appropriate {@link View}s.
 */
public class ContactDisplayUtils {
    private static final String TAG = "ContactDisplayUtils";
    private static BidiFormatter sBidiFormatter = BidiFormatter.getInstance();

    /**
     * Returns the display name of the contact, using the current display order setting.
     * Returns res/string/missing_name if there is no display name.
     */
    public static CharSequence getDisplayName(Context context, Contact contactData) {
        ContactsPreferences prefs = new ContactsPreferences(context);
        final CharSequence displayName = contactData.getDisplayName();
        if (prefs.getDisplayOrder() == ContactsPreferences.DISPLAY_ORDER_PRIMARY) {
            if (!TextUtils.isEmpty(displayName)) {
                if (contactData.getDisplayNameSource() == DisplayNameSources.PHONE) {
                    return sBidiFormatter.unicodeWrap(
                            displayName.toString(), TextDirectionHeuristics.LTR);
                }
                return displayName;
            }
        } else {
            final CharSequence altDisplayName = contactData.getAltDisplayName();
            if (!TextUtils.isEmpty(altDisplayName)) {
                return altDisplayName;
            }
        }
        return context.getResources().getString(R.string.missing_name);
    }

    /**
     * Returns the phonetic name of the contact or null if there isn't one.
     */
    public static String getPhoneticName(Context context, Contact contactData) {
        String phoneticName = contactData.getPhoneticName();
        if (!TextUtils.isEmpty(phoneticName)) {
            return phoneticName;
        }
        return null;
    }

    /**
     * Returns the attribution string for the contact, which may specify the contact directory that
     * the contact came from. Returns null if there is none applicable.
     */
    public static String getAttribution(Context context, Contact contactData) {
        if (contactData.isDirectoryEntry()) {
            String directoryDisplayName = contactData.getDirectoryDisplayName();
            String directoryType = contactData.getDirectoryType();
            final String displayName;
            if (!TextUtils.isEmpty(directoryDisplayName)) {
                displayName = directoryDisplayName;
            } else if (!TextUtils.isEmpty(directoryType)) {
                displayName = directoryType;
            } else {
                return null;
            }
            return context.getString(R.string.contact_directory_description, displayName);
        }
        return null;
    }

    /**
     * Returns the organization of the contact. If several organizations are given,
     * the first one is used. Returns null if not applicable.
     */
    public static String getCompany(Context context, Contact contactData) {
        final boolean displayNameIsOrganization = contactData.getDisplayNameSource()
                == DisplayNameSources.ORGANIZATION;
        for (RawContact rawContact : contactData.getRawContacts()) {
            for (DataItem dataItem : Iterables.filter(
                    rawContact.getDataItems(), OrganizationDataItem.class)) {
                OrganizationDataItem organization = (OrganizationDataItem) dataItem;
                final String company = organization.getCompany();
                final String title = organization.getTitle();
                final String combined;
                // We need to show company and title in a combined string. However, if the
                // DisplayName is already the organization, it mirrors company or (if company
                // is empty title). Make sure we don't show what's already shown as DisplayName
                if (TextUtils.isEmpty(company)) {
                    combined = displayNameIsOrganization ? null : title;
                } else {
                    if (TextUtils.isEmpty(title)) {
                        combined = displayNameIsOrganization ? null : company;
                    } else {
                        if (displayNameIsOrganization) {
                            combined = title;
                        } else {
                            combined = context.getString(
                                    R.string.organization_company_and_title,
                                    company, title);
                        }
                    }
                }

                if (!TextUtils.isEmpty(combined)) {
                    return combined;
                }
            }
        }
        return null;
    }

    /**
     * Sets the starred state of this contact.
     */
    public static void configureStarredMenuItem(MenuItem starredMenuItem, boolean isDirectoryEntry,
            boolean isUserProfile, boolean isStarred) {
        // Check if the starred state should be visible
        if (!isDirectoryEntry && !isUserProfile) {
            starredMenuItem.setVisible(true);
            final int resId = isStarred
                    ? R.drawable.quantum_ic_star_vd_theme_24
                    : R.drawable.quantum_ic_star_border_vd_theme_24;
            starredMenuItem.setIcon(resId);
            starredMenuItem.setChecked(isStarred);
            starredMenuItem.setTitle(isStarred ? R.string.menu_removeStar : R.string.menu_addStar);
        } else {
            starredMenuItem.setVisible(false);
        }
    }

    /**
     * Sets the display name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setDisplayName(Context context, Contact contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getDisplayName(context, contactData), textView);
    }

    /**
     * Sets the company and job title of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setCompanyName(Context context, Contact contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getCompany(context, contactData), textView);
    }

    /**
     * Sets the phonetic name of this contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setPhoneticName(Context context, Contact contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getPhoneticName(context, contactData), textView);
    }

    /**
     * Sets the attribution contact to the given {@link TextView}. If
     * there is none, then set the view to gone.
     */
    public static void setAttribution(Context context, Contact contactData, TextView textView) {
        if (textView == null) {
            return;
        }
        setDataOrHideIfNone(getAttribution(context, contactData), textView);
    }

    /**
     * Helper function to display the given text in the {@link TextView} or
     * hides the {@link TextView} if the text is empty or null.
     */
    private static void setDataOrHideIfNone(CharSequence textToDisplay, TextView textView) {
        if (!TextUtils.isEmpty(textToDisplay)) {
            textView.setText(textToDisplay);
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setText(null);
            textView.setVisibility(View.GONE);
        }
    }

    private static Html.ImageGetter sImageGetter;

    public static Html.ImageGetter getImageGetter(Context context) {
        if (sImageGetter == null) {
            sImageGetter = new DefaultImageGetter(context.getPackageManager());
        }
        return sImageGetter;
    }

    /** Fetcher for images from resources to be included in HTML text. */
    private static class DefaultImageGetter implements Html.ImageGetter {
        /** The scheme used to load resources. */
        private static final String RES_SCHEME = "res";

        private final PackageManager mPackageManager;

        public DefaultImageGetter(PackageManager packageManager) {
            mPackageManager = packageManager;
        }

        @Override
        public Drawable getDrawable(String source) {
            // Returning null means that a default image will be used.
            Uri uri;
            try {
                uri = Uri.parse(source);
            } catch (Throwable e) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Could not parse image source: " + source);
                }
                return null;
            }
            if (!RES_SCHEME.equals(uri.getScheme())) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Image source does not correspond to a resource: " + source);
                }
                return null;
            }
            // The URI authority represents the package name.
            String packageName = uri.getAuthority();

            Resources resources = getResourcesForResourceName(packageName);
            if (resources == null) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Could not parse image source: " + source);
                }
                return null;
            }

            List<String> pathSegments = uri.getPathSegments();
            if (pathSegments.size() != 1) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Could not parse image source: " + source);
                }
                return null;
            }

            final String name = pathSegments.get(0);
            final int resId = resources.getIdentifier(name, "drawable", packageName);

            if (resId == 0) {
                // Use the default image icon in this case.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Cannot resolve resource identifier: " + source);
                }
                return null;
            }

            try {
                return getResourceDrawable(resources, resId);
            } catch (NotFoundException e) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Resource not found: " + source, e);
                }
                return null;
            }
        }

        /** Returns the drawable associated with the given id. */
        private Drawable getResourceDrawable(Resources resources, int resId)
                throws NotFoundException {
            Drawable drawable = resources.getDrawable(resId);
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
            return drawable;
        }

        /** Returns the {@link Resources} of the package of the given resource name. */
        private Resources getResourcesForResourceName(String packageName) {
            try {
                return mPackageManager.getResourcesForApplication(packageName);
            } catch (NameNotFoundException e) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Could not find package: " + packageName);
                }
                return null;
            }
        }
    }

    /**
     * Sets an alpha value on the view.
     */
    public static void setAlphaOnViewBackground(View view, float alpha) {
        if (view != null) {
            // Convert alpha layer to a black background HEX color with an alpha value for better
            // performance (i.e. use setBackgroundColor() instead of setAlpha())
            view.setBackgroundColor((int) (MoreMath.clamp(alpha, 0.0f, 1.0f) * 255) << 24);
        }
    }

    /**
     * Returns the top coordinate of the first item in the {@link ListView}. If the first item
     * in the {@link ListView} is not visible or there are no children in the list, then return
     * Integer.MIN_VALUE. Note that the returned value will be <= 0 because the first item in the
     * list cannot have a positive offset.
     */
    public static int getFirstListItemOffset(ListView listView) {
        if (listView == null || listView.getChildCount() == 0 ||
                listView.getFirstVisiblePosition() != 0) {
            return Integer.MIN_VALUE;
        }
        return listView.getChildAt(0).getTop();
    }

    /**
     * Tries to scroll the first item in the list to the given offset (this can be a no-op if the
     * list is already in the correct position).
     * @param listView that should be scrolled
     * @param offset which should be <= 0
     */
    public static void requestToMoveToOffset(ListView listView, int offset) {
        // We try to offset the list if the first item in the list is showing (which is presumed
        // to have a larger height than the desired offset). If the first item in the list is not
        // visible, then we simply do not scroll the list at all (since it can get complicated to
        // compute how many items in the list will equal the given offset). Potentially
        // some animation elsewhere will make the transition smoother for the user to compensate
        // for this simplification.
        if (listView == null || listView.getChildCount() == 0 ||
                listView.getFirstVisiblePosition() != 0 || offset > 0) {
            return;
        }

        // As an optimization, check if the first item is already at the given offset.
        if (listView.getChildAt(0).getTop() == offset) {
            return;
        }

        listView.setSelectionFromTop(0, offset);
    }
}
