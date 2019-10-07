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

import android.app.Activity;
import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import im.vector.activity.CountryPickerActivity;
import im.vector.util.PhoneNumberUtils;

/**
 * Helper class to handle phone number formatting and validation
 */
public class PhoneNumberHandler implements TextWatcher, View.OnFocusChangeListener {

    @IntDef({DISPLAY_COUNTRY_FULL_NAME, DISPLAY_COUNTRY_ISO_CODE})
    @Retention(RetentionPolicy.SOURCE)
    @interface DisplayMode {
    }

    private static final int DISPLAY_COUNTRY_FULL_NAME = 0;
    public static final int DISPLAY_COUNTRY_ISO_CODE = 1;

    private EditText mPhoneNumberInput;
    private EditText mCountryCodeInput;

    private final int mDisplayMode;

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

    public PhoneNumberHandler(@NonNull final Activity activity,
                              @NonNull final EditText phoneNumberInput,
                              @NonNull final EditText countryCodeInput,
                              @DisplayMode final int displayMode,
                              final int requestCode) {
        mPhoneNumberInput = phoneNumberInput;
        mCountryCodeInput = countryCodeInput;
        mDisplayMode = displayMode;

        mPhoneNumberInput.addTextChangedListener(this);
        mPhoneNumberInput.setOnFocusChangeListener(this);

        // Hide picker by default so placeholder is visible
        mCountryCodeInput.setVisibility(View.GONE);

        mCountryCodeInput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!activity.isFinishing()) {
                    final Intent intent = CountryPickerActivity.getIntent(activity, true);
                    activity.startActivityForResult(intent, requestCode);
                }
            }
        });
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Remove references to View components to avoid memory leak
     */
    public void release() {
        mPhoneNumberInput.removeTextChangedListener(this);
        mPhoneNumberInput = null;
        mCountryCodeInput = null;
    }

    /**
     * Clear phone number data
     */
    public void reset() {
        mCurrentPhoneNumber = null;
        mPhoneNumberInput.setText("");
        mCountryCodeInput.setVisibility(View.GONE);
    }

    /**
     * Reformat current phone number according to the new country code + set new country code
     *
     * @param newCountryCode
     */
    public void setCountryCode(final String newCountryCode) {
        if (TextUtils.isEmpty(mPhoneNumberInput.getText())) {
            updateCountryCode(newCountryCode);
        } else {
            // Clear old prefix from phone before assigning new one
            String updatedPhone = mPhoneNumberInput.getText().toString();
            if (mCurrentPhonePrefix != null && updatedPhone.startsWith(mCurrentPhonePrefix)) {
                updatedPhone = updatedPhone.substring(mCurrentPhonePrefix.length());
            }
            updateCountryCode(newCountryCode);

            if (!TextUtils.isEmpty(updatedPhone)) {
                formatPhoneNumber(updatedPhone);
            } else if (mCountryCodeInput.getVisibility() == View.VISIBLE) {
                initPhoneWithPrefix();
            }
        }
    }

    /**
     * Check whether the current phone number is a valid for the current country code
     *
     * @return true if valid
     */
    public boolean isPhoneNumberValidForCountry() {
        return mCurrentPhoneNumber != null && PhoneNumberUtil.getInstance().isValidNumberForRegion(mCurrentPhoneNumber, mCountryCode);
    }

    /**
     * Get the current phone number
     *
     * @return phone number object
     */
    public Phonenumber.PhoneNumber getPhoneNumber() {
        return mCurrentPhoneNumber;
    }

    /**
     * Get the phone number in E164 format
     *
     * @return formatted phone number
     */
    public String getE164PhoneNumber() {
        return mCurrentPhoneNumber == null ? null : PhoneNumberUtils.getE164format(mCurrentPhoneNumber);
    }

    /**
     * Get the country code of the current phone number
     *
     * @return country code (ie. FR, US, etc.)
     */
    public String getCountryCode() {
        // Always extract from phone number object instead of using mCurrentRegionCode just in case
        return mCurrentPhoneNumber == null ? null : PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(mCurrentPhoneNumber.getCountryCode());
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Set the current country code and update the country code field and the prefix
     *
     * @param newCountryCode
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

    /**
     * Init the phone number field with the country prefix (ie. "+33" for country code "FR")
     */
    private void initPhoneWithPrefix() {
        if (!TextUtils.isEmpty(mCurrentPhonePrefix)) {
            mPhoneNumberInput.setText(mCurrentPhonePrefix);
            mPhoneNumberInput.setSelection(mPhoneNumberInput.getText().length());
        }
    }

    /**
     * Format the given string according to the expected format for the current country code
     *
     * @param rawPhoneNumber raw phone number to format
     */
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

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            mCountryCodeInput.setVisibility(View.VISIBLE);
            if (TextUtils.isEmpty(mPhoneNumberInput.getText()) && !TextUtils.isEmpty(mCurrentPhonePrefix)) {
                initPhoneWithPrefix();
            }
        } else {
            // Lost focus, display back the placeholder if field only has the prefix
            if (TextUtils.isEmpty(mPhoneNumberInput.getText()) || TextUtils.equals(mPhoneNumberInput.getText(), mCurrentPhonePrefix)) {
                reset();
            }
        }
    }

}
