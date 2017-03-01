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

package im.vector;

import android.support.annotation.IntDef;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.EditText;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import im.vector.util.PhoneNumberUtils;

/**
 * Helper class to handle phone number formatting and validation
 */
public class PhoneNumberHandler implements TextWatcher {
    private static final String LOG_TAG = PhoneNumberHandler.class.getSimpleName();

    @IntDef({DISPLAY_COUNTRY_FULL_NAME, DISPLAY_COUNTRY_ISO_CODE})
    @Retention(RetentionPolicy.SOURCE)
    @interface DisplayMode {}
    public static final int DISPLAY_COUNTRY_FULL_NAME = 0;
    public static final int DISPLAY_COUNTRY_ISO_CODE = 1;

    private EditText mPhoneNumberInput;
    private EditText mCountryCodeInput;

    private int mDisplayMode;

    // Ex "FR"
    private String mCountryCode;
    // Ex "+33"
    private String mCurrentPhonePrefix;
    private Phonenumber.PhoneNumber mCurrentPhoneNumber;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public PhoneNumberHandler(EditText phoneNumberInput, EditText countryCodeInput, @DisplayMode int displayMode) {
        mPhoneNumberInput = phoneNumberInput;
        mCountryCodeInput = countryCodeInput;
        mDisplayMode = displayMode;

        if (mPhoneNumberInput != null) {
            mPhoneNumberInput.addTextChangedListener(this);
        }
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Remove references to View components to avoid memory leak
     */
    public void release(){
        mPhoneNumberInput.removeTextChangedListener(this);
        mPhoneNumberInput = null;
        mCountryCodeInput = null;
    }

    public void setCountryCode(final String newCountryCode) {
        if (TextUtils.isEmpty(mPhoneNumberInput.getText())) {
            updateCountryCode(newCountryCode);
            initPhoneWithPrefix();
        } else {
            // Clear old prefix from phone before assigning new one
            String updatedPhone = mPhoneNumberInput.getText().toString();
            if (mCurrentPhonePrefix != null && updatedPhone.startsWith(mCurrentPhonePrefix)) {
                updatedPhone = updatedPhone.substring(mCurrentPhonePrefix.length());
            }
            updateCountryCode(newCountryCode);

            if (TextUtils.isEmpty(updatedPhone)) {
                initPhoneWithPrefix();
            } else {
                formatPhoneNumber(updatedPhone);
            }
        }
    }

    public boolean isValidPhoneNumber(){
        return mCurrentPhoneNumber != null && PhoneNumberUtil.getInstance().isPossibleNumber(mCurrentPhoneNumber);
    }

    public boolean isPhoneNumberValidForCountry(){
        return mCurrentPhoneNumber != null && PhoneNumberUtil.getInstance().isValidNumberForRegion(mCurrentPhoneNumber, mCountryCode);
    }

    public Phonenumber.PhoneNumber getPhoneNumber(){
        return mCurrentPhoneNumber;
    }

    public String gete164PhoneNumber(){
        return mCurrentPhoneNumber == null ? null : PhoneNumberUtils.getE164format(mCurrentPhoneNumber);
    }

    public String getMsisdnPhoneNumber(){
        String msisdn = gete164PhoneNumber();
        if (msisdn != null && msisdn.startsWith("+")) {
            msisdn.substring(1);
        }
        return msisdn;
    }

    public String getCountryCode(){
        // Always extract from phone number object instead of using mCurrentRegionCode just in case
        return mCurrentPhoneNumber == null ? null : PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(mCurrentPhoneNumber.getCountryCode());
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    private void updateCountryCode(final String newCountryCode) {
        if (!TextUtils.isEmpty(newCountryCode) && !newCountryCode.equals(mCountryCode)) {
            mCountryCode = newCountryCode;
            switch (mDisplayMode) {
                case DISPLAY_COUNTRY_FULL_NAME:
                    mCountryCodeInput.setText(PhoneNumberUtils.getHumanCountryCode(mCountryCode));
                    break;
                case DISPLAY_COUNTRY_ISO_CODE:
                    mCountryCodeInput.setText(mCountryCode);
                    break;
            }
            // Update the prefix
            final int prefix = PhoneNumberUtil.getInstance().getCountryCodeForRegion(mCountryCode);
            if (prefix > 0) {
                mCurrentPhonePrefix = "+" + prefix;
            }
        }
    }

    private void initPhoneWithPrefix() {
        if (!TextUtils.isEmpty(mCurrentPhonePrefix)) {
            mPhoneNumberInput.setText(mCurrentPhonePrefix);
            mPhoneNumberInput.setSelection(mPhoneNumberInput.getText().length());
        }
    }

    private void formatPhoneNumber(final String rawPhoneNumber) {
        if (!TextUtils.isEmpty(mCountryCode)) {
            try {
                mCurrentPhoneNumber = PhoneNumberUtil.getInstance().parse(rawPhoneNumber.trim(), mCountryCode);
                final String formattedNumber = PhoneNumberUtil.getInstance().format(mCurrentPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
                if (!TextUtils.equals(formattedNumber, mPhoneNumberInput.getText())) {
                    // Update field with the formatted number
                    mPhoneNumberInput.setText(formattedNumber);
                    mPhoneNumberInput.setSelection(mPhoneNumberInput.getText().length());
                }
            } catch (NumberParseException e) {
                mCurrentPhoneNumber = null;
            }
        }
    }

    /*
     * *********************************************************************************************
     * Listeners
     * *********************************************************************************************
     */

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {

    }

    @Override
    public void afterTextChanged(Editable s) {
        formatPhoneNumber(s.toString());
    }

}
