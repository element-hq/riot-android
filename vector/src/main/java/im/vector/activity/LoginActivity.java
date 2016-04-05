/*
 * Copyright 2015 OpenMarket Ltd
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

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.util.JsonUtils;

import im.vector.LoginHandler;
import im.vector.Matrix;
import im.vector.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the login screen.
 */
public class LoginActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "LoginActivity";

    static final int ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE = 314;
    static final int FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE = 315;

    // activity modes
    // either the user logs in
    // or creates a new account
    static final int LOGIN_MODE = 0;
    static final int ACCOUNT_CREATION_MODE = 1;

    public static final String LOGIN_PREF = "vector_login";
    public static final String PASSWORD_PREF = "vector_password";

    // saved parameters index

    // login
    private static final String SAVED_LOGIN_EMAIL_ADDRESS = "SAVED_LOGIN_EMAIL_ADDRESS";
    private static final String SAVED_LOGIN_PASSWORD_ADDRESS = "SAVED_LOGIN_PASSWORD_ADDRESS";

    // creation
    private static final String SAVED_CREATION_EMAIL_ADDRESS = "SAVED_CREATION_EMAIL_ADDRESS";
    private static final String SAVED_CREATION_USER_NAME = "SAVED_CREATION_USER_NAME";
    private static final String SAVED_CREATION_PASSWORD1 = "SAVED_CREATION_PASSWORD1";
    private static final String SAVED_CREATION_PASSWORD2 = "SAVED_CREATION_PASSWORD2";
    private static final String SAVED_CREATION_REGISTRATION_RESPONSE = "SAVED_CREATION_REGISTRATION_RESPONSE";

    // mode
    private static final String SAVED_MODE = "SAVED_MODE";

    // servers part
    private static final String SAVED_IS_SERVER_URL_EXPANDED = "SAVED_IS_SERVER_URL_EXPANDED";
    private static final String SAVED_HOME_SERVER_URL = "SAVED_HOME_SERVER_URL";
    private static final String SAVED_IDENTITY_SERVER_URL = "SAVED_IDENTITY_SERVER_URL";

    // activity mode
    private int mMode = LOGIN_MODE;

    // graphical items
    // login button
    private Button mLoginButton;

    // create account button
    private Button mRegisterButton;

    // the login account name
    private TextView mLoginEmailTextView;

    // the login password
    private TextView mLoginPasswordTextView;

    // the creation email
    private TextView mCreationEmailTextView;

    // the creation user name
    private TextView mCreationUsernameTextView;

    // the password 1 name
    private TextView mCreationPassword1TextView;

    // the password 2 name
    private TextView mCreationPassword2TextView;

    // forgot my password
    private TextView mPasswordForgottenTxtView;

    // the home server text
    private EditText mHomeServerText;

    // the identity server text
    private EditText mIdentityServerText;

    // used to display a UI mask on the screen
    private RelativeLayout mLoginMaskView;

    private boolean mIsHomeServerUrlIsDisplayed;
    private View mDisplayHomeServerUrlView;
    private View mHomeServerUrlsLayout;
    private ImageView mExpandImageView;

    String mHomeServerUrl = null;

    // allowed registration response
    private RegistrationFlowResponse mRegistrationResponse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_login);

        // resume the application
        if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            Log.e(LOG_TAG, "Resume the application");
            finish();
            return;
        }

        if (hasCredentials()) {
            Log.e(LOG_TAG, "goToSplash because the credentials are already provided.");
            goToSplash();
            finish();
            return;
        }

        // bind UI widgets
        mLoginMaskView = (RelativeLayout)findViewById(R.id.flow_ui_mask_login);

        // login
        mLoginEmailTextView = (EditText) findViewById(R.id.login_user_name);
        mLoginPasswordTextView = (EditText) findViewById(R.id.login_password);

        // account creation
        mCreationEmailTextView = (EditText) findViewById(R.id.creation_email_address);
        mCreationUsernameTextView = (EditText) findViewById(R.id.creation_your_name);
        mCreationPassword1TextView = (EditText) findViewById(R.id.creation_password1);
        mCreationPassword2TextView = (EditText) findViewById(R.id.creation_password2);

        mPasswordForgottenTxtView = (TextView) findViewById(R.id.login_forgot_password);

        mHomeServerText = (EditText) findViewById(R.id.login_matrix_server_url);
        mIdentityServerText = (EditText) findViewById(R.id.login_identity_url);

        mLoginButton = (Button)findViewById(R.id.button_login);
        mRegisterButton = (Button)findViewById(R.id.button_register);

        mDisplayHomeServerUrlView = findViewById(R.id.display_server_url_layout);
        mHomeServerUrlsLayout =  findViewById(R.id.login_matrix_server_options_layout);
        mExpandImageView = (ImageView)findViewById(R.id.display_server_url_expand_icon);

        if (null != savedInstanceState) {
            mLoginEmailTextView.setText(savedInstanceState.getString(SAVED_LOGIN_EMAIL_ADDRESS));
            mLoginPasswordTextView.setText(savedInstanceState.getString(SAVED_LOGIN_PASSWORD_ADDRESS));
            mIsHomeServerUrlIsDisplayed = savedInstanceState.getBoolean(SAVED_IS_SERVER_URL_EXPANDED);
            mHomeServerText.setText(savedInstanceState.getString(SAVED_HOME_SERVER_URL));
            mIdentityServerText.setText(savedInstanceState.getString(SAVED_IDENTITY_SERVER_URL));

            mCreationEmailTextView.setText(savedInstanceState.getString(SAVED_CREATION_EMAIL_ADDRESS));
            mCreationUsernameTextView.setText(savedInstanceState.getString(SAVED_CREATION_USER_NAME));
            mCreationPassword1TextView.setText(savedInstanceState.getString(SAVED_CREATION_PASSWORD1));
            mCreationPassword2TextView.setText(savedInstanceState.getString(SAVED_CREATION_PASSWORD2));

            mRegistrationResponse = (RegistrationFlowResponse)savedInstanceState.getSerializable(SAVED_CREATION_REGISTRATION_RESPONSE);

            mMode = savedInstanceState.getInt(SAVED_MODE, LOGIN_MODE);
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);

            mLoginEmailTextView.setText(preferences.getString(LOGIN_PREF, ""));
            mLoginPasswordTextView.setText(preferences.getString(PASSWORD_PREF, ""));
        }

        // TODO implement the forgot password
        mPasswordForgottenTxtView.setVisibility(View.GONE);

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = mLoginEmailTextView.getText().toString().trim();
                String password = mLoginPasswordTextView.getText().toString().trim();
                String serverUrl = mHomeServerText.getText().toString().trim();
                String identityServerUrl = mIdentityServerText.getText().toString().trim();
                onLoginClick(serverUrl, identityServerUrl, username, password);
            }
        });

        // account creation handler
        mRegisterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onRegisterClick();
            }
        });

        // home server input validity: if the user taps on the next / done button
        mHomeServerText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {

                    // the user validates the home server url
                    if (!TextUtils.equals(mHomeServerUrl, mHomeServerText.getText().toString())) {
                        mHomeServerUrl = mHomeServerText.getText().toString();

                        mRegistrationResponse = null;
                        checkFlows();
                        return true;
                    }
                }

                return false;
            }
        });

        // home server input validity: when focus changes
        mHomeServerText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    if (!TextUtils.equals(mHomeServerUrl, mHomeServerText.getText().toString())) {
                        mHomeServerUrl = mHomeServerText.getText().toString();
                        mRegistrationResponse = null;

                        checkFlows();
                    }
                }
            }
        });

        // "forgot password?" handler
        mPasswordForgottenTxtView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(LoginActivity.this, "Not implemented..", Toast.LENGTH_SHORT).show();
            }
        });

        mDisplayHomeServerUrlView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mIsHomeServerUrlIsDisplayed = !mIsHomeServerUrlIsDisplayed;
                refreshDisplay();
            }
        });

        refreshDisplay();

        // reset the badge counter
        CommonActivityUtils.updateBadgeCount(this, 0);

        mLoginEmailTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(LOGIN_PREF, mLoginEmailTextView.getText().toString());
                editor.commit();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mLoginPasswordTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putString(PASSWORD_PREF, mLoginPasswordTextView.getText().toString());
                editor.commit();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // retrieve the home server path
        mHomeServerUrl = mHomeServerText.getText().toString();

        // check if the login supports the server flows
        checkFlows();
    }


    private boolean hasCredentials() {
        try {
            return Matrix.getInstance(this).getDefaultSession() != null;
        } catch (Exception e) {
            Log.w(LOG_TAG,"## Exception: "+e.getMessage());
        }

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // getDefaultSession could trigger an exception if the login data are corrupted
                    CommonActivityUtils.logout(LoginActivity.this);
                } catch (Exception e) {
                    Log.w(LOG_TAG, "## Exception: " + e.getMessage());
                }
            }
        });

        return false;
    }

    private void goToSplash() {
        Log.e(LOG_TAG, "Go to splash.");
        startActivity(new Intent(this, SplashActivity.class));
    }

    /**
     * check if the current page is supported by the current implementation
     */
    private void checkFlows() {
        if (mMode == LOGIN_MODE) {
            checkLoginFlows();
        } else {
            checkRegistrationFlows();
        }
    }

    //==============================================================================================================
    // registration management
    //==============================================================================================================

    /**
     * Check if the client supports the registration kind.
     * @param hsConfig the homeserver config
     * @param registrationFlowResponse the response
     */
    private void onRegistrationFlow(HomeserverConnectionConfig hsConfig, RegistrationFlowResponse registrationFlowResponse) {
        setFlowsMaskEnabled(false);
        setLoginButtonsEnabled(true);

        ArrayList<LoginFlow>supportedFlows = new ArrayList<LoginFlow>();

        // supported only m.login.password by now
        for (LoginFlow flow : registrationFlowResponse.flows) {
            boolean isSupported;

            isSupported = TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD, flow.type) || TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_CODE, flow.type);

            // check each stages
            if (!isSupported && (null != flow.stages)) {
                isSupported = true;

                for(String stage : flow.stages) {
                    isSupported &= TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD, stage) ||
                            TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY, stage) ||
                            TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_RECAPTCHA, stage);
                }
            }

            if (isSupported) {
                supportedFlows.add(flow);
            }
        }

        if (supportedFlows.size() > 0) {
            mRegistrationResponse = registrationFlowResponse;
            registrationFlowResponse.flows = supportedFlows;
        } else  {
            String hs = mHomeServerText.getText().toString();
            boolean validHomeServer = false;

            try {
                Uri hsUri = Uri.parse(hs);
                validHomeServer = "http".equals(hsUri.getScheme()) || "https".equals(hsUri.getScheme());
            } catch (Exception e) {
                Log.d(LOG_TAG,"## Exception: "+e.getMessage());
            }

            if (!validHomeServer) {
                Toast.makeText(LoginActivity.this, getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(LoginActivity.this, AccountCreationActivity.class);
            intent.putExtra(AccountCreationActivity.EXTRA_HOME_SERVER_ID, hs);
            startActivityForResult(intent, ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE);
        }
    }

    /**
     * Check the homeserver flows.
     * i.e checks if this registration page is enough to perform a registration.
     * else switch to a fallback page
     */
    private void checkRegistrationFlows() {
        if (null == mRegistrationResponse) {
            try {
                final HomeserverConnectionConfig hsConfig = getHsConfig();
                LoginHandler loginHandler = new LoginHandler();

                // invalid URL
                if (null == hsConfig) {
                    setLoginButtonsEnabled(false);
                } else {
                    setFlowsMaskEnabled(true);

                    loginHandler.getSupportedRegistrationFlows(LoginActivity.this, hsConfig, new SimpleApiCallback<RegistrationFlowResponse>() {
                        @Override
                        public void onSuccess(RegistrationFlowResponse registrationFlowResponse) {
                            onRegistrationFlow(hsConfig, registrationFlowResponse);
                        }

                        private void onError(String errorMessage) {
                            setFlowsMaskEnabled(false);
                            setLoginButtonsEnabled(false);
                            Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            Log.e(LOG_TAG, "Network Error: " + e.getMessage(), e);
                            onError(getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onError(getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            RegistrationFlowResponse registrationFlowResponse = null;

                            // when a response is not completed the server returns an error message
                            if ((null != e.mStatus) && (e.mStatus == 401)) {
                                try {
                                    registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                                } catch (Exception castExcept) {
                                }
                            }

                            if (null != registrationFlowResponse) {
                                onRegistrationFlow(hsConfig, registrationFlowResponse);
                            } else {
                                onError(getString(R.string.login_error_unable_login) + " : " + e.error + "(" + e.errcode + ")");
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                setLoginButtonsEnabled(true);
                setFlowsMaskEnabled(false);
            }
        }
    }


    private void onRegisterClick() {
        // the user switches to another mode
        if (mMode == LOGIN_MODE) {
            mMode = ACCOUNT_CREATION_MODE;
            refreshDisplay();
            return;
        }
    }

    //==============================================================================================================
    // login management
    //==============================================================================================================

    private void onLoginClick(String hsUrlString, String identityUrlString, String username, String password) {
        // the user switches to another mode
        if (mMode != LOGIN_MODE) {
            mMode = LOGIN_MODE;
            refreshDisplay();
            return;
        }

        // --------------------- sanity tests for input values.. ---------------------
        if (!hsUrlString.startsWith("http")) {
            Toast.makeText(this, getString(R.string.login_error_must_start_http), Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, getString(R.string.login_error_invalid_credentials), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!hsUrlString.startsWith("http://") && !hsUrlString.startsWith("https://")) {
            hsUrlString = "https://" + hsUrlString;
        }

        if (!identityUrlString.startsWith("http://") && !identityUrlString.startsWith("https://")) {
            identityUrlString = "https://" + identityUrlString;
        }

        // ---------------------------------------------------------------------------

        Uri hsUrl = Uri.parse(hsUrlString);
        final HomeserverConnectionConfig hsConfig = new HomeserverConnectionConfig(hsUrl);

        hsConfig.setIdentityServerUri(Uri.parse(identityUrlString));

        // disable UI actions
        setFlowsMaskEnabled(true);

        try {
            LoginHandler loginHandler = new LoginHandler();
            loginHandler.login(this, hsConfig, username, password, new SimpleApiCallback<HomeserverConnectionConfig>(this) {
                @Override
                public void onSuccess(HomeserverConnectionConfig c) {
                    setFlowsMaskEnabled(false);
                    goToSplash();
                    LoginActivity.this.finish();
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "Network Error: " + e.getMessage(), e);
                    setFlowsMaskEnabled(false);
                    Toast.makeText(getApplicationContext(), getString(R.string.login_error_network_error), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    setFlowsMaskEnabled(false);
                    String msg = getString(R.string.login_error_unable_login) + " : " + e.getMessage();
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    setFlowsMaskEnabled(false);
                    String msg = getString(R.string.login_error_unable_login) + " : " + e.error + "(" + e.errcode + ")";
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
            setFlowsMaskEnabled(false);
            setLoginButtonsEnabled(true);
        }
    }

    /**
     * Check the homeserver flows.
     * i.e checks if this login page is enough to perform a registration.
     * else switcth to a fallback page
     */
    private void checkLoginFlows() {
        try {
            LoginHandler loginHandler = new LoginHandler();
            final HomeserverConnectionConfig hsConfig = getHsConfig();

            // invalid URL
            if (null == hsConfig) {
                setLoginButtonsEnabled(false);
            } else {
                setFlowsMaskEnabled(true);

                loginHandler.getSupportedLoginFlows(LoginActivity.this, hsConfig, new SimpleApiCallback<List<LoginFlow>>() {
                    @Override
                    public void onSuccess(List<LoginFlow> flows) {
                        setFlowsMaskEnabled(false);
                        setLoginButtonsEnabled(true);
                        boolean isSupported = true;

                        // supported only m.login.password by now
                        for (LoginFlow flow : flows) {
                            isSupported &= TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD, flow.type);
                        }

                        // if not supported, switch to the fallback login
                        if (!isSupported) {
                            Intent intent = new Intent(LoginActivity.this, FallbackLoginActivity.class);
                            intent.putExtra(FallbackLoginActivity.EXTRA_HOME_SERVER_ID, hsConfig.getHomeserverUri().toString());
                            startActivityForResult(intent, FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE);
                        }
                    }

                    private void onError(String errorMessage) {
                        setFlowsMaskEnabled(false);
                        setLoginButtonsEnabled(false);
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "Network Error: " + e.getMessage(), e);
                        onError(getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError(getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onError(getString(R.string.login_error_unable_login) + " : " + e.error + "(" + e.errcode + ")");
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
            setLoginButtonsEnabled(true);
            setFlowsMaskEnabled(false);
        }
    }

    //==============================================================================================================
    // Instance backup
    //==============================================================================================================

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        if (!TextUtils.isEmpty(mLoginEmailTextView.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_LOGIN_EMAIL_ADDRESS, mLoginEmailTextView.getText().toString().trim());
        }

        if (!TextUtils.isEmpty(mLoginPasswordTextView.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_LOGIN_PASSWORD_ADDRESS, mLoginPasswordTextView.getText().toString().trim());
        }

        savedInstanceState.putBoolean(SAVED_IS_SERVER_URL_EXPANDED, mIsHomeServerUrlIsDisplayed);

        if (!TextUtils.isEmpty(mHomeServerText.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_HOME_SERVER_URL, mHomeServerText.getText().toString().trim());
        }

        if (!TextUtils.isEmpty(mCreationEmailTextView.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_CREATION_EMAIL_ADDRESS, mCreationEmailTextView.getText().toString().trim());
        }

        if (!TextUtils.isEmpty(mCreationUsernameTextView.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_CREATION_USER_NAME, mCreationUsernameTextView.getText().toString().trim());
        }

        if (!TextUtils.isEmpty(mCreationPassword1TextView.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_CREATION_PASSWORD1, mCreationPassword1TextView.getText().toString().trim());
        }

        if (!TextUtils.isEmpty(mCreationPassword2TextView.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_CREATION_PASSWORD2, mCreationPassword2TextView.getText().toString().trim());
        }

        if (null != mRegistrationResponse) {
            savedInstanceState.putSerializable(SAVED_CREATION_REGISTRATION_RESPONSE, mRegistrationResponse);
        }

        savedInstanceState.putInt(SAVED_MODE, mMode);
    }

    //==============================================================================================================
    // Display management
    //==============================================================================================================

    /**
     * Refresh the visibility of mHomeServerText
     */
    private void refreshDisplay() {

        // check if the device supported the dedicated mode
        checkFlows();

        // home server
        mHomeServerUrlsLayout.setVisibility(mIsHomeServerUrlIsDisplayed ? View.VISIBLE : View.GONE);
        mExpandImageView.setImageResource(mIsHomeServerUrlIsDisplayed ? R.drawable.ic_material_arrow_drop_down_black : R.drawable.ic_material_arrow_drop_up_black);

        //
        boolean isLoginMode = mMode == LOGIN_MODE;

        View loginLayout = findViewById(R.id.login_inputs_layout);
        View creationLayout = findViewById(R.id.creation_inputs_layout);

        loginLayout.setVisibility(isLoginMode ? View.VISIBLE : View.GONE);
        creationLayout.setVisibility(isLoginMode ? View.GONE : View.VISIBLE);

        mLoginButton.setBackgroundColor(getResources().getColor(isLoginMode ? R.color.vector_green_color : android.R.color.white));
        mLoginButton.setTextColor(getResources().getColor(!isLoginMode ? R.color.vector_green_color : android.R.color.white));

        mRegisterButton.setBackgroundColor(getResources().getColor(!isLoginMode ? R.color.vector_green_color : android.R.color.white));
        mRegisterButton.setTextColor(getResources().getColor(isLoginMode ? R.color.vector_green_color : android.R.color.white));
    }

    /**
     * display a loading screen mask over the login screen
     * @param aIsMaskEnabled true to enable the loading screen, false otherwise
     */
    private void setFlowsMaskEnabled(boolean aIsMaskEnabled) {
        // disable/enable login buttons
        setLoginButtonsEnabled(!aIsMaskEnabled);

        if(null != mLoginMaskView) {
            mLoginMaskView.setVisibility(aIsMaskEnabled ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * @param enabled enabled/disabled the login buttons
     */
    private void setLoginButtonsEnabled(Boolean enabled) {
        mLoginButton.setEnabled(enabled);
        mRegisterButton.setEnabled(enabled);

        mLoginButton.setAlpha(enabled ? 1.0f : 0.5f);
        mRegisterButton.setAlpha(enabled ? 1.0f : 0.5f);
    }

    //==============================================================================================================
    // extracted info
    //==============================================================================================================

    /**
     * @return the homeserver config. null if the url is not valid
     */
    private HomeserverConnectionConfig getHsConfig() {
        String hsUrlString = mHomeServerText.getText().toString();

        if ((null == hsUrlString) || !hsUrlString.startsWith("http") || TextUtils.equals(hsUrlString, "http://") || TextUtils.equals(hsUrlString, "https://")) {
            Toast.makeText(this,getString(R.string.login_error_must_start_http),Toast.LENGTH_SHORT).show();
            return null;
        }

        if(!hsUrlString.startsWith("http://") && !hsUrlString.startsWith("https://")){
            hsUrlString = "https://" + hsUrlString;
        }

        return new HomeserverConnectionConfig(Uri.parse(hsUrlString));
    }

    //==============================================================================================================
    // third party activities
    //==============================================================================================================

    protected void onActivityResult(int requestCode, int resultCode, Intent data)  {
        if ((ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE == requestCode) || (FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE == requestCode)) {
            if (resultCode == RESULT_OK) {
                String homeServer = data.getStringExtra("homeServer");
                String homeServerUrl = data.getStringExtra("homeServerUrl");
                String userId = data.getStringExtra("userId");
                String accessToken = data.getStringExtra("accessToken");

                // build a credential with the provided items
                Credentials credentials = new Credentials();
                credentials.userId = userId;
                credentials.homeServer = homeServer;
                credentials.accessToken = accessToken;

                final HomeserverConnectionConfig hsConfig = new HomeserverConnectionConfig(
                        Uri.parse(homeServerUrl), credentials
                );

                Log.e(LOG_TAG, "Account creation succeeds");

                // let's go...
                MXSession session = Matrix.getInstance(getApplicationContext()).createSession(hsConfig);
                Matrix.getInstance(getApplicationContext()).addSession(session);
                goToSplash();
                LoginActivity.this.finish();
            } else if ((resultCode == RESULT_CANCELED) && (FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE == requestCode)) {
                // reset the home server to let the user writes a valid one.
                mHomeServerText.setText("https://");
                setLoginButtonsEnabled(false);
            }
        }
    }
}
