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

import android.accounts.AuthenticatorDescription;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import com.android.contacts.R;
import com.android.contacts.model.dataitem.DataKind;

import com.google.common.base.Preconditions;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Internal structure that represents constraints and styles for a specific data
 * source, such as the various data types they support, including details on how
 * those types should be rendered and edited.
 * <p>
 * In the future this may be inflated from XML defined by a data source.
 */
public abstract class AccountType {
    private static final String TAG = "AccountType";

    /**
     * The {@link RawContacts#ACCOUNT_TYPE} these constraints apply to.
     */
    public String accountType = null;

    /**
     * The {@link RawContacts#DATA_SET} these constraints apply to.
     */
    public String dataSet = null;

    /**
     * Package that resources should be loaded from.  Will be null for embedded types, in which
     * case resources are stored in this package itself.
     *
     * TODO Clean up {@link #resourcePackageName}, {@link #syncAdapterPackageName} and
     * {@link #getViewContactNotifyServicePackageName()}.
     *
     * There's the following invariants:
     * - {@link #syncAdapterPackageName} is always set to the actual sync adapter package name.
     * - {@link #resourcePackageName} too is set to the same value, unless {@link #isEmbedded()},
     *   in which case it'll be null.
     * There's an unfortunate exception of {@link FallbackAccountType}.  Even though it
     * {@link #isEmbedded()}, but we set non-null to {@link #resourcePackageName} for unit tests.
     */
    public String resourcePackageName;
    /**
     * The package name for the authenticator (for the embedded types, i.e. Google and Exchange)
     * or the sync adapter (for external type, including extensions).
     */
    public String syncAdapterPackageName;

    public int titleRes;
    public int iconRes;

    /**
     * Set of {@link DataKind} supported by this source.
     */
    private ArrayList<DataKind> mKinds = Lists.newArrayList();

    /**
     * Lookup map of {@link #mKinds} on {@link DataKind#mimeType}.
     */
    private HashMap<String, DataKind> mMimeKinds = Maps.newHashMap();

    protected boolean mIsInitialized;

    protected static class DefinitionException extends Exception {
        public DefinitionException(String message) {
            super(message);
        }

        public DefinitionException(String message, Exception inner) {
            super(message, inner);
        }
    }

