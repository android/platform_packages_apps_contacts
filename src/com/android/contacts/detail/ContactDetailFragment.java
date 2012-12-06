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

package com.android.contacts.detail;

import android.app.Activity;
import android.app.Fragment;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.ParseException;
import android.net.Uri;
import android.net.WebAddress;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.CalendarContract;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.DisplayNameSources;
import android.provider.ContactsContract.StatusUpdates;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;
import android.widget.ListView;
import android.widget.TextView;

import com.android.contacts.ContactSaveService;
import com.android.contacts.R;
import com.android.contacts.TypePrecedence;
import com.android.contacts.activities.ContactDetailActivity.FragmentKeyListener;
import com.android.contacts.common.CallUtil;
import com.android.contacts.common.ClipboardUtils;
import com.android.contacts.common.Collapser;
import com.android.contacts.common.ContactsUtils;
import com.android.contacts.common.GroupMetaData;
import com.android.contacts.common.Collapser.Collapsible;
import com.android.contacts.common.ContactPresenceIconUtil;
import com.android.contacts.common.GeoUtil;
import com.android.contacts.common.MoreContactUtils;
import com.android.contacts.common.editor.SelectAccountDialogFragment;
import com.android.contacts.common.model.AccountTypeManager;
import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.common.model.account.AccountType;
import com.android.contacts.common.model.account.AccountType.EditType;
import com.android.contacts.common.model.account.AccountWithDataSet;
import com.android.contacts.common.model.dataitem.DataKind;
import com.android.contacts.common.util.AccountsListAdapter.AccountListFilter;
import com.android.contacts.common.util.ContactDisplayUtils;
import com.android.contacts.common.util.DataStatus;
import com.android.contacts.common.util.DateUtils;
import com.android.contacts.common.model.Contact;
import com.android.contacts.common.model.RawContact;
import com.android.contacts.common.model.RawContactDelta;
import com.android.contacts.common.model.RawContactDeltaList;
import com.android.contacts.common.model.RawContactModifier;
import com.android.contacts.common.model.dataitem.DataItem;
import com.android.contacts.common.model.dataitem.EmailDataItem;
import com.android.contacts.common.model.dataitem.EventDataItem;
import com.android.contacts.common.model.dataitem.GroupMembershipDataItem;
import com.android.contacts.common.model.dataitem.ImDataItem;
import com.android.contacts.common.model.dataitem.NicknameDataItem;
import com.android.contacts.common.model.dataitem.NoteDataItem;
import com.android.contacts.common.model.dataitem.OrganizationDataItem;
import com.android.contacts.common.model.dataitem.PhoneDataItem;
import com.android.contacts.common.model.dataitem.RelationDataItem;
import com.android.contacts.common.model.dataitem.SipAddressDataItem;
import com.android.contacts.common.model.dataitem.StructuredNameDataItem;
import com.android.contacts.common.model.dataitem.StructuredPostalDataItem;
import com.android.contacts.common.model.dataitem.WebsiteDataItem;
import com.android.contacts.util.PhoneCapabilityTester;
import com.android.contacts.util.StructuredPostalUtils;
import com.android.contacts.util.UiClosables;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.contacts.common.BrcmIccUtils;
import android.os.SystemProperties;

