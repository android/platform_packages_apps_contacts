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
 * limitations under the License.
 */

package com.android.contacts.quickcontact;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.CommonDataKinds.SipAddress;

import com.android.contacts.util.PhoneCapabilityTester;

import com.google.common.collect.Sets;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Internally hold a cache of scaled icons based on {@link PackageManager}
 * queries, keyed internally on MIME-type.
 */
public class ResolveCache {
    /**
     * Specific list {@link ApplicationInfo#packageName} of apps that are
     * prefered <strong>only</strong> for the purposes of default icons when
     * multiple {@link ResolveInfo} are found to match. This only happens when
     * the user has not selected a default app yet, and they will still be
     * presented with the system disambiguation dialog.
     * If several of this list match (e.g. Android Browser vs. Chrome), we will pick either one
     */
    private static final HashSet<String> sPreferResolve = Sets.newHashSet(
            "com.android.email",
            "com.google.android.email",

            "com.android.phone",

            "com.google.android.apps.maps",

            "com.android.chrome",
            "org.chromium.webview_shell",
            "com.google.android.browser",
            "com.android.browser");

    private final Context mContext;
    private final PackageManager mPackageManager;

    private static ResolveCache sInstance;

    /**
     * Returns an instance of the ResolveCache. Only one internal instance is kept, so
     * the argument packageManagers is ignored for all but the first call
     */
    public synchronized static ResolveCache getInstance(Context context) {
        if (sInstance == null) {
            final Context applicationContext = context.getApplicationContext();
            sInstance = new ResolveCache(applicationContext);

            // Register for package-changes so that we can flush our cache
            final IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            applicationContext.registerReceiver(sInstance.mPackageIntentReceiver, filter);
        }
        return sInstance;
    }

    private synchronized static void flush() {
        sInstance = null;
    }

    /**
     * Called anytime a package is installed, uninstalled etc, so that we can wipe our cache
     */
    private BroadcastReceiver mPackageIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            flush();
        }
    };

    /**
     * Cached entry holding the best {@link ResolveInfo} for a specific
     * MIME-type, along with a {@link SoftReference} to its icon.
     */
    private static class Entry {
        public ResolveInfo bestResolve;
        public Drawable icon;
    }

    private HashMap<String, Entry> mCache = new HashMap<String, Entry>();


    private ResolveCache(Context context) {
        mContext = context;
        mPackageManager = context.getPackageManager();
    }

    /**
     * Get the {@link Entry} best associated with the given mimetype and intent,
     * or create and populate a new one if it doesn't exist.
     */
    protected Entry getEntry(String mimeType, Intent intent) {
        Entry entry = mCache.get(mimeType);
        if (entry != null) return entry;
        entry = new Entry();

        if (SipAddress.CONTENT_ITEM_TYPE.equals(mimeType)
                && !PhoneCapabilityTester.isSipPhone(mContext)) {
            intent = null;
        }

        if (intent != null) {
            final List<ResolveInfo> matches = mPackageManager.queryIntentActivities(intent,
                    PackageManager.MATCH_DEFAULT_ONLY);

            // Pick first match, otherwise best found
            ResolveInfo bestResolve = null;
            final int size = matches.size();
            if (size == 1) {
                bestResolve = matches.get(0);
            } else if (size > 1) {
                bestResolve = getBestResolve(intent, matches);
            }

            if (bestResolve != null) {
                final Drawable icon = bestResolve.loadIcon(mPackageManager);

                entry.bestResolve = bestResolve;
                entry.icon = icon;
            }
        }

        mCache.put(mimeType, entry);
        return entry;
    }

    /**
     * Best {@link ResolveInfo} when multiple found. Ties are broken by
     * selecting first from the {@link QuickContactActivity#sPreferResolve} list of
     * preferred packages, second by apps that live on the system partition,
     * otherwise the app from the top of the list. This is
     * <strong>only</strong> used for selecting a default icon for
     * displaying in the track, and does not shortcut the system
     * {@link Intent} disambiguation dialog.
     */
    protected ResolveInfo getBestResolve(Intent intent, List<ResolveInfo> matches) {
        // Try finding preferred activity, otherwise detect disambig
        final ResolveInfo foundResolve = mPackageManager.resolveActivity(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        final boolean foundDisambig = (foundResolve.match &
                IntentFilter.MATCH_CATEGORY_MASK) == 0;

        if (!foundDisambig) {
            // Found concrete match, so return directly
            return foundResolve;
        }

        // Accept any package from prefer list, otherwise first system app
        ResolveInfo firstSystem = null;
        for (ResolveInfo info : matches) {
            final boolean isSystem = (info.activityInfo.applicationInfo.flags
                    & ApplicationInfo.FLAG_SYSTEM) != 0;
            final boolean isPrefer = sPreferResolve
                    .contains(info.activityInfo.applicationInfo.packageName);

            if (isPrefer) return info;
            if (isSystem && firstSystem == null) firstSystem = info;
        }

        // Return first system found, otherwise first from list
        return firstSystem != null ? firstSystem : matches.get(0);
    }

    /**
     * Check {@link PackageManager} to see if any apps offer to handle the
     * given {@link Intent}.
     */
    public boolean hasResolve(String mimeType, Intent intent) {
        return getEntry(mimeType, intent).bestResolve != null;
    }

    /**
     * Return the best icon for the given {@link Action}, which is usually
     * based on the {@link ResolveInfo} found through a
     * {@link PackageManager} query.
     */
    public Drawable getIcon(String mimeType, Intent intent) {
        return getEntry(mimeType, intent).icon;
    }

    public void clear() {
        mCache.clear();
    }
}
