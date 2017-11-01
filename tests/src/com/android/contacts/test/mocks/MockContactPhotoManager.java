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

package com.android.contacts.test.mocks;

import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import com.android.contacts.ContactPhotoManager;

/**
 * A photo preloader that always uses the "no contact" picture and never executes any real
 * db queries
 */
public class MockContactPhotoManager extends ContactPhotoManager {
    @Override
    public void loadThumbnail(ImageView view, long photoId, boolean darkTheme, boolean isCircular,
            DefaultImageRequest defaultImageRequest, DefaultImageProvider defaultProvider) {
        defaultProvider.applyDefaultImage(view, -1, darkTheme, null);
    }

    @Override
    public void loadPhoto(ImageView view, Uri photoUri, int requestedExtent, boolean darkTheme,
            boolean isCircular, DefaultImageRequest defaultImageRequest,
            DefaultImageProvider defaultProvider) {
        defaultProvider.applyDefaultImage(view, requestedExtent, darkTheme, null);
    }

    @Override
    public void removePhoto(ImageView view) {
        view.setImageDrawable(null);
    }

    @Override
    public void cancelPendingRequests(View fragmentRootView) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void refreshCache() {
    }

    @Override
    public void cacheBitmap(Uri photoUri, Bitmap bitmap, byte[] photoBytes) {
    }

    @Override
    public void preloadPhotosInBackground() {
    }
}
