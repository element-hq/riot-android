/*
 * Copyright 2017 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.util.Pair;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import im.vector.VectorApp;

/**
 * This class contains the phone number toolbox
 */
public class PhoneNumberUtils {
    private static final String LOG_TAG = PhoneNumberUtils.class.getSimpleName();

    // preference keys
    public static final String COUNTRY_CODE_PREF_KEY = "COUNTRY_CODE_PREF_KEY";

    private static String[] mCountryCodes;
    private static String[] mCountryNames;
    // ex FR -> France
    private static Map<String, String> mCountryNameByCC;
    // ex FR -> 33
    private static List<CountryPhoneData> mCountryIndicatorList;

    /**
     * The locale has been updated.
     * The country code to string maps become invalid
     */
    public static void onLocaleUpdate() {
        mCountryCodes = null;
        mCountryIndicatorList = null;
    }

    /**
     * Build the country codes list
     */
    private static void buildCountryCodesList() {
        if (null == mCountryCodes) {
            Locale applicationLocale = VectorApp.getApplicationLocale();

            // retrieve the ISO country code
            String[] isoCountryCodes = Locale.getISOCountries();
            List<Pair<String, String>> countryCodes = new ArrayList<>();

            // retrieve the human display name
            for (String countryCode : isoCountryCodes) {
                Locale locale = new Locale("", countryCode);
                countryCodes.add(new Pair<>(countryCode, locale.getDisplayCountry(applicationLocale)));
            }

            // sort by human display names
            Collections.sort(countryCodes, new Comparator<Pair<String, String>>() {
                @Override
                public int compare(Pair<String, String> lhs, Pair<String, String> rhs) {
                    return lhs.second.compareTo(rhs.second);
                }
            });

            mCountryNameByCC = new HashMap<>(isoCountryCodes.length);
            mCountryCodes = new String[isoCountryCodes.length];
            mCountryNames = new String[isoCountryCodes.length];

            for (int index = 0; index < isoCountryCodes.length; index++) {
                Pair<String, String> pair = countryCodes.get(index);

                mCountryCodes[index] = pair.first;
                mCountryNames[index] = pair.second;
                mCountryNameByCC.put(pair.first, pair.second);
            }
        }
    }

    /**
     * Get the list of all country names with their phone number indicator
     *
     * @return list of pair name - indicator
     */
    public static List<CountryPhoneData> getCountriesWithIndicator() {
        if (mCountryIndicatorList == null) {
            mCountryIndicatorList = new ArrayList<>();

            buildCountryCodesList();
            for (Map.Entry<String, String> entry : mCountryNameByCC.entrySet()) {
                final int indicator = PhoneNumberUtil.getInstance().getCountryCodeForRegion(entry.getKey());
                if (indicator > 0) {
                    mCountryIndicatorList.add(new CountryPhoneData(entry.getKey(), entry.getValue(), indicator));
                }
            }
            // sort by human display names
            Collections.sort(mCountryIndicatorList, new Comparator<CountryPhoneData>() {
                @Override
                public int compare(CountryPhoneData lhs, CountryPhoneData rhs) {
                    return lhs.getCountryName().compareTo(rhs.getCountryName());
                }
            });
        }

        return mCountryIndicatorList;
    }

    /**
     * Provide a human readable name for a a country code.
     *
     * @param countryCode the country code
     * @return the human readable name
     */
    public static String getHumanCountryCode(final String countryCode) {
        buildCountryCodesList();
        String name = null;

        if (!TextUtils.isEmpty(countryCode)) {
            name = mCountryNameByCC.get(countryCode);
        }

        return name;
    }

    /**
     * Provide the selected country code
     *
     * @param context the application context
     * @return the ISO country code or "" if it does not exist
     */
    public static String getCountryCode(final Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (!preferences.contains(COUNTRY_CODE_PREF_KEY) || TextUtils.isEmpty(preferences.getString(COUNTRY_CODE_PREF_KEY, ""))) {
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                String countryCode = tm.getNetworkCountryIso().toUpperCase(VectorApp.getApplicationLocale());
                if (TextUtils.isEmpty(countryCode)
                        && !TextUtils.isEmpty(Locale.getDefault().getCountry())
                        && PhoneNumberUtil.getInstance().getCountryCodeForRegion(Locale.getDefault().getCountry()) != 0) {
                    // Use Locale as a last resort
                    setCountryCode(context, Locale.getDefault().getCountry());
                } else {
                    setCountryCode(context, countryCode);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getCountryCode failed " + e.getMessage());
            }
        }

        return preferences.getString(COUNTRY_CODE_PREF_KEY, "");
    }

