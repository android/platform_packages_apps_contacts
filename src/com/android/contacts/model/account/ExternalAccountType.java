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

package com.android.contacts.model.account;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import com.android.contacts.R;
import com.android.contacts.model.dataitem.DataKind;
import com.android.contactsbind.FeedbackHelper;

import com.google.common.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A general contacts account type descriptor.
 */
public class ExternalAccountType extends BaseAccountType {
    private static final String TAG = "ExternalAccountType";

    private static final String SYNC_META_DATA = "android.content.SyncAdapter";

    /**
     * The metadata name for so-called "contacts.xml".
     *
     * On LMP and later, we also accept the "alternate" name.
     * This is to allow sync adapters to have a contacts.xml without making it visible on older
     * platforms. If you modify this also update the corresponding list in
     * ContactsProvider/PhotoPriorityResolver
     */
    private static final String[] METADATA_CONTACTS_NAMES = new String[] {
            "android.provider.ALTERNATE_CONTACTS_STRUCTURE",
            "android.provider.CONTACTS_STRUCTURE"
    };

    private static final String TAG_CONTACTS_SOURCE_LEGACY = "ContactsSource";
    private static final String TAG_CONTACTS_ACCOUNT_TYPE = "ContactsAccountType";
    private static final String TAG_CONTACTS_DATA_KIND = "ContactsDataKind";
    private static final String TAG_EDIT_SCHEMA = "EditSchema";

    private static final String ATTR_INVITE_CONTACT_ACTIVITY = "inviteContactActivity";
    private static final String ATTR_INVITE_CONTACT_ACTION_LABEL = "inviteContactActionLabel";
    private static final String ATTR_VIEW_CONTACT_NOTIFY_SERVICE = "viewContactNotifyService";
    private static final String ATTR_VIEW_GROUP_ACTIVITY = "viewGroupActivity";
    private static final String ATTR_VIEW_GROUP_ACTION_LABEL = "viewGroupActionLabel";
    private static final String ATTR_DATA_SET = "dataSet";
    private static final String ATTR_EXTENSION_PACKAGE_NAMES = "extensionPackageNames";

    // The following attributes should only be set in non-sync-adapter account types.  They allow
    // for the account type and resource IDs to be specified without an associated authenticator.
    private static final String ATTR_ACCOUNT_TYPE = "accountType";
    private static final String ATTR_ACCOUNT_LABEL = "accountTypeLabel";
    private static final String ATTR_ACCOUNT_ICON = "accountTypeIcon";

    private final boolean mIsExtension;

    private String mInviteContactActivity;
    private String mInviteActionLabelAttribute;
    private int mInviteActionLabelResId;
    private String mViewContactNotifyService;
    private String mViewGroupActivity;
    private String mViewGroupLabelAttribute;
    private int mViewGroupLabelResId;
    private List<String> mExtensionPackageNames;
    private String mAccountTypeLabelAttribute;
    private String mAccountTypeIconAttribute;
    private boolean mHasContactsMetadata;
    private boolean mHasEditSchema;
    private boolean mGroupMembershipEditable;

    public ExternalAccountType(Context context, String resPackageName, boolean isExtension) {
        this(context, resPackageName, isExtension, null);
    }

    /**
     * Constructor used for testing to initialize with any arbitrary XML.
     *
     * @param injectedMetadata If non-null, it'll be used to initialize the type.  Only set by
     *     tests.  If null, the metadata is loaded from the specified package.
     */
    ExternalAccountType(Context context, String packageName, boolean isExtension,
            XmlResourceParser injectedMetadata) {
        this.mIsExtension = isExtension;
        this.resourcePackageName = packageName;
        this.syncAdapterPackageName = packageName;

        final XmlResourceParser parser;
        if (injectedMetadata == null) {
            parser = loadContactsXml(context, packageName);
        } else {
            parser = injectedMetadata;
        }
        boolean needLineNumberInErrorLog = true;
        try {
            if (parser != null) {
                inflate(context, parser);
            }

            // Done parsing; line number no longer needed in error log.
            needLineNumberInErrorLog = false;
            if (mHasEditSchema) {
                checkKindExists(StructuredName.CONTENT_ITEM_TYPE);
                checkKindExists(DataKind.PSEUDO_MIME_TYPE_NAME);
                checkKindExists(DataKind.PSEUDO_MIME_TYPE_PHONETIC_NAME);
                checkKindExists(Photo.CONTENT_ITEM_TYPE);
            } else {
                // Bring in name and photo from fallback source, which are non-optional
                addDataKindStructuredName(context);
                addDataKindName(context);
                addDataKindPhoneticName(context);
                addDataKindPhoto(context);
            }
        } catch (DefinitionException e) {
            final StringBuilder error = new StringBuilder();
            error.append("Problem reading XML");
            if (needLineNumberInErrorLog && (parser != null)) {
                error.append(" in line ");
                error.append(parser.getLineNumber());
            }
            error.append(" for external package ");
            error.append(packageName);
            // Only send feedback if not from tests. There are tests that expect failures so no need
            // to report those.
            if (injectedMetadata == null) {
                FeedbackHelper.sendFeedback(context, TAG, "Failed to build external account type",
                        e);
            }
            return;
        } finally {
            if (parser != null) {
                parser.close();
            }
        }

        mExtensionPackageNames = new ArrayList<String>();
        mInviteActionLabelResId = resolveExternalResId(context, mInviteActionLabelAttribute,
                syncAdapterPackageName, ATTR_INVITE_CONTACT_ACTION_LABEL);
        mViewGroupLabelResId = resolveExternalResId(context, mViewGroupLabelAttribute,
                syncAdapterPackageName, ATTR_VIEW_GROUP_ACTION_LABEL);
        titleRes = resolveExternalResId(context, mAccountTypeLabelAttribute,
                syncAdapterPackageName, ATTR_ACCOUNT_LABEL);
        iconRes = resolveExternalResId(context, mAccountTypeIconAttribute,
                syncAdapterPackageName, ATTR_ACCOUNT_ICON);

        final DataKind dataKind = getKindForMimetype(GroupMembership.CONTENT_ITEM_TYPE);
        mGroupMembershipEditable = dataKind != null && dataKind.editable;

        // If we reach this point, the account type has been successfully initialized.
        mIsInitialized = true;
    }

