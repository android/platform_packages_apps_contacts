/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.contacts.quickcontact;

import android.app.Fragment;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.contacts.common.ContactPresenceIconUtil;
import com.android.contacts.R;

import java.util.List;

/** A fragment that shows the list of resolve items below a tab */
public class QuickContactListFragment extends Fragment {
    private ListView mListView;
    private List<Action> mActions;
    private RelativeLayout mFragmentContainer;
    private Listener mListener;
    private String mMimeType;
    private ClipboardManager mClipBoard;
    private Toast longPressToast;

    public QuickContactListFragment(String mimeType) {
        setRetainInstance(true);
        this.mMimeType = mimeType;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        mFragmentContainer = (RelativeLayout) inflater.inflate(R.layout.quickcontact_list_fragment,
                container, false);
        mListView = (ListView) mFragmentContainer.findViewById(R.id.list);
        mListView.setItemsCanFocus(true);

        mFragmentContainer.setOnClickListener(mOutsideClickListener);
        mClipBoard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
        longPressToast = Toast.makeText(getActivity(), R.string.quick_contact_copied_toast, Toast.LENGTH_SHORT);

        configureAdapter();
        return mFragmentContainer;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public void setActions(List<Action> actions) {
        mActions = actions;
        configureAdapter();
    }

    public void setListener(Listener value) {
        mListener = value;
    }

    private void configureAdapter() {
        if (mActions == null || mListView == null) return;

        mListView.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return mActions.size();
            }

            @Override
            public Object getItem(int position) {
                return mActions.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // Set action title based on summary value
                final Action action = mActions.get(position);
                String mimeType = action.getMimeType();

                final View resultView = convertView != null ? convertView
                        : getActivity().getLayoutInflater().inflate(
                                mimeType.equals(StructuredPostal.CONTENT_ITEM_TYPE) ?
                                        R.layout.quickcontact_list_item_address :
                                        R.layout.quickcontact_list_item,
                                        parent, false);

                // TODO: Put those findViewByIds in a container
                final TextView text1 = (TextView) resultView.findViewById(
                        android.R.id.text1);
                final TextView text2 = (TextView) resultView.findViewById(
                        android.R.id.text2);
                final View actionsContainer = resultView.findViewById(
                        R.id.actions_view_container);
                final ImageView alternateActionButton = (ImageView) resultView.findViewById(
                        R.id.secondary_action_button);
                final View alternateActionDivider = resultView.findViewById(R.id.vertical_divider);
                final ImageView presenceIconView =
                        (ImageView) resultView.findViewById(R.id.presence_icon);

                actionsContainer.setOnClickListener(mPrimaryActionClickListener);
                actionsContainer.setOnLongClickListener(mPrimaryActionLongClickListener);
                actionsContainer.setTag(action);
                alternateActionButton.setOnClickListener(mSecondaryActionClickListener);
                alternateActionButton.setTag(action);

                final boolean hasAlternateAction = action.getAlternateIntent() != null;
                alternateActionDivider.setVisibility(hasAlternateAction ? View.VISIBLE : View.GONE);
                alternateActionButton.setImageDrawable(action.getAlternateIcon());
                alternateActionButton.setContentDescription(action.getAlternateIconDescription());
                alternateActionButton.setVisibility(hasAlternateAction ? View.VISIBLE : View.GONE);

                if (mimeType.equals(Phone.CONTENT_ITEM_TYPE)) {
                    // Force LTR text direction for phone numbers
                    text1.setTextDirection(View.TEXT_DIRECTION_LTR);

                    // Special case for phone numbers in accessibility mode
                    text1.setContentDescription(getActivity().getString(
                            R.string.description_dial_phone_number, action.getBody()));
                    if (hasAlternateAction) {
                        alternateActionButton.setContentDescription(getActivity()
                                .getString(R.string.description_send_message, action.getBody()));
                    }
                }

                text1.setText(action.getBody());
                if (text2 != null) {
                    CharSequence subtitle = action.getSubtitle();
                    text2.setText(subtitle);
                    if (TextUtils.isEmpty(subtitle)) {
                        text2.setVisibility(View.GONE);
                    } else {
                        text2.setVisibility(View.VISIBLE);
                    }
                }
                final Drawable presenceIcon = ContactPresenceIconUtil.getPresenceIcon(
                        getActivity(), action.getPresence());
                if (presenceIcon != null) {
                    presenceIconView.setImageDrawable(presenceIcon);
                    presenceIconView.setVisibility(View.VISIBLE);
                } else {
                    presenceIconView.setVisibility(View.GONE);
                }
                return resultView;
            }
        });
    }

    /** A data item (e.g. phone number) was clicked */
    protected final OnClickListener mPrimaryActionClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Action action = (Action) v.getTag();
            if (mListener != null) mListener.onItemClicked(action, false);
        }
    };

    /** A data item was long clicked */
    protected final OnLongClickListener mPrimaryActionLongClickListener = new OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            final Action action = (Action) v.getTag();
            ClipData clip = android.content.ClipData.newPlainText(action.getSubtitle(), action.getBody());
            mClipBoard.setPrimaryClip(clip);
            longPressToast.show();
            return true;
        }
    };

    /** A secondary action (SMS) was clicked */
    protected final OnClickListener mSecondaryActionClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            final Action action = (Action) v.getTag();
            if (mListener != null) mListener.onItemClicked(action, true);
        }
    };

    private final OnClickListener mOutsideClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mListener != null) mListener.onOutsideClick();
        }
    };

    public interface Listener {
        void onOutsideClick();
        void onItemClicked(Action action, boolean alternate);
    }
}
