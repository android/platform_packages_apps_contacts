package com.android.contacts.util;

import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Test cases for {@link ContactPhotoUtils}.
 *
 * adb shell am instrument -w -e class com.android.contacts.util.ContactPhotoUtilsTest \
 *   com.android.contacts.tests/android.test.InstrumentationTestRunner
 */
@SmallTest
public class ContactPhotoUtilsTest extends AndroidTestCase {

  private Uri tempUri;

  @Override
  protected void setUp() throws Exception {
    tempUri = ContactPhotoUtils.generateTempImageUri(getContext());
  }

  protected void tearDown() throws Exception {
    getContext().getContentResolver().delete(tempUri, null, null);
  }

  public void testFileUriDataPathFails() {
    String filePath =
        "file:///data/data/com.android.contacts/shared_prefs/com.android.contacts.xml";

    assertFalse(
        ContactPhotoUtils.savePhotoFromUriToUri(getContext(), Uri.parse(filePath), tempUri, false));
  }

  public void testFileUriCanonicalDataPathFails() {
    String filePath =
        "file:///storage/../data/data/com.android.contacts/shared_prefs/com.android.contacts.xml";

    assertFalse(
        ContactPhotoUtils.savePhotoFromUriToUri(getContext(), Uri.parse(filePath), tempUri, false));
  }

  public void testContentUriInternalPasses() {
    Uri internal = ContactPhotoUtils.generateTempImageUri(getContext());

    assertTrue(
        ContactPhotoUtils.savePhotoFromUriToUri(getContext(), internal, tempUri, true));
  }
}
