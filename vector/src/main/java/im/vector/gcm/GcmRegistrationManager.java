/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 * Copyright 2017 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
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
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PushersResponse;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.util.PreferencesManager;
import retrofit.RetrofitError;


import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager {
    private static final String LOG_TAG = "GcmRegistrationManager";

    private static final String PREFS_GCM = "GcmRegistrationManager";

    private static final String PREFS_SENDER_ID_KEY = "GcmRegistrationManager.senderId";
    private static final String PREFS_PUSHER_URL_KEY = "GcmRegistrationManager.pusherUrl";
    private static final String PREFS_PUSHER_FILE_TAG_KEY = "GcmRegistrationManager.pusherFileTag";
    private static final String PREFS_ALLOW_NOTIFICATIONS = "GcmRegistrationManager.PREFS_ALLOW_NOTIFICATIONS";
    private static final String PREFS_TURN_SCREEN_ON = "GcmRegistrationManager.PREFS_TURN_SCREEN_ON";
    private static final String PREFS_ALLOW_BACKGROUND_SYNC = "GcmRegistrationManager.PREFS_ALLOW_BACKGROUND_SYNC";

    private static final String PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM = "PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM";
    private static final String PREFS_PUSHER_REGISTRATION_TOKEN_KEY = "PREFS_PUSHER_REGISTRATION_TOKEN_KEY";

    private static final String PREFS_SYNC_TIMEOUT = "GcmRegistrationManager.PREFS_SYNC_TIMEOUT";
    private static final String PREFS_SYNC_DELAY = "GcmRegistrationManager.PREFS_SYNC_DELAY";

    private static final String DEFAULT_PUSHER_APP_ID = "im.vector.app.android";
    private static final String DEFAULT_PUSHER_URL = "https://matrix.org/_matrix/push/v1/notify";
    private static final String DEFAULT_PUSHER_FILE_TAG = "mobile";

    /**
     * GCM registration interface
     */
    public interface GCMRegistrationListener {
        // GCM is properly registered.
        void onGCMRegistered();
        // GCM registration fails.
        void onGCMRegistrationFailed();
    }

    /**
     * 3rd party server registration interface
     */
    public interface ThirdPartyRegistrationListener {
        // the third party server is registered
        void onThirdPartyRegistered();
        // the third party server registration fails.
        void onThirdPartyRegistrationFailed();

        // the third party server is unregister
        void onThirdPartyUnregistered();
        // the third party server unregistration fails
        void onThirdPartyUnregistrationFailed();
    }

    private String mPusherAppName = null;
    private String mPusherLang = null;

    // the session registration listener
    private final ArrayList<ThirdPartyRegistrationListener> mThirdPartyRegistrationListeners = new ArrayList<>();

    // the pushers list
    public ArrayList<Pusher> mPushersList = new ArrayList<>();

    /**
     * Registration steps
     */
    private enum RegistrationState {
        UNREGISTRATED,
        GCM_REGISTRATING,
        GCM_REGISTRED,
        SERVER_REGISTRATING,
        SERVER_REGISTERED,
        SERVER_UNREGISTRATING,
    }

    // the pusher base
    private final String mBasePusherDeviceName;

    // the context
    private final Context mContext;

    // the registration state
    private RegistrationState mRegistrationState = RegistrationState.UNREGISTRATED;

    // defines the GCM registration token
    private String mRegistrationToken = null;

    // 3 states : null not initialized (retrieved by flavor)
    private static Boolean mUseGCM;

    /**
     * Constructor
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
                        if (areDeviceNotificationsAllowed() && (mRegistrationState == RegistrationState.GCM_REGISTRED)) {
                            register(null);
                        } else if (!areDeviceNotificationsAllowed() && (mRegistrationState == RegistrationState.SERVER_REGISTERED)) {
                            unregister(null);
                        }
                    }
                }
            }
        });
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
                public void onThirdPartyRegistered() {
                }

                @Override
                public void onThirdPartyRegistrationFailed() {
                }

                private void onGCMUnregistred() {
                    Log.d(LOG_TAG, "resetGCMRegistration : remove the GCM registration token done");
                    clearOldStoredRegistrationToken();
                    mRegistrationToken = null;

                    // reset the registration state
                    mRegistrationState = RegistrationState.UNREGISTRATED;
                    // try again
                    checkRegistrations();
                }

                @Override
                public void onThirdPartyUnregistered() {
                    onGCMUnregistred();
                }

                @Override
                public void onThirdPartyUnregistrationFailed() {
                    onGCMUnregistred();
                }
            });

            unregister(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), 0);
        } else if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            Log.d(LOG_TAG, "checkPusherRegistration : try to register to GCM server");

            registerToGCM(new GCMRegistrationListener() {
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
        } else if (mRegistrationState == RegistrationState.GCM_REGISTRED) {
            // register the 3rd party server
            // the server registration might have failed
            // so ensure that it will be done when the application is debackgrounded.
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
     * @return the GCM registration token
     */
    public String getGCMRegistrationToken() {
        String registrationToken = getStoredRegistrationToken();

        if (TextUtils.isEmpty(registrationToken)) {
            Log.d(LOG_TAG, "## getGCMRegistrationToken() : undefined token -> getting a nex one");
            registrationToken = GCMHelper.getRegistrationToken();
        }
        return registrationToken;
    }

    /**
     * Register to GCM.
     * @param gcmRegistrationListener the events listener.
     */
    private void registerToGCM(final GCMRegistrationListener gcmRegistrationListener) {
        Log.d(LOG_TAG, "registerToGCM with state " + mRegistrationState);

        // do not use GCM
        if (!useGCM()) {
            Log.d(LOG_TAG, "registerPusher : GCM is disabled");

            // warn the listener
            if (null != gcmRegistrationListener) {
                try {
                    gcmRegistrationListener.onGCMRegistrationFailed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "registerToGCM : onPusherRegistered/onPusherRegistrationFailed failed " + e.getLocalizedMessage());
                }
            }
            return;
        }
        if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            mRegistrationState = RegistrationState.GCM_REGISTRATING;

            try {
                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... voids) {
                        String registrationToken = getGCMRegistrationToken();

                        if (registrationToken != null) {
                            mRegistrationToken = registrationToken;
                        }

                        return registrationToken;
                    }

                    @Override
                    protected void onPostExecute(String pushKey) {
                        mRegistrationState = (pushKey != null) ? RegistrationState.GCM_REGISTRED : RegistrationState.UNREGISTRATED;
                        setStoredRegistrationToken(pushKey);

                        // warn the listener
                        if (null != gcmRegistrationListener) {
                            try {
                                if (pushKey != null) {
                                    gcmRegistrationListener.onGCMRegistered();
                                } else {
                                    gcmRegistrationListener.onGCMRegistrationFailed();
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "registerToGCM : onPusherRegistered/onPusherRegistrationFailed failed " + e.getMessage());
                            }
                        }

                        if (mRegistrationState == RegistrationState.GCM_REGISTRED) {
                            // register the sessions to the 3rd party server
                            // this setting should be updated from the listener
                            if (useGCM()) {
                                register(null);
                            }
                        }
                    }
                }.execute();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## registerToGCM() failed " + e.getMessage());
                // warn the listener
                if (null != gcmRegistrationListener) {
                    try {
                        gcmRegistrationListener.onGCMRegistrationFailed();
                    } catch (Exception e2) {
                        Log.e(LOG_TAG, "registerToGCM : onPusherRegistered/onPusherRegistrationFailed failed " + e2.getMessage());
                    }
                }
            }
        } else if (mRegistrationState == RegistrationState.GCM_REGISTRATING) {
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
     * @param newToken the new registration token
     */
    public void resetGCMRegistration(final String newToken) {
        Log.d(LOG_TAG, "resetGCMRegistration");

        if (RegistrationState.SERVER_REGISTERED == mRegistrationState) {
            Log.d(LOG_TAG, "resetGCMRegistration : unregister before retrieving the new GCM key");

            unregister(new ThirdPartyRegistrationListener() {
                @Override
                public void onThirdPartyRegistered() {
                }

                @Override
                public void onThirdPartyRegistrationFailed() {
                }

                @Override
                public void onThirdPartyUnregistered() {
                    Log.d(LOG_TAG, "resetGCMRegistration : unregistration is done --> start the registration process");
                    resetGCMRegistration(newToken);
                }

                @Override
                public void onThirdPartyUnregistrationFailed() {
                    Log.d(LOG_TAG, "resetGCMRegistration : unregistration failed.");
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

        // wait 5 seconds before registering
        relaunchTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (RegistrationState.GCM_REGISTRED == mRegistrationState) {
                    if (null != mRegistrationToken) {
                        mRegistrationState = RegistrationState.SERVER_REGISTERED;
                    }

                    if (RegistrationState.SERVER_REGISTERED == mRegistrationState) {

                        Log.d(LOG_TAG, "500 error : unregister first");

                        unregister(new ThirdPartyRegistrationListener() {
                            @Override
                            public void onThirdPartyRegistered() {
                            }

                            @Override
                            public void onThirdPartyRegistrationFailed() {
                            }

                            @Override
                            public void onThirdPartyUnregistered() {
                                Log.d(LOG_TAG, "500 error : onThirdPartyUnregistered");

                                setStoredRegistrationToken(null);
                                mRegistrationState = RegistrationState.UNREGISTRATED;
                                register(null);
                            }

                            @Override
                            public void onThirdPartyUnregistrationFailed() {
                                Log.d(LOG_TAG, "500 error : onThirdPartyUnregistrationFailed");

                                setStoredRegistrationToken(null);
                                mRegistrationState = RegistrationState.UNREGISTRATED;
                                register(null);
                            }
                        });

                    } else {
                        Log.d(LOG_TAG, "500 error : no GCM key");

                        setStoredRegistrationToken(null);
                        mRegistrationState = RegistrationState.UNREGISTRATED;
                        register(null);
                    }
                }
            }
        }, 5000);
    }

    /**
     * Register the session to the 3rd-party app server
     * @param session the session to register.
     * @param listener the registration listener
     */
    private void registerToThirdPartyServer(final MXSession session, boolean append, final ThirdPartyRegistrationListener listener) {
        // test if the push server registration is allowed
        if (!areDeviceNotificationsAllowed() || !useGCM()) {
            if (!areDeviceNotificationsAllowed()) {
                Log.d(LOG_TAG, "registerPusher : the user disabled it.");
            }  else {
                Log.d(LOG_TAG, "registerPusher : GCM is disabled.");
            }

            if (null != listener) {
                try {
                    listener.onThirdPartyRegistrationFailed();
                }  catch (Exception e) {
                    Log.e(LOG_TAG, "registerToThirdPartyServer failed " + e.getLocalizedMessage());
                }
            }

            // fallback to the GCM_REGISTRED state
            // thus, the client will try again to register with checkRegistrations.
            mRegistrationState = RegistrationState.GCM_REGISTRED;

            return;
        }

        Log.d(LOG_TAG, "registerToThirdPartyServer of " + session.getMyUserId());

        session.getPushersRestClient()
                .addHttpPusher(mRegistrationToken, DEFAULT_PUSHER_APP_ID, computePushTag(session),
                        mPusherLang, mPusherAppName, mBasePusherDeviceName,
                        DEFAULT_PUSHER_URL, append, isBackgroundSyncAllowed(),  new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "registerToThirdPartyServer succeeded");

                                if (null != listener) {
                                    try {
                                        listener.onThirdPartyRegistered();
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "onSessionRegistered failed " + e.getLocalizedMessage());
                                    }
                                }
                            }

                            private void onError(final String message) {
                                Log.e(LOG_TAG, "registerToThirdPartyServer failed" + session.getMyUserId() + " (" + message + ")");

                                // fallback to the GCM_REGISTRED state
                                // thus, the client will try again to register with checkRegistrations.
                                mRegistrationState = RegistrationState.GCM_REGISTRED;

                                if (null != listener) {
                                    try {
                                        listener.onThirdPartyRegistrationFailed();
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "onThirdPartyRegistrationFailed failed " + e.getLocalizedMessage());
                                    }
                                }
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                Log.e(LOG_TAG, "registerToThirdPartyServer onNetworkError " + e.getLocalizedMessage());
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                Log.e(LOG_TAG, "registerToThirdPartyServer onMatrixError " + e.errcode);
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                Log.e(LOG_TAG, "registerToThirdPartyServer onUnexpectedError " + e.getLocalizedMessage());
                                onError(e.getLocalizedMessage());

                                // track the 500 HTTP error
                                if (e instanceof RetrofitError) {
                                    RetrofitError retrofitError = (RetrofitError)e;

                                    // an HTTP error 500 issue has been reported several times
                                    // it seems that the server is either rebooting
                                    // or the GCM key seems triggering error on server side.
                                    if ((null != retrofitError.getResponse()) && (500 == retrofitError.getResponse().getStatus())) {
                                        manage500Error();
                                    }
                                }
                            }
                        });
    }

    /**
     * Refresh the pushers list (i.e the devices which expect to have notification).
     * @param sessions the sessions
     * @param callback the callback;
     */
    public void refreshPushersList(List<MXSession> sessions, final ApiCallback<Void> callback) {
        if ((null != sessions) && (sessions.size() > 0)) {
            sessions.get(0).getPushersRestClient().getPushers(new ApiCallback<PushersResponse>() {

                @Override
                public void onSuccess(PushersResponse pushersResponse) {
                    if (null == pushersResponse.pushers) {
                        mPushersList = new ArrayList<>();
                    } else {
                        mPushersList = new ArrayList<>(pushersResponse.pushers);

                        // move the self pusher to the top of the list
                        Pusher selfPusher = null;

                        for(Pusher pusher : mPushersList) {
                            if (TextUtils.equals(pusher.pushkey, getGCMRegistrationToken())) {
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
                    Log.e(LOG_TAG, "refreshPushersList failed " + e.getLocalizedMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "refreshPushersList failed " + e.getLocalizedMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "refreshPushersList failed " + e.getLocalizedMessage());
                }
            });
        }
    }

    /**
     * Force to register the sessions to the third party servers.
     * The GCM registration must have been done and there is no pending registration.
     * @param listener the listener
     */
    public void forceSessionsRegistration(final ThirdPartyRegistrationListener listener) {
        if ((mRegistrationState == RegistrationState.SERVER_REGISTERED) || (mRegistrationState == RegistrationState.GCM_REGISTRED)) {
            mRegistrationState = RegistrationState.GCM_REGISTRED;

            register(listener);
        } else {
            if (null != listener) {
                try {
                    listener.onThirdPartyRegistrationFailed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "forceSessionsRegistration failed " + e.getMessage());
                }
            }
        }
    }

    /**
     * Register the current sessions to the 3rd party GCM server
     * @param listener the registration listener.
     */
    public void register(final ThirdPartyRegistrationListener listener) {
        Log.d(LOG_TAG, "register with state " + mRegistrationState);

        addSessionsRegistrationListener(listener);

        if (mRegistrationState == RegistrationState.SERVER_REGISTRATING) {
            // please wait
        } else if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            Log.d(LOG_TAG, "register unregistrated : try to register again");

            // if the registration failed
            // try to register again
            registerToGCM(new GCMRegistrationListener() {
                @Override
                public void onGCMRegistered() {
                    Log.d(LOG_TAG, "GCM registration failed again : register on server side");
                    register(listener);
                }

                @Override
                public void onGCMRegistrationFailed() {
                    Log.d(LOG_TAG, "register unregistrated : GCM registration failed again");
                    dispatchOnThirdPartyRegistrationFailed();
                }
            });
        } else if (mRegistrationState == RegistrationState.SERVER_REGISTERED) {
            Log.e(LOG_TAG, "register : already registred");
            dispatchOnThirdPartyRegistered();
        } else if (mRegistrationState != RegistrationState.GCM_REGISTRED) {
            Log.e(LOG_TAG, "register : invalid state " + mRegistrationState);
            dispatchOnThirdPartyRegistrationFailed();
        } else {
            // check if the notifications must be displayed
            if (useGCM() && areDeviceNotificationsAllowed()) {
                mRegistrationState = RegistrationState.SERVER_REGISTRATING;
                registerToThirdPartyServer(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), 0);
            } else {
                dispatchOnThirdPartyRegistrationFailed();
            }
        }
    }

    /**
     * Recursive method to register a MXSessions list.
     * @param sessions the sessions list.
     * @param index the index of the MX sessions to register.
     */
    private void registerToThirdPartyServer(final ArrayList<MXSession> sessions, final int index) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            Log.d(LOG_TAG, "registerSessions : all the sessions are registered");
            mRegistrationState = RegistrationState.SERVER_REGISTERED;
            dispatchOnThirdPartyRegistered();

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

        registerToThirdPartyServer(session, (index > 0), new ThirdPartyRegistrationListener() {
            @Override
            public void onThirdPartyRegistered() {
                Log.d(LOG_TAG, "registerSessions : session " + session.getMyUserId() + " is registred");
                registerToThirdPartyServer(sessions, index + 1);
            }

            @Override
            public void onThirdPartyRegistrationFailed() {
                Log.d(LOG_TAG, "registerSessions : onSessionRegistrationFailed " + session.getMyUserId());

                mRegistrationState = RegistrationState.GCM_REGISTRED;
                dispatchOnThirdPartyRegistrationFailed();
            }

            @Override
            public void onThirdPartyUnregistered() {
            }

            @Override
            public void onThirdPartyUnregistrationFailed() {
            }
        });
    }

    /**
     * Unregister the current sessions from the 3rd party server.
     * @param listener the registration listener.
     */
    public void unregister(final ThirdPartyRegistrationListener listener) {
        Log.d(LOG_TAG, "unregister with state " + mRegistrationState);

        addSessionsRegistrationListener(listener);

        if (mRegistrationState == RegistrationState.SERVER_UNREGISTRATING) {
          // please wait
        } else if (mRegistrationState != RegistrationState.SERVER_REGISTERED) {
            Log.e(LOG_TAG, "unregisterSessions : invalid state " + mRegistrationState);
            dispatchOnThirdPartyUnregistrationFailed();
        } else {
            mRegistrationState = RegistrationState.SERVER_UNREGISTRATING;
            unregister(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), 0);
        }
    }

    /**
     * Recursive method to unregister a MXSessions list.
     * @param sessions the sessions list.
     * @param index the index of the MX sessions to register.
     */
    private void unregister(final ArrayList<MXSession> sessions, final int index) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            mRegistrationState = RegistrationState.GCM_REGISTRED;

            // trigger a registration if the user disabled thme while the unregistration was processing
            if (useGCM() && areDeviceNotificationsAllowed() && Matrix.hasValidSessions() ) {
                register(null);
            } else {
                CommonActivityUtils.onGcmUpdate(mContext);
            }

            dispatchOnThirdPartyUnregistered();
            return;
        }

        MXSession session = sessions.get(index);

        unregister(session , new ThirdPartyRegistrationListener() {
            @Override
            public void onThirdPartyRegistered() {
            }

            @Override
            public void onThirdPartyRegistrationFailed() {
            }

            @Override
            public void onThirdPartyUnregistered() {
                unregister(sessions, index+1);
            }

            @Override
            public void onThirdPartyUnregistrationFailed() {
                mRegistrationState = RegistrationState.SERVER_REGISTERED;
                dispatchOnThirdPartyUnregistrationFailed();
            }
        });
    }

    /**
     * Unregister a pusher.
     * @param pusher the pusher.
     * @param callback the asynchronous callback
     */
    public void unregister(final MXSession session,  final Pusher pusher, final ApiCallback<Void> callback) {
        session.getPushersRestClient().removeHttpPusher(pusher.pushkey, pusher.appId, pusher.profileTag, pusher.lang, pusher.appDisplayName, pusher.deviceDisplayName, pusher.data.get("url"), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                refreshPushersList(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), callback);
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (e.mStatus == 404) {
                    // httpPusher is not available on server side anymore so assume the removal was successful
                    onSuccess(null);
                    return;
                }
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Unregister a session from the 3rd-party app server
     * @param session the session.
     * @param listener the listener
     */
    public void unregister(final MXSession session, final ThirdPartyRegistrationListener listener) {
        Log.d(LOG_TAG, "unregister " + session.getMyUserId());

        session.getPushersRestClient()
                .removeHttpPusher(mRegistrationToken, DEFAULT_PUSHER_APP_ID, computePushTag(session),
                        mPusherLang, mPusherAppName, mBasePusherDeviceName,
                        DEFAULT_PUSHER_URL, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "unregisterSession succeeded");

                                if (null != listener) {
                                    try {
                                        listener.onThirdPartyUnregistered();
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "unregister : onThirdPartyUnregistered " + e.getLocalizedMessage());
                                    }
                                }
                            }

                            private void onError(final String message) {
                                if (session.isAlive()) {
                                    Log.e(LOG_TAG, "fail to unregister " + session.getMyUserId() + " (" + message + ")");

                                    if (null != listener) {
                                        try {
                                            listener.onThirdPartyUnregistrationFailed();
                                        } catch (Exception e) {
                                            Log.e(LOG_TAG, "unregister : onThirdPartyUnregistrationFailed " + e.getLocalizedMessage());
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                Log.e(LOG_TAG, "unregisterSession onNetworkError " + e.getMessage());
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                if (e.mStatus == 404) {
                                    // httpPusher is not available on server side anymore so assume the removal was successful
                                    onSuccess(null);
                                    return;
                                }
                                Log.e(LOG_TAG, "unregisterSession onMatrixError " + e.errcode);
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                Log.e(LOG_TAG, "unregisterSession onUnexpectedError " + e.getMessage());
                                onError(e.getLocalizedMessage());
                            }
                        });
    }

    //================================================================================
    // statuses
    //================================================================================

    /**
     * Tells if GCM has a push key.
     */
    public boolean hasRegistrationToken() {
        return null != mRegistrationToken;
    }

    /**
     * Tell if GCM is rregistred i.e. ready to use
     */
    public boolean isGCMRegistred() {
        return (mRegistrationState == RegistrationState.GCM_REGISTRED) || (mRegistrationState == RegistrationState.SERVER_REGISTRATING) || (mRegistrationState == RegistrationState.SERVER_REGISTERED);
    }

    /**
     * Tells if the GCM is registrating
     */
    private boolean isGCMRegistrating() {
        return (mRegistrationState == RegistrationState.SERVER_REGISTRATING) || (mRegistrationState == RegistrationState.SERVER_UNREGISTRATING);
    }

    /**
     * Tells if the GCM is registrered on server
     */
    public boolean isServerRegistred() {
        return mRegistrationState == RegistrationState.SERVER_REGISTERED;
    }

    /**
     * Tells if the GCM is unregistrered on server
     */
    public boolean isServerUnRegistred() {
        return mRegistrationState == RegistrationState.GCM_REGISTRED;
    }

    //================================================================================
    // GCM preferences
    //================================================================================

    /**
     * Clear the GCM preferences
     */
    public void clearPreferences() {
        getGcmSharedPreferences().edit().clear().commit();
    }

    /**
     * Tells if the client prefers GCM over events polling thread.
     * @return true to use GCM before using the events polling thread, false otherwise
     */
    public boolean useGCM() {
        if (null == mUseGCM) {
            mUseGCM = true;

            try {
                mUseGCM = TextUtils.equals(mContext.getResources().getString(R.string.allow_gcm_use), "true");
            } catch (Exception e) {
                Log.e(LOG_TAG, "useGCM " + e.getLocalizedMessage());
            }
        }
        return mUseGCM;
    }

    /**
     * @return true the notifications must be triggered on this device
     */
    public boolean areDeviceNotificationsAllowed() {
        return getGcmSharedPreferences().getBoolean(PREFS_ALLOW_NOTIFICATIONS, true);
    }

    /**
     * Update the device notifications management.
     * @param areAllowed true to enable the device notifications.
     */
    public void setDeviceNotificationsAllowed(boolean areAllowed) {
        if (!getGcmSharedPreferences().edit()
                .putBoolean(PREFS_ALLOW_NOTIFICATIONS, areAllowed)
                .commit()) {
            Log.e(LOG_TAG, "## setDeviceNotificationsAllowed () : commit failed");
        }

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
     * @param flag true to enable the device notifications.
     */
    public void setScreenTurnedOn(boolean flag) {
        if (!getGcmSharedPreferences().edit()
                .putBoolean(PREFS_TURN_SCREEN_ON, flag)
                .commit()) {
            Log.e(LOG_TAG, "## setScreenTurnedOn() : commit failed");
        }
    }

    /**
     * @return true if the background sync is allowed
     */
    public boolean isBackgroundSyncAllowed() {
        return getGcmSharedPreferences().getBoolean(PREFS_ALLOW_BACKGROUND_SYNC, true);
    }

    /**
     * Tell if the application can be restarted in background
     * @return true if the application can be restarted in background
     */
    public boolean canStartAppInBackground() {
        return isBackgroundSyncAllowed() || (null != getStoredRegistrationToken());
    }

    /**
     * Allow the background sync
     * @param isAllowed true to allow the background sync.
     */
    public void setBackgroundSyncAllowed(boolean isAllowed) {
        if (!getGcmSharedPreferences().edit()
                .putBoolean(PREFS_ALLOW_BACKGROUND_SYNC, isAllowed)
                .commit()) {
            Log.e(LOG_TAG, "## setBackgroundSyncAllowed() : commit failed");
        }

        // when GCM is disabled, enable / disable the "Listen for events" notifications
        CommonActivityUtils.onGcmUpdate(mContext);
    }

    /**
     * @return the sync timeout in ms.
     */
    public int getBackgroundSyncTimeOut() {
        return getGcmSharedPreferences().getInt(PREFS_SYNC_TIMEOUT, 30000);
    }

    /**
     * @param syncDelay the new sync delay in ms.
     */
    public void setBackgroundSyncTimeOut(int syncDelay) {
        if (!getGcmSharedPreferences().edit()
                .putInt(PREFS_SYNC_TIMEOUT, syncDelay)
                .commit()) {
            Log.e(LOG_TAG, "## setBackgroundSyncTimeOut() : commit failed");
        }
    }

    /**
     * @return the delay between two syncs in ms.
     */
    public int getBackgroundSyncDelay() {
        // on fdroid version, the default sync delay is about 10 minutes
        // set a large value because many users don't know it can be defined from the settings page
        if ((null == mRegistrationToken) && (null == getStoredRegistrationToken()) && !getGcmSharedPreferences().contains(PREFS_SYNC_DELAY)) {
            return 10 * 60 * 1000;
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
        if (!getGcmSharedPreferences().edit()
                .putInt(PREFS_SYNC_DELAY, syncDelay)
                .commit()) {
            Log.e(LOG_TAG, "## setBackgroundSyncDelay() : commit failed");
        }
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
        if (!getGcmSharedPreferences().edit()
                .remove(PREFS_PUSHER_REGISTRATION_TOKEN_KEY)
                .commit()) {
            Log.e(LOG_TAG, "## setStoredRegistrationToken() : commit failed");
        }
    }

    /**
     * Set the GCM registration for the currently-running version of this app.
     * @param registrationToken the registration token
     */
    private void setStoredRegistrationToken(String registrationToken) {
        Log.d(LOG_TAG, "Saving registration token");

        if (!getGcmSharedPreferences().edit()
                .putString(PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM, registrationToken)
                .commit()) {
            Log.e(LOG_TAG, "## setStoredRegistrationToken() : commit failed");
        }
    }

    /**
     * Clear the GCM data
     * @param clearRegistrationToken true to clear the provided GCM token
     * @param callback the asynchronous callback
     */
    public void clearGCMData(final boolean clearRegistrationToken, final ApiCallback callback) {
        try {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    setStoredRegistrationToken(null);
                    mRegistrationToken = null;
                    mRegistrationState = RegistrationState.UNREGISTRATED;

                    if (clearRegistrationToken) {
                        GCMHelper.clearRegistrationToken();
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void nothing) {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }

                }
            }.execute();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## clearGCMData failed " + e.getMessage());

            if (null != callback) {
                callback.onUnexpectedError(e);
            }
        }
    }

    //================================================================================
    // Events dispatcher
    //================================================================================

    /**
     * Add a listener to the third party server.
     * @param listener the new listener.
     */
    private void addSessionsRegistrationListener(final ThirdPartyRegistrationListener listener) {
        synchronized (this) {
            if ((null != listener) && (mThirdPartyRegistrationListeners.indexOf(listener) == -1)) {
                mThirdPartyRegistrationListeners.add(listener);
            }
        }
    }

    /**
     * Dispatch the onThirdPartyRegistered to the listeners.
     */
    private void dispatchOnThirdPartyRegistered() {
        // disable the application start on device boot
        PreferencesManager.setAutoStartOnBoot(mContext, false);

        synchronized (this) {
            for(ThirdPartyRegistrationListener listener : mThirdPartyRegistrationListeners) {
                try {
                    listener.onThirdPartyRegistered();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onSessionsRegistered " + e.getLocalizedMessage());
                }
            }

            mThirdPartyRegistrationListeners.clear();
        }
    }

    /**
     * Dispatch the onThirdPartyRegistrationFailed to the listeners.
     */
    private void dispatchOnThirdPartyRegistrationFailed() {
        synchronized (this) {
            for(ThirdPartyRegistrationListener listener : mThirdPartyRegistrationListeners) {
                try {
                    listener.onThirdPartyRegistrationFailed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onSessionsRegistrationFailed " + e.getLocalizedMessage());
                }
            }

            mThirdPartyRegistrationListeners.clear();
        }
    }

    /**
     * Dispatch the onThirdPartyUnregistered to the listeners.
     */
    private void dispatchOnThirdPartyUnregistered() {
        synchronized (this) {
            for(ThirdPartyRegistrationListener listener : mThirdPartyRegistrationListeners) {
                try {
                    listener.onThirdPartyUnregistered();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onSessionUnregistered " + e.getLocalizedMessage());
                }
            }

            mThirdPartyRegistrationListeners.clear();
        }
    }

    /**
     * Dispatch the onThirdPartyUnregistrationFailed to the listeners.
     */
    private void dispatchOnThirdPartyUnregistrationFailed() {
        synchronized (this) {
            for(ThirdPartyRegistrationListener listener : mThirdPartyRegistrationListeners) {
                try {
                    listener.onThirdPartyUnregistrationFailed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "dispatchOnThirdPartyUnregistrationFailed " + e.getLocalizedMessage());
                }
            }

            mThirdPartyRegistrationListeners.clear();
        }
    }
}
