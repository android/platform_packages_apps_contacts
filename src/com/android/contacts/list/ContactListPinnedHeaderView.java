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
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.android.contacts.R;

/**
 * A custom view for the pinned section header shown at the top of the contact list.
 */
public class ContactListPinnedHeaderView extends TextView {

    public ContactListPinnedHeaderView(Context context, AttributeSet attrs, View parent) {
        super(context, attrs);

        if (R.styleable.ContactListItemView == null) {
            return;
        }
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ContactListItemView);
        int backgroundColor = a.getColor(
                R.styleable.ContactListItemView_list_item_background_color, Color.WHITE);
        int textOffsetTop = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_text_offset_top, 0);
        int paddingStartOffset = a.getDimensionPixelSize(
                R.styleable.ContactListItemView_list_item_padding_left, 0);
        int textWidth = getResources().getDimensionPixelSize(
                R.dimen.contact_list_section_header_width);
        int widthIncludingPadding = paddingStartOffset + textWidth;
        a.recycle();

        setBackgroundColor(backgroundColor);
        setTextAppearance(getContext(), R.style.SectionHeaderStyle);
        setLayoutParams(new LayoutParams(widthIncludingPadding, LayoutParams.WRAP_CONTENT));
        setLayoutDirection(parent.getLayoutDirection());
        setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);

        // Apply text top offset. Multiply by two, because we are implementing this by padding for a
        // vertically centered view, rather than adjusting the position directly via a layout.
        setPaddingRelative(
                getPaddingStart() + paddingStartOffset,
                getPaddingTop() + (textOffsetTop * 2),
                getPaddingEnd(),
                getPaddingBottom());
    }

    /**
     * Sets section header or makes it invisible if the title is null.
     */
    public void setSectionHeaderTitle(String title) {
        if (title != null) {
            setText(title);
            setVisibility(View.VISIBLE);
        } else {
            setVisibility(View.GONE);
        }
    }
}
