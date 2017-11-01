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
package com.android.contacts.list;

import android.content.Context;
import android.content.CursorLoader;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactPhotoManager.DefaultImageRequest;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.compat.CompatUtils;
import com.android.contacts.compat.DirectoryCompat;
import com.android.contacts.util.SearchUtil;

import java.util.HashSet;

/**
 * Common base class for various contact-related lists, e.g. contact list, phone number list
 * etc.
 */
public abstract class ContactEntryListAdapter extends IndexerListAdapter {

    private static final String TAG = "ContactEntryListAdapter";

    /**
     * Indicates whether the {@link Directory#LOCAL_INVISIBLE} directory should
     * be included in the search.
     */
    public static final boolean LOCAL_INVISIBLE_DIRECTORY_ENABLED = false;

    private int mDisplayOrder;
    private int mSortOrder;

    private boolean mDisplayPhotos;
    private boolean mCircularPhotos = true;
    private boolean mQuickContactEnabled;
    private boolean mAdjustSelectionBoundsEnabled;

    /**
     * indicates if contact queries include favorites
     */
    private boolean mIncludeFavorites;

    private int mNumberOfFavorites;

    /**
     * The root view of the fragment that this adapter is associated with.
     */
    private View mFragmentRootView;

    private ContactPhotoManager mPhotoLoader;

    private String mQueryString;
    private String mUpperCaseQueryString;
    private boolean mSearchMode;
    private int mDirectorySearchMode;
    private int mDirectoryResultLimit = Integer.MAX_VALUE;

    private boolean mEmptyListEnabled = true;

    private boolean mSelectionVisible;

    private ContactListFilter mFilter;
    private boolean mDarkTheme = false;

    /** Resource used to provide header-text for default filter. */
    private CharSequence mDefaultFilterHeaderText;

    public ContactEntryListAdapter(Context context) {
        super(context);
        setDefaultFilterHeaderText(R.string.local_search_label);
        addPartitions();
    }

    /**
     * @param fragmentRootView Root view of the fragment. This is used to restrict the scope of
     * image loading requests that get cancelled on cursor changes.
     */
    protected void setFragmentRootView(View fragmentRootView) {
        mFragmentRootView = fragmentRootView;
    }

    protected void setDefaultFilterHeaderText(int resourceId) {
        mDefaultFilterHeaderText = getContext().getResources().getText(resourceId);
    }

    @Override
    protected ContactListItemView newView(
            Context context, int partition, Cursor cursor, int position, ViewGroup parent) {
        final ContactListItemView view = new ContactListItemView(context, null);
        view.setIsSectionHeaderEnabled(isSectionHeaderDisplayEnabled());
        view.setAdjustSelectionBoundsEnabled(isAdjustSelectionBoundsEnabled());
        return view;
    }

    @Override
    protected void bindView(View itemView, int partition, Cursor cursor, int position) {
        final ContactListItemView view = (ContactListItemView) itemView;
        view.setIsSectionHeaderEnabled(isSectionHeaderDisplayEnabled());
        bindWorkProfileIcon(view, partition);
    }

    @Override
    protected View createPinnedSectionHeaderView(Context context, ViewGroup parent) {
        return new ContactListPinnedHeaderView(context, null, parent);
    }

    @Override
    protected void setPinnedSectionTitle(View pinnedHeaderView, String title) {
        ((ContactListPinnedHeaderView) pinnedHeaderView).setSectionHeaderTitle(title);
    }

    protected void addPartitions() {
        addPartition(createDefaultDirectoryPartition());
    }

    protected DirectoryPartition createDefaultDirectoryPartition() {
        DirectoryPartition partition = new DirectoryPartition(true, true);
        partition.setDirectoryId(Directory.DEFAULT);
        partition.setDirectoryType(getContext().getString(R.string.contactsList));
        partition.setPriorityDirectory(true);
        partition.setPhotoSupported(true);
        partition.setLabel(mDefaultFilterHeaderText.toString());
        return partition;
    }

