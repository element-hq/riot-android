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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import im.vector.LoginHandler;
import im.vector.Matrix;
import im.vector.R;
import im.vector.receiver.VectorRegistrationReceiver;
import im.vector.receiver.VectorUniversalLinkReceiver;

/**
 * Dummy activity used to dispatch the vector URL links.
 */
@SuppressLint("LongLogTag")
public class VectorUniversalLinkActivity extends VectorActivity {
    private static final String LOG_TAG = "VectorUniversalLinkActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String intentAction = VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK;

        try {
            // dispatch on the right receiver
            if (VectorRegistrationReceiver.SUPPORTED_PATH_ACCOUNT_EMAIL_VALIDATION.equals(getIntent().getData().getPath())) {

                Uri intentUri = getIntent().getData();
                // account registration URL set in a mail:
                HashMap<String, String> mailRegParams = VectorRegistrationReceiver.parseMailRegistrationLink(intentUri);

                // when there is a next link, assume it is a new account creation
                if (mailRegParams.containsKey(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_NEXT_LINK) || (null == Matrix.getInstance(this).getDefaultSession())) {
                    // logout current session, before starting any mail validation
                    // to have the LoginActivity always in a "no credentials state".
                    CommonActivityUtils.logout(this, false);
                    intentAction = VectorRegistrationReceiver.BROADCAST_ACTION_REGISTRATION;
                } else {
                    intentAction = null;
                    // display a spinner while binding the email
                    setContentView(R.layout.activity_vector_universal_link_activity);
                    emailBinding(intentUri, mailRegParams);
                }
            } else {
                intentAction = VectorUniversalLinkReceiver.BROADCAST_ACTION_UNIVERSAL_LINK;
            }
        } catch (Exception ex){
            Log.e(LOG_TAG,"## onCreate(): Exception - Msg="+ex.getMessage());
        }

        if (null != intentAction) {
            Intent myBroadcastIntent = new Intent(intentAction, getIntent().getData());
            sendBroadcast(myBroadcastIntent);
            finish();
        }
    }

    /**
     * Email binding management
     * @param uri the uri.
     * @param aMapParams the parsed params
     */
    private void emailBinding(Uri uri, HashMap<String, String> aMapParams) {
        Log.d(LOG_TAG,"## emailBinding()");

        String ISUrl = uri.getScheme() + "://" + uri.getHost();

        final HomeserverConnectionConfig homeServerConfig = new HomeserverConnectionConfig(Uri.parse(ISUrl), Uri.parse(ISUrl), null, new ArrayList<Fingerprint>(), false);

        String token = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_TOKEN);
        String clientSecret = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_CLIENT_SECRET);
        String identityServerSessId = aMapParams.get(VectorRegistrationReceiver.KEY_MAIL_VALIDATION_IDENTITY_SERVER_SESSION_ID);

        final LoginHandler loginHandler = new LoginHandler();

        loginHandler.submitEmailTokenValidation(getApplicationContext(), homeServerConfig, token, clientSecret, identityServerSessId, new ApiCallback<Boolean>() {

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
                VectorUniversalLinkActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), errorMessage, Toast.LENGTH_LONG).show();
                        bringAppToForeground();
                    }
                });
            }

            @Override
            public void onSuccess(Boolean isSuccess) {
                VectorUniversalLinkActivity.this.runOnUiThread(new Runnable() {
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
