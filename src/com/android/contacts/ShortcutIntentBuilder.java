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
package com.android.contacts;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.os.BuildCompat;
import android.telecom.PhoneAccount;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;

import com.android.contacts.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.lettertiles.LetterTileDrawable;
import com.android.contacts.util.BitmapUtil;
import com.android.contacts.util.ImplicitIntentsUtil;

/**
 * Constructs shortcut intents.
 */
public class ShortcutIntentBuilder {

    private static final String[] CONTACT_COLUMNS = {
        Contacts.DISPLAY_NAME,
        Contacts.PHOTO_ID,
        Contacts.LOOKUP_KEY
    };

    private static final int CONTACT_DISPLAY_NAME_COLUMN_INDEX = 0;
    private static final int CONTACT_PHOTO_ID_COLUMN_INDEX = 1;
    private static final int CONTACT_LOOKUP_KEY_COLUMN_INDEX = 2;

    private static final String[] PHONE_COLUMNS = {
        Phone.DISPLAY_NAME,
        Phone.PHOTO_ID,
        Phone.NUMBER,
        Phone.TYPE,
        Phone.LABEL,
        Phone.LOOKUP_KEY
    };

    private static final int PHONE_DISPLAY_NAME_COLUMN_INDEX = 0;
    private static final int PHONE_PHOTO_ID_COLUMN_INDEX = 1;
    private static final int PHONE_NUMBER_COLUMN_INDEX = 2;
    private static final int PHONE_TYPE_COLUMN_INDEX = 3;
    private static final int PHONE_LABEL_COLUMN_INDEX = 4;
    private static final int PHONE_LOOKUP_KEY_COLUMN_INDEX = 5;

    private static final String[] PHOTO_COLUMNS = {
        Photo.PHOTO,
    };

    private static final int PHOTO_PHOTO_COLUMN_INDEX = 0;

    private static final String PHOTO_SELECTION = Photo._ID + "=?";

    private final OnShortcutIntentCreatedListener mListener;
    private final Context mContext;
    private int mIconSize;
    private final int mIconDensity;
    private final int mOverlayTextBackgroundColor;
    private final Resources mResources;

    /**
     * This is a hidden API of the launcher in JellyBean that allows us to disable the animation
     * that it would usually do, because it interferes with our own animation for QuickContact.
     * This is needed since some versions of the launcher override the intent flags and therefore
     * ignore Intent.FLAG_ACTIVITY_NO_ANIMATION.
     */
    public static final String INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION =
            "com.android.launcher.intent.extra.shortcut.INGORE_LAUNCH_ANIMATION";

    /**
     * Listener interface.
     */
    public interface OnShortcutIntentCreatedListener {

        /**
         * Callback for shortcut intent creation.
         *
         * @param uri the original URI for which the shortcut intent has been
         *            created.
         * @param shortcutIntent resulting shortcut intent.
         */
        void onShortcutIntentCreated(Uri uri, Intent shortcutIntent);
    }

    public ShortcutIntentBuilder(Context context, OnShortcutIntentCreatedListener listener) {
        mContext = context;
        mListener = listener;

        mResources = context.getResources();
        final ActivityManager am = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        mIconSize = mResources.getDimensionPixelSize(R.dimen.shortcut_icon_size);
        if (mIconSize == 0) {
            mIconSize = am.getLauncherLargeIconSize();
        }
        mIconDensity = am.getLauncherLargeIconDensity();
        mOverlayTextBackgroundColor = mResources.getColor(R.color.shortcut_overlay_text_background);
    }

    public void createContactShortcutIntent(Uri contactUri) {
        new ContactLoadingAsyncTask(contactUri).execute();
    }

    public void createPhoneNumberShortcutIntent(Uri dataUri, String shortcutAction) {
        new PhoneNumberLoadingAsyncTask(dataUri, shortcutAction).execute();
    }

    /**
     * An asynchronous task that loads name, photo and other data from the database.
     */
    private abstract class LoadingAsyncTask extends AsyncTask<Void, Void, Void> {
        protected Uri mUri;
        protected String mContentType;
        protected String mDisplayName;
        protected String mLookupKey;
        protected byte[] mBitmapData;
        protected long mPhotoId;

