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
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import im.vector.LoginHandler;
import im.vector.Matrix;
import im.vector.R;

import java.util.List;

/**
 * Displays the login screen.
 */
public class LoginActivity extends MXCActionBarActivity {
    protected static final String TAG_FRAGMENT_SSL_FINGERPRINT = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_SSL_FINGERPRINT";

    private static final String LOG_TAG = "LoginActivity";
    static final int ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE = 314;
    static final int FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE = 315;

    // graphical items
    private Button mLoginButton;
    //private TextView mCreateAccountTxtView; // register a new user
    //private TextView mPasswordForgottenTxtView; // register a new user
    private EditText mHomeServerText;
    //private RelativeLayout mLogingMaskView; // used to display a UI mask on the screen

    String mHomeServerUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vector_login);

        // lock screen orientation in portrait
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

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
        }

        // bind UI widgets
        //mLogingMaskView = (RelativeLayout)findViewById(R.id.flow_ui_mask_login);
        mHomeServerText = (EditText) findViewById(R.id.login_matrix_server_url);
        //mCreateAccountTxtView = (TextView) findViewById(R.id.login_register_account);
        //mPasswordForgottenTxtView = (TextView) findViewById(R.id.login_forgot_password);
        mLoginButton = (Button)findViewById(R.id.button_login);

        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = ((EditText) findViewById(R.id.login_user_name)).getText().toString().trim();
                String password = ((EditText) findViewById(R.id.editText_password)).getText().toString().trim();
                String serverUrl = ((EditText) findViewById(R.id.login_matrix_server_url)).getText().toString().trim();
                onLoginClick(serverUrl, username, password);
            }
        });

        // account creation handler
        /*mCreateAccountTxtView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String hs = mHomeServerText.getText().toString();

                boolean validHomeServer = false;

                try {
                    Uri hsUri = Uri.parse(hs);
                    validHomeServer = "http".equals(hsUri.getScheme()) || "https".equals(hsUri.getScheme());
                } catch (Exception e) {
                    Log.w(LOG_TAG,"## Exception: "+e.getMessage());
                }

                if (!validHomeServer) {
                    Toast.makeText(LoginActivity.this, getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(LoginActivity.this, AccountCreationActivity.class);
                intent.putExtra(AccountCreationActivity.EXTRA_HOME_SERVER_ID, hs);
                startActivityForResult(intent, ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE);
            }
        });*/


        // home server input validity: if the user taps on the next / done button
        mHomeServerText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {

                    // the user validates the home server url
                    if (!TextUtils.equals(mHomeServerUrl, mHomeServerText.getText().toString())) {
                        mHomeServerUrl = mHomeServerText.getText().toString();
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
                        checkFlows();
                    }
                }
            }
        });

        // "forgot password?" handler
        /*mPasswordForgottenTxtView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(LoginActivity.this, "Not implemented..", Toast.LENGTH_SHORT).show();
            }
        });*/

    }

    @Override
    protected void onResume() {
        super.onResume();

        // retrieve the home server path
        mHomeServerUrl = mHomeServerText.getText().toString();

        // check if the login supports the server flows
        checkFlows();
    }

    private void onLoginClick(String hsUrlString, String username, String password) {
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
        // ---------------------------------------------------------------------------

        Uri hsUrl = Uri.parse(hsUrlString);
        final HomeserverConnectionConfig hsConfig = new HomeserverConnectionConfig(hsUrl);

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
            /*mLoginButton.setEnabled(true);
            mCreateAccountTxtView.setEnabled(true);*/
        }
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
                    Log.w(LOG_TAG,"## Exception: "+e.getMessage());
                }
            }
        });

        return false;
    }

    private void goToSplash() {
        Log.e(LOG_TAG, "Go to splash.");
        startActivity(new Intent(this, SplashActivity.class));
    }

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

    /**
     * display a loading screen mask over the login screen
     * @param aIsMaskEnabled true to enable the loading screen, false otherwise
     */
    private void setFlowsMaskEnabled(boolean aIsMaskEnabled) {
        // disable/enable login buttons
        setLoginButtonsEnabled(!aIsMaskEnabled);

        /*if(null != mLogingMaskView)
            mLogingMaskView.setVisibility(aIsMaskEnabled ? View.VISIBLE : View.GONE);*/
    }

    /**
     * @param enabled enabled/disabled the login buttons
     */
    private void setLoginButtonsEnabled(Boolean enabled) {
        mLoginButton.setEnabled(enabled);
        //mCreateAccountTxtView.setEnabled(enabled);

        mLoginButton.setAlpha(enabled ? 1.0f : 0.5f);
        //mCreateAccountTxtView.setAlpha(enabled ? 1.0f : 0.5f);
    }

    /**
     * Check the homeserver flows.
     * i.e checks if this login page is enough to perform a registration.
     * else switcth to a fallback page
     */
    private void checkFlows() {
        try {
            LoginHandler loginHandler = new LoginHandler();
            final HomeserverConnectionConfig hsConfig = getHsConfig();

            // invalid URL
            if (null == hsConfig) {
                setLoginButtonsEnabled(false);
            } else {
                setFlowsMaskEnabled(true);

                loginHandler.getSupportedFlows(LoginActivity.this, hsConfig, new SimpleApiCallback<List<LoginFlow>>() {
                    @Override
                    public void onSuccess(List<LoginFlow> flows) {
                        setFlowsMaskEnabled(false);
                        setLoginButtonsEnabled(true);
                        Boolean isSupported = true;

                        // supported only m.login.password by now
                        for(LoginFlow flow : flows) {
                            isSupported &= TextUtils.equals("m.login.password", flow.type);
                        }

                        // if not supported, switch to the fallback login
                        if (!isSupported) {
                            Intent intent = new Intent(LoginActivity.this, FallbackLoginActivity.class);
                            intent.putExtra(FallbackLoginActivity.EXTRA_HOME_SERVER_ID, hsConfig.getHomeserverUri().toString());
                            startActivityForResult(intent, FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE);
                        }
                    }

                    private void onError(String errorMessage) {
                        setLoginButtonsEnabled(false);
                        setFlowsMaskEnabled(false);
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
}