public class ContactDetailFragment extends Fragment implements FragmentKeyListener,
        SelectAccountDialogFragment.Listener, OnItemClickListener {

    private static final String TAG = "ContactDetailFragment";

    private static final int TEXT_DIRECTION_UNDEFINED = -1;

    private interface ContextMenuIds {
        static final int COPY_TEXT = 0;
        static final int CLEAR_DEFAULT = 1;
        static final int SET_DEFAULT = 2;
    }

    private static final String KEY_CONTACT_URI = "contactUri";
    private static final String KEY_LIST_STATE = "liststate";

    private Context mContext;
    private View mView;
    private OnScrollListener mVerticalScrollListener;
    private Uri mLookupUri;
    private Listener mListener;

    private Contact mContactData;
    private ViewGroup mStaticPhotoContainer;
    private View mPhotoTouchOverlay;
    private ListView mListView;
    private ViewAdapter mAdapter;
    private Uri mPrimaryPhoneUri = null;
    private ViewEntryDimensions mViewEntryDimensions;

    private final ContactDetailPhotoSetter mPhotoSetter = new ContactDetailPhotoSetter();

    private Button mQuickFixButton;
    private QuickFix mQuickFix;
    private boolean mContactHasSocialUpdates;
    private boolean mShowStaticPhoto = true;

    private final QuickFix[] mPotentialQuickFixes = new QuickFix[] {
            new MakeLocalCopyQuickFix(),
            new AddToMyContactsQuickFix()
    };

    /**
     * The view shown if the detail list is empty.
     * We set this to the list view when first bind the adapter, so that it won't be shown while
     * we're loading data.
     */
    private View mEmptyView;

    /**
     * Saved state of the {@link ListView}. This must be saved and applied to the {@ListView} only
     * when the adapter has been populated again.
     */
    private Parcelable mListState;

    /**
     * Lists of specific types of entries to be shown in contact details.
     */
    private ArrayList<DetailViewEntry> mPhoneEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mSmsEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mEmailEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mPostalEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mImEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mNicknameEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mGroupEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mRelationEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mNoteEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mWebsiteEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mSipEntries = new ArrayList<DetailViewEntry>();
    private ArrayList<DetailViewEntry> mEventEntries = new ArrayList<DetailViewEntry>();
    private final Map<AccountType, List<DetailViewEntry>> mOtherEntriesMap =
            new HashMap<AccountType, List<DetailViewEntry>>();
    private ArrayList<ViewEntry> mAllEntries = new ArrayList<ViewEntry>();
    private LayoutInflater mInflater;

    private boolean mIsUniqueNumber;
    private boolean mIsUniqueEmail;

    private ListPopupWindow mPopup;

    /**
     * This is to forward touch events to the list view to enable users to scroll the list view
     * from the blank area underneath the static photo when the layout with static photo is used.
     */
    private OnTouchListener mForwardTouchToListView = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mListView != null) {
                mListView.dispatchTouchEvent(event);
                return true;
            }
            return false;
        }
    };

    /**
     * This is to forward drag events to the list view to enable users to scroll the list view
     * from the blank area underneath the static photo when the layout with static photo is used.
     */
    private OnDragListener mForwardDragToListView = new OnDragListener() {
        @Override
        public boolean onDrag(View v, DragEvent event) {
            if (mListView != null) {
                mListView.dispatchDragEvent(event);
                return true;
            }
            return false;
        }
    };

    public ContactDetailFragment() {
        // Explicit constructor for inflation
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mLookupUri = savedInstanceState.getParcelable(KEY_CONTACT_URI);
            mListState = savedInstanceState.getParcelable(KEY_LIST_STATE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_CONTACT_URI, mLookupUri);
        if (mListView != null) {
            outState.putParcelable(KEY_LIST_STATE, mListView.onSaveInstanceState());
        }
    }

    @Override
    public void onPause() {
        dismissPopupIfShown();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mViewEntryDimensions = new ViewEntryDimensions(mContext.getResources());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mView = inflater.inflate(R.layout.contact_detail_fragment, container, false);
        // Set the touch and drag listener to forward the event to the mListView so that
        // vertical scrolling can happen from outside of the list view.
        mView.setOnTouchListener(mForwardTouchToListView);
        mView.setOnDragListener(mForwardDragToListView);

        mInflater = inflater;

        mStaticPhotoContainer = (ViewGroup) mView.findViewById(R.id.static_photo_container);
        mPhotoTouchOverlay = mView.findViewById(R.id.photo_touch_intercept_overlay);

        mListView = (ListView) mView.findViewById(android.R.id.list);
        mListView.setOnItemClickListener(this);
        mListView.setItemsCanFocus(true);
        mListView.setOnScrollListener(mVerticalScrollListener);

        // Don't set it to mListView yet.  We do so later when we bind the adapter.
        mEmptyView = mView.findViewById(android.R.id.empty);

        mQuickFixButton = (Button) mView.findViewById(R.id.contact_quick_fix);
        mQuickFixButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mQuickFix != null) {
                    mQuickFix.execute();
                }
            }
        });

        mView.setVisibility(View.INVISIBLE);

        if (mContactData != null) {
            bindData();
        }

        return mView;
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    protected Context getContext() {
        return mContext;
    }

    protected Listener getListener() {
        return mListener;
    }

    protected Contact getContactData() {
        return mContactData;
    }

    public void setVerticalScrollListener(OnScrollListener listener) {
        mVerticalScrollListener = listener;
    }

    public Uri getUri() {
        return mLookupUri;
    }

    /**
     * Sets whether the static contact photo (that is not in a scrolling region), should be shown
     * or not.
     */
    public void setShowStaticPhoto(boolean showPhoto) {
        mShowStaticPhoto = showPhoto;
    }

    /**
     * Shows the contact detail with a message indicating there are no contact details.
     */
    public void showEmptyState() {
        setData(null, null);
    }

    public void setData(Uri lookupUri, Contact result) {
        mLookupUri = lookupUri;
        mContactData = result;
        bindData();
    }

    /**
     * Reset the list adapter in this {@link Fragment} to get rid of any saved scroll position
     * from a previous contact.
     */
    public void resetAdapter() {
        if (mListView != null) {
            mListView.setAdapter(mAdapter);
        }
    }

    /**
     * Returns the top coordinate of the first item in the {@link ListView}. If the first item
     * in the {@link ListView} is not visible or there are no children in the list, then return
     * Integer.MIN_VALUE. Note that the returned value will be <= 0 because the first item in the
     * list cannot have a positive offset.
     */
    public int getFirstListItemOffset() {
        return ContactDetailDisplayUtils.getFirstListItemOffset(mListView);
    }

    /**
     * Tries to scroll the first item to the given offset (this can be a no-op if the list is
     * already in the correct position).
     * @param offset which should be <= 0
     */
    public void requestToMoveToOffset(int offset) {
        ContactDetailDisplayUtils.requestToMoveToOffset(mListView, offset);
    }

    protected void bindData() {
        if (mView == null) {
            return;
        }

        if (isAdded()) {
            getActivity().invalidateOptionsMenu();
        }

        if (mContactData == null) {
            mView.setVisibility(View.INVISIBLE);
            if (mStaticPhotoContainer != null) {
                mStaticPhotoContainer.setVisibility(View.GONE);
            }
            mAllEntries.clear();
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
            return;
        }

        // Setup the photo if applicable
        if (mStaticPhotoContainer != null) {
            // The presence of a static photo container is not sufficient to determine whether or
            // not we should show the photo. Check the mShowStaticPhoto flag which can be set by an
            // outside class depending on screen size, layout, and whether the contact has social
            // updates or not.
            if (mShowStaticPhoto) {
                mStaticPhotoContainer.setVisibility(View.VISIBLE);
                final ImageView photoView = (ImageView) mStaticPhotoContainer.findViewById(
                        R.id.photo);
                final boolean expandPhotoOnClick = mContactData.getPhotoUri() != null;
                final OnClickListener listener = mPhotoSetter.setupContactPhotoForClick(
                        mContext, mContactData, photoView, expandPhotoOnClick);
                if (mPhotoTouchOverlay != null) {
                    mPhotoTouchOverlay.setVisibility(View.VISIBLE);
                    if (expandPhotoOnClick || mContactData.isWritableContact(mContext)) {
                        mPhotoTouchOverlay.setOnClickListener(listener);
                    } else {
                        mPhotoTouchOverlay.setClickable(false);
                    }
                }
            } else {
                mStaticPhotoContainer.setVisibility(View.GONE);
            }
        }

        // Build up the contact entries
        buildEntries();

        // Collapse similar data items for select {@link DataKind}s.
        Collapser.collapseList(mPhoneEntries);
        Collapser.collapseList(mSmsEntries);
        Collapser.collapseList(mEmailEntries);
        Collapser.collapseList(mPostalEntries);
        Collapser.collapseList(mImEntries);
        Collapser.collapseList(mEventEntries);
        Collapser.collapseList(mWebsiteEntries);

        mIsUniqueNumber = mPhoneEntries.size() == 1;
        mIsUniqueEmail = mEmailEntries.size() == 1;

        // Make one aggregated list of all entries for display to the user.
        setupFlattenedList();

        if (mAdapter == null) {
            mAdapter = new ViewAdapter();
            mListView.setAdapter(mAdapter);
        }

        // Restore {@link ListView} state if applicable because the adapter is now populated.
        if (mListState != null) {
            mListView.onRestoreInstanceState(mListState);
            mListState = null;
        }

        mAdapter.notifyDataSetChanged();

        mListView.setEmptyView(mEmptyView);

        configureQuickFix();

        mView.setVisibility(View.VISIBLE);
    }

    /*
     * Sets {@link #mQuickFix} to a useful action and configures the visibility of
     * {@link #mQuickFixButton}
     */
    private void configureQuickFix() {
        mQuickFix = null;

        for (QuickFix fix : mPotentialQuickFixes) {
            if (fix.isApplicable()) {
                mQuickFix = fix;
                break;
            }
        }

        // Configure the button
        if (mQuickFix == null) {
            mQuickFixButton.setVisibility(View.GONE);
        } else {
            mQuickFixButton.setVisibility(View.VISIBLE);
            mQuickFixButton.setText(mQuickFix.getTitle());
        }
    }

    /** @return default group id or -1 if no group or several groups are marked as default */
    private long getDefaultGroupId(List<GroupMetaData> groups) {
        long defaultGroupId = -1;
        for (GroupMetaData group : groups) {
            if (group.isDefaultGroup()) {
                // two default groups? return neither
                if (defaultGroupId != -1) return -1;
                defaultGroupId = group.getGroupId();
            }
        }
        return defaultGroupId;
    }

    /**
     * Build up the entries to display on the screen.
     */
    private final void buildEntries() {
        final boolean hasPhone = PhoneCapabilityTester.isPhone(mContext);
        final ComponentName smsComponent = PhoneCapabilityTester.getSmsComponent(getContext());
        final boolean hasSms = (smsComponent != null);
        final boolean hasSip = PhoneCapabilityTester.isSipPhone(mContext);

        // Clear out the old entries
        mAllEntries.clear();

        mPrimaryPhoneUri = null;

        // Build up method entries
        if (mContactData == null) {
            return;
        }

        ArrayList<String> groups = new ArrayList<String>();
        for (RawContact rawContact: mContactData.getRawContacts()) {
            final long rawContactId = rawContact.getId();
            final AccountType accountType = rawContact.getAccountType(mContext);
            for (DataItem dataItem : rawContact.getDataItems()) {
                dataItem.setRawContactId(rawContactId);

                if (dataItem.getMimeType() == null) continue;

                if (dataItem instanceof GroupMembershipDataItem) {
                    GroupMembershipDataItem groupMembership =
                            (GroupMembershipDataItem) dataItem;
                    Long groupId = groupMembership.getGroupRowId();
                    if (groupId != null) {
                        handleGroupMembership(groups, mContactData.getGroupMetaData(), groupId);
                    }
                    continue;
                }

                final DataKind kind = AccountTypeManager.getInstance(mContext)
                        .getKindOrFallback(accountType, dataItem.getMimeType());
                if (kind == null) continue;

                final DetailViewEntry entry = DetailViewEntry.fromValues(mContext, dataItem,
                        mContactData.isDirectoryEntry(), mContactData.getDirectoryId(), kind);
                entry.maxLines = kind.maxLinesForDisplay;

                final boolean hasData = !TextUtils.isEmpty(entry.data);
                final boolean isSuperPrimary = dataItem.isSuperPrimary();

                if (dataItem instanceof StructuredNameDataItem) {
                    // Always ignore the name. It is shown in the header if set
                } else if (dataItem instanceof PhoneDataItem && hasData) {
                    PhoneDataItem phone = (PhoneDataItem) dataItem;
                    // Build phone entries
                    entry.data = phone.getFormattedPhoneNumber();
                    final Intent phoneIntent = hasPhone ?
                            CallUtil.getCallIntent(entry.data) : null;
                    Intent smsIntent = null;
                    if (hasSms) {
                        smsIntent = new Intent(Intent.ACTION_SENDTO,
                                Uri.fromParts(CallUtil.SCHEME_SMSTO, entry.data, null));
                        smsIntent.setComponent(smsComponent);
                    }
                    if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
                        if (null != phoneIntent) {
                            phoneIntent.setClassName("com.android.dialer", "com.android.dialer.PhoneSelect");
                            phoneIntent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        }
                    }

                    // Configure Icons and Intents.
                    if (hasPhone && hasSms) {
                        entry.intent = phoneIntent;
                        entry.secondaryIntent = smsIntent;
                        entry.secondaryActionIcon = kind.iconAltRes;
                        entry.secondaryActionDescription =
                            ContactDisplayUtils.getSmsLabelResourceId(entry.type);
                    } else if (hasPhone) {
                        entry.intent = phoneIntent;
                    } else if (hasSms) {
                        entry.intent = smsIntent;
                    } else {
                        entry.intent = null;
                    }

                    // Remember super-primary phone
                    if (isSuperPrimary) mPrimaryPhoneUri = entry.uri;

                    entry.isPrimary = isSuperPrimary;

                    // If the entry is a primary entry, then render it first in the view.
                    if (entry.isPrimary) {
                        // add to beginning of list so that this phone number shows up first
                        mPhoneEntries.add(0, entry);
                    } else {
                        // add to end of list
                        mPhoneEntries.add(entry);
                    }

                    // Configure the text direction. Phone numbers should be displayed LTR
                    // regardless of what locale the device is in.
                    entry.textDirection = View.TEXT_DIRECTION_LTR;
                } else if (dataItem instanceof EmailDataItem && hasData) {
                    // Build email entries
                    entry.intent = new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts(CallUtil.SCHEME_MAILTO, entry.data, null));
                    entry.isPrimary = isSuperPrimary;
                    // If entry is a primary entry, then render it first in the view.
                    if (entry.isPrimary) {
                        mEmailEntries.add(0, entry);
                    } else {
                        mEmailEntries.add(entry);
                    }

                    // When Email rows have status, create additional Im row
                    final DataStatus status = mContactData.getStatuses().get(entry.id);
                    if (status != null) {
                        EmailDataItem email = (EmailDataItem) dataItem;
                        ImDataItem im = ImDataItem.createFromEmail(email);

                        final DetailViewEntry imEntry = DetailViewEntry.fromValues(mContext, im,
                                mContactData.isDirectoryEntry(), mContactData.getDirectoryId(),
                                kind);
                        buildImActions(mContext, imEntry, im);
                        imEntry.setPresence(status.getPresence());
                        imEntry.maxLines = kind.maxLinesForDisplay;
                        mImEntries.add(imEntry);
                    }
                } else if (dataItem instanceof StructuredPostalDataItem && hasData) {
                    // Build postal entries
                    entry.intent = StructuredPostalUtils.getViewPostalAddressIntent(entry.data);
                    mPostalEntries.add(entry);
                } else if (dataItem instanceof ImDataItem && hasData) {
                    // Build IM entries
                    buildImActions(mContext, entry, (ImDataItem) dataItem);

                    // Apply presence when available
                    final DataStatus status = mContactData.getStatuses().get(entry.id);
                    if (status != null) {
                        entry.setPresence(status.getPresence());
                    }
                    mImEntries.add(entry);
                } else if (dataItem instanceof OrganizationDataItem) {
                    // Organizations are not shown. The first one is shown in the header
                    // and subsequent ones are not supported anymore
                } else if (dataItem instanceof NicknameDataItem && hasData) {
                    // Build nickname entries
                    final boolean isNameRawContact =
                        (mContactData.getNameRawContactId() == rawContactId);

                    final boolean duplicatesTitle =
                        isNameRawContact
                        && mContactData.getDisplayNameSource() == DisplayNameSources.NICKNAME;

                    if (!duplicatesTitle) {
                        entry.uri = null;
                        mNicknameEntries.add(entry);
                    }
                } else if (dataItem instanceof NoteDataItem && hasData) {
                    // Build note entries
                    entry.uri = null;
                    mNoteEntries.add(entry);
                } else if (dataItem instanceof WebsiteDataItem && hasData) {
                    // Build Website entries
                    entry.uri = null;
                    try {
                        WebAddress webAddress = new WebAddress(entry.data);
                        entry.intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse(webAddress.toString()));
                    } catch (ParseException e) {
                        Log.e(TAG, "Couldn't parse website: " + entry.data);
                    }
                    mWebsiteEntries.add(entry);
                } else if (dataItem instanceof SipAddressDataItem && hasData) {
                    // Build SipAddress entries
                    entry.uri = null;
                    if (hasSip) {
                        entry.intent = CallUtil.getCallIntent(
                                Uri.fromParts(CallUtil.SCHEME_SIP, entry.data, null));
                    } else {
                        entry.intent = null;
                    }
                    mSipEntries.add(entry);
                    // TODO: Now that SipAddress is in its own list of entries
                    // (instead of grouped in mOtherEntries), consider
                    // repositioning it right under the phone number.
                    // (Then, we'd also update FallbackAccountType.java to set
                    // secondary=false for this field, and tweak the weight
                    // of its DataKind.)
                } else if (dataItem instanceof EventDataItem && hasData) {
                    final Calendar cal = DateUtils.parseDate(entry.data, false);
                    if (cal != null) {
                        final Date nextAnniversary =
                                DateUtils.getNextAnnualDate(cal);
                        final Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
                        builder.appendPath("time");
                        ContentUris.appendId(builder, nextAnniversary.getTime());
                        entry.intent = new Intent(Intent.ACTION_VIEW).setData(builder.build());
                    }
                    entry.data = DateUtils.formatDate(mContext, entry.data);
                    entry.uri = null;
                    mEventEntries.add(entry);
                } else if (dataItem instanceof RelationDataItem && hasData) {
                    entry.intent = new Intent(Intent.ACTION_SEARCH);
                    entry.intent.putExtra(SearchManager.QUERY, entry.data);
                    entry.intent.setType(Contacts.CONTENT_TYPE);
                    mRelationEntries.add(entry);
                } else {
                    // Handle showing custom rows
                    entry.intent = new Intent(Intent.ACTION_VIEW);
                    entry.intent.setDataAndType(entry.uri, entry.mimetype);

                    entry.data = dataItem.buildDataString(getContext(), kind);

                    if (!TextUtils.isEmpty(entry.data)) {
                        // If the account type exists in the hash map, add it as another entry for
                        // that account type
                        if (mOtherEntriesMap.containsKey(accountType)) {
                            List<DetailViewEntry> listEntries = mOtherEntriesMap.get(accountType);
                            listEntries.add(entry);
                        } else {
                            // Otherwise create a new list with the entry and add it to the hash map
                            List<DetailViewEntry> listEntries = new ArrayList<DetailViewEntry>();
                            listEntries.add(entry);
                            mOtherEntriesMap.put(accountType, listEntries);
                        }
                    }
                }
            }
        }

        if (!groups.isEmpty()) {
            DetailViewEntry entry = new DetailViewEntry();
            Collections.sort(groups);
            StringBuilder sb = new StringBuilder();
            int size = groups.size();
            for (int i = 0; i < size; i++) {
                if (i != 0) {
                    sb.append(", ");
                }
                sb.append(groups.get(i));
            }
            entry.mimetype = GroupMembership.MIMETYPE;
            entry.kind = mContext.getString(R.string.groupsLabel);
            entry.data = sb.toString();
            mGroupEntries.add(entry);
        }
    }

    /**
     * Collapse all contact detail entries into one aggregated list with a {@link HeaderViewEntry}
     * at the top.
     */
    private void setupFlattenedList() {
        // All contacts should have a header view (even if there is no data for the contact).
        mAllEntries.add(new HeaderViewEntry());

        addPhoneticName();

        flattenList(mPhoneEntries);
        flattenList(mSmsEntries);
        flattenList(mEmailEntries);
        flattenList(mImEntries);
        flattenList(mNicknameEntries);
        flattenList(mWebsiteEntries);

        addNetworks();

        flattenList(mSipEntries);
        flattenList(mPostalEntries);
        flattenList(mEventEntries);
        flattenList(mGroupEntries);
        flattenList(mRelationEntries);
        flattenList(mNoteEntries);
    }

    /**
     * Add phonetic name (if applicable) to the aggregated list of contact details. This has to be
     * done manually because phonetic name doesn't have a mimetype or action intent.
     */
    private void addPhoneticName() {
        String phoneticName = ContactDetailDisplayUtils.getPhoneticName(mContext, mContactData);
        if (TextUtils.isEmpty(phoneticName)) {
            return;
        }

        // Add a title
        String phoneticNameKindTitle = mContext.getString(R.string.name_phonetic);
        mAllEntries.add(new KindTitleViewEntry(phoneticNameKindTitle.toUpperCase()));

        // Add the phonetic name
        final DetailViewEntry entry = new DetailViewEntry();
        entry.kind = phoneticNameKindTitle;
        entry.data = phoneticName;
        mAllEntries.add(entry);
    }

    /**
     * Add attribution and other third-party entries (if applicable) under the "networks" section
     * of the aggregated list of contact details. This has to be done manually because the
     * attribution does not have a mimetype and the third-party entries don't have actually belong
     * to the same {@link DataKind}.
     */
    private void addNetworks() {
        String attribution = ContactDetailDisplayUtils.getAttribution(mContext, mContactData);
        boolean hasAttribution = !TextUtils.isEmpty(attribution);
        int networksCount = mOtherEntriesMap.keySet().size();

        // Note: invitableCount will always be 0 for me profile.  (ContactLoader won't set
        // invitable types for me profile.)
        int invitableCount = mContactData.getInvitableAccountTypes().size();
        if (!hasAttribution && networksCount == 0 && invitableCount == 0) {
            return;
        }

        // Add a title
        String networkKindTitle = mContext.getString(R.string.connections);
        mAllEntries.add(new KindTitleViewEntry(networkKindTitle.toUpperCase()));

        // Add the attribution if applicable
        if (hasAttribution) {
            final DetailViewEntry entry = new DetailViewEntry();
            entry.kind = networkKindTitle;
            entry.data = attribution;
            mAllEntries.add(entry);

            // Add a divider below the attribution if there are network details that will follow
            if (networksCount > 0) {
                mAllEntries.add(new SeparatorViewEntry());
            }
        }

        // Add the other entries from third parties
        for (AccountType accountType : mOtherEntriesMap.keySet()) {

            // Add a title for each third party app
            mAllEntries.add(new NetworkTitleViewEntry(mContext, accountType));

            for (DetailViewEntry detailEntry : mOtherEntriesMap.get(accountType)) {
                // Add indented separator
                SeparatorViewEntry separatorEntry = new SeparatorViewEntry();
                separatorEntry.setIsInSubSection(true);
                mAllEntries.add(separatorEntry);

                // Add indented detail
                detailEntry.setIsInSubSection(true);
                mAllEntries.add(detailEntry);
            }
        }

        mOtherEntriesMap.clear();

        // Add the "More networks" button, which opens the invitable account type list popup.
        if (invitableCount > 0) {
            addMoreNetworks();
        }
    }

    /**
     * Add the "More networks" entry.  When clicked, show a popup containing a list of invitable
     * account types.
     */
    private void addMoreNetworks() {
        // First, prepare for the popup.

        // Adapter for the list popup.
        final InvitableAccountTypesAdapter popupAdapter = new InvitableAccountTypesAdapter(mContext,
                mContactData);

        // Listener called when a popup item is clicked.
        final AdapterView.OnItemClickListener popupItemListener
                = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                    long id) {
                if (mListener != null && mContactData != null) {
                    mListener.onItemClicked(MoreContactUtils.getInvitableIntent(
                            popupAdapter.getItem(position) /* account type */,
                            mContactData.getLookupUri()));
                }
            }
        };

        // Then create the click listener for the "More network" entry.  Open the popup.
        View.OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                showListPopup(v, popupAdapter, popupItemListener);
            }
        };

        // Finally create the entry.
        mAllEntries.add(new AddConnectionViewEntry(mContext, onClickListener));
    }

    /**
     * Iterate through {@link DetailViewEntry} in the given list and add it to a list of all
     * entries. Add a {@link KindTitleViewEntry} at the start if the length of the list is not 0.
     * Add {@link SeparatorViewEntry}s as dividers as appropriate. Clear the original list.
     */
    private void flattenList(ArrayList<DetailViewEntry> entries) {
        int count = entries.size();

        // Add a title for this kind by extracting the kind from the first entry
        if (count > 0) {
            String kind = entries.get(0).kind;
            mAllEntries.add(new KindTitleViewEntry(kind.toUpperCase()));
        }

        // Add all the data entries for this kind
        for (int i = 0; i < count; i++) {
            // For all entries except the first one, add a divider above the entry
            if (i != 0) {
                mAllEntries.add(new SeparatorViewEntry());
            }
            mAllEntries.add(entries.get(i));
        }

        // Clear old list because it's not needed anymore.
        entries.clear();
    }

    /**
     * Maps group ID to the corresponding group name, collapses all synonymous groups.
     * Ignores default groups (e.g. My Contacts) and favorites groups.
     */
    private void handleGroupMembership(
            ArrayList<String> groups, List<GroupMetaData> groupMetaData, long groupId) {
        if (groupMetaData == null) {
            return;
        }

        for (GroupMetaData group : groupMetaData) {
            if (group.getGroupId() == groupId) {
                if (!group.isDefaultGroup() && !group.isFavorites()) {
                    String title = group.getTitle();
                    if (!TextUtils.isEmpty(title) && !groups.contains(title)) {
                        groups.add(title);
                    }
                }
                break;
            }
        }
    }

    /**
     * Writes the Instant Messaging action into the given entry value.
     */
    @VisibleForTesting
    public static void buildImActions(Context context, DetailViewEntry entry,
            ImDataItem im) {
        final boolean isEmail = im.isCreatedFromEmail();

        if (!isEmail && !im.isProtocolValid()) {
            return;
        }

        final String data = im.getData();
        if (TextUtils.isEmpty(data)) {
            return;
        }

        final int protocol = isEmail ? Im.PROTOCOL_GOOGLE_TALK : im.getProtocol();

        if (protocol == Im.PROTOCOL_GOOGLE_TALK) {
            final int chatCapability = im.getChatCapability();
            entry.chatCapability = chatCapability;
            entry.typeString = Im.getProtocolLabel(context.getResources(), Im.PROTOCOL_GOOGLE_TALK,
                    null).toString();
            if ((chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0) {
                entry.intent =
                        new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
                entry.secondaryIntent =
                        new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
            } else if ((chatCapability & Im.CAPABILITY_HAS_VOICE) != 0) {
                // Allow Talking and Texting
                entry.intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
                entry.secondaryIntent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?call"));
            } else {
                entry.intent =
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("xmpp:" + data + "?message"));
            }
        } else {
            // Build an IM Intent
            final Intent imIntent = getCustomIMIntent(im, protocol);
            if (imIntent != null &&
                    PhoneCapabilityTester.isIntentRegistered(context, imIntent)) {
                entry.intent = imIntent;
            }
        }
    }

    @VisibleForTesting
    public static Intent getCustomIMIntent(ImDataItem im, int protocol) {
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
        final Uri imUri = new Uri.Builder().scheme(CallUtil.SCHEME_IMTO).authority(
                authority).appendPath(data).build();
        final Intent intent = new Intent(Intent.ACTION_SENDTO, imUri);
        return intent;
    }

    /**
     * Show a list popup.  Used for "popup-able" entry, such as "More networks".
     */
    private void showListPopup(View anchorView, ListAdapter adapter,
            final AdapterView.OnItemClickListener onItemClickListener) {
        dismissPopupIfShown();
        mPopup = new ListPopupWindow(mContext, null);
        mPopup.setAnchorView(anchorView);
        mPopup.setWidth(anchorView.getWidth());
        mPopup.setAdapter(adapter);
        mPopup.setModal(true);

        // We need to wrap the passed onItemClickListener here, so that we can dismiss() the
        // popup afterwards.  Otherwise we could directly use the passed listener.
        mPopup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position,
                    long id) {
                onItemClickListener.onItemClick(parent, view, position, id);
                dismissPopupIfShown();
            }
        });
        mPopup.show();
    }

    private void dismissPopupIfShown() {
        UiClosables.closeQuietly(mPopup);
        mPopup = null;
    }

    /**
     * Base class for an item in the {@link ViewAdapter} list of data, which is
     * supplied to the {@link ListView}.
     */
    static class ViewEntry {
        private final int viewTypeForAdapter;
        protected long id = -1;
        /** Whether or not the entry can be focused on or not. */
        protected boolean isEnabled = false;

        ViewEntry(int viewType) {
            viewTypeForAdapter = viewType;
        }

        int getViewType() {
            return viewTypeForAdapter;
        }

        long getId() {
            return id;
        }

        boolean isEnabled(){
            return isEnabled;
        }

        /**
         * Called when the entry is clicked.  Only {@link #isEnabled} entries can get clicked.
         *
         * @param clickedView  {@link View} that was clicked  (Used, for example, as the anchor view
         *        for a popup.)
         * @param fragmentListener  {@link Listener} set to {@link ContactDetailFragment}
         */
        public void click(View clickedView, Listener fragmentListener) {
        }
    }

    /**
     * Header item in the {@link ViewAdapter} list of data.
     */
    private static class HeaderViewEntry extends ViewEntry {

        HeaderViewEntry() {
            super(ViewAdapter.VIEW_TYPE_HEADER_ENTRY);
        }

    }

    /**
     * Separator between items of the same {@link DataKind} in the
     * {@link ViewAdapter} list of data.
     */
    private static class SeparatorViewEntry extends ViewEntry {

        /**
         * Whether or not the entry is in a subsection (if true then the contents will be indented
         * to the right)
         */
        private boolean mIsInSubSection = false;

        SeparatorViewEntry() {
            super(ViewAdapter.VIEW_TYPE_SEPARATOR_ENTRY);
        }

        public void setIsInSubSection(boolean isInSubSection) {
            mIsInSubSection = isInSubSection;
        }

        public boolean isInSubSection() {
            return mIsInSubSection;
        }
    }

    /**
     * Title entry for items of the same {@link DataKind} in the
     * {@link ViewAdapter} list of data.
     */
    private static class KindTitleViewEntry extends ViewEntry {

        private final String mTitle;

        KindTitleViewEntry(String titleText) {
            super(ViewAdapter.VIEW_TYPE_KIND_TITLE_ENTRY);
            mTitle = titleText;
        }

        public String getTitle() {
            return mTitle;
        }
    }

    /**
     * A title for a section of contact details from a single 3rd party network.
     */
    private static class NetworkTitleViewEntry extends ViewEntry {
        private final Drawable mIcon;
        private final CharSequence mLabel;

        public NetworkTitleViewEntry(Context context, AccountType type) {
            super(ViewAdapter.VIEW_TYPE_NETWORK_TITLE_ENTRY);
            this.mIcon = type.getDisplayIcon(context);
            this.mLabel = type.getDisplayLabel(context);
            this.isEnabled = false;
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public CharSequence getLabel() {
            return mLabel;
        }
    }

    /**
     * This is used for the "Add Connections" entry.
     */
    private static class AddConnectionViewEntry extends ViewEntry {
        private final Drawable mIcon;
        private final CharSequence mLabel;
        private final View.OnClickListener mOnClickListener;

        private AddConnectionViewEntry(Context context, View.OnClickListener onClickListener) {
            super(ViewAdapter.VIEW_TYPE_ADD_CONNECTION_ENTRY);
            this.mIcon = context.getResources().getDrawable(
                    R.drawable.ic_menu_add_field_holo_light);
            this.mLabel = context.getString(R.string.add_connection_button);
            this.mOnClickListener = onClickListener;
            this.isEnabled = true;
        }

        @Override
        public void click(View clickedView, Listener fragmentListener) {
            if (mOnClickListener == null) return;
            mOnClickListener.onClick(clickedView);
        }

        public Drawable getIcon() {
            return mIcon;
        }

        public CharSequence getLabel() {
            return mLabel;
        }
    }

    /**
     * An item with a single detail for a contact in the {@link ViewAdapter}
     * list of data.
     */
    static class DetailViewEntry extends ViewEntry implements Collapsible<DetailViewEntry> {
        // TODO: Make getters/setters for these fields
        public int type = -1;
        public String kind;
        public String typeString;
        public String data;
        public Uri uri;
        public int maxLines = 1;
        public int textDirection = TEXT_DIRECTION_UNDEFINED;
        public String mimetype;

        public Context context = null;
        public boolean isPrimary = false;
        public int secondaryActionIcon = -1;
        public int secondaryActionDescription = -1;
        public Intent intent;
        public Intent secondaryIntent = null;
        public ArrayList<Long> ids = new ArrayList<Long>();
        public int collapseCount = 0;

        public int presence = -1;
        public int chatCapability = 0;

        private boolean mIsInSubSection = false;

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("type", type)
                    .add("kind", kind)
                    .add("typeString", typeString)
                    .add("data", data)
                    .add("uri", uri)
                    .add("maxLines", maxLines)
                    .add("mimetype", mimetype)
                    .add("context", context)
                    .add("isPrimary", isPrimary)
                    .add("secondaryActionIcon", secondaryActionIcon)
                    .add("secondaryActionDescription", secondaryActionDescription)
                    .add("intent", intent)
                    .add("secondaryIntent", secondaryIntent)
                    .add("ids", ids)
                    .add("collapseCount", collapseCount)
                    .add("presence", presence)
                    .add("chatCapability", chatCapability)
                    .add("mIsInSubSection", mIsInSubSection)
                    .toString();
        }

        DetailViewEntry() {
            super(ViewAdapter.VIEW_TYPE_DETAIL_ENTRY);
            isEnabled = true;
        }

        /**
         * Build new {@link DetailViewEntry} and populate from the given values.
         */
        public static DetailViewEntry fromValues(Context context, DataItem item,
                boolean isDirectoryEntry, long directoryId, DataKind dataKind) {
            final DetailViewEntry entry = new DetailViewEntry();
            entry.id = item.getId();
            entry.context = context;
            entry.uri = ContentUris.withAppendedId(Data.CONTENT_URI, entry.id);
            if (isDirectoryEntry) {
                entry.uri = entry.uri.buildUpon().appendQueryParameter(
                        ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
            }
            entry.mimetype = item.getMimeType();
            entry.kind = dataKind.getKindString(context);
            entry.data = item.buildDataString(context, dataKind);

            if (item.hasKindTypeColumn(dataKind)) {
                entry.type = item.getKindTypeColumn(dataKind);

                // get type string
                entry.typeString = "";
                for (EditType type : dataKind.typeList) {
                    if (type.rawValue == entry.type) {
                        if (type.customColumn == null) {
                            // Non-custom type. Get its description from the resource
                            entry.typeString = context.getString(type.labelRes);
                        } else {
                            // Custom type. Read it from the database
                            entry.typeString =
                                    item.getContentValues().getAsString(type.customColumn);
                        }
                        break;
                    }
                }
            } else {
                entry.typeString = "";
            }

            return entry;
        }

        public void setPresence(int presence) {
            this.presence = presence;
        }

        public void setIsInSubSection(boolean isInSubSection) {
            mIsInSubSection = isInSubSection;
        }

        public boolean isInSubSection() {
            return mIsInSubSection;
        }

        @Override
        public void collapseWith(DetailViewEntry entry) {
            // Choose the label associated with the highest type precedence.
            if (TypePrecedence.getTypePrecedence(mimetype, type)
                    > TypePrecedence.getTypePrecedence(entry.mimetype, entry.type)) {
                type = entry.type;
                kind = entry.kind;
                typeString = entry.typeString;
            }

            // Choose the max of the maxLines and maxLabelLines values.
            maxLines = Math.max(maxLines, entry.maxLines);

            // Choose the presence with the highest precedence.
            if (StatusUpdates.getPresencePrecedence(presence)
                    < StatusUpdates.getPresencePrecedence(entry.presence)) {
                presence = entry.presence;
            }

            // If any of the collapsed entries are primary make the whole thing primary.
            isPrimary = entry.isPrimary ? true : isPrimary;

            // uri, and contactdId, shouldn't make a difference. Just keep the original.

            // Keep track of all the ids that have been collapsed with this one.
            ids.add(entry.getId());
            collapseCount++;
        }

        @Override
        public boolean shouldCollapseWith(DetailViewEntry entry) {
            if (entry == null) {
                return false;
            }

            if (!MoreContactUtils.shouldCollapse(mimetype, data, entry.mimetype, entry.data)) {
                return false;
            }

            if (!TextUtils.equals(mimetype, entry.mimetype)
                    || !ContactsUtils.areIntentActionEqual(intent, entry.intent)
                    || !ContactsUtils.areIntentActionEqual(
                            secondaryIntent, entry.secondaryIntent)) {
                return false;
            }

            return true;
        }

        @Override
        public void click(View clickedView, Listener fragmentListener) {
            if (fragmentListener == null || intent == null) return;
            fragmentListener.onItemClicked(intent);
        }
    }

    /**
     * Cache of the children views for a view that displays a header view entry.
     */
    private static class HeaderViewCache {
        public final TextView displayNameView;
        public final TextView companyView;
        public final ImageView photoView;
        public final View photoOverlayView;
        public final ImageView starredView;
        public final int layoutResourceId;

        public HeaderViewCache(View view, int layoutResourceInflated) {
            displayNameView = (TextView) view.findViewById(R.id.name);
            companyView = (TextView) view.findViewById(R.id.company);
            photoView = (ImageView) view.findViewById(R.id.photo);
            photoOverlayView = view.findViewById(R.id.photo_touch_intercept_overlay);
            starredView = (ImageView) view.findViewById(R.id.star);
            layoutResourceId = layoutResourceInflated;
        }

        public void enablePhotoOverlay(OnClickListener listener) {
            if (photoOverlayView != null) {
                photoOverlayView.setOnClickListener(listener);
                photoOverlayView.setVisibility(View.VISIBLE);
            }
        }
    }

    private static class KindTitleViewCache {
        public final TextView titleView;

        public KindTitleViewCache(View view) {
            titleView = (TextView)view.findViewById(R.id.title);
        }
    }

    /**
     * Cache of the children views for a view that displays a {@link NetworkTitleViewEntry}
     */
    private static class NetworkTitleViewCache {
        public final TextView name;
        public final ImageView icon;

        public NetworkTitleViewCache(View view) {
            name = (TextView) view.findViewById(R.id.network_title);
            icon = (ImageView) view.findViewById(R.id.network_icon);
        }
    }

    /**
     * Cache of the children views for a view that displays a {@link AddConnectionViewEntry}
     */
    private static class AddConnectionViewCache {
        public final TextView name;
        public final ImageView icon;
        public final View primaryActionView;

        public AddConnectionViewCache(View view) {
            name = (TextView) view.findViewById(R.id.add_connection_label);
            icon = (ImageView) view.findViewById(R.id.add_connection_icon);
            primaryActionView = view.findViewById(R.id.primary_action_view);
        }
    }

    /**
     * Cache of the children views of a contact detail entry represented by a
     * {@link DetailViewEntry}
     */
    private static class DetailViewCache {
        public final TextView type;
        public final TextView data;
        public final ImageView presenceIcon;
        public final ImageView secondaryActionButton;
        public final View actionsViewContainer;
        public final View primaryActionView;
        public final View secondaryActionViewContainer;
        public final View secondaryActionDivider;
        public final View primaryIndicator;

        public DetailViewCache(View view,
                OnClickListener primaryActionClickListener,
                OnClickListener secondaryActionClickListener) {
            type = (TextView) view.findViewById(R.id.type);
            data = (TextView) view.findViewById(R.id.data);
            primaryIndicator = view.findViewById(R.id.primary_indicator);
            presenceIcon = (ImageView) view.findViewById(R.id.presence_icon);

            actionsViewContainer = view.findViewById(R.id.actions_view_container);
            actionsViewContainer.setOnClickListener(primaryActionClickListener);
            primaryActionView = view.findViewById(R.id.primary_action_view);

            secondaryActionViewContainer = view.findViewById(
                    R.id.secondary_action_view_container);
            secondaryActionViewContainer.setOnClickListener(
                    secondaryActionClickListener);
            secondaryActionButton = (ImageView) view.findViewById(
                    R.id.secondary_action_button);

            secondaryActionDivider = view.findViewById(R.id.vertical_divider);
        }
    }

    private final class ViewAdapter extends BaseAdapter {

        public static final int VIEW_TYPE_DETAIL_ENTRY = 0;
        public static final int VIEW_TYPE_HEADER_ENTRY = 1;
        public static final int VIEW_TYPE_KIND_TITLE_ENTRY = 2;
        public static final int VIEW_TYPE_NETWORK_TITLE_ENTRY = 3;
        public static final int VIEW_TYPE_ADD_CONNECTION_ENTRY = 4;
        public static final int VIEW_TYPE_SEPARATOR_ENTRY = 5;
        private static final int VIEW_TYPE_COUNT = 6;

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            switch (getItemViewType(position)) {
                case VIEW_TYPE_HEADER_ENTRY:
                    return getHeaderEntryView(convertView, parent);
                case VIEW_TYPE_SEPARATOR_ENTRY:
                    return getSeparatorEntryView(position, convertView, parent);
                case VIEW_TYPE_KIND_TITLE_ENTRY:
                    return getKindTitleEntryView(position, convertView, parent);
                case VIEW_TYPE_DETAIL_ENTRY:
                    return getDetailEntryView(position, convertView, parent);
                case VIEW_TYPE_NETWORK_TITLE_ENTRY:
                    return getNetworkTitleEntryView(position, convertView, parent);
                case VIEW_TYPE_ADD_CONNECTION_ENTRY:
                    return getAddConnectionEntryView(position, convertView, parent);
                default:
                    throw new IllegalStateException("Invalid view type ID " +
                            getItemViewType(position));
            }
        }

        private View getHeaderEntryView(View convertView, ViewGroup parent) {
            final int desiredLayoutResourceId = R.layout.detail_header_contact_without_updates;
            View result = null;
            HeaderViewCache viewCache = null;

            // Only use convertView if it has the same layout resource ID as the one desired
            // (the two can be different on wide 2-pane screens where the detail fragment is reused
            // for many different contacts that do and do not have social updates).
            if (convertView != null) {
                viewCache = (HeaderViewCache) convertView.getTag();
                if (viewCache.layoutResourceId == desiredLayoutResourceId) {
                    result = convertView;
                }
            }

            // Otherwise inflate a new header view and create a new view cache.
            if (result == null) {
                result = mInflater.inflate(desiredLayoutResourceId, parent, false);
                viewCache = new HeaderViewCache(result, desiredLayoutResourceId);
                result.setTag(viewCache);
            }

            ContactDetailDisplayUtils.setDisplayName(mContext, mContactData,
                    viewCache.displayNameView);
            ContactDetailDisplayUtils.setCompanyName(mContext, mContactData, viewCache.companyView);

            // Set the photo if it should be displayed
            if (viewCache.photoView != null) {
                final boolean expandOnClick = mContactData.getPhotoUri() != null;
                final OnClickListener listener = mPhotoSetter.setupContactPhotoForClick(
                        mContext, mContactData, viewCache.photoView, expandOnClick);

                if (expandOnClick || mContactData.isWritableContact(mContext)) {
                    viewCache.enablePhotoOverlay(listener);
                }
            }

            // Set the starred state if it should be displayed
            final ImageView favoritesStar = viewCache.starredView;
            if (favoritesStar != null) {
                ContactDetailDisplayUtils.configureStarredImageView(favoritesStar,
                        mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                        mContactData.getStarred());
                final Uri lookupUri = mContactData.getLookupUri();
                favoritesStar.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Toggle "starred" state
                        // Make sure there is a contact
                        if (lookupUri != null) {
                            // Read the current starred value from the UI instead of using the last
                            // loaded state. This allows rapid tapping without writing the same
                            // value several times
                            final Object tag = favoritesStar.getTag();
                            final boolean isStarred = tag == null
                                    ? false : (Boolean) favoritesStar.getTag();

                            // To improve responsiveness, swap out the picture (and tag) in the UI
                            // already
                            ContactDetailDisplayUtils.configureStarredImageView(favoritesStar,
                                    mContactData.isDirectoryEntry(), mContactData.isUserProfile(),
                                    !isStarred);

                            // Now perform the real save
                            Intent intent = ContactSaveService.createSetStarredIntent(
                                    getContext(), lookupUri, !isStarred);
                            getContext().startService(intent);
                        }
                    }
                });
            }

            return result;
        }

        private View getSeparatorEntryView(int position, View convertView, ViewGroup parent) {
            final SeparatorViewEntry entry = (SeparatorViewEntry) getItem(position);
            final View result = (convertView != null) ? convertView :
                    mInflater.inflate(R.layout.contact_detail_separator_entry_view, parent, false);

            result.setPadding(entry.isInSubSection() ? mViewEntryDimensions.getWidePaddingLeft() :
                    mViewEntryDimensions.getPaddingLeft(), 0,
                    mViewEntryDimensions.getPaddingRight(), 0);

            return result;
        }

        private View getKindTitleEntryView(int position, View convertView, ViewGroup parent) {
            final KindTitleViewEntry entry = (KindTitleViewEntry) getItem(position);
            final View result;
            final KindTitleViewCache viewCache;

            if (convertView != null) {
                result = convertView;
                viewCache = (KindTitleViewCache)result.getTag();
            } else {
                result = mInflater.inflate(R.layout.list_separator, parent, false);
                viewCache = new KindTitleViewCache(result);
                result.setTag(viewCache);
            }

            viewCache.titleView.setText(entry.getTitle());

            return result;
        }

        private View getNetworkTitleEntryView(int position, View convertView, ViewGroup parent) {
            final NetworkTitleViewEntry entry = (NetworkTitleViewEntry) getItem(position);
            final View result;
            final NetworkTitleViewCache viewCache;

            if (convertView != null) {
                result = convertView;
                viewCache = (NetworkTitleViewCache) result.getTag();
            } else {
                result = mInflater.inflate(R.layout.contact_detail_network_title_entry_view,
                        parent, false);
                viewCache = new NetworkTitleViewCache(result);
                result.setTag(viewCache);
            }

            viewCache.name.setText(entry.getLabel());
            viewCache.icon.setImageDrawable(entry.getIcon());

            return result;
        }

        private View getAddConnectionEntryView(int position, View convertView, ViewGroup parent) {
            final AddConnectionViewEntry entry = (AddConnectionViewEntry) getItem(position);
            final View result;
            final AddConnectionViewCache viewCache;

            if (convertView != null) {
                result = convertView;
                viewCache = (AddConnectionViewCache) result.getTag();
            } else {
                result = mInflater.inflate(R.layout.contact_detail_add_connection_entry_view,
                        parent, false);
                viewCache = new AddConnectionViewCache(result);
                result.setTag(viewCache);
            }
            viewCache.name.setText(entry.getLabel());
            viewCache.icon.setImageDrawable(entry.getIcon());
            viewCache.primaryActionView.setOnClickListener(entry.mOnClickListener);

            return result;
        }

        private View getDetailEntryView(int position, View convertView, ViewGroup parent) {
            final DetailViewEntry entry = (DetailViewEntry) getItem(position);
            final View v;
            final DetailViewCache viewCache;

            // Check to see if we can reuse convertView
            if (convertView != null) {
                v = convertView;
                viewCache = (DetailViewCache) v.getTag();
            } else {
                // Create a new view if needed
                v = mInflater.inflate(R.layout.contact_detail_list_item, parent, false);

                // Cache the children
                viewCache = new DetailViewCache(v,
                        mPrimaryActionClickListener, mSecondaryActionClickListener);
                v.setTag(viewCache);
            }

            bindDetailView(position, v, entry);
            return v;
        }

        private void bindDetailView(int position, View view, DetailViewEntry entry) {
            final Resources resources = mContext.getResources();
            DetailViewCache views = (DetailViewCache) view.getTag();

            if (!TextUtils.isEmpty(entry.typeString)) {
                views.type.setText(entry.typeString.toUpperCase());
                views.type.setVisibility(View.VISIBLE);
            } else {
                views.type.setVisibility(View.GONE);
            }

            views.data.setText(entry.data);
            setMaxLines(views.data, entry.maxLines);

            // Gray out the data item if it does not perform an action when clicked
            // Set primary_text_color even if it might have been set by default to avoid
            // views being gray sometimes when they are not supposed to, due to view reuse
            ((TextView) view.findViewById(R.id.data)).setTextColor(
                        getResources().getColor((entry.intent == null) ?
                        R.color.secondary_text_color : R.color.primary_text_color));

            // Set the default contact method
            views.primaryIndicator.setVisibility(entry.isPrimary ? View.VISIBLE : View.GONE);

            // Set the presence icon
            final Drawable presenceIcon = ContactPresenceIconUtil.getPresenceIcon(
                    mContext, entry.presence);
            final ImageView presenceIconView = views.presenceIcon;
            if (presenceIcon != null) {
                presenceIconView.setImageDrawable(presenceIcon);
                presenceIconView.setVisibility(View.VISIBLE);
            } else {
                presenceIconView.setVisibility(View.GONE);
            }

            final ActionsViewContainer actionsButtonContainer =
                    (ActionsViewContainer) views.actionsViewContainer;
            actionsButtonContainer.setTag(entry);
            actionsButtonContainer.setPosition(position);
            registerForContextMenu(actionsButtonContainer);

            // Set the secondary action button
            final ImageView secondaryActionView = views.secondaryActionButton;
            Drawable secondaryActionIcon = null;
            String secondaryActionDescription = null;
            if (entry.secondaryActionIcon != -1) {
                secondaryActionIcon = resources.getDrawable(entry.secondaryActionIcon);
                if (ContactDisplayUtils.isCustomPhoneType(entry.type)) {
                    secondaryActionDescription = resources.getString(
                            entry.secondaryActionDescription, entry.typeString);
                } else {
                    secondaryActionDescription = resources.getString(
                            entry.secondaryActionDescription);
                }
            } else if ((entry.chatCapability & Im.CAPABILITY_HAS_CAMERA) != 0) {
                secondaryActionIcon =
                        resources.getDrawable(R.drawable.sym_action_videochat_holo_light);
                secondaryActionDescription = resources.getString(R.string.video_chat);
            } else if ((entry.chatCapability & Im.CAPABILITY_HAS_VOICE) != 0) {
                secondaryActionIcon =
                        resources.getDrawable(R.drawable.sym_action_audiochat_holo_light);
                secondaryActionDescription = resources.getString(R.string.audio_chat);
            }

            final View secondaryActionViewContainer = views.secondaryActionViewContainer;
            if (entry.secondaryIntent != null && secondaryActionIcon != null) {
                secondaryActionView.setImageDrawable(secondaryActionIcon);
                secondaryActionView.setContentDescription(secondaryActionDescription);
                secondaryActionViewContainer.setTag(entry);
                secondaryActionViewContainer.setVisibility(View.VISIBLE);
                views.secondaryActionDivider.setVisibility(View.VISIBLE);
            } else {
                secondaryActionViewContainer.setVisibility(View.GONE);
                views.secondaryActionDivider.setVisibility(View.GONE);
            }

            // Right and left padding should not have "pressed" effect.
            view.setPadding(
                    entry.isInSubSection()
                            ? mViewEntryDimensions.getWidePaddingLeft()
                            : mViewEntryDimensions.getPaddingLeft(),
                    0, mViewEntryDimensions.getPaddingRight(), 0);
            // Top and bottom padding should have "pressed" effect.
            final View primaryActionView = views.primaryActionView;
            primaryActionView.setPadding(
                    primaryActionView.getPaddingLeft(),
                    mViewEntryDimensions.getPaddingTop(),
                    primaryActionView.getPaddingRight(),
                    mViewEntryDimensions.getPaddingBottom());
            secondaryActionViewContainer.setPadding(
                    secondaryActionViewContainer.getPaddingLeft(),
                    mViewEntryDimensions.getPaddingTop(),
                    secondaryActionViewContainer.getPaddingRight(),
                    mViewEntryDimensions.getPaddingBottom());

            // Set the text direction
            if (entry.textDirection != TEXT_DIRECTION_UNDEFINED) {
                views.data.setTextDirection(entry.textDirection);
            }
        }

        private void setMaxLines(TextView textView, int maxLines) {
            if (maxLines == 1) {
                textView.setSingleLine(true);
                textView.setEllipsize(TextUtils.TruncateAt.END);
            } else {
                textView.setSingleLine(false);
                textView.setMaxLines(maxLines);
                textView.setEllipsize(null);
            }
        }

        private final OnClickListener mPrimaryActionClickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener == null) return;
                final ViewEntry entry = (ViewEntry) view.getTag();
                if (entry == null) return;
                entry.click(view, mListener);
            }
        };

        private final OnClickListener mSecondaryActionClickListener = new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mListener == null) return;
                if (view == null) return;
                final ViewEntry entry = (ViewEntry) view.getTag();
                if (entry == null || !(entry instanceof DetailViewEntry)) return;
                final DetailViewEntry detailViewEntry = (DetailViewEntry) entry;
                final Intent intent = detailViewEntry.secondaryIntent;
                if (intent == null) return;
                mListener.onItemClicked(intent);
            }
        };

        @Override
        public int getCount() {
            return mAllEntries.size();
        }

        @Override
        public ViewEntry getItem(int position) {
            return mAllEntries.get(position);
        }

        @Override
        public int getItemViewType(int position) {
            return mAllEntries.get(position).getViewType();
        }

        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }

        @Override
        public long getItemId(int position) {
            final ViewEntry entry = mAllEntries.get(position);
            if (entry != null) {
                return entry.getId();
            }
            return -1;
        }

        @Override
        public boolean areAllItemsEnabled() {
            // Header will always be an item that is not enabled.
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItem(position).isEnabled();
        }
    }

    @Override
    public void onAccountSelectorCancelled() {
    }

    @Override
    public void onAccountChosen(AccountWithDataSet account, Bundle extraArgs) {
        createCopy(account);
    }

    private void createCopy(AccountWithDataSet account) {
        if (mListener != null) {
            mListener.onCreateRawContactRequested(mContactData.getContentValues(), account);
        }
    }

    /**
     * Default (fallback) list item click listener.  Note the click event for DetailViewEntry is
     * caught by individual views in the list item view to distinguish the primary action and the
     * secondary action, so this method won't be invoked for that.  (The listener is set in the
     * bindview in the adapter)
     * This listener is used for other kind of entries.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (mListener == null) return;
        final ViewEntry entry = mAdapter.getItem(position);
        if (entry == null) return;
        entry.click(view, mListener);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);

        AdapterView.AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        DetailViewEntry selectedEntry = (DetailViewEntry) mAllEntries.get(info.position);

        menu.setHeaderTitle(selectedEntry.data);
        menu.add(ContextMenu.NONE, ContextMenuIds.COPY_TEXT,
                ContextMenu.NONE, getString(R.string.copy_text));

        // Don't allow setting or clearing of defaults for directory contacts
        if (mContactData.isDirectoryEntry()) {
            return;
        }

        String selectedMimeType = selectedEntry.mimetype;

        // Defaults to true will only enable the detail to be copied to the clipboard.
        boolean isUniqueMimeType = true;

        // Only allow primary support for Phone and Email content types
        if (Phone.CONTENT_ITEM_TYPE.equals(selectedMimeType)) {
            isUniqueMimeType = mIsUniqueNumber;
        } else if (Email.CONTENT_ITEM_TYPE.equals(selectedMimeType)) {
            isUniqueMimeType = mIsUniqueEmail;
        }

        // Checking for previously set default
        if (selectedEntry.isPrimary) {
            menu.add(ContextMenu.NONE, ContextMenuIds.CLEAR_DEFAULT,
                    ContextMenu.NONE, getString(R.string.clear_default));
        } else if (!isUniqueMimeType) {
            menu.add(ContextMenu.NONE, ContextMenuIds.SET_DEFAULT,
                    ContextMenu.NONE, getString(R.string.set_default));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
            menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        switch (item.getItemId()) {
            case ContextMenuIds.COPY_TEXT:
                copyToClipboard(menuInfo.position);
                return true;
            case ContextMenuIds.SET_DEFAULT:
                setDefaultContactMethod(mListView.getItemIdAtPosition(menuInfo.position));
                return true;
            case ContextMenuIds.CLEAR_DEFAULT:
                clearDefaultContactMethod(mListView.getItemIdAtPosition(menuInfo.position));
                return true;
            default:
                throw new IllegalArgumentException("Unknown menu option " + item.getItemId());
        }
    }

    private void setDefaultContactMethod(long id) {
        Intent setIntent = ContactSaveService.createSetSuperPrimaryIntent(mContext, id);
        mContext.startService(setIntent);
    }

    private void clearDefaultContactMethod(long id) {
        Intent clearIntent = ContactSaveService.createClearPrimaryIntent(mContext, id);
        mContext.startService(clearIntent);
    }

    private void copyToClipboard(int viewEntryPosition) {
        // Getting the text to copied
        DetailViewEntry detailViewEntry = (DetailViewEntry) mAllEntries.get(viewEntryPosition);
        CharSequence textToCopy = detailViewEntry.data;

        // Checking for empty string
        if (TextUtils.isEmpty(textToCopy)) return;

        ClipboardUtils.copyText(getActivity(), detailViewEntry.typeString, textToCopy, true);
    }

    @Override
    public boolean handleKeyDown(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                     if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
                        TelephonyManager telephonyManager =
                            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE1);
                        TelephonyManager telephonyManager2 =
                            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE2);
                        if ( (telephonyManager != null &&
                                telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) ||
                                (telephonyManager2 != null &&
                                telephonyManager2.getCallState() != TelephonyManager.CALL_STATE_IDLE) ) {
                            // Skip out and let the key be handled at a higher level
                            break;
                        }
                    }else {
                        TelephonyManager telephonyManager =
                            (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE1);
                        if (telephonyManager != null &&
                                telephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                            // Skip out and let the key be handled at a higher level
                            break;
                        }
                    }

                int index = mListView.getSelectedItemPosition();
                if (index != -1) {
                    final DetailViewEntry entry = (DetailViewEntry) mAdapter.getItem(index);
                    if (entry != null && entry.intent != null &&
                            entry.intent.getAction() == Intent.ACTION_CALL_PRIVILEGED) {
                        mContext.startActivity(entry.intent);
                        return true;
                    }
                } else if (mPrimaryPhoneUri != null) {
                    // There isn't anything selected, call the default number
                    if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
                        Intent intent = new Intent();
                        intent.setClassName("com.android.dialer", "com.android.dialer.PhoneSelect");
                        intent.setData(mPrimaryPhoneUri);
                        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                        mContext.startActivity(intent);
                    }else {
                        mContext.startActivity(CallUtil.getCallIntent(mPrimaryPhoneUri));
                    }
                    return true;
                }
                return false;
            }
        }

        return false;
    }

    /**
     * Base class for QuickFixes. QuickFixes quickly fix issues with the Contact without
     * requiring the user to go to the editor. Example: Add to My Contacts.
     */
    private static abstract class QuickFix {
        public abstract boolean isApplicable();
        public abstract String getTitle();
        public abstract void execute();
    }

    private class AddToMyContactsQuickFix extends QuickFix {
        @Override
        public boolean isApplicable() {
            // Only local contacts
            if (mContactData == null || mContactData.isDirectoryEntry()) return false;

            // User profile cannot be added to contacts
            if (mContactData.isUserProfile()) return false;

            // Only if exactly one raw contact
            if (mContactData.getRawContacts().size() != 1) return false;

            // test if the default group is assigned
            final List<GroupMetaData> groups = mContactData.getGroupMetaData();

            // For accounts without group support, groups is null
            if (groups == null) return false;

            // remember the default group id. no default group? bail out early
            final long defaultGroupId = getDefaultGroupId(groups);
            if (defaultGroupId == -1) return false;

            final RawContact rawContact = (RawContact) mContactData.getRawContacts().get(0);
            final AccountType type = rawContact.getAccountType(getContext());
            // Offline or non-writeable account? Nothing to fix
            if (type == null || !type.areContactsWritable()) return false;

            // Check whether the contact is in the default group
            boolean isInDefaultGroup = false;
            for (DataItem dataItem : Iterables.filter(
                    rawContact.getDataItems(), GroupMembershipDataItem.class)) {
                GroupMembershipDataItem groupMembership = (GroupMembershipDataItem) dataItem;
                final Long groupId = groupMembership.getGroupRowId();
                if (groupId != null && groupId == defaultGroupId) {
                    isInDefaultGroup = true;
                    break;
                }
            }

            return !isInDefaultGroup;
        }

        @Override
        public String getTitle() {
            return getString(R.string.add_to_my_contacts);
        }

        @Override
        public void execute() {
            final long defaultGroupId = getDefaultGroupId(mContactData.getGroupMetaData());
            // there should always be a default group (otherwise the button would be invisible),
            // but let's be safe here
            if (defaultGroupId == -1) return;

            // add the group membership to the current state
            final RawContactDeltaList contactDeltaList = mContactData.createRawContactDeltaList();
            final RawContactDelta rawContactEntityDelta = contactDeltaList.get(0);

            final AccountTypeManager accountTypes = AccountTypeManager.getInstance(mContext);
            final AccountType type = rawContactEntityDelta.getAccountType(accountTypes);
            final DataKind groupMembershipKind = type.getKindForMimetype(
                    GroupMembership.CONTENT_ITEM_TYPE);
            final ValuesDelta entry = RawContactModifier.insertChild(rawContactEntityDelta,
                    groupMembershipKind);
            entry.setGroupRowId(defaultGroupId);

            // and fire off the intent. we don't need a callback, as the database listener
            // should update the ui
            final Intent intent = ContactSaveService.createSaveContactIntent(getActivity(),
                    contactDeltaList, "", 0, false, getActivity().getClass(),
                    Intent.ACTION_VIEW, null);
            getActivity().startService(intent);
        }
    }

    private class MakeLocalCopyQuickFix extends QuickFix {
        @Override
        public boolean isApplicable() {
            // Not a directory contact? Nothing to fix here
            if (mContactData == null || !mContactData.isDirectoryEntry()) return false;

            // No export support? Too bad
            if (mContactData.getDirectoryExportSupport() == Directory.EXPORT_SUPPORT_NONE) {
                return false;
            }

            return true;
        }

        @Override
        public String getTitle() {
            return getString(R.string.menu_copyContact);
        }

        @Override
        public void execute() {
            if (mListener == null) {
                return;
            }

            int exportSupport = mContactData.getDirectoryExportSupport();
            switch (exportSupport) {
                case Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY: {
                    createCopy(new AccountWithDataSet(mContactData.getDirectoryAccountName(),
                                    mContactData.getDirectoryAccountType(), null));
                    break;
                }
                case Directory.EXPORT_SUPPORT_ANY_ACCOUNT: {
                    final List<AccountWithDataSet> accounts = new ArrayList<AccountWithDataSet>(AccountTypeManager.getInstance(mContext).getAccounts(true));
                    Log.d(TAG, "MakeLocalCopyQuickFix.execute(): size before remove SIM accounts = " + accounts.size());
                    accounts.remove(new AccountWithDataSet(
                            BrcmIccUtils.ACCOUNT_NAME_SIM1, BrcmIccUtils.ACCOUNT_TYPE_SIM, BrcmIccUtils.ACCOUNT_NAME_SIM1));
                    if(SystemProperties.getInt("ro.dual.sim.phone", 0) == 1) {
                        accounts.remove(new AccountWithDataSet(
                                BrcmIccUtils.ACCOUNT_NAME_SIM2, BrcmIccUtils.ACCOUNT_TYPE_SIM, BrcmIccUtils.ACCOUNT_NAME_SIM2));
                    }
                    Log.d(TAG, "MakeLocalCopyQuickFix.execute(): size after remove SIM accounts = " + accounts.size());

                    if (accounts.isEmpty()) {
                        createCopy(null);
                        return;  // Don't show a dialog.
                    }

                    // In the common case of a single writable account, auto-select
                    // it without showing a dialog.
                    if (accounts.size() == 1) {
                        createCopy(accounts.get(0));
                        return;  // Don't show a dialog.
                    }

                    SelectAccountDialogFragment.show(getFragmentManager(),
                            ContactDetailFragment.this, R.string.dialog_new_contact_account,
                            AccountListFilter.ACCOUNTS_CONTACT_WRITABLE_WITHOUT_SIM,
                            null);
                    break;
                }
            }
        }
    }

    /**
     * This class loads the correct padding values for a contact detail item so they can be applied
     * dynamically. For example, this supports the case where some detail items can be indented and
     * need extra padding.
     */
    private static class ViewEntryDimensions {

        private final int mWidePaddingLeft;
        private final int mPaddingLeft;
        private final int mPaddingRight;
        private final int mPaddingTop;
        private final int mPaddingBottom;

        public ViewEntryDimensions(Resources resources) {
            mPaddingLeft = resources.getDimensionPixelSize(
                    R.dimen.detail_item_side_margin);
            mPaddingTop = resources.getDimensionPixelSize(
                    R.dimen.detail_item_vertical_margin);
            mWidePaddingLeft = mPaddingLeft +
                    resources.getDimensionPixelSize(R.dimen.detail_item_icon_margin) +
                    resources.getDimensionPixelSize(R.dimen.detail_network_icon_size);
            mPaddingRight = mPaddingLeft;
            mPaddingBottom = mPaddingTop;
        }

        public int getWidePaddingLeft() {
            return mWidePaddingLeft;
        }

        public int getPaddingLeft() {
            return mPaddingLeft;
        }

        public int getPaddingRight() {
            return mPaddingRight;
        }

        public int getPaddingTop() {
            return mPaddingTop;
        }

        public int getPaddingBottom() {
            return mPaddingBottom;
        }
    }

    public static interface Listener {
        /**
         * User clicked a single item (e.g. mail). The intent passed in could be null.
         */
        public void onItemClicked(Intent intent);

        /**
         * User requested creation of a new contact with the specified values.
         *
         * @param values ContentValues containing data rows for the new contact.
         * @param account Account where the new contact should be created.
         */
        public void onCreateRawContactRequested(ArrayList<ContentValues> values,
                AccountWithDataSet account);
    }

    /**
     * Adapter for the invitable account types; used for the invitable account type list popup.
     */
    private final static class InvitableAccountTypesAdapter extends BaseAdapter {
        private final Context mContext;
        private final LayoutInflater mInflater;
        private final ArrayList<AccountType> mAccountTypes;

        public InvitableAccountTypesAdapter(Context context, Contact contactData) {
            mContext = context;
            mInflater = LayoutInflater.from(context);
            final List<AccountType> types = contactData.getInvitableAccountTypes();
            mAccountTypes = new ArrayList<AccountType>(types.size());

            for (int i = 0; i < types.size(); i++) {
                mAccountTypes.add(types.get(i));
            }

            Collections.sort(mAccountTypes, new AccountType.DisplayLabelComparator(mContext));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View resultView =
                    (convertView != null) ? convertView
                    : mInflater.inflate(R.layout.account_selector_list_item, parent, false);

            final TextView text1 = (TextView)resultView.findViewById(android.R.id.text1);
            final TextView text2 = (TextView)resultView.findViewById(android.R.id.text2);
            final ImageView icon = (ImageView)resultView.findViewById(android.R.id.icon);

            final AccountType accountType = mAccountTypes.get(position);

            CharSequence action = accountType.getInviteContactActionLabel(mContext);
            CharSequence label = accountType.getDisplayLabel(mContext);
            if (TextUtils.isEmpty(action)) {
                text1.setText(label);
                text2.setVisibility(View.GONE);
            } else {
                text1.setText(action);
                text2.setVisibility(View.VISIBLE);
                text2.setText(label);
            }
            icon.setImageDrawable(accountType.getDisplayIcon(mContext));

            return resultView;
        }

        @Override
        public int getCount() {
            return mAccountTypes.size();
        }

        @Override
        public AccountType getItem(int position) {
            return mAccountTypes.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}
