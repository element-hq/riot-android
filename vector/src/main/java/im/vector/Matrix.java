package im.vector;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MXFileStore;
import org.matrix.androidsdk.data.MXMemoryStore;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.model.login.Credentials;
import im.vector.activity.CommonActivityUtils;
import im.vector.activity.SplashActivity;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.store.LoginStorage;
import im.vector.util.RageShake;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Singleton to control access to the Matrix SDK and providing point of control for MXSessions.
 */
public class Matrix {

    private static final String LOG_TAG = "Matrix";

    private static Matrix instance = null;

    private LoginStorage mLoginStorage;
    private ArrayList<MXSession> mMXSessions;
    private GcmRegistrationManager mGcmRegistrationManager;
    private Context mAppContext;

    public boolean mHasBeenDisconnected = false;

    // network event manager
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;

    protected Matrix(Context appContext) {
        mAppContext = appContext.getApplicationContext();
        mLoginStorage = new LoginStorage(mAppContext);
        mMXSessions = new ArrayList<MXSession>();
        mGcmRegistrationManager = new GcmRegistrationManager(mAppContext);
        RageShake.getInstance().start(mAppContext);

        mNetworkConnectivityReceiver = new NetworkConnectivityReceiver();
        appContext.registerReceiver(mNetworkConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    public synchronized static Matrix getInstance(Context appContext) {
        if ((instance == null) && (null != appContext)) {
            instance = new Matrix(appContext);
        }
        return instance;
    }

    public LoginStorage getLoginStorage() {
        return mLoginStorage;
    }

    /**
     * @return the application version
     */
    public String getVersion(boolean longformat) {
        String versionName = "";
        try {
            PackageInfo pInfo = mAppContext.getPackageManager().getPackageInfo(mAppContext.getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (Exception e) {
        }

        String gitVersion = mAppContext.getResources().getString(R.string.git_revision);
        if (longformat) {
            String date = mAppContext.getResources().getString(R.string.git_revision_date);
            versionName += " (" + gitVersion + "-" + date + ")";
        } else {
            versionName += " (" + gitVersion + ")";
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
        ArrayList<MXSession> sessions = new ArrayList<MXSession>();

        synchronized (LOG_TAG) {
            if (null != mMXSessions) {
                sessions = new ArrayList<MXSession>(mMXSessions);
            }
        }

        return sessions;
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

        ArrayList<String> matrixIds = new ArrayList<String>();
        sessions = new ArrayList<MXSession>();

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
    public synchronized void clearSession(Context context, MXSession session, Boolean clearCredentials) {
        if (clearCredentials) {
            mLoginStorage.removeCredentials(session.getHomeserverConfig());
        }

        session.clear(context);

        synchronized (LOG_TAG) {
            mMXSessions.remove(session);
        }
    }

    /**
     * Clear any existing session.
     * @param context the context.
     * @param clearCredentials  true to clear the credentials.
     */
    public synchronized void clearSessions(Context context, Boolean clearCredentials) {
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
            store = new MXMemoryStore(hsConfig.getCredentials());
        }

        return new MXSession(hsConfig, new MXDataHandler(store, credentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
                if (null != VectorApp.getCurrentActivity()) {
                    CommonActivityUtils.logout(VectorApp.getCurrentActivity());
                }
            }
        }), mAppContext);
    }

    /**
     * Reload the matrix sessions.
     * The session caches are cleared before being reloaded.
     * Any opened activity is closed and the application switches to the splash screen.
     * @param fromActivity the caller activity
     */
    public void reloadSessions(Activity fromActivity) {
        ArrayList<MXSession> sessions = getMXSessions(fromActivity);

        for(MXSession session : sessions) {
            CommonActivityUtils.logout(fromActivity, session, false);
        }

        clearSessions(fromActivity, false);

        synchronized (LOG_TAG) {
            // build a new sessions list
            ArrayList<HomeserverConnectionConfig> configs = mLoginStorage.getCredentialsList();

            for(HomeserverConnectionConfig config : configs) {
                MXSession session = createSession(config);
                mMXSessions.add(session);
            }
        }

        Intent intent = new Intent(fromActivity, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        fromActivity.startActivity(intent);
        fromActivity.finish();
    }

    public GcmRegistrationManager getSharedGcmRegistrationManager() {
        return mGcmRegistrationManager;
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
        if ((null != mNetworkConnectivityReceiver) && (null != networkEventListener)) {
            mNetworkConnectivityReceiver.addEventListener(networkEventListener);
        }
    }

    /**
     * Remove a network event listener.
     * @param networkEventListener the event listener to remove
     */
    public void removeNetworkEventListener(final IMXNetworkEventListener networkEventListener) {
        if ((null != mNetworkConnectivityReceiver) && (null != networkEventListener)) {
            mNetworkConnectivityReceiver.removeEventListener(networkEventListener);
        }
    }

    /**
     * @return true if the device is connected to a data network
     */
    public boolean isConnected() {
        return mNetworkConnectivityReceiver.isConnected();
    }
}
