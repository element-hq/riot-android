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

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;
import org.matrix.androidsdk.util.Log;

import java.util.Collection;
import java.util.List;


public class LoginHandler {
    private static final String LOG_TAG = "LoginHandler";

    /**
     * The account login / creation succeeds so create the dedicated session and store it.
     * @param appCtx the application context.
     * @param hsConfig the homeserver config
     * @param credentials the credentials
     * @param callback the callback
     */
    private void onRegistrationDone(Context appCtx, HomeserverConnectionConfig hsConfig, Credentials credentials, SimpleApiCallback<HomeserverConnectionConfig> callback) {
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
     * @param ctx the context.
     * @param hsConfig The homeserver config.
     * @param username The username.
     * @param phoneNumber The phone number.
     * @param phoneNumberCountry The phone number country code.
     * @param password The password;
     * @param callback The callback.
     */
    public void login(Context ctx, final HomeserverConnectionConfig hsConfig, final String username,
                      final String phoneNumber, final String phoneNumberCountry, final String password,
                      final SimpleApiCallback<HomeserverConnectionConfig> callback) {
        final Context appCtx = ctx.getApplicationContext();

        final SimpleApiCallback<Credentials> loginCallback = new SimpleApiCallback<Credentials>() {
            @Override
            public void onSuccess(Credentials credentials) {
                onRegistrationDone(appCtx, hsConfig, credentials, callback);
            }

            @Override
            public void onNetworkError(final Exception e) {
                UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                if (unrecCertEx != null) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();
                    UnrecognizedCertHandler.show(hsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            login(appCtx, hsConfig, username, phoneNumber, phoneNumberCountry, password, callback);
                        }

                        @Override
                        public void onIgnore() {
                            callback.onNetworkError(e);
                        }

                        @Override
                        public void onReject() {
                            callback.onNetworkError(e);
                        }
                    });
                } else {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }
        };

