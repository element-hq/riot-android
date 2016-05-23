/**
 * Copyright 2015 Google Inc. All Rights Reserved.
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
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PushersResponse;

import im.vector.Matrix;
import im.vector.R;
import im.vector.activity.CommonActivityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager {
    private static String LOG_TAG = "GcmRegistrationManager";

    public static final String PREFS_GCM = "GcmRegistrationManager";
    public static final String PREFS_KEY_REG_ID_PREFIX = "REG_ID-";

    public static final String PREFS_PUSHER_APP_ID_KEY = "GcmRegistrationManager.pusherAppId";
    public static final String PREFS_SENDER_ID_KEY = "GcmRegistrationManager.senderId";
    public static final String PREFS_PUSHER_URL_KEY = "GcmRegistrationManager.pusherUrl";
    public static final String PREFS_PUSHER_FILE_TAG_KEY = "GcmRegistrationManager.pusherFileTag";

    private static String DEFAULT_PUSHER_APP_ID = "im.vector.app.android";
    private static String DEFAULT_PUSHER_URL = "https://matrix.org/_matrix/push/v1/notify";
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
    private String mPusherUrl = null;
    private String mPusherBaseFileTag = null;

    private String mPusherAppName = null;
    private String mPusherLang = null;
    private ArrayList<GcmSessionRegistration> mSessionsregistrationListener = new ArrayList<GcmSessionRegistration>();

    // the pushers list
    public ArrayList<Pusher> mPushersList = new ArrayList<>();

    private enum RegistrationState {
        UNREGISTRATED,
        GCM_REGISTRATING,
        GCM_REGISTRED,
        SERVER_REGISTRATING,
        SERVER_REGISTERED,
        SERVER_UNREGISTRATING,
    }

    private static String mBasePusherDeviceName = Build.MODEL.trim();

    private Context mContext;
    private RegistrationState mRegistrationState = RegistrationState.UNREGISTRATED;

    private String mPushKey = null;

    /**
     * Constructor
     * @param appContext the application context.
     */
    public GcmRegistrationManager(Context appContext) {
        mContext = appContext.getApplicationContext();

        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            mPusherAppName = pInfo.packageName;
            mPusherLang = mContext.getResources().getConfiguration().locale.getLanguage();
        } catch (Exception e) {
            mPusherAppName = "VectorApp";
            mPusherLang = "en";
        }

        loadGcmData();
    }

    /**
     * reset the Registration
     */
    public void reset() {

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

    /**
     * Refresh the pushers list
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
     * Force to retrieve the
     * @param appContext
     * @param registrationListener
     */
    public void refreshPushToken(final Context appContext, final GcmRegistrationIdListener registrationListener) {
        setStoredPushKey(null);
        mRegistrationState = RegistrationState.UNREGISTRATED;
        registerPusher(appContext, registrationListener);
    }


    /**
     * Check if the GCM registration has been broken with a new token ID.
     * The GCM could have resetted it (onTokenRefresh).
     * @param appContext the application context
     */
    public void checkPusherRegistration(final Context appContext) {
        if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            Log.d(LOG_TAG, "checkPusherRegistration : try to register to GCM server");

            registerPusher(appContext, new GcmRegistrationIdListener() {
                @Override
                public void onPusherRegistered() {
                    Log.d(LOG_TAG, "checkPusherRegistration : reregistered");
                    // vector always uses GCM.
                    // there is no way to enable / disable it in the application settings
                    if (!useGCM()) {
                        setUseGCM(true);
                        CommonActivityUtils.onGcmUpdate(mContext);
                    }
                }

                @Override
                public void onPusherRegistrationFailed() {
                    Log.d(LOG_TAG, "checkPusherRegistration : onPusherRegistrationFailed");
                }
            });
        } else if (mRegistrationState == RegistrationState.GCM_REGISTRED) {
            // register the 3rd party server
            // the server registration might have failed
            // so ensure that it will be done when the application is debackgrounded.
            if (useGCM()) {
                registerSessions(appContext, null);
            }
        } else if (mRegistrationState == RegistrationState.SERVER_REGISTERED) {
            refreshPushersList(new ArrayList<MXSession>(Matrix.getInstance(mContext).getSessions()), null);
        }
    }

    /**
     * Register to the GCM.
     * @param registrationListener the events listener.
     */
    public void registerPusher(final Context appContext, final GcmRegistrationIdListener registrationListener) {
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
                    String pushKey = getPushKey(appContext);

                    if (pushKey != null) {
                        mPushKey = pushKey;
                    }

                    return mPushKey;
                }

                @Override
                protected void onPostExecute(String pushKey) {

                    mRegistrationState = (pushKey != null) ? RegistrationState.GCM_REGISTRED : RegistrationState.UNREGISTRATED;
                    setStoredPushKey(pushKey);

                    // warn the listener
                    if (null != registrationListener) {
                        try {
                            if (pushKey != null) {
                                registrationListener.onPusherRegistered();
                            } else {
                                registrationListener.onPusherRegistrationFailed();
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "registerPusher : onPusherRegistered/onPusherRegistrationFailed failed " + e.getLocalizedMessage());
                        }
                    }

                    if (mRegistrationState == RegistrationState.GCM_REGISTRED) {
                        // register the sessions to the 3rd party server
                        // this setting should be updated from the listener
                        if (useGCM()) {
                            registerSessions(appContext, null);
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
        return preferences.getBoolean(mContext.getString(R.string.settings_key_use_google_cloud_messaging), true);
    }

    /**
     * Update the GCM status
     * @param use
     */
    public void setUseGCM(Boolean use) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(mContext).edit();
        editor.putBoolean(mContext.getString(R.string.settings_key_use_google_cloud_messaging), use);
        editor.apply();
    }

    public boolean isGCMRegistred() {
        return (mRegistrationState == RegistrationState.GCM_REGISTRED) || (mRegistrationState == RegistrationState.SERVER_REGISTRATING) || (mRegistrationState == RegistrationState.SERVER_REGISTERED);
    }

    public boolean is3rdPartyServerRegistred() {
        return mRegistrationState == RegistrationState.SERVER_REGISTERED;
    }

    public boolean isRegistrating() {
        return (mRegistrationState == RegistrationState.SERVER_REGISTRATING) || (mRegistrationState == RegistrationState.SERVER_UNREGISTRATING);
    }

    private String getPushKey(Context appContext) {
        String pushKey = getStoredPushKey();

        if (pushKey == null) {
            try {
                Log.d(LOG_TAG, "Getting the GCM Registration Token");

                InstanceID instanceID = InstanceID.getInstance(appContext);

                pushKey = instanceID.getToken(appContext.getString(R.string.gcm_defaultSenderId),
                        GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);


                Log.d(LOG_TAG, "GCM Registration Token: " + pushKey);

                //setStoredRegistrationId(registrationId);
            } catch (IOException e) {
                Log.e(LOG_TAG, "getPushKey failed with exception : " + e.getLocalizedMessage());
                pushKey = null;
            } catch (Exception e) {
                Log.e(LOG_TAG, "getPushKey failed with exception : " + e.getLocalizedMessage());
                pushKey = null;
            }
        }
        return pushKey;
    }

    /**
     * Compute the profileTag for a session
     * @param session the session
     * @return the profile tag
     */
    private String computePushTag(final MXSession session) {
        String tag =  mPusherBaseFileTag + "_" + Math.abs(session.getMyUserId().hashCode());

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
    public void registerSession(final MXSession session, boolean append, final GcmSessionRegistration listener) {
        session.getPushersRestClient()
                .addHttpPusher(mPushKey, mPusherAppId, computePushTag(session),
                        mPusherLang, mPusherAppName, mBasePusherDeviceName,
                        mPusherUrl, append, new ApiCallback<Void>() {
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

                            private void onError(final String message) {
                                Log.e(LOG_TAG, "fail to register " + session.getMyUserId() + " (" + message + ")");

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
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                Log.e(LOG_TAG, "registerPusher onMatrixError " + e.errcode);
                                onError(e.getLocalizedMessage());
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                Log.e(LOG_TAG, "registerPusher onUnexpectedError " + e.getMessage());
                                onError(e.getLocalizedMessage());
                            }
                        });
    }


    public void addSessionsRegistrationListener(final GcmSessionRegistration listener) {
        synchronized (this) {
            if ((null != listener) && (mSessionsregistrationListener.indexOf(listener) == -1)) {
                mSessionsregistrationListener.add(listener);
            }
        }
    }

    private void onSessionsRegistred() {
        synchronized (this) {
            for(GcmSessionRegistration listener : mSessionsregistrationListener) {
                try {
                    listener.onSessionRegistred();
                } catch (Exception e) {

                }
            }

            mSessionsregistrationListener.clear();
        }
    }

    private void onSessionsRegistrationFailed() {
        synchronized (this) {
            for(GcmSessionRegistration listener : mSessionsregistrationListener) {
                try {
                    listener.onSessionRegistrationFailed();
                } catch (Exception e) {

                }
            }

            mSessionsregistrationListener.clear();
        }
    }

    private void onSessionsUnregistred() {
        synchronized (this) {
            for(GcmSessionRegistration listener : mSessionsregistrationListener) {
                try {
                    listener.onSessionUnregistred();
                } catch (Exception e) {

                }
            }

            mSessionsregistrationListener.clear();
        }
    }

    private void onSessionsUnregistrationFailed() {
        synchronized (this) {
            for(GcmSessionRegistration listener : mSessionsregistrationListener) {
                try {
                    listener.onSessionUnregistrationFailed();
                } catch (Exception e) {

                }
            }

            mSessionsregistrationListener.clear();
        }
    }

    public void reregisterSessions(final Context appContext, final GcmSessionRegistration listener) {
        if ((mRegistrationState == RegistrationState.SERVER_REGISTERED) || (mRegistrationState == RegistrationState.GCM_REGISTRED)){
            mRegistrationState = RegistrationState.GCM_REGISTRED;

            registerSessions(appContext, listener);
        } else {
            if (null != listener) {
                try {
                    listener.onSessionRegistrationFailed();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Register the current sessions to the 3rd party GCM server
     * @param listener the registration listener.
     */
    public void registerSessions(final Context appContext, final GcmSessionRegistration listener) {
        if (mRegistrationState == RegistrationState.SERVER_REGISTRATING) {
            addSessionsRegistrationListener(listener);
        } else if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            Log.d(LOG_TAG, "registerSessions unregistrated : try to register again");

            // if the registration failed
            // try to register again
            registerPusher(appContext, new GcmRegistrationIdListener() {
                @Override
                public void onPusherRegistered() {
                    Log.d(LOG_TAG, "GCM registration failed again : register on server side");
                    registerSessions(appContext, listener);
                }

                @Override
                public void onPusherRegistrationFailed() {
                    Log.d(LOG_TAG, "registerSessions unregistrated : GCM registration failed again");

                    if (null != listener) {
                        try {
                            listener.onSessionRegistrationFailed();
                        } catch (Exception e) {
                        }
                    }
                }
            });
        } else if (mRegistrationState == RegistrationState.SERVER_REGISTERED) {
            Log.e(LOG_TAG, "registerSessions : already registred");

            if (null != listener) {
                try {
                    listener.onSessionRegistred();
                } catch (Exception e) {
                }
            }
        } else if (mRegistrationState != RegistrationState.GCM_REGISTRED) {
            Log.e(LOG_TAG, "registerSessions : invalid state " + mRegistrationState);

            if (null != listener) {
                try {
                    listener.onSessionRegistrationFailed();
                } catch (Exception e) {
                }
            }
        } else {
            mRegistrationState = RegistrationState.SERVER_REGISTRATING;
            addSessionsRegistrationListener(listener);
            registerSessions(new ArrayList<MXSession>(Matrix.getInstance(mContext).getSessions()), 0);
        }
    }

    /**
     * Recursive method to register a MXSessions list.
     * @param sessions the sessions list.
     * @param index the index of the MX sessions to register.
     */
    private void registerSessions(final ArrayList<MXSession> sessions, final int index) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            Log.d(LOG_TAG, "registerSessions : all the sessions are registred");
            mRegistrationState = RegistrationState.SERVER_REGISTERED;
            onSessionsRegistred();

            // get the pushers list
            refreshPushersList(sessions, null);
            return;
        }

        final MXSession session = sessions.get(index);

        registerSession(session, (index > 0), new GcmSessionRegistration() {
            @Override
            public void onSessionRegistred() {
                Log.d(LOG_TAG, "registerSessions : session " + session.getMyUserId() + " is registred");
                registerSessions(sessions, index + 1);
            }

            @Override
            public void onSessionRegistrationFailed() {
                Log.d(LOG_TAG, "registerSessions : onSessionRegistrationFailed " + session.getMyUserId());

                mRegistrationState = RegistrationState.GCM_REGISTRED;
                onSessionsRegistrationFailed();
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
        session.getPushersRestClient()
                .removeHttpPusher(mPushKey, mPusherAppId, computePushTag(session),
                        mPusherLang, mPusherAppName, mBasePusherDeviceName,
                        mPusherUrl, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "unregisterSession succeeded");

                                if (null != listener) {
                                    try {
                                        listener.onSessionUnregistred();
                                    } catch (Exception e) {
                                    }
                                }
                            }

                            private void onError(final String message) {
                                if (session.isAlive()) {
                                    Log.e(LOG_TAG, "fail to unregister " + session.getMyUserId() + " (" + message + ")");

                                    if (null != listener) {
                                        try {
                                            listener.onSessionUnregistrationFailed();
                                        } catch (Exception e) {
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

    /**
     * Unregister the current sessions from the 3rd party GCM server
     * @param listener the registration listener.
     */
    public void unregisterSessions(final GcmSessionRegistration listener) {
        if (mRegistrationState == RegistrationState.SERVER_UNREGISTRATING) {
            addSessionsRegistrationListener(listener);
        } else if (mRegistrationState != RegistrationState.SERVER_REGISTERED) {
            Log.e(LOG_TAG, "unregisterSessions : invalid state " + mRegistrationState);

            if (null != listener) {
                try {
                    listener.onSessionUnregistrationFailed();
                } catch (Exception e) {
                }
            }
        } else {
            mRegistrationState = RegistrationState.SERVER_UNREGISTRATING;
            addSessionsRegistrationListener(listener);
            unregisterSessions(new ArrayList<MXSession>(Matrix.getInstance(mContext).getSessions()), 0);
        }
    }

    /**
     * Recursive method to unregister a MXSessions list.
     * @param sessions the sessions list.
     * @param index the index of the MX sessions to register.
     */
    private void unregisterSessions(final ArrayList<MXSession> sessions, final int index) {
        // reach this end of the list ?
        if (index >= sessions.size()) {
            mRegistrationState = RegistrationState.GCM_REGISTRED;
            onSessionsUnregistred();
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
                unregisterSessions(sessions, index+1);
            }

            @Override
            public void onSessionUnregistrationFailed() {
                mRegistrationState = RegistrationState.SERVER_REGISTERED;
                onSessionsUnregistrationFailed();
            }
        });
    }

    /**
     * @return the GCM registration stored for this version of the app or null if none is stored.
     */
    private String getStoredPushKey() {
        return getSharedPreferences().getString(getPushKeyKey(), null);
    }

    /**
     * Set the GCM registration for the currently-running version of this app.
     * @param pushKey
     */
    private void setStoredPushKey(String pushKey) {
        String key = getPushKeyKey();
        if (key == null) {
            Log.e(LOG_TAG, "Failed to store registration ID");
            return;
        }

        Log.d(LOG_TAG, "Saving push key " + pushKey + " under key " + key);
        getSharedPreferences().edit()
                .putString(key, pushKey)
                .apply();
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(PREFS_GCM, Context.MODE_PRIVATE);
    }

    private String getPushKeyKey() {
        try {
            PackageInfo packageInfo = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            return PREFS_KEY_REG_ID_PREFIX + Integer.toString(packageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(LOG_TAG, "getPushKeyKey failed " + e.getLocalizedMessage());
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
            editor.putString(PREFS_PUSHER_URL_KEY, mPusherUrl);
            editor.putString(PREFS_PUSHER_FILE_TAG_KEY, mPusherBaseFileTag);

            editor.commit();
        } catch (Exception e) {
            Log.e(LOG_TAG, "SaveGCMData failed " + e.getLocalizedMessage());
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

            String pusherUrl = preferences.getString(PREFS_PUSHER_URL_KEY, null);
            mPusherUrl = TextUtils.isEmpty(pusherUrl) ? DEFAULT_PUSHER_URL : pusherUrl;

            String pusherFileTag = preferences.getString(PREFS_PUSHER_FILE_TAG_KEY, null);
            mPusherBaseFileTag = TextUtils.isEmpty(pusherFileTag) ? DEFAULT_PUSHER_FILE_TAG : pusherFileTag;

        } catch (Exception e) {
            Log.e(LOG_TAG, "loadGcmData failed " + e.getLocalizedMessage());
        }
    }
}
