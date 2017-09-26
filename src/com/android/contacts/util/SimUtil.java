package com.android.contacts.util;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for sim specific util methods.
 */
public class SimUtil {
    private static final String TAG = SimUtil.class.getSimpleName();

    /**
     * The method will return Single slot SIM phone number only if user is owner and number
     * exist in SIM memory Otherwise, return null
     * @param context
     * @return SIM phone number
     */
    public static String getLine1Number(Context context) {
        return getLine1Number(context, 0);
    }

    /**
     * The method will return all SIM phone numbers only if user is owner and number
     * exist in SIM memory Otherwise, return null
     * @param context
     * @return SIM phone number
     */
    public static List<String> getLine1Numbers(Context context) {
        ArrayList<String> list = new ArrayList<String>();
        int count = getSimCount(context);

        if (count > 1) {
            for (int i = 0; i < count; i++) {
                list.add(getLine1Number(context, i));
            }
            return list;
        } else {
            list.add(getLine1Number(context));
            return list;
        }
    }

    /**
     * The method will return SIM phone number only if user is owner and number
     * exist in SIM memory Otherwise, return null
     * @param context
     * @param slotId The slot id. If Single Slot, ignore this paramter.
     * @return SIM phone number
     */
    public static String getLine1Number(Context context, int slotId) {
        if (!isMultiSimEnabled(context)) {
            return getTelephonyManager(context).getLine1Number();
        } else {
            SubscriptionInfo sir = getActiveSubscriptionInfo(context, slotId);
            if (sir != null) {
                return sir.getNumber();
            } else {
                return null;
            }
        }
    }

    /**
     * Return whether or not the device is multi sim capable.
     * @return true if the device is multi sim capable, otherwise false.
     */
    public static boolean isMultiSimEnabled(Context context) {
        boolean ret = false;
        TelephonyManager tm = getTelephonyManager(context);

        try {
            Method get = tm.getClass().getMethod("isMultiSimEnabled");

            ret = (boolean) get.invoke(tm);
        } catch (Exception e) {
            Log.e(TAG, "isMultiSimEnabled exception: " + e.toString());
        }

        return ret;
    }

    /**
     * The method will return SIM count.
     * @param context
     * @return SIM count
     */
    public static int getSimCount(Context context) {
        return getTelephonyManager(context).getPhoneCount();
    }

    /**
     * Checks whether SIM card is absent.
     *
     * @param context the calling {@link Context}
     * @param slotId slot of the SIM card to be checked. If Single Slot, ignore this paramter.
     * @return <code>true</code> if SIM card is not ready, <code>false</code>
     *         otherwise.
     */
    public static boolean isSimAbsent(Context context, int slotId) {
        return getSimState(context, slotId) != TelephonyManager.SIM_STATE_READY;
    }

    /**
     * Returns a constant indicating the state of the default SIM card.
     *
     * @param slotId The slot id of the SIM. If Single Slot, ignore this paramter.
     * @return The SIM state
     * @see #SIM_STATE_UNKNOWN
     * @see #SIM_STATE_ABSENT
     * @see #SIM_STATE_PIN_REQUIRED
     * @see #SIM_STATE_PUK_REQUIRED
     * @see #SIM_STATE_NETWORK_LOCKED
     * @see #SIM_STATE_READY
     * @see #SIM_STATE_NOT_READY
     * @see #SIM_STATE_PERM_DISABLED
     * @see #SIM_STATE_CARD_IO_ERROR
     */
    public static int getSimState(Context context, int slotId) {
        TelephonyManager tm = getTelephonyManager(context);
        if (tm != null) {
            if (!isMultiSimEnabled(context)) {
                return tm.getSimState();
            } else {
                return getSimState(tm, slotId);
            }
        }
        return TelephonyManager.SIM_STATE_UNKNOWN;
    }

    private static int getSimState(TelephonyManager tm, int slotId) {
        int ret = TelephonyManager.SIM_STATE_UNKNOWN;

        try {
            Class[] paramTypes = { int.class };
            Method get = tm.getClass().getMethod("getSimState", paramTypes);

            Object[] params = { slotId };
            ret = (int) get.invoke(tm, params);
        } catch (Exception e) {
            Log.e(TAG, "getSimState exception: " + e.toString());
        }

        return ret;
    }

    private static SubscriptionInfo getActiveSubscriptionInfo(Context context, final int slotId) {
        final List<SubscriptionInfo> subInfoList =
                SubscriptionManager.from(context).getActiveSubscriptionInfoList();
        if (subInfoList != null) {
            final int subInfoLength = subInfoList.size();

            for (int i = 0; i < subInfoLength; ++i) {
                final SubscriptionInfo sir = subInfoList.get(i);
                if (sir.getSimSlotIndex() == slotId) {
                    return sir;
                }
            }
        }

        return null;
    }

    private static TelephonyManager getTelephonyManager(Context context) {
        return (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
    }
}
