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

import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.widget.TextView;

import junit.framework.Assert;

/**
 * Utility class to check the value of spanned text in text views.
 */
public class SpannedTestUtils {
    /**
     * Checks that the text contained in the text view matches the given HTML text.
     *
     * @param expectedHtmlText the expected text to be in the text view
     * @param textView the text view from which to get the text
     */
    public static void checkHtmlText(String expectedHtmlText, TextView textView) {
        String actualHtmlText = Html.toHtml((Spanned) textView.getText());
        if (TextUtils.isEmpty(expectedHtmlText)) {
            // If the text is empty, it does not add the <p></p> bits to it.
            Assert.assertEquals("", actualHtmlText);
        } else {
            Assert.assertEquals("<p dir=ltr>" + expectedHtmlText + "</p>\n", actualHtmlText);
        }
    }


    /**
     * Assert span exists in the correct location.
     *
     * @param seq The spannable string to check.
     * @param start The starting index.
     * @param end The ending index.
     */
    public static void assertPrefixSpan(CharSequence seq, int start, int end) {
        Assert.assertTrue(seq instanceof Spanned);
        Spanned spannable = (Spanned) seq;

        if (start > 0) {
            Assert.assertEquals(0, getNumForegroundColorSpansBetween(spannable, 0, start - 1));
        }
        Assert.assertEquals(1, getNumForegroundColorSpansBetween(spannable, start, end));
        Assert.assertEquals(0, getNumForegroundColorSpansBetween(spannable, end + 1,
                spannable.length() - 1));
    }

    private static int getNumForegroundColorSpansBetween(Spanned value, int start, int end) {
        return value.getSpans(start, end, StyleSpan.class).length;
    }

    /**
     * Asserts that the given character sequence is not a Spanned object and text is correct.
     *
     * @param seq The sequence to check.
     * @param expected The expected text.
     */
    public static void assertNotSpanned(CharSequence seq, String expected) {
        Assert.assertFalse(seq instanceof Spanned);
        Assert.assertEquals(expected, seq);
    }

    public static int getNextTransition(SpannableString seq, int start) {
        return seq.nextSpanTransition(start, seq.length(), StyleSpan.class);
    }
}
