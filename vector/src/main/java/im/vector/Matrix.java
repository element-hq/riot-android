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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Looper;
import android.text.TextUtils;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.IMXCall;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.login.Credentials;

import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.SplashActivity;
import im.vector.activity.VectorHomeActivity;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.services.EventStreamService;
import im.vector.store.LoginStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

/**
 * Singleton to control access to the Matrix SDK and providing point of control for MXSessions.
 */
public class Matrix {
    // the log tag
    private static final String LOG_TAG = "Matrix";

    // static instance
    private static Matrix instance = null;

    // the application context
    private Context mAppContext;

    // login storage
    private LoginStorage mLoginStorage;

    // list of session
    private ArrayList<MXSession> mMXSessions;

    // GCM registration manager
    private GcmRegistrationManager mGCMRegistrationManager;

    // list of store : some sessions or activities use tmp stores
    // provide an storage to exchange them
    private ArrayList<IMXStore> mTmpStores;

    // tell if the client should be logged out
    public boolean mHasBeenDisconnected = false;

    // i.e the event has been read from another client
    private static final MXEventListener mLiveEventListener = new MXEventListener() {
        @Override
        public void onIgnoredUsersListUpdate() {
            // the application cache will be cleared at next launch if the application is not yet launched
            // else it will be done when onLiveEventsChunkProcessed will be called in VectorHomeActivity.
            VectorHomeActivity.mClearCacheRequired = true;
        }

        private boolean mRefreshUnreadCounter = false;

        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            mRefreshUnreadCounter |=  Event.EVENT_TYPE_MESSAGE.equals(event.getType()) || Event.EVENT_TYPE_RECEIPT.equals(event.getType());
        }

        @Override
        public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
            // when the client does not use GCM (ie. FDroid),
            // we need to compute the application badge values

            if ((null != instance) && (null != instance.mMXSessions)) {
                if (mRefreshUnreadCounter) {
                    GcmRegistrationManager gcmMgr = instance.getSharedGCMRegistrationManager();

                    // perform update: if the GCM is not available or if GCM registration failed
                    if ((null != gcmMgr) && (!gcmMgr.useGCM() || !gcmMgr.hasRegistrationToken())) {
                        int unreadCount = 0;

                        for (MXSession session : instance.mMXSessions) {
                            if (session.isAlive()) {
                                Collection<Room> rooms = session.getDataHandler().getStore().getRooms();

                                if (null != rooms) {
                                    for (Room room : rooms) {
                                        if ((0 != room.getNotificationCount()) || (0 != room.getHighlightCount())) {
                                            unreadCount++;
                                        }
                                    }
                                }
                            }
                        }

                        // update the badge counter
                        CommonActivityUtils.updateBadgeCount(instance.mAppContext, unreadCount);
                    }
                }

                // TODO find a way to detect which session is synced
                for (MXSession session : instance.mMXSessions) {
                    VectorApp.removeSyncingSession(session);
                }
            }

            mRefreshUnreadCounter = false;

