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

package org.matrix.matrixandroidsdk;
import android.app.Activity;
import android.app.Application;

import org.matrix.matrixandroidsdk.contacts.ContactsManager;
import org.matrix.matrixandroidsdk.contacts.PIDsRetriever;
import org.matrix.matrixandroidsdk.services.EventStreamService;

import java.util.Timer;
import java.util.TimerTask;

/**
 * The main application injection point
 */
public class ConsoleApplication extends Application {
    private Timer mActivityTransitionTimer;
    private TimerTask mActivityTransitionTimerTask;
    public boolean isInBackground = true;
    private final long MAX_ACTIVITY_TRANSITION_TIME_MS = 2000;

    @Override
    public void onCreate() {
        super.onCreate();
        mActivityTransitionTimer = null;
        mActivityTransitionTimerTask = null;

        // get the contact update at application launch
        ContactsManager.refreshLocalContactsSnapshot(this);

        isInBackground = false;
    }

    public void startActivityTransitionTimer() {
        this.mActivityTransitionTimer = new Timer();
        this.mActivityTransitionTimerTask = new TimerTask() {
            public void run() {
                ConsoleApplication.this.isInBackground = true;
                PIDsRetriever.getIntance().onAppBackgrounded();
            }
        };

        this.mActivityTransitionTimer.schedule(mActivityTransitionTimerTask, MAX_ACTIVITY_TRANSITION_TIME_MS);
    }

    public void stopActivityTransitionTimer() {
        if (this.mActivityTransitionTimerTask != null) {
            this.mActivityTransitionTimerTask.cancel();
        }

        if (this.mActivityTransitionTimer != null) {
            this.mActivityTransitionTimer.cancel();
        }

        if (isInBackground) {
            // get the contact update at application launch
            ContactsManager.refreshLocalContactsSnapshot(this);
        }

        this.isInBackground = false;
    }

    static private Activity mCurrentActivity = null;
    public static void setCurrentActivity(Activity activity) {
        mCurrentActivity = activity;
    }
    public static Activity getCurrentActivity() { return mCurrentActivity; }

    /**
     * Return true if the application is in background.
     */
    public static boolean isAppInBackground() {
        if (mCurrentActivity != null) {
            return ((ConsoleApplication)(mCurrentActivity.getApplication())).isInBackground;
        }

        return true;
    }
}