    /**
     * Returns the CONTACTS_STRUCTURE metadata (aka "contacts.xml") in the given apk package.
     *
     * This method looks through all services in the package that handle sync adapter
     * intents for the first one that contains CONTACTS_STRUCTURE metadata. We have to look
     * through all sync adapters in the package in case there are contacts and other sync
     * adapters (eg, calendar) in the same package.
     *
     * Returns {@code null} if the package has no CONTACTS_STRUCTURE metadata.  In this case
     * the account type *will* be initialized with minimal configuration.
     */
    public static XmlResourceParser loadContactsXml(Context context, String resPackageName) {
        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(SYNC_META_DATA).setPackage(resPackageName);
        final List<ResolveInfo> intentServices = pm.queryIntentServices(intent,
                PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);

        if (intentServices != null) {
            for (final ResolveInfo resolveInfo : intentServices) {
                final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
                if (serviceInfo == null) {
                    continue;
                }
                for (String metadataName : METADATA_CONTACTS_NAMES) {
                    final XmlResourceParser parser = serviceInfo.loadXmlMetaData(
                            pm, metadataName);
                    if (parser != null) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, String.format("Metadata loaded from: %s, %s, %s",
                                    serviceInfo.packageName, serviceInfo.name,
                                    metadataName));
                        }
                        return parser;
                    }
                }
            }
        }

        // Package was found, but that doesn't contain the CONTACTS_STRUCTURE metadata.
        return null;
    }

    /**
     * Returns {@code TRUE} if the package contains CONTACTS_STRUCTURE metadata.
     */
    public static boolean hasContactsXml(Context context, String resPackageName) {
        return loadContactsXml(context, resPackageName) != null;
    }

    private void checkKindExists(String mimeType) throws DefinitionException {
        if (getKindForMimetype(mimeType) == null) {
            throw new DefinitionException(mimeType + " must be supported");
        }
    }

    @Override
    public boolean isEmbedded() {
        return false;
    }

    @Override
    public boolean isExtension() {
        return mIsExtension;
    }

    @Override
    public boolean areContactsWritable() {
        return mHasEditSchema;
    }

    /**
     * Whether this account type has the android.provider.CONTACTS_STRUCTURE metadata xml.
     */
    public boolean hasContactsMetadata() {
        return mHasContactsMetadata;
    }

    @Override
    public String getInviteContactActivityClassName() {
        return mInviteContactActivity;
    }

    @Override
    protected int getInviteContactActionResId() {
        return mInviteActionLabelResId;
    }

    @Override
    public String getViewContactNotifyServiceClassName() {
        return mViewContactNotifyService;
    }

    @Override
    public String getViewGroupActivity() {
        return mViewGroupActivity;
    }

    @Override
    protected int getViewGroupLabelResId() {
        return mViewGroupLabelResId;
    }

    @Override
    public List<String> getExtensionPackageNames() {
        return mExtensionPackageNames;
    }

    @Override
    public boolean isGroupMembershipEditable() {
        return mGroupMembershipEditable;
    }

    /**
     * Inflate this {@link AccountType} from the given parser. This may only
     * load details matching the publicly-defined schema.
     */
    protected void inflate(Context context, XmlPullParser parser) throws DefinitionException {
        final AttributeSet attrs = Xml.asAttributeSet(parser);

        try {
            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // Drain comments and whitespace
            }

            if (type != XmlPullParser.START_TAG) {
                throw new IllegalStateException("No start tag found");
            }

            String rootTag = parser.getName();
            if (!TAG_CONTACTS_ACCOUNT_TYPE.equals(rootTag) &&
                    !TAG_CONTACTS_SOURCE_LEGACY.equals(rootTag)) {
                throw new IllegalStateException("Top level element must be "
                        + TAG_CONTACTS_ACCOUNT_TYPE + ", not " + rootTag);
            }

            mHasContactsMetadata = true;

            int attributeCount = parser.getAttributeCount();
            for (int i = 0; i < attributeCount; i++) {
                String attr = parser.getAttributeName(i);
                String value = parser.getAttributeValue(i);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, attr + "=" + value);
                }
                if (ATTR_INVITE_CONTACT_ACTIVITY.equals(attr)) {
                    mInviteContactActivity = value;
                } else if (ATTR_INVITE_CONTACT_ACTION_LABEL.equals(attr)) {
                    mInviteActionLabelAttribute = value;
                } else if (ATTR_VIEW_CONTACT_NOTIFY_SERVICE.equals(attr)) {
                    mViewContactNotifyService = value;
                } else if (ATTR_VIEW_GROUP_ACTIVITY.equals(attr)) {
                    mViewGroupActivity = value;
                } else if (ATTR_VIEW_GROUP_ACTION_LABEL.equals(attr)) {
                    mViewGroupLabelAttribute = value;
                } else if (ATTR_DATA_SET.equals(attr)) {
                    dataSet = value;
                } else if (ATTR_EXTENSION_PACKAGE_NAMES.equals(attr)) {
                    mExtensionPackageNames.add(value);
                } else if (ATTR_ACCOUNT_TYPE.equals(attr)) {
                    accountType = value;
                } else if (ATTR_ACCOUNT_LABEL.equals(attr)) {
                    mAccountTypeLabelAttribute = value;
                } else if (ATTR_ACCOUNT_ICON.equals(attr)) {
                    mAccountTypeIconAttribute = value;
                } else if (Log.isLoggable(TAG, Log.WARN)) {
                    Log.w(TAG, "Unsupported attribute " + attr);
                }
            }

            // Parse all children kinds
            final int startDepth = parser.getDepth();
            while (((type = parser.next()) != XmlPullParser.END_TAG
                        || parser.getDepth() > startDepth)
                    && type != XmlPullParser.END_DOCUMENT) {

                if (type != XmlPullParser.START_TAG || parser.getDepth() != startDepth + 1) {
                    continue; // Not a direct child tag
                }

                String tag = parser.getName();
                if (TAG_EDIT_SCHEMA.equals(tag)) {
                    mHasEditSchema = true;
                    parseEditSchema(context, parser, attrs);
                } else if (TAG_CONTACTS_DATA_KIND.equals(tag)) {
                    final TypedArray a = context.obtainStyledAttributes(attrs,
                            R.styleable.ContactsDataKind);
                    final DataKind kind = new DataKind();

                    kind.mimeType = a
                            .getString(R.styleable.ContactsDataKind_android_mimeType);
                    final String summaryColumn = a.getString(
                            R.styleable.ContactsDataKind_android_summaryColumn);
                    if (summaryColumn != null) {
                        // Inflate a specific column as summary when requested
                        kind.actionHeader = new SimpleInflater(summaryColumn);
                    }
                    final String detailColumn = a.getString(
                            R.styleable.ContactsDataKind_android_detailColumn);
                    if (detailColumn != null) {
                        // Inflate specific column as summary
                        kind.actionBody = new SimpleInflater(detailColumn);
                    }

                    a.recycle();

                    addKind(kind);
                }
            }
        } catch (XmlPullParserException e) {
            throw new DefinitionException("Problem reading XML", e);
        } catch (IOException e) {
            throw new DefinitionException("Problem reading XML", e);
        }
    }

    /**
     * Takes a string in the "@xxx/yyy" format and return the resource ID for the resource in
     * the resource package.
     *
     * If the argument is in the invalid format or isn't a resource name, it returns -1.
     *
     * @param context context
     * @param resourceName Resource name in the "@xxx/yyy" format, e.g. "@string/invite_lavbel"
     * @param packageName name of the package containing the resource.
     * @param xmlAttributeName attribute name which the resource came from.  Used for logging.
     */
    @VisibleForTesting
    static int resolveExternalResId(Context context, String resourceName,
            String packageName, String xmlAttributeName) {
        if (TextUtils.isEmpty(resourceName)) {
            return -1; // Empty text is okay.
        }
        if (resourceName.charAt(0) != '@') {
            if (Log.isLoggable(TAG, Log.WARN) && !isFromTestApp(packageName)) {
                Log.w(TAG, xmlAttributeName + " must be a resource name beginnig with '@'");
            }
            return -1;
        }
        final String name = resourceName.substring(1);
        final Resources res;
        try {
             res = context.getPackageManager().getResourcesForApplication(packageName);
        } catch (NameNotFoundException e) {
            if (Log.isLoggable(TAG, Log.WARN) && !isFromTestApp(packageName)) {
                Log.w(TAG, "Unable to load package " + packageName);
            }
            return -1;
        }
        final int resId = res.getIdentifier(name, null, packageName);
        if (resId == 0) {
            if (Log.isLoggable(TAG, Log.WARN) && !isFromTestApp(packageName)) {
                Log.w(TAG, "Unable to load " + resourceName + " from package " + packageName);
            }
            return -1;
        }
        return resId;
    }

    @VisibleForTesting
    static boolean isFromTestApp(String packageName) {
        return TextUtils.equals(packageName, "com.google.android.contacts.tests");
    }
}
