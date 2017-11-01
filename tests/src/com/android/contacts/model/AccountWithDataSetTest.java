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

package com.android.contacts.model;

import android.os.Bundle;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.contacts.model.account.AccountWithDataSet;

import com.google.common.collect.Lists;

import java.util.List;

/**
 * Test case for {@link AccountWithDataSet}.
 *
 * adb shell am instrument -w -e class com.android.contacts.model.AccountWithDataSetTest \
       com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class AccountWithDataSetTest extends AndroidTestCase {
    public void testStringifyAndUnstringify() {
        AccountWithDataSet a1 = new AccountWithDataSet("name1", "typeA", null);
        AccountWithDataSet a2 = new AccountWithDataSet("name2", "typeB", null);
        AccountWithDataSet a3 = new AccountWithDataSet("name3", "typeB", "dataset");

        // stringify() & unstringify
        AccountWithDataSet a1r = AccountWithDataSet.unstringify(a1.stringify());
        AccountWithDataSet a2r = AccountWithDataSet.unstringify(a2.stringify());
        AccountWithDataSet a3r = AccountWithDataSet.unstringify(a3.stringify());

        assertEquals(a1, a1r);
        assertEquals(a2, a2r);
        assertEquals(a3, a3r);

        MoreAsserts.assertNotEqual(a1, a2r);
        MoreAsserts.assertNotEqual(a1, a3r);

        MoreAsserts.assertNotEqual(a2, a1r);
        MoreAsserts.assertNotEqual(a2, a3r);

        MoreAsserts.assertNotEqual(a3, a1r);
        MoreAsserts.assertNotEqual(a3, a2r);
    }

    public void testStringifyAndUnstringifyLocalAccount() {
        final String stringified = AccountWithDataSet.getNullAccount().stringify();

        final AccountWithDataSet restored = AccountWithDataSet.unstringify(stringified);

        assertEquals(AccountWithDataSet.getNullAccount(), restored);
    }

    public void testStringifyListAndUnstringify() {
        AccountWithDataSet a1 = new AccountWithDataSet("name1", "typeA", null);
        AccountWithDataSet a2 = new AccountWithDataSet("name2", "typeB", null);
        AccountWithDataSet a3 = new AccountWithDataSet("name3", "typeB", "dataset");

        // Empty list
        assertEquals(0, stringifyListAndUnstringify().size());

        // 1 element
        final List<AccountWithDataSet> listA = stringifyListAndUnstringify(a1);
        assertEquals(1, listA.size());
        assertEquals(a1, listA.get(0));

        // 2 elements
        final List<AccountWithDataSet> listB = stringifyListAndUnstringify(a2, a1);
        assertEquals(2, listB.size());
        assertEquals(a2, listB.get(0));
        assertEquals(a1, listB.get(1));

        // 3 elements
        final List<AccountWithDataSet> listC = stringifyListAndUnstringify(a3, a2, a1);
        assertEquals(3, listC.size());
        assertEquals(a3, listC.get(0));
        assertEquals(a2, listC.get(1));
        assertEquals(a1, listC.get(2));
    }

    private static List<AccountWithDataSet> stringifyListAndUnstringify(
            AccountWithDataSet... accounts) {

        List<AccountWithDataSet> list = Lists.newArrayList(accounts);
        return AccountWithDataSet.unstringifyList(AccountWithDataSet.stringifyList(list));
    }

    public void testParcelable() {
        AccountWithDataSet a1 = new AccountWithDataSet("name1", "typeA", null);
        AccountWithDataSet a2 = new AccountWithDataSet("name2", "typeB", null);
        AccountWithDataSet a3 = new AccountWithDataSet("name3", "typeB", "dataset");

        // Parcel them & unpercel.
        final Bundle b = new Bundle();
        b.putParcelable("a1", a1);
        b.putParcelable("a2", a2);
        b.putParcelable("a3", a3);

        AccountWithDataSet a1r = b.getParcelable("a1");
        AccountWithDataSet a2r = b.getParcelable("a2");
        AccountWithDataSet a3r = b.getParcelable("a3");

        assertEquals(a1, a1r);
        assertEquals(a2, a2r);
        assertEquals(a3, a3r);

        MoreAsserts.assertNotEqual(a1, a2r);
        MoreAsserts.assertNotEqual(a1, a3r);

        MoreAsserts.assertNotEqual(a2, a1r);
        MoreAsserts.assertNotEqual(a2, a3r);

        MoreAsserts.assertNotEqual(a3, a1r);
        MoreAsserts.assertNotEqual(a3, a2r);
    }
}
