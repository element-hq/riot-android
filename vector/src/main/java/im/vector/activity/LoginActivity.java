/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.transition.TransitionManager;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXPatterns;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.HttpException;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.login.AutoDiscovery;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LocalizedFlowDataLoginTerms;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.ThreePidCredentials;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;

import butterknife.BindView;
import butterknife.OnClick;
import im.vector.BuildConfig;
import im.vector.LoginHandler;
import im.vector.Matrix;
import im.vector.PhoneNumberHandler;
import im.vector.R;
import im.vector.RegistrationManager;
import im.vector.UnrecognizedCertHandler;
import im.vector.activity.policies.AccountCreationTermsActivity;
import im.vector.activity.util.RequestCodesKt;
import im.vector.features.hhs.ResourceLimitDialogHelper;
import im.vector.push.fcm.FcmHelper;
import im.vector.receiver.LoginConfig;
import im.vector.receiver.VectorRegistrationReceiver;
import im.vector.receiver.VectorUniversalLinkReceiver;
import im.vector.repositories.ServerUrlsRepository;
import im.vector.ui.badge.BadgeProxy;
import im.vector.util.PhoneNumberUtils;
import im.vector.util.ViewUtilKt;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Displays the login screen.
 */
public class LoginActivity extends MXCActionBarActivity implements RegistrationManager.RegistrationListener, RegistrationManager.UsernameValidityListener {
    private static final String LOG_TAG = LoginActivity.class.getSimpleName();

    private final static int REGISTER_POLLING_PERIOD = 10 * 1000;

    private static final int REQUEST_REGISTRATION_COUNTRY = 1245;
    private static final int REQUEST_LOGIN_COUNTRY = 5678;


    public static final String EXTRA_RESTART_FROM_INVALID_CREDENTIALS = "EXTRA_RESTART_FROM_INVALID_CREDENTIALS";
    public static final String EXTRA_CONFIG = "EXTRA_CONFIG";


    // activity modes
    // either the user logs in
    // or creates a new account
    private static final int MODE_UNKNOWN = 0;
    private static final int MODE_LOGIN = 1;
    private static final int MODE_LOGIN_SSO = 2;
    private static final int MODE_ACCOUNT_CREATION = 3;
    private static final int MODE_FORGOT_PASSWORD = 4;
    private static final int MODE_FORGOT_PASSWORD_WAITING_VALIDATION = 5;
    private static final int MODE_ACCOUNT_CREATION_THREE_PID = 6;

    // saved parameters index
    // creation
    private static final String SAVED_CREATION_EMAIL_THREEPID = "SAVED_CREATION_EMAIL_THREEPID";

    private ThreePid mPendingEmailValidation;

    // mode
    private static final String SAVED_MODE = "SAVED_MODE";

    // activity mode
    private int mMode = MODE_LOGIN;

    // Cache for Discovery results (domain -> Discovery)
    private Map<String, AutoDiscovery.DiscoveredClientConfig> autoDiscoveredDomainCache = new HashMap<>();

    // graphical items
    @BindView(R.id.login_main_container)
    ViewGroup mMainContainer;

    @BindView(R.id.login_form_container)
    ViewGroup mFormContainer;

    // Will contain all the TextInputLayout of the layout
    List<TextInputLayout> mTextInputLayouts;

    // Layouts
    @BindView(R.id.login_inputs_layout)
    View mLoginLayout;

    @BindView(R.id.creation_inputs_layout)
    View mCreationLayout;

    @BindView(R.id.forget_password_inputs_layout)
    View mForgetPasswordLayout;

    @BindView(R.id.three_pid_layout)
    View mThreePidLayout;

    // login button
    @BindView(R.id.button_login)
    Button mLoginButton;

    @BindView(R.id.button_switch_to_register)
    Button mSwitchToRegisterButton;

    // login SSO button
    @BindView(R.id.button_login_sso)
    Button mLoginSsoButton;

    // create account button
    @BindView(R.id.button_register)
    Button mRegisterButton;

    @BindView(R.id.button_switch_to_login)
    Button mSwitchToLoginButton;

    // forgot password button
    @BindView(R.id.button_reset_password)
    Button mForgotPasswordButton;

    // The email has been validated
    @BindView(R.id.button_forgot_email_validate)
    Button mForgotValidateEmailButton;

    // the login account name
    @BindView(R.id.login_user_name_til)
    TextInputLayout mLoginEmailTextViewTil;

    @BindView(R.id.login_user_name)
    EditText mLoginEmailTextView;

    // Login phone number
    @BindView(R.id.login_phone_number_value_til)
    TextInputLayout mLoginPhoneNumberTil;

    @BindView(R.id.login_phone_number_value)
    EditText mLoginPhoneNumber;

    @BindView(R.id.login_phone_number_country)
    EditText mLoginPhoneNumberCountryCode;

    // the login password
    @BindView(R.id.login_password_til)
    TextInputLayout mLoginPasswordTextViewTil;

    @BindView(R.id.login_password)
    EditText mLoginPasswordTextView;

    @BindView(R.id.login_actions_bar)
    View mButtonsView;

    // if the taps on login button
    // after updating the IS / HS urls
    // without selecting another item
    // the IS/HS textviews don't lose the focus
    // and the flow is not checked.
    private boolean mIsPendingLogin;

    // the creation user name
    @BindView(R.id.creation_your_name_til)
    TextInputLayout mCreationUsernameTextViewTil;

    @BindView(R.id.creation_your_name)
    EditText mCreationUsernameTextView;

    // the password 1 name
    @BindView(R.id.creation_password1_til)
    TextInputLayout mCreationPassword1TextViewTil;

    @BindView(R.id.creation_password1)
    EditText mCreationPassword1TextView;

    // the password 2 name
    @BindView(R.id.creation_password2_til)
    TextInputLayout mCreationPassword2TextViewTil;

    @BindView(R.id.creation_password2)
    EditText mCreationPassword2TextView;

    // forgot my password
    @BindView(R.id.login_forgot_password)
    TextView mPasswordForgottenTxtView;

    // the forgot password email text view
    @BindView(R.id.forget_email_address_til)
    TextInputLayout mForgotEmailTextViewTil;

    @BindView(R.id.forget_email_address)
    EditText mForgotEmailTextView;

    // the password 1 name
    @BindView(R.id.forget_new_password_til)
    TextInputLayout mForgotPassword1TextViewTil;

    @BindView(R.id.forget_new_password)
    EditText mForgotPassword1TextView;

    // the password 2 name
    @BindView(R.id.forget_confirm_new_password_til)
    TextInputLayout mForgotPassword2TextViewTil;

    @BindView(R.id.forget_confirm_new_password)
    EditText mForgotPassword2TextView;

    // the home server text
    @BindView(R.id.login_matrix_server_url_til)
    TextInputLayout mHomeServerTextTil;

    @BindView(R.id.login_matrix_server_url)
    EditText mHomeServerText;

    // the identity server text
    @BindView(R.id.login_identity_url_til)
    TextInputLayout mIdentityServerTextTil;

    @BindView(R.id.login_identity_url)
    EditText mIdentityServerText;

    // used to display a UI mask on the screen
    @BindView(R.id.flow_ui_mask_login)
    View mWaitingView;

    // a text displayed while there is progress
    @BindView(R.id.flow_progress_message_textview)
    TextView mProgressTextView;

    // the layout (there is a layout for each mode)
    @BindView(R.id.main_input_layout)
    View mMainLayout;

    // HS / identity URL layouts
    @BindView(R.id.login_matrix_server_options_layout)
    View mHomeServerUrlsLayout;

    @BindView(R.id.display_server_url_expand_checkbox)
    CheckBox mUseCustomHomeServersCheckbox;

    // the pending universal link uri (if any)
    private Parcelable mUniversalLinkUri;

    // the HS and the IS urls
    private String mHomeServerUrl = null;
    private String mIdentityServerUrl = null;

    // Account creation - Three pid
    @BindView(R.id.instructions)
    TextView mThreePidInstructions;

    @BindView(R.id.registration_email_til)
    TextInputLayout mEmailAddressTil;

    @BindView(R.id.registration_email)
    EditText mEmailAddress;

    @BindView(R.id.registration_phone_number)
    View mPhoneNumberLayout;

    @BindView(R.id.registration_phone_number_country)
    EditText mRegistrationPhoneNumberCountryCode;

    @BindView(R.id.registration_phone_number_value_til)
    TextInputLayout mRegistrationPhoneNumberTil;

    @BindView(R.id.registration_phone_number_value)
    EditText mRegistrationPhoneNumber;

    @BindView(R.id.button_submit_three_pid)
    Button mSubmitThreePidButton;

    @BindView(R.id.button_skip_three_pid)
    Button mSkipThreePidButton;

    // Home server options
    @BindView(R.id.homeserver_layout)
    View mHomeServerOptionLayout;

    // Registration Manager
    private RegistrationManager mRegistrationManager;

    // login handler
    private final LoginHandler mLoginHandler = new LoginHandler();

    // save the config because trust a certificate is asynchronous.
    private HomeServerConnectionConfig mHomeserverConnectionConfig;

    // next link parameters
    private Map<String, String> mEmailValidationExtraParams;

    // the next link parameters were not managed
    private boolean mIsMailValidationPending;

    // use to reset the password when the user click on the email validation
    private ThreePidCredentials mForgotPid = null;

    // network state notification
    private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

