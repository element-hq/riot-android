/* 
 * Copyright 2014 OpenMarket Ltd
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
package im.vector.gcm;

import android.content.Context;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import im.vector.R;

public class GCMHelper {

    private static final String LOG_TAG = "GCMHelper";

    /**
     * Retrieves the GCM registration token.
     *
     * @param appContext the application context
     * @return the registration token.
     */
    public static String getRegistrationToken(Context appContext) {
        String registrationToken;

        try {
            Log.d(LOG_TAG, "Getting the GCM Registration Token");
            registrationToken = InstanceID.getInstance(appContext).getToken(appContext.getString(R.string.gcm_defaultSenderId),
                    GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);

            Log.d(LOG_TAG, "GCM Registration Token: " + registrationToken);
        } catch (Exception e) {
            Log.e(LOG_TAG, "getRegistrationToken failed with exception : " + e.getMessage());
            registrationToken = null;
        }

        return registrationToken;
    }

    /**
     * Clear the registration token.
     *
     * @param appContext the application context
     */
    public static void clearRegistrationToken(Context appContext) {
        try {
            InstanceID.getInstance(appContext).deleteToken(appContext.getString(R.string.gcm_defaultSenderId), GoogleCloudMessaging.INSTANCE_ID_SCOPE);
            InstanceID.getInstance(appContext).deleteInstanceID();
        } catch (Exception e) {
            Log.e(LOG_TAG, "clearRegistrationToken failed with exception : " + e.getMessage());
        }
    }
}
