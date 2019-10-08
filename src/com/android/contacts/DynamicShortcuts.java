/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PersistableBundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import androidx.annotation.VisibleForTesting;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.core.os.BuildCompat;
import android.util.Log;

import com.android.contacts.activities.RequestPermissionsActivity;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.lettertiles.LetterTileDrawable;
import com.android.contacts.util.BitmapUtil;
import com.android.contacts.util.ImplicitIntentsUtil;
import com.android.contacts.util.PermissionsUtil;
import com.android.contactsbind.experiments.Flags;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class creates and updates the dynamic shortcuts displayed on the Nexus launcher for the
 * Contacts app.
 *
 * Currently it adds shortcuts for the top 3 contacts in the {@link Contacts#CONTENT_STREQUENT_URI}
 *
 * Usage: DynamicShortcuts.initialize should be called during Application creation. This will
 * schedule a Job to keep the shortcuts up-to-date so no further interactions should be necessary.
 */
@TargetApi(Build.VERSION_CODES.N_MR1)
public class DynamicShortcuts {
    private static final String TAG = "DynamicShortcuts";

    // Must be the same as shortcutId in res/xml/shortcuts.xml
    // Note: This doesn't fit very well because this is a "static" shortcut but it's still the most
    // sensible place to put it right now.
    public static final String SHORTCUT_ADD_CONTACT = "shortcut-add-contact";

    // Note the Nexus launcher automatically truncates shortcut labels if they exceed these limits
    // however, we implement our own truncation in case the shortcut is shown on a launcher that
    // has different behavior
    private static final int SHORT_LABEL_MAX_LENGTH = 12;
    private static final int LONG_LABEL_MAX_LENGTH = 30;
    private static final int MAX_SHORTCUTS = 3;

    private static final String EXTRA_SHORTCUT_TYPE = "extraShortcutType";

    // Because pinned shortcuts persist across app upgrades these values should not be changed
    // though new ones may be added
    private static final int SHORTCUT_TYPE_UNKNOWN = 0;
    private static final int SHORTCUT_TYPE_CONTACT_URI = 1;
    private static final int SHORTCUT_TYPE_ACTION_URI = 2;

    @VisibleForTesting
    static final String[] PROJECTION = new String[] {
            Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY
    };

    private final Context mContext;

    private final ContentResolver mContentResolver;
    private final ShortcutManager mShortcutManager;
    private int mShortLabelMaxLength = SHORT_LABEL_MAX_LENGTH;
    private int mLongLabelMaxLength = LONG_LABEL_MAX_LENGTH;
    private int mIconSize;
    private final int mContentChangeMinUpdateDelay;
    private final int mContentChangeMaxUpdateDelay;
    private final JobScheduler mJobScheduler;

    public DynamicShortcuts(Context context) {
        this(context, context.getContentResolver(), (ShortcutManager)
                context.getSystemService(Context.SHORTCUT_SERVICE),
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE));
    }

    @VisibleForTesting
    public DynamicShortcuts(Context context, ContentResolver contentResolver,
            ShortcutManager shortcutManager, JobScheduler jobScheduler) {
        mContext = context;
        mContentResolver = contentResolver;
        mShortcutManager = shortcutManager;
        mJobScheduler = jobScheduler;
        mContentChangeMinUpdateDelay = Flags.getInstance()
                .getInteger(Experiments.DYNAMIC_MIN_CONTENT_CHANGE_UPDATE_DELAY_MILLIS);
        mContentChangeMaxUpdateDelay = Flags.getInstance()
                .getInteger(Experiments.DYNAMIC_MAX_CONTENT_CHANGE_UPDATE_DELAY_MILLIS);
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        mIconSize = context.getResources().getDimensionPixelSize(R.dimen.shortcut_icon_size);
        if (mIconSize == 0) {
            mIconSize = am.getLauncherLargeIconSize();
        }
    }

    @VisibleForTesting
    void setShortLabelMaxLength(int length) {
        this.mShortLabelMaxLength = length;
    }

    @VisibleForTesting
    void setLongLabelMaxLength(int length) {
        this.mLongLabelMaxLength = length;
    }

    @VisibleForTesting
    void refresh() {
        // Guard here in addition to initialize because this could be run by the JobScheduler
        // after permissions are revoked (maybe)
        if (!hasRequiredPermissions()) return;

        final List<ShortcutInfo> shortcuts = getStrequentShortcuts();
        mShortcutManager.setDynamicShortcuts(shortcuts);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "set dynamic shortcuts " + shortcuts);
        }
        updatePinned();
    }

    @VisibleForTesting
    void updatePinned() {
        final List<ShortcutInfo> updates = new ArrayList<>();
        final List<String> removedIds = new ArrayList<>();
        final List<String> enable = new ArrayList<>();

        for (ShortcutInfo shortcut : mShortcutManager.getPinnedShortcuts()) {
            final PersistableBundle extras = shortcut.getExtras();

            if (extras == null || extras.getInt(EXTRA_SHORTCUT_TYPE, SHORTCUT_TYPE_UNKNOWN) !=
                    SHORTCUT_TYPE_CONTACT_URI) {
                continue;
            }

            // The contact ID may have changed but that's OK because it is just an optimization
            final long contactId = extras.getLong(Contacts._ID);

            final ShortcutInfo update = createShortcutForUri(
                    Contacts.getLookupUri(contactId, shortcut.getId()));
            if (update != null) {
                updates.add(update);
                if (!shortcut.isEnabled()) {
                    // Handle the case that a contact is disabled because it doesn't exist but
                    // later is created (for instance by a sync)
                    enable.add(update.getId());
                }
            } else if (shortcut.isEnabled()) {
                removedIds.add(shortcut.getId());
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "updating " + updates);
            Log.d(TAG, "enabling " + enable);
            Log.d(TAG, "disabling " + removedIds);
        }

        mShortcutManager.updateShortcuts(updates);
        mShortcutManager.enableShortcuts(enable);
        mShortcutManager.disableShortcuts(removedIds,
                mContext.getString(R.string.dynamic_shortcut_contact_removed_message));
    }

    private ShortcutInfo createShortcutForUri(Uri contactUri) {
        final Cursor cursor = mContentResolver.query(contactUri, PROJECTION, null, null, null);
        if (cursor == null) return null;

        try {
            if (cursor.moveToFirst()) {
                return createShortcutFromRow(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public List<ShortcutInfo> getStrequentShortcuts() {
        // The limit query parameter doesn't seem to work for this uri but we'll leave it because in
        // case it does work on some phones or platform versions.
        final Uri uri = Contacts.CONTENT_STREQUENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY,
                        String.valueOf(MAX_SHORTCUTS))
                .build();
        final Cursor cursor = mContentResolver.query(uri, PROJECTION, null, null, null);

        if (cursor == null) return Collections.emptyList();

        final List<ShortcutInfo> result = new ArrayList<>();

        try {
            int i = 0;
            while (i < MAX_SHORTCUTS && cursor.moveToNext()) {
                final ShortcutInfo shortcut = createShortcutFromRow(cursor);
                if (shortcut == null) {
                    continue;
                }
                result.add(shortcut);
                i++;
            }
        } finally {
            cursor.close();
        }
        return result;
    }


    @VisibleForTesting
    ShortcutInfo createShortcutFromRow(Cursor cursor) {
        final ShortcutInfo.Builder builder = builderForContactShortcut(cursor);
        if (builder == null) {
            return null;
        }
        addIconForContact(cursor, builder);
        return builder.build();
    }

    @VisibleForTesting
    ShortcutInfo.Builder builderForContactShortcut(Cursor cursor) {
        final long id = cursor.getLong(0);
        final String lookupKey = cursor.getString(1);
        final String displayName = cursor.getString(2);
        return builderForContactShortcut(id, lookupKey, displayName);
    }

    @VisibleForTesting
    ShortcutInfo.Builder builderForContactShortcut(long id, String lookupKey, String displayName) {
        if (lookupKey == null || displayName == null) {
            return null;
        }
        final PersistableBundle extras = new PersistableBundle();
        extras.putLong(Contacts._ID, id);
        extras.putInt(EXTRA_SHORTCUT_TYPE, SHORTCUT_TYPE_CONTACT_URI);

        final ShortcutInfo.Builder builder = new ShortcutInfo.Builder(mContext, lookupKey)
                .setIntent(ImplicitIntentsUtil.getIntentForQuickContactLauncherShortcut(mContext,
                        Contacts.getLookupUri(id, lookupKey)))
                .setDisabledMessage(mContext.getString(R.string.dynamic_shortcut_disabled_message))
                .setExtras(extras);

        setLabel(builder, displayName);
        return builder;
    }

    @VisibleForTesting
    ShortcutInfo getActionShortcutInfo(String id, String label, Intent action, Icon icon) {
        if (id == null || label == null) {
            return null;
        }
        final PersistableBundle extras = new PersistableBundle();
        extras.putInt(EXTRA_SHORTCUT_TYPE, SHORTCUT_TYPE_ACTION_URI);

        final ShortcutInfo.Builder builder = new ShortcutInfo.Builder(mContext, id)
                .setIntent(action)
                .setIcon(icon)
                .setExtras(extras)
                .setDisabledMessage(mContext.getString(R.string.dynamic_shortcut_disabled_message));

        setLabel(builder, label);
        return builder.build();
    }

    public ShortcutInfo getQuickContactShortcutInfo(long id, String lookupKey, String displayName) {
        final ShortcutInfo.Builder builder = builderForContactShortcut(id, lookupKey, displayName);
        if (builder == null) {
            return null;
        }
        addIconForContact(id, lookupKey, displayName, builder);
        return builder.build();
    }

    private void setLabel(ShortcutInfo.Builder builder, String label) {
        if (label.length() < mLongLabelMaxLength) {
            builder.setLongLabel(label);
        } else {
            builder.setLongLabel(label.substring(0, mLongLabelMaxLength - 1).trim() + "…");
        }

        if (label.length() < mShortLabelMaxLength) {
            builder.setShortLabel(label);
        } else {
            builder.setShortLabel(label.substring(0, mShortLabelMaxLength - 1).trim() + "…");
        }
    }

    private void addIconForContact(Cursor cursor, ShortcutInfo.Builder builder) {
        final long id = cursor.getLong(0);
        final String lookupKey = cursor.getString(1);
        final String displayName = cursor.getString(2);
        addIconForContact(id, lookupKey, displayName, builder);
    }

    private void addIconForContact(long id, String lookupKey, String displayName,
            ShortcutInfo.Builder builder) {
        Bitmap bitmap = getContactPhoto(id);
        if (bitmap == null) {
            bitmap = getFallbackAvatar(displayName, lookupKey);
        }
        final Icon icon;
        if (BuildCompat.isAtLeastO()) {
            icon = Icon.createWithAdaptiveBitmap(bitmap);
        } else {
            icon = Icon.createWithBitmap(bitmap);
        }

        builder.setIcon(icon);
    }

    private Bitmap getContactPhoto(long id) {
        final InputStream photoStream = Contacts.openContactPhotoInputStream(
                mContext.getContentResolver(),
                ContentUris.withAppendedId(Contacts.CONTENT_URI, id), true);

        if (photoStream == null) return null;
        try {
            final Bitmap bitmap = decodeStreamForShortcut(photoStream);
            photoStream.close();
            return bitmap;
        } catch (IOException e) {
            Log.e(TAG, "Failed to decode contact photo for shortcut. ID=" + id, e);
            return null;
        } finally {
            try {
                photoStream.close();
            } catch (IOException e) {
                // swallow
            }
        }
    }

    private Bitmap decodeStreamForShortcut(InputStream stream) throws IOException {
        final BitmapRegionDecoder bitmapDecoder = BitmapRegionDecoder.newInstance(stream, false);

        final int sourceWidth = bitmapDecoder.getWidth();
        final int sourceHeight = bitmapDecoder.getHeight();

        final int iconMaxWidth = mShortcutManager.getIconMaxWidth();
        final int iconMaxHeight = mShortcutManager.getIconMaxHeight();

        final int sampleSize = Math.min(
                BitmapUtil.findOptimalSampleSize(sourceWidth, mIconSize),
                BitmapUtil.findOptimalSampleSize(sourceHeight, mIconSize));
        final BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sampleSize;

        final int scaledWidth = sourceWidth / opts.inSampleSize;
        final int scaledHeight = sourceHeight / opts.inSampleSize;

        final int targetWidth = Math.min(scaledWidth, iconMaxWidth);
        final int targetHeight = Math.min(scaledHeight, iconMaxHeight);

        // Make it square.
        final int targetSize = Math.min(targetWidth, targetHeight);

        // The region is defined in the coordinates of the source image then the sampling is
        // done on the extracted region.
        final int prescaledXOffset = ((scaledWidth - targetSize) * opts.inSampleSize) / 2;
        final int prescaledYOffset = ((scaledHeight - targetSize) * opts.inSampleSize) / 2;

        final Bitmap bitmap = bitmapDecoder.decodeRegion(new Rect(
                prescaledXOffset, prescaledYOffset,
                sourceWidth - prescaledXOffset, sourceHeight - prescaledYOffset
        ), opts);
        bitmapDecoder.recycle();

        if (!BuildCompat.isAtLeastO()) {
            return BitmapUtil.getRoundedBitmap(bitmap, targetSize, targetSize);
        }

        return bitmap;
    }

    private Bitmap getFallbackAvatar(String displayName, String lookupKey) {
        // Use a circular icon if we're not on O or higher.
        final boolean circularIcon = !BuildCompat.isAtLeastO();

        final ContactPhotoManager.DefaultImageRequest request =
                new ContactPhotoManager.DefaultImageRequest(displayName, lookupKey, circularIcon);
        if (BuildCompat.isAtLeastO()) {
            // On O, scale the image down to add the padding needed by AdaptiveIcons.
            request.scale = LetterTileDrawable.getAdaptiveIconScale();
        }
        final Drawable avatar = ContactPhotoManager.getDefaultAvatarDrawableForContact(
                mContext.getResources(), true, request);
        final Bitmap result = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        // The avatar won't draw unless it thinks it is visible
        avatar.setVisible(true, true);
        final Canvas canvas = new Canvas(result);
        avatar.setBounds(0, 0, mIconSize, mIconSize);
        avatar.draw(canvas);
        return result;
    }

    @VisibleForTesting
    void handleFlagDisabled() {
        removeAllShortcuts();
        mJobScheduler.cancel(ContactsJobService.DYNAMIC_SHORTCUTS_JOB_ID);
    }

    private void removeAllShortcuts() {
        mShortcutManager.removeAllDynamicShortcuts();

        final List<ShortcutInfo> pinned = mShortcutManager.getPinnedShortcuts();
        final List<String> ids = new ArrayList<>(pinned.size());
        for (ShortcutInfo shortcut : pinned) {
            ids.add(shortcut.getId());
        }
        mShortcutManager.disableShortcuts(ids, mContext
                .getString(R.string.dynamic_shortcut_disabled_message));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "DynamicShortcuts have been removed.");
        }
    }

    @VisibleForTesting
    void scheduleUpdateJob() {
        final JobInfo job = new JobInfo.Builder(
                ContactsJobService.DYNAMIC_SHORTCUTS_JOB_ID,
                new ComponentName(mContext, ContactsJobService.class))
                // We just observe all changes to contacts. It would be better to be more granular
                // but CP2 only notifies using this URI anyway so there isn't any point in adding
                // that complexity.
                .addTriggerContentUri(new JobInfo.TriggerContentUri(ContactsContract.AUTHORITY_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(mContentChangeMinUpdateDelay)
                .setTriggerContentMaxDelay(mContentChangeMaxUpdateDelay)
                .build();
        mJobScheduler.schedule(job);
    }

    void updateInBackground() {
        new ShortcutUpdateTask(this).execute();
    }

    public synchronized static void initialize(Context context) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            final Flags flags = Flags.getInstance();
            Log.d(TAG, "DyanmicShortcuts.initialize\nVERSION >= N_MR1? " +
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) +
                    "\nisJobScheduled? " +
                    (CompatUtils.isLauncherShortcutCompatible() && isJobScheduled(context)) +
                    "\nminDelay=" +
                    flags.getInteger(Experiments.DYNAMIC_MIN_CONTENT_CHANGE_UPDATE_DELAY_MILLIS) +
                    "\nmaxDelay=" +
                    flags.getInteger(Experiments.DYNAMIC_MAX_CONTENT_CHANGE_UPDATE_DELAY_MILLIS));
        }

        if (!CompatUtils.isLauncherShortcutCompatible()) return;

        final DynamicShortcuts shortcuts = new DynamicShortcuts(context);

        if (!shortcuts.hasRequiredPermissions()) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(RequestPermissionsActivity.BROADCAST_PERMISSIONS_GRANTED);
            LocalBroadcastManager.getInstance(shortcuts.mContext).registerReceiver(
                    new PermissionsGrantedReceiver(), filter);
        } else if (!isJobScheduled(context)) {
            // Update the shortcuts. If the job is already scheduled then either the app is being
            // launched to run the job in which case the shortcuts will get updated when it runs or
            // it has been launched for some other reason and the data we care about for shortcuts
            // hasn't changed. Because the job reschedules itself after completion this check
            // essentially means that this will run on each app launch that happens after a reboot.
            // Note: the task schedules the job after completing.
            new ShortcutUpdateTask(shortcuts).execute();
        }
    }

    @VisibleForTesting
    public static void reset(Context context) {
        final JobScheduler jobScheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobScheduler.cancel(ContactsJobService.DYNAMIC_SHORTCUTS_JOB_ID);

        if (!CompatUtils.isLauncherShortcutCompatible()) {
            return;
        }
        new DynamicShortcuts(context).removeAllShortcuts();
    }

    @VisibleForTesting
    boolean hasRequiredPermissions() {
        return PermissionsUtil.hasContactsPermissions(mContext);
    }

    public static void updateFromJob(final JobService service, final JobParameters jobParams) {
        new ShortcutUpdateTask(new DynamicShortcuts(service)) {
            @Override
            protected void onPostExecute(Void aVoid) {
                // Must call super first which will reschedule the job before we call jobFinished
                super.onPostExecute(aVoid);
                service.jobFinished(jobParams, false);
            }
        }.execute();
    }

    @VisibleForTesting
    public static boolean isJobScheduled(Context context) {
        final JobScheduler scheduler = (JobScheduler) context
                .getSystemService(Context.JOB_SCHEDULER_SERVICE);
        return scheduler.getPendingJob(ContactsJobService.DYNAMIC_SHORTCUTS_JOB_ID) != null;
    }

    public static void reportShortcutUsed(Context context, String lookupKey) {
        if (!CompatUtils.isLauncherShortcutCompatible() || lookupKey == null) return;
        final ShortcutManager shortcutManager = (ShortcutManager) context
                .getSystemService(Context.SHORTCUT_SERVICE);
        shortcutManager.reportShortcutUsed(lookupKey);
    }

    private static class ShortcutUpdateTask extends AsyncTask<Void, Void, Void> {
        private DynamicShortcuts mDynamicShortcuts;

        public ShortcutUpdateTask(DynamicShortcuts shortcuts) {
            mDynamicShortcuts = shortcuts;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            mDynamicShortcuts.refresh();
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "ShorcutUpdateTask.onPostExecute");
            }
            // The shortcuts may have changed so update the job so that we are observing the
            // correct Uris
            mDynamicShortcuts.scheduleUpdateJob();
        }
    }

    private static class PermissionsGrantedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Clear the receiver.
            LocalBroadcastManager.getInstance(context).unregisterReceiver(this);
            DynamicShortcuts.initialize(context);
        }
    }
}
