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
 * limitations under the License
 */

package com.android.contacts.database;

import android.database.Cursor;
import android.net.Uri;
import android.test.InstrumentationTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit test for {@link NoNullCursorAsyncQueryHandler}
 */
@SmallTest
public class NoNullCursorAsyncQueryHandlerTest extends InstrumentationTestCase {

    private MockContentResolver mMockContentResolver;

    private static final String AUTHORITY = "com.android.contacts.common.unittest";
    private static final Uri URI = Uri.parse("content://" + AUTHORITY);
    private static final String[] PROJECTION = new String[]{"column1", "column2"};

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mMockContentResolver = new MockContentResolver();
        final MockContentProvider mMockContentProvider = new MockContentProvider() {
            @Override
            public Cursor query(Uri uri, String[] projection, String selection,
                    String[] selectionArgs,
                    String sortOrder) {
                return null;
            }
        };

        mMockContentResolver.addProvider(AUTHORITY, mMockContentProvider);
    }

    public void testCursorIsNotNull() throws Throwable {

        final CountDownLatch latch = new CountDownLatch(1);
        final ObjectHolder<Cursor> cursorHolder = ObjectHolder.newInstance();
        final ObjectHolder<Boolean> ranHolder = ObjectHolder.newInstance(false);

        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {

                NoNullCursorAsyncQueryHandler handler = new NoNullCursorAsyncQueryHandler(
                        mMockContentResolver) {
                    @Override
                    protected void onNotNullableQueryComplete(int token, Object cookie,
                            Cursor cursor) {
                        cursorHolder.obj = cursor;
                        ranHolder.obj = true;
                        latch.countDown();
                    }
                };
                handler.startQuery(1, null, URI, PROJECTION, null, null, null);
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        assertFalse(cursorHolder.obj == null);
        assertTrue(ranHolder.obj);
    }

    public void testCursorContainsCorrectCookies() throws Throwable {
        final ObjectHolder<Boolean> ranHolder = ObjectHolder.newInstance(false);
        final CountDownLatch latch = new CountDownLatch(1);
        final ObjectHolder<Object> cookieHolder = ObjectHolder.newInstance();
        final String cookie = "TEST COOKIE";
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                final NoNullCursorAsyncQueryHandler handler = new NoNullCursorAsyncQueryHandler(
                        mMockContentResolver) {
                    @Override
                    protected void onNotNullableQueryComplete(int token, Object cookie,
                            Cursor cursor) {
                        ranHolder.obj = true;
                        cookieHolder.obj = cookie;
                        latch.countDown();
                    }
                };
                handler.startQuery(1, cookie, URI, PROJECTION, null, null, null);
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        assertSame(cookie, cookieHolder.obj);
        assertTrue(ranHolder.obj);
    }

    public void testCursorContainsCorrectColumns() throws Throwable {
        final ObjectHolder<Boolean> ranHolder = ObjectHolder.newInstance(false);
        final CountDownLatch latch = new CountDownLatch(1);
        final ObjectHolder<Cursor> cursorHolder = ObjectHolder.newInstance();
        final String cookie = "TEST COOKIE";
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                final NoNullCursorAsyncQueryHandler handler = new NoNullCursorAsyncQueryHandler(
                        mMockContentResolver) {
                    @Override
                    protected void onNotNullableQueryComplete(int token, Object cookie,
                            Cursor cursor) {
                        ranHolder.obj = true;
                        cursorHolder.obj = cursor;
                        latch.countDown();
                    }
                };
                handler.startQuery(1, cookie, URI, PROJECTION, null, null, null);
            }
        });

        latch.await(5, TimeUnit.SECONDS);
        assertSame(PROJECTION, cursorHolder.obj.getColumnNames());
        assertTrue(ranHolder.obj);
    }

    private static class ObjectHolder<T> {
        public T obj;

        public static <E> ObjectHolder<E> newInstance() {
            return new ObjectHolder<E>();
        }

        public static <E> ObjectHolder<E> newInstance(E value) {
            ObjectHolder<E> holder = new ObjectHolder<E>();
            holder.obj = value;
            return holder;
        }
    }
}