        public LoadingAsyncTask(Uri uri) {
            mUri = uri;
        }

        @Override
        protected Void doInBackground(Void... params) {
            mContentType = mContext.getContentResolver().getType(mUri);
            loadData();
            loadPhoto();
            return null;
        }

        protected abstract void loadData();

        private void loadPhoto() {
            if (mPhotoId == 0) {
                return;
            }

            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(Data.CONTENT_URI, PHOTO_COLUMNS, PHOTO_SELECTION,
                    new String[] { String.valueOf(mPhotoId) }, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mBitmapData = cursor.getBlob(PHOTO_PHOTO_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
    }

    private final class ContactLoadingAsyncTask extends LoadingAsyncTask {
        public ContactLoadingAsyncTask(Uri uri) {
            super(uri);
        }

        @Override
        protected void loadData() {
            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(mUri, CONTACT_COLUMNS, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mDisplayName = cursor.getString(CONTACT_DISPLAY_NAME_COLUMN_INDEX);
                        mPhotoId = cursor.getLong(CONTACT_PHOTO_ID_COLUMN_INDEX);
                        mLookupKey = cursor.getString(CONTACT_LOOKUP_KEY_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        @Override
        protected void onPostExecute(Void result) {
            createContactShortcutIntent(mUri, mContentType, mDisplayName, mLookupKey, mBitmapData);
        }
    }

    private final class PhoneNumberLoadingAsyncTask extends LoadingAsyncTask {
        private final String mShortcutAction;
        private String mPhoneNumber;
        private int mPhoneType;
        private String mPhoneLabel;

        public PhoneNumberLoadingAsyncTask(Uri uri, String shortcutAction) {
            super(uri);
            mShortcutAction = shortcutAction;
        }

        @Override
        protected void loadData() {
            ContentResolver resolver = mContext.getContentResolver();
            Cursor cursor = resolver.query(mUri, PHONE_COLUMNS, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        mDisplayName = cursor.getString(PHONE_DISPLAY_NAME_COLUMN_INDEX);
                        mPhotoId = cursor.getLong(PHONE_PHOTO_ID_COLUMN_INDEX);
                        mPhoneNumber = cursor.getString(PHONE_NUMBER_COLUMN_INDEX);
                        mPhoneType = cursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                        mPhoneLabel = cursor.getString(PHONE_LABEL_COLUMN_INDEX);
                        mLookupKey = cursor.getString(PHONE_LOOKUP_KEY_COLUMN_INDEX);
                    }
                } finally {
                    cursor.close();
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            createPhoneNumberShortcutIntent(mUri, mDisplayName, mLookupKey, mBitmapData,
                    mPhoneNumber, mPhoneType, mPhoneLabel, mShortcutAction);
        }
    }

    private Drawable getPhotoDrawable(byte[] bitmapData, String displayName, String lookupKey) {
        if (bitmapData != null) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length, null);
            return new BitmapDrawable(mContext.getResources(), bitmap);
        } else {
            final DefaultImageRequest request = new DefaultImageRequest(displayName, lookupKey,
                    false);
            if (BuildCompat.isAtLeastO()) {
                // On O, scale the image down to add the padding needed by AdaptiveIcons.
                request.scale = LetterTileDrawable.getAdaptiveIconScale();
            }
            return ContactPhotoManager.getDefaultAvatarDrawableForContact(mContext.getResources(),
                    false, request);
        }
    }

    private void createContactShortcutIntent(Uri contactUri, String contentType, String displayName,
            String lookupKey, byte[] bitmapData) {
        Intent intent = null;
        if (TextUtils.isEmpty(displayName)) {
            displayName = mContext.getResources().getString(R.string.missing_name);
        }
        if (BuildCompat.isAtLeastO()) {
            final long contactId = ContentUris.parseId(contactUri);
            final ShortcutManager sm = (ShortcutManager)
                    mContext.getSystemService(Context.SHORTCUT_SERVICE);
            final DynamicShortcuts dynamicShortcuts = new DynamicShortcuts(mContext);
            final ShortcutInfo shortcutInfo = dynamicShortcuts.getQuickContactShortcutInfo(
                    contactId, lookupKey, displayName);
            if (shortcutInfo != null) {
                intent = sm.createShortcutResultIntent(shortcutInfo);
            }
        }
        final Drawable drawable = getPhotoDrawable(bitmapData, displayName, lookupKey);

        final Intent shortcutIntent = ImplicitIntentsUtil.getIntentForQuickContactLauncherShortcut(
                mContext, contactUri);

        intent = intent == null ? new Intent() : intent;

        final Bitmap icon = generateQuickContactIcon(drawable);
        if (BuildCompat.isAtLeastO()) {
            final IconCompat compatIcon = IconCompat.createWithAdaptiveBitmap(icon);
            compatIcon.addToShortcutIntent(intent, null, mContext);
        } else {
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        }
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName);

        mListener.onShortcutIntentCreated(contactUri, intent);
    }

    private void createPhoneNumberShortcutIntent(Uri uri, String displayName, String lookupKey,
            byte[] bitmapData, String phoneNumber, int phoneType, String phoneLabel,
            String shortcutAction) {
        final Drawable drawable = getPhotoDrawable(bitmapData, displayName, lookupKey);
        final Bitmap icon;
        final Uri phoneUri;
        final String shortcutName;
        if (TextUtils.isEmpty(displayName)) {
            displayName = mContext.getResources().getString(R.string.missing_name);
        }

        if (Intent.ACTION_CALL.equals(shortcutAction)) {
            // Make the URI a direct tel: URI so that it will always continue to work
            phoneUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null);
            icon = generatePhoneNumberIcon(drawable, phoneType, phoneLabel,
                    R.drawable.quantum_ic_phone_vd_theme_24);
            shortcutName = mContext.getResources()
                    .getString(R.string.call_by_shortcut, displayName);
        } else {
            phoneUri = Uri.fromParts(ContactsUtils.SCHEME_SMSTO, phoneNumber, null);
            icon = generatePhoneNumberIcon(drawable, phoneType, phoneLabel,
                    R.drawable.quantum_ic_message_vd_theme_24);
            shortcutName = mContext.getResources().getString(R.string.sms_by_shortcut, displayName);
        }

        final Intent shortcutIntent = new Intent(shortcutAction, phoneUri);
        shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        Intent intent = null;
        IconCompat compatAdaptiveIcon = null;
        if (BuildCompat.isAtLeastO()) {
            compatAdaptiveIcon = IconCompat.createWithAdaptiveBitmap(icon);
            final ShortcutManager sm = (ShortcutManager)
                    mContext.getSystemService(Context.SHORTCUT_SERVICE);
            final String id = shortcutAction + lookupKey + phoneUri.toString().hashCode();
            final DynamicShortcuts dynamicShortcuts = new DynamicShortcuts(mContext);
            final ShortcutInfo shortcutInfo = dynamicShortcuts.getActionShortcutInfo(
                    id, displayName, shortcutIntent, compatAdaptiveIcon.toIcon());
            if (shortcutInfo != null) {
                intent = sm.createShortcutResultIntent(shortcutInfo);
            }
        }

        intent = intent == null ? new Intent() : intent;
        // This will be non-null in O and above.
        if (compatAdaptiveIcon != null) {
            compatAdaptiveIcon.addToShortcutIntent(intent, null, mContext);
        } else {
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, icon);
        }
        intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
        intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);

