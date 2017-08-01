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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import org.matrix.androidsdk.util.Log;

import im.vector.VectorApp;

public class GAHelper {

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
        return false;
    }

    /**
     * Initialize the google analytics
     */
    public static void initGoogleAnalytics(Context context) {
        if (null == mDefaultExceptionHandler) {
            mDefaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        }

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
    }
}
