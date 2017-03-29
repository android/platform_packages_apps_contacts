/*
 Copyright (c) 2014, 2016, The Linux Foundation. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts.activities;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.android.contacts.R;

public class PopupList {
    private final Context mContext;
    private final View mAnchorView;
    private final ArrayList<Item> mItems = new ArrayList<Item>();

    private PopupWindow mPopupWindow;
    private ListView mContentList;
    private OnPopupItemClickListener mOnPopupItemClickListener;

    private int mPopupOffsetX;
    private int mPopupOffsetY;
    private int mPopupWidth;
    private int mPopupHeight;

    private final PopupWindow.OnDismissListener mOnDismissListener =
            new PopupWindow.OnDismissListener() {
                @Override
                public void onDismiss() {
                    if (mPopupWindow == null) return;
                    mPopupWindow = null;
                    ViewTreeObserver observer = mAnchorView.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeGlobalOnLayoutListener(mOnGlobalLayoutListener);
                    }
                }
            };

    private final OnItemClickListener mOnItemClickListener =
            new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (mPopupWindow == null) return;
                    mPopupWindow.dismiss();
                    if (mOnPopupItemClickListener != null) {
                        mOnPopupItemClickListener.onPopupItemClick((int) id);
                    }
                }
            };

    private final OnGlobalLayoutListener mOnGlobalLayoutListener =
            new OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (mPopupWindow == null) return;
                    updatePopupLayoutParams();
                    // Need to update the position of the popup window
                    mPopupWindow.update(mAnchorView,
                            mPopupOffsetX, mPopupOffsetY, mPopupWidth, mPopupHeight);
                }
            };

    public PopupList(Context context, View anchorView) {
        mContext = context;
        mAnchorView = anchorView;
    }

    public void setOnPopupItemClickListener(OnPopupItemClickListener listener) {
        mOnPopupItemClickListener = listener;
    }

    public void addItem(int id, String title) {
        mItems.add(new Item(id, title));
    }

    public void clearItems() {
        mItems.clear();
    }

    public boolean isShowing() {
        if (mPopupWindow != null) {
            return mPopupWindow.isShowing();
        }
        return false;
    }

    public void show() {
        if (mPopupWindow != null) return;
        mAnchorView.getViewTreeObserver()
                .addOnGlobalLayoutListener(mOnGlobalLayoutListener);
        mPopupWindow = createPopupWindow();
        updatePopupLayoutParams();
        mPopupWindow.setWidth(mPopupWidth);
        mPopupWindow.setHeight(mPopupHeight);
        mPopupWindow.showAsDropDown(mAnchorView, mPopupOffsetX, mPopupOffsetY);
    }

    public void dismiss() {
        if (mPopupWindow != null)
            mPopupWindow.dismiss();
    }

    private void updatePopupLayoutParams() {
        ListView content = mContentList;
        PopupWindow popup = mPopupWindow;

        Rect p = new Rect();
        popup.getBackground().getPadding(p);

        int maxHeight = mPopupWindow.getMaxAvailableHeight(mAnchorView) - p.top - p.bottom;
        mContentList.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST));
        mPopupWidth = content.getMeasuredWidth() + p.top + p.bottom;
        mPopupHeight = Math.min(maxHeight, content.getMeasuredHeight() + p.left + p.right);
        mPopupOffsetX = -p.left;
        mPopupOffsetY = -mAnchorView.getHeight()-p.top;
    }

    private PopupWindow createPopupWindow() {
        PopupWindow popup = new PopupWindow(mContext);
        popup.setOnDismissListener(mOnDismissListener);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));

        mContentList = new ListView(mContext, null,
                android.R.attr.dropDownListViewStyle);
        mContentList.setBackgroundResource(R.color.select_popup_button_background);
        mContentList.setAdapter(new ItemDataAdapter());
        mContentList.setOnItemClickListener(mOnItemClickListener);
        popup.setElevation(24);
        popup.setContentView(mContentList);
        popup.setFocusable(true);
        popup.setOutsideTouchable(true);

        return popup;
    }

    public Item findItem(int id) {
        for (Item item : mItems) {
            if (item.id == id) return item;
        }
        return null;
    }

    public static interface OnPopupItemClickListener {
        public boolean onPopupItemClick(int itemId);
    }

    public static class Item {
        public final int id;
        public String title;

        public Item(int id, String title) {
            this.id = id;
            this.title = title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    private class ItemDataAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return mItems.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(mContext)
                        .inflate(R.layout.popup_list_item, null);
            }
            TextView text = (TextView) convertView.findViewById(R.id.popup_list_title);
            text.setText(mItems.get(position).title);
            return convertView;
        }
    }
}