        mListener.onShortcutIntentCreated(uri, intent);
    }

    private Bitmap generateQuickContactIcon(Drawable photo) {
        // Setup the drawing classes
        Bitmap bitmap = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // Copy in the photo
        Rect dst = new Rect(0,0, mIconSize, mIconSize);
        photo.setBounds(dst);
        photo.draw(canvas);

        // Don't put a rounded border on an icon for O
        if (BuildCompat.isAtLeastO()) {
            return bitmap;
        }

        // Draw the icon with a rounded border
        RoundedBitmapDrawable roundedDrawable =
                RoundedBitmapDrawableFactory.create(mResources, bitmap);
        roundedDrawable.setAntiAlias(true);
        roundedDrawable.setCornerRadius(mIconSize / 2);
        Bitmap roundedBitmap = Bitmap.createBitmap(mIconSize, mIconSize, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(roundedBitmap);
        roundedDrawable.setBounds(dst);
        roundedDrawable.draw(canvas);
        canvas.setBitmap(null);

        return roundedBitmap;
    }

    /**
     * Generates a phone number shortcut icon. Adds an overlay describing the type of the phone
     * number, and if there is a photo also adds the call action icon.
     */
    private Bitmap generatePhoneNumberIcon(Drawable photo, int phoneType, String phoneLabel,
            int actionResId) {
        final Resources r = mContext.getResources();
        final float density = r.getDisplayMetrics().density;

        final Drawable phoneDrawable = r.getDrawableForDensity(actionResId, mIconDensity);
        // These icons have the same height and width so either is fine for the size.
        final Bitmap phoneIcon =
                BitmapUtil.drawableToBitmap(phoneDrawable, phoneDrawable.getIntrinsicHeight());

        Bitmap icon = generateQuickContactIcon(photo);
        Canvas canvas = new Canvas(icon);

        // Copy in the photo
        Paint photoPaint = new Paint();
        photoPaint.setDither(true);
        photoPaint.setFilterBitmap(true);
        Rect dst = new Rect(0, 0, mIconSize, mIconSize);

        // Create an overlay for the phone number type if we're pre-O. O created shortcuts have the
        // app badge which overlaps the type overlay.
        CharSequence overlay = Phone.getTypeLabel(r, phoneType, phoneLabel);
        if (!BuildCompat.isAtLeastO() && overlay != null) {
            TextPaint textPaint = new TextPaint(
                    Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
            textPaint.setTextSize(r.getDimension(R.dimen.shortcut_overlay_text_size));
            textPaint.setColor(r.getColor(R.color.textColorIconOverlay));
            textPaint.setShadowLayer(4f, 0, 2f, r.getColor(R.color.textColorIconOverlayShadow));

            final FontMetricsInt fmi = textPaint.getFontMetricsInt();

            // First fill in a darker background around the text to be drawn
            final Paint workPaint = new Paint();
            workPaint.setColor(mOverlayTextBackgroundColor);
            workPaint.setStyle(Paint.Style.FILL);
            final int textPadding = r
                    .getDimensionPixelOffset(R.dimen.shortcut_overlay_text_background_padding);
            final int textBandHeight = (fmi.descent - fmi.ascent) + textPadding * 2;
            dst.set(0, mIconSize - textBandHeight, mIconSize, mIconSize);
            canvas.drawRect(dst, workPaint);

            overlay = TextUtils.ellipsize(overlay, textPaint, mIconSize, TruncateAt.END);
            final float textWidth = textPaint.measureText(overlay, 0, overlay.length());
            canvas.drawText(overlay, 0, overlay.length(), (mIconSize - textWidth) / 2, mIconSize
                    - fmi.descent - textPadding, textPaint);
        }

        // Draw the phone action icon as an overlay
        int iconWidth = icon.getWidth();
        if (BuildCompat.isAtLeastO()) {
            // On O we need to calculate where the phone icon goes slightly differently. The whole
            // canvas area is 108dp, a centered circle with a diameter of 66dp is the "safe zone".
            // So we start the drawing the phone icon at
            // 108dp - 21 dp (distance from right edge of safe zone to the edge of the canvas)
            // - 24 dp (size of the phone icon) on the x axis (left)
            // The y axis is simply 21dp for the distance to the safe zone (top).
            // See go/o-icons-eng for more details and a handy picture.
            final int left = (int) (mIconSize - (45 * density));
            final int top = (int) (21 * density);
            canvas.drawBitmap(phoneIcon, left, top, photoPaint);
        } else {
            dst.set(iconWidth - ((int) (20 * density)), -1,
                    iconWidth, ((int) (19 * density)));
            canvas.drawBitmap(phoneIcon, null, dst, photoPaint);
        }

        canvas.setBitmap(null);
        return icon;
    }
}
