/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.core.view.ViewCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.contacts.R;
import com.android.contacts.activities.ActionBarAdapter;
import com.android.contacts.group.GroupMembersFragment;
import com.android.contacts.list.MultiSelectEntryContactListAdapter.SelectedContactsListener;
import com.android.contacts.logging.ListEvent.ActionType;
import com.android.contacts.logging.Logger;
import com.android.contacts.logging.SearchState;
import com.android.contacts.model.AccountTypeManager;
import com.android.contacts.model.account.AccountType;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.model.account.GoogleAccountType;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Fragment containing a contact list used for browsing contacts and optionally selecting
 * multiple contacts via checkboxes.
 */
public abstract class MultiSelectContactsListFragment<T extends MultiSelectEntryContactListAdapter>
        extends ContactEntryListFragment<T>
        implements SelectedContactsListener {

    protected boolean mAnimateOnLoad;
    private static final String TAG = "MultiContactsList";

    public interface OnCheckBoxListActionListener {
        void onStartDisplayingCheckBoxes();
        void onSelectedContactIdsChanged();
        void onStopDisplayingCheckBoxes();
    }

    private static final String EXTRA_KEY_SELECTED_CONTACTS = "selected_contacts";

    private OnCheckBoxListActionListener mCheckBoxListListener;

    public void setCheckBoxListListener(OnCheckBoxListActionListener checkBoxListListener) {
        mCheckBoxListListener = checkBoxListListener;
    }

    public void setAnimateOnLoad(boolean shouldAnimate) {
        mAnimateOnLoad = shouldAnimate;
    }

    @Override
    public void onSelectedContactsChanged() {
        if (mCheckBoxListListener != null) mCheckBoxListListener.onSelectedContactIdsChanged();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (savedInstanceState == null && mAnimateOnLoad) {
            setLayoutAnimation(getListView(), R.anim.slide_and_fade_in_layout_animation);
        }
        return getView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            final TreeSet<Long> selectedContactIds = (TreeSet<Long>)
                    savedInstanceState.getSerializable(EXTRA_KEY_SELECTED_CONTACTS);
            getAdapter().setSelectedContactIds(selectedContactIds);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (mCheckBoxListListener != null) {
            mCheckBoxListListener.onSelectedContactIdsChanged();
        }
    }

    public TreeSet<Long> getSelectedContactIds() {
        return getAdapter().getSelectedContactIds();
    }

    public long[] getSelectedContactIdsArray() {
        return getAdapter().getSelectedContactIdsArray();
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();
        getAdapter().setSelectedContactsListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_KEY_SELECTED_CONTACTS, getSelectedContactIds());
    }

    public void displayCheckBoxes(boolean displayCheckBoxes) {
        if (getAdapter() != null) {
            getAdapter().setDisplayCheckBoxes(displayCheckBoxes);
            if (!displayCheckBoxes) {
                clearCheckBoxes();
            }
        }
    }

    public void clearCheckBoxes() {
        getAdapter().setSelectedContactIds(new TreeSet<Long>());
    }

    @Override
    protected boolean onItemLongClick(int position, long id) {
        final int previouslySelectedCount = getAdapter().getSelectedContactIds().size();
        final long contactId = getContactId(position);
        final int partition = getAdapter().getPartitionForPosition(position);
        if (contactId >= 0 && partition == ContactsContract.Directory.DEFAULT) {
            if (mCheckBoxListListener != null) {
                mCheckBoxListListener.onStartDisplayingCheckBoxes();
            }
            getAdapter().toggleSelectionOfContactId(contactId);
            Logger.logListEvent(ActionType.SELECT, getListType(),
                    /* count */ getAdapter().getCount(), /* clickedIndex */ position,
                    /* numSelected */ 1);
            // Manually send clicked event if there is a checkbox.
            // See b/24098561. TalkBack will not read it otherwise.
            final int index = position + getListView().getHeaderViewsCount() - getListView()
                    .getFirstVisiblePosition();
            if (index >= 0 && index < getListView().getChildCount()) {
                getListView().getChildAt(index).sendAccessibilityEvent(AccessibilityEvent
                        .TYPE_VIEW_CLICKED);
            }
        }
        final int nowSelectedCount = getAdapter().getSelectedContactIds().size();
        if (mCheckBoxListListener != null
                && previouslySelectedCount != 0 && nowSelectedCount == 0) {
            // Last checkbox has been unchecked. So we should stop displaying checkboxes.
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        }
        return true;
    }

    @Override
    protected void onItemClick(int position, long id) {
        final long contactId = getContactId(position);
        if (contactId < 0) {
            return;
        }
        if (getAdapter().isDisplayingCheckBoxes()) {
            getAdapter().toggleSelectionOfContactId(contactId);
        }
        if (mCheckBoxListListener != null && getAdapter().getSelectedContactIds().size() == 0) {
            mCheckBoxListListener.onStopDisplayingCheckBoxes();
        }
    }

    private long getContactId(int position) {
        final int contactIdColumnIndex = getAdapter().getContactColumnIdIndex();

        final Cursor cursor = (Cursor) getAdapter().getItem(position);
        if (cursor != null) {
            if (cursor.getColumnCount() > contactIdColumnIndex) {
                return cursor.getLong(contactIdColumnIndex);
            }
        }

        Log.w(TAG, "Failed to get contact ID from cursor column " + contactIdColumnIndex);
        return -1;
    }

    /**
     * Returns the state of the search results currently presented to the user.
     */
    public SearchState createSearchState() {
        return createSearchState(/* selectedPosition */ -1);
    }

    /**
     * Returns the state of the search results presented to the user
     * at the time the result in the given position was clicked.
     */
    public SearchState createSearchStateForSearchResultClick(int selectedPosition) {
        return createSearchState(selectedPosition);
    }

    private SearchState createSearchState(int selectedPosition) {
        final MultiSelectEntryContactListAdapter adapter = getAdapter();
        if (adapter == null) {
            return null;
        }
        final SearchState searchState = new SearchState();
        searchState.queryLength = adapter.getQueryString() == null
                ? 0 : adapter.getQueryString().length();
        searchState.numPartitions = adapter.getPartitionCount();

        // Set the number of results displayed to the user.  Note that the adapter.getCount(),
        // value does not always match the number of results actually displayed to the user,
        // which is why we calculate it manually.
        final List<Integer> numResultsInEachPartition = new ArrayList<>();
        for (int i = 0; i < adapter.getPartitionCount(); i++) {
            final Cursor cursor = adapter.getCursor(i);
            if (cursor == null || cursor.isClosed()) {
                // Something went wrong, abort.
                numResultsInEachPartition.clear();
                break;
            }
            numResultsInEachPartition.add(cursor.getCount());
        }
        if (!numResultsInEachPartition.isEmpty()) {
            int numResults = 0;
            for (int i = 0; i < numResultsInEachPartition.size(); i++) {
                numResults += numResultsInEachPartition.get(i);
            }
            searchState.numResults = numResults;
        }

        // If a selection was made, set additional search state
        if (selectedPosition >= 0) {
            searchState.selectedPartition = adapter.getPartitionForPosition(selectedPosition);
            searchState.selectedIndexInPartition = adapter.getOffsetInPartition(selectedPosition);
            final Cursor cursor = adapter.getCursor(searchState.selectedPartition);
            searchState.numResultsInSelectedPartition =
                    cursor == null || cursor.isClosed() ? -1 : cursor.getCount();

            // Calculate the index across all partitions
            if (!numResultsInEachPartition.isEmpty()) {
                int selectedIndex = 0;
                for (int i = 0; i < searchState.selectedPartition; i++) {
                    selectedIndex += numResultsInEachPartition.get(i);
                }
                selectedIndex += searchState.selectedIndexInPartition;
                searchState.selectedIndex = selectedIndex;
            }
        }
        return searchState;
    }

    protected void setLayoutAnimation(final ViewGroup view, int animationId) {
        if (view == null) {
            return;
        }
        view.setLayoutAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setLayoutAnimation(null);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        view.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(getActivity(), animationId));
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        final View accountFilterContainer = getView().findViewById(
                R.id.account_filter_header_container);
        if (accountFilterContainer == null) {
            return;
        }

        int firstCompletelyVisibleItem = firstVisibleItem;
        if (view != null && view.getChildAt(0) != null && view.getChildAt(0).getTop() < 0) {
            firstCompletelyVisibleItem++;
        }

        if (firstCompletelyVisibleItem == 0) {
            ViewCompat.setElevation(accountFilterContainer, 0);
        } else {
            ViewCompat.setElevation(accountFilterContainer,
                    getResources().getDimension(R.dimen.contact_list_header_elevation));
        }
    }

    protected void bindListHeaderCustom(View listView, View accountFilterContainer) {
        bindListHeaderCommon(listView, accountFilterContainer);

        final TextView accountFilterHeader = (TextView) accountFilterContainer.findViewById(
                R.id.account_filter_header);
        accountFilterHeader.setText(R.string.listCustomView);
        accountFilterHeader.setAllCaps(false);

        final ImageView accountFilterHeaderIcon = (ImageView) accountFilterContainer
                .findViewById(R.id.account_filter_icon);
        accountFilterHeaderIcon.setVisibility(View.GONE);
    }

    /**
     * Show account icon, count of contacts and account name in the header of the list.
     */
    protected void bindListHeader(Context context, View listView, View accountFilterContainer,
            AccountWithDataSet accountWithDataSet, int memberCount) {
        if (memberCount < 0) {
            hideHeaderAndAddPadding(context, listView, accountFilterContainer);
            return;
        }

        bindListHeaderCommon(listView, accountFilterContainer);

        final AccountTypeManager accountTypeManager = AccountTypeManager.getInstance(context);
        final AccountType accountType = accountTypeManager.getAccountType(
                accountWithDataSet.type, accountWithDataSet.dataSet);

        // Set text of count of contacts and account name
        final TextView accountFilterHeader = (TextView) accountFilterContainer.findViewById(
                R.id.account_filter_header);
        final String headerText = shouldShowAccountName(accountType)
                ? String.format(context.getResources().getQuantityString(
                        R.plurals.contacts_count_with_account, memberCount),
                                memberCount, accountWithDataSet.name)
                : context.getResources().getQuantityString(
                        R.plurals.contacts_count, memberCount, memberCount);
        accountFilterHeader.setText(headerText);
        accountFilterHeader.setAllCaps(false);

        // Set icon of the account
        final Drawable icon = accountType != null ? accountType.getDisplayIcon(context) : null;
        final ImageView accountFilterHeaderIcon = (ImageView) accountFilterContainer
                .findViewById(R.id.account_filter_icon);

        // If it's a writable Google account, we set icon size as 24dp; otherwise, we set it as
        // 20dp. And we need to change margin accordingly. This is because the Google icon looks
        // smaller when the icons are of the same size.
        if (accountType instanceof GoogleAccountType) {
            accountFilterHeaderIcon.getLayoutParams().height = getResources()
                    .getDimensionPixelOffset(R.dimen.contact_browser_list_header_icon_size);
            accountFilterHeaderIcon.getLayoutParams().width = getResources()
                    .getDimensionPixelOffset(R.dimen.contact_browser_list_header_icon_size);

            setMargins(accountFilterHeaderIcon,
                    getResources().getDimensionPixelOffset(
                            R.dimen.contact_browser_list_header_icon_left_margin),
                    getResources().getDimensionPixelOffset(
                            R.dimen.contact_browser_list_header_icon_right_margin));
        } else {
            accountFilterHeaderIcon.getLayoutParams().height = getResources()
                    .getDimensionPixelOffset(R.dimen.contact_browser_list_header_icon_size_alt);
            accountFilterHeaderIcon.getLayoutParams().width = getResources()
                    .getDimensionPixelOffset(R.dimen.contact_browser_list_header_icon_size_alt);

            setMargins(accountFilterHeaderIcon,
                    getResources().getDimensionPixelOffset(
                            R.dimen.contact_browser_list_header_icon_left_margin_alt),
                    getResources().getDimensionPixelOffset(
                            R.dimen.contact_browser_list_header_icon_right_margin_alt));
        }
        accountFilterHeaderIcon.requestLayout();

        accountFilterHeaderIcon.setVisibility(View.VISIBLE);
        accountFilterHeaderIcon.setImageDrawable(icon);
    }

    private boolean shouldShowAccountName(AccountType accountType) {
        return (accountType.isGroupMembershipEditable() && this instanceof GroupMembersFragment)
                || GoogleAccountType.ACCOUNT_TYPE.equals(accountType.accountType);
    }

    private void setMargins(View v, int l, int r) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.setMarginStart(l);
            p.setMarginEnd(r);
            v.setLayoutParams(p);
            v.requestLayout();
        }
    }

    private void bindListHeaderCommon(View listView, View accountFilterContainer) {
        // Show header and remove top padding of the list
        accountFilterContainer.setVisibility(View.VISIBLE);
        setListViewPaddingTop(listView, /* paddingTop */ 0);
    }

    /**
     * Hide header of list view and add padding to the top of list view.
     */
    protected void hideHeaderAndAddPadding(Context context, View listView,
            View accountFilterContainer) {
        accountFilterContainer.setVisibility(View.GONE);
        setListViewPaddingTop(listView,
                /* paddingTop */ context.getResources().getDimensionPixelSize(
                        R.dimen.contact_browser_list_item_padding_top_or_bottom));
    }

    private void setListViewPaddingTop(View listView, int paddingTop) {
        listView.setPadding(listView.getPaddingLeft(), paddingTop, listView.getPaddingRight(),
                listView.getPaddingBottom());
    }

    /**
     * Returns the {@link ActionBarAdapter} object associated with list fragment.
     */
    public ActionBarAdapter getActionBarAdapter() {
        return null;
    }
}
