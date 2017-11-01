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

package com.android.contacts.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.android.contacts.R;

/**
 * Layout that calculates its height based on its width, or vice versa (depending on the set
 * {@link #setDirection(Direction)}. The factor is specified in {@link #setRatio(float)}.
 * <p>For {@link Direction#heightToWidth}: width := height * factor</p>
 * <p>For {@link Direction#widthToHeight}: height := width * factor</p>
 * <p>Only one child is allowed; if more are required, another ViewGroup can be used as the direct
 * child of this layout.</p>
 */
public class ProportionalLayout extends ViewGroup {
    /** Specifies whether the width should be calculated based on the height or vice-versa  */
    public enum Direction {
        widthToHeight("widthToHeight"),
        heightToWidth("heightToWidth");

        public final String XmlName;

        private Direction(String xmlName) {
            XmlName = xmlName;
        }

        /**
         * Parses the given direction string and returns the Direction instance. This
         * should be used when inflating from xml
         */
        public static Direction parse(String value) {
            if (widthToHeight.XmlName.equals(value)) {
                return Direction.widthToHeight;
            } else if (heightToWidth.XmlName.equals(value)) {
                return Direction.heightToWidth;
            } else {
                throw new IllegalStateException("direction must be either " +
                        widthToHeight.XmlName + " or " + heightToWidth.XmlName);
            }
        }
    }

    private Direction mDirection;
    private float mRatio;

    public ProportionalLayout(Context context) {
        super(context);
    }

    public ProportionalLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initFromAttributes(context, attrs);
    }

    public ProportionalLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initFromAttributes(context, attrs);
    }

    private void initFromAttributes(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProportionalLayout);

        mDirection = Direction.parse(a.getString(R.styleable.ProportionalLayout_direction));
        mRatio = a.getFloat(R.styleable.ProportionalLayout_ratio, 1.0f);

        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (getChildCount() != 1) {
            throw new IllegalStateException("ProportionalLayout requires exactly one child");
        }

        final View child = getChildAt(0);

        // Do a first pass to get the optimal size
        measureChild(child, widthMeasureSpec, heightMeasureSpec);
        final int childWidth = child.getMeasuredWidth();
        final int childHeight = child.getMeasuredHeight();

        final int width;
        final int height;
        if (mDirection == Direction.heightToWidth) {
            width = Math.round(childHeight * mRatio);
            height = childHeight;
        } else {
            width = childWidth;
            height = Math.round(childWidth * mRatio);
        }

        // Do a second pass so that all children are informed of the new size
        measureChild(child,
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));

        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (getChildCount() != 1) {
            throw new IllegalStateException("ProportionalLayout requires exactly one child");
        }

        final View child = getChildAt(0);
        child.layout(0, 0, right-left, bottom-top);
    }

    public Direction getDirection() {
        return mDirection;
    }

    public void setDirection(Direction direction) {
        mDirection = direction;
    }

    public float getRatio() {
        return mRatio;
    }

    public void setRatio(float ratio) {
        mRatio = ratio;
    }
}
