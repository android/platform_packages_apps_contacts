/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.contacts;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Pair;

import com.android.contacts.model.dataitem.DataItem;
import com.android.contacts.model.dataitem.EmailDataItem;
import com.android.contacts.model.dataitem.ImDataItem;

/**
 * Tests for {@link ContactsUtils}.
 */
@SmallTest
public class ContactsUtilsTests extends AndroidTestCase {

    private static final String TEST_ADDRESS = "user@example.org";
    private static final String TEST_PROTOCOL = "prot%col";

    public void testIsGraphicNull() throws Exception {
        assertFalse(ContactsUtils.isGraphic(null));
    }

    public void testIsGraphicEmpty() throws Exception {
        assertFalse(ContactsUtils.isGraphic(""));
    }

    public void testIsGraphicSpaces() throws Exception {
        assertFalse(ContactsUtils.isGraphic("  "));
    }

    public void testIsGraphicPunctuation() throws Exception {
        assertTrue(ContactsUtils.isGraphic("."));
    }

    public void testAreObjectsEqual() throws Exception {
        assertTrue("null:null", ContactsUtils.areObjectsEqual(null, null));
        assertTrue("1:1", ContactsUtils.areObjectsEqual(1, 1));

        assertFalse("null:1", ContactsUtils.areObjectsEqual(null, 1));
        assertFalse("1:null", ContactsUtils.areObjectsEqual(1, null));
        assertFalse("1:2", ContactsUtils.areObjectsEqual(1, 2));
    }

    public void testAreIntentActionEqual() throws Exception {
        assertTrue("1", ContactsUtils.areIntentActionEqual(null, null));
        assertTrue("1", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("a")));

        assertFalse("11", ContactsUtils.areIntentActionEqual(new Intent("a"), null));
        assertFalse("12", ContactsUtils.areIntentActionEqual(null, new Intent("a")));

        assertFalse("21", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent()));
        assertFalse("22", ContactsUtils.areIntentActionEqual(new Intent(), new Intent("b")));
        assertFalse("23", ContactsUtils.areIntentActionEqual(new Intent("a"), new Intent("b")));
    }

    public void testImIntentCustom() throws Exception {
        // Custom IM types have encoded authority. We send the imto Intent here, because
        // legacy third party apps might not accept xmpp yet
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
        values.put(Im.CUSTOM_PROTOCOL, TEST_PROTOCOL);
        values.put(Im.DATA, TEST_ADDRESS);
        final ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        final Pair<Intent, Intent> intents = ContactsUtils.buildImIntent(getContext(), im);
        final Intent imIntent = intents.first;

        assertEquals(Intent.ACTION_SENDTO, imIntent.getAction());

        final Uri data = imIntent.getData();
        assertEquals("imto", data.getScheme());
        assertEquals(TEST_PROTOCOL, data.getAuthority());
        assertEquals(TEST_ADDRESS, data.getPathSegments().get(0));

        assertNull(intents.second);
    }

    public void testImIntent() throws Exception {
        // Test GTalk XMPP URI. No chat capabilities provided
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        values.put(Im.DATA, TEST_ADDRESS);
        final ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        final Pair<Intent, Intent> intents = ContactsUtils.buildImIntent(getContext(), im);
        final Intent imIntent = intents.first;

        assertEquals(Intent.ACTION_SENDTO, imIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", imIntent.getData().toString());

        assertNull(intents.second);
    }

    public void testImIntentWithAudio() throws Exception {
        // Test GTalk XMPP URI. Audio chat capabilities provided
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        values.put(Im.DATA, TEST_ADDRESS);
        values.put(Im.CHAT_CAPABILITY, Im.CAPABILITY_HAS_VOICE | Im.CAPABILITY_HAS_VIDEO);
        final ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        final Pair<Intent, Intent> intents = ContactsUtils.buildImIntent(getContext(), im);
        final Intent imIntent = intents.first;

        assertEquals(Intent.ACTION_SENDTO, imIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", imIntent.getData().toString());

        final Intent secondaryIntent = intents.second;
        assertEquals(Intent.ACTION_SENDTO, secondaryIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?call", secondaryIntent.getData().toString());
    }

    public void testImIntentWithVideo() throws Exception {
        // Test GTalk XMPP URI. Video chat capabilities provided
        final ContentValues values = new ContentValues();
        values.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
        values.put(Im.TYPE, Im.TYPE_HOME);
        values.put(Im.PROTOCOL, Im.PROTOCOL_GOOGLE_TALK);
        values.put(Im.DATA, TEST_ADDRESS);
        values.put(Im.CHAT_CAPABILITY, Im.CAPABILITY_HAS_VOICE | Im.CAPABILITY_HAS_VIDEO |
                Im.CAPABILITY_HAS_VOICE);
        final ImDataItem im = (ImDataItem) DataItem.createFrom(values);

        final Pair<Intent, Intent> intents = ContactsUtils.buildImIntent(getContext(), im);
        final Intent imIntent = intents.first;

        assertEquals(Intent.ACTION_SENDTO, imIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", imIntent.getData().toString());

        final Intent secondaryIntent = intents.second;
        assertEquals(Intent.ACTION_SENDTO, secondaryIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?call", secondaryIntent.getData().toString());
    }


    public void testImEmailIntent() throws Exception {
        // Email addresses are treated as Google Talk entries
        // This test only tests the VIDEO+CAMERA case. The other cases have been addressed by the
        // Im tests
        final ContentValues values = new ContentValues();
        values.put(Email.MIMETYPE, Email.CONTENT_ITEM_TYPE);
        values.put(Email.TYPE, Email.TYPE_HOME);
        values.put(Email.DATA, TEST_ADDRESS);
        values.put(Email.CHAT_CAPABILITY, Im.CAPABILITY_HAS_VOICE | Im.CAPABILITY_HAS_VIDEO |
                Im.CAPABILITY_HAS_VOICE);
        final ImDataItem im = ImDataItem.createFromEmail(
                (EmailDataItem) DataItem.createFrom(values));

        final Pair<Intent, Intent> intents = ContactsUtils.buildImIntent(getContext(), im);
        final Intent imIntent = intents.first;

        assertEquals(Intent.ACTION_SENDTO, imIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?message", imIntent.getData().toString());

        final Intent secondaryIntent = intents.second;
        assertEquals(Intent.ACTION_SENDTO, secondaryIntent.getAction());
        assertEquals("xmpp:" + TEST_ADDRESS + "?call", secondaryIntent.getData().toString());
    }
}
