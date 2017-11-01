/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.contacts.group;

import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.list.ContactsSectionIndexer;

import java.util.Arrays;
import java.util.List;

@SmallTest
public class GroupUtilTest extends AndroidTestCase {

    public void testNeedTrimming() {
        final int zeroCount = 0;
        final int emptyPositions[] = new int[]{};
        final int emptyCounts[] = new int[]{};
        assertFalse(GroupUtil.needTrimming(zeroCount, emptyPositions, emptyCounts));

        final int count = 22;
        int positions[] = new int[]{0, 1, 3, 5, 8, 9};
        int counts[] = new int[]{1, 2, 2, 3, 1, 2};
        assertFalse(GroupUtil.needTrimming(count, positions, counts));

        positions = new int[]{0, 1, 7, 9, 16, 17, 19, 20};
        counts = new int[]{1, 6, 2, 7, 1, 2, 1, 2};
        assertTrue(GroupUtil.needTrimming(count, positions, counts));
    }

    public void testUpdateBundle_smallSet() {
        final Bundle bundle = new Bundle();
        final String[] sections = new String[]{"…", "A", "I", "T", "W", "Y", "Z", "#"};
        final int[] counts = new int[]{1, 6, 2, 7, 1, 2, 1, 2};
        final Integer[] subscripts = new Integer[]{1, 2, 5, 7, 8, 10, 11, 15, 16, 17, 18};
        final List<Integer> subscriptsList = Arrays.asList(subscripts);
        final ContactsSectionIndexer indexer = new ContactsSectionIndexer(sections, counts);

        GroupUtil.updateBundle(bundle, indexer, subscriptsList, sections, counts);

        final String[] newSections = new String[]{"…", "A", "T", "Z", "#"};
        final int[] newCounts = new int[]{1, 3, 4, 1, 2};

        assertNotNull(bundle.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES));
        MoreAsserts.assertEquals("Wrong sections!", newSections, bundle.getStringArray(Contacts
                .EXTRA_ADDRESS_BOOK_INDEX_TITLES));

        assertNotNull(bundle.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS));
        MoreAsserts.assertEquals("Wrong counts!", newCounts, bundle.getIntArray(Contacts
                .EXTRA_ADDRESS_BOOK_INDEX_COUNTS));
    }

    public void testUpdateBundle_mediumSet() {
        final Bundle bundle = new Bundle();
        final String[] sections = new String[]{"A", "B", "C", "D", "E", "F", "G", "H", "J",
                "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "V", "W", "X", "Y", "Z"};
        final int[] counts = new int[]{81, 36, 84, 55, 28, 15, 18, 38, 145, 60, 41, 73, 15, 2, 56,
                1, 74, 73, 45, 14, 28, 9, 18, 21};
        final Integer[] subscripts = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14,
                15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56,
                57, 58, 59, 60, 61, 62, 63, 64, 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77,
                78, 79, 80, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93, 94, 95, 96, 97, 98,
                99, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115,
                116, 117, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127, 128, 129, 130, 131, 132,
                133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149,
                150, 151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166,
                167, 168, 169, 170, 171, 172, 173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183,
                184, 185, 186, 187, 188, 189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200,
                201, 202, 203, 204, 205, 206, 207, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217,
                218, 219, 220, 221, 222, 223, 224, 225, 226, 227, 228, 229, 230, 231, 232, 233, 234,
                235, 236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251,
                252, 253, 254, 255, 256, 257, 258, 259, 260, 261, 262, 263, 264, 265, 266, 267, 268,
                269, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282, 283, 284, 285,
                286, 287, 288, 289, 290, 291, 292, 293, 294, 295, 296, 297, 298, 299, 300, 301, 302,
                303, 304, 305, 306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319,
                320, 321, 322, 323, 324, 325, 326, 327, 328, 329, 330, 331, 332, 333, 334, 335, 336,
                337, 344, 347, 348, 349, 350, 351, 352, 353, 354, 495, 496, 497, 498, 499, 558, 559,
                597, 598, 599, 600, 601, 602, 668, 669, 670, 671, 672, 673, 746, 747, 820, 821, 885,
                886, 887, 888, 889, 890, 891, 892, 893, 894, 939, 979, 980, 981, 982, 983, 984, 985,
                986, 987, 988, 989, 990, 991, 992, 993, 994, 995, 996, 997, 998, 999, 1000, 1001,
                1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015,
                1016, 1017, 1018, 1019, 1020, 1021, 1022, 1023, 1024, 1025, 1026, 1027, 1028, 1029};
        final List<Integer> subscriptsList = Arrays.asList(subscripts);
        final ContactsSectionIndexer indexer = new ContactsSectionIndexer(sections, counts);

        GroupUtil.updateBundle(bundle, indexer, subscriptsList, sections, counts);

        final String[] newSections = new String[]{"A", "H", "J", "K", "L", "M", "N", "O", "P",
                "R", "S", "T", "V", "W"};
        final int[] newCounts = new int[]{1, 8, 140, 58, 37, 65, 15, 2, 55, 72, 63, 44, 14, 25};

        assertNotNull(bundle.getStringArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_TITLES));
        MoreAsserts.assertEquals("Wrong sections!", newSections, bundle.getStringArray(Contacts
                .EXTRA_ADDRESS_BOOK_INDEX_TITLES));

        assertNotNull(bundle.getIntArray(Contacts.EXTRA_ADDRESS_BOOK_INDEX_COUNTS));
        MoreAsserts.assertEquals("Wrong counts!", newCounts, bundle.getIntArray(Contacts
                .EXTRA_ADDRESS_BOOK_INDEX_COUNTS));
    }

}
