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
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.rest.callback.ApiCallback;

import im.vector.Matrix;
import im.vector.activity.CommonActivityUtils;
import im.vector.push.PushManager;

import java.util.ArrayList;


/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager extends PushManager {
    private static final String LOG_TAG = "GcmRegistrationManager";

    private static final String PREFS_GCM = "GcmRegistrationManager";

    private static final String PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM = "PREFS_PUSHER_REGISTRATION_TOKEN_KEY_FCM";
    private static final String PREFS_PUSHER_REGISTRATION_TOKEN_KEY = "PREFS_PUSHER_REGISTRATION_TOKEN_KEY";

    private static final String DEFAULT_PUSHER_APP_ID = "im.vector.app.android";
    private static final String DEFAULT_PUSHER_URL = "https://matrix.org/_matrix/push/v1/notify";
    private static final String DEFAULT_PUSHER_FILE_TAG = "mobile";

    private String mPusherAppName = null;
    private String mPusherLang = null;

    // the session registration listener
    private final ArrayList<ThirdPartyRegistrationListener> mThirdPartyRegistrationListeners = new ArrayList<>();

    // the pushers list
    public ArrayList<Pusher> mPushersList = new ArrayList<>();

    // the pusher base
    private static final String mBasePusherDeviceName = Build.MODEL.trim();

    // defines the GCM registration token
    private String mPushKey = null;

    // 3 states : null not initialized (retrieved by flavor)
    private static Boolean mUseGCM;

    /**
     * Constructor
     * @param appContext the application context.
     */
    public GcmRegistrationManager(final Context appContext) {
        super(appContext);
    }

    /**
     * Check if the GCM registration has been broken with a new token ID.
     * The GCM could have cleared it (onTokenRefresh).
     */
    public void checkRegistrations() {
        Log.d(LOG_TAG, "checkRegistrations with state " + mRegistrationState);

        if (!usePush()) {
            Log.d(LOG_TAG, "checkRegistrations : GCM is disabled");
            return;
        }

        // remove the GCM registration token after switching to the FCM one
        if (null != getOldStoredRegistrationToken()) {
            Log.d(LOG_TAG, "checkRegistrations : remove the GCM registration token after switching to the FCM one");

            mPushKey = getOldStoredRegistrationToken();

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
                    mPushKey = null;

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

            registerToPushService(new PushRegistrationListener() {
                @Override
                public void onPushRegistered() {
                    Log.d(LOG_TAG, "checkRegistrations : reregistered");
                    CommonActivityUtils.onPushServiceUpdate(mContext);
                }

                @Override
                public void onPushRegistrationFailed() {
                    Log.d(LOG_TAG, "checkRegistrations : onPusherRegistrationFailed");
                }
            });
        } else if (mRegistrationState == RegistrationState.PUSH_REGISTRED) {
            // register the 3rd party server
            // the server registration might have failed
            // so ensure that it will be done when the application is debackgrounded.
            if (usePush() && areDeviceNotificationsAllowed()) {
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
    public String getPushRegistrationToken() {
        String registrationToken = getStoredRegistrationToken();

        if (TextUtils.isEmpty(registrationToken)) {
            Log.d(LOG_TAG, "## getGCMRegistrationToken() : undefined token -> getting a nex one");
            registrationToken = GCMHelper.getRegistrationToken();
        }
        return registrationToken;
    }

    /**
     * Reset the GCM registration.
     * @param newToken the new registration token
     */
    @Override
    public void resetPushServiceRegistration(final String newToken) {
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
                    resetPushServiceRegistration(newToken);
                }

                @Override
                public void onThirdPartyUnregistrationFailed() {
                    Log.d(LOG_TAG, "resetGCMRegistration : unregistration failed.");
                }
            });
        } else {
            final boolean clearEverything = TextUtils.isEmpty(newToken);

            Log.d(LOG_TAG, "resetGCMRegistration : Clear the GCM data");
            clearPushData(clearEverything, new SimpleApiCallback<Void>() {
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
    // GCM preferences
    //================================================================================

    /**
     * Clear the GCM preferences
     */
    @Override
    public void clearPreferences() {
        super.clearPreferences();
        getGcmSharedPreferences().edit().clear().commit();
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
    @Override
    protected String getStoredRegistrationToken() {
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
    @Override
    protected void setStoredRegistrationToken(String registrationToken) {
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
    @Override
    public void clearPushData(final boolean clearRegistrationToken, final ApiCallback callback) {
        try {
            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {
                    setStoredRegistrationToken(null);
                    mPushKey = null;
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
}
