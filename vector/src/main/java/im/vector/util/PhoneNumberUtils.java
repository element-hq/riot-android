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

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.matrix.androidsdk.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * This class contains the phone number toolbox
 */
public class PhoneNumberUtils {

    private static final String LOG_TAG = "PhoneNumberUtils";

    // preference keys
    private static final String COUNTRY_CODE_PREF_KEY = "COUNTRY_CODE_PREF_KEY";

    /**
     * Retrieve the international phone number prefix from the country code.
     * @param code the country code.
     * @return the international phone number prefix.
     */
    public static String getPhonePrefix(String code) {
        return mCountry2phone.get(code.toUpperCase());
    }

    /**
     * @return the country codes.
     */
    public static Collection<String> getCountryCodes() {
        return mCountry2phone.keySet();
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

    /**
     * International phone number prefixes by country code.
     */
    private static Map<String, String> mCountry2phone = new HashMap<>();
    
    static {
        mCountry2phone.put("AF", "+93");
        mCountry2phone.put("AL", "+355");
        mCountry2phone.put("DZ", "+213");
        mCountry2phone.put("AD", "+376");
        mCountry2phone.put("AO", "+244");
        mCountry2phone.put("AG", "+1-268");
        mCountry2phone.put("AR", "+54");
        mCountry2phone.put("AM", "+374");
        mCountry2phone.put("AU", "+61");
        mCountry2phone.put("AT", "+43");
        mCountry2phone.put("AZ", "+994");
        mCountry2phone.put("BS", "+1-242");
        mCountry2phone.put("BH", "+973");
        mCountry2phone.put("BD", "+880");
        mCountry2phone.put("BB", "+1-246");
        mCountry2phone.put("BY", "+375");
        mCountry2phone.put("BE", "+32");
        mCountry2phone.put("BZ", "+501");
        mCountry2phone.put("BJ", "+229");
        mCountry2phone.put("BT", "+975");
        mCountry2phone.put("BO", "+591");
        mCountry2phone.put("BA", "+387");
        mCountry2phone.put("BW", "+267");
        mCountry2phone.put("BR", "+55");
        mCountry2phone.put("BN", "+673");
        mCountry2phone.put("BG", "+359");
        mCountry2phone.put("BF", "+226");
        mCountry2phone.put("BI", "+257");
        mCountry2phone.put("KH", "+855");
        mCountry2phone.put("CM", "+237");
        mCountry2phone.put("CA", "+1");
        mCountry2phone.put("CV", "+238");
        mCountry2phone.put("CF", "+236");
        mCountry2phone.put("TD", "+235");
        mCountry2phone.put("CL", "+56");
        mCountry2phone.put("CN", "+86");
        mCountry2phone.put("CO", "+57");
        mCountry2phone.put("KM", "+269");
        mCountry2phone.put("CD", "+243");
        mCountry2phone.put("CG", "+242");
        mCountry2phone.put("CR", "+506");
        mCountry2phone.put("CI", "+225");
        mCountry2phone.put("HR", "+385");
        mCountry2phone.put("CU", "+53");
        mCountry2phone.put("CY", "+357");
        mCountry2phone.put("CZ", "+420");
        mCountry2phone.put("DK", "+45");
        mCountry2phone.put("DJ", "+253");
        mCountry2phone.put("DM", "+1-767");
        mCountry2phone.put("DO", "+1-809");
        mCountry2phone.put("EC", "+593");
        mCountry2phone.put("EG", "+20");
        mCountry2phone.put("SV", "+503");
        mCountry2phone.put("GQ", "+240");
        mCountry2phone.put("ER", "+291");
        mCountry2phone.put("EE", "+372");
        mCountry2phone.put("ET", "+251");
        mCountry2phone.put("FJ", "+679");
        mCountry2phone.put("FI", "+358");
        mCountry2phone.put("FR", "+33");
        mCountry2phone.put("GA", "+241");
        mCountry2phone.put("GM", "+220");
        mCountry2phone.put("GE", "+995");
        mCountry2phone.put("DE", "+49");
        mCountry2phone.put("GH", "+233");
        mCountry2phone.put("GR", "+30");
        mCountry2phone.put("GD", "+1-473");
        mCountry2phone.put("GT", "+502");
        mCountry2phone.put("GN", "+224");
        mCountry2phone.put("GW", "+245");
        mCountry2phone.put("GY", "+592");
        mCountry2phone.put("HT", "+509");
        mCountry2phone.put("HN", "+504");
        mCountry2phone.put("HU", "+36");
        mCountry2phone.put("IS", "+354");
        mCountry2phone.put("IN", "+91");
        mCountry2phone.put("ID", "+62");
        mCountry2phone.put("IR", "+98");
        mCountry2phone.put("IQ", "+964");
        mCountry2phone.put("IE", "+353");
        mCountry2phone.put("IL", "+972");
        mCountry2phone.put("IT", "+39");
        mCountry2phone.put("JM", "+1-876");
        mCountry2phone.put("JP", "+81");
        mCountry2phone.put("JO", "+962");
        mCountry2phone.put("KZ", "+7");
        mCountry2phone.put("KE", "+254");
        mCountry2phone.put("KI", "+686");
        mCountry2phone.put("KP", "+850");
        mCountry2phone.put("KR", "+82");
        mCountry2phone.put("KW", "+965");
        mCountry2phone.put("KG", "+996");
        mCountry2phone.put("LA", "+856");
        mCountry2phone.put("LV", "+371");
        mCountry2phone.put("LB", "+961");
        mCountry2phone.put("LS", "+266");
        mCountry2phone.put("LR", "+231");
        mCountry2phone.put("LY", "+218");
        mCountry2phone.put("LI", "+423");
        mCountry2phone.put("LT", "+370");
        mCountry2phone.put("LU", "+352");
        mCountry2phone.put("MK", "+389");
        mCountry2phone.put("MG", "+261");
        mCountry2phone.put("MW", "+265");
        mCountry2phone.put("MY", "+60");
        mCountry2phone.put("MV", "+960");
        mCountry2phone.put("ML", "+223");
        mCountry2phone.put("MT", "+356");
        mCountry2phone.put("MH", "+692");
        mCountry2phone.put("MR", "+222");
        mCountry2phone.put("MU", "+230");
        mCountry2phone.put("MX", "+52");
        mCountry2phone.put("FM", "+691");
        mCountry2phone.put("MD", "+373");
        mCountry2phone.put("MC", "+377");
        mCountry2phone.put("MN", "+976");
        mCountry2phone.put("ME", "+382");
        mCountry2phone.put("MA", "+212");
        mCountry2phone.put("MZ", "+258");
        mCountry2phone.put("MM", "+95");
        mCountry2phone.put("NA", "+264");
        mCountry2phone.put("NR", "+674");
        mCountry2phone.put("NP", "+977");
        mCountry2phone.put("NL", "+31");
        mCountry2phone.put("NZ", "+64");
        mCountry2phone.put("NI", "+505");
        mCountry2phone.put("NE", "+227");
        mCountry2phone.put("NG", "+234");
        mCountry2phone.put("NO", "+47");
        mCountry2phone.put("OM", "+968");
        mCountry2phone.put("PK", "+92");
        mCountry2phone.put("PW", "+680");
        mCountry2phone.put("PA", "+507");
        mCountry2phone.put("PG", "+675");
        mCountry2phone.put("PY", "+595");
        mCountry2phone.put("PE", "+51");
        mCountry2phone.put("PH", "+63");
        mCountry2phone.put("PL", "+48");
        mCountry2phone.put("PT", "+351");
        mCountry2phone.put("QA", "+974");
        mCountry2phone.put("RO", "+40");
        mCountry2phone.put("RU", "+7");
        mCountry2phone.put("RW", "+250");
        mCountry2phone.put("KN", "+1-869");
        mCountry2phone.put("LC", "+1-758");
        mCountry2phone.put("VC", "+1-784");
        mCountry2phone.put("WS", "+685");
        mCountry2phone.put("SM", "+378");
        mCountry2phone.put("ST", "+239");
        mCountry2phone.put("SA", "+966");
        mCountry2phone.put("SN", "+221");
        mCountry2phone.put("RS", "+381");
        mCountry2phone.put("SC", "+248");
        mCountry2phone.put("SL", "+232");
        mCountry2phone.put("SG", "+65");
        mCountry2phone.put("SK", "+421");
        mCountry2phone.put("SI", "+386");
        mCountry2phone.put("SB", "+677");
        mCountry2phone.put("SO", "+252");
        mCountry2phone.put("ZA", "+27");
        mCountry2phone.put("ES", "+34");
        mCountry2phone.put("LK", "+94");
        mCountry2phone.put("SD", "+249");
        mCountry2phone.put("SR", "+597");
        mCountry2phone.put("SZ", "+268");
        mCountry2phone.put("SE", "+46");
        mCountry2phone.put("CH", "+41");
        mCountry2phone.put("SY", "+963");
        mCountry2phone.put("TJ", "+992");
        mCountry2phone.put("TZ", "+255");
        mCountry2phone.put("TH", "+66");
        mCountry2phone.put("TL", "+670");
        mCountry2phone.put("TG", "+228");
        mCountry2phone.put("TO", "+676");
        mCountry2phone.put("TT", "+1-868");
        mCountry2phone.put("TN", "+216");
        mCountry2phone.put("TR", "+90");
        mCountry2phone.put("TM", "+993");
        mCountry2phone.put("TV", "+688");
        mCountry2phone.put("UG", "+256");
        mCountry2phone.put("UA", "+380");
        mCountry2phone.put("AE", "+971");
        mCountry2phone.put("GB", "+44");
        mCountry2phone.put("US", "+1");
        mCountry2phone.put("UY", "+598");
        mCountry2phone.put("UZ", "+998");
        mCountry2phone.put("VU", "+678");
        mCountry2phone.put("VA", "+379");
        mCountry2phone.put("VE", "+58");
        mCountry2phone.put("VN", "+84");
        mCountry2phone.put("YE", "+967");
        mCountry2phone.put("ZM", "+260");
        mCountry2phone.put("ZW", "+263");
        mCountry2phone.put("GE", "+995");
        mCountry2phone.put("TW", "+886");
        mCountry2phone.put("SO", "+252");
        mCountry2phone.put("GE", "+995");
        mCountry2phone.put("CX", "+61");
        mCountry2phone.put("CC", "+61");
        mCountry2phone.put("NF", "+672");
        mCountry2phone.put("NC", "+687");
        mCountry2phone.put("PF", "+689");
        mCountry2phone.put("YT", "+262");
        mCountry2phone.put("GP", "+590");
        mCountry2phone.put("GP", "+590");
        mCountry2phone.put("PM", "+508");
        mCountry2phone.put("WF", "+681");
        mCountry2phone.put("CK", "+682");
        mCountry2phone.put("NU", "+683");
        mCountry2phone.put("TK", "+690");
        mCountry2phone.put("GG", "+44");
        mCountry2phone.put("IM", "+44");
        mCountry2phone.put("JE", "+44");
        mCountry2phone.put("AI", "+1-264");
        mCountry2phone.put("BM", "+1-441");
        mCountry2phone.put("IO", "+246");
        mCountry2phone.put("VG", "+1-284");
        mCountry2phone.put("KY", "+1-345");
        mCountry2phone.put("FK", "+500");
        mCountry2phone.put("GI", "+350");
        mCountry2phone.put("MS", "+1-664");
        mCountry2phone.put("SH", "+290");
        mCountry2phone.put("TC", "+1-649");
        mCountry2phone.put("MP", "+1-670");
        mCountry2phone.put("PR", "+1-787");
        mCountry2phone.put("AS", "+1-684");
        mCountry2phone.put("GU", "+1-671");
        mCountry2phone.put("VI", "+1-340");
        mCountry2phone.put("HK", "+852");
        mCountry2phone.put("MO", "+853");
        mCountry2phone.put("FO", "+298");
        mCountry2phone.put("GL", "+299");
        mCountry2phone.put("GF", "+594");
        mCountry2phone.put("GP", "+590");
        mCountry2phone.put("MQ", "+596");
        mCountry2phone.put("RE", "+262");
        mCountry2phone.put("AX", "+358-18");
        mCountry2phone.put("AW", "+297");
        mCountry2phone.put("AN", "+599");
        mCountry2phone.put("SJ", "+47");
        mCountry2phone.put("AC", "+247");
        mCountry2phone.put("TA", "+290");
        mCountry2phone.put("CS", "+381");
        mCountry2phone.put("PS", "+970");
        mCountry2phone.put("EH", "+212");
    }

}
