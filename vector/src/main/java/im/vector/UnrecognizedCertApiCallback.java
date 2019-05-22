/*
 * Copyright 2019 New Vector Ltd
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

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;

/**
 * Handle certificate errors in API callbacks
 */
public abstract class UnrecognizedCertApiCallback<T> extends SimpleApiCallback<T> {

    private HomeServerConnectionConfig mHsConfig;

    public UnrecognizedCertApiCallback(HomeServerConnectionConfig hsConfig, ApiCallback callback) {
        super(callback);
        mHsConfig = hsConfig;
    }

    public UnrecognizedCertApiCallback(HomeServerConnectionConfig hsConfig) {
        mHsConfig = hsConfig;
    }

    /**
     * The request failed because an unknown TLS certificate, yet the user accepted it
     * <p>
     * The usual behavior is to play the request again
     */
    public abstract void onAcceptedCert();

    /**
     * The request failed because of an unknown TLS certificate or a network error
     *
     * @param e
     */
    public void onTLSOrNetworkError(Exception e) {
        super.onNetworkError(e);
    }

    @Override
    public void onNetworkError(final Exception e) {
        if (!UnrecognizedCertHandler.handle(mHsConfig, e, new UnrecognizedCertHandler.Callback() {
            @Override
            public void onAccept() {
                onAcceptedCert();
            }

            @Override
            public void onIgnore() {
                onTLSOrNetworkError(e);
            }

            @Override
            public void onReject() {
                onTLSOrNetworkError(e);
            }
        })) {
            onTLSOrNetworkError(e);
        }
    }
}
