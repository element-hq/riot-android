/*
 * Copyright 2016 OpenMarket Ltd
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

import android.content.Context;
import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;
import org.matrix.androidsdk.util.Log;

import java.util.Collection;
import java.util.List;


public class LoginHandler {
    private static final String LOG_TAG = LoginHandler.class.getSimpleName();

    /**
     * The account login / creation succeeds so create the dedicated session and store it.
     *
     * @param appCtx      the application context.
     * @param hsConfig    the homeserver config
     * @param credentials the credentials
     * @param callback    the callback
     */
    private void onRegistrationDone(Context appCtx, HomeServerConnectionConfig hsConfig, Credentials credentials, SimpleApiCallback<HomeServerConnectionConfig> callback) {
        // sanity check - GA issue
        if (TextUtils.isEmpty(credentials.userId)) {
            callback.onMatrixError(new MatrixError(MatrixError.FORBIDDEN, "No user id"));
            return;
        }

        Collection<MXSession> sessions = Matrix.getMXSessions(appCtx);
        boolean isDuplicated = false;

        for (MXSession existingSession : sessions) {
            Credentials cred = existingSession.getCredentials();
            isDuplicated |= TextUtils.equals(credentials.userId, cred.userId) && TextUtils.equals(credentials.homeServer, cred.homeServer);
        }

        if (!isDuplicated) {
            hsConfig.setCredentials(credentials);
            MXSession session = Matrix.getInstance(appCtx).createSession(hsConfig);
            Matrix.getInstance(appCtx).addSession(session);
        }

        callback.onSuccess(hsConfig);
    }

    /**
     * Try to login.
     * The MXSession is created if the operation succeeds.
     *
     * @param ctx                the context.
     * @param hsConfig           The homeserver config.
     * @param username           The username.
     * @param phoneNumber        The phone number.
     * @param phoneNumberCountry The phone number country code.
     * @param password           The password;
     * @param callback           The callback.
     */
    public void login(Context ctx, final HomeServerConnectionConfig hsConfig, final String username,
                      final String phoneNumber, final String phoneNumberCountry, final String password,
                      final SimpleApiCallback<HomeServerConnectionConfig> callback) {
        final Context appCtx = ctx.getApplicationContext();

        callLogin(ctx, hsConfig, username, phoneNumber, phoneNumberCountry, password, new UnrecognizedCertApiCallback<Credentials>(hsConfig, callback) {
            @Override
            public void onSuccess(Credentials credentials) {
                onRegistrationDone(appCtx, hsConfig, credentials, callback);
            }

            @Override
            public void onAcceptedCert() {
                login(appCtx, hsConfig, username, phoneNumber, phoneNumberCountry, password, callback);
            }
        });
    }

    /**
     * Log the user using the given params after identifying if the login is a 3pid, a username or a phone number
     *
     * @param hsConfig
     * @param username
     * @param phoneNumber
     * @param phoneNumberCountry
     * @param password
     * @param callback
     */
    private void callLogin(final Context ctx, final HomeServerConnectionConfig hsConfig, final String username,
                           final String phoneNumber, final String phoneNumberCountry,
                           final String password, final SimpleApiCallback<Credentials> callback) {
        LoginRestClient client = new LoginRestClient(hsConfig);
        String deviceName = ctx.getString(R.string.login_mobile_device);

        if (!TextUtils.isEmpty(username)) {
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                // Login with 3pid
                client.loginWith3Pid(ThreePid.MEDIUM_EMAIL, username.toLowerCase(VectorApp.getApplicationLocale()), password, deviceName, callback);
            } else {
                // Login with user
                client.loginWithUser(username, password, deviceName, callback);
            }
        } else if (!TextUtils.isEmpty(phoneNumber) && !TextUtils.isEmpty(phoneNumberCountry)) {
            client.loginWithPhoneNumber(phoneNumber, phoneNumberCountry, password, deviceName, callback);
        }
    }

    /**
     * Retrieve the supported login flows of a home server.
     *
     * @param ctx      the application context.
     * @param hsConfig the home server config.
     * @param callback the supported flows list callback.
     */
    public void getSupportedLoginFlows(Context ctx, final HomeServerConnectionConfig hsConfig, final SimpleApiCallback<List<LoginFlow>> callback) {
        final Context appCtx = ctx.getApplicationContext();
        LoginRestClient client = new LoginRestClient(hsConfig);

        client.getSupportedLoginFlows(new UnrecognizedCertApiCallback<List<LoginFlow>>(hsConfig, callback) {
            @Override
            public void onAcceptedCert() {
                getSupportedLoginFlows(appCtx, hsConfig, callback);
            }
        });
    }

    /**
     * Retrieve the supported registration flows of a home server.
     *
     * @param ctx      the application context.
     * @param hsConfig the home server config.
     * @param callback the supported flows list callback.
     */
    public void getSupportedRegistrationFlows(Context ctx, final HomeServerConnectionConfig hsConfig, final SimpleApiCallback<HomeServerConnectionConfig> callback) {
        register(ctx, hsConfig, new RegistrationParams(), callback);
    }

    /**
     * Retrieve the supported registration flows of a home server.
     *
     * @param ctx      the application context.
     * @param hsConfig the home server config.
     * @param callback the supported flows list callback.
     */
    private void register(Context ctx, final HomeServerConnectionConfig hsConfig, final RegistrationParams params, final SimpleApiCallback<HomeServerConnectionConfig> callback) {
        final Context appCtx = ctx.getApplicationContext();
        LoginRestClient client = new LoginRestClient(hsConfig);

        // avoid dispatching the device name
        params.initial_device_display_name = ctx.getString(R.string.login_mobile_device);

        client.register(params, new UnrecognizedCertApiCallback<Credentials>(hsConfig, callback) {
            @Override
            public void onSuccess(Credentials credentials) {
                onRegistrationDone(appCtx, hsConfig, credentials, callback);
            }

            @Override
            public void onAcceptedCert() {
                getSupportedRegistrationFlows(appCtx, hsConfig, callback);
            }
        });
    }

    /**
     * Perform the validation of a mail ownership.
     *
     * @param aCtx              Android App context
     * @param aHomeServerConfig server configuration
     * @param aToken            the token
     * @param aClientSecret     the client secret
     * @param aSid              the server identity session id
     * @param aRespCallback     asynchronous callback response
     */
    public void submitEmailTokenValidation(final Context aCtx, final HomeServerConnectionConfig aHomeServerConfig,
                                           final String aToken, final String aClientSecret, final String aSid,
                                           final ApiCallback<Boolean> aRespCallback) {
        final ThreePid pid = new ThreePid(null, ThreePid.MEDIUM_EMAIL);
        ThirdPidRestClient restClient = new ThirdPidRestClient(aHomeServerConfig);

        pid.submitValidationToken(restClient, aToken, aClientSecret, aSid, new UnrecognizedCertApiCallback<Boolean>(aHomeServerConfig, aRespCallback) {
            @Override
            public void onAcceptedCert() {
                submitEmailTokenValidation(aCtx, aHomeServerConfig, aToken, aClientSecret, aSid, aRespCallback);
            }
        });
    }
}
