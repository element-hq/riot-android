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

import im.vector.activity.CallViewActivity;
import im.vector.activity.CommonActivityUtils;
import im.vector.contacts.ContactsManager;
import im.vector.contacts.PIDsRetriever;
import im.vector.ga.GAHelper;
import im.vector.gcm.GcmRegistrationManager;
import im.vector.services.EventStreamService;
import im.vector.util.LogUtilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The main application injection point
 */
public class VectorApp extends Application {
    private static final String LOG_TAG = "VectorApp";

    private Timer mActivityTransitionTimer;
    private TimerTask mActivityTransitionTimerTask;
    private boolean mIsInBackground = true;
    private final long MAX_ACTIVITY_TRANSITION_TIME_MS = 2000;

    // google analytics info
    public static int VERSION_BUILD = -1;
    public static String VECTOR_VERSION_STRING = "";
    public static String SDK_VERSION_STRING = "";

    private Boolean mIsCallingInBackground = false;

    private static VectorApp instance = null;

    private EventEmitter<Activity> mOnActivityDestroyedListener;

    private static Bitmap mSavedPickerImagePreview = null;

    @Override
    public void onCreate() {
        Log.d(LOG_TAG, "onCreate");
        super.onCreate();

        instance = this;

        mOnActivityDestroyedListener = new EventEmitter<>();

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
    }

    public static VectorApp getInstance() {
        return instance;
    }

    public EventEmitter<Activity> getOnActivityDestroyedListener() {
        return mOnActivityDestroyedListener;
    }
    /**
     * Suspend background threads.
     */
    private void suspendApp() {
        GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(VectorApp.this).getSharedGcmRegistrationManager();

        // suspend the events thread if the client uses GCM
        if (!gcmRegistrationManager.isBackgroundSyncAllowed() || (gcmRegistrationManager.useGCM() && gcmRegistrationManager.hasPushKey())) {
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
     * The application is warned that a call is ended.
     */
    public void onCallEnd() {
        if (isAppInBackground() && mIsCallingInBackground) {
            Log.d(LOG_TAG, "onCallEnd : Suspend the events thread because the call was ended whereas the application was in background");
            suspendApp();
        }

        mIsCallingInBackground = false;
    }

    private void startActivityTransitionTimer() {
        this.mActivityTransitionTimer = new Timer();
        this.mActivityTransitionTimerTask = new TimerTask() {
            public void run() {
                if (VectorApp.this.mActivityTransitionTimerTask != null) {
                    VectorApp.this.mActivityTransitionTimerTask.cancel();
                    VectorApp.this.mActivityTransitionTimerTask = null;
                }

                if (VectorApp.this.mActivityTransitionTimer != null) {
                    VectorApp.this.mActivityTransitionTimer.cancel();
                    VectorApp.this.mActivityTransitionTimer = null;
                }

                VectorApp.this.mIsInBackground = true;
                mIsCallingInBackground = (null != CallViewActivity.getActiveCall());

                // if there is a pending call
                // the application is not suspended
                if (!mIsCallingInBackground) {
                    Log.d(LOG_TAG, "Suspend the application because there was no resumed activity within 2 seconds");
                    suspendApp();
                }
            }
        };

        this.mActivityTransitionTimer.schedule(mActivityTransitionTimerTask, MAX_ACTIVITY_TRANSITION_TIME_MS);
    }

    private void stopActivityTransitionTimer() {
        if (this.mActivityTransitionTimerTask != null) {
            this.mActivityTransitionTimerTask.cancel();
            this.mActivityTransitionTimerTask = null;
        }

        if (this.mActivityTransitionTimer != null) {
            this.mActivityTransitionTimer.cancel();
            this.mActivityTransitionTimer = null;
        }

        if (isAppInBackground() && !mIsCallingInBackground) {

            // the event stream service has been killed
            if (null == EventStreamService.getInstance()) {
                CommonActivityUtils.startEventStreamService(VectorApp.this);
            } else {
                CommonActivityUtils.resumeEventStream(VectorApp.this);

                // try to perform a GCM registration if it failed
                // or if the GCM server generated a new push key
                GcmRegistrationManager gcmRegistrationManager = Matrix.getInstance(this).getSharedGcmRegistrationManager();

                if (null != gcmRegistrationManager) {
                    gcmRegistrationManager.checkPusherRegistration(this);
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

        this.mIsCallingInBackground = false;
        this.mIsInBackground = false;
    }

    static private Activity mCurrentActivity = null;
    public static void setCurrentActivity(Activity activity) {
        if (VectorApp.isAppInBackground() && (null != activity)) {
            Matrix matrixInstance =  Matrix.getInstance(activity.getApplicationContext());

            // sanity check
            if (null != matrixInstance) {
                matrixInstance.refreshPushRules();
            }

            Log.d(LOG_TAG, "The application is resumed");
            // display the memory usage when the application is debackgrounded.
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
    public static Activity getCurrentActivity() { return mCurrentActivity; }

    /**
     * Return true if the application is in background.
     */
    public static boolean isAppInBackground() {
        return (null == mCurrentActivity) && (null != getInstance()) && getInstance().mIsInBackground;
    }
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

