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

package im.vector;
import android.app.Activity;
import android.app.Application;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.matrix.androidsdk.MXSession;

import im.vector.activity.VectorCallViewActivity;
import im.vector.activity.CommonActivityUtils;
import im.vector.contacts.ContactsManager;
import im.vector.contacts.PIDsRetriever;
import im.vector.ga.GAHelper;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.services.EventStreamService;
import im.vector.util.LogUtilities;
import im.vector.util.RageShake;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The main application injection point
 */
public class VectorApp extends Application {

    private static final String LOG_TAG = "VectorApp";

    // instance
    private static VectorApp instance = null;

    // rage shake detection
    private static RageShake mRageShake = new RageShake();

    // active activity detection
    private Timer mActivityTransitionTimer;
    private TimerTask mActivityTransitionTimerTask;
    private boolean mIsInBackground = true;
    private final long MAX_ACTIVITY_TRANSITION_TIME_MS = 2000;

    // google analytics info
    public static int VERSION_BUILD = -1;
    public static String VECTOR_VERSION_STRING = "";
    public static String SDK_VERSION_STRING = "";

    // call in progress management
    private boolean mIsCallingInBackground = false;

    // the current activity
    private static Activity mCurrentActivity = null;

    // return the current instance
    public static VectorApp getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate();

        instance = this;
        mActivityTransitionTimer = null;
        mActivityTransitionTimerTask = null;

        try {
            PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            VERSION_BUILD = pinfo.versionCode;
        }
        catch (PackageManager.NameNotFoundException e) {}

        VECTOR_VERSION_STRING = Matrix.getInstance(this).getVersion(true);

        // not the first launch
        if (null != Matrix.getInstance(this).getDefaultSession()) {
            SDK_VERSION_STRING = Matrix.getInstance(this).getDefaultSession().getVersion(true);
        } else {
            SDK_VERSION_STRING = "";
        }

        LogUtilities.setLogDirectory(new File(getCacheDir().getAbsolutePath() + "/logs"));
        LogUtilities.storeLogcat();

        GAHelper.initGoogleAnalytics(getApplicationContext());

        // get the contact update at application launch
        ContactsManager.refreshLocalContactsSnapshot(this);

