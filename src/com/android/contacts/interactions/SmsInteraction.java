/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.contacts.interactions;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Telephony.Sms;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.TextDirectionHeuristics;

import com.android.contacts.R;
import com.android.contacts.util.ContactDisplayUtils;

/**
 * Represents an sms interaction, wrapping the columns in
 * {@link android.provider.Telephony.Sms}.
 */
public class SmsInteraction implements ContactInteraction {

    private static final String URI_TARGET_PREFIX = "smsto:";
    private static final int SMS_ICON_RES = R.drawable.quantum_ic_message_vd_theme_24;
    private static BidiFormatter sBidiFormatter = BidiFormatter.getInstance();

    private ContentValues mValues;

    public SmsInteraction(ContentValues values) {
        mValues = values;
    }

    @Override
    public Intent getIntent() {
        String address = getAddress();
        return address == null ? null : new Intent(Intent.ACTION_VIEW).setData(
                Uri.parse(URI_TARGET_PREFIX + address));
    }

    @Override
    public long getInteractionDate() {
        Long date = getDate();
        return date == null ? -1 : date;
    }

    @Override
    public String getViewHeader(Context context) {
        String body = getBody();
        if (getType() == Sms.MESSAGE_TYPE_SENT) {
            body = context.getResources().getString(R.string.message_from_you_prefix, body);
        }
        return body;
    }

    @Override
    public String getViewBody(Context context) {
        return getAddress();
    }

    @Override
    public String getViewFooter(Context context) {
        Long date = getDate();
        return date == null ? null : ContactInteractionUtil.formatDateStringFromTimestamp(
                date, context);
    }

    @Override
    public Drawable getIcon(Context context) {
        return context.getResources().getDrawable(SMS_ICON_RES);
    }

    @Override
    public Drawable getBodyIcon(Context context) {
        return null;
    }

    @Override
    public Drawable getFooterIcon(Context context) {
        return null;
    }

    public String getAddress() {
        final String address = mValues.getAsString(Sms.ADDRESS);
        return address == null ? null :
            sBidiFormatter.unicodeWrap(address, TextDirectionHeuristics.LTR);
    }

    public String getBody() {
        return mValues.getAsString(Sms.BODY);
    }

    public Long getDate() {
        return mValues.getAsLong(Sms.DATE);
    }


    public Long getDateSent() {
        return mValues.getAsLong(Sms.DATE_SENT);
    }

    public Integer getErrorCode() {
        return mValues.getAsInteger(Sms.ERROR_CODE);
    }

    public Boolean getLocked() {
        return mValues.getAsBoolean(Sms.LOCKED);
    }

    public Integer getPerson() {
        return mValues.getAsInteger(Sms.PERSON);
    }

    public Integer getProtocol() {
        return mValues.getAsInteger(Sms.PROTOCOL);
    }

    public Boolean getRead() {
        return mValues.getAsBoolean(Sms.READ);
    }

    public Boolean getReplyPathPresent() {
        return mValues.getAsBoolean(Sms.REPLY_PATH_PRESENT);
    }

    public Boolean getSeen() {
        return mValues.getAsBoolean(Sms.SEEN);
    }

    public String getServiceCenter() {
        return mValues.getAsString(Sms.SERVICE_CENTER);
    }

    public Integer getStatus() {
        return mValues.getAsInteger(Sms.STATUS);
    }

    public String getSubject() {
        return mValues.getAsString(Sms.SUBJECT);
    }

    public Integer getThreadId() {
        return mValues.getAsInteger(Sms.THREAD_ID);
    }

    public Integer getType() {
        return mValues.getAsInteger(Sms.TYPE);
    }

    @Override
    public Spannable getContentDescription(Context context) {
        final String phoneNumber = getViewBody(context);
        final String contentDescription = context.getResources().getString(
                R.string.content_description_recent_sms,
                getViewHeader(context), phoneNumber, getViewFooter(context));
        return ContactDisplayUtils.getTelephoneTtsSpannable(contentDescription, phoneNumber);
    }

    @Override
    public int getIconResourceId() {
        return SMS_ICON_RES;
    }
}
