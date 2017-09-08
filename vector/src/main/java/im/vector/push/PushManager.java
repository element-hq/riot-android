package im.vector.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PushersResponse;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;
import im.vector.util.PreferencesManager;
import retrofit.RetrofitError;


/**
 * Helper class to handle the Push notification systems {@link SharedPreferences}
 */
public abstract class PushManager {
    private static final String LOG_TAG = "PushManager";

    private static final String PREFS_NOTIFICATION = "PushManager";

    // GcmRegistrationManager because of history. TODO: Write migration for old settings to PushManager
    private static final String PREFS_ALLOW_NOTIFICATIONS = "GcmRegistrationManager.PREFS_ALLOW_NOTIFICATIONS";
    private static final String PREFS_TURN_SCREEN_ON = "GcmRegistrationManager.PREFS_TURN_SCREEN_ON";
    private static final String PREFS_ALLOW_BACKGROUND_SYNC = "GcmRegistrationManager.PREFS_ALLOW_BACKGROUND_SYNC";

    private static final String PREFS_PUSHER_REGISTRATION_TOKEN_KEY = "PREFS_PUSHER_REGISTRATION_TOKEN_KEY";

    private static final String PREFS_SYNC_TIMEOUT = "PushManager.PREFS_SYNC_TIMEOUT";
    private static final String PREFS_SYNC_DELAY = "PushManager.PREFS_SYNC_DELAY";

    private static final String DEFAULT_PUSHER_APP_ID = "im.vector.app.android";
    private static final String DEFAULT_PUSHER_URL = "https://matrix.org/_matrix/push/v1/notify";
    private static final String DEFAULT_PUSHER_FILE_TAG = "mobile";

    /**
     * Push registration interface
     */
    public interface PushRegistrationListener {
        // Push is properly registered.
        void onPushRegistered();
        // Push registration fails.
        void onPushRegistrationFailed();
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
    protected enum RegistrationState {
        UNREGISTRATED,
        PUSH_REGISTRATING,
        PUSH_REGISTRED,
        SERVER_REGISTRATING,
        SERVER_REGISTERED,
        SERVER_UNREGISTRATING,
    }

    // the pusher base
    private static final String mBasePusherDeviceName = Build.MODEL.trim();

    // the context
    protected final Context mContext;

    // the registration state
    protected RegistrationState mRegistrationState = RegistrationState.UNREGISTRATED;

    // defines the Push registration token
    private String mPushKey = null;

    // 3 states : null not initialized (retrieved by flavor)
    private static Boolean mUsePush;

