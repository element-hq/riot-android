/*
 * Copyright 2017 Vector Creations Ltd
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

package im.vector;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.login.RegistrationToolsKt;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.login.AuthParams;
import org.matrix.androidsdk.rest.model.login.AuthParamsCaptcha;
import org.matrix.androidsdk.rest.model.login.AuthParamsLoginPassword;
import org.matrix.androidsdk.rest.model.login.AuthParamsThreePid;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LocalizedFlowDataLoginTerms;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import im.vector.util.UrlUtilKt;

public class RegistrationManager {
    private static final String LOG_TAG = RegistrationManager.class.getSimpleName();

    private static final String ERROR_MISSING_STAGE = "ERROR_MISSING_STAGE";
    private static final String ERROR_EMPTY_USER_ID = "ERROR_EMPTY_USER_ID";

    private static final String NEXTLINK_BASE_URL = "https://riot.im/app";

    // saved parameters index
    private static final String SAVED_CREATION_USER_NAME = "SAVED_CREATION_USER_NAME";
    private static final String SAVED_CREATION_PASSWORD = "SAVED_CREATION_PASSWORD";
    private static final String SAVED_CREATION_REGISTRATION_RESPONSE = "SAVED_CREATION_REGISTRATION_RESPONSE";

    // List of stages supported by the app
    private static final List<String> VECTOR_SUPPORTED_STAGES = Arrays.asList(
            LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD,
            LoginRestClient.LOGIN_FLOW_TYPE_DUMMY,
            LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY,
            LoginRestClient.LOGIN_FLOW_TYPE_MSISDN,
            LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA,
            LoginRestClient.LOGIN_FLOW_TYPE_TERMS);

    // Config
    private HomeServerConnectionConfig mHsConfig;
    private LoginRestClient mLoginRestClient;
    private ThirdPidRestClient mThirdPidRestClient;
    private ProfileRestClient mProfileRestClient;

    // Flows
    private RegistrationFlowResponse mRegistrationResponse;
    private final Set<String> mSupportedStages = new HashSet<>();
    private final List<String> mRequiredStages = new ArrayList<>();
    private final List<String> mOptionalStages = new ArrayList<>();

    // Current registration params
    private String mUsername;
    private String mPassword;
    private ThreePid mEmail;
    private ThreePid mPhoneNumber;
    private String mCaptchaResponse;
    private boolean mTermsApproved;

    // True when the user entered both email and phone but only phone will be used for account registration
    private boolean mShowThreePidWarning;

    /*
     * *********************************************************************************************
     * Constructor
     * *********************************************************************************************
     */

    public RegistrationManager(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mUsername = savedInstanceState.getString(SAVED_CREATION_USER_NAME);
            mPassword = savedInstanceState.getString(SAVED_CREATION_PASSWORD);

            mRegistrationResponse = (RegistrationFlowResponse) savedInstanceState.getSerializable(SAVED_CREATION_REGISTRATION_RESPONSE);
        }
    }

    /*
     * *********************************************************************************************
     * Public methods
     * *********************************************************************************************
     */

    /**
     * Reset values to allow a new registration
     */
    public void reset() {
        mHsConfig = null;
        mLoginRestClient = null;
        mThirdPidRestClient = null;
        mProfileRestClient = null;
        mRegistrationResponse = null;

        mSupportedStages.clear();
        mRequiredStages.clear();
        mOptionalStages.clear();

        mUsername = null;
        mPassword = null;
        mEmail = null;
        mPhoneNumber = null;
        mCaptchaResponse = null;
        mTermsApproved = false;

        mShowThreePidWarning = false;
    }

    /**
     * Set the home server config
     *
     * @param hsConfig
     */
    public void setHsConfig(final HomeServerConnectionConfig hsConfig) {
        mHsConfig = hsConfig;
        mLoginRestClient = null;
        mThirdPidRestClient = null;
        mProfileRestClient = null;
    }

    /**
     * Set username and password (registration params)
     *
     * @param username
     * @param password
     */
    public void setAccountData(final String username, final String password) {
        mUsername = username;
        mPassword = password;
    }

    /**
     * Set the captcha response (registration param)
     *
     * @param captchaResponse
     */
    public void setCaptchaResponse(final String captchaResponse) {
        mCaptchaResponse = captchaResponse;
    }

    /**
     * Tell the terms are approved (registration param)
     */
    public void setTermsApproved() {
        mTermsApproved = true;
    }

    /**
     * Set the supported flow stages for the current home server)
     *
     * @param registrationFlowResponse
     */
    public void setSupportedRegistrationFlows(final RegistrationFlowResponse registrationFlowResponse) {
        if (registrationFlowResponse != null) {
            mRegistrationResponse = registrationFlowResponse;
            analyzeRegistrationStages(registrationFlowResponse);
        }
    }

    /**
     * Make a register request to check whether a username is available or not
     *
     * @param context
     * @param listener
     */
    public void checkUsernameAvailability(final Context context, final UsernameValidityListener listener) {
        if (getLoginRestClient() != null) {
            // Trigger a fake registration (without password) to know whether the user name is available or not.
            RegistrationParams params = new RegistrationParams();
            params.username = mUsername;

            // Note: We do not pass sessionId here, this is not necessary.

            register(context, params, new InternalRegistrationListener() {
                @Override
                public void onRegistrationSuccess() {
                    // The registration could not succeed without password.
                    // Keep calling listener (the error case) as a fallback,
                    listener.onUsernameAvailabilityChecked(false);
                }

                @Override
                public void onRegistrationFailed(String message) {
                    listener.onUsernameAvailabilityChecked(!TextUtils.equals(MatrixError.USER_IN_USE, message));
                }

                @Override
                public void onResourceLimitExceeded(MatrixError e) {
                    // Should not happen, consider user is available, registration will fail later on
                    listener.onUsernameAvailabilityChecked(true);
                }
            });
        }
    }

    /**
     * @return true if there is a password flow.
     */
    private boolean isPasswordBasedFlowSupported() {
        if ((null != mRegistrationResponse) && (null != mRegistrationResponse.flows)) {
            for (LoginFlow flow : mRegistrationResponse.flows) {
                if (TextUtils.equals(flow.type, LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD)
                        || ((null != flow.stages) && flow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD))) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Make the registration request with params depending on singleton values
     *
     * @param context
     * @param listener
     */
    public void attemptRegistration(final Context context, final RegistrationListener listener) {
        final String registrationType;
        if (mRegistrationResponse != null && !TextUtils.isEmpty(mRegistrationResponse.session)) {
            AuthParams authParams = null;
            if (mPhoneNumber != null && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN) && !TextUtils.isEmpty(mPhoneNumber.sid)) {
                Uri identityServerUri = mHsConfig.getIdentityServerUri();
                if (identityServerUri == null) {
                    listener.onIdentityServerMissing();
                    return;
                } else {
                    registrationType = LoginRestClient.LOGIN_FLOW_TYPE_MSISDN;
                    authParams = getThreePidAuthParams(mPhoneNumber.clientSecret, identityServerUri.getHost(),
                            mPhoneNumber.sid, LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
                }
            } else if (mEmail != null && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                if (TextUtils.isEmpty(mEmail.sid)) {
                    // Email token needs to be requested before doing validation
                    Log.d(LOG_TAG, "attemptRegistration: request email validation");
                    requestValidationToken(context, mEmail, new ThreePidRequestListener() {
                        @Override
                        public void onIdentityServerMissing() {
                            listener.onIdentityServerMissing();
                        }

                        @Override
                        public void onThreePidRequested(ThreePid pid) {
                            if (!TextUtils.isEmpty(pid.sid)) {
                                // The session id for the email validation has just been received.
                                // We trigger here a new registration request without delay to attach the current username
                                // and the pwd to the registration session.
                                attemptRegistration(context, listener);

                                // Notify the listener to wait for the email validation
                                listener.onWaitingEmailValidation();
                            }
                        }

                        @Override
                        public void onThreePidRequestFailed(String errorMessage) {
                            listener.onThreePidRequestFailed(errorMessage);
                        }
                    });
                    return;
                } else {
                    Uri identityServerUri = mHsConfig.getIdentityServerUri();
                    if (identityServerUri == null) {
                        listener.onIdentityServerMissing();
                        return;
                    } else {
                        registrationType = LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY;
                        authParams = getThreePidAuthParams(mEmail.clientSecret, identityServerUri.getHost(),
                                mEmail.sid, LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
                    }
                }
            } else if (!TextUtils.isEmpty(mCaptchaResponse) && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
                registrationType = LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA;
                authParams = getCaptchaAuthParams(mCaptchaResponse);
            } else if (mTermsApproved && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_TERMS)) {
                registrationType = LoginRestClient.LOGIN_FLOW_TYPE_TERMS;
                authParams = new AuthParams(LoginRestClient.LOGIN_FLOW_TYPE_TERMS);
            } else if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_DUMMY)) {
                registrationType = LoginRestClient.LOGIN_FLOW_TYPE_DUMMY;
                authParams = new AuthParams(LoginRestClient.LOGIN_FLOW_TYPE_DUMMY);
            } else if (isPasswordBasedFlowSupported()) {
                // never has been tested
                registrationType = LoginRestClient.LOGIN_FLOW_TYPE_PASSWORD;
                authParams = new AuthParamsLoginPassword();

                if (null != mUsername) {
                    ((AuthParamsLoginPassword) authParams).user = mUsername;
                }

                if (null != mPassword) {
                    ((AuthParamsLoginPassword) authParams).password = mPassword;
                }
            } else {
                // others
                registrationType = "";
            }

            if (TextUtils.equals(registrationType, LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)
                    && mEmail != null && !isCaptchaRequired()) {
                // Email will not be processed
                mShowThreePidWarning = true;
                mEmail = null;
            }

            final RegistrationParams params = new RegistrationParams();
            if (!registrationType.equals(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)
                    && !registrationType.equals(LoginRestClient.LOGIN_FLOW_TYPE_TERMS)) {
                if (mUsername != null) {
                    params.username = mUsername;
                }
                if (mPassword != null) {
                    params.password = mPassword;
                }
                params.bind_email = mEmail != null;
                params.bind_msisdn = mPhoneNumber != null;
            }

            if (authParams != null) {
                // Always send the current session
                authParams.session = mRegistrationResponse.session;

                params.auth = authParams;
            }

            register(context, params, new InternalRegistrationListener() {
                @Override
                public void onRegistrationSuccess() {
                    if (mShowThreePidWarning) {
                        // An email was entered but was not attached to account
                        listener.onRegistrationSuccess(context.getString(R.string.auth_threepid_warning_message));
                    } else {
                        listener.onRegistrationSuccess(null);
                    }
                }

                @Override
                public void onRegistrationFailed(String message) {
                    if (TextUtils.equals(ERROR_MISSING_STAGE, message)
                            && (mPhoneNumber == null || isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN))) {
                        if (mEmail != null && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                            attemptRegistration(context, listener);
                        } else if (isTermsRequired()) {
                            listener.onWaitingTerms(getLocalizedLoginTerms(context));
                        } else {
                            // At this point, only captcha can be the missing stage
                            listener.onWaitingCaptcha(getCaptchaPublicKey());
                        }
                    } else {
                        listener.onRegistrationFailed(message);
                    }
                }

                @Override
                public void onResourceLimitExceeded(MatrixError e) {
                    listener.onResourceLimitExceeded(e);
                }
            });
        }
    }

    /**
     * Register step after a mail validation.
     * In the registration flow after an email was validated {@see #startEmailOwnershipValidation},
     * this register request must be performed to reach the next registration step.
     *
     * @param context
     * @param aClientSecret   client secret
     * @param aSid            identity server session ID
     * @param aIdentityServer identity server url
     * @param aSessionId      session ID
     * @param listener
     */
    public void registerAfterEmailValidation(final Context context,
                                             final String aClientSecret,
                                             final String aSid,
                                             final String aIdentityServer,
                                             final String aSessionId,
                                             final RegistrationListener listener) {
        Log.d(LOG_TAG, "registerAfterEmailValidation");
        // set session
        if (null != mRegistrationResponse) {
            mRegistrationResponse.session = aSessionId;
        }

        RegistrationParams registrationParams = new RegistrationParams();
        registrationParams.auth = getThreePidAuthParams(aClientSecret, UrlUtilKt.removeUrlScheme(aIdentityServer),
                aSid, LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);

        registrationParams.auth.session = aSessionId;

        // Note: username, password and bind_email must not be set in registrationParams
        mUsername = null;
        mPassword = null;
        clearThreePid();

        register(context, registrationParams, new InternalRegistrationListener() {
            @Override
            public void onRegistrationSuccess() {
                listener.onRegistrationSuccess(null);
            }

            @Override
            public void onRegistrationFailed(String message) {
                if (TextUtils.equals(ERROR_MISSING_STAGE, message)) {
                    if (isTermsRequired()) {
                        listener.onWaitingTerms(getLocalizedLoginTerms(context));
                    } else {
                        // At this point, only captcha can be the missing stage
                        listener.onWaitingCaptcha(getCaptchaPublicKey());
                    }
                } else {
                    listener.onRegistrationFailed(message);
                }
            }

            @Override
            public void onResourceLimitExceeded(MatrixError e) {
                listener.onResourceLimitExceeded(e);
            }
        });
    }

    /**
     * Check if a stage supported by the current home server can be handle by the app
     *
     * @return true if at least one stage cannot be handle
     */
    public boolean hasNonSupportedStage() {
        return !VECTOR_SUPPORTED_STAGES.containsAll(mSupportedStages);
    }

    /**
     * Check if the given stage is supported by the current home server
     *
     * @param stage
     * @return true if supported
     */
    public boolean supportStage(final String stage) {
        return mSupportedStages.contains(stage);
    }

    /**
     * Check if the current home server has a three pid (email, phone number) which can be added (ie. not completed yet)
     *
     * @return true if can add a three pid
     */
    public boolean canAddThreePid() {
        return (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY) && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY))
                || (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN) && !isCompleted(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN));
    }

    /**
     * Check if the given stage has been completed
     *
     * @param stage
     * @return true if completed
     */
    private boolean isCompleted(final String stage) {
        return mRegistrationResponse != null && mRegistrationResponse.completed != null && mRegistrationResponse.completed.contains(stage);
    }

    /**
     * Check if the given stage is optional for the current home server
     *
     * @param stage
     * @return true if optional
     */
    public boolean isOptional(final String stage) {
        return mOptionalStages.contains(stage);
    }

    /**
     * Check if the given stage is required by the current home server
     *
     * @param stage
     * @return true if required
     */
    private boolean isRequired(final String stage) {
        return mRequiredStages.contains(stage);
    }

    /**
     * @return true if email is mandatory for registration and not completed yet
     */
    public boolean isEmailRequired() {
        return isStageRequired(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
    }

    /**
     * @return true if phone number is mandatory for registration and not completed yet
     */
    public boolean isPhoneNumberRequired() {
        return isStageRequired(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
    }

    /**
     * @return true if captcha is mandatory for registration and not completed yet
     */
    private boolean isCaptchaRequired() {
        return isStageRequired(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
    }

    /**
     * @return true if captcha is mandatory for registration and not completed yet
     */
    private boolean isTermsRequired() {
        return isStageRequired(LoginRestClient.LOGIN_FLOW_TYPE_TERMS);
    }

    /**
     * Return true if the stage is required and not completed
     *
     * @param stage
     * @return
     */
    private boolean isStageRequired(final String stage) {
        return mRegistrationResponse != null
                && isRequired(stage)
                && !isCompleted(stage);
    }

    /**
     * Check whether the current home server supports registration without three pid
     * (ie. does not support three pid or supports but it is optional)
     *
     * @return
     */
    public boolean canSkipThreePid() {
        boolean canSkip = true;

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
            canSkip = isOptional(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
        }

        if (canSkip) {
            if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                canSkip = isOptional(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
            }
        }

        return canSkip;
    }

    /**
     * Submit the token for the given three pid
     *
     * @param token
     * @param pid
     * @param listener
     */
    public void submitValidationToken(final String token, final ThreePid pid, final ThreePidValidationListener listener) {
        if (getThirdPidRestClient() != null) {
            pid.submitValidationToken(getThirdPidRestClient(), token, pid.clientSecret, pid.sid, new ApiCallback<Boolean>() {
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

    /**
     * Add email three pid to singleton values
     * It will be processed later on
     *
     * @param emailThreePid
     */
    public void addEmailThreePid(final ThreePid emailThreePid) {
        mEmail = emailThreePid;
    }

    /**
     * Get the current email three pid (if any).
     *
     * @return the corresponding three pid
     */
    public ThreePid getEmailThreePid() {
        return mEmail;
    }

    /**
     * Add phone number to the registration process by requesting token first
     *
     * @param context
     * @param phoneNumber
     * @param countryCode
     * @param listener
     */
    public void addPhoneNumberThreePid(final Context context, final String phoneNumber, final String countryCode, final ThreePidRequestListener listener) {
        final ThreePid pid = new ThreePid(phoneNumber, countryCode, ThreePid.MEDIUM_MSISDN);
        requestValidationToken(context, pid, listener);
    }

    /**
     * Clear three pids from singleton values
     */
    public void clearThreePid() {
        mEmail = null;
        mPhoneNumber = null;
        mShowThreePidWarning = false;
    }

    /**
     * Return the three pid instructions
     *
     * @return instructions
     */
    public String getThreePidInstructions(final Context context) {
        int instructionRes = -1;
        if (mRegistrationResponse != null) {
            if (isRequired(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)
                    && isRequired(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                // Both required
                instructionRes = R.string.auth_add_email_and_phone_message;
            } else if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                    // Both supported but not both required
                    instructionRes = R.string.auth_add_email_phone_message;
                } else {
                    // Only email
                    instructionRes = R.string.auth_add_email_message;
                }
            } else if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                // Only phone number
                instructionRes = R.string.auth_add_phone_message;
            }
        }
        return instructionRes != -1 ? context.getString(instructionRes) : "";
    }

    /*
     * *********************************************************************************************
     * Private methods
     * *********************************************************************************************
     */

    /**
     * Get the list of LocalizedFlowDataLoginTerms the user has to accept
     *
     * @return list of LocalizedFlowDataLoginTerms the user has to accept
     */
    private List<LocalizedFlowDataLoginTerms> getLocalizedLoginTerms(Context context) {
        return RegistrationToolsKt.getLocalizedLoginTerms(mRegistrationResponse,
                context.getString(R.string.resources_language),
                "en");
    }

    /**
     * Get the public key for captcha registration
     *
     * @return public key
     */
    @Nullable
    private String getCaptchaPublicKey() {
        return RegistrationToolsKt.getCaptchaPublicKey(mRegistrationResponse);
    }

    /**
     * Get a login rest client
     *
     * @return login rest client
     */
    private LoginRestClient getLoginRestClient() {
        if (mLoginRestClient == null && mHsConfig != null) {
            mLoginRestClient = new LoginRestClient(mHsConfig);
        }
        return mLoginRestClient;
    }

    /**
     * Get a third pid rest client
     *
     * @return third pid rest client
     */
    private ThirdPidRestClient getThirdPidRestClient() {
        if (mThirdPidRestClient == null && mHsConfig != null && mHsConfig.getIdentityServerUri() != null) {
            mThirdPidRestClient = new ThirdPidRestClient(mHsConfig);
        }
        return mThirdPidRestClient;
    }

    /**
     * Get a profile rest client
     *
     * @return third pid rest client
     */
    private ProfileRestClient getProfileRestClient() {
        if (mProfileRestClient == null && mHsConfig != null) {
            mProfileRestClient = new ProfileRestClient(mHsConfig);
        }
        return mProfileRestClient;
    }

    /**
     * Set the flow stages for the current home server
     *
     * @param registrationFlowResponse
     */
    private void setRegistrationFlowResponse(final RegistrationFlowResponse registrationFlowResponse) {
        if (registrationFlowResponse != null) {
            mRegistrationResponse = registrationFlowResponse;
        }
    }

    /**
     * Analyze the flows stages
     *
     * @param newFlowResponse
     */
    private void analyzeRegistrationStages(final RegistrationFlowResponse newFlowResponse) {
        mSupportedStages.clear();
        mRequiredStages.clear();
        mOptionalStages.clear();

        boolean canCaptchaBeMissing = false;
        boolean canTermsBeMissing = false;
        boolean canPhoneBeMissing = false;
        boolean canEmailBeMissing = false;

        // Add all supported stage and check if some stage can be missing
        for (LoginFlow loginFlow : newFlowResponse.flows) {
            mSupportedStages.addAll(loginFlow.stages);

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
                canCaptchaBeMissing = true;
            }

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_TERMS)) {
                canTermsBeMissing = true;
            }

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
                canPhoneBeMissing = true;
            }

            if (!loginFlow.stages.contains(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
                canEmailBeMissing = true;
            }
        }

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)) {
            if (canCaptchaBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA);
            }
        }

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_TERMS)) {
            if (canTermsBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_TERMS);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_TERMS);
            }
        }

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY)) {
            if (canEmailBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_EMAIL_IDENTITY);
            }
        }

        if (supportStage(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN)) {
            if (canPhoneBeMissing) {
                mOptionalStages.add(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
            } else {
                mRequiredStages.add(LoginRestClient.LOGIN_FLOW_TYPE_MSISDN);
            }
        }
    }

    /**
     * Format three pid params for registration request
     *
     * @param clientSecret
     * @param host
     * @param sid          received by requestToken request
     * @param medium       type of three pid
     * @return map of params
     */
    private AuthParams getThreePidAuthParams(final String clientSecret,
                                             final String host,
                                             final String sid,
                                             final String medium) {
        AuthParamsThreePid authParams = new AuthParamsThreePid(medium);

        authParams.threePidCredentials.clientSecret = clientSecret;
        authParams.threePidCredentials.idServer = host;
        authParams.threePidCredentials.sid = sid;

        return authParams;
    }

    /**
     * Format captcha params for registration request
     *
     * @param captchaResponse
     * @return
     */
    private AuthParams getCaptchaAuthParams(final String captchaResponse) {
        AuthParamsCaptcha authParams = new AuthParamsCaptcha();
        authParams.response = captchaResponse;
        return authParams;
    }

    /**
     * Request a validation token for the given three pid
     *
     * @param context
     * @param pid
     * @param listener
     */
    private void requestValidationToken(final Context context, final ThreePid pid, final ThreePidRequestListener listener) {
        Uri identityServerUri = mHsConfig.getIdentityServerUri();
        if (identityServerUri == null) {
            listener.onIdentityServerMissing();
        } else {
            if (getThirdPidRestClient() != null) {
                switch (pid.medium) {
                    case ThreePid.MEDIUM_EMAIL:
                        String nextLink = NEXTLINK_BASE_URL + "/#/register?client_secret=" + pid.clientSecret;
                        nextLink += "&hs_url=" + mHsConfig.getHomeserverUri().toString();
                        nextLink += "&is_url=" + identityServerUri.toString();
                        nextLink += "&session_id=" + mRegistrationResponse.session;
                        pid.requestEmailValidationToken(getProfileRestClient(), nextLink, true, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                listener.onThreePidRequested(pid);
                            }

                            @Override
                            public void onNetworkError(final Exception e) {
                                warnAfterCertificateError(context, e, pid, listener);
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                String errorMessage = build3PidErrorMessage(context, R.string.account_email_error, e.getLocalizedMessage());
                                listener.onThreePidRequestFailed(errorMessage);
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                String errorMessage = null;
                                if (TextUtils.equals(MatrixError.THREEPID_IN_USE, e.errcode)) {
                                    errorMessage = build3PidErrorMessage(context, R.string.account_email_already_used_error, null);
                                } else {
                                    errorMessage = build3PidErrorMessage(context, R.string.account_email_error, e.getLocalizedMessage());
                                }

                                listener.onThreePidRequestFailed(errorMessage);
                            }
                        });
                        break;
                    case ThreePid.MEDIUM_MSISDN:
                        pid.requestPhoneNumberValidationToken(getProfileRestClient(), true, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                mPhoneNumber = pid;
                                listener.onThreePidRequested(pid);
                            }

                            @Override
                            public void onNetworkError(final Exception e) {
                                warnAfterCertificateError(context, e, pid, listener);
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                String errorMessage = build3PidErrorMessage(context, R.string.account_phone_number_error, e.getLocalizedMessage());
                                listener.onThreePidRequestFailed(errorMessage);
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                String errorMessage = null;
                                if (TextUtils.equals(MatrixError.THREEPID_IN_USE, e.errcode)) {
                                    errorMessage = build3PidErrorMessage(context, R.string.account_phone_number_already_used_error, null);
                                } else {
                                    errorMessage = build3PidErrorMessage(context, R.string.account_phone_number_error, e.mReason);
                                }

                                listener.onThreePidRequestFailed(errorMessage);
                            }
                        });
                        break;
                }
            }
        }
    }

    /**
     * Creates a 3PID error message from a string resource id and adds the additional infos, if non-null
     *
     * @param context
     * @param errorMessageRes
     * @param additionalInfo
     * @return The error message
     */
    private static String build3PidErrorMessage(Context context, @StringRes int errorMessageRes, @Nullable String additionalInfo) {
        StringBuilder builder = new StringBuilder();
        builder.append(context.getString(errorMessageRes));

        if (additionalInfo != null) {
            builder.append(' ');
            builder.append(context.getString(R.string.account_additional_info, additionalInfo));
        }

        return builder.toString();
    }

    /**
     * Display warning dialog in case of certificate error
     *
     * @param context
     * @param e        the exception
     * @param pid
     * @param listener
     */
    private void warnAfterCertificateError(final Context context, final Exception e, final ThreePid pid, final ThreePidRequestListener listener) {
        UnrecognizedCertificateException unrecCertEx = CertUtil.getCertificateException(e);
        if (unrecCertEx != null) {
            final Fingerprint fingerprint = unrecCertEx.getFingerprint();

            UnrecognizedCertHandler.show(mHsConfig, fingerprint, false, new UnrecognizedCertHandler.Callback() {
                @Override
                public void onAccept() {
                    requestValidationToken(context, pid, listener);
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

    /**
     * Send a registration request with the given parameters
     *
     * @param context
     * @param params   registration params
     * @param listener
     */
    private void register(final Context context, final RegistrationParams params, final InternalRegistrationListener listener) {
        if (getLoginRestClient() != null) {
            params.initial_device_display_name = context.getString(R.string.login_mobile_device);
            mLoginRestClient.register(params, new UnrecognizedCertApiCallback<Credentials>(mHsConfig) {
                @Override
                public void onSuccess(Credentials credentials) {
                    if (TextUtils.isEmpty(credentials.userId)) {
                        listener.onRegistrationFailed(ERROR_EMPTY_USER_ID);
                    } else {
                        // Initiate login process
                        Collection<MXSession> sessions = Matrix.getMXSessions(context);
                        boolean isDuplicated = false;

                        for (MXSession existingSession : sessions) {
                            Credentials cred = existingSession.getCredentials();
                            isDuplicated |= TextUtils.equals(credentials.userId, cred.userId) && TextUtils.equals(credentials.homeServer, cred.homeServer);
                        }

                        if (null == mHsConfig) {
                            listener.onRegistrationFailed("null mHsConfig");
                        } else {
                            if (!isDuplicated) {
                                mHsConfig.setCredentials(credentials);
                                MXSession session = Matrix.getInstance(context).createSession(mHsConfig);
                                Matrix.getInstance(context).addSession(session);
                            }

                            listener.onRegistrationSuccess();
                        }
                    }
                }

                @Override
                public void onAcceptedCert() {
                    register(context, params, listener);
                }

                @Override
                public void onTLSOrNetworkError(final Exception e) {
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
                        // happens while polling email validation, do nothing
                    } else if (null != e.mStatus && e.mStatus == 401) {
                        try {
                            RegistrationFlowResponse registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                            setRegistrationFlowResponse(registrationFlowResponse);
                        } catch (Exception castExcept) {
                            Log.e(LOG_TAG, "JsonUtils.toRegistrationFlowResponse " + castExcept.getLocalizedMessage(), castExcept);
                        }
                        listener.onRegistrationFailed(ERROR_MISSING_STAGE);
                    } else if (TextUtils.equals(e.errcode, MatrixError.RESOURCE_LIMIT_EXCEEDED)) {
                        listener.onResourceLimitExceeded(e);
                    } else {
                        listener.onRegistrationFailed("");
                    }
                }
            });
        }
    }

    public boolean hasRegistrationResponse() {
        return mRegistrationResponse != null;
    }

    public void saveInstanceState(Bundle savedInstanceState) {
        if (!TextUtils.isEmpty(mUsername)) {
            savedInstanceState.putString(SAVED_CREATION_USER_NAME, mUsername);
        }

        if (!TextUtils.isEmpty(mPassword)) {
            savedInstanceState.putString(SAVED_CREATION_PASSWORD, mPassword);
        }

        if (null != mRegistrationResponse) {
            savedInstanceState.putSerializable(SAVED_CREATION_REGISTRATION_RESPONSE, mRegistrationResponse);
        }
    }

    /*
     * *********************************************************************************************
     * Private listeners
     * *********************************************************************************************
     */

    private interface InternalRegistrationListener {
        void onRegistrationSuccess();

        void onRegistrationFailed(String message);

        void onResourceLimitExceeded(MatrixError e);
    }

    /*
     * *********************************************************************************************
     * Public listeners
     * *********************************************************************************************
     */

    public interface ThreePidRequestListener {
        void onIdentityServerMissing();

        void onThreePidRequested(ThreePid pid);

        void onThreePidRequestFailed(String errorMessage);
    }

    public interface ThreePidValidationListener {
        void onThreePidValidated(boolean isSuccess);
    }

    public interface UsernameValidityListener {
        void onUsernameAvailabilityChecked(boolean isAvailable);
    }

    public interface RegistrationListener {
        void onRegistrationSuccess(String warningMessage);

        void onRegistrationFailed(String message);

        void onWaitingEmailValidation();

        void onIdentityServerMissing();

        /**
         * @param publicKey the Captcha public key
         */
        void onWaitingCaptcha(String publicKey);

        /**
         * @param localizedFlowDataLoginTerms list of LocalizedFlowDataLoginTerms the user has to accept
         */
        void onWaitingTerms(List<LocalizedFlowDataLoginTerms> localizedFlowDataLoginTerms);

        void onThreePidRequestFailed(String message);

        void onResourceLimitExceeded(MatrixError e);
    }
}
