package com.android.contacts.util;

import android.content.ContentProviderOperation;
import android.content.ContentProviderOperation.Builder;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContactsEntity;
import android.text.TextUtils;
import android.util.Log;

import com.android.contacts.R;
import com.android.contacts.model.ContactLoader;
import com.android.contacts.model.RawContactDelta;
import com.android.contacts.model.RawContactDeltaList;
import com.android.contacts.model.ValuesDelta;
import com.android.contacts.model.account.AccountWithDataSet;
import com.android.contacts.util.SimUtil;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * "AutoFillProfile" feature allows auto gathering of the device's owner's data
 * from the device and saving it as the regular data rows in the profile database
 * for the "local" account's profile contact.
 * The intention of "AutoFillProfile" feature is to supply gathered information
 * as a part of the profile contact without involving user in adding them by
 * editing the profile contact manually.
 */
public class LocalProfile {
    private static final String TAG = LocalProfile.class.getSimpleName();

    // for the identification of the data written by LocalProfile
    public static final String FLAG_COLUMN = Data.SYNC2;
    public static final String FLAG_VALUE = "autoset:";

    // for the identification of dual SIM Slots in DSDS
    private static final String FLAG_SLOT_COLUMN = Data.SYNC3;

    private static final int SINGLE_SLOT = -1;

    private static boolean sIsSimNumberUpdated = false;

    /**
     * Creates a "local profile" raw_contact in the profile database if one already doesn't exist.
     * If a sim number doesn't exits, it will also update the number of "local profile".
     * This method performs database operations and shouldn't be called on the main thread.
     *
     * @param context
     */
    public static synchronized void updateProfileWithSimNumber(Context context) {
        // Check whether this method had been executed once due to performance advances.
        Log.d(TAG, "updateProfileWithSimNumber sIsSimNumberUpdated:" + sIsSimNumberUpdated);
        if (sIsSimNumberUpdated) {
            return;
        }

        // - If no SIM and no raw contact, this is first boot-up without SIM.
        //   In this case, nothing to do. Just return.
        // - If SIM exists and no raw contact, this is boot-up after SIM is inserted.
        //   In this case, needs to add SIM number.
        // - If no SIM and raw contact exists, this is boot-up after SIM is removed.
        //   In this case, needs to remove SIM number.
        if (!isSimExist(context) && !isRawContactExist(context)) {
            return;
        }

        long rawContactId = getRawContactId(context);
        Log.v(TAG, "rawContactId:" + rawContactId);

        if (rawContactId != -1) {
            autoFillProfile(context, rawContactId, Phone.CONTENT_ITEM_TYPE);
        }

        sIsSimNumberUpdated = true;
    }