    /**
     * Remove all directories after the default directory. This is typically used when contacts
     * list screens are asked to exit the search mode and thus need to remove all remote directory
     * results for the search.
     *
     * This code assumes that the default directory and directories before that should not be
     * deleted (e.g. Join screen has "suggested contacts" directory before the default director,
     * and we should not remove the directory).
     */
    public void removeDirectoriesAfterDefault() {
        final int partitionCount = getPartitionCount();
        for (int i = partitionCount - 1; i >= 0; i--) {
            final Partition partition = getPartition(i);
            if ((partition instanceof DirectoryPartition)
                    && ((DirectoryPartition) partition).getDirectoryId() == Directory.DEFAULT) {
                break;
            } else {
                removePartition(i);
            }
        }
    }

    protected int getPartitionByDirectoryId(long id) {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                if (((DirectoryPartition)partition).getDirectoryId() == id) {
                    return i;
                }
            }
        }
        return -1;
    }

    protected DirectoryPartition getDirectoryById(long id) {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                final DirectoryPartition directoryPartition = (DirectoryPartition) partition;
                if (directoryPartition.getDirectoryId() == id) {
                    return directoryPartition;
                }
            }
        }
        return null;
    }

    public abstract String getContactDisplayName(int position);
    public abstract void configureLoader(CursorLoader loader, long directoryId);

    /**
     * Marks all partitions as "loading"
     */
    public void onDataReload() {
        boolean notify = false;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                if (!directoryPartition.isLoading()) {
                    notify = true;
                }
                directoryPartition.setStatus(DirectoryPartition.STATUS_NOT_LOADED);
            }
        }
        if (notify) {
            notifyDataSetChanged();
        }
    }

    @Override
    public void clearPartitions() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                DirectoryPartition directoryPartition = (DirectoryPartition)partition;
                directoryPartition.setStatus(DirectoryPartition.STATUS_NOT_LOADED);
            }
        }
        super.clearPartitions();
    }

    public boolean isSearchMode() {
        return mSearchMode;
    }

    public void setSearchMode(boolean flag) {
        mSearchMode = flag;
    }

    public String getQueryString() {
        return mQueryString;
    }

    public void setQueryString(String queryString) {
        mQueryString = queryString;
        if (TextUtils.isEmpty(queryString)) {
            mUpperCaseQueryString = null;
        } else {
            mUpperCaseQueryString = SearchUtil
                    .cleanStartAndEndOfSearchQuery(queryString.toUpperCase()) ;
        }
    }

    public String getUpperCaseQueryString() {
        return mUpperCaseQueryString;
    }

    public int getDirectorySearchMode() {
        return mDirectorySearchMode;
    }

    public void setDirectorySearchMode(int mode) {
        mDirectorySearchMode = mode;
    }

    public int getDirectoryResultLimit() {
        return mDirectoryResultLimit;
    }

    public int getDirectoryResultLimit(DirectoryPartition directoryPartition) {
        final int limit = directoryPartition.getResultLimit();
        return limit == DirectoryPartition.RESULT_LIMIT_DEFAULT ? mDirectoryResultLimit : limit;
    }

    public void setDirectoryResultLimit(int limit) {
        this.mDirectoryResultLimit = limit;
    }

    public int getContactNameDisplayOrder() {
        return mDisplayOrder;
    }

    public void setContactNameDisplayOrder(int displayOrder) {
        mDisplayOrder = displayOrder;
    }

    public int getSortOrder() {
        return mSortOrder;
    }

    public void setSortOrder(int sortOrder) {
        mSortOrder = sortOrder;
    }

    public void setPhotoLoader(ContactPhotoManager photoLoader) {
        mPhotoLoader = photoLoader;
    }

    protected ContactPhotoManager getPhotoLoader() {
        return mPhotoLoader;
    }

    public boolean getDisplayPhotos() {
        return mDisplayPhotos;
    }

    public void setDisplayPhotos(boolean displayPhotos) {
        mDisplayPhotos = displayPhotos;
    }

    public boolean getCircularPhotos() {
        return mCircularPhotos;
    }

    public void setCircularPhotos(boolean circularPhotos) {
        mCircularPhotos = circularPhotos;
    }

    public boolean isEmptyListEnabled() {
        return mEmptyListEnabled;
    }

    public void setEmptyListEnabled(boolean flag) {
        mEmptyListEnabled = flag;
    }

    public boolean isSelectionVisible() {
        return mSelectionVisible;
    }

    public void setSelectionVisible(boolean flag) {
        this.mSelectionVisible = flag;
    }

    public boolean isQuickContactEnabled() {
        return mQuickContactEnabled;
    }

    public void setQuickContactEnabled(boolean quickContactEnabled) {
        mQuickContactEnabled = quickContactEnabled;
    }

    public boolean isAdjustSelectionBoundsEnabled() {
        return mAdjustSelectionBoundsEnabled;
    }

    public void setAdjustSelectionBoundsEnabled(boolean enabled) {
        mAdjustSelectionBoundsEnabled = enabled;
    }

    public boolean shouldIncludeFavorites() {
        return mIncludeFavorites;
    }

    public void setIncludeFavorites(boolean includeFavorites) {
        mIncludeFavorites = includeFavorites;
    }

    public void setFavoritesSectionHeader(int numberOfFavorites) {
        if (mIncludeFavorites) {
            mNumberOfFavorites = numberOfFavorites;
            setSectionHeader(numberOfFavorites);
        }
    }

    public int getNumberOfFavorites() {
        return mNumberOfFavorites;
    }

    private void setSectionHeader(int numberOfItems) {
        SectionIndexer indexer = getIndexer();
        if (indexer != null) {
            ((ContactsSectionIndexer) indexer).setFavoritesHeader(numberOfItems);
        }
    }

    public void setDarkTheme(boolean value) {
        mDarkTheme = value;
    }

    /**
     * Updates partitions according to the directory meta-data contained in the supplied
     * cursor.
     */
    public void changeDirectories(Cursor cursor) {
        if (cursor.getCount() == 0) {
            // Directory table must have at least local directory, without which this adapter will
            // enter very weird state.
            Log.e(TAG, "Directory search loader returned an empty cursor, which implies we have " +
                    "no directory entries.", new RuntimeException());
            return;
        }
        HashSet<Long> directoryIds = new HashSet<Long>();

        int idColumnIndex = cursor.getColumnIndex(Directory._ID);
        int directoryTypeColumnIndex = cursor.getColumnIndex(DirectoryListLoader.DIRECTORY_TYPE);
        int displayNameColumnIndex = cursor.getColumnIndex(Directory.DISPLAY_NAME);
        int photoSupportColumnIndex = cursor.getColumnIndex(Directory.PHOTO_SUPPORT);

        // TODO preserve the order of partition to match those of the cursor
        // Phase I: add new directories
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            long id = cursor.getLong(idColumnIndex);
            directoryIds.add(id);
            if (getPartitionByDirectoryId(id) == -1) {
                DirectoryPartition partition = new DirectoryPartition(false, true);
                partition.setDirectoryId(id);
                if (DirectoryCompat.isRemoteDirectoryId(id)) {
                    if (DirectoryCompat.isEnterpriseDirectoryId(id)) {
                        partition.setLabel(mContext.getString(R.string.directory_search_label_work));
                    } else {
                        partition.setLabel(mContext.getString(R.string.directory_search_label));
                    }
                } else {
                    if (DirectoryCompat.isEnterpriseDirectoryId(id)) {
                        partition.setLabel(mContext.getString(R.string.list_filter_phones_work));
                    } else {
                        partition.setLabel(mDefaultFilterHeaderText.toString());
                    }
                }
                partition.setDirectoryType(cursor.getString(directoryTypeColumnIndex));
                partition.setDisplayName(cursor.getString(displayNameColumnIndex));
                int photoSupport = cursor.getInt(photoSupportColumnIndex);
                partition.setPhotoSupported(photoSupport == Directory.PHOTO_SUPPORT_THUMBNAIL_ONLY
                        || photoSupport == Directory.PHOTO_SUPPORT_FULL);
                addPartition(partition);
            }
        }

        // Phase II: remove deleted directories
        int count = getPartitionCount();
        for (int i = count; --i >= 0; ) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition) {
                long id = ((DirectoryPartition)partition).getDirectoryId();
                if (!directoryIds.contains(id)) {
                    removePartition(i);
                }
            }
        }

        invalidate();
        notifyDataSetChanged();
    }

    @Override
    public void changeCursor(int partitionIndex, Cursor cursor) {
        if (partitionIndex >= getPartitionCount()) {
            // There is no partition for this data
            return;
        }

        Partition partition = getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            ((DirectoryPartition)partition).setStatus(DirectoryPartition.STATUS_LOADED);
        }

        if (mDisplayPhotos && mPhotoLoader != null && isPhotoSupported(partitionIndex)) {
            mPhotoLoader.refreshCache();
        }

        super.changeCursor(partitionIndex, cursor);

        if (isSectionHeaderDisplayEnabled() && partitionIndex == getIndexedPartition()) {
            updateIndexer(cursor);
        }

        // When the cursor changes, cancel any pending asynchronous photo loads.
        mPhotoLoader.cancelPendingRequests(mFragmentRootView);
    }

    public void changeCursor(Cursor cursor) {
        changeCursor(0, cursor);
    }

    /**
     * Updates the indexer, which is used to produce section headers.
     */
    private void updateIndexer(Cursor cursor) {
        if (cursor == null || cursor.isClosed()) {
            setIndexer(null);
            return;
        }

        Bundle bundle = cursor.getExtras();
        if (bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES) &&
                bundle.containsKey(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS)) {
            String sections[] =
                    bundle.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES);
            int counts[] = bundle.getIntArray(
                    Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS);

            if (getExtraStartingSection()) {
                // Insert an additional unnamed section at the top of the list.
                String allSections[] = new String[sections.length + 1];
                int allCounts[] = new int[counts.length + 1];
                for (int i = 0; i < sections.length; i++) {
                    allSections[i + 1] = sections[i];
                    allCounts[i + 1] = counts[i];
                }
                allCounts[0] = 1;
                allSections[0] = "";
                setIndexer(new ContactsSectionIndexer(allSections, allCounts));
            } else {
                setIndexer(new ContactsSectionIndexer(sections, counts));
            }
        } else {
            setIndexer(null);
        }
    }

    protected boolean getExtraStartingSection() {
        return false;
    }

    @Override
    public int getViewTypeCount() {
        // We need a separate view type for each item type, plus another one for
        // each type with header, plus one for "other".
        return getItemViewTypeCount() * 2 + 1;
    }

    @Override
    public int getItemViewType(int partitionIndex, int position) {
        int type = super.getItemViewType(partitionIndex, position);
        if (isSectionHeaderDisplayEnabled() && partitionIndex == getIndexedPartition()) {
            Placement placement = getItemPlacementInSection(position);
            return placement.firstInSection ? type : getItemViewTypeCount() + type;
        } else {
            return type;
        }
    }

    @Override
    public boolean isEmpty() {
        // TODO
//        if (contactsListActivity.mProviderStatus != ProviderStatus.STATUS_NORMAL) {
//            return true;
//        }

        if (!mEmptyListEnabled) {
            return false;
        } else if (isSearchMode()) {
            return TextUtils.isEmpty(getQueryString());
        } else {
            return super.isEmpty();
        }
    }

    public boolean isLoading() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition
                    && ((DirectoryPartition) partition).isLoading()) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllPartitionsEmpty() {
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            if (!isPartitionEmpty(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Changes visibility parameters for the default directory partition.
     */
    public void configureDefaultPartition(boolean showIfEmpty, boolean hasHeader) {
        int defaultPartitionIndex = -1;
        int count = getPartitionCount();
        for (int i = 0; i < count; i++) {
            Partition partition = getPartition(i);
            if (partition instanceof DirectoryPartition &&
                    ((DirectoryPartition)partition).getDirectoryId() == Directory.DEFAULT) {
                defaultPartitionIndex = i;
                break;
            }
        }
        if (defaultPartitionIndex != -1) {
            setShowIfEmpty(defaultPartitionIndex, showIfEmpty);
            setHasHeader(defaultPartitionIndex, hasHeader);
        }
    }

    @Override
    protected View newHeaderView(Context context, int partition, Cursor cursor,
            ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.directory_header, parent, false);
        if (!getPinnedPartitionHeadersEnabled()) {
            // If the headers are unpinned, there is no need for their background
            // color to be non-transparent. Setting this transparent reduces maintenance for
            // non-pinned headers. We don't need to bother synchronizing the activity's
            // background color with the header background color.
            view.setBackground(null);
        }
        return view;
    }

    protected void bindWorkProfileIcon(final ContactListItemView view, int partitionId) {
        final Partition partition = getPartition(partitionId);
        if (partition instanceof DirectoryPartition) {
            final DirectoryPartition directoryPartition = (DirectoryPartition) partition;
            final long directoryId = directoryPartition.getDirectoryId();
            final long userType = ContactsUtils.determineUserType(directoryId, null);
            view.setWorkProfileIconEnabled(userType == ContactsUtils.USER_TYPE_WORK);
        }
    }

    @Override
    protected void bindHeaderView(View view, int partitionIndex, Cursor cursor) {
        Partition partition = getPartition(partitionIndex);
        if (!(partition instanceof DirectoryPartition)) {
            return;
        }

        DirectoryPartition directoryPartition = (DirectoryPartition)partition;
        long directoryId = directoryPartition.getDirectoryId();
        TextView labelTextView = (TextView)view.findViewById(R.id.label);
        TextView displayNameTextView = (TextView)view.findViewById(R.id.display_name);
        labelTextView.setText(directoryPartition.getLabel());
        if (!DirectoryCompat.isRemoteDirectoryId(directoryId)) {
            displayNameTextView.setText(null);
        } else {
            String directoryName = directoryPartition.getDisplayName();
            String displayName = !TextUtils.isEmpty(directoryName)
                    ? directoryName
                    : directoryPartition.getDirectoryType();
            displayNameTextView.setText(displayName);
        }

        final Resources res = getContext().getResources();
        final int headerPaddingTop = partitionIndex == 1 && getPartition(0).isEmpty()?
                0 : res.getDimensionPixelOffset(R.dimen.directory_header_extra_top_padding);
        // There should be no extra padding at the top of the first directory header
        view.setPaddingRelative(view.getPaddingStart(), headerPaddingTop, view.getPaddingEnd(),
                view.getPaddingBottom());
    }

    // Default implementation simply returns number of rows in the cursor.
    // Broken out into its own routine so can be overridden by child classes
    // for eg number of unique contacts for a phone list.
    protected int getResultCount(Cursor cursor) {
        return cursor == null ? 0 : cursor.getCount();
    }

    // TODO: fix PluralRules to handle zero correctly and use Resources.getQuantityText directly
    public String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
        if (count == 0) {
            return getContext().getString(zeroResourceId);
        } else {
            String format = getContext().getResources()
                    .getQuantityText(pluralResourceId, count).toString();
            return String.format(format, count);
        }
    }

    public boolean isPhotoSupported(int partitionIndex) {
        Partition partition = getPartition(partitionIndex);
        if (partition instanceof DirectoryPartition) {
            return ((DirectoryPartition) partition).isPhotoSupported();
        }
        return true;
    }

    /**
     * Returns the currently selected filter.
     */
    public ContactListFilter getFilter() {
        return mFilter;
    }

    public void setFilter(ContactListFilter filter) {
        mFilter = filter;
    }

    // TODO: move sharable logic (bindXX() methods) to here with extra arguments

    /**
     * Loads the photo for the quick contact view and assigns the contact uri.
     * @param photoIdColumn Index of the photo id column
     * @param photoUriColumn Index of the photo uri column. Optional: Can be -1
     * @param contactIdColumn Index of the contact id column
     * @param lookUpKeyColumn Index of the lookup key column
     * @param displayNameColumn Index of the display name column
     */
    protected void bindQuickContact(final ContactListItemView view, int partitionIndex,
            Cursor cursor, int photoIdColumn, int photoUriColumn, int contactIdColumn,
            int lookUpKeyColumn, int displayNameColumn) {
        long photoId = 0;
        if (!cursor.isNull(photoIdColumn)) {
            photoId = cursor.getLong(photoIdColumn);
        }

        QuickContactBadge quickContact = view.getQuickContact();
        quickContact.assignContactUri(
                getContactUri(partitionIndex, cursor, contactIdColumn, lookUpKeyColumn));
        if (CompatUtils.hasPrioritizedMimeType()) {
            // The Contacts app never uses the QuickContactBadge. Therefore, it is safe to assume
            // that only Dialer will use this QuickContact badge. This means prioritizing the phone
            // mimetype here is reasonable.
            quickContact.setPrioritizedMimeType(Phone.CONTENT_ITEM_TYPE);
        }

        if (photoId != 0 || photoUriColumn == -1) {
            getPhotoLoader().loadThumbnail(quickContact, photoId, mDarkTheme, mCircularPhotos,
                    null);
        } else {
            final String photoUriString = cursor.getString(photoUriColumn);
            final Uri photoUri = photoUriString == null ? null : Uri.parse(photoUriString);
            DefaultImageRequest request = null;
            if (photoUri == null) {
                request = getDefaultImageRequestFromCursor(cursor, displayNameColumn,
                        lookUpKeyColumn);
            }
            getPhotoLoader().loadPhoto(quickContact, photoUri, -1, mDarkTheme, mCircularPhotos,
                    request);
        }

    }

    @Override
    public boolean hasStableIds() {
        // Whenever bindViewId() is called, the values passed into setId() are stable or
        // stable-ish. For example, when one contact is modified we don't expect a second
        // contact's Contact._ID values to change.
        return true;
    }

    protected void bindViewId(final ContactListItemView view, Cursor cursor, int idColumn) {
        // Set a semi-stable id, so that talkback won't get confused when the list gets
        // refreshed. There is little harm in inserting the same ID twice.
        long contactId = cursor.getLong(idColumn);
        view.setId((int) (contactId % Integer.MAX_VALUE));

    }

    protected Uri getContactUri(int partitionIndex, Cursor cursor,
            int contactIdColumn, int lookUpKeyColumn) {
        long contactId = cursor.getLong(contactIdColumn);
        String lookupKey = cursor.getString(lookUpKeyColumn);
        long directoryId = ((DirectoryPartition)getPartition(partitionIndex)).getDirectoryId();
        Uri uri = Contacts.getLookupUri(contactId, lookupKey);
        if (uri != null && directoryId != Directory.DEFAULT) {
            uri = uri.buildUpon().appendQueryParameter(
                    ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId)).build();
        }
        return uri;
    }

    /**
     * Retrieves the lookup key and display name from a cursor, and returns a
     * {@link DefaultImageRequest} containing these contact details
     *
     * @param cursor Contacts cursor positioned at the current row to retrieve contact details for
     * @param displayNameColumn Column index of the display name
     * @param lookupKeyColumn Column index of the lookup key
     * @return {@link DefaultImageRequest} with the displayName and identifier fields set to the
     * display name and lookup key of the contact.
     */
    public DefaultImageRequest getDefaultImageRequestFromCursor(Cursor cursor,
            int displayNameColumn, int lookupKeyColumn) {
        final String displayName = cursor.getString(displayNameColumn);
        final String lookupKey = cursor.getString(lookupKeyColumn);
        return new DefaultImageRequest(displayName, lookupKey, mCircularPhotos);
    }
}
