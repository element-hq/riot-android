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

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.androidsdk.util.Log;

import im.vector.Matrix;
import im.vector.R;
import im.vector.util.ThemeUtils;

public class PhoneNumberVerificationActivity extends RiotAppCompatActivity implements TextView.OnEditorActionListener, TextWatcher {

    private static final String LOG_TAG = PhoneNumberVerificationActivity.class.getSimpleName();

    private static final String EXTRA_MATRIX_ID = "EXTRA_MATRIX_ID";
    private static final String EXTRA_PID = "EXTRA_PID";

    private TextInputEditText mPhoneNumberCode;
    private TextInputLayout mPhoneNumberCodeLayout;

    private MXSession mSession;
    private ThreePid mThreePid;

    // True when a phone number token is submitted
    // Used to prevent user to submit several times in a row
    private boolean mIsSubmittingToken;

     /*
     * *********************************************************************************************
     * Static methods
     * *********************************************************************************************
     */

    public static Intent getIntent(final Context context, final String sessionId, final ThreePid pid) {
        final Intent intent = new Intent(context, PhoneNumberVerificationActivity.class);
        intent.putExtra(EXTRA_MATRIX_ID, sessionId);
        intent.putExtra(EXTRA_PID, pid);
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

        setTitle(R.string.settings_phone_number_verification);
        setContentView(R.layout.activity_phone_number_verification);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mPhoneNumberCode = findViewById(R.id.phone_number_code_value);
        mPhoneNumberCodeLayout = findViewById(R.id.phone_number_code);
        waitingView = findViewById(R.id.loading_view);

        final Intent intent = getIntent();
        mSession = Matrix.getInstance(this).getSession(intent.getStringExtra(EXTRA_MATRIX_ID));

        if ((null == mSession) || !mSession.isAlive()) {
            finish();
            return;
        }

        mThreePid = (ThreePid) intent.getSerializableExtra(EXTRA_PID);

        mPhoneNumberCode.addTextChangedListener(this);
        mPhoneNumberCode.setOnEditorActionListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsSubmittingToken = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_phone_number_verification, menu);
        CommonActivityUtils.tintMenuIcons(menu, ThemeUtils.getColor(this, R.attr.icon_tint_on_dark_action_bar_color));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_verify_phone_number:
                submitCode();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*
    * *********************************************************************************************
    * Utils
    * *********************************************************************************************
    */

    /**
     * Submit code (token) to attach phone number to account
     */
    private void submitCode() {
        if (!mIsSubmittingToken) {
            mIsSubmittingToken = true;
            if (TextUtils.isEmpty(mPhoneNumberCode.getText())) {
                mPhoneNumberCodeLayout.setErrorEnabled(true);
                mPhoneNumberCodeLayout.setError(getString(R.string.settings_phone_number_verification_error_empty_code));
            } else {
                showWaitingView();
                mSession.getThirdPidRestClient()
                        .submitValidationToken(mThreePid.medium, mPhoneNumberCode.getText().toString(), mThreePid.clientSecret, mThreePid.sid, new ApiCallback<Boolean>() {
                            @Override
                            public void onSuccess(Boolean isSuccess) {
                                if (isSuccess) {
                                    // the validation of mail ownership succeed, just resume the registration flow
                                    // next step: just register
                                    Log.e(LOG_TAG, "## submitPhoneNumberValidationToken(): onSuccess() - registerAfterEmailValidations() started");
                                    registerAfterPhoneNumberValidation(mThreePid);
                                } else {
                                    Log.e(LOG_TAG, "## submitPhoneNumberValidationToken(): onSuccess() - failed (success=false)");
                                    onSubmitCodeError(getString(R.string.settings_phone_number_verification_error));
                                }
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                onSubmitCodeError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                onSubmitCodeError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                onSubmitCodeError(e.getLocalizedMessage());
                            }
                        });
            }

        }
    }

    private void registerAfterPhoneNumberValidation(final ThreePid pid) {
        mSession.getMyUser().add3Pid(pid, true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Intent intent = new Intent();
                setResult(RESULT_OK, intent);
                finish();
            }

            @Override
            public void onNetworkError(Exception e) {
                onSubmitCodeError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onSubmitCodeError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onSubmitCodeError(e.getLocalizedMessage());
            }
        });
    }

    private void onSubmitCodeError(final String errorMessage) {
        mIsSubmittingToken = false;
        stopWaitingView();
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    /*
    * *********************************************************************************************
    * Listeners
    * *********************************************************************************************
    */

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE && !isFinishing()) {
            submitCode();
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
        if (mPhoneNumberCodeLayout.getError() != null) {
            mPhoneNumberCodeLayout.setError(null);
            mPhoneNumberCodeLayout.setErrorEnabled(false);
        }
    }
}