        mRageShake.start(this);
    }

    /**
     * Suspend background threads.
     */
    private void suspendApp() {
        GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(VectorApp.this).getSharedGCMRegistrationManager();

        // suspend the events thread if the client uses GCM
        if (!gcmRegistrationManager.isBackgroundSyncAllowed() || (gcmRegistrationManager.useGCM() && gcmRegistrationManager.hasRegistrationToken())) {
            Log.d(LOG_TAG, "suspendApp ; pause the event stream");
            CommonActivityUtils.pauseEventStream(VectorApp.this);
        } else {
            Log.d(LOG_TAG, "suspendApp ; the event stream is not paused because GCM is disabled.");
        }

        // the sessions are not anymore seen as "online"
        ArrayList<MXSession> sessions = Matrix.getInstance(this).getSessions();
        for(MXSession session : sessions) {
            if (session.isAlive()) {
                session.setIsOnline(false);
                session.setSyncDelay(gcmRegistrationManager.getBackgroundSyncDelay());
                session.setSyncTimeout(gcmRegistrationManager.getBackgroundSyncTimeOut());
            }
        }

        PIDsRetriever.getIntance().onAppBackgrounded();

        MyPresenceManager.advertiseAllUnavailable();
    }

    /**
     * Test if application is put in background.
     * i.e wait 2s before assuming that the application is put in background.
     */
    private void startActivityTransitionTimer() {
        mActivityTransitionTimer = new Timer();
        mActivityTransitionTimerTask = new TimerTask() {
            @Override
            public void run() {
                if (mActivityTransitionTimerTask != null) {
                    mActivityTransitionTimerTask.cancel();
                    mActivityTransitionTimerTask = null;
                }

                if (mActivityTransitionTimer != null) {
                    mActivityTransitionTimer.cancel();
                    mActivityTransitionTimer = null;
                }

                VectorApp.this.mIsInBackground = true;
                mIsCallingInBackground = (null != VectorCallViewActivity.getActiveCall());

                // if there is a pending call
                // the application is not suspended
                if (!mIsCallingInBackground) {
                    Log.d(LOG_TAG, "Suspend the application because there was no resumed activity within 2 seconds");
                    suspendApp();
                } else {
                    Log.d(LOG_TAG, "App not suspended due to call in progress");
                }
            }
        };

        mActivityTransitionTimer.schedule(mActivityTransitionTimerTask, MAX_ACTIVITY_TRANSITION_TIME_MS);
    }

    /**
     * Stop the background detection.
     */
    private void stopActivityTransitionTimer() {
        if (mActivityTransitionTimerTask != null) {
            mActivityTransitionTimerTask.cancel();
            mActivityTransitionTimerTask = null;
        }

        if (mActivityTransitionTimer != null) {
            mActivityTransitionTimer.cancel();
            mActivityTransitionTimer = null;
        }

        if (isAppInBackground() && !mIsCallingInBackground) {

            // the event stream service has been killed
            if (null == EventStreamService.getInstance()) {
                CommonActivityUtils.startEventStreamService(VectorApp.this);
            } else {
                CommonActivityUtils.resumeEventStream(VectorApp.this);

                // try to perform a GCM registration if it failed
                // or if the GCM server generated a new push key
                GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(this).getSharedGCMRegistrationManager();

                if (null != gcmRegistrationManager) {
                    gcmRegistrationManager.checkRegistrations();
                }
            }

            // get the contact update at application launch
            ContactsManager.refreshLocalContactsSnapshot(this);

            ArrayList<MXSession> sessions = Matrix.getInstance(this).getSessions();
            for(MXSession session : sessions) {
                session.getMyUser().refreshUserInfos(null);
                session.setIsOnline(true);
                session.setSyncDelay(0);
                session.setSyncTimeout(0);
            }
        }

        MyPresenceManager.advertiseAllOnline();

        mIsCallingInBackground = false;
        mIsInBackground = false;
    }

    /**
     * Update the current active activity.
     * It manages the application background / foreground when it is required.
     * @param activity the current activity, null if there is no more one.
     */
    public static void setCurrentActivity(Activity activity) {
        if (VectorApp.isAppInBackground() && (null != activity)) {
            Matrix matrixInstance =  Matrix.getInstance(activity.getApplicationContext());

            // sanity check
            if (null != matrixInstance) {
                matrixInstance.refreshPushRules();
            }

            Log.d(LOG_TAG, "The application is resumed");
            // display the memory usage when the application is put iun foreground..
            CommonActivityUtils.displayMemoryInformation(activity);
        }

        // wait 2s to check that the application is put in background
        if (null != getInstance()) {
            if (null == activity) {
                getInstance().startActivityTransitionTimer();
            } else {
                getInstance().stopActivityTransitionTimer();
            }
        }

        mCurrentActivity = activity;
    }

    /**
     * @return the current active activity
     */
    public static Activity getCurrentActivity() { return mCurrentActivity; }

    /**
     * Return true if the application is in background.
     */
    public static boolean isAppInBackground() {
        return (null == mCurrentActivity) && (null != getInstance()) && getInstance().mIsInBackground;
    }

    //==============================================================================================================
    // Calls management.
    //==============================================================================================================

    /**
     * The application is warned that a call is ended.
     */
    public void onCallEnd() {
        if (isAppInBackground() && mIsCallingInBackground) {
            Log.d(LOG_TAG, "onCallEnd : Suspend the events thread because the call was ended whereas the application was in background");
            suspendApp();
        }

        mIsCallingInBackground = false;
    }

    //==============================================================================================================
    // cert management : store the active activities.
    //==============================================================================================================

    private EventEmitter<Activity> mOnActivityDestroyedListener = new EventEmitter<>();

    /**
     * @return the EventEmitter list.
     */
    public EventEmitter<Activity> getOnActivityDestroyedListener() {
        return mOnActivityDestroyedListener;
    }

    //==============================================================================================================
    // Media pickers : image backup
    //==============================================================================================================

    private static Bitmap mSavedPickerImagePreview = null;

    /**
     * The image taken from the medias picker is stored in a static variable because
     * saving it would take too much time.
     * @return the saved image from medias picker
     */
    public static Bitmap getSavedPickerImagePreview(){
        return mSavedPickerImagePreview;
    }

    /**
     * Save the image taken in the medias picker
     * @param aSavedCameraImagePreview
     */
    public static void setSavedCameraImagePreview(Bitmap aSavedCameraImagePreview){
        if (aSavedCameraImagePreview != mSavedPickerImagePreview) {
            // force to release memory
            // reported by GA
            // it seems that the medias picker might be refreshed
            // while leaving the activity
            // recycle the bitmap trigger a rendering issue
            // Canvas: trying to use a recycled bitmap...

            /*if (null != mSavedPickerImagePreview) {
                mSavedPickerImagePreview.recycle();
                mSavedPickerImagePreview = null;
                System.gc();
            }*/


            mSavedPickerImagePreview = aSavedCameraImagePreview;
        }
    }
}