    /**
     * Whether this account type was able to be fully initialized.  This may be false if
     * (for example) the package name associated with the account type could not be found.
     */
    public final boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * @return Whether this type is an "embedded" type.  i.e. any of {@link FallbackAccountType},
     * {@link GoogleAccountType} or {@link ExternalAccountType}.
     *
     * If an embedded type cannot be initialized (i.e. if {@link #isInitialized()} returns
     * {@code false}) it's considered critical, and the application will crash.  On the other
     * hand if it's not an embedded type, we just skip loading the type.
     */
    public boolean isEmbedded() {
        return true;
    }

    public boolean isExtension() {
        return false;
    }

    /**
     * @return True if contacts can be created and edited using this app. If false,
     * there could still be an external editor as provided by
     * {@link #getEditContactActivityClassName()} or {@link #getCreateContactActivityClassName()}
     */
    public abstract boolean areContactsWritable();

    /**
     * Returns an optional custom invite contact activity.
     *
     * Only makes sense for non-embedded account types.
     * The activity class should reside in the sync adapter package as determined by
     * {@link #syncAdapterPackageName}.
     */
    public String getInviteContactActivityClassName() {
        return null;
    }

    /**
     * Returns an optional service that can be launched whenever a contact is being looked at.
     * This allows the sync adapter to provide more up-to-date information.
     *
     * The service class should reside in the sync adapter package as determined by
     * {@link #getViewContactNotifyServicePackageName()}.
     */
    public String getViewContactNotifyServiceClassName() {
        return null;
    }

    /**
     * TODO This is way too hacky should be removed.
     *
     * This is introduced for {@link GoogleAccountType} where {@link #syncAdapterPackageName}
     * is the authenticator package name but the notification service is in the sync adapter
     * package.  See {@link #resourcePackageName} -- we should clean up those.
     */
    public String getViewContactNotifyServicePackageName() {
        return syncAdapterPackageName;
    }

    /** Returns an optional Activity string that can be used to view the group. */
    public String getViewGroupActivity() {
        return null;
    }

    public CharSequence getDisplayLabel(Context context) {
        // Note this resource is defined in the sync adapter package, not resourcePackageName.
        return getResourceText(context, syncAdapterPackageName, titleRes, accountType);
    }

    /**
     * Creates an {@link AccountInfo} for the specified account with the same type
     *
     * <p>The {@link AccountWithDataSet#type} must match {@link #accountType} of this instance</p>
     */
    public AccountInfo wrapAccount(Context context, AccountWithDataSet account) {
        Preconditions.checkArgument(Objects.equal(account.type, accountType),
                "Account types must match: account.type=%s but accountType=%s",
                account.type, accountType);

        return new AccountInfo(
                new AccountDisplayInfo(account, account.name,
                        getDisplayLabel(context), getDisplayIcon(context), false), this);
    }

    /**
     * @return resource ID for the "invite contact" action label, or -1 if not defined.
     */
    protected int getInviteContactActionResId() {
        return -1;
    }

    /**
     * @return resource ID for the "view group" label, or -1 if not defined.
     */
    protected int getViewGroupLabelResId() {
        return -1;
    }

    /**
     * Returns {@link AccountTypeWithDataSet} for this type.
     */
    public AccountTypeWithDataSet getAccountTypeAndDataSet() {
        return AccountTypeWithDataSet.get(accountType, dataSet);
    }

    /**
     * Returns a list of additional package names that should be inspected as additional
     * external account types.  This allows for a primary account type to indicate other packages
     * that may not be sync adapters but which still provide contact data, perhaps under a
     * separate data set within the account.
     */
    public List<String> getExtensionPackageNames() {
        return new ArrayList<String>();
    }

    /**
     * Returns an optional custom label for the "invite contact" action, which will be shown on
     * the contact card.  (If not defined, returns null.)
     */
    public CharSequence getInviteContactActionLabel(Context context) {
        // Note this resource is defined in the sync adapter package, not resourcePackageName.
        return getResourceText(context, syncAdapterPackageName, getInviteContactActionResId(), "");
    }

    /**
     * Returns a label for the "view group" action. If not defined, this falls back to our
     * own "View Updates" string
     */
    public CharSequence getViewGroupLabel(Context context) {
        // Note this resource is defined in the sync adapter package, not resourcePackageName.
        final CharSequence customTitle =
                getResourceText(context, syncAdapterPackageName, getViewGroupLabelResId(), null);

        return customTitle == null
                ? context.getText(R.string.view_updates_from_group)
                : customTitle;
    }

    /**
     * Return a string resource loaded from the given package (or the current package
     * if {@code packageName} is null), unless {@code resId} is -1, in which case it returns
     * {@code defaultValue}.
     *
     * (The behavior is undefined if the resource or package doesn't exist.)
     */
    @VisibleForTesting
    static CharSequence getResourceText(Context context, String packageName, int resId,
            String defaultValue) {
        if (resId != -1 && packageName != null) {
            final PackageManager pm = context.getPackageManager();
            return pm.getText(packageName, resId, null);
        } else if (resId != -1) {
            return context.getText(resId);
        } else {
            return defaultValue;
        }
    }

    public Drawable getDisplayIcon(Context context) {
        return getDisplayIcon(context, titleRes, iconRes, syncAdapterPackageName);
    }

    public static Drawable getDisplayIcon(Context context, int titleRes, int iconRes,
            String syncAdapterPackageName) {
        if (titleRes != -1 && syncAdapterPackageName != null) {
            final PackageManager pm = context.getPackageManager();
            return pm.getDrawable(syncAdapterPackageName, iconRes, null);
        } else if (titleRes != -1) {
            return context.getResources().getDrawable(iconRes);
        } else {
            return null;
        }
    }

    /**
     * Whether or not groups created under this account type have editable membership lists.
     */
    abstract public boolean isGroupMembershipEditable();

    /**
     * {@link Comparator} to sort by {@link DataKind#weight}.
     */
    private static Comparator<DataKind> sWeightComparator = new Comparator<DataKind>() {
        @Override
        public int compare(DataKind object1, DataKind object2) {
            return object1.weight - object2.weight;
        }
    };

    /**
     * Return list of {@link DataKind} supported, sorted by
     * {@link DataKind#weight}.
     */
    public ArrayList<DataKind> getSortedDataKinds() {
        // TODO: optimize by marking if already sorted
        Collections.sort(mKinds, sWeightComparator);
        return mKinds;
    }

    /**
     * Find the {@link DataKind} for a specific MIME-type, if it's handled by
     * this data source.
     */
    public DataKind getKindForMimetype(String mimeType) {
        return this.mMimeKinds.get(mimeType);
    }

    public void initializeFieldsFromAuthenticator(AuthenticatorDescription authenticator) {
        accountType = authenticator.type;
        titleRes = authenticator.labelId;
        iconRes = authenticator.iconId;
    }

    /**
     * Add given {@link DataKind} to list of those provided by this source.
     */
    public DataKind addKind(DataKind kind) throws DefinitionException {
        if (kind.mimeType == null) {
            throw new DefinitionException("null is not a valid mime type");
        }
        if (mMimeKinds.get(kind.mimeType) != null) {
            throw new DefinitionException(
                    "mime type '" + kind.mimeType + "' is already registered");
        }

        kind.resourcePackageName = this.resourcePackageName;
        this.mKinds.add(kind);
        this.mMimeKinds.put(kind.mimeType, kind);
        return kind;
    }

    /**
     * Description of a specific "type" or "label" of a {@link DataKind} row,
     * such as {@link Phone#TYPE_WORK}. Includes constraints on total number of
     * rows a {@link Contacts} may have of this type, and details on how
     * user-defined labels are stored.
     */
    public static class EditType {
        public int rawValue;
        public int labelRes;
        public boolean secondary;
        /**
         * The number of entries allowed for the type. -1 if not specified.
         * @see DataKind#typeOverallMax
         */
        public int specificMax;
        public String customColumn;

        public EditType(int rawValue, int labelRes) {
            this.rawValue = rawValue;
            this.labelRes = labelRes;
            this.specificMax = -1;
        }

        public EditType setSecondary(boolean secondary) {
            this.secondary = secondary;
            return this;
        }

        public EditType setSpecificMax(int specificMax) {
            this.specificMax = specificMax;
            return this;
        }

        public EditType setCustomColumn(String customColumn) {
            this.customColumn = customColumn;
            return this;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof EditType) {
                final EditType other = (EditType)object;
                return other.rawValue == rawValue;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return rawValue;
        }

        @Override
        public String toString() {
            return this.getClass().getSimpleName()
                    + " rawValue=" + rawValue
                    + " labelRes=" + labelRes
                    + " secondary=" + secondary
                    + " specificMax=" + specificMax
                    + " customColumn=" + customColumn;
        }
    }

    public static class EventEditType extends EditType {
        private boolean mYearOptional;

        public EventEditType(int rawValue, int labelRes) {
            super(rawValue, labelRes);
        }

        public boolean isYearOptional() {
            return mYearOptional;
        }

        public EventEditType setYearOptional(boolean yearOptional) {
            mYearOptional = yearOptional;
            return this;
        }

        @Override
        public String toString() {
            return super.toString() + " mYearOptional=" + mYearOptional;
        }
    }

    /**
     * Description of a user-editable field on a {@link DataKind} row, such as
     * {@link Phone#NUMBER}. Includes flags to apply to an {@link EditText}, and
     * the column where this field is stored.
     */
    public static final class EditField {
        public String column;
        public int titleRes;
        public int inputType;
        public int minLines;
        public boolean optional;
        public boolean shortForm;
        public boolean longForm;
        public String phoneticsColumn;

        public EditField(String column, int titleRes) {
            this.column = column;
            this.titleRes = titleRes;
        }

        public EditField(String column, int titleRes, int inputType) {
            this(column, titleRes);
            this.inputType = inputType;
        }

        public EditField setOptional(boolean optional) {
            this.optional = optional;
            return this;
        }

        public EditField setShortForm(boolean shortForm) {
            this.shortForm = shortForm;
            return this;
        }

        public EditField setLongForm(boolean longForm) {
            this.longForm = longForm;
            return this;
        }

        public EditField setPhoneticsColumn(String phoneticsColumn) {
            this.phoneticsColumn = phoneticsColumn;
            return this;
        }

        public EditField setMinLines(int minLines) {
            this.minLines = minLines;
            return this;
        }

        public boolean isMultiLine() {
            return (inputType & EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
        }


        @Override
        public String toString() {
            return this.getClass().getSimpleName() + ":"
                    + " column=" + column
                    + " titleRes=" + titleRes
                    + " inputType=" + inputType
                    + " minLines=" + minLines
                    + " optional=" + optional
                    + " shortForm=" + shortForm
                    + " longForm=" + longForm;
        }
    }

    /**
     * Generic method of inflating a given {@link ContentValues} into a user-readable
     * {@link CharSequence}. For example, an inflater could combine the multiple
     * columns of {@link StructuredPostal} together using a string resource
     * before presenting to the user.
     */
    public interface StringInflater {
        public CharSequence inflateUsing(Context context, ContentValues values);
    }

    /**
     * Compare two {@link AccountType} by their {@link AccountType#getDisplayLabel} with the
     * current locale.
     */
    public static class DisplayLabelComparator implements Comparator<AccountType> {
        private final Context mContext;
        /** {@link Comparator} for the current locale. */
        private final Collator mCollator = Collator.getInstance();

        public DisplayLabelComparator(Context context) {
            mContext = context;
        }

        private String getDisplayLabel(AccountType type) {
            CharSequence label = type.getDisplayLabel(mContext);
            return (label == null) ? "" : label.toString();
        }

        @Override
        public int compare(AccountType lhs, AccountType rhs) {
            return mCollator.compare(getDisplayLabel(lhs), getDisplayLabel(rhs));
        }
    }
}