        callLogin(hsConfig, username, phoneNumber, phoneNumberCountry, password, loginCallback);
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
    private void callLogin(final HomeserverConnectionConfig hsConfig, final String username,
                           final String phoneNumber, final String phoneNumberCountry,
                           final String password, final SimpleApiCallback<Credentials> callback) {
        LoginRestClient client = new LoginRestClient(hsConfig);
        if (!TextUtils.isEmpty(username)) {
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                // Login with 3pid
                client.loginWith3Pid(ThreePid.MEDIUM_EMAIL, username.toLowerCase(), password, callback);
            } else {
                // Login with user
                client.loginWithUser(username, password, callback);
            }
        } else if (!TextUtils.isEmpty(phoneNumber) && !TextUtils.isEmpty(phoneNumberCountry)) {
            client.loginWithPhoneNumber(phoneNumber, phoneNumberCountry, password, callback);
        }
    }

    /**
     * Retrieve the supported login flows of a home server.
     * @param ctx the application context.
     * @param hsConfig the home server config.
     * @param callback the supported flows list callback.
     */
    public void getSupportedLoginFlows(Context ctx, final HomeserverConnectionConfig hsConfig, final SimpleApiCallback<List<LoginFlow>> callback) {
        final Context appCtx = ctx.getApplicationContext();
        LoginRestClient client = new LoginRestClient(hsConfig);

        client.getSupportedLoginFlows(new SimpleApiCallback<List<LoginFlow>>() {
            @Override
            public void onSuccess(List<LoginFlow> flows) {
                Log.d(LOG_TAG, "getSupportedLoginFlows " + flows);
                callback.onSuccess(flows);
            }

            @Override
            public void onNetworkError(final Exception e) {
                UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                if (unrecCertEx != null) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();

                    UnrecognizedCertHandler.show(hsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            getSupportedLoginFlows(appCtx, hsConfig, callback);
                        }

                        @Override
                        public void onIgnore() {
                            callback.onNetworkError(e);
                        }

                        @Override
                        public void onReject() {
                            callback.onNetworkError(e);
                        }
                    });
                } else {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }
        });
    }

    /**
     * Retrieve the supported registration flows of a home server.
     * @param ctx the application context.
     * @param hsConfig the home server config.
     * @param callback the supported flows list callback.
     */
    public void getSupportedRegistrationFlows(Context ctx, final HomeserverConnectionConfig hsConfig, final SimpleApiCallback<HomeserverConnectionConfig> callback) {
        register(ctx, hsConfig, new RegistrationParams(), callback);
    }

    /**
     * Retrieve the supported registration flows of a home server.
     * @param ctx the application context.
     * @param hsConfig the home server config.
     * @param callback the supported flows list callback.
     */
    public void register(Context ctx, final HomeserverConnectionConfig hsConfig, final RegistrationParams params, final SimpleApiCallback<HomeserverConnectionConfig> callback) {
        final Context appCtx = ctx.getApplicationContext();
        LoginRestClient client = new LoginRestClient(hsConfig);

        client.register(params, new SimpleApiCallback <Credentials> () {
            @Override
            public void onSuccess(Credentials credentials){
                onRegistrationDone(appCtx, hsConfig, credentials, callback);
            }

            @Override
            public void onNetworkError ( final Exception e){
                UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                if (unrecCertEx != null) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();
                    Log.d(LOG_TAG, "Found fingerprint: SHA-256: " + fingerprint.getBytesAsHexString());

                    UnrecognizedCertHandler.show(hsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            getSupportedRegistrationFlows(appCtx, hsConfig, callback);
                        }

                        @Override
                        public void onIgnore() {
                            callback.onNetworkError(e);
                        }

                        @Override
                        public void onReject() {
                            callback.onNetworkError(e);
                        }
                    });
                } else {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError (Exception e){
                callback.onUnexpectedError(e);
            }

            @Override
            public void onMatrixError (MatrixError e){
                callback.onMatrixError(e);
            }
        });
    }

    /**
     * Request a validation token for the given email
     * @param ctx the context.
     * @param hsConfig the homeserver configuration.
     * @param email the email.
     * @param session the session description
     * @param callback the callback.
     */
    public void requestEmailValidationToken(final Context ctx, final HomeserverConnectionConfig hsConfig,
                                            final String email, final String session,
                                            final SimpleApiCallback<ThreePid> callback) {
        final ThreePid pid = new ThreePid(email, ThreePid.MEDIUM_EMAIL);

        ThirdPidRestClient client = new ThirdPidRestClient(hsConfig);

        // TODO define an application settings
        String webAppUrl = "https://vector.im/develop";

        String nextLink = webAppUrl + "/#/register?client_secret=" + pid.clientSecret;
        nextLink += "&hs_url=" + hsConfig.getHomeserverUri().toString();
        nextLink += "&is_url=" + hsConfig.getIdentityServerUri().toString();
        nextLink += "&session_id=" + session;

        pid.requestEmailValidationToken(client, nextLink, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                callback.onSuccess(pid);
            }

            @Override
            public void onNetworkError(final Exception e) {
                UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                if (unrecCertEx != null) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();

                    UnrecognizedCertHandler.show(hsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            requestEmailValidationToken(ctx,hsConfig, email, session, callback);
                        }

                        @Override
                        public void onIgnore() {
                            callback.onNetworkError(e);
                        }

                        @Override
                        public void onReject() {
                            callback.onNetworkError(e);
                        }
                    });
                } else {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError (Exception e){
                callback.onUnexpectedError(e);
            }

            @Override
            public void onMatrixError (MatrixError e){
                callback.onMatrixError(e);
            }
        });
    }

    /**
     * Perform the validation of a mail ownership.
     * @param aCtx Android App context
     * @param aHomeServerConfig server configuration
     * @param aToken the token generated by {@link #requestEmailValidationToken(Context, HomeserverConnectionConfig, String, String, SimpleApiCallback)}
     * @param aClientSecret the client secret which was supplied by {@link #requestEmailValidationToken(Context, HomeserverConnectionConfig, String, String, SimpleApiCallback)}
     * @param aSid the server identity session id
     * @param aRespCallback asynchronous callback response
     */
    public void submitEmailTokenValidation(final Context aCtx, final HomeserverConnectionConfig aHomeServerConfig,
                                           final String aToken, final String aClientSecret, final String aSid,
                                           final ApiCallback<Boolean> aRespCallback) {
        final ThreePid pid = new ThreePid(null,  ThreePid.MEDIUM_EMAIL);
        ThirdPidRestClient restClient = new ThirdPidRestClient(aHomeServerConfig);

        pid.submitValidationToken(restClient, aToken, aClientSecret, aSid, new ApiCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isSuccess) {
                aRespCallback.onSuccess(isSuccess);
            }

            @Override
            public void onNetworkError(final Exception e) {
                UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                if (unrecCertEx != null) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();

                    UnrecognizedCertHandler.show(aHomeServerConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            submitEmailTokenValidation(aCtx, aHomeServerConfig, aToken, aClientSecret, aSid, aRespCallback);
                        }

                        @Override
                        public void onIgnore() {
                            aRespCallback.onNetworkError(e);
                        }

                        @Override
                        public void onReject() {
                            aRespCallback.onNetworkError(e);
                        }
                    });
                } else {
                    aRespCallback.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                aRespCallback.onUnexpectedError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                aRespCallback.onMatrixError(e);
            }
        });
    }
}