    /**
     * Constructor
     * @param appContext the application context.
     */
    public PushManager(final Context appContext) {
        mContext = appContext.getApplicationContext();

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
                    if (usePush()) {
                        // test if the user expect having notifications on his device but it was not yet done
                        if (areDeviceNotificationsAllowed() && (mRegistrationState == RegistrationState.PUSH_REGISTRED)) {
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
     * Check if the push registration has been set up.
     * The Push could have cleared it (onTokenRefresh).
     */
    public abstract void checkRegistrations();

    //================================================================================
    // Push registration
    //================================================================================

    /**
     * Retrieve the Push registration token.
     * @return the Push registration token
     */
    public abstract String getPushRegistrationToken();

    /**
     * Reset the Push registration.
     */
    public void resetPushServiceRegistration() {
        resetPushServiceRegistration(null);
    }

    /**
     * Reset the Push registration.
     * @param newToken the new registration token
     */
    public abstract void resetPushServiceRegistration(final String newToken);

    //================================================================================
    // third party registration management
    //================================================================================

    /**
     * Compute the profileTag for a session
     * @param session the session
     * @return the profile tag
     */
    protected static String computePushTag(final MXSession session) {
        String tag = DEFAULT_PUSHER_FILE_TAG + "_" + Math.abs(session.getMyUserId().hashCode());

        // tag max length : 32 bytes
        if (tag.length() > 32) {
            tag = Math.abs(tag.hashCode()) + "";
        }

        return tag;
    }

    /**
     * Register the session to the 3rd-party app server
     * @param session the session to register.
     * @param listener the registration listener
     */
    protected void registerToThirdPartyServer(final MXSession session, boolean append, final ThirdPartyRegistrationListener listener) {
        // test if the push server registration is allowed
        if (!areDeviceNotificationsAllowed() || !usePush()) {
            if (!areDeviceNotificationsAllowed()) {
                Log.d(LOG_TAG, "registerPusher : the user disabled it.");
            }  else {
                Log.d(LOG_TAG, "registerPusher : Push is disabled.");
            }

            if (null != listener) {
                try {
                    listener.onThirdPartyRegistrationFailed();
                }  catch (Exception e) {
                    Log.e(LOG_TAG, "registerToThirdPartyServer failed " + e.getLocalizedMessage());
                }
            }

            // fallback to the PUSH_REGISTRED state
            // thus, the client will try again to register with checkRegistrations.
            mRegistrationState = RegistrationState.PUSH_REGISTRED;

            return;
        }

        Log.d(LOG_TAG, "registerToThirdPartyServer of " + session.getMyUserId());

        session.getPushersRestClient()
                .addHttpPusher(mPushKey, DEFAULT_PUSHER_APP_ID, computePushTag(session),
                        mPusherLang, mPusherAppName, mBasePusherDeviceName,
                        DEFAULT_PUSHER_URL, append, new ApiCallback<Void>() {
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

                                // fallback to the PUSH_REGISTRED state
                                // thus, the client will try again to register with checkRegistrations.
                                mRegistrationState = RegistrationState.PUSH_REGISTRED;

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
                                    // or the Push key seems triggering error on server side.
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
                            if (TextUtils.equals(pusher.pushkey, getPushRegistrationToken())) {
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
     * The Push registration must have been done and there is no pending registration.
     * @param listener the listener
     */
    public void forceSessionsRegistration(final ThirdPartyRegistrationListener listener) {
        if ((mRegistrationState == RegistrationState.SERVER_REGISTERED) || (mRegistrationState == RegistrationState.PUSH_REGISTRED)){
            mRegistrationState = RegistrationState.PUSH_REGISTRED;

            register(listener);
        } else {
            if (null != listener) {
                try {
                    listener.onThirdPartyRegistrationFailed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "forceSessionsRegistration failed " + e.getLocalizedMessage());
                }
            }
        }
    }

    /**
     * Register the current sessions to the 3rd party Push server
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
            registerToPushService(new PushRegistrationListener() {
                @Override
                public void onPushRegistered() {
                    Log.d(LOG_TAG, "Push registration failed again : register on server side");
                    register(listener);
                }

                @Override
                public void onPushRegistrationFailed() {
                    Log.d(LOG_TAG, "register unregistrated : Push registration failed again");
                    dispatchOnThirdPartyRegistrationFailed();
                }
            });
        } else if (mRegistrationState == RegistrationState.SERVER_REGISTERED) {
            Log.e(LOG_TAG, "register : already registred");
            dispatchOnThirdPartyRegistered();
        } else if (mRegistrationState != RegistrationState.PUSH_REGISTRED) {
            Log.e(LOG_TAG, "register : invalid state " + mRegistrationState);
            dispatchOnThirdPartyRegistrationFailed();
        } else {
            // check if the notifications must be displayed
            if (usePush() && areDeviceNotificationsAllowed()) {
                mRegistrationState = RegistrationState.SERVER_REGISTRATING;
                registerToThirdPartyServer(new ArrayList<>(Matrix.getInstance(mContext).getSessions()), 0);
            } else {
                dispatchOnThirdPartyRegistrationFailed();
            }
        }
    }

    /**
     * Register to Push Service.
     * @param pushRegistrationListener the events listener.
     */
    protected void registerToPushService(final PushRegistrationListener pushRegistrationListener) {
        Log.d(LOG_TAG, "registerToPushService with state " + mRegistrationState);

        // do not use Push
        if (!usePush()) {
            Log.d(LOG_TAG, "registerPusher : Push is disabled");

            // warn the listener
            if (null != pushRegistrationListener) {
                try {
                    pushRegistrationListener.onPushRegistrationFailed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "registerToPushService : onPusherRegistered/onPusherRegistrationFailed failed " + e.getLocalizedMessage());
                }
            }
            return;
        }
        if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            mRegistrationState = RegistrationState.PUSH_REGISTRATING;

            try {
                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... voids) {
                        String registrationToken = getPushRegistrationToken();

                        if (registrationToken != null) {
                            mPushKey = registrationToken;
                        }

                        return registrationToken;
                    }

                    @Override
                    protected void onPostExecute(String pushKey) {
                        mRegistrationState = (pushKey != null) ? RegistrationState.PUSH_REGISTRED : RegistrationState.UNREGISTRATED;
                        setStoredRegistrationToken(pushKey);

                        // warn the listener
                        if (null != pushRegistrationListener) {
                            try {
                                if (pushKey != null) {
                                    pushRegistrationListener.onPushRegistered();
                                } else {
                                    pushRegistrationListener.onPushRegistrationFailed();
                                }
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "registerToPushService : onPusherRegistered/onPusherRegistrationFailed failed " + e.getMessage());
                            }
                        }

                        if (mRegistrationState == RegistrationState.PUSH_REGISTRED) {
                            // register the sessions to the 3rd party server
                            // this setting should be updated from the listener
                            if (usePush()) {
                                register(null);
                            }
                        }
                    }
                }.execute();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## registerToPushService() failed " + e.getMessage());
                // warn the listener
                if (null != pushRegistrationListener) {
                    try {
                        pushRegistrationListener.onPushRegistrationFailed();
                    } catch (Exception e2) {
                        Log.e(LOG_TAG, "registerToPushService : onPusherRegistered/onPusherRegistrationFailed failed " + e2.getMessage());
                    }
                }
            }
        } else if (mRegistrationState == RegistrationState.PUSH_REGISTRATING) {
            pushRegistrationListener.onPushRegistrationFailed();
        } else {
            pushRegistrationListener.onPushRegistered();
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
            if (usePush() && !areDeviceNotificationsAllowed()) {
                // remove them
                unregister(null);
            } else {
                CommonActivityUtils.onPushServiceUpdate(mContext);
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

                mRegistrationState = RegistrationState.PUSH_REGISTRED;
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
    protected void unregister(final ArrayList<MXSession> sessions, final int index) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            mRegistrationState = RegistrationState.PUSH_REGISTRED;

            // trigger a registration if the user disabled thme while the unregistration was processing
            if (usePush() && areDeviceNotificationsAllowed() && Matrix.hasValidSessions() ) {
                register(null);
            } else {
                CommonActivityUtils.onPushServiceUpdate(mContext);
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
                .removeHttpPusher(mPushKey, DEFAULT_PUSHER_APP_ID, computePushTag(session),
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
     * Tells if Push system has a push key.
     */
    public boolean hasRegistrationToken() {
        return null != mPushKey;
    }

    /**
     * Tell if Push is registered i.e. ready to use
     */
    public boolean isPushRegistered() {
        return (mRegistrationState == RegistrationState.PUSH_REGISTRED) ||
                (mRegistrationState == RegistrationState.SERVER_REGISTRATING) ||
                (mRegistrationState == RegistrationState.SERVER_REGISTERED);
    }

    /**
     * Tells if the Push is registrating
     */
    private boolean isPushRegistrating() {
        return (mRegistrationState == RegistrationState.SERVER_REGISTRATING) ||
                (mRegistrationState == RegistrationState.SERVER_UNREGISTRATING);
    }

    /**
     * Tells if the Push is registrered on server
     */
    public boolean isServerRegistered() {
        return mRegistrationState == RegistrationState.SERVER_REGISTERED;
    }

    /**
     * Tells if the Push is unregistrered on server
     */
    public boolean isServerUnRegistred() {
        return mRegistrationState == RegistrationState.PUSH_REGISTRED;
    }

    //================================================================================
    // Push preferences
    //================================================================================

    /**
     * Clear the Push preferences
     */
    public void clearPreferences() {
        getPushSharedPreferences().edit().clear().commit();
    }

    /**
     * Tells if the client prefers Push over events polling thread.
     * @return true to use Push service before using the events polling thread, false otherwise
     */
    public boolean usePush() {
        if (null == mUsePush) {
            mUsePush = true;

            try {
                mUsePush = TextUtils.equals(mContext.getResources().getString(R.string.allow_gcm_use), "true");
            } catch (Exception e) {
                Log.e(LOG_TAG, "usePush " + e.getLocalizedMessage());
            }
        }
        return mUsePush;
    }

    /**
     * @return true the notifications must be triggered on this device
     */
    public boolean areDeviceNotificationsAllowed() {
        return getPushSharedPreferences().getBoolean(PREFS_ALLOW_NOTIFICATIONS, true);
    }

    /**
     * Update the device notifications management.
     * @param areAllowed true to enable the device notifications.
     */
    public void setDeviceNotificationsAllowed(boolean areAllowed) {
        if (!getPushSharedPreferences().edit()
                .putBoolean(PREFS_ALLOW_NOTIFICATIONS, areAllowed)
                .commit()) {
            Log.e(LOG_TAG, "## setDeviceNotificationsAllowed () : commit failed");
        }

        if (!usePush()) {
            // when Push is disabled, enable / disable the "Listen for events" notifications
            CommonActivityUtils.onPushServiceUpdate(mContext);
        }
    }

    /**
     * @return true if the notifications should turn the screen on for 3 seconds.
     */
    public boolean isScreenTurnedOn() {
        return getPushSharedPreferences().getBoolean(PREFS_TURN_SCREEN_ON, false);
    }

    /**
     * Update the screen on management when a notification is received.
     * @param flag true to enable the device notifications.
     */
    public void setScreenTurnedOn(boolean flag) {
        if (!getPushSharedPreferences().edit()
                .putBoolean(PREFS_TURN_SCREEN_ON, flag)
                .commit()) {
            Log.e(LOG_TAG, "## setScreenTurnedOn() : commit failed");
        }
    }

    /**
     * @return true if the background sync is allowed
     */
    public boolean isBackgroundSyncAllowed() {
        return getPushSharedPreferences().getBoolean(PREFS_ALLOW_BACKGROUND_SYNC, true);
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
        if (!getPushSharedPreferences().edit()
                .putBoolean(PREFS_ALLOW_BACKGROUND_SYNC, isAllowed)
                .commit()) {
            Log.e(LOG_TAG, "## setBackgroundSyncAllowed() : commit failed");
        }

        // when Push is disabled, enable / disable the "Listen for events" notifications
        CommonActivityUtils.onPushServiceUpdate(mContext);
    }

    /**
     * @return the sync timeout in ms.
     */
    public int getBackgroundSyncTimeOut() {
        int currentValue = 30000;

        MXSession session = Matrix.getInstance(mContext).getDefaultSession();

        if (null != session) {
            currentValue = session.getSyncTimeout();
        }
        return getPushSharedPreferences().getInt(PREFS_SYNC_TIMEOUT, currentValue);
    }

    /**
     * @param syncDelay the new sync delay in ms.
     */
    public void setBackgroundSyncTimeOut(int syncDelay) {
        if (!getPushSharedPreferences().edit()
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
        if ((null == mPushKey) && (null == getStoredRegistrationToken()) && !getPushSharedPreferences().contains(PREFS_SYNC_DELAY)) {
            return 10 * 60 * 1000;
        } else {
            int currentValue = 0;
            MXSession session = Matrix.getInstance(mContext).getDefaultSession();

            if (null != session) {
                currentValue = session.getSyncDelay();
            }

            return getPushSharedPreferences().getInt(PREFS_SYNC_DELAY, currentValue);
        }
    }

    /**
     * @param syncDelay the delay between two syncs in ms.
     */
    public void setBackgroundSyncDelay(int syncDelay) {
        if (!getPushSharedPreferences().edit()
                .putInt(PREFS_SYNC_DELAY, syncDelay)
                .commit()) {
            Log.e(LOG_TAG, "## setBackgroundSyncDelay() : commit failed");
        }
    }

    //================================================================================
    // PushService push key
    //================================================================================

    /**
     * @return the Push preferences
     */
    protected SharedPreferences getPushSharedPreferences() {
        return mContext.getSharedPreferences(PREFS_NOTIFICATION, Context.MODE_PRIVATE);
    }

    /**
     * @return the registration stored for this version of the app or null if none is stored.
     */
    protected String getStoredRegistrationToken() {
        return getPushSharedPreferences().getString(PREFS_PUSHER_REGISTRATION_TOKEN_KEY, null);
    }

    /**
     * Set the Push registration for the currently-running version of this app.
     * @param registrationToken the registration token
     */
    protected void setStoredRegistrationToken(String registrationToken) {
        Log.d(LOG_TAG, "Saving registration token");

        if (!getPushSharedPreferences().edit()
                .putString(PREFS_PUSHER_REGISTRATION_TOKEN_KEY, registrationToken)
                .commit()) {
            Log.e(LOG_TAG, "## setStoredRegistrationToken() : commit failed");
        }
    }

    /**
     * Clear the Push data
     * @param clearRegistrationToken true to clear the provided Push token
     * @param callback the asynchronous callback
     */
    public abstract void clearPushData(final boolean clearRegistrationToken, final ApiCallback callback);

    //================================================================================
    // Events dispatcher
    //================================================================================

    /**
     * Add a listener to the third party server.
     * @param listener the new listener.
     */
    protected void addSessionsRegistrationListener(final ThirdPartyRegistrationListener listener) {
        synchronized (this) {
            if ((null != listener) && (mThirdPartyRegistrationListeners.indexOf(listener) == -1)) {
                mThirdPartyRegistrationListeners.add(listener);
            }
        }
    }

    /**
     * Dispatch the onThirdPartyRegistered to the listeners.
     */
    protected void dispatchOnThirdPartyRegistered() {
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
    protected void dispatchOnThirdPartyRegistrationFailed() {
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
    protected void dispatchOnThirdPartyUnregistered() {
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
    protected void dispatchOnThirdPartyUnregistrationFailed() {
        synchronized (this) {
            for (ThirdPartyRegistrationListener listener : mThirdPartyRegistrationListeners) {
                try {
                    listener.onThirdPartyUnregistrationFailed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "dispatchOnThirdPartyUnregistrationFailed " + e.getLocalizedMessage());
                }
            }

            mThirdPartyRegistrationListeners.clear();
        }
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
                if (RegistrationState.PUSH_REGISTRED == mRegistrationState) {
                    if (null != mPushKey) {
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
                        Log.d(LOG_TAG, "500 error : no Push key");

                        setStoredRegistrationToken(null);
                        mRegistrationState = RegistrationState.UNREGISTRATED;
                        register(null);
                    }
                }
            }
        }, 5000);
    }
}
