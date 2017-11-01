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

import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

/**
 * Unit tests for {@link SearchUtil}.
 */
@SmallTest
public class SearchUtilTest extends TestCase {

    public void testFindMatchingLine() {
        final String actual = "this is a long test string.\nWith potentially many lines.\n" +
                "test@google.com\nhello\nblah\n'leading punc";

        SearchUtil.MatchedLine matched = SearchUtil.findMatchingLine(actual, "poten");
        assertEquals("With potentially many lines.", matched.line);
        assertEquals(5, matched.startIndex);

        // Full line match.
        matched = SearchUtil.findMatchingLine(actual, "hello");
        assertEquals("hello", matched.line);
        assertEquals(0, matched.startIndex);

        // First line match
        matched = SearchUtil.findMatchingLine(actual, "this");
        assertEquals("this is a long test string.", matched.line);
        assertEquals(0, matched.startIndex);

        // Last line match
        matched = SearchUtil.findMatchingLine(actual, "punc");
        assertEquals("'leading punc", matched.line);
        assertEquals(9, matched.startIndex);
    }

    public void testContains() {
        final String actual = "this is a long test string.\nWith potentially many lines.\n" +
                "test@google.com\nhello\nblah\n'leading punc";
        assertEquals(0, SearchUtil.contains(actual, "this"));
        assertEquals(10, SearchUtil.contains(actual, "lon"));

        assertEquals(1, SearchUtil.contains("'leading punc", "lead"));
        assertEquals(9, SearchUtil.contains("'leading punc", "punc"));

    }

    public void testContainsNotFound() {
        final String actual = "this is a long test string.\nWith potentially many lines.\n" +
                "test@google.com\nhello\nblah\n'leading punc";

        // Non-prefix
        assertEquals(-1, SearchUtil.contains(actual, "ith"));
        assertEquals(-1, SearchUtil.contains(actual, "ing"));

        // Complete misses
        assertEquals(-1, SearchUtil.contains(actual, "thisx"));
        assertEquals(-1, SearchUtil.contains(actual, "manyx"));
        assertEquals(-1, SearchUtil.contains(actual, "hellox"));

        // Test for partial match of start of query to end of line
        assertEquals(-1, SearchUtil.contains(actual, "punctual"));
    }

    public void testFindNextTokenStart() {
        final String actual = "....hello.kitty";
        //                     012345678901234

        // Find first token.
        assertEquals(4, SearchUtil.findNextTokenStart(actual, 0));
        assertEquals(4, SearchUtil.findNextTokenStart(actual, 1));
        assertEquals(4, SearchUtil.findNextTokenStart(actual, 2));
        assertEquals(4, SearchUtil.findNextTokenStart(actual, 3));

        // Find second token.
        assertEquals(10, SearchUtil.findNextTokenStart(actual, 4));
        assertEquals(10, SearchUtil.findNextTokenStart(actual, 5));
        assertEquals(10, SearchUtil.findNextTokenStart(actual, 6));
        assertEquals(10, SearchUtil.findNextTokenStart(actual, 7));
        assertEquals(10, SearchUtil.findNextTokenStart(actual, 8));
        assertEquals(10, SearchUtil.findNextTokenStart(actual, 9));

        // No token.
        assertEquals(actual.length(), SearchUtil.findNextTokenStart(actual, 10));
        assertEquals(actual.length(), SearchUtil.findNextTokenStart(actual, 11));
        assertEquals(actual.length(), SearchUtil.findNextTokenStart(actual, 12));
        assertEquals(actual.length(), SearchUtil.findNextTokenStart(actual, 13));
        assertEquals(actual.length(), SearchUtil.findNextTokenStart(actual, 14));
    }

    public void testCleanStartAndEndOfSearchQuery() {
        assertEquals("test", SearchUtil.cleanStartAndEndOfSearchQuery("...test..."));
        assertEquals("test", SearchUtil.cleanStartAndEndOfSearchQuery(" test "));
        assertEquals("test", SearchUtil.cleanStartAndEndOfSearchQuery(" ||test"));
        assertEquals("test", SearchUtil.cleanStartAndEndOfSearchQuery("test.."));
    }

}
