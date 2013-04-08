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

package com.android.contacts.format;

import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

/**
 * Highlights the text in a text field.
 */
public class PrefixHighlighter {
    private final int mPrefixHighlightColor;

    private ForegroundColorSpan mPrefixColorSpan;

    public PrefixHighlighter(int prefixHighlightColor) {
        mPrefixHighlightColor = prefixHighlightColor;
    }

    /**
     * Sets the text on the given text view, highlighting the word that matches the given prefix.
     *
     * @param view the view on which to set the text
     * @param text the string to use as the text
     * @param prefix the prefix to look for
     */
    public void setText(TextView view, String text, char[] prefix) {
        view.setText(apply(text, prefix));
    }

    /**
     * Returns a CharSequence which highlights the given prefix if found in the given text.
     *
     * @param text the text to which to apply the highlight
     * @param prefix the prefix to look for
     */
    public CharSequence apply(CharSequence text, char[] prefix) {
        int index = FormatUtils.indexOfWordPrefix(text, prefix);
        if (index == -1 && prefix != null && prefix.length > 1) {
            // prefix can contain preedit character, so trying look again.
            char[] prefix2 = new char[prefix.length - 1];
            System.arraycopy(prefix, 0, prefix2, 0, prefix2.length);
            prefix = prefix2;
            index = FormatUtils.indexOfWordPrefix(text, prefix);
        }
        if (index != -1) {
            if (mPrefixColorSpan == null) {
                mPrefixColorSpan = new ForegroundColorSpan(mPrefixHighlightColor);
            }

            SpannableString result = new SpannableString(text);
            result.setSpan(mPrefixColorSpan, index, index + prefix.length, 0 /* flags */);
            return result;
        } else {
            return text;
        }
    }
}
