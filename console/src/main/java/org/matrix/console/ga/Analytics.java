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

package org.matrix.console.ga;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.google.android.gms.analytics.ExceptionParser;
import com.google.android.gms.analytics.ExceptionReporter;
import com.google.android.gms.analytics.GoogleAnalytics;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;

import android.content.Context;
import android.util.Log;

public class Analytics {
    private static final String LOG_TAG = "Analytics";

    public static GoogleAnalytics mAnalytics;
    public static Tracker mTracker;

    /**
     * Initialise Google Analytics immediately so it will catch all sorts of errors prior to EasyTracker onStart. Also makes sensible
     * exception stack traces if you want.
     * @param context App context
     * @param trackerId The tracker ID to use.
     * @return A GoogleAnalytics reference
     */
    public static GoogleAnalytics initialiseGoogleAnalytics(Context context, String trackerId, final ExceptionParser callback) {
        mAnalytics = GoogleAnalytics.getInstance(context);
        mAnalytics.setLocalDispatchPeriod(1800);

        mTracker = mAnalytics.newTracker(trackerId);
        mTracker.enableExceptionReporting(true);
        //mTracker.enableAdvertisingIdCollection(true);
        mTracker.enableAutoActivityTracking(true);

        // overwrite the exception parser to be more useful.
        Thread.UncaughtExceptionHandler handler = Thread.getDefaultUncaughtExceptionHandler();
        if (handler != null && handler instanceof ExceptionReporter) { // this handler is the GA one
            ExceptionReporter exceptionReporter = (ExceptionReporter)handler;
            exceptionReporter.setExceptionParser(callback);
            Thread.setDefaultUncaughtExceptionHandler(exceptionReporter);

            Log.d(LOG_TAG, "Analytics active.");
        }
        else {
            Log.e(LOG_TAG, "Cannot set custom exception parser.");
        }

        return mAnalytics;
    }

    public static String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    public static void sendEvent(String category, String action, String label) {
        sendEvent(category, action, label, Long.MAX_VALUE);
    }

    public static void sendEvent(String category, String action, String label, long value) {
        // add sanity check, GA could have been disabled.
        if (null != mTracker) {
            HitBuilders.EventBuilder eventBuilder = new HitBuilders.EventBuilder(category, action);

            if (null != label) {
                eventBuilder.setLabel(label);
            }

            if (Long.MAX_VALUE != value) {
                eventBuilder.setValue(value);
            }

            mTracker.send(eventBuilder.build());
        }
    }
}
