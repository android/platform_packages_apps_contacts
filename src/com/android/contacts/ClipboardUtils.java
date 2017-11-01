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

package com.android.contacts;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

public class ClipboardUtils {
    private static final String TAG = "ClipboardUtils";

    private ClipboardUtils() { }

    /**
     * Copy a text to clipboard.
     *
     * @param context Context
     * @param label Label to show to the user describing this clip.
     * @param text Text to copy.
     * @param showToast If {@code true}, a toast is shown to the user.
     */
    public static void copyText(Context context, CharSequence label, CharSequence text,
            boolean showToast) {
        if (TextUtils.isEmpty(text)) return;

        ClipboardManager clipboardManager = (ClipboardManager) context.getSystemService(
                Context.CLIPBOARD_SERVICE);
        ClipData clipData = ClipData.newPlainText(label == null ? "" : label, text);
        clipboardManager.setPrimaryClip(clipData);

        if (showToast) {
            String toastText = context.getString(R.string.toast_text_copied);
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
        }
    }
}
