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

import android.graphics.Typeface;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableString;

import junit.framework.TestCase;

/**
 * Unit tests for {@link TextHighlighter}.
 */
@SmallTest
public class TextHighlighterTest extends TestCase {
    private static final int TEST_PREFIX_HIGHLIGHT_COLOR = 0xFF0000;

    /** The object under test. */
    private TextHighlighter mTextHighlighter;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTextHighlighter = new TextHighlighter(Typeface.BOLD);
    }

    public void testApply_EmptyPrefix() {
        CharSequence seq = mTextHighlighter.applyPrefixHighlight("", "");
        SpannedTestUtils.assertNotSpanned(seq, "");

        seq = mTextHighlighter.applyPrefixHighlight("test", "");
        SpannedTestUtils.assertNotSpanned(seq, "test");
    }

    public void testSetText_MatchingPrefix() {
        final String prefix = "TE";

        CharSequence seq = mTextHighlighter.applyPrefixHighlight("test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mTextHighlighter.applyPrefixHighlight("Test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mTextHighlighter.applyPrefixHighlight("TEst", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 0, 1);

        seq = mTextHighlighter.applyPrefixHighlight("a test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 2, 3);
    }

    public void testSetText_NotMatchingPrefix() {
        final CharSequence seq = mTextHighlighter.applyPrefixHighlight("test", "TA");
        SpannedTestUtils.assertNotSpanned(seq, "test");
    }

    public void testSetText_FirstMatch() {
        final CharSequence seq = mTextHighlighter.applyPrefixHighlight(
                "a test's tests are not tests", "TE");
        SpannedTestUtils.assertPrefixSpan(seq, 2, 3);
    }

    public void testSetText_NoMatchingMiddleOfWord() {
        final String prefix = "TE";
        CharSequence seq = mTextHighlighter.applyPrefixHighlight("atest", prefix);
        SpannedTestUtils.assertNotSpanned(seq, "atest");

        seq = mTextHighlighter.applyPrefixHighlight("atest otest", prefix);
        SpannedTestUtils.assertNotSpanned(seq, "atest otest");

        seq = mTextHighlighter.applyPrefixHighlight("atest test", prefix);
        SpannedTestUtils.assertPrefixSpan(seq, 6, 7);
    }

    public void testSetMask_Highlight() {
        final SpannableString testString1 = new SpannableString("alongtest");
        mTextHighlighter.applyMaskingHighlight(testString1, 2, 4);
        assertEquals(2, SpannedTestUtils.getNextTransition(testString1, 0));
        assertEquals(4, SpannedTestUtils.getNextTransition(testString1, 2));

        mTextHighlighter.applyMaskingHighlight(testString1, 3, 6);
        assertEquals(2, SpannedTestUtils.getNextTransition(testString1, 0));
        assertEquals(4, SpannedTestUtils.getNextTransition(testString1, 3));

        mTextHighlighter.applyMaskingHighlight(testString1, 4, 5);
        assertEquals(3, SpannedTestUtils.getNextTransition(testString1, 2));

        mTextHighlighter.applyMaskingHighlight(testString1, 7, 8);
        assertEquals(6, SpannedTestUtils.getNextTransition(testString1, 5));
        assertEquals(7, SpannedTestUtils.getNextTransition(testString1, 6));
        assertEquals(8, SpannedTestUtils.getNextTransition(testString1, 7));
    }
}
