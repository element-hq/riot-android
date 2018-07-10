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

package im.vector.push;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.text.TextUtils;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;

import im.vector.Matrix;
import im.vector.activity.CommonActivityUtils;


/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class TemplatePushManager extends PushManager {
    private static final String LOG_TAG = TemplatePushManager.class.getSimpleName();;


    private static final String DEFAULT_PUSHER_APP_ID = "im.vector.app.push.template";
    private static final String DEFAULT_PUSHER_URL = "https://matrix.org/_matrix/push/v1/notify";
    private static final String DEFAULT_PUSHER_FILE_TAG = "mobile";

    /**
     * Constructor
     * @param appContext the application context.
     */
    public TemplatePushManager(final Context appContext) {
        super(appContext);
    }

    /**
     * Check if the GCM registration has been broken with a new token ID.
     * The GCM could have cleared it (onTokenRefresh).
     */
    @Override
    public void checkRegistrations() {
        Log.d(LOG_TAG, "checkRegistrations is not implemented");
    }

    //================================================================================
    // GCM registration
    //================================================================================
    
    /**
     * Retrieve the GCM registration token.
     *
     * @return the GCM registration token
     */
    @Override
    public String getPushRegistrationToken() {
        return null;
    }

    /**
     * @return the GCM registration stored for this version of the app or null if none is stored.
     */
    @Override
    protected String getStoredRegistrationToken() {
        return null;
    }

    /**
     * Set the GCM registration for the currently-running version of this app.
     * @param registrationToken the registration token
     */
    @Override
    protected void setStoredRegistrationToken(String registrationToken) {
        Log.d(LOG_TAG, "Saving registration token (template)");
    }

    /**
     * Clear the GCM data
     * @param clearRegistrationToken true to clear the provided GCM token
     * @param callback the asynchronous callback
     */
    @Override
    public void clearPushData(final boolean clearRegistrationToken, final ApiCallback callback) {
        if (null != callback) {
            callback.onSuccess(null);
        }
    }

    /**
     * Get Default App ID
     * @return
     */
    @Override
    protected String getDefaultPusherAppId() {
        return DEFAULT_PUSHER_APP_ID;
    }

    /**
     * Get Default Pusher Url
     * @return
     */
    @Override
    protected String getDefaultPusherUrl(){
        return DEFAULT_PUSHER_URL;
    }
}
