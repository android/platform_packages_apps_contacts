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

package com.android.contacts.ui;

import com.android.contacts.Collapser;
import com.android.contacts.ContactPresenceIconUtil;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.model.ContactsSource;
import com.android.contacts.model.Sources;
import com.android.contacts.model.ContactsSource.DataKind;
import com.android.contacts.ui.widget.CheckableImageView;
import com.android.contacts.util.Constants;
import com.android.contacts.util.DataStatus;
import com.android.contacts.util.NotifyingAsyncQueryHandler;
import com.android.internal.policy.PolicyManager;
import com.google.android.collect.Sets;

import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.QuickContact;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.StatusUpdates;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.CommonDataKinds.Website;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Window that shows QuickContact dialog for a specific {@link Contacts#_ID}.
 */
public class QuickContactWindow implements Window.Callback,
        NotifyingAsyncQueryHandler.AsyncQueryListener, View.OnClickListener,
        AbsListView.OnItemClickListener, CompoundButton.OnCheckedChangeListener, KeyEvent.Callback,
        OnGlobalLayoutListener {
    private static final String TAG = "QuickContactWindow";

    /**
     * Interface used to allow the person showing a {@link QuickContactWindow} to
     * know when the window has been dismissed.
     */
    public interface OnDismissListener {
        public void onDismiss(QuickContactWindow dialog);
    }

    /**
     * Custom layout the sole purpose of which is to intercept the BACK key and
     * close QC even when the soft keyboard is open.
     */
    public static class RootLayout extends RelativeLayout {

        QuickContactWindow mQuickContactWindow;

        public RootLayout(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        /**
         * Intercepts the BACK key event and dismisses QuickContact window.
         */
        @Override
        public boolean dispatchKeyEventPreIme(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                mQuickContactWindow.onBackPressed();
                return true;
            } else {
                return super.dispatchKeyEventPreIme(event);
            }
        }
    }

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final WindowManager mWindowManager;
    private Window mWindow;
    private View mDecor;
    private final Rect mRect = new Rect();

    private boolean mDismissed = false;
    private boolean mQuerying = false;
    private boolean mShowing = false;

    private NotifyingAsyncQueryHandler mHandler;
    private OnDismissListener mDismissListener;
    private ResolveCache mResolveCache;

    private Uri mLookupUri;
    private Rect mAnchor;

    private int mShadowHoriz;
    private int mShadowVert;
    private int mShadowTouch;

    private int mScreenWidth;
    private int mScreenHeight;
    private int mRequestedY;

    private boolean mHasValidSocial = false;
    private boolean mMakePrimary = false;

    private ImageView mArrowUp;
    private ImageView mArrowDown;

    private int mMode;
    private RootLayout mRootView;
    private View mHeader;
    private HorizontalScrollView mTrackScroll;
    private ViewGroup mTrack;
    private Animation mTrackAnim;

    private View mFooter;
    private View mFooterDisambig;
    private ListView mResolveList;
    private CheckableImageView mLastAction;
    private CheckBox mSetPrimaryCheckBox;

    private int mWindowRecycled = 0;
    private int mActionRecycled = 0;

    /**
     * Set of {@link Action} that are associated with the aggregate currently
     * displayed by this dialog, represented as a map from {@link String}
     * MIME-type to {@link ActionList}.
     */
    private ActionMap mActions = new ActionMap();

    /**
     * Pool of unused {@link CheckableImageView} that have previously been
     * inflated, and are ready to be recycled through {@link #obtainView()}.
     */
    private LinkedList<View> mActionPool = new LinkedList<View>();

    private String[] mExcludeMimes;

    /**
     * {@link #PRECEDING_MIMETYPES} and {@link #FOLLOWING_MIMETYPES} are used to sort MIME-types.
     *
     * <p>The MIME-types in {@link #PRECEDING_MIMETYPES} appear in the front of the dialog,
     * in the order in the array.
     *
     * <p>The ones in {@link #FOLLOWING_MIMETYPES} appear in the end of the dialog, in alphabetical
     * order.
     *
     * <p>The rest go between them, in the order in the array.
     */
    private static final String[] PRECEDING_MIMETYPES = new String[] {
            Phone.CONTENT_ITEM_TYPE,
            Contacts.CONTENT_ITEM_TYPE,
            Constants.MIME_SMS_ADDRESS,
            Email.CONTENT_ITEM_TYPE,
    };

    /**
     * See {@link #PRECEDING_MIMETYPES}.
     */
    private static final String[] FOLLOWING_MIMETYPES = new String[] {
            StructuredPostal.CONTENT_ITEM_TYPE,
            Website.CONTENT_ITEM_TYPE,
    };

    /**
     * Specific list {@link ApplicationInfo#packageName} of apps that are
     * prefered <strong>only</strong> for the purposes of default icons when
     * multiple {@link ResolveInfo} are found to match. This only happens when
     * the user has not selected a default app yet, and they will still be
     * presented with the system disambiguation dialog.
     */
    private static final HashSet<String> sPreferResolve = Sets.newHashSet(
            "com.android.email",
            "com.android.calendar",
            "com.android.contacts",
            "com.android.mms",
            "com.android.phone",
            "com.android.browser");

    private static final int TOKEN_DATA = 1;

    static final boolean LOGD = false;

    static final boolean TRACE_LAUNCH = false;
    static final String TRACE_TAG = "quickcontact";

    /**
     * Prepare a dialog to show in the given {@link Context}.
     */
    public QuickContactWindow(Context context) {
        mContext = new ContextThemeWrapper(context, R.style.QuickContact);
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);

        mWindow = PolicyManager.makeNewWindow(mContext);
        mWindow.setCallback(this);
        mWindow.setWindowManager(mWindowManager, null, null);
        mWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);

        mWindow.setContentView(R.layout.quickcontact);

        mRootView = (RootLayout)mWindow.findViewById(R.id.root);
        mRootView.mQuickContactWindow = this;
        mRootView.setFocusable(true);
        mRootView.setFocusableInTouchMode(true);
        mRootView.setDescendantFocusability(RootLayout.FOCUS_AFTER_DESCENDANTS);

        mArrowUp = (ImageView)mWindow.findViewById(R.id.arrow_up);
        mArrowDown = (ImageView)mWindow.findViewById(R.id.arrow_down);

        mResolveCache = new ResolveCache(mContext);

        final Resources res = mContext.getResources();
        mShadowHoriz = res.getDimensionPixelSize(R.dimen.quickcontact_shadow_horiz);
        mShadowVert = res.getDimensionPixelSize(R.dimen.quickcontact_shadow_vert);
        mShadowTouch = res.getDimensionPixelSize(R.dimen.quickcontact_shadow_touch);

        mScreenWidth = mWindowManager.getDefaultDisplay().getWidth();
        mScreenHeight = mWindowManager.getDefaultDisplay().getHeight();

        mTrack = (ViewGroup)mWindow.findViewById(R.id.quickcontact);
        mTrackScroll = (HorizontalScrollView)mWindow.findViewById(R.id.scroll);

        mFooter = mWindow.findViewById(R.id.footer);
        mFooterDisambig = mWindow.findViewById(R.id.footer_disambig);
        mResolveList = (ListView)mWindow.findViewById(android.R.id.list);
        mSetPrimaryCheckBox = (CheckBox)mWindow.findViewById(android.R.id.checkbox);

        mSetPrimaryCheckBox.setOnCheckedChangeListener(this);

        // Prepare track entrance animation
        mTrackAnim = AnimationUtils.loadAnimation(mContext, R.anim.quickcontact);
        mTrackAnim.setInterpolator(new Interpolator() {
            public float getInterpolation(float t) {
                // Pushes past the target area, then snaps back into place.
                // Equation for graphing: 1.2-((x*1.6)-1.1)^2
                final float inner = (t * 1.55f) - 1.1f;
                return 1.2f - inner * inner;
            }
        });

        mHandler = new NotifyingAsyncQueryHandler(mContext, this);
    }

    /**
     * Prepare a dialog to show in the given {@link Context}, and notify the
     * given {@link OnDismissListener} each time this dialog is dismissed.
     */
    public QuickContactWindow(Context context, OnDismissListener dismissListener) {
        this(context);
        mDismissListener = dismissListener;
    }

    private View getHeaderView(int mode) {
        View header = null;
        switch (mode) {
            case QuickContact.MODE_SMALL:
                header = mWindow.findViewById(R.id.header_small);
                break;
            case QuickContact.MODE_MEDIUM:
                header = mWindow.findViewById(R.id.header_medium);
                break;
            case QuickContact.MODE_LARGE:
                header = mWindow.findViewById(R.id.header_large);
                break;
        }

        if (header instanceof ViewStub) {
            // Inflate actual header if we picked a stub
            final ViewStub stub = (ViewStub)header;
            header = stub.inflate();
        } else if (header != null) {
            header.setVisibility(View.VISIBLE);
        }

        return header;
    }

    /**
     * Start showing a dialog for the given {@link Contacts#_ID} pointing
     * towards the given location.
     */
    public synchronized void show(Uri lookupUri, Rect anchor, int mode, String[] excludeMimes) {
        if (mQuerying || mShowing) {
            Log.w(TAG, "dismissing before showing");
            dismissInternal();
        }

        if (TRACE_LAUNCH && !android.os.Debug.isMethodTracingActive()) {
            android.os.Debug.startMethodTracing(TRACE_TAG);
        }

        // Validate incoming parameters
        final boolean validMode = (mode == QuickContact.MODE_SMALL
                || mode == QuickContact.MODE_MEDIUM || mode == QuickContact.MODE_LARGE);
        if (!validMode) {
            throw new IllegalArgumentException("Invalid mode, expecting MODE_LARGE, "
                    + "MODE_MEDIUM, or MODE_SMALL");
        }

        if (anchor == null) {
            throw new IllegalArgumentException("Missing anchor rectangle");
        }

        // Prepare header view for requested mode
        mLookupUri = lookupUri;
        mAnchor = new Rect(anchor);
        mMode = mode;
        mExcludeMimes = excludeMimes;

        mHeader = getHeaderView(mode);

        setHeaderText(R.id.name, R.string.quickcontact_missing_name);

        setHeaderText(R.id.status, null);
        setHeaderText(R.id.timestamp, null);

        setHeaderImage(R.id.presence, null);

        resetTrack();

        // We need to have a focused view inside the QuickContact window so
        // that the BACK key event can be intercepted
        mRootView.requestFocus();

        mHasValidSocial = false;
        mDismissed = false;
        mQuerying = true;

        // Start background query for data, but only select photo rows when they
        // directly match the super-primary PHOTO_ID.
        final Uri dataUri = getDataUri(lookupUri);
        mHandler.cancelOperation(TOKEN_DATA);

        // Only request photo data when required by mode
        if (mMode == QuickContact.MODE_LARGE) {
            // Select photos, but only super-primary
            mHandler.startQuery(TOKEN_DATA, lookupUri, dataUri, DataQuery.PROJECTION, Data.MIMETYPE
                    + "!=? OR (" + Data.MIMETYPE + "=? AND " + Data._ID + "=" + Contacts.PHOTO_ID
                    + ")", new String[] { Photo.CONTENT_ITEM_TYPE, Photo.CONTENT_ITEM_TYPE }, null);
        } else {
            // Exclude all photos from cursor
            mHandler.startQuery(TOKEN_DATA, lookupUri, dataUri, DataQuery.PROJECTION, Data.MIMETYPE
                    + "!=?", new String[] { Photo.CONTENT_ITEM_TYPE }, null);
        }
    }

    /**
     * Build a {@link Uri} into the {@link Data} table for the requested
     * {@link Contacts#CONTENT_LOOKUP_URI} style {@link Uri}.
     */
    private Uri getDataUri(Uri lookupUri) {
        // TODO: Formalize method of extracting LOOKUP_KEY
        final List<String> path = lookupUri.getPathSegments();
        final boolean validLookup = path.size() >= 3 && "lookup".equals(path.get(1));
        if (!validLookup) {
            // We only accept valid lookup-style Uris
            throw new IllegalArgumentException("Expecting lookup-style Uri");
        } else if (path.size() == 3) {
            // No direct _ID provided, so force a lookup
            lookupUri = Contacts.lookupContact(mContext.getContentResolver(), lookupUri);
        }

        final long contactId = ContentUris.parseId(lookupUri);
        return Uri.withAppendedPath(ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                Contacts.Data.CONTENT_DIRECTORY);
    }

    /**
     * Show the correct call-out arrow based on a {@link R.id} reference.
     */
    private void showArrow(int whichArrow, int requestedX) {
        final View showArrow = (whichArrow == R.id.arrow_up) ? mArrowUp : mArrowDown;
        final View hideArrow = (whichArrow == R.id.arrow_up) ? mArrowDown : mArrowUp;

        final int arrowWidth = mArrowUp.getMeasuredWidth();

        showArrow.setVisibility(View.VISIBLE);
        ViewGroup.MarginLayoutParams param = (ViewGroup.MarginLayoutParams)showArrow.getLayoutParams();
        param.leftMargin = requestedX - arrowWidth / 2;

        hideArrow.setVisibility(View.INVISIBLE);
    }

    /**
     * Actual internal method to show this dialog. Called only by
     * {@link #considerShowing()} when all data requirements have been met.
     */
    private void showInternal() {
        mDecor = mWindow.getDecorView();
        mDecor.getViewTreeObserver().addOnGlobalLayoutListener(this);
        WindowManager.LayoutParams l = mWindow.getAttributes();

        l.width = mScreenWidth + mShadowHoriz + mShadowHoriz;
        l.height = WindowManager.LayoutParams.WRAP_CONTENT;

        // Force layout measuring pass so we have baseline numbers
        mDecor.measure(l.width, l.height);
        final int blockHeight = mDecor.getMeasuredHeight();

        l.gravity = Gravity.TOP | Gravity.LEFT;
        l.x = -mShadowHoriz;

        if (mAnchor.top > blockHeight) {
            // Show downwards callout when enough room, aligning bottom block
            // edge with top of anchor area, and adjusting to inset arrow.
            showArrow(R.id.arrow_down, mAnchor.centerX());
            l.y = mAnchor.top - blockHeight + mShadowVert;
            l.windowAnimations = R.style.QuickContactAboveAnimation;

        } else {
            // Otherwise show upwards callout, aligning block top with bottom of
            // anchor area, and adjusting to inset arrow.
            showArrow(R.id.arrow_up, mAnchor.centerX());
            l.y = mAnchor.bottom - mShadowVert;
            l.windowAnimations = R.style.QuickContactBelowAnimation;

        }

        l.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        mRequestedY = l.y;
        mWindowManager.addView(mDecor, l);
        mShowing = true;
        mQuerying = false;
        mDismissed = false;

        mTrack.startAnimation(mTrackAnim);

        if (TRACE_LAUNCH) {
            android.os.Debug.stopMethodTracing();
            Log.d(TAG, "Window recycled " + mWindowRecycled + " times, chiclets "
                    + mActionRecycled + " times");
        }
    }

    /** {@inheritDoc} */
    public void onGlobalLayout() {
        layoutInScreen();
    }

    /**
     * Adjust vertical {@link WindowManager.LayoutParams} to fit window as best
     * as possible, shifting up to display content as needed.
     */
    private void layoutInScreen() {
        if (!mShowing) return;

        final WindowManager.LayoutParams l = mWindow.getAttributes();
        final int originalY = l.y;

        final int blockHeight = mDecor.getHeight();

        l.y = mRequestedY;
        if (mRequestedY + blockHeight > mScreenHeight) {
            // Shift up from bottom when overflowing
            l.y = mScreenHeight - blockHeight;
        }

        if (originalY != l.y) {
            // Only update when value is changed
            mWindow.setAttributes(l);
        }
    }

    /**
     * Dismiss this dialog if showing.
     */
    public synchronized void dismiss() {
        // Notify any listeners that we've been dismissed
        if (mDismissListener != null) {
            mDismissListener.onDismiss(this);
        }

        dismissInternal();
    }

    private void dismissInternal() {
        // Remove any attached window decor for recycling
        boolean hadDecor = mDecor != null;
        if (hadDecor) {
            mWindowManager.removeView(mDecor);
            mWindowRecycled++;
            mDecor.getViewTreeObserver().removeGlobalOnLayoutListener(this);
            mDecor = null;
            mWindow.closeAllPanels();
        }
        mShowing = false;
        mDismissed = true;

        // Cancel any pending queries
        mHandler.cancelOperation(TOKEN_DATA);
        mQuerying = false;

        // Completely hide header and reset track
        mHeader.setVisibility(View.GONE);
        resetTrack();
    }

    /**
     * Reset track to initial state, recycling any chiclets.
     */
    private void resetTrack() {
        // Release reference to last chiclet
        mLastAction = null;

        // Clear track actions and scroll to hard left
        mResolveCache.clear();
        mActions.clear();

        // Recycle any chiclets in use
        while (mTrack.getChildCount() > 2) {
            this.releaseView(mTrack.getChildAt(1));
            mTrack.removeViewAt(1);
        }

        mTrackScroll.fullScroll(View.FOCUS_LEFT);
        mWasDownArrow = false;

        // Clear any primary requests
        mMakePrimary = false;
        mSetPrimaryCheckBox.setChecked(false);

        setResolveVisible(false, null);
    }

    /**
     * Consider showing this window, which will only call through to
     * {@link #showInternal()} when all data items are present.
     */
    private void considerShowing() {
        if (!mShowing && !mDismissed) {
            if (mMode == QuickContact.MODE_MEDIUM && !mHasValidSocial) {
                // Missing valid social, swap medium for small header
                mHeader.setVisibility(View.GONE);
                mHeader = getHeaderView(QuickContact.MODE_SMALL);
            }

            // All queries have returned, pull curtain
            showInternal();
        }
    }

    /** {@inheritDoc} */
    public synchronized void onQueryComplete(int token, Object cookie, Cursor cursor) {
        // Bail early when query is stale
        if (cookie != mLookupUri) return;

        if (cursor == null) {
            // Problem while running query, so bail without showing
            Log.w(TAG, "Missing cursor for token=" + token);
            this.dismiss();
            return;
        }

        handleData(cursor);

        if (!cursor.isClosed()) {
            cursor.close();
        }

        considerShowing();
    }

    /** Assign this string to the view, if found in {@link #mHeader}. */
    private void setHeaderText(int id, int resId) {
        setHeaderText(id, mContext.getResources().getText(resId));
    }

    /** Assign this string to the view, if found in {@link #mHeader}. */
    private void setHeaderText(int id, CharSequence value) {
        final View view = mHeader.findViewById(id);
        if (view instanceof TextView) {
            ((TextView)view).setText(value);
            view.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
        }
    }

    /** Assign this image to the view, if found in {@link #mHeader}. */
    private void setHeaderImage(int id, Drawable drawable) {
        final View view = mHeader.findViewById(id);
        if (view instanceof ImageView) {
            ((ImageView)view).setImageDrawable(drawable);
            view.setVisibility(drawable == null ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Find the QuickContact-specific presence icon for showing in chiclets.
     */
    private Drawable getTrackPresenceIcon(int status) {
        int resId;
        switch (status) {
            case StatusUpdates.AVAILABLE:
                resId = R.drawable.quickcontact_slider_presence_active;
                break;
            case StatusUpdates.IDLE:
            case StatusUpdates.AWAY:
                resId = R.drawable.quickcontact_slider_presence_away;
                break;
            case StatusUpdates.DO_NOT_DISTURB:
                resId = R.drawable.quickcontact_slider_presence_busy;
                break;
            case StatusUpdates.INVISIBLE:
                resId = R.drawable.quickcontact_slider_presence_inactive;
                break;
            case StatusUpdates.OFFLINE:
            default:
                resId = R.drawable.quickcontact_slider_presence_inactive;
        }
        return mContext.getResources().getDrawable(resId);
    }

    /** Read {@link String} from the given {@link Cursor}. */
    private static String getAsString(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return cursor.getString(index);
    }

    /** Read {@link Integer} from the given {@link Cursor}. */
    private static int getAsInt(Cursor cursor, String columnName) {
        final int index = cursor.getColumnIndex(columnName);
        return cursor.getInt(index);
    }

    /**
     * Abstract definition of an action that could be performed, along with
     * string description and icon.
     */
    private interface Action extends Collapser.Collapsible<Action> {
        public CharSequence getHeader();
        public CharSequence getBody();

        public String getMimeType();
        public Drawable getFallbackIcon();

        /**
         * Build an {@link Intent} that will perform this action.
         */
        public Intent getIntent();

        /**
         * Checks if the contact data for this action is primary.
         */
        public Boolean isPrimary();

        /**
         * Returns a lookup (@link Uri) for the contact data item.
         */
        public Uri getDataUri();
    }

    /**
     * Description of a specific {@link Data#_ID} item, with style information
     * defined by a {@link DataKind}.
     */
    private static class DataAction implements Action {
        private final Context mContext;
        private final DataKind mKind;
        private final String mMimeType;

        private CharSequence mHeader;
        private CharSequence mBody;
        private Intent mIntent;

        private boolean mAlternate;
        private Uri mDataUri;
        private boolean mIsPrimary;

        /**
         * Create an action from common {@link Data} elements.
         */
        public DataAction(Context context, String mimeType, DataKind kind,
                long dataId, Cursor cursor) {
            mContext = context;
            mKind = kind;
            mMimeType = mimeType;

            // Inflate strings from cursor
            mAlternate = Constants.MIME_SMS_ADDRESS.equals(mimeType);
            if (mAlternate && mKind.actionAltHeader != null) {
                mHeader = mKind.actionAltHeader.inflateUsing(context, cursor);
            } else if (mKind.actionHeader != null) {
                mHeader = mKind.actionHeader.inflateUsing(context, cursor);
            }

            if (getAsInt(cursor, Data.IS_SUPER_PRIMARY) != 0) {
                mIsPrimary = true;
            }

            if (mKind.actionBody != null) {
                mBody = mKind.actionBody.inflateUsing(context, cursor);
            }

            mDataUri = ContentUris.withAppendedId(Data.CONTENT_URI, dataId);

            // Handle well-known MIME-types with special care
            if (Phone.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final String number = getAsString(cursor, Phone.NUMBER);
                if (!TextUtils.isEmpty(number)) {
                    final Uri callUri = Uri.fromParts(Constants.SCHEME_TEL, number, null);
                    mIntent = new Intent(Intent.ACTION_CALL_PRIVILEGED, callUri);
                }

            } else if (Constants.MIME_SMS_ADDRESS.equals(mimeType)) {
                final String number = getAsString(cursor, Phone.NUMBER);
                if (!TextUtils.isEmpty(number)) {
                    final Uri smsUri = Uri.fromParts(Constants.SCHEME_SMSTO, number, null);
                    mIntent = new Intent(Intent.ACTION_SENDTO, smsUri);
                }

            } else if (Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final String address = getAsString(cursor, Email.DATA);
                if (!TextUtils.isEmpty(address)) {
                    final Uri mailUri = Uri.fromParts(Constants.SCHEME_MAILTO, address, null);
                    mIntent = new Intent(Intent.ACTION_SENDTO, mailUri);
                }

            } else if (Website.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final String url = getAsString(cursor, Website.URL);
                if (!TextUtils.isEmpty(url)) {
                    mIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                }

            } else if (Im.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final boolean isEmail = Email.CONTENT_ITEM_TYPE.equals(
                        getAsString(cursor, Data.MIMETYPE));
                if (isEmail || isProtocolValid(cursor)) {
                    final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK :
                            getAsInt(cursor, Im.PROTOCOL);

                    if (isEmail) {
                        // Use Google Talk string when using Email, and clear data
                        // Uri so we don't try saving Email as primary.
                        mHeader = context.getText(R.string.chat_gtalk);
                        mDataUri = null;
                    }

                    String host = getAsString(cursor, Im.CUSTOM_PROTOCOL);
                    String data = getAsString(cursor, isEmail ? Email.DATA : Im.DATA);
                    if (protocol != Im.PROTOCOL_CUSTOM) {
                        // Try bringing in a well-known host for specific protocols
                        host = ContactsUtils.lookupProviderNameFromId(protocol);
                    }

                    if (!TextUtils.isEmpty(host) && !TextUtils.isEmpty(data)) {
                        final String authority = host.toLowerCase();
                        final Uri imUri = new Uri.Builder().scheme(Constants.SCHEME_IMTO).authority(
                                authority).appendPath(data).build();
                        mIntent = new Intent(Intent.ACTION_SENDTO, imUri);
                    }
                }
            }

            if (mIntent == null) {
                // Otherwise fall back to default VIEW action
                mIntent = new Intent(Intent.ACTION_VIEW, mDataUri);
            }

            // Always launch as new task, since we're like a launcher
            mIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }

        private boolean isProtocolValid(Cursor cursor) {
            final int columnIndex = cursor.getColumnIndex(Im.PROTOCOL);
            if (cursor.isNull(columnIndex)) {
                return false;
            }
            try {
                Integer.valueOf(cursor.getString(columnIndex));
            } catch (NumberFormatException e) {
                return false;
            }
            return true;
        }

        /** {@inheritDoc} */
        public CharSequence getHeader() {
            return mHeader;
        }

        /** {@inheritDoc} */
        public CharSequence getBody() {
            return mBody;
        }

        /** {@inheritDoc} */
        public String getMimeType() {
            return mMimeType;
        }

        /** {@inheritDoc} */
        public Uri getDataUri() {
            return mDataUri;
        }

        /** {@inheritDoc} */
        public Boolean isPrimary() {
            return mIsPrimary;
        }

        /** {@inheritDoc} */
        public Drawable getFallbackIcon() {
            // Bail early if no valid resources
            final String resPackageName = mKind.resPackageName;
            if (resPackageName == null) return null;

            final PackageManager pm = mContext.getPackageManager();
            if (mAlternate && mKind.iconAltRes != -1) {
                return pm.getDrawable(resPackageName, mKind.iconAltRes, null);
            } else if (mKind.iconRes != -1) {
                return pm.getDrawable(resPackageName, mKind.iconRes, null);
            } else {
                return null;
            }
        }

        /** {@inheritDoc} */
        public Intent getIntent() {
            return mIntent;
        }

        /** {@inheritDoc} */
        public boolean collapseWith(Action other) {
            if (!shouldCollapseWith(other)) {
                return false;
            }
            return true;
        }

        /** {@inheritDoc} */
        public boolean shouldCollapseWith(Action t) {
            if (t == null) {
                return false;
            }
            if (!(t instanceof DataAction)) {
                Log.e(TAG, "t must be DataAction");
                return false;
            }
            DataAction other = (DataAction)t;
            if (!ContactsUtils.areObjectsEqual(mKind, other.mKind)) {
                return false;
            }
            if (!ContactsUtils.shouldCollapse(mContext, mMimeType, mBody, other.mMimeType,
                    other.mBody)) {
                return false;
            }
            if (!TextUtils.equals(mMimeType, other.mMimeType)
                    || !ContactsUtils.areIntentActionEqual(mIntent, other.mIntent)
                    ) {
                return false;
            }
            return true;
        }
    }

    /**
     * Specific action that launches the profile card.
     */
    private static class ProfileAction implements Action {
        private final Context mContext;
        private final Uri mLookupUri;

        public ProfileAction(Context context, Uri lookupUri) {
            mContext = context;
            mLookupUri = lookupUri;
        }

        /** {@inheritDoc} */
        public CharSequence getHeader() {
            return null;
        }

        /** {@inheritDoc} */
        public CharSequence getBody() {
            return null;
        }

        /** {@inheritDoc} */
        public String getMimeType() {
            return Contacts.CONTENT_ITEM_TYPE;
        }

        /** {@inheritDoc} */
        public Drawable getFallbackIcon() {
            return mContext.getResources().getDrawable(R.drawable.ic_contacts_details);
        }

        /** {@inheritDoc} */
        public Intent getIntent() {
            final Intent intent = new Intent(Intent.ACTION_VIEW, mLookupUri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            return intent;
        }

        /** {@inheritDoc} */
        public Boolean isPrimary() {
            return null;
        }

        /** {@inheritDoc} */
        public Uri getDataUri() {
            return null;
        }

        /** {@inheritDoc} */
        public boolean collapseWith(Action t) {
            return false; // Never dup.
        }

        /** {@inheritDoc} */
        public boolean shouldCollapseWith(Action t) {
            return false; // Never dup.
        }
    }

    /**
     * Internally hold a cache of scaled icons based on {@link PackageManager}
     * queries, keyed internally on MIME-type.
     */
    private static class ResolveCache {
        private PackageManager mPackageManager;

        /**
         * Cached entry holding the best {@link ResolveInfo} for a specific
         * MIME-type, along with a {@link SoftReference} to its icon.
         */
        private static class Entry {
            public ResolveInfo bestResolve;
            public SoftReference<Drawable> icon;
        }

        private HashMap<String, Entry> mCache = new HashMap<String, Entry>();

        public ResolveCache(Context context) {
            mPackageManager = context.getPackageManager();
        }

        /**
         * Get the {@link Entry} best associated with the given {@link Action},
         * or create and populate a new one if it doesn't exist.
         */
        protected Entry getEntry(Action action) {
            final String mimeType = action.getMimeType();
            Entry entry = mCache.get(mimeType);
            if (entry != null) return entry;
            entry = new Entry();

            final Intent intent = action.getIntent();
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
                    entry.icon = new SoftReference<Drawable>(icon);
                }
            }

            mCache.put(mimeType, entry);
            return entry;
        }

        /**
         * Best {@link ResolveInfo} when multiple found. Ties are broken by
         * selecting first from the {QuickContactWindow#sPreferResolve} list of
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
                final boolean isPrefer = QuickContactWindow.sPreferResolve
                        .contains(info.activityInfo.applicationInfo.packageName);



                if (isPrefer) return info;
                if (isSystem && firstSystem != null) firstSystem = info;
            }

            // Return first system found, otherwise first from list
            return firstSystem != null ? firstSystem : matches.get(0);
        }

        /**
         * Check {@link PackageManager} to see if any apps offer to handle the
         * given {@link Action}.
         */
        public boolean hasResolve(Action action) {
            return getEntry(action).bestResolve != null;
        }

        /**
         * Find the best description for the given {@link Action}, usually used
         * for accessibility purposes.
         */
        public CharSequence getDescription(Action action) {
            final CharSequence actionHeader = action.getHeader();
            final ResolveInfo info = getEntry(action).bestResolve;
            if (!TextUtils.isEmpty(actionHeader)) {
                return actionHeader;
            } else if (info != null) {
                return info.loadLabel(mPackageManager);
            } else {
                return null;
            }
        }

        /**
         * Return the best icon for the given {@link Action}, which is usually
         * based on the {@link ResolveInfo} found through a
         * {@link PackageManager} query.
         */
        public Drawable getIcon(Action action) {
            final SoftReference<Drawable> iconRef = getEntry(action).icon;
            return (iconRef == null) ? null : iconRef.get();
        }

        public void clear() {
            mCache.clear();
        }
    }

    /**
     * Provide a strongly-typed {@link LinkedList} that holds a list of
     * {@link Action} objects.
     */
    private class ActionList extends ArrayList<Action> {
    }

    /**
     * Provide a simple way of collecting one or more {@link Action} objects
     * under a MIME-type key.
     */
    private class ActionMap extends HashMap<String, ActionList> {
        private void collect(String mimeType, Action info) {
            // Create list for this MIME-type when needed
            ActionList collectList = get(mimeType);
            if (collectList == null) {
                collectList = new ActionList();
                put(mimeType, collectList);
            }
            collectList.add(info);
        }
    }

    /**
     * Check if the given MIME-type appears in the list of excluded MIME-types
     * that the most-recent caller requested.
     */
    private boolean isMimeExcluded(String mimeType) {
        if (mExcludeMimes == null) return false;
        for (String excludedMime : mExcludeMimes) {
            if (TextUtils.equals(excludedMime, mimeType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle the result from the {@link #TOKEN_DATA} query.
     */
    private void handleData(Cursor cursor) {
        if (cursor == null) return;

        if (!isMimeExcluded(Contacts.CONTENT_ITEM_TYPE)) {
            // Add the profile shortcut action
            final Action action = new ProfileAction(mContext, mLookupUri);
            mActions.collect(Contacts.CONTENT_ITEM_TYPE, action);
        }

        final DataStatus status = new DataStatus();
        final Sources sources = Sources.getInstance(mContext);
        final ImageView photoView = (ImageView)mHeader.findViewById(R.id.photo);

        Bitmap photoBitmap = null;
        while (cursor.moveToNext()) {
            final long dataId = cursor.getLong(DataQuery._ID);
            final String accountType = cursor.getString(DataQuery.ACCOUNT_TYPE);
            final String mimeType = cursor.getString(DataQuery.MIMETYPE);

            // Handle any social status updates from this row
            status.possibleUpdate(cursor);

            // Skip this data item if MIME-type excluded
            if (isMimeExcluded(mimeType)) continue;

            // Handle photos included as data row
            if (Photo.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final int colPhoto = cursor.getColumnIndex(Photo.PHOTO);
                final byte[] photoBlob = cursor.getBlob(colPhoto);
                if (photoBlob != null) {
                    photoBitmap = BitmapFactory.decodeByteArray(photoBlob, 0, photoBlob.length);
                }
                continue;
            }

            final DataKind kind = sources.getKindOrFallback(accountType, mimeType, mContext,
                    ContactsSource.LEVEL_MIMETYPES);

            if (kind != null) {
                // Build an action for this data entry, find a mapping to a UI
                // element, build its summary from the cursor, and collect it
                // along with all others of this MIME-type.
                final Action action = new DataAction(mContext, mimeType, kind, dataId, cursor);
                considerAdd(action, mimeType);
            }

            // If phone number, also insert as text message action
            if (Phone.CONTENT_ITEM_TYPE.equals(mimeType) && kind != null) {
                final Action action = new DataAction(mContext, Constants.MIME_SMS_ADDRESS,
                        kind, dataId, cursor);
                considerAdd(action, Constants.MIME_SMS_ADDRESS);
            }

            // Handle Email rows with presence data as Im entry
            final boolean hasPresence = !cursor.isNull(DataQuery.PRESENCE);
            if (hasPresence && Email.CONTENT_ITEM_TYPE.equals(mimeType)) {
                final DataKind imKind = sources.getKindOrFallback(accountType,
                        Im.CONTENT_ITEM_TYPE, mContext, ContactsSource.LEVEL_MIMETYPES);
                if (imKind != null) {
                    final Action action = new DataAction(mContext, Im.CONTENT_ITEM_TYPE, imKind,
                            dataId, cursor);
                    considerAdd(action, Im.CONTENT_ITEM_TYPE);
                }
            }
        }

        if (cursor.moveToLast()) {
            // Read contact information from last data row
            final String name = cursor.getString(DataQuery.DISPLAY_NAME);
            final int presence = cursor.getInt(DataQuery.CONTACT_PRESENCE);
            final Drawable statusIcon = ContactPresenceIconUtil.getPresenceIcon(mContext, presence);

            setHeaderText(R.id.name, name);
            setHeaderImage(R.id.presence, statusIcon);
        }

        if (photoView != null) {
            // Place photo when discovered in data, otherwise hide
            photoView.setVisibility(photoBitmap != null ? View.VISIBLE : View.GONE);
            photoView.setImageBitmap(photoBitmap);
        }

        mHasValidSocial = status.isValid();
        if (mHasValidSocial && mMode != QuickContact.MODE_SMALL) {
            // Update status when valid was found
            setHeaderText(R.id.status, status.getStatus());
            setHeaderText(R.id.timestamp, status.getTimestampLabel(mContext));
        }

        // Turn our list of actions into UI elements

        // Index where we start adding child views.
        int index = mTrack.getChildCount() - 1;

        // All the mime-types to add.
        final Set<String> containedTypes = new HashSet<String>(mActions.keySet());

        boolean hasData = false;

        // First, add PRECEDING_MIMETYPES, which are most common.
        for (String mimeType : PRECEDING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                hasData = true;
                mTrack.addView(inflateAction(mimeType), index++);
                containedTypes.remove(mimeType);
            }
        }

        // Keep the current index to append non PRECEDING/FOLLOWING items.
        final int indexAfterPreceding = index;

        // Then, add FOLLOWING_MIMETYPES, which are least common.
        for (String mimeType : FOLLOWING_MIMETYPES) {
            if (containedTypes.contains(mimeType)) {
                hasData = true;
                mTrack.addView(inflateAction(mimeType), index++);
                containedTypes.remove(mimeType);
            }
        }

        // Go back to just after PRECEDING_MIMETYPES, and append the rest.
        index = indexAfterPreceding;
        final String[] remainingTypes = containedTypes.toArray(new String[containedTypes.size()]);
        if (remainingTypes.length > 0) hasData = true;
        Arrays.sort(remainingTypes);
        for (String mimeType : remainingTypes) {
            mTrack.addView(inflateAction(mimeType), index++);
        }

        // When there is no data to display, add a TextView to show the user there's no data
        if (!hasData) {
            View view = mInflater.inflate(R.layout.quickcontact_item_nodata, mTrack, false);
            mTrack.addView(view, index++);
        }
    }

    /**
     * Consider adding the given {@link Action}, which will only happen if
     * {@link PackageManager} finds an application to handle
     * {@link Action#getIntent()}.
     */
    private void considerAdd(Action action, String mimeType) {
        if (mResolveCache.hasResolve(action)) {
            mActions.collect(mimeType, action);
        }
    }

    /**
     * Obtain a new {@link CheckableImageView} for a new chiclet, either by
     * recycling one from {@link #mActionPool}, or by inflating a new one. When
     * finished, use {@link #releaseView(View)} to return back into the pool for
     * later recycling.
     */
    private synchronized View obtainView() {
        View view = mActionPool.poll();
        if (view == null || QuickContactActivity.FORCE_CREATE) {
            view = mInflater.inflate(R.layout.quickcontact_item, mTrack, false);
        }
        return view;
    }

    /**
     * Return the given {@link CheckableImageView} into our internal pool for
     * possible recycling during another pass.
     */
    private synchronized void releaseView(View view) {
        // Only add CheckableImageViews
        if (!(view instanceof CheckableImageView)) {
            return;
        }
        mActionPool.offer(view);
        mActionRecycled++;
    }

    /**
     * Inflate the in-track view for the action of the given MIME-type, collapsing duplicate values.
     * Will use the icon provided by the {@link DataKind}.
     */
    private View inflateAction(String mimeType) {
        final CheckableImageView view = (CheckableImageView)obtainView();
        boolean isActionSet = false;

        // Add direct intent if single child, otherwise flag for multiple
        ActionList children = mActions.get(mimeType);
        if (children.size() > 1) {
            Collapser.collapseList(children);
        }
        Action firstInfo = children.get(0);
        if (children.size() == 1) {
            view.setTag(firstInfo);
        } else {
            for (Action action : children) {
                if (action.isPrimary()) {
                    view.setTag(action);
                    isActionSet = true;
                    break;
                }
            }
            if (!isActionSet) {
                view.setTag(children);
            }
        }

        // Set icon and listen for clicks
        final CharSequence descrip = mResolveCache.getDescription(firstInfo);
        final Drawable icon = mResolveCache.getIcon(firstInfo);
        view.setChecked(false);
        view.setContentDescription(descrip);
        view.setImageDrawable(icon);
        view.setOnClickListener(this);
        return view;
    }

    /** {@inheritDoc} */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // Pass list item clicks along so that Intents are handled uniformly
        onClick(view);
    }

    /**
     * Flag indicating if {@link #mArrowDown} was visible during the last call
     * to {@link #setResolveVisible(boolean, CheckableImageView)}. Used to
     * decide during a later call if the arrow should be restored.
     */
    private boolean mWasDownArrow = false;

    /**
     * Helper for showing and hiding {@link #mFooterDisambig}, which will
     * correctly manage {@link #mArrowDown} as needed.
     */
    private void setResolveVisible(boolean visible, CheckableImageView actionView) {
        // Show or hide the resolve list if needed
        boolean visibleNow = mFooterDisambig.getVisibility() == View.VISIBLE;

        if (mLastAction != null) mLastAction.setChecked(false);
        if (actionView != null) actionView.setChecked(true);
        mLastAction = actionView;

        // Bail early if already in desired state
        if (visible == visibleNow) return;

        mFooter.setVisibility(visible ? View.GONE : View.VISIBLE);
        mFooterDisambig.setVisibility(visible ? View.VISIBLE : View.GONE);

        if (visible) {
            // If showing list, then hide and save state of down arrow
            mWasDownArrow = mWasDownArrow || (mArrowDown.getVisibility() == View.VISIBLE);
            mArrowDown.setVisibility(View.INVISIBLE);
        } else {
            // If hiding list, restore any down arrow state
            mArrowDown.setVisibility(mWasDownArrow ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /** {@inheritDoc} */
    public void onClick(View view) {
        final boolean isActionView = (view instanceof CheckableImageView);
        final CheckableImageView actionView = isActionView ? (CheckableImageView)view : null;
        final Object tag = view.getTag();
        if (tag instanceof Action) {
            // Incoming tag is concrete intent, so try launching
            final Action action = (Action)tag;
            final boolean makePrimary = mMakePrimary;

            try {
                mContext.startActivity(action.getIntent());
            } catch (ActivityNotFoundException e) {
                Toast.makeText(mContext, R.string.quickcontact_missing_app, Toast.LENGTH_SHORT)
                        .show();
            }

            // Hide the resolution list, if present
            setResolveVisible(false, null);
            this.dismiss();

            if (makePrimary) {
                ContentValues values = new ContentValues(1);
                values.put(Data.IS_SUPER_PRIMARY, 1);
                final Uri dataUri = action.getDataUri();
                if (dataUri != null) {
                    mContext.getContentResolver().update(dataUri, values, null, null);
                }
            }
        } else if (tag instanceof ActionList) {
            // Incoming tag is a MIME-type, so show resolution list
            final ActionList children = (ActionList)tag;

            // Show resolution list and set adapter
            setResolveVisible(true, actionView);

            mResolveList.setOnItemClickListener(this);
            mResolveList.setAdapter(new BaseAdapter() {
                public int getCount() {
                    return children.size();
                }

                public Object getItem(int position) {
                    return children.get(position);
                }

                public long getItemId(int position) {
                    return position;
                }

                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) {
                        convertView = mInflater.inflate(
                                R.layout.quickcontact_resolve_item, parent, false);
                    }

                    // Set action title based on summary value
                    final Action action = (Action)getItem(position);

                    TextView text1 = (TextView)convertView.findViewById(android.R.id.text1);
                    TextView text2 = (TextView)convertView.findViewById(android.R.id.text2);

                    text1.setText(action.getHeader());
                    text2.setText(action.getBody());

                    convertView.setTag(action);
                    return convertView;
                }
            });

            // Make sure we resize to make room for ListView
            mDecor.forceLayout();
            mDecor.invalidate();

        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        mMakePrimary = isChecked;
    }

    private void onBackPressed() {
        // Back key will first dismiss any expanded resolve list, otherwise
        // it will close the entire dialog.
        if (mFooterDisambig.getVisibility() == View.VISIBLE) {
            setResolveVisible(false, null);
            mDecor.forceLayout();
            mDecor.invalidate();
        } else {
            dismiss();
        }
    }

    /** {@inheritDoc} */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mWindow.superDispatchKeyEvent(event)) {
            return true;
        }
        return event.dispatch(this, mDecor != null
                ? mDecor.getKeyDispatcherState() : null, this);
    }

    /** {@inheritDoc} */
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking();
            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking()
                && !event.isCanceled()) {
            onBackPressed();
            return true;
        }

        return false;
    }

    /** {@inheritDoc} */
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        // TODO: make this window accessible
        return false;
    }

    /**
     * Detect if the given {@link MotionEvent} is outside the boundaries of this
     * window, which usually means we should dismiss.
     */
    protected void detectEventOutside(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && mDecor != null) {
            // Only try detecting outside events on down-press
            mDecor.getHitRect(mRect);
            mRect.top = mRect.top + mShadowTouch;
            mRect.bottom = mRect.bottom - mShadowTouch;
            final int x = (int)event.getX();
            final int y = (int)event.getY();
            if (!mRect.contains(x, y)) {
                event.setAction(MotionEvent.ACTION_OUTSIDE);
            }
        }
    }

    /** {@inheritDoc} */
    public boolean dispatchTouchEvent(MotionEvent event) {
        detectEventOutside(event);
        if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            dismiss();
            return true;
        } else {
            return mWindow.superDispatchTouchEvent(event);
        }
    }

    /** {@inheritDoc} */
    public boolean dispatchTrackballEvent(MotionEvent event) {
        return mWindow.superDispatchTrackballEvent(event);
    }

    /** {@inheritDoc} */
    public void onContentChanged() {
    }

    /** {@inheritDoc} */
    public boolean onCreatePanelMenu(int featureId, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    public View onCreatePanelView(int featureId) {
        return null;
    }

    /** {@inheritDoc} */
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean onMenuOpened(int featureId, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    public void onPanelClosed(int featureId, Menu menu) {
    }

    /** {@inheritDoc} */
    public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return false;
    }

    /** {@inheritDoc} */
    public boolean onSearchRequested() {
        return false;
    }

    /** {@inheritDoc} */
    public void onWindowAttributesChanged(android.view.WindowManager.LayoutParams attrs) {
        if (mDecor != null) {
            mWindowManager.updateViewLayout(mDecor, attrs);
        }
    }

    /** {@inheritDoc} */
    public void onWindowFocusChanged(boolean hasFocus) {
    }

    /** {@inheritDoc} */
    public void onAttachedToWindow() {
        // No actions
    }

    /** {@inheritDoc} */
    public void onDetachedFromWindow() {
        // No actions
    }

    private interface DataQuery {
        final String[] PROJECTION = new String[] {
                Data._ID,

                RawContacts.ACCOUNT_TYPE,
                Contacts.STARRED,
                Contacts.DISPLAY_NAME,
                Contacts.CONTACT_PRESENCE,

                Data.STATUS,
                Data.STATUS_RES_PACKAGE,
                Data.STATUS_ICON,
                Data.STATUS_LABEL,
                Data.STATUS_TIMESTAMP,
                Data.PRESENCE,

                Data.RES_PACKAGE,
                Data.MIMETYPE,
                Data.IS_PRIMARY,
                Data.IS_SUPER_PRIMARY,
                Data.RAW_CONTACT_ID,

                Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5,
                Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10, Data.DATA11,
                Data.DATA12, Data.DATA13, Data.DATA14, Data.DATA15,
        };

        final int _ID = 0;

        final int ACCOUNT_TYPE = 1;
        final int STARRED = 2;
        final int DISPLAY_NAME = 3;
        final int CONTACT_PRESENCE = 4;

        final int STATUS = 5;
        final int STATUS_RES_PACKAGE = 6;
        final int STATUS_ICON = 7;
        final int STATUS_LABEL = 8;
        final int STATUS_TIMESTAMP = 9;
        final int PRESENCE = 10;

        final int RES_PACKAGE = 11;
        final int MIMETYPE = 12;
        final int IS_PRIMARY = 13;
        final int IS_SUPER_PRIMARY = 14;
    }
}