    /**
     * Update the selected country code.
     *
     * @param context     the context
     * @param countryCode the country code
     */
    public static void setCountryCode(final Context context, final String countryCode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(COUNTRY_CODE_PREF_KEY, countryCode);
        editor.commit();
    }

    /**
     * Compute an unique key from a text and a country code
     *
     * @param text        the text
     * @param countryCode the country code
     * @return the unique key
     */
    private static String getMapKey(final String text, final String countryCode) {
        return "μ" + countryCode + "μ" + text;
    }

    /**
     * Phone numbers cache by text.
     */
    private static final HashMap<String, Object> mPhoneNumberByText = new HashMap<>();

    /**
     * Provide libphonenumber phonenumber from an unformatted one.
     *
     * @param text        the unformatted phone number
     * @param countryCode the cuntry code
     * @return the phone number
     */
    private static Phonenumber.PhoneNumber getPhoneNumber(final String text, final String countryCode) {
        String key = getMapKey(text, countryCode);
        Phonenumber.PhoneNumber phoneNumber = null;

        if (mPhoneNumberByText.containsKey(key)) {
            Object value = mPhoneNumberByText.get(key);

            if (value instanceof Phonenumber.PhoneNumber) {
                phoneNumber = (Phonenumber.PhoneNumber) value;
            }
        } else {
            try {
                phoneNumber = PhoneNumberUtil.getInstance().parse(text, countryCode);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getPhoneNumber() : failed " + e.getMessage());
            }

            if (null != phoneNumber) {
                mPhoneNumberByText.put(key, phoneNumber);
            } else {
                // add a dummy object to avoid searching twice
                mPhoneNumberByText.put(key, "");
            }
        }

        return phoneNumber;
    }

    /**
     * E164 phone number by unformatted phonenumber
     */
    private static final HashMap<String, String> mE164PhoneNumberByText = new HashMap<>();

    /**
     * Convert an unformatted phone number to a E164 format one.
     *
     * @param context     the coontext
     * @param phoneNumber the unformatted phone number
     * @return the E164 phone number
     */
    public static String getE164format(final Context context, final String phoneNumber) {
        return getE164format(phoneNumber, getCountryCode(context));
    }

    /**
     * Convert an unformatted phone number to a E164 format one.
     *
     * @param phoneNumber the unformatted phone number
     * @param countryCode teh country code
     * @return the E164 phone number
     */
    private static String getE164format(final String phoneNumber, final String countryCode) {
        if (TextUtils.isEmpty(phoneNumber) || TextUtils.isEmpty(countryCode)) {
            return null;
        }

        String key = getMapKey(phoneNumber, countryCode);
        String e164Pn = mE164PhoneNumberByText.get(key);

        if (null == e164Pn) {
            e164Pn = "";
            try {
                Phonenumber.PhoneNumber pn = getPhoneNumber(phoneNumber, countryCode);
                if (null != pn) {
                    e164Pn = PhoneNumberUtil.getInstance().format(pn, PhoneNumberUtil.PhoneNumberFormat.E164);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getE164format() failed " + e.getMessage());
            }

            if (e164Pn.startsWith("+")) {
                e164Pn = e164Pn.substring(1);
            }

            mE164PhoneNumberByText.put(key, e164Pn);
        }

        return !TextUtils.isEmpty(e164Pn) ? e164Pn : null;
    }


    /**
     * Convert a @{@link com.google.i18n.phonenumbers.Phonenumber.PhoneNumber} to a string with E164 format.
     *
     * @param phoneNumber
     * @return formatted screen
     */
    public static String getE164format(final Phonenumber.PhoneNumber phoneNumber) {
        String phoneNumberFormatted = null;
        if (phoneNumber != null) {
            phoneNumberFormatted = PhoneNumberUtil.getInstance().format(phoneNumber, PhoneNumberUtil.PhoneNumberFormat.E164);
        }
        return phoneNumberFormatted;
    }
}
