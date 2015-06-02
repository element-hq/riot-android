package org.matrix.console.gcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.console.Matrix;
import org.matrix.console.R;

import java.io.IOException;
import java.util.ArrayList;


/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager {
    private static String LOG_TAG = "GcmRegistrationManager";

    public static final String PREFS_GCM = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager";
    public static final String PREFS_KEY_REG_ID_PREFIX = "REG_ID-";

    public static final String PREFS_PUSHER_APP_ID_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherAppId";
    public static final String PREFS_SENDER_ID_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.senderId";
    public static final String PREFS_PUSHER_URL_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherUrl";
    public static final String PREFS_PUSHER_FILE_TAG_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherFileTag";
    public static final String PREFS_APP_VERSION = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.appVersion";

    // TODO: Make this configurable at build time
    private static String DEFAULT_SENDER_ID = "0";
    private static String DEFAULT_PUSHER_APP_ID = "org.matrix.console.android";
    private static String DEFAULT_PUSHER_URL = "http://matrix.org/_matrix/push/v1/notify";
    private static String DEFAULT_PUSHER_FILE_TAG = "mobile";

    /**
     * GCM registration interface
     */
    public interface GcmRegistrationIdListener {
        void onPusherRegistered();
        void onPusherRegistrationFailed();
    }

    /**
     * 3rd party server registation interface
     */
    public interface GcmSessionRegistration {
        void onSessionRegistred();
        void onSessionRegistrationFailed();

        void onSessionUnregistred();
        void onSessionUnregistrationFailed();
    }

    // theses both entries can be updated from the settings page in debug mode
    private String mPusherAppId = null;
    private String mSenderId = null;
    private String mPusherUrl = null;
    private String mPusherFileTag = null;

    private String mPusherAppName = null;
    private String mPusherLang = null;

    private enum RegistrationState {
        UNREGISTRATED,
        GCM_REGISTRATING,
        GCM_REGISTRED,
        SERVER_REGISTRATING,
        SERVER_REGISTERED
    };

    private static String mBasePusherDeviceName = Build.MODEL.trim();

    private Context mContext;
    private RegistrationState mRegistrationState = RegistrationState.UNREGISTRATED;

    private String mRegistrationId = null;

    public GcmRegistrationManager(Context appContext) {
        mContext = appContext.getApplicationContext();

        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            mPusherAppName = pInfo.packageName;
            mPusherLang = mContext.getResources().getConfiguration().locale.getLanguage();
        } catch (Exception e) {
            mPusherAppName = "Matrix Console";
            mPusherLang = "en";
        }

        loadGcmData();
    }

    /**
     * reset the Registration
     */
    public void reset() {
        // TODO warn server that the sessions must not anymore receive notifications
        unregisterSessions(null);

        // remove the customized keys
        getSharedPreferences().
                edit().
                remove(PREFS_PUSHER_APP_ID_KEY).
                remove(PREFS_SENDER_ID_KEY).
                remove(PREFS_PUSHER_URL_KEY).
                remove(PREFS_PUSHER_FILE_TAG_KEY).
                commit();

        loadGcmData();
    }

    /*
        getters & setters
     */
    public String pusherAppId() {
        return mPusherAppId;
    }

    public void setPusherAppId(String pusherAppId) {
        if (!TextUtils.isEmpty(pusherAppId) && !pusherAppId.equals(mPusherAppId)) {
            mPusherAppId = pusherAppId;
            SaveGCMData();
        }
    }

    public String senderId() {
        return mSenderId;
    }

    public void setSenderId(String senderId) {
        if (!TextUtils.isEmpty(senderId) && !senderId.equals(mSenderId)) {
            mSenderId = senderId;
            SaveGCMData();
        }
    }

    public String pusherUrl() {
        return mPusherUrl;
    }

    public void setPusherUrl(String pusherUrl) {
        if (!TextUtils.isEmpty(pusherUrl) && !pusherUrl.equals(mPusherUrl)) {
            mPusherUrl = pusherUrl;
            SaveGCMData();
        }
    }

    public String pusherFileTag() {
        return mPusherFileTag;
    }

    public void setPusherFileTag(String pusherFileTag) {
        if (!TextUtils.isEmpty(pusherFileTag) && !pusherFileTag.equals(mPusherFileTag)) {
            mPusherFileTag = pusherFileTag;
            SaveGCMData();
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    public boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                Log.e(LOG_TAG, "checkPlayServices isUserRecoverableError " +  GooglePlayServicesUtil.getErrorString(resultCode));
            } else {
                Log.e(LOG_TAG, "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    /**
     * Register to the GCM.
     * @param registrationListener the events listener.
     */
    public void registerPusher(final GcmRegistrationIdListener registrationListener) {
        // already registred
        if (mRegistrationState == RegistrationState.GCM_REGISTRED) {
            if (null != registrationListener) {
                registrationListener.onPusherRegistered();
            }
        } else if (mRegistrationState != RegistrationState.UNREGISTRATED) {
            if (null != registrationListener) {
                registrationListener.onPusherRegistrationFailed();
            }
        } else {

            mRegistrationState = RegistrationState.GCM_REGISTRATING;

            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    String registrationId = null;

                    if (checkPlayServices()) {
                        registrationId = getRegistrationId();

                        if (registrationId != null) {
                            mRegistrationId = registrationId;
                        }
                    }
                    return registrationId;
                }

                @Override
                protected void onPostExecute(String registrationId) {

                    if (registrationId != null) {
                        mRegistrationState = RegistrationState.GCM_REGISTRED;
                    } else {
                        mRegistrationState = RegistrationState.UNREGISTRATED;
                    }

                    setStoredRegistrationId(registrationId);

                    // register the sessions to the 3rd party server
                    if (useGCM()) {
                        registerSessions(null);
                    }

                    // warn the listener
                    if (null != registrationListener) {
                        try {
                            if (registrationId != null) {
                                registrationListener.onPusherRegistered();
                            } else {
                                registrationListener.onPusherRegistrationFailed();
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }.execute();
        }
    }

    /**
     * @return true if use GCM
     */
    public Boolean useGCM() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.settings_key_use_gcm), false);
    }

    public Boolean isGCMRegistred() {
        return (mRegistrationState == RegistrationState.GCM_REGISTRED) || (mRegistrationState == RegistrationState.SERVER_REGISTRATING) || (mRegistrationState == RegistrationState.SERVER_REGISTERED);
    }

    public Boolean is3rdPartyServerRegistred() {
        return mRegistrationState == RegistrationState.SERVER_REGISTERED;
    }


    private String getRegistrationId() {
        String registrationId = getStoredRegistrationId();

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        if (isNewAppVersion()) {
            registrationId = null;
            setStoredRegistrationId(null);
        }

        if (registrationId == null) {
            try {
                // and callback if not.
                GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
                registrationId = gcm.register(mSenderId);
                //setStoredRegistrationId(registrationId);
            } catch (IOException e) {
                registrationId = null;
            }
        }
        return registrationId;
    }


    /**
     * Register the session to the 3rd-party app server
     * @param session the session to register.
     * @param listener the registration listener
     */
    public void registerSession(final MXSession session, final GcmSessionRegistration listener) {
        session.getPushersRestClient()
                .addHttpPusher(mRegistrationId, mPusherAppId, mPusherFileTag + "_" + session.getMyUser().userId,
                        mPusherLang, mPusherAppName, mBasePusherDeviceName,
                        mPusherUrl, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "registerPusher succeeded");

                                if (null != listener) {
                                    try {
                                        listener.onSessionRegistred();
                                    } catch (Exception e) {
                                    }
                                }
                            }

                            private void onError() {
                                if (null != listener) {
                                    try {
                                        listener.onSessionRegistrationFailed();
                                    } catch (Exception e) {
                                    }
                                }
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                Log.e(LOG_TAG, "registerPusher onNetworkError " + e.getMessage());
                                onError();
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                Log.e(LOG_TAG, "registerPusher onMatrixError " + e.errcode);
                                onError();
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                Log.e(LOG_TAG, "registerPusher onUnexpectedError " + e.getMessage());
                                onError();
                            }
                        });
    }

    /**
     * Register the current sessions to the 3rd party GCM server
     * @param listener the registration listener.
     */
    public void registerSessions(final GcmSessionRegistration listener) {
        if (mRegistrationState != RegistrationState.GCM_REGISTRED) {
            if (null != listener) {
                try {
                    listener.onSessionRegistrationFailed();
                } catch (Exception e) {
                }
            }
        } else {
            mRegistrationState = RegistrationState.SERVER_REGISTRATING;
            registerSessions(new ArrayList<MXSession>(Matrix.getInstance(mContext).getSessions()), 0, listener);
        }
    }

    /**
     * Recursive method to register a MXSessions list.
     * @param sessions the sessions list.
     * @param index the index of the MX sessions to register.
     * @param listener the registration listener.
     */
    private void registerSessions(final ArrayList<MXSession> sessions, final int index, final GcmSessionRegistration listener) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            mRegistrationState = RegistrationState.SERVER_REGISTERED;

            if (null != listener) {
                try {
                    listener.onSessionRegistred();
                } catch (Exception e) {
                }
            }
            return;
        }

        MXSession session = sessions.get(index);

        registerSession(session , new GcmSessionRegistration() {
            @Override
            public void onSessionRegistred() {
                registerSessions(sessions, index+1, listener);
            }

            @Override
            public void onSessionRegistrationFailed() {
                if (null != listener) {
                    try {
                        mRegistrationState = RegistrationState.GCM_REGISTRED;
                        listener.onSessionRegistrationFailed();
                    } catch (Exception e) {
                    }
                }
            }

            @Override
            public void onSessionUnregistred() {
            }

            @Override
            public void onSessionUnregistrationFailed() {
            }
        });
    }

    /**
     * Unregister the user identified from his matrix Id from the 3rd-party app server
     * @param session
     */
    public void unregisterSession(final MXSession session, final GcmSessionRegistration listener) {
        // TODO warn server that the sessions must not anymore receive notifications

        if (null != listener) {
            try {
                listener.onSessionUnregistrationFailed();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Unregister the current sessions from the 3rd party GCM server
     * @param listener the registration listener.
     */
    public void unregisterSessions(final GcmSessionRegistration listener) {
        if (mRegistrationState != RegistrationState.SERVER_REGISTERED) {
            if (null != listener) {
                try {
                    listener.onSessionUnregistrationFailed();
                } catch (Exception e) {
                }
            }
        } else {
            mRegistrationState = RegistrationState.GCM_REGISTRED;

            try {
                listener.onSessionUnregistred();
            } catch (Exception e) {
            }

            // TODO wait after a server API update
            //unregisterSessions(new ArrayList<MXSession>(Matrix.getInstance(mContext).getSessions()), 0, listener);
        }
    }

    /**
     * Recursive method to unregister a MXSessions list.
     * @param sessions the sessions list.
     * @param index the index of the MX sessions to register.
     * @param listener the registration listener.
     */
    private void unregisterSessions(final ArrayList<MXSession> sessions, final int index, final GcmSessionRegistration listener) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            if (null != listener) {
                try {
                    listener.onSessionUnregistred();
                } catch (Exception e) {
                }
            }
            return;
        }

        MXSession session = sessions.get(index);

        unregisterSession(session , new GcmSessionRegistration() {
            @Override
            public void onSessionRegistred() {
            }

            @Override
            public void onSessionRegistrationFailed() {
            }

            @Override
            public void onSessionUnregistred() {
                unregisterSessions(sessions, index+1, listener);
            }

            @Override
            public void onSessionUnregistrationFailed() {
                if (null != listener) {
                    try {
                        listener.onSessionUnregistrationFailed();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    /**
     * @return the GCM registration stored for this version of the app or null if none is stored.
     */
    private String getStoredRegistrationId() {
        return getSharedPreferences().getString(getRegistrationIdKey(), null);
    }

    /**
     * @return true if the current application version is not the same the expected one.
     */
    private Boolean isNewAppVersion() {
        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            int currentVersion = pInfo.versionCode;

            int registeredVersion = getSharedPreferences().getInt(PREFS_APP_VERSION, Integer.MIN_VALUE);

            if (registeredVersion != currentVersion) {
                Log.d(LOG_TAG, "App version changed.");
                getSharedPreferences().edit().putInt(PREFS_APP_VERSION, currentVersion).commit();
                return true;
            }

            return false;
        } catch (Exception e) {

        }

        return true;
    }

    /**
     * Set the GCM registration for the currently-running version of this app.
     * @param registrationId
     */
    private void setStoredRegistrationId(String registrationId) {
        String key = getRegistrationIdKey();
        if (key == null) {
            Log.e(LOG_TAG, "Failed to store registration ID");
            return;
        }

        Log.d(LOG_TAG, "Saving registrationId " + registrationId + " under key " + key);
        getSharedPreferences().edit()
                .putString(key, registrationId)
                .commit();
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(PREFS_GCM, Context.MODE_PRIVATE);
    }

    private String getRegistrationIdKey() {
        try {
            PackageInfo packageInfo = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            return PREFS_KEY_REG_ID_PREFIX + Integer.toString(packageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Save the GCM info to the preferences
     */
    private void SaveGCMData() {
        try {
            SharedPreferences preferences = getSharedPreferences();
            SharedPreferences.Editor editor = preferences.edit();

            editor.putString(PREFS_PUSHER_APP_ID_KEY, mPusherAppId);
            editor.putString(PREFS_SENDER_ID_KEY, mSenderId);
            editor.putString(PREFS_PUSHER_URL_KEY, mPusherUrl);
            editor.putString(PREFS_PUSHER_FILE_TAG_KEY, mPusherFileTag);

            editor.commit();
        } catch (Exception e) {

        }
    }

    /**
     * Load the GCM info from the preferences
     */
    private void loadGcmData() {
        try {
            SharedPreferences preferences = getSharedPreferences();

            String pusherAppId = preferences.getString(PREFS_PUSHER_APP_ID_KEY, null);
            mPusherAppId = TextUtils.isEmpty(pusherAppId) ? DEFAULT_PUSHER_APP_ID : pusherAppId;

            String senderId = preferences.getString(PREFS_SENDER_ID_KEY, null);
            mSenderId = TextUtils.isEmpty(senderId) ? DEFAULT_SENDER_ID : senderId;

            String pusherUrl = preferences.getString(PREFS_PUSHER_URL_KEY, null);
            mPusherUrl = TextUtils.isEmpty(pusherUrl) ? DEFAULT_PUSHER_URL : pusherUrl;

            String pusherFileTag = preferences.getString(PREFS_PUSHER_FILE_TAG_KEY, null);
            mPusherFileTag = TextUtils.isEmpty(pusherFileTag) ? DEFAULT_PUSHER_FILE_TAG : pusherFileTag;

        } catch (Exception e) {

        }
    }
}
