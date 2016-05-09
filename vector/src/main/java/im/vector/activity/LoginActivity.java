/*
 * Copyright 2016 OpenMarket Ltd
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

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.util.JsonUtils;

import im.vector.LoginHandler;
import im.vector.Matrix;
import im.vector.R;
import im.vector.receiver.VectorRegistrationReceiver;
import im.vector.receiver.VectorUniversalLinkReceiver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Displays the login screen.
 */
public class LoginActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "LoginActivity";

    static final int ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE = 314;
    static final int FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE = 315;
    static final int CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE = 316;

    private final static int REGISTER_POLLING_PERIOD = 10 * 1000;

    // activity modes
    // either the user logs in
    // or creates a new account
    static final int MODE_UNKNOWN = 0;
    static final int MODE_LOGIN = 1;
    static final int MODE_ACCOUNT_CREATION = 2;

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

    // mail validation
    public static final String BROADCAST_ACTION_MAIL_VALIDATION = "im.vector.activity.BROADCAST_ACTION_MAIL_VALIDATION";
    public static final String EXTRA_IS_STOP_REQUIRED = "EXTRA_IS_STOP_REQUIRED";
    public static final String KEY_SUBMIT_TOKEN_SUCCESS = "success";

    // activity mode
    private int mMode = MODE_LOGIN;

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

    private TextView mProgressTextView;

    private View mMainLayout;

    private boolean mIsHomeServerUrlIsDisplayed;
    private View mDisplayHomeServerUrlView;
    private View mHomeServerUrlsLayout;
    private ImageView mExpandImageView;

    private Parcelable mUniversalLinkUri;

    String mHomeServerUrl = null;
    String mIdentityServerUrl = null;

    // allowed registration response
    private RegistrationFlowResponse mRegistrationResponse;

    // login handler
    private LoginHandler mLoginHandler = new LoginHandler();

    // save the config because trust a certificate is asynchronous.
    private HomeserverConnectionConfig mHomeserverConnectionConfig;

    // next link parameters
    private HashMap<String, String> mEmailValidationExtraParams;

    // the next link parameters were not managed
    private boolean mIsMailValidationPending;
    private boolean mIsUserNameAvailable;

    private Runnable mRegisterPollingRunnable;
    private Handler mHandler;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(LOG_TAG, "## onDestroy(): IN");

        // ignore any server response when the acitity is destroyed
        mMode = MODE_UNKNOWN;
        mEmailValidationExtraParams = null;
    }

    /**
     * Used in the mail validation flow.
     * THis method is called when the LoginActivity is set to foreground due
     * to a {@link #startActivity(Intent)} where the flags Intent.FLAG_ACTIVITY_CLEAR_TOP and Intent.FLAG_ACTIVITY_SINGLE_TOP}
     * are set (see: {@link VectorRegistrationReceiver}).
     * @param aIntent new intent
     */
    @Override
    protected void onNewIntent(Intent aIntent) {
        super.onNewIntent(aIntent);
        Log.d(LOG_TAG, "## onNewIntent(): IN ");

        Bundle receivedBundle;

        if(null ==aIntent){
            Log.w(LOG_TAG, "## onNewIntent(): Unexpected value - aIntent=null ");
        } else if(null == (receivedBundle = aIntent.getExtras())){
            Log.w(LOG_TAG, "## onNewIntent(): Unexpected value - extras are missing");
        } else if (receivedBundle.containsKey(VectorRegistrationReceiver.EXTRA_EMAIL_VALIDATION_PARAMS)) {
            Log.d(LOG_TAG, "## onNewIntent() Login activity started by email verification for registration");

            if(processEmailValidationExtras(receivedBundle)){
                checkIfMailValidationPending();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "## onCreate(): IN");
        setContentView(R.layout.activity_vector_login);

        // warn that the application has started.
        CommonActivityUtils.onApplicationStarted(this);

        // already registered
        if (hasCredentials()) {
            Log.e(LOG_TAG, "## onCreate(): goToSplash because the credentials are already provided.");
            goToSplash();
            finish();
            return;
        }

        Bundle receivedBundle = getIntent().getExtras();

        // resume the application
        if (null != receivedBundle) {
            if (receivedBundle.containsKey(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                mUniversalLinkUri = receivedBundle.getParcelable(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
                Log.d(LOG_TAG, "## onCreate() Login activity started by universal link");
                // activity has been launched from an universal link
            } else if (receivedBundle.containsKey(VectorRegistrationReceiver.EXTRA_EMAIL_VALIDATION_PARAMS)) {
                Log.d(LOG_TAG, "## onCreate() Login activity started by email verification for registration");
                processEmailValidationExtras(receivedBundle);
            }
        } else if ((getIntent().getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != 0) {
            Log.e(LOG_TAG, "## onCreate(): Resume the application");
            finish();
            return;
        }

        // bind UI widgets
        mLoginMaskView = (RelativeLayout) findViewById(R.id.flow_ui_mask_login);

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

        mLoginButton = (Button) findViewById(R.id.button_login);
        mRegisterButton = (Button) findViewById(R.id.button_register);

        mDisplayHomeServerUrlView = findViewById(R.id.display_server_url_layout);
        mHomeServerUrlsLayout = findViewById(R.id.login_matrix_server_options_layout);
        mExpandImageView = (ImageView) findViewById(R.id.display_server_url_expand_icon);

        mProgressTextView = (TextView) findViewById(R.id.flow_progress_message_textview);

        mMainLayout = findViewById(R.id.main_input_layout);

        if (null != savedInstanceState) {
            restoreSavedData(savedInstanceState);
        } else {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(LoginActivity.this);

            mLoginEmailTextView.setText(preferences.getString(LOGIN_PREF, ""));
            mLoginPasswordTextView.setText(preferences.getString(PASSWORD_PREF, ""));
        }

        // TODO implement the forgot password
        mPasswordForgottenTxtView.setVisibility(View.GONE);

        // trap the UI events
        mLoginMaskView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });

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
                mIsUserNameAvailable = false;
                onRegisterClick(true);
            }
        });

        // home server input validity: if the user taps on the next / done button
        mHomeServerText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onHomeserverUrlUpdate();
                    return true;
                }

                return false;
            }
        });

        // home server input validity: when focus changes
        mHomeServerText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    onHomeserverUrlUpdate();
                }
            }
        });

        // identity server input validity: if the user taps on the next / done button
        mIdentityServerText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onIdentityserverUrlUpdate();
                    return true;
                }

                return false;
            }
        });

        // identity server input validity: when focus changes
        mIdentityServerText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    onIdentityserverUrlUpdate();
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

        // set the handler used by the register to poll the server response
        mHandler = new Handler(getMainLooper());
    }

    /**
     * Check if the home server url has been updated
     */
    private void onHomeserverUrlUpdate() {
        if (!TextUtils.equals(mHomeServerUrl, mHomeServerText.getText().toString())) {
            mHomeServerUrl = mHomeServerText.getText().toString();
            mRegistrationResponse = null;

            // invalidate the current homeserver config
            mHomeserverConnectionConfig = null;
            // the account creation is not always supported so ensure that the dedicated button is always displayed.
            mRegisterButton.setVisibility(View.VISIBLE);

            checkFlows();
        }
    }

    /**
     * Check if the home server url has been updated
     */
    private void onIdentityserverUrlUpdate() {
        if (!TextUtils.equals(mIdentityServerUrl, mIdentityServerText.getText().toString())) {
            mIdentityServerUrl = mIdentityServerText.getText().toString();
            mRegistrationResponse = null;

            // invalidate the current homeserver config
            mHomeserverConnectionConfig = null;
            // the account creation is not always supported so ensure that the dedicated button is always displayed.
            mRegisterButton.setVisibility(View.VISIBLE);
        }
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "## onResume(): IN");

        // retrieve the home server path
        mHomeServerUrl = mHomeServerText.getText().toString();
        mIdentityServerUrl = mIdentityServerText.getText().toString();

        // check if the login supports the server flows
        checkFlows();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((MODE_ACCOUNT_CREATION == mMode) && (null != mRegistrationResponse)) {
                // cancel the registration flow
                mEmailValidationExtraParams = null;
                mRegistrationResponse = null;
                onRegistrationEnd();
                setFlowsMaskEnabled(false);

                mMode = MODE_LOGIN;
                refreshDisplay();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * @return true if some credentials have been saved.
     */
    private boolean hasCredentials() {
        try {
            return Matrix.getInstance(this).getDefaultSession() != null;
        } catch (Exception e) {
            Log.w(LOG_TAG, "## Exception: " + e.getMessage());
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

    /**
     * Some sessions have been registred, skip the login process.
     */
    private void goToSplash() {
        Log.w(LOG_TAG, "## gotoSplash(): Go to splash.");

        Log.w("LOGIN", "## goToSplash() start SplashActivity");
        Intent intent = new Intent(this, SplashActivity.class);

        if (null != mUniversalLinkUri) {
            intent.putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, mUniversalLinkUri);
        }

        startActivity(intent);
    }

    /**
     * check if the current page is supported by the current implementation
     */
    private void checkFlows() {
        if (mMode == MODE_LOGIN) {
            checkLoginFlows();
        } else {
            checkRegistrationFlows();
        }
    }

    //==============================================================================================================
    // registration management
    //==============================================================================================================

    /**
     * Error case Management
     * @param matrixError the matrix error
     */
    private void onFailureDuringAuthRequest(MatrixError matrixError) {
        String message = matrixError.getLocalizedMessage();

        Log.d(LOG_TAG, "onFailureDuringAuthRequest " + message);

        setFlowsMaskEnabled(false);

        // detect if it is a Matrix SDK issue
        String errCode = matrixError.errcode;

        if (null != errCode) {
            if (TextUtils.equals(errCode, MatrixError.FORBIDDEN)) {
                message = getResources().getString(R.string.login_error_forbidden);
            } else if (TextUtils.equals(errCode, MatrixError.UNKNOWN_TOKEN)) {
                message = getResources().getString(R.string.login_error_unknown_token);
            } else if (TextUtils.equals(errCode, MatrixError.BAD_JSON)) {
                message = getResources().getString(R.string.login_error_bad_json);
            } else if (TextUtils.equals(errCode, MatrixError.NOT_JSON)) {
                message = getResources().getString(R.string.login_error_not_json);
            } else if (TextUtils.equals(errCode, MatrixError.LIMIT_EXCEEDED)) {
                message = getResources().getString(R.string.login_error_limit_exceeded);
            } else if (TextUtils.equals(errCode, MatrixError.USER_IN_USE)) {
                message = getResources().getString(R.string.login_error_user_in_use);
            } else if (TextUtils.equals(errCode, MatrixError.LOGIN_EMAIL_URL_NOT_YET)) {
                message = getResources().getString(R.string.login_error_login_email_not_yet);
            }
        }

        Log.e(LOG_TAG, "## onFailureDuringAuthRequest(): Msg= \""+message+"\"");
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    /**
     * Parse the given bundle to check if it contains the email verification extra.
     * If yes, it initializes the LoginActivity to start in registration mode to finalize a registration
     * process that is in progress. This is mainly used when the LoginActivity
     * is triggered from the {@link VectorRegistrationReceiver}.
     * @param aRegistrationBundle bundle to be parsed
     * @return true operation succeed, false otherwise
     */
    private boolean processEmailValidationExtras(Bundle aRegistrationBundle){
        boolean retCode =false;

        Log.d(LOG_TAG, "## processEmailValidationExtras() IN");

        if(null != aRegistrationBundle) {
            mEmailValidationExtraParams = (HashMap<String, String>) aRegistrationBundle.getSerializable(VectorRegistrationReceiver.EXTRA_EMAIL_VALIDATION_PARAMS);

            if (null != mEmailValidationExtraParams) {
                // login was started in email validation mode
                mIsMailValidationPending = true;
                mMode = MODE_ACCOUNT_CREATION;
                Matrix.getInstance(this).clearSessions(this, true);
                retCode = true;
            }
        } else {
            Log.w(LOG_TAG, "## processEmailValidationExtras(): Bundle is missing - aRegistrationBundle=null");
        }
        Log.d(LOG_TAG, "## processEmailValidationExtras() OUT - reCode="+retCode);
        return retCode;
    }


    /**
     * Perform an email validation for a registration flow. One account has been created where
     * a mail was provided. To validate the email ownership a MX submitToken REST api call must be performed.
     * @param aMapParams map containing the parameters
     */
    private void startEmailOwnershipValidation(HashMap<String, String> aMapParams) {
        Log.i(LOG_TAG, "## startEmailOwnershipValidation(): IN aMapParams="+aMapParams);
        if(null != aMapParams) {
            // display waiting UI..
            setFlowsMaskEnabled(true);

            // display wait screen with no text (same as iOS) for now..
            onRegistrationStart("");

            // set register mode
            mMode = MODE_ACCOUNT_CREATION;

            String token = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_TOKEN);
            String clientSecret = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_CLIENT_SECRET);
            String identityServerSessId = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_IDENTITY_SERVER_SESSION_ID);
            String sessionId = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_SESSION_ID);
            String homeServer = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_HOME_SERVER_URL);
            String identityServer = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_IDENTITY_SERVER_URL);

            submitEmailToken(token, clientSecret, identityServerSessId, sessionId, homeServer, identityServer);
        } else {
            Log.i(LOG_TAG, "## startEmailOwnershipValidation(): skipped");
        }
    }

    /**
     * Used to resume the registration process when it is waiting for the mail validation.
     *
     * @param aClientSecret   client secret
     * @param aSid            identity server session ID
     * @param aIdentityServer identity server url
     * @param aSessionId      session ID
     * @param aHomeServer     home server url
     */
    private void submitEmailToken(final String aToken, final String aClientSecret, final String aSid, final String aSessionId, final String aHomeServer, final String aIdentityServer) {
        final HomeserverConnectionConfig homeServerConfig = mHomeserverConnectionConfig = new HomeserverConnectionConfig(Uri.parse(aHomeServer), Uri.parse(aIdentityServer), null, new ArrayList<Fingerprint>(), false);
        Log.i(LOG_TAG, "## submitEmailToken(): IN");

        if (mMode == MODE_ACCOUNT_CREATION) {
            Log.i(LOG_TAG, "## submitEmailToken(): calling submitEmailTokenValidation()..");
            mLoginHandler.submitEmailTokenValidation(getApplicationContext(), homeServerConfig, aToken, aClientSecret, aSid, new ApiCallback<Map<String,Object>>() {
                private void errorHandler(String errorMessage) {
                    Log.w(LOG_TAG, "## submitEmailToken(): errorHandler().");
                    setFlowsMaskEnabled(false);
                    setLoginButtonsEnabled(false);
                    onRegistrationEnd();
                    Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSuccess(Map<String,Object> mapResp) {
                    if(null != mapResp) {
                        Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onSuccess() - respObject=" + mapResp.toString());

                        Boolean status = (Boolean)mapResp.get(KEY_SUBMIT_TOKEN_SUCCESS);
                        if (null != status) {
                            if (status.booleanValue()) {
                                // the validation of mail ownership succeed, just resume the registration flow
                                // next step: just register
                                Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onSuccess() - registerAfterEmailValidations() started");
                                registerAfterEmailValidation(aClientSecret, aSid, aIdentityServer, aSessionId);
                            } else {
                                Log.w(LoginActivity.LOG_TAG, "## submitEmailToken(): onSuccess() - failed (success=false)");
                                errorHandler(getString(R.string.login_error_unable_register_mail_ownership));
                            }
                        } else {
                            Log.w(LoginActivity.LOG_TAG, "## submitEmailToken(): onSuccess() - failded (parameter missing)");
                        }
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onNetworkError() Msg=" + e.getLocalizedMessage());
                    errorHandler(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onMatrixError() Msg=" + e.getLocalizedMessage());
                    errorHandler(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onUnexpectedError() Msg=" + e.getLocalizedMessage());
                    errorHandler(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                }
            });
        }
    }

    /**
     * Register step after a mail validation.
     * In the registration flow after a mail was validated {@see #startEmailOwnershipValidation},
     * this register request must be performed to reach the next registration step stage.
     *
     * @param aClientSecret   client secret
     * @param aSid            identity server session ID
     * @param aIdentityServer identity server url
     * @param aSessionId      session ID
     */
    private void registerAfterEmailValidation(String aClientSecret, String aSid, String aIdentityServer, String aSessionId) {
        mMode = MODE_ACCOUNT_CREATION;
        HashMap<String, Object> authParams = new HashMap<String, Object>();
        HashMap<String, Object> thirdPartyIdsCredentialsAuth = new HashMap<String, Object>();
        RegistrationParams registrationParams = new RegistrationParams();

        Log.w(LoginActivity.LOG_TAG, "## registerAfterEmailValidation(): IN aSessionId="+aSessionId);
        // set session
        if(null != mRegistrationResponse) {
            Log.w(LoginActivity.LOG_TAG, "## registerAfterEmailValidation(): update session ID, old value="+mRegistrationResponse.session);
            mRegistrationResponse.session = aSessionId;
        }

        // remove URL scheme
        aIdentityServer = CommonActivityUtils.removeUrlScheme(aIdentityServer);

        thirdPartyIdsCredentialsAuth.put("client_secret", aClientSecret);
        thirdPartyIdsCredentialsAuth.put("id_server", aIdentityServer);
        thirdPartyIdsCredentialsAuth.put("sid", aSid);

        authParams.put("session", aSessionId);
        authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
        authParams.put("threepid_creds", thirdPartyIdsCredentialsAuth);

        registrationParams.auth = authParams;
        // Note: username, password and bind_email must not be set in registrationParams

        Log.d(LOG_TAG, "## registerAfterEmailValidation(): start register() with authParams=" + authParams);
        setFlowsMaskEnabled(true);
        register(registrationParams);
    }

    /**
     * Check the homeserver flows.
     * i.e checks if this registration page is enough to perform a registration.
     * else switch to a fallback page
     */
    private void register(final RegistrationParams params) {

        Log.d(LOG_TAG,"## register(): IN");

        // should not check login flows
        if (mMode != MODE_ACCOUNT_CREATION) {
            Log.d(LOG_TAG,"## register(): exit1");
            return;
        }
        if (null != mRegistrationResponse) {
            try {
                final HomeserverConnectionConfig hsConfig = getHsConfig();

                // invalid URL
                if (null == hsConfig) {
                    setLoginButtonsEnabled(false);
                } else {

                    final String fSession = mRegistrationResponse.session;

                    mLoginHandler.register(LoginActivity.this, hsConfig, params, new SimpleApiCallback<HomeserverConnectionConfig>()
                    {
                        @Override
                        public void onSuccess(HomeserverConnectionConfig homeserverConnectionConfig) {
                            if ((mMode == MODE_ACCOUNT_CREATION) && TextUtils.equals(fSession, getRegistrationSession())) {
                                setFlowsMaskEnabled(false);
                                goToSplash();
                                LoginActivity.this.finish();
                            }
                        }

                        private void onError (String errorMessage){
                            if ((mMode == MODE_ACCOUNT_CREATION) && TextUtils.equals(fSession, getRegistrationSession())) {
                                setFlowsMaskEnabled(false);
                                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onNetworkError (Exception e){
                            Log.e(LOG_TAG, "Network Error: " + e.getMessage(), e);
                            onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                        }

                        @Override
                        public void onUnexpectedError (Exception e){
                            onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                        }

                        @Override
                        public void onMatrixError (MatrixError e){
                            if ((mMode == MODE_ACCOUNT_CREATION) && TextUtils.equals(fSession, getRegistrationSession())) {
                                Log.d(LOG_TAG, "## register(): onMatrixError - Msg="+e.mErrorBodyAsString);
                                // waiting for email case
                                if (TextUtils.equals(e.errcode, MatrixError.UNAUTHORIZED)) {
                                    // refresh the messages
                                    onRegistrationStart(getResources().getString(R.string.auth_email_validation_message));

                                    // check if the next link parameters have been received
                                    if (null != mEmailValidationExtraParams) {
                                        Log.d(LOG_TAG, "## register(): Received UNAUTHORIZED - the next link parameters were received, stop polling on register()");
                                        mRegisterPollingRunnable = null;
                                    } else {
                                        Log.d(LOG_TAG, "## register(): Received UNAUTHORIZED - Wait for validation..");
                                        mRegisterPollingRunnable = new Runnable() {
                                            @Override
                                            public void run() {
                                                // check if the next link was not received
                                                if ((MODE_ACCOUNT_CREATION == mMode) && (null == mEmailValidationExtraParams)) {
                                                    register(params);
                                                }
                                            }
                                        };

                                        mHandler.postDelayed(mRegisterPollingRunnable, REGISTER_POLLING_PERIOD);
                                    }
                                } else {
                                    Log.d(LOG_TAG, "## register(): The registration continues");

                                    // reset polling handler
                                    mRegisterPollingRunnable = null;

                                    // detect if a parameter is expected
                                    RegistrationFlowResponse registrationFlowResponse = null;

                                    // when a response is not completed the server returns an error message
                                    if ((null != e.mStatus) && (e.mStatus == 401)) {
                                        try {
                                            registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                                        } catch (Exception castExcept) {
                                            Log.w(LOG_TAG, "## register(): Received status 401 - Exception - JsonUtils.toRegistrationFlowResponse()");
                                        }
                                    } else {
                                        Log.d(LOG_TAG, "## register(): Received not expected status 401 ="+e.mStatus);
                                    }

                                    // check if the server response can be casted
                                    if (null != registrationFlowResponse) {
                                        Log.d(LOG_TAG, "## register(): Received status 401 - Registration continues to next onRegisterClick() for captcha..");
                                        mRegistrationResponse = registrationFlowResponse;
                                        // next step
                                        onRegisterClick(false);
                                    } else {
                                        onRegistrationEnd();
                                        onFailureDuringAuthRequest(e);
                                    }
                                }
                            } else {
                                Log.d(LOG_TAG, "## register(): onMatrixError - received session is different, mMode="+mMode+"(MODE_ACCOUNT_CREATION="+MODE_ACCOUNT_CREATION+")");
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                onRegistrationEnd();
                setFlowsMaskEnabled(false);
            }
        } else {
            Log.e(LOG_TAG, "## register(): Exit  - mRegistrationResponse=null");
        }
    }

    /**
     * Perform a register operation to check if the user name provided for the registration
     * is available (ie server must not return M_USER_IN_USE)
     *
     * @param params registration params
     */
    private void checkNameAvailability(final RegistrationParams params) {
        Log.d(LOG_TAG,"## checkNameAvailability(): IN");

        // should not check login flows
        if (mMode != MODE_ACCOUNT_CREATION) {
            Log.d(LOG_TAG,"## checkNameAvailability(): exit1");
            return;
        }
        if (null != mRegistrationResponse) {
            try {
                final HomeserverConnectionConfig hsConfig = getHsConfig();

                // invalid URL
                if (null == hsConfig) {
                    Log.d(LOG_TAG,"## checkNameAvailability(): exit2");
                    return;
                } else {
                    final String fSession = mRegistrationResponse.session;

                    // display wait screen with no text (same as iOS) for now..
                    onRegistrationStart("");

                    mLoginHandler.register(LoginActivity.this, hsConfig, params, new SimpleApiCallback<HomeserverConnectionConfig>() {
                        // common local error handler
                        private void onError (String errorMessage){
                            if ((mMode == MODE_ACCOUNT_CREATION) && TextUtils.equals(fSession, getRegistrationSession())) {
                                setFlowsMaskEnabled(false);
                                setLoginButtonsEnabled(false);
                                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onSuccess(HomeserverConnectionConfig homeserverConnectionConfig) {
                            // should not happen..  given the request, only a 401 status is expected from the server
                            if ((mMode == MODE_ACCOUNT_CREATION) && TextUtils.equals(fSession, getRegistrationSession())) {
                                onRegisterClick(false);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e){
                            Log.e(LOG_TAG, "## checkNameAvailability(): Network Error: " + e.getMessage(), e);
                            onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e){
                            onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                        }

                        @Override
                        public void onMatrixError(MatrixError aErrorParam){
                            if ((mMode == MODE_ACCOUNT_CREATION)/* && TextUtils.equals(fSession, getRegistrationSession())*/) {
                                Log.d(LOG_TAG, "## checkNameAvailability(): onMatrixError Response="+aErrorParam.mErrorBodyAsString);

                                if (TextUtils.equals(aErrorParam.errcode, MatrixError.USER_IN_USE)) {
                                    // user name is already taken, the registration process stops here (new user name should be provided)
                                    // ex: {"errcode":"M_USER_IN_USE","error":"User ID already taken."}
                                    Log.d(LOG_TAG, "## checkNameAvailability(): user name is used");
                                    onRegistrationEnd();
                                    setFlowsMaskEnabled(false);
                                    onFailureDuringAuthRequest(aErrorParam);
                                } else {
                                    Log.d(LOG_TAG, "## checkNameAvailability(): The registration continues");
                                    RegistrationFlowResponse registrationFlowResponse = null;

                                    // expected status code is 401
                                    if ((null != aErrorParam.mStatus) && (aErrorParam.mStatus == 401)) {
                                        try {
                                            registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(aErrorParam.mErrorBodyAsString);
                                        } catch (Exception castExcept) {
                                            Log.w(LOG_TAG, "## checkNameAvailability(): Received status 401 - Exception - JsonUtils.toRegistrationFlowResponse()");
                                        }
                                    } else {
                                        Log.d(LOG_TAG, "## checkNameAvailability(): Received not expected status 401 ="+aErrorParam.mStatus);
                                    }

                                    // check if the server response can be casted
                                    if (null != registrationFlowResponse) {
                                        Log.d(LOG_TAG, "## checkNameAvailability(): Received status 401 - Registration continues to next onRegisterClick()");

                                        // update with the new registration value and go to next step
                                        mIsUserNameAvailable = true;
                                        mRegistrationResponse = registrationFlowResponse;
                                        onRegisterClick(false);
                                    } else {
                                        onRegistrationEnd();
                                        onFailureDuringAuthRequest(aErrorParam);
                                    }
                                }
                            } else {
                                Log.d(LOG_TAG, "## checkNameAvailability(): Received status 401 - Registration continues to next onRegisterClick()");
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                onRegistrationEnd();
                setFlowsMaskEnabled(false);
            }
        } else {
            Log.e(LOG_TAG, "## checkNameAvailability(): Exit  - mRegistrationResponse=null");
        }
    }

    /**
     * @return true if the email identity is completed
     */
    private boolean isEmailIdentityFlowCompleted() {
        // sanity checks
        if ((null != mRegistrationResponse) && (null != mRegistrationResponse.completed)) {
            return mRegistrationResponse.completed.indexOf(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY) >= 0;
        }

        return false;
    }

    /**
     * return true if a captcha flow is required
     */
    private boolean isRecaptchaFlowRequired() {
        // sanity checks
        if ((null != mRegistrationResponse) && (null != mRegistrationResponse.flows)) {
            for (LoginFlow loginFlow : mRegistrationResponse.flows){
                if ((loginFlow.stages.indexOf(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_RECAPTCHA) < 0) && !TextUtils.equals(loginFlow.type, LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_RECAPTCHA)) {
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    /**
     * Check if the client supports the registration kind.
     *
     * @param hsConfig                 the homeserver config
     * @param registrationFlowResponse the response
     */
    private void onRegistrationFlow(HomeserverConnectionConfig hsConfig, RegistrationFlowResponse registrationFlowResponse) {
        setFlowsMaskEnabled(false);
        setLoginButtonsEnabled(true);

        ArrayList<LoginFlow> supportedFlows = new ArrayList<LoginFlow>();

        // supported only m.login.password by now
        for (LoginFlow flow : registrationFlowResponse.flows) {
            boolean isSupported;

            isSupported = TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD, flow.type) || TextUtils.equals(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_CODE, flow.type);

            // check each stages
            if (!isSupported && (null != flow.stages)) {
                isSupported = true;

                for (String stage : flow.stages) {
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
            Log.w(LOG_TAG, "## onRegistrationFlow(): mRegistrationResponse updated");
            mRegistrationResponse = registrationFlowResponse;
            registrationFlowResponse.flows = supportedFlows;
        } else {
            String hs = mHomeServerText.getText().toString();
            boolean validHomeServer = false;

            try {
                Uri hsUri = Uri.parse(hs);
                validHomeServer = "http".equals(hsUri.getScheme()) || "https".equals(hsUri.getScheme());
            } catch (Exception e) {
                Log.d(LOG_TAG, "## Exception: " + e.getMessage());
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
     * Start a mail validation if required.
     * Note the mail validation processing can only be started if
     * {@link #onRegistrationFlow(HomeserverConnectionConfig, RegistrationFlowResponse)} has
     * been at least started once.
     *
     */
    private void checkIfMailValidationPending(){
        Log.d(LOG_TAG,"## checkIfMailValidationPending(): mIsMailValidationPending="+mIsMailValidationPending);

        if(null == mRegistrationResponse){
            Log.w(LOG_TAG, "## checkIfMailValidationPending(): pending mail validation delayed (mRegistrationResponse=null)");
        }
        else if(mIsMailValidationPending) {
            mIsMailValidationPending = false;

            // remove the pending polling register if any
            if (null != mRegisterPollingRunnable) {
                mHandler.removeCallbacks(mRegisterPollingRunnable);
                Log.w(LOG_TAG, "## checkIfMailValidationPending(): pending register() removed from handler");
            } else{
                Log.w(LOG_TAG, "## checkIfMailValidationPending(): no registering polling on M_UNAUTHORIZED");
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(null != mEmailValidationExtraParams) {
                        startEmailOwnershipValidation(mEmailValidationExtraParams);
                    }
                }
            });
        } else {
            Log.d(LOG_TAG,"## checkIfMailValidationPending(): pending mail validation not started");
        }
    }

    /**
     * Check the homeserver flows.
     * i.e checks if this registration page is enough to perform a registration.
     * else switch to a fallback page
     */
    private void checkRegistrationFlows() {
        Log.d(LOG_TAG,"## checkRegistrationFlows(): IN");
        // should not check login flows
        if (mMode != MODE_ACCOUNT_CREATION) {
            return;
        }

        if (null == mRegistrationResponse) {
            try {
                final HomeserverConnectionConfig hsConfig = getHsConfig();

                // invalid URL
                if (null == hsConfig) {
                    setLoginButtonsEnabled(false);
                } else {
                    setFlowsMaskEnabled(true);

                    mLoginHandler.getSupportedRegistrationFlows(LoginActivity.this, hsConfig, new SimpleApiCallback<HomeserverConnectionConfig>() {
                        @Override
                        public void onSuccess(HomeserverConnectionConfig homeserverConnectionConfig) {
                            // should never be called
                        }

                        private void onError(String errorMessage) {
                            // should not check login flows
                            if (mMode == MODE_ACCOUNT_CREATION) {
                                setFlowsMaskEnabled(false);
                                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            if (mMode == MODE_ACCOUNT_CREATION) {
                                Log.e(LOG_TAG, "Network Error: " + e.getMessage(), e);
                                onError(getString(R.string.login_error_registration_network_error) + " : " + e.getLocalizedMessage());
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            if (mMode == MODE_ACCOUNT_CREATION) {
                                onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                            }
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            if (mMode == MODE_ACCOUNT_CREATION) {
                                Log.d(LOG_TAG,"## checkRegistrationFlows(): onMatrixError - Resp="+e.mErrorBodyAsString);
                                RegistrationFlowResponse registrationFlowResponse = null;

                                // when a response is not completed the server returns an error message
                                if (null != e.mStatus) {
                                    if (e.mStatus == 401) {
                                        try {
                                            registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                                        } catch (Exception castExcept) {
                                        }
                                    } else if (e.mStatus == 403) {
                                        // not supported by the server
                                        mRegisterButton.setVisibility(View.GONE);
                                        mMode = MODE_LOGIN;
                                        refreshDisplay();
                                    }
                                }

                                if (null != registrationFlowResponse) {
                                    onRegistrationFlow(hsConfig, registrationFlowResponse);
                                } else {
                                    onFailureDuringAuthRequest(e);
                                }

                                // start Login due to a pending email validation
                                checkIfMailValidationPending();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(), getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                setFlowsMaskEnabled(false);
            }
        }
    }

    /**
     * The registration process starts
     * @param text the text helper
     */
    private void onRegistrationStart(String text) {
        mMainLayout.setVisibility(View.GONE);
        mProgressTextView.setVisibility(View.VISIBLE);
        mProgressTextView.setText(text);
    }

    /**
     * The registration process stops.
     */
    private void onRegistrationEnd() {
        mMainLayout.setVisibility(View.VISIBLE);
        mProgressTextView.setVisibility(View.GONE);
    }

    /**
     * @return the registration session
     */
    private String getRegistrationSession() {
        if (null != mRegistrationResponse) {
            return mRegistrationResponse.session;
        } else {
            return null;
        }
    }

    /**
     * The user clicks on the register button.
     */
    private void onRegisterClick(boolean checkRegistrationValues) {
        Log.d(LOG_TAG, "## onRegisterClick(): IN - checkRegistrationValues=" + checkRegistrationValues);
        onClick();

        // the user switches to another mode
        if (mMode != MODE_ACCOUNT_CREATION) {
            mMode = MODE_ACCOUNT_CREATION;
            refreshDisplay();
            return;
        }

        // sanity check
        if (null == mRegistrationResponse) {
            Log.d(LOG_TAG,"## onRegisterClick(): return - mRegistrationResponse=nuul");
            return;
        }

        // parameters
        final String email = mCreationEmailTextView.getText().toString().trim();
        final String name = mCreationUsernameTextView.getText().toString().trim();
        final String password = mCreationPassword1TextView.getText().toString().trim();
        final String passwordCheck = mCreationPassword2TextView.getText().toString().trim();

        if (checkRegistrationValues) {
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(getApplicationContext(), getString(R.string.auth_invalid_user_name), Toast.LENGTH_SHORT).show();
                return;
            } else if (TextUtils.isEmpty(password)) {
                Toast.makeText(getApplicationContext(), getString(R.string.auth_missing_password), Toast.LENGTH_SHORT).show();
                return;
            } else if (password.length() < 6) {
                Toast.makeText(getApplicationContext(), getString(R.string.auth_invalid_password), Toast.LENGTH_SHORT).show();
                return;
            } else if (!TextUtils.equals(password, passwordCheck)) {
                Toast.makeText(getApplicationContext(), getString(R.string.auth_password_dont_match), Toast.LENGTH_SHORT).show();
                return;
            } else if (!TextUtils.isEmpty(email) && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(getApplicationContext(), getString(R.string.auth_invalid_email), Toast.LENGTH_SHORT).show();
                return;
            } else {
                String expression ="^[a-z0-9.\\-_]+$";

                Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(name);
                if (!matcher.matches()) {
                    Toast.makeText(getApplicationContext(), getString(R.string.auth_invalid_user_name), Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // display a warning when there is no email address
            if (TextUtils.isEmpty(email)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(R.string.auth_missing_optional_email);
                builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        onRegisterClick(false);
                    }
                });

                builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

                AlertDialog alert = builder.create();
                alert.show();

                return;
            }
        }

        final String fsession = mRegistrationResponse.session;

        // ask server if chosen user name is available before doing anything
        if(!mIsUserNameAvailable && !isEmailIdentityFlowCompleted()) {
            Log.d(LOG_TAG,"## onRegisterClick(): start check user name flow");
            setFlowsMaskEnabled(true);

            // set only name value
            RegistrationParams params = new RegistrationParams();
            //params.auth = authParams;
            params.username = name;
            params.password = password;
            params.bind_email = !TextUtils.isEmpty(email);

            checkNameAvailability(params);
            Log.d(LOG_TAG, "## onRegisterClick(): check user name flow exit");
            return;
        }

        // require an email registration
        if (!TextUtils.isEmpty(email) && !isEmailIdentityFlowCompleted()) {
            Log.d(LOG_TAG,"## onRegisterClick(): start email flow");
            setFlowsMaskEnabled(true);

            final HomeserverConnectionConfig hsConfig = getHsConfig();
            mLoginHandler.requestValidationToken(LoginActivity.this, hsConfig, email, fsession,new SimpleApiCallback<ThreePid>() {
                @Override
                public void onSuccess(ThreePid thirdPid) {
                    if ((mMode == MODE_ACCOUNT_CREATION) && (TextUtils.equals(fsession, getRegistrationSession()))) {
                        HashMap<String, Object> pidsCredentialsAuth = new HashMap<String, Object>();
                        pidsCredentialsAuth.put("client_secret", thirdPid.clientSecret);
                        String identityServerHost = mIdentityServerText.getText().toString().trim();
                        if (identityServerHost.startsWith("http://")) {
                            identityServerHost = identityServerHost.substring("http://".length());
                        } else if (identityServerHost.startsWith("https://")) {
                            identityServerHost = identityServerHost.substring("https://".length());
                        }

                        pidsCredentialsAuth.put("id_server", identityServerHost);
                        pidsCredentialsAuth.put("sid", thirdPid.sid);
                        RegistrationParams params = new RegistrationParams();

                        HashMap<String, Object> authParams = new HashMap<String, Object>();
                        authParams.put("session", mRegistrationResponse.session);
                        authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
                        authParams.put("threepid_creds", pidsCredentialsAuth);

                        params.auth = authParams;
                        params.username = name;
                        params.password = password;
                        params.bind_email = true;

                        register(params);
                    }
                }

                private void onError (String errorMessage){
                    if ((mMode == MODE_ACCOUNT_CREATION) && (TextUtils.equals(fsession, getRegistrationSession()))) {
                        setFlowsMaskEnabled(false);
                        setLoginButtonsEnabled(false);
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(final Exception e) {
                    onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(final MatrixError e) {
                    onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                }
            });

            Log.d(LOG_TAG, "## onRegisterClick(): email flow exit");
            return;
        }

        // checks parameters
        if (isRecaptchaFlowRequired()) {
            Log.d(LOG_TAG,"## onRegisterClick(): start captcha flow");
            // retrieve the site_key
            String site_key = null;

            if (null != mRegistrationResponse.params) {
                Object recaptchaParamsAsVoid = mRegistrationResponse.params.get(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_RECAPTCHA);

                if (null != recaptchaParamsAsVoid) {
                    try {
                        Map<String, String> recaptchaParams = (Map<String, String>)recaptchaParamsAsVoid;
                        site_key = recaptchaParams.get("public_key");

                    } catch (Exception e) {
                    }

                    if (!TextUtils.isEmpty(site_key)) {
                        Intent intent = new Intent(LoginActivity.this, AccountCreationCaptchaActivity.class);

                        intent.putExtra(AccountCreationCaptchaActivity.EXTRA_HOME_SERVER_URL, mHomeServerUrl);
                        intent.putExtra(AccountCreationCaptchaActivity.EXTRA_SITE_KEY, site_key);

                        startActivityForResult(intent, CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE);
                        Log.d(LOG_TAG, "## onRegisterClick(): captcha flow started AccountCreationCaptchaActivity");
                        return;
                    }
                }
            }
        } else {
            Log.d(LOG_TAG,"## onRegisterClick(): captcha flow skipped");
        }

        Log.d(LOG_TAG,"## onRegisterClick(): start default flow");
        // use the default registration
        RegistrationParams params = new RegistrationParams();

        HashMap<String, Object> authParams = new HashMap<String, Object>();
        authParams.put("session", mRegistrationResponse.session);
        authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD);

        params.auth = authParams;
        params.username = name;
        params.password = password;
        params.bind_email = true;

        register(params);
    }

    //==============================================================================================================
    // login management
    //==============================================================================================================

    /**
     * Dismiss the keyboard and save the updated values
     */
    private void onClick() {
        onIdentityserverUrlUpdate();
        onHomeserverUrlUpdate();

        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mHomeServerText.getWindowToken(), 0);
    }

    private void onLoginClick(String hsUrlString, String identityUrlString, String username, String password) {
        onClick();

        // the user switches to another mode
        if (mMode != MODE_LOGIN) {
            mMode = MODE_LOGIN;
            refreshDisplay();
            return;
        }

        // --------------------- sanity tests for input values.. ---------------------
        if (!hsUrlString.startsWith("http")) {
            Toast.makeText(this, getString(R.string.login_error_must_start_http), Toast.LENGTH_SHORT).show();
            return;
        }

        if (!identityUrlString.startsWith("http")) {
            Toast.makeText(this, getString(R.string.login_error_must_start_http), Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, getString(R.string.login_error_invalid_credentials), Toast.LENGTH_SHORT).show();
            return;
        }

        // ---------------------------------------------------------------------------
        final HomeserverConnectionConfig hsConfig = getHsConfig();

        // disable UI actions
        setFlowsMaskEnabled(true);

        try {
            mLoginHandler.login(this, hsConfig, username, password, new SimpleApiCallback<HomeserverConnectionConfig>(this) {
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
                    onFailureDuringAuthRequest(e);
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
        // should not check login flows
        if (mMode != MODE_LOGIN) {
            return;
        }

        try {
            final HomeserverConnectionConfig hsConfig = getHsConfig();

            // invalid URL
            if (null == hsConfig) {
                setLoginButtonsEnabled(false);
            } else {
                setFlowsMaskEnabled(true);

                mLoginHandler.getSupportedLoginFlows(LoginActivity.this, hsConfig, new SimpleApiCallback<List<LoginFlow>>() {
                    @Override
                    public void onSuccess(List<LoginFlow> flows) {
                        if (mMode == MODE_LOGIN) {
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
                    }

                    private void onError(String errorMessage) {
                        if (mMode == MODE_LOGIN) {
                            setFlowsMaskEnabled(false);
                            setLoginButtonsEnabled(false);
                            Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                        }
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
                        onFailureDuringAuthRequest(e);
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
            setFlowsMaskEnabled(false);
        }
    }

    //==============================================================================================================
    // Instance backup
    //==============================================================================================================

    private void restoreSavedData(Bundle savedInstanceState) {
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

            mRegistrationResponse = (RegistrationFlowResponse) savedInstanceState.getSerializable(SAVED_CREATION_REGISTRATION_RESPONSE);

            mMode = savedInstanceState.getInt(SAVED_MODE, MODE_LOGIN);

            // check if the application has been opened by click on an url
            if (savedInstanceState.containsKey(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                mUniversalLinkUri = savedInstanceState.getParcelable(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(LOG_TAG, "## onRestoreInstanceState(): IN");
        restoreSavedData(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
        Log.d(LOG_TAG, "## onSaveInstanceState(): IN");

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

        if (!TextUtils.isEmpty(mIdentityServerText.getText().toString().trim())) {
            savedInstanceState.putString(SAVED_IDENTITY_SERVER_URL, mIdentityServerText.getText().toString().trim());
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

        // check if the application has been opened by click on an url
        if (null != mUniversalLinkUri) {
            savedInstanceState.putParcelable(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, mUniversalLinkUri);
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
        boolean isLoginMode = mMode == MODE_LOGIN;

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
     * @param aIsMaskEnabled
     */
    private void setFlowsMaskEnabled(boolean aIsMaskEnabled) {
        setFlowsMaskEnabled(aIsMaskEnabled, null);
    }

    /**
     * display a loading screen mask over the login screen
     * @param aIsMaskEnabled true to enable the loading screen, false otherwise
     */
    private void setFlowsMaskEnabled(boolean aIsMaskEnabled, String progressText) {
        // disable/enable login buttons
        setLoginButtonsEnabled(!aIsMaskEnabled);

        if(null != mLoginMaskView) {
            mLoginMaskView.setVisibility(aIsMaskEnabled ? View.VISIBLE : View.GONE);
        }

        if (null != mProgressTextView) {
            mProgressTextView.setText(progressText);
            mProgressTextView.setVisibility((null != progressText) ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * @param enabled enabled/disabled the login buttons
     */
    private void setLoginButtonsEnabled(boolean enabled) {
        mLoginButton.setEnabled(enabled || (mMode == MODE_ACCOUNT_CREATION));
        mRegisterButton.setEnabled(enabled || (mMode == MODE_LOGIN));

        mLoginButton.setAlpha((enabled || (mMode == MODE_ACCOUNT_CREATION)) ? 1.0f : 0.5f);
        mRegisterButton.setAlpha((enabled || (mMode == MODE_LOGIN)) ? 1.0f : 0.5f);
    }

    //==============================================================================================================
    // extracted info
    //==============================================================================================================

    /**
     * @return the homeserver config. null if the url is not valid
     */
    private HomeserverConnectionConfig getHsConfig() {
        if (null == mHomeserverConnectionConfig) {
            String hsUrlString = mHomeServerText.getText().toString();

            if (TextUtils.isEmpty(hsUrlString) || !hsUrlString.startsWith("http") || TextUtils.equals(hsUrlString, "http://") || TextUtils.equals(hsUrlString, "https://")) {
                Toast.makeText(this, getString(R.string.login_error_must_start_http), Toast.LENGTH_SHORT).show();
                return null;
            }

            if (!hsUrlString.startsWith("http://") && !hsUrlString.startsWith("https://")) {
                hsUrlString = "https://" + hsUrlString;
            }

            String identityServerUrlString = mIdentityServerText.getText().toString();

            if (TextUtils.isEmpty(identityServerUrlString) || !identityServerUrlString.startsWith("http") || TextUtils.equals(identityServerUrlString, "http://") || TextUtils.equals(identityServerUrlString, "https://")) {
                Toast.makeText(this, getString(R.string.login_error_must_start_http), Toast.LENGTH_SHORT).show();
                return null;
            }

            if (!identityServerUrlString.startsWith("http://") && !identityServerUrlString.startsWith("https://")) {
                identityServerUrlString = "https://" + identityServerUrlString;
            }

            mHomeserverConnectionConfig = new HomeserverConnectionConfig(Uri.parse(hsUrlString), Uri.parse(identityServerUrlString), null, new ArrayList<Fingerprint>(), false);
        }

        return mHomeserverConnectionConfig;
    }

    //==============================================================================================================
    // third party activities
    //==============================================================================================================

    protected void onActivityResult(int requestCode, int resultCode, Intent data)  {
        Log.d(LOG_TAG,"## onActivityResult(): IN - requestCode="+requestCode+" resultCode="+resultCode);

        if (CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE == requestCode) {
            if (resultCode == RESULT_OK) {
                Log.d(LOG_TAG,"## onActivityResult(): CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE => RESULT_OK");
                String captchaResponse = data.getStringExtra("response");

                final RegistrationParams params = new RegistrationParams();

                HashMap<String, Object> authParams = new HashMap<String, Object>();
                authParams.put("session", mRegistrationResponse.session);
                authParams.put("response", captchaResponse);
                authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_RECAPTCHA);

                params.auth = authParams;
                if(null == mEmailValidationExtraParams) {
                    params.username = mCreationUsernameTextView.getText().toString().trim();
                    params.password = mCreationPassword1TextView.getText().toString().trim();
                    params.bind_email = !TextUtils.isEmpty(mCreationEmailTextView.getText().toString().trim());
                } else {
                    // if LoginActivity was started from an email validation do not set username, pswd and email,
                    // otherwise the server returns M_USER_IN_USE error code
                    Log.w(LOG_TAG, "## onActivityResult(): mail validation in progress => username, pswd and email not set in register()");
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setFlowsMaskEnabled(true);
                        Log.d(LOG_TAG, "## onActivityResult(): CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE starts register()");
                        register(params);
                    }
                });
            } else {
                Log.d(LOG_TAG,"## onActivityResult(): CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE => RESULT_KO");
                // cancel the registration flow
                mRegistrationResponse = null;
                onRegistrationEnd();
                setFlowsMaskEnabled(false);
                refreshDisplay();
            }
        } else if ((ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE == requestCode) || (FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE == requestCode)) {
            if (resultCode == RESULT_OK) {
                Log.d(LOG_TAG,"## onActivityResult(): ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE => RESULT_OK");
                String homeServer = data.getStringExtra("homeServer");
                String userId = data.getStringExtra("userId");
                String accessToken = data.getStringExtra("accessToken");

                // build a credential with the provided items
                Credentials credentials = new Credentials();
                credentials.userId = userId;
                credentials.homeServer = homeServer;
                credentials.accessToken = accessToken;

                final HomeserverConnectionConfig hsConfig = getHsConfig();

                try {
                    hsConfig.setCredentials(credentials);
                } catch (Exception e) {
                    Log.d(LOG_TAG, "hsConfig setCredentials failed " + e.getLocalizedMessage());
                }

                Log.d(LOG_TAG, "Account creation succeeds");

                // let's go...
                MXSession session = Matrix.getInstance(getApplicationContext()).createSession(hsConfig);
                Matrix.getInstance(getApplicationContext()).addSession(session);
                goToSplash();
                LoginActivity.this.finish();
            } else if ((resultCode == RESULT_CANCELED) && (FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE == requestCode)) {
                Log.d(LOG_TAG,"## onActivityResult(): RESULT_CANCELED && FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE");
                // reset the home server to let the user writes a valid one.
                mHomeServerText.setText("https://");
                setLoginButtonsEnabled(false);
            }
        }
    }
}
