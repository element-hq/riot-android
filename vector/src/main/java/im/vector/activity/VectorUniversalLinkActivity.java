/*
 * Copyright 2016 OpenMarket Ltd
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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;

import java.util.List;
import java.util.Map;

import im.vector.LoginHandler;
import im.vector.Matrix;
import im.vector.R;
import im.vector.receiver.LoginConfig;
import im.vector.receiver.VectorRegistrationReceiver;
import im.vector.receiver.VectorUniversalLinkReceiver;
import uk.co.chrisjenx.calligraphy.CalligraphyContextWrapper;

/**
 * Dummy activity used to dispatch the vector URL links.
 */
@SuppressLint("LongLogTag")
public class VectorUniversalLinkActivity extends VectorAppCompatActivity {
    private static final String LOG_TAG = VectorUniversalLinkActivity.class.getSimpleName();

    private static final String SUPPORTED_PATH_CONFIG = "/config/config";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(CalligraphyContextWrapper.wrap(newBase));
    }

    @Override
    public int getLayoutRes() {
        // display a spinner while binding the email
        return R.layout.activity_vector_universal_link_activity;
    }

    @Override
    public void initUiAndData() {
        configureToolbar();

        String intentAction = VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK;

        try {
            // dispatch on the right receiver
            if (VectorRegistrationReceiver.SUPPORTED_PATH_ACCOUNT_EMAIL_VALIDATION.equals(getIntent().getData().getPath())) {

                // We consider here an email validation
                Uri intentUri = getIntent().getData();

                Map<String, String> mailRegParams = VectorRegistrationReceiver.parseMailRegistrationLink(intentUri);

                // Assume it is a new account creation when there is a next link, or when no session is already available.
                MXSession session = Matrix.getInstance(this).getDefaultSession();
                if (mailRegParams.containsKey(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_NEXT_LINK) || (null == session)) {
                    if (null != session) {
                        Log.d(LOG_TAG, "## onCreate(): logout the current sessions, before finalizing an account creation based on an email validation");

                        // This logout is asynchronous, pursue the action in the callback to have the LoginActivity in a "no credentials state".
                        intentAction = null;
                        final Intent myBroadcastIntent = new Intent(this, VectorRegistrationReceiver.class);
                        myBroadcastIntent.setAction(VectorRegistrationReceiver.BROADCAST_ACTION_REGISTRATION);
                        myBroadcastIntent.setData(getIntent().getData());

                        CommonActivityUtils.logout(VectorUniversalLinkActivity.this,
                                Matrix.getMXSessions(VectorUniversalLinkActivity.this),
                                true,
                                new SimpleApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        Log.d(LOG_TAG, "## onCreate(): logout succeeded");
                                        sendBroadcast(myBroadcastIntent);
                                        finish();
                                    }
                                });
                    } else {
                        intentAction = VectorRegistrationReceiver.BROADCAST_ACTION_REGISTRATION;
                    }
                } else {
                    intentAction = null;
                    emailBinding(intentUri, mailRegParams);
                }
            } else if (SUPPORTED_PATH_CONFIG.equals(getIntent().getData().getPath())) {
                MXSession mSession = Matrix.getInstance(this).getDefaultSession();

                if (null == mSession) {
                    // user is not yet logged in, this is the nominal case
                    startLoginActivity();
                    return;
                } else {
                    displayAlreadyLoginPopup();
                    return;
                }
            } else {
                intentAction = VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK;
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "## onCreate(): Exception - Msg=" + ex.getMessage(), ex);
        }

        if (null != intentAction) {
            // since android O
            // set the class to avoid having "Background execution not allowed"
            Intent myBroadcastIntent = new Intent(this,
                    TextUtils.equals(intentAction, VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK) ?
                            VectorUniversalLinkReceiver.class : VectorRegistrationReceiver.class);

            myBroadcastIntent.setAction(intentAction);
            myBroadcastIntent.setData(getIntent().getData());
            sendBroadcast(myBroadcastIntent);
            finish();
        }
    }

    /**
     * Start the login screen with identity server and home server pre-filled
     */
    private void startLoginActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(LoginActivity.EXTRA_CONFIG, LoginConfig.Companion.parse(getIntent().getData()));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Propose to disconnect from a previous HS, when clicking on an auto config link
     */
    private void displayAlreadyLoginPopup() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_warning)
                .setMessage(R.string.error_user_already_logged_in)
                .setCancelable(false)
                .setPositiveButton(R.string.logout, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CommonActivityUtils.logout(VectorUniversalLinkActivity.this,
                                Matrix.getMXSessions(VectorUniversalLinkActivity.this),
                                true,
                                new SimpleApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        Log.d(LOG_TAG, "## displayAlreadyLoginPopup(): logout succeeded");
                                        startLoginActivity();
                                    }
                                });
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .show();
    }

    /**
     * Email binding management
     *
     * @param uri        the uri.
     * @param aMapParams the parsed params
     */
    private void emailBinding(Uri uri, Map<String, String> aMapParams) {
        Log.d(LOG_TAG, "## emailBinding()");

        String ISUrl = uri.getScheme() + "://" + uri.getHost();

        final HomeServerConnectionConfig homeServerConfig = new HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(ISUrl))
                .withIdentityServerUri(Uri.parse(ISUrl))
                .build();

        String token = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_TOKEN);
        String clientSecret = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_CLIENT_SECRET);
        String identityServerSessId = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_IDENTITY_SERVER_SESSION_ID);

        final LoginHandler loginHandler = new LoginHandler();

        loginHandler.submitEmailTokenValidation(getApplicationContext(), homeServerConfig, token, clientSecret, identityServerSessId,
                new ApiCallback<Boolean>() {

                    private void bringAppToForeground() {
                        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                        List<ActivityManager.RunningTaskInfo> tasklist = am.getRunningTasks(100);

                        if (!tasklist.isEmpty()) {
                            int nSize = tasklist.size();
                            for (int i = 0; i < nSize; i++) {
                                final ActivityManager.RunningTaskInfo taskinfo = tasklist.get(i);
                                if (taskinfo.topActivity.getPackageName().equals(getApplicationContext().getPackageName())) {
                                    Log.d(LOG_TAG, "## emailBinding(): bring the app in foreground.");
                                    am.moveTaskToFront(taskinfo.id, 0);
                                }
                            }
                        }

                        finish();
                    }

                    private void errorHandler(final String errorMessage) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                                bringAppToForeground();
                            }
                        });
                    }

                    @Override
                    public void onSuccess(Boolean isSuccess) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(LOG_TAG, "## emailBinding(): succeeds.");
                                bringAppToForeground();
                            }
                        });
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.d(LOG_TAG, "## emailBinding(): onNetworkError() Msg=" + e.getLocalizedMessage());
                        errorHandler(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.d(LOG_TAG, "## emailBinding(): onMatrixError() Msg=" + e.getLocalizedMessage());
                        errorHandler(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.d(LOG_TAG, "## emailBinding(): onUnexpectedError() Msg=" + e.getLocalizedMessage());
                        errorHandler(getString(R.string.login_error_unable_register) + " : " + e.getLocalizedMessage());
                    }
                });
    }
}