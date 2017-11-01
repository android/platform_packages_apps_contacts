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
package com.android.contacts.widget;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;

import com.google.common.annotations.VisibleForTesting;

/**
 * A general purpose adapter that is composed of multiple sub-adapters. It just
 * appends them in the order they are added. It listens to changes from all
 * sub-adapters and propagates them to its own listeners.
 */
public class CompositeListAdapter extends BaseAdapter {

    private static final int INITIAL_CAPACITY = 2;

    private ListAdapter[] mAdapters;
    private int[] mCounts;
    private int[] mViewTypeCounts;
    private int mSize = 0;
    private int mCount = 0;
    private int mViewTypeCount = 0;
    private boolean mAllItemsEnabled = true;
    private boolean mCacheValid = true;

    private DataSetObserver mDataSetObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            invalidate();
            notifyDataChanged();
        }

        @Override
        public void onInvalidated() {
            invalidate();
            notifyDataChanged();
        }
    };

    public CompositeListAdapter() {
        this(INITIAL_CAPACITY);
    }

    public CompositeListAdapter(int initialCapacity) {
        mAdapters = new ListAdapter[INITIAL_CAPACITY];
        mCounts = new int[INITIAL_CAPACITY];
        mViewTypeCounts = new int[INITIAL_CAPACITY];
    }

    @VisibleForTesting
    /*package*/ void addAdapter(ListAdapter adapter) {
        if (mSize >= mAdapters.length) {
            int newCapacity = mSize + 2;
            ListAdapter[] newAdapters = new ListAdapter[newCapacity];
            System.arraycopy(mAdapters, 0, newAdapters, 0, mSize);
            mAdapters = newAdapters;

            int[] newCounts = new int[newCapacity];
            System.arraycopy(mCounts, 0, newCounts, 0, mSize);
            mCounts = newCounts;

            int[] newViewTypeCounts = new int[newCapacity];
            System.arraycopy(mViewTypeCounts, 0, newViewTypeCounts, 0, mSize);
            mViewTypeCounts = newViewTypeCounts;
        }

        adapter.registerDataSetObserver(mDataSetObserver);

        int count = adapter.getCount();
        int viewTypeCount = adapter.getViewTypeCount();

        mAdapters[mSize] = adapter;
        mCounts[mSize] = count;
        mCount += count;
        mAllItemsEnabled &= adapter.areAllItemsEnabled();
        mViewTypeCounts[mSize] = viewTypeCount;
        mViewTypeCount += viewTypeCount;
        mSize++;

        notifyDataChanged();
    }

    protected void notifyDataChanged() {
        if (getCount() > 0) {
            notifyDataSetChanged();
        } else {
            notifyDataSetInvalidated();
        }
    }

    protected void invalidate() {
        mCacheValid = false;
    }

    protected void ensureCacheValid() {
        if (mCacheValid) {
            return;
        }

        mCount = 0;
        mAllItemsEnabled = true;
        mViewTypeCount = 0;
        for (int i = 0; i < mSize; i++) {
            int count = mAdapters[i].getCount();
            int viewTypeCount = mAdapters[i].getViewTypeCount();
            mCounts[i] = count;
            mCount += count;
            mAllItemsEnabled &= mAdapters[i].areAllItemsEnabled();
            mViewTypeCount += viewTypeCount;
        }

        mCacheValid = true;
    }

    public int getCount() {
        ensureCacheValid();
        return mCount;
    }

    public Object getItem(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mCounts.length; i++) {
            int end = start + mCounts[i];
            if (position >= start && position < end) {
                return mAdapters[i].getItem(position - start);
            }
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }

    public long getItemId(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mCounts.length; i++) {
            int end = start + mCounts[i];
            if (position >= start && position < end) {
                return mAdapters[i].getItemId(position - start);
            }
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }

    @Override
    public int getViewTypeCount() {
        ensureCacheValid();
        return mViewTypeCount;
    }

    @Override
    public int getItemViewType(int position) {
        ensureCacheValid();
        int start = 0;
        int viewTypeOffset = 0;
        for (int i = 0; i < mCounts.length; i++) {
            int end = start + mCounts[i];
            if (position >= start && position < end) {
                return viewTypeOffset + mAdapters[i].getItemViewType(position - start);
            }
            viewTypeOffset += mViewTypeCounts[i];
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mCounts.length; i++) {
            int end = start + mCounts[i];
            if (position >= start && position < end) {
                return mAdapters[i].getView(position - start, convertView, parent);
            }
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }

    @Override
    public boolean areAllItemsEnabled() {
        ensureCacheValid();
        return mAllItemsEnabled;
    }

    @Override
    public boolean isEnabled(int position) {
        ensureCacheValid();
        int start = 0;
        for (int i = 0; i < mCounts.length; i++) {
            int end = start + mCounts[i];
            if (position >= start && position < end) {
                return mAdapters[i].areAllItemsEnabled()
                        || mAdapters[i].isEnabled(position - start);
            }
            start = end;
        }

        throw new ArrayIndexOutOfBoundsException(position);
    }
}
