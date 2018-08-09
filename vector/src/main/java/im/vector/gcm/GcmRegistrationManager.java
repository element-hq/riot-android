/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.gcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.PushersRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PushersResponse;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.util.PreferencesManager;

/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager {
    private static final String LOG_TAG = GcmRegistrationManager.class.getSimpleName();

    private static final String PREFS_GCM = "GcmRegistrationManager";

    private static final String PREFS_ALLOW_NOTIFICATIONS = "GcmRegistrationManager.PREFS_ALLOW_NOTIFICATIONS";
    private static final String PREFS_TURN_SCREEN_ON = "GcmRegistrationManager.PREFS_TURN_SCREEN_ON";
    private static final String PREFS_ALLOW_BACKGROUND_SYNC = "GcmRegistrationManager.PREFS_ALLOW_BACKGROUND_SYNC";
    private static final String PREFS_ALLOW_SENDING_CONTENT_TO_GCM = "GcmRegistrationManager.PREFS_ALLOW_SENDING_CONTENT_TO_GCM";

    private static final String PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM = "PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM";
    private static final String PREFS_PUSHER_REGISTRATION_TOKEN_KEY = "PREFS_PUSHER_REGISTRATION_TOKEN_KEY";
    private static final String PREFS_PUSHER_REGISTRATION_STATUS = "PREFS_PUSHER_REGISTRATION_STATUS";

    private static final String PREFS_SYNC_TIMEOUT = "GcmRegistrationManager.PREFS_SYNC_TIMEOUT";
    private static final String PREFS_SYNC_DELAY = "GcmRegistrationManager.PREFS_SYNC_DELAY";

    private static final String DEFAULT_PUSHER_APP_ID = "im.vector.app.android";
    private static final String DEFAULT_PUSHER_URL = "https://matrix.org/_matrix/push/v1/notify";
    private static final String DEFAULT_PUSHER_FILE_TAG = "mobile";

    /**
     * GCM registration interface
     */
    public interface GcmRegistrationListener {
        // GCM is properly registered.
        void onGCMRegistered();

        // GCM registration fails.
        void onGCMRegistrationFailed();
    }

    /**
     * 3rd party server registration listener
     */
    public interface ThirdPartyRegistrationListener {
        // the third party server is successfully registered/unregistered
        void onSuccess();

        // An error occurred
        void onError();
    }

    private String mPusherAppName = null;
    private String mPusherLang = null;

    // the session registration listener
    private final List<ThirdPartyRegistrationListener> mThirdPartyRegistrationListeners = new ArrayList<>();

    // the pushers list
    public List<Pusher> mPushersList = new ArrayList<>();

    /**
     * Registration steps (Note: do not change the order of enum, cause their ordinal are store in the SharedPrefs)
     */
    private enum RegistrationState {
        /**
         * GCM is not registered
         */
        UNREGISTRATED,
        /**
         * GCM registration is started
         */
        GCM_REGISTRATING,
        /**
         * GCM is registered, but token has not been sent to the Push server
         */
        GCM_REGISTERED,
        /**
         * GCM token is currently sent to the Push server
         */
        SERVER_REGISTRATING,
        /**
         * GCM token has been sent to the Push server
         */
        SERVER_REGISTERED,
        /**
         * Push servers is currently un registering
         */
        SERVER_UNREGISTRATING,
    }

    // the pusher base
    private final String mBasePusherDeviceName;

    // the context
    private final Context mContext;

    // the registration state
    private RegistrationState mRegistrationState;

    // defines the GCM registration token
    private String mRegistrationToken;

    // 3 states : null not initialized (retrieved by flavor)
    private static Boolean mUseGCM;

    // pusher rest client
    private Map<String, PushersRestClient> mPushersRestClients = new HashMap<>();

    /**
     * Constructor
     *
     * @param appContext the application context.
     */
    public GcmRegistrationManager(final Context appContext) {
        mContext = appContext.getApplicationContext();
        // TODO customise it ?
        mBasePusherDeviceName = Build.MODEL.trim();

        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            mPusherAppName = pInfo.packageName;
            mPusherLang = mContext.getResources().getConfiguration().locale.getLanguage();
        } catch (Exception e) {
            mPusherAppName = "VectorApp";
            mPusherLang = "en";
        }

        Matrix.getInstance(appContext).addNetworkEventListener(new IMXNetworkEventListener() {
            @Override
            public void onNetworkConnectionUpdate(boolean isConnected) {
                if (isConnected) {
                    // test if the server registration / unregistration should be done
                    if (useGCM()) {
                        // test if the user expect having notifications on his device but it was not yet done
                        if (areDeviceNotificationsAllowed() && (mRegistrationState == RegistrationState.GCM_REGISTERED)) {
                            register(null);
                        } else if (!areDeviceNotificationsAllowed() && (mRegistrationState == RegistrationState.SERVER_REGISTERED)) {
                            unregister(null);
                        }
                    }
                }
            }
        });

        mRegistrationState = getStoredRegistrationState();
        mRegistrationToken = getStoredRegistrationToken();
    }

    /**
     * Retrieves the pushers rest client.
     *
     * @param session the session
     * @return the pushers rest client.
     */
    private PushersRestClient getPushersRestClient(MXSession session) {
        PushersRestClient pushersRestClient = mPushersRestClients.get(session.getMyUserId());

        if (null == pushersRestClient) {
            // pusher uses a custom server
            if (!TextUtils.isEmpty(mContext.getString(R.string.push_server_url))) {
                try {
                    HomeServerConnectionConfig hsConfig = new HomeServerConnectionConfig(Uri.parse(mContext.getString(R.string.push_server_url)));
                    hsConfig.setCredentials(session.getCredentials());
                    pushersRestClient = new PushersRestClient(hsConfig);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## getPushersRestClient() failed " + e.getMessage(), e);
                }
            }

            if (null == pushersRestClient) {
                pushersRestClient = session.getPushersRestClient();
            }

            mPushersRestClients.put(session.getMyUserId(), pushersRestClient);
        }

        return pushersRestClient;
    }

    /**
     * Check if the GCM registration has been broken with a new token ID.
     * The GCM could have cleared it (onTokenRefresh).
     */
    public void checkRegistrations() {
        Log.d(LOG_TAG, "checkRegistrations with state " + mRegistrationState);

        if (!useGCM()) {
            Log.d(LOG_TAG, "checkRegistrations : GCM is disabled");
            return;
        }

        // remove the GCM registration token after switching to the FCM one
        if (null != getOldStoredRegistrationToken()) {
            Log.d(LOG_TAG, "checkRegistrations : remove the GCM registration token after switching to the FCM one");

            mRegistrationToken = getOldStoredRegistrationToken();

            addSessionsRegistrationListener(new ThirdPartyRegistrationListener() {
                @Override
                public void onSuccess() {
                    Log.d(LOG_TAG, "resetGCMRegistration : remove the GCM registration token done");
                    clearOldStoredRegistrationToken();
                    mRegistrationToken = null;

                    // reset the registration state
                    setAndStoreRegistrationState(RegistrationState.UNREGISTRATED);
                    // try again
                    checkRegistrations();
                }

                @Override
                public void onError() {
                    // Ignore any error
                    onSuccess();
                }
            });

            unregister(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), 0);
        } else if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            Log.d(LOG_TAG, "checkPusherRegistration : try to register to GCM server");

            registerToGcm(new GcmRegistrationListener() {
                @Override
                public void onGCMRegistered() {
                    Log.d(LOG_TAG, "checkRegistrations : reregistered");
                    CommonActivityUtils.onGcmUpdate(mContext);
                }

                @Override
                public void onGCMRegistrationFailed() {
                    Log.d(LOG_TAG, "checkRegistrations : onPusherRegistrationFailed");
                }
            });
        } else if (mRegistrationState == RegistrationState.GCM_REGISTERED) {
            // register the 3rd party server
            // the server registration might have failed
            // so ensure that it will be done when the application is de-backgrounded.
            if (useGCM() && areDeviceNotificationsAllowed()) {
                register(null);
            }
        } else if (mRegistrationState == RegistrationState.SERVER_REGISTERED) {
            refreshPushersList(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), null);
        }
    }

    //================================================================================
    // GCM registration
    //================================================================================

    /**
     * Retrieve the GCM registration token.
     *
     * @return the GCM registration token
     */
    private String getGcmRegistrationToken() {
        String registrationToken = getStoredRegistrationToken();

        if (TextUtils.isEmpty(registrationToken)) {
            Log.d(LOG_TAG, "## getGcmRegistrationToken() : undefined token -> getting a new one");
            registrationToken = GCMHelper.getRegistrationToken();
        }
        return registrationToken;
    }

    /**
     * Register to GCM.
     *
     * @param gcmRegistrationListener the events listener.
     */
    private void registerToGcm(final @NonNull GcmRegistrationListener gcmRegistrationListener) {
        Log.d(LOG_TAG, "registerToGcm with state " + mRegistrationState);

        // do not use GCM
        if (!useGCM()) {
            Log.d(LOG_TAG, "registerPusher : GCM is disabled");

            // warn the listener
            try {
                gcmRegistrationListener.onGCMRegistrationFailed();
            } catch (Exception e) {
                Log.e(LOG_TAG, "registerToGcm : onGCMRegistrationFailed failed " + e.getMessage(), e);
            }
            return;
        }

        if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            setAndStoreRegistrationState(RegistrationState.GCM_REGISTRATING);

            String token = getGcmRegistrationToken();

            setAndStoreRegistrationState((token != null ? RegistrationState.GCM_REGISTERED : RegistrationState.UNREGISTRATED));

            setAndStoreRegistrationToken(token);

            // warn the listener
            try {
                if (mRegistrationToken != null) {
                    gcmRegistrationListener.onGCMRegistered();
                } else {
                    gcmRegistrationListener.onGCMRegistrationFailed();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "registerToGcm : onGCMRegistered/onGCMRegistrationFailed failed " + e.getMessage(), e);
            }

            if (mRegistrationState == RegistrationState.GCM_REGISTERED) {
                // register the sessions to the 3rd party server
                // this setting should be updated from the listener
                if (useGCM()) {
                    register(null);
                }
            }
        } else if (mRegistrationState == RegistrationState.GCM_REGISTRATING) {
            // Please wait
            gcmRegistrationListener.onGCMRegistrationFailed();
        } else {
            gcmRegistrationListener.onGCMRegistered();
        }
    }

    /**
     * Reset the GCM registration.
     */
    public void resetGCMRegistration() {
        resetGCMRegistration(null);
    }

    /**
     * Reset the GCM registration.
     *
     * @param newToken the new registration token, to register again, or null
     */
    public void resetGCMRegistration(final @Nullable String newToken) {
        Log.d(LOG_TAG, "resetGCMRegistration");

        if (RegistrationState.SERVER_REGISTERED == mRegistrationState) {
            Log.d(LOG_TAG, "resetGCMRegistration : unregister server before retrieving the new GCM key");

            unregister(new ThirdPartyRegistrationListener() {
                @Override
                public void onSuccess() {
                    Log.d(LOG_TAG, "resetGCMRegistration : un-registration is done --> start the registration process");
                    resetGCMRegistration(newToken);
                }

                @Override
                public void onError() {
                    Log.d(LOG_TAG, "resetGCMRegistration : un-registration failed.");
                }
            });
        } else {
            final boolean clearEverything = TextUtils.isEmpty(newToken);

            Log.d(LOG_TAG, "resetGCMRegistration : Clear the GCM data");
            clearGCMData(clearEverything, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    if (!clearEverything) {
                        Log.d(LOG_TAG, "resetGCMRegistration : make a full registration process.");
                        register(null);
                    } else {
                        Log.d(LOG_TAG, "resetGCMRegistration : Ready to register.");
                    }
                }
            });
        }
    }

    //================================================================================
    // third party registration management
    //================================================================================

    /**
     * Compute the profileTag for a session
     *
     * @param session the session
     * @return the profile tag
     */
    private static String computePushTag(final MXSession session) {
        String tag = DEFAULT_PUSHER_FILE_TAG + "_" + Math.abs(session.getMyUserId().hashCode());

        // tag max length : 32 bytes
        if (tag.length() > 32) {
            tag = Math.abs(tag.hashCode()) + "";
        }

        return tag;
    }

    /**
     * Manage the 500 http error case.
     */
    private void manage500Error() {
        Log.d(LOG_TAG, "got a 500 error -> reset the registration and try again");

        Timer relaunchTimer = new Timer();

        // wait 30 seconds before registering
        relaunchTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (RegistrationState.SERVER_REGISTERED == mRegistrationState) {
                    Log.d(LOG_TAG, "500 error : unregister first");

                    unregister(new ThirdPartyRegistrationListener() {
                        @Override
                        public void onSuccess() {
                            Log.d(LOG_TAG, "500 error: unregister success");

                            setAndStoreRegistrationToken(null);
                            setAndStoreRegistrationState(RegistrationState.UNREGISTRATED);
                            register(null);
                        }

                        @Override
                        public void onError() {
                            Log.d(LOG_TAG, "500 error : unregister error");

                            setAndStoreRegistrationToken(null);
                            setAndStoreRegistrationState(RegistrationState.UNREGISTRATED);
                            register(null);
                        }
                    });

                } else {
                    Log.d(LOG_TAG, "500 error : no GCM key");

                    setAndStoreRegistrationToken(null);
                    setAndStoreRegistrationState(RegistrationState.UNREGISTRATED);
                    register(null);
                }
            }
        }, 30 * 1000);
    }


    /**
     * Force sessions registration
     */
    public void onAppResume() {
        if (mRegistrationState == RegistrationState.SERVER_REGISTERED) {
            Log.d(LOG_TAG, "## onAppResume() : force the GCM registration");

            forceSessionsRegistration(null);
        }
    }

    /**
     * Register the session to the 3rd-party app server
     *
     * @param session  the session to register.
     * @param listener the registration listener
     */
    private void registerToThirdPartyServer(final MXSession session,
                                            final boolean append,
                                            @NonNull final ThirdPartyRegistrationListener listener) {
        // test if the push server registration is allowed
        if (!areDeviceNotificationsAllowed() || !useGCM() || !session.isAlive()) {
            if (!areDeviceNotificationsAllowed()) {
                Log.d(LOG_TAG, "registerPusher : the user disabled it.");
            } else if (!session.isAlive()) {
                Log.d(LOG_TAG, "registerPusher : the session is not anymore alive");
            } else {
                Log.d(LOG_TAG, "registerPusher : GCM is disabled.");
            }

            try {
                listener.onError();
            } catch (Exception e) {
                Log.e(LOG_TAG, "registerToThirdPartyServer failed " + e.getMessage(), e);
            }

            // fallback to the GCM_REGISTERED state
            // thus, the client will try again to register with checkRegistrations.
            setAndStoreRegistrationState(RegistrationState.GCM_REGISTERED);

            return;
        }

        Log.d(LOG_TAG, "registerToThirdPartyServer of " + session.getMyUserId());

        // send only the event id but not the event content if:
        // - the user let the app run in background to fetch the event content from the homeserver
        // - or, if the app cannot run in background, the user does not want to send event content to GCM
        boolean eventIdOnlyPushes = isBackgroundSyncAllowed() || !isContentSendingAllowed();

        getPushersRestClient(session).addHttpPusher(mRegistrationToken,
                DEFAULT_PUSHER_APP_ID,
                computePushTag(session),
                mPusherLang,
                mPusherAppName,
                mBasePusherDeviceName,
                DEFAULT_PUSHER_URL,
                append,
                eventIdOnlyPushes,
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        Log.d(LOG_TAG, "registerToThirdPartyServer succeeded");

                        try {
                            listener.onSuccess();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "onSessionRegistered failed " + e.getMessage(), e);
                        }
                    }

                    private void onError(final String message) {
                        Log.e(LOG_TAG, "registerToThirdPartyServer failed" + session.getMyUserId() + " (" + message + ")");

                        // fallback to the GCM_REGISTERED state
                        // thus, the client will try again to register with checkRegistrations.
                        setAndStoreRegistrationState(RegistrationState.GCM_REGISTERED);

                        try {
                            listener.onError();
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "onThirdPartyRegistrationFailed failed " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "registerToThirdPartyServer onNetworkError " + e.getMessage(), e);
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (mRegistrationState == RegistrationState.SERVER_REGISTRATING) {
                                    Log.e(LOG_TAG, "registerToThirdPartyServer onNetworkError -> retry");
                                    registerToThirdPartyServer(session, append, listener);
                                }
                            }
                        }, 30 * 1000);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "registerToThirdPartyServer onMatrixError " + e.errcode);
                        onError(e.getMessage());

                        if (MatrixError.UNKNOWN.equals(e.errcode)) {
                            manage500Error();
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "registerToThirdPartyServer onUnexpectedError " + e.getMessage(), e);
                        onError(e.getMessage());
                    }
                });
    }

    /**
     * Refresh the pushers list (i.e the devices which expect to have notification).
     *
     * @param sessions the sessions
     * @param callback the callback
     */
    public void refreshPushersList(List<MXSession> sessions, final ApiCallback<Void> callback) {
        if (null != sessions && !sessions.isEmpty()) {
            getPushersRestClient(sessions.get(0)).getPushers(new ApiCallback<PushersResponse>() {

                @Override
                public void onSuccess(PushersResponse pushersResponse) {
                    if (null == pushersResponse.pushers) {
                        mPushersList = new ArrayList<>();
                    } else {
                        mPushersList = new ArrayList<>(pushersResponse.pushers);

                        // move the self pusher to the top of the list
                        Pusher selfPusher = null;

                        for (Pusher pusher : mPushersList) {
                            if (TextUtils.equals(pusher.pushkey, getGcmRegistrationToken())) {
                                selfPusher = pusher;
                                break;
                            }
                        }

                        if (null != selfPusher) {
                            mPushersList.remove(selfPusher);
                            mPushersList.add(0, selfPusher);
                        }
                    }

                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "refreshPushersList failed " + e.getMessage(), e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "refreshPushersList failed " + e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "refreshPushersList failed " + e.getMessage(), e);
                }
            });
        }
    }

    /**
     * Force to register the sessions to the third party servers.
     * The GCM registration must have been done and there is no pending registration.
     *
     * @param listener the listener
     */
    public void forceSessionsRegistration(@Nullable ThirdPartyRegistrationListener listener) {
        if (mRegistrationState == RegistrationState.SERVER_REGISTERED
                || mRegistrationState == RegistrationState.GCM_REGISTERED) {
            setAndStoreRegistrationState(RegistrationState.GCM_REGISTERED);

            register(listener);
        } else {
            if (null != listener) {
                try {
                    listener.onError();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "forceSessionsRegistration failed " + e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Register the current sessions to the 3rd party GCM server
     *
     * @param listener the registration listener.
     */
    public void register(@Nullable final ThirdPartyRegistrationListener listener) {
        Log.d(LOG_TAG, "register with state " + mRegistrationState);

        addSessionsRegistrationListener(listener);

        switch (mRegistrationState) {
            case UNREGISTRATED:
                Log.d(LOG_TAG, "register unregistrated : try to register again");

                // if the registration failed
                // try to register again
                registerToGcm(new GcmRegistrationListener() {
                    @Override
                    public void onGCMRegistered() {
                        Log.d(LOG_TAG, "GCM registration success: register on server side");
                        register(listener);
                    }

                    @Override
                    public void onGCMRegistrationFailed() {
                        Log.d(LOG_TAG, "GCM registration failed");
                        dispatchOnThirdPartyError();
                    }
                });
                break;
            case GCM_REGISTRATING:
                // please wait
                break;
            case GCM_REGISTERED:
                // check if the notifications must be displayed
                if (useGCM()
                        && areDeviceNotificationsAllowed()
                        && !TextUtils.isEmpty(mRegistrationToken)) {
                    setAndStoreRegistrationState(RegistrationState.SERVER_REGISTRATING);
                    registerToThirdPartyServer(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), 0);
                } else {
                    dispatchOnThirdPartyError();
                }
                break;
            case SERVER_REGISTRATING:
                // please wait
                break;
            case SERVER_REGISTERED:
                Log.e(LOG_TAG, "register : already registered");
                // disable the application start on device boot
                PreferencesManager.setAutoStartOnBoot(mContext, false);

                dispatchOnThirdPartySuccess();
                break;
            case SERVER_UNREGISTRATING:
                Log.e(LOG_TAG, "register : invalid state " + mRegistrationState);
                dispatchOnThirdPartyError();
                break;
        }
    }

    /**
     * Recursive method to register a MXSessions list.
     *
     * @param sessions the sessions list.
     * @param index    the index of the MX sessions to register.
     */
    private void registerToThirdPartyServer(final ArrayList<MXSession> sessions, final int index) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            Log.d(LOG_TAG, "registerSessions : all the sessions are registered");
            setAndStoreRegistrationState(RegistrationState.SERVER_REGISTERED);

            // disable the application start on device boot
            PreferencesManager.setAutoStartOnBoot(mContext, false);

            dispatchOnThirdPartySuccess();

            // get the pushers list
            refreshPushersList(sessions, null);

            // the notifications have been disabled while registering them
            if (useGCM() && !areDeviceNotificationsAllowed()) {
                // remove them
                unregister(null);
            } else {
                CommonActivityUtils.onGcmUpdate(mContext);
            }

            return;
        }

        final MXSession session = sessions.get(index);

        registerToThirdPartyServer(session, index > 0, new ThirdPartyRegistrationListener() {
            @Override
            public void onSuccess() {
                Log.d(LOG_TAG, "registerSessions : session " + session.getMyUserId() + " is registered");

                // Go on with the next index
                registerToThirdPartyServer(sessions, index + 1);
            }

            @Override
            public void onError() {
                Log.d(LOG_TAG, "registerSessions : onSessionRegistrationFailed " + session.getMyUserId());

                // fallback to the GCM_REGISTERED state
                setAndStoreRegistrationState(RegistrationState.GCM_REGISTERED);
                dispatchOnThirdPartyError();
            }
        });
    }

    /**
     * Unregister the current sessions from the 3rd party server.
     *
     * @param listener the registration listener.
     */
    public void unregister(final ThirdPartyRegistrationListener listener) {
        Log.d(LOG_TAG, "unregister with state " + mRegistrationState);

        addSessionsRegistrationListener(listener);

        if (mRegistrationState == RegistrationState.SERVER_UNREGISTRATING) {
            // please wait
        } else if (mRegistrationState != RegistrationState.SERVER_REGISTERED) {
            Log.e(LOG_TAG, "unregisterSessions : invalid state " + mRegistrationState);
            dispatchOnThirdPartyError();
        } else {
            setAndStoreRegistrationState(RegistrationState.SERVER_UNREGISTRATING);
            unregister(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), 0);
        }
    }

    /**
     * Recursive method to unregister a MXSessions list.
     *
     * @param sessions the sessions list.
     * @param index    the index of the MX sessions to register.
     */
    private void unregister(final ArrayList<MXSession> sessions, final int index) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            setAndStoreRegistrationState(RegistrationState.GCM_REGISTERED);

            // trigger a registration if the user enabled them while the un-registration was processing
            if (useGCM() && areDeviceNotificationsAllowed() && Matrix.hasValidSessions()) {
                register(null);
            } else {
                CommonActivityUtils.onGcmUpdate(mContext);
            }

            dispatchOnThirdPartySuccess();
            return;
        }

        MXSession session = sessions.get(index);

        unregister(session, new ThirdPartyRegistrationListener() {
            @Override
            public void onSuccess() {
                unregister(sessions, index + 1);
            }

            @Override
            public void onError() {
                setAndStoreRegistrationState(RegistrationState.SERVER_REGISTERED);
                dispatchOnThirdPartyError();
            }
        });
    }

    /**
     * Unregister a pusher.
     *
     * @param pusher   the pusher.
     * @param callback the asynchronous callback
     */
    public void unregister(final MXSession session, final Pusher pusher, final ApiCallback<Void> callback) {
        getPushersRestClient(session).removeHttpPusher(pusher.pushkey,
                pusher.appId,
                pusher.profileTag,
                pusher.lang,
                pusher.appDisplayName,
                pusher.deviceDisplayName,
                pusher.data.get("url"),
                new SimpleApiCallback<Void>(callback) {
                    @Override
                    public void onSuccess(Void info) {
                        mPushersRestClients.remove(session.getMyUserId());
                        refreshPushersList(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), callback);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (e.mStatus == 404) {
                            mPushersRestClients.remove(session.getMyUserId());
                            // httpPusher is not available on server side anymore so assume the removal was successful
                            onSuccess(null);
                            return;
                        }

                        super.onMatrixError(e);
                    }
                });
    }

    /**
     * Unregister a session from the 3rd-party app server
     *
     * @param session  the session.
     * @param listener the listener
     */
    public void unregister(final MXSession session, @Nullable final ThirdPartyRegistrationListener listener) {
        Log.d(LOG_TAG, "unregister " + session.getMyUserId());

        getPushersRestClient(session).removeHttpPusher(mRegistrationToken,
                DEFAULT_PUSHER_APP_ID,
                computePushTag(session),
                mPusherLang,
                mPusherAppName,
                mBasePusherDeviceName,
                DEFAULT_PUSHER_URL,
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        Log.d(LOG_TAG, "unregisterSession succeeded");

                        if (null != listener) {
                            try {
                                listener.onSuccess();
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "unregister : onSuccess() " + e.getMessage(), e);
                            }
                        }
                    }

                    private void onError(final String message) {
                        if (session.isAlive()) {
                            Log.e(LOG_TAG, "fail to unregister " + session.getMyUserId() + " (" + message + ")");

                            if (null != listener) {
                                try {
                                    listener.onError();
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "unregister : onError() " + e.getMessage(), e);
                                }
                            }
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "unregisterSession onNetworkError " + e.getMessage(), e);
                        onError(e.getMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (e.mStatus == 404) {
                            // httpPusher is not available on server side anymore so assume the removal was successful
                            onSuccess(null);
                            return;
                        }
                        Log.e(LOG_TAG, "unregisterSession onMatrixError " + e.errcode);
                        onError(e.getMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "unregisterSession onUnexpectedError " + e.getMessage(), e);
                        onError(e.getMessage());
                    }
                });
    }

    //================================================================================
    // statuses
    //================================================================================

    /**
     * Tells if GCM has a push token.
     */
    public boolean hasRegistrationToken() {
        return null != mRegistrationToken;
    }

    /**
     * @return the current registration token
     */
    public String getCurrentRegistrationToken() {
        return mRegistrationToken;
    }

    /**
     * Tell if GCM is registered i.e. ready to use
     */
    public boolean isGcmRegistered() {
        return (mRegistrationState == RegistrationState.GCM_REGISTERED)
                || (mRegistrationState == RegistrationState.SERVER_REGISTRATING)
                || (mRegistrationState == RegistrationState.SERVER_REGISTERED);
    }

    /**
     * Tells if the GCM is registered on server
     */
    public boolean isServerRegistered() {
        return mRegistrationState == RegistrationState.SERVER_REGISTERED;
    }

    /**
     * Tells if the GCM is unregistered on server
     */
    public boolean isServerUnRegistered() {
        return mRegistrationState == RegistrationState.GCM_REGISTERED;
    }

    //================================================================================
    // GCM preferences
    //================================================================================

    /**
     * Notification privacy policies as displayed to the end user.
     * In the code, this enumeration is currently implemented with combinations of booleans.
     */
    public enum NotificationPrivacy {
        /**
         * Reduced privacy: message metadata and content are sent through the push service.
         * Notifications for messages in e2e rooms are displayed with low detail.
         */
        REDUCED,

        /**
         * Notifications are displayed with low detail (X messages in RoomY).
         * Only message metadata is sent through the push service.
         */
        LOW_DETAIL,

        /**
         * Normal: full detailed notifications by keeping user privacy.
         * Only message metadata is sent through the push service. The app then makes a sync in bg
         * with the homeserver.
         */
        NORMAL

        // Some hints for future usage
        //UNKNOWN,              // the policy has not been set yet
        //NO_NOTIFICATIONS,     // no notifications

        // TODO: This enum could turn into an enum class with methods like isContentSendingAllowed()
    }

    /**
     * Clear the GCM preferences
     */
    public void clearPreferences() {
        getGcmSharedPreferences()
                .edit()
                .clear()
                .apply();
    }

    /**
     * Tells if the client prefers GCM over events polling thread.
     *
     * @return true to use GCM before using the events polling thread, false otherwise
     */
    public boolean useGCM() {
        if (null == mUseGCM) {
            mUseGCM = true;

            try {
                mUseGCM = TextUtils.equals(mContext.getString(R.string.allow_gcm_use), "true");
            } catch (Exception e) {
                Log.e(LOG_TAG, "useGCM " + e.getMessage(), e);
            }
        }
        return mUseGCM;
    }

    /**
     * @return the current notification privacy setting as displayed to the end user.
     */
    public NotificationPrivacy getNotificationPrivacy() {
        NotificationPrivacy notificationPrivacy = NotificationPrivacy.LOW_DETAIL;

        boolean isContentSendingAllowed = isContentSendingAllowed();
        boolean isBackgroundSyncAllowed = isBackgroundSyncAllowed();

        if (isContentSendingAllowed && !isBackgroundSyncAllowed) {
            notificationPrivacy = NotificationPrivacy.REDUCED;
        } else if (!isContentSendingAllowed && isBackgroundSyncAllowed) {
            notificationPrivacy = NotificationPrivacy.NORMAL;
        }

        return notificationPrivacy;
    }

    /**
     * Update the notification privacy setting.
     * Translate the setting displayed to end user into internal booleans.
     *
     * @param notificationPrivacy the new notification privacy.
     */
    public void setNotificationPrivacy(NotificationPrivacy notificationPrivacy) {

        switch (notificationPrivacy) {
            case REDUCED:
                setContentSendingAllowed(true);
                setBackgroundSyncAllowed(false);
                break;
            case LOW_DETAIL:
                setContentSendingAllowed(false);
                setBackgroundSyncAllowed(false);
                break;
            case NORMAL:
                setContentSendingAllowed(false);
                setBackgroundSyncAllowed(true);
                break;
        }

        forceSessionsRegistration(null);
    }

    /**
     * @return true the notifications must be triggered on this device
     */
    public boolean areDeviceNotificationsAllowed() {
        return getGcmSharedPreferences().getBoolean(PREFS_ALLOW_NOTIFICATIONS, true);
    }

    /**
     * Update the device notifications management.
     *
     * @param areAllowed true to enable the device notifications.
     */
    public void setDeviceNotificationsAllowed(boolean areAllowed) {
        getGcmSharedPreferences()
                .edit()
                .putBoolean(PREFS_ALLOW_NOTIFICATIONS, areAllowed)
                .apply();

        if (!useGCM()) {
            // when GCM is disabled, enable / disable the "Listen for events" notifications
            CommonActivityUtils.onGcmUpdate(mContext);
        }
    }

    /**
     * @return true if the notifications should turn the screen on for 3 seconds.
     */
    public boolean isScreenTurnedOn() {
        return getGcmSharedPreferences().getBoolean(PREFS_TURN_SCREEN_ON, false);
    }

    /**
     * Update the screen on management when a notification is received.
     *
     * @param flag true to enable the device notifications.
     */
    public void setScreenTurnedOn(boolean flag) {
        getGcmSharedPreferences()
                .edit()
                .putBoolean(PREFS_TURN_SCREEN_ON, flag)
                .apply();
    }

    /**
     * Tell if the application can run in background.
     * It depends on the app settings and the `IgnoringBatteryOptimizations` permission.
     *
     * @return true if the background sync is allowed
     */
    public boolean isBackgroundSyncAllowed() {
        // If using GCM, first check if the application has the "run in background" permission.
        // No permission, no background sync
        if (hasRegistrationToken()
                && !PreferencesManager.isIgnoringBatteryOptimizations(mContext)) {
            return false;
        }

        // then, this depends on the user setting
        return getGcmSharedPreferences().getBoolean(PREFS_ALLOW_BACKGROUND_SYNC, true);
    }

    /**
     * Allow the background sync.
     * Background sync (isBackgroundSyncAllowed) is really enabled if the "isIgnoringBatteryOptimizations"
     * permission has been granted.
     *
     * @param isAllowed true to allow the background sync.
     */
    public void setBackgroundSyncAllowed(boolean isAllowed) {
        getGcmSharedPreferences()
                .edit()
                .putBoolean(PREFS_ALLOW_BACKGROUND_SYNC, isAllowed)
                .apply();

        // when GCM is disabled, enable / disable the "Listen for events" notifications
        CommonActivityUtils.onGcmUpdate(mContext);
    }

    /**
     * Tell if the application can be restarted in background
     *
     * @return true if the application can be restarted in background
     */
    public boolean canStartAppInBackground() {
        return isBackgroundSyncAllowed() || null != getStoredRegistrationToken();
    }

    /**
     * @return true if the non encrypted content may be sent through Google services servers
     */
    public boolean isContentSendingAllowed() {
        return getGcmSharedPreferences().getBoolean(PREFS_ALLOW_SENDING_CONTENT_TO_GCM, true);
    }

    /**
     * Allow or not to send unencrypted content through Google services servers
     *
     * @param isAllowed true to allow the content sending.
     */
    public void setContentSendingAllowed(boolean isAllowed) {
        getGcmSharedPreferences()
                .edit()
                .putBoolean(PREFS_ALLOW_SENDING_CONTENT_TO_GCM, isAllowed)
                .apply();
    }

    /**
     * @return the sync timeout in ms.
     */
    public int getBackgroundSyncTimeOut() {
        return getGcmSharedPreferences().getInt(PREFS_SYNC_TIMEOUT, 6000);
    }

    /**
     * @param syncDelay the new sync delay in ms.
     */
    public void setBackgroundSyncTimeOut(int syncDelay) {
        getGcmSharedPreferences()
                .edit()
                .putInt(PREFS_SYNC_TIMEOUT, syncDelay)
                .apply();
    }

    /**
     * @return the delay between two syncs in ms.
     */
    public int getBackgroundSyncDelay() {
        // on fdroid version, the default sync delay is about 1 minutes
        // set a large value because many users don't know it can be defined from the settings page
        if ((null == mRegistrationToken) && (null == getStoredRegistrationToken()) && !getGcmSharedPreferences().contains(PREFS_SYNC_DELAY)) {
            return 60 * 1000;
        } else {
            int currentValue = 0;
            MXSession session = Matrix.getInstance(mContext).getDefaultSession();

            if (null != session) {
                currentValue = session.getSyncDelay();
            }

            return getGcmSharedPreferences().getInt(PREFS_SYNC_DELAY, currentValue);
        }
    }

    /**
     * @param syncDelay the delay between two syncs in ms.
     */
    public void setBackgroundSyncDelay(int syncDelay) {
        // 0 means wait to have a push
        if (null == mRegistrationToken) {
            syncDelay = Math.max(syncDelay, 1000);
        }

        getGcmSharedPreferences()
                .edit()
                .putInt(PREFS_SYNC_DELAY, syncDelay)
                .apply();
    }

    //================================================================================
    // GCM push key
    //================================================================================

    /**
     * @return the GCM preferences
     */
    private SharedPreferences getGcmSharedPreferences() {
        return mContext.getSharedPreferences(PREFS_GCM, Context.MODE_PRIVATE);
    }

    /**
     * @return the GCM registration stored for this version of the app or null if none is stored.
     */
    private String getStoredRegistrationToken() {
        return getGcmSharedPreferences().getString(PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM, null);
    }

    /**
     * @return the old registration token (after updating GCM to FCM)
     */
    private String getOldStoredRegistrationToken() {
        return getGcmSharedPreferences().getString(PREFS_PUSHER_REGISTRATION_TOKEN_KEY, null);
    }

    /**
     * Remove the old registration token
     */
    private void clearOldStoredRegistrationToken() {
        Log.d(LOG_TAG, "Remove old registration token");

        getGcmSharedPreferences()
                .edit()
                .remove(PREFS_PUSHER_REGISTRATION_TOKEN_KEY)
                .apply();
    }

    /**
     * Set the GCM registration for the currently-running version of this app.
     *
     * @param registrationToken the registration token
     */
    private void setAndStoreRegistrationToken(String registrationToken) {
        Log.d(LOG_TAG, "Saving registration token");

        mRegistrationToken = registrationToken;

        getGcmSharedPreferences()
                .edit()
                .putString(PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM, registrationToken)
                .apply();
    }

    /**
     * @return the registration status
     */
    private RegistrationState getStoredRegistrationState() {
        return RegistrationState.values()[getGcmSharedPreferences().getInt(PREFS_PUSHER_REGISTRATION_STATUS, RegistrationState.UNREGISTRATED.ordinal())];
    }

    /**
     * Update the stored registration state.
     *
     * @param state the new state
     */
    private void setAndStoreRegistrationState(RegistrationState state) {
        mRegistrationState = state;

        // do not store the .ing state
        if ((RegistrationState.GCM_REGISTRATING != state) &&
                (RegistrationState.SERVER_REGISTRATING != state) &&
                (RegistrationState.SERVER_UNREGISTRATING != state)) {

            getGcmSharedPreferences()
                    .edit()
                    .putInt(PREFS_PUSHER_REGISTRATION_STATUS, state.ordinal())
                    .apply();
        }
    }

    /**
     * Clear the GCM data
     *
     * @param clearRegistrationToken true to clear the provided GCM token
     * @param callback               the asynchronous callback
     */
    public void clearGCMData(boolean clearRegistrationToken, @NonNull ApiCallback<Void> callback) {
        try {
            setAndStoreRegistrationToken(null);
            setAndStoreRegistrationState(RegistrationState.UNREGISTRATED);

            if (clearRegistrationToken) {
                GCMHelper.clearRegistrationToken();
            }

            callback.onSuccess(null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## clearGCMData failed " + e.getMessage(), e);

            callback.onUnexpectedError(e);
        }
    }

    //================================================================================
    // Events dispatcher
    //================================================================================

    /**
     * Add a listener to the third party server.
     *
     * @param listener the new listener.
     */
    private void addSessionsRegistrationListener(final ThirdPartyRegistrationListener listener) {
        synchronized (this) {
            if (null != listener && !mThirdPartyRegistrationListeners.contains(listener)) {
                mThirdPartyRegistrationListeners.add(listener);
            }
        }
    }

    /**
     * Dispatch the onThirdPartyRegistered to the listeners.
     */
    private void dispatchOnThirdPartySuccess() {
        synchronized (this) {
            for (ThirdPartyRegistrationListener listener : mThirdPartyRegistrationListeners) {
                try {
                    listener.onSuccess();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "dispatchOnThirdPartySuccess " + e.getMessage(), e);
                }
            }

            mThirdPartyRegistrationListeners.clear();
        }
    }

    /**
     * Dispatch the onThirdPartyRegistrationFailed to the listeners.
     */
    private void dispatchOnThirdPartyError() {
        synchronized (this) {
            for (ThirdPartyRegistrationListener listener : mThirdPartyRegistrationListeners) {
                try {
                    listener.onError();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "dispatchOnThirdPartyError " + e.getMessage(), e);
                }
            }

            mThirdPartyRegistrationListeners.clear();
        }
    }
}
