/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.contacts.util;

import android.content.res.Resources;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ListView;

import com.android.contacts.R;
import com.android.contacts.compat.CompatUtils;

/**
 * Provides static functions to work with views
 */
public class ViewUtil {
    private ViewUtil() {}

    /**
     * Returns the width as specified in the LayoutParams
     * @throws IllegalStateException Thrown if the view's width is unknown before a layout pass
     * s
     */
    public static int getConstantPreLayoutWidth(View view) {
        // We haven't been layed out yet, so get the size from the LayoutParams
        final ViewGroup.LayoutParams p = view.getLayoutParams();
        if (p.width < 0) {
            throw new IllegalStateException("Expecting view's width to be a constant rather " +
                    "than a result of the layout pass");
        }
        return p.width;
    }

    /**
     * Returns a boolean indicating whether or not the view's layout direction is RTL
     *
     * @param view - A valid view
     * @return True if the view's layout direction is RTL
     */
    public static boolean isViewLayoutRtl(View view) {
        return view.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private static final ViewOutlineProvider OVAL_OUTLINE_PROVIDER;
    static {
        if (CompatUtils.isLollipopCompatible()) {
            OVAL_OUTLINE_PROVIDER = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            };
        } else {
            OVAL_OUTLINE_PROVIDER = null;
        }
    }

    private static final ViewOutlineProvider RECT_OUTLINE_PROVIDER;
    static {
        if (CompatUtils.isLollipopCompatible()) {
            RECT_OUTLINE_PROVIDER = new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRect(0, 0, view.getWidth(), view.getHeight());
                }
            };
        } else {
            RECT_OUTLINE_PROVIDER = null;
        }
    }

    /**
     * Adds a rectangular outline to a view. This can be useful when you want to add a shadow
     * to a transparent view. See b/16856049.
     * @param view view that the outline is added to
     * @param res The resources file.
     */
    public static void addRectangularOutlineProvider(View view, Resources res) {
        if (CompatUtils.isLollipopCompatible()) {
            view.setOutlineProvider(RECT_OUTLINE_PROVIDER);
        }
    }

    /**
     * Configures the floating action button, clipping it to a circle and setting its translation z.
     * @param view The float action button's view.
     * @param res The resources file.
     */
    public static void setupFloatingActionButton(View view, Resources res) {
        if (CompatUtils.isLollipopCompatible()) {
            view.setOutlineProvider(OVAL_OUTLINE_PROVIDER);
            view.setTranslationZ(
                    res.getDimensionPixelSize(R.dimen.floating_action_button_translation_z));
        }
    }

    /**
     * Adds padding to the bottom of the given {@link ListView} so that the floating action button
     * does not obscure any content.
     *
     * @param listView to add the padding to
     * @param res valid resources object
     */
    public static void addBottomPaddingToListViewForFab(ListView listView, Resources res) {
        final int fabPadding = res.getDimensionPixelSize(
                R.dimen.floating_action_button_list_bottom_padding);
        listView.setPaddingRelative(listView.getPaddingStart(), listView.getPaddingTop(),
                listView.getPaddingEnd(), listView.getPaddingBottom() + fabPadding);
        listView.setClipToPadding(false);
    }
}
