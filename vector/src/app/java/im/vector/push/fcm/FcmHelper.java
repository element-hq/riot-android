/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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
package im.vector.push.fcm;

import android.app.Activity;
import android.content.Context;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.matrix.androidsdk.util.Log;

/**
 * This class store the FCM token in SharedPrefs and ensure this token is retrieved.
 * It has an alter ego in the fdroid variant.
 */
public class FcmHelper {
    private static final String LOG_TAG = FcmHelper.class.getSimpleName();

    private static final String PREFS_KEY_FCM_TOKEN = "FCM_TOKEN";

    /**
     * Retrieves the FCM registration token.
     *
     * @return the FCM token or null if not received from FCM
     */
    @Nullable
    public static String getFcmToken(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREFS_KEY_FCM_TOKEN, null);
    }

    /**
     * Store FCM token to the SharedPrefs
     *
     * @param context android context
     * @param token   the token to store
     */
    public static void storeFcmToken(@NonNull Context context,
                                     @Nullable String token) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(PREFS_KEY_FCM_TOKEN, token)
                .apply();
    }

    /**
     * onNewToken may not be called on application upgrade, so ensure my shared pref is set
     *
     * @param activity the first launch Activity
     */
    public static void ensureFcmTokenIsRetrieved(final Activity activity) {
        if (TextUtils.isEmpty(getFcmToken(activity))) {
            try {
                FirebaseInstanceId.getInstance().getInstanceId().addOnSuccessListener(activity, new OnSuccessListener<InstanceIdResult>() {
                    @Override
                    public void onSuccess(InstanceIdResult instanceIdResult) {
                        storeFcmToken(activity, instanceIdResult.getToken());
                    }
                });
            } catch (Exception e) {
                Log.e(LOG_TAG, "## ensureFcmTokenIsRetrieved() : failed " + e.getMessage(), e);
            }
        }
    }
}
