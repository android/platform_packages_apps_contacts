/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.app.Activity;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Trace;
import androidx.core.content.ContextCompat;

import com.android.contacts.R;
import com.android.contacts.activities.PeopleActivity;

public class MaterialColorMapUtils {
    private final TypedArray sPrimaryColors;
    private final TypedArray sSecondaryColors;

    public MaterialColorMapUtils(Resources resources) {
        sPrimaryColors = resources.obtainTypedArray(
                com.android.contacts.R.array.letter_tile_colors);
        sSecondaryColors = resources.obtainTypedArray(
                com.android.contacts.R.array.letter_tile_colors_dark);
    }

    public static class MaterialPalette implements Parcelable {
        public MaterialPalette(int primaryColor, int secondaryColor) {
            mPrimaryColor = primaryColor;
            mSecondaryColor = secondaryColor;
        }
        public final int mPrimaryColor;
        public final int mSecondaryColor;

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            MaterialPalette other = (MaterialPalette) obj;
            if (mPrimaryColor != other.mPrimaryColor) {
                return false;
            }
            if (mSecondaryColor != other.mSecondaryColor) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mPrimaryColor;
            result = prime * result + mSecondaryColor;
            return result;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mPrimaryColor);
            dest.writeInt(mSecondaryColor);
        }

        private MaterialPalette(Parcel in) {
            mPrimaryColor = in.readInt();
            mSecondaryColor = in.readInt();
        }

        public static final Creator<MaterialPalette> CREATOR = new Creator<MaterialPalette>() {
                @Override
                public MaterialPalette createFromParcel(Parcel in) {
                    return new MaterialPalette(in);
                }

                @Override
                public MaterialPalette[] newArray(int size) {
                    return new MaterialPalette[size];
                }
        };
    }

    /**
     * Return primary and secondary colors from the Material color palette that are similar to
     * {@param color}.
     */
    public MaterialPalette calculatePrimaryAndSecondaryColor(int color) {
        Trace.beginSection("calculatePrimaryAndSecondaryColor");

        final float colorHue = hue(color);
        float minimumDistance = Float.MAX_VALUE;
        int indexBestMatch = 0;
        for (int i = 0; i < sPrimaryColors.length(); i++) {
            final int primaryColor = sPrimaryColors.getColor(i, 0);
            final float comparedHue = hue(primaryColor);
            // No need to be perceptually accurate when calculating color distances since
            // we are only mapping to 15 colors. Being slightly inaccurate isn't going to change
            // the mapping very often.
            final float distance = Math.abs(comparedHue - colorHue);
            if (distance < minimumDistance) {
                minimumDistance = distance;
                indexBestMatch = i;
            }
        }

        Trace.endSection();
        return new MaterialPalette(sPrimaryColors.getColor(indexBestMatch, 0),
                sSecondaryColors.getColor(indexBestMatch, 0));
    }

    public static MaterialPalette getDefaultPrimaryAndSecondaryColors(Resources resources) {
        final int primaryColor = resources.getColor(
                R.color.quickcontact_default_photo_tint_color);
        final int secondaryColor = resources.getColor(
                R.color.quickcontact_default_photo_tint_color_dark);
        return new MaterialPalette(primaryColor, secondaryColor);
    }

    /**
     * Returns the hue component of a color int.
     *
     * @return A value between 0.0f and 1.0f
     */
    public static float hue(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        int V = Math.max(b, Math.max(r, g));
        int temp = Math.min(b, Math.min(r, g));

        float H;

        if (V == temp) {
            H = 0;
        } else {
            final float vtemp = V - temp;
            final float cr = (V - r) / vtemp;
            final float cg = (V - g) / vtemp;
            final float cb = (V - b) / vtemp;

            if (r == V) {
                H = cb - cg;
            } else if (g == V) {
                H = 2 + cr - cb;
            } else {
                H = 4 + cg - cr;
            }

            H /= 6.f;
            if (H < 0) {
                H++;
            }
        }

        return H;
    }

    /**
     * Returns status bar color for group view and non-group views.
     */
    public static int getStatusBarColor(Activity activity) {
        final boolean isGroupView = activity instanceof PeopleActivity
                && ((PeopleActivity) activity).isGroupView();
        return isGroupView
                ? ContextCompat.getColor(activity, R.color.group_primary_color_dark)
                : ContextCompat.getColor(activity, R.color.primary_color_dark);
    }

    /**
     * Returns toolbar color for group view and non-group views.
     */
    public static int getToolBarColor(Activity activity) {
        final boolean isGroupView = activity instanceof PeopleActivity
                && ((PeopleActivity) activity).isGroupView();
        return isGroupView
                ? ContextCompat.getColor(activity, R.color.group_primary_color)
                : ContextCompat.getColor(activity, R.color.primary_color);
    }
}
