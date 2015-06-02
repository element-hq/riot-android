package org.matrix.matrixandroidsdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MXFileStore;
import org.matrix.androidsdk.data.MXMemoryStore;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;
import org.matrix.matrixandroidsdk.activity.SplashActivity;
import org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager;
import org.matrix.matrixandroidsdk.store.LoginStorage;
import org.matrix.matrixandroidsdk.util.RageShake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Singleton to control access to the Matrix SDK and providing point of control for MXSessions.
 */
public class Matrix {

    private static Matrix instance = null;

    private LoginStorage mLoginStorage;
    private ArrayList<MXSession> mMXSessions;
    private GcmRegistrationManager mGcmRegistrationManager;
    private Context mAppContext;

    protected Matrix(Context appContext) {
        mAppContext = appContext.getApplicationContext();
        mLoginStorage = new LoginStorage(mAppContext);
        mMXSessions = new ArrayList<MXSession>();
        mGcmRegistrationManager = new GcmRegistrationManager(mAppContext);
        RageShake.getInstance().start(mAppContext);
    }

    public synchronized static Matrix getInstance(Context appContext) {
        if ((instance == null) && (null != appContext)) {
            instance = new Matrix(appContext);
        }
        return instance;
    }

    /**
     * Static method top the MXSession list
     * @param context the application content
     * @return the sessions list
     */
    public static Collection<MXSession> getMXSessions(Context context) {
        return Matrix.getInstance(context.getApplicationContext()).getSessions();
    }

    /**
     * @return The list of sessions
     */
    public Collection<MXSession> getSessions() {
        return mMXSessions;
    }

    /**
     * Retrieve the default session if one exists.
     *
     * The default session may be user-configured, or it may be the last session the user was using.
     * @return The default session or null.
     */
    public synchronized MXSession getDefaultSession() {
        if (mMXSessions.size() > 0) {
            return mMXSessions.get(0);
        }

        ArrayList<Credentials> credsList = mLoginStorage.getCredentialsList();

        // any account ?
        if ((credsList == null) || (credsList.size() == 0)) {
            return null;
        }

        ArrayList<String> matrixIds = new ArrayList<String>();

        for(Credentials creds : credsList) {
            // avoid duplicated accounts.
            if (matrixIds.indexOf(creds.userId) < 0) {
                MXSession session = createSession(creds);
                mMXSessions.add(session);

                matrixIds.add(creds.userId);
            }
        }

        return mMXSessions.get(0);
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
            for (MXSession session : mMXSessions) {
                Credentials credentials = session.getCredentials();

                if ((null != credentials) && (credentials.userId.equals(matrixId))) {
                    return session;
                }
            }
        }

        return getDefaultSession();
    }

    /**
     * Return the used media caches.
     * This class can inherited to customized it.
     * @return the mediasCache.
     */
    public MXMediasCache getMediasCache() {
        if (mMXSessions.size() > 0) {
            return mMXSessions.get(0).getMediasCache();
        }
        return null;
    }

    /**
     * Return the used latestMessages caches.
     * This class can inherited to customized it.
     * @return the latest messages cache.
     */
    public MXLatestChatMessageCache getDefaultLatestChatMessageCache() {
        if (mMXSessions.size() > 0) {
            return mMXSessions.get(0).getLatestChatMessageCache();
        }
        return null;
    }
    /**
     *
     * @return true if the matrix client instance defines a valid session
     */
    public static Boolean hasValidValidSession() {
        return (null != instance) && (instance.mMXSessions.size() > 0);
    }

    /**
     * Refresh the sessions push rules.
     */
    public void refreshPushRules() {
        for(MXSession session : mMXSessions) {
            if (null != session.getDataHandler()) {
                session.getDataHandler().refreshPushRules();
            }
        }
    }

    /**
     * Clear a session.
     * @param context the context.
     * @param session the session to clear.
     * @param clearCredentials true to clear the credentials.
     */
    public synchronized void clearSession(Context context, MXSession session, Boolean clearCredentials) {
        if (clearCredentials) {
            mLoginStorage.removeCredentials(session.getCredentials());
        }

        session.clear(context);
        mMXSessions.remove(session);
    }

    /**
     * Clear any existing session.
     * @param context the context.
     * @param clearCredentials  true to clear the credentials.
     */
    public synchronized void clearSessions(Context context, Boolean clearCredentials) {
        while (mMXSessions.size() > 0) {
            clearSession(context, mMXSessions.get(0), clearCredentials);
        }
    }

    /**
     * Set a default session.
     * @param session The session to store as the default session.
     */
    public synchronized void addSession(MXSession session) {
        mLoginStorage.addCredentials(session.getCredentials());
        mMXSessions.add(session);
    }

    /**
     * Creates an MXSession from some credentials.
     * @param credentials The credentials to create a session from.
     * @return The session.
     */
    public MXSession createSession(Credentials credentials) {
        return createSession(mAppContext, credentials, true);
    }

    /**
     * Creates an MXSession from some credentials.
     * @param context the context.
     * @param credentials The credentials to create a session from.
     * @param useHttps True to enforce https URIs on the home server.
     * @return The session.
     */
    public MXSession createSession(Context context, Credentials credentials, boolean useHttps) {
        if (!credentials.homeServer.startsWith("http")) {
            if (useHttps) {
                credentials.homeServer = "https://" + credentials.homeServer;
            }
            else {
                credentials.homeServer = "http://" + credentials.homeServer;
            }
        }

        IMXStore store;

        if (true ) {
            store = new MXFileStore(credentials, context);
        } else {
            store = new MXMemoryStore(credentials);
        }

        return new MXSession(new MXDataHandler(store, credentials), credentials, mAppContext);
    }

    /**
     * Reload the matrix sessions.
     * The session caches are cleared before being reloaded.
     * Any opened activity is closed and the application switches to the splash screen.
     * @param fromActivity the caller activity
     */
    public void reloadSessions(Activity fromActivity) {
        for(MXSession session : mMXSessions) {
            CommonActivityUtils.logout(fromActivity, session, false);
        }

        clearSessions(fromActivity, false);

        // build a new sessions list
        ArrayList<Credentials> credsList = mLoginStorage.getCredentialsList();

        for(Credentials creds : credsList) {
            MXSession session = createSession(creds);
            mMXSessions.add(session);
        }

        Intent intent = new Intent(fromActivity, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        fromActivity.startActivity(intent);
        fromActivity.finish();
    }

    public GcmRegistrationManager getSharedGcmRegistrationManager() {
        return mGcmRegistrationManager;
    }

}
