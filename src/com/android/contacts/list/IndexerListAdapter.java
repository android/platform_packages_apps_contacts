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
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SectionIndexer;

/**
 * A list adapter that supports section indexer and a pinned header.
 */
public abstract class IndexerListAdapter extends PinnedHeaderListAdapter implements SectionIndexer {

    protected Context mContext;
    private SectionIndexer mIndexer;
    private int mIndexedPartition = 0;
    private boolean mSectionHeaderDisplayEnabled;
    private View mHeader;

    /**
     * An item view is displayed differently depending on whether it is placed
     * at the beginning, middle or end of a section. It also needs to know the
     * section header when it is at the beginning of a section. This object
     * captures all this configuration.
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

    private Placement mPlacementCache = new Placement();

    /**
     * Constructor.
     */
    public IndexerListAdapter(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * Creates a section header view that will be pinned at the top of the list
     * as the user scrolls.
     */
    protected abstract View createPinnedSectionHeaderView(Context context, ViewGroup parent);

    /**
     * Sets the title in the pinned header as the user scrolls.
     */
    protected abstract void setPinnedSectionTitle(View pinnedHeaderView, String title);

    public boolean isSectionHeaderDisplayEnabled() {
        return mSectionHeaderDisplayEnabled;
    }

    public void setSectionHeaderDisplayEnabled(boolean flag) {
        this.mSectionHeaderDisplayEnabled = flag;
    }

    public int getIndexedPartition() {
        return mIndexedPartition;
    }

    public void setIndexedPartition(int partition) {
        this.mIndexedPartition = partition;
    }

    public SectionIndexer getIndexer() {
        return mIndexer;
    }

    public void setIndexer(SectionIndexer indexer) {
        mIndexer = indexer;
        mPlacementCache.invalidate();
    }

    public Object[] getSections() {
        if (mIndexer == null) {
            return new String[] { " " };
        } else {
            return mIndexer.getSections();
        }
    }

    /**
     * @return relative position of the section in the indexed partition
     */
    public int getPositionForSection(int sectionIndex) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getPositionForSection(sectionIndex);
    }

    /**
     * @param position relative position in the indexed partition
     */
    public int getSectionForPosition(int position) {
        if (mIndexer == null) {
            return -1;
        }

        return mIndexer.getSectionForPosition(position);
    }

    @Override
    public int getPinnedHeaderCount() {
        if (isSectionHeaderDisplayEnabled()) {
            return super.getPinnedHeaderCount() + 1;
        } else {
            return super.getPinnedHeaderCount();
        }
    }

    @Override
    public View getPinnedHeaderView(int viewIndex, View convertView, ViewGroup parent) {
        if (isSectionHeaderDisplayEnabled() && viewIndex == getPinnedHeaderCount() - 1) {
            if (mHeader == null) {
                mHeader = createPinnedSectionHeaderView(mContext, parent);
            }
            return mHeader;
        } else {
            return super.getPinnedHeaderView(viewIndex, convertView, parent);
        }
    }

    @Override
    public void configurePinnedHeaders(PinnedHeaderListView listView) {
        super.configurePinnedHeaders(listView);

        if (!isSectionHeaderDisplayEnabled()) {
            return;
        }

        int index = getPinnedHeaderCount() - 1;
        if (mIndexer == null || getCount() == 0) {
            listView.setHeaderInvisible(index, false);
        } else {
            int listPosition = listView.getPositionAt(listView.getTotalTopPinnedHeaderHeight());
            int position = listPosition - listView.getHeaderViewsCount();

            int section = -1;
            int partition = getPartitionForPosition(position);
            if (partition == mIndexedPartition) {
                int offset = getOffsetInPartition(position);
                if (offset != -1) {
                    section = getSectionForPosition(offset);
                }
            }

            if (section == -1) {
                listView.setHeaderInvisible(index, false);
            } else {
                View topChild = getViewAtVisiblePosition(listView, listPosition);
                if (topChild != null) {
                    // Match the pinned header's height to the height of the list item.
                    mHeader.setMinimumHeight(topChild.getMeasuredHeight());
                }
                setPinnedSectionTitle(mHeader, (String)mIndexer.getSections()[section]);

                // Compute the item position where the current partition begins
                int partitionStart = getPositionForPartition(mIndexedPartition);
                if (hasHeader(mIndexedPartition)) {
                    partitionStart++;
                }

                // Compute the item position where the next section begins
                int nextSectionPosition = partitionStart + getPositionForSection(section + 1);
                boolean isLastInSection = position == nextSectionPosition - 1;
                listView.setFadingHeader(index, listPosition, isLastInSection);
            }
        }
    }

    /**
     * Returns the view used for the specified list position assuming it is visible or null if
     * it isn't.
     *
     * <p>This makes some assumptions about the implementation of ListView (child views are the
     * item views and are ordered in the same way as the adapter items they are displaying)
     * but they are probably safe given that the API is stable.</p>
     */
    private View getViewAtVisiblePosition(ListView list, int position) {
        final int firstVisiblePosition = list.getFirstVisiblePosition();
        final int childCount = list.getChildCount();
        final int index = position - firstVisiblePosition;
        if (index >= 0 && index < childCount) {
            // Position is on-screen, use existing view.
            return list.getChildAt(index);
        } else {
            return null;
        }
    }

    /**
     * Computes the item's placement within its section and populates the {@code placement}
     * object accordingly.  Please note that the returned object is volatile and should be
     * copied if the result needs to be used later.
     */
    public Placement getItemPlacementInSection(int position) {
        if (mPlacementCache.position == position) {
            return mPlacementCache;
        }

        mPlacementCache.position = position;
        if (isSectionHeaderDisplayEnabled()) {
            int section = getSectionForPosition(position);
            if (section != -1 && getPositionForSection(section) == position) {
                mPlacementCache.firstInSection = true;
                mPlacementCache.sectionHeader = (String)getSections()[section];
            } else {
                mPlacementCache.firstInSection = false;
                mPlacementCache.sectionHeader = null;
            }

            mPlacementCache.lastInSection = (getPositionForSection(section + 1) - 1 == position);
        } else {
            mPlacementCache.firstInSection = false;
            mPlacementCache.lastInSection = false;
            mPlacementCache.sectionHeader = null;
        }
        return mPlacementCache;
    }
}
