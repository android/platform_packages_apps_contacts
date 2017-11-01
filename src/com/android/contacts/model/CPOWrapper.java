/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.ContentProviderOperation;

/**
 * This class is created for the purpose of compatibility and make the type of
 * ContentProviderOperation available on pre-M SDKs.
 */
public class CPOWrapper {
    private ContentProviderOperation mOperation;
    private int mType;

    public CPOWrapper(ContentProviderOperation builder, int type) {
        mOperation = builder;
        mType = type;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        this.mType = type;
    }

    public ContentProviderOperation getOperation() {
        return mOperation;
    }

    public void setOperation(ContentProviderOperation operation) {
        this.mOperation = operation;
    }
}
