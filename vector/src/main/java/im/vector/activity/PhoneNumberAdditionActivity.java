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

package im.vector.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.androidsdk.util.Log;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.PhoneNumberUtils;
import im.vector.util.ThemeUtils;

public class PhoneNumberAdditionActivity extends RiotAppCompatActivity implements TextView.OnEditorActionListener, TextWatcher, View.OnClickListener {

    private static final String LOG_TAG = PhoneNumberAdditionActivity.class.getSimpleName();

    private static final String EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID";

    private static final int REQUEST_COUNTRY = 1245;
    private static final int REQUEST_VERIFICATION = 6789;

    private TextInputEditText mCountry;
    private TextInputLayout mCountryLayout;
    private TextInputEditText mPhoneNumber;
    private TextInputLayout mPhoneNumberLayout;
    private View mLoadingView;

    private MXSession mSession;

    // Ex "FR"
    private String mCurrentRegionCode;
    // Ex "+33"
    private String mCurrentPhonePrefix;
    private Phonenumber.PhoneNumber mCurrentPhoneNumber;

    // True when a phone number is submitted
    // Used to prevent user to submit several times in a row
    private boolean mIsSubmittingPhone;

     /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static Intent getIntent(final Context context, final String sessionId) {
        final Intent intent = new Intent(context, PhoneNumberAdditionActivity.class);
        intent.putExtra(EXTRA_MATRIX_ID, sessionId);
        return intent;
    }

