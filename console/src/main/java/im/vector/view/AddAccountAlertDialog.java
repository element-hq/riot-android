/*
 * Copyright 2014 OpenMarket Ltd
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
package im.vector.view;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import im.vector.LoginHandler;
import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.FallbackLoginActivity;
import im.vector.activity.SplashActivity;

import java.util.List;

/**
 * Dialog to add an account.
 * The caller activity must implement onActivityResult
 * to provide the registration data to onFlowActivityResult.
 */
public class AddAccountAlertDialog extends AlertDialog.Builder  {
    // the onActivityResult identitier
    public static final int FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE = 2718;

    // UI items
    Activity mActivity;
    EditText mUsernameEditText;
    EditText mPasswordEditText;
    EditText mHomeServerEditText;
    View mSearchMaskView;
    AlertDialog mDialog;

    // the latest home server URL.
    String mHomeServerUrl;

    /**
     * Creator.
     * @param activity the activity creator
     */
    public AddAccountAlertDialog(Activity activity) {
        super(activity);
        mActivity = activity;

        LayoutInflater layoutInflater = LayoutInflater.from(activity);

        // extract the layout
        View layout = layoutInflater.inflate(R.layout.fragment_dialog_add_account, null);

        // and the useful UI items
        mUsernameEditText = (EditText) layout.findViewById(R.id.editText_username);
        mPasswordEditText = (EditText) layout.findViewById(R.id.editText_password);
        mHomeServerEditText = (EditText) layout.findViewById(R.id.editText_hs);
        mSearchMaskView = layout.findViewById(R.id.search_mask_view);
        mSearchMaskView.setVisibility(View.GONE);
        mHomeServerUrl = mHomeServerEditText.getText().toString();

        // detect if the user taps on the next / done button
        mHomeServerEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    return onHomeServerUrlUpdate();
                }
                return false;
            }
        });

        // disable the OK if the home server url is not the same
        mHomeServerEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(TextUtils.equals(mHomeServerUrl, mHomeServerEditText.getText().toString()));
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });


        // detect the focus changes
        mHomeServerEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    onHomeServerUrlUpdate();
                }
            }
        });

        // dialog title and layout
        setTitle(R.string.action_add_account);
        setView(layout);

        // buttons management.
        setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                onLogin();
            }
        });

        setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {

            }
        });
    }

    /**
     * Create and show the dialog.
     * @return the shown dialog.
     */
    public AlertDialog show() {
        mDialog = super.show();
        return mDialog;
    }

    /**
     * Check if the home server URL after its edition.
     * The flows request is triggered to check if the dialog supports the login.
     * The fallback activity is launched if it is not supported.
     * @return true if a flows check request is triggered.
     */
    private Boolean onHomeServerUrlUpdate() {
        // the user validates the homeserver url
        if (!TextUtils.equals(mHomeServerUrl, mHomeServerEditText.getText().toString())) {
            mHomeServerUrl = mHomeServerEditText.getText().toString();
            checkFlows();
            return true;
        } else {
            mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            return false;
        }
    }

    /**
     * Trigger a flows check request.
     */
    private void checkFlows() {
        // warn the user there is something in progress
        mSearchMaskView.setVisibility(View.VISIBLE);
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        try {
            // build the home server config
            HomeserverConnectionConfig hsConfig = null;
            try {
                hsConfig = new HomeserverConnectionConfig(Uri.parse(mHomeServerUrl));
            } catch (Exception e) {
            }

            LoginHandler loginHandler = new LoginHandler();

            // sanity check
            if (null != hsConfig) {{
                loginHandler.getSupportedFlows(mActivity, hsConfig, new SimpleApiCallback<List<LoginFlow>>() {
                    @Override
                    public void onSuccess(List<LoginFlow> flows) {
                        mSearchMaskView.setVisibility(View.GONE);
                        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Boolean isSupported = true;

                        // supported only m.login.password by now
                        for(LoginFlow flow : flows) {
                            isSupported &= TextUtils.equals("m.login.password", flow.type);
                        }

                        // if not supported, switch to the fallback login
                        if (!isSupported) {
                            mDialog.dismiss();

                            Intent intent = new Intent(mActivity, FallbackLoginActivity.class);
                            intent.putExtra(FallbackLoginActivity.EXTRA_HOME_SERVER_ID, mHomeServerUrl);
                            mActivity.startActivityForResult(intent, FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE);
                        }
                    }

                    private void onError(String errorMessage) {
                        mSearchMaskView.setVisibility(View.GONE);
                        Toast.makeText(mActivity, errorMessage, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError(mActivity.getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError(mActivity.getString(R.string.login_error_unable_login) + " : " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onError(mActivity.getString(R.string.login_error_unable_login) + " : " + e.error + "(" + e.errcode + ")");
                    }
                });
            }
            }
        } catch (Exception globalExcep) {
            mSearchMaskView.setVisibility(View.GONE);
            Toast.makeText(mActivity, mActivity.getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The user triggers a login.
     */
    private void onLogin() {
        String hsUrlString = mHomeServerEditText.getText().toString();
        String username = mUsernameEditText.getText().toString();
        String password = mPasswordEditText.getText().toString();

        if (!hsUrlString.startsWith("http")) {
            Toast.makeText(mActivity, mActivity.getString(R.string.login_error_must_start_http), Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(mActivity, mActivity.getString(R.string.login_error_invalid_credentials), Toast.LENGTH_SHORT).show();
            return;
        }

        Uri hsUrl = Uri.parse(hsUrlString);

        LoginRestClient client = null;
        final HomeserverConnectionConfig hsConfig = new HomeserverConnectionConfig(hsUrl);

        try {
            client = new LoginRestClient(hsConfig);
        } catch (Exception e) {
        }

        if (null == client) {
            Toast.makeText(mActivity, mActivity.getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            LoginHandler loginHandler = new LoginHandler();
            loginHandler.login(mActivity, hsConfig, username, password, new SimpleApiCallback<HomeserverConnectionConfig>(mActivity) {
                @Override
                public void onSuccess(HomeserverConnectionConfig c) {
                    // loginHandler creates the session so just need to switch to the splash activity
                    mActivity.startActivity(new Intent(mActivity, SplashActivity.class));
                    mActivity.finish();
                }

                @Override
                public void onNetworkError(Exception e) {
                    Toast.makeText(mActivity, mActivity.getString(R.string.login_error_network_error), Toast.LENGTH_LONG).show();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    String msg = mActivity.getString(R.string.login_error_unable_login) + " : " + e.getMessage();
                    Toast.makeText(mActivity, msg, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    String msg = mActivity.getString(R.string.login_error_unable_login) + " : " + e.error + "(" + e.errcode + ")";
                    Toast.makeText(mActivity, msg, Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(mActivity, mActivity.getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * onActivityResult parameters management
     * @param activity the caller activity.
     * @param requestCode the request code (should be FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE)
     * @param resultCode the result code
     * @param data the data
     */
    public static void onFlowActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (AddAccountAlertDialog.FALLBACK_LOGIN_ACTIVITY_REQUEST_CODE == requestCode) {
            if (resultCode == Activity.RESULT_OK) {
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

                // let's go...
                MXSession session = Matrix.getInstance(activity).createSession(hsConfig);
                Matrix.getInstance(activity).addSession(session);

                // loginHandler creates the session so just need to switch to the splash activity
                activity.startActivity(new Intent(activity, SplashActivity.class));
                activity.finish();
            }
        }
    }
}
