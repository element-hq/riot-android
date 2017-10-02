/*
 * Copyright 2016 OpenMarket Ltd
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

package im.vector.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.provider.Settings;

import im.vector.Matrix;
import im.vector.services.EventStreamService;

/**
 * Dismiss notification receiver
 */
public class DismissNotificationReceiver extends BroadcastReceiver {
    private static final String DISMISS_NOTIFICATIONS_TS_KEY = "DISMISS_NOTIFICATIONS_TS_KEY";
    private static final String LATEST_NOTIFIED_MESSAGE_TS_KEY = "LATEST_NOTIFIED_MESSAGE_TS_KEY";


    public void onReceive(Context context, Intent intent) {
        // don't use System.currentTimeMillis to avoid hiding unexpected message
        // use the server clock
        // some 10 to 20s delays have been seen
        setNotificationDismissTs(context, getLatestNotifiedMessageTs(context));
        // TODO manage multi accounts
        EventStreamService.onMessagesNotificationDismiss(Matrix.getInstance(context).getDefaultSession().getMyUserId());
    }

    /**
     * Get the latest notification dismiss timestamp.
     * @param context the context
     * @return the timestamp
     */
    public static long getNotificationDismissTs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getLong(DISMISS_NOTIFICATIONS_TS_KEY, 0);
    }

    /**
     * Set the latest notification dismiss timestamp.
     * @param context the context
     * @param ts the timestamp
     */
    public static void setNotificationDismissTs(Context context, long ts) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(DISMISS_NOTIFICATIONS_TS_KEY, ts);
        editor.commit();
    }

    /**
     * Get the latest notified message timestamp.
     * @param context the context
     * @return the timestamp
     */
    public static long getLatestNotifiedMessageTs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getLong(LATEST_NOTIFIED_MESSAGE_TS_KEY, 0);
    }

    /**
     * Set the latest notified message timestamp.
     * @param context the context
     * @param ts the timestamp
     */
    public static void setLatestNotifiedMessageTs(Context context, long ts) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putLong(LATEST_NOTIFIED_MESSAGE_TS_KEY, ts);
        editor.commit();
    }
}

