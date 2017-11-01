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

import android.graphics.Bitmap;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Tests for {@link com.android.contacts.util.BitmapUtil}.
 */
@SmallTest
public class BitmapUtilTests extends AndroidTestCase {
    public void testGetSmallerExtentFromBytes1() throws Exception {
        assertEquals(100, BitmapUtil.getSmallerExtentFromBytes(createJpegRawData(100, 100)));
        assertEquals(100, BitmapUtil.getSmallerExtentFromBytes(createPngRawData(100, 100)));
    }

    public void testGetSmallerExtentFromBytes2() throws Exception {
        assertEquals(50, BitmapUtil.getSmallerExtentFromBytes(createJpegRawData(200, 50)));
        assertEquals(50, BitmapUtil.getSmallerExtentFromBytes(createPngRawData(200, 50)));
    }

    public void testGetSmallerExtentFromBytes3() throws Exception {
        assertEquals(40, BitmapUtil.getSmallerExtentFromBytes(createJpegRawData(40, 150)));
        assertEquals(40, BitmapUtil.getSmallerExtentFromBytes(createPngRawData(40, 150)));
    }

    public void testFindOptimalSampleSizeExact() throws Exception {
        assertEquals(1, BitmapUtil.findOptimalSampleSize(512, 512));
    }

    public void testFindOptimalSampleSizeBigger() throws Exception {
        assertEquals(1, BitmapUtil.findOptimalSampleSize(512, 1024));
    }

    public void testFindOptimalSampleSizeSmaller1() throws Exception {
        assertEquals(2, BitmapUtil.findOptimalSampleSize(512, 256));
    }

    public void testFindOptimalSampleSizeSmaller2() throws Exception {
        assertEquals(2, BitmapUtil.findOptimalSampleSize(512, 230));
    }

    public void testFindOptimalSampleSizeSmaller3() throws Exception {
        assertEquals(4, BitmapUtil.findOptimalSampleSize(512, 129));
    }

    public void testFindOptimalSampleSizeSmaller4() throws Exception {
        assertEquals(4, BitmapUtil.findOptimalSampleSize(512, 128));
    }

    public void testFindOptimalSampleSizeUnknownOriginal() throws Exception {
        assertEquals(1, BitmapUtil.findOptimalSampleSize(-1, 128));
    }

    public void testFindOptimalSampleSizeUnknownTarget() throws Exception {
        assertEquals(1, BitmapUtil.findOptimalSampleSize(128, -1));
    }

    public void testDecodeWithSampleSize1() throws IOException {
        assertBitmapSize(128, 64, BitmapUtil.decodeBitmapFromBytes(createJpegRawData(128, 64), 1));
        assertBitmapSize(128, 64, BitmapUtil.decodeBitmapFromBytes(createPngRawData(128, 64), 1));
    }

    public void testDecodeWithSampleSize2() throws IOException {
        assertBitmapSize(64, 32, BitmapUtil.decodeBitmapFromBytes(createJpegRawData(128, 64), 2));
        assertBitmapSize(64, 32, BitmapUtil.decodeBitmapFromBytes(createPngRawData(128, 64), 2));
    }

    public void testDecodeWithSampleSize2a() throws IOException {
        assertBitmapSize(25, 20, BitmapUtil.decodeBitmapFromBytes(createJpegRawData(50, 40), 2));
        assertBitmapSize(25, 20, BitmapUtil.decodeBitmapFromBytes(createPngRawData(50, 40), 2));
    }

    public void testDecodeWithSampleSize4() throws IOException {
        assertBitmapSize(32, 16, BitmapUtil.decodeBitmapFromBytes(createJpegRawData(128, 64), 4));
        assertBitmapSize(32, 16, BitmapUtil.decodeBitmapFromBytes(createPngRawData(128, 64), 4));
    }

    private void assertBitmapSize(int expectedWidth, int expectedHeight, Bitmap bitmap) {
        assertEquals(expectedWidth, bitmap.getWidth());
        assertEquals(expectedHeight, bitmap.getHeight());
    }

    private byte[] createJpegRawData(int sourceWidth, int sourceHeight) throws IOException {
        return createRawData(Bitmap.CompressFormat.JPEG, sourceWidth, sourceHeight);
    }

    private byte[] createPngRawData(int sourceWidth, int sourceHeight) throws IOException {
        return createRawData(Bitmap.CompressFormat.PNG, sourceWidth, sourceHeight);
    }

    private byte[] createRawData(Bitmap.CompressFormat format, int sourceWidth,
            int sourceHeight) throws IOException {
        // Create a temp bitmap as our source
        Bitmap b = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        b.compress(format, 50, outputStream);
        final byte[] data = outputStream.toByteArray();
        outputStream.close();
        return data;
    }
}
