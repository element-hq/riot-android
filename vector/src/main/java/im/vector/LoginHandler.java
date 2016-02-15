package im.vector;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;

import java.util.Collection;
import java.util.List;


public class LoginHandler {
    private static final String LOG_TAG = "LoginHandler";

    /**
     * Try to login.
     * The MXSession is created if the operation succeeds.
     * @param ctx the context.
     * @param hsConfig The homeserver config.
     * @param username The username.
     * @param password The password;
     * @param callback The callback.
     */
    public void login(Context ctx, final HomeserverConnectionConfig hsConfig, final String username, final String password,
                              final SimpleApiCallback<HomeserverConnectionConfig> callback) {
        final Context appCtx = ctx.getApplicationContext();
        LoginRestClient client = new LoginRestClient(hsConfig);

        client.loginWithPassword(username, password, new SimpleApiCallback<Credentials>() {
            @Override
            public void onSuccess(Credentials credentials) {
                Collection<MXSession> sessions = Matrix.getMXSessions(appCtx);
                Boolean isDuplicated = false;

                for (MXSession existingSession : sessions) {
                    Credentials cred = existingSession.getCredentials();
                    isDuplicated |= TextUtils.equals(credentials.userId, cred.userId) && TextUtils.equals(credentials.homeServer, cred.homeServer);
                }

                if (!isDuplicated) {
                    hsConfig.setCredentials(credentials);
                    MXSession session = Matrix.getInstance(appCtx).createSession(hsConfig);
                    Matrix.getInstance(appCtx).addSession(session);
                }

                Log.d(LOG_TAG, "client loginWithPassword succeeded.");
                callback.onSuccess(hsConfig);
            }

            @Override
            public void onNetworkError(final Exception e) {
                UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                if (unrecCertEx != null) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();
                    Log.d(LOG_TAG, "Found fingerprint: SHA-256: " + fingerprint.getBytesAsHexString());
                    // TODO: Handle this. For example by displaying a "Do you trust this cert?" dialog

                    UnrecognizedCertHandler.show(hsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            login(appCtx, hsConfig, username, password, callback);
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
     * Retrieve the supported flows of a home server.
     * @param ctx the application conttext.
     * @param hsConfig the home server config.
     * @param callback the supported flows list callback.
     */
    public void getSupportedFlows(Context ctx, final HomeserverConnectionConfig hsConfig, final SimpleApiCallback<List<LoginFlow>> callback) {
        final Context appCtx = ctx.getApplicationContext();
        LoginRestClient client = new LoginRestClient(hsConfig);

        client.getSupportedFlows(new SimpleApiCallback<List<LoginFlow>>() {
            @Override
            public void onSuccess(List<LoginFlow> flows) {
                Log.d(LOG_TAG, "getSupportedFlows " + flows);
                callback.onSuccess(flows);
            }

            @Override
            public void onNetworkError(final Exception e) {
                UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                if (unrecCertEx != null) {
                    final Fingerprint fingerprint = unrecCertEx.getFingerprint();
                    Log.d(LOG_TAG, "Found fingerprint: SHA-256: " + fingerprint.getBytesAsHexString());

                    UnrecognizedCertHandler.show(hsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                        @Override
                        public void onAccept() {
                            getSupportedFlows(appCtx, hsConfig, callback);
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

}