            Log.d(LOG_TAG, "onLiveEventsChunkProcessed ");
            EventStreamService.checkDisplayedNotification();
        }
    };

    // a common call events listener
    private static final MXCallsManager.MXCallsManagerListener mCallsManagerListener = new MXCallsManager.MXCallsManagerListener() {
        private android.os.Handler mUIHandler = null;

        /**
         * @return the UI handler
         */
        private android.os.Handler getUIHandler() {
            if (null == mUIHandler) {
                mUIHandler = new android.os.Handler(Looper.getMainLooper());
            }

            return mUIHandler;
        }

        /**
         * Called when there is an incoming call within the room.
         */
        @Override
        public void onIncomingCall(final IMXCall call, final MXUsersDevicesMap<MXDeviceInfo> unknownDevices) {
            if (null != call) {
                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // can only manage one call instance.
                        if (null == VectorCallViewActivity.getActiveCall()) {
                            Log.d(LOG_TAG, "onIncomingCall with no active call");

                            VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();

                            // if the home activity does not exist : the application has been woken up by a notification)
                            if (null == homeActivity) {
                                Log.d(LOG_TAG, "onIncomingCall : the home activity does not exist -> launch it");

                                Context context = VectorApp.getInstance();

                                // clear the activity stack to home activity
                                Intent intent = new Intent(context, VectorHomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.putExtra(VectorHomeActivity.EXTRA_CALL_SESSION_ID, call.getSession().getMyUserId());
                                intent.putExtra(VectorHomeActivity.EXTRA_CALL_ID, call.getCallId());
                                if (null != unknownDevices) {
                                    intent.putExtra(VectorHomeActivity.EXTRA_CALL_UNKNOWN_DEVICES, unknownDevices);
                                }
                                context.startActivity(intent);
                            } else {
                                Log.d(LOG_TAG, "onIncomingCall : the home activity exists : but permissions have to be checked before");
                                // check incoming call required permissions, before allowing the call..
                                homeActivity.startCall(call.getSession().getMyUserId(), call.getCallId(), unknownDevices);
                            }
                        } else {
                            Log.d(LOG_TAG, "onIncomingCall : a call is already in progress -> cancel");
                            call.hangup("busy");
                        }
                    }
                });
            }
        }

        /**
         * Called when a called has been hung up
         */
        @Override
        public void onCallHangUp(final IMXCall call) {
            Log.d(LOG_TAG, "onCallHangUp");

            final VectorHomeActivity homeActivity = VectorHomeActivity.getInstance();

            if (null != homeActivity) {
                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "onCallHangUp : onCallHangunp");
                        homeActivity.onCallEnd(call);
                    }
                });
            } else {
                Log.d(LOG_TAG, "onCallHangUp : homeactivity does not exist -> don't know what to do");
            }
        }


        @Override
        public void onVoipConferenceStarted(String roomId) {

        }

        @Override
        public void onVoipConferenceFinished(String roomId) {
        }
    };

    // constructor
    protected Matrix(Context appContext) {
        instance = this;

        mAppContext = appContext.getApplicationContext();
        mLoginStorage = new LoginStorage(mAppContext);
        mMXSessions = new ArrayList<>();
        mTmpStores = new ArrayList<>();

        mGCMRegistrationManager = new GcmRegistrationManager(mAppContext);
    }

    /**
     * Retrieve the static instance.
     * Create it if it does not exist yet.
     * @param appContext the application context
     * @return the shared instance
     */
    public synchronized static Matrix getInstance(Context appContext) {
        if ((instance == null) && (null != appContext)) {
            instance = new Matrix(appContext);
        }
        return instance;
    }

    /**
     * @return the loginstorage
     */
    public LoginStorage getLoginStorage() {
        return mLoginStorage;
    }

    /**
     * @return the application name
     */
    public static String getApplicationName() {
        return instance.mAppContext.getApplicationInfo().loadLabel(instance.mAppContext.getPackageManager()).toString();
    }

    /**
     * @return the application version
     */
    public String getVersion(boolean longformat) {
        String versionName = "";
        String flavor = "";

        try {
            PackageInfo pInfo = mAppContext.getPackageManager().getPackageInfo(mAppContext.getPackageName(), 0);
            versionName = pInfo.versionName;

            flavor = mAppContext.getResources().getString(R.string.flavor_description);

            if (!TextUtils.isEmpty(flavor)) {
                flavor += "-";
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## versionName() : failed " + e.getMessage());
        }

        String gitVersion = mAppContext.getResources().getString(R.string.git_revision);
        if (longformat) {
            String date = mAppContext.getResources().getString(R.string.git_revision_date);
            versionName += " (" + flavor + gitVersion + "-" + date + ")";
        } else {
            versionName += " (" + flavor + gitVersion + ")";
        }

        return versionName;
    }

    /**
     * Static method top the MXSession list
     * @param context the application content
     * @return the sessions list
     */
    public static ArrayList<MXSession> getMXSessions(Context context) {
        if ((null != context) && (null != instance)) {
            return instance.getSessions();
        } else {
            return null;
        }
    }

    /**
     * @return The list of sessions
     */
    public ArrayList<MXSession> getSessions() {
        ArrayList<MXSession> sessions = new ArrayList<>();

        synchronized (LOG_TAG) {
            if (null != mMXSessions) {
                sessions = new ArrayList<>(mMXSessions);
            }
        }

        return sessions;
    }

    /**
     * Tell if there is a corrupted store in the active session/
     * @param context the application context
     * @return true if there is a corrupted store.
     */
    public static boolean hasCorruptedStore(Context context) {
        boolean hasCorruptedStore = false;
        ArrayList<MXSession> sessions = Matrix.getMXSessions(context);

        if (null != sessions) {
            for (MXSession session : sessions) {
                if (session.isAlive()) {
                    hasCorruptedStore |= session.getDataHandler().getStore().isCorrupted();
                }
            }
        }
        return hasCorruptedStore;
    }

    /**
     * Retrieve the default session if one exists.
     *
     * The default session may be user-configured, or it may be the last session the user was using.
     * @return The default session or null.
     */
    public synchronized MXSession getDefaultSession() {
        ArrayList<MXSession> sessions = getSessions();

        if (sessions.size() > 0) {
            return sessions.get(0);
        }

        ArrayList<HomeserverConnectionConfig> hsConfigList = mLoginStorage.getCredentialsList();

        // any account ?
        if ((hsConfigList == null) || (hsConfigList.size() == 0)) {
            return null;
        }

        ArrayList<String> matrixIds = new ArrayList<>();
        sessions = new ArrayList<>();

        for(HomeserverConnectionConfig config: hsConfigList) {
            // avoid duplicated accounts.
            if (config.getCredentials() != null && matrixIds.indexOf(config.getCredentials().userId) < 0) {
                MXSession session = createSession(config);
                sessions.add(session);
                matrixIds.add(config.getCredentials().userId);
            }
        }

        synchronized (LOG_TAG) {
            mMXSessions = sessions;
        }

        return sessions.get(0);
    }

    /**
     * Static method to return a MXSession from an account Id.
     * @param matrixId the matrix id
     * @return the MXSession.
     */
    public static MXSession getMXSession(Context context, String matrixId) {
        return Matrix.getInstance(context.getApplicationContext()).getSession(matrixId);
    }

    /**
     *Retrieve a session from an user Id.
     * The application should be able to manage multi session.
     * @param matrixId the matrix id
     * @return the MXsession if it exists.
     */
    public synchronized MXSession getSession(String matrixId) {
        if (null != matrixId) {
            ArrayList<MXSession> sessions;

            synchronized (this) {
                sessions = getSessions();
            }

            for (MXSession session : sessions) {
                Credentials credentials = session.getCredentials();

                if ((null != credentials) && (credentials.userId.equals(matrixId))) {
                    return session;
                }
            }
        }

        return getDefaultSession();
    }

    /**
     * Add an error listener to each sessions
     * @param activity the activity.
     */
    public static void setSessionErrorListener(Activity activity) {
        if ((null != instance) && (null != activity)) {
            Collection<MXSession> sessions = getMXSessions(activity);

            for(MXSession session : sessions) {
                if (session.isAlive()) {
                    session.setFailureCallback(new ErrorListener(session, activity));
                }
            }
        }
    }

    /**
     * Remove the sessions error listener to each
     */
    public static void removeSessionErrorListener(Activity activity) {
        if ((null != instance) && (null != activity)) {
            Collection<MXSession> sessions = getMXSessions(activity);

            for(MXSession session : sessions) {
                if (session.isAlive()) {
                    session.setFailureCallback(null);
                }
            }
        }
    }

    /**
     * Return the used media caches.
     * This class can inherited to customized it.
     * @return the mediasCache.
     */
    public MXMediasCache getMediasCache() {
        if (getSessions().size() > 0) {
            return getSessions().get(0).getMediasCache();
        }
        return null;
    }

    /**
     * Return the used latestMessages caches.
     * This class can inherited to customized it.
     * @return the latest messages cache.
     */
    public MXLatestChatMessageCache getDefaultLatestChatMessageCache() {
        if (getSessions().size() > 0) {
            return getSessions().get(0).getLatestChatMessageCache();
        }
        return null;
    }
    /**
     *
     * @return true if the matrix client instance defines a valid session
     */
    public static boolean hasValidSessions() {
        if (null == instance) {
            Log.e(LOG_TAG, "hasValidSessions : has no instance");
            return false;
        }

        boolean res;

        synchronized (LOG_TAG) {
            res = (null != instance.mMXSessions) && (instance.mMXSessions.size() > 0);

            if (!res) {
                Log.e(LOG_TAG, "hasValidSessions : has no session");
            } else {
                for(MXSession session : instance.mMXSessions) {
                    // some GA issues reported that the data handler can be null
                    // so assume the application should be restarted
                    res &= (null != session.getDataHandler());
                }

                if (!res) {
                    Log.e(LOG_TAG, "hasValidSessions : one sesssion has no valid data hanlder");
                }
            }
        }

        return res;
    }

    //==============================================================================================================
    // Session management
    //==============================================================================================================

    /**
     * Clear a session.
     * @param context the context.
     * @param session the session to clear.
     * @param clearCredentials true to clear the credentials.
     */
    public synchronized void clearSession(Context context, MXSession session, boolean clearCredentials) {
        if (clearCredentials) {
            mLoginStorage.removeCredentials(session.getHomeserverConfig());
        }

        session.getDataHandler().removeListener(mLiveEventListener);
        session.mCallsManager.removeListener(mCallsManagerListener);

        if (clearCredentials) {
            session.logout(context, null);
        } else {
            session.clear(context);
        }

        VectorApp.removeSyncingSession(session);

        synchronized (LOG_TAG) {
            mMXSessions.remove(session);
        }
    }

    /**
     * Clear any existing session.
     * @param context the context.
     * @param clearCredentials  true to clear the credentials.
     */
    public synchronized void clearSessions(Context context, boolean clearCredentials) {
        synchronized (LOG_TAG) {
            while (mMXSessions.size() > 0) {
                clearSession(context, mMXSessions.get(0), clearCredentials);
            }
        }
    }

    /**
     * Set a default session.
     * @param session The session to store as the default session.
     */
    public synchronized void addSession(MXSession session) {
        mLoginStorage.addCredentials(session.getHomeserverConfig());
        synchronized (LOG_TAG) {
            mMXSessions.add(session);
        }
    }

    /**
     * Creates an MXSession from some credentials.
     * @param hsConfig The HomeserverConnectionConfig to create a session from.
     * @return The session.
     */
    public MXSession createSession(HomeserverConnectionConfig hsConfig) {
        return createSession(mAppContext, hsConfig);
    }

    /**
     * Creates an MXSession from some credentials.
     * @param context the context.
     * @param hsConfig The HomeserverConnectionConfig to create a session from.
     * @return The session.
     */
    public MXSession createSession(final Context context, HomeserverConnectionConfig hsConfig) {
        IMXStore store;

        Credentials credentials = hsConfig.getCredentials();

        if (true) {
            store = new MXFileStore(hsConfig, context);
        } else {
            store = new MXMemoryStore(hsConfig.getCredentials(), context);
        }

        MXSession session = new MXSession(hsConfig, new MXDataHandler(store, credentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
                if (null != VectorApp.getCurrentActivity()) {
                    CommonActivityUtils.logout(VectorApp.getCurrentActivity());
                }
            }
        }), mAppContext);

        // if a device id is defined, enable the encryption
        if (!TextUtils.isEmpty(credentials.deviceId)) {
            session.enableCryptoWhenStarting();
        }

        session.getDataHandler().addListener(mLiveEventListener);
        session.mCallsManager.addListener(mCallsManagerListener);
        return session;
    }

    /**
     * Reload the matrix sessions.
     * The session caches are cleared before being reloaded.
     * Any opened activity is closed and the application switches to the splash screen.
     * @param context the context
     */
    public void reloadSessions(final Context context) {
        ArrayList<MXSession> sessions = getMXSessions(context);

        for(MXSession session : sessions) {
            CommonActivityUtils.logout(context, session, false);
        }

        clearSessions(context, false);

        synchronized (LOG_TAG) {
            // build a new sessions list
            ArrayList<HomeserverConnectionConfig> configs = mLoginStorage.getCredentialsList();

            for(HomeserverConnectionConfig config : configs) {
                MXSession session = createSession(config);
                mMXSessions.add(session);
            }
        }

        // clear GCM token before launching the splash screen
        Matrix.getInstance(context).getSharedGCMRegistrationManager().clearGCMData(new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(final Void anything) {
                Intent intent = new Intent(context.getApplicationContext(), SplashActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.getApplicationContext().startActivity(intent);

                if (null != VectorApp.getCurrentActivity()) {
                    VectorApp.getCurrentActivity().finish();
                }
            }});
    }

    /**
     * @return the GCM registration manager
     */
    public GcmRegistrationManager getSharedGCMRegistrationManager() {
        return mGCMRegistrationManager;
    }

    //==============================================================================================================
    // Push rules management
    //==============================================================================================================

    /**
     * Refresh the sessions push rules.
     */
    public void refreshPushRules() {
        ArrayList<MXSession> sessions;

        synchronized (this) {
            sessions = getSessions();
        }

        for(MXSession session : sessions) {
            if (null != session.getDataHandler()) {
                session.getDataHandler().refreshPushRules();
            }
        }
    }

    //==============================================================================================================
    // Network events manager
    //==============================================================================================================

    /**
     * Add a network event listener.
     * @param networkEventListener the event listener to add
     */
    public void addNetworkEventListener(final IMXNetworkEventListener networkEventListener) {
        if ((null != getDefaultSession()) && (null != networkEventListener)){
           getDefaultSession().getNetworkConnectivityReceiver().addEventListener(networkEventListener);
        }
    }

    /**
     * Remove a network event listener.
     * @param networkEventListener the event listener to remove
     */
    public void removeNetworkEventListener(final IMXNetworkEventListener networkEventListener) {
        if ((null != getDefaultSession()) && (null != networkEventListener)){
            getDefaultSession().getNetworkConnectivityReceiver().removeEventListener(networkEventListener);
        }
    }

    /**
     * @return true if the device is connected to a data network
     */
    public boolean isConnected() {
        if (null != getDefaultSession()) {
            return getDefaultSession().getNetworkConnectivityReceiver().isConnected();
        }

        return true;
    }

    //==============================================================================================================
    // Tmp stores list management
    //==============================================================================================================

    /**
     * Add a tmp IMXStore in the currently used stores list
     * @param store the store
     * @return the store index
     */
    public int addTmpStore(IMXStore store) {
        // sanity check
        if (null != store) {
            int pos = mTmpStores.indexOf(store);

            if (pos < 0) {
                mTmpStores.add(store);
                pos = mTmpStores.indexOf(store);
            }

            return pos;
        }

        return -1;
    }

    /**
     * Remove the dedicated store from the tmp stores list.
     * @param store the store to remove
     */
    public void removeTmpStore(IMXStore store) {
        if (null != store) {
            mTmpStores.remove(store);
        }
    }

    /**
     * Return a tmp store.
     * @param storeIndex the store index.
     * @return the store
     */
    public IMXStore getTmpStore(int storeIndex) {
        if ((storeIndex >= 0) && (storeIndex < mTmpStores.size())) {
            return mTmpStores.get(storeIndex);
        }

        return null;
    }

    /**
     * Clear the tmp stores list.
     */
    public void clearTmpStoresList() {
        mTmpStores = new ArrayList<>();
    }
}
