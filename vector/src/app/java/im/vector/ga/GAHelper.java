/* 
 * Copyright 2014 OpenMarket Ltd
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
package im.vector.ga;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.analytics.ExceptionParser;

import im.vector.R;
import im.vector.VectorApp;

public class GAHelper {

    private static final String LOG_TAG = "GAHelper";

    //==============================================================================================================
    // Google analytics
    //==============================================================================================================
    /**
     * Update the GA use.
     * @param context the context
     * @param value the new value
     */
    public static void setUseGA(Context context, boolean value) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(context.getString(R.string.ga_use_settings), value);
        editor.commit();

        initGoogleAnalytics(context);
    }

    /**
     * Tells if GA can be used
     * @param context the context
     * @return null if not defined, true / false when defined
     */
    public static Boolean useGA(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (preferences.contains(context.getString(R.string.ga_use_settings))) {
            return preferences.getBoolean(context.getString(R.string.ga_use_settings), false);
        } else {
            try {
                // test if the client should not use GA
                boolean allowGA = TextUtils.equals(context.getResources().getString(R.string.allow_ga_use), "true");

                if (!allowGA) {
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putBoolean(context.getString(R.string.ga_use_settings), false);
                    editor.commit();

                    return false;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "useGA " + e.getLocalizedMessage());
            }

            return null;
        }
    }

    /**
     * Initialize the google analytics
     */
    public static void initGoogleAnalytics(Context context) {
        Boolean useGA = useGA(context);

        if (null == useGA) {
            Log.e(LOG_TAG, "Google Analytics use is not yet initialized");
            return;
        }

        if (!useGA) {
            Log.e(LOG_TAG, "The user decides to do not use Google Analytics");
            return;
        }

        // pull tracker resource ID from res/values/analytics.xml
        int trackerResId = context.getResources().getIdentifier("ga_trackingId", "string", context.getPackageName());
        if (trackerResId == 0) {
            Log.e(LOG_TAG, "Unable to find tracker id for Google Analytics");
            return;
        }

        String trackerId = context.getString(trackerResId);
        Log.d(LOG_TAG, "Tracker ID: "+trackerId);
        // init google analytics with this tracker ID
        if (!TextUtils.isEmpty(trackerId)) {
            Analytics.initialiseGoogleAnalytics(context, trackerId, new ExceptionParser() {
                @Override
                public String getDescription(String threadName, Throwable throwable) {
                    StringBuilder b = new StringBuilder();

                    b.append("Vector Build : " + VectorApp.VERSION_BUILD + "\n");
                    b.append("Vector Version : " + VectorApp.VECTOR_VERSION_STRING + "\n");
                    b.append("SDK Version : " + VectorApp.SDK_VERSION_STRING + "\n");
                    b.append("Phone : " + Build.MODEL.trim() + " (" + Build.VERSION.INCREMENTAL + " " + Build.VERSION.RELEASE + " " + Build.VERSION.CODENAME + ")\n");
                    b.append("Thread: ");
                    b.append(threadName);

                    Activity a = VectorApp.getCurrentActivity();
                    if (a != null) {
                        b.append(", Activity:");
                        b.append(a.getLocalClassName());
                    }

                    b.append(", Exception: ");
                    b.append(Analytics.getStackTrace(throwable));
                    Log.e("FATAL EXCEPTION", b.toString());
                    return b.toString();
                }
            });
        }
    }

}