    private static boolean isSimExist(Context context) {
        int simCount = SimUtil.getSimCount(context);
        for (int slot = 0; slot < simCount; slot++) {
            if (!SimUtil.isSimAbsent(context, slot)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRawContactExist(Context context) {
        boolean isRawContactExist = false;
        try (Cursor cursor = queryRawContact(context)) {
            if (cursor != null && cursor.getCount() > 0) {
                return true;
            }
        } catch (Exception e) {
            Log.d(TAG, "queryRawContact e = " + e.toString());
        }
        return false;
    }

    private static void autoFillProfile(Context context, long rawContactId, String mimeType) {
        StringBuilder whereClause = new StringBuilder()
                .append(RawContactsEntity.DELETED).append("=0 AND ")
                .append(Data.MIMETYPE).append(" IN ('")
                .append(mimeType).append("')");
        RawContactDeltaList set = RawContactDeltaList.fromQuery(
                RawContactsEntity.PROFILE_CONTENT_URI,
                context.getContentResolver(),
                whereClause.toString(), null, null);
        RawContactDelta entity = set.getByRawContactId(rawContactId);
        doUpdateWithDelta(context, entity, rawContactId, mimeType);
    }

    private static void doUpdateWithDelta(Context context, final RawContactDelta entity,
                                          long rawContactId, String mimeType) {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

        // Handle phone update
        int simCount = SimUtil.getSimCount(context);
        Log.d(TAG, "simCount:" + simCount);
        if (simCount > 1) {
            for (int slot = 0; slot < simCount; slot++) {
                pushSimOperation(context, entity, rawContactId, ops, slot, mimeType);
            }
        } else {
            pushSimOperation(context, entity, rawContactId, ops, SINGLE_SLOT, mimeType);
        }

        if (ops.size() > 0) {
            // update the local profile
            try {
                context.getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
            } catch (RemoteException e) {
                Log.e(TAG, e.toString());
            } catch (OperationApplicationException e) {
                Log.e(TAG, e.toString());
            }
        }
    }

    private static void pushSimOperation(Context context, final RawContactDelta entity,
                                         long rawContactId, ArrayList<ContentProviderOperation> ops,
                                         int sub, String mimeType) {
        ArrayList<ValuesDelta> phones = null;
        ValuesDelta phoneRowDelta = null;
        if (entity != null) {
            phones = entity.getMimeEntries(mimeType);
        }

        String simPhoneNumber = SimUtil.getLine1Number(context, sub);
        Log.d(TAG, "pushSimOperation simPhoneNumber:" + simPhoneNumber);

        if (phones != null) {
            for (ValuesDelta phoneRow : phones) {
                String phoneNumber = phoneRow.getAsString(Phone.NORMALIZED_NUMBER);
                String flag = phoneRow.getAsString(FLAG_COLUMN);
                Integer subscription = phoneRow.getAsInteger(FLAG_SLOT_COLUMN, SINGLE_SLOT);
                if ((FLAG_VALUE.equals(flag) || (phoneNumber != null && phoneNumber
                        .equals(simPhoneNumber))) && sub == subscription) {
                    // users already registered a similar phone number
                    // or we already registered it for them
                    phoneRowDelta = phoneRow;
                    break;
                }
            }
        }

        if (TextUtils.isEmpty(simPhoneNumber)) {
            if (phoneRowDelta != null) {
                phoneRowDelta.markDeleted();
            }
        } else {
            // find the old phone number, replace with the new one
            if (phoneRowDelta != null) {
                phoneRowDelta.put(Phone.NUMBER, simPhoneNumber);
                phoneRowDelta.put(FLAG_COLUMN, FLAG_VALUE);
                if (sub != SINGLE_SLOT) {
                    phoneRowDelta.put(FLAG_SLOT_COLUMN, sub);
                }
            } else {
                ContentValues values = new ContentValues();
                values.put(Phone.MIMETYPE, mimeType);
                values.put(Phone.NUMBER, simPhoneNumber);
                values.put(Phone.TYPE, Phone.TYPE_MOBILE);
                values.put(FLAG_COLUMN, FLAG_VALUE);
                values.put(Phone.IS_PRIMARY, 1);
                values.put(Phone.RAW_CONTACT_ID, rawContactId);
                if (sub != SINGLE_SLOT) {
                    values.put(FLAG_SLOT_COLUMN, sub);
                }
                phoneRowDelta = ValuesDelta.fromAfter(values);
            }
        }

        // update the phone number
        if (phoneRowDelta != null) {
            Builder builder = phoneRowDelta.buildDiff(Uri.withAppendedPath(Profile.CONTENT_URI,
                    RawContacts.Data.CONTENT_DIRECTORY));
            if (builder != null) {
                ops.add(builder.build());
            }
        }
    }

    /**
     * Only one local-contact rawcontact will be created in the profile
     * database. Thus, the rawcontact id won't be saved in the shared preference
     * any more.
     * <br />
     * The method will return the only one raw contact belonging to
     * local-contact and remove the abundant records if there're more than one
     * contact. If there's no such a contact, it'll create one.
     *
     * @return the raw contact id
     */
    public static long getRawContactId(Context context) {
        long id = -1;
        AccountWithDataSet localAccount = getLocalAccount(context);
        Cursor cursor = null;
        try {
            cursor = queryRawContact(context);
            if (cursor != null) {
                if (cursor.getCount() == 0) {
                    // Create local profile if it's not existent
                    id = createProfile(context, localAccount);
                } else if (cursor.moveToFirst()) {
                    id = cursor.getLong(0);
                    if (cursor.getCount() > 1) {
                        // Remove the abundant records and keep the first one
                        // This should really never happen...
                        Log.e(TAG,
                                "More than one local profile row_contact in profile database.");
                        String nameString;
                        String typeString;
                        String dataSetString;

                        if (localAccount.name == null) {
                            nameString = " is null";
                        } else {
                            nameString = " = '" +  localAccount.name + "'";
                        }
                        if (localAccount.type == null) {
                            typeString = " is null";
                        } else {
                            typeString = " = '" +  localAccount.type + "'";
                        }
                        if (localAccount.dataSet == null) {
                            dataSetString = " is null";
                        } else {
                            dataSetString = " = '" +  localAccount.dataSet + "'";
                        }
                        StringBuilder localProfileQuery = new StringBuilder(
                                RawContacts.ACCOUNT_NAME).append(nameString).append(" AND ").
                                append(RawContacts.ACCOUNT_TYPE).append(typeString).append(" AND ").
                                append(RawContacts.DATA_SET).append(dataSetString);

                        context.getContentResolver().delete(
                                Profile.CONTENT_RAW_CONTACTS_URI,
                                localProfileQuery.toString(), null);
                    }
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return id;
    }

    private static Cursor queryRawContact(Context context) {
        AccountWithDataSet localAccount = getLocalAccount(context);
        String nameString;
        String typeString;
        String dataSetString;

        if (localAccount.name == null) {
            nameString = " is null";
        } else {
            nameString = " = '" +  localAccount.name + "'";
        }
        if (localAccount.type == null) {
            typeString = " is null";
        } else {
            typeString = " = '" +  localAccount.type + "'";
        }
        if (localAccount.dataSet == null) {
            dataSetString = " is null";
        } else {
            dataSetString = " = '" +  localAccount.dataSet + "'";
        }
        StringBuilder localProfileQuery = new StringBuilder(
                RawContacts.ACCOUNT_NAME).append(nameString).append(" AND ").
                append(RawContacts.ACCOUNT_TYPE).append(typeString).append(" AND ").
                append(RawContacts.DATA_SET).append(dataSetString).append(" AND ").
                append(RawContacts.DELETED).append("= 0");
        return context.getContentResolver().query(Profile.CONTENT_RAW_CONTACTS_URI,
                new String[]{RawContacts._ID}, localProfileQuery.toString(), null, null);
    }

    private static long createProfile(Context context, AccountWithDataSet localAccount) {
        ContentValues values = new ContentValues();
        values.put(RawContacts.ACCOUNT_NAME, localAccount.name);
        values.put(RawContacts.ACCOUNT_TYPE, localAccount.type);
        values.put(RawContacts.DATA_SET, localAccount.dataSet);
        Uri contentUri = context.getContentResolver().insert(
                Profile.CONTENT_RAW_CONTACTS_URI, values);
        return ContentUris.parseId(contentUri);
    }

    private static AccountWithDataSet getLocalAccount(Context context) {
        return AccountWithDataSet.getNullAccount();
    }
}
