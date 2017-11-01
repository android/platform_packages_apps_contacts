package com.android.contacts;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

/**
 * Tests for MoreContactsUtils.
 */
@SmallTest
public class MoreContactUtilsTest extends TestCase {

    public void testShouldCollapse() throws Exception {
        assertCollapses("1", true, null, null, null, null);
        assertCollapses("2", true, "a", "b", "a", "b");

        assertCollapses("11", false, "a", null, null, null);
        assertCollapses("12", false, null, "a", null, null);
        assertCollapses("13", false, null, null, "a", null);
        assertCollapses("14", false, null, null, null, "a");

        assertCollapses("21", false, "a", "b", null, null);
        assertCollapses("22", false, "a", "b", "a", null);
        assertCollapses("23", false, "a", "b", null, "b");
        assertCollapses("24", false, "a", "b", "a", "x");
        assertCollapses("25", false, "a", "b", "x", "b");

        assertCollapses("31", false, null, null, "a", "b");
        assertCollapses("32", false, "a", null, "a", "b");
        assertCollapses("33", false, null, "b", "a", "b");
        assertCollapses("34", false, "a", "x", "a", "b");
        assertCollapses("35", false, "x", "b", "a", "b");

        assertCollapses("41", true, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE, null);
        assertCollapses("42", true, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, "1");

        assertCollapses("51", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, "2");
        assertCollapses("52", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, null);
        assertCollapses("53", false, Phone.CONTENT_ITEM_TYPE, null, Phone.CONTENT_ITEM_TYPE, "2");

        // Test phone numbers
        assertCollapses("60", true, Phone.CONTENT_ITEM_TYPE, "1234567", Phone.CONTENT_ITEM_TYPE,
                "1234567");
        assertCollapses("61", false, Phone.CONTENT_ITEM_TYPE, "1234567", Phone.CONTENT_ITEM_TYPE,
                "1234568");
        assertCollapses("62", true, Phone.CONTENT_ITEM_TYPE, "1234567;0", Phone.CONTENT_ITEM_TYPE,
                "1234567;0");
        assertCollapses("63", false, Phone.CONTENT_ITEM_TYPE, "1234567;89321",
                Phone.CONTENT_ITEM_TYPE, "1234567;89322");
        assertCollapses("64", true, Phone.CONTENT_ITEM_TYPE, "1234567;89321",
                Phone.CONTENT_ITEM_TYPE, "1234567;89321");
        assertCollapses("65", false, Phone.CONTENT_ITEM_TYPE, "1234567;0111111111",
                Phone.CONTENT_ITEM_TYPE, "1234567;");
        assertCollapses("66", false, Phone.CONTENT_ITEM_TYPE, "12345675426;91970xxxxx",
                Phone.CONTENT_ITEM_TYPE, "12345675426");
        assertCollapses("67", false, Phone.CONTENT_ITEM_TYPE, "12345675426;23456xxxxx",
                Phone.CONTENT_ITEM_TYPE, "12345675426;234567xxxx");
        assertCollapses("68", true, Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567");
        assertCollapses("69", false, Phone.CONTENT_ITEM_TYPE, "1234567;1234567;1234567",
                Phone.CONTENT_ITEM_TYPE, "1234567;1234567");

        // test some numbers with country and area code
        assertCollapses("70", true, Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678",
                Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678");
        assertCollapses("71", true, Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678",
                Phone.CONTENT_ITEM_TYPE, "+49 (89)12345678");
        assertCollapses("72", true, Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234",
                Phone.CONTENT_ITEM_TYPE, "+49 (8092)1234");
        assertCollapses("73", false, Phone.CONTENT_ITEM_TYPE, "0049 (8092) 1234",
                Phone.CONTENT_ITEM_TYPE, "+49/80921234");
        assertCollapses("74", false, Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345678",
                Phone.CONTENT_ITEM_TYPE, "+49 (89) 12345679");

        // test special handling of collapsing country code for NANP region only
        // This is non symmetrical, because we prefer the number with the +1.
        assertEquals("100", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "+1 (415) 555-1212", Phone.CONTENT_ITEM_TYPE, "(415) 555-1212"));
        assertEquals("101", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "+14155551212", Phone.CONTENT_ITEM_TYPE, "4155551212"));
        assertEquals("102", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "(415) 555-1212", Phone.CONTENT_ITEM_TYPE, "+1 (415) 555-1212"));
        assertEquals("103", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "4155551212", Phone.CONTENT_ITEM_TYPE, "+14155551212"));
        // Require explicit +1 country code declaration to collapse
        assertEquals("104", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "1-415-555-1212", Phone.CONTENT_ITEM_TYPE, "415-555-1212"));
        assertEquals("105", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "14155551212", Phone.CONTENT_ITEM_TYPE, "4155551212"));
        assertEquals("106", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "+1 (415) 555-1212", Phone.CONTENT_ITEM_TYPE, " 1 (415) 555-1212"));
        assertEquals("107", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "+14155551212", Phone.CONTENT_ITEM_TYPE, " 14155551212"));
        assertEquals("108", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "1 (415) 555-1212", Phone.CONTENT_ITEM_TYPE, "+1 (415) 555-1212"));
        assertEquals("109", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "14155551212", Phone.CONTENT_ITEM_TYPE, "+14155551212"));

        // test some numbers with wait symbol and area code
        assertCollapses("200", true, Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234;89321",
                Phone.CONTENT_ITEM_TYPE, "+49/80921234;89321");
        assertCollapses("201", false, Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234;89321",
                Phone.CONTENT_ITEM_TYPE, "+49/80921235;89321");
        assertCollapses("202", false, Phone.CONTENT_ITEM_TYPE, "+49 (8092) 1234;89322",
                Phone.CONTENT_ITEM_TYPE, "+49/80921234;89321");
        assertCollapses("203", true, Phone.CONTENT_ITEM_TYPE, "1234567;+49 (8092) 1234",
                Phone.CONTENT_ITEM_TYPE, "1234567;+49/80921234");

        assertCollapses("300", true, Phone.CONTENT_ITEM_TYPE, "", Phone.CONTENT_ITEM_TYPE, "");

        assertCollapses("301", false, Phone.CONTENT_ITEM_TYPE, "1", Phone.CONTENT_ITEM_TYPE, "");

        assertCollapses("302", false, Phone.CONTENT_ITEM_TYPE, "", Phone.CONTENT_ITEM_TYPE, "1");

        assertCollapses("303", true, Phone.CONTENT_ITEM_TYPE, "---", Phone.CONTENT_ITEM_TYPE, "---");

        assertCollapses("304", false, Phone.CONTENT_ITEM_TYPE, "1-/().", Phone.CONTENT_ITEM_TYPE,
                "--$%1");

        // Test numbers using keypad letters. This is non-symmetrical, because we prefer
        // the version with letters.
        assertEquals("400", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "abcdefghijklmnopqrstuvwxyz", Phone.CONTENT_ITEM_TYPE,
                "22233344455566677778889999"));
        assertEquals("401", false, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "22233344455566677778889999", Phone.CONTENT_ITEM_TYPE,
                "abcdefghijklmnopqrstuvwxyz"));

        assertCollapses("402", false, Phone.CONTENT_ITEM_TYPE, "1;2", Phone.CONTENT_ITEM_TYPE,
                "12");

        assertCollapses("403", false, Phone.CONTENT_ITEM_TYPE, "1,2", Phone.CONTENT_ITEM_TYPE,
                "12");
    }

    public void testShouldCollapse_collapsesSameNumberWithDifferentFormats() {
        assertEquals("1", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "555-1212", Phone.CONTENT_ITEM_TYPE, "5551212"));
        assertEquals("1", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "415-555-1212", Phone.CONTENT_ITEM_TYPE, "(415) 555-1212"));
        assertEquals("2", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "4155551212", Phone.CONTENT_ITEM_TYPE, "(415) 555-1212"));
        assertEquals("3", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "1-415-555-1212", Phone.CONTENT_ITEM_TYPE, "1 (415) 555-1212"));
        assertEquals("4", true, MoreContactUtils.shouldCollapse(Phone.CONTENT_ITEM_TYPE,
                "14155551212", Phone.CONTENT_ITEM_TYPE, "1 (415) 555-1212"));
    }

    private void assertCollapses(String message, boolean expected, CharSequence mimetype1,
            CharSequence data1, CharSequence mimetype2, CharSequence data2) {
        assertEquals(message, expected, MoreContactUtils.shouldCollapse(mimetype1, data1, mimetype2,
                data2));
        assertEquals(message, expected, MoreContactUtils.shouldCollapse(mimetype2, data2, mimetype1,
                data1));

        // If data1 and data2 are the same instance, make sure the same test passes with different
        // instances.
        if (data1 == data2 && data1 != null) {
            // Create a different instance
            final CharSequence data2_newref = new StringBuilder(data2).append("").toString();

            if (data1 == data2_newref) {
                // In some cases no matter what we do the runtime reuses the same instance, so
                // we can't do the "different instance" test.
                return;
            }

            // we have two different instances, now make sure we get the same result as before
            assertEquals(message, expected, MoreContactUtils.shouldCollapse(mimetype1, data1,
                    mimetype2, data2_newref));
            assertEquals(message, expected, MoreContactUtils.shouldCollapse(mimetype2, data2_newref,
                    mimetype1, data1));
        }
    }
}
