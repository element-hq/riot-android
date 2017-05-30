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
package im.vector.ga;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import org.matrix.androidsdk.util.Log;

import com.google.android.gms.analytics.ExceptionParser;
import com.google.android.gms.analytics.HitBuilders;

import im.vector.R;
import im.vector.VectorApp;

public class GAHelper {

    private static final String LOG_TAG = "GAHelper";

    //==============================================================================================================
    // Google analytics
    //==============================================================================================================

    // default exception handler
    private static Thread.UncaughtExceptionHandler mDefaultExceptionHandler = null;

    /**
     * Tells if the GA use can be updated
     *
     * @return true if it can be updated
     */
    public static boolean isGAUseUpdatable() {
        return true;
    }

    /**
     * Update the GA use.
     *
     * @param context the context
     * @param value   the new value
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
     *
     * @param context the context
     * @return null if not defined, true / false when defined
     */
    public static Boolean useGA(Context context) {
        // avoid getting the GA issues from the forked branches
        if (!TextUtils.equals(VectorApp.getInstance().getPackageName(), "im.vector.alpha")) {
            return false;
        }

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
    public static void initGoogleAnalytics(final Context context) {
        int trackerResId = 0;

        Boolean useGA = useGA(context);

        if (null == useGA) {
            Log.e(LOG_TAG, "Google Analytics use is not yet initialized");
        } else if (!useGA) {
            Log.e(LOG_TAG, "The user decides to do not use Google Analytics");
        } else {
            // pull tracker resource ID from res/values/analytics.xml
            trackerResId = context.getResources().getIdentifier("ga_trackingId", "string", context.getPackageName());

            if (trackerResId == 0) {
                Log.e(LOG_TAG, "Unable to find tracker id for Google Analytics");
            }
        }

        String trackerId = null;

        if (0 != trackerResId) {
            trackerId = context.getString(trackerResId);
        }

        Log.d(LOG_TAG, "Tracker ID: " + trackerId);

        if (null == mDefaultExceptionHandler) {
            mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        }

        // init google analytics with this tracker ID
        if (!TextUtils.isEmpty(trackerId)) {
            Analytics.initialiseGoogleAnalytics(context, trackerId, new ExceptionParser() {
                @Override
                public String getDescription(String threadName, Throwable throwable) {
                    return VectorApp.uncaughtException(threadName, throwable);
                }
            });
        } else {
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable e) {
                    VectorApp.uncaughtException(thread.getName(), e);

                    if (null != mDefaultExceptionHandler) {
                        mDefaultExceptionHandler.uncaughtException(thread, e);
                    }
                }
            });
        }
    }

    /**
     * Send a GA stats
     *
     * @param context  the context
     * @param category the category
     * @param action   the action
     * @param label    the label
     * @param value    the value
     */
    public static void sendGAStats(Context context, String category, String action, String label, long value) {
        Boolean useGA = useGA(context);

        if (null == useGA) {
            Log.e(LOG_TAG, "Google Analytics use is not yet initialized");
            return;
        }

        if (!useGA) {
            Log.e(LOG_TAG, "The user decides to do not use Google Analytics");
            return;
        }

        try {
            // send by default a timing event
            // check if a value is set
            if ((null != Analytics.mTracker) && (value != Long.MAX_VALUE)) {
                HitBuilders.TimingBuilder timingEvent = new HitBuilders.TimingBuilder();

                timingEvent.setValue(value);

                if (!TextUtils.isEmpty(category)) {
                    timingEvent.setCategory(category);
                }

                if (!TextUtils.isEmpty(action)) {
                    timingEvent.setVariable(action);
                }

                /*if (!TextUtils.isEmpty(label)) {
                    timingEvent.setLabel(label);
                }*/

                Analytics.mTracker.send(timingEvent.build());
            }

            // default management
            Analytics.sendEvent(category, action, label, value);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## sendGAStats failed " + e.getMessage());
        }
    }
}
