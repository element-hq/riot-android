/*
 * Copyright 2017 OpenMarket Ltd
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
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Pair;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;

/**
 * This class contains the phone number toolbox
 */
public class PhoneNumberUtils {

    private static final String LOG_TAG = "PhoneNumberUtils";

    // preference keys
    public static final String COUNTRY_CODE_PREF_KEY = "COUNTRY_CODE_PREF_KEY";

    private static String[] mCountryCodes = null;
    private static String[] mHumanCountryCodes = null;
    private static HashMap<String, String> mHumanCountryCodeByCC = null;

    /**
     * Build the country codes list
     */
    private static void buildContryCodesList() {
        if (null == mCountryCodes) {
            // retrieve the ISO country code
            String[] isoCountryCodes = Locale.getISOCountries();
            ArrayList<Pair<String, String>> countryCodes = new ArrayList<>();

            // retrieve the human display name
            for (String countryCode : isoCountryCodes) {
                Locale locale = new Locale("", countryCode);
                countryCodes.add(new Pair<>(countryCode, locale.getDisplayCountry()));
            }

            // sort by human display names
            Collections.sort(countryCodes, new Comparator<Pair<String, String>>() {
                @Override
                public int compare(Pair<String, String> lhs, Pair<String, String> rhs) {
                    return lhs.second.compareTo(rhs.second);
                }
            });

            mHumanCountryCodeByCC = new HashMap<>(isoCountryCodes.length);
            mCountryCodes = new String[isoCountryCodes.length];
            mHumanCountryCodes = new String[isoCountryCodes.length];

            for(int index = 0; index < isoCountryCodes.length; index++) {
                Pair<String, String> pair = countryCodes.get(index);

                mCountryCodes[index] = pair.first;
                mHumanCountryCodes[index] = pair.second;
                mHumanCountryCodeByCC.put(pair.first, pair.second);
            }
        }
    }

    /**
     * @return the country codes.
     */
    public static String[] getCountryCodes() {
        buildContryCodesList();
        return mCountryCodes;
    }
    /**
     * @return the human readable country codes.
     */
    public static String[] getHumanCountryCodes() {
        buildContryCodesList();
        return mHumanCountryCodes;
    }

    /**
     * Provide a human readable name for a a country code.
     * @param countryCode the country code
     * @return the human readable name
     */
    public static String getHumanCountryCode(String countryCode) {
        String name = null;

        if (!TextUtils.isEmpty(countryCode)) {
            name = mHumanCountryCodeByCC.get(countryCode);
        }

        return name;
    }

    /**
     * Provide the selected country code
     * @param context the application context
     * @return the ISO country code or "" if it does not exist
     */
    public static String getCountryCode(Context context) {
        String countryCode = "";

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (!preferences.contains(COUNTRY_CODE_PREF_KEY)) {
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                countryCode = tm.getNetworkCountryIso().toUpperCase();
                setCountryCode(context, countryCode);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getCountryCode failed " + e.getMessage());
            }
        } else {
            countryCode = preferences.getString(COUNTRY_CODE_PREF_KEY, "");
        }

        return countryCode;
    }

    /**
     * Update the selected country code.
     * @param context the context
     * @param countryCode the country code
     */
    public static void setCountryCode(Context context, String countryCode) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(COUNTRY_CODE_PREF_KEY, countryCode);
        editor.commit();
    }

    /**
     * Compute an unique key from a text and a contry code
     * @param text the text
     * @param countryCode the country code
     * @return the unique key
     */
    private static String getMapKey(String text, String countryCode) {
        return "μ" +  countryCode + "μ" + text;
    }

    /**
     * Phone numbers cache by text.
     */
    static HashMap<String, Object> mPhoneNumberByText = new HashMap<>();

    /**
     * Provide libphonenumber phonenumber from an unformatted one.
     * @param text the unformatted phone number
     * @param countryCode the cuntry code
     * @return the phone number
     */
    public static Phonenumber.PhoneNumber getPhoneNumber(String text, String countryCode) {
        String key = getMapKey(text, countryCode);
        Phonenumber.PhoneNumber phoneNumber = null;

        if (mPhoneNumberByText.containsKey(key)) {
            Object value = mPhoneNumberByText.get(key);

            if (value instanceof Phonenumber.PhoneNumber) {
                phoneNumber = (Phonenumber.PhoneNumber)value;
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
    static HashMap<String, String> mE164PhoneNumberByText = new HashMap<>();

    /**
     * Convert an unformatted phone number to a E164 format one.
     * @param context the coontext
     * @param phoneNumber the unformatted phone number
     * @return the E164 phone number
     */
    public static String getE164format(Context context, String phoneNumber) {
        return getE164format(phoneNumber, getCountryCode(context));
    }

    /**
     * Convert an unformatted phone number to a E164 format one.
     * @param phoneNumber the unformatted phone number
     * @param countryCode teh country code
     * @return the E164 phone number
     */
    public static String getE164format(String phoneNumber, String countryCode) {
        if (TextUtils.isEmpty(phoneNumber) || TextUtils.isEmpty(countryCode)) {
            return null;
        }

        String key = getMapKey(phoneNumber, countryCode);
        String e164Pn = mE164PhoneNumberByText.get(key);

        if (null == e164Pn) {
            e164Pn = "";
            try {
                e164Pn = PhoneNumberUtil.getInstance().format(getPhoneNumber(phoneNumber, countryCode), PhoneNumberUtil.PhoneNumberFormat.E164);
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
}
