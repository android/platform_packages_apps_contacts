/*
 * Copyright (c) 2013-2017, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.list;

import android.accounts.Account;
import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.content.Intent;
import android.content.AsyncQueryHandler;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.AbsListView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.ImageView;

import com.android.contacts.activities.MultiPickContactsActivity;
import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.format.TextHighlighter;
import com.android.contacts.list.AccountFilterActivity;
import com.android.contacts.list.ContactsSectionIndexer;
import com.android.contacts.list.ContactListFilter;
import com.android.contacts.SimContactsConstants;
import com.android.contacts.util.UriUtils;
import com.android.contacts.list.ContactsPickMode;
import com.android.contacts.list.OnCheckListActionListener;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.R;

import java.util.ArrayList;

public class ContactsFragment extends ListFragment {
    private final static String TAG = "ContactsFragment";
    private final static boolean DEBUG = true;
    private static final String SORT_ORDER = " desc";

    static final String[] CONTACTS_SUMMARY_PROJECTION = new String[] {
            Contacts._ID, // 0
            Contacts.NAME_RAW_CONTACT_ID, // 1
            Contacts.LOOKUP_KEY, // 2
            Contacts.DISPLAY_NAME_PRIMARY, // 3
            Contacts.PHOTO_ID, // 4
            Contacts.PHOTO_THUMBNAIL_URI, // 5
            RawContacts.ACCOUNT_TYPE, // 6
            RawContacts.ACCOUNT_NAME, // 7
    };
    static final String CONTACTS_SELECTION = Contacts.IN_VISIBLE_GROUP + "=1";
    static final String LOCAL_SELECTION = RawContacts.ACCOUNT_TYPE + " IS NULL ";

    private static final String[] DATA_PROJECTION = new String[] {
            Data._ID, // 0
            Data.CONTACT_ID, // 1
            Contacts.LOOKUP_KEY, // 2
            Data.DISPLAY_NAME, // 3
            Contacts.PHOTO_ID,// 4
            Contacts.PHOTO_THUMBNAIL_URI, // 5
            RawContacts.ACCOUNT_TYPE, // 6
            RawContacts.ACCOUNT_NAME, // 7
            Data.DATA1, // 8 Phone.NUMBER, Email.address
            Data.DATA2, // 9 phone.type
            Data.DATA3, // 10 Phone.LABEL
            Data.MIMETYPE, //11
    };

    // contacts column
    private static final int SUMMARY_ID_COLUMN_INDEX = 0;
    private static final int SUMMARY_COLUMN_CONTACT_ID = 1;
    private static final int SUMMARY_LOOKUP_KEY_COLUMN_INDEX = 2;
    private static final int SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX = 3;
    private static final int SUMMARY_PHOTO_ID_COLUMN_INDEX = 4;
    private static final int SUMMARY_CONTACT_COLUMN_PHOTO_URI = 5;
    private static final int SUMMARY_ACCOUNT_TYPE = 6;
    private static final int SUMMARY_ACCOUNT_NAME = 7;

    private static final int DATA_DATA1_COLUMN = 8;
    private static final int DATA_DATA2_COLUMN = 9;  //phone.type
    private static final int DATA_DATA3_COLUMN = 10; //Phone.LABEL
    private static final int DATA_MIMETYPE_COLUMN = 11;

    private static final int QUERY_TOKEN = 43;

    private int subscription;
    private QueryHandler mQueryHandler;
    private Bundle mChoiceSet;
    private TextView mSelectAllLabel;
    private Intent mIntent;
    private Context mContext;
    private ContactsPickMode mPickMode;
    private int mMode;
    private OnCheckListActionListener mCheckListListener;
    private ContactItemListAdapter mContactListAdapter;
    private String query;
    private View mRootView;
    private SectionIndexer mIndexer;
    private View mHeaderView;
    private ContactListFilter mFilter;

    /**
     * An item view is displayed differently depending on whether it is placed at the beginning,
     * middle or end of a section. It also needs to know the section header when it is at the
     * beginning of a section. This object captures all this configuration.
     */
    public static final class Placement {
        private int position = ListView.INVALID_POSITION;
        public boolean firstInSection;
        public boolean lastInSection;
        public String sectionHeader;

        public void invalidate() {
            position = ListView.INVALID_POSITION;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPickMode = ContactsPickMode.getInstance();
        mMode = mPickMode.getMode();
        mFilter = (ContactListFilter) mPickMode.getIntent().getParcelableExtra(
                AccountFilterActivity.EXTRA_CONTACT_LIST_FILTER);
        if (mFilter == null)
            mFilter = ContactListFilter
            .restoreDefaultPreferences(PreferenceManager
                    .getDefaultSharedPreferences(mContext));
        if (mContactListAdapter == null) {
            mContactListAdapter = new ContactItemListAdapter(mContext);
        }
        mHeaderView = new View(mContext);
        AbsListView.LayoutParams layoutParams = new AbsListView.LayoutParams(
                AbsListView.LayoutParams.MATCH_PARENT,
                (int)(mContext.getResources().getDimension(R.dimen.header_listview_height)));
        mHeaderView.setLayoutParams(layoutParams);
        getListView().addHeaderView(mHeaderView, null, false);
        setListAdapter(mContactListAdapter);
        mQueryHandler = new QueryHandler(mContext);
        startQuery();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = (MultiPickContactsActivity) activity;
    }

    @Override
    public void onResume() {
        // ContactsPickMode is singleton, its mode may be changed by other mode.
        // need to reset
        mPickMode.setMode(mMode);
        super.onResume();
    }

    @Override
    public void onStop() {
        mMode = mPickMode.getMode();
        super.onStop();
    }

    public void setCheckListListener(OnCheckListActionListener checkListListener) {
        mCheckListListener = checkListListener;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        mCheckListListener.onHideSoftKeyboard();
        CheckBox checkBox = (CheckBox) v.findViewById(R.id.pick_contact_check);
        boolean isChecked = !checkBox.isChecked();
        checkBox.setChecked(isChecked);
        ContactItemCache cache = (ContactItemCache) v.getTag();
        String key = String.valueOf(cache.id);
        if (!mCheckListListener.onContainsKey(key)) {
            String[] value = null;
            if (mPickMode.isPickContact()) {
                value = new String[] {
                        cache.lookupKey, key,
                        String.valueOf(cache.nameRawContactId),
                        cache.photoUri == null ? null : String
                                .valueOf(cache.photoUri), cache.name,
                                cache.accountType, cache.accountName};
            } else if (mPickMode.isPickPhone()) {
                value = new String[] { cache.name, cache.number, cache.type,
                        cache.label, cache.contact_id };
            } else if (mPickMode.isPickEmail()) {
                value = new String[] {cache.name, cache.email};
            }
            mCheckListListener.putValue(key, value);
        } else {
            mCheckListListener.onRemove(key);
        }
        mCheckListListener.onUpdateActionBar();
        mCheckListListener.exitSearch();
        mContactListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onDestroy() {
        mQueryHandler.removeCallbacksAndMessages(QUERY_TOKEN);

        if (mContactListAdapter.getCursor() != null) {
            mContactListAdapter.getCursor().close();
        }
        super.onDestroy();
    }

    private Uri getUriToQuery() {
        Uri uri;
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_DEFAULT_CONTACT:
            case ContactsPickMode.MODE_SEARCH_CONTACT:
                uri = Contacts.CONTENT_URI;
                break;
            case ContactsPickMode.MODE_DEFAULT_EMAIL:
            case ContactsPickMode.MODE_SEARCH_EMAIL:
                uri = Email.CONTENT_URI;
                break;
            case ContactsPickMode.MODE_DEFAULT_PHONE:
            case ContactsPickMode.MODE_SEARCH_PHONE:
                uri = Phone.CONTENT_URI;
                uri = uri.buildUpon().appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                        String.valueOf(Directory.DEFAULT))
                        .appendQueryParameter(ContactsContract.REMOVE_DUPLICATE_ENTRIES, "true")
                        .build();
                break;
            default:
                uri = Contacts.CONTENT_URI;
        }
        return uri.buildUpon().appendQueryParameter(Contacts.EXTRA_ADDRESS_BOOK_INDEX, "true")
                .build();
    }

    private Uri getFilterUri() {
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_SEARCH_CONTACT:
                return Contacts.CONTENT_FILTER_URI;
            case ContactsPickMode.MODE_SEARCH_PHONE:
                return Phone.CONTENT_FILTER_URI;
            case ContactsPickMode.MODE_SEARCH_EMAIL:
                return Email.CONTENT_FILTER_URI;
            default:
                log("getFilterUri: Incorrect mode: " + mPickMode.getMode());
        }
        return Contacts.CONTENT_FILTER_URI;
    }

    public String[] getProjectionForQuery() {
        switch (mPickMode.getMode()) {
            case ContactsPickMode.MODE_DEFAULT_CONTACT:
            case ContactsPickMode.MODE_SEARCH_CONTACT:
                return CONTACTS_SUMMARY_PROJECTION;
            case ContactsPickMode.MODE_DEFAULT_PHONE:
            case ContactsPickMode.MODE_SEARCH_PHONE:
            case ContactsPickMode.MODE_DEFAULT_EMAIL:
            case ContactsPickMode.MODE_SEARCH_EMAIL:
                return DATA_PROJECTION;
            default:
                log("getProjectionForQuery: Incorrect mode: " + mPickMode.getMode());
        }
        return CONTACTS_SUMMARY_PROJECTION;
    }

    private String getSortOrder(String[] projection) {
        return RawContacts.SORT_KEY_PRIMARY;
    }

    private String getSelectionForAccount() {
        @SuppressWarnings("deprecation")
        StringBuilder selection = new StringBuilder();
        if(mFilter == null)
            return null;
        switch (mFilter.filterType) {
            case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS:
                return null;
            case ContactListFilter.FILTER_TYPE_CUSTOM:
                return CONTACTS_SELECTION;
            case ContactListFilter.FILTER_TYPE_ACCOUNT:
                if (mPickMode.isSearchMode())
                    selection.append(RawContacts.ACCOUNT_TYPE).append("='")
                        .append(mFilter.accountType).append("' AND ")
                        .append(RawContacts.ACCOUNT_NAME).append("='")
                        .append(mFilter.accountName).append("'");
                return selection.toString();
            case ContactListFilter.FILTER_TYPE_DEVICE_CONTACTS:
                return LOCAL_SELECTION;
        }
        return null;
    }

    public void startQuery() {
        Uri uri = getUriToQuery();
        if (mFilter != null
                && mFilter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
            // We should exclude the invisiable contacts.
            uri = uri.buildUpon()
                    .appendQueryParameter(RawContacts.ACCOUNT_NAME,
                            mFilter.accountName)
                    .appendQueryParameter(RawContacts.ACCOUNT_TYPE,
                            mFilter.accountType)
                    .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                            ContactsContract.Directory.DEFAULT + "").build();
        }

        String[] projection = getProjectionForQuery();
        String selection = getSelectionForAccount();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection, null,
                getSortOrder(projection));
    }

    public void doFilter(String s) {
        query = s;
        if (TextUtils.isEmpty(s)) {
            mContactListAdapter.changeCursor(null);
            return;
        }
        Uri uri = Uri.withAppendedPath(getFilterUri(), Uri.encode(query));
        String[] projection = getProjectionForQuery();
        String selection = getSelectionForAccount();
        mQueryHandler.startQuery(QUERY_TOKEN, null, uri, projection, selection, null,
                getSortOrder(projection));
    }

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(Context context) {
            super(context.getContentResolver());
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (mHeaderView != null && mPickMode.isSearchMode()) {
                getListView().removeHeaderView(mHeaderView);
            }
            mContactListAdapter.changeCursor(cursor);
        }
    }

    private class ContactItemCache {
        long id;
        String name;
        String number;
        String lookupKey;
        String type;
        String label;
        String contact_id;
        String email;
        String accountType;
        String accountName;
        long nameRawContactId;
        Uri photoUri;
        Uri contactUri;
        long photoId;
    }

    public class ContactItemListAdapter extends CursorAdapter
            implements SectionIndexer {
        protected LayoutInflater mInflater;
        private ContactPhotoManager mContactPhotoManager;
        private final TextHighlighter mTextHighlighter;
        private Placement mPlacement = new Placement();

        public ContactItemListAdapter(Context context) {
            super(context, null, false);
            mInflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            mContactPhotoManager = ContactPhotoManager.getInstance(mContext);
            mTextHighlighter = new TextHighlighter(Typeface.BOLD);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContactItemCache cache = (ContactItemCache) view.getTag();
            if (mPickMode.isPickContact()) {
                cache.id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                cache.lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                cache.nameRawContactId = cursor.getLong(SUMMARY_COLUMN_CONTACT_ID);
                cache.accountType = cursor.getString(SUMMARY_ACCOUNT_TYPE);
                cache.accountName = cursor.getString(SUMMARY_ACCOUNT_NAME);
                ((TextView) view.findViewById(R.id.pick_contact_name))
                        .setText(cache.name == null ? "" : cache.name);
                view.findViewById(R.id.pick_contact_number).setVisibility(View.GONE);
                setPhotoView(view, cursor, cache);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            } else if (mPickMode.isPickPhone()) {
                cache.id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                cache.number = cursor.getString(DATA_DATA1_COLUMN);
                cache.label = cursor.getString(DATA_DATA3_COLUMN);
                cache.type = String.valueOf(cursor.getInt(DATA_DATA2_COLUMN));
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.number);
                setPhotoView(view, cursor, cache);
                setItemView(view, cursor, cache);
                setLabel(view, cursor);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            } else if (mPickMode.isPickEmail()) {
                cache.id = cursor.getLong(SUMMARY_ID_COLUMN_INDEX);
                cache.name = cursor.getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                cache.email = cursor.getString(DATA_DATA1_COLUMN);
                ((TextView) view.findViewById(R.id.pick_contact_number)).setText(cache.email);
                setPhotoView(view, cursor, cache);
                setItemView(view, cursor, cache);
                setLabel(view, cursor);
                setHeaderAndHighLightIfNeed(view, cache, cursor);
            }

            CheckBox checkBox = (CheckBox) view.findViewById(R.id.pick_contact_check);
            if (mCheckListListener.onContainsKey(String.valueOf(cache.id))) {
                checkBox.setChecked(true);
            } else {
                checkBox.setChecked(false);
            }
        }

        private void setHeaderAndHighLightIfNeed(View view, ContactItemCache cache,
                Cursor cursor) {
            if (mPickMode.isSearchMode()) {
                hideSectionHeader(view);
                if (!TextUtils.isEmpty(query)) {
                    setFilterHighLight(view, cache);
                }
            } else {
                bindSectionHeader(view, cursor.getPosition());
            }
        }

        private void setFilterHighLight(View view, ContactItemCache cache) {
            TextView nameView = (TextView) view.findViewById(R.id.pick_contact_name);
            CharSequence nameText = cache.name;
            nameText = mTextHighlighter.applyPrefixHighlight(nameText, query.toUpperCase());
            nameView.setText(nameText);
            TextView numberView = (TextView) view.findViewById(R.id.pick_contact_number);
            if (mPickMode.isPickEmail()) {
                CharSequence emailText = cache.email;
                emailText = mTextHighlighter.applyPrefixHighlight(emailText, query.toUpperCase());
                numberView.setText(emailText);
            } else {
                CharSequence numberText = cache.number;
                numberText = mTextHighlighter.applyPrefixHighlight(numberText, query.toUpperCase());
                numberView.setText(numberText);
            }
        }

        private void setLabel(View view, Cursor cursor) {
            TextView labelView = (TextView) view.findViewById(R.id.label);
            CharSequence label = null;
            if (!cursor.isNull(DATA_DATA2_COLUMN)) {
                final int type = cursor.getInt(DATA_DATA2_COLUMN);
                final String customLabel = cursor.getString(DATA_DATA3_COLUMN);
                label = Phone.getTypeLabel(mContext.getResources(), type, customLabel);
            }
            labelView.setText(label);
        }

        private void setItemView(View view, Cursor cursor, ContactItemCache cache) {
            ImageView photoView = (ImageView) view
                    .findViewById(R.id.pick_contact_photo);
            boolean isFirstEntry = true;
            if (!cursor.isNull(SUMMARY_COLUMN_CONTACT_ID)) {
                long currentContactId = cursor.getLong(SUMMARY_COLUMN_CONTACT_ID);
                int position = cursor.getPosition();
                if (!cursor.isFirst() && cursor.moveToPrevious()) {
                    if (!cursor.isNull(SUMMARY_COLUMN_CONTACT_ID)) {
                        final long previousContactId = cursor.getLong(SUMMARY_COLUMN_CONTACT_ID);
                        if (currentContactId == previousContactId) {
                            isFirstEntry = false;
                        }
                    }
                }
                cursor.moveToPosition(position);
            }

            if (isFirstEntry) {
                view.getLayoutParams().height = mContext.getResources()
                        .getDimensionPixelSize(R.dimen.pick_contact_first_item_height);
                photoView.setVisibility(View.VISIBLE);
                view.findViewById(R.id.pick_contact_name).setVisibility(View.VISIBLE);
                ((TextView) view.findViewById(R.id.pick_contact_name))
                        .setText(cache.name == null ? "" : cache.name);
            } else {
                view.getLayoutParams().height = mContext.getResources()
                        .getDimensionPixelSize(R.dimen.pick_contact_same_item_height);
                photoView.setVisibility(View.INVISIBLE);
                view.findViewById(R.id.pick_contact_name).setVisibility(View.GONE);
            }
        }

        private void setPhotoView(View view, Cursor cursor, ContactItemCache cache) {
            ImageView photoView = ((ImageView) view
                    .findViewById(R.id.pick_contact_photo));
            photoView.setVisibility(View.VISIBLE);

            if (!cursor.isNull(SUMMARY_PHOTO_ID_COLUMN_INDEX)) {
                cache.photoId = cursor.getLong(SUMMARY_PHOTO_ID_COLUMN_INDEX);
            } else {
                cache.photoId = 0;
            }
            if (!cursor.isNull(SUMMARY_CONTACT_COLUMN_PHOTO_URI)) {
                cache.photoUri = UriUtils.parseUriOrNull(cursor
                        .getString(SUMMARY_CONTACT_COLUMN_PHOTO_URI));
            } else {
                cache.photoUri = null;
            }
            Account account = null;
            if (!cursor.isNull(SUMMARY_ACCOUNT_TYPE)
                    && !cursor.isNull(SUMMARY_ACCOUNT_NAME)) {
                final String accountType = cursor
                        .getString(SUMMARY_ACCOUNT_TYPE);
                final String accountName = cursor
                        .getString(SUMMARY_ACCOUNT_NAME);
                account = new Account(accountName, accountType);
            }

            if (cache.photoId != 0) {
                mContactPhotoManager.loadThumbnail(photoView, cache.photoId, account, false, true,
                        null);
            } else {
                final Uri photoUri = cache.photoUri == null ? null : cache.photoUri;
                DefaultImageRequest request = null;
                if (photoUri == null) {
                    cache.lookupKey = cursor.getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                    request = new DefaultImageRequest(cache.name, cache.lookupKey, true);
                }
                mContactPhotoManager.loadDirectoryPhoto(photoView, photoUri, account, false, true,
                        request);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = null;
            v = mInflater.inflate(R.layout.multi_pick_contact_item, parent, false);
            ContactItemCache dataCache = new ContactItemCache();
            v.setTag(dataCache);
            return v;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            View v;
            if (!getCursor().moveToPosition(position)) {
                throw new IllegalStateException("couldn't move cursor to position " + position);
            }
            if (convertView != null && convertView.getTag() != null) {
                v = convertView;
            } else {
                v = newView(mContext, getCursor(), parent);
            }
            bindView(v, mContext, getCursor());
            return v;
        }

        @Override
        public void changeCursor(Cursor cursor) {
            super.changeCursor(cursor);
            updateIndexer(cursor);
        }

        private void bindSectionHeader(View view, int position) {
            TextView section = (TextView) view.findViewById(R.id.section_index);
            section.setVisibility(View.VISIBLE);
            Placement placement = getItemPlacementInSection(position);
            section.setText(placement.sectionHeader);
            section.setTextAppearance(mContext, R.style.SectionHeaderStyle);
        }

        private void hideSectionHeader(View view) {
            TextView section = (TextView) view.findViewById(R.id.section_index);
            section.setVisibility(View.GONE);
        }

        /**
         * Updates the indexer, which is used to produce section headers.
         */
        private void updateIndexer(Cursor cursor) {
            if (cursor == null) {
                setIndexer(null);
                return;
            }
            Bundle bundle = cursor.getExtras();
            if (bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES)
                    && bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS)) {
                String sections[] = bundle.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
                int counts[] = bundle.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);
                setIndexer(new ContactsSectionIndexer(sections, counts));
            } else {
                setIndexer(null);
            }
        }

        public void setIndexer(SectionIndexer index) {
            mIndexer = index;
            mPlacement.invalidate();
        }

        public int getPositionForSection(int sectionIndex) {
            if (mIndexer != null) {
                return mIndexer.getPositionForSection(sectionIndex);
            }
            return -1;
        }

        public Object[] getSections() {
            if (mIndexer != null) {
                return mIndexer.getSections();
            } else {
                return new String[] {" "};
            }
        }

        public int getSectionForPosition(int position) {
            if (mIndexer != null) {
                return mIndexer.getSectionForPosition(position);
            }
            return -1;
        }

        /**
         * Computes the item's placement within its section and populates the {@code placement}
         * object accordingly. Please note that the returned object is volatile and should be copied
         * if the result needs to be used later.
         */
        public Placement getItemPlacementInSection(int position) {
            if (mPlacement.position == position) {
                return mPlacement;
            }
            mPlacement.position = position;
            int section = getSectionForPosition(position);
            if (section != -1 && getPositionForSection(section) == position) {
                mPlacement.firstInSection = true;
                mPlacement.sectionHeader = (String) getSections()[section];
            } else {
                mPlacement.firstInSection = false;
                mPlacement.sectionHeader = null;
            }

            mPlacement.lastInSection = (getPositionForSection(section + 1) - 1 == position);
            return mPlacement;
        }
    }

    protected static void log(String msg) {
        if (DEBUG)
            Log.d(TAG, msg);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mRootView = inflater.inflate(R.layout.multi_pick_contacts_fragment, container, false);
        return mRootView;
    }

    /**
     * @param isSelectedAll isSelectedAll is true, selected all contacts
     * isSelectedAll is False, deselected all contacts
     */
    public void setSelectedAll(boolean isSelectedAll) {
        Cursor cursor = mContactListAdapter.getCursor();
        if (cursor == null) {
            return;
        }
        ContactItemCache cache = new ContactItemCache();
        String key;
        // selected all contacts
        if (isSelectedAll) {
            int count = cursor.getCount();
            for (int i = 0; i < count; i++) {
                cursor.moveToPosition(i);
                key = String.valueOf(cursor.getLong(0));
                if (!mCheckListListener.onContainsKey(key)) {
                    String[] value = null;
                    if (mPickMode.isPickContact()) {
                        cache.lookupKey = cursor
                                .getString(SUMMARY_LOOKUP_KEY_COLUMN_INDEX);
                        cache.name = cursor
                                .getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                        cache.nameRawContactId = cursor
                                .getLong(SUMMARY_COLUMN_CONTACT_ID);
                        String photoUri = cursor.getString(SUMMARY_CONTACT_COLUMN_PHOTO_URI);
                        cache.accountType = cursor.getString(SUMMARY_ACCOUNT_TYPE);
                        cache.accountName = cursor.getString(SUMMARY_ACCOUNT_NAME);
                        cache.photoUri = UriUtils.parseUriOrNull(photoUri);
                        value = new String[] { cache.lookupKey, key,
                                String.valueOf(cache.nameRawContactId),
                                photoUri, cache.name, cache.accountType, cache.accountName};
                    } else if (mPickMode.isPickPhone()) {
                        cache.name = cursor
                                .getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                        cache.number = cursor.getString(DATA_DATA1_COLUMN);
                        cache.label = cursor.getString(DATA_DATA3_COLUMN);
                        cache.type = String.valueOf(cursor
                                .getInt(DATA_DATA2_COLUMN));
                        value = new String[] { cache.name, cache.number,
                                cache.type, cache.label, cache.contact_id };
                    } else if (mPickMode.isPickEmail()) {
                        cache.name = cursor
                                .getString(SUMMARY_DISPLAY_NAME_PRIMARY_COLUMN_INDEX);
                        cache.email = cursor.getString(DATA_DATA1_COLUMN);
                        value = new String[] { cache.name, cache.email };
                    }
                    mCheckListListener.putValue(key, value);
                }
            }
        } else {
            mCheckListListener.onClear();
        }
        // update actionbar selected button to display selected item numbers
        mCheckListListener.onUpdateActionBar();
        mContactListAdapter.notifyDataSetChanged();
    }

}
