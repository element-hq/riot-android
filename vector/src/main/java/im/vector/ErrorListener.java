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
package im.vector;

import android.app.Activity;

import org.matrix.androidsdk.util.Log;

import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;

import java.util.Arrays;

import im.vector.activity.CommonActivityUtils;
import im.vector.store.LoginStorage;

/**
 * Listen to error threw by the Matrix client when querying the API
 */
public class ErrorListener implements ApiFailureCallback {
    private static final String LOG_TAG = ErrorListener.class.getSimpleName();

    private final Activity mActivity;
    private final MXSession mSession;

    public ErrorListener(MXSession session, Activity activity) {
        mSession = session;
        mActivity = activity;
    }

    @Override
    public void onNetworkError(final Exception e) {
        Log.e(LOG_TAG, "Network error: " + e.getMessage());

        // Do not trigger toaster if the application is in background
        if (!VectorApp.isAppInBackground()) {
            UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
            if (unrecCertEx == null) {
                handleNetworkError(e);
            }
            else {
                handleCertError(unrecCertEx, e);
            }

        }
    }

    @Override
    public void onMatrixError(MatrixError e) {
        Log.e(LOG_TAG, "Matrix error: " + e.errcode + " - " + e.error);

        // The access token was not recognized: log out
        if (MatrixError.UNKNOWN_TOKEN.equals(e.errcode)) {
            CommonActivityUtils.logout(mActivity);
        }
    }

    @Override
    public void onUnexpectedError(Exception e) {
        Log.e(LOG_TAG, "Unexpected error: " + e.getMessage());
    }

    private void handleNetworkError(Exception e) {
        mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
            Toast.makeText(mActivity, mActivity.getString(R.string.network_error), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleCertError(UnrecognizedCertificateException unrecCertEx, final Exception e) {
        final Fingerprint fingerprint = unrecCertEx.getFingerprint();
        Log.d(LOG_TAG, "Found fingerprint: SHA-256: " + fingerprint.getBytesAsHexString());

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
            UnrecognizedCertHandler.show(mSession.getHomeServerConfig(), fingerprint, true, new UnrecognizedCertHandler.Callback() {
                @Override
                public void onAccept() {
                    LoginStorage loginStorage = Matrix.getInstance(mActivity.getApplicationContext()).getLoginStorage();
                    loginStorage.replaceCredentials(mSession.getHomeServerConfig());
                }

                @Override
                public void onIgnore() {
                    handleNetworkError(e);
                }

                @Override
                public void onReject() {
                    CommonActivityUtils.logout(mActivity, Arrays.asList(mSession), true, null);
                }
            });
            }
        });
    }
}