/*
 * Copyright (C) 2010 The Android Open Source Project
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
package com.android.contacts.vcard;

import android.accounts.Account;
import android.net.Uri;

import com.android.contacts.model.account.AccountWithDataSet;
import com.android.vcard.VCardSourceDetector;

/**
 * Class representing one request for importing vCard (given as a Uri).
 *
 * Mainly used when {@link ImportVCardActivity} requests {@link VCardService}
 * to import some specific Uri.
 *
 * Note: This object's accepting only One Uri does NOT mean that
 * there's only one vCard entry inside the instance, as one Uri often has multiple
 * vCard entries inside it.
 */
public class ImportRequest {
    /**
     * Can be null (typically when there's no Account available in the system).
     */
    public final Account account;

    /**
     * Uri to be imported. May have different content than originally given from users, so
     * when displaying user-friendly information (e.g. "importing xxx.vcf"), use
     * {@link #displayName} instead.
     *
     * If this is null {@link #data} contains the byte stream of the vcard.
     */
    public final Uri uri;

    /**
     * Holds the byte stream of the vcard, if {@link #uri} is null.
     */
    public final byte[] data;

    /**
     * String to be displayed to the user to indicate the source of the VCARD.
     */
    public final String displayName;

    /**
     * Can be {@link VCardSourceDetector#PARSE_TYPE_UNKNOWN}.
     */
    public final int estimatedVCardType;

    /**
     * Can be null, meaning no preferable charset is available.
     */
    public final String estimatedCharset;

    /**
     * Assumes that one Uri contains only one version, while there's a (tiny) possibility
     * we may have two types in one vCard.
     *
     * e.g.
     * BEGIN:VCARD
     * VERSION:2.1
     * ...
     * END:VCARD
     * BEGIN:VCARD
     * VERSION:3.0
     * ...
     * END:VCARD
     *
     * We've never seen this kind of a file, but we may have to cope with it in the future.
     */
    public final int vcardVersion;

    /**
     * The count of vCard entries in {@link #uri}. A receiver of this object can use it
     * when showing the progress of import. Thus a receiver must be able to torelate this
     * variable being invalid because of vCard's limitation.
     *
     * vCard does not let us know this count without looking over a whole file content,
     * which means we have to open and scan over {@link #uri} to know this value, while
     * it may not be opened more than once (Uri does not require it to be opened multiple times
     * and may become invalid after its close() request).
     */
    public final int entryCount;

    public ImportRequest(AccountWithDataSet account,
            byte[] data, Uri uri, String displayName, int estimatedType, String estimatedCharset,
            int vcardVersion, int entryCount) {
        this.account = account != null ? account.getAccountOrNull() : null;
        this.data = data;
        this.uri = uri;
        this.displayName = displayName;
        this.estimatedVCardType = estimatedType;
        this.estimatedCharset = estimatedCharset;
        this.vcardVersion = vcardVersion;
        this.entryCount = entryCount;
    }
}