    /*
    * *********************************************************************************************
    * Activity lifecycle
    * *********************************************************************************************
    */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.settings_add_phone_number);
        setContentView(R.layout.activity_phone_number_addition);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mCountry = findViewById(R.id.phone_number_country_value);
        mCountryLayout = findViewById(R.id.phone_number_country);
        mPhoneNumber = findViewById(R.id.phone_number_value);
        mPhoneNumberLayout = findViewById(R.id.phone_number);
        mLoadingView = findViewById(R.id.loading_view);

        final Intent intent = getIntent();
        mSession = Matrix.getInstance(this).getSession(intent.getStringExtra(EXTRA_MATRIX_ID));

        if ((null == mSession) || !mSession.isAlive()) {
            finish();
            return;
        }

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsSubmittingPhone = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_phone_number_addition, menu);
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult(RESULT_CANCELED);
                finish();
                return true;
            case R.id.action_add_phone_number:
                submitPhoneNumber();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_COUNTRY:
                    if (data != null && data.hasExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE)) {
                        mCountryLayout.setError(null);
                        mCountryLayout.setErrorEnabled(false);

                        if (TextUtils.isEmpty(mPhoneNumber.getText())) {
                            setCountryCode(data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE));
                            initPhoneWithPrefix();
                        } else {
                            // Clear old prefix from phone before assigning new one
                            String updatedPhone = mPhoneNumber.getText().toString();
                            if (mCurrentPhonePrefix != null && updatedPhone.startsWith(mCurrentPhonePrefix)) {
                                updatedPhone = updatedPhone.substring(mCurrentPhonePrefix.length());
                            }
                            setCountryCode(data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE));

                            if (TextUtils.isEmpty(updatedPhone)) {
                                initPhoneWithPrefix();
                            } else {
                                formatPhoneNumber(updatedPhone);
                            }
                        }
                    }
                    break;
                case REQUEST_VERIFICATION:
                    Intent intent = new Intent();
                    setResult(RESULT_OK, intent);
                    finish();
                    break;
            }
        }
    }

    /*
    * *********************************************************************************************
    * Utils
    * *********************************************************************************************
    */

    private void initViews() {
        setCountryCode(PhoneNumberUtils.getCountryCode(this));

        initPhoneWithPrefix();

        mCountry.setOnClickListener(this);
        mPhoneNumber.setOnEditorActionListener(this);
        mPhoneNumber.addTextChangedListener(this);
    }

    private void setCountryCode(final String newCountryCode) {
        if (!TextUtils.isEmpty(newCountryCode) && !newCountryCode.equals(mCurrentRegionCode)) {
            mCurrentRegionCode = newCountryCode;
            mCountry.setText(PhoneNumberUtils.getHumanCountryCode(mCurrentRegionCode));
            // Update the prefix
            final int prefix = PhoneNumberUtil.getInstance().getCountryCodeForRegion(mCurrentRegionCode);
            if (prefix > 0) {
                mCurrentPhonePrefix = "+" + prefix;
            }
        }
    }

    private void initPhoneWithPrefix() {
        if (!TextUtils.isEmpty(mCurrentPhonePrefix)) {
            mPhoneNumber.setText(mCurrentPhonePrefix);
            mPhoneNumber.setSelection(mPhoneNumber.getText().length());
        }
    }

    private void formatPhoneNumber(final String rawPhoneNumber) {
        if (!TextUtils.isEmpty(mCurrentRegionCode)) {
            try {
                mCurrentPhoneNumber = PhoneNumberUtil.getInstance().parse(rawPhoneNumber.trim(), mCurrentRegionCode);
                final String formattedNumber = PhoneNumberUtil.getInstance().format(mCurrentPhoneNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL);
                if (!TextUtils.equals(formattedNumber, mPhoneNumber.getText())) {
                    // Update field with the formatted number
                    mPhoneNumber.setText(formattedNumber);
                    mPhoneNumber.setSelection(mPhoneNumber.getText().length());
                }
            } catch (NumberParseException e) {
                mCurrentPhoneNumber = null;
            }
        }
    }

    private void submitPhoneNumber() {
        if (mCurrentRegionCode == null) {
            mCountryLayout.setErrorEnabled(true);
            mCountryLayout.setError(getString(R.string.settings_phone_number_country_error));
        } else {
            if (mCurrentPhoneNumber == null || !PhoneNumberUtil.getInstance().isValidNumberForRegion(mCurrentPhoneNumber, mCurrentRegionCode)) {
                mPhoneNumberLayout.setErrorEnabled(true);
                mPhoneNumberLayout.setError(getString(R.string.settings_phone_number_error));
            } else {
                addPhoneNumber(mCurrentPhoneNumber);
            }
        }
    }

    /**
     * Link phone number to account
     *
     * @param phoneNumber
     */
    private void addPhoneNumber(final Phonenumber.PhoneNumber phoneNumber) {
        if (!mIsSubmittingPhone) {
            mIsSubmittingPhone = true;

            mLoadingView.setVisibility(View.VISIBLE);

            final String e164phone = PhoneNumberUtils.getE164format(phoneNumber);
            // Extract from phone number object instead of using mCurrentRegionCode just in case
            final String countryCode = PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(phoneNumber.getCountryCode());
            final ThreePid pid = new ThreePid(e164phone, countryCode, ThreePid.MEDIUM_MSISDN);

            mSession.getMyUser().requestPhoneNumberValidationToken(pid, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    mLoadingView.setVisibility(View.GONE);
                    Intent intent = PhoneNumberVerificationActivity.getIntent(PhoneNumberAdditionActivity.this,
                            mSession.getCredentials().userId, pid);
                    startActivityForResult(intent, REQUEST_VERIFICATION);
                }

                @Override
                public void onNetworkError(Exception e) {
                    onSubmitPhoneError(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (TextUtils.equals(MatrixError.THREEPID_IN_USE, e.errcode)) {
                        onSubmitPhoneError(getString(R.string.account_phone_number_already_used_error));
                    } else {
                        onSubmitPhoneError(e.getLocalizedMessage());
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onSubmitPhoneError(e.getLocalizedMessage());
                }
            });
        } else {
            Log.e(LOG_TAG, "Already submitting");
        }
    }

    /**
     * Handle phone submission error
     *
     * @param errorMessage
     */
    private void onSubmitPhoneError(final String errorMessage) {
        mIsSubmittingPhone = false;
        mLoadingView.setVisibility(View.GONE);
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    /*
    * *********************************************************************************************
    * Listeners
    * *********************************************************************************************
    */

    @Override
    public void onClick(View v) {
        Intent intent = CountryPickerActivity.getIntent(this, true);
        startActivityForResult(intent, REQUEST_COUNTRY);
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE && !isFinishing()) {
            submitPhoneNumber();
            return true;
        }
        return false;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {

    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        formatPhoneNumber(s.toString());
        if (mPhoneNumberLayout.getError() != null) {
            mPhoneNumberLayout.setError(null);
            mPhoneNumberLayout.setErrorEnabled(false);
        }
    }
}
