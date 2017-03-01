/*
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
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationResponse;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import im.vector.activity.CommonActivityUtils;

public class RegistrationManager {
    private static final String LOG_TAG = RegistrationManager.class.getSimpleName();

    private static volatile RegistrationManager sInstance;

    private static String ERROR_MISSING_STAGE = "ERROR_MISSING_STAGE";

    private static List<String> VECTOR_SUPPORTED_STAGES = Arrays.asList(
            LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD,
            LoginRestClient.LOGIN_FLOW_TYPE_DUMMY,
            LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY,
            LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);

    private HomeserverConnectionConfig mHsConfig;
    private LoginRestClient mLoginRestClient;
    private ThirdPidRestClient mThirdPidRestClient;

    private RegistrationFlowResponse mRegistrationResponse;

    private final Set<String> mSupportedStages = new HashSet<>();
    private final List<String> mRequiredStages = new ArrayList<>();
    private final List<String> mConditionalOptionalStages = new ArrayList<>();
    private final List<String> mOptionalStages = new ArrayList<>();

    // Params
    private String mUsername;
    private String mPassword;
    private ThreePid mEmail;
    private ThreePid mPhoneNumber;
    private String mCaptchaResponse;
    /*
    * *********************************************************************************************
    * Singleton
    * *********************************************************************************************
    */

    public static RegistrationManager getInstance() {
        if (sInstance == null) {
            sInstance = new RegistrationManager();
        }
        return sInstance;
    }

    private RegistrationManager() {
    }

    /*
    * *********************************************************************************************
    * Public methods
    * *********************************************************************************************
    */

    private void resetSingleton() {
        // Clear all data to allow new registration
        mHsConfig = null;
        mLoginRestClient = null;
        mThirdPidRestClient = null;
        mRegistrationResponse = null;

        mSupportedStages.clear();
        mRequiredStages.clear();
        mOptionalStages.clear();
        mConditionalOptionalStages.clear();

        mUsername = null;
        mPassword = null;
        mEmail = null;
        mPhoneNumber = null;
        mCaptchaResponse = null;
    }

    public String getHsServerUrl() {
        return mHsConfig != null ? mHsConfig.getHomeserverUri().toString() : "";
    }

    public void setHsConfig(final HomeserverConnectionConfig hsConfig) {
        mHsConfig = hsConfig;
    }

    public HomeserverConnectionConfig getHsConfig() {
        return mHsConfig;
    }

    public LoginRestClient getLoginRestClient() {
        if (mLoginRestClient == null && mHsConfig != null) {
            mLoginRestClient = new LoginRestClient(mHsConfig);
        }
        return mLoginRestClient;
    }

    public ThirdPidRestClient getThirdPidRestClient() {
        if (mThirdPidRestClient == null && mHsConfig != null) {
            mThirdPidRestClient = new ThirdPidRestClient(mHsConfig);
        }
        return mThirdPidRestClient;
    }

    public void setRegistrationFlowResponse(final RegistrationFlowResponse registrationFlowResponse) {
        if (registrationFlowResponse != null) {
            mRegistrationResponse = registrationFlowResponse;
            analyzeRegistrationStages();
        }
    }

    public RegistrationFlowResponse getRegistrationFlowResponse() {
        return mRegistrationResponse;
    }

    public ThreePid getEmail() {
        return mEmail;
    }

    public ThreePid getPhoneNumber() {
        return mPhoneNumber;
    }

    public void setAccountData(final String username, final String password) {
        mUsername = username;
        mPassword = password;
    }

    public boolean hasNonSupportedStage() {
        return !VECTOR_SUPPORTED_STAGES.containsAll(mSupportedStages);
    }

    public void setCaptchaResponse(final String captchaResponse) {
        mCaptchaResponse = captchaResponse;
    }

    public void checkUsernameAvailability(final Context context, final UsernameValidityListener listener) {
        if (getLoginRestClient() != null) {
            final RegistrationParams params = new RegistrationParams();
            params.username = mUsername;

            register(context, params, new RegistrationInternalListener() {
                @Override
                public void onRegistrationSuccess() {
                    listener.onUsernameAvailabilityChecked(true);
                }

                @Override
                public void onRegistrationFailed(String message) {
                    if (TextUtils.equals(MatrixError.USER_IN_USE, message)) {
                        listener.onUsernameAvailabilityChecked(false);
                    } else {
                        listener.onUsernameAvailabilityChecked(true);
                    }
                }
            });
        }
    }

    // TODO use constants for JSON keys + create method to create params from pid
    public void attemptRegistration(final Context context, final RegistrationListener listener) {
        if (!TextUtils.isEmpty(mRegistrationResponse.session)) {
            Map<String, Object> authParams = new HashMap<>();
            if (mPhoneNumber != null && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN) && !TextUtils.isEmpty(mPhoneNumber.sid)) {
                Map<String, Object> pidsCredentialsAuth = new HashMap<>();
                pidsCredentialsAuth.put("client_secret", mPhoneNumber.clientSecret);
                pidsCredentialsAuth.put("id_server", mHsConfig.getHomeserverUri().getHost());
                pidsCredentialsAuth.put("sid", mPhoneNumber.sid);
                authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
                authParams.put("threepid_creds", pidsCredentialsAuth);
                authParams.put("session", mRegistrationResponse.session);
            } else if (mEmail != null && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                if (TextUtils.isEmpty(mEmail.sid)) {
                    requestValidationToken(mEmail, new ThreePidRequestListener() {
                        @Override
                        public void onThreePidRequested(ThreePid pid) {
                            listener.onWaitingEmailValidation();
                        }
                    });
                    return;
                } else {
                    Map<String, Object> pidsCredentialsAuth = new HashMap<>();
                    pidsCredentialsAuth.put("client_secret", mEmail.clientSecret);
                    pidsCredentialsAuth.put("id_server", mHsConfig.getHomeserverUri().getHost());
                    pidsCredentialsAuth.put("sid", mEmail.sid);
                    authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
                    authParams.put("threepid_creds", pidsCredentialsAuth);
                    authParams.put("session", mRegistrationResponse.session);
                }
            } else if (!TextUtils.isEmpty(mCaptchaResponse) && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
                authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
                authParams.put("response", mCaptchaResponse);
                authParams.put("session", mRegistrationResponse.session);
            } else if (mSupportedStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_DUMMY)) {
                authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_DUMMY);
            } else {
                authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD);
            }

            final RegistrationParams params = new RegistrationParams();
            params.username = mUsername;
            params.password = mPassword;
            if (!authParams.isEmpty()) {
                params.auth = authParams;
            }
            params.bind_email = mEmail != null;

            register(context, params, new RegistrationInternalListener() {
                @Override
                public void onRegistrationSuccess() {
                    listener.onRegistrationSuccess();
                }

                @Override
                public void onRegistrationFailed(String message) {
                    listener.onRegistrationFailed(message);
                }
            });
        }
    }

    /**
     * Register step after a mail validation.
     * In the registration flow after a mail was validated {@see #startEmailOwnershipValidation},
     * this register request must be performed to reach the next registration step.
     *
     * @param aClientSecret   client secret
     * @param aSid            identity server session ID
     * @param aIdentityServer identity server url
     * @param aSessionId      session ID
     */
    public void registerAfterEmailValidation(final Context context, String aClientSecret, String aSid,
                                             String aIdentityServer, String aSessionId,
                                             final RegistrationListener listener) {
        Map<String, Object> authParams = new HashMap<>();
        Map<String, Object> thirdPartyIdsCredentialsAuth = new HashMap<>();
        RegistrationParams registrationParams = new RegistrationParams();

        // set session
        if (null != mRegistrationResponse) {
            mRegistrationResponse.session = aSessionId;
        }

        // remove URL scheme
        aIdentityServer = CommonActivityUtils.removeUrlScheme(aIdentityServer);

        thirdPartyIdsCredentialsAuth.put("client_secret", aClientSecret);
        thirdPartyIdsCredentialsAuth.put("id_server", aIdentityServer);
        thirdPartyIdsCredentialsAuth.put("sid", aSid);

        authParams.put("session", aSessionId);
        authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
        authParams.put("threepid_creds", thirdPartyIdsCredentialsAuth);

        registrationParams.auth = authParams;
        // Note: username, password and bind_email must not be set in registrationParams

        // privacy
        //Log.d(LOG_TAG, "## registerAfterEmailValidation(): getIntent register() with authParams=" + authParams);
        Log.d(LOG_TAG, "## registerAfterEmailValidation(): ");
        register(context, registrationParams, new RegistrationInternalListener() {
            @Override
            public void onRegistrationSuccess() {
                listener.onRegistrationSuccess();
            }

            @Override
            public void onRegistrationFailed(String message) {
                if (TextUtils.equals(ERROR_MISSING_STAGE, message)) {
                    listener.onWaitingCaptcha();
                } else {
                    listener.onRegistrationFailed(message);
                }
            }
        });
    }

    //TODO maybe public method without internal listener
    private void register(final Context context, final RegistrationParams params, final RegistrationInternalListener listener) {
        if (getLoginRestClient() != null) {
            mLoginRestClient.register(params, new SimpleApiCallback<Credentials>() {
                @Override
                public void onSuccess(Credentials credentials) {
                    if (TextUtils.isEmpty(credentials.userId)) {
                        listener.onRegistrationFailed("No user id");
                    } else {
                        Collection<MXSession> sessions = Matrix.getMXSessions(context);
                        boolean isDuplicated = false;

                        for (MXSession existingSession : sessions) {
                            Credentials cred = existingSession.getCredentials();
                            isDuplicated |= TextUtils.equals(credentials.userId, cred.userId) && TextUtils.equals(credentials.homeServer, cred.homeServer);
                        }

                        if (!isDuplicated) {
                            mHsConfig.setCredentials(credentials);
                            MXSession session = Matrix.getInstance(context).createSession(mHsConfig);
                            Matrix.getInstance(context).addSession(session);
                        }
                        resetSingleton();
                        listener.onRegistrationSuccess();
                    }
                }

                @Override
                public void onNetworkError(final Exception e) {
                    UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                    if (unrecCertEx != null) {
                        final Fingerprint fingerprint = unrecCertEx.getFingerprint();
                        Log.d(LOG_TAG, "Found fingerprint: SHA-256: " + fingerprint.getBytesAsHexString());

                        UnrecognizedCertHandler.show(mHsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                            @Override
                            public void onAccept() {
                                register(context, params, listener);
                            }

                            @Override
                            public void onIgnore() {
                                listener.onRegistrationFailed(e.getLocalizedMessage());
                            }

                            @Override
                            public void onReject() {
                                listener.onRegistrationFailed(e.getLocalizedMessage());
                            }
                        });
                    } else {
                        listener.onRegistrationFailed(e.getLocalizedMessage());
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    listener.onRegistrationFailed(e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (TextUtils.equals(e.errcode, MatrixError.USER_IN_USE)) {
                        // user name is already taken, the registration process stops here (new user name should be provided)
                        // ex: {"errcode":"M_USER_IN_USE","error":"User ID already taken."}
                        Log.d(LOG_TAG, "User name is used");
                        listener.onRegistrationFailed(MatrixError.USER_IN_USE);
                    } else if (TextUtils.equals(e.errcode, MatrixError.UNAUTHORIZED)) {
                        //do nothing
                    } else if (null != e.mStatus && e.mStatus == 401) {
                        try {
                            RegistrationFlowResponse registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                            setRegistrationFlowResponse(registrationFlowResponse);
                            mRegistrationResponse = RegistrationManager.getInstance().getRegistrationFlowResponse();
                        } catch (Exception castExcept) {
                            Log.e(LOG_TAG, "JsonUtils.toRegistrationFlowResponse " + castExcept.getLocalizedMessage());
                        }
                        listener.onRegistrationFailed(ERROR_MISSING_STAGE);
                    }
                }
            });
        }
    }

    public boolean canAddThreePid() {
        return (mSupportedStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY) && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY))
                || (mSupportedStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN) && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN));
    }

    public boolean isCompleted(final String stage) {
        return mRegistrationResponse != null && mRegistrationResponse.completed != null && mRegistrationResponse.completed.contains(stage);
    }

    public boolean supportStage(final String stage) {
        return mSupportedStages.contains(stage);
    }

    public boolean isOptional(final String stage) {
        return mOptionalStages.contains(stage);
    }

    public boolean isRequired(final String stage) {
        return mRequiredStages.contains(stage);
    }

    /**
     * @return true if captcha is mandatory for registration and not completed yet
     */
    public boolean isCaptchaRequired() {
        return mRequiredStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)
                && (mRegistrationResponse.completed == null || !mRegistrationResponse.completed.contains(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA));
    }

    /**
     * @return true if email is mandatory for registration and not completed yet
     */
    public boolean isEmailRequired() {
        return mRegistrationResponse != null
                && mRequiredStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)
                && (mRegistrationResponse.completed == null || !mRegistrationResponse.completed.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY));
    }

    /**
     * @return true if phone number is mandatory for registration and not completed yet
     */
    public boolean isPhoneNumberRequired() {
        return mRegistrationResponse != null
                && mRequiredStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)
                && (mRegistrationResponse.completed == null || !mRegistrationResponse.completed.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN));
    }

    public boolean canSkip() {
        return mOptionalStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)
                && mOptionalStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
    }

    /**
     * Request a validation token for the given phone number
     *
     * @param pid
     * @param listener
     */
    public void requestValidationToken(final ThreePid pid, final ThreePidRequestListener listener) {
        if (getThirdPidRestClient() != null) {
            switch (pid.medium) {
                case ThreePid.MEDIUM_EMAIL:
                    String webAppUrl = "https://vector.im/develop";
                    String nextLink = webAppUrl + "/#/register?client_secret=" + pid.clientSecret;
                    nextLink += "&hs_url=" + mHsConfig.getHomeserverUri().toString();
                    nextLink += "&is_url=" + mHsConfig.getIdentityServerUri().toString();
                    nextLink += "&session_id=" + mRegistrationResponse.session;
                    pid.requestEmailValidationToken(getThirdPidRestClient(), nextLink, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            listener.onThreePidRequested(pid);
                        }

                        @Override
                        public void onNetworkError(final Exception e) {
                            UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                            if (unrecCertEx != null) {
                                final Fingerprint fingerprint = unrecCertEx.getFingerprint();

                                UnrecognizedCertHandler.show(mHsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                                    @Override
                                    public void onAccept() {
                                        requestValidationToken(pid, listener);
                                    }

                                    @Override
                                    public void onIgnore() {
                                        listener.onThreePidRequested(pid);
                                    }

                                    @Override
                                    public void onReject() {
                                        listener.onThreePidRequested(pid);
                                    }
                                });
                            } else {
                                listener.onThreePidRequested(pid);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            listener.onThreePidRequested(pid);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            listener.onThreePidRequested(pid);
                        }
                    });
                    break;
                case ThreePid.MEDIUM_MSISDN:
                    pid.requestPhoneNumberValidationToken(getThirdPidRestClient(), null, new ApiCallback<RequestPhoneNumberValidationResponse>() {
                        @Override
                        public void onSuccess(RequestPhoneNumberValidationResponse response) {
                            mPhoneNumber = pid;
                            listener.onThreePidRequested(pid);
                        }

                        @Override
                        public void onNetworkError(final Exception e) {
                            UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
                            if (unrecCertEx != null) {
                                final Fingerprint fingerprint = unrecCertEx.getFingerprint();

                                UnrecognizedCertHandler.show(mHsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                                    @Override
                                    public void onAccept() {
                                        requestValidationToken(pid, listener);
                                    }

                                    @Override
                                    public void onIgnore() {
                                        listener.onThreePidRequested(pid);
                                    }

                                    @Override
                                    public void onReject() {
                                        listener.onThreePidRequested(pid);
                                    }
                                });
                            } else {
                                listener.onThreePidRequested(pid);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            listener.onThreePidRequested(pid);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            listener.onThreePidRequested(pid);
                        }
                    });
                    break;
            }
        }
    }

    public void submitValidationToken(final String token, final ThreePid pid, final ThreePidValidationListener listener) {
        if (getThirdPidRestClient() != null) {
            pid.submitValidationToken(getThirdPidRestClient(), pid.sid, token, pid.clientSecret, new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean isSuccess) {
                    listener.onThreePidValidated(isSuccess);
                }

                @Override
                public void onNetworkError(Exception e) {
                    listener.onThreePidValidated(false);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    listener.onThreePidValidated(false);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    listener.onThreePidValidated(false);
                }
            });
        }
    }

    public String getCaptchaPublicKey() {
        String publicKey = null;
        if (null != mRegistrationResponse.params) {
            Object recaptchaParamsAsVoid = mRegistrationResponse.params.get(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);

            if (null != recaptchaParamsAsVoid) {
                try {
                    Map<String, String> recaptchaParams = (Map<String, String>) recaptchaParamsAsVoid;
                    publicKey = recaptchaParams.get("public_key");

                } catch (Exception e) {
                    Log.e(LOG_TAG, "JsonUtils.recaptchaParams " + e.getLocalizedMessage());
                }
            }
        }
        return publicKey;
    }

    public void addEmailThreePid(final String email) {
        mEmail = new ThreePid(email, ThreePid.MEDIUM_EMAIL);
    }

    public void addPhoneNumberThreePid(final String phoneNumber, final String countryCode, final ThreePidRequestListener listener) {
        final ThreePid pid = new ThreePid(phoneNumber, countryCode, ThreePid.MEDIUM_MSISDN);
        requestValidationToken(pid, listener);
    }

    public void clearThreePid(){
        mEmail = null;
        mPhoneNumber = null;
    }

    /*
    * *********************************************************************************************
    * Private methods
    * *********************************************************************************************
    */

    private void analyzeRegistrationStages() {
        final List<String> requiredStages = new ArrayList<>();
        final List<String> conditionalStages = new ArrayList<>();
        final List<String> optionalStages = new ArrayList<>();

        boolean canCaptchaBeMissing = false;
        boolean canEmailBeMissing = false;
        boolean canPhoneBeMissing = false;
        boolean canThreePidBeMissing = false;
        for (LoginFlow loginFlow : mRegistrationResponse.flows) {
            mSupportedStages.addAll(loginFlow.stages);

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
                canCaptchaBeMissing = true;
            }
            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                canPhoneBeMissing = true;
                if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                    canThreePidBeMissing = true;
                }
            }
            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                canEmailBeMissing = true;
            }
        }

        // Check if captcha is required/optional
        if (mSupportedStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
            if (canCaptchaBeMissing) {
                optionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
            } else {
                requiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
            }
        }

        if (mSupportedStages.containsAll(Arrays.asList(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY, LoginRestClient.LOGIN_FLOW_TYPE_MSISDN))
                && !canThreePidBeMissing && canPhoneBeMissing && canEmailBeMissing) {
            // Both are supported and at least is required
            conditionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
            conditionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
        } else {
            if (mSupportedStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                if (canEmailBeMissing) {
                    optionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
                } else {
                    requiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
                }
            }
            if (mSupportedStages.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                if (canPhoneBeMissing) {
                    optionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
                } else {
                    requiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
                }
            }
        }

        mRequiredStages.clear();
        mRequiredStages.addAll(requiredStages);
        mOptionalStages.clear();
        mOptionalStages.addAll(optionalStages);
    }

    /*
    * *********************************************************************************************
    * Private listeners
    * *********************************************************************************************
    */

    private interface RegistrationInternalListener {
        void onRegistrationSuccess();

        void onRegistrationFailed(String message);
    }

    /*
    * *********************************************************************************************
    * Public listeners
    * *********************************************************************************************
    */

    public interface ThreePidRequestListener {
        void onThreePidRequested(ThreePid pid);
    }

    public interface ThreePidValidationListener {
        void onThreePidValidated(boolean isSuccess);
    }

    public interface UsernameValidityListener {
        void onUsernameAvailabilityChecked(boolean isAvailable);
    }

    public interface RegistrationListener {
        void onRegistrationSuccess();

        void onRegistrationFailed(String message);

        void onWaitingEmailValidation();

        void onWaitingCaptcha();
    }
}
