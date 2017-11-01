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

import android.database.CharArrayBuffer;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Test cases for format utility methods.
 */
@SmallTest
public class FormatUtilsTests extends AndroidTestCase {

    public void testOverlapPoint() throws Exception {
        assertEquals(2, FormatUtils.overlapPoint("abcde", "cdefg"));
        assertEquals(-1, FormatUtils.overlapPoint("John Doe", "John Doe"));
        assertEquals(5, FormatUtils.overlapPoint("John Doe", "Doe, John"));
        assertEquals(-1, FormatUtils.overlapPoint("Mr. John Doe", "Mr. Doe, John"));
        assertEquals(13, FormatUtils.overlapPoint("John Herbert Doe", "Doe, John Herbert"));
    }

    public void testCopyToCharArrayBuffer() {
        CharArrayBuffer charArrayBuffer = new CharArrayBuffer(20);
        checkCopyToCharArrayBuffer(charArrayBuffer, null, 0);
        checkCopyToCharArrayBuffer(charArrayBuffer, "", 0);
        checkCopyToCharArrayBuffer(charArrayBuffer, "test", 4);
        // Check that it works after copying something into it.
        checkCopyToCharArrayBuffer(charArrayBuffer, "", 0);
        checkCopyToCharArrayBuffer(charArrayBuffer, "test", 4);
        checkCopyToCharArrayBuffer(charArrayBuffer, null, 0);
        // This requires a resize of the actual buffer.
        checkCopyToCharArrayBuffer(charArrayBuffer, "test test test test test", 24);
    }

    public void testCharArrayBufferToString() {
        checkCharArrayBufferToString("");
        checkCharArrayBufferToString("test");
        checkCharArrayBufferToString("test test test test test");
    }

    /** Checks that copying a string into a {@link CharArrayBuffer} and back works correctly. */
    private void checkCharArrayBufferToString(String text) {
        CharArrayBuffer buffer = new CharArrayBuffer(20);
        FormatUtils.copyToCharArrayBuffer(text, buffer);
        assertEquals(text, FormatUtils.charArrayBufferToString(buffer));
    }

    /**
     * Checks that copying into the char array buffer copies the values correctly.
     */
    private void checkCopyToCharArrayBuffer(CharArrayBuffer buffer, String value, int length) {
        FormatUtils.copyToCharArrayBuffer(value, buffer);
        assertEquals(length, buffer.sizeCopied);
        for (int index = 0; index < length; ++index) {
            assertEquals(value.charAt(index), buffer.data[index]);
        }
    }

    public void testIndexOfWordPrefix_NullPrefix() {
        assertEquals(-1, FormatUtils.indexOfWordPrefix("test", null));
    }

    public void testIndexOfWordPrefix_NullText() {
        assertEquals(-1, FormatUtils.indexOfWordPrefix(null, "TE"));
    }

    public void testIndexOfWordPrefix_MatchingPrefix() {
        checkIndexOfWordPrefix("test", "TE", 0);
        checkIndexOfWordPrefix("Test", "TE", 0);
        checkIndexOfWordPrefix("TEst", "TE", 0);
        checkIndexOfWordPrefix("TEST", "TE", 0);
        checkIndexOfWordPrefix("a test", "TE", 2);
        checkIndexOfWordPrefix("test test", "TE", 0);
        checkIndexOfWordPrefix("a test test", "TE", 2);
    }

    public void testIndexOfWordPrefix_NotMatchingPrefix() {
        checkIndexOfWordPrefix("test", "TA", -1);
        checkIndexOfWordPrefix("test type theme", "TA", -1);
        checkIndexOfWordPrefix("atest retest pretest", "TEST", -1);
        checkIndexOfWordPrefix("tes", "TEST", -1);
    }

    public void testIndexOfWordPrefix_LowerCase() {
        // The prefix match only works if the prefix is un upper case.
        checkIndexOfWordPrefix("test", "te", -1);
    }

    /**
     * Checks that getting the index of a word prefix in the given text returns the expected index.
     *
     * @param text the text in which to look for the word
     * @param wordPrefix the word prefix to look for
     * @param expectedIndex the expected value to be returned by the function
     */
    private void checkIndexOfWordPrefix(String text, String wordPrefix, int expectedIndex) {
        assertEquals(expectedIndex, FormatUtils.indexOfWordPrefix(text, wordPrefix));
    }
}
