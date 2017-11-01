package com.android.contacts.location;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import java.util.Locale;

/**
 * This class is used to detect the country where the user is. It is a simplified version of the
 * country detector service in the framework. The sources of country location are queried in the
 * following order of reliability:
 * <ul>
 * <li>Mobile network</li>
 * <li>SIM's country</li>
 * <li>User's default locale</li>
 * </ul>
 *
 * As far as possible this class tries to replicate the behavior of the system's country detector
 * service:
 * 1) Order in priority of sources of country location
 * 2) Mobile network information provided by CDMA phones is ignored
 */
public class CountryDetector {
    private static final String TAG = "CountryDetector";

    private static CountryDetector sInstance;

    private final Context mContext;
    private final LocaleProvider mLocaleProvider;
    private final TelephonyManager mTelephonyManager;

    // Used as a default country code when all the sources of country data have failed in the
    // exceedingly rare event that the device does not have a default locale set for some reason.
    private final String DEFAULT_COUNTRY_ISO = "US";

    /**
     * Class that can be used to return the user's default locale. This is in its own class so that
     * it can be mocked out.
     */
    public static class LocaleProvider {
        public Locale getDefaultLocale() {
            return Locale.getDefault();
        }
    }

    private CountryDetector(Context context) {
        this (context, (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE),
                new LocaleProvider());
    }

    private CountryDetector(Context context, TelephonyManager telephonyManager,
            LocaleProvider localeProvider) {
        mTelephonyManager = telephonyManager;
        mLocaleProvider = localeProvider;
        mContext = context;
    }

    /**
     * Factory method for {@link CountryDetector} that allows the caller to provide mock objects.
     */
    public CountryDetector getInstanceForTest(Context context, TelephonyManager telephonyManager,
            LocaleProvider localeProvider) {
        return new CountryDetector(context, telephonyManager, localeProvider);
    }

    /**
     * Returns the instance of the country detector. {@link #initialize(Context)} must have been
     * called previously.
     *
     * @return the initialized country detector.
     */
    public synchronized static CountryDetector getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CountryDetector(context.getApplicationContext());
        }
        return sInstance;
    }

    public String getCurrentCountryIso() {
        String result = null;
        if (isNetworkCountryCodeAvailable()) {
            result = getNetworkBasedCountryIso();
        }
        if (TextUtils.isEmpty(result)) {
            result = getSimBasedCountryIso();
        }
        if (TextUtils.isEmpty(result)) {
            result = getLocaleBasedCountryIso();
        }
        if (TextUtils.isEmpty(result)) {
            result = DEFAULT_COUNTRY_ISO;
        }
        return result.toUpperCase(Locale.US);
    }

    /**
     * @return the country code of the current telephony network the user is connected to.
     */
    private String getNetworkBasedCountryIso() {
        return mTelephonyManager.getNetworkCountryIso();
    }

    /**
     * @return the country code of the SIM card currently inserted in the device.
     */
    private String getSimBasedCountryIso() {
        return mTelephonyManager.getSimCountryIso();
    }

    /**
     * @return the country code of the user's currently selected locale.
     */
    private String getLocaleBasedCountryIso() {
        Locale defaultLocale = mLocaleProvider.getDefaultLocale();
        if (defaultLocale != null) {
            return defaultLocale.getCountry();
        }
        return null;
    }

    private boolean isNetworkCountryCodeAvailable() {
        // On CDMA TelephonyManager.getNetworkCountryIso() just returns the SIM's country code.
        return mTelephonyManager.getPhoneType() == TelephonyManager.PHONE_TYPE_GSM;
    }
}
