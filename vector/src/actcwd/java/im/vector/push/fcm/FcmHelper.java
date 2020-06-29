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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.matrix.androidsdk.core.Log;

import im.vector.R;

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


            //vfe: according to firebase doc
            //'app should always check the device for a compatible Google Play services APK before accessing Google Play services features'
            if (checkPlayServices(activity)) {
                try {
                    FirebaseInstanceId.getInstance().getInstanceId()
                            .addOnSuccessListener(activity, new OnSuccessListener<InstanceIdResult>() {
                                @Override
                                public void onSuccess(InstanceIdResult instanceIdResult) {
                                    storeFcmToken(activity, instanceIdResult.getToken());
                                }
                            })
                            .addOnFailureListener(activity, new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Log.e(LOG_TAG, "## ensureFcmTokenIsRetrieved() : failed " + e.getMessage(), e);
                                }
                            });
                } catch (Throwable e) {
                    Log.e(LOG_TAG, "## ensureFcmTokenIsRetrieved() : failed " + e.getMessage(), e);
                }
            } else {
                Toast.makeText(activity, R.string.no_valid_google_play_services_apk, Toast.LENGTH_SHORT).show();
                Log.e(LOG_TAG, "No valid Google Play Services found. Cannot use FCM.");
            }
        }
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private static boolean checkPlayServices(Activity activity) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(activity);
        if (resultCode != ConnectionResult.SUCCESS) {
            return false;
        }
        return true;
    }
}