                if (networkInfo != null && networkInfo.isConnected()) {
                    // refresh only once
                    if (mIsWaitingNetworkConnection) {
                        refreshDisplay(true);
                    } else {
                        removeNetworkStateNotificationListener();
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## BroadcastReceiver onReceive failed " + e.getMessage(), e);
            }
        }
    };

    private ResourceLimitDialogHelper mResourceLimitDialogHelper;

    private boolean mIsWaitingNetworkConnection = false;

    /**
     * Tell whether the password has been reset with success.
     * Used to return on login screen on submit button pressed.
     */
    private boolean mIsPasswordReset;

    // there is a polling thread to monitor when the email has been validated.
    private Runnable mRegisterPollingRunnable;
    private Handler mHandler;

    private PhoneNumberHandler mLoginPhoneNumberHandler;
    private PhoneNumberHandler mRegistrationPhoneNumberHandler;

    private Dialog mCurrentDialog;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    protected void onDestroy() {
        if (mLoginPhoneNumberHandler != null) {
            mLoginPhoneNumberHandler.release();
        }
        if (mRegistrationPhoneNumberHandler != null) {
            mRegistrationPhoneNumberHandler.release();
        }
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
            mCurrentDialog = null;
        }

        cancelEmailPolling();
        super.onDestroy();
        Log.i(LOG_TAG, "## onDestroy(): IN");
        // ignore any server response when the activity is destroyed
        mMode = MODE_UNKNOWN;
        mEmailValidationExtraParams = null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeNetworkStateNotificationListener();
        autoDiscoveredDomainCache.clear();
    }

    /**
     * Used in the mail validation flow.
     * This method is called when the LoginActivity is set to foreground due
     * to a {@link #startActivity(Intent)} where the flags Intent.FLAG_ACTIVITY_CLEAR_TOP and Intent.FLAG_ACTIVITY_SINGLE_TOP}
     * are set (see: {@link VectorRegistrationReceiver}).
     *
     * @param aIntent new intent
     */
    @Override
    protected void onNewIntent(Intent aIntent) {
        super.onNewIntent(aIntent);
        Log.d(LOG_TAG, "## onNewIntent(): IN ");

        Bundle receivedBundle;

        if (null == aIntent) {
            Log.d(LOG_TAG, "## onNewIntent(): Unexpected value - aIntent=null ");
        } else if (null == (receivedBundle = aIntent.getExtras())) {
            Log.d(LOG_TAG, "## onNewIntent(): Unexpected value - extras are missing");
        } else if (receivedBundle.containsKey(VectorRegistrationReceiver.EXTRA_EMAIL_VALIDATION_PARAMS)) {
            Log.d(LOG_TAG, "## onNewIntent() Login activity started by email verification for registration");

            if (processEmailValidationExtras(receivedBundle)) {
                checkIfMailValidationPending();
            }
        }
    }

    @Override
    public int getLayoutRes() {
        return R.layout.activity_vector_login;
    }

    @Override
    public void initUiAndData() {
        if (null == getIntent()) {
            Log.d(LOG_TAG, "## onCreate(): IN with no intent");
        } else {
            Log.d(LOG_TAG, "## onCreate(): IN with flags " + Integer.toHexString(getIntent().getFlags()));
        }

        // warn that the application has started.
        CommonActivityUtils.onApplicationStarted(this);

        FcmHelper.ensureFcmTokenIsRetrieved(this);

        Intent intent = getIntent();

        // already registered
        if (hasCredentials()) {
            /*
            if (null != intent && (intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0) {
                Log.d(LOG_TAG, "## onCreate(): goToSplash because the credentials are already provided.");
                goToSplash();
            } else {
                // detect if the application has already been started
                if (EventStreamService.getInstance() == null) {
                    Log.d(LOG_TAG, "## onCreate(): goToSplash with credentials but there is no event stream service.");
                    goToSplash();
                } else {
                    Log.d(LOG_TAG, "## onCreate(): close the login screen because it is a temporary task");
                }
            }
            */
            Log.d(LOG_TAG, "## onCreate(): goToSplash because the credentials are already provided.");
            goToSplash();

            finish();
            return;
        }

        setWaitingView(mWaitingView);

        // login
        ViewUtilKt.tintDrawableCompat(mLoginPhoneNumberCountryCode, R.attr.vctr_settings_icon_tint_color);

        // account creation - three pid
        ViewUtilKt.tintDrawableCompat(mRegistrationPhoneNumberCountryCode, R.attr.vctr_settings_icon_tint_color);

        if (isFirstCreation()) {
            mRegistrationManager = new RegistrationManager(null);
            mResourceLimitDialogHelper = new ResourceLimitDialogHelper(this, null);
            mHomeServerText.setText(ServerUrlsRepository.INSTANCE.getLastHomeServerUrl(this));
            mIdentityServerText.setText(ServerUrlsRepository.INSTANCE.getLastIdentityServerUrl(this));
        } else {
            final Bundle savedInstanceState = getSavedInstanceState();
            mRegistrationManager = new RegistrationManager(savedInstanceState);
            mResourceLimitDialogHelper = new ResourceLimitDialogHelper(this, savedInstanceState);
            restoreSavedData(savedInstanceState);
        }
        addToRestorables(mResourceLimitDialogHelper);

        mForgotValidateEmailButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onForgotOnEmailValidated(getHsConfig());
            }
        });

        // home server input validity: if the user taps on the next / done button
        mHomeServerText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onHomeServerUrlUpdate(true);
                    return true;
                }

                return false;
            }
        });

        // home server input validity: when focus changes
        mHomeServerText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    onHomeServerUrlUpdate(true);
                }
            }
        });

        // identity server input validity: if the user taps on the next / done button
        mIdentityServerText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onIdentityServerUrlUpdate(true);
                    return true;
                }

                return false;
            }
        });

        // identity server input validity: when focus changes
        mIdentityServerText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    onIdentityServerUrlUpdate(true);
                }
            }
        });

        // "forgot password?" handler
        mPasswordForgottenTxtView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMode = MODE_FORGOT_PASSWORD;
                refreshDisplay(true);
            }
        });

        mUseCustomHomeServersCheckbox.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mUseCustomHomeServersCheckbox.post(new Runnable() {
                    @Override
                    public void run() {
                        // Reset SSO mode
                        if (mMode == MODE_LOGIN_SSO) {
                            mMode = MODE_LOGIN;
                        }

                        // reset the HS urls.
                        mHomeServerUrl = null;
                        mIdentityServerUrl = null;
                        onIdentityServerUrlUpdate(false);
                        onHomeServerUrlUpdate(false);
                        refreshDisplay(true);
                    }
                });
            }
        });

        mLoginPhoneNumberHandler = new PhoneNumberHandler(this,
                mLoginPhoneNumber,
                mLoginPhoneNumberCountryCode,
                PhoneNumberHandler.DISPLAY_COUNTRY_ISO_CODE,
                REQUEST_LOGIN_COUNTRY);
        mLoginPhoneNumberHandler.setCountryCode(PhoneNumberUtils.getCountryCode(this));

        mRegistrationPhoneNumberHandler = new PhoneNumberHandler(this,
                mRegistrationPhoneNumber,
                mRegistrationPhoneNumberCountryCode,
                PhoneNumberHandler.DISPLAY_COUNTRY_ISO_CODE,
                REQUEST_REGISTRATION_COUNTRY);

        // reset the badge counter
        BadgeProxy.INSTANCE.updateBadgeCount(this, 0);

        mHomeServerText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Disable buttons
                setActionButtonsEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String cleanedUrl = sanitizeUrl(s.toString());

                if (!TextUtils.equals(cleanedUrl, s.toString())) {
                    mHomeServerText.setText(cleanedUrl);
                    mHomeServerText.setSelection(cleanedUrl.length());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        mIdentityServerText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Disable buttons
                setActionButtonsEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String cleanedUrl = sanitizeUrl(s.toString());

                if (!TextUtils.equals(cleanedUrl, s.toString())) {
                    mIdentityServerText.setText(cleanedUrl);
                    mIdentityServerText.setSelection(cleanedUrl.length());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        // set the handler used by the register to poll the server response
        mHandler = new Handler(getMainLooper());

        // Check whether the application has been resumed from an universal link
        Bundle receivedBundle = null != intent ? getIntent().getExtras() : null;
        if (null != receivedBundle) {
            if (receivedBundle.containsKey(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
                mUniversalLinkUri = receivedBundle.getParcelable(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
                Log.d(LOG_TAG, "## onCreate() Login activity started by universal link");
            } else if (receivedBundle.containsKey(VectorRegistrationReceiver.EXTRA_EMAIL_VALIDATION_PARAMS)) {
                Log.d(LOG_TAG, "## onCreate() Login activity started by email verification for registration");
                if (processEmailValidationExtras(receivedBundle)) {
                    // Reset the pending email validation if any.
                    mPendingEmailValidation = null;

                    // Finalize the email verification.
                    checkIfMailValidationPending();
                }
            }
        }

        // Check whether an email validation was pending when the instance was saved.
        if (null != mPendingEmailValidation) {
            Log.d(LOG_TAG, "## onCreate() An email validation was pending");

            // Sanity check
            HomeServerConnectionConfig hsConfig = getHsConfig();
            if (null != hsConfig && !isFirstCreation()) {
                // retrieve the name and pwd from store data (we consider here that these inputs have been already checked)
                Log.d(LOG_TAG, "## onCreate() Resume email validation");
                // Resume the email validation polling
                enableLoadingScreen(true);
                mRegistrationManager.addEmailThreePid(mPendingEmailValidation);
                mRegistrationManager.attemptRegistration(this, this);
                onWaitingEmailValidation();
            }
        }

        mTextInputLayouts = ViewUtilKt.findAllTextInputLayout(mFormContainer);
        ViewUtilKt.autoResetTextInputLayoutErrors(mTextInputLayouts);

        mLoginEmailTextView.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String candidate = mLoginEmailTextView.getText().toString();
                //Try to see if we can find a domain
                if (MXPatterns.isUserId(candidate)) {
                    //looks like a user name with domain
                    String possibleDomain = candidate.substring(candidate.indexOf(":") + 1);
                    if (possibleDomain.isEmpty()) return;
                    tryAutoDiscover(possibleDomain);
                }
            }
        });

        // Get config extra
        LoginConfig loginConfig = getIntent().getParcelableExtra(EXTRA_CONFIG);
        if (isFirstCreation() && loginConfig != null) {
            mHomeServerText.setText(loginConfig.getHomeServerUrl());
            mIdentityServerText.setText(loginConfig.getIdentityServerUrl());
            mUseCustomHomeServersCheckbox.performClick();
        }
    }

    private void tryAutoDiscover(String possibleDomain) {
        if (autoDiscoveredDomainCache.containsKey(possibleDomain)) {
            onAutoDiscoveryRetrieved(possibleDomain, autoDiscoveredDomainCache.get(possibleDomain));
        } else {
            enableLoadingScreen(true);

            new AutoDiscovery().findClientConfig(possibleDomain, new ApiCallback<AutoDiscovery.DiscoveredClientConfig>() {

                String mDomain = possibleDomain;

                @Override
                public void onUnexpectedError(Exception e) {
                    if (!TextUtils.equals(mDomain, possibleDomain)) return;
                    enableLoadingScreen(false);
                    Log.e(LOG_TAG, "AutoDiscovery error for domain" + mDomain, e);
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (!TextUtils.equals(mDomain, possibleDomain)) return;
                    enableLoadingScreen(false);
                    Log.e(LOG_TAG, "AutoDiscovery Network error for domain " + mDomain, e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    enableLoadingScreen(false);
                    //nop
                }

                @Override
                public void onSuccess(AutoDiscovery.DiscoveredClientConfig info) {
                    if (info.getAction() == AutoDiscovery.Action.PROMPT
                            || info.getAction() == AutoDiscovery.Action.IGNORE) {
                        // Prompt or Ignore, keep in cache
                        autoDiscoveredDomainCache.put(mDomain, info);
                    }

                    Log.d(LOG_TAG, "AutoDiscovery info " + info);
                    if (!TextUtils.equals(mDomain, possibleDomain)) return;

                    enableLoadingScreen(false);
                    onAutoDiscoveryRetrieved(mDomain, info);
                }
            });
        }
    }

    private void onAutoDiscoveryRetrieved(String domain, AutoDiscovery.DiscoveredClientConfig info) {
        // Do not change anything if not in login mode
        if (mMode != MODE_LOGIN) return;

        if (AutoDiscovery.Action.PROMPT == info.getAction()) {
            if (info.getWellKnown() == null) return;
            if (info.getWellKnown().homeServer == null) return;
            final String hs = info.getWellKnown().homeServer.baseURL;
            String ids = ServerUrlsRepository.INSTANCE.getDefaultIdentityServerUrl(LoginActivity.this);
            if (info.getWellKnown().identityServer != null
                    && !TextUtils.isEmpty(info.getWellKnown().identityServer.baseURL)) {
                ids = info.getWellKnown().identityServer.baseURL;
            }

            if (hs != null) {
                if (ServerUrlsRepository.INSTANCE.isDefaultHomeServerUrl(LoginActivity.this, hs)) {
                    if (mUseCustomHomeServersCheckbox.isChecked()) {
                        mHomeServerText.setText(null);
                        mIdentityServerText.setText(null);
                        mUseCustomHomeServersCheckbox.performClick();
                    }
                } else {
                    if (!mUseCustomHomeServersCheckbox.isChecked()
                            || !hs.equals(mHomeServerUrl)
                            || !ids.equals(mIdentityServerUrl)) {
                        String finalIds = ids;
                        new AlertDialog.Builder(LoginActivity.this)
                                .setTitle(getString(R.string.autodiscover_well_known_autofill_dialog_title))
                                .setMessage(getString(R.string.autodiscover_well_known_autofill_dialog_message,
                                        domain,
                                        String.format("• %s\n• %s", hs, ids)))
                                .setPositiveButton(getString(R.string.autodiscover_well_known_autofill_confirm), (dialog, which) -> {
                                    mHomeServerText.setText(hs);
                                    mIdentityServerText.setText(finalIds);
                                    if (!mUseCustomHomeServersCheckbox.isChecked()) {
                                        mUseCustomHomeServersCheckbox.performClick();
                                    } else {
                                        onHomeServerUrlUpdate(true);
                                        onIdentityServerUrlUpdate(true);
                                    }
                                })
                                .setNegativeButton(R.string.ignore, null)
                                .show();
                    }
                }
            }
        } else if (AutoDiscovery.Action.FAIL_ERROR == info.getAction()
                || AutoDiscovery.Action.FAIL_PROMPT == info.getAction()) {
            mLoginEmailTextViewTil.setError(getString(R.string.autodiscover_invalid_response));
        }
    }

    /**
     * The server URLs have been updated from a receiver
     */
    public void onServerUrlsUpdateFromReferrer() {
        mHomeServerText.setText(ServerUrlsRepository.INSTANCE.getLastHomeServerUrl(this));
        mIdentityServerText.setText(ServerUrlsRepository.INSTANCE.getLastIdentityServerUrl(this));

        if (!mUseCustomHomeServersCheckbox.isChecked()) {
            mUseCustomHomeServersCheckbox.performClick();
        }
    }

    /**
     * @return the home server Url according to custom HS checkbox
     */
    private String getHomeServerUrl() {
        String url = ServerUrlsRepository.INSTANCE.getDefaultHomeServerUrl(this);

        if (mUseCustomHomeServersCheckbox.isChecked()) {
            url = mHomeServerText.getText().toString().trim();

            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
        }

        return url;
    }

    /**
     * @return the identity server URL according to custom HS checkbox
     */
    private String getIdentityServerUrl() {
        String url = ServerUrlsRepository.INSTANCE.getDefaultIdentityServerUrl(this);

        if (mUseCustomHomeServersCheckbox.isChecked()) {
            url = mIdentityServerText.getText().toString().trim();

            if (url.endsWith("/")) {
                url = url.substring(0, url.length() - 1);
            }
        }

        return url;
    }

    /**
     * Add a listener to be notified when the device gets connected to a network.
     * This method is mainly used to refresh the login UI upon the network is back.
     * See {@link #removeNetworkStateNotificationListener()}
     */
    private void addNetworkStateNotificationListener() {
        if (null != Matrix.getInstance(getApplicationContext()) && !mIsWaitingNetworkConnection) {
            try {
                registerReceiver(mNetworkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
                mIsWaitingNetworkConnection = true;
            } catch (Exception e) {
                Log.e(LOG_TAG, "## addNetworkStateNotificationListener : " + e.getMessage(), e);
            }
        }
    }

    /**
     * Remove the network listener set in {@link #addNetworkStateNotificationListener()}.
     */
    private void removeNetworkStateNotificationListener() {
        if (null != Matrix.getInstance(getApplicationContext()) && mIsWaitingNetworkConnection) {
            try {
                unregisterReceiver(mNetworkReceiver);
                mIsWaitingNetworkConnection = false;
            } catch (Exception e) {
                Log.e(LOG_TAG, "## removeNetworkStateNotificationListener : " + e.getMessage(), e);
            }
        }
    }

    /**
     * Check if the home server url has been updated
     *
     * @param checkFlowOnUpdate check the flow on IS update
     * @return true if the HS url has been updated
     */
    private boolean onHomeServerUrlUpdate(boolean checkFlowOnUpdate) {
        if (!TextUtils.equals(mHomeServerUrl, getHomeServerUrl())) {
            // Reset SSO mode
            if (mMode == MODE_LOGIN_SSO) {
                mMode = MODE_LOGIN;
            }

            mHomeServerUrl = getHomeServerUrl();
            mRegistrationManager.reset();

            // invalidate the current homeserver config
            mHomeserverConnectionConfig = null;
            // the account creation is not always supported so ensure that the dedicated button is always displayed.
            if (mMode == MODE_ACCOUNT_CREATION) {
                mRegisterButton.setVisibility(View.VISIBLE);
            } else if (mMode == MODE_LOGIN) {
                mSwitchToRegisterButton.setVisibility(View.VISIBLE);
            }

            // Wellknown request, to fill identity server Url
            new AutoDiscovery()
                    .getIdentityServer(mHomeServerUrl, new ApiCallback<String>() {

                        @Override
                        public void onSuccess(@Nullable String info) {
                            if (!TextUtils.isEmpty(info)) {
                                mIdentityServerUrl = info;
                            } else {
                                // Use default
                                mIdentityServerUrl = ServerUrlsRepository.INSTANCE.getLastIdentityServerUrl(LoginActivity.this);
                            }
                            mIdentityServerText.setText(mIdentityServerUrl);

                            onHomeServerUrlUpdateStep2(checkFlowOnUpdate);
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            onHomeServerUrlUpdateStep2(checkFlowOnUpdate);
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            onHomeServerUrlUpdateStep2(checkFlowOnUpdate);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            onHomeServerUrlUpdateStep2(checkFlowOnUpdate);
                        }
                    });

            return true;
        }

        return false;
    }

    private void onHomeServerUrlUpdateStep2(boolean checkFlowOnUpdate) {
        if (checkFlowOnUpdate) {
            checkFlows();
        }

        // Check if we have to display the identity server url field
        checkIdentityServerUrlField();
    }

    private void checkIdentityServerUrlField() {
        mIdentityServerTextTil.setVisibility(View.GONE);

        if (mMode == MODE_ACCOUNT_CREATION || mMode == MODE_FORGOT_PASSWORD) {
            new LoginRestClient(getHsConfig())
                    .doesServerRequireIdentityServerParam(new ApiCallback<Boolean>() {
                        @Override
                        public void onNetworkError(Exception e) {

                        }

                        @Override
                        public void onMatrixError(MatrixError e) {

                        }

                        @Override
                        public void onUnexpectedError(Exception e) {

                        }

                        @Override
                        public void onSuccess(Boolean info) {
                            if (info) {
                                mIdentityServerTextTil.setVisibility(View.VISIBLE);
                            }
                        }
                    });
        }
    }

    /**
     * Check if the identity server url has been updated
     *
     * @param checkFlowOnUpdate check the flow on IS update
     * @return true if the IS url has been updated
     */
    private boolean onIdentityServerUrlUpdate(boolean checkFlowOnUpdate) {
        if (!TextUtils.equals(mIdentityServerUrl, getIdentityServerUrl())) {
            // Reset SSO mode
            if (mMode == MODE_LOGIN_SSO) {
                mMode = MODE_LOGIN;
            }

            mIdentityServerUrl = getIdentityServerUrl();
            mRegistrationManager.reset();

            // invalidate the current homeserver config
            mHomeserverConnectionConfig = null;
            // the account creation is not always supported so ensure that the dedicated button is always displayed.
            if (mMode == MODE_ACCOUNT_CREATION) {
                mRegisterButton.setVisibility(View.VISIBLE);
            } else if (mMode == MODE_LOGIN) {
                mSwitchToRegisterButton.setVisibility(View.VISIBLE);
            }

            if (checkFlowOnUpdate) {
                checkFlows();
            }

            return true;
        }

        return false;
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LOG_TAG, "## onResume(): IN");

        if (isFirstCreation() && getIntent().getBooleanExtra(EXTRA_RESTART_FROM_INVALID_CREDENTIALS, false)) {
            mLoginEmailTextViewTil.setError(getString(R.string.invalid_or_expired_credentials));
        }

        // retrieve the home server path
        mHomeServerUrl = getHomeServerUrl();
        mIdentityServerUrl = getIdentityServerUrl();

        // If home server url or identity server url are not the default ones, check the mUseCustomHomeServersCheckbox
        if (!ServerUrlsRepository.INSTANCE.isDefaultHomeServerUrl(this, mHomeServerText.getText().toString())
                || !ServerUrlsRepository.INSTANCE.isDefaultIdentityServerUrl(this, mIdentityServerText.getText().toString())) {
            mUseCustomHomeServersCheckbox.setChecked(true);
        }

        refreshDisplay(true);
    }

    /**
     * Cancel the current mode to switch to the login one.
     * It should restore the login UI
     */
    private void fallbackToLoginMode() {
        // display the main layout
        mMainLayout.setVisibility(View.VISIBLE);

        // cancel the registration flow
        cancelEmailPolling();
        mEmailValidationExtraParams = null;
        mRegistrationManager.reset();
        showMainLayout();
        enableLoadingScreen(false);

        mMode = MODE_LOGIN;
        refreshDisplay(true);
    }

    /**
     * Cancel the current mode to switch to the registration one.
     * It should restore the registration UI
     */
    private void fallbackToRegistrationMode() {
        // display the main layout
        mMainLayout.setVisibility(View.VISIBLE);

        showMainLayout();
        enableLoadingScreen(false);

        mMode = MODE_ACCOUNT_CREATION;
        refreshDisplay(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            Log.d(LOG_TAG, "KEYCODE_BACK pressed");
            if (MODE_ACCOUNT_CREATION == mMode && !mRegistrationManager.hasRegistrationResponse()) {
                Log.d(LOG_TAG, "## cancel the registration mode");
                fallbackToLoginMode();
                return true;
            } else if (MODE_FORGOT_PASSWORD == mMode || MODE_FORGOT_PASSWORD_WAITING_VALIDATION == mMode) {
                Log.d(LOG_TAG, "## cancel the forgot password mode");
                fallbackToLoginMode();
                return true;
            } else if (MODE_ACCOUNT_CREATION_THREE_PID == mMode) {
                Log.d(LOG_TAG, "## cancel the three pid mode");
                cancelEmailPolling();
                mRegistrationManager.clearThreePid();
                mEmailAddress.setText("");
                mRegistrationPhoneNumberHandler.reset();
                fallbackToRegistrationMode();
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
            MXSession session = Matrix.getInstance(this).getDefaultSession();
            return null != session && session.isAlive();

        } catch (Exception e) {
            Log.e(LOG_TAG, "## Exception: " + e.getMessage(), e);
        }

        Log.e(LOG_TAG, "## hasCredentials() : invalid credentials");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // getDefaultSession could trigger an exception if the login data are corrupted
                    CommonActivityUtils.logout(LoginActivity.this);
                } catch (Exception e) {
                    Log.w(LOG_TAG, "## Exception: " + e.getMessage(), e);
                }
            }
        });

        return false;
    }

    /**
     * Some sessions have been registered, skip the login process.
     */
    private void goToSplash() {
        Log.d(LOG_TAG, "## gotoSplash(): Go to splash.");

        Intent intent = new Intent(this, SplashActivity.class);
        if (null != mUniversalLinkUri) {
            intent.putExtra(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, mUniversalLinkUri);
        }

        startActivity(intent);
    }

    private void saveServerUrlsIfCustomValuesHasBeenEntered() {
        // Save urls if not using default
        if (mUseCustomHomeServersCheckbox.isChecked()) {
            ServerUrlsRepository.INSTANCE.saveServerUrls(this,
                    mHomeServerText.getText().toString().trim(),
                    mIdentityServerText.getText().toString().trim());
        }
    }

    /**
     * check if the current page is supported by the current implementation
     */
    private void checkFlows() {
        if (mMode == MODE_LOGIN
                || mMode == MODE_LOGIN_SSO
                || mMode == MODE_FORGOT_PASSWORD
                || mMode == MODE_FORGOT_PASSWORD_WAITING_VALIDATION) {
            checkLoginFlows();
        } else {
            checkRegistrationFlows();
        }
    }

    //==============================================================================================================
    // Forgot password management
    //==============================================================================================================

    /**
     * the user forgot his password
     */
    @OnClick(R.id.button_reset_password)
    void onForgotPasswordClick() {
        final HomeServerConnectionConfig hsConfig = getHsConfig();

        // it might be null if the identity / homeserver urls are invalids
        if (null == hsConfig) {
            return;
        }

        // parameters
        final String email = mForgotEmailTextView.getText().toString().trim();
        final String password = mForgotPassword1TextView.getText().toString().trim();
        final String passwordCheck = mForgotPassword2TextView.getText().toString().trim();

        boolean hasError = false;

        if (TextUtils.isEmpty(email)) {
            mForgotEmailTextViewTil.setError(getString(R.string.auth_reset_password_missing_email));
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            mForgotEmailTextViewTil.setError(getString(R.string.auth_invalid_email));
            hasError = true;
        }

        if (TextUtils.isEmpty(password)) {
            mForgotPassword1TextViewTil.setError(getString(R.string.auth_reset_password_missing_password));
            hasError = true;
        } else if (password.length() < 6) {
            mForgotPassword1TextViewTil.setError(getString(R.string.auth_invalid_password));
            hasError = true;
        }

        if (!TextUtils.equals(password, passwordCheck)) {
            mForgotPassword2TextViewTil.setError(getString(R.string.auth_password_dont_match));
            hasError = true;
        }

        if (hasError) {
            return;
        }

        // privacy
        //Log.d(LOG_TAG, "onForgotPasswordClick for email " + email);
        Log.d(LOG_TAG, "onForgotPasswordClick");

        enableLoadingScreen(true);

        // Check if the HS require an identity server
        new LoginRestClient(getHsConfig())
                .doesServerRequireIdentityServerParam(new ApiCallback<Boolean>() {
                    @Override
                    public void onNetworkError(Exception e) {
                        enableLoadingScreen(false);
                        Toast.makeText(LoginActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        enableLoadingScreen(false);
                        Toast.makeText(LoginActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        enableLoadingScreen(false);
                        Toast.makeText(LoginActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onSuccess(Boolean requiresIdentityServer) {
                        Uri identityServerUri = hsConfig.getIdentityServerUri();
                        if (requiresIdentityServer
                                && (identityServerUri == null || identityServerUri.toString().isEmpty())) {
                            enableLoadingScreen(false);
                            Toast.makeText(LoginActivity.this, R.string.identity_server_not_defined_for_password_reset, Toast.LENGTH_LONG).show();

                        } else {
                            doForgetPasswordRequest(hsConfig, email, null);
                        }
                    }
                });
    }

    private void doForgetPasswordRequest(HomeServerConnectionConfig hsConfig, String email, @Nullable String identityServerHost) {
        ProfileRestClient pRest = new ProfileRestClient(hsConfig);
        Uri idUri = (identityServerHost != null) ? Uri.parse(identityServerHost) : null;
        pRest.forgetPassword(idUri, email, new ApiCallback<ThreePid>() {
            @Override
            public void onSuccess(ThreePid thirdPid) {
                if (mMode == MODE_FORGOT_PASSWORD) {
                    Log.d(LOG_TAG, "onForgotPasswordClick : requestEmailValidationToken succeeds");

                    enableLoadingScreen(false);

                    // refresh the messages
                    hideMainLayoutAndToast(getString(R.string.auth_reset_password_email_validation_message, email));
                    mButtonsView.setVisibility(View.VISIBLE);

                    mMode = MODE_FORGOT_PASSWORD_WAITING_VALIDATION;
                    refreshDisplay(true);

                    mForgotPid = new ThreePidCredentials();
                    mForgotPid.clientSecret = thirdPid.getClientSecret();
                    mForgotPid.idServer = identityServerHost;
                    mForgotPid.sid = thirdPid.getSid();
                }
            }

            /**
             * Display a toast to warn that the operation failed
             *
             * @param errorMessage the error message.
             */
            private void onError(final String errorMessage) {
                Log.e(LOG_TAG, "onForgotPasswordClick : requestEmailValidationToken fails with error " + errorMessage);

                if (mMode == MODE_FORGOT_PASSWORD) {
                    enableLoadingScreen(false);
                    Toast.makeText(LoginActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onNetworkError(final Exception e) {
                if (mMode == MODE_FORGOT_PASSWORD) {
                    UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                    if (unrecCertEx != null) {
                        final Fingerprint fingerprint = unrecCertEx.getFingerprint();

                        UnrecognizedCertHandler.show(hsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                            @Override
                            public void onAccept() {
                                onForgotPasswordClick();
                            }

                            @Override
                            public void onIgnore() {
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onReject() {
                                onError(e.getLocalizedMessage());
                            }
                        });
                    } else {
                        onError(e.getLocalizedMessage());
                    }
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (TextUtils.equals(MatrixError.THREEPID_NOT_FOUND, e.errcode)) {
                    onError(getString(R.string.account_email_not_found_error));
                } else {
                    onError(e.getLocalizedMessage());
                }
            }
        });
    }

    /**
     * The user warns the client that the reset password email has been received
     */
    private void onForgotOnEmailValidated(final HomeServerConnectionConfig hsConfig) {
        if (mIsPasswordReset) {
            Log.d(LOG_TAG, "onForgotOnEmailValidated : go back to login screen");

            mIsPasswordReset = false;
            mMode = MODE_LOGIN;
            showMainLayout();
            refreshDisplay(true);
        } else {
            ProfileRestClient profileRestClient = new ProfileRestClient(hsConfig);
            enableLoadingScreen(true);

            Log.d(LOG_TAG, "onForgotOnEmailValidated : try to reset the password");

            profileRestClient.resetPassword(mForgotPassword1TextView.getText().toString().trim(), mForgotPid, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    if (mMode == MODE_FORGOT_PASSWORD_WAITING_VALIDATION) {
                        Log.d(LOG_TAG, "onForgotOnEmailValidated : the password has been updated");

                        enableLoadingScreen(false);

                        // refresh the messages
                        hideMainLayoutAndToast(getString(R.string.auth_reset_password_success_message));
                        mButtonsView.setVisibility(View.VISIBLE);
                        mIsPasswordReset = true;
                        refreshDisplay(true);
                    }
                }

                /**
                 * Display a toast to warn that the operation failed
                 *
                 * @param errorMessage the error message.
                 */
                private void onError(String errorMessage, boolean cancel) {
                    if (mMode == MODE_FORGOT_PASSWORD_WAITING_VALIDATION) {
                        Log.d(LOG_TAG, "onForgotOnEmailValidated : failed " + errorMessage);

                        // display the dedicated
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                        enableLoadingScreen(false);

                        if (cancel) {
                            showMainLayout();
                            mMode = MODE_LOGIN;
                            refreshDisplay(true);
                        }
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    onError(e.getLocalizedMessage(), false);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (mMode == MODE_FORGOT_PASSWORD_WAITING_VALIDATION) {
                        if (TextUtils.equals(e.errcode, MatrixError.UNAUTHORIZED)) {
                            Log.d(LOG_TAG, "onForgotOnEmailValidated : failed UNAUTHORIZED");

                            onError(getString(R.string.auth_reset_password_error_unauthorized), false);
                        } else if (TextUtils.equals(e.errcode, MatrixError.NOT_FOUND)) {
                            String hsUrlString = hsConfig.getHomeserverUri().toString();

                            // if the identifier is not found on riot.im
                            // check if it was created with matrix.org
                            if (TextUtils.equals(hsUrlString, getString(R.string.vector_im_server_url))) {
                                hsConfig.setHomeserverUri(Uri.parse(getString(R.string.matrix_org_server_url)));
                                onForgotOnEmailValidated(hsConfig);

                                Log.d(LOG_TAG, "onForgotOnEmailValidated : test with matrix.org as HS");

                            } else {
                                onError(e.getLocalizedMessage(), false);
                            }
                        } else {
                            onError(e.getLocalizedMessage(), true);
                        }
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onError(e.getLocalizedMessage(), true);
                }
            });
        }
    }

    //==============================================================================================================
    // registration management
    //==============================================================================================================

    /**
     * Error case Management
     *
     * @param matrixError the matrix error
     */
    private void onFailureDuringAuthRequest(MatrixError matrixError) {
        enableLoadingScreen(false);

        final String errCode = matrixError.errcode;

        if (MatrixError.RESOURCE_LIMIT_EXCEEDED.equals(errCode)) {
            Log.e(LOG_TAG, "## onFailureDuringAuthRequest(): RESOURCE_LIMIT_EXCEEDED");
            mResourceLimitDialogHelper.displayDialog(matrixError);
        } else {
            final String message;
            boolean displayInTil = false;

            if (TextUtils.equals(errCode, MatrixError.FORBIDDEN)) {
                message = getString(R.string.login_error_forbidden);
                displayInTil = true;
            } else if (TextUtils.equals(errCode, MatrixError.UNKNOWN_TOKEN)) {
                message = getString(R.string.login_error_unknown_token);
            } else if (TextUtils.equals(errCode, MatrixError.BAD_JSON)) {
                message = getString(R.string.login_error_bad_json);
            } else if (TextUtils.equals(errCode, MatrixError.NOT_JSON)) {
                message = getString(R.string.login_error_not_json);
            } else if (TextUtils.equals(errCode, MatrixError.LIMIT_EXCEEDED)) {
                message = getString(R.string.login_error_limit_exceeded);
            } else if (TextUtils.equals(errCode, MatrixError.USER_IN_USE)) {
                message = getString(R.string.login_error_user_in_use);
            } else if (TextUtils.equals(errCode, MatrixError.LOGIN_EMAIL_URL_NOT_YET)) {
                message = getString(R.string.login_error_login_email_not_yet);
            } else {
                message = matrixError.getLocalizedMessage();
            }

            Log.e(LOG_TAG, "## onFailureDuringAuthRequest(): Msg= \"" + message + "\"");

            if (displayInTil) {
                mLoginPasswordTextViewTil.setError(message);
            } else {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Parse the given bundle to check if it contains the email verification extra.
     * If yes, it initializes the LoginActivity to start in registration mode to finalize a registration
     * process that is in progress. This is mainly used when the LoginActivity
     * is triggered from the {@link VectorRegistrationReceiver}.
     *
     * @param aRegistrationBundle bundle to be parsed
     * @return true operation succeed, false otherwise
     */
    private boolean processEmailValidationExtras(Bundle aRegistrationBundle) {
        boolean retCode = false;

        Log.d(LOG_TAG, "## processEmailValidationExtras() IN");

        if (null != aRegistrationBundle) {
            mEmailValidationExtraParams =
                    (HashMap<String, String>) aRegistrationBundle.getSerializable(VectorRegistrationReceiver.EXTRA_EMAIL_VALIDATION_PARAMS);

            if (null != mEmailValidationExtraParams) {
                // login was started in email validation mode
                mIsMailValidationPending = true;
                mMode = MODE_ACCOUNT_CREATION;
                Matrix.getInstance(this).clearSessions(this, true, null);
                retCode = true;
            }
        } else {
            Log.e(LOG_TAG, "## processEmailValidationExtras(): Bundle is missing - aRegistrationBundle=null");
        }
        Log.d(LOG_TAG, "## processEmailValidationExtras() OUT - reCode=" + retCode);
        return retCode;
    }


    /**
     * Perform an email validation for a registration flow. One account has been created where
     * a mail was provided. To validate the email ownership a MX submitToken REST api call must be performed.
     *
     * @param aMapParams map containing the parameters
     */
    private void startEmailOwnershipValidation(Map<String, String> aMapParams) {
        Log.d(LOG_TAG, "## startEmailOwnershipValidation(): IN aMapParams=" + aMapParams);

        if (null != aMapParams) {
            // display waiting UI..
            enableLoadingScreen(true);

            // display wait screen with no text (same as iOS) for now..
            hideMainLayoutAndToast("");

            // set register mode
            mMode = MODE_ACCOUNT_CREATION;

            String token = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_TOKEN);
            String clientSecret = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_CLIENT_SECRET);
            String identityServerSessId = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_IDENTITY_SERVER_SESSION_ID);
            String sessionId = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_SESSION_ID);
            String homeServer = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_HOME_SERVER_URL);
            String identityServer = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_IDENTITY_SERVER_URL);

            // When the user tries to update his/her password after forgetting it (tap on the dedicated link)
            // The HS / IS urls are not provided in the email link.
            // This link should be only opened by the webclient (known issue server side)
            // Use the current configuration by default (it might not work on some account if the user uses another HS)
            if (null == homeServer) {
                homeServer = getHomeServerUrl();
            }

            if (null == identityServer) {
                identityServer = getIdentityServerUrl();
            }

            // test if the home server urls are valid
            try {
                Uri.parse(homeServer);
                Uri.parse(identityServer);
            } catch (Exception e) {
                Toast.makeText(this, R.string.login_error_invalid_home_server, Toast.LENGTH_SHORT).show();
                return;
            }

            submitEmailToken(token, clientSecret, identityServerSessId, sessionId, homeServer, identityServer);
        } else {
            Log.d(LOG_TAG, "## startEmailOwnershipValidation(): skipped");
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
    private void submitEmailToken(final String aToken,
                                  final String aClientSecret,
                                  final String aSid,
                                  final String aSessionId,
                                  final String aHomeServer,
                                  final String aIdentityServer) {
        final HomeServerConnectionConfig homeServerConfig = mHomeserverConnectionConfig =
                new HomeServerConnectionConfig.Builder()
                        .withHomeServerUri(Uri.parse(aHomeServer))
                        .withIdentityServerUri(Uri.parse(aIdentityServer))
                        .build();

        mRegistrationManager.setHsConfig(homeServerConfig);
        Log.d(LOG_TAG, "## submitEmailToken(): IN");

        if (mMode == MODE_ACCOUNT_CREATION) {
            Log.d(LOG_TAG, "## submitEmailToken(): calling submitEmailTokenValidation()..");
            mLoginHandler.submitEmailTokenValidation(getApplicationContext(), homeServerConfig, aToken, aClientSecret, aSid, new ApiCallback<Boolean>() {
                private void errorHandler(String errorMessage) {
                    Log.d(LOG_TAG, "## submitEmailToken(): errorHandler().");
                    enableLoadingScreen(false);
                    setActionButtonsEnabled(false);
                    showMainLayout();
                    refreshDisplay(true);
                    Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSuccess(Boolean isSuccess) {
                    if (isSuccess) {
                        // if aSessionId is null, it means that this request has been triggered by clicking on a "forgot password" link
                        if (null == aSessionId) {
                            Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onSuccess() - the password update is in progress");

                            mMode = MODE_FORGOT_PASSWORD_WAITING_VALIDATION;

                            mForgotPid = new ThreePidCredentials();
                            mForgotPid.clientSecret = aClientSecret;
                            mForgotPid.idServer = Uri.parse(aIdentityServer).getHost();
                            mForgotPid.sid = aSid;

                            mIsPasswordReset = false;
                            onForgotOnEmailValidated(homeServerConfig);
                        } else {
                            // the validation of mail ownership succeed, just resume the registration flow
                            // next step: just register
                            Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onSuccess() - registerAfterEmailValidations() started");
                            mMode = MODE_ACCOUNT_CREATION;
                            enableLoadingScreen(true);
                            mRegistrationManager.registerAfterEmailValidation(LoginActivity.this,
                                    aClientSecret,
                                    aSid,
                                    aIdentityServer,
                                    aSessionId,
                                    LoginActivity.this);
                        }
                    } else {
                        Log.d(LoginActivity.LOG_TAG, "## submitEmailToken(): onSuccess() - failed (success=false)");
                        errorHandler(getString(R.string.login_error_unable_register_mail_ownership));
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
     * Check if the client supports the registration kind.
     */
    private void onRegistrationFlow() {
        enableLoadingScreen(false);
        setActionButtonsEnabled(true);

        // Check whether all listed flows in this authentication session are supported
        // We suggest using the fallback page (if any), when at least one flow is not supported.
        if (mRegistrationManager.hasNonSupportedStage() || alwaysUseFallback()) {
            String hs = getHomeServerUrl();
            boolean validHomeServer = false;

            try {
                Uri hsUri = Uri.parse(hs);
                validHomeServer = "http".equals(hsUri.getScheme()) || "https".equals(hsUri.getScheme());
            } catch (Exception e) {
                Log.e(LOG_TAG, "## Exception: " + e.getMessage(), e);
            }

            if (!validHomeServer) {
                Toast.makeText(LoginActivity.this, getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = FallbackAuthenticationActivity.Companion.getIntentToRegister(this, hs);
            startActivityForResult(intent, RequestCodesKt.FALLBACK_AUTHENTICATION_ACTIVITY_REQUEST_CODE);
        }
    }

    /**
     * Start a mail validation if required.
     */
    private void checkIfMailValidationPending() {
        Log.d(LOG_TAG, "## checkIfMailValidationPending(): mIsMailValidationPending=" + mIsMailValidationPending);

        if (!mRegistrationManager.hasRegistrationResponse()) {
            Log.d(LOG_TAG, "## checkIfMailValidationPending(): pending mail validation delayed (mRegistrationResponse=null)");
        } else if (mIsMailValidationPending) {
            mIsMailValidationPending = false;

            // remove the pending polling register if any
            cancelEmailPolling();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (null != mEmailValidationExtraParams) {
                        startEmailOwnershipValidation(mEmailValidationExtraParams);
                    }
                }
            });
        } else {
            Log.d(LOG_TAG, "## checkIfMailValidationPending(): pending mail validation not started");
        }
    }

    /**
     * Check the homeserver flows.
     * i.e checks if this registration page is enough to perform a registration.
     * else switch to a fallback page
     */
    private void checkRegistrationFlows() {
        Log.d(LOG_TAG, "## checkRegistrationFlows(): IN");
        // should only check registration flows
        if (mMode != MODE_ACCOUNT_CREATION) {
            return;
        }

        if (!mRegistrationManager.hasRegistrationResponse()) {
            try {
                final HomeServerConnectionConfig hsConfig = getHsConfig();

                // invalid URL
                if (null == hsConfig) {
                    setActionButtonsEnabled(false);
                } else {
                    enableLoadingScreen(true);

                    mLoginHandler.getSupportedRegistrationFlows(this, hsConfig, new SimpleApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void avoid) {
                            // should never be called
                        }

                        private void onError(String errorMessage) {
                            // should not check login flows
                            if (mMode == MODE_ACCOUNT_CREATION) {
                                showMainLayout();
                                enableLoadingScreen(false);
                                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            addNetworkStateNotificationListener();
                            if (mMode == MODE_ACCOUNT_CREATION) {
                                Log.e(LOG_TAG, "Network Error: " + e.getMessage(), e);
                                onError(getString(R.string.login_error_registration_network_error) + " : " + e.getLocalizedMessage());
                                setActionButtonsEnabled(false);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            if (mMode == MODE_ACCOUNT_CREATION) {
                                if (e instanceof HttpException
                                        && ((HttpException) e).getHttpError().getHttpCode() == HttpsURLConnection.HTTP_BAD_METHOD /* 405 */) {
                                    // Registration is not allowed
                                    onRegistrationNotAllowed();
                                } else {
                                    onError(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                                }
                            }
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            removeNetworkStateNotificationListener();

                            if (mMode == MODE_ACCOUNT_CREATION) {
                                Log.d(LOG_TAG, "## checkRegistrationFlows(): onMatrixError - Resp=" + e.getLocalizedMessage());
                                RegistrationFlowResponse registrationFlowResponse = null;

                                // when a response is not completed the server returns an error message
                                if (null != e.mStatus) {
                                    if (e.mStatus == 401) {
                                        try {
                                            registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                                        } catch (Exception castExcept) {
                                            Log.e(LOG_TAG, "JsonUtils.toRegistrationFlowResponse " + castExcept.getLocalizedMessage(), castExcept);
                                        }
                                    } else if (e.mStatus == 403) {
                                        onRegistrationNotAllowed();
                                    }
                                }

                                if (null != registrationFlowResponse) {
                                    mRegistrationManager.setSupportedRegistrationFlows(registrationFlowResponse);
                                    onRegistrationFlow();
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
                enableLoadingScreen(false);
            }
        } else {
            setActionButtonsEnabled(true);
        }
    }

    private void onRegistrationNotAllowed() {
        // Registration not supported by the server
        mMode = MODE_LOGIN;
        refreshDisplay(true);

        mSwitchToRegisterButton.setVisibility(View.GONE);
    }

    /**
     * Hide the main layout and display a toast.
     *
     * @param text the text helper
     */
    private void hideMainLayoutAndToast(String text) {
        mMainLayout.setVisibility(View.GONE);
        mProgressTextView.setVisibility(View.VISIBLE);
        mProgressTextView.setText(text);
        mButtonsView.setVisibility(View.GONE);
    }

    /**
     * Show the main layout.
     */
    private void showMainLayout() {
        mMainLayout.setVisibility(View.VISIBLE);
        mProgressTextView.setVisibility(View.GONE);
        mButtonsView.setVisibility(View.VISIBLE);
    }

    /**
     * The user clicks on the switch to register button.
     */
    @OnClick(R.id.button_switch_to_register)
    void onSwitchToRegisterClick() {
        Log.d(LOG_TAG, "## onSwitchToRegisterClick(): IN");
        onClick();

        // the user switches to another mode
        if (mMode != MODE_ACCOUNT_CREATION) {
            mMode = MODE_ACCOUNT_CREATION;
            refreshDisplay(true);
        }
    }

    @OnClick(R.id.button_login_sso)
    void openLoginFallback() {
        final HomeServerConnectionConfig hsConfig = getHsConfig();

        Intent intent = FallbackAuthenticationActivity.Companion
                .getIntentToLogin(LoginActivity.this, hsConfig.getHomeserverUri().toString());
        startActivityForResult(intent, RequestCodesKt.FALLBACK_AUTHENTICATION_ACTIVITY_REQUEST_CODE);
    }

    /**
     * The user clicks on the register button.
     */
    @OnClick(R.id.button_register)
    void onRegisterClick() {
        Log.d(LOG_TAG, "## onRegisterClick(): IN");
        onClick();

        // sanity check
        if (!mRegistrationManager.hasRegistrationResponse()) {
            Log.d(LOG_TAG, "## onRegisterClick(): return - mRegistrationResponse=null");
            return;
        }

        // parameters
        final String name = mCreationUsernameTextView.getText().toString().trim();
        final String password = mCreationPassword1TextView.getText().toString().trim();
        final String passwordCheck = mCreationPassword2TextView.getText().toString().trim();

        boolean hasError = false;

        if (TextUtils.isEmpty(name)) {
            mCreationUsernameTextViewTil.setError(getString(R.string.error_empty_field_enter_user_name));
            hasError = true;
        } else {
            String expression = "^[a-z0-9.\\-_]+$";

            Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(name);
            if (!matcher.matches()) {
                mCreationUsernameTextViewTil.setError(getString(R.string.auth_invalid_user_name));
                hasError = true;
            }
        }

        if (TextUtils.isEmpty(password)) {
            mCreationPassword1TextViewTil.setError(getString(R.string.auth_missing_password));
            hasError = true;
        } else if (password.length() < 6) {
            mCreationPassword1TextViewTil.setError(getString(R.string.auth_invalid_password));
            hasError = true;
        }

        if (!TextUtils.equals(password, passwordCheck)) {
            mCreationPassword2TextViewTil.setError(getString(R.string.auth_password_dont_match));
            hasError = true;
        }

        if (hasError) {
            return;
        }

        enableLoadingScreen(true);

        mRegistrationManager.setAccountData(name, password);
        mRegistrationManager.checkUsernameAvailability(this, this);
    }

    //==============================================================================================================
    // login management
    //==============================================================================================================

    /**
     * Dismiss the keyboard and save the updated values
     */
    private void onClick() {
        onIdentityServerUrlUpdate(false);
        onHomeServerUrlUpdate(false);

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mHomeServerText.getWindowToken(), 0);

        resetAllErrorsInForm();
    }

    /**
     * Reset all errors displayed in the textInputLayouts
     */
    private void resetAllErrorsInForm() {
        // Reset all inlined errors
        ViewUtilKt.resetTextInputLayoutErrors(mTextInputLayouts);
    }

    /**
     * The user clicks on the login button
     */
    @OnClick(R.id.button_switch_to_login)
    void onSwitchToLoginClick() {
        onClick();

        // the user switches to another mode
        if (mMode != MODE_LOGIN) {
            // end any pending registration UI
            showMainLayout();

            mMode = MODE_LOGIN;
            refreshDisplay(true);
        }
    }

    /**
     * The user clicks on the login button
     */
    @OnClick(R.id.button_login)
    void onLoginClick() {
        if (onHomeServerUrlUpdate(true) || onIdentityServerUrlUpdate(true)) {
            mIsPendingLogin = true;
            Log.d(LOG_TAG, "## onLoginClick() : The user taps on login but the IS/HS did not lose the focus");
            return;
        }

        onClick();

        mIsPendingLogin = false;

        final HomeServerConnectionConfig hsConfig = getHsConfig();
        final String hsUrlString = getHomeServerUrl();
        final String identityUrlString = getIdentityServerUrl();

        // --------------------- sanity tests for input values.. ---------------------
        if (!hsUrlString.startsWith("http")) {
            displayErrorOnUrl(mHomeServerTextTil, getString(R.string.login_error_must_start_http));
            return;
        }

        if (!identityUrlString.startsWith("http") && !TextUtils.isEmpty(identityUrlString)) {
            displayErrorOnUrl(mIdentityServerTextTil, getString(R.string.login_error_must_start_http));
            return;
        }

        final String username = mLoginEmailTextView.getText().toString().trim();
        final String phoneNumber = mLoginPhoneNumberHandler.getE164PhoneNumber();
        final String phoneNumberCountry = mLoginPhoneNumberHandler.getCountryCode();
        final String password = mLoginPasswordTextView.getText().toString().trim();

        boolean hasError = false;

        if (TextUtils.isEmpty(password)) {
            mLoginPasswordTextViewTil.setError(getString(R.string.error_empty_field_your_password));
            hasError = true;
        }

        if (TextUtils.isEmpty(username) && !mLoginPhoneNumberHandler.isPhoneNumberValidForCountry()) {
            // Check if phone number is empty or just invalid
            if (mLoginPhoneNumberHandler.getPhoneNumber() != null) {
                mLoginPhoneNumberTil.setError(getString(R.string.auth_invalid_phone));
                return;
            } else {
                mLoginEmailTextViewTil.setError(getString(R.string.auth_invalid_login_param));
                return;
            }
        }

        if (hasError) {
            return;
        }

        // disable UI actions
        enableLoadingScreen(true);

        login(hsConfig, hsUrlString, identityUrlString, username, phoneNumber, phoneNumberCountry, password);
    }

    /**
     * Make login request with given params
     *
     * @param hsConfig           the HS config
     * @param hsUrlString        the HS url
     * @param identityUrlString  the Identity server URL
     * @param username           the username
     * @param phoneNumber        the phone number
     * @param phoneNumberCountry the phone number country code
     * @param password           the user password
     */
    private void login(final HomeServerConnectionConfig hsConfig,
                       final String hsUrlString,
                       final String identityUrlString,
                       final String username,
                       final String phoneNumber,
                       final String phoneNumberCountry,
                       final String password) {
        try {
            mLoginHandler.login(this, hsConfig, username, phoneNumber, phoneNumberCountry, password, new SimpleApiCallback<Void>(this) {
                @Override
                public void onSuccess(Void avoid) {
                    enableLoadingScreen(false);

                    saveServerUrlsIfCustomValuesHasBeenEntered();

                    goToSplash();
                    finish();
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "onLoginClick : Network Error: " + e.getMessage(), e);
                    enableLoadingScreen(false);
                    Toast.makeText(getApplicationContext(), getString(R.string.login_error_network_error), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "onLoginClick : onUnexpectedError" + e.getMessage(), e);
                    enableLoadingScreen(false);
                    String msg = getString(R.string.login_error_unable_login) + " : " + e.getMessage();
                    Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    // if the registration is forbidden with matrix.org url
                    // try with the vector.im HS
                    if (TextUtils.equals(hsUrlString, getString(R.string.vector_im_server_url)) && TextUtils.equals(e.errcode, MatrixError.FORBIDDEN)) {
                        Log.e(LOG_TAG, "onLoginClick : test with matrix.org as HS");
                        mHomeserverConnectionConfig = new HomeServerConnectionConfig.Builder()
                                .withHomeServerUri(Uri.parse(getString(R.string.matrix_org_server_url)))
                                .withIdentityServerUri(Uri.parse(identityUrlString))
                                .build();

                        login(mHomeserverConnectionConfig,
                                getString(R.string.matrix_org_server_url),
                                identityUrlString,
                                username,
                                phoneNumber,
                                phoneNumberCountry,
                                password);
                    } else {
                        Log.e(LOG_TAG, "onLoginClick : onMatrixError " + e.getLocalizedMessage());
                        enableLoadingScreen(false);
                        onFailureDuringAuthRequest(e);
                    }
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
            enableLoadingScreen(false);
            setActionButtonsEnabled(true);
        }
    }

    /**
     * Check the homeserver flows.
     * i.e checks if this login page is enough to perform a registration.
     * else switch to a fallback page
     */
    private void checkLoginFlows() {
        // check only login flows
        if (mMode != MODE_LOGIN && mMode != MODE_FORGOT_PASSWORD) {
            return;
        }

        try {
            final HomeServerConnectionConfig hsConfig = getHsConfig();

            // invalid URL
            if (null == hsConfig) {
                setActionButtonsEnabled(false);
            } else {
                enableLoadingScreen(true);

                mLoginHandler.getSupportedLoginFlows(this, hsConfig, new SimpleApiCallback<List<LoginFlow>>() {
                    @Override
                    public void onSuccess(List<LoginFlow> flows) {
                        // stop listening to network state
                        removeNetworkStateNotificationListener();

                        if (mMode == MODE_LOGIN || mMode == MODE_FORGOT_PASSWORD) {
                            enableLoadingScreen(false);
                            setActionButtonsEnabled(true);

                            boolean isTypePasswordDetected = false;
                            boolean isSsoDetected = false;

                            for (LoginFlow flow : flows) {
                                switch (flow.type) {
                                    case LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD:
                                        isTypePasswordDetected = true;
                                        break;
                                    case LoginRestClient.LOGIN_FLOW_TYPE_SSO:
                                    case LoginRestClient.LOGIN_FLOW_TYPE_CAS:
                                        isSsoDetected = true;
                                        break;
                                    default:
                                        // Unsupported flow detected
                                        Log.w(LOG_TAG, "Unsupported login flow: " + flow.type);
                                        break;
                                }
                            }

                            if (isSsoDetected) {
                                // SSO has priority over password
                                mMode = MODE_LOGIN_SSO;
                                refreshDisplay(true);
                            } else if (isTypePasswordDetected) {
                                // In case we were previously in SSO mode
                                refreshDisplay(false);
                                if (mIsPendingLogin) {
                                    onLoginClick();
                                }
                            } else {
                                // if not supported, switch to the fallback login
                                openLoginFallback();
                            }
                        }
                    }

                    private void onError(String errorMessage) {
                        if (mMode == MODE_LOGIN || mMode == MODE_FORGOT_PASSWORD) {
                            enableLoadingScreen(false);
                            setActionButtonsEnabled(false);
                            displayErrorOnUrl(mHomeServerTextTil, errorMessage);
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "Network Error: " + e.getMessage(), e);

                        if (e instanceof UnknownHostException) {
                            onError(getString(R.string.login_error_unknown_host));
                        } else if (e instanceof SSLHandshakeException) {
                            onError(getString(R.string.login_error_ssl_handshake));
                        } else {
                            // listen to network state, to resume processing as soon as the network is back
                            addNetworkStateNotificationListener();
                            onError(getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        // Handle correctly the 404, the Matrix SDK should be patched later
                        if (e instanceof HttpException
                                && ((HttpException) e).getHttpError().getHttpCode() == HttpsURLConnection.HTTP_NOT_FOUND /* 404 */) {
                            onError(getString(R.string.login_error_homeserver_not_found));
                        } else {
                            onError(getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                        }
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onFailureDuringAuthRequest(e);
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
            enableLoadingScreen(false);
        }
    }

    //==============================================================================================================
    // Instance backup
    //==============================================================================================================

    /**
     * Restore the saved instance data.
     *
     * @param savedInstanceState the instance state
     */
    private void restoreSavedData(@NonNull Bundle savedInstanceState) {
        Log.d(LOG_TAG, "## restoreSavedData(): IN");

        mMode = savedInstanceState.getInt(SAVED_MODE, MODE_LOGIN);

        // check if the application has been opened by click on an url
        if (savedInstanceState.containsKey(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI)) {
            mUniversalLinkUri = savedInstanceState.getParcelable(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI);
        }

        mPendingEmailValidation = savedInstanceState.getParcelable(SAVED_CREATION_EMAIL_THREEPID);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
        Log.d(LOG_TAG, "## onSaveInstanceState(): IN");

        mRegistrationManager.saveInstanceState(savedInstanceState);

        // check if the application has been opened by click on an url
        if (null != mUniversalLinkUri) {
            savedInstanceState.putParcelable(VectorUniversalLinkReceiver.EXTRA_UNIVERSAL_LINK_URI, mUniversalLinkUri);
        }

        // check whether an email validation is in progress
        if (null != mRegisterPollingRunnable) {
            // Retrieve the current email three pid
            ThreePid email3pid = mRegistrationManager.getEmailThreePid();
            if (null != email3pid) {
                savedInstanceState.putParcelable(SAVED_CREATION_EMAIL_THREEPID, email3pid);
            }
        }

        savedInstanceState.putInt(SAVED_MODE, mMode);
    }

    //==============================================================================================================
    // Display management
    //==============================================================================================================

    /**
     * Refresh the visibility of mHomeServerText
     */
    private void refreshDisplay(boolean checkFlow) {
        // check if the device supported the dedicated mode
        if (checkFlow) {
            checkFlows();
        }

        TransitionManager.beginDelayedTransition(mMainContainer);

        // home server
        mHomeServerUrlsLayout.setVisibility(mUseCustomHomeServersCheckbox.isChecked() ? View.VISIBLE : View.GONE);

        // Hide views
        mLoginLayout.setVisibility(View.GONE);
        mCreationLayout.setVisibility(View.GONE);
        mForgetPasswordLayout.setVisibility(View.GONE);
        mThreePidLayout.setVisibility(View.GONE);

        // Hide text
        mPasswordForgottenTxtView.setVisibility(View.GONE);

        // Hide all buttons
        mLoginButton.setVisibility(View.GONE);
        mSwitchToRegisterButton.setVisibility(View.GONE);
        mLoginSsoButton.setVisibility(View.GONE);
        mRegisterButton.setVisibility(View.GONE);
        mSwitchToLoginButton.setVisibility(View.GONE);
        mForgotPasswordButton.setVisibility(View.GONE);
        mForgotValidateEmailButton.setVisibility(View.GONE);
        mSubmitThreePidButton.setVisibility(View.GONE);
        mSkipThreePidButton.setVisibility(View.GONE);

        mHomeServerOptionLayout.setVisibility(View.VISIBLE);

        // Then show them depending on mode
        switch (mMode) {
            case MODE_LOGIN:
                mLoginLayout.setVisibility(View.VISIBLE);
                mPasswordForgottenTxtView.setVisibility(View.VISIBLE);
                mLoginButton.setVisibility(View.VISIBLE);
                mSwitchToRegisterButton.setVisibility(View.VISIBLE);
                break;
            case MODE_LOGIN_SSO:
                mLoginSsoButton.setVisibility(View.VISIBLE);
                break;
            case MODE_ACCOUNT_CREATION:
                mCreationLayout.setVisibility(View.VISIBLE);
                mRegisterButton.setVisibility(View.VISIBLE);
                mSwitchToLoginButton.setVisibility(View.VISIBLE);
                break;
            case MODE_FORGOT_PASSWORD:
                mForgetPasswordLayout.setVisibility(View.VISIBLE);
                mForgotPasswordButton.setVisibility(View.VISIBLE);
                break;
            case MODE_FORGOT_PASSWORD_WAITING_VALIDATION:
                mForgotValidateEmailButton.setVisibility(View.VISIBLE);
                break;
            case MODE_ACCOUNT_CREATION_THREE_PID:
                mThreePidLayout.setVisibility(View.VISIBLE);
                mSubmitThreePidButton.setVisibility(View.VISIBLE);
                if (mRegistrationManager.canSkipThreePid()) {
                    mSkipThreePidButton.setVisibility(View.VISIBLE);
                }
                mHomeServerOptionLayout.setVisibility(View.GONE);
                break;
        }


        // update the button text to the current status
        // 1 - the user does not warn that he clicks on the email validation
        // 2 - the password has been reset and the user is invited to switch to the login screen
        mForgotValidateEmailButton.setText(mIsPasswordReset ? R.string.auth_return_to_login : R.string.auth_reset_password_next_step_button);
    }

    /**
     * Display a loading screen mask over the login screen
     *
     * @param isLoadingScreenVisible true to enable the loading screen, false otherwise
     */
    private void enableLoadingScreen(boolean isLoadingScreenVisible) {
        // disable register/login buttons when loading screen is displayed
        setActionButtonsEnabled(!isLoadingScreenVisible);

        if (isLoadingScreenVisible) {
            showWaitingView();
        } else {
            hideWaitingView();
        }
    }

    /**
     * @param enabled enabled/disabled the action buttons
     */
    private void setActionButtonsEnabled(boolean enabled) {
        mLoginButton.setEnabled(enabled);
        mSwitchToRegisterButton.setEnabled(enabled);
        mLoginSsoButton.setEnabled(enabled);
        mRegisterButton.setEnabled(enabled);
        mSwitchToLoginButton.setEnabled(enabled);
        mForgotPasswordButton.setEnabled(enabled);
        mForgotValidateEmailButton.setEnabled(enabled);
        mSubmitThreePidButton.setEnabled(enabled);
        mSkipThreePidButton.setEnabled(enabled);
    }

    //==============================================================================================================
    // extracted info
    //==============================================================================================================

    /**
     * Sanitize an URL
     *
     * @param url the url to sanitize
     * @return the sanitized url
     */
    private static String sanitizeUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return url;
        }

        return url.replaceAll("\\s", "");
    }

    /**
     * @return the homeserver config. null if the url is not valid
     */
    private HomeServerConnectionConfig getHsConfig() {
        if (null == mHomeserverConnectionConfig) {
            String hsUrlString = getHomeServerUrl();

            if (TextUtils.isEmpty(hsUrlString)
                    || !hsUrlString.startsWith("http")
                    || TextUtils.equals(hsUrlString, "http://")
                    || TextUtils.equals(hsUrlString, "https://")) {
                displayErrorOnUrl(mHomeServerTextTil, getString(R.string.login_error_must_start_http));
                return null;
            }

            if (!hsUrlString.startsWith("http://") && !hsUrlString.startsWith("https://")) {
                hsUrlString = "https://" + hsUrlString;
            }

            String identityServerUrlString = getIdentityServerUrl();
            if (!TextUtils.isEmpty(identityServerUrlString) && !identityServerUrlString.startsWith("http")
                    || TextUtils.equals(identityServerUrlString, "http://")
                    || TextUtils.equals(identityServerUrlString, "https://")) {
                displayErrorOnUrl(mIdentityServerTextTil, getString(R.string.login_error_must_start_http));
                return null;
            }

            if (!TextUtils.isEmpty(identityServerUrlString)
                    && !identityServerUrlString.startsWith("http://")
                    && !identityServerUrlString.startsWith("https://")) {
                identityServerUrlString = "https://" + identityServerUrlString;
            }

            try {
                mHomeserverConnectionConfig = null;
                mHomeserverConnectionConfig = new HomeServerConnectionConfig.Builder()
                        .withHomeServerUri(Uri.parse(hsUrlString))
                        .withIdentityServerUri(Uri.parse(identityServerUrlString))
                        .build();
            } catch (Exception e) {
                Log.e(LOG_TAG, "getHsConfig fails " + e.getLocalizedMessage(), e);
            }
        }

        mRegistrationManager.setHsConfig(mHomeserverConnectionConfig);
        return mHomeserverConnectionConfig;
    }

    private void displayErrorOnUrl(TextInputLayout textInputLayout, String error) {
        if (mUseCustomHomeServersCheckbox.isChecked()) {
            // Inline display
            textInputLayout.setError(error);
        } else {
            // Toast
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
        }
    }

    //==============================================================================================================
    // third party activities
    //==============================================================================================================

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(LOG_TAG, "## onActivityResult(): IN - requestCode=" + requestCode + " resultCode=" + resultCode);
        if (resultCode == RESULT_OK && requestCode == REQUEST_REGISTRATION_COUNTRY) {
            if (data != null && data.hasExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE) && mRegistrationPhoneNumberHandler != null) {
                mRegistrationPhoneNumberHandler.setCountryCode(data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE));
            }
        } else if (resultCode == RESULT_OK && requestCode == REQUEST_LOGIN_COUNTRY) {
            if (data != null && data.hasExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE) && mLoginPhoneNumberHandler != null) {
                mLoginPhoneNumberHandler.setCountryCode(data.getStringExtra(CountryPickerActivity.EXTRA_OUT_COUNTRY_CODE));
            }
        } else if (RequestCodesKt.CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE == requestCode) {
            if (resultCode == RESULT_OK) {
                Log.d(LOG_TAG, "## onActivityResult(): CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE => RESULT_OK");
                String captchaResponse = data.getStringExtra("response");
                mRegistrationManager.setCaptchaResponse(captchaResponse);
                createAccount();
            } else {
                Log.d(LOG_TAG, "## onActivityResult(): CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE => RESULT_KO");
                // cancel the registration flow
                mRegistrationManager.reset();
                fallbackToRegistrationMode();
            }
        } else if (RequestCodesKt.TERMS_CREATION_ACTIVITY_REQUEST_CODE == requestCode) {
            if (resultCode == RESULT_OK) {
                Log.d(LOG_TAG, "## onActivityResult(): TERMS_CREATION_ACTIVITY_REQUEST_CODE => RESULT_OK");
                mRegistrationManager.setTermsApproved();
                createAccount();
            } else {
                Log.d(LOG_TAG, "## onActivityResult(): TERMS_CREATION_ACTIVITY_REQUEST_CODE => RESULT_KO");
                // cancel the registration flow
                mRegistrationManager.reset();
                fallbackToRegistrationMode();
            }
        } else if (RequestCodesKt.FALLBACK_AUTHENTICATION_ACTIVITY_REQUEST_CODE == requestCode) {
            if (resultCode == RESULT_OK) {
                Log.d(LOG_TAG, "## onActivityResult(): FALLBACK_ACTIVITY => RESULT_OK");
                final HomeServerConnectionConfig hsConfig = getHsConfig();

                Credentials credentials = FallbackAuthenticationActivity.Companion.getResultCredentials(data);

                try {
                    hsConfig.setCredentials(credentials);
                } catch (Exception e) {
                    Log.d(LOG_TAG, "hsConfig setCredentials failed " + e.getLocalizedMessage());
                }

                // let's go...
                MXSession session = Matrix.getInstance(getApplicationContext()).createSession(hsConfig);
                Matrix.getInstance(getApplicationContext()).addSession(session);

                saveServerUrlsIfCustomValuesHasBeenEntered();

                goToSplash();
                finish();
            } else if (resultCode == RESULT_CANCELED) {
                Log.d(LOG_TAG, "## onActivityResult(): fallback cancelled");
                // reset the home server to let the user writes a valid one.
                mHomeserverConnectionConfig = null;
                mRegistrationManager.reset();

                if (mMode != MODE_LOGIN_SSO) {
                    mHomeServerText.setText(null);
                    setActionButtonsEnabled(false);
                    checkFlows();
                }
            }
        }
    }

    /*
     * *********************************************************************************************
     * Account creation - Threepid
     * *********************************************************************************************
     */

    /**
     * Init the view asking for email and/or phone number depending on supported registration flows
     */
    private void initThreePidView() {
        // Make sure to start with a clear state
        mRegistrationManager.clearThreePid();
        mEmailAddress.setText("");
        mRegistrationPhoneNumberHandler.reset();
        mEmailAddress.requestFocus();

        mThreePidInstructions.setText(mRegistrationManager.getThreePidInstructions(this));

        if (mRegistrationManager.supportStage(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
            mEmailAddress.setVisibility(View.VISIBLE);
            if (mRegistrationManager.isOptional(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                mEmailAddressTil.setHint(getString(R.string.auth_opt_email_placeholder));
            } else {
                mEmailAddressTil.setHint(getString(R.string.auth_email_placeholder));
            }
        } else {
            mEmailAddress.setVisibility(View.GONE);
        }

        if (mRegistrationManager.supportStage(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
            mRegistrationPhoneNumberHandler.setCountryCode(PhoneNumberUtils.getCountryCode(this));
            mPhoneNumberLayout.setVisibility(View.VISIBLE);
            if (mRegistrationManager.isOptional(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                mRegistrationPhoneNumberTil.setHint(getString(R.string.auth_opt_phone_number_placeholder));
            } else {
                mRegistrationPhoneNumberTil.setHint(getString(R.string.auth_phone_number_placeholder));
            }
        } else {
            mPhoneNumberLayout.setVisibility(View.GONE);
        }

        if (mRegistrationManager.canSkipThreePid()) {
            mSkipThreePidButton.setVisibility(View.VISIBLE);
            mSkipThreePidButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Make sure no three pid is attached to the process
                    mRegistrationManager.clearThreePid();
                    createAccount();
                    mRegistrationPhoneNumberHandler.reset();
                    mEmailAddress.setText("");
                }
            });
        } else {
            mSkipThreePidButton.setVisibility(View.GONE);
        }
    }

    /**
     * Submit the three pids
     */
    @OnClick(R.id.button_submit_three_pid)
    void submitThreePids() {
        dismissKeyboard(this);

        // Make sure to start with a clear state in case user already submitted before but canceled
        mRegistrationManager.clearThreePid();

        // Check that email format is valid and not empty if field is required
        final String email = mEmailAddress.getText().toString();
        if (!TextUtils.isEmpty(email)) {
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                mEmailAddressTil.setError(getString(R.string.auth_invalid_email));
                return;
            }
        } else if (mRegistrationManager.isEmailRequired()) {
            mEmailAddressTil.setError(getString(R.string.auth_missing_email));
            return;
        }

        // Check that phone number format is valid and not empty if field is required
        if (mRegistrationPhoneNumberHandler.getPhoneNumber() != null) {
            if (!mRegistrationPhoneNumberHandler.isPhoneNumberValidForCountry()) {
                mRegistrationPhoneNumberTil.setError(getString(R.string.auth_invalid_phone));
                return;
            }
        } else if (mRegistrationManager.isPhoneNumberRequired()) {
            mRegistrationPhoneNumberTil.setError(getString(R.string.auth_missing_phone));
            return;
        }

        if (!mRegistrationManager.canSkipThreePid() && mRegistrationPhoneNumberHandler.getPhoneNumber() == null && TextUtils.isEmpty(email)) {
            // Both are required and empty (previous error was R.string.auth_missing_email_or_phone)
            mEmailAddressTil.setError(getString(R.string.auth_missing_email));
            mRegistrationPhoneNumberTil.setError(getString(R.string.auth_missing_phone));
            return;
        }

        if (!TextUtils.isEmpty(email)) {
            // Communicate email to singleton (will be validated later on)
            mRegistrationManager.addEmailThreePid(ThreePid.Companion.fromEmail(email));
        }

        if (mRegistrationPhoneNumberHandler.getPhoneNumber() != null) {
            // Communicate phone number to singleton + start validation process (always phone first)
            enableLoadingScreen(true);
            mRegistrationManager
                    .addPhoneNumberThreePid(this, mRegistrationPhoneNumberHandler.getE164PhoneNumber(), mRegistrationPhoneNumberHandler.getCountryCode(),
                            new RegistrationManager.ThreePidRequestListener() {
                                @Override
                                public void onIdentityServerMissing() {
                                    LoginActivity.this.onIdentityServerMissing();
                                }

                                @Override
                                public void onThreePidRequested(ThreePid pid) {
                                    enableLoadingScreen(false);
                                    if (!TextUtils.isEmpty(pid.getSid())) {
                                        onPhoneNumberSidReceived(pid);
                                    }
                                }

                                @Override
                                public void onThreePidRequestFailed(String errorMessage) {
                                    LoginActivity.this.onThreePidRequestFailed(errorMessage);
                                }
                            });
        } else {
            createAccount();
        }
    }

    /**
     * Ask user the token received by SMS after phone number validation
     *
     * @param pid phone number pid
     */
    private void onPhoneNumberSidReceived(final ThreePid pid) {
        final View dialogLayout = getLayoutInflater().inflate(R.layout.dialog_phone_number_verification, null);
        mCurrentDialog = new AlertDialog.Builder(LoginActivity.this)
                .setView(dialogLayout)
                .setMessage(R.string.settings_phone_number_verification_instruction)
                .setPositiveButton(R.string.auth_submit, null)
                .setNegativeButton(R.string.cancel, null)
                .create();

        // Trick to prevent dialog being closed automatically when positive button is used
        mCurrentDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final TextInputEditText tokenView = dialogLayout.findViewById(R.id.phone_number_code_value);
                        submitPhoneNumber(tokenView.getText().toString(), pid);
                    }
                });
            }
        });

        mCurrentDialog.show();
    }

    /**
     * Submit the phone number token entered by the user
     *
     * @param token code entered by the user
     * @param pid   phone number pid
     */
    private void submitPhoneNumber(final String token, final ThreePid pid) {
        if (TextUtils.isEmpty(token)) {
            // TODO Display this error in the dialog
            Toast.makeText(LoginActivity.this, R.string.auth_invalid_token, Toast.LENGTH_SHORT).show();
        } else {
            mRegistrationManager.submitValidationToken(token, pid,
                    new RegistrationManager.ThreePidValidationListener() {
                        @Override
                        public void onThreePidValidated(boolean isSuccess) {
                            if (isSuccess) {
                                createAccount();
                            } else {
                                Toast.makeText(LoginActivity.this, R.string.auth_invalid_token, Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    /**
     * Start registration process
     */
    private void createAccount() {
        if (mCurrentDialog != null) {
            mCurrentDialog.dismiss();
        }
        enableLoadingScreen(true);
        hideMainLayoutAndToast("");
        mRegistrationManager.attemptRegistration(this, this);
    }

    /**
     * Cancel the polling for email validation
     */
    private void cancelEmailPolling() {
        mPendingEmailValidation = null;
        if (mHandler != null && mRegisterPollingRunnable != null) {
            mHandler.removeCallbacks(mRegisterPollingRunnable);
        }
    }

    /*
     * *********************************************************************************************
     * Account creation - Listeners
     * *********************************************************************************************
     */

    @Override
    public void onRegistrationSuccess(String warningMessage) {
        cancelEmailPolling();
        enableLoadingScreen(false);
        if (!TextUtils.isEmpty(warningMessage)) {
            mCurrentDialog = new AlertDialog.Builder(LoginActivity.this)
                    .setTitle(R.string.dialog_title_warning)
                    .setMessage(warningMessage)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            saveServerUrlsIfCustomValuesHasBeenEntered();

                            goToSplash();
                            finish();
                        }
                    })
                    .show();
        } else {
            saveServerUrlsIfCustomValuesHasBeenEntered();
            goToSplash();
            finish();
        }
    }

    @Override
    public void onRegistrationFailed(String message) {
        cancelEmailPolling();
        mEmailValidationExtraParams = null;
        Log.e(LOG_TAG, "## onRegistrationFailed(): " + message);
        showMainLayout();
        enableLoadingScreen(false);
        refreshDisplay(true);
        Toast.makeText(this, R.string.login_error_unable_register, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onWaitingEmailValidation() {
        Log.d(LOG_TAG, "## onWaitingEmailValidation");

        // Prompt the user to check his email
        hideMainLayoutAndToast(getString(R.string.auth_email_validation_message));
        enableLoadingScreen(true);

        // Loop to know whether the email has been checked
        mRegisterPollingRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(LOG_TAG, "## onWaitingEmailValidation attempt registration");
                mRegistrationManager.attemptRegistration(LoginActivity.this, LoginActivity.this);
                mHandler.postDelayed(mRegisterPollingRunnable, REGISTER_POLLING_PERIOD);
            }
        };
        mHandler.postDelayed(mRegisterPollingRunnable, REGISTER_POLLING_PERIOD);
    }

    @Override
    public void onIdentityServerMissing() {
        Log.d(LOG_TAG, "## onIdentityServerMissing()");
        enableLoadingScreen(false);
        showMainLayout();
        refreshDisplay(false);
        Toast.makeText(this, R.string.identity_server_not_defined, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onWaitingCaptcha(String publicKey) {
        cancelEmailPolling();
        if (!TextUtils.isEmpty(publicKey)) {
            Log.d(LOG_TAG, "## onWaitingCaptcha");
            Intent intent = new Intent(LoginActivity.this, AccountCreationCaptchaActivity.class);
            intent.putExtra(AccountCreationCaptchaActivity.EXTRA_HOME_SERVER_URL, mHomeServerUrl);
            intent.putExtra(AccountCreationCaptchaActivity.EXTRA_SITE_KEY, publicKey);
            startActivityForResult(intent, RequestCodesKt.CAPTCHA_CREATION_ACTIVITY_REQUEST_CODE);
        } else {
            Log.d(LOG_TAG, "## onWaitingCaptcha(): captcha flow cannot be done");
            Toast.makeText(this, R.string.login_error_unable_register, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onWaitingTerms(List<LocalizedFlowDataLoginTerms> localizedFlowDataLoginTerms) {
        cancelEmailPolling();
        if (!localizedFlowDataLoginTerms.isEmpty()) {
            Log.d(LOG_TAG, "## onWaitingTerms");
            Intent intent = AccountCreationTermsActivity.Companion.getIntent(this, localizedFlowDataLoginTerms);
            startActivityForResult(intent, RequestCodesKt.TERMS_CREATION_ACTIVITY_REQUEST_CODE);
        } else {
            Log.d(LOG_TAG, "## onWaitingTerms(): terms flow cannot be done");
            Toast.makeText(this, R.string.login_error_unable_register, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onThreePidRequestFailed(String message) {
        Log.d(LOG_TAG, "## onThreePidRequestFailed():" + message);
        enableLoadingScreen(false);
        showMainLayout();
        refreshDisplay(true);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onUsernameAvailabilityChecked(boolean isAvailable) {
        enableLoadingScreen(false);
        if (!isAvailable) {
            showMainLayout();
            mCreationUsernameTextViewTil.setError(getString(R.string.auth_username_in_use));
        } else {
            if (mRegistrationManager.canAddThreePid()) {
                // Show next screen with email/phone number
                showMainLayout();
                mMode = MODE_ACCOUNT_CREATION_THREE_PID;
                initThreePidView();
                refreshDisplay(true);
            } else {
                // Start registration
                createAccount();
            }
        }
    }

    @Override
    public void onResourceLimitExceeded(MatrixError e) {
        enableLoadingScreen(false);
        mResourceLimitDialogHelper.displayDialog(e);
    }

    /**
     * For test only, will always return false in production build
     */
    private boolean alwaysUseFallback() {
        if (BuildConfig.DEBUG) {
            // You can return true here, for test only, but never commit the change
            return false;
        }

        // Never edit this line.
        return false;
    }
}
