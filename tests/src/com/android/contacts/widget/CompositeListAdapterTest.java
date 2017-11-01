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

import android.content.Context;
import android.database.DataSetObserver;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Tests for {@link CompositeListAdapter}.
 */
@SmallTest
public class CompositeListAdapterTest extends AndroidTestCase {

    private final class MockAdapter extends ArrayAdapter<String> {
        boolean allItemsEnabled = true;
        HashSet<Integer> enabledItems = new HashSet<Integer>();
        int viewTypeCount = 1;
        HashMap<Integer, Integer> viewTypes = new HashMap<Integer, Integer>();

        private MockAdapter(Context context, List<String> objects) {
            super(context, android.R.layout.simple_list_item_1, objects);
            for (int i = 0; i < objects.size(); i++) {
                viewTypes.put(i, 0);
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return new MockView(getContext(), position);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return allItemsEnabled;
        }

        @Override
        public boolean isEnabled(int position) {
            return enabledItems.contains(position);
        }

        @Override
        public int getViewTypeCount() {
            return viewTypeCount;
        }

        @Override
        public int getItemViewType(int position) {
            return viewTypes.get(position);
        }
    }

    private final class MockView extends View {
        public MockView(Context context, int position) {
            super(context);
            setTag(position);
        }
    }

    private final class TestDataSetObserver extends DataSetObserver {

        public int changeCount;
        public int invalidationCount;

        @Override
        public void onChanged() {
            changeCount++;
        }

        @Override
        public void onInvalidated() {
            invalidationCount++;
        }
    }

    private MockAdapter mAdapter1;
    private MockAdapter mAdapter2;
    private MockAdapter mAdapter3;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAdapter1 = new MockAdapter(getContext(), Lists.newArrayList("A", "B"));
        mAdapter2 = new MockAdapter(getContext(), new ArrayList<String>());
        mAdapter3 = new MockAdapter(getContext(), Lists.newArrayList("C", "D", "E"));
    }

