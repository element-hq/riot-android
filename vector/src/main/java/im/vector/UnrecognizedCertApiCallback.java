package im.vector;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;

/**
 * Handle certificate errors in API callbacks
 */
public class UnrecognizedCertApiCallback<T> extends SimpleApiCallback<T> {

    private HomeServerConnectionConfig mHsConfig;
    private ApiCallback mCallback;

    public UnrecognizedCertApiCallback(HomeServerConnectionConfig hsConfig, ApiCallback callback) {
        super(callback);
        mHsConfig = hsConfig;
        mCallback = callback;
    }

    public UnrecognizedCertApiCallback(HomeServerConnectionConfig hsConfig) {
        mHsConfig = hsConfig;
    }

    /**
     * The request failed because an unknown TLS certificate, yet the user accepted it
     *
     * The usual behavior is to play the request again
     */
    public void onAcceptedCert() {

    }

    /**
     * The request failed because of an unknown TLS certificate or a network error
     * @param e
     */
    public void onTLSOrNetworkError(Exception e) {
        super.onNetworkError(e);
    }

    @Override
    public void onNetworkError(final Exception e) {
        if(!UnrecognizedCertHandler.handle(mHsConfig, e, new UnrecognizedCertHandler.Callback() {
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