    public void testGetCount() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);
        adapter.addAdapter(mAdapter3);

        assertEquals(5, adapter.getCount());
    }

    public void testGetCountWithInvalidation() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        assertEquals(0, adapter.getCount());

        adapter.addAdapter(mAdapter1);
        assertEquals(2, adapter.getCount());

        adapter.addAdapter(mAdapter2);
        assertEquals(2, adapter.getCount());

        adapter.addAdapter(mAdapter3);
        assertEquals(5, adapter.getCount());
    }

    public void testGetItem() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);
        adapter.addAdapter(mAdapter3);

        assertEquals("A", adapter.getItem(0));
        assertEquals("B", adapter.getItem(1));
        assertEquals("C", adapter.getItem(2));
        assertEquals("D", adapter.getItem(3));
        assertEquals("E", adapter.getItem(4));
    }

    public void testGetItemId() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);
        adapter.addAdapter(mAdapter3);

        assertEquals(0, adapter.getItemId(0));
        assertEquals(1, adapter.getItemId(1));
        assertEquals(0, adapter.getItemId(2));
        assertEquals(1, adapter.getItemId(3));
        assertEquals(2, adapter.getItemId(4));
    }

    public void testGetView() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);
        adapter.addAdapter(mAdapter3);

        assertEquals(0, adapter.getView(0, null, null).getTag());
        assertEquals(1, adapter.getView(1, null, null).getTag());
        assertEquals(0, adapter.getView(2, null, null).getTag());
        assertEquals(1, adapter.getView(3, null, null).getTag());
        assertEquals(2, adapter.getView(4, null, null).getTag());
    }

    public void testGetViewTypeCount() {
        mAdapter1.viewTypeCount = 2;
        mAdapter2.viewTypeCount = 3;
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);
        adapter.addAdapter(mAdapter3);

        // Note that mAdapter2 adds an implicit +1
        assertEquals(6, adapter.getViewTypeCount());
    }

    public void testGetItemViewType() {
        mAdapter1.viewTypeCount = 2;
        mAdapter1.viewTypes.put(0, 1);
        mAdapter1.viewTypes.put(1, 0);

        mAdapter3.viewTypeCount = 3;
        mAdapter3.viewTypes.put(0, 1);
        mAdapter3.viewTypes.put(1, 2);
        mAdapter3.viewTypes.put(2, 0);

        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);
        adapter.addAdapter(mAdapter3);

        assertEquals(1, adapter.getItemViewType(0));
        assertEquals(0, adapter.getItemViewType(1));

        // Note: mAdapter2 throws in a +1

        assertEquals(4, adapter.getItemViewType(2));
        assertEquals(5, adapter.getItemViewType(3));
        assertEquals(3, adapter.getItemViewType(4));
    }

    public void testNotifyDataSetChangedPropagated() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);

        TestDataSetObserver observer = new TestDataSetObserver();
        adapter.registerDataSetObserver(observer);
        mAdapter1.add("X");

        assertEquals(1, observer.changeCount);
        assertEquals(0, observer.invalidationCount);
        assertEquals(3, adapter.getCount());
        assertEquals("A", adapter.getItem(0));
        assertEquals("B", adapter.getItem(1));
        assertEquals("X", adapter.getItem(2));

        mAdapter2.add("Y");
        assertEquals(2, observer.changeCount);
        assertEquals(0, observer.invalidationCount);
        assertEquals(4, adapter.getCount());
        assertEquals("A", adapter.getItem(0));
        assertEquals("B", adapter.getItem(1));
        assertEquals("X", adapter.getItem(2));
        assertEquals("Y", adapter.getItem(3));

    }

    public void testNotifyDataSetChangedOnAddingAdapter() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);

        TestDataSetObserver observer = new TestDataSetObserver();
        adapter.registerDataSetObserver(observer);
        adapter.addAdapter(mAdapter3);

        assertEquals(1, observer.changeCount);
        assertEquals(0, observer.invalidationCount);
        assertEquals(5, adapter.getCount());
        assertEquals("A", adapter.getItem(0));
        assertEquals("B", adapter.getItem(1));
        assertEquals("C", adapter.getItem(2));
        assertEquals("D", adapter.getItem(3));
        assertEquals("E", adapter.getItem(4));
    }

    public void testNotifyDataSetInvalidated() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);

        TestDataSetObserver observer = new TestDataSetObserver();
        adapter.registerDataSetObserver(observer);

        mAdapter1.remove("A");
        assertEquals(1, observer.changeCount);
        assertEquals(0, observer.invalidationCount);
        assertEquals(1, adapter.getCount());

        mAdapter1.remove("B");
        assertEquals(1, observer.changeCount);
        assertEquals(1, observer.invalidationCount);
        assertEquals(0, adapter.getCount());
    }

    public void testAreAllItemsEnabled() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter3);

        assertTrue(adapter.areAllItemsEnabled());
    }

    public void testAreAllItemsEnabledWithInvalidation() {
        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        assertTrue(adapter.areAllItemsEnabled());

        mAdapter3.allItemsEnabled = false;
        adapter.addAdapter(mAdapter3);

        assertFalse(adapter.areAllItemsEnabled());
    }

    public void testIsEnabled() {
        mAdapter1.allItemsEnabled = false;
        mAdapter1.enabledItems.add(1);

        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter2);
        adapter.addAdapter(mAdapter3);

        assertFalse(adapter.isEnabled(0));
        assertTrue(adapter.isEnabled(1));
        assertTrue(adapter.isEnabled(2));
        assertTrue(adapter.isEnabled(3));
        assertTrue(adapter.isEnabled(4));
    }

    public void testIsEnabledWhenAllEnabledAtLeastOneAdapter() {
        mAdapter1.allItemsEnabled = false;
        mAdapter1.enabledItems.add(1);
        mAdapter3.allItemsEnabled = false;
        mAdapter3.enabledItems.add(1);

        CompositeListAdapter adapter = new CompositeListAdapter();
        adapter.addAdapter(mAdapter1);
        adapter.addAdapter(mAdapter3);

        assertFalse(adapter.isEnabled(0));
        assertTrue(adapter.isEnabled(1));
        assertFalse(adapter.isEnabled(2));
        assertTrue(adapter.isEnabled(3));
        assertFalse(adapter.isEnabled(4));
    }
}
